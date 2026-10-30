package com.andrerinas.openheadunit.aap.protocol.messages

import android.location.Location
import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.andrerinas.openheadunit.location.LocationAccuracyPolicy
import com.andrerinas.openheadunit.location.LocationWireDiagnostics
import com.andrerinas.openheadunit.utils.AppLog
import com.google.protobuf.Message

class LocationUpdateEvent(location: Location)
    : SensorEvent(Sensors.SensorType.LOCATION_VALUE, makeProto(location)) {

    companion object {

        /** When [logWireSample] last emitted. Written from the poll and main threads. */
        @Volatile
        private var lastSampleAtMs = 0L

        private fun makeProto(location: Location): Message {
            val data = Sensors.SensorBatch.LocationData.newBuilder().apply {
                timestamp = location.time
                latitude = (location.latitude * 1E7).toInt()
                longitude = (location.longitude * 1E7).toInt()
                if (location.hasAltitude()) {
                    altitude = (location.altitude * 1E2).toInt()
                }
                if (location.hasBearing()) {
                    bearing = (location.bearing * 1E6).toInt()
                }
                if (location.hasSpeed()) {
                    speed = (location.speed * 1E3).toInt()
                }
                // accuracy is a proto2 `required` field, so leaving it unset throws
                // UninitializedMessageException out of build() - and on the control
                // channel that surfaces as a HandleException, which kills the session.
                // LocationAccuracyPolicy is what guarantees a usable number for every
                // fix, including the chips that answer hasAccuracy() with true and then
                // report zero.
                accuracy = LocationAccuracyPolicy.accuracyMm(
                    location.hasAccuracy(), location.accuracy
                )
            }.build()

            logWireSample(location, data)

            return Sensors.SensorBatch.newBuilder().addLocationData(data).build()
        }

        /**
         * Records what this event actually puts on the wire, at most once per sample interval.
         *
         * Read from [data] rather than from [location], so the line is the encoded values a phone
         * receives and not the floats they came from - a wrong multiplier would look right in any
         * log written from the source side. See [LocationWireDiagnostics].
         */
        private fun logWireSample(
            location: Location,
            data: Sensors.SensorBatch.LocationData,
        ) {
            val now = System.currentTimeMillis()
            if (!LocationWireDiagnostics.shouldSample(now, lastSampleAtMs)) return
            lastSampleAtMs = now

            val skew = LocationWireDiagnostics.skewMs(data.timestamp, now)
            AppLog.i(
                "LocationUpdateEvent: on the wire " +
                    "lat=${data.latitude} lon=${data.longitude} (deg x 1e7) " +
                    "accuracy=${data.accuracy}mm " +
                    "altitude=${if (data.hasAltitude()) "${data.altitude} (cm)" else "unset"} " +
                    "speed=${if (data.hasSpeed()) "${data.speed} (mm/s)" else "unset"} " +
                    "bearing=${if (data.hasBearing()) "${data.bearing} (deg x 1e6)" else "unset"} " +
                    "timestamp=${data.timestamp} skew=${skew}ms " +
                    "[${LocationWireDiagnostics.skewVerdict(skew)}]; " +
                    "source provider=${location.provider} hasAccuracy=${location.hasAccuracy()} " +
                    "rawAccuracy=${location.accuracy}"
            )
        }
    }
}
