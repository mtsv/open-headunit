package com.andrerinas.openheadunit.aap.navigation

import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus.NavigationClusterStatus.NavigationStatusEnum
import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus.NextTurnDetail.NextEvent
import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus.NextTurnDetail.Side

/**
 * Turns an Android Auto maneuver into the `ru.encars.UPDATE_MANEUVER` payload that head unit mods
 * consume to draw an arrow on the car's instrument cluster.
 *
 * ## Why this exists at all
 *
 * A head unit that is not an Android Auto receiver has no way to reach its own dashboard: the
 * cluster is driven over the vehicle bus by an OEM or community service, never by an ordinary app.
 * On the Voyah/Pateo units the community service is VoyahTweaks, which listens for
 * [ACTION] and renders whatever arrives. Yandex Maps reaches it, but only because VoyahTweaks
 * injects a Frida hook into Yandex to make it broadcast; Yandex has no such feature of its own.
 *
 * Open Headunit needs no hook. It already parses the whole navigation channel from the phone, so it
 * can send the broadcast directly - which is the entire integration.
 *
 * ## Where the vocabulary comes from
 *
 * Two sources, and they disagree, so both are recorded here.
 *
 * The VoyahTweaks author lists: `EXIT_RIGHT`, `EXIT_LEFT`, `RIGHT`, `LEFT`, `UTURN_LEFT`,
 * `UTURN_RIGHT`, `ENTER_ROUNDABOUT`, `LEAVE_ROUNDABOUT`.
 *
 * Three captured routes (1514 broadcasts, sniffed off Yandex with Frida) contain: `LEFT` (899),
 * `RIGHT` (445), `ENTER_ROUNDABOUT` (71), `SLIGHT_RIGHT` (64), `LEAVE_ROUNDABOUT` (14), and the
 * empty string (21). `SLIGHT_RIGHT` is in no list the author gave, and neither `EXIT_` value was
 * ever observed.
 *
 * So the captures are treated as authoritative and the author's list as a hint: a ramp or fork maps
 * to [ICON_SLIGHT_LEFT]/[ICON_SLIGHT_RIGHT], which are proven to render, rather than to
 * [ICON_EXIT_LEFT]/[ICON_EXIT_RIGHT], which are not. An unrecognised id most likely draws nothing,
 * and a maneuver drawn as the wrong arrow is worse than one drawn as no arrow - so everything this
 * object cannot map confidently becomes [ICON_NONE] instead of a guess.
 *
 * Should someone confirm the `EXIT_` ids on a real cluster, [rampIcon] is the single place to
 * change.
 *
 * ## Units
 *
 * `unit` was `m` in every one of the 1514 captured samples, including at 3839 m - Yandex never
 * switched to kilometres inside the range covered. So [UNIT_METERS] is sent unconditionally and the
 * distance goes out in raw metres, which is exactly what `NextTurnDistanceEvent.distance_meters`
 * already carries. No conversion, and no rounding that a cluster would have to undo.
 */
object EncarsManeuverPolicy {

    /** The broadcast every consumer of this protocol listens for. */
    const val ACTION = "ru.encars.UPDATE_MANEUVER"

    /**
     * VoyahTweaks, the only consumer seen in the wild. Sent with `setPackage`, as Yandex does:
     * the receiver is registered at runtime, so an implicit broadcast would reach it too, but
     * targeting keeps a maneuver stream off every other app on the unit.
     *
     * The package must also appear in the manifest's `<queries>` block. Android 11 - which is what
     * these units run - filters package visibility, and an invisible target fails silently.
     */
    const val DEFAULT_TARGET_PACKAGE = "ru.kachalin.voyahtweaks"

    const val EXTRA_ICON_ID = "iconId"
    const val EXTRA_NEXT_ROAD = "nextRoad"
    const val EXTRA_DISTANCE = "distance"
    const val EXTRA_UNIT = "unit"

    /** The only unit ever observed, at every distance. See the class KDoc. */
    const val UNIT_METERS = "m"

    /**
     * How long an unchanged maneuver may go un-repeated before the cluster drops it.
     *
     * This protocol has no "still valid" message: the consumer keeps the arrow alive only while
     * broadcasts keep arriving, and expires it when they stop. Yandex never trips over that because
     * it broadcasts unconditionally about once a second for the whole route - the captures show it
     * re-sending the same maneuver with only GPS jitter between samples.
     *
     * The first version of this sink dropped identical payloads, on the reasoning that a cluster
     * gains nothing from being told the same thing twice. That is true of the cluster and false of
     * the protocol: standing still in traffic freezes the distance, identical payloads stop being
     * sent, and the arrow disappears from the dashboard after a while - reported from a real car,
     * and the reason this constant exists.
     *
     * One second is chosen because it is the cadence Yandex is observed to use and therefore the
     * only one proven to hold the arrow. The consumer's actual timeout is unknown, so this is the
     * safe side of an unmeasured deadline rather than a tuned value: raising it trades dashboard
     * reliability for a saving of a few broadcasts a minute, which is the wrong way round.
     */
    const val REFRESH_INTERVAL_MS = 1_000L

