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
