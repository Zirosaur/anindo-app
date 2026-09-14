package com.ziro.anindo.core.provider

import android.util.Log
import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamCandidate
import com.ziro.anindo.core.model.StreamResult
import com.ziro.anindo.core.network.NetworkClient
import com.ziro.anindo.core.provider.decryptor.PutarinDecryptor
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
        val seenUrls = mutableSetOf<String>()

        val links = doc.select(".episodelist ul li a, ul.episodes li a, .episodes a, .eplister ul li a, a[href*='/nonton/'], a[href*='/episode/']")
        for (link in links) {
            val rawHref = link.attr("href").ifBlank { link.attr("abs:href") }
            val href = if (rawHref.startsWith("http")) rawHref else "$base/${rawHref.trimStart('/')}"
            val title = link.text().trim()
            if (href.isNotBlank() && !seenUrls.contains(href) && (href.contains("/nonton/") || href.contains("/episode/"))) {
                seenUrls.add(href)
                val numMatch = Regex("""\b(?:episode|ep)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(title)
                    ?: Regex("""episode-(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(href)
                val epNum = numMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 1.0f
                val cleanTitle = if (title.isNotBlank()) title else "Episode $epNum"
                episodes.add(Episode(title = cleanTitle, epNum = epNum, url = href))
            }
        }
        episodes.sortedBy { it.epNum }
    }

    override suspend fun extractStreams(episode: Episode): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val html = NetworkClient.get(episode.url, referer = base)
        val doc = Jsoup.parse(html, base)
        val candidates = mutableListOf<StreamCandidate>()

        val iframes = doc.select(".video-content iframe, iframe[src*='putarin'], iframe[src*='puterin'], iframe[src*='filedon'], .player-embed iframe, iframe")
        Log.d("AnindoStream", "NontonAnime found ${iframes.size} iframes on ${episode.url}")

        for (iframe in iframes) {
            val rawSrc = iframe.attr("src").ifBlank { iframe.attr("abs:src") }
            val src = if (rawSrc.startsWith("//")) "https:$rawSrc" else rawSrc
            if (src.isNotBlank() && (src.contains("putarin") || src.contains("puterin") || src.contains("stream") || src.contains("filedon"))) {
                val srvName = when {
                    src.contains("putarin") || src.contains("puterin") -> "Putarin (HLS)"
                    src.contains("filedon") -> "Filedon (HD)"
                    else -> "Stream Server"
                }
                candidates.add(
                    StreamCandidate(
                        server = srvName,
                        quality = "Adaptive HD",
                        isHls = src.contains("putarin") || src.contains("puterin") || src.contains("m3u8"),
                        resolve = {
                            withContext(Dispatchers.IO) {
                                try {
                                    if (src.contains("putarin") || src.contains("puterin")) {
                                        val resolved = PutarinDecryptor.decrypt(src)
                                        if (resolved != null && (resolved.contains(".m3u8") || resolved.contains(".mp4") || !resolved.contains("/embed/"))) {
                                            StreamResult(url = resolved, referer = episode.url, isHls = true)
                                        } else null
                                    } else null
                                } catch (e: Exception) {
                                    Log.e("AnindoStream", "Error resolving NontonAnime candidate $src", e)
                                    null
                                }
                            }
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
