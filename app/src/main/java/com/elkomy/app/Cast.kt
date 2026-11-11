package com.elkomy.app

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors

/**
 * بث DLNA / UPnP (AVTransport) لتلفزيونات سامسونج و LG وأغلب الشاشات الذكية على نفس الواي فاي.
 *
 * الحل الجذري: التلفزيون مبيفتحش رابط الديبريد/TorBox بنفسه (https، redirects، HLS، headers).
 * فالموبايل بيشغّل سيرفر صغير (Proxy) والتلفزيون بيفتح رابط http محلي من الموبايل،
 * والموبايل بيجيب الفيديو من المصدر ويمرّره (مع Range للتقديم والتأخير).
 * لازم التطبيق يفضل مفتوح والموبايل على نفس الواي فاي طول البث.
 */
object Dlna {
    class Dev(val name: String, val ctrl: String)

    private const val SVC = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val UA = "Mozilla/5.0 (Linux; Android 13) Elkomy-DLNA/3.0"
    private const val FLAGS = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    /** آخر سبب فشل (للعرض) — فاضي لو كله تمام */
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

    // ---------------- SOAP ----------------
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

    // ---------------- أدوات ----------------
    private class Resolved(val url: String, val type: String?)

    /** بيمشي ورا الـ redirects عشان نعرف النوع الحقيقي والرابط النهائي */
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

    private fun mimeFor(url: String, st: String?): String {
        val p = url.substringBefore('?').lowercase()
        if (p.endsWith(".m3u8") || url.contains("m3u8", true) || (st != null && st.contains("mpegurl")))
            return "application/vnd.apple.mpegurl"
        if (st != null && st.startsWith("video/")) return st
        return when {
            p.endsWith(".mkv") -> "video/x-matroska"
            p.endsWith(".avi") -> "video/x-msvideo"
            p.endsWith(".ts") -> "video/mp2t"
            else -> "video/mp4"
        }
    }

