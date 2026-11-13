package com.medsel.mobilescope.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sauvegarde / restauration des préférences et de l'historique (le compte Google n'est jamais inclus). */
object Backup {
    private const val SETTINGS = "app_settings"

    fun export(ctx: Context): String {
        val root = JSONObject()
        root.put("app", "MobileScope")
        root.put("version", 1)
        val st = JSONObject()
        for ((k, v) in ctx.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).all) {
            val o = JSONObject()
            when (v) {
                is Boolean -> { o.put("t", "b"); o.put("v", v) }
                is Int -> { o.put("t", "i"); o.put("v", v) }
                is Long -> { o.put("t", "l"); o.put("v", v) }
                is Float -> { o.put("t", "f"); o.put("v", v.toDouble()) }
                is String -> { o.put("t", "s"); o.put("v", v) }
                is Set<*> -> { o.put("t", "set"); o.put("v", JSONArray(v.map { it.toString() })) }
                else -> continue
            }
            st.put(k, o)
        }
        root.put("settings", st)
        val hist = JSONArray()
        History.load(ctx).forEach {
            hist.put(JSONArray(listOf(it.t, it.ram, it.storage, it.battery, it.tempX10, it.rx, it.tx, it.charging, it.currentMa)))
        }
        root.put("history", hist)
        return root.toString(2)
    }

    /** Retourne le nombre de préférences et de relevés restaurés, ou null si le fichier n'est pas valide. */
    fun import(ctx: Context, text: String): Pair<Int, Int>? {
        return try {
            val root = JSONObject(text)
            if (root.optString("app") != "MobileScope") return null
            val st = root.getJSONObject("settings")
            val e = ctx.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
            var n = 0
            for (k in st.keys()) {
                val o = st.getJSONObject(k)
                when (o.getString("t")) {
                    "b" -> e.putBoolean(k, o.getBoolean("v"))
                    "i" -> e.putInt(k, o.getInt("v"))
                    "l" -> e.putLong(k, o.getLong("v"))
                    "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                    "s" -> e.putString(k, o.getString("v"))
                    "set" -> {
                        val a = o.getJSONArray("v")
                        e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                    else -> continue
                }
                n++
            }
            e.apply()
            val h = root.optJSONArray("history") ?: JSONArray()
            val samples = (0 until h.length()).map {
                val r = h.getJSONArray(it)
                Sample(r.getLong(0), r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4), r.getLong(5), r.getLong(6), r.optInt(7, 0), r.optInt(8, 0))
            }
            History.replaceAll(ctx, samples)
            n to samples.size
        } catch (_: Exception) { null }
    }
}
