package com.mab.f2mbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * Registry of focus requesters, keyed by a caller-chosen id.
 *
 * A screen that wants its focus position restored after navigating away and
 * back keeps one of these, attaches [requesterFor] to every element that can
 * hold focus, and remembers the id of the element the user left. On return
 * the screen asks the registry for that id again: the requester instance is
 * the very same one the element attached, so the focus lands on the exact
 * element the user was on.
 */
class FocusRegistry {

    private val requesters = mutableMapOf<String, FocusRequester>()

    /**
     * The requester for [id] — the same instance for every call, so an
     * element and a restore request always talk about the same node.
     */
    fun requesterFor(id: String): FocusRequester =
        requesters.getOrPut(id) { FocusRequester() }

    /**
     * Requests focus on the element with [id], retrying while it is not
     * composed yet (lazy items attach late, and a restored scroll position
     * may still be settling). Gives up after [maxAttempts] tries so a screen
     * whose element is gone does not spin forever.
     */
    suspend fun requestFocus(id: String, maxAttempts: Int = 40) {
        val requester = requesterFor(id)
        repeat(maxAttempts) {
            if (requester.requestFocus()) return
            delay(FocusRetryDelayMs)
        }
    }
}

/** Delay between two attempts to grab focus. */
private const val FocusRetryDelayMs = 50L

/**
 * Focus id of the poster at [index] of a poster grid. The listing and the
 * person screen show the same kind of grid, so they share the ids.
 */
fun GridPosterId(index: Int): String = "grid:$index"

/** Focus id of the "previous page" button of a paginated screen. */
const val PagerPreviousId = "pagerPrevious"

/** Focus id of the "next page" button of a paginated screen. */
const val PagerNextId = "pagerNext"

/**
 * The focused element of a screen, tracked by id, so the focus position can
 * be restored when the user navigates back to the screen.
 */
class FocusTracker internal constructor(
    private val registry: FocusRegistry,
    private val focusedIdState: MutableState<String?>
) {

    /** Id of the element that holds the focus, or null if none did yet. */
    val focusedId: String? get() = focusedIdState.value

    /** The requester an element must attach to become restorable. */
    fun requesterFor(id: String): FocusRequester = registry.requesterFor(id)

    /** Call from an element's focus change to record it as the focused one. */
    fun onFocused(id: String) {
        focusedIdState.value = id
    }

    /**
     * Puts the focus back where the user left it. When nothing was focused
     * yet, [fallbackId] takes over.
     */
    suspend fun restore(fallbackId: String) {
        registry.requestFocus(focusedId ?: fallbackId)
    }
}

/**
 * A [FocusTracker] whose focused id survives navigating away and back.
 *
 * The tracker lives inside the screen's saved-state scope, so the id of the
 * focused element is restored together with the screen's scroll position and
 * pagination.
 */
@Composable
fun rememberFocusTracker(): FocusTracker {
    val registry = remember { FocusRegistry() }
    val focusedId = rememberSaveable(saver = NullableStringStateSaver) {
        mutableStateOf<String?>(null)
    }
    return remember(registry, focusedId) { FocusTracker(registry, focusedId) }
}
