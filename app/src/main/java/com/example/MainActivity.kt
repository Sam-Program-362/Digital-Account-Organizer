package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.DigitalAccountOrganizerApp
import com.example.app.OrganizerViewModel
import com.example.core.security.LockState
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
            val restrictScreenshotsEntireApp by viewModel.restrictScreenshotsEntireApp.collectAsStateWithLifecycle()

            var isCredentialScreenOrModalActive by remember { mutableStateOf(false) }

            // Handle initial incoming ACTION_VIEW / ACTION_SEND intent (e.g. tapping a .dao-backup file)
            LaunchedEffect(Unit) {
                extractIncomingBackupUri(intent)?.let { uri ->
                    viewModel.setPendingExternalImportUri(uri)
                }
            }

            // Handle subsequent incoming intents when MainActivity is already running (singleTop)
            DisposableEffect(Unit) {
                val listener = Consumer<Intent> { newIntent ->
                    extractIncomingBackupUri(newIntent)?.let { uri ->
                        viewModel.setPendingExternalImportUri(uri)
                    }
                }
                addOnNewIntentListener(listener)
                onDispose {
                    removeOnNewIntentListener(listener)
                }
            }

            // Selective FLAG_SECURE:
            // By default, normal browsing screens allow screenshots & screen recording.
            // FLAG_SECURE is applied only when:
            // 1. The user enabled "Restrict Screenshots Across Entire App" in Settings, OR
            // 2. The app is on the Master Lock Unlock screen, OR
            // 3. The user is actively viewing or editing a credential/password page or secret modal.
            val shouldSecureWindow =
                restrictScreenshotsEntireApp ||
                    lockState is LockState.Locked ||
                    isCredentialScreenOrModalActive

            LaunchedEffect(shouldSecureWindow) {
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
                    lockState = lockState,
                    restrictScreenshotsEntireApp = restrictScreenshotsEntireApp,
                    onCredentialProtectionStateChange = { active ->
                        isCredentialScreenOrModalActive = active
                    }
                )
            }
        }
    }

    private fun extractIncomingBackupUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            }
            else -> null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizerRootContent(
    activity: FragmentActivity,
    viewModel: OrganizerViewModel,
    lockState: LockState,
    restrictScreenshotsEntireApp: Boolean,
    onCredentialProtectionStateChange: (Boolean) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val services by viewModel.services.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
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
    val savedVaultBackups by viewModel.savedVaultBackups.collectAsStateWithLifecycle()
    val currentDirectoryListing by viewModel.currentDirectoryListing.collectAsStateWithLifecycle()
    val pendingExternalImportUri by viewModel.pendingExternalImportUri.collectAsStateWithLifecycle()
    val stagedBackup by viewModel.stagedBackup.collectAsStateWithLifecycle()
    val conflictResolutions by viewModel.conflictResolutions.collectAsStateWithLifecycle()
    val generatedExportLockKey by viewModel.generatedExportLockKey.collectAsStateWithLifecycle()

    // Store destination as a primitive String in rememberSaveable to prevent Bundle serialization issues
    var currentDestinationName by rememberSaveable { mutableStateOf(OrganizerDestination.DASHBOARD.name) }
    val currentDestination = remember(currentDestinationName) {
        OrganizerDestination.entries.firstOrNull { it.name == currentDestinationName }
            ?: OrganizerDestination.DASHBOARD
    }
    var selectedServiceIdForDetail by rememberSaveable { mutableStateOf<String?>(null) }

    var showAccountEditor by rememberSaveable { mutableStateOf(false) }
    var editingAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingAccount = remember(allAccounts, editingAccountId) {
        allAccounts.firstOrNull { it.account.id == editingAccountId }
    }

    var showServiceEditor by rememberSaveable { mutableStateOf(false) }
    var editingServiceId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingService = remember(services, editingServiceId) {
        services.firstOrNull { it.service.id == editingServiceId }
    }

    var showVaultVerificationDialog by remember { mutableStateOf(false) }
    var pendingVaultVerifiedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isChildCredentialScreenActive by remember { mutableStateOf(false) }

    // If an external .dao-backup file was opened via OS "Open With" or Share, jump to Backup screen
    LaunchedEffect(pendingExternalImportUri, lockState) {
        if (pendingExternalImportUri != null && lockState is LockState.Unlocked) {
            showAccountEditor = false
            showServiceEditor = false
            currentDestinationName = OrganizerDestination.BACKUP.name
        }
    }

    // Reset child credential flag when switching top-level tabs
    LaunchedEffect(currentDestinationName) {
        isChildCredentialScreenActive = false
    }

    val isAnyCredentialViewActive =
        showAccountEditor ||
            isChildCredentialScreenActive ||
            showVaultVerificationDialog ||
            !oneTimeRecoveryCode.isNullOrBlank()

    LaunchedEffect(isAnyCredentialViewActive) {
        onCredentialProtectionStateChange(isAnyCredentialViewActive)
    }

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

    val triggerOneTimeRecoveryCodeDownload: () -> Unit = {
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        viewModel.saveOneTimeRecoveryCodeDirectly("dao_recovery_code_$dateStamp.txt")
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
                        subtitle = "Verify your identity to view or copy saved passwords and notes",
                        onAuthenticated = {
                            viewModel.recordVaultSessionVerified()
                            onVerified()
                        }
                    )
                } else if (unlocked?.isAppLockEnabled == true) {
                    pendingVaultVerifiedAction = onVerified
                    showVaultVerificationDialog = true
                } else {
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
            if (!showAccountEditor && !showServiceEditor && currentDestination != OrganizerDestination.DASHBOARD) {
                BackHandler {
                    currentDestinationName = OrganizerDestination.DASHBOARD.name
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    topBar = {
                        if (!showAccountEditor && !showServiceEditor) {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "Digital Account Organizer",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
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
                        }
                    },
                    bottomBar = {
                        if (!useNavRail && !showAccountEditor && !showServiceEditor) {
                            NavigationBar {
                                OrganizerDestination.entries.forEach { dest ->
                                    NavigationBarItem(
                                        selected = currentDestination == dest,
                                        onClick = { currentDestinationName = dest.name },
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
                        if (useNavRail && !showAccountEditor && !showServiceEditor) {
                            NavigationRail {
                                OrganizerDestination.entries.forEach { dest ->
                                    NavigationRailItem(
                                        selected = currentDestination == dest,
                                        onClick = { currentDestinationName = dest.name },
                                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                                        label = { Text(dest.label) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            val activeOverlayPage = when {
                                showAccountEditor -> "ACCOUNT_EDITOR"
                                showServiceEditor -> "SERVICE_EDITOR"
                                else -> "MAIN_${currentDestination.name}"
                            }

                            AnimatedContent(
                                targetState = activeOverlayPage,
                                transitionSpec = {
                                    if (targetState == "ACCOUNT_EDITOR" || targetState == "SERVICE_EDITOR") {
                                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { -it / 4 } + fadeOut())
                                    } else if (initialState == "ACCOUNT_EDITOR" || initialState == "SERVICE_EDITOR") {
                                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { it / 4 } + fadeOut())
                                    } else {
                                        fadeIn() togetherWith fadeOut()
                                    }
                                },
                                label = "root_page_transition"
                            ) { pageState ->
                                when (pageState) {
                                    "ACCOUNT_EDITOR" -> {
                                        AccountEditorDialog(
                                            initialAccount = editingAccount,
                                            categories = categories,
                                            onDismiss = {
                                                showAccountEditor = false
                                                editingAccountId = null
                                            },
                                            onSave = { existingId, displayName, email, username, password, clearSavedPassword, categoryId, purpose, recEmail, recPhone, notes, status, onError ->
                                                viewModel.saveAccount(
                                                    existingId = existingId,
                                                    displayName = displayName,
                                                    email = email,
                                                    username = username,
                                                    password = password,
                                                    clearSavedPassword = clearSavedPassword,
                                                    categoryId = categoryId,
                                                    purpose = purpose,
                                                    recoveryEmailReference = recEmail,
                                                    recoveryPhoneReference = recPhone,
                                                    notes = notes,
                                                    status = status,
                                                    onSaved = {
                                                        showAccountEditor = false
                                                        editingAccountId = null
                                                    },
                                                    onError = onError
                                                )
                                            }
                                        )
                                    }

                                    "SERVICE_EDITOR" -> {
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
                                                editingServiceId = null
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
                                                        editingServiceId = null
                                                    },
                                                    onError = onError
                                                )
                                            }
                                        )
                                    }

                                    else -> {
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
                                                        currentDestinationName = OrganizerDestination.SETTINGS.name
                                                    },
                                                    onSkipAllLocks = {
                                                        viewModel.dismissInitialLockSuggestion(disableAllLocks = true)
                                                    },
                                                    onCreateServiceClick = {
                                                        editingServiceId = null
                                                        showServiceEditor = true
                                                    },
                                                    onAddAccountClick = {
                                                        editingAccountId = null
                                                        showAccountEditor = true
                                                    },
                                                    onOpenServiceDetail = { serviceId ->
                                                        selectedServiceIdForDetail = serviceId
                                                        currentDestinationName = OrganizerDestination.SERVICES.name
                                                    },
                                                    onOpenAccountsForCategory = { categoryId ->
                                                        viewModel.clearAllFilters()
                                                        viewModel.selectCategoryFilter(categoryId)
                                                        currentDestinationName = OrganizerDestination.ACCOUNTS.name
                                                    },
                                                    onOpenAllAccounts = {
                                                        currentDestinationName = OrganizerDestination.ACCOUNTS.name
                                                    },
                                                    onOpenBackup = {
                                                        currentDestinationName = OrganizerDestination.BACKUP.name
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
                                                        editingAccountId = null
                                                        showAccountEditor = true
                                                    },
                                                    onEditAccountClick = { item ->
                                                        editingAccountId = item.account.id
                                                        showAccountEditor = true
                                                    },
                                                    onToggleArchiveAccount = { id, archived ->
                                                        viewModel.setAccountArchived(id, archived)
                                                    },
                                                    onDeleteAccountPermanently = { id ->
                                                        viewModel.deleteAccountPermanently(id)
                                                    },
                                                    onDecryptSecret = { viewModel.decryptSecretField(it) },
                                                    onRequestVerifiedAction = requestVaultVerifiedAction,
                                                    onCredentialScreenActiveChange = { active ->
                                                        isChildCredentialScreenActive = active
                                                    }
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
                                                        editingServiceId = null
                                                        showServiceEditor = true
                                                    },
                                                    onEditServiceClick = { svc ->
                                                        editingServiceId = svc.service.id
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
                                                                onSaved = {
                                                                    showServiceEditor = false
                                                                    editingServiceId = null
                                                                },
                                                                onError = {}
                                                            )
                                                        }
                                                    },
                                                    onDismissDuplicatePrompt = {
                                                        viewModel.dismissDuplicateServicePrompt()
                                                    },
                                                    onDecryptSecret = { viewModel.decryptSecretField(it) },
                                                    onRequestVerifiedAction = requestVaultVerifiedAction,
                                                    onSaveServiceAccount = { existingId, serviceId, linkedAccId, displayName, email, username, password, clearSavedPassword, categoryId, purpose, recEmail, recPhone, webUrl, notes, status, onSaved, onError ->
                                                        viewModel.saveServiceAccount(
                                                            existingId = existingId,
                                                            serviceId = serviceId,
                                                            linkedAccountId = linkedAccId,
                                                            displayName = displayName,
                                                            email = email,
                                                            username = username,
                                                            password = password,
                                                            clearSavedPassword = clearSavedPassword,
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
                                                    },
                                                    onCredentialScreenActiveChange = { active ->
                                                        isChildCredentialScreenActive = active
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
                                                        currentDestinationName = OrganizerDestination.ACCOUNTS.name
                                                    }
                                                )
                                            }

                                            OrganizerDestination.BACKUP -> {
                                                BackupScreen(
                                                    accounts = filteredAccounts,
                                                    services = services,
                                                    categories = categories,
                                                    savedVaultBackups = savedVaultBackups,
                                                    storageShortcuts = viewModel.storageShortcuts,
                                                    currentDirectoryListing = currentDirectoryListing,
                                                    externalImportUri = pendingExternalImportUri,
                                                    stagedBackup = stagedBackup,
                                                    conflictResolutions = conflictResolutions,
                                                    generatedExportLockKey = generatedExportLockKey,
                                                    onBrowseStorageDirectory = { dirPath ->
                                                        viewModel.browseStorageDirectory(dirPath)
                                                    },
                                                    onRefreshBackups = {
                                                        viewModel.refreshSavedVaultBackups()
                                                    },
                                                    onConsumeExternalImportUri = {
                                                        viewModel.setPendingExternalImportUri(null)
                                                    },
                                                    onCredentialModalActiveChange = { active ->
                                                        isChildCredentialScreenActive = active
                                                    },
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
                                                    onSavePendingBackupDirectly = { fileName, onError ->
                                                        viewModel.savePendingEncryptedBackupDirectly(fileName, onError)
                                                    },
                                                    onWritePendingBackupToUri = { uri ->
                                                        viewModel.writePendingEncryptedBackupToUri(activity, uri)
                                                    },
                                                    onSavePlaintextCsvDirectly = { fileName, onError ->
                                                        viewModel.savePlaintextCsvDirectly(fileName, onError)
                                                    },
                                                    onPreparePlaintextCsv = { onReady, onError ->
                                                        viewModel.preparePlaintextCsvExport(onReady, onError)
                                                    },
                                                    onWritePendingCsvToUri = { uri ->
                                                        viewModel.writePendingCsvToUri(activity, uri)
                                                    },
                                                    onStageBackupFromVaultFile = { vaultPath, lockKey, onError ->
                                                        viewModel.stageBackupFromVaultFile(vaultPath, lockKey, onError)
                                                    },
                                                    onStageBackupFromUri = { uri, lockKey, onError ->
                                                        viewModel.stageBackupFromUri(activity, uri, lockKey, onError)
                                                    },
                                                    onDeleteVaultBackupFile = { vaultPath ->
                                                        viewModel.deleteVaultBackupFile(vaultPath)
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
                                                    restrictScreenshotsEntireApp = restrictScreenshotsEntireApp,
                                                    isBiometricHardwareAvailable = isBiometricHardwareAvailable,
                                                    onSelectThemePreference = { viewModel.setThemePreference(it) },
                                                    onSelectColorPalette = { viewModel.setColorPalette(it) },
                                                    onSetRestrictScreenshotsEntireApp = {
                                                        viewModel.setRestrictScreenshotsEntireApp(it)
                                                    },
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
                                                    onLockNow = { viewModel.lockOrganizerNow() },
                                                    onCredentialModalActiveChange = { active ->
                                                        isChildCredentialScreenActive = active
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
