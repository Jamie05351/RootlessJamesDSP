package me.timschneeberger.rootlessjamesdsp.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.tan

/**
 * RBJ Cookbook biquad filter using DF2 Transposed (Direct Form II Transposed) topology.
 *
 * The DF2T form per sample (single channel):
 *   y    = b0*x + s1
 *   s1   = b1*x - a1*y + s2
 *   s2   = b2*x - a2*y
 *
 * Coefficients are normalised so a0 = 1.
 */
class BiquadFilter {

    enum class Type(val id: Int) {
        PEAK(0),
        LOW_SHELF(1),
        HIGH_SHELF(2),
        LOW_PASS(3),
        HIGH_PASS(4),
        ALL_PASS(5),
        NOTCH(6),
        BAND_PASS(7)
    }

    // Normalised coefficients (a0 = 1)
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // DF2T state — two channels (L, R)
    private var s1L = 0.0
    private var s2L = 0.0
    private var s1R = 0.0
    private var s2R = 0.0

    var polarity: Boolean = false   // false = normal, true = polarity flip (invert)
    var bypass: Boolean = false

    /** Configure all coefficients from filter-type parameters. */
    fun configure(type: Type, freqHz: Double, gainDb: Double, q: Double, sampleRate: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val A = 10.0.pow(gainDb / 40.0)     // linear amplitude (sqrt of power)

        var nb0: Double
        var nb1: Double
        var nb2: Double
        var na0: Double
        var na1: Double
        var na2: Double

        when (type) {
            Type.PEAK -> {
                nb0 = 1.0 + alpha * A
                nb1 = -2.0 * cosW0
                nb2 = 1.0 - alpha * A
                na0 = 1.0 + alpha / A
                na1 = -2.0 * cosW0
                na2 = 1.0 - alpha / A
            }
            Type.LOW_SHELF -> {
                val twoSqrtAAlpha = 2.0 * sqrt(A) * alpha
                nb0 = A * ((A + 1) - (A - 1) * cosW0 + twoSqrtAAlpha)
                nb1 = 2.0 * A * ((A - 1) - (A + 1) * cosW0)
                nb2 = A * ((A + 1) - (A - 1) * cosW0 - twoSqrtAAlpha)
                na0 = (A + 1) + (A - 1) * cosW0 + twoSqrtAAlpha
                na1 = -2.0 * ((A - 1) + (A + 1) * cosW0)
                na2 = (A + 1) + (A - 1) * cosW0 - twoSqrtAAlpha
            }
            Type.HIGH_SHELF -> {
                val twoSqrtAAlpha = 2.0 * sqrt(A) * alpha
                nb0 = A * ((A + 1) + (A - 1) * cosW0 + twoSqrtAAlpha)
                nb1 = -2.0 * A * ((A - 1) + (A + 1) * cosW0)
                nb2 = A * ((A + 1) + (A - 1) * cosW0 - twoSqrtAAlpha)
                na0 = (A + 1) - (A - 1) * cosW0 + twoSqrtAAlpha
                na1 = 2.0 * ((A - 1) - (A + 1) * cosW0)
                na2 = (A + 1) - (A - 1) * cosW0 - twoSqrtAAlpha
            }
            Type.LOW_PASS -> {
                nb0 = (1.0 - cosW0) / 2.0
                nb1 = 1.0 - cosW0
                nb2 = (1.0 - cosW0) / 2.0
                na0 = 1.0 + alpha
                na1 = -2.0 * cosW0
                na2 = 1.0 - alpha
            }
            Type.HIGH_PASS -> {
                nb0 = (1.0 + cosW0) / 2.0
                nb1 = -(1.0 + cosW0)
                nb2 = (1.0 + cosW0) / 2.0
                na0 = 1.0 + alpha
                na1 = -2.0 * cosW0
                na2 = 1.0 - alpha
            }
            Type.ALL_PASS -> {
                nb0 = 1.0 - alpha
                nb1 = -2.0 * cosW0
                nb2 = 1.0 + alpha
                na0 = 1.0 + alpha
                na1 = -2.0 * cosW0
                na2 = 1.0 - alpha
            }
            Type.NOTCH -> {
                nb0 = 1.0
                nb1 = -2.0 * cosW0
                nb2 = 1.0
                na0 = 1.0 + alpha
                na1 = -2.0 * cosW0
                na2 = 1.0 - alpha
            }
            Type.BAND_PASS -> {
                // Constant skirt gain (peak gain = Q)
                nb0 = sinW0 / 2.0
                nb1 = 0.0
                nb2 = -sinW0 / 2.0
                na0 = 1.0 + alpha
                na1 = -2.0 * cosW0
                na2 = 1.0 - alpha
            }
        }

        // Normalise by a0
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0

        resetState()
    }

