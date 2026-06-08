package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.allapps.DrawerScrollAnimation
import app.lawnchair.preferences.getAdapter
import app.lawnchair.views.HomeMorphAnimation
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun BeachModsPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs2 = preferenceManager2()
    val swipeFadeEnabled by prefs2.drawerPageSwipeFade.getAdapter()
    PreferenceLayout(
        label = stringResource(id = R.string.beach_mods_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(id = R.string.beach_animations_heading),
        ) {
            Item {
                SwitchPreference(
                    adapter = prefs2.iconBounce.getAdapter(),
                    label = stringResource(id = R.string.beach_icon_bounce_label),
                    description = stringResource(id = R.string.beach_icon_bounce_description),
                )
            }
            Item {
                ListPreference(
                    adapter = prefs2.homeMorphAnimation.getAdapter(),
                    entries = HomeMorphAnimation.listEntries(),
                    label = stringResource(id = R.string.beach_home_morph_label),
                    description = stringResource(id = R.string.beach_home_morph_description),
                )
            }
            Item {
                ListPreference(
                    adapter = prefs2.drawerScrollAnimation.getAdapter(),
                    entries = DrawerScrollAnimation.listEntries(),
                    label = stringResource(id = R.string.beach_drawer_scroll_animation_label),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs2.drawerOvershoot.getAdapter(),
                    label = stringResource(id = R.string.beach_drawer_overshoot_label),
                    description = stringResource(id = R.string.beach_drawer_overshoot_description),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs2.pixelatedFade.getAdapter(),
                    label = stringResource(id = R.string.beach_pixelated_fade_label),
                    description = stringResource(id = R.string.beach_pixelated_fade_description),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs2.scaleBounce.getAdapter(),
                    label = stringResource(id = R.string.beach_scale_bounce_label),
                    description = stringResource(id = R.string.beach_scale_bounce_description),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs2.drawerPageSwipeFade.getAdapter(),
                    label = stringResource(id = R.string.beach_drawer_page_swipe_fade_label),
                    description = stringResource(id = R.string.beach_drawer_page_swipe_fade_description),
                )
            }
            Item(visible = swipeFadeEnabled) {
                SliderPreference(
                    adapter = prefs2.drawerPageSwipeFadeDuration.getAdapter(),
                    label = stringResource(id = R.string.beach_drawer_page_swipe_fade_duration_label),
                    valueRange = 100..600,
                    step = 50,
                    showUnit = "ms",
                )
            }
        }
    }
}
