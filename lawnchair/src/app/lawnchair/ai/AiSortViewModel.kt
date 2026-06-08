package app.lawnchair.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.flowerpot.Flowerpot
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.preferences2.firstBlockingCached
import com.android.launcher3.BuildConfig
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class AiSortResponse(
    val pages: List<AiProposedPageDto>,
    val unassigned: List<Int> = emptyList(),
)

@Serializable
private data class AiProposedPageDto(
    val name: String,
    val icon: String,
    val apps: List<Int>,
)

data class ProposedApp(
    val label: String,
    val packageKey: String,
)

data class ProposedPage(
    val name: String,
    val icon: String,
    val apps: List<ProposedApp>,
    val isIncluded: Boolean = true,
)

sealed class AiSortState {
    data object Idle : AiSortState()
    data object NeedApiKey : AiSortState()
    data object ChoosingMode : AiSortState()
    data object Loading : AiSortState()
    data class Proposal(
        val pages: List<ProposedPage>,
        val unassignedCount: Int,
        val isFallback: Boolean,
    ) : AiSortState()
    data class Error(val message: String) : AiSortState()
    data object Applied : AiSortState()
}

class AiSortViewModel(application: Application) : AndroidViewModel(application) {

    private val folderService = FolderService.INSTANCE.get(application)
    private val prefs2 = PreferenceManager2.getInstance(application)
    private val reloadHelper = ReloadHelper(application)
    private val deepSeekApi = DeepSeekApiService.create()
    private val playStoreFetcher = PlayStoreDescriptionFetcher()
    private val json = Json { ignoreUnknownKeys = true }

    private var storedAllApps: List<AppInfo> = emptyList()
    private var storedExistingPages: List<FolderInfo> = emptyList()
    private var storedSortUnassignedOnly: Boolean = true

    private val _state = MutableStateFlow<AiSortState>(AiSortState.Idle)
    val state: StateFlow<AiSortState> = _state.asStateFlow()

    private fun resolveApiKey(): String {
        val userKey = prefs2.deepSeekApiKey.firstBlockingCached()
        return userKey.ifBlank { BuildConfig.DEEPSEEK_API_KEY }
    }

    fun reset(allApps: List<AppInfo>, existingPages: List<FolderInfo>) {
        storedAllApps = allApps
        storedExistingPages = existingPages
        val apiKey = resolveApiKey()
        _state.value = when {
            apiKey.isBlank() -> AiSortState.NeedApiKey
            existingPages.isNotEmpty() -> AiSortState.ChoosingMode
            else -> AiSortState.Loading.also { startSortInternal(sortUnassignedOnly = false) }
        }
    }

    fun saveApiKeyAndContinue(key: String) {
        viewModelScope.launch {
            prefs2.deepSeekApiKey.set(key.trim())
            val existingPages = storedExistingPages
            _state.value = if (existingPages.isNotEmpty()) AiSortState.ChoosingMode else AiSortState.Loading
            if (existingPages.isEmpty()) startSortInternal(sortUnassignedOnly = false)
        }
    }

    fun startSort(sortUnassignedOnly: Boolean) {
        storedSortUnassignedOnly = sortUnassignedOnly
        _state.value = AiSortState.Loading
        startSortInternal(sortUnassignedOnly)
    }

    private fun startSortInternal(sortUnassignedOnly: Boolean) {
        // Capture on the main thread before handing off to IO — storedAllApps and
        // storedExistingPages are written by reset() on the main thread and are not
        // thread-safe to read concurrently from IO.
        val allApps = storedAllApps
        val existingPages = storedExistingPages
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = resolveApiKey()
                val appsToSort = if (sortUnassignedOnly) {
                    val assignedKeys: Set<ComponentKey> = existingPages
                        .flatMap { it.getContents().mapNotNull { item -> item.componentKey } }
                        .toSet()
                    allApps.filter { app -> app.toComponentKey() !in assignedKeys }
                } else {
                    allApps
                }

                if (appsToSort.isEmpty()) {
                    _state.value = AiSortState.Proposal(
                        pages = emptyList(),
                        unassignedCount = 0,
                        isFallback = false,
                    )
                    return@launch
                }

                val categorized = Flowerpot.Manager.getInstance(getApplication()).categorizeApps(appsToSort)
                val packageToKeys = buildPackageToKeysMap(appsToSort)

                val proposal = if (apiKey.isNotBlank()) {
                    try {
                        fetchDeepSeekProposal(apiKey, categorized, packageToKeys, existingPages)
                    } catch (e: Exception) {
                        Log.w("AiSortVM", "DeepSeek API failed, falling back to Flowerpot", e)
                        buildFlowerpotFallback(categorized, packageToKeys)
                    }
                } else {
                    buildFlowerpotFallback(categorized, packageToKeys)
                }

                _state.value = proposal
            } catch (e: Exception) {
                Log.e("AiSortVM", "Sort failed", e)
                _state.value = AiSortState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun buildPackageToKeysMap(apps: List<AppInfo>): Map<String, String> {
        return apps.mapNotNull { app ->
            val pkg = app.componentKey?.componentName?.packageName ?: return@mapNotNull null
            pkg to app.toComponentKey().toString()
        }.toMap()
    }

    private suspend fun fetchDeepSeekProposal(
        apiKey: String,
        categorized: Map<String, List<AppInfo>>,
        packageToKeys: Map<String, String>,
        existingPages: List<FolderInfo>,
    ): AiSortState.Proposal {
        val indexedApps = buildIndexedApps(categorized)
        val packageNames = indexedApps.map { (_, pkg) -> pkg }
        val descriptions = playStoreFetcher.fetchAll(packageNames)
        val systemPrompt = buildSystemPrompt()
        val userMessage = buildUserMessage(indexedApps, categorized, descriptions, existingPages)

        val response = deepSeekApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = DeepSeekRequest(
                model = DeepSeekApiService.CHAT_MODEL,
                maxTokens = 4096,
                messages = listOf(
                    DeepSeekMessage(role = "system", content = systemPrompt),
                    DeepSeekMessage(role = "user", content = userMessage),
                ),
            ),
        )

        val responseText = response.choices.firstOrNull()?.message?.content ?: ""
        return parseResponse(responseText, indexedApps, packageToKeys, categorized)
    }

