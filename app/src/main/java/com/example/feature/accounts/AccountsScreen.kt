package com.example.feature.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.app.AccountFilterState
import com.example.app.AccountSortOrder
import com.example.core.designsystem.AccountStatusBadge
import com.example.core.designsystem.CategoryBadgeChip
import com.example.core.designsystem.DestructiveConfirmationDialog
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.ServiceLogoFrame
import com.example.domain.model.AccountStatus
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.CategoryWithCount
import com.example.domain.model.ServiceWithCount
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(
    accounts: List<AccountWithDetails>,
    services: List<ServiceWithCount>,
    categories: List<CategoryWithCount>,
    filterState: AccountFilterState,
    openCreateDialogForServiceId: String?,
    onConsumeCreateDialogRequest: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onSelectServiceFilter: (String?) -> Unit,
    onSelectCategoryFilter: (String?) -> Unit,
    onSelectStatusFilter: (AccountStatus?) -> Unit,
    onToggleShowArchived: (Boolean) -> Unit,
    onSelectSortOrder: (AccountSortOrder) -> Unit,
    onClearAllFilters: () -> Unit,
    onSaveAccount: (
        existingId: String?,
        serviceId: String,
        displayName: String,
        email: String?,
        username: String?,
        categoryId: String?,
        purpose: String?,
        recoveryEmailReference: String?,
        recoveryPhoneReference: String?,
        websiteUrl: String?,
        notes: String?,
        status: AccountStatus,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onToggleArchiveAccount: (String, Boolean) -> Unit,
    onDeleteAccountPermanently: (String) -> Unit,
    onNavigateToCreateService: () -> Unit
) {
    var editingAccount by remember { mutableStateOf<AccountWithDetails?>(null) }
    var preselectedServiceIdForForm by rememberSaveable { mutableStateOf<String?>(null) }
    var showFormDialog by rememberSaveable { mutableStateOf(false) }
    var viewingAccount by remember { mutableStateOf<AccountWithDetails?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountWithDetails?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    if (openCreateDialogForServiceId != null) {
        editingAccount = null
        preselectedServiceIdForForm = openCreateDialogForServiceId.takeIf { it.isNotEmpty() }
        showFormDialog = true
        onConsumeCreateDialogRequest()
    }

    val activeServices = remember(services) { services.filter { !it.service.isArchived } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("accounts_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Accounts",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Multiple accounts per service supported. No passwords stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (services.isEmpty()) {
                            onNavigateToCreateService()
                        } else {
                            editingAccount = null
                            preselectedServiceIdForForm = filterState.selectedServiceId
                            showFormDialog = true
                        }
                    },
                    modifier = Modifier.testTag("add_account_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (services.isEmpty()) "Create Service First" else "Add Account")
                }
            }

            // Search and sort bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = filterState.searchQuery,
                    onValueChange = onUpdateSearchQuery,
                    placeholder = { Text("Search label, email, username, service...") },
                    singleLine = true,
                    trailingIcon = {
                        if (filterState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onUpdateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("accounts_search_input")
                )

                Column {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("accounts_sort_button")
                    ) {
                        Text("Sort")
                    }
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

            // Filter chips row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterState.showArchived,
                    onClick = { onToggleShowArchived(!filterState.showArchived) },
                    label = { Text("Include Archived") },
                    modifier = Modifier.testTag("accounts_filter_archived_chip")
                )

                AccountStatus.entries.forEach { status ->
                    val selected = filterState.selectedStatus == status
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectStatusFilter(if (selected) null else status) },
                        label = { Text(status.displayLabel) },
                        modifier = Modifier.testTag("accounts_filter_status_${status.name}")
                    )
                }

                services.forEach { svc ->
                    val selected = filterState.selectedServiceId == svc.service.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectServiceFilter(if (selected) null else svc.service.id) },
                        label = { Text("Service: ${svc.service.name}") }
                    )
                }

                categories.forEach { cat ->
                    val selected = filterState.selectedCategoryId == cat.category.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCategoryFilter(if (selected) null else cat.category.id) },
                        label = { Text("Category: ${cat.category.name}") }
                    )
                }

                val hasActiveFilter = filterState.searchQuery.isNotEmpty() ||
                    filterState.selectedServiceId != null ||
                    filterState.selectedCategoryId != null ||
                    filterState.selectedStatus != null ||
                    filterState.showArchived
                if (hasActiveFilter) {
                    TextButton(onClick = onClearAllFilters) {
                        Text("Reset Filters")
                    }
                }
            }
        }

        if (accounts.isEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val noServicesYet = services.isEmpty()
                EmptyStateCard(
                    icon = Icons.Default.Person,
                    title = if (noServicesYet) {
                        "Create a Custom Service Before Adding Accounts"
                    } else {
                        "No Matching Account Records"
                    },
                    body = if (noServicesYet) {
                        "Every account belongs to a user-created service. Create your first service now, then add one or more accounts under it."
                    } else {
                        "Add a new account record or clear active search/filter criteria."
                    },
                    actionLabel = if (noServicesYet) "Create Service" else "Add Account",
                    actionTestTag = "accounts_empty_action_button",
                    onActionClick = {
                        if (noServicesYet) {
                            onNavigateToCreateService()
                        } else {
                            editingAccount = null
                            preselectedServiceIdForForm = filterState.selectedServiceId
                            showFormDialog = true
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(accounts, key = { it.account.id }) { item ->
                    AccountItemCard(
                        item = item,
                        onClick = { viewingAccount = item },
                        onEditClick = {
                            editingAccount = item
                            showFormDialog = true
                        },
                        onArchiveToggleClick = {
                            onToggleArchiveAccount(item.account.id, !item.account.isArchived)
                        },
                        onDeleteClick = {
                            deletingAccount = item
                        }
                    )
                }
            }
        }
    }

    val detailItem = viewingAccount
    if (detailItem != null) {
        AccountDetailDialog(
            item = detailItem,
            onEdit = {
                editingAccount = detailItem
                viewingAccount = null
                showFormDialog = true
            },
            onArchiveToggle = {
                onToggleArchiveAccount(detailItem.account.id, !detailItem.account.isArchived)
                viewingAccount = null
            },
            onDelete = {
                deletingAccount = detailItem
                viewingAccount = null
            },
            onDismiss = { viewingAccount = null }
        )
    }

    if (showFormDialog && services.isNotEmpty()) {
        AccountFormDialog(
            initial = editingAccount,
            preselectedServiceId = preselectedServiceIdForForm,
            services = activeServices.ifEmpty { services },
            categories = categories,
            onDismiss = { showFormDialog = false },
            onSave = { serviceId, displayName, email, username, categoryId, purpose, recEmail, recPhone, website, notes, status, onError ->
                onSaveAccount(
                    editingAccount?.account?.id,
                    serviceId,
                    displayName,
                    email,
                    username,
                    categoryId,
                    purpose,
                    recEmail,
                    recPhone,
                    website,
                    notes,
                    status,
                    { showFormDialog = false },
                    onError
                )
            }
        )
    }

    val targetDelete = deletingAccount
    if (targetDelete != null) {
        DestructiveConfirmationDialog(
            title = "Permanently Delete '${targetDelete.account.displayName}'?",
            message = "This will permanently remove the '${targetDelete.account.displayName}' account record under '${targetDelete.service.name}'. If you only want to hide it from active lists, cancel and use Archive instead.",
            confirmLabel = "Delete Permanently",
            onConfirm = {
                onDeleteAccountPermanently(targetDelete.account.id)
                deletingAccount = null
            },
            onDismiss = { deletingAccount = null }
        )
    }
}

