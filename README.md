# No Hold Tune

Android app that spares you the other person's hold tune.

- **They put you on hold** → your side goes on hold too, so their hold tune doesn't play to you.
- **They come back** → your side resumes automatically (~1.5 s, the network's own confirmation time).

Android 13+, VoLTE calls.

## Setup (`play-store` branch)

1. Install `NoHoldTune.apk`.
2. Open the app → **Set as default phone app**.

Google Play only lets the default phone app handle calls, so this variant is a small dialer: a dial pad
and its own in-call screen (Answer, End, Hold, Mute, Speaker, Keypad). It needs no restricted permissions
(only notifications, CALL_PHONE and full-screen intents).

The `main` branch keeps the sideload variant, which works next to any dialer (e.g. Truecaller) by pairing
as a watch companion — not allowed on Play.

## How it works

The `InCallService` (the dialer's own, on this branch) receives `android.telecom.event.CALL_REMOTELY_HELD` / `CALL_REMOTELY_UNHELD`
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
