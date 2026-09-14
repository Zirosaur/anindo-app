package com.ziro.anindo.core.provider

import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamCandidate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object ProviderRegistry {
    private val providers = mutableMapOf<String, BaseProvider>()

    init {
        register(OtakudesuProvider())
        register(NontonAnimeProvider())
    }

    fun register(provider: BaseProvider) {
        providers[provider.name] = provider
    }

    fun get(name: String): BaseProvider? = providers[name]

    fun all(): List<BaseProvider> = providers.values.toList()

    suspend fun searchAll(query: String): List<Anime> = coroutineScope {
        val tasks = providers.values.map { p ->
            async {
                try {
                    p.search(query)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        tasks.awaitAll().flatten()
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
        val alternates = providers.values.filter { it.name != excludeProvider }
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
            } catch (_: Exception) {
                // Try next provider
            }
        }
        return Pair(null, emptyList())
    }
}
