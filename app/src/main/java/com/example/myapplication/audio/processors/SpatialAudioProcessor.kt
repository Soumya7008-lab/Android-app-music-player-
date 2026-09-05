package com.example.myapplication.audio.processors

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {
    private var isEnabled = false

    companion object {
        init {
            System.loadLibrary("spatializer")
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isEnabled || inputAudioFormat.channelCount != 2) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }

        val remaining = inputBuffer.remaining()
        val buffer = replaceOutputBuffer(remaining)

        // Pass direct buffer to JNI
        nativeProcess(inputBuffer, remaining, inputAudioFormat.sampleRate.toFloat())
        
        buffer.put(inputBuffer)
        buffer.flip()
    }

    private external fun nativeProcess(buffer: ByteBuffer, length: Int, sampleRate: Float)
}
