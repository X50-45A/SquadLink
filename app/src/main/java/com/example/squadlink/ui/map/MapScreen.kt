package com.example.squadlink.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*

// ── Tactical colour palette ──────────────────────────────────────────────────
private val GridColor       = Color(0x2200FF41)
private val FieldFill       = Color(0x1A4CAF50)
private val FieldStroke     = Color(0xFF76FF03)
private val OutOfBoundsRed  = Color(0xCCF44336)
private val ObjectiveYellow = Color(0xFFFFD600)
private val SafeZoneBlue    = Color(0xFF29B6F6)

@Composable
fun MapScreen(vm: MapViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val field  = state.field
    val ctx    = LocalContext.current

    val mapStyle = remember {
        MapStyleOptions.loadRawResourceStyle(ctx, com.example.squadlink.R.raw.map_style_tactical)
    }

    // Only render map once field data is loaded from Firebase
    if (field == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(field.center, field.defaultZoom)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(
                mapStyleOptions = mapStyle,
                isMyLocationEnabled = true
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            // Field perimeter polygon
            Polygon(
                points = field.perimeter,
                fillColor = FieldFill,
                strokeColor = FieldStroke,
                strokeWidth = 4f,
                geodesic = false
            )

            // Squad player markers
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

            // Tactical markers
            state.tacticalMarkers.forEach { marker ->
                val color = when (marker.type) {
                    MarkerType.OBJECTIVE -> ObjectiveYellow
                    MarkerType.SAFE_ZONE -> SafeZoneBlue
                    MarkerType.DANGER -> OutOfBoundsRed
                    MarkerType.CUSTOM -> Color.White
                }
                Marker(
                    state = MarkerState(position = marker.position),
                    title = marker.label,
                    icon = bitmapDescriptorFromColor(color)
                )
            }
        }

        // Tactical grid overlay
        if (state.gridVisible) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTacticalGrid(this)
            }
        }

        // HUD: squad status
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC101810))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    field.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF76FF03)
                )
                Text(
                    "${state.players.count { !it.isOutOfBounds }}/${state.players.size} en zona",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }

        // Grid toggle
        FloatingActionButton(
            onClick = vm::toggleGrid,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(44.dp),
            containerColor = Color(0xCC101810),
            contentColor = if (state.gridVisible) Color(0xFF76FF03) else Color.Gray
        ) {
            Icon(Icons.Default.GridOn, contentDescription = "Cuadrícula táctica")
        }

        // Out-of-bounds alert
        AnimatedVisibility(
            visible = state.showOutOfBoundsAlert,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically() + fadeIn(),
            exit  = slideOutVertically() + fadeOut()
        ) {
            OutOfBoundsAlert(onDismiss = vm::dismissOutOfBoundsAlert)
        }
    }
}

@Composable
private fun OutOfBoundsAlert(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(top = 72.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCB71C1C)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "⚠ FUERA DEL CAMPO — Regresa a la zona de juego",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("OK", color = Color.White)
            }
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
            (color.red   * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue  * 255).toInt()
        ), hsv
    )
    return BitmapDescriptorFactory.defaultMarker(hsv[0])
}