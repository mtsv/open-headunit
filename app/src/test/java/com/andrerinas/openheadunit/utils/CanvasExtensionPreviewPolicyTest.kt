package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasExtensionPreviewPolicyTest {

    @Test
    fun `a single change waits the debounce`() {
        assertEquals(600L, CanvasExtensionPreviewPolicy.delayMs(nowMs = 10_000, firstPendingAtMs = 10_000))
    }

    @Test
    fun `each further change restarts the wait`() {
        assertEquals(600L, CanvasExtensionPreviewPolicy.delayMs(nowMs = 10_400, firstPendingAtMs = 10_000))
    }

    // Holding ±10 must still show the rail moving now and then.
    @Test
    fun `a long hold is sent once the first unsent change is old enough`() {
        assertEquals(300L, CanvasExtensionPreviewPolicy.delayMs(nowMs = 11_200, firstPendingAtMs = 10_000))
        assertEquals(0L, CanvasExtensionPreviewPolicy.delayMs(nowMs = 11_500, firstPendingAtMs = 10_000))
        assertEquals(0L, CanvasExtensionPreviewPolicy.delayMs(nowMs = 13_000, firstPendingAtMs = 10_000))
    }
}
