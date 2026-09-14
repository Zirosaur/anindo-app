package com.ziro.anindo.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziro.anindo.AnindoApp
import com.ziro.anindo.core.data.local.entity.EpisodeProgressEntity
import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamResult
import com.ziro.anindo.core.provider.ProviderRegistry
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            } catch (e: Exception) {}
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

                var episodes = provider.getEpisodes(animeUrl)
                if (episodes.isEmpty() && currentAnime != null && currentAnime!!.title.isNotBlank()) {
                    val cleanTitle = currentAnime!!.title
                        .replace(Regex("""(?i)\(ongoing\)"""), "")
                        .replace(Regex("""\[.*?\]"""), "")
                        .trim()
                    val (_, altEps) = ProviderRegistry.findCrossProviderEpisodes(cleanTitle, providerName)
                    if (altEps.isNotEmpty()) {
                        episodes = altEps
                    }
                }

                if (episodes.isEmpty()) {
                    _uiState.value = DetailsUiState.Error("Daftar episode tidak ditemukan.")
                } else {
                    _uiState.value = DetailsUiState.Success(episodes)
                }
            } catch (e: Exception) {
                // If primary failed with exception, attempt failover
                var altFound = false
                if (currentAnime != null && currentAnime!!.title.isNotBlank()) {
                    try {
                        val cleanTitle = currentAnime!!.title
                            .replace(Regex("""(?i)\(ongoing\)"""), "")
                            .replace(Regex("""\[.*?\]"""), "")
                            .trim()
                        val (_, altEps) = ProviderRegistry.findCrossProviderEpisodes(cleanTitle, providerName)
                        if (altEps.isNotEmpty()) {
                            _uiState.value = DetailsUiState.Success(altEps)
                            altFound = true
                        }
                    } catch (e2: Exception) {}
                }
                if (!altFound) {
                    _uiState.value = DetailsUiState.Error(e.localizedMessage ?: "Gagal memuat episode")
                }
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

    suspend fun resolveStream(episode: Episode, providerName: String): StreamResult? = withContext(Dispatchers.IO) {
        val provider = ProviderRegistry.get(providerName) ?: ProviderRegistry.all().first()
        val candidates = try {
            provider.extractStreams(episode)
        } catch (e: Exception) {
            Log.e("AnindoStream", "Error extracting streams from $providerName for ${episode.title}", e)
            emptyList()
        }
        Log.d("AnindoStream", "Found ${candidates.size} stream candidates for ${episode.title} ($providerName)")

        for (cand in candidates) {
            try {
                val result = cand.resolve()
                if (result != null && result.url.isNotBlank()) {
                    Log.d("AnindoStream", "Success resolving stream from server: ${cand.server}")
                    return@withContext result
                }
            } catch (e: Throwable) {
                Log.e("AnindoStream", "Failed candidate: ${cand.server}", e)
            }
        }

        // Cross-Provider Fallback if primary fails
        Log.w("AnindoStream", "All primary stream candidates failed for ${episode.title}, trying cross-provider fallback")
        val (_, fallbackStreams) = try {
            ProviderRegistry.findCrossProviderFallback(episode.title, episode.epNum, providerName)
        } catch (e: Exception) {
            Log.e("AnindoStream", "Error in cross-provider fallback", e)
            Pair(null, emptyList())
        }

        for (cand in fallbackStreams) {
            try {
                val result = cand.resolve()
                if (result != null && result.url.isNotBlank()) {
                    Log.d("AnindoStream", "Success resolving fallback stream from server: ${cand.server}")
                    return@withContext result
                }
            } catch (e: Throwable) {
                Log.e("AnindoStream", "Failed fallback candidate: ${cand.server}", e)
            }
        }
        Log.e("AnindoStream", "No working stream candidate found for ${episode.title}")
        null
    }

    suspend fun resolveDownloadStream(episode: Episode, providerName: String): StreamResult? = withContext(Dispatchers.IO) {
        val provider = ProviderRegistry.get(providerName) ?: ProviderRegistry.all().first()
        val candidates = try {
            provider.extractStreams(episode)
        } catch (e: Exception) {
            Log.e("AnindoStream", "Error extracting streams for download: ${e.message}", e)
            emptyList()
        }
        Log.d("AnindoStream", "Found ${candidates.size} stream candidates for download: ${episode.title}")

        // For downloading, prioritize direct MP4 candidates (!isHls) first
        val sortedCandidates = candidates.sortedBy { if (it.isHls) 1 else 0 }

        for (cand in sortedCandidates) {
            try {
                val result = cand.resolve()
                if (result != null && result.url.isNotBlank()) {
                    Log.d("AnindoStream", "Success resolving download stream from server: ${cand.server} (HLS: ${cand.isHls})")
                    return@withContext result
                }
            } catch (e: Throwable) {
                Log.e("AnindoStream", "Failed download candidate: ${cand.server}", e)
            }
        }

        // Cross-Provider Fallback for download
        Log.w("AnindoStream", "All primary download candidates failed for ${episode.title}, trying fallback")
        val (_, fallbackStreams) = try {
            ProviderRegistry.findCrossProviderFallback(episode.title, episode.epNum, providerName)
        } catch (e: Exception) {
            Pair(null, emptyList())
        }

        val sortedFallback = fallbackStreams.sortedBy { if (it.isHls) 1 else 0 }
        for (cand in sortedFallback) {
            try {
                val result = cand.resolve()
                if (result != null && result.url.isNotBlank()) {
                    Log.d("AnindoStream", "Success resolving fallback download stream: ${cand.server}")
                    return@withContext result
                }
            } catch (e: Throwable) {
                Log.e("AnindoStream", "Failed fallback download candidate: ${cand.server}", e)
            }
        }
        Log.e("AnindoStream", "No working download candidate found for ${episode.title}")
        null
    }
}

