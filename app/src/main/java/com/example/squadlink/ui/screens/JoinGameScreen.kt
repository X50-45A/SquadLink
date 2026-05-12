package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.squadlink.data.FirebaseGameMapRepository
import com.example.squadlink.data.GamePhase
import com.example.squadlink.data.GameTeam
import com.example.squadlink.ui.map.FieldSelectionViewModel
import com.example.squadlink.ui.session.GameSessionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGameScreen(
    fieldVm: FieldSelectionViewModel,
    sessionVm: GameSessionViewModel,
    onGameJoined: () -> Unit,
    onBack: () -> Unit
) {
    var gameCode by remember { mutableStateOf("") }
    var playerName by remember { mutableStateOf("") }
    var selectedTeam by remember { mutableStateOf(GameTeam.RED) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val gameRepo = remember { FirebaseGameMapRepository() }
    val game by gameRepo.observeGame(gameCode).collectAsState(initial = null)
    val fieldState by fieldVm.uiState.collectAsState()
    val sessionState by sessionVm.uiState.collectAsState()

    LaunchedEffect(sessionState.suggestedPlayerName) {
        if (playerName.isBlank()) {
            playerName = sessionState.suggestedPlayerName
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unirse a partida") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
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

            OutlinedTextField(
                value = playerName,
                onValueChange = {
                    playerName = it
                    errorMessage = null
                },
                label = { Text("Nombre en partida") },
                placeholder = { Text("Ej: Alpha-1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = gameCode,
                onValueChange = {
                    gameCode = it.uppercase()
                    errorMessage = null
                },
                label = { Text("Codigo de partida") },
                placeholder = { Text("Ej: ALFA-2024") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Text
                )
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Equipo", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameTeam.entries.forEach { team ->
                            FilterChip(
                                selected = selectedTeam == team,
                                onClick = { selectedTeam = team },
                                label = { Text(team.label) }
                            )
                        }
                    }
                }
            }

            if (game != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(game!!.missionType.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (game!!.phase == GamePhase.BRIEFING) {
                                "Briefing abierto"
                            } else {
                                "La partida ya ha empezado"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        try {
                            gameRepo.joinGameTeam(gameCode, selectedTeam, playerName)
                            game?.fieldId
                                ?.let { fieldId -> fieldState.fields.firstOrNull { it.id == fieldId } }
                                ?.let(fieldVm::selectField)
                            sessionVm.joinGame(gameCode, playerName)
                            onGameJoined()
                        } catch (error: Throwable) {
                            errorMessage = error.message ?: "No se pudo entrar en la partida."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = gameCode.isNotBlank() &&
                    playerName.isNotBlank() &&
                    game?.phase == GamePhase.BRIEFING
            ) {
                Text("Entrar al briefing")
            }
        }
    }
}
