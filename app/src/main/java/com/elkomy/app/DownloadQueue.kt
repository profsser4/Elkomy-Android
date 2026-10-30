package com.elkomy.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference

/**
 * طابور التحميل: ملف ورا ملف تلقائي (واحد بس بيتحمّل في نفس الوقت).
 * الاستخدام من أي مكان:  DownloadQueue.enqueue(context, url, "اسم.mkv")
 */
object DownloadQueue {
    enum class State { QUEUED, RUNNING, DONE, FAILED, CANCELED }

    class Item(val id: Long, @Volatile var url: String, val name: String, @Volatile var batch: Int) {
        @Volatile var src = ""                 // وصف بيستخدمه التطبيق يجدّد الرابط لو انتهى (debrid)
        @Volatile var partUri: String? = null   // الملف الناقص اللي بنكمّل عليه
        @Volatile var chunk = 0L
        @Volatile var gots: LongArray? = null   // اللي اتحمّل من كل جزء
        @Volatile var newUrl: String? = null
        @Volatile var refreshErr: String? = null
        @Volatile var note = ""
        @Volatile var state = State.QUEUED
        @Volatile var done = 0L
        @Volatile var total = 0L
        @Volatile var speed = 0L
        @Volatile var error = ""
        @Volatile var uri: String? = null
        @Volatile var cancel = false
        @Volatile var paused = false
        @Volatile var conns = 0
        @Volatile var segs = IntArray(0)
    }

    val items = CopyOnWriteArrayList<Item>()
    private val seq = AtomicLong(1)
    @Volatile private var batch = 0

    private var app: Context? = null
    @Volatile var conns = 8

