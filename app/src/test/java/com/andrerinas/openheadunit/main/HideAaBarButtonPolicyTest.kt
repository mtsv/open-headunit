package com.andrerinas.openheadunit.main

import com.andrerinas.openheadunit.main.HideAaBarButtonPolicy.TapOutcome
import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy.Ineligible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideAaBarButtonPolicyTest {

    @Test
    fun `shown only over a running session with the feature eligible`() {
        assertTrue(HideAaBarButtonPolicy.isVisible(null, transportStarted = true))
        assertFalse(HideAaBarButtonPolicy.isVisible(null, transportStarted = false))
        assertFalse(HideAaBarButtonPolicy.isVisible(Ineligible.NOT_ENABLED, transportStarted = true))
        assertFalse(HideAaBarButtonPolicy.isVisible(Ineligible.FIT_MODE, transportStarted = true))
    }

    @Test
    fun `it fades after five seconds alone`() {
        assertFalse(HideAaBarButtonPolicy.isFaded(lastWakeMs = 1_000, nowMs = 5_999))
        assertTrue(HideAaBarButtonPolicy.isFaded(lastWakeMs = 1_000, nowMs = 6_000))
    }

    // A half-visible button pressed by accident would cost a logo screen and the rail.
    @Test
    fun `a tap on a faded button wakes it and does not toggle`() {
        assertEquals(TapOutcome.WAKE, HideAaBarButtonPolicy.onTap(faded = true))
        assertEquals(TapOutcome.TOGGLE, HideAaBarButtonPolicy.onTap(faded = false))
    }

    @Test
    fun `faded is dimmer than awake but never invisible`() {
        assertEquals(0.8f, HideAaBarButtonPolicy.alpha(faded = false, configuredOpacityPercent = 80), 1e-4f)
        assertEquals(0.2f, HideAaBarButtonPolicy.alpha(faded = true, configuredOpacityPercent = 80), 1e-4f)
        assertEquals(0.1f, HideAaBarButtonPolicy.alpha(faded = true, configuredOpacityPercent = 0), 1e-4f)
    }

    @Test
    fun `the default sits centred on the top edge`() {
        assertEquals(
            Pair(847, 0),
            HideAaBarButtonPolicy.position(
                HideAaBarButtonPolicy.DEFAULT_X_PERCENT, HideAaBarButtonPolicy.DEFAULT_Y_PERCENT,
                80, 1775, 675,
            ),
        )
    }

    @Test
    fun `no stored position can put the button off the window`() {
        assertEquals(Pair(1695, 595), HideAaBarButtonPolicy.position(150, 300, 80, 1775, 675))
        assertEquals(Pair(0, 0), HideAaBarButtonPolicy.position(-20, -1, 80, 1775, 675))
        assertEquals(Pair(0, 0), HideAaBarButtonPolicy.position(50, 50, 900, 800, 600))
    }

    @Test
    fun `a dragged position is saved as the percent that puts it back`() {
        val (x, y) = HideAaBarButtonPolicy.percentOf(1695, 595, 80, 1775, 675)
        assertEquals(Pair(100, 100), Pair(x, y))
        assertEquals(Pair(1695, 595), HideAaBarButtonPolicy.position(x, y, 80, 1775, 675))
        assertEquals(Pair(0, 0), HideAaBarButtonPolicy.percentOf(-40, -10, 80, 1775, 675))
    }
}
