package com.example.squadlink.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.squadlink.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_TACTICAL = "tactical_alerts"
    }

    fun ensureChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_TACTICAL) == null) {
            val channel = NotificationChannel(
                CHANNEL_TACTICAL,
                "Alertas tacticas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                description = "Notificaciones de partida y geofence"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyTactical(id: Int, title: String, message: String) {
        if (!hasNotificationPermission()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_TACTICAL)
            .setSmallIcon(R.drawable.ic_squadlink_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
