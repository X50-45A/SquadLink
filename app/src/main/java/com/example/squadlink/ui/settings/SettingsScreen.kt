package com.example.squadlink.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    var nameInput by remember(state.playerName) { mutableStateOf(state.playerName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Sección: Red / Apariencia ─────────────────────────────────
            PreferenceSectionHeader("Apariencia")

            PreferenceRow(
                title = "Tema oscuro",
                subtitle = "Interfaz en modo oscuro",
                checked = state.darkTheme,
                onCheckedChange = vm::setDarkTheme
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Sección: Partida ──────────────────────────────────────────
            PreferenceSectionHeader("Partida")

            // Player name — saved on focus lost / done
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Nombre en partida") },
                placeholder = { Text("Ej: Alpha-1") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
                trailingIcon = {
                    if (nameInput != state.playerName) {
                        TextButton(onClick = { vm.setPlayerName(nameInput) }) {
                            Text("Guardar")
                        }
                    }
                }
            )

            PreferenceRow(
                title = "Cuadrícula táctica",
                subtitle = "Mostrar cuadrícula sobre el mapa",
                checked = state.showGrid,
                onCheckedChange = vm::setShowGrid
            )

            PreferenceRow(
                title = "Mantener pantalla activa",
                subtitle = "Evita que la pantalla se apague durante la partida",
                checked = state.keepScreenOn,
                onCheckedChange = vm::setKeepScreenOn
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PreferenceSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}