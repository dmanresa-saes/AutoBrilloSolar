package com.autobrillo.solar.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "auto_brillo")

class PreferencesRepository(private val context: Context) {
    private val offsetKey = floatPreferencesKey("offset_percent")
    private val userPausedKey = booleanPreferencesKey("user_paused")
    private val manualOverrideUntilKey = longPreferencesKey("manual_override_until")
    private val sunriseKey = longPreferencesKey("sunrise_millis")
    private val sunsetKey = longPreferencesKey("sunset_millis")
    private val serviceActiveKey = booleanPreferencesKey("service_active")
    private val nightModeKey = booleanPreferencesKey("night_mode")
    private val lastLuxKey = floatPreferencesKey("last_lux")
    private val lastAppliedKey = floatPreferencesKey("last_applied_percent")
    private val lastReasonKey = androidx.datastore.preferences.core.stringPreferencesKey("last_reason")

    val preferencesFlow: Flow<AutoBrightnessPreferences> = context.dataStore.data.map { prefs ->
        AutoBrightnessPreferences(
            offsetPercent = prefs[offsetKey] ?: 0f,
            userPaused = prefs[userPausedKey] ?: false,
            manualOverrideUntil = prefs[manualOverrideUntilKey] ?: 0L,
            sunriseMillis = prefs[sunriseKey] ?: defaultSunriseMillis(),
            sunsetMillis = prefs[sunsetKey] ?: defaultSunsetMillis(),
            serviceActive = prefs[serviceActiveKey] ?: false,
            nightMode = prefs[nightModeKey] ?: false,
            lastLux = prefs[lastLuxKey] ?: -1f,
            lastAppliedPercent = prefs[lastAppliedKey] ?: -1f,
            lastReason = prefs[lastReasonKey] ?: ""
        )
    }

    suspend fun currentPreferences(): AutoBrightnessPreferences = preferencesFlow.first()

    suspend fun setOffset(percent: Float) {
        context.dataStore.edit { it[offsetKey] = percent.coerceIn(-20f, 20f) }
    }

    suspend fun setUserPaused(paused: Boolean) {
        context.dataStore.edit { it[userPausedKey] = paused }
    }

    suspend fun setManualOverride(durationMillis: Long) {
        val until = System.currentTimeMillis() + durationMillis
        context.dataStore.edit { it[manualOverrideUntilKey] = until }
    }

    suspend fun clearManualOverride() {
        context.dataStore.edit { it[manualOverrideUntilKey] = 0L }
    }

    suspend fun updateSunTimes(sunriseMillis: Long, sunsetMillis: Long) {
        context.dataStore.edit {
            it[sunriseKey] = sunriseMillis
            it[sunsetKey] = sunsetMillis
        }
    }

    suspend fun setServiceActive(active: Boolean) {
        context.dataStore.edit { it[serviceActiveKey] = active }
    }

    suspend fun setNightMode(enabled: Boolean) {
        context.dataStore.edit { it[nightModeKey] = enabled }
    }

    suspend fun updateLastMeasurement(lux: Float, percent: Int, reason: String) {
        context.dataStore.edit {
            it[lastLuxKey] = lux
            it[lastAppliedKey] = percent.toFloat()
            it[lastReasonKey] = reason
        }
    }

    private fun defaultSunriseMillis(): Long = defaultTimeMillis(LocalTime.of(7, 0))
    private fun defaultSunsetMillis(): Long = defaultTimeMillis(LocalTime.of(20, 0))

    private fun defaultTimeMillis(time: LocalTime): Long {
        val zoned = ZonedDateTime.of(LocalDate.now(), time, ZoneId.systemDefault())
        return zoned.toInstant().toEpochMilli()
    }
}

data class AutoBrightnessPreferences(
    val offsetPercent: Float,
    val userPaused: Boolean,
    val manualOverrideUntil: Long,
    val sunriseMillis: Long,
    val sunsetMillis: Long,
    val serviceActive: Boolean,
    val nightMode: Boolean,
    val lastLux: Float,
    val lastAppliedPercent: Float,
    val lastReason: String
)
