package com.elkomy.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.elkomy.app.DownloadQueue.State
import java.util.Locale

fun dlSize(b: Long): String {
    if (b <= 0) return "0 B"
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < 4) { v /= 1024; i++ }
    return String.format(Locale.US, if (i >= 2) "%.1f" else "%.0f", v) + " " + u[i]
}

fun dlEta(s: Long): String {
    val h = s / 3600
    val m = s % 3600 / 60
    val x = s % 60
    return if (h > 0) "${h}س ${m}د" else if (m > 0) "${m}د ${x}ث" else "${x}ث"
}

class DownloadsActivity : AppCompatActivity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var tvBig: TextView
    private lateinit var tvSum: TextView
    private lateinit var overall: ProgressBar
    private lateinit var empty: TextView
    private lateinit var lv: ListView
    private lateinit var overallFg: GradientDrawable

    private val BLUE = 0xFF38BDF8.toInt()
    private val GREEN = 0xFF22C55E.toInt()
    private val RED = 0xFFEF4444.toInt()
    private val GRAY = 0xFF94A3B8.toInt()
    private val CARD = 0xFF1E293B.toInt()

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            h.postDelayed(this, 500)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun round(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }

    /** شريط تقدم بحواف دايرية؛ بيرجّع (الشريط، الجزء الملوّن) عشان نغيّر لونه حسب الحالة */
    private fun makeBar(ctx: Context, hDp: Int): Pair<ProgressBar, GradientDrawable> {
        val bg = round(0xFF334155.toInt(), hDp / 2)
        val fg = round(BLUE, hDp / 2)
        val layers = LayerDrawable(arrayOf(bg, ClipDrawable(fg, Gravity.START, ClipDrawable.HORIZONTAL)))
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressDrawable = layers
        }
        return Pair(bar, fg)
    }

    private fun txt(ctx: Context, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    inner class Holder(ctx: Context) {
        val root = LinearLayout(ctx)
        val name = txt(ctx, 14f, Color.WHITE, true)
        val pct = txt(ctx, 22f, BLUE, true)
        val info = txt(ctx, 12f, GRAY)
        val a1 = txt(ctx, 20f, Color.WHITE)
        val a2 = txt(ctx, 20f, Color.WHITE)
        val bar: ProgressBar
        val fg: GradientDrawable

        init {
            val (b, f) = makeBar(ctx, 8)
            bar = b; fg = f
            root.orientation = LinearLayout.VERTICAL
            root.background = round(CARD, 14)
            root.setPadding(dp(14), dp(12), dp(14), dp(12))
            val r1 = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            name.maxLines = 2
            name.ellipsize = android.text.TextUtils.TruncateAt.END
            r1.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            for (a in arrayOf(a1, a2)) {
                a.setPadding(dp(10), dp(4), dp(10), dp(4))
                a.isClickable = true
                a.isFocusable = true
                r1.addView(a)
            }
            root.addView(r1)
            root.addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(10) })
            val r3 = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            pct.minWidth = dp(70)
            r3.addView(pct)
            info.gravity = Gravity.END
            r3.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(r3, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            root.tag = this
        }

        fun bind(item: DownloadQueue.Item) {
            name.text = item.name
            val total = item.total
            val done = item.done
            a2.visibility = View.GONE
            a1.visibility = View.VISIBLE
            when (item.state) {
                State.RUNNING -> {
                    val p = if (total > 0) (done * 1000 / total).toInt() else 0
                    bar.progress = p
                    fg.setColor(BLUE)
                    pct.text = if (total > 0) "${p / 10}%" else dlSize(done)
                    pct.setTextColor(BLUE)
                    val eta = if (item.speed > 0 && total > 0) (total - done) / item.speed else -1L
                    info.text = (if (total > 0) "${dlSize(done)} / ${dlSize(total)}" else dlSize(done)) +
                        (if (item.speed > 0) " · ${dlSize(item.speed)}/s" else "") +
                        (if (eta >= 0) " · متبقي ${dlEta(eta)}" else "")
                    a1.text = "✖"
                    a1.setOnClickListener { DownloadQueue.cancel(item) }
                }
                State.QUEUED -> {
                    bar.progress = 0
                    val pos = DownloadQueue.items.filter { it.state == State.QUEUED }.indexOf(item) + 1
                    pct.text = "⏳"
                    pct.setTextColor(GRAY)
                    info.text = "في الانتظار · رقم $pos في الطابور"
                    a1.text = "✖"
                    a1.setOnClickListener { DownloadQueue.cancel(item) }
                }
                State.DONE -> {
                    bar.progress = 1000
                    fg.setColor(GREEN)
                    pct.text = "✓"
                    pct.setTextColor(GREEN)
                    info.text = "اكتمل · ${dlSize(item.done)}"
                    a1.text = "▶"
                    a1.setOnClickListener {
                        val u = item.uri ?: return@setOnClickListener
                        startActivity(Intent(this@DownloadsActivity, PlayerActivity::class.java)
                            .putExtra("url", u).putExtra("title", item.name))
                    }
                    a2.visibility = View.VISIBLE
                    a2.text = "🗑"
                    a2.setOnClickListener {
                        android.app.AlertDialog.Builder(this@DownloadsActivity).setTitle(item.name)
                            .setItems(arrayOf("حذف من القايمة فقط", "حذف الملف من الجهاز")) { _, w ->
                                if (w == 1) DownloadQueue.deleteFile(this@DownloadsActivity, item) else DownloadQueue.remove(item)
                                refresh()
                            }.setNegativeButton("إلغاء", null).show()
                    }
                }
                State.FAILED, State.CANCELED -> {
                    fg.setColor(RED)
                    bar.progress = if (total > 0) (done * 1000 / total).toInt() else 0
                    pct.text = if (item.state == State.FAILED) "✕" else "⏹"
                    pct.setTextColor(RED)
                    info.text = if (item.state == State.FAILED) "فشل: ${item.error}" else "اتلغى"
                    a1.text = "↻"
                    a1.setOnClickListener { DownloadQueue.retry(this@DownloadsActivity, item) }
                    a2.visibility = View.VISIBLE
                    a2.text = "🗑"
                    a2.setOnClickListener { DownloadQueue.remove(item); refresh() }
                }
            }
        }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = DownloadQueue.items.size
        override fun getItem(p: Int): Any = DownloadQueue.items[p]
        override fun getItemId(p: Int) = DownloadQueue.items[p].id
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val hd = (cv?.tag as? Holder) ?: Holder(parent.context)
            val item = DownloadQueue.items.getOrNull(p) ?: return hd.root
            hd.bind(item)
            return hd.root
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        DownloadQueue.load(this)
        supportActionBar?.hide()
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B1220.toInt())
            fitsSystemWindows = true
        }

        // الشريط العلوي
        val top = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(14), dp(6))
        }
        top.addView(txt(ctx, 22f, Color.WHITE).apply {
            text = "➡"
            setPadding(dp(14), dp(6), dp(14), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })
        top.addView(txt(ctx, 20f, Color.WHITE, true).apply { text = "⬇ التحميلات" }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(txt(ctx, 13f, BLUE, true).apply {
            text = "مسح الفاشل"
            setPadding(dp(10), dp(6), dp(10), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { DownloadQueue.clearFinished(); refresh() }
        })
        root.addView(top)

        // كارت العدّاد الكلي
        val sumCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = round(CARD, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val sumRow = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        tvBig = txt(ctx, 30f, BLUE, true).apply { text = "0%"; minWidth = dp(90) }
        tvSum = txt(ctx, 14f, Color.WHITE)
        sumRow.addView(tvBig)
        sumRow.addView(tvSum, LinearLayout.LayoutParams(0, -2, 1f))
        sumCard.addView(sumRow)
        val (ob, ofg) = makeBar(ctx, 10)
        overall = ob; overallFg = ofg
        sumCard.addView(overall, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(10) })
        root.addView(sumCard, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(8), dp(14), dp(10))
        })

        empty = txt(ctx, 15f, GRAY).apply {
            text = "مفيش تحميلات دلوقتي.\nدوس ⬇ تحميل على أي فيلم أو حلقة."
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(60), dp(20), dp(20))
        }
        root.addView(empty, LinearLayout.LayoutParams(-1, -2))

        lv = ListView(ctx).apply {
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = dp(10)
            setPadding(dp(14), 0, dp(14), dp(20))
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
            adapter = this@DownloadsActivity.adapter
        }
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
        val all = DownloadQueue.items
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        val batch = DownloadQueue.batchItems()
        val n = batch.size
        if (n == 0) {
            tvBig.text = "0%"; tvSum.text = "—"; overall.progress = 0
            return
        }
        var sum = 0.0
        var finished = 0
        var okN = 0
        var failN = 0
        batch.forEach {
            when (it.state) {
                State.DONE -> { sum += 1.0; finished++; okN++ }
                State.FAILED, State.CANCELED -> { sum += 1.0; finished++; failN++ }
                State.RUNNING -> if (it.total > 0) sum += it.done.toDouble() / it.total
                State.QUEUED -> {}
            }
        }
        val p = (sum / n * 100).toInt().coerceIn(0, 100)
        overall.progress = p * 10
        tvBig.text = "$p%"
        val active = finished < n
        overallFg.setColor(if (!active && failN == 0) GREEN else BLUE)
        tvBig.setTextColor(if (!active && failN == 0) GREEN else BLUE)
        tvSum.text = if (active) {
            val cur = batch.firstOrNull { it.state == State.RUNNING }
            "جاري تحميل ملف ${minOf(finished + 1, n)} من $n" +
                (if (cur != null && cur.speed > 0) "\n⚡ ${dlSize(cur.speed)}/s" else "")
        } else {
            "✓ خلص: $okN من $n" + (if (failN > 0) " · $failN فشل أو اتلغى" else "")
        }
    }

    override fun onResume() {
        super.onResume()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
    }
}
