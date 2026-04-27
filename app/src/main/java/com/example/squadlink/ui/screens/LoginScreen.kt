package com.example.squadlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.squadlink.R
import com.example.squadlink.model.AccountRole
import com.example.squadlink.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    vm: ProfileViewModel,
    onLoggedIn: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf(AccountRole.PLAYER) }

    LaunchedEffect(state.isSessionResolved, state.activeUser) {
        if (state.isSessionResolved && state.activeUser.isNotBlank()) {
            onLoggedIn()
        }
    }

    if (!state.isSessionResolved) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Comprobando sesion de Firebase...")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isRegisterMode) "Crear cuenta" else "Iniciar sesion")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_squadlink_logo),
                contentDescription = "SquadLink",
                modifier = Modifier.size(140.dp),
                tint = Color.Unspecified
            )
            Text("Firebase ya gestiona las cuentas reales de SquadLink.", fontSize = 18.sp)
            Text(
                "Para probar ambos flujos, crea una cuenta Game Master y otra Jugador.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isRegisterMode,
                    onClick = {
                        isRegisterMode = false
                        vm.clearError()
                    },
                    label = { Text("Entrar") }
                )
                FilterChip(
                    selected = isRegisterMode,
                    onClick = {
                        isRegisterMode = true
                        vm.clearError()
                    },
                    label = { Text("Registrar") }
                )
            }

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

            if (isRegisterMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre visible") },
                    placeholder = { Text("Ej: Alpha-1 o GM-Central") },
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    vm.clearError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Correo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    vm.clearError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Contrasena") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            if (isRegisterMode) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Repetir contrasena") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Text(
                    "Rol de la cuenta",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccountRole.entries.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.label) }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (isRegisterMode) {
                        vm.register(
                            displayName = displayName,
                            email = email,
                            password = password,
                            role = selectedRole
                        )
                    } else {
                        vm.signIn(email = email, password = password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isBusy &&
                    email.isNotBlank() &&
                    password.isNotBlank() &&
                    (!isRegisterMode || (
                        displayName.isNotBlank() &&
                            confirmPassword.isNotBlank() &&
                            password == confirmPassword
                        ))
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isRegisterMode) "Crear cuenta" else "Entrar")
                }
            }

            if (isRegisterMode && confirmPassword.isNotBlank() && password != confirmPassword) {
                Text(
                    "Las contrasenas no coinciden.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
