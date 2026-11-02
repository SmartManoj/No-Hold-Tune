# No Hold Tune

Android app that spares you the other person's hold tune.

- **They put you on hold** → your side goes on hold too, so their hold tune doesn't play to you.
- **They come back** → your side resumes automatically (~1.5 s, the network's own confirmation time).

Works with any dialer (tested with Truecaller as the in-call UI) on a VoLTE call, Android 13+.

## Setup (no adb)

1. Install `NoHoldTune.apk`.
2. Open the app → **Pair (one-time setup)** → pick any Bluetooth device in the list (earphones, laptop…).
3. Scroll the permission list to the end → **Allow**.

Why pairing: Android only lets non-dialer apps follow calls if they hold `MANAGE_ONGOING_CALLS`, which the
system grants to watch-companion apps (`COMPANION_DEVICE_WATCH` role). The paired device is just the key;
it doesn't need to be connected. The role also grants SMS / contacts / call-log access as a bundle — the app
doesn't use them.

Alternative with adb: `adb shell appops set com.smart.noholdtone MANAGE_ONGOING_CALLS allow`

## How it works

A non-UI `InCallService` receives `android.telecom.event.CALL_REMOTELY_HELD` / `CALL_REMOTELY_UNHELD`
connection events and calls `Call.hold()` / `Call.unhold()`.

Timing, measured on Jio VoLTE:

| Action | Behaviour |
|---|---|
| unhold right after their unhold | works; confirms in ~1.5 s |
| hold right after their hold | always fails (`CALL_HOLD_FAILED`) |
| hold after 0.3 / 0.6 / 0.9 / 1.2 s | on hold after 2.2 / **1.5** / 1.8 / 2.2 s → 0.6 s used |

A second request while one is in flight is harmful (unhold on an already-active call becomes a Telecom
"swap" and re-holds it), so the app re-checks only after 3 s.

## Build

No Gradle: `bash build.sh` (aapt2 → javac → d8 → zipalign → apksigner with the debug keystore) → `build/NoHoldTune.apk`.
Needs the Android SDK (build-tools 35, platform 33) and a JDK.
