package com.andrerinas.openheadunit.location

/**
 * What goes in the `accuracy` field of a LOCATION sensor event.
 *
 * The field is proto2 `required` and carries millimetres, so every fix has to produce a number and
 * the number has to mean something. Three head-unit GPS chips in a row have been seen reporting
 * `hasAccuracy() == true` with `accuracy == 0.0f` - on one field log, 883 of 1066 fixes in a single
 * drive. Zero-millimetre accuracy is not a claim any receiver can act on: it is not merely optimistic,
 * it is impossible, and a consumer fusion engine that sanity-checks its inputs is entitled to throw
 * the whole fix away. Android Auto preferring the phone's own position while the head unit was
 * sending steadily is what that looks like from the driver's seat.
 *
 * So "absent" and "present but not a real measurement" are collapsed into the same answer here: a
 * declared-coarse fix, which Android Auto can weigh honestly, rather than a perfect one it cannot
 * believe. [UNKNOWN_ACCURACY_MM] is deliberately pessimistic - inventing a tighter figure to win the
 * comparison against the phone would be fabricating a measurement the chip never made.
 */
object LocationAccuracyPolicy {

    /** Stand-in for a fix that reports no usable accuracy: 100 m, in the field's millimetres. */
    const val UNKNOWN_ACCURACY_MM = 100_000

    /**
     * Stand-in for a fix whose chip claims an accuracy but reports zero or less: 1 m. Only such
     * impossible readings are lifted to it - a genuine sub-metre measurement is carried through
     * unchanged, since rounding it up would discard precision the chip really has.
     */
    const val MIN_ACCURACY_MM = 1_000

    /**
     * The `accuracy` value in millimetres for a fix.
     *
     * @param hasAccuracy what `Location.hasAccuracy()` says.
     * @param accuracyMeters `Location.getAccuracy()`, meaningful only when [hasAccuracy].
     * @return the measured accuracy in millimetres; [MIN_ACCURACY_MM] when the measurement comes
     *   out as zero or negative; or [UNKNOWN_ACCURACY_MM] when the chip did not measure one at all.
     */
    fun accuracyMm(hasAccuracy: Boolean, accuracyMeters: Float): Int {
        if (!hasAccuracy || !accuracyMeters.isFinite()) return UNKNOWN_ACCURACY_MM
        val accuracyMillimeters = (accuracyMeters * 1E3).toInt()
        return if (accuracyMillimeters <= 0) MIN_ACCURACY_MM else accuracyMillimeters
    }
}
