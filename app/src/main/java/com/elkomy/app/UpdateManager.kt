package com.elkomy.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** تحديث من جوه التطبيق: بينزّل الـAPK ويفتح شاشة التثبيت فوق النسخة الحالية (من غير ما تنزّل البرنامج بإيديك) */
object UpdateManager {
    @Volatile private var busy = false
    private var lastCheck = 0L

    fun check(a: Activity, manual: Boolean = false) {
        val repo = BuildConfig.REPO
        if (!repo.contains("/") || repo.contains("CHANGE-ME")) {
            if (manual) Toast.makeText(a, "رابط التحديث مش متضبط", Toast.LENGTH_LONG).show()
            return
        }
        val now = System.currentTimeMillis()
        if (!manual && now - lastCheck < 30 * 60 * 1000) return
        lastCheck = now
        Thread {
            try {
                val c = URL("https://github.com/$repo/releases/latest/download/version.txt")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val latest = c.inputStream.bufferedReader().readText().trim().toInt()
                a.runOnUiThread {
                    if (a.isFinishing) return@runOnUiThread
                    if (latest > BuildConfig.VERSION_CODE) ask(a, repo)
                    else if (manual) Toast.makeText(a, "إنت على آخر نسخة ✓", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                if (manual) a.runOnUiThread { Toast.makeText(a, "مقدرتش أفحص التحديث", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun ask(a: Activity, repo: String) {
        AlertDialog.Builder(a).setTitle("نسخة جديدة متاحة")
            .setMessage("هيتنزّل التحديث جوه التطبيق ويتثبّت فوق النسخة الحالية (مش هتحتاج تمسح حاجة).")
            .setPositiveButton("تحديث") { _, _ -> download(a, "https://github.com/$repo/releases/latest/download/app-debug.apk") }
            .setNegativeButton("بعدين", null).show()
    }

    private fun keepOn(a: Activity, on: Boolean) {
        if (on) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun download(a: Activity, url: String) {
        if (busy) return
        // أندرويد 8+: لازم إذن «تثبيت تطبيقات غير معروفة» للتطبيق ده مرة واحدة
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "فعّل «السماح بالتثبيت» للتطبيق ده ثم ارجع واضغط تحديث", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        busy = true
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val tv = TextView(a).apply { text = "بنزّل التحديث..."; setPadding(0, 0, 0, 16) }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 8)
            addView(tv); addView(bar)
        }
        val dlg = AlertDialog.Builder(a).setTitle("تحديث التطبيق").setView(box).setCancelable(false).create()
        dlg.show()
        keepOn(a, true)
        Thread {
            try {
                val dir = File(a.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val f = File(dir, "update.apk")
                var total = -1L
                var done = 0L
                var lastP = -1
                var fails = 0
                while (true) {
                    try {
                        var u = url
                        var c: HttpURLConnection
                        var hops = 0
                        while (true) {
                            c = URL(u).openConnection() as HttpURLConnection
                            c.instanceFollowRedirects = false
                            c.connectTimeout = 20000; c.readTimeout = 60000
                            if (done > 0) c.setRequestProperty("Range", "bytes=$done-")
                            val code = c.responseCode
                            if (code in 300..399 && hops++ < 6) {
                                u = URL(URL(u), c.getHeaderField("Location")).toString()
                                c.disconnect(); continue
                            }
                            if (code !in 200..299) throw Exception("HTTP $code")
                            break
                        }
                        // 206 = السيرفر كمّل من المكان اللي وقفنا عنده، غير كده نبدأ من الأول
                        val append = c.responseCode == 206 && done > 0
                        if (!append) { done = 0; total = c.contentLengthLong }
                        FileOutputStream(f, append).use { o ->
                            c.inputStream.use { i ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = i.read(buf); if (r < 0) break
                                    o.write(buf, 0, r); done += r
                                    if (total > 0) {
                                        val p = (done * 100 / total).toInt()
                                        if (p != lastP) {
                                            lastP = p
                                            a.runOnUiThread { bar.progress = p; tv.text = "بنزّل التحديث... $p%" }
                                        }
                                    }
                                }
                            }
                        }
                        if (total > 0 && done < total) throw Exception("الملف وصل ناقص")
                        break
                    } catch (e: Exception) {
                        if (++fails > 15) throw e
                        Thread.sleep(2000)
                    }
                }
                a.runOnUiThread { keepOn(a, false); dlg.dismiss(); busy = false; install(a, f) }
            } catch (e: Exception) {
                busy = false
                a.runOnUiThread {
                    keepOn(a, false); dlg.dismiss()
                    Toast.makeText(a, "فشل التحديث: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun install(a: Activity, f: File) {
        try {
            val uri = FileProvider.getUriForFile(a, a.packageName + ".fileprovider", f)
            a.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(a, "مقدرتش أفتح التثبيت: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
