package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.squadlink.ui.session.GameSessionViewModel

// Placeholder data model - will be replaced by Firebase data
data class SquadMember(
    val id: String,
    val name: String,
    val role: String,
    val isOnline: Boolean
)

private val mockMembers = listOf(
    SquadMember("1", "Alpha-1", "Team Leader", true),
    SquadMember("2", "Alpha-2", "Rifleman", true),
    SquadMember("3", "Alpha-3", "Medic", false),
    SquadMember("4", "Alpha-4", "Support", true)
)

private val mockTeams = listOf(
    "Alpha" to listOf("Alpha-1", "Alpha-2", "Alpha-3"),
    "Bravo" to listOf("Bravo-1", "Bravo-2")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadScreen(sessionVm: GameSessionViewModel) {
    val sessionState by sessionVm.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (sessionState.isGameMaster) "Jugadores" else "Escuadron") })
        }
    ) { padding ->
        if (sessionState.isGameMaster) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            Text(
                                "Equipos en la partida",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${mockTeams.sumOf { it.second.size }} jugadores conectados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(mockTeams) { team ->
                    TeamCard(team.first, team.second)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    // Squad summary card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Escuadron Alpha",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${mockMembers.count { it.isOnline }}/${mockMembers.size} miembros conectados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Miembros",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(mockMembers) { member ->
                    MemberCard(member = member)
                }
            }
        }
    }
}

@Composable
private fun TeamCard(name: String, players: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                players.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MemberCard(member: SquadMember) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                tint = if (member.isOnline)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    member.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Online indicator
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (member.isOnline)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (member.isOnline) "Online" else "Offline",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (member.isOnline)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
