package com.example.squadlink.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.squadlink.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameSessionUiState(
    val activeGameCode: String = "",
    val isGameMaster: Boolean = false
)

class GameSessionViewModel(
    private val repo: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<GameSessionUiState> = combine(
        repo.activeGameCode,
        repo.isGameMaster
    ) { code, gm ->
        GameSessionUiState(code, gm)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GameSessionUiState()
    )

    fun joinGame(code: String) {
        viewModelScope.launch {
            repo.setActiveGameCode(code)
            repo.setIsGameMaster(false)
        }
    }

    fun startGame(code: String) {
        viewModelScope.launch {
            repo.setActiveGameCode(code)
        }
    }

    fun endGame() {
        viewModelScope.launch { repo.clearActiveGameCode() }
    }

    fun setGameMaster(enabled: Boolean) {
        viewModelScope.launch { repo.setIsGameMaster(enabled) }
    }

    class Factory(private val repo: UserPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameSessionViewModel(repo) as T
    }
}
