package app.lawnchair.allapps.pages

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.ReloadHelper
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DrawerPageViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository: FolderService = FolderService.INSTANCE.get(application)
    private val reloadHelper = ReloadHelper(application)

    val pages: StateFlow<List<FolderInfo>> = repository.getFoldersFlow()
        .catch { exception ->
            Log.e("DrawerPageVM", "Error loading pages", exception)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _selectedPageId = MutableStateFlow<Int?>(null)
    val selectedPageId: StateFlow<Int?> = _selectedPageId.asStateFlow()

    private val _isBulkSelectMode = MutableStateFlow(false)
    val isBulkSelectMode: StateFlow<Boolean> = _isBulkSelectMode.asStateFlow()

    private val _selectedApps = MutableStateFlow<Set<ComponentKey>>(emptySet())
    val selectedApps: StateFlow<Set<ComponentKey>> = _selectedApps.asStateFlow()

    fun selectPage(pageId: Int?) {
        _selectedPageId.value = pageId
    }

    fun createPage(title: String, icon: String?, iconOnly: Boolean, hideFromAll: Boolean) {
        viewModelScope.launch {
            val folderInfo = FolderInfo().apply {
                this.title = title
                this.icon = icon
                this.iconOnly = iconOnly
                this.hideFromAll = hideFromAll
            }
            repository.saveFolderInfo(folderInfo)
            reloadHelper.reloadGrid()
        }
    }

    fun renamePage(pageId: Int, newTitle: String, newIcon: String?, newIconOnly: Boolean, newHideFromAll: Boolean) {
        viewModelScope.launch {
            val page = pages.value.find { it.id == pageId } ?: return@launch
            repository.updateFolderInfo(page.apply {
                title = newTitle
                icon = newIcon
                iconOnly = newIconOnly
                hideFromAll = newHideFromAll
            })
        }
    }

    fun deletePage(pageId: Int) {
        viewModelScope.launch {
            repository.deleteFolderInfo(pageId)
            if (_selectedPageId.value == pageId) {
                _selectedPageId.value = null
            }
            reloadHelper.reloadGrid()
        }
    }

    fun wipePages() {
        viewModelScope.launch {
            _selectedPageId.value = null
            exitBulkSelectMode()
            repository.deleteAllFolders()
        }
    }

    fun moveAppsToPage(appKeys: Set<ComponentKey>, targetPageId: Int) {
        viewModelScope.launch {
            try {
                val page = pages.value.find { it.id == targetPageId } ?: return@launch
                val existingKeys = page.getContents().mapNotNull { it.componentKey }.toSet()
                val allKeys = existingKeys + appKeys
                repository.updateFolderWithKeys(targetPageId, page.title?.toString() ?: "Page", page.icon, page.iconOnly, page.hideFromAll, allKeys)
                _selectedPageId.value = targetPageId
                exitBulkSelectMode()
                reloadHelper.reloadGrid()
            } catch (e: Exception) {
                Log.e("DrawerPageVM", "Failed to move apps to page", e)
            }
        }
    }

    fun enterBulkSelectMode() {
        _isBulkSelectMode.value = true
        _selectedApps.value = emptySet()
    }

    fun exitBulkSelectMode() {
        _isBulkSelectMode.value = false
        _selectedApps.value = emptySet()
    }

    fun toggleAppSelection(key: ComponentKey) {
        _selectedApps.value = if (key in _selectedApps.value) {
            _selectedApps.value - key
        } else {
            _selectedApps.value + key
        }
    }
}
