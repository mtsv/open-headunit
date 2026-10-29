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
        val faded = HideAaBarButtonPolicy.DEFAULT_FADED_OPACITY_PERCENT
        assertEquals(0.8f, HideAaBarButtonPolicy.alpha(false, activeOpacityPercent = 80, fadedOpacityPercent = faded), 1e-4f)
        assertEquals(0.2f, HideAaBarButtonPolicy.alpha(true, activeOpacityPercent = 80, fadedOpacityPercent = faded), 1e-4f)
        assertEquals(0.1f, HideAaBarButtonPolicy.alpha(true, activeOpacityPercent = 0, fadedOpacityPercent = faded), 1e-4f)
        assertEquals(0.05f, HideAaBarButtonPolicy.alpha(true, activeOpacityPercent = 80, fadedOpacityPercent = 0), 1e-4f)
    }

    @Test
    fun `both levels are the user's, but faded never outshines active`() {
        assertEquals(1.0f, HideAaBarButtonPolicy.alpha(false, activeOpacityPercent = 100, fadedOpacityPercent = 40), 1e-4f)
        assertEquals(0.4f, HideAaBarButtonPolicy.alpha(true, activeOpacityPercent = 100, fadedOpacityPercent = 40), 1e-4f)
        assertEquals(0.5f, HideAaBarButtonPolicy.alpha(true, activeOpacityPercent = 50, fadedOpacityPercent = 70), 1e-4f)
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
