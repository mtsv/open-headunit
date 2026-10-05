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

/**
 * The boundary at one hour is the point of this table: the format changes there, and a 59:59 track
 * that grew an `hh:` field - or a 1:00:00 track that lost one - is the regression to catch. The
 * zero-length case pins that an unset duration appends nothing at all, since proto2 reports an
 * absent optional uint32 as 0 and a `[00:00]` suffix on every track without the field would be a
 * visible change to titles that previously carried none.
 */
class MediaNotificationTitleDurationTest {

    @Test
    fun `a track under an hour is m colon ss`() {
        assertEquals("3:45", MediaNotificationTextPolicy.formatDuration(225L))
    }

    @Test
    fun `seconds are zero-padded but the leading minutes are not`() {
        assertEquals("0:07", MediaNotificationTextPolicy.formatDuration(7L))
        assertEquals("9:05", MediaNotificationTextPolicy.formatDuration(545L))
    }

    @Test
    fun `the last second under an hour still has no hours field`() {
        assertEquals("59:59", MediaNotificationTextPolicy.formatDuration(3599L))
    }

    @Test
    fun `exactly one hour gains the hours field`() {
        assertEquals("1:00:00", MediaNotificationTextPolicy.formatDuration(3600L))
    }

    @Test
    fun `a track over an hour is h colon mm colon ss`() {
        assertEquals("1:05:03", MediaNotificationTextPolicy.formatDuration(3903L))
        assertEquals("2:30:00", MediaNotificationTextPolicy.formatDuration(9000L))
    }

    @Test
    fun `a duration past ten hours keeps every field, widening the hours`() {
        // A long podcast or an audiobook chapter; nothing may wrap or truncate.
        assertEquals("12:00:01", MediaNotificationTextPolicy.formatDuration(43201L))
    }

    @Test
    fun `the duration is appended to the title in square brackets`() {
        assertEquals(
            "Nuvole Bianche [5:57]",
            MediaNotificationTextPolicy.titleWithDuration("Nuvole Bianche", 357L)
        )
    }

    @Test
    fun `an absent duration leaves the title exactly as it was`() {
        // proto2 reports an unset optional uint32 as 0, so this is the no-duration case.
        assertEquals("Nuvole Bianche", MediaNotificationTextPolicy.titleWithDuration("Nuvole Bianche", 0L))
    }

    @Test
    fun `a negative duration is treated as absent rather than formatted`() {
        assertEquals("Nuvole Bianche", MediaNotificationTextPolicy.titleWithDuration("Nuvole Bianche", -1L))
    }

    @Test
    fun `the placeholder title takes a duration like any other`() {
        assertEquals("Video [1:00:00]", MediaNotificationTextPolicy.titleWithDuration("Video", 3600L))
    }
}
