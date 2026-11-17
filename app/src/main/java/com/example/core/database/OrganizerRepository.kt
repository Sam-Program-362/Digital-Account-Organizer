package com.example.core.database

import android.net.Uri
import androidx.room.withTransaction
import com.example.core.files.ServiceAssetStorage
import com.example.core.files.WebsiteLogoFetcher
import com.example.core.security.DatabaseKeyManager
import com.example.domain.model.AccountRecord
import com.example.domain.model.AccountStatus
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.AppColorPalette
import com.example.domain.model.CategoryRecord
import com.example.domain.model.CategoryWithCount
import com.example.domain.model.DashboardSummary
import com.example.domain.model.LinkedServiceSummary
import com.example.domain.model.Normalizer
import com.example.domain.model.ServiceAccountRecord
import com.example.domain.model.ServiceAccountWithDetails
import com.example.domain.model.ServiceAssetRecord
import com.example.domain.model.ServiceDeleteResolution
import com.example.domain.model.ServiceRecord
import com.example.domain.model.ServiceWithCount
import com.example.domain.model.ThemePreference
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class FetchedServiceLogoResult(
    val asset: ServiceAssetRecord,
    val sourceHost: String,
    val normalizedWebsiteUrl: String
)

sealed interface SaveServiceOutcome {
    data class Saved(val service: ServiceRecord) : SaveServiceOutcome
    data class DuplicateNameDetected(val existingServiceName: String) : SaveServiceOutcome
    data class ValidationError(val message: String) : SaveServiceOutcome
}

sealed interface SaveCategoryOutcome {
    data class Saved(val category: CategoryRecord) : SaveCategoryOutcome
    data class DuplicateNameDetected(val existingCategoryName: String) : SaveCategoryOutcome
    data class ValidationError(val message: String) : SaveCategoryOutcome
}

sealed interface SaveAccountOutcome {
    data class Saved(val account: AccountRecord) : SaveAccountOutcome
    data class ValidationError(val message: String) : SaveAccountOutcome
}

sealed interface SaveServiceAccountOutcome {
    data class Saved(val serviceAccount: ServiceAccountRecord) : SaveServiceAccountOutcome
    data class ValidationError(val message: String) : SaveServiceAccountOutcome
}

