package com.example.feature.backup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.CategoryWithCount
import com.example.domain.model.ServiceWithCount
import com.example.ui.theme.JetBrainsMonoFontFamily
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
    stagedBackup: StagedBackupPackage?,
    conflictResolutions: Map<String, ConflictResolution>,
    generatedExportLockKey: String?,
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

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }

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
            pendingImportVaultEntry = null
            pendingImportUri = uri
            importLockKeyInput = ""
            errorMessage = null
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
                    text = "Export locked backups directly to Downloads & Built-In Backup Vault, or restore with your Export Lock Key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OfflinePrivacyBanner(
                noticeText = "When you export a .dao-backup file, the application automatically generates a unique random Export Lock Key (e.g. DAO-XXXX-XXXX-XXXX-XXXX) and encrypts the file with AES-256-GCM + Argon2id. Files are saved directly to Downloads and your Built-In Backup Vault list."
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
                subtitle = "Includes standalone Accounts, Services, Service Account entries, encrypted Passwords, custom logos, and Recovery Code metadata.",
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
                        if (encryptedVaultBackups.isNotEmpty()) {
                            showImportSourceModal = true
                        } else {
                            runCatching {
                                openBackupDocumentLauncher.launch(arrayOf("*/*"))
                            }.onFailure {
                                showImportSourceModal = true
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_encrypted_backup_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import / Restore .dao-backup")
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

        // 3. Built-In Backup Vault & Downloads List
        item {
            FormSectionCard(
                title = "Built-In Backup Vault & Downloads (${savedVaultBackups.size})",
                subtitle = "Saved .dao-backup and .csv files stored in Downloads and your in-app Backup Vault.",
                icon = Icons.Default.SaveAlt
            ) {
                if (savedVaultBackups.isEmpty()) {
                    Text(
                        text = "No saved backup files in your Built-In Backup Vault yet. Export a .dao-backup or CSV above to save directly to Downloads and this vault list.",
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
                                            text = "${dateFormatter.format(Date(entry.lastModifiedMillis))} • $sizeKb KB • Saved in Downloads & Vault",
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
                                                contentDescription = "Delete ${entry.fileName} from vault",
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
                        text = "The application generated this random Export Lock Key to lock your .dao-backup file. Copy and save this key—anyone importing this backup file will be asked for it.",
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
        AlertDialog(
            onDismissRequest = { showImportSourceModal = false },
            title = { Text("Select .dao-backup to Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (encryptedVaultBackups.isEmpty()) {
                        Text(
                            text = "No .dao-backup files are stored in the Built-In Backup Vault yet. Export a backup first or browse an external file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Choose a backup from your Built-In Backup Vault (Downloads):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        encryptedVaultBackups.take(5).forEach { entry ->
                            OutlinedButton(
                                onClick = {
                                    showImportSourceModal = false
                                    pendingImportUri = null
                                    pendingImportVaultEntry = entry
                                    importLockKeyInput = ""
                                    errorMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = entry.fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = {
                            showImportSourceModal = false
                            runCatching {
                                openBackupDocumentLauncher.launch(arrayOf("*/*"))
                            }.onFailure {
                                errorMessage =
                                    "System file picker is unavailable on this device. Select a backup from the Built-In Backup Vault list."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("browse_external_backup_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Browse Device Storage...")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportSourceModal = false }) {
                    Text("Close")
                }
            }
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
                            text = "Selected Backup: ${importVaultEntry.fileName}",
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
