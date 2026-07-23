package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.BeachModsAppDrawer
import app.lawnchair.ui.preferences.navigation.BeachModsHomeScreen
import com.android.launcher3.R

@Composable
fun BeachModsPreferences(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(id = R.string.beach_mods_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            Item {
                NavigationActionPreference(
                    label = stringResource(id = R.string.beach_drawer_heading),
                    destination = BeachModsAppDrawer,
                    subtitle = stringResource(id = R.string.beach_cat_drawer_description),
                )
            }
            Item {
                NavigationActionPreference(
                    label = stringResource(id = R.string.beach_anim_home_heading),
                    destination = BeachModsHomeScreen,
                    subtitle = stringResource(id = R.string.beach_cat_home_description),
                )
            }
        }
    }
}
