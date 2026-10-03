package com.cybai.musicplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE sourceUrl = :url LIMIT 1")
    suspend fun getSongByUrl(url: String): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long)

    @Query("UPDATE songs SET totalListenTimeMs = totalListenTimeMs + :timeMs WHERE id = :songId")
    suspend fun updateListenTime(songId: Long, timeMs: Long)

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT 10")
    fun getMostPlayedSongs(): Flow<List<Song>>

    @Query("SELECT SUM(totalListenTimeMs) FROM songs")
    fun getTotalListenTime(): Flow<Long?>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteSongs(): Flow<List<Song>>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = 0, totalListenTimeMs = 0")
    suspend fun resetAllStats()
}
