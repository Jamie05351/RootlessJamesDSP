package me.timschneeberger.rootlessjamesdsp.dsp

import android.content.Context
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import timber.log.Timber

/**
 * Global Parametric EQ processor — 6 independent biquad filter slots applied in series.
 *
 * All 6 filters act on the full waveform (no band splitting).
 * Preferences are read from the [Constants.PREF_PEQ] shared-preferences namespace.
 */
class ParametricEqProcessor(private val context: Context) {

    private val MAX_FILTERS = 6
    private val filters = Array(MAX_FILTERS) { BiquadFilter() }

    var enabled: Boolean = false
    private var sampleRate: Float = 44100f

    fun setSampleRate(sr: Float) {
        if (sr <= 0f) return
        sampleRate = sr
        readAndApplyPreferences()
    }

    fun readAndApplyPreferences() {
        val prefs = context.getSharedPreferences(Constants.PREF_PEQ, Context.MODE_MULTI_PROCESS)
        enabled = prefs.getBoolean(context.getString(R.string.key_peq_enable), false)

        for (i in 0 until MAX_FILTERS) {
            val n = i + 1
            val typeId = prefs.getString(
                context.getString(R.string.key_peq_filter_type, n), "0"
            )?.toIntOrNull() ?: 0
            val freq = prefs.getFloat(
                context.getString(R.string.key_peq_filter_freq, n), defaultFreq(n)
            ).toDouble()
            val gain = prefs.getFloat(
                context.getString(R.string.key_peq_filter_gain, n), 0f
            ).toDouble()
            val q = prefs.getFloat(
                context.getString(R.string.key_peq_filter_q, n), 1.0f
            ).toDouble()

            val filterType = BiquadFilter.Type.entries.firstOrNull { it.id == typeId }
                ?: BiquadFilter.Type.PEAK

            if (sampleRate > 0f) {
                try {
                    filters[i].bypass = false
                    filters[i].configure(
                        filterType,
                        freq.coerceIn(1.0, (sampleRate / 2.0) - 1.0),
                        gain,
                        q.coerceAtLeast(0.01),
                        sampleRate.toDouble()
                    )
                } catch (ex: Exception) {
                    Timber.e(ex, "ParametricEqProcessor: failed to configure filter $n")
                    filters[i].bypass = true
                }
            }
        }
    }

    fun process(buf: FloatArray, frames: Int) {
        if (!enabled) return
        for (f in filters) f.process(buf, frames)
    }

    fun process(buf: ShortArray, frames: Int) {
        if (!enabled) return
        for (f in filters) f.process(buf, frames)
    }

    private fun defaultFreq(n: Int) = when (n) {
        1 -> 100f; 2 -> 500f; 3 -> 1000f; 4 -> 3000f; 5 -> 8000f; else -> 16000f
    }
}
