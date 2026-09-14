package com.ziro.anindo.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(DohDns())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()


    fun get(url: String, referer: String? = null, headers: Map<String, String> = emptyMap()): String {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)

        if (referer != null) {
            reqBuilder.header("Referer", referer)
        }

        headers.forEach { (k, v) ->
            reqBuilder.header(k, v)
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP error code: ${response.code} for URL: $url")
            }
            return response.body?.string() ?: ""
        }
    }
}
