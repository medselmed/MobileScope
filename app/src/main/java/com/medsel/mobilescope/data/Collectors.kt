package com.medsel.mobilescope.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Toutes les lectures système (à appeler hors du thread principal). */
@Suppress("DEPRECATION")
object Collectors {

    private const val DAY = 86_400_000L

    // ---------- utilitaires ----------
    fun midnight(daysAgo: Int = 0): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return c.timeInMillis
    }

    fun dayLabels(n: Int): List<String> {
        val f = SimpleDateFormat("EEE", Locale.getDefault())
        return (n - 1 downTo 0).map { f.format(Date(midnight(it))).take(3) }
    }

    private fun normalizeDay(timestamp: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    private fun nextDay(timestamp: Long): Long =
        Calendar.getInstance().apply { timeInMillis = timestamp; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

    private fun rangeDays(startMs: Long, endMs: Long): List<Long> {
        val start = normalizeDay(minOf(startMs, endMs))
        val end = normalizeDay(maxOf(startMs, endMs))
        val result = ArrayList<Long>()
        var current = start
        while (current <= end && result.size < 3661) {
            result.add(current)
            current = nextDay(current)
        }
        return result
    }

    private fun dayKey(timestamp: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(normalizeDay(timestamp)))

    fun labelsBetween(startMs: Long, endMs: Long): List<String> {
        val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
        return rangeDays(startMs, endMs).map { fmt.format(Date(it)) }
    }

    private fun daysAgo(t: Long, m0: Long): Int = if (t >= m0) 0 else (((m0 - t - 1) / DAY) + 1).toInt()

    private fun label(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private class Acc(val name: String) { var count = 0; var sec = 0L }

    private fun top(map: Map<String, Acc>): List<TopContact> =
        map.entries.sortedByDescending { it.value.count }.take(5)
            .map { TopContact(it.value.name, it.key, it.value.count, it.value.sec) }

    // ---------- appareil ----------
    fun device(ctx: Context): DeviceInfo {
        val dm = ctx.resources.displayMetrics
        return DeviceInfo(
            model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            patch = Build.VERSION.SECURITY_PATCH ?: "—",
            hardware = Build.HARDWARE,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "—",
            cores = Runtime.getRuntime().availableProcessors(),
            screen = "${dm.widthPixels}×${dm.heightPixels} px, ${dm.densityDpi} dpi",
            uptimeMs = SystemClock.elapsedRealtime()
        )
    }

    // ---------- RAM ----------
    fun ram(ctx: Context): RamInfo {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val m = HashMap<String, Long>()
        runCatching {
            File("/proc/meminfo").forEachLine { line ->
                val p = line.split(":")
                if (p.size == 2) m[p[0].trim()] = (p[1].trim().split(" ")[0].toLongOrNull() ?: 0L) * 1024L
            }
        }
        return RamInfo(mi.totalMem, mi.availMem, mi.threshold, mi.lowMemory,
            m["Cached"] ?: 0L, m["SwapTotal"] ?: 0L, m["SwapFree"] ?: 0L)
    }

    // ---------- stockage ----------
    fun storage(ctx: Context, withApps: Boolean): StorageInfo {
        val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val sf = StatFs(Environment.getDataDirectory().path)
        var total = sf.totalBytes
        var free = sf.availableBytes
        runCatching {
            total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
        }
        var apps = 0L; var data = 0L; var cache = 0L; var access = false
        val all = ArrayList<AppSize>()
        if (withApps && hasUsageAccess(ctx)) {
            val user = Process.myUserHandle()
            for (ai in ctx.packageManager.getInstalledApplications(0)) {
                try {
                    val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, user)
                    apps += s.appBytes; data += s.dataBytes; cache += s.cacheBytes
                    all.add(AppSize(ai.packageName, "", s.appBytes + s.dataBytes + s.cacheBytes))
                    access = true
                } catch (_: Exception) { }
            }
        }
        val pm = ctx.packageManager
        val best = all.sortedByDescending { it.bytes }.take(10).map { it.copy(label = label(pm, it.pkg)) }
        return StorageInfo(total, free, apps, data, cache, best, access)
    }

    // ---------- batterie ----------
    fun battery(ctx: Context): BatteryInfo {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val status = when (st) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "En charge"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Décharge"
            BatteryManager.BATTERY_STATUS_FULL -> "Pleine"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Ne charge pas"
            else -> "Inconnu"
        }
        val plug = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Secteur"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Sans fil"
            else -> "Aucun"
        }
        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Bonne"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surchauffe"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Défectueuse"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Surtension"
            BatteryManager.BATTERY_HEALTH_COLD -> "Trop froide"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Défaillance"
            else -> "Inconnue"
        }
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // La plupart des appareils donnent des µA ; certains des mA.
        val ma = if (raw == Int.MIN_VALUE || raw == 0) null else if (abs(raw) > 20_000) abs(raw) / 1000 else abs(raw)
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val cap = if (counter > 0 && pct > 0) (counter / 1000L * 100 / pct).toInt() else null
        val cycles = (i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1).takeIf { it > 0 }
        return BatteryInfo(
            pct, status, charging, plug,
            (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            health, i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—",
            ma, cap, cycles
        )
    }

    // ---------- réseau ----------
    private fun summary(nsm: NetworkStatsManager, type: Int, s: Long, e: Long): Traffic = try {
        val b = nsm.querySummaryForDevice(type, null, s, e)
        Traffic(b.rxBytes, b.txBytes)
    } catch (_: Exception) { Traffic(0, 0) }

    private fun topApps(ctx: Context, nsm: NetworkStatsManager, s: Long, e: Long): List<AppTraffic> {
        val rx = HashMap<Int, Long>(); val tx = HashMap<Int, Long>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val st = nsm.querySummary(type, null, s, e)
                val b = NetworkStats.Bucket()
                while (st.hasNextBucket()) {
                    st.getNextBucket(b)
                    rx[b.uid] = (rx[b.uid] ?: 0L) + b.rxBytes
                    tx[b.uid] = (tx[b.uid] ?: 0L) + b.txBytes
                }
                st.close()
            } catch (_: Exception) { }
        }
        val pm = ctx.packageManager
        return rx.keys.union(tx.keys).map { uid ->
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull()
            val name = when {
                uid == NetworkStats.Bucket.UID_REMOVED -> "Apps désinstallées"
                uid == NetworkStats.Bucket.UID_TETHERING -> "Partage de connexion"
                uid == 0 || uid == 1000 -> "Système Android"
                pkg != null -> label(pm, pkg)
                else -> "UID $uid"
            }
            AppTraffic(pkg ?: "", name, rx[uid] ?: 0L, tx[uid] ?: 0L)
        }.filter { it.total > 0 }.sortedByDescending { it.total }.take(10)
    }

    fun network(ctx: Context, startDateMs: Long = midnight(6), endDateMs: Long = System.currentTimeMillis()): NetReport {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val type = when {
            caps == null -> "Hors ligne"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Données mobiles"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Autre"
        }
        var rssi: Int? = null; var link: Int? = null
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            runCatching {
                val wi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
                if (wi.rssi > -127) rssi = wi.rssi
                if (wi.linkSpeed > 0) link = wi.linkSpeed
            }
        }
        val boot = Traffic(TrafficStats.getTotalRxBytes().coerceAtLeast(0), TrafficStats.getTotalTxBytes().coerceAtLeast(0))
        val bootM = Traffic(TrafficStats.getMobileRxBytes().coerceAtLeast(0), TrafficStats.getMobileTxBytes().coerceAtLeast(0))
        val access = hasUsageAccess(ctx)
        var periods = emptyList<Period>()
        var days = emptyList<DayTraffic>()
        if (access) {
            val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val now = System.currentTimeMillis()
            val selectedStart = normalizeDay(minOf(startDateMs, endDateMs))
            val selectedEnd = normalizeDay(maxOf(startDateMs, endDateMs))
            val selectedEndExclusive = nextDay(selectedEnd)
            periods = listOf(Period(
                "Période sélectionnée",
                summary(nsm, ConnectivityManager.TYPE_WIFI, selectedStart, selectedEndExclusive),
                summary(nsm, ConnectivityManager.TYPE_MOBILE, selectedStart, selectedEndExclusive),
                topApps(ctx, nsm, selectedStart, selectedEndExclusive)
            ))
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            days = rangeDays(selectedStart, selectedEnd).map { d ->
                val e = nextDay(d).coerceAtMost(selectedEndExclusive)
                DayTraffic(fmt.format(Date(d)),
                    summary(nsm, ConnectivityManager.TYPE_WIFI, d, e).total,
                    summary(nsm, ConnectivityManager.TYPE_MOBILE, d, e).total)
            }
        }
        return NetReport(type, caps != null, caps?.linkDownstreamBandwidthKbps ?: 0, caps?.linkUpstreamBandwidthKbps ?: 0,
            rssi, link, boot, bootM, access, periods, days)
    }

    // ---------- applications ----------
    fun apps(ctx: Context, startDateMs: Long = midnight(6), endDateMs: Long = System.currentTimeMillis()): AppsReport {
        val pm = ctx.packageManager
        val all = pm.getInstalledApplications(0)
        val sys = all.count { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
        val newest = all.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }.mapNotNull { ai ->
            try {
                val pi = pm.getPackageInfo(ai.packageName, 0)
                InstalledApp(pm.getApplicationLabel(ai).toString(), ai.packageName, pi.versionName ?: "—", pi.firstInstallTime)
            } catch (_: Exception) { null }
        }.sortedByDescending { it.installedAt }.take(6)
        val access = hasUsageAccess(ctx)
        var screen = 0L
        val top = ArrayList<AppUsage>()
        if (access) {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStart = normalizeDay(minOf(startDateMs, endDateMs))
            val usageEnd = nextDay(normalizeDay(maxOf(startDateMs, endDateMs)))
            val map = usm.queryAndAggregateUsageStats(usageStart, usageEnd)
            for ((pkg, st) in map) {
                val fg = st.totalTimeInForeground
                if (fg > 0) { screen += fg; top.add(AppUsage(pkg, label(pm, pkg), fg, st.lastTimeUsed)) }
            }
            top.sortByDescending { it.fgMs }
        }
        return AppsReport(all.size, all.size - sys, sys, access, screen, top.take(10), newest)
    }

    // ---------- contacts ----------
    fun contacts(ctx: Context): ContactsReport {
        val cr = ctx.contentResolver
        var total = 0
        var withPhone = 0
        var starred = 0
        val rows = ArrayList<Triple<Long, String, Boolean>>()
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.STARRED, ContactsContract.Contacts.HAS_PHONE_NUMBER),
            null, null, ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                total++
                val id = c.getLong(0)
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Contact sans nom"
                val isStarred = c.getInt(2) == 1
                if (c.getInt(3) > 0) withPhone++
                if (isStarred) starred++
                rows.add(Triple(id, name, isStarred))
            }
        }
        val emails = HashSet<Long>()
        cr.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID), null, null, null
        )?.use { c -> while (c.moveToNext()) emails.add(c.getLong(0)) }

        val byNumber = HashMap<String, HashSet<Long>>()
        val firstNumber = HashMap<Long, String>()
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(1) ?: continue
                if (!firstNumber.containsKey(id)) firstNumber[id] = number
                val key = number.filter { it.isDigit() }.takeLast(9)
                if (key.length >= 6) byNumber.getOrPut(key) { HashSet() }.add(id)
            }
        }
        val duplicateIds = byNumber.values.filter { it.size > 1 }.flatten().toSet()
        val contacts = rows.map { (id, name, isStarred) ->
            ContactItem(id, name, firstNumber[id], id in emails, isStarred, id in duplicateIds)
        }
        return ContactsReport(
            total, withPhone, emails.size, starred, byNumber.count { it.value.size > 1 },
            contacts.filter { it.starred }.take(15), contacts
        )
    }

    private fun lookupContactName(ctx: Context, number: String): String? = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        ctx.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
    }.getOrNull()

    // ---------- appels ----------
    fun calls(ctx: Context, startDateMs: Long = midnight(6), endDateMs: Long = System.currentTimeMillis()): CallsReport {
        val days = rangeDays(startDateMs, endDateMs)
        val start = days.first()
        val endExclusive = nextDay(days.last())
        val labels = days.map { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it)) }
        val dayIndices = days.mapIndexed { index, value -> dayKey(value) to index }.toMap()
        var inc = 0; var out = 0; var miss = 0; var rej = 0
        var inSec = 0L; var outSec = 0L; var longest = 0L
        val perDay = IntArray(days.size)
        val per = HashMap<String, Acc>()
        val resolvedNames = HashMap<String, String>()
        val missedCalls = ArrayList<CallEntry>()
        val rejectedCalls = ArrayList<CallEntry>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME),
            CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE + " < ?",
            arrayOf(start.toString(), endExclusive.toString()),
            CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val num = c.getString(0) ?: ""
                val type = c.getInt(1); val date = c.getLong(2); val dur = c.getLong(3)
                val cachedName = c.getString(4)
                val displayName = if (num.isBlank()) "Numéro masqué" else
                    cachedName?.takeIf { it.isNotBlank() } ?: resolvedNames.getOrPut(num) { lookupContactName(ctx, num) ?: num }
                when (type) {
                    CallLog.Calls.INCOMING_TYPE -> { inc++; inSec += dur }
                    CallLog.Calls.OUTGOING_TYPE -> { out++; outSec += dur }
                    CallLog.Calls.MISSED_TYPE -> {
                        miss++
                        missedCalls.add(CallEntry(num, displayName, type, date, dur))
                    }
                    CallLog.Calls.REJECTED_TYPE -> {
                        rej++
                        rejectedCalls.add(CallEntry(num, displayName, type, date, dur))
                    }
                }
                if (dur > longest) longest = dur
                dayIndices[dayKey(date)]?.let { perDay[it]++ }
                if (num.isNotBlank()) {
                    val e = per.getOrPut(num) { Acc(displayName) }
                    e.count++; e.sec += dur
                }
            }
        }
        return CallsReport(
            inc + out + miss + rej, inc, out, miss, rej, inSec, outSec, longest,
            perDay.toList(), top(per), labels, missedCalls.take(300), rejectedCalls.take(300)
        )
    }

    // ---------- SMS ----------
    fun sms(ctx: Context, startDateMs: Long = midnight(6), endDateMs: Long = System.currentTimeMillis()): SmsReport {
        val days = rangeDays(startDateMs, endDateMs)
        val start = days.first()
        val endExclusive = nextDay(days.last())
        val labels = days.map { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it)) }
        val dayIndices = days.mapIndexed { index, value -> dayKey(value) to index }.toMap()
        var rec = 0; var sent = 0; var cr = 0L; var cs = 0L
        val rd = IntArray(days.size); val sd = IntArray(days.size)
        val per = HashMap<String, Acc>()
        val resolvedNames = HashMap<String, String>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.TYPE, Telephony.Sms.DATE, Telephony.Sms.BODY),
            Telephony.Sms.DATE + " >= ? AND " + Telephony.Sms.DATE + " < ?",
            arrayOf(start.toString(), endExclusive.toString()),
            Telephony.Sms.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(0) ?: ""
                val type = c.getInt(1); val date = c.getLong(2)
                val len = (c.getString(3) ?: "").length.toLong()
                val dayIndex = dayIndices[dayKey(date)]
                when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> {
                        rec++; cr += len
                        if (dayIndex != null) rd[dayIndex]++
                    }
                    Telephony.Sms.MESSAGE_TYPE_SENT -> {
                        sent++; cs += len
                        if (dayIndex != null) sd[dayIndex]++
                    }
                    else -> continue
                }
                if (addr.isNotBlank()) {
                    val displayName = resolvedNames.getOrPut(addr) { lookupContactName(ctx, addr) ?: addr }
                    per.getOrPut(addr) { Acc(displayName) }.count++
                }
            }
        }
        return SmsReport(rec, sent, cr, cs, rd.toList(), sd.toList(), top(per), labels)
    }
}
