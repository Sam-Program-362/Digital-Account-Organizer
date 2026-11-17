package com.example.feature.services

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.example.app.DuplicateServicePrompt
import com.example.core.database.FetchedServiceLogoResult
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
import com.example.domain.model.ServiceAccountWithDetails
import com.example.domain.model.ServiceAssetRecord
import com.example.domain.model.ServiceDeleteResolution
import com.example.domain.model.ServiceWithCount
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServicesScreen(
    services: List<ServiceWithCount>,
    serviceAccounts: List<ServiceAccountWithDetails>,
    standaloneAccounts: List<AccountWithDetails>,
    categories: List<CategoryWithCount>,
    selectedServiceIdForDetail: String?,
    duplicatePrompt: DuplicateServicePrompt?,
    onSelectServiceForDetail: (String?) -> Unit,
    onCreateServiceClick: () -> Unit,
    onEditServiceClick: (ServiceWithCount) -> Unit,
    onToggleArchiveService: (String, Boolean) -> Unit,
    onResolveDeleteService: (
        serviceId: String,
        resolution: ServiceDeleteResolution,
        reassignTargetId: String?,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onConfirmDuplicateServiceVariation: () -> Unit,
    onDismissDuplicatePrompt: () -> Unit,
    onDecryptSecret: (String?) -> String?,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    onSaveServiceAccount: (
        existingId: String?,
        serviceId: String,
        linkedAccountId: String?,
        displayName: String,
        email: String?,
        username: String?,
        password: String?,
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
    onToggleArchiveServiceAccount: (String, Boolean) -> Unit,
    onDeleteServiceAccountPermanently: (String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showArchivedServices by rememberSaveable { mutableStateOf(false) }
    var deletingService by remember { mutableStateOf<ServiceWithCount?>(null) }

    var addingOrEditingForServiceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingServiceAccount by remember { mutableStateOf<ServiceAccountWithDetails?>(null) }
    var initialServiceAccountPassword by remember { mutableStateOf("") }
    var viewingServiceAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingServiceAccount by remember { mutableStateOf<ServiceAccountWithDetails?>(null) }

    val filteredServices = remember(services, serviceAccounts, searchQuery, showArchivedServices) {
        val q = searchQuery.trim().lowercase()
        val matchingServiceIdsByEntry = if (q.isEmpty()) {
            emptySet()
        } else {
            serviceAccounts.filter { entry ->
                entry.serviceAccount.displayName.lowercase().contains(q) ||
                    (entry.effectiveEmail?.lowercase()?.contains(q) == true) ||
                    (entry.effectiveUsername?.lowercase()?.contains(q) == true) ||
                    (entry.serviceAccount.purpose?.lowercase()?.contains(q) == true)
            }.map { it.service.id }.toSet()
        }

        services.filter { item ->
            val archiveOk = if (showArchivedServices) true else !item.service.isArchived
            val queryOk = if (q.isEmpty()) {
                true
            } else {
                item.service.name.lowercase().contains(q) ||
                    (item.service.websiteUrl?.lowercase()?.contains(q) == true) ||
                    (item.service.description?.lowercase()?.contains(q) == true) ||
                    item.service.id in matchingServiceIdsByEntry
            }
            archiveOk && queryOk
        }
    }

    val detailService = remember(services, selectedServiceIdForDetail) {
        services.firstOrNull { it.service.id == selectedServiceIdForDetail }
    }

    val viewingServiceAccount = remember(serviceAccounts, viewingServiceAccountId) {
        serviceAccounts.firstOrNull { it.serviceAccount.id == viewingServiceAccountId }
    }

    val openEditServiceAccount: (ServiceAccountWithDetails) -> Unit = { entry ->
        if (entry.serviceAccount.hasPassword || entry.serviceAccount.hasNotes) {
            onRequestVerifiedAction {
                editingServiceAccount = entry
                initialServiceAccountPassword =
                    onDecryptSecret(entry.serviceAccount.encryptedPassword).orEmpty()
                addingOrEditingForServiceId = entry.service.id
            }
        } else {
            editingServiceAccount = entry
            initialServiceAccountPassword = ""
            addingOrEditingForServiceId = entry.service.id
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("services_screen")
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
                    text = "Services",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Create services and add detailed Service Account entries (linked or custom with optional passwords).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onCreateServiceClick,
                modifier = Modifier.testTag("create_service_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create Service")
            }
        }

        // Separate search bar for Services & Service Accounts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search services or service accounts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("services_search_input")
            )
            FilterChip(
                selected = showArchivedServices,
                onClick = { showArchivedServices = !showArchivedServices },
                label = { Text("Archived") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("services_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredServices.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Apps,
                        title = if (searchQuery.isBlank()) "No Services Created Yet" else "No Matching Services",
                        body = "Create a Service (such as GitHub, Google, or Proton) with a custom or fetched website logo, then add your Service Account entries inside it.",
                        actionLabel = "Create First Service",
                        actionTestTag = "services_empty_create_button",
                        onActionClick = onCreateServiceClick
                    )
                }
            } else {
                items(filteredServices, key = { it.service.id }) { item ->
                    val entries = remember(serviceAccounts, item.service.id) {
                        serviceAccounts.filter {
                            it.service.id == item.service.id && !it.serviceAccount.isArchived
                        }
                    }
                    ServiceCardItem(
                        item = item,
                        entries = entries,
                        onOpenDetail = { onSelectServiceForDetail(item.service.id) },
                        onAddServiceAccount = {
                            editingServiceAccount = null
                            initialServiceAccountPassword = ""
                            addingOrEditingForServiceId = item.service.id
                        },
                        onViewServiceAccount = { entry ->
                            viewingServiceAccountId = entry.serviceAccount.id
                        },
                        onEditService = { onEditServiceClick(item) },
                        onDeleteService = { deletingService = item }
                    )
                }
            }
        }
    }

    if (detailService != null) {
        val entriesForService = serviceAccounts.filter { it.service.id == detailService.service.id }
        ServiceDetailModal(
            serviceWithCount = detailService,
            serviceAccounts = entriesForService,
            onDismiss = { onSelectServiceForDetail(null) },
            onEditService = {
                val target = detailService
                onSelectServiceForDetail(null)
                onEditServiceClick(target)
            },
            onToggleArchiveService = {
                onToggleArchiveService(detailService.service.id, !detailService.service.isArchived)
            },
            onAddServiceAccount = {
                editingServiceAccount = null
                initialServiceAccountPassword = ""
                addingOrEditingForServiceId = detailService.service.id
            },
            onViewServiceAccount = { entry ->
                viewingServiceAccountId = entry.serviceAccount.id
            },
            onEditServiceAccount = { entry ->
                openEditServiceAccount(entry)
            },
            onDeleteServiceAccount = { entry ->
                deletingServiceAccount = entry
            }
        )
    }

    val targetServiceIdForForm = addingOrEditingForServiceId
    if (targetServiceIdForForm != null) {
        val svcWithCount = services.firstOrNull { it.service.id == targetServiceIdForForm }
        if (svcWithCount != null) {
            ServiceAccountEditorDialog(
                serviceWithCount = svcWithCount,
                initialEntry = editingServiceAccount,
                initialDecryptedPassword = initialServiceAccountPassword,
                standaloneAccounts = standaloneAccounts.filter { !it.account.isArchived },
                categories = categories,
                onDismiss = {
                    addingOrEditingForServiceId = null
                    editingServiceAccount = null
                },
                onSave = { linkedAccId, displayName, email, username, password, categoryId, purpose, recEmail, recPhone, webUrl, notes, status, onError ->
                    onSaveServiceAccount(
                        editingServiceAccount?.serviceAccount?.id,
                        svcWithCount.service.id,
                        linkedAccId,
                        displayName,
                        email,
                        username,
                        password,
                        categoryId,
                        purpose,
                        recEmail,
                        recPhone,
                        webUrl,
                        notes,
                        status,
                        {
                            addingOrEditingForServiceId = null
                            editingServiceAccount = null
                        },
                        onError
                    )
                }
            )
        }
    }

    if (viewingServiceAccount != null) {
        ServiceAccountDetailDialog(
            item = viewingServiceAccount,
            onDecryptSecret = onDecryptSecret,
            onRequestVerifiedAction = onRequestVerifiedAction,
            onDismiss = { viewingServiceAccountId = null },
            onEdit = {
                val target = viewingServiceAccount
                viewingServiceAccountId = null
                openEditServiceAccount(target)
            },
            onToggleArchive = {
                onToggleArchiveServiceAccount(
                    viewingServiceAccount.serviceAccount.id,
                    !viewingServiceAccount.serviceAccount.isArchived
                )
            }
        )
    }

    val targetDeleteSvc = deletingService
    if (targetDeleteSvc != null) {
        val otherServices = services.filter { it.service.id != targetDeleteSvc.service.id }
        SafeDeleteServiceDialog(
            serviceWithCount = targetDeleteSvc,
            otherServices = otherServices,
            onDismiss = { deletingService = null },
            onConfirmResolution = { resolution, reassignId, onError ->
                onResolveDeleteService(
                    targetDeleteSvc.service.id,
                    resolution,
                    reassignId,
                    { deletingService = null },
                    onError
                )
            }
        )
    }

    val targetDeleteEntry = deletingServiceAccount
    if (targetDeleteEntry != null) {
        DestructiveConfirmationDialog(
            title = "Delete '${targetDeleteEntry.serviceAccount.displayName}'?",
            message = "This will permanently remove this Service Account entry from ${targetDeleteEntry.service.name}. Any linked standalone Account in your Accounts tab will remain untouched.",
            confirmLabel = "Delete Entry",
            onConfirm = {
                onDeleteServiceAccountPermanently(targetDeleteEntry.serviceAccount.id)
                deletingServiceAccount = null
            },
            onDismiss = { deletingServiceAccount = null }
        )
    }

    if (duplicatePrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissDuplicatePrompt,
            title = { Text("Similar Service Name Exists") },
            text = {
                Text(
                    "A service named '${duplicatePrompt.conflictingServiceName}' already exists. Would you like to save '${duplicatePrompt.pendingName}' as a separate service anyway?"
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDuplicateServiceVariation,
                    modifier = Modifier.testTag("confirm_duplicate_service_button")
                ) {
                    Text("Save Anyway")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissDuplicatePrompt) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ServiceCardItem(
    item: ServiceWithCount,
    entries: List<ServiceAccountWithDetails>,
    onOpenDetail: () -> Unit,
    onAddServiceAccount: () -> Unit,
    onViewServiceAccount: (ServiceAccountWithDetails) -> Unit,
    onEditService: () -> Unit,
    onDeleteService: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .testTag("service_card_${item.service.name}"),
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
                    ServiceLogoFrame(
                        serviceName = item.service.name,
                        asset = item.iconAsset,
                        size = 48.dp
                    )
                    Column {
                        Text(
                            text = item.service.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!item.service.websiteUrl.isNullOrBlank()) {
                            Text(
                                text = item.service.websiteUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${item.activeAccountCount} active service account(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onAddServiceAccount,
                        modifier = Modifier.testTag("add_service_account_for_${item.service.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add account to ${item.service.name}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onEditService) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${item.service.name}")
                    }
                    IconButton(onClick = onDeleteService) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete ${item.service.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (entries.isNotEmpty()) {
                HorizontalDivider()
                entries.take(3).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewServiceAccount(entry) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry.serviceAccount.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (entry.linkedAccount != null) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = "Linked standalone account",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (entry.serviceAccount.hasPassword || entry.linkedAccount?.hasPassword == true) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = "Password available",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = entry.effectiveIdentifier,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AccountStatusBadge(status = entry.serviceAccount.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceDetailModal(
    serviceWithCount: ServiceWithCount,
    serviceAccounts: List<ServiceAccountWithDetails>,
    onDismiss: () -> Unit,
    onEditService: () -> Unit,
    onToggleArchiveService: () -> Unit,
    onAddServiceAccount: () -> Unit,
    onViewServiceAccount: (ServiceAccountWithDetails) -> Unit,
    onEditServiceAccount: (ServiceAccountWithDetails) -> Unit,
    onDeleteServiceAccount: (ServiceAccountWithDetails) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ServiceLogoFrame(
                    serviceName = serviceWithCount.service.name,
                    asset = serviceWithCount.iconAsset,
                    size = 44.dp
                )
                Column {
                    Text(serviceWithCount.service.name, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${serviceAccounts.size} service account entry(s)",
                        style = MaterialTheme.typography.bodySmall,
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
                if (!serviceWithCount.service.websiteUrl.isNullOrBlank()) {
                    Text(
                        text = "Website: ${serviceWithCount.service.websiteUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
                if (!serviceWithCount.service.description.isNullOrBlank()) {
                    Text(
                        text = serviceWithCount.service.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Service Accounts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = onAddServiceAccount,
                        modifier = Modifier.testTag("service_detail_add_entry_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Service Account")
                    }
                }

                if (serviceAccounts.isEmpty()) {
                    Text(
                        text = "No service accounts created for ${serviceWithCount.service.name} yet. Tap 'Add Service Account' to link an existing standalone Account or add a custom service username/email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    serviceAccounts.forEach { entry ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onViewServiceAccount(entry) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = entry.serviceAccount.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        AccountStatusBadge(
                                            status = entry.serviceAccount.status,
                                            isArchived = entry.serviceAccount.isArchived
                                        )
                                    }
                                    Text(
                                        text = entry.effectiveIdentifier,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (entry.linkedAccount != null) {
                                            "Linked to Standalone Account: ${entry.linkedAccount.displayName}"
                                        } else {
                                            "Unlinked service-specific account"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = { onEditServiceAccount(entry) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit entry")
                                    }
                                    IconButton(onClick = { onDeleteServiceAccount(entry) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete entry",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleArchiveService) {
                    Text(if (serviceWithCount.service.isArchived) "Unarchive" else "Archive")
                }
                Button(onClick = onEditService) {
                    Text("Edit Service")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceAccountEditorDialog(
    serviceWithCount: ServiceWithCount,
    initialEntry: ServiceAccountWithDetails?,
    initialDecryptedPassword: String,
    standaloneAccounts: List<AccountWithDetails>,
    categories: List<CategoryWithCount>,
    onDismiss: () -> Unit,
    onSave: (
        linkedAccountId: String?,
        displayName: String,
        email: String?,
        username: String?,
        password: String?,
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
    var useLinkedAccount by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.linkedAccountId != null)
    }
    var selectedLinkedAccountId by rememberSaveable {
        mutableStateOf(
            initialEntry?.serviceAccount?.linkedAccountId
                ?: standaloneAccounts.firstOrNull()?.account?.id
        )
    }
    var displayName by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.displayName.orEmpty())
    }
    var email by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.email.orEmpty())
    }
    var username by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.username.orEmpty())
    }
    var password by rememberSaveable { mutableStateOf(initialDecryptedPassword) }
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.categoryId)
    }
    var purpose by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.purpose.orEmpty())
    }
    var recoveryEmail by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.recoveryEmailReference.orEmpty())
    }
    var recoveryPhone by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.recoveryPhoneReference.orEmpty())
    }
    var websiteUrl by rememberSaveable {
        mutableStateOf(
            initialEntry?.serviceAccount?.websiteUrl
                ?: serviceWithCount.service.websiteUrl.orEmpty()
        )
    }
    var notes by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.notes.orEmpty())
    }
    var status by rememberSaveable {
        mutableStateOf(initialEntry?.serviceAccount?.status ?: AccountStatus.ACTIVE)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialEntry == null) {
                    "Add Account Entry for ${serviceWithCount.service.name}"
                } else {
                    "Edit ${serviceWithCount.service.name} Entry"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Account Connection Mode",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = !useLinkedAccount,
                        onClick = { useLinkedAccount = false },
                        label = { Text("Custom / Unlinked Account") },
                        modifier = Modifier.testTag("service_account_mode_custom")
                    )
                    FilterChip(
                        selected = useLinkedAccount,
                        onClick = {
                            if (standaloneAccounts.isNotEmpty()) {
                                useLinkedAccount = true
                                if (selectedLinkedAccountId == null) {
                                    selectedLinkedAccountId = standaloneAccounts.first().account.id
                                }
                            }
                        },
                        enabled = standaloneAccounts.isNotEmpty(),
                        label = { Text("Link Standalone Account (${standaloneAccounts.size})") },
                        modifier = Modifier.testTag("service_account_mode_linked")
                    )
                }

                if (useLinkedAccount && standaloneAccounts.isNotEmpty()) {
                    Text(
                        text = "Select Standalone Account",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        standaloneAccounts.forEach { acc ->
                            FilterChip(
                                selected = selectedLinkedAccountId == acc.account.id,
                                onClick = {
                                    selectedLinkedAccountId = acc.account.id
                                    if (displayName.isBlank()) {
                                        displayName = acc.account.displayName
                                    }
                                },
                                label = {
                                    Text("${acc.account.displayName} (${acc.account.primaryIdentifier})")
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        errorMessage = null
                    },
                    label = { Text("Entry Label * (e.g., Main Profile, Work Handle)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_account_label_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = {
                        Text(
                            if (useLinkedAccount) {
                                "Service Username / Handle Override (Optional)"
                            } else {
                                "Service Username / Handle"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_account_username_input")
                )

                if (!useLinkedAccount) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Service Email Address (Optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("service_account_email_input")
                    )
                }

                PasswordManagerFieldEditor(
                    password = password,
                    onPasswordChange = { password = it },
                    label = if (useLinkedAccount) {
                        "Service-Specific Password (Optional — leave blank to use linked account's)"
                    } else {
                        "Service Password (Optional, Encrypted)"
                    },
                    isOptional = true,
                    testTag = "service_account_password_input"
                )

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose / Specific Role") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                            label = { Text(option.displayLabel) }
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
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = recoveryPhone,
                    onValueChange = { recoveryPhone = it },
                    label = { Text("Recovery Phone Reference") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { websiteUrl = it },
                    label = { Text("Service Login / Profile URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Protected Notes (Verified on view)") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_account_notes_input")
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
                    val linkedId = if (useLinkedAccount) selectedLinkedAccountId else null
                    onSave(
                        linkedId,
                        displayName,
                        email.takeIf { it.isNotBlank() },
                        username.takeIf { it.isNotBlank() },
                        password.takeIf { it.isNotBlank() },
                        selectedCategoryId,
                        purpose.takeIf { it.isNotBlank() },
                        recoveryEmail.takeIf { it.isNotBlank() },
                        recoveryPhone.takeIf { it.isNotBlank() },
                        websiteUrl.takeIf { it.isNotBlank() },
                        notes.takeIf { it.isNotBlank() },
                        status
                    ) { err ->
                        errorMessage = err
                    }
                },
                modifier = Modifier.testTag("save_service_account_button")
            ) {
                Text("Save Entry")
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
private fun ServiceAccountDetailDialog(
    item: ServiceAccountWithDetails,
    onDecryptSecret: (String?) -> String?,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ServiceLogoFrame(
                    serviceName = item.service.name,
                    asset = item.serviceAsset,
                    size = 44.dp
                )
                Column {
                    Text(item.serviceAccount.displayName, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${item.service.name} • ${item.effectiveIdentifier}",
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
                        status = item.serviceAccount.status,
                        isArchived = item.serviceAccount.isArchived
                    )
                    item.category?.let { CategoryBadgeChip(categoryName = it.name) }
                }

                item.linkedAccount?.let { linked ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AccountAvatarFrame(
                                displayName = linked.displayName,
                                identifier = linked.primaryIdentifier,
                                size = 34.dp
                            )
                            Column {
                                Text(
                                    text = "Linked Standalone Account",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${linked.displayName} (${linked.primaryIdentifier})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (!item.effectiveEmail.isNullOrBlank()) {
                    Text(
                        text = "Email: ${item.effectiveEmail}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
                if (!item.effectiveUsername.isNullOrBlank()) {
                    Text(
                        text = "Username: ${item.effectiveUsername}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
                if (!item.serviceAccount.purpose.isNullOrBlank()) {
                    Text(
                        text = "Purpose: ${item.serviceAccount.purpose}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val hasOwnPassword = item.serviceAccount.hasPassword
                val hasLinkedPassword = item.linkedAccount?.hasPassword == true

                ProtectedVaultFieldCard(
                    label = if (hasOwnPassword) {
                        "Service Account Password"
                    } else {
                        "Linked Account Password"
                    },
                    hasValue = hasOwnPassword || hasLinkedPassword,
                    onDecryptValue = {
                        val cipher = if (hasOwnPassword) {
                            item.serviceAccount.encryptedPassword
                        } else {
                            item.linkedAccount?.encryptedPassword
                        }
                        onDecryptSecret(cipher)
                    },
                    onRequestVerifiedAction = onRequestVerifiedAction,
                    testTagPrefix = "service_account_password_${item.serviceAccount.id}",
                    isMonospace = true
                )

                ProtectedVaultFieldCard(
                    label = "Protected Notes",
                    hasValue = item.serviceAccount.hasNotes,
                    onDecryptValue = { item.serviceAccount.notes },
                    onRequestVerifiedAction = onRequestVerifiedAction,
                    testTagPrefix = "service_account_notes_${item.serviceAccount.id}",
                    isMonospace = false
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleArchive) {
                    Text(if (item.serviceAccount.isArchived) "Unarchive" else "Archive")
                }
                Button(onClick = onEdit) {
                    Text("Edit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ServiceEditorDialog(
    initialService: ServiceWithCount?,
    onImportCustomLogo: (Uri, (ServiceAssetRecord) -> Unit, (String) -> Unit) -> Unit,
    onFetchWebsiteLogo: (String, (FetchedServiceLogoResult) -> Unit, (String) -> Unit) -> Unit,
    onCancelWebsiteLogoFetch: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        name: String,
        websiteUrl: String?,
        description: String?,
        iconAssetId: String?,
        onError: (String) -> Unit
    ) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialService?.service?.name.orEmpty()) }
    var websiteUrl by rememberSaveable { mutableStateOf(initialService?.service?.websiteUrl.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initialService?.service?.description.orEmpty()) }
    var currentAsset by remember { mutableStateOf(initialService?.iconAsset) }
    var isFetchingLogo by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportCustomLogo(
                uri,
                { asset ->
                    currentAsset = asset
                    statusMessage = "Custom logo imported."
                    errorMessage = null
                },
                { err -> errorMessage = err }
            )
        }
    }

    AlertDialog(
        onDismissRequest = {
            onCancelWebsiteLogoFetch()
            onDismiss()
        },
        title = { Text(if (initialService == null) "Create Service" else "Edit Service") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ServiceLogoFrame(
                        serviceName = name.ifBlank { "Service" },
                        asset = currentAsset,
                        size = 56.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Service Logo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pick an image from device or fetch directly from the website HTTPS URL.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_service_logo_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Image")
                    }

                    if (currentAsset != null) {
                        TextButton(onClick = { currentAsset = null }) {
                            Text("Clear Logo")
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Service Name * (e.g., GitHub, Google)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_name_input")
                )

                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = {
                        websiteUrl = it
                        errorMessage = null
                    },
                    label = { Text("Website URL (e.g., https://github.com)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_website_input")
                )

                OutlinedButton(
                    onClick = {
                        isFetchingLogo = true
                        errorMessage = null
                        statusMessage = null
                        onFetchWebsiteLogo(
                            websiteUrl,
                            { fetched ->
                                isFetchingLogo = false
                                currentAsset = fetched.asset
                                if (websiteUrl.isBlank()) {
                                    websiteUrl = fetched.normalizedWebsiteUrl
                                }
                                statusMessage = "Fetched logo from ${fetched.sourceHost}"
                            },
                            { err ->
                                isFetchingLogo = false
                                errorMessage = err
                            }
                        )
                    },
                    enabled = websiteUrl.isNotBlank() && !isFetchingLogo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fetch_website_logo_button")
                ) {
                    if (isFetchingLogo) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching Website Logo...")
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fetch Logo from URL")
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes (Optional)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_description_input")
                )

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

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
                        initialService?.service?.id,
                        name,
                        websiteUrl.takeIf { it.isNotBlank() },
                        description.takeIf { it.isNotBlank() },
                        currentAsset?.id
                    ) { err ->
                        errorMessage = err
                    }
                },
                modifier = Modifier.testTag("save_service_button")
            ) {
                Text("Save Service")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onCancelWebsiteLogoFetch()
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SafeDeleteServiceDialog(
    serviceWithCount: ServiceWithCount,
    otherServices: List<ServiceWithCount>,
    onDismiss: () -> Unit,
    onConfirmResolution: (
        resolution: ServiceDeleteResolution,
        reassignTargetId: String?,
        onError: (String) -> Unit
    ) -> Unit
) {
    val hasLinkedEntries = serviceWithCount.totalAccountCount > 0
    var selectedResolution by remember {
        mutableStateOf(
            if (hasLinkedEntries) {
                ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD
            } else {
                ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS
            }
        )
    }
    var reassignServiceId by remember {
        mutableStateOf(otherServices.firstOrNull()?.service?.id)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Service '${serviceWithCount.service.name}'?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasLinkedEntries) {
                    Text(
                        text = "This service has no service account entries and can be deleted safely.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "This service has ${serviceWithCount.totalAccountCount} service account entry(s). Your standalone Accounts in the Accounts tab are never deleted. Choose how to handle this service's entries:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedResolution = ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD
                            }
                    ) {
                        RadioButton(
                            selected = selectedResolution == ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD,
                            onClick = {
                                selectedResolution = ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD
                            }
                        )
                        Text("Archive service instead (Recommended)", style = MaterialTheme.typography.bodySmall)
                    }
                    if (otherServices.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedResolution = ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE
                                }
                        ) {
                            RadioButton(
                                selected = selectedResolution == ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE,
                                onClick = {
                                    selectedResolution = ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE
                                }
                            )
                            Text(
                                "Move entries to '${otherServices.firstOrNull { it.service.id == reassignServiceId }?.service?.name ?: otherServices.first().service.name}'",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedResolution = ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS
                            }
                    ) {
                        RadioButton(
                            selected = selectedResolution == ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS,
                            onClick = {
                                selectedResolution = ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS
                            }
                        )
                        Text(
                            "Delete service and its ${serviceWithCount.totalAccountCount} service entry(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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
                    onConfirmResolution(selectedResolution, reassignServiceId) { err ->
                        errorMessage = err
                    }
                },
                modifier = Modifier.testTag("confirm_delete_service_button")
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