    /** بيرجّع قايمة التحميلات اللي اتحمّلت قبل كده (عشان تفضل ظاهرة وتتشغّل من مشغّل التطبيق) */
    @Synchronized fun load(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        app = a
        conns = a.getSharedPreferences("dlq", Context.MODE_PRIVATE).getInt("conns", 8)
        try {
            val arr = JSONArray(a.getSharedPreferences("dlq", Context.MODE_PRIVATE).getString("items", "[]"))
            var mx = 0L
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val it = Item(o.getLong("id"), o.getString("url"), o.getString("name"), 0)
                it.total = o.optLong("total"); it.done = o.optLong("done")
                it.uri = o.optString("uri").ifEmpty { null }
                it.src = o.optString("src")
                val st = State.valueOf(o.optString("state", "FAILED"))
                if (st == State.DONE) {
                    if (!exists(a, it.uri)) continue
                    it.state = State.DONE
                } else {
                    it.state = State.FAILED
                    val part = o.optString("part").ifEmpty { null }
                    val ga = o.optJSONArray("gots")
                    if (part != null && ga != null && ga.length() > 0 && it.total > 0 && exists(a, part)) {
                        it.partUri = part
                        it.chunk = o.optLong("chunk")
                        it.gots = LongArray(ga.length()) { idx -> ga.getLong(idx) }
                        it.done = it.gots!!.sum()
                        it.error = "اتقطع — دوس ▶ تكمّل من نفس المكان"
                    } else { it.done = 0; it.error = "اتقطع (التطبيق اتقفل)" }
                }
                items.add(it); mx = maxOf(mx, it.id)
            }
            seq.set(mx + 1)
        } catch (_: Throwable) {}
    }

    fun setConns(n: Int) {
        conns = n
        app?.getSharedPreferences("dlq", Context.MODE_PRIVATE)?.edit()?.putInt("conns", n)?.apply()
    }

    fun togglePause(item: Item) { if (item.state == State.RUNNING) item.paused = !item.paused }

    fun activeCount() = items.count { it.state == State.QUEUED || it.state == State.RUNNING }

    private fun exists(c: Context, u: String?): Boolean {
        if (u == null) return false
        return try {
            if (u.startsWith("file:")) File(Uri.parse(u).path ?: return false).exists()
            else { c.contentResolver.openAssetFileDescriptor(Uri.parse(u), "r")?.use { true } ?: false }
        } catch (_: Throwable) { false }
    }

    fun save() {
        val a = app ?: return
        try {
            val arr = JSONArray()
            items.forEach {
                arr.put(JSONObject().put("id", it.id).put("url", it.url).put("name", it.name)
                    .put("state", it.state.name).put("total", it.total).put("done", it.done).put("uri", it.uri ?: "")
                    .put("src", it.src).put("part", it.partUri ?: "").put("chunk", it.chunk)
                    .put("gots", JSONArray((it.gots ?: LongArray(0)).toList())))
            }
            a.getSharedPreferences("dlq", Context.MODE_PRIVATE).edit().putString("items", arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    /** بيحذف الملف نفسه من الجهاز وبعدين من القايمة */
    fun deleteFile(ctx: Context, item: Item) {
        try {
            val u = item.uri
            if (u != null) {
                if (u.startsWith("file:")) File(Uri.parse(u).path ?: "").delete()
                else ctx.contentResolver.delete(Uri.parse(u), null, null)
            }
        } catch (_: Throwable) {}
        remove(item)
    }

    private fun busy() = items.any { it.state == State.QUEUED || it.state == State.RUNNING }

    fun enqueue(ctx: Context, url: String, name: String, src: String = ""): Item {
        val item: Item
        synchronized(this) {
            val same = items.firstOrNull { it.url == url && (it.state == State.QUEUED || it.state == State.RUNNING) }
            if (same != null) return same
            if (!busy()) batch++
            item = Item(seq.getAndIncrement(), url, name, batch)
            item.src = src
            items.add(item)
        }
        save()
        start(ctx)
        return item
    }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
    }

    fun cancel(item: Item) {
        if (item.state == State.RUNNING) item.cancel = true
        else if (item.state == State.QUEUED) item.state = State.CANCELED
    }

    fun retry(ctx: Context, item: Item) {
        if (item.state != State.FAILED && item.state != State.CANCELED) return
        synchronized(this) {
            if (!busy()) batch++
            item.batch = batch
            item.cancel = false; item.paused = false
            if (!canResume(item)) { item.done = 0; item.total = 0 }
            item.speed = 0; item.error = ""; item.note = ""
            item.state = State.QUEUED
        }
        save()
        start(ctx)
    }

    fun canResume(item: Item) = item.partUri != null && item.gots != null && item.chunk > 0 && item.total > 0

    /** بيمسح الملف الناقص من الجهاز */
    fun dropPartial(item: Item) {
        val u = item.partUri ?: return
        item.partUri = null; item.gots = null; item.chunk = 0
        val a = app ?: return
        try {
            if (u.startsWith("file:")) File(Uri.parse(u).path ?: "").delete()
            else a.contentResolver.delete(Uri.parse(u), null, null)
        } catch (_: Throwable) {}
    }

    fun setLink(id: Long, u: String) { items.firstOrNull { it.id == id }?.newUrl = u }
    fun refreshFail(id: Long, m: String) { items.firstOrNull { it.id == id }?.refreshErr = m }

    /** بيطلب من الصفحة تجدّد الرابط (debrid) وبيستنى الرد لحد 3 دقايق */
    fun requestRefresh(item: Item): String? {
        if (item.src.isEmpty()) return null
        val m = MainActivity.inst ?: return null
        item.newUrl = null; item.refreshErr = null
        m.runJs("window.dlRefresh&&window.dlRefresh(${item.id},${JSONObject.quote(item.src)})")
        val t0 = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - t0 < 180_000) {
            if (item.cancel) return null
            item.newUrl?.let { return it }
            if (item.refreshErr != null) return null
            try { Thread.sleep(300) } catch (_: InterruptedException) {}
        }
        return null
    }

    fun remove(item: Item) {
        if (item.state != State.RUNNING) { dropPartial(item); items.remove(item) }
        save()
    }

    fun clearFinished() {
        items.removeAll { it.state == State.CANCELED || (it.state == State.FAILED && it.partUri == null) }   // المكتمل بيفضل عشان تشغّله من التطبيق؛ امسحه بالسلة 🗑
        save()
    }

    fun batchItems(): List<Item> = items.filter { it.batch == batch }
}

class DownloadService : Service() {
    private val lock = Any()
    private var running = false
    private var lastStartId = 0
    private var lastNotify = 0L
    private val main = Handler(Looper.getMainLooper())

    companion object {
        private const val CH = "dl_progress"
        private const val CH_DONE = "dl_done"
        private const val NOTIF_ID = 4711
        private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    }

    override fun onBind(i: Intent?): IBinder? = null

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = nm()
            mgr.createNotificationChannel(NotificationChannel(CH, "تقدم التحميل", NotificationManager.IMPORTANCE_LOW))
            mgr.createNotificationChannel(NotificationChannel(CH_DONE, "اكتمال التحميل", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotif(null)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)
        synchronized(lock) {
            lastStartId = startId
            if (!running) {
                running = true
                Thread { loop() }.start()
            }
        }
        return START_NOT_STICKY
    }

