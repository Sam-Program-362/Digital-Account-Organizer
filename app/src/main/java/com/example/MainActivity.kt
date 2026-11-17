package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.DigitalAccountOrganizerApp
import com.example.app.OrganizerViewModel
import com.example.core.security.LockState
import com.example.feature.accounts.AccountsScreen
import com.example.feature.backup.BackupScreen
import com.example.feature.categories.CategoriesScreen
import com.example.feature.dashboard.DashboardScreen
import com.example.feature.security.OneTimeRecoveryKeyDialog
import com.example.feature.security.UnlockScreen
import com.example.feature.services.ServicesScreen
import com.example.feature.settings.SettingsScreen
import com.example.ui.theme.DigitalAccountOrganizerTheme
import javax.crypto.Cipher

enum class OrganizerDestination(
    val routeId: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    ACCOUNTS("accounts", R.string.nav_accounts, Icons.Filled.Person, Icons.Outlined.Person),
    SERVICES("services", R.string.nav_services, Icons.Filled.Apps, Icons.Outlined.Apps),
    CATEGORIES("categories", R.string.nav_categories, Icons.Filled.Folder, Icons.Outlined.Folder),
    BACKUP("backup", R.string.nav_backup, Icons.Filled.Backup, Icons.Outlined.Backup),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
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

            // Apply FLAG_SECURE whenever app protection is enabled or organizer is locked
            val shouldProtectWindow = when (val state = lockState) {
                is LockState.Locked -> true
                is LockState.Unlocked -> state.isAppLockEnabled
                LockState.Uninitialized -> true
            }
            LaunchedEffect(shouldProtectWindow) {
                if (shouldProtectWindow) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            DigitalAccountOrganizerTheme(themePreference = themePreference) {
                OrganizerRootContent(
                    activity = this,
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerRootContent(
    activity: FragmentActivity,
    viewModel: OrganizerViewModel
) {
    val context = LocalContext.current
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    val transientMessage by viewModel.transientMessage.collectAsStateWithLifecycle()
    val oneTimeRecoveryCode by viewModel.oneTimeRecoveryCode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(transientMessage) {
        val msg = transientMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearTransientMessage()
    }

    when (val currentLock = lockState) {
        LockState.Uninitialized -> {
            Box(modifier = Modifier.fillMaxSize())
        }
        is LockState.Locked -> {
            UnlockScreen(
                lockedState = currentLock,
                errorMessage = unlockError,
                onUnlockWithPassword = viewModel::unlockWithPassword,
                onRequestBiometricUnlock = {
                    viewModel.prepareBiometricUnlockCipher()
                        .onSuccess { cipher ->
                            showStrongBiometricPrompt(
                                activity = activity,
                                title = "Unlock Digital Account Organizer",
                                subtitle = "Authenticate with strong biometrics to unwrap the database key",
                                cipher = cipher,
                                onSuccess = { authedCipher ->
                                    viewModel.unlockWithBiometricCipher(authedCipher)
                                }
                            )
                        }
                },
                onRecoverWithKey = viewModel::recoverWithKeyAndResetPassword,
                onDestructiveReset = viewModel::performDestructiveOrganizerReset,
                onClearError = viewModel::clearUnlockError
            )
        }
        is LockState.Unlocked -> {
            val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
            val services by viewModel.services.collectAsStateWithLifecycle()
            val categories by viewModel.categories.collectAsStateWithLifecycle()
            val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
            val filteredAccounts by viewModel.filteredAccounts.collectAsStateWithLifecycle()
            val filterState by viewModel.filterState.collectAsStateWithLifecycle()
            val duplicatePrompt by viewModel.duplicateServicePrompt.collectAsStateWithLifecycle()
            val stagedBackup by viewModel.stagedBackup.collectAsStateWithLifecycle()
            val conflictResolutions by viewModel.conflictResolutions.collectAsStateWithLifecycle()
            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()

            var currentDestination by rememberSaveable { mutableStateOf(OrganizerDestination.HOME) }
            var openCreateServiceDialogRequest by rememberSaveable { mutableStateOf(false) }
            var openCreateAccountForServiceId by rememberSaveable { mutableStateOf<String?>(null) }

            if (currentDestination != OrganizerDestination.HOME) {
                BackHandler {
                    currentDestination = OrganizerDestination.HOME
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isExpandedWidth = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            actions = {
                                if (currentLock.isAppLockEnabled) {
                                    IconButton(
                                        onClick = viewModel::lockOrganizerNow,
                                        modifier = Modifier.testTag("top_bar_lock_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.action_lock_now)
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    },
                    bottomBar = {
                        if (!isExpandedWidth) {
                            NavigationBar {
                                OrganizerDestination.entries.forEach { dest ->
                                    val selected = currentDestination == dest
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentDestination = dest },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelResId)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(dest.labelResId),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag("nav_tab_${dest.routeId}")
                                    )
                                }
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isExpandedWidth) {
                            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                OrganizerDestination.entries.forEach { dest ->
                                    val selected = currentDestination == dest
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentDestination = dest },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelResId)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelResId)) },
                                        modifier = Modifier.testTag("nav_rail_${dest.routeId}")
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            when (currentDestination) {
                                OrganizerDestination.HOME -> {
                                    DashboardScreen(
                                        summary = dashboardSummary,
                                        isAppLockEnabled = currentLock.isAppLockEnabled,
                                        onCreateServiceClick = {
                                            currentDestination = OrganizerDestination.SERVICES
                                            openCreateServiceDialogRequest = true
                                        },
                                        onAddAccountClick = {
                                            if (services.isEmpty()) {
                                                currentDestination = OrganizerDestination.SERVICES
                                                openCreateServiceDialogRequest = true
                                            } else {
                                                currentDestination = OrganizerDestination.ACCOUNTS
                                                openCreateAccountForServiceId = ""
                                            }
                                        },
                                        onOpenAccountsForService = { serviceId ->
                                            viewModel.clearAllFilters()
                                            viewModel.selectServiceFilter(serviceId)
                                            currentDestination = OrganizerDestination.ACCOUNTS
                                        },
                                        onOpenAccountsForCategory = { categoryId ->
                                            viewModel.clearAllFilters()
                                            viewModel.selectCategoryFilter(categoryId)
                                            currentDestination = OrganizerDestination.ACCOUNTS
                                        },
                                        onOpenAllAccounts = {
                                            viewModel.clearAllFilters()
                                            currentDestination = OrganizerDestination.ACCOUNTS
                                        },
                                        onOpenBackup = {
                                            currentDestination = OrganizerDestination.BACKUP
                                        },
                                        onLockNowClick = viewModel::lockOrganizerNow
                                    )
                                }
                                OrganizerDestination.ACCOUNTS -> {
                                    AccountsScreen(
                                        accounts = filteredAccounts,
                                        services = services,
                                        categories = categories,
                                        filterState = filterState,
                                        openCreateDialogForServiceId = openCreateAccountForServiceId,
                                        onConsumeCreateDialogRequest = { openCreateAccountForServiceId = null },
                                        onUpdateSearchQuery = viewModel::updateSearchQuery,
                                        onSelectServiceFilter = viewModel::selectServiceFilter,
                                        onSelectCategoryFilter = viewModel::selectCategoryFilter,
                                        onSelectStatusFilter = viewModel::selectStatusFilter,
                                        onToggleShowArchived = viewModel::setShowArchived,
                                        onSelectSortOrder = viewModel::setSortOrder,
                                        onClearAllFilters = viewModel::clearAllFilters,
                                        onSaveAccount = viewModel::saveAccount,
                                        onToggleArchiveAccount = viewModel::setAccountArchived,
                                        onDeleteAccountPermanently = viewModel::deleteAccountPermanently,
                                        onNavigateToCreateService = {
                                            currentDestination = OrganizerDestination.SERVICES
                                            openCreateServiceDialogRequest = true
                                        }
                                    )
                                }
                                OrganizerDestination.SERVICES -> {
                                    ServicesScreen(
                                        services = services,
                                        allAccounts = allAccounts,
                                        duplicatePrompt = duplicatePrompt,
                                        openCreateDialogRequest = openCreateServiceDialogRequest,
                                        onConsumeCreateDialogRequest = { openCreateServiceDialogRequest = false },
                                        onImportLogoUri = viewModel::importServiceLogo,
                                        onFetchLogoFromUrl = viewModel::fetchServiceLogoFromUrl,
                                        onCancelLogoFetch = viewModel::cancelServiceLogoFetch,
                                        onSaveService = viewModel::saveService,
                                        onDismissDuplicatePrompt = viewModel::dismissDuplicateServicePrompt,
                                        onToggleArchiveService = viewModel::setServiceArchived,
                                        onDeleteOrResolveService = viewModel::deleteOrResolveService,
                                        onAddAccountForService = { serviceId ->
                                            currentDestination = OrganizerDestination.ACCOUNTS
                                            openCreateAccountForServiceId = serviceId
                                        }
                                    )
                                }
                                OrganizerDestination.CATEGORIES -> {
                                    CategoriesScreen(
                                        categories = categories,
                                        onSaveCategory = viewModel::saveCategory,
                                        onDeleteCategorySafely = viewModel::deleteCategorySafely,
                                        onBrowseCategoryAccounts = { categoryId ->
                                            viewModel.clearAllFilters()
                                            viewModel.selectCategoryFilter(categoryId)
                                            currentDestination = OrganizerDestination.ACCOUNTS
                                        }
                                    )
                                }
                                OrganizerDestination.BACKUP -> {
                                    BackupScreen(
                                        services = services,
                                        categories = categories,
                                        accounts = allAccounts,
                                        stagedBackup = stagedBackup,
                                        conflictResolutions = conflictResolutions,
                                        onPrepareEncryptedExport = viewModel::prepareEncryptedBackup,
                                        onWriteEncryptedExportToUri = { uri ->
                                            viewModel.writePendingEncryptedBackupToUri(context, uri)
                                        },
                                        onPrepareCsvExport = viewModel::preparePlaintextCsvExport,
                                        onWriteCsvExportToUri = { uri ->
                                            viewModel.writePendingCsvToUri(context, uri)
                                        },
                                        onStageBackupFromUri = { uri, pass, onErr ->
                                            viewModel.stageBackupFromUri(context, uri, pass, onErr)
                                        },
                                        onUpdateConflictResolution = viewModel::updateConflictResolution,
                                        onCommitStagedBackup = viewModel::commitStagedBackup,
                                        onCancelStagedBackup = viewModel::cancelStagedBackup
                                    )
                                }
                                OrganizerDestination.SETTINGS -> {
                                    SettingsScreen(
                                        unlockedState = currentLock,
                                        themePreference = themePreference,
                                        onSelectThemePreference = viewModel::setThemePreference,
                                        onEnableAppLock = viewModel::enableAppLock,
                                        onChangeMasterPassword = viewModel::changeMasterPassword,
                                        onDisableAppLock = viewModel::disableAppLock,
                                        onUpdateAutoLockTimeout = viewModel::updateAutoLockTimeout,
                                        onRequestEnableBiometric = {
                                            val canAuth = BiometricManager.from(activity)
                                                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                                                viewModel.prepareBiometricEnrollmentCipher()
                                                    .onSuccess { cipher ->
                                                        showStrongBiometricPrompt(
                                                            activity = activity,
                                                            title = "Enable Strong Biometric Unlock",
                                                            subtitle = "Confirm strong biometric authentication to wrap the database key",
                                                            cipher = cipher,
                                                            onSuccess = { authedCipher ->
                                                                viewModel.completeBiometricEnrollment(authedCipher)
                                                            }
                                                        )
                                                    }
                                            }
                                        },
                                        onDisableBiometric = viewModel::disableBiometricUnlock,
                                        onLockNow = viewModel::lockOrganizerNow
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val recoveryCodeToDisplay = oneTimeRecoveryCode
    if (recoveryCodeToDisplay != null) {
        OneTimeRecoveryKeyDialog(
            recoveryCode = recoveryCodeToDisplay,
            onConfirmedSaved = viewModel::acknowledgeOneTimeRecoveryCode
        )
    }
}

private fun showStrongBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cipher: Cipher,
    onSuccess: (Cipher) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                result.cryptoObject?.cipher?.let(onSuccess)
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Use Master Password")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
}
