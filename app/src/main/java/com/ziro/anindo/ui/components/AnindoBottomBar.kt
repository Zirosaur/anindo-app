package com.ziro.anindo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ziro.anindo.ui.navigation.Screen

@Composable
fun AnindoBottomBar(navController: NavController) {
    val items = listOf(
        Triple(Screen.Library, "Koleksi", Icons.Default.Bookmarks),
        Triple(Screen.Ongoing, "Ongoing", Icons.Default.Whatshot),
        Triple(Screen.Explore, "Jelajah", Icons.Default.Explore),
        Triple(Screen.History, "Riwayat", Icons.Default.History)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar in player screen
    if (currentRoute?.startsWith("player") == true) return

    NavigationBar {
        items.forEach { (screen, label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Library.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
