package com.mahaam.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifier {
    const val CH_REC = "recording"
    const val CH_SAVED = "saved"
    const val CH_MONTHLY = "monthly"
    const val ID_REC = 1001
    const val ID_SAVED = 1002
    const val ID_MONTHLY = 1003

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REC, c.getString(R.string.ch_recording), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SAVED, c.getString(R.string.ch_saved), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MONTHLY, c.getString(R.string.ch_monthly), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun recording(c: Context, number: Int, startWall: Long): Notification {
        val stop = PendingIntent.getService(
            c, 1,
            Intent(c, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(c, CH_REC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(c.getString(R.string.notif_recording_text, number))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(startWall)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(c))
            .addAction(0, c.getString(R.string.notif_stop), stop)
            .build()
    }

    fun postSaved(c: Context, text: String) {
        val n = NotificationCompat.Builder(c, CH_SAVED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(c))
            .build()
        c.getSystemService(NotificationManager::class.java).notify(ID_SAVED, n)
    }

    fun postMonthly(c: Context, monthKey: String, count: Int) {
        val text = c.getString(R.string.notif_monthly_text, Prefs.monthLabel(monthKey), count)
        val n = NotificationCompat.Builder(c, CH_MONTHLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(c))
            .build()
        c.getSystemService(NotificationManager::class.java).notify(ID_MONTHLY, n)
    }
}
