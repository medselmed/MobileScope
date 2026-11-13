package com.medsel.mobilescope.data

import java.net.HttpURLConnection
import java.net.URL

/** Test de débit simple vers les points de mesure publics de Cloudflare. Retourne des Mb/s, ou null en cas d'échec. */
object Speed {
    private fun mbps(bytes: Long, nanos: Long): Float = if (nanos <= 0L) 0f else (bytes * 8f / 1_000_000f) / (nanos / 1_000_000_000f)

    fun download(bytes: Int = 15_000_000, onProgress: (Float) -> Unit = {}): Float? = try {
        val c = URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        var total = 0L
        val t0 = System.nanoTime()
        c.inputStream.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                onProgress(mbps(total, System.nanoTime() - t0))
            }
        }
        c.disconnect()
        mbps(total, System.nanoTime() - t0)
    } catch (_: Exception) { null }

    fun upload(bytes: Int = 5_000_000, onProgress: (Float) -> Unit = {}): Float? = try {
        val c = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 20000
        c.setFixedLengthStreamingMode(bytes)
        val buf = ByteArray(64 * 1024)
        var sent = 0L
        val t0 = System.nanoTime()
        c.outputStream.use { os ->
            while (sent < bytes) {
                val n = minOf(buf.size.toLong(), bytes - sent).toInt()
                os.write(buf, 0, n)
                sent += n
                onProgress(mbps(sent, System.nanoTime() - t0))
            }
        }
        c.responseCode
        c.disconnect()
        mbps(sent, System.nanoTime() - t0)
    } catch (_: Exception) { null }
}
