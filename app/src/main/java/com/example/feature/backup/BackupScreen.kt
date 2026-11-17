package com.example.feature.backup

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.CategoryWithCount
import com.example.domain.model.ServiceWithCount

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(
    services: List<ServiceWithCount>,
    categories: List<CategoryWithCount>,
    accounts: List<AccountWithDetails>,
    stagedBackup: StagedBackupPackage?,
    conflictResolutions: Map<String, ConflictResolution>,
    onPrepareEncryptedExport: (
        passphrase: String,
        mode: ExportMode,
        selectedAccountIds: Set<String>,
        selectedServiceIds: Set<String>,
        selectedCategoryIds: Set<String>,
        onReadyForSafSave: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onWriteEncryptedExportToUri: (Uri) -> Unit,
    onPrepareCsvExport: (onReadyForSafSave: () -> Unit, onError: (String) -> Unit) -> Unit,
    onWriteCsvExportToUri: (Uri) -> Unit,
    onStageBackupFromUri: (Uri, String, (String) -> Unit) -> Unit,
    onUpdateConflictResolution: (String, ConflictResolution) -> Unit,
    onCommitStagedBackup: ((String) -> Unit) -> Unit,
    onCancelStagedBackup: () -> Unit
) {
    val context = LocalContext.current
    var selectedExportMode by rememberSaveable { mutableStateOf(ExportMode.FULL) }
    var selectedServiceIds by remember { mutableStateOf(setOf<String>()) }
    var selectedCategoryIds by remember { mutableStateOf(setOf<String>()) }
    var selectedAccountIds by remember { mutableStateOf(setOf<String>()) }

    var showExportPassphraseDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showCsvWarningDialog by rememberSaveable { mutableStateOf(false) }
    var errorBanner by remember { mutableStateOf<String?>(null) }

    val createEncryptedDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            onWriteEncryptedExportToUri(uri)
        }
    }

    val createCsvDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            onWriteCsvExportToUri(uri)
        }
    }

    val openBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            errorBanner = null
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
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Portable encrypted .dao-backup archives work across devices without relying on Android Keystore or automatic cloud sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (errorBanner != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorBanner!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .testTag("backup_error_banner")
                    )
                }
            }
        }

        // Staged Import Preview & Conflict Resolution Card (shown when a backup file is staged)
        if (stagedBackup != null) {
            item {
                StagedImportReviewCard(
                    staged = stagedBackup,
                    resolutions = conflictResolutions,
                    onUpdateResolution = onUpdateConflictResolution,
                    onConfirmCommit = {
                        onCommitStagedBackup { err -> errorBanner = err }
                    },
                    onCancel = onCancelStagedBackup
                )
            }
        }

        // Section 1: Encrypted .dao-backup Export
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Export Encrypted Backup (.dao-backup)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Argon2id + AES-256-GCM authenticated archive including custom service logos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Export Scope",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
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
                                modifier = Modifier.testTag("export_mode_chip_${mode.name}")
                            )
                        }
                    }

                    when (selectedExportMode) {
                        ExportMode.FULL -> {
                            Text(
                                text = "Includes all ${services.size} service(s), ${accounts.size} account(s), ${categories.size} category(s), and referenced service artwork.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ExportMode.SELECTED_SERVICES -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        ExportMode.SELECTED_ACCOUNTS -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                        label = { Text("${acc.account.displayName} (${acc.service.name})") }
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            errorBanner = null
                            showExportPassphraseDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_encrypted_export_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Encrypted .dao-backup")
                    }
                }
            }
        }

        // Section 2: Import Encrypted Backup
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Restore / Import Encrypted Backup",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Validates archive integrity and previews conflicts before writing to your database.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            openBackupDocLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("select_backup_file_to_import_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Select .dao-backup File")
                    }
                }
            }
        }

        // Section 3: Optional Plaintext CSV Export
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TableView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Plaintext CSV Export (Spreadsheet Convenience)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Not a full restorable backup. Does not include custom service logos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showCsvWarningDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_csv_button")
                    ) {
                        Text("Export Plaintext CSV...")
                    }
                }
            }
        }
    }

    if (showExportPassphraseDialog) {
        BackupPassphraseDialog(
            title = "Set Backup Passphrase",
            subtitle = "Choose a strong passphrase (8+ characters) for this backup file. Keep it safe—this passphrase is independent of Android Keystore so you can restore on another device, and losing it makes the backup unrecoverable.",
            confirmLabel = "Encrypt & Choose Destination",
            requireConfirmation = true,
            onDismiss = { showExportPassphraseDialog = false },
            onSubmit = { passphrase ->
                onPrepareEncryptedExport(
                    passphrase,
                    selectedExportMode,
                    selectedAccountIds,
                    selectedServiceIds,
                    selectedCategoryIds,
                    {
                        showExportPassphraseDialog = false
                        createEncryptedDocLauncher.launch("organizer-backup.dao-backup")
                    },
                    { err -> errorBanner = err }
                )
            }
        )
    }

    val importUri = pendingImportUri
    if (importUri != null) {
        BackupPassphraseDialog(
            title = "Enter Backup Passphrase",
            subtitle = "Enter the backup passphrase used when this .dao-backup file was created to decrypt and preview its contents.",
            confirmLabel = "Decrypt & Preview",
            requireConfirmation = false,
            onDismiss = { pendingImportUri = null },
            onSubmit = { passphrase ->
                val targetUri = importUri
                pendingImportUri = null
                onStageBackupFromUri(targetUri, passphrase) { err ->
                    errorBanner = err
                }
            }
        )
    }

    if (showCsvWarningDialog) {
        AlertDialog(
            onDismissRequest = { showCsvWarningDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.csv_warning_title))
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.csv_warning_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCsvWarningDialog = false
                        onPrepareCsvExport(
                            { createCsvDocLauncher.launch("digital-accounts-export.csv") },
                            { err -> errorBanner = err }
                        )
                    },
                    modifier = Modifier.testTag("confirm_csv_export_button")
                ) {
                    Text("I Understand — Export CSV")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCsvWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StagedImportReviewCard(
    staged: StagedBackupPackage,
    resolutions: Map<String, ConflictResolution>,
    onUpdateResolution: (String, ConflictResolution) -> Unit,
    onConfirmCommit: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staged_import_preview_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Import Preview & Conflict Resolution",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Backup created at ${staged.exportedAtIso.ifBlank { "Unknown" }} • Schema v${staged.dataSchemaVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "New records ready to add: ${staged.newServicesCount} service(s), ${staged.newCategoriesCount} category(s), ${staged.newAccountsCount} account(s), ${staged.assetBytesById.size} logo asset(s).",
                style = MaterialTheme.typography.bodyMedium
            )

            if (staged.conflicts.isNotEmpty()) {
                Text(
                    text = "Conflicts & Potential Duplicates (${staged.conflicts.size}) — Default is Skip so existing records are never overwritten silently:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                staged.conflicts.forEach { conflict ->
                    val currentChoice = resolutions[conflict.incomingId] ?: ConflictResolution.SKIP
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = conflict.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${conflict.kind.displayLabel} — ${conflict.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ConflictResolution.entries.forEach { option ->
                                    FilterChip(
                                        selected = currentChoice == option,
                                        onClick = { onUpdateResolution(conflict.incomingId, option) },
                                        label = { Text(option.displayLabel) },
                                        modifier = Modifier.testTag(
                                            "conflict_choice_${conflict.incomingId}_${option.name}"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("cancel_staged_import_button")
                ) {
                    Text("Cancel Import")
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onConfirmCommit,
                    modifier = Modifier.testTag("commit_staged_import_button")
                ) {
                    Text("Apply Import Transactionally")
                }
            }
        }
    }
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        localError = null
                    },
                    label = { Text("Backup Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_passphrase_input")
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = {
                            confirmPassphrase = it
                            localError = null
                        },
                        label = { Text("Confirm Backup Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_confirm_passphrase_input")
                    )
                }
                if (localError != null) {
                    Text(
                        text = localError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        passphrase.length < BackupEngine.MIN_BACKUP_PASSPHRASE_CHARS -> {
                            localError = "Passphrase must be at least ${BackupEngine.MIN_BACKUP_PASSPHRASE_CHARS} characters."
                        }
                        requireConfirmation && passphrase != confirmPassphrase -> {
                            localError = "Passphrases do not match."
                        }
                        else -> onSubmit(passphrase)
                    }
                },
                modifier = Modifier.testTag("submit_backup_passphrase_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
