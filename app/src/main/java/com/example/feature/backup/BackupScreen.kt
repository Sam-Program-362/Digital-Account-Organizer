package com.example.feature.backup

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.FormSectionCard
import com.example.core.designsystem.OfflinePrivacyBanner
import com.example.core.files.SavedVaultBackupEntry
import com.example.core.files.StorageDirectoryListing
import com.example.core.files.StorageFolderShortcut
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.CategoryWithCount
import com.example.domain.model.ServiceWithCount
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(
    accounts: List<AccountWithDetails>,
    services: List<ServiceWithCount>,
    categories: List<CategoryWithCount>,
    savedVaultBackups: List<SavedVaultBackupEntry>,
    storageShortcuts: List<StorageFolderShortcut>,
    currentDirectoryListing: StorageDirectoryListing?,
    externalImportUri: Uri?,
    stagedBackup: StagedBackupPackage?,
    conflictResolutions: Map<String, ConflictResolution>,
    generatedExportLockKey: String?,
    onBrowseStorageDirectory: (String?) -> Unit,
    onRefreshBackups: () -> Unit,
    onConsumeExternalImportUri: () -> Unit,
    onCredentialModalActiveChange: (Boolean) -> Unit = {},
    onPrepareAutoLockedBackup: (
        mode: ExportMode,
        selectedAccountIds: Set<String>,
        selectedServiceIds: Set<String>,
        selectedCategoryIds: Set<String>,
        onLockKeyGenerated: (String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onClearGeneratedExportLockKey: () -> Unit,
    onSavePendingBackupDirectly: (fileName: String, onError: (String) -> Unit) -> Unit,
    onWritePendingBackupToUri: (Uri) -> Unit,
    onSavePlaintextCsvDirectly: (fileName: String, onError: (String) -> Unit) -> Unit,
    onPreparePlaintextCsv: (onReadyForSafSave: () -> Unit, onError: (String) -> Unit) -> Unit,
    onWritePendingCsvToUri: (Uri) -> Unit,
    onStageBackupFromVaultFile: (vaultFilePath: String, lockKey: String, onError: (String) -> Unit) -> Unit,
    onStageBackupFromUri: (Uri, String, onError: (String) -> Unit) -> Unit,
    onDeleteVaultBackupFile: (vaultFilePath: String) -> Unit,
    onUpdateConflictResolution: (String, ConflictResolution) -> Unit,
    onCancelStagedBackup: () -> Unit,
    onCommitStagedBackup: (onError: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var selectedExportModeName by rememberSaveable { mutableStateOf(ExportMode.FULL.name) }
    val selectedExportMode = remember(selectedExportModeName) {
        ExportMode.entries.firstOrNull { it.name == selectedExportModeName } ?: ExportMode.FULL
    }
    var selectedAccountIds by remember { mutableStateOf(setOf<String>()) }
    var selectedServiceIds by remember { mutableStateOf(setOf<String>()) }
    var selectedCategoryIds by remember { mutableStateOf(setOf<String>()) }

    var showGeneratedKeyModal by remember { mutableStateOf(false) }
    var confirmedKeySaved by remember { mutableStateOf(false) }
    var copiedKeyNotice by remember { mutableStateOf(false) }

    var showPlainCsvConfirm by remember { mutableStateOf(false) }
    var showImportSourceModal by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportVaultEntry by remember { mutableStateOf<SavedVaultBackupEntry?>(null) }
    var importLockKeyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var systemChooserNotice by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }

    // If the user opened or shared a .dao-backup file from an external File Manager / Quick Share / Chat app
    LaunchedEffect(externalImportUri) {
        if (externalImportUri != null) {
            pendingImportVaultEntry = null
            pendingImportUri = externalImportUri
            importLockKeyInput = ""
            errorMessage = null
            onConsumeExternalImportUri()
        }
    }

    val isCredentialDialogOpen =
        (showGeneratedKeyModal && !generatedExportLockKey.isNullOrBlank()) ||
            pendingImportUri != null ||
            pendingImportVaultEntry != null

    LaunchedEffect(isCredentialDialogOpen) {
        onCredentialModalActiveChange(isCredentialDialogOpen)
    }

    DisposableEffect(Unit) {
        onDispose {
            onCredentialModalActiveChange(false)
        }
    }

    val createEncryptedBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            onWritePendingBackupToUri(uri)
            onClearGeneratedExportLockKey()
        }
    }

    val createPlainCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            onWritePendingCsvToUri(uri)
        }
    }

    val openBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            showImportSourceModal = false
            pendingImportVaultEntry = null
            pendingImportUri = uri
            importLockKeyInput = ""
            errorMessage = null
        }
    }

    val getContentChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            showImportSourceModal = false
            pendingImportVaultEntry = null
            pendingImportUri = uri
            importLockKeyInput = ""
            errorMessage = null
        }
    }

    val launchSystemFileChooserWithFallback: () -> Unit = {
        systemChooserNotice = null
        val openedViaSaf = runCatching {
            openBackupDocumentLauncher.launch(arrayOf("*/*"))
            true
        }.getOrDefault(false)

        if (!openedViaSaf) {
            val openedViaGetContent = runCatching {
                val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                val chooser = Intent.createChooser(getContentIntent, "Select .dao-backup file")
                getContentChooserLauncher.launch(chooser)
                true
            }.getOrDefault(false)

            if (!openedViaGetContent) {
                systemChooserNotice =
                    "No external system file manager app is installed on this device. Use the built-in Device Folder Browser below or select from Discovered Backups."
            }
        }
    }

    val encryptedVaultBackups = remember(savedVaultBackups) {
        savedVaultBackups.filter { it.isEncryptedBackup }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("backup_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Portable cross-device .dao-backup exports locked with an auto-generated Export Lock Key, plus an In-App Device Storage Browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OfflinePrivacyBanner(
                noticeText = "Every exported .dao-backup file is portable across devices and locked with a random Export Lock Key (DAO-XXXX-XXXX-XXXX-XXXX) using AES-256-GCM + Argon2id. You can import backups from Downloads, any device folder, or via 'Open With' in your phone's File Manager."
            )
        }

        if (errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 1. Export Locked .dao-backup Card
        item {
            FormSectionCard(
                title = "Locked Encrypted Backup (.dao-backup)",
                subtitle = "Portable across devices. Includes standalone Accounts, Services, Service Account entries, encrypted Passwords, custom logos, and Recovery Code metadata.",
                icon = Icons.Default.Lock
            ) {
                Text(
                    text = "Export Scope",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedExportMode == mode,
                            onClick = { selectedExportModeName = mode.name },
                            label = { Text(mode.displayLabel) },
                            modifier = Modifier.testTag("export_mode_${mode.name}")
                        )
                    }
                }

                when (selectedExportMode) {
                    ExportMode.FULL -> Unit
                    ExportMode.SELECTED_ACCOUNTS -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            accounts.forEach { acc ->
                                val selected = acc.account.id in selectedAccountIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedAccountIds = if (selected) {
                                            selectedAccountIds - acc.account.id
                                        } else {
                                            selectedAccountIds + acc.account.id
                                        }
                                    },
                                    label = { Text(acc.account.displayName) }
                                )
                            }
                        }
                    }
                    ExportMode.SELECTED_SERVICES -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            services.forEach { svc ->
                                val selected = svc.service.id in selectedServiceIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedServiceIds = if (selected) {
                                            selectedServiceIds - svc.service.id
                                        } else {
                                            selectedServiceIds + svc.service.id
                                        }
                                    },
                                    label = { Text(svc.service.name) }
                                )
                            }
                        }
                    }
                    ExportMode.SELECTED_CATEGORIES -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val selected = cat.category.id in selectedCategoryIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedCategoryIds = if (selected) {
                                            selectedCategoryIds - cat.category.id
                                        } else {
                                            selectedCategoryIds + cat.category.id
                                        }
                                    },
                                    label = { Text(cat.category.name) }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        onPrepareAutoLockedBackup(
                            selectedExportMode,
                            selectedAccountIds,
                            selectedServiceIds,
                            selectedCategoryIds,
                            {
                                confirmedKeySaved = false
                                copiedKeyNotice = false
                                showGeneratedKeyModal = true
                            },
                            { err -> errorMessage = err }
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_encrypted_backup_button")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Export Lock Key & Export .dao-backup")
                }

                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        systemChooserNotice = null
                        onRefreshBackups()
                        onBrowseStorageDirectory(currentDirectoryListing?.currentPath)
                        showImportSourceModal = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_encrypted_backup_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import / Restore .dao-backup (Browse Storage)")
                }
            }
        }

        // 2. Staged Backup Preview & Conflict Resolution (if staged)
        if (stagedBackup != null) {
            item {
                StagedBackupPreviewCard(
                    staged = stagedBackup,
                    resolutions = conflictResolutions,
                    onUpdateResolution = onUpdateConflictResolution,
                    onCancel = onCancelStagedBackup,
                    onCommit = {
                        onCommitStagedBackup { err -> errorMessage = err }
                    }
                )
            }
        }

        // 3. Discovered Backups in Downloads & Built-In Backup Vault
        item {
            FormSectionCard(
                title = "Discovered Backups in Downloads & Vault (${savedVaultBackups.size})",
                subtitle = "Automatically discovers .dao-backup files in your device's Downloads folder, Documents folder, and in-app Backup Vault.",
                icon = Icons.Default.SaveAlt
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap Import on any backup below or browse folders:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onRefreshBackups() },
                        modifier = Modifier.testTag("refresh_discovered_backups_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rescan")
                    }
                }

                if (savedVaultBackups.isEmpty()) {
                    Text(
                        text = "No .dao-backup or .csv files found yet. Export a backup above or tap 'Import / Restore .dao-backup' to browse your device storage folders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedVaultBackups.forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vault_backup_item_${entry.fileName}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = entry.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sizeKb = (entry.byteSize / 1024L).coerceAtLeast(1L)
                                        Text(
                                            text = "${dateFormatter.format(Date(entry.lastModifiedMillis))} • $sizeKb KB • ${entry.sourceLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (entry.isEncryptedBackup) {
                                            FilledTonalButton(
                                                onClick = {
                                                    pendingImportUri = null
                                                    pendingImportVaultEntry = entry
                                                    importLockKeyInput = ""
                                                    errorMessage = null
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("import_vault_backup_${entry.fileName}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LockOpen,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Import", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        IconButton(
                                            onClick = { onDeleteVaultBackupFile(entry.absolutePath) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete ${entry.fileName}",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
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

        // 4. Plaintext CSV Audit Export Card
        item {
            FormSectionCard(
                title = "Plaintext CSV Inventory Export (No Passwords)",
                subtitle = "Exports non-sensitive account & service inventory to a spreadsheet-friendly CSV file in Downloads & Backup Vault.",
                icon = Icons.Default.Description
            ) {
                OutlinedButton(
                    onClick = { showPlainCsvConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_plain_csv_button")
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Inventory CSV (No Passwords)")
                }
            }
        }
    }

    if (showGeneratedKeyModal && !generatedExportLockKey.isNullOrBlank()) {
        val lockKey = generatedExportLockKey
        val fileTimestamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
        val targetFileName = "digital_accounts_$fileTimestamp.dao-backup"

        AlertDialog(
            onDismissRequest = {
                showGeneratedKeyModal = false
                onClearGeneratedExportLockKey()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Save Your Export Lock Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "The application generated this random Export Lock Key to lock your .dao-backup file. Copy and save this key—anyone importing this backup file on this or another device will be asked for it.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = lockKey,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("generated_export_lock_key_text")
                            )
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("DAO Export Lock Key", lockKey)
                                    )
                                    copiedKeyNotice = true
                                    confirmedKeySaved = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("copy_export_lock_key_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (copiedKeyNotice) "Copied to Clipboard!" else "Copy Export Lock Key")
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = confirmedKeySaved,
                            onCheckedChange = { confirmedKeySaved = it },
                            modifier = Modifier.testTag("confirm_export_lock_key_checkbox")
                        )
                        Text(
                            text = "I have copied or saved this Export Lock Key.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    TextButton(
                        onClick = {
                            showGeneratedKeyModal = false
                            runCatching {
                                createEncryptedBackupLauncher.launch(targetFileName)
                            }.onFailure {
                                onSavePendingBackupDirectly(targetFileName) { err ->
                                    errorMessage = err
                                }
                            }
                        },
                        enabled = confirmedKeySaved,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Or Choose Custom Folder via System Picker...")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGeneratedKeyModal = false
                        onSavePendingBackupDirectly(targetFileName) { err ->
                            errorMessage = err
                        }
                    },
                    enabled = confirmedKeySaved,
                    modifier = Modifier.testTag("save_encrypted_backup_file_button")
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Locked .dao-backup File")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGeneratedKeyModal = false
                        onClearGeneratedExportLockKey()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlainCsvConfirm) {
        val csvTimestamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
        val csvFileName = "digital_accounts_$csvTimestamp.csv"

        AlertDialog(
            onDismissRequest = { showPlainCsvConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = { Text("Export Unencrypted CSV?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Plaintext CSV files are readable by any spreadsheet app and are saved directly to Downloads & your Built-In Backup Vault. Saved passwords are automatically excluded from CSV exports."
                    )
                    TextButton(
                        onClick = {
                            showPlainCsvConfirm = false
                            onPreparePlaintextCsv(
                                {
                                    runCatching {
                                        createPlainCsvLauncher.launch(csvFileName)
                                    }.onFailure {
                                        onSavePlaintextCsvDirectly(csvFileName) { err ->
                                            errorMessage = err
                                        }
                                    }
                                },
                                { err -> errorMessage = err }
                            )
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Or Choose Custom Folder via System Picker...")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPlainCsvConfirm = false
                        onSavePlaintextCsvDirectly(csvFileName) { err ->
                            errorMessage = err
                        }
                    },
                    modifier = Modifier.testTag("confirm_plain_csv_export_button")
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export CSV")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPlainCsvConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImportSourceModal) {
        DeviceStorageBackupBrowserDialog(
            discoveredBackups = encryptedVaultBackups,
            storageShortcuts = storageShortcuts,
            currentDirectoryListing = currentDirectoryListing,
            systemChooserNotice = systemChooserNotice,
            onBrowseDirectory = onBrowseStorageDirectory,
            onRefreshDiscovered = {
                onRefreshBackups()
                onBrowseStorageDirectory(currentDirectoryListing?.currentPath)
            },
            onSelectBackupEntry = { entry ->
                showImportSourceModal = false
                pendingImportUri = null
                pendingImportVaultEntry = entry
                importLockKeyInput = ""
                errorMessage = null
            },
            onSelectFilePath = { filePath ->
                val f = File(filePath.trim())
                showImportSourceModal = false
                pendingImportUri = null
                pendingImportVaultEntry = SavedVaultBackupEntry(
                    fileName = f.name.ifBlank { filePath.substringAfterLast('/') },
                    absolutePath = filePath.trim(),
                    byteSize = if (f.exists()) f.length() else 0L,
                    lastModifiedMillis = if (f.exists()) f.lastModified() else System.currentTimeMillis(),
                    isEncryptedBackup = true,
                    sourceLabel = "Device Storage"
                )
                importLockKeyInput = ""
                errorMessage = null
            },
            onLaunchSystemFileChooser = launchSystemFileChooserWithFallback,
            onDismiss = { showImportSourceModal = false }
        )
    }

    val importUri = pendingImportUri
    val importVaultEntry = pendingImportVaultEntry
    if (importUri != null || importVaultEntry != null) {
        AlertDialog(
            onDismissRequest = {
                pendingImportUri = null
                pendingImportVaultEntry = null
            },
            title = { Text("Enter Export Lock Key to Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (importVaultEntry != null) {
                        Text(
                            text = "Selected Backup: ${importVaultEntry.fileName} (${importVaultEntry.sourceLabel})",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (importUri != null) {
                        Text(
                            text = "Selected External Backup File",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Enter or paste the Export Lock Key (e.g., DAO-XXXX-XXXX-XXXX-XXXX) that the application generated when this .dao-backup file was created.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importLockKeyInput,
                        onValueChange = { importLockKeyInput = it },
                        label = { Text("Export Lock Key") },
                        placeholder = { Text("DAO-XXXX-XXXX-XXXX-XXXX") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_lock_key_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val key = importLockKeyInput.trim()
                        val targetVault = importVaultEntry
                        val targetUri = importUri
                        pendingImportVaultEntry = null
                        pendingImportUri = null
                        if (targetVault != null) {
                            onStageBackupFromVaultFile(targetVault.absolutePath, key) { err ->
                                errorMessage = err
                            }
                        } else if (targetUri != null) {
                            onStageBackupFromUri(targetUri, key) { err ->
                                errorMessage = err
                            }
                        }
                    },
                    enabled = importLockKeyInput.trim().isNotEmpty(),
                    modifier = Modifier.testTag("confirm_stage_backup_button")
                ) {
                    Text("Unlock & Preview Import")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        pendingImportUri = null
                        pendingImportVaultEntry = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceStorageBackupBrowserDialog(
    discoveredBackups: List<SavedVaultBackupEntry>,
    storageShortcuts: List<StorageFolderShortcut>,
    currentDirectoryListing: StorageDirectoryListing?,
    systemChooserNotice: String?,
    onBrowseDirectory: (String?) -> Unit,
    onRefreshDiscovered: () -> Unit,
    onSelectBackupEntry: (SavedVaultBackupEntry) -> Unit,
    onSelectFilePath: (String) -> Unit,
    onLaunchSystemFileChooser: () -> Unit,
    onDismiss: () -> Unit
) {
    var manualFilePath by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Browse & Select .dao-backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefreshDiscovered) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh storage scan"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Discovered .dao-backup files across Downloads, Documents & App Vault
                Text(
                    text = "Discovered in Downloads & Device Storage (${discoveredBackups.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                if (discoveredBackups.isEmpty()) {
                    Text(
                        text = "No .dao-backup files automatically detected in Downloads yet. Use the Folder Browser or System File App below to pick a file from another folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    discoveredBackups.take(8).forEach { entry ->
                        OutlinedButton(
                            onClick = { onSelectBackupEntry(entry) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${entry.sourceLabel} • ${(entry.byteSize / 1024L).coerceAtLeast(1L)} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 2. Built-In Device Folder Explorer
                Text(
                    text = "In-App Device Storage Folder Browser",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    storageShortcuts.forEach { shortcut ->
                        val isCurrent = currentDirectoryListing?.currentPath == shortcut.path
                        FilterChip(
                            selected = isCurrent,
                            onClick = { onBrowseDirectory(shortcut.path) },
                            label = { Text(shortcut.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("storage_shortcut_${shortcut.label}")
                        )
                    }
                }

                if (currentDirectoryListing != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentDirectoryListing.currentPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (currentDirectoryListing.parentPath != null) {
                                    TextButton(
                                        onClick = { onBrowseDirectory(currentDirectoryListing.parentPath) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = "Parent folder",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Up", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (currentDirectoryListing.entries.isEmpty()) {
                                Text(
                                    text = "No subdirectories or .dao-backup files in this folder.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                currentDirectoryListing.entries.take(15).forEach { node ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (node.isDirectory) {
                                                    onBrowseDirectory(node.absolutePath)
                                                } else {
                                                    onSelectFilePath(node.absolutePath)
                                                }
                                            }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (node.isDirectory) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = node.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = if (node.isDirectory) null else JetBrainsMonoFontFamily,
                                            fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!node.isDirectory) {
                                            Text(
                                                text = "${(node.byteSize / 1024L).coerceAtLeast(1L)} KB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Direct File Path Entry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualFilePath,
                        onValueChange = { manualFilePath = it },
                        label = { Text("Or enter .dao-backup file path") },
                        placeholder = { Text("/storage/emulated/0/Download/backup.dao-backup") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_backup_path_input")
                    )
                    FilledTonalButton(
                        onClick = { onSelectFilePath(manualFilePath) },
                        enabled = manualFilePath.trim().isNotEmpty(),
                        modifier = Modifier.testTag("open_manual_backup_path_button")
                    ) {
                        Text("Open")
                    }
                }

                HorizontalDivider()

                // 4. Multi-Fallback System File Manager Chooser
                OutlinedButton(
                    onClick = onLaunchSystemFileChooser,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browse_external_backup_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open System File Manager App...")
                }

                if (systemChooserNotice != null) {
                    Text(
                        text = systemChooserNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StagedBackupPreviewCard(
    staged: StagedBackupPackage,
    resolutions: Map<String, ConflictResolution>,
    onUpdateResolution: (String, ConflictResolution) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staged_backup_preview_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Backup Unlocked — Ready to Import",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Exported at ${staged.exportedAtIso} • ${staged.accounts.size} standalone account(s), ${staged.serviceAccounts.size} service entry(s), ${staged.services.size} service(s), ${staged.categories.size} category(s).",
                style = MaterialTheme.typography.bodySmall
            )

            if (staged.conflicts.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Conflicts Detected (${staged.conflicts.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                staged.conflicts.forEach { conflict ->
                    val currentChoice = resolutions[conflict.incomingId] ?: conflict.defaultResolution
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${conflict.recordType}: ${conflict.title} (${conflict.kind.displayLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConflictResolution.entries.forEach { res ->
                                FilterChip(
                                    selected = currentChoice == res,
                                    onClick = { onUpdateResolution(conflict.incomingId, res) },
                                    label = { Text(res.displayLabel) }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onCommit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("commit_staged_backup_button")
                ) {
                    Text("Commit Import")
                }
            }
        }
    }
}
