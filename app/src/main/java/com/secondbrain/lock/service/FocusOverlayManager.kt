package com.secondbrain.lock.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.secondbrain.lock.MainActivity
import com.secondbrain.lock.R
import java.util.concurrent.TimeUnit

/**
 * A small floating "time remaining" pill shown while a Pomodoro/focus session is active on any
 * device — deliberately NOT a full-screen block. There's no "distraction block set" concept
 * built yet (which apps to hard-lock during focus is an unmade product decision), so this only
 * gives visibility into the cross-device session, matching the reporter's ask for "draw a
 * pomodoro clock" without silently taking over the whole phone.
 */
class FocusOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var endsAtMillis: Long = 0L
    private var onNaturalCompletion: (() -> Unit)? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            val remaining = endsAtMillis - System.currentTimeMillis()
            if (remaining <= 0) {
                onNaturalCompletion?.invoke()
                hide()
                return
            }
            updateCountdown(remaining)
            handler.postDelayed(this, 1000)
        }
    }

    val isShowing: Boolean get() = overlayView != null

    fun show(endsAtMillis: Long, onNaturalCompletion: () -> Unit) {
        this.endsAtMillis = endsAtMillis
        this.onNaturalCompletion = onNaturalCompletion
        if (overlayView != null) return

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_focus, null)
        view.setOnClickListener {
            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(openApp)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 48
        }

        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) return

        overlayView = view
        handler.post(tickRunnable)
    }

    fun hide() {
        handler.removeCallbacks(tickRunnable)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        onNaturalCompletion = null
    }

    private fun updateCountdown(remainingMillis: Long) {
        val view = overlayView ?: return
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
        view.findViewById<TextView>(R.id.focusCountdown).text = String.format("%02d:%02d", minutes, seconds)
    }
}
