package app.lawnchair.data.wallpaper.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.room.withTransaction
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.util.bitmapToByteArray
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@LauncherAppSingleton
class WallpaperService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val db = AppDatabase.INSTANCE.get(context)
    val dao = db.wallpaperDao()

    suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        try {
            val wallpaperDrawable = wallpaperManager.drawable
            val currentBitmap = (wallpaperDrawable as BitmapDrawable).toBitmap()

            val byteArray = bitmapToByteArray(currentBitmap)

            saveWallpaper(byteArray)
        } catch (e: Exception) {
            Log.e("WallpaperChange", "Error detecting wallpaper change: ${e.message}")
        }
    }

    private fun calculateChecksum(imageData: ByteArray): String {
        return MessageDigest.getInstance("MD5")
            .digest(imageData)
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun saveWallpaper(imageData: ByteArray) {
        val timestamp = System.currentTimeMillis()
        val checksum = calculateChecksum(imageData)
        val existingWallpapers = withContext(Dispatchers.IO) { dao.getTopWallpapers() }

        if (existingWallpapers.any { it.checksum == checksum }) {
            Log.d("WallpaperService", "Wallpaper already exists with checksum: $checksum")
            return
        }

        val imagePath = saveImageToAppStorage(imageData)

        var evictedPath: String? = null
        db.withTransaction {
            if (existingWallpapers.size < 4) {
                dao.insert(
                    Wallpaper(
                        imagePath = imagePath,
                        rank = existingWallpapers.size,
                        timestamp = timestamp,
                        checksum = checksum,
                    ),
                )
            } else {
                val lowestRanked = existingWallpapers.minByOrNull { it.timestamp }
                if (lowestRanked != null) {
                    evictedPath = lowestRanked.imagePath
                    dao.deleteWallpaper(lowestRanked.id)
                    for (wallpaper in existingWallpapers) {
                        if (wallpaper.rank >= lowestRanked.rank) {
                            dao.updateRank(wallpaper.rank)
                        }
                    }
                }
                dao.insert(
                    Wallpaper(
                        imagePath = imagePath,
                        rank = 0,
                        timestamp = timestamp,
                        checksum = checksum,
                    ),
                )
            }
        }

        evictedPath?.let { deleteWallpaperFile(it) }
    }

    suspend fun updateWallpaperRank(selectedWallpaper: Wallpaper) {
        val topWallpapers = withContext(Dispatchers.IO) { dao.getTopWallpapers() }
        val currentTime = System.currentTimeMillis()

        db.withTransaction {
            dao.updateWallpaper(selectedWallpaper.id, rank = 0, timestamp = currentTime)
            for (wallpaper in topWallpapers) {
                if (wallpaper.id != selectedWallpaper.id) {
                    dao.updateRank(wallpaper.rank)
                }
            }
        }
    }

    suspend fun getTopWallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        dao.getTopWallpapers()
    }

    private fun deleteWallpaperFile(imagePath: String) {
        val file = File(imagePath)
        if (file.exists() && !file.delete()) {
            Log.w("WallpaperService", "Failed to delete wallpaper file: $imagePath")
        }
    }

    private fun saveImageToAppStorage(imageData: ByteArray): String {
        val storageDir = File(context.filesDir, "wallpapers")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageHash = imageData.hashCode().toString()
        val imageFile = File(storageDir, "wallpaper_$imageHash.jpg")

        if (!imageFile.exists()) {
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageData)
            }
        }

        return imageFile.absolutePath
    }

    override fun close() {
        // No open resources or coroutine scopes to clean up.
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getWallpaperService)
    }
}
