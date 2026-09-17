package com.example.myapplication.audio.processors

import kotlin.math.*

/**
 * CRYSTAL CLEAR TITAN AUDIO ENGINE
 * Optimized for pure High-Res transparency and crispness.
 */
class TitanKotlinEngine {
    private var sampleRate = 44100f
    
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
            return if (y.isFinite()) y else { reset(); x }
        }
        
        private fun reset() { x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f }
    }

    private val filtersL = Array(5) { Biquad() }
    private val filtersR = Array(5) { Biquad() }
    private val frequencies = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

    // Ultra-High-Pass Filter for the Exciter (Clarity only where it matters)
    private val exciterHPL = Biquad()
    private val exciterHPR = Biquad()

    private var angle = 0f
    private val bufferSize = 4096
    private val delayL = FloatArray(bufferSize)
    private val delayR = FloatArray(bufferSize)
    private var writeIndex = 0

    var rotationSpeed = 7.0f
    var clarityLevel = 0.5f
    var snappiness = 0.5f
    var soundstageWidth = 1.0f
    var spatialEnabled = false

    private var prevEnv = 0f
    private val outFrame = FloatArray(2)

    fun setup(sr: Float) {
        if (sr > 0) {
            this.sampleRate = sr
            // Initialize Exciter HPFs at 4kHz for "Air" clarity
            exciterHPL.setPeaking(6000f, sr, 0.7f, 6f)
            exciterHPR.setPeaking(6000f, sr, 0.7f, 6f)
            updateEq(floatArrayOf(0f, 0f, 0f, 0f, 0f))
        }
    }

    fun updateEq(gains: FloatArray) {
        for (i in 0 until 5) {
            val g = if (i < gains.size) gains[i] else 0f
            filtersL[i].setPeaking(frequencies[i], sampleRate, 1.1f, g)
            filtersR[i].setPeaking(frequencies[i], sampleRate, 1.1f, g)
        }
    }

    private fun readHermite(buf: FloatArray, delaySamples: Float): Float {
        var readPos = writeIndex - delaySamples
        while (readPos < 0) readPos += bufferSize
        val i1 = readPos.toInt() % bufferSize
        val f = readPos - i1.toFloat()
        val i0 = (i1 - 1 + bufferSize) % bufferSize
        val i2 = (i1 + 1) % bufferSize
        val i3 = (i1 + 2) % bufferSize
        val y0 = buf[i0]; val y1 = buf[i1]; val y2 = buf[i2]; val y3 = buf[i3]
        val a = (3f * (y1 - y2) - y0 + y3) * 0.5f
        val b = 2f * y2 + y0 - 5f * y1 * 0.5f - y3 * 0.5f
        val c = (y2 - y0) * 0.5f
        return (((a * f) + b) * f + c) * f + y1
    }

    fun process(lIn: Float, rIn: Float): FloatArray {
        var l = lIn; var r = rIn

        // 1. Crystal Clear EQ
        for (i in 0 until 5) {
            l = filtersL[i].process(l)
            r = filtersR[i].process(r)
        }

        // 2. High-Res Dynamics (Minimalist Transient Shaper)
        val mono = (abs(l) + abs(r)) * 0.5f
        val attack = 0.005f + (snappiness * 0.05f)
        val env = if (mono > prevEnv) prevEnv + attack * (mono - prevEnv) else prevEnv + 0.002f * (mono - prevEnv)
        prevEnv = env
        val tGain = 1.0f + (mono - env) * snappiness * 1.5f
        l *= tGain; r *= tGain

        // 3. Immersive 16D Spatial
        delayL[writeIndex] = l
        delayR[writeIndex] = r
        if (spatialEnabled) {
            val s = sin(angle); val c = cos(angle)
            val gL = 0.5f - (s * 0.48f); val gR = 0.5f + (s * 0.48f)
            val maxD = (1.5f / 1000f) * sampleRate
            l = readHermite(delayL, gR * maxD) * gL * (0.85f + c * 0.15f)
            r = readHermite(delayR, gL * maxD) * gR * (0.85f + c * 0.15f)
            angle += rotationSpeed / sampleRate
            if (angle > 6.2831855f) angle -= 6.2831855f
        }
        writeIndex = (writeIndex + 1) % bufferSize

        // 4. Stereo Soundstage
        val mid = (l + r) * 0.5f
        val side = (l - r) * 0.5f * soundstageWidth
        l = mid + side; r = mid - side

        // 5. HIGH-RES CLARITY EXCITER (Harmonics only in the 'Air' band)
        // This makes vocals and treble pop without adding grit to the low-end
        val highL = exciterHPL.process(l)
        val highR = exciterHPR.process(r)
        val harmonicsL = highL * highL * (if (highL > 0) 1f else -1f)
        val harmonicsR = highR * highR * (if (highR > 0) 1f else -1f)
        l += (harmonicsL * clarityLevel * 0.15f)
        r += (harmonicsR * clarityLevel * 0.15f)

        // 6. TRANSPARENT LIMITER (Soft-knee tanh for natural dynamics)
        fun limit(v: Float): Float {
            return if (v > 0.7f || v < -0.7f) {
                // Soft-knee: smoothly compress peaks above 0.7
                val sign = if (v > 0f) 1f else -1f
                val abs = if (v > 0f) v else -v
                sign * (0.7f + 0.3f * kotlin.math.tanh((abs - 0.7f) / 0.3f))
            } else v
        }

        outFrame[0] = limit(l); outFrame[1] = limit(r)
        return outFrame
    }
}
