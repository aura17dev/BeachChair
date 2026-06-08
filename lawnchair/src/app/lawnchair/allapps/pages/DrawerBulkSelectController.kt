package app.lawnchair.allapps.pages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Themes
import com.android.launcher3.BubbleTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs

class DrawerBulkSelectController(
    context: Context,
    private val recyclerView: RecyclerView,
    private val viewModel: DrawerPageViewModel,
    scope: CoroutineScope,
) {
    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private var downX = 0f
    private var downY = 0f
    private var prevY = 0f
    private var downChild: BubbleTextView? = null
    private var isScrolling = false
    private var shouldIntercept = false
    private var velocityTracker: VelocityTracker? = null

    private val primaryColor = Themes.getAttrColor(context, android.R.attr.colorPrimary)

    private val touchListener = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    prevY = e.y
                    isScrolling = false
                    shouldIntercept = false
                    downChild = rv.findChildViewUnder(e.x, e.y) as? BubbleTextView

                    if (viewModel.isBulkSelectMode.value) {
                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(e)
                        shouldIntercept = true
                        return true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(e)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!shouldIntercept) {
                        velocityTracker?.recycle()
                        velocityTracker = null
                        downChild = null
                    }
                }
            }
            return shouldIntercept
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            velocityTracker?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dy = prevY - e.y
                    if (abs(dy) > 0) {
                        if (abs(e.y - downY) > touchSlop) isScrolling = true
                        rv.scrollBy(0, dy.toInt())
                    }
                    prevY = e.y
                }

                MotionEvent.ACTION_UP -> {
                    if (isScrolling) {
                        velocityTracker?.let { vt ->
                            vt.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                            rv.fling(0, (-vt.yVelocity).toInt())
                        }
                    } else {
                        val child = downChild ?: return
                        val info = child.tag as? AppInfo ?: return
                        viewModel.toggleAppSelection(info.toComponentKey())
                    }
                    velocityTracker?.recycle()
                    velocityTracker = null
                    shouldIntercept = false
                    downChild = null
                }

                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    shouldIntercept = false
                    downChild = null
                }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    }

    private val badgeRadius = 9f * density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }
    private val emptyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 0, 0, 0)
    }
    private val emptyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f * density
        alpha = 200
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val checkPath = Path()

    private val decoration = object : RecyclerView.ItemDecoration() {
        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (!viewModel.isBulkSelectMode.value) return
            val selectedKeys = viewModel.selectedApps.value

            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i) as? BubbleTextView ?: continue
                val info = child.tag as? AppInfo ?: continue

                val cx = child.x + child.width - badgeRadius - 3 * density
                val cy = child.y + badgeRadius + 3 * density
                val isSelected = info.toComponentKey() in selectedKeys

                if (isSelected) {
                    c.drawCircle(cx, cy, badgeRadius, fillPaint)
                    val r = badgeRadius
                    checkPath.reset()
                    checkPath.moveTo(cx - r * 0.45f, cy)
                    checkPath.lineTo(cx - r * 0.08f, cy + r * 0.42f)
                    checkPath.lineTo(cx + r * 0.48f, cy - r * 0.38f)
                    c.drawPath(checkPath, checkPaint)
                } else {
                    c.drawCircle(cx, cy, badgeRadius, emptyFillPaint)
                    c.drawCircle(cx, cy, badgeRadius, emptyStrokePaint)
                }
            }
        }
    }

    init {
        recyclerView.addOnItemTouchListener(touchListener)
        recyclerView.addItemDecoration(decoration)

        viewModel.selectedApps
            .onEach { recyclerView.invalidateItemDecorations() }
            .launchIn(scope)

        viewModel.isBulkSelectMode
            .onEach { recyclerView.invalidateItemDecorations() }
            .launchIn(scope)
    }

    fun detach() {
        velocityTracker?.recycle()
        velocityTracker = null
        recyclerView.removeOnItemTouchListener(touchListener)
        recyclerView.removeItemDecoration(decoration)
    }
}
