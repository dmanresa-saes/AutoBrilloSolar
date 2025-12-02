package com.autobrillo.solar.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BrightnessWorkScheduler {
    private const val UNIQUE_NAME = "auto-brightness-cycle"
    private val DEFAULT_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(5)

    fun startImmediately(context: Context, initialDelayMillis: Long = 0L) {
        enqueue(context, initialDelayMillis)
    }

    fun scheduleNext(context: Context, delayMillis: Long = DEFAULT_DELAY_MILLIS) {
        enqueue(context, delayMillis)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    private fun enqueue(context: Context, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<BrightnessWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
