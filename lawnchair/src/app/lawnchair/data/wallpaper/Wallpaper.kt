package app.lawnchair.data.wallpaper

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "Wallpapers", indices = [Index(value = ["timestamp"])])
data class Wallpaper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val rank: Int,
    val timestamp: Long,
    val checksum: String? = null,
)
