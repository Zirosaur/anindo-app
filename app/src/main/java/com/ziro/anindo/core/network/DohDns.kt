package com.ziro.anindo.core.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS-over-HTTPS (DoH) Resolver for bypassing Indonesian ISP DNS Poisoning / Censorship (Trust Positif/Nawala).
 * Uses Cloudflare (1.1.1.1) and Google (8.8.8.8) secure DNS endpoints directly via IP address.
 */
class DohDns : Dns {
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    // Lightweight bootstrap OkHttpClient configured with system DNS to query DoH IP endpoints
    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        // Direct IP addresses or localhost do not require DoH
        if (isIpAddress(hostname) || hostname == "localhost") {
            return Dns.SYSTEM.lookup(hostname)
        }

        // Check in-memory DNS cache
        cache[hostname]?.let { return it }

        val providerMode = try {
            com.ziro.anindo.AnindoApp.instance.settingsManager.dohProvider.value
        } catch (_: Throwable) {
            "cloudflare"
        }

        if (providerMode == "system") {
            val systemResult = Dns.SYSTEM.lookup(hostname)
            cache[hostname] = systemResult
            return systemResult
        }

        if (providerMode == "google") {
            try {
                val ips = queryGoogleDoh("https://8.8.8.8/resolve?name=$hostname&type=A")
                if (ips.isNotEmpty()) {
                    cache[hostname] = ips
                    return ips
                }
            } catch (_: Exception) {}
        } else {
            // 1. Query Cloudflare DoH (Direct IP 1.1.1.1)
            try {
                val ips = queryDoh("https://1.1.1.1/dns-query?name=$hostname&type=A")
                if (ips.isNotEmpty()) {
                    cache[hostname] = ips
                    return ips
                }
            } catch (_: Exception) {}

            // 2. Query Cloudflare Secondary DoH (Direct IP 1.0.0.1)
            try {
                val ips = queryDoh("https://1.0.0.1/dns-query?name=$hostname&type=A")
                if (ips.isNotEmpty()) {
                    cache[hostname] = ips
                    return ips
                }
            } catch (_: Exception) {}
        }

        // Secondary fallback between DoH providers
        if (providerMode != "google") {
            try {
                val ips = queryGoogleDoh("https://8.8.8.8/resolve?name=$hostname&type=A")
                if (ips.isNotEmpty()) {
                    cache[hostname] = ips
                    return ips
                }
            } catch (_: Exception) {}
        } else {
            try {
                val ips = queryDoh("https://1.1.1.1/dns-query?name=$hostname&type=A")
                if (ips.isNotEmpty()) {
                    cache[hostname] = ips
                    return ips
                }
            } catch (_: Exception) {}
        }

        // 4. Fallback to System DNS
        val systemResult = Dns.SYSTEM.lookup(hostname)
        cache[hostname] = systemResult
        return systemResult
    }

    private fun queryDoh(url: String): List<InetAddress> {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .header("User-Agent", NetworkClient.USER_AGENT)
            .build()

        bootstrapClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val bodyStr = resp.body?.string() ?: return emptyList()
            val json = JSONObject(bodyStr)
            val answers = json.optJSONArray("Answer") ?: return emptyList()
            val addresses = mutableListOf<InetAddress>()
            for (i in 0 until answers.length()) {
                val obj = answers.getJSONObject(i)
                if (obj.optInt("type") == 1) { // Type 1: A record (IPv4)
                    val ipStr = obj.optString("data").trim()
                    if (isIpAddress(ipStr)) {
                        try {
                            addresses.add(InetAddress.getByName(ipStr))
                        } catch (_: Exception) {}
                    }
                }
            }
            return addresses
        }
    }

    private fun queryGoogleDoh(url: String): List<InetAddress> {
        return queryDoh(url)
    }

    private fun isIpAddress(host: String): Boolean {
        return host.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$"))
    }
}
