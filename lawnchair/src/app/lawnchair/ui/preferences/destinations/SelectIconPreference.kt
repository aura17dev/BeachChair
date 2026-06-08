package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.override.CustomizeDraft
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SelectIconPreference(componentKey: ComponentKey, shortcutId: Int = -1) {
    val context = LocalContext.current
    val label = remember(componentKey) {
        val launcherApps: LauncherApps = context.requireSystemService()
        val intent = Intent().setComponent(componentKey.componentName)
        val activity = launcherApps.resolveActivity(intent, componentKey.user)
        activity.label.toString()
    }
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val launcherAppState = LauncherAppState.getInstance(context)
    val model = launcherAppState.model

    val repo = IconOverrideRepository.INSTANCE.get(context)
    val dbHasCustomIcon by produceState(initialValue = false) {
        if (shortcutId != -1) {
            value = try {
                val dbController = LauncherAppState.getInstance(context).model.modelDbController
                dbController.query(
                    arrayOf(com.android.launcher3.LauncherSettings.Favorites.ICON),
                    "_id = ?",
                    arrayOf(shortcutId.toString()),
                    null
                ).use { cursor ->
                    cursor.moveToFirst() && !cursor.isNull(0)
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    val appIcon by produceState<Drawable?>(initialValue = null, componentKey) {
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
                // ignore
            }
        }
    }

    OnResult<IconPickerItem> { item ->
        CustomizeDraft.stagedIconDraft = item
        CustomizeDraft.stagedResetDraft = false
        CustomizeDraft.hasDraft = true
        (context as Activity).finish()
    }

    val overrideItem by repo.observeTarget(componentKey).collectAsStateWithLifecycle(initialValue = null)
    val hasOverride = overrideItem != null || dbHasCustomIcon

    PreferenceLayoutLazyColumn(label = label) {
        preferenceGroupItems(1, isFirstChild = true) {
            PreferenceTemplate(
                title = { Text(text = label) },
                description = { Text(text = stringResource(id = R.string.icon_picker_customizing_app)) },
                startWidget = {
                    appIcon?.let {
                        Image(
                            painter = rememberDrawablePainter(it),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            )
        }
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = false) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        CustomizeDraft.stagedIconDraft = null
                        CustomizeDraft.stagedResetDraft = true
                        CustomizeDraft.hasDraft = true
                        (context as Activity).finish()
                    },
                )
            }
        }
        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = false,
            heading = { stringResource(id = R.string.pick_icon_from_label) },
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    if (iconPack.packageName.isEmpty()) {
                        navController.navigate(IconPicker(componentKey = componentKey.toString()))
                    } else {
                        navController.navigate(
                            IconPicker(
                                packageName = iconPack.packageName,
                                componentKey = componentKey.toString()
                            )
                        )
                    }
                },
            )
        }
    }
}
