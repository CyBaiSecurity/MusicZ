package com.cybai.musicplayer

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MusicApplication", "Application onCreate started")
        initializeYoutubeDL()
    }

    private fun initializeYoutubeDL() {
        try {
            Log.d("MusicApplication", "Initializing YoutubeDL and FFmpeg...")
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            
            // Update yt-dlp in the background to avoid blocking startup
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("MusicApplication", "Updating yt-dlp...")
                    YoutubeDL.getInstance().updateYoutubeDL(this@MusicApplication)
                    Log.d("MusicApplication", "yt-dlp update successful")
                } catch (e: Exception) {
                    Log.e("MusicApplication", "failed to update yt-dlp", e)
                }
            }
            
            Log.d("MusicApplication", "Initialization successful")
        } catch (e: Exception) {
            Log.e("MusicApplication", "failed to initialize youtubedl-android", e)
        }
    }
}
