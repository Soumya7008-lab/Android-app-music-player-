package com.example.myapplication

import android.app.SearchManager
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.AppTheme
import androidx.compose.material3.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

enum class DragAnchors { Start, End }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val isDark = ThemeManager.isDarkTheme(this)
        // Manually apply the Splash Theme based on the persisted setting
        // This ensures the window background image is used during initial render
        setTheme(if (isDark) R.style.Theme_MyApplication_Splash_Dark else R.style.Theme_MyApplication_Splash_Light)
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        handleIntent(intent)
        
        setContent {
            var darkTheme by remember { mutableStateOf(isDark) }
            AppTheme(darkTheme = darkTheme) {
                val musicViewModel: MusicViewModel = viewModel()
                
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            musicViewModel.setUiVisible(true)
                        } else if (event == Lifecycle.Event.ON_STOP) {
                            musicViewModel.setUiVisible(false)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                MainNavigation(
                    musicViewModel = musicViewModel,
                    isDarkTheme = darkTheme,
                    onThemeToggle = {
                        val newTheme = !darkTheme
                        darkTheme = newTheme
                        ThemeManager.saveThemePreference(this, newTheme)
                    }
                )
            }
        }
    }

    override fun onStop() {
        // Apply the icon change only when the user leaves the app
        ThemeManager.applyIconChange(this)
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val viewModel = androidx.lifecycle.ViewModelProvider(this)[MusicViewModel::class.java]
                viewModel.playAudioFromUri(uri)
            }
        } else if (intent.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            // Can be handled here or in the service
        } else if (intent.action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query = intent.getStringExtra(SearchManager.QUERY) ?: ""
            // The Media3 session service also handles this when running,
            // but waking the app through the Activity ensures the UI is ready
            // and the ViewModel can connect to the MediaController!
            val viewModel = androidx.lifecycle.ViewModelProvider(this)[MusicViewModel::class.java]
            viewModel.playFromSearch(query)
        }
    }
}

