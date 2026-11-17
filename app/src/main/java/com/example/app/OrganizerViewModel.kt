package com.example.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.OrganizerRepository
import com.example.core.database.SaveAccountOutcome
import com.example.core.database.SaveCategoryOutcome
import com.example.core.database.SaveServiceAccountOutcome
import com.example.core.database.SaveServiceOutcome
import com.example.core.files.BackupVaultStorage
import com.example.core.files.SavedVaultBackupEntry
import com.example.core.security.AutoLockTimeout
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.LockState
import com.example.core.security.clearChars
import com.example.domain.model.AccountStatus
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.AppColorPalette
import com.example.domain.model.CategoryWithCount
import com.example.domain.model.DashboardSummary
import com.example.domain.model.Normalizer
import com.example.domain.model.ServiceAccountWithDetails
import com.example.domain.model.ServiceAssetRecord
import com.example.domain.model.ServiceDeleteResolution
import com.example.domain.model.ServiceWithCount
import com.example.domain.model.ThemePreference
import com.example.feature.backup.BackupEngine
import com.example.feature.backup.ConflictResolution
import com.example.feature.backup.ExportMode
import com.example.feature.backup.ExportSelection
import com.example.feature.backup.StagedBackupPackage
import java.time.Instant
import javax.crypto.Cipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountSortOrder(val displayLabel: String) {
    RECENTLY_UPDATED("Recently Updated"),
    LABEL_ASC("Account Label (A–Z)"),
    EMAIL_ASC("Email / Username (A–Z)"),
    CREATED_NEWEST("Recently Created")
}

data class AccountFilterState(
    val searchQuery: String = "",
    val selectedCategoryId: String? = null,
    val selectedStatus: AccountStatus? = null,
    val showArchived: Boolean = false,
    val sortOrder: AccountSortOrder = AccountSortOrder.RECENTLY_UPDATED
)

