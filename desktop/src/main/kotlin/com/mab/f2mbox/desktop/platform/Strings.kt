package com.mab.f2mbox.desktop.platform

/**
 * Desktop string table — verbatim port of
 * `app/src/main/res/values/strings.xml`. Names are the resource names
 * camelCased (`search_hint` → `searchHint`); format strings keep the
 * Android `%1$x` placeholders and are used with [String.format].
 *
 * Strings that end in punctuation/ellipsis carry a trailing LRM
 * (`\u200E`) exactly like the Android resources: they are LTR-leaning
 * and would otherwise have the punctuation pulled to the wrong side by
 * the RTL bidi pass.
 */
object Strings {
    const val appName = "F2M Box"
    const val tagline = "Your World of Entertainment.\u200E"
    const val shortDescription = "Movies and series, all in one place.\u200E"

    // Connect / splash
    const val serverWakeupHint = "The server may take up to 2 minutes to wake up"
    const val retry = "Retry"
    const val playExternally = "Play in system player"

    // Home
    const val loading = "Loading…\u200E"
    const val ratingFormat = "★ %1\$s"
    const val seeMore = "See more"
    const val heroPreviousSlide = "Previous hero slide"
    const val heroNextSlide = "Next hero slide"
    const val heroOpenSlide = "Open featured title"

    // Search
    const val search = "Search"
    const val clearCacheRefresh = "Clear cache & refresh\u200E"
    const val viewLogs = "Logs"
    const val clearLogs = "Clear logs"
    const val logsEmpty = "No logs yet."
    const val logsLastCrash = "Last crash (previous session)"
    const val crashContinue = "Continue anyway"
    const val searchHint = "Movie or series name…\u200E"
    const val searchHintDetail = "Press OK on the field to open the keyboard.\u200E"
    const val noResults = "No results found"

    // Listing
    const val pageFormat = "Page %1\$d\u200E"
    const val previous = "Previous"
    const val next = "Next"

    // Detail
    const val back = "Back"
    const val infoRelease = "Release"
    const val infoPublished = "Published"
    const val infoDuration = "Duration"
    const val infoDurationValue = "%1\$d min\u200E"
    const val infoCountries = "Countries"
    const val infoLanguage = "Language"
    const val directors = "Directors"
    const val cast = "Cast"
    const val castAndCrew = "Cast & crew"
    const val suggested = "Suggested"
    const val comments = "Comments (%1\$d)\u200E"
    const val moreComments = "More comments"
    const val commentsError = "Couldn't load comments.\u200E"
    const val likeCount = "👍 %1\$d"
    const val dislikeCount = "👎 %1\$d"
    const val trailer = "Trailer"
    const val watchTrailer = "Watch trailer"
    const val dubbing = "Dubbing"
    const val voiceActors = "Voice actors"
    const val downloads = "Play"

    // Player
    const val playerLoading = "Loading video"
    const val playerBuffering = "Buffering"
    const val playerPlay = "Play"
    const val playerPause = "Pause"
    const val playerReplay = "Replay"
    const val playerSkipBack = "Skip back 10 seconds"
    const val playerSkipForward = "Skip forward 10 seconds"
    const val playerFullscreen = "Full screen"
    const val playerExitFullscreen = "Exit full screen"
    const val playerSpeed = "Playback speed"
    const val playerSubtitles = "Subtitles"
    const val playerSubtitlesOff = "Off"
    const val playerAudio = "Audio track"
    const val playerAudioAuto = "Auto"
    const val playerError = "Couldn't play the video. Check your connection and try again.\u200E"
    const val playerErrorDecode = "This device can't decode this video. Try another quality.\u200E"
    const val playerErrorDecodeCodec = "This device can't decode %1\$s. Try another quality.\u200E"
    const val playerErrorNetwork = "Playback failed. Check your connection and try again.\u200E"
    const val playerSubtitleCustomize = "Customize…"
    const val playerSubtitleSize = "Size"
    const val playerSubtitleWeight = "Weight"
    const val playerSubtitleColor = "Text color"
    const val playerSubtitleBackground = "Background"
    const val playerSubtitleEdge = "Edge"
    const val playerSubtitleStrength = "Edge strength"
    const val playerSubtitleOffset = "Bottom offset"
    const val playerSubtitleOptSmall = "Small"
    const val playerSubtitleOptNormal = "Normal"
    const val playerSubtitleOptBold = "Bold"
    const val playerSubtitleOptLarge = "Large"
    const val playerSubtitleOptXl = "Extra large"
    const val playerSubtitleOptWhite = "White"
    const val playerSubtitleOptYellow = "Yellow"
    const val playerSubtitleOptCyan = "Cyan"
    const val playerSubtitleOptGreen = "Green"
    const val playerSubtitleOptOrange = "Orange"
    const val playerSubtitleOptPink = "Pink"
    const val playerSubtitleOptLightBlue = "Light blue"
    const val playerSubtitleOptNone = "None"
    const val playerSubtitleOptShadow = "Shadow"
    const val playerSubtitleOptOutline = "Outline"
    const val playerSubtitleCancel = "Cancel"
    const val playerSubtitleSave = "Save"

    // Person
    const val personKindActor = "Actor"
    const val personKindDirector = "Director"
    const val personKindWriter = "Writer"
    const val personKindCreator = "Creator"

    // Errors
    const val errorNoInternet = "No internet connection. Check your network and try again.\u200E"
    const val errorTimeout = "The server took too long to respond. It may be waking up — try again.\u200E"
    const val errorConnect = "Could not connect to the F2M Box server.\u200E"
    const val errorServer = "Server error. Please try again.\u200E"
    const val errorUnexpectedResponse = "Received an unexpected response from the server.\u200E"
    const val errorUnknown = "Something went wrong. Please try again.\u200E"

    // Manual domain entry (last fallback when nothing connects)
    const val errorDomainHint = "Know the new site address? Enter it below (without https://).\u200E"
    const val errorDomainField = "example.com"
    const val errorDomainSubmit = "Connect"
    const val errorDomainInvalid = "Enter a valid domain, e.g. example.com\u200E"
}
