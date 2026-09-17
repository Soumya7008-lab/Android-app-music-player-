package com.example.myapplication

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.geometry.Rect
import kotlin.math.*

private const val TWO_PI = 6.2831853f

@Composable
fun ThreeDPlayButton(
    onClick: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val squishX by animateFloatAsState(
        targetValue = if (isPressed) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "SquishX"
    )
    val squishY by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "SquishY"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "IconScale"
    )

    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = squishX
                scaleY = squishY
            }
            .drawBehind {
                // Ground Shadow
                if (isDark) {
                    val glowAlpha = if (isPressed) 0.04f else 0.08f
                    // Deep metallic ground shadow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                            center = center + Offset(0f, 10.dp.toPx()),
                            radius = size.maxDimension * 0.8f,
                        ),
                        radius = size.maxDimension * 0.8f,
                        center = center + Offset(0f, 10.dp.toPx())
                    )
                    // Theme glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(themeColor.copy(alpha = glowAlpha), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.65f,
                        ),
                        radius = size.maxDimension * 0.65f,
                        center = center
                    )
                } else {
                    // Elevated shadow for Light Mode
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent),
                            center = center + Offset(0f, 8.dp.toPx()),
                            radius = size.maxDimension * 0.75f,
                        ),
                        radius = size.maxDimension * 0.75f,
                        center = center + Offset(0f, 8.dp.toPx())
                    )
                }

                // Main 3D Sphere Body - Metallic Refinement
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            // Deeper Metallic Palette
                            listOf(Color(0xFF2A2A2A), Color(0xFF080808), Color(0xFF000000))
                        } else {
                            listOf(Color.White, Color(0xFFE0E0E0))
                        },
                        center = center - Offset(size.width * 0.15f, size.height * 0.15f),
                        radius = size.maxDimension * 0.8f
                    ),
                    radius = size.maxDimension / 2,
                    center = center
                )

                // Rim Light (Bottom Right)
                if (isDark) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF444444).copy(alpha = 0.15f), Color.Transparent),
                            center = center + Offset(size.width * 0.3f, size.height * 0.3f),
                            radius = size.maxDimension * 0.4f
                        ),
                        radius = size.maxDimension / 2,
                        center = center
                    )
                }

                // Inner Glow / Lighting
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF222222).copy(alpha = 0.12f), Color.Transparent)
                        } else {
                            listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)
                        },
                        center = center - Offset(size.width * 0.2f, size.height * 0.2f),
                        radius = size.maxDimension * 0.6f
                    ),
                    radius = size.maxDimension / 2,
                    center = center
                )
                
                // Sharp Specular Highlight - Muted for Dark Metal
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF666666).copy(alpha = 0.2f), Color.Transparent)
                        } else {
                            listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)
                        },
                        center = center - Offset(size.width * 0.25f, size.height * 0.25f),
                        radius = size.maxDimension * 0.12f
                    ),
                    radius = size.maxDimension * 0.12f,
                    center = center - Offset(size.width * 0.25f, size.height * 0.25f)
                )

                val rimAlpha = if (isDark) 0.25f else 0.15f
                val rimColor = if (isDark) Color(0xFF333333).copy(alpha = rimAlpha) else Color.Black.copy(alpha = rimAlpha)
                drawCircle(
                    color = rimColor,
                    radius = (size.maxDimension / 2) - 0.5.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = themeColor,
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}

