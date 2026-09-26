# Research: Ray-Ban Meta Gen 2 and the Wearables Device Access Toolkit (2026-09-20)

Compiled by a research agent with web sources. Claims carry links. The headline finding is in the box below; read that first.

> **Headline gotcha.** On Android you effectively cannot run the glasses camera and glasses audio at the same time. Audio playback (TTS, MediaPlayer) is blocked while a camera stream session is active ([issue #78](https://github.com/facebook/meta-wearables-dat-android/issues/78)), and using the glasses mic over SCO collapses video to about 0.5 to 1.3 fps ([issue #159](https://github.com/facebook/meta-wearables-dat-android/issues/159)). Real Android stream throughput is about 650 Kbps at about 10 fps, too low for OCR of fine text ([issue #174](https://github.com/facebook/meta-wearables-dat-android/issues/174)). Demo C must be designed as **burst capture, then speak**, not continuous stream plus speech. See [../06-glasses-bridge.md](../06-glasses-bridge.md).

## 1. Toolkit status, SDK coordinates, prerequisites

Still a developer preview: "Developers can access our SDK and documentation, test on supported AI glasses, and create organizations and release channels to share with test users." ([README](https://github.com/facebook/meta-wearables-dat-android)) Public publishing is gated to select partners; general availability targeted "in 2026" ([Meta blog](https://developers.meta.com/blog/introducing-meta-wearables-device-access-toolkit/), [FAQ](https://developers.meta.com/wearables/faq/)).

Public Android SDK on Maven Central, no token needed:
- Repo `github.com/facebook/meta-wearables-dat-android` (Apache, last push 2026-09-14)
- Group `com.meta.wearable`, version **0.9.0** (2026-08-03, [CHANGELOG](https://github.com/facebook/meta-wearables-dat-android/blob/main/CHANGELOG.md))
- Artifacts: `mwdat-core`, `mwdat-camera`, `mwdat-display`, `mwdat-mockdevice`
- An older doc page says to use GitHub Packages with a PAT; that is stale. Use `mavenCentral()`.

| Item | Requirement | Source |
|---|---|---|
| Min Android | Android 10+, Android Studio Flamingo+ | [llms.txt](https://wearables.developer.meta.com/llms.txt), [Setup](https://wearables.developer.meta.com/docs/getting-started-toolkit/) |
| Meta AI app | Must be installed on the phone; registration and permission flows depend on it | [AGENTS.md](https://github.com/facebook/meta-wearables-dat-android/blob/main/AGENTS.md) |
| Meta AI app version | v282 for SDK 0.9.0 | [Version dependencies](https://wearables.developer.meta.com/docs/version-dependencies) |
| Glasses firmware | v126 for Ray-Ban Meta on SDK 0.9.0 | same; [Known issues](https://wearables.developer.meta.com/docs/knownissues) |
| Developer Mode | Meta AI app → Settings → App Info → tap App version 5 times → toggle. Also per device: Settings → Your glasses → Developer Mode | [Setup](https://wearables.developer.meta.com/docs/getting-started-toolkit/) |
| Developer Center account | Requires a Meta Managed Account (MMA) organization created at work.meta.com; only MMA members can join the team | [Onboarding](https://wearables.developer.meta.com/docs/develop/dat/onboarding-and-organization-management) |
| App registration | `APPLICATION_ID` and `CLIENT_TOKEN` manifest meta-data. **In Developer Mode both can be `0`**; attestation is not used | [AGENTS.md](https://github.com/facebook/meta-wearables-dat-android/blob/main/AGENTS.md) |
| Manifest | `BLUETOOTH`, `BLUETOOTH_CONNECT`, `INTERNET`, plus a custom URL scheme intent-filter for the registration callback | same |

The Meta AI app must be installed but not foregrounded; one open 0.9.0 regression reports 5 to 15 s of stream lag that disappears when the Meta AI app process is force-stopped ([#162](https://github.com/facebook/meta-wearables-dat-android/issues/162)).

## 2. Capabilities on Ray-Ban Meta Gen 2 (non-display)

**Camera: live encoded video stream to the phone, plus stills.**
- `VideoQuality.HIGH` 720×1280, `MEDIUM` 504×896, `LOW` 360×640. Frame rate in {2, 7, 15, 24, 30}.
- Codec H.265/HEVC Annex-B with parameter sets inline in the first keyframe; `VideoFrame.isCodecConfig` is never true in practice, so csd-0 must be extracted from frame 1 ([#155](https://github.com/facebook/meta-wearables-dat-android/issues/155)).
- API shape (0.9.0): `Wearables.initialize()` → `createSession(AutoDeviceSelector())` → `session.start()` → `session.addCamera(StreamConfiguration(MEDIUM, 24))` → `camera.stream.start()` → collect `camera.stream.videoStream`. `addStream()` was removed in 0.9.0.
- `capturePhoto()` works only while the stream is STREAMING.
- Measured throughput on Android (Gen 1, S21 Ultra, 0.9.0): 652 Kbps at 9.6 fps; on-screen text unreadable by OCR ([#174](https://github.com/facebook/meta-wearables-dat-android/issues/174)). Android transport is Bluetooth Classic; iOS moved to Wi-Fi in 0.8, Android did not ([#159](https://github.com/facebook/meta-wearables-dat-android/issues/159)).
- First 5 to 10 s of a session are laggy ([#58](https://github.com/facebook/meta-wearables-dat-android/issues/58)).

**Microphone: no SDK API on Android.** Mic and speaker are reached through Bluetooth profiles: A2DP (stereo, output only) and HFP (8 kHz mono, bidirectional, beamformed). Meta's [mics-and-speakers doc](https://wearables.developer.meta.com/docs/develop/dat/microphones-and-speakers/) points Android developers at `AudioManager.mode = MODE_IN_COMMUNICATION` plus `setCommunicationDevice()`. The official CameraAccess sample records sound from the phone mic.

**Speakers: plain A2DP, so Android TextToSpeech works** when the camera is not streaming.

**Touch and button events: no public API.** Plan for zero on-glasses input.

**Session lifecycle.** `DeviceSessionState`: IDLE → STARTING → STARTED → PAUSED → STOPPING → STOPPED. `StreamState`: STOPPED → STARTING → STARTED → STREAMING → STOPPING → STOPPED → CLOSED. Sessions pause or stop when another experience takes the device, the user folds or doffs the glasses, Bluetooth drops, or access is revoked. Stopped sessions cannot be reused; recreate. Typed errors include `BATTERY_LOW`, `THERMAL_HOT`, `PEAK_POWER_LIMIT`, `TIMEOUT`. Historic hard timeouts (30 s in 0.4.0, about 3 min in earlier firmware, fixed in 0.6.0) mean a reconnect loop with about 500 ms delay is still wise ([#61](https://github.com/facebook/meta-wearables-dat-android/issues/61), [#76](https://github.com/facebook/meta-wearables-dat-android/issues/76)).

**LED:** the capture LED lights whenever the camera is active and cannot be defeated; tampering disables the camera ([9to5Google](https://9to5google.com/2026/07/07/meta-ray-ban-smart-glasses-privacy-light-camera-update/)). Practical streaming ceiling on battery and thermals: about 30 to 45 min.

## 3. Mock Device Kit

`mwdat-mockdevice`: `MockDeviceKit.getInstance(context).enable()`, `.pairGlasses(GlassesModel.RAYBAN_META)`, then `powerOn() / unfold() / don() / doff()`, permission overrides, and media replay: `device.services.camera.setCameraFeed(videoUri)` and `.setCapturedImage(imageUri)`. H.264 and H.265 video, JPEG and PNG images; Android does not transcode, so ship H.265. Also streaming simulation from the phone camera (0.6.0+). Works in instrumentation tests. No glasses, no Meta AI app, no Developer Center needed. ([AGENTS.md](https://github.com/facebook/meta-wearables-dat-android/blob/main/AGENTS.md), [Mock Device Kit docs](https://wearables.developer.meta.com/docs/develop/dat/mock-device-kit/))

## 4. Restrictions that matter

- **Friend's glasses:** yes, if their phone's Meta AI app has Developer Mode on (configured per linked device). Formal path: release channel invite; invitee email must already be a Meta Account ([Release channels](https://wearables.developer.meta.com/docs/develop/dat/set-up-release-channels)).
- **Blockers:** country restrictions for AI-glasses features; Developer Mode "may reset after app or firmware updates"; DAT install fails under 10% glasses battery or with Wi-Fi off; SDK and Developer Center version mismatches ([Known issues](https://wearables.developer.meta.com/docs/knownissues)).
- **Third-party computer vision:** not prohibited. The [Acceptable Use Policy](https://wearables.developer.meta.com/acceptable-use-policy) is generic, though it prohibits encouraging recording in sensitive locations (do not route through restrooms or labs on camera). SDK analytics and crash reporting are on by default; opt out with `ANALYTICS_OPT_OUT` / `CRASH_REPORTING_OPT_OUT` manifest meta-data.

## 5. Samples and community

- Official `samples/CameraAccess` in the repo: Compose app with registration, session, streaming, photo capture, video recording, an `HevcDecoder`, and an in-app MockDeviceKit debug menu. The repo ships a Claude Code plugin (`plugins/mwdat-android/skills/`) covering camera streaming, mock testing, session lifecycle, permissions, debugging (`install-skills.sh`). A docs MCP server exists at `https://mcp.developer.meta.com/wearables`.
- Community: [Intent-Lab/VisionClaw](https://github.com/Intent-Lab/VisionClaw), [Intent-Lab/GlassFlow](https://github.com/Intent-Lab/GlassFlow), [jacksonmafra-umain/spectra](https://github.com/jacksonmafra-umain/spectra), [safishamsi/FirstSight](https://github.com/safishamsi/FirstSight) (hackathon 3rd place, first-aid guidance on Ray-Ban Meta).
- Cautionary tale: [andrewwbuilds/hackmit26](https://github.com/andrewwbuilds/hackmit26) planned around Ray-Ban Meta, hit the constraints, and shipped with the phone in a chest harness, with an ADR titled "why the phone is on the chest instead of on Ray-Ban Meta."

## 6. Fallback: glasses as a plain Bluetooth headset

Pair in Android Settings. TTS out over A2DP usually lands on the glasses; to force it, find the `AudioDeviceInfo` of type `TYPE_BLUETOOTH_A2DP` with the glasses' product name and call `setPreferredDevice()` on an `AudioTrack` (synthesize TTS to file and play through your own `AudioTrack`, since `TextToSpeech` only exposes `setAudioAttributes`). Mic in over HFP: `MODE_IN_COMMUNICATION` plus `setCommunicationDevice()` (API 31+); `startBluetoothSco()` is deprecated from Android 13. Caveat: communication mode forces 8 kHz mono for everything including TTS and suspends A2DP; do not leave it on. ([AudioManager guide](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager))

Samsung One UI prioritises the Galaxy Watch for call audio when the watch handles calls, so headsets can lose the route; check Dual Audio and Auto switch. Meta's own troubleshooting advice is to disconnect other Bluetooth devices ([Meta help](https://www.meta.com/help/smart-glasses/articles/ray-ban-meta/bluetooth-connection-error-ray-ban-meta-smart-glasses/)).

## 7. Glasses plus watch on one phone

No toolkit-specific report for this pair, but the mechanism is real: both share one radio, and Samsung audio priority plus Auto switch will fight for the output route. The dominant measured problem is the toolkit's own camera/audio interlock, not the watch.

## Recommended integration approach

1. **Pre-event:** create the MMA org at work.meta.com and a Developer Center team (unbounded latency; start now); update the Meta AI app to v282+ and glasses firmware to v126+; enable Developer Mode and the per-device toggle on the actual Gen 2s. **Verify on day one that Developer Mode plus `application_id=0` bypasses Developer Center registration**, which is the mitigation if the org approval stalls.
2. **Hour 0 to 2:** build a MockDeviceKit-only skeleton (`mwdat-core`, `mwdat-camera`, `mwdat-mockdevice`, ids `0`) that replays a pre-recorded H.265 hallway walkthrough. Everyone develops against that all weekend.
3. **Design for burst capture, not continuous streaming.** Start the stream, let it stabilise about 10 s, grab frames or `capturePhoto()` for a short window, stop the camera, then speak. Never overlap frames and speech.
4. **Speech over A2DP with TextToSpeech**, only while the stream is stopped. Do not enter communication mode unless the glasses mic is truly needed.
5. **Voice input, if any, from the phone mic.**
6. **Reconnect loop** around session and stream; observe `session.state`, `stream.state`, `session.errors`, `stream.errorStream`; never reuse a stopped session.
7. **Decode correctly:** first NAL `0x40 0x01` is an HEVC VPS; pull csd-0 from the first keyframe; copy the sample's `HevcDecoder.kt`.
8. **Foreground service** for the streaming work so the demo survives screen-off.
9. **Plan B binary:** same app with a `MOCK` flag and a phone-camera mode.

## First 2 hours: verify
1. Version triple: Meta AI app v282+, firmware v126+, `mwdat 0.9.0`.
2. Registration reaches `REGISTERED` and `createSession` reaches `STARTED` with ids `0` and Developer Mode on.
3. Measure actual frame rate and latency at MEDIUM/24 on the demo phone, with the Meta AI app killed and with it running. Under 5 fps means redesign to single-photo capture immediately.
4. Prove the audio interlock: speak TTS while streaming, then after `camera.stop()`. Time the stop → speak → restart round trip; that is the UX budget per instruction.
5. Run the loop with the Galaxy Watch 8 Classic connected and confirm TTS lands on the glasses, not the watch.
