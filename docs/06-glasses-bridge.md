# 06. Glasses Bridge (Ray-Ban Meta Gen 2)

**Owner:** Device Lead runs, Claude writes. **Depends on:** localization (text path), routing (instructions). **Research:** [research/meta-glasses.md](research/meta-glasses.md). Read the research before touching this module; it changed the design.

## The constraint that shapes everything

On Android, the Meta Wearables Device Access Toolkit (version 0.9.0, developer preview) **blocks audio playback while a camera stream session is active**, and the stream itself runs at about 10 fps and 650 Kbps over Bluetooth Classic, which is too low for reading small text. Both are documented in the toolkit's issue tracker and confirmed by multiple teams. A HackMIT 2026 team planned exactly what we planned and ended up strapping the phone to their chest.

So Demo C is **not** "stream continuously and speak over it". It is **"look, then speak"** in bursts:

```
loop while navigating:
  start stream (about 10 s to stabilise the first time, faster after)
  capture: take 2 to 3 full-resolution stills with capturePhoto() over about 2 s
           (or sample video frames if stills prove slow)
  stop stream
  recognize: OCR the stills, vote, maybe snap to a node
  if node changed or instruction changed:
     speak the instruction over A2DP with TextToSpeech
     send it to the watch
  wait until speech has finished (UtteranceProgressListener), then repeat
```

Round-trip budget per cycle: measured in Hour 0 to 2. If a stop → speak → restart cycle takes more than about 8 s, we reduce the cycle to "capture on demand" triggered by the routing engine when the user is expected to be near the next anchor (we know the distance from the last node).

## Two modes, decided by Hour 18

| Mode | What the glasses do | What the phone does | When we pick it |
|---|---|---|---|
| **Full** | Camera in bursts, speaker out | Recognition, routing, speech, watch | Toolkit works on day one and stills are readable |
| **Headset fallback** | Speaker out only, paired as a normal Bluetooth device | Camera (phone at chest height or in a shirt pocket), recognition, routing, speech, watch | Toolkit fails, stills are unreadable, or the cycle is too slow |

The story is the same in both: spoken after-hours redirect, then spoken turns from what a camera saw. The fallback is honest as long as Devpost says which camera was used.

## Setup (pre-event, with lead time)

From the research:
- **Meta Managed Account organization** at work.meta.com, then a Wearables Developer Center team. This step has unbounded latency; start it today. **Mitigation:** in Developer Mode the app's `APPLICATION_ID` and `CLIENT_TOKEN` can both be `0`, which appears to bypass Developer Center registration. Verify this on day one; it is the difference between working and blocked.
- Meta AI app v282 or newer on the demo phone; glasses firmware v126 or newer. Update with the glasses above 10% battery and Wi-Fi on.
- Developer Mode: Meta AI app → Settings → App Info → tap the version 5 times → toggle; then per device under the glasses' settings. It can silently reset after an app or firmware update, so check it again Friday morning.
- SDK: `com.meta.wearable:mwdat-core`, `mwdat-camera`, `mwdat-mockdevice`, version 0.9.0, from Maven Central. Ignore any doc that mentions GitHub Packages and a token.
- Manifest: `BLUETOOTH`, `BLUETOOTH_CONNECT`, `INTERNET`, the registration callback URL scheme, and the analytics and crash-reporting opt-out meta-data.
- Country: the toolkit's full capability is limited to countries where AI glasses features are supported; the US is fine.

## Streaming details

- API shape (0.9.0): `Wearables.initialize()` → `createSession(AutoDeviceSelector())` → `session.start()` → `session.addCamera(StreamConfiguration(quality, fps))` → `camera.stream.start()` → collect `videoStream`. `capturePhoto()` works only while `STREAMING`.
- Quality: `MEDIUM` (504×896) at 15 fps for the video path; stills for OCR.
- Codec: H.265 Annex-B with parameter sets inline in the first keyframe. Copy `HevcDecoder.kt` from the official CameraAccess sample rather than writing a decoder.
- Run the session in a foreground service so it survives the screen turning off in a pocket.
- **Reconnect loop:** on any `SecurityException` or stream error, wait 500 ms and recreate the session. Never reuse a stopped session. Observe `session.state`, `stream.state`, `session.errors`, `stream.errorStream` separately.
- The first 5 to 10 s of a session are laggy. Warm the session up before the recording starts.
- A 0.9.0 regression adds 5 to 15 s of lag while the Meta AI app process is running; force-stop it before a demo run and measure both ways in Hour 0 to 2.
- Capture LED is always on while the camera is active and cannot be disabled. Fine; mention it as a privacy feature if asked.
- Thermal and battery cap a session at about 30 to 45 minutes. Demo C is 3 minutes.

## Speech

