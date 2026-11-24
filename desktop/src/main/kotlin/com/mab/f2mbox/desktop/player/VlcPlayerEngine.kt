package com.mab.f2mbox.desktop.player

import com.mab.f2mbox.desktop.platform.VazirFonts
import java.io.File
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.InputEvents
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.lang.reflect.InvocationTargetException
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** One selectable audio/subtitle track of the playing media. */
data class TrackInfo(val id: Int, val label: String)

/** Events the player screen reacts to. */
sealed interface EngineEvent {
    data object Playing : EngineEvent
    data object Paused : EngineEvent
    /** Network/cache fill while opening or after a seek; 0–100. */
    data class Buffering(val percent: Float) : EngineEvent
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
    private val extraArgs: List<String> = emptyList(),
    /** Subtitle look applied to this playback; libvlc reads it when the engine's factory is built. */
    private val subtitleStyle: SubtitleStyle = SubtitleStyle.Default,
    /** Where playback resumes (used when a style change rebuilds the engine mid-watch). */
    private val startPositionMs: Long = 0L,
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
     * For embedded subtitles inside media files (MKV/MP4/TS) and remote streaming URLs,
     * libVLC decodes in software (`--codec=avcodec`, `--avcodec-hw=none`) so libVLC can
     * perform YUVA -> I420 alpha blending of embedded subtitle subpictures.
     */
    private val decodeArgs = (if (startPositionMs > 0) {
        // On a rebuild, open the media already at [startPositionMs] via the media
        // ':start-time' option, instead of seeking after the first playing event.
        // Verified on a remote HTTP stream: seeking immediately after play() was
        // unreliable (playback stayed at 0 while the seek was racing media setup);
        // ':start-time' makes the resume deterministic and avoids the race.
        listOf(
            "--start-time=${startPositionMs / 1000.0}",
            // The seek guarantee above requires the demuxer's start-time support
            // for network sources; none needed here — mkv/ts/mp4 all honor it.
        )
    } else emptyList()) + listOf("--codec=avcodec", "--avcodec-hw=none") + extraArgs

    /**
     * The full libvlc global argument list including user subtitle styling.
     */
    private val factoryArgs = decodeArgs + subtitleStyle.toVlcArgs(
        fontFamily = VazirFonts.registeredFamily()
    )

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
     *
     * [InputEvents.NONE] is essential on macOS: with the default input
     * handling vlcj registers AWT mouse/key listeners on the video surface,
     * which becomes the deepest Swing listener for every click over the
     * video. AWT routes mouse events to that listener instead of letting
     * Compose's interop event forwarding reach the overlay, so the player
     * controls would be keyboard-only. Keeping the surface listener-free
     * lets the overlay's Compose pointer handlers receive real clicks.
     * Playback itself needs no AWT input handling (no DVD menus).
     */
    val component: CallbackMediaPlayerComponent =
        CallbackMediaPlayerComponent(
            factory,
            null, // full-screen strategy: the player screen handles fullscreen itself
            InputEvents.NONE,
            true, // lock buffers, matching vlcj's default callback surface
            null, // default (scaled) image painter
        )

    val componentSurface: JComponent = component.videoSurfaceComponent()

    private val player: EmbeddedMediaPlayer = component.mediaPlayer()

    /** True once the first `playing` event arrived — see the error-kind KDoc. */
    private var startedPlaying = false

    /** Applies [startPositionMs] once, on the first `playing` event. */
    private var startApplied = false

    /** True once the resume position actually took effect (verified via timeChanged). */
    @Volatile
    private var resumeSettled = false

    /** Guards the deferred start against a release before the window showed. */
    @Volatile
    private var released = false

    /** Native vlcj factories are torn down on the EDT once. */
    @Volatile
    private var nativeReleased = false

