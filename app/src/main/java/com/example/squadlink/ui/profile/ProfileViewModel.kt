package com.example.squadlink.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.squadlink.data.FirebaseAccountRepository
import com.example.squadlink.data.UserPreferencesRepository
import com.example.squadlink.model.AccountRole
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val activeUser: String = "",
    val activeEmail: String = "",
    val isGameMaster: Boolean = false,
    val isBusy: Boolean = false,
    val isSessionResolved: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val repo: UserPreferencesRepository,
    private val accountRepository: FirebaseAccountRepository
) : ViewModel() {

    private val viewState = MutableStateFlow(ProfileUiState())

    val uiState: StateFlow<ProfileUiState> = combine(
        repo.activeUserName,
        repo.activeUserEmail,
        repo.isGameMaster,
        viewState
    ) { name, email, gm, state ->
        state.copy(
            activeUser = name,
            activeEmail = email,
            isGameMaster = gm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    init {
        restoreSession()
    }

    fun restoreSession() {
        viewModelScope.launch {
            viewState.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                accountRepository.restoreSession()
            } catch (error: Throwable) {
                repo.clearSession()
                viewState.update { it.copy(errorMessage = error.toFriendlyMessage()) }
            }
            viewState.update { it.copy(isBusy = false, isSessionResolved = true) }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            viewState.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                accountRepository.signIn(email = email, password = password)
            } catch (error: Throwable) {
                viewState.update { it.copy(errorMessage = error.toFriendlyMessage()) }
            }
            viewState.update { it.copy(isBusy = false, isSessionResolved = true) }
        }
    }

    fun register(
        displayName: String,
        email: String,
        password: String,
        role: AccountRole
    ) {
        viewModelScope.launch {
            viewState.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                accountRepository.register(
                    displayName = displayName,
                    email = email,
                    password = password,
                    role = role
                )
            } catch (error: Throwable) {
                viewState.update { it.copy(errorMessage = error.toFriendlyMessage()) }
            }
            viewState.update { it.copy(isBusy = false, isSessionResolved = true) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            viewState.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                accountRepository.signOut()
            } catch (error: Throwable) {
                viewState.update { it.copy(errorMessage = error.toFriendlyMessage()) }
            }
            viewState.update { it.copy(isBusy = false) }
        }
    }

    fun clearError() {
        viewState.update { it.copy(errorMessage = null) }
    }

    class Factory(private val repo: UserPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(
                repo = repo,
                accountRepository = FirebaseAccountRepository(repo)
            ) as T
    }
}

private fun Throwable.toFriendlyMessage(): String {
    return when (this) {
        is FirebaseAuthWeakPasswordException ->
            "La contrasena es demasiado debil. Usa al menos 6 caracteres."
        is FirebaseAuthUserCollisionException ->
            "Ya existe una cuenta registrada con ese correo."
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException ->
            "Correo o contrasena incorrectos."
        is FirebaseNetworkException ->
            "No se pudo conectar con Firebase. Revisa la red del dispositivo."
        is FirebaseFirestoreException ->
            "La sesion se abrio, pero no se pudo cargar el perfil en Firestore."
        else -> message ?: "Ha ocurrido un error autenticando la cuenta."
    }
}
