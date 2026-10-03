package com.cybai.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cybai.musicplayer.data.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicController(context: Context) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _currentSongId = MutableStateFlow<Long?>(null)
    val currentSongId = _currentSongId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _isPlaylist = MutableStateFlow(false)
    val isPlaylist = _isPlaylist.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue = _queue.asStateFlow()

    private var controller: MediaController? = null
    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val c = controllerFuture?.get() ?: return@addListener
            controller = c
            _shuffleModeEnabled.value = c.shuffleModeEnabled
            _repeatMode.value = c.repeatMode
            
            c.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentSongId.value = mediaItem?.mediaId?.toLongOrNull()
                    _duration.value = c.duration.coerceAtLeast(0L)
                    updateQueue()
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    updateQueue()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = c.duration.coerceAtLeast(0L)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        // All Songs always loops. 
                        // Playlists loop if Repeat All is ON OR if Shuffle is ON (as requested)
                        if (!_isPlaylist.value || c.shuffleModeEnabled || c.repeatMode == Player.REPEAT_MODE_ALL) {
                            c.seekToDefaultPosition(0)
                            c.play()
                        }
                    }
                }
            })
            if (c.isPlaying) startPositionUpdates()
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                controller?.let {
                    _currentPosition.value = it.currentPosition
                    _duration.value = it.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }

    private fun updateQueue() {
        val c = controller ?: return
        val items = mutableListOf<MediaItem>()
        for (i in 0 until c.mediaItemCount) {
            items.add(c.getMediaItemAt(i))
        }
        _queue.value = items
    }

    fun playSong(song: Song, songs: List<Song> = emptyList(), isPlaylist: Boolean = false) {
        _isPlaylist.value = isPlaylist
        val c = controller ?: return
        
        if (songs.isNotEmpty()) {
            val mediaItems = songs.map { s ->
                MediaItem.Builder()
                    .setMediaId(s.id.toString())
                    .setUri(s.filePath)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).build())
                    .build()
            }
            val index = songs.indexOfFirst { it.id == song.id }
            
            // Set items and start at the selected index
            c.setMediaItems(mediaItems, index, 0L)
            
            // Force a reshuffle of the sequence if shuffle is already on
            if (c.shuffleModeEnabled) {
                c.shuffleModeEnabled = false
                c.shuffleModeEnabled = true
            }
        } else {
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.filePath)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).build())
                .build()
            c.setMediaItem(mediaItem)
        }
        c.prepare()
        c.play()
        _currentSong.value = song
    }

    fun toggleShuffle() {
        val newValue = !(_shuffleModeEnabled.value)
        controller?.let { c ->
            _shuffleModeEnabled.value = newValue
            if (newValue) {
                // To avoid the "pause/hiccup" from setMediaItems:
                // We keep the current item and reshuffle all OTHER items around it.
                val count = c.mediaItemCount
                if (count > 1) {
                    val currentIndex = c.currentMediaItemIndex
                    val otherItems = mutableListOf<MediaItem>()
                    
                    // Collect all items EXCEPT the one currently playing
                    for (i in 0 until count) {
                        if (i != currentIndex) {
                            otherItems.add(c.getMediaItemAt(i))
                        }
                    }
                    
                    otherItems.shuffle()
                    
                    // Remove all items except the current one
                    // We remove everything after the current, then everything before.
                    if (currentIndex < count - 1) {
                        c.removeMediaItems(currentIndex + 1, count)
                    }
                    if (currentIndex > 0) {
                        c.removeMediaItems(0, currentIndex)
                    }
                    
                    // Now the queue only has the current song. Add the shuffled items after it.
                    c.addMediaItems(otherItems)
                }
            }
            // Keep system shuffle off as we've manually ordered the queue
            c.shuffleModeEnabled = false
        }
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = nextMode
    }

    fun skipToNext() {
        val c = controller ?: return
        // Use the same logic as auto-advancing: loop if it's All Songs or if Playlist has Repeat/Shuffle on
        if (c.repeatMode == Player.REPEAT_MODE_OFF && !c.shuffleModeEnabled && !c.hasNextMediaItem()) {
            if (!_isPlaylist.value) {
                // All Songs loops even if repeat is off
                c.seekToDefaultPosition(0)
                c.play()
            } else {
                // Playlists stop if everything is off
                c.pause()
                c.seekToDefaultPosition(c.currentMediaItemIndex)
            }
        } else {
            c.seekToNext()
        }
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun playNext(song: Song) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.filePath)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).build())
            .build()
        controller?.addMediaItem((controller?.currentMediaItemIndex ?: 0) + 1, mediaItem)
    }

    fun skipToQueueItem(index: Int) {
        controller?.let {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture!!)
    }
}