data class DuplicateServicePrompt(
    val existingId: String?,
    val pendingName: String,
    val pendingWebsiteUrl: String?,
    val pendingDescription: String?,
    val pendingIconAssetId: String?,
    val conflictingServiceName: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizerViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val keyManager: DatabaseKeyManager = container.keyManager
    private val repository: OrganizerRepository = container.repository
    private val backupEngine: BackupEngine = container.backupEngine
    private val backupVaultStorage: BackupVaultStorage = container.backupVaultStorage

    val lockState: StateFlow<LockState> = keyManager.lockState

    private val _filterState = MutableStateFlow(AccountFilterState())
    val filterState: StateFlow<AccountFilterState> = _filterState.asStateFlow()

    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage.asStateFlow()

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    private val _oneTimeRecoveryCode = MutableStateFlow<String?>(null)
    val oneTimeRecoveryCode: StateFlow<String?> = _oneTimeRecoveryCode.asStateFlow()

    private val _duplicateServicePrompt = MutableStateFlow<DuplicateServicePrompt?>(null)
    val duplicateServicePrompt: StateFlow<DuplicateServicePrompt?> = _duplicateServicePrompt.asStateFlow()

    private val _stagedBackup = MutableStateFlow<StagedBackupPackage?>(null)
    val stagedBackup: StateFlow<StagedBackupPackage?> = _stagedBackup.asStateFlow()

    private val _conflictResolutions = MutableStateFlow<Map<String, ConflictResolution>>(emptyMap())
    val conflictResolutions: StateFlow<Map<String, ConflictResolution>> = _conflictResolutions.asStateFlow()

    private val _savedVaultBackups = MutableStateFlow<List<SavedVaultBackupEntry>>(emptyList())
    val savedVaultBackups: StateFlow<List<SavedVaultBackupEntry>> = _savedVaultBackups.asStateFlow()

    private val _pendingExportBytes = MutableStateFlow<ByteArray?>(null)
    private val _generatedExportLockKey = MutableStateFlow<String?>(null)
    val generatedExportLockKey: StateFlow<String?> = _generatedExportLockKey.asStateFlow()

    private val _pendingCsvContent = MutableStateFlow<String?>(null)

    init {
        refreshSavedVaultBackups()
    }

    fun refreshSavedVaultBackups() {
        viewModelScope.launch {
            runCatching {
                backupVaultStorage.listSavedBackups()
            }.onSuccess { list ->
                _savedVaultBackups.value = list
            }
        }
    }

    private fun <T> unlockedFlow(defaultValue: T, block: () -> Flow<T>): StateFlow<T> {
        return lockState.flatMapLatest { state ->
            if (state is LockState.Unlocked) {
                runCatching { block() }.getOrElse { flowOf(defaultValue) }
            } else {
                flowOf(defaultValue)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = defaultValue
        )
    }

    val services: StateFlow<List<ServiceWithCount>> = unlockedFlow(emptyList()) {
        repository.observeServicesWithCounts()
    }

    val categories: StateFlow<List<CategoryWithCount>> = unlockedFlow(emptyList()) {
        repository.observeCategoriesWithCounts()
    }

    val allAccounts: StateFlow<List<AccountWithDetails>> = unlockedFlow(emptyList()) {
        repository.observeAccountsWithDetails()
    }

    val allServiceAccounts: StateFlow<List<ServiceAccountWithDetails>> = unlockedFlow(emptyList()) {
        repository.observeServiceAccountsWithDetails()
    }

    val dashboardSummary: StateFlow<DashboardSummary> = unlockedFlow(
        DashboardSummary(0, 0, 0, 0, emptyList(), emptyList(), emptyList())
    ) {
        repository.observeDashboardSummary()
    }

    val themePreference: StateFlow<ThemePreference> = unlockedFlow(ThemePreference.SYSTEM) {
        repository.observeThemePreference()
    }

    val colorPalette: StateFlow<AppColorPalette> = unlockedFlow(AppColorPalette.EMERALD) {
        repository.observeColorPalette()
    }

    /**
     * Searches and filters ONLY standalone core Accounts (never mixing in Service Account entries).
     */
    val filteredAccounts: StateFlow<List<AccountWithDetails>> = combine(
        allAccounts,
        _filterState
    ) { accounts, filter ->
        val normalizedQuery = Normalizer.normalizeName(filter.searchQuery)
        accounts.filter { item ->
            val matchesArchive = if (filter.showArchived) true else !item.account.isArchived
            val matchesCategory = filter.selectedCategoryId == null || item.account.categoryId == filter.selectedCategoryId
            val matchesStatus = filter.selectedStatus == null || item.account.status == filter.selectedStatus
            val matchesQuery = if (normalizedQuery.isEmpty()) {
                true
            } else {
                item.account.normalizedDisplayName.contains(normalizedQuery) ||
                    (item.account.normalizedEmail?.contains(normalizedQuery) == true) ||
                    (item.account.normalizedUsername?.contains(normalizedQuery) == true) ||
                    (item.category?.normalizedName?.contains(normalizedQuery) == true) ||
                    (item.account.purpose?.lowercase()?.contains(normalizedQuery) == true)
            }
            matchesArchive && matchesCategory && matchesStatus && matchesQuery
        }.let { filtered ->
            when (filter.sortOrder) {
                AccountSortOrder.RECENTLY_UPDATED ->
                    filtered.sortedByDescending { it.account.updatedAtEpochMillis }
                AccountSortOrder.LABEL_ASC ->
                    filtered.sortedBy { it.account.normalizedDisplayName }
                AccountSortOrder.EMAIL_ASC ->
                    filtered.sortedWith(
                        compareBy<AccountWithDetails> { it.account.normalizedEmail ?: it.account.normalizedUsername ?: "zzzz" }
                            .thenBy { it.account.normalizedDisplayName }
                    )
                AccountSortOrder.CREATED_NEWEST ->
                    filtered.sortedByDescending { it.account.createdAtEpochMillis }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun clearTransientMessage() {
        _transientMessage.value = null
    }

    fun clearUnlockError() {
        _unlockError.value = null
    }

    fun acknowledgeOneTimeRecoveryCode() {
        _oneTimeRecoveryCode.value = null
    }

    // --- Password Manager Verification & Secret Decryption ---
    fun decryptSecretField(encryptedValue: String?): String? =
        repository.decryptSecret(encryptedValue)

    fun isVaultVerificationRequired(): Boolean =
        keyManager.isVaultVerificationRequired()

    fun recordVaultSessionVerified() {
        keyManager.recordVaultSessionVerified()
    }

    fun verifyVaultWithMasterPassword(passwordInput: String): Boolean {
        val chars = passwordInput.toCharArray()
        return try {
            keyManager.verifyVaultWithMasterPassword(chars)
        } finally {
            chars.clearChars()
        }
    }

    fun setPasswordManagerLockEnabled(enabled: Boolean) {
        keyManager.setPasswordManagerLockEnabled(enabled)
        _transientMessage.value = if (enabled) {
            "Password Manager verification enabled (5-minute session window)."
        } else {
            "Password Manager verification disabled."
        }
    }

    fun setUseMasterPasswordForVault(useMasterPassword: Boolean) {
        keyManager.setUseMasterPasswordForVault(useMasterPassword)
        _transientMessage.value = if (useMasterPassword) {
            "Password Manager now verifies with Master Password."
        } else {
            "Password Manager now verifies with Device Lock (Default)."
        }
    }

    fun dismissInitialLockSuggestion(disableAllLocks: Boolean = false) {
        keyManager.dismissInitialLockSuggestion(disableAllLocks = disableAllLocks)
        if (disableAllLocks) {
            _transientMessage.value = "Continuing without locks. You can enable protection anytime in Settings."
        }
    }

    fun getDecryptedRecoveryCode(): String? =
        keyManager.getDecryptedRecoveryCode()

    fun saveOneTimeRecoveryCodeDirectly(fileName: String) {
        val code = keyManager.getDecryptedRecoveryCode()
        if (code.isNullOrBlank()) {
            _transientMessage.value = "No recovery code available to download."
            return
        }
        viewModelScope.launch {
            runCatching {
                val content = buildRecoveryCodeFileText(code)
                val result = backupVaultStorage.saveRecoveryCodeToDownloadsAndVault(fileName, content)
                keyManager.markRecoveryCodeFileDownloadedOnce()
                result
            }.onSuccess { result ->
                _transientMessage.value = "Recovery code saved to ${result.displayLocation} (one-time download completed)."
            }.onFailure { e ->
                _transientMessage.value = "Could not save recovery code file: ${e.message}"
            }
        }
    }

    fun writeOneTimeRecoveryCodeToUri(context: Context, destinationUri: Uri) {
        val code = keyManager.getDecryptedRecoveryCode()
        if (code.isNullOrBlank()) {
            _transientMessage.value = "No recovery code available to download."
            return
        }
        viewModelScope.launch {
            runCatching {
                val content = buildRecoveryCodeFileText(code)
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Unable to open destination file.")
                keyManager.markRecoveryCodeFileDownloadedOnce()
            }.onSuccess {
                _transientMessage.value = "Recovery code file downloaded (one-time download completed)."
            }.onFailure { e ->
                _transientMessage.value = "Could not save recovery code file: ${e.message}"
            }
        }
    }

    private fun buildRecoveryCodeFileText(code: String): String = buildString {
        appendLine("==================================================")
        appendLine("DIGITAL ACCOUNT ORGANIZER — RECOVERY CODE")
        appendLine("==================================================")
        appendLine("Generated / Exported (UTC): ${Instant.now()}")
        appendLine()
        appendLine("Recovery Code:")
        appendLine(code)
        appendLine()
        appendLine("Keep this file in a safe place outside this device.")
        appendLine("This recovery code can reset a forgotten Master Password.")
        appendLine("==================================================")
    }

    // --- Filter mutations (Standalone Accounts) ---
    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun selectCategoryFilter(categoryId: String?) {
        _filterState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun selectStatusFilter(status: AccountStatus?) {
        _filterState.update { it.copy(selectedStatus = status) }
    }

    fun setShowArchived(show: Boolean) {
        _filterState.update { it.copy(showArchived = show) }
    }

    fun setSortOrder(order: AccountSortOrder) {
        _filterState.update { it.copy(sortOrder = order) }
    }

    fun clearAllFilters() {
        _filterState.value = AccountFilterState()
    }

    // --- Unlock & Security Operations ---
    fun unlockWithPassword(passwordInput: String) {
        val chars = passwordInput.toCharArray()
        viewModelScope.launch {
            try {
                keyManager.unlockWithMasterPassword(chars)
                _unlockError.value = null
            } catch (_: Exception) {
                _unlockError.value = "Incorrect master password. Please try again."
            } finally {
                chars.clearChars()
            }
        }
    }

    fun recoverWithKeyAndResetPassword(
        recoveryCodeInput: String,
        newPasswordInput: String,
        onSuccess: () -> Unit
    ) {
        val codeChars = recoveryCodeInput.toCharArray()
        val passChars = newPasswordInput.toCharArray()
        viewModelScope.launch {
            try {
                val result = keyManager.recoverAndResetMasterPassword(codeChars, passChars)
                _oneTimeRecoveryCode.value = String(result.recoveryCode)
                result.recoveryCode.clearChars()
                _unlockError.value = null
                onSuccess()
                _transientMessage.value = "Master password reset. Save your new recovery code."
            } catch (e: Exception) {
                _unlockError.value = e.message ?: "Invalid recovery key or password."
            } finally {
                codeChars.clearChars()
                passChars.clearChars()
            }
        }
    }

    fun recoverWithBiometricAndResetPassword(
        authenticatedDecryptCipher: Cipher,
        newPasswordInput: String,
        onSuccess: () -> Unit
    ) {
        val passChars = newPasswordInput.toCharArray()
        viewModelScope.launch {
            try {
                val result = keyManager.recoverAndResetMasterPasswordWithBiometric(
                    authenticatedDecryptCipher = authenticatedDecryptCipher,
                    newMasterPassword = passChars
                )
                _oneTimeRecoveryCode.value = String(result.recoveryCode)
                result.recoveryCode.clearChars()
                _unlockError.value = null
                onSuccess()
                _transientMessage.value = "Master password reset via Fingerprint. Save your new recovery code."
            } catch (e: Exception) {
                _unlockError.value = e.message ?: "Fingerprint recovery failed."
            } finally {
                passChars.clearChars()
            }
        }
    }

    fun performDestructiveOrganizerReset() {
        viewModelScope.launch {
            keyManager.performDestructiveReset {
                container.databaseFactory.deleteDatabaseFiles()
                container.assetStorage.clearAllAssets()
            }
            _unlockError.value = null
            _transientMessage.value = "Local organizer reset to a clean encrypted workspace."
        }
    }

    fun enableAppLock(
        masterPasswordInput: String,
        timeout: AutoLockTimeout,
        onError: (String) -> Unit
    ) {
        val chars = masterPasswordInput.toCharArray()
        viewModelScope.launch {
            try {
                val result = keyManager.enableAppLock(chars, timeout)
                _oneTimeRecoveryCode.value = String(result.recoveryCode)
                result.recoveryCode.clearChars()
                _transientMessage.value = "Master password lock enabled. Recovery code saved encrypted."
            } catch (e: Exception) {
                onError(e.message ?: "Unable to enable app lock.")
            } finally {
                chars.clearChars()
            }
        }
    }

    fun changeMasterPassword(
        currentPasswordInput: String,
        newPasswordInput: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentChars = currentPasswordInput.toCharArray()
        val newChars = newPasswordInput.toCharArray()
        viewModelScope.launch {
            try {
                keyManager.changeMasterPassword(currentChars, newChars)
                _transientMessage.value = "Master password updated."
                onSuccess()
            } catch (_: Exception) {
                onError("Current master password is incorrect or new password is too short (8+ characters required).")
            } finally {
                currentChars.clearChars()
                newChars.clearChars()
            }
        }
    }

    fun disableAppLock(
        currentPasswordInput: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentChars = currentPasswordInput.toCharArray()
        viewModelScope.launch {
            try {
                keyManager.disableAppLock(currentChars)
                _transientMessage.value = "Master password lock disabled. Database remains encrypted at rest."
                onSuccess()
            } catch (_: Exception) {
                onError("Incorrect master password.")
            } finally {
                currentChars.clearChars()
            }
        }
    }

    fun updateAutoLockTimeout(timeout: AutoLockTimeout) {
        keyManager.updateAutoLockTimeout(timeout)
        _transientMessage.value = "Auto-lock timeout updated: ${timeout.displayLabel}"
    }

    fun lockOrganizerNow() {
        keyManager.lockNow()
    }

    fun prepareBiometricEnrollmentCipher(): Result<Cipher> =
        runCatching { keyManager.prepareBiometricEnrollmentCipher() }

    fun completeBiometricEnrollment(cipher: Cipher) {
        runCatching {
            keyManager.completeBiometricEnrollment(cipher)
            _transientMessage.value = "Fingerprint verification & recovery enabled."
        }.onFailure {
            _transientMessage.value = "Could not complete fingerprint setup: ${it.message}"
        }
    }

    fun prepareBiometricUnlockCipher(): Result<Cipher> =
        runCatching { keyManager.prepareBiometricUnlockCipher() }

    fun unlockWithBiometricCipher(cipher: Cipher) {
        runCatching {
            keyManager.unlockWithBiometricCipher(cipher)
            _unlockError.value = null
        }.onFailure {
            _unlockError.value = "Fingerprint unlock failed. Please use your master password."
        }
    }

    fun disableBiometricUnlock() {
        runCatching {
            keyManager.disableBiometricUnlock()
            _transientMessage.value = "Fingerprint verification disabled."
        }
    }

    // --- Service & Custom Artwork Operations ---
    private var activeLogoFetchJob: kotlinx.coroutines.Job? = null

    fun importServiceLogo(
        uri: Uri,
        onImported: (ServiceAssetRecord) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                repository.importServiceIconFromUri(uri)
            }.onSuccess { record ->
                onImported(record)
            }.onFailure { e ->
                onError(e.message ?: "Selected file is not a valid image.")
            }
        }
    }

    fun fetchServiceLogoFromUrl(
        rawWebsiteUrl: String,
        onFetched: (com.example.core.database.FetchedServiceLogoResult) -> Unit,
        onError: (String) -> Unit
    ) {
        activeLogoFetchJob?.cancel()
        activeLogoFetchJob = viewModelScope.launch {
            runCatching {
                repository.fetchServiceIconFromWebsiteUrl(rawWebsiteUrl)
            }.onSuccess { result ->
                onFetched(result)
            }.onFailure { e ->
                if (e !is kotlinx.coroutines.CancellationException) {
                    onError(e.message ?: "Unable to fetch logo from website URL.")
                }
            }
        }
    }

    fun cancelServiceLogoFetch() {
        activeLogoFetchJob?.cancel()
        activeLogoFetchJob = null
    }

    fun saveService(
        existingId: String? = null,
        name: String,
        websiteUrl: String?,
        description: String?,
        iconAssetId: String?,
        allowDuplicateVariation: Boolean = false,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (
                val outcome = repository.saveService(
                    existingId = existingId,
                    name = name,
                    websiteUrl = websiteUrl,
                    description = description,
                    iconAssetId = iconAssetId,
                    allowDuplicateVariation = allowDuplicateVariation
                )
            ) {
                is SaveServiceOutcome.Saved -> {
                    _duplicateServicePrompt.value = null
                    _transientMessage.value = "Service '${outcome.service.name}' saved."
                    onSaved()
                }
                is SaveServiceOutcome.DuplicateNameDetected -> {
                    _duplicateServicePrompt.value = DuplicateServicePrompt(
                        existingId = existingId,
                        pendingName = name,
                        pendingWebsiteUrl = websiteUrl,
                        pendingDescription = description,
                        pendingIconAssetId = iconAssetId,
                        conflictingServiceName = outcome.existingServiceName
                    )
                }
                is SaveServiceOutcome.ValidationError -> {
                    onError(outcome.message)
                }
            }
        }
    }

    fun dismissDuplicateServicePrompt() {
        _duplicateServicePrompt.value = null
    }

    fun setServiceArchived(serviceId: String, archived: Boolean) {
        viewModelScope.launch {
            repository.setServiceArchived(serviceId, archived)
            _transientMessage.value = if (archived) "Service archived." else "Service restored."
        }
    }

    fun deleteOrResolveService(
        serviceId: String,
        resolution: ServiceDeleteResolution,
        reassignTargetServiceId: String? = null,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                repository.deleteOrResolveService(
                    serviceId = serviceId,
                    resolution = resolution,
                    reassignTargetServiceId = reassignTargetServiceId
                )
            }.onSuccess {
                _transientMessage.value = when (resolution) {
                    ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD -> "Service archived safely."
                    ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE -> "Service entries moved and service deleted."
                    ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS -> "Service and its service entries deleted."
                }
                onCompleted()
            }.onFailure { e ->
                onError(e.message ?: "Unable to delete service.")
            }
        }
    }

    // --- Category Operations ---
    fun saveCategory(
        existingId: String? = null,
        name: String,
        description: String?,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (val outcome = repository.saveCategory(existingId, name, description)) {
                is SaveCategoryOutcome.Saved -> {
                    _transientMessage.value = "Category '${outcome.category.name}' saved."
                    onSaved()
                }
                is SaveCategoryOutcome.DuplicateNameDetected -> {
                    onError("A category named '${outcome.existingCategoryName}' already exists.")
                }
                is SaveCategoryOutcome.ValidationError -> {
                    onError(outcome.message)
                }
            }
        }
    }

    fun deleteCategorySafely(
        categoryId: String,
        reassignToCategoryId: String? = null,
        onCompleted: () -> Unit
    ) {
        viewModelScope.launch {
            repository.deleteCategorySafely(categoryId, reassignToCategoryId)
            _transientMessage.value = "Category removed. Account records were preserved."
            onCompleted()
        }
    }

    // --- Standalone Account Operations ---
    fun saveAccount(
        existingId: String? = null,
        displayName: String,
        email: String?,
        username: String?,
        password: String?,
        clearSavedPassword: Boolean = false,
        categoryId: String?,
        purpose: String?,
        recoveryEmailReference: String?,
        recoveryPhoneReference: String?,
        notes: String?,
        status: AccountStatus,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (
                val outcome = repository.saveAccount(
                    existingId = existingId,
                    displayName = displayName,
                    email = email,
                    username = username,
                    password = password,
                    clearSavedPassword = clearSavedPassword,
                    categoryId = categoryId,
                    purpose = purpose,
                    recoveryEmailReference = recoveryEmailReference,
                    recoveryPhoneReference = recoveryPhoneReference,
                    notes = notes,
                    status = status
                )
            ) {
                is SaveAccountOutcome.Saved -> {
                    _transientMessage.value = "Account '${outcome.account.displayName}' saved."
                    onSaved()
                }
                is SaveAccountOutcome.ValidationError -> {
                    onError(outcome.message)
                }
            }
        }
    }

    fun setAccountArchived(accountId: String, archived: Boolean) {
        viewModelScope.launch {
            repository.setAccountArchived(accountId, archived)
            _transientMessage.value = if (archived) "Account archived." else "Account restored."
        }
    }

    fun deleteAccountPermanently(accountId: String) {
        viewModelScope.launch {
            repository.deleteAccountPermanently(accountId)
            _transientMessage.value = "Account permanently deleted."
        }
    }

    // --- Service Account Entry Operations ---
    fun saveServiceAccount(
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
        status: AccountStatus,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (
                val outcome = repository.saveServiceAccount(
                    existingId = existingId,
                    serviceId = serviceId,
                    linkedAccountId = linkedAccountId,
                    displayName = displayName,
                    email = email,
                    username = username,
                    password = password,
                    clearSavedPassword = clearSavedPassword,
                    categoryId = categoryId,
                    purpose = purpose,
                    recoveryEmailReference = recoveryEmailReference,
                    recoveryPhoneReference = recoveryPhoneReference,
                    websiteUrl = websiteUrl,
                    notes = notes,
                    status = status
                )
            ) {
                is SaveServiceAccountOutcome.Saved -> {
                    _transientMessage.value = "Service entry '${outcome.serviceAccount.displayName}' saved."
                    onSaved()
                }
                is SaveServiceAccountOutcome.ValidationError -> {
                    onError(outcome.message)
                }
            }
        }
    }

    fun setServiceAccountArchived(serviceAccountId: String, archived: Boolean) {
        viewModelScope.launch {
            repository.setServiceAccountArchived(serviceAccountId, archived)
            _transientMessage.value = if (archived) "Service entry archived." else "Service entry restored."
        }
    }

    fun deleteServiceAccountPermanently(serviceAccountId: String) {
        viewModelScope.launch {
            repository.deleteServiceAccountPermanently(serviceAccountId)
            _transientMessage.value = "Service entry permanently deleted."
        }
    }

    // --- Theme & Color Palette Preferences ---
    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            repository.setThemePreference(theme)
        }
    }

    fun setColorPalette(palette: AppColorPalette) {
        viewModelScope.launch {
            repository.setColorPalette(palette)
        }
    }

    // --- Backup & Restore Operations (App-Generated Export Lock Key + Direct Save to Downloads & Vault) ---
    fun prepareAutoLockedEncryptedBackup(
        mode: ExportMode,
        selectedAccountIds: Set<String> = emptySet(),
        selectedServiceIds: Set<String> = emptySet(),
        selectedCategoryIds: Set<String> = emptySet(),
        onLockKeyGenerated: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val generatedKey = backupEngine.generateExportLockKey()
        val keyChars = generatedKey.toCharArray()
        viewModelScope.launch {
            try {
                val bytes = backupEngine.createEncryptedBackupBytes(
                    passphrase = keyChars,
                    selection = ExportSelection(
                        mode = mode,
                        selectedAccountIds = selectedAccountIds,
                        selectedServiceIds = selectedServiceIds,
                        selectedCategoryIds = selectedCategoryIds
                    )
                )
                _pendingExportBytes.value = bytes
                _generatedExportLockKey.value = generatedKey
                onLockKeyGenerated(generatedKey)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create encrypted backup.")
            } finally {
                keyChars.clearChars()
            }
        }
    }

    fun clearGeneratedExportLockKey() {
        _generatedExportLockKey.value = null
    }

    fun savePendingEncryptedBackupDirectly(
        fileName: String,
        onError: (String) -> Unit
    ) {
        val bytes = _pendingExportBytes.value
        if (bytes == null) {
            onError("No pending encrypted backup to save. Please generate a new Export Lock Key.")
            return
        }
        viewModelScope.launch {
            runCatching {
                backupVaultStorage.saveBackupBytesToDownloadsAndVault(fileName, bytes)
            }.onSuccess { result ->
                _pendingExportBytes.value = null
                _generatedExportLockKey.value = null
                refreshSavedVaultBackups()
                _transientMessage.value =
                    "Locked .dao-backup saved to ${result.displayLocation} (${(bytes.size / 1024).coerceAtLeast(1)} KB)."
            }.onFailure { e ->
                onError(e.message ?: "Could not save .dao-backup file.")
            }
        }
    }

    fun writePendingEncryptedBackupToUri(context: Context, destinationUri: Uri) {
        val bytes = _pendingExportBytes.value ?: return
        viewModelScope.launch {
            runCatching {
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: error("Unable to open destination file.")
                // Also save a copy in the built-in Backup Vault for convenient access
                val fallbackName = "digital_accounts_${System.currentTimeMillis()}.dao-backup"
                backupVaultStorage.saveBackupBytesToDownloadsAndVault(fallbackName, bytes)
            }.onSuccess {
                refreshSavedVaultBackups()
                _transientMessage.value = "Encrypted .dao-backup exported (${(bytes.size / 1024).coerceAtLeast(1)} KB). Keep your Export Lock Key safe!"
            }.onFailure { e ->
                _transientMessage.value = "Export failed: ${e.message}"
            }
            _pendingExportBytes.value = null
        }
    }

    fun savePlaintextCsvDirectly(
        fileName: String,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val csv = backupEngine.exportPlaintextCsv()
                backupVaultStorage.saveCsvToDownloadsAndVault(fileName, csv)
            }.onSuccess { result ->
                refreshSavedVaultBackups()
                _transientMessage.value = "Plaintext CSV saved to ${result.displayLocation}."
            }.onFailure { e ->
                onError(e.message ?: "Failed to export CSV.")
            }
        }
    }

    fun preparePlaintextCsvExport(
        onReadyForSafSave: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                backupEngine.exportPlaintextCsv()
            }.onSuccess { csv ->
                _pendingCsvContent.value = csv
                onReadyForSafSave()
            }.onFailure { e ->
                onError(e.message ?: "Failed to generate CSV export.")
            }
        }
    }

    fun writePendingCsvToUri(context: Context, destinationUri: Uri) {
        val csv = _pendingCsvContent.value ?: return
        viewModelScope.launch {
            runCatching {
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Unable to open destination file.")
            }.onSuccess {
                _transientMessage.value = "Plaintext CSV exported."
            }.onFailure { e ->
                _transientMessage.value = "CSV export failed: ${e.message}"
            }
            _pendingCsvContent.value = null
        }
    }

    fun stageBackupFromVaultFile(
        vaultFilePath: String,
        passphraseInput: String,
        onError: (String) -> Unit
    ) {
        val passChars = passphraseInput.toCharArray()
        viewModelScope.launch {
            try {
                val fileBytes = backupVaultStorage.readVaultFileBytes(vaultFilePath)
                val staged = backupEngine.stageAndPreviewEncryptedBackup(fileBytes, passChars)
                _stagedBackup.value = staged
                _conflictResolutions.value = staged.conflicts.associate {
                    it.incomingId to ConflictResolution.SKIP
                }
            } catch (e: Exception) {
                onError(e.message ?: "Incorrect Export Lock Key or damaged backup file.")
            } finally {
                passChars.clearChars()
            }
        }
    }

    fun stageBackupFromUri(
        context: Context,
        sourceUri: Uri,
        passphraseInput: String,
        onError: (String) -> Unit
    ) {
        val passChars = passphraseInput.toCharArray()
        viewModelScope.launch {
            try {
                val fileBytes = context.contentResolver.openInputStream(sourceUri)?.use {
                    it.readBytes()
                } ?: throw IllegalArgumentException("Unable to read selected backup file.")

                val staged = backupEngine.stageAndPreviewEncryptedBackup(fileBytes, passChars)
                _stagedBackup.value = staged
                _conflictResolutions.value = staged.conflicts.associate {
                    it.incomingId to ConflictResolution.SKIP
                }
            } catch (e: Exception) {
                onError(e.message ?: "Incorrect Export Lock Key or damaged backup file.")
            } finally {
                passChars.clearChars()
            }
        }
    }

    fun deleteVaultBackupFile(vaultFilePath: String) {
        viewModelScope.launch {
            val deleted = backupVaultStorage.deleteVaultFile(vaultFilePath)
            refreshSavedVaultBackups()
            if (deleted) {
                _transientMessage.value = "Backup file removed from Built-In Backup Vault."
            }
        }
    }

    fun updateConflictResolution(incomingId: String, resolution: ConflictResolution) {
        _conflictResolutions.update { current ->
            current.toMutableMap().apply { put(incomingId, resolution) }
        }
    }

    fun cancelStagedBackup() {
        _stagedBackup.value = null
        _conflictResolutions.value = emptyMap()
    }

    fun commitStagedBackup(onError: (String) -> Unit) {
        val staged = _stagedBackup.value ?: return
        val resolutions = _conflictResolutions.value
        viewModelScope.launch {
            runCatching {
                backupEngine.commitStagedBackup(staged, resolutions)
            }.onSuccess { summary ->
                _stagedBackup.value = null
                _conflictResolutions.value = emptyMap()
                _transientMessage.value =
                    "Import complete: +${summary.importedAccounts} accounts, +${summary.importedServiceAccounts} service entries, +${summary.importedServices} services (${summary.replacedRecords} replaced, ${summary.skippedRecords} skipped)."
            }.onFailure { e ->
                onError(e.message ?: "Failed to commit backup import.")
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OrganizerViewModel(container) as T
                }
            }
    }
}
