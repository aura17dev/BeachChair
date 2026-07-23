package app.lawnchair.allapps

import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import java.util.WeakHashMap

/**
 * Pops each app icon into view with a springy overshoot as rows are revealed during scrolling.
 *
 * Newly attached icons start small and transparent, then spring up to full size with a slight
 * bounce. Popping is gated on active scrolling so opening the drawer doesn't fire a pop storm;
 * icons are reset on detach so recycled holders never reappear mid-pop.
 */
object DrawerPop {

    /** Scale each icon starts from before popping to full size. */
    private const val START_SCALE = 0.6f

    /** Pop duration per icon (ms). */
    private const val ANIM_MS = 260L

    /** Overshoot tension — higher = bouncier. */
    private const val TENSION = 2.5f

    private val installed = WeakHashMap<RecyclerView, PopListener>()
    private val interpolator = OvershootInterpolator(TENSION)

    fun install(root: View, enabled: () -> Boolean) {
        if (root is RecyclerView) {
            if (installed[root] == null) {
                val listener = PopListener(enabled)
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

    private class PopListener(
        var enabled: () -> Boolean,
    ) : RecyclerView.OnScrollListener(), RecyclerView.OnChildAttachStateChangeListener {

        @Volatile private var scrolling = false

        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            scrolling = newState != RecyclerView.SCROLL_STATE_IDLE
        }

        override fun onChildViewAttachedToWindow(view: View) {
            if (view !is BubbleTextView) return
            if (!enabled() || !scrolling) {
                clear(view)
                return
            }
            view.pivotX = view.width * 0.5f
            view.pivotY = view.height * 0.5f
            view.scaleX = START_SCALE
            view.scaleY = START_SCALE
            view.alpha = 0f
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(ANIM_MS)
                .setInterpolator(interpolator)
                .start()
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            if (view !is BubbleTextView) return
            view.animate().cancel()
            clear(view)
        }

        private fun clear(view: BubbleTextView) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
        }
    }
}
