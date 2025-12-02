package com.autobrillo.solar.domain

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.roundToInt

class BrightnessManager(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var lastAutoUpdateAt = 0L

    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun manageWriteSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun applySmoothPercent(percent: Int, durationMs: Long = 350L) {
        if (!canWriteSettings()) return
        val constrainedPercent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val currentValue = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            percentToValue(constrainedPercent)
        )
        val targetValue = percentToValue(constrainedPercent)
        if (currentValue == targetValue) {
            return
        }
        handler.post {
            ValueAnimator.ofInt(currentValue, targetValue).apply {
                duration = durationMs
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Int
                    setSystemBrightness(value)
                }
                start()
            }
        }
    }

    fun applyImmediatePercent(percent: Int) {
        if (!canWriteSettings()) return
        val constrained = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        setSystemBrightness(percentToValue(constrained))
    }

    private fun percentToValue(percent: Int): Int = (percent / 100f * 255).roundToInt().coerceIn(0, 255)

    private fun setSystemBrightness(value: Int) {
        lastAutoUpdateAt = SystemClock.elapsedRealtime()
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }

    fun lastAutoUpdateAge(): Long = SystemClock.elapsedRealtime() - lastAutoUpdateAt

    companion object {
        const val MIN_PERCENT = 0
        const val MAX_PERCENT = 100
    }
}
