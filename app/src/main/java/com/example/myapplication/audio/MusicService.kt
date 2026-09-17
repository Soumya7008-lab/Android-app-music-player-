package com.example.myapplication.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.myapplication.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

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
class MusicService : MediaSessionService() {

    companion object {
        private const val TAG = "MusicService"
    }

    private var mediaSession: MediaSession? = null
    private val spatialAudioProcessor = SpatialAudioProcessor()

    // TITAN ENGINE PARAMETERS (defaults: flat EQ, moderate settings)
    private var titanClarity = 0.5f
    private var titanSnappiness = 0.5f
    private var titanSoundstage = 1.0f
    private var titanRotationSpeed = 7.0f
    private var titanEqGains = floatArrayOf(0f, 0f, 0f, 0f, 0f)

    override fun onCreate() {
        super.onCreate()

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

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(CustomMediaSessionCallback())
            .build()
    }

    private fun syncTitanParams() {
        spatialAudioProcessor.setParams(
            titanRotationSpeed, titanClarity, titanSnappiness, titanSoundstage, titanEqGains
        )
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

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
