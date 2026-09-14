package com.ziro.anindo.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String, val title: String) {
    object Library : Screen("library", "Koleksi")
    object Ongoing : Screen("ongoing", "Ongoing")
    object Explore : Screen("explore", "Jelajah")
    object History : Screen("history", "Riwayat")

    object Details : Screen(
        "details/{animeUrl}?animeId={animeId}&title={title}&poster={poster}&provider={provider}",
        "Detail"
    ) {
        fun createRoute(
            animeUrl: String,
            animeId: String = "",
            title: String = "",
            poster: String = "",
            provider: String = "otakudesu"
        ): String {
            val encUrl = URLEncoder.encode(animeUrl, "UTF-8")
            val encId = URLEncoder.encode(animeId, "UTF-8")
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encPoster = URLEncoder.encode(poster, "UTF-8")
            val encProvider = URLEncoder.encode(provider, "UTF-8")
            return "details/$encUrl?animeId=$encId&title=$encTitle&poster=$encPoster&provider=$encProvider"
        }
    }

    object Player : Screen(
        "player/{streamUrl}/{title}?animeId={animeId}&animeTitle={animeTitle}&poster={poster}&epUrl={epUrl}",
        "Pemutar"
    ) {
        fun createRoute(
            streamUrl: String,
            title: String,
            animeId: String = "",
            animeTitle: String = "",
            poster: String = "",
            epUrl: String = ""
        ): String {
            val encStream = URLEncoder.encode(streamUrl, "UTF-8")
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encId = URLEncoder.encode(animeId, "UTF-8")
            val encAnimeTitle = URLEncoder.encode(animeTitle, "UTF-8")
            val encPoster = URLEncoder.encode(poster, "UTF-8")
            val encEp = URLEncoder.encode(epUrl, "UTF-8")
            return "player/$encStream/$encTitle?animeId=$encId&animeTitle=$encAnimeTitle&poster=$encPoster&epUrl=$encEp"
        }
    }
}
