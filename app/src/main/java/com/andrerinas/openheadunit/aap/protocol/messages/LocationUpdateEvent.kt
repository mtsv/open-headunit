package com.andrerinas.openheadunit.aap.protocol.messages

import android.location.Location
import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.andrerinas.openheadunit.location.LocationAccuracyPolicy
import com.google.protobuf.Message

class LocationUpdateEvent(location: Location)
    : SensorEvent(Sensors.SensorType.LOCATION_VALUE, makeProto(location)) {

    companion object {
        private fun makeProto(location: Location): Message {
            return Sensors.SensorBatch.newBuilder().also { batch ->
                batch.addLocationData(
                    Sensors.SensorBatch.LocationData.newBuilder().apply {
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
                    }
                )
            }.build()
        }
    }
}
