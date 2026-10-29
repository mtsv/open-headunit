package com.andrerinas.openheadunit.utils

/**
 * Whether an UpdateUiConfigRequest the phone has not answered is sent again.
 *
 * The phone answers each UpdateUiConfigRequest it takes with an UpdateUiConfigReply, within about
 * 40 ms on the head unit mid-session. A session that starts with the AA bar hidden re-announces at
 * TransportStarted, which comes right after service discovery and before the phone has set up the
 * video channel. The phone dropped that request with no reply and drew its normal full canvas,
 * which OHU's transform, sized for the hidden bar's shorter canvas, stretched past the bottom of
 * the screen until the bar was toggled off and on (HUR_Log_20261029_234712_869: two sessions
 * started with the bar hidden, neither request answered; every mid-session send was).
 *
 * So the last request counts as unanswered until the reply comes, and goes out again on a timeout
 * for as long as the session lasts. A reply names no request, so a late one for an earlier send
 * also settles the latest; both carry margins the phone has then been told.
 */
object UiConfigAckPolicy {

    /** Far above the ~40 ms seen on the device, and well inside AA's logo screen at connect. */
    const val ACK_TIMEOUT_MS = 1_000L

    /** Sends in all for one set of margins, the first included: about 8 s of a phone starting up. */
    const val MAX_SENDS = 8

    enum class Next { NOTHING, RESEND, GIVE_UP }

    /** What to do when [ACK_TIMEOUT_MS] has passed since the last of [sends] sends. */
    fun onTimeout(answered: Boolean, sends: Int, sessionLive: Boolean): Next = when {
        answered || !sessionLive -> Next.NOTHING
        sends >= MAX_SENDS -> Next.GIVE_UP
        else -> Next.RESEND
    }
}
