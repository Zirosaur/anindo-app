package com.ziro.anindo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ziro.anindo.ui.components.AnindoBottomBar
import com.ziro.anindo.ui.navigation.Screen
import com.ziro.anindo.ui.screens.details.DetailsScreen
import com.ziro.anindo.ui.screens.explore.ExploreScreen
import com.ziro.anindo.ui.screens.library.LibraryScreen
import com.ziro.anindo.ui.screens.ongoing.OngoingScreen
import com.ziro.anindo.ui.screens.player.PlayerScreen
import com.ziro.anindo.ui.theme.AnindoTheme
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AnindoTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isPlayerActive = currentRoute?.startsWith("player/") == true

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isPlayerActive) {
                            AnindoBottomBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Library.route,
                        modifier = if (isPlayerActive) Modifier.fillMaxSize() else Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Library.route) {
                            LibraryScreen(
                                onAnimeClick = { animeId, provider, detailUrl ->
                                    navController.navigate(
                                        Screen.Details.createRoute(
                                            animeUrl = detailUrl,
                                            animeId = animeId,
                                            provider = provider
                                        )
                                    )
                                },
                                onPlayEpisode = { episodeUrl, episodeTitle ->
                                    navController.navigate(
                                        Screen.Player.createRoute(
                                            streamUrl = episodeUrl,
                                            title = episodeTitle,
                                            epUrl = episodeUrl
                                        )
                                    )
                                },
                                onExploreClick = { navController.navigate(Screen.Explore.route) }
                            )
                        }
                        composable(Screen.Ongoing.route) {
                            OngoingScreen(
                                onAnimeClick = { anime ->
                                    navController.navigate(
                                        Screen.Details.createRoute(
                                            animeUrl = anime.url,
                                            title = anime.title,
                                            poster = anime.posterUrl ?: "",
                                            provider = "otakudesu"

                                        )
                                    )
                                }
                            )
                        }
                        composable(Screen.Explore.route) {
                            ExploreScreen(
                                onAnimeClick = { anime ->
                                    navController.navigate(
                                        Screen.Details.createRoute(
                                            animeUrl = anime.url,
                                            title = anime.title,
                                            poster = anime.posterUrl ?: "",
                                            provider = anime.provider.lowercase()
                                        )

                                    )
                                }
                            )
                        }
                        composable(Screen.History.route) {
                            LibraryScreen(
                                onAnimeClick = { animeId, provider, detailUrl ->
                                    navController.navigate(
                                        Screen.Details.createRoute(
                                            animeUrl = detailUrl,
                                            animeId = animeId,
                                            provider = provider
                                        )
                                    )
                                },
                                onPlayEpisode = { episodeUrl, episodeTitle ->
                                    navController.navigate(
                                        Screen.Player.createRoute(
                                            streamUrl = episodeUrl,
                                            title = episodeTitle,
                                            epUrl = episodeUrl
                                        )
                                    )
                                },
                                onExploreClick = { navController.navigate(Screen.Explore.route) }
                            )
                        }
                        composable(
                            route = Screen.Details.route,
                            arguments = listOf(
                                navArgument("animeUrl") { type = NavType.StringType },
                                navArgument("animeId") { type = NavType.StringType; defaultValue = "" },
                                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                                navArgument("provider") { type = NavType.StringType; defaultValue = "otakudesu" }
                            )
                        ) { backStackEntry ->
                            val encUrl = backStackEntry.arguments?.getString("animeUrl") ?: ""
                            val encId = backStackEntry.arguments?.getString("animeId") ?: ""
                            val encTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val encPoster = backStackEntry.arguments?.getString("poster") ?: ""
                            val encProvider = backStackEntry.arguments?.getString("provider") ?: "otakudesu"

                            val animeUrl = URLDecoder.decode(encUrl, "UTF-8")
                            val animeId = URLDecoder.decode(encId, "UTF-8")
                            val animeTitle = URLDecoder.decode(encTitle, "UTF-8")
                            val posterUrl = URLDecoder.decode(encPoster, "UTF-8")
                            val provider = URLDecoder.decode(encProvider, "UTF-8")

                            DetailsScreen(
                                animeUrl = animeUrl,
                                animeId = animeId,
                                animeTitle = animeTitle.ifBlank { "Detail Anime" },
                                posterUrl = posterUrl,
                                providerName = provider,
                                onBackClick = { navController.popBackStack() },
                                onPlayEpisode = { streamUrl, epTitle ->
                                    navController.navigate(
                                        Screen.Player.createRoute(
                                            streamUrl = streamUrl,
                                            title = epTitle,
                                            animeId = animeId,
                                            animeTitle = animeTitle,
                                            poster = posterUrl,
                                            epUrl = streamUrl
                                        )
                                    )
                                }
                            )
                        }
                        composable(
                            route = Screen.Player.route,
                            arguments = listOf(
                                navArgument("streamUrl") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType },
                                navArgument("animeId") { type = NavType.StringType; defaultValue = "" },
                                navArgument("animeTitle") { type = NavType.StringType; defaultValue = "" },
                                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                                navArgument("epUrl") { type = NavType.StringType; defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            val encStream = backStackEntry.arguments?.getString("streamUrl") ?: ""
                            val encTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val encId = backStackEntry.arguments?.getString("animeId") ?: ""
                            val encAnimeTitle = backStackEntry.arguments?.getString("animeTitle") ?: ""
                            val encPoster = backStackEntry.arguments?.getString("poster") ?: ""
                            val encEp = backStackEntry.arguments?.getString("epUrl") ?: ""

                            val streamUrl = URLDecoder.decode(encStream, "UTF-8")
                            val title = URLDecoder.decode(encTitle, "UTF-8")
                            val animeId = URLDecoder.decode(encId, "UTF-8")
                            val animeTitle = URLDecoder.decode(encAnimeTitle, "UTF-8")
                            val posterUrl = URLDecoder.decode(encPoster, "UTF-8")
                            val epUrl = URLDecoder.decode(encEp, "UTF-8")

                            PlayerScreen(
                                streamUrl = streamUrl,
                                title = title,
                                animeId = animeId,
                                animeTitle = animeTitle,
                                posterUrl = posterUrl,
                                episodeUrl = epUrl,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
