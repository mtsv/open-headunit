package com.andrerinas.openheadunit.input

/** The two steering-wheel buttons whose long press is sent to Android Auto. */
enum class SteeringButton { VOICE, PHONE }

/**
 * Tells a long press of the Voyah steering wheel's Voice and Phone buttons from a short one.
 *
 * The head unit's key service (`com.qinggan.keymanager.service`) has no long-press code: a held
 * button is a DOWN, possibly repeats, and an UP, all carrying the OEM's own key code (130 Voice,
 * 128 Phone — captured on a live H97C unit by the VoyahTune project). So the length is measured
 * here, and a press counts as long the moment it has been held for [LONG_PRESS_MS], without waiting
 * for the release. Holding the button gets an answer without letting go of it.
 *
 * Voice is the exception when Voyah Tweaks is installed. VT hooks the key service upstream of
 * every listener, consumes short Voice presses for its own voice commands, and lets only the long
 * ones through — which is why, with VT, only a long press ever showed the stock "voice commands not
 * implemented" text. Whatever VT does to the timing on the way (VoyahTune, the other tweaker,
 * holds the DOWN and replays it at release), a Voice event that reaches OHU at all *is* a long
 * press. So under VT, Voice fires on its first DOWN instead of arming the timer: measuring a
 * replayed press would read it as short and drop it.
 *
 * What the car showed (2026-10-31, builds 12–13) overturned both assumptions above. The SDK's
 * listener *does* report long presses itself (`onKeyLongPressStart`, at about a second), and VT
 * does *not* keep short Voice presses from listeners. So the receiver now passes
 * `serviceReportsLong`: no timer is armed, a press fires only on [onLongReported], and
 * `longOnFirstDown` is always false. Firing on the first DOWN would have taken every short press
 * from VT. A timer of OHU's own would fire on a 600 ms–1 s press that the service then hands on as
 * short, so both would act on it. The timer and the VT rule stay for an SDK that reports neither.
 *
 * Pure: the receiver owns the clock and the timer, and feeds the timer's expiry back in through
 * [onTimer].
 */
object SteeringLongPressPolicy {

    /** VoyahTune's threshold for the same buttons on the same unit. */
    const val LONG_PRESS_MS = 600L

    const val QG_KEY_VOICE = 130
    const val QG_KEY_PHONE = 128

    fun buttonFor(qgKeyCode: Int): SteeringButton? = when (qgKeyCode) {
        QG_KEY_VOICE -> SteeringButton.VOICE
        QG_KEY_PHONE -> SteeringButton.PHONE
        else -> null
    }

    /** Per button: whether it is held, and whether this hold has already fired. */
    data class State(val held: Boolean = false, val fired: Boolean = false)

    sealed interface Effect {
        object None : Effect
        /** Start (or restart) the button's timer; call [onTimer] when it expires. */
        data class ArmTimer(val delayMs: Long) : Effect
        /** Stop the button's timer. */
        object CancelTimer : Effect
        /** The press is long: act on it. */
        object Fire : Effect
    }

    data class Decision(val state: State, val effect: Effect)

    /**
     * A DOWN. Key repeats of a held button change nothing; a DOWN while the previous hold never saw
     * its UP starts a new hold, since a lost UP must not leave the button deaf.
     */
    fun onDown(
        state: State,
        button: SteeringButton,
        repeatCount: Int,
        longOnFirstDown: Boolean,
        serviceReportsLong: Boolean = false,
    ): Decision {
        if (repeatCount > 0 && state.held) return Decision(state, Effect.None)
        if (button == SteeringButton.VOICE && longOnFirstDown) {
            return Decision(State(held = true, fired = true), Effect.Fire)
        }
        // The car's key service measures long presses itself (~1 s, onKeyLongPressStart). A timer
        // of OHU's own that fires first would class a 600 ms–1 s press as long while the service
        // classes it short and hands it to VT or the stock call screen: both would act on it.
        if (serviceReportsLong) return Decision(State(held = true, fired = false), Effect.None)
        return Decision(State(held = true, fired = false), Effect.ArmTimer(LONG_PRESS_MS))
    }

    /** The timer armed by [onDown] expired. Fires once per hold. */
    fun onTimer(state: State): Decision {
        if (!state.held || state.fired) return Decision(state, Effect.None)
        return Decision(state.copy(fired = true), Effect.Fire)
    }

    /**
     * The key service measured a long press itself (its listener's `onKeyLongPressStart`, seen on
     * the car's SDK on 2026-10-31). Whichever of this and the timer comes first fires; the other
     * finds the hold already fired. Fires even without a DOWN seen, since that is all the service
     * needs to have said.
     */
    fun onLongReported(state: State): Decision {
        if (state.fired) return Decision(state, Effect.None)
        return Decision(State(held = true, fired = true), Effect.Fire)
    }

    /** An UP. Before the timer it is a short press, which belongs to VT or the stock handler. */
    fun onUp(state: State): Decision {
        if (!state.held) return Decision(State(), Effect.None)
        return Decision(State(), if (state.fired) Effect.None else Effect.CancelTimer)
    }
}