class OrganizerRepository(
    private val databaseFactory: EncryptedDatabaseFactory,
    private val assetStorage: ServiceAssetStorage,
    private val keyManager: DatabaseKeyManager? = null,
    private val websiteLogoFetcher: WebsiteLogoFetcher = WebsiteLogoFetcher(
        context = null,
        assetStorage = assetStorage
    ),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val db: OrganizerDatabase
        get() = databaseFactory.getUnlockedDatabase()

    fun encryptSecret(plaintext: String?): String? {
        val cleaned = plaintext?.takeIf { it.isNotEmpty() } ?: return null
        return keyManager?.encryptSecretField(cleaned) ?: cleaned
    }

    fun decryptSecret(stored: String?): String? {
        val raw = stored?.takeIf { it.isNotEmpty() } ?: return null
        return keyManager?.decryptSecretField(raw) ?: raw
    }

    fun observeServicesWithCounts(): Flow<List<ServiceWithCount>> {
        return combine(
            db.serviceDao().observeAllServices(),
            db.serviceAssetDao().observeAllAssets(),
            db.serviceAccountDao().observeAllServiceAccounts()
        ) { services, assets, serviceAccounts ->
            val assetMap = assets.associateBy({ it.id }, { it.toDomain() })
            val entriesByService = serviceAccounts.groupBy { it.serviceId }
            services.map { entity ->
                val linked = entriesByService[entity.id].orEmpty()
                ServiceWithCount(
                    service = entity.toDomain(),
                    iconAsset = entity.iconAssetId?.let { assetMap[it] },
                    activeAccountCount = linked.count { it.archivedAtEpochMillis == null },
                    totalAccountCount = linked.size
                )
            }
        }
    }

    fun observeCategoriesWithCounts(): Flow<List<CategoryWithCount>> {
        return combine(
            db.categoryDao().observeAllCategories(),
            db.accountDao().observeAllAccounts()
        ) { categories, accounts ->
            val accountsByCategory = accounts.groupBy { it.categoryId }
            categories.map { entity ->
                val linked = accountsByCategory[entity.id].orEmpty()
                CategoryWithCount(
                    category = entity.toDomain(),
                    activeAccountCount = linked.count { it.archivedAtEpochMillis == null },
                    totalAccountCount = linked.size
                )
            }
        }
    }

    /**
     * Observes standalone core Accounts (defaulting to accounts without any service, e.g.,
     * example123@gmail.com) along with their category and the list of Services that link to them.
     */
    fun observeAccountsWithDetails(): Flow<List<AccountWithDetails>> {
        return combine(
            db.accountDao().observeAllAccounts(),
            db.categoryDao().observeAllCategories(),
            db.serviceAccountDao().observeAllServiceAccounts(),
            db.serviceDao().observeAllServices(),
            db.serviceAssetDao().observeAllAssets()
        ) { accounts, categories, serviceAccounts, services, assets ->
            val categoryMap = categories.associateBy({ it.id }, { it.toDomain() })
            val serviceMap = services.associateBy({ it.id }, { it.toDomain() })
            val assetMap = assets.associateBy({ it.id }, { it.toDomain() })
            val serviceAccountsByLinkedId = serviceAccounts
                .filter { it.linkedAccountId != null }
                .groupBy { it.linkedAccountId!! }

            accounts.map { accountEntity ->
                val domainAccount = accountEntity.toDomain()
                val category = accountEntity.categoryId?.let { categoryMap[it] }
                val linkedServiceEntries = serviceAccountsByLinkedId[accountEntity.id]
                    .orEmpty()
                    .mapNotNull { saEntity ->
                        val svc = serviceMap[saEntity.serviceId] ?: return@mapNotNull null
                        val asset = svc.iconAssetId?.let { assetMap[it] }
                        val domainSa = saEntity.toDomain()
                        LinkedServiceSummary(
                            serviceAccountId = domainSa.id,
                            service = svc,
                            serviceAsset = asset,
                            entryLabel = domainSa.displayName,
                            identifier = domainSa.email ?: domainAccount.email ?: domainSa.username ?: domainAccount.username,
                            hasOwnPassword = domainSa.hasPassword,
                            status = domainSa.status
                        )
                    }

                AccountWithDetails(
                    account = domainAccount,
                    category = category,
                    linkedServices = linkedServiceEntries
                )
            }
        }
    }

    /**
     * Observes all Service-specific Account entries across all Services.
     */
    fun observeServiceAccountsWithDetails(): Flow<List<ServiceAccountWithDetails>> {
        return combine(
            db.serviceAccountDao().observeAllServiceAccounts(),
            db.serviceDao().observeAllServices(),
            db.serviceAssetDao().observeAllAssets(),
            db.accountDao().observeAllAccounts(),
            db.categoryDao().observeAllCategories()
        ) { serviceAccounts, services, assets, standaloneAccounts, categories ->
            val serviceMap = services.associateBy({ it.id }, { it.toDomain() })
            val assetMap = assets.associateBy({ it.id }, { it.toDomain() })
            val accountMap = standaloneAccounts.associateBy({ it.id }, { it.toDomain() })
            val categoryMap = categories.associateBy({ it.id }, { it.toDomain() })

            serviceAccounts.mapNotNull { saEntity ->
                val service = serviceMap[saEntity.serviceId] ?: return@mapNotNull null
                val asset = service.iconAssetId?.let { assetMap[it] }
                val linkedAccount = saEntity.linkedAccountId?.let { accountMap[it] }
                val category = saEntity.categoryId?.let { categoryMap[it] }
                ServiceAccountWithDetails(
                    serviceAccount = saEntity.toDomain(),
                    service = service,
                    serviceAsset = asset,
                    linkedAccount = linkedAccount,
                    category = category
                )
            }
        }
    }

    /**
     * Dashboard summary counts ONLY standalone core Accounts in [DashboardSummary.totalActiveAccounts]
     * and [DashboardSummary.totalArchivedAccounts]—Service Account entries do NOT inflate Home screen
     * account totals.
     */
    fun observeDashboardSummary(): Flow<DashboardSummary> {
        return combine(
            observeAccountsWithDetails(),
            observeServicesWithCounts(),
            observeCategoriesWithCounts()
        ) { accounts, services, categories ->
            val activeAccounts = accounts.filter { !it.account.isArchived }
            val archivedAccounts = accounts.filter { it.account.isArchived }
            val activeServices = services.filter { !it.service.isArchived }
            DashboardSummary(
                totalActiveAccounts = activeAccounts.size,
                totalArchivedAccounts = archivedAccounts.size,
                totalServices = activeServices.size,
                totalCategories = categories.size,
                recentAccounts = activeAccounts.sortedByDescending { it.account.updatedAtEpochMillis }.take(6),
                topCategories = categories.sortedByDescending { it.activeAccountCount }.take(6),
                topServices = activeServices.sortedByDescending { it.activeAccountCount }.take(6)
            )
        }
    }

    fun observeThemePreference(): Flow<ThemePreference> {
        return db.appPreferenceDao().observeAllPreferences().map { prefs ->
            val raw = prefs.firstOrNull { it.key == PREF_KEY_THEME }?.value
            ThemePreference.fromStorage(raw)
        }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        db.appPreferenceDao().upsertPreference(
            AppPreferenceEntity(
                key = PREF_KEY_THEME,
                value = theme.storageValue,
                updatedAtEpochMillis = clockMillis()
            )
        )
    }

    fun observeColorPalette(): Flow<AppColorPalette> {
        return db.appPreferenceDao().observeAllPreferences().map { prefs ->
            val raw = prefs.firstOrNull { it.key == PREF_KEY_COLOR_PALETTE }?.value
            AppColorPalette.fromStorage(raw)
        }
    }

    suspend fun setColorPalette(palette: AppColorPalette) {
        db.appPreferenceDao().upsertPreference(
            AppPreferenceEntity(
                key = PREF_KEY_COLOR_PALETTE,
                value = palette.storageValue,
                updatedAtEpochMillis = clockMillis()
            )
        )
    }

    suspend fun importServiceIconFromUri(uri: Uri): ServiceAssetRecord {
        val entity = assetStorage.importFromUri(uri)
        db.serviceAssetDao().upsertAsset(entity)
        return entity.toDomain()
    }

    suspend fun importServiceIconFromBytes(bytes: ByteArray): ServiceAssetRecord {
        val entity = assetStorage.importFromRawBytes(bytes)
        db.serviceAssetDao().upsertAsset(entity)
        return entity.toDomain()
    }

    suspend fun fetchServiceIconFromWebsiteUrl(rawWebsiteUrl: String): FetchedServiceLogoResult {
        val fetched = websiteLogoFetcher.fetchAndStoreLogo(rawWebsiteUrl)
        db.serviceAssetDao().upsertAsset(fetched.assetEntity)
        return FetchedServiceLogoResult(
            asset = fetched.assetEntity.toDomain(),
            sourceHost = fetched.sourceHost,
            normalizedWebsiteUrl = fetched.normalizedWebsiteUrl
        )
    }

    suspend fun saveService(
        existingId: String? = null,
        name: String,
        websiteUrl: String?,
        description: String?,
        iconAssetId: String?,
        allowDuplicateVariation: Boolean = false
    ): SaveServiceOutcome {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return SaveServiceOutcome.ValidationError("Service name is required.")
        }
        if (!Normalizer.validateOptionalUrl(websiteUrl)) {
            return SaveServiceOutcome.ValidationError("Please enter a valid website URL (e.g. https://example.com).")
        }

        val normalized = Normalizer.normalizeName(trimmedName)
        val matches = db.serviceDao().findByNormalizedName(normalized)
            .filter { it.id != existingId }
        if (matches.isNotEmpty() && !allowDuplicateVariation) {
            return SaveServiceOutcome.DuplicateNameDetected(matches.first().name)
        }

        val now = clockMillis()
        val existing = existingId?.let { db.serviceDao().getServiceById(it) }
        val entity = ServiceEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = trimmedName,
            normalizedName = normalized,
            websiteUrl = Normalizer.cleanOptionalText(websiteUrl),
            description = Normalizer.cleanOptionalText(description),
            iconAssetId = iconAssetId,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            archivedAtEpochMillis = existing?.archivedAtEpochMillis
        )

        if (existing == null) {
            db.serviceDao().insertService(entity)
        } else {
            db.serviceDao().updateService(entity)
            cleanupOrphanedAssets()
        }
        return SaveServiceOutcome.Saved(entity.toDomain())
    }

    suspend fun setServiceArchived(serviceId: String, archived: Boolean) {
        val existing = db.serviceDao().getServiceById(serviceId) ?: return
        val now = clockMillis()
        db.serviceDao().updateService(
            existing.copy(
                updatedAtEpochMillis = now,
                archivedAtEpochMillis = if (archived) now else null
            )
        )
    }

    /**
     * Deletes or resolves a service safely without ever silently cascade-deleting service account entries.
     */
    suspend fun deleteOrResolveService(
        serviceId: String,
        resolution: ServiceDeleteResolution,
        reassignTargetServiceId: String? = null
    ) {
        val database = db
        database.withTransaction {
            val linkedCount = database.serviceAccountDao().countServiceAccountsForService(serviceId)
            val now = clockMillis()
            if (linkedCount == 0) {
                database.serviceDao().deleteServiceById(serviceId)
            } else {
                when (resolution) {
                    ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD -> {
                        val existing = database.serviceDao().getServiceById(serviceId) ?: return@withTransaction
                        database.serviceDao().updateService(
                            existing.copy(
                                updatedAtEpochMillis = now,
                                archivedAtEpochMillis = now
                            )
                        )
                    }
                    ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE -> {
                        val targetId = requireNotNull(reassignTargetServiceId) {
                            "A destination service is required to reassign service accounts."
                        }
                        require(targetId != serviceId) {
                            "Destination service must be different from the deleted service."
                        }
                        val targetExists = database.serviceDao().getServiceById(targetId) != null
                        require(targetExists) { "Destination service does not exist." }
                        database.serviceAccountDao().reassignServiceForServiceAccounts(serviceId, targetId, now)
                        database.serviceDao().deleteServiceById(serviceId)
                    }
                    ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS -> {
                        database.serviceAccountDao().deleteServiceAccountsForService(serviceId)
                        database.serviceDao().deleteServiceById(serviceId)
                    }
                }
            }
        }
        cleanupOrphanedAssets()
    }

    suspend fun saveCategory(
        existingId: String? = null,
        name: String,
        description: String?
    ): SaveCategoryOutcome {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return SaveCategoryOutcome.ValidationError("Category name is required.")
        }
        val normalized = Normalizer.normalizeName(trimmedName)
        val conflict = db.categoryDao().findByNormalizedName(normalized)
        if (conflict != null && conflict.id != existingId) {
            return SaveCategoryOutcome.DuplicateNameDetected(conflict.name)
        }

        val now = clockMillis()
        val existing = existingId?.let { db.categoryDao().getCategoryById(it) }
        val entity = CategoryEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = trimmedName,
            normalizedName = normalized,
            description = Normalizer.cleanOptionalText(description),
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now
        )
        if (existing == null) {
            db.categoryDao().insertCategory(entity)
        } else {
            db.categoryDao().updateCategory(entity)
        }
        return SaveCategoryOutcome.Saved(entity.toDomain())
    }

    /**
     * Deletes a category while preserving all standalone accounts and service accounts.
     */
    suspend fun deleteCategorySafely(
        categoryId: String,
        reassignToCategoryId: String? = null
    ) {
        val database = db
        database.withTransaction {
            val now = clockMillis()
            database.accountDao().reassignCategoryForAccounts(
                sourceCategoryId = categoryId,
                targetCategoryId = reassignToCategoryId,
                updatedAt = now
            )
            database.serviceAccountDao().reassignCategoryForServiceAccounts(
                sourceCategoryId = categoryId,
                targetCategoryId = reassignToCategoryId,
                updatedAt = now
            )
            database.categoryDao().deleteCategoryById(categoryId)
        }
    }

    /**
     * Saves a standalone core Account (independent of any Service) with its own encrypted password and notes.
     * When editing an existing account ([existingId] != null):
     * - If [clearSavedPassword] is true, the stored password is removed.
     * - If [password] is non-blank, it is encrypted and replaces the stored password.
     * - If [password] is null or blank and [clearSavedPassword] is false, the existing encrypted password is preserved.
     */
    suspend fun saveAccount(
        existingId: String? = null,
        displayName: String,
        email: String?,
        username: String?,
        password: String? = null,
        clearSavedPassword: Boolean = false,
        categoryId: String?,
        purpose: String?,
        recoveryEmailReference: String?,
        recoveryPhoneReference: String?,
        notes: String?,
        status: AccountStatus
    ): SaveAccountOutcome {
        val cleanedEmail = Normalizer.cleanOptionalText(email)
        val cleanedUsername = Normalizer.cleanOptionalText(username)
        val effectiveLabel = displayName.trim().ifEmpty {
            cleanedEmail ?: cleanedUsername.orEmpty()
        }
        if (effectiveLabel.isEmpty()) {
            return SaveAccountOutcome.ValidationError("Please provide an Account Label, Email (e.g. example123@gmail.com), or Username.")
        }
        if (categoryId != null && db.categoryDao().getCategoryById(categoryId) == null) {
            return SaveAccountOutcome.ValidationError("Selected category does not exist.")
        }

        val now = clockMillis()
        val existing = existingId?.let { db.accountDao().getAccountById(it) }
        val trimmedNewPassword = password?.trim()?.takeIf { it.isNotEmpty() }
        val encryptedPwd = when {
            clearSavedPassword -> null
            trimmedNewPassword != null -> encryptSecret(trimmedNewPassword)
            else -> existing?.encryptedPassword
        }
        val entity = AccountEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            displayName = effectiveLabel,
            normalizedDisplayName = Normalizer.normalizeName(effectiveLabel),
            email = cleanedEmail,
            normalizedEmail = Normalizer.normalizeOptionalIdentifier(cleanedEmail),
            username = cleanedUsername,
            normalizedUsername = Normalizer.normalizeOptionalIdentifier(cleanedUsername),
            encryptedPassword = encryptedPwd,
            categoryId = categoryId,
            purpose = Normalizer.cleanOptionalText(purpose),
            recoveryEmailReference = Normalizer.cleanOptionalText(recoveryEmailReference),
            recoveryPhoneReference = Normalizer.cleanOptionalText(recoveryPhoneReference),
            notes = notes?.takeIf { it.isNotBlank() },
            status = status.name,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            archivedAtEpochMillis = existing?.archivedAtEpochMillis
        )

        if (existing == null) {
            db.accountDao().insertAccount(entity)
        } else {
            db.accountDao().updateAccount(entity)
        }
        return SaveAccountOutcome.Saved(entity.toDomain())
    }

    suspend fun setAccountArchived(accountId: String, archived: Boolean) {
        val existing = db.accountDao().getAccountById(accountId) ?: return
        val now = clockMillis()
        db.accountDao().updateAccount(
            existing.copy(
                updatedAtEpochMillis = now,
                archivedAtEpochMillis = if (archived) now else null
            )
        )
    }

    suspend fun deleteAccountPermanently(accountId: String) {
        db.accountDao().deleteAccountById(accountId)
    }

    /**
     * Saves a Service-specific Account entry under a [serviceId].
     * Supports linking an existing standalone [AccountEntity] ([linkedAccountId]) OR entering
     * a custom unlinked email/username/handle, plus an optional encrypted password and notes.
     * When editing an existing entry ([existingId] != null), leaving [password] blank preserves
     * the existing encrypted password unless [clearSavedPassword] is true.
     */
    suspend fun saveServiceAccount(
        existingId: String? = null,
        serviceId: String,
        linkedAccountId: String?,
        displayName: String,
        email: String?,
        username: String?,
        password: String?,
        clearSavedPassword: Boolean = false,
        categoryId: String?,
        purpose: String?,
        recoveryEmailReference: String?,
        recoveryPhoneReference: String?,
        websiteUrl: String?,
        notes: String?,
        status: AccountStatus
    ): SaveServiceAccountOutcome {
        if (serviceId.isBlank() || db.serviceDao().getServiceById(serviceId) == null) {
            return SaveServiceAccountOutcome.ValidationError("Please select an existing service.")
        }
        val linkedAcc = linkedAccountId?.let { db.accountDao().getAccountById(it) }
        val cleanedEmail = Normalizer.cleanOptionalText(email) ?: linkedAcc?.email
        val cleanedUsername = Normalizer.cleanOptionalText(username) ?: linkedAcc?.username
        val trimmedLabel = displayName.trim().ifEmpty {
            linkedAcc?.displayName ?: cleanedEmail ?: cleanedUsername.orEmpty()
        }
        if (trimmedLabel.isEmpty()) {
            return SaveServiceAccountOutcome.ValidationError("Entry label, linked account, email, or username is required.")
        }
        if (categoryId != null && db.categoryDao().getCategoryById(categoryId) == null) {
            return SaveServiceAccountOutcome.ValidationError("Selected category does not exist.")
        }
        if (!Normalizer.validateOptionalUrl(websiteUrl)) {
            return SaveServiceAccountOutcome.ValidationError("Please enter a valid URL.")
        }

        val now = clockMillis()
        val existing = existingId?.let { db.serviceAccountDao().getServiceAccountById(it) }
        val trimmedNewPassword = password?.trim()?.takeIf { it.isNotEmpty() }
        val encryptedPwd = when {
            clearSavedPassword -> null
            trimmedNewPassword != null -> encryptSecret(trimmedNewPassword)
            else -> existing?.encryptedPassword
        }
        val entity = ServiceAccountEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            serviceId = serviceId,
            linkedAccountId = linkedAcc?.id,
            displayName = trimmedLabel,
            normalizedDisplayName = Normalizer.normalizeName(trimmedLabel),
            email = cleanedEmail,
            normalizedEmail = Normalizer.normalizeOptionalIdentifier(cleanedEmail),
            username = cleanedUsername,
            normalizedUsername = Normalizer.normalizeOptionalIdentifier(cleanedUsername),
            encryptedPassword = encryptedPwd,
            categoryId = categoryId,
            purpose = Normalizer.cleanOptionalText(purpose),
            recoveryEmailReference = Normalizer.cleanOptionalText(recoveryEmailReference),
            recoveryPhoneReference = Normalizer.cleanOptionalText(recoveryPhoneReference),
            websiteUrl = Normalizer.cleanOptionalText(websiteUrl),
            notes = notes?.takeIf { it.isNotBlank() },
            status = status.name,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            archivedAtEpochMillis = existing?.archivedAtEpochMillis
        )

        if (existing == null) {
            db.serviceAccountDao().insertServiceAccount(entity)
        } else {
            db.serviceAccountDao().updateServiceAccount(entity)
        }
        return SaveServiceAccountOutcome.Saved(entity.toDomain())
    }

    suspend fun setServiceAccountArchived(serviceAccountId: String, archived: Boolean) {
        val existing = db.serviceAccountDao().getServiceAccountById(serviceAccountId) ?: return
        val now = clockMillis()
        db.serviceAccountDao().updateServiceAccount(
            existing.copy(
                updatedAtEpochMillis = now,
                archivedAtEpochMillis = if (archived) now else null
            )
        )
    }

    suspend fun deleteServiceAccountPermanently(serviceAccountId: String) {
        db.serviceAccountDao().deleteServiceAccountById(serviceAccountId)
    }

    suspend fun cleanupOrphanedAssets() {
        val orphans = db.serviceAssetDao().getOrphanedAssets()
        for (orphan in orphans) {
            assetStorage.deleteAssetFile(orphan.relativePath)
            db.serviceAssetDao().deleteAssetById(orphan.id)
        }
    }

    companion object {
        const val PREF_KEY_THEME = "theme_mode"
        const val PREF_KEY_COLOR_PALETTE = "color_palette"
    }
}
