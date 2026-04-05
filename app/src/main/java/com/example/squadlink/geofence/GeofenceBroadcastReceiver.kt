package com.example.squadlink.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.squadlink.data.UserPreferencesRepository
import com.example.squadlink.notifications.NotificationHelper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = GeofencingEvent.fromIntent(intent) ?: return@launch
                if (event.hasError()) return@launch

                val repo = UserPreferencesRepository(context)
                val activeGame = repo.activeGameCode.first()
                val isGameMaster = repo.isGameMaster.first()
                if (activeGame.isBlank() || isGameMaster) return@launch

                if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                    NotificationHelper(context).notifyTactical(
                        id = 2001,
                        title = "Fuera del campo",
                        message = "Has salido del area de juego"
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