- Android `TextToSpeech`, warmed up at app start (construct on a background thread, set locale, speak a blank utterance) so the first real instruction starts in tens of milliseconds.
- `setAudioAttributes(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, CONTENT_TYPE_SPEECH)` so it routes over A2DP like media, with ducking.
- `QUEUE_FLUSH` for every new instruction; a stale instruction must not finish playing.
- Always pass an `utteranceId` and use `UtteranceProgressListener` to know when speech ends, both to restart the camera and to fire the watch haptic in sync.
- Install **Google TTS** on the demo phone and construct the engine with `com.google.android.tts`; Samsung's default engine is slower and more robotic.
- Do **not** enter `MODE_IN_COMMUNICATION` (the glasses mic path): it forces 8 kHz mono for everything including our speech and suspends A2DP. We have no voice input in the demo, so we never need it.
- Routing to the glasses rather than the watch: if the watch grabs the route, find the `AudioDeviceInfo` of type `TYPE_BLUETOOTH_A2DP` whose product name matches the glasses and set it as the preferred device on an `AudioTrack` that plays TTS synthesized to a file. Only build this if the three-device test shows the problem.

## Recognition on glasses frames

See [03-localization.md](03-localization.md), section 2. Key points for this module:
- Feed stills via `InputImage.fromBitmap(bitmap, rotation)`. Test the rotation in the first hour.
- Crop the centre of the frame; glasses cameras have a wide field of view and the sign the wearer is looking at is in the middle.
- Prefer big text anchors on the Demo C route. Check the character height in the glasses footage captured this week against ML Kit's 16 px minimum.
- Plan B recognizer (MediaPipe Image Embedder against the anchor photos) if OCR is not reliable by Hour 16.

## Mock Device Kit

`mwdat-mockdevice` lets anyone develop without the physical glasses: enable, pair a mock `RAYBAN_META`, power on, don, then `setCameraFeed(videoUri)` with an H.265 file (Android does not transcode; convert the glasses footage with ffmpeg before Friday). The whole bridge, recognition, and speech loop can be developed against the Student Center footage on a laptop-connected phone while the Device Lead has the real glasses.

## Glasses mode screen (S3)

See [07-app-shell-ui.md](07-app-shell-ui.md). Large status text, last seen anchor, current and next instruction, a small live thumbnail of the last still, Repeat and Stop buttons. The phone screen is what the second camera films in Demo C, so it must look alive.

## Work plan

| Hour | Task | Done when |
|---|---|---|
| Pre-event | MMA org, Developer Mode, firmware, sample app connects, glasses footage converted to H.265 for the mock | Sample app streams from the real glasses |
| 0 to 2 | Skeleton with mock device replaying footage; real-device session reaches `STARTED`; measure fps, latency, and the stop → speak → restart cycle | Numbers written in the debug overlay and in this doc |
| 14 to 16 | Burst capture loop, OCR with voting, node snap, TTS over A2DP, reconnect loop | Replay of the Student Center footage speaks the right turns |
| 16 to 18 | Real glasses walk in Klaus past 3 text anchors | 3 correct spoken turns in one walk |
| 18 | **Mode decision:** full or headset fallback | Written in [15-open-questions.md](15-open-questions.md) |

## What to verify in the first two hours

1. Meta AI app v282+, firmware v126+, SDK 0.9.0.
2. Registration reaches `REGISTERED` and the session reaches `STARTED` with ids `0` and Developer Mode on.
3. Frame rate and latency at MEDIUM with the Meta AI app killed and running.
4. The audio interlock: TTS while streaming, then after stopping. Time the cycle.
5. The full loop with the watch connected; TTS lands on the glasses.

## Verified on the real glasses, 2026-09-25 evening

Tested with Meta's CameraAccess sample (tools/meta-sample/) on the S25 Ultra (Android 17), Meta AI app 290.1, Ray-Ban Meta Gen 2, Developer Mode on, app id and client token `0`.

- **Developer Mode with ids `0` works.** Registration completed with an "Unverified app" consent screen in the Meta AI app, then a per-app camera permission prompt. No Meta developer organisation needed.
- **The toolkit version must match the "DWA" component on the glasses, not just the firmware.** The glasses report DWA 0.7.0.10.0. Toolkit 0.9.0 and 1.0.0 both fail at session start with `START_ERROR_DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED` ("Your glasses need an update. Go to App Connections page in Meta AI to update"), and the Meta AI app offered no update. **Toolkit 0.7.0 streams.** CampusMaps must build against `com.meta.wearable:mwdat-*:0.7.0` (API differences from 0.9.0: no `Camera` object; `session.addStream(...)` style; copy from tools/meta-sample/sample-0.7.0/samples/CameraAccess). Re-check the DWA version before the demo; if the Meta AI app updates it to 0.9+, switch the pin.
- **Stream numbers, second run, no audio, 50 s stable:** warm-up 7 fps for 5 s, then a steady 24 fps at 220 to 520 kbps with 22 to 29 ms transport delay (first run showed 15 fps, 100 kbps and 1.4 s delay while warming up). The image is soft; small text will not be readable from video frames, so stills are the OCR path, as planned. **`capturePhoto()` during the stream returned a photo in 2.2 s** (tap 20:15:23.47, data 20:15:25.53), which sets the burst-cycle budget.
- **First stream died after 37 s** with a Bluetooth Classic socket read failure while Spotify was opened on the phone. Consistent with the documented audio/camera interlock, possibly harsher (audio may drop the stream, not just be blocked). Treat "never overlap audio and camera" as a hard rule and keep the reconnect loop.
- Version table (Meta docs): 1.0.0 needs app V290 + firmware V128; 0.9.0 needs V282 + V126; 0.7.0 needs V272 + V125.
