package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface KeystoreKeyWrapper {
    fun wrapWithDeviceKey(dek: SensitiveBytes, createdAtEpochMillis: Long): KeyEnvelope
    fun unwrapWithDeviceKey(envelope: KeyEnvelope): SensitiveBytes
    fun deleteDeviceKey()

    fun createBiometricEncryptCipher(): Cipher
    fun createBiometricDecryptCipher(nonce: ByteArray): Cipher
    fun wrapWithBiometricCipher(
        authenticatedCipher: Cipher,
        dek: SensitiveBytes,
        createdAtEpochMillis: Long
    ): KeyEnvelope
    fun unwrapWithBiometricCipher(
        authenticatedCipher: Cipher,
        envelope: KeyEnvelope
    ): SensitiveBytes
    fun deleteBiometricKey()
}

/**
 * Uses Android Keystore ("AndroidKeyStore") in production, with a fallback private key file
 * only when running under unit-test / container environments where AndroidKeyStore provider is absent.
 */
class AndroidKeystoreKeyWrapper(
    private val fallbackDirectory: File? = null
) : KeystoreKeyWrapper {

    private val secureRandom = SecureRandom()

    override fun wrapWithDeviceKey(dek: SensitiveBytes, createdAtEpochMillis: Long): KeyEnvelope {
        val secretKey = getOrCreateDeviceKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val nonce = cipher.iv
        val prototype = KeyEnvelope(
            type = EnvelopeType.DEVICE_KEYSTORE,
            nonce = nonce,
            ciphertextWithTag = ByteArray(0),
            keystoreAlias = DEVICE_KEY_ALIAS,
            createdAtEpochMillis = createdAtEpochMillis
        )
        val rawDek = dek.copyBytes()
        try {
            cipher.updateAAD(prototype.canonicalAad())
            val ciphertext = cipher.doFinal(rawDek)
            return prototype.copy(ciphertextWithTag = ciphertext)
        } finally {
            rawDek.clearBytes()
        }
    }

    override fun unwrapWithDeviceKey(envelope: KeyEnvelope): SensitiveBytes {
        require(envelope.type == EnvelopeType.DEVICE_KEYSTORE) {
            "Expected DEVICE_KEYSTORE envelope."
        }
        val secretKey = loadDeviceKey()
            ?: throw IllegalStateException("Device Keystore key is missing.")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(KeyEnvelope.GCM_TAG_BITS, envelope.nonce)
        )
        cipher.updateAAD(envelope.canonicalAad())
        val plaintext = cipher.doFinal(envelope.ciphertextWithTag)
        return SensitiveBytes(plaintext)
    }

    override fun deleteDeviceKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(DEVICE_KEY_ALIAS)) {
                keyStore.deleteEntry(DEVICE_KEY_ALIAS)
            }
        }
        fallbackDeviceKeyFile()?.delete()
    }

    override fun createBiometricEncryptCipher(): Cipher {
        val secretKey = recreateBiometricKey()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
    }

    override fun createBiometricDecryptCipher(nonce: ByteArray): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Biometric Keystore key is not enrolled.")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(KeyEnvelope.GCM_TAG_BITS, nonce))
        }
    }

    override fun wrapWithBiometricCipher(
        authenticatedCipher: Cipher,
        dek: SensitiveBytes,
        createdAtEpochMillis: Long
    ): KeyEnvelope {
        val nonce = authenticatedCipher.iv
        val prototype = KeyEnvelope(
            type = EnvelopeType.BIOMETRIC_KEYSTORE,
            nonce = nonce,
            ciphertextWithTag = ByteArray(0),
            keystoreAlias = BIOMETRIC_KEY_ALIAS,
            createdAtEpochMillis = createdAtEpochMillis
        )
        val rawDek = dek.copyBytes()
        try {
            authenticatedCipher.updateAAD(prototype.canonicalAad())
            val ciphertext = authenticatedCipher.doFinal(rawDek)
            return prototype.copy(ciphertextWithTag = ciphertext)
        } finally {
            rawDek.clearBytes()
        }
    }

    override fun unwrapWithBiometricCipher(
        authenticatedCipher: Cipher,
        envelope: KeyEnvelope
    ): SensitiveBytes {
        require(envelope.type == EnvelopeType.BIOMETRIC_KEYSTORE) {
            "Expected BIOMETRIC_KEYSTORE envelope."
        }
        authenticatedCipher.updateAAD(envelope.canonicalAad())
        val plaintext = authenticatedCipher.doFinal(envelope.ciphertextWithTag)
        return SensitiveBytes(plaintext)
    }

    override fun deleteBiometricKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        }
    }

    private fun getOrCreateDeviceKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(DEVICE_KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                DEVICE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            // Fallback for JVM/Robolectric environment where AndroidKeyStore provider is unavailable
            val file = fallbackDeviceKeyFile() ?: throw e
            if (file.exists()) {
                val bytes = file.readBytes()
                return SecretKeySpec(bytes, "AES")
            }
            val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            return SecretKeySpec(bytes, "AES")
        }
    }

    private fun loadDeviceKey(): SecretKey? {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(DEVICE_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        } catch (_: Exception) {
            // Ignore and check fallback
        }
        val file = fallbackDeviceKeyFile()
        if (file != null && file.exists()) {
            return SecretKeySpec(file.readBytes(), "AES")
        }
        return null
    }

    private fun fallbackDeviceKeyFile(): File? =
        fallbackDirectory?.let { File(it, ".device_wrap_key.bin") }

    private fun recreateBiometricKey(): SecretKey {
        deleteBiometricKey()
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEVICE_KEY_ALIAS = "dao_device_envelope_key_v1"
        const val BIOMETRIC_KEY_ALIAS = "dao_biometric_envelope_key_v1"
    }
}
