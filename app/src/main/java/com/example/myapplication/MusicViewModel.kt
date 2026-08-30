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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.myapplication.audio.MusicService
import com.example.myapplication.data.MediaScanner
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private val Context.dataStore by preferencesDataStore(name = "music_prefs")

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
    val selectedPlaylistId: String? = null
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
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
        setupMediaController(application)
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val rawTracks = mediaScanner.scanAudioFiles()
            
            // 1. Load Overrides from DataStore
            val overridesStr = app.dataStore.data.map { it[OVERRIDES_KEY] ?: "" }.first()
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

            // 2. Load Playlists from DataStore
            val playlistsStr = app.dataStore.data.map { it[PLAYLISTS_KEY] ?: "" }.first()
            val playlists = if (playlistsStr.isEmpty()) {
                listOf(
                    Playlist("pl_happy", "Happy", isDefault = true),
                    Playlist("pl_sad", "Sad", isDefault = true),
                    Playlist("pl_relaxed", "Relaxed", isDefault = true)
                )
            } else {
                parsePlaylists(playlistsStr, finalizedTracks)
            }

            _uiState.update { state ->
                val sorted = sortTracks(finalizedTracks, state.sortOrder)
                state.copy(
                    playlist = sorted,
                    filteredPlaylist = filterTracks(sorted, state.searchQuery),
                    playlists = playlists,
                    isLoading = false
                )
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
                    _uiState.update { it.copy(currentTrack = track) }
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

    fun setTrack(track: Track) {
        val player = controller ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(track.customArtworkUri?.let { Uri.parse(it) })
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(track.data)
            .setMediaId(track.data)
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        _uiState.update { it.copy(currentTrack = track, isPlaying = true, skipDirection = 1) }
        startProgressPolling()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        val current = _uiState.value.currentTrack ?: return
        val index = _uiState.value.playlist.indexOf(current)
        if (index != -1 && index < _uiState.value.playlist.size - 1) {
            _uiState.update { it.copy(skipDirection = 1) }
            setTrack(_uiState.value.playlist[index + 1])
        }
    }

    fun skipPrevious() {
        val current = _uiState.value.currentTrack ?: return
        val index = _uiState.value.playlist.indexOf(current)
        if (index > 0) {
            _uiState.update { it.copy(skipDirection = -1) }
            setTrack(_uiState.value.playlist[index - 1])
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

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val player = controller
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(1)
                    _uiState.update { it.copy(
                        progress = pos.toFloat() / dur.toFloat(),
                        currentPosition = pos,
                        duration = dur
                    ) }
                }
                delay(100)
            }
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
        savePlaylists()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        progressJob?.cancel()
    }
}
