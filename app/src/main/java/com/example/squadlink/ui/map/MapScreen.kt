package com.example.squadlink.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

private val GridColor = Color(0x2200FF41)
private val FieldFill = Color(0x1A4CAF50)
private val FieldStroke = Color(0xFF76FF03)
private val OutOfBoundsRed = Color(0xCCF44336)

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

    val mapStyle = remember {
        runCatching {
            MapStyleOptions.loadRawResourceStyle(ctx, R.raw.map_style_tactical)
        }.getOrNull()
    }

    val locationPermissionState = rememberLocationPermissionState()
    var requestedLocationPermission by rememberSaveable { mutableStateOf(false) }
    var showFieldPicker by rememberSaveable { mutableStateOf(field == null) }
    var mapLoaded by remember { mutableStateOf(false) }
    var showMapError by remember { mutableStateOf(false) }
    var markerMode by remember { mutableStateOf<MarkerType?>(null) }
    var customMarkerLabel by remember { mutableStateOf("") }
    val mapLocked = sessionState.activeGameCode.isNotBlank()

    LaunchedEffect(locationPermissionState.hasPermission, requestedLocationPermission) {
        if (!locationPermissionState.hasPermission && !requestedLocationPermission) {
            requestedLocationPermission = true
            locationPermissionState.requestPermission()
        }
    }

    val currentFieldId = field?.id
    val selectedFieldId = selectedField?.id
    LaunchedEffect(selectedFieldId, currentFieldId) {
        if (selectedField != null && selectedField.id != currentFieldId) {
            mapVm.onFieldLoaded(selectedField)
            showFieldPicker = false
        }
    }

    LocationUpdatesEffect(
        enabled = locationPermissionState.hasPermission,
        onLocation = { location ->
            mapVm.onPlayerLocationUpdate(LatLng(location.latitude, location.longitude))
        }
    )

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
                state = mapState,
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
                    val label = markerLabelFor(mode, customMarkerLabel)
                    mapVm.addTacticalMarker(mode, latLng, label)
                    markerMode = null
                }
            )
        } else {
            PortraitMapLayout(
                state = mapState,
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
                    val label = markerLabelFor(mode, customMarkerLabel)
                    mapVm.addTacticalMarker(mode, latLng, label)
                    markerMode = null
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
                onSelectMode = { markerMode = it }
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
    onMapClick: (LatLng) -> Unit
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
            onMapClick = onMapClick
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
    onMapClick: (LatLng) -> Unit
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
                onMapClick = onMapClick
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
    onMapClick: (LatLng) -> Unit
) {
    val tacticalIcons: Map<String, BitmapDescriptor> =
        if (mapLoaded) rememberTacticalMarkerIcons() else emptyMap()
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(
            mapType = MapType.TERRAIN,
            mapStyleOptions = mapStyle,
            isMyLocationEnabled = hasLocationPermission
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = hasLocationPermission,
            mapToolbarEnabled = false
        ),
        onMapLoaded = onMapLoaded,
        onMapClick = onMapClick
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

        state.tacticalMarkers.forEach { marker ->
            Marker(
                state = MarkerState(position = marker.position),
                title = marker.label,
                icon = tacticalIcons[marker.type.name.lowercase()]
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
    onSelectMode: (com.example.squadlink.ui.map.MarkerType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
                            onClick = { onSelectMode(type) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isSelected) Color(0xFF76FF03) else Color.White
                            )
                        ) {
                            Text(type.name, style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun rememberTacticalMarkerIcons(): Map<String, BitmapDescriptor> {
    val ctx = LocalContext.current
    return remember(ctx) {
        mapOf(
            "player" to createMarkerIcon(ctx, Color(0xFF00FF41), true),
            "player_out" to createMarkerIcon(ctx, Color(0xFFF44336), true),
            "objective" to createMarkerIcon(ctx, Color(0xFFFFD600), false),
            "safe_zone" to createMarkerIcon(ctx, Color(0xFF29B6F6), false),
            "enemy" to createMarkerIcon(ctx, Color(0xFFD32F2F), false),
            "custom" to createMarkerIcon(ctx, Color.White, false)
        )
    }
}

private fun createMarkerIcon(context: Context, color: Color, isPlayer: Boolean): BitmapDescriptor {
    val size = 64
    val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color.toArgb()
        isAntiAlias = true
    }

    if (isPlayer) {
        val path = Path().apply {
            moveTo(size / 2f, 0f)
            lineTo(size.toFloat(), size.toFloat())
            lineTo(size / 2f, size * 0.75f)
            lineTo(0f, size.toFloat())
            close()
        }
        canvas.drawPath(path, paint)
    } else {
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = android.graphics.Color.BLACK
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
    }

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun markerLabelFor(mode: com.example.squadlink.ui.map.MarkerType, custom: String): String = when (mode) {
    com.example.squadlink.ui.map.MarkerType.ENEMY -> "Enemigo"
    com.example.squadlink.ui.map.MarkerType.OBJECTIVE -> "Objetivo"
    com.example.squadlink.ui.map.MarkerType.CUSTOM -> custom.ifBlank { "Marcador" }
    else -> mode.name
}

private fun markerToolsForRole(isGm: Boolean): List<com.example.squadlink.ui.map.MarkerType> {
    return if (isGm) {
        listOf(com.example.squadlink.ui.map.MarkerType.ENEMY, com.example.squadlink.ui.map.MarkerType.OBJECTIVE, com.example.squadlink.ui.map.MarkerType.CUSTOM)
    } else {
        listOf(com.example.squadlink.ui.map.MarkerType.ENEMY, com.example.squadlink.ui.map.MarkerType.CUSTOM)
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
