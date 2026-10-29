package com.andrerinas.openheadunit.utils

/**
 * Whether the margins the phone was told about still describe the panel. init() re-reads the
 * display metrics on every scale update, so a device whose window insets are still settling can
 * leave one margin on the wire and a different one in use, with nothing to reconcile them.
 */
object MarginAnnouncementPolicy {

    /** Nothing has been announced yet, so there is nothing to have drifted from. */
    const val NOT_ANNOUNCED = -1

    /**
     * [announcedTopLeft] / [liveTopLeft]: whether the margins sit all on the right and bottom (the
     * hidden AA bar's canvas, CanvasExtensionPolicy) rather than split evenly. The same totals
     * with the canvas moved to the other anchor is still a different picture for AA.
     */
    fun shouldReannounce(
        announcedWidthMargin: Int,
        announcedHeightMargin: Int,
        liveWidthMargin: Int,
        liveHeightMargin: Int,
        announcedTopLeft: Boolean = false,
        liveTopLeft: Boolean = false,
    ): Boolean {
        if (announcedWidthMargin == NOT_ANNOUNCED || announcedHeightMargin == NOT_ANNOUNCED) return false
        return announcedWidthMargin != liveWidthMargin || announcedHeightMargin != liveHeightMargin ||
            announcedTopLeft != liveTopLeft
    }

    /**
     * Whether a projection activity going away should forget what the phone was told. Only once
     * the session is over: the next one's service discovery records its own. Android destroys and
     * recreates the activity mid-session (another app in front, then back), and the phone still
     * holds the last announcement; forgetting it made every later drift look like "nothing
     * announced", so the hidden AA bar's <-> stopped reaching the phone after such a return.
     */
    fun forgetOnProjectionDestroy(sessionLive: Boolean): Boolean = !sessionLive
}
