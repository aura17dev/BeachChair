package app.lawnchair.icons

import android.content.Context
import android.util.Log
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.PathShapeDelegate
import app.lawnchair.preferences.PreferenceChangeListener
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LooperExecutor
import com.patrykmichalik.opto.core.firstBlocking
import app.lawnchair.preferences2.firstBlockingCached
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus

@LauncherAppSingleton
class LawnchairThemeManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    private val prefs: LauncherPrefs,
    private val iconControllerFactory: IconControllerFactory,
    private val lifecycle: DaggerSingletonTracker,
    private val prefs2: PreferenceManager2,
    private val prefs1: PreferenceManager,
) : ThemeManager(
    context,
    uiExecutor,
    prefs,
    iconControllerFactory,
    lifecycle,
) {
    private val statePrefs1 = listOf(
        prefs1.wrapAdaptiveIcons,
        prefs1.transparentIconBackground,
        prefs1.shadowBGIcons,
        prefs1.coloredBackgroundLightness,
        prefs1.forceIconMonochrome,
    )

    private val prefListener = PreferenceChangeListener {
        uiExecutor.execute { verifyIconState() }
    }

    override var iconState = parseIconStateV2(null)

    init {
        val scope = MainScope() + CoroutineName("LawnchairThemeManager")
        merge(
            prefs2.iconShape.get(),
            prefs2.customIconShape.get(),
            prefs2.folderShape.get(),
            prefs2.customFolderShape.get(),
        ).distinctUntilChanged()
            .onEach { verifyIconState() }
            .launchIn(scope)

        statePrefs1.forEach { it.addListener(prefListener) }

        lifecycle.addCloseable {
            scope.cancel()
            statePrefs1.forEach { it.removeListener(prefListener) }
        }
    }

    override fun verifyIconState() {
        val newState = parseIconStateV2(iconState)
        if (newState == iconState) return
        iconState = newState

        listeners.forEach { it.onThemeChanged() }

        // Reload the launcher model so already-loaded icons are regenerated with the new shape /
        // theme. This is driven from here (rather than from the pref's onSet via
        // ReloadHelper.reloadIcons) so the reload is guaranteed to run *after* iconState has been
        // updated — the old onSet path raced the state update and frequently reloaded with the
        // stale shape, which is why shape changes appeared to do nothing until a restart.
        Executors.MODEL_EXECUTOR.execute {
            val app = LauncherAppState.getInstance(context)
            app.iconCache.clearMemoryCache()
            app.model.reloadIfActive()
        }
    }

    private fun prefs1State(): String = statePrefs1.joinToString(",") { it.get().toString() }

    private fun parseIconStateV2(oldState: IconState?): IconState {
        val currentAppShape: IconShape = try {
            prefs2.iconShape.firstBlockingCached()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting icon shape", e)
            IconShape.Circle
        }

        val currentFolderShape: IconShape = try {
            prefs2.folderShape.firstBlockingCached()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting folder shape", e)
            IconShape.Circle
        }

        val currentPrefs1State = prefs1State()
        val appShapeKey = currentAppShape.getHashString() + currentPrefs1State
        val folderShapeKey = currentFolderShape.getHashString() + currentPrefs1State
        val combinedKey = "$appShapeKey:$folderShapeKey"

        val appShape =
            if (oldState != null && (oldState.iconShape as? PathShapeDelegate)?.iconShape == currentAppShape) {
                oldState.iconShape
            } else {
                PathShapeDelegate(currentAppShape)
            }

        val folderShape =
            if (oldState != null && (oldState.folderShape as? PathShapeDelegate)?.iconShape == currentFolderShape) {
                oldState.folderShape
            } else {
                PathShapeDelegate(currentFolderShape)
            }

        return IconState(
            iconMask = combinedKey,
            folderRadius = 1f,
            shapeRadius = 1f,
            themeController = iconControllerFactory.createThemeController(),
            iconShape = appShape,
            folderShape = folderShape,
        )
    }
}

private const val TAG = "LawnchairThemeManager"
