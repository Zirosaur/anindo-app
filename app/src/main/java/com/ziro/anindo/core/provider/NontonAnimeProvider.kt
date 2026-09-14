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

class NontonAnimeProvider : BaseProvider() {
    override val name: String = "nontonanime"
    override val displayName: String = "NontonAnime"

    override suspend fun getBaseUrl(forceRefresh: Boolean): String {
        return DynamicDomainResolver.resolve(name, forceRefresh)
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val encQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val searchUrl = "$base/?s=$encQuery"
        val html = NetworkClient.get(searchUrl, referer = base)
        val doc = Jsoup.parse(html, base)
        val results = mutableListOf<Anime>()

        val items = doc.select("article.animeseries, .relat article, .result ul li, article")
        for (item in items) {
            val a = item.selectFirst("a[href*='/anime/'], h2 a, a") ?: continue
            val rawHref = a.attr("href").ifBlank { a.attr("abs:href") }
            val href = if (rawHref.startsWith("http")) rawHref else "$base/${rawHref.trimStart('/')}"
            val title = item.selectFirst(".title, h2, h3")?.text()?.trim() ?: a.text().trim()
            val imgEl = item.selectFirst("img")
            val rawImg = (imgEl?.attr("src") ?: "").ifBlank { imgEl?.attr("data-src") ?: "" }
            val img = if (rawImg.startsWith("http")) rawImg else if (rawImg.isNotBlank()) "$base/${rawImg.trimStart('/')}" else null
            val type = item.selectFirst(".type, .status")?.text()?.trim()

            if (title.isNotBlank() && href.isNotBlank()) {
                results.add(
                    Anime(
                        title = title,
                        url = href,
                        posterUrl = img,
                        provider = name,
                        type = type
                    )
                )
            }
        }
        results
    }

    override suspend fun getEpisodes(animeUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val html = NetworkClient.get(animeUrl, referer = base)
        val doc = Jsoup.parse(html, base)
        val episodes = mutableListOf<Episode>()

        val links = doc.select(".episodelist ul li a, ul.episodes li a, .episodes a")
        for (link in links) {
            val rawHref = link.attr("href").ifBlank { link.attr("abs:href") }
            val href = if (rawHref.startsWith("http")) rawHref else "$base/${rawHref.trimStart('/')}"
            val title = link.text().trim()
            if (href.isNotBlank()) {
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
        val doc = Jsoup.parse(html, base)
        val candidates = mutableListOf<StreamCandidate>()

        val iframes = doc.select(".video-content iframe, iframe[src*='putarin'], iframe[src*='filedon']")
        for (iframe in iframes) {
            val rawSrc = iframe.attr("src").ifBlank { iframe.attr("abs:src") }
            val src = if (rawSrc.startsWith("//")) "https:$rawSrc" else rawSrc
            if (src.isNotBlank()) {
                val srvName = when {
                    src.contains("putarin") -> "Putarin (HLS)"
                    src.contains("filedon") -> "Filedon (HD)"
                    else -> "Stream Server"
                }
                candidates.add(
                    StreamCandidate(
                        server = srvName,
                        quality = "720p",
                        isHls = src.contains("putarin") || src.contains("m3u8"),
                        resolve = {
                            StreamResult(url = src, referer = episode.url)
                        }
                    )
                )
            }
        }
        candidates
    }

    override suspend fun getOngoing(): List<Anime> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val ongoingUrls = listOf("$base/ongoing-bajzxqv/", "$base/ongoing/")
        val results = mutableListOf<Anime>()

        for (u in ongoingUrls) {
            try {
                val html = NetworkClient.get(u, referer = base)
                val doc = Jsoup.parse(html, base)
                val items = doc.select("article.animeseries, .content article, article")
                for (item in items) {
                    val a = item.selectFirst("a[href*='/anime/'], a") ?: continue
                    val rawHref = a.attr("href").ifBlank { a.attr("abs:href") }
                    val href = if (rawHref.startsWith("http")) rawHref else "$base/${rawHref.trimStart('/')}"
                    val title = item.selectFirst(".title, h2")?.text()?.trim() ?: a.text().trim()
                    val imgEl = item.selectFirst("img")
                    val rawImg = (imgEl?.attr("src") ?: "").ifBlank { imgEl?.attr("data-src") ?: "" }
                    val img = if (rawImg.startsWith("http")) rawImg else if (rawImg.isNotBlank()) "$base/${rawImg.trimStart('/')}" else null
                    val ep = item.selectFirst(".epx, .episode")?.text()?.trim()

                    if (title.isNotBlank() && href.isNotBlank()) {
                        results.add(
                            Anime(
                                title = title,
                                url = href,
                                posterUrl = img,
                                provider = name,
                                status = ep ?: "Ongoing"
                            )
                        )
                    }
                }
                if (results.isNotEmpty()) break
            } catch (_: Exception) {}
        }
        results
    }
}
