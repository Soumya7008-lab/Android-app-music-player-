package com.example.myapplication.audio

import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

class AudioEffectsManager(audioSessionId: Int) {
    private var equalizer: Equalizer? = null
    private var virtualizer: Virtualizer? = null

    init {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                if (strengthSupported) {
                    setStrength(1000.toShort()) // Max strength for surround effect
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed to initialize effects", e)
        }
    }

    fun setEqualizerBand(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }

    fun setSurroundStrength(strength: Short) {
        if (virtualizer?.strengthSupported == true) {
            virtualizer?.setStrength(strength)
        }
    }

    fun release() {
        equalizer?.release()
        virtualizer?.release()
    }
}
