package com.example.squadlink.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.squadlink.model.AirsoftField
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay

private val GridColor = Color(0x2200FF41)
private val FieldFill = Color(0x1A4CAF50)
private val FieldStroke = Color(0xFF76FF03)
private val OutOfBoundsRed = Color(0xCCF44336)
private val ObjectiveYellow = Color(0xFFFFD600)
private val SafeZoneBlue = Color(0xFF29B6F6)

@Composable
fun MapScreen(
    fieldVm: FieldSelectionViewModel,
    mapVm: MapViewModel = viewModel()
) {
    val mapState by mapVm.uiState.collectAsState()
    val selectionState by fieldVm.uiState.collectAsState()
    val selectedField = selectionState.selectedField
    val field = mapState.field
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val mapStyle = remember {
        runCatching {
            MapStyleOptions.loadRawResourceStyle(ctx, com.example.squadlink.R.raw.map_style_tactical)
        }.getOrNull()
    }

    val locationPermissionState = rememberLocationPermissionState()
    var requestedLocationPermission by rememberSaveable { mutableStateOf(false) }
    var showFieldPicker by rememberSaveable { mutableStateOf(field == null) }
    var mapLoaded by remember { mutableStateOf(false) }
    var showMapError by remember { mutableStateOf(false) }
    var markerMode by remember { mutableStateOf<MarkerType?>(null) }
    var customMarkerLabel by remember { mutableStateOf("") }

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
        onLocation = mapVm::onPlayerLocationUpdate
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
            onClick = { showFieldPicker = true }
        )

        if (showFieldPicker) {
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

        MarkerToolsPanel(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            activeMode = markerMode,
            customLabel = customMarkerLabel,
            onCustomLabelChange = { customMarkerLabel = it },
            onSelectMode = { markerMode = it }
        )

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
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Geofence lista para cargar",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF76FF03)
                )
            }
            Button(onClick = { onSelect(field) }) {
                Text("Abrir")
            }
        }
    }
}

@Composable
private fun ChangeFieldButton(modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text("Cambiar campo")
    }
}

@Composable
private fun PortraitMapLayout(
    state: MapUiState,
    cameraState: CameraPositionState,
    mapStyle: MapStyleOptions?,
    field: AirsoftField,
    hasLocationPermission: Boolean,
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
        AnimatedVisibility(
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
    onMapLoaded: () -> Unit,
    onMapClick: (LatLng) -> Unit
) {
    val tacticalIcons = rememberTacticalMarkerIcons()
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(
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
        field.perimeter.forEach { polygon ->
            Polygon(
                points = polygon,
                fillColor = FieldFill,
                strokeColor = FieldStroke,
                strokeWidth = 4f,
                geodesic = false
            )
        }

        state.players.forEach { player ->
            Marker(
                state = MarkerState(position = player.position),
                title = player.name,
                snippet = player.role,
                icon = bitmapDescriptorFromColor(
                    if (player.isOutOfBounds) OutOfBoundsRed else Color(0xFF00E676)
                )
            )
        }

        state.tacticalMarkers.forEach { marker ->
            val icon = tacticalIcons[marker.type]
            Marker(
                state = MarkerState(position = marker.position),
                title = marker.label,
                icon = icon
            )
        }
    }
}

@Composable
private fun MapLoadErrorCard(
    modifier: Modifier,
    onRetry: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCB71C1C)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("El mapa no ha cargado", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Revisa la API key, los SHA-1 en Google Cloud y que el emulador tenga Google Play Services.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onRetry) { Text("Reintentar", color = Color.White) }
        }
    }
}

@Composable
private fun MarkerToolsPanel(
    modifier: Modifier,
    activeMode: MarkerType?,
    customLabel: String,
    onCustomLabelChange: (String) -> Unit,
    onSelectMode: (MarkerType?) -> Unit
) {
    Card(
        modifier = modifier.widthIn(max = 240.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC101810)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Marcadores", color = Color.White, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkerModeButton("Objetivo", activeMode == MarkerType.OBJECTIVE) {
                    onSelectMode(MarkerType.OBJECTIVE)
                }
                MarkerModeButton("Seguro", activeMode == MarkerType.SAFE_ZONE) {
                    onSelectMode(MarkerType.SAFE_ZONE)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkerModeButton("Peligro", activeMode == MarkerType.DANGER) {
                    onSelectMode(MarkerType.DANGER)
                }
                MarkerModeButton("Custom", activeMode == MarkerType.CUSTOM) {
                    onSelectMode(MarkerType.CUSTOM)
                }
            }
            if (activeMode == MarkerType.CUSTOM) {
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = onCustomLabelChange,
                    singleLine = true,
                    label = { Text("Etiqueta") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = "Toca el mapa para colocar",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MarkerModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color(0xFF1B3D1B) else Color.Transparent,
            contentColor = Color.White
        )
    ) {
        Text(label)
    }
}

