package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.views.HomeMorphAnimation
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

/**
 * Home-screen Beach Mods. Motion lives here alongside future home-screen personalization.
 */
@Composable
fun BeachModsHomeScreenPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs2 = preferenceManager2()
    PreferenceLayout(
        label = stringResource(id = R.string.beach_anim_home_heading),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(id = R.string.beach_home_motion_heading),
        ) {
            Item {
                ListPreference(
                    adapter = prefs2.homeMorphAnimation.getAdapter(),
                    entries = HomeMorphAnimation.listEntries(),
                    label = stringResource(id = R.string.beach_home_morph_label),
                    description = stringResource(id = R.string.beach_home_morph_description),
                )
            }
        }
    }
}
