package com.ziro.anindo.ui.screens.details

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import com.ziro.anindo.AnindoApp

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    animeUrl: String,
    animeId: String = "",
    animeTitle: String = "Detail Anime",
    posterUrl: String = "",
    providerName: String = "otakudesu",
    onBackClick: () -> Unit,
    onPlayEpisode: (String, String, String) -> Unit,
    viewModel: DetailsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResolving by remember { mutableStateOf(false) }

    val currentAnime = remember(animeId, animeUrl, animeTitle, posterUrl) {
        Anime(
            title = animeTitle,
            url = animeUrl,
            posterUrl = posterUrl,
            provider = providerName,
            id = animeId.ifBlank { animeUrl.hashCode().toString() }
        )
    }


    LaunchedEffect(animeUrl, providerName, animeId) {
        viewModel.setAnimeInfo(currentAnime)
        viewModel.loadEpisodes(animeUrl, providerName, currentAnime.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(animeTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleBookmark(currentAnime) }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Hapus dari Koleksi" else "Simpan ke Koleksi",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is DetailsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DetailsUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.episodes) { episode ->
                            val progress = progressMap[episode.url]
                            val isCompleted = progress?.isCompleted == true

                            Card(
                                onClick = {
                                    if (isResolving) return@Card
                                    isResolving = true
                                    scope.launch {
                                        val stream = viewModel.resolveStream(episode, providerName)
                                        isResolving = false
                                        if (stream != null && stream.url.isNotBlank()) {
                                            onPlayEpisode(stream.url, episode.title, stream.referer ?: episode.url)
                                        } else {
                                            Toast.makeText(context, "Gagal memuat server video episode ini", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCompleted)
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = episode.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                                                color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        Toast.makeText(context, "Mengekstrak unduhan...", Toast.LENGTH_SHORT).show()
                                                        val stream = viewModel.resolveDownloadStream(episode, providerName)
                                                        if (stream != null && stream.url.isNotBlank()) {
                                                            AnindoApp.instance.downloadManager.enqueueDownload(
                                                                animeId = currentAnime.computedId,
                                                                animeTitle = currentAnime.title,
                                                                posterUrl = currentAnime.posterUrl ?: "",
                                                                episodeTitle = episode.title,
                                                                episodeUrl = episode.url,
                                                                videoStreamUrl = stream.url,
                                                                referer = stream.referer ?: episode.url
                                                            )
                                                            Toast.makeText(context, "Mulai mengunduh! Cek progres di tab Unduhan (Koleksi).", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "Gagal mengunduh episode ini", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Unduh Episode",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (isCompleted) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selesai ditonton",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Putar",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }


                                    // If watched partially (not completed yet)
                                    if (progress != null && !isCompleted && progress.durationMs > 0) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { progress.progressPercent },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is DetailsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (isResolving) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
