package me.timschneeberger.rootlessjamesdsp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import me.timschneeberger.rootlessjamesdsp.utils.extensions.ContextExtensions.sendLocalBroadcast
import timber.log.Timber

class ExternalPeqUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        if (intent.action != ACTION_SET_PEQ) return

        val prefs = context.getSharedPreferences(Constants.PREF_PEQ, Context.MODE_MULTI_PROCESS)
        val editor = prefs.edit()

        val enabled = intent.getBooleanExtra(EXTRA_PEQ_ENABLED, true)
        val count = intent.getIntExtra(EXTRA_PEQ_COUNT, MAX_FILTERS).coerceIn(0, MAX_FILTERS)

        editor.putBoolean(context.getString(R.string.key_peq_enable), enabled)

        for (slot in 1..MAX_FILTERS) {
            if (slot <= count) {
                val type = intent.getIntExtra(extraType(slot), TYPE_PEAK).coerceIn(TYPE_PEAK, TYPE_BAND_PASS)
                val freq = intent.getFloatExtra(extraFreq(slot), defaultFreq(slot)).coerceIn(1f, 20000f)
                val gain = intent.getFloatExtra(extraGain(slot), 0f).coerceIn(-24f, 24f)
                val q = intent.getFloatExtra(extraQ(slot), 1f).coerceAtLeast(0.01f)

                editor.putString(context.getString(R.string.key_peq_filter_type, slot), type.toString())
                editor.putFloat(context.getString(R.string.key_peq_filter_freq, slot), freq)
                editor.putFloat(context.getString(R.string.key_peq_filter_gain, slot), gain)
                editor.putFloat(context.getString(R.string.key_peq_filter_q, slot), q)
            } else {
                editor.putString(context.getString(R.string.key_peq_filter_type, slot), TYPE_PEAK.toString())
                editor.putFloat(context.getString(R.string.key_peq_filter_freq, slot), defaultFreq(slot))
                editor.putFloat(context.getString(R.string.key_peq_filter_gain, slot), 0f)
                editor.putFloat(context.getString(R.string.key_peq_filter_q, slot), 1f)
            }
        }

        editor.apply()

        context.sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED))
        Timber.d("Applied external PEQ update: enabled=$enabled count=$count")
    }

    companion object {
        const val ACTION_SET_PEQ = "me.timschneeberger.rootlessjamesdsp.SET_PEQ"

        const val EXTRA_PEQ_ENABLED = "rootlessjamesdsp.peq.enabled"
        const val EXTRA_PEQ_COUNT = "rootlessjamesdsp.peq.count"

        private const val EXTRA_PREFIX = "rootlessjamesdsp.peq"
        private const val MAX_FILTERS = 6

        const val TYPE_PEAK = 0
        const val TYPE_LOW_SHELF = 1
        const val TYPE_HIGH_SHELF = 2
        const val TYPE_LOW_PASS = 3
        const val TYPE_HIGH_PASS = 4
        const val TYPE_ALL_PASS = 5
        const val TYPE_NOTCH = 6
        const val TYPE_BAND_PASS = 7

        fun extraType(slot: Int) = "$EXTRA_PREFIX.$slot.type"
        fun extraFreq(slot: Int) = "$EXTRA_PREFIX.$slot.freq"
        fun extraGain(slot: Int) = "$EXTRA_PREFIX.$slot.gain"
        fun extraQ(slot: Int) = "$EXTRA_PREFIX.$slot.q"

        private fun defaultFreq(slot: Int): Float = when (slot) {
            1 -> 100f
            2 -> 500f
            3 -> 1000f
            4 -> 3000f
            5 -> 8000f
            else -> 16000f
        }
    }
}
