package com.elkomy.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.*
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    lateinit var web: WebView
    companion object { var inst: MainActivity? = null }
    private var dialogOpen = false
    private var custom: android.view.View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private fun hideCustom() {
        val v = custom ?: return
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        custom = null
        customCb?.onCustomViewHidden(); customCb = null
        web.visibility = android.view.View.VISIBLE
        window.decorView.systemUiVisibility = 0
    }
    override fun onResume() { super.onResume(); UpdateManager.check(this) }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) try { web.clearCache(false) } catch (_: Exception) {}
    }
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    inner class Bridge {
        @JavascriptInterface
        fun play(url: String, title: String, startSec: Int) = runOnUiThread {
            startActivity(Intent(this@MainActivity, PlayerActivity::class.java)
                .putExtra("url", url).putExtra("title", title).putExtra("start", startSec))
        }
        /** بيعرض زرار «الحلقة التالية» + عدّاد في آخر 200 ثانية جوه المشغّل الداخلي */
        @JavascriptInterface
        fun showNext(label: String, left: Int) = runOnUiThread { PlayerActivity.inst?.setNext(label) }
        /** بيضيف الملف في طابور التحميل (ملف ورا ملف تلقائي) */
        @JavascriptInterface
        fun download(url: String, name: String) = runOnUiThread { dl(url, name) }
        /** نفس التحميل بس مع وصف يستخدمه التطبيق يجدّد الرابط (debrid) لو انتهى وإنت بتكمّل */
        @JavascriptInterface
        fun downloadR(url: String, name: String, src: String) = runOnUiThread { dl(url, name, src) }
        @JavascriptInterface
        fun setLink(id: Long, url: String) { DownloadQueue.setLink(id, url) }
        @JavascriptInterface
        fun refreshFail(id: Long, msg: String) { DownloadQueue.refreshFail(id, msg) }
        /** بيفتح شاشة التحميلات (العدّاد + النسبة) */
        @JavascriptInterface
        fun openDownloads() = runOnUiThread {
            startActivity(Intent(this@MainActivity, DownloadsActivity::class.java))
        }
        @JavascriptInterface
        fun saveText(name: String, text: String) = runOnUiThread { saveTxt(name, text) }
        @JavascriptInterface
        fun checkUpdate() = runOnUiThread { UpdateManager.check(this@MainActivity, true) }
        @JavascriptInterface
        fun appVersion(): Int = BuildConfig.VERSION_CODE
        @JavascriptInterface
        fun dlActive(): Int = DownloadQueue.activeCount()
        @JavascriptInterface
        fun changeServer() {} // مقفول: اللينك ثابت جوه التطبيق

        /**
         * جسر الـIPTV: الطلب بيطلع من التليفون نفسه (آيبي مصري + http مسموح + من غير CORS).
         * بيشتغل في الخلفية، ولما يخلص بينادي window.__nh(id, status) وبعدين الصفحة تاخد النتيجة بـ httpTake(id).
         * keep = أسماء حقول مفصولة بفاصلة: لو الرد قايمة كبيرة بنسيب الحقول دي بس (أخف بكتير على الصفحة).
         * ttlMin = كام دقيقة نعيد استخدام نسخة محفوظة على التليفون (0 = من غير كاش).
         */
        @JavascriptInterface
        fun httpGet(id: String, url: String, keep: String, ttlMin: Int) { iptvFetch(id, url, keep, ttlMin) }
        @JavascriptInterface
        fun httpTake(id: String): String = httpRes.remove(id) ?: ""
    }

    private val httpRes = java.util.concurrent.ConcurrentHashMap<String, String>()
    // طلب كبير واحد-اتنين في نفس الوقت بس، عشان الذاكرة ما تتملاش (كان السبب في OutOfMemoryError)
    private val bigGate = java.util.concurrent.Semaphore(2)
    private val MAX_RAW = 40L * 1024 * 1024

    private fun iptvFetch(id: String, url: String, keep: String, ttlMin: Int) {
        Thread {
            var status = 0
            var out = ""
            var gated = false
            try {
                val dir = File(cacheDir, "iptv").apply { mkdirs() }
                val cf = File(dir, java.security.MessageDigest.getInstance("SHA-1")
                    .digest((url + "|" + keep).toByteArray()).joinToString("") { "%02x".format(it) } + ".json")
                if (ttlMin > 0 && cf.exists() && System.currentTimeMillis() - cf.lastModified() < ttlMin * 60_000L && cf.length() < 12L * 1024 * 1024) {
                    out = cf.readText(); status = 200
                } else {
                    bigGate.acquire(); gated = true
                    var u = url
                    var hops = 0
                    while (true) {
                        val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                        c.connectTimeout = 15000; c.readTimeout = 60000
                        c.instanceFollowRedirects = false
                        c.setRequestProperty("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                        status = c.responseCode
                        if (status in 301..308 && hops++ < 5) {
                            val loc = c.getHeaderField("Location")
                            if (!loc.isNullOrBlank()) { u = java.net.URL(java.net.URL(u), loc).toString(); c.disconnect(); continue }
                        }
                        if (status in 200..299) {
                            // بنكتب الناتج في ملف مؤقت وإحنا بنقرا الستريم (من غير ما نحط الرد الكامل في الذاكرة)
                            val tmp = File(dir, cf.name + ".tmp")
                            c.inputStream.use { st -> streamToFile(st, keep, tmp) }
                            if (tmp.length() > 12L * 1024 * 1024) { tmp.delete(); throw RuntimeException("الرد كبير جدًا، اختار قسم أصغر") }
                            out = tmp.readText()
                            if (ttlMin > 0) { cf.delete(); tmp.renameTo(cf) } else tmp.delete()
                        } else {
                            out = (c.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "").take(300)
                        }
                        c.disconnect()
                        break
                    }
                }
            } catch (e: Throwable) {
                status = 0
                out = (e.message ?: e.javaClass.simpleName).take(200)
                try { System.gc() } catch (_: Throwable) {}
            } finally { if (gated) bigGate.release() }
            httpRes[id] = out
            runJs("window.__nh&&__nh(${org.json.JSONObject.quote(id)},$status)")
        }.start()
    }

    /** بيقرا JSON ستريم وبيكتب الحقول المطلوبة بس (من غير ما يبني الشجرة كلها في الذاكرة) */
    private fun streamToFile(input: java.io.InputStream, keep: String, dest: File) {
        val keys = keep.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
        java.io.BufferedWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(dest), Charsets.UTF_8), 32768).use { w ->
            if (keys.isEmpty()) {
                // من غير فلترة: ننسخ بايتات بحد أقصى
                val r = java.io.InputStreamReader(input, Charsets.UTF_8)
                val buf = CharArray(16384); var total = 0L
                while (true) {
                    val n = r.read(buf); if (n < 0) break
                    total += n; if (total > MAX_RAW) throw RuntimeException("الرد كبير جدًا")
                    w.write(buf, 0, n)
                }
                return
            }
            val jr = android.util.JsonReader(java.io.InputStreamReader(input, Charsets.UTF_8))
            val jw = android.util.JsonWriter(w)
            if (jr.peek() != android.util.JsonToken.BEGIN_ARRAY) { jr.skipValue(); jw.beginArray(); jw.endArray(); jw.flush(); return }
            jr.beginArray(); jw.beginArray()
            while (jr.hasNext()) {
                if (jr.peek() != android.util.JsonToken.BEGIN_OBJECT) { jr.skipValue(); continue }
                jr.beginObject(); jw.beginObject()
                while (jr.hasNext()) {
                    val k = jr.nextName()
                    if (k !in keys) { jr.skipValue(); continue }
                    when (jr.peek()) {
                        android.util.JsonToken.STRING -> jw.name(k).value(jr.nextString())
                        android.util.JsonToken.NUMBER -> { val d = jr.nextDouble(); if (d % 1.0 == 0.0 && Math.abs(d) < 9e15) jw.name(k).value(d.toLong()) else jw.name(k).value(d) }
                        android.util.JsonToken.BOOLEAN -> jw.name(k).value(jr.nextBoolean())
                        android.util.JsonToken.NULL -> { jr.nextNull(); jw.name(k).nullValue() }
                        else -> jr.skipValue()
                    }
                }
                jr.endObject(); jw.endObject()
            }
            jr.endArray(); jw.endArray(); jw.flush()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWeb() {
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        web.addJavascriptInterface(Bridge(), "AndroidPlayer")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(v: android.view.View, cb: CustomViewCallback) {
                if (custom != null) { cb.onCustomViewHidden(); return }
                custom = v; customCb = cb
                addContentView(v, android.view.ViewGroup.LayoutParams(-1, -1))
                web.visibility = android.view.View.GONE
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            override fun onHideCustomView() { hideCustom() }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, u: String?) {
                // شارة صغيرة بتعرض رقم النسخة؛ لو دوست عليها بيفحص التحديث
                val js = "(function(){if(document.getElementById('appVerBadge'))return;" +
                    "var b=document.createElement('div');b.id='appVerBadge';" +
                    "b.textContent='v${BuildConfig.VERSION_CODE} ⟳';" +
                    "b.style.cssText='position:fixed;bottom:6px;left:6px;z-index:2147483647;" +
                    "background:rgba(0,0,0,.55);color:#fff;font:11px sans-serif;padding:3px 8px;border-radius:10px';" +
                    "b.onclick=function(){AndroidPlayer.checkUpdate()};" +
                    "document.body.appendChild(b)})()"
                v.evaluateJavascript(js, null)
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) retryDialog()
            }
            @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
                // المتصفح الداخلي وقع (غالبًا ذاكرة) — من غير الدالة دي أندرويد كان بيقفل التطبيق كله
                runOnUiThread {
                    try { (v.parent as? android.view.ViewGroup)?.removeView(v); v.destroy() } catch (_: Exception) {}
                    buildWeb()
                    web.loadUrl(if (bakedOk()) BuildConfig.SERVER_URL else (prefs.getString("url", "") ?: ""))
                    Toast.makeText(this@MainActivity, "الصفحة وقفت (ذاكرة) واتفتحت تاني", Toast.LENGTH_LONG).show()
                }
                return true
            }
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url.toString()
                if (u.startsWith("http")) return false
                try {
                    val i = if (u.startsWith("intent:")) Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
                            else Intent(Intent.ACTION_VIEW, Uri.parse(u))
                    startActivity(i)
                } catch (_: Exception) {}
                return true
            }
        }
        web.setDownloadListener { url, _, cd, mime, _ -> dl(url, URLUtil.guessFileName(url, cd, mime)) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        inst = this
        val oldH = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { prefs.edit().putString("lastCrash", android.util.Log.getStackTraceString(e).take(1500)).commit() } catch (_: Exception) {}
            oldH?.uncaughtException(t, e)
        }
        prefs.getString("lastCrash", null)?.let { c ->
            prefs.edit().remove("lastCrash").apply()
            AlertDialog.Builder(this).setTitle("آخر خروج مفاجئ — ابعتلي النص ده").setMessage(c).setPositiveButton("تمام", null).show()
        }
        DownloadQueue.load(this)
        buildWeb()
        if (bakedOk()) web.loadUrl(BuildConfig.SERVER_URL)
        else askUrl("اللينك مش متضبط في server_url.txt") // بيظهر بس لو البناء فيه CHANGE-ME
        checkUpdate()
        askNotifPermission()
    }

    /** أندرويد 13+ محتاج إذن الإشعارات عشان إشعار نسبة التحميل يظهر */
    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
        }
    }

    private fun checkUpdate() = UpdateManager.check(this)

    private fun clean(n: String) = n.replace(Regex("[\\\\/:*?\"<>|]+"), "_").ifBlank { "video.mkv" }

    /** التحميل بقى بيدخل طابور التطبيق: كل ملف يبدأ بعد اللي قبله لوحده */
    fun runJs(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    private fun dl(url: String, name: String, src: String = "") {
        try {
            val n = clean(name)
            val before = DownloadQueue.items.count {
                it.state == DownloadQueue.State.QUEUED || it.state == DownloadQueue.State.RUNNING
            }
            val q = DownloadQueue.enqueue(this, url, n, src)
            val tag = dlEpOf(q)?.let { e -> " · الحلقة ${e.second}" } ?: ""
            Toast.makeText(
                this,
                if (before == 0) "بدأ التحميل ⬇$tag (تابعه من زرار التحميلات)" else "اتضاف للطابور$tag · رقم ${before + 1}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) { Toast.makeText(this, "فشل التحميل: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun saveTxt(name: String, text: String) {
        try {
            val n = clean(name)
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, n)
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/TorFlix")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            } else File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), n).writeText(text)
            Toast.makeText(this, "تم حفظ الترجمة: $n", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "فشل حفظ الترجمة", Toast.LENGTH_LONG).show() }
    }

    private fun bakedOk(): Boolean {
        val b = BuildConfig.SERVER_URL
        return b.startsWith("http") && !b.contains("CHANGE-ME")
    }

    /** لما النت يفصل: زرار إعادة المحاولة بس، من غير ما حد يكتب لينك */
    private fun retryDialog() {
        if (dialogOpen) return
        if (!bakedOk()) { askUrl("تعذّر الاتصال بالسيرفر"); return }
        dialogOpen = true
        AlertDialog.Builder(this).setTitle("تعذّر الاتصال بالسيرفر")
            .setMessage("اتأكد من النت وجرّب تاني.")
            .setCancelable(false)
            .setPositiveButton("إعادة المحاولة") { _, _ -> dialogOpen = false; web.loadUrl(BuildConfig.SERVER_URL) }
            .setNegativeButton("خروج") { _, _ -> dialogOpen = false; finish() }
            .show()
    }

    private fun askUrl(msg: String) {
        if (dialogOpen) return
        dialogOpen = true
        val et = EditText(this).apply { setText(prefs.getString("url", "https://")); setSingleLine() }
        AlertDialog.Builder(this).setTitle(msg).setView(et).setCancelable(false)
            .setPositiveButton("حفظ") { _, _ ->
                var u = et.text.toString().trim()
                if (!u.startsWith("http")) u = "https://$u"
                prefs.edit().putString("url", u).apply()
                dialogOpen = false
                web.loadUrl(u)
            }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (custom != null) hideCustom() else if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
