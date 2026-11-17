package com.example.core.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

data class KdfParameters(
    val algorithm: String = ALGORITHM_ARGON2ID,
    val version: Int = CURRENT_KDF_VERSION,
    val memoryKiB: Int = DEFAULT_MEMORY_KIB,
    val iterations: Int = DEFAULT_ITERATIONS,
    val parallelism: Int = DEFAULT_PARALLELISM,
    val outputLengthBytes: Int = KEY_LENGTH_BYTES
) {
    fun validate() {
        require(algorithm == ALGORITHM_ARGON2ID) { "Unsupported KDF algorithm: $algorithm" }
        require(version == CURRENT_KDF_VERSION) { "Unsupported KDF version: $version" }
        require(memoryKiB in MIN_MEMORY_KIB..MAX_MEMORY_KIB) {
            "Argon2id memoryKiB out of safe bounds: $memoryKiB"
        }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
            "Argon2id iterations out of safe bounds: $iterations"
        }
        require(parallelism in MIN_PARALLELISM..MAX_PARALLELISM) {
            "Argon2id parallelism out of safe bounds: $parallelism"
        }
        require(outputLengthBytes == KEY_LENGTH_BYTES) {
            "Invalid output key length: $outputLengthBytes"
        }
    }

    companion object {
        const val ALGORITHM_ARGON2ID = "Argon2id"
        const val CURRENT_KDF_VERSION = 1
        const val SALT_LENGTH_BYTES = 16
        const val KEY_LENGTH_BYTES = 32

        // OWASP minimum baseline: 19 MiB (19456 KiB), 2 iterations, parallelism 1
        const val MIN_MEMORY_KIB = 19_456
        const val DEFAULT_MEMORY_KIB = 19_456
        const val MAX_MEMORY_KIB = 262_144 // 256 MiB DoS cap

        const val MIN_ITERATIONS = 2
        const val DEFAULT_ITERATIONS = 2
        const val MAX_ITERATIONS = 16

        const val MIN_PARALLELISM = 1
        const val DEFAULT_PARALLELISM = 1
        const val MAX_PARALLELISM = 8
    }
}

class Argon2idKeyDeriver(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun generateSalt(): ByteArray {
        val salt = ByteArray(KdfParameters.SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derives a 256-bit key from [password] without trimming or altering characters.
     * Clears intermediate UTF-8 password bytes before returning.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        parameters: KdfParameters = KdfParameters()
    ): SensitiveBytes {
        require(password.isNotEmpty()) { "Password must not be empty." }
        require(salt.size == KdfParameters.SALT_LENGTH_BYTES) {
            "Salt must be ${KdfParameters.SALT_LENGTH_BYTES} bytes."
        }
        parameters.validate()

        val passwordBytes = charArrayToUtf8Bytes(password)
        val output = ByteArray(parameters.outputLengthBytes)
        try {
            val argon2Params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(parameters.memoryKiB)
                .withIterations(parameters.iterations)
                .withParallelism(parameters.parallelism)
                .withSalt(salt)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(argon2Params)
            generator.generateBytes(passwordBytes, output, 0, output.size)
            return SensitiveBytes(output)
        } catch (e: Exception) {
            output.clearBytes()
            throw e
        } finally {
            passwordBytes.clearBytes()
        }
    }

    private fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val result = ByteArray(byteBuffer.remaining())
        byteBuffer.get(result)
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), 0.toByte())
        }
        return result
    }
}
