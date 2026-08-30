package app.lawnchair.sexyspaces

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.launcher3.model.data.AppInfo

object SexySpacesBridge {
    val privateAppsUri: Uri = Uri.parse("content://app.beachchair.sexyspaces.privateapps/selected_apps")

    private const val TAG = "SexySpacesBridge"
    private const val COMPONENT_COLUMN = "component"
    private const val SEARCH_KEYWORD_COLUMN = "search_keyword"
    private const val PACKAGE_NAME = "app.beachchair.sexyspaces"
    private const val ACTION_OPEN_UNLOCKED = "app.beachchair.sexyspaces.action.OPEN_UNLOCKED"
    private val settingsUri: Uri = Uri.parse("content://app.beachchair.sexyspaces.privateapps/settings")

    @Volatile
    private var selectedComponents: Set<String> = emptySet()

    fun refresh(context: Context): Set<String> {
        val next = mutableSetOf<String>()
        runCatching {
            context.contentResolver.query(privateAppsUri, arrayOf(COMPONENT_COLUMN), null, null, null)
                ?.use { cursor ->
                    val componentIndex = cursor.getColumnIndex(COMPONENT_COLUMN)
                    while (cursor.moveToNext()) {
                        cursor.getString(componentIndex)?.let(next::add)
                    }
                }
        }.onFailure {
            Log.d(TAG, "Sexy Spaces app list unavailable", it)
        }
        selectedComponents = next
        return next
    }

    fun currentComponents(context: Context): Set<String> {
        return selectedComponents.ifEmpty { refresh(context) }
    }

    fun hiddenKeysFor(apps: Iterable<AppInfo>, context: Context): Set<String> {
        val privateComponents = currentComponents(context)
        if (privateComponents.isEmpty()) return emptySet()
        return apps.asSequence()
            .filter { it.componentName?.flattenToString() in privateComponents }
            .map { "${it.componentName?.flattenToString()}#${it.user.hashCode()}" }
            .toSet()
    }

    fun searchKeyword(context: Context): String {
        runCatching {
            context.contentResolver.query(settingsUri, arrayOf(SEARCH_KEYWORD_COLUMN), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val keywordIndex = cursor.getColumnIndex(SEARCH_KEYWORD_COLUMN)
                        return cursor.getString(keywordIndex)?.trim()?.lowercase().orEmpty().ifEmpty { "spaces" }
                    }
                }
        }.onFailure {
            Log.d(TAG, "Spaces settings unavailable", it)
        }
        return "spaces"
    }

    fun openUnlockedIntent(context: Context): Intent? {
        val intent = Intent(ACTION_OPEN_UNLOCKED)
            .setPackage(PACKAGE_NAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }
}
