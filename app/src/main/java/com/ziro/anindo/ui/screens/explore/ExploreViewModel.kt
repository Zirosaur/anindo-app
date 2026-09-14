package com.ziro.anindo.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.provider.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ExploreUiState {
    object Idle : ExploreUiState()
    object Loading : ExploreUiState()
    data class Success(val results: List<Anime>) : ExploreUiState()
    data class Error(val message: String) : ExploreUiState()
}

class ExploreViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Idle)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = ExploreUiState.Idle
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500) // Debounce search
            _uiState.value = ExploreUiState.Loading

            try {
                val results = ProviderRegistry.searchAll(newQuery)
                if (results.isEmpty()) {
                    _uiState.value = ExploreUiState.Error("Tidak ada anime yang ditemukan untuk '$newQuery'")
                } else {
                    _uiState.value = ExploreUiState.Success(results)
                }
            } catch (e: Exception) {
                _uiState.value = ExploreUiState.Error(e.localizedMessage ?: "Terjadi kesalahan saat mencari")
            }
        }
    }
}
