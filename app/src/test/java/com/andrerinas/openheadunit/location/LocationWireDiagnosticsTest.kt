package com.andrerinas.openheadunit.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two rules, and the awkward cases in each.
 *
 * Sampling has to survive a clock that moves backwards, because the fault this diagnostic exists to
 * find is a wrong clock: a unit that boots with the wrong time and is then corrected by NTP jumps
 * its wall clock, and a naive `now - last >= interval` goes silent for however long the jump was -
 * exactly across the window worth reading.
 *
 * The skew sign is the other: positive and negative mean opposite faults and only one of them is
 * physically ordinary, so the verdict has to name the direction rather than the magnitude alone.
 */
class LocationWireDiagnosticsTest {

    private val interval = LocationWireDiagnostics.SAMPLE_INTERVAL_MS

    // --- when a fix is worth a line ---

    @Test
    fun `the first fix of a session is always sampled`() {
        assertTrue(LocationWireDiagnostics.shouldSample(nowMs = 1_000_000L, lastSampleAtMs = 0L))
    }

    @Test
    fun `a fix inside the interval is not sampled`() {
        assertFalse(LocationWireDiagnostics.shouldSample(
            nowMs = 1_000_000L + interval - 1, lastSampleAtMs = 1_000_000L))
    }

    @Test
    fun `a fix exactly at the interval is sampled`() {
        assertTrue(LocationWireDiagnostics.shouldSample(
            nowMs = 1_000_000L + interval, lastSampleAtMs = 1_000_000L))
    }

    @Test
    fun `a clock that jumped backwards still samples rather than going silent`() {
        // NTP correcting a unit that booted with a wrong time - the very fault being watched for.
        assertTrue(LocationWireDiagnostics.shouldSample(
            nowMs = 500_000L, lastSampleAtMs = 1_000_000L))
    }

    @Test
    fun `the interval is overridable for a caller that wants a different cadence`() {
        assertTrue(LocationWireDiagnostics.shouldSample(
            nowMs = 2_000L, lastSampleAtMs = 1_000L, intervalMs = 1_000L))
        assertFalse(LocationWireDiagnostics.shouldSample(
            nowMs = 1_999L, lastSampleAtMs = 1_000L, intervalMs = 1_000L))
    }

    // --- what the skew says ---

    @Test
    fun `skew is the clock minus the time the fix carries`() {
        assertEquals(250L, LocationWireDiagnostics.skewMs(fixTimeMs = 1_000_000L, nowMs = 1_000_250L))
    }

    @Test
    fun `a fix dated in the future gives a negative skew`() {
        assertEquals(-5_000L, LocationWireDiagnostics.skewMs(
            fixTimeMs = 1_005_000L, nowMs = 1_000_000L))
    }

    @Test
    fun `ordinary fix latency is not suspect`() {
        // A few hundred ms is the chip and the scheduler, not a broken clock.
        assertFalse(LocationWireDiagnostics.isSuspect(250L))
        assertFalse(LocationWireDiagnostics.isSuspect(-250L))
        assertFalse(LocationWireDiagnostics.isSuspect(0L))
    }

    @Test
    fun `skew is suspect in both directions once it reaches the threshold`() {
        val t = LocationWireDiagnostics.SUSPECT_SKEW_MS
        assertTrue(LocationWireDiagnostics.isSuspect(t))
        assertTrue(LocationWireDiagnostics.isSuspect(-t))
        assertFalse(LocationWireDiagnostics.isSuspect(t - 1))
        assertFalse(LocationWireDiagnostics.isSuspect(-(t - 1)))
    }

    @Test
    fun `an unremarkable clock says so rather than staying silent`() {
        assertEquals("clock agrees with GPS time", LocationWireDiagnostics.skewVerdict(120L))
    }

    @Test
    fun `the verdict names the direction, because the two mean different faults`() {
        val ahead = LocationWireDiagnostics.skewVerdict(8_000L)
        assertTrue(ahead, ahead.contains("AHEAD"))
        assertTrue(ahead, ahead.contains("8s"))

        val behind = LocationWireDiagnostics.skewVerdict(-8_000L)
        assertTrue(behind, behind.contains("BEHIND"))
        assertTrue(behind, behind.contains("8s"))
    }

    @Test
    fun `a future-dated fix never reports a negative number of seconds`() {
        // The sign carries the direction in words; the magnitude must read as a plain duration.
        val verdict = LocationWireDiagnostics.skewVerdict(-12_000L)
        assertFalse(verdict, verdict.contains("-12"))
        assertTrue(verdict, verdict.contains("12s"))
    }
}
