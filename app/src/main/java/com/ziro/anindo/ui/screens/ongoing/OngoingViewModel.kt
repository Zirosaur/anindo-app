package com.ziro.anindo.ui.screens.ongoing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.provider.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OngoingUiState {
    object Loading : OngoingUiState()
    data class Success(val list: List<Anime>) : OngoingUiState()
    data class Error(val message: String) : OngoingUiState()
}

class OngoingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<OngoingUiState>(OngoingUiState.Loading)
    val uiState: StateFlow<OngoingUiState> = _uiState.asStateFlow()

    init {
        loadOngoing()
    }

    fun loadOngoing() {
        viewModelScope.launch {
            _uiState.value = OngoingUiState.Loading
            try {
                // Fetch ongoing anime from primary provider
                val provider = ProviderRegistry.get("otakudesu") ?: ProviderRegistry.all().first()
                val list = provider.getOngoing()
                _uiState.value = OngoingUiState.Success(list)
            } catch (e: Exception) {
                _uiState.value = OngoingUiState.Error(e.localizedMessage ?: "Gagal memuat anime ongoing")
            }
        }
    }
}
