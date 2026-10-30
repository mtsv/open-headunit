package com.andrerinas.openheadunit.app

import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two rules: what the service starts with, and what it adds while capturing.
 *
 * The one that matters is the first. The microphone type is while-in-use, so a background start
 * that claims it is refused even with the permission granted, and this service starts from boot,
 * USB and Bluetooth receivers where a refusal means no session at all.
 */
class ForegroundServiceTypePolicyTest {

    private val u = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    private val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK

    private val mic = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

    // --- what the service starts with ---

    @Test
    fun `the start never claims the microphone type, whatever the permission and the setting say`() {
        assertEquals(base, ForegroundServiceTypePolicy.baseTypeMask(u))
    }

    @Test
    fun `before Q the start takes no types at all`() {
        assertEquals(0, ForegroundServiceTypePolicy.baseTypeMask(Build.VERSION_CODES.P))
    }

    // --- what it adds while capturing ---

    @Test
    fun `capture claims the microphone type with the permission and the setting`() {
        assertEquals(base or mic, ForegroundServiceTypePolicy.withMicrophone(u, true, true))
    }

    @Test
    fun `a denied permission never claims it`() {
        assertEquals(base, ForegroundServiceTypePolicy.withMicrophone(u, false, true))
    }

    @Test
    fun `a head unit that hands the microphone to the phone never claims it`() {
        assertEquals(base, ForegroundServiceTypePolicy.withMicrophone(u, true, false))
    }

    @Test
    fun `neither claims it`() {
        assertEquals(base, ForegroundServiceTypePolicy.withMicrophone(u, false, false))
    }

    @Test
    fun `before Q capture takes no types either`() {
        assertEquals(0, ForegroundServiceTypePolicy.withMicrophone(
            Build.VERSION_CODES.P, true, true))
    }

    // --- the relationship between the two ---

    @Test
    fun `capture only ever adds to the start mask, never removes from it`() {
        for (granted in listOf(true, false)) {
            for (enabled in listOf(true, false)) {
                val withMic = ForegroundServiceTypePolicy.withMicrophone(u, granted, enabled)
                assertEquals(
                    "withMicrophone($granted, $enabled) dropped a type the start had",
                    base, withMic and base)
            }
        }
    }
}

/**
 * Location is while-in-use like the microphone, so the rules that matter are the same two: it must
 * never appear in the start mask (a background start that claims it is refused, and onCreate answers
 * a refusal by stopping the service), and claiming either type must compose the full set rather than
 * replace it.
 *
 * The composition rule is the one with teeth. startForeground takes the mask wholesale, so a
 * microphone demote that named only the base types would drop a held location type - and the head
 * unit's GPS would go silent the next time the projection left the screen, which is the exact bug
 * this type was added to fix.
 */
class ForegroundServiceTypeLocationTest {

    private val u = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    private val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK

    private val mic = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    private val loc = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

    @Test
    fun `the start never claims the location type`() {
        // The whole reason it is claimed later: a background start that names it is refused.
        assertEquals(0, ForegroundServiceTypePolicy.baseTypeMask(u) and loc)
    }

    @Test
    fun `location is claimed with the permission and the setting`() {
        assertEquals(base or loc, ForegroundServiceTypePolicy.withLocation(u, true, true))
    }

    @Test
    fun `a denied location permission never claims it`() {
        assertEquals(base, ForegroundServiceTypePolicy.withLocation(u, false, true))
    }

    @Test
    fun `GPS for navigation turned off never claims it`() {
        // Nothing is being sent to the phone, so there is nothing for the type to keep alive.
        assertEquals(base, ForegroundServiceTypePolicy.withLocation(u, true, false))
    }

    @Test
    fun `before Q location takes no types either`() {
        assertEquals(0, ForegroundServiceTypePolicy.withLocation(
            Build.VERSION_CODES.P, true, true))
    }

    // --- the two while-in-use types coexist ---

    @Test
    fun `claiming location keeps a microphone type that was already held`() {
        assertEquals(base or mic or loc,
            ForegroundServiceTypePolicy.withLocation(u, true, true, microphoneHeld = true))
    }

    @Test
    fun `claiming the microphone keeps a location type that was already held`() {
        assertEquals(base or mic or loc,
            ForegroundServiceTypePolicy.withMicrophone(u, true, true, locationHeld = true))
    }

    @Test
    fun `dropping the microphone keeps a held location type`() {
        // What demoteAfterMicrophone composes. Using baseTypeMask here instead is the regression.
        assertEquals(base or loc,
            ForegroundServiceTypePolicy.whileInUseMask(u, microphone = false, location = true))
    }

    @Test
    fun `dropping location keeps a held microphone type`() {
        assertEquals(base or mic,
            ForegroundServiceTypePolicy.whileInUseMask(u, microphone = true, location = false))
    }

    @Test
    fun `the existing three-argument microphone call is unchanged by the new parameter`() {
        // Every pre-existing call site omits locationHeld; none of them may start claiming it.
        for (granted in listOf(true, false)) {
            for (enabled in listOf(true, false)) {
                val mask = ForegroundServiceTypePolicy.withMicrophone(u, granted, enabled)
                assertEquals("withMicrophone($granted, $enabled) claimed location", 0, mask and loc)
            }
        }
    }

    @Test
    fun `every composition still carries the base types`() {
        for (micHeld in listOf(true, false)) {
            for (locHeld in listOf(true, false)) {
                val mask = ForegroundServiceTypePolicy.whileInUseMask(u, micHeld, locHeld)
                assertEquals(
                    "whileInUseMask($micHeld, $locHeld) dropped a base type", base, mask and base)
            }
        }
    }
}
