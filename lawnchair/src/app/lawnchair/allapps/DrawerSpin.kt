package app.lawnchair.allapps

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Tumbles each app icon with a small rotation that tracks scroll velocity, then unwinds back
 * to upright when the list comes to rest.
 *
 * Fast scrolling spins icons a few degrees in the scroll direction; the rotation springs back
 * to zero on settle. Adjacent positions rotate in opposite directions so the drawer shimmers
 * as it flies past.
 */
object DrawerSpin {

    /** Maximum rotation in degrees at peak scroll velocity. */
    private const val MAX_SPIN_DEG = 12f

    /** Smoothing factor for the velocity-driven rotation. */
    private const val SMOOTH = 0.35f

    /** Settle duration back to upright after scroll stops (ms). */
    private const val SETTLE_MS = 360L

    private val installed = WeakHashMap<RecyclerView, SpinListener>()

    fun install(root: View, enabled: () -> Boolean) {
        if (root is RecyclerView) {
            if (installed[root] == null) {
                val listener = SpinListener(enabled)
                root.addOnScrollListener(listener)
                root.addOnChildAttachStateChangeListener(listener)
                installed[root] = listener
            } else {
                installed[root]?.enabled = enabled
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) install(root.getChildAt(i), enabled)
        }
    }

    private class SpinListener(
        var enabled: () -> Boolean,
    ) : RecyclerView.OnScrollListener(), RecyclerView.OnChildAttachStateChangeListener {

        /** Current rotation magnitude in degrees, shared by all children (sign varies per slot). */
        private var spin = 0f
        private var settleAnim: ValueAnimator? = null
        private var density = 0f

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (!enabled()) {
                if (abs(spin) > 0.01f || hasAnySpin(rv)) reset(rv)
                return
            }
            if (dy == 0) return
            settleAnim?.cancel()
            settleAnim = null

            if (density == 0f) density = rv.resources.displayMetrics.density
            val target = (dy / (18f * density)).coerceIn(-1f, 1f) * MAX_SPIN_DEG
            spin += (target - spin) * SMOOTH
            apply(rv)
        }

        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && abs(spin) > 0.05f) {
                startSettle(rv)
            }
        }

        override fun onChildViewAttachedToWindow(view: View) {
            val rv = view.parent as? RecyclerView ?: return
            if (!enabled()) {
                clear(view)
                return
            }
            applyToChild(view, rv)
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            clear(view)
        }

        private fun apply(rv: RecyclerView) {
            for (i in 0 until rv.childCount) applyToChild(rv.getChildAt(i), rv)
        }

        private fun applyToChild(child: View, rv: RecyclerView) {
            if (child !is BubbleTextView) return
            // Alternating slots spin opposite ways for a shimmer effect.
            val pos = rv.getChildAdapterPosition(child).coerceAtLeast(0)
            val sign = if (pos % 2 == 0) 1f else -1f
            child.pivotX = child.width * 0.5f
            child.pivotY = child.height * 0.5f
            child.rotation = spin * sign
        }

        private fun startSettle(rv: RecyclerView) {
            val start = spin
            settleAnim?.cancel()
            settleAnim = ValueAnimator.ofFloat(start, 0f).apply {
                duration = SETTLE_MS
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener {
                    spin = it.animatedValue as Float
                    apply(rv)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        spin = 0f
                        reset(rv)
                    }
                })
                start()
            }
        }

        private fun hasAnySpin(rv: RecyclerView): Boolean {
            for (i in 0 until rv.childCount) {
                if ((rv.getChildAt(i) as? BubbleTextView)?.rotation != 0f) return true
            }
            return false
        }

        private fun reset(rv: RecyclerView) {
            settleAnim?.cancel()
            spin = 0f
            for (i in 0 until rv.childCount) clear(rv.getChildAt(i))
        }

        private fun clear(view: View?) {
            (view as? BubbleTextView)?.rotation = 0f
        }
    }
}
