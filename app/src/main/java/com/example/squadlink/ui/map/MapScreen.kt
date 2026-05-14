package com.example.squadlink.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.squadlink.model.AirsoftField
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import com.example.squadlink.ui.session.GameSessionViewModel
import androidx.core.graphics.createBitmap
import com.example.squadlink.R
import com.example.squadlink.data.FieldRepository
import com.example.squadlink.data.FirebaseAccountRepository
import com.example.squadlink.data.FirebaseGameMapRepository
import com.example.squadlink.data.UserPreferencesRepository
import com.example.squadlink.geofence.GeofenceManager
import com.example.squadlink.notifications.NotificationHelper
import kotlinx.coroutines.launch

private val GridColor = Color(0x2200FF41)
private val FieldFill = Color(0x1A4CAF50)
private val FieldStroke = Color(0xFF76FF03)
private val OutOfBoundsRed = Color(0xCCF44336)
private val NatoBlue = Color(0xFF2979FF)
private val NatoRed = Color(0xFFD32F2F)
private val NatoDarkRed = Color(0xFFB71C1C)
private val NatoYellow = Color(0xFFFFC400)
private val NatoGreen = Color(0xFF66BB6A)
private val NatoDark = Color(0xFF101810)

@Composable
fun MapScreen(
    fieldVm: FieldSelectionViewModel,
    sessionVm: GameSessionViewModel,
    mapVm: MapViewModel = viewModel()
) {
    val mapState by mapVm.uiState.collectAsState()
    val selectionState by fieldVm.uiState.collectAsState()
    val sessionState by sessionVm.uiState.collectAsState()
    val selectedField = selectionState.selectedField
    val field = mapState.field
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    val preferencesRepo = remember(ctx) { UserPreferencesRepository(ctx) }
    val accountRepo = remember(ctx) { FirebaseAccountRepository(preferencesRepo) }
    val gameMapRepo = remember { FirebaseGameMapRepository() }
    val currentProfile by accountRepo.observeCurrentProfile().collectAsState(initial = null)
    val activeGame by gameMapRepo
        .observeGame(sessionState.activeGameCode)
        .collectAsState(initial = null)
    val teamAliases = remember(currentProfile?.squadName, currentProfile?.squadCode) {
        listOf(currentProfile?.squadName, currentProfile?.squadCode)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .map { it.lowercase() }
    }
    val currentTeamLabel = remember(currentProfile?.squadCode, currentProfile?.squadName) {
        currentProfile?.squadCode?.takeIf { it.isNotBlank() }
            ?: currentProfile?.squadName.orEmpty()
    }

    val mapStyle = remember {
        runCatching {
            MapStyleOptions.loadRawResourceStyle(ctx, R.raw.map_style_tactical)
        }.getOrNull()
    }

    val locationPermissionState = rememberLocationPermissionState()
    val backgroundLocationPermission = rememberBackgroundLocationPermissionState()
    val notificationPermission = rememberNotificationPermissionState()
    val geofenceManager = remember(ctx) { GeofenceManager(ctx) }
    val notificationHelper = remember(ctx) { NotificationHelper(ctx) }
    var requestedLocationPermission by rememberSaveable { mutableStateOf(false) }
    var showFieldPicker by rememberSaveable { mutableStateOf(field == null) }
    var mapLoaded by remember { mutableStateOf(false) }
    var showMapError by remember { mutableStateOf(false) }
    var customMarkerLabel by remember { mutableStateOf("") }
    var objectiveMenuPosition by remember { mutableStateOf<LatLng?>(null) }
    var selectedObjective by remember { mutableStateOf<DynamicObjective?>(null) }
    var selectedTacticalMarker by remember { mutableStateOf<TacticalMarker?>(null) }
    var objectiveEditorPosition by remember { mutableStateOf<LatLng?>(null) }
    var objectiveEditorTarget by remember { mutableStateOf<DynamicObjective?>(null) }
    var safeZonePosition by remember { mutableStateOf<LatLng?>(null) }
    val mapLocked = sessionState.activeGameCode.isNotBlank()
    val markerMode = mapState.markerMode
    var markerCount by remember { mutableStateOf(0) }
    var knownObjectiveIds by remember(sessionState.activeGameCode) { mutableStateOf<Set<String>>(emptySet()) }
    var objectiveNotificationsReady by remember(sessionState.activeGameCode) { mutableStateOf(false) }
    val visibleTacticalMarkers = remember(
        mapState.tacticalMarkers,
        sessionState.isGameMaster,
        teamAliases
    ) {
        mapState.tacticalMarkers.filter { marker ->
            sessionState.isGameMaster || marker.ownerTeam.isBlank() || marker.ownerTeam.lowercase() in teamAliases
        }
    }
    val visibleDynamicObjectives = remember(
        mapState.dynamicObjectives,
        sessionState.isGameMaster,
        teamAliases
    ) {
        mapState.dynamicObjectives.filter { objective ->
            sessionState.isGameMaster || objective.targetTeam.isNullOrBlank() ||
                objective.targetTeam.lowercase() in teamAliases
        }
    }
    val displayMapState = mapState.copy(
        tacticalMarkers = visibleTacticalMarkers,
        dynamicObjectives = visibleDynamicObjectives
    )

    LaunchedEffect(locationPermissionState.hasPermission, requestedLocationPermission) {
        if (!locationPermissionState.hasPermission && !requestedLocationPermission) {
            requestedLocationPermission = true
            locationPermissionState.requestPermission()
        }
    }

    LaunchedEffect(sessionState.activeGameCode) {
        val activeGameCode = sessionState.activeGameCode
        if (activeGameCode.isBlank()) {
            mapVm.onTacticalMarkersUpdated(emptyList())
            return@LaunchedEffect
        }
        gameMapRepo.observeTacticalMarkers(activeGameCode).collect { markers ->
            mapVm.onTacticalMarkersUpdated(markers)
        }
    }

    LaunchedEffect(sessionState.activeGameCode) {
        val activeGameCode = sessionState.activeGameCode
        if (activeGameCode.isBlank()) {
            mapVm.onDynamicObjectivesUpdated(emptyList())
            return@LaunchedEffect
        }
        gameMapRepo.observeDynamicObjectives(activeGameCode).collect { objectives ->
            mapVm.onDynamicObjectivesUpdated(objectives)
        }
    }

    val currentFieldId = field?.id
    val selectedFieldId = selectedField?.id
    val activeGameFieldId = activeGame?.fieldId
    LaunchedEffect(activeGameFieldId, currentFieldId) {
        val gameFieldId = activeGameFieldId ?: return@LaunchedEffect
        if (gameFieldId != currentFieldId) {
            FieldRepository.fields.firstOrNull { it.id == gameFieldId }?.let { gameField ->
                mapVm.onFieldLoaded(gameField)
                showFieldPicker = false
            }
        }
    }

    LaunchedEffect(selectedFieldId, currentFieldId) {
        if (selectedField != null && selectedField.id != currentFieldId) {
            mapVm.onFieldLoaded(selectedField)
            showFieldPicker = false
        }
    }

    LocationUpdatesEffect(
        enabled = locationPermissionState.hasPermission,
        onLocation = { location ->
            mapVm.onPlayerLocationUpdate(
                LatLng(location.latitude, location.longitude),
                sessionState.isGameMaster
            )
        }
    )

    val shouldArmGeofence = sessionState.activeGameCode.isNotBlank() &&
        !sessionState.isGameMaster &&
        field != null &&
        locationPermissionState.hasPermission &&
        backgroundLocationPermission.hasPermission

    LaunchedEffect(shouldArmGeofence, field?.id) {
        if (shouldArmGeofence) {
            field?.let { geofenceManager.registerFieldGeofence(it) }
        } else {
            geofenceManager.clearGeofences()
        }
    }

    LaunchedEffect(mapState.tacticalMarkers.size, sessionState.activeGameCode) {
        if (sessionState.activeGameCode.isNotBlank()) {
            if (mapState.tacticalMarkers.size > markerCount) {
                val last = mapState.tacticalMarkers.last()
                notificationHelper.notifyTactical(
                    id = 3000 + mapState.tacticalMarkers.size,
                    title = "Nuevo marcador",
                    message = last.label
                )
            }
        }
        markerCount = mapState.tacticalMarkers.size
    }

    LaunchedEffect(visibleDynamicObjectives, sessionState.activeGameCode, sessionState.isGameMaster) {
        val visibleIds = visibleDynamicObjectives.map { it.id }.toSet()
        if (sessionState.activeGameCode.isNotBlank() && !sessionState.isGameMaster) {
            if (objectiveNotificationsReady) {
                visibleDynamicObjectives
                    .filter { it.id !in knownObjectiveIds }
                    .forEachIndexed { index, objective ->
                        notificationHelper.notifyTactical(
                            id = 4000 + index + visibleDynamicObjectives.size,
                            title = "Nuevo objetivo: ${objective.type.label}",
                            message = objective.description.ifBlank { "Revisa el mapa tactico." }
                        )
                    }
            } else {
                objectiveNotificationsReady = true
            }
        }
        knownObjectiveIds = visibleIds
    }

    if (field == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            FieldSelectionScreen(
                state = selectionState,
                onQueryChange = fieldVm::updateQuery,
                onSelectField = fieldVm::selectField
            )
            if (!locationPermissionState.hasPermission) {
                LocationPermissionCard(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    onRequestPermission = locationPermissionState.requestPermission
                )
            }
        }
        return
    }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(field.center, field.defaultZoom)
    }

    LaunchedEffect(mapLoaded) {
        if (!mapLoaded) {
            delay(6000L)
            if (!mapLoaded) showMapError = true
        } else {
            showMapError = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            LandscapeMapLayout(
                state = displayMapState,
                cameraState = cameraState,
                mapStyle = mapStyle,
                field = field,
                hasLocationPermission = locationPermissionState.hasPermission,
                mapLoaded = mapLoaded,
                onToggleGrid = mapVm::toggleGrid,
                onDismissAlert = mapVm::dismissOutOfBoundsAlert,
                onMapLoaded = { mapLoaded = true },
                onMapClick = { latLng ->
                    val mode = markerMode ?: return@LandscapeMapLayout
                    if (mode == MarkerType.SAFE_ZONE) {
                        safeZonePosition = latLng
                        return@LandscapeMapLayout
                    }
                    val label = markerLabelFor(mode, customMarkerLabel)
                    val marker = mapVm.addTacticalMarker(
                        type = mode,
                        position = latLng,
                        label = label,
                        ownerName = sessionState.suggestedPlayerName,
                        ownerTeam = if (sessionState.isGameMaster) "" else currentTeamLabel
                    )
                    mapVm.setMarkerMode(null)
                    if (sessionState.activeGameCode.isNotBlank()) {
                        scope.launch {
                            gameMapRepo.upsertTacticalMarker(sessionState.activeGameCode, marker)
                        }
                    }
                },
                onMapLongClick = { latLng ->
                    if (sessionState.activeGameCode.isNotBlank()) {
                        objectiveMenuPosition = latLng
                        selectedObjective = null
                        selectedTacticalMarker = null
                    }
                },
                onTacticalMarkerClick = { marker ->
                    selectedTacticalMarker = marker
                    selectedObjective = null
                    objectiveMenuPosition = null
                },
                onDynamicObjectiveClick = { objective ->
                    selectedObjective = objective
                    selectedTacticalMarker = null
                    objectiveMenuPosition = null
                }
            )
        } else {
            PortraitMapLayout(
                state = displayMapState,
                cameraState = cameraState,
                mapStyle = mapStyle,
                field = field,
                hasLocationPermission = locationPermissionState.hasPermission,
                mapLoaded = mapLoaded,
                onToggleGrid = mapVm::toggleGrid,
                onDismissAlert = mapVm::dismissOutOfBoundsAlert,
                onMapLoaded = { mapLoaded = true },
                onMapClick = { latLng ->
                    val mode = markerMode ?: return@PortraitMapLayout
                    if (mode == MarkerType.SAFE_ZONE) {
                        safeZonePosition = latLng
                        return@PortraitMapLayout
                    }
                    val label = markerLabelFor(mode, customMarkerLabel)
                    val marker = mapVm.addTacticalMarker(
                        type = mode,
                        position = latLng,
                        label = label,
                        ownerName = sessionState.suggestedPlayerName,
                        ownerTeam = if (sessionState.isGameMaster) "" else currentTeamLabel
                    )
                    mapVm.setMarkerMode(null)
                    if (sessionState.activeGameCode.isNotBlank()) {
                        scope.launch {
                            gameMapRepo.upsertTacticalMarker(sessionState.activeGameCode, marker)
                        }
                    }
                },
                onMapLongClick = { latLng ->
                    if (sessionState.activeGameCode.isNotBlank()) {
                        objectiveMenuPosition = latLng
                        selectedObjective = null
                        selectedTacticalMarker = null
                    }
                },
                onTacticalMarkerClick = { marker ->
                    selectedTacticalMarker = marker
                    selectedObjective = null
                    objectiveMenuPosition = null
                },
                onDynamicObjectiveClick = { objective ->
                    selectedObjective = objective
                    selectedTacticalMarker = null
                    objectiveMenuPosition = null
                }
            )
        }

        ChangeFieldButton(
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            onClick = { showFieldPicker = true },
            enabled = !mapLocked
        )

        if (showFieldPicker && !mapLocked) {
            FieldSelectionPanel(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 12.dp, end = 12.dp),
                state = selectionState,
                onQueryChange = fieldVm::updateQuery,
                onSelectField = {
                    fieldVm.selectField(it)
                    showFieldPicker = false
                },
                onClose = { showFieldPicker = false }
            )
        }

        if (sessionState.activeGameCode.isNotBlank() && !sessionState.isGameMaster && !backgroundLocationPermission.hasPermission) {
            BackgroundLocationPermissionCard(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                onRequestPermission = backgroundLocationPermission.requestPermission
            )
        }

        if (sessionState.activeGameCode.isNotBlank() && !notificationPermission.hasPermission) {
            NotificationPermissionCard(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                onRequestPermission = notificationPermission.requestPermission
            )
        }

        if (!locationPermissionState.hasPermission) {
            LocationPermissionCard(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                onRequestPermission = locationPermissionState.requestPermission
            )
        }

        if (markerMode != null) {
            MarkerPlacementHint(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
                label = markerLabelFor(markerMode!!, customMarkerLabel)
            )
        }

        val tools = markerToolsForRole(sessionState.isGameMaster)
        if (tools.isNotEmpty()) {
            MarkerToolsPanel(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                tools = tools,
                activeMode = markerMode,
                customLabel = customMarkerLabel,
                onCustomLabelChange = { customMarkerLabel = it },
                onSelectMode = { mapVm.setMarkerMode(it) }
            )
        }

        objectiveMenuPosition?.let { position ->
            ObjectiveMapActionCard(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                isGameMaster = sessionState.isGameMaster,
                onAddObjective = {
                    objectiveEditorPosition = position
                    objectiveEditorTarget = null
                    objectiveMenuPosition = null
                },
                onAddMarker = {
                    mapVm.setMarkerMode(com.example.squadlink.ui.map.MarkerType.CUSTOM)

                    val marker = mapVm.addTacticalMarker(
                        type = com.example.squadlink.ui.map.MarkerType.CUSTOM,
                        position = position,
                        label = markerLabelFor(com.example.squadlink.ui.map.MarkerType.CUSTOM, customMarkerLabel),
                        ownerName = sessionState.suggestedPlayerName,
                        ownerTeam = if (sessionState.isGameMaster) "" else currentTeamLabel
                    )
                    if (sessionState.activeGameCode.isNotBlank()) {
                        scope.launch {
                            gameMapRepo.upsertTacticalMarker(sessionState.activeGameCode, marker)
                        }
                    }
                    objectiveMenuPosition = null
                },
                onDismiss = { objectiveMenuPosition = null }
            )
        }

        selectedObjective?.let { objective ->
            if (sessionState.isGameMaster) {
                DynamicObjectiveActionCard(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    objective = objective,
                    onEdit = {
                        objectiveEditorTarget = objective
                        objectiveEditorPosition = objective.position
                        selectedObjective = null
                    },
                    onDelete = {
                        mapVm.deleteDynamicObjective(objective.id)
                        if (sessionState.activeGameCode.isNotBlank()) {
                            scope.launch {
                                gameMapRepo.deleteDynamicObjective(sessionState.activeGameCode, objective.id)
                            }
                        }
                        selectedObjective = null
                    },
                    onDismiss = { selectedObjective = null }
                )
            }
        }

        selectedTacticalMarker?.let { marker ->
            TacticalMarkerActionCard(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                marker = marker,
                canDelete = canManageTacticalMarker(marker, sessionState.isGameMaster, teamAliases),
                onDelete = {
                    mapVm.deleteTacticalMarker(marker.id)
                    if (sessionState.activeGameCode.isNotBlank()) {
                        scope.launch {
                            gameMapRepo.deleteTacticalMarker(sessionState.activeGameCode, marker.id)
                        }
                    }
                    selectedTacticalMarker = null
                },
                onDismiss = { selectedTacticalMarker = null }
            )
        }

        if (objectiveEditorPosition != null) {
            ObjectiveEditorDialog(
                objective = objectiveEditorTarget,
                position = objectiveEditorPosition!!,
                onDismiss = {
                    objectiveEditorPosition = null
                    objectiveEditorTarget = null
                },
                onSave = { type, description, targetTeam ->
                    val existing = objectiveEditorTarget
                    val objective = if (existing == null) {
                        mapVm.addDynamicObjective(
                            type = type,
                            description = description,
                            position = objectiveEditorPosition!!,
                            targetTeam = targetTeam
                        )
                    } else {
                        existing.copy(
                            type = type,
                            description = description.trim(),
                            targetTeam = targetTeam?.trim()?.takeIf { it.isNotBlank() }
                        ).also(mapVm::updateDynamicObjective)
                    }
                    if (sessionState.activeGameCode.isNotBlank()) {
                        scope.launch {
                            gameMapRepo.upsertDynamicObjective(sessionState.activeGameCode, objective)
                        }
                    }
                    objectiveEditorPosition = null
                    objectiveEditorTarget = null
                }
            )
        }

        safeZonePosition?.let { position ->
            SafeZoneEditorDialog(
                onDismiss = { safeZonePosition = null },
                onSave = { label, radiusMeters ->
                    val marker = mapVm.addTacticalMarker(
                        type = MarkerType.SAFE_ZONE,
                        position = position,
                        label = label,
                        ownerName = sessionState.suggestedPlayerName,
                        ownerTeam = "",
                        radiusMeters = radiusMeters
                    )
                    mapVm.setMarkerMode(null)
                    if (sessionState.activeGameCode.isNotBlank()) {
                        scope.launch {
                            gameMapRepo.upsertTacticalMarker(sessionState.activeGameCode, marker)
                        }
                    }
                    safeZonePosition = null
                }
            )
        }

        if (showMapError) {
            MapLoadErrorCard(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp, start = 16.dp, end = 16.dp),
                onRetry = { mapLoaded = false; showMapError = false }
            )
        }
    }
}

