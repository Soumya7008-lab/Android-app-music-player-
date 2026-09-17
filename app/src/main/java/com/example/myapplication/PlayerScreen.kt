package com.example.myapplication

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
                    val auraColor = if (isDark) Color(0xFF050505) else themeColor.copy(alpha = 0.02f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(auraColor, Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.5f
                        ),
                        radius = size.maxDimension * 0.5f,
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
                                val auraColor = if (isDark) Color(0xFF121212) else themeColor.copy(alpha = 0.08f)
                                val glowRadius = if (isDark) size.maxDimension / 2 + 10.dp.toPx() else size.maxDimension / 2 + 30.dp.toPx()
                                
                                // Soft shadow for elevation
                                val shadowAlpha = if (isDark) 0.5f else 0.25f
                                val shadowOffset = if (isDark) 10.dp.toPx() else 18.dp.toPx()
                                val shadowRadius = if (isDark) size.maxDimension / 2 else size.maxDimension * 0.65f
                                
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent),
                                        center = center + Offset(0f, shadowOffset),
                                        radius = shadowRadius
                                    ),
                                    radius = shadowRadius,
                                    center = center + Offset(0f, shadowOffset)
                                )

                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(auraColor, Color.Transparent),
                                        center = center,
                                        radius = glowRadius
                                    ),
                                    radius = glowRadius,
                                    center = center
                                )
                            }
                    )

                    AnimatedContent(
                        targetState = currentTrack,
                        transitionSpec = {
                            // Cinematic Pace: Smooth and deliberate
                            val springSpec = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 120f)
                            val scaleSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 80f)
                            val fadeDuration = 700
                            
                            (slideInHorizontally(animationSpec = springSpec) { width -> if (uiState.skipDirection >= 0) width / 2 else -width / 2 } + 
                             fadeIn(animationSpec = tween(fadeDuration)) + 
                             scaleIn(initialScale = 0.92f, animationSpec = scaleSpring)).togetherWith(
                                slideOutHorizontally(animationSpec = springSpec) { width -> if (uiState.skipDirection >= 0) -width / 2 else width / 2 } + 
                                fadeOut(animationSpec = tween(fadeDuration)) + 
                                scaleOut(targetScale = 0.92f, animationSpec = scaleSpring)
                            )
                        },
                        label = "PosterTransition"
                    ) { track ->
                        var totalDragX by remember { mutableStateOf(0f) }
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDragX = 0f },
                                    onDragEnd = {
                                        if (totalDragX < -50.dp.toPx()) {
                                            viewModel.skipNext()
                                        } else if (totalDragX > 50.dp.toPx()) {
                                            viewModel.skipPrevious()
                                        }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        totalDragX += dragAmount
                                    }
                                )
                            }
                        ) {
                            // THE POSTER FRAME
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
                                            colors = if (isDark) listOf(Color(0xFF121212), Color(0xFF050505))
                                            else listOf(Color(0xFFFFFFFF), Color(0xFFF8F9FA))
                                        )
                                    )
                                    .drawBehind {
                                        val rimColor = if (isDark) Color(0xFF222222) else Color.Black.copy(alpha = 0.05f)
                                        drawRect(
                                            color = rimColor,
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                        drawRect(
                                            color = themeColor.copy(alpha = if (isDark) 0.02f else 0.05f),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                            ) {
                                if (track?.customArtworkUri != null) {
                                    AsyncImage(
                                        model = track.customArtworkUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
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

                            // HOVERING SPATIAL AUDIO TOGGLE - SITS INSIDE THE POSTER
                            val is16D = uiState.is16DEnabled
                            Surface(
                                onClick = { viewModel.toggle16D() },
                                shape = CircleShape,
                                color = if (isDark) Color(0xFF1A1A1A) else Color.White,
                                shadowElevation = if (is16D) 8.dp else 4.dp,
                                modifier = Modifier
                                    .size(44.dp)
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 12.dp, end = 12.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawBehind {
                                            // Soft activation grey shadow for both modes
                                            if (is16D) {
                                                drawCircle(
                                                    brush = Brush.radialGradient(
                                                        colors = listOf(
                                                            (if (isDark) Color(0xFF444444) else Color(0xFFDDDDDD)).copy(alpha = 0.4f), 
                                                            Color.Transparent
                                                        ),
                                                        center = center,
                                                        radius = size.maxDimension * 0.9f
                                                    ),
                                                    radius = size.maxDimension * 0.9f,
                                                    center = center
                                                )
                                            }
                                            val rimColor = if (isDark) Color(0xFF333333) else Color.Black.copy(alpha = 0.08f)
                                            drawCircle(
                                                color = rimColor,
                                                style = Stroke(width = 1.dp.toPx())
                                            )
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SurroundSound,
                                        contentDescription = "Spatial Audio",
                                        modifier = Modifier.size(22.dp),
                                        tint = if (is16D) themeColor else (if (isDark) Color.DarkGray else Color.Gray)
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

                // BRANDING LOGO SECTION - HIGH RES STYLE
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Branding Logo",
                        modifier = Modifier.size(24.dp).graphicsLayer { alpha = 0.6f },
                        tint = if (isDark) Color.Gray else Color.Black
                    )
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
