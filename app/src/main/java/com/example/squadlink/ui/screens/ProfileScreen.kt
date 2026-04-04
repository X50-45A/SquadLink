package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.squadlink.ui.profile.AccountRole
import com.example.squadlink.ui.profile.DemoAccount
import com.example.squadlink.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onLogout: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val accounts = listOf(
        DemoAccount("Alpha-1", AccountRole.PLAYER),
        DemoAccount("Alpha-2", AccountRole.PLAYER),
        DemoAccount("Bravo-1", AccountRole.PLAYER),
        DemoAccount("GM-1", AccountRole.GAME_MASTER)
    )

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
                if (state.isGameMaster) "Rol: Game Master" else "Rol: Jugador",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            Text("Cambiar cuenta", style = MaterialTheme.typography.titleMedium)
            accounts.forEach { account ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.activeUser == account.name,
                        onClick = { vm.selectAccount(account) }
                    )
                    Column {
                        Text(account.name)
                        Text(
                            if (account.role == AccountRole.GAME_MASTER) "Game Master" else "Jugador",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    vm.logout()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar sesion")
            }
        }
    }
}
