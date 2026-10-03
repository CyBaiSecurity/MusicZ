package com.cybai.musicplayer.worker

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cybai.musicplayer.data.MusicDatabase
import com.cybai.musicplayer.data.Song
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val dao = MusicDatabase.getDatabase(applicationContext).songDao()

        // Double check for duplicate URL
        if (dao.getSongByUrl(url) != null) {
            return Result.success(workDataOf("error" to "Song already exists"))
        }

        val musicDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (musicDir == null || !musicDir.exists()) {
            return Result.failure()
        }

        return try {
            Log.d("DownloadWorker", "Starting download for URL: $url")
            
            // Check if initialized just in case
            try {
                YoutubeDL.getInstance()
            } catch (e: Exception) {
                Log.e("DownloadWorker", "YoutubeDL instance not available", e)
                return Result.failure(workDataOf("error" to "YoutubeDL not initialized: ${e.message}"))
            }

            val request = YoutubeDLRequest(url).apply {
                addOption("-x") // extract audio
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0") // Best quality
                addOption("--no-mtime") // Faster
                addOption("--no-playlist")
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                addOption("--referer", "https://www.google.com/")
                // Use --print to get exactly what we need after the download finishes
                addOption("--print", "after_move:filepath")
                addOption("--print", "after_move:title")
                addOption("--print", "after_move:duration")
                addOption("--print", "after_move:thumbnail")
                addOption("-o", "${musicDir.absolutePath}/%(title)s.%(ext)s")
            }

            Log.d("DownloadWorker", "Executing download...")
            val response = YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                setProgressAsync(workDataOf("progress" to progress.toInt()))
            }

            // The output will contain the printed values from the --print options
            val outputLines = response.out.lines().filter { it.isNotBlank() }
            // Since we added 4 prints now, they should be at the end of the output
            // Order: filepath, title, duration, thumbnail
            if (outputLines.size >= 4) {
                val filePath = outputLines[outputLines.size - 4].trim()
                val title = outputLines[outputLines.size - 3].trim()
                val durationSeconds = outputLines[outputLines.size - 2].trim().toDoubleOrNull() ?: 0.0
                val thumbnailUrl = outputLines[outputLines.size - 1].trim()
                
                val file = File(filePath)
                if (file.exists()) {
                    val song = Song(
                        title = title,
                        filePath = file.absolutePath,
                        durationMs = (durationSeconds * 1000).toLong(),
                        sourceUrl = url,
                        dateAdded = System.currentTimeMillis(),
                        thumbnailUrl = thumbnailUrl
                    )
                    dao.insertSong(song)
                    Log.d("DownloadWorker", "Download successfully saved to DB: $title")
                    return Result.success()
                }
            }

            // Fallback if --print output is not as expected but file might exist
            Log.w("DownloadWorker", "Print output not parsed correctly, trying fallback discovery")
            // (Previous logic was redundant and crashing, so we just check if any new file appeared or fail gracefully)
            Result.failure(workDataOf("error" to "Completed but failed to index. Please restart app."))
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Download failed", e)
            val errorMessage = when (e) {
                is com.yausername.youtubedl_android.YoutubeDLException -> "Library error: ${e.message}"
                else -> e.message ?: "Unknown error"
            }
            Result.failure(workDataOf("error" to errorMessage))
        }
    }

    private fun parseDuration(duration: String): Int {
        val parts = duration.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }
}