    private fun extFor(mime: String) = when {
        mime.contains("mpegurl") -> "m3u8"
        mime.contains("matroska") -> "mkv"
        mime == "video/mp2t" -> "ts"
        mime.contains("msvideo") -> "avi"
        else -> "mp4"
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

    private fun localIp(d: Dev): String? = try {
        val host = URL(d.ctrl).host
        DatagramSocket().use { s -> s.connect(InetAddress.getByName(host), 9); s.localAddress.hostAddress }
    } catch (_: Throwable) { null }

    // ---------------- إرسال للتلفزيون ----------------
    private fun didl(url: String, title: String, mime: String): String {
        val pi = if (mime.contains("mpegurl")) "http-get:*:$mime:*" else "http-get:*:$mime:$FLAGS"
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${esc(title)}</dc:title>" +
            "<upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"$pi\">${esc(url)}</res></item></DIDL-Lite>"
    }

    private fun state(d: Dev): String? {
        val r = soap(d.ctrl, "GetTransportInfo", "") ?: return null
        return Regex("<CurrentTransportState>(.*?)</CurrentTransportState>").find(r)?.groupValues?.get(1)
    }

    private fun setAndPlay(d: Dev, url: String, title: String, mime: String, withMeta: Boolean): Boolean {
        val meta = if (withMeta) esc(didl(url, title, mime)) else ""
        if (soap(d.ctrl, "SetAVTransportURI",
                "<CurrentURI>${esc(url)}</CurrentURI><CurrentURIMetaData>$meta</CurrentURIMetaData>") == null) return false
        soap(d.ctrl, "Play", "<Speed>1</Speed>")
        var last: String? = null
        for (i in 0 until 8) {
            try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
            last = state(d)
            if (last == null) return true
            if (last == "PLAYING" || last == "TRANSITIONING" || last == "PAUSED_PLAYBACK") return true
        }
        lastError = "التلفزيون استلم الرابط بس مشغّلوش (${last ?: "?"})"
        return false
    }

    /** يبعت الفيديو للتلفزيون ويشغّله (ويكمّل من startSec). لازم يتنادى من thread خلفي. */
    fun start(d: Dev, url: String, title: String, startSec: Int): Boolean {
        lastError = ""
        Proxy.last = ""
        Proxy.hits = 0
        val res = resolve(url)
        val m1 = mimeFor(res.url, res.type)
        val hls = m1.contains("mpegurl")

        val ip = localIp(d)
        val port = try { Proxy.ensure() } catch (t: Throwable) { lastError = "proxy: ${t.javaClass.simpleName}"; -1 }

        // (رابط، نوع، مع ميتاداتا؟) — كل تلفزيون بيقبل شكل مختلف فبنجرّب بالترتيب
        val attempts = ArrayList<Triple<String, String, Boolean>>()
        if (ip != null && port > 0) {
            val host = "$ip:$port"
            attempts += Triple(Proxy.urlFor(url, extFor(m1), host), m1, true)
            if (!hls && m1 != "video/mp4") attempts += Triple(Proxy.urlFor(url, "mp4", host), "video/mp4", true)
            attempts += Triple(Proxy.urlFor(url, extFor(m1), host), m1, false)
        }
        // احتياطي: الرابط المباشر من غير بروكسي
        attempts += Triple(res.url, m1, true)

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
        lastError = "$lastError | phone=$ip:$port tvRequests=${Proxy.hits} ${Proxy.last}".trim()
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

    /** بيوقف سيرفر البث المحلي (نادِه لما تقفل البث أو التطبيق) */
    fun shutdown() { Proxy.stop() }

    // =====================================================================
    //  سيرفر البث المحلي: التلفزيون بيطلب منه والموبايل بيجيب من المصدر
    // =====================================================================
    private object Proxy {
        @Volatile var last: String = ""
        @Volatile var hits: Int = 0
        @Volatile private var ss: ServerSocket? = null
        private val pool = Executors.newCachedThreadPool()

        @Synchronized fun ensure(): Int {
            val cur = ss
            if (cur != null && !cur.isClosed) return cur.localPort
            val n = ServerSocket(0, 50)
            ss = n
            Thread {
                while (!n.isClosed) {
                    try { val c = n.accept(); pool.execute { handle(c) } } catch (_: Throwable) { if (n.isClosed) break }
                }
            }.apply { isDaemon = true }.start()
            return n.localPort
        }

        @Synchronized fun stop() { try { ss?.close() } catch (_: Throwable) {}; ss = null }

        private fun b64(s: String) =
            Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        fun urlFor(upstream: String, ext: String, host: String) = "http://$host/p/${b64(upstream)}.$ext"

        private fun extOfUrl(u: String): String {
            val e = u.substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
            return if (e.length in 1..4 && e.all { it.isLetterOrDigit() }) e else "bin"
        }

        private fun ctype(ext: String) = when (ext) {
            "mkv" -> "video/x-matroska"
            "mp4", "m4v" -> "video/mp4"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "ts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            "m4s" -> "video/iso.segment"
            "aac" -> "audio/aac"
            "vtt" -> "text/vtt"
            else -> "application/octet-stream"
        }

        private fun readLine(i: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = i.read()
                if (b < 0) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) return sb.toString().trimEnd('\r')
                sb.append(b.toChar())
            }
        }

        private fun head(out: OutputStream, status: String, h: Map<String, String>) {
            val sb = StringBuilder("HTTP/1.1 $status\r\n")
            for ((k, v) in h) sb.append(k).append(": ").append(v).append("\r\n")
            sb.append("Connection: close\r\n\r\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            out.flush()
        }

        /** فتح الرابط مع تتبّع الـ redirects يدوياً (http↔https) */
        private fun open(url: String, range: String?): HttpURLConnection? {
            var cur = url
            for (i in 0 until 8) {
                val c = URL(cur).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 8000; c.readTimeout = 25000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept", "*/*")
                if (range != null) c.setRequestProperty("Range", range)
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location"); c.disconnect()
                    if (loc.isNullOrBlank()) return null
                    cur = URL(URL(cur), loc).toString(); continue
                }
                return c
            }
            return null
        }

        private fun proxify(u: String, host: String, forceExt: String? = null) =
            urlFor(u, forceExt ?: extOfUrl(u), host)

        private fun rewrite(txt: String, base: String, host: String): String {
            val uriAttr = Regex("URI=\"([^\"]+)\"")
            val sb = StringBuilder()
            var nextIsPlaylist = false
            for (raw in txt.lines()) {
                val line = raw.trimEnd('\r')
                when {
                    line.isBlank() -> {}
                    line.startsWith("#") -> {
                        if (line.startsWith("#EXT-X-STREAM-INF")) nextIsPlaylist = true
                        val isPl = line.startsWith("#EXT-X-MEDIA") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF")
                        sb.append(uriAttr.replace(line) { m ->
                            val abs = try { URL(URL(base), m.groupValues[1]).toString() } catch (_: Throwable) { m.groupValues[1] }
                            "URI=\"" + proxify(abs, host, if (isPl) "m3u8" else null) + "\""
                        })
                    }
                    else -> {
                        val abs = try { URL(URL(base), line.trim()).toString() } catch (_: Throwable) { line.trim() }
                        sb.append(proxify(abs, host, if (nextIsPlaylist) "m3u8" else null))
                        nextIsPlaylist = false
                    }
                }
                sb.append('\n')
            }
            return sb.toString()
        }

        private fun handle(s: Socket) {
            try {
                s.soTimeout = 30000
                val inp = BufferedInputStream(s.getInputStream())
                val out = BufferedOutputStream(s.getOutputStream(), 65536)
                val reqLine = readLine(inp) ?: return
                val parts = reqLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                var range: String? = null
                var host = ""
                while (true) {
                    val l = readLine(inp) ?: break
                    if (l.isEmpty()) break
                    val i = l.indexOf(':'); if (i < 0) continue
                    val k = l.substring(0, i).trim().lowercase(); val v = l.substring(i + 1).trim()
                    if (k == "range") range = v
                    if (k == "host") host = v
                }
                hits++
                if (!parts[1].startsWith("/p/")) { head(out, "404 Not Found", mapOf("Content-Length" to "0")); return }
                val tok = parts[1].removePrefix("/p/").substringBefore('?')
                val ext = tok.substringAfterLast('.', "bin")
                val url = try {
                    String(Base64.decode(tok.substringBeforeLast('.'), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
                } catch (_: Throwable) { "" }
                if (!url.startsWith("http")) { head(out, "400 Bad Request", mapOf("Content-Length" to "0")); return }

                val common = LinkedHashMap<String, String>()
                common["Content-Type"] = ctype(ext)
                common["transferMode.dlna.org"] = "Streaming"
                common["contentFeatures.dlna.org"] = if (ext == "m3u8") "DLNA.ORG_OP=00;DLNA.ORG_CI=0" else FLAGS

                // ---- قائمة HLS: نعدّل الروابط جواها عشان تعدّي من البروكسي ----
                if (ext == "m3u8") {
                    val c = open(url, null)
                    if (c == null || c.responseCode >= 400) {
                        last = "upstream ${c?.responseCode ?: "no-response"}"
                        c?.disconnect(); head(out, "502 Bad Gateway", mapOf("Content-Length" to "0")); return
                    }
                    val txt = c.inputStream.bufferedReader().use { it.readText() }
                    val base = c.url.toString(); c.disconnect()
                    val body = rewrite(txt, base, host).toByteArray(Charsets.UTF_8)
                    common["Content-Length"] = body.size.toString()
                    head(out, "200 OK", common)
                    if (method != "HEAD") { out.write(body); out.flush() }
                    return
                }

                // ---- HEAD: نجيب الحجم من المصدر بطلب Range صغير ----
                if (method == "HEAD") {
                    val c = open(url, "bytes=0-0")
                    if (c == null || c.responseCode >= 400) {
                        last = "upstream ${c?.responseCode ?: "no-response"}"
                        c?.disconnect(); head(out, "502 Bad Gateway", mapOf("Content-Length" to "0")); return
                    }
                    val total = c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                        ?: (if (c.responseCode == 200) c.contentLengthLong else -1L)
                    c.disconnect()
                    common["Accept-Ranges"] = "bytes"
                    if (total > 0) common["Content-Length"] = total.toString()
                    head(out, "200 OK", common)
                    return
                }

                // ---- GET عادي (فيديو) مع Range ----
                val c = open(url, range)
                if (c == null) { last = "upstream no-response"; head(out, "502 Bad Gateway", mapOf("Content-Length" to "0")); return }
                val code = c.responseCode
                if (code >= 400) {
                    last = "upstream $code"
                    c.disconnect(); head(out, "$code Error", mapOf("Content-Length" to "0")); return
                }
                val len = c.contentLengthLong
                val cr = c.getHeaderField("Content-Range")
                if (len >= 0) common["Content-Length"] = len.toString()
                if (cr != null) common["Content-Range"] = cr
                if (code == 206 || c.getHeaderField("Accept-Ranges") == "bytes") common["Accept-Ranges"] = "bytes"
                head(out, if (code == 206) "206 Partial Content" else "200 OK", common)
                c.inputStream.use { src ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.flush()
                }
                c.disconnect()
            } catch (_: Throwable) {
                // التلفزيون قفل الاتصال (تقديم/تأخير) — طبيعي
            } finally {
                try { s.close() } catch (_: Throwable) {}
            }
        }
    }
}
