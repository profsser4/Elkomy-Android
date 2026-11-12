package com.elkomy.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * خدمة أمامية (Foreground Service) بتفضل شغالة طول ما التلفزيون بيشغّل البث:
 *  - بتخلّي النظام ميقتلش التطبيق (مهم لو البث عن طريق بروكسي الموبايل) حتى لو الشاشة اتقفلت.
 *  - بتسأل التلفزيون كل ٣ ثواني هو واصل فين، وبتحفظ الموضع (للاستكمال) وتبلّغ الصفحة.
 *  - لو الموبايل بعيد عن التلفزيون (برا الواي فاي) بتحسب الموضع بالتقدير من آخر موضع معروف.
 */
class CastService : Service() {
    companion object {
        private const val CH = "elkomy_cast"
        private const val ID = 4711
        const val ACT_STOP = "com.elkomy.app.CAST_STOP"

        @Volatile var active = false
        @Volatile var dev: Dlna.Dev? = null
        @Volatile var url = ""
        @Volatile var title = ""
        @Volatile var pos = 0L          // آخر موضع معروف من التلفزيون (ثواني)
        @Volatile var len = 0L
        @Volatile var playing = true
        @Volatile var uiAttached = false   // المشغّل مفتوح قدام المستخدم → هو اللي بيتعامل مع نهاية الحلقة
        @Volatile private var seen = false

        /** بتتنادى بعد ما التلفزيون يبدأ يشغّل فعلاً (والمشغّل ظاهر على الشاشة) */
        fun begin(ctx: Context, d: Dlna.Dev, u: String, t: String, startSec: Long) {
            dev = d; url = u; title = t; pos = startSec; len = 0; playing = true; seen = false
            val i = Intent(ctx.applicationContext, CastService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.applicationContext.startForegroundService(i)
                else ctx.applicationContext.startService(i)
            } catch (_: Throwable) {}
        }

        /** إنهاء الخدمة من غير ما نبعت Stop للتلفزيون (اللي ينادي بيتولّى الـ Stop) */
        fun end(ctx: Context) {
            active = false
            dev = null
            try { ctx.applicationContext.stopService(Intent(ctx.applicationContext, CastService::class.java)) } catch (_: Throwable) {}
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var poller: Thread? = null
    @Volatile private var fails = 0
    @Volatile private var lastOkAt = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == ACT_STOP) {
            val d = dev
            active = false
            dev = null
            if (d != null) Thread { Dlna.stop(d) }.start()
            try { getSharedPreferences("cast", MODE_PRIVATE).edit().clear().apply() } catch (_: Throwable) {}
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        try {
            val n = buildNotif(pos)
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(ID, n)
        } catch (_: Throwable) {
            // مفيش صلاحية/نوع خدمة في الـ Manifest — البث نفسه شغال بس من غير الخدمة
            active = false
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        if (poller?.isAlive != true) {
            fails = 0
            poller = Thread { loop() }.also { it.isDaemon = true; it.start() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        active = false
        super.onDestroy()
    }

    // ---------------- متابعة التلفزيون ----------------
    private fun estimate(): Long {
        if (fails == 0 || !playing || len <= 0) return pos
        return minOf(len, pos + (System.currentTimeMillis() - lastOkAt) / 1000)
    }

    private fun loop() {
        var tickN = 0
        lastOkAt = System.currentTimeMillis()
        while (active) {
            val d = dev ?: break
            val st = Dlna.transportState(d)
            val p = if (st != null) Dlna.pos(d) else null
            val now = System.currentTimeMillis()
            if (st != null && p != null) {
                fails = 0
                lastOkAt = now
                pos = p.first
                if (p.second > 0) len = p.second
                playing = st == "PLAYING" || st == "TRANSITIONING"
                if (st == "PLAYING") seen = true
                if (!uiAttached) {
                    if (len > 10 && pos >= len - 3) { finishCast(true); break }
                    if (seen && (st == "STOPPED" || st == "NO_MEDIA_PRESENT")) { finishCast(false); break }
                }
            } else {
                fails++
                if (!uiAttached && len > 10 && estimate() >= len) { finishCast(true); break }
                if (fails > 3600) { finishCast(false); break }   // ~٣ ساعات من غير رد
            }
            if (++tickN % 5 == 0) {
                val e = estimate()
                persist(e)
                if (!uiAttached) report(e, len, false)
                try {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, buildNotif(e))
                } catch (_: Throwable) {}
            }
            try { Thread.sleep(3000) } catch (_: InterruptedException) { break }
        }
    }

    private fun persist(e: Long) {
        try {
            val u = url
            if (u.isEmpty()) return
            val ed = getSharedPreferences("pos", MODE_PRIVATE).edit()
            if (len > 0 && e > len * 0.95) ed.remove("p:$u") else ed.putInt("p:$u", e.toInt())
            ed.apply()
            getSharedPreferences("cast", MODE_PRIVATE).edit()
                .putString("url", u).putString("title", title)
                .putLong("pos", e).putLong("len", len)
                .putLong("ts", System.currentTimeMillis()).apply()
        } catch (_: Throwable) {}
    }

    private fun report(p: Long, l: Long, ended: Boolean) {
        if (l <= 0) return
        main.post {
            try {
                MainActivity.inst?.web?.evaluateJavascript("window.nativeProgress&&nativeProgress($p,$l,$ended)", null)
            } catch (_: Throwable) {}
        }
    }

    private fun finishCast(ended: Boolean) {
        val d = dev
        if (ended) {
            try { getSharedPreferences("pos", MODE_PRIVATE).edit().remove("p:$url").apply() } catch (_: Throwable) {}
            try { getSharedPreferences("cast", MODE_PRIVATE).edit().clear().apply() } catch (_: Throwable) {}
            report(len, len, true)
        } else {
            persist(estimate())
            report(estimate(), len, false)
        }
        active = false
        dev = null
        if (d != null) Dlna.stop(d)   // بيقفل البروكسي والأقفال كمان
        main.post { stopForegroundCompat(); stopSelf() }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_REMOVE) else stopForeground(true)
        } catch (_: Throwable) {}
    }

    // ---------------- الإشعار ----------------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "البث على التلفزيون", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun fmt(sec: Long): String {
        val s = maxOf(0L, sec)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    private fun buildNotif(e: Long): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val stopPi = PendingIntent.getService(this, 1, Intent(this, CastService::class.java).setAction(ACT_STOP), flags)
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val openPi = if (open != null) PendingIntent.getActivity(this, 2, open, flags) else null
        val txt = if (len > 0) "${fmt(e)} / ${fmt(len)}" else fmt(e)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("📺  " + (dev?.name ?: "التلفزيون"))
            .setContentText(title.substringBefore("\n@@SUB@@") + " · " + txt)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف البث", stopPi)
            .build()
    }
}
