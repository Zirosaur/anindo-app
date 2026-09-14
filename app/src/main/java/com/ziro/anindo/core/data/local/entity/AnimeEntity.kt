package com.ziro.anindo.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ziro.anindo.core.model.Anime

@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val posterUrl: String = "",
    val rating: String = "N/A",
    val type: String = "TV",
    val status: String = "Unknown",
    val synopsis: String = "",
    val genres: String = "", // Comma-separated list of genres
    val provider: String = "otakudesu",
    val url: String = "",
    val isBookmarked: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toAnime(): Anime {
        return Anime(
            id = id,
            title = title,
            url = url,
            posterUrl = posterUrl,
            rating = rating,
            type = type,
            status = status,
            synopsis = synopsis,
            genres = if (genres.isNotBlank()) genres.split(",").map { it.trim() } else emptyList(),
            provider = provider
        )
    }

    companion object {
        fun fromAnime(anime: Anime, isBookmarked: Boolean = false): AnimeEntity {
            return AnimeEntity(
                id = anime.computedId,
                title = anime.title,
                posterUrl = anime.posterUrl ?: "",
                rating = anime.rating ?: "N/A",
                type = anime.type ?: "TV",
                status = anime.status ?: "Unknown",
                synopsis = anime.synopsis ?: "",
                genres = anime.genres.joinToString(","),
                provider = anime.provider,
                url = anime.url,
                isBookmarked = isBookmarked,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
