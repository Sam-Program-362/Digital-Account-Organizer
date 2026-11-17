package com.example.feature.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.AccountStatusBadge
import com.example.core.designsystem.CategoryBadgeChip
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.OfflinePrivacyBanner
import com.example.core.designsystem.ServiceLogoFrame
import com.example.domain.model.AccountWithDetails
import com.example.domain.model.DashboardSummary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    isAppLockEnabled: Boolean,
    onCreateServiceClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onOpenAccountsForService: (String) -> Unit,
    onOpenAccountsForCategory: (String) -> Unit,
    onOpenAllAccounts: () -> Unit,
    onOpenBackup: () -> Unit,
    onLockNowClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OfflinePrivacyBanner(
                noticeText = stringResource(R.string.offline_organizer_notice)
            )
        }

        // Summary metrics grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    label = "Active Accounts",
                    count = summary.totalActiveAccounts,
                    icon = Icons.Default.Person,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_active_accounts"),
                    onClick = onOpenAllAccounts
                )
                MetricStatCard(
                    label = "Custom Services",
                    count = summary.totalServices,
                    icon = Icons.Default.Apps,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_custom_services"),
                    onClick = onCreateServiceClick
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    label = "Categories",
                    count = summary.totalCategories,
                    icon = Icons.Default.Folder,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_categories"),
                    onClick = onOpenAllAccounts
                )
                MetricStatCard(
                    label = "Archived Records",
                    count = summary.totalArchivedAccounts,
                    icon = Icons.Default.Backup,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_archived_accounts"),
                    onClick = onOpenAllAccounts
                )
            }
        }

        // Quick actions row
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
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCreateServiceClick,
                            modifier = Modifier.testTag("quick_action_create_service")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_create_service))
                        }
                        FilledTonalButton(
                            onClick = onAddAccountClick,
                            modifier = Modifier.testTag("quick_action_add_account")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_add_account))
                        }
                        OutlinedButton(
                            onClick = onOpenBackup,
                            modifier = Modifier.testTag("quick_action_backup")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Backup / Restore")
                        }
                        if (isAppLockEnabled) {
                            OutlinedButton(
                                onClick = onLockNowClick,
                                modifier = Modifier.testTag("quick_action_lock_now")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_lock_now))
                            }
                        }
                    }
                }
            }
        }

        // Services & Categories shortcuts
        if (summary.topServices.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Browse by Service",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        summary.topServices.forEach { svcWithCount ->
                            AssistChip(
                                onClick = { onOpenAccountsForService(svcWithCount.service.id) },
                                label = {
                                    Text("${svcWithCount.service.name} (${svcWithCount.activeAccountCount})")
                                },
                                leadingIcon = {
                                    ServiceLogoFrame(
                                        serviceName = svcWithCount.service.name,
                                        asset = svcWithCount.iconAsset,
                                        size = 22.dp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (summary.topCategories.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Browse by Category",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        summary.topCategories.forEach { catWithCount ->
                            AssistChip(
                                onClick = { onOpenAccountsForCategory(catWithCount.category.id) },
                                label = {
                                    Text("${catWithCount.category.name} (${catWithCount.activeAccountCount})")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Recent Accounts or First-Run Empty State
        if (summary.recentAccounts.isEmpty()) {
            item {
                val needServiceFirst = summary.totalServices == 0
                EmptyStateCard(
                    icon = if (needServiceFirst) Icons.Default.Apps else Icons.Default.Person,
                    title = if (needServiceFirst) {
                        "Start by Creating Your First Custom Service"
                    } else {
                        "No Account Records Yet"
                    },
                    body = if (needServiceFirst) {
                        "Digital Account Organizer never relies on a pre-bundled provider catalogue. Create a custom service (and optional logo) first, then attach as many accounts as you have for that service."
                    } else {
                        "You have ${summary.totalServices} custom service(s) ready. Add your first account record to track its label, identifier, category, and purpose."
                    },
                    actionLabel = if (needServiceFirst) "Create Custom Service" else "Add First Account",
                    actionTestTag = "dashboard_empty_primary_button",
                    onActionClick = if (needServiceFirst) onCreateServiceClick else onAddAccountClick
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recently Updated Accounts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onOpenAllAccounts) {
                        Text("View All")
                    }
                }
            }

            items(summary.recentAccounts, key = { it.account.id }) { item ->
                RecentAccountRowCard(
                    item = item,
                    onClick = { onOpenAccountsForService(item.service.id) }
                )
            }
        }
    }
}

@Composable
private fun MetricStatCard(
    label: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentAccountRowCard(
    item: AccountWithDetails,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServiceLogoFrame(
                serviceName = item.service.name,
                asset = item.serviceAsset,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(item.service.name)
                        val identifier = item.account.email ?: item.account.username
                        if (!identifier.isNullOrBlank()) {
                            append(" • ")
                            append(identifier)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountStatusBadge(status = item.account.status, isArchived = item.account.isArchived)
                item.category?.let { cat ->
                    CategoryBadgeChip(categoryName = cat.name)
                }
            }
        }
    }
}
