package app.lawnchair.allapps

import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap

/**
 * Animates rows into view as the user scrolls through the app-drawer, creating a cascading
 * waterfall reveal effect.
 *
 * When the user scrolls down, newly revealed rows slide up from below and fade in. When
 * scrolling up, they slide down from above. Each row entering the leading edge gets a brief
 * (180 ms) translateY + alpha entrance. The stagger delay per row position gives the cascade
 * its waterfall feel.
 *
 * Views are properly reset on detach so recycled holders never reappear mid-animation.
 */
object DrawerRowCascade {

    /** Distance (dp) the entering row slides from. */
    private const val SLIDE_DP = 28f

    /** Animation duration per row (ms). */
    private const val ANIM_MS = 200L

    /** Extra delay (ms) added per stagger slot, cycling over STAGGER_CYCLE slots. */
    private const val STAGGER_MS = 20L
    private const val STAGGER_CYCLE = 5

    private val installed = WeakHashMap<RecyclerView, CascadeListener>()
    private val interpolator = DecelerateInterpolator(1.5f)

    fun install(root: View, enabled: () -> Boolean) {
        if (root is RecyclerView) {
            if (installed[root] == null) {
                val listener = CascadeListener(enabled)
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

    private class CascadeListener(
        var enabled: () -> Boolean,
    ) : RecyclerView.OnScrollListener(), RecyclerView.OnChildAttachStateChangeListener {

        /** +1 = scrolling down, -1 = scrolling up, 0 = idle */
        @Volatile private var scrollDir = 0
        private var density = 0f

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy != 0) scrollDir = if (dy > 0) 1 else -1
        }

        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) scrollDir = 0
        }

        override fun onChildViewAttachedToWindow(view: View) {
            if (!enabled() || scrollDir == 0) {
                // Ensure no leftover state on a recycled view.
                view.alpha = 1f
                view.translationY = 0f
                return
            }
            val dir = scrollDir
            val rv = view.parent as? RecyclerView ?: return
            if (density == 0f) density = rv.resources.displayMetrics.density
            val slidePx = SLIDE_DP * density

            // Use adapter position modulo cycle so nearby rows stagger without large delays.
            val pos = rv.getChildAdapterPosition(view).coerceAtLeast(0)
            val delay = (pos % STAGGER_CYCLE) * STAGGER_MS

            // Set starting state before layout so the view is invisible for one frame.
            view.alpha = 0f
            view.translationY = if (dir > 0) slidePx else -slidePx

            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(ANIM_MS)
                .setInterpolator(interpolator)
                .start()
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            view.animate().cancel()
            view.alpha = 1f
            view.translationY = 0f
        }
    }
}
