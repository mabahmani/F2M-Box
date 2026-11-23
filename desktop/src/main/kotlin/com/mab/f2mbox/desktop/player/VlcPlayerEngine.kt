package com.mab.f2mbox.desktop.player

import java.io.File
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
 * Runs the media directly — no RTSP indirection. Decoding is forced to
 * software so libVLC can composite subtitles into the memory (callback)
 * frames — see [decodeArgs]; the hardware decoders hand libVLC
 * decoder-native (CVPX/NV12) frames that its subtitle blender cannot draw
 * onto, so subtitles would silently disappear.
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
    /** Subtitle look applied to this playback; libvlc reads it when the engine's factory is built. */
    subtitleStyle: SubtitleStyle = SubtitleStyle.Default,
    /** Where playback resumes (used when a style change rebuilds the engine mid-watch). */
    startPositionMs: Long = 0L,
    /**
     * The native libVLC location chosen by [VlcDiscovery]. When present its
     * directory becomes the top `jna.library.path` entry, so a packaged app
     * loads the bundled libVLC (and vlcj derives VLC_PLUGIN_PATH from it);
     * `null` keeps the legacy addSearchPath behaviour.
     */
    nativeLocation: VlcLocation? = null,
) {

    private val bus = EngineEventBus(listener)

    /**
     * libVLC must decode in software for subtitles to reach the callback
     * surface.
     *
     * On the hardware path (VideoToolbox on macOS, D3D11VA/DXVA2 on Windows)
     * libVLC hands the subtitle bitmap (a YUVA subpicture) to its alpha-blend
     * filter against the decoder's native CVPX/NV12 picture, which has no
     * blend routine — it logs
     * `no matching alpha blending routine (chroma: YUVA -> CVPN)` and the
     * subtitle never reaches the frame (verified with a paused A/B capture:
     * with hardware decode the subtitle region is pixel-identical with the
     * track on or off). The software avcodec decoder produces an I420
     * picture, which *does* have a YUVA->I420 blender, so subtitles composite
     * into the callback frame. Forcing it globally (`--codec=avcodec` also
     * overrides macOS's separate `videotoolbox` module) costs CPU decoding,
     * but it is the only way to show subtitles without a native video surface
     * that would sit above — and hide — the Compose overlay controls.
     */
    private val decodeArgs = listOf("--codec=avcodec", "--avcodec-hw=none") + extraArgs

    /**
     * The full libvlc global argument list. The style's freetype options
     * ride along here because libvlc only honours them as global
     * (`--opt=value`) args, not as per-media `:` options.
     */
    private val factoryArgs = decodeArgs + subtitleStyle.toVlcArgs()

    private val factory = createFactory(nativeLocation, factoryArgs)

    /**
     * Puts [location]'s directory at the front of `jna.library.path` so
     * vlcj's native discovery loads this libVLC (bundled or system) and
     * force-loads libvlccore first — the latter is what lets the rpath-less
     * macOS dylibs resolve. Plugins come along via VLC_PLUGIN_PATH, which
     * vlcj sets from the discovered lib directory.
     */
    private fun createFactory(location: VlcLocation?, args: List<String>): MediaPlayerFactory {
        if (location != null) {
            val dir = location.libDir.toString()
            val existing = System.getProperty("jna.library.path")
            System.setProperty(
                "jna.library.path",
                if (existing.isNullOrBlank()) dir else "$dir${File.pathSeparator}$existing",
            )
            com.sun.jna.NativeLibrary.addSearchPath("libvlc", dir)
            com.sun.jna.NativeLibrary.addSearchPath("libvlccore", dir)
        }
        return MediaPlayerFactory(*args.toTypedArray())
    }

    /**
     * Callback rendering (memory-video): libvlc delivers decoded frames to
     * a [CallbackMediaPlayerComponent] that vlcj paints with Java2D — a
     * *lightweight* surface, so the Compose chrome can draw on top of it.
     * The embedded (native) component would render subtitles for free, but a
     * heavyweight AWT surface always paints above lightweight/Compose content
     * and would hide the controls, so we keep the callback surface and force
     * software decoding instead (see [factoryArgs]) so libVLC can blend the
     * subtitles into those frames itself.
     */
    val component: CallbackMediaPlayerComponent =
        CallbackMediaPlayerComponent(*factoryArgs.toTypedArray())

    val componentSurface: JComponent = component.videoSurfaceComponent()

    private val player: EmbeddedMediaPlayer = component.mediaPlayer()

    /** True once the first `playing` event arrived — see the error-kind KDoc. */
    private var startedPlaying = false

    /** Applies [startPositionMs] once, on the first `playing` event. */
    private var startApplied = false

    /** Guards the deferred start against a release before the window showed. */
    @Volatile
    private var released = false

    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                startedPlaying = true
                // Resume where a style change interrupted playback (the
                // engine is rebuilt to re-read the global freetype args).
                if (!startApplied) {
                    startApplied = true
                    if (startPositionMs > 0) mediaPlayer.controls().setTime(startPositionMs)
                }
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
        playWhenDisplayable(url)
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
    private fun playWhenDisplayable(url: String) {
        Thread({
            var waited = 0
            while (!released && !componentSurface.isDisplayable && waited < 10_000) {
                Thread.sleep(50)
                waited += 50
            }
            if (!released && componentSurface.isDisplayable) {
                javax.swing.SwingUtilities.invokeLater {
                    if (!released) player.media().play(url)
                }
            } else if (!released) {
                bus.emit(EngineEvent.Error(EngineEvent.ErrorKind.Source))
            }
        }, "vlc-start").apply {
            isDaemon = true
            start()
        }
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
        // Releases both the media player and the component's own factory;
        // [factory] is the separate instance created for native discovery.
        component.release()
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
