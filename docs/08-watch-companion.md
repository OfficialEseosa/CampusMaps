# 08. Watch Companion (Galaxy Watch 8 Classic, Wear OS 6)

**Owner:** QA and Floater, with Claude. **Depends on:** routing instructions. **Research:** [research/mlkit-barometer-wear.md](research/mlkit-barometer-wear.md), section 4.

## Checkpoint

The watch starts only after routing produces correct instructions for all three demos (target: Hour 14). If it is not reliable by feature freeze (Hour 28), Demo C runs on glasses audio alone and the watch is not mentioned. This is a free win when it works and a time sink when it does not.

## What it does

The phone does all the thinking. The watch displays and buzzes.

- **One screen:** a large direction arrow (left, right, straight, turn around, up, down), an icon for the next step type (stairs up, stairs down, elevator, door or entrance, locked entrance, arrived), and a short label ("10 m", "Room 312", "Floor 3").
- **Haptics:** a distinct vibration pattern per instruction type, from the table in [04-routing.md](04-routing.md), so the wearer can navigate without looking.
- **Arrows are relative instructions**, not world-locked. "Turn left next", not an arrow at the real hallway.
- The phone sends each new instruction the moment routing updates, and the watch buzzes when the phone's speech for that instruction finishes (so sound and buzz land together in Demo C).

## Stack (2026)

| Piece | Choice | Notes |
|---|---|---|
| UI | Compose for Wear OS, `androidx.wear.compose:compose-material3:1.5.0` | First stable M3 for Wear; do not use Horologist's Compose UI (Google now advises against it) |
| Phone to watch | `play-services-wearable` 19.x: `MessageClient` for each instruction, `DataClient` for the current route and destination | `MessageClient` has no retry; the route lives in `DataClient` so the watch can recover after a Bluetooth drop |
| Discovery | `CapabilityClient` with a `wear.xml` capability | Find the phone node once |
| Keep the screen on | Ongoing Activity API plus `AmbientLifecycleObserver` | Wear OS 6 keeps the activity RESUMED in ambient; old `onPause`-based ambient code is wrong |
| Haptics | `Vibrator` + `VibrationEffect.createWaveform` for patterns, `createPredefined` for confirmations | Check `hasAmplitudeControl()`; start and end waveforms at amplitude 0 |
| Target | API 36 | Wear OS 6 requirement |

**Hard requirement:** the phone app and the Wear app must share the same application id and the same signing key, debug builds included, or the Data Layer never connects. Same Android Studio project, two modules, one `applicationId`.

## Message protocol

Path `/instruction`, payload a small JSON:

```json
{ "seq": 12, "type": "turn", "direction": "left", "label": "10 m", "text": "Turn left at the lobby T-junction", "haptic": "left" }
```

Path `/route` in `DataClient`: the full ordered instruction list plus the current index, so the watch can redraw after reconnecting. The watch ignores any `/instruction` with a `seq` lower than the last one it showed.

## Haptic patterns (milliseconds on/off)

| Type | Pattern |
|---|---|
| straight | 80 |
| left | 80, 80, 80 |
| right | 80, 80, 80, 80, 80 |
| stairs up / down | 300, 100, 80 |
| elevator | 300, 100, 300 |
| door / entrance | 80, 100, 300 |
| locked notice | 80, 60, 80, 60, 80, 60, 80 |
| arrive | 600 |

Tune on the wrist in Hour 16. Left and right must be unmistakable; that is the whole point.

## Install

Settings → About watch → Software → tap Software version 5 times; Developer options → ADB debugging, Wireless debugging, turn off automatic Wi-Fi; same Wi-Fi as the laptop; pair once with `adb pair`, then `adb connect`. Venue Wi-Fi may block device-to-device traffic; keep a phone hotspot ready. Do the first install **before the event**.

## Work plan

| Hour | Task | Done when |
|---|---|---|
| 0 to 2 | Wear module in the project, "hello" screen installs over Wi-Fi | Watch shows a test string sent from the phone |
| 14 to 16 | Instruction screen, haptic patterns, `MessageClient` and `DataClient` wiring | Walking the Klaus route shows the right arrow and buzz at each step |
| 16 to 18 | Reconnect test (walk out of range and back), ongoing activity, ambient | Watch recovers without restart |
| 20 to 24 | Used in the Demo C recording | Visible in the footage |

## Cut line

If the message path works but haptics or ambient handling misbehave, ship arrows only. If the Data Layer does not connect by Hour 18, stop and do not come back to it.
