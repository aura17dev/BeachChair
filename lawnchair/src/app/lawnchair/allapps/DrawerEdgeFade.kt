package app.lawnchair.allapps

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap

/**
 * Fades icons near the top and bottom edges of the app-drawer RecyclerView, creating a depth
 * effect where only the center of the list is fully opaque — content "emerges" from a soft fog.
 *
 * The fade zone spans two icon rows from each edge. Alpha is clamped to [MIN_ALPHA, 1.0].
 * Works on all child view types (icons, headers) so the whole list blends consistently.
 */
object DrawerEdgeFade {

    /** Minimum alpha applied at the very edge of the visible list. */
    private const val MIN_ALPHA = 0.15f

    /** How many row-heights from each edge to begin fading (fractional rows are fine). */
    private const val FADE_ROWS = 2.2f

    private val installed = WeakHashMap<RecyclerView, FadeListener>()

    fun install(root: View, enabled: () -> Boolean) {
        if (root is RecyclerView) {
            if (installed[root] == null) {
                val listener = FadeListener(enabled)
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

    private class FadeListener(
        var enabled: () -> Boolean,
    ) : RecyclerView.OnScrollListener(), RecyclerView.OnChildAttachStateChangeListener {

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy == 0) return
            if (!enabled()) {
                reset(rv)
                return
            }
            applyFade(rv)
        }

        override fun onChildViewAttachedToWindow(view: View) {
            val rv = view.parent as? RecyclerView ?: return
            if (!enabled()) {
                view.alpha = 1f
                return
            }
            val rvHeight = rv.height.toFloat()
            val rowHeight = (view.height.takeIf { it > 0 } ?: (rv.height / 7).coerceAtLeast(1))
            applyFadeToChild(view, rvHeight, FADE_ROWS * rowHeight)
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            view.alpha = 1f
        }

        private fun applyFade(rv: RecyclerView) {
            val rvHeight = rv.height.toFloat()
            val fallbackRowHeight = (rv.height / 7).coerceAtLeast(1)
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                val rowHeight = child.height.takeIf { it > 0 } ?: fallbackRowHeight
                applyFadeToChild(child, rvHeight, FADE_ROWS * rowHeight)
            }
        }

        private fun applyFadeToChild(child: View, rvHeight: Float, fadeZonePx: Float) {
            val centerY = child.y + child.height * 0.5f

            val alpha = when {
                centerY < fadeZonePx ->
                    MIN_ALPHA + (1f - MIN_ALPHA) * (centerY / fadeZonePx).coerceIn(0f, 1f)
                centerY > rvHeight - fadeZonePx ->
                    MIN_ALPHA + (1f - MIN_ALPHA) * ((rvHeight - centerY) / fadeZonePx).coerceIn(0f, 1f)
                else -> 1f
            }
            child.alpha = alpha
        }

        private fun reset(rv: RecyclerView) {
            for (i in 0 until rv.childCount) rv.getChildAt(i)?.alpha = 1f
        }
    }
}
