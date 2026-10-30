package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The constants are a wire contract with Android Auto, not this project's own numbering, so the
 * table that matters is the one pinning their values. They came out of Android Auto's own binary
 * and match the published `android.car.hardware.property.LocationCharacterization` constants; a
 * well-meaning tidy-up into a sequential enum would silently announce a different car.
 *
 * The announced value is the other half: it must stay an honest description of a device reading
 * GPS_PROVIDER and nothing else, because every richer flag is a claim about hardware a tablet in a
 * car does not have - and, as three drives established, buys nothing in return.
 */
class LocationCharacterizationPolicyTest {

    // --- the wire contract ---

    @Test
    fun `every flag carries the value Android Auto defines for it`() {
        assertEquals(1, LocationCharacterizationPolicy.PRIOR_LOCATIONS)
        assertEquals(2, LocationCharacterizationPolicy.GYROSCOPE_FUSION)
        assertEquals(4, LocationCharacterizationPolicy.ACCELEROMETER_FUSION)
        assertEquals(8, LocationCharacterizationPolicy.COMPASS_FUSION)
        assertEquals(16, LocationCharacterizationPolicy.WHEEL_SPEED_FUSION)
        assertEquals(32, LocationCharacterizationPolicy.STEERING_ANGLE_FUSION)
        assertEquals(64, LocationCharacterizationPolicy.CAR_SPEED_FUSION)
        assertEquals(128, LocationCharacterizationPolicy.DEAD_RECKONED)
        assertEquals(256, LocationCharacterizationPolicy.RAW_GPS_ONLY)
    }

    @Test
    fun `the flags are a bitmask, so each one is a distinct single bit`() {
        val flags = listOf(
            LocationCharacterizationPolicy.PRIOR_LOCATIONS,
            LocationCharacterizationPolicy.GYROSCOPE_FUSION,
            LocationCharacterizationPolicy.ACCELEROMETER_FUSION,
            LocationCharacterizationPolicy.COMPASS_FUSION,
            LocationCharacterizationPolicy.WHEEL_SPEED_FUSION,
            LocationCharacterizationPolicy.STEERING_ANGLE_FUSION,
            LocationCharacterizationPolicy.CAR_SPEED_FUSION,
            LocationCharacterizationPolicy.DEAD_RECKONED,
            LocationCharacterizationPolicy.RAW_GPS_ONLY,
        )
        for (flag in flags) {
            assertTrue("$flag is not a single bit", flag > 0 && flag and (flag - 1) == 0)
        }
        assertEquals("two flags share a bit", flags.size, flags.toSet().size)
    }

    // --- what this head unit claims ---

    @Test
    fun `the honest description of this head unit is raw satellites and nothing more`() {
        // What HEAD_UNIT must always mean, whatever ANNOUNCED is set to this week: a tablet reading
        // GPS_PROVIDER has no CAN bus and fuses nothing into the fix.
        assertEquals(
            LocationCharacterizationPolicy.RAW_GPS_ONLY,
            LocationCharacterizationPolicy.HEAD_UNIT
        )
        val unclaimable = LocationCharacterizationPolicy.PRIOR_LOCATIONS or
            LocationCharacterizationPolicy.GYROSCOPE_FUSION or
            LocationCharacterizationPolicy.ACCELEROMETER_FUSION or
            LocationCharacterizationPolicy.COMPASS_FUSION or
            LocationCharacterizationPolicy.WHEEL_SPEED_FUSION or
            LocationCharacterizationPolicy.STEERING_ANGLE_FUSION or
            LocationCharacterizationPolicy.CAR_SPEED_FUSION or
            LocationCharacterizationPolicy.DEAD_RECKONED
        assertEquals(0, LocationCharacterizationPolicy.HEAD_UNIT and unclaimable)
    }

    // --- when it is announced ---

    @Test
    fun `what is announced is what this head unit actually is`() {
        // A diagnostic once pointed ANNOUNCED at every fusion flag, to find out whether Android
        // Auto weighs this field at all. It does not - three drives polled the phone's own GPS at
        // the same rate whatever was declared, see the ANNOUNCED KDoc - so the experiment is over
        // and there is no reason left to announce anything but the truth. This test is what stops
        // it being reopened by someone who has not read that table.
        assertEquals(
            LocationCharacterizationPolicy.HEAD_UNIT,
            LocationCharacterizationPolicy.ANNOUNCED
        )
    }

    @Test
    fun `GPS for navigation on announces the current value`() {
        assertEquals(
            LocationCharacterizationPolicy.ANNOUNCED,
            LocationCharacterizationPolicy.announced(gpsForNavigationEnabled = true)
        )
    }

    @Test
    fun `GPS for navigation off announces nothing at all`() {
        // Null rather than 0: the LOCATION sensor is not announced either, so the field is left
        // unset instead of describing a source that will never send a fix.
        assertNull(LocationCharacterizationPolicy.announced(gpsForNavigationEnabled = false))
    }

    @Test
    fun `the announced value is never zero, which would declare nothing`() {
        // Zero is what the head unit sent before this policy existed, and what the phone logged
        // back while it navigated from its own position instead.
        assertTrue(LocationCharacterizationPolicy.announced(true)!! > 0)
    }
}
