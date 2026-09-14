package com.ziro.anindo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { AnindoBottomBar(navController = navController) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Library.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Library.route) {
                            LibraryScreen(
                                onExploreClick = { navController.navigate(Screen.Explore.route) }
                            )
                        }
                        composable(Screen.Ongoing.route) {
                            OngoingScreen(
                                onAnimeClick = { anime ->
                                    navController.navigate(Screen.Details.createRoute(anime.url))
                                }
                            )
                        }
                        composable(Screen.Explore.route) {
                            ExploreScreen(
                                onAnimeClick = { anime ->
                                    navController.navigate(Screen.Details.createRoute(anime.url))
                                }
                            )
                        }
                        composable(Screen.History.route) {
                            LibraryScreen(
                                onExploreClick = { navController.navigate(Screen.Explore.route) }
                            )
                        }
                        composable(
                            route = Screen.Details.route,
                            arguments = listOf(navArgument("animeUrl") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val encUrl = backStackEntry.arguments?.getString("animeUrl") ?: ""
                            val animeUrl = URLDecoder.decode(encUrl, "UTF-8")
                            DetailsScreen(
                                animeUrl = animeUrl,
                                onBackClick = { navController.popBackStack() },
                                onPlayEpisode = { streamUrl, title ->
                                    navController.navigate(Screen.Player.createRoute(streamUrl, title))
                                }
                            )
                        }
                        composable(
                            route = Screen.Player.route,
                            arguments = listOf(
                                navArgument("streamUrl") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val encStream = backStackEntry.arguments?.getString("streamUrl") ?: ""
                            val encTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val streamUrl = URLDecoder.decode(encStream, "UTF-8")
                            val title = URLDecoder.decode(encTitle, "UTF-8")

                            PlayerScreen(
                                streamUrl = streamUrl,
                                title = title,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
