package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.squadlink.model.SquadRole
import com.example.squadlink.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onLogout: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    var displayNameInput by remember(state.activeUser) { mutableStateOf(state.activeUser) }
    var callsignInput by remember(state.callsign) { mutableStateOf(state.callsign) }
    var squadRoleInput by remember(state.squadRole) { mutableStateOf(state.squadRole) }

    LaunchedEffect(state.activeUser, state.isSessionResolved) {
        if (state.isSessionResolved && state.activeUser.isBlank()) {
            onLogout()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Perfil") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cuenta activa", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.activeEmail.isBlank()) "Sin correo sincronizado" else state.activeEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (state.isGameMaster) "Rol de cuenta: Game Master" else "Rol de cuenta: Jugador",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.errorMessage != null) {
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

            HorizontalDivider()

            Text("Identidad", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = displayNameInput,
                onValueChange = {
                    displayNameInput = it
                    vm.clearError()
                },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = callsignInput,
                onValueChange = {
                    callsignInput = it
                    vm.clearError()
                },
                label = { Text("Callsign") },
                placeholder = { Text("Ej: Alpha-1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Rol dentro del escuadron", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SquadRole.entries.forEach { role ->
                    FilterChip(
                        selected = squadRoleInput == role,
                        onClick = {
                            squadRoleInput = role
                            vm.clearError()
                        },
                        label = { Text(role.label) }
                    )
                }
            }

            Button(
                onClick = {
                    vm.updateProfile(
                        displayName = displayNameInput,
                        callsign = callsignInput,
                        squadRole = squadRoleInput
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy &&
                    displayNameInput.isNotBlank() &&
                    callsignInput.isNotBlank() &&
                    (
                        displayNameInput != state.activeUser ||
                            callsignInput != state.callsign ||
                            squadRoleInput != state.squadRole
                        )
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar perfil")
                }
            }

            HorizontalDivider()

            Text("Escuadron actual", style = MaterialTheme.typography.titleMedium)
            if (state.squadName.isBlank()) {
                Text(
                    "Todavia no perteneces a ningun escuadron. Puedes crear uno o unirte desde la pestana Escuadron.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(state.squadName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "La gestion de miembros y el codigo de union estan en la pestana Escuadron.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.logout() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy
            ) {
                Text("Cerrar sesion")
            }
        }
    }
}
