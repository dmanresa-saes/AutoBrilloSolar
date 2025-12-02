package com.autobrillo.solar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autobrillo.solar.service.AutoBrightnessService

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON -> AutoBrightnessService.start(context)
            Intent.ACTION_SCREEN_OFF -> AutoBrightnessService.handleScreenOff(context)
            Intent.ACTION_BOOT_COMPLETED -> AutoBrightnessService.start(context)
        }
    }
}
