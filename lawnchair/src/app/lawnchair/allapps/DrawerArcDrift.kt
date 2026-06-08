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
 * Bends the app-drawer list into a gentle arc: icons in the upper quarter drift right, those
 * in the lower quarter drift left, and those at the center and very edges stay flush.
 *
 * The arc is always visible at a base amplitude (~8 dp) and deepens slightly with scroll
 * velocity, so it feels alive while browsing and settled when the list is at rest.
 *
 * Horizontal drift is a full sine cycle across the list height so the shape is an S-curve
 * rather than a simple lean. The pivot is centered on each icon so the arc reads as a
 * positional offset, not a rotation.
 */
object DrawerArcDrift {

    /** Base horizontal drift at rest (dp) — applied even when idle. */
    private const val BASE_AMP_DP = 8f

    /** Extra drift added at peak scroll velocity (dp). */
    private const val VELOCITY_AMP_DP = 6f

    /** Smoothing factor for the velocity-driven extra amplitude. */
    private const val SMOOTH = 0.35f

    /** Settle duration back to base amplitude after scroll stops (ms). */
    private const val SETTLE_MS = 320L

    private val installed = WeakHashMap<RecyclerView, ArcListener>()

    fun install(root: View, enabled: () -> Boolean) {
        if (root is RecyclerView) {
            if (installed[root] == null) {
                val listener = ArcListener(enabled)
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

    private class ArcListener(
        var enabled: () -> Boolean,
    ) : RecyclerView.OnScrollListener(), RecyclerView.OnChildAttachStateChangeListener {

        /** Extra amplitude riding on top of BASE_AMP_DP during scroll. */
        private var extraAmp = 0f
        private var settleAnim: ValueAnimator? = null
        private var density = 0f

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (!enabled()) {
                if (extraAmp != 0f || hasAnyDrift(rv)) reset(rv)
                return
            }
            settleAnim?.cancel()
            settleAnim = null

            if (density == 0f) density = rv.resources.displayMetrics.density
            val target = (abs(dy) / (18f * density)).coerceIn(0f, 1f) * VELOCITY_AMP_DP * density
            extraAmp += (target - extraAmp) * SMOOTH
            applyArc(rv)
        }

        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && extraAmp > 0.5f) {
                startSettle(rv)
            }
        }

        override fun onChildViewAttachedToWindow(view: View) {
            val rv = view.parent as? RecyclerView ?: return
            if (!enabled()) {
                clearDrift(view)
                return
            }
            if (density == 0f) density = rv.resources.displayMetrics.density
            val rvHeight = rv.height.toFloat().coerceAtLeast(1f)
            val totalAmp = BASE_AMP_DP * density + extraAmp
            applyArcToChild(view, rvHeight, totalAmp)
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            clearDrift(view)
        }

        private fun applyArc(rv: RecyclerView) {
            val rvHeight = rv.height.toFloat().coerceAtLeast(1f)
            val totalAmp = BASE_AMP_DP * density + extraAmp
            for (i in 0 until rv.childCount) applyArcToChild(rv.getChildAt(i), rvHeight, totalAmp)
        }

        private fun applyArcToChild(child: View, rvHeight: Float, totalAmp: Float) {
            if (child !is BubbleTextView) return
            val centerY = child.y + child.height * 0.5f
            val fraction = (centerY / rvHeight).coerceIn(0f, 1f)

            // S-curve: sin(2π·fraction) gives 0→+1→0→−1→0 across the list height.
            val curve = sin(2.0 * PI * fraction).toFloat()
            child.translationX = totalAmp * curve
        }

        private fun startSettle(rv: RecyclerView) {
            val start = extraAmp
            settleAnim?.cancel()
            settleAnim = ValueAnimator.ofFloat(start, 0f).apply {
                duration = SETTLE_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    extraAmp = it.animatedValue as Float
                    applyArc(rv)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        extraAmp = 0f
                        applyArc(rv)
                    }
                })
                start()
            }
        }

        private fun hasAnyDrift(rv: RecyclerView): Boolean {
            for (i in 0 until rv.childCount) {
                if ((rv.getChildAt(i) as? BubbleTextView)?.translationX != 0f) return true
            }
            return false
        }

        private fun reset(rv: RecyclerView) {
            settleAnim?.cancel()
            extraAmp = 0f
            for (i in 0 until rv.childCount) clearDrift(rv.getChildAt(i))
        }

        private fun clearDrift(view: View?) {
            (view as? BubbleTextView)?.translationX = 0f
        }
    }
}