    /** ملف ورا ملف: لما واحد يخلص (أو يفشل) الشغل بيروح للي بعده لوحده */
    private fun loop() {
        while (true) {
            val next = synchronized(lock) {
                val x = DownloadQueue.items.firstOrNull { it.state == DownloadQueue.State.QUEUED }
                if (x == null) running = false
                x
            } ?: break
            download(next)
        }
        main.post {
            synchronized(lock) {
                if (!running) {
                    stopForeground(true)
                    stopSelf(lastStartId)
                }
            }
        }
    }

    // ---------- التحميل ----------
    private class Sink(val out: OutputStream, val uri: Uri?, val file: File?) {
        fun finish(ctx: Context): String {
            try { out.flush(); out.close() } catch (_: Throwable) {}
            if (uri != null) {
                if (Build.VERSION.SDK_INT >= 29) {
                    val v = ContentValues()
                    v.put(MediaStore.Downloads.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, v, null, null)
                }
                return uri.toString()
            }
            return Uri.fromFile(file!!).toString()
        }
        fun abort(ctx: Context) {
            try { out.close() } catch (_: Throwable) {}
            if (uri != null) try { ctx.contentResolver.delete(uri, null, null) } catch (_: Throwable) {}
            file?.delete()
        }
    }

    private fun safeName(n: String): String {
        var s = n.replace(Regex("[\\\\/:*?\"<>|]+"), "_").trim()
        if (s.isEmpty()) s = "video"
        if (!s.contains('.')) s += ".mp4"
        return s.take(150)
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (ext == "mkv") "video/x-matroska" else "application/octet-stream"
    }

