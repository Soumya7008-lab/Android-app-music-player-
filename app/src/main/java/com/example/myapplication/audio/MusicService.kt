package com.example.myapplication.audio

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.example.myapplication.audio.processors.SpatialAudioProcessor
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.myapplication.Track
import com.example.myapplication.Playlist
import com.example.myapplication.dataStore
import com.example.myapplication.data.MediaScanner
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.LibraryResult
import com.google.common.collect.ImmutableList
import com.example.myapplication.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

/**
 * MUSIC SERVICE — BULLETPROOF AUDIO PIPELINE
 *
 * Instead of relying on DefaultRenderersFactory.buildAudioSink() (which may not
 * be called on all Media3 versions), we create MediaCodecAudioRenderer DIRECTLY
 * with our custom DefaultAudioSink that contains the SpatialAudioProcessor.
 *
 * Pipeline: Decoder → SpatialAudioProcessor (EQ + 16D + Dynamics) → AudioTrack
 */
@UnstableApi
class MusicService : MediaLibraryService() {

    companion object {
        private const val TAG = "MusicService"
        private const val ROOT_ID = "root_id"
    }

    private var mediaSession: MediaLibrarySession? = null
    private val spatialAudioProcessor = SpatialAudioProcessor()

    // TITAN ENGINE PARAMETERS (defaults: flat EQ, moderate settings)
    private var titanClarity = 0.5f
    private var titanSnappiness = 0.5f
    private var titanSoundstage = 1.0f
    private var titanRotationSpeed = 1.0f
    private var titanEqGains = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var mediaScanner: MediaScanner

