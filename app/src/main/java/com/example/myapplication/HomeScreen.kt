package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onMiniPlayerClick: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val imagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            pendingAction?.invoke()
        }
        pendingAction = null
    }

    fun runWithPermission(permission: String, action: () -> Unit) {
        Log.d("HomeScreen", "runWithPermission: checking $permission")
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            Log.d("HomeScreen", "runWithPermission: already granted")
            action()
        } else {
            Log.d("HomeScreen", "runWithPermission: requesting $permission")
            pendingAction = action
            permissionLauncher.launch(arrayOf(permission))
        }
    }

    var trackToEdit by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    val lazyListState = rememberLazyListState()

    val liquidSpringSpec = spring<Float>(
        stiffness = Spring.StiffnessLow,
        dampingRatio = Spring.DampingRatioLowBouncy
    )

    var overscrollOffset by remember { mutableFloatStateOf(0f) }
    val springOverscroll by animateFloatAsState(
        targetValue = overscrollOffset,
        animationSpec = liquidSpringSpec,
        label = "LiquidOverscroll"
    )

    val marqueeMaxHeight = 240.dp
    val marqueeMaxHeightPx = with(density) { marqueeMaxHeight.toPx() }
    var marqueeOffsetHeightPx by remember { mutableFloatStateOf(0f) }
    val animatedMarqueeOffset by animateFloatAsState(
        targetValue = marqueeOffsetHeightPx,
        animationSpec = liquidSpringSpec,
        label = "LiquidMarquee"
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (overscrollOffset != 0f) overscrollOffset = 0f

                return if (delta < 0) {
                    val oldOffset = marqueeOffsetHeightPx
                    val newOffset = (oldOffset + delta).coerceIn(-marqueeMaxHeightPx, 0f)
                    marqueeOffsetHeightPx = newOffset
                    val consumed = newOffset - oldOffset
                    Offset(x = 0f, y = consumed)
                } else {
                    if (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0) {
                        val oldOffset = marqueeOffsetHeightPx
                        val newOffset = (oldOffset + delta).coerceIn(-marqueeMaxHeightPx, 0f)
                        marqueeOffsetHeightPx = newOffset
                        val consumed = newOffset - oldOffset
                        Offset(x = 0f, y = consumed)
                    } else {
                        Offset.Zero
                    }
                }
            }
        }
    }

    val shrinkProgress = (1f + (animatedMarqueeOffset / marqueeMaxHeightPx)).coerceIn(0f, 1f)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            trackToEdit?.let { track ->
                viewModel.updateTrackArtwork(track, it.toString())
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris, context.contentResolver)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            // HEADER Area
            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "HOME",
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HomeHeader(
                onScan = { runWithPermission(audioPermission) { viewModel.loadLocalMusic() } },
                onImport = { importLauncher.launch(arrayOf("audio/*")) },
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                userName = uiState.userName
            )

            AnimatedVisibility(
                visible = uiState.searchQuery.isEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lerp(0.dp, marqueeMaxHeight, shrinkProgress))
                        .alpha(shrinkProgress)
                        .graphicsLayer {
                            scaleY = 0.95f + (0.05f * shrinkProgress)
                            translationY = animatedMarqueeOffset * 0.15f
                        }
                ) {
                    if (shrinkProgress > 0.05f) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                "RECENTLY PLAYED",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RecentlyPlayedMarquee(
                                tracks = uiState.playlist.take(10),
                                currentTrack = uiState.currentTrack,
                                isPlaying = uiState.isPlaying,
                                onTrackClick = { track ->
                                    val index = uiState.playlist.indexOf(track)
                                    viewModel.playPlaylist(uiState.playlist, if (index != -1) index else 0, "recently_played")
                                    onOpenPlayer()
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 24.dp)
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            val oldOffset = marqueeOffsetHeightPx
                            val newOffset = (oldOffset + delta).coerceIn(-marqueeMaxHeightPx, 0f)
                            marqueeOffsetHeightPx = newOffset
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (uiState.searchQuery.isEmpty()) "All Tracks" else "Search Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (uiState.searchQuery.isEmpty()) {
                    SortPill(
                        currentOrder = uiState.sortOrder,
                        onOrderSelect = { viewModel.setSortOrder(it) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (uiState.filteredPlaylist.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (uiState.searchQuery.isEmpty()) {
                        EmptyHomeState(
                            onScan = { runWithPermission(audioPermission) { viewModel.loadLocalMusic() } },
                            onImport = { importLauncher.launch(arrayOf("audio/*")) }
                        )
                    } else {
                        Text("No tracks found matching \"${uiState.searchQuery}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = springOverscroll
                            scaleY = 1f + (kotlin.math.abs(springOverscroll) / 4000f)
                        },
                    contentPadding = PaddingValues(bottom = 220.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredPlaylist) { track ->
                        HomeTrackItem(
                            track = track,
                            isCurrentTrack = track == uiState.currentTrack,
                            isPlaying = uiState.isPlaying,
                            playlists = uiState.playlists,
                            onPlayToggle = {
                                if (track == uiState.currentTrack) viewModel.togglePlayPause()
                                else {
                                    val index = uiState.filteredPlaylist.indexOf(track)
                                    viewModel.playPlaylist(uiState.filteredPlaylist, index, "all_tracks")
                                }
                            },
                            onAddToPlaylist = { viewModel.addTrackToPlaylist(track, it) },
                            onDelete = { viewModel.deleteTrack(track) },
                            onEditPoster = {
                                runWithPermission(imagePermission) {
                                    trackToEdit = track
                                    galleryLauncher.launch("image/*")
                                }
                            },
                            onRename = { trackToRename = it }
                        )
                    }
                }
            }
        }

        if (trackToRename != null) {
            RenameTrackDialog(
                track = trackToRename!!,
                onDismiss = { trackToRename = null },
                onConfirm = { newTitle ->
                    viewModel.renameTrack(trackToRename!!, newTitle)
                    trackToRename = null
                }
            )
        }
    }
}

@Composable
fun RenameTrackDialog(track: Track, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf(track.title) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Rename Track", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) { Text("Cancel") }
        }
    )
}

