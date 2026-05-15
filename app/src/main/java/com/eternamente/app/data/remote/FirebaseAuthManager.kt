package com.eternamente.app.data.remote

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Centraliza todas las operaciones de Firebase Authentication.
 *
 * Este manager actúa como capa de acceso remoto (remote data source) exclusivamente
 * para auth. No conoce Room ni DataStore — esa coordinación vive en el ViewModel.
 *
 * La aplicación es **offline-first**: Firebase Auth es complementario. Si no hay
 * conexión, [login] y [register] devuelven [Result.Error] pero la sesión local
 * sigue activa vía Room + DataStore.
 */
@Singleton
class FirebaseAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

    /** Usuario actualmente autenticado en Firebase, o `null` si no hay sesión. */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /** `true` si hay una sesión Firebase activa. */
    val isAuthenticated: Boolean get() = firebaseAuth.currentUser != null

    // ── Autenticación ─────────────────────────────────────────────────────────

    /**
     * Inicia sesión en Firebase con correo y contraseña.
     *
     * Casos de error mapeados a mensajes en español:
     * - Credenciales inválidas → [FirebaseAuthException] con mensaje claro
     * - Sin internet → mensaje indicando que los datos locales están seguros
     * - Cuenta deshabilitada / demasiados intentos → mensajes específicos
     *
     * @param email    Correo del usuario.
     * @param password Contraseña (en EternaMente MVP: el PIN de 6 dígitos).
     * @return [Result.Success] con el [FirebaseUser] autenticado, o [Result.Error].
     */
    suspend fun login(email: String, password: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            safeCall {
                suspendCancellableCoroutine { cont ->
                    firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { authResult ->
                            val user = authResult.user
                            if (user != null) {
                                cont.resume(user)
                            } else {
                                cont.resumeWithException(
                                    IllegalStateException("Firebase: usuario nulo tras signIn")
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            cont.resumeWithException(mapFirebaseError(e))
                        }
                        .addOnCanceledListener { cont.cancel() }
                }
            }
        }

    /**
     * Crea una cuenta nueva en Firebase Auth con correo y contraseña.
     *
     * Casos manejados:
     * - Correo ya registrado → [FirebaseAuthException] "EMAIL_ALREADY_IN_USE"
     * - Contraseña débil → mensaje indicando el mínimo requerido
     * - Sin internet → error descriptivo; los datos locales ya se guardaron en Room
     *
     * @param email    Correo del usuario.
     * @param password Contraseña (en EternaMente MVP: el PIN de 6 dígitos).
     * @return [Result.Success] con el [FirebaseUser] creado, o [Result.Error].
     */
    suspend fun register(email: String, password: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            safeCall {
                suspendCancellableCoroutine { cont ->
                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { authResult ->
                            val user = authResult.user
                            if (user != null) {
                                cont.resume(user)
                            } else {
                                cont.resumeWithException(
                                    IllegalStateException("Firebase: usuario nulo tras createUser")
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            cont.resumeWithException(mapFirebaseError(e))
                        }
                        .addOnCanceledListener { cont.cancel() }
                }
            }
        }

    /**
     * Cierra la sesión de Firebase Auth.
     *
     * La sesión local (Room + DataStore) debe limpiarse por separado vía
     * [com.eternamente.app.domain.usecase.LogoutUseCase].
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall { firebaseAuth.signOut() }
    }

    // ── Migración offline → cloud ─────────────────────────────────────────────

    /**
     * Migra un usuario existente de Room a Firebase Auth.
     *
     * Estrategia:
     * 1. Intentar login — el usuario puede ya tener cuenta Firebase (si se registró
     *    online en una instalación anterior).
     * 2. Si falla con "usuario no encontrado" o "credencial inválida": crear cuenta nueva.
     * 3. Cualquier otro fallo (red, etc.) se devuelve tal cual para que el llamador
     *    lo maneje (reintento diferido, log, etc.).
     *
     * Esta función prepara la sincronización futura Room → Firestore sin interrumpir
     * el flujo offline-first actual.
     *
     * @param email    Email del usuario en Room.
     * @param password Credencial a usar en Firebase (PIN en MVP).
     * @return [Result.Success] con el [FirebaseUser] vinculado, o [Result.Error].
     */
    suspend fun migrateLocalUserToFirebase(
        email: String,
        password: String
    ): Result<FirebaseUser> {
        val loginResult = login(email, password)
        if (loginResult is Result.Success) {
            Timber.i("FirebaseAuthManager: usuario ya existe en Firebase — login OK (migración)")
            return loginResult
        }
        val errorCode = ((loginResult as Result.Error).exception as? FirebaseAuthException)?.errorCode
        return if (errorCode == "ERROR_USER_NOT_FOUND" || errorCode == "ERROR_INVALID_CREDENTIAL") {
            Timber.i("FirebaseAuthManager: creando cuenta Firebase para usuario local")
            register(email, password)
        } else {
            Timber.w(loginResult.exception, "FirebaseAuthManager: migración fallida — sin red?")
            loginResult
        }
    }

    // ── Mapeo de errores Firebase → mensajes legibles ────────────────────────

    /**
     * Convierte un [FirebaseAuthException] a una excepción con mensaje en español.
     * Si no es [FirebaseAuthException], se devuelve sin modificar.
     */
    private fun mapFirebaseError(exception: Exception): Exception {
        if (exception !is FirebaseAuthException) return exception
        val message = when (exception.errorCode) {
            "ERROR_INVALID_EMAIL"          -> "El formato del correo no es válido."
            "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL"     -> "Credenciales incorrectas. Verifica tu correo y contraseña."
            "ERROR_USER_NOT_FOUND"         -> "No existe una cuenta con este correo en Firebase."
            "ERROR_USER_DISABLED"          -> "Esta cuenta ha sido deshabilitada."
            "ERROR_EMAIL_ALREADY_IN_USE"   -> "Ya existe una cuenta con este correo en Firebase."
            "ERROR_WEAK_PASSWORD"          -> "La contraseña es demasiado débil (mínimo 6 caracteres)."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Sin conexión a internet. Tus datos locales están seguros."
            "ERROR_TOO_MANY_REQUESTS"      -> "Demasiados intentos fallidos. Intenta más tarde."
            "ERROR_OPERATION_NOT_ALLOWED"  -> "Este método de autenticación no está habilitado en Firebase."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "Ya existe una cuenta con este correo usando otro método de login."
            else -> "Error Firebase (${exception.errorCode}): ${exception.message}"
        }
        return FirebaseAuthException(exception.errorCode, message)
    }
}
