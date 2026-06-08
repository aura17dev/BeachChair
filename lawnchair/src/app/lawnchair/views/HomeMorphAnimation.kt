package app.lawnchair.views

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

/**
 * Tuning presets for the app->home "morph back into icon" animation
 * ([LawnchairFloatingSurfaceView]). The morph *trajectory* (the app window shrinking) is
 * driven by the system via GestureNavContract and is NOT tunable here. What these presets
 * control is the part BeachChair owns: the icon's spring "landing" bounce and how quickly the
 * workspace scales back into place behind it.
 *
 * @param stiffness          spring stiffness for the icon landing (see SpringForce.STIFFNESS_*)
 * @param dampingRatio       spring damping for the icon landing (see SpringForce.DAMPING_RATIO_*)
 * @param velocityScale      multiplier on the icon's launch velocity (higher = more travel/overshoot)
 * @param contentDurationMult multiplier on CONTENT_SCALE_DURATION for the workspace zoom-in
 */
enum class HomeMorphAnimation(
    @StringRes val labelRes: Int,
    val stiffness: Float,
    val dampingRatio: Float,
    val velocityScale: Float,
    val contentDurationMult: Float,
) {
    /** Current BeachChair feel: loose, playful low-stiffness wobble. */
    SIGNATURE(
        R.string.beach_home_morph_signature,
        stiffness = 200f,
        dampingRatio = 0.2f,
        velocityScale = 1f,
        contentDurationMult = 3f,
    ),

    /** Crisp, precise settle with minimal bounce and a quicker zoom. */
    SNAPPY(
        R.string.beach_home_morph_snappy,
        stiffness = 1500f,
        dampingRatio = 0.75f,
        velocityScale = 0.5f,
        contentDurationMult = 2f,
    ),

    /** Leans into the spring: more overshoot and wobble on landing. */
    PLAYFUL(
        R.string.beach_home_morph_playful,
        stiffness = 200f,
        dampingRatio = 0.2f,
        velocityScale = 1.7f,
        contentDurationMult = 3f,
    ),

    /** Keeps a little bounce but resolves the whole return noticeably faster. */
    FAST(
        R.string.beach_home_morph_fast,
        stiffness = 350f,
        dampingRatio = 0.5f,
        velocityScale = 1f,
        contentDurationMult = 1.75f,
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
