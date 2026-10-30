package com.andrerinas.openheadunit.app

import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Which foreground-service types this service claims, and when.
 *
 * The microphone type cannot be claimed at service start. Since Android 14 it is a while-in-use
 * type, so holding the permission is necessary and not sufficient: a service started from the
 * background is refused with "the app must be in the eligible state/exemptions" even with
 * RECORD_AUDIO granted, and this service starts from boot, USB and Bluetooth receivers. Measured on
 * a background Bluetooth auto-start, five times out of five.
 *
 * So the start claims [baseTypeMask], which holds no while-in-use type and cannot be refused, and
 * the microphone is added by [withMicrophone] at the moment capture actually opens - by then the
 * projection is on screen and the app is eligible. It is dropped again when capture stops, so the
 * type is held only while it is true.
 *
 * **Location is the same kind of type, claimed for the opposite reason.** It is while-in-use too,
 * so it must stay out of [baseTypeMask] for exactly the reason the microphone does - a background
 * start that claims it is refused, and onCreate answers a refusal by stopping the service, which
 * costs the whole session. But holding it is what keeps the head unit's GPS alive: without a
 * foreground service of this type, a "while using the app" location grant is withdrawn the moment
 * no OHU activity is visible, and ACCESS_FINE_LOCATION starts reading as denied. A field log showed
 * GPS going silent for 127 s, 328 s and 164 s, each window matching an AapProjectionActivity
 * onPause/onResume pair to within the app-op grace period, with Android Auto falling back to the
 * phone's own position every time. [withLocation] claims it once projection is up and the app is
 * eligible, and the claim then survives the activity going away - which is the entire point.
 *
 * The two while-in-use types are independent and can be held at once, so every claim goes through
 * [whileInUseMask] with both flags. Re-claiming one must never silently drop the other: a
 * startForeground call replaces the mask wholesale rather than adding to it.
 */
object ForegroundServiceTypePolicy {

    /**
     * What to claim when the service starts, from any caller and any process state.
     *
     * @param sdkInt the running platform level.
     * @return the mask for startForeground, or 0 before Android Q, where the call takes no types.
     */
    fun baseTypeMask(sdkInt: Int): Int {
        if (sdkInt < Build.VERSION_CODES.Q) return 0

        return ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    }

    /**
     * [baseTypeMask] plus whichever while-in-use types are true right now.
     *
     * Every claim composes the full set rather than adding to the live one, because startForeground
     * replaces the mask: a call that named only the microphone would drop a location type the
     * service was holding, and take the head unit's GPS down with it.
     *
     * @param sdkInt the running platform level.
     * @param microphone whether the microphone type should be held.
     * @param location whether the location type should be held.
     * @return the mask for startForeground, or 0 before Android Q, where the call takes no types.
     */
    fun whileInUseMask(sdkInt: Int, microphone: Boolean, location: Boolean): Int {
        val base = baseTypeMask(sdkInt)
        if (base == 0) return 0

        var mask = base
        if (microphone) mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (location) mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return mask
    }

    /**
     * What to claim while the microphone is open.
     *
     * @param sdkInt the running platform level.
     * @param recordAudioGranted whether RECORD_AUDIO is held right now.
     * @param headUnitMicEnabled the user's microphone setting.
     * @param locationHeld whether the location type is currently held, so re-claiming for the
     *   microphone keeps it rather than dropping it.
     * @return [baseTypeMask] plus the microphone type when both hold, plus location when held.
     */
    fun withMicrophone(
        sdkInt: Int,
        recordAudioGranted: Boolean,
        headUnitMicEnabled: Boolean,
        locationHeld: Boolean = false,
    ): Int = whileInUseMask(
        sdkInt = sdkInt,
        microphone = recordAudioGranted && headUnitMicEnabled,
        location = locationHeld,
    )

    /**
     * What to claim while the head unit's own GPS is feeding the phone.
     *
     * @param sdkInt the running platform level.
     * @param fineLocationGranted whether ACCESS_FINE_LOCATION is held right now.
     * @param gpsForNavigationEnabled the user's "GPS for navigation" setting - with it off the head
     *   unit sends no fixes, so there is nothing for the type to keep alive.
     * @param microphoneHeld whether the microphone type is currently held, so re-claiming for
     *   location keeps it rather than dropping it.
     * @return [baseTypeMask] plus the location type when both hold, plus the microphone when held.
     */
    fun withLocation(
        sdkInt: Int,
        fineLocationGranted: Boolean,
        gpsForNavigationEnabled: Boolean,
        microphoneHeld: Boolean = false,
    ): Int = whileInUseMask(
        sdkInt = sdkInt,
        microphone = microphoneHeld,
        location = fineLocationGranted && gpsForNavigationEnabled,
    )
}
