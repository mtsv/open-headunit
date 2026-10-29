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

    /**
     * How long the button stays active after a touch (Settings.canvasExtensionButtonFadeAfterSeconds).
     * 5 s was the fixed value before it was a setting. 0 keeps it active for good: it never fades,
     * so every tap toggles.
     */
    const val DEFAULT_FADE_AFTER_SECONDS = 5
    const val MAX_FADE_AFTER_SECONDS = 60

    /** When the button fades after a wake, in ms; null when it never does. */
    fun fadeAfterMs(fadeAfterSeconds: Int): Long? =
        if (fadeAfterSeconds <= 0) null else fadeAfterSeconds.coerceAtMost(MAX_FADE_AFTER_SECONDS) * 1_000L

    /**
     * Defaults of the two opacity settings. Active follows the Home button's opacity until it is
     * set on its own (Settings.canvasExtensionButtonOpacityPercent); faded is the 20% the button
     * had before either was configurable.
     */
    const val DEFAULT_FADED_OPACITY_PERCENT = 20

    /**
     * Faded may go all the way to invisible. It was held at 5% so the way back to the rail could
     * always be found; on the head unit an invisible button was asked for instead. It stays where it
     * was and still takes touches at 0 alpha, so a tap there wakes it to the active opacity.
     */
    const val MIN_FADED_OPACITY_PERCENT = 0
    const val MIN_ACTIVE_OPACITY_PERCENT = 10

    /** Default position (Q15): the top edge, centred. */
    const val DEFAULT_X_PERCENT = 50
    const val DEFAULT_Y_PERCENT = 0

    /**
     * Only over a running session: before TransportStarted there is no AA layout to switch.
     * [ineligible] already covers the feature being switched off in settings.
     */
    fun isVisible(ineligible: CanvasExtensionPolicy.Ineligible?, transportStarted: Boolean): Boolean =
        ineligible == null && transportStarted

    fun isFaded(lastWakeMs: Long, nowMs: Long, fadeAfterSeconds: Int = DEFAULT_FADE_AFTER_SECONDS): Boolean {
        val fadeAfterMs = fadeAfterMs(fadeAfterSeconds) ?: return false
        return nowMs - lastWakeMs >= fadeAfterMs
    }

    fun onTap(faded: Boolean): TapOutcome = if (faded) TapOutcome.WAKE else TapOutcome.TOGGLE

    /**
     * The active opacity while awake, the faded one after [fadeAfterMs]. Faded is never brighter
     * than active, so a faded button never looks pressable when the awake one does not.
     */
    fun alpha(faded: Boolean, activeOpacityPercent: Int, fadedOpacityPercent: Int): Float {
        val awake = activeOpacityPercent.coerceIn(MIN_ACTIVE_OPACITY_PERCENT, 100) / 100f
        if (!faded) return awake
        return minOf(fadedOpacityPercent.coerceIn(MIN_FADED_OPACITY_PERCENT, 100) / 100f, awake)
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