@Composable
private fun FieldSelectionScreen(
    state: FieldSelectionUiState,
    onQueryChange: (String) -> Unit,
    onSelectField: (AirsoftField) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        FieldSelectionPanel(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            state = state,
            onQueryChange = onQueryChange,
            onSelectField = onSelectField,
            onClose = null
        )
    }
}

@Composable
private fun FieldSelectionPanel(
    modifier: Modifier,
    state: FieldSelectionUiState,
    onQueryChange: (String) -> Unit,
    onSelectField: (AirsoftField) -> Unit,
    onClose: (() -> Unit)?
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC101810)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Selecciona un campo",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text("Buscar campo") },
                modifier = Modifier.fillMaxWidth()
            )

            if (state.fields.isEmpty()) {
                Text(
                    text = "No hay campos que coincidan",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.fields, key = { it.id }) { field ->
                        FieldListItem(field = field, onSelect = onSelectField)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldListItem(field: AirsoftField, onSelect: (AirsoftField) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1210))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                // Usamos locationName si existiera, o name
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Button(
                onClick = { onSelect(field) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Cargar")
            }
        }
    }
}

@Composable
private fun PortraitMapLayout(
    state: MapUiState,
    cameraState: CameraPositionState,
    mapStyle: MapStyleOptions?,
    field: AirsoftField,
    hasLocationPermission: Boolean,
    mapLoaded: Boolean,
    onToggleGrid: () -> Unit,
    onDismissAlert: () -> Unit,
    onMapLoaded: () -> Unit,
    onMapClick: (LatLng) -> Unit,
    onMapLongClick: (LatLng) -> Unit,
    onTacticalMarkerClick: (TacticalMarker) -> Unit,
    onDynamicObjectiveClick: (DynamicObjective) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TacticalGoogleMap(
            modifier = Modifier.fillMaxSize(),
            state = state,
            cameraState = cameraState,
            mapStyle = mapStyle,
            field = field,
            hasLocationPermission = hasLocationPermission,
            mapLoaded = mapLoaded,
            onMapLoaded = onMapLoaded,
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            onTacticalMarkerClick = onTacticalMarkerClick,
            onDynamicObjectiveClick = onDynamicObjectiveClick
        )
        if (state.gridVisible) Canvas(Modifier.fillMaxSize()) { drawTacticalGrid(this) }
        MapHud(
            Modifier.align(Alignment.TopStart).padding(12.dp),
            field.name,
            state.players.count { !it.isOutOfBounds },
            state.players.size
        )
        GridToggleFab(Modifier.align(Alignment.BottomEnd).padding(16.dp), state.gridVisible, onToggleGrid)
        androidx.compose.animation.AnimatedVisibility(
            visible = state.showOutOfBoundsAlert,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) { OutOfBoundsAlert(onDismissAlert) }
    }
}

