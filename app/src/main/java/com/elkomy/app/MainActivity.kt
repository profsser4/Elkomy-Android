package com.elkomy.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    lateinit var web: WebView
    companion object { var inst: MainActivity? = null }
    private var dialogOpen = false
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    inner class Bridge {
        @JavascriptInterface
        fun play(url: String, title: String, startSec: Int) = runOnUiThread {
            startActivity(Intent(this@MainActivity, PlayerActivity::class.java)
                .putExtra("url", url).putExtra("title", title).putExtra("start", startSec))
        }
        @JavascriptInterface
        fun download(url: String, name: String) = runOnUiThread { dl(url, name) }
        @JavascriptInterface
        fun saveText(name: String, text: String) = runOnUiThread { saveTxt(name, text) }
        @JavascriptInterface
        fun changeServer() = runOnUiThread { askUrl("تغيير السيرفر") }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        inst = this
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        web.addJavascriptInterface(Bridge(), "AndroidPlayer")
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
    }

    private fun checkUpdate() {
        val repo = BuildConfig.REPO
        if (!repo.contains("/") || repo.contains("CHANGE-ME")) return
        Thread {
            try {
                val c = java.net.URL("https://github.com/$repo/releases/latest/download/version.txt")
                    .openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val latest = c.inputStream.bufferedReader().readText().trim().toInt()
                if (latest > BuildConfig.VERSION_CODE) runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    AlertDialog.Builder(this).setTitle("نسخة جديدة متاحة")
                        .setMessage("في تحديث جديد للتطبيق. اضغط تحديث لتنزيله وتثبيته فوق النسخة الحالية.")
                        .setPositiveButton("تحديث") { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/$repo/releases/latest/download/app-debug.apk")))
                        }.setNegativeButton("بعدين", null).show()
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun clean(n: String) = n.replace(Regex("[\\\\/:*?\"<>|]+"), "_").ifBlank { "video.mkv" }

    private fun dl(url: String, name: String) {
        try {
            val n = clean(name)
            val r = DownloadManager.Request(Uri.parse(url)).setTitle(n)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Elkomy/$n")
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
            Toast.makeText(this, "بدأ التحميل: Download/Elkomy", Toast.LENGTH_LONG).show()
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
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
