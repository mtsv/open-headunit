package com.andrerinas.openheadunit.input

import com.andrerinas.openheadunit.input.SteeringLongPressPolicy.Effect
import com.andrerinas.openheadunit.input.SteeringLongPressPolicy.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins how a Voyah Voice or Phone press is classed. Short presses belong to Voyah Tweaks (Voice) and
 * the stock call screen (Phone), so the one thing this must never do is fire on a short press —
 * except Voice under VT, where only long presses ever arrive.
 */
class SteeringLongPressPolicyTest {

    private val idle = State()

    @Test
    fun `the two OEM key codes map to their buttons and nothing else does`() {
        assertEquals(SteeringButton.VOICE, SteeringLongPressPolicy.buttonFor(130))
        assertEquals(SteeringButton.PHONE, SteeringLongPressPolicy.buttonFor(128))
        for (other in listOf(3, 4, 6, 173, 3090, 0, -1)) assertNull(SteeringLongPressPolicy.buttonFor(other))
    }

    @Test
    fun `the service's own long press fires once, and the timer after it does nothing`() {
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.VOICE, 0, longOnFirstDown = false)
        val long = SteeringLongPressPolicy.onLongReported(down.state)
        assertEquals(Effect.Fire, long.effect)
        assertEquals(Effect.None, SteeringLongPressPolicy.onTimer(long.state).effect)
        assertEquals(Effect.None, SteeringLongPressPolicy.onLongReported(long.state).effect)
        assertEquals(Effect.None, SteeringLongPressPolicy.onUp(long.state).effect)
    }

    @Test
    fun `the service's long press after the timer fired does not fire again`() {
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.PHONE, 0, longOnFirstDown = false)
        val timer = SteeringLongPressPolicy.onTimer(down.state)
        assertEquals(Effect.Fire, timer.effect)
        assertEquals(Effect.None, SteeringLongPressPolicy.onLongReported(timer.state).effect)
    }

    @Test
    fun `when the service reports long presses no timer is armed, so a short click never also fires`() {
        for (button in SteeringButton.values()) {
            val down = SteeringLongPressPolicy.onDown(idle, button, 0, longOnFirstDown = false, serviceReportsLong = true)
            assertEquals(Effect.None, down.effect)
            // A short click's release cancels nothing that exists; what matters is that it never fires.
            assertNotEquals(Effect.Fire, SteeringLongPressPolicy.onUp(down.state).effect)
            assertEquals(Effect.Fire, SteeringLongPressPolicy.onLongReported(down.state).effect)
        }
    }

    @Test
    fun `the service's long press fires even when its DOWN never arrived`() {
        assertEquals(Effect.Fire, SteeringLongPressPolicy.onLongReported(idle).effect)
    }

    @Test
    fun `a press released before the timer is short and does nothing`() {
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.PHONE, 0, longOnFirstDown = false)
        assertEquals(Effect.ArmTimer(SteeringLongPressPolicy.LONG_PRESS_MS), down.effect)
        val up = SteeringLongPressPolicy.onUp(down.state)
        assertEquals(Effect.CancelTimer, up.effect)
        assertEquals(idle, up.state)
    }

    @Test
    fun `a held press fires once at the timer and the release then does nothing`() {
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.PHONE, 0, longOnFirstDown = false)
        val timer = SteeringLongPressPolicy.onTimer(down.state)
        assertEquals(Effect.Fire, timer.effect)
        assertEquals(Effect.None, SteeringLongPressPolicy.onTimer(timer.state).effect)
        val up = SteeringLongPressPolicy.onUp(timer.state)
        assertEquals(Effect.None, up.effect)
        assertEquals(idle, up.state)
    }

    @Test
    fun `key repeats while held neither restart the timer nor fire`() {
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.PHONE, 0, longOnFirstDown = false)
        val repeat = SteeringLongPressPolicy.onDown(down.state, SteeringButton.PHONE, 3, longOnFirstDown = false)
        assertEquals(Effect.None, repeat.effect)
        assertEquals(down.state, repeat.state)
    }

    @Test
    fun `a timer that expires after the release is ignored`() {
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.PHONE, 0, longOnFirstDown = false)
        val up = SteeringLongPressPolicy.onUp(down.state)
        assertEquals(Effect.None, SteeringLongPressPolicy.onTimer(up.state).effect)
    }

    @Test
    fun `a release with no press is ignored`() {
        assertEquals(Effect.None, SteeringLongPressPolicy.onUp(idle).effect)
    }

    @Test
    fun `a new press after a lost release starts a fresh hold`() {
        val first = SteeringLongPressPolicy.onDown(idle, SteeringButton.PHONE, 0, longOnFirstDown = false)
        val fired = SteeringLongPressPolicy.onTimer(first.state)
        // No UP arrives; the next press must still be able to fire.
        val second = SteeringLongPressPolicy.onDown(fired.state, SteeringButton.PHONE, 0, longOnFirstDown = false)
        assertEquals(Effect.ArmTimer(SteeringLongPressPolicy.LONG_PRESS_MS), second.effect)
        assertEquals(Effect.Fire, SteeringLongPressPolicy.onTimer(second.state).effect)
    }

    @Test
    fun `under Voyah Tweaks Voice fires on its first DOWN, however VT replays it`() {
        // VT may hold the DOWN and replay it with the UP straight after; measured, that reads short.
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.VOICE, 0, longOnFirstDown = true)
        assertEquals(Effect.Fire, down.effect)
        assertEquals(Effect.None, SteeringLongPressPolicy.onUp(down.state).effect)
    }

    @Test
    fun `under Voyah Tweaks Phone is still timed, since VT leaves it alone`() {
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.PHONE, 0, longOnFirstDown = true)
        assertEquals(Effect.ArmTimer(SteeringLongPressPolicy.LONG_PRESS_MS), down.effect)
    }

    @Test
    fun `without Voyah Tweaks Voice is timed like Phone`() {
        val down = SteeringLongPressPolicy.onDown(idle, SteeringButton.VOICE, 0, longOnFirstDown = false)
        assertEquals(Effect.ArmTimer(SteeringLongPressPolicy.LONG_PRESS_MS), down.effect)
        assertEquals(Effect.CancelTimer, SteeringLongPressPolicy.onUp(down.state).effect)
    }
}
