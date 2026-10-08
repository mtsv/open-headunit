package com.andrerinas.openheadunit.location

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The zero case is the regression this table exists for. A field log from a drive carried
 * `hAcc=0` on 883 of 1066 fixes, and the old code sent every one of them as `accuracy = 0` mm,
 * because the only guard was `hasAccuracy()` and the chip answered true. Android Auto kept using
 * the phone's position while the head unit was sending the whole time.
 *
 * The NaN row is the same bug wearing a different hat: it passes `<= 0f` (every comparison with NaN
 * is false) and then becomes 0 on the way to Int, so a guard written only as a sign test puts the
 * impossible value straight back on the wire.
 */
class LocationAccuracyPolicyTest {

    private val unknown = LocationAccuracyPolicy.UNKNOWN_ACCURACY_MM

    private val min_accuracy = LocationAccuracyPolicy.MIN_ACCURACY_MM

    @Test
    fun `a real measurement is carried through in millimetres`() {
        assertEquals(5_000, LocationAccuracyPolicy.accuracyMm(true, 5f))
        assertEquals(51_000, LocationAccuracyPolicy.accuracyMm(true, 51f))
    }

    @Test
    fun `a sub-metre measurement keeps its precision rather than rounding to nothing`() {
        assertEquals(500, LocationAccuracyPolicy.accuracyMm(true, 0.5f))
        assertEquals(1, LocationAccuracyPolicy.accuracyMm(true, 0.001f))
    }

    @Test
    fun `a fix that reports no accuracy at all is declared coarse`() {
        assertEquals(unknown, LocationAccuracyPolicy.accuracyMm(false, 0f))
        // The value is meaningless when hasAccuracy() is false, so it must not be read either way.
        assertEquals(unknown, LocationAccuracyPolicy.accuracyMm(false, 7f))
    }

    @Test
    fun `zero is treated as no measurement, not as a perfect one`() {
        assertEquals(min_accuracy, LocationAccuracyPolicy.accuracyMm(true, 0f))
    }

    @Test
    fun `a negative accuracy is treated as no measurement`() {
        assertEquals(min_accuracy, LocationAccuracyPolicy.accuracyMm(true, -1f))
    }

    @Test
    fun `NaN is treated as no measurement rather than becoming zero`() {
        assertEquals(unknown, LocationAccuracyPolicy.accuracyMm(true, Float.NaN))
    }

    @Test
    fun `an infinite accuracy is treated as no measurement rather than saturating`() {
        assertEquals(unknown, LocationAccuracyPolicy.accuracyMm(true, Float.POSITIVE_INFINITY))
        assertEquals(unknown, LocationAccuracyPolicy.accuracyMm(true, Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `nothing this policy returns is ever zero or negative`() {
        // The one invariant the wire cares about: the receiver must never be handed an accuracy it
        // cannot act on, whatever the chip reported.
        val inputs = listOf(
            0f, -1f, -0.001f, 0.001f, 0.5f, 5f, 51f,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
        )
        for (has in listOf(true, false)) {
            for (value in inputs) {
                val mm = LocationAccuracyPolicy.accuracyMm(has, value)
                assert(mm > 0) { "accuracyMm($has, $value) returned $mm" }
            }
        }
    }
}
