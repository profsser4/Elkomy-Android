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

    private fun soap(ctrl: String, action: String, args: String): String? = try {
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
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val st = if (code in 200..299) c.inputStream else c.errorStream
        val r = st?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (code in 200..299) r else null
    } catch (_: Throwable) { null }

    private fun mime(url: String): String {
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

    /** يبعت الرابط للتلفزيون ويشغّله (ويكمّل من startSec). لازم يتنادى من thread خلفي. */
    fun start(d: Dev, url: String, title: String, startSec: Int): Boolean {
        soap(d.ctrl, "Stop", "")
        val didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${esc(title)}</dc:title>" +
            "<upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"http-get:*:${mime(url)}:*\">${esc(url)}</res></item></DIDL-Lite>"
        val ok = soap(d.ctrl, "SetAVTransportURI",
            "<CurrentURI>${esc(url)}</CurrentURI><CurrentURIMetaData>${esc(didl)}</CurrentURIMetaData>") != null
        if (!ok) return false
        soap(d.ctrl, "Play", "<Speed>1</Speed>")
        if (startSec > 5) {
            // التلفزيون محتاج ثواني يحمّل قبل ما يقبل Seek
            for (i in 0 until 10) {
                try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                if (soap(d.ctrl, "Seek", "<Unit>REL_TIME</Unit><Target>${hms(startSec.toLong())}</Target>") != null) break
            }
        }
        return true
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
