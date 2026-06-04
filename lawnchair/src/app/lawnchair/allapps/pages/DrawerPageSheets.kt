package app.lawnchair.allapps.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.util.ComponentKey

data class AppListItem(
    val key: ComponentKey,
    val label: String,
)

@Composable
fun AddAppsToPageSheet(
    apps: List<AppListItem>,
    pageTitle: String,
    onConfirm: (Set<ComponentKey>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedKeys by remember { mutableStateOf<Set<ComponentKey>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = if (searchQuery.isBlank()) {
        apps
    } else {
        apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Add apps to $pageTitle",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
        ) {
            items(filteredApps) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedKeys = if (app.key in selectedKeys) {
                                selectedKeys - app.key
                            } else {
                                selectedKeys + app.key
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = app.key in selectedKeys,
                        onCheckedChange = {
                            selectedKeys = if (app.key in selectedKeys) {
                                selectedKeys - app.key
                            } else {
                                selectedKeys + app.key
                            }
                        },
                    )
                    Text(
                        text = app.label,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = { onConfirm(selectedKeys) },
                enabled = selectedKeys.isNotEmpty(),
            ) {
                Text("Add ${selectedKeys.size} apps")
            }
        }
    }
}

@Composable
fun CreatePageSheet(
    existingPages: List<FolderInfo>,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val nameExists = existingPages.any { it.title?.toString() == name.trim() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "New Page",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Page name") },
            singleLine = true,
            isError = nameExists && name.isNotBlank(),
            supportingText = if (nameExists && name.isNotBlank()) {
                { Text("A page with this name already exists") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty() && !nameExists) {
                        onCreate(trimmed)
                    }
                },
                enabled = name.trim().isNotEmpty() && !nameExists,
            ) {
                Text("Create")
            }
        }
    }
}

@Composable
fun RenamePageSheet(
    page: FolderInfo,
    existingPages: List<FolderInfo>,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(page.title?.toString() ?: "") }
    val nameExists = existingPages
        .filter { it.id != page.id }
        .any { it.title?.toString() == name.trim() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Edit Page",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Page name") },
            singleLine = true,
            isError = nameExists && name.isNotBlank(),
            supportingText = if (nameExists && name.isNotBlank()) {
                { Text("A page with this name already exists") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDelete() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Delete page",
                color = MaterialTheme.colorScheme.error,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${page.getContents().size} apps",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 13.sp,
            )
        }

        HorizontalDivider()

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty() && !nameExists) {
                        onRename(trimmed)
                    }
                },
                enabled = name.trim().isNotEmpty() && !nameExists,
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun MoveToPageSheet(
    pages: List<FolderInfo>,
    onCreatePage: () -> Unit,
    onSelectPage: (FolderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Move to Page",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (pages.isEmpty()) {
            Text(
                text = "No pages yet. Create one first.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            pages.forEach { page ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPage(page) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = page.title?.toString() ?: "Page",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${page.getContents().size}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                HorizontalDivider()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCreatePage() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "+ Create new page",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Cancel")
        }
    }
}
