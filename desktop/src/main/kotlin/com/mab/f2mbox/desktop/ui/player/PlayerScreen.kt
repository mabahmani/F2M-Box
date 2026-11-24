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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.mab.f2mbox.desktop.player.SubtitleCue
import com.mab.f2mbox.desktop.player.SubtitleStyle
import com.mab.f2mbox.desktop.player.TrackInfo
import com.mab.f2mbox.desktop.player.ExternalPlay
import com.mab.f2mbox.desktop.player.VlcDiscovery
import com.mab.f2mbox.desktop.player.VlcPlayerEngine
import com.mab.f2mbox.desktop.ui.common.LocalSubtitleStyleStore
import java.awt.BorderLayout
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
 * seek bar, speed/track menus, subtitle-customize panel, seek OSD, error
 * card) drives a [VlcPlayerEngine] whose video surface is an AWT component
 * under a [SwingPanel]; the Compose overlay draws on top of it.
 *
 * Subtitles are rendered by libvlc itself (both embedded text tracks and
 * styling), so there is no custom SubtitleView layer as on Android. The
 * subtitle look is applied as global freetype args when the engine is built
 * — see [SubtitleStyle] and the customize panel; saving a change rebuilds
 * the engine at the current position. Backspace/ESC walk the same layered
 * Back chain: cancel a menu/panel, hide the controls, then leave the screen.
 */
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleUrl: String? = null,
    registerEsc: ((() -> Unit)?) -> Unit = {},
) {
    val discovery = remember { VlcDiscovery.findLibVlc() }

    if (discovery.isFailure) {
        VlcSetupCard(
            message = discovery.exceptionOrNull()?.message ?: Strings.playerError,
            url = url,
            onPlayExternally = { ExternalPlay.open(url) },
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

    // Subtitle look. The saved style is loaded from the desktop store; the
    // panel edits a draft; Save persists it and bumps [styleRevision] so the
    // engine is rebuilt with the new global freetype args, resuming at the
    // current position. (libvlc only reads subtitle styling from global
    // factory args, not per-media options — see SubtitleStyle.)
    val styleStore = LocalSubtitleStyleStore.current
    var subtitleStyle by remember { mutableStateOf(styleStore.load()) }
    var subtitleDraft by remember { mutableStateOf(subtitleStyle) }
    var subtitlePanelVisible by remember { mutableStateOf(false) }
    // Position to resume at when an engine rebuild interrupts playback.
    var resumeAtMs by remember { mutableLongStateOf(0L) }
    var styleRevision by remember { mutableIntStateOf(0) }

    // One persistent AWT host for the libvlc video surface. Tearing down and
    // recreating the Compose<->AWT interop (as keying a SwingPanel by engine
    // does) leaves a black frame and can strand playback; instead we mount
    // this host once and swap the engine's surface inside it when the engine
    // is rebuilt (e.g. after saving subtitle styling).
    // Dark, opaque AWT host: an empty default JPanel is light grey and would
    // flash white behind the spinner while the engine builds off-thread.
    val videoHost = remember {
        JPanel(BorderLayout()).apply {
            background = java.awt.Color.BLACK
            isOpaque = true
        }
    }

    // Build the engine off the UI thread: constructing libVLC's
    // MediaPlayerFactory (and its AWT component) is a multi-second native
    // initialisation. Building it inline during composition froze the window
    // and delayed the player from appearing; now navigation is instant and a
    // spinner shows until the first `playing` event. The engine's surface is
    // mounted/released by the DisposableEffect(engine) below.
    LaunchedEffect(url, retryNonce, styleRevision) {
        errorKind = null
        val startAt = resumeAtMs
        val holder = AtomicReference<VlcPlayerEngine?>(null)
        try {
            val created = withContext(Dispatchers.IO) {
                VlcPlayerEngine(
                    url = url,
                    extraArgs = emptyList(),
                    subtitleStyle = subtitleStyle,
                    startPositionMs = startAt,
                    nativeLocation = discovery.getOrNull(),
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
                ).also { holder.set(it) }
            }
            // Hand ownership to the state; DisposableEffect(engine) releases it
            // on the next change or when this screen leaves composition.
            engine = created
            holder.set(null)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            errorKind = EngineEvent.ErrorKind.Source
        } finally {
            // Cancelled/failed before hand-off (or after building but before
            // assignment): release whatever we built so the native resources
            // are not leaked.
            holder.getAndSet(null)?.release()
        }
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
    // Mouse behavior over the video surface: movement/click reveals the
    // overlay (and resets the auto-hide timer). Read/written from the AWT
    // dispatch thread — the surface's listener calls this.
    val mouseWake: () -> Unit = {
        controlsVisible = true
        interaction++
    }

    val layeredBack: () -> Unit = {
        when {
            subtitlePanelVisible -> {
                // Cancel the panel: discard the draft.
                subtitleDraft = subtitleStyle
                subtitlePanelVisible = false
            }
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

    // Mount the current engine's surface into the stable host and keep it in
    // sync as the engine is rebuilt. Interop is created once (below); only
    // the child AWT component swaps here. The engine built asynchronously by
    // the LaunchedEffect above is owned here, so it is released when the
    // engine changes or the screen leaves composition.
    DisposableEffect(engine) {
        val boundEngine = engine
        val surface = boundEngine?.componentSurface
        val wake: java.awt.event.MouseAdapter? = surface?.let {
            object : java.awt.event.MouseAdapter() {
                override fun mouseMoved(event: java.awt.event.MouseEvent) = mouseWake()

                override fun mouseClicked(event: java.awt.event.MouseEvent) {
                    // Single click reveals (or keeps up) the overlay; double
                    // click leaves the screen.
                    if (event.clickCount == 2) layeredBack() else mouseWake()
                }
            }
        }
        if (surface != null && wake != null) {
            surface.isFocusable = false
            // Match the dark host so the brief gap before the first frame is
            // black rather than the component's default light background.
            surface.isOpaque = true
            surface.background = java.awt.Color.BLACK
            surface.addMouseListener(wake)
            surface.addMouseMotionListener(wake)
            SwingUtilities.invokeLater {
                videoHost.removeAll()
                videoHost.add(surface, BorderLayout.CENTER)
                videoHost.revalidate()
                videoHost.repaint()
            }
        }
        onDispose {
            if (surface != null && wake != null) {
                surface.removeMouseListener(wake)
                surface.removeMouseMotionListener(wake)
            }
            if (boundEngine != null) {
                // Detach this engine's surface before releasing it, so a
                // style-change rebuild never shows (or releases under) a
                // surface that is still mounted.
                SwingUtilities.invokeLater {
                    if (surface != null && surface.parent === videoHost) {
                        videoHost.remove(surface)
                        videoHost.revalidate()
                        videoHost.repaint()
                    }
                }
                boundEngine.release()
            }
        }
    }

    // Panel actions. Save persists the draft and rebuilds the engine (at the
    // current position) so the new global freetype args take effect; Cancel
    // drops the draft.
    val openSubtitleSettings: () -> Unit = {
        subtitleDraft = subtitleStyle
        subtitleMenuVisible = false
        subtitlePanelVisible = true
        interaction++
    }
    val saveSubtitlePanel: () -> Unit = {
        styleStore.save(subtitleDraft)
        subtitleStyle = subtitleDraft
        resumeAtMs = positionMs
        subtitlePanelVisible = false
        styleRevision++
        interaction++
    }
    val cancelSubtitlePanel: () -> Unit = {
        subtitleDraft = subtitleStyle
        subtitlePanelVisible = false
        interaction++
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
    LaunchedEffect(speedMenuVisible, subtitleMenuVisible, audioMenuVisible, subtitlePanelVisible) {
        val anyOpen = speedMenuVisible || subtitleMenuVisible || audioMenuVisible || subtitlePanelVisible
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

    // Auto-hide: the overlay goes away after [ControlsHideDelayMs] without
    // interaction (the Android port's cadence). Every interaction bumps
    // [interaction], restarting this timer.
    LaunchedEffect(interaction) {
        if (controlsVisible) {
            delay(ControlsHideDelayMs)
            if (!speedMenuVisible && !subtitleMenuVisible && !audioMenuVisible && !subtitlePanelVisible) {
                controlsVisible = false
            }
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
            // One persistent AWT host for the libvlc video surface. It is
            // mounted once (compose interop is stable across engine
            // rebuilds); the engine's surface is added/swapped inside it by
            // the DisposableEffect above. Mouse handling lives on the
            // surface itself (with interop blending the interop view catches
            // mouse events first, so an AWT listener — not Compose
            // pointerInput — is the reliable path).
            SwingPanel(
                factory = { videoHost },
                modifier = Modifier.fillMaxSize()
            )

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
                        resumeAtMs = 0
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
                onOpenSubtitleSettings = openSubtitleSettings,
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

            if (subtitlePanelVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    SubtitleSettingsPanel(
                        style = subtitleDraft,
                        onChange = { subtitleDraft = it },
                        onSave = saveSubtitlePanel,
                        onCancel = cancelSubtitlePanel
                    )
                }
            }

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
    onOpenSubtitleSettings: () -> Unit,
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
                    add(Strings.playerSubtitleCustomize)
                }
                val selected = subtitleTracks.indexOfFirst { it.id == currentSubtitleId }
                ChoiceMenu(
                    options = options,
                    selectedIndex = if (selected >= 0) selected + 1 else 0,
                    onSelect = { index ->
                        when (index) {
                            options.lastIndex -> onOpenSubtitleSettings()
                            else -> onSubtitleSelect(
                                if (index == 0) TrackOff else subtitleTracks[index - 1].id
                            )
                        }
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
 * message (with the install hint), a way back out, and the instant
 * fallback — hand the url to the system player. The card stays on
 * screen after the hand-off; Back remains the way out.
 */
@Composable
internal fun VlcSetupCard(
    message: String,
    url: String,
    onPlayExternally: () -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPlayExternally) {
                    Text(Strings.playExternally)
                }
                Button(onClick = onNavigateBack) {
                    Text(Strings.back)
                }
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

// ---- Subtitle customize panel (desktop port of Android's SubtitleSettingsPanel) ----

/** The three edge treatments in display order. */
private val SubtitleEdgeChoices = SubtitleStyle.Edge.entries

/** The line shown in the customize panel's live preview (a Persian sample). */
internal const val SubtitlePreviewSample = "این یک نمونهٔ زیرنویس است."

/**
 * Live preview of the draft [style] over a dark, video-like band. It uses the
 * same [DesktopSubtitleView] a Compose subtitle layer would, so size, weight,
 * color, background, edge and bottom offset can be judged before saving.
 * libVLC still renders the real playback subtitles; this is a sample line.
 */
@Composable
internal fun SubtitlePreview(
    style: SubtitleStyle,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101014), Color(0xFF1B1B23))
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        DesktopSubtitleView(
            cue = SubtitleCue(startMs = 0L, endMs = 1L, text = SubtitlePreviewSample),
            style = style,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Modal editor for the player's subtitle look. Edits a [style] draft via
 * [onChange]; [onSave] persists it (and rebuilds playback) and [onCancel]
 * discards. Keyboard: Up/Down move between rows, Left/Right adjust a slider
 * or move between chips, Enter/Center pick the highlighted chip or a
 * Cancel/Save button. Mouse clicks pick directly. The panel grabs focus so
 * the chrome behind it stays inert while it is open.
 */
@Composable
internal fun SubtitleSettingsPanel(
    style: SubtitleStyle,
    onChange: (SubtitleStyle) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Rows: 0 size, 1 weight, 2 color, 3 background, 4 edge, 5 strength,
    // 6 offset; 7 = Cancel/Save buttons.
    val buttonRow = 7
    var row by remember { mutableIntStateOf(0) }
    var col by remember { mutableIntStateOf(0) }
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        while (!panelFocus.requestFocus()) delay(50)
    }

    val maxCol: (Int) -> Int = { r ->
        when (r) {
            1 -> 1
            2 -> SubtitleStyle.ColorOptions.lastIndex
            4 -> SubtitleEdgeChoices.lastIndex
            buttonRow -> 1 // Cancel (0) / Save (1)
            else -> 0
        }
    }
    val stepFor: (Int) -> Int = { r -> if (r == 0 || r == 6) 1 else 5 }
    // Rows 0, 3, 5, 6 are sliders (Left/Right scrubs them); the rest are
    // chip rows (Left/Right moves between chips). Row 4 (edge) is chips, so
    // it must NOT be treated as a slider.
    val isSliderRow: (Int) -> Boolean = { it == 0 || it == 3 || it == 5 || it == 6 }
    val adjustSlider: (Int, Int) -> Unit = { r, delta ->
        when (r) {
            0 -> onChange(
                style.copy(
                    textSizeSp = (style.textSizeSp + delta)
                        .coerceIn(SubtitleStyle.MinTextSizeSp, SubtitleStyle.MaxTextSizeSp)
                )
            )
            3 -> onChange(style.copy(backgroundOpacityPercent = (style.backgroundOpacityPercent + delta).coerceIn(0, 100)))
            5 -> onChange(style.copy(edgeStrengthPercent = (style.edgeStrengthPercent + delta).coerceIn(0, 100)))
            6 -> onChange(style.copy(bottomOffsetPercent = (style.bottomOffsetPercent + delta).coerceIn(0, 30)))
        }
    }
    val activate: (Int, Int) -> Unit = { r, c ->
        when (r) {
            1 -> onChange(style.copy(bold = c == 1))
            2 -> onChange(style.copy(colorRgb = SubtitleStyle.ColorOptions[c.coerceIn(0, SubtitleStyle.ColorOptions.lastIndex)]))
            3 -> onChange(style.copy(backgroundOpacityPercent = (style.backgroundOpacityPercent + 25).coerceIn(0, 100)))
            4 -> onChange(style.copy(edge = SubtitleEdgeChoices[c.coerceIn(0, SubtitleEdgeChoices.lastIndex)]))
            5 -> onChange(style.copy(edgeStrengthPercent = (style.edgeStrengthPercent + 25).coerceIn(0, 100)))
            6 -> onChange(style.copy(bottomOffsetPercent = (style.bottomOffsetPercent + 5).coerceIn(0, 30)))
            else -> {}
        }
    }


    Box(
        modifier = modifier
            .width(620.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(20.dp)
            .focusRequester(panelFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        row = (row - 1).coerceAtLeast(0)
                        col = col.coerceIn(0, maxCol(row))
                        true
                    }
                    Key.DirectionDown -> {
                        row = (row + 1).coerceAtMost(buttonRow)
                        col = col.coerceIn(0, maxCol(row))
                        true
                    }
                    Key.DirectionLeft -> {
                        if (isSliderRow(row)) adjustSlider(row, -stepFor(row))
                        else col = (col - 1).coerceAtLeast(0)
                        true
                    }
                    Key.DirectionRight -> {
                        if (isSliderRow(row)) adjustSlider(row, stepFor(row))
                        else col = (col + 1).coerceAtMost(maxCol(row))
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                        if (row == buttonRow) {
                            if (col == 0) onCancel() else onSave()
                        } else {
                            activate(row, col)
                        }
                        true
                    }
                    // Backspace is handled by the screen (cancels the panel);
                    // let it through.
                    Key.Backspace -> false
                    else -> true
                }
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubtitlePreview(style = style)
            SubtitleSliderPanelRow(
                label = Strings.playerSubtitleSize,
                fillFraction = (style.textSizeSp - SubtitleStyle.MinTextSizeSp) /
                    (SubtitleStyle.MaxTextSizeSp - SubtitleStyle.MinTextSizeSp),
                valueLabel = "${style.textSizeSp.roundToInt()}sp",
                highlighted = row == 0
            )
            SubtitleChipsPanelRow(
                label = Strings.playerSubtitleWeight,
                options = listOf(Strings.playerSubtitleOptNormal, Strings.playerSubtitleOptBold),
                selectedIndex = if (style.bold) 1 else 0,
                highlightedIndex = if (row == 1) col else -1,
                onPick = { onChange(style.copy(bold = it == 1)) }
            )
            SubtitleChipsPanelRow(
                label = Strings.playerSubtitleColor,
                options = listOf(
                    Strings.playerSubtitleOptWhite,
                    Strings.playerSubtitleOptYellow,
                    Strings.playerSubtitleOptCyan,
                    Strings.playerSubtitleOptGreen,
                    Strings.playerSubtitleOptOrange,
                    Strings.playerSubtitleOptPink,
                    Strings.playerSubtitleOptLightBlue
                ),
                selectedIndex = SubtitleStyle.ColorOptions.indexOf(style.colorRgb).coerceAtLeast(0),
                highlightedIndex = if (row == 2) col else -1,
                onPick = { onChange(style.copy(colorRgb = SubtitleStyle.ColorOptions[it])) }
            )
            SubtitleSliderPanelRow(
                label = Strings.playerSubtitleBackground,
                fillFraction = style.backgroundOpacityPercent / 100f,
                valueLabel = "${style.backgroundOpacityPercent}%",
                highlighted = row == 3
            )
            SubtitleChipsPanelRow(
                label = Strings.playerSubtitleEdge,
                options = listOf(
                    Strings.playerSubtitleOptNone,
                    Strings.playerSubtitleOptShadow,
                    Strings.playerSubtitleOptOutline
                ),
                selectedIndex = SubtitleEdgeChoices.indexOf(style.edge).coerceAtLeast(0),
                highlightedIndex = if (row == 4) col else -1,
                onPick = { onChange(style.copy(edge = SubtitleEdgeChoices[it])) }
            )
            SubtitleSliderPanelRow(
                // Only meaningful for Shadow/Outline; show it regardless.
                label = Strings.playerSubtitleStrength,
                fillFraction = style.edgeStrengthPercent / 100f,
                valueLabel = "${style.edgeStrengthPercent}%",
                highlighted = row == 5
            )
            SubtitleSliderPanelRow(
                label = Strings.playerSubtitleOffset,
                // Offset is 0..30% of height; the bar shows that range.
                fillFraction = style.bottomOffsetPercent / 30f,
                valueLabel = "${style.bottomOffsetPercent}%",
                highlighted = row == 6
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubtitlePanelChip(
                    label = Strings.playerSubtitleCancel,
                    highlighted = row == buttonRow && col == 0,
                    onClick = onCancel
                )
                SubtitlePanelChip(
                    label = Strings.playerSubtitleSave,
                    highlighted = row == buttonRow && col == 1,
                    onClick = onSave
                )
            }
        }
    }
}

/** One label + horizontally-scrollable chip row of the subtitle panel. */
@Composable
private fun SubtitleChipsPanelRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    highlightedIndex: Int,
    onPick: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    // Keyboard navigation moves the highlight; scroll it into view so the
    // wider rows (the seven colors) can reach the trailing off-screen chips.
    // A LazyRow also scrolls with the mouse wheel / trackpad on desktop.
    LaunchedEffect(highlightedIndex) {
        if (highlightedIndex !in options.indices) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == highlightedIndex }
        val fullyVisible = item != null &&
            item.offset >= info.viewportStartOffset &&
            item.offset + item.size <= info.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(highlightedIndex)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.width(110.dp)
        )
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(options) { index, option ->
                val highlighted = index == highlightedIndex
                val current = index == selectedIndex
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                highlighted -> MaterialTheme.colorScheme.primary
                                current -> Color.White.copy(alpha = 0.25f)
                                else -> Color.White.copy(alpha = 0.15f)
                            }
                        )
                        .pointerInput(selectedIndex) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()

                                    if (event.changes.any { it.pressed }) {
                                        onPick(index)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
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
}

/** One slider row of the subtitle panel (scrubbed with Left/Right). */
@Composable
private fun SubtitleSliderPanelRow(
    label: String,
    fillFraction: Float,
    valueLabel: String,
    highlighted: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.width(110.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (highlighted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f))
            )
        }
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}

/** A Cancel/Save chip of the subtitle panel. */
@Composable
private fun SubtitlePanelChip(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
            .pointerInput(label) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            onClick()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
    }
}
