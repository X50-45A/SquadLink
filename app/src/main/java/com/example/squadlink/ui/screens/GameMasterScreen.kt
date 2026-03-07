package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMasterScreen(
    onBack: () -> Unit
) {
    var gameStarted by remember { mutableStateOf(false) }
    var gameCode by remember { mutableStateOf("ALFA-${(1000..9999).random()}") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel Game Master") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: add tactical marker */ }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir marcador")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Game code card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Código de partida", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            gameCode,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "Comparte este código con los jugadores",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Game controls
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Control de partida", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { gameStarted = !gameStarted },
                                modifier = Modifier.weight(1f),
                                colors = if (gameStarted)
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                else ButtonDefaults.buttonColors()
                            ) {
                                Icon(
                                    if (gameStarted) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (gameStarted) "Finalizar" else "Iniciar partida")
                            }
                        }
                    }
                }
            }

            // Tactical markers section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Marcadores tácticos", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        // TODO: List of tactical markers placed on map
                        Text(
                            "Usa el botón + para añadir objetivos y marcadores al mapa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Placeholder marker chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Objetivo A") },
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, null,
                                        modifier = Modifier.size(16.dp))
                                }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("Zona segura") },
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, null,
                                        modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            // Players connected
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Jugadores conectados", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "3 jugadores en sala — partida ${if (gameStarted) "en curso" else "en espera"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
