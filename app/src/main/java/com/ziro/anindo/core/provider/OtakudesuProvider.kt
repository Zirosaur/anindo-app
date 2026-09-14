package com.ziro.anindo.core.provider

import android.util.Base64
import android.util.Log
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
import org.jsoup.parser.Parser
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

        val links = doc.select(".episodelist ul li a, .venser ul li a, div[class*='episode'] a, a[href*='/episode/']")
        val seenUrls = mutableSetOf<String>()

        for (link in links) {
            val rawHref = link.attr("href").ifBlank { link.attr("abs:href") }
            val href = if (rawHref.startsWith("http")) rawHref else "$base/${rawHref.trimStart('/')}"
            val rawTitle = link.text().trim()

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
        episodes.sortedBy { it.epNum }
    }

    override suspend fun extractStreams(episode: Episode): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val base = getBaseUrl()
        val html = NetworkClient.get(episode.url, referer = base)
        val doc = Jsoup.parse(html, base)
        val candidates = mutableListOf<StreamCandidate>()

        // 1. Download section mirrors (Filedon & Pixeldrain) - proven fastest & most reliable in anindo CLI
        val downloadItems = doc.select(".download ul li")
        for (item in downloadItems) {
            val qText = item.selectFirst("strong")?.text()?.trim() ?: "720p"
            val links = item.select("a")
            for (link in links) {
                val hostName = link.text().trim()
                val href = link.attr("href").ifBlank { link.attr("abs:href") }
                if (href.isNotBlank()) {
                    if (hostName.contains("Filedon", ignoreCase = true)) {
                        candidates.add(
                            StreamCandidate(
                                server = "Filedon ($qText)",
                                quality = qText,
                                isHls = false,
                                resolve = { resolveFiledon(href) }
                            )
                        )
                    } else if (hostName.contains("Pdrain", ignoreCase = true) || hostName.contains("Pixeldrain", ignoreCase = true)) {
                        candidates.add(
                            StreamCandidate(
                                server = "Pixeldrain ($qText)",
                                quality = qText,
                                isHls = false,
                                resolve = { resolvePixeldrain(href) }
                            )
                        )
                    }
                }
            }
        }

        // 2. Direct iframe embeds in current episode page (desustream, odcdn, putarin)
        val iframes = doc.select(".responsive-embed-stream iframe, .player-embed iframe, iframe[src*='desu'], iframe[src*='stream'], iframe[src*='putarin']")
        for (iframe in iframes) {
            val rawSrc = iframe.attr("src").ifBlank { iframe.attr("abs:src") }
            val src = if (rawSrc.startsWith("//")) "https:$rawSrc" else rawSrc
            if (src.isNotBlank()) {
                val isPutarin = src.contains("putarin")
                val isDesu = src.contains("desustream") || src.contains("odcdn")
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
                        isHls = isPutarin || src.contains("m3u8"),
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

        // 3. Mirrorstream section (YourUpload, Filedon, OndesuHD, ODStream)
        val actions = Regex("""action\s*:\s*["']([a-f0-9]{32})["']""").findAll(html).map { it.groupValues[1] }.toList()
        val mirrorLinks = doc.select(".mirrorstream ul li a")
        for (link in mirrorLinks) {
            val dataContent = link.attr("data-content")
            val serverName = link.text().trim()
            if (dataContent.isNotBlank()) {
                candidates.add(
                    StreamCandidate(
                        server = "$serverName (Mirror)",
                        quality = "720p",
                        isHls = false,
                        resolve = {
                            resolveMirrorStream(
                                base = base,
                                epUrl = episode.url,
                                dataB64 = dataContent,
                                mirrorName = serverName,
                                actions = actions
                            )
                        }
                    )
                )
            }
        }

        // Sort candidates: prioritize 720p Filedon, 1080p Filedon, 480p Filedon, ODCloud, Mirrors, Pixeldrain
        candidates.sortWith(
            compareBy(
                {
                    val s = it.server.lowercase()
                    when {
                        s.contains("filedon") && s.contains("720p") -> 0
                        s.contains("filedon") && s.contains("1080p") -> 1
                        s.contains("filedon") && s.contains("480p") -> 2
                        s.contains("filedon") -> 3
                        s.contains("odcloud") -> 4
                        s.contains("yourupload") -> 5
                        s.contains("mirror") -> 6
                        s.contains("pixeldrain") -> 7
                        else -> 8
                    }
                }
            )
        )
        candidates
    }

    private suspend fun curlRedirect(url: String, timeoutSec: Int = 5): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .build()

            val noRedirectClient = NetworkClient.client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            noRedirectClient.newCall(req).execute().use { resp ->
                val loc = resp.header("Location") ?: resp.header("location")
                if (!loc.isNullOrBlank()) {
                    loc
                } else if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val m = Regex("""data-page="([^"]+)"""").find(body)
                    if (m != null) {
                        val unescaped = Parser.unescapeEntities(m.groupValues[1], false)
                        JSONObject(unescaped).optJSONObject("props")?.optString("embed_url")
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e("AnindoStream", "curlRedirect error for $url: ${e.message}")
            null
        }
    }

    private suspend fun resolveFiledon(rawHref: String): StreamResult? = withContext(Dispatchers.IO) {
        try {
            Log.d("AnindoStream", "Resolving Filedon for: $rawHref")
            var embedUrl: String? = null
            if (rawHref.contains("/embed/")) {
                embedUrl = rawHref
            } else if (rawHref.contains("/view/")) {
                embedUrl = rawHref.replace("/view/", "/embed/")
            } else {
                val loc = curlRedirect(rawHref, 5)
                Log.d("AnindoStream", "curlRedirect result: $loc")
                if (!loc.isNullOrBlank()) {
                    embedUrl = loc.replace("/view/", "/embed/")
                }
            }

            // Fetch embed or rawHref directly
            val targetUrl = embedUrl ?: rawHref
            val page = NetworkClient.get(targetUrl)

            // 1. Try extracting direct R2 URL from data-page JSON
            val m = Regex("""data-page="([^"]+)"""").find(page)
            if (m != null) {
                val unescaped = Parser.unescapeEntities(m.groupValues[1], false)
                val json = JSONObject(unescaped)
                val props = json.optJSONObject("props")
                var r2Url = props?.optString("url")
                // If on /view/ page, props contains embed_url instead
                if (r2Url.isNullOrBlank() && props?.has("embed_url") == true) {
                    val secondaryEmbed = props.optString("embed_url")
                    if (secondaryEmbed.isNotBlank()) {
                        Log.d("AnindoStream", "Fetching secondary embed from props: $secondaryEmbed")
                        val secondaryPage = NetworkClient.get(secondaryEmbed)
                        val m2 = Regex("""data-page="([^"]+)"""").find(secondaryPage)
                        if (m2 != null) {
                            val unescaped2 = Parser.unescapeEntities(m2.groupValues[1], false)
                            r2Url = JSONObject(unescaped2).optJSONObject("props")?.optString("url")
                        }
                    }
                }

                if (!r2Url.isNullOrBlank() && isDirectMediaUrl(r2Url)) {
                    Log.d("AnindoStream", "Filedon R2 resolved: ${r2Url.take(60)}...")
                    return@withContext StreamResult(url = r2Url, referer = "")
                }
            }

            // 2. Direct regex search for cloudflarestorage.com or direct mp4 inside page
            val directR2Match = Regex("""["'](https?://[^"']*?r2\.cloudflarestorage\.com[^"']*)["']""").find(page)
                ?: Regex("""["'](https?://[^"']*?filedon\.co[^"']*?\.mp4[^"']*)["']""").find(page)
            if (directR2Match != null) {
                val foundUrl = Parser.unescapeEntities(directR2Match.groupValues[1], false)
                Log.d("AnindoStream", "Filedon direct regex found: ${foundUrl.take(60)}...")
                return@withContext StreamResult(url = foundUrl, referer = "")
            }

            Log.w("AnindoStream", "Failed to find direct media in Filedon page for $rawHref")
            null
        } catch (e: Exception) {
            Log.e("AnindoStream", "Exception resolving Filedon: ${e.message}", e)
            null
        }
    }

    private suspend fun resolveYourUpload(embedUrl: String): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val mId = Regex("""id=([A-Za-z0-9_-]+)""").find(embedUrl)
                ?: Regex("""/embed/([A-Za-z0-9_-]+)""").find(embedUrl)
            val yuId = mId?.groupValues?.get(1) ?: return@withContext null
            val page = NetworkClient.get("https://yourupload.com/embed/$yuId", referer = "https://desudrive.com/")
            val vMatch = Regex("""file\s*:\s*['"](https?://[^'"]+\.mp4[^'"]*)['"]""").find(page)
                ?: Regex("""property="og:video"\s+content="([^"]+)"""").find(page)
            if (vMatch != null && isDirectMediaUrl(vMatch.groupValues[1])) {
                StreamResult(url = vMatch.groupValues[1], referer = "https://yourupload.com/")
            } else null
        } catch (e: Exception) {
            Log.e("AnindoStream", "resolveYourUpload error: ${e.message}")
            null
        }
    }

    private suspend fun resolvePixeldrain(rawHref: String): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val loc = curlRedirect(rawHref, 5)
            if (!loc.isNullOrBlank() && loc.contains("pixeldrain.com/u/")) {
                val fileId = loc.substringAfter("/u/").substringBefore("/").substringBefore("?").substringBefore("#").trim()
                if (fileId.isNotBlank()) {
                    val directUrl = "https://pixeldrain.com/api/file/$fileId"
                    StreamResult(url = directUrl, referer = "")
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("AnindoStream", "resolvePixeldrain error: ${e.message}")
            null
        }
    }

    private suspend fun resolveMirrorStream(
        base: String,
        epUrl: String,
        dataB64: String,
        mirrorName: String,
        actions: List<String>
    ): StreamResult? = withContext(Dispatchers.IO) {
        if (actions.size < 2) return@withContext null
        try {
            val otakudesuBase = base
            val ajaxUrl = "${otakudesuBase.trimEnd('/')}/wp-admin/admin-ajax.php"

            // 1. In anindo CLI: actions[1] is nonce action
            val nonceAction = actions[1]
            val resolveAction = actions[0]

            val nonceReqBody = FormBody.Builder()
                .add("action", nonceAction)
                .build()
            val nonceReq = Request.Builder()
                .url(ajaxUrl)
                .post(nonceReqBody)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Referer", epUrl)
                .header("Origin", otakudesuBase)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val nonceResStr = NetworkClient.client.newCall(nonceReq).execute().use { it.body?.string() ?: "" }
            val nonce = JSONObject(nonceResStr).optString("data", "")
            if (nonce.isBlank()) {
                Log.w("AnindoStream", "Failed to get nonce for Otakudesu AJAX mirror")
                return@withContext null
            }

            // 2. Query mirror embed with actions[0]
            val decodedJson = String(Base64.decode(dataB64, Base64.DEFAULT), StandardCharsets.UTF_8)
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
                .header("Referer", epUrl)
                .header("Origin", otakudesuBase)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val resolveResStr = NetworkClient.client.newCall(resolveReq).execute().use { it.body?.string() ?: "" }
            val b64Html = JSONObject(resolveResStr).optString("data", "")
            if (b64Html.isBlank()) return@withContext null

            val embedHtml = String(Base64.decode(b64Html, Base64.DEFAULT), StandardCharsets.UTF_8)
            val srcMatch = Regex("""src=["']([^"']+)["']""").find(embedHtml) ?: return@withContext null
            val srcUrl = srcMatch.groupValues[1]

            val lowerName = mirrorName.lowercase()
            val lowerSrc = srcUrl.lowercase()

            when {
                lowerName.contains("yourupload") || lowerSrc.contains("yuplod") -> {
                    resolveYourUpload(srcUrl)
                }
                lowerName.contains("filedon") || lowerSrc.contains("filedon") -> {
                    resolveFiledon(srcUrl)
                }
                else -> {
                    val ifrHtml = NetworkClient.get(srcUrl, referer = epUrl)
                    val direct = extractDirectMediaFromHtml(ifrHtml)
                    if (direct != null) StreamResult(url = direct, referer = srcUrl) else null
                }
            }
        } catch (e: Exception) {
            Log.e("AnindoStream", "resolveMirrorStream error: ${e.message}", e)
            null
        }
    }

    private fun extractDirectMediaFromHtml(html: String): String? {
        val vMatch = Regex("""videoURL\s*=\s*["']([^"']+)["']""").find(html)
        if (vMatch != null && isDirectMediaUrl(vMatch.groupValues[1])) {
            return vMatch.groupValues[1]
        }
        val fileMatch = Regex("""(?:file|sources?)\s*:\s*["']([^"']+)["']""").find(html)
        if (fileMatch != null && isDirectMediaUrl(fileMatch.groupValues[1])) {
            return fileMatch.groupValues[1]
        }
        val sourceMatch = Regex("""<source[^>]+src=["']([^"']+)["']""").find(html)
        if (sourceMatch != null && isDirectMediaUrl(sourceMatch.groupValues[1])) {
            return sourceMatch.groupValues[1]
        }
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
                clean.contains("cloudflarestorage.com") || clean.contains("googlevideo.com") ||
                clean.contains("archive.org") || clean.contains("odcloud.net") ||
                clean.contains("pixeldrain.com/api/file") || clean.contains("yourupload.com") ||
                clean.contains("/api/hls") || clean.contains("putarin")
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
