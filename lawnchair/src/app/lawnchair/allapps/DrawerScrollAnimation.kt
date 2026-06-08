package app.lawnchair.allapps

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

enum class DrawerScrollAnimation(
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
) {
    NONE(
        R.string.drawer_scroll_anim_none,
        R.string.drawer_scroll_anim_none_desc,
    ),
    WAVE(
        R.string.drawer_scroll_anim_wave,
        R.string.drawer_scroll_anim_wave_desc,
    ),
    EDGE_FADE(
        R.string.drawer_scroll_anim_edge_fade,
        R.string.drawer_scroll_anim_edge_fade_desc,
    ),
    TILT(
        R.string.drawer_scroll_anim_tilt,
        R.string.drawer_scroll_anim_tilt_desc,
    ),
    CASCADE(
        R.string.drawer_scroll_anim_cascade,
        R.string.drawer_scroll_anim_cascade_desc,
    ),
    ARC(
        R.string.drawer_scroll_anim_arc,
        R.string.drawer_scroll_anim_arc_desc,
    ),
    ;

    companion object {
        fun fromString(s: String): DrawerScrollAnimation =
            values().firstOrNull { it.name == s } ?: WAVE

        @Composable
        fun listEntries(): List<ListPreferenceEntry<DrawerScrollAnimation>> =
            values().map { anim ->
                ListPreferenceEntry(value = anim) { stringResource(id = anim.labelRes) }
            }
    }
}
