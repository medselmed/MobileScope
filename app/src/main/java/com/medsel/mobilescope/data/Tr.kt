package com.medsel.mobilescope.data

import android.content.Context
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Traduction légère FR -> EN. Le français est la langue source : en français, tout passe tel quel.
 * En anglais : correspondance exacte d'abord, puis remplacement des fragments (textes à variables).
 */
object Tr {
    @Volatile var lang: String = "fr"
    private val cache = ConcurrentHashMap<String, String>()

    val isEnglish: Boolean get() = lang == "en"

    /** Applique la langue enregistrée (à appeler au démarrage, dans les widgets et le travail de fond). */
    fun apply(ctx: Context) = set(Prefs.lang(ctx))

    fun set(code: String) {
        val c = if (code == "en") "en" else "fr"
        if (c != lang) cache.clear()
        lang = c
        Fmt.english = c == "en"
        Locale.setDefault(if (c == "en") Locale.ENGLISH else Locale.FRENCH)
    }

    private val fragments: List<Pair<String, String>> by lazy { FRAGMENTS.sortedByDescending { it.first.length } }

    /** Phrases à variables dont la structure change en anglais (appliquées avant les fragments). */
    private val anchored: List<Pair<Regex, String>> = listOf(
        Regex("""^Période : (.+) · (\d+) appels\.$""") to "Period: $1 · $2 calls.",
        Regex("""^Du (.+) au (.+) · (\d+) jours\.$""") to "From $1 to $2 · $3 days.",
        Regex("""^Il reste (.+) sur (.+)\.$""") to "$1 left out of $2."
    )

    private val post: List<Pair<Regex, String>> = listOf(
        Regex("""\b1 last minutes""") to "1 last minute"
    )

    fun t(s: String): String {
        if (lang != "en" || s.isEmpty()) return s
        cache[s]?.let { return it }
        val out = compute(s)
        if (cache.size < 4000) cache[s] = out
        return out
    }

    private fun compute(s: String): String {
        EXACT[s]?.let { return it }
        for ((r, rep) in anchored) if (r.matches(s)) return r.replace(s, rep)
        var out = s
        for ((fr, en) in fragments) if (out.contains(fr)) out = out.replace(fr, en)
        for ((r, rep) in post) out = r.replace(out, rep)
        return out
    }

    /** Pour les tests : l'exact ou les fragments ont-ils modifié la chaîne ? */
    fun covered(s: String): Boolean = EXACT.containsKey(s) || FRAGMENTS.any { s.contains(it.first) }
}
