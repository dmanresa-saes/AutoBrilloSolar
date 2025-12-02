package com.autobrillo.solar.domain

import java.time.ZonedDateTime
import kotlin.math.ln
import kotlin.math.roundToInt

object BrightnessDecider {
    private const val MODEL_MAX_LUX = 1200f

    fun targetPercent(
        lux: Float,
        sunTimes: SunTimesCalculator.SunTimes,
        offsetPercent: Float,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Int {
        val safeLux = lux.coerceAtLeast(0f)
        val normalized = ln(safeLux + 1f) / ln(MODEL_MAX_LUX + 1f)
        val scaled = BrightnessManager.MIN_PERCENT + (normalized * (BrightnessManager.MAX_PERCENT - BrightnessManager.MIN_PERCENT))
        val withOffset = scaled + offsetPercent
        if (sunTimes.isNight(now) && safeLux <= NIGHT_ZERO_LUX) {
            return BrightnessManager.MIN_PERCENT
        }
        val minNightPercent = if (sunTimes.isNight(now)) BrightnessManager.MIN_PERCENT else MIN_DAY_PERCENT
        return withOffset.coerceIn(
            minNightPercent.toFloat(),
            BrightnessManager.MAX_PERCENT.toFloat()
        ).roundToInt()
    }

    private const val NIGHT_ZERO_LUX = 5f
    private const val MIN_DAY_PERCENT = 5
}
