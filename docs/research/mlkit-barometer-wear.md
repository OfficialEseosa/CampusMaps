# Research: ML Kit OCR, anchor recognition alternatives, barometer, Wear OS 6, TTS, routing (2026-09-20)

Compiled by a research agent with web sources. Claims carry links; anything without a link is the agent's inference.

## 1. ML Kit Text Recognition v2

**Artifacts.** Bundled: `com.google.mlkit:text-recognition:16.0.1` (Latin; last released 2024-08-07, stable). Unbundled via Play Services: `com.google.android.gms:play-services-mlkit-text-recognition:19.0.1`. Min API 21. ([release notes](https://developers.google.com/ml-kit/release-notes), [Android guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android))

**Bundled vs unbundled.** Bundled adds about 4 MB per architecture and works the instant the app installs. Unbundled downloads the model on first use, so the first OCR call can stall. **Use bundled for a demo.** ([installation paths](https://developers.google.com/ml-kit/tips/installation-paths))

**Latency.** Latin OCR on a 1280×720 frame on a current flagship: roughly 60 to 120 ms per frame, so 5 to 10 fps of analysis is realistic. Throttle; do not OCR at 30 fps.

**Input guidelines.** Each character at least 16×16 px, ideally no larger than about 24×24 px. Recommended formats YUV_420_888 (Camera2) or NV21. Use CameraX `STRATEGY_KEEP_ONLY_LATEST` and drop frames while a `process()` is in flight.

**Practical settings:** analyse at 1280×720 (1920×1080 if signage is small or far), cap at 5 to 8 fps, crop a centre region of interest before building the `InputImage`; cropping to about half the frame roughly halves latency and removes distractor text.

**Non-CameraX frames.** `InputImage.fromBitmap(bitmap, rotationDegrees)` for a decoded JPEG stream; `InputImage.fromByteArray(nv21, w, h, rotation, IMAGE_FORMAT_NV21)` for raw NV21; `fromByteBuffer` likewise; `fromMediaImage` for Camera2/CameraX only. Rotation must be the degrees needed to make the image upright; getting it wrong is the top cause of empty OCR results.

**Matching noisy OCR to room numbers.**
1. Regex pre-filter on each `Text.Element`: `^([A-Z]{1,4}\s?-?)?\d{3,4}[A-Za-z]?$`. Normalise: uppercase, strip whitespace and hyphens, apply a confusion map (`O→0, I/l→1, S→5, B→8, Z→2`).
2. Fuzzy match against the building's room list with Levenshtein; allow distance 1 for a 3-digit room, 1 to 2 for "CS305". `org.apache.commons:commons-text` or 20 lines of DP. ([post-OCR survey](https://dl.acm.org/doi/fullHtml/10.1145/3453476))
3. ML Kit `confidence` is nullable (often null on the Play Services variant). Gate on bounding-box height (at least about 20 px) instead.
4. Temporal voting: ring buffer of the last 5 to 8 frames; accept a room id only when the same canonical id wins 3 of the last 5, then lock out for about 3 s. Voting is the biggest accuracy lever (92% to 98% in published experiments).

## 2. Recognising signage from a low-quality stream

| Option | Setup effort | Robustness | Verdict |
|---|---|---|---|
| MediaPipe Image Embedder + cosine nearest neighbour | 2 to 3 h. `com.google.mediapipe:tasks-vision`, MobileNet-V3 `.tflite`, embed the anchor photos at startup, compare each frame. No training. | Good for "which poster am I looking at"; degrades with heavy crop or occlusion. Single-digit ms. | **Recommended** fallback recogniser ([guide](https://ai.google.dev/edge/mediapipe/solutions/vision/image_embedder/android)) |
| ML Kit Image Labeling custom model | 4 to 6 h plus training | Fixed class set | Only for the tidy API |
| LiteRT / Model Maker classifier | 6 to 10 h incl. Colab | 20 images per class overfits | Skip |
| OpenCV ORB/SIFT (`org.opencv:opencv` 5.0.0 on Maven Central) | 6 to 8 h | Excellent for planar textured posters, gives homography; brittle on blur | Fallback if the embedder confuses similar posters |

Rule: embedder plus nearest neighbour with a cosine threshold around 0.75 to 0.85 (tuned on site) plus the same N-of-M voting. Combine with OCR: if OCR reads a room number, trust it; otherwise fall back to poster retrieval.

## 3. Barometer floor detection

- `SensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)`; hPa. `SENSOR_DELAY_NORMAL` (about 5 Hz) or `SENSOR_DELAY_UI`; Samsung flagships report about 0.01 hPa resolution.
- Physics: about 0.12 hPa per metre; a 3.5 to 4 m storey is 0.42 to 0.48 hPa. Stair climbing produces 0.06 to 0.18 hPa over about 2 s. ([Geo-spatial Information Science](https://www.tandfonline.com/doi/full/10.1080/10095020.2019.1631573))
- Filtering: moving average or EMA over 1 to 2 s (alpha 0.1 to 0.2), detect transitions on the slope. Published methods classify same-floor / stairs / elevator using standard deviation plus slope. ([ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S014036641630041X), [arXiv 1607.00363](https://arxiv.org/pdf/1607.00363))
- Stairs vs elevator: elevator is fast, monotonic, near-constant slope with near-zero step energy; stairs are slower, noisier, with step cadence from `TYPE_STEP_DETECTOR`. Fuse the two.
- Pitfalls: absolute pressure is meaningless (device-to-device offset up to 2 hPa, about 5 floors); weather drift over 30 min is comparable to one floor, so re-zero whenever a known floor is confirmed; HVAC, doors, revolving doors cause 0.1 to 0.3 hPa steps; picking the phone up is worth up to 0.2 hPa. ([Sensors 19:3622](https://doi.org/10.3390/s19163622), [Sci Rep 2024](https://www.nature.com/articles/s41598-024-64824-9))
- Galaxy Watch 8 Classic: has a hardware barometer ([GSMArena](https://www.gsmarena.com/samsung_galaxy_watch8_classic-13998.php)) exposed through `SensorManager` to Wear apps ([Samsung Developer](https://developer.samsung.com/sdp/blog/en/2022/05/25/check-which-sensor-you-can-use-in-galaxy-watch-running-wear-os-powered-by-samsung)). Verify on the device; sampling behaves differently with the screen off.

## 4. Wear OS 6 companion

- Stack: Compose for Wear OS with `androidx.wear.compose:compose-material3:1.5.0` (first stable, Aug 2025; 1.7.0-beta02 as of Aug 2026; stick to stable). ([releases](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3))
- Horologist: Google now says do not use its Composables, Compose Layout, or Compose Material; use M3 `AppScaffold`/`ScreenScaffold`. Horologist DataLayer helpers are still useful. ([Horologist](https://google.github.io/horologist/datalayer/), [wear-os-samples PR #1387](https://github.com/android/wear-os-samples/pull/1387))
- Phone to watch: `MessageClient` for turn-by-turn events (RPC, under 100 KB, no persistence, no retry, tens to hundreds of ms over Bluetooth); `DataClient` for persistent shared state like the current route (syncs, survives disconnects); `ChannelClient` only for streams. **MessageClient for "next instruction", DataClient for "current route".** ([overview](https://developer.android.com/training/wearables/data/overview))
- Hard requirement: phone and watch APKs share the same application id and signing key, debug builds included, or they never see each other. Use `CapabilityClient` plus a `wear.xml` capability to discover the node.
- Install to Galaxy Watch 8 Classic: Settings → About watch → Software → tap build 5 times; Developer options → ADB debugging + Wireless debugging + "turn off automatic Wi-Fi"; same Wi-Fi as the laptop; `adb pair IP:port`, code, `adb connect IP:port`. ([Samsung](https://developer.samsung.com/sdp/blog/en/2024/04/30/connect-galaxy-watch-to-android-studio-over-wi-fi), [Android](https://developer.android.com/training/wearables/get-started/debug-wifi))
- Haptics: `Vibrator` + `VibrationEffect`. Check `hasAmplitudeControl()`; without it any non-zero amplitude becomes 100%. `createPredefined(EFFECT_CLICK / EFFECT_DOUBLE_CLICK)` for confirmations, `createWaveform(timings, amplitudes, -1)` for directional patterns. Start and end waveforms at amplitude 0. ([haptics APIs](https://developer.android.com/develop/ui/views/haptics/haptics-apis))
- Keeping the screen up: Ongoing Activity API plus `AmbientLifecycleObserver` (not the deprecated `AmbientModeSupport`). ([ongoing activity](https://developer.android.com/training/wearables/notifications/ongoing-activity), [always-on](https://developer.android.com/training/wearables/always-on))
- Wear OS 6 changes that break old tutorials: target API 36; in ambient the top activity stays RESUMED (old `onPause`-based ambient code is wrong); `BODY_SENSORS` replaced by granular health permissions; tile events are batched; new watches only support Watch Face Format. ([behavior changes](https://developer.android.com/training/wearables/versions/6/changes))

## 5. TextToSpeech

- First-utterance latency is engine bind plus voice load: 300 ms to 1.5 s to `onInit`, then more on the first `speak()`. Warm up at app start on a background thread, set the locale, speak a blank utterance so the voice is resident. Afterwards utterances start in tens of ms.
- `QUEUE_FLUSH` for navigation; a new instruction cancels a stale one. Always pass an `utteranceId` and use `UtteranceProgressListener` to sync watch haptics with speech end.
- Routing: `setAudioAttributes(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, CONTENT_TYPE_SPEECH)`. Routes to A2DP Bluetooth like media, with correct ducking. Avoid `startBluetoothSco()` unless the headset path is specifically needed; flaky on Samsung.
- Engine: Galaxy phones default to Samsung TTS, slower and more robotic than Google TTS. Enumerate `getEngines()` and construct with `com.google.android.tts` if present; install Google TTS on the demo phone beforehand. ([engine guide](https://accessibleandroid.com/tts-engines-on-android-past-present-and-future/))
- Android 16: no new on-device TTS API; audio side adds Auracast/LE-Audio routing; `AudioManager.setCommunicationDevice()` (API 31+) remains the modern routing API. ([AOSP notes](https://source.android.com/docs/whatsnew/android-16-release))

## 6. Routing data

- Do not add a graph library. Dijkstra with a `PriorityQueue` is 60 to 80 lines.
- Alternatives: the penalty method (run Dijkstra, multiply the weight of every edge on the returned path by about 1.2, repeat until K distinct paths) gives genuinely different routes; k-shortest-paths yields near-identical variants. ([Improved Alternative Route Planning](https://drops.dagstuhl.de/storage/01oasics/oasics-vol033-atmos2013/OASIcs.ATMOS.2013.108/OASIcs.ATMOS.2013.108.pdf), [Alternative Routes](https://arxiv.org/pdf/1002.4330)). Note from the main plan: for CampusMaps the entrance × vertical-method enumeration in [../04-routing.md](../04-routing.md) is even simpler and more explainable; the penalty method is the backup if enumeration yields too few options.
- JSON: kotlinx.serialization (compiler plugin, no reflection, sealed-class support). Moshi only if already deep in Retrofit. ([comparison](https://www.jonker.co.nz/posts/switching-to-kotlinx-serialization/))
- Add a fixed floor-change penalty so routes do not ping-pong between floors.

## Recommended stack

| Library | Version | Why |
|---|---|---|
| `com.google.mlkit:text-recognition` | 16.0.1 (bundled) | No first-run download |
| `com.google.mediapipe:tasks-vision` | latest 0.10.x | Poster recognition with zero training (fallback) |
| `org.apache.commons:commons-text` | 1.13.x | Levenshtein for OCR matching (or hand-written) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.8.x | Building graph JSON |
| `androidx.wear.compose:compose-material3` | 1.5.0 | Wear OS 6 UI |
| `androidx.wear:wear-ongoing`, `androidx.wear:wear` | latest stable | Ongoing activity, ambient |
| `com.google.android.gms:play-services-wearable` | 19.x | MessageClient / DataClient / CapabilityClient |
| `androidx.camera:camera-*` | 1.4.x | `KEEP_ONLY_LATEST` backpressure for OCR frames (phone fallback path) |
| Platform `SensorManager`, `Vibrator`, `TextToSpeech` | | No wrappers |
| (fallback) `org.opencv:opencv` | 5.0.0 | Only if the embedder cannot separate similar posters |

## Gotchas
1. `InputImage` rotation wrong gives silent empty OCR results.
2. Unbundled ML Kit model not downloaded at demo time; use bundled.
3. Barometer absolute values are useless; calibrate per floor and re-zero.
4. Phone and Wear APKs must share package name and signing key.
5. Wear OS 6 keeps the activity RESUMED in ambient; target API 36.
6. Samsung TTS is the Galaxy default; install and select Google TTS.
7. `hasAmplitudeControl()` false collapses waveform amplitudes to 100%.
8. ML Kit `confidence` is often null; gate on box height and voting.
9. Horologist Compose UI is deprecated guidance.
10. "312" appears on door plates, fire maps, and directories; a single frame lies. Vote.
11. `MessageClient` has no retry; mirror critical state in `DataClient`.

## Must test before the event
1. Wi-Fi ADB install to the watch end-to-end, on the venue Wi-Fi if possible (client isolation breaks pairing; have a phone hotspot ready).
2. Barometer walk test in the real building: stairs and elevator, per-floor delta, drift over 30 minutes.
3. OCR on the real signage at real distances and lighting; measure character pixel height against the 16 px floor.
4. Phone to watch latency and reconnect after walking out of range.
5. Full audio chain: TTS warm-up, A2DP routing to the glasses, ducking.
