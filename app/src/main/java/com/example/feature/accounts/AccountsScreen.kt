package com.example.feature.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.AccountFilterState
import com.example.app.AccountSortOrder
import com.example.core.designsystem.AccountAvatarFrame
import com.example.core.designsystem.AccountStatusBadge
import com.example.core.designsystem.CategoryBadgeChip
import com.example.core.designsystem.DestructiveConfirmationDialog
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.FormSectionCard
import com.example.core.designsystem.FullScreenSubpageScaffold
import com.example.core.designsystem.PasswordManagerFieldEditor
import com.example.core.designsystem.ProtectedBackupCodesVaultCard
import com.example.core.designsystem.ProtectedVaultFieldCard
import com.example.core.designsystem.ServiceLogoFrame
import com.example.domain.model.AccountAvatarColor
import com.example.domain.model.AccountRecord
import com.example.domain.model.AccountStatus
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.CategoryWithCount
import com.example.domain.model.DateOfBirthCalculator
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(
    accounts: List<AccountWithDetails>,
    categories: List<CategoryWithCount>,
    filterState: AccountFilterState,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategoryFilter: (String?) -> Unit,
    onSelectStatusFilter: (AccountStatus?) -> Unit,
    onToggleShowArchived: (Boolean) -> Unit,
    onSelectSortOrder: (AccountSortOrder) -> Unit,
    onClearFilters: () -> Unit,
    onAddAccountClick: () -> Unit,
    onEditAccountClick: (AccountWithDetails) -> Unit,
    onToggleArchiveAccount: (String, Boolean) -> Unit,
    onDeleteAccountPermanently: (String) -> Unit,
    onDecryptSecret: (String?) -> String?,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    onCredentialScreenActiveChange: (Boolean) -> Unit = {}
) {
    var viewingAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountWithDetails?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val viewingAccount = remember(accounts, viewingAccountId) {
        accounts.firstOrNull { it.account.id == viewingAccountId }
    }

    val isCredentialPageActive = viewingAccount != null
    LaunchedEffect(isCredentialPageActive) {
        onCredentialScreenActiveChange(isCredentialPageActive)
    }
    DisposableEffect(Unit) {
        onDispose {
            onCredentialScreenActiveChange(false)
        }
    }

    AnimatedContent(
        targetState = viewingAccount,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "accounts_page_transition"
    ) { activeDetailAccount ->
        if (activeDetailAccount != null) {
            AccountDetailPage(
                item = activeDetailAccount,
                onDecryptSecret = onDecryptSecret,
                onRequestVerifiedAction = onRequestVerifiedAction,
                onBack = { viewingAccountId = null },
                onEdit = {
                    val target = activeDetailAccount
                    viewingAccountId = null
                    onEditAccountClick(target)
                },
                onToggleArchive = {
                    onToggleArchiveAccount(
                        activeDetailAccount.account.id,
                        !activeDetailAccount.account.isArchived
                    )
                },
                onDelete = {
                    deletingAccount = activeDetailAccount
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("accounts_screen")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accounts",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Standalone personal & work accounts with encrypted Password & Backup Codes Vault.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onAddAccountClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("add_account_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Account")
                    }
                }

                // Standalone Accounts Search & Sort bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search label, email, phone, username...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filterState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("accounts_search_input")
                    )

                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("accounts_sort_button")
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sort")
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            AccountSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.displayLabel) },
                                    onClick = {
                                        onSelectSortOrder(order)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter chips (Status, Category, Archive — no Service mixing)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterState.showArchived,
                        onClick = { onToggleShowArchived(!filterState.showArchived) },
                        label = { Text("Include Archived") },
                        modifier = Modifier.testTag("filter_archived_chip")
                    )
                    AccountStatus.entries.forEach { status ->
                        FilterChip(
                            selected = filterState.selectedStatus == status,
                            onClick = {
                                onSelectStatusFilter(
                                    if (filterState.selectedStatus == status) null else status
                                )
                            },
                            label = { Text(status.displayLabel) },
                            modifier = Modifier.testTag("filter_status_${status.name}")
                        )
                    }
                    categories.forEach { cat ->
                        FilterChip(
                            selected = filterState.selectedCategoryId == cat.category.id,
                            onClick = {
                                onSelectCategoryFilter(
                                    if (filterState.selectedCategoryId == cat.category.id) {
                                        null
                                    } else {
                                        cat.category.id
                                    }
                                )
                            },
                            label = { Text(cat.category.name) }
                        )
                    }
                    if (
                        filterState.searchQuery.isNotEmpty() ||
                        filterState.selectedCategoryId != null ||
                        filterState.selectedStatus != null ||
                        filterState.showArchived
                    ) {
                        TextButton(
                            onClick = onClearFilters,
                            modifier = Modifier.testTag("clear_filters_button")
                        ) {
                            Text("Reset Filters")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("accounts_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (accounts.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Person,
                                title = "No Standalone Accounts Found",
                                body = "Add your standalone accounts (e.g., example123@gmail.com or example321@example.com) with their encrypted passwords, backup codes, connected phones, and notes.",
                                actionLabel = "Add Account",
                                actionTestTag = "accounts_empty_add_button",
                                onActionClick = onAddAccountClick
                            )
                        }
                    } else {
                        items(accounts, key = { it.account.id }) { item ->
                            StandaloneAccountCard(
                                item = item,
                                onClick = { viewingAccountId = item.account.id },
                                onEdit = { onEditAccountClick(item) },
                                onToggleArchive = {
                                    onToggleArchiveAccount(item.account.id, !item.account.isArchived)
                                },
                                onDelete = { deletingAccount = item }
                            )
                        }
                    }
                }
            }
        }
    }

    val targetDelete = deletingAccount
    if (targetDelete != null) {
        DestructiveConfirmationDialog(
            title = "Delete Account '${targetDelete.account.displayName}'?",
            message = "This will permanently delete this standalone account and its encrypted password and backup codes. Any Service Account entries linked to it will remain, with their link cleared.",
            confirmLabel = "Delete Permanently",
            onConfirm = {
                if (viewingAccountId == targetDelete.account.id) {
                    viewingAccountId = null
                }
                onDeleteAccountPermanently(targetDelete.account.id)
                deletingAccount = null
            },
            onDismiss = { deletingAccount = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandaloneAccountCard(
    item: AccountWithDetails,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("account_card_${item.account.displayName}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccountAvatarFrame(
                        displayName = item.account.displayName,
                        identifier = item.account.email ?: item.account.username,
                        email = item.account.email,
                        avatarColorHex = item.account.avatarColorHex,
                        size = 46.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Slightly reduced Account Label size for visual hierarchy
                        Text(
                            text = item.account.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Increased Account Address size for clear prominence
                        val primaryId = item.account.email ?: item.account.username
                        if (!primaryId.isNullOrBlank()) {
                            Text(
                                text = primaryId,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!item.account.email.isNullOrBlank() && !item.account.username.isNullOrBlank()) {
                            Text(
                                text = "@${item.account.username}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${item.account.displayName}")
                    }
                    IconButton(onClick = onToggleArchive) {
                        Icon(
                            imageVector = if (item.account.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (item.account.isArchived) "Unarchive" else "Archive"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete ${item.account.displayName}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AccountStatusBadge(
                    status = item.account.status,
                    isArchived = item.account.isArchived
                )
                item.category?.let { cat ->
                    CategoryBadgeChip(categoryName = cat.name)
                }
                if (item.account.hasPassword) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Password Saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (item.account.hasBackupCodes) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Backup Codes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (item.account.phoneNumbers.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${item.account.phoneNumbers.size} Phone(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (item.linkedServices.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${item.linkedServices.size} Linked Service(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (!item.account.purpose.isNullOrBlank()) {
                Text(
                    text = item.account.purpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Full-screen Standalone Account Detail Page with one-tap Quick Copy on all non-secret fields
 * and strict Password Manager verification before revealing or copying Passwords, Backup Codes, and Notes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountDetailPage(
    item: AccountWithDetails,
    onDecryptSecret: (String?) -> String?,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }
    var lastCopiedFieldLabel by remember { mutableStateOf<String?>(null) }

    val copyToClipboard: (label: String, value: String) -> Unit = { label, value ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        lastCopiedFieldLabel = label
    }

    FullScreenSubpageScaffold(
        title = item.account.displayName,
        subtitle = item.account.primaryIdentifier,
        onBack = onBack,
        backButtonTestTag = "account_detail_back_button",
        actions = {
            OutlinedButton(
                onClick = onToggleArchive,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (item.account.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (item.account.isArchived) "Unarchive" else "Archive",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("detail_edit_account_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit", style = MaterialTheme.typography.labelMedium)
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("account_detail_page"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (lastCopiedFieldLabel != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_copy_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Copied \"$lastCopiedFieldLabel\" to clipboard",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(onClick = { lastCopiedFieldLabel = null }) {
                                Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Hero Header & Quick-Copy Identity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AccountAvatarFrame(
                                displayName = item.account.displayName,
                                identifier = item.account.email ?: item.account.username,
                                email = item.account.email,
                                avatarColorHex = item.account.avatarColorHex,
                                size = 56.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = item.account.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        copyToClipboard("Account Name", item.account.displayName)
                                    }
                                )
                                Text(
                                    text = item.account.primaryIdentifier,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        copyToClipboard("Account Address", item.account.primaryIdentifier)
                                    }
                                )
                                Text(
                                    text = "Tap any field below to quick-copy to clipboard",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AccountStatusBadge(
                                status = item.account.status,
                                isArchived = item.account.isArchived
                            )
                            item.category?.let { CategoryBadgeChip(categoryName = it.name) }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        CopyableDetailFieldRow(
                            label = "Account Name / Label",
                            displayValue = item.account.displayName,
                            copyValue = item.account.displayName,
                            isCopied = lastCopiedFieldLabel == "Account Name / Label",
                            onCopy = { copyToClipboard("Account Name / Label", item.account.displayName) },
                            testTag = "detail_copy_account_name"
                        )

                        CopyableDetailFieldRow(
                            label = "Account Address (Email)",
                            displayValue = item.account.email,
                            copyValue = item.account.email,
                            monospace = true,
                            isCopied = lastCopiedFieldLabel == "Account Address (Email)",
                            onCopy = { item.account.email?.let { copyToClipboard("Account Address (Email)", it) } },
                            testTag = "detail_copy_email"
                        )

                        CopyableDetailFieldRow(
                            label = "Username / Handle",
                            displayValue = item.account.username,
                            copyValue = item.account.username,
                            monospace = true,
                            isCopied = lastCopiedFieldLabel == "Username / Handle",
                            onCopy = { item.account.username?.let { copyToClipboard("Username / Handle", it) } },
                            testTag = "detail_copy_username"
                        )

                        val dobRaw = item.account.dateOfBirth
                        if (!dobRaw.isNullOrBlank()) {
                            val ageYears = remember(dobRaw) { item.account.calculateAgeYears() }
                            val dobDisplay = if (ageYears != null) {
                                "$dobRaw  •  Current Age: $ageYears years old"
                            } else {
                                dobRaw
                            }
                            CopyableDetailFieldRow(
                                label = "Date of Birth & Calculated Age",
                                displayValue = dobDisplay,
                                copyValue = dobRaw,
                                monospace = true,
                                isCopied = lastCopiedFieldLabel == "Date of Birth",
                                onCopy = { copyToClipboard("Date of Birth", dobRaw) },
                                testTag = "detail_copy_dob"
                            )
                        }

                        CopyableDetailFieldRow(
                            label = "Purpose / Role",
                            displayValue = item.account.purpose,
                            copyValue = item.account.purpose,
                            isCopied = lastCopiedFieldLabel == "Purpose / Role",
                            onCopy = { item.account.purpose?.let { copyToClipboard("Purpose / Role", it) } },
                            testTag = "detail_copy_purpose"
                        )
                    }
                }
            }

            // Connected Phone Numbers & Separate Recovery Section
            if (
                item.account.phoneNumbers.isNotEmpty() ||
                !item.account.recoveryEmailReference.isNullOrBlank() ||
                !item.account.recoveryPhoneReference.isNullOrBlank()
            ) {
                item {
                    FormSectionCard(
                        title = "Connected Phones & Recovery Details",
                        subtitle = "Connected account numbers are kept separate from the Recovery Phone. Tap any item to quick copy.",
                        icon = Icons.Default.Phone
                    ) {
                        if (item.account.phoneNumbers.isNotEmpty()) {
                            Text(
                                text = "Connected Account Phone Numbers (${item.account.phoneNumbers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            item.account.phoneNumbers.forEachIndexed { idx, phone ->
                                val phoneLabel = if (item.account.phoneNumbers.size == 1) {
                                    "Connected Phone Number"
                                } else {
                                    "Connected Phone #${idx + 1}"
                                }
                                CopyableDetailFieldRow(
                                    label = phoneLabel,
                                    displayValue = phone,
                                    copyValue = phone,
                                    monospace = true,
                                    isCopied = lastCopiedFieldLabel == phoneLabel,
                                    onCopy = { copyToClipboard(phoneLabel, phone) },
                                    testTag = "detail_copy_connected_phone_$idx"
                                )
                            }
                        }

                        if (
                            item.account.phoneNumbers.isNotEmpty() &&
                            (!item.account.recoveryEmailReference.isNullOrBlank() ||
                                !item.account.recoveryPhoneReference.isNullOrBlank())
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        }

                        CopyableDetailFieldRow(
                            label = "Recovery Account / Email",
                            displayValue = item.account.recoveryEmailReference,
                            copyValue = item.account.recoveryEmailReference,
                            monospace = true,
                            isCopied = lastCopiedFieldLabel == "Recovery Account / Email",
                            onCopy = {
                                item.account.recoveryEmailReference?.let {
                                    copyToClipboard("Recovery Account / Email", it)
                                }
                            },
                            testTag = "detail_copy_recovery_email"
                        )

                        CopyableDetailFieldRow(
                            label = "Recovery Phone Number",
                            displayValue = item.account.recoveryPhoneReference,
                            copyValue = item.account.recoveryPhoneReference,
                            monospace = true,
                            isCopied = lastCopiedFieldLabel == "Recovery Phone Number",
                            onCopy = {
                                item.account.recoveryPhoneReference?.let {
                                    copyToClipboard("Recovery Phone Number", it)
                                }
                            },
                            testTag = "detail_copy_recovery_phone"
                        )
                    }
                }
            }

            // Password Manager & Backup Codes Vault Section
            item {
                FormSectionCard(
                    title = "Password Manager & Backup Codes Vault",
                    subtitle = "Protected by AES-256-GCM + SQLCipher. Both Show and Copy require verification.",
                    icon = Icons.Default.Lock
                ) {
                    if (!item.account.hasPassword && !item.account.hasBackupCodes && !item.account.hasNotes) {
                        Text(
                            text = "No password, backup codes, or protected notes saved for this account yet. Tap Edit to store encrypted credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ProtectedVaultFieldCard(
                            label = "Account Password",
                            hasValue = item.account.hasPassword,
                            onDecryptValue = { onDecryptSecret(item.account.encryptedPassword) },
                            onRequestVerifiedAction = onRequestVerifiedAction,
                            testTagPrefix = "account_password_${item.account.id}",
                            isMonospace = true
                        )

                        ProtectedBackupCodesVaultCard(
                            hasBackupCodes = item.account.hasBackupCodes,
                            onDecryptCodes = { onDecryptSecret(item.account.encryptedBackupCodes) },
                            onRequestVerifiedAction = onRequestVerifiedAction,
                            testTagPrefix = "account_backup_codes_${item.account.id}"
                        )

                        ProtectedVaultFieldCard(
                            label = "Protected Notes",
                            hasValue = item.account.hasNotes,
                            onDecryptValue = { item.account.notes },
                            onRequestVerifiedAction = onRequestVerifiedAction,
                            testTagPrefix = "account_notes_${item.account.id}",
                            isMonospace = false
                        )
                    }
                }
            }

            // Linked Services Section
            item {
                FormSectionCard(
                    title = "Linked Services (${item.linkedServices.size})",
                    subtitle = "Services that use this standalone Account",
                    icon = Icons.Default.Link
                ) {
                    if (item.linkedServices.isEmpty()) {
                        Text(
                            text = "No Services are linked to this standalone account yet. Open the Services tab to link this account to any service.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.linkedServices.forEach { linked ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ServiceLogoFrame(
                                            serviceName = linked.service.name,
                                            asset = linked.serviceAsset,
                                            size = 38.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = linked.service.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${linked.entryLabel} • ${linked.status.displayLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        AccountStatusBadge(status = linked.status)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Record Timestamps & Danger Zone
            item {
                FormSectionCard(
                    title = "Record Activity",
                    icon = Icons.Default.Shield
                ) {
                    DetailFieldRow(
                        "Created",
                        dateFormatter.format(Date(item.account.createdAtEpochMillis))
                    )
                    DetailFieldRow(
                        "Last Updated",
                        dateFormatter.format(Date(item.account.updatedAtEpochMillis))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Standalone Account")
                    }
                }
            }
        }
    }
}

/**
 * Interactive detail row that copies its value to the clipboard when clicked.
 */
@Composable
private fun CopyableDetailFieldRow(
    label: String,
    displayValue: String?,
    copyValue: String?,
    monospace: Boolean = false,
    isCopied: Boolean = false,
    onCopy: () -> Unit,
    testTag: String
) {
    if (displayValue.isNullOrBlank() || copyValue.isNullOrBlank()) return
    Surface(
        onClick = onCopy,
        shape = RoundedCornerShape(10.dp),
        color = if (isCopied) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isCopied) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (monospace) JetBrainsMonoFontFamily else null,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (isCopied) "Copied" else "Copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DetailFieldRow(label: String, value: String?, monospace: Boolean = false) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) JetBrainsMonoFontFamily else null
        )
    }
}

/**
 * Full-screen Standalone Account Add / Edit Page.
 * Supports:
 * - Single-letter avatar preview (first letter of Email Address) + custom Profile Color Palette
 * - Date of Birth (with DatePicker + calculated current age preview)
 * - Multiple Connected Phone Numbers (kept separate from single Recovery Phone Number)
 * - Encrypted Password Manager + Encrypted Account Backup/Recovery Codes (both blank-on-edit)
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorDialog(
    initialAccount: AccountWithDetails?,
    categories: List<CategoryWithCount>,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        displayName: String,
        email: String?,
        username: String?,
        password: String?,
        clearSavedPassword: Boolean,
        categoryId: String?,
        purpose: String?,
        recoveryEmailReference: String?,
        recoveryPhoneReference: String?,
        notes: String?,
        status: AccountStatus,
        phoneNumbers: List<String>,
        dateOfBirth: String?,
        backupCodes: String?,
        clearSavedBackupCodes: Boolean,
        avatarColorHex: String?,
        onError: (String) -> Unit
    ) -> Unit
) {
    val hasExistingSavedPassword = initialAccount?.account?.hasPassword == true
    val hasExistingSavedBackupCodes = initialAccount?.account?.hasBackupCodes == true

    var displayName by rememberSaveable { mutableStateOf(initialAccount?.account?.displayName.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(initialAccount?.account?.email.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(initialAccount?.account?.username.orEmpty()) }
    var avatarColorHex by rememberSaveable { mutableStateOf(initialAccount?.account?.avatarColorHex) }
    var dateOfBirth by rememberSaveable { mutableStateOf(initialAccount?.account?.dateOfBirth.orEmpty()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // Connected account phone numbers (multiple allowed, separate from recoveryPhone)
    var connectedPhoneNumbers by rememberSaveable {
        mutableStateOf(initialAccount?.account?.phoneNumbers ?: emptyList())
    }
    var pendingNewPhoneInput by rememberSaveable { mutableStateOf("") }

    // Security rule: NEVER load or pre-fill an existing saved password or backup codes into edit fields
    var password by rememberSaveable { mutableStateOf("") }
    var clearSavedPassword by rememberSaveable { mutableStateOf(false) }

    var backupCodesInput by rememberSaveable { mutableStateOf("") }
    var clearSavedBackupCodes by rememberSaveable { mutableStateOf(false) }

    var selectedCategoryId by rememberSaveable { mutableStateOf(initialAccount?.account?.categoryId) }
    var purpose by rememberSaveable { mutableStateOf(initialAccount?.account?.purpose.orEmpty()) }
    var recoveryEmail by rememberSaveable {
        mutableStateOf(initialAccount?.account?.recoveryEmailReference.orEmpty())
    }
    var recoveryPhone by rememberSaveable {
        mutableStateOf(initialAccount?.account?.recoveryPhoneReference.orEmpty())
    }
    var notes by rememberSaveable { mutableStateOf(initialAccount?.account?.notes.orEmpty()) }
    var statusName by rememberSaveable {
        mutableStateOf((initialAccount?.account?.status ?: AccountStatus.ACTIVE).name)
    }
    val status = remember(statusName) {
        AccountStatus.entries.firstOrNull { it.name == statusName } ?: AccountStatus.ACTIVE
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val calculatedAgePreview = remember(dateOfBirth) {
        DateOfBirthCalculator.calculateAgeYears(dateOfBirth)
    }

    val triggerSave: () -> Unit = {
        val finalPhones = buildList {
            addAll(connectedPhoneNumbers)
            val extra = pendingNewPhoneInput.trim()
            if (extra.isNotEmpty() && extra !in connectedPhoneNumbers) {
                add(extra)
            }
        }
        onSave(
            initialAccount?.account?.id,
            displayName,
            email.takeIf { it.isNotBlank() },
            username.takeIf { it.isNotBlank() },
            password.takeIf { it.isNotBlank() },
            clearSavedPassword,
            selectedCategoryId,
            purpose.takeIf { it.isNotBlank() },
            recoveryEmail.takeIf { it.isNotBlank() },
            recoveryPhone.takeIf { it.isNotBlank() },
            notes.takeIf { it.isNotBlank() },
            status,
            finalPhones,
            dateOfBirth.trim().takeIf { it.isNotEmpty() },
            backupCodesInput.takeIf { it.isNotBlank() },
            clearSavedBackupCodes,
            avatarColorHex
        ) { err ->
            errorMessage = err
        }
    }

    if (showDatePickerDialog) {
        val initialMillis = remember(dateOfBirth) {
            DateOfBirthCalculator.parseDate(dateOfBirth)
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val pickedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            dateOfBirth = pickedDate.toString()
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    FullScreenSubpageScaffold(
        title = if (initialAccount == null) "Add Standalone Account" else "Edit Standalone Account",
        subtitle = if (initialAccount == null) {
            "Create a personal or work account with encrypted password & backup codes"
        } else {
            "Updating ${initialAccount.account.displayName}"
        },
        onBack = onDismiss,
        backButtonTestTag = "account_editor_back_button",
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(
                onClick = triggerSave,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("save_account_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save Account")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage != null) {
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

            // 1. Account Identity & Single-Letter Avatar Personalization
            FormSectionCard(
                title = "Account Identity & Logo Personalization",
                subtitle = "Logo displays the single first letter of your email address with your chosen profile color",
                icon = Icons.Default.Palette
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AccountAvatarFrame(
                        displayName = displayName.ifBlank { "Account" },
                        identifier = email.ifBlank { username },
                        email = email,
                        avatarColorHex = avatarColorHex,
                        size = 56.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val previewLetter = remember(email, username, displayName) {
                            AccountRecord.computeSingleAvatarLetter(
                                email = email,
                                identifier = username,
                                displayName = displayName
                            )
                        }
                        Text(
                            text = "Logo Letter: \"$previewLetter\" (1st letter of Email)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Select a profile color below to personalize this standalone account badge.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = avatarColorHex == null,
                        onClick = { avatarColorHex = null },
                        label = { Text("Theme Default") },
                        modifier = Modifier.testTag("avatar_color_default")
                    )
                    AccountAvatarColor.entries.forEach { option ->
                        val isSelected = avatarColorHex.equals(option.storageHex, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { avatarColorHex = option.storageHex },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(option.contentHex))
                                        .border(1.dp, Color(option.containerHex), CircleShape)
                                )
                            },
                            label = { Text(option.displayLabel) },
                            modifier = Modifier.testTag("avatar_color_${option.name}")
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        errorMessage = null
                    },
                    label = { Text("Account Label * (e.g., Personal Gmail, Work Email)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_label_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Email Address (e.g., example123@gmail.com)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_email_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text("Username / Handle (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_username_input")
                )

                // Date of Birth & Calculated Current Age
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it },
                        label = { Text("Date of Birth (YYYY-MM-DD)") },
                        placeholder = { Text("e.g., 1998-05-21") },
                        leadingIcon = {
                            Icon(Icons.Default.Cake, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (dateOfBirth.isNotEmpty()) {
                                IconButton(onClick = { dateOfBirth = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear date of birth")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("account_dob_input")
                    )

                    FilledTonalButton(
                        onClick = { showDatePickerDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("account_dob_picker_button")
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Date")
                    }
                }

                if (dateOfBirth.isNotBlank()) {
                    Text(
                        text = if (calculatedAgePreview != null) {
                            "Calculated Current Age: $calculatedAgePreview years old"
                        } else {
                            "Use format YYYY-MM-DD (e.g. 1998-05-21) to auto-calculate current age"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (calculatedAgePreview != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("account_calculated_age_text")
                    )
                }
            }

            // 2. Multiple Connected Phone Numbers (Kept Separate from Recovery Phone)
            FormSectionCard(
                title = "Connected Account Phone Numbers",
                subtitle = "Add one or multiple phone numbers connected to this account (separate from Recovery Phone)",
                icon = Icons.Default.Phone
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pendingNewPhoneInput,
                        onValueChange = { pendingNewPhoneInput = it },
                        label = { Text("Add Connected Phone Number") },
                        placeholder = { Text("e.g., +1-555-0199") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("account_connected_phone_input")
                    )
                    FilledTonalButton(
                        onClick = {
                            val trimmed = pendingNewPhoneInput.trim()
                            if (trimmed.isNotEmpty() && trimmed !in connectedPhoneNumbers) {
                                connectedPhoneNumbers = connectedPhoneNumbers + trimmed
                                pendingNewPhoneInput = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("account_add_connected_phone_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                if (connectedPhoneNumbers.isEmpty()) {
                    Text(
                        text = "No connected phone numbers added yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        connectedPhoneNumbers.forEachIndexed { idx, phone ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connected_phone_row_$idx")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = phone,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = JetBrainsMonoFontFamily
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            connectedPhoneNumbers = connectedPhoneNumbers.filterIndexed { i, _ -> i != idx }
                                        },
                                        modifier = Modifier.testTag("remove_connected_phone_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove $phone",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Encrypted Password Manager & Encrypted Backup Codes
            FormSectionCard(
                title = "Encrypted Password & Backup Codes Vault",
                subtitle = "Protected by AES-256-GCM + SQLCipher. Existing values stay hidden when editing.",
                icon = Icons.Default.Key
            ) {
                PasswordManagerFieldEditor(
                    password = password,
                    onPasswordChange = { password = it },
                    label = "Account Password (Encrypted)",
                    isOptional = true,
                    hasExistingSavedPassword = hasExistingSavedPassword,
                    clearSavedPassword = clearSavedPassword,
                    onClearSavedPasswordChange = { clearSavedPassword = it },
                    testTag = "account_password_input"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    text = "Account Backup / Recovery Codes (Encrypted)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (hasExistingSavedBackupCodes) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (clearSavedBackupCodes) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (clearSavedBackupCodes) {
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
                                    imageVector = if (clearSavedBackupCodes) Icons.Default.Close else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (clearSavedBackupCodes) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = when {
                                        clearSavedBackupCodes -> "Saved backup codes will be removed when you save"
                                        backupCodesInput.isNotEmpty() -> "Entering new backup codes to replace saved codes"
                                        else -> "Saved • enter new backup codes to replace"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            FilterChip(
                                selected = clearSavedBackupCodes,
                                onClick = {
                                    clearSavedBackupCodes = !clearSavedBackupCodes
                                    if (clearSavedBackupCodes) {
                                        backupCodesInput = ""
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (clearSavedBackupCodes) "Keep Saved" else "Clear Codes",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("account_backup_codes_clear_chip")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = backupCodesInput,
                    onValueChange = {
                        if (clearSavedBackupCodes && it.isNotEmpty()) {
                            clearSavedBackupCodes = false
                        }
                        backupCodesInput = it
                    },
                    label = {
                        Text(
                            if (hasExistingSavedBackupCodes) {
                                "New Backup / Recovery Codes (leave blank to keep saved)"
                            } else {
                                "Backup / Recovery Codes (One per line or comma-separated)"
                            }
                        )
                    },
                    placeholder = {
                        Text("e.g.\n1948-2049\n8392-1042\n7520-9914")
                    },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_backup_codes_input")
                )
            }

            // 4. Classification & Status
            FormSectionCard(
                title = "Classification & Status",
                subtitle = "Organize by purpose, status, and optional category",
                icon = Icons.Default.Folder
            ) {
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose / Role (e.g., Primary Identity, Banking)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_purpose_input")
                )

                Text(
                    text = "Account Status",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccountStatus.entries.forEach { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { statusName = option.name },
                            label = { Text(option.displayLabel) },
                            modifier = Modifier.testTag("account_status_${option.name}")
                        )
                    }
                }

                if (categories.isNotEmpty()) {
                    Text(
                        text = "Category (Optional)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Uncategorized") }
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.category.id,
                                onClick = { selectedCategoryId = cat.category.id },
                                label = { Text(cat.category.name) }
                            )
                        }
                    }
                }
            }

            // 5. Recovery References & Protected Notes
            FormSectionCard(
                title = "Recovery Account, Recovery Phone & Protected Notes",
                subtitle = "Recovery phone is separate from connected account phone numbers",
                icon = Icons.Default.Lock
            ) {
                OutlinedTextField(
                    value = recoveryEmail,
                    onValueChange = { recoveryEmail = it },
                    label = { Text("Recovery Email / Account Reference") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_recovery_email_input")
                )

                OutlinedTextField(
                    value = recoveryPhone,
                    onValueChange = { recoveryPhone = it },
                    label = { Text("Recovery Phone Number (Single, Separate from Connected Phones)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_recovery_phone_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Protected Notes (Verified on view & copy)") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_notes_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = triggerSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Account")
                }
            }
        }
    }
}
