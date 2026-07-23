package app.lawnchair.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstBlockingCached
import com.android.launcher3.util.Executors

/**
 * Softens the tail of the swipe-up app-close transition.
 *
 * On this device the launcher window (content AND any z-ordered surface) is not composited during
 * the system's swipe-up-home transition — see memory app-close-transition-oem-blank — so the home
 * cannot be shown during the blank. The one thing we DO control is a `setZOrderOnTop(true)` surface
 * that composites in a brief window right as the transition ends, just before/over the real content
 * pops in. We use that window to play a quick reveal of a home snapshot (soft fade + scale settle),
 * turning the hard "pop" into a gentler arrival, then hand off to the real content.
 *
 * This does NOT remove the wallpaper blank earlier in the transition; that part is system-owned.
 */
object HomeCloseReveal {

    private const val DURATION_MS = 320L
    /** Keep the final full frame briefly so it overlaps the real content before removing. */
    private const val HOLD_MS = 90L
    /** Slight initial zoom so the opaque snapshot fully covers the screen (no edge gap) as it settles. */
    private const val START_SCALE = 1.06f
    /** Fraction of the animation over which the snapshot fades in from the wallpaper. */
    private const val FADE_FRACTION = 0.45f
    /** Fallback cleanup deadline; must comfortably exceed DURATION_MS + HOLD_MS. */
    private const val WATCHDOG_MS = 2000L
    /** Snapshot capture divisor: 2 = quarter the pixels, ~4x cheaper capture and upload. */
    private const val SNAPSHOT_DOWNSCALE = 2

    fun play(launcher: LawnchairLauncher) {
        val dragLayer = launcher.dragLayer
        val w = dragLayer.width
        val h = dragLayer.height
        if (w <= 0 || h <= 0) return

        // Snapshot the home content (transparent bg; the real wallpaper stays behind it).
        // Captured at half resolution: the software draw of the whole drag layer plus the
        // full-size bitmap allocation cost a ~50 ms main-thread stall on every app close, and
        // the snapshot is only on screen for a 320 ms moving fade — the upscale is invisible.
        val bitmap = createBitmap(w / SNAPSHOT_DOWNSCALE, h / SNAPSHOT_DOWNSCALE)
        runCatching {
            val canvas = Canvas(bitmap)
            canvas.scale(1f / SNAPSHOT_DOWNSCALE, 1f / SNAPSHOT_DOWNSCALE)
            dragLayer.draw(canvas)
        }.onFailure { bitmap.recycle(); return }

        val surfaceView = SurfaceView(launcher).apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
        }
        dragLayer.addView(
            surfaceView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )

        var cleanedUp = false
        val cleanup = {
            if (!cleanedUp) {
                cleanedUp = true
                runCatching { dragLayer.removeView(surfaceView) }
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        // Watchdog: if the surface is never created (compositing quirks on some devices), the
        // normal animation-driven cleanup never runs — without this, a full-screen bitmap and a
        // stale SurfaceView would leak on every app close.
        Executors.MAIN_EXECUTOR.handler.postDelayed(cleanup, WATCHDOG_MS)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            private var started = false

            override fun surfaceCreated(holder: SurfaceHolder) {
                if (started || cleanedUp) return
                started = true
                animate(holder, bitmap, w, h) {
                    Executors.MAIN_EXECUTOR.handler.postDelayed(cleanup, HOLD_MS)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
    }

    private fun animate(
        holder: SurfaceHolder,
        bitmap: Bitmap,
        w: Int,
        h: Int,
        onEnd: () -> Unit,
    ) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val matrix = Matrix()
        val cx = w * 0.5f
        val cy = h * 0.5f

        fun drawFrame(f: Float) {
            if (!holder.surface.isValid || bitmap.isRecycled) return
            val scale = START_SCALE + (1f - START_SCALE) * f
            val alpha = (255 * (f / FADE_FRACTION).coerceIn(0f, 1f)).toInt()

            val canvas = holder.lockHardwareCanvas() ?: return
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                matrix.reset()
                // The snapshot is downscaled; scale it back up to screen size here.
                matrix.postScale(scale * SNAPSHOT_DOWNSCALE, scale * SNAPSHOT_DOWNSCALE)
                // Keep the zoom centered on screen regardless of the extra upscale factor.
                matrix.postTranslate(
                    cx - scale * SNAPSHOT_DOWNSCALE * bitmap.width * 0.5f,
                    cy - scale * SNAPSHOT_DOWNSCALE * bitmap.height * 0.5f,
                )
                paint.alpha = alpha
                canvas.drawBitmap(bitmap, matrix, paint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { drawFrame(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    drawFrame(1f)
                    onEnd()
                }
            })
            start()
        }
    }
}
