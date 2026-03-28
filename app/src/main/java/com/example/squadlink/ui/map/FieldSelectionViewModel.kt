package com.example.squadlink.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.squadlink.data.FieldRepository
import com.example.squadlink.data.UserPreferencesRepository
import com.example.squadlink.model.AirsoftField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FieldSelectionUiState(
    val query: String = "",
    val fields: List<AirsoftField> = emptyList(),
    val selectedField: AirsoftField? = null
)

class FieldSelectionViewModel(
    private val repo: UserPreferencesRepository
) : ViewModel() {

    private val allFields = FieldRepository.fields

    private val _uiState = MutableStateFlow(
        FieldSelectionUiState(fields = allFields)
    )
    val uiState: StateFlow<FieldSelectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.selectedFieldId.collect { fieldId ->
                val selected = allFields.firstOrNull { it.id == fieldId }
                _uiState.update { it.copy(selectedField = selected) }
            }
        }
    }

    fun updateQuery(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            allFields
        } else {
            allFields.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
        _uiState.update { it.copy(query = query, fields = filtered) }
    }

    fun selectField(field: AirsoftField) {
        viewModelScope.launch { repo.setSelectedFieldId(field.id) }
        _uiState.update { it.copy(selectedField = field) }
    }

    fun clearSelection() {
        viewModelScope.launch { repo.clearSelectedField() }
        _uiState.update { it.copy(selectedField = null) }
    }

    class Factory(private val repo: UserPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FieldSelectionViewModel(repo) as T
    }
}
