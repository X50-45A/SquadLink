package com.example.squadlink.fcm

import android.util.Log
import com.example.squadlink.data.FirebaseAccountRepository
import com.example.squadlink.data.UserPreferencesRepository
import com.example.squadlink.notifications.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SquadLinkMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("SquadLinkFCM", "Message received from: ${remoteMessage.from}")
        
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "SquadLink"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "Nueva actualizacion de partida"

        NotificationHelper(applicationContext).notifyTactical(
            id = (System.currentTimeMillis() % 10000).toInt(),
            title = title,
            message = body
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("SquadLinkFCM", "New FCM token: $token")
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                FirebaseAccountRepository(UserPreferencesRepository(applicationContext))
                    .saveFcmToken(token)
            }.onFailure {
                Log.w("SquadLinkFCM", "Could not persist FCM token", it)
            }
        }
    }
}
