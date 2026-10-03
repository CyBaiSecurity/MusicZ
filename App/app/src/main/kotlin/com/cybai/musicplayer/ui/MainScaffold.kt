package com.cybai.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.cybai.musicplayer.data.Song
import com.cybai.musicplayer.ui.screens.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val currentSongId by viewModel.musicController.currentSongId.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val currentSong = remember(currentSongId, allSongs) {
        allSongs.find { it.id == currentSongId }
    }

    var showPlayerFull by remember { mutableStateOf(false) }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    var showSongMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (currentSong != null) {
                        MiniPlayer(
                            song = currentSong!!,
                            isPlaying = viewModel.musicController.isPlaying.collectAsState().value,
                            onPlayPause = {
                                if (viewModel.musicController.isPlaying.value) {
                                    viewModel.musicController.pause()
                                } else {
                                    viewModel.musicController.resume()
                                }
                            },
                            onClick = { showPlayerFull = true }
                        )
                    }
                    NavigationBar(
                        tonalElevation = 0.dp // Cleaner look
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        navItems.forEach { screen ->
                            if (screen.route.contains("/")) return@forEach
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(screen.title) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.AllSongs.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable(Screen.Download.route) { DownloadScreen(viewModel) }
                composable(Screen.AllSongs.route) { 
                    AllSongsScreen(
                        viewModel = viewModel,
                        onShowMenu = { song ->
                            selectedSongForMenu = song
                            showSongMenu = true
                        }
                    ) 
                }
                composable(Screen.Playlists.route) { PlaylistsScreen(viewModel, navController) }
                composable(Screen.Stats.route) { StatsScreen(viewModel) }
                composable(
                    route = Screen.PlaylistDetail.route,
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.LongType },
                        navArgument("playlistName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: -1L
                    val playlistName = backStackEntry.arguments?.getString("playlistName") ?: ""
                    PlaylistDetailScreen(
                        viewModel = viewModel,
                        playlistId = playlistId,
                        playlistName = playlistName,
                        onBack = { navController.popBackStack() },
                        onShowMenu = { song ->
                            selectedSongForMenu = song
                            showSongMenu = true
                        }
                    )
                }
            }
        }

        if (showPlayerFull && currentSong != null) {
            BackHandler {
                showPlayerFull = false
            }
            // Instant visibility
            if (showPlayerFull) {
                FullPlayerScreen(
                    song = currentSong,
                    viewModel = viewModel,
                    onDismiss = { showPlayerFull = false },
                    onShowMenu = { song ->
                        selectedSongForMenu = song
                        showSongMenu = true
                    }
                )
            }
        }

        if (showSongMenu && selectedSongForMenu != null) {
            ModalBottomSheet(
                onDismissRequest = { showSongMenu = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = selectedSongForMenu!!.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    HorizontalDivider()

                    ListItem(
                        headlineContent = { Text("Play Next") },
                        leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        modifier = Modifier.clickable {
                            viewModel.playNext(selectedSongForMenu!!)
                            showSongMenu = false
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Add to Playlist") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showPlaylistDialog = true
                            showSongMenu = false
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            viewModel.deleteSong(selectedSongForMenu!!)
                            showSongMenu = false
                        }
                    )
                }
            }
        }

        if (showPlaylistDialog && selectedSongForMenu != null) {
            val playlists by viewModel.playlists.collectAsState()
            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPlaylistDialog = false }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Select Playlist") },
                text = {
                    LazyColumn {
                        items(playlists) { playlist ->
                            TextButton(
                                onClick = {
                                    viewModel.addSongToPlaylist(selectedSongForMenu!!.id, playlist.id)
                                    showPlaylistDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (song.thumbnailUrl != null) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun FullPlayerScreen(
    song: Song,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onShowMenu: (Song) -> Unit
) {
    val isPlaying by viewModel.musicController.isPlaying.collectAsState()
    val shuffleMode by viewModel.musicController.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.musicController.repeatMode.collectAsState()
    val isPlaylist by viewModel.musicController.isPlaylist.collectAsState()
    val currentPosition by viewModel.musicController.currentPosition.collectAsState()
    val totalDuration by viewModel.musicController.duration.collectAsState()
    val queue by viewModel.musicController.queue.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    
    // Optimized slider position calculation
    val sliderPosition = remember(currentPosition, totalDuration) {
        if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f
    }
    
    // Theme colors matching the rest of the app (using colorScheme)
    val backgroundColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = onSurface)
                }
                Text(
                    text = if (showQueue) "UP NEXT" else if (isPlaylist) "PLAYING FROM PLAYLIST" else "PLAYING FROM LIBRARY",
                    style = MaterialTheme.typography.labelMedium,
                    color = onSurface.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onShowMenu(song) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = onSurface)
                }
            }

            if (showQueue) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    itemsIndexed(queue) { index, mediaItem ->
                        val isCurrent = mediaItem.mediaId == song.id.toString()
                        val songInQueue = allSongs.find { it.id.toString() == mediaItem.mediaId }
                        
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                                    color = if (isCurrent) primaryColor else onSurface,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (songInQueue?.thumbnailUrl != null) {
                                        AsyncImage(
                                            model = songInQueue.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = onSurface.copy(alpha = 0.4f))
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.musicController.skipToQueueItem(index)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                // Album Art
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.thumbnailUrl != null) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Song Info & Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Unknown Artist",
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                        Icon(
                            if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color(0xFFFF5252) else onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { viewModel.musicController.seekTo((it * totalDuration).toLong()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = primaryColor.copy(alpha = 0.2f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            style = MaterialTheme.typography.labelMedium,
                            color = onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatDuration(totalDuration),
                            style = MaterialTheme.typography.labelMedium,
                            color = onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.musicController.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(24.dp),
                        tint = if (shuffleMode) primaryColor else onSurface.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = { viewModel.musicController.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = onSurface, modifier = Modifier.size(40.dp))
                }

                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isPlaying) viewModel.musicController.pause() else viewModel.musicController.resume()
                        },
                    color = primaryColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                IconButton(onClick = { viewModel.musicController.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = onSurface, modifier = Modifier.size(40.dp))
                }

                IconButton(onClick = { viewModel.musicController.toggleRepeat() }) {
                    Icon(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) primaryColor else onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Bottom Bar
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SpeakerGroup, contentDescription = null, tint = onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                Row {
                    Icon(Icons.Default.Share, contentDescription = null, tint = onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp).padding(end = 16.dp))
                    IconButton(
                        onClick = { showQueue = !showQueue },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Queue",
                            tint = if (showQueue) primaryColor else onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
