package com.cybai.musicplayer.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cybai.musicplayer.data.MusicDatabase
import kotlinx.coroutines.*

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastUpdateJob: Job? = null
    private var lastPlayCountIncrementSongId: Long? = null
    private var lastPlayerPosition: Long = 0

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .build()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startStatsTracking()
                    incrementPlayCountIfNeeded()
                } else {
                    stopStatsTracking()
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                lastPlayCountIncrementSongId = null
                lastPlayerPosition = 0
                if (exoPlayer?.isPlaying == true) {
                    incrementPlayCountIfNeeded()
                }
            }
        })
    }

    private fun incrementPlayCountIfNeeded() {
        val songId = exoPlayer?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (songId != lastPlayCountIncrementSongId) {
            lastPlayCountIncrementSongId = songId
            serviceScope.launch {
                MusicDatabase.getDatabase(this@PlaybackService).songDao().incrementPlayCount(songId)
            }
        }
    }

    private fun startStatsTracking() {
        lastUpdateJob?.cancel()
        lastPlayerPosition = exoPlayer?.currentPosition ?: 0
        lastUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(5000) // Update every 5 seconds
                updateListenTime()
            }
        }
    }

    private fun stopStatsTracking() {
        lastUpdateJob?.cancel()
        updateListenTime()
    }

    private fun updateListenTime() {
        val songId = exoPlayer?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val currentPosition = exoPlayer?.currentPosition ?: return
        val delta = currentPosition - lastPlayerPosition
        if (delta > 0) {
            serviceScope.launch {
                MusicDatabase.getDatabase(this@PlaybackService).songDao().updateListenTime(songId, delta)
            }
        }
        lastPlayerPosition = currentPosition
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0 || player?.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
