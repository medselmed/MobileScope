package com.medsel.mobilescope.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import com.medsel.mobilescope.R
import com.medsel.mobilescope.data.Collectors
import com.medsel.mobilescope.data.Extras
import com.medsel.mobilescope.data.History
import com.medsel.mobilescope.data.Tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dessine les anneaux des widgets (RemoteViews ne sait pas dessiner : on fournit une image). */
object WidgetArt {
    fun ring(percent: Int, color: Int, sizePx: Int = 220, label: String = "$percent%"): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = sizePx * 0.11f
        val rect = RectF(stroke / 2, stroke / 2, sizePx - stroke / 2, sizePx - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFF2A3348.toInt()
        c.drawArc(rect, 0f, 360f, false, p)
        p.color = color
        c.drawArc(rect, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG)
        t.color = 0xFFEEF2F8.toInt()
        t.textAlign = Paint.Align.CENTER
        t.textSize = sizePx * 0.26f
        t.typeface = Typeface.DEFAULT_BOLD
        c.drawText(label, sizePx / 2f, sizePx / 2f + t.textSize * 0.35f, t)
        return bmp
    }

    fun scoreColor(score: Int): Int = when {
        score >= 80 -> 0xFF8FD11E.toInt()
        score >= 55 -> 0xFFFFB020.toInt()
        else -> 0xFFFF5C5C.toInt()
    }
}

abstract class BaseWidget : AppWidgetProvider() {
    abstract fun build(ctx: Context, ids: IntArray): RemoteViews

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Tr.apply(context)
        try { History.record(context) } catch (_: Exception) { }
        val rv = try {
            build(context, appWidgetIds)
        } catch (_: Exception) {
            return
        }
        appWidgetManager.updateAppWidget(appWidgetIds, rv)
    }

    protected fun launch(ctx: Context): PendingIntent? {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    protected fun refresh(ctx: Context, ids: IntArray): PendingIntent {
        val i = Intent(ctx, javaClass)
        i.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        return PendingIntent.getBroadcast(ctx, javaClass.name.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    protected fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

/** Widget « Santé » : score global + anneaux RAM, stockage, batterie. */
class HealthWidget : BaseWidget() {
    override fun build(ctx: Context, ids: IntArray): RemoteViews {
        val ram = Collectors.ram(ctx)
        val st = Collectors.storage(ctx, false)
        val bat = Collectors.battery(ctx)
        val h = Extras.health(ctx)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_health)
        rv.setTextViewText(R.id.w_score, h.score.toString())
        rv.setTextColor(R.id.w_score, WidgetArt.scoreColor(h.score))
        rv.setTextViewText(R.id.w_time, Tr.t("Mis à jour à ") + now())
        rv.setTextViewText(R.id.w_title, Tr.t("MobileScope · Santé"))
        rv.setTextViewText(R.id.w_lbl_ram, Tr.t("RAM"))
        rv.setTextViewText(R.id.w_lbl_storage, Tr.t("Stockage"))
        rv.setTextViewText(R.id.w_lbl_battery, Tr.t("Batterie"))
        rv.setImageViewBitmap(R.id.w_ring_ram, WidgetArt.ring((ram.usedFrac * 100).toInt(), 0xFFFF5CA8.toInt()))
        rv.setImageViewBitmap(R.id.w_ring_storage, WidgetArt.ring((st.usedFrac * 100).toInt(), 0xFFFFB020.toInt()))
        rv.setImageViewBitmap(R.id.w_ring_battery, WidgetArt.ring(bat.percent, if (bat.percent <= 15 && !bat.charging) 0xFFFF5C5C.toInt() else 0xFF8FD11E.toInt()))
        val go = launch(ctx)
        if (go != null) rv.setOnClickPendingIntent(R.id.w_root, go)
        rv.setOnClickPendingIntent(R.id.w_refresh, refresh(ctx, ids))
        return rv
    }
}

/** Widget « Batterie » : grand anneau + état, température, tension. */
class BatteryWidget : BaseWidget() {
    override fun build(ctx: Context, ids: IntArray): RemoteViews {
        val bat = Collectors.battery(ctx)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_battery)
        val color = when {
            bat.charging -> 0xFF2DD4F0.toInt()
            bat.percent <= 15 -> 0xFFFF5C5C.toInt()
            bat.percent <= 35 -> 0xFFFFB020.toInt()
            else -> 0xFF8FD11E.toInt()
        }
        rv.setImageViewBitmap(R.id.w_bat_ring, WidgetArt.ring(bat.percent, color, 260))
        rv.setTextViewText(R.id.w_bat_status, Tr.t(bat.status))
        rv.setTextViewText(R.id.w_bat_label, Tr.t("Batterie"))
        rv.setTextViewText(R.id.w_bat_detail, "%.1f °C · %d mV".format(Locale.US, bat.tempC, bat.voltageMv))
        rv.setTextViewText(R.id.w_bat_time, Tr.t("Mis à jour à ") + now())
        val go = launch(ctx)
        if (go != null) rv.setOnClickPendingIntent(R.id.w_bat_root, go)
        rv.setOnClickPendingIntent(R.id.w_bat_status, refresh(ctx, ids))
        return rv
    }
}
