package com.secondbrain.lock.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.secondbrain.lock.R

/**
 * A brief, calm, non-punitive pause shown once — right before a hard lock takes over — rather
 * than jumping straight to a block. Auto-continues after [COOLDOWN_SECONDS] or as soon as the
 * user taps a reason chip; either way it always ends by handing off to the real lock, it never
 * grants extra time.
 */
class CooldownOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shownForPackageInternal: String? = null
    private var secondsLeft = COOLDOWN_SECONDS
    private var onDone: (() -> Unit)? = null

    val isShowing: Boolean get() = overlayView != null
    val shownForPackage: String? get() = shownForPackageInternal

    private val tickRunnable = object : Runnable {
        override fun run() {
            secondsLeft--
            overlayView?.findViewById<TextView>(R.id.cooldownCountdown)?.text = "Continuing in $secondsLeft..."
            if (secondsLeft <= 0) {
                finish()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    fun show(packageName: String, appName: String, onDone: () -> Unit) {
        if (shownForPackageInternal == packageName && overlayView != null) return
        hide()
        this.onDone = onDone
        secondsLeft = COOLDOWN_SECONDS

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_cooldown, null)
        view.findViewById<TextView>(R.id.cooldownAppName).text = appName

        val reasons = listOf("Habit", "Bored", "Checking something", "Needed it")
        val row1 = view.findViewById<LinearLayout>(R.id.cooldownChipRow)
        val row2 = view.findViewById<LinearLayout>(R.id.cooldownChipRow2)
        reasons.forEachIndexed { index, reason ->
            val chip = TextView(context).apply {
                text = reason
                setTextColor(0xFFA7AEB5.toInt())
                textSize = 12.5f
                setPadding(28, 16, 28, 16)
                background = context.getDrawable(R.drawable.overlay_chip_bg)
                setOnClickListener { finish() }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
            chip.layoutParams = params
            if (index < 2) row1.addView(chip) else row2.addView(chip)
        }

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
        if (!added) {
            onDone()
            return
        }

        overlayView = view
        shownForPackageInternal = packageName
        handler.postDelayed(tickRunnable, 1000)
    }

    private fun finish() {
        val callback = onDone
        hide()
        callback?.invoke()
    }

    /** Cancels the cooldown without invoking the completion callback — e.g. the user left the app. */
    fun hide() {
        handler.removeCallbacks(tickRunnable)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        shownForPackageInternal = null
        onDone = null
    }

    companion object {
        private const val COOLDOWN_SECONDS = 5
    }
}
