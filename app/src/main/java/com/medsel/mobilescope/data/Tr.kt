package com.medsel.mobilescope.data

import android.content.Context
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Traduction légère. Le français est la langue source : en français, tout passe tel quel.
 * En anglais ou en arabe : correspondance exacte d'abord, puis règles de phrases à variables,
 * puis remplacement de fragments.
 */
object Tr {
    @Volatile var lang: String = "fr"
    private val cache = ConcurrentHashMap<String, String>()

    val isEnglish: Boolean get() = lang == "en"
    val isRtl: Boolean get() = lang == "ar"

    /** Applique la langue enregistrée (à appeler au démarrage, dans les widgets et le travail de fond). */
    fun apply(ctx: Context) = set(Prefs.lang(ctx))

    fun set(code: String) {
        val c = if (code == "en" || code == "ar") code else "fr"
        if (c != lang) cache.clear()
        lang = c
        Fmt.lang = c
        Locale.setDefault(
            when (c) {
                "en" -> Locale.ENGLISH
                "ar" -> Locale.forLanguageTag("ar-u-nu-latn")
                else -> Locale.FRENCH
            }
        )
    }

    private val fragEn: List<Pair<String, String>> by lazy { FRAGMENTS.sortedByDescending { it.first.length } }
    private val fragAr: List<Pair<String, String>> by lazy { FRAGMENTS_AR.sortedByDescending { it.first.length } }

    private const val BYTES_EN = "(?:Ko|Mo|Go|To|KB|MB|GB|TB|o|B)"
    private const val BYTES_AR = "(?:ك\\.ب|م\\.ب|غ\\.ب|ت\\.ب|ب)"

    private val anchoredEn: List<Pair<Regex, String>> = listOf(
        Regex("^Période : (.+) · (\\d+) appels\\.$") to "Period: \$1 · \$2 calls.",
        Regex("^Du (.+) au (.+) · (\\d+) jours\\.$") to "From \$1 to \$2 · \$3 days.",
        Regex("^Il reste (.+) sur (.+)\\.$") to "\$1 left out of \$2.",
        Regex("^([\\d.,]+ $BYTES_EN) sur ([\\d.,]+ $BYTES_EN)$") to "\$1 of \$2",
        Regex("^Ce mois-ci : (.+) sur (.+)$") to "This month: \$1 of \$2",
        Regex("^(.+) consommés sur (.+)\\.$") to "\$1 used of \$2."
    )

    private val anchoredAr: List<Pair<Regex, String>> = listOf(
        Regex("^Période : (.+) · (\\d+) appels\\.$") to "الفترة: \$1 · \$2 مكالمة.",
        Regex("^Du (.+) au (.+) · (\\d+) jours\\.$") to "من \$1 إلى \$2 · \$3 أيام.",
        Regex("^Il reste (.+) sur (.+)\\.$") to "المتبقي \$1 من \$2.",
        Regex("^([\\d.,]+ $BYTES_AR) sur ([\\d.,]+ $BYTES_AR)$") to "\$1 من \$2",
        Regex("^Ce mois-ci : (.+) sur (.+)$") to "هذا الشهر: \$1 من \$2",
        Regex("^(.+) consommés sur (.+)\\.$") to "\$1 مستهلكة من \$2."
    )

    private val postEn: List<Pair<Regex, String>> = listOf(
        Regex("\\b1 last minutes") to "1 last minute"
    )

    fun t(s: String): String {
        if (lang == "fr" || s.isEmpty()) return s
        cache[s]?.let { return it }
        val out = compute(s)
        if (cache.size < 4000) cache[s] = out
        return out
    }

    private fun compute(s: String): String {
        val ar = lang == "ar"
        (if (ar) EXACT_AR else EXACT)[s]?.let { return it }
        for ((r, rep) in (if (ar) anchoredAr else anchoredEn)) if (r.matches(s)) return r.replace(s, rep)
        var out = s
        for ((fr, tr) in (if (ar) fragAr else fragEn)) if (out.contains(fr)) out = out.replace(fr, tr)
        if (!ar) for ((r, rep) in postEn) out = r.replace(out, rep)
        return out
    }
}
