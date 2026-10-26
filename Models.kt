package com.medsel.mobilescope.data

import java.util.Locale

data class User(val name: String, val email: String, val photo: String?, val guest: Boolean)

data class DeviceInfo(
    val model: String, val android: String, val patch: String, val hardware: String,
    val abi: String, val cores: Int, val screen: String, val uptimeMs: Long
)

data class RamInfo(
    val total: Long, val avail: Long, val threshold: Long, val low: Boolean,
    val cached: Long, val swapTotal: Long, val swapFree: Long
) {
    val used get() = total - avail
    val usedFrac get() = if (total > 0) used.toFloat() / total else 0f
}

data class AppSize(val pkg: String, val label: String, val bytes: Long)

data class StorageInfo(
    val total: Long, val free: Long, val apps: Long, val data: Long, val cache: Long,
    val top: List<AppSize>, val perAppAccess: Boolean
) {
    val used get() = total - free
    val usedFrac get() = if (total > 0) used.toFloat() / total else 0f
}

data class BatteryInfo(
    val percent: Int, val status: String, val charging: Boolean, val plug: String,
    val tempC: Float, val voltageMv: Int, val health: String, val tech: String,
    val currentMa: Int?, val capacityMah: Int?, val cycles: Int?
)

data class Traffic(val rx: Long, val tx: Long) { val total get() = rx + tx }
data class AppTraffic(val pkg: String, val label: String, val rx: Long, val tx: Long) { val total get() = rx + tx }
data class Period(val label: String, val wifi: Traffic, val mobile: Traffic, val apps: List<AppTraffic>)
data class DayTraffic(val label: String, val wifi: Long, val mobile: Long)

data class NetReport(
    val type: String, val online: Boolean, val downKbps: Int, val upKbps: Int,
    val wifiRssi: Int?, val wifiLink: Int?, val boot: Traffic, val bootMobile: Traffic,
    val hasAccess: Boolean, val periods: List<Period>, val days: List<DayTraffic>
)

data class InstalledApp(val label: String, val pkg: String, val version: String, val installedAt: Long)
data class AppUsage(val pkg: String, val label: String, val fgMs: Long, val lastUsed: Long)
data class AppsReport(
    val total: Int, val user: Int, val system: Int, val hasAccess: Boolean,
    val screenMs: Long, val top: List<AppUsage>, val newest: List<InstalledApp>
)

data class ContactItem(val id: Long, val name: String, val number: String?)
data class ContactsReport(
    val total: Int, val withPhone: Int, val withEmail: Int, val starred: Int,
    val dupNumbers: Int, val favorites: List<ContactItem>
)

data class TopContact(val name: String, val number: String, val count: Int, val seconds: Long)
data class CallsReport(
    val total: Int, val incoming: Int, val outgoing: Int, val missed: Int, val rejected: Int,
    val inSec: Long, val outSec: Long, val longest: Long, val perDay: List<Int>, val top: List<TopContact>
)
data class SmsReport(
    val received: Int, val sent: Int, val charsReceived: Long, val charsSent: Long,
    val recvPerDay: List<Int>, val sentPerDay: List<Int>, val top: List<TopContact>
)

object Fmt {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b o"
        val units = arrayOf("Ko", "Mo", "Go", "To")
        var v = b.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(Locale.getDefault(), if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
    }

    fun duration(sec: Long): String {
        val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return when {
            d > 0 -> "$d j $h h"
            h > 0 -> "$h h ${m.toString().padStart(2, '0')} min"
            m > 0 -> "$m min ${s.toString().padStart(2, '0')} s"
            else -> "$s s"
        }
    }

    fun pct(f: Float) = "${(f * 100).toInt()} %"
}
