package com.ziro.anindo.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamCandidate
import com.ziro.anindo.core.model.StreamResult
import com.ziro.anindo.core.provider.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DetailsUiState {
    object Loading : DetailsUiState()
    data class Success(val episodes: List<Episode>) : DetailsUiState()
    data class Error(val message: String) : DetailsUiState()
}

class DetailsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    fun loadEpisodes(animeUrl: String, providerName: String = "otakudesu") {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            try {
                val provider = ProviderRegistry.get(providerName) ?: ProviderRegistry.all().first()
                val episodes = provider.getEpisodes(animeUrl)
                if (episodes.isEmpty()) {
                    _uiState.value = DetailsUiState.Error("Daftar episode tidak ditemukan.")
                } else {
                    _uiState.value = DetailsUiState.Success(episodes)
                }
            } catch (e: Exception) {
                _uiState.value = DetailsUiState.Error(e.localizedMessage ?: "Gagal memuat episode")
            }
        }
    }

    suspend fun resolveStream(episode: Episode, providerName: String): StreamResult? {
        val provider = ProviderRegistry.get(providerName) ?: ProviderRegistry.all().first()
        val candidates = provider.extractStreams(episode)
        for (cand in candidates) {
            val result = cand.resolve()
            if (result != null && result.url.isNotBlank()) {
                return result
            }
        }
        // Cross-Provider Fallback if primary fails
        val (_, fallbackStreams) = ProviderRegistry.findCrossProviderFallback(episode.title, episode.epNum, providerName)
        for (cand in fallbackStreams) {
            val result = cand.resolve()
            if (result != null && result.url.isNotBlank()) {
                return result
            }
        }
        return null
    }
}
