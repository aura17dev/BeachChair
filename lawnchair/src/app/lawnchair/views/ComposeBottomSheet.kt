package app.lawnchair.views

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.FloatProperty
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.LinearLayout
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.ProvideLifecycleState
import app.lawnchair.util.minus
import com.android.launcher3.Launcher
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.views.AbstractSlideInView
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer

class ComposeBottomSheet<T>(context: Context) : AbstractSlideInView<T>(context, null, 0) where T : Context, T : ActivityContext {

    private val container = ComposeView(context)
    private var imeShift = 0f
    private var _hintCloseProgress = mutableFloatStateOf(0f)
    private var hintCloseDistance = 0f
    val hintCloseProgress: Float get() = _hintCloseProgress.floatValue
    private var registeredBackCallback: Any? = null  // OnBackAnimationCallback on API 34+

    init {
        layoutParams = BaseDragLayer.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            .apply { ignoreInsets = true }
        gravity = Gravity.BOTTOM

        mContent = LinearLayout(context).apply { addView(container) }
    }

    fun show() {
        val parent = parent
        if (parent is ViewGroup) {
            parent.removeView(this)
        }
        removeAllViews()
        addView(mContent)
        attachToContainer()
        animateOpen()
        registerPredictiveBack()
    }

    fun setContent(
        contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
        content: @Composable ComposeBottomSheet<T>.() -> Unit,
    ) {
        container.setContent {
            Providers {
                ContentWrapper(contentPaddings = contentPaddings) {
                    content(this)
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        setTranslationShift(mTranslationShift)
    }

    private fun animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.animationPlayer.isRunning) {
            return
        }
        mIsOpen = true
        setUpDefaultOpenAnimation().start()
    }

    override fun handleClose(animate: Boolean) {
        if (mActivityContext is Launcher) {
            mActivityContext.hideKeyboard()
        }
        handleClose(animate, DEFAULT_CLOSE_DURATION)
    }

    override fun onCloseComplete() {
        super.onCloseComplete()
        setSystemUiFlags(0)
        unregisterPredictiveBack()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Guaranteed cleanup: if the sheet is torn down without a completed close (activity
        // teardown, drag layer force-clear), a still-registered callback would swallow every
        // subsequent back gesture on the launcher.
        unregisterPredictiveBack()
    }

    override fun isOfType(type: Int): Boolean {
        return type and TYPE_COMPOSE_VIEW != 0
    }

    override fun getScrimColor(context: Context): Int {
        return ColorTokens.WidgetsPickerScrim.resolveColor(context)
    }

    override fun setTranslationShift(translationShift: Float) {
        mTranslationShift = translationShift
        updateContentShift()
        if (mColorScrim != null) {
            mColorScrim.alpha = 1 - mTranslationShift
        }
    }

    private fun setImeShift(shift: Float) {
        imeShift = shift
        updateContentShift()
    }

    private fun updateContentShift() {
        mContent.translationY = mTranslationShift * mContent.height + imeShift
    }

    override fun addHintCloseAnim(
        distanceToMove: Float,
        interpolator: Interpolator,
        target: PendingAnimation,
    ) {
        super.addHintCloseAnim(distanceToMove, interpolator, target)
        hintCloseDistance = distanceToMove
        target.setFloat(this, HINT_CLOSE_PROGRESS, 1f, interpolator)
    }

    private fun setSystemUiFlags(flags: Int) {
        if (mActivityContext is Launcher) {
            mActivityContext.systemUiController?.updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                flags,
            )
        }
    }

    @Suppress("ClassVerificationFailure")
    private fun registerPredictiveBack() {
        if (Build.VERSION.SDK_INT < 34) return
        val activity = mActivityContext as? Activity ?: return
        var gestureShift = 0f
        val cb = object : OnBackAnimationCallback {
            override fun onBackStarted(backEvent: BackEvent) = Unit
            override fun onBackProgressed(backEvent: BackEvent) {
                gestureShift = backEvent.progress * PREDICTIVE_BACK_FRACTION
                setTranslationShift(gestureShift)
            }
            override fun onBackCancelled() {
                val start = gestureShift
                gestureShift = 0f
                ValueAnimator.ofFloat(start, 0f).apply {
                    duration = 150
                    addUpdateListener { setTranslationShift(it.animatedValue as Float) }
                    start()
                }
            }
            override fun onBackInvoked() {
                gestureShift = 0f
                handleClose(true)
            }
        }
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            cb,
        )
        registeredBackCallback = cb
    }

