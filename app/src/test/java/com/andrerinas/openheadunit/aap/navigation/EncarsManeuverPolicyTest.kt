package com.andrerinas.openheadunit.aap.navigation

import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus.NextTurnDetail.NextEvent
import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus.NextTurnDetail.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tables here pin three things that came from evidence rather than from a specification, and
 * that a later reader would otherwise be tempted to "correct".
 *
 * First, every id this object can emit was seen in a capture. Three sniffed Yandex routes produced
 * LEFT, RIGHT, SLIGHT_RIGHT, ENTER_ROUNDABOUT, LEAVE_ROUNDABOUT and the empty string, and nothing
 * else. The VoyahTweaks author's EXIT_LEFT/EXIT_RIGHT appear in no capture, so a ramp deliberately
 * produces SLIGHT_*, and `a ramp does not use the unconfirmed EXIT ids` is the test that says so.
 *
 * Second, an unsided maneuver produces no arrow. There is no neutral turn icon in this vocabulary,
 * and drawing the wrong hand is worse than drawing nothing - the U-turn case is the one that would
 * otherwise invite a left-hand-traffic guess.
 *
 * Third, the distance is metres as a Double at every range. The captures never switch to
 * kilometres, including at 3839 m, and every value carries double precision.
 */
class EncarsManeuverPolicyTest {

    @Test
    fun `an ordinary turn becomes the sided arrow the cluster draws`() {
        assertEquals(
            EncarsManeuverPolicy.ICON_LEFT,
            EncarsManeuverPolicy.iconId(NextEvent.TURN, Side.LEFT)
        )
        assertEquals(
            EncarsManeuverPolicy.ICON_RIGHT,
            EncarsManeuverPolicy.iconId(NextEvent.TURN, Side.RIGHT)
        )
    }

    @Test
    fun `a sharp turn collapses onto the ordinary turn rather than inventing an id`() {
        assertEquals(
            EncarsManeuverPolicy.ICON_LEFT,
            EncarsManeuverPolicy.iconId(NextEvent.SHARP_TURN, Side.LEFT)
        )
        assertEquals(
            EncarsManeuverPolicy.ICON_RIGHT,
            EncarsManeuverPolicy.iconId(NextEvent.SHARP_TURN, Side.RIGHT)
        )
    }

    @Test
    fun `a slight turn is the one sided id the captures confirm beyond left and right`() {
        assertEquals(
            EncarsManeuverPolicy.ICON_SLIGHT_RIGHT,
            EncarsManeuverPolicy.iconId(NextEvent.SLIGHT_TURN, Side.RIGHT)
        )
        assertEquals(
            EncarsManeuverPolicy.ICON_SLIGHT_LEFT,
            EncarsManeuverPolicy.iconId(NextEvent.SLIGHT_TURN, Side.LEFT)
        )
    }

    @Test
    fun `a ramp does not use the unconfirmed EXIT ids`() {
        val ramps = listOf(NextEvent.ON_RAMP, NextEvent.OFFRAMP, NextEvent.FORK, NextEvent.MERGE)
        for (event in ramps) {
            val left = EncarsManeuverPolicy.iconId(event, Side.LEFT)
            val right = EncarsManeuverPolicy.iconId(event, Side.RIGHT)
            assertEquals("$event to the left", EncarsManeuverPolicy.ICON_SLIGHT_LEFT, left)
            assertEquals("$event to the right", EncarsManeuverPolicy.ICON_SLIGHT_RIGHT, right)
            assertTrue(
                "$event must not emit an id no capture contains",
                left != EncarsManeuverPolicy.ICON_EXIT_LEFT &&
                    right != EncarsManeuverPolicy.ICON_EXIT_RIGHT
            )
        }
    }

    @Test
    fun `a u-turn is sided both ways and never guessed`() {
        assertEquals(
            EncarsManeuverPolicy.ICON_UTURN_LEFT,
            EncarsManeuverPolicy.iconId(NextEvent.U_TURN, Side.LEFT)
        )
        assertEquals(
            EncarsManeuverPolicy.ICON_UTURN_RIGHT,
            EncarsManeuverPolicy.iconId(NextEvent.U_TURN, Side.RIGHT)
        )
        assertEquals(
            EncarsManeuverPolicy.ICON_NONE,
            EncarsManeuverPolicy.iconId(NextEvent.U_TURN, Side.UNSPECIFIED)
        )
    }