@Composable
private fun AccountItemCard(
    item: AccountWithDetails,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onArchiveToggleClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("account_item_card_${item.account.displayName}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceLogoFrame(
                    serviceName = item.service.name,
                    asset = item.serviceAsset,
                    size = 46.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.account.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.service.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val primaryId = item.account.email ?: item.account.username
                    if (!primaryId.isNullOrBlank()) {
                        Text(
                            text = primaryId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onEditClick, modifier = Modifier.testTag("edit_account_button")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${item.account.displayName}")
                }
                IconButton(onClick = onArchiveToggleClick) {
                    Icon(
                        imageVector = if (item.account.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = if (item.account.isArchived) "Restore account" else "Archive account"
                    )
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.testTag("delete_account_button")) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete ${item.account.displayName}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountStatusBadge(status = item.account.status, isArchived = item.account.isArchived)
                    item.category?.let { cat ->
                        CategoryBadgeChip(categoryName = cat.name)
                    }
                }
                if (!item.account.purpose.isNullOrBlank()) {
                    Text(
                        text = item.account.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountDetailDialog(
    item: AccountWithDetails,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogoFrame(
                    serviceName = item.service.name,
                    asset = item.serviceAsset,
                    size = 42.dp
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.account.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        item.service.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountStatusBadge(status = item.account.status, isArchived = item.account.isArchived)
                    item.category?.let { CategoryBadgeChip(categoryName = it.name) }
                }

                DetailFieldRow("Email", item.account.email, context, allowCopy = true)
                DetailFieldRow("Username / ID", item.account.username, context, allowCopy = true)
                DetailFieldRow("Purpose", item.account.purpose, context)
                DetailFieldRow("Recovery Email Ref", item.account.recoveryEmailReference, context, allowCopy = true)
                DetailFieldRow("Recovery Phone Ref", item.account.recoveryPhoneReference, context, allowCopy = true)
                DetailFieldRow("Account Website", item.account.websiteUrl, context, allowCopy = true)
                DetailFieldRow("Notes", item.account.notes, context)

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Created: ${dateFormatter.format(Date(item.account.createdAtEpochMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last Updated: ${dateFormatter.format(Date(item.account.updatedAtEpochMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onEdit) {
                Text("Edit Record")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onArchiveToggle) {
                    Text(if (item.account.isArchived) "Restore" else "Archive")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun DetailFieldRow(
    label: String,
    value: String?,
    context: Context,
    allowCopy: Boolean = false
) {
    if (value.isNullOrBlank()) return
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (allowCopy) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountFormDialog(
    initial: AccountWithDetails?,
    preselectedServiceId: String?,
    services: List<ServiceWithCount>,
    categories: List<CategoryWithCount>,
    onDismiss: () -> Unit,
    onSave: (
        serviceId: String,
        displayName: String,
        email: String?,
        username: String?,
        categoryId: String?,
        purpose: String?,
        recoveryEmailReference: String?,
        recoveryPhoneReference: String?,
        websiteUrl: String?,
        notes: String?,
        status: AccountStatus,
        onError: (String) -> Unit
    ) -> Unit
) {
    var selectedServiceId by rememberSaveable {
        mutableStateOf(
            initial?.account?.serviceId
                ?: preselectedServiceId
                ?: services.first().service.id
        )
    }
    var displayName by rememberSaveable { mutableStateOf(initial?.account?.displayName.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(initial?.account?.email.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(initial?.account?.username.orEmpty()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf(initial?.account?.categoryId) }
    var purpose by rememberSaveable { mutableStateOf(initial?.account?.purpose.orEmpty()) }
    var recoveryEmail by rememberSaveable { mutableStateOf(initial?.account?.recoveryEmailReference.orEmpty()) }
    var recoveryPhone by rememberSaveable { mutableStateOf(initial?.account?.recoveryPhoneReference.orEmpty()) }
    var websiteUrl by rememberSaveable { mutableStateOf(initial?.account?.websiteUrl.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.account?.notes.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(initial?.account?.status ?: AccountStatus.ACTIVE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "Add Account Record" else "Edit Account Record")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Non-password reminder banner
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.no_password_storage_notice),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Service selector
                Text(
                    text = "Service *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    services.forEach { svc ->
                        FilterChip(
                            selected = selectedServiceId == svc.service.id,
                            onClick = { selectedServiceId = svc.service.id },
                            label = { Text(svc.service.name) },
                            modifier = Modifier.testTag("account_form_service_chip_${svc.service.name}")
                        )
                    }
                }

                // Group 1: Identity
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        errorMessage = null
                    },
                    label = { Text("Account Label * (e.g. Personal, Work, Secondary)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_display_name_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_email_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Handle / ID (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_username_input")
                )

                // Group 2: Organization & Status
                Text(
                    text = "Status *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.displayLabel) },
                            modifier = Modifier.testTag("account_status_chip_${st.name}")
                        )
                    }
                }

                if (categories.isNotEmpty()) {
                    Text(
                        text = "Category (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("None") }
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.category.id,
                                onClick = { selectedCategoryId = cat.category.id },
                                label = { Text(cat.category.name) },
                                modifier = Modifier.testTag("account_category_chip_${cat.category.name}")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose (Optional — why you keep this account)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_purpose_input")
                )

                // Group 3: Recovery References
                OutlinedTextField(
                    value = recoveryEmail,
                    onValueChange = { recoveryEmail = it },
                    label = { Text("Recovery Email Reference (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_recovery_email_input")
                )

                OutlinedTextField(
                    value = recoveryPhone,
                    onValueChange = { recoveryPhone = it },
                    label = { Text("Recovery Phone Reference (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_recovery_phone_input")
                )

                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = {
                        websiteUrl = it
                        errorMessage = null
                    },
                    label = { Text("Account URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_website_input")
                )

                // Group 4: Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional — do not store passwords)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_notes_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("account_form_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedServiceId,
                        displayName,
                        email,
                        username,
                        selectedCategoryId,
                        purpose,
                        recoveryEmail,
                        recoveryPhone,
                        websiteUrl,
                        notes,
                        status
                    ) { err -> errorMessage = err }
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
