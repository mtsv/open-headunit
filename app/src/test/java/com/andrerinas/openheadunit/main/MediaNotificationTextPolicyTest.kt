package com.andrerinas.openheadunit.main

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The lopsided cases are the point of this table. Joining two fields is trivial when both arrive;
 * what the notification must never show is a line that leads or trails with a separator because one
 * of them did not, and Android Auto sends a blank field rather than omitting it, so "absent" and
 * "empty" have to mean the same thing here.
 */
class MediaNotificationTextPolicyTest {

    @Test
    fun `both fields are joined by the separator`() {
        assertEquals(
            "Ludovico Einaudi - Nightbook",
            MediaNotificationTextPolicy.artistLine("Ludovico Einaudi", "Nightbook")
        )
    }

    @Test
    fun `an artist with no album stands alone, with no trailing separator`() {
        assertEquals("Ludovico Einaudi", MediaNotificationTextPolicy.artistLine("Ludovico Einaudi", null))
        assertEquals("Ludovico Einaudi", MediaNotificationTextPolicy.artistLine("Ludovico Einaudi", ""))
        assertEquals("Ludovico Einaudi", MediaNotificationTextPolicy.artistLine("Ludovico Einaudi", "   "))
    }

    @Test
    fun `an album with no artist stands alone, with no leading separator`() {
        assertEquals("Nightbook", MediaNotificationTextPolicy.artistLine(null, "Nightbook"))
        assertEquals("Nightbook", MediaNotificationTextPolicy.artistLine("", "Nightbook"))
        assertEquals("Nightbook", MediaNotificationTextPolicy.artistLine("   ", "Nightbook"))
    }

    @Test
    fun `neither field leaves the line empty rather than showing a separator`() {
        assertEquals("", MediaNotificationTextPolicy.artistLine(null, null))
        assertEquals("", MediaNotificationTextPolicy.artistLine("", ""))
        assertEquals("", MediaNotificationTextPolicy.artistLine("  ", "  "))
    }

    @Test
    fun `surrounding whitespace never reaches the notification`() {
        assertEquals(
            "Ludovico Einaudi - Nightbook",
            MediaNotificationTextPolicy.artistLine("  Ludovico Einaudi ", " Nightbook  ")
        )
    }

    @Test
    fun `a track with no metadata at all produces the same empty line as before`() {
        // What the notification showed when it read metadata.artist directly and nothing was set:
        // proto2 hands back an empty string for an unset optional field.
        assertEquals("", MediaNotificationTextPolicy.artistLine("", ""))
    }
}
