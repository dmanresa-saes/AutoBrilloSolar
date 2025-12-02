package com.autobrillo.solar

import android.app.Application
import com.autobrillo.solar.data.PreferencesRepository
import com.autobrillo.solar.domain.BrightnessManager
import com.autobrillo.solar.domain.CameraLightMeter
import com.autobrillo.solar.domain.LocationProvider
import com.autobrillo.solar.domain.SunTimesCalculator

class AutoBrilloApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val preferencesRepository = PreferencesRepository(app)
    val brightnessManager = BrightnessManager(app)
    val cameraLightMeter = CameraLightMeter(app)
    val locationProvider = LocationProvider(app)
    val sunTimesCalculator = SunTimesCalculator(app)
}

object ServiceLocator {
    private fun container(context: android.content.Context) =
        (context.applicationContext as AutoBrilloApp).container

    fun preferences(context: android.content.Context) = container(context).preferencesRepository
    fun brightnessManager(context: android.content.Context) = container(context).brightnessManager
    fun cameraLightMeter(context: android.content.Context) = container(context).cameraLightMeter
    fun locationProvider(context: android.content.Context) = container(context).locationProvider
    fun sunCalculator(context: android.content.Context) = container(context).sunTimesCalculator
}
