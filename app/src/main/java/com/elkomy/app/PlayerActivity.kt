package com.elkomy.app

import android.app.AlertDialog
import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Rational
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.PathParser
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.util.VLCVideoLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** أيقونات بشكل نتفلكس (Material paths) من غير ملفات صور */
class IconView(ctx: Context, kind: Int) : View(ctx) {
    companion object {
        const val PLAY = 0
        const val PAUSE = 1
        const val BACK = 2
        const val R10 = 3
        const val F10 = 4
        const val SUBS = 5
        const val SPEED = 6
        const val ZOOM = 7
        const val CAST = 8
        const val DL = 9
        const val LOCK = 10
        const val PIP = 11
        const val TIMER = 12
        const val AI = 13
        const val TUNE = 14
        private val D = arrayOf(
            "M8 5v14l11-7z",
            "M6 19h4V5H6v14zm8-14v14h4V5h-4z",
            "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
            "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z",
            "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z",
            "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 12h4v2H4v-2zm10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z",
            "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z",
            "M19 12h-2v3h-3v2h5v-5zM7 9h3V7H5v5h2V9zm14-6H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16.01H3V4.99h18v14.02z",
            "M1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11zm20-7H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z",
            "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
            "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z",
            "M19 11h-8v6h8v-6zm4 8V4.98C23 3.88 22.1 3 21 3H3c-1.1 0-2 .88-2 1.98V19c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2zm-2 .02H3V4.97h18v14.05z",
            "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
            "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z",
            "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"
        )
    }

    private var k = kind
    private var path: Path = PathParser.createPathFromPathData(D[kind]) ?: Path()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 7.5f
    }

    fun setKind(n: Int) {
        if (n == k) return
        k = n
        path = PathParser.createPathFromPathData(D[n]) ?: Path()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val aw = width - paddingLeft - paddingRight
        val ah = height - paddingTop - paddingBottom
        val s = minOf(aw, ah).toFloat()
        if (s <= 0f) return
        c.save()
        c.translate(paddingLeft + (aw - s) / 2f, paddingTop + (ah - s) / 2f)
        c.scale(s / 24f, s / 24f)
        c.save()
        if (k == F10) c.scale(-1f, 1f, 12f, 12f)
        c.drawPath(path, p)
        c.restore()
        if (k == R10 || k == F10) c.drawText("10", 12f, 16.6f, p)
        c.restore()
    }
}

class PlayerActivity : AppCompatActivity() {
    companion object {
        @Volatile var inst: PlayerActivity? = null
        const val AI_URL = "https://api.anthropic.com/v1/messages"
        const val AI_MODEL = "claude-haiku-5-5"
    }
    private lateinit var vlc: LibVLC
    private lateinit var mp: MediaPlayer
    private lateinit var video: VLCVideoLayout
    private lateinit var root: FrameLayout
    private lateinit var ui: FrameLayout
    private lateinit var seek: SeekBar
    private lateinit var tvRemain: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var playBtn: IconView
    private lateinit var spinner: ProgressBar
    private lateinit var ripL: TextView
    private lateinit var ripR: TextView

    private val h = Handler(Looper.getMainLooper())
    private var dragging = false
    private var started = false
    private var n = 0
    private var sub = ""
    private var subAdded = false
    private var titleText = ""
    private var pfd: ParcelFileDescriptor? = null

    private var uiShown = true
    private var panel: Dialog? = null
    private var zoomMode = 0   // 0 طبيعي (شرايط سودا) · 1 تمديد بدون قص · 2 ملء الشاشة بقص
    private var accum = 0
    private var lastSeekAt = 0L
    private var lastDir = 0
    private val ripHide = arrayOfNulls<Runnable>(2)

    // زرار «الحلقة التالية»: ثابت في الشريط السفلي (برقم الحلقة) + زرار كبير بعدّاد في آخر 250 ثانية
    private var nextLabel = ""
    private var nextBtn: LinearLayout? = null
    private var nextLbl: TextView? = null
    private var nextTime: TextView? = null
    private var nextItem: LinearLayout? = null
    private var dlItem: LinearLayout? = null
    private var skipStopReport = false

    // قايمة تشغيل محلية (حلقات اتحمّلت): لما الحلقة تخلص بتفتح اللي بعدها لوحدها
    private var plUrls: Array<String> = emptyArray()
    private var plTitles: Array<String> = emptyArray()
    private var plIdx = -1
    private fun hasLocalNext() = plIdx >= 0 && plIdx + 1 < plUrls.size
    private fun plLabel(i: Int) = plTitles.getOrNull(i)?.substringBefore(" · ") ?: ""

