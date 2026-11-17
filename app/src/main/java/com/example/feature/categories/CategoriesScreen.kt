package com.example.feature.categories

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.EmptyStateCard
import com.example.domain.model.CategoryWithCount

private val OPTIONAL_STARTER_SUGGESTIONS = listOf(
    "Personal" to "Everyday personal accounts and subscriptions",
    "Work" to "Professional and career accounts",
    "Finance" to "Banking, taxes, and billing portals",
    "Developer" to "Code hosting, cloud consoles, and domains",
    "Shopping" to "Retail and delivery accounts"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    categories: List<CategoryWithCount>,
    onSaveCategory: (
        existingId: String?,
        name: String,
        description: String?,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onDeleteCategorySafely: (
        categoryId: String,
        reassignToCategoryId: String?,
        onCompleted: () -> Unit
    ) -> Unit,
    onBrowseCategoryAccounts: (String) -> Unit
) {
    var editingCategory by remember { mutableStateOf<CategoryWithCount?>(null) }
    var showFormDialog by rememberSaveable { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<CategoryWithCount?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("categories_screen")
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
                    text = "Categories",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Group accounts across different services by purpose or context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    editingCategory = null
                    showFormDialog = true
                },
                modifier = Modifier.testTag("create_category_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Category")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (categories.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Folder,
                        title = "No Categories Created Yet",
                        body = "Create your own custom categories or tap an optional starter suggestion below.",
                        actionLabel = "Create Custom Category",
                        actionTestTag = "categories_empty_create_button",
                        onActionClick = {
                            editingCategory = null
                            showFormDialog = true
                        }
                    )
                }
            }

            // Optional starter suggestions that are only added if the user explicitly clicks them
            val existingNames = categories.map { it.category.normalizedName }.toSet()
            val remainingSuggestions = OPTIONAL_STARTER_SUGGESTIONS.filter {
                it.first.lowercase() !in existingNames
            }
            if (remainingSuggestions.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Optional Starter Suggestions (Tap to add)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                remainingSuggestions.forEach { (sugName, sugDesc) ->
                                    AssistChip(
                                        onClick = {
                                            onSaveCategory(null, sugName, sugDesc, {}, {})
                                        },
                                        label = { Text("+ $sugName") },
                                        modifier = Modifier.testTag("starter_category_chip_$sugName")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(categories, key = { it.category.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBrowseCategoryAccounts(item.category.id) }
                        .testTag("category_card_${item.category.name}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.category.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${item.activeAccountCount} active account(s) • ${item.totalAccountCount} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!item.category.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.category.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                editingCategory = item
                                showFormDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${item.category.name}")
                        }
                        IconButton(
                            onClick = { deletingCategory = item },
                            modifier = Modifier.testTag("delete_category_button_${item.category.name}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete ${item.category.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        CategoryFormDialog(
            initial = editingCategory,
            onDismiss = { showFormDialog = false },
            onSave = { name, desc, onError ->
                onSaveCategory(
                    editingCategory?.category?.id,
                    name,
                    desc,
                    { showFormDialog = false },
                    onError
                )
            }
        )
    }

    val targetDelete = deletingCategory
    if (targetDelete != null) {
        val otherCategories = categories.filter { it.category.id != targetDelete.category.id }
        SafeDeleteCategoryDialog(
            categoryWithCount = targetDelete,
            otherCategories = otherCategories,
            onDismiss = { deletingCategory = null },
            onConfirmDelete = { reassignCategoryId ->
                onDeleteCategorySafely(targetDelete.category.id, reassignCategoryId) {
                    deletingCategory = null
                }
            }
        )
    }
}

@Composable
private fun CategoryFormDialog(
    initial: CategoryWithCount?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, onError: (String) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.category?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.category?.description.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Category Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_description_input")
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
                    onSave(name, description.takeIf { it.isNotBlank() }) { err ->
                        errorMessage = err
                    }
                },
                modifier = Modifier.testTag("save_category_button")
            ) {
                Text("Save Category")
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
private fun SafeDeleteCategoryDialog(
    categoryWithCount: CategoryWithCount,
    otherCategories: List<CategoryWithCount>,
    onDismiss: () -> Unit,
    onConfirmDelete: (reassignToCategoryId: String?) -> Unit
) {
    var selectedReassignId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Category '${categoryWithCount.category.name}'?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Deleting a category never deletes your accounts. Choose how to handle the ${categoryWithCount.totalAccountCount} account(s) currently assigned to this category:",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReassignId = null }
                ) {
                    RadioButton(
                        selected = selectedReassignId == null,
                        onClick = { selectedReassignId = null }
                    )
                    Text(
                        text = "Keep accounts uncategorized",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                otherCategories.forEach { other ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReassignId = other.category.id }
                    ) {
                        RadioButton(
                            selected = selectedReassignId == other.category.id,
                            onClick = { selectedReassignId = other.category.id }
                        )
                        Text(
                            text = "Reassign accounts to '${other.category.name}'",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(selectedReassignId) },
                modifier = Modifier.testTag("confirm_delete_category_button")
            ) {
                Text("Delete Category")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
