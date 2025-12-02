package com.autobrillo.solar.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autobrillo.solar.ServiceLocator
import com.autobrillo.solar.domain.BrightnessDecider
import com.autobrillo.solar.service.AutoBrightnessService
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrightnessWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val preferencesRepository = ServiceLocator.preferences(applicationContext)
        val prefs = preferencesRepository.currentPreferences()
        if (prefs.userPaused) {
            return@withContext Result.success()
        }
        if (prefs.nightMode) {
            val brightnessManager = ServiceLocator.brightnessManager(applicationContext)
            if (brightnessManager.canWriteSettings()) {
                brightnessManager.applyImmediatePercent(NIGHT_MODE_PERCENT)
            }
            preferencesRepository.updateLastMeasurement(0f, NIGHT_MODE_PERCENT, "MODO_NOCHE")
            BrightnessWorkScheduler.scheduleNext(applicationContext)
            return@withContext Result.success()
        }
        val now = System.currentTimeMillis()
        val remainingOverride = prefs.manualOverrideUntil - now
        if (remainingOverride > 0) {
            BrightnessWorkScheduler.scheduleNext(applicationContext, remainingOverride)
            return@withContext Result.success()
        }

        val sunTimes = ServiceLocator.sunCalculator(applicationContext).fromEpoch(
            prefs.sunriseMillis,
            prefs.sunsetMillis
        )
        val cameraMeter = ServiceLocator.cameraLightMeter(applicationContext)
        val lux = try {
            cameraMeter.measureLux()
        } catch (security: SecurityException) {
            AutoBrightnessService.notifyCameraPermissionError(applicationContext)
            return@withContext Result.failure()
        } catch (t: Throwable) {
            t.printStackTrace()
            BrightnessWorkScheduler.scheduleNext(applicationContext)
            return@withContext Result.retry()
        }

        val targetPercent = BrightnessDecider.targetPercent(
            lux,
            sunTimes,
            prefs.offsetPercent,
            ZonedDateTime.now()
        )
        val brightnessManager = ServiceLocator.brightnessManager(applicationContext)
        if (!brightnessManager.canWriteSettings()) {
            AutoBrightnessService.notifyWriteSettingsMissing(applicationContext)
            return@withContext Result.failure()
        }
        brightnessManager.applySmoothPercent(targetPercent)
        preferencesRepository.updateLastMeasurement(
            lux,
            targetPercent,
            if (sunTimes.isNight()) "AUTO_NOCHE" else "AUTO_DIA"
        )
        BrightnessWorkScheduler.scheduleNext(applicationContext)
        Result.success()
    }
}

private const val NIGHT_MODE_PERCENT = 0
