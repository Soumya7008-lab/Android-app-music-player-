package com.example.myapplication

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onMiniPlayerClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPlaylist = remember(uiState.selectedPlaylistId, uiState.playlists) {
        uiState.playlists.find { it.id == uiState.selectedPlaylistId }
    }
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            if (selectedPlaylist == null) "LIBRARY" else selectedPlaylist.name.uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        if (selectedPlaylist != null) {
                            IconButton(onClick = { viewModel.setSelectedPlaylist(null) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        if (selectedPlaylist != null && !selectedPlaylist.isDefault) {
                            var showDeleteMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showDeleteMenu = true }) {
                                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                                DropdownMenu(
                                    expanded = showDeleteMenu,
                                    onDismissRequest = { showDeleteMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Playlist", color = Color.Red) },
                                        onClick = {
                                            viewModel.deletePlaylist(selectedPlaylist.id)
                                            viewModel.setSelectedPlaylist(null)
                                            showDeleteMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = { Spacer(modifier = Modifier.height(1.dp)) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AnimatedContent(
                    targetState = uiState.selectedPlaylistId,
                    transitionSpec = {
                        if (targetState != null) {
                            (scaleIn(
                                initialScale = 0.85f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)
                            ) + fadeIn()).togetherWith(fadeOut(animationSpec = tween(200)))
                        } else {
                            fadeIn(animationSpec = tween(300)) togetherWith (scaleOut(
                                targetScale = 0.85f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                            ) + fadeOut())
                        }
                    },
                    label = "LibraryPopAnimation"
                ) { targetPlaylistId ->
                    if (targetPlaylistId == null) {
                        PlaylistLandingView(
                            playlists = uiState.playlists,
                            onPlaylistClick = { viewModel.setSelectedPlaylist(it.id) },
                            onCreateClick = { showCreateDialog = true },
                            viewModel = viewModel
                        )
                    } else {
                        val playlist = uiState.playlists.find { it.id == targetPlaylistId }
                        if (playlist != null) {
                            PlaylistDetailView(
                                playlist = playlist,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    viewModel.createPlaylist(name)
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
fun PlaylistLandingView(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: MusicViewModel
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
            "Your Collections",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Mood-based and custom playlists",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            item {
                CreatePlaylistCard(onClick = onCreateClick)
            }

            items(playlists) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun CreatePlaylistCard(onClick: () -> Unit) {
    val themeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(themeColor.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = themeColor.copy(alpha = 0.4f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    ),
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Add,
                null,
                modifier = Modifier.size(48.dp),
                tint = themeColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Create New",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = themeColor
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    viewModel: MusicViewModel
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Launcher for selecting playlist poster
    val posterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.updatePlaylistArtwork(playlist.id, it.toString())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .drawBehind {
                if (isDark) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.15f),
                        style = Stroke(width = 1.2.dp.toPx()),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // --- UPDATED: Use playlist custom artwork if available ---
            if (playlist.customArtworkUri != null) {
                AsyncImage(
                    model = playlist.customArtworkUri,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    if (playlist.isDefault) Icons.Default.AutoAwesome else Icons.Default.LibraryMusic,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${playlist.tracks.size} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    showMenu = false
                    showRenameDialog = true
                },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            // NEW: Set Poster Option
            DropdownMenuItem(
                text = { Text("Set Poster") },
                onClick = {
                    showMenu = false
                    posterLauncher.launch("image/*")
                },
                leadingIcon = { Icon(Icons.Default.Image, null) }
            )
            // NEW: Delete Playlist Option (for non-default playlists)
            if (!playlist.isDefault) {
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.Red) },
                    onClick = {
                        showMenu = false
                        viewModel.deletePlaylist(playlist.id)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                )
            }
        }
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { 
                viewModel.renamePlaylist(playlist.id, it)
                showRenameDialog = false
            }
        )
    }
}

@Composable
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Rename Playlist", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
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
                onClick = { if (name.isNotBlank()) onConfirm(name) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    viewModel: MusicViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddTracksDialog by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var currentTargetIndex by remember { mutableStateOf<Int?>(null) }

    val weightedSpringSpec = spring<Float>(
        stiffness = Spring.StiffnessLow,
        dampingRatio = 0.9f
    )

    var overscrollOffset by remember { mutableFloatStateOf(0f) }
    val springOverscroll by animateFloatAsState(
        targetValue = overscrollOffset,
        animationSpec = weightedSpringSpec,
        label = "LocalizedSpring"
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f && draggedIndex == null) overscrollOffset += available.y * 0.2f
                return super.onPostScroll(consumed, available, source)
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (overscrollOffset != 0f) overscrollOffset = 0f
                return Offset.Zero
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PlaylistHeader(
            playlist = playlist, // Updated to pass playlist object for artwork
            isShuffleOn = uiState.shuffleModeEnabled,
            onPlayAll = { if (playlist.tracks.isNotEmpty()) viewModel.setTrack(playlist.tracks[0]) },
            onAddTracks = { showAddTracksDialog = true },
            onToggleShuffle = { viewModel.toggleShuffle() }
        )

        if (playlist.tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No songs in this playlist yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showAddTracksDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ADD SONGS")
                    }
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
                    .graphicsLayer {
                        translationY = springOverscroll
                        scaleY = 1f + (kotlin.math.abs(springOverscroll) / 6000f)
                    },
                contentPadding = PaddingValues(bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = playlist.tracks,
                    key = { _, track -> track.id }
                ) { index, track ->
                    val isDragged = draggedIndex == index

                    val itemHeightPx = with(density) { 80.dp.toPx() }
                    val targetIdx = if (draggedIndex != null) {
                        (draggedIndex!! + (dragOffset / itemHeightPx).toInt()).coerceIn(0, playlist.tracks.size - 1)
                    } else null
                    
                    val displacementY by animateFloatAsState(
                        targetValue = if (draggedIndex != null && !isDragged) {
                            if (index > draggedIndex!! && index <= targetIdx!!) -itemHeightPx
                            else if (index < draggedIndex!! && index >= targetIdx!!) itemHeightPx
                            else 0f
                        } else 0f,
                        animationSpec = weightedSpringSpec,
                        label = "Displacement"
                    )

                    val verticalOffset by animateFloatAsState(
                        targetValue = if (isDragged) dragOffset else displacementY,
                        animationSpec = if (isDragged) snap() else weightedSpringSpec,
                        label = "DragAnimation"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragged) 10f else 0f)
                            .graphicsLayer {
                                translationY = verticalOffset
                                scaleX = if (isDragged) 1.03f else 1f
                                scaleY = if (isDragged) 1.03f else 1f
                                alpha = if (isDragged) 0.85f else 1f
                            }
                            .pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { 
                                        draggedIndex = index
                                        currentTargetIndex = index
                                    },
                                    onDragEnd = {
                                        if (draggedIndex != null && currentTargetIndex != null && draggedIndex != currentTargetIndex) {
                                            viewModel.moveTrack(playlist.id, draggedIndex!!, currentTargetIndex!!)
                                        }
                                        draggedIndex = null
                                        dragOffset = 0f
                                        currentTargetIndex = null
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffset = 0f
                                        currentTargetIndex = null
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        currentTargetIndex = (index + (dragOffset / itemHeightPx).toInt()).coerceIn(0, playlist.tracks.size - 1)
                                    }
                                )
                            }
                    ) {
                        TrackListItem(
                            number = index + 1,
                            title = track.title,
                            artist = track.artist,
                            duration = track.duration,
                            isPlaying = track == uiState.currentTrack && uiState.isPlaying,
                            onClick = { 
                                if (track == uiState.currentTrack) viewModel.togglePlayPause()
                                else viewModel.setTrack(track)
                            },
                            // NEW: THREE-DOT MENU FOR INSTANT REMOVAL
                            trailingContent = {
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Remove from Playlist", color = Color.Red) },
                                            onClick = {
                                                viewModel.removeTrackFromPlaylist(track, playlist.id)
                                                showMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddTracksDialog) {
        AddTracksToPlaylistDialog(
            allTracks = uiState.playlist,
            existingTracks = playlist.tracks,
            onDismiss = { showAddTracksDialog = false },
            onToggleTrack = { track, shouldAdd ->
                if (shouldAdd) viewModel.addTrackToPlaylist(track, playlist.id)
                else viewModel.removeTrackFromPlaylist(track, playlist.id)
            }
        )
    }
}

@Composable
fun PlaylistHeader(
    playlist: Playlist,
    isShuffleOn: Boolean,
    onPlayAll: () -> Unit,
    onAddTracks: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    Column(modifier = Modifier.padding(24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // LAYER 1: Modern 3D Soft Light Spill (Moved slightly further inside)
            Box(
                modifier = Modifier
                    .size(440.dp)
                    .offset(x = -75.dp) // Nudged further inward toward the center of the poster
                    .drawBehind {
                        val coreAlpha = if (isDark) 0.65f else 0.35f
                        val ambientAlpha = if (isDark) 0.25f else 0.12f
                        val glowColor = if (isDark) Color.White else Color.Black

                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = coreAlpha),
                                    glowColor.copy(alpha = ambientAlpha),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.maxDimension / 2f
                            ),
                            radius = size.maxDimension / 2f,
                            center = center
                        )
                    }
            )

            // LAYER 2: The Clipped Poster & Rim Border (With Elevated Depth)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        shadowElevation = 16.dp.toPx()
                        shape = RoundedCornerShape(24.dp)
                        clip = true
                        spotShadowColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f)
                        ambientShadowColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.15f)
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .drawBehind {
                        val rimColor = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.15f)
                        drawRoundRect(
                            color = rimColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (playlist.customArtworkUri != null) {
                    AsyncImage(
                        model = playlist.customArtworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            playlist.name,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "${playlist.tracks.size} tracks in collection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f).height(56.dp)) {
                LibraryThreeDButton(
                    onClick = onPlayAll,
                    text = "PLAY ALL",
                    icon = Icons.Default.PlayArrow
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(56.dp)) {
                LibraryThreeDButton(onClick = onAddTracks, text = "", icon = Icons.Default.Add)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(56.dp)) {
                LibraryThreeDButton(
                    onClick = onToggleShuffle,
                    text = "",
                    icon = Icons.Default.Shuffle,
                    isActive = isShuffleOn
                )
            }
        }
    }
}
@Composable
fun AddTracksToPlaylistDialog(
    allTracks: List<Track>,
    existingTracks: List<Track>,
    onDismiss: () -> Unit,
    onToggleTrack: (Track, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val scrollState = rememberLazyListState()

    val filteredTracks = remember(allTracks, searchQuery) {
        if (searchQuery.isEmpty()) allTracks
        else allTracks.filter {
            it.title.lowercase().contains(searchQuery.lowercase()) ||
            it.artist.lowercase().contains(searchQuery.lowercase())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Column {
                Text("Manage Songs", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                SleekSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search songs...",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Box(modifier = Modifier.height(400.dp)) {
                LazyColumn(state = scrollState) {
                    items(
                        items = filteredTracks,
                        key = { it.id }
                    ) { track ->
                        val isAlreadyAdded = existingTracks.any { it.id == track.id }
                        val themeColor = MaterialTheme.colorScheme.primary
                        val isDark = MaterialTheme.colorScheme.background == Color.Black
                        val selectionColor = if (isAlreadyAdded) themeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleTrack(track, !isAlreadyAdded) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAlreadyAdded) themeColor else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAlreadyAdded) themeColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .drawBehind {
                                        if (isAlreadyAdded) {
                                            drawRoundRect(
                                                color = themeColor.copy(alpha = 0.8f),
                                                style = Stroke(width = 2.dp.toPx()),
                                                cornerRadius = CornerRadius(8.dp.toPx())
                                            )
                                        } else {
                                            drawRoundRect(
                                                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                                                style = Stroke(width = 1.dp.toPx()),
                                                cornerRadius = CornerRadius(8.dp.toPx())
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAlreadyAdded) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = selectionColor
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Done") }
        }
    )
}

@Composable
fun LibraryThreeDButton(
    onClick: () -> Unit,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "BtnScale"
    )

    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    // TUNED: Added a rich dark gray tint (Color(0xFF262626)) for dark mode so the buttons pop out clearly
    val surfaceColor = if (isActive) themeColor
    else if (text.isNotEmpty()) themeColor
    else (if (isDark) Color(0xFF262626) else Color(0xFFF5F5F5))

    val contentColor = if (isActive) (if (isDark) Color.Black else Color.White)
    else if (text.isNotEmpty()) MaterialTheme.colorScheme.onPrimary
    else (if (isDark) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.85f))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isPressed) 4.dp.toPx() else 24.dp.toPx()
                shape = RoundedCornerShape(16.dp)
                clip = true
                // TUNED: Slightly softened shadow intensities for a balanced look
                spotShadowColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f)
                ambientShadowColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.3f)
            }
            .drawBehind {
                val shadowColor = if (isDark) Color.White else Color.Black
                val shadowAlpha = if (isDark) 0.45f else 0.4f
                val offsetY = if (isPressed) 3.dp.toPx() else 10.dp.toPx()
                val spread = 5.dp.toPx()

                drawRoundRect(
                    color = shadowColor.copy(alpha = shadowAlpha),
                    topLeft = Offset(-spread / 2f, offsetY),
                    size = androidx.compose.ui.geometry.Size(size.width + spread, size.height + spread),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )

                // High-definition rim light border
                val rimAlpha: Float = if (isActive) 1f else (if (isDark) 0.6f else 0.25f)
                val rimColor = if (isActive) themeColor else (if (isDark) Color.White else Color.Black)

                drawRoundRect(
                    color = rimColor.copy(alpha = rimAlpha),
                    size = size,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = if (isActive) 2.2.dp.toPx() else 1.8.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(24.dp))
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text, color = contentColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp)
            }
        }
    }
}
@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Create Playlist", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) { Text("Cancel") }
        }
    )
}
