package com.mab.f2mbox.desktop.player

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import javax.swing.JComponent

/** One selectable audio/subtitle track of the playing media. */
data class TrackInfo(val id: Int, val label: String)

/** Events the player screen reacts to. */
sealed interface EngineEvent {
    data object Playing : EngineEvent
    data object Paused : EngineEvent
    data class Position(val ms: Long, val durationMs: Long) : EngineEvent
    data class Rate(val rate: Float) : EngineEvent
    data class TracksReady(
        val audio: List<TrackInfo>,
        val subtitles: List<TrackInfo>,
        val currentAudio: Int,
        val currentSubtitle: Int,
    ) : EngineEvent
    data class Error(val kind: ErrorKind) : EngineEvent

    enum class ErrorKind { Network, Decode, Source }
}

/**
 * Fans [EngineEvent]s out to the engine's single listener. Once an
 * [EngineEvent.Error] went through, every further event is suppressed:
 * the native player is dead after an error and late position/rate
 * updates would fight with the screen's error state. [reset] re-opens
 * the pipe (used when the same bus outlives one media).
 */
internal class EngineEventBus(private val listener: (EngineEvent) -> Unit) {

    private var failed = false

    fun emit(event: EngineEvent) {
        if (failed) return
        if (event is EngineEvent.Error) failed = true
        listener(event)
    }

    fun reset() {
        failed = false
    }
}

/**
 * The desktop playback engine: vlcj (libVLC) behind the [EngineEvent]
 * vocabulary the player screen consumes.
 *
 * Runs the media directly — no RTSP indirection — with hardware decoding
 * left to libvlc (`avcodec-hw=any`), mirroring the Android player's
 * `ExoPlayer`-defaults stance.
 *
 * Error classification is heuristic: libvlc reports a bare error event
 * with no kind. An error before the first `playing` event means the
 * source could not be opened ([ErrorKind.Source]; dead link, bad
 * domain, offline); one after playback started means the stream broke
 * mid-decode ([ErrorKind.Decode]). [ErrorKind.Network] is reserved for
 * a finer-grained split (e.g. socket-state sniffing) later.
 *
 * The caller owns the lifecycle: create per playback, [release] when the
 * screen goes away. The AWT video surface is [component], embedded by
 * the screen with `SwingPanel`.
 */