    /**
     * Whether a maneuver should go out now: because it differs from the last one, or because the
     * last one is old enough that the cluster may be about to forget it.
     *
     * [msSinceLastSend] is measured on a monotonic clock. A negative value - which a caller can
     * produce before anything has been sent - counts as overdue rather than as recent, so the first
     * maneuver of a route is never held back.
     */
    fun shouldSend(payloadChanged: Boolean, msSinceLastSend: Long): Boolean =
        payloadChanged || msSinceLastSend >= REFRESH_INTERVAL_MS || msSinceLastSend < 0L

    /**
     * Whether a payload is worth holding on the dashboard - that is, whether the refresh in
     * [REFRESH_INTERVAL_MS] should keep going.
     *
     * A maneuver with no arrow, no road and no distance left says nothing, and repeating it once a
     * second pins "0 m" to the cluster for the rest of the drive. That is what a finished route
     * decays into, and it was reported from a real car: the route ended and the dashboard kept
     * showing zero.
     *
     * Letting the refresh lapse is what clears it. The consumer expires a maneuver it stops hearing
     * about - the same timeout that made [REFRESH_INTERVAL_MS] necessary - so going quiet is the
     * protocol's way of saying "nothing now", and there is no retraction message to send instead.
     *
     * An empty arrow on its own is not nothing: the captured destination leg carries an empty
     * `iconId` with a real distance counting down, and that has to keep refreshing like any other.
     */
    fun holdsCluster(iconId: String, nextRoad: String, distance: Double): Boolean =
        iconId.isNotEmpty() || nextRoad.isNotEmpty() || distance > 0.0

    /**
     * Whether a cluster status means the route is over and the arrow should go.
     *
     * [NavigationStatusEnum.REROUTING] deliberately holds: the maneuver on screen is stale for a
     * moment, but the drive is still happening and blanking the dashboard every time the phone
     * recalculates would be worse than a second of an old arrow.
     *
     * This is the *only* end-of-route signal some setups produce. `INSTRUMENT_CLUSTER_STOP` looks
     * like the obvious hook and is not: on the head unit this was developed against it never
     * arrives at all - neither it nor `INSTRUMENT_CLUSTER_START` appears once in a captured
     * session - while the status message goes ACTIVE and INACTIVE exactly as the route starts and
     * ends. Anything that must happen when a route finishes belongs here, not there.
     */
    fun clearsCluster(status: NavigationStatusEnum?): Boolean = when (status) {
        NavigationStatusEnum.ACTIVE, NavigationStatusEnum.REROUTING -> false
        else -> true
    }

    /**
     * No arrow. Sent for a maneuver that cannot be drawn, and for the run-in to the destination:
     * in the captures the final leg carries an empty `iconId` and an empty `nextRoad` together,
     * counting the distance down to zero.
     */
    const val ICON_NONE = ""

    const val ICON_LEFT = "LEFT"
    const val ICON_RIGHT = "RIGHT"
    const val ICON_SLIGHT_LEFT = "SLIGHT_LEFT"
    const val ICON_SLIGHT_RIGHT = "SLIGHT_RIGHT"
    const val ICON_UTURN_LEFT = "UTURN_LEFT"
    const val ICON_UTURN_RIGHT = "UTURN_RIGHT"
    const val ICON_ENTER_ROUNDABOUT = "ENTER_ROUNDABOUT"
    const val ICON_LEAVE_ROUNDABOUT = "LEAVE_ROUNDABOUT"

    /**
     * Reported by the VoyahTweaks author for "smooth turn (road exit)", never seen in a capture.
     * Not emitted; see the class KDoc and [rampIcon].
     */
    const val ICON_EXIT_LEFT = "EXIT_LEFT"
    const val ICON_EXIT_RIGHT = "EXIT_RIGHT"

