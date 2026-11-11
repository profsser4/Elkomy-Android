package com.elkomy.app

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL

/**
 * بث DLNA / UPnP (AVTransport): بيشتغل مع تلفزيونات سامسونج و LG وأغلب الشاشات الذكية
 * اللي على نفس الواي فاي. التلفزيون هو اللي بيفتح الرابط بنفسه، والموبايل بيبقى ريموت.
 */
object Dlna {
    class Dev(val name: String, val ctrl: String)

    private const val SVC = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val UA = "Mozilla/5.0 (Linux; Android 13) Elkomy-DLNA/2.0"

    /** آخر سبب فشل (للعرض/اللوج) — فاضي لو كله تمام */
    @Volatile var lastError: String = ""

    /** بيدوّر على الشاشات (٥ ثواني) ويبلّغ عن كل شاشة تتلاقي. onDone بيتنادى في الآخر. */
    fun discover(ctx: Context, onFound: (Dev) -> Unit, onDone: () -> Unit) {
        Thread {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = try {
                wm?.createMulticastLock("elkomy-cast")?.apply { setReferenceCounted(false); acquire() }
            } catch (_: Throwable) { null }
            val seen = HashSet<String>()
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                    soTimeout = 1000
                }
                val addr = InetAddress.getByName("239.255.255.250")
                val targets = arrayOf("urn:schemas-upnp-org:device:MediaRenderer:1", SVC)
                for (round in 0..1) for (st in targets) {
                    val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n").toByteArray()
                    sock.send(DatagramPacket(msg, msg.size, addr, 1900))
                }
                val end = System.currentTimeMillis() + 5000
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < end) {
                    val p = DatagramPacket(buf, buf.size)
                    try { sock.receive(p) } catch (_: SocketTimeoutException) { continue }
                    val txt = String(p.data, 0, p.length)
                    val loc = Regex("(?im)^location:\\s*(\\S+)").find(txt)?.groupValues?.get(1) ?: continue
                    if (!seen.add(loc)) continue
                    Thread { describe(loc)?.let(onFound) }.start()
                }
            } catch (_: Throwable) {
            } finally {
                try { sock?.close() } catch (_: Throwable) {}
                try { lock?.release() } catch (_: Throwable) {}
                onDone()
            }
        }.start()
    }

    private fun describe(loc: String): Dev? = try {
        val c = URL(loc).openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 4000
        val xml = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        if (!xml.contains("MediaRenderer")) null else {
            val name = Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1)?.trim() ?: "TV"
            val svc = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml).map { it.groupValues[1] }.firstOrNull { it.contains("AVTransport") }
            val ctrl = svc?.let {
                Regex("<controlURL>(.*?)</controlURL>", RegexOption.DOT_MATCHES_ALL)
                    .find(it)?.groupValues?.get(1)?.trim()
            }
            if (ctrl == null) null else Dev(unesc(name), URL(URL(loc), ctrl).toString())
        }
    } catch (_: Throwable) { null }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private class Resp(val code: Int, val body: String) { val ok get() = code in 200..299 }

    private fun upnpErr(b: String): String =
        Regex("<errorCode>(\\d+)</errorCode>").find(b)?.groupValues?.get(1)?.let { "UPnP $it" } ?: ""

    private fun soap(ctrl: String, action: String, args: String): String? {
        val r: Resp = try {
            val body = "<?xml version=\"1.0\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
                "<u:$action xmlns:u=\"$SVC\"><InstanceID>0</InstanceID>$args</u:$action>" +
                "</s:Body></s:Envelope>"
            val c = URL(ctrl).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 4000; c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            c.setRequestProperty("SOAPAction", "\"$SVC#$action\"")
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Connection", "close")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val st = if (code in 200..299) c.inputStream else c.errorStream
            val txt = st?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            Resp(code, txt)
        } catch (t: Throwable) {
            lastError = "$action: ${t.javaClass.simpleName}"
            return null
        }
        if (r.ok) return r.body
        lastError = "$action: HTTP ${r.code} ${upnpErr(r.body)}".trim()
        return null
    }

    // ---------- تجهيز الرابط ----------
    private class Resolved(val url: String, val type: String?)

    /** بيمشي ورا الـ redirects (روابط الديبريد/IPTV بتحوّل) عشان التلفزيون ياخد الرابط النهائي مباشرة */
    private fun resolve(url: String): Resolved {
        var cur = url
        var type: String? = null
        try {
            for (i in 0 until 6) {
                val c = URL(cur).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 5000; c.readTimeout = 6000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Range", "bytes=0-0")
                val code = c.responseCode
                val loc = c.getHeaderField("Location")
                type = c.contentType
                c.disconnect()
                if (code in 300..399 && !loc.isNullOrBlank()) { cur = URL(URL(cur), loc).toString(); continue }
                break
            }
        } catch (_: Throwable) {}
        return Resolved(cur, type?.substringBefore(';')?.trim()?.lowercase())
    }

    private fun mimeFor(url: String, serverType: String?): String {
        if (serverType != null && (serverType.startsWith("video/") || serverType.contains("mpegurl")))
            return if (serverType == "video/x-matroska" || serverType == "video/mp4" || serverType.startsWith("video/")) serverType else "application/vnd.apple.mpegurl"
        val p = url.substringBefore('?').lowercase()
        return when {
            p.endsWith(".mkv") -> "video/x-matroska"
            p.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            p.endsWith(".avi") -> "video/x-msvideo"
            p.endsWith(".ts") -> "video/mp2t"
            else -> "video/mp4"
        }
    }

    private fun hms(sec: Long): String {
        val s = maxOf(0L, sec)
        return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    private fun parseHms(t: String?): Long {
        if (t.isNullOrBlank() || t.contains("NOT_IMPLEMENTED")) return 0
        val p = t.trim().substringBefore('.').split(":")
        if (p.size != 3) return 0
        return try { p[0].toLong() * 3600 + p[1].toLong() * 60 + p[2].toLong() } catch (_: Throwable) { 0 }
    }

    private fun didl(url: String, title: String, mime: String) =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
        "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
        "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
        "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${esc(title)}</dc:title>" +
        "<upnp:class>object.item.videoItem</upnp:class>" +
        "<res protocolInfo=\"http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_CI=0;" +
        "DLNA.ORG_FLAGS=01700000000000000000000000000000\">${esc(url)}</res></item></DIDL-Lite>"

    private fun state(d: Dev): String? {
        val r = soap(d.ctrl, "GetTransportInfo", "") ?: return null
        return Regex("<CurrentTransportState>(.*?)</CurrentTransportState>").find(r)?.groupValues?.get(1)
    }

    /** يجرّب يبعت + يشغّل، ويتأكد إن التلفزيون فعلاً بدأ (مش بس قبل الأمر) */
    private fun setAndPlay(d: Dev, url: String, title: String, mime: String, withMeta: Boolean): Boolean {
        val meta = if (withMeta) esc(didl(url, title, mime)) else ""
        if (soap(d.ctrl, "SetAVTransportURI",
                "<CurrentURI>${esc(url)}</CurrentURI><CurrentURIMetaData>$meta</CurrentURIMetaData>") == null) return false
        soap(d.ctrl, "Play", "<Speed>1</Speed>")
        var last: String? = null
        for (i in 0 until 7) {
            try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
            last = state(d)
            if (last == null) return true // التلفزيون مبيدعمش GetTransportInfo — نعتبرها نجحت
            if (last == "PLAYING" || last == "TRANSITIONING" || last == "PAUSED_PLAYBACK") return true
        }
        lastError = "التلفزيون استلم الرابط بس مشغّلوش (${last ?: "?"})"
        return false
    }

    /** يبعت الرابط للتلفزيون ويشغّله (ويكمّل من startSec). لازم يتنادى من thread خلفي. */
    fun start(d: Dev, url: String, title: String, startSec: Int): Boolean {
        lastError = ""
        val res = resolve(url)
        val finalUrl = res.url
        val m1 = mimeFor(finalUrl, res.type)
        // (رابط، نوع، مع ميتاداتا؟) — بنجرّب كذا شكل لأن كل تلفزيون بيقبل شكل مختلف
        val attempts = ArrayList<Triple<String, String, Boolean>>()
        attempts += Triple(finalUrl, m1, true)
        if (m1 != "video/mp4") attempts += Triple(finalUrl, "video/mp4", true)
        attempts += Triple(finalUrl, m1, false)
        if (finalUrl != url) attempts += Triple(url, m1, true)

        soap(d.ctrl, "Stop", "")
        try { Thread.sleep(400) } catch (_: InterruptedException) {}
        for ((u, m, meta) in attempts) {
            if (!setAndPlay(d, u, title, m, meta)) continue
            if (startSec > 5) {
                for (i in 0 until 10) {
                    if (soap(d.ctrl, "Seek", "<Unit>REL_TIME</Unit><Target>${hms(startSec.toLong())}</Target>") != null) break
                    try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                }
            }
            lastError = ""
            return true
        }
        return false
    }

    fun resume(d: Dev) { soap(d.ctrl, "Play", "<Speed>1</Speed>") }
    fun pause(d: Dev) { soap(d.ctrl, "Pause", "") }
    fun stop(d: Dev) { soap(d.ctrl, "Stop", "") }
    fun seek(d: Dev, sec: Long) {
        soap(d.ctrl, "Seek", "<Unit>REL_TIME</Unit><Target>${hms(sec)}</Target>")
    }

    /** (الموضع, المدة) بالثواني، أو null لو التلفزيون مردّش */
    fun pos(d: Dev): Pair<Long, Long>? {
        val r = soap(d.ctrl, "GetPositionInfo", "") ?: return null
        val rel = Regex("<RelTime>(.*?)</RelTime>").find(r)?.groupValues?.get(1)
        val dur = Regex("<TrackDuration>(.*?)</TrackDuration>").find(r)?.groupValues?.get(1)
        return Pair(parseHms(rel), parseHms(dur))
    }
}