    private fun openSink(name: String): Sink {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(name))
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Elkomy")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                ?: throw IOException("مقدرتش أنشئ الملف في التخزين")
            val os = contentResolver.openOutputStream(uri) ?: throw IOException("مقدرتش أفتح الملف للكتابة")
            return Sink(BufferedOutputStream(os, 256 * 1024), uri, null)
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        dir.mkdirs()
        var f = File(dir, name)
        var i = 1
        while (f.exists()) {
            f = File(dir, name.substringBeforeLast('.') + " ($i)." + name.substringAfterLast('.', "mp4"))
            i++
        }
        return Sink(BufferedOutputStream(FileOutputStream(f), 256 * 1024), null, f)
    }

    private class RSink(val ch: FileChannel, val pfd: ParcelFileDescriptor?, val raf: RandomAccessFile?, val uri: Uri?, val file: File?) {
        fun close() = closeAll()
        fun uriString(): String = uri?.toString() ?: Uri.fromFile(file!!).toString()
        private fun closeAll() {
            try { ch.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
            try { raf?.close() } catch (_: Throwable) {}
        }
        fun finish(ctx: Context): String {
            try { ch.force(false) } catch (_: Throwable) {}
            closeAll()
            if (uri != null) {
                if (Build.VERSION.SDK_INT >= 29) {
                    val v = ContentValues()
                    v.put(MediaStore.Downloads.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, v, null, null)
                }
                return uri.toString()
            }
            return Uri.fromFile(file!!).toString()
        }
        fun abort(ctx: Context) {
            closeAll()
            if (uri != null) try { ctx.contentResolver.delete(uri, null, null) } catch (_: Throwable) {}
            file?.delete()
        }
    }

    /** ملف بكتابة عشوائية (عشان كل اتصال يكتب الجزء بتاعه في مكانه) */
    private fun openRSink(name: String, total: Long): RSink {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(name))
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Elkomy")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                ?: throw IOException("مقدرتش أنشئ الملف في التخزين")
            val pfd = contentResolver.openFileDescriptor(uri, "rw") ?: throw IOException("مقدرتش أفتح الملف للكتابة")
            try { android.system.Os.ftruncate(pfd.fileDescriptor, total) } catch (_: Throwable) {}
            return RSink(FileOutputStream(pfd.fileDescriptor).channel, pfd, null, uri, null)
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        dir.mkdirs()
        var f = File(dir, name)
        var i = 1
        while (f.exists()) {
            f = File(dir, name.substringBeforeLast('.') + " ($i)." + name.substringAfterLast('.', "mp4"))
            i++
        }
        val raf = RandomAccessFile(f, "rw")
        raf.setLength(total)
        return RSink(raf.channel, null, raf, null, f)
    }

    /** بيفتح الملف الناقص اللي اتحفظ قبل كده عشان نكمّل عليه */
    private fun openExisting(u: String): RSink {
        if (u.startsWith("file:")) {
            val f = File(Uri.parse(u).path ?: throw IOException("الملف الناقص ضاع"))
            if (!f.exists()) throw IOException("الملف الناقص اتمسح — ابدأ من جديد")
            val raf = RandomAccessFile(f, "rw")
            return RSink(raf.channel, null, raf, null, f)
        }
        val uri = Uri.parse(u)
        val pfd = try { contentResolver.openFileDescriptor(uri, "rw") } catch (_: Throwable) { null }
            ?: throw IOException("الملف الناقص اتمسح — ابدأ من جديد")
        return RSink(FileOutputStream(pfd.fileDescriptor).channel, pfd, null, uri, null)
    }

    /** بيجرّب الرابط: لو بيدعم الاستكمال بيرجّع (الرابط النهائي، حجم الملف) */
    private fun probeUrl(u: String): Pair<String, Long>? {
        return try {
            val (c, f) = open(u, "bytes=0-0")
            try {
                if (c.responseCode != 206) null
                else {
                    val t = c.getHeaderField("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    if (t == null || t <= 0) null else Pair(f, t)
                }
            } finally { try { c.disconnect() } catch (_: Throwable) {} }
        } catch (_: Exception) { null }
    }

    /** بيفتح الاتصال ويمشي ورا التحويلات؛ بيرجّع (الاتصال، الرابط النهائي) */
    private fun open(start: String, range: String? = null): Pair<HttpURLConnection, String> {
        var u = start
        for (i in 0..6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "*/*")
            if (range != null) c.setRequestProperty("Range", range)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw IOException("تحويل غير صالح")
                u = URL(URL(u), loc).toString()
                continue
            }
            if (code !in 200..299) { c.disconnect(); throw IOException("HTTP $code") }
            return Pair(c, u)
        }
        throw IOException("تحويلات كتير")
    }

    private fun download(item: DownloadQueue.Item) {
        item.state = DownloadQueue.State.RUNNING
        val resume = DownloadQueue.canResume(item)
        if (resume) item.done = item.gots!!.sum()
        else { item.done = 0; item.total = 0; item.segs = IntArray(0); item.partUri = null; item.gots = null; item.chunk = 0 }
        item.speed = 0; item.error = ""; item.note = ""
        item.paused = false; item.conns = 0
        notifyProgress(item, true)
        var conn: HttpURLConnection? = null
        var sink: Sink? = null
        var rs: RSink? = null
        try {
            val want = DownloadQueue.conns.coerceIn(1, 16)
            if (resume) {
                val r = openExisting(item.partUri!!)
                rs = r
                var p = probeUrl(item.url)
                if (p == null) {
                    // الرابط انتهى: نجدّده من الـdebrid ونكمّل من نفس المكان
                    item.note = "🔄 بجدد الرابط..."
                    val nu = DownloadQueue.requestRefresh(item)
                        ?: throw IOException(if (item.src.isEmpty()) "الرابط انتهى ومينفعش يتجدّد — ابدأ من جديد" else "ماقدرتش أجدّد الرابط — افتح التطبيق وجرّب تاني")
                    item.url = nu
                    p = probeUrl(nu) ?: throw IOException("الرابط الجديد مش بيدعم الاستكمال")
                    item.note = ""
                }
                if (p.second != item.total) throw IOException("الملف اتغيّر (حجم مختلف) — ابدأ من جديد")
                downloadSegmented(item, p.first, item.total, r, want, item.chunk, item.gots)
                item.uri = r.finish(this)
                item.partUri = null; item.gots = null
            } else {
                val (c0, fin) = open(item.url, if (want > 1) "bytes=0-0" else null)
                conn = c0
                var segTotal = -1L
                if (want > 1 && c0.responseCode == 206) {
                    segTotal = c0.getHeaderField("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L
                    c0.disconnect()
                    conn = null
                    if (segTotal < 2L * 1024 * 1024) {
                        conn = open(fin).first
                        segTotal = -1L
                    }
                }
                if (segTotal > 0) {
                    item.total = segTotal
                    val r = openRSink(safeName(item.name), segTotal)
                    rs = r
                    item.partUri = r.uriString()
                    downloadSegmented(item, fin, segTotal, r, want, 0L, null)
                    item.uri = r.finish(this)
                    item.partUri = null; item.gots = null
                } else {
                    val cc = conn!!
                    item.total = cc.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                    val sk = openSink(safeName(item.name))
                    sink = sk
                    cc.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var lastT = SystemClock.elapsedRealtime()
                        var lastB = 0L
                        while (true) {
                            if (item.cancel) throw IOException("canceled")
                            val r = input.read(buf)
                            if (r < 0) break
                            sk.out.write(buf, 0, r)
                            item.done += r
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastT >= 700) {
                                item.speed = (item.done - lastB) * 1000 / (now - lastT)
                                lastT = now
                                lastB = item.done
                                notifyProgress(item, false)
                            }
                        }
                    }
                    if (item.total > 0 && item.done < item.total) throw IOException("الملف وصل ناقص")
                    item.uri = sk.finish(this)
                }
            }
            item.speed = 0
            item.conns = 0
            item.note = ""
            item.state = DownloadQueue.State.DONE
            notifyDone(item)
        } catch (e: Exception) {
            sink?.abort(this)
            item.speed = 0
            item.conns = 0
            item.note = ""
            if (item.cancel) {
                rs?.abort(this)
                DownloadQueue.dropPartial(item)
                item.state = DownloadQueue.State.CANCELED
            } else {
                // لو في تقدّم بنسيب الملف الناقص عشان تكمّل منه بعدين
                if (rs != null) {
                    if (item.done > 0 && DownloadQueue.canResume(item)) rs.close()
                    else { rs.abort(this); DownloadQueue.dropPartial(item) }
                }
                item.state = DownloadQueue.State.FAILED
                val m = (e.message ?: "فشل التحميل").take(80)
                item.error = if (DownloadQueue.canResume(item)) "$m — دوس ▶ تكمّل من نفس المكان" else m
            }
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
            DownloadQueue.save()
        }
    }

    /** تحميل متعدد الاتصالات زي IDM + استكمال: التقدّم بيتحفظ، والرابط بيتجدّد لو انتهى */
    private fun downloadSegmented(item: DownloadQueue.Item, url0: String, total: Long, sk: RSink, want: Int, chunkIn: Long, initGot: LongArray?) {
        val chunk = if (chunkIn > 0) chunkIn else maxOf(1L shl 20, (total + 63) / 64)
        val n = ((total + chunk - 1) / chunk).toInt()
        val got = AtomicLongArray(n)
        val doneBytes = AtomicLong(0)
        if (initGot != null && initGot.size == n) {
            for (k in 0 until n) { got.set(k, initGot[k]); doneBytes.addAndGet(initGot[k]) }
        }
        item.chunk = chunk
        val next = AtomicInteger(0)
        val active = AtomicInteger(0)
        val fail = AtomicReference<String?>(null)
        val curUrl = AtomicReference(url0)
        val refreshLock = Any()
        fun lenOf(i: Int) = minOf(total, (i + 1) * chunk) - i * chunk
        fun snapshot() { item.gots = LongArray(n) { got.get(it) } }
        snapshot()

        fun refresh(bad: String): Boolean = synchronized(refreshLock) {
            if (curUrl.get() != bad) return@synchronized true
            item.note = "🔄 بجدد الرابط..."
            val nu = DownloadQueue.requestRefresh(item) ?: return@synchronized false
            val pr = probeUrl(nu)
            if (pr == null || pr.second != total) return@synchronized false
            curUrl.set(pr.first)
            item.url = nu
            item.note = ""
            true
        }

        val worker = Runnable {
            val buf = ByteArray(64 * 1024)
            while (true) {
                if (item.cancel || fail.get() != null) return@Runnable
                val i = next.getAndIncrement()
                if (i >= n) return@Runnable
                val s = i * chunk
                val e = s + lenOf(i) - 1
                var tries = 0
                while (got.get(i) < lenOf(i)) {
                    if (item.cancel || fail.get() != null) return@Runnable
                    if (item.paused) { try { Thread.sleep(200) } catch (_: InterruptedException) {}; continue }
                    val before = got.get(i)
                    val used = curUrl.get()
                    var c: HttpURLConnection? = null
                    var counted = false
                    try {
                        active.incrementAndGet(); counted = true
                        c = open(used, "bytes=${s + before}-$e").first
                        if (c.responseCode != 206) throw IOException("السيرفر مش بيدعم التقسيم")
                        c.inputStream.use { inp ->
                            while (true) {
                                if (item.cancel || item.paused || fail.get() != null) break
                                val left = lenOf(i) - got.get(i)
                                if (left <= 0) break
                                val r = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (r < 0) break
                                val bb = ByteBuffer.wrap(buf, 0, r)
                                var pos = s + got.get(i)
                                while (bb.hasRemaining()) pos += sk.ch.write(bb, pos)
                                got.addAndGet(i, r.toLong())
                                doneBytes.addAndGet(r.toLong())
                            }
                        }
                        if (got.get(i) > before) tries = 0
                        else if (!item.paused && !item.cancel && ++tries > 6) { fail.compareAndSet(null, "السيرفر وقّف الإرسال"); return@Runnable }
                    } catch (ex: Exception) {
                        if (item.cancel) return@Runnable
                        val msg = ex.message ?: ""
                        if (Regex("HTTP (401|403|404|410)").containsMatchIn(msg)) {
                            // الرابط انتهى: نجدّده من الـdebrid ونكمّل
                            if (!refresh(used)) { fail.compareAndSet(null, if (item.src.isEmpty()) "الرابط انتهى ومينفعش يتجدّد" else "ماقدرتش أجدّد الرابط"); return@Runnable }
                            continue
                        }
                        if (got.get(i) > before) tries = 0
                        if (++tries > 6) { fail.compareAndSet(null, msg.ifEmpty { "فشل الاتصال" }.take(80)); return@Runnable }
                        try { Thread.sleep(1000L * tries) } catch (_: InterruptedException) {}
                    } finally {
                        if (counted) active.decrementAndGet()
                        try { c?.disconnect() } catch (_: Throwable) {}
                    }
                }
            }
        }

        val workers = (0 until minOf(want, n)).map { Thread(worker).also { t -> t.start() } }
        var lastT = SystemClock.elapsedRealtime()
        var lastB = doneBytes.get()
        var lastSave = lastT
        while (workers.any { it.isAlive }) {
            try { Thread.sleep(300) } catch (_: InterruptedException) {}
            val now = SystemClock.elapsedRealtime()
            val d = doneBytes.get()
            item.done = d
            if (now - lastT >= 700) {
                item.speed = if (item.paused) 0 else (d - lastB) * 1000 / (now - lastT)
                lastT = now; lastB = d
            }
            item.conns = active.get()
            item.segs = IntArray(n) { (got.get(it) * 100 / lenOf(it)).toInt() }
            if (now - lastSave >= 4000) { snapshot(); DownloadQueue.save(); lastSave = now }
            notifyProgress(item, false)
        }
        item.done = doneBytes.get()
        item.segs = IntArray(n) { (got.get(it) * 100 / lenOf(it)).toInt() }
        snapshot()
        fail.get()?.let { throw IOException(it) }
        if (item.cancel) throw IOException("canceled")
        if (doneBytes.get() < total) throw IOException("الملف وصل ناقص")
    }

    // ---------- الإشعارات ----------
    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, DownloadsActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotif(item: DownloadQueue.Item?): Notification {
        val b = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent())
        if (item == null) {
            b.setContentTitle("جاري التحضير للتحميل...").setProgress(0, 0, true)
        } else {
            val batch = DownloadQueue.batchItems()
            val n = batch.size
            val fin = batch.count {
                it.state == DownloadQueue.State.DONE || it.state == DownloadQueue.State.FAILED || it.state == DownloadQueue.State.CANCELED
            }
            val pct = if (item.total > 0) (item.done * 100 / item.total).toInt() else 0
            b.setContentTitle(if (n > 1) "تحميل ملف ${minOf(fin + 1, n)} من $n" else "جاري التحميل")
                .setContentText(item.name + (if (item.total > 0) " · $pct%" else "") +
                    (if (item.paused) " · ⏸ متوقف" else if (item.speed > 0) " · ${dlSize(item.speed)}/s" else ""))
                .setProgress(100, pct, item.total <= 0)
        }
        return b.build()
    }

    private fun notifyProgress(item: DownloadQueue.Item, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotify < 800) return
        lastNotify = now
        try { nm().notify(NOTIF_ID, buildNotif(item)) } catch (_: Throwable) {}
    }

    private fun notifyDone(item: DownloadQueue.Item) {
        try {
            val n = NotificationCompat.Builder(this, CH_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("اكتمل التحميل ✓")
                .setContentText(item.name)
                .setAutoCancel(true)
                .setContentIntent(openIntent())
                .build()
            nm().notify(2000 + (item.id % 1000).toInt(), n)
        } catch (_: Throwable) {}
    }
}
