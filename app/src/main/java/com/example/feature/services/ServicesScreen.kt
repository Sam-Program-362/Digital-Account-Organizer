package com.example.feature.services

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.example.core.designsystem.FormSectionCard
import com.example.core.designsystem.FullScreenSubpageScaffold
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
        clearSavedPassword: Boolean,
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
    onDeleteServiceAccountPermanently: (String) -> Unit,
    onCredentialScreenActiveChange: (Boolean) -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showArchivedServices by rememberSaveable { mutableStateOf(false) }
    var deletingService by remember { mutableStateOf<ServiceWithCount?>(null) }

    var addingOrEditingForServiceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingServiceAccountId by rememberSaveable { mutableStateOf<String?>(null) }
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
            // When "Archived" chip is toggled ON, show ONLY archived services and hide active ones.
            val archiveOk = if (showArchivedServices) item.service.isArchived else !item.service.isArchived
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

    val isCredentialPageActive = viewingServiceAccount != null || addingOrEditingForServiceId != null
    LaunchedEffect(isCredentialPageActive) {
        onCredentialScreenActiveChange(isCredentialPageActive)
    }
    DisposableEffect(Unit) {
        onDispose {
            onCredentialScreenActiveChange(false)
        }
    }

    val editingServiceAccount = remember(serviceAccounts, editingServiceAccountId) {
        serviceAccounts.firstOrNull { it.serviceAccount.id == editingServiceAccountId }
    }

    val targetServiceForForm = remember(services, addingOrEditingForServiceId) {
        services.firstOrNull { it.service.id == addingOrEditingForServiceId }
    }

    val openEditServiceAccount: (ServiceAccountWithDetails) -> Unit = { entry ->
        editingServiceAccountId = entry.serviceAccount.id
        addingOrEditingForServiceId = entry.service.id
    }

    // Priority of full-screen pages inside Services tab:
    // 1. Add / Edit Service Account Page
    // 2. Service Account Detail Page
    // 3. Service Detail Page
    // 4. Services List Page
    val currentPageKey = when {
        targetServiceForForm != null -> "EDITOR_${targetServiceForForm.service.id}_${editingServiceAccountId.orEmpty()}"
        viewingServiceAccount != null -> "ENTRY_DETAIL_${viewingServiceAccount.serviceAccount.id}"
        detailService != null -> "SERVICE_DETAIL_${detailService.service.id}"
        else -> "SERVICES_LIST"
    }

    AnimatedContent(
        targetState = currentPageKey,
        transitionSpec = {
            if (targetState != "SERVICES_LIST" && initialState == "SERVICES_LIST") {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            } else if (targetState == "SERVICES_LIST") {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut())
            } else {
                fadeIn() togetherWith fadeOut()
            }
        },
        label = "services_subpage_transition"
    ) { pageKey ->
        when {
            pageKey.startsWith("EDITOR_") && targetServiceForForm != null -> {
                ServiceAccountEditorDialog(
                    serviceWithCount = targetServiceForForm,
                    initialEntry = editingServiceAccount,
                    standaloneAccounts = standaloneAccounts.filter { !it.account.isArchived },
                    categories = categories,
                    onDismiss = {
                        addingOrEditingForServiceId = null
                        editingServiceAccountId = null
                    },
                    onSave = { linkedAccId, displayName, email, username, password, clearSavedPassword, categoryId, purpose, recEmail, recPhone, webUrl, notes, status, onError ->
                        onSaveServiceAccount(
                            editingServiceAccount?.serviceAccount?.id,
                            targetServiceForForm.service.id,
                            linkedAccId,
                            displayName,
                            email,
                            username,
                            password,
                            clearSavedPassword,
                            categoryId,
                            purpose,
                            recEmail,
                            recPhone,
                            webUrl,
                            notes,
                            status,
                            {
                                addingOrEditingForServiceId = null
                                editingServiceAccountId = null
                            },
                            onError
                        )
                    }
                )
            }

            pageKey.startsWith("ENTRY_DETAIL_") && viewingServiceAccount != null -> {
                ServiceAccountDetailPage(
                    item = viewingServiceAccount,
                    onDecryptSecret = onDecryptSecret,
                    onRequestVerifiedAction = onRequestVerifiedAction,
                    onBack = { viewingServiceAccountId = null },
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
                    },
                    onDelete = {
                        deletingServiceAccount = viewingServiceAccount
                    }
                )
            }

            pageKey.startsWith("SERVICE_DETAIL_") && detailService != null -> {
                val entriesForService = remember(serviceAccounts, detailService.service.id) {
                    serviceAccounts.filter { it.service.id == detailService.service.id }
                }
                ServiceDetailPage(
                    serviceWithCount = detailService,
                    serviceAccounts = entriesForService,
                    onBack = { onSelectServiceForDetail(null) },
                    onEditService = { onEditServiceClick(detailService) },
                    onToggleArchiveService = {
                        onToggleArchiveService(
                            detailService.service.id,
                            !detailService.service.isArchived
                        )
                    },
                    onDeleteService = { deletingService = detailService },
                    onAddServiceAccount = {
                        editingServiceAccountId = null
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

            else -> {
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
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select any service to open its dedicated page and manage Service Account entries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onCreateServiceClick,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("create_service_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Service")
                        }
                    }

                    // Separate search bar + Archived-only filter chip
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("services_search_input")
                        )
                        FilterChip(
                            selected = showArchivedServices,
                            onClick = { showArchivedServices = !showArchivedServices },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Archived") },
                            modifier = Modifier.testTag("services_archived_filter_chip")
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
                                    icon = if (showArchivedServices) Icons.Default.Archive else Icons.Default.Apps,
                                    title = when {
                                        showArchivedServices && searchQuery.isBlank() -> "No Archived Services"
                                        showArchivedServices -> "No Matching Archived Services"
                                        searchQuery.isBlank() -> "No Services Created Yet"
                                        else -> "No Matching Services"
                                    },
                                    body = if (showArchivedServices) {
                                        "Only archived services are shown while the Archived filter is active. Tap 'Archived' again to return to your active services."
                                    } else {
                                        "Create a Service (such as GitHub, Google, or Proton) with a custom or fetched website logo, then add your Service Account entries inside it."
                                    },
                                    actionLabel = if (showArchivedServices) "Show Active Services" else "Create First Service",
                                    actionTestTag = "services_empty_create_button",
                                    onActionClick = {
                                        if (showArchivedServices) {
                                            showArchivedServices = false
                                        } else {
                                            onCreateServiceClick()
                                        }
                                    }
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
                                        editingServiceAccountId = null
                                        addingOrEditingForServiceId = item.service.id
                                    },
                                    onViewServiceAccount = { entry ->
                                        onSelectServiceForDetail(item.service.id)
                                        viewingServiceAccountId = entry.serviceAccount.id
                                    },
                                    onEditService = { onEditServiceClick(item) },
                                    onToggleArchiveService = {
                                        onToggleArchiveService(item.service.id, !item.service.isArchived)
                                    },
                                    onDeleteService = { deletingService = item }
                                )
                            }
                        }
                    }
                }
            }
        }
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
                    {
                        if (selectedServiceIdForDetail == targetDeleteSvc.service.id) {
                            onSelectServiceForDetail(null)
                        }
                        deletingService = null
                    },
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
                if (viewingServiceAccountId == targetDeleteEntry.serviceAccount.id) {
                    viewingServiceAccountId = null
                }
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
    onToggleArchiveService: () -> Unit,
    onDeleteService: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .testTag("service_card_${item.service.name}"),
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
                    ServiceLogoFrame(
                        serviceName = item.service.name,
                        asset = item.iconAsset,
                        size = 48.dp
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = item.service.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.service.isArchived) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "Archived",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
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
                            text = "${item.activeAccountCount} active • ${item.totalAccountCount} total entry(s)",
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
                    IconButton(onClick = onToggleArchiveService) {
                        Icon(
                            imageVector = if (item.service.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (item.service.isArchived) "Unarchive ${item.service.name}" else "Archive ${item.service.name}"
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
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
                if (entries.size > 3) {
                    Text(
                        text = "View all ${entries.size} entries on Service page →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Full-Screen Service Detail Page replacing the old cramped ServiceDetailModal AlertDialog.
 * Features a hero service header, proportional compact "+ Add Entry" pill button, and rich entry cards.
 */
@Composable
private fun ServiceDetailPage(
    serviceWithCount: ServiceWithCount,
    serviceAccounts: List<ServiceAccountWithDetails>,
    onBack: () -> Unit,
    onEditService: () -> Unit,
    onToggleArchiveService: () -> Unit,
    onDeleteService: () -> Unit,
    onAddServiceAccount: () -> Unit,
    onViewServiceAccount: (ServiceAccountWithDetails) -> Unit,
    onEditServiceAccount: (ServiceAccountWithDetails) -> Unit,
    onDeleteServiceAccount: (ServiceAccountWithDetails) -> Unit
) {
    FullScreenSubpageScaffold(
        title = serviceWithCount.service.name,
        subtitle = "${serviceAccounts.size} service account entry(s)",
        onBack = onBack,
        backButtonTestTag = "service_detail_back_button",
        actions = {
            OutlinedButton(
                onClick = onToggleArchiveService,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (serviceWithCount.service.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (serviceWithCount.service.isArchived) "Unarchive" else "Archive",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            OutlinedButton(
                onClick = onEditService,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("service_detail_edit_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit", style = MaterialTheme.typography.labelMedium)
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("service_detail_page"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            ServiceLogoFrame(
                                serviceName = serviceWithCount.service.name,
                                asset = serviceWithCount.iconAsset,
                                size = 58.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = serviceWithCount.service.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (serviceWithCount.service.isArchived) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "Archived",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (!serviceWithCount.service.websiteUrl.isNullOrBlank()) {
                                    Text(
                                        text = serviceWithCount.service.websiteUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${serviceWithCount.activeAccountCount} active • ${serviceAccounts.size} total entry(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!serviceWithCount.service.description.isNullOrBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Text(
                                text = serviceWithCount.service.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section Header with sleek compact "+ Add Entry" button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Service Account Entries",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap an entry to view credentials & notes, or add a new account",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = onAddServiceAccount,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("service_detail_add_entry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add Account",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (serviceAccounts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.PersonAdd,
                        title = "No Accounts in ${serviceWithCount.service.name} Yet",
                        body = "Link one of your existing standalone Accounts or add a service-specific username/email with its own optional encrypted password.",
                        actionLabel = "Add First Service Account",
                        actionTestTag = "service_detail_empty_add_button",
                        onActionClick = onAddServiceAccount
                    )
                }
            } else {
                items(serviceAccounts, key = { it.serviceAccount.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewServiceAccount(entry) }
                            .testTag("service_entry_card_${entry.serviceAccount.displayName}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = entry.serviceAccount.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    AccountStatusBadge(
                                        status = entry.serviceAccount.status,
                                        isArchived = entry.serviceAccount.isArchived
                                    )
                                    entry.category?.let { CategoryBadgeChip(categoryName = it.name) }
                                }
                                Text(
                                    text = entry.effectiveIdentifier,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (entry.linkedAccount != null) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Linked: ${entry.linkedAccount.displayName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "Custom Service Account",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (entry.serviceAccount.hasPassword || entry.linkedAccount?.hasPassword == true) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDeleteService,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove or Reassign Service")
                }
            }
        }
    }
}

/**
 * Full-screen Service Account Add / Edit Page replacing the old AlertDialog.
 * Never pre-fills or exposes an existing saved password when editing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceAccountEditorDialog(
    serviceWithCount: ServiceWithCount,
    initialEntry: ServiceAccountWithDetails?,
    standaloneAccounts: List<AccountWithDetails>,
    categories: List<CategoryWithCount>,
    onDismiss: () -> Unit,
    onSave: (
        linkedAccountId: String?,
        displayName: String,
        email: String?,
        username: String?,
        password: String?,
        clearSavedPassword: Boolean,
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
    val hasExistingSavedPassword = initialEntry?.serviceAccount?.hasPassword == true

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
    // Security rule: NEVER load or pre-fill an existing saved password into the edit text field
    var password by rememberSaveable { mutableStateOf("") }
    var clearSavedPassword by rememberSaveable { mutableStateOf(false) }
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
    var statusName by rememberSaveable {
        mutableStateOf((initialEntry?.serviceAccount?.status ?: AccountStatus.ACTIVE).name)
    }
    val status = remember(statusName) {
        AccountStatus.entries.firstOrNull { it.name == statusName } ?: AccountStatus.ACTIVE
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val triggerSave: () -> Unit = {
        val linkedId = if (useLinkedAccount) selectedLinkedAccountId else null
        onSave(
            linkedId,
            displayName,
            email.takeIf { it.isNotBlank() },
            username.takeIf { it.isNotBlank() },
            password.takeIf { it.isNotBlank() },
            clearSavedPassword,
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
    }

    FullScreenSubpageScaffold(
        title = if (initialEntry == null) {
            "Add Account to ${serviceWithCount.service.name}"
        } else {
            "Edit ${serviceWithCount.service.name} Entry"
        },
        subtitle = if (initialEntry == null) {
            "Link a standalone account or create a service-specific credential"
        } else {
            "Updating ${initialEntry.serviceAccount.displayName}"
        },
        onBack = onDismiss,
        backButtonTestTag = "service_account_editor_back_button",
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(
                onClick = triggerSave,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("save_service_account_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save Entry")
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

            FormSectionCard(
                title = "Account Connection Mode",
                subtitle = "Choose whether this service uses a standalone Account or a custom service login",
                icon = Icons.Default.Link
            ) {
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
            }

            FormSectionCard(
                title = "Service Entry Identity",
                subtitle = "Label and username or email for ${serviceWithCount.service.name}",
                icon = Icons.Default.Person
            ) {
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
            }

            FormSectionCard(
                title = "Service Password Manager (Optional)",
                subtitle = if (hasExistingSavedPassword) {
                    "Existing service password stays hidden and safe. Leave blank to keep it unchanged."
                } else if (useLinkedAccount) {
                    "Leave blank to inherit the linked standalone Account's password, or set a service-specific password"
                } else {
                    "Optional service-specific password encrypted with AES-256-GCM"
                },
                icon = Icons.Default.Key
            ) {
                PasswordManagerFieldEditor(
                    password = password,
                    onPasswordChange = { password = it },
                    label = if (useLinkedAccount) {
                        "Service-Specific Password"
                    } else {
                        "Service Password (Encrypted)"
                    },
                    isOptional = true,
                    hasExistingSavedPassword = hasExistingSavedPassword,
                    clearSavedPassword = clearSavedPassword,
                    onClearSavedPasswordChange = { clearSavedPassword = it },
                    testTag = "service_account_password_input"
                )
            }

            FormSectionCard(
                title = "Classification & Details",
                subtitle = "Status, category, and recovery details",
                icon = Icons.Default.Folder
            ) {
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
                            onClick = { statusName = option.name },
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
                    Text("Save Entry")
                }
            }
        }
    }
}

/**
 * Full-screen Service Account Entry Detail Page replacing the old AlertDialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceAccountDetailPage(
    item: ServiceAccountWithDetails,
    onDecryptSecret: (String?) -> String?,
    onRequestVerifiedAction: (onVerified: () -> Unit) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    FullScreenSubpageScaffold(
        title = item.serviceAccount.displayName,
        subtitle = "${item.service.name} • ${item.effectiveIdentifier}",
        onBack = onBack,
        backButtonTestTag = "service_account_detail_back_button",
        actions = {
            OutlinedButton(
                onClick = onToggleArchive,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (item.serviceAccount.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (item.serviceAccount.isArchived) "Unarchive" else "Archive",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("service_account_detail_edit_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit", style = MaterialTheme.typography.labelMedium)
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("service_account_detail_page"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            ServiceLogoFrame(
                                serviceName = item.service.name,
                                asset = item.serviceAsset,
                                size = 54.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.serviceAccount.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${item.service.name} • ${item.effectiveIdentifier}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AccountStatusBadge(
                                status = item.serviceAccount.status,
                                isArchived = item.serviceAccount.isArchived
                            )
                            item.category?.let { CategoryBadgeChip(categoryName = it.name) }
                        }

                        item.linkedAccount?.let { linked ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AccountAvatarFrame(
                                        displayName = linked.displayName,
                                        identifier = linked.primaryIdentifier,
                                        email = linked.email,
                                        avatarColorHex = linked.avatarColorHex,
                                        size = 36.dp
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

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
                        if (!item.serviceAccount.websiteUrl.isNullOrBlank()) {
                            Text(
                                text = "Login URL: ${item.serviceAccount.websiteUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                val hasOwnPassword = item.serviceAccount.hasPassword
                val hasLinkedPassword = item.linkedAccount?.hasPassword == true

                FormSectionCard(
                    title = "Password Manager Vault",
                    subtitle = "Verify your identity to reveal or copy credentials and protected notes",
                    icon = Icons.Default.Lock
                ) {
                    if (!hasOwnPassword && !hasLinkedPassword && !item.serviceAccount.hasNotes) {
                        Text(
                            text = "No password or protected notes saved for this entry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
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
                }
            }

            item {
                OutlinedButton(
                    onClick = onDelete,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Service Account Entry")
                }
            }
        }
    }
}

/**
 * Full-screen Service Create / Edit Page replacing the old AlertDialog.
 */
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

    val handleDismiss: () -> Unit = {
        onCancelWebsiteLogoFetch()
        onDismiss()
    }

    val triggerSave: () -> Unit = {
        onSave(
            initialService?.service?.id,
            name,
            websiteUrl.takeIf { it.isNotBlank() },
            description.takeIf { it.isNotBlank() },
            currentAsset?.id
        ) { err ->
            errorMessage = err
        }
    }

    FullScreenSubpageScaffold(
        title = if (initialService == null) "Create Service" else "Edit Service",
        subtitle = if (initialService == null) {
            "Define a service with a custom or website-fetched logo"
        } else {
            "Updating ${initialService.service.name}"
        },
        onBack = handleDismiss,
        backButtonTestTag = "service_editor_back_button",
        actions = {
            TextButton(onClick = handleDismiss) {
                Text("Cancel")
            }
            Button(
                onClick = triggerSave,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("save_service_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save Service")
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

            if (statusMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            FormSectionCard(
                title = "Service Identity & Website",
                subtitle = "Service name, HTTPS URL, and optional description",
                icon = Icons.Default.Apps
            ) {
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

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes (Optional)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_description_input")
                )
            }

            FormSectionCard(
                title = "Service Logo",
                subtitle = "Fetch the official icon directly from the website URL or upload an image",
                icon = Icons.Default.Image
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ServiceLogoFrame(
                        serviceName = name.ifBlank { "Service" },
                        asset = currentAsset,
                        size = 64.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (currentAsset != null) "Custom Logo Attached" else "Monogram Fallback Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Logos are sanitized, re-encoded to PNG, and stored offline inside encrypted app storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
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
                            .weight(1f)
                            .testTag("fetch_website_logo_button")
                    ) {
                        if (isFetchingLogo) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Fetching...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fetch Logo from URL")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }.onFailure {
                                errorMessage = "System photo picker is unavailable on this device. Try 'Fetch Logo from URL'."
                            }
                        },
                        modifier = Modifier.testTag("pick_service_logo_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Image")
                    }
                }

                if (currentAsset != null) {
                    TextButton(onClick = { currentAsset = null }) {
                        Text("Clear Custom Logo")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = handleDismiss,
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
                    Text("Save Service")
                }
            }
        }
    }
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
