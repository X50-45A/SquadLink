package com.example.squadlink.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.squadlink.model.AirsoftField
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*

private val GridColor = Color(0x2200FF41)
private val FieldFill = Color(0x1A4CAF50)
private val FieldStroke = Color(0xFF76FF03)
private val OutOfBoundsRed = Color(0xCCF44336)
private val ObjectiveYellow = Color(0xFFFFD600)
private val SafeZoneBlue = Color(0xFF29B6F6)

@Composable
fun MapScreen(
    mapVm: MapViewModel = viewModel(),
    fieldVm: FieldSelectionViewModel = viewModel()
) {
    val mapState by mapVm.uiState.collectAsState()
    val selectionState by fieldVm.uiState.collectAsState()
    val selectedField = selectionState.selectedField
    val field = mapState.field
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val mapStyle = remember {
        MapStyleOptions.loadRawResourceStyle(ctx, com.example.squadlink.R.raw.map_style_tactical)
    }

    val locationPermissionState = rememberLocationPermissionState()
    var requestedLocationPermission by rememberSaveable { mutableStateOf(false) }
    var showFieldPicker by rememberSaveable { mutableStateOf(field == null) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            LandscapeMapLayout(
                state = mapState,
                cameraState = cameraState,
                mapStyle = mapStyle,
                field = field,
                hasLocationPermission = locationPermissionState.hasPermission,
                onToggleGrid = mapVm::toggleGrid,
                onDismissAlert = mapVm::dismissOutOfBoundsAlert
            )
        } else {
            PortraitMapLayout(
                state = mapState,
                cameraState = cameraState,
                mapStyle = mapStyle,
                field = field,
                hasLocationPermission = locationPermissionState.hasPermission,
                onToggleGrid = mapVm::toggleGrid,
                onDismissAlert = mapVm::dismissOutOfBoundsAlert
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
    mapStyle: MapStyleOptions,
    field: AirsoftField,
    hasLocationPermission: Boolean,
    onToggleGrid: () -> Unit,
    onDismissAlert: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TacticalGoogleMap(
            modifier = Modifier.fillMaxSize(),
            state = state,
            cameraState = cameraState,
            mapStyle = mapStyle,
            field = field,
            hasLocationPermission = hasLocationPermission
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
    mapStyle: MapStyleOptions,
    field: AirsoftField,
    hasLocationPermission: Boolean,
    onToggleGrid: () -> Unit,
    onDismissAlert: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TacticalGoogleMap(
                modifier = Modifier.fillMaxSize(),
                state = state,
                cameraState = cameraState,
                mapStyle = mapStyle,
                field = field,
                hasLocationPermission = hasLocationPermission
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
    mapStyle: MapStyleOptions,
    field: AirsoftField,
    hasLocationPermission: Boolean
) {
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
        )
    ) {
        Polygon(
            points = field.perimeter, fillColor = FieldFill, strokeColor = FieldStroke,
            strokeWidth = 4f, geodesic = false
        )

        state.players.forEach { player ->
            Marker(
                state = MarkerState(position = player.position),
                title = player.name, snippet = player.role,
                icon = bitmapDescriptorFromColor(
                    if (player.isOutOfBounds) OutOfBoundsRed else Color(0xFF00E676)
                )
            )
        }

        state.tacticalMarkers.forEach { marker ->
            val color = when (marker.type) {
                MarkerType.OBJECTIVE -> ObjectiveYellow
                MarkerType.SAFE_ZONE -> SafeZoneBlue
                MarkerType.DANGER -> OutOfBoundsRed
                MarkerType.CUSTOM -> Color.White
            }
            Marker(
                state = MarkerState(position = marker.position),
                title = marker.label, icon = bitmapDescriptorFromColor(color)
            )
        }
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
                "Activa la ubicacion para ver tu posicion y recibir alertas del geofence.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onRequestPermission) { Text("Activar") }
        }
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
