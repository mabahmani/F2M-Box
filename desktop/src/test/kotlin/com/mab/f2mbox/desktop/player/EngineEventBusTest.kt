package com.mab.f2mbox.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine's event pipeline, runnable without native libvlc: events
 * fan out to the single listener in order, and everything after an
 * Error is suppressed (the player is dead once it errors).
 */
class EngineEventBusTest {

    @Test
    fun `events fan out to the listener in order`() {
        val received = mutableListOf<EngineEvent>()
        val bus = EngineEventBus(received::add)

        bus.emit(EngineEvent.Playing)
        bus.emit(EngineEvent.Position(1_000, 60_000))
        bus.emit(EngineEvent.Paused)

        assertEquals(
            listOf(
                EngineEvent.Playing,
                EngineEvent.Position(1_000, 60_000),
                EngineEvent.Paused,
            ),
            received
        )
    }

    @Test
    fun `error is delivered once and everything after it is suppressed`() {
        val received = mutableListOf<EngineEvent>()
        val bus = EngineEventBus(received::add)

        bus.emit(EngineEvent.Playing)
        bus.emit(EngineEvent.Error(EngineEvent.ErrorKind.Source))
        bus.emit(EngineEvent.Position(2_000, 60_000))
        bus.emit(EngineEvent.Error(EngineEvent.ErrorKind.Decode))

        assertEquals(EngineEvent.Error(EngineEvent.ErrorKind.Source), received.last())
        assertEquals(2, received.size)
    }

    @Test
    fun `reset re-opens the pipe after an error`() {
        val received = mutableListOf<EngineEvent>()
        val bus = EngineEventBus(received::add)
        bus.emit(EngineEvent.Error(EngineEvent.ErrorKind.Network))
        bus.reset()
        bus.emit(EngineEvent.Playing)
        assertTrue(received.contains(EngineEvent.Playing))
    }
}
