package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.SteeringActionPolicy.Step
import com.andrerinas.openheadunit.input.SteeringButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringActionPolicyTest {

    @Test
    fun `with no session nothing happens`() {
        for (button in SteeringButton.values()) {
            for (fg in listOf(true, false)) {
                val steps = SteeringActionPolicy.stepsFor(button, sessionLive = false, projectionForeground = fg)
                assertEquals(1, steps.size)
                assertTrue(steps.single() is Step.Ignore)
            }
        }
    }

    @Test
    fun `a hidden projection is raised before the button is handed over`() {
        assertEquals(
            listOf(Step.RaiseProjection, Step.ToggleAssistant),
            SteeringActionPolicy.stepsFor(SteeringButton.VOICE, sessionLive = true, projectionForeground = false),
        )
        assertEquals(
            listOf(Step.RaiseProjection, Step.OpenPhone),
            SteeringActionPolicy.stepsFor(SteeringButton.PHONE, sessionLive = true, projectionForeground = false),
        )
    }

    @Test
    fun `a projection already in front is not raised again`() {
        assertEquals(
            listOf(Step.ToggleAssistant),
            SteeringActionPolicy.stepsFor(SteeringButton.VOICE, sessionLive = true, projectionForeground = true),
        )
        assertEquals(
            listOf(Step.OpenPhone),
            SteeringActionPolicy.stepsFor(SteeringButton.PHONE, sessionLive = true, projectionForeground = true),
        )
    }
}