class VlcPlayerEngine(
    url: String,
    listener: (EngineEvent) -> Unit,
    /** Extra libvlc args; used by the diagnostic harnesses to get native logs. */
    extraArgs: List<String> = emptyList(),
    /** Subtitle look applied to this playback; libvlc reads it when the media opens. */
    subtitleStyle: SubtitleStyle = SubtitleStyle.Default,
) {

    private val bus = EngineEventBus(listener)

    private val factory = MediaPlayerFactory(
        *(listOf("--avcodec-hw=any") + extraArgs).toTypedArray(),
    )

    /**
     * Callback rendering (memory-video): libvlc delivers decoded frames to
     * a [CallbackMediaPlayerComponent] that vlcj paints with Java2D — no
     * native-surface attachment. The alternative (the embedded component's
     * NSView attachment via `OsxComponentId`) is broken on modern JDKs:
     * the reflection chain asks the AWT peer for `getViewPtr`, which
     * `sun.lwawt` peers no longer expose, so libvlc renders nowhere and
     * the window stays black while audio plays.
     */
    val component: CallbackMediaPlayerComponent = CallbackMediaPlayerComponent()

    val componentSurface: JComponent = component.videoSurfaceComponent()

    private val player: EmbeddedMediaPlayer = component.mediaPlayer()

    /** True once the first `playing` event arrived — see the error-kind KDoc. */
    private var startedPlaying = false

    /** The media currently loaded, so a style change can re-open it. */
    private var currentMrl: String? = null

    /** Guards the deferred start against a release before the window showed. */
    @Volatile
    private var released = false

    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                startedPlaying = true
                bus.emit(EngineEvent.Playing)
                emitTracks(mediaPlayer)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                bus.emit(EngineEvent.Paused)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                bus.emit(EngineEvent.Paused)
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, time: Long) {
                bus.emit(EngineEvent.Position(time, mediaPlayer.status().length()))
            }

            override fun elementaryStreamAdded(
                mediaPlayer: MediaPlayer,
                type: TrackType,
                id: Int,
            ) {
                emitTracks(mediaPlayer)
            }

            override fun elementaryStreamSelected(
                mediaPlayer: MediaPlayer,
                type: TrackType,
                id: Int,
            ) {
                emitTracks(mediaPlayer)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                bus.emit(
                    EngineEvent.Error(
                        if (startedPlaying) EngineEvent.ErrorKind.Decode
                        else EngineEvent.ErrorKind.Source
                    )
                )
            }
        })
        playWhenDisplayable(url, subtitleStyle.toMediaOptions(videoHeight()))
    }

    /**
     * Starts playback, but only once the video surface is displayable —
     * libvlc refuses to attach to a hidden AWT component ("The video
     * surface component must be displayable"). The screen composes the
     * engine before the window's AWT hierarchy is showing, so the start
     * waits (max 10s) for the surface to become displayable, then plays
     * on the AWT thread; recreations (retry) find it displayable and
     * play immediately.
     */
    private fun playWhenDisplayable(url: String, options: List<String> = emptyList()) {
        currentMrl = url
        Thread({
            var waited = 0
            while (!released && !componentSurface.isDisplayable && waited < 10_000) {
                Thread.sleep(50)
                waited += 50
            }
            if (!released && componentSurface.isDisplayable) {
                javax.swing.SwingUtilities.invokeLater {
                    if (!released) player.media().play(url, *options.toTypedArray())
                }
            } else if (!released) {
                bus.emit(EngineEvent.Error(EngineEvent.ErrorKind.Source))
            }
        }, "vlc-start").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Re-opens the current media with [style]'s subtitle options, resuming
     * at the position (and rate) it had. libvlc reads subtitle styling only
     * when the media opens, so this is the only way to apply a change
     * mid-playback — the brief hiccup approach A accepted. Best-effort: if
     * nothing is playing, it does nothing.
     */
    fun applySubtitleStyle(style: SubtitleStyle) {
        val mrl = currentMrl ?: return
        val time = player.status().time()
        val rate = player.status().rate()
        val wasPlaying = player.status().isPlaying()
        val options = style.toMediaOptions(videoHeight())
        javax.swing.SwingUtilities.invokeLater {
            if (released) return@invokeLater
            if (!player.media().prepare(mrl, *options.toTypedArray())) return@invokeLater
            player.controls().play()
            player.controls().setTime(time.coerceAtLeast(0))
            player.controls().setRate(rate)
            if (!wasPlaying) player.controls().pause()
        }
    }

    private fun videoHeight(): Int =
        try {
            player.video().videoDimension()?.height ?: 1080
        } catch (_: Exception) {
            1080
        }

    fun play() = player.controls().play()

    fun pause() = player.controls().pause()

    fun seekTo(ms: Long) = player.controls().setTime(ms.coerceAtLeast(0))

    fun seekBy(deltaMs: Long) {
        val current = player.status().time()
        val duration = player.status().length()
        val target = (current + deltaMs).coerceIn(0L, if (duration > 0) duration else current + deltaMs)
        player.controls().setTime(target)
    }

    fun setRate(rate: Float) {
        player.controls().setRate(rate)
        bus.emit(EngineEvent.Rate(rate))
    }

    fun selectAudio(id: Int) = player.audio().setTrack(id)

    fun selectSubtitle(id: Int) = player.subpictures().setTrack(id)

    /**
     * Re-reads the track lists and emits [EngineEvent.TracksReady] — the
     * screen also calls this when the user opens a track menu, because
     * libvlc populates tracks lazily.
     */
    fun refreshTracks() = emitTracks(player)

    fun release() {
        released = true
        player.release()
        factory.release()
        bus.reset()
    }

    private fun emitTracks(mediaPlayer: MediaPlayer) {
        val audio = mediaPlayer.audio().trackDescriptions().orEmpty().toTrackInfos()
        val subtitles = mediaPlayer.subpictures().trackDescriptions().orEmpty().toTrackInfos()
        bus.emit(
            EngineEvent.TracksReady(
                audio = audio,
                subtitles = subtitles,
                currentAudio = mediaPlayer.audio().track(),
                currentSubtitle = mediaPlayer.subpictures().track(),
            )
        )
    }

    /** libvlc's disable marker (-1) becomes id -1/"Off" handled by the menu. */
    private fun List<TrackDescription>.toTrackInfos(): List<TrackInfo> =
        this.map { TrackInfo(id = it.id(), label = it.description() ?: "Track ${it.id()}") }
}
