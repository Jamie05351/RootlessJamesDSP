package me.timschneeberger.rootlessjamesdsp.dsp

import android.content.Context
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import timber.log.Timber

/**
 * Linkwitz-Riley 2nd-order (LR2) crossover processor.
 *
 * LR2 Lowpass  = 2nd-order Butterworth LPF at the crossover frequency (Q = 1/√2 ≈ 0.7071).
 * LR2 Highpass = 2nd-order Butterworth HPF at the crossover frequency (Q = 1/√2 ≈ 0.7071).
 *
 * Both filters use the RBJ-cookbook DF2-Transposed biquad ([BiquadFilter]).
 * The LP and HP outputs sum flat in amplitude when both are used simultaneously
 * (acoustic summation), which is the defining characteristic of an LR crossover.
 *
 * Preferences are read from the [Constants.PREF_CROSSOVER] shared-preferences namespace.
 */
class CrossoverProcessor(private val context: Context) {

    companion object {
        /** Butterworth Q for LR2 crossover (= 1/√2). */
        const val LR2_Q = 0.7071067811865476
    }

    private val lpFilter = BiquadFilter()
    private val hpFilter = BiquadFilter()

    var enabled: Boolean = false
    var lpEnabled: Boolean = false
    var hpEnabled: Boolean = false
    private var crossoverHz: Float = 1000f
    private var sampleRate: Float = 44100f

    /** Update sample rate and reconfigure filters. */
    fun setSampleRate(sr: Float) {
        if (sr <= 0f) return
        sampleRate = sr
        readAndApplyPreferences()
    }

    /** Read crossover preferences and rebuild filter coefficients. */
    fun readAndApplyPreferences() {
        val prefs = context.getSharedPreferences(Constants.PREF_CROSSOVER, Context.MODE_MULTI_PROCESS)

        enabled = prefs.getBoolean(context.getString(R.string.key_crossover_enable), false)
        lpEnabled = prefs.getBoolean(context.getString(R.string.key_crossover_lp_enable), true)
        hpEnabled = prefs.getBoolean(context.getString(R.string.key_crossover_hp_enable), true)
        crossoverHz = prefs.getFloat(context.getString(R.string.key_crossover_freq), 1000f)

        if (sampleRate <= 0f) return

        val safeFreq = crossoverHz.toDouble().coerceIn(10.0, (sampleRate / 2.0) - 1.0)

        try {
            // LR2 LP = 2nd-order Butterworth LPF (Q = 1/√2)
            lpFilter.bypass = !lpEnabled
            lpFilter.configure(
                BiquadFilter.Type.LOW_PASS,
                safeFreq,
                0.0,   // gain unused for LP
                LR2_Q,
                sampleRate.toDouble()
            )

            // LR2 HP = 2nd-order Butterworth HPF (Q = 1/√2)
            hpFilter.bypass = !hpEnabled
            hpFilter.configure(
                BiquadFilter.Type.HIGH_PASS,
                safeFreq,
                0.0,   // gain unused for HP
                LR2_Q,
                sampleRate.toDouble()
            )
        } catch (ex: Exception) {
            Timber.e(ex, "CrossoverProcessor: failed to configure filters")
        }
    }

    /** Process interleaved stereo float buffer in-place. */
    fun process(buf: FloatArray, frames: Int) {
        if (!enabled) return
        lpFilter.process(buf, frames)
        hpFilter.process(buf, frames)
    }

    /** Process interleaved stereo short buffer in-place. */
    fun process(buf: ShortArray, frames: Int) {
        if (!enabled) return
        lpFilter.process(buf, frames)
        hpFilter.process(buf, frames)
    }
}
