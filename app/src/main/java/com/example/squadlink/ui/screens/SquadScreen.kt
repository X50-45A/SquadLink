package com.example.squadlink.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.squadlink.model.AccountRole
import com.example.squadlink.model.SquadMemberProfile
import com.example.squadlink.ui.squad.SquadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadScreen(vm: SquadViewModel) {
    val state by vm.uiState.collectAsState()
    var squadNameInput by rememberSaveable { mutableStateOf("") }
    var joinCodeInput by rememberSaveable { mutableStateOf("") }
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
                                "Codigo de union: ${state.squadCode}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                "${state.members.size} miembro(s) sincronizados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
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
                        isCurrentUser = member.uid == state.currentUserId
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: SquadMemberProfile,
    isCurrentUser: Boolean
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
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = if (isCurrentUser) "Tu perfil" else member.accountRole.label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentUser) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
