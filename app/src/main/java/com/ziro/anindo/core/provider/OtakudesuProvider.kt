package com.ziro.anindo.core.provider

import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamCandidate
import com.ziro.anindo.core.model.StreamResult
import com.ziro.anindo.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OtakudesuProvider : BaseProvider() {
    override val name: String = "otakudesu"
    override val displayName: String = "Otakudesu"

    override suspend fun getBaseUrl(forceRefresh: Boolean): String {
        return DynamicDomainResolver.resolve(name, forceRefresh)
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val encQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val searchUrl = "$base/?s=$encQuery&post_type=anime"
        val html = NetworkClient.get(searchUrl, referer = base)
        val doc = Jsoup.parse(html)
        val results = mutableListOf<Anime>()

        val items = doc.select("ul.chivsrc li, .page .venser .page li")
        for (item in items) {
            val a = item.selectFirst("h2 a, a") ?: continue
            val href = a.attr("abs:href")
            val title = a.text().trim()
            val img = item.selectFirst("img")?.attr("abs:src")
            val status = item.selectFirst(".set:contains(Status)")?.text()

            if (title.isNotBlank() && href.isNotBlank()) {
                results.add(
                    Anime(
                        title = title,
                        url = href,
                        posterUrl = img,
                        provider = name,
                        status = status
                    )
                )
            }
        }
        results
    }

    override suspend fun getEpisodes(animeUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val html = NetworkClient.get(animeUrl, referer = base)
        val doc = Jsoup.parse(html)
        val episodes = mutableListOf<Episode>()

        val links = doc.select(".episodelst ul li a, #rightlist ul li a")
        for (link in links) {
            val href = link.attr("abs:href")
            val title = link.text().trim()
            if (href.contains("/episode/")) {
                val numMatch = Regex("""\b(?:episode|ep)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(title)
                val epNum = numMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 1.0f
                episodes.add(Episode(title = title, epNum = epNum, url = href))
            }
        }
        episodes.sortedBy { it.epNum }
    }

    override suspend fun extractStreams(episode: Episode): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val html = NetworkClient.get(episode.url, referer = base)
        val doc = Jsoup.parse(html)
        val candidates = mutableListOf<StreamCandidate>()

        // 1. Check primary iframe embed
        val primaryIframe = doc.selectFirst(".responsive-embed-stream iframe, .player-embed iframe")
        val primarySrc = primaryIframe?.attr("abs:src")?.ifBlank { primaryIframe.attr("src") }
        if (!primarySrc.isNullOrBlank()) {
            candidates.add(
                StreamCandidate(
                    server = "Default Player",
                    quality = "Auto HD",
                    isHls = primarySrc.contains("m3u8") || primarySrc.contains("hls"),
                    resolve = {
                        StreamResult(url = primarySrc, referer = episode.url)
                    }
                )
            )
        }

        // 2. Check mirror options
        val mirrorElements = doc.select(".mirrorstream ul li a, .mirror ul li a")
        for (m in mirrorElements) {
            val serverName = m.text().trim()
            val dataContent = m.attr("data-content")
            if (dataContent.isNotBlank()) {
                candidates.add(
                    StreamCandidate(
                        server = serverName,
                        quality = "Mirror",
                        isHls = false,
                        resolve = {
                            // Resolve mirror link via Otakudesu action_mirror endpoint
                            StreamResult(url = dataContent, referer = episode.url)
                        }
                    )
                )
            }
        }
        candidates
    }

    override suspend fun getOngoing(): List<Anime> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val ongoingUrl = "$base/ongoing-anime/"
        val html = NetworkClient.get(ongoingUrl, referer = base)
        val doc = Jsoup.parse(html)
        val results = mutableListOf<Anime>()

        val items = doc.select(".venz ul li, .rapi ul li")
        for (item in items) {
            val a = item.selectFirst(".thumb a, h2 a, a") ?: continue
            val href = a.attr("abs:href")
            val title = item.selectFirst(".jdlflm, h2")?.text()?.trim() ?: a.text().trim()
            val img = item.selectFirst(".thumb img, img")?.attr("abs:src")
            val epInfo = item.selectFirst(".epz")?.text()?.trim()

            if (title.isNotBlank() && href.isNotBlank()) {
                results.add(
                    Anime(
                        title = title,
                        url = href,
                        posterUrl = img,
                        provider = name,
                        status = epInfo ?: "Ongoing"
                    )
                )
            }
        }
        results
    }
}
