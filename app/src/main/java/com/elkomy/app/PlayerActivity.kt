package com.elkomy.app

import android.app.Dialog
import android.content.Context
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
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
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
import org.videolan.libvlc.util.VLCVideoLayout

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
        private val D = arrayOf(
            "M8 5v14l11-7z",
            "M6 19h4V5H6v14zm8-14v14h4V5h-4z",
            "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
            "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z",
            "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z",
            "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 12h4v2H4v-2zm10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z",
            "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z",
            "M19 12h-2v3h-3v2h5v-5zM7 9h3V7H5v5h2V9zm14-6H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16.01H3V4.99h18v14.02z"
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
    companion object { @Volatile var inst: PlayerActivity? = null }
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
    private var zoomFill = false
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
    private var skipStopReport = false

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

    private val hideRun = Runnable { hideUi() }

    private val tick = object : Runnable {
        override fun run() {
            if (waiting) { h.postDelayed(this, 500); return }
            if (++n % 20 == 0) report(false)
            val len = mp.length
            if (!dragging && len > 0) {
                val t = mp.time
                seek.progress = (mp.position * 1000).toInt()
                tvRemain.text = fmt(len - t)
            }
            playBtn.setKind(if (mp.isPlaying) IconView.PAUSE else IconView.PLAY)
            updateNext()
            h.postDelayed(this, 500)
        }
    }

    private fun report(ended: Boolean) {
        val len = mp.length / 1000
        val pos = mp.time / 1000
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
        val raw = intent.getStringExtra("title") ?: ""
        sub = raw.substringAfter("\n@@SUB@@", "")
        titleText = raw.substringBefore("\n@@SUB@@")
        vlc = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--network-caching=1500"))
        mp = MediaPlayer(vlc)
        mp.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.EndReached -> {
                    if (!waiting) {
                        report(true)
                        if (nextLabel.isNotEmpty() && autoNext) beginWait(nextLabel) else finish()
                    }
                }
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
                        try { mp.addSlave(0, Uri.parse(sub), true) } catch (_: Throwable) {}
                    }
                }
                MediaPlayer.Event.Paused -> {
                    playBtn.setKind(IconView.PLAY)
                    h.removeCallbacks(hideRun)
                }
                MediaPlayer.Event.Buffering -> spinner.visibility = if (ev.buffering < 100f) View.VISIBLE else View.GONE
                MediaPlayer.Event.EncounteredError -> {
                    spinner.visibility = View.GONE
                    swapping = false
                    waitLayer?.visibility = View.GONE
                    Toast.makeText(this, "تعذر تشغيل الفيديو", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
        buildUi()
        inst = this
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
                if (System.currentTimeMillis() - lastSeekAt < 700) {
                    val s = side(e.x)
                    if (s != 0) stepSeek(s)
                }
                return false
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (System.currentTimeMillis() - lastSeekAt < 900) return true
                if (uiShown) hideUi() else showUi()
                return true
            }
            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    val s = side(e.x)
                    if (s == 0) togglePlay() else stepSeek(s)
                }
                return true
            }
        })
        touch.setOnTouchListener { _, e -> gd.onTouchEvent(e); true }
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
                    mp.setPosition(s.progress / 1000f)
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
            addView(labeled(IconView.ZOOM, "ملء الشاشة") { toggleZoom() })
            addView(labeled(IconView.SUBS, "الصوت والترجمة") { openTracks() })
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
            addView(row2)
        }
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
        val len = mp.length
        val left = if (len > 0) (len - mp.time) / 1000 else -1L
        // الزرار الثابت في الشريط السفلي: بيظهر طول ما فيه حلقة تالية معروفة
        nextItem?.let { ni ->
            if (nextLabel.isNotEmpty()) {
                (ni.getChildAt(1) as TextView).text = "الحلقة التالية $nextLabel"
                if (ni.visibility != View.VISIBLE) ni.visibility = View.VISIBLE
            } else if (ni.visibility != View.GONE) ni.visibility = View.GONE
        }
        // الزرار الكبير بالعدّاد: آخر 250 ثانية، فوق الواجهة (ظاهر حتى لو الأزرار مخفية)
        if (nextLabel.isNotEmpty() && left in 1L..250L) {
            nextLbl?.text = nextLabel
            nextTime?.text = "· " + fmt(len - mp.time)
            if (b.visibility != View.VISIBLE) b.visibility = View.VISIBLE
        } else if (b.visibility != View.GONE) b.visibility = View.GONE
    }

    private fun goNext() {
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
        n = 0
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
        mp.play()
    }

    // ================= إظهار/إخفاء الواجهة =================
    private fun showUi() {
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
        val was = mp.isPlaying
        if (was) mp.pause() else mp.play()
        playBtn.setKind(if (was) IconView.PLAY else IconView.PAUSE)
        showUi()
    }

    private fun seekBy(sec: Int) {
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

    private fun toggleZoom() {
        zoomFill = !zoomFill
        mp.videoScale = if (zoomFill) MediaPlayer.ScaleType.SURFACE_FIT_SCREEN else MediaPlayer.ScaleType.SURFACE_BEST_FIT
        Toast.makeText(this, if (zoomFill) "ملء الشاشة" else "الحجم الطبيعي", Toast.LENGTH_SHORT).show()
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
        if (list.isEmpty()) {
            inner.addView(optionRow("غير متاح", false) {})
        } else {
            list.forEach { t ->
                val label = if (t.id == -1) "إيقاف" else t.name
                inner.addView(optionRow(label, t.id == selId) { set(t.id); panel?.dismiss() })
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
        box.addView(column("الترجمة", mp.spuTracks, mp.spuTrack, true) { mp.setSpuTrack(it) },
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

    // ================= ريموت / كيبورد (تلفزيون) =================
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
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
    private fun loadMedia(url: String, startIntent: Int) {
        val u = Uri.parse(url)
        val m = if (u.scheme == "content") {
            val fd = try { contentResolver.openFileDescriptor(u, "r") } catch (_: Throwable) { null }
            pfd = fd
            if (fd != null) Media(vlc, fd.fileDescriptor) else Media(vlc, u)
        } else Media(vlc, u)
        m.setHWDecoderEnabled(true, false)
        val local = u.scheme == "content" || u.scheme == "file"
        var st = startIntent
        if (st <= 0 && local) st = getSharedPreferences("pos", MODE_PRIVATE).getInt("p:$url", 0)
        if (st > 5) m.addOption(":start-time=$st")
        if (u.scheme == "http" || u.scheme == "https") { m.addOption(":http-reconnect=true"); m.addOption(":network-caching=3000") }
        mp.media = m
        m.release()
    }

    override fun onStart() {
        super.onStart()
        mp.attachViews(video, null, false, false)
        if (!started) {
            started = true
            val url = intent.getStringExtra("url") ?: return finish()
            curUrl = url
            loadMedia(url, intent.getIntExtra("start", 0))
        }
        mp.play()
        h.post(tick)
    }

    override fun onStop() {
        super.onStop()
        h.removeCallbacks(tick)
        h.removeCallbacks(hideRun)
        panel?.dismiss()
        if (!skipStopReport && !waiting) report(false)
        try {
            val url = curUrl
            val len = mp.length / 1000
            if ((url.startsWith("content:") || url.startsWith("file:")) && len > 0) {
                val pos = mp.time / 1000
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
        h.removeCallbacks(waitTimeout)
        if (inst === this) inst = null
        mp.release()
        vlc.release()
        try { pfd?.close() } catch (_: Throwable) {}
    }
}
