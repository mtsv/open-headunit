package com.andrerinas.openheadunit.aap

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.connection.CommManager.ConnectionState
import com.andrerinas.openheadunit.input.SteeringButton
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Carries out [SteeringActionPolicy]'s steps. Shared by the Voyah steering-wheel receiver and the
 * `ACTION_VOICE_ASSISTANT` / `ACTION_OPEN_PHONE` automation commands, so a button and a macro do
 * exactly the same thing. Main thread.
 */
object SteeringActionRunner {

    fun run(context: Context, button: SteeringButton, source: String) {
        val commManager = App.provide(context).commManager
        val settings = App.provide(context).settings
        val steps = SteeringActionPolicy.stepsFor(
            button = button,
            sessionLive = commManager.connectionState.value is ConnectionState.TransportStarted,
            projectionForeground = AapProjectionActivity.isForeground,
            showBarFirst = settings.voyahSteerShowAaBar,
            barEnabled = settings.canvasExtensionEnabled,
            barHidden = settings.canvasExtensionActive,
        )
        AppLog.i("SteeringAction: $button from $source -> $steps")
        for (step in steps) {
            when (step) {
                SteeringActionPolicy.Step.RaiseProjection -> context.sendBroadcast(
                    Intent(AapService.ACTION_RAISE_PROJECTION).apply { setPackage(context.packageName) },
                )
                SteeringActionPolicy.Step.ShowAaBar -> {
                    // Stored first, so a projection that is not there yet starts with the bar shown;
                    // one that is (even paused behind another app) re-applies it on the broadcast.
                    settings.canvasExtensionActive = false
                    context.sendBroadcast(
                        Intent(AapService.ACTION_CANVAS_EXTENSION_CHANGED).apply { setPackage(context.packageName) },
                    )
                }
                SteeringActionPolicy.Step.ToggleAssistant -> commManager.sendToggleVoiceAssistant()
                SteeringActionPolicy.Step.OpenPhone -> {
                    commManager.sendKey(KeyEvent.KEYCODE_CALL, true, null, source)
                    commManager.sendKey(KeyEvent.KEYCODE_CALL, false, null, source)
                }
                is SteeringActionPolicy.Step.Ignore -> Unit
            }
        }
    }
}
