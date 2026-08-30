package app.beachchair.sexyspaces

import android.content.Context

internal object UnlockedLaunchGate {
    private const val PREFS_NAME = "unlocked_launch_gate"
    private const val KEY_ARMED_UNTIL = "armed_until"
    private const val WINDOW_MS = 10_000L

    fun arm(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ARMED_UNTIL, System.currentTimeMillis() + WINDOW_MS)
            .apply()
    }

    fun consume(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unlocked = prefs.getLong(KEY_ARMED_UNTIL, 0L) >= System.currentTimeMillis()
        prefs.edit().remove(KEY_ARMED_UNTIL).apply()
        return unlocked
    }
}
