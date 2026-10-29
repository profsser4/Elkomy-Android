package com.elkomy.app

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class PlayerActivity : AppCompatActivity() {
    private lateinit var vlc: LibVLC
    private lateinit var mp: MediaPlayer
    private lateinit var video: VLCVideoLayout
    private lateinit var bar: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var tv: TextView
    private val h = Handler(Looper.getMainLooper())
    private var dragging = false
    private var started = false
    private var n = 0
    private var sub = ""
    private var subAdded = false

    private val tick = object : Runnable {
        override fun run() {
            if (++n % 20 == 0) report(false)
            if (!dragging && mp.length > 0) {
                seek.progress = (mp.position * 1000).toInt()
                tv.text = "${fmt(mp.time)} / ${fmt(mp.length)}"
            }
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
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        sub = (intent.getStringExtra("title") ?: "").substringAfter("\n@@SUB@@", "")
        vlc = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--network-caching=1500"))
        mp = MediaPlayer(vlc)
        mp.setEventListener { ev ->
            if (ev.type == MediaPlayer.Event.EndReached) { report(true); finish() }
            else if (ev.type == MediaPlayer.Event.Playing && sub.isNotEmpty() && !subAdded) {
                subAdded = true
                try { mp.addSlave(0, Uri.parse(sub), true) } catch (_: Throwable) {}
            }
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = VLCVideoLayout(this)
        root.addView(video)
        seek = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) { if (user) mp.setPosition(p / 1000f) }
                override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(s: SeekBar) { dragging = false }
            })
        }
        tv = TextView(this).apply { setTextColor(Color.WHITE); text = "00:00" }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xAA000000.toInt()); setPadding(16, 8, 16, 8)
            addView(btn("⏯") { if (mp.isPlaying) mp.pause() else mp.play() })
            addView(tv)
            addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btn("🔊") { pick("الصوت", mp.audioTracks) { mp.setAudioTrack(it) } })
            addView(btn("CC") { pick("الترجمة", mp.spuTracks) { mp.setSpuTrack(it) } })
        }
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        video.setOnClickListener { bar.visibility = if (bar.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        setContentView(root)
    }

    private fun pick(title: String, tracks: Array<MediaPlayer.TrackDescription>?, set: (Int) -> Unit) {
        val t = tracks ?: return
        AlertDialog.Builder(this).setTitle(title)
            .setItems(t.map { it.name }.toTypedArray()) { _, i -> set(t[i].id) }.show()
    }

    override fun onStart() {
        super.onStart()
        mp.attachViews(video, null, false, false)
        if (!started) {
            started = true
            val url = intent.getStringExtra("url") ?: return finish()
            val m = Media(vlc, Uri.parse(url))
            m.setHWDecoderEnabled(true, false)
            val st = intent.getIntExtra("start", 0)
            if (st > 5) m.addOption(":start-time=$st")
            mp.media = m
            m.release()
        }
        mp.play()
        h.post(tick)
    }

    override fun onStop() {
        super.onStop()
        h.removeCallbacks(tick)
        report(false)
        mp.pause()
        mp.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        mp.release()
        vlc.release()
    }
}
