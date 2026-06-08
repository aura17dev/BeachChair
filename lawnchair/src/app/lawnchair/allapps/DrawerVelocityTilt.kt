package app.lawnchair.allapps

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Applies a subtle 3D perspective tilt (rotationX) to the app-drawer RecyclerView while
 * the user is scrolling, giving the list a physical, weighted feel.
 *
 * The tilt angle tracks scroll velocity: fast downward scrolling tilts the list slightly
 * away from the viewer at the top; fast upward scrolling reverses it. The tilt springs back
 * to flat the moment the finger lifts, via a short settle animation.
 */
object DrawerVelocityTilt {

    /** Maximum tilt in degrees (applied at peak scroll velocity). */
    private const val MAX_TILT_DEG = 5f

    /** Smoothing factor: lower = slower/smoother response. */
    private const val SMOOTH = 0.30f

    /** Settle duration back to flat (ms). */
    private const val SETTLE_MS = 380L

    private val installed = WeakHashMap<RecyclerView, TiltListener>()

    fun install(root: View, enabled: () -> Boolean) {
        if (root is RecyclerView) {
            if (installed[root] == null) {
                val listener = TiltListener(enabled)
                root.addOnScrollListener(listener)
                installed[root] = listener
            } else {
                installed[root]?.enabled = enabled
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) install(root.getChildAt(i), enabled)
        }
    }

    private class TiltListener(
        var enabled: () -> Boolean,
    ) : RecyclerView.OnScrollListener() {

        private var tilt = 0f
        private var settleAnim: ValueAnimator? = null
        private var density = 0f

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy == 0) return
            if (!enabled()) {
                if (abs(tilt) > 0.01f) reset(rv)
                return
            }
            settleAnim?.cancel()
            settleAnim = null

            if (density == 0f) density = rv.resources.displayMetrics.density
            // Map scroll speed to tilt angle; sign determines direction.
            val target = -(dy / (20f * density)).coerceIn(-1f, 1f) * MAX_TILT_DEG
            tilt += (target - tilt) * SMOOTH
            applyTilt(rv)
        }

        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && abs(tilt) > 0.05f) {
                startSettle(rv)
            }
        }

        private fun applyTilt(rv: RecyclerView) {
            rv.rotationX = tilt
        }

        private fun startSettle(rv: RecyclerView) {
            val start = tilt
            settleAnim?.cancel()
            settleAnim = ValueAnimator.ofFloat(start, 0f).apply {
                duration = SETTLE_MS
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener {
                    tilt = it.animatedValue as Float
                    applyTilt(rv)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        tilt = 0f
                        reset(rv)
                    }
                })
                start()
            }
        }

        private fun reset(rv: RecyclerView) {
            settleAnim?.cancel()
            tilt = 0f
            rv.rotationX = 0f
        }
    }
}
