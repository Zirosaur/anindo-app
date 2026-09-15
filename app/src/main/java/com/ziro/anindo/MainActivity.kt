package com.ziro.anindo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ziro.anindo.core.data.local.entity.EpisodeProgressEntity
import com.ziro.anindo.core.model.Episode
import com.ziro.anindo.core.network.NetworkClient
import com.ziro.anindo.core.provider.DynamicDomainResolver
import com.ziro.anindo.core.provider.ProviderRegistry
import com.ziro.anindo.ui.components.AnindoBottomBar
import com.ziro.anindo.ui.components.AnindoDrawerSheet
import com.ziro.anindo.ui.navigation.Screen
import com.ziro.anindo.ui.screens.details.DetailsScreen
import com.ziro.anindo.ui.screens.explore.ExploreScreen
import com.ziro.anindo.ui.screens.library.LibraryScreen
import com.ziro.anindo.ui.screens.library.LibraryTab
import com.ziro.anindo.ui.screens.ongoing.OngoingScreen
import com.ziro.anindo.ui.screens.player.PlayerScreen
import com.ziro.anindo.ui.screens.settings.SettingsScreen
import com.ziro.anindo.ui.theme.AnindoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsManager = AnindoApp.instance.settingsManager
            val appTheme by settingsManager.appTheme.collectAsState()
            val dynamicColorPref by settingsManager.dynamicColor.collectAsState()

            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (appTheme) {
                "dark", "amoled" -> true
                "light" -> false
                else -> isSystemDark
            }
            val isAmoled = (appTheme == "amoled")

            AnindoTheme(
                darkTheme = isDark,
                isAmoled = isAmoled,
                dynamicColor = dynamicColorPref
            ) {
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
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                var drawerUpdateMessage by remember { mutableStateOf<String?>(null) }
                var drawerOtaMessage by remember { mutableStateOf<String?>(null) }
                var showAboutDialog by remember { mutableStateOf(false) }

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

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !isPlayerActive && currentRoute != Screen.Settings.route,
                    drawerContent = {
                        AnindoDrawerSheet(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            },
                            onCloseDrawer = {
                                scope.launch { drawerState.close() }
                            },
                            onCheckUpdate = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val res = NetworkClient.get("https://api.github.com/repos/Zirosaur/anindo-app/releases/latest")
                                        val json = JSONObject(res)
                                        val tag = json.optString("tag_name", "")
                                        val notes = json.optString("name", "").ifBlank { tag }
                                        withContext(Dispatchers.Main) {
                                            if (tag.isNotBlank() && tag != "v0.1.13-beta") {
                                                drawerUpdateMessage = "Versi baru tersedia: $tag\n\n$notes\n\nUnduh versi terbaru dari GitHub Releases."
                                            } else {
                                                drawerUpdateMessage = "anindo-app sudah versi terbaru (v0.1.13-beta)!"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            drawerUpdateMessage = "Gagal memeriksa pembaruan: ${e.localizedMessage ?: "Cek koneksi internet"}"
                                        }
                                    }
                                }
                            },
                            onCheckOta = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val otaku = DynamicDomainResolver.resolve("otakudesu", forceRefresh = true)
                                        val nonton = DynamicDomainResolver.resolve("nontonanime", forceRefresh = true)
                                        withContext(Dispatchers.Main) {
                                            drawerOtaMessage = "Aturan OTA Berhasil Disinkronkan!\n• Otakudesu: $otaku\n• NontonAnime: $nonton"
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            drawerOtaMessage = "Gagal menyinkronkan aturan OTA: ${e.localizedMessage ?: "Koneksi bermasalah"}"
                                        }
                                    }
                                }
                            },
                            onShowAbout = {
                                showAboutDialog = true
                            }
                        )
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (!isPlayerActive && currentRoute != Screen.Settings.route) {
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
                                    onMenuClick = { scope.launch { drawerState.open() } },
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
                                    },
                                    onMenuClick = { scope.launch { drawerState.open() } }
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
                                    },
                                    onMenuClick = { scope.launch { drawerState.open() } }
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
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onExploreClick = { navigateToTab(Screen.Explore.route) }
                                )
                            }
                            composable(Screen.Settings.route) {
                                SettingsScreen(
                                    onBackClick = { navController.popBackStack() }
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

                // Drawer Alert Dialogs
                drawerUpdateMessage?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { drawerUpdateMessage = null },
                        title = { Text("Pembaruan Aplikasi") },
                        text = { Text(msg) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    drawerUpdateMessage = null
                                    if (msg.contains("Versi baru tersedia")) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Zirosaur/anindo-app/releases/latest"))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            ) {
                                Text(if (msg.contains("Versi baru")) "Unduh" else "OK")
                            }
                        },
                        dismissButton = {
                            if (msg.contains("Versi baru")) {
                                TextButton(onClick = { drawerUpdateMessage = null }) {
                                    Text("Nanti")
                                }
                            }
                        }
                    )
                }

                drawerOtaMessage?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { drawerOtaMessage = null },
                        title = { Text("Sinkronisasi Aturan OTA") },
                        text = { Text(msg) },
                        confirmButton = {
                            TextButton(onClick = { drawerOtaMessage = null }) {
                                Text("OK")
                            }
                        }
                    )
                }

                if (showAboutDialog) {
                    AlertDialog(
                        onDismissRequest = { showAboutDialog = false },
                        title = { Text("Tentang anindo-app") },
                        text = {
                            Text(
                                "anindo-app adalah aplikasi streaming & unduh anime subtitle Indonesia bebas iklan, terinspirasi oleh kesederhanaan dan keindahan Mihon / Tachiyomi.\n\n" +
                                "Versi: v0.1.13-beta\n" +
                                "Lisensi: Open Source\n" +
                                "Pengembang: Ziro & Komunitas"
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showAboutDialog = false
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Zirosaur/anindo-app"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Text("Kunjungi GitHub")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAboutDialog = false }) {
                                Text("Tutup")
                            }
                        }
                    )
                }
            }
        }
    }
}
