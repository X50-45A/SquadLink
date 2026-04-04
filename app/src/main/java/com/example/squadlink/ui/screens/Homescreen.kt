package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.squadlink.R

@Composable
fun HomeScreen(
    sessionVm: com.example.squadlink.ui.session.GameSessionViewModel,
    onJoinGame: () -> Unit,
    onCreateGame: () -> Unit
) {
    val sessionState by sessionVm.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_squadlink_logo),
            contentDescription = "SquadLink",
            modifier = Modifier.size(120.dp),
            tint = Color.Unspecified
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SquadLink",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Coordinacion tactica para Airsoft",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (sessionState.isGameMaster) {
            Button(
                onClick = onCreateGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Acceso Game Master", fontSize = 16.sp)
            }
        } else {
            Button(
                onClick = onJoinGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Unirse a partida", fontSize = 16.sp)
            }
        }
    }
}
