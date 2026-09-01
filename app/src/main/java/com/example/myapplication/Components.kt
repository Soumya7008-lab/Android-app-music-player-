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

@Composable
fun ThreeDPlayButton(
    onClick: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "ButtonScale",
    )

    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val surfaceColor = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (isDark) {
                    val glowAlpha = if (isPressed) 0.15f else 0.45f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(themeColor.copy(alpha = glowAlpha), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.9f,
                        ),
                        radius = size.maxDimension * 0.9f,
                        center = center
                    )
                } else {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent),
                            center = center + Offset(0f, 4.dp.toPx()),
                            radius = size.maxDimension * 0.7f
                        ),
                        radius = size.maxDimension * 0.7f,
                        center = center + Offset(0f, 4.dp.toPx())
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.1f), Color.Transparent),
                            center = center + Offset(0f, 8.dp.toPx()),
                            radius = size.maxDimension * 0.9f
                        ),
                        radius = size.maxDimension * 0.9f,
                        center = center + Offset(0f, 8.dp.toPx())
                    )
                }

                val rimAlpha = if (isDark) 0.3f else 0.15f
                val rimColor = if (isDark) Color.White.copy(alpha = rimAlpha) else Color.Black.copy(alpha = rimAlpha)
                drawCircle(
                    color = rimColor,
                    radius = (size.maxDimension / 2) - 0.5.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isPressed) {
                        listOf(surfaceColor.copy(alpha = 0.85f), surfaceColor)
                    } else {
                        listOf(surfaceColor, surfaceColor.copy(alpha = 0.75f))
                    }
                )
            )
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
            modifier = Modifier.size(44.dp)
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

            val glowAlpha = if (isDark) 0.12f else 0.05f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(themeColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = width / 1.6f
                ),
                radius = width / 1.6f,
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
                    colors = listOf(themeColor.copy(alpha = if (isDark) 0.35f else 0.15f), Color.Transparent),
                    center = Offset(indicatorX, height / 2),
                    radius = 16.dp.toPx() * interactionScale
                ),
                radius = 16.dp.toPx() * interactionScale,
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
    onTogglePlay: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    Surface(
        color = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF9F9F9),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(72.dp)
            .graphicsLayer {
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape(24.dp)
                clip = true
                if (isDark) {
                    spotShadowColor = Color.White.copy(alpha = 0.25f)
                    ambientShadowColor = Color.White.copy(alpha = 0.15f)
                }
            }
            .drawBehind {
                val rimColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                drawRoundRect(
                    color = rimColor,
                    style = Stroke(width = 1.2.dp.toPx()),
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
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
                        tint = themeColor.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = trackArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                    tint = themeColor
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
    
    val animatedData = data.map { 
        animateFloatAsState(
            targetValue = it,
            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
            label = "BarHeight"
        ).value
    }

    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = animatedData.size
            val spacing = 6.dp.toPx()
            val totalSpacing = spacing * (barCount - 1)
            val barWidth = (size.width - totalSpacing) / barCount
            
            animatedData.forEachIndexed { index, value ->
                val x = index * (barWidth + spacing)
                // SCALE: make bars more visible even at low values
                val scaledValue = (value * 1.2f).coerceIn(0.1f, 1f)
                val barHeight = size.height * scaledValue
                val y = size.height - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(themeColor.copy(alpha = 0.35f), Color.Transparent)
                    ),
                    topLeft = Offset(x, y - 10.dp.toPx()),
                    size = Size(barWidth, barHeight + 10.dp.toPx()),
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
                    color = Color.White.copy(alpha = 0.4f),
                    topLeft = Offset(x + 1.5.dp.toPx(), y + 1.5.dp.toPx()),
                    size = Size(barWidth - 3.dp.toPx(), 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
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
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = when {
                    value > 0.6f -> Icons.Default.VolumeUp
                    value > 0f -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeOff
                },
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "MASTER VOLUME",
                style = MaterialTheme.typography.labelMedium,
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
                .height(48.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            if (change.pressed) {
                                isTouching = true
                                val newValue = (change.position.x / size.width).coerceIn(0f, 1f)
                                onValueChange(newValue)
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
                    .offset(x = (constraintsWidth - 24.dp) * value)
                    .size(24.dp)
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
    val sliderHeight = 160.dp

    val interactionScale by animateFloatAsState(
        targetValue = if (isDragging) 1.25f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "SliderScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(54.dp)
    ) {
        Box(
            modifier = Modifier
                .height(sliderHeight + 24.dp) // Added vertical padding
                .width(54.dp) // Wide touch area
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            if (change.pressed) {
                                isDragging = true
                                // Accounting for 12dp vertical padding on each side
                                val relativeY = (change.position.y - 12.dp.toPx()).coerceIn(0f, sliderHeight.toPx())
                                val newValue = 1f - (relativeY / sliderHeight.toPx())
                                onValueChange(newValue)
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
                    .width(4.dp)
                    .clip(CircleShape)
                    .background(themeColor.copy(alpha = 0.15f))
            )

            // Thumb Container
            Box(
                modifier = Modifier.height(sliderHeight).width(32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                 // Thumb
                 Box(
                    modifier = Modifier
                        .offset(y = (-sliderHeight * value))
                        .size(26.dp)
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
        
        Spacer(modifier = Modifier.height(10.dp))
        
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
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                if (isDark) {
                    shadowElevation = 8.dp.toPx()
                    spotShadowColor = Color.White.copy(alpha = 0.15f)
                    ambientShadowColor = Color.White.copy(alpha = 0.1f)
                }
            }
            .drawBehind {
                if (isDark) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.15f),
                        style = Stroke(width = 1.dp.toPx()),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                }
            }
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
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
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}
