package com.example.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.security.AutoLockTimeout
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.LockState
import com.example.domain.model.AppColorPalette
import com.example.domain.model.ThemePreference
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    unlockedState: LockState.Unlocked,
    themePreference: ThemePreference,
    colorPalette: AppColorPalette,
    restrictScreenshotsEntireApp: Boolean,
    isBiometricHardwareAvailable: Boolean,
    onSelectThemePreference: (ThemePreference) -> Unit,
    onSelectColorPalette: (AppColorPalette) -> Unit,
    onSetRestrictScreenshotsEntireApp: (Boolean) -> Unit,
    onSetPasswordManagerLockEnabled: (Boolean) -> Unit,
    onSetUseMasterPasswordForVault: (Boolean) -> Unit,
    onEnableAppLock: (masterPassword: String, timeout: AutoLockTimeout, onError: (String) -> Unit) -> Unit,
    onChangeMasterPassword: (currentPassword: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDisableAppLock: (currentPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onUpdateAutoLockTimeout: (AutoLockTimeout) -> Unit,
    onToggleBiometricUnlock: (Boolean) -> Unit,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    onGetDecryptedRecoveryCode: () -> String?,
    onDownloadRecoveryCodeOnce: () -> Unit,
    onLockNow: () -> Unit,
    onCredentialModalActiveChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showEnableLockDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDisableLockDialog by remember { mutableStateOf(false) }
    var revealedRecoveryCode by remember { mutableStateOf<String?>(null) }

    val isCredentialModalOpen =
        revealedRecoveryCode != null ||
            showEnableLockDialog ||
            showChangePasswordDialog ||
            showDisableLockDialog

    LaunchedEffect(isCredentialModalOpen) {
        onCredentialModalActiveChange(isCredentialModalOpen)
    }

    DisposableEffect(Unit) {
        onDispose {
            onCredentialModalActiveChange(false)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Settings & Security",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure appearance mode, interface color themes, screenshot protection, Password Manager verification, and Master Password lock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Appearance Mode & Interface Color Themes
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
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Appearance & Interface Themes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Select Light Mode, Dark Mode, or System Default independently from your interface color theme.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Display Mode",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = themePreference == pref,
                                onClick = { onSelectThemePreference(pref) },
                                label = { Text(pref.displayLabel) },
                                modifier = Modifier.testTag("theme_mode_${pref.name}")
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Interface Color Theme",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppColorPalette.entries.forEach { palette ->
                            val selected = colorPalette == palette
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelectColorPalette(palette) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("color_palette_${palette.name}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(palette.previewHex))
                                )
                                Column {
                                    Text(
                                        text = palette.displayLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = palette.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Password Manager Verification Lock
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
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Password Manager Verification Lock",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Browsing accounts and services never asks for verification. Verification is only required when viewing, copying, or editing a password or its notes (5-minute session window).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = unlockedState.isPasswordManagerLockEnabled,
                            onCheckedChange = onSetPasswordManagerLockEnabled,
                            modifier = Modifier.testTag("password_manager_lock_switch")
                        )
                    }

                    if (unlockedState.isPasswordManagerLockEnabled && unlockedState.isAppLockEnabled) {
                        HorizontalDivider()
                        Text(
                            text = "Verification Method for Viewing Passwords & Notes",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !unlockedState.useMasterPasswordForVault,
                                onClick = { onSetUseMasterPasswordForVault(false) },
                                label = { Text("Device Lock / Fingerprint (Default)") },
                                modifier = Modifier.testTag("vault_method_device_lock")
                            )
                            FilterChip(
                                selected = unlockedState.useMasterPasswordForVault,
                                onClick = { onSetUseMasterPasswordForVault(true) },
                                label = { Text("Master Password") },
                                modifier = Modifier.testTag("vault_method_master_password")
                            )
                        }
                    }
                }
            }
        }

        // 2b. Screen Capture & Recording Protection (FLAG_SECURE)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Restrict Screenshots Across Entire App",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (restrictScreenshotsEntireApp) {
                                    "Enabled — Screenshots and screen recording are blocked on every page in the application."
                                } else {
                                    "Default (Off) — Normal pages allow screenshots & recording. Password Manager and Credential pages are automatically protected."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = restrictScreenshotsEntireApp,
                            onCheckedChange = onSetRestrictScreenshotsEntireApp,
                            modifier = Modifier.testTag("restrict_screenshots_entire_app_switch")
                        )
                    }
                }
            }
        }

        // 3. Master Password App Lock (8+ characters), Fingerprint & Permanent Encrypted Recovery Code
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
                                text = "Master Password App Lock",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (unlockedState.isAppLockEnabled) {
                                    "Enabled — Requires your Master Password (8+ characters) or Fingerprint on app launch."
                                } else {
                                    "Optional — Set a Master Password (minimum ${DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS} characters) to lock the entire app on launch."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!unlockedState.isAppLockEnabled) {
                        Button(
                            onClick = { showEnableLockDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_app_lock_button")
                        ) {
                            Text("Enable Master Password Lock (${DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS}+ chars)")
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showChangePasswordDialog = true },
                                modifier = Modifier.testTag("change_master_password_button")
                            ) {
                                Text("Change Password")
                            }
                            OutlinedButton(
                                onClick = { showDisableLockDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("disable_app_lock_button")
                            ) {
                                Text("Disable Lock")
                            }
                            Button(
                                onClick = onLockNow,
                                modifier = Modifier.testTag("settings_lock_now_button")
                            ) {
                                Text("Lock Now")
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Auto-Lock Timeout",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AutoLockTimeout.entries.forEach { timeout ->
                                FilterChip(
                                    selected = unlockedState.autoLockTimeout == timeout,
                                    onClick = { onUpdateAutoLockTimeout(timeout) },
                                    label = { Text(timeout.displayLabel) }
                                )
                            }
                        }

                        HorizontalDivider()

                        // Fingerprint Verification & Recovery Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Fingerprint Verification & Recovery",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isBiometricHardwareAvailable) {
                                            "Must be enabled here first to allow Fingerprint app unlock, Password Manager verification, and forgotten Master Password recovery."
                                        } else {
                                            "No biometric fingerprint hardware/enrollment detected on this device."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = unlockedState.isBiometricEnabled,
                                onCheckedChange = onToggleBiometricUnlock,
                                enabled = isBiometricHardwareAvailable,
                                modifier = Modifier.testTag("biometric_unlock_switch")
                            )
                        }

                        if (unlockedState.hasStoredRecoveryCode) {
                            HorizontalDivider()

                            Text(
                                text = "Permanent Encrypted Recovery Code",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Your recovery code is kept permanently in AES-256-GCM encrypted form and included in Full Encrypted Backups. You can view it anytime or download it once as a .txt file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onRequestVerifiedAction {
                                            revealedRecoveryCode = onGetDecryptedRecoveryCode()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("view_stored_recovery_code_button")
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("View Code")
                                }

                                OutlinedButton(
                                    onClick = {
                                        onRequestVerifiedAction {
                                            onDownloadRecoveryCodeOnce()
                                        }
                                    },
                                    enabled = !unlockedState.recoveryCodesDownloadedOnce,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("download_recovery_code_file_once_button")
                                ) {
                                    Icon(
                                        imageVector = if (unlockedState.recoveryCodesDownloadedOnce) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.Download
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (unlockedState.recoveryCodesDownloadedOnce) {
                                            "Downloaded (.txt)"
                                        } else {
                                            "Save .txt Once"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Security Architecture Summary
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Encryption & Vault Architecture",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "• Database & Password Encryption: SQLCipher 256-bit AES + per-field AES-256-GCM encryption for all saved passwords.\n" +
                            "• Key Derivation: Argon2id + Android Keystore hardware-backed AES-256-GCM wrapping.\n" +
                            "• Locked Exports: Auto-generated random Export Lock Key required on every .dao-backup import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    revealedRecoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = { revealedRecoveryCode = null },
            title = { Text("Your Encrypted Recovery Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "This recovery code is stored encrypted inside your app vault and can reset your Master Password if forgotten.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = code,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Recovery Code", code))
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Code")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { revealedRecoveryCode = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showEnableLockDialog) {
        EnableAppLockDialog(
            onDismiss = { showEnableLockDialog = false },
            onConfirm = { newPass, timeout, onError ->
                onEnableAppLock(newPass, timeout) { err ->
                    onError(err)
                }
                if (newPass.length >= DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS) {
                    showEnableLockDialog = false
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangeMasterPasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { currentPass, newPass, onError ->
                onChangeMasterPassword(
                    currentPass,
                    newPass,
                    { showChangePasswordDialog = false },
                    onError
                )
            }
        )
    }

    if (showDisableLockDialog) {
        DisableAppLockDialog(
            onDismiss = { showDisableLockDialog = false },
            onConfirm = { currentPass, onError ->
                onDisableAppLock(
                    currentPass,
                    { showDisableLockDialog = false },
                    onError
                )
            }
        )
    }
}

@Composable
private fun EnableAppLockDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String, timeout: AutoLockTimeout, onError: (String) -> Unit) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val minChars = DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable Master Password Lock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose a Master Password of at least $minChars characters. An encrypted recovery code will also be generated.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("New Master Password ($minChars+ chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enable_lock_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enable_lock_confirm_input")
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
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
                        password.length < minChars -> {
                            errorMessage = "Master password must be at least $minChars characters."
                        }
                        password != confirmPassword -> {
                            errorMessage = "Passwords do not match."
                        }
                        else -> {
                            onConfirm(password, AutoLockTimeout.ONE_MINUTE) { err ->
                                errorMessage = err
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("confirm_enable_lock_button")
            ) {
                Text("Enable Lock")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChangeMasterPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (currentPass: String, newPass: String, onError: (String) -> Unit) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val minChars = DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Master Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = null
                    },
                    label = { Text("Current Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("New Master Password ($minChars+ chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm New Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
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
                        newPassword.length < minChars -> {
                            errorMessage = "New master password must be at least $minChars characters."
                        }
                        newPassword != confirmPassword -> {
                            errorMessage = "New passwords do not match."
                        }
                        else -> {
                            onConfirm(currentPassword, newPassword) { err ->
                                errorMessage = err
                            }
                        }
                    }
                }
            ) {
                Text("Update Password")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DisableAppLockDialog(
    onDismiss: () -> Unit,
    onConfirm: (currentPass: String, onError: (String) -> Unit) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable Master Password Lock?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your current Master Password to disable launch lock. Your database and saved passwords remain encrypted at rest.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = null
                    },
                    label = { Text("Current Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("disable_lock_password_input")
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(currentPassword) { err -> errorMessage = err }
                },
                enabled = currentPassword.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_disable_lock_button")
            ) {
                Text("Disable Lock")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
