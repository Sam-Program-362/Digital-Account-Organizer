package com.example.feature.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DestructiveConfirmationDialog
import com.example.core.security.LockState
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun UnlockScreen(
    lockedState: LockState.Locked,
    errorMessage: String?,
    onUnlockWithPassword: (String) -> Unit,
    onRequestBiometricUnlock: () -> Unit,
    onRecoverWithKey: (recoveryCode: String, newPassword: String, onSuccess: () -> Unit) -> Unit,
    onDestructiveReset: () -> Unit,
    onClearError: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showRecoveryDialog by rememberSaveable { mutableStateOf(false) }
    var showDestructiveResetDialog by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("unlock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.unlock_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.unlock_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (errorMessage != null) onClearError()
                        },
                        label = { Text("Master Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_password_input")
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("unlock_error_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val current = password
                            password = ""
                            onUnlockWithPassword(current)
                        },
                        enabled = password.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("unlock_submit_button")
                    ) {
                        Text("Unlock Organizer")
                    }

                    if (lockedState.isBiometricEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onRequestBiometricUnlock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("unlock_biometric_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Unlock"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock with Strong Biometrics")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (lockedState.hasRecoveryEnvelope) {
                            TextButton(
                                onClick = { showRecoveryDialog = true },
                                modifier = Modifier.testTag("open_recovery_key_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Use Recovery Key")
                            }
                        }
                        TextButton(
                            onClick = { showDestructiveResetDialog = true },
                            modifier = Modifier.testTag("open_destructive_reset_button")
                        ) {
                            Text(
                                text = "Reset Organizer",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRecoveryDialog) {
        RecoveryUnlockDialog(
            errorMessage = errorMessage,
            onDismiss = {
                onClearError()
                showRecoveryDialog = false
            },
            onSubmit = { recoveryCode, newPassword ->
                onRecoverWithKey(recoveryCode, newPassword) {
                    showRecoveryDialog = false
                }
            }
        )
    }

    if (showDestructiveResetDialog) {
        DestructiveConfirmationDialog(
            title = "Permanently Erase Encrypted Organizer?",
            message = "Because Digital Account Organizer has no cloud server, a forgotten master password without a saved recovery key cannot be recovered. Resetting will permanently delete the encrypted local database and start an empty organizer.",
            confirmLabel = "Erase All Data & Reset",
            onConfirm = {
                showDestructiveResetDialog = false
                onDestructiveReset()
            },
            onDismiss = { showDestructiveResetDialog = false }
        )
    }
}

@Composable
private fun RecoveryUnlockDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (recoveryCode: String, newPassword: String) -> Unit
) {
    var recoveryCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recover with Recovery Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your 55-character Base32 recovery key and choose a new master password (minimum 12 characters). Your database records will remain intact.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = recoveryCode,
                    onValueChange = {
                        recoveryCode = it
                        localError = null
                    },
                    label = { Text("Recovery Key") },
                    placeholder = { Text("XXXXXXX-XXXXXXX-...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recovery_key_input")
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        localError = null
                    },
                    label = { Text("New Master Password (12+ chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recovery_new_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text("Confirm New Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recovery_confirm_password_input")
                )
                val activeError = localError ?: errorMessage
                if (activeError != null) {
                    Text(
                        text = activeError,
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
                        recoveryCode.isBlank() -> localError = "Please enter your recovery key."
                        newPassword.length < 12 -> localError = "New master password must be at least 12 characters."
                        newPassword != confirmPassword -> localError = "Passwords do not match."
                        else -> onSubmit(recoveryCode, newPassword)
                    }
                },
                modifier = Modifier.testTag("submit_recovery_reset_button")
            ) {
                Text("Recover & Reset")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Modal displayed once when enabling App Lock or rotating recovery credentials.
 * Requires explicit confirmation that the user saved the recovery key outside the device.
 */
@Composable
fun OneTimeRecoveryKeyDialog(
    recoveryCode: String,
    onConfirmedSaved: () -> Unit
) {
    var hasConfirmedSaved by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* Block accidental dismissal until confirmed */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Your Recovery Key")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This independent 256-bit recovery key is shown only once and is never stored in plaintext. Write it down or store it in a safe place separate from this device. There is no server reset.",
                    style = MaterialTheme.typography.bodySmall
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = recoveryCode,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .testTag("one_time_recovery_code_text")
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = hasConfirmedSaved,
                        onCheckedChange = { hasConfirmedSaved = it },
                        modifier = Modifier.testTag("confirm_saved_recovery_key_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I have saved this recovery key in a safe place outside this device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmedSaved,
                enabled = hasConfirmedSaved,
                modifier = Modifier.testTag("acknowledge_recovery_key_button")
            ) {
                Text("Done")
            }
        }
    )
}
