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
        fun changeServer() = runOnUiThread { askUrl("تغيير السيرفر") }

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

    private fun iptvFetch(id: String, url: String, keep: String, ttlMin: Int) {
        Thread {
            var status = 0
            var out = ""
            try {
                val dir = File(cacheDir, "iptv").apply { mkdirs() }
                val cf = File(dir, java.security.MessageDigest.getInstance("SHA-1")
                    .digest((url + "|" + keep).toByteArray()).joinToString("") { "%02x".format(it) } + ".json")
                if (ttlMin > 0 && cf.exists() && System.currentTimeMillis() - cf.lastModified() < ttlMin * 60_000L) {
                    out = cf.readText(); status = 200
                } else {
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
                        val st = if (status in 200..399) c.inputStream else c.errorStream
                        val raw = st?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                        out = if (status in 200..299) trimJson(raw, keep) else raw.take(300)
                        if (status in 200..299 && ttlMin > 0) try { cf.writeText(out) } catch (_: Exception) {}
                        c.disconnect()
                        break
                    }
                }
            } catch (e: Throwable) {
                status = 0
                out = (e.message ?: e.javaClass.simpleName).take(200)
            }
            httpRes[id] = out
            runJs("window.__nh&&__nh(${org.json.JSONObject.quote(id)},$status)")
        }.start()
    }

    /** قايمة JSON كبيرة -> نسيب الحقول المطلوبة بس */
    private fun trimJson(raw: String, keep: String): String {
        if (keep.isBlank()) return raw
        val t = raw.trimStart()
        if (!t.startsWith("[")) return raw
        return try {
            val keys = keep.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val arr = org.json.JSONArray(t)
            val res = org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val n = org.json.JSONObject()
                for (k in keys) if (o.has(k)) n.put(k, o.get(k))
                res.put(n)
            }
            res.toString()
        } catch (_: Throwable) { raw }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        inst = this
        DownloadQueue.load(this)
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
                if (r.isForMainFrame) askUrl("تعذّر الاتصال بالسيرفر")
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
        val baked = BuildConfig.SERVER_URL
        val url = if (baked.startsWith("http") && !baked.contains("CHANGE-ME")) baked else prefs.getString("url", null)
        if (url == null) askUrl("اكتب رابط السيرفر") else web.loadUrl(url)
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
            DownloadQueue.enqueue(this, url, n, src)
            Toast.makeText(
                this,
                if (before == 0) "بدأ التحميل ⬇ (تابعه من زرار التحميلات)" else "اتضاف للطابور · رقم ${before + 1}",
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
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Elkomy")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            } else File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), n).writeText(text)
            Toast.makeText(this, "تم حفظ الترجمة: $n", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "فشل حفظ الترجمة", Toast.LENGTH_LONG).show() }
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
