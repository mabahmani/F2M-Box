package com.mab.f2mbox.desktop.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.SwingPanel
import com.mab.f2mbox.desktop.platform.Strings
import com.mab.f2mbox.desktop.player.EngineEvent
import com.mab.f2mbox.desktop.player.TrackInfo
import com.mab.f2mbox.desktop.player.VlcDiscovery
import com.mab.f2mbox.desktop.player.VlcPlayerEngine
import java.util.Locale
import kotlinx.coroutines.delay

private const val SeekStepMs = 10_000L

/** How long the overlay stays up after the last interaction. */
private const val ControlsHideDelayMs = 4_000L

/** Minimum distance between two seeks, so key auto-repeat doesn't spam. */
private const val SeekThrottleMs = 120L

/** How long the hidden-mode seek OSD stays up. */
private const val SeekOsdDelayMs = 900L

private const val OneHourMs = 3_600_000L
private const val UnknownDurationLabel = "--:--"

/** libvlc's "no track selected" marker. */
private const val TrackOff = -1

/**
 * In-app player, desktop: the Android TV chrome (top scrim, transport row,
 * seek bar, speed/track menus, seek OSD, error card) drives a
 * [VlcPlayerEngine] whose video surface is an AWT component under a
 * [SwingPanel]; the Compose overlay draws on top of it.
 *
 * Parity cut (plan Task 7): the subtitle-styling panel and the custom
 * SubtitleView layer are not ported — libvlc renders the selected subtitle
 * track itself. Everything else is carried over, including the layered
 * Back behavior: Backspace/ESC cancel a menu, then hide the controls, then
 * leave the screen.
 */
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    registerEsc: ((() -> Unit)?) -> Unit = {},
) {
    val discovery = remember { VlcDiscovery.findLibVlc() }

    if (discovery.isFailure) {
        VlcSetupCard(
            message = discovery.exceptionOrNull()?.message ?: Strings.playerError,
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
        return
    }

    // Playback snapshot, driven by the engine's events (no polling).
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var everPlayed by remember { mutableStateOf(false) }
    // Why playback failed: null while healthy. libvlc reports no codec
    // name in its error event, so the codec detail of the Android build
    // has no counterpart here.
    var errorKind by remember { mutableStateOf<EngineEvent.ErrorKind?>(null) }
    var audioTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var subtitleTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var currentAudioId by remember { mutableIntStateOf(TrackOff) }
    var currentSubtitleId by remember { mutableIntStateOf(TrackOff) }

    var engine by remember { mutableStateOf<VlcPlayerEngine?>(null) }
    // Bumped by the error card's retry: recreates the engine fresh.
    var retryNonce by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    DisposableEffect(url, retryNonce) {
        val created = VlcPlayerEngine(
            url = url,
            extraArgs = emptyList(),
            listener = { event ->
                when (event) {
                    is EngineEvent.Playing -> {
                        isPlaying = true
                        everPlayed = true
                    }
                    is EngineEvent.Paused -> isPlaying = false
                    is EngineEvent.Position -> {
                        positionMs = event.ms.coerceAtLeast(0L)
                        durationMs = event.durationMs.takeIf { it > 0 } ?: 0L
                    }
                    is EngineEvent.Rate -> playbackSpeed = event.rate
                    is EngineEvent.TracksReady -> {
                        audioTracks = event.audio.filter { it.id >= 0 }
                        subtitleTracks = event.subtitles.filter { it.id >= 0 }
                        currentAudioId = event.currentAudio
                        currentSubtitleId = event.currentSubtitle
                    }
                    is EngineEvent.Error -> errorKind = event.kind
                }
            },
        )
        engine = created
        onDispose { created.release() }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every interaction so the auto-hide timer restarts.
    var interaction by remember { mutableIntStateOf(0) }

    var speedMenuVisible by remember { mutableStateOf(false) }
    var subtitleMenuVisible by remember { mutableStateOf(false) }
    var audioMenuVisible by remember { mutableStateOf(false) }

    val isEnded = durationMs > 0 && positionMs >= durationMs - 1_000

    // Layered Back: cancel a menu, then hide the controls, then leave —
    // the desktop port of the Android BackHandler chain. ESC reaches it
    // through the app root's escHandler; Backspace is handled in-screen.
    val layeredBack: () -> Unit = {
        when {
            speedMenuVisible || subtitleMenuVisible || audioMenuVisible -> {
                speedMenuVisible = false
                subtitleMenuVisible = false
                audioMenuVisible = false
            }
            controlsVisible -> controlsVisible = false
            else -> onNavigateBack()
        }
    }
    DisposableEffect(Unit) {
        registerEsc(layeredBack)
        onDispose { registerEsc(null) }
    }

    val playPauseFocus = remember { FocusRequester() }
    val surfaceFocus = remember { FocusRequester() }
    val speedButtonFocus = remember { FocusRequester() }
    val subtitleButtonFocus = remember { FocusRequester() }
    val audioButtonFocus = remember { FocusRequester() }

    // When a selector menu closes, focus returns to the button that opened
    // it (guarded so the initial composition doesn't steal focus).
    var wasMenuOpen by remember { mutableStateOf(false) }
    var menuReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(speedMenuVisible, subtitleMenuVisible, audioMenuVisible) {
        val anyOpen = speedMenuVisible || subtitleMenuVisible || audioMenuVisible
        if (anyOpen && !wasMenuOpen) {
            menuReturnFocus = when {
                speedMenuVisible -> speedButtonFocus
                audioMenuVisible -> audioButtonFocus
                else -> subtitleButtonFocus
            }
        }
        if (wasMenuOpen && !anyOpen) {
            menuReturnFocus?.let { target -> while (!target.requestFocus()) delay(50) }
        }
        wasMenuOpen = anyOpen
    }

    // Focus follows the overlay: controls take it when they appear, the
    // surface takes it back when they hide, so key events always land
    // somewhere that handles them.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            while (!playPauseFocus.requestFocus()) delay(50)
        } else {
            while (!surfaceFocus.requestFocus()) delay(50)
        }
    }

    // Seek OSD: shown while seeking from the hidden-controls mode.
    var seekOsdVisible by remember { mutableStateOf(false) }
    var seekOsdNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekOsdVisible, seekOsdNonce) {
        if (seekOsdVisible) {
            delay(SeekOsdDelayMs)
            seekOsdVisible = false
        }
    }

    val currentEngine: VlcPlayerEngine? = engine

    // All seeks funnel through one throttled entry point: key auto-repeat
    // fires far faster than a player should re-seek.
    val lastSeekAt = remember { mutableLongStateOf(0L) }
    val seek: (Long) -> Unit = { deltaMs ->
        val now = System.currentTimeMillis()
        if (now - lastSeekAt.longValue >= SeekThrottleMs) {
            lastSeekAt.longValue = now
            currentEngine?.seekBy(deltaMs)
        }
    }

    // The player chrome is LTR: timelines read left-to-right everywhere.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(surfaceFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        // Hidden mode: Left/Right belong to seeking.
                        Key.DirectionLeft -> {
                            if (!controlsVisible) {
                                seek(-SeekStepMs)
                                seekOsdVisible = true
                                seekOsdNonce++
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (!controlsVisible) {
                                seek(SeekStepMs)
                                seekOsdVisible = true
                                seekOsdNonce++
                                true
                            } else {
                                false
                            }
                        }
                        // Hidden mode: Center/Up/Down/Enter/Space open the
                        // controls; visible mode: everything falls through
                        // to the focused control.
                        Key.DirectionCenter, Key.Enter, Key.Spacebar,
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        // ESC is layered-back at the app root; Backspace
                        // does the same thing here.
                        Key.Backspace -> {
                            layeredBack()
                            true
                        }
                        else -> false
                    }
                }
                // Bubble phase: any key the focused control didn't consume
                // (focus moves) still counts as interaction and keeps the
                // overlay up.
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) interaction++
                    false
                }
        ) {
            // The libvlc video surface (callback renderer's JComponent).
            // Non-focusable so key events stay in the Compose focus system.
            currentEngine?.let { e ->
                SwingPanel(
                    factory = {
                        e.componentSurface.apply { isFocusable = false }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (!everPlayed && errorKind == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(56.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            errorKind?.let { kind ->
                PlayerError(
                    kind = kind,
                    onRetry = {
                        errorKind = null
                        everPlayed = false
                        positionMs = 0
                        retryNonce++
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            PlayerControls(
                engine = currentEngine,
                title = title,
                visible = controlsVisible,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                isEnded = isEnded,
                playbackSpeed = playbackSpeed,
                speedMenuVisible = speedMenuVisible,
                onToggleSpeedMenu = {
                    speedMenuVisible = !speedMenuVisible
                    if (speedMenuVisible) subtitleMenuVisible = false
                    interaction++
                },
                onSpeedSelect = {
                    playbackSpeed = it
                    currentEngine?.setRate(it)
                    speedMenuVisible = false
                    interaction++
                },
                subtitleTracks = subtitleTracks,
                currentSubtitleId = currentSubtitleId,
                subtitleMenuVisible = subtitleMenuVisible,
                onToggleSubtitleMenu = {
                    subtitleMenuVisible = !subtitleMenuVisible
                    if (subtitleMenuVisible) {
                        speedMenuVisible = false
                        audioMenuVisible = false
                    }
                    interaction++
                },
                onSubtitleSelect = { trackId ->
                    currentEngine?.selectSubtitle(trackId)
                    subtitleMenuVisible = false
                    interaction++
                },
                audioTracks = audioTracks,
                currentAudioId = currentAudioId,
                audioMenuVisible = audioMenuVisible,
                onToggleAudioMenu = {
                    audioMenuVisible = !audioMenuVisible
                    if (audioMenuVisible) {
                        speedMenuVisible = false
                        subtitleMenuVisible = false
                    }
                    interaction++
                },
                onAudioSelect = { trackId ->
                    currentEngine?.selectAudio(trackId)
                    audioMenuVisible = false
                    interaction++
                },
                onSeek = {
                    seek(it)
                    interaction++
                },
                onInteraction = { interaction++ },
                onBack = { controlsVisible = false },
                playPauseFocus = playPauseFocus,
                speedButtonFocus = speedButtonFocus,
                subtitleButtonFocus = subtitleButtonFocus,
                audioButtonFocus = audioButtonFocus,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (controlsVisible) 1f else 0f)
            )

            if (!controlsVisible && seekOsdVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 48.dp, vertical = 24.dp)
                ) {
                    SeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = seek,
                        onTogglePlayPause = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * The overlay chrome: a top scrim with the back button and title, a bottom
 * scrim with the transport controls and seek bar. Always composed so the
 * focus requesters stay attached; [visible] only fades it in and out.
 */
@Composable
private fun PlayerControls(
    engine: VlcPlayerEngine?,
    title: String,
    visible: Boolean,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isEnded: Boolean,
    playbackSpeed: Float,
    speedMenuVisible: Boolean,
    onToggleSpeedMenu: () -> Unit,
    onSpeedSelect: (Float) -> Unit,
    subtitleTracks: List<TrackInfo>,
    currentSubtitleId: Int,
    subtitleMenuVisible: Boolean,
    onToggleSubtitleMenu: () -> Unit,
    onSubtitleSelect: (Int) -> Unit,
    audioTracks: List<TrackInfo>,
    currentAudioId: Int,
    audioMenuVisible: Boolean,
    onToggleAudioMenu: () -> Unit,
    onAudioSelect: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    onBack: () -> Unit,
    playPauseFocus: FocusRequester,
    speedButtonFocus: FocusRequester,
    subtitleButtonFocus: FocusRequester,
    audioButtonFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Shared play/pause/replay action: the play button and the focused
        // seek bar (Center key) both trigger it.
        val togglePlayPause = {
            if (engine != null) {
                when {
                    isEnded -> {
                        engine.seekTo(0)
                        engine.play()
                    }
                    isPlaying -> engine.pause()
                    else -> engine.play()
                }
            }
            onInteraction()
        }

        // Top scrim: back button + title.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlayerButton(onClick = onBack, description = Strings.back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                text = title.ifBlank { Strings.trailer },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Bottom scrim: transport row + seek bar + times.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(horizontal = 48.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (speedMenuVisible) {
                val speeds = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }
                ChoiceMenu(
                    options = speeds.map { speedLabel(it) },
                    selectedIndex = speeds.indexOf(playbackSpeed).coerceAtLeast(0),
                    onSelect = { onSpeedSelect(speeds[it]) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (subtitleMenuVisible) {
                val options = buildList {
                    add(Strings.playerSubtitlesOff)
                    subtitleTracks.forEach { add(it.label) }
                }
                val selected = subtitleTracks.indexOfFirst { it.id == currentSubtitleId }
                ChoiceMenu(
                    options = options,
                    selectedIndex = if (selected >= 0) selected + 1 else 0,
                    onSelect = { index ->
                        onSubtitleSelect(
                            if (index == 0) TrackOff else subtitleTracks[index - 1].id
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (audioMenuVisible) {
                val options = buildList {
                    add(Strings.playerAudioAuto)
                    audioTracks.forEach { add(it.label) }
                }
                val selected = audioTracks.indexOfFirst { it.id == currentAudioId }
                ChoiceMenu(
                    options = options,
                    selectedIndex = if (selected >= 0) selected + 1 else 0,
                    onSelect = { index ->
                        onAudioSelect(
                            // "Auto" = the first track; libvlc has no explicit
                            // auto marker (-1 would mute entirely).
                            if (index == 0) audioTracks.firstOrNull()?.id ?: TrackOff
                            else audioTracks[index - 1].id
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerButton(
                    onClick = { onSeek(-SeekStepMs) },
                    description = Strings.playerSkipBack,
                    repeatable = true
                ) {
                    Icon(Icons.Default.Replay10, null, tint = Color.White)
                }
                PlayerButton(
                    onClick = togglePlayPause,
                    description = when {
                        isEnded -> Strings.playerReplay
                        isPlaying -> Strings.playerPause
                        else -> Strings.playerPlay
                    },
                    modifier = Modifier.focusRequester(playPauseFocus),
                    emphasized = true
                ) {
                    Icon(
                        when {
                            isEnded -> Icons.Default.Replay
                            isPlaying -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        null,
                        tint = Color.White
                    )
                }
                PlayerButton(
                    onClick = { onSeek(SeekStepMs) },
                    description = Strings.playerSkipForward,
                    repeatable = true
                ) {
                    Icon(Icons.Default.Forward10, null, tint = Color.White)
                }
                PlayerButton(
                    onClick = onToggleSpeedMenu,
                    description = Strings.playerSpeed,
                    modifier = Modifier.focusRequester(speedButtonFocus)
                ) {
                    Text(
                        text = speedLabel(playbackSpeed),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                }
                // Only offered when the video actually carries text tracks.
                if (subtitleTracks.isNotEmpty()) {
                    val subtitlesOn = subtitleTracks.any { it.id == currentSubtitleId }
                    PlayerButton(
                        onClick = onToggleSubtitleMenu,
                        description = Strings.playerSubtitles,
                        modifier = Modifier.focusRequester(subtitleButtonFocus)
                    ) {
                        Icon(
                            if (subtitlesOn) {
                                Icons.Default.ClosedCaption
                            } else {
                                Icons.Default.ClosedCaptionOff
                            },
                            null,
                            tint = Color.White
                        )
                    }
                }
                // Only offered when there's more than one audio track to pick.
                if (audioTracks.size > 1) {
                    PlayerButton(
                        onClick = onToggleAudioMenu,
                        description = Strings.playerAudio,
                        modifier = Modifier.focusRequester(audioButtonFocus)
                    ) {
                        Icon(Icons.Default.Audiotrack, null, tint = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                onTogglePlayPause = togglePlayPause,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The seek bar. Focusable in the controls mode: Left/Right scrub ±10s per
 * key press (auto-repeat keeps scrubbing), Up/Down fall through so focus
 * returns to the transport row, and Center toggles play/pause. Also
 * tap-to-seek for mouse users.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    // Both times use the same shape (h:mm:ss once the video is an hour
    // long) so they line up and the bar stays centered between them.
    val showHours = durationMs >= OneHourMs
    val timeWidth = if (showHours) 92.dp else 60.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Elapsed time: fixed width so the digits changing don't shift the
        // bar around.
        Text(
            text = formatTime(positionMs, showHours),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(timeWidth)
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .border(
                    width = if (isFocused) 1.dp else 0.dp,
                    color = if (isFocused) Color.White.copy(alpha = 0.7f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeek(-SeekStepMs)
                            true
                        }
                        Key.DirectionRight -> {
                            onSeek(SeekStepMs)
                            true
                        }
                        // Center on the focused bar toggles play/pause.
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            onTogglePlayPause()
                            true
                        }
                        else -> false
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            onSeek((offset.x / size.width.toFloat() * durationMs).toLong())
                        }
                    }
                }
        ) {
            val trackHeight = 6.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.3f))
                    .align(Alignment.CenterStart)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.CenterStart)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * fraction - 7.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Text(
            text = if (durationMs > 0) {
                formatTime(durationMs, showHours)
            } else {
                UnknownDurationLabel
            },
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(timeWidth)
        )
    }
}

/**
 * A circular transport button with the app's standard focus treatment:
 * scale up on focus and fill with the primary color. Activates on the
 * Center/Enter KeyDown; one-shot buttons debounce the auto-repeat
 * key-downs (desktop has no repeat count, so a timestamp guards them).
 */
@Composable
private fun PlayerButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    repeatable: Boolean = false,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var lastActivationAt by remember { mutableLongStateOf(0L) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(150)
    )
    Box(
        modifier = modifier
            .size(if (emphasized) 64.dp else 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> MaterialTheme.colorScheme.primary
                    emphasized -> Color.White.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.15f)
                }
            )
            .semantics { contentDescription = description }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val activationKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.Spacebar
                if (!activationKey) return@onKeyEvent false
                if (!repeatable) {
                    val now = System.currentTimeMillis()
                    if (now - lastActivationAt < 250) return@onKeyEvent true
                    lastActivationAt = now
                }
                onClick()
                true
            }
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Generic selector menu: a row of option chips with TV-style roving
 * highlight. The row itself is the single focus target — Left/Right move
 * the highlight, Center picks the highlighted option and closes, and
 * everything else is consumed so the menu stays modal (ESC reaches the
 * app root's escHandler, which closes the menu).
 */
@Composable
private fun ChoiceMenu(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var highlighted by remember(selectedIndex) {
        mutableIntStateOf(selectedIndex.coerceIn(0, options.lastIndex))
    }
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        while (!menuFocus.requestFocus()) delay(50)
    }
    Row(
        modifier = modifier
            .focusRequester(menuFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        highlighted = (highlighted - 1).coerceAtLeast(0)
                        true
                    }
                    Key.DirectionRight -> {
                        highlighted = (highlighted + 1).coerceAtMost(options.lastIndex)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                        onSelect(highlighted)
                        true
                    }
                    // Consume everything else so the menu stays modal.
                    else -> true
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isHighlighted = index == highlighted
            val isCurrent = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isHighlighted -> MaterialTheme.colorScheme.primary
                            isCurrent -> Color.White.copy(alpha = 0.25f)
                            else -> Color.White.copy(alpha = 0.15f)
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

/** Centered playback-failure card with a retry action. */
@Composable
private fun PlayerError(
    kind: EngineEvent.ErrorKind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when (kind) {
                EngineEvent.ErrorKind.Decode -> Strings.playerErrorDecode
                EngineEvent.ErrorKind.Network -> Strings.playerErrorNetwork
                EngineEvent.ErrorKind.Source -> Strings.playerError
            },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        Button(onClick = onRetry) {
            Text(Strings.retry)
        }
    }
}

/**
 * The no-VLC machine path: the player can't run without the native
 * libvlc, so instead of a cryptic crash the user gets the discovery
 * message (with the install hint) and a way back out.
 */
@Composable
private fun VlcSetupCard(
    message: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 64.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onNavigateBack) {
                Text(Strings.back)
            }
        }
    }
}

/** Formats a position as m:ss or h:mm:ss (latin digits for timecodes). */
private fun formatTime(ms: Long, showHours: Boolean = false): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (showHours || hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
