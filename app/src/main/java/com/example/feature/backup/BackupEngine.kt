package com.example.feature.backup

import android.util.Base64
import androidx.room.withTransaction
import com.example.core.database.AccountEntity
import com.example.core.database.AppPreferenceEntity
import com.example.core.database.CategoryEntity
import com.example.core.database.EncryptedDatabaseFactory
import com.example.core.database.OrganizerRepository
import com.example.core.database.ServiceAssetEntity
import com.example.core.database.ServiceEntity
import com.example.core.files.ServiceAssetStorage
import com.example.core.security.Argon2idKeyDeriver
import com.example.core.security.KdfParameters
import com.example.core.security.clearBytes
import com.example.domain.model.AccountStatus
import com.example.domain.model.Normalizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

enum class ExportMode(val displayLabel: String) {
    FULL("Full Organizer Backup"),
    SELECTED_ACCOUNTS("Selected Accounts"),
    SELECTED_SERVICES("Selected Services"),
    SELECTED_CATEGORIES("Selected Categories")
}

data class ExportSelection(
    val mode: ExportMode = ExportMode.FULL,
    val selectedAccountIds: Set<String> = emptySet(),
    val selectedServiceIds: Set<String> = emptySet(),
    val selectedCategoryIds: Set<String> = emptySet()
)

enum class ConflictKind(val displayLabel: String) {
    NEW("New Record"),
    ID_CONFLICT("ID Conflict (Same UUID, different content)"),
    POSSIBLE_DUPLICATE("Possible Duplicate (Matching name/identifier)")
}

enum class ConflictResolution(val displayLabel: String) {
    SKIP("Skip (Keep Local)"),
    KEEP_BOTH("Keep Both"),
    REPLACE("Replace Local")
}

data class StagedRecordConflict(
    val recordType: String, // "SERVICE", "CATEGORY", "ACCOUNT"
    val incomingId: String,
    val localMatchId: String?,
    val title: String,
    val subtitle: String,
    val kind: ConflictKind,
    val defaultResolution: ConflictResolution = ConflictResolution.SKIP
)

data class StagedBackupPackage(
    val exportedAtIso: String,
    val dataSchemaVersion: Int,
    val services: List<ServiceEntity>,
    val categories: List<CategoryEntity>,
    val accounts: List<AccountEntity>,
    val preferences: List<AppPreferenceEntity>,
    val assetBytesById: Map<String, ByteArray>,
    val assetChecksumById: Map<String, String>,
    val conflicts: List<StagedRecordConflict>,
    val newServicesCount: Int,
    val newCategoriesCount: Int,
    val newAccountsCount: Int
)

data class CommitImportSummary(
    val importedServices: Int,
    val importedCategories: Int,
    val importedAccounts: Int,
    val skippedRecords: Int,
    val replacedRecords: Int
)

