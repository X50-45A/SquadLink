package com.example.squadlink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.squadlink.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val darkTheme: Boolean     = false,
    val playerName: String     = "",
    val showGrid: Boolean      = true,
    val keepScreenOn: Boolean  = true
)

class SettingsViewModel(
    private val repo: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        repo.darkTheme,
        repo.playerName,
        repo.showGrid,
        repo.keepScreenOn
    ) { darkTheme, playerName, showGrid, keepScreenOn ->
        SettingsUiState(darkTheme, playerName, showGrid, keepScreenOn)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setDarkTheme(enabled: Boolean) =
        viewModelScope.launch { repo.setDarkTheme(enabled) }

    fun setPlayerName(name: String) =
        viewModelScope.launch { repo.setPlayerName(name) }

    fun setShowGrid(enabled: Boolean) =
        viewModelScope.launch { repo.setShowGrid(enabled) }

    fun setKeepScreenOn(enabled: Boolean) =
        viewModelScope.launch { repo.setKeepScreenOn(enabled) }

    class Factory(private val repo: UserPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repo) as T
    }
}