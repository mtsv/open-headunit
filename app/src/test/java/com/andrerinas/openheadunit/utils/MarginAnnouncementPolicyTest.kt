package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measured on a Moto edge 30 neo whose window insets were still settling: 0x102 went on the wire
 * from a transient 2237x1080 reading and 0x144 was used for touch a few seconds later.
 */
class MarginAnnouncementPolicyTest {

    private val none = MarginAnnouncementPolicy.NOT_ANNOUNCED

    @Test
    fun `a margin that moved after it was announced is re-announced`() {
        assertTrue(MarginAnnouncementPolicy.shouldReannounce(0, 102, 0, 144))
        assertTrue(MarginAnnouncementPolicy.shouldReannounce(480, 360, 0, 360))
    }

    @Test
    fun `a margin that did not move is left alone`() {
        assertFalse(MarginAnnouncementPolicy.shouldReannounce(0, 144, 0, 144))
        assertFalse(MarginAnnouncementPolicy.shouldReannounce(0, 0, 0, 0))
    }

    // Hiding the AA bar can land on the same totals with the canvas moved to the top-left.
    @Test
    fun `the same margins moved to the other anchor are re-announced`() {
        assertTrue(MarginAnnouncementPolicy.shouldReannounce(0, 46, 0, 46, announcedTopLeft = false, liveTopLeft = true))
        assertTrue(MarginAnnouncementPolicy.shouldReannounce(0, 46, 0, 46, announcedTopLeft = true, liveTopLeft = false))
        assertFalse(MarginAnnouncementPolicy.shouldReannounce(0, 46, 0, 46, announcedTopLeft = true, liveTopLeft = true))
        assertFalse(MarginAnnouncementPolicy.shouldReannounce(none, none, 0, 46, liveTopLeft = true))
    }

    // Every recalculate before the capability message would otherwise look like a drift.
    @Test
    fun `nothing announced yet is not a drift`() {
        assertFalse(MarginAnnouncementPolicy.shouldReannounce(none, none, 0, 144))
        assertFalse(MarginAnnouncementPolicy.shouldReannounce(none, 144, 0, 144))
        assertFalse(MarginAnnouncementPolicy.shouldReannounce(0, none, 0, 144))
    }
}
