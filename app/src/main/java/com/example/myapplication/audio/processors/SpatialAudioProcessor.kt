package com.example.myapplication.audio.processors

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SPATIAL AUDIO PROCESSOR
 * 
 * Intercepts ExoPlayer's decoded PCM audio stream and routes it through the
 * TitanKotlinEngine for EQ, 16D spatial rotation, clarity enhancement, and dynamics.
 *
 * Supports BOTH PCM_16BIT and PCM_FLOAT input formats (critical for Media3 1.11+).
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val TAG = "SpatialAudioProcessor"
    }

    private val engine = TitanKotlinEngine()
    private var isFloat = false
    private var configured = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Accept BOTH 16-bit integer PCM and 32-bit float PCM
        // ExoPlayer's decoder output format depends on codec, device, and sink settings.
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                isFloat = false
                configured = true
                engine.setup(inputAudioFormat.sampleRate.toFloat())
                Log.i(TAG, "Configured: PCM_16BIT, ${inputAudioFormat.sampleRate}Hz, ${inputAudioFormat.channelCount}ch")
                inputAudioFormat // Output same format as input
            }
            C.ENCODING_PCM_FLOAT -> {
                isFloat = true
                configured = true
                engine.setup(inputAudioFormat.sampleRate.toFloat())
                Log.i(TAG, "Configured: PCM_FLOAT, ${inputAudioFormat.sampleRate}Hz, ${inputAudioFormat.channelCount}ch")
                inputAudioFormat // Output same format as input
            }
            else -> {
                configured = false
                Log.w(TAG, "Unsupported encoding: ${inputAudioFormat.encoding} — processor inactive, audio will pass through unprocessed")
                AudioFormat.NOT_SET // Gracefully go inactive — audio plays unprocessed
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        engine.spatialEnabled = enabled
        Log.d(TAG, "16D spatial audio ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun setParams(speed: Float, clarity: Float, snap: Float, width: Float, eq: FloatArray) {
        engine.rotationSpeed = speed
        engine.clarityLevel = clarity
        engine.snappiness = snap
        engine.soundstageWidth = width
        engine.updateEq(eq)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        if (isFloat) {
            // ---- 32-BIT FLOAT PATH ----
            // Each sample is 4 bytes. Stereo frame = 8 bytes.
            val bytesPerSample = 4
            val frameSize = channels * bytesPerSample

            while (inputBuffer.remaining() >= frameSize) {
                val l = inputBuffer.float
                val r = if (channels > 1) inputBuffer.float else l

                val out = engine.process(l, r)

                buffer.putFloat(out[0])
                if (channels > 1) {
                    buffer.putFloat(out[1])
                }
            }
        } else {
            // ---- 16-BIT INTEGER PATH ----
            // Each sample is 2 bytes. Stereo frame = 4 bytes.
            val bytesPerSample = 2
            val frameSize = channels * bytesPerSample

            while (inputBuffer.remaining() >= frameSize) {
                val lShort = inputBuffer.short
                val rShort = if (channels > 1) inputBuffer.short else lShort

                val l = lShort.toFloat() / 32768.0f
                val r = rShort.toFloat() / 32768.0f

                val out = engine.process(l, r)

                buffer.putShort((out[0] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort())
                if (channels > 1) {
                    buffer.putShort((out[1] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }

        buffer.flip()
    }
}
