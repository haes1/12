package com.example.eyeswipe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * This service does nothing on its own. It only exists so EyeTrackingService
 * has a way to inject a real system-level swipe gesture into whatever app is
 * currently in the foreground (TikTok, Instagram, etc.). The user has to
 * enable it manually in Settings > Accessibility — Android does not allow an
 * app to grant itself this permission.
 */
class SwipeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — we never react to accessibility events,
        // we only use this service to dispatch gestures.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    /** Simulates a vertical swipe-up, like flicking to the next TikTok/Reels video. */
    fun performSwipeUp() {
        val metrics = resources.displayMetrics
        val startX = metrics.widthPixels / 2f
        val startY = metrics.heightPixels * 0.75f
        val endY = metrics.heightPixels * 0.25f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS))
            .build()

        dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val TAG = "SwipeAccessibility"
        private const val SWIPE_DURATION_MS = 220L

        var instance: SwipeAccessibilityService? = null
            private set
    }
}
