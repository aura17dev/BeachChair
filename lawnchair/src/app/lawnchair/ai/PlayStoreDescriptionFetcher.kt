package app.lawnchair.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlayStoreDescriptionFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // LRU-evicting cache: drops the least-recently-used entry beyond MAX_CACHE_SIZE.
    // All reads and writes are guarded by cacheMutex; fetchAll launches many concurrent
    // coroutines that all touch this map.
    private val cacheMutex = Mutex()
    private val cache = object : LinkedHashMap<String, String?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?) = size > MAX_CACHE_SIZE
    }

    // Limits outbound network parallelism.
    private val semaphore = Semaphore(10)

    private val metaDescRegex = Regex("""<meta name="description" content="([^"]{10,300})"""")
    private val htmlEntities = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'")

    suspend fun fetchAll(packageNames: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            packageNames
                .map { pkg -> async { pkg to fetch(pkg) } }
                .awaitAll()
                .mapNotNull { (pkg, desc) -> desc?.let { pkg to it } }
                .toMap()
        }

    private suspend fun fetch(packageName: String): String? {
        // Fast path: check the cache without hitting the network.
        cacheMutex.withLock {
            if (cache.containsKey(packageName)) return cache[packageName]
        }

        return semaphore.withPermit {
            // Re-check inside the permit in case a concurrent coroutine finished first.
            cacheMutex.withLock {
                if (cache.containsKey(packageName)) return@withPermit cache[packageName]
            }

            try {
                val request = Request.Builder()
                    .url("https://play.google.com/store/apps/details?id=$packageName&hl=en")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .build()
                val body = client.newCall(request).execute().use { it.body?.string() }
                if (body == null) {
                    cacheMutex.withLock { cache[packageName] = null }
                    return@withPermit null
                }
                val raw = metaDescRegex.find(body)?.groupValues?.get(1)?.trim()
                if (raw == null) {
                    cacheMutex.withLock { cache[packageName] = null }
                    return@withPermit null
                }
                val cleaned = htmlEntities.entries.fold(raw) { s, (entity, char) -> s.replace(entity, char) }
                val desc = cleaned.substringBefore(". ").take(120).trim()
                cacheMutex.withLock { cache[packageName] = desc }
                desc
            } catch (e: Exception) {
                cacheMutex.withLock { cache[packageName] = null }
                null
            }
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 150
    }
}