@Composable
private fun LandscapeMapLayout(
    state: MapUiState,
    cameraState: CameraPositionState,
    mapStyle: MapStyleOptions?,
    field: AirsoftField,
    hasLocationPermission: Boolean,
    mapLoaded: Boolean,
    onToggleGrid: () -> Unit,
    onDismissAlert: () -> Unit,
    onMapLoaded: () -> Unit,
    onMapClick: (LatLng) -> Unit,
    onMapLongClick: (LatLng) -> Unit,
    onTacticalMarkerClick: (TacticalMarker) -> Unit,
    onDynamicObjectiveClick: (DynamicObjective) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TacticalGoogleMap(
                modifier = Modifier.fillMaxSize(),
                state = state,
                cameraState = cameraState,
                mapStyle = mapStyle,
                field = field,
                hasLocationPermission = hasLocationPermission,
                mapLoaded = mapLoaded,
                onMapLoaded = onMapLoaded,
                onMapClick = onMapClick,
                onMapLongClick = onMapLongClick,
                onTacticalMarkerClick = onTacticalMarkerClick,
                onDynamicObjectiveClick = onDynamicObjectiveClick
            )
            if (state.gridVisible) Canvas(Modifier.fillMaxSize()) { drawTacticalGrid(this) }
            androidx.compose.animation.AnimatedVisibility(
                visible = state.showOutOfBoundsAlert,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) { OutOfBoundsAlert(onDismissAlert) }
        }

        // Side panel in landscape -- maximises map space
        Column(
            modifier = Modifier.width(160.dp).fillMaxHeight().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MapHud(
                Modifier.fillMaxWidth(),
                field.name,
                state.players.count { !it.isOutOfBounds },
                state.players.size
            )
            if (state.showOutOfBoundsAlert) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xCCB71C1C))) {
                    Text(
                        "! FUERA DEL CAMPO",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            GridToggleFab(Modifier.size(44.dp), state.gridVisible, onToggleGrid)
        }
    }
}

