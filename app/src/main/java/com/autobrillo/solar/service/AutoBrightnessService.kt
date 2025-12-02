package com.autobrillo.solar.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.autobrillo.solar.ServiceLocator
import com.autobrillo.solar.data.AutoBrightnessPreferences
import com.autobrillo.solar.data.PreferencesRepository
import com.autobrillo.solar.domain.BrightnessManager
import com.autobrillo.solar.domain.SunTimesCalculator
import com.autobrillo.solar.work.BrightnessWorkScheduler
import com.autobrillo.solar.R
import com.autobrillo.solar.util.SystemBrightnessObserver
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AutoBrightnessService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private lateinit var preferences: PreferencesRepository
    private lateinit var brightnessManager: BrightnessManager
    private lateinit var sunCalculator: SunTimesCalculator
    private var notificationState = NotificationState(
        isPaused = false,
        offsetPercent = 0f,
        sunrise = ZonedDateTime.now(),
        sunset = ZonedDateTime.now().plusHours(12)
    )
    private var latestPreferences: AutoBrightnessPreferences? = null
    private var prefsJob: Job? = null
    private var brightnessObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        NotificationFactory.ensureChannel(this)
        preferences = ServiceLocator.preferences(this)
        brightnessManager = ServiceLocator.brightnessManager(this)
        sunCalculator = ServiceLocator.sunCalculator(this)
        startForeground(
            NotificationFactory.NOTIFICATION_ID,
            NotificationFactory.buildForegroundNotification(this, notificationState)
        )
        observePreferences()
        registerBrightnessObserver()
        scope.launch { preferences.setServiceActive(true) }
        BrightnessWorkScheduler.startImmediately(this, SCREEN_ON_DELAY_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_SCREEN_OFF -> handleScreenOff()
            ACTION_CAMERA_PERMISSION_ERROR -> handleCameraPermissionError()
            ACTION_WRITE_PERMISSION_ERROR -> handleWritePermissionError()
            else -> handleStart()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        prefsJob?.cancel()
        scope.launch { preferences.setServiceActive(false) }
        brightnessObserver?.let { contentResolver.unregisterContentObserver(it) }
        BrightnessWorkScheduler.cancel(this)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStart() {
        scope.launch {
            preferences.setUserPaused(false)
            preferences.clearManualOverride()
            notificationState = notificationState.copy(errorMessage = null)
            updateNotification()
            applyInitialBrightnessSnapshot()
            BrightnessWorkScheduler.startImmediately(this@AutoBrightnessService, SCREEN_ON_DELAY_MS)
        }
    }

    private fun handlePause() {
        scope.launch {
            preferences.setUserPaused(true)
            BrightnessWorkScheduler.cancel(this@AutoBrightnessService)
            notificationState = notificationState.copy(errorMessage = getString(R.string.status_paused))
            updateNotification()
        }
    }

    private fun handleResume() {
        scope.launch {
            preferences.setUserPaused(false)
            preferences.clearManualOverride()
            notificationState = notificationState.copy(errorMessage = null)
            updateNotification()
            BrightnessWorkScheduler.startImmediately(this@AutoBrightnessService, SCREEN_ON_DELAY_MS)
        }
    }

    private fun handleScreenOff() {
        brightnessManager.applyImmediatePercent(BrightnessManager.MIN_PERCENT)
        BrightnessWorkScheduler.cancel(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleCameraPermissionError() {
        scope.launch {
            preferences.setUserPaused(true)
            notificationState = notificationState.copy(
                errorMessage = getString(R.string.error_camera_permission)
            )
            updateNotification()
        }
    }

    private fun handleWritePermissionError() {
        scope.launch {
            preferences.setUserPaused(true)
            notificationState = notificationState.copy(
                errorMessage = getString(R.string.error_write_settings)
            )
            updateNotification()
        }
    }

    private fun observePreferences() {
        prefsJob = scope.launch {
            preferences.preferencesFlow.collect { prefs ->
                latestPreferences = prefs
                notificationState = notificationState.copy(
                    isPaused = prefs.userPaused,
                    offsetPercent = prefs.offsetPercent,
                    sunrise = Instant.ofEpochMilli(prefs.sunriseMillis).atZone(ZoneId.systemDefault()),
                    sunset = Instant.ofEpochMilli(prefs.sunsetMillis).atZone(ZoneId.systemDefault()),
                    errorMessage = if (prefs.userPaused) notificationState.errorMessage else null
                )
                updateNotification()
            }
        }
    }

    private fun registerBrightnessObserver() {
        val observer = SystemBrightnessObserver {
            if (brightnessManager.lastAutoUpdateAge() < 1500L) return@SystemBrightnessObserver
            scope.launch {
                preferences.setManualOverride(TimeUnit.MINUTES.toMillis(10))
            }
        }
        brightnessObserver = observer
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            observer
        )
    }

    private fun applyInitialBrightnessSnapshot() {
        if (!brightnessManager.canWriteSettings()) return
        val prefs = latestPreferences
        val sunTimes = if (prefs != null) {
            sunCalculator.fromEpoch(prefs.sunriseMillis, prefs.sunsetMillis)
        } else {
            sunCalculator.defaultSunTimes()
        }
        if (prefs?.nightMode == true) {
            brightnessManager.applyImmediatePercent(NIGHT_MODE_PERCENT)
            scope.launch {
                preferences.updateLastMeasurement(0f, NIGHT_MODE_PERCENT, "MODO_NOCHE_INICIAL")
            }
            return
        }
        val initialPercent = if (sunTimes.isNight()) NIGHT_INITIAL_PERCENT else DAY_INITIAL_PERCENT
        brightnessManager.applyImmediatePercent(initialPercent)
        scope.launch {
            preferences.updateLastMeasurement(
                0f,
                initialPercent,
                if (sunTimes.isNight()) "ARRANQUE_NOCHE" else "ARRANQUE_DIA"
            )
        }
    }

    private fun updateNotification() {
        val manager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        manager.notify(
            NotificationFactory.NOTIFICATION_ID,
            NotificationFactory.buildForegroundNotification(this, notificationState)
        )
    }

    companion object {
        private const val NIGHT_MODE_PERCENT = 0
        private const val NIGHT_INITIAL_PERCENT = 0
        private const val DAY_INITIAL_PERCENT = 80
        private const val SCREEN_ON_DELAY_MS = 1000L
        private const val ACTION_PREFIX = "com.autobrillo.solar.action."
        const val ACTION_PAUSE = ACTION_PREFIX + "PAUSE"
        const val ACTION_RESUME = ACTION_PREFIX + "RESUME"
        const val ACTION_SCREEN_OFF = ACTION_PREFIX + "SCREEN_OFF"
        const val ACTION_CAMERA_PERMISSION_ERROR = ACTION_PREFIX + "CAMERA_PERM"
        const val ACTION_WRITE_PERMISSION_ERROR = ACTION_PREFIX + "WRITE_PERM"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutoBrightnessService::class.java)
            )
        }

        fun notifyCameraPermissionError(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutoBrightnessService::class.java).apply {
                    action = ACTION_CAMERA_PERMISSION_ERROR
                }
            )
        }

        fun notifyWriteSettingsMissing(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutoBrightnessService::class.java).apply {
                    action = ACTION_WRITE_PERMISSION_ERROR
                }
            )
        }

        fun handleScreenOff(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutoBrightnessService::class.java).apply {
                    action = ACTION_SCREEN_OFF
                }
            )
        }
    }
}
