package com.example.myapplication

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
    onMiniPlayerClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPlaylist = remember(uiState.selectedPlaylistId, uiState.playlists) {
        uiState.playlists.find { it.id == uiState.selectedPlaylistId }
    }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Handle back button for playlist details
    BackHandler(enabled = uiState.selectedPlaylistId != null) {
        viewModel.setSelectedPlaylist(null)
    }

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
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isEmpty()) playlists
        else playlists.filter { it.name.lowercase().contains(searchQuery.lowercase()) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
            "Your Collections",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Manage your mood-based and custom playlists",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SLEEK SEARCH FOR PLAYLISTS
        SleekSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Find a collection...",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 220.dp)
        ) {
            item {
                CreatePlaylistCard(onClick = onCreateClick)
            }

            items(filteredPlaylists) { playlist ->
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

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "CreateCardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f) // More modern rectangular profile
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(28.dp))
            .background(themeColor.copy(alpha = 0.05f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                drawRoundRect(
                    color = themeColor.copy(alpha = 0.3f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                    ),
                    cornerRadius = CornerRadius(28.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = themeColor.copy(alpha = 0.1f)
            ) {
                Icon(
                    Icons.Default.Add,
                    null,
                    modifier = Modifier.padding(12.dp),
                    tint = themeColor
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "NEW COLLECTION",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = themeColor,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    viewModel: MusicViewModel
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val themeColor = MaterialTheme.colorScheme.primary
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "CardScale"
    )

    val posterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.updatePlaylistArtwork(playlist.id, it.toString())
        }
    }

    val metalBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1C1C1C), Color(0xFF0F0F0F))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF9F9F9), Color(0xFFEBEBEB))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        // MODERN FLOATING POSTER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    shape = RoundedCornerShape(24.dp)
                    clip = true
                    if (isDark) {
                        shadowElevation = 12.dp.toPx()
                        spotShadowColor = Color.White.copy(alpha = 0.15f)
                        ambientShadowColor = Color.White.copy(alpha = 0.1f)
                    } else {
                        shadowElevation = 8.dp.toPx()
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
                .background(metalBrush, shape = RoundedCornerShape(24.dp)),
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(themeColor.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playlist.isDefault) Icons.Default.AutoAwesome else Icons.Default.LibraryMusic,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = themeColor.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            playlist.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            "${playlist.tracks.size} TRACKS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 0.5.sp
        )

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
            DropdownMenuItem(
                text = { Text("Set Poster") },
                onClick = {
                    showMenu = false
                    posterLauncher.launch("image/*")
                },
                leadingIcon = { Icon(Icons.Default.Image, null) }
            )
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
    val primaryColor = MaterialTheme.colorScheme.primary

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
            playlist = playlist,
            isShuffleOn = uiState.shuffleModeEnabled,
            onPlayAll = {
                if (playlist.tracks.isNotEmpty()) {
                    viewModel.playPlaylist(playlist.tracks, 0, playlist.id)
                }
            },
            onAddTracks = { showAddTracksDialog = true },
            onToggleShuffle = { viewModel.toggleShuffle() }
        )

        // THIN DIVIDER
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
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
                        // APPLY ELASTIC SCALE EFFECT
                        val scale = 1f + (kotlin.math.abs(springOverscroll) / 8000f)
                        scaleX = scale
                        scaleY = scale
                    },
                contentPadding = PaddingValues(bottom = 220.dp), // INCREASED TO CLEAR MINI PLAYER
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = playlist.tracks,
                    key = { _, track -> track.id }
                ) { index, track ->
                    val isDragged = draggedIndex == index

                    // PRECISE HEIGHT MATCHING
                    val itemHeightPx = with(density) { 84.dp.toPx() }

                    val targetIdx = if (draggedIndex != null) {
                        (draggedIndex!! + (dragOffset / itemHeightPx).roundToInt()).coerceIn(0, playlist.tracks.size - 1)
                    } else null

                    val displacementY by animateFloatAsState(
                        targetValue = if (draggedIndex != null && !isDragged) {
                            if (index > draggedIndex!! && index <= targetIdx!!) -itemHeightPx
                            else if (index < draggedIndex!! && index >= targetIdx!!) itemHeightPx
                            else 0f
                        } else 0f,
                        animationSpec = spring(stiffness = 400f, dampingRatio = 0.85f),
                        label = "Displacement"
                    )

                    val verticalOffset by animateFloatAsState(
                        targetValue = if (isDragged) dragOffset else displacementY,
                        animationSpec = if (isDragged) snap() else spring(stiffness = 400f, dampingRatio = 0.85f),
                        label = "DragAnimation"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragged) 10f else 0f)
                            .graphicsLayer {
                                translationY = verticalOffset
                                if (isDragged) {
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    alpha = 0.98f

                                    // FIXED: ALIGNED GLOW SHADOW WITH SHAPE
                                    shadowElevation = 24.dp.toPx()
                                    shape = RoundedCornerShape(16.dp)
                                    clip = false
                                    spotShadowColor = primaryColor.copy(alpha = 0.6f)
                                    ambientShadowColor = primaryColor.copy(alpha = 0.3f)
                                }
                            }
                            .then(
                                if (isDragged) {
                                    Modifier.background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                } else Modifier
                            )
                            .pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffset = 0f
                                        currentTargetIndex = index
                                    },
                                    onDragEnd = {
                                        val from = draggedIndex
                                        val to = currentTargetIndex
                                        if (from != null && to != null && from != to) {
                                            viewModel.moveTrack(playlist.id, from, to)
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
                                        currentTargetIndex = (index + (dragOffset / itemHeightPx).roundToInt()).coerceIn(0, playlist.tracks.size - 1)
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
                                else viewModel.playPlaylist(playlist.tracks, index, playlist.id)
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
    val themeColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // COMPACT POSTER WITH REFINED SLEEK GLOW
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .drawBehind {
                        val glowAlpha = if (isDark) 0.18f else 0.08f
                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(themeColor.copy(alpha = glowAlpha), Color.Transparent),
                                center = center,
                                radius = size.maxDimension * 0.9f
                            ),
                            radius = size.maxDimension * 0.9f,
                            center = center
                        )
                    }
                    .graphicsLayer {
                        shadowElevation = 16.dp.toPx()
                        shape = RoundedCornerShape(20.dp)
                        clip = true
                        spotShadowColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.25f)
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .drawBehind {
                        val rimColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.1f)
                        drawRoundRect(
                            color = rimColor,
                            style = Stroke(width = 1.2.dp.toPx()),
                            cornerRadius = CornerRadius(20.dp.toPx())
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
                        modifier = Modifier.size(48.dp),
                        tint = themeColor.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Text(
                    "${playlist.tracks.size} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // COMPACT BUTTONS ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1.5f).height(48.dp)) {
                LibraryThreeDButton(
                    onClick = onPlayAll,
                    text = "PLAY",
                    icon = Icons.Default.PlayArrow
                )
            }
            Box(modifier = Modifier.weight(1f).height(48.dp)) {
                LibraryThreeDButton(onClick = onAddTracks, text = "ADD", icon = Icons.Default.Add)
            }
            Box(modifier = Modifier.size(48.dp)) {
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
