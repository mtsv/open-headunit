# Tweaks

Features this fork adds to Open Headunit (OHU). Upstream is
[andreknieriem/open-headunit](https://github.com/andreknieriem/open-headunit). Each entry
describes what the tweak does, how to use it, and where its code lives.

## Hide Android Auto bar

Android Auto draws a bar (Home, the app switcher and notifications) along the left, right or
bottom edge of the screen. This tweak slides that bar off screen and gives its room to the app in
front, so a navigator gets the full width of the head unit's display. It works during a session,
without reconnecting.

### Using it

1. In **Settings**, under **Hide Android Auto bar**, turn on **Show hide-bar button**.
2. Open **Hidden area** and set how many screen pixels to push off each edge: left, right and
   bottom. Raise the side the bar is on until the bar just disappears. During a session the
   projection updates as you type, so you can tune it while watching. Each side is capped at 25%
   of the screen.
3. During a session, tap the round **`<->`** button to hide or show the bar. Its icon is filled
   while the bar is hidden.

Android Auto shows its logo screen for about a second every time the bar is hidden or shown.
That is Android Auto laying itself out again; the session is not restarted.

Whether the bar is hidden is remembered: the next session starts the way the last one left it.

**Other ways to toggle the bar:**

- **Quick Settings** over the projection has the same switch and the same **Hidden area** dialog.
- **Key mapping** has a **Hide / Show Android Auto Bar** action, so a hardware or steering-wheel
  key can toggle it.

### The button

- **Move it:** long-press until it vibrates, then drag it. Its position is saved as a percentage
  of the screen, so it stays on screen if the window size changes. By default it is centred on
  the top edge.
- **Fading:** after a while without a touch, the button fades. The first tap on a faded button
  only wakes it and does not toggle the bar, so an accidental touch costs nothing. This matters
  most when the faded opacity is 0% and the button is invisible.
- **Settings:**

  | Setting | Range | Default |
  |---|---|---|
  | Button size | 32–120 dp | the Home floating button's size |
  | Button opacity (active) | 10–100% | the Home floating button's opacity |
  | Button opacity (faded) | 0–100%, never above active | 20% |
  | Button stays active for | 0–60 s; 0 = **Always active** (never fades) | 5 s |

- **Looks:** a #282A2E circle with a #43464C ring, so at 100% opacity it is fully solid.

### When it is not available

The settings show the reason in place of the description. The bar can't be hidden:

- unless **Video fit mode** is **Fill**
- with **Forced Scale**
- with **HUD mode** (mirroring)
- in portrait (landscape only)
- in picture-in-picture
- with a **SurfaceView** on Android older than 7 (use TextureView or GLES20 there)

If the setup stops qualifying during a session, the bar comes back. It hides again once the setup
qualifies again.

### How it works

OHU tells Android Auto the screen is larger than it is, by the hidden-area amounts. Android Auto
lays out for that larger screen with its bar in the extra strip, and OHU draws the picture shifted
so that strip falls outside the window.

- **Sizing:** the larger screen goes to the phone as extra margin in a mid-session
  `UpdateUiConfigRequest`. The shape the session announced at discovery (pixel aspect ratio or
  margins) stays as it was. If the larger screen does not fit the video buffer, it is shrunk
  evenly, so Android Auto's UI gets a few percent smaller while the bar is hidden.
- **Touches:** while the bar is hidden, touches are mapped straight into the larger screen's
  coordinates. The hidden bar can't be reached, by design; the `<->` button is the way back.
- **DPI:** the hidden area is edited in screen pixels but stored in dp at the announced density,
  so a later DPI change keeps the bar hidden without retuning.
- **Delivery:** the phone answers each margin update in about 40 ms, but drops one sent before
  the video channel is ready. OHU resends an unanswered update every second, up to 8 times
  (`UiConfigAckPolicy`). Without that, a session that started with the bar hidden was laid out
  too tall.

### Code

| Part | Where |
|---|---|
| Eligibility, the size and margin math, touch mapping, dp↔px | `utils/CanvasExtensionPolicy.kt` |
| Debounce of the live preview while tuning | `utils/CanvasExtensionPreviewPolicy.kt` |
| Resending margins the phone did not answer | `utils/UiConfigAckPolicy.kt` |
| The button's view (fade, drag, tap) | `main/HideAaBarButton.kt` |
| The button's decisions (visibility, fade, opacity, position) | `main/HideAaBarButtonPolicy.kt` |
| The **Hidden area** dialog, shared by Settings and Quick Settings | `main/CanvasExtensionDialog.kt` |
| Applying it to the projection, re-announcing margins | `aap/AapProjectionActivity.kt` |
| The key action | `KeyCode.KEY_HIDE_AA_BAR` (65540), handled in `aap/AapTransport.kt` |
| Persisted settings | `utils/Settings.kt`, keys `canvas-extension-*` (also in the settings backup) |
| The button's look | `res/drawable/bg_hide_aa_bar_button.xml`, `ic_hide_aa_bar*.xml` |

The code calls this feature **CanvasExtension**. The UI never calls it "fullscreen", because
OHU's existing **Fullscreen** setting is something else: it hides Android's own status and
navigation bars.

## Navigation hints on the instrument cluster

Android Auto sends the whole turn-by-turn stream to the head unit, but a head unit has no way to
reach the car's own dashboard: the instrument cluster is driven over the vehicle bus by an OEM or
community service, never by an ordinary app. This tweak hands each maneuver to a head unit mod
that *can* draw on it, so the next arrow, the road it leads onto and the distance to it appear
between the dials instead of only on the projection.

It is off by default, and does nothing at all on a unit without such a mod.

### Using it

1. Install the mod that drives your cluster. The only one seen in the wild is **VoyahTweaks**
   (`ru.kachalin.voyahtweaks`), on Voyah and Pateo units.
2. In **Settings**, under **Navigation**, turn on **Navigation hints on dashboard**.
3. Start a route on the phone. The arrow follows the maneuver Android Auto is announcing, and goes
   away when the route ends.

This is not the **Navigation notifications** setting above it. That one posts Open Headunit's own
notification on the head unit's screen; this one sends nothing to the screen and everything to the
dashboard. The two are independent and can be used together or apart.

### What reaches the cluster

Four values per maneuver: an arrow id, the road the maneuver leads onto, the distance, and the
unit — always metres, at every distance.

| Android Auto maneuver | Arrow |
|---|---|
| Turn, sharp turn | `LEFT` / `RIGHT` |
| Slight turn | `SLIGHT_LEFT` / `SLIGHT_RIGHT` |
| On-ramp, off-ramp, fork, merge | `SLIGHT_LEFT` / `SLIGHT_RIGHT` |
| U-turn | `UTURN_LEFT` / `UTURN_RIGHT` |
| Roundabout enter, enter-and-exit | `ENTER_ROUNDABOUT` |
| Roundabout exit | `LEAVE_ROUNDABOUT` |
| Depart, straight, name change, ferry, destination | none |

### What it cannot do

- **An unsided turn draws no arrow.** Android Auto sends `UNSPECIFIED` for the turn side more
  often than one would like, and this vocabulary has no neutral turn to fall back on. Guessing a
  hand for a U-turn would be right in left-hand-drive countries and wrong in the rest, so an
  unsided maneuver goes out with no arrow rather than the wrong one.
- **The road name can lag by a turn.** `road` is `required` in the protocol, so the phone must put
  something there even for a turn onto an unnamed street — and what it puts is the previous
  maneuver's road. One captured drive ends with the same name on four consecutive maneuvers. A
  repeat cannot be told from a genuine one, because a route can legitimately turn onto a road and
  then U-turn back onto it, so nothing here can repair it.
- **Ramps are drawn as slight turns.** `EXIT_LEFT` and `EXIT_RIGHT` were described by the
  VoyahTweaks author but appear in no capture, so they are never sent: an id the cluster does not
  recognise most likely draws nothing at all.

### How it works

Open Headunit broadcasts `ru.encars.UPDATE_MANEUVER` with the maneuver in its extras, targeted at
the mod's package. The broadcast carries no permission — it cannot, because the consumer is a
third-party app that has never heard of Open Headunit — so `setPackage` is what keeps a maneuver
stream off every other app on the unit. The package is also listed in the manifest's `<queries>`
block: Android 11, which these units run, filters package visibility, and a target the app cannot
see fails the broadcast silently.

Yandex Maps reaches the same receiver, but only because VoyahTweaks injects a Frida hook into it
to make it broadcast. Open Headunit needs no hook: it already parses the navigation channel, so
sending the broadcast is the entire integration.

- **The heartbeat.** The protocol has no "still valid" message — the consumer holds the arrow only
  while broadcasts keep arriving, and expires it when they stop. So the last maneuver is re-sent
  once a second on a timer of OHU's own. Repeating whatever Android Auto last said is not enough
  on its own, because Android Auto drives its updates off the phone's position: a car stopped in
  traffic freezes the distance, identical payloads stop being sent, and the arrow disappears off
  the dashboard. One second is the cadence Yandex is observed to use, and so the only one proven
  to hold the arrow.
- **Ending a route.** The cluster is cleared as soon as the cluster status goes anything other
  than ACTIVE or REROUTING. `INSTRUMENT_CLUSTER_STOP` looks like the obvious hook and is not — it
  never arrives at all on the unit this was built against. REROUTING deliberately holds the arrow:
  a second of a stale maneuver beats blanking the dashboard every time the phone recalculates.
- **Backstops.** The heartbeat gives up on its own if the phone says nothing for 10 seconds, so a
  session that dies mid-route cannot pin its last turn to the dashboard for the life of the
  process, or put two broadcasters on one cluster when the next session starts. A payload with no
  arrow, no road and no distance left is not refreshed either — letting it lapse is what stops a
  finished route pinning "0 m" to the dials.
- **The distance is a `Double`**, not the `Float` the mod's author described. Every captured value
  has full double precision, and a consumer reading the extra as the other type gets its default
  of zero instead.

### Where the vocabulary came from

Two sources that disagree, so the code records both. The VoyahTweaks author listed eight arrow
ids. Three routes sniffed off Yandex with Frida — 1514 broadcasts — contain `LEFT` (899), `RIGHT`
(445), `ENTER_ROUNDABOUT` (71), `SLIGHT_RIGHT` (64), `LEAVE_ROUNDABOUT` (14) and the empty string
(21). `SLIGHT_RIGHT` is in no list the author gave, and neither `EXIT_` value was ever observed.

The captures are treated as authoritative and the author's list as a hint, because a maneuver
drawn as the wrong arrow is worse than one drawn as no arrow. Anything that cannot be mapped
confidently is sent as no arrow rather than a guess.

### Code

| Part | Where |
|---|---|
| Arrow table, road and distance rules, the timings | `aap/navigation/EncarsManeuverPolicy.kt` |
| The broadcast itself and the heartbeat timer | `aap/navigation/EncarsNavigationSink.kt` |
| Feeding it from the navigation channel | `aap/AapNavigationHelper.kt` |
| Clearing it when the route ends | `aap/AapNavigation.kt` |
| Clearing it when the session ends | `aap/AapService.kt`, on disconnect |
| Which road names a maneuver, not the accumulated street | `aap/NavigationRoadPolicy.kt` |
| The one process-wide sink, so a new session cannot start a second one | `AppComponent.kt` |
| Persisted setting | `utils/Settings.kt`, key `encars-navigation-broadcast` |
| Package visibility for the target | `AndroidManifest.xml`, `<queries>` |

`EncarsManeuverPolicy`'s KDoc is where the reasoning lives: what each capture showed, which
constants are guarded against an unmeasured deadline, and which real-car report forced each one.
Read it before changing a number.

The code calls this feature **Encars**, after the `ru.encars.UPDATE_MANEUVER` broadcast it speaks.

## Car GPS for the phone

Open Headunit reads the head unit's own GPS and sends it to the phone over the sensor channel,
under the **GPS for navigation** setting (on by default). That much is upstream. What this fork
adds is a fix that keeps it running, an accurate declaration of what the position is, and — the
part worth knowing before spending an evening on it — proof of what Android Auto does with it.

### What it achieves, and what it does not

**Android Auto does not navigate from the head unit's position.** It takes the fixes and goes on
using the phone's own, which is the opposite of what the feature looks like it promises.

Two independent pieces of evidence:

- Turn the phone's location off during a session, with the head unit holding a good fix and
  sending it at 1 Hz. The map loses position entirely. Nothing OHU sends reaches the map.
- Across three logged drives, the phone called `getLastLocation` on its *own* GPS provider at a
  steady ~6.6 Hz, and never once took a position from the head unit — see **The declaration**
  below for the full table.

So if the car's GPS is better than the phone's — a phone being spoofed, or just a tablet with a
better antenna than a handset in a cupholder — this will not fix the map. Nothing in the
projection protocol appears able to. The remaining route is a phone-side app publishing the head
unit's fixes as a **mock location provider**, which is different software and not part of OHU.

What the fork did fix is the part that was genuinely broken: the head unit now keeps sending a
correct position for the whole drive rather than stopping a few seconds after the projection goes
to the background.

### Keeping GPS alive behind the projection

Location stopped whenever the projection activity paused and resumed when it came back, with the
gaps lining up with `onPause`/`onResume` to within the app-op grace period.

The cause is that a "while using the app" location grant is exactly what it says: Android keeps
feeding an app location while it is visible, and an app that is only a foreground *service* has to
say so by declaring the `location` foreground-service type. OHU declared `connectedDevice`,
`mediaPlayback` and `microphone`, but not `location`, so the moment the activity stopped being
visible the fixes dried up.

The service now claims the `location` type for as long as GPS is running and drops it again
afterwards, rather than holding it for the whole session: `location` is a *while-in-use* type, and
on Android 14+ a service holding one cannot be started from the background. Claiming it only when
GPS is actually wanted keeps every other way the service starts working.

Confirmed on the drive after the change: the oldest fix the phone was sent was 978 ms old, and no
stale events at all.

Note that it needs the location permission granted as **Allow all the time** on Android 10+ to be
reliable; "While using the app" is what produced the original symptom.

### The declaration

`SensorSourceService` carries a `location_characterization` bitmask saying how a car derives its
position — raw satellites, dead reckoning, fusion with gyroscope, wheel speed, steering angle.
OHU never sent the field, so it arrived as 0: nothing declared, where a real car declares
something. It now sends `RAW_GPS_ONLY` (256), which is the truth — a tablet reading
`GPS_PROVIDER` has no CAN bus and fuses nothing.

This was tested as a fix for the section above and **is not one.** It is kept only because
declaring 0 was wrong on its own terms, at a cost of one varint:

| Announced | Phone's own-GPS polls | Window | Rate | Positions taken from the head unit |
|---|---|---|---|---|
| nothing (field absent) | 97 | 15 s | 6.5/s | 0 |
| 256, the honest value | 106 | 16 s | 6.6/s | 0 |
| 255, every fusion flag | 171 | 26 s | 6.6/s | 0 |

The third row is what closes it. 256 is the *weakest* value in the enum, so a phone ignoring it
might only have been preferring a better-described source; 255 claims every fusion flag and dead
reckoning, which no phone can rationally rank below its own fused provider. The phone parsed and
echoed all three and sourced its position identically each time. **Do not reach for a stronger
claim here** — the experiment has been run, it is dishonest, and it does not work.

### Diagnostics

With `GPS for navigation` on, one fix in every ten seconds is logged as it goes onto the wire,
read back out of the built protobuf rather than from the `Location` object, so a wrong multiplier
or a dropped field is visible rather than assumed. The sample carries the fix's age, and marks it
suspect past 3 s.

Zero and missing accuracies are also repaired before sending: a fix arriving with `accuracy=0`
goes out as 1 m rather than as a literal zero, and one with no accuracy at all as 100 m. In one
captured drive 883 of 1066 fixes had `accuracy=0`, which a consumer may read as "perfect" or as
"unknown" and neither is true.

### Code

| Part | Where |
|---|---|
| Which FGS types may be claimed, and when | `app/ForegroundServiceTypePolicy.kt` |
| Claiming and dropping the location type | `aap/AapService.kt` |
| The permission and the service's type list | `AndroidManifest.xml` |
| What is declared, and the drive table behind it | `aap/LocationCharacterizationPolicy.kt` |
| Sending the declaration | `aap/protocol/messages/ServiceDiscoveryResponse.kt` |
| The wire field | `proto/control.proto`, `Service.SensorSourceService` field 2 |
| Sampling and skew verdicts | `location/LocationWireDiagnostics.kt` |
| The accuracy floor | `location/LocationAccuracyPolicy.kt` |
| Encoding a fix, and the wire sample | `aap/protocol/messages/LocationUpdateEvent.kt` |

## Voyah steering wheel: Voice and Phone

The Voyah Free's steering wheel has **Voice** and **Phone** buttons that the head unit keeps for
itself, so Android Auto never hears about them. This tweak gives each a long press inside Android
Auto and leaves the short presses where they were:

| Button | Short press | Long press |
|---|---|---|
| Voice | unchanged (Voyah Tweaks' voice commands) | Google Assistant in Android Auto |
| Phone | unchanged (the head unit's call screen) | Android Auto's phone screen |

A long press brings Open Headunit to the front first if it is in the background. A second long
press of Voice while the assistant is listening closes it again. The head unit's own "voice
commands not implemented" message no longer appears, at all, while Open Headunit is running.

It is off by default, and the settings only appear on a head unit that has the Voyah key service.

### Using it

1. In **Settings**, under **Input**, turn on **Voyah steering wheel buttons**.
2. **Long-press Voice: Google Assistant** and **Long-press Phone: Android Auto phone** are on by
   default; turn either off to leave that button alone.
3. During a session, hold a button for about a second (the head unit's own long-press time).

With no session, a long press of Voice does nothing (but the message still stays away), and a long
press of Phone does what it always did: nothing.

**Without the steering wheel:** two automation commands do the same thing, for Tasker, MacroDroid
or `adb`, on any head unit:

```
adb shell am broadcast -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver \
  -a com.andrerinas.openheadunit.ACTION_VOICE_ASSISTANT      # or ACTION_OPEN_PHONE
```

### Status: works on the car (build 13, Voyah Free, Voyah Tweaks installed)

Tested on 2026-10-31. Both buttons work with a short and a long press, as in the table above. The
stock message does not appear.

### Reading the log

The buttons are read through the head unit's own key-service SDK, which Open Headunit loads from
the unit at run time and checks the shape of before using. Each time the service starts and each
time settings are saved, the log first says `VoyahKeys: setting on|off,
com.qinggan.keymanager.service visible|not visible, currently attached|detached`. It attaches only
when both are on/visible, and then says which of these happened:

- `VoyahKeys: com.qinggan.system.KeyManager from …` followed by the SDK's whole public surface,
  then `init onResult(true)` (or, after 3 s without one, `registering listeners (no init result …)`)
  and `registerInterceptListener(…) -> …` — it attached. Every button event is then logged as
  `VoyahKeys: intercept|observe onKeyDown (DOWN) int=… keyCode=… consume=…`. Long-press the buttons
  and look for `VoyahKeys: long press VOICE`.
- `VoyahKeys: cannot use … : <reason>` or `… not found in …` — the SDK has a shape Open Headunit
  does not drive yet. The surface logged just above it is what is needed to add it; send the log.

The automation commands above work regardless.

### How it works

The presses never become Android key events or a broadcast: they go from the car to
`com.qinggan.keymanager.service`, which hands them to the OEM apps registered with it. Voyah
Tweaks and VoyahTune both get them by injecting a hook into that process, and two hooks on the same
method fight (VoyahTune's installer disables Voyah Tweaks for that reason), so Open Headunit does
not inject anything. It registers through the OEM's client SDK, the way the stock phone and speech
apps do:

- **Voice** through an *intercept* listener. The SDK reports each press as callbacks: a short
  press is `onKeyDown` then `onKeyShortClick`; a long one is `onKeyDown`, then
  `onKeyHoldPressStart` and `onKeyLongPressStart` at about a second, then `onKeyLongPressStop`.
  With Voyah Tweaks installed, only the three long-press callbacks are swallowed, which is what
  keeps the message away; the short-press ones pass on to Voyah Tweaks. (An earlier build
  swallowed them all, believing Voyah Tweaks took short presses before any listener; it does not,
  and its voice commands stopped.) Without Voyah Tweaks every Voice callback is swallowed, since the
  stock short press shows the same message.
- **Phone** through an *observing* listener that never swallows anything, so the stock short
  press still opens the call screen.

A press is long when the key service says so (`onKeyLongPressStart`), not by a timer of Open
Headunit's own: a timer that fired sooner would class a press as long that the service then hands
on as short, and both would act on it.

The listeners stay registered for as long as Open Headunit's service runs, not only during a
session, so the message stays away when nothing is projecting. Stopping Open Headunit brings it
back.

| Steering key | OEM key code |
|---|---|
| Voice | 130 |
| Phone | 128 |

(From VoyahTune's capture on a live H97C unit. Star is 3090 and DVR 173; neither is used here.)

### Code

| Part | Where |
|---|---|
| Long vs short | `input/SteeringLongPressPolicy.kt`, fed by `KeyManagerApiShape.callbackFor` |
| Which events are swallowed | `input/VoiceKeyConsumePolicy.kt` |
| Raise, then assistant or phone | `aap/SteeringActionPolicy.kt`, run by `aap/SteeringActionRunner.kt` |
| Which SDK shapes are accepted | `connection/carkey/voyah/KeyManagerApiShape.kt` |
| Loading the SDK from the unit, and the log dump | `connection/carkey/voyah/VoyahKeyManagerSdk.kt` |
| The listeners | `connection/carkey/voyah/CarVoyahReceiver.kt` |
| Attaching when the setting changes | `connection/carkey/CarKeysManager.onSettingsChanged` |
| Automation commands | `contract/.../HeadUnitIntent.kt`, `automation/AutomationCommandPolicy.kt`, `aap/AapService.kt` |
| Persisted settings | `utils/Settings.kt`, keys `voyah-steer-*` (also in the settings backup) |
| Package visibility for the OEM apps | `AndroidManifest.xml`, `<queries>` |
