# 14. Tech Stack and Versions

Chosen from the research in [research/](research/) on 2026-09-20. **Verify every version against Maven Central and Google's Maven the morning the build starts, then lock them in the version catalog and do not upgrade during the event.** A test project should verify this set compiles together.

## Build

| Piece | Version | Why |
|---|---|---|
| Android Studio | current stable | |
| Android Gradle Plugin | 9.4.0 | Current stable, supports API 37 |
| Kotlin | 2.4.10 | Matches SceneView's stdlib |
| compileSdk / targetSdk | 37 / 37 | ARCore 1.56 forces target 37 through manifest merge anyway |
| minSdk (phone) | 24 | Compose floor; the demo phone is Android 16 |
| Wear module target | 36 | Wear OS 6 |
| JVM target | 17 | |
| Compose BOM | 2026.09.00 | Matches SceneView 4.38.0 |
| Serialization | kotlinx.serialization 1.8.x | Compiler plugin, no reflection, sealed classes |

## Phone app dependencies

| Library | Version | Module | Why |
|---|---|---|---|
| `io.github.sceneview:arsceneview` | 4.38.0 | AR guidance | Compose `ARScene`, GLB loading, augmented images, maintained |
| `com.google.ar:core` | 1.54.0 (transitive) | Localization | What SceneView is tested against; do not force 1.56 |
| `com.google.mlkit:text-recognition` | 16.0.1 (bundled) | Localization, glasses | On-device OCR, no first-run download |
| `com.meta.wearable:mwdat-core`, `mwdat-camera`, `mwdat-mockdevice` | **0.7.0** (was 0.9.0; the glasses run DWA 0.7 and refuse 0.9 and 1.0, verified on the real glasses 2026-09-25, see 06) | Glasses bridge | Official toolkit, Maven Central |
| `com.google.android.gms:play-services-wearable` | 19.x | Watch | MessageClient, DataClient, CapabilityClient |
| `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle` | 1.4.x | Glasses fallback | Phone camera frames for OCR when the glasses stream is not used |
| `androidx.navigation:navigation-compose` | current stable | App shell | |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | current stable | App shell | |
| Platform `SensorManager`, `TextToSpeech`, `AudioManager` | | Barometer, speech | No wrappers |
| (optional) `com.google.android.gms:play-services-location` | 21.x | Geospatial leg | Only if the outdoor leg stays |
| (plan B) `com.google.mediapipe:tasks-vision` | 0.10.x | Localization | Image embedder for poster retrieval if OCR fails on glasses frames |
| (plan B) `org.apache.commons:commons-text` | 1.13.x | Localization | Levenshtein; or 20 lines by hand |

## Wear app dependencies

| Library | Version | Why |
|---|---|---|
| `androidx.wear.compose:compose-material3` | 1.5.0 | Stable Wear M3 |
| `androidx.wear.compose:compose-foundation` | matching | |
| `androidx.wear:wear-ongoing` | current stable | Ongoing activity |
| `androidx.wear:wear` | current stable | `AmbientLifecycleObserver` |
| `com.google.android.gms:play-services-wearable` | 19.x | Same as phone |

## Tools (not shipped)

| Tool | Use |
|---|---|
| `arcoreimg.exe` from the ARCore SDK 1.56.0 download | `eval-img` to score anchor photos; `build-db` to build `.imgdb` files offline |
| ffmpeg | Convert glasses footage to H.265 for the Mock Device Kit |
| Blender or a CC0 model | `arrow.glb`, `pin.glb` |
| Android Studio Logcat filtered to the app | The only acceptable error report format |

## Project layout (one Gradle project, two modules)

```
CampusMaps/
  app/                      phone app, applicationId com.campusmaps
    src/main/assets/buildings/{KL,CS,CSE}.json
    src/main/assets/anchors/{KL,CS,CSE}.imgdb and reference photos
    src/main/assets/models/arrow.glb, pin.glb
    src/main/java/com/campusmaps/
      data/        building model, loader, validator          (02)
      localization/ ARCore image path, OCR path, barometer   (03)
      routing/     graph, Dijkstra, alternatives, instructions (04)
      ar/          SceneView screen, ribbon, marker, minimap  (05)
      glasses/     toolkit session, burst loop, TTS          (06)
      ui/          screens, view model, debug overlay        (07)
      watch/       Data Layer sender                         (08)
  wear/                     Wear OS app, same applicationId, same signing
  docs/                     this folder (or keep it at the root)
```

`routing/` and `data/` have no Android dependencies so their unit tests run on the JVM in seconds.

## Things not in the stack, and why

- Unity or Unreal: a native Kotlin app integrates the glasses toolkit and Wear OS far more easily, and the team is writing Kotlin.
- Sceneform, `ArFragment`: archived.
- ARCore for Jetpack XR: developer preview on phones, no augmented images.
- Cloud Anchors, any backend, accounts: network in the demo path.
- Graph libraries: Dijkstra by hand is shorter than the import.
- OpenCV: only if the MediaPipe embedder cannot separate similar posters; it is a native library and costs hours to set up.
- Horologist UI components: Google now advises M3 components instead.

## Hour 0 verification (2026-09-25)

Every version below was checked against Google Maven / Maven Central metadata on 2026-09-25 and pinned in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). `./gradlew :core:build :app:assembleDebug` is green with this set and the debug APK launches on the `survey36` emulator (API 36). **Do not upgrade anything until after the event.**