@Composable
private fun TacticalGoogleMap(
    modifier: Modifier,
    state: MapUiState,
    cameraState: CameraPositionState,
    mapStyle: MapStyleOptions?,
    field: AirsoftField,
    hasLocationPermission: Boolean,
    mapLoaded: Boolean,
    onMapLoaded: () -> Unit,
    onMapClick: (LatLng) -> Unit,
    onMapLongClick: (LatLng) -> Unit,
    onTacticalMarkerClick: (TacticalMarker) -> Unit,
    onDynamicObjectiveClick: (DynamicObjective) -> Unit
) {
    val tacticalIcons: Map<String, BitmapDescriptor> =
        if (mapLoaded) rememberTacticalMarkerIcons() else emptyMap()
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(
            mapType = MapType.SATELLITE,
            mapStyleOptions = mapStyle,
            isMyLocationEnabled = hasLocationPermission
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = hasLocationPermission,
            mapToolbarEnabled = false
        ),
        onMapLoaded = onMapLoaded,
        onMapClick = onMapClick,
        onMapLongClick = onMapLongClick
    ) {
        field.perimeter.forEach { poly ->
            Polygon(
                points = poly,
                fillColor = FieldFill,
                strokeColor = FieldStroke,
                strokeWidth = 3f
            )
        }

        state.players.forEach { player ->
            val iconKey = if (player.isOutOfBounds) "player_out" else "player"
            Marker(
                state = MarkerState(position = player.position),
                title = player.name,
                icon = tacticalIcons[iconKey]
            )
        }

        state.tacticalMarkers
            .filter { it.type == MarkerType.SAFE_ZONE && it.radiusMeters > 0.0 }
            .forEach { marker ->
                Circle(
                    center = marker.position,
                    radius = marker.radiusMeters,
                    fillColor = NatoGreen.copy(alpha = 0.18f),
                    strokeColor = NatoGreen,
                    strokeWidth = 4f
                )
            }

        state.tacticalMarkers.forEach { marker ->
            Marker(
                state = MarkerState(position = marker.position),
                title = marker.label,
                icon = tacticalIcons[marker.type.name.lowercase()],
                onClick = {
                    onTacticalMarkerClick(marker)
                    true
                },
                onInfoWindowLongClick = { onTacticalMarkerClick(marker) }
            )
        }

        state.dynamicObjectives.forEach { objective ->
            Marker(
                state = MarkerState(position = objective.position),
                title = objectiveTitle(objective),
                snippet = objective.description,
                icon = tacticalIcons["dynamic_objective"],
                onClick = {
                    onDynamicObjectiveClick(objective)
                    true
                },
                onInfoWindowLongClick = { onDynamicObjectiveClick(objective) }
            )
        }
    }
}

