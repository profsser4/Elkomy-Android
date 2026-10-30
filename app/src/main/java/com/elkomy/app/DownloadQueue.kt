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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * طابور التحميل: ملف ورا ملف تلقائي (واحد بس بيتحمّل في نفس الوقت).
 * الاستخدام من أي مكان:  DownloadQueue.enqueue(context, url, "اسم.mkv")
 */
object DownloadQueue {
    enum class State { QUEUED, RUNNING, DONE, FAILED, CANCELED }

    class Item(val id: Long, val url: String, val name: String, @Volatile var batch: Int) {
        @Volatile var state = State.QUEUED
        @Volatile var done = 0L
        @Volatile var total = 0L
        @Volatile var speed = 0L
        @Volatile var error = ""
        @Volatile var uri: String? = null
        @Volatile var cancel = false
    }

    val items = CopyOnWriteArrayList<Item>()
    private val seq = AtomicLong(1)
    @Volatile private var batch = 0

    private var app: Context? = null

    /** بيرجّع قايمة التحميلات اللي اتحمّلت قبل كده (عشان تفضل ظاهرة وتتشغّل من مشغّل التطبيق) */
    @Synchronized fun load(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        app = a
        try {
            val arr = JSONArray(a.getSharedPreferences("dlq", Context.MODE_PRIVATE).getString("items", "[]"))
            var mx = 0L
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val it = Item(o.getLong("id"), o.getString("url"), o.getString("name"), 0)
                it.total = o.optLong("total"); it.done = o.optLong("done")
                it.uri = o.optString("uri").ifEmpty { null }
                val st = State.valueOf(o.optString("state", "FAILED"))
                if (st == State.DONE) {
                    if (!exists(a, it.uri)) continue
                    it.state = State.DONE
                } else { it.state = State.FAILED; it.error = "اتقطع (التطبيق اتقفل)" }
                items.add(it); mx = maxOf(mx, it.id)
            }
            seq.set(mx + 1)
        } catch (_: Throwable) {}
    }

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
                    .put("state", it.state.name).put("total", it.total).put("done", it.done).put("uri", it.uri ?: ""))
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

    fun enqueue(ctx: Context, url: String, name: String): Item {
        val item: Item
        synchronized(this) {
            val same = items.firstOrNull { it.url == url && (it.state == State.QUEUED || it.state == State.RUNNING) }
            if (same != null) return same
            if (!busy()) batch++
            item = Item(seq.getAndIncrement(), url, name, batch)
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
            item.cancel = false
            item.done = 0; item.total = 0; item.speed = 0; item.error = ""
            item.state = State.QUEUED
        }
        save()
        start(ctx)
    }

    fun remove(item: Item) {
        if (item.state != State.RUNNING) items.remove(item)
        save()
    }

    fun clearFinished() {
        items.removeAll { it.state == State.FAILED || it.state == State.CANCELED }   // المكتمل بيفضل عشان تشغّله من التطبيق؛ امسحه بالسلة 🗑
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

    private fun open(start: String): HttpURLConnection {
        var u = start
        for (i in 0..6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "*/*")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw IOException("تحويل غير صالح")
                u = URL(URL(u), loc).toString()
                continue
            }
            if (code !in 200..299) { c.disconnect(); throw IOException("HTTP $code") }
            return c
        }
        throw IOException("تحويلات كتير")
    }

    private fun download(item: DownloadQueue.Item) {
        item.state = DownloadQueue.State.RUNNING
        item.done = 0; item.total = 0; item.speed = 0; item.error = ""
        notifyProgress(item, true)
        var conn: HttpURLConnection? = null
        var sink: Sink? = null
        try {
            conn = open(item.url)
            item.total = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val sk = openSink(safeName(item.name))
            sink = sk
            conn.inputStream.use { input ->
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
            item.speed = 0
            item.state = DownloadQueue.State.DONE
            notifyDone(item)
        } catch (e: Exception) {
            sink?.abort(this)
            item.speed = 0
            if (item.cancel) item.state = DownloadQueue.State.CANCELED
            else {
                item.state = DownloadQueue.State.FAILED
                item.error = (e.message ?: "فشل التحميل").take(80)
            }
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
            DownloadQueue.save()
        }
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
                .setContentText(item.name + if (item.total > 0) " · $pct%" else "")
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
