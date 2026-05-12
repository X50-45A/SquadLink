package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.squadlink.data.FirebaseGameMapRepository
import com.example.squadlink.data.GamePhase
import com.example.squadlink.ui.map.FieldSelectionViewModel
import com.example.squadlink.ui.map.MapViewModel
import com.example.squadlink.ui.map.MarkerType
import com.example.squadlink.ui.map.ObjectiveType
import com.example.squadlink.ui.session.GameSessionViewModel
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val gameRepo = remember { FirebaseGameMapRepository() }
    val activeGame by gameRepo
        .observeGame(sessionState.activeGameCode)
        .collectAsState(initial = null)

    var gameCode by remember { mutableStateOf("ALFA-${(1000..9999).random()}") }
    var missionType by remember { mutableStateOf(ObjectiveType.FLAG) }
    var missionDescription by remember {
        mutableStateOf("Capturar y mantener las banderas indicadas por el Game Master.")
    }
    var missionMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val hasActiveGame = sessionState.activeGameCode.isNotBlank()
    val isRunning = activeGame?.phase == GamePhase.RUNNING
    val showBriefingTools = hasActiveGame && !isRunning

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel Game Master") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
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
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            errorMessage.orEmpty(),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

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
                        Text(
                            if (hasActiveGame) sessionState.activeGameCode else gameCode,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            if (isRunning) {
                                "Partida iniciada"
                            } else {
                                "Comparte este codigo durante el briefing"
                            },
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
                                    onClick = { if (!hasActiveGame) fieldVm.selectField(field) },
                                    enabled = !hasActiveGame
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
                        Text("Mision principal", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Box {
                            OutlinedButton(
                                onClick = { missionMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !hasActiveGame
                            ) {
                                Text(activeGame?.missionType?.label ?: missionType.label)
                            }
                            DropdownMenu(
                                expanded = missionMenuExpanded,
                                onDismissRequest = { missionMenuExpanded = false }
                            ) {
                                ObjectiveType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.label) },
                                        onClick = {
                                            missionType = type
                                            missionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = activeGame?.missionDescription ?: missionDescription,
                            onValueChange = {
                                missionDescription = it
                                errorMessage = null
                            },
                            label = { Text("Descripcion para jugadores") },
                            minLines = 3,
                            enabled = !hasActiveGame,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Briefing y control", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hasActiveGame) {
                                "Los jugadores pueden entrar en equipo rojo o azul hasta que inicies la partida."
                            } else {
                                "Crear briefing te convierte en Game Master de esta partida."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                if (hasActiveGame) {
                                    scope.launch {
                                        try {
                                            gameRepo.startGame(sessionState.activeGameCode)
                                            onOpenMap()
                                        } catch (error: Throwable) {
                                            errorMessage = error.message ?: "No se pudo iniciar la partida."
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        try {
                                            gameRepo.createGame(
                                                gameCode = gameCode,
                                                fieldId = selectedField?.id.orEmpty(),
                                                missionType = missionType,
                                                missionDescription = missionDescription
                                            )
                                            sessionVm.startGame(gameCode)
                                        } catch (error: Throwable) {
                                            errorMessage = error.message ?: "No se pudo crear la partida."
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedField != null &&
                                missionDescription.isNotBlank() &&
                                !isRunning
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(if (hasActiveGame) "Iniciar partida" else "Crear briefing")
                        }

                        if (hasActiveGame) {
                            OutlinedButton(
                                onClick = { sessionVm.endGame() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Cerrar partida local")
                            }
                        }
                    }
                }
            }

            if (showBriefingTools) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Zonas seguras y marcadores", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Marca las zonas seguras antes de iniciar para que los jugadores las vean al entrar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MarkerChip("Bandera", MarkerType.OBJECTIVE, mapVm, onOpenMap)
                                    MarkerChip("Zona segura", MarkerType.SAFE_ZONE, mapVm, onOpenMap)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MarkerChip("Peligro", MarkerType.DANGER, mapVm, onOpenMap)
                                    MarkerChip("Personal", MarkerType.CUSTOM, mapVm, onOpenMap)
                                }
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
                        Text(
                            "Cuando el briefing este activo, gestiona rojo y azul desde la pestaña Equipos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerChip(
    label: String,
    markerType: MarkerType,
    mapVm: MapViewModel,
    onOpenMap: () -> Unit
) {
    AssistChip(
        onClick = {
            mapVm.setMarkerMode(markerType)
            onOpenMap()
        },
        label = { Text(label) },
        leadingIcon = {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}