@Composable
fun ModernFluidBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    var isTouching by remember { mutableStateOf(value = false) }

    val interactionScale by animateFloatAsState(
        targetValue = if (isTouching) 1.6f else 1f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.7f),
        label = "Interaction"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        if (change.pressed) {
                            isTouching = true
                            onSeekStarted()
                            val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSeek(newProgress)
                            // CRITICAL: Consume the event to prevent parent Pager or ModalSheet from swiping/minimizing
                            change.consume()
                        }
                        if (event.changes.all { !it.pressed }) {
                            isTouching = false
                            onSeekFinished()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            val width = size.width
            val height = size.height

            val glowAlpha = if (isDark) 0.05f else 0.03f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(themeColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = width / 2.2f
                ),
                radius = width / 2.2f,
                center = center
            )

            drawRoundRect(
                color = themeColor.copy(alpha = if (isDark) 0.08f else 0.04f),
                size = Size(width, 4.dp.toPx()),
                topLeft = Offset(0f, (height / 2) - 2.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(themeColor.copy(alpha = 0.35f), themeColor)
                ),
                size = Size(width * progress, 4.dp.toPx()),
                topLeft = Offset(0f, (height / 2) - 2.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            val indicatorX = width * progress

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(themeColor.copy(alpha = if (isDark) 0.15f else 0.1f), Color.Transparent),
                    center = Offset(indicatorX, height / 2),
                    radius = 12.dp.toPx() * interactionScale
                ),
                radius = 12.dp.toPx() * interactionScale,
                center = Offset(indicatorX, height / 2)
            )

            if (isTouching) {
                drawCircle(
                    color = themeColor,
                    radius = 5.dp.toPx() * interactionScale,
                    center = Offset(indicatorX, height / 2),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            } else {
                drawRoundRect(
                    color = themeColor,
                    topLeft = Offset(indicatorX - 1.25.dp.toPx(), (height / 2) - 10.dp.toPx()),
                    size = Size(2.5.dp.toPx(), 20.dp.toPx()),
                    cornerRadius = CornerRadius(1.25.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    trackTitle: String,
    trackArtist: String,
    artworkUri: String?,
    isPlaying: Boolean,
    progress: Float,
    visualizerData: List<Float>, // NEW: Visualizer data
    onTogglePlay: () -> Unit,
    onForward: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    // BREATHTAKING MESH SURFACE
    val surfaceBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF222222), Color(0xFF080808))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF2F2F2))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(76.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = if (isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.55f), // SLIGHTLY REDUCED BLACK SHADOW
                    ambientColor = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f) // SLIGHTLY REDUCED AMBIENT
                )
                .graphicsLayer {
                    shape = RoundedCornerShape(28.dp)
                    clip = true
                }
                .drawBehind {
                    // 3. SILKY GRAIN TEXTURE
                    val grainAlpha = if (isDark) 0.12f else 0.06f
                    val grainColor = if (isDark) Color.White else Color.Black
                    val step = 3.dp.toPx()
                    for (x in 0..size.width.toInt() step step.toInt()) {
                        for (y in 0..size.height.toInt() step step.toInt()) {
                            if ((x * 19 + y * 23) % 9 == 0) {
                                drawCircle(
                                    color = grainColor.copy(alpha = grainAlpha),
                                    radius = 0.6.dp.toPx(),
                                    center = Offset(x.toFloat(), y.toFloat())
                                )
                            }
                        }
                    }

                    // 4. COMPLEX METALLIC RIM (Diamond Cut)
                    val outerRimColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.15f)
                    val innerRimColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.6f)
                    
                    // Outer bright edge
                    drawRoundRect(
                        color = outerRimColor,
                        style = Stroke(width = 1.5.dp.toPx()),
                        cornerRadius = CornerRadius(28.dp.toPx())
                    )
                    // Inner soft highlight
                    drawRoundRect(
                        color = innerRimColor,
                        topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                        size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                        style = Stroke(width = 0.8.dp.toPx()),
                        cornerRadius = CornerRadius(26.5.dp.toPx())
                    )
                }
                .background(surfaceBrush)
                .clickable(onClick = onClick),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // SMART PROGRESS BAR (Top-aligned thin line)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.5.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(themeColor.copy(alpha = 0.6f), Color.White)
                            )
                        )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    // ELEVATED ARTWORK
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .graphicsLayer {
                                shadowElevation = 8.dp.toPx()
                                shape = RoundedCornerShape(14.dp)
                                clip = true
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (artworkUri != null) {
                            AsyncImage(
                                model = artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = themeColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp).align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trackTitle,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = trackArtist.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isPlaying) {
                                Spacer(modifier = Modifier.width(20.dp))
                                MiniLEDVisualizer(
                                    data = visualizerData,
                                    color = themeColor,
                                    modifier = Modifier.width(56.dp).height(12.dp) // INCREASED WIDTH
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // CUSTOM MINIMALIST CONTROLS
                        Surface(
                            onClick = onTogglePlay,
                            shape = CircleShape,
                            color = themeColor.copy(alpha = 0.1f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    tint = themeColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // SMART FORWARD BUTTON
                        IconButton(
                            onClick = onForward,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Forward",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniLEDVisualizer(
    data: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 12 // INCREASED FROM 8
    val displayData = if (data.size >= barCount) data.take(barCount) else List(barCount) { 0.1f }

    Canvas(modifier = modifier) {
        val spacing = 2.dp.toPx()
        val barWidth = (size.width - (spacing * (barCount - 1))) / barCount
        val segmentCount = 4
        val segmentHeight = size.height / segmentCount
        val segmentSpacing = 1.dp.toPx()

        displayData.forEachIndexed { index, value ->
            val x = index * (barWidth + spacing)
            val activeSegments = (value * segmentCount).roundToInt().coerceIn(1, segmentCount)

            for (i in 0 until segmentCount) {
                val y = size.height - (i + 1) * segmentHeight
                val isActive = i < activeSegments
                
                drawRoundRect(
                    color = if (isActive) color else color.copy(alpha = 0.1f),
                    topLeft = Offset(x, y + segmentSpacing / 2),
                    size = Size(barWidth, segmentHeight - segmentSpacing),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun TrackListItem(
    number: Int,
    title: String,
    artist: String,
    duration: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    
    val activeBackgroundColor = if (isDark) {
        themeColor.copy(alpha = 0.15f)
    } else {
        themeColor.copy(alpha = 0.08f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isPlaying) activeBackgroundColor else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(
                    if (isPlaying) {
                        if (isDark) themeColor else Color.Black
                    } else Color.Transparent
                )
        )

        Spacer(modifier = Modifier.width(20.dp))

        Text(
            text = number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPlaying) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(32.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.Normal,
                color = if (isPlaying) themeColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) themeColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        if (trailingContent != null) {
            trailingContent()
        } else {
            Text(
                text = duration,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 24.dp)
            )
        }
    }
}

@Composable
fun LibraryBottomNavigation(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    currentTab: String,
    onTabSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val themeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
        ) {
            val width = size.width
            val height = size.height
            val cutoutRadius = 24.dp.toPx()

            val path = Path().apply {
                moveTo(0f, cutoutRadius)
                arcTo(
                    rect = Rect(-cutoutRadius, 0f, cutoutRadius, cutoutRadius * 2f),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(width - cutoutRadius, 0f)
                arcTo(
                    rect = Rect(width - cutoutRadius, 0f, width + cutoutRadius, cutoutRadius * 2f),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path = path, color = backgroundColor.copy(alpha = 0.6f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .height(64.dp)
                .graphicsLayer {
                    shadowElevation = 16.dp.toPx()
                    shape = RoundedCornerShape(32.dp)
                    clip = true
                    spotShadowColor = if (isDarkTheme) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.35f)
                    ambientShadowColor = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)
                }
                .background(backgroundColor)
                .drawBehind {
                    val rimColor = if (isDarkTheme) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.1f)
                    drawRoundRect(
                        color = rimColor,
                        style = Stroke(width = 1.5.dp.toPx()),
                        cornerRadius = CornerRadius(32.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onTabSelect("home") }) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = if (currentTab == "home") themeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onTabSelect("library") }) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = "Library",
                        tint = if (currentTab == "library") themeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onTabSelect("equalizer") }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Equalizer",
                        tint = if (currentTab == "equalizer") themeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onThemeToggle) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicEqualizer(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    val themeColor = MaterialTheme.colorScheme.primary
    
    // HIGH-PERFORMANCE ANIMATION: Use a single transition instead of 20 separate ones
    val infiniteTransition = rememberInfiniteTransition(label = "WaveProp")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveOffset"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = data.size
            val spacing = 6.dp.toPx()
            val totalSpacing = spacing * (barCount - 1)
            val barWidth = (size.width - totalSpacing) / barCount
            
            data.forEachIndexed { index, value ->
                val x = index * (barWidth + spacing)
                
                // Add a subtle wave-like movement even when idle for a premium feel
                val dynamicImpact = (value * 1.1f + sin(waveOffset + index * 0.5f) * 0.05f).coerceIn(0.1f, 1f)
                val barHeight = size.height * dynamicImpact
                val y = size.height - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(themeColor.copy(alpha = 0.35f), Color.Transparent)
                    ),
                    topLeft = Offset(x, y - 8.dp.toPx()),
                    size = Size(barWidth, barHeight + 8.dp.toPx()),
                    cornerRadius = CornerRadius(barWidth / 2)
                )

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(themeColor, themeColor.copy(alpha = 0.6f))
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2)
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(x + 1.2.dp.toPx(), y + 1.2.dp.toPx()),
                    size = Size(barWidth - 2.4.dp.toPx(), 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.2.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun MasterVolumeBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = MaterialTheme.colorScheme.primary
    var isTouching by remember { mutableStateOf(false) }

    val interactionScale by animateFloatAsState(
        targetValue = if (isTouching) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "VolScale"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                imageVector = when {
                    value > 0.6f -> Icons.Default.VolumeUp
                    value > 0f -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeOff
                },
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "MASTER VOLUME",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = themeColor
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            if (change.pressed) {
                                isTouching = true
                                val newValue = (change.position.x / size.width).coerceIn(0f, 1f)
                                onValueChange(newValue)
                                // CRITICAL: Consume the event to prevent parent Pager from swiping
                                change.consume()
                            }
                            if (event.changes.all { !it.pressed }) {
                                isTouching = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val constraintsWidth = maxWidth
            
            // Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(themeColor.copy(alpha = 0.15f))
            )

            // Progress
            Box(
                modifier = Modifier
                    .fillMaxWidth(value)
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(themeColor.copy(alpha = 0.7f), themeColor)
                        )
                    )
            )

            // Thumb
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (constraintsWidth - 20.dp) * value)
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = interactionScale
                        scaleY = interactionScale
                        shadowElevation = if (isTouching) 12.dp.toPx() else 4.dp.toPx()
                        shape = CircleShape
                        clip = true
                    }
                    .background(themeColor)
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = (size.maxDimension / 2) - 0.5.dp.toPx(),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
            )
        }
    }
}

