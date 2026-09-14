package com.ziro.anindo.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziro.anindo.AnindoApp
import com.ziro.anindo.core.data.local.entity.EpisodeProgressEntity
import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamResult
import com.ziro.anindo.core.provider.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class DetailsUiState {
    object Loading : DetailsUiState()
    data class Success(val episodes: List<Episode>) : DetailsUiState()
    data class Error(val message: String) : DetailsUiState()
}

class DetailsViewModel : ViewModel() {
    private val repository = AnindoApp.instance.repository

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<String, EpisodeProgressEntity>>(emptyMap())
    val progressMap: StateFlow<Map<String, EpisodeProgressEntity>> = _progressMap.asStateFlow()

    private var currentAnime: Anime? = null

    fun loadEpisodes(animeUrl: String, providerName: String = "otakudesu", animeId: String = "") {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            try {
                if (animeId.isNotBlank()) {
                    repository.isBookmarked(animeId).collect { bookmarked ->
                        _isBookmarked.value = bookmarked
                    }
                }
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            if (animeId.isNotBlank()) {
                repository.getProgressForAnime(animeId).collect { list ->
                    _progressMap.value = list.associateBy { it.episodeUrl }
                }
            }
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

    fun setAnimeInfo(anime: Anime) {
        currentAnime = anime
        viewModelScope.launch {
            repository.isBookmarked(anime.id).collect { bookmarked ->
                _isBookmarked.value = bookmarked
            }
        }
    }

    fun toggleBookmark(fallbackAnime: Anime) {
        val anime = currentAnime ?: fallbackAnime
        viewModelScope.launch {
            repository.toggleBookmark(anime)
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
