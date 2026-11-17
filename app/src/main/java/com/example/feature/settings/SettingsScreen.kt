package com.example.feature.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.security.AutoLockTimeout
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.LockState
import com.example.domain.model.ThemePreference

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    unlockedState: LockState.Unlocked,
    themePreference: ThemePreference,
    onSelectThemePreference: (ThemePreference) -> Unit,
    onEnableAppLock: (password: String, timeout: AutoLockTimeout, onError: (String) -> Unit) -> Unit,
    onChangeMasterPassword: (current: String, newPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDisableAppLock: (currentPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onUpdateAutoLockTimeout: (AutoLockTimeout) -> Unit,
    onRequestEnableBiometric: () -> Unit,
    onDisableBiometric: () -> Unit,
    onLockNow: () -> Unit
) {
    var showEnableLockDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showDisableLockDialog by rememberSaveable { mutableStateOf(false) }

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
                    text = "Settings & Protection",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Configure appearance, optional master-password lock, biometric unlock, and privacy settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 1: Theme & Appearance
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
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Appearance & Theme",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = themePreference == pref,
                                onClick = { onSelectThemePreference(pref) },
                                label = { Text(pref.displayLabel) },
                                modifier = Modifier.testTag("theme_chip_${pref.name}")
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Application-Level Lock & Key Hierarchy
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
                            imageVector = if (unlockedState.isAppLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Application Access Protection",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (unlockedState.isAppLockEnabled) {
                                    "Status: Master-Password Lock Enabled (Argon2id + AES-256-GCM)"
                                } else {
                                    "Status: Optional Lock Disabled (Encrypted at rest via Android Keystore)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Your local SQLCipher database is always encrypted with a random 256-bit key. Enabling the optional App Lock wraps that key with an app-specific master password and generates a one-time recovery key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!unlockedState.isAppLockEnabled) {
                        Button(
                            onClick = { showEnableLockDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_app_lock_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Enable Master-Password Lock")
                        }
                    } else {
                        // Auto-lock timeout selector
                        Text(
                            text = "Background Auto-Lock Policy",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AutoLockTimeout.entries.forEach { timeoutOption ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUpdateAutoLockTimeout(timeoutOption) }
                                    .testTag("timeout_option_${timeoutOption.name}")
                            ) {
                                RadioButton(
                                    selected = unlockedState.autoLockTimeout == timeoutOption,
                                    onClick = { onUpdateAutoLockTimeout(timeoutOption) }
                                )
                                Text(
                                    text = timeoutOption.displayLabel,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Biometric toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Strong Biometric Unlock",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Uses Android Keystore CryptoObject. Master password remains fallback.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = unlockedState.isBiometricEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) onRequestEnableBiometric() else onDisableBiometric()
                                },
                                modifier = Modifier.testTag("biometric_unlock_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showChangePasswordDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("change_master_password_button")
                            ) {
                                Text("Change Password")
                            }
                            OutlinedButton(
                                onClick = { showDisableLockDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("disable_app_lock_button")
                            ) {
                                Text("Disable Lock")
                            }
                        }

                        Button(
                            onClick = onLockNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_lock_now_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Lock Organizer Now")
                        }
                    }
                }
            }
        }

        // Section 3: Privacy & Architecture Guarantees
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Privacy & Security Posture",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "• Local-First & Scoped Network Access: All organizer records stay on-device. HTTPS-only network access (cleartext disabled) is used exclusively when you explicitly tap 'Fetch Logo from URL'—never for background telemetry, analytics, or cloud sync.\n" +
                            "• No Service Passwords Stored: This utility organizes account metadata only and never asks for external account passwords, tokens, or TOTP seeds.\n" +
                            "• Auto Backup Excluded: Database, custom service logos, and key envelopes are excluded from Android cloud backup and device-to-device transfer.\n" +
                            "• Recent-Apps Protection: FLAG_SECURE prevents sensitive screenshots and app-switcher previews when App Lock is enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showEnableLockDialog) {
        EnableAppLockDialog(
            initialTimeout = unlockedState.autoLockTimeout,
            onDismiss = { showEnableLockDialog = false },
            onConfirm = { password, timeout, onError ->
                onEnableAppLock(password, timeout) { err ->
                    onError(err)
                }
                if (password.length >= DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS) {
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
    initialTimeout: AutoLockTimeout,
    onDismiss: () -> Unit,
    onConfirm: (password: String, timeout: AutoLockTimeout, onError: (String) -> Unit) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedTimeout by remember { mutableStateOf(initialTimeout) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable Master-Password Lock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Create an app-specific master password (12+ characters, unrelated to Google or any external service). You will also receive a one-time recovery key. If you lose both, your encrypted database cannot be recovered.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("New Master Password (12+ chars)") },
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
                        .testTag("enable_lock_confirm_password_input")
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
                        password.length < DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS -> {
                            errorMessage = "Master password must be at least ${DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS} characters."
                        }
                        password != confirmPassword -> {
                            errorMessage = "Passwords do not match."
                        }
                        else -> {
                            onConfirm(password, selectedTimeout) { err -> errorMessage = err }
                        }
                    }
                },
                modifier = Modifier.testTag("confirm_enable_lock_button")
            ) {
                Text("Enable Lock & Generate Recovery Key")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Master Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Changing your master password rewraps the 256-bit database key with a fresh Argon2id salt without rewriting your database records.",
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
                        .testTag("change_password_current_input")
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("New Master Password (12+ chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_password_new_input")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_password_confirm_input")
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
                        newPassword.length < DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS -> {
                            errorMessage = "New password must be at least ${DatabaseKeyManager.MIN_MASTER_PASSWORD_CHARS} characters."
                        }
                        newPassword != confirmPassword -> {
                            errorMessage = "New passwords do not match."
                        }
                        else -> onConfirm(currentPassword, newPassword) { err -> errorMessage = err }
                    }
                },
                modifier = Modifier.testTag("submit_change_password_button")
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
        title = { Text("Disable Master-Password Lock?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Verify your current master password to switch back to automatic local unlock via Android Keystore. Your SQLCipher database remains encrypted at rest.",
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
