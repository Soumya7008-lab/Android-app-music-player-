package com.example.myapplication.audio.processors

import kotlin.math.*

/**
 * HIGH-FIDELITY PURE KOTLIN AUDIO ENGINE
 * Replaces the complex C++ engine for 100% stability and zero jitter.
 */
class TitanKotlinEngine {
    private var sampleRate = 44100f
    
    // Biquad State
    private class Biquad {
        var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f

        fun setPeaking(freq: Float, sr: Float, Q: Float, gainDb: Float) {
            val A = 10f.pow(gainDb / 40f)
            val w0 = 2f * PI.toFloat() * freq / sr
            val alpha = sin(w0) / (2f * Q)
            val cosW0 = cos(w0)
            val a0 = 1f + alpha / A
            b0 = (1f + alpha * A) / a0
            b1 = (-2f * cosW0) / a0
            b2 = (1f - alpha * A) / a0
            a1 = (-2f * cosW0) / a0
            a2 = (1f - alpha / A) / a0
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    private val filtersL = Array(5) { Biquad() }
    private val filtersR = Array(5) { Biquad() }
    private val frequencies = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

    // Spatial State
    private var angle = 0f
    private val bufferSize = 8192
    private val delayL = FloatArray(bufferSize)
    private val delayR = FloatArray(bufferSize)
    private var writeIndex = 0

    // Params
    var rotationSpeed = 1.2f
    var clarityLevel = 0.5f
    var snappiness = 0.5f
    var soundstageWidth = 1.0f
    var spatialEnabled = false

    private var prevEnv = 0f

    fun setup(sr: Float) {
        this.sampleRate = sr
        updateEq(floatArrayOf(0f, 0f, 0f, 0f, 0f))
    }

    fun updateEq(gains: FloatArray) {
        for (i in 0 until 5) {
            val g = if (i < gains.size) gains[i] else 0f
            filtersL[i].setPeaking(frequencies[i], sampleRate, 1.0f, g)
            filtersR[i].setPeaking(frequencies[i], sampleRate, 1.0f, g)
        }
    }

    private fun readHermite(buf: FloatArray, delaySamples: Float): Float {
        var readPos = writeIndex - delaySamples
        while (readPos < 0) readPos += bufferSize
        val i1 = readPos.toInt() % bufferSize
        val f = readPos - i1
        val i0 = (i1 - 1 + bufferSize) % bufferSize
        val i2 = (i1 + 1) % bufferSize
        val i3 = (i1 + 2) % bufferSize
        
        val y0 = buf[i0]; val y1 = buf[i1]; val y2 = buf[i2]; val y3 = buf[i3]
        val a = (3f * (y1 - y2) - y0 + y3) * 0.5f
        val b = 2f * y2 + y0 - 5f * y1 * 0.5f - y3 * 0.5f
        val c = (y2 - y0) * 0.5f
        return (((a * f) + b) * f + c) * f + y1
    }

    fun processFrame(lIn: Float, rIn: Float): Pair<Float, Float> {
        var l = lIn
        var r = rIn

        // 1. EQ
        for (i in 0 until 5) {
            l = filtersL[i].process(l)
            r = filtersR[i].process(r)
        }

        // 2. Dynamics (Transient)
        val mono = (abs(l) + abs(r)) * 0.5f
        val attack = 0.01f + (snappiness * 0.1f)
        val env = if (mono > prevEnv) prevEnv + attack * (mono - prevEnv) else prevEnv + 0.001f * (mono - prevEnv)
        prevEnv = env
        val tGain = 1.0f + (mono - env) * snappiness * 2.0f
        l *= tGain; r *= tGain

        // 3. Spatial
        delayL[writeIndex] = l
        delayR[writeIndex] = r
        if (spatialEnabled) {
            val s = sin(angle); val c = cos(angle)
            val gL = 0.5f - (s * 0.45f); val gR = 0.5f + (s * 0.45f)
            val maxD = (0.7f / 1000f) * sampleRate
            l = readHermite(delayL, gR * maxD) * gL * (0.85f + c * 0.15f)
            r = readHermite(delayR, gL * maxD) * gR * (0.85f + c * 0.15f)
            angle += rotationSpeed / sampleRate
            if (angle > 2f * PI) angle -= 2f * PI.toFloat()
        }
        writeIndex = (writeIndex + 1) % bufferSize

        // 4. M/S Width
        val mid = (l + r) * 0.5f
        val side = (l - r) * 0.5f * soundstageWidth
        l = mid + side; r = mid - side

        // 5. Exciter & Limiter
        fun finalize(v: Float): Float {
            val harmonics = v * v * (if (v > 0) 1f else -1f)
            val out = v + (harmonics * clarityLevel * 0.2f)
            return (out * (1.5f - 0.5f * out * out)).coerceIn(-1f, 1f)
        }

        return finalize(l) to finalize(r)
    }
}
