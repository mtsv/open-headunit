# After a car restart, OHU sometimes can't reconnect to the phone through the dongle

**Type:** Bug

## Setup

- The phone connects over **wireless Android Auto** to a **USB AA dongle**.
- The dongle is plugged into the head unit's USB port.
- This is the setup the docs recommend for OHU.

## Symptom

- Sometimes, after the car is restarted, OHU never reconnects to the phone.
- **Restarting OHU does not help**: force-stopping and relaunching the app gives the same result.
- **Only a head-unit restart helps.** After that, the connection works again.
- It happens intermittently; how to reproduce it reliably isn't known yet.

## What this suggests

Restarting the app clears all of OHU's in-process state, yet the problem survives it. Rebooting
the head unit fixes it. So the state that is stuck is probably *below* OHU, in the Android USB host
stack or in the dongle as the head unit sees it, and not in OHU's own connection state. Candidates:

- **The dongle is stuck in accessory (AOA) mode, or left half-switched.** On a car restart the
  head unit may keep USB power on, or cut it too briefly. The dongle then never re-enumerates, and
  OHU sees a device it can't talk to.
  - Relevant code: `connection/usb/StaleAccessoryRecoveryPolicy.kt` (the re-switch, then
    USB-reset ladder), `AoaSwitchFailurePolicy.kt`, `UsbAccessoryHandoffPolicy.kt`.
  - Check whether that ladder ran at all, and whether the USB reset actually re-enumerated the
    dongle.
- **The USB device is held by someone else.** A stale handle could be held by the system, by a
  stock car app, or by a previous OHU process whose native libusb fd survived. The claim then
  fails until reboot. Relevant code: `UsbNative.kt` / `cpp/usbhelper.c`,
  `LibusbProjectionConnection`, `UsbSwitchClaim.kt`.
- **USB permission or the attach intent is lost.** The device may already be attached when OHU
  starts, so no `USB_DEVICE_ATTACHED` arrives. Relevant code: `UsbAttachPolicy.kt`, `UsbReceiver.kt`,
  `app/UsbAttachedActivity.kt`.
- **The device is blacklisted** after earlier failures, and the blacklist persists across app
  restarts. Relevant code: `UsbBlacklistPolicy.kt`.
- **The dongle's own wireless side is stuck.** In this case the dongle's USB side is fine, but its
  Wi-Fi/BT link to the phone never comes back up. A head-unit reboot power-cycles the dongle, and
  an app restart doesn't. To tell this apart, unplug and replug the dongle instead of rebooting.

## Needed to diagnose

1. **An OHU log exported from a failing session,** captured before the head unit is rebooted. It
   should cover the car start, the failed connection attempts, and an OHU restart.
2. **`adb shell dumpsys usb` and `lsusb` (or `ls /dev/bus/usb/*`) while it is failing,** to see
   whether the dongle is enumerated and with which VID:PID: accessory mode `18d1:2d00/2d01`, or
   its own ID.
3. **Whether unplugging and replugging the dongle fixes it** without a head-unit reboot. This
   splits "the head unit's USB stack is stuck" from "the dongle is stuck".
4. **A comparison with a log from a good reconnect** after a car restart.

## Fix direction (once the cause is known)

- If it's the USB layer: extend the recovery ladder so an app restart, or a new attempt, can force
  re-enumeration (a USB reset, or a port power cycle where the head unit allows it). Put that in a
  policy with tests, per the repo convention.
- If OHU misses the already-attached device: scan `UsbManager.deviceList` at startup rather than
  waiting for an attach broadcast.
