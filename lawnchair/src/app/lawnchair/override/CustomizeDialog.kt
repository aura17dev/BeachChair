package app.lawnchair.override

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.gestures.type.GestureType
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.launcher
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.components.AppGesturePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.navigation.SelectIcon
import app.lawnchair.ui.util.addIfNotNull
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import android.content.ContentValues
import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import app.lawnchair.icons.picker.IconPickerItem
import android.app.Activity

data class DbShortcut(
    val id: Int,
    val container: Int,
    val hasCustomIcon: Boolean
)

data class DbShortcutState(
    val folderMap: Map<Int, Int>,
    val shortcuts: List<DbShortcut>
)

object CustomizeDraft {
    var stagedIconDraft: IconPickerItem? = null
    var stagedResetDraft: Boolean = false
    var hasDraft: Boolean = false

    fun clear() {
        stagedIconDraft = null
        stagedResetDraft = false
        hasDraft = false
    }
}

@Composable
fun CustomizeDialog(
    icon: Drawable,
    title: String,
    onTitleChange: (String) -> Unit,
    defaultTitle: String,
    launchSelectIcon: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .navigationBarsOrDisplayCutoutPadding()
            .fillMaxWidth(),
    ) {
        val iconPainter = rememberDrawablePainter(drawable = icon)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 24.dp)
                .clip(MaterialTheme.shapes.small)
                .addIfNotNull(launchSelectIcon) {
                    clickable(onClick = it)
                }
                .padding(all = 8.dp),
        ) {
            Image(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
            )
            if (launchSelectIcon != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(12.dp),
                    )
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            trailingIcon = {
                if (title != defaultTitle) {
                    ClickableIcon(
                        painter = painterResource(id = R.drawable.ic_undo),
                        onClick = { onTitleChange(defaultTitle) },
                    )
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large,
            label = { Text(text = stringResource(id = R.string.label)) },
            isError = title.isEmpty(),
        )
        content?.invoke()
    }
}

