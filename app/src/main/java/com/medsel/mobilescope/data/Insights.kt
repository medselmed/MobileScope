package com.medsel.mobilescope.data

import android.app.admin.DevicePolicyManager
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import java.io.File
import java.util.Calendar

// ---------------------------------------------------------------- modèles
data class PermApp(val pkg: String, val label: String)
data class PermGroup(val key: String, val label: String, val apps: List<PermApp>)

data class SecurityExtras(val accessibility: List<String>, val admins: List<String>, val sideloaded: List<String>)

data class CleanApp(val pkg: String, val label: String, val sizeBytes: Long, val cacheBytes: Long, val lastUsed: Long?, val system: Boolean)
data class CleanupReport(
    val hasAccess: Boolean, val totalCache: Long, val apps: List<CleanApp>,
    val images: Long, val videos: Long, val audio: Long, val otherExternal: Long, val categoriesOk: Boolean
)

data class ScreenTimeReport(val hasAccess: Boolean, val days: List<Pair<String, Long>>, val thisWeekMs: Long, val lastWeekMs: Long)

data class Autonomy(val drainPerHour: Float?, val hoursLeft: Float?, val chargePerHour: Float?, val hoursToFull: Float?)

object Insights {
    private const val DAY = 86_400_000L

    private fun label(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    @Suppress("DEPRECATION")
    private fun packagesWithPermissions(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

    // ---------------------------------------------------------------- permissions
    private val groups = listOf(
        Triple("camera", "Caméra", listOf("android.permission.CAMERA")),
        Triple("mic", "Micro", listOf("android.permission.RECORD_AUDIO")),
        Triple("location", "Position", listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION")),
        Triple("contacts", "Contacts", listOf("android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS")),
        Triple("sms", "SMS", listOf("android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS")),
        Triple("calls", "Journal d'appels", listOf("android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG")),
        Triple("phone", "Téléphone", listOf("android.permission.READ_PHONE_STATE", "android.permission.CALL_PHONE")),
        Triple("files", "Fichiers et médias", listOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO")),
        Triple("calendar", "Agenda", listOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR")),
        Triple("body", "Activité et capteurs corporels", listOf("android.permission.ACTIVITY_RECOGNITION", "android.permission.BODY_SENSORS"))
    )

    /** Pour chaque famille sensible : les applications installées par l'utilisateur qui ont déjà reçu l'accès. */
    fun permissionAudit(ctx: Context): List<PermGroup> {
        val pm = ctx.packageManager
        val result = HashMap<String, MutableList<PermApp>>()
        for (pi in packagesWithPermissions(pm)) {
            val ai = pi.applicationInfo ?: continue
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val req = pi.requestedPermissions ?: continue
            val flags = pi.requestedPermissionsFlags ?: continue
            for ((key, _, perms) in groups) {
                var granted = false
                for (i in req.indices) {
                    if (req[i] in perms && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) { granted = true; break }
                }
                if (granted) result.getOrPut(key) { ArrayList() }.add(PermApp(pi.packageName, label(pm, pi.packageName)))
            }
        }
        return groups.map { (key, lab, _) ->
            PermGroup(key, lab, (result[key] ?: emptyList<PermApp>()).sortedBy { it.label.lowercase() })
        }
    }

    // ---------------------------------------------------------------- sécurité étendue
    fun securityExtras(ctx: Context): SecurityExtras {
        val pm = ctx.packageManager
        val acc = ArrayList<String>()
        val raw = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull()
        if (!raw.isNullOrBlank()) {
            raw.split(':').forEach {
                val pkg = it.substringBefore('/')
                if (pkg.isNotBlank() && pkg != ctx.packageName) acc.add(label(pm, pkg))
            }
        }
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admins = (dpm.activeAdmins ?: emptyList()).map { label(pm, it.packageName) }
        val side = ArrayList<String>()
        for (ai in pm.getInstalledApplications(0)) {
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || ai.packageName == ctx.packageName) continue
            val installer = try {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(ai.packageName).installingPackageName
                else @Suppress("DEPRECATION") pm.getInstallerPackageName(ai.packageName)
            } catch (_: Exception) { null }
            if (installer != "com.android.vending") side.add(label(pm, ai.packageName))
        }
        return SecurityExtras(acc.distinct(), admins.distinct(), side.sortedBy { it.lowercase() })
    }

    // ---------------------------------------------------------------- nettoyage
    fun cleanup(ctx: Context): CleanupReport {
        val pm = ctx.packageManager
        val access = Collectors.hasUsageAccess(ctx)
        if (!access) return CleanupReport(false, 0, emptyList(), 0, 0, 0, 0, false)
        val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val usage = runCatching { usm.queryAndAggregateUsageStats(now - 180 * DAY, now) }.getOrDefault(emptyMap())
        val user = Process.myUserHandle()
        var cache = 0L
        val list = ArrayList<CleanApp>()
        for (ai in pm.getInstalledApplications(0)) {
            try {
                val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, user)
                cache += s.cacheBytes
                val last = usage[ai.packageName]?.lastTimeUsed?.takeIf { it > 0L }
                list.add(CleanApp(ai.packageName, label(pm, ai.packageName), s.appBytes + s.dataBytes + s.cacheBytes,
                    s.cacheBytes, last, (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0))
            } catch (_: Exception) { }
        }
        var img = 0L; var vid = 0L; var aud = 0L; var other = 0L; var ok = false
        try {
            val e = ssm.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, user)
            img = e.imageBytes; vid = e.videoBytes; aud = e.audioBytes
            other = (e.totalBytes - e.imageBytes - e.videoBytes - e.audioBytes).coerceAtLeast(0L)
            ok = true
        } catch (_: Exception) { }
        return CleanupReport(true, cache, list, img, vid, aud, other, ok)
    }

    /** Vide le cache propre à MobileScope (Android interdit de vider celui des autres applications). */
    fun clearOwnCache(ctx: Context): Long {
        var freed = 0L
        fun wipe(f: File?) {
            if (f == null || !f.exists()) return
            if (f.isDirectory) f.listFiles()?.forEach { wipe(it) }
            freed += if (f.isFile) f.length() else 0L
            f.delete()
        }
        ctx.cacheDir.listFiles()?.forEach { wipe(it) }
        ctx.externalCacheDir?.listFiles()?.forEach { wipe(it) }
        return freed
    }

    // ---------------------------------------------------------------- temps d'écran
    private fun daysAgo(t: Long, m0: Long): Int = if (t >= m0) 0 else (((m0 - t - 1) / DAY) + 1).toInt()

    fun screenTime(ctx: Context, days: Int = 14): ScreenTimeReport {
        val labels = Collectors.dayLabels(days)
        if (!Collectors.hasUsageAccess(ctx)) return ScreenTimeReport(false, labels.map { it to 0L }, 0, 0)
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val m0 = Collectors.midnight(0)
        val sums = LongArray(days)
        val list = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, Collectors.midnight(days - 1), System.currentTimeMillis())
        }.getOrNull() ?: emptyList()
        for (u in list) {
            val ago = daysAgo(u.firstTimeStamp, m0)
            if (ago in 0 until days) sums[days - 1 - ago] += u.totalTimeInForeground
        }
        val week = (days - 7 until days).sumOf { sums[it] }
        val prev = if (days >= 14) (days - 14 until days - 7).sumOf { sums[it] } else 0L
        return ScreenTimeReport(true, labels.indices.map { labels[it] to sums[it] }, week, prev)
    }

    // ---------------------------------------------------------------- forfait data
    fun capBytes(ctx: Context): Long = Prefs.dataCapMb(ctx) * (if (Fmt.decimal) 1_000_000L else 1_048_576L)

    fun cycleStart(day: Int): Long {
        val c = Calendar.getInstance()
        val d = day.coerceIn(1, 28)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.get(Calendar.DAY_OF_MONTH) < d) c.add(Calendar.MONTH, -1)
        c.set(Calendar.DAY_OF_MONTH, d)
        return c.timeInMillis
    }

