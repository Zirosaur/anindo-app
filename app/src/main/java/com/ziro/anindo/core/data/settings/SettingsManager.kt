package com.ziro.anindo.core.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // Player Preferences
    private val _defaultQuality = MutableStateFlow(prefs.getString(KEY_QUALITY, "best") ?: "best")
    val defaultQuality: StateFlow<String> = _defaultQuality.asStateFlow()

    private val _defaultResizeMode = MutableStateFlow(prefs.getInt(KEY_RESIZE_MODE, 0))
    val defaultResizeMode: StateFlow<Int> = _defaultResizeMode.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f))
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    private val _seekIntervalSeconds = MutableStateFlow(prefs.getInt(KEY_SEEK_INTERVAL, 10))
    val seekIntervalSeconds: StateFlow<Int> = _seekIntervalSeconds.asStateFlow()

    private val _isGestureControlsEnabled = MutableStateFlow(prefs.getBoolean(KEY_GESTURES, true))
    val isGestureControlsEnabled: StateFlow<Boolean> = _isGestureControlsEnabled.asStateFlow()

    private val _isKeepScreenAwake = MutableStateFlow(prefs.getBoolean(KEY_KEEP_AWAKE, true))
    val isKeepScreenAwake: StateFlow<Boolean> = _isKeepScreenAwake.asStateFlow()

    private val _isAutoPauseScreenOff = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PAUSE, true))
    val isAutoPauseScreenOff: StateFlow<Boolean> = _isAutoPauseScreenOff.asStateFlow()

    // Download Preferences
    private val _downloadLocation = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_LOCATION, "public") ?: "public")
    val downloadLocation: StateFlow<String> = _downloadLocation.asStateFlow()

    private val _downloadWifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val downloadWifiOnly: StateFlow<Boolean> = _downloadWifiOnly.asStateFlow()

    private val _notifyDownloadComplete = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_DOWNLOAD, true))
    val notifyDownloadComplete: StateFlow<Boolean> = _notifyDownloadComplete.asStateFlow()

    // Network & Provider Preferences
    private val _defaultProvider = MutableStateFlow(prefs.getString(KEY_DEFAULT_PROVIDER, "all") ?: "all")
    val defaultProvider: StateFlow<String> = _defaultProvider.asStateFlow()

    private val _dohProvider = MutableStateFlow(prefs.getString(KEY_DOH_PROVIDER, "cloudflare") ?: "cloudflare")
    val dohProvider: StateFlow<String> = _dohProvider.asStateFlow()

    // Appearance Preferences
    private val _appTheme = MutableStateFlow(prefs.getString(KEY_APP_THEME, "system") ?: "system")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    // Modifiers
    fun setDefaultQuality(quality: String) {
        prefs.edit().putString(KEY_QUALITY, quality).apply()
        _defaultQuality.value = quality
    }

    fun setDefaultResizeMode(mode: Int) {
        prefs.edit().putInt(KEY_RESIZE_MODE, mode).apply()
        _defaultResizeMode.value = mode
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
        _defaultPlaybackSpeed.value = speed
    }

    fun setSeekInterval(seconds: Int) {
        prefs.edit().putInt(KEY_SEEK_INTERVAL, seconds).apply()
        _seekIntervalSeconds.value = seconds
    }

    fun setGestureControls(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURES, enabled).apply()
        _isGestureControlsEnabled.value = enabled
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_AWAKE, enabled).apply()
        _isKeepScreenAwake.value = enabled
    }

    fun setAutoPauseScreenOff(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PAUSE, enabled).apply()
        _isAutoPauseScreenOff.value = enabled
    }

    fun setDownloadLocation(location: String) {
        prefs.edit().putString(KEY_DOWNLOAD_LOCATION, location).apply()
        _downloadLocation.value = location
    }

    fun setDownloadWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        _downloadWifiOnly.value = wifiOnly
    }

    fun setNotifyDownloadComplete(notify: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_DOWNLOAD, notify).apply()
        _notifyDownloadComplete.value = notify
    }

    fun setDefaultProvider(provider: String) {
        prefs.edit().putString(KEY_DEFAULT_PROVIDER, provider).apply()
        _defaultProvider.value = provider
    }

    fun setDohProvider(doh: String) {
        prefs.edit().putString(KEY_DOH_PROVIDER, doh).apply()
        _dohProvider.value = doh
    }

    fun setAppTheme(theme: String) {
        prefs.edit().putString(KEY_APP_THEME, theme).apply()
        _appTheme.value = theme
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _defaultQuality.value = "best"
        _defaultResizeMode.value = 0
        _defaultPlaybackSpeed.value = 1.0f
        _seekIntervalSeconds.value = 10
        _isGestureControlsEnabled.value = true
        _isKeepScreenAwake.value = true
        _isAutoPauseScreenOff.value = true
        _downloadLocation.value = "public"
        _downloadWifiOnly.value = false
        _notifyDownloadComplete.value = true
        _defaultProvider.value = "all"
        _dohProvider.value = "cloudflare"
        _appTheme.value = "system"
        _dynamicColor.value = true
    }

    companion object {
        private const val PREF_FILE = "anindo_preferences"

        private const val KEY_QUALITY = "pref_quality"
        private const val KEY_RESIZE_MODE = "pref_resize_mode"
        private const val KEY_PLAYBACK_SPEED = "pref_playback_speed"
        private const val KEY_SEEK_INTERVAL = "pref_seek_interval"
        private const val KEY_GESTURES = "pref_gestures"
        private const val KEY_KEEP_AWAKE = "pref_keep_awake"
        private const val KEY_AUTO_PAUSE = "pref_auto_pause"

        private const val KEY_DOWNLOAD_LOCATION = "pref_download_location"
        private const val KEY_WIFI_ONLY = "pref_wifi_only"
        private const val KEY_NOTIFY_DOWNLOAD = "pref_notify_download"

        private const val KEY_DEFAULT_PROVIDER = "pref_default_provider"
        private const val KEY_DOH_PROVIDER = "pref_doh_provider"

        private const val KEY_APP_THEME = "pref_app_theme"
        private const val KEY_DYNAMIC_COLOR = "pref_dynamic_color"
    }
}
