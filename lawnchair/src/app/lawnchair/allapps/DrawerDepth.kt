package app.lawnchair.allapps

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Gives the app-drawer a depth-of-field feel: icons near the vertical center sit at full size
 * and opacity, while those toward the top and bottom edges shrink and dim slightly, as if the
 * list were wrapped around a shallow cylinder.
 *
 * The effect is purely positional — it updates as rows move through the viewport on scroll and
 * as new rows attach — so it reads as a steady depth gradient rather than a motion animation.
 */
object DrawerDepth {

    /** How much an icon shrinks at the very edge (fraction of full size). */
    private const val SCALE_FALLOFF = 0.18f

    /** How much an icon dims at the very edge (fraction of full opacity). */
    private const val ALPHA_FALLOFF = 0.45f

    private val installed = WeakHashMap<RecyclerView, DepthListener>()

    fun install(root: View, enabled: () -> Boolean) {
        if (root is RecyclerView) {
            if (installed[root] == null) {
                val listener = DepthListener(enabled)
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

    private class DepthListener(
        var enabled: () -> Boolean,
    ) : RecyclerView.OnScrollListener(), RecyclerView.OnChildAttachStateChangeListener {

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (!enabled()) {
                if (hasAnyDepth(rv)) reset(rv)
                return
            }
            apply(rv)
        }

        override fun onChildViewAttachedToWindow(view: View) {
            val rv = view.parent as? RecyclerView ?: return
            if (!enabled()) {
                clear(view)
                return
            }
            applyToChild(view, rv.height.toFloat().coerceAtLeast(1f))
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            clear(view)
        }

        private fun apply(rv: RecyclerView) {
            val h = rv.height.toFloat().coerceAtLeast(1f)
            for (i in 0 until rv.childCount) applyToChild(rv.getChildAt(i), h)
        }

        private fun applyToChild(child: View, rvHeight: Float) {
            if (child !is BubbleTextView) return
            val centerY = child.y + child.height * 0.5f
            val fraction = (centerY / rvHeight).coerceIn(0f, 1f)
            // 0 at the vertical center, 1 at either edge.
            val dist = abs(fraction - 0.5f) * 2f
            val scale = 1f - SCALE_FALLOFF * dist
            child.pivotX = child.width * 0.5f
            child.pivotY = child.height * 0.5f
            child.scaleX = scale
            child.scaleY = scale
            child.alpha = 1f - ALPHA_FALLOFF * dist
        }

        private fun hasAnyDepth(rv: RecyclerView): Boolean {
            for (i in 0 until rv.childCount) {
                val c = rv.getChildAt(i) as? BubbleTextView ?: continue
                if (c.scaleX != 1f || c.alpha != 1f) return true
            }
            return false
        }

        private fun reset(rv: RecyclerView) {
            for (i in 0 until rv.childCount) clear(rv.getChildAt(i))
        }

        private fun clear(view: View?) {
            (view as? BubbleTextView)?.apply {
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
            }
        }
    }
}
