package com.example.myapplication.audio.processors

import kotlin.math.*

/**
 * TITAN HI-RES AUDIO ENGINE v2
 *
 * Professional-grade DSP with:
 * - Smooth parameter interpolation (no clicks/pops when changing EQ)
 * - Crossfade 16D toggle (no pop when switching spatial on/off)
 * - Anti-denormal protection (prevents ARM float noise)
 * - Gentle clarity exciter (airy shimmer without harshness)
 * - Soft-knee transparent limiter
 * - Professional spatial audio (slow, immersive binaural rotation)
 */
class TitanKotlinEngine {
    private var sampleRate = 44100f

    // ===== BIQUAD FILTER WITH ANTI-DENORMAL =====
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
            // Anti-denormal: add tiny constant to prevent ARM float noise
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2 + 1e-25f
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return if (y.isFinite()) y else { reset(); x }
        }

        fun reset() { x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f }
    }

    // ===== EQ SECTION =====
    private val filtersL = Array(5) { Biquad() }
    private val filtersR = Array(5) { Biquad() }
    private val frequencies = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

    // SMOOTH PARAMETER INTERPOLATION: Prevents clicks when moving EQ sliders
    // Instead of instantly changing biquad coefficients, we interpolate toward targets
    private val currentGains = FloatArray(5) { 0f }  // What the filters are CURRENTLY at
    private val targetGains = FloatArray(5) { 0f }    // What the UI wants
    private var smoothingCoeff = 0.002f                // ~50ms smoothing at 44.1kHz

    // ===== EXCITER =====
    private val exciterHPL = Biquad()
    private val exciterHPR = Biquad()

    // ===== 16D SPATIAL =====
    private var angle = 0f
    private val bufferSize = 8192  // Larger buffer for smoother spatial
    private val delayL = FloatArray(bufferSize)
    private val delayR = FloatArray(bufferSize)
    private var writeIndex = 0

    // Crossfade for smooth 16D toggle (prevents pop on enable/disable)
    private var spatialMix = 0f       // 0.0 = fully dry, 1.0 = fully spatial
    private var spatialTarget = 0f    // Where we're fading to
    private val spatialFadeSpeed = 0.0005f  // ~2000 samples to fully crossfade (~45ms)

    // ===== PARAMETERS =====
    var rotationSpeed = 1.0f     // ~0.16 Hz — slow, immersive, professional spatial audio
    var clarityLevel = 0.5f
    var snappiness = 0.5f
    var soundstageWidth = 1.0f
    var spatialEnabled = false
        set(value) {
            field = value
            spatialTarget = if (value) 1f else 0f  // Smooth crossfade, not instant
        }

    // ===== DYNAMICS =====
    private var prevEnv = 0f
    private val outFrame = FloatArray(2)

    fun setup(sr: Float) {
        if (sr > 0) {
            this.sampleRate = sr
            // Recalculate smoothing coefficient for actual sample rate
            // ~50ms time constant: coeff = 1 - exp(-1 / (sr * 0.05))
            smoothingCoeff = 1f - exp(-1f / (sr * 0.05f))

            // Initialize Exciter high-shelf at 6kHz for airy shimmer
            exciterHPL.setPeaking(6000f, sr, 0.7f, 6f)
            exciterHPR.setPeaking(6000f, sr, 0.7f, 6f)
            updateEq(floatArrayOf(0f, 0f, 0f, 0f, 0f))
        }
    }

    fun updateEq(gains: FloatArray) {
        // Only update TARGETS — actual coefficients are smoothed in process()
        for (i in 0 until 5) {
            targetGains[i] = if (i < gains.size) gains[i] else 0f
        }
    }

    /**
     * Reset all DSP state to defaults.
     * Called by the "RESET" button in the studio engine UI.
     */
    fun resetState() {
        for (i in 0 until 5) {
            currentGains[i] = 0f
            targetGains[i] = 0f
            filtersL[i].reset()
            filtersR[i].reset()
            filtersL[i].setPeaking(frequencies[i], sampleRate, 1.1f, 0f)
            filtersR[i].setPeaking(frequencies[i], sampleRate, 1.1f, 0f)
        }
        exciterHPL.reset()
        exciterHPR.reset()
        prevEnv = 0f
        angle = 0f
        spatialMix = 0f
        spatialTarget = 0f
        writeIndex = 0
        delayL.fill(0f)
        delayR.fill(0f)
    }

    // ===== HERMITE INTERPOLATION (for delay line reads) =====
    private fun readHermite(buf: FloatArray, delaySamples: Float): Float {
        var readPos = writeIndex - delaySamples
        while (readPos < 0) readPos += bufferSize
        val i1 = readPos.toInt() % bufferSize
        val f = readPos - readPos.toInt().toFloat()
        val i0 = (i1 - 1 + bufferSize) % bufferSize
        val i2 = (i1 + 1) % bufferSize
        val i3 = (i1 + 2) % bufferSize
        val y0 = buf[i0]; val y1 = buf[i1]; val y2 = buf[i2]; val y3 = buf[i3]
        val a = (3f * (y1 - y2) - y0 + y3) * 0.5f
        val b = 2f * y2 + y0 - 5f * y1 * 0.5f - y3 * 0.5f
        val c = (y2 - y0) * 0.5f
        return (((a * f) + b) * f + c) * f + y1
    }

    // ===== MAIN DSP PROCESS (called once per stereo sample) =====
    fun process(lIn: Float, rIn: Float): FloatArray {
        var l = lIn; var r = rIn

        // ──────────────────────────────────────────────────────────
        // 1. SMOOTH EQ — Interpolate gains toward targets each sample
        //    This prevents clicks/pops when the user drags EQ sliders
        // ──────────────────────────────────────────────────────────
        for (i in 0 until 5) {
            val diff = targetGains[i] - currentGains[i]
            if (abs(diff) > 0.001f) {
                // Exponential smoothing toward target
                currentGains[i] += diff * smoothingCoeff
                // Recalculate biquad coefficients with smoothed gain
                filtersL[i].setPeaking(frequencies[i], sampleRate, 1.1f, currentGains[i])
                filtersR[i].setPeaking(frequencies[i], sampleRate, 1.1f, currentGains[i])
            }
        }
        for (i in 0 until 5) {
            l = filtersL[i].process(l)
            r = filtersR[i].process(r)
        }

        // ──────────────────────────────────────────────────────────
        // 2. DYNAMICS — Gentle transient shaper (musical, not aggressive)
        // ──────────────────────────────────────────────────────────
        val mono = (abs(l) + abs(r)) * 0.5f
        val attack = 0.005f + (snappiness * 0.03f)  // Gentler attack range
        val env = if (mono > prevEnv) prevEnv + attack * (mono - prevEnv)
                  else prevEnv + 0.001f * (mono - prevEnv)  // Slower release
        prevEnv = env
        val tGain = 1.0f + (mono - env) * snappiness * 1.0f  // Reduced from 1.5
        l *= tGain; r *= tGain

        // ──────────────────────────────────────────────────────────
        // 3. PROFESSIONAL 16D SPATIAL AUDIO — Slow binaural rotation
        //    Crossfades smoothly between dry and spatial to prevent pops
        // ──────────────────────────────────────────────────────────
        delayL[writeIndex] = l
        delayR[writeIndex] = r

        // Smooth crossfade: spatialMix glides toward spatialTarget
        if (abs(spatialMix - spatialTarget) > 0.0001f) {
            spatialMix += (spatialTarget - spatialMix) * spatialFadeSpeed
            // Snap to target when close enough
            if (abs(spatialMix - spatialTarget) < 0.001f) spatialMix = spatialTarget
        }

        if (spatialMix > 0.001f) {
            val s = sin(angle); val c = cos(angle)
            
            // Professional HRTF-style level difference (Acoustic Shadow)
            // Head shadow rarely drops below -12dB (approx 0.25 amplitude).
            // This ensures the opposite ear still plays softly instead of going blank.
            val gL = 0.5f - (s * 0.25f)
            val gR = 0.5f + (s * 0.25f)
            
            val maxD = (1.5f / 1000f) * sampleRate

            // Read from delay lines (Interaural Time Difference)
            val delayReadL = readHermite(delayL, gR * maxD)
            val delayReadR = readHermite(delayR, gL * maxD)

            // Crossfeed: The far ear still hears the room reflection of the near ear
            // We mix 20% of the opposite channel's delayed signal to simulate room scatter
            val crossL = delayReadL * 0.80f + delayReadR * 0.20f
            val crossR = delayReadR * 0.80f + delayReadL * 0.20f

            val spatL = crossL * gL * (0.85f + c * 0.15f)
            val spatR = crossR * gR * (0.85f + c * 0.15f)

            // Crossfade between dry and spatial
            l = l * (1f - spatialMix) + spatL * spatialMix
            r = r * (1f - spatialMix) + spatR * spatialMix

            angle += rotationSpeed / sampleRate
            if (angle > 6.2831855f) angle -= 6.2831855f
        }
        writeIndex = (writeIndex + 1) % bufferSize

        // ──────────────────────────────────────────────────────────
        // 4. STEREO SOUNDSTAGE — M/S width control
        // ──────────────────────────────────────────────────────────
        val mid = (l + r) * 0.5f
        val side = (l - r) * 0.5f * soundstageWidth
        l = mid + side; r = mid - side

        // ──────────────────────────────────────────────────────────
        // 5. HI-RES CLARITY EXCITER — Gentle air-band harmonics
        //    Soft-clipped to prevent any harshness
        // ──────────────────────────────────────────────────────────
        val highL = exciterHPL.process(l)
        val highR = exciterHPR.process(r)
        // Soft-clipped harmonics: tanh tames harsh peaks before mixing
        val harmonicsL = tanh(highL * highL * (if (highL > 0) 1f else -1f) * 2f)
        val harmonicsR = tanh(highR * highR * (if (highR > 0) 1f else -1f) * 2f)
        l += (harmonicsL * clarityLevel * 0.08f)  // Reduced from 0.15 to 0.08
        r += (harmonicsR * clarityLevel * 0.08f)

        // ──────────────────────────────────────────────────────────
        // 6. TRANSPARENT LIMITER — Soft-knee with high threshold
        //    Preserves dynamics, only catches true peaks
        // ──────────────────────────────────────────────────────────
        fun limit(v: Float): Float {
            return if (v > 0.85f || v < -0.85f) {
                val sign = if (v > 0f) 1f else -1f
                val abs = if (v > 0f) v else -v
                sign * (0.85f + 0.15f * tanh((abs - 0.85f) / 0.15f))
            } else v
        }

        outFrame[0] = limit(l); outFrame[1] = limit(r)
        return outFrame
    }
}
