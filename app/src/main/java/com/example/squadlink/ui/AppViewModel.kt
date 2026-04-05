package com.example.squadlink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.squadlink.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Top-level ViewModel owned by MainActivity.
 * Exposes only the preferences that affect the entire app shell
 * (theme, screen-on flag) so MainActivity can observe them directly.
 */
class AppViewModel(repo: UserPreferencesRepository) : ViewModel() {

    val darkTheme = repo.darkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val keepScreenOn = repo.keepScreenOn.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    class Factory(private val repo: UserPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(repo) as T
    }
}