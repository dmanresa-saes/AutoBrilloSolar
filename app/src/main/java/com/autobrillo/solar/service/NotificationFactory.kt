package com.autobrillo.solar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autobrillo.solar.R
import com.autobrillo.solar.ui.OffsetActivity
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationFactory {
    private const val CHANNEL_ID = "auto_brightness_channel"
    private const val CHANNEL_NAME = "AutoBrillo Solar"
    const val NOTIFICATION_ID = 42

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notification_desc)
                enableLights(false)
                enableVibration(false)
                lightColor = Color.WHITE
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(context: Context, state: NotificationState): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            OffsetActivity.launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, AutoBrightnessService::class.java).apply {
                action = if (state.isPaused) AutoBrightnessService.ACTION_RESUME else AutoBrightnessService.ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val sunrise = timeFormatter.format(state.sunrise.withZoneSameInstant(ZoneId.systemDefault()))
        val sunset = timeFormatter.format(state.sunset.withZoneSameInstant(ZoneId.systemDefault()))
        val contentText = state.errorMessage ?: context.getString(
            R.string.notification_content,
            sunrise,
            sunset,
            state.offsetPercent
        )
        val actionIcon = if (state.isPaused) R.drawable.ic_play else R.drawable.ic_pause
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                actionIcon,
                context.getString(if (state.isPaused) R.string.action_resume else R.string.action_pause),
                actionIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }
}

data class NotificationState(
    val isPaused: Boolean,
    val offsetPercent: Float,
    val sunrise: java.time.ZonedDateTime,
    val sunset: java.time.ZonedDateTime,
    val errorMessage: String? = null
)
