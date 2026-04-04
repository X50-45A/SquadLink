package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.squadlink.R
import com.example.squadlink.ui.profile.AccountRole
import com.example.squadlink.ui.profile.DemoAccount
import com.example.squadlink.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    vm: ProfileViewModel,
    onLoggedIn: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val accounts = listOf(
        DemoAccount("Alpha-1", AccountRole.PLAYER),
        DemoAccount("Alpha-2", AccountRole.PLAYER),
        DemoAccount("Bravo-1", AccountRole.PLAYER),
        DemoAccount("GM-1", AccountRole.GAME_MASTER)
    )

    LaunchedEffect(state.activeUser) {
        if (state.activeUser.isNotBlank()) {
            onLoggedIn()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Inicio de sesion") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_squadlink_logo),
                contentDescription = "SquadLink",
                modifier = Modifier.size(140.dp),
                tint = Color.Unspecified
            )
            Text("Selecciona tu cuenta", fontSize = 20.sp)

            accounts.forEach { account ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.selectAccount(account)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (account.role == AccountRole.GAME_MASTER)
                                    "Game Master"
                                else
                                    "Jugador",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Entrar")
                    }
                }
            }
        }
    }
}
