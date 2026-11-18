package com.example.core.files

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
    val isEncryptedBackup: Boolean,
    val sourceLabel: String = "App Vault",
    val contentUriString: String? = null
)

data class DirectSaveResult(
    val fileName: String,
    val vaultEntry: SavedVaultBackupEntry,
    val savedToPublicDownloads: Boolean,
    val displayLocation: String
)

data class StorageFolderShortcut(
    val label: String,
    val path: String
)

data class StorageBrowserNode(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val byteSize: Long,
    val lastModifiedMillis: Long
)

data class StorageDirectoryListing(
    val currentPath: String,
    val parentPath: String?,
    val entries: List<StorageBrowserNode>,
    val readable: Boolean
)

/**
 * Crash-free Backup & Export file storage and cross-device discovery manager that:
 * 1. Saves `.dao-backup`, `.csv`, and recovery `.txt` files directly to both public `Downloads`
 *    (`MediaStore.Downloads` on Android 10+ / fallback) and the in-app `filesDir/backup_vault`.
 * 2. Scans `MediaStore.Downloads`, public `Downloads`, public `Documents`, and the in-app vault
 *    so `.dao-backup` files transferred from another device are immediately discoverable.
 * 3. Provides an In-App Device Storage Folder Browser so users can navigate folders and select
 *    `.dao-backup` files even on devices or emulators where Android's system `DocumentsUI` picker
 *    is unavailable.
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
            isEncryptedBackup = vaultFile.name.endsWith(".dao-backup", ignoreCase = true),
            sourceLabel = if (savedToDownloads) "Downloads & Vault" else "App Vault"
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
            isEncryptedBackup = false,
            sourceLabel = if (savedToDownloads) "Downloads & Vault" else "App Vault"
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
            isEncryptedBackup = false,
            sourceLabel = if (savedToDownloads) "Downloads & Vault" else "App Vault"
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

    /**
     * Lists saved backups in the built-in App Vault plus any `.dao-backup` files discovered
     * in public `Downloads`, `Documents`, or `MediaStore.Downloads` (including backups transferred
     * from another device).
     */
    suspend fun listSavedBackups(): List<SavedVaultBackupEntry> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<SavedVaultBackupEntry>()
        val seenFileNamesAndSizes = mutableSetOf<String>()

        // 1. In-App Backup Vault files
        val vaultFiles = vaultDir.listFiles().orEmpty()
            .filter {
                it.isFile && (
                    it.name.endsWith(".dao-backup", ignoreCase = true) ||
                        it.name.endsWith(".csv", ignoreCase = true)
                    )
            }
            .sortedByDescending { it.lastModified() }

        for (file in vaultFiles) {
            val key = "${file.name.lowercase()}:${file.length()}"
            seenFileNamesAndSizes.add(key)
            discovered.add(
                SavedVaultBackupEntry(
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    byteSize = file.length(),
                    lastModifiedMillis = file.lastModified(),
                    isEncryptedBackup = file.name.endsWith(".dao-backup", ignoreCase = true),
                    sourceLabel = "Downloads & Vault"
                )
            )
        }

        // 2. Scan MediaStore.Downloads (Android 10+) for external .dao-backup files
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATE_MODIFIED
                )
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol) ?: continue
                        if (!name.endsWith(".dao-backup", ignoreCase = true)) continue
                        val id = cursor.getLong(idCol)
                        val size = cursor.getLong(sizeCol)
                        val modifiedMillis = cursor.getLong(dateCol) * 1000L
                        val dedupeKey = "${name.lowercase()}:$size"
                        if (seenFileNamesAndSizes.add(dedupeKey)) {
                            val contentUri = ContentUris.withAppendedId(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                id
                            )
                            discovered.add(
                                SavedVaultBackupEntry(
                                    fileName = name,
                                    absolutePath = contentUri.toString(),
                                    byteSize = size,
                                    lastModifiedMillis = modifiedMillis,
                                    isEncryptedBackup = true,
                                    sourceLabel = "Device Downloads",
                                    contentUriString = contentUri.toString()
                                )
                            )
                        }
                    }
                }
            }
        }

        // 3. Scan accessible public Downloads / Documents folders on filesystem
        val candidateDirs = listOfNotNull(
            runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }.getOrNull(),
            runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) }.getOrNull(),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Documents")
        ).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

        for (dir in candidateDirs) {
            val files = runCatching { dir.listFiles() }.getOrNull().orEmpty()
            for (file in files) {
                if (!file.isFile || !file.canRead()) continue
                if (!file.name.endsWith(".dao-backup", ignoreCase = true)) continue
                val dedupeKey = "${file.name.lowercase()}:${file.length()}"
                if (seenFileNamesAndSizes.add(dedupeKey)) {
                    discovered.add(
                        SavedVaultBackupEntry(
                            fileName = file.name,
                            absolutePath = file.absolutePath,
                            byteSize = file.length(),
                            lastModifiedMillis = file.lastModified(),
                            isEncryptedBackup = true,
                            sourceLabel = dir.name.ifBlank { "Device Storage" }
                        )
                    )
                }
            }
        }

        discovered.sortedByDescending { it.lastModifiedMillis }
    }

    fun getQuickAccessDirectories(): List<StorageFolderShortcut> {
        val shortcuts = mutableListOf<StorageFolderShortcut>()
        val downloadsDir = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }.getOrNull() ?: File("/storage/emulated/0/Download")
        shortcuts.add(StorageFolderShortcut("Downloads", downloadsDir.absolutePath))

        val documentsDir = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        }.getOrNull() ?: File("/storage/emulated/0/Documents")
        shortcuts.add(StorageFolderShortcut("Documents", documentsDir.absolutePath))

        val rootStorage = runCatching {
            Environment.getExternalStorageDirectory()
        }.getOrNull() ?: File("/storage/emulated/0")
        shortcuts.add(StorageFolderShortcut("Device Storage", rootStorage.absolutePath))

        shortcuts.add(StorageFolderShortcut("App Vault", vaultDir.absolutePath))
        return shortcuts.distinctBy { it.path }
    }

    /**
     * Lists subdirectories and `.dao-backup` files inside [directoryPath] for the In-App
     * Device Storage Folder Browser.
     */
    suspend fun browseDirectory(directoryPath: String?): StorageDirectoryListing =
        withContext(Dispatchers.IO) {
            val defaultDir = runCatching {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }.getOrNull()?.takeIf { it.exists() } ?: vaultDir

            val targetDir = directoryPath?.takeIf { it.isNotBlank() }?.let { File(it) } ?: defaultDir
            val resolvedDir = if (targetDir.exists() && targetDir.isDirectory) targetDir else defaultDir

            val rawChildren = runCatching { resolvedDir.listFiles() }.getOrNull()
            val readable = rawChildren != null
            val nodes = rawChildren.orEmpty()
                .filter { file ->
                    !file.name.startsWith(".") && (
                        file.isDirectory ||
                            file.name.endsWith(".dao-backup", ignoreCase = true)
                        )
                }
                .sortedWith(
                    compareByDescending<File> { it.isDirectory }
                        .thenByDescending { if (it.isFile) it.lastModified() else 0L }
                        .thenBy { it.name.lowercase() }
                )
                .map { file ->
                    StorageBrowserNode(
                        name = file.name,
                        absolutePath = file.absolutePath,
                        isDirectory = file.isDirectory,
                        byteSize = if (file.isFile) file.length() else 0L,
                        lastModifiedMillis = file.lastModified()
                    )
                }

            val parent = resolvedDir.parentFile?.takeIf { it.exists() && it.canRead() }?.absolutePath
            StorageDirectoryListing(
                currentPath = resolvedDir.absolutePath,
                parentPath = parent,
                entries = nodes,
                readable = readable
            )
        }

    /**
     * Reads `.dao-backup` bytes from a vault path, an external storage file path, or a `content://` / `file://` URI.
     */
    suspend fun readVaultFileBytes(pathOrUri: String): ByteArray = withContext(Dispatchers.IO) {
        val trimmed = pathOrUri.trim()
        require(trimmed.isNotEmpty()) { "Backup file path is empty." }

        if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
            val uri = Uri.parse(trimmed)
            return@withContext context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Unable to open backup URI: $trimmed")
        }

        val target = File(trimmed)
        require(target.exists() && target.isFile) {
            "Selected backup file does not exist or cannot be accessed: ${target.name}"
        }
        require(target.canRead()) {
            "Selected backup file cannot be read from this folder. Try moving it to Downloads or using the System File Chooser."
        }
        target.readBytes()
    }

    suspend fun deleteVaultFile(absolutePath: String): Boolean = withContext(Dispatchers.IO) {
        if (absolutePath.startsWith("content://")) {
            return@withContext runCatching {
                context.contentResolver.delete(Uri.parse(absolutePath), null, null) > 0
            }.getOrDefault(false)
        }
        val target = File(absolutePath)
        val canonicalVault = vaultDir.canonicalPath
        val canonicalTarget = runCatching { target.canonicalPath }.getOrNull() ?: return@withContext false
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
