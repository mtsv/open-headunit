package com.andrerinas.openheadunit.utils

/**
 * When a value tuned in the "Hidden area" dialog over the projection is sent to the phone.
 *
 * The picture follows every press at once; only the UpdateUiConfigRequest waits. The spike showed
 * AA settles on the last of a burst, so this is not about protocol safety: every re-announce costs
 * AA's ~1 s logo screen, and holding ±10 would otherwise flash it on every step. The wait restarts
 * on each change, but never past [MAX_WAIT_MS] from the first unsent one, so a long hold still
 * shows the rail moving.
 */
object CanvasExtensionPreviewPolicy {

    const val DEBOUNCE_MS = 600L
    const val MAX_WAIT_MS = 1_500L

    /** How long to wait from [nowMs] before sending, given the first unsent change came at [firstPendingAtMs]. */
    fun delayMs(nowMs: Long, firstPendingAtMs: Long): Long {
        val untilCap = MAX_WAIT_MS - (nowMs - firstPendingAtMs)
        return minOf(DEBOUNCE_MS, untilCap).coerceAtLeast(0L)
    }
}
