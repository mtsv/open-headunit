package com.andrerinas.openheadunit.main

import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy

/**
 * The round `<->` button that switches "Hide Android Auto bar" (features/fullscreen-plan.md §6).
 * Unlike the Home button it lives inside the projection activity, over the picture, so it fades
 * when left alone, and a tap on a faded button only wakes it: a half-visible button pressed by
 * accident would cost the driver a second of AA's logo screen and a rail they did not ask for.
 */
object HideAaBarButtonPolicy {

    enum class TapOutcome { WAKE, TOGGLE }

    const val FADE_AFTER_MS = 5_000L

    /** Faded but never invisible, so the way back to the rail can always be found. */
    const val FADED_ALPHA = 0.2f

    /** Default position (Q15): the top edge, centred. */
    const val DEFAULT_X_PERCENT = 50
    const val DEFAULT_Y_PERCENT = 0

    /**
     * Only over a running session: before TransportStarted there is no AA layout to switch.
     * [ineligible] already covers the feature being switched off in settings.
     */
    fun isVisible(ineligible: CanvasExtensionPolicy.Ineligible?, transportStarted: Boolean): Boolean =
        ineligible == null && transportStarted

    fun isFaded(lastWakeMs: Long, nowMs: Long): Boolean = nowMs - lastWakeMs >= FADE_AFTER_MS

    fun onTap(faded: Boolean): TapOutcome = if (faded) TapOutcome.WAKE else TapOutcome.TOGGLE

    /** The configured opacity while awake; at most that while faded. */
    fun alpha(faded: Boolean, configuredOpacityPercent: Int): Float {
        val awake = (configuredOpacityPercent / 100f).coerceIn(0.1f, 1.0f)
        return if (faded) minOf(FADED_ALPHA, awake) else awake
    }

    /**
     * The button's top-left in window px. The percent is of the room left once the button fits,
     * so 0 and 100 put it flush with an edge and no stored value can push it into a hidden strip
     * or off the window.
     */
    fun position(
        xPercent: Int,
        yPercent: Int,
        buttonSizePx: Int,
        windowW: Int,
        windowH: Int,
    ): Pair<Int, Int> {
        val roomX = (windowW - buttonSizePx).coerceAtLeast(0)
        val roomY = (windowH - buttonSizePx).coerceAtLeast(0)
        return Pair(roomX * xPercent.coerceIn(0, 100) / 100, roomY * yPercent.coerceIn(0, 100) / 100)
    }

    /** The inverse of [position], for saving where a drag ended. */
    fun percentOf(left: Int, top: Int, buttonSizePx: Int, windowW: Int, windowH: Int): Pair<Int, Int> {
        val roomX = windowW - buttonSizePx
        val roomY = windowH - buttonSizePx
        val x = if (roomX > 0) (left.coerceIn(0, roomX) * 100f / roomX).toInt() else DEFAULT_X_PERCENT
        val y = if (roomY > 0) (top.coerceIn(0, roomY) * 100f / roomY).toInt() else DEFAULT_Y_PERCENT
        return Pair(x, y)
    }
}
