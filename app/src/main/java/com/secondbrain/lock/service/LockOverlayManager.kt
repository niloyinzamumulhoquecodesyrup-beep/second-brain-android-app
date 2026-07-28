package com.secondbrain.lock.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.secondbrain.lock.R
import com.secondbrain.lock.data.UsageStatsHelper
import java.util.concurrent.TimeUnit

/**
 * Owns the single full-screen "hard lock" overlay window. Only one can be shown at a time —
 * whichever locked app is currently in the foreground.
 */
class LockOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentLockedPackage: String? = null
    private var countdownEnabled = true

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    val isShowing: Boolean get() = overlayView != null
    val lockedPackage: String? get() = currentLockedPackage

    /**
     * @param label Short reason shown above the app name, e.g. "DAILY LIMIT REACHED",
     *   "SCHEDULED BLOCK", "FOCUS SESSION".
     * @param subtitle One-line explanation, non-punitive tone.
     * @param showMidnightCountdown Whether the bottom panel counts down to local midnight —
     *   true for daily-budget locks; false for schedule/focus locks, which resolve on their own
     *   window/session ending rather than at midnight (in which case [subtitle] should say so).
     */
    fun show(
        packageName: String,
        appName: String,
        label: String = "DAILY LIMIT REACHED",
        subtitle: String = "You've used your time for today. This app unlocks automatically at midnight.",
        showMidnightCountdown: Boolean = true
    ) {
        if (currentLockedPackage == packageName && overlayView != null) return
        hide()

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_lock, null)
        view.findViewById<TextView>(R.id.overlayLabel).text = label
        view.findViewById<TextView>(R.id.overlayAppName).text = appName
        view.findViewById<TextView>(R.id.overlaySubtitle).text = subtitle
        view.findViewById<TextView>(R.id.overlayHome).setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }

        countdownEnabled = showMidnightCountdown
        val countdownPanel = view.findViewById<View>(R.id.overlayCountdownPanel)
        countdownPanel.visibility = if (showMidnightCountdown) View.VISIBLE else View.GONE

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) return

        overlayView = view
        currentLockedPackage = packageName
        if (showMidnightCountdown) handler.post(tickRunnable)
    }

    fun hide() {
        handler.removeCallbacks(tickRunnable)
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
        currentLockedPackage = null
    }

    private fun updateCountdown() {
        if (!countdownEnabled) return
        val view = overlayView ?: return
        val remaining = UsageStatsHelper.millisUntilReset()
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        view.findViewById<TextView>(R.id.overlayCountdown).text =
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