    /**
     * Applies the resume seek and verifies it landed. On network sources the
     * first `playing` event can fire before the demuxer accepts seeks, so the
     * initial setTime is retried (then via `lengthChanged`) until
     * [resumeSettled] flips — confirmed by observing time >= target in
     * [timeChanged]. Capped at 10 attempts with 250ms gaps.
     */
    private fun applyResumeWithRetry(mediaPlayer: MediaPlayer) {
        var attempts = 0
        val retryThread = Thread({
            while (!released && !resumeSettled && attempts < 10) {
                attempts++
                mediaPlayer.controls().setTime(startPositionMs)
                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "vlc-resume").apply {
            isDaemon = true
            start()
        }
    }

    private fun emitIfActive(event: EngineEvent) {
        if (!released) bus.emit(event)
    }

    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                if (released) return
                startedPlaying = true
                // Resume where a style change interrupted playback (the
                // engine is rebuilt to re-read the global freetype args).
                // For network streams the position is not yet settled at the
                // first `playing` event: setTime can race media setup and be
                // swallowed. Retry until it actually takes, capped at 5s.
                if (startPositionMs > 0 && !startApplied) {
                    startApplied = true
                    applyResumeWithRetry(mediaPlayer)
                }
                emitIfActive(EngineEvent.Playing)
                emitTracks(mediaPlayer)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (released) return
                // If the resume seek was swallowed before the length was known
                // (typical on network sources), retry once the duration lands.
                if (startPositionMs > 0 && startApplied && !resumeSettled) {
                    applyResumeWithRetry(mediaPlayer)
                }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                if (released) return
                emitIfActive(EngineEvent.Buffering(newCache.coerceIn(0f, 100f)))
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                emitIfActive(EngineEvent.Paused)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                emitIfActive(EngineEvent.Paused)
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, time: Long) {
                if (released) return
                // Confirms the resume seek landed: once the demuxer reports a
                // time at/after the target, stop retrying.
                if (startPositionMs > 0 && !resumeSettled && time >= startPositionMs - 1_000) {
                    resumeSettled = true
                }
                emitIfActive(EngineEvent.Position(time, mediaPlayer.status().length()))
            }

            override fun elementaryStreamAdded(
                mediaPlayer: MediaPlayer,
                type: TrackType,
                id: Int,
            ) {
                if (released) return
                emitTracks(mediaPlayer)
            }

            override fun elementaryStreamSelected(
                mediaPlayer: MediaPlayer,
                type: TrackType,
                id: Int,
            ) {
                if (released) return
                emitTracks(mediaPlayer)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                if (released) return
                emitIfActive(
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
                    if (!released) {
                        if (startPositionMs > 0) {
                            player.media().play(url, ":start-time=${startPositionMs / 1000}")
                        } else {
                            player.media().play(url)
                        }
                    }
                }
            } else if (!released) {
                emitIfActive(EngineEvent.Error(EngineEvent.ErrorKind.Source))
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

    /**
     * Tears down native libVLC on the caller's thread (blocking EDT if invoked
     * off it). Prefer [releaseAsync] when leaving the player so navigation is
     * not stalled on HTTPS stop/teardown.
     */
    fun release() {
        released = true
        runReleaseOnEdt()
    }

    /** Detach from the UI first; native stop/release runs on a daemon thread. */
    fun releaseAsync() {
        released = true
        Thread({
            runReleaseOnEdt()
        }, "vlc-release").apply {
            isDaemon = true
            start()
        }
    }

    private fun runReleaseOnEdt() {
        val disposeNative = Runnable {
            if (nativeReleased) return@Runnable
            nativeReleased = true
            try {
                player.controls().stop()
            } catch (_: Throwable) {
            }
            // Component was built from [factory] and does not own it: release
            // the player first, then the factory.
            try {
                component.release()
            } catch (_: Throwable) {
            }
            try {
                factory.release()
            } catch (_: Throwable) {
            }
            bus.reset()
        }
        if (SwingUtilities.isEventDispatchThread()) {
            disposeNative.run()
        } else {
            try {
                SwingUtilities.invokeAndWait(disposeNative)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: InvocationTargetException) {
            }
        }
    }

    private fun emitTracks(mediaPlayer: MediaPlayer) {
        if (released) return
        val audio = mediaPlayer.audio().trackDescriptions().orEmpty().toTrackInfos()
        val subtitles = mediaPlayer.subpictures().trackDescriptions().orEmpty().toTrackInfos()
        emitIfActive(
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
