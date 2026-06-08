package app.lawnchair.data.folder.service

import android.content.Context
import android.content.pm.LauncherApps
import android.util.Log
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.Converters
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.folder.FolderItemEntity
import app.lawnchair.data.toEntity
import com.android.launcher3.AppFilter
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@LauncherAppSingleton
class FolderService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userCache = UserCache.INSTANCE.get(context)
    private val appFilter = AppFilter(context)
    private val converters = Converters()

    @Volatile private var keyMapCache: Map<String, AppInfo>? = null

    fun invalidateKeyMap() {
        keyMapCache = null
    }

    fun getFoldersFlow(): Flow<List<FolderInfo>> {
        return folderDao.getAllFoldersWithItems().map { foldersWithItems ->
            val keyMap = buildKeyMap()
            foldersWithItems.mapNotNull { mapToFolderInfo(it, true, keyMap) }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    }

    private fun buildKeyMap(): Map<String, AppInfo> {
        keyMapCache?.let { return it }
        return synchronized(this) {
            keyMapCache ?: buildKeyMapInternal().also { keyMapCache = it }
        }
    }

    private fun buildKeyMapInternal(): Map<String, AppInfo> = buildMap {
        userCache.userProfiles.forEach { profile ->
            launcherApps?.getActivityList(null, profile)
                ?.filter { appFilter.shouldShowApp(it.componentName) }
                ?.forEach { activityInfo ->
                    val appInfo = AppInfo(context, activityInfo, activityInfo.user)
                    val key = converters.fromComponentKey(appInfo.componentKey)
                    if (key != null) put(key, appInfo)
                }
        }
    }

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, icon: String?, iconOnly: Boolean, hideFromAll: Boolean, appInfos: List<AppInfo>) = withContext(Dispatchers.IO) {
        folderDao.insertFolderWithItems(
            FolderInfoEntity(id = folderInfoId, title = title, icon = icon, iconOnly = iconOnly, hideFromAll = hideFromAll),
            appInfos.mapIndexed { index, appInfo ->
                appInfo.toEntity(folderInfoId).copy(rank = index)
            }.toList(),
        )
    }

    suspend fun updateFolderWithKeys(folderInfoId: Int, title: String, icon: String?, iconOnly: Boolean, hideFromAll: Boolean, keys: Set<ComponentKey>) = withContext(Dispatchers.IO) {
        folderDao.insertFolderWithItems(
            FolderInfoEntity(id = folderInfoId, title = title, icon = icon, iconOnly = iconOnly, hideFromAll = hideFromAll),
            keys.mapIndexed { index, key ->
                FolderItemEntity(folderId = folderInfoId, rank = index, componentKey = key.toString())
            },
        )
    }

    suspend fun saveFolderInfo(folderInfo: FolderInfo) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderInfoEntity(title = folderInfo.title.toString(), icon = folderInfo.icon, iconOnly = folderInfo.iconOnly, hideFromAll = folderInfo.hideFromAll))
    }

    suspend fun updateFolderInfo(folderInfo: FolderInfo, hide: Boolean = false) = withContext(Dispatchers.IO) {
        folderDao.updateFolderInfo(folderInfo.id, folderInfo.title.toString(), folderInfo.icon, folderInfo.iconOnly, folderInfo.hideFromAll, hide)
    }

    suspend fun deleteFolderInfo(id: Int) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
    }

    suspend fun deleteAllFolders() = withContext(Dispatchers.IO) {
        folderDao.deleteAllFolders()
    }

    suspend fun getFolderInfo(folderId: Int, hasId: Boolean = false): FolderInfo? = withContext(Dispatchers.Default) {
        folderDao.getFolderWithItems(folderId)?.let {
            val keyMap = buildKeyMap()
            mapToFolderInfo(it, hasId, keyMap)
        }
    }

    private fun mapToFolderInfo(folderWithItems: FolderWithItems, hasId: Boolean, keyMap: Map<String, AppInfo>): FolderInfo? {
        return try {
            val domainFolderInfo = FolderInfo().apply {
                if (hasId) id = folderWithItems.folder.id
                title = folderWithItems.folder.title
                icon = folderWithItems.folder.icon
                iconOnly = folderWithItems.folder.iconOnly
                hideFromAll = folderWithItems.folder.hideFromAll
            }
            folderWithItems.items.sortedBy { it.rank }.forEach { itemEntity ->
                keyMap[itemEntity.componentKey]?.let { domainFolderInfo.add(it) }
            }
            domainFolderInfo
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to map FolderWithItems for id: ${folderWithItems.folder.id}", e)
            null
        }
    }

    suspend fun createFolderWithKeys(title: String, icon: String?, iconOnly: Boolean, hideFromAll: Boolean, keys: Set<ComponentKey>): Int = withContext(Dispatchers.IO) {
        val newId = folderDao.insertFolderGetId(
            FolderInfoEntity(title = title, icon = icon, iconOnly = iconOnly, hideFromAll = hideFromAll),
        ).toInt()
        folderDao.insertFolderItems(
            keys.mapIndexed { index, key ->
                FolderItemEntity(folderId = newId, rank = index, componentKey = key.toString())
            },
        )
        newId
    }

    suspend fun getAllFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        try {
            val foldersWithItems = folderDao.getAllFoldersWithItems().firstOrNull() ?: emptyList()
            val keyMap = buildKeyMap()
            foldersWithItems.mapNotNull { mapToFolderInfo(it, true, keyMap) }
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to get all folders", e)
            emptyList()
        }
    }

    override fun close() {
        keyMapCache = null
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getFolderService)
    }
}
