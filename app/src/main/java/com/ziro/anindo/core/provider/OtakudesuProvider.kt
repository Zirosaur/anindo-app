package com.ziro.anindo.core.provider

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
        val doc = Jsoup.parse(html, base)
        val results = mutableListOf<Anime>()

        val items = doc.select("ul.chivsrc li, .page .venser .page li, .chivsrc li")
        for (item in items) {
            val a = item.selectFirst("h2 a, a") ?: continue
            val rawHref = a.attr("href").ifBlank { a.attr("abs:href") }
            val href = if (rawHref.startsWith("http")) rawHref else "$base/${rawHref.trimStart('/')}"
            val title = a.text().trim()
            val imgEl = item.selectFirst("img")
            val rawImg = (imgEl?.attr("src") ?: "").ifBlank { imgEl?.attr("data-src") ?: "" }
            val img = if (rawImg.startsWith("http")) rawImg else if (rawImg.isNotBlank()) "$base/${rawImg.trimStart('/')}" else null
            val status = item.selectFirst(".set:contains(Status)")?.text()?.replace("Status :", "")?.trim()

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
        val doc = Jsoup.parse(html, base)
        val episodes = mutableListOf<Episode>()

        // Otakudesu uses .episodelist, .venser, or direct episode links
        val links = doc.select(".episodelist ul li a, .venser ul li a, div[class*='episode'] a, a[href*='/episode/']")
        val seenUrls = mutableSetOf<String>()

        for (link in links) {
            val rawHref = link.attr("href").ifBlank { link.attr("abs:href") }
            val href = if (rawHref.startsWith("http")) rawHref else "$base/${rawHref.trimStart('/')}"
            val rawTitle = link.text().trim()

            // Filter out duplicates and non-episode divider links (e.g. pembatas)
            if (!href.contains("/episode/") || seenUrls.contains(href) ||
                rawTitle.contains("pembatas", ignoreCase = true) || href.contains("pembatas", ignoreCase = true)
            ) {
                continue
            }
            seenUrls.add(href)

            val title = if (rawTitle.isNotBlank()) {
                rawTitle.replace("Subtitle Indonesia", "").replace("Sub Indo", "").trim()
            } else {
                "Episode"
            }

            // Parse episode number
            val numMatch = Regex("""(?i)(?:episode|ep)\s*(\d+(?:\.\d+)?)""").find(rawTitle)
                ?: Regex("""(?i)episode-(\d+(?:\.\d+)?)""").find(href)
            val epNum = numMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 1f

            episodes.add(
                Episode(
                    title = if (title.isBlank()) "Episode $epNum" else title,
                    url = href,
                    epNum = epNum
                )
            )
        }
        // Otakudesu defaults to newest first; sort ascending so Episode 1 is on top
        episodes.sortedBy { it.epNum }
    }

    override suspend fun extractStreams(episode: Episode): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val html = NetworkClient.get(episode.url, referer = base)
        val doc = Jsoup.parse(html, base)
        val candidates = mutableListOf<StreamCandidate>()

        // 1. Check direct iframe embeds (desustream, odcdn, putarin, etc.)
        val iframes = doc.select(".responsive-embed-stream iframe, .player-embed iframe, iframe[src*='desu'], iframe[src*='stream'], iframe[src*='putarin']")
        for (iframe in iframes) {
            val rawSrc = iframe.attr("src").ifBlank { iframe.attr("abs:src") }
            val src = if (rawSrc.startsWith("//")) "https:$rawSrc" else rawSrc
            if (src.isNotBlank()) {
                val isPutarin = src.contains("putarin")
                val isDesu = src.contains("desustream") || src.contains("odcdn")
                val isHls = src.contains("m3u8") || isPutarin
                val srvName = when {
                    isPutarin -> "Putarin (HLS)"
                    isDesu -> "ODCloud / Desustream (HD)"
                    src.contains("yourupload") -> "YourUpload"
                    else -> "Stream VIP"
                }

                candidates.add(
                    StreamCandidate(
                        server = srvName,
                        quality = "720p",
                        isHls = isHls,
                        resolve = {
                            when {
                                isPutarin -> {
                                    val resolved = PutarinDecryptor.decrypt(src)
                                    StreamResult(url = resolved ?: src, referer = episode.url)
                                }
                                isDesu -> {
                                    try {
                                        val ifrHtml = NetworkClient.get(src, referer = episode.url)
                                        val vMatch = Regex("""videoURL\s*=\s*["']([^"']+)["']""").find(ifrHtml)
                                        if (vMatch != null) {
                                            StreamResult(url = vMatch.groupValues[1], referer = src)
                                        } else {
                                            StreamResult(url = src, referer = episode.url)
                                        }
                                    } catch (e: Exception) {
                                        StreamResult(url = src, referer = episode.url)
                                    }
                                }
                                else -> {
                                    StreamResult(url = src, referer = episode.url)
                                }
                            }
                        }
                    )
                )
            }
        }

        // 2. Download section mirrors (Filedon, Pixeldrain, etc.)
        val downloadItems = doc.select(".download ul li")
        for (item in downloadItems) {
            val qText = item.selectFirst("strong")?.text()?.trim() ?: "720p"
            val links = item.select("a")
            for (link in links) {
                val hostName = link.text().trim()
                val href = link.attr("href").ifBlank { link.attr("abs:href") }
                if (href.isNotBlank() && (hostName.contains("Filedon", ignoreCase = true) || hostName.contains("Pdrain", ignoreCase = true) || hostName.contains("Pixeldrain", ignoreCase = true))) {
                    candidates.add(
                        StreamCandidate(
                            server = hostName,
                            quality = qText,
                            isHls = false,
                            resolve = {
                                StreamResult(url = href, referer = episode.url)
                            }
                        )
                    )
                }
            }
        }

        // 3. Mirror stream options
        val mirrorLinks = doc.select(".mirrorstream ul li a")
        for (link in mirrorLinks) {
            val dataContent = link.attr("data-content")
            val serverName = link.text().trim()
            if (dataContent.isNotBlank()) {
                candidates.add(
                    StreamCandidate(
                        server = serverName,
                        quality = "Mirror",
                        isHls = false,
                        resolve = {
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
        val doc = Jsoup.parse(html, base)
        val results = mutableListOf<Anime>()

        val items = doc.select(".venz ul li, .rapi ul li, .venz li, .rapi li")
        for (item in items) {
            val a = item.selectFirst(".thumb a, h2 a, a") ?: continue
            val rawHref = a.attr("href").ifBlank { a.attr("abs:href") }
            val href = if (rawHref.startsWith("http")) rawHref else "$base/${rawHref.trimStart('/')}"
            val title = item.selectFirst(".jdlflm, h2, .title")?.text()?.trim() ?: a.text().trim()
            val imgEl = item.selectFirst(".thumb img, img")
            val rawImg = (imgEl?.attr("src") ?: "").ifBlank { imgEl?.attr("data-src") ?: "" }
            val img = if (rawImg.startsWith("http")) rawImg else if (rawImg.isNotBlank()) "$base/${rawImg.trimStart('/')}" else null
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
