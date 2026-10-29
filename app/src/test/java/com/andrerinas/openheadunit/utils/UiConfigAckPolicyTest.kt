package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.utils.UiConfigAckPolicy.Next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the resend of an unanswered UpdateUiConfigRequest. Regression: a session started with the
 * AA bar hidden sent its margins before the phone's video channel was up, the phone dropped them
 * without a reply, and the picture was drawn stretched past the bottom of the screen.
 */
class UiConfigAckPolicyTest {

    @Test
    fun `an unanswered request in a live session is sent again`() {
        assertEquals(Next.RESEND, UiConfigAckPolicy.onTimeout(answered = false, sends = 1, sessionLive = true))
    }

    @Test
    fun `an answered request is left alone`() {
        assertEquals(Next.NOTHING, UiConfigAckPolicy.onTimeout(answered = true, sends = 1, sessionLive = true))
    }

    @Test
    fun `nothing is sent once the session is over`() {
        assertEquals(Next.NOTHING, UiConfigAckPolicy.onTimeout(answered = false, sends = 1, sessionLive = false))
    }

    @Test
    fun `a phone that never answers is given up on after the last send`() {
        val max = UiConfigAckPolicy.MAX_SENDS
        assertEquals(Next.RESEND, UiConfigAckPolicy.onTimeout(answered = false, sends = max - 1, sessionLive = true))
        assertEquals(Next.GIVE_UP, UiConfigAckPolicy.onTimeout(answered = false, sends = max, sessionLive = true))
    }

    @Test
    fun `the timeout is far above the reply time seen on the device`() {
        // Replies came within ~40 ms mid-session; a resend before then would only double up.
        assertTrue(UiConfigAckPolicy.ACK_TIMEOUT_MS >= 10 * 40)
    }

    @Test
    fun `the resends cover a phone that takes seconds to set up its video`() {
        // The dropped request went out ~0.3 s before the first frame; give startup several seconds.
        assertTrue(UiConfigAckPolicy.ACK_TIMEOUT_MS * UiConfigAckPolicy.MAX_SENDS >= 5_000L)
    }
}
