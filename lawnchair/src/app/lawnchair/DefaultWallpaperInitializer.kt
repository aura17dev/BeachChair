/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sets a default wallpaper on first launch if one hasn't already been applied.
 */
object DefaultWallpaperInitializer {
    private const val TAG = "DefaultWallpaperInit"

    suspend fun applyIfNeeded(launcher: LawnchairLauncher) {
        val prefs2 = PreferenceManager2.getInstance(launcher)
        if (prefs2.defaultWallpaperApplied.firstBlocking()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(launcher)
                val drawable = ResourcesCompat.getDrawable(
                    launcher.resources,
                    R.drawable.default_wallpaper,
                    launcher.theme,
                ) ?: return@withContext

                val bitmap = drawableToBitmap(drawable)
                wallpaperManager.setBitmap(bitmap)

                prefs2.defaultWallpaperApplied.setBlocking(true)
                Log.d(TAG, "Default wallpaper applied successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply default wallpaper", e)
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
