package com.example.feature.accounts

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.app.AccountFilterState
import com.example.app.AccountSortOrder
import com.example.core.designsystem.AccountAvatarFrame
import com.example.core.designsystem.AccountStatusBadge
import com.example.core.designsystem.CategoryBadgeChip
import com.example.core.designsystem.DestructiveConfirmationDialog
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.PasswordManagerFieldEditor
import com.example.core.designsystem.ProtectedVaultFieldCard
import com.example.core.designsystem.ServiceLogoFrame
import com.example.domain.model.AccountStatus
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.CategoryWithCount
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
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
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit
) {
    var viewingAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountWithDetails?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val viewingAccount = remember(accounts, viewingAccountId) {
        accounts.firstOrNull { it.account.id == viewingAccountId }
    }

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
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Standalone personal & work accounts with encrypted Password Manager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onAddAccountClick,
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
                placeholder = { Text("Search label, email, username, purpose...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("accounts_search_input")
            )

            OutlinedButton(
                onClick = { showSortMenu = true },
                modifier = Modifier.testTag("accounts_sort_button")
            ) {
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
                        onSelectStatusFilter(if (filterState.selectedStatus == status) null else status)
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
                            if (filterState.selectedCategoryId == cat.category.id) null else cat.category.id
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
                        body = "Add your standalone accounts (e.g., example123@gmail.com or example321@example.com) with their encrypted passwords and notes. You can later link them to Services.",
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
                        onEdit = {
                            if (item.account.hasPassword || item.account.hasNotes) {
                                onRequestVerifiedAction { onEditAccountClick(item) }
                            } else {
                                onEditAccountClick(item)
                            }
                        },
                        onToggleArchive = {
                            onToggleArchiveAccount(item.account.id, !item.account.isArchived)
                        },
                        onDelete = { deletingAccount = item }
                    )
                }
            }
        }
    }

    if (viewingAccount != null) {
        AccountDetailDialog(
            item = viewingAccount,
            onDecryptSecret = onDecryptSecret,
            onRequestVerifiedAction = onRequestVerifiedAction,
            onDismiss = { viewingAccountId = null },
            onEdit = {
                val target = viewingAccount
                viewingAccountId = null
                if (target.account.hasPassword || target.account.hasNotes) {
                    onRequestVerifiedAction { onEditAccountClick(target) }
                } else {
                    onEditAccountClick(target)
                }
            }
        )
    }

    val targetDelete = deletingAccount
    if (targetDelete != null) {
        DestructiveConfirmationDialog(
            title = "Delete Account '${targetDelete.account.displayName}'?",
            message = "This will permanently delete this standalone account and its encrypted password. Any Service Account entries linked to it will remain, with their link cleared.",
            confirmLabel = "Delete Permanently",
            onConfirm = {
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
                        size = 46.dp
                    )
                    Column {
                        Text(
                            text = item.account.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val primaryId = item.account.email ?: item.account.username
                        if (!primaryId.isNullOrBlank()) {
                            Text(
                                text = primaryId,
                                style = MaterialTheme.typography.bodySmall,
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
                        shape = MaterialTheme.shapes.extraSmall,
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
                if (item.linkedServices.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
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

@Composable
private fun AccountDetailDialog(
    item: AccountWithDetails,
    onDecryptSecret: (String?) -> String?,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AccountAvatarFrame(
                    displayName = item.account.displayName,
                    identifier = item.account.email ?: item.account.username,
                    size = 44.dp
                )
                Column {
                    Text(item.account.displayName, fontWeight = FontWeight.Bold)
                    Text(
                        text = item.account.primaryIdentifier,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccountStatusBadge(
                        status = item.account.status,
                        isArchived = item.account.isArchived
                    )
                    item.category?.let { CategoryBadgeChip(categoryName = it.name) }
                }

                DetailFieldRow("Email Address", item.account.email, monospace = true)
                DetailFieldRow("Username / Handle", item.account.username, monospace = true)
                DetailFieldRow("Purpose / Role", item.account.purpose)
                DetailFieldRow("Recovery Email Reference", item.account.recoveryEmailReference, monospace = true)
                DetailFieldRow("Recovery Phone Reference", item.account.recoveryPhoneReference, monospace = true)

                if (item.account.hasPassword || item.account.hasNotes) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Password Manager Vault",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                ProtectedVaultFieldCard(
                    label = "Account Password",
                    hasValue = item.account.hasPassword,
                    onDecryptValue = { onDecryptSecret(item.account.encryptedPassword) },
                    onRequestVerifiedAction = onRequestVerifiedAction,
                    testTagPrefix = "account_password_${item.account.id}",
                    isMonospace = true
                )

                ProtectedVaultFieldCard(
                    label = "Protected Notes",
                    hasValue = item.account.hasNotes,
                    onDecryptValue = { item.account.notes },
                    onRequestVerifiedAction = onRequestVerifiedAction,
                    testTagPrefix = "account_notes_${item.account.id}",
                    isMonospace = false
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Linked Services (${item.linkedServices.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.linkedServices.isEmpty()) {
                    Text(
                        text = "No Services linked to this account yet. Open the Services tab to link this account to any service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.linkedServices.forEach { linked ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ServiceLogoFrame(
                                    serviceName = linked.service.name,
                                    asset = linked.serviceAsset,
                                    size = 34.dp
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
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                DetailFieldRow(
                    "Created",
                    dateFormatter.format(Date(item.account.createdAtEpochMillis))
                )
                DetailFieldRow(
                    "Last Updated",
                    dateFormatter.format(Date(item.account.updatedAtEpochMillis))
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEdit,
                modifier = Modifier.testTag("detail_edit_account_button")
            ) {
                Text("Edit")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditorDialog(
    initialAccount: AccountWithDetails?,
    initialDecryptedPassword: String,
    categories: List<CategoryWithCount>,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        displayName: String,
        email: String?,
        username: String?,
        password: String?,
        categoryId: String?,
        purpose: String?,
        recoveryEmailReference: String?,
        recoveryPhoneReference: String?,
        notes: String?,
        status: AccountStatus,
        onError: (String) -> Unit
    ) -> Unit
) {
    var displayName by rememberSaveable { mutableStateOf(initialAccount?.account?.displayName.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(initialAccount?.account?.email.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(initialAccount?.account?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(initialDecryptedPassword) }
    var selectedCategoryId by rememberSaveable { mutableStateOf(initialAccount?.account?.categoryId) }
    var purpose by rememberSaveable { mutableStateOf(initialAccount?.account?.purpose.orEmpty()) }
    var recoveryEmail by rememberSaveable {
        mutableStateOf(initialAccount?.account?.recoveryEmailReference.orEmpty())
    }
    var recoveryPhone by rememberSaveable {
        mutableStateOf(initialAccount?.account?.recoveryPhoneReference.orEmpty())
    }
    var notes by rememberSaveable { mutableStateOf(initialAccount?.account?.notes.orEmpty()) }
    var status by rememberSaveable {
        mutableStateOf(initialAccount?.account?.status ?: AccountStatus.ACTIVE)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialAccount == null) "Add Standalone Account" else "Edit Account")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

                PasswordManagerFieldEditor(
                    password = password,
                    onPasswordChange = { password = it },
                    label = "Account Password (Encrypted)",
                    isOptional = true,
                    testTag = "account_password_input"
                )

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
                    text = "Status",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccountStatus.entries.forEach { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
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

                OutlinedTextField(
                    value = recoveryEmail,
                    onValueChange = { recoveryEmail = it },
                    label = { Text("Recovery Email Reference") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_recovery_email_input")
                )

                OutlinedTextField(
                    value = recoveryPhone,
                    onValueChange = { recoveryPhone = it },
                    label = { Text("Recovery Phone Reference") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_recovery_phone_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Protected Notes (Verified on view)") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_notes_input")
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
                    onSave(
                        initialAccount?.account?.id,
                        displayName,
                        email.takeIf { it.isNotBlank() },
                        username.takeIf { it.isNotBlank() },
                        password.takeIf { it.isNotBlank() },
                        selectedCategoryId,
                        purpose.takeIf { it.isNotBlank() },
                        recoveryEmail.takeIf { it.isNotBlank() },
                        recoveryPhone.takeIf { it.isNotBlank() },
                        notes.takeIf { it.isNotBlank() },
                        status
                    ) { err ->
                        errorMessage = err
                    }
                },
                modifier = Modifier.testTag("save_account_button")
            ) {
                Text("Save Account")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