@Composable
fun EqualizerSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = MaterialTheme.colorScheme.primary
    var isDragging by remember { mutableStateOf(false) }
    val sliderHeight = 145.dp

    val interactionScale by animateFloatAsState(
        targetValue = if (isDragging) 1.25f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "SliderScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(50.dp)
    ) {
        Box(
            modifier = Modifier
                .height(sliderHeight + 8.dp) // Minimal vertical padding
                .width(50.dp) // Wide touch area
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            if (change.pressed) {
                                isDragging = true
                                // Accounting for 4dp vertical padding on each side
                                val relativeY = (change.position.y - 4.dp.toPx()).coerceIn(0f, sliderHeight.toPx())
                                val newValue = 1f - (relativeY / sliderHeight.toPx())
                                onValueChange(newValue)
                                // CRITICAL: Consume the event to prevent parent Pager from swiping
                                change.consume()
                            }
                            if (event.changes.all { !it.pressed }) {
                                isDragging = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Track
            Box(
                modifier = Modifier
                    .height(sliderHeight)
                    .width(3.dp)
                    .clip(CircleShape)
                    .background(themeColor.copy(alpha = 0.15f))
            )

            // Thumb Container
            Box(
                modifier = Modifier.height(sliderHeight).width(28.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                 // Thumb
                 Box(
                    modifier = Modifier
                        .offset(y = (-(sliderHeight - 22.dp) * value))
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = interactionScale
                            scaleY = interactionScale
                            shadowElevation = if (isDragging) 16.dp.toPx() else 4.dp.toPx()
                            shape = CircleShape
                            clip = true
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isDragging) listOf(themeColor, themeColor.copy(alpha = 0.8f))
                                else listOf(themeColor.copy(alpha = 0.95f), themeColor)
                            )
                        )
                        .drawBehind {
                            if (isDragging) {
                                drawCircle(
                                    color = themeColor.copy(alpha = 0.25f),
                                    radius = size.maxDimension * 1.6f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            drawCircle(
                                color = Color.White.copy(alpha = 0.3f),
                                radius = (size.maxDimension / 2) - 0.5.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp)) // Shrunk from 4dp
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun SleekSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search..."
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val surfaceColor = if (isDark) Color(0xFF0F0F0F) else Color(0xFFF7F7F7)
    val themeColor = MaterialTheme.colorScheme.primary

    Surface(
        color = surfaceColor,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                shape = RoundedCornerShape(18.dp)
                clip = true
                if (isDark) {
                    shadowElevation = 4.dp.toPx()
                    spotShadowColor = Color.White.copy(alpha = 0.08f)
                } else {
                    shadowElevation = 6.dp.toPx()
                    spotShadowColor = Color.Black.copy(alpha = 0.05f)
                }
            }
            .drawBehind {
                // SOFT RADIANT TINT
                val tintAlpha = if (isDark) 0.1f else 0.05f
                drawRoundRect(
                    color = themeColor.copy(alpha = tintAlpha),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )

                val rimAlpha = if (isDark) 0.12f else 0.06f
                val rimColor = if (isDark) Color.White else Color.Black
                drawRoundRect(
                    color = rimColor.copy(alpha = rimAlpha),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
            }
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = themeColor.copy(alpha = 0.7f)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = themeColor
            ),
            singleLine = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}
