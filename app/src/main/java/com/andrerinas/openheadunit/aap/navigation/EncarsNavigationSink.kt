package com.andrerinas.openheadunit.aap.navigation

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Sends the maneuver the phone just reported to a head unit mod that can draw it on the
 * instrument cluster. [EncarsManeuverPolicy] holds every decision and the evidence behind it;
 * this is only the Android call and the timer that keeps the arrow alive.
 *
 * Unlike [com.andrerinas.openheadunit.contract.NavigationUpdateIntent] this broadcast carries no
 * permission. It cannot: the consumer is a third-party app that has never heard of Open Headunit
 * and holds nothing this app declares, which is exactly how Yandex reaches the same receiver.
 * Targeting the package instead of broadcasting implicitly is what keeps it from being readable by
 * everything on the unit.
 *
 * ## Why there is a heartbeat
 *
 * The consumer expires a maneuver that stops being repeated - see
 * [EncarsManeuverPolicy.REFRESH_INTERVAL_MS]. Repeating whatever Android Auto last said is not
 * enough on its own, because Android Auto is itself a source that can go quiet: it drives its
 * updates off the phone's position, and a car that is not moving gives it little reason to speak.
 * So the last payload is re-sent on a timer of this class's own, and the dashboard survives a
 * traffic jam whether or not the phone keeps talking.
 */
class EncarsNavigationSink(
    private val context: Context,
    private val targetPackage: String = EncarsManeuverPolicy.DEFAULT_TARGET_PACKAGE
) {

    private data class Payload(val iconId: String, val nextRoad: String, val distance: Double) {
        fun holdsCluster(): Boolean =
            EncarsManeuverPolicy.holdsCluster(iconId, nextRoad, distance)
    }

    /** The last payload sent, and what the heartbeat re-sends. Null while no route is running. */
    private var last: Payload? = null

    /** Monotonic, so it survives a wall-clock correction mid-drive. */
    private var lastSentAtMs = -1L

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Re-posts itself only while there is something worth holding. That is what stops a finished
     * route pinning "0 m" to the cluster, and it is also the only thing that ever stops this
     * runnable: nothing tears down an [EncarsNavigationSink] - its owner,
     * [com.andrerinas.openheadunit.aap.AapMessageHandlerType], has no teardown hook and is simply
     * dropped when the session ends - so a heartbeat that always re-posted would outlive the
     * transport that created it and broadcast forever.
     */
    private val heartbeat = object : Runnable {
        override fun run() {
            val payload = last ?: return
            if (!payload.holdsCluster()) return
            broadcast(payload, isHeartbeat = true)
            handler.postDelayed(this, EncarsManeuverPolicy.REFRESH_INTERVAL_MS)
        }
    }

    /**
     * Offers one maneuver.
     *
     * [nextEventType] and [turnSide] are the wire numbers
     * [com.andrerinas.openheadunit.contract.NavigationUpdateIntent] already carries, so both the
     * legacy `NextTurnDetail` messages and the newer instrument-cluster ones arrive here already
     * normalised.
     *
     * A payload identical to the last one is still sent once the refresh interval has passed; see
     * [EncarsManeuverPolicy.shouldSend].
     */
    fun send(nextEventType: Int, turnSide: Int, road: String?, distanceMeters: Int?) {
        val payload = Payload(
            iconId = EncarsManeuverPolicy.iconIdFromWire(nextEventType, turnSide),
            nextRoad = EncarsManeuverPolicy.nextRoad(road),
            distance = EncarsManeuverPolicy.distance(distanceMeters)
        )

        val changed = payload != last
        val sinceLast = if (lastSentAtMs < 0L) -1L else SystemClock.elapsedRealtime() - lastSentAtMs

        last = payload
        // Whatever the phone says re-arms the timer, so a heartbeat only ever fires in the gap
        // Android Auto leaves rather than alongside it. A payload with nothing left to show arms
        // nothing: letting the refresh lapse is how the cluster is cleared.
        armHeartbeat(payload)

        if (!EncarsManeuverPolicy.shouldSend(changed, sinceLast)) return
        broadcast(payload, isHeartbeat = false)
    }

    /**
     * Clears the cluster when the route ends, and stops the heartbeat that would otherwise keep a
     * finished route's arrow on the dashboard forever.
     *
     * No capture covers the end of a route: the sniffed routes were stopped by hand while still
     * running, so nothing records what Yandex sends - if anything - when one finishes. An empty
     * maneuver is the one payload the protocol is known to carry (the destination leg sends exactly
     * this), so it is the safest thing to send, and a stale arrow pointing left after arrival is
     * the outcome worth avoiding.
     */
    fun clear() {
        handler.removeCallbacks(heartbeat)
        last = null
        lastSentAtMs = -1L
        broadcast(
            Payload(EncarsManeuverPolicy.ICON_NONE, "", 0.0),
            isHeartbeat = false,
            what = "cleared"
        )
    }

    private fun armHeartbeat(payload: Payload) {
        handler.removeCallbacks(heartbeat)
        if (!payload.holdsCluster()) return
        handler.postDelayed(heartbeat, EncarsManeuverPolicy.REFRESH_INTERVAL_MS)
    }

    private fun broadcast(payload: Payload, isHeartbeat: Boolean, what: String? = null) {
        val intent = Intent(EncarsManeuverPolicy.ACTION).apply {
            setPackage(targetPackage)
            putExtra(EncarsManeuverPolicy.EXTRA_ICON_ID, payload.iconId)
            putExtra(EncarsManeuverPolicy.EXTRA_NEXT_ROAD, payload.nextRoad)
            // A Double, not a Float. See EncarsManeuverPolicy.distance.
            putExtra(EncarsManeuverPolicy.EXTRA_DISTANCE, payload.distance)
            putExtra(EncarsManeuverPolicy.EXTRA_UNIT, EncarsManeuverPolicy.UNIT_METERS)
        }

        try {
            context.applicationContext.sendBroadcast(intent)
            lastSentAtMs = SystemClock.elapsedRealtime()
            // Heartbeats are not logged: they repeat once a second for the whole drive and would
            // bury the maneuvers that actually changed, which are what a captured log is read for.
            if (!isHeartbeat) {
                AppLog.d(
                    "Nav: encars -> $targetPackage " +
                        (what ?: "iconId=${payload.iconId} distance=${payload.distance} " +
                            "road=${payload.nextRoad}")
                )
            }
        } catch (e: Exception) {
            AppLog.w("Nav: encars broadcast to $targetPackage failed: ${e.message}")
        }
    }
}
