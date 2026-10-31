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

    // "Show the Android Auto bar first" (2026-10-31): a hidden bar is shown after the raise and
    // before the action, and only then.

    private fun withBar(button: SteeringButton, fg: Boolean, setting: Boolean, enabled: Boolean, hidden: Boolean) =
        SteeringActionPolicy.stepsFor(
            button, sessionLive = true, projectionForeground = fg,
            showBarFirst = setting, barEnabled = enabled, barHidden = hidden,
        )

    @Test
    fun `a hidden bar is shown after the raise and before the action`() {
        assertEquals(
            listOf(Step.RaiseProjection, Step.ShowAaBar, Step.ToggleAssistant),
            withBar(SteeringButton.VOICE, fg = false, setting = true, enabled = true, hidden = true),
        )
        assertEquals(
            listOf(Step.RaiseProjection, Step.ShowAaBar, Step.OpenPhone),
            withBar(SteeringButton.PHONE, fg = false, setting = true, enabled = true, hidden = true),
        )
    }

    @Test
    fun `a hidden bar is shown even when the projection is already in front`() {
        assertEquals(
            listOf(Step.ShowAaBar, Step.OpenPhone),
            withBar(SteeringButton.PHONE, fg = true, setting = true, enabled = true, hidden = true),
        )
    }

    @Test
    fun `the bar is left alone when the setting is off, it is already shown, or its tweak is off`() {
        for (button in SteeringButton.values()) for (fg in listOf(true, false)) {
            for ((setting, enabled, hidden) in listOf(
                Triple(false, true, true),
                Triple(true, true, false),
                Triple(true, false, true),
            )) {
                val steps = withBar(button, fg, setting, enabled, hidden)
                assertTrue("$button fg=$fg $setting/$enabled/$hidden: $steps", Step.ShowAaBar !in steps)
            }
        }
    }

    @Test
    fun `with no session the bar is not touched either`() {
        val steps = SteeringActionPolicy.stepsFor(
            SteeringButton.VOICE, sessionLive = false, projectionForeground = false,
            showBarFirst = true, barEnabled = true, barHidden = true,
        )
        assertTrue(steps.single() is Step.Ignore)
    }
}
