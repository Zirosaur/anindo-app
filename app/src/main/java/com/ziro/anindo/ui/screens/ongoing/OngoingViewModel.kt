package com.ziro.anindo.ui.screens.ongoing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.provider.ProviderRegistry
import kotlinx.coroutines.Dispatchers
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
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = OngoingUiState.Loading
            try {
                // Try primary provider first (Otakudesu)
                val primary = ProviderRegistry.get("otakudesu") ?: ProviderRegistry.all().first()
                var list = try { primary.getOngoing() } catch (_: Exception) { emptyList() }

                // If primary returned empty or failed, fallback to alternate providers
                if (list.isEmpty()) {
                    val alternates = ProviderRegistry.all().filter { it.name.lowercase() != primary.name.lowercase() }
                    for (alt in alternates) {
                        try {
                            val altList = alt.getOngoing()
                            if (altList.isNotEmpty()) {
                                list = altList
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (list.isEmpty()) {
                    _uiState.value = OngoingUiState.Error("Tidak ada anime on-going yang ditemukan. Periksa koneksi internet Anda.")
                } else {
                    _uiState.value = OngoingUiState.Success(list)
                }
            } catch (e: Exception) {
                _uiState.value = OngoingUiState.Error(e.localizedMessage ?: "Gagal memuat anime ongoing")
            }
        }
    }
}