    // الانتقال للحلقة التالية من غير ما نقفل المشغّل (زي نتفلكس)
    @Volatile var autoNext = true
    private var waiting = false       // مستني الصفحة تجيب رابط الحلقة التالية
    private var swapping = false      // الحلقة الجديدة اتحمّلت ومستنيين أول إطار
    private var curUrl = ""
    private var tvTitle: TextView? = null
    private var waitLayer: LinearLayout? = null
    private var waitText: TextView? = null
    private val waitTimeout = Runnable {
        if (waiting) {
            Toast.makeText(this, "تعذّر تجهيز الحلقة التالية", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val RED = 0xFFE50914.toInt()

    // ---- ترقيات جديدة ----
    private var hwOn = true               // فك تشفير بالهاردوير، ولو فشل بنرجع للسوفتوير تلقائي
    private var hwRetried = false
    private var resolveTried = false      // محاولة تانية بعد حل تحويلات الرابط يدويًا (TorBox وغيره)
    private var resolvedUrl: String? = null
    private val BROWSER_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private var locked = false
    private var lockBtn: IconView? = null
    private var hud: TextView? = null
    private val hudHide = Runnable { hud?.visibility = View.GONE }
    private val lockHide = Runnable { lockBtn?.visibility = View.GONE }
    private var gMode = 0                 // 0 لا شيء · 1 سطوع · 2 صوت · 3 تقديم/تأخير بالسحب
    private var gStartBright = 0.5f
    private var gStartVol = 100
    private var gStartPos = 0L
    private var gTarget = 0L
    private var longFast = false
    private var prevRate = 1f
    private var aspectIdx = 0
    private val aspects = arrayOf<String?>(null, "16:9", "4:3", "21:9", "1:1")
    private var eqIdx = -1
    private var sleepRun: Runnable? = null
    private var sleepAtEnd = false
    private var autoPip = true
    private var aiBusy = false
    private var picked = false
    private var lastPos = 0L              // أبعد نقطة اتشغّلت (بنستخدمها نفرّق بين نهاية الفيلم وفشل التشغيل)

    private val hideRun = Runnable { hideUi() }

    private val tick = object : Runnable {
        override fun run() {
            if (waiting) { h.postDelayed(this, 500); return }
            if (inPip() && uiShown) hideUi()
            if (++n % 20 == 0) report(false)
            if (cast != null) {
                val cl = castLen
                if (!dragging && cl > 0) {
                    seek.progress = (castPos * 1000 / cl).toInt().coerceIn(0, 1000)
                    tvRemain.text = fmt((cl - castPos) * 1000)
                }
                playBtn.setKind(if (castPlaying) IconView.PAUSE else IconView.PLAY)
                if (cl > 10 && castPos >= cl - 2 && !castFired && System.currentTimeMillis() - castSince > 8000) {
                    castFired = true
                    onEnded()
                }
            } else {
                val len = mp.length
                if (!dragging && len > 0) {
                    val t = mp.time
                    seek.progress = (mp.position * 1000).toInt()
                    tvRemain.text = fmt(len - t)
                }
                playBtn.setKind(if (mp.isPlaying) IconView.PAUSE else IconView.PLAY)
                if (mp.isPlaying) { val tn = mp.time; if (tn > lastPos) lastPos = tn }
            }
            updateNext()
            h.postDelayed(this, 500)
        }
    }

    private fun report(ended: Boolean) {
        val len = curLen()
        val pos = curPos()
        if (len <= 0) return
        MainActivity.inst?.web?.evaluateJavascript("window.nativeProgress&&nativeProgress($pos,$len,$ended)", null)
    }

    private fun fmt(ms: Long): String {
        val s = maxOf(0L, ms) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun round(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun focusScale(v: View) {
        v.setOnFocusChangeListener { x, f ->
            x.animate().scaleX(if (f) 1.15f else 1f).scaleY(if (f) 1.15f else 1f).setDuration(120).start()
        }
    }

    private fun iconBtn(kind: Int, onClick: () -> Unit) = IconView(this, kind).apply {
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick(); showUi() }
        focusScale(this)
    }

    private fun labeled(kind: Int, label: String, onClick: () -> Unit): LinearLayout {
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            isClickable = true
            isFocusable = true
            addView(IconView(ctx, kind), LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(TextView(ctx).apply {
                this.text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            setOnClickListener { onClick() }
            focusScale(this)
        }
    }

    private fun rip(left: Boolean): TextView {
        val w = (resources.displayMetrics.widthPixels * 0.32f).toInt()
        return TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
            alpha = 0f
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(200).toFloat()
                colors = intArrayOf(0x55FFFFFF, 0x00FFFFFF)
            }
            layoutParams = FrameLayout.LayoutParams(w, -1, if (left) Gravity.START else Gravity.END)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val raw = intent.getStringExtra("title") ?: intent.data?.lastPathSegment ?: ""
        sub = raw.substringAfter("\n@@SUB@@", "")
        titleText = raw.substringBefore("\n@@SUB@@")
        plUrls = intent.getStringArrayExtra("pl_urls") ?: emptyArray()
        plTitles = intent.getStringArrayExtra("pl_titles") ?: emptyArray()
        plIdx = if (plUrls.isNotEmpty() && plUrls.size == plTitles.size) intent.getIntExtra("pl_idx", -1) else -1
        if (hasLocalNext()) nextLabel = plLabel(plIdx + 1)
        val vopts = arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--network-caching=1500", "--http-user-agent=$BROWSER_UA")
        arabicFontPath()?.let { vopts.add("--freetype-font=$it") }   // خط عربي لترجمة VLC (من غيره بتظهر مربعات)
        vlc = LibVLC(this, vopts)
        mp = MediaPlayer(vlc)
        mp.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.EndReached -> { if (cast == null) onEnded() }
                MediaPlayer.Event.Playing -> {
                    if (swapping) {
                        swapping = false
                        waitLayer?.visibility = View.GONE
                        showUi()
                    }
                    spinner.visibility = View.GONE
                    playBtn.setKind(IconView.PAUSE)
                    scheduleHide()
                    if (sub.isNotEmpty() && !subAdded) {
                        subAdded = true
                        loadOwnSub(sub)
                    }
                    if (ownOn && mp.spuTrack != -1) { try { mp.setSpuTrack(-1) } catch (_: Throwable) {} }
                    if (!picked) {
                        picked = true
                        h.postDelayed({ autoPickTracks() }, 1800)
                    }
                }
                MediaPlayer.Event.Paused -> {
                    playBtn.setKind(IconView.PLAY)
                    h.removeCallbacks(hideRun)
                }
                MediaPlayer.Event.Buffering -> spinner.visibility = if (ev.buffering < 100f) View.VISIBLE else View.GONE
                MediaPlayer.Event.EncounteredError -> {
                    if (hwOn && !hwRetried && cast == null && curUrl.isNotEmpty()) {
                        // أول فشل: نجرّب فك التشفير بالسوفتوير (بيشغّل أي كودك) من نفس المكان
                        hwRetried = true
                        hwOn = false
                        Toast.makeText(this, "بجرّب وضع التوافق الكامل...", Toast.LENGTH_SHORT).show()
                        val p = (maxOf(0L, mp.time) / 1000).toInt()
                        h.post { if (!isDestroyed) { loadMedia(curUrl, p); mp.play() } }
                    } else if (!resolveTried && cast == null && curUrl.startsWith("http")) {
                        // تاني فشل: نعدّي الفيديو على بروكسي محلي (شبكة الأندرويد بدل اتصال VLC المباشر)
                        resolveTried = true
                        Toast.makeText(this, "بجرّب مسار تاني للرابط...", Toast.LENGTH_SHORT).show()
                        val cu = curUrl
                        val p = (maxOf(0L, mp.time) / 1000).toInt()
                        val px = startProxy(cu)
                        if (px != null) {
                            resolvedUrl = px
                            h.post { if (!isDestroyed) { loadMedia(cu, p); mp.play() } }
                        } else failFinal()
                    } else {
                        failFinal()
                    }
                }
                else -> {}
            }
        }
        buildUi()
        inst = this
        h.post(subTick)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        video = VLCVideoLayout(this)
        root.addView(video, FrameLayout.LayoutParams(-1, -1))

        // طبقة اللمس: لمسة = إظهار/إخفاء، لمستين يمين/شمال = ±10 ثواني، لمستين في النص = تشغيل/إيقاف
        val touch = View(this)
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            private fun side(x: Float): Int {
                val w = touch.width.toFloat()
                return if (x < w * 0.35f) -1 else if (x > w * 0.65f) 1 else 0
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (locked) return false
                if (System.currentTimeMillis() - lastSeekAt < 700) {
                    val s = side(e.x)
                    if (s != 0) stepSeek(s)
                }
                return false
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (locked) { showLockBtn(); return true }
                if (System.currentTimeMillis() - lastSeekAt < 900) return true
                if (uiShown) hideUi() else showUi()
                return true
            }
            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                if (locked) return true
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    val s = side(e.x)
                    if (s == 0) togglePlay() else stepSeek(s)
                }
                return true
            }
            // سحب رأسي يمين = صوت (لحد 200%) · شمال = سطوع · سحب أفقي = تقديم/تأخير مع معاينة
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (locked || e1 == null || e2.pointerCount > 1) return false
                val w = touch.width.toFloat()
                val hh = touch.height.toFloat()
                if (w <= 0f || hh <= 0f) return false
                if (gMode == 0) {
                    val ax = Math.abs(e2.x - e1.x)
                    val ay = Math.abs(e2.y - e1.y)
                    gMode = if (ax > ay * 1.4f) 3 else if (e1.x < w / 2f) 1 else 2
                    when (gMode) {
                        1 -> gStartBright = currentBrightness()
                        2 -> if (cast != null) gMode = -1 else gStartVol = mp.volume
                        3 -> if (cast != null || mp.length <= 0) gMode = -1 else {
                            gStartPos = mp.time
                            gTarget = gStartPos
                            h.removeCallbacks(hideRun)
                        }
                    }
                }
                val d = (e1.y - e2.y) / hh
                when (gMode) {
                    1 -> {
                        val v = (gStartBright + d * 1.3f).coerceIn(0.02f, 1f)
                        setBrightness(v)
                        showHud("🔆 " + (v * 100).toInt() + "%")
                    }
                    2 -> {
                        val v = (gStartVol + d * 220f).toInt().coerceIn(0, 200)
                        mp.setVolume(v)
                        showHud((if (v == 0) "🔇 " else "🔊 ") + v + "%")
                    }
                    3 -> {
                        val len = mp.length
                        gTarget = (gStartPos + ((e2.x - e1.x) / w * 150000f).toLong()).coerceIn(0L, maxOf(0L, len - 1500L))
                        val diff = (gTarget - gStartPos) / 1000
                        showHud((if (diff >= 0) "+" else "-") + fmt(Math.abs(diff) * 1000) + "   (" + fmt(gTarget) + ")")
                    }
                }
                return true
            }
            // ضغطة مطوّلة = سرعة 2x لحد ما تسيب إيدك
            override fun onLongPress(e: MotionEvent) {
                if (locked || cast != null || gMode != 0 || !mp.isPlaying) return
                prevRate = mp.rate
                longFast = true
                mp.setRate(2f)
                showHud("⏩ 2x", 0L)
            }
        })
        touch.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            if (e.actionMasked == MotionEvent.ACTION_UP) endGesture(true)
            else if (e.actionMasked == MotionEvent.ACTION_CANCEL) endGesture(false)
            true
        }
        root.addView(touch, FrameLayout.LayoutParams(-1, -1))

        ripL = rip(true)
        ripR = rip(false)
        root.addView(ripL)
        root.addView(ripR)

        spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(RED)
        }
        root.addView(spinner, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))

        // ---- الترجمة (بنرسمها بنفسنا): بتطلع فوق شريط التحكم لما يظهر وتنزل لما يختفي (زي نتفلكس)
        subView = object : TextView(this) {
            override fun onDraw(c: Canvas) { super.onDraw(c); super.onDraw(c) }   // مرتين عشان الضل يبان كحدود سودا واضحة
        }.apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
            setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        root.addView(subView, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(34); marginStart = dp(56); marginEnd = dp(56)
        })

        ui = FrameLayout(this)

        // ---- الشريط العلوي: رجوع + العنوان
        val back = iconBtn(IconView.BACK) { finish() }
        back.setPadding(dp(4), dp(4), dp(4), dp(4))
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC000000.toInt(), 0))
            setPadding(dp(24), dp(14), dp(24), dp(34))
            addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(TextView(this@PlayerActivity).apply {
                tvTitle = this
                text = titleText
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setShadowLayer(6f, 0f, 1f, Color.BLACK)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
        }
        ui.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // ---- النص: -10 / تشغيل-إيقاف / +10
        val b10 = iconBtn(IconView.R10) { seekBy(-10) }
        val f10 = iconBtn(IconView.F10) { seekBy(10) }
        playBtn = iconBtn(IconView.PAUSE) { togglePlay() }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(b10, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(playBtn, LinearLayout.LayoutParams(dp(84), dp(84)).apply {
                marginStart = dp(52); marginEnd = dp(52)
            })
            addView(f10, LinearLayout.LayoutParams(dp(56), dp(56)))
        }
        ui.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // ---- الشريط السفلي: السيك بار الأحمر + الوقت المتبقي + الأزرار
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(RED)
            progressBackgroundTintList = ColorStateList.valueOf(0x66FFFFFF)
            thumbTintList = ColorStateList.valueOf(RED)
            splitTrack = false
            keyProgressIncrement = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                    if (user && mp.length > 0) tvRemain.text = fmt(mp.length - p.toLong() * mp.length / 1000)
                }
                override fun onStartTrackingTouch(s: SeekBar) {
                    dragging = true
                    h.removeCallbacks(hideRun)
                }
                override fun onStopTrackingTouch(s: SeekBar) {
                    dragging = false
                    val dv = cast
                    if (dv != null) {
                        val t = castLen * s.progress / 1000
                        castPos = t
                        Thread { Dlna.seek(dv, t) }.start()
                    } else mp.setPosition(s.progress / 1000f)
                    scheduleHide()
                }
            })
        }
        tvRemain = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.END
        }
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
            addView(tvRemain, LinearLayout.LayoutParams(dp(76), -2))
        }
        val speedItem = labeled(IconView.SPEED, "السرعة") { openSpeed() }
        tvSpeed = speedItem.getChildAt(1) as TextView
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(speedItem)
            addView(labeled(IconView.ZOOM, "حجم الصورة") { toggleZoom() })
            addView(labeled(IconView.SUBS, "الصوت والترجمة") { openTracks() })
            addView(labeled(IconView.CAST, "بث") { openCast() })
            addView(labeled(IconView.AI, "ترجمة ذكية") { aiTranslate() })
            addView(labeled(IconView.TUNE, "أدوات") { openTools() })
            addView(labeled(IconView.LOCK, "قفل") { setLocked(true) })
            // زرار «تحميل»: بيطلب من الصفحة تحمّل الفيديو الحالي (مش بيظهر للملفات المحمّلة أصلًا على الموبايل)
            val di = labeled(IconView.DL, "تحميل") { startDownload() }
            dlItem = di
            addView(di)
            // زرار ثابت للحلقة التالية (بيظهر أول ما الصفحة تبلّغ برقمها)
            val ni = labeled(IconView.PLAY, "الحلقة التالية") { goNext() }
            ni.visibility = View.GONE
            nextItem = ni
            addView(ni)
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xDD000000.toInt(), 0))
            setPadding(dp(24), dp(34), dp(24), dp(12))
            isClickable = true
            setOnClickListener { showUi() }
            addView(row1)
            addView(HorizontalScrollView(this@PlayerActivity).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                addView(row2)
            })
        }
        bottomBar = bottom
        ui.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        root.addView(ui, FrameLayout.LayoutParams(-1, -1))

        // ---- زرار الحلقة التالية: صغير وشفاف على الشمال تحت الفيديو، مخفي لحد آخر 250 ثانية
        val nIcon = IconView(this, IconView.PLAY)
        val nTitle = TextView(this).apply {
            text = "الحلقة التالية"
            setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        }
        nextLbl = TextView(this).apply { setTextColor(0xFFB3B3B3.toInt()); textSize = 12f }
        nextTime = TextView(this).apply { setTextColor(0xFFB3B3B3.toInt()); textSize = 12f }
        val nb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(12), dp(7), dp(14), dp(7))
            background = GradientDrawable().apply {
                setColor(0xCC141414.toInt()); cornerRadius = dp(6).toFloat(); setStroke(dp(1), 0x66FFFFFF)
            }
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            addView(nIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(nTitle, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            addView(nextLbl, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            addView(nextTime, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
            setOnClickListener { goNext() }
            focusScale(this)
        }
        nextBtn = nb
        root.addView(nb, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(28); bottomMargin = dp(112)
        })

        // ---- شاشة «جاري تجهيز الحلقة التالية»: بتغطي كل حاجة لحد ما الحلقة الجديدة تشتغل
        waitText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        waitLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xE6000000.toInt())
            visibility = View.GONE
            isClickable = true
            addView(ProgressBar(this@PlayerActivity).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(RED)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(waitText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        }
        root.addView(waitLayer, FrameLayout.LayoutParams(-1, -1))
        castLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = round(0xCC141414.toInt(), 16)
            visibility = View.GONE
        }
        root.addView(castLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(70) })
        hud = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = round(0xCC141414.toInt(), 20)
            visibility = View.GONE
        }
        root.addView(hud, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(28) })
        lockBtn = IconView(this, IconView.LOCK).apply {
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = round(0x99000000.toInt(), 26)
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener { setLocked(false) }
        }
        root.addView(lockBtn, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(28) })
        setContentView(root)
        showUi()
    }

    // ================= الحلقة التالية =================
    /** الصفحة بتنادي عليها (AndroidPlayer.showNext) لما يبقى فيه حلقة تالية؛ العدّاد بيتحسب هنا */
    fun setNext(label: String) {
        // لو الصفحة بعتت سبب بدل رقم (بيبدأ بـ !) نعرضه كرسالة بدل ما نفتح الزرار
        if (label.startsWith("!")) {
            Toast.makeText(this, label.substring(1), Toast.LENGTH_LONG).show()
            return
        }
        nextLabel = label
        updateNext()
    }

    private fun updateNext() {
        val b = nextBtn ?: return
        if (isDestroyed || isFinishing || waiting || swapping) return
        if (inPip()) { if (b.visibility != View.GONE) b.visibility = View.GONE; return }
        val len = curLen() * 1000
        val left = if (len > 0) (len - curPos() * 1000) / 1000 else -1L
        // الزرار الثابت في الشريط السفلي: بيظهر طول ما فيه حلقة تالية معروفة
        nextItem?.let { ni ->
            if (nextLabel.isNotEmpty()) {
                (ni.getChildAt(1) as TextView).text = "الحلقة التالية $nextLabel"
                if (ni.visibility != View.VISIBLE) ni.visibility = View.VISIBLE
            } else if (ni.visibility != View.GONE) ni.visibility = View.GONE
        }
        dlItem?.let { di ->
            val local = curUrl.startsWith("content:") || curUrl.startsWith("file:")
            val want = if (local) View.GONE else View.VISIBLE
            if (di.visibility != want) di.visibility = want
        }
        // الزرار الكبير بالعدّاد: آخر 250 ثانية، فوق الواجهة (ظاهر حتى لو الأزرار مخفية)
        if (nextLabel.isNotEmpty() && left in 1L..250L) {
            nextLbl?.text = nextLabel
            nextTime?.text = "· " + fmt(len - curPos() * 1000)
            if (b.visibility != View.VISIBLE) b.visibility = View.VISIBLE
        } else if (b.visibility != View.GONE) b.visibility = View.GONE
    }

    /** الحلقة التالية المتحمّلة على الجهاز: بتشتغل فورًا من غير ما نرجع للصفحة */
    private fun playLocalNext() {
        if (!hasLocalNext() || isDestroyed || isFinishing) return
        try { getSharedPreferences("pos", MODE_PRIVATE).edit().remove("p:$curUrl").apply() } catch (_: Throwable) {}
        plIdx++
        titleText = plTitles[plIdx]
        tvTitle?.text = titleText
        sub = ""; subAdded = false; n = 0; hwOn = true; hwRetried = false; resolveTried = false; resolvedUrl = null
        clearOwn()
        nextBtn?.visibility = View.GONE
        nextLabel = if (hasLocalNext()) plLabel(plIdx + 1) else ""
        seek.progress = 0
        tvRemain.text = "00:00"
        try { pfd?.close() } catch (_: Throwable) {}
        pfd = null
        curUrl = plUrls[plIdx]
        loadMedia(curUrl, 0)
        if (cast == null) mp.play()
        Toast.makeText(this, "▶ " + titleText, Toast.LENGTH_SHORT).show()
        showUi()
    }

    /** تحميل الفيديو الحالي: الصفحة (window.nativeDownload) هي اللي بتعرف الرابط الأصلي وبتبدأ التحميل في التطبيق */
    private fun startDownload() {
        if (curUrl.startsWith("content:") || curUrl.startsWith("file:")) {
            Toast.makeText(this, "الملف ده متحمّل على الموبايل أصلًا", Toast.LENGTH_SHORT).show()
            return
        }
        val web = MainActivity.inst?.web
        if (web == null) {
            Toast.makeText(this, "تعذّر بدء التحميل", Toast.LENGTH_SHORT).show()
            return
        }
        web.evaluateJavascript("(window.nativeDownload?nativeDownload():false)") { r ->
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (r == "true") Toast.makeText(this, "⬇️ بدأ التحميل — تابعه من زرار التحميلات", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goNext() {
        if (hasLocalNext()) { playLocalNext(); return }
        val lbl = nextLabel
        nextBtn?.visibility = View.GONE
        nextLabel = ""
        // نطلب الحلقة التالية من الصفحة، وبعدين نبلّغ إن دي خلصت (عشان تتحفظ كمشاهَدة)، ونفضل في المشغّل
        MainActivity.inst?.web?.evaluateJavascript("window.nativeNext&&nativeNext()", null)
        report(true)
        beginWait(lbl)
    }

    /** بنفضل في المشغّل ونعرض «جاري التجهيز» لحد ما الصفحة تبعت رابط الحلقة الجديدة (swapTo) */
    private fun beginWait(label: String) {
        waiting = true
        h.removeCallbacks(hideRun)
        try { mp.pause() } catch (_: Throwable) {}
        panel?.dismiss()
        nextBtn?.visibility = View.GONE
        ui.animate().cancel(); ui.alpha = 0f; ui.visibility = View.GONE; uiShown = false
        waitText?.text = if (label.isNotEmpty()) "الحلقة التالية $label\nجاري التجهيز..." else "جاري تجهيز الحلقة التالية..."
        waitLayer?.visibility = View.VISIBLE
        h.removeCallbacks(waitTimeout)
        h.postDelayed(waitTimeout, 60000)
    }

    fun isWaitingNext() = waiting

    /** MainActivity بينادي عليها لما الصفحة تجيب رابط الحلقة التالية والمشغّل لسه مفتوح */
    fun swapTo(url: String, raw: String, startSec: Int) {
        h.removeCallbacks(waitTimeout)
        sub = raw.substringAfter("\n@@SUB@@", "")
        titleText = raw.substringBefore("\n@@SUB@@")
        tvTitle?.text = titleText
        subAdded = false
        clearOwn()
        n = 0
        hwOn = true; hwRetried = false; resolveTried = false; resolvedUrl = null
        nextLabel = ""
        skipStopReport = false
        waiting = false
        swapping = true
        seek.progress = 0
        tvRemain.text = "00:00"
        try { pfd?.close() } catch (_: Throwable) {}
        pfd = null
        curUrl = url
        loadMedia(url, startSec)
        if (cast == null) mp.play()
    }

    // ================= إظهار/إخفاء الواجهة =================
    private fun showUi() {
        if (locked) return
        uiShown = true
        ui.animate().cancel()
        ui.visibility = View.VISIBLE
        ui.animate().alpha(1f).setDuration(180).start()
        scheduleHide()
        if (!playBtn.hasFocus()) playBtn.requestFocus()
    }

    private fun hideUi() {
        if (panel != null || dragging) return
        uiShown = false
        ui.animate().cancel()
        ui.animate().alpha(0f).setDuration(220).withEndAction { if (!uiShown) ui.visibility = View.GONE }.start()
    }

    private fun scheduleHide() {
        h.removeCallbacks(hideRun)
        if (mp.isPlaying && panel == null && !dragging) h.postDelayed(hideRun, 4000)
    }

    // ================= التحكم =================
    private fun togglePlay() {
        cast?.let { dv ->
            castPlaying = !castPlaying
            val pl = castPlaying
            Thread { if (pl) Dlna.resume(dv) else Dlna.pause(dv) }.start()
            playBtn.setKind(if (pl) IconView.PAUSE else IconView.PLAY)
            showUi()
            return
        }
        val was = mp.isPlaying
        if (was) mp.pause() else mp.play()
        playBtn.setKind(if (was) IconView.PLAY else IconView.PAUSE)
        showUi()
    }

    private fun seekBy(sec: Int) {
        cast?.let { dv ->
            if (castLen <= 0) return
            val t = (castPos + sec).coerceIn(0L, maxOf(0L, castLen - 2))
            castPos = t
            Thread { Dlna.seek(dv, t) }.start()
            return
        }
        val len = mp.length
        if (len <= 0) return
        val t = (mp.time + sec * 1000L).coerceIn(0L, maxOf(0L, len - 1500L))
        mp.setTime(t)
        seek.progress = (t * 1000 / len).toInt()
        tvRemain.text = fmt(len - t)
    }

    /** لمس مزدوج / سهم ريموت: كل ضغطة +10 ثواني ومعاها عدّاد متراكم زي نتفلكس */
    private fun stepSeek(dir: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSeekAt > 900 || dir != lastDir) accum = 0
        lastDir = dir
        lastSeekAt = now
        accum += 10
        seekBy(dir * 10)
        flashRip(dir < 0, "$accum ثانية")
    }

    private fun flashRip(left: Boolean, txt: String) {
        val v = if (left) ripL else ripR
        val i = if (left) 0 else 1
        v.text = (if (left) "⏪\n" else "⏩\n") + txt
        v.animate().cancel()
        v.alpha = 1f
        ripHide[i]?.let { h.removeCallbacks(it) }
        val r = Runnable { v.animate().alpha(0f).setDuration(250).start() }
        ripHide[i] = r
        h.postDelayed(r, 600)
    }

    /** بيفتكر اختيارك الأخير للترجمة، وإلا بيختار العربي لوحده لو موجود */
    private fun autoPickTracks() {
        if (isDestroyed || cast != null) return
        try {
            val pref = getSharedPreferences("pos", MODE_PRIVATE).getString("sub_pref", null)
            if (pref == "own" && ownOn) return
            if (pref == "off") { vlcSubChosen = true; disableOwn(); try { mp.setSpuTrack(-1) } catch (_: Throwable) {}; return }
            val spu = mp.spuTracks ?: return
            if (spu.isEmpty()) return
            val ar = Regex("(^|[\\s\\[(-])(ar|ara)([\\s\\])-]|$)", RegexOption.IGNORE_CASE)
            fun isAr(n: String) = n.contains("arab", true) || n.contains("عرب") || ar.containsMatchIn(n)
            val cand = spu.firstOrNull { it.id != -1 && pref != null && it.name == pref }
                ?: spu.firstOrNull { it.id != -1 && isAr(it.name) }
            if (cand != null) {
                vlcSubChosen = true
                if (mp.spuTrack != cand.id) mp.setSpuTrack(cand.id)
                disableOwn()
            }
        } catch (_: Throwable) {}
    }

    private fun toggleZoom() {
        zoomMode = (zoomMode + 1) % 3
        mp.videoScale = when (zoomMode) {
            1 -> MediaPlayer.ScaleType.SURFACE_FILL
            2 -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
        Toast.makeText(this, when (zoomMode) {
            1 -> "تمديد (يملا الشاشة بدون قص)"
            2 -> "ملء الشاشة (بيقص فوق وتحت)"
            else -> "الحجم الطبيعي"
        }, Toast.LENGTH_SHORT).show()
    }

    // ================= HUD / إيماءات / قفل / PiP / مؤقت نوم =================
    private fun showHud(t: String, ms: Long = 900L) {
        val v = hud ?: return
        v.text = t
        v.visibility = View.VISIBLE
        h.removeCallbacks(hudHide)
        if (ms > 0L) h.postDelayed(hudHide, ms)
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (_: Throwable) { 0.5f }
    }

    private fun setBrightness(v: Float) {
        val lp = window.attributes
        lp.screenBrightness = v
        window.attributes = lp
    }

    private fun endGesture(commit: Boolean) {
        if (gMode == 3 && commit && cast == null) {
            val len = mp.length
            if (len > 0) {
                mp.setTime(gTarget)
                seek.progress = (gTarget * 1000 / len).toInt()
                tvRemain.text = fmt(len - gTarget)
            }
            scheduleHide()
        }
        gMode = 0
        if (longFast) {
            longFast = false
            try { mp.setRate(prevRate) } catch (_: Throwable) {}
            h.removeCallbacks(hudHide)
            hud?.visibility = View.GONE
        }
    }

    private fun setLocked(on: Boolean) {
        locked = on
        if (on) {
            panel?.dismiss()
            h.removeCallbacks(hideRun)
            uiShown = false
            ui.animate().cancel(); ui.alpha = 0f; ui.visibility = View.GONE
            showHud("🔒 الشاشة مقفولة — المس الشاشة وبعدين اضغط القفل", 1600L)
            showLockBtn()
        } else {
            h.removeCallbacks(lockHide)
            lockBtn?.visibility = View.GONE
            showUi()
        }
    }

    private fun showLockBtn() {
        lockBtn?.visibility = View.VISIBLE
        h.removeCallbacks(lockHide)
        h.postDelayed(lockHide, 2500)
    }

    private fun inPip() = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, "صورة داخل صورة محتاجة أندرويد 8 أو أحدث", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            panel?.dismiss()
            var r = 16f / 9f
            try { mp.currentVideoTrack?.let { if (it.width > 0 && it.height > 0) r = it.width.toFloat() / it.height } } catch (_: Throwable) {}
            r = r.coerceIn(0.45f, 2.3f)
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational((r * 1000).toInt(), 1000)).build())
        } catch (_: Throwable) {
            Toast.makeText(this, "صورة داخل صورة مش مفعّلة: ضيف supportsPictureInPicture في الـ Manifest", Toast.LENGTH_LONG).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (autoPip && cast == null && !waiting && !locked && mp.isPlaying) enterPip()
    }

    /** min: 0 إلغاء · -1 لحد نهاية الحلقة · غير كده بالدقايق */
    private fun setSleep(min: Int) {
        sleepRun?.let { h.removeCallbacks(it) }
        sleepRun = null
        sleepAtEnd = false
        when {
            min == 0 -> Toast.makeText(this, "اتلغى مؤقت النوم", Toast.LENGTH_SHORT).show()
            min < 0 -> { sleepAtEnd = true; Toast.makeText(this, "😴 هيقفل بعد نهاية الحلقة", Toast.LENGTH_SHORT).show() }
            else -> {
                val r = Runnable {
                    val dv = cast
                    if (dv != null) Thread { Dlna.pause(dv) }.start() else try { mp.pause() } catch (_: Throwable) {}
                    Toast.makeText(this, "😴 مؤقت النوم: تم الإيقاف", Toast.LENGTH_LONG).show()
                    finish()
                }
                sleepRun = r
                h.postDelayed(r, min * 60_000L)
                Toast.makeText(this, "😴 هيقفل بعد $min دقيقة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= اللوحات (صوت/ترجمة/سرعة) =================
    private fun showPanel(content: View, wFrac: Float, hFrac: Float?) {
        panel?.dismiss()
        val d = Dialog(this)
        panel = d
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(content)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = resources.displayMetrics
            setLayout(
                (dm.widthPixels * wFrac).toInt(),
                if (hFrac == null) ViewGroup.LayoutParams.WRAP_CONTENT else (dm.heightPixels * hFrac).toInt()
            )
            setDimAmount(0.6f)
        }
        d.setOnDismissListener {
            if (panel === d) panel = null
            scheduleHide()
        }
        h.removeCallbacks(hideRun)
        d.show()
        d.window?.let { w -> WindowInsetsControllerCompat(w, w.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
    }

    private fun optionRow(text: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = (if (selected) "✓   " else "      ") + text
        setTextColor(if (selected) Color.WHITE else 0xFFB3B3B3.toInt())
        textSize = 16f
        if (selected) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(8), dp(11), dp(8), dp(11))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        focusScale(this)
    }

    private fun column(title: String, tracks: Array<MediaPlayer.TrackDescription>?, selId: Int, allowOff: Boolean,
                       extra: String? = null, extraSel: Boolean = false, extraSet: (() -> Unit)? = null,
                       set: (Int) -> Unit): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, dp(10))
        })
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = (tracks ?: emptyArray()).filter { allowOff || it.id != -1 }
        if (extra != null) inner.addView(optionRow(extra, extraSel) { extraSet?.invoke(); panel?.dismiss() })
        if (list.isEmpty()) {
            if (extra == null) inner.addView(optionRow("غير متاح", false) {})
        } else {
            list.forEach { t ->
                val label = if (t.id == -1) "إيقاف" else t.name
                inner.addView(optionRow(label, t.id == selId && !(extra != null && extraSel)) { set(t.id); panel?.dismiss() })
            }
        }
        col.addView(ScrollView(this).apply { addView(inner) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return col
    }

    private fun openTracks() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = round(0xF2181818.toInt(), 12)
            setPadding(dp(22), dp(18), dp(22), dp(14))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        box.addView(column("الصوت", mp.audioTracks, mp.audioTrack, false) { mp.setAudioTrack(it) },
            LinearLayout.LayoutParams(0, -1, 1f))
        box.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) }, LinearLayout.LayoutParams(dp(1), -1).apply {
            marginStart = dp(12); marginEnd = dp(12)
        })
        box.addView(column("الترجمة", mp.spuTracks, mp.spuTrack, true,
            if (ownCues.isNotEmpty()) "ترجمة الحلقة" else null, ownOn, { enableOwn() }) { id ->
            mp.setSpuTrack(id)
            disableOwn()
            vlcSubChosen = id != -1
            val nm = if (id == -1) "off" else mp.spuTracks?.firstOrNull { it.id == id }?.name
            getSharedPreferences("pos", MODE_PRIVATE).edit().putString("sub_pref", nm).apply()
        },
            LinearLayout.LayoutParams(0, -1, 1f))
        showPanel(box, 0.78f, 0.78f)
    }

    private fun openSpeed() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xF2181818.toInt(), 12)
            setPadding(dp(22), dp(18), dp(22), dp(14))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        box.addView(TextView(this).apply {
            text = "سرعة التشغيل"
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, dp(10))
        })
        val cur = mp.rate
        floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { x ->
            val name = if (x == 1f) "عادية" else (if (x % 1f == 0f) x.toInt().toString() else x.toString()) + "x"
            box.addView(optionRow(name, Math.abs(cur - x) < 0.01f) {
                mp.setRate(x)
                tvSpeed.text = if (x == 1f) "السرعة" else "السرعة ($name)"
                panel?.dismiss()
            })
        }
        showPanel(box, 0.36f, null)
    }

    // ================= البث للتلفزيون =================
    @Volatile private var cast: Dlna.Dev? = null        // بث DLNA (سامسونج / LG / أي شاشة)
    @Volatile private var castPos = 0L
    @Volatile private var castLen = 0L
    @Volatile private var castSince = 0L
    private var castPlaying = false
    private var castFired = false
    private var castLabel: TextView? = null
    private var castList: LinearLayout? = null
    private var searching = false
    private var renderer: RendererItem? = null          // بث Chromecast عن طريق LibVLC
    private var rd: RendererDiscoverer? = null
    private val rItems = ArrayList<RendererItem>()
    private val dItems = ArrayList<Dlna.Dev>()

    private fun curPos(): Long = if (cast != null) castPos else mp.time / 1000
    private fun curLen(): Long = if (cast != null) castLen else mp.length / 1000

    private fun onEnded() {
        if (waiting) return
        if (cast == null) {
            // لو الفيديو «خلص» وإحنا لسه بعيد عن آخره، ده فشل تشغيل مش نهاية فيلم: منقفلش المشغّل
            val len = mp.length
            val early = if (len > 0) lastPos < len - 120_000L else lastPos < 20_000L
            if (early) {
                if (hwOn && !hwRetried && curUrl.isNotEmpty()) {
                    hwRetried = true
                    hwOn = false
                    Toast.makeText(this, "الفيديو وقف بدري، بجرّب وضع التوافق الكامل...", Toast.LENGTH_SHORT).show()
                    val p = (lastPos / 1000).toInt()
                    h.post { if (!isDestroyed) { loadMedia(curUrl, p); mp.play() } }
                } else {
                    spinner.visibility = View.GONE
                    Toast.makeText(this, "الفيديو وقف عند " + fmt(lastPos) + " من " + fmt(len) + " (الملف أو الرابط فيه مشكلة)", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
        if (sleepAtEnd) { sleepAtEnd = false; report(true); finish(); return }
        if (hasLocalNext() && autoNext) { h.post { playLocalNext() }; return }
        report(true)
        if (nextLabel.isNotEmpty() && autoNext) beginWait(nextLabel) else finish()
    }

    private fun startRd() {
        if (rd != null) return
        try {
            val r = RendererDiscoverer(vlc, "microdns_renderer")
            r.setEventListener { ev ->
                when (ev.type) {
                    RendererDiscoverer.Event.ItemAdded -> {
                        val item = ev.item
                        item.retain()
                        runOnUiThread { rItems.add(item); fillCast() }
                    }
                    RendererDiscoverer.Event.ItemDeleted -> {
                        val nm = ev.item.name
                        runOnUiThread { rItems.removeAll { x -> x.name == nm }; fillCast() }
                    }
                    else -> {}
                }
            }
            if (r.start()) rd = r
        } catch (_: Throwable) {}
    }

    private fun openCast() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xF2181818.toInt(), 12)
            setPadding(dp(22), dp(18), dp(22), dp(14))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        box.addView(TextView(this).apply {
            text = "البث على شاشة"
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, dp(10))
        })
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        castList = inner
        box.addView(ScrollView(this).apply { addView(inner) }, LinearLayout.LayoutParams(-1, 0, 1f))
        searching = true
        dItems.clear()
        fillCast()
        startRd()
        Dlna.discover(this, { d ->
            runOnUiThread { if (dItems.none { it.ctrl == d.ctrl }) { dItems.add(d); fillCast() } }
        }, { runOnUiThread { searching = false; fillCast() } })
        showPanel(box, 0.55f, 0.8f)
    }

    private fun fillCast() {
        val l = castList ?: return
        l.removeAllViews()
        if (cast != null || renderer != null)
            l.addView(optionRow("⏹  إيقاف البث (الرجوع للموبايل)", false) { stopCast(); panel?.dismiss() })
        rItems.forEach { r ->
            l.addView(optionRow("📺  " + r.displayName + "  (Chromecast)", renderer === r) { castToRenderer(r); panel?.dismiss() })
        }
        dItems.forEach { d ->
            l.addView(optionRow("📺  " + d.name, cast?.ctrl == d.ctrl) { castToDlna(d); panel?.dismiss() })
        }
        if (rItems.isEmpty() && dItems.isEmpty())
            l.addView(optionRow(if (searching) "جاري البحث عن الشاشات..." else "مفيش شاشات ظاهرة. اتأكد إن الموبايل والتلفزيون على نفس الواي فاي", false) {})
        l.addView(optionRow("🖥  مشاركة الشاشة (Miracast / Smart View)", false) { openMirror() })
    }

    private fun openMirror() {
        try { startActivity(Intent(Settings.ACTION_CAST_SETTINGS)) }
        catch (_: Throwable) {
            try { startActivity(Intent("android.settings.WIFI_DISPLAY_SETTINGS")) }
            catch (_: Throwable) {
                Toast.makeText(this, "افتح Smart View من لوحة الإشعارات (اسحب من فوق)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun castToRenderer(r: RendererItem) {
        val pos = mp.time / 1000
        stopDlnaOnly()
        renderer = r
        mp.setRenderer(r)
        loadMedia(curUrl, pos.toInt())
        mp.play()
        castLabel?.text = "📺  " + r.displayName
        castLabel?.visibility = View.VISIBLE
    }

    private fun castToDlna(d: Dlna.Dev) {
        if (!curUrl.startsWith("http")) {
            Toast.makeText(this, "الملفات المحمّلة على الموبايل مبتتبثش. اشغّلها من الرابط الأونلاين", Toast.LENGTH_LONG).show()
            return
        }
        val pos = mp.time / 1000
        if (renderer != null) { mp.setRenderer(null); renderer = null }
        stopDlnaOnly()
        try { mp.pause() } catch (_: Throwable) {}
        castPos = pos; castLen = mp.length / 1000; castFired = false
        castPlaying = true; castSince = System.currentTimeMillis()
        cast = d
        castLabel?.text = "📺  " + d.name
        castLabel?.visibility = View.VISIBLE
        val url = curUrl
        val tt = titleText
        Thread {
            val ok = Dlna.start(d, url, tt, pos.toInt(), applicationContext)
            if (!ok) runOnUiThread {
                Toast.makeText(this, "التلفزيون رفض الرابط أو مش بيرد\n" + Dlna.lastError, Toast.LENGTH_LONG).show()
                stopCast()
            } else runOnUiThread { castNotice(); CastService.begin(this, d, url, tt, pos) }
        }.start()
        startPoll()
    }

    /** بيعرّف المستخدم البث شغال إزاي (مباشر ولا عن طريق الموبايل) */
    private fun castNotice() {
        if (isDestroyed || cast == null) return
        if (Dlna.viaProxy) {
            castLabel?.text = "📺  " + (cast?.name ?: "") + "  (عن طريق الموبايل)"
            Toast.makeText(this, "البث شغال عن طريق الموبايل: سيب التطبيق مفتوح والموبايل على نفس الواي فاي", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "البث مباشر من الإنترنت: تقدر تقفل الموبايل أو تخرج وهيفضل شغال", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDlnaOnly() {
        val d = cast ?: return
        cast = null
        CastService.end(this)
        Thread { Dlna.stop(d) }.start()
    }

    private fun stopCast() {
        val d = cast
        if (d != null) {
            val pos = castPos
            cast = null
            CastService.end(this)
            Thread { Dlna.stop(d) }.start()
            try {
                // لو رجعنا للمشغّل بعد ما قفلناه والبث شغال، المشغّل المحلي مفيهوش فيديو لسه
                if (mp.media == null) loadMedia(curUrl, pos.toInt()) else mp.setTime(pos * 1000)
                mp.play()
            } catch (_: Throwable) {}
        } else if (renderer != null) {
            val pos = mp.time / 1000
            renderer = null
            mp.setRenderer(null)
            loadMedia(curUrl, pos.toInt())
            mp.play()
        }
        castLabel?.visibility = View.GONE
    }

    private fun startPoll() {
        Thread {
            while (!isDestroyed) {
                val d = cast ?: break
                val r = Dlna.pos(d)
                if (r != null && cast != null) {
                    castPos = r.first
                    if (r.second > 0) castLen = r.second
                }
                try { Thread.sleep(1200) } catch (_: InterruptedException) { break }
            }
        }.also { it.isDaemon = true }.start()
    }

    // ================= لوحة الأدوات =================
    private fun toolsBox(title: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xF2181818.toInt(), 12)
            setPadding(dp(22), dp(18), dp(22), dp(14))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        box.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, dp(10))
        })
        return box
    }

    /** صف بزرارين − / + لضبط قيمة (بالميكروثانية) من غير ما اللوحة تتقفل */
    private fun stepper(label: String, getV: () -> Long, setV: (Long) -> Unit): View {
        val tv = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER }
        fun refresh() { tv.text = label + ": " + (getV() / 1000) + " ms" }
        fun btn(t: String, d: Long) = TextView(this).apply {
            text = t
            setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(4), dp(18), dp(4))
            background = round(0x33FFFFFF, 8)
            isClickable = true
            isFocusable = true
            setOnClickListener { setV(getV() + d); refresh() }
            focusScale(this)
        }
        refresh()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(8), dp(6), dp(8), dp(6))
            addView(btn("−", -100_000L))
            addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
            addView(btn("+", 100_000L))
        }
    }

    private fun openTools() {
        val box = toolsBox("أدوات")
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(optionRow("📐  نسبة العرض: " + (aspects[aspectIdx] ?: "تلقائي"), false) {
            aspectIdx = (aspectIdx + 1) % aspects.size
            try { mp.setAspectRatio(aspects[aspectIdx]) } catch (_: Throwable) {}
            showHud("📐 " + (aspects[aspectIdx] ?: "تلقائي"))
            panel?.dismiss()
        })
        inner.addView(stepper("تأخير الصوت", { try { mp.audioDelay } catch (_: Throwable) { 0L } }, { try { mp.setAudioDelay(it) } catch (_: Throwable) {} }))
        inner.addView(stepper("تأخير الترجمة", { if (ownOn) ownDelayUs else try { mp.spuDelay } catch (_: Throwable) { 0L } }, { v -> if (ownOn) { ownDelayUs = v } else { try { mp.setSpuDelay(v) } catch (_: Throwable) {} } }))
        inner.addView(optionRow("🎚  الإيكولايزر (بيس / صوت أوضح)", eqIdx >= 0) { openEq() })
        inner.addView(optionRow("😴  مؤقت النوم", sleepRun != null || sleepAtEnd) { openSleep() })
        inner.addView(optionRow("🪟  صورة داخل صورة (PiP)", false) { enterPip() })
        inner.addView(optionRow("↪  PiP تلقائي لما تخرج من التطبيق", autoPip) { autoPip = !autoPip; panel?.dismiss() })
        inner.addView(optionRow("✨  إعدادات الذكاء الصناعي (المفتاح / الموديل)", false) { aiSettings() })
        box.addView(ScrollView(this).apply { addView(inner) }, LinearLayout.LayoutParams(-1, 0, 1f))
        showPanel(box, 0.55f, 0.85f)
    }

    private fun openEq() {
        val box = toolsBox("الإيكولايزر")
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(optionRow("بدون", eqIdx == -1) {
            eqIdx = -1
            try { mp.setEqualizer(null) } catch (_: Throwable) {}
            panel?.dismiss()
        })
        val n = try { MediaPlayer.Equalizer.getPresetCount() } catch (_: Throwable) { 0 }
        for (i in 0 until n) {
            val nm = try { MediaPlayer.Equalizer.getPresetName(i) } catch (_: Throwable) { "Preset $i" }
            inner.addView(optionRow(nm, eqIdx == i) {
                eqIdx = i
                try { mp.setEqualizer(MediaPlayer.Equalizer.createFromPreset(i)) } catch (_: Throwable) {}
                panel?.dismiss()
            })
        }
        box.addView(ScrollView(this).apply { addView(inner) }, LinearLayout.LayoutParams(-1, 0, 1f))
        showPanel(box, 0.4f, 0.85f)
    }

    private fun openSleep() {
        val box = toolsBox("مؤقت النوم")
        box.addView(optionRow("إيقاف المؤقت", sleepRun == null && !sleepAtEnd) { setSleep(0); panel?.dismiss() })
        intArrayOf(15, 30, 45, 60, 90).forEach { m ->
            box.addView(optionRow("بعد $m دقيقة", false) { setSleep(m); panel?.dismiss() })
        }
        box.addView(optionRow("لحد نهاية الحلقة", sleepAtEnd) { setSleep(-1); panel?.dismiss() })
        showPanel(box, 0.36f, null)
    }

    // ================= ترجمة ذكية بالذكاء الصناعي (Claude) =================
    private class Cue(val start: String, val end: String, val text: String)

    private fun aiPrefs() = getSharedPreferences("ai", MODE_PRIVATE)

    private fun aiSettings() {
        panel?.dismiss()
        val p = aiPrefs()
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val keyEt = EditText(this).apply {
            hint = "API Key"
            setText(p.getString("key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val urlEt = EditText(this).apply { hint = "Endpoint"; setText(p.getString("url", AI_URL)); setSingleLine() }
        val modelEt = EditText(this).apply { hint = "Model"; setText(p.getString("model", AI_MODEL)); setSingleLine() }
        ll.addView(keyEt); ll.addView(urlEt); ll.addView(modelEt)
        AlertDialog.Builder(this)
            .setTitle("إعدادات الذكاء الصناعي")
            .setView(ll)
            .setPositiveButton("حفظ") { _, _ ->
                p.edit()
                    .putString("key", keyEt.text.toString().trim())
                    .putString("url", urlEt.text.toString().trim().ifEmpty { AI_URL })
                    .putString("model", modelEt.text.toString().trim().ifEmpty { AI_MODEL })
                    .apply()
                Toast.makeText(this, "اتحفظت", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /** بياخد ملف الترجمة الخارجي للحلقة، يترجمه عربي بدفعات متوازية، ويضيفه كمسار ترجمة جديد */
    private fun aiTranslate() {
        if (aiBusy) { Toast.makeText(this, "الترجمة شغالة بالفعل...", Toast.LENGTH_SHORT).show(); return }
        val src = sub
        if (src.isEmpty()) {
            Toast.makeText(this, "مفيش ملف ترجمة خارجي للحلقة دي عشان أترجمه", Toast.LENGTH_LONG).show()
            return
        }
        val p = aiPrefs()
        val key = p.getString("key", "") ?: ""
        if (key.isEmpty()) {
            Toast.makeText(this, "حط مفتاح الـ API الأول", Toast.LENGTH_LONG).show()
            aiSettings()
            return
        }
        val url = p.getString("url", AI_URL) ?: AI_URL
        val model = p.getString("model", AI_MODEL) ?: AI_MODEL
        val vid = curUrl
        val out = File(cacheDir, "ai_" + Integer.toHexString(src.hashCode()) + ".srt")
        if (out.exists() && out.length() > 0) { attachAiSub(out); return }
        aiBusy = true
        showHud("🤖 بقرا الترجمة...", 0L)
        Thread {
            var pool: java.util.concurrent.ExecutorService? = null
            try {
                val cues = parseCues(readSubText(src))
                if (cues.isEmpty()) throw IllegalStateException("ملف الترجمة فاضي أو مش مفهوم")
                val sample = cues.take(200).joinToString(" ") { it.text }
                val letters = sample.count { it.isLetter() }
                val arabic = sample.count { it in '\u0600'..'\u06FF' }
                if (letters > 0 && arabic * 10 > letters * 6) {
                    runOnUiThread {
                        aiBusy = false
                        hud?.visibility = View.GONE
                        Toast.makeText(this, "الترجمة عربي أصلًا", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }
                val total = minOf(cues.size, 4000)
                val size = 40
                val nBatches = (total + size - 1) / size
                val tr = arrayOfNulls<String>(cues.size)
                val done = AtomicInteger(0)
                val failed = AtomicInteger(0)
                val lastErr = java.util.concurrent.atomic.AtomicReference("")
                val ex = Executors.newFixedThreadPool(4)
                pool = ex
                val futures = (0 until nBatches).map { bi ->
                    ex.submit(Callable {
                        val from = bi * size
                        val to = minOf(from + size, total)
                        try {
                            val res = aiBatch(url, key, model, cues, from, to)
                            for ((i, t) in res) tr[i] = t
                        } catch (e: Exception) {
                            failed.incrementAndGet()
                            lastErr.set(e.message ?: e.javaClass.simpleName)
                        }
                        val c = done.incrementAndGet()
                        runOnUiThread { if (!isDestroyed) showHud("🤖 بترجم... $c/$nBatches", 0L) }
                    })
                }
                futures.forEach { it.get() }
                if (failed.get() >= nBatches) throw IllegalStateException(lastErr.get())
                val sb = StringBuilder()
                for ((i, c) in cues.withIndex()) {
                    sb.append(i + 1).append('\n').append(c.start).append(" --> ").append(c.end).append('\n')
                        .append(tr[i] ?: c.text).append("\n\n")
                }
                out.writeText(sb.toString(), Charsets.UTF_8)
                val nf = failed.get()
                runOnUiThread {
                    aiBusy = false
                    if (isDestroyed) return@runOnUiThread
                    hud?.visibility = View.GONE
                    if (curUrl == vid) attachAiSub(out)
                    if (nf > 0) Toast.makeText(this, "تنبيه: $nf دفعة فشلت وفضلت بلغتها الأصلية", Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) {
                val msg = e.message ?: e.javaClass.simpleName
                runOnUiThread {
                    aiBusy = false
                    if (isDestroyed) return@runOnUiThread
                    hud?.visibility = View.GONE
                    Toast.makeText(this, "فشلت الترجمة الذكية: $msg", Toast.LENGTH_LONG).show()
                }
            } finally {
                pool?.shutdown()
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun attachAiSub(f: File) {
        try {
            loadOwnSub(Uri.fromFile(f).toString(), true)
            Toast.makeText(this, "✨ اتضافت الترجمة العربية الذكية", Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "تعذّر إضافة الترجمة", Toast.LENGTH_SHORT).show()
        }
    }


    // ================= ترجمة بتتحرك زي نتفلكس (بنرسمها بنفسنا بدل VLC) =================
    // الترجمة الخارجية (ملف الحلقة + الترجمة الذكية) بتتعرض في TextView بتطلع فوق شريط التحكم لما يظهر وتنزل لما يختفي.
    // الترجمة المدمجة جوه الفيديو لسه بيرسمها VLC زي ما هي.
    private class OwnCue(val s: Long, val e: Long, val t: String)
    private var ownCues: List<OwnCue> = emptyList()
    private var ownOn = false
    private var ownDelayUs = 0L
    private var ownIdx = -1
    private var ownShown = -2
    private var vlcSubChosen = false
    private var subLoadId = 0
    private var subView: TextView? = null
    private var bottomBar: View? = null
    private var subLifted = false

    private val subTick = object : Runnable {
        override fun run() {
            try { updateOwnSub() } catch (_: Throwable) {}
            if (!isDestroyed) h.postDelayed(this, 100)
        }
    }

    private fun tsMs(t: String): Long {
        val p = t.replace(',', '.').split(':', '.')
        return try { p[0].toLong() * 3600000L + p[1].toLong() * 60000L + p[2].toLong() * 1000L + p[3].toLong() } catch (_: Throwable) { 0L }
    }

    private fun cleanSub(t: String) = t.replace(Regex("<[^>]*>"), "").replace(Regex("\\{[^}]*\\}"), "")
        .replace(Regex("\\s*\\n\\s*"), "\n").trim()

    private fun loadOwnSub(src: String, force: Boolean = false) {
        val id = ++subLoadId
        Thread {
            try {
                val cues = parseCues(readSubText(src))
                    .map { OwnCue(tsMs(it.start), tsMs(it.end), cleanSub(it.text)) }
                    .filter { it.t.isNotEmpty() }
                    .sortedBy { it.s }
                if (cues.isEmpty()) throw IllegalStateException("empty")
                runOnUiThread {
                    if (isDestroyed || id != subLoadId) return@runOnUiThread
                    ownCues = cues
                    ownIdx = -1
                    val pref = getSharedPreferences("pos", MODE_PRIVATE).getString("sub_pref", null)
                    if (force || (!vlcSubChosen && pref != "off")) enableOwn(false) else disableOwn()
                }
            } catch (_: Throwable) {
                // لو القراءة فشلت: نرجع للطريقة القديمة (VLC بيرسمها)
                runOnUiThread {
                    if (!isDestroyed && id == subLoadId) {
                        try { mp.addSlave(0, Uri.parse(src), true) } catch (_: Throwable) {}
                    }
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun enableOwn(save: Boolean = true) {
        if (ownCues.isEmpty()) return
        ownOn = true
        vlcSubChosen = false
        ownIdx = -1
        ownShown = -2
        try { mp.setSpuTrack(-1) } catch (_: Throwable) {}
        if (save) getSharedPreferences("pos", MODE_PRIVATE).edit().putString("sub_pref", "own").apply()
        updateOwnSub()
    }

    private fun disableOwn() {
        ownOn = false
        ownShown = -2
        subView?.visibility = View.GONE
    }

    private fun clearOwn() {
        subLoadId++
        ownCues = emptyList()
        ownOn = false
        ownIdx = -1
        ownShown = -2
        ownDelayUs = 0L
        vlcSubChosen = false
        subView?.visibility = View.GONE
    }

    private fun updateOwnSub() {
        val v = subView ?: return
        // رفع/خفض الترجمة مع ظهور/اختفاء شريط التحكم
        val want = uiShown && ui.visibility == View.VISIBLE && !locked
        if (want != subLifted) {
            subLifted = want
            val lift = ((bottomBar?.height ?: dp(130)) - dp(54)).coerceAtLeast(dp(40))
            v.animate().cancel()
            v.animate().translationY(if (want) -lift.toFloat() else 0f).setDuration(220).start()
        }
        val c = ownCues
        if (!ownOn || c.isEmpty() || cast != null || inPip() || waiting) {
            if (v.visibility != View.GONE) v.visibility = View.GONE
            ownShown = -2
            return
        }
        val t = mp.time - ownDelayUs / 1000L
        var i = ownIdx
        if (i < 0 || i >= c.size || t < c[i].s || t > c[i].e) {
            var lo = 0
            var hi = c.size - 1
            var f = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (c[mid].s <= t) { f = mid; lo = mid + 1 } else hi = mid - 1
            }
            var hit = -1
            var k = f
            while (k >= 0 && k > f - 4) {
                if (t <= c[k].e) { hit = k; break }
                k--
            }
            i = hit
            ownIdx = hit
        }
        if (i < 0) {
            if (v.visibility != View.GONE) v.visibility = View.GONE
            ownShown = -2
        } else {
            if (ownShown != i) { v.text = c[i].t; ownShown = i }
            if (v.visibility != View.VISIBLE) v.visibility = View.VISIBLE
        }
    }

    private fun readSubText(src: String): String {
        val bytes: ByteArray = if (src.startsWith("http")) {
            val c = URL(src).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.inputStream.use { it.readBytes() }
            } finally { c.disconnect() }
        } else {
            contentResolver.openInputStream(Uri.parse(src))?.use { it.readBytes() } ?: throw IllegalStateException("مش قادر أقرا الترجمة")
        }
        var t = String(bytes, Charsets.UTF_8)
        if (t.contains('\uFFFD')) t = String(bytes, charset("windows-1256"))
        return t.removePrefix("\uFEFF")
    }

    private val tsRe = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})""")

    private fun parseCues(raw: String): List<Cue> {
        val out = ArrayList<Cue>()
        val blocks = raw.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val lines = b.split('\n')
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val m = tsRe.find(lines[ti]) ?: continue
            val text = lines.drop(ti + 1).joinToString("\n").trim()
            if (text.isEmpty()) continue
            out.add(Cue(normTs(m.groupValues[1]), normTs(m.groupValues[2]), text))
        }
        return out
    }

    private fun normTs(t: String): String {
        val parts = t.replace(',', '.').split(':')
        val hh = if (parts.size == 3) parts[0].toInt() else 0
        val mm = parts[parts.size - 2].toInt()
        val sm = parts[parts.size - 1].split('.')
        val ms = ((sm.getOrNull(1) ?: "0") + "00").take(3)
        return "%02d:%02d:%02d,%s".format(hh, mm, sm[0].toInt(), ms)
    }

    /** بيترجم دفعة واحدة (from until to) ويرجّع (رقم السطر ← النص العربي) */
    private fun aiBatch(url: String, key: String, model: String, cues: List<Cue>, from: Int, to: Int): List<Pair<Int, String>> {
        val user = StringBuilder()
        for (i in from until to) user.append(i).append('|').append(cues[i].text.replace("\n", " ⏎ ")).append('\n')
        val system = "You translate movie and TV subtitles into natural, easy-to-read Arabic (simple Modern Standard Arabic, " +
            "short lines, keep names and tone). Each input line is `N|text`. Reply with ONLY the translated lines in the exact " +
            "same format `N|arabic text`, one per input line, same N, nothing else. The symbol ⏎ is a line break: keep it. " +
            "Keep tags like <i> intact. Translate faithfully."
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user.toString())))
            .toString()
        val resp = JSONObject(httpPost(url, key, body))
        val arr = resp.getJSONArray("content")
        val txt = StringBuilder()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            if (o.optString("type") == "text") txt.append(o.optString("text"))
        }
        val line = Regex("^\\s*(\\d+)\\s*\\|(.*)$")
        val res = ArrayList<Pair<Int, String>>()
        for (l in txt.toString().split('\n')) {
            val m = line.find(l) ?: continue
            val idx = m.groupValues[1].toInt()
            if (idx in from until to) res.add(idx to m.groupValues[2].trim().replace(" ⏎ ", "\n").replace("⏎", "\n"))
        }
        return res
    }

    private fun httpPost(urlStr: String, key: String, body: String): String {
        var lastCode = 0
        for (attempt in 0..2) {
            val c = URL(urlStr).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 15000
                c.readTimeout = 90000
                c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.setRequestProperty("x-api-key", key)
                c.setRequestProperty("anthropic-version", "2023-06-01")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                if (code in 200..299) return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                lastCode = code
                val err = try { c.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Throwable) { null }
                if (code == 429 || code >= 500) { Thread.sleep(1500L * (attempt + 1)); continue }
                throw java.io.IOException("HTTP $code " + (err ?: "").take(160))
            } finally {
                c.disconnect()
            }
        }
        throw java.io.IOException("HTTP $lastCode")
    }

    // ================= ريموت / كيبورد (تلفزيون) =================
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (locked && e.keyCode != KeyEvent.KEYCODE_BACK && e.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            e.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && e.keyCode != KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (e.action == KeyEvent.ACTION_DOWN) showLockBtn()
            return true
        }
        if (e.action == KeyEvent.ACTION_DOWN && panel == null) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { togglePlay(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!mp.isPlaying) togglePlay(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (mp.isPlaying) togglePlay(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30); showUi(); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30); showUi(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (!uiShown) { togglePlay(); return true }
                    scheduleHide()
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!uiShown) {
                        stepSeek(if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        return true
                    }
                    scheduleHide()
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!uiShown) { showUi(); return true }
                    scheduleHide()
                }
                else -> {}
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // ================= دورة الحياة =================
    // فحص سريع للرابط (كود HTTP + نوع الملف) بـ User-Agent معيّن، للتشخيص
    private fun httpProbe(url: String, ua: String): String {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", ua)
            c.setRequestProperty("Range", "bytes=0-0")
            val code = c.responseCode
            val ct = c.contentType ?: "?"
            c.disconnect()
            "$code ($ct)"
        } catch (e: Throwable) { (e.javaClass.simpleName + ": " + (e.message ?: "")).take(80) }
    }

    // بروكسي محلي: بيجيب الفيديو بشبكة الأندرويد نفسها (TLS وشهادات النظام + User-Agent متصفح) ويدّيه لـ VLC على 127.0.0.1
    // بيتخطى أي مشكلة في اتصال VLC المباشر بسيرفر TorBox (شهادات / UA / تحويلات)
    private var proxySock: java.net.ServerSocket? = null
    @Volatile private var proxyTarget = ""

    private fun startProxy(target: String): String? {
        return try {
            proxyTarget = target
            if (proxySock == null) {
                val ss = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
                proxySock = ss
                Thread {
                    while (!ss.isClosed) {
                        val cl = try { ss.accept() } catch (_: Throwable) { break }
                        Thread { proxyServe(cl) }.start()
                    }
                }.start()
            }
            "http://127.0.0.1:" + proxySock!!.localPort + "/v"
        } catch (_: Throwable) { null }
    }

    private fun proxyServe(cl: java.net.Socket) {
        var c: HttpURLConnection? = null
        try {
            cl.soTimeout = 60000
            val inp = cl.getInputStream()
            val sb = StringBuilder()
            while (sb.length < 16384) {
                val b = inp.read()
                if (b < 0) break
                sb.append(b.toChar())
                if (sb.endsWith("\r\n\r\n")) break
            }
            val lines = sb.toString().split("\r\n")
            val method = lines[0].substringBefore(' ').ifEmpty { "GET" }
            var range: String? = null
            for (l in lines) if (l.startsWith("Range:", true)) range = l.substringAfter(':').trim()
            c = URL(proxyTarget).openConnection() as HttpURLConnection
            c.requestMethod = if (method == "HEAD") "HEAD" else "GET"
            c.instanceFollowRedirects = true
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", BROWSER_UA)
            c.setRequestProperty("Accept-Encoding", "identity")
            if (range != null) c.setRequestProperty("Range", range)
            val code = c.responseCode
            val out = cl.getOutputStream()
            val head = StringBuilder("HTTP/1.1 " + code + " " + (c.responseMessage ?: "OK") + "\r\n")
            for (k in listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges")) {
                val v = c.getHeaderField(k)
                if (v != null) head.append(k).append(": ").append(v).append("\r\n")
            }
            head.append("Connection: close\r\n\r\n")
            out.write(head.toString().toByteArray())
            if (method != "HEAD") {
                val src = if (code >= 400) c.errorStream else c.inputStream
                if (src != null) {
                    val buf = ByteArray(65536)
                    while (true) {
                        val r = src.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                    }
                }
            }
            out.flush()
        } catch (_: Throwable) {
        } finally {
            try { c?.disconnect() } catch (_: Throwable) {}
            try { cl.close() } catch (_: Throwable) {}
        }
    }

    // بيدوّر في خطوط النظام على خط عربي عشان VLC يرسم الترجمة العربي بدل المربعات
    // (بيفحص فعلاً إن الخط فيه حرف عربي، ومش بس بيعتمد على اسم الملف)
    private var arFontCache: String? = null
    private var arFontDone = false
    private fun arabicFontPath(): String? {
        if (arFontDone) return arFontCache
        arFontDone = true
        // 1) خط متضمّن جوه التطبيق (assets/arabic_sub.ttf): مضمون على أي جهاز
        try {
            val dst = File(filesDir, "arabic_sub.ttf")
            if (!dst.exists() || dst.length() < 10000) {
                assets.open("arabic_sub.ttf").use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
            }
            if (dst.exists() && dst.length() > 10000) { arFontCache = dst.absolutePath; return arFontCache }
        } catch (_: Throwable) {}
        // 2) لو مفيش: دوّر في خطوط النظام
        try {
            val dirs = listOf("/system/fonts", "/system_ext/fonts", "/product/fonts", "/vendor/fonts", "/data/fonts/files")
            val files = ArrayList<File>()
            for (d in dirs) {
                File(d).walkTopDown().maxDepth(3).forEach { f ->
                    val n = f.name.lowercase()
                    if (f.isFile && (n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".ttc"))) files.add(f)
                }
            }
            fun score(f: File): Int {
                val n = f.name.lowercase()
                var sc = 50
                if (n.contains("naskh")) sc -= 20
                if (n.contains("arabic")) sc -= 15
                if (n.contains("kufi")) sc -= 5
                if (n.contains("regular")) sc -= 8
                if (n.contains("ui")) sc += 2
                if (n.contains("bold") || n.contains("italic") || n.contains("light") || n.contains("thin") || n.contains("black") || n.contains("medium")) sc += 15
                if (n.endsWith(".ttc")) sc += 5
                return sc
            }
            val paint = android.graphics.Paint()
            for (f in files.sortedBy { score(it) }) {
                try {
                    paint.typeface = Typeface.createFromFile(f)
                    // لازم الخط يدعم الحروف العربي الأساسية
                    if (paint.hasGlyph("\u0639") && paint.hasGlyph("\u0645") && paint.hasGlyph("\u0627")) {
                        arFontCache = f.absolutePath
                        return arFontCache
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun failFinal() {
        spinner.visibility = View.GONE
        swapping = false
        waitLayer?.visibility = View.GONE
        Toast.makeText(this, "تعذر تشغيل الفيديو", Toast.LENGTH_LONG).show()
        val cu = curUrl
        val shown = try { val u = Uri.parse(cu); (u.scheme + "://" + u.host + (u.path ?: "")).take(160) } catch (_: Throwable) { cu.take(80) }
        val port = proxySock?.localPort
        Thread {
            val sb = StringBuilder("بناء 2\nالرابط (من غير التوكن):\n").append(shown).append("\n")
            if (cu.startsWith("http")) {
                sb.append("\nUA بتاع VLC: ").append(httpProbe(cu, "VLC/3.0.20 LibVLC/3.0.20"))
                sb.append("\nUA متصفح: ").append(httpProbe(cu, BROWSER_UA))
                if (port != null) sb.append("\nعن طريق البروكسي: ").append(httpProbe("http://127.0.0.1:" + port + "/v", BROWSER_UA))
            } else sb.append("\n(مش رابط http)")
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    try { AlertDialog.Builder(this).setTitle("تشخيص التشغيل").setMessage(sb.toString()).setPositiveButton("تمام", null).show() } catch (_: Throwable) {}
                }
            }
        }.start()
    }

    private fun loadMedia(url: String, startIntent: Int) {
        picked = false
        lastPos = 0L
        cast?.let { dv ->
            if (url.startsWith("http")) {
                castPos = startIntent.toLong(); castLen = 0; castFired = false
                castPlaying = true; castSince = System.currentTimeMillis()
                swapping = false
                waitLayer?.visibility = View.GONE
                val tt = titleText
                Thread {
                    val ok = Dlna.start(dv, url, tt, startIntent, applicationContext)
                    if (!ok) runOnUiThread {
                        Toast.makeText(this, "التلفزيون رفض الرابط\n" + Dlna.lastError, Toast.LENGTH_LONG).show()
                        stopCast()
                    } else runOnUiThread { castNotice(); CastService.begin(this, dv, url, tt, startIntent.toLong()) }
                }.start()
                showUi()
                return
            } else {
                // ملف محمّل على الموبايل: التلفزيون مش هيوصله، نرجع نشغّله محليًا
                cast = null
                CastService.end(this)
                Thread { Dlna.stop(dv) }.start()
                castLabel?.visibility = View.GONE
            }
        }
        val u = Uri.parse(resolvedUrl ?: url)
        val m = if (u.scheme == "content") {
            val fd = try { contentResolver.openFileDescriptor(u, "r") } catch (_: Throwable) { null }
            pfd = fd
            if (fd != null) Media(vlc, fd.fileDescriptor) else Media(vlc, u)
        } else Media(vlc, u)
        m.setHWDecoderEnabled(hwOn, false)
        var st = startIntent
        // استكمال المشاهدة: آخر موضع محفوظ (من المشغّل المحلي أو من متابعة البث على التلفزيون)
        if (st <= 0) st = getSharedPreferences("pos", MODE_PRIVATE).getInt("p:$url", 0)
        if (st > 5) m.addOption(":start-time=$st")
        if (u.scheme == "http" || u.scheme == "https") { m.addOption(":http-reconnect=true"); m.addOption(":network-caching=3000"); m.addOption(":http-user-agent=$BROWSER_UA") }
        mp.media = m
        m.release()
    }

    override fun onStart() {
        super.onStart()
        mp.attachViews(video, null, false, false)
        CastService.uiAttached = true
        if (!started) {
            started = true
            val url = intent.getStringExtra("url") ?: intent.data?.toString() ?: return finish()
            curUrl = url
            val cs = CastService.dev
            if (CastService.active && cs != null && CastService.url == url) {
                // رجعنا لنفس الفيديو والبث لسه شغال على التلفزيون: نكمّل تحكّم فيه من غير ما نعيد التشغيل
                cast = cs
                castPos = CastService.pos
                castLen = CastService.len
                castPlaying = CastService.playing
                castFired = false
                castSince = System.currentTimeMillis()
                castLabel?.text = "📺  " + cs.name
                castLabel?.visibility = View.VISIBLE
                startPoll()
            } else loadMedia(url, intent.getIntExtra("start", 0))
        }
        if (cast == null) mp.play()
        h.post(tick)
    }

    override fun onStop() {
        super.onStop()
        CastService.uiAttached = false
        h.removeCallbacks(tick)
        h.removeCallbacks(hideRun)
        panel?.dismiss()
        if (!skipStopReport && !waiting) report(false)
        try {
            val url = curUrl
            val c = cast
            val len = if (c != null) castLen else mp.length / 1000
            if ((url.startsWith("content:") || url.startsWith("file:") || url.startsWith("http")) && len > 0) {
                val pos = if (c != null) castPos else mp.time / 1000
                val e = getSharedPreferences("pos", MODE_PRIVATE).edit()
                if (pos > len * 0.95) e.remove("p:$url") else e.putInt("p:$url", pos.toInt())
                e.apply()
            }
        } catch (_: Throwable) {}
        mp.pause()
        mp.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { proxySock?.close() } catch (_: Throwable) {}
        h.removeCallbacks(subTick)
        h.removeCallbacks(waitTimeout)
        h.removeCallbacks(hudHide)
        h.removeCallbacks(lockHide)
        sleepRun?.let { h.removeCallbacks(it) }
        if (inst === this) inst = null
        // البث بيفضل شغال حتى بعد قفل المشغّل (CastService بيكمّل متابعته). بيتوقف من زرار «إيقاف البث»
        // أو من إشعار الخدمة أو من ريموت التلفزيون.
        try { rd?.stop(); rd?.release() } catch (_: Throwable) {}
        rItems.forEach { try { it.release() } catch (_: Throwable) {} }
        mp.release()
        vlc.release()
        try { pfd?.close() } catch (_: Throwable) {}
    }
}
