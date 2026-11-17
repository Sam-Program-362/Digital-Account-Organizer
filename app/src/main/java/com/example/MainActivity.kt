package com.example

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.DigitalAccountOrganizerApp
import com.example.app.OrganizerViewModel
import com.example.core.security.LockState
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.ServiceWithCount
import com.example.feature.accounts.AccountEditorDialog
import com.example.feature.accounts.AccountsScreen
import com.example.feature.backup.BackupScreen
import com.example.feature.categories.CategoriesScreen
import com.example.feature.dashboard.DashboardScreen
import com.example.feature.security.OneTimeRecoveryKeyDialog
import com.example.feature.security.UnlockScreen
import com.example.feature.security.VaultVerificationDialog
import com.example.feature.services.ServiceEditorDialog
import com.example.feature.services.ServicesScreen
import com.example.feature.settings.SettingsScreen
import com.example.ui.theme.DigitalAccountOrganizerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher

private enum class OrganizerDestination(
    val label: String,
    val icon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Home", Icons.Default.Home, "nav_home"),
    ACCOUNTS("Accounts", Icons.Default.AccountCircle, "nav_accounts"),
    SERVICES("Services", Icons.Default.Apps, "nav_services"),
    CATEGORIES("Categories", Icons.Default.Folder, "nav_categories"),
    BACKUP("Backup", Icons.Default.CloudQueue, "nav_backup"),
    SETTINGS("Settings", Icons.Default.Settings, "nav_settings")
}

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DigitalAccountOrganizerApp

        setContent {
            val viewModel: OrganizerViewModel = viewModel(
                factory = OrganizerViewModel.factory(app.container)
            )

            val lockState by viewModel.lockState.collectAsStateWithLifecycle()
            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
            val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()

            LaunchedEffect(lockState) {
                val shouldSecureWindow = when (val current = lockState) {
                    is LockState.Locked -> true
                    is LockState.Unlocked -> current.isAppLockEnabled || current.isPasswordManagerLockEnabled
                    else -> false
                }
                if (shouldSecureWindow) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            DigitalAccountOrganizerTheme(
                themePreference = themePreference,
                colorPalette = colorPalette
            ) {
                OrganizerRootContent(
                    activity = this,
                    viewModel = viewModel,
                    lockState = lockState
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizerRootContent(
    activity: FragmentActivity,
    viewModel: OrganizerViewModel,
    lockState: LockState
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val services by viewModel.services.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val filteredAccounts by viewModel.filteredAccounts.collectAsStateWithLifecycle()
    val allServiceAccounts by viewModel.allServiceAccounts.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()

    val transientMessage by viewModel.transientMessage.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    val oneTimeRecoveryCode by viewModel.oneTimeRecoveryCode.collectAsStateWithLifecycle()
    val duplicateServicePrompt by viewModel.duplicateServicePrompt.collectAsStateWithLifecycle()
    val stagedBackup by viewModel.stagedBackup.collectAsStateWithLifecycle()
    val conflictResolutions by viewModel.conflictResolutions.collectAsStateWithLifecycle()
    val generatedExportLockKey by viewModel.generatedExportLockKey.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable { mutableStateOf(OrganizerDestination.DASHBOARD) }
    var selectedServiceIdForDetail by rememberSaveable { mutableStateOf<String?>(null) }

    var showAccountEditor by rememberSaveable { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountWithDetails?>(null) }
    var initialAccountPassword by remember { mutableStateOf("") }

    var showServiceEditor by rememberSaveable { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<ServiceWithCount?>(null) }

    var showVaultVerificationDialog by remember { mutableStateOf(false) }
    var pendingVaultVerifiedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(transientMessage) {
        val msg = transientMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearTransientMessage()
        }
    }

    val isBiometricHardwareAvailable = remember(activity) {
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    val saveRecoveryCodeFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.writeOneTimeRecoveryCodeToUri(activity, uri)
        }
    }

    val triggerOneTimeRecoveryCodeDownload: () -> Unit = {
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        saveRecoveryCodeFileLauncher.launch("dao_recovery_code_$dateStamp.txt")
    }

    val requestVaultVerifiedAction: (onVerified: () -> Unit) -> Unit = { onVerified ->
        if (!viewModel.isVaultVerificationRequired()) {
            onVerified()
        } else {
            val unlocked = lockState as? LockState.Unlocked
            val useMasterPassword =
                unlocked?.isAppLockEnabled == true && unlocked.useMasterPasswordForVault
            if (useMasterPassword) {
                pendingVaultVerifiedAction = onVerified
                showVaultVerificationDialog = true
            } else {
                val bm = BiometricManager.from(activity)
                val canDeviceAuth = bm.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == BiometricManager.BIOMETRIC_SUCCESS

                if (canDeviceAuth) {
                    launchDeviceOrBiometricPrompt(
                        activity = activity,
                        title = "Verify Password Manager",
                        subtitle = "Verify your identity to view, copy, or edit saved passwords and notes",
                        onAuthenticated = {
                            viewModel.recordVaultSessionVerified()
                            onVerified()
                        }
                    )
                } else if (unlocked?.isAppLockEnabled == true) {
                    pendingVaultVerifiedAction = onVerified
                    showVaultVerificationDialog = true
                } else {
                    // Device has no screen lock configured and no Master Password is enabled
                    viewModel.recordVaultSessionVerified()
                    onVerified()
                }
            }
        }
    }

    when (lockState) {
        is LockState.Uninitialized -> {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }

        is LockState.Locked -> {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    UnlockScreen(
                        lockedState = lockState,
                        errorMessage = unlockError,
                        onUnlockWithPassword = { viewModel.unlockWithPassword(it) },
                        onRequestBiometricUnlock = {
                            viewModel.prepareBiometricUnlockCipher()
                                .onSuccess { cipher ->
                                    launchCryptoBiometricPrompt(
                                        activity = activity,
                                        title = "Unlock Digital Account Organizer",
                                        subtitle = "Verify your fingerprint to unlock your encrypted workspace",
                                        cipher = cipher,
                                        onAuthenticated = { authCipher ->
                                            viewModel.unlockWithBiometricCipher(authCipher)
                                        }
                                    )
                                }
                        },
                        onRecoverWithKey = { code, newPass, onSuccess ->
                            viewModel.recoverWithKeyAndResetPassword(code, newPass, onSuccess)
                        },
                        onRecoverWithBiometric = { newPass, onSuccess ->
                            viewModel.prepareBiometricUnlockCipher()
                                .onSuccess { cipher ->
                                    launchCryptoBiometricPrompt(
                                        activity = activity,
                                        title = "Fingerprint Master Password Recovery",
                                        subtitle = "Verify your fingerprint to reset your forgotten Master Password",
                                        cipher = cipher,
                                        onAuthenticated = { authCipher ->
                                            viewModel.recoverWithBiometricAndResetPassword(
                                                authenticatedDecryptCipher = authCipher,
                                                newPasswordInput = newPass,
                                                onSuccess = onSuccess
                                            )
                                        }
                                    )
                                }
                        },
                        onDestructiveReset = { viewModel.performDestructiveOrganizerReset() },
                        onClearError = { viewModel.clearUnlockError() }
                    )
                }
            }
        }

        is LockState.Unlocked -> {
            if (currentDestination != OrganizerDestination.DASHBOARD) {
                BackHandler {
                    currentDestination = OrganizerDestination.DASHBOARD
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Digital Account Organizer") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            actions = {
                                if (lockState.isAppLockEnabled) {
                                    IconButton(
                                        onClick = { viewModel.lockOrganizerNow() },
                                        modifier = Modifier.testTag("top_bar_lock_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock Organizer Now"
                                        )
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (!useNavRail) {
                            NavigationBar {
                                OrganizerDestination.entries.forEach { dest ->
                                    NavigationBarItem(
                                        selected = currentDestination == dest,
                                        onClick = { currentDestination = dest },
                                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                                        label = { Text(dest.label, maxLines = 1) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavRail) {
                            NavigationRail {
                                OrganizerDestination.entries.forEach { dest ->
                                    NavigationRailItem(
                                        selected = currentDestination == dest,
                                        onClick = { currentDestination = dest },
                                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                                        label = { Text(dest.label) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentDestination) {
                                OrganizerDestination.DASHBOARD -> {
                                    DashboardScreen(
                                        summary = dashboardSummary,
                                        isAppLockEnabled = lockState.isAppLockEnabled,
                                        showInitialLockSuggestion = !lockState.hasDismissedInitialLockSuggestion && !lockState.isAppLockEnabled,
                                        onKeepDeviceVaultLock = {
                                            viewModel.dismissInitialLockSuggestion(disableAllLocks = false)
                                        },
                                        onOpenSecuritySettings = {
                                            viewModel.dismissInitialLockSuggestion(disableAllLocks = false)
                                            currentDestination = OrganizerDestination.SETTINGS
                                        },
                                        onSkipAllLocks = {
                                            viewModel.dismissInitialLockSuggestion(disableAllLocks = true)
                                        },
                                        onCreateServiceClick = {
                                            editingService = null
                                            showServiceEditor = true
                                        },
                                        onAddAccountClick = {
                                            editingAccount = null
                                            initialAccountPassword = ""
                                            showAccountEditor = true
                                        },
                                        onOpenServiceDetail = { serviceId ->
                                            selectedServiceIdForDetail = serviceId
                                            currentDestination = OrganizerDestination.SERVICES
                                        },
                                        onOpenAccountsForCategory = { categoryId ->
                                            viewModel.clearAllFilters()
                                            viewModel.selectCategoryFilter(categoryId)
                                            currentDestination = OrganizerDestination.ACCOUNTS
                                        },
                                        onOpenAllAccounts = {
                                            currentDestination = OrganizerDestination.ACCOUNTS
                                        },
                                        onOpenBackup = {
                                            currentDestination = OrganizerDestination.BACKUP
                                        },
                                        onLockNowClick = { viewModel.lockOrganizerNow() }
                                    )
                                }

                                OrganizerDestination.ACCOUNTS -> {
                                    AccountsScreen(
                                        accounts = filteredAccounts,
                                        categories = categories,
                                        filterState = filterState,
                                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                        onSelectCategoryFilter = { viewModel.selectCategoryFilter(it) },
                                        onSelectStatusFilter = { viewModel.selectStatusFilter(it) },
                                        onToggleShowArchived = { viewModel.setShowArchived(it) },
                                        onSelectSortOrder = { viewModel.setSortOrder(it) },
                                        onClearFilters = { viewModel.clearAllFilters() },
                                        onAddAccountClick = {
                                            editingAccount = null
                                            initialAccountPassword = ""
                                            showAccountEditor = true
                                        },
                                        onEditAccountClick = { item ->
                                            editingAccount = item
                                            initialAccountPassword =
                                                viewModel.decryptSecretField(item.account.encryptedPassword).orEmpty()
                                            showAccountEditor = true
                                        },
                                        onToggleArchiveAccount = { id, archived ->
                                            viewModel.setAccountArchived(id, archived)
                                        },
                                        onDeleteAccountPermanently = { id ->
                                            viewModel.deleteAccountPermanently(id)
                                        },
                                        onDecryptSecret = { viewModel.decryptSecretField(it) },
                                        onRequestVerifiedAction = requestVaultVerifiedAction
                                    )
                                }

                                OrganizerDestination.SERVICES -> {
                                    ServicesScreen(
                                        services = services,
                                        serviceAccounts = allServiceAccounts,
                                        standaloneAccounts = filteredAccounts,
                                        categories = categories,
                                        selectedServiceIdForDetail = selectedServiceIdForDetail,
                                        duplicatePrompt = duplicateServicePrompt,
                                        onSelectServiceForDetail = { selectedServiceIdForDetail = it },
                                        onCreateServiceClick = {
                                            editingService = null
                                            showServiceEditor = true
                                        },
                                        onEditServiceClick = { svc ->
                                            editingService = svc
                                            showServiceEditor = true
                                        },
                                        onToggleArchiveService = { id, archived ->
                                            viewModel.setServiceArchived(id, archived)
                                        },
                                        onResolveDeleteService = { serviceId, resolution, reassignId, onCompleted, onError ->
                                            viewModel.deleteOrResolveService(
                                                serviceId = serviceId,
                                                resolution = resolution,
                                                reassignTargetServiceId = reassignId,
                                                onCompleted = onCompleted,
                                                onError = onError
                                            )
                                        },
                                        onConfirmDuplicateServiceVariation = {
                                            val dup = duplicateServicePrompt
                                            if (dup != null) {
                                                viewModel.saveService(
                                                    existingId = dup.existingId,
                                                    name = dup.pendingName,
                                                    websiteUrl = dup.pendingWebsiteUrl,
                                                    description = dup.pendingDescription,
                                                    iconAssetId = dup.pendingIconAssetId,
                                                    allowDuplicateVariation = true,
                                                    onSaved = { showServiceEditor = false },
                                                    onError = {}
                                                )
                                            }
                                        },
                                        onDismissDuplicatePrompt = {
                                            viewModel.dismissDuplicateServicePrompt()
                                        },
                                        onDecryptSecret = { viewModel.decryptSecretField(it) },
                                        onRequestVerifiedAction = requestVaultVerifiedAction,
                                        onSaveServiceAccount = { existingId, serviceId, linkedAccId, displayName, email, username, password, categoryId, purpose, recEmail, recPhone, webUrl, notes, status, onSaved, onError ->
                                            viewModel.saveServiceAccount(
                                                existingId = existingId,
                                                serviceId = serviceId,
                                                linkedAccountId = linkedAccId,
                                                displayName = displayName,
                                                email = email,
                                                username = username,
                                                password = password,
                                                categoryId = categoryId,
                                                purpose = purpose,
                                                recoveryEmailReference = recEmail,
                                                recoveryPhoneReference = recPhone,
                                                websiteUrl = webUrl,
                                                notes = notes,
                                                status = status,
                                                onSaved = onSaved,
                                                onError = onError
                                            )
                                        },
                                        onToggleArchiveServiceAccount = { id, archived ->
                                            viewModel.setServiceAccountArchived(id, archived)
                                        },
                                        onDeleteServiceAccountPermanently = { id ->
                                            viewModel.deleteServiceAccountPermanently(id)
                                        }
                                    )
                                }

                                OrganizerDestination.CATEGORIES -> {
                                    CategoriesScreen(
                                        categories = categories,
                                        onSaveCategory = { existingId, name, description, onSaved, onError ->
                                            viewModel.saveCategory(
                                                existingId = existingId,
                                                name = name,
                                                description = description,
                                                onSaved = onSaved,
                                                onError = onError
                                            )
                                        },
                                        onDeleteCategorySafely = { categoryId, reassignToId, onCompleted ->
                                            viewModel.deleteCategorySafely(
                                                categoryId = categoryId,
                                                reassignToCategoryId = reassignToId,
                                                onCompleted = onCompleted
                                            )
                                        },
                                        onBrowseCategoryAccounts = { categoryId ->
                                            viewModel.clearAllFilters()
                                            viewModel.selectCategoryFilter(categoryId)
                                            currentDestination = OrganizerDestination.ACCOUNTS
                                        }
                                    )
                                }

                                OrganizerDestination.BACKUP -> {
                                    BackupScreen(
                                        accounts = filteredAccounts,
                                        services = services,
                                        categories = categories,
                                        stagedBackup = stagedBackup,
                                        conflictResolutions = conflictResolutions,
                                        generatedExportLockKey = generatedExportLockKey,
                                        onPrepareAutoLockedBackup = { mode, accIds, svcIds, catIds, onKeyReady, onError ->
                                            viewModel.prepareAutoLockedEncryptedBackup(
                                                mode = mode,
                                                selectedAccountIds = accIds,
                                                selectedServiceIds = svcIds,
                                                selectedCategoryIds = catIds,
                                                onLockKeyGenerated = onKeyReady,
                                                onError = onError
                                            )
                                        },
                                        onClearGeneratedExportLockKey = {
                                            viewModel.clearGeneratedExportLockKey()
                                        },
                                        onWritePendingBackupToUri = { uri ->
                                            viewModel.writePendingEncryptedBackupToUri(activity, uri)
                                        },
                                        onPreparePlaintextCsv = { onReady, onError ->
                                            viewModel.preparePlaintextCsvExport(onReady, onError)
                                        },
                                        onWritePendingCsvToUri = { uri ->
                                            viewModel.writePendingCsvToUri(activity, uri)
                                        },
                                        onStageBackupFromUri = { uri, lockKey, onError ->
                                            viewModel.stageBackupFromUri(activity, uri, lockKey, onError)
                                        },
                                        onUpdateConflictResolution = { incomingId, resolution ->
                                            viewModel.updateConflictResolution(incomingId, resolution)
                                        },
                                        onCancelStagedBackup = { viewModel.cancelStagedBackup() },
                                        onCommitStagedBackup = { onError ->
                                            viewModel.commitStagedBackup(onError)
                                        }
                                    )
                                }

                                OrganizerDestination.SETTINGS -> {
                                    SettingsScreen(
                                        unlockedState = lockState,
                                        themePreference = themePreference,
                                        colorPalette = colorPalette,
                                        isBiometricHardwareAvailable = isBiometricHardwareAvailable,
                                        onSelectThemePreference = { viewModel.setThemePreference(it) },
                                        onSelectColorPalette = { viewModel.setColorPalette(it) },
                                        onSetPasswordManagerLockEnabled = {
                                            viewModel.setPasswordManagerLockEnabled(it)
                                        },
                                        onSetUseMasterPasswordForVault = {
                                            viewModel.setUseMasterPasswordForVault(it)
                                        },
                                        onEnableAppLock = { password, timeout, onError ->
                                            viewModel.enableAppLock(password, timeout, onError)
                                        },
                                        onChangeMasterPassword = { currentPass, newPass, onSuccess, onError ->
                                            viewModel.changeMasterPassword(
                                                currentPass,
                                                newPass,
                                                onSuccess,
                                                onError
                                            )
                                        },
                                        onDisableAppLock = { currentPass, onSuccess, onError ->
                                            viewModel.disableAppLock(currentPass, onSuccess, onError)
                                        },
                                        onUpdateAutoLockTimeout = {
                                            viewModel.updateAutoLockTimeout(it)
                                        },
                                        onToggleBiometricUnlock = { enable ->
                                            if (enable) {
                                                viewModel.prepareBiometricEnrollmentCipher()
                                                    .onSuccess { cipher ->
                                                        launchCryptoBiometricPrompt(
                                                            activity = activity,
                                                            title = "Enable Fingerprint Verification",
                                                            subtitle = "Verify your fingerprint to enable biometric unlock and recovery",
                                                            cipher = cipher,
                                                            onAuthenticated = { authCipher ->
                                                                viewModel.completeBiometricEnrollment(authCipher)
                                                            }
                                                        )
                                                    }
                                            } else {
                                                viewModel.disableBiometricUnlock()
                                            }
                                        },
                                        onRequestVerifiedAction = requestVaultVerifiedAction,
                                        onGetDecryptedRecoveryCode = {
                                            viewModel.getDecryptedRecoveryCode()
                                        },
                                        onDownloadRecoveryCodeOnce = triggerOneTimeRecoveryCodeDownload,
                                        onLockNow = { viewModel.lockOrganizerNow() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showAccountEditor) {
                AccountEditorDialog(
                    initialAccount = editingAccount,
                    initialDecryptedPassword = initialAccountPassword,
                    categories = categories,
                    onDismiss = {
                        showAccountEditor = false
                        editingAccount = null
                    },
                    onSave = { existingId, displayName, email, username, password, categoryId, purpose, recEmail, recPhone, notes, status, onError ->
                        viewModel.saveAccount(
                            existingId = existingId,
                            displayName = displayName,
                            email = email,
                            username = username,
                            password = password,
                            categoryId = categoryId,
                            purpose = purpose,
                            recoveryEmailReference = recEmail,
                            recoveryPhoneReference = recPhone,
                            notes = notes,
                            status = status,
                            onSaved = {
                                showAccountEditor = false
                                editingAccount = null
                            },
                            onError = onError
                        )
                    }
                )
            }

            if (showServiceEditor) {
                ServiceEditorDialog(
                    initialService = editingService,
                    onImportCustomLogo = { uri, onImported, onError ->
                        viewModel.importServiceLogo(uri, onImported, onError)
                    },
                    onFetchWebsiteLogo = { url, onFetched, onError ->
                        viewModel.fetchServiceLogoFromUrl(url, onFetched, onError)
                    },
                    onCancelWebsiteLogoFetch = {
                        viewModel.cancelServiceLogoFetch()
                    },
                    onDismiss = {
                        showServiceEditor = false
                        editingService = null
                    },
                    onSave = { existingId, name, websiteUrl, description, iconAssetId, onError ->
                        viewModel.saveService(
                            existingId = existingId,
                            name = name,
                            websiteUrl = websiteUrl,
                            description = description,
                            iconAssetId = iconAssetId,
                            onSaved = {
                                showServiceEditor = false
                                editingService = null
                            },
                            onError = onError
                        )
                    }
                )
            }

            if (showVaultVerificationDialog) {
                VaultVerificationDialog(
                    isBiometricEnabled = lockState.isBiometricEnabled && isBiometricHardwareAvailable,
                    onVerifyWithMasterPassword = { pass ->
                        viewModel.verifyVaultWithMasterPassword(pass)
                    },
                    onRequestBiometricOrDeviceVerify = {
                        launchDeviceOrBiometricPrompt(
                            activity = activity,
                            title = "Verify Password Manager",
                            subtitle = "Verify your fingerprint or device lock to reveal passwords and notes",
                            onAuthenticated = {
                                viewModel.recordVaultSessionVerified()
                                showVaultVerificationDialog = false
                                val action = pendingVaultVerifiedAction
                                pendingVaultVerifiedAction = null
                                action?.invoke()
                            }
                        )
                    },
                    onVerified = {
                        showVaultVerificationDialog = false
                        val action = pendingVaultVerifiedAction
                        pendingVaultVerifiedAction = null
                        action?.invoke()
                    },
                    onDismiss = {
                        showVaultVerificationDialog = false
                        pendingVaultVerifiedAction = null
                    }
                )
            }

            val recoveryCodeToDisplay = oneTimeRecoveryCode
            if (!recoveryCodeToDisplay.isNullOrBlank()) {
                OneTimeRecoveryKeyDialog(
                    recoveryCode = recoveryCodeToDisplay,
                    canDownloadFileOnce = !lockState.recoveryCodesDownloadedOnce,
                    onDownloadFileOnce = triggerOneTimeRecoveryCodeDownload,
                    onConfirmedSaved = { viewModel.acknowledgeOneTimeRecoveryCode() }
                )
            }
        }
    }
}

private fun launchCryptoBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cipher: Cipher,
    onAuthenticated: (Cipher) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                result.cryptoObject?.cipher?.let(onAuthenticated)
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Use Password")
        .build()

    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}

private fun launchDeviceOrBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onAuthenticated: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthenticated()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    prompt.authenticate(info)
}
