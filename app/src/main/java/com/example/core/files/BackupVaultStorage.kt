package com.example.core.files

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedVaultBackupEntry(
    val fileName: String,
    val absolutePath: String,
    val byteSize: Long,
    val lastModifiedMillis: Long,
    val isEncryptedBackup: Boolean
)

data class DirectSaveResult(
    val fileName: String,
    val vaultEntry: SavedVaultBackupEntry,
    val savedToPublicDownloads: Boolean,
    val displayLocation: String
)

/**
 * Crash-free Backup & Export file storage manager that saves `.dao-backup`, `.csv`, and
 * recovery `.txt` files directly to:
 * 1. The built-in App Backup Vault (`filesDir/backup_vault`) so files are always immediately
 *    listed and importable inside the app with zero external file picker dependency.
 * 2. The device's public `Downloads` collection via `MediaStore.Downloads` (Android 10+)
 *    or external Downloads directory fallback without requiring storage permissions or
 *    launching an external DocumentsUI activity.
 */
class BackupVaultStorage(
    private val context: Context
) {
    private val vaultDir: File
        get() = File(context.filesDir, "backup_vault").apply {
            if (!exists()) mkdirs()
        }

    suspend fun saveBackupBytesToDownloadsAndVault(
        fileName: String,
        bytes: ByteArray
    ): DirectSaveResult = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(fileName, defaultExt = ".dao-backup")
        val vaultFile = File(vaultDir, safeName)
        FileOutputStream(vaultFile).use { out ->
            out.write(bytes)
            out.flush()
            runCatching { out.fd.sync() }
        }

        val savedToDownloads = writeToPublicDownloads(
            fileName = safeName,
            mimeType = "application/octet-stream",
            bytes = bytes
        )

        val entry = SavedVaultBackupEntry(
            fileName = vaultFile.name,
            absolutePath = vaultFile.absolutePath,
            byteSize = vaultFile.length(),
            lastModifiedMillis = vaultFile.lastModified(),
            isEncryptedBackup = vaultFile.name.endsWith(".dao-backup", ignoreCase = true)
        )
        val locationLabel = if (savedToDownloads) {
            "Downloads/$safeName & In-App Backup Vault"
        } else {
            "In-App Backup Vault ($safeName)"
        }
        DirectSaveResult(
            fileName = safeName,
            vaultEntry = entry,
            savedToPublicDownloads = savedToDownloads,
            displayLocation = locationLabel
        )
    }

    suspend fun saveCsvToDownloadsAndVault(
        fileName: String,
        csvContent: String
    ): DirectSaveResult = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(fileName, defaultExt = ".csv")
        val bytes = csvContent.toByteArray(Charsets.UTF_8)
        val vaultFile = File(vaultDir, safeName)
        FileOutputStream(vaultFile).use { out ->
            out.write(bytes)
            out.flush()
            runCatching { out.fd.sync() }
        }

        val savedToDownloads = writeToPublicDownloads(
            fileName = safeName,
            mimeType = "text/csv",
            bytes = bytes
        )

        val entry = SavedVaultBackupEntry(
            fileName = vaultFile.name,
            absolutePath = vaultFile.absolutePath,
            byteSize = vaultFile.length(),
            lastModifiedMillis = vaultFile.lastModified(),
            isEncryptedBackup = false
        )
        val locationLabel = if (savedToDownloads) {
            "Downloads/$safeName & In-App Backup Vault"
        } else {
            "In-App Backup Vault ($safeName)"
        }
        DirectSaveResult(
            fileName = safeName,
            vaultEntry = entry,
            savedToPublicDownloads = savedToDownloads,
            displayLocation = locationLabel
        )
    }

    suspend fun saveRecoveryCodeToDownloadsAndVault(
        fileName: String,
        content: String
    ): DirectSaveResult = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(fileName, defaultExt = ".txt")
        val bytes = content.toByteArray(Charsets.UTF_8)
        val vaultFile = File(vaultDir, safeName)
        FileOutputStream(vaultFile).use { out ->
            out.write(bytes)
            out.flush()
            runCatching { out.fd.sync() }
        }

        val savedToDownloads = writeToPublicDownloads(
            fileName = safeName,
            mimeType = "text/plain",
            bytes = bytes
        )

        val entry = SavedVaultBackupEntry(
            fileName = vaultFile.name,
            absolutePath = vaultFile.absolutePath,
            byteSize = vaultFile.length(),
            lastModifiedMillis = vaultFile.lastModified(),
            isEncryptedBackup = false
        )
        val locationLabel = if (savedToDownloads) {
            "Downloads/$safeName & In-App Backup Vault"
        } else {
            "In-App Backup Vault ($safeName)"
        }
        DirectSaveResult(
            fileName = safeName,
            vaultEntry = entry,
            savedToPublicDownloads = savedToDownloads,
            displayLocation = locationLabel
        )
    }

    suspend fun listSavedBackups(): List<SavedVaultBackupEntry> = withContext(Dispatchers.IO) {
        val files = vaultDir.listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(".dao-backup", ignoreCase = true) || it.name.endsWith(".csv", ignoreCase = true)) }
            .sortedByDescending { it.lastModified() }

        files.map { file ->
            SavedVaultBackupEntry(
                fileName = file.name,
                absolutePath = file.absolutePath,
                byteSize = file.length(),
                lastModifiedMillis = file.lastModified(),
                isEncryptedBackup = file.name.endsWith(".dao-backup", ignoreCase = true)
            )
        }
    }

    suspend fun readVaultFileBytes(absolutePath: String): ByteArray = withContext(Dispatchers.IO) {
        val target = File(absolutePath)
        val canonicalVault = vaultDir.canonicalPath
        val canonicalTarget = target.canonicalPath
        require(canonicalTarget.startsWith(canonicalVault)) {
            "Invalid backup vault file path."
        }
        require(target.exists() && target.isFile) {
            "Backup file no longer exists in vault."
        }
        target.readBytes()
    }

    suspend fun deleteVaultFile(absolutePath: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(absolutePath)
        val canonicalVault = vaultDir.canonicalPath
        val canonicalTarget = target.canonicalPath
        if (!canonicalTarget.startsWith(canonicalVault)) return@withContext false
        if (target.exists()) target.delete() else false
    }

    private fun writeToPublicDownloads(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val savedViaMediaStore = runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: return@runCatching false
                val completeValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, completeValues, null, null)
                true
            }.getOrDefault(false)
            if (savedViaMediaStore) return true
        }

        return runCatching {
            val extDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!extDownloads.exists()) extDownloads.mkdirs()
            val outFile = File(extDownloads, fileName)
            FileOutputStream(outFile).use { out ->
                out.write(bytes)
                out.flush()
            }
            true
        }.getOrDefault(false)
    }

    private fun sanitizeFileName(raw: String, defaultExt: String): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (cleaned.isEmpty()) {
            "digital_accounts_${System.currentTimeMillis()}$defaultExt"
        } else if (!cleaned.contains('.')) {
            "$cleaned$defaultExt"
        } else {
            cleaned
        }
    }
}
