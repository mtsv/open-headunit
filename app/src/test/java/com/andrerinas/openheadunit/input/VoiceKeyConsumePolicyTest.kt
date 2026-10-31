package com.andrerinas.openheadunit.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stock "voice commands not implemented" text must never appear (user requirement,
 * 2026-10-31), Voyah Tweaks' short Voice press must keep working, and the stock Phone short press
 * must keep opening the call screen. Build 12 consumed every Voice callback and silenced VT; the
 * first two tests pin the fix.
 */
class VoiceKeyConsumePolicyTest {

    private fun consume(code: Int, longPhase: Boolean, vt: Boolean, featureOn: Boolean = true, voiceLong: Boolean = true) =
        VoiceKeyConsumePolicy.consume(code, longPhase, featureOn, voiceLong, vt)

    @Test
    fun `with Voyah Tweaks a short Voice press passes through to VT`() {
        assertFalse(consume(130, longPhase = false, vt = true))
    }

    @Test
    fun `with Voyah Tweaks the long-press callbacks of Voice are swallowed`() {
        assertTrue(consume(130, longPhase = true, vt = true))
    }

    @Test
    fun `without Voyah Tweaks every Voice callback is swallowed`() {
        assertTrue(consume(130, longPhase = false, vt = false))
        assertTrue(consume(130, longPhase = true, vt = false))
    }

    @Test
    fun `Phone is never swallowed, so its short press still opens the stock call screen`() {
        for (vt in listOf(true, false)) for (long in listOf(true, false)) {
            assertFalse(consume(128, longPhase = long, vt = vt))
        }
    }

    @Test
    fun `no other key is swallowed`() {
        for (code in listOf(0, 3, 4, 6, 173, 3090, 1, 2)) {
            assertFalse(consume(code, longPhase = true, vt = false))
        }
    }

    @Test
    fun `nothing is swallowed while the feature or the Voice action is off`() {
        assertFalse(consume(130, longPhase = true, vt = false, featureOn = false))
        assertFalse(consume(130, longPhase = true, vt = false, voiceLong = false))
    }
}
