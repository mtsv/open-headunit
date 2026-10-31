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
 */
object SteeringActionPolicy {

    sealed interface Step {
        object RaiseProjection : Step
        /** [com.andrerinas.openheadunit.connection.CommManager.sendToggleVoiceAssistant]. */
        object ToggleAssistant : Step
        /** A KEYCODE_CALL click, which Android Auto answers with its phone screen. */
        object OpenPhone : Step
        data class Ignore(val why: String) : Step
    }

    fun stepsFor(button: SteeringButton, sessionLive: Boolean, projectionForeground: Boolean): List<Step> {
        if (!sessionLive) return listOf(Step.Ignore("no session"))
        val action = when (button) {
            SteeringButton.VOICE -> Step.ToggleAssistant
            SteeringButton.PHONE -> Step.OpenPhone
        }
        return if (projectionForeground) listOf(action) else listOf(Step.RaiseProjection, action)
    }
}
