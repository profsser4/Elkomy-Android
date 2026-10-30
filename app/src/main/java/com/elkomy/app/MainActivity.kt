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
        /** بيضيف الملف في طابور التحميل (ملف ورا ملف تلقائي) */
        @JavascriptInterface
        fun download(url: String, name: String) = runOnUiThread { dl(url, name) }
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
        fun changeServer() = runOnUiThread { askUrl("تغيير السيرفر") }
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
    private fun dl(url: String, name: String) {
        try {
            val n = clean(name)
            val before = DownloadQueue.items.count {
                it.state == DownloadQueue.State.QUEUED || it.state == DownloadQueue.State.RUNNING
            }
            DownloadQueue.enqueue(this, url, n)
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