class BackupEngine(
    private val databaseFactory: EncryptedDatabaseFactory,
    private val assetStorage: ServiceAssetStorage,
    private val argon2Deriver: Argon2idKeyDeriver = Argon2idKeyDeriver(),
    private val secureRandom: SecureRandom = SecureRandom(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Creates an encrypted `.dao-backup` container as bytes ready to write via SAF.
     */
    suspend fun createEncryptedBackupBytes(
        passphrase: CharArray,
        selection: ExportSelection = ExportSelection(),
        kdfParameters: KdfParameters = KdfParameters()
    ): ByteArray {
        require(passphrase.size >= MIN_BACKUP_PASSPHRASE_CHARS) {
            "Backup passphrase must be at least $MIN_BACKUP_PASSPHRASE_CHARS characters."
        }
        val db = databaseFactory.getUnlockedDatabase()
        val allServices = db.serviceDao().getAllServices()
        val allCategories = db.categoryDao().getAllCategories()
        val allAccounts = db.accountDao().getAllAccounts()
        val allAssets = db.serviceAssetDao().getAllAssets()
        val allPrefs = db.appPreferenceDao().getAllPreferences()
            .filter { it.key in ALLOWLISTED_PREF_KEYS }

        val (exportServices, exportCategories, exportAccounts, exportPrefs) = when (selection.mode) {
            ExportMode.FULL -> Quad(allServices, allCategories, allAccounts, allPrefs)
            ExportMode.SELECTED_ACCOUNTS -> {
                val chosenAccounts = allAccounts.filter { it.id in selection.selectedAccountIds }
                val refServiceIds = chosenAccounts.map { it.serviceId }.toSet()
                val refCategoryIds = chosenAccounts.mapNotNull { it.categoryId }.toSet()
                Quad(
                    allServices.filter { it.id in refServiceIds },
                    allCategories.filter { it.id in refCategoryIds },
                    chosenAccounts,
                    emptyList()
                )
            }
            ExportMode.SELECTED_SERVICES -> {
                val chosenServices = allServices.filter { it.id in selection.selectedServiceIds }
                val serviceIds = chosenServices.map { it.id }.toSet()
                val linkedAccounts = allAccounts.filter { it.serviceId in serviceIds }
                val refCategoryIds = linkedAccounts.mapNotNull { it.categoryId }.toSet()
                Quad(
                    chosenServices,
                    allCategories.filter { it.id in refCategoryIds },
                    linkedAccounts,
                    emptyList()
                )
            }
            ExportMode.SELECTED_CATEGORIES -> {
                val chosenCategories = allCategories.filter { it.id in selection.selectedCategoryIds }
                val categoryIds = chosenCategories.map { it.id }.toSet()
                val linkedAccounts = allAccounts.filter { it.categoryId in categoryIds }
                val refServiceIds = linkedAccounts.map { it.serviceId }.toSet()
                Quad(
                    allServices.filter { it.id in refServiceIds },
                    chosenCategories,
                    linkedAccounts,
                    emptyList()
                )
            }
        }

        val referencedAssetIds = exportServices.mapNotNull { it.iconAssetId }.toSet()
        val exportAssets = allAssets.filter { it.id in referencedAssetIds }

        val innerZipBytes = buildInnerPayloadZip(
            services = exportServices,
            categories = exportCategories,
            accounts = exportAccounts,
            preferences = exportPrefs,
            assets = exportAssets,
            exportMode = selection.mode
        )

        val salt = argon2Deriver.generateSalt()
        val nonce = ByteArray(GCM_NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val canonicalAad = canonicalHeaderAad(
            format = FORMAT_ID,
            formatVersion = CURRENT_FORMAT_VERSION,
            cipher = CIPHER_ID,
            kdfParameters = kdfParameters,
            salt = salt,
            nonce = nonce,
            payloadEncoding = PAYLOAD_ENCODING_ID
        )

        val encryptedPayload = try {
            argon2Deriver.deriveKey(passphrase, salt, kdfParameters).use { key ->
                val rawKey = key.copyBytes()
                try {
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(rawKey, "AES"),
                        GCMParameterSpec(GCM_TAG_BITS, nonce)
                    )
                    cipher.updateAAD(canonicalAad)
                    cipher.doFinal(innerZipBytes)
                } finally {
                    rawKey.clearBytes()
                }
            }
        } finally {
            innerZipBytes.clearBytes()
        }

        val headerJson = JSONObject().apply {
            put("format", FORMAT_ID)
            put("formatVersion", CURRENT_FORMAT_VERSION)
            put("cipher", CIPHER_ID)
            put("kdf", kdfParameters.algorithm)
            put("kdfVersion", kdfParameters.version)
            put(
                "kdfParameters",
                JSONObject().apply {
                    put("memoryKiB", kdfParameters.memoryKiB)
                    put("iterations", kdfParameters.iterations)
                    put("parallelism", kdfParameters.parallelism)
                    put("outputLengthBytes", kdfParameters.outputLengthBytes)
                }
            )
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            put("payloadEncoding", PAYLOAD_ENCODING_ID)
        }.toString(2).toByteArray(StandardCharsets.UTF_8)

        return ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry(ENTRY_HEADER_JSON))
                zos.write(headerJson)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry(ENTRY_PAYLOAD_ENC))
                zos.write(encryptedPayload)
                zos.closeEntry()
            }
            baos.toByteArray()
        }
    }

    /**
     * Validates, decrypts, and stages an untrusted `.dao-backup` byte array, returning an
     * [StagedBackupPackage] with conflict analysis before any database mutation occurs.
     */
    suspend fun stageAndPreviewEncryptedBackup(
        backupFileBytes: ByteArray,
        passphrase: CharArray
    ): StagedBackupPackage {
        require(backupFileBytes.isNotEmpty()) { "Backup file is empty." }
        require(backupFileBytes.size <= MAX_BACKUP_FILE_BYTES) {
            "Backup file exceeds maximum supported size (${MAX_BACKUP_FILE_BYTES / (1024 * 1024)} MB)."
        }

        var headerBytes: ByteArray? = null
        var payloadEncBytes: ByteArray? = null
        var outerEntries = 0

        ZipInputStream(ByteArrayInputStream(backupFileBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                outerEntries++
                require(outerEntries <= 4) { "Unexpected number of entries in outer backup container." }
                validateSafeEntryName(entry.name)
                when (entry.name) {
                    ENTRY_HEADER_JSON -> {
                        require(headerBytes == null) { "Duplicate header.json entry." }
                        headerBytes = readBoundedEntryBytes(zis, MAX_HEADER_BYTES)
                    }
                    ENTRY_PAYLOAD_ENC -> {
                        require(payloadEncBytes == null) { "Duplicate payload.enc entry." }
                        payloadEncBytes = readBoundedEntryBytes(zis, MAX_BACKUP_FILE_BYTES)
                    }
                    else -> throw IllegalArgumentException("Unexpected outer entry in backup: ${entry.name}")
                }
                zis.closeEntry()
            }
        }

        val verifiedHeaderBytes = requireNotNull(headerBytes) { "Backup is missing header.json." }
        val verifiedPayloadEnc = requireNotNull(payloadEncBytes) { "Backup is missing payload.enc." }

        val headerObj = JSONObject(String(verifiedHeaderBytes, StandardCharsets.UTF_8))
        val format = headerObj.optString("format")
        require(format == FORMAT_ID) { "Unsupported backup format: $format" }
        val formatVersion = headerObj.optInt("formatVersion", -1)
        require(formatVersion == CURRENT_FORMAT_VERSION) {
            "Unsupported backup format version: $formatVersion"
        }
        val cipherId = headerObj.optString("cipher")
        require(cipherId == CIPHER_ID) { "Unsupported backup cipher: $cipherId" }
        val payloadEncoding = headerObj.optString("payloadEncoding")
        require(payloadEncoding == PAYLOAD_ENCODING_ID) {
            "Unsupported payload encoding: $payloadEncoding"
        }

        val kdfObj = headerObj.getJSONObject("kdfParameters")
        val kdfParams = KdfParameters(
            algorithm = headerObj.optString("kdf"),
            version = headerObj.optInt("kdfVersion", -1),
            memoryKiB = kdfObj.optInt("memoryKiB", -1),
            iterations = kdfObj.optInt("iterations", -1),
            parallelism = kdfObj.optInt("parallelism", -1),
            outputLengthBytes = kdfObj.optInt("outputLengthBytes", -1)
        ).also { it.validate() }

        val salt = Base64.decode(headerObj.getString("salt"), Base64.NO_WRAP)
        val nonce = Base64.decode(headerObj.getString("nonce"), Base64.NO_WRAP)
        require(salt.size == KdfParameters.SALT_LENGTH_BYTES) { "Invalid backup salt length." }
        require(nonce.size == GCM_NONCE_BYTES) { "Invalid backup nonce length." }

        val canonicalAad = canonicalHeaderAad(
            format = format,
            formatVersion = formatVersion,
            cipher = cipherId,
            kdfParameters = kdfParams,
            salt = salt,
            nonce = nonce,
            payloadEncoding = payloadEncoding
        )

        val decryptedZipBytes = try {
            argon2Deriver.deriveKey(passphrase, salt, kdfParams).use { key ->
                val rawKey = key.copyBytes()
                try {
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(rawKey, "AES"),
                        GCMParameterSpec(GCM_TAG_BITS, nonce)
                    )
                    cipher.updateAAD(canonicalAad)
                    cipher.doFinal(verifiedPayloadEnc)
                } finally {
                    rawKey.clearBytes()
                }
            }
        } catch (e: GeneralSecurityException) {
            throw GeneralSecurityException("Incorrect backup passphrase or damaged backup file.", e)
        }

        try {
            return parseAndDiffInnerZip(decryptedZipBytes)
        } finally {
            decryptedZipBytes.clearBytes()
        }
    }

    /**
     * Applies user-confirmed conflict decisions and commits the staged package in a single transaction.
     */
    suspend fun commitStagedBackup(
        staged: StagedBackupPackage,
        resolutionsByIncomingId: Map<String, ConflictResolution>
    ): CommitImportSummary {
        val db = databaseFactory.getUnlockedDatabase()
        var importedServices = 0
        var importedCategories = 0
        var importedAccounts = 0
        var skippedRecords = 0
        var replacedRecords = 0

        val conflictByIncomingId = staged.conflicts.associateBy { it.incomingId }

        // Pre-validate and write needed assets into private storage
        val committedAssetIdMap = mutableMapOf<String, String>()
        for ((assetId, rawPngBytes) in staged.assetBytesById) {
            val expectedSha = staged.assetChecksumById[assetId]
            val actualSha = ServiceAssetStorage.sha256Hex(rawPngBytes)
            require(expectedSha != null && expectedSha.equals(actualSha, ignoreCase = true)) {
                "Asset integrity checksum mismatch for asset $assetId."
            }
            val assetEntity = assetStorage.importFromRawBytes(rawPngBytes, preferredAssetId = assetId)
            db.serviceAssetDao().upsertAsset(assetEntity)
            committedAssetIdMap[assetId] = assetEntity.id
        }

        db.withTransaction {
            val serviceIdRemap = mutableMapOf<String, String>()
            val categoryIdRemap = mutableMapOf<String, String>()

            // 1. Categories
            for (incomingCat in staged.categories) {
                val conflict = conflictByIncomingId[incomingCat.id]
                if (conflict == null) {
                    db.categoryDao().insertCategory(incomingCat)
                    categoryIdRemap[incomingCat.id] = incomingCat.id
                    importedCategories++
                } else {
                    val choice = resolutionsByIncomingId[incomingCat.id] ?: conflict.defaultResolution
                    when (choice) {
                        ConflictResolution.SKIP -> {
                            categoryIdRemap[incomingCat.id] = conflict.localMatchId ?: incomingCat.id
                            skippedRecords++
                        }
                        ConflictResolution.REPLACE -> {
                            val targetId = conflict.localMatchId ?: incomingCat.id
                            db.categoryDao().upsertCategory(incomingCat.copy(id = targetId))
                            categoryIdRemap[incomingCat.id] = targetId
                            replacedRecords++
                        }
                        ConflictResolution.KEEP_BOTH -> {
                            val newId = UUID.randomUUID().toString()
                            val disambiguatedName = "${incomingCat.name} (Imported)"
                            val copy = incomingCat.copy(
                                id = newId,
                                name = disambiguatedName,
                                normalizedName = Normalizer.normalizeName(disambiguatedName)
                            )
                            db.categoryDao().upsertCategory(copy)
                            categoryIdRemap[incomingCat.id] = newId
                            importedCategories++
                        }
                    }
                }
            }

            // 2. Services
            for (incomingSvc in staged.services) {
                val mappedAssetId = incomingSvc.iconAssetId?.let { committedAssetIdMap[it] }
                val preparedSvc = incomingSvc.copy(iconAssetId = mappedAssetId)
                val conflict = conflictByIncomingId[incomingSvc.id]
                if (conflict == null) {
                    db.serviceDao().insertService(preparedSvc)
                    serviceIdRemap[incomingSvc.id] = incomingSvc.id
                    importedServices++
                } else {
                    val choice = resolutionsByIncomingId[incomingSvc.id] ?: conflict.defaultResolution
                    when (choice) {
                        ConflictResolution.SKIP -> {
                            serviceIdRemap[incomingSvc.id] = conflict.localMatchId ?: incomingSvc.id
                            skippedRecords++
                        }
                        ConflictResolution.REPLACE -> {
                            val targetId = conflict.localMatchId ?: incomingSvc.id
                            db.serviceDao().upsertService(preparedSvc.copy(id = targetId))
                            serviceIdRemap[incomingSvc.id] = targetId
                            replacedRecords++
                        }
                        ConflictResolution.KEEP_BOTH -> {
                            val newId = UUID.randomUUID().toString()
                            val disambiguatedName = "${preparedSvc.name} (Imported)"
                            val copy = preparedSvc.copy(
                                id = newId,
                                name = disambiguatedName,
                                normalizedName = Normalizer.normalizeName(disambiguatedName)
                            )
                            db.serviceDao().upsertService(copy)
                            serviceIdRemap[incomingSvc.id] = newId
                            importedServices++
                        }
                    }
                }
            }

            // 3. Accounts
            for (incomingAcc in staged.accounts) {
                val resolvedServiceId = serviceIdRemap[incomingAcc.serviceId] ?: incomingAcc.serviceId
                val resolvedCategoryId = incomingAcc.categoryId?.let { categoryIdRemap[it] ?: it }
                val preparedAcc = incomingAcc.copy(
                    serviceId = resolvedServiceId,
                    categoryId = resolvedCategoryId
                )
                val conflict = conflictByIncomingId[incomingAcc.id]
                if (conflict == null) {
                    db.accountDao().insertAccount(preparedAcc)
                    importedAccounts++
                } else {
                    val choice = resolutionsByIncomingId[incomingAcc.id] ?: conflict.defaultResolution
                    when (choice) {
                        ConflictResolution.SKIP -> {
                            skippedRecords++
                        }
                        ConflictResolution.REPLACE -> {
                            val targetId = conflict.localMatchId ?: incomingAcc.id
                            db.accountDao().upsertAccount(preparedAcc.copy(id = targetId))
                            replacedRecords++
                        }
                        ConflictResolution.KEEP_BOTH -> {
                            val newId = UUID.randomUUID().toString()
                            val copy = preparedAcc.copy(
                                id = newId,
                                displayName = "${preparedAcc.displayName} (Imported)",
                                normalizedDisplayName = Normalizer.normalizeName("${preparedAcc.displayName} (Imported)")
                            )
                            db.accountDao().upsertAccount(copy)
                            importedAccounts++
                        }
                    }
                }
            }

            // 4. Allowlisted preferences
            for (pref in staged.preferences) {
                if (pref.key in ALLOWLISTED_PREF_KEYS) {
                    db.appPreferenceDao().upsertPreference(pref)
                }
            }
        }

        return CommitImportSummary(
            importedServices = importedServices,
            importedCategories = importedCategories,
            importedAccounts = importedAccounts,
            skippedRecords = skippedRecords,
            replacedRecords = replacedRecords
        )
    }

    /**
     * Generates a properly escaped RFC 4180 CSV export of accounts, services, and categories.
     * Never includes security metadata or password-like fields.
     */
    suspend fun exportPlaintextCsv(): String {
        val db = databaseFactory.getUnlockedDatabase()
        val servicesById = db.serviceDao().getAllServices().associateBy { it.id }
        val categoriesById = db.categoryDao().getAllCategories().associateBy { it.id }
        val accounts = db.accountDao().getAllAccounts()

        val sb = StringBuilder()
        sb.appendLine(
            listOf(
                "Account ID",
                "Service Name",
                "Service Website",
                "Account Label",
                "Email",
                "Username",
                "Category",
                "Status",
                "Archived",
                "Purpose",
                "Recovery Email Reference",
                "Recovery Phone Reference",
                "Account Website",
                "Notes",
                "Created At (UTC)",
                "Updated At (UTC)"
            ).joinToString(",") { escapeCsvCell(it) }
        )

        for (account in accounts) {
            val service = servicesById[account.serviceId]
            val category = account.categoryId?.let { categoriesById[it] }
            val row = listOf(
                account.id,
                service?.name.orEmpty(),
                service?.websiteUrl.orEmpty(),
                account.displayName,
                account.email.orEmpty(),
                account.username.orEmpty(),
                category?.name.orEmpty(),
                account.status,
                if (account.archivedAtEpochMillis != null) "Yes" else "No",
                account.purpose.orEmpty(),
                account.recoveryEmailReference.orEmpty(),
                account.recoveryPhoneReference.orEmpty(),
                account.websiteUrl.orEmpty(),
                account.notes.orEmpty(),
                Instant.ofEpochMilli(account.createdAtEpochMillis).toString(),
                Instant.ofEpochMilli(account.updatedAtEpochMillis).toString()
            )
            sb.appendLine(row.joinToString(",") { escapeCsvCell(it) })
        }
        return sb.toString()
    }

    private fun escapeCsvCell(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun buildInnerPayloadZip(
        services: List<ServiceEntity>,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>,
        preferences: List<AppPreferenceEntity>,
        assets: List<ServiceAssetEntity>,
        exportMode: ExportMode
    ): ByteArray {
        val assetPayloads = mutableMapOf<String, ByteArray>()
        val assetsManifestArray = JSONArray()

        for (asset in assets) {
            val bytes = assetStorage.readAssetBytes(asset.relativePath) ?: continue
            val actualSha = ServiceAssetStorage.sha256Hex(bytes)
            assetPayloads[asset.id] = bytes
            assetsManifestArray.put(
                JSONObject().apply {
                    put("id", asset.id)
                    put("mimeType", asset.mimeType)
                    put("width", asset.width)
                    put("height", asset.height)
                    put("byteSize", bytes.size)
                    put("sha256", actualSha)
                }
            )
        }

        val manifestJson = JSONObject().apply {
            put("dataSchemaVersion", CURRENT_DATA_SCHEMA_VERSION)
            put("applicationName", "Digital Account Organizer")
            put("exportedAt", Instant.ofEpochMilli(clockMillis()).toString())
            put("exportMode", exportMode.name)
            put("servicesCount", services.size)
            put("categoriesCount", categories.size)
            put("accountsCount", accounts.size)
            put("assetsCount", assetPayloads.size)
            put("assets", assetsManifestArray)
        }.toString(2).toByteArray(StandardCharsets.UTF_8)

        val dataJson = JSONObject().apply {
            put(
                "services",
                JSONArray().apply {
                    for (s in services) {
                        put(
                            JSONObject().apply {
                                put("id", s.id)
                                put("name", s.name)
                                put("websiteUrl", s.websiteUrl ?: JSONObject.NULL)
                                put("description", s.description ?: JSONObject.NULL)
                                put("iconAssetId", s.iconAssetId?.takeIf { it in assetPayloads } ?: JSONObject.NULL)
                                put("createdAt", Instant.ofEpochMilli(s.createdAtEpochMillis).toString())
                                put("updatedAt", Instant.ofEpochMilli(s.updatedAtEpochMillis).toString())
                                put(
                                    "archivedAt",
                                    s.archivedAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL
                                )
                            }
                        )
                    }
                }
            )
            put(
                "categories",
                JSONArray().apply {
                    for (c in categories) {
                        put(
                            JSONObject().apply {
                                put("id", c.id)
                                put("name", c.name)
                                put("description", c.description ?: JSONObject.NULL)
                                put("createdAt", Instant.ofEpochMilli(c.createdAtEpochMillis).toString())
                                put("updatedAt", Instant.ofEpochMilli(c.updatedAtEpochMillis).toString())
                            }
                        )
                    }
                }
            )
            put(
                "accounts",
                JSONArray().apply {
                    for (a in accounts) {
                        put(
                            JSONObject().apply {
                                put("id", a.id)
                                put("serviceId", a.serviceId)
                                put("displayName", a.displayName)
                                put("email", a.email ?: JSONObject.NULL)
                                put("username", a.username ?: JSONObject.NULL)
                                put("categoryId", a.categoryId ?: JSONObject.NULL)
                                put("purpose", a.purpose ?: JSONObject.NULL)
                                put("recoveryEmailReference", a.recoveryEmailReference ?: JSONObject.NULL)
                                put("recoveryPhoneReference", a.recoveryPhoneReference ?: JSONObject.NULL)
                                put("websiteUrl", a.websiteUrl ?: JSONObject.NULL)
                                put("notes", a.notes ?: JSONObject.NULL)
                                put("status", a.status)
                                put("createdAt", Instant.ofEpochMilli(a.createdAtEpochMillis).toString())
                                put("updatedAt", Instant.ofEpochMilli(a.updatedAtEpochMillis).toString())
                                put(
                                    "archivedAt",
                                    a.archivedAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL
                                )
                            }
                        )
                    }
                }
            )
            put(
                "preferences",
                JSONArray().apply {
                    for (p in preferences) {
                        put(
                            JSONObject().apply {
                                put("key", p.key)
                                put("value", p.value)
                                put("updatedAt", Instant.ofEpochMilli(p.updatedAtEpochMillis).toString())
                            }
                        )
                    }
                }
            )
        }.toString(2).toByteArray(StandardCharsets.UTF_8)

        return ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestJson)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("data.json"))
                zos.write(dataJson)
                zos.closeEntry()

                for ((assetId, bytes) in assetPayloads) {
                    zos.putNextEntry(ZipEntry("assets/$assetId.png"))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            baos.toByteArray()
        }
    }

    private suspend fun parseAndDiffInnerZip(decryptedZipBytes: ByteArray): StagedBackupPackage {
        var manifestBytes: ByteArray? = null
        var dataBytes: ByteArray? = null
        val assetBytesById = mutableMapOf<String, ByteArray>()
        var totalDecompressed = 0
        var entryCount = 0

        ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_INNER_ENTRIES) { "Backup archive contains too many entries." }
                validateSafeEntryName(entry.name)

                val bytes = readBoundedEntryBytes(zis, MAX_DECOMPRESSED_TOTAL_BYTES - totalDecompressed)
                totalDecompressed += bytes.size
                require(totalDecompressed <= MAX_DECOMPRESSED_TOTAL_BYTES) {
                    "Backup payload exceeds maximum decompressed size."
                }

                when {
                    entry.name == "manifest.json" -> {
                        require(manifestBytes == null) { "Duplicate manifest.json entry." }
                        manifestBytes = bytes
                    }
                    entry.name == "data.json" -> {
                        require(dataBytes == null) { "Duplicate data.json entry." }
                        dataBytes = bytes
                    }
                    entry.name.startsWith("assets/") && entry.name.endsWith(".png") -> {
                        val assetId = entry.name.removePrefix("assets/").removeSuffix(".png")
                        require(assetId.isNotBlank() && !assetId.contains("/")) {
                            "Invalid asset entry name: ${entry.name}"
                        }
                        assetBytesById[assetId] = bytes
                    }
                    else -> throw IllegalArgumentException("Unexpected inner entry in backup payload: ${entry.name}")
                }
                zis.closeEntry()
            }
        }

        val manifestObj = JSONObject(
            String(requireNotNull(manifestBytes) { "Missing manifest.json." }, StandardCharsets.UTF_8)
        )
        val dataObj = JSONObject(
            String(requireNotNull(dataBytes) { "Missing data.json." }, StandardCharsets.UTF_8)
        )

        val schemaVersion = manifestObj.optInt("dataSchemaVersion", -1)
        require(schemaVersion == CURRENT_DATA_SCHEMA_VERSION) {
            "Unsupported backup data schema version: $schemaVersion"
        }

        val expectedAssetsArray = manifestObj.optJSONArray("assets") ?: JSONArray()
        val assetChecksumById = mutableMapOf<String, String>()
        for (i in 0 until expectedAssetsArray.length()) {
            val aObj = expectedAssetsArray.getJSONObject(i)
            val id = aObj.getString("id")
            val sha = aObj.getString("sha256")
            val bytes = assetBytesById[id]
                ?: throw IllegalArgumentException("Missing asset binary for declared asset $id")
            val actualSha = ServiceAssetStorage.sha256Hex(bytes)
            require(actualSha.equals(sha, ignoreCase = true)) {
                "Checksum verification failed for asset $id."
            }
            assetChecksumById[id] = sha
        }

        val servicesArray = dataObj.getJSONArray("services")
        val categoriesArray = dataObj.getJSONArray("categories")
        val accountsArray = dataObj.getJSONArray("accounts")
        val prefsArray = dataObj.optJSONArray("preferences") ?: JSONArray()

        require(servicesArray.length() == manifestObj.getInt("servicesCount")) {
            "Service count mismatch against manifest."
        }
        require(categoriesArray.length() == manifestObj.getInt("categoriesCount")) {
            "Category count mismatch against manifest."
        }
        require(accountsArray.length() == manifestObj.getInt("accountsCount")) {
            "Account count mismatch against manifest."
        }

        val incomingServices = (0 until servicesArray.length()).map { idx ->
            val o = servicesArray.getJSONObject(idx)
            val name = o.getString("name").trim()
            require(name.isNotEmpty()) { "Service record has blank name." }
            ServiceEntity(
                id = o.getString("id"),
                name = name,
                normalizedName = Normalizer.normalizeName(name),
                websiteUrl = o.optNullableString("websiteUrl"),
                description = o.optNullableString("description"),
                iconAssetId = o.optNullableString("iconAssetId"),
                createdAtEpochMillis = parseIsoInstant(o.getString("createdAt")),
                updatedAtEpochMillis = parseIsoInstant(o.getString("updatedAt")),
                archivedAtEpochMillis = o.optNullableString("archivedAt")?.let { parseIsoInstant(it) }
            )
        }

        val incomingCategories = (0 until categoriesArray.length()).map { idx ->
            val o = categoriesArray.getJSONObject(idx)
            val name = o.getString("name").trim()
            require(name.isNotEmpty()) { "Category record has blank name." }
            CategoryEntity(
                id = o.getString("id"),
                name = name,
                normalizedName = Normalizer.normalizeName(name),
                description = o.optNullableString("description"),
                createdAtEpochMillis = parseIsoInstant(o.getString("createdAt")),
                updatedAtEpochMillis = parseIsoInstant(o.getString("updatedAt"))
            )
        }

        val incomingAccounts = (0 until accountsArray.length()).map { idx ->
            val o = accountsArray.getJSONObject(idx)
            val displayName = o.getString("displayName").trim()
            require(displayName.isNotEmpty()) { "Account record has blank displayName." }
            val statusStr = o.getString("status")
            AccountStatus.valueOf(statusStr) // Validate enum value
            val email = o.optNullableString("email")
            val username = o.optNullableString("username")
            AccountEntity(
                id = o.getString("id"),
                serviceId = o.getString("serviceId"),
                displayName = displayName,
                normalizedDisplayName = Normalizer.normalizeName(displayName),
                email = email,
                normalizedEmail = Normalizer.normalizeOptionalIdentifier(email),
                username = username,
                normalizedUsername = Normalizer.normalizeOptionalIdentifier(username),
                categoryId = o.optNullableString("categoryId"),
                purpose = o.optNullableString("purpose"),
                recoveryEmailReference = o.optNullableString("recoveryEmailReference"),
                recoveryPhoneReference = o.optNullableString("recoveryPhoneReference"),
                websiteUrl = o.optNullableString("websiteUrl"),
                notes = o.optNullableString("notes"),
                status = statusStr,
                createdAtEpochMillis = parseIsoInstant(o.getString("createdAt")),
                updatedAtEpochMillis = parseIsoInstant(o.getString("updatedAt")),
                archivedAtEpochMillis = o.optNullableString("archivedAt")?.let { parseIsoInstant(it) }
            )
        }

        val incomingPrefs = (0 until prefsArray.length()).mapNotNull { idx ->
            val o = prefsArray.getJSONObject(idx)
            val key = o.getString("key")
            if (key !in ALLOWLISTED_PREF_KEYS) return@mapNotNull null
            AppPreferenceEntity(
                key = key,
                value = o.getString("value"),
                updatedAtEpochMillis = parseIsoInstant(o.getString("updatedAt"))
            )
        }

        // Diff against current database
        val db = databaseFactory.getUnlockedDatabase()
        val localServicesById = db.serviceDao().getAllServices().associateBy { it.id }
        val localServicesByNorm = db.serviceDao().getAllServices().associateBy { it.normalizedName }
        val localCategoriesById = db.categoryDao().getAllCategories().associateBy { it.id }
        val localCategoriesByNorm = db.categoryDao().getAllCategories().associateBy { it.normalizedName }
        val localAccountsById = db.accountDao().getAllAccounts().associateBy { it.id }
        val localAccounts = db.accountDao().getAllAccounts()

        val conflicts = mutableListOf<StagedRecordConflict>()
        var newServicesCount = 0
        var newCategoriesCount = 0
        var newAccountsCount = 0

        for (cat in incomingCategories) {
            val byId = localCategoriesById[cat.id]
            val byNorm = localCategoriesByNorm[cat.normalizedName]
            when {
                byId != null -> {
                    conflicts.add(
                        StagedRecordConflict(
                            recordType = "CATEGORY",
                            incomingId = cat.id,
                            localMatchId = byId.id,
                            title = "Category: ${cat.name}",
                            subtitle = "Local category '${byId.name}' has the same ID",
                            kind = ConflictKind.ID_CONFLICT
                        )
                    )
                }
                byNorm != null -> {
                    conflicts.add(
                        StagedRecordConflict(
                            recordType = "CATEGORY",
                            incomingId = cat.id,
                            localMatchId = byNorm.id,
                            title = "Category: ${cat.name}",
                            subtitle = "Matches local category name '${byNorm.name}'",
                            kind = ConflictKind.POSSIBLE_DUPLICATE
                        )
                    )
                }
                else -> newCategoriesCount++
            }
        }

        for (svc in incomingServices) {
            val byId = localServicesById[svc.id]
            val byNorm = localServicesByNorm[svc.normalizedName]
            when {
                byId != null -> {
                    conflicts.add(
                        StagedRecordConflict(
                            recordType = "SERVICE",
                            incomingId = svc.id,
                            localMatchId = byId.id,
                            title = "Service: ${svc.name}",
                            subtitle = "Local service '${byId.name}' has the same ID",
                            kind = ConflictKind.ID_CONFLICT
                        )
                    )
                }
                byNorm != null -> {
                    conflicts.add(
                        StagedRecordConflict(
                            recordType = "SERVICE",
                            incomingId = svc.id,
                            localMatchId = byNorm.id,
                            title = "Service: ${svc.name}",
                            subtitle = "Matches local service name '${byNorm.name}'",
                            kind = ConflictKind.POSSIBLE_DUPLICATE
                        )
                    )
                }
                else -> newServicesCount++
            }
        }

        for (acc in incomingAccounts) {
            val byId = localAccountsById[acc.id]
            val duplicateHeuristic = localAccounts.firstOrNull { local ->
                local.id != acc.id &&
                    local.normalizedDisplayName == acc.normalizedDisplayName &&
                    local.normalizedEmail == acc.normalizedEmail &&
                    local.normalizedUsername == acc.normalizedUsername
            }
            when {
                byId != null -> {
                    conflicts.add(
                        StagedRecordConflict(
                            recordType = "ACCOUNT",
                            incomingId = acc.id,
                            localMatchId = byId.id,
                            title = "Account: ${acc.displayName}",
                            subtitle = "Local account '${byId.displayName}' shares the same UUID",
                            kind = ConflictKind.ID_CONFLICT
                        )
                    )
                }
                duplicateHeuristic != null -> {
                    conflicts.add(
                        StagedRecordConflict(
                            recordType = "ACCOUNT",
                            incomingId = acc.id,
                            localMatchId = duplicateHeuristic.id,
                            title = "Account: ${acc.displayName}",
                            subtitle = "Similar to local account '${duplicateHeuristic.displayName}'",
                            kind = ConflictKind.POSSIBLE_DUPLICATE
                        )
                    )
                }
                else -> newAccountsCount++
            }
        }

        return StagedBackupPackage(
            exportedAtIso = manifestObj.optString("exportedAt", ""),
            dataSchemaVersion = schemaVersion,
            services = incomingServices,
            categories = incomingCategories,
            accounts = incomingAccounts,
            preferences = incomingPrefs,
            assetBytesById = assetBytesById,
            assetChecksumById = assetChecksumById,
            conflicts = conflicts,
            newServicesCount = newServicesCount,
            newCategoriesCount = newCategoriesCount,
            newAccountsCount = newAccountsCount
        )
    }

    private fun canonicalHeaderAad(
        format: String,
        formatVersion: Int,
        cipher: String,
        kdfParameters: KdfParameters,
        salt: ByteArray,
        nonce: ByteArray,
        payloadEncoding: String
    ): ByteArray {
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        val canonical = buildString {
            append("dao-backup-aad|")
            append("format=$format|v=$formatVersion|cipher=$cipher|")
            append("kdf=${kdfParameters.algorithm}:${kdfParameters.version}:${kdfParameters.memoryKiB}:${kdfParameters.iterations}:${kdfParameters.parallelism}:${kdfParameters.outputLengthBytes}|")
            append("salt=$saltB64|nonce=$nonceB64|enc=$payloadEncoding")
        }
        return canonical.toByteArray(StandardCharsets.UTF_8)
    }

    private fun validateSafeEntryName(name: String) {
        require(name.isNotBlank()) { "Archive entry name is blank." }
        require(!name.startsWith("/") && !name.startsWith("\\")) {
            "Archive entry must not use an absolute path: $name"
        }
        require(!name.contains("..")) {
            "Archive path traversal detected in entry: $name"
        }
    }

    private fun readBoundedEntryBytes(zis: ZipInputStream, maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "Exceeded maximum archive size limit." }
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = zis.read(buffer)
            if (read == -1) break
            total += read
            require(total <= maxBytes) { "Archive entry exceeds maximum allowed byte size." }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return getString(key).takeIf { it.isNotEmpty() }
    }

    private fun parseIsoInstant(iso: String): Long =
        Instant.parse(iso).toEpochMilli()

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        const val FORMAT_ID = "digital-account-organizer-backup"
        const val CURRENT_FORMAT_VERSION = 1
        const val CURRENT_DATA_SCHEMA_VERSION = 1
        const val CIPHER_ID = "AES-256-GCM"
        const val PAYLOAD_ENCODING_ID = "zip-utf8"
        const val ENTRY_HEADER_JSON = "header.json"
        const val ENTRY_PAYLOAD_ENC = "payload.enc"

        const val MIN_BACKUP_PASSPHRASE_CHARS = 8
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128

        private const val MAX_HEADER_BYTES = 64 * 1024 // 64 KiB
        private const val MAX_BACKUP_FILE_BYTES = 50 * 1024 * 1024 // 50 MiB
        private const val MAX_DECOMPRESSED_TOTAL_BYTES = 64 * 1024 * 1024 // 64 MiB
        private const val MAX_INNER_ENTRIES = 512

        val ALLOWLISTED_PREF_KEYS = setOf(OrganizerRepository.PREF_KEY_THEME)
    }
}
