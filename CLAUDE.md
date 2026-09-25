# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Open Headunit turns an Android tablet/phone into an Android Auto **receiver** (head unit). It
speaks the Android Auto Projection (AAP) protocol — reverse-engineered, protobuf over TLS — to a
phone running Android Auto, over USB (AOA/libusb) or Wi-Fi. Revival of Michael Reid's original
`headunit` project.

Gradle multi-project: `:app` (everything) and `:contract` (a tiny library holding the public
automation intent constants — see `contract/README.md`, which is the authoritative reference for
the broadcast/deep-link automation surface).

## Build and test

Windows shell: use `gradlew.bat`; the commands below use the `./gradlew` spelling.

```bash
./gradlew :app:assembleGithubDebug            # the standard build (what CI builds)
./gradlew :app:testGithubDebugUnitTest        # the full JVM unit-test suite
./gradlew :app:lintGithubDebug                # advisory only (abortOnError = false)
```

Run a single test class or method (Gradle's `--tests` filter):

```bash
./gradlew :app:testGithubDebugUnitTest --tests '*ProjectionWatchdogPolicyTest'
./gradlew :app:testGithubDebugUnitTest --tests '*ProjectionWatchdogPolicyTest.the session is live*'
```

Test reports land in `app/build/reports/tests/testGithubDebugUnitTest/index.html`.

Toolchain: JDK 21 (Gradle 8.13 rejects Java 24+), NDK pinned in `app/build.gradle.kts`, CMake
3.22.1. Native code (`app/src/main/cpp`) builds automatically as part of `assemble`.

### Flavors

`distribution` dimension: **`github`** (minSdk 16) and **`playstore`** (minSdk 21). Each flavor
supplies its own `utils/VpnControl.kt` from `app/src/github/` and `app/src/playstore/` — shared
`main` code calls it unconditionally, so anything touching VPN must be added to *both* source sets
or the other flavor stops compiling. CI builds `github` only.

There are **no instrumented tests** (`app/src/androidTest` does not exist). Everything is JVM unit
tests under `app/src/test`.

### Signing

Release signing reads, in order: `key.properties` → `secrets.properties` → env vars
(`HEADUNIT_KEYSTORE_PASSWORD`, `HEADUNIT_KEY_PASSWORD`), with the keystore at
`headunit-release-key.jks` in the root. Absent all of these, release builds are simply unsigned —
the build does not fail. Debug builds need nothing.

## The dominant convention: policy objects

This is the single most important thing to understand before writing code here.

There are ~146 `*Policy.kt` / `*Monitor.kt` / `*Tracker.kt` files and ~210 test files, and almost
none of them import `android.*`. The pattern: **every non-trivial decision is extracted out of the
Android-coupled class (Service, Activity, Fragment, transport thread) into a pure Kotlin `object`
or `class` with no framework dependencies, and unit-tested exhaustively.** The Android class then
just calls it and acts on the answer.

Examples: `ProjectionWatchdogPolicy` (when the video watchdog fires), `AapReadRecoveryPolicy`
(whether a read error is fatal), `UsbEndpointSelectionPolicy`, `DecoderRestartPolicy`,
`AutomationCommandPolicy`, `LinkLossTeardownPolicy`.

Consequences for any change you make:

- **New behavioral logic goes in a policy object with a test**, not inline in a service or
  fragment. If you find yourself adding a condition to `AapService` or `AapTransport`, that
  condition probably belongs in a policy.
- **Mocking is rare** (4 of 210 test files use Mockito). If a test needs heavy mocking, the logic
  under test is in the wrong place.
- **KDoc on policies is history, not description.** These comments record the bug that forced the
  extraction, the issue number, and why the thresholds are what they are. Read them before changing
  a constant — several constants are load-bearing against each other (e.g.
  `ProjectionWatchdogPolicy.DISPLAY_FREEZE_MS` vs. `CorruptionConcealmentPolicy`, a relation that
  is pinned by tests). Preserve and extend this style; don't strip it to one-liners.
- Tests use backtick-quoted sentence names and often carry a header comment explaining which
  regression the table pins.

## Architecture

### Connection lifecycle — `CommManager` is the source of truth

`connection/CommManager.kt` owns both the physical link and the protocol layer, and exposes a
single `connectionState` StateFlow. Everything else (`AapService`, `AapProjectionActivity`, UI
fragments) **observes that flow** rather than being called imperatively.

```
Disconnected → Connecting → Connected → StartingTransport → HandshakeComplete → TransportStarted
                                                                                      ↓
                                                    (disconnect / read error / phone bye-bye) → Disconnected
```

A frequent source of bugs: `HandshakeComplete` is a *brief* window; `TransportStarted` is the
steady state for the whole drive. Code that checks for only one of them is usually wrong.

The physical link is abstracted behind `connection/projection/ProjectionConnection`, implemented by
`StandardUsbProjectionConnection`, `LibusbProjectionConnection` (native fallback via
`cpp/usbhelper.c`), and `SocketProjectionConnection` (all Wi-Fi paths, port 5277).

### Protocol — `aap/`

`AapTransport` is the message pump, running three `HandlerThread`s: **Send** (encrypt + write),
**Poll** (read + decrypt + dispatch), and **Video** (assemble + decode, on its own thread so
decoder backpressure cannot stall every other channel). Messages are routed by channel id
(`aap/protocol/Channel.kt`: control, sensor, video, input, audio×3, mic, bluetooth, media playback,
navigation, …) to `AapMessageHandler` implementations — `AapControl`, `AapAudio`, `AapVideo`,
`AapMediaPlayback`, `AapNavigation`.

Wire format is protobuf. **`aap/protocol/proto/*.java` is generated** from `app/src/main/proto/*.proto`
by `app/src/main/proto/gen.sh` (which hardcodes a local protoc path — edit it for your machine).
There is no protobuf Gradle plugin; the generated Java is checked in. Edit the `.proto` and
regenerate, never the `.java`.

`aap/AapService.kt` is the foreground service tying it together: USB attach detection, Wi-Fi
discovery/server, the notification, car mode, night mode, media session, GPS.

### Wireless modes — `connection/wifi/modes/`

Four strategies, selected in settings, each with very different mechanics and a large body of
policy objects:

- **Helper** — the companion Wireless Helper app triggers projection (works up to Android Auto 17.3).
- **Native** (`modes/nativeaa/`, the largest subtree) — speaks Android Auto's own wireless
  handshake directly over Wi-Fi Direct or the head unit's hotspot, including Bluetooth/HFP wake
  (`HfpSlcInitiator`, `BluetoothWakePolicy`) and credential handoff.
- **Manual / Auto** — connect to a phone running Android Auto's built-in "Headunit Server".
- Plus hardware USB dongles, which need no software negotiation.

Android Auto 17.4+ broke most third-party wireless triggers — this constraint shapes much of the
code here; see the NOTE at the top of `README.md`.

### Video — `decoder/video/`

Hardware `MediaCodec` H.264/H.265 with a **software FFmpeg HEVC fallback** (`cpp/ffmpeg_hevc_decoder.cpp`
→ `libhur_soft_hevc.so`; prebuilt FFmpeg `.so`s live in `app/src/main/jniLibs/<abi>/`). CMake
compiles the decoder with `HUR_HAVE_FFMPEG=0` if those libs are missing rather than failing, so a
build can silently lose software HEVC. Broken vendor H.265 decoders are a known field problem, which
is why the decoder area carries so much recovery/credibility policy (`DecoderConfigLadder`,
`PictureCredibilityPolicy`, `KeyframeRepairTracker`, `VideoRecoveryPolicy`).

### Settings and automation

`utils/Settings.kt` is a single large SharedPreferences facade — all persisted user config goes
through it, typed as Kotlin properties.

`automation/AutomationReceiver` is the public control surface for Tasker/MacroDroid/adb, and the app
broadcasts `SESSION_STATE`. **The applicationId and the namespace deliberately differ**:

| | |
|---|---|
| applicationId (package) | `com.andrerinas.headunitrevived` |
| namespace / action prefix | `com.andrerinas.openheadunit` |

The Play Store listing was kept when the app was renamed. Using the wrong one silently does nothing.
Configuration commands are gated behind an "Allow external configuration" setting and withhold
credential-bearing keys; read `contract/README.md` before touching anything in `automation/`.

## Conventions

- Kotlin, 4-space indent, 100-column limit, LF endings, trailing commas allowed — see `.editorconfig`.
  Import order: default, `android.`, `androidx.`, `com.`, `org.`, `java.`, `kotlin.`, aliases.
- New code is Kotlin; the handful of remaining `.java` files (`Utils.java`, `SystemProperties.java`,
  generated protobuf) are legacy.
- Log through `utils/AppLog`, not `android.util.Log` — `AppLog` feeds the in-app capture/export that
  users attach to bug reports.
- `minSdk 16` on the github flavor is real: guard new framework APIs by `Build.VERSION` rather than
  assuming anything modern is available.
- `CHANGELOG.md` and `LICENSE` are copied into the APK assets at build time (`copyRootAssets` task)
  and shown in-app; the changelog is also mirrored into `README.md`.
- Commit subjects on this repo read as a sentence about the outcome, area-prefixed —
  e.g. `Audio: the sink is measured, sized by the dial, and keeps what it has`.

## Gotchas

- `versionCode`/`versionName` in `app/build.gradle.kts` do not move between candidates of the same
  fix, so they cannot identify a build. `BuildConfig.GIT_SHA` (with a `-dirty` suffix) can.
- `BuildConfig.AVAILABLE_LOCALES` is scanned from `values-*` directories **at configuration time**;
  adding a translation directory requires a Gradle re-sync to be picked up.
- Lint is not run in CI: `:app:lintAnalyzeGithubDebug` hangs on GitHub runners (likely the large
  generated protobuf sources). Run it locally, and treat findings as advisory.
- `.github/workflows/README.md` documents CI and carries known-failure notes; check it before
  concluding a red `main` is your doing.