@Composable
private fun MapHud(modifier: Modifier, fieldName: String, playersIn: Int, playersTotal: Int) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC101810)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                fieldName,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).padding(1.dp)) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFF00FF41)) }
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "SQUAD: $playersIn/$playersTotal",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00FF41)
                )
            }
        }
    }
}

@Composable
private fun GridToggleFab(modifier: Modifier, active: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (active) Color(0xFF2E7D32) else Color(0xCC101810),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(Icons.Default.GridOn, contentDescription = "Alternar cuadr\u00edcula")
    }
}

@Composable
private fun ChangeFieldButton(modifier: Modifier, onClick: () -> Unit, enabled: Boolean) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xCC101810),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text("CAMBIAR CAMPO", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OutOfBoundsAlert(onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OutOfBoundsRed),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(16.dp).fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "\u00a1FUERA DE L\u00cdMITES!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    "Est\u00e1s fuera del \u00e1rea de juego. Regresa al campo inmediatamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Descartar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MapLoadErrorCard(modifier: Modifier, onRetry: () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCCB71C1C))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Error al cargar el mapa", color = Color.White)
            TextButton(onClick = onRetry) {
                Text("REINTENTAR", color = Color.White)
            }
        }
    }
}

@Composable
private fun ObjectiveMapActionCard(
    modifier: Modifier,
    isGameMaster: Boolean,
    onAddObjective: () -> Unit,
    onAddMarker: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101810)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Punto seleccionado",
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            if (isGameMaster) {
                TextButton(onClick = onAddObjective) {
                    Text("Objetivo dinamico")
                }
            }
            TextButton(onClick = onAddMarker) {
                Text(if (isGameMaster) "Marcador" else "Añadir marcador")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun DynamicObjectiveActionCard(
    modifier: Modifier,
    objective: DynamicObjective,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101810)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(objectiveTitle(objective), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        objective.description.ifBlank { "Sin descripcion" },
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Text("Modificar objetivo dinamico")
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            }
        }
    }
}

