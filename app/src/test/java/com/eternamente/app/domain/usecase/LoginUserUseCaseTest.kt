package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.data.local.crypto.CryptoManager
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.AuthException
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.model.UserCredentials
import com.eternamente.app.domain.repository.AuthRepository
import com.eternamente.app.domain.repository.UserRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [LoginUserUseCase].
 *
 * Cubre: login exitoso, usuario no encontrado, PIN incorrecto,
 * política de bloqueo tras 5 intentos y cuenta bloqueada activamente.
 */
class LoginUserUseCaseTest {

    private lateinit var useCase: LoginUserUseCase

    private val userRepository: UserRepository             = mockk()
    private val authRepository: AuthRepository             = mockk()
    private val cryptoManager: CryptoManager               = mockk()
    private val userPrefsRepository: UserPreferencesRepository = mockk()

    private val testUser = User(
        id             = "user-123",
        email          = "ana@example.com",
        name           = "Ana García",
        age            = 72,
        educationYears = 12,
        gender         = "Mujer",
        createdAt      = 1_000_000L,
        consentGivenAt = 1_000_001L
    )

    private val testCredentials = UserCredentials(
        userId               = "user-123",
        pinHash              = "correct_hash",
        pinSalt              = "salt_base64",
        failedLoginAttempts  = 0,
        lockedUntil          = null
    )

    @Before
    fun setUp() {
        useCase = LoginUserUseCase(
            userRepository, authRepository, cryptoManager, userPrefsRepository
        )

        coEvery { userRepository.getUserByEmail("ana@example.com") } returns Result.Success(testUser)
        coEvery { authRepository.getCredentialsByUserId("user-123") } returns Result.Success(testCredentials)
        coEvery { cryptoManager.verifyPin("123456", "salt_base64", "correct_hash") } returns true
        coEvery { authRepository.updateFailedAttempts(any(), any(), any()) } returns Result.Success(Unit)
        coEvery { userPrefsRepository.updateCurrentUserId(any()) } just Runs
    }

    // ── Login exitoso ─────────────────────────────────────────────────────────

    @Test
    fun `login exitoso retorna el usuario`() = runTest {
        val result = useCase("ana@example.com", "123456")
        assertTrue(result is Result.Success)
        assertEquals(testUser, (result as Result.Success).data)
    }

    @Test
    fun `login exitoso almacena userId en DataStore`() = runTest {
        useCase("ana@example.com", "123456")
        coVerify(exactly = 1) { userPrefsRepository.updateCurrentUserId("user-123") }
    }

    @Test
    fun `login exitoso NO actualiza failed attempts si era 0`() = runTest {
        useCase("ana@example.com", "123456")
        coVerify(exactly = 0) { authRepository.updateFailedAttempts(any(), any(), any()) }
    }

    @Test
    fun `login exitoso resetea intentos fallidos previos`() = runTest {
        coEvery { authRepository.getCredentialsByUserId("user-123") } returns
            Result.Success(testCredentials.copy(failedLoginAttempts = 2))

        useCase("ana@example.com", "123456")
        coVerify { authRepository.updateFailedAttempts("user-123", 0, null) }
    }

    @Test
    fun `correo se normaliza a minusculas`() = runTest {
        useCase("ANA@EXAMPLE.COM", "123456")
        coVerify { userRepository.getUserByEmail("ana@example.com") }
    }

    // ── Usuario no encontrado ─────────────────────────────────────────────────

    @Test
    fun `correo no registrado retorna UserNotFound`() = runTest {
        coEvery { userRepository.getUserByEmail(any()) } returns Result.Success(null)

        val result = useCase("noexiste@example.com", "123456")
        assertTrue(result is Result.Error)
        assertEquals(AuthException.UserNotFound, (result as Result.Error).exception)
    }

    // ── PIN incorrecto ────────────────────────────────────────────────────────

    @Test
    fun `pin incorrecto retorna InvalidPin con intentos restantes`() = runTest {
        coEvery { cryptoManager.verifyPin("000000", "salt_base64", "correct_hash") } returns false

        val result = useCase("ana@example.com", "000000")
        assertTrue(result is Result.Error)
        val exception = (result as Result.Error).exception
        assertTrue(exception is AuthException.InvalidPin)
        assertEquals(
            LoginUserUseCase.MAX_FAILED_ATTEMPTS - 1,
            (exception as AuthException.InvalidPin).attemptsRemaining
        )
    }

    @Test
    fun `pin incorrecto incrementa failedLoginAttempts`() = runTest {
        coEvery { cryptoManager.verifyPin(any(), any(), any()) } returns false

        useCase("ana@example.com", "000000")
        coVerify { authRepository.updateFailedAttempts("user-123", 1, null) }
    }

    // ── Bloqueo de cuenta ─────────────────────────────────────────────────────

    @Test
    fun `5to intento fallido bloquea la cuenta 30 minutos`() = runTest {
        coEvery { authRepository.getCredentialsByUserId("user-123") } returns
            Result.Success(testCredentials.copy(failedLoginAttempts = 4)) // 4 previos → 5to ahora
        coEvery { cryptoManager.verifyPin(any(), any(), any()) } returns false

        val result = useCase("ana@example.com", "000000")
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is AuthException.AccountLocked)

        // Verifica que lockedUntil se establece (no null)
        coVerify { authRepository.updateFailedAttempts("user-123", 5, match { it != null }) }
    }

    @Test
    fun `cuenta activamente bloqueada retorna AccountLocked sin verificar PIN`() = runTest {
        val futureTime = System.currentTimeMillis() + 20 * 60_000L // 20 min en el futuro
        coEvery { authRepository.getCredentialsByUserId("user-123") } returns
            Result.Success(testCredentials.copy(lockedUntil = futureTime))

        val result = useCase("ana@example.com", "123456")
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is AuthException.AccountLocked)

        // NO debe verificar el PIN cuando está bloqueada
        coVerify(exactly = 0) { cryptoManager.verifyPin(any(), any(), any()) }
    }

    @Test
    fun `bloqueo expirado permite intentar login`() = runTest {
        val pastTime = System.currentTimeMillis() - 1_000L // Ya expiró
        coEvery { authRepository.getCredentialsByUserId("user-123") } returns
            Result.Success(testCredentials.copy(lockedUntil = pastTime))

        val result = useCase("ana@example.com", "123456")
        assertTrue("El login debe proceder cuando el bloqueo expiró", result is Result.Success)
    }
}