    /**
     * The icon for [nextEvent] taken to [side].
     *
     * [side] is what decides whether a sided maneuver can be drawn at all. Android Auto sends
     * `UNSPECIFIED` more often than one would like, and there is no neutral turn arrow in this
     * vocabulary, so an unsided turn produces [ICON_NONE] rather than defaulting to one hand. That
     * includes U-turns: guessing left would be right in left-hand-drive countries and wrong in the
     * rest, and this protocol is in use in both.
     */
    fun iconId(nextEvent: NextEvent?, side: Side?): String = when (nextEvent) {
        // Nothing to draw, or nothing this vocabulary can express. DESTINATION lands here
        // deliberately: the captures show the destination leg carrying an empty id.
        null,
        NextEvent.UNKNOWN,
        NextEvent.DEPART,
        NextEvent.NAME_CHANGE,
        NextEvent.STRAIGHT,
        NextEvent.FERRY_BOAT,
        NextEvent.FERRY_TRAIN,
        NextEvent.DESTINATION -> ICON_NONE

        // SHARP_TURN collapses onto the ordinary turn: no sharp arrow was ever observed, and a
        // sharp turn drawn as a turn is still the correct direction.
        NextEvent.TURN,
        NextEvent.SHARP_TURN -> sided(side, ICON_LEFT, ICON_RIGHT)

        NextEvent.SLIGHT_TURN -> sided(side, ICON_SLIGHT_LEFT, ICON_SLIGHT_RIGHT)

        NextEvent.ON_RAMP,
        NextEvent.OFFRAMP,
        NextEvent.FORK,
        NextEvent.MERGE -> rampIcon(side)

        NextEvent.U_TURN -> sided(side, ICON_UTURN_LEFT, ICON_UTURN_RIGHT)

        // Entering is the useful half of a combined enter-and-exit: it is the one that is still
        // ahead of the driver when the message arrives.
        NextEvent.ROUNDABOUT_ENTER,
        NextEvent.ROUNDABOUT_ENTER_AND_EXIT -> ICON_ENTER_ROUNDABOUT

        NextEvent.ROUNDABOUT_EXIT -> ICON_LEAVE_ROUNDABOUT

        // Defensive: the generated enum can gain values, and forNumber hands back anything the
        // phone sends. An unknown maneuver is not worth a wrong arrow.
        else -> ICON_NONE
    }

    /**
     * The same decision from the wire numbers [NavigationUpdateIntent][com.andrerinas.openheadunit.contract.NavigationUpdateIntent]
     * already carries, so a caller that has normalised both message families does not have to hold
     * on to the protobuf enums as well.
     *
     * Unknown numbers resolve to `null` and therefore to [ICON_NONE].
     */
    fun iconIdFromWire(nextEventType: Int, turnSide: Int): String =
        iconId(NextEvent.forNumber(nextEventType), Side.forNumber(turnSide))

    /**
     * A ramp, fork or merge.
     *
     * Kept apart from [iconId]'s table because this is the one mapping the two sources disagree
     * about, and it is the single line to change if `EXIT_LEFT`/`EXIT_RIGHT` are ever confirmed to
     * render. See the class KDoc.
     */
    fun rampIcon(side: Side?): String = sided(side, ICON_SLIGHT_LEFT, ICON_SLIGHT_RIGHT)

    /**
     * The distance the broadcast carries.
     *
     * A `Double`, not a `Float`, although the author described the field as a float: every captured
     * value has full double precision (`100.20007581718811`), which a `Float` could not have
     * printed - it would have rendered as `100.20008`. Matching the observed wire matters more than
     * matching the description, because a consumer reading the extra as the other type gets its
     * default value instead and shows a distance of zero.
     *
     * A missing or negative distance becomes `0.0`, which is what the first broadcast of every
     * captured route carries before the phone has computed one.
     */
    fun distance(distanceMeters: Int?): Double =
        distanceMeters?.takeIf { it >= 0 }?.toDouble() ?: 0.0

    /**
     * The road the next maneuver leads onto: [turnRoad] from the turn itself, or [firstStepRoad]
     * from the route's first step, which describes the same maneuver from the other message family.
     *
     * Both sources belong to the maneuver being announced, and that is the whole point. The
     * snapshot also carries an accumulated "current street", and reading it here is the bug this
     * signature exists to prevent: it is only overwritten when a road arrives with a name, so a
     * turn onto an unnamed street leaves the *previous* street sitting in it, and the cluster keeps
     * announcing a road the driver left two turns ago. Reported from a real car.
     *
     * That stickiness is right where it comes from - a notification reading "Street: —" is worse
     * than one a beat out of date - and wrong here, because this field names the road ahead. An
     * unnamed street has no name, and the captures show an empty `nextRoad` is ordinary: the whole
     * destination leg carries one.
     *
     * Open Headunit's own `—` placeholder is undone for the same reason, rather than forwarded as
     * a road called "—".
     */
    fun nextRoad(turnRoad: String?, firstStepRoad: String? = null): String =
        named(turnRoad) ?: named(firstStepRoad) ?: ""

    private fun named(road: String?): String? =
        road?.trim()?.takeIf { it.isNotEmpty() && it != PLACEHOLDER_ROAD }

    private const val PLACEHOLDER_ROAD = "—"

    private fun sided(side: Side?, left: String, right: String): String = when (side) {
        Side.LEFT -> left
        Side.RIGHT -> right
        else -> ICON_NONE
    }
}
