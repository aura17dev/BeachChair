/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.views

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RequiresApi
import app.lawnchair.LawnchairLauncher

/**
 * Softens the home screen showing through a low-opacity app-drawer background. Two parts, because two
 * different surfaces are involved:
 *
 *  1. Launcher content (workspace icons/widgets + hotseat) — blurred directly with a per-view
 *     [RenderEffect].
 *  2. The wallpaper — which lives in a separate window and CANNOT be blurred by the system on this
 *     device (SurfaceFlinger reports supports_background_blur=0 on One UI). We instead draw a
 *     pre-blurred snapshot of the wallpaper into an ImageView at the bottom of the drag layer and
 *     fade it in over the (sharp, unblurrable) real wallpaper while the drawer is open.
 *
 * All of this is API 31+ ([RenderEffect]); a no-op below that. Reading the wallpaper bitmap needs a
 * storage permission — if it's unavailable the backdrop is skipped and only the content blur applies.
 */
object DrawerHomeBlur {

    /** Pixel blur radius at strength 1.0. */
    private const val MAX_BLUR_PX = 60f
    private const val DURATION_MS = 300L

    private var animator: ValueAnimator? = null
    /** 0 = drawer closed (sharp home), 1 = drawer fully open (blurred). */
    private var openness = 0f

    private var backdrop: ImageView? = null
    private var backdropRadius = -1f

    /** Animate toward the drawer being [open], using [strengthFraction] (0..1) for the blur radius. */
    fun setOpen(launcher: LawnchairLauncher, open: Boolean, strengthFraction: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val strengthRadius = strengthFraction.coerceIn(0f, 1f) * MAX_BLUR_PX
        val target = if (open) 1f else 0f
        animator?.cancel()
        if (openness == target) {
            applyFrame(launcher, target, strengthRadius)
            return
        }
        animator = ValueAnimator.ofFloat(openness, target).apply {
            duration = DURATION_MS
            addUpdateListener {
                openness = it.animatedValue as Float
                applyFrame(launcher, openness, strengthRadius)
            }
            start()
        }
    }

    /** Immediately drop all blur (e.g. when the feature is toggled off). */
    fun clear(launcher: LawnchairLauncher) {
        animator?.cancel()
        animator = null
        openness = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) applyFrame(launcher, 0f, 0f)
    }

    /** Drop the cached wallpaper snapshot so it reloads next open (call after a wallpaper change). */
    fun invalidateWallpaper(launcher: LawnchairLauncher) {
        backdrop?.let { view -> runCatching { launcher.dragLayer.removeView(view) } }
        backdrop = null
        backdropRadius = -1f
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyFrame(launcher: LawnchairLauncher, progress: Float, strengthRadius: Float) {
        // 1) Launcher content: blur ramps with how open the drawer is.
        val contentRadius = progress * strengthRadius
        val effect = if (contentRadius < 1f) {
            null
        } else {
            RenderEffect.createBlurEffect(contentRadius, contentRadius, Shader.TileMode.CLAMP)
        }
        launcher.workspace?.setRenderEffect(effect)
        launcher.hotseat?.setRenderEffect(effect)

        // 2) Wallpaper backdrop: a pre-blurred copy fades in over the real (unblurrable) wallpaper.
        val bd = ensureBackdrop(launcher, strengthRadius)
        bd?.alpha = progress
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun ensureBackdrop(launcher: LawnchairLauncher, blurRadius: Float): ImageView? {
        if (blurRadius < 1f) return backdrop
        backdrop?.let { view ->
            if (blurRadius != backdropRadius) {
                view.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
                backdropRadius = blurRadius
            }
            return view
        }
        val drawable: Drawable = runCatching {
            WallpaperManager.getInstance(launcher).drawable
        }.getOrNull() ?: run {
            Log.w(TAG, "wallpaper drawable unavailable (missing storage permission?) — content blur only")
            return null
        }
        val view = ImageView(launcher).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(drawable)
            alpha = 0f
            isClickable = false
            isFocusable = false
            setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
        }
        val added = runCatching {
            // Index 0 = bottom of the drag layer: above the real wallpaper window, below the workspace.
            launcher.dragLayer.addView(
                view,
                0,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }.isSuccess
        if (!added) return null
        backdrop = view
        backdropRadius = blurRadius
        return view
    }

    private const val TAG = "DrawerHomeBlur"
}
