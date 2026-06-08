package app.lawnchair.allapps.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.ai.AiSortState
import app.lawnchair.ai.ProposedPage

@Composable
fun AiSortSheetContent(
    state: AiSortState,
    onApiKeySave: (String) -> Unit,
    onSortModeSelected: (sortUnassignedOnly: Boolean) -> Unit,
    onPageNameChange: (index: Int, name: String) -> Unit,
    onTogglePageIncluded: (index: Int) -> Unit,
    onReassignApp: (fromPageIndex: Int, appIndex: Int, toPageIndex: Int) -> Unit,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ai_sort_state",
    ) { currentState ->
        when (currentState) {
            is AiSortState.Idle -> {}
            is AiSortState.NeedApiKey -> AiApiKeySetupSheet(
                onSave = onApiKeySave,
                onDismiss = onDismiss,
            )
            is AiSortState.ChoosingMode -> AiSortEntrySheet(
                onSortUnassigned = { onSortModeSelected(true) },
                onFullResort = { onSortModeSelected(false) },
                onDismiss = onDismiss,
            )
            is AiSortState.Loading -> AiSortLoadingSheet()
            is AiSortState.Proposal -> AiSortConfirmationSheet(
                proposal = currentState,
                onPageNameChange = onPageNameChange,
                onTogglePageIncluded = onTogglePageIncluded,
                onReassignApp = onReassignApp,
                onApply = onApply,
                onDismiss = onDismiss,
            )
            is AiSortState.Error -> AiSortErrorSheet(
                message = currentState.message,
                onRetry = onRetry,
                onDismiss = onDismiss,
            )
            is AiSortState.Applied -> AiSortAppliedSheet(onDismiss = onDismiss)
        }
    }
}

@Composable
private fun AiApiKeySetupSheet(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "DeepSeek API Key",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "AI sorting requires a DeepSeek API key. Get yours at platform.deepseek.com",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("sk-...") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onSave(key) },
                enabled = key.trim().isNotBlank(),
            ) { Text("Save & Continue") }
        }
    }
}

@Composable
private fun AiSortEntrySheet(
    onSortUnassigned: () -> Unit,
    onFullResort: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "AI Sort Apps",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "You already have pages set up. How would you like to proceed?",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        SortOptionCard(
            title = "Sort new apps only",
            description = "AI organizes apps that aren't in any page yet.",
            onClick = onSortUnassigned,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SortOptionCard(
            title = "Full re-sort",
            description = "AI proposes a fresh organization for all apps. New pages will be added alongside your existing ones — delete old pages manually afterward if needed.",
            onClick = onFullResort,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SortOptionCard(title: String, description: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiSortLoadingSheet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Analyzing your apps...",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Fetching app descriptions, then asking DeepSeek to organize them",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiSortConfirmationSheet(
    proposal: AiSortState.Proposal,
    onPageNameChange: (Int, String) -> Unit,
    onTogglePageIncluded: (Int) -> Unit,
    onReassignApp: (fromPageIndex: Int, appIndex: Int, toPageIndex: Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val includedCount = proposal.pages.count { it.isIncluded }
    val pageNames = proposal.pages.map { it.name }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Sort Proposal",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${proposal.pages.size} pages suggested",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (proposal.isFallback) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Offline categorization — no AI key or network error",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(proposal.pages) { index, page ->
                ProposedPageCard(
                    page = page,
                    pageIndex = index,
                    pageNames = pageNames,
                    onNameChange = { name -> onPageNameChange(index, name) },
                    onToggleIncluded = { onTogglePageIncluded(index) },
                    onReassignApp = { appIndex, toPageIndex -> onReassignApp(index, appIndex, toPageIndex) },
                )
            }

            if (proposal.unassignedCount > 0) {
                item {
                    Text(
                        text = "${proposal.unassignedCount} apps left in All Apps",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onApply,
                enabled = includedCount > 0,
            ) {
                Text(if (includedCount > 0) "Create $includedCount pages" else "No pages selected")
            }
        }
    }
}

@Composable
private fun ProposedPageCard(
    page: ProposedPage,
    pageIndex: Int,
    pageNames: List<String>,
    onNameChange: (String) -> Unit,
    onToggleIncluded: () -> Unit,
    onReassignApp: (appIndex: Int, toPageIndex: Int) -> Unit,
) {
    val icon = LucideIconsMap[page.icon] ?: LucideIconsMap["Folder"]
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (page.isIncluded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Header row — tapping anywhere expands/collapses
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (page.isIncluded) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = page.name,
                        onValueChange = onNameChange,
                        enabled = page.isIncluded,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (page.isIncluded) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                        ),
                        singleLine = true,
                    )
                    Text(
                        text = "${page.apps.size} apps",
                        fontSize = 12.sp,
                        color = if (page.isIncluded) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))

                // Expand/collapse chevron
                val chevron = LucideIconsMap["ChevronDown"]
                if (chevron != null) {
                    Icon(
                        imageVector = chevron,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (expanded) 180f else 0f),
                        tint = if (page.isIncluded) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Include/exclude toggle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (page.isIncluded) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                        )
                        .clickable(onClick = onToggleIncluded),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (page.isIncluded) "✕" else "+",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (page.isIncluded) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            // Expanded app list
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = if (page.isIncluded) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    page.apps.forEachIndexed { appIndex, app ->
                        AppRow(
                            label = app.label,
                            appIndex = appIndex,
                            currentPageIndex = pageIndex,
                            pageNames = pageNames,
                            isPageIncluded = page.isIncluded,
                            onReassign = { toPageIndex -> onReassignApp(appIndex, toPageIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    label: String,
    appIndex: Int,
    currentPageIndex: Int,
    pageNames: List<String>,
    isPageIncluded: Boolean,
    onReassign: (toPageIndex: Int) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val textColor = if (isPageIncluded) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { showMenu = true }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        val moveIcon = LucideIconsMap["ArrowRight"]
        if (moveIcon != null) {
            Icon(
                imageVector = moveIcon,
                contentDescription = "Move to page",
                modifier = Modifier.size(14.dp),
                tint = textColor.copy(alpha = 0.5f),
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            Text(
                text = "Move to page",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            pageNames.forEachIndexed { index, name ->
                if (index != currentPageIndex) {
                    DropdownMenuItem(
                        text = { Text(name, fontSize = 14.sp) },
                        onClick = { showMenu = false; onReassign(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSortErrorSheet(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Sort failed",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun AiSortAppliedSheet(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Done!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your apps have been sorted into new pages.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onDismiss) { Text("Done") }
    }
}