    /** Octets (reçus + envoyés) en données mobiles depuis [startMs], ou null sans accès aux statistiques. */
    @Suppress("DEPRECATION")
    fun mobileUsedSince(ctx: Context, startMs: Long): Long? {
        if (!Collectors.hasUsageAccess(ctx)) return null
        return try {
            val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, startMs, System.currentTimeMillis())
            b.rxBytes + b.txBytes
        } catch (_: Exception) { null }
    }

    // ---------------------------------------------------------------- autonomie
    fun autonomy(samples: List<Sample>, percent: Int, charging: Boolean, currentMa: Int?, capacityMah: Int?): Autonomy {
        val recent = samples.filter { it.t >= System.currentTimeMillis() - 12 * 3_600_000L }
        var dropSum = 0f; var dropHours = 0f; var riseSum = 0f; var riseHours = 0f
        for (i in 1 until recent.size) {
            val a = recent[i - 1]; val b = recent[i]
            val h = (b.t - a.t) / 3_600_000f
            if (h <= 0f || h > 3f) continue
            if (a.charging == 0 && b.charging == 0 && b.battery < a.battery) { dropSum += (a.battery - b.battery); dropHours += h }
            if (a.charging == 1 && b.charging == 1 && b.battery > a.battery) { riseSum += (b.battery - a.battery); riseHours += h }
        }
        var drain = if (dropHours >= 0.5f) dropSum / dropHours else null
        var rise = if (riseHours >= 0.2f) riseSum / riseHours else null
        if (drain == null && !charging && currentMa != null && capacityMah != null && currentMa > 0 && capacityMah > 0) {
            drain = currentMa * 100f / capacityMah
        }
        if (rise == null && charging && currentMa != null && capacityMah != null && currentMa > 0 && capacityMah > 0) {
            rise = currentMa * 100f / capacityMah
        }
        val left = if (!charging && drain != null && drain > 0f) percent / drain else null
        val full = if (charging && rise != null && rise > 0f) (100 - percent).coerceAtLeast(0) / rise else null
        return Autonomy(drain, left, rise, full)
    }
}
