package com.ziro.anindo.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziro.anindo.AnindoApp
import com.ziro.anindo.core.data.local.entity.DownloadEntity
import com.ziro.anindo.core.data.local.entity.EpisodeProgressEntity
import com.ziro.anindo.core.model.Anime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibraryTab {
    BOOKMARKS,
    HISTORY,
    DOWNLOADS
}

class LibraryViewModel : ViewModel() {
    private val repository = AnindoApp.instance.repository

    private val _selectedTab = MutableStateFlow(LibraryTab.BOOKMARKS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Anime>>(emptyList())
    val bookmarks: StateFlow<List<Anime>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow<List<EpisodeProgressEntity>>(emptyList())
    val history: StateFlow<List<EpisodeProgressEntity>> = _history.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val downloads: StateFlow<List<DownloadEntity>> = _downloads.asStateFlow()

    init {
        viewModelScope.launch {
            repository.cleanupCorruptedHistory()
        }
        viewModelScope.launch {
            repository.getBookmarkedAnime().collect { list ->
                _bookmarks.value = list
            }
        }
        viewModelScope.launch {
            repository.getRecentHistory().collect { list ->
                _history.value = list
            }
        }
        viewModelScope.launch {
            repository.getAllDownloads().collect { list ->
                _downloads.value = list
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteDownload(id: String, filePath: String = "") {
        viewModelScope.launch {
            repository.deleteDownload(id, filePath)
        }
    }
}
