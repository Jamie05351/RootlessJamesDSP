package me.timschneeberger.rootlessjamesdsp.dsp

import android.content.Context
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import me.timschneeberger.rootlessjamesdsp.utils.preferences.Preferences
import timber.log.Timber

/**
 * Multi-band Parametric EQ processor.
 *
 * Supports 2-band or 3-band operation.  Each band is implemented as an independent
 * RBJ-cookbook DF2-Transposed biquad filter (see [BiquadFilter]).
 *
 * Preferences are read from the [Constants.PREF_PEQ] shared-preferences namespace.
 */
class ParametricEqProcessor(private val context: Context) {

    private val MAX_BANDS = 3
    private val filters = Array(MAX_BANDS) { BiquadFilter() }

    var enabled: Boolean = false
    var bandCount: Int = 2          // 2 = two-band, 3 = three-band
    private var sampleRate: Float = 44100f

    /** Update sample rate and reconfigure all bands. */
    fun setSampleRate(sr: Float) {
        if (sr <= 0f) return
        sampleRate = sr
        readAndApplyPreferences()
    }

    /**
     * Read the current PEQ preferences and push them into the biquad filter array.
     * Called whenever preferences change.
     */
    fun readAndApplyPreferences() {
        val prefs = context.getSharedPreferences(Constants.PREF_PEQ, Context.MODE_MULTI_PROCESS)

        enabled = prefs.getBoolean(context.getString(R.string.key_peq_enable), false)
        bandCount = prefs.getString(context.getString(R.string.key_peq_band_count), "2")?.toIntOrNull() ?: 2

        for (b in 0 until MAX_BANDS) {
            val bandIndex = b + 1
            val active = b < bandCount

            val typeId = prefs.getString(
                context.getString(R.string.key_peq_band_type, bandIndex), "0"
            )?.toIntOrNull() ?: 0
            val freq = prefs.getFloat(
                context.getString(R.string.key_peq_band_freq, bandIndex), 1000f
            ).toDouble()
            val gain = prefs.getFloat(
                context.getString(R.string.key_peq_band_gain, bandIndex), 0f
            ).toDouble()
            val q = prefs.getFloat(
                context.getString(R.string.key_peq_band_q, bandIndex), 1.0f
            ).toDouble()
            val polarity = prefs.getBoolean(
                context.getString(R.string.key_peq_band_polarity, bandIndex), false
            )

            val filterType = BiquadFilter.Type.entries.firstOrNull { it.id == typeId }
                ?: BiquadFilter.Type.PEAK

            filters[b].polarity = polarity
            filters[b].bypass = !active

            if (active && sampleRate > 0f) {
                val safeFreq = freq.coerceIn(1.0, (sampleRate / 2.0) - 1.0)
                val safeQ = q.coerceAtLeast(0.01)
                try {
                    filters[b].configure(filterType, safeFreq, gain, safeQ, sampleRate.toDouble())
                } catch (ex: Exception) {
                    Timber.e(ex, "ParametricEqProcessor: failed to configure band $bandIndex")
                    filters[b].bypass = true
                }
            }
        }
    }

    /** Process interleaved stereo float buffer in-place. */
    fun process(buf: FloatArray, frames: Int) {
        if (!enabled) return
        for (b in 0 until bandCount) {
            filters[b].process(buf, frames)
        }
    }

    /** Process interleaved stereo short buffer in-place. */
    fun process(buf: ShortArray, frames: Int) {
        if (!enabled) return
        for (b in 0 until bandCount) {
            filters[b].process(buf, frames)
        }
    }
}
