package com.cybai.musicplayer.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.cybai.musicplayer.data.*
import com.cybai.musicplayer.playback.MusicController
import com.cybai.musicplayer.worker.DownloadWorker
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MusicDatabase.getDatabase(application)
    private val songDao = database.songDao()
    private val playlistDao = database.playlistDao()
    private val workManager = WorkManager.getInstance(application)
    
    val musicController = MusicController(application)

    val allSongs = songDao.getAllSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favoriteSongs = songDao.getFavoriteSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val playlists = playlistDao.getAllPlaylists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val mostPlayed = songDao.getMostPlayedSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val totalListenTime = songDao.getTotalListenTime().stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    enum class SortOrder {
        NAME_ASC, NAME_DESC, DATE_ADDED
    }

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val sortedSongs = combine(allSongs, _sortOrder, _searchQuery) { songs, order, query ->
        val filtered = if (query.isBlank()) {
            songs
        } else {
            songs.filter { it.title.contains(query, ignoreCase = true) }
        }
        
        when (order) {
            SortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOrder.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _downloadStatus = MutableStateFlow<WorkInfo?>(null)
    val downloadStatus = _downloadStatus.asStateFlow()

    init {
        syncFilesWithDatabase()
    }

    private fun syncFilesWithDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val musicDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return@launch
            val files = musicDir.listFiles { file -> file.isFile && file.extension == "mp3" } ?: return@launch
            val filePathsOnDisk = files.map { it.absolutePath }.toSet()
            
            val currentSongs = songDao.getAllSongs().first()
            
            // 1. Remove songs from DB that are no longer on disk
            currentSongs.forEach { song ->
                if (song.filePath !in filePathsOnDisk) {
                    songDao.deleteSong(song)
                    Log.d("MainViewModel", "Removed stale song from DB: ${song.title}")
                }
            }

            // 2. Add new songs found on disk to DB
            val currentPathsInDb = currentSongs.map { it.filePath }.toSet()
            val retriever = MediaMetadataRetriever()
            files.forEach { file ->
                if (file.absolutePath !in currentPathsInDb) {
                    var durationMs = 0L
                    try {
                        retriever.setDataSource(file.absolutePath)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to extract duration for ${file.name}", e)
                    }

                    val song = Song(
                        title = file.nameWithoutExtension,
                        filePath = file.absolutePath,
                        durationMs = durationMs,
                        sourceUrl = "local_sync",
                        dateAdded = System.currentTimeMillis()
                    )
                    songDao.insertSong(song)
                }
            }
            retriever.release()
        }
    }

    fun downloadSong(url: String) {
        viewModelScope.launch {
            // Check initialization again before starting
            try {
                YoutubeDL.getInstance()
                FFmpeg.getInstance()
            } catch (e: Exception) {
                // Try to init again if failed
                try {
                    YoutubeDL.getInstance().init(getApplication())
                    FFmpeg.getInstance().init(getApplication())
                } catch (inner: Exception) {
                    Log.e("MainViewModel", "Library initialization failed", inner)
                    // We can't easily show a toast from VM without a channel or similar, 
                    // but we can update a state
                    return@launch
                }
            }

            if (songDao.getSongByUrl(url) != null) {
                return@launch
            }

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("url" to url))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            workManager.enqueueUniqueWork("download_$url", ExistingWorkPolicy.KEEP, request)
            
            workManager.getWorkInfoByIdFlow(request.id).collect {
                _downloadStatus.value = it
            }
        }
    }

    fun playSong(song: Song) {
        musicController.playSong(song, sortedSongs.value, isPlaylist = false)
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            // Delete file
            val file = File(song.filePath)
            if (file.exists()) file.delete()
            
            // Delete from DB
            playlistDao.deleteSongFromAllPlaylists(song.id)
            songDao.deleteSong(song)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistDao.insertPlaylist(Playlist(name = name))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            // Room should handle cross-references if configured with cascade, 
            // but we can manually clean up if needed.
            // Based on PlaylistDao, we have a deleteSongFromAllPlaylists, 
            // but not a deleteAllSongsFromPlaylist. 
            // However, usually we just delete the playlist and its references.
            playlistDao.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(songId: Long, playlistId: Long) {
        viewModelScope.launch {
            playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> {
        return playlistDao.getSongsInPlaylist(playlistId)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            songDao.updateFavoriteStatus(song.id, !song.isFavorite)
        }
    }

    fun playNext(song: Song) {
        musicController.playNext(song)
    }

    fun resetStats() {
        viewModelScope.launch(Dispatchers.IO) {
            songDao.resetAllStats()
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicController.release()
    }
}
