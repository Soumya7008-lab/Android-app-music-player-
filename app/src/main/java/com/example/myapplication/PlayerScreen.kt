package com.example.myapplication

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MusicViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(themeColor.copy(alpha = if (isDark) 0.12f else 0.04f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.8f
                        ),
                        radius = size.maxDimension * 0.8f,
                        center = center
                    )
                }
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("NOW PLAYING", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = themeColor)
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = themeColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier.height(300.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .drawBehind {
                                val auraAlpha = if (isDark) 0.35f else 0.12f
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(themeColor.copy(alpha = auraAlpha), Color.Transparent),
                                        center = center,
                                        radius = size.maxDimension / 2 + 50.dp.toPx()
                                    ),
                                    radius = size.maxDimension / 2 + 50.dp.toPx(),
                                    center = center
                                )
                            }
                    )

                    AnimatedContent(
                        targetState = currentTrack,
                        transitionSpec = {
                            if (uiState.skipDirection >= 0) {
                                (slideInHorizontally(animationSpec = tween(500, easing = EaseOutQuart)) { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally(animationSpec = tween(500, easing = EaseOutQuart)) { width -> -width } + fadeOut())
                            } else {
                                (slideInHorizontally(animationSpec = tween(500, easing = EaseOutQuart)) { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally(animationSpec = tween(500, easing = EaseOutQuart)) { width -> width } + fadeOut())
                            }
                        },
                        label = "PosterTransition"
                    ) { track ->
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(280.dp)
                                    .graphicsLayer {
                                        rotationY = 3f
                                        rotationX = 2f
                                        cameraDistance = 12f * density
                                    }
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = if (isDark) listOf(Color(0xFF1E1E1E), Color(0xFF0D0D0D))
                                            else listOf(Color(0xFFFFFFFF), Color(0xFFF8F9FA))
                                        )
                                    )
                                    .drawBehind {
                                        val rimColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f)
                                        drawRect(
                                            color = rimColor,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                        )
                                        drawRect(
                                            color = themeColor.copy(alpha = 0.05f),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                        )
                                    }
                            ) {
                                if (track?.customArtworkUri != null) {
                                    AsyncImage(
                                        model = track.customArtworkUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        // PERFECTED: FULLY OCCUPY POSTER FRAME
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(120.dp).align(Alignment.Center),
                                        tint = themeColor.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentTrack?.title ?: "Select a track",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = currentTrack?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ModernFluidBar(
                        progress = uiState.progress,
                        onSeek = { viewModel.seekTo(it) },
                        onSeekStarted = { viewModel.onSeekStarted() },
                        onSeekFinished = { viewModel.onSeekFinished() }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(uiState.currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(uiState.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (uiState.shuffleModeEnabled) themeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.skipPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, null, tint = themeColor, modifier = Modifier.size(44.dp))
                    }

                    ThreeDPlayButton(
                        onClick = { viewModel.togglePlayPause() },
                        isPlaying = uiState.isPlaying,
                        modifier = Modifier.size(86.dp)
                    )

                    IconButton(onClick = { viewModel.skipNext() }) {
                        Icon(Icons.Default.SkipNext, null, tint = themeColor, modifier = Modifier.size(44.dp))
                    }

                    IconButton(onClick = { viewModel.toggleRepeat() }) {
                        Icon(
                            if (uiState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) themeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = themeColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SurroundSound, null, modifier = Modifier.size(18.dp), tint = themeColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("3D SPATIAL AUDIO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = themeColor)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
