package com.mab.f2mbox.ui.common

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver

/**
 * Marker for "no value" in the string savers below. It cannot appear in a
 * url or in a focus id, so it is safe to use as a placeholder.
 */
private const val NO_VALUE = "\u0000"

/**
 * Saver for an int holder, so a plain counter survives navigating away and
 * back. `rememberSaveable` does not save a `MutableState` on its own — the
 * holder has to be wrapped in a saver that stores the plain value.
 */
val IntStateSaver: Saver<MutableIntState, Int> = Saver(
    save = { it.intValue },
    restore = { mutableIntStateOf(it) }
)

/** Saver for a nullable string holder. */
val NullableStringStateSaver: Saver<MutableState<String?>, String> = Saver(
    save = { it.value ?: NO_VALUE },
    restore = { mutableStateOf<String?>(if (it == NO_VALUE) null else it) }
)

/**
 * Saver for a holder of a list of optional strings — the visited-page stack
 * of a listing screen, whose first entry is a null marker.
 */
val NullableStringListStateSaver: Saver<MutableState<List<String?>>, List<String>> = Saver(
    save = { pages -> pages.value.map { page -> page ?: NO_VALUE } },
    restore = { pages -> mutableStateOf(pages.map { page -> if (page == NO_VALUE) null else page }) }
)

/** Saver for a holder of a set of ints — the expanded rows of a detail page. */
val IntSetStateSaver: Saver<MutableState<Set<Int>>, List<Int>> = Saver(
    save = { it.value.toList() },
    restore = { mutableStateOf(it.toSet()) }
)