package app.lawnchair.allapps.pages

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.util.ComponentKey

data class AppListItem(
    val key: ComponentKey,
    val label: String,
    val icon: Bitmap? = null,
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

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
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
                .heightIn(max = 400.dp),
        ) {
            items(filteredApps, key = { it.key.toString() }) { app ->
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
                        onCheckedChange = null,
                    )
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .padding(end = 10.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }
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
    onCreate: (String, String?, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val nameExists by remember(existingPages) { derivedStateOf { existingPages.any { it.title?.toString() == name.trim() } } }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    var iconOnly by remember { mutableStateOf(false) }
    var hideFromAll by remember { mutableStateOf(false) }
    var pickerExpanded by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { pickerExpanded = !pickerExpanded }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tab Icon",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selectedIcon != null) {
                val iconVector = LucideIconsMap[selectedIcon]
                if (iconVector != null) {
                    androidx.compose.material3.Icon(
                        imageVector = iconVector,
                        contentDescription = selectedIcon,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(text = "None", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(text = "None", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (pickerExpanded) {
            IconPickerGrid(selectedIcon = selectedIcon, onSelect = { selectedIcon = it })
        }

        if (selectedIcon != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { iconOnly = !iconOnly }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = iconOnly,
                    onCheckedChange = { iconOnly = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Icon only (hide text label)",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { hideFromAll = !hideFromAll }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = hideFromAll,
                onCheckedChange = { hideFromAll = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Hide apps from \"All Apps\" tab",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

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
                        onCreate(trimmed, selectedIcon, iconOnly, hideFromAll)
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
    onRename: (String, String?, Boolean, Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(page.title?.toString() ?: "") }
    val nameExists by remember(existingPages, page.id) { derivedStateOf { existingPages.filter { it.id != page.id }.any { it.title?.toString() == name.trim() } } }
    var selectedIcon by remember { mutableStateOf<String?>(page.icon) }
    var iconOnly by remember { mutableStateOf(page.iconOnly) }
    var hideFromAll by remember { mutableStateOf(page.hideFromAll) }
    var pickerExpanded by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { pickerExpanded = !pickerExpanded }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tab Icon",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selectedIcon != null) {
                val iconVector = LucideIconsMap[selectedIcon]
                if (iconVector != null) {
                    androidx.compose.material3.Icon(
                        imageVector = iconVector,
                        contentDescription = selectedIcon,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(text = "None", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(text = "None", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (pickerExpanded) {
            IconPickerGrid(selectedIcon = selectedIcon, onSelect = { selectedIcon = it })
        }

        if (selectedIcon != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { iconOnly = !iconOnly }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = iconOnly,
                    onCheckedChange = { iconOnly = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Icon only (hide text label)",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { hideFromAll = !hideFromAll }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = hideFromAll,
                onCheckedChange = { hideFromAll = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Hide apps from \"All Apps\" tab",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

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
                        onRename(trimmed, selectedIcon, iconOnly, hideFromAll)
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
            LazyColumn {
                items(pages, key = { it.id }) { page ->
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

@Composable
fun IconStyleSheet(
    currentShowLabels: Boolean,
    onSelect: (showLabels: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "App Icon Style",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "How should apps appear in your drawer?",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            IconStyleOption(
                title = "Icon & Text",
                description = "App name shown below icon",
                selected = currentShowLabels,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(true) },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    repeat(2) {
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                            repeat(3) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                                    )
                                }
                            }
                        }
                        if (it == 0) Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
            IconStyleOption(
                title = "Icon Only",
                description = "Clean look, no labels",
                selected = !currentShowLabels,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(false) },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    repeat(2) {
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                )
                            }
                        }
                        if (it == 0) Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) { Text("Cancel") }
    }
}

@Composable
private fun IconPickerGrid(
    selectedIcon: String?,
    onSelect: (String?) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 224.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selectedIcon == null) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .clickable { onSelect(null) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "—",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedIcon == null) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        gridItems(LucideIconsList, key = { (name, _) -> name }) { (name, vector) ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selectedIcon == name) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .clickable { onSelect(name) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vector,
                    contentDescription = name,
                    modifier = Modifier.size(22.dp),
                    tint = if (selectedIcon == name) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IconStyleOption(
    title: String,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = modifier
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            preview()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
