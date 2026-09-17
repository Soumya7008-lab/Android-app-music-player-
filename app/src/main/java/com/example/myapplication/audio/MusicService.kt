package com.example.myapplication.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.myapplication.audio.processors.SpatialAudioProcessor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.myapplication.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

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

        // BUILD THE AUDIO SINK DIRECTLY with our processor in the chain
        // This avoids any issues with buildAudioSink override signature changes across Media3 versions.
        val audioSink = DefaultAudioSink.Builder(this)
            .setAudioProcessors(arrayOf(spatialAudioProcessor))
            .build()
        
        Log.i(TAG, "AudioSink created with SpatialAudioProcessor in processing chain")

        // Use a custom RenderersFactory that returns our pre-built audio sink
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                Log.i(TAG, "buildAudioSink(3-param) called — returning custom sink with processor")
                return audioSink
            }
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()

        Log.i(TAG, "ExoPlayer created with custom renderersFactory")

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
                    Log.d(TAG, "Titan params: clarity=$titanClarity snap=$titanSnappiness stage=$titanSoundstage")
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
                    Log.i(TAG, "16D spatial audio: $enabled")
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
