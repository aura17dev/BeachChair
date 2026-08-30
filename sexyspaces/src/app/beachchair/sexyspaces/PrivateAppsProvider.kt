package app.beachchair.sexyspaces

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PrivateAppsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (uri == PRIVATE_APPS_SETTINGS_URI) {
            val cursor = MatrixCursor(arrayOf(PRIVATE_APPS_SEARCH_KEYWORD_COLUMN))
            val context = context ?: return cursor
            val preferences = runBlocking { context.privateSpaceDataStore.data.first() }
            cursor.addRow(arrayOf(preferences[searchKeywordKey] ?: "spaces"))
            return cursor
        }

        val cursor = MatrixCursor(arrayOf(PRIVATE_APPS_COMPONENT_COLUMN))
        if (uri != PRIVATE_APPS_URI) return cursor

        val context = context ?: return cursor
        val preferences = runBlocking { context.privateSpaceDataStore.data.first() }
        val hideInBeachChair = preferences[hideInBeachChairKey] ?: true
        if (!hideInBeachChair) return cursor

        preferences[selectedAppsKey].orEmpty()
            .sorted()
            .forEach { cursor.addRow(arrayOf(it)) }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uri) {
            PRIVATE_APPS_SETTINGS_URI -> "vnd.android.cursor.item/vnd.beachchair.private-app-settings"
            else -> "vnd.android.cursor.dir/vnd.beachchair.private-app"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
