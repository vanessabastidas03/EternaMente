package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.data.local.crypto.CryptoManager
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.AuthException
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.AuthRepository
import com.eternamente.app.domain.repository.GamificationRepository
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
 * Tests unitarios para [RegisterUserUseCase].
 *
 * Cubre: validaciones de entrada, hashing de PIN, creación de usuario,
 * unicidad de correo y persistencia de credenciales.
 */
class RegisterUserUseCaseTest {

    private lateinit var useCase: RegisterUserUseCase

    private val userRepository: UserRepository             = mockk()
    private val authRepository: AuthRepository             = mockk()
    private val gamificationRepository: GamificationRepository = mockk()
    private val cryptoManager: CryptoManager               = mockk()
    private val userPrefsRepository: UserPreferencesRepository = mockk()

    private val validUser = User(
        id             = "test-uuid",
        email          = "ana@example.com",
        name           = "Ana García",
        age            = 0,
        educationYears = 0,
        gender         = "",
        createdAt      = 1_000_000L,
        consentGivenAt = null
    )

    @Before
    fun setUp() {
        useCase = RegisterUserUseCase(
            userRepository, authRepository, gamificationRepository,
            cryptoManager, userPrefsRepository
        )

        // Default stubs — éxito
        coEvery { userRepository.getUserByEmail(any()) } returns Result.Success(null)
        coEvery { userRepository.registerUser(any()) }   returns Result.Success(validUser)
        coEvery { authRepository.saveCredentials(any()) } returns Result.Success(Unit)
        coEvery { gamificationRepository.initializeProfile(any()) } returns Result.Success(mockk())
        coEvery { cryptoManager.generateSalt() }         returns "salt_base64"
        coEvery { cryptoManager.hashPin(any(), any()) }  returns "hash_base64"
        coEvery { userPrefsRepository.updateCurrentUserId(any()) } just Runs
    }

    // ── Casos de éxito ────────────────────────────────────────────────────────

    @Test
    fun `registro exitoso retorna Result Success con el usuario`() = runTest {
        val result = useCase("Ana García", "ana@example.com", "123456", "123456")
        assertTrue("Esperaba Result.Success", result is Result.Success)
    }

    @Test
    fun `registro exitoso llama a hashPin y saveCredentials`() = runTest {
        useCase("Ana García", "ana@example.com", "123456", "123456")
        coVerify(exactly = 1) { cryptoManager.hashPin("123456", "salt_base64") }
        coVerify(exactly = 1) { authRepository.saveCredentials(any()) }
    }

    @Test
    fun `registro exitoso almacena userId en DataStore`() = runTest {
        useCase("Ana García", "ana@example.com", "123456", "123456")
        coVerify(exactly = 1) { userPrefsRepository.updateCurrentUserId(any()) }
    }

    @Test
    fun `el correo se normaliza a minusculas`() = runTest {
        useCase("Ana", "ANA@EXAMPLE.COM", "123456", "123456")
        coVerify { userRepository.getUserByEmail("ana@example.com") }
    }

    // ── Validaciones de formato ───────────────────────────────────────────────

    @Test
    fun `nombre vacio retorna AuthException`() = runTest {
        val result = useCase("", "ana@example.com", "123456", "123456")
        assertTrue(result is Result.Error)
    }

    @Test
    fun `correo invalido retorna InvalidEmail`() = runTest {
        val result = useCase("Ana", "no-es-correo", "123456", "123456")
        assertTrue(result is Result.Error)
        assertEquals(AuthException.InvalidEmail, (result as Result.Error).exception)
    }

    @Test
    fun `pin con menos de 6 digitos retorna error`() = runTest {
        val result = useCase("Ana", "ana@example.com", "12345", "12345")
        assertTrue(result is Result.Error)
    }

    @Test
    fun `pin con letras retorna error`() = runTest {
        val result = useCase("Ana", "ana@example.com", "12A456", "12A456")
        assertTrue(result is Result.Error)
    }

    @Test
    fun `pins no coincidentes retorna PinMismatch`() = runTest {
        val result = useCase("Ana", "ana@example.com", "123456", "654321")
        assertTrue(result is Result.Error)
        assertEquals(AuthException.PinMismatch, (result as Result.Error).exception)
    }

    // ── Unicidad de correo ─────────────────────────────────────────────────────

    @Test
    fun `correo ya registrado retorna EmailAlreadyExists`() = runTest {
        coEvery { userRepository.getUserByEmail("ana@example.com") } returns
            Result.Success(validUser) // Ya existe

        val result = useCase("Ana", "ana@example.com", "123456", "123456")
        assertTrue(result is Result.Error)
        assertEquals(AuthException.EmailAlreadyExists, (result as Result.Error).exception)
    }

    // ── Errores de persistencia ────────────────────────────────────────────────

    @Test
    fun `error en UserRepository retorna Result Error`() = runTest {
        coEvery { userRepository.registerUser(any()) } returns
            Result.Error(Exception("DB error"))

        val result = useCase("Ana", "ana@example.com", "123456", "123456")
        assertTrue(result is Result.Error)
    }

    @Test
    fun `error en AuthRepository retorna Result Error`() = runTest {
        coEvery { authRepository.saveCredentials(any()) } returns
            Result.Error(Exception("Credentials DB error"))

        val result = useCase("Ana", "ana@example.com", "123456", "123456")
        assertTrue(result is Result.Error)
    }
}
