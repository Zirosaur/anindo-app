package com.ziro.anindo.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.calculateCacheSize(context)
    }

    // States from ViewModel
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val defaultResizeMode by viewModel.defaultResizeMode.collectAsState()
    val defaultPlaybackSpeed by viewModel.defaultPlaybackSpeed.collectAsState()
    val seekIntervalSeconds by viewModel.seekIntervalSeconds.collectAsState()
    val isGestureControlsEnabled by viewModel.isGestureControlsEnabled.collectAsState()
    val isKeepScreenAwake by viewModel.isKeepScreenAwake.collectAsState()
    val isAutoPauseScreenOff by viewModel.isAutoPauseScreenOff.collectAsState()

    val downloadLocation by viewModel.downloadLocation.collectAsState()
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsState()
    val notifyDownloadComplete by viewModel.notifyDownloadComplete.collectAsState()

    val defaultProvider by viewModel.defaultProvider.collectAsState()
    val dohProvider by viewModel.dohProvider.collectAsState()

    val appTheme by viewModel.appTheme.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()

    val cacheSize by viewModel.cacheSize.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateMessage by viewModel.updateMessage.collectAsState()
    val isCheckingOta by viewModel.isCheckingOta.collectAsState()
    val otaMessage by viewModel.otaMessage.collectAsState()

    // Dialog States
    var showQualityDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var showDohDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // SECTION: Pemutar Video
            item {
                SettingsSectionHeader(title = "🎬 Pemutar Video")
            }
            item {
                val qualityLabel = when (defaultQuality) {
                    "best" -> "Resolusi Tertinggi (Auto)"
                    else -> defaultQuality
                }
                SettingsItem(
                    icon = Icons.Default.HighQuality,
                    title = "Kualitas Video Bawaan",
                    subtitle = qualityLabel,
                    onClick = { showQualityDialog = true }
                )
            }
            item {
                val resizeLabel = when (defaultResizeMode) {
                    0 -> "Fit (Proporsional)"
                    1 -> "Zoom (Penuh Layar)"
                    2 -> "Stretch (Rentangkan)"
                    else -> "Fit"
                }
                SettingsItem(
                    icon = Icons.Default.AspectRatio,
                    title = "Rasio Aspek Bawaan",
                    subtitle = resizeLabel,
                    onClick = { showResizeDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Speed,
                    title = "Kecepatan Putar Bawaan",
                    subtitle = "${defaultPlaybackSpeed}x",
                    onClick = { showSpeedDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Durasi Lompat (Double Tap Seek)",
                    subtitle = "$seekIntervalSeconds Detik",
                    onClick = { showSeekDialog = true }
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Gesture,
                    title = "Kontrol Gestur Layar",
                    subtitle = "Usap sisi kiri untuk kecerahan, kanan untuk volume",
                    checked = isGestureControlsEnabled,
                    onCheckedChange = { viewModel.setGestureControls(it) }
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.ScreenLockPortrait,
                    title = "Layar Tetap Menyala (Keep Awake)",
                    subtitle = "Layar tidak redup atau mati sendiri saat menonton video",
                    checked = isKeepScreenAwake,
                    onCheckedChange = { viewModel.setKeepScreenAwake(it) }
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.PlayCircle,
                    title = "Jeda Otomatis Saat Layar Mati",
                    subtitle = "Otomatis jeda video saat tombol power ditekan",
                    checked = isAutoPauseScreenOff,
                    onCheckedChange = { viewModel.setAutoPauseScreenOff(it) }
                )
            }

            // SECTION: Unduhan
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSectionHeader(title = "📥 Unduhan")
            }
            item {
                val locLabel = if (downloadLocation == "public") {
                    "Folder Publik (/sdcard/Download/Anindo/)"
                } else {
                    "Penyimpanan Internal Aplikasi"
                }
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = "Lokasi Penyimpanan",
                    subtitle = locLabel,
                    onClick = { showStorageDialog = true }
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Wifi,
                    title = "Unduh Hanya via Wi-Fi",
                    subtitle = "Mencegah penggunaan kuota data seluler",
                    checked = downloadWifiOnly,
                    onCheckedChange = { viewModel.setDownloadWifiOnly(it) }
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifikasi Selesai Unduh",
                    subtitle = "Tampilkan notifikasi di sistem saat unduhan selesai",
                    checked = notifyDownloadComplete,
                    onCheckedChange = { viewModel.setNotifyDownloadComplete(it) }
                )
            }

            // SECTION: Jaringan & Sumber
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSectionHeader(title = "🌐 Jaringan & Sumber")
            }
            item {
                val provLabel = when (defaultProvider) {
                    "all" -> "Semua Provider (Pencarian Serentak)"
                    "otakudesu" -> "Otakudesu"
                    "nontonanimeid" -> "NontonAnimeID"
                    else -> defaultProvider
                }
                SettingsItem(
                    icon = Icons.Default.Public,
                    title = "Provider Pencarian Bawaan",
                    subtitle = provLabel,
                    onClick = { showProviderDialog = true }
                )
            }
            item {
                val dohLabel = when (dohProvider) {
                    "cloudflare" -> "Cloudflare (1.1.1.1) - Cepat & Aman"
                    "google" -> "Google (8.8.8.8)"
                    "system" -> "DNS Sistem (Tanpa DoH)"
                    else -> dohProvider
                }
                SettingsItem(
                    icon = Icons.Default.Dns,
                    title = "DNS-over-HTTPS (Anti-Blokir)",
                    subtitle = dohLabel,
                    onClick = { showDohDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    title = "Periksa Pembaruan Aturan OTA",
                    subtitle = "Sinkronkan mirror domain dan selektor scraper terbaru dari GitHub",
                    trailingContent = {
                        if (isCheckingOta) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    },
                    onClick = {
                        if (!isCheckingOta) {
                            viewModel.checkOtaRules()
                        }
                    }
                )
            }

            // SECTION: Tampilan
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSectionHeader(title = "🎨 Tampilan")
            }
            item {
                val themeLabel = when (appTheme) {
                    "system" -> "Mengikuti Pengaturan Sistem"
                    "dark" -> "Tema Gelap (Dark)"
                    "amoled" -> "AMOLED Hitam Murni (Hemat Baterai)"
                    "light" -> "Tema Terang (Light)"
                    else -> "Mengikuti Sistem"
                }
                SettingsItem(
                    icon = Icons.Default.BrightnessMedium,
                    title = "Tema Aplikasi",
                    subtitle = themeLabel,
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Palette,
                    title = "Warna Dinamis (Material You)",
                    subtitle = "Mengikuti palet warna wallpaper perangkat (Android 12+)",
                    checked = dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }

            // SECTION: Data & Penyimpanan
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSectionHeader(title = "🧹 Data & Penyimpanan")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.CleaningServices,
                    title = "Bersihkan Cache Gambar",
                    subtitle = "Ukuran cache saat ini: $cacheSize",
                    onClick = {
                        viewModel.clearCache(context)
                    }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Hapus Seluruh Riwayat Tontonan",
                    subtitle = "Reset seluruh catatan progres episode yang pernah ditonton",
                    onClick = { showClearHistoryDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.RestartAlt,
                    title = "Reset Pengaturan ke Awal",
                    subtitle = "Kembalikan seluruh konfigurasi ke pengaturan pabrik bawaan",
                    onClick = { showResetDialog = true }
                )
            }

            // SECTION: Tentang
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSectionHeader(title = "ℹ️ Tentang Aplikasi")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.SystemUpdate,
                    title = "Periksa Pembaruan Aplikasi",
                    subtitle = "Versi rilis saat ini: v0.1.13-beta",
                    trailingContent = {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    },
                    onClick = {
                        if (!isCheckingUpdate) {
                            viewModel.checkForAppUpdates("v0.1.13-beta")
                        }
                    }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Repositori GitHub & Lisensi",
                    subtitle = "github.com/Zirosaur/anindo-app (Open Source)",
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Zirosaur/anindo-app"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // DIALOGS
    if (showQualityDialog) {
        val options = listOf(
            "best" to "Resolusi Tertinggi (Auto)",
            "1080p" to "1080p Full HD",
            "720p" to "720p HD",
            "480p" to "480p SD",
            "360p" to "360p Hemat Kuota"
        )
        SingleChoiceDialog(
            title = "Pilih Kualitas Video Bawaan",
            options = options,
            selectedKey = defaultQuality,
            onSelect = {
                viewModel.setDefaultQuality(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showResizeDialog) {
        val options = listOf(
            0 to "Fit (Proporsional dengan letterbox)",
            1 to "Zoom (Penuh layar memotong sisi)",
            2 to "Stretch (Rentangkan memenuhi layar)"
        )
        SingleChoiceDialog(
            title = "Pilih Rasio Aspek Bawaan",
            options = options,
            selectedKey = defaultResizeMode,
            onSelect = {
                viewModel.setDefaultResizeMode(it)
                showResizeDialog = false
            },
            onDismiss = { showResizeDialog = false }
        )
    }

    if (showSpeedDialog) {
        val options = listOf(
            0.75f to "0.75x (Lambat)",
            1.0f to "1.0x (Normal)",
            1.25f to "1.25x",
            1.5f to "1.5x",
            2.0f to "2.0x (Cepat)"
        )
        SingleChoiceDialog(
            title = "Pilih Kecepatan Putar Bawaan",
            options = options,
            selectedKey = defaultPlaybackSpeed,
            onSelect = {
                viewModel.setDefaultPlaybackSpeed(it)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showSeekDialog) {
        val options = listOf(
            5 to "5 Detik",
            10 to "10 Detik (Standar)",
            15 to "15 Detik",
            30 to "30 Detik"
        )
        SingleChoiceDialog(
            title = "Durasi Lompat Ganda (Double Tap)",
            options = options,
            selectedKey = seekIntervalSeconds,
            onSelect = {
                viewModel.setSeekInterval(it)
                showSeekDialog = false
            },
            onDismiss = { showSeekDialog = false }
        )
    }

    if (showStorageDialog) {
        val options = listOf(
            "public" to "Folder Publik (/sdcard/Download/Anindo/)\nBisa diakses Galeri & VLC",
            "internal" to "Penyimpanan Khusus Aplikasi (Scoped Storage)"
        )
        SingleChoiceDialog(
            title = "Lokasi Penyimpanan Unduhan",
            options = options,
            selectedKey = downloadLocation,
            onSelect = {
                viewModel.setDownloadLocation(it)
                showStorageDialog = false
            },
            onDismiss = { showStorageDialog = false }
        )
    }

    if (showProviderDialog) {
        val options = listOf(
            "all" to "Semua Provider (Pencarian Serentak)",
            "otakudesu" to "Otakudesu Saja",
            "nontonanimeid" to "NontonAnimeID Saja"
        )
        SingleChoiceDialog(
            title = "Provider Pencarian Bawaan",
            options = options,
            selectedKey = defaultProvider,
            onSelect = {
                viewModel.setDefaultProvider(it)
                showProviderDialog = false
            },
            onDismiss = { showProviderDialog = false }
        )
    }

    if (showDohDialog) {
        val options = listOf(
            "cloudflare" to "Cloudflare (1.1.1.1) - Rekomendasi",
            "google" to "Google (8.8.8.8)",
            "system" to "DNS Sistem (Standar ISP)"
        )
        SingleChoiceDialog(
            title = "Pilih Engine DNS-over-HTTPS",
            options = options,
            selectedKey = dohProvider,
            onSelect = {
                viewModel.setDohProvider(it)
                showDohDialog = false
            },
            onDismiss = { showDohDialog = false }
        )
    }

    if (showThemeDialog) {
        val options = listOf(
            "system" to "Mengikuti Sistem",
            "dark" to "Tema Gelap (Dark)",
            "amoled" to "AMOLED Pure Black",
            "light" to "Tema Terang (Light)"
        )
        SingleChoiceDialog(
            title = "Pilih Tema Aplikasi",
            options = options,
            selectedKey = appTheme,
            onSelect = {
                viewModel.setAppTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Hapus Semua Riwayat?") },
            text = { Text("Seluruh riwayat episode yang telah Anda tonton akan dihapus secara permanen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Pengaturan?") },
            text = { Text("Semua preferensi pemutar, unduhan, tema, dan jaringan akan dikembalikan ke nilai default.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Notification / Message Dialogs
    updateMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text("Pembaruan Aplikasi") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpdateDialog()
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
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text("Nanti")
                    }
                }
            }
        )
    }

    otaMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissOtaDialog() },
            title = { Text("Sinkronisasi Aturan OTA") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissOtaDialog() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(key) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (key == selectedKey),
                            onClick = { onSelect(key) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
