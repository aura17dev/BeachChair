/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppDrawerHapticFeedbackPreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.SuggestionsPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreferenceWithPreview
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.AppDrawerHiddenApps
import com.android.launcher3.R

object AppDrawerRoutes {
    const val HIDDEN_APPS = "hiddenApps"
}

@Composable
fun AppDrawerPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val resources = context.resources

    PreferenceLayout(
        label = stringResource(id = R.string.app_drawer_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
            DrawerLayoutPreference(prefs2.enableDrawerPages.getAdapter())
        val hiddenApps = prefs2.hiddenApps.getAdapter().state.value
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            Item {
                NavigationActionPreference(
                    label = stringResource(id = R.string.hidden_apps_label),
                    destination = AppDrawerHiddenApps,
                    subtitle = resources.getQuantityString(R.plurals.apps_count, hiddenApps.size, hiddenApps.size),
                )
            }
            Item { SearchBarPreference(SearchRoute.DRAWER_SEARCH, showLabel = false) }
            Item { AppDrawerFolderPreferenceItem() }
            SuggestionsPreference()
            AppDrawerHapticFeedbackPreference()
        }
        PreferenceGroup(heading = stringResource(R.string.style)) {
            Item { ColorPreference(preference = prefs2.appDrawerBackgroundColor) }
            Item {
                SliderPreference(
                    label = stringResource(id = R.string.background_opacity),
                    adapter = prefs.drawerOpacity.getAdapter(),
                    step = 0.1f,
                    valueRange = 0F..1F,
                    showAsPercentage = true,
                )
            }
            val blurHomeAdapter = prefs.drawerBlurHome.getAdapter()
            Item {
                SwitchPreference(
                    label = stringResource(id = R.string.drawer_blur_home_label),
                    description = stringResource(id = R.string.drawer_blur_home_description),
                    adapter = blurHomeAdapter,
                )
            }
            if (blurHomeAdapter.state.value) {
                Item {
                    SliderPreference(
                        label = stringResource(id = R.string.drawer_blur_home_strength_label),
                        adapter = prefs.drawerBlurHomeRadius.getAdapter(),
                        step = 0.1f,
                        valueRange = 0F..1F,
                        showAsPercentage = true,
                    )
                }
            }
            Item { ColorPreference(preference = prefs2.workProfileTabBackgroundColor) }
            Item {
                SwitchPreference(
                    label = stringResource(id = R.string.work_profile_tab_container_background_label),
                    adapter = prefs2.workProfileTabContainerBackground.getAdapter(),
                )
            }
            Item {
                SwitchPreference(
                    label = stringResource(id = R.string.pref_all_apps_search_bar_background),
                    adapter = prefs2.appDrawerSearchBarBackground.getAdapter(),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            Item {
                SliderPreference(
                    label = stringResource(id = R.string.app_drawer_columns),
                    adapter = prefs2.drawerColumns.getAdapter(),
                    step = 1,
                    valueRange = 3..10,
                )
            }
            Item {
                SliderPreference(
                    adapter = prefs2.drawerCellHeightFactor.getAdapter(),
                    label = stringResource(id = R.string.row_height_label),
                    valueRange = 0.3F..1.5F,
                    step = 0.1F,
                    showAsPercentage = true,
                )
            }
            Item {
                SliderPreference(
                    adapter = prefs2.drawerLeftRightMarginFactor.getAdapter(),
                    label = stringResource(id = R.string.app_drawer_indent_label),
                    valueRange = 0.0F..1.5F,
                    step = 0.05F,
                    showAsPercentage = true,
                )
            }
        }
        val showDrawerLabels = prefs2.showIconLabelsInDrawer.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            Item {
                SliderPreference(
                    label = stringResource(id = R.string.icon_sizes),
                    adapter = prefs2.drawerIconSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
            Item {
                SwitchPreference(
                    adapter = showDrawerLabels,
                    label = stringResource(id = R.string.show_labels),
                )
            }
            Item(
                "drawer_icon_label_size",
                showDrawerLabels.state.value,
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.drawerIconLabelSizeFactor.getAdapter(),
                    step = 0.1F,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
            Item(
                "drawer_label_twoline",
                showDrawerLabels.state.value,
            ) {
                SwitchPreference(
                    adapter = prefs2.twoLineAllApps.getAdapter(),
                    label = stringResource(R.string.twoline_label),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.advanced)) {
            Item {
                SwitchPreference(
                    label = stringResource(id = R.string.pref_all_apps_remember_position_title),
                    description = stringResource(id = R.string.pref_all_apps_remember_position_description),
                    adapter = prefs2.rememberPosition.getAdapter(),
                )
            }
            Item {
                SwitchPreference(
                    label = stringResource(id = R.string.pref_all_apps_show_scrollbar_title),
                    adapter = prefs2.showScrollbar.getAdapter(),
                )
            }
        }
        PreferenceGroup(heading = "AI Sort") {
            Item {
                app.lawnchair.ui.preferences.components.controls.TextPreference(
                    label = "DeepSeek API Key",
                    description = { if (it.isBlank()) "Tap to set. Get yours at platform.deepseek.com" else "sk-…${it.takeLast(4)}" },
                    adapter = prefs2.deepSeekApiKey.getAdapter(),
                )
            }
            Item {
                SwitchPreference(
                    label = "Auto-sort new apps",
                    description = "When a new app is installed, automatically assign it to the best matching drawer page",
                    adapter = prefs2.aiAutoSortNewApps.getAdapter(),
                )
            }
        }
    }
}

@Composable
private fun DrawerLayoutPreference(enableDrawerPagesAdapter: PreferenceAdapter<Boolean>) {
    SwitchPreferenceWithPreview(
        label = stringResource(id = R.string.layout),
        adapter = enableDrawerPagesAdapter,
        disabledLabel = stringResource(id = R.string.feed_default),
        disabledContent = {
            // Search bar
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .fillMaxWidth(0.8f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp),
                    ),
            )
            // App icon row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                    )
                }
            }
        },
        enabledLabel = stringResource(id = R.string.beach_drawer_pages_label),
        enabledContent = {
            // Search bar
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .fillMaxWidth(0.8f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp),
                    ),
            )
            // App icon row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                    )
                }
            }
            // Page tab indicators: one active pill + two inactive dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 5.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(3.dp),
                        ),
                )
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                CircleShape,
                            ),
                    )
                }
            }
        },
    )
}
