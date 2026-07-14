package com.secondbrain.lock.service

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.LinearLayout

/**
 * Consumes BACK so the block screen can't be dismissed by the system back gesture/button —
 * this is what makes the lock "hard" rather than a skippable interstitial. The home button/
 * gesture is owned by the OS and can't be intercepted this way; leaving to the home screen is
 * offered explicitly as the only way out.
 */
class BlockingOverlayRoot @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }
}
