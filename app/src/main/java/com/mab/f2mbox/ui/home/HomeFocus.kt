package com.mab.f2mbox.ui.home

/**
 * Focus ids of the home screen's focusable elements.
 *
 * The home screen has several kinds of focusable elements — the hero
 * slider, the quick links, the genre chips and the posters of every
 * section row. Each one reports its id to the screen's focus tracker, so
 * the focus can be put back on the very same element when the user
 * navigates back to the home screen.
 */

/** Focus id of the hero slider. */
const val HeroFocusId = "hero"

/** Row key of the "continue watching" row of the home screen. */
const val WatchedSectionRowKey = "watched"

/**
 * Focus id of the poster at [index] of the home section row [rowKey].
 *
 * The row key identifies the row itself (its position on the home screen),
 * because two sections of the payload may share the same id — or have none
 * at all — and their posters must not share a focus id.
 */
fun SectionPosterId(rowKey: String, index: Int): String = "section:$rowKey:$index"

/** Focus id of the quick-link chip at [index]. */
fun NavChipId(index: Int): String = "navChip:$index"

/**
 * Focus id of the genre chip at [index] of the quick link pointing at
 * [sectionUrl]. The url — not the index — identifies the row, because the
 * genre rows crossfade into each other and both rows are briefly composed.
 */
fun GenreChipId(sectionUrl: String?, index: Int): String =
    "genreChip:${sectionUrl.orEmpty()}:$index"
