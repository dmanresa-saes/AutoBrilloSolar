package com.autobrillo.solar.util

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper

class SystemBrightnessObserver(
    handler: Handler = Handler(Looper.getMainLooper()),
    private val onChangeCallback: () -> Unit
) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        onChangeCallback()
    }
}
