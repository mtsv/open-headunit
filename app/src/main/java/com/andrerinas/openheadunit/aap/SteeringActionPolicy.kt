package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.input.SteeringButton

/**
 * What a long press of a steering-wheel Voice or Phone button — or the matching automation command
 * — does: bring the projection to the front if it is hidden, then hand the button to Android Auto.
 *
 * With no session there is nobody to hand it to, so nothing happens. For Voice that is still the
 * point: its press was consumed (see [com.andrerinas.openheadunit.input.VoiceKeyConsumePolicy]),
 * so the stock "voice commands not implemented" text does not appear either.
 *
 * The raise is sent before the key, without waiting for the projection to come up: Android Auto
 * takes input without video focus. If the car shows the assistant or phone screen being lost
 * while focus returns, the wait belongs here.
 *
 * With "show the Android Auto bar first" on (user request, 2026-10-31), a hidden AA bar (the
 * "Hide Android Auto bar" tweak, `CanvasExtensionPolicy`) is shown between the raise and the
 * action, so the assistant or the phone screen comes up with the bar in place. It is only ever
 * shown, never hidden again afterwards: the press is a request to use Android Auto's own UI, and the
 * <-> button hides it again. A bar that is not hidden is left alone, and so is one whose tweak is
 * switched off, since its stored on/off state then means nothing on screen.
 */
object SteeringActionPolicy {

    sealed interface Step {
        object RaiseProjection : Step
        /** Show the hidden AA bar: store it as shown and have the projection re-apply that. */
        object ShowAaBar : Step
        /** [com.andrerinas.openheadunit.connection.CommManager.sendToggleVoiceAssistant]. */
        object ToggleAssistant : Step
        /** A KEYCODE_CALL click, which Android Auto answers with its phone screen. */
        object OpenPhone : Step
        data class Ignore(val why: String) : Step
    }

    /**
     * [showBarFirst] is the user's setting; [barEnabled] and [barHidden] are the hide-bar tweak's
     * `canvasExtensionEnabled` and `canvasExtensionActive`.
     */
    fun stepsFor(
        button: SteeringButton,
        sessionLive: Boolean,
        projectionForeground: Boolean,
        showBarFirst: Boolean = false,
        barEnabled: Boolean = false,
        barHidden: Boolean = false,
    ): List<Step> {
        if (!sessionLive) return listOf(Step.Ignore("no session"))
        val action = when (button) {
            SteeringButton.VOICE -> Step.ToggleAssistant
            SteeringButton.PHONE -> Step.OpenPhone
        }
        return listOfNotNull(
            Step.RaiseProjection.takeIf { !projectionForeground },
            Step.ShowAaBar.takeIf { showBarFirst && barEnabled && barHidden },
            action,
        )
    }
}