    /**
     * Configure as a 1st-order LP or HP filter (bilinear transform).
     * Used for the 1st-order stage in a BW3 cascade.
     * b2 and a2 are set to 0, making this a degenerate biquad.
     */
    fun configureFirstOrder(isLowPass: Boolean, freqHz: Double, sampleRate: Double) {
        val k = tan(PI * freqHz / sampleRate)
        if (isLowPass) {
            b0 = k / (k + 1.0)
            b1 = k / (k + 1.0)
            b2 = 0.0
            a1 = (k - 1.0) / (k + 1.0)
            a2 = 0.0
        } else {
            b0 =  1.0 / (k + 1.0)
            b1 = -1.0 / (k + 1.0)
            b2 = 0.0
            a1 = (k - 1.0) / (k + 1.0)
            a2 = 0.0
        }
        bypass = false
        resetState()
    }

    /** Reset internal DF2T delay-line state (call on sample-rate change or large gaps). */
    fun resetState() {
        s1L = 0.0; s2L = 0.0
        s1R = 0.0; s2R = 0.0
    }

    /**
     * Process an interleaved stereo float buffer in-place.
     * @param buf  Interleaved stereo samples [L0, R0, L1, R1, …]
     * @param frames Number of stereo frames (buf.size / 2)
     */
    fun process(buf: FloatArray, frames: Int) {
        if (bypass) return

        val polarityScale = if (polarity) -1.0 else 1.0

        var i = 0
        repeat(frames) {
            // Left
            val xL = buf[i].toDouble()
            val yL = b0 * xL + s1L
            s1L = b1 * xL - a1 * yL + s2L
            s2L = b2 * xL - a2 * yL
            buf[i] = (yL * polarityScale).toFloat()
            i++

            // Right
            val xR = buf[i].toDouble()
            val yR = b0 * xR + s1R
            s1R = b1 * xR - a1 * yR + s2R
            s2R = b2 * xR - a2 * yR
            buf[i] = (yR * polarityScale).toFloat()
            i++
        }
    }

    /**
     * Process an interleaved stereo ShortArray buffer, converting to float internally.
     */
    fun process(buf: ShortArray, frames: Int) {
        if (bypass) return

        val scale = 1.0 / 32768.0
        val polarityScale = if (polarity) -1.0 else 1.0

        var i = 0
        repeat(frames) {
            val xL = buf[i] * scale
            val yL = b0 * xL + s1L
            s1L = b1 * xL - a1 * yL + s2L
            s2L = b2 * xL - a2 * yL
            buf[i] = ((yL * polarityScale) * 32768.0).toInt().coerceIn(-32768, 32767).toShort()
            i++

            val xR = buf[i] * scale
            val yR = b0 * xR + s1R
            s1R = b1 * xR - a1 * yR + s2R
            s2R = b2 * xR - a2 * yR
            buf[i] = ((yR * polarityScale) * 32768.0).toInt().coerceIn(-32768, 32767).toShort()
            i++
        }
    }
}
