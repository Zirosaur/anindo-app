package com.ziro.anindo.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziro.anindo.R
import com.ziro.anindo.ui.navigation.Screen

@Composable
fun AnindoDrawerSheet(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    onCheckUpdate: () -> Unit,
    onCheckOta: () -> Unit,
    onShowAbout: () -> Unit
) {
    val context = LocalContext.current

    ModalDrawerSheet(
        modifier = Modifier.width(310.dp)
    ) {
        // Drawer Header with Logo & Version
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "Anindo Logo",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "anindo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = " v0.1.13-beta ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Streaming & Unduh Anime Subtitle Indonesia Bebas Iklan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Drawer Items
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Pengaturan (Settings)") },
            selected = currentRoute == Screen.Settings.route,
            onClick = {
                onCloseDrawer()
                onNavigate(Screen.Settings.route)
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            label = { Text("Pembaruan Aturan OTA") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onCheckOta()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
            label = { Text("Periksa Pembaruan APK") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onCheckUpdate()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
            label = { Text("Laporkan Masalah / Request") },
            selected = false,
            onClick = {
                onCloseDrawer()
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Zirosaur/anindo-app/issues"))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            label = { Text("Tentang anindo") },
            selected = false,
            onClick = {
                onCloseDrawer()
                onShowAbout()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Drawer Footer
        Text(
            text = "anindo-app • Open Source",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(20.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}
