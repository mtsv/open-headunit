package com.andrerinas.openheadunit.input

/**
 * Whether OHU's intercept listener on the Voyah key service swallows a key callback.
 *
 * Required by the user (2026-10-31): a long press of the steering wheel's Voice button must never
 * show the stock "voice commands not implemented" text. Observing the press is not enough to stop
 * that; the event has to be consumed before the stock handler sees it.
 *
 * Only Voice is ever consumed, and how much of it depends on Voyah Tweaks:
 *
 * - **With VT**, only the callbacks a long press alone produces ([longPhase]: hold start, long
 *   press start and stop). Build 10–12 consumed every Voice callback on the belief that VT eats
 *   short presses upstream of every listener, so only long ones arrive. The car showed otherwise
 *   (2026-10-31, build 12): a short Voice press reaches the intercept listener as `onKeyDown` +
 *   `onKeyShortClick`, consuming them silenced VT's voice commands, while the long press opened
 *   the assistant without the text. So the short-press callbacks pass through to whatever VT
 *   acts on; the stock text, which only ever showed on a long press, is cut off at the long ones.
 * - **Without VT**, the stock short press shows the same text as the long one and nothing else
 *   wants it, so every Voice callback is consumed. Short does nothing; long opens the assistant.
 *
 * The answer depends only on the key code and which callback it is, never on the long-press
 * decision or the session, so the binder callback can answer at once without waiting on the main
 * thread.
 *
 * Phone is never consumed: its short press must still open the stock call screen, and its stock
 * long press does nothing, so there is nothing to hide.
 */
object VoiceKeyConsumePolicy {

    fun consume(
        qgKeyCode: Int,
        longPhase: Boolean,
        featureOn: Boolean,
        voiceLongEnabled: Boolean,
        vtInstalled: Boolean,
    ): Boolean {
        if (!featureOn || !voiceLongEnabled) return false
        if (SteeringLongPressPolicy.buttonFor(qgKeyCode) != SteeringButton.VOICE) return false
        return longPhase || !vtInstalled
    }
}
