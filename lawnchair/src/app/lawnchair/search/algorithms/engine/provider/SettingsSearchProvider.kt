package app.lawnchair.search.algorithms.engine.provider

import android.content.Context
import android.provider.Settings
import android.util.Log
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.algorithms.data.SettingInfo
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import com.patrykmichalik.opto.core.firstBlocking
import app.lawnchair.preferences2.firstBlockingCached
import java.lang.reflect.Modifier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

object SettingsSearchProvider : SearchProvider {

    override val id: String = "settings"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = flow {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        if (query.isBlank() || !prefs.searchResultSettingsEntry.get()) {
            emit(emptyList())
            return@flow
        }

        val settingsInfoList = findSettingsByNameAndAction(query, prefs2)

        val searchResults = settingsInfoList.map { settingInfo ->
            SearchResult.Setting(data = settingInfo)
        }
        emit(searchResults)
    }
}

@Volatile private var cachedFields: List<Pair<String, String>>? = null

private fun getSettingsFields(): List<Pair<String, String>> {
    var fields = cachedFields
    if (fields == null) {
        fields = try {
            Settings::class.java.fields
                .filter {
                    it.type == String::class.java &&
                        Modifier.isStatic(it.modifiers) &&
                        it.name.startsWith("ACTION_")
                }
                .mapNotNull {
                    try {
                        it.name to it.get(null) as String
                    } catch (e: Exception) {
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e("SettingSearch", "Failed to retrieve settings fields", e)
            emptyList()
        }
        cachedFields = fields
    }
    return fields
}

private suspend fun findSettingsByNameAndAction(query: String, prefs2: PreferenceManager2): List<SettingInfo> = try {
    if (query.isBlank()) {
        emptyList()
    } else {
        withContext(
            Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e("SettingSearch", "Something went wrong ", e)
            },
        ) {
            val max = prefs2.maxSettingsEntryResultCount.firstBlockingCached()
            if (max <= 0) return@withContext emptyList()
            getSettingsFields()
                .asSequence()
                .filter { (name, action) ->
                    name.contains(query, ignoreCase = true) &&
                        !action.contains("REQUEST", ignoreCase = true) &&
                        !name.contains("REQUEST", ignoreCase = true) &&
                        !action.contains("PERMISSION", ignoreCase = true) &&
                        !name.contains("DETAIL", ignoreCase = true) &&
                        !name.contains("REMOTE", ignoreCase = true)
                }
                .map { (name, action) ->
                    val id = name + action
                    val requiresUri = action.contains("URI")
                    SettingInfo(id, name, action, requiresUri)
                }
                .take(max)
                .toList()
        }
    }
} catch (e: Exception) {
    Log.e("SettingSearch", "Something went wrong ", e)
    emptyList()
}
