package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.AppTheme
import androidx.compose.material3.*

enum class DragAnchors { Start, End }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkTheme by remember { mutableStateOf(true) }
            AppTheme(darkTheme = darkTheme) {
                val musicViewModel: MusicViewModel = viewModel()
                MainNavigation(
                    musicViewModel = musicViewModel,
                    isDarkTheme = darkTheme,
                    onThemeToggle = { darkTheme = !darkTheme }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainNavigation(
    musicViewModel: MusicViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val uiState by musicViewModel.uiState.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("home") }

    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeight.toPx() }

    // --- MILITARY-GRADE LIQUID DRAG ENGINE ---
    val anchoredDraggableState = remember(screenHeightPx) {
        AnchoredDraggableState(
            initialValue = DragAnchors.End,
            anchors = DraggableAnchors {
                DragAnchors.Start at 0f
                DragAnchors.End at screenHeightPx
            },
            positionalThreshold = { distance: Float -> distance * 0.4f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
            decayAnimationSpec = exponentialDecay()
        )
    }

    LaunchedEffect(showPlayer) {
        if (showPlayer) {
            anchoredDraggableState.animateTo(DragAnchors.Start)
        } else {
            anchoredDraggableState.animateTo(DragAnchors.End)
        }
    }

    LaunchedEffect(anchoredDraggableState.currentValue) {
        if (anchoredDraggableState.currentValue == DragAnchors.End) {
            showPlayer = false
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        
        // --- BASE LAYER: VERTICAL APP FLOW ---
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Scrolling Content Area
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val direction = when {
                            targetState == "equalizer" -> 1
                            targetState == "library" && initialState == "home" -> 1
                            else -> -1
                        }
                        (slideInHorizontally(
                            initialOffsetX = { it * direction },
                            animationSpec = spring(stiffness = 500f, dampingRatio = 0.75f)
                        ) + fadeIn()).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { -it * direction / 2 },
                                animationSpec = spring(stiffness = 500f)
                            ) + fadeOut()
                        )
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        "home" -> HomeScreen(
                            viewModel = musicViewModel,
                            onMiniPlayerClick = { showPlayer = true },
                            onOpenPlayer = { showPlayer = true }
                        )
                        "library" -> LibraryScreen(
                            viewModel = musicViewModel,
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = onThemeToggle,
                            onMiniPlayerClick = { showPlayer = true }
                        )
                        "equalizer" -> EqualizerScreen(
                            viewModel = musicViewModel
                        )
                    }
                }
            }

            // 2. FIXED BOTTOM NAVIGATION AREA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                MusicBottomNavigation(
                    currentTab = currentTab,
                    onTabSelect = { currentTab = it },
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle
                )
            }
        }

        // --- OVERLAY LAYER: FLOATING MINI PLAYER ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp), 
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = uiState.currentTrack != null && !showPlayer,
                enter = slideInVertically(
                    initialOffsetY = { it * 2 },
                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.65f)
                ) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut()
            ) {
                uiState.currentTrack?.let { track ->
                    MiniPlayer(
                        trackTitle = track.title,
                        trackArtist = track.artist,
                        artworkUri = track.customArtworkUri,
                        isPlaying = uiState.isPlaying,
                        onTogglePlay = { musicViewModel.togglePlayPause() },
                        onClick = { showPlayer = true }
                    )
                }
            }
        }

        // --- FULL-SCREEN LIQUID LAYER ---
        val rawOffset = anchoredDraggableState.offset
        val currentOffset = if (rawOffset.isNaN()) screenHeightPx else rawOffset
        
        if (currentOffset < screenHeightPx) {
            val dragAlpha = (1f - (currentOffset / screenHeightPx)).coerceIn(0f, 1f)
            val dragScale = (0.92f + (0.08f * dragAlpha)).coerceIn(0.92f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = dragAlpha
                        scaleX = dragScale
                        scaleY = dragScale
                        translationY = currentOffset
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .anchoredDraggable(
                        state = anchoredDraggableState,
                        orientation = Orientation.Vertical
                    )
            ) {
                PlayerScreen(
                    viewModel = musicViewModel,
                    onBackClick = { showPlayer = false }
                )
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .size(40.dp, 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                )
            }
            BackHandler { showPlayer = false }
        }
    }
}

@Composable
fun MusicBottomNavigation(
    currentTab: String,
    onTabSelect: (String) -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val surfaceColor = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF9F9F9)
    val pillShape = RoundedCornerShape(28.dp)
    
    Surface(
        color = surfaceColor,
        shape = pillShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer {
                // SURGICAL FIX: Shadow must follow shape to avoid sharp corners
                shape = pillShape
                clip = true

                if (isDark) {
                    shadowElevation = 16.dp.toPx()
                    spotShadowColor = Color.White.copy(alpha = 0.25f)
                    ambientShadowColor = Color.White.copy(alpha = 0.1f)
                } else {
                    shadowElevation = 8.dp.toPx()
                }
            }
            .drawBehind {
                val rimColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                drawRoundRect(
                    color = rimColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()),
                    cornerRadius = CornerRadius(28.dp.toPx())
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavPillItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentTab == "home",
                onClick = { onTabSelect("home") }
            )
            NavPillItem(
                icon = Icons.Default.LibraryMusic,
                label = "Library",
                isSelected = currentTab == "library",
                onClick = { onTabSelect("library") }
            )
            NavPillItem(
                icon = Icons.Default.Tune,
                label = "EQ",
                isSelected = currentTab == "equalizer",
                onClick = { onTabSelect("equalizer") }
            )
            NavPillItem(
                icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                label = "Theme",
                isSelected = false,
                onClick = onThemeToggle
            )
        }
    }
}

@Composable
fun NavPillItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.6f),
        label = "NavScale"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "NavColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
        AnimatedVisibility(visible = isSelected) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
