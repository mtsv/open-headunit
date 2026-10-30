package com.andrerinas.openheadunit.location

import kotlin.math.abs

/**
 * When to record what a LOCATION sensor event actually put on the wire, and how to read the clock
 * skew that comes with it.
 *
 * Added because the head unit reached the point where every observable thing on its side was
 * correct - a fix from its own GPS chip, encoded, sent at 1 Hz, sensor started, no drops and no send
 * failures - and Android Auto still navigated from the phone's own position. At that point the only
 * unexamined link left is what the bytes say, so this samples them.
 *
 * Two things are worth watching, and neither is the position:
 *
 * **The encoded scales.** `sensors.proto` carries no units, so every multiplier in LocationUpdateEvent
 * is a guess inherited from the original reverse engineering: degrees x 1e7, metres x 1e3, degrees of
 * bearing x 1e6. A receiver that disagreed about any of them would reject the fix while the head unit
 * saw nothing wrong at all, which is indistinguishable from what is happening now.
 *
 * **The timestamp.** A resent fix carries `System.currentTimeMillis()` rather than the GPS time the
 * chip reported, so on a unit whose clock is wrong the resend is stamped wrong too - and a fix that
 * reads stale or future-dated is exactly the kind a phone discards without comment. [skewMs] measures
 * the head unit's clock against GPS time, which is the one half of that comparison this side can see;
 * the other half is the phone's clock, and only a phone-side log shows that.
 */
object LocationWireDiagnostics {

    /**
     * How often a sample is worth a line.
     *
     * Fixes arrive at 1 Hz for a whole drive, so logging each one buries everything else in a log a
     * reporter attaches to an issue. Ten seconds is frequent enough to show drift building over a
     * drive and rare enough to read.
     */
    const val SAMPLE_INTERVAL_MS = 10_000L

    /**
     * How far the clock may sit from GPS time before it is worth calling out.
     *
     * Three seconds is well beyond the hundreds of milliseconds that ordinary scheduling and the
     * chip's own fix latency account for, and well under the tens of seconds that would make a fix
     * obviously stale. A unit inside this band is not the explanation for anything.
     */
    const val SUSPECT_SKEW_MS = 3_000L

    /**
     * Whether this fix is the one to log.
     *
     * @param nowMs the current wall clock.
     * @param lastSampleAtMs when a sample was last taken, or 0 if never.
     * @return true for the first fix of a session and then once per [intervalMs].
     */
    fun shouldSample(
        nowMs: Long,
        lastSampleAtMs: Long,
        intervalMs: Long = SAMPLE_INTERVAL_MS,
    ): Boolean {
        if (lastSampleAtMs <= 0L) return true
        // A clock that jumped backwards (NTP correcting a unit that boots with a wrong time, which
        // is the very fault this watches for) must not stop sampling until it catches up again.
        if (nowMs < lastSampleAtMs) return true
        return nowMs - lastSampleAtMs >= intervalMs
    }

    /**
     * How far the head unit's clock sits from the time the fix carries.
     *
     * Positive means the clock runs ahead of the fix - the normal direction, and the ordinary case
     * is the few hundred milliseconds the fix took to arrive. Negative means the fix is dated in the
     * future, which only a wrong clock produces.
     */
    fun skewMs(fixTimeMs: Long, nowMs: Long): Long = nowMs - fixTimeMs

    /** Whether [skewMs] is far enough out to be worth investigating. */
    fun isSuspect(skewMs: Long): Boolean = abs(skewMs) >= SUSPECT_SKEW_MS

    /**
     * The skew in words, so a log reader does not have to do the arithmetic or remember the sign.
     *
     * @return a short phrase naming the direction and whether it matters.
     */
    fun skewVerdict(skewMs: Long): String = when {
        !isSuspect(skewMs) -> "clock agrees with GPS time"
        skewMs > 0 -> "head unit clock is ${skewMs / 1000}s AHEAD of GPS time, so a resent fix is " +
            "stamped that far in the past and the phone may read it as stale"
        else -> "head unit clock is ${-skewMs / 1000}s BEHIND GPS time, so a resent fix is stamped " +
            "in the future and the phone may discard it outright"
    }
}
