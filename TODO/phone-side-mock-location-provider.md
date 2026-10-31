# A companion app publishes the head unit's GPS as the phone's own location

**Type:** Feature

## What

The head unit's GPS is often the better one — a fixed antenna with a clear view of the sky, against
a handset in a cupholder — and in some areas the phone's is actively spoofed. Android Auto
navigates from the phone's position regardless of what the head unit sends it.

A companion app on the phone would register as a **mock location provider** and publish the fixes
Open Headunit sends. The phone's own location then *is* the head unit's, so Android Auto uses it
without having to be persuaded of anything.

## Why the protocol route is closed

This is the fallback, and it is the fallback because the direct route was tried and does not work.
Two independent proofs, both repeatable:

- Turn the phone's location off mid-session, with the head unit holding a good fix and sending it
  at 1 Hz. The map loses position entirely. Nothing OHU sends reaches the map.
- Across three logged drives the phone polled its *own* GPS provider at a steady ~6.6 Hz and took
  no position from the head unit, whatever the head unit declared about its location source:

  | `location_characterization` | Own-GPS polls | Window | Rate | Taken from head unit |
  |---|---|---|---|---|
  | absent (0) | 97 | 15 s | 6.5/s | 0 |
  | 256, the honest value | 106 | 16 s | 6.6/s | 0 |
  | 255, every fusion flag | 171 | 26 s | 6.6/s | 0 |

The third row is the one that closes it: 255 is a claim no phone can rationally rank below its own
fused provider, and it changed nothing. See `aap/LocationCharacterizationPolicy.kt` and the
**Car GPS for the phone** entry in `TWEAKS.md`. **Do not reopen that line of attack.**

## What the logs already tell us

Worth knowing before designing anything, because it removes a guess:

- Android Auto polls **`GPS_PROVIDER`**, not the fused provider. The phone logcat shows
  `gps provider getLastLocation from : 10211/com.google.android.projection.gearhead`, 171 times in
  26 seconds. So `GPS_PROVIDER` is the provider to mock, and mocking only the fused one would
  very likely do nothing.
- Nothing is being mocked today: `setTestProvider`, `addTestProvider` and `isMock=true` appear zero
  times across all three captures, so there is no existing mock app on the phone to conflict with.

## What it needs

1. **A channel from the head unit to the phone.** This is the main design question, and there is
   no free one today:
   - `connection/wifi/modes/helper/NearbySocket.kt` is a `Socket` subclass carrying the **AAP
     stream itself** over Nearby Connections. It is not a side channel and must not be
     multiplexed onto.
   - The Nearby discovery/pairing machinery around it (`NearbyManager`, `HelperStrategy`) is
     reusable for a second, separate connection.
   - Alternatives: a small socket server of its own beside the projection server (projection is
     port 5277, `SocketProjectionConnection`), or Bluetooth SPP, which the native mode already
     stands up for HFP wake (`connection/wifi/modes/nativeaa/HfpSlcInitiator.kt`).
   - Whatever is chosen has to survive the phone's screen being off for a whole drive.
2. **The companion app.** `ACCESS_MOCK_LOCATION` in its manifest, then
   `LocationManager.addTestProvider` / `setTestProviderLocation` on `GPS_PROVIDER`. The permission
   is not granted at runtime — the grant *is* the user picking the app under Developer options.
3. **A stand-down policy.** Pure Kotlin with tests, per the repo convention. The phone's real GNSS
   is **overridden, not blended**, so a head unit fix that goes stale or disappears — a tunnel, a
   cold start, a dropped link — takes the phone's position with it. The policy decides when to stop
   mocking and let the phone fall back to its own receiver. `location/LocationWireDiagnostics.kt`
   already computes fix age and a staleness verdict and is the obvious input.
   - It also has to remove the test provider on teardown. A companion app that is killed without
     removing it leaves the phone mocked after the drive ends.
4. **Setup UX.** Developer options → *Select mock location app* is not discoverable. At minimum a
   link to `ACTION_APPLICATION_DEVELOPMENT_SETTINGS` and a clear check of whether the app has
   actually been selected.
5. **Docs on completion.** A `TWEAKS.md` entry, and delete this file and its line in
   `TODO_features_and_bugs.md` in the same change.

## What it costs

These are the reasons this is a separate opt-in app and not a feature of OHU:

- **Developer options stay on permanently.**
- **Every app on the phone sees it.** `Location.isFromMockProvider` is public, and the override is
  not scoped to Android Auto. Banking apps and some navigation apps refuse to run with a mock
  provider active.
- **A bad head unit fix becomes the phone's reality**, including for apps that have nothing to do
  with the car. Item 3 is what bounds this, and it is the part most worth getting right.

## Open questions

- Does Android Auto accept a mocked `GPS_PROVIDER`, or does it detect and discard mock locations?
  Nothing in the captures answers this, and it decides whether the whole approach works. **Test it
  first, by hand, with any off-the-shelf mock location app** before building anything.
- Does the Wireless Helper companion app still function on Android Auto 17.4+? If the existing
  helper is already dead on current AA, reusing its machinery is less attractive. See the NOTE at
  the top of `README.md`.
- Should the head unit keep sending location over the sensor channel at the same time? It costs
  almost nothing, and the two are independent.
- Is this one more app, or a feature of the existing Wireless Helper?
