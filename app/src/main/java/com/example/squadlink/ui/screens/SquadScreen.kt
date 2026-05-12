package com.example.squadlink.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.squadlink.data.FirebaseGameMapRepository
import com.example.squadlink.data.GamePlayer
import com.example.squadlink.data.GameTeam
import com.example.squadlink.data.UserPreferencesRepository
import com.example.squadlink.ui.map.DynamicObjective
import com.example.squadlink.model.AccountRole
import com.example.squadlink.model.SquadMemberProfile
import com.example.squadlink.ui.squad.SquadViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadScreen(vm: SquadViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { UserPreferencesRepository(context) }
    val activeGameCode by prefs.activeGameCode.collectAsState(initial = "")
    val isSessionGameMaster by prefs.isGameMaster.collectAsState(initial = false)

    if (activeGameCode.isNotBlank()) {
        ActiveGameRosterScreen(
            gameCode = activeGameCode,
            isGameMaster = isSessionGameMaster
        )
        return
    }

    var squadNameInput by rememberSaveable { mutableStateOf("") }
    var joinCodeInput by rememberSaveable { mutableStateOf("") }
    var memberToRemove by rememberSaveable(state.currentUserId) { mutableStateOf<String?>(null) }
    val title = if (state.currentAccountRole == AccountRole.GAME_MASTER) {
        "Jugadores"
    } else {
        "Escuadron"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (state.errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.errorMessage ?: "",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (!state.hasSquad) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Crear escuadron", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Crea un escuadron nuevo y comparte el codigo con el resto del equipo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = squadNameInput,
                                onValueChange = {
                                    squadNameInput = it
                                    vm.clearError()
                                },
                                label = { Text("Nombre del escuadron") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    vm.createSquad(squadNameInput)
                                    squadNameInput = ""
                                },
                                enabled = !state.isBusy && squadNameInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Crear")
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Unirse a escuadron", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Si ya existe, introduce su codigo para entrar con tu perfil actual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = joinCodeInput,
                                onValueChange = {
                                    joinCodeInput = it.uppercase()
                                    vm.clearError()
                                },
                                label = { Text("Codigo de union") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedButton(
                                onClick = {
                                    vm.joinSquad(joinCodeInput)
                                    joinCodeInput = ""
                                },
                                enabled = !state.isBusy && joinCodeInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Unirse")
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(state.squadName, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (state.isLeader) {
                                    "Codigo de union: ${state.squadCode}"
                                } else {
                                    "Tu Team Leader gestiona las invitaciones al escuadron."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                "${state.members.size} miembro(s) sincronizados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            if (state.isLeader) {
                                Text(
                                    "Comparte este codigo para agregar miembros nuevos. Mantén pulsado sobre un miembro para expulsarlo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { vm.leaveSquad() },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Salir del escuadron")
                    }
                }

                item {
                    Text("Miembros", style = MaterialTheme.typography.titleMedium)
                }

                items(state.members, key = { it.uid }) { member ->
                    MemberCard(
                        member = member,
                        isCurrentUser = member.uid == state.currentUserId,
                        isLeader = member.uid == state.squadLeaderId,
                        canRemove = state.isLeader && member.uid != state.currentUserId,
                        onLongPress = { memberToRemove = member.uid }
                    )
                }
            }
        }
    }

    val selectedMember = state.members.firstOrNull { it.uid == memberToRemove }
    if (selectedMember != null) {
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Expulsar miembro") },
            text = {
                Text(
                    "Se eliminara a ${selectedMember.callsign} del escuadron actual. Podra volver a entrar si recibe de nuevo el codigo de union."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeMember(selectedMember.uid)
                        memberToRemove = null
                    }
                ) {
                    Text("Expulsar")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveGameRosterScreen(
    gameCode: String,
    isGameMaster: Boolean
) {
    val gameRepo = remember { FirebaseGameMapRepository() }
    val scope = rememberCoroutineScope()
    val game by gameRepo.observeGame(gameCode).collectAsState(initial = null)
    val players by gameRepo.observeGamePlayers(gameCode).collectAsState(initial = emptyList())
    val objectives by gameRepo.observeDynamicObjectives(gameCode).collectAsState(initial = emptyList())
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val currentPlayer = players.firstOrNull { it.uid == currentUserId }
    val visibleObjectives = objectives.filter { objective ->
        isGameMaster || objective.isVisibleFor(currentPlayer?.team)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isGameMaster) "Equipos" else "Objetivos")
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(game?.missionType?.label ?: "Mision", style = MaterialTheme.typography.titleMedium)
                        Text(
                            game?.missionDescription.orEmpty().ifBlank { "Sin descripcion de mision." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (isGameMaster) {
                item {
                    Text("Equipo rojo", style = MaterialTheme.typography.titleMedium)
                }
                items(players.filter { it.team == GameTeam.RED }, key = { it.uid }) { player ->
                    GamePlayerCard(
                        player = player,
                        onExpel = {
                            scope.launch { gameRepo.expelPlayer(gameCode, player.uid) }
                        }
                    )
                }
                item {
                    Text("Equipo azul", style = MaterialTheme.typography.titleMedium)
                }
                items(players.filter { it.team == GameTeam.BLUE }, key = { it.uid }) { player ->
                    GamePlayerCard(
                        player = player,
                        onExpel = {
                            scope.launch { gameRepo.expelPlayer(gameCode, player.uid) }
                        }
                    )
                }
                if (players.isEmpty()) {
                    item {
                        Text(
                            "Aun no hay jugadores en el briefing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Text(
                        currentPlayer?.team?.label ?: "Equipo pendiente",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (visibleObjectives.isEmpty()) {
                    item {
                        Text(
                            "Aun no hay objetivos adicionales.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(visibleObjectives, key = { it.id }) { objective ->
                    ObjectiveCard(objective)
                }
            }
        }
    }
}

@Composable
private fun GamePlayerCard(
    player: GamePlayer,
    onExpel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(player.callsign.ifBlank { player.displayName }, style = MaterialTheme.typography.bodyLarge)
                Text(player.displayName, style = MaterialTheme.typography.bodySmall)
                Text(
                    listOf(player.squadRole, player.squadName)
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                        .ifBlank { "Sin escuadron" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onExpel) {
                Text("Expulsar")
            }
        }
    }
}

@Composable
private fun ObjectiveCard(objective: DynamicObjective) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(objective.type.label, style = MaterialTheme.typography.titleSmall)
            Text(objective.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                objective.targetTeam?.let { "Solo: $it" } ?: "Todos los equipos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DynamicObjective.isVisibleFor(team: GameTeam?): Boolean {
    val target = targetTeam?.trim()?.lowercase() ?: return true
    val currentTeam = team ?: return false
    return target == currentTeam.name.lowercase() ||
        target == currentTeam.label.lowercase() ||
        (currentTeam == GameTeam.RED && target == "rojo") ||
        (currentTeam == GameTeam.BLUE && target == "azul")
}

@Composable
private fun MemberCard(
    member: SquadMemberProfile,
    isCurrentUser: Boolean,
    isLeader: Boolean,
    canRemove: Boolean,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    if (canRemove) {
                        onLongPress()
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.callsign,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = member.squadRole.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isCurrentUser) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else if (isLeader) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = when {
                        isCurrentUser -> "Tu perfil"
                        isLeader -> "Team Leader"
                        else -> member.accountRole.label
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentUser) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else if (isLeader) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
