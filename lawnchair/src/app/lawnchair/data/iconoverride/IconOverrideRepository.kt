package app.lawnchair.data.iconoverride

import android.content.Context
import app.lawnchair.data.AppDatabase
import app.lawnchair.icons.picker.IconPickerItem
import com.android.launcher3.LauncherAppState
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

@LauncherAppSingleton
class IconOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("IconOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).iconOverrideDao()
    @Volatile private var _overridesMap = mapOf<ComponentKey, IconPickerItem>()
    val overridesMap get() = _overridesMap

    init {
        scope.launch {
            dao.observeAll()
                .flowOn(Dispatchers.IO)
                .collect { overrides ->
                    _overridesMap = overrides.associateBy(
                        keySelector = { it.target },
                        valueTransform = { it.iconPickerItem },
                    )
                }
        }
    }

    suspend fun setOverride(target: ComponentKey, item: IconPickerItem) {
        // Update the in-memory map immediately so the icon provider sees the new override
        // before MODEL_EXECUTOR runs updateIconsForPkg. Without this, the background thread
        // reads a stale overridesMap and caches the old icon for all subsequent getTitleAndIcon calls.
        val previous = _overridesMap
        _overridesMap = _overridesMap + (target to item)
        try {
            dao.insert(IconOverride(target, item))
        } catch (e: Exception) {
            _overridesMap = previous
            throw e
        }
        // Call directly — the old queue-based approach had a race where Room's InvalidationTracker
        // could fire the Flow collector (draining an empty queue) before offer() was called.
        updatePackageIcons(target)
    }

    suspend fun deleteOverride(target: ComponentKey) {
        val previous = _overridesMap
        _overridesMap = _overridesMap - target
        try {
            dao.delete(target)
        } catch (e: Exception) {
            _overridesMap = previous
            throw e
        }
        updatePackageIcons(target)
    }

    fun observeTarget(target: ComponentKey) = dao.observeTarget(target)

    fun observeCount() = dao.observeCount()

    suspend fun deleteAll() {
        dao.deleteAll()
        val appState = LauncherAppState.getInstance(context)
        appState.iconCache.clearAll()
        appState.model.reloadIfActive()
    }

    private fun updatePackageIcons(target: ComponentKey) {
        val appState = LauncherAppState.INSTANCE.get(context)
        val model = appState.model
        com.android.launcher3.util.Executors.MODEL_EXECUTOR.execute {
            appState.iconCache.updateIconsForPkg(target.componentName.packageName, target.user)
            model.onPackageIconsUpdated(hashSetOf(target.componentName.packageName), target.user)
        }
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconOverrideRepository)
    }
}
