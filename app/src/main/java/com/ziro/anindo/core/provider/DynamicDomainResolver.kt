package com.ziro.anindo.core.provider

import com.ziro.anindo.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object DynamicDomainResolver {
    private const val REMOTE_DOMAINS_URL = "https://raw.githubusercontent.com/Zirosaur/anindo/main/domains.json"

    private val defaultDomains = mapOf(
        "otakudesu" to listOf(
            "https://otakudesu.blog",
            "https://otakudesu.cloud",
            "https://otakudesu.wiki"
        ),
        "nontonanime" to listOf(
            "https://ww2.nontonanime.co",
            "https://nontonanime.co",
            "https://nontonanime.site"
        )
    )

    private val domainCache = ConcurrentHashMap<String, String>()

    suspend fun resolve(provider: String, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val key = provider.lowercase()
        if (!forceRefresh && domainCache.containsKey(key)) {
            return@withContext domainCache[key]!!
        }

        val candidates = mutableListOf<String>()

        // 1. Try remote domains.json from GitHub
        try {
            val jsonStr = NetworkClient.get(REMOTE_DOMAINS_URL)
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray(key)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i).trim().trimEnd('/')
                    if (u.isNotBlank() && !candidates.contains(u)) {
                        candidates.add(u)
                    }
                }
            } else {
                val str = json.optString(key).trim().trimEnd('/')
                if (str.isNotBlank() && !candidates.contains(str)) {
                    candidates.add(str)
                }
            }
        } catch (e: Exception) {}

        // Add built-in defaults
        defaultDomains[key]?.forEach { d ->
            val clean = d.trim().trimEnd('/')
            if (!candidates.contains(clean)) {
                candidates.add(clean)
            }
        }

        // 2. Probe candidates in order and return the first responding one
        for (cand in candidates) {
            if (probeDomain(cand)) {
                domainCache[key] = cand
                return@withContext cand
            }
        }

        // 3. Absolute fallback
        val fallback = defaultDomains[key]?.firstOrNull() ?: "https://otakudesu.blog"
        domainCache[key] = fallback
        fallback
    }

    private fun probeDomain(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        return try {
            val req = Request.Builder()
                .url(candidate)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .build()

            NetworkClient.client.newCall(req).execute().use { resp ->
                resp.isSuccessful || resp.code in 300..399
            }
        } catch (e: Exception) {
            false
        }
    }
}
