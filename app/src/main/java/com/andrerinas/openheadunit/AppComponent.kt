package com.andrerinas.openheadunit

import android.app.NotificationManager
import android.content.Context
import android.net.wifi.WifiManager
import com.andrerinas.openheadunit.aap.navigation.EncarsNavigationSink
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.decoder.audio.AudioDecoder
import com.andrerinas.openheadunit.decoder.video.DeviceMemoryProfile
import com.andrerinas.openheadunit.decoder.video.VideoDecoder
import com.andrerinas.openheadunit.connection.carkey.CarKeysManager
import com.andrerinas.openheadunit.utils.SUExecutor
import com.andrerinas.openheadunit.utils.Settings

class AppComponent(private val app: App) {

    val settings = Settings(app)
    // A function, not a reading: this decoder is a process singleton, so anything resolved here
    // once would outlive every settings change the user makes.
    val videoDecoder = VideoDecoder(settings) {
        DeviceMemoryProfile.readWithOverride(app, settings.debugForceMemoryProfile)
    }
    val audioDecoder = AudioDecoder()

    val notificationManager: NotificationManager
        get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val wifiManager: WifiManager
        get() = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val commManager = CommManager(app, settings, audioDecoder, videoDecoder)

    val suExecutor = SUExecutor()

    val carKeysManager = CarKeysManager()

    /**
     * Process-scoped on purpose. The sink refreshes the cluster on a timer of its own, so one per
     * transport meant a session that ended mid-route left its timer running and a second session
     * added another beside it - two broadcasters putting an old route's turns and a new route's on
     * one dashboard, alternating. One instance cannot do that: a new session simply hands the same
     * sink new maneuvers.
     */
    val encarsNavigationSink = EncarsNavigationSink(app)
}
