package com.ziro.anindo.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ziro.anindo.AnindoApp
import com.ziro.anindo.core.network.NetworkClient
import com.ziro.anindo.core.provider.DynamicDomainResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat

class SettingsViewModel : ViewModel() {
    private val settingsManager = AnindoApp.instance.settingsManager
    private val repository = AnindoApp.instance.repository

    val defaultQuality = settingsManager.defaultQuality
    val defaultResizeMode = settingsManager.defaultResizeMode
    val defaultPlaybackSpeed = settingsManager.defaultPlaybackSpeed
    val seekIntervalSeconds = settingsManager.seekIntervalSeconds
    val isGestureControlsEnabled = settingsManager.isGestureControlsEnabled
    val isKeepScreenAwake = settingsManager.isKeepScreenAwake
    val isAutoPauseScreenOff = settingsManager.isAutoPauseScreenOff

    val downloadLocation = settingsManager.downloadLocation
    val downloadWifiOnly = settingsManager.downloadWifiOnly
    val notifyDownloadComplete = settingsManager.notifyDownloadComplete

    val defaultProvider = settingsManager.defaultProvider
    val dohProvider = settingsManager.dohProvider

    val appTheme = settingsManager.appTheme
    val dynamicColor = settingsManager.dynamicColor

    private val _cacheSize = MutableStateFlow("0.0 MB")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    private val _isCheckingOta = MutableStateFlow(false)
    val isCheckingOta: StateFlow<Boolean> = _isCheckingOta.asStateFlow()

    private val _otaMessage = MutableStateFlow<String?>(null)
    val otaMessage: StateFlow<String?> = _otaMessage.asStateFlow()

    fun calculateCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val totalBytes = calculateFolderSize(context.cacheDir)
            _cacheSize.value = formatSize(totalBytes)
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
            } catch (_: Exception) {}
            calculateCacheSize(context)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun checkOtaRules() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingOta.value = true
            _otaMessage.value = null
            try {
                val otakuDomain = DynamicDomainResolver.resolve("otakudesu", forceRefresh = true)
                val nontonDomain = DynamicDomainResolver.resolve("nontonanime", forceRefresh = true)
                _otaMessage.value = "Aturan OTA Berhasil Disinkronkan!\n• Otakudesu: $otakuDomain\n• NontonAnime: $nontonDomain"
            } catch (e: Exception) {
                _otaMessage.value = "Gagal memperbarui aturan OTA: ${e.localizedMessage ?: "Koneksi bermasalah"}"
            } finally {
                _isCheckingOta.value = false
            }
        }
    }

    fun checkForAppUpdates(currentVersion: String = "v0.1.13-beta") {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdate.value = true
            _updateMessage.value = null
            try {
                val responseStr = NetworkClient.get("https://api.github.com/repos/Zirosaur/anindo-app/releases/latest")
                val json = JSONObject(responseStr)
                val latestTag = json.optString("tag_name", "")
                val releaseNotes = json.optString("name", "").ifBlank { latestTag }

                if (latestTag.isNotBlank() && latestTag != currentVersion) {
                    _updateMessage.value = "Versi baru tersedia: $latestTag\n\n$releaseNotes\n\nKunjungi GitHub Releases untuk mengunduh."
                } else {
                    _updateMessage.value = "Aplikasi sudah versi terbaru ($currentVersion)!"
                }
            } catch (e: Exception) {
                _updateMessage.value = "Gagal memeriksa pembaruan: ${e.localizedMessage ?: "Cek koneksi internet Anda"}"
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateMessage.value = null
    }

    fun dismissOtaDialog() {
        _otaMessage.value = null
    }

    // Setters
    fun setDefaultQuality(quality: String) = settingsManager.setDefaultQuality(quality)
    fun setDefaultResizeMode(mode: Int) = settingsManager.setDefaultResizeMode(mode)
    fun setDefaultPlaybackSpeed(speed: Float) = settingsManager.setDefaultPlaybackSpeed(speed)
    fun setSeekInterval(seconds: Int) = settingsManager.setSeekInterval(seconds)
    fun setGestureControls(enabled: Boolean) = settingsManager.setGestureControls(enabled)
    fun setKeepScreenAwake(enabled: Boolean) = settingsManager.setKeepScreenAwake(enabled)
    fun setAutoPauseScreenOff(enabled: Boolean) = settingsManager.setAutoPauseScreenOff(enabled)
    fun setDownloadLocation(location: String) = settingsManager.setDownloadLocation(location)
    fun setDownloadWifiOnly(wifiOnly: Boolean) = settingsManager.setDownloadWifiOnly(wifiOnly)
    fun setNotifyDownloadComplete(notify: Boolean) = settingsManager.setNotifyDownloadComplete(notify)
    fun setDefaultProvider(provider: String) = settingsManager.setDefaultProvider(provider)
    fun setDohProvider(doh: String) = settingsManager.setDohProvider(doh)
    fun setAppTheme(theme: String) = settingsManager.setAppTheme(theme)
    fun setDynamicColor(enabled: Boolean) = settingsManager.setDynamicColor(enabled)
    fun resetToDefaults() = settingsManager.resetToDefaults()

    private fun calculateFolderSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size: Long = 0
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateFolderSize(file) else file.length()
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0.0 MB"
        val df = DecimalFormat("#.##")
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            val gb = mb / 1024
            "${df.format(gb)} GB"
        } else {
            "${df.format(mb)} MB"
        }
    }
}
