package com.andrerinas.openheadunit.aap.navigation

import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Sends the maneuver the phone just reported to a head unit mod that can draw it on the
 * instrument cluster. [EncarsManeuverPolicy] holds every decision and the evidence behind it;
 * this is only the Android call.
 *
 * Unlike [com.andrerinas.openheadunit.contract.NavigationUpdateIntent] this broadcast carries no
 * permission. It cannot: the consumer is a third-party app that has never heard of Open Headunit
 * and holds nothing this app declares, which is exactly how Yandex reaches the same receiver.
 * Targeting the package instead of broadcasting implicitly is what keeps it from being readable by
 * everything on the unit.
 */
class EncarsNavigationSink(
    private val context: Context,
    private val targetPackage: String = EncarsManeuverPolicy.DEFAULT_TARGET_PACKAGE
) {

    /** The last payload sent, so an unchanged repeat is not re-broadcast. */
    private var lastSent: Triple<String, String, Double>? = null

    /**
     * Broadcasts one maneuver.
     *
     * [nextEventType] and [turnSide] are the wire numbers
     * [com.andrerinas.openheadunit.contract.NavigationUpdateIntent] already carries, so both the
     * legacy `NextTurnDetail` messages and the newer instrument-cluster ones arrive here already
     * normalised.
     *
     * Android Auto repeats an unchanged maneuver every second - the captured logs show the same
     * road and distance three and four times in a row - and a cluster gains nothing from being told
     * again. Identical payloads are dropped, which also keeps a stationary car from filling the log
     * with one line per second.
     */
    fun send(nextEventType: Int, turnSide: Int, road: String?, distanceMeters: Int?) {
        val iconId = EncarsManeuverPolicy.iconIdFromWire(nextEventType, turnSide)
        val nextRoad = EncarsManeuverPolicy.nextRoad(road)
        val distance = EncarsManeuverPolicy.distance(distanceMeters)

        val payload = Triple(iconId, nextRoad, distance)
        if (payload == lastSent) return
        lastSent = payload

        val intent = Intent(EncarsManeuverPolicy.ACTION).apply {
            setPackage(targetPackage)
            putExtra(EncarsManeuverPolicy.EXTRA_ICON_ID, iconId)
            putExtra(EncarsManeuverPolicy.EXTRA_NEXT_ROAD, nextRoad)
            putExtra(EncarsManeuverPolicy.EXTRA_DISTANCE, distance)
            putExtra(EncarsManeuverPolicy.EXTRA_UNIT, EncarsManeuverPolicy.UNIT_METERS)
        }

        try {
            context.applicationContext.sendBroadcast(intent)
            AppLog.d("Nav: encars -> $targetPackage iconId=$iconId distance=$distance road=$nextRoad")
        } catch (e: Exception) {
            AppLog.w("Nav: encars broadcast to $targetPackage failed: ${e.message}")
        }
    }

    /**
     * Clears the cluster when the route ends.
     *
     * No capture covers this: the sniffed routes were stopped by hand while still running, so
     * nothing records what Yandex sends - if anything - when a route finishes. An empty maneuver is
     * the one payload the protocol is known to carry (the destination leg sends exactly this), so
     * it is the safest thing to send, and leaving a stale arrow pointing left on the dashboard
     * after arrival is the outcome worth avoiding.
     */
    fun clear() {
        lastSent = null
        val intent = Intent(EncarsManeuverPolicy.ACTION).apply {
            setPackage(targetPackage)
            putExtra(EncarsManeuverPolicy.EXTRA_ICON_ID, EncarsManeuverPolicy.ICON_NONE)
            putExtra(EncarsManeuverPolicy.EXTRA_NEXT_ROAD, "")
            putExtra(EncarsManeuverPolicy.EXTRA_DISTANCE, 0.0)
            putExtra(EncarsManeuverPolicy.EXTRA_UNIT, EncarsManeuverPolicy.UNIT_METERS)
        }
        try {
            context.applicationContext.sendBroadcast(intent)
            AppLog.d("Nav: encars -> $targetPackage cleared")
        } catch (e: Exception) {
            AppLog.w("Nav: encars clear to $targetPackage failed: ${e.message}")
        }
    }
}
