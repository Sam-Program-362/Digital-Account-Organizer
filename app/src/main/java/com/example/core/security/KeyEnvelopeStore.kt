package com.example.core.security

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class AutoLockTimeout(val timeoutMillis: Long, val displayLabel: String) {
    IMMEDIATE(0L, "Immediately when leaving app"),
    ONE_MINUTE(60_000L, "After 1 minute in background"),
    FIVE_MINUTES(300_000L, "After 5 minutes in background");

    companion object {
        fun fromMillis(millis: Long): AutoLockTimeout =
            entries.firstOrNull { it.timeoutMillis == millis } ?: IMMEDIATE
    }
}

data class PersistedSecurityState(
    val isAppLockEnabled: Boolean,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
    val envelopes: Map<EnvelopeType, KeyEnvelope>
)

interface KeyEnvelopeStore {
    fun readState(): PersistedSecurityState?
    fun writeStateAtomically(state: PersistedSecurityState)
    fun clearAll()
}

/**
 * Stores versioned binary key-envelope metadata in app-private storage using atomic temp-file swap.
 * Never persists raw DEK, KEK, master password, or recovery key.
 */
class FileKeyEnvelopeStore(
    private val directory: File
) : KeyEnvelopeStore {

    private val stateFile = File(directory, "security_envelopes.bin")
    private val tempFile = File(directory, "security_envelopes.bin.tmp")

    @Synchronized
    override fun readState(): PersistedSecurityState? {
        if (!stateFile.exists()) return null
        DataInputStream(FileInputStream(stateFile)).use { input ->
            val magic = input.readInt()
            require(magic == FILE_MAGIC) { "Corrupt security envelope store header." }
            val version = input.readInt()
            require(version == STORE_VERSION) { "Unsupported security envelope store version: $version" }
            val isAppLockEnabled = input.readBoolean()
            val timeoutMillis = input.readLong()
            val count = input.readInt()
            require(count in 1..4) { "Invalid envelope count: $count" }

            val envelopes = mutableMapOf<EnvelopeType, KeyEnvelope>()
            repeat(count) {
                val formatVersion = input.readInt()
                val type = EnvelopeType.valueOf(input.readUTF())
                val cipherAlgorithm = input.readUTF()
                val nonce = ByteArray(input.readInt()).also { input.readFully(it) }
                val ciphertext = ByteArray(input.readInt()).also { input.readFully(it) }
                val hasKdf = input.readBoolean()
                val kdfParams = if (hasKdf) {
                    KdfParameters(
                        algorithm = input.readUTF(),
                        version = input.readInt(),
                        memoryKiB = input.readInt(),
                        iterations = input.readInt(),
                        parallelism = input.readInt(),
                        outputLengthBytes = input.readInt()
                    ).also { it.validate() }
                } else {
                    null
                }
                val hasSalt = input.readBoolean()
                val salt = if (hasSalt) {
                    ByteArray(input.readInt()).also { input.readFully(it) }
                } else {
                    null
                }
                val hasAlias = input.readBoolean()
                val alias = if (hasAlias) input.readUTF() else null
                val createdAt = input.readLong()

                envelopes[type] = KeyEnvelope(
                    formatVersion = formatVersion,
                    type = type,
                    cipherAlgorithm = cipherAlgorithm,
                    nonce = nonce,
                    ciphertextWithTag = ciphertext,
                    kdfParameters = kdfParams,
                    salt = salt,
                    keystoreAlias = alias,
                    createdAtEpochMillis = createdAt
                )
            }
            return PersistedSecurityState(
                isAppLockEnabled = isAppLockEnabled,
                autoLockTimeout = AutoLockTimeout.fromMillis(timeoutMillis),
                envelopes = envelopes
            )
        }
    }

    @Synchronized
    override fun writeStateAtomically(state: PersistedSecurityState) {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        DataOutputStream(FileOutputStream(tempFile)).use { out ->
            out.writeInt(FILE_MAGIC)
            out.writeInt(STORE_VERSION)
            out.writeBoolean(state.isAppLockEnabled)
            out.writeLong(state.autoLockTimeout.timeoutMillis)
            out.writeInt(state.envelopes.size)
            for (envelope in state.envelopes.values) {
                out.writeInt(envelope.formatVersion)
                out.writeUTF(envelope.type.name)
                out.writeUTF(envelope.cipherAlgorithm)
                out.writeInt(envelope.nonce.size)
                out.write(envelope.nonce)
                out.writeInt(envelope.ciphertextWithTag.size)
                out.write(envelope.ciphertextWithTag)

                val kdf = envelope.kdfParameters
                out.writeBoolean(kdf != null)
                if (kdf != null) {
                    out.writeUTF(kdf.algorithm)
                    out.writeInt(kdf.version)
                    out.writeInt(kdf.memoryKiB)
                    out.writeInt(kdf.iterations)
                    out.writeInt(kdf.parallelism)
                    out.writeInt(kdf.outputLengthBytes)
                }

                val salt = envelope.salt
                out.writeBoolean(salt != null)
                if (salt != null) {
                    out.writeInt(salt.size)
                    out.write(salt)
                }

                val alias = envelope.keystoreAlias
                out.writeBoolean(alias != null)
                if (alias != null) {
                    out.writeUTF(alias)
                }

                out.writeLong(envelope.createdAtEpochMillis)
            }
            out.flush()
        }
        if (!tempFile.renameTo(stateFile)) {
            tempFile.copyTo(stateFile, overwrite = true)
            tempFile.delete()
        }
    }

    @Synchronized
    override fun clearAll() {
        tempFile.delete()
        stateFile.delete()
    }

    companion object {
        private const val FILE_MAGIC = 0x44414F53 // "DAOS"
        private const val STORE_VERSION = 1
    }
}