    @Suppress("ClassVerificationFailure", "UNCHECKED_CAST")
    private fun unregisterPredictiveBack() {
        if (Build.VERSION.SDK_INT < 34) return
        val cb = (registeredBackCallback as? OnBackAnimationCallback) ?: return
        (mActivityContext as? Activity)?.onBackInvokedDispatcher
            ?.unregisterOnBackInvokedCallback(cb)
        registeredBackCallback = null
    }

    internal fun notifyDrag(dy: Float) {
        val h = mContent.height.takeIf { it > 0 } ?: return
        setTranslationShift((mTranslationShift + dy / h).coerceIn(0f, 1f))
    }

    internal fun notifyDragEnd(velocityY: Float) {
        if (mTranslationShift > 0.35f || velocityY > 1500f) {
            handleClose(true)
        } else {
            ValueAnimator.ofFloat(mTranslationShift, 0f).apply {
                duration = (mTranslationShift * 250L).toLong().coerceAtLeast(80L)
                interpolator = DecelerateInterpolator()
                addUpdateListener { setTranslationShift(it.animatedValue as Float) }
                start()
            }
        }
    }

    @Composable
    private fun DragHandle() {
        val dragState = rememberDraggableState { dy -> notifyDrag(dy) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> notifyDragEnd(velocity) },
                )
                .padding(top = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        }
    }

    @Composable
    private fun SystemUi(setStatusBar: Boolean = true, setNavBar: Boolean = true) {
        // todo change to isLight
        val useDarkIcons = true

        SideEffect {
            var flags = 0
            if (setStatusBar) {
                flags = flags or (
                    if (useDarkIcons) {
                        SystemUiController.FLAG_LIGHT_STATUS
                    } else {
                        SystemUiController.FLAG_DARK_STATUS
                    }
                    )
            }
            if (setNavBar) {
                flags = flags or (
                    if (useDarkIcons) {
                        SystemUiController.FLAG_LIGHT_NAV
                    } else {
                        SystemUiController.FLAG_DARK_NAV
                    }
                    )
            }
            setSystemUiFlags(flags)
        }
    }

    @Composable
    private fun Providers(
        content: @Composable () -> Unit,
    ) {
        LawnchairTheme {
            ProvideLifecycleState {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun ContentWrapper(
        contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
        content: @Composable ComposeBottomSheet<T>.() -> Unit,
    ) {
        val imePaddings = WindowInsets.ime
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .asPaddingValues()

        val translation = imePaddings - contentPaddings
        setImeShift(with(LocalDensity.current) { -translation.calculateBottomPadding().toPx() })

        SystemUi(setStatusBar = false)
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                shape = backgroundShape,
            ) {
                Column(
                    modifier = Modifier.graphicsLayer(
                        alpha = 1f - (hintCloseProgress * 0.5f),
                        translationY = hintCloseProgress * -hintCloseDistance,
                    ),
                ) {
                    DragHandle()
                    Box(modifier = Modifier.padding(contentPaddings)) {
                        content(this@ComposeBottomSheet)
                    }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_CLOSE_DURATION = 200L
        private const val PREDICTIVE_BACK_FRACTION = 0.2f
        private val backgroundShape = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)

        private val HINT_CLOSE_PROGRESS = object : FloatProperty<ComposeBottomSheet<*>>("hintCloseProgress") {

            override fun setValue(view: ComposeBottomSheet<*>, value: Float) {
                view._hintCloseProgress.floatValue = value
            }

            override fun get(view: ComposeBottomSheet<*>) = view._hintCloseProgress.floatValue
        }

        fun <T> show(
            context: T,
            contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
            content: @Composable ComposeBottomSheet<T>.() -> Unit,
        ) where T : Context, T : ActivityContext {
            closeAllOpenViews(context)
            val view = ComposeBottomSheet<T>(context)
            view.setContent(contentPaddings, content)
            view.show()
        }
    }
}