    @Test
    fun `every sided maneuver without a side draws nothing`() {
        val sided = listOf(
            NextEvent.TURN,
            NextEvent.SHARP_TURN,
            NextEvent.SLIGHT_TURN,
            NextEvent.U_TURN,
            NextEvent.ON_RAMP,
            NextEvent.OFFRAMP,
            NextEvent.FORK,
            NextEvent.MERGE
        )
        for (event in sided) {
            assertEquals(
                "$event with an unspecified side",
                EncarsManeuverPolicy.ICON_NONE,
                EncarsManeuverPolicy.iconId(event, Side.UNSPECIFIED)
            )
            assertEquals(
                "$event with no side at all",
                EncarsManeuverPolicy.ICON_NONE,
                EncarsManeuverPolicy.iconId(event, null)
            )
        }
    }

    @Test
    fun `a roundabout keeps the half of the maneuver still ahead of the driver`() {
        assertEquals(
            EncarsManeuverPolicy.ICON_ENTER_ROUNDABOUT,
            EncarsManeuverPolicy.iconId(NextEvent.ROUNDABOUT_ENTER, Side.UNSPECIFIED)
        )
        assertEquals(
            EncarsManeuverPolicy.ICON_ENTER_ROUNDABOUT,
            EncarsManeuverPolicy.iconId(NextEvent.ROUNDABOUT_ENTER_AND_EXIT, Side.UNSPECIFIED)
        )
        assertEquals(
            EncarsManeuverPolicy.ICON_LEAVE_ROUNDABOUT,
            EncarsManeuverPolicy.iconId(NextEvent.ROUNDABOUT_EXIT, Side.UNSPECIFIED)
        )
    }

    @Test
    fun `the destination leg draws no arrow, as the captures show`() {
        assertEquals(
            EncarsManeuverPolicy.ICON_NONE,
            EncarsManeuverPolicy.iconId(NextEvent.DESTINATION, Side.UNSPECIFIED)
        )
    }

    @Test
    fun `maneuvers this vocabulary cannot express draw nothing`() {
        val inexpressible = listOf(
            NextEvent.UNKNOWN,
            NextEvent.DEPART,
            NextEvent.NAME_CHANGE,
            NextEvent.STRAIGHT,
            NextEvent.FERRY_BOAT,
            NextEvent.FERRY_TRAIN
        )
        for (event in inexpressible) {
            assertEquals(
                "$event",
                EncarsManeuverPolicy.ICON_NONE,
                EncarsManeuverPolicy.iconId(event, Side.RIGHT)
            )
        }
    }

    @Test
    fun `a missing maneuver draws nothing`() {
        assertEquals(
            EncarsManeuverPolicy.ICON_NONE,
            EncarsManeuverPolicy.iconId(null, Side.LEFT)
        )
    }

    @Test
    fun `the wire numbers reach the same decision as the enums`() {
        assertEquals(
            EncarsManeuverPolicy.ICON_RIGHT,
            EncarsManeuverPolicy.iconIdFromWire(NextEvent.TURN.number, Side.RIGHT.number)
        )
        assertEquals(
            EncarsManeuverPolicy.ICON_ENTER_ROUNDABOUT,
            EncarsManeuverPolicy.iconIdFromWire(
                NextEvent.ROUNDABOUT_ENTER.number,
                Side.UNSPECIFIED.number
            )
        )
    }

    @Test
    fun `a wire number outside the protocol draws nothing instead of failing`() {
        assertEquals(EncarsManeuverPolicy.ICON_NONE, EncarsManeuverPolicy.iconIdFromWire(999, 999))
        assertEquals(EncarsManeuverPolicy.ICON_NONE, EncarsManeuverPolicy.iconIdFromWire(-1, -1))
    }

    @Test
    fun `distance is metres, unconverted, at every range the captures cover`() {
        assertEquals(0.0, EncarsManeuverPolicy.distance(0), 0.0)
        assertEquals(830.0, EncarsManeuverPolicy.distance(830), 0.0)
        // The longest distance in the captures, still sent as metres rather than as 3.8 km.
        assertEquals(3839.0, EncarsManeuverPolicy.distance(3839), 0.0)
        assertEquals(EncarsManeuverPolicy.UNIT_METERS, "m")
    }

    @Test
    fun `an absent or negative distance becomes the zero the first broadcast carries`() {
        assertEquals(0.0, EncarsManeuverPolicy.distance(null), 0.0)
        assertEquals(0.0, EncarsManeuverPolicy.distance(-1), 0.0)
    }

    @Test
    fun `the road placeholder is undone rather than sent as a road name`() {
        assertEquals("", EncarsManeuverPolicy.nextRoad("—"))
        assertEquals("", EncarsManeuverPolicy.nextRoad(""))
        assertEquals("", EncarsManeuverPolicy.nextRoad("   "))
        assertEquals("", EncarsManeuverPolicy.nextRoad(null))
        assertEquals("Butlerova St", EncarsManeuverPolicy.nextRoad("Butlerova St"))
        assertEquals("Butlerova St", EncarsManeuverPolicy.nextRoad("  Butlerova St  "))
    }
}
