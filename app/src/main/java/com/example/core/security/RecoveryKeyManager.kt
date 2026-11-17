package com.example.core.security

import java.security.MessageDigest
import java.security.SecureRandom

class RecoveryKeyManager(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    /**
     * Generates a 256-bit random recovery secret and formats it as a human-enterable
     * Base32 string with a 2-byte SHA-256 checksum (55 Base32 chars split into 7-char groups).
     */
    fun generateRecoveryCode(): CharArray {
        val rawSecret = ByteArray(RECOVERY_SECRET_BYTES)
        secureRandom.nextBytes(rawSecret)
        try {
            val checksum = computeChecksum(rawSecret)
            val combined = ByteArray(RECOVERY_SECRET_BYTES + CHECKSUM_BYTES)
            System.arraycopy(rawSecret, 0, combined, 0, RECOVERY_SECRET_BYTES)
            System.arraycopy(checksum, 0, combined, RECOVERY_SECRET_BYTES, CHECKSUM_BYTES)
            val encoded = encodeBase32(combined)
            combined.clearBytes()
            return formatGroups(encoded)
        } finally {
            rawSecret.clearBytes()
        }
    }

    /**
     * Validates the recovery code checksum and derives a 256-bit KEK for unwrapping/wrapping the DEK.
     */
    fun deriveKekFromRecoveryCode(recoveryCode: CharArray): SensitiveBytes {
        val normalized = normalizeCode(recoveryCode)
        try {
            require(normalized.size == EXPECTED_BASE32_CHARS) {
                "Recovery key must be $EXPECTED_BASE32_CHARS Base32 characters."
            }
            val combined = decodeBase32(normalized)
            try {
                require(combined.size == RECOVERY_SECRET_BYTES + CHECKSUM_BYTES) {
                    "Decoded recovery key has invalid byte length."
                }
                val secret = combined.copyOfRange(0, RECOVERY_SECRET_BYTES)
                val providedChecksum = combined.copyOfRange(
                    RECOVERY_SECRET_BYTES,
                    RECOVERY_SECRET_BYTES + CHECKSUM_BYTES
                )
                val expectedChecksum = computeChecksum(secret)
                if (!MessageDigest.isEqual(providedChecksum, expectedChecksum)) {
                    secret.clearBytes()
                    throw IllegalArgumentException("Recovery key checksum verification failed.")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update("dao-recovery-kek-v1".toByteArray(Charsets.UTF_8))
                val kekBytes = digest.digest(secret)
                secret.clearBytes()
                return SensitiveBytes(kekBytes)
            } finally {
                combined.clearBytes()
            }
        } finally {
            normalized.clearChars()
        }
    }

    private fun computeChecksum(secret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(secret)
        return digest.copyOfRange(0, CHECKSUM_BYTES)
    }

    private fun normalizeCode(input: CharArray): CharArray {
        val buffer = CharArray(input.size)
        var count = 0
        for (ch in input) {
            if (ch == '-' || ch.isWhitespace()) continue
            buffer[count++] = ch.uppercaseChar()
        }
        val result = buffer.copyOf(count)
        buffer.clearChars()
        return result
    }

    private fun formatGroups(encoded: CharArray): CharArray {
        val out = StringBuilder(encoded.size + 8)
        for (i in encoded.indices) {
            if (i > 0 && i % 7 == 0) out.append('-')
            out.append(encoded[i])
        }
        val chars = CharArray(out.length)
        out.getChars(0, out.length, chars, 0)
        encoded.clearChars()
        return chars
    }

    private fun encodeBase32(bytes: ByteArray): CharArray {
        val out = CharArray((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        var outIndex = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                bitsLeft -= 5
                out[outIndex++] = BASE32_ALPHABET[index]
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            out[outIndex++] = BASE32_ALPHABET[index]
        }
        return out
    }

    private fun decodeBase32(chars: CharArray): ByteArray {
        val out = ByteArray(RECOVERY_SECRET_BYTES + CHECKSUM_BYTES)
        var buffer = 0
        var bitsLeft = 0
        var outIndex = 0
        for (c in chars) {
            val value = BASE32_ALPHABET.indexOf(c)
            require(value >= 0) { "Invalid character in recovery key." }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                if (outIndex < out.size) {
                    out[outIndex++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                }
                bitsLeft -= 8
            }
        }
        return out
    }

    companion object {
        private const val RECOVERY_SECRET_BYTES = 32
        private const val CHECKSUM_BYTES = 2
        private const val EXPECTED_BASE32_CHARS = 55
        private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    }
}
