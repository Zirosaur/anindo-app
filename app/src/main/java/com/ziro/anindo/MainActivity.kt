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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ziro.anindo.core.data.local.entity.EpisodeProgressEntity
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.provider.ProviderRegistry
import com.ziro.anindo.ui.components.AnindoBottomBar
import com.ziro.anindo.ui.navigation.Screen
import com.ziro.anindo.ui.screens.details.DetailsScreen
import com.ziro.anindo.ui.screens.explore.ExploreScreen
import com.ziro.anindo.ui.screens.library.LibraryScreen
import com.ziro.anindo.ui.screens.library.LibraryTab
import com.ziro.anindo.ui.screens.ongoing.OngoingScreen
import com.ziro.anindo.ui.screens.player.PlayerScreen
import com.ziro.anindo.ui.theme.AnindoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AnindoTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {}
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isPlayerActive = currentRoute?.startsWith("player/") == true

                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val onHistoryItemClick: (EpisodeProgressEntity) -> Unit = { item ->
                    val isLocal = !item.episodeUrl.startsWith("http://", ignoreCase = true) && !item.episodeUrl.startsWith("https://", ignoreCase = true)
                    if (isLocal || item.episodeUrl.endsWith(".mp4", ignoreCase = true) || item.episodeUrl.contains("/api/hls")) {
                        navController.navigate(
                            Screen.Player.createRoute(
                                streamUrl = item.episodeUrl,
                                title = item.episodeTitle,
                                animeId = item.animeId,
                                animeTitle = item.animeTitle,
                                poster = item.animePosterUrl,
                                epUrl = item.episodeUrl
                            )
                        )
                    } else {
                        Toast.makeText(context, "Menghubungkan ke server video...", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val streamResult = withContext(Dispatchers.IO) {
                                ProviderRegistry.resolveStreamForEpisodeUrl(item.episodeUrl, item.episodeTitle)
                            }
                            if (streamResult != null && streamResult.url.isNotBlank()) {
                                navController.navigate(
                                    Screen.Player.createRoute(
                                        streamUrl = streamResult.url,
                                        title = item.episodeTitle,
                                        animeId = item.animeId,
                                        animeTitle = item.animeTitle,
                                        poster = item.animePosterUrl,
                                        epUrl = item.episodeUrl,
                                        referer = streamResult.referer ?: item.episodeUrl
                                    )
                                )
                            } else {
                                Toast.makeText(context, "Gagal memuat server video episode ini", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val navigateToTab: (String) -> Unit = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

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
                                initialTab = LibraryTab.BOOKMARKS,
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
                                onHistoryClick = onHistoryItemClick,
                                onExploreClick = { navigateToTab(Screen.Explore.route) }
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
                                            provider = anime.provider.lowercase().ifBlank { "otakudesu" }
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
                                initialTab = LibraryTab.HISTORY,
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
                                onHistoryClick = onHistoryItemClick,
                                onExploreClick = { navigateToTab(Screen.Explore.route) }
                            )
                        }
                        composable(
                            route = Screen.Details.route,
                            arguments = listOf(
                                navArgument("animeUrlB64") { type = NavType.StringType },
                                navArgument("animeId") { type = NavType.StringType; defaultValue = "" },
                                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                                navArgument("provider") { type = NavType.StringType; defaultValue = "otakudesu" }
                            )
                        ) { backStackEntry ->
                            val encUrl = backStackEntry.arguments?.getString("animeUrlB64") ?: ""
                            val encId = backStackEntry.arguments?.getString("animeId") ?: ""
                            val encTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val encPoster = backStackEntry.arguments?.getString("poster") ?: ""
                            val encProvider = backStackEntry.arguments?.getString("provider") ?: "otakudesu"

                            val animeUrl = Screen.decodeParam(encUrl)
                            val animeId = Screen.decodeParam(encId)
                            val animeTitle = Screen.decodeParam(encTitle)
                            val posterUrl = Screen.decodeParam(encPoster)
                            val provider = Screen.decodeParam(encProvider)

                            DetailsScreen(
                                animeUrl = animeUrl,
                                animeId = animeId,
                                animeTitle = animeTitle.ifBlank { "Detail Anime" },
                                posterUrl = posterUrl,
                                providerName = provider,
                                onBackClick = { navController.popBackStack() },
                                onPlayEpisode = { streamUrl, epTitle, epUrl, ref ->
                                    navController.navigate(
                                        Screen.Player.createRoute(
                                            streamUrl = streamUrl,
                                            title = epTitle,
                                            animeId = animeId,
                                            animeTitle = animeTitle,
                                            poster = posterUrl,
                                            epUrl = epUrl,
                                            referer = ref
                                        )
                                    )
                                }
                            )
                        }
                        composable(
                            route = Screen.Player.route,
                            arguments = listOf(
                                navArgument("streamUrlB64") { type = NavType.StringType },
                                navArgument("titleB64") { type = NavType.StringType },
                                navArgument("animeId") { type = NavType.StringType; defaultValue = "" },
                                navArgument("animeTitle") { type = NavType.StringType; defaultValue = "" },
                                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                                navArgument("epUrl") { type = NavType.StringType; defaultValue = "" },
                                navArgument("referer") { type = NavType.StringType; defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            val encStream = backStackEntry.arguments?.getString("streamUrlB64") ?: ""
                            val encTitle = backStackEntry.arguments?.getString("titleB64") ?: ""
                            val encId = backStackEntry.arguments?.getString("animeId") ?: ""
                            val encAnimeTitle = backStackEntry.arguments?.getString("animeTitle") ?: ""
                            val encPoster = backStackEntry.arguments?.getString("poster") ?: ""
                            val encEp = backStackEntry.arguments?.getString("epUrl") ?: ""
                            val encReferer = backStackEntry.arguments?.getString("referer") ?: ""

                            val streamUrl = Screen.decodeParam(encStream)
                            val title = Screen.decodeParam(encTitle)
                            val animeId = Screen.decodeParam(encId)
                            val animeTitle = Screen.decodeParam(encAnimeTitle)
                            val posterUrl = Screen.decodeParam(encPoster)
                            val epUrl = Screen.decodeParam(encEp)
                            val referer = Screen.decodeParam(encReferer)

                            PlayerScreen(
                                streamUrl = streamUrl,
                                title = title,
                                animeId = animeId,
                                animeTitle = animeTitle,
                                posterUrl = posterUrl,
                                episodeUrl = epUrl,
                                referer = referer,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