@Composable
private fun MarkerPlacementHint(modifier: Modifier, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC101810)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = "Colocando: $label",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private fun markerLabelFor(type: MarkerType, customLabel: String): String {
    return when (type) {
        MarkerType.OBJECTIVE -> "Objetivo"
        MarkerType.SAFE_ZONE -> "Zona segura"
        MarkerType.DANGER -> "Peligro"
        MarkerType.CUSTOM -> if (customLabel.isBlank()) "Marcador" else customLabel
    }
}

@Composable
private fun MapHud(modifier: Modifier, fieldName: String, playersInZone: Int, totalPlayers: Int) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC101810))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(fieldName, style = MaterialTheme.typography.labelSmall, color = Color(0xFF76FF03))
            Text(
                "$playersInZone/$totalPlayers en zona",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun GridToggleFab(modifier: Modifier, active: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = Color(0xCC101810),
        contentColor = if (active) Color(0xFF76FF03) else Color.Gray
    ) {
        Icon(Icons.Default.GridOn, contentDescription = "Cuadricula tactica")
    }
}

@Composable
private fun OutOfBoundsAlert(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.padding(top = 72.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCB71C1C)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "! FUERA DEL CAMPO - Regresa a la zona de juego",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("OK", color = Color.White) }
        }
    }
}

@Composable
private fun LocationPermissionCard(
    modifier: Modifier,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC101810)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ubicacion desactivada", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Activa la ubicacion para recibir alertas del geofence y funciones futuras.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onRequestPermission) { Text("Activar") }
        }
    }
}

@Composable
private fun LocationUpdatesEffect(
    enabled: Boolean,
    onLocation: (LatLng) -> Unit
) {
    val context = LocalContext.current
    val client = remember { LocationServices.getFusedLocationProviderClient(context) }
    val request = remember {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(4000L)
            .build()
    }
    val callback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onLocation(LatLng(location.latitude, location.longitude))
            }
        }
    }

    DisposableEffect(enabled) {
        if (enabled) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }
        onDispose { client.removeLocationUpdates(callback) }
    }
}

private fun drawTacticalGrid(scope: DrawScope) {
    val step = 80f
    var x = 0f
    while (x < scope.size.width) {
        scope.drawLine(GridColor, Offset(x, 0f), Offset(x, scope.size.height), strokeWidth = 0.5f)
        x += step
    }
    var y = 0f
    while (y < scope.size.height) {
        scope.drawLine(GridColor, Offset(0f, y), Offset(scope.size.width, y), strokeWidth = 0.5f)
        y += step
    }
}

private fun bitmapDescriptorFromColor(color: Color): BitmapDescriptor {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        ),
        hsv
    )
    return BitmapDescriptorFactory.defaultMarker(hsv[0])
}

private enum class TacticalShape { DIAMOND, SQUARE, TRIANGLE, CIRCLE }

@Composable
private fun rememberTacticalMarkerIcons(): Map<MarkerType, BitmapDescriptor> {
    return remember {
        mapOf(
            MarkerType.OBJECTIVE to tacticalMarkerIcon(TacticalShape.DIAMOND, ObjectiveYellow, Color(0xFF101810)),
            MarkerType.SAFE_ZONE to tacticalMarkerIcon(TacticalShape.SQUARE, SafeZoneBlue, Color(0xFF101810)),
            MarkerType.DANGER to tacticalMarkerIcon(TacticalShape.TRIANGLE, OutOfBoundsRed, Color(0xFF101810)),
            MarkerType.CUSTOM to tacticalMarkerIcon(TacticalShape.CIRCLE, Color.White, Color(0xFF101810))
        )
    }
}

private fun tacticalMarkerIcon(
    shape: TacticalShape,
    stroke: Color,
    fill: Color
): BitmapDescriptor {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val padding = 12f
    val rect = RectF(padding, padding, size - padding, size - padding)

    val path = Path().apply {
        when (shape) {
            TacticalShape.DIAMOND -> {
                moveTo(size / 2f, rect.top)
                lineTo(rect.right, size / 2f)
                lineTo(size / 2f, rect.bottom)
                lineTo(rect.left, size / 2f)
                close()
            }
            TacticalShape.SQUARE -> {
                addRect(rect, Path.Direction.CW)
            }
            TacticalShape.TRIANGLE -> {
                moveTo(size / 2f, rect.top)
                lineTo(rect.right, rect.bottom)
                lineTo(rect.left, rect.bottom)
                close()
            }
            TacticalShape.CIRCLE -> {
                addOval(rect, Path.Direction.CW)
            }
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

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private data class LocationPermissionState(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit
)

@Composable
private fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasPermissionState = remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissionState.value = result.values.any { it }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermissionState.value = hasLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return LocationPermissionState(
        hasPermission = hasPermissionState.value,
        requestPermission = { launcher.launch(LocationPermissions) }
    )
}

private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private fun hasLocationPermission(context: Context): Boolean {
    return LocationPermissions.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
