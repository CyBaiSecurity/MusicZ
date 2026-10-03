package com.cybai.musicplayer.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.cybai.musicplayer.ui.MainViewModel

@Composable
fun DownloadScreen(viewModel: MainViewModel) {
    var url by remember { mutableStateOf("") }
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val context = LocalContext.current

    val isConnected = remember { checkConnectivity(context) }
    var connectionError by remember { mutableStateOf(!isConnected) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Download Music",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("YouTube URL") },
            placeholder = { Text("https://www.youtube.com/watch?v=...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (connectionError) {
            ErrorMessage(message = "No internet connection")
        } else {
            Button(
                onClick = {
                    if (checkConnectivity(context)) {
                        viewModel.downloadSong(url)
                    } else {
                        connectionError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download MP3")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedContent(
            targetState = downloadStatus,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "downloadStatus"
        ) { info ->
            if (info != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (info.state) {
                        WorkInfo.State.ENQUEUED -> {
                            Text("Waiting to start...", style = MaterialTheme.typography.bodyMedium)
                        }
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getInt("progress", 0)
                            val animatedProgress by animateFloatAsState(
                                targetValue = progress / 100f,
                                label = "progressAnimation"
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Downloading... $progress%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        WorkInfo.State.SUCCEEDED -> {
                            StatusMessage(
                                message = "Download complete!",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        WorkInfo.State.FAILED -> {
                            val error = info.outputData.getString("error") ?: "Unknown error"
                            ErrorMessage(message = "Download failed: $error")
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun StatusMessage(message: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = message,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium
    )
}

fun checkConnectivity(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
