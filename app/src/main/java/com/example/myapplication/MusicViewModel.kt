package com.example.myapplication

import android.app.Application
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.myapplication.audio.MusicService
import com.example.myapplication.data.MediaScanner
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

val Context.dataStore by preferencesDataStore(name = "music_prefs")

enum class SortOrder {
    NAME, LAST_ADDED
}

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val data: String,
    val duration: String,
    val dateAdded: Long = 0,
    val customArtworkUri: String? = null
)

data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val isDefault: Boolean = false,
    val customArtworkUri: String? = null // NEW: Playlist custom poster support
)

data class EqPreset(
    val name: String,
    val bands: List<Float>
)

data class UiState(
    val playlist: List<Track> = emptyList(),
    val filteredPlaylist: List<Track> = emptyList(),
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val skipDirection: Int = 0,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isLoading: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val sortOrder: SortOrder = SortOrder.LAST_ADDED,
    val searchQuery: String = "",
    val selectedPlaylistId: String? = null,
    val playingPlaylistId: String? = null,
    val is16DEnabled: Boolean = false, // 16D State
    // --- TITAN AUDIO ENGINE STATE ---
    val masterVolume: Float = 1.0f,
    val clarityLevel: Float = 0.5f,
    val snappiness: Float = 0.5f,
    val soundstageWidth: Float = 1.0f,
    val tempo: Float = 1.0f,
    // --- EQUALIZER & VISUALIZER STATE ---
    val eqBands: List<Float> = listOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f),
    val selectedPreset: String = "Flat",
    val userName: String = "User",
    val isOnboardingRequired: Boolean = true,
    val eqPresets: List<EqPreset> = listOf(
        EqPreset("Flat", listOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)),
        EqPreset("Bass Boost", listOf(0.95f, 0.7f, 0.5f, 0.4f, 0.3f)), // Aggressive Lows
        EqPreset("Studio Crystal", listOf(0.45f, 0.5f, 0.6f, 0.85f, 0.95f)), // High clarity
        EqPreset("Vocals", listOf(0.2f, 0.45f, 0.9f, 0.7f, 0.4f)),
        EqPreset("Cinema", listOf(0.85f, 0.6f, 0.4f, 0.6f, 0.9f))
    ),
    val visualizerData: List<Float> = List(20) { 0.1f }
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState(
        userName = PreferenceManager.getUserName(application),
        isOnboardingRequired = PreferenceManager.isFirstLaunch(application)
    ))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private val mediaScanner = MediaScanner(application)
    private val app = application
    
    private val OVERRIDES_KEY = stringPreferencesKey("track_overrides")
    private val PLAYLISTS_KEY = stringPreferencesKey("playlists_data")
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? 
        get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    // Track modifications to ensure they are never lost
    private val trackOverrides = mutableMapOf<Long, Pair<String?, String?>>()

    init {
        // PRIORITY 1: Setup controller immediately
        setupMediaController(application)
        // PRIORITY 2: Load data on a high-priority background thread
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val rawTracks = mediaScanner.scanAudioFiles()
            
            val overridesDeferred = async { app.dataStore.data.map { it[OVERRIDES_KEY] ?: "" }.first() }
            val playlistsDeferred = async { app.dataStore.data.map { it[PLAYLISTS_KEY] ?: "" }.first() }
            
            val overridesStr = overridesDeferred.await()
            val overrideMap = parseOverrides(overridesStr)
            trackOverrides.clear()
            trackOverrides.putAll(overrideMap)

            val finalizedTracks = rawTracks.map { track ->
                overrideMap[track.id]?.let { ov ->
                    track.copy(
                        title = ov.first ?: track.title,
                        customArtworkUri = ov.second ?: track.customArtworkUri
                    )
                } ?: track
            }

            val playlistsStr = playlistsDeferred.await()
            val playlists = parsePlaylists(playlistsStr, finalizedTracks)

            withContext(Dispatchers.Main.immediate) {
                _uiState.update { it.copy(
                    playlist = finalizedTracks,
                    filteredPlaylist = finalizedTracks,
                    playlists = playlists,
                    isLoading = false
                ) }
            }
        }
    }

    private fun parseOverrides(str: String): Map<Long, Pair<String?, String?>> {
        if (str.isEmpty()) return emptyMap()
        return try {
            str.split(";;").associate {
                val parts = it.split("||")
                val id = parts[0].toLong()
                val title = if (parts[1] == "null") null else parts[1]
                val art = if (parts[2] == "null") null else parts[2]
                id to (title to art)
            }
        } catch (e: Exception) { emptyMap() }
    }

    private fun saveOverrides() {
        viewModelScope.launch {
            val str = trackOverrides.entries.joinToString(";;") { (id, pair) ->
                "${id}||${pair.first ?: "null"}||${pair.second ?: "null"}"
            }
            app.dataStore.edit { it[OVERRIDES_KEY] = str }
        }
    }

    private fun parsePlaylists(str: String, allTracks: List<Track>): List<Playlist> {
        return try {
            str.split(";;").map {
                val parts = it.split("||")
                val id = parts[0]
                val name = parts[1]
                val isDef = parts[2] == "true"
                val trackIds = parts[3].split(",").filter { it.isNotEmpty() }.map { it.toLong() }.toSet()
                // Format: id||name||isDefault||trackIds||customArtworkUri
                val artwork = if (parts.size > 4 && parts[4] != "null") parts[4] else null
                Playlist(id, name, allTracks.filter { track -> track.id in trackIds }, isDef, artwork)
            }
        } catch (e: Exception) {
            listOf(
                Playlist("pl_happy", "Happy", isDefault = true),
                Playlist("pl_sad", "Sad", isDefault = true),
                Playlist("pl_relaxed", "Relaxed", isDefault = true)
            )
        }
    }

    private fun savePlaylists() {
        viewModelScope.launch {
            val str = _uiState.value.playlists.joinToString(";;") { pl ->
                val ids = pl.tracks.joinToString(",") { it.id.toString() }
                // Format: id||name||isDefault||trackIds||customArtworkUri
                "${pl.id}||${pl.name}||${pl.isDefault}||$ids||${pl.customArtworkUri ?: "null"}"
            }
            app.dataStore.edit { it[PLAYLISTS_KEY] = str }
        }
    }

    // SURGICAL FIX: Copy artwork to internal storage to guarantee permanent access
    private fun saveArtworkToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = app.contentResolver.openInputStream(uri)
            val fileName = "artwork_${System.currentTimeMillis()}.jpg"
            val file = File(app.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Failed to save artwork: ${e.message}")
            null
        }
    }

    private fun setupMediaController(application: Application) {
        val sessionToken = SessionToken(application, ComponentName(application, MusicService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val mediaController = controller ?: return@addListener
            mediaController.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _uiState.update { it.copy(duration = mediaController.duration) }
                    }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val currentUri = mediaItem?.mediaId
                    val track = _uiState.value.playlist.find { it.data == currentUri }
                    
                    val newDirection = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) 1 else _uiState.value.skipDirection
                    
                    _uiState.update { it.copy(
                        currentTrack = track,
                        skipDirection = newDirection
                    ) }
                }
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _uiState.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
                }
                override fun onRepeatModeChanged(repeatMode: Int) {
                    _uiState.update { it.copy(repeatMode = repeatMode) }
                }
            })
            _uiState.update { it.copy(
                isPlaying = mediaController.isPlaying,
                shuffleModeEnabled = mediaController.shuffleModeEnabled,
                repeatMode = mediaController.repeatMode
            ) }
            if (mediaController.isPlaying) startProgressPolling()
            
            // CRITICAL: Sync all audio engine state from UI to service on connect
            syncAllAudioStateToService()
        }, MoreExecutors.directExecutor())
    }

    fun loadLocalMusic() {
        loadData()
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { state ->
            val sorted = sortTracks(state.playlist, order)
            state.copy(sortOrder = order, playlist = sorted, filteredPlaylist = filterTracks(sorted, state.searchQuery))
        }
    }

    private fun sortTracks(tracks: List<Track>, order: SortOrder): List<Track> {
        return when (order) {
            SortOrder.NAME -> tracks.sortedBy { it.title.lowercase() }
            SortOrder.LAST_ADDED -> tracks.sortedByDescending { it.dateAdded }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredPlaylist = filterTracks(state.playlist, query)
            )
        }
    }

    private fun filterTracks(tracks: List<Track>, query: String): List<Track> {
        if (query.isEmpty()) return tracks
        val lowerQuery = query.lowercase()
        return tracks.filter { 
            it.title.lowercase().contains(lowerQuery) || 
            it.artist.lowercase().contains(lowerQuery) 
        }
    }

    fun setSelectedPlaylist(playlistId: String?) {
        _uiState.update { it.copy(selectedPlaylistId = playlistId) }
    }

    fun playPlaylist(tracks: List<Track>, startIndex: Int, playlistId: String? = null) {
        val player = controller ?: return
        
        val mediaItems = tracks.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setArtworkUri(track.customArtworkUri?.let { Uri.parse(it) })
                .build()
            MediaItem.Builder()
                .setUri(track.data)
                .setMediaId(track.data)
                .setMediaMetadata(metadata)
                .build()
        }
        
        player.setMediaItems(mediaItems, startIndex, 0)
        player.prepare()
        player.play()
        
        _uiState.update { it.copy(
            currentTrack = tracks[startIndex], 
            isPlaying = true, 
            skipDirection = 1,
            playingPlaylistId = playlistId
        ) }
        startProgressPolling()
    }

    fun setTrack(track: Track) {
        // Fallback for single track play, using the global list as context
        val index = _uiState.value.playlist.indexOf(track)
        if (index != -1) {
            playPlaylist(_uiState.value.playlist, index, "all_tracks")
        } else {
            // If track not in list, just play it alone
            playPlaylist(listOf(track), 0, null)
        }
    }

    fun playFromSearch(query: String) {
        viewModelScope.launch {
            // Wait for controller to be ready
            while (controller == null || _uiState.value.isLoading) {
                delay(100)
            }

            val lowerQuery = query.lowercase().trim()
            val state = _uiState.value
            
            // 1. Check if user asked for a playlist by name
            val matchedPlaylist = state.playlists.find { 
                it.name.lowercase().contains(lowerQuery) || 
                lowerQuery.contains(it.name.lowercase()) 
            }
            
            val tracksToPlay = if (matchedPlaylist != null && matchedPlaylist.tracks.isNotEmpty()) {
                matchedPlaylist.tracks
            } else if (lowerQuery.isNotBlank()) {
                // 2. Otherwise search for songs/artists
                val matches = state.playlist.filter {
                    it.title.lowercase().contains(lowerQuery) ||
                    it.artist.lowercase().contains(lowerQuery)
                }
                if (matches.isNotEmpty()) matches else state.playlist
            } else {
                // 3. Play everything if no specific query
                state.playlist
            }
            
            if (tracksToPlay.isNotEmpty()) {
                playPlaylist(tracksToPlay, 0, matchedPlaylist?.id)
            }
        }
    }

    fun playAudioFromUri(uri: Uri) {
        viewModelScope.launch {
            // Wait for controller to be ready
            while (controller == null) {
                delay(100)
            }

            val existingTrack = _uiState.value.playlist.find { it.data == uri.toString() }
            if (existingTrack != null) {
                setTrack(existingTrack)
                return@launch
            }

            var title = "Unknown"
            val artist = "Unknown Artist"
            
            if (uri.scheme == "content") {
                try {
                    app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val titleIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (titleIndex != -1) {
                                title = cursor.getString(titleIndex) ?: "Unknown"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error fetching URI metadata", e)
                }
            } else if (uri.scheme == "file") {
                title = uri.lastPathSegment ?: "Unknown"
            }

            val tempTrack = Track(
                id = uri.hashCode().toLong(),
                title = title,
                artist = artist,
                data = uri.toString(),
                duration = "0:00"
            )
            
            setTrack(tempTrack)
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        val player = controller ?: return
        _uiState.update { it.copy(skipDirection = 1) }
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    fun skipPrevious() {
        val player = controller ?: return
        _uiState.update { it.copy(skipDirection = -1) }
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        }
    }

    fun seekTo(position: Float) {
        val player = controller ?: return
        val seekPos = (position * player.duration).toLong()
        player.seekTo(seekPos)
        _uiState.update { it.copy(progress = position, currentPosition = seekPos) }
    }

    fun onSeekStarted() { progressJob?.cancel() }
    fun onSeekFinished() { startProgressPolling() }

    fun toggleShuffle() {
        val player = controller ?: return
        val newValue = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newValue
        _uiState.update { it.copy(shuffleModeEnabled = newValue) }
    }

    fun toggleRepeat() {
        val player = controller ?: return
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _uiState.update { it.copy(repeatMode = nextMode) }
    }

    fun toggleShuffleMode() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun toggle16D() {
        val newState = !_uiState.value.is16DEnabled
        _uiState.update { it.copy(is16DEnabled = newState) }
        
        val args = Bundle().apply { putBoolean("enabled", newState) }
        controller?.sendCustomCommand(SessionCommand("TOGGLE_16D", Bundle.EMPTY), args)
    }

    private var isUiVisible = true

    fun setUiVisible(visible: Boolean) {
        isUiVisible = visible
        if (visible && controller?.isPlaying == true) {
            startProgressPolling()
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var tick = 0f
            while (true) {
                val player = controller
                
                if (!isUiVisible) {
                    // Battery saving: slow down updates significantly when UI is not visible
                    delay(500)
                    if (player == null || !player.isPlaying) break
                    continue
                }

                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(1)
                    
                    // Generate fluid visualizer data at high frequency
                    tick += 0.04f // Adjusted for 8ms polling (was 0.25 for 50ms)
                    val bands = _uiState.value.eqBands
                    val newData = List(20) { i ->
                        val eqImpact = bands[i % bands.size]
                        (eqImpact * 0.4f + kotlin.math.sin(tick + i) * 0.3f + 0.3f).coerceIn(0.1f, 1.0f)
                    }

                    _uiState.update { it.copy(
                        progress = pos.toFloat() / dur.toFloat(),
                        currentPosition = pos,
                        duration = dur,
                        visualizerData = newData
                    ) }
                    delay(8) // Support 120Hz refresh rate
                } else {
                    if (_uiState.value.visualizerData.any { it > 0.11f }) {
                        _uiState.update { state ->
                            state.copy(visualizerData = state.visualizerData.map { (it * 0.85f).coerceAtLeast(0.1f) })
                        }
                        delay(16) // Smooth decay for visualizer bars
                    } else {
                        delay(250) // Idle polling
                    }
                }
            }
        }
    }

    fun updateEqBand(index: Int, value: Float) {
        _uiState.update { state ->
            val newBands = state.eqBands.toMutableList()
            if (index in newBands.indices) {
                newBands[index] = value
                
                // --- SYNC WITH AUDIO ENGINE ---
                sendEqUpdateToService(index, value)
            }
            state.copy(eqBands = newBands, selectedPreset = "Custom")
        }
    }

    fun applyPreset(name: String) {
        _uiState.update { state ->
            val preset = state.eqPresets.find { it.name == name }
            if (preset != null) {
                preset.bands.forEachIndexed { index, value ->
                    sendEqUpdateToService(index, value)
                }
                state.copy(eqBands = preset.bands, selectedPreset = name)
            } else state
        }
    }

    fun setMasterVolume(value: Float) {
        val player = controller ?: return
        player.volume = value
        _uiState.update { it.copy(masterVolume = value) }
    }

    fun updateTitanParams(
        clarity: Float = _uiState.value.clarityLevel,
        snappiness: Float = _uiState.value.snappiness,
        soundstage: Float = _uiState.value.soundstageWidth
    ) {
        _uiState.update { it.copy(
            clarityLevel = clarity,
            snappiness = snappiness,
            soundstageWidth = soundstage
        ) }
        val args = Bundle().apply {
            putFloat("clarity", clarity)
            putFloat("snappiness", snappiness)
            putFloat("soundstage", soundstage)
        }
        controller?.sendCustomCommand(SessionCommand("UPDATE_TITAN_PARAMS", Bundle.EMPTY), args)
    }

    fun setTempo(tempo: Float) {
        _uiState.update { it.copy(tempo = tempo) }
        val args = Bundle().apply { putFloat("tempo", tempo) }
        controller?.sendCustomCommand(SessionCommand("UPDATE_TEMPO", Bundle.EMPTY), args)
    }

    fun resetStudioEngine() {
        // Reset EQ bands to flat (0.5 = 0dB)
        val flatBands = listOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        _uiState.update { it.copy(
            eqBands = flatBands,
            selectedPreset = "Flat",
            clarityLevel = 0.5f,
            snappiness = 0.5f,
            soundstageWidth = 1.0f,
            tempo = 1.0f,
            masterVolume = 1.0f
        ) }
        // Send all flat EQ bands to service
        flatBands.forEachIndexed { index, value ->
            sendEqUpdateToService(index, value)
        }
        // Reset Titan params
        val titanArgs = Bundle().apply {
            putFloat("clarity", 0.5f)
            putFloat("snappiness", 0.5f)
            putFloat("soundstage", 1.0f)
        }
        controller?.sendCustomCommand(SessionCommand("UPDATE_TITAN_PARAMS", Bundle.EMPTY), titanArgs)
        // Reset tempo
        val tempoArgs = Bundle().apply { putFloat("tempo", 1.0f) }
        controller?.sendCustomCommand(SessionCommand("UPDATE_TEMPO", Bundle.EMPTY), tempoArgs)
        // Reset volume
        controller?.volume = 1.0f
    }

    fun updateUserName(name: String) {
        PreferenceManager.setUserName(app, name)
        _uiState.update { it.copy(userName = name) }
    }

    fun completeOnboarding() {
        PreferenceManager.setFirstLaunchCompleted(app)
        _uiState.update { it.copy(isOnboardingRequired = false) }
    }

    private fun sendEqUpdateToService(index: Int, value: Float) {
        val mediaController = controller ?: return
        // Map 0..1 to -24..24 dB, but send as scale factor * 100 for short transmission
        val gainDb = (value - 0.5f) * 48.0f
        val level = (gainDb * 100).toInt().toShort()
        val args = Bundle().apply {
            putInt("band_index", index)
            putShort("level", level)
        }
        mediaController.sendCustomCommand(SessionCommand("UPDATE_EQ", Bundle.EMPTY), args)
    }

    /**
     * Pushes ALL current UI audio state to the MusicService.
     * Called once when the MediaController first connects, ensuring the service
     * engine parameters match the UI state from the very start.
     */
    private fun syncAllAudioStateToService() {
        val state = _uiState.value
        
        // 1. Sync 16D toggle
        val toggle16DArgs = Bundle().apply { putBoolean("enabled", state.is16DEnabled) }
        controller?.sendCustomCommand(SessionCommand("TOGGLE_16D", Bundle.EMPTY), toggle16DArgs)
        
        // 2. Sync all EQ bands
        state.eqBands.forEachIndexed { index, value ->
            sendEqUpdateToService(index, value)
        }
        
        // 3. Sync Titan params (clarity, snappiness, soundstage)
        val titanArgs = Bundle().apply {
            putFloat("clarity", state.clarityLevel)
            putFloat("snappiness", state.snappiness)
            putFloat("soundstage", state.soundstageWidth)
        }
        controller?.sendCustomCommand(SessionCommand("UPDATE_TITAN_PARAMS", Bundle.EMPTY), titanArgs)
        
        // 4. Sync tempo
        val tempoArgs = Bundle().apply { putFloat("tempo", state.tempo) }
        controller?.sendCustomCommand(SessionCommand("UPDATE_TEMPO", Bundle.EMPTY), tempoArgs)
        
        // 5. Sync master volume
        controller?.volume = state.masterVolume
    }

    fun saveCustomPreset(name: String) {
        _uiState.update { state ->
            val newPreset = EqPreset(name, state.eqBands.toList())
            state.copy(
                eqPresets = state.eqPresets + newPreset,
                selectedPreset = name
            )
        }
    }

    fun deletePreset(name: String) {
        _uiState.update { state ->
            // Don't delete built-in presets (just a safety check)
            val builtIn = listOf("Flat", "Bass Boost", "Vocals", "High Hat", "Cinema")
            if (name in builtIn) return@update state
            
            val newPresets = state.eqPresets.filter { it.name != name }
            state.copy(
                eqPresets = newPresets,
                selectedPreset = if (state.selectedPreset == name) "Flat" else state.selectedPreset
            )
        }
    }

    fun updateTrackArtwork(track: Track, uri: String) {
        viewModelScope.launch {
            val internalUri = saveArtworkToInternalStorage(Uri.parse(uri)) ?: uri
            
            _uiState.update { state ->
                val updatedMainList = state.playlist.map {
                    if (it.id == track.id) it.copy(customArtworkUri = internalUri) else it
                }
                val updatedFilteredList = state.filteredPlaylist.map {
                    if (it.id == track.id) it.copy(customArtworkUri = internalUri) else it
                }
                val updatedPlaylists = state.playlists.map { playlist ->
                    val updatedTracksInPlaylist = playlist.tracks.map {
                        if (it.id == track.id) it.copy(customArtworkUri = internalUri) else it
                    }
                    playlist.copy(tracks = updatedTracksInPlaylist)
                }
                val updatedCurrentTrack = if (state.currentTrack?.id == track.id) {
                    state.currentTrack.copy(customArtworkUri = internalUri)
                } else {
                    state.currentTrack
                }
                
                // Record modification
                trackOverrides[track.id] = (trackOverrides[track.id]?.first ?: track.title) to internalUri
                
                state.copy(
                    playlist = updatedMainList,
                    filteredPlaylist = updatedFilteredList,
                    playlists = updatedPlaylists,
                    currentTrack = updatedCurrentTrack
                )
            }
            saveOverrides()
            savePlaylists()
        }
    }

    fun renameTrack(track: Track, newTitle: String) {
        _uiState.update { state ->
            val updatedMainList = state.playlist.map {
                if (it.id == track.id) it.copy(title = newTitle) else it
            }
            val updatedFilteredList = state.filteredPlaylist.map {
                if (it.id == track.id) it.copy(title = newTitle) else it
            }
            val updatedPlaylists = state.playlists.map { playlist ->
                val updatedTracksInPlaylist = playlist.tracks.map {
                    if (it.id == track.id) it.copy(title = newTitle) else it
                }
                playlist.copy(tracks = updatedTracksInPlaylist)
            }
            val updatedCurrentTrack = if (state.currentTrack?.id == track.id) {
                state.currentTrack.copy(title = newTitle)
            } else {
                state.currentTrack
            }
            
            // Record modification
            trackOverrides[track.id] = newTitle to (trackOverrides[track.id]?.second ?: track.customArtworkUri)
            
            state.copy(
                playlist = updatedMainList,
                filteredPlaylist = updatedFilteredList,
                playlists = updatedPlaylists,
                currentTrack = updatedCurrentTrack
            )
        }
        saveOverrides()
        savePlaylists()
    }

    // NEW: Function to set/edit playlist artwork
    fun updatePlaylistArtwork(playlistId: String, uri: String) {
        viewModelScope.launch {
            val internalUri = saveArtworkToInternalStorage(Uri.parse(uri)) ?: uri
            _uiState.update { state ->
                val updatedPlaylists = state.playlists.map { 
                    if (it.id == playlistId) it.copy(customArtworkUri = internalUri) else it 
                }
                state.copy(playlists = updatedPlaylists)
            }
            savePlaylists()
        }
    }

    fun importFiles(uris: List<Uri>, resolver: ContentResolver) {}

    fun deleteTrack(track: Track) {
        _uiState.update { state ->
            val newList = state.playlist.filter { it.id != track.id }
            state.copy(
                playlist = newList,
                filteredPlaylist = filterTracks(newList, state.searchQuery)
            )
        }
        trackOverrides.remove(track.id)
        saveOverrides()
    }

    fun createPlaylist(name: String) {
        _uiState.update { state ->
            val newPlaylist = Playlist(id = "pl_${System.currentTimeMillis()}", name = name)
            state.copy(playlists = state.playlists + newPlaylist)
        }
        savePlaylists()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        _uiState.update { state ->
            val updated = state.playlists.map { 
                if (it.id == playlistId) it.copy(name = newName) else it 
            }
            state.copy(playlists = updated)
        }
        savePlaylists()
    }

    fun deletePlaylist(playlistId: String) {
        _uiState.update { state ->
            val pl = state.playlists.find { it.id == playlistId }
            if (pl?.isDefault == true) return@update state
            state.copy(playlists = state.playlists.filter { it.id != playlistId })
        }
        savePlaylists()
    }

    fun addTrackToPlaylist(track: Track, playlistId: String) {
        _uiState.update { state ->
            val updatedPlaylists = state.playlists.map { pl ->
                if (pl.id == playlistId && !pl.tracks.any { it.id == track.id }) {
                    pl.copy(tracks = pl.tracks + track)
                } else pl
            }
            state.copy(playlists = updatedPlaylists)
        }
        savePlaylists()
    }

    fun removeTrackFromPlaylist(track: Track, playlistId: String) {
        _uiState.update { state ->
            val updatedPlaylists = state.playlists.map { pl ->
                if (pl.id == playlistId) {
                    val newList = pl.tracks.filter { it.id != track.id }
                    pl.copy(tracks = newList)
                } else pl
            }
            state.copy(playlists = updatedPlaylists)
        }
        savePlaylists()
    }

    fun moveTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val updatedPlaylists = state.playlists.map { playlist ->
                if (playlist.id == playlistId) {
                    val mutableTracks = playlist.tracks.toMutableList()
                    if (fromIndex in mutableTracks.indices && toIndex in mutableTracks.indices) {
                        val track = mutableTracks.removeAt(fromIndex)
                        mutableTracks.add(toIndex, track)
                    }
                    playlist.copy(tracks = mutableTracks)
                } else playlist
            }
            state.copy(playlists = updatedPlaylists)
        }
        
        // SYNC PLAYER QUEUE
        if (playlistId == _uiState.value.playingPlaylistId) {
            controller?.moveMediaItem(fromIndex, toIndex)
        }
        
        savePlaylists()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        progressJob?.cancel()
    }
}
