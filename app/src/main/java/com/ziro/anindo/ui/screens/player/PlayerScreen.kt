package com.ziro.anindo.ui.screens.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ziro.anindo.AnindoApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    title: String,
    animeId: String = "",
    animeTitle: String = "",
    posterUrl: String = "",
    episodeUrl: String = "",
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = AnindoApp.instance.repository
    val scope = rememberCoroutineScope()

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    // Gestures HUD feedback state
    var volumePercent by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()) }
    var brightnessPercent by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f) }
    var showGestureHud by remember { mutableStateOf(false) }
    var gestureHudText by remember { mutableStateOf("") }
    var gestureHudIcon by remember { mutableStateOf(Icons.Default.VolumeUp) }

    // Playback settings
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        )
    }
    val resizeLabels = remember { listOf("Fit", "Zoom", "Stretch") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(streamUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Resume saved playback position
    LaunchedEffect(episodeUrl) {
        if (episodeUrl.isNotBlank()) {
            val progress = repository.getEpisodeProgress(episodeUrl)
            if (progress != null && progress.playbackPositionMs > 5000L && !progress.isCompleted) {
                exoPlayer.seekTo(progress.playbackPositionMs)
            }
        }
    }

    // Player event listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            // Save final progress on exit
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (episodeUrl.isNotBlank() && dur > 0) {
                scope.launch {
                    repository.saveEpisodeProgress(
                        animeId = animeId,
                        animeTitle = animeTitle,
                        animePosterUrl = posterUrl,
                        episodeUrl = episodeUrl,
                        episodeTitle = title,
                        episodeNumber = "",
                        positionMs = pos,
                        durationMs = dur
                    )
                }
            }
            exoPlayer.release()
            // Reset screen brightness
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = -1f
                win.attributes = lp
            }
        }
    }

    // Periodic progress ticker and auto-save
    LaunchedEffect(exoPlayer) {
        var tickCounter = 0
        while (isActive) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                tickCounter++
                if (tickCounter % 5 == 0 && episodeUrl.isNotBlank() && duration > 0) {
                    repository.saveEpisodeProgress(
                        animeId = animeId,
                        animeTitle = animeTitle,
                        animePosterUrl = posterUrl,
                        episodeUrl = episodeUrl,
                        episodeTitle = title,
                        episodeNumber = "",
                        positionMs = currentPosition,
                        durationMs = duration
                    )
                }
            }
            delay(1000L)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible && !isLocked) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = {
                        isControlsVisible = !isControlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val half = size.width / 2f
                            if (offset.x < half) {
                                // Double tap left: rewind 10s
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                                gestureHudIcon = Icons.Default.Replay10
                                gestureHudText = "-10s"
                            } else {
                                // Double tap right: forward 10s
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration))
                                gestureHudIcon = Icons.Default.Forward10
                                gestureHudText = "+10s"
                            }
                            showGestureHud = true
                            scope.launch {
                                delay(700L)
                                showGestureHud = false
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { showGestureHud = true },
                    onDragEnd = {
                        scope.launch {
                            delay(600L)
                            showGestureHud = false
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / 600f
                        val isRightSide = change.position.x > (size.width / 2f)

                        if (isRightSide) {
                            // Volume control (Right side)
                            val newVol = (volumePercent + delta).coerceIn(0f, 1f)
                            volumePercent = newVol
                            val targetStream = (newVol * maxVolume).toInt()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStream, 0)
                            gestureHudIcon = Icons.Default.VolumeUp
                            gestureHudText = "Volume ${(newVol * 100).toInt()}%"
                        } else {
                            // Brightness control (Left side)
                            val newBri = (brightnessPercent + delta).coerceIn(0.01f, 1f)
                            brightnessPercent = newBri
                            activity?.window?.let { win ->
                                val lp = win.attributes
                                lp.screenBrightness = newBri
                                win.attributes = lp
                            }
                            gestureHudIcon = Icons.Default.BrightnessMedium
                            gestureHudText = "Kecerahan ${(newBri * 100).toInt()}%"
                        }
                    }
                )
            }
    ) {
        // Android ExoPlayer Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Custom overlay UI
                    resizeMode = resizeModes[resizeModeIndex]
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                }
            },
            update = { view ->
                view.resizeMode = resizeModes[resizeModeIndex]
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Spinner
        if (isBuffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Gesture HUD Badge (Center Overlay)
        AnimatedVisibility(
            visible = showGestureHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = gestureHudIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = gestureHudText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Top & Bottom Controls Overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top App Bar Shade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                )

                // Top Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    // Aspect Ratio Button
                    IconButton(
                        onClick = {
                            resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                            playerViewRef?.resizeMode = resizeModes[resizeModeIndex]
                        }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Rasio Layar", tint = Color.White)
                            Text(
                                text = resizeLabels[resizeModeIndex],
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Playback Speed Button
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Icon(Icons.Default.Speed, contentDescription = "Kecepatan", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        playbackSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Lock Screen Toggle
                IconButton(
                    onClick = { isLocked = !isLocked },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Kunci Layar",
                        tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // Center Play / Pause Controls (hidden if locked)
                if (!isLocked) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)) },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)) },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White)
                        }
                    }
                }

                // Bottom Controls Shade
                if (!isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    // Bottom Time & Seek Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Slider(
                            value = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { percent ->
                                if (duration > 0) {
                                    val target = (percent * duration).toLong()
                                    currentPosition = target
                                    exoPlayer.seekTo(target)
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
