package app.lawnchair.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.core.graphics.createBitmap
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstBlockingCached
import com.android.app.animation.Interpolators
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.CellLayout
import com.android.launcher3.GestureNavContract
import com.android.launcher3.Insettable
import com.android.launcher3.QuickstepTransitionManager.CONTENT_SCALE_DURATION
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.util.Executors
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.window.RefreshRateTracker.Companion.getSingleFrameMs
import com.android.launcher3.views.FloatingIconView.getLocationBoundsForView
import com.android.launcher3.views.FloatingIconViewCompanion.setPropertiesVisible
import kotlin.math.roundToInt

class LawnchairFloatingSurfaceView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr),
    OnGlobalLayoutListener,
    Insettable,
    SurfaceHolder.Callback2 {
    private val mTmpPosition = RectF()

    private val mLauncher: LawnchairLauncher = context!!.launcher
    private val mIconPosition = RectF()
    private val mDeviceProfile = mLauncher.deviceProfile

    private val mIconBounds: Rect = Rect()
    private val mRemoveViewRunnable = Runnable { this.removeViewFromParent() }

    private val mSurfaceView: SurfaceView = SurfaceView(context)

    private var mIcon: View? = null
    private var mIconBitmap: Bitmap? = null
    private var mContract: GestureNavContract? = null
    private var mMorphAnim: HomeMorphAnimation = HomeMorphAnimation.SIGNATURE

    // The exact view we last hid via setIconVisible(false). Tracked separately from mIcon so we can
    // always restore the specific view we hid, even if mIcon is reassigned or the hotseat rebinds a
    // new BubbleTextView underneath us. Left un-restored, that view's icon stays permanently blank —
    // the "app-close icon disappears" bug, which only surfaced once GNC was enabled on Samsung.
    // (No timeout restore: the morph surface legitimately lingers on screen until the next gesture,
    // so a timer would restore the real icon mid-linger and double it. Restore at teardown instead.)
    private var mHiddenIcon: View? = null

    init {
        mSurfaceView.setLayerType(LAYER_TYPE_HARDWARE, null)
        mSurfaceView.setZOrderOnTop(true)

        mSurfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)
        mSurfaceView.holder.addCallback(this)

        mIsOpen = true
        addView(mSurfaceView)
    }

    override fun handleClose(animate: Boolean) {
        setCurrentIconVisible(true)
        // Belt-and-suspenders: if mIcon was reassigned away from the view we actually hid, the call
        // above won't restore it. This does.
        forceRestoreHiddenIcon()
        mLauncher.viewCache.recycleView(R.layout.floating_surface_view, this)
        mContract = null
        mIcon = null
        mIsOpen = false

        // Remove after some time, to avoid flickering
        Executors.MAIN_EXECUTOR.handler.postDelayed(
            mRemoveViewRunnable,
            mLauncher.getSingleFrameMs().toLong(),
        )
    }

    private fun removeViewFromParent() {
        // Never tear down while a home icon is still hidden by us.
        forceRestoreHiddenIcon()
        if (mIconBitmap != null) {
            mIconBitmap!!.recycle()
            mIconBitmap = null
        }
        mLauncher.dragLayer.removeViewInLayout(this)
    }

    private fun removeViewImmediate() {
        // Cancel any pending remove
        Executors.MAIN_EXECUTOR.handler.removeCallbacks(mRemoveViewRunnable)
        if (isAttachedToWindow) {
            removeViewFromParent()
        }
    }

    override fun isOfType(type: Int): Boolean {
        return (type and TYPE_ICON_SURFACE) != 0
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        close(false)
        removeViewImmediate()
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        getViewTreeObserver().addOnGlobalLayoutListener(this)
        updateIconLocation()
    }

    fun getLauncherContentAnimator(
        startDelay: Int,
    ): Pair<AnimatorSet?, Runnable?> {
        // The home content is not composited during the morph (the launcher window isn't resumed
        // yet), so animating it here is invisible. The visible reveal is run by the launcher on
        // resume instead (LawnchairLauncher.playCloseRevealAnimation). Here we only keep the
        // background depth/blur and the pause/resume bookkeeping.
        mLauncher.pauseExpensiveViewUpdates()
        val endListener = Runnable { mLauncher.resumeExpensiveViewUpdates() }
        val launcherAnimator = AnimatorSet()
        launcherAnimator.setStartDelay(startDelay.toLong())
        return Pair<AnimatorSet?, Runnable?>(launcherAnimator, endListener)
    }

    private fun getBackgroundAnimator(): ObjectAnimator {
        val depthController = DepthController(mLauncher)
        val targetDepth = mLauncher.stateManager.state.getDepth<LawnchairLauncher?>(mLauncher)

        val backgroundRadiusAnim = createDepthAnimator(
            depthController,
            targetDepth,
            onEnd = {
                if (Utilities.ATLEAST_R) {
                    val viewRootImpl = mLauncher.dragLayer.getViewRootImpl()
                    val parent = viewRootImpl?.surfaceControl
                    val dimLayer: SurfaceControl = SurfaceControl.Builder()
                        .setName("Blur layer")
                        .setParent(parent)
                        .setOpaque(false)
                        .setEffectLayer()
                        .build()

                    createDepthAnimator(
                        depthController,
                        mLauncher.depthController.stateDepth.value,
                    ) {
                        depthController.dispose()
                        SurfaceControl.Transaction().remove(dimLayer).apply()
                    }.start()
                } else {
                    createDepthAnimator(
                        depthController,
                        mLauncher.depthController.stateDepth.value,
                    ) {
                        depthController.dispose()
                    }.start()
                }
            },
        )

        return backgroundRadiusAnim
    }

    private fun createDepthAnimator(
        depthController: DepthController,
        targetDepth: Float,
        onEnd: (() -> Unit)? = null,
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(
            depthController.stateDepth,
            MultiPropertyFactory.MULTI_PROPERTY_VALUE,
            targetDepth,
        ).apply {
            duration = CONTENT_SCALE_DURATION.toLong() * 2
            interpolator = Interpolators.DECELERATE_2
            onEnd?.let {
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            it()
                        }
                    },
                )
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        getViewTreeObserver().removeOnGlobalLayoutListener(this)
        setCurrentIconVisible(true)
        forceRestoreHiddenIcon()
    }

    override fun onGlobalLayout() {
        updateIconLocation()
    }

    fun getIcon(): View? {
        return mLauncher.getFirstHomeElementForAppClose(
            null, /* StableViewInfo */
            mContract!!.componentName.packageName,
            mContract!!.user,
        )
    }

    override fun setInsets(insets: Rect?) {}

    private fun updateIconLocation() {
        if (mContract == null) {
            return
        }

        synchronized(this) {
            val icon = getIcon()

            // Resolve the icon's on-screen bounds BEFORE hiding it. On a cold launch
            // getFirstHomeElementForAppClose can return a view that isn't laid out yet, so its
            // location comes back as (0,0)/empty. Acting on that renders the morph surface as a ghost
            // in the top-left corner AND leaves the real dock icon hidden (blank slot). Treat an
            // unresolved location as "not ready": restore any hidden icon, hide the surface, and bail.
            // onGlobalLayout re-runs this once the icon is actually laid out.
            if (icon != null) {
                getLocationBoundsForView(mLauncher, icon, false, mTmpPosition, mIconBounds)
            }
            val locationReady = icon != null && !mIconBounds.isEmpty &&
                (mTmpPosition.left >= 1f || mTmpPosition.top >= 1f)
            if (!locationReady) {
                forceRestoreHiddenIcon()
                mSurfaceView.visibility = INVISIBLE
                return
            }
            mSurfaceView.visibility = VISIBLE

            val iconChanged = mIcon !== icon
            if (iconChanged) {
                setCurrentIconVisible(true)
                mIcon = icon
                setCurrentIconVisible(false)
            }

            if (mTmpPosition != mIconPosition) {
                mIconPosition.set(mTmpPosition)
                updateSurfaceViewLayout()
            }

            sendIconInfo()

            if (mIcon != null && iconChanged && !mIconBounds.isEmpty) {
                if (mIconBitmap == null || mIconBitmap!!.getWidth() != mIconBounds.width() || mIconBitmap!!.getHeight() != mIconBounds.height()) {
                    if (mIconBitmap != null) mIconBitmap!!.recycle()
                    mIconBitmap = createBitmap(
                        mIconBounds.width(),
                        mIconBounds.height(),
                        Bitmap.Config.ARGB_8888,
                    )
                }
                postInvalidateIconDrawing()
            }
        }
    }

    private fun postInvalidateIconDrawing() {
        synchronized(this) {
            post {
                if (mIcon == null) return@post
                drawIconOnBitmap()
                drawOnSurface()
            }
        }
    }

    private fun updateSurfaceViewLayout() {
        post {
            val lp = mSurfaceView.layoutParams as LayoutParams
            lp.width = mIconPosition.width().roundToInt()
            lp.height = mIconPosition.height().roundToInt()
            lp.leftMargin = mIconPosition.left.roundToInt()
            lp.topMargin = mIconPosition.top.roundToInt()
        }
    }

    private fun drawIconOnBitmap() {
        if (mIcon != null && !mIconBounds.isEmpty) {
            setCurrentIconVisible(true)
            try {
                val c = Canvas(mIconBitmap!!)
                c.translate(-mIconBounds.left.toFloat(), -mIconBounds.top.toFloat())
                mIcon!!.draw(c)
            } catch (t: Throwable) {
                Log.e(this.javaClass.name, "drawIconOnBitmap: ", t)
            }
            setCurrentIconVisible(false)
        }
    }

    private fun sendIconInfo() {
        if (mContract != null && Utilities.ATLEAST_Q) {
            mContract!!.sendEndPosition(mIconPosition, mLauncher, mSurfaceView.surfaceControl)
        }
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        drawOnSurface()
        sendIconInfo()
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        updateIconLocation()
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {}

    override fun surfaceRedrawNeeded(surfaceHolder: SurfaceHolder) {
        drawOnSurface()
    }

    private fun drawOnSurface() {
        val surfaceHolder = mSurfaceView.holder
        if (!surfaceHolder.surface.isValid || mIconBitmap == null) return

        synchronized(this) {
            val c = surfaceHolder.lockHardwareCanvas()
            if (c != null) {
                try {
                    c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    c.drawBitmap(mIconBitmap!!, 0f, 0f, null)
                } finally {
                    surfaceHolder.unlockCanvasAndPost(c)
                }
            }
        }
    }

    private fun setCurrentIconVisible(isVisible: Boolean) {
        val icon = mIcon ?: return
        setPropertiesVisible(icon, isVisible)
        if (isVisible) {
            if (mHiddenIcon === icon) mHiddenIcon = null
        } else {
            // Restore any previously-hidden, different view before hiding a new one so we never
            // leave two icons blank.
            mHiddenIcon?.takeIf { it !== icon }?.let { setPropertiesVisible(it, true) }
            mHiddenIcon = icon
        }
    }

    /** Unconditionally re-show whatever view we last hid; every teardown path funnels through here. */
    private fun forceRestoreHiddenIcon() {
        mHiddenIcon?.let { setPropertiesVisible(it, true) }
        mHiddenIcon = null
    }

    companion object {
        /**
         * Shows the surfaceView for the provided contract
         */
        fun show(launcher: LawnchairLauncher, contract: GestureNavContract?) {
            val view: LawnchairFloatingSurfaceView =
                launcher.viewCache.getView<LawnchairFloatingSurfaceView?>(
                    R.layout.floating_surface_view,
                    launcher,
                    launcher.dragLayer,
                )
            view.mContract = contract
            view.mIsOpen = true
            view.mMorphAnim = PreferenceManager2.getInstance(launcher)
                .homeMorphAnimation.firstBlockingCached()

            val anim = AnimatorSet()
            val startDelay = launcher.getSingleFrameMs()
            val launcherContentAnimator: Pair<AnimatorSet?, Runnable?> =
                view.getLauncherContentAnimator(startDelay)
            anim.playTogether(launcherContentAnimator.first, view.getBackgroundAnimator())
            anim.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        launcherContentAnimator.second!!.run()
                        // Cold-launch fix: the morph leaves the real dock icon hidden via
                        // setIconVisible(false) and relies on the surface to display it — but on a
                        // cold launch the captured bitmap is empty, so the slot blanks until the next
                        // gesture triggers handleClose. The morph has settled here, so restore the
                        // real icon now. It's invisible under the still-on-top surface when the bitmap
                        // is good, and shows through when the bitmap is blank.
                        view.forceRestoreHiddenIcon()
                    }
                },
            )

            view.removeViewImmediate()
            launcher.dragLayer.addView(view)
            anim.start()
            view.getIcon()?.let {
                launcher.showFullScreenOverlay(endView = it) {}
            }
        }
    }
}
