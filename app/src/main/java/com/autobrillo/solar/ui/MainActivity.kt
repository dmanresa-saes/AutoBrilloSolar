package com.autobrillo.solar.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.autobrillo.solar.ServiceLocator
import com.autobrillo.solar.BuildConfig
import com.autobrillo.solar.data.AutoBrightnessPreferences
import com.autobrillo.solar.databinding.ActivityMainBinding
import com.autobrillo.solar.service.AutoBrightnessService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { ServiceLocator.preferences(this) }
    private val brightnessManager by lazy { ServiceLocator.brightnessManager(this) }
    private val locationProvider by lazy { ServiceLocator.locationProvider(this) }
    private val sunCalculator by lazy { ServiceLocator.sunCalculator(this) }
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionHints()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonPermissions.setOnClickListener { requestRuntimePermissions() }
        binding.buttonWriteSettings.setOnClickListener {
            startActivity(brightnessManager.manageWriteSettingsIntent())
        }
        binding.buttonUpdateLocation.setOnClickListener { updateSunTimesManually() }
        binding.buttonNightToggle.setOnClickListener { toggleNightMode() }
        binding.buttonOffset.setOnClickListener {
            startActivity(OffsetActivity.launchIntent(this))
        }
        binding.buttonToggleService.setOnClickListener {
            AutoBrightnessService.start(this)
        }
        AutoBrightnessService.start(this)

        lifecycleScope.launch {
            preferences.preferencesFlow.collectLatest { prefs ->
                updateUi(prefs)
            }
        }
        updatePermissionHints()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionHints()
    }

    private fun updateUi(prefs: AutoBrightnessPreferences) {
        val sunrise = Instant.ofEpochMilli(prefs.sunriseMillis).atZone(ZoneId.systemDefault())
        val sunset = Instant.ofEpochMilli(prefs.sunsetMillis).atZone(ZoneId.systemDefault())
        binding.textServiceStatus.text = if (prefs.userPaused) {
            getString(com.autobrillo.solar.R.string.status_paused)
        } else if (prefs.serviceActive) {
            getString(com.autobrillo.solar.R.string.status_active)
        } else {
            getString(com.autobrillo.solar.R.string.status_waiting)
        }
        binding.textSunrise.text = getString(com.autobrillo.solar.R.string.label_sunrise, timeFormatter.format(sunrise))
        binding.textSunset.text = getString(com.autobrillo.solar.R.string.label_sunset, timeFormatter.format(sunset))
        binding.textOffset.text = getString(com.autobrillo.solar.R.string.label_offset_value, prefs.offsetPercent)
        binding.textVersion.text = getString(
            com.autobrillo.solar.R.string.label_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.BUILD_TYPE
        )
        val debugText = if (prefs.lastAppliedPercent >= 0 && prefs.lastReason.isNotEmpty()) {
            getString(
                com.autobrillo.solar.R.string.label_debug_info,
                prefs.lastLux,
                prefs.lastAppliedPercent.toInt(),
                prefs.lastReason
            )
        } else {
            "-"
        }
        binding.textDebug.text = debugText
        binding.buttonNightToggle.text = getString(
            if (prefs.nightMode) com.autobrillo.solar.R.string.night_toggle_off
            else com.autobrillo.solar.R.string.night_toggle_on
        )
        binding.buttonNightToggle.isSelected = prefs.nightMode
    }

    private fun requestRuntimePermissions() {
        val missing = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.CAMERA)) missing += Manifest.permission.CAMERA
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) missing += Manifest.permission.ACCESS_COARSE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            updatePermissionHints()
        }
    }

    private fun updatePermissionHints() {
        val cameraGranted = hasPermission(Manifest.permission.CAMERA)
        val locationGranted = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val writeGranted = brightnessManager.canWriteSettings()
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        binding.textPermissions.text = getString(
            com.autobrillo.solar.R.string.label_permissions,
            statusIcon(cameraGranted),
            statusIcon(locationGranted),
            statusIcon(writeGranted),
            statusIcon(notificationsGranted)
        )
    }

    private fun updateSunTimesManually() {
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestRuntimePermissions()
            Toast.makeText(this, com.autobrillo.solar.R.string.toast_location_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }
        binding.buttonUpdateLocation.isEnabled = false
        lifecycleScope.launch {
            val messageRes = try {
                val location = locationProvider.getLocation()
                val sunTimes = sunCalculator.computeSunTimes(location)
                preferences.updateSunTimes(
                    sunTimes.sunrise.toInstant().toEpochMilli(),
                    sunTimes.sunset.toInstant().toEpochMilli()
                )
                if (location == null) {
                    com.autobrillo.solar.R.string.toast_location_fallback
                } else {
                    com.autobrillo.solar.R.string.toast_location_updated_success
                }
            } catch (t: Throwable) {
                com.autobrillo.solar.R.string.toast_location_error
            } finally {
                binding.buttonUpdateLocation.isEnabled = true
            }
            Toast.makeText(this@MainActivity, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleNightMode() {
        lifecycleScope.launch {
            val current = preferences.currentPreferences()
            if (!current.nightMode) {
                preferences.setNightMode(true)
                if (brightnessManager.canWriteSettings()) {
                    brightnessManager.applyImmediatePercent(0)
                }
                preferences.updateLastMeasurement(0f, 0, "MODO_NOCHE_MANUAL")
                Toast.makeText(this@MainActivity, com.autobrillo.solar.R.string.toast_night_mode_on, Toast.LENGTH_SHORT).show()
            } else {
                preferences.setNightMode(false)
                preferences.updateLastMeasurement(
                    current.lastLux,
                    current.lastAppliedPercent.toInt(),
                    "AUTO"
                )
                Toast.makeText(this@MainActivity, com.autobrillo.solar.R.string.toast_night_mode_off, Toast.LENGTH_SHORT).show()
                AutoBrightnessService.start(this@MainActivity)
            }
        }
    }

    private fun statusIcon(granted: Boolean): String = if (granted) "OK" else "PEND"

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
