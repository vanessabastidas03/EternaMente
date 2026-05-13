package com.eternamente.app.data.local.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the SQLCipher database encryption key using the Android Keystore.
 *
 * Security model:
 * 1. A random 256-bit database passphrase is generated on first launch.
 * 2. The passphrase is encrypted with an AES-256-GCM key that lives in the
 *    Android Keystore (never exportable, backed by hardware if available).
 * 3. The IV + ciphertext blob is stored in private [android.content.SharedPreferences].
 * 4. On subsequent launches, the blob is decrypted via the Keystore key.
 *
 * The raw passphrase is held in a [ByteArray] that **must** be zeroed by the caller
 * immediately after handing it to [net.sqlcipher.database.SupportFactory].
 */
@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {
        // ── SQLCipher key (Keystore AES-GCM) ─────────────────────────────────
        const val KEYSTORE_PROVIDER     = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS      = "EternaMenteMasterKey_v1"
        const val PREFS_FILE            = "eternamente_crypto.prefs"
        const val PREF_ENCRYPTED_KEY    = "db_key_enc_v1"
        const val DB_KEY_SIZE_BYTES     = 32   // 256-bit AES key for SQLCipher
        const val GCM_IV_SIZE_BYTES     = 12
        const val GCM_TAG_BIT_LENGTH    = 128
        const val AES_GCM_NO_PADDING    = "AES/GCM/NoPadding"

        // ── PIN hashing (PBKDF2-SHA256) ───────────────────────────────────────
        const val PBKDF2_ALGORITHM       = "PBKDF2WithHmacSHA256"
        const val PBKDF2_ITERATIONS      = 100_000
        const val PBKDF2_KEY_LENGTH_BITS = 256
        const val SALT_SIZE_BYTES        = 32
    }

    /**
     * Returns the database passphrase as a raw [ByteArray], creating and persisting
     * it on the very first call.
     *
     * **Security contract**: the caller must zero the returned array
     * (`passphrase.fill(0)`) immediately after passing it to [net.sqlcipher.database.SupportFactory].
     *
     * @return 32-byte (256-bit) passphrase for SQLCipher.
     */
    fun getOrCreateDatabaseKey(): ByteArray {
        val prefs   = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val stored  = prefs.getString(PREF_ENCRYPTED_KEY, null)

        return if (stored != null) {
            decryptKey(Base64.decode(stored, Base64.NO_WRAP))
        } else {
            // First launch: generate a cryptographically random key
            val rawKey = ByteArray(DB_KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
            val blob   = encryptKey(rawKey)
            prefs.edit()
                .putString(PREF_ENCRYPTED_KEY, Base64.encodeToString(blob, Base64.NO_WRAP))
                .apply()
            rawKey
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            return (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    /** Encrypts [rawKey] and returns `IV (12 bytes) || ciphertext`. */
    private fun encryptKey(rawKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv         = cipher.iv               // GCM generates a random IV
        val ciphertext = cipher.doFinal(rawKey)
        return iv + ciphertext
    }

    /** Decrypts a blob produced by [encryptKey]. */
    private fun decryptKey(blob: ByteArray): ByteArray {
        val iv         = blob.copyOfRange(0, GCM_IV_SIZE_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_SIZE_BYTES, blob.size)
        val cipher     = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PIN hashing — PBKDF2WithHmacSHA256
    //
    // El PIN NUNCA se almacena en texto plano. Se deriva con PBKDF2-SHA256
    // usando 100 000 iteraciones (recomendación NIST SP 800-132 para 2024).
    // La comparación usa MessageDigest.isEqual (tiempo constante) para prevenir
    // ataques de timing.
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Genera un salt criptográficamente seguro de [SALT_SIZE_BYTES] bytes,
     * codificado en Base64 sin padding para almacenamiento en Room.
     */
    fun generateSalt(): String {
        val salt = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    /**
     * Deriva el hash PBKDF2-SHA256 del PIN con el [saltBase64] dado.
     *
     * @param pin        PIN de 6 dígitos en texto plano (se limpia de memoria tras el uso).
     * @param saltBase64 Salt Base64 generado con [generateSalt].
     * @return Hash Base64 (256 bits) apto para almacenamiento en Room.
     */
    fun hashPin(pin: String, saltBase64: String): String {
        val salt    = Base64.decode(saltBase64, Base64.NO_WRAP)
        val factory = javax.crypto.SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec    = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS
        )
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()    // Limpia el PIN de la memoria
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Verifica si [pin] coincide con el hash almacenado.
     *
     * Usa comparación de bytes en tiempo constante ([java.security.MessageDigest.isEqual])
     * para prevenir ataques de timing.
     *
     * @param pin               PIN ingresado (texto plano).
     * @param saltBase64        Salt almacenado (Base64).
     * @param expectedHashBase64 Hash almacenado (Base64).
     * @return `true` si el PIN es correcto.
     */
    fun verifyPin(pin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        val computed = hashPin(pin, saltBase64)
        return java.security.MessageDigest.isEqual(
            Base64.decode(computed, Base64.NO_WRAP),
            Base64.decode(expectedHashBase64, Base64.NO_WRAP)
        )
    }

}
