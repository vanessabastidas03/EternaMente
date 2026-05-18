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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.junit5.MockKExtension

@ExtendWith(MockKExtension::class)
@DisplayName("RegisterUserUseCase")
class RegisterUserUseCaseTest {

    private val userRepository: UserRepository               = mockk()
    private val authRepository: AuthRepository               = mockk()
    private val gamificationRepository: GamificationRepository = mockk()
    private val cryptoManager: CryptoManager                 = mockk()
    private val userPrefsRepository: UserPreferencesRepository = mockk()

    private lateinit var useCase: RegisterUserUseCase

    private val existingUser = User(
        id = "uid-existing", email = "ana@example.com", name = "Ana García",
        age = 0, educationYears = 0, gender = "", createdAt = 1_000_000L, consentGivenAt = null
    )

    @BeforeEach
    fun setUp() {
        useCase = RegisterUserUseCase(
            userRepository, authRepository, gamificationRepository,
            cryptoManager, userPrefsRepository
        )
        // Happy-path stubs
        coEvery { userRepository.getUserByEmail(any()) }          returns Result.Success(null)
        coEvery { userRepository.registerUser(any()) }            returns Result.Success(existingUser)
        coEvery { authRepository.saveCredentials(any()) }         returns Result.Success(Unit)
        coEvery { gamificationRepository.initializeProfile(any()) } returns Result.Success(mockk())
        coEvery { cryptoManager.generateSalt() }                  returns "salt64"
        coEvery { cryptoManager.hashPin(any(), any()) }           returns "hash64"
        coEvery { userPrefsRepository.updateCurrentUserId(any()) } just Runs
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Éxito")
    inner class Success {

        @Test
        fun `devuelve Result Success con el usuario creado`() = runTest {
            val result = useCase("Ana García", "ana@example.com", "123456", "123456")
            assertTrue(result is Result.Success)
        }

        @Test
        fun `normaliza el correo a minúsculas antes de verificar unicidad`() = runTest {
            useCase("Ana", "ANA@EXAMPLE.COM", "123456", "123456")
            coVerify { userRepository.getUserByEmail("ana@example.com") }
        }

        @Test
        fun `llama a hashPin y saveCredentials exactamente una vez`() = runTest {
            useCase("Ana García", "ana@example.com", "123456", "123456")
            coVerify(exactly = 1) { cryptoManager.hashPin("123456", "salt64") }
            coVerify(exactly = 1) { authRepository.saveCredentials(any()) }
        }

        @Test
        fun `almacena el userId en DataStore tras el registro`() = runTest {
            useCase("Ana García", "ana@example.com", "123456", "123456")
            coVerify(exactly = 1) { userPrefsRepository.updateCurrentUserId(any()) }
        }

        @Test
        fun `inicializa el perfil de gamificación`() = runTest {
            useCase("Ana García", "ana@example.com", "123456", "123456")
            coVerify(exactly = 1) { gamificationRepository.initializeProfile(any()) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Correo duplicado")
    inner class DuplicateEmail {

        @Test
        fun `correo ya registrado devuelve EmailAlreadyExists`() = runTest {
            coEvery { userRepository.getUserByEmail("ana@example.com") } returns
                Result.Success(existingUser)

            val result = useCase("Ana", "ana@example.com", "123456", "123456")

            assertInstanceOf(Result.Error::class.java, result)
            assertEquals(
                AuthException.EmailAlreadyExists,
                (result as Result.Error).exception
            )
        }

        @Test
        fun `correo duplicado no llama a registerUser ni saveCredentials`() = runTest {
            coEvery { userRepository.getUserByEmail(any()) } returns Result.Success(existingUser)

            useCase("Ana", "ana@example.com", "123456", "123456")

            coVerify(exactly = 0) { userRepository.registerUser(any()) }
            coVerify(exactly = 0) { authRepository.saveCredentials(any()) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("PIN demasiado corto / formato inválido")
    inner class InvalidPin {

        @Test
        fun `pin de 5 dígitos devuelve error`() = runTest {
            val result = useCase("Ana", "ana@example.com", "12345", "12345")
            assertTrue(result is Result.Error)
        }

        @Test
        fun `pin de 7 dígitos devuelve error`() = runTest {
            val result = useCase("Ana", "ana@example.com", "1234567", "1234567")
            assertTrue(result is Result.Error)
        }

        @Test
        fun `pin con letras devuelve error`() = runTest {
            val result = useCase("Ana", "ana@example.com", "12A456", "12A456")
            assertTrue(result is Result.Error)
        }

        @Test
        fun `pins que no coinciden devuelven PinMismatch`() = runTest {
            val result = useCase("Ana", "ana@example.com", "123456", "654321")
            assertInstanceOf(Result.Error::class.java, result)
            assertEquals(AuthException.PinMismatch, (result as Result.Error).exception)
        }

        @Test
        fun `pin vacío devuelve error`() = runTest {
            val result = useCase("Ana", "ana@example.com", "", "")
            assertTrue(result is Result.Error)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Errores de validación de campos")
    inner class FieldValidation {

        @Test
        fun `nombre vacío devuelve error`() = runTest {
            val result = useCase("", "ana@example.com", "123456", "123456")
            assertTrue(result is Result.Error)
        }

        @Test
        fun `correo con formato inválido devuelve InvalidEmail`() = runTest {
            val result = useCase("Ana", "no-es-correo", "123456", "123456")
            assertInstanceOf(Result.Error::class.java, result)
            assertEquals(AuthException.InvalidEmail, (result as Result.Error).exception)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Errores de persistencia")
    inner class PersistenceErrors {

        @Test
        fun `fallo en UserRepository devuelve Result Error`() = runTest {
            coEvery { userRepository.registerUser(any()) } returns Result.Error(Exception("DB error"))
            val result = useCase("Ana", "ana@example.com", "123456", "123456")
            assertTrue(result is Result.Error)
        }

        @Test
        fun `fallo en AuthRepository devuelve Result Error`() = runTest {
            coEvery { authRepository.saveCredentials(any()) } returns
                Result.Error(Exception("credentials error"))
            val result = useCase("Ana", "ana@example.com", "123456", "123456")
            assertTrue(result is Result.Error)
        }
    }
}
