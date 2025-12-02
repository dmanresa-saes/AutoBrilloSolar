package com.autobrillo.solar.domain

import android.location.Location
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class SunTimesCalculator(private val context: android.content.Context) {
    suspend fun computeSunTimes(location: Location?): SunTimes {
        val zone = ZoneId.systemDefault()
        if (location == null) {
            return defaultSunTimes(zone)
        }
        val date = LocalDate.now()
        val sunriseUtcHour = solarTimeUtc(date, location.latitude, location.longitude, true)
        val sunsetUtcHour = solarTimeUtc(date, location.latitude, location.longitude, false)
        val sunriseZdt = hoursToZoned(date, sunriseUtcHour, zone)
        val sunsetZdt = hoursToZoned(date, sunsetUtcHour, zone)
        return SunTimes(sunriseZdt, sunsetZdt)
    }

    fun defaultSunTimes(zone: ZoneId = ZoneId.systemDefault()): SunTimes {
        val date = LocalDate.now()
        val sunrise = ZonedDateTime.of(date, DEFAULT_SUNRISE, zone)
        val sunset = ZonedDateTime.of(date, DEFAULT_SUNSET, zone)
        return SunTimes(sunrise, sunset)
    }

    fun fromEpoch(sunriseMillis: Long, sunsetMillis: Long): SunTimes {
        val zone = ZoneId.systemDefault()
        if (sunriseMillis <= 0L || sunsetMillis <= 0L) {
            return defaultSunTimes(zone)
        }
        val sunriseTime = Instant.ofEpochMilli(sunriseMillis).atZone(zone).toLocalTime()
        val sunsetTime = Instant.ofEpochMilli(sunsetMillis).atZone(zone).toLocalTime()
        val today = LocalDate.now(zone)
        var sunrise = ZonedDateTime.of(today, sunriseTime, zone)
        var sunset = ZonedDateTime.of(today, sunsetTime, zone)
        if (!sunset.isAfter(sunrise)) {
            // If both instants come from the previous day, sunset will appear before sunrise
            // once we clamp them to "today". Push sunset to the next day so the interval is valid.
            sunset = sunset.plusDays(1)
        }
        return SunTimes(sunrise, sunset)
    }

    private fun hoursToZoned(date: LocalDate, hours: Double, zone: ZoneId): ZonedDateTime {
        val totalSeconds = (hours * 3600).toLong()
        val base = ZonedDateTime.of(date, java.time.LocalTime.MIDNIGHT, ZoneId.of("UTC"))
        val utcDateTime = base.plusSeconds(totalSeconds)
        return utcDateTime.withZoneSameInstant(zone)
    }

    private fun solarTimeUtc(date: LocalDate, latitude: Double, longitude: Double, sunrise: Boolean): Double {
        val zenith = 90.833
        val n = date.dayOfYear.toDouble()
        val lngHour = longitude / 15.0
        val t = if (sunrise) n + ((6 - lngHour) / 24) else n + ((18 - lngHour) / 24)
        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(rad(m))) + (0.020 * sin(rad(2 * m))) + 282.634
        l = normalize(l)
        var ra = deg(atan(0.91764 * tan(rad(l))))
        ra = normalize(ra)
        val lQuadrant = (l / 90).toInt() * 90
        val raQuadrant = (ra / 90).toInt() * 90
        ra += (lQuadrant - raQuadrant)
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asinSafe(sinDec))
        val cosH = (cos(rad(zenith)) - (sinDec * sin(rad(latitude)))) / (cosDec * cos(rad(latitude)))
        val h = if (sunrise) 360 - degSafe(acosSafe(cosH)) else degSafe(acosSafe(cosH))
        val hHours = h / 15.0
        val tLocal = hHours + ra - (0.06571 * t) - 6.622
        var ut = tLocal - lngHour
        ut = normalizeHour(ut)
        return ut
    }

    private fun normalize(value: Double): Double {
        var result = value % 360
        if (result < 0) result += 360
        return result
    }

    private fun normalizeHour(value: Double): Double {
        var result = value % 24
        if (result < 0) result += 24
        return result
    }

    private fun rad(value: Double): Double = value * PI / 180.0
    private fun deg(value: Double): Double = value * 180.0 / PI

    private fun acosSafe(value: Double): Double = kotlin.math.acos(value.coerceIn(-1.0, 1.0))
    private fun asinSafe(value: Double): Double = kotlin.math.asin(value.coerceIn(-1.0, 1.0))
    private fun degSafe(value: Double): Double = deg(value)

    data class SunTimes(val sunrise: ZonedDateTime, val sunset: ZonedDateTime) {
        fun isNight(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
            val todaySunrise = sunrise.withZoneSameInstant(now.zone)
            val todaySunset = sunset.withZoneSameInstant(now.zone)
            return now.isBefore(todaySunrise) || now.isAfter(todaySunset)
        }
    }

    companion object {
        private val DEFAULT_SUNRISE = java.time.LocalTime.of(7, 0)
        private val DEFAULT_SUNSET = java.time.LocalTime.of(20, 0)
    }
}
