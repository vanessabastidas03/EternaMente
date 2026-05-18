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
import io.mockk.junit5.MockKExtension
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

@ExtendWith(MockKExtension::class)
@DisplayName("LoginUserUseCase")
class LoginUserUseCaseTest {

    private val userRepository: UserRepository               = mockk()
    private val authRepository: AuthRepository               = mockk()
    private val cryptoManager: CryptoManager                 = mockk()
    private val userPrefsRepository: UserPreferencesRepository = mockk()

    private lateinit var useCase: LoginUserUseCase

    private val testUser = User(
        id = "user-123", email = "ana@example.com", name = "Ana García",
        age = 72, educationYears = 12, gender = "Mujer",
        createdAt = 1_000_000L, consentGivenAt = 1_000_001L
    )

    private val validCredentials = UserCredentials(
        userId = "user-123",
        pinHash = "correct_hash",
        pinSalt = "salt64",
        failedLoginAttempts = 0,
        lockedUntil = null
    )

    @BeforeEach
    fun setUp() {
        useCase = LoginUserUseCase(userRepository, authRepository, cryptoManager, userPrefsRepository)

        coEvery { userRepository.getUserByEmail("ana@example.com") } returns Result.Success(testUser)
        coEvery { authRepository.getCredentialsByUserId("user-123") } returns Result.Success(validCredentials)
        coEvery { cryptoManager.verifyPin("123456", "salt64", "correct_hash") } returns true
        coEvery { authRepository.updateFailedAttempts(any(), any(), any()) } returns Result.Success(Unit)
        coEvery { userPrefsRepository.updateCurrentUserId(any()) } just Runs
        coEvery { userPrefsRepository.updateIsLoggedIn(any()) } just Runs
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Login exitoso")
    inner class LoginSuccess {

        @Test
        fun `devuelve el usuario autenticado`() = runTest {
            val result = useCase("ana@example.com", "123456")
            assertInstanceOf(Result.Success::class.java, result)
            assertEquals(testUser, (result as Result.Success).data)
        }

        @Test
        fun `normaliza el correo a minúsculas`() = runTest {
            useCase("ANA@EXAMPLE.COM", "123456")
            coVerify { userRepository.getUserByEmail("ana@example.com") }
        }

        @Test
        fun `almacena userId en DataStore al tener éxito`() = runTest {
            useCase("ana@example.com", "123456")
            coVerify(exactly = 1) { userPrefsRepository.updateCurrentUserId("user-123") }
        }

        @Test
        fun `no actualiza failedAttempts cuando ya era cero`() = runTest {
            useCase("ana@example.com", "123456")
            coVerify(exactly = 0) { authRepository.updateFailedAttempts(any(), any(), any()) }
        }

        @Test
        fun `resetea intentos fallidos previos al tener éxito`() = runTest {
            coEvery { authRepository.getCredentialsByUserId("user-123") } returns
                Result.Success(validCredentials.copy(failedLoginAttempts = 3))

            useCase("ana@example.com", "123456")

            coVerify { authRepository.updateFailedAttempts("user-123", 0, null) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("PIN incorrecto")
    inner class WrongPin {

        @Test
        fun `devuelve InvalidPin con intentos restantes`() = runTest {
            coEvery { cryptoManager.verifyPin("000000", "salt64", "correct_hash") } returns false

            val result = useCase("ana@example.com", "000000")

            assertInstanceOf(Result.Error::class.java, result)
            val ex = (result as Result.Error).exception
            assertInstanceOf(AuthException.InvalidPin::class.java, ex)
            assertEquals(
                LoginUserUseCase.MAX_FAILED_ATTEMPTS - 1,
                (ex as AuthException.InvalidPin).attemptsRemaining
            )
        }

        @Test
        fun `incrementa el contador de intentos fallidos en Room`() = runTest {
            coEvery { cryptoManager.verifyPin(any(), any(), any()) } returns false

            useCase("ana@example.com", "000000")

            coVerify { authRepository.updateFailedAttempts("user-123", 1, null) }
        }

        @Test
        fun `el quinto fallo bloquea la cuenta 30 minutos`() = runTest {
            coEvery { authRepository.getCredentialsByUserId("user-123") } returns
                Result.Success(validCredentials.copy(failedLoginAttempts = 4))
            coEvery { cryptoManager.verifyPin(any(), any(), any()) } returns false

            val result = useCase("ana@example.com", "000000")

            assertInstanceOf(Result.Error::class.java, result)
            assertInstanceOf(AuthException.AccountLocked::class.java, (result as Result.Error).exception)
            coVerify { authRepository.updateFailedAttempts("user-123", 5, match { it != null }) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Usuario no existe")
    inner class UserNotFound {

        @Test
        fun `correo no registrado devuelve UserNotFound`() = runTest {
            coEvery { userRepository.getUserByEmail(any()) } returns Result.Success(null)

            val result = useCase("noexiste@example.com", "123456")

            assertInstanceOf(Result.Error::class.java, result)
            assertEquals(AuthException.UserNotFound, (result as Result.Error).exception)
        }

        @Test
        fun `usuario no encontrado no intenta verificar PIN`() = runTest {
            coEvery { userRepository.getUserByEmail(any()) } returns Result.Success(null)

            useCase("noexiste@example.com", "123456")

            coVerify(exactly = 0) { cryptoManager.verifyPin(any(), any(), any()) }
        }

        @Test
        fun `credenciales no encontradas devuelven UserNotFound`() = runTest {
            coEvery { authRepository.getCredentialsByUserId("user-123") } returns
                Result.Success(null)

            val result = useCase("ana@example.com", "123456")

            assertInstanceOf(Result.Error::class.java, result)
            assertEquals(AuthException.UserNotFound, (result as Result.Error).exception)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Cuenta bloqueada")
    inner class AccountLocked {

        @Test
        fun `cuenta bloqueada activamente devuelve AccountLocked sin verificar PIN`() = runTest {
            val futureMs = System.currentTimeMillis() + 20 * 60_000L
            coEvery { authRepository.getCredentialsByUserId("user-123") } returns
                Result.Success(validCredentials.copy(lockedUntil = futureMs))

            val result = useCase("ana@example.com", "123456")

            assertInstanceOf(Result.Error::class.java, result)
            assertInstanceOf(AuthException.AccountLocked::class.java, (result as Result.Error).exception)
            coVerify(exactly = 0) { cryptoManager.verifyPin(any(), any(), any()) }
        }

        @Test
        fun `bloqueo expirado permite el intento de login`() = runTest {
            val pastMs = System.currentTimeMillis() - 1_000L
            coEvery { authRepository.getCredentialsByUserId("user-123") } returns
                Result.Success(validCredentials.copy(lockedUntil = pastMs))

            val result = useCase("ana@example.com", "123456")

            assertTrue(result is Result.Success)
        }

        @Test
        fun `AccountLocked incluye minutos restantes positivos`() = runTest {
            val futureMs = System.currentTimeMillis() + 25 * 60_000L
            coEvery { authRepository.getCredentialsByUserId("user-123") } returns
                Result.Success(validCredentials.copy(lockedUntil = futureMs))

            val result = useCase("ana@example.com", "123456")

            val ex = (result as Result.Error).exception as AuthException.AccountLocked
            assertTrue(ex.minutesRemaining > 0)
        }
    }
}