| Piece | Pinned | Notes |
|---|---|---|
| Gradle wrapper | 9.6.1 | AGP 9.4 needs Gradle 9.6+ |
| AGP | 9.4.0 | 9.4.1 exists; not taken. Built-in Kotlin: no `org.jetbrains.kotlin.android` plugin in `app/` |
| Kotlin (KGP, Compose compiler, serialization plugin) | 2.4.10 | Matches SceneView 4.38.0's stdlib |
| JDK | Temurin 21 runs Gradle; bytecode target 17 | |
| compileSdk / targetSdk | 37 / 37 | SDK package `platforms;android-37.0` + `build-tools;37.0.0` installed with sdkmanager |
| **minSdk** | **29 (was 24)** | **Deviation.** `mwdat-camera:0.9.0` declares minSdk 29 and the manifest merger fails at 24: `uses-sdk:minSdkVersion 24 cannot be smaller than version 29 declared in library [com.meta.wearable:mwdat-camera:0.9.0]`. Demo phone is Android 16, so no cost. |
| Compose BOM | 2026.09.00 | Resolves ui 1.12.1, material3 1.4.0 |
| activity-compose | 1.13.0 | |
| lifecycle-runtime-compose, lifecycle-viewmodel-compose | 2.11.0 | |
| navigation-compose | 2.10.2 | |
| core-ktx | 1.19.1 | |
| kotlinx-serialization-json | 1.11.0 | Doc said 1.8.x; 1.11.0 is the stable that matches Kotlin 2.4 |
| kotlinx-coroutines | 1.11.0 | core in `:core`, android in `:app` |
| JUnit | 4.13.2 (+ kotlin-test-junit 2.4.10) | `:core` tests run with `useJUnit()` |
| arsceneview | 4.38.0 | 4.39.0 exists; not taken. Pulls ARCore 1.54.0 and Filament 1.72.1 |
| ML Kit text-recognition | 16.0.1 (bundled) | |
| play-services-wearable | 19.0.0 | 20.0.1 exists; doc said 19.x |
| mwdat-core / camera / mockdevice | 0.9.0 | **1.0.0 is now on Maven Central** and the toolkit's AGENTS.md on `main` documents 1.0.0; API may differ from 0.9.0 samples. Kept 0.9.0 per plan. |
| CameraX core, camera2, lifecycle, view | 1.5.3 | Latest 1.5.x; 1.6.2 is stable too |

Nothing was dropped. Debug APK is about 147 MB (Filament, ML Kit bundled model, all ABIs); fine for sideloading.

Manifest: ARCore meta-data is `optional` (overrides SceneView's `required`) so the app runs on the emulator; Meta toolkit meta-data names are `com.meta.wearable.mwdat.APPLICATION_ID` / `CLIENT_TOKEN`, filled from `manifestPlaceholders` (`"0"` in Developer Mode); `MainActivity` carries a `campusmaps://` VIEW intent filter for the Meta AI registration callback.

Layout differs from the tree above: pure-JVM code (`data/`, `routing/`) lives in the `:core` Gradle module (`core/src/main/kotlin/com/campusmaps/...`, Kotlin/JVM, no Android); `:app` depends on it with `implementation(project(":core"))` and keeps Kotlin sources under `app/src/main/kotlin/com/campusmaps/`.

Build and install:
```bash
./gradlew :core:build :app:assembleDebug        # Git Bash; gradlew.bat from PowerShell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.campusmaps/.MainActivity
```

Emulator check (2026-09-25): `survey36` (API 36, `emulator-5554`) installs the debug APK, `MainActivity` cold-starts in about 9 s under swiftshader and shows "Skeleton build OK v0.1.0 (1)"; no `AndroidRuntime` errors in Logcat. A "System UI isn't responding" dialog can appear right after a cold emulator boot; tap Wait, it is the emulator, not the app.

## Integration branch (teammate base, 2026-09-25)

The `integration` branch uses the teammate's version catalog (`gradle/libs.versions.toml`), not the table above. Minimal bumps made while porting Raphael's code onto it, each forced by a dependency, nothing else upgraded:

| Piece | Teammate pin | Integration | Why |
|---|---|---|---|
| Kotlin (and the compose / serialization / jvm plugins) | 2.2.21 | **2.4.10** | SceneView 4.38.0 and its kotlin-stdlib 2.4.10 carry Kotlin 2.4 metadata; 2.2 cannot read them |
| `app` compileSdk | 36 | **37** | SceneView 4.38.0 AAR metadata requires compileSdk 37. targetSdk stays 36; AGP stays 9.1.0 (it warns that 37 is above its tested maximum; the build is green) |
| `io.github.sceneview:arsceneview` | (none) | 4.38.0 | Raphael's world-locked AR layer. ARCore resolves to the teammate's 1.56.0 |
| `com.meta.wearable:mwdat-core/camera/mockdevice` | (none) | 0.7.0 | Glasses toolkit, the version DWA 0.7 accepts (06). Compiles; not yet called (GlassesLink is still simulated) |
| `kotlin-test`, `kotlin-test-junit` | (none) | = Kotlin | `core` tests |
| `maps-compose` | 8.2.2 | removed | Outdoor leg is ARCore Geospatial per the plan (owner decision) |

Compose BOM 2026.04.01, AGP 9.1.0, coroutines 1.10.2, serialization 1.9.0, Gradle 9.3.1 are the teammate's and build with the above.

## Launch splash (2026-09-25)

| Piece | Version | Why |
|---|---|---|
| `androidx.core:core-splashscreen` | 1.2.0 | System launch splash on Ink (no white flash), backported to minSdk 29. Used by `MainActivity.installSplashScreen()` and `ui/splash`. |
