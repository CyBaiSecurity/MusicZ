package com.cybai.musicplayer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Download : Screen("download", "Download", Icons.Default.Download)
    object AllSongs : Screen("songs", "Songs", Icons.Default.MusicNote)
    object Playlists : Screen("playlists", "Playlists", Icons.Default.PlaylistPlay)
    object Stats : Screen("stats", "Stats", Icons.Default.BarChart)
    object PlaylistDetail : Screen("playlist_detail/{playlistId}/{playlistName}", "Playlist Detail", Icons.AutoMirrored.Filled.List)
}

val navItems = listOf(
    Screen.Download,
    Screen.AllSongs,
    Screen.Playlists,
    Screen.Stats
)