@Composable
fun MainNavigation(
    musicViewModel: MusicViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val uiState by musicViewModel.uiState.collectAsState()

    if (uiState.isOnboardingRequired) {
        OnboardingScreen(
            onComplete = { name ->
                musicViewModel.updateUserName(name)
                musicViewModel.completeOnboarding()
            }
        )
    } else {
        MainNavigationContent(
            musicViewModel = musicViewModel,
            isDarkTheme = isDarkTheme,
            onThemeToggle = onThemeToggle
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainNavigationContent(
    musicViewModel: MusicViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val uiState by musicViewModel.uiState.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    val tabs = listOf("home", "library", "equalizer")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // --- SMART BACK-NAVIGATION HISTORY ---
    val navigationHistory = remember { mutableStateListOf(0) } // Start with Home

    // Track tab changes to build history
    LaunchedEffect(pagerState.currentPage) {
        // Only add if it's a different tab than the last one in history
        if (navigationHistory.lastOrNull() != pagerState.currentPage) {
            navigationHistory.add(pagerState.currentPage)
            // Limit history size to 6 to prevent "too much history"
            if (navigationHistory.size > 6) {
                navigationHistory.removeAt(0)
            }
        }
    }

    // Handle back button for tab history
    // FIXED: Only enabled when NOT on Home. If on Home, it will close the app.
    BackHandler(enabled = (showSettings || showPlayer || pagerState.currentPage != 0)) {
        if (showSettings) {
            showSettings = false
        } else if (showPlayer) {
            showPlayer = false
        } else if (navigationHistory.size > 1) {
            // Remove the current tab from history
            navigationHistory.removeAt(navigationHistory.size - 1)
            // Get the previous tab and remove it (so the LaunchedEffect can re-add it correctly)
            val previousPage = navigationHistory.removeAt(navigationHistory.size - 1)
            coroutineScope.launch {
                pagerState.animateScrollToPage(previousPage)
            }
        } else {
            // Fallback: Go back to Home
            coroutineScope.launch {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    // Sync currentTab with pagerState (optional, but good for keeping logic clean)
    val currentTab = tabs[pagerState.currentPage]

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

    // --- FULL-SCREEN LIQUID LAYER CALCS ---
    val rawOffset = anchoredDraggableState.offset
    val currentOffset = if (rawOffset.isNaN()) screenHeightPx else rawOffset

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
        
        // --- BASE LAYER: OVERLAPPING APP FLOW (Truly Floating Nav) ---
    // --- PURE LIQUID BOUNCE ENGINE ---
    val bounceAnimatable = remember { Animatable(0f) }
    var lastPage by remember { mutableIntStateOf(pagerState.currentPage) }
    
    // Watch for page changes to trigger a directional side-bounce
    LaunchedEffect(pagerState.currentPage) {
        val direction = if (pagerState.currentPage > lastPage) 1f else -1f
        lastPage = pagerState.currentPage
        
        // Side-bounce "Kick": Snaps to an offset and springs back
        // This works for both gestures and nav taps
        bounceAnimatable.snapTo(direction * 0.25f) 
        bounceAnimatable.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                stiffness = 300f, // Slower, more luxurious bounce
                dampingRatio = 0.5f // Noticeable overshoot
            )
        )
    }

    // --- BASE LAYER: OVERLAPPING APP FLOW ---
    // 1. Full-screen Scrolling Content Area (Swipeable Pager with Pure Bounce)
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1, 
            userScrollEnabled = true,
            pageSpacing = 0.dp,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(stiffness = 800f, dampingRatio = Spring.DampingRatioNoBouncy)
            )
        ) { page ->
            // --- PURE BOUNCE TRANSITION (Optimized for 120Hz) ---
            val pageOffset = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
            val absOffset = pageOffset.absoluteValue
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 1. Fade & Scale (Standard Premium Feel)
                        val lerpOffset = absOffset.coerceIn(0f, 1f)
                        alpha = (1f - lerpOffset * 1.5f).coerceIn(0f, 1f)
                        scaleX = 1f - (lerpOffset * 0.05f)
                        scaleY = 1f - (lerpOffset * 0.05f)
                        
                        // 2. PURE SIDE-BOUNCE (NO PARALLAX)
                        // Displaces the entire page content by the bounce amount
                        // Only applied to the active page being viewed
                        if (absOffset < 1f) {
                            translationX = bounceAnimatable.value * size.width
                        }
                    }
                    .clipToBounds()
            ) {
                    when (tabs[page]) {
                        "home" -> HomeScreen(
                            viewModel = musicViewModel,
                            onMiniPlayerClick = { showPlayer = true },
                            onOpenPlayer = { showPlayer = true }
                        )
                        "library" -> LibraryScreen(
                            viewModel = musicViewModel,
                            onMiniPlayerClick = { showPlayer = true }
                        )
                        "equalizer" -> EqualizerScreen(
                            viewModel = musicViewModel
                        )
                    }
                }
            }
        }

        // 2. FIXED FLOATING NAVIGATION AREA WITH GRADIENT MASK
        val navParallaxProgress = (1f - (currentOffset / screenHeightPx)).coerceIn(0f, 1f)
        val bgColor = MaterialTheme.colorScheme.background

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            bgColor.copy(alpha = 0.8f),
                            bgColor
                        )
                    )
                )
                .graphicsLayer {
                    translationY = navParallaxProgress * 80.dp.toPx()
                    alpha = (1f - navParallaxProgress * 1.5f).coerceIn(0f, 1f)
                    scaleX = 1f - (navParallaxProgress * 0.08f)
                    scaleY = 1f - (navParallaxProgress * 0.08f)
                }
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 32.dp), // Added top padding for the gradient
            contentAlignment = Alignment.Center
        ) {
            MusicBottomNavigation(
                currentTab = currentTab,
                onTabSelect = { selectedTab ->
                    val targetPage = tabs.indexOf(selectedTab)
                    if (targetPage != -1 && targetPage != pagerState.currentPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                },
                onSettingsClick = { showSettings = true }
            )
        }

        // --- OVERLAY LAYER: FLOATING MINI PLAYER ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 110.dp), 
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
                        progress = uiState.progress,
                        visualizerData = uiState.visualizerData,
                        onTogglePlay = { musicViewModel.togglePlayPause() },
                        onForward = { musicViewModel.skipNext() },
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

        // --- MODERN LIQUID SETTINGS LAYER ---
        val settingsProgress by animateFloatAsState(
            targetValue = if (showSettings) 1f else 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            ),
            label = "SettingsAnimation"
        )

        if (settingsProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 0.85f + (0.15f * settingsProgress)
                        scaleX = scale
                        scaleY = scale
                        alpha = settingsProgress.coerceIn(0f, 1f)
                        translationY = (1f - settingsProgress) * size.height * 0.5f
                        
                        // Luxurious rounded corners that morph as it opens
                        val cornerProgress = (1f - settingsProgress).coerceAtLeast(0f)
                        shape = RoundedCornerShape((32 * cornerProgress).dp)
                        clip = true
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* BLOCK CLICKS FROM PASSING THROUGH */ }
                    )
            ) {
                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle,
                    onBackClick = { showSettings = false },
                    userName = uiState.userName,
                    onUpdateName = { musicViewModel.updateUserName(it) }
                )
            }
        }
    }
}

@Composable
fun MusicBottomNavigation(
    currentTab: String,
    onTabSelect: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    // MACHINED CRYSTAL SURFACE BRUSH
    val surfaceBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF222222), Color(0xFF080808))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF2F2F2))
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp) // INCREASED FROM 72.dp TO PREVENT CLIPPING
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(41.dp),
                spotColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.75f), // SLIGHTLY INCREASED BLACK SHADOW
                ambientColor = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.28f) // SLIGHTLY INCREASED AMBIENT
            )
            .graphicsLayer {
                shape = RoundedCornerShape(41.dp)
                clip = true
            }
            .drawBehind {
                // 1. SILKY GRAIN TEXTURE
                val grainAlpha = if (isDark) 0.1f else 0.05f
                val grainColor = if (isDark) Color.White else Color.Black
                val step = 4.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt()) {
                    for (y in 0..size.height.toInt() step step.toInt()) {
                        if ((x * 13 + y * 17) % 11 == 0) {
                            drawCircle(
                                color = grainColor.copy(alpha = grainAlpha),
                                radius = 0.6.dp.toPx(),
                                center = Offset(x.toFloat(), y.toFloat())
                            )
                        }
                    }
                }

                // 2. DIAMOND-CUT DOUBLE RIM
                val outerRimColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.12f)
                val innerRimColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.5f)
                
                // Outer edge
                drawRoundRect(
                    color = outerRimColor,
                    style = Stroke(width = 1.2.dp.toPx()),
                    cornerRadius = CornerRadius(41.dp.toPx())
                )
                // Inner highlight
                drawRoundRect(
                    color = innerRimColor,
                    topLeft = Offset(1.2.dp.toPx(), 1.2.dp.toPx()),
                    size = Size(size.width - 2.4.dp.toPx(), size.height - 2.4.dp.toPx()),
                    style = Stroke(width = 0.6.dp.toPx()),
                    cornerRadius = CornerRadius(39.8.dp.toPx())
                )
            }
            .background(surfaceBrush),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
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
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = false,
                onClick = onSettingsClick
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
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = 600f, dampingRatio = 0.55f),
        label = "NavScale"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "NavColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        // 3D ICON CONTAINER
        Box(
            modifier = Modifier
                .size(42.dp)
                .graphicsLayer {
                    if (isSelected) {
                        shadowElevation = 12.dp.toPx()
                        shape = CircleShape
                        clip = true
                        spotShadowColor = contentColor.copy(alpha = 0.4f)
                    }
                }
                .background(
                    if (isSelected) contentColor.copy(alpha = 0.1f) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
        }
        
        AnimatedVisibility(
            visible = isSelected,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = contentColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
