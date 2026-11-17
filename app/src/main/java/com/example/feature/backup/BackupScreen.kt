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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.OfflinePrivacyBanner
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
    onWritePendingBackupToUri: (Uri) -> Unit,
    onPreparePlaintextCsv: (onReadyForSafSave: () -> Unit, onError: (String) -> Unit) -> Unit,
    onWritePendingCsvToUri: (Uri) -> Unit,
    onStageBackupFromUri: (Uri, String, onError: (String) -> Unit) -> Unit,
    onUpdateConflictResolution: (String, ConflictResolution) -> Unit,
    onCancelStagedBackup: () -> Unit,
    onCommitStagedBackup: (onError: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var selectedExportMode by rememberSaveable { mutableStateOf(ExportMode.FULL) }
    var selectedAccountIds by remember { mutableStateOf(setOf<String>()) }
    var selectedServiceIds by remember { mutableStateOf(setOf<String>()) }
    var selectedCategoryIds by remember { mutableStateOf(setOf<String>()) }

    var showGeneratedKeyModal by remember { mutableStateOf(false) }
    var confirmedKeySaved by remember { mutableStateOf(false) }
    var copiedKeyNotice by remember { mutableStateOf(false) }

    var showPlainCsvConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var importLockKeyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val timestamp = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) }

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
            pendingImportUri = uri
            importLockKeyInput = ""
            errorMessage = null
        }
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
                    text = "Export locked backups protected by an app-generated Export Lock Key, or import an existing .dao-backup file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OfflinePrivacyBanner(
                noticeText = "When you export a .dao-backup file, the application automatically generates a unique random Export Lock Key (e.g. DAO-XXXX-XXXX-XXXX-XXXX) and encrypts the file with AES-256-GCM + Argon2id. Copy and keep that key—anyone trying to import the file must enter it."
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Locked Encrypted Backup (.dao-backup)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Includes standalone Accounts, Services, Service Account entries, encrypted Passwords, custom logos, and Recovery Code metadata.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                                onClick = { selectedExportMode = mode },
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
                            openBackupDocumentLauncher.launch(arrayOf("*/*"))
                        },
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

        // 3. Plaintext CSV Audit Export Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(
                                text = "Plaintext CSV Inventory Export",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Exports non-sensitive account & service inventory to a spreadsheet-friendly CSV file. Saved passwords are never included in CSV exports.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showPlainCsvConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_plain_csv_button")
                    ) {
                        Text("Export Inventory CSV (No Passwords)")
                    }
                }
            }
        }
    }

    if (showGeneratedKeyModal && !generatedExportLockKey.isNullOrBlank()) {
        val lockKey = generatedExportLockKey
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGeneratedKeyModal = false
                        createEncryptedBackupLauncher.launch("digital_accounts_$timestamp.dao-backup")
                    },
                    enabled = confirmedKeySaved,
                    modifier = Modifier.testTag("save_encrypted_backup_file_button")
                ) {
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
                Text(
                    "Plaintext CSV files are readable by any spreadsheet app. Saved passwords are automatically excluded from CSV exports. Continue?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPlainCsvConfirm = false
                        onPreparePlaintextCsv(
                            { createPlainCsvLauncher.launch("digital_accounts_$timestamp.csv") },
                            { err -> errorMessage = err }
                        )
                    },
                    modifier = Modifier.testTag("confirm_plain_csv_export_button")
                ) {
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

    val importUri = pendingImportUri
    if (importUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Enter Export Lock Key to Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        val targetUri = importUri
                        val key = importLockKeyInput.trim()
                        pendingImportUri = null
                        onStageBackupFromUri(targetUri, key) { err ->
                            errorMessage = err
                        }
                    },
                    enabled = importLockKeyInput.trim().isNotEmpty(),
                    modifier = Modifier.testTag("confirm_stage_backup_button")
                ) {
                    Text("Unlock & Preview Import")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingImportUri = null }) {
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
