package com.ziro.anindo.core.provider

import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamCandidate

abstract class BaseProvider {
    abstract val name: String
    abstract val displayName: String

    abstract suspend fun getBaseUrl(forceRefresh: Boolean = false): String
    abstract suspend fun search(query: String): List<Anime>
    abstract suspend fun getEpisodes(animeUrl: String): List<Episode>
    abstract suspend fun extractStreams(episode: Episode): List<StreamCandidate>
    abstract suspend fun getOngoing(): List<Anime>
}
