package com.ziro.anindo.core.provider

import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamCandidate
import com.ziro.anindo.core.model.StreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object ProviderRegistry {
    private val providers = ConcurrentHashMap<String, BaseProvider>()

    init {
        val otakudesu = OtakudesuProvider()
        val nontonAnime = NontonAnimeProvider()
        register(otakudesu)
        register(nontonAnime, "nontonanimeid")
    }

    fun register(provider: BaseProvider, vararg aliases: String) {
        providers[provider.name.lowercase()] = provider
        aliases.forEach { alias ->
            providers[alias.lowercase()] = provider
        }
    }

    fun get(name: String): BaseProvider? {
        return providers[name.lowercase()]
    }

    fun all(): List<BaseProvider> {
        return providers.values.distinctBy { it.name }.toList()
    }

    suspend fun searchAll(query: String): List<Anime> = coroutineScope {
        val uniqueProviders = all()
        val tasks = uniqueProviders.map { p ->
            async {
                try {
                    p.search(query)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        tasks.awaitAll().flatten()
    }

    /**
     * Cross-Provider Episodes List Fallback:
     * If provider A returns empty episode list, search alternate providers for the anime
     * and retrieve episodes from an alternate provider.
     */
    suspend fun findCrossProviderEpisodes(
        animeTitle: String,
        excludeProvider: String
    ): Pair<String, List<Episode>> {
        val alternates = all().filter { it.name.lowercase() != excludeProvider.lowercase() }
        for (alt in alternates) {
            try {
                val searchMatches = alt.search(animeTitle)
                val targetAnime = searchMatches.firstOrNull() ?: continue
                val eps = alt.getEpisodes(targetAnime.url)
                if (eps.isNotEmpty()) {
                    return Pair(alt.name, eps)
                }
            } catch (e: Exception) {
                // Try next provider
            }
        }
        return Pair("", emptyList())
    }

    /**
     * Cross-Provider Episode Stream Fallback:
     * If all stream candidates on provider A fail, search alternate providers
     * for the same episode and return fallback stream candidates.
     */
    suspend fun findCrossProviderFallback(
        animeTitle: String,
        epNum: Float,
        excludeProvider: String
    ): Pair<Episode?, List<StreamCandidate>> {
        val alternates = all().filter { it.name.lowercase() != excludeProvider.lowercase() }
        for (alt in alternates) {
            try {
                val searchMatches = alt.search(animeTitle)
                val targetAnime = searchMatches.firstOrNull() ?: continue
                val eps = alt.getEpisodes(targetAnime.url)
                val matchedEp = eps.firstOrNull { it.epNum == epNum } ?: continue
                val streams = alt.extractStreams(matchedEp)
                if (streams.isNotEmpty()) {
                    return Pair(matchedEp, streams)
                }
            } catch (e: Exception) {
                // Try next provider
            }
        }
        return Pair(null, emptyList())
    }

    suspend fun resolveStreamForEpisodeUrl(
        episodeUrl: String,
        episodeTitle: String = ""
    ): StreamResult? = withContext(Dispatchers.IO) {
        val primaryProvider = if (episodeUrl.contains("nontonanime", ignoreCase = true)) {
            get("nontonanimeid") ?: get("otakudesu")
        } else {
            get("otakudesu") ?: all().firstOrNull()
        } ?: return@withContext null

        val episode = Episode(
            title = episodeTitle.ifBlank { "Episode" },
            epNum = 0f,
            url = episodeUrl
        )

        try {
            val candidates = primaryProvider.extractStreams(episode)
            for (cand in candidates) {
                try {
                    val res = cand.resolve()
                    if (res != null && res.url.isNotBlank()) {
                        return@withContext res
                    }
                } catch (e: Throwable) {
                    // Try next candidate
                }
            }
        } catch (e: Exception) {
            // Extraction error
        }
        null
    }
}
