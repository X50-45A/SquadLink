package com.example.squadlink.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import java.net.URL
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var displayNameInput by remember(state.activeUser) { mutableStateOf(state.activeUser) }
    var callsignInput by remember(state.callsign) { mutableStateOf(state.callsign) }
    var squadRoleInput by remember(state.squadRole) { mutableStateOf(state.squadRole) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            vm.uploadProfilePhoto(uri)
        }
    }

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
                if (state.isGameMaster) "Rol en partida: Game Master" else "Rol en partida: Jugador",
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

            Text("Foto de perfil", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProfilePhoto(
                    uri = state.photoUrl.ifBlank { state.profilePhotoUri },
                    fallback = state.callsign.ifBlank { state.activeUser },
                    modifier = Modifier.size(96.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                        enabled = !state.isBusy
                    ) {
                        Text(if (state.photoUrl.isBlank() && state.profilePhotoUri.isBlank()) "Añadir foto" else "Cambiar foto")
                    }
                    if (state.photoUrl.isNotBlank() || state.profilePhotoUri.isNotBlank()) {
                        OutlinedButton(
                            onClick = { vm.updateProfilePhoto("") },
                            enabled = !state.isBusy
                        ) {
                            Text("Quitar")
                        }
                    }
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

@Composable
private fun ProfilePhoto(
    uri: String,
    fallback: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = runCatching {
            if (uri.isBlank()) {
                null
            } else if (uri.startsWith("data:image/")) {
                val base64 = uri.substringAfter("base64,", missingDelimiterValue = "")
                if (base64.isBlank()) {
                    null
                } else {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                }
            } else if (uri.startsWith("http://") || uri.startsWith("https://")) {
                URL(uri).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream).asImageBitmap()
                }
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
                ImageDecoder.decodeBitmap(source).asImageBitmap()
            }
        }.getOrNull()
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "Foto de perfil",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                initialsFor(fallback),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun initialsFor(value: String): String {
    return value
        .split(" ", "-", "_")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "SL" }
}

