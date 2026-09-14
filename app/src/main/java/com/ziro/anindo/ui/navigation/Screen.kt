package com.ziro.anindo.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Library : Screen("library", "Koleksi")
    object Ongoing : Screen("ongoing", "Ongoing")
    object Explore : Screen("explore", "Jelajah")
    object History : Screen("history", "Riwayat")
    object Details : Screen("details/{animeUrl}", "Detail") {
        fun createRoute(animeUrl: String) = "details/${java.net.URLEncoder.encode(animeUrl, "UTF-8")}"
    }
    object Player : Screen("player/{streamUrl}/{title}", "Pemutar") {
        fun createRoute(streamUrl: String, title: String) = 
            "player/${java.net.URLEncoder.encode(streamUrl, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}"
    }
}