@Composable
private fun TacticalMarkerActionCard(
    modifier: Modifier,
    marker: TacticalMarker,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101810)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(marker.label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                if (marker.ownerTeam.isNotBlank()) {
                    Text(
                        "Equipo: ${marker.ownerTeam}",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (canDelete) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ObjectiveEditorDialog(
    objective: DynamicObjective?,
    position: LatLng,
    onDismiss: () -> Unit,
    onSave: (ObjectiveType, String, String?) -> Unit
) {
    var type by remember(objective?.id) { mutableStateOf(objective?.type ?: ObjectiveType.FLAG) }
    var description by remember(objective?.id) { mutableStateOf(objective?.description.orEmpty()) }
    var targetOnlyOneTeam by remember(objective?.id) { mutableStateOf(!objective?.targetTeam.isNullOrBlank()) }
    var targetTeam by remember(objective?.id) { mutableStateOf(objective?.targetTeam.orEmpty()) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (objective == null) "Nuevo objetivo dinamico" else "Modificar objetivo")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.label)
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        ObjectiveType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    type = option
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripcion") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Solo a un equipo", modifier = Modifier.weight(1f))
                    Switch(
                        checked = targetOnlyOneTeam,
                        onCheckedChange = { checked ->
                            targetOnlyOneTeam = checked
                            if (!checked) targetTeam = ""
                        }
                    )
                }

                if (targetOnlyOneTeam) {
                    OutlinedTextField(
                        value = targetTeam,
                        onValueChange = { targetTeam = it },
                        label = { Text("Nombre o codigo del equipo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    "Posicion: %.5f, %.5f".format(position.latitude, position.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        type,
                        description,
                        if (targetOnlyOneTeam) targetTeam else null
                    )
                },
                enabled = description.isNotBlank() && (!targetOnlyOneTeam || targetTeam.isNotBlank())
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun SafeZoneEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var label by remember { mutableStateOf("Zona segura") }
    var radiusText by remember { mutableStateOf("25") }
    val radius = radiusText.toDoubleOrNull()?.coerceIn(5.0, 250.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zona segura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { value ->
                        radiusText = value.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Radio en metros") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Se dibujara un limite circular visible para todos los jugadores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, radius ?: 25.0) },
                enabled = label.isNotBlank() && radius != null
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun MarkerPlacementHint(modifier: Modifier, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC000000))
    ) {
        Text(
            "Toca el mapa para colocar: $label",
            modifier = Modifier.padding(8.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun MarkerToolsPanel(
    modifier: Modifier,
    tools: List<com.example.squadlink.ui.map.MarkerType>,
    activeMode: com.example.squadlink.ui.map.MarkerType?,
    customLabel: String,
    onCustomLabelChange: (String) -> Unit,
    onSelectMode: (com.example.squadlink.ui.map.MarkerType?) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (expanded) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC101810)),
                modifier = Modifier.width(200.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Herramientas T\u00e1cticas", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    tools.forEach { type ->
                        val isSelected = activeMode == type
                        TextButton(
                            onClick = { onSelectMode(if (isSelected) null else type) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isSelected) Color(0xFF76FF03) else Color.White
                            )
                        ) {
                            Text(markerToolLabel(type), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (activeMode == com.example.squadlink.ui.map.MarkerType.CUSTOM) {
                        OutlinedTextField(
                            value = customLabel,
                            onValueChange = onCustomLabelChange,
                            label = { Text("Etiqueta", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = Color(0xFF1B5E20),
            contentColor = Color.White
        ) {
            Icon(
                if (expanded) Icons.Default.Close else Icons.Default.Warning,
                contentDescription = "Herramientas"
            )
        }
    }
}

private fun drawTacticalGrid(scope: DrawScope) {
    val size = scope.size
    val step = 100f
    // Simple grid lines for visual effect
    for (x in 0..(size.width / step).toInt()) {
        scope.drawLine(GridColor, Offset(x * step, 0f), Offset(x * step, size.height))
    }
    for (y in 0..(size.height / step).toInt()) {
        scope.drawLine(GridColor, Offset(0f, y * step), Offset(size.width, y * step))
    }
}

private enum class NatoShape { RECTANGLE, DIAMOND, TRIANGLE, CIRCLE }

@Composable
private fun rememberTacticalMarkerIcons(): Map<String, BitmapDescriptor> {
    return remember {
        mapOf(
            "player" to createPlayerMarkerIcon(Color(0xFF00FF41)),
            "player_out" to createPlayerMarkerIcon(Color(0xFFF44336)),
            "objective" to createNatoMarkerIcon(NatoShape.RECTANGLE, NatoBlue, NatoDark, "OBJ"),
            "safe_zone" to createNatoMarkerIcon(NatoShape.RECTANGLE, NatoGreen, NatoDark, "SZ"),
            "danger" to createNatoMarkerIcon(NatoShape.TRIANGLE, NatoRed, NatoDark, "DNG"),
            "enemy" to createNatoMarkerIcon(NatoShape.DIAMOND, NatoRed, NatoDark, "EN"),
            "enemy_heavy" to createNatoMarkerIcon(NatoShape.DIAMOND, NatoDarkRed, NatoDark, "HV"),
            "contact" to createNatoMarkerIcon(NatoShape.TRIANGLE, NatoYellow, NatoDark, "CT"),
            "dynamic_objective" to createNatoMarkerIcon(NatoShape.CIRCLE, NatoBlue, NatoDark, "DYN"),
            "custom" to createNatoMarkerIcon(NatoShape.CIRCLE, Color.White, NatoDark, "MK")
        )
    }
}

private fun createPlayerMarkerIcon(color: Color): BitmapDescriptor {
    val size = 64
    val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color.toArgb()
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val path = Path().apply {
        moveTo(size / 2f, 0f)
        lineTo(size.toFloat(), size.toFloat())
        lineTo(size / 2f, size * 0.75f)
        lineTo(0f, size.toFloat())
        close()
    }
    canvas.drawPath(path, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun createNatoMarkerIcon(
    shape: NatoShape,
    stroke: Color,
    fill: Color,
    label: String
): BitmapDescriptor {
    val size = 72
    val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val padding = 10f
    val rect = RectF(padding, padding, size - padding, size - padding)

    val path = Path().apply {
        when (shape) {
            NatoShape.RECTANGLE -> addRect(rect, Path.Direction.CW)
            NatoShape.DIAMOND -> {
                moveTo(size / 2f, rect.top)
                lineTo(rect.right, size / 2f)
                lineTo(size / 2f, rect.bottom)
                lineTo(rect.left, size / 2f)
                close()
            }
            NatoShape.TRIANGLE -> {
                moveTo(size / 2f, rect.top)
                lineTo(rect.right, rect.bottom)
                lineTo(rect.left, rect.bottom)
                close()
            }
            NatoShape.CIRCLE -> addOval(rect, Path.Direction.CW)
        }
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fill.toArgb()
    }
    canvas.drawPath(path, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = stroke.toArgb()
    }
    canvas.drawPath(path, strokePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = stroke.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText(label, size / 2f, size / 2f + 6f, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun markerLabelFor(mode: com.example.squadlink.ui.map.MarkerType, custom: String): String = when (mode) {
    com.example.squadlink.ui.map.MarkerType.OBJECTIVE -> "Bandera"
    com.example.squadlink.ui.map.MarkerType.SAFE_ZONE -> "Zona segura"
    com.example.squadlink.ui.map.MarkerType.DANGER -> "Peligro"
    com.example.squadlink.ui.map.MarkerType.ENEMY -> "Enemigo"
    com.example.squadlink.ui.map.MarkerType.ENEMY_HEAVY -> "Alta presencia"
    com.example.squadlink.ui.map.MarkerType.CONTACT -> "Contacto"
    com.example.squadlink.ui.map.MarkerType.CUSTOM -> custom.ifBlank { "Marcador" }
}

private fun markerToolLabel(mode: com.example.squadlink.ui.map.MarkerType): String = when (mode) {
    com.example.squadlink.ui.map.MarkerType.OBJECTIVE -> "Objetivo"
    com.example.squadlink.ui.map.MarkerType.SAFE_ZONE -> "Zona segura"
    com.example.squadlink.ui.map.MarkerType.DANGER -> "Peligro"
    com.example.squadlink.ui.map.MarkerType.ENEMY -> "Enemigo"
    com.example.squadlink.ui.map.MarkerType.ENEMY_HEAVY -> "Alta presencia"
    com.example.squadlink.ui.map.MarkerType.CONTACT -> "Contacto"
    com.example.squadlink.ui.map.MarkerType.CUSTOM -> "Personal"
}

private fun objectiveTitle(objective: DynamicObjective): String {
    val target = objective.targetTeam?.let { " - $it" } ?: " - todos"
    return "${objective.type.label}$target"
}

private fun canManageTacticalMarker(
    marker: TacticalMarker,
    isGameMaster: Boolean,
    teamAliases: List<String>
): Boolean {
    return isGameMaster ||
        (marker.ownerTeam.isNotBlank() && marker.ownerTeam.lowercase() in teamAliases) ||
        (marker.ownerTeam.isBlank() && teamAliases.isEmpty())
}

private fun markerToolsForRole(isGm: Boolean): List<com.example.squadlink.ui.map.MarkerType> {
    return if (isGm) {
        listOf(
            com.example.squadlink.ui.map.MarkerType.OBJECTIVE,
            com.example.squadlink.ui.map.MarkerType.SAFE_ZONE,
            com.example.squadlink.ui.map.MarkerType.DANGER,
            com.example.squadlink.ui.map.MarkerType.CUSTOM
        )
    } else {
        listOf(
            com.example.squadlink.ui.map.MarkerType.ENEMY,
            com.example.squadlink.ui.map.MarkerType.ENEMY_HEAVY,
            com.example.squadlink.ui.map.MarkerType.CONTACT
        )
    }
}

@Composable
private fun LocationPermissionCard(modifier: Modifier, onRequestPermission: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101010))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Se requiere permiso de ubicaci\u00f3n para mostrarte en el mapa.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Button(onClick = onRequestPermission) {
                Text("Permitir")
            }
        }
    }
}

@Composable
private fun BackgroundLocationPermissionCard(
    modifier: Modifier,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101010))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Ubicacion en segundo plano",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                "Permite alertas cuando el telefono esta bloqueado.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Button(onClick = onRequestPermission) { Text("Activar") }
        }
    }
}

@Composable
private fun NotificationPermissionCard(
    modifier: Modifier,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101010))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Notificaciones",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                "Permite alertas en la pantalla bloqueada.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Button(onClick = onRequestPermission) { Text("Permitir") }
        }
    }
}

@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    return remember(hasPermission) {
        LocationPermissionState(hasPermission) { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    }
}

private data class NotificationPermissionState(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit
)

@Composable
private fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    val hasPermissionState = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermissionState.value = granted
    }

    return NotificationPermissionState(
        hasPermission = hasPermissionState.value,
        requestPermission = {
            if (Build.VERSION.SDK_INT >= 33) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )
}

private data class BackgroundLocationPermissionState(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit
)

@Composable
private fun rememberBackgroundLocationPermissionState(): BackgroundLocationPermissionState {
    val context = LocalContext.current
    val hasPermissionState = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermissionState.value = granted
    }

    return BackgroundLocationPermissionState(
        hasPermission = hasPermissionState.value,
        requestPermission = { launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    )
}

class LocationPermissionState(val hasPermission: Boolean, val requestPermission: () -> Unit)

@SuppressLint("MissingPermission")
@Composable
fun LocationUpdatesEffect(enabled: Boolean, onLocation: (android.location.Location) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(enabled, lifecycleOwner) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocation(it) }
            }
        }

        if (enabled) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                client.removeLocationUpdates(callback)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            client.removeLocationUpdates(callback)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
