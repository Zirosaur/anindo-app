package com.ziro.anindo.core.provider

import android.util.Base64
import com.ziro.anindo.core.model.Anime
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.model.StreamCandidate
import com.ziro.anindo.core.model.StreamResult
import com.ziro.anindo.core.network.NetworkClient
import com.ziro.anindo.core.provider.decryptor.PutarinDecryptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
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

        // 1. Direct iframe embeds in current episode page
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
                    else -> "Stream Server Utama"
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
                                    if (resolved != null && isDirectMediaUrl(resolved)) {
                                        StreamResult(url = resolved, referer = episode.url)
                                    } else null
                                }
                                isDesu -> {
                                    try {
                                        val ifrHtml = NetworkClient.get(src, referer = episode.url)
                                        val direct = extractDirectMediaFromHtml(ifrHtml)
                                        if (direct != null) {
                                            StreamResult(url = direct, referer = src)
                                        } else null
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                else -> {
                                    try {
                                        val ifrHtml = NetworkClient.get(src, referer = episode.url)
                                        val direct = extractDirectMediaFromHtml(ifrHtml)
                                        if (direct != null) {
                                            StreamResult(url = direct, referer = src)
                                        } else null
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                        }
                    )
                )
            }
        }

        // 2. Mirrorstream AJAX embeds (ODStream, Ondesu HD, Archive/Arcg, etc.)
        val mirrorLinks = doc.select(".mirrorstream ul li a")
        if (mirrorLinks.isNotEmpty()) {
            val nonceActionMatch = Regex("""data\s*:\s*\{\s*action\s*:\s*["']([a-f0-9]{32})["']""").find(html)
            val allActions = Regex("""action\s*:\s*["']([a-f0-9]{32})["']""").findAll(html).map { it.groupValues[1] }.toList()
            val resolveAction = allActions.firstOrNull { it != nonceActionMatch?.groupValues?.get(1) } ?: "2a3505c93b0035d3f455df82bf976b84"
            val nonceAction = nonceActionMatch?.groupValues?.get(1) ?: "aa1208d27f29ca340c92c66d1926f13f"

            for (link in mirrorLinks) {
                val dataContent = link.attr("data-content")
                val serverName = link.text().trim()
                if (dataContent.isNotBlank()) {
                    candidates.add(
                        StreamCandidate(
                            server = "Mirror $serverName",
                            quality = "720p",
                            isHls = false,
                            resolve = {
                                resolveMirrorStream(
                                    base = base,
                                    referer = episode.url,
                                    dataContentB64 = dataContent,
                                    nonceAction = nonceAction,
                                    resolveAction = resolveAction
                                )
                            }
                        )
                    )
                }
            }
        }

        // 3. Download Section: Pixeldrain / Pdrain Direct Video Stream
        val downloadItems = doc.select(".download ul li")
        for (item in downloadItems) {
            val qText = item.selectFirst("strong")?.text()?.trim() ?: "720p"
            val links = item.select("a")
            for (link in links) {
                val hostName = link.text().trim()
                val href = link.attr("href").ifBlank { link.attr("abs:href") }
                if (href.isNotBlank() && (hostName.contains("Pdrain", ignoreCase = true) || hostName.contains("Pixeldrain", ignoreCase = true))) {
                    candidates.add(
                        StreamCandidate(
                            server = "Pixeldrain ($qText)",
                            quality = qText,
                            isHls = false,
                            resolve = {
                                val direct = resolvePixeldrain(href)
                                if (direct != null) {
                                    StreamResult(url = direct, referer = "")
                                } else null
                            }
                        )
                    )
                }
            }
        }

        candidates
    }

    private fun resolveMirrorStream(
        base: String,
        referer: String,
        dataContentB64: String,
        nonceAction: String,
        resolveAction: String
    ): StreamResult? {
        return try {
            val ajaxUrl = "${base.trimEnd('/')}/wp-admin/admin-ajax.php"

            // 1. Fetch nonce
            val nonceReqBody = FormBody.Builder()
                .add("action", nonceAction)
                .build()
            val nonceReq = Request.Builder()
                .url(ajaxUrl)
                .post(nonceReqBody)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val nonceResStr = NetworkClient.client.newCall(nonceReq).execute().use { it.body?.string() ?: "" }
            val nonce = JSONObject(nonceResStr).optString("data", "")
            if (nonce.isBlank()) return null

            // 2. Decode payload JSON
            val decodedJson = String(Base64.decode(dataContentB64, Base64.DEFAULT), StandardCharsets.UTF_8)
            val payloadObj = JSONObject(decodedJson)

            val formBuilder = FormBody.Builder()
                .add("id", payloadObj.optString("id"))
                .add("i", payloadObj.optString("i"))
                .add("q", payloadObj.optString("q"))
                .add("nonce", nonce)
                .add("action", resolveAction)

            val resolveReq = Request.Builder()
                .url(ajaxUrl)
                .post(formBuilder.build())
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val resolveResStr = NetworkClient.client.newCall(resolveReq).execute().use { it.body?.string() ?: "" }
            val b64Html = JSONObject(resolveResStr).optString("data", "")
            if (b64Html.isBlank()) return null

            val embedHtml = String(Base64.decode(b64Html, Base64.DEFAULT), StandardCharsets.UTF_8)
            val ifrMatch = Regex("""src=["']([^"']+)["']""").find(embedHtml)
            val ifrSrc = ifrMatch?.groupValues?.get(1) ?: return null

            // 3. Fetch iframe and extract direct video
            val ifrHtml = NetworkClient.get(ifrSrc, referer = referer)
            val directMedia = extractDirectMediaFromHtml(ifrHtml)
            if (directMedia != null) {
                StreamResult(url = directMedia, referer = ifrSrc)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolvePixeldrain(shortlink: String): String? {
        return try {
            val req = Request.Builder()
                .url(shortlink)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .build()

            val noRedirectClient = NetworkClient.client.newBuilder()
                .followRedirects(false)
                .build()

            val loc = noRedirectClient.newCall(req).execute().use { resp ->
                resp.header("Location")
            }

            if (!loc.isNullOrBlank() && loc.contains("pixeldrain.com/u/")) {
                val fileId = loc.substringAfter("/u/").substringBefore("?").substringBefore("#").trim()
                if (fileId.isNotBlank()) {
                    "https://pixeldrain.com/api/file/$fileId"
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDirectMediaFromHtml(html: String): String? {
        // 1. videoURL = "https://..."
        val vMatch = Regex("""videoURL\s*=\s*["']([^"']+)["']""").find(html)
        if (vMatch != null && isDirectMediaUrl(vMatch.groupValues[1])) {
            return vMatch.groupValues[1]
        }
        // 2. file: "https://..." (playerjs / archive.org)
        val fileMatch = Regex("""(?:file|sources?)\s*:\s*["']([^"']+)["']""").find(html)
        if (fileMatch != null && isDirectMediaUrl(fileMatch.groupValues[1])) {
            return fileMatch.groupValues[1]
        }
        // 3. <source src="https://..."> (blogger / googlevideo / ondesu)
        val sourceMatch = Regex("""<source[^>]+src=["']([^"']+)["']""").find(html)
        if (sourceMatch != null && isDirectMediaUrl(sourceMatch.groupValues[1])) {
            return sourceMatch.groupValues[1]
        }
        // 4. Any direct mp4/m3u8 url
        val directMatch = Regex("""["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""").find(html)
        if (directMatch != null && isDirectMediaUrl(directMatch.groupValues[1])) {
            return directMatch.groupValues[1]
        }
        return null
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        if (!clean.startsWith("http")) return false
        if (clean.contains("/embed/") || clean.contains("desustream.net/dstream/") || clean.contains("link.desustream.com")) return false
        return clean.contains(".mp4") || clean.contains(".m3u8") ||
                clean.contains("googlevideo.com") || clean.contains("archive.org") ||
                clean.contains("odcloud.net") || clean.contains("pixeldrain.com/api/file")
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
