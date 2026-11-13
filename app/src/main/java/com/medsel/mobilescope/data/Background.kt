package com.medsel.mobilescope.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.medsel.mobilescope.R
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Relevé périodique en arrière-plan (toutes les 30 min) + alertes optionnelles. */
class SampleWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        return try {
            Tr.apply(applicationContext)
            History.record(applicationContext)
            if (Prefs.alertsEnabled(applicationContext)) Alerts.check(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object Background {
    private const val NAME = "mobilescope_history"

    /** Aligne la planification sur les préférences (historique en arrière-plan activé ou non). */
    fun sync(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (Prefs.historyEnabled(ctx)) {
            val req = PeriodicWorkRequest.Builder(SampleWorker::class.java, 30, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(NAME)
        }
    }
}

object Alerts {
    private const val CHANNEL = "mobilescope_alerts"
    private const val GAP_MS = 6 * 3_600_000L

    private fun may(ctx: Context, key: String): Boolean {
        val sp = ctx.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong(key, 0L) < GAP_MS) return false
        sp.edit().putLong(key, now).apply()
        return true
    }

    fun check(ctx: Context) {
        val bat = Collectors.battery(ctx)
        val st = Collectors.storage(ctx, false)
        if (bat.tempC >= Prefs.alertTemp(ctx) && may(ctx, "temp")) {
            notify(ctx, 1, "Batterie chaude", "Température : ${"%.1f".format(Locale.US, bat.tempC)} °C. Débranchez le chargeur et laissez refroidir.")
        }
        val usedPct = (st.usedFrac * 100).toInt()
        if (usedPct >= Prefs.alertStorage(ctx) && may(ctx, "storage")) {
            notify(ctx, 2, "Stockage presque plein", "$usedPct % utilisés. Libérez de l'espace pour garder un téléphone fluide.")
        }
        if (!bat.charging && bat.percent <= Prefs.alertBattery(ctx) && may(ctx, "battery")) {
            notify(ctx, 3, "Batterie faible", "Il reste ${bat.percent} %. Pensez à brancher le chargeur.")
        }
    }

    fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, Tr.t("Alertes MobileScope"), NotificationManager.IMPORTANCE_DEFAULT))
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = if (launch != null) PendingIntent.getActivity(ctx, id, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) else null
        val b = android.app.Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(Tr.t(title))
            .setContentText(Tr.t(text))
            .setStyle(android.app.Notification.BigTextStyle().bigText(Tr.t(text)))
            .setAutoCancel(true)
        if (pi != null) b.setContentIntent(pi)
        nm.notify(id, b.build())
    }
}
