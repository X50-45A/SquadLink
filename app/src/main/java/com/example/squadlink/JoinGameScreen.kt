package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGameScreen(
    onGameJoined: () -> Unit,
    onBack: () -> Unit
) {
    var gameCode by remember { mutableStateOf("") }
    var playerName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unirse a partida") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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

            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text("Nombre en partida") },
                placeholder = { Text("Ej: Alpha-1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = gameCode,
                onValueChange = { gameCode = it.uppercase() },
                label = { Text("Código de partida") },
                placeholder = { Text("Ej: ALFA-2024") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Text
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onGameJoined,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = gameCode.isNotBlank() && playerName.isNotBlank()
            ) {
                Text("Entrar")
            }
        }
    }
}
