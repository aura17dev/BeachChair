package app.lawnchair.allapps

import android.content.Context
import app.lawnchair.LauncherDispatchers
import app.lawnchair.util.MainThreadInitializedObject
import com.android.launcher3.util.ComponentKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Tracks how often apps are launched with a recency-weighted score, so the most-launched row
 * reflects current habits rather than all-time totals.
 *
 * Each app's score decays by half every [HALF_LIFE_MS]; a launch adds 1 to the decayed score.
 *
 * The in-memory [scores] map is the source of truth: reads ([currentScore], [topApps]) never
 * touch disk, so they are safe to call on the main thread or per-keystroke during search.
 * Writes are coalesced and flushed to a small dedicated SharedPreferences file on a background
 * dispatcher. The file is hydrated once, asynchronously, on construction.
 */
class MostLaunchedTracker(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + LauncherDispatchers.dbIO)

    /** key.toString() -> [Entry]. In-memory source of truth; disk is only persistence. */
    private val scores = ConcurrentHashMap<String, Entry>()

    @Volatile private var flushJob: Job? = null

    init {
        // Hydrate off the main thread. Reads before this completes simply see no history,
        // which at worst leaves the most-used row briefly empty on the first launch after boot.
        scope.launch { hydrate() }
    }

    private fun hydrate() {
        runCatching {
            prefs.all.forEach { (keyString, value) ->
                val (score, timestamp) = parse(value as? String)
                // putIfAbsent: never clobber a launch recorded before hydration finished.
                if (timestamp > 0L) scores.putIfAbsent(keyString, Entry(score, timestamp))
            }
        }
    }

    fun recordLaunch(key: ComponentKey) {
        val now = System.currentTimeMillis()
        scores.compute(key.toString()) { _, existing ->
            val decayed = existing?.let { decay(it.score, it.timestamp, now) } ?: 0.0
            Entry(decayed + 1.0, now)
        }
        scheduleFlush()
    }

    /** Current recency-weighted score for a single app (0.0 if never launched). */
    fun currentScore(key: ComponentKey): Double {
        val entry = scores[key.toString()] ?: return 0.0
        return decay(entry.score, entry.timestamp, System.currentTimeMillis())
    }

    /** Component keys of the [count] highest-scoring apps, best first. */
    fun topApps(count: Int): List<ComponentKey> {
        val now = System.currentTimeMillis()
        return scores.entries
            .mapNotNull { (keyString, entry) ->
                val key = ComponentKey.fromString(keyString) ?: return@mapNotNull null
                key to decay(entry.score, entry.timestamp, now)
            }
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }
    }

    private fun scheduleFlush() {
        // Debounce: coalesce bursts of launches into a single disk write.
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DELAY_MS)
            flush()
        }
    }

    private fun flush() {
        runCatching {
            // Prune entries that have decayed to noise (apps not launched in months, or
            // uninstalled apps) so neither the map nor the prefs file grows without bound.
            val now = System.currentTimeMillis()
            scores.entries.removeIf { (_, entry) -> decay(entry.score, entry.timestamp, now) < PRUNE_THRESHOLD }
            // The file is dedicated to this tracker, so clear() also drops keys pruned above
            // or written by earlier versions.
            val editor = prefs.edit().clear()
            scores.forEach { (keyString, entry) ->
                editor.putString(keyString, "${entry.score}:${entry.timestamp}")
            }
            editor.apply()
        }
    }

    private fun parse(value: String?): Pair<Double, Long> {
        if (value == null) return 0.0 to 0L
        val sep = value.indexOf(':')
        if (sep < 0) return 0.0 to 0L
        val score = value.substring(0, sep).toDoubleOrNull() ?: 0.0
        val timestamp = value.substring(sep + 1).toLongOrNull() ?: 0L
        return score to timestamp
    }

    private fun decay(score: Double, timestamp: Long, now: Long): Double {
        if (score <= 0.0 || timestamp <= 0L) return 0.0
        val elapsed = (now - timestamp).coerceAtLeast(0L)
        return score * 0.5.pow(elapsed.toDouble() / HALF_LIFE_MS)
    }

    private data class Entry(val score: Double, val timestamp: Long)

    companion object {
        private const val PREFS_NAME = "most_launched_apps"
        private const val HALF_LIFE_MS = 7.0 * 24 * 60 * 60 * 1000 // 7 days
        private const val FLUSH_DELAY_MS = 2000L

        /**
         * Scores below this are indistinguishable from "never used" (a single launch decays to
         * it after ~7 half-lives ≈ 7 weeks) and are dropped at flush time.
         */
        private const val PRUNE_THRESHOLD = 0.01

        @JvmField
        val INSTANCE = MainThreadInitializedObject(::MostLaunchedTracker)
    }
}
