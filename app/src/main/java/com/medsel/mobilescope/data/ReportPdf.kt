package com.medsel.mobilescope.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rapport PDF (A4) généré avec l'API système : aucune dépendance. */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    private fun paint(size: Float, color: Int, bold: Boolean = false): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size; p.color = color
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return p
    }

    private class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = 0f
        var n = 0
        fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            page = p; c = p.canvas; y = M
        }
        fun need(h: Float) { if (page == null || y + h > H - M) newPage() }
        fun finish() { page?.let { doc.finishPage(it) } }
    }

    private val ink = Color.parseColor("#0E1320")
    private val grey = Color.parseColor("#59647C")
    private val line = Color.parseColor("#DCE2EE")
    private val violet = Color.parseColor("#5B49E6")

    private fun heading(w: Writer, text: String) {
        w.need(34f)
        w.y += 10f
        w.c!!.drawText(Tr.t(text), M, w.y + 14f, paint(14f, violet, true))
        w.c!!.drawLine(M, w.y + 20f, W - M, w.y + 20f, Paint().also { it.color = line; it.strokeWidth = 1f })
        w.y += 30f
    }

    private fun textLine(w: Writer, text: String, size: Float = 10.5f, color: Int = ink, bold: Boolean = false) {
        val p = paint(size, color, bold)
        val t = Tr.t(text)
        // découpe simple à la largeur de la page
        var rest = t
        while (rest.isNotEmpty()) {
            var n = p.breakText(rest, true, W - 2 * M, null)
            if (n <= 0) n = rest.length
            if (n < rest.length) { val sp = rest.lastIndexOf(' ', n); if (sp > 10) n = sp }
            w.need(size + 5f)
            w.c!!.drawText(rest.substring(0, n).trim(), M, w.y + size, p)
            w.y += size + 5f
            rest = rest.substring(n).trim()
        }
    }

    private fun bar(w: Writer, label: String, value: Int, detail: String) {
        w.need(22f)
        val c = w.c!!
        c.drawText(Tr.t(label), M, w.y + 10f, paint(10f, ink, true))
        val x0 = M + 120f; val x1 = W - M - 150f
        c.drawRoundRect(RectF(x0, w.y + 2f, x1, w.y + 11f), 4f, 4f, Paint().also { it.color = Color.parseColor("#E7EBF3") })
        val col = when { value >= 80 -> "#6BA814"; value >= 55 -> "#E0A000"; else -> "#E04A4A" }
        c.drawRoundRect(RectF(x0, w.y + 2f, x0 + (x1 - x0) * value.coerceIn(0, 100) / 100f, w.y + 11f), 4f, 4f, Paint().also { it.color = Color.parseColor(col) })
        c.drawText("$value/100", x1 + 8f, w.y + 10f, paint(9.5f, ink, true))
        c.drawText(Tr.t(detail).take(34), x1 + 52f, w.y + 10f, paint(8f, grey))
        w.y += 20f
    }

    private fun chart(w: Writer, title: String, values: List<Float>, colorHex: String, lo: Float?, hi: Float?) {
        w.need(86f)
        val c = w.c!!
        c.drawText(Tr.t(title), M, w.y + 10f, paint(10f, ink, true))
        val top = w.y + 16f; val h = 56f; val x1 = W - M
        val grid = Paint().also { it.color = line; it.strokeWidth = 0.8f }
        for (k in 0..2) c.drawLine(M, top + h * k / 2f, x1, top + h * k / 2f, grid)
        if (values.size >= 2) {
            val mn = lo ?: values.min(); val mx = (hi ?: values.max()).let { if (it <= mn) mn + 1f else it }
            val path = Path()
            val dx = (x1 - M) / (values.size - 1)
            values.forEachIndexed { i, v ->
                val x = M + i * dx
                val y = top + h - ((v - mn) / (mx - mn)).coerceIn(0f, 1f) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val st = Paint(Paint.ANTI_ALIAS_FLAG)
            st.style = Paint.Style.STROKE; st.strokeWidth = 2f; st.color = Color.parseColor(colorHex)
            c.drawPath(path, st)
            c.drawText("%.0f".format(Locale.US, mx), x1 - 30f, top - 2f, paint(7.5f, grey))
        }
        w.y += 82f
    }

    fun build(ctx: Context): File {
        val dev = Collectors.device(ctx)
        val h = Extras.health(ctx)
        val sec = Extras.security(ctx)
        val ram = Collectors.ram(ctx)
        val st = Collectors.storage(ctx, false)
        val bat = Collectors.battery(ctx)
        val samples = History.load(ctx).filter { it.t >= System.currentTimeMillis() - 7 * 86_400_000L }
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val doc = PdfDocument()
        val w = Writer(doc)
        w.newPage()
        w.c!!.drawText("MobileScope", M, w.y + 22f, paint(24f, violet, true))
        w.y += 30f
        textLine(w, "Rapport de santé de l'appareil", 13f, ink, true)
        textLine(w, "${dev.model} · ${dev.android} · ${df.format(Date())}", 9.5f, grey)

        heading(w, "Score de santé")
        w.need(70f)
        val c = w.c!!
        val cx = M + 32f; val cy = w.y + 34f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.style = Paint.Style.STROKE; ring.strokeWidth = 8f; ring.color = Color.parseColor("#E7EBF3")
        c.drawCircle(cx, cy, 28f, ring)
        ring.color = when { h.score >= 80 -> Color.parseColor("#6BA814"); h.score >= 55 -> Color.parseColor("#E0A000"); else -> Color.parseColor("#E04A4A") }
        ring.strokeCap = Paint.Cap.ROUND
        c.drawArc(RectF(cx - 28f, cy - 28f, cx + 28f, cy + 28f), -90f, 360f * h.score / 100f, false, ring)
        val sp = paint(20f, ink, true); sp.textAlign = Paint.Align.CENTER
        c.drawText("${h.score}", cx, cy + 7f, sp)
        c.drawText(Tr.t(verdictText(h.score)), M + 80f, cy - 2f, paint(11f, ink, true))
        c.drawText(Tr.t("Calculé à partir de la mémoire, du stockage, de la batterie et de la sécurité."), M + 80f, cy + 13f, paint(8.5f, grey))
        w.y += 74f
        h.areas.forEach { bar(w, it.label, it.score, it.detail) }

        heading(w, "Ressources")
        textLine(w, "Mémoire : ${Fmt.bytes(ram.used)} utilisés sur ${Fmt.bytes(ram.total)} (${Fmt.pct(ram.usedFrac)})")
        textLine(w, "Stockage : ${Fmt.bytes(st.used)} utilisés sur ${Fmt.bytes(st.total)} (${Fmt.pct(st.usedFrac)})")
        textLine(w, "Batterie : ${bat.percent} %, ${bat.status}, ${"%.1f".format(Locale.US, bat.tempC)} °C, santé ${bat.health}")

        if (samples.size >= 2) {
            heading(w, "Évolution sur 7 jours")
            chart(w, "Mémoire vive utilisée (%)", samples.map { it.ram.toFloat() }, "#E0408A", 0f, 100f)
            chart(w, "Stockage utilisé (%)", samples.map { it.storage.toFloat() }, "#E0A000", 0f, 100f)
            chart(w, "Niveau de batterie (%)", samples.map { it.battery.toFloat() }, "#6BA814", 0f, 100f)
            chart(w, "Température de la batterie (°C)", samples.map { it.tempX10 / 10f }, "#E0702A", null, null)
        }

        val stm = Insights.screenTime(ctx, 14)
        if (stm.hasAccess) {
            heading(w, "Temps d'écran")
            textLine(w, "Cette semaine : ${Fmt.duration(stm.thisWeekMs / 1000)} · semaine précédente : ${Fmt.duration(stm.lastWeekMs / 1000)}")
        }
        val capB = Insights.capBytes(ctx)
        val used = Insights.mobileUsedSince(ctx, Insights.cycleStart(Prefs.capCycleDay(ctx)))
        if (used != null) {
            heading(w, "Données mobiles")
            textLine(w, if (capB > 0L) "Ce mois-ci : ${Fmt.bytes(used)} sur ${Fmt.bytes(capB)}" else "Ce mois-ci : ${Fmt.bytes(used)}")
        }

        heading(w, "Sécurité (${sec.score}/100)")
        sec.checks.forEach { textLine(w, (if (it.ok) "[OK] " else "[!] ") + it.title + " — " + it.detail, 9.5f, if (it.ok) ink else Color.parseColor("#B04040")) }

        heading(w, "Conseils")
        h.advice.forEach { textLine(w, "• $it", 10f) }
        w.finish()

        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "MobileScope-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun verdictText(score: Int): String = when {
        score >= 85 -> "Excellent état"
        score >= 70 -> "Bon état"
        score >= 55 -> "État correct, quelques points à surveiller"
        else -> "Attention : des actions sont recommandées"
    }
}
