package com.ziro.anindo.ui.navigation

import android.util.Base64

sealed class Screen(val route: String, val title: String) {
    object Library : Screen("library", "Koleksi")
    object Ongoing : Screen("ongoing", "Ongoing")
    object Explore : Screen("explore", "Jelajah")
    object History : Screen("history", "Riwayat")
    object Settings : Screen("settings", "Pengaturan")

    object Details : Screen(
        "details/{animeUrlB64}?animeId={animeId}&title={title}&poster={poster}&provider={provider}",
        "Detail"
    ) {
        fun createRoute(
            animeUrl: String,
            animeId: String = "",
            title: String = "",
            poster: String = "",
            provider: String = "otakudesu"
        ): String {
            val encUrl = encodeParam(animeUrl)
            val encId = encodeParam(animeId)
            val encTitle = encodeParam(title)
            val encPoster = encodeParam(poster)
            val encProvider = encodeParam(provider)
            return "details/$encUrl?animeId=$encId&title=$encTitle&poster=$encPoster&provider=$encProvider"
        }
    }

    object Player : Screen(
        "player/{streamUrlB64}/{titleB64}?animeId={animeId}&animeTitle={animeTitle}&poster={poster}&epUrl={epUrl}&referer={referer}",
        "Pemutar"
    ) {
        fun createRoute(
            streamUrl: String,
            title: String,
            animeId: String = "",
            animeTitle: String = "",
            poster: String = "",
            epUrl: String = "",
            referer: String = ""
        ): String {
            val encStream = encodeParam(streamUrl)
            val encTitle = encodeParam(title)
            val encId = encodeParam(animeId)
            val encAnimeTitle = encodeParam(animeTitle)
            val encPoster = encodeParam(poster)
            val encEp = encodeParam(epUrl)
            val encReferer = encodeParam(referer)
            return "player/$encStream/$encTitle?animeId=$encId&animeTitle=$encAnimeTitle&poster=$encPoster&epUrl=$encEp&referer=$encReferer"
        }
    }

    companion object {
        fun encodeParam(value: String): String {
            if (value.isBlank()) return ""
            return try {
                Base64.encodeToString(
                    value.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            } catch (e: Throwable) {
                try {
                    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
                } catch (e2: Throwable) {
                    java.net.URLEncoder.encode(value, "UTF-8")
                }
            }
        }

        fun decodeParam(encoded: String): String {
            if (encoded.isBlank()) return ""
            return try {
                String(
                    Base64.decode(
                        encoded,
                        Base64.URL_SAFE or Base64.NO_WRAP
                    ),
                    Charsets.UTF_8
                )
            } catch (e: Throwable) {
                try {
                    String(java.util.Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
                } catch (e2: Throwable) {
                    try {
                        java.net.URLDecoder.decode(encoded, "UTF-8")
                    } catch (e3: Throwable) {
                        encoded
                    }
                }
            }
        }
    }
}
