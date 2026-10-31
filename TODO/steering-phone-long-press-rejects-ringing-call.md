# Long press on the steering-wheel Phone button rejects a ringing call

**Type:** Feature

## What

While an incoming call is ringing, a long press of the steering-wheel Phone button should drop
(reject) the call instead of doing what it does now.

The short press is not affected. It still goes to the stock call screen, because the Phone
listener in `CarVoyahReceiver` only observes and never consumes the press.

## Current behaviour

A long Phone press always opens Android Auto's phone screen, whether or not a call is ringing:

- `CarVoyahReceiver` detects the long press using `SteeringLongPressPolicy`. The SDK's
  `onKeyLongPressStart` is the source.
- `SteeringActionPolicy.stepsFor(PHONE, …)` returns `RaiseProjection?`, then `ShowAaBar?`, then
  `OpenPhone`.
- `OpenPhone` sends a `KEYCODE_CALL` click to Android Auto. It runs in `SteeringActionRunner`.
- The long press is gated by the `Settings.voyahSteerPhoneLong` setting.

## What it needs

1. **Know that a call is ringing.** This is the open question.
   - OHU does not currently decode the AAP **phone status** channel. `Channel.ID_PHONE` /
     `"PHONE_STATUS"` exists in `aap/protocol/Channel.kt`, but no `AapMessageHandler` handles it,
     and there is no `PhoneStatus` proto in `app/src/main/proto/`.
   - **Option A:** advertise the phone-status service, then add the proto messages and a handler
     that tracks the call state (idle / ringing / in call). Edit the `.proto` and regenerate with
     `gen.sh`; never edit the generated `.java`.
   - **Option B:** read the car's own call state, from Android `TelephonyManager` or the car's
     HFP/Bluetooth call state. This only works if the head unit is also paired as a hands-free
     device.
   - Check which signal the car actually provides before choosing. Log it first.
2. **Drop the call.** Candidates, in order of preference:
   - Send `KEYCODE_ENDCALL` to Android Auto as an input key event, the same way `OpenPhone` sends
     `KEYCODE_CALL`. Verify on the car that AA honours it while ringing. It needs to be in the
     keycodes OHU advertises in the input service config.
   - Fall back to an HFP reject (`AT+CHUP`) if the head unit holds the HFP link.
3. **Policy.** Extend `SteeringActionPolicy` with a `callRinging` input. Map
   `PHONE && callRinging` to a new `Step.RejectCall`, and do not raise the projection or show the
   AA bar first. Add the cases to `SteeringActionPolicyTest`.
4. **Docs.** Add a `TWEAKS.md` entry. Consider an automation command alongside the existing
   phone one (`contract/README.md`, `HeadUnitIntent.kt`).

## Open questions

- Should a long press **during** an active call hang it up as well, or only while ringing?
- Should this be behind its own setting, or always on when `voyahSteerPhoneLong` is on?
- Does the car's stock call screen also react to the same long press? It is the observer path, so
  the press is not consumed. If it does, the call could be rejected twice, or answered by one
  handler and dropped by the other.
