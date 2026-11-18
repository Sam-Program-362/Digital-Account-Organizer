package com.example.core.designsystem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccountStatus
import com.example.domain.model.PasswordGenerator
import com.example.domain.model.ServiceAssetRecord
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

/**
 * Full-screen sub-page header and container that replaces pop-up AlertDialogs for Detail and Add/Edit screens.
 * Includes automatic BackHandler integration and a refined top header bar.
 */
@Composable
fun FullScreenSubpageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    backButtonTestTag: String = "subpage_back_button",
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag(backButtonTestTag)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate back"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 4.dp),
                        content = actions
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize(),
            content = content
        )
    }
}

/**
 * Clean, elevated section card for grouping fields and details on full-screen pages.
 */
@Composable
fun FormSectionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    trailingHeader: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (trailingHeader != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingHeader()
                }
            }

            content()
        }
    }
}

/**
 * Renders a standalone Account's single-letter monogram avatar inside a rounded badge.
 * Uses ONLY 1 letter: the first letter of the email address (falling back to username/displayName),
 * and applies the user's personalized profile color palette when [avatarColorHex] is set.
 */
@Composable
fun AccountAvatarFrame(
    displayName: String,
    identifier: String?,
    modifier: Modifier = Modifier,
    email: String? = null,
    avatarColorHex: String? = null,
    size: Dp = 46.dp
) {
    val shape = RoundedCornerShape((size.value * 0.28f).dp)
    val singleLetter = remember(displayName, identifier, email) {
        val effectiveEmail = email ?: identifier?.takeIf { it.contains('@') }
        com.example.domain.model.AccountRecord.computeSingleAvatarLetter(
            email = effectiveEmail,
            identifier = identifier,
            displayName = displayName
        )
    }
    val paletteColor = remember(avatarColorHex) {
        com.example.domain.model.AccountAvatarColor.fromHex(avatarColorHex)
    }
    val containerColor = if (paletteColor != null) {
        androidx.compose.ui.graphics.Color(paletteColor.containerHex)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
    }
    val borderColor = if (paletteColor != null) {
        androidx.compose.ui.graphics.Color(paletteColor.contentHex).copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    val textColor = if (paletteColor != null) {
        androidx.compose.ui.graphics.Color(paletteColor.contentHex)
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            )
            .semantics {
                contentDescription = "$displayName account avatar ($singleLetter)"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = singleLetter,
            style = if (size >= 52.dp) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Renders a service's custom logo/icon inside a consistent rounded frame, preserving aspect ratio.
 * Falls back to a safe geometric monogram if no custom asset is attached.
 */
@Composable
fun ServiceLogoFrame(
    serviceName: String,
    asset: ServiceAssetRecord?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val context = LocalContext.current
    val bitmap = remember(asset?.id, asset?.relativePath) {
        asset?.let { record ->
            runCatching {
                val file = File(context.filesDir, record.relativePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else {
                    null
                }
            }.getOrNull()
        }
    }

    val shape = RoundedCornerShape((size.value * 0.26f).dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = shape
            )
            .semantics {
                contentDescription = "$serviceName logo"
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$serviceName custom icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size - 6.dp)
                    .clip(RoundedCornerShape((size.value * 0.2f).dp))
            )
        } else {
            val initials = remember(serviceName) {
                serviceName.trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("") { it.first().uppercaseChar().toString() }
                    .ifEmpty { "S" }
            }
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Google-Password-Manager-style protected row for Password or Notes.
 * Strictly requires verification before EITHER showing (revealing) OR copying to the clipboard.
 * When the field is hidden (`revealedText == null` and `!isVerifiedForCurrentReveal`),
 * both Reveal and Copy trigger [onRequestVerifiedAction].
 */
@Composable
fun ProtectedVaultFieldCard(
    label: String,
    hasValue: Boolean,
    onDecryptValue: () -> String?,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    testTagPrefix: String,
    isMonospace: Boolean = false
) {
    if (!hasValue) return
    val context = LocalContext.current
    var revealedText by remember(testTagPrefix) { mutableStateOf<String?>(null) }
    var isVerifiedForCard by remember(testTagPrefix) { mutableStateOf(false) }
    var copiedFeedback by remember(testTagPrefix) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "$label (Encrypted Vault)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (revealedText != null) {
                                revealedText = null
                                isVerifiedForCard = false
                            } else {
                                onRequestVerifiedAction {
                                    isVerifiedForCard = true
                                    revealedText = onDecryptValue()
                                }
                            }
                        },
                        modifier = Modifier.testTag("${testTagPrefix}_reveal_button")
                    ) {
                        Icon(
                            imageVector = if (revealedText != null) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (revealedText != null) "Hide $label" else "Reveal $label",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val performCopy: () -> Unit = {
                                isVerifiedForCard = true
                                val plain = revealedText ?: onDecryptValue()
                                if (!plain.isNullOrEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText(label, plain))
                                    copiedFeedback = true
                                }
                            }
                            if (isVerifiedForCard && revealedText != null) {
                                performCopy()
                            } else {
                                onRequestVerifiedAction {
                                    performCopy()
                                }
                            }
                        },
                        modifier = Modifier.testTag("${testTagPrefix}_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy $label (requires verification)",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = revealedText ?: "•••••••••••• (Verify to reveal or copy)",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (isMonospace && revealedText != null) JetBrainsMonoFontFamily else null,
                color = if (revealedText != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.testTag("${testTagPrefix}_value_text")
            )

            if (copiedFeedback) {
                Text(
                    text = "Verified & copied $label to clipboard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Password-Manager-verified vault card for Account Backup / Recovery Codes.
 * Encrypted at rest (AES-256-GCM + SQLCipher). Requires verification before revealing or copying.
 * Once verified, renders each individual backup code with a one-tap Quick Copy button plus Copy All.
 */
@Composable
fun ProtectedBackupCodesVaultCard(
    hasBackupCodes: Boolean,
    onDecryptCodes: () -> String?,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    testTagPrefix: String
) {
    if (!hasBackupCodes) return
    val context = LocalContext.current
    var revealedCodes by remember(testTagPrefix) { mutableStateOf<List<String>?>(null) }
    var isVerifiedForCard by remember(testTagPrefix) { mutableStateOf(false) }
    var copiedNotice by remember(testTagPrefix) { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "Backup / Recovery Codes (Encrypted Vault)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (revealedCodes != null) {
                            Text(
                                text = "${revealedCodes!!.size} recovery code(s) • Tap any code to quick copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (revealedCodes != null) {
                                revealedCodes = null
                                isVerifiedForCard = false
                                copiedNotice = null
                            } else {
                                onRequestVerifiedAction {
                                    isVerifiedForCard = true
                                    val raw = onDecryptCodes()
                                    revealedCodes = com.example.domain.model.BackupCodesFormatter.parseCodesList(raw)
                                }
                            }
                        },
                        modifier = Modifier.testTag("${testTagPrefix}_reveal_button")
                    ) {
                        Icon(
                            imageVector = if (revealedCodes != null) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (revealedCodes != null) {
                                "Hide Backup Codes"
                            } else {
                                "Verify & Reveal Backup Codes"
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val performCopyAll: () -> Unit = {
                                isVerifiedForCard = true
                                val codes = revealedCodes ?: com.example.domain.model.BackupCodesFormatter.parseCodesList(onDecryptCodes())
                                if (codes.isNotEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("Backup Codes", codes.joinToString("\n"))
                                    )
                                    copiedNotice = "Copied all ${codes.size} backup codes to clipboard"
                                }
                            }
                            if (isVerifiedForCard && revealedCodes != null) {
                                performCopyAll()
                            } else {
                                onRequestVerifiedAction {
                                    performCopyAll()
                                }
                            }
                        },
                        modifier = Modifier.testTag("${testTagPrefix}_copy_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy All Backup Codes (requires verification)",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            val codes = revealedCodes
            if (codes == null) {
                Text(
                    text = "••••-••••   ••••-••••   ••••-•••• (Verify to view or quick-copy codes)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("${testTagPrefix}_masked_text")
                )
            } else if (codes.isEmpty()) {
                Text(
                    text = "No backup codes found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    codes.forEachIndexed { index, code ->
                        Surface(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Backup Code #${index + 1}", code))
                                copiedNotice = "Copied backup code #${index + 1} ($code) to clipboard"
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("${testTagPrefix}_code_item_$index")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                    Text(
                                        text = code,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Quick copy code #${index + 1}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (copiedNotice != null) {
                Text(
                    text = copiedNotice!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Built-in Password Manager field editor with Show/Hide toggle, Password Generator, Strength badge,
 * and secure edit behavior:
 * - When editing an item with an existing saved password ([hasExistingSavedPassword] == true),
 *   the input starts blank and displays `"Saved • enter password to change to new"` so the existing
 *   password cannot be exposed simply by opening Edit.
 * - Includes an explicit `"Clear saved password"` toggle chip if the user wants to remove the saved password.
 * - Properly weights the helper caption with `Modifier.weight(1f)` so the `"Generate Strong"` button
 *   never wraps vertically or creates empty vertical space.
 */
@Composable
fun PasswordManagerFieldEditor(
    password: String,
    onPasswordChange: (String) -> Unit,
    label: String = "Password (Encrypted)",
    isOptional: Boolean = true,
    hasExistingSavedPassword: Boolean = false,
    clearSavedPassword: Boolean = false,
    onClearSavedPasswordChange: ((Boolean) -> Unit)? = null,
    testTag: String = "password_manager_input"
) {
    var showPassword by remember { mutableStateOf(false) }
    val strengthLabel = remember(password) { PasswordGenerator.estimateStrengthLabel(password) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasExistingSavedPassword) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (clearSavedPassword) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                },
                border = BorderStroke(
                    1.dp,
                    if (clearSavedPassword) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (clearSavedPassword) Icons.Default.Close else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (clearSavedPassword) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                clearSavedPassword -> "Saved password will be removed when you save"
                                password.isNotEmpty() -> "Entering a new password to replace saved password"
                                else -> "Saved • enter password to change to new"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (clearSavedPassword) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.testTag("${testTag}_saved_status_text")
                        )
                    }

                    if (onClearSavedPasswordChange != null) {
                        FilterChip(
                            selected = clearSavedPassword,
                            onClick = {
                                val next = !clearSavedPassword
                                onClearSavedPasswordChange(next)
                                if (next) {
                                    onPasswordChange("")
                                }
                            },
                            label = {
                                Text(
                                    text = if (clearSavedPassword) "Keep Saved" else "Clear Password",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("${testTag}_clear_saved_chip")
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = password,
            onValueChange = { newValue ->
                if (clearSavedPassword && newValue.isNotEmpty()) {
                    onClearSavedPasswordChange?.invoke(false)
                }
                onPasswordChange(newValue)
            },
            label = {
                Text(
                    when {
                        hasExistingSavedPassword -> "New Password (leave blank to keep saved)"
                        isOptional -> "$label (Optional)"
                        else -> "$label *"
                    }
                )
            },
            placeholder = {
                Text(
                    if (hasExistingSavedPassword) {
                        "Saved • enter password to change to new"
                    } else {
                        "Enter or generate a strong password"
                    }
                )
            },
            singleLine = true,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (password.isNotEmpty()) {
                    "Strength: $strengthLabel • Encrypted with AES-256-GCM"
                } else {
                    "Stored encrypted inside SQLCipher + AES-256-GCM"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (password.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            FilledTonalButton(
                onClick = {
                    onClearSavedPasswordChange?.invoke(false)
                    onPasswordChange(PasswordGenerator.generatePassword(length = 16))
                    showPassword = true
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("${testTag}_generate_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Generate Strong",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Accessible status badge that pairs an icon and text label so status is never conveyed by color alone.
 */
@Composable
fun AccountStatusBadge(
    status: AccountStatus,
    isArchived: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (icon, containerColor, contentColor, label) = when {
        isArchived -> Quad(
            Icons.Default.Block,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Archived (${status.displayLabel})"
        )
        status == AccountStatus.ACTIVE -> Quad(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            status.displayLabel
        )
        status == AccountStatus.INACTIVE -> Quad(
            Icons.Default.PauseCircle,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            status.displayLabel
        )
        else -> Quad(
            Icons.Default.Block,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            status.displayLabel
        )
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = "Account status: $label"
        },
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoryBadgeChip(
    categoryName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Category: $categoryName"
        },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OfflinePrivacyBanner(
    noticeText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_privacy_banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Offline Encryption Active",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = noticeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    actionTestTag: String = "empty_state_action_button",
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.testTag(actionTestTag)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun DestructiveConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_destructive_action_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_destructive_action_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
