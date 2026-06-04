package app.lawnchair.allapps.views

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.RelativeLayout
import app.lawnchair.search.LawnchairSearchUiDelegate
import com.android.launcher3.R
import com.android.launcher3.allapps.LauncherAllAppsContainerView

class SearchContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LauncherAllAppsContainerView(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private var dragHandleView: View? = null

    override fun createSearchUiDelegate() = LawnchairSearchUiDelegate(this)

    override fun onFinishInflate() {
        super.onFinishInflate()
        addDragHandle()
    }

    private fun addDragHandle() {
        val handleView = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2f * density
                setColor(Color.WHITE)
            }
            alpha = 0.3f
        }
        dragHandleView = handleView
        addView(
            handleView,
            RelativeLayout.LayoutParams(
                (36 * density).toInt(),
                (4 * density).toInt(),
            ).apply {
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
                topMargin = (8 * density).toInt()
            },
        )
    }

    override fun setInsets(insets: Rect) {
        super.setInsets(insets)
        if (insets.top > 0) {
            // Do NOT add an extra paddingTop = insets.top here. The search bar
            // (AllAppsSearchInput.setInsets) and the app grid already offset themselves below
            // the status bar via their own inset-based top margins; adding it again on the
            // container double-counted the status-bar height and produced a large empty band
            // between the status bar and the search box. Keep top padding at 0.
            setPadding(paddingLeft, 0, paddingRight, paddingBottom)
            // Reposition the drag handle so it sits inside the status bar area at ~8dp
            // from the physical screen top.
            dragHandleView?.let { handle ->
                (handle.layoutParams as? RelativeLayout.LayoutParams)?.let { lp ->
                    lp.topMargin = (8 * density).toInt()
                    handle.layoutParams = lp
                }
            }
            // Immersive drawer: anchor the sheet panel (whose bounds drive the opaque drawer
            // surface drawn on the scrim) to y=0 so the dark drawer fills behind the transparent
            // status bar. With paddingTop = 0 the panel already sits at the top, so no negative
            // margin is needed.
            findViewById<View?>(R.id.bottom_sheet_background)?.let { panel ->
                (panel.layoutParams as? MarginLayoutParams)?.let { lp ->
                    if (lp.topMargin != 0) {
                        lp.topMargin = 0
                        panel.layoutParams = lp
                    }
                }
            }
        }
    }
}
