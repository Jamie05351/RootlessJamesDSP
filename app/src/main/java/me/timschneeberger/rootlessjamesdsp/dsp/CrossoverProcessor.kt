package me.timschneeberger.rootlessjamesdsp.dsp

import android.content.Context
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.tan

/**
 * Crossover processor supporting 2-way and 3-way operation.
 *
 * Filter topologies (value stored in preference):
 *  0 = LR2  — Linkwitz-Riley 2nd order (Q = 0.5)
 *  1 = BW3  — Butterworth 3rd order (1st-order + 2nd-order Q=1 in cascade)
 *  2 = LR4  — Linkwitz-Riley 4th order (two 2nd-order Q=0.7071 in cascade)
 *
 * Preferences are read from the [Constants.PREF_CROSSOVER] shared-preferences namespace.
 */
class CrossoverProcessor(private val context: Context) {

    companion object {
        const val TOPO_LR2 = 0
        const val TOPO_BW3 = 1
        const val TOPO_LR4 = 2

        const val Q_LR2  = 0.5
        const val Q_BW3  = 1.0          // 2nd-order stage of the BW3 triplet
        const val Q_LR4  = 0.7071067811865476   // 1/√2
    }

    // Each crossover point needs up to 2 LP stages and 2 HP stages (for LR4/BW3)
    private val lp1a = BiquadFilter()   // crossover-1 low-pass  stage-1
    private val lp1b = BiquadFilter()   // crossover-1 low-pass  stage-2 (LR4) or 1st-order (BW3)
    private val hp1a = BiquadFilter()   // crossover-1 high-pass stage-1
    private val hp1b = BiquadFilter()   // crossover-1 high-pass stage-2 (LR4) or 1st-order (BW3)

    private val lp2a = BiquadFilter()   // crossover-2 (3-way only)
    private val lp2b = BiquadFilter()
    private val hp2a = BiquadFilter()
    private val hp2b = BiquadFilter()

    var enabled: Boolean = false
    var mode: Int = 2                   // 2 = 2-way, 3 = 3-way

    var lowPolarity:  Boolean = false
    var midPolarity:  Boolean = false
    var highPolarity: Boolean = false

    private var sampleRate: Float = 44100f

    fun setSampleRate(sr: Float) {
        if (sr <= 0f) return
        sampleRate = sr
        readAndApplyPreferences()
    }

    fun readAndApplyPreferences() {
        val prefs = context.getSharedPreferences(Constants.PREF_CROSSOVER, Context.MODE_MULTI_PROCESS)

        enabled   = prefs.getBoolean(context.getString(R.string.key_crossover_enable), false)
        mode      = prefs.getString(context.getString(R.string.key_crossover_mode), "2")
                        ?.toIntOrNull() ?: 2

        lowPolarity  = prefs.getBoolean(context.getString(R.string.key_crossover_low_polarity),  false)
        midPolarity  = prefs.getBoolean(context.getString(R.string.key_crossover_mid_polarity),  false)
        highPolarity = prefs.getBoolean(context.getString(R.string.key_crossover_high_polarity), false)

        val freq1  = prefs.getFloat(context.getString(R.string.key_crossover_1_freq), 500f).toDouble()
        val topo1  = prefs.getString(context.getString(R.string.key_crossover_1_type), "0")
                         ?.toIntOrNull() ?: TOPO_LR2

        val freq2  = prefs.getFloat(context.getString(R.string.key_crossover_2_freq), 4000f).toDouble()
        val topo2  = prefs.getString(context.getString(R.string.key_crossover_2_type), "0")
                         ?.toIntOrNull() ?: TOPO_LR2

        if (sampleRate <= 0f) return

        val fs = sampleRate.toDouble()
        configureCrossover(lp1a, lp1b, hp1a, hp1b, freq1.coerceIn(10.0, fs / 2 - 1), topo1, fs)
        if (mode == 3) {
            configureCrossover(lp2a, lp2b, hp2a, hp2b, freq2.coerceIn(10.0, fs / 2 - 1), topo2, fs)
        }
    }

    private fun configureCrossover(
        lpA: BiquadFilter, lpB: BiquadFilter,
        hpA: BiquadFilter, hpB: BiquadFilter,
        freqHz: Double, topo: Int, fs: Double
    ) {
        try {
            when (topo) {
                TOPO_LR2 -> {
                    lpA.configure(BiquadFilter.Type.LOW_PASS,  freqHz, 0.0, Q_LR2, fs)
                    hpA.configure(BiquadFilter.Type.HIGH_PASS, freqHz, 0.0, Q_LR2, fs)
                    lpB.bypass = true
                    hpB.bypass = true
                }
                TOPO_BW3 -> {
                    // 2nd-order stage (Q=1) + 1st-order stage (degenerate biquad b2=a2=0)
                    lpA.configure(BiquadFilter.Type.LOW_PASS,  freqHz, 0.0, Q_BW3, fs)
                    hpA.configure(BiquadFilter.Type.HIGH_PASS, freqHz, 0.0, Q_BW3, fs)
                    lpB.configureFirstOrder(isLowPass = true,  freqHz, fs)
                    hpB.configureFirstOrder(isLowPass = false, freqHz, fs)
                }
                TOPO_LR4 -> {
                    // Two identical 2nd-order stages in cascade
                    lpA.configure(BiquadFilter.Type.LOW_PASS,  freqHz, 0.0, Q_LR4, fs)
                    lpB.configure(BiquadFilter.Type.LOW_PASS,  freqHz, 0.0, Q_LR4, fs)
                    hpA.configure(BiquadFilter.Type.HIGH_PASS, freqHz, 0.0, Q_LR4, fs)
                    hpB.configure(BiquadFilter.Type.HIGH_PASS, freqHz, 0.0, Q_LR4, fs)
                }
                else -> {
                    lpA.configure(BiquadFilter.Type.LOW_PASS,  freqHz, 0.0, Q_LR2, fs)
                    hpA.configure(BiquadFilter.Type.HIGH_PASS, freqHz, 0.0, Q_LR2, fs)
                    lpB.bypass = true; hpB.bypass = true
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "CrossoverProcessor: failed to configure crossover at $freqHz Hz topo=$topo")
        }
    }

    fun process(buf: FloatArray, frames: Int) {
        if (!enabled) return
        lp1a.process(buf, frames); lp1b.process(buf, frames)
        hp1a.process(buf, frames); hp1b.process(buf, frames)
        if (mode == 3) {
            lp2a.process(buf, frames); lp2b.process(buf, frames)
            hp2a.process(buf, frames); hp2b.process(buf, frames)
        }
    }

    fun process(buf: ShortArray, frames: Int) {
        if (!enabled) return
        lp1a.process(buf, frames); lp1b.process(buf, frames)
        hp1a.process(buf, frames); hp1b.process(buf, frames)
        if (mode == 3) {
            lp2a.process(buf, frames); lp2b.process(buf, frames)
            hp2a.process(buf, frames); hp2b.process(buf, frames)
        }
    }
}
