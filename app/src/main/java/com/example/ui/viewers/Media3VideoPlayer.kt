package com.example.ui.viewers

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.FileEntry
import com.example.model.Formatters
import com.example.model.VideoAspectMode
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun Media3VideoPlayerOverlay(
    entry: FileEntry,
    uriString: String,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val exoPlayer = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var aspectMode by remember { mutableStateOf(VideoAspectMode.FIT) }
    var showControls by remember { mutableStateOf(true) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAspectMenu by remember { mutableStateOf(false) }

    // Gesture HUD state
    var gestureHudLabel by remember { mutableStateOf<String?>(null) }
    var gestureHudFraction by remember { mutableFloatStateOf(0f) }
    var seekFlashLabel by remember { mutableStateOf<String?>(null) }

    var currentBrightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f
        )
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(seekFlashLabel) {
        if (seekFlashLabel != null) {
            delay(750)
            seekFlashLabel = null
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video surface wrapped with AspectRatio handling
        val videoContainerModifier = when (aspectMode) {
            VideoAspectMode.RATIO_16_9 -> Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
            VideoAspectMode.RATIO_4_3 -> Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .align(Alignment.Center)
            else -> Modifier.fillMaxSize()
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = when (aspectMode) {
                    VideoAspectMode.FIT, VideoAspectMode.RATIO_16_9, VideoAspectMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    VideoAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    VideoAspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = videoContainerModifier
        )

        // Gesture capture layer: tap to toggle HUD, double-tap ±10s, vertical drag left=brightness / right=volume
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            val isLeft = offset.x < size.width / 2f
                            val delta = if (isLeft) -10_000L else 10_000L
                            val next = (exoPlayer.currentPosition + delta).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
                            exoPlayer.seekTo(next)
                            positionMs = next
                            seekFlashLabel = if (isLeft) "−10s" else "+10s"
                        }
                    )
                }
                .pointerInput(Unit) {
                    var activeLeftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            activeLeftSide = startOffset.x < size.width / 2f
                        },
                        onDragEnd = { gestureHudLabel = null },
                        onDragCancel = { gestureHudLabel = null },
                        onVerticalDrag = { _, dragAmount ->
                            val deltaFraction = -dragAmount / (size.height.coerceAtLeast(1) * 0.7f)
                            if (activeLeftSide) {
                                currentBrightness = (currentBrightness + deltaFraction).coerceIn(0.05f, 1.0f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = currentBrightness
                                    win.attributes = lp
                                }
                                gestureHudLabel = "Brightness"
                                gestureHudFraction = currentBrightness
                            } else {
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val curFrac = curVol.toFloat() / maxVol.toFloat()
                                val nextFrac = (curFrac + deltaFraction).coerceIn(0f, 1f)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (nextFrac * maxVol).roundToInt(),
                                    0
                                )
                                gestureHudLabel = "Volume"
                                gestureHudFraction = nextFrac
                            }
                        }
                    )
                }
        )

        // Vertical monochrome gesture HUD (Brightness / Volume)
        AnimatedVisibility(
            visible = gestureHudLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xDD111114),
                border = BorderStroke(1.dp, Color(0xFF3F3F46))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = if (gestureHudLabel == "Brightness") Icons.Outlined.Brightness6 else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.width(120.dp)) {
                        Text(
                            text = "${gestureHudLabel.orEmpty()} · ${(gestureHudFraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF3F3F46))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(gestureHudFraction.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }

        // Double-tap seek flash indicator
        AnimatedVisibility(
            visible = seekFlashLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xDD111114),
                border = BorderStroke(1.dp, Color(0xFF52525B))
            ) {
                Text(
                    text = seekFlashLabel.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Top & Bottom Monochrome Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xB809090B))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("video_close_button")
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close video", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Swipe left: brightness · Swipe right: volume · Double-tap: ±10s",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA1A1AA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Speed dropdown + fine slider popover
                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1C1C21),
                            border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                            onClick = { showSpeedMenu = true },
                            modifier = Modifier.testTag("video_speed_dropdown")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Outlined.Speed, contentDescription = "Playback speed", tint = Color.White, modifier = Modifier.size(16.dp))
                                Text(
                                    text = String.format(Locale.US, "%.2fx", playbackSpeed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                            containerColor = Color(0xFF141418),
                            border = BorderStroke(1.dp, Color(0xFF3F3F46))
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(220.dp)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "SPEED · ${String.format(Locale.US, "%.2fx", playbackSpeed)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA1A1AA)
                                )
                                Slider(
                                    value = playbackSpeed,
                                    onValueChange = { playbackSpeed = (it * 20f).roundToInt() / 20f },
                                    valueRange = 0.25f..2.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color(0xFF3F3F46)
                                    ),
                                    modifier = Modifier.testTag("video_speed_slider")
                                )
                            }
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${preset}x",
                                            color = if (playbackSpeed == preset) Color.White else Color(0xFFA1A1AA)
                                        )
                                    },
                                    onClick = {
                                        playbackSpeed = preset
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Aspect Ratio dropdown
                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1C1C21),
                            border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                            onClick = { showAspectMenu = true },
                            modifier = Modifier.testTag("video_aspect_dropdown")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Outlined.AspectRatio, contentDescription = "Aspect ratio", tint = Color.White, modifier = Modifier.size(16.dp))
                                Text(
                                    text = aspectMode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showAspectMenu,
                            onDismissRequest = { showAspectMenu = false },
                            containerColor = Color(0xFF141418),
                            border = BorderStroke(1.dp, Color(0xFF3F3F46))
                        ) {
                            VideoAspectMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.label,
                                            color = if (aspectMode == mode) Color.White else Color(0xFFA1A1AA)
                                        )
                                    },
                                    onClick = {
                                        aspectMode = mode
                                        showAspectMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Center Transport Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            positionMs = target
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x99111114))
                    ) {
                        Icon(Icons.Outlined.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .testTag("video_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L))
                            exoPlayer.seekTo(target)
                            positionMs = target
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x99111114))
                    ) {
                        Icon(Icons.Outlined.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
                    }
                }

                // Bottom Seek Slider Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xB809090B))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = Formatters.formatDuration(positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Text(
                            text = Formatters.formatDuration(durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                    Slider(
                        value = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { frac ->
                            val target = (frac * durationMs).toLong()
                            exoPlayer.seekTo(target)
                            positionMs = target
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF3F3F46)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_seek_slider")
                    )
                }
            }
        }
    }
}

@Composable
fun InlineAudioPlayerBar(
    entry: FileEntry,
    uriString: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var speedExpanded by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { durationMs = player.duration.coerceAtLeast(0L) }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(300)
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { speedExpanded = true }
                    ) {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = speedExpanded,
                        onDismissRequest = { speedExpanded = false }
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s}x") },
                                onClick = {
                                    speed = s
                                    player.playbackParameters = PlaybackParameters(s)
                                    speedExpanded = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause audio" else "Play audio"
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close audio player")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(Formatters.formatDuration(positionMs), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = { frac ->
                        val target = (frac * durationMs).toLong()
                        player.seekTo(target)
                        positionMs = target
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(Formatters.formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
