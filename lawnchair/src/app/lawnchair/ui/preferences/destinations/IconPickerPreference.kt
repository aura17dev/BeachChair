package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.icons.iconpack.CustomIconPack
import app.lawnchair.icons.iconpack.IconPack
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.filter
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupDescription
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceSearchScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.verticalGridItems
import androidx.compose.foundation.layout.size
import app.lawnchair.ui.util.LazyGridLayout
import app.lawnchair.ui.util.resultSender
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun IconPickerPreference(
    packageName: String,
    componentKey: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconPack = remember {
        IconPackProvider.INSTANCE.get(context).getIconPackOrSystem(packageName)
    }
    if (iconPack == null) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        SideEffect {
            backDispatcher?.onBackPressed()
        }
        return
    }

    val targetComponentKey = remember(componentKey) {
        if (componentKey.isNotEmpty()) ComponentKey.fromString(componentKey) else null
    }

    val targetLabel = remember(targetComponentKey) {
        if (targetComponentKey != null) {
            try {
                val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
                val intent = Intent().setComponent(targetComponentKey.componentName)
                val lai = launcherApps.resolveActivity(intent, targetComponentKey.user)
                lai?.label?.toString()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val targetIcon by produceState<Drawable?>(initialValue = null, targetComponentKey) {
        if (targetComponentKey != null) {
            launch(Dispatchers.IO) {
                try {
                    val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
                    val intent = Intent().setComponent(targetComponentKey.componentName)
                    val lai = launcherApps.resolveActivity(intent, targetComponentKey.user)
                    if (lai != null) {
                        val loaded = LauncherAppState.getInstance(context).iconCache.getFullResIcon(lai.activityInfo)
                        if (loaded != null) {
                            value = loaded
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    // The icon pack's own appfilter mapping for this exact app, if any — surfaced as a
    // "Suggested" shortcut so the user doesn't have to scroll the whole pack to find it.
    // Only meaningful for real icon packs; SystemIconPack would just return the default icon.
    val suggestedItem by produceState<IconPickerItem?>(initialValue = null, iconPack, targetComponentKey) {
        if (targetComponentKey != null && iconPack is CustomIconPack) {
            launch(Dispatchers.IO) {
                try {
                    iconPack.loadBlocking()
                    val entry = iconPack.getIcon(targetComponentKey.componentName) ?: return@launch
                    value = IconPickerItem(
                        packPackageName = entry.packPackageName,
                        drawableName = entry.name,
                        label = targetLabel ?: entry.name,
                        type = entry.type,
                    )
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val onClickItem = resultSender<IconPickerItem>()

    val pickerComponent = remember {
        val launcherApps: LauncherApps = context.requireSystemService()
        launcherApps
            .getActivityList(iconPack.packPackageName, Process.myUserHandle()).firstOrNull()?.componentName
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val icon = it.data?.getParcelableExtra<Intent.ShortcutIconResource>(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
        ) ?: return@rememberLauncherForActivityResult
        val entry = (iconPack as CustomIconPack).createFromExternalPicker(icon) ?: return@rememberLauncherForActivityResult
        onClickItem(entry)
    }

    PreferenceSearchScaffold(
        value = searchQuery,
        modifier = modifier,
        onValueChange = { searchQuery = it },
        placeholder = {
            Text(
                text = iconPack.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            if (pickerComponent != null) {
                OverflowMenu {
                    DropdownMenuItem(onClick = {
                        val intent = Intent("com.novalauncher.THEME")
                            .addCategory("com.novalauncher.category.CUSTOM_ICON_PICKER")
                            .setComponent(pickerComponent)
                        pickerLauncher.launch(intent)
                        hideMenu()
                    }, text = {
                        Text(text = stringResource(id = R.string.icon_pack_external_picker))
                    })
                }
            }
        },
    ) {
        val scaffoldPadding = it

        IconPickerGrid(
            scaffoldPadding = scaffoldPadding,
            iconPack = iconPack,
            searchQuery = searchQuery,
            onClickItem = onClickItem,
            targetComponentKey = targetComponentKey,
            targetLabel = targetLabel,
            targetIcon = targetIcon,
            suggestedItem = suggestedItem,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconPickerGrid(
    scaffoldPadding: PaddingValues,
    iconPack: IconPack,
    searchQuery: String,
    modifier: Modifier = Modifier,
    onClickItem: (item: IconPickerItem) -> Unit,
    targetComponentKey: ComponentKey? = null,
    targetLabel: String? = null,
    targetIcon: Drawable? = null,
    suggestedItem: IconPickerItem? = null,
) {
    var loadFailed by remember { mutableStateOf(false) }
    val categoriesFlow = remember {
        iconPack.getAllIcons()
            .catch { loadFailed = true }
    }
    val categories by categoriesFlow.collectAsStateWithLifecycle(emptyList())
    val filteredCategories by remember(searchQuery) {
        derivedStateOf {
            categories.asSequence()
                .map { it.filter(searchQuery) }
                .filter { it.items.isNotEmpty() }
                .toList()
        }
    }

    val density = LocalDensity.current
    val gridLayout = remember {
        LazyGridLayout(
            minWidth = 56.dp,
            gapWidth = 16.dp,
            density = density,
        )
    }
    val numColumns by gridLayout.numColumns
    PreferenceLazyColumn(scaffoldPadding, modifier = modifier.then(gridLayout.onSizeChanged())) {
        if (targetComponentKey != null && targetLabel != null) {
            item {
                PreferenceTemplate(
                    title = { Text(text = targetLabel) },
                    description = { Text(text = stringResource(id = R.string.icon_picker_customizing_app)) },
                    startWidget = {
                        targetIcon?.let {
                            Image(
                                painter = rememberDrawablePainter(it),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                )
            }
        }
        if (numColumns != 0 && suggestedItem != null && searchQuery.isEmpty()) {
            stickyHeader {
                Text(
                    text = stringResource(id = R.string.icon_picker_suggested_category),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            verticalGridItems(
                modifier = Modifier
                    .padding(horizontal = 8.dp),
                items = listOf(suggestedItem),
                numColumns = numColumns,
            ) { _, item ->
                IconPreview(
                    iconPack = iconPack,
                    iconItem = item,
                ) {
                    onClickItem(item)
                }
            }
        }
        if (numColumns != 0) {
            filteredCategories.forEach { category ->
                stickyHeader {
                    Text(
                        text = category.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                verticalGridItems(
                    modifier = Modifier
                        .padding(horizontal = 8.dp),
                    items = category.items,
                    numColumns = numColumns,
                ) { _, item ->
                    IconPreview(
                        iconPack = iconPack,
                        iconItem = item,
                    ) {
                        onClickItem(item)
                    }
                }
            }
        }
        if (loadFailed) {
            item {
                PreferenceGroupDescription(
                    description = stringResource(id = R.string.icon_picker_load_failed),
                )
            }
        }
    }
}

@Composable
fun IconPreview(
    iconPack: IconPack,
    iconItem: IconPickerItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val drawable by produceState<Drawable?>(initialValue = null, iconPack, iconItem) {
        launch(Dispatchers.IO) {
            value = iconPack.getIcon(iconItem.toIconEntry(), 0)
        }
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Image(
            painter = rememberDrawablePainter(drawable),
            contentDescription = iconItem.drawableName,
            modifier = Modifier.aspectRatio(1f),
        )
    }
}