    private fun buildIndexedApps(categorized: Map<String, List<AppInfo>>): List<Pair<AppInfo, String>> {
        return categorized.entries.flatMap { (_, apps) -> apps }.mapNotNull { app ->
            val pkg = app.componentKey?.componentName?.packageName ?: return@mapNotNull null
            app to pkg
        }
    }

    private fun buildSystemPrompt(): String = """
You are an app organizer for an Android smartphone. Apps are listed by index number. Create logical drawer pages.
Rules:
- Create 4-8 pages total (fewer is better, maximum 10)
- Every app index MUST appear in exactly one page — "unassigned" must be empty
- When in doubt, assign an app to the closest matching page
- Choose icon from this list only: Grid, Folder, Apps, Games, Social, Chat, Tools, Settings, Books, Music, Video, Photos, Lifestyle, Star, Phone, Mail, Map, Compass, Globe, Film, Activity, Briefcase, Calendar, Coffee, File, Gift, Image, Key, Lock, Moon, Shield, Sun, User, VideoCamera, Wifi, Search, Bell, Home
- Return ONLY valid JSON with no explanation
- JSON format: {"pages":[{"name":"...","icon":"...","apps":[0,1,2]}],"unassigned":[]}
    """.trimIndent()

    private fun buildUserMessage(
        indexedApps: List<Pair<AppInfo, String>>,
        categorized: Map<String, List<AppInfo>>,
        descriptions: Map<String, String>,
        existingPages: List<FolderInfo>,
    ): String {
        val existingPagesSection = if (existingPages.isNotEmpty()) {
            val lines = existingPages.joinToString("\n") { page ->
                val appNames = page.getContents().take(5).mapNotNull { it.title?.toString() }.joinToString(", ")
                "- ${page.title}: $appNames${if (page.getContents().size > 5) " and more" else ""}"
            }
            "Existing pages (do not reassign these apps):\n$lines\n\n"
        } else ""

        val pkgToCategory = categorized.entries
            .flatMap { (cat, apps) -> apps.mapNotNull { it.componentKey?.componentName?.packageName?.let { pkg -> pkg to cat } } }
            .toMap()

        val appList = indexedApps.mapIndexed { i, (app, pkg) ->
            val cat = pkgToCategory[pkg]?.lowercase()?.replace('_', '-') ?: ""
            val desc = descriptions[pkg]?.let { " \"$it\"" } ?: ""
            val catTag = if (cat.isNotBlank()) "[$cat]" else ""
            "$i:${app.title}$catTag$desc"
        }.joinToString("\n")

        return "${existingPagesSection}Assign every app index to a page:\n$appList\n\nReturn JSON:"
    }

    private fun parseResponse(
        responseText: String,
        indexedApps: List<Pair<AppInfo, String>>,
        packageToKeys: Map<String, String>,
        categorized: Map<String, List<AppInfo>>,
    ): AiSortState.Proposal {
        val jsonStart = responseText.indexOf('{')
        val jsonEnd = responseText.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
            return buildFlowerpotFallback(categorized, packageToKeys)
        }
        val jsonStr = responseText.substring(jsonStart, jsonEnd + 1)

