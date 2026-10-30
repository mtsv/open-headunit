package com.andrerinas.openheadunit.aap

/**
 * What this head unit declares about where its position comes from.
 *
 * Android Auto's `SensorSourceService` carries a `location_characterization` bitmask describing how
 * a car derives its location - raw satellites, dead reckoning, fusion with gyroscope, wheel speed,
 * steering angle and so on. Open Headunit never sent the field, so it arrived as 0: nothing
 * declared. A drive log caught the phone printing exactly that back:
 *
 * ```
 * CAR.GAL.GAL.LITE: Adding service (Service id=1 type=SensorSourceService
 *   {sensors=[type=13,type=1,type=10,] locationCharacterization=0 ...})
 * ```
 *
 * In the same session the head unit held a correct GPS fix, encoded it, and sent it at 1 Hz with no
 * drops and no send failures, while Android Auto called `getLastLocation` on the *phone's* own GPS
 * provider 97 times and navigated from that. Declaring nothing is the one difference left between
 * what this head unit does and what a real car does, which is why the field is now sent.
 *
 * The values are Android Auto's own, extracted from its binary, and match the published
 * `android.car.hardware.property.LocationCharacterization` constants one for one - except that the
 * projection protocol spells the last one `RAW_GPS_ONLY` where the automotive API says
 * `RAW_GNSS_ONLY`. They are bit flags, so a real car combines several.
 */
object LocationCharacterizationPolicy {

    /** Prior samples refine the raw fix, as a Kalman filter does. */
    const val PRIOR_LOCATIONS = 1

    /** Gyroscope data refines the raw fix. */
    const val GYROSCOPE_FUSION = 2

    /** Accelerometer data refines the raw fix. */
    const val ACCELEROMETER_FUSION = 4

    /** Compass data refines the raw fix. */
    const val COMPASS_FUSION = 8

    /** Wheel speed refines the raw fix. */
    const val WHEEL_SPEED_FUSION = 16

    /** Steering angle refines the raw fix. */
    const val STEERING_ANGLE_FUSION = 32

    /** Car speed refines the raw fix. */
    const val CAR_SPEED_FUSION = 64

    /** Position is carried between satellite fixes, so it still means something in a tunnel. */
    const val DEAD_RECKONED = 128

    /** Satellites only, without the fusion a complete dead reckoning would need. */
    const val RAW_GPS_ONLY = 256

    /**
     * What this head unit announces: [RAW_GPS_ONLY], and nothing else.
     *
     * Open Headunit reads `LocationManager.GPS_PROVIDER` on an Android device. That is the satellite
     * fix and only the satellite fix: there is no CAN bus to take wheel speed or steering angle
     * from, and the device's own gyroscope and accelerometer are never fused into it - the fused
     * provider that would do so is deliberately not used, because a network-derived position must
     * never travel under the car's label. See LocationHolder.currentGpsFix.
     *
     * So every flag above [RAW_GPS_ONLY] would be a claim about hardware this head unit does not
     * have. The temptation is real - a car declaring dead reckoning and wheel-speed fusion presents
     * a stronger case than one declaring bare satellites, and the whole point of sending the field
     * is to be preferred over the phone. But a false declaration would not make the position any
     * better; it would tell Android Auto the fix can be trusted through a tunnel, where this head
     * unit actually has nothing to offer, and being believed at the wrong moment is worse than not
     * being believed at all.
     */
    const val HEAD_UNIT = RAW_GPS_ONLY

    /**
     * The value announced: always [HEAD_UNIT].
     *
     * ## What this field does not do
     *
     * It does not persuade Android Auto to navigate from the head unit's position. Three drives
     * settled that, each read off a paired head unit log and phone logcat. The phone echoed the
     * announced value back every time, so all three reached it and were parsed; what follows is
     * `gearhead` calling `gps provider getLastLocation` on the phone's *own* provider:
     *
     * | Announced | Calls | Window | Rate | Mock locations |
     * |---|---|---|---|---|
     * | nothing (field absent) | 97 | 15 s | 6.5/s | 0 |
     * | 256, the honest [HEAD_UNIT] | 106 | 16 s | 6.6/s | 0 |
     * | 255, every fusion flag | 171 | 26 s | 6.6/s | 0 |
     *
     * The counts differ only because the captures differ in length. Per second the three are
     * indistinguishable: Android Auto polls its own GPS at the same ~6.6 Hz whatever the head unit
     * declares, and in none of the three did a single position come from the head unit.
     *
     * The third drive was the deciding one. [HEAD_UNIT] is the *weakest* value in the enum, so a
     * phone that ignored it might merely have been preferring a better-described source;
     * announcing every fusion flag and dead reckoning - a claim no phone can rationally rank below
     * its own fused provider - removes that explanation. Android Auto does not weigh this field
     * when deciding whose position to navigate from, at any value.
     *
     * So do not reach for a stronger claim here: that experiment has been run, it is dishonest,
     * and it does not work. The remaining path is a phone-side mock location provider, which is a
     * different piece of software and not this one.
     *
     * ## Why it is still sent
     *
     * Because declaring 0 was wrong on its own terms. A real car sends this field; the head unit
     * sent nothing, which says "no information" rather than "satellites only". It costs one varint
     * and it is now accurate. That is reason enough to keep, and the only reason - it buys no
     * behaviour.
     */
    const val ANNOUNCED = HEAD_UNIT

    /**
     * The value to announce, or null when there is nothing to characterize.
     *
     * @param gpsForNavigationEnabled the user's "GPS for navigation" setting. With it off, the
     *   LOCATION sensor is not announced either, so describing a location source that will never
     *   send a fix would be describing nothing.
     * @return [ANNOUNCED] when the sensor is announced, and null when it is not, so the caller can
     *   leave the field unset rather than declaring a source of 0.
     */
    fun announced(gpsForNavigationEnabled: Boolean): Int? =
        if (gpsForNavigationEnabled) ANNOUNCED else null
}
