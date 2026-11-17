package com.example.feature.services

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.app.DuplicateServicePrompt
import com.example.core.database.FetchedServiceLogoResult
import com.example.core.designsystem.AccountStatusBadge
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.ServiceLogoFrame
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.ServiceAssetRecord
import com.example.domain.model.ServiceDeleteResolution
import com.example.domain.model.ServiceWithCount

@Composable
fun ServicesScreen(
    services: List<ServiceWithCount>,
    allAccounts: List<AccountWithDetails>,
    duplicatePrompt: DuplicateServicePrompt?,
    openCreateDialogRequest: Boolean,
    onConsumeCreateDialogRequest: () -> Unit,
    onImportLogoUri: (Uri, (ServiceAssetRecord) -> Unit, (String) -> Unit) -> Unit,
    onFetchLogoFromUrl: (String, (FetchedServiceLogoResult) -> Unit, (String) -> Unit) -> Unit = { _, _, _ -> },
    onCancelLogoFetch: () -> Unit = {},
    onSaveService: (
        existingId: String?,
        name: String,
        websiteUrl: String?,
        description: String?,
        iconAssetId: String?,
        allowDuplicateVariation: Boolean,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onDismissDuplicatePrompt: () -> Unit,
    onToggleArchiveService: (String, Boolean) -> Unit,
    onDeleteOrResolveService: (
        serviceId: String,
        resolution: ServiceDeleteResolution,
        reassignTargetServiceId: String?,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onAddAccountForService: (String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<ServiceWithCount?>(null) }
    var showFormDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDetailServiceId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingService by remember { mutableStateOf<ServiceWithCount?>(null) }

    if (openCreateDialogRequest) {
        editingService = null
        showFormDialog = true
        onConsumeCreateDialogRequest()
    }

    val visibleServices = remember(services, searchQuery, showArchived) {
        val q = searchQuery.trim().lowercase()
        services.filter { item ->
            val archiveMatch = if (showArchived) true else !item.service.isArchived
            val queryMatch = q.isEmpty() ||
                item.service.normalizedName.contains(q) ||
                (item.service.websiteUrl?.lowercase()?.contains(q) == true) ||
                (item.service.description?.lowercase()?.contains(q) == true)
            archiveMatch && queryMatch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("services_screen")
    ) {
        // Header bar & controls
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
                        text = "Custom Services",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Every service is created manually by you—no external provider directory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        editingService = null
                        showFormDialog = true
                    },
                    modifier = Modifier.testTag("create_service_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Service")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search services...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("services_search_input")
                )
                FilterChip(
                    selected = showArchived,
                    onClick = { showArchived = !showArchived },
                    label = { Text("Archived") },
                    modifier = Modifier.testTag("services_show_archived_chip")
                )
            }
        }

        if (visibleServices.isEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                EmptyStateCard(
                    icon = Icons.Default.Apps,
                    title = if (services.isEmpty()) "No Custom Services Created Yet" else "No Matching Services",
                    body = if (services.isEmpty()) {
                        "Create a custom service (with an optional custom logo/icon and website URL) to organize one or more accounts under it."
                    } else {
                        "Try adjusting your search or enabling the Archived filter."
                    },
                    actionLabel = if (services.isEmpty()) "Create Service" else null,
                    actionTestTag = "services_empty_create_button",
                    onActionClick = if (services.isEmpty()) {
                        {
                            editingService = null
                            showFormDialog = true
                        }
                    } else {
                        null
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleServices, key = { it.service.id }) { item ->
                    ServiceDirectoryCard(
                        item = item,
                        onClick = { selectedDetailServiceId = item.service.id },
                        onAddAccountClick = { onAddAccountForService(item.service.id) },
                        onEditClick = {
                            editingService = item
                            showFormDialog = true
                        },
                        onArchiveToggleClick = {
                            onToggleArchiveService(item.service.id, !item.service.isArchived)
                        },
                        onDeleteClick = {
                            deletingService = item
                        }
                    )
                }
            }
        }
    }

    // Detail dialog showing linked accounts
    val detailService = services.firstOrNull { it.service.id == selectedDetailServiceId }
    if (detailService != null) {
        val linkedAccounts = allAccounts.filter { it.account.serviceId == detailService.service.id }
        ServiceDetailDialog(
            serviceWithCount = detailService,
            linkedAccounts = linkedAccounts,
            onAddAccount = {
                val id = detailService.service.id
                selectedDetailServiceId = null
                onAddAccountForService(id)
            },
            onEditService = {
                editingService = detailService
                selectedDetailServiceId = null
                showFormDialog = true
            },
            onDismiss = { selectedDetailServiceId = null }
        )
    }

    if (showFormDialog) {
        ServiceFormDialog(
            initial = editingService,
            onImportLogoUri = onImportLogoUri,
            onFetchLogoFromUrl = onFetchLogoFromUrl,
            onCancelLogoFetch = onCancelLogoFetch,
            onDismiss = { showFormDialog = false },
            onSave = { name, website, desc, iconId, onError ->
                onSaveService(
                    editingService?.service?.id,
                    name,
                    website,
                    desc,
                    iconId,
                    false,
                    { showFormDialog = false },
                    onError
                )
            }
        )
    }

    if (duplicatePrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissDuplicatePrompt,
            title = { Text("Similar Service Name Exists") },
            text = {
                Text(
                    "A service named '${duplicatePrompt.conflictingServiceName}' already exists in your organizer. Would you like to go back and rename '${duplicatePrompt.pendingName}', or keep both as visually distinguishable services?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveService(
                            duplicatePrompt.existingId,
                            duplicatePrompt.pendingName,
                            duplicatePrompt.pendingWebsiteUrl,
                            duplicatePrompt.pendingDescription,
                            duplicatePrompt.pendingIconAssetId,
                            true,
                            { showFormDialog = false },
                            {}
                        )
                    },
                    modifier = Modifier.testTag("confirm_keep_duplicate_service_button")
                ) {
                    Text("Keep Visually Distinct Service")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissDuplicatePrompt) {
                    Text("Go Back & Rename")
                }
            }
        )
    }

    val targetDelete = deletingService
    if (targetDelete != null) {
        val otherServices = services.filter { it.service.id != targetDelete.service.id }
        SafeDeleteServiceDialog(
            serviceWithCount = targetDelete,
            availableTargetServices = otherServices,
            onDismiss = { deletingService = null },
            onConfirmResolution = { resolution, targetServiceId, onError ->
                onDeleteOrResolveService(
                    targetDelete.service.id,
                    resolution,
                    targetServiceId,
                    { deletingService = null },
                    onError
                )
            }
        )
    }
}

