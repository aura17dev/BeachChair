/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.preferences2

import android.content.Context
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.util.Executors
import com.android.quickstep.TouchInteractionService
import com.android.quickstep.util.TISBindHelper

class ReloadHelper(private val context: Context) {

    private val idp: InvariantDeviceProfile
        get() = InvariantDeviceProfile.INSTANCE.get(context)
    private var tis: TouchInteractionService.TISBinder? = null
    private val tisBinder = TISBindHelper(context) { tis = it }

    fun reloadGrid() {
        idp.onPreferencesChanged(context)
    }

    fun recreate() {
        LawnchairLauncher.instance?.recreateIfNotScheduled()
    }

    fun restart() {
        reloadGrid()
        recreate()
    }

    /**
     * Forces a plain model + icon-cache reload.
     *
     * Note: this must NOT be used to apply icon *shape* / *theme* changes — those are stored in
     * Lawnchair's DataStore, not [LauncherPrefs], so the reload has to be sequenced after
     * [com.android.launcher3.graphics.ThemeManager] updates its icon state. That is handled by
     * `LawnchairThemeManager.verifyIconState`, which observes the shape prefs and reloads the
     * model itself. Calling this from a shape pref's `onSet` instead races that state update and
     * reloads with the stale shape (which used to make shape changes appear to do nothing).
     */
    fun reloadIcons() {
        Executors.MODEL_EXECUTOR.execute {
            LauncherAppState.INSTANCE.get(context).iconCache.clearMemoryCache()
            LauncherAppState.INSTANCE.get(context).model.reloadIfActive()
        }
    }

    fun reloadTaskbar() {
        tisBinder.runOnBindToTouchInteractionService {
            tis?.taskbarManager?.recreateTaskbars()
        }
    }
}
