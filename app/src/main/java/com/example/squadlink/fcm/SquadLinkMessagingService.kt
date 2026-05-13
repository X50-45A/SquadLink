package com.example.squadlink.fcm

import android.util.Log
import com.example.squadlink.notifications.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SquadLinkMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("SquadLinkFCM", "Message received from: ${remoteMessage.from}")
        
        remoteMessage.notification?.let {
            val title = it.title ?: "SquadLink"
            val body = it.body ?: "Nueva actualizacion de partida"
            
            NotificationHelper(applicationContext).notifyTactical(
                id = (System.currentTimeMillis() % 10000).toInt(),
                title = title,
                message = body
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("SquadLinkFCM", "New FCM token: $token")
    }
}
