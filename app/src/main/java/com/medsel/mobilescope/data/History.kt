package com.medsel.mobilescope.data

import android.content.Context
import android.net.TrafficStats
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un relevé : pourcentages entiers, température en dixièmes de °C, compteurs réseau depuis le démarrage. */
data class Sample(
    val t: Long, val ram: Int, val storage: Int, val battery: Int, val tempX10: Int, val rx: Long, val tx: Long,
    val charging: Int = 0, val currentMa: Int = 0
)

object History {
    private const val KEY = "samples"
    private const val MAX = 800

    private fun sp(ctx: Context) = ctx.getSharedPreferences("history", Context.MODE_PRIVATE)

    @Synchronized
    fun load(ctx: Context): List<Sample> {
        val raw = sp(ctx).getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val r = a.getJSONArray(it)
                Sample(r.getLong(0), r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4), r.getLong(5), r.getLong(6), r.optInt(7, 0), r.optInt(8, 0))
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Ajoute un relevé si le dernier date de plus de [minGapMs] (ou si [force]). Retourne le relevé ajouté. */
    @Synchronized
    fun record(ctx: Context, force: Boolean = false, minGapMs: Long = 10 * 60_000L): Sample? {
        val all = load(ctx)
        val now = System.currentTimeMillis()
        if (!force && all.isNotEmpty() && now - all.last().t < minGapMs) return null
        val ram = Collectors.ram(ctx)
        val st = Collectors.storage(ctx, false)
        val bat = Collectors.battery(ctx)
        val s = Sample(
            now, (ram.usedFrac * 100).toInt(), (st.usedFrac * 100).toInt(), bat.percent, (bat.tempC * 10).toInt(),
            TrafficStats.getTotalRxBytes().coerceAtLeast(0), TrafficStats.getTotalTxBytes().coerceAtLeast(0),
            if (bat.charging) 1 else 0, bat.currentMa ?: 0
        )
        val next = (all + s).takeLast(MAX)
        val arr = JSONArray()
        next.forEach { arr.put(JSONArray(listOf(it.t, it.ram, it.storage, it.battery, it.tempX10, it.rx, it.tx, it.charging, it.currentMa))) }
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
        return s
    }

    /** Remplace tout l'historique (restauration d'une sauvegarde). */
    @Synchronized
    fun replaceAll(ctx: Context, samples: List<Sample>) {
        val arr = JSONArray()
        samples.takeLast(MAX).forEach { arr.put(JSONArray(listOf(it.t, it.ram, it.storage, it.battery, it.tempX10, it.rx, it.tx, it.charging, it.currentMa))) }
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun clear(ctx: Context) { sp(ctx).edit().remove(KEY).apply() }

    /** Trafic par jour déduit des relevés (les compteurs repartent de zéro au redémarrage). */
    fun dailyTraffic(samples: List<Sample>, days: Int): List<Pair<String, Long>> {
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        val m0 = Collectors.midnight(0)
        val sums = LongArray(days)
        for (i in 1 until samples.size) {
            val a = samples[i - 1]; val b = samples[i]
            val dRx = b.rx - a.rx; val dTx = b.tx - a.tx
            if (dRx < 0 || dTx < 0) continue
            val ago = if (b.t >= m0) 0 else (((m0 - b.t - 1) / 86_400_000L) + 1).toInt()
            if (ago < days) sums[days - 1 - ago] += dRx + dTx
        }
        return (days - 1 downTo 0).map { fmt.format(Date(Collectors.midnight(it))).take(3) to sums[days - 1 - it] }
    }

    fun csv(samples: List<Sample>): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return buildString {
            if (Fmt.english) appendLine("date;ram_pct;storage_pct;battery_pct;temperature_c;received_bytes;sent_bytes;charging;current_ma")
            else appendLine("date;ram_pct;stockage_pct;batterie_pct;temperature_c;recu_octets;envoye_octets;charge;courant_ma")
            samples.forEach {
                appendLine("${df.format(Date(it.t))};${it.ram};${it.storage};${it.battery};${it.tempX10 / 10.0};${it.rx};${it.tx};${it.charging};${it.currentMa}")
            }
        }
    }
}
