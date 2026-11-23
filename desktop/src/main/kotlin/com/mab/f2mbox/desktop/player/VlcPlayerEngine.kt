package com.mab.f2mbox.desktop.player

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

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
) {

    private val bus = EngineEventBus(listener)

    private val factory = MediaPlayerFactory("--avcodec-hw=any")

    val component: EmbeddedMediaPlayerComponent = EmbeddedMediaPlayerComponent()

    private val player: EmbeddedMediaPlayer = component.mediaPlayer()

    /** True once the first `playing` event arrived — see the error-kind KDoc. */
    private var startedPlaying = false

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
        player.media().play(url)
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
