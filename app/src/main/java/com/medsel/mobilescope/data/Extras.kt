package com.medsel.mobilescope.data

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

data class CheckItem(val title: String, val ok: Boolean, val detail: String, val weight: Int)
data class SecurityReport(val checks: List<CheckItem>) {
    val score: Int
        get() {
            val total = checks.sumOf { it.weight }
            if (total == 0) return 0
            return checks.filter { it.ok }.sumOf { it.weight } * 100 / total
        }
}

data class HealthArea(val key: String, val label: String, val score: Int, val detail: String)
data class HealthReport(val score: Int, val areas: List<HealthArea>, val advice: List<String>)

data class CpuCore(val index: Int, val curKhz: Long?, val maxKhz: Long?)
data class CpuReport(val soc: String, val abi: String, val cores: List<CpuCore>, val governor: String?, val tempC: Float?)

data class DisplayReport(
    val resolution: String, val dpi: Int, val refreshHz: Float, val modes: List<Float>,
    val brightnessPct: Int?, val timeoutSec: Int?, val inches: Float?
)

data class WifiReport(
    val type: String, val connected: Boolean, val rssi: Int?, val linkMbps: Int?, val freqMhz: Int?,
    val channel: Int?, val standard: String?, val ip: String?, val dns: List<String>
)

data class SensorItem(val name: String, val typeName: String, val vendor: String, val maxRange: Float, val powerMa: Float)

data class LatencyTarget(val label: String, val host: String, val port: Int)
data class LatencyResult(val target: LatencyTarget, val samples: List<Long?>) {
    private val ok get() = samples.filterNotNull()
    val avg: Long? get() = if (ok.isEmpty()) null else ok.sum() / ok.size
    val min: Long? get() = ok.minOrNull()
    val max: Long? get() = ok.maxOrNull()
    val lossPct: Int get() = if (samples.isEmpty()) 0 else (samples.count { it == null } * 100 / samples.size)
}

object Extras {

