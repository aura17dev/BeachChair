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

    fun createPage(title: String) {
        viewModelScope.launch {
            val folderInfo = FolderInfo().apply {
                this.title = title
            }
            repository.saveFolderInfo(folderInfo)
            reloadHelper.reloadGrid()
        }
    }

    fun renamePage(pageId: Int, newTitle: String) {
        viewModelScope.launch {
            val page = pages.value.find { it.id == pageId } ?: return@launch
            repository.updateFolderInfo(page.apply { title = newTitle })
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

    fun moveAppsToPage(appKeys: Set<ComponentKey>, targetPageId: Int) {
        // Bulk move will be fully implemented when ItemInfo serialization is handled.
        // For now, exit bulk-select and refresh so the UI stays consistent.
        viewModelScope.launch {
            exitBulkSelectMode()
            reloadHelper.reloadGrid()
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
