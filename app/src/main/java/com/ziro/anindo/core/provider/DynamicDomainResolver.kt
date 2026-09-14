package com.ziro.anindo.core.provider

import com.ziro.anindo.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object DynamicDomainResolver {
    private const val REMOTE_DOMAINS_URL = "https://raw.githubusercontent.com/Zirosaur/anindo/main/domains.json"

    private val defaultDomains = mapOf(
        "otakudesu" to "https://otakudesu.blog",
        "nontonanime" to "https://ww2.nontonanime.co"
    )

    private val domainCache = mutableMapOf<String, String>()

    suspend fun resolve(provider: String, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!forceRefresh && domainCache.containsKey(provider)) {
            return@withContext domainCache[provider]!!
        }

        // 1. Try remote domains.json from GitHub
        try {
            val jsonStr = NetworkClient.get(REMOTE_DOMAINS_URL)
            val json = JSONObject(jsonStr)
            if (json.has(provider)) {
                val candidate = json.getString(provider).trim().trimEnd('/')
                val verified = probeFollowRedirect(candidate)
                domainCache[provider] = verified
                return@withContext verified
            }
        } catch (_: Exception) {
            // Fallback to default
        }

        // 2. Probe default domain
        val fallback = defaultDomains[provider] ?: ""
        val resolved = probeFollowRedirect(fallback)
        domainCache[provider] = resolved
        resolved
    }

    private fun probeFollowRedirect(candidate: String): String {
        if (candidate.isBlank()) return candidate
        return try {
            val req = Request.Builder().url(candidate).head().build()
            NetworkClient.client.newCall(req).execute().use { response ->
                val finalUrl = response.request.url.toString()
                finalUrl.trimEnd('/')
            }
        } catch (_: Exception) {
            candidate.trimEnd('/')
        }
    }
}
