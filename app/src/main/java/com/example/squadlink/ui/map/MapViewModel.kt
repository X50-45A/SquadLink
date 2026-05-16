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
    val type: MarkerType,
    val ownerName: String = "",
    val ownerTeam: String = "",
    val radiusMeters: Double = 0.0
)

data class SafeZoneArea(
    val id: String,
    val name: String,
    val center: LatLng,
    val radius: Float,
    val points: List<LatLng> = emptyList()
)

enum class MarkerType { OBJECTIVE, SAFE_ZONE, DANGER, ENEMY, ENEMY_HEAVY, CONTACT, CUSTOM }

data class DynamicObjective(
    val id: String,
    val type: ObjectiveType,
    val description: String,
    val position: LatLng,
    val targetTeam: String? = null
)

enum class ObjectiveType(val label: String) {
    RECOVERY("Recuperacion"),
    FLAG("Bandera"),
    VIP("VIP"),
    SABOTAGE("Sabotaje"),
    DEFENSE("Defensa"),
    CUSTOM("Otro");

    companion object {
        fun fromWireValue(value: String?): ObjectiveType {
            return entries.firstOrNull { it.name == value } ?: CUSTOM
        }
    }
}

data class MapUiState(
    val field: AirsoftField? = null,
    val players: List<PlayerMarker> = emptyList(),
    val tacticalMarkers: List<TacticalMarker> = emptyList(),
    val safeZoneAreas: List<SafeZoneArea> = emptyList(),
    val dynamicObjectives: List<DynamicObjective> = emptyList(),
    val markerMode: MarkerType? = null,
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

    fun onSafeZoneAreasUpdated(areas: List<SafeZoneArea>) {
        _uiState.update { it.copy(safeZoneAreas = areas) }
    }

    fun onDynamicObjectivesUpdated(objectives: List<DynamicObjective>) {
        _uiState.update { it.copy(dynamicObjectives = objectives) }
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
                dynamicObjectives = emptyList(),
                markerMode = null,
                showOutOfBoundsAlert = false,
                currentPlayerOutOfBounds = false,
                safeZoneAreas = emptyList()
            )
        }
    }

    /** Called every time the GPS updates the current player's location */
    fun onPlayerLocationUpdate(position: LatLng, isGameMaster: Boolean) {
        viewModelScope.launch {
            val field = _uiState.value.field ?: return@launch
            if (isGameMaster) {
                _uiState.update {
                    it.copy(
                        currentPlayerOutOfBounds = false,
                        showOutOfBoundsAlert = false
                    )
                }
                return@launch
            }
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

    fun addTacticalMarker(
        type: MarkerType,
        position: LatLng,
        label: String,
        ownerName: String = "",
        ownerTeam: String = "",
        radiusMeters: Double = 0.0
    ): TacticalMarker {
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
            type = type,
            ownerName = ownerName,
            ownerTeam = ownerTeam,
            radiusMeters = radiusMeters
        )
        _uiState.update { it.copy(tacticalMarkers = it.tacticalMarkers + marker, markerMode = null) }
        return marker
    }

    fun deleteTacticalMarker(markerId: String) {
        _uiState.update {
            it.copy(tacticalMarkers = it.tacticalMarkers.filterNot { marker -> marker.id == markerId })
        }
    }

    fun addSafeZoneArea(
        name: String,
        center: LatLng,
        radius: Float,
        points: List<LatLng>
    ): SafeZoneArea {
        val area = SafeZoneArea(
            id = "safezone_${System.currentTimeMillis()}",
            name = name,
            center = center,
            radius = radius,
            points = points
        )
        _uiState.update { it.copy(safeZoneAreas = it.safeZoneAreas + area) }
        return area
    }

    fun deleteSafeZoneArea(areaId: String) {
        _uiState.update {
            it.copy(safeZoneAreas = it.safeZoneAreas.filterNot { area -> area.id == areaId })
        }
    }

    fun updateSafeZoneArea(area: SafeZoneArea) {
        _uiState.update {
            it.copy(
                safeZoneAreas = it.safeZoneAreas.map { current ->
                    if (current.id == area.id) area else current
                }
            )
        }
    }

    fun addDynamicObjective(
        type: ObjectiveType,
        description: String,
        position: LatLng,
        targetTeam: String?
    ): DynamicObjective {
        val objective = DynamicObjective(
            id = "objective_${System.currentTimeMillis()}",
            type = type,
            description = description.trim(),
            position = position,
            targetTeam = targetTeam?.trim()?.takeIf { it.isNotBlank() }
        )
        _uiState.update { it.copy(dynamicObjectives = it.dynamicObjectives + objective) }
        return objective
    }

    fun updateDynamicObjective(objective: DynamicObjective) {
        _uiState.update {
            it.copy(
                dynamicObjectives = it.dynamicObjectives.map { current ->
                    if (current.id == objective.id) objective else current
                }
            )
        }
    }

    fun deleteDynamicObjective(objectiveId: String) {
        _uiState.update {
            it.copy(dynamicObjectives = it.dynamicObjectives.filterNot { objective -> objective.id == objectiveId })
        }
    }

    fun setMarkerMode(type: MarkerType?) {
        _uiState.update { it.copy(markerMode = type) }
    }

    fun dismissOutOfBoundsAlert() {
        _uiState.update { it.copy(showOutOfBoundsAlert = false) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(gridVisible = !it.gridVisible) }
    }
}
