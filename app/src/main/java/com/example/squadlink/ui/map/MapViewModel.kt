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

enum class MarkerType { OBJECTIVE, SAFE_ZONE, DANGER, CUSTOM }

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
        _uiState.update { it.copy(field = field) }
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
        val marker = TacticalMarker(
            id = "${type.name.lowercase()}_${System.currentTimeMillis()}",
            label = label,
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