    override fun onCreate() {
        super.onCreate()
        
        mediaScanner = MediaScanner(this)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // STEP 1: Build AudioSink with our processor DIRECTLY in the chain
        val audioSink = DefaultAudioSink.Builder(this)
            .setAudioProcessors(arrayOf(spatialAudioProcessor))
            .build()

        Log.i(TAG, "✅ AudioSink created with SpatialAudioProcessor")

        // STEP 2: Create a RenderersFactory that builds MediaCodecAudioRenderer DIRECTLY
        // This bypasses DefaultRenderersFactory entirely — no more relying on buildAudioSink overrides
        val renderersFactory = RenderersFactory { handler: Handler,
                                                  _: VideoRendererEventListener,
                                                  audioRendererEventListener: AudioRendererEventListener,
                                                  _: TextOutput,
                                                  _: MetadataOutput ->
            Log.i(TAG, "✅ RenderersFactory.createRenderers() called — building MediaCodecAudioRenderer with custom AudioSink")
            arrayOf<Renderer>(
                MediaCodecAudioRenderer(
                    this@MusicService,
                    MediaCodecSelector.DEFAULT,
                    handler,
                    audioRendererEventListener,
                    audioSink
                )
            )
        }

        // STEP 3: Build ExoPlayer with our custom renderers factory
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()

        Log.i(TAG, "✅ ExoPlayer created with direct MediaCodecAudioRenderer pipeline")

        syncTitanParams()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, player, CustomMediaSessionCallback())
            .setSessionActivity(pendingIntent)
            .build()
    }

    private fun syncTitanParams() {
        spatialAudioProcessor.setParams(
            titanRotationSpeed, titanClarity, titanSnappiness, titanSoundstage, titanEqGains
        )
    }

    private inner class CustomMediaSessionCallback : MediaLibrarySession.Callback {
        
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // CRITICAL: Whitelist ALL custom commands.
            // Without this, Media3 blocks every sendCustomCommand() from the MediaController.
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand("UPDATE_EQ", Bundle.EMPTY))
                .add(SessionCommand("TOGGLE_16D", Bundle.EMPTY))
                .add(SessionCommand("UPDATE_TITAN_PARAMS", Bundle.EMPTY))
                .add(SessionCommand("UPDATE_TEMPO", Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Google Assistant and Android Auto require a valid root to browse
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setTitle("My Music")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, null))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                val (tracks, _) = getFinalizedTracks()
                val items = tracks.map { track ->
                    MediaItem.Builder()
                        .setUri(track.data)
                        .setMediaId(track.data)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setIsPlayable(true)
                                .build()
                        )
                        .build()
                }
                LibraryResult.ofItemList(items, null)
            }
        }
        
        private suspend fun getFinalizedTracks(): Pair<List<Track>, List<Playlist>> {
            val rawTracks = mediaScanner.scanAudioFiles()
            
            val overridesStr = dataStore.data.map { it[stringPreferencesKey("track_overrides")] ?: "" }.first()
            val overrideMap = if (overridesStr.isEmpty()) emptyMap() else {
                try {
                    overridesStr.split(";;").associate {
                        val parts = it.split("||")
                        parts[0].toLong() to (if (parts[1] == "null") null else parts[1] to if (parts[2] == "null") null else parts[2])
                    }
                } catch (e: Exception) { emptyMap() }
            }

            val finalizedTracks = rawTracks.map { track ->
                overrideMap[track.id]?.let { ov ->
                    track.copy(
                        title = ov.first ?: track.title,
                        customArtworkUri = ov.second ?: track.customArtworkUri
                    )
                } ?: track
            }

            val playlistsStr = dataStore.data.map { it[stringPreferencesKey("playlists_data")] ?: "" }.first()
            val playlists = try {
                if (playlistsStr.isEmpty()) emptyList()
                else playlistsStr.split(";;").map {
                    val parts = it.split("||")
                    val id = parts[0]
                    val name = parts[1]
                    val isDef = parts[2] == "true"
                    val trackIds = parts[3].split(",").filter { it.isNotEmpty() }.map { it.toLong() }.toSet()
                    val artwork = if (parts.size > 4 && parts[4] != "null") parts[4] else null
                    Playlist(id, name, finalizedTracks.filter { track -> track.id in trackIds }, isDef, artwork)
                }
            } catch (e: Exception) { emptyList() }

            return finalizedTracks to playlists
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Check if this is a voice search from Google Assistant
            val isVoiceSearch = mediaItems.size == 1 && mediaItems[0].requestMetadata.searchQuery != null
            if (isVoiceSearch) {
                val query = mediaItems[0].requestMetadata.searchQuery!!
                Log.i(TAG, "🎤 Voice Search Query: $query")

                return serviceScope.future {
                    val (tracks, playlists) = getFinalizedTracks()
                    val lowerQuery = query.lowercase().trim()
                    
                    // 1. Check if user asked for a playlist by name
                    val matchedPlaylist = playlists.find { 
                        it.name.lowercase().contains(lowerQuery) || 
                        lowerQuery.contains(it.name.lowercase()) 
                    }
                    
                    val tracksToPlay = if (matchedPlaylist != null && matchedPlaylist.tracks.isNotEmpty()) {
                        Log.i(TAG, "🎤 Voice Search matched playlist: ${matchedPlaylist.name}")
                        matchedPlaylist.tracks
                    } else if (lowerQuery.isNotBlank()) {
                        // 2. Otherwise search for songs/artists
                        val matches = tracks.filter {
                            it.title.lowercase().contains(lowerQuery) ||
                            it.artist.lowercase().contains(lowerQuery)
                        }
                        if (matches.isNotEmpty()) matches else tracks
                    } else {
                        // 3. Play everything if no specific query
                        tracks
                    }
                    
                    val items = tracksToPlay.map { track ->
                        val metadata = MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtworkUri(track.customArtworkUri?.let { Uri.parse(it) })
                            .setIsPlayable(true)
                            .build()
                        MediaItem.Builder()
                            .setUri(track.data)
                            .setMediaId(track.data)
                            .setMediaMetadata(metadata)
                            .build()
                    }.toMutableList()
                    
                    items
                }
            }
            return Futures.immediateFuture(mediaItems)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return serviceScope.future {
                val tracks = mediaScanner.scanAudioFiles()
                if (tracks.isNotEmpty()) {
                    val track = tracks.first()
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setIsPlayable(true)
                        .build()
                    val mediaItem = MediaItem.Builder()
                        .setUri(track.data)
                        .setMediaId(track.data)
                        .setMediaMetadata(metadata)
                        .build()
                    
                    MediaSession.MediaItemsWithStartPosition(
                        listOf(mediaItem), 0, 0
                    )
                } else {
                    throw UnsupportedOperationException("No media available to resume")
                }
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "UPDATE_EQ" -> {
                    val bandIndex = args.getInt("band_index")
                    val level = args.getShort("level").toFloat() / 100f // Decode dB value
                    if (bandIndex in titanEqGains.indices) {
                        titanEqGains[bandIndex] = level
                        syncTitanParams()
                        Log.d(TAG, "EQ band $bandIndex → ${level}dB")
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "UPDATE_TITAN_PARAMS" -> {
                    titanClarity = args.getFloat("clarity", titanClarity)
                    titanSnappiness = args.getFloat("snappiness", titanSnappiness)
                    titanSoundstage = args.getFloat("soundstage", titanSoundstage)
                    syncTitanParams()
                    Log.d(TAG, "Titan: clarity=$titanClarity snap=$titanSnappiness stage=$titanSoundstage")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "UPDATE_TEMPO" -> {
                    val tempo = args.getFloat("tempo", 1.0f)
                    mediaSession?.player?.playbackParameters = androidx.media3.common.PlaybackParameters(tempo)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "TOGGLE_16D" -> {
                    val enabled = args.getBoolean("enabled")
                    spatialAudioProcessor.setEnabled(enabled)
                    syncTitanParams()
                    Log.i(TAG, "🔊 16D spatial audio: ${if (enabled) "ON" else "OFF"}")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0 || player?.playbackState == ExoPlayer.STATE_IDLE) {
            stopSelf()
        }
    }
}
