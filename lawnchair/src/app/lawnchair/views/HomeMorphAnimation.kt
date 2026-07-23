package app.lawnchair.views

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

/**
 * Presets for the app->home "close" animation ([LawnchairFloatingSurfaceView]).
 *
 * The visible, always-on part of the close is the home **content** (the workspace grid and the
 * dock) springing back into place as the app window shrinks away — this plays for *every* app,
 * whether or not it has an icon on the home screen. These presets parameterise that content
 * settle, so each one reads as a genuinely different motion.
 *
 * (For apps that *do* have a home/dock icon, the system additionally morphs the shrinking window
 * into that icon via GestureNavContract; that trajectory is not tunable here.)
 *
 * @param startScale         content scale at the start of the close, animating to 1 (smaller = more zoom)
 * @param startRotation      content rotation (deg) at the start, animating to 0 (kept modest for full-screen)
 * @param startTransYFraction content vertical offset at the start as a fraction of screen height, animating to 0
 * @param overshootTension   bounce on settle: 0 = smooth decelerate, higher = springier overshoot
 * @param durationMs         length of the content settle
 */
enum class HomeMorphAnimation(
    @StringRes val labelRes: Int,
    val startScale: Float,
    val startRotation: Float = 0f,
    val startTransYFraction: Float = 0f,
    val overshootTension: Float = 0f,
    val durationMs: Long,
) {
    /** Current BeachChair feel: a gentle zoom-in with a soft bounce. */
    SIGNATURE(
        R.string.beach_home_morph_signature,
        startScale = 0.85f,
        overshootTension = 1.5f,
        durationMs = 450L,
    ),

    /** Crisp and quick, almost no bounce. */
    SNAPPY(
        R.string.beach_home_morph_snappy,
        startScale = 0.92f,
        overshootTension = 0f,
        durationMs = 260L,
    ),

    /** Leans into the bounce: deeper zoom and a big springy overshoot. */
    PLAYFUL(
        R.string.beach_home_morph_playful,
        startScale = 0.7f,
        overshootTension = 4f,
        durationMs = 520L,
    ),

    /** Resolves the whole return noticeably faster. */
    FAST(
        R.string.beach_home_morph_fast,
        startScale = 0.9f,
        overshootTension = 0.5f,
        durationMs = 230L,
    ),

    /** The home swings in with a rotation and settles upright. */
    SPIN(
        R.string.beach_home_morph_spin,
        startScale = 0.6f,
        startRotation = 16f,
        overshootTension = 2.5f,
        durationMs = 540L,
    ),

    /** Big, loose, wobbly overshoot — bounces well past full size before settling. */
    JELLY(
        R.string.beach_home_morph_jelly,
        startScale = 0.5f,
        overshootTension = 7f,
        durationMs = 620L,
    ),

    /** Drops down and rotated, then rights itself with a bounce. */
    TUMBLE(
        R.string.beach_home_morph_tumble,
        startScale = 0.72f,
        startRotation = -14f,
        startTransYFraction = -0.12f,
        overshootTension = 3f,
        durationMs = 560L,
    ),

    /** Pops up from tiny and overshoots past full size before settling. */
    POP(
        R.string.beach_home_morph_pop,
        startScale = 0.3f,
        overshootTension = 5f,
        durationMs = 500L,
    ),
    ;

    companion object {
        fun fromString(s: String): HomeMorphAnimation =
            values().firstOrNull { it.name == s } ?: SIGNATURE

        @Composable
        fun listEntries(): List<ListPreferenceEntry<HomeMorphAnimation>> =
            values().map { preset ->
                ListPreferenceEntry(value = preset) { stringResource(id = preset.labelRes) }
            }
    }
}
