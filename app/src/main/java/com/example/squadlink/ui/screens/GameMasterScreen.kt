package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.squadlink.ui.map.FieldSelectionViewModel
import com.example.squadlink.ui.map.MapViewModel
import com.example.squadlink.ui.map.MarkerType
import com.example.squadlink.ui.session.GameSessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMasterScreen(
    fieldVm: FieldSelectionViewModel,
    sessionVm: GameSessionViewModel,
    mapVm: MapViewModel,
    onBack: () -> Unit,
    onOpenMap: () -> Unit
) {
    val sessionState by sessionVm.uiState.collectAsState()
    val fieldState by fieldVm.uiState.collectAsState()
    val selectedField = fieldState.selectedField
    var gameCode by remember { mutableStateOf("ALFA-${(1000..9999).random()}") }
    val gameStarted = sessionState.activeGameCode.isNotBlank()

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
        }
    ) { padding ->
        if (!sessionState.isGameMaster) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Acceso restringido", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Este panel es solo para cuentas Game Master. Cambia la cuenta en Perfil.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Codigo de partida", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(gameCode, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Comparte este codigo con los jugadores",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Campo de airsoft", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            fieldState.fields.forEach { field ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    RadioButton(
                                        selected = field.id == selectedField?.id,
                                        onClick = { fieldVm.selectField(field) }
                                    )
                                    Text(field.name, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Control de partida", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (gameStarted) {
                                        sessionVm.endGame()
                                    } else {
                                        sessionVm.startGame(gameCode)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedField != null,
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

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Marcadores tacticos", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Coloca zonas seguras y banderas directamente en el mapa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(
                                        onClick = {
                                            mapVm.setMarkerMode(MarkerType.OBJECTIVE)
                                            onOpenMap()
                                        },
                                        label = { Text("Bandera") },
                                        leadingIcon = {
                                            Icon(Icons.Default.LocationOn, null,
                                                modifier = Modifier.size(16.dp))
                                        }
                                    )
                                    AssistChip(
                                        onClick = {
                                            mapVm.setMarkerMode(MarkerType.SAFE_ZONE)
                                            onOpenMap()
                                        },
                                        label = { Text("Zona segura") },
                                        leadingIcon = {
                                            Icon(Icons.Default.LocationOn, null,
                                                modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(
                                        onClick = {
                                            mapVm.setMarkerMode(MarkerType.DANGER)
                                            onOpenMap()
                                        },
                                        label = { Text("Peligro") },
                                        leadingIcon = {
                                            Icon(Icons.Default.LocationOn, null,
                                                modifier = Modifier.size(16.dp))
                                        }
                                    )
                                    AssistChip(
                                        onClick = {
                                            mapVm.setMarkerMode(MarkerType.CUSTOM)
                                            onOpenMap()
                                        },
                                        label = { Text("Personal") },
                                        leadingIcon = {
                                            Icon(Icons.Default.LocationOn, null,
                                                modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Equipos", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Alpha", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Alpha-1, Alpha-2, Alpha-3",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Bravo", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Bravo-1, Bravo-2",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
