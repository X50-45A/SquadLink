package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.squadlink.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onLogout: () -> Unit
) {
    val state by vm.uiState.collectAsState()

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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cuenta activa", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.activeUser.isBlank()) "Ninguna" else state.activeUser,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                if (state.activeEmail.isBlank()) "Sin correo sincronizado" else state.activeEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (state.isGameMaster) "Rol: Game Master" else "Rol: Jugador",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                "Las cuentas ahora viven en Firebase. Si quieres probar ambos roles, cierra sesion y crea otra cuenta desde la pantalla de acceso.",
                style = MaterialTheme.typography.bodyMedium
            )

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
