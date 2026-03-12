package com.example.squadlink.ui.map

import androidx.lifecycle.ViewModel
import com.example.squadlink.data.FieldRepository
import com.example.squadlink.model.AirsoftField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FieldSelectionUiState(
    val query: String = "",
    val fields: List<AirsoftField> = emptyList(),
    val selectedField: AirsoftField? = null
)

class FieldSelectionViewModel : ViewModel() {

    private val allFields = FieldRepository.fields

    private val _uiState = MutableStateFlow(
        FieldSelectionUiState(fields = allFields)
    )
    val uiState: StateFlow<FieldSelectionUiState> = _uiState.asStateFlow()

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
        _uiState.update { it.copy(selectedField = field) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedField = null) }
    }
}
