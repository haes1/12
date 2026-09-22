package com.example.eyeswipe

import android.content.Context

/**
 * Small wrapper around SharedPreferences so MainActivity (which writes the
 * slider value) and EyeTrackingService (which reads it on every frame) agree
 * on where the "how far up you must look" threshold lives.
 */
object Prefs {
    private const val PREFS_NAME = "eyeswipe_prefs"
    private const val KEY_THRESHOLD_PROGRESS = "pitch_threshold_progress"

    // SeekBar progress (0..35) maps to a threshold of 5..40 degrees.
    const val DEFAULT_THRESHOLD_PROGRESS = 18

    fun getThresholdProgress(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THRESHOLD_PROGRESS, DEFAULT_THRESHOLD_PROGRESS)

    fun setThresholdProgress(context: Context, progress: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THRESHOLD_PROGRESS, progress)
            .apply()
    }

    /** The actual "looking up" angle in degrees that triggers a swipe. */
    fun getThresholdDegrees(context: Context): Int = getThresholdProgress(context) + 5
}
