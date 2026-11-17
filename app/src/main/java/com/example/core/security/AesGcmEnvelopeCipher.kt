package com.example.core.security

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class EnvelopeType {
    DEVICE_KEYSTORE,
    MASTER_PASSWORD,
    BIOMETRIC_KEYSTORE,
    RECOVERY_KEY
}

data class KeyEnvelope(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val type: EnvelopeType,
    val cipherAlgorithm: String = CIPHER_AES_256_GCM,
    val nonce: ByteArray,
    val ciphertextWithTag: ByteArray,
    val kdfParameters: KdfParameters? = null,
    val salt: ByteArray? = null,
    val keystoreAlias: String? = null,
    val createdAtEpochMillis: Long
) {
    /**
     * Canonical Associated Authenticated Data (AAD) binding the envelope metadata
     * to the GCM authentication tag so header tampering is detected on decrypt.
     */
    fun canonicalAad(): ByteArray {
        val kdfPart = kdfParameters?.let {
            "${it.algorithm}:${it.version}:${it.memoryKiB}:${it.iterations}:${it.parallelism}:${it.outputLengthBytes}"
        } ?: "none"
        val aliasPart = keystoreAlias ?: "none"
        val canonical = "dao-envelope|v=$formatVersion|type=${type.name}|cipher=$cipherAlgorithm|kdf=$kdfPart|alias=$aliasPart"
        return canonical.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val CIPHER_AES_256_GCM = "AES-256-GCM"
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val DEK_BYTES = 32
    }
}

class AesGcmEnvelopeCipher(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun wrapDekWithRawKey(
        dek: SensitiveBytes,
        kek: SensitiveBytes,
        type: EnvelopeType,
        kdfParameters: KdfParameters? = null,
        salt: ByteArray? = null,
        createdAtEpochMillis: Long = System.currentTimeMillis()
    ): KeyEnvelope {
        val nonce = ByteArray(KeyEnvelope.GCM_NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val prototype = KeyEnvelope(
            type = type,
            nonce = nonce,
            ciphertextWithTag = ByteArray(0),
            kdfParameters = kdfParameters,
            salt = salt,
            createdAtEpochMillis = createdAtEpochMillis
        )
        val rawDek = dek.copyBytes()
        val rawKek = kek.copyBytes()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(rawKek, "AES")
            val gcmSpec = GCMParameterSpec(KeyEnvelope.GCM_TAG_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(prototype.canonicalAad())
            val ciphertext = cipher.doFinal(rawDek)
            return prototype.copy(ciphertextWithTag = ciphertext)
        } finally {
            rawDek.clearBytes()
            rawKek.clearBytes()
        }
    }

    fun unwrapDekWithRawKey(
        envelope: KeyEnvelope,
        kek: SensitiveBytes
    ): SensitiveBytes {
        validateEnvelopeStructure(envelope)
        val rawKek = kek.copyBytes()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(rawKek, "AES")
            val gcmSpec = GCMParameterSpec(KeyEnvelope.GCM_TAG_BITS, envelope.nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(envelope.canonicalAad())
            val plaintext = cipher.doFinal(envelope.ciphertextWithTag)
            if (plaintext.size != KeyEnvelope.DEK_BYTES) {
                plaintext.clearBytes()
                throw GeneralSecurityException("Unwrapped DEK has unexpected length.")
            }
            return SensitiveBytes(plaintext)
        } finally {
            rawKek.clearBytes()
        }
    }

    fun validateEnvelopeStructure(envelope: KeyEnvelope) {
        require(envelope.formatVersion == KeyEnvelope.CURRENT_FORMAT_VERSION) {
            "Unsupported envelope format version: ${envelope.formatVersion}"
        }
        require(envelope.cipherAlgorithm == KeyEnvelope.CIPHER_AES_256_GCM) {
            "Unsupported envelope cipher: ${envelope.cipherAlgorithm}"
        }
        require(envelope.nonce.size == KeyEnvelope.GCM_NONCE_BYTES) {
            "Invalid GCM nonce size: ${envelope.nonce.size}"
        }
        require(envelope.ciphertextWithTag.size == KeyEnvelope.DEK_BYTES + KeyEnvelope.GCM_TAG_BITS / 8) {
            "Invalid wrapped DEK ciphertext length."
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