        return try {
            val dto = json.decodeFromString<AiSortResponse>(jsonStr)
            val pages = dto.pages.map { page ->
                ProposedPage(
                    name = page.name,
                    icon = page.icon.ifBlank { "Folder" },
                    apps = page.apps.mapNotNull { idx ->
                        val (app, pkg) = indexedApps.getOrNull(idx) ?: return@mapNotNull null
                        val key = packageToKeys[pkg] ?: return@mapNotNull null
                        ProposedApp(label = app.title.toString(), packageKey = key)
                    },
                )
            }.filter { it.apps.isNotEmpty() }

            val assignedIndices = dto.pages.flatMap { it.apps }.toSet()
            val unassignedCount = indexedApps.indices.count { it !in assignedIndices }

            AiSortState.Proposal(pages = pages, unassignedCount = unassignedCount, isFallback = false)
        } catch (e: Exception) {
            Log.w("AiSortVM", "Failed to parse DeepSeek JSON, using fallback", e)
            buildFlowerpotFallback(categorized, packageToKeys)
        }
    }

    private fun buildFlowerpotFallback(
        categorized: Map<String, List<AppInfo>>,
        packageToKeys: Map<String, String>,
    ): AiSortState.Proposal {
        data class MergeGroup(val name: String, val icon: String, val categories: List<String>)

        val mergeGroups = listOf(
            MergeGroup("Games", "Games", listOf("GAME")),
            MergeGroup("Social", "Social", listOf("SOCIAL", "COMMUNICATION")),
            MergeGroup("Multimedia", "Music", listOf("MUSIC", "VIDEO", "ENTERTAINMENT", "PHOTOGRAPHY")),
            MergeGroup("Work", "Briefcase", listOf("PRODUCTIVITY", "TOOLS", "FINANCE")),
            MergeGroup("Lifestyle", "Lifestyle", listOf("HEALTH_AND_FITNESS", "FOOD_AND_DRINK", "SPORTS")),
            MergeGroup("Learn", "Books", listOf("EDUCATION", "BOOKS_AND_REFERENCE", "NEWS")),
            MergeGroup("Travel", "Compass", listOf("MAPS_AND_NAVIGATION", "TRAVEL_AND_LOCAL", "WEATHER")),
            MergeGroup("Shopping", "Gift", listOf("SHOPPING")),
        )

        val categoryToGroup = mergeGroups.flatMap { group ->
            group.categories.map { cat -> cat to group }
        }.toMap()

        val grouped = mutableMapOf<MergeGroup, MutableList<ProposedApp>>()
        val overflow = mutableListOf<ProposedApp>()

        for ((category, apps) in categorized) {
            if (apps.isEmpty()) continue
            val proposedApps = apps.mapNotNull { app ->
                val pkg = app.componentKey?.componentName?.packageName ?: return@mapNotNull null
                val key = packageToKeys[pkg] ?: return@mapNotNull null
                ProposedApp(label = app.title.toString(), packageKey = key)
            }
            val group = categoryToGroup[category]
            if (group != null) {
                grouped.getOrPut(group) { mutableListOf() }.addAll(proposedApps)
            } else {
                overflow.addAll(proposedApps)
            }
        }

        val pages = grouped.entries
            .filter { it.value.isNotEmpty() }
            .map { (group, apps) -> ProposedPage(name = group.name, icon = group.icon, apps = apps) }
            .toMutableList()

        if (overflow.isNotEmpty()) {
            pages.add(ProposedPage(name = "Other", icon = "Grid", apps = overflow))
        }

        return AiSortState.Proposal(pages = pages, unassignedCount = 0, isFallback = true)
    }

    fun updatePageName(index: Int, name: String) {
        val current = _state.value as? AiSortState.Proposal ?: return
        val updated = current.pages.toMutableList()
        updated[index] = updated[index].copy(name = name)
        _state.value = current.copy(pages = updated)
    }

    fun togglePageIncluded(index: Int) {
        val current = _state.value as? AiSortState.Proposal ?: return
        val updated = current.pages.toMutableList()
        updated[index] = updated[index].copy(isIncluded = !updated[index].isIncluded)
        _state.value = current.copy(pages = updated)
    }

    fun reassignApp(fromPageIndex: Int, appIndex: Int, toPageIndex: Int) {
        val current = _state.value as? AiSortState.Proposal ?: return
        val pages = current.pages.toMutableList()
        val app = pages[fromPageIndex].apps.getOrNull(appIndex) ?: return
        pages[fromPageIndex] = pages[fromPageIndex].copy(
            apps = pages[fromPageIndex].apps.toMutableList().also { it.removeAt(appIndex) },
        )
        pages[toPageIndex] = pages[toPageIndex].copy(
            apps = pages[toPageIndex].apps + app,
        )
        _state.value = current.copy(pages = pages)
    }

    fun applyProposal() {
        val current = _state.value as? AiSortState.Proposal ?: return
        val includedPages = current.pages.filter { it.isIncluded }
        if (includedPages.isEmpty()) {
            _state.value = AiSortState.Applied
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                for (page in includedPages) {
                    val keys = page.apps
                        .mapNotNull { app ->
                            try { ComponentKey.fromString(app.packageKey) } catch (e: Exception) { null }
                        }
                        .toSet()
                    if (keys.isNotEmpty()) {
                        folderService.createFolderWithKeys(
                            title = page.name.trim().ifBlank { "Page" },
                            icon = page.icon,
                            iconOnly = false,
                            hideFromAll = false,
                            keys = keys,
                        )
                    }
                }
                _state.value = AiSortState.Applied
            } catch (e: Exception) {
                Log.e("AiSortVM", "Failed to apply proposal", e)
                _state.value = AiSortState.Error(e.message ?: "Failed to apply")
            }
        }
    }

    fun retry() {
        _state.value = AiSortState.Loading
        startSortInternal(storedSortUnassignedOnly)
    }
}
