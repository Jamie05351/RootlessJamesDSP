package me.timschneeberger.rootlessjamesdsp.preference

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.model.GraphicEqNode
import me.timschneeberger.rootlessjamesdsp.model.GraphicEqNodeList
import me.timschneeberger.rootlessjamesdsp.view.GraphicEqualizerSurface
import kotlin.math.*

/**
 * A Preference that renders a live biquad filter frequency-response curve using the same
 * GraphicEqualizerSurface used by the arbitrary EQ.
 *
 * Callers supply a [FilterConfig] list via [setFilters]; the view redraws automatically.
 * It also watches the parent SharedPreferences and calls [onPreferencesChanged] so subclasses
 * can refresh their filter list when a related key changes.
 */
open class BiquadFrequencyResponsePreference : Preference, SharedPreferences.OnSharedPreferenceChangeListener {

    data class FilterConfig(
        val type: Int,      // 0=Peak 1=LowShelf 2=HighShelf 3=LowPass 4=HighPass 5=AllPass 6=Notch 7=BandPass
        val freq: Float,
        val gain: Float,
        val q: Float,
        val enabled: Boolean = true
    )

    private var surface: GraphicEqualizerSurface? = null
    private var filters: List<FilterConfig> = emptyList()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) :
            this(context, attrs, androidx.preference.R.attr.preferenceStyle)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes) {
        layoutResource = R.layout.preference_biquad_frequency_response
        isSelectable = false
    }

    override fun onAttached() {
        super.onAttached()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDetached() {
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDetached()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
        onPreferencesChanged(sp)
        surface?.let { refreshGraph() }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        surface = holder.itemView.findViewById(R.id.biquad_response_surface)
        sharedPreferences?.let { onPreferencesChanged(it) }
        refreshGraph()
    }

    /** Override to read filter configs from SharedPreferences when any preference changes. */
    open fun onPreferencesChanged(sp: SharedPreferences) {}

    fun setFilters(newFilters: List<FilterConfig>) {
        filters = newFilters
        refreshGraph()
    }

    private fun refreshGraph() {
        val view = surface ?: return
        val nodes = GraphicEqNodeList()
        val nPts = 256
        val fs = 48000.0
        val minFreq = 20.0
        val maxFreq = 20000.0

        for (i in 0 until nPts) {
            val t = i / (nPts - 1).toDouble()
            val freq = exp(t * (ln(maxFreq) - ln(minFreq)) + ln(minFreq))
            var totalDb = 0.0
            for (f in filters) {
                if (f.enabled) totalDb += biquadGainAt(f, freq, fs)
            }
            nodes.add(GraphicEqNode(freq, totalDb))
        }
        view.setNodes(nodes)
    }

    private fun biquadGainAt(f: FilterConfig, freq: Double, fs: Double): Double {
        val w = 2.0 * PI * freq / fs
        val cosW = cos(w)
        val sinW = sin(w)
        val A = 10.0.pow(f.gain / 40.0)
        val alpha = sinW / (2.0 * f.q)

        val (b0, b1, b2, a0, a1, a2) = when (f.type) {
            0 -> doubleArrayOf(         // Peak
                1 + alpha * A, -2 * cosW, 1 - alpha * A,
                1 + alpha / A, -2 * cosW, 1 - alpha / A
            )
            1 -> {                      // Low shelf
                val sA = sqrt(A)
                doubleArrayOf(
                    A * ((A + 1) - (A - 1) * cosW + 2 * sA * alpha),
                    2 * A * ((A - 1) - (A + 1) * cosW),
                    A * ((A + 1) - (A - 1) * cosW - 2 * sA * alpha),
                    (A + 1) + (A - 1) * cosW + 2 * sA * alpha,
                    -2 * ((A - 1) + (A + 1) * cosW),
                    (A + 1) + (A - 1) * cosW - 2 * sA * alpha
                )
            }
            2 -> {                      // High shelf
                val sA = sqrt(A)
                doubleArrayOf(
                    A * ((A + 1) + (A - 1) * cosW + 2 * sA * alpha),
                    -2 * A * ((A - 1) + (A + 1) * cosW),
                    A * ((A + 1) + (A - 1) * cosW - 2 * sA * alpha),
                    (A + 1) - (A - 1) * cosW + 2 * sA * alpha,
                    2 * ((A - 1) - (A + 1) * cosW),
                    (A + 1) - (A - 1) * cosW - 2 * sA * alpha
                )
            }
            3 -> doubleArrayOf(         // Low pass
                (1 - cosW) / 2, 1 - cosW, (1 - cosW) / 2,
                1 + alpha, -2 * cosW, 1 - alpha
            )
            4 -> doubleArrayOf(         // High pass
                (1 + cosW) / 2, -(1 + cosW), (1 + cosW) / 2,
                1 + alpha, -2 * cosW, 1 - alpha
            )
            5 -> doubleArrayOf(         // All pass
                1 - alpha, -2 * cosW, 1 + alpha,
                1 + alpha, -2 * cosW, 1 - alpha
            )
            6 -> doubleArrayOf(         // Notch
                1.0, -2 * cosW, 1.0,
                1 + alpha, -2 * cosW, 1 - alpha
            )
            7 -> doubleArrayOf(         // Band pass
                sinW / 2, 0.0, -sinW / 2,
                1 + alpha, -2 * cosW, 1 - alpha
            )
            else -> doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        }

        // Evaluate |H(e^jω)|² = |B(e^jω)|² / |A(e^jω)|²
        val bRe = b0 + b1 * cosW + b2 * cos(2 * w)
        val bIm = -(b1 * sinW + b2 * sin(2 * w))
        val aRe = a0 + a1 * cosW + a2 * cos(2 * w)
        val aIm = -(a1 * sinW + a2 * sin(2 * w))

        val aMag2 = aRe * aRe + aIm * aIm
        if (aMag2 == 0.0) return 0.0
        return 10.0 * log10((bRe * bRe + bIm * bIm) / aMag2)
    }

    private operator fun DoubleArray.component6() = this[5]
}
