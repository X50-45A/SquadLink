package com.example.squadlink.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.squadlink.model.AirsoftField
import com.example.squadlink.util.isInsideGeofence
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerMarker(
    val id: String,
    val name: String,
    val role: String,
    val position: LatLng,
    val isOutOfBounds: Boolean = false
)

data class TacticalMarker(
    val id: String,
    val label: String,
    val position: LatLng,
    val type: MarkerType
)

enum class MarkerType { OBJECTIVE, SAFE_ZONE, DANGER, ENEMY, ENEMY_HEAVY, CONTACT, CUSTOM }

data class MapUiState(
    val field: AirsoftField? = null,
    val players: List<PlayerMarker> = emptyList(),
    val tacticalMarkers: List<TacticalMarker> = emptyList(),
    val currentPlayerOutOfBounds: Boolean = false,
    val showOutOfBoundsAlert: Boolean = false,
    val gridVisible: Boolean = true
)

class MapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var objectiveIndex = 0
    private var safeIndex = 0
    private var dangerIndex = 0
    private var enemyIndex = 0
    private var enemyHeavyIndex = 0
    private var contactIndex = 0

    /** Called by Firebase listener when squad positions update */
    fun onPlayersUpdated(players: List<PlayerMarker>) {
        _uiState.update { it.copy(players = players) }
    }

    /** Called by Firebase listener when GM adds/removes tactical markers */
    fun onTacticalMarkersUpdated(markers: List<TacticalMarker>) {
        _uiState.update { it.copy(tacticalMarkers = markers) }
    }

    /** Called when Firebase loads the field data for this game session */
    fun onFieldLoaded(field: AirsoftField) {
        objectiveIndex = 0
        safeIndex = 0
        dangerIndex = 0
        enemyIndex = 0
        enemyHeavyIndex = 0
        contactIndex = 0
        _uiState.update {
            it.copy(
                field = field,
                tacticalMarkers = emptyList(),
                showOutOfBoundsAlert = false,
                currentPlayerOutOfBounds = false
            )
        }
    }

    /** Called every time the GPS updates the current player's location */
    fun onPlayerLocationUpdate(position: LatLng) {
        viewModelScope.launch {
            val field = _uiState.value.field ?: return@launch
            val outOfBounds = !isInsideGeofence(position, field.perimeter)
            _uiState.update {
                it.copy(
                    currentPlayerOutOfBounds = outOfBounds,
                    showOutOfBoundsAlert = outOfBounds
                )
            }
            // TODO: push position + outOfBounds flag to Firebase Realtime DB
        }
    }

    fun addTacticalMarker(type: MarkerType, position: LatLng, label: String) {
        val resolvedLabel = when (type) {
            MarkerType.OBJECTIVE -> {
                val letter = ('A'.code + objectiveIndex).toChar()
                objectiveIndex += 1
                "Bandera $letter"
            }
            MarkerType.SAFE_ZONE -> {
                safeIndex += 1
                "Zona segura $safeIndex"
            }
            MarkerType.DANGER -> {
                dangerIndex += 1
                "Peligro $dangerIndex"
            }
            MarkerType.ENEMY -> {
                enemyIndex += 1
                "Enemigo $enemyIndex"
            }
            MarkerType.ENEMY_HEAVY -> {
                enemyHeavyIndex += 1
                "Alta presencia $enemyHeavyIndex"
            }
            MarkerType.CONTACT -> {
                contactIndex += 1
                "Contacto $contactIndex"
            }
            MarkerType.CUSTOM -> if (label.isBlank()) "Marcador" else label
        }
        val marker = TacticalMarker(
            id = "${type.name.lowercase()}_${System.currentTimeMillis()}",
            label = resolvedLabel,
            position = position,
            type = type
        )
        _uiState.update { it.copy(tacticalMarkers = it.tacticalMarkers + marker) }
    }

    fun dismissOutOfBoundsAlert() {
        _uiState.update { it.copy(showOutOfBoundsAlert = false) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(gridVisible = !it.gridVisible) }
    }
}