@Composable
private fun ServiceDirectoryCard(
    item: ServiceWithCount,
    onClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onEditClick: () -> Unit,
    onArchiveToggleClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("service_item_card_${item.service.name}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceLogoFrame(
                    serviceName = item.service.name,
                    asset = item.iconAsset,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.service.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.service.isArchived) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(Archived)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "${item.activeAccountCount} active account(s) • ${item.totalAccountCount} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!item.service.websiteUrl.isNullOrBlank()) {
                        Text(
                            text = item.service.websiteUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onEditClick, modifier = Modifier.testTag("edit_service_button")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${item.service.name}")
                }
                IconButton(onClick = onArchiveToggleClick) {
                    Icon(
                        imageVector = if (item.service.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = if (item.service.isArchived) "Unarchive service" else "Archive service"
                    )
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.testTag("delete_service_button")) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete ${item.service.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (!item.service.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.service.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onAddAccountClick,
                    modifier = Modifier.testTag("add_account_to_service_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Account to ${item.service.name}")
                }
            }
        }
    }
}

@Composable
private fun ServiceDetailDialog(
    serviceWithCount: ServiceWithCount,
    linkedAccounts: List<AccountWithDetails>,
    onAddAccount: () -> Unit,
    onEditService: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogoFrame(
                    serviceName = serviceWithCount.service.name,
                    asset = serviceWithCount.iconAsset,
                    size = 40.dp
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(serviceWithCount.service.name, style = MaterialTheme.typography.titleLarge)
                    serviceWithCount.service.websiteUrl?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                serviceWithCount.service.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "Associated Accounts (${linkedAccounts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (linkedAccounts.isEmpty()) {
                    Text(
                        text = "No accounts recorded under this service yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    linkedAccounts.forEach { acc ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.account.displayName, style = MaterialTheme.typography.titleSmall)
                                    val idText = acc.account.email ?: acc.account.username ?: "No email/username"
                                    Text(idText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AccountStatusBadge(status = acc.account.status, isArchived = acc.account.isArchived)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAddAccount) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Account")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEditService) { Text("Edit") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun ServiceFormDialog(
    initial: ServiceWithCount?,
    onImportLogoUri: (Uri, (ServiceAssetRecord) -> Unit, (String) -> Unit) -> Unit,
    onFetchLogoFromUrl: (String, (FetchedServiceLogoResult) -> Unit, (String) -> Unit) -> Unit,
    onCancelLogoFetch: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        websiteUrl: String?,
        description: String?,
        iconAssetId: String?,
        onError: (String) -> Unit
    ) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.service?.name.orEmpty()) }
    var websiteUrl by rememberSaveable { mutableStateOf(initial?.service?.websiteUrl.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.service?.description.orEmpty()) }
    var currentAsset by remember { mutableStateOf(initial?.iconAsset) }
    var isFetchingLogo by remember { mutableStateOf(false) }
    var fetchedHostStatus by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            onCancelLogoFetch()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onCancelLogoFetch()
            isFetchingLogo = false
            onImportLogoUri(
                uri,
                { imported ->
                    currentAsset = imported
                    fetchedHostStatus = null
                    errorMessage = null
                },
                { err -> errorMessage = err }
            )
        }
    }

    AlertDialog(
        onDismissRequest = {
            onCancelLogoFetch()
            onDismiss()
        },
        title = {
            Text(if (initial == null) "Create Custom Service" else "Edit Service")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Custom logo preview & actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ServiceLogoFrame(
                        serviceName = name.ifBlank { "Service" },
                        asset = currentAsset,
                        size = 56.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isFetchingLogo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_service_logo_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (currentAsset == null) "Choose Logo from Device" else "Change Logo from Device")
                        }
                        if (currentAsset != null) {
                            TextButton(
                                onClick = {
                                    currentAsset = null
                                    fetchedHostStatus = null
                                },
                                enabled = !isFetchingLogo,
                                modifier = Modifier.testTag("remove_service_logo_button")
                            ) {
                                Text("Remove Custom Logo")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Service Name *") },
                    placeholder = { Text("e.g. Fastmail, GitLab, Local Credit Union") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_name_input")
                )

                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = {
                        websiteUrl = it
                        fetchedHostStatus = null
                        errorMessage = null
                    },
                    label = { Text("Website URL (Optional)") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_website_input")
                )

                // Explicit user-triggered HTTPS Website Logo Fetch button & status
                OutlinedButton(
                    onClick = {
                        val targetUrl = websiteUrl.trim()
                        if (targetUrl.isEmpty()) {
                            errorMessage = "Enter a website URL first to fetch its logo."
                            return@OutlinedButton
                        }
                        isFetchingLogo = true
                        fetchedHostStatus = null
                        errorMessage = null
                        onFetchLogoFromUrl(
                            targetUrl,
                            { result ->
                                isFetchingLogo = false
                                currentAsset = result.asset
                                websiteUrl = result.normalizedWebsiteUrl
                                fetchedHostStatus = "Logo fetched & saved locally from ${result.sourceHost}"
                                errorMessage = null
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
                        .testTag("fetch_service_logo_button")
                ) {
                    if (isFetchingLogo) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("fetch_service_logo_loading_indicator"),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching Website Logo...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Fetch Logo from Website URL")
                    }
                }

                if (fetchedHostStatus != null) {
                    Text(
                        text = fetchedHostStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("service_logo_fetch_status_text")
                    )
                } else {
                    Text(
                        text = "When online, tap 'Fetch Logo from Website URL' to download the site's official icon over HTTPS and save it locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes (Optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_description_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("service_form_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCancelLogoFetch()
                    onSave(
                        name,
                        websiteUrl.takeIf { it.isNotBlank() },
                        description.takeIf { it.isNotBlank() },
                        currentAsset?.id
                    ) { err -> errorMessage = err }
                },
                enabled = !isFetchingLogo,
                modifier = Modifier.testTag("save_service_button")
            ) {
                Text("Save Service")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onCancelLogoFetch()
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
    availableTargetServices: List<ServiceWithCount>,
    onDismiss: () -> Unit,
    onConfirmResolution: (
        resolution: ServiceDeleteResolution,
        reassignTargetServiceId: String?,
        onError: (String) -> Unit
    ) -> Unit
) {
    val hasAccounts = serviceWithCount.totalAccountCount > 0
    var selectedResolution by remember {
        mutableStateOf(
            if (hasAccounts) {
                ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD
            } else {
                ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS
            }
        )
    }
    var selectedTargetServiceId by remember {
        mutableStateOf(availableTargetServices.firstOrNull()?.service?.id)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (hasAccounts) {
                    "Service Has ${serviceWithCount.totalAccountCount} Linked Account(s)"
                } else {
                    "Delete '${serviceWithCount.service.name}'?"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasAccounts) {
                    Text("This service has no associated account records and can be safely deleted.")
                } else {
                    Text(
                        text = "To protect your organizer integrity, deleting a service never silently cascade-deletes its accounts. Choose how to handle the ${serviceWithCount.totalAccountCount} account(s) linked to '${serviceWithCount.service.name}':",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedResolution = ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD }
                    ) {
                        RadioButton(
                            selected = selectedResolution == ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD,
                            onClick = { selectedResolution = ServiceDeleteResolution.ARCHIVE_SERVICE_INSTEAD }
                        )
                        Text(
                            text = "Archive service instead (Recommended — keeps all accounts intact)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (availableTargetServices.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedResolution = ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE }
                        ) {
                            RadioButton(
                                selected = selectedResolution == ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE,
                                onClick = { selectedResolution = ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE }
                            )
                            Text(
                                text = "Move accounts to another service, then delete '${serviceWithCount.service.name}'",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (selectedResolution == ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE) {
                            Column(modifier = Modifier.padding(start = 32.dp)) {
                                availableTargetServices.forEach { candidate ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedTargetServiceId = candidate.service.id }
                                    ) {
                                        RadioButton(
                                            selected = selectedTargetServiceId == candidate.service.id,
                                            onClick = { selectedTargetServiceId = candidate.service.id }
                                        )
                                        Text(candidate.service.name, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedResolution = ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS }
                    ) {
                        RadioButton(
                            selected = selectedResolution == ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS,
                            onClick = { selectedResolution = ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS }
                        )
                        Text(
                            text = "Permanently delete '${serviceWithCount.service.name}' AND all ${serviceWithCount.totalAccountCount} linked account(s)",
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
                    onConfirmResolution(selectedResolution, selectedTargetServiceId) { err ->
                        errorMessage = err
                    }
                },
                colors = if (selectedResolution == ServiceDeleteResolution.DELETE_SERVICE_AND_ACCOUNTS) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.testTag("confirm_service_delete_resolution_button")
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