@Composable
fun CustomizeAppDialog(
    icon: Drawable,
    defaultTitle: String,
    componentKey: ComponentKey,
    shortcutId: Int = -1,
    shortcutContainer: Int = -1,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val prefs = preferenceManager()
    val preferenceManager2 = preferenceManager2()
    val showComponentNames by preferenceManager2.showComponentNames.asState()
    val hiddenApps by preferenceManager2.hiddenApps.asState()
    val adapter = preferenceManager2.hiddenApps.getAdapter()
    val context = LocalContext.current
    var title by remember {
        mutableStateOf(prefs.customAppName[componentKey] ?: defaultTitle)
    }
    val launcherAppState = LauncherAppState.getInstance(context)

    val route = SelectIcon(componentKey.toString(), shortcutId)

    Log.d("CustomizeDialog", route.toString())

    var stagedIconItem by remember { mutableStateOf<IconPickerItem?>(null) }
    var resetDefault by remember { mutableStateOf(false) }

    val openIconPicker = {
        CustomizeDraft.clear()
        val intent = PreferenceActivity.createIntent(context, route)
        context.startActivity(intent)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (CustomizeDraft.hasDraft) {
                    if (CustomizeDraft.stagedResetDraft) {
                        resetDefault = true
                        stagedIconItem = null
                    } else {
                        val draft = CustomizeDraft.stagedIconDraft
                        if (draft != null) {
                            stagedIconItem = draft
                            resetDefault = false
                        }
                    }
                    CustomizeDraft.clear()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Observe the override for this component so the preview refreshes immediately when
    // the user picks a new icon from the icon picker and returns to the dialog.
    val repo = IconOverrideRepository.INSTANCE.get(context)
    val overrideItem by repo.observeTarget(componentKey).collectAsStateWithLifecycle(initialValue = null)
    val iconPackProvider = remember { IconPackProvider.INSTANCE.get(context) }
    val currentIcon by produceState<Drawable>(initialValue = icon, overrideItem, stagedIconItem, resetDefault) {
        if (resetDefault) {
            launch(Dispatchers.IO) {
                try {
                    val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
                    val intent = Intent().setComponent(componentKey.componentName)
                    val lai = launcherApps.resolveActivity(intent, componentKey.user)
                    if (lai != null) {
                        val loaded = LauncherAppState.getInstance(context).iconCache.getFullResIcon(lai.activityInfo)
                        if (loaded != null) {
                            value = loaded
                        }
                    }
                } catch (e: Exception) {
                    value = icon
                }
            }
        } else if (stagedIconItem != null) {
            val item = stagedIconItem!!
            launch(Dispatchers.IO) {
                val loaded = iconPackProvider.getDrawable(item.toIconEntry(), 0, componentKey.user)
                if (loaded != null) value = loaded
            }
        } else {
            val item = overrideItem?.iconPickerItem
            if (item != null) {
                launch(Dispatchers.IO) {
                    val loaded = iconPackProvider.getDrawable(item.toIconEntry(), 0, componentKey.user)
                    if (loaded != null) value = loaded
                }
            } else {
                if (shortcutId != -1) {
                    launch(Dispatchers.IO) {
                        val dbController = LauncherAppState.getInstance(context).model.modelDbController
                        dbController.query(
                            arrayOf(com.android.launcher3.LauncherSettings.Favorites.ICON),
                            "_id = ?",
                            arrayOf(shortcutId.toString()),
                            null
                        ).use { cursor ->
                            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                                val blob = cursor.getBlob(0)
                                val bitmap = android.graphics.BitmapFactory.decodeByteArray(blob, 0, blob.size)
                                if (bitmap != null) {
                                    value = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                                }
                            } else {
                                value = icon
                            }
                        }
                    }
                } else {
                    value = icon
                }
            }
        }
    }

    val dbShortcutState by produceState<DbShortcutState?>(initialValue = null) {
        launch(Dispatchers.IO) {
            try {
                val dbController = LauncherAppState.getInstance(context).model.modelDbController
                val userSerial = dbController.getSerialNumberForUser(componentKey.user)
                val folderMap = mutableMapOf<Int, Int>()
                val matchingShortcuts = mutableListOf<DbShortcut>()
                
                dbController.query(
                    arrayOf(
                        com.android.launcher3.LauncherSettings.Favorites._ID,
                        com.android.launcher3.LauncherSettings.Favorites.CONTAINER,
                        com.android.launcher3.LauncherSettings.Favorites.INTENT,
                        com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID,
                        com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE,
                        com.android.launcher3.LauncherSettings.Favorites.ICON
                    ),
                    null,
                    null,
                    null
                ).use { cursor ->
                    val idCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites._ID)
                    val containerCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.CONTAINER)
                    val intentCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.INTENT)
                    val profileIdCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID)
                    val itemTypeCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE)
                    val iconCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.ICON)
                    
                    val folderType = com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
                    
                    while (cursor.moveToNext()) {
                        val itemType = cursor.getInt(itemTypeCol)
                        val id = cursor.getInt(idCol)
                        val container = cursor.getInt(containerCol)
                        if (itemType == folderType) {
                            folderMap[id] = container
                        } else {
                            val profileId = cursor.getLong(profileIdCol)
                            if (profileId == userSerial) {
                                val intentStr = cursor.getString(intentCol)
                                if (!intentStr.isNullOrEmpty()) {
                                    try {
                                        val intent = Intent.parseUri(intentStr, 0)
                                        if (intent.component == componentKey.componentName) {
                                            val hasCustomIcon = !cursor.isNull(iconCol)
                                            matchingShortcuts.add(DbShortcut(id, container, hasCustomIcon))
                                        }
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    }
                }
                
                value = DbShortcutState(folderMap, matchingShortcuts)
            } catch (e: Exception) {
                Log.e("CustomizeDialog", "Error loading shortcuts", e)
            }
        }
    }

    var applyToDock by remember { mutableStateOf(false) }
    var applyToHomeScreen by remember { mutableStateOf(false) }
    var applyToAppDrawer by remember { mutableStateOf(false) }
    var statesInitialized by remember { mutableStateOf(false) }

    val dockContainer = com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
    val desktopContainer = com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP

    androidx.compose.runtime.LaunchedEffect(dbShortcutState) {
        val state = dbShortcutState
        if (state != null && !statesInitialized) {
            fun resolveContainer(container: Int): Int {
                if (container == dockContainer || container == desktopContainer) return container
                return state.folderMap[container] ?: container
            }
            
            val hasDockCustomIcon = state.shortcuts.any { resolveContainer(it.container) == dockContainer && it.hasCustomIcon }
            val hasHomeScreenCustomIcon = state.shortcuts.any { resolveContainer(it.container) == desktopContainer && it.hasCustomIcon }
            
            val sourceIsDock = shortcutContainer == dockContainer || state.folderMap[shortcutContainer] == dockContainer
            val sourceIsHomeScreen = shortcutContainer == desktopContainer || state.folderMap[shortcutContainer] == desktopContainer
            val sourceIsAppDrawer = !sourceIsDock && !sourceIsHomeScreen
            
            applyToDock = sourceIsDock || hasDockCustomIcon
            applyToHomeScreen = sourceIsHomeScreen || hasHomeScreenCustomIcon
            applyToAppDrawer = sourceIsAppDrawer || repo.overridesMap.containsKey(componentKey)
            
            statesInitialized = true
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val onApplyClick: () -> Unit = {
        val currentTitle = title
        val finalApplyToDock = applyToDock
        val finalApplyToHomeScreen = applyToHomeScreen
        val finalApplyToAppDrawer = applyToAppDrawer
        val finalStagedIconItem = stagedIconItem
        val finalResetDefault = resetDefault
        val applicationContext = context.applicationContext
        
        coroutineScope.launch(Dispatchers.Main) {
            val previousTitle = prefs.customAppName[componentKey]
            val newTitle = if (currentTitle != defaultTitle) currentTitle else null
            if (newTitle != previousTitle) {
                prefs.customAppName[componentKey] = newTitle
                launcherAppState.model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
            }
            
            com.android.launcher3.util.Executors.MODEL_EXECUTOR.execute {
                try {
                    val dbController = LauncherAppState.getInstance(applicationContext).model.modelDbController
                    val userSerial = dbController.getSerialNumberForUser(componentKey.user)
                    val folderMap = mutableMapOf<Int, Int>()
                    val matchingShortcuts = mutableListOf<DbShortcut>()
                    
                    dbController.query(
                        arrayOf(
                            com.android.launcher3.LauncherSettings.Favorites._ID,
                            com.android.launcher3.LauncherSettings.Favorites.CONTAINER,
                            com.android.launcher3.LauncherSettings.Favorites.INTENT,
                            com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID,
                            com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE,
                            com.android.launcher3.LauncherSettings.Favorites.ICON
                        ),
                        null,
                        null,
                        null
                    ).use { cursor ->
                        val idCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites._ID)
                        val containerCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.CONTAINER)
                        val intentCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.INTENT)
                        val profileIdCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID)
                        val itemTypeCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE)
                        val iconCol = cursor.getColumnIndex(com.android.launcher3.LauncherSettings.Favorites.ICON)
                        
                        val folderType = com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
                        
                        while (cursor.moveToNext()) {
                            val itemType = cursor.getInt(itemTypeCol)
                            val id = cursor.getInt(idCol)
                            val container = cursor.getInt(containerCol)
                            if (itemType == folderType) {
                                folderMap[id] = container
                            } else {
                                val profileId = cursor.getLong(profileIdCol)
                                if (profileId == userSerial) {
                                    val intentStr = cursor.getString(intentCol)
                                    if (!intentStr.isNullOrEmpty()) {
                                        try {
                                            val intent = Intent.parseUri(intentStr, 0)
                                            if (intent.component == componentKey.componentName) {
                                                val hasCustomIcon = !cursor.isNull(iconCol)
                                                matchingShortcuts.add(DbShortcut(id, container, hasCustomIcon))
                                            }
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    fun resolveContainer(container: Int): Int {
                        if (container == dockContainer || container == desktopContainer) return container
                        return folderMap[container] ?: container
                    }
                    
                    var iconBlob: ByteArray? = null
                    if (finalResetDefault) {
                        iconBlob = null
                    } else if (finalStagedIconItem != null) {
                        val item = finalStagedIconItem
                        val iconPackProvider = IconPackProvider.INSTANCE.get(applicationContext)
                        val drawable = iconPackProvider.getDrawable(item.toIconEntry(), 0, componentKey.user)
                        if (drawable != null) {
                            try {
                                val bitmap = drawable.toBitmap()
                                val stream = java.io.ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                iconBlob = stream.toByteArray()
                            } catch (e: Exception) {
                                Log.e("CustomizeDialog", "Error converting drawable to bitmap", e)
                            }
                        }
                    } else {
                        val item = overrideItem?.iconPickerItem
                        if (item != null) {
                            val iconPackProvider = IconPackProvider.INSTANCE.get(applicationContext)
                            val drawable = iconPackProvider.getDrawable(item.toIconEntry(), 0, componentKey.user)
                            if (drawable != null) {
                                try {
                                    val bitmap = drawable.toBitmap()
                                    val stream = java.io.ByteArrayOutputStream()
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                    iconBlob = stream.toByteArray()
                                } catch (e: Exception) {
                                    Log.e("CustomizeDialog", "Error converting drawable to bitmap", e)
                                }
                            }
                        } else if (shortcutId != -1) {
                            dbController.query(
                                arrayOf(com.android.launcher3.LauncherSettings.Favorites.ICON),
                                "_id = ?",
                                arrayOf(shortcutId.toString()),
                                null
                            ).use { cursor ->
                                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                                    iconBlob = cursor.getBlob(0)
                                }
                            }
                        }
                    }
                    
                    val idsToUpdate = mutableListOf<Int>()
                    val idsToClear = mutableListOf<Int>()

                    for (shortcut in matchingShortcuts) {
                        val resolved = resolveContainer(shortcut.container)
                        if (resolved == dockContainer) {
                            // Only write the blob if we actually prepared one. Using
                            // finalStagedIconItem != null here caused null blobs to be
                            // written when drawable loading failed (e.g. Teams adaptive icon),
                            // which cleared the existing custom icon instead of preserving it.
                            if (finalApplyToDock && iconBlob != null) {
                                idsToUpdate.add(shortcut.id)
                            } else if (!finalApplyToDock) {
                                idsToClear.add(shortcut.id)
                            }
                        } else if (resolved == desktopContainer) {
                            if (finalApplyToHomeScreen && iconBlob != null) {
                                idsToUpdate.add(shortcut.id)
                            } else if (!finalApplyToHomeScreen) {
                                idsToClear.add(shortcut.id)
                            }
                        }
                    }
                    
                    if (idsToUpdate.isNotEmpty()) {
                        val contentValues = ContentValues().apply {
                            put(com.android.launcher3.LauncherSettings.Favorites.ICON, iconBlob)
                        }
                        val selection = com.android.launcher3.LauncherSettings.Favorites._ID + " IN (" + idsToUpdate.joinToString(",") + ")"
                        dbController.update(contentValues, selection, null)
                    }
                    
                    if (idsToClear.isNotEmpty()) {
                        val contentValues = ContentValues().apply {
                            putNull(com.android.launcher3.LauncherSettings.Favorites.ICON)
                        }
                        val selection = com.android.launcher3.LauncherSettings.Favorites._ID + " IN (" + idsToClear.joinToString(",") + ")"
                        dbController.update(contentValues, selection, null)
                    }
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        if (finalResetDefault) {
                            repo.deleteOverride(componentKey)
                        } else if (finalStagedIconItem != null) {
                            // Always write to IconOverrideRepository regardless of
                            // applyToAppDrawer. The repo is what CacheDataUpdatedTask
                            // consults at runtime (via LawnchairIconProvider.getIcon).
                            // Without this, any app update triggers CacheDataUpdatedTask
                            // which re-fetches from the icon provider, finds no override,
                            // and reverts the dock icon to default — the DB blob is only
                            // read at launcher startup via tryLoadWorkspaceIconsInBulk.
                            repo.setOverride(componentKey, finalStagedIconItem)
                        }
                        // If no new icon was staged and not resetting, leave the repo
                        // as-is (preserving any existing override).

                        LauncherAppState.getInstance(applicationContext).model.forceReload()
                    }
                } catch (e: Exception) {
                    Log.e("CustomizeDialog", "Error committing icon changes", e)
                }
            }
            onClose()
        }
    }

    CustomizeDialog(
        icon = currentIcon,
        title = title,
        onTitleChange = { title = it },
        defaultTitle = defaultTitle,
        launchSelectIcon = openIconPicker,
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.apply_icon_to)) {
            Item {
                SwitchPreference(
                    checked = applyToDock,
                    label = stringResource(id = R.string.dock_label),
                    onCheckedChange = { applyToDock = it },
                )
            }
            Item {
                SwitchPreference(
                    checked = applyToHomeScreen,
                    label = stringResource(id = R.string.home_screen_label),
                    onCheckedChange = { applyToHomeScreen = it },
                )
            }
            Item {
                SwitchPreference(
                    checked = applyToAppDrawer,
                    label = stringResource(id = R.string.app_drawer_label),
                    onCheckedChange = { applyToAppDrawer = it },
                )
            }
        }

        PreferenceGroup(
            description = componentKey.componentName.flattenToString(),
            showDescription = showComponentNames,
        ) {
            val stringKey = componentKey.toString()
            Item {
                SwitchPreference(
                    checked = hiddenApps.contains(stringKey),
                    label = stringResource(id = R.string.hide_from_drawer),
                    onCheckedChange = { newValue ->
                        val newSet = hiddenApps.toMutableSet()
                        if (newValue) newSet.add(stringKey) else newSet.remove(stringKey)
                        adapter.onChange(newSet)
                    },
                )
            }
        }

        if (preferenceManager2.iconSwipeGestures.asState().value && context.launcher.stateManager.state != LauncherState.ALL_APPS) {
            PreferenceGroup(heading = stringResource(R.string.gestures_label)) {
                listOf(
                    GestureType.SWIPE_LEFT,
                    GestureType.SWIPE_RIGHT,
                ).map { gestureType ->
                    Item {
                        AppGesturePreference(
                            componentKey,
                            gestureType,
                            stringResource(id = gestureType.labelResId),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Button(
                onClick = onApplyClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.action_apply))
            }
        }
    }
}
