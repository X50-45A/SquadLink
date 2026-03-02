package com.example.squadlink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa Táctico") },
                actions = {
                    // TODO: Add marker creation button for GM
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Añadir marcador")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // TODO: Replace with Google Maps / OSMdroid composable
            // GoogleMap or MapView will be embedded here
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2D4A2D)), // Placeholder map background (field green)
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Mapa en construcción",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Se integrará Google Maps / OpenStreetMap",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Floating player count HUD
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Escuadrón Alpha", style = MaterialTheme.typography.labelMedium)
                    Text("3/6 jugadores activos", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