@Composable
fun HomeHeader(
    onScan: () -> Unit,
    onImport: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    userName: String
) {
    Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp)) {
        Text(
            "Welcome, $userName",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Discover and manage your music",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        SleekSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = "Search your library..."
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            ModernActionPill(
                onClick = onScan,
                text = "RESCAN",
                icon = Icons.Default.Search,
                isOutlined = false,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            ModernActionPill(
                onClick = onImport,
                text = "IMPORT",
                icon = Icons.Default.FileUpload,
                isOutlined = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SortPill(
    currentOrder: SortOrder,
    onOrderSelect: (SortOrder) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    Box {
        Surface(
            onClick = { showMenu = true },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(32.dp)
                .graphicsLayer {
                    if (isDark) {
                        shadowElevation = 6.dp.toPx()
                        spotShadowColor = Color.White.copy(alpha = 0.15f)
                        ambientShadowColor = Color.White.copy(alpha = 0.1f)
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (currentOrder == SortOrder.NAME) "Name" else "Last Added",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            DropdownMenuItem(
                text = { Text("Sort by Name") },
                onClick = {
                    onOrderSelect(SortOrder.NAME)
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Default.TextFields, null) }
            )
            DropdownMenuItem(
                text = { Text("Sort by Last Added") },
                onClick = {
                    onOrderSelect(SortOrder.LAST_ADDED)
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Default.History, null) }
            )
        }
    }
}

@Composable
fun ModernActionPill(
    onClick: () -> Unit,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isOutlined: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.5f),
        label = "PillScale"
    )

    val isDark = MaterialTheme.colorScheme.background == Color.Black
    
    // INVERTED METALLIC LOGIC: Black in light mode, White in dark mode
    val metalBrush = if (isDark) {
        // Dark Mode -> White Metal Button
        Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFE5E5E5))
        )
    } else {
        // Light Mode -> Black Metal Button
        Brush.verticalGradient(
            colors = listOf(Color(0xFF2C2C2C), Color(0xFF000000))
        )
    }

    val primaryColor = if (isDark) Color.White else Color.Black
    val contentColor = if (isDark) Color.Black else Color.White

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(50.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.3f),
                ambientColor = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.1f)
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .then(
                if (isOutlined) {
                    Modifier
                        .background(Color.Transparent)
                        .drawBehind {
                            drawRoundRect(
                                color = primaryColor,
                                style = Stroke(width = 2.dp.toPx()),
                                cornerRadius = CornerRadius(25.dp.toPx())
                            )
                        }
                } else {
                    Modifier
                        .background(metalBrush)
                        .drawBehind {
                            // METALLIC RIM (Inverted for contrast)
                            val rimColor = if (isDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f)
                            drawRoundRect(
                                color = rimColor,
                                style = Stroke(width = 1.2.dp.toPx()),
                                cornerRadius = CornerRadius(25.dp.toPx())
                            )
                        }
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon, 
                null, 
                tint = if (isOutlined) primaryColor else contentColor, 
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (isOutlined) primaryColor else contentColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun RecentlyPlayedMarquee(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit
) {
    val scrollState = rememberScrollState()
    val isScrollInProgress = scrollState.isScrollInProgress

    LaunchedEffect(isScrollInProgress) {
        if (!isScrollInProgress) {
            while (true) {
                delay(30)
                scrollState.scrollBy(1.5f)
                if (scrollState.value >= scrollState.maxValue) {
                    scrollState.scrollTo(0)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        (tracks + tracks + tracks).forEach { track ->
            val isDark = MaterialTheme.colorScheme.background == Color.Black
            val isCurrent = track == currentTrack

            val metalBrush = if (isDark) {
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1C1C1C), Color(0xFF0F0F0F))
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF9F9F9), Color(0xFFEBEBEB))
                )
            }

            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(200.dp)
                    .graphicsLayer {
                        shape = RoundedCornerShape(24.dp)
                        clip = true
                        if (isDark) {
                            shadowElevation = 8.dp.toPx()
                            spotShadowColor = Color.White.copy(alpha = 0.15f)
                            ambientShadowColor = Color.White.copy(alpha = 0.1f)
                        } else {
                            shadowElevation = 6.dp.toPx()
                            spotShadowColor = Color.Black.copy(alpha = 0.1f)
                        }
                    }
                    .drawBehind {
                        val rimColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f)
                        drawRoundRect(
                            color = rimColor,
                            style = Stroke(width = 1.2.dp.toPx()),
                            cornerRadius = CornerRadius(24.dp.toPx())
                        )
                    }
                    .background(metalBrush, shape = RoundedCornerShape(24.dp))
                    .clickable { onTrackClick(track) }
                    .padding(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.customArtworkUri != null) {
                            AsyncImage(
                                model = track.customArtworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                              )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTrackItem(
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    playlists: List<Playlist>,
    onPlayToggle: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onDelete: () -> Unit,
    onEditPoster: () -> Unit,
    onRename: (Track) -> Unit
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    // BLACK METAL TINT GRADIENT
    val metalBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1C1C1C), Color(0xFF0F0F0F))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF9F9F9), Color(0xFFEBEBEB))
        )
    }

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shape = RoundedCornerShape(20.dp)
                clip = true
                if (isDark) {
                    shadowElevation = 8.dp.toPx()
                    spotShadowColor = Color.White.copy(alpha = 0.15f)
                    ambientShadowColor = Color.White.copy(alpha = 0.1f)
                } else {
                    shadowElevation = 6.dp.toPx()
                    spotShadowColor = Color.Black.copy(alpha = 0.1f)
                }
            }
            .drawBehind {
                // METALLIC RIM HIGHLIGHT
                val rimColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f)
                drawRoundRect(
                    color = rimColor,
                    style = Stroke(width = 1.2.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
            }
            .background(metalBrush, shape = RoundedCornerShape(20.dp))
            .clickable { onPlayToggle() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(54.dp)) {
                if (track.customArtworkUri != null) {
                    AsyncImage(
                        model = track.customArtworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(themeColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = themeColor.copy(alpha = 0.5f))
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentTrack) themeColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(onClick = onPlayToggle) {
                Icon(
                    if (isCurrentTrack && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    tint = themeColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            var showMenu by remember { mutableStateOf(false) }
            var showPlaylistMenu by remember { mutableStateOf(false) }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        onClick = {
                            showMenu = false
                            showPlaylistMenu = true
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Track") },
                        onClick = {
                            showMenu = false
                            onRename(track)
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Poster") },
                        onClick = {
                            showMenu = false
                            onEditPoster()
                        },
                        leadingIcon = { Icon(Icons.Default.Image, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    )
                }

                if (showPlaylistMenu) {
                    AlertDialog(
                        onDismissRequest = { showPlaylistMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        title = { Text("Select Playlist", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                playlists.forEach { pl ->
                                    TextButton(
                                        onClick = {
                                            onAddToPlaylist(pl.id)
                                            showPlaylistMenu = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(pl.name, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showPlaylistMenu = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHomeState(onScan: () -> Unit, onImport: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp), // Clear the floating nav bar
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                Icons.Default.MusicOff,
                null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Your library is empty",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Scan your device or import your favorite tracks to start listening.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ModernActionPill(
                    onClick = onScan,
                    text = "AUTO SCAN",
                    icon = Icons.Default.Search,
                    isOutlined = false,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                ModernActionPill(
                    onClick = onImport,
                    text = "IMPORT",
                    icon = Icons.Default.FileUpload,
                    isOutlined = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
