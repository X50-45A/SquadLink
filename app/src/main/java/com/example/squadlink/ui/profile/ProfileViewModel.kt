package com.example.squadlink.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.squadlink.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AccountRole { PLAYER, GAME_MASTER }

data class DemoAccount(
    val name: String,
    val role: AccountRole
)

data class ProfileUiState(
    val activeUser: String = "",
    val isGameMaster: Boolean = false
)

class ProfileViewModel(
    private val repo: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        repo.activeUserName,
        repo.isGameMaster
    ) { name, gm ->
        ProfileUiState(name, gm)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    fun selectAccount(account: DemoAccount) {
        viewModelScope.launch {
            repo.setActiveUserName(account.name)
            repo.setIsGameMaster(account.role == AccountRole.GAME_MASTER)
            repo.clearActiveGameCode()
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.clearActiveUserName()
            repo.setIsGameMaster(false)
            repo.clearActiveGameCode()
        }
    }

    class Factory(private val repo: UserPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(repo) as T
    }
}
