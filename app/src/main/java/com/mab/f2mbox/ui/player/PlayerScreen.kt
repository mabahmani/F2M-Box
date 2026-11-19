@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.player

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mab.f2mbox.R
import kotlinx.coroutines.delay
import java.util.Locale

/** One D-pad seek step, in milliseconds. */
private const val SeekStepMs = 10_000L

/** How long the controls stay on screen after the last interaction. */
private const val ControlsHideDelayMs = 4_000L

/** How often the progress read-out is refreshed from the player. */
private const val PositionPollMs = 500L

/** Minimum gap between two seeks, so key auto-repeat doesn't thrash the player. */
private const val SeekThrottleMs = 120L

/** How long the seek OSD stays up after the last seek key. */
private const val SeekOsdDelayMs = 900L

/**
 * Full-screen video player built for the D-pad, with two input modes like
 * mainstream TV players:
 *
 * - **Controls hidden** (normal viewing): Left/Right seek ±10s with a small
 *   progress OSD (holding the key repeats); Center/Up/Down open the controls
 *   (focus lands on play/pause); Back exits.
 * - **Controls visible**: Left/Right move focus across the transport row
 *   (skip back, play/pause, skip forward), Up/Down move between the rows
 *   (transport ↔ seek bar ↔ back button), Center activates the focused
 *   control. Holding Center on a skip button keeps seeking in steps; on the
 *   seek bar Left/Right scrub. The speed button opens a modal speed menu,
 *   the CC button opens a modal subtitle menu (Off + every text track
 *   embedded in the video) and the audio button opens a modal audio menu
 *   (Auto + every audio track) — all use Left/Right to choose, Center to
 *   apply, Back to close. Back hides the controls, and a second press exits.
 *
 * The overlay auto-hides after [ControlsHideDelayMs] while playing, the
 * screen is kept on during playback, and playback pauses when the activity
 * stops. The player chrome is laid out LTR even though the app is RTL:
 * timelines read left-to-right on every platform.
 *
 * [onNavigateBack] leaves the player.
 */
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Decoder fallback: if the device's preferred (hardware) HEVC or other
    // codec decoder fails to init, retry with any other registered decoder.
    val renderersFactory = remember {
        DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    }

    val player = remember(url) {
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            // Prefer Persian audio whenever the file carries it; otherwise
            // ExoPlayer's default rules decide (container default flag,
            // device locale, then quality).
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages("fa")
                .build()
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Playback snapshot, polled: the few values the overlay needs, read on a
    // fixed cadence instead of wiring a Listener per field.
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }

    // Why playback failed: null while healthy, otherwise the failure class
    // plus the display name of the codec that was being decoded (if known).
    var errorKind by remember { mutableStateOf<PlaybackErrorKind?>(null) }
    var errorCodec by remember { mutableStateOf<String?>(null) }
    // Subtitle (text) and audio tracks embedded in the video, rebuilt
    // whenever the player reports a new set of tracks.
    var subtitleTracks by remember { mutableStateOf(listOf<MediaTrack>()) }
    var audioTracks by remember { mutableStateOf(listOf<MediaTrack>()) }
    // Active subtitle cues, rewritten to force bottom-center placement.
    var subtitleCues by remember { mutableStateOf(emptyList<Cue>()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Grab the codec while it's still known: a decode failure
                // usually means the device lacks a working decoder for it.
                errorCodec = player.videoFormat?.sampleMimeType?.let(::codecDisplayName)
                errorKind = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
                    PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
                        PlaybackErrorKind.Decode
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                        PlaybackErrorKind.Network
                    else -> PlaybackErrorKind.Generic
                }
            }

            override fun onCues(cues: List<Cue>) {
                subtitleCues = forceBottomCues(cues)
            }

            override fun onTracksChanged(tracks: Tracks) {
                subtitleTracks = tracksOfType(tracks, C.TRACK_TYPE_TEXT)
                audioTracks = tracksOfType(tracks, C.TRACK_TYPE_AUDIO)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            isEnded = player.playbackState == Player.STATE_ENDED
            delay(PositionPollMs)
        }
    }

    // Pause when the activity goes to the background so audio doesn't leak.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the screen on only while actually playing.
    val view = LocalView.current
    LaunchedEffect(isPlaying) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        if (isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every interaction so the auto-hide timer restarts.
    var interaction by remember { mutableIntStateOf(0) }

    // Playback speed and its selector menu. Hoisted here so the auto-hide
    // timer can pause while the menu is open.
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var speedMenuVisible by remember { mutableStateOf(false) }
    var subtitleMenuVisible by remember { mutableStateOf(false) }
    var audioMenuVisible by remember { mutableStateOf(false) }
    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(
        controlsVisible, isPlaying, interaction,
        speedMenuVisible, subtitleMenuVisible, audioMenuVisible
    ) {
        if (controlsVisible && isPlaying &&
            !speedMenuVisible && !subtitleMenuVisible && !audioMenuVisible
        ) {
            delay(ControlsHideDelayMs)
            controlsVisible = false
        }
    }

    // Seek OSD: shown while seeking from the hidden-controls mode, so the
    // user sees the position move without the controls stealing Left/Right
    // for focus navigation.
    var seekOsdVisible by remember { mutableStateOf(false) }
    var seekOsdNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekOsdVisible, seekOsdNonce) {
        if (seekOsdVisible) {
            delay(SeekOsdDelayMs)
            seekOsdVisible = false
        }
    }

    // All seeks funnel through one throttled entry point: key auto-repeat
    // fires far faster than a player should re-seek.
    val lastSeekAt = remember { mutableLongStateOf(0L) }
    val seek: (Long) -> Unit = { deltaMs ->
        val now = SystemClock.uptimeMillis()
        if (now - lastSeekAt.longValue >= SeekThrottleMs) {
            lastSeekAt.longValue = now
            seekBy(player, deltaMs)
        }
    }

    // Applies a subtitle selection: null turns subtitles off, otherwise the
    // given embedded text track is forced on.
    val selectSubtitle: (MediaTrack?) -> Unit = { track ->
        val builder = player.trackSelectionParameters.buildUpon()
        if (track == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(
                TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)
            )
        }
        player.trackSelectionParameters = builder.build()
    }

    // Applies an audio selection: null resets to Auto (the player picks),
    // otherwise the given embedded audio track is forced on.
    val selectAudio: (MediaTrack?) -> Unit = { track ->
        val builder = player.trackSelectionParameters.buildUpon()
        builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        if (track == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        } else {
            builder.setOverrideForType(
                TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)
            )
        }
        player.trackSelectionParameters = builder.build()
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

    // Back: close any open menu first, then hide the overlay, then leave.
    BackHandler {
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
                        // Hidden mode: Center/Up/Down open the controls;
                        // visible mode: everything falls through to the
                        // focused control.
                        Key.DirectionCenter, Key.Enter, Key.Spacebar,
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
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
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        // Subtitles are rendered by our own SubtitleView
                        // (bottom-pinned, app font) — hide the built-in one.
                        subtitleView?.visibility = android.view.View.GONE
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )

            // Bottom-pinned subtitle layer: rewritten cues (embedded
            // positioning stripped) in the app's Vazirmatn face.
            AndroidView(
                factory = { ctx ->
                    SubtitleView(ctx).apply {
                        setStyle(
                            CaptionStyleCompat(
                                Color.White.toArgb(),
                                Color.Transparent.toArgb(),
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_NONE,
                                android.graphics.Color.TRANSPARENT,
                                ResourcesCompat.getFont(ctx, R.font.vazirmatn_rd_regular)
                            )
                        )
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setBottomPaddingFraction(0.02f)
                    }
                },
                update = { it.setCues(subtitleCues) },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering && errorKind == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(56.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            errorKind?.let { kind ->
                PlayerError(
                    kind = kind,
                    codec = errorCodec,
                    onRetry = {
                        errorKind = null
                        errorCodec = null
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        player.playWhenReady = true
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            PlayerControls(
                player = player,
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
                    speedMenuVisible = false
                    interaction++
                },
                subtitleTracks = subtitleTracks,
                subtitleMenuVisible = subtitleMenuVisible,
                onToggleSubtitleMenu = {
                    subtitleMenuVisible = !subtitleMenuVisible
                    if (subtitleMenuVisible) {
                        speedMenuVisible = false
                        audioMenuVisible = false
                    }
                    interaction++
                },
                onSubtitleSelect = {
                    selectSubtitle(it)
                    subtitleMenuVisible = false
                    interaction++
                },
                audioTracks = audioTracks,
                audioMenuVisible = audioMenuVisible,
                onToggleAudioMenu = {
                    audioMenuVisible = !audioMenuVisible
                    if (audioMenuVisible) {
                        speedMenuVisible = false
                        subtitleMenuVisible = false
                    }
                    interaction++
                },
                onAudioSelect = {
                    selectAudio(it)
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
    player: Player,
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
    subtitleTracks: List<MediaTrack>,
    subtitleMenuVisible: Boolean,
    onToggleSubtitleMenu: () -> Unit,
    onSubtitleSelect: (MediaTrack?) -> Unit,
    audioTracks: List<MediaTrack>,
    audioMenuVisible: Boolean,
    onToggleAudioMenu: () -> Unit,
    onAudioSelect: (MediaTrack?) -> Unit,
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
            if (isEnded) {
                player.seekTo(0)
                player.play()
            } else if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
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
            PlayerButton(onClick = onBack, description = stringResource(R.string.back)) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text(
                text = title.ifBlank { stringResource(R.string.trailer) },
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
                    add(stringResource(R.string.player_subtitles_off))
                    subtitleTracks.forEach { add(it.label) }
                }
                val selected = subtitleTracks.indexOfFirst {
                    it.group.isTrackSelected(it.trackIndex)
                }
                ChoiceMenu(
                    options = options,
                    selectedIndex = if (selected >= 0) selected + 1 else 0,
                    onSelect = { index ->
                        onSubtitleSelect(if (index == 0) null else subtitleTracks[index - 1])
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (audioMenuVisible) {
                val options = buildList {
                    add(stringResource(R.string.player_audio_auto))
                    audioTracks.forEach { add(it.label) }
                }
                val selected = audioTracks.indexOfFirst {
                    it.group.isTrackSelected(it.trackIndex)
                }
                ChoiceMenu(
                    options = options,
                    selectedIndex = if (selected >= 0) selected + 1 else 0,
                    onSelect = { index ->
                        onAudioSelect(if (index == 0) null else audioTracks[index - 1])
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
                    description = stringResource(R.string.player_skip_back),
                    repeatable = true
                ) {
                    Icon(Icons.Default.Replay10, null, tint = Color.White)
                }
                PlayerButton(
                    onClick = togglePlayPause,
                    description = stringResource(
                        when {
                            isEnded -> R.string.player_replay
                            isPlaying -> R.string.player_pause
                            else -> R.string.player_play
                        }
                    ),
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
                    description = stringResource(R.string.player_skip_forward),
                    repeatable = true
                ) {
                    Icon(Icons.Default.Forward10, null, tint = Color.White)
                }
                PlayerButton(
                    onClick = onToggleSpeedMenu,
                    description = stringResource(R.string.player_speed),
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
                    val subtitlesOn = subtitleTracks.any {
                        it.group.isTrackSelected(it.trackIndex)
                    }
                    PlayerButton(
                        onClick = onToggleSubtitleMenu,
                        description = stringResource(R.string.player_subtitles),
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
                        description = stringResource(R.string.player_audio),
                        modifier = Modifier.focusRequester(audioButtonFocus)
                    ) {
                        Icon(Icons.Default.Audiotrack, null, tint = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTime(positionMs),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
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
 * tap-to-seek for touch devices.
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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        // Center on the focused bar toggles play/pause
                        // (auto-repeat ignored so holding doesn't flicker).
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                onTogglePlayPause()
                            }
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
            text = formatTime(durationMs),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
    }
}

/**
 * A circular transport button with the app's standard focus treatment:
 * scale up on focus and fill with the primary color. Activates on the
 * Center/Enter KeyDown so holding the key auto-repeats for [repeatable]
 * buttons (skip buttons); one-shot buttons ignore auto-repeat.
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
                if (!repeatable && event.nativeKeyEvent.repeatCount > 0) {
                    // One-shot buttons ignore key auto-repeat.
                    return@onKeyEvent true
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
 * highlight. The row itself is the single focus target — Left/Right move the
 * highlight, Center picks the highlighted option and closes, and everything
 * else is consumed so the menu stays modal (Back is handled by the screen).
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
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            onSelect(highlighted)
                        }
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

/** One selectable embedded track (audio or subtitle text) in the video. */
private class MediaTrack(val group: Tracks.Group, val trackIndex: Int, val label: String)

/** Collects every supported embedded track of one type from a [Tracks] snapshot. */
private fun tracksOfType(tracks: Tracks, type: Int): List<MediaTrack> = buildList {
    tracks.groups.forEach { group ->
        if (group.type == type) {
            repeat(group.length) { i ->
                if (group.isTrackSupported(i)) {
                    add(MediaTrack(group, i, trackLabel(group.getTrackFormat(i), i)))
                }
            }
        }
    }
}

/** Human-readable label for an embedded track: its name, language, or index. */
private fun trackLabel(format: Format, index: Int): String {
    format.label?.takeIf { it.isNotBlank() }?.let { return it }
    format.language?.takeIf { it.isNotBlank() }?.let { lang ->
        Locale.forLanguageTag(lang).displayLanguage.takeIf {
            it.isNotBlank() && !it.equals(lang, ignoreCase = true)
        }?.let { return it }
        return lang.uppercase(Locale.US)
    }
    return "Track ${index + 1}"
}

/** Renders a playback speed as a compact label: 1x, 1.25x, 1.5x… */
private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/** The classes of playback failure the error card can describe. */
private enum class PlaybackErrorKind {
    /** The device couldn't decode the media (codec unsupported/failing). */
    Decode,

    /** The stream couldn't be fetched (network/HTTP problem). */
    Network,

    /** Anything else. */
    Generic
}

/**
 * Strips embedded cue positioning (line/anchor/position/size and vertical
 * writing) so every cue renders at the default spot: bottom-center of the
 * [SubtitleView], offset by its bottom padding fraction.
 */
private fun forceBottomCues(cues: List<Cue>): List<Cue> = cues.map { cue ->
    cue.buildUpon()
        .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
        .setLineAnchor(Cue.TYPE_UNSET)
        .setPosition(Cue.DIMEN_UNSET)
        .setPositionAnchor(Cue.TYPE_UNSET)
        .setSize(Cue.DIMEN_UNSET)
        .setBitmapHeight(Cue.DIMEN_UNSET)
        .setVerticalType(Cue.TYPE_UNSET)
        .setTextSize(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
        .build()
}

/** Human-readable name for a video sample mime type, or null if unknown. */
private fun codecDisplayName(sampleMimeType: String?): String? = when (sampleMimeType) {    MimeTypes.VIDEO_H265 -> "HEVC (x265)"
    MimeTypes.VIDEO_H264 -> "H.264 (x264)"
    MimeTypes.VIDEO_H263 -> "H.263"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP8 -> "VP8"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    MimeTypes.VIDEO_MP4V -> "MPEG-4"
    null -> null
    else -> sampleMimeType
}

/** Centered playback-failure card with a retry action. */
@Composable
private fun PlayerError(
    kind: PlaybackErrorKind,
    codec: String?,
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
                PlaybackErrorKind.Decode ->
                    codec?.let { stringResource(R.string.player_error_decode_codec, it) }
                        ?: stringResource(R.string.player_error_decode)
                PlaybackErrorKind.Network -> stringResource(R.string.player_error_network)
                PlaybackErrorKind.Generic -> stringResource(R.string.player_error)
            },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

/** Seeks by [deltaMs], clamped to the stream's bounds. */
private fun seekBy(player: Player, deltaMs: Long) {
    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
    val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
    player.seekTo(target)
}

/** Formats a position as m:ss or h:mm:ss (latin digits for timecodes). */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
