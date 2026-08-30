package app.beachchair.sexyspaces

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal const val PRIVATE_APPS_AUTHORITY = "app.beachchair.sexyspaces.privateapps"
internal const val PRIVATE_APPS_COMPONENT_COLUMN = "component"
internal const val PRIVATE_APPS_SEARCH_KEYWORD_COLUMN = "search_keyword"
internal val PRIVATE_APPS_URI: Uri = Uri.parse("content://$PRIVATE_APPS_AUTHORITY/selected_apps")
internal val PRIVATE_APPS_SETTINGS_URI: Uri = Uri.parse("content://$PRIVATE_APPS_AUTHORITY/settings")

internal val Context.privateSpaceDataStore by preferencesDataStore("private_space")
internal val selectedAppsKey = stringSetPreferencesKey("selected_apps")
internal val selectedAppOrderKey = stringPreferencesKey("selected_app_order")
internal val foldersKey = stringPreferencesKey("folders")
internal val appFolderAssignmentsKey = stringPreferencesKey("app_folder_assignments")
internal val allowFaceUnlockKey = booleanPreferencesKey("allow_face_unlock")
internal val hideInBeachChairKey = booleanPreferencesKey("hide_in_beachchair")
internal val lockDelayMsKey = stringPreferencesKey("lock_delay_ms")
internal val pageTitleKey = stringPreferencesKey("page_title")
internal val searchKeywordKey = stringPreferencesKey("search_keyword")
internal val slutModeEnabledKey = booleanPreferencesKey("slut_mode_enabled")
internal val gridColumnCountKey = stringPreferencesKey("grid_column_count")
internal val tileShapeKey = stringPreferencesKey("tile_shape")
internal val labelTextColorKey = stringPreferencesKey("label_text_color")
internal val tileSizeDpKey = stringPreferencesKey("tile_size_dp")
