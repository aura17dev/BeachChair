/*
 * Copyright 2024, Lawnchair
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

package app.lawnchair.allapps

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A "magnifying wave" that ripples through the app-drawer icons while the user scrolls.
 *
 * Each icon is scaled by a travelling sine wave whose:
 *  - amplitude tracks the scroll speed (faster scroll = bigger magnification),
 *  - phase travels in the scroll direction, so the crest sweeps forwards when scrolling down
 *    and backwards when scrolling up.
 *
 * When scrolling stops the wave springs back down to nothing and every icon returns to scale 1.
 *
 * Companion to the open-time icon bounce in `LawnchairLauncher`; gated by the Beach Mods
 * "Icon scroll wave" preference.
 */
object IconScrollWave {

    /** Largest extra scale applied at a wave crest (1.0 + this). */
    private const val MAX_AMPLITUDE = 0.22f

    /** How many icon rows fit in one full wave (crest-to-crest). */
    private const val WAVELENGTH_ROWS = 2.4f

    /** Fraction of the scroll distance the crest travels across the screen, per frame. */
    private const val TRAVEL_FACTOR = 0.6f

    /** Maps each attached RecyclerView to its listener so we never double-install. */
    private val installed = WeakHashMap<RecyclerView, WaveListener>()

    /**
     * Recursively installs the wave on every [RecyclerView] under [root]. Idempotent — safe to
     * call again whenever the drawer opens to catch lazily-created lists (e.g. drawer pages).
     */
    fun install(root: View, enabled: () -> Boolean) {
        if (root is RecyclerView) {
            if (installed[root] == null) {
                val listener = WaveListener(enabled)
                root.addOnScrollListener(listener)
                root.addOnChildAttachStateChangeListener(listener)
                installed[root] = listener
            } else {
                installed[root]?.enabled = enabled
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                install(root.getChildAt(i), enabled)
            }
        }
    }

    private class WaveListener(
        var enabled: () -> Boolean,
    ) : RecyclerView.OnScrollListener(), RecyclerView.OnChildAttachStateChangeListener {

        private var amplitude = 0f
        private var phaseTravel = 0f
        private var settleAnim: ValueAnimator? = null

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy == 0) return
            if (!enabled()) {
                if (amplitude != 0f) reset(rv)
                return
            }
            settleAnim?.cancel()
            settleAnim = null

            val density = rv.resources.displayMetrics.density
            val wavelength = wavelengthPx(rv)
            // Phase travels in the scroll direction so the crest sweeps with the finger.
            phaseTravel += dy * (2.0 * PI / wavelength).toFloat() * TRAVEL_FACTOR

            // Amplitude follows scroll speed, smoothed so it eases in/out.
            val target = (abs(dy) / (18f * density)).coerceIn(0f, 1f) * MAX_AMPLITUDE
            amplitude += (target - amplitude) * 0.4f

            applyWave(rv, wavelength)
        }

        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && amplitude > 0.001f) {
                startSettle(rv)
            }
        }

        private fun wavelengthPx(rv: RecyclerView): Float {
            val rowHeight = rv.getChildAt(0)?.height?.takeIf { it > 0 }
                ?: (96f * rv.resources.displayMetrics.density).toInt()
            return WAVELENGTH_ROWS * rowHeight
        }

        private fun applyWave(rv: RecyclerView, wavelength: Float) {
            for (i in 0 until rv.childCount) {
                val icon = rv.getChildAt(i) as? BubbleTextView ?: continue
                val centerY = icon.y + icon.height / 2f
                val theta = 2.0 * PI * centerY / wavelength - phaseTravel
                val bump = 0.5f + 0.5f * sin(theta).toFloat()
                val scale = 1f + amplitude * bump
                icon.pivotX = icon.width / 2f
                icon.pivotY = icon.height / 2f
                icon.scaleX = scale
                icon.scaleY = scale
            }
        }

        private fun startSettle(rv: RecyclerView) {
            val wavelength = wavelengthPx(rv)
            val start = amplitude
            settleAnim?.cancel()
            settleAnim = ValueAnimator.ofFloat(start, 0f).apply {
                duration = 340
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    amplitude = it.animatedValue as Float
                    applyWave(rv, wavelength)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        amplitude = 0f
                        reset(rv)
                    }
                })
                start()
            }
        }

        private fun reset(rv: RecyclerView) {
            amplitude = 0f
            for (i in 0 until rv.childCount) {
                (rv.getChildAt(i) as? BubbleTextView)?.let {
                    it.scaleX = 1f
                    it.scaleY = 1f
                }
            }
        }

        // Reset any view leaving the screen so a recycled holder can't reappear mid-magnify.
        override fun onChildViewAttachedToWindow(view: View) = Unit

        override fun onChildViewDetachedFromWindow(view: View) {
            (view as? BubbleTextView)?.let {
                it.scaleX = 1f
                it.scaleY = 1f
            }
        }
    }
}
