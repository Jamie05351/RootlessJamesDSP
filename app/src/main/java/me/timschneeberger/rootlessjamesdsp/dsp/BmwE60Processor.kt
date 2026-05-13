package me.timschneeberger.rootlessjamesdsp.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class BmwE60Processor {
    private val lowLp2L = StereoBiquad()
    private val lowLp2R = StereoBiquad()
    private val lowLp1L = FirstOrder()
    private val lowLp1R = FirstOrder()
    private val midHp2L = StereoBiquad()
    private val midHp2R = StereoBiquad()
    private val midHp1L = FirstOrder()
    private val midHp1R = FirstOrder()
    private val subsonicL = StereoBiquad()
    private val subsonicR = StereoBiquad()
    private val lowEq1L = StereoBiquad()
    private val lowEq1R = StereoBiquad()
    private val lowEq2L = StereoBiquad()
    private val lowEq2R = StereoBiquad()
    private val midEq1L = StereoBiquad()
    private val midEq1R = StereoBiquad()
    private val midEq2L = StereoBiquad()
    private val midEq2R = StereoBiquad()
    private val lowApL = FirstOrderAllPass()
    private val lowApR = FirstOrderAllPass()
    private val midApL = FirstOrderAllPass()
    private val midApR = FirstOrderAllPass()
    private val delayL = FractionalDelay(512)
    private val delayR = FractionalDelay(512)

    var enabled = true
    var headroomDb = -6.0
    var lowGainL = 1.0
    var lowGainR = 1.0
    var midGainL = -4.0
    var midGainR = -4.0
    var postGainL = -2.0
    var postGainR = -2.0
    var lpfEnabled = true
    var lpfType = 0
    var lpfFreq = 160.0
    var hpfEnabled = true
    var hpfType = 1
    var hpfFreq = 140.0
    var subsonicEnabled = true
    var subsonicFreq = 25.0
    var lowPolarity = false
    var midPolarity = true
    var midDelayLms = 1.80
    var midDelayRms = 1.80
    var lowApEnabled = true
    var lowApFreq = 145.0
    var lowApDeg = 0.0
    var midApEnabled = true
    var midApFreq = 130.0
    var midApDeg = -14.0
    var lowEq1Enabled = true
    var lowEq1Freq = 85.0
    var lowEq1Gain = 2.0
    var lowEq1Q = 0.80
    var lowEq2Enabled = true
    var lowEq2Freq = 100.0
    var lowEq2Gain = 2.0
    var lowEq2Q = 1.0
    var midEq1Enabled = true
    var midEq1Freq = 185.0
    var midEq1Gain = -5.0
    var midEq1Q = 4.3
    var midEq2Enabled = true
    var midEq2Freq = 250.0
    var midEq2Gain = -3.0
    var midEq2Q = 1.5

    private var sampleRate = 48000.0
    private var lowGainLScalar = 1.0
    private var lowGainRScalar = 1.0
    private var midGainLScalar = 1.0
    private var midGainRScalar = 1.0
    private var postGainLScalar = 1.0
    private var postGainRScalar = 1.0
    private var headroomScalar = 1.0

    fun setSampleRate(sr: Float) {
        if (sr <= 0f) return
        sampleRate = sr.toDouble()
        rebuild()
    }

    fun rebuild() {
        val fs = sampleRate
        headroomScalar = dbToLin(headroomDb)
        lowGainLScalar = dbToLin(lowGainL)
        lowGainRScalar = dbToLin(lowGainR)
        midGainLScalar = dbToLin(midGainL)
        midGainRScalar = dbToLin(midGainR)
        postGainLScalar = dbToLin(postGainL)
        postGainRScalar = dbToLin(postGainR)

        subsonicL.setHighPass(subsonicFreq, 0.7071067812, fs)
        subsonicR.setHighPass(subsonicFreq, 0.7071067812, fs)

        if (lpfType == 0) {
            lowLp2L.setLowPass(lpfFreq, 0.7071067812, fs)
            lowLp2R.setLowPass(lpfFreq, 0.7071067812, fs)
            lowLp1L.bypass = true
            lowLp1R.bypass = true
        } else {
            lowLp2L.setLowPass(lpfFreq, 1.0, fs)
            lowLp2R.setLowPass(lpfFreq, 1.0, fs)
            lowLp1L.setLowPass(lpfFreq, fs)
            lowLp1R.setLowPass(lpfFreq, fs)
        }

        if (hpfType == 0) {
            midHp2L.setHighPass(hpfFreq, 0.7071067812, fs)
            midHp2R.setHighPass(hpfFreq, 0.7071067812, fs)
            midHp1L.bypass = true
            midHp1R.bypass = true
        } else {
            midHp2L.setHighPass(hpfFreq, 1.0, fs)
            midHp2R.setHighPass(hpfFreq, 1.0, fs)
            midHp1L.setHighPass(hpfFreq, fs)
            midHp1R.setHighPass(hpfFreq, fs)
        }

        lowEq1L.setPeak(lowEq1Freq, lowEq1Q, lowEq1Gain, fs)
        lowEq1R.setPeak(lowEq1Freq, lowEq1Q, lowEq1Gain, fs)
        lowEq2L.setPeak(lowEq2Freq, lowEq2Q, lowEq2Gain, fs)
        lowEq2R.setPeak(lowEq2Freq, lowEq2Q, lowEq2Gain, fs)
        midEq1L.setPeak(midEq1Freq, midEq1Q, midEq1Gain, fs)
        midEq1R.setPeak(midEq1Freq, midEq1Q, midEq1Gain, fs)
        midEq2L.setPeak(midEq2Freq, midEq2Q, midEq2Gain, fs)
        midEq2R.setPeak(midEq2Freq, midEq2Q, midEq2Gain, fs)

        lowApL.setPhaseTrim(lowApFreq, lowApDeg, fs)
        lowApR.setPhaseTrim(lowApFreq, lowApDeg, fs)
        midApL.setPhaseTrim(midApFreq, midApDeg, fs)
        midApR.setPhaseTrim(midApFreq, midApDeg, fs)

        delayL.setDelay(midDelayLms * fs * 0.001)
        delayR.setDelay(midDelayRms * fs * 0.001)
    }

    fun process(buffer: FloatArray, frames: Int) {
        if (!enabled) return
        var i = 0
        repeat(frames) {
            var srcL = buffer[i].toDouble() * headroomScalar
            var srcR = buffer[i + 1].toDouble() * headroomScalar
            if (subsonicEnabled) {
                srcL = subsonicL.process(srcL)
                srcR = subsonicR.process(srcR)
            }

            var lowL = if (lpfEnabled) lowPath(srcL, true) else 0.0
            var lowR = if (lpfEnabled) lowPath(srcR, false) else 0.0
            var midL = if (hpfEnabled) midPath(srcL, true) else 0.0
            var midR = if (hpfEnabled) midPath(srcR, false) else 0.0

            if (lowPolarity) { lowL = -lowL; lowR = -lowR }
            if (midPolarity) { midL = -midL; midR = -midR }

            val outL = (lowL + midL) * postGainLScalar
            val outR = (lowR + midR) * postGainRScalar
            buffer[i] = outL.coerceIn(-1.0, 1.0).toFloat()
            buffer[i + 1] = outR.coerceIn(-1.0, 1.0).toFloat()
            i += 2
        }
    }

    fun process(buffer: ShortArray, frames: Int) {
        if (!enabled) return
        val floatBuffer = FloatArray(frames * 2)
        var i = 0
        while (i < floatBuffer.size) {
            floatBuffer[i] = buffer[i] / 32768.0f
            i++
        }
        process(floatBuffer, frames)
        i = 0
        while (i < floatBuffer.size) {
            buffer[i] = (floatBuffer[i] * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
            i++
        }
    }

    private fun lowPath(x: Double, left: Boolean): Double {
        var y = x
        if (left) {
            y = lowLp2L.process(y)
            y = lowLp1L.process(y)
            if (lowEq1Enabled) y = lowEq1L.process(y)
            if (lowEq2Enabled) y = lowEq2L.process(y)
            if (lowApEnabled) y = lowApL.process(y)
            y *= lowGainLScalar
        } else {
            y = lowLp2R.process(y)
            y = lowLp1R.process(y)
            if (lowEq1Enabled) y = lowEq1R.process(y)
            if (lowEq2Enabled) y = lowEq2R.process(y)
            if (lowApEnabled) y = lowApR.process(y)
            y *= lowGainRScalar
        }
        return y
    }

    private fun midPath(x: Double, left: Boolean): Double {
        var y = x
        if (left) {
            y = midHp2L.process(y)
            y = midHp1L.process(y)
            if (midEq1Enabled) y = midEq1L.process(y)
            if (midEq2Enabled) y = midEq2L.process(y)
            if (midApEnabled) y = midApL.process(y)
            y = delayL.process(y)
            y *= midGainLScalar
        } else {
            y = midHp2R.process(y)
            y = midHp1R.process(y)
            if (midEq1Enabled) y = midEq1R.process(y)
            if (midEq2Enabled) y = midEq2R.process(y)
            if (midApEnabled) y = midApR.process(y)
            y = delayR.process(y)
            y *= midGainRScalar
        }
        return y
    }

    private fun dbToLin(db: Double): Double = exp(db * 0.11512925464970228)

    private class StereoBiquad {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
        private var a1 = 0.0; private var a2 = 0.0
        private var z1 = 0.0; private var z2 = 0.0
        var bypass = false

        fun setLowPass(freq: Double, q: Double, fs: Double) = configure(freq, q, fs, true)
        fun setHighPass(freq: Double, q: Double, fs: Double) = configure(freq, q, fs, false)

        private fun configure(freq: Double, q: Double, fs: Double, lowPass: Boolean) {
            val fc = freq.coerceIn(20.0, fs * 0.49)
            val w0 = 2.0 * PI * fc / fs
            val cw = cos(w0)
            val sw = sin(w0)
            val alpha = sw / (2.0 * max(0.05, q))
            val a0 = 1.0 + alpha
            if (lowPass) {
                b0 = ((1.0 - cw) * 0.5) / a0
                b1 = (1.0 - cw) / a0
                b2 = ((1.0 - cw) * 0.5) / a0
            } else {
                b0 = ((1.0 + cw) * 0.5) / a0
                b1 = (-(1.0 + cw)) / a0
                b2 = ((1.0 + cw) * 0.5) / a0
            }
            a1 = (-2.0 * cw) / a0
            a2 = (1.0 - alpha) / a0
            bypass = false
            reset()
        }

        fun setPeak(freq: Double, q: Double, gainDb: Double, fs: Double) {
            val fc = freq.coerceIn(20.0, fs * 0.49)
            val a = exp(gainDb * 0.11512925464970228 * 0.5)
            val w0 = 2.0 * PI * fc / fs
            val cw = cos(w0)
            val sw = sin(w0)
            val alpha = sw / (2.0 * max(0.05, q))
            val inv = 1.0 / (1.0 + alpha / a)
            b0 = (1.0 + alpha * a) * inv
            b1 = (-2.0 * cw) * inv
            b2 = (1.0 - alpha * a) * inv
            a1 = (-2.0 * cw) * inv
            a2 = (1.0 - alpha / a) * inv
            bypass = false
            reset()
        }

        fun process(x: Double): Double {
            if (bypass) return x
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            if (abs(z1) < 1e-20) z1 = 0.0
            if (abs(z2) < 1e-20) z2 = 0.0
            return y
        }

        private fun reset() { z1 = 0.0; z2 = 0.0 }
    }

    private class FirstOrder {
        var bypass = true
        private var b0 = 1.0; private var b1 = 0.0; private var a1 = 0.0
        private var z1 = 0.0

        fun setLowPass(freq: Double, fs: Double) = configure(freq, fs, true)
        fun setHighPass(freq: Double, fs: Double) = configure(freq, fs, false)

        private fun configure(freq: Double, fs: Double, lowPass: Boolean) {
            val k = tan(PI * freq.coerceIn(20.0, fs * 0.49) / fs)
            val inv = 1.0 / (1.0 + k)
            if (lowPass) {
                b0 = k * inv; b1 = b0
            } else {
                b0 = inv; b1 = -inv
            }
            a1 = (k - 1.0) * inv
            z1 = 0.0
            bypass = false
        }

        fun process(x: Double): Double {
            if (bypass) return x
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y
            if (abs(z1) < 1e-20) z1 = 0.0
            return y
        }
    }

    private class FirstOrderAllPass {
        private var b0 = 0.0; private var b1 = 1.0; private var a1 = 0.0
        private var z1 = 0.0

        fun setPhaseTrim(freq: Double, deg: Double, fs: Double) {
            val fc = freq.coerceIn(20.0, fs * 0.49)
            val phi = (deg * PI / 180.0).coerceIn(-PI * 0.49, PI * 0.49)
            val w = 2.0 * PI * fc / fs
            val t = tan(-phi * 0.5) / max(1e-9, tan(w * 0.5))
            val a = ((1.0 - t) / (1.0 + t)).coerceIn(-0.9999, 0.9999)
            b0 = a
            b1 = 1.0
            a1 = a
            z1 = 0.0
        }

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y
            if (abs(z1) < 1e-20) z1 = 0.0
            return y
        }
    }

    private class FractionalDelay(private val size: Int) {
        private val buffer = DoubleArray(size)
        private var index = 0
        private var delay = 0.0

        fun setDelay(samples: Double) {
            delay = samples.coerceIn(0.0, (size - 2).toDouble())
        }

        fun process(x: Double): Double {
            buffer[index] = x
            var read = index - delay
            while (read < 0.0) read += size.toDouble()
            val i0 = read.toInt() % size
            val i1 = (i0 + 1) % size
            val frac = read - i0
            val y = buffer[i0] * (1.0 - frac) + buffer[i1] * frac
            index++
            if (index >= size) index = 0
            return y
        }
    }
}