    // ---------- sécurité ----------
    private fun patchAgeDays(): Long? = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(Build.VERSION.SECURITY_PATCH)
        if (d == null) null else (System.currentTimeMillis() - d.time) / 86_400_000L
    } catch (_: Exception) { null }

    private var secCache: Pair<Long, SecurityReport>? = null

    fun security(ctx: Context): SecurityReport {
        val c = secCache
        if (c != null && System.currentTimeMillis() - c.first < 60_000L) return c.second
        val r = buildSecurity(ctx)
        secCache = System.currentTimeMillis() to r
        return r
    }

    private fun buildSecurity(ctx: Context): SecurityReport {
        val list = ArrayList<CheckItem>()
        val kg = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val secure = kg.isDeviceSecure
        list.add(CheckItem("Verrouillage de l'écran", secure,
            if (secure) "Code, schéma ou biométrie actif" else "Aucun verrouillage sécurisé : activez un code", 3))

        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val enc = dpm.storageEncryptionStatus
        val encOk = enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
            enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER ||
            enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY
        list.add(CheckItem("Chiffrement du stockage", encOk,
            if (encOk) "Données chiffrées" else "Chiffrement non confirmé par le système", 2))

        val age = patchAgeDays()
        val patchOk = age != null && age <= 180
        list.add(CheckItem("Correctif de sécurité récent", patchOk,
            if (age == null) "Date inconnue" else "Daté du ${Build.VERSION.SECURITY_PATCH} (il y a $age jours)", 3))

        val dev = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)
        list.add(CheckItem("Options pour les développeurs", !dev,
            if (dev) "Activées : à désactiver si inutiles" else "Désactivées", 1))

        val adb = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)
        list.add(CheckItem("Débogage USB", !adb,
            if (adb) "Activé : risque si le téléphone est branché à un inconnu" else "Désactivé", 2))

        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk", "/data/local/xbin/su")
        val rooted = suPaths.any { File(it).exists() } || (Build.TAGS ?: "").contains("test-keys")
        list.add(CheckItem("Appareil non modifié (root)", !rooted,
            if (rooted) "Indices de modification détectés (heuristique)" else "Aucun indice de root (heuristique)", 3))

        val recent = Build.VERSION.SDK_INT >= 33
        list.add(CheckItem("Version d'Android récente", recent,
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", 1))

        val x = runCatching { Insights.securityExtras(ctx) }.getOrNull()
        if (x != null) {
            list.add(CheckItem("Services d'accessibilité", x.accessibility.isEmpty(),
                if (x.accessibility.isEmpty()) "Aucun service tiers actif" else "Actifs : " + x.accessibility.joinToString(", "), 2))
            list.add(CheckItem("Applications administrateur", x.admins.isEmpty(),
                if (x.admins.isEmpty()) "Aucune application administrateur" else "Administrateurs : " + x.admins.joinToString(", "), 2))
            list.add(CheckItem("Applications hors Play Store", x.sideloaded.size <= 3,
                if (x.sideloaded.isEmpty()) "Toutes viennent du Play Store" else "${x.sideloaded.size} application(s) : " + x.sideloaded.take(5).joinToString(", "), 1))
        }
        return SecurityReport(list)
    }

    // ---------- santé globale ----------
    fun health(ctx: Context): HealthReport {
        val ram = Collectors.ram(ctx)
        val st = Collectors.storage(ctx, false)
        val bat = Collectors.battery(ctx)
        val sec = security(ctx)
        val upDays = SystemClock.elapsedRealtime() / 86_400_000L

        val ramFree = (1f - ram.usedFrac) * 100f
        val ramScore = (ramFree * 2f).toInt().coerceIn(0, 100)
        val stFree = (1f - st.usedFrac) * 100f
        val stScore = (stFree * 3.3f).toInt().coerceIn(0, 100)
        var batScore = if (bat.health == "Bonne") 100 else 45
        if (bat.tempC >= 45f) batScore -= 40 else if (bat.tempC >= 40f) batScore -= 15
        val cyc = bat.cycles
        if (cyc != null) { if (cyc > 800) batScore -= 20 else if (cyc > 500) batScore -= 10 }
        batScore = batScore.coerceIn(0, 100)
        val secScore = sec.score

        val total = (ramScore * 20 + stScore * 25 + batScore * 25 + secScore * 30) / 100
        val areas = listOf(
            HealthArea("ram", "Mémoire vive", ramScore, "${ramFree.toInt()} % libres"),
            HealthArea("storage", "Stockage", stScore, "${stFree.toInt()} % libres (${Fmt.bytes(st.free)})"),
            HealthArea("battery", "Batterie", batScore, "Santé ${bat.health.lowercase(Locale.ROOT)}, ${"%.1f".format(Locale.US, bat.tempC)} °C"),
            HealthArea("security", "Sécurité", secScore, "${sec.checks.count { it.ok }} contrôles sur ${sec.checks.size} réussis")
        )
        val advice = ArrayList<String>()
        if (stFree < 15f) advice.add("Le stockage est presque plein : supprimez des vidéos, des téléchargements ou des applications inutilisées.")
        if (ramFree < 20f) advice.add("La mémoire vive est très sollicitée : fermez les applications en arrière-plan.")
        if (bat.tempC >= 40f) advice.add("La batterie est chaude (${"%.0f".format(Locale.US, bat.tempC)} °C) : évitez la charge sous le soleil ou avec une coque épaisse.")
        if (bat.health != "Bonne") advice.add("Android signale une batterie « ${bat.health.lowercase(Locale.ROOT)} » : un contrôle en atelier peut être utile.")
        if (upDays >= 14) advice.add("Le téléphone n'a pas redémarré depuis $upDays jours : un redémarrage libère de la mémoire.")
        for (c in sec.checks) if (!c.ok) advice.add("${c.title} : ${c.detail}.")
        if (advice.isEmpty()) advice.add("Tout est en ordre. Continuez ainsi !")
        return HealthReport(total, areas, advice)
    }

    // ---------- processeur ----------
    private fun readLong(path: String): Long? = try { File(path).readText().trim().toLongOrNull() } catch (_: Exception) { null }
    private fun readText(path: String): String? = try { File(path).readText().trim().ifBlank { null } } catch (_: Exception) { null }

    fun cpu(ctx: Context): CpuReport {
        val n = Runtime.getRuntime().availableProcessors()
        val cores = (0 until n).map {
            val base = "/sys/devices/system/cpu/cpu$it/cpufreq/"
            CpuCore(it, readLong(base + "scaling_cur_freq"), readLong(base + "cpuinfo_max_freq"))
        }
        var temp: Float? = null
        for (z in 0..20) {
            val raw = readLong("/sys/class/thermal/thermal_zone$z/temp") ?: continue
            val c = if (raw > 1000) raw / 1000f else raw.toFloat()
            if (c in 15f..110f) { temp = c; break }
        }
        val soc = if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL.isNotBlank() && Build.SOC_MODEL != Build.UNKNOWN)
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        return CpuReport(soc, Build.SUPPORTED_ABIS.joinToString(", "), cores,
            readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"), temp)
    }

    // ---------- écran ----------
    @Suppress("DEPRECATION")
    fun display(ctx: Context): DisplayReport {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val m = ctx.resources.displayMetrics
        val modes = d?.supportedModes?.map { Math.round(it.refreshRate * 10f) / 10f }?.distinct()?.sorted() ?: emptyList()
        val inches = if (m.xdpi > 0 && m.ydpi > 0) {
            val w = m.widthPixels / m.xdpi; val h = m.heightPixels / m.ydpi
            sqrt(w * w + h * h)
        } else null
        val bright = runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255 }.getOrNull()
        val timeout = runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000 }.getOrNull()
        return DisplayReport("${m.widthPixels} × ${m.heightPixels} px", m.densityDpi, d?.refreshRate ?: 0f, modes, bright, timeout, inches)
    }

    // ---------- Wi-Fi / IP ----------
    @Suppress("DEPRECATION")
    fun wifi(ctx: Context): WifiReport {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = if (net != null) cm.getNetworkCapabilities(net) else null
        val type = when {
            caps == null -> "Hors ligne"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Données mobiles"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Autre"
        }
        val lp = if (net != null) cm.getLinkProperties(net) else null
        val ip = lp?.linkAddresses?.map { it.address }?.firstOrNull { !it.isLoopbackAddress && it.address.size == 4 }?.hostAddress
        val dns = lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
        var rssi: Int? = null; var link: Int? = null; var freq: Int? = null; var std: String? = null
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            runCatching {
                val wi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
                if (wi.rssi > -127) rssi = wi.rssi
                if (wi.linkSpeed > 0) link = wi.linkSpeed
                if (wi.frequency > 0) freq = wi.frequency
                if (Build.VERSION.SDK_INT >= 30) {
                    std = when (wi.wifiStandard) {
                        1 -> "802.11a/b/g"
                        4 -> "Wi-Fi 4 (n)"
                        5 -> "Wi-Fi 5 (ac)"
                        6 -> "Wi-Fi 6 (ax)"
                        7 -> "802.11ad"
                        8 -> "Wi-Fi 7 (be)"
                        else -> null
                    }
                }
            }
        }
        val f = freq
        val channel = when {
            f == null -> null
            f in 2400..2500 -> (f - 2407) / 5
            f in 5000..5900 -> (f - 5000) / 5
            f in 5925..7125 -> (f - 5950) / 5
            else -> null
        }
        return WifiReport(type, caps != null, rssi, link, freq, channel, std, ip, dns)
    }

    // ---------- capteurs ----------
    private val sensorNames = mapOf(
        "accelerometer" to "Accéléromètre", "gyroscope" to "Gyroscope", "magnetic_field" to "Magnétomètre",
        "light" to "Luminosité", "proximity" to "Proximité", "pressure" to "Baromètre",
        "ambient_temperature" to "Température ambiante", "relative_humidity" to "Humidité",
        "step_counter" to "Compteur de pas", "step_detector" to "Détecteur de pas", "gravity" to "Gravité",
        "linear_acceleration" to "Accélération linéaire", "rotation_vector" to "Vecteur de rotation",
        "heart_rate" to "Rythme cardiaque", "significant_motion" to "Mouvement significatif"
    )

    fun sensors(ctx: Context): List<SensorItem> {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getSensorList(Sensor.TYPE_ALL).map {
            val key = it.stringType.substringAfterLast('.')
            SensorItem(it.name, sensorNames[key] ?: key.replace('_', ' '), it.vendor, it.maximumRange, it.power)
        }.sortedBy { it.typeName }
    }

    // ---------- latence réseau ----------
    fun latency(target: LatencyTarget, count: Int = 5, timeoutMs: Int = 2000): LatencyResult {
        val out = ArrayList<Long?>()
        repeat(count) {
            val s = Socket()
            val t0 = System.nanoTime()
            val v = try {
                s.connect(InetSocketAddress(target.host, target.port), timeoutMs)
                (System.nanoTime() - t0) / 1_000_000L
            } catch (_: Exception) { null }
            try { s.close() } catch (_: Exception) { }
            out.add(v)
            try { Thread.sleep(120) } catch (_: InterruptedException) { }
        }
        return LatencyResult(target, out)
    }

    // ---------- rapport texte ----------
    fun report(ctx: Context): String {
        val dev = Collectors.device(ctx)
        val ram = Collectors.ram(ctx)
        val st = Collectors.storage(ctx, false)
        val bat = Collectors.battery(ctx)
        val h = health(ctx)
        val sec = security(ctx)
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return buildString {
            appendLine("RAPPORT MOBILESCOPE — ${df.format(Date())}")
            appendLine("====================================")
            appendLine("Appareil : ${dev.model}")
            appendLine("Système  : ${dev.android}, correctif ${dev.patch}")
            appendLine("Processeur : ${dev.hardware}, ${dev.cores} cœurs (${dev.abi})")
            appendLine("Écran    : ${dev.screen}")
            appendLine("Allumé depuis : ${Fmt.duration(dev.uptimeMs / 1000)}")
            appendLine()
            appendLine("SCORE DE SANTÉ : ${h.score}/100")
            h.areas.forEach { appendLine(" - ${it.label} : ${it.score}/100 (${it.detail})") }
            appendLine()
            appendLine("MÉMOIRE : ${Fmt.bytes(ram.used)} utilisés sur ${Fmt.bytes(ram.total)} (${Fmt.pct(ram.usedFrac)})")
            appendLine("STOCKAGE : ${Fmt.bytes(st.used)} utilisés sur ${Fmt.bytes(st.total)} (${Fmt.pct(st.usedFrac)})")
            appendLine("BATTERIE : ${bat.percent} %, ${bat.status}, ${"%.1f".format(Locale.US, bat.tempC)} °C, santé ${bat.health}")
            appendLine()
            appendLine("SÉCURITÉ : ${sec.score}/100")
            sec.checks.forEach { appendLine(" [${if (it.ok) "OK" else "!!"}] ${it.title} — ${it.detail}") }
            appendLine()
            appendLine("Conseils :")
            h.advice.forEach { appendLine(" * $it") }
        }
    }
}
