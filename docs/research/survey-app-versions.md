# Research: CampusSurvey pinned versions and gotchas (verified 2026-09-21)

Compiled by a research agent that fetched each version's POM from Google Maven or Maven Central. Applies to the survey app only; CampusMaps has its own pins in [../14-tech-stack.md](../14-tech-stack.md).

## Toolchain
- **AGP 8.13.2**, not 9.x: AGP 9.0 needs Gradle 9.1 and 9.4 needs Gradle 9.6; the cached Gradle 8.14 caps us at 8.13.x. AGP 8.13 supports compileSdk 36 and build-tools 36.0.0. JDK 17+ required; the installed Temurin 21 is fine. ([AGP compatibility](https://developer.android.com/build/releases/gradle-plugin))
- **Kotlin 2.4.20**: compatible with Gradle 7.6.3 to 9.7.0 and AGP 8.5.2 to 9.3.1. The Compose compiler plugin and serialization plugin versions equal the Kotlin version. Runtime `kotlinx-serialization-json` 1.11.0. ([KGP table](https://kotlinlang.org/docs/gradle-configure-project.html))
- **Compose BOM 2026.09.00**: ui 1.12.1, material3 1.4.0, material-icons-extended 1.7.8 (frozen). The BOM does not manage activity, lifecycle, or navigation: activity-compose 1.13.0, lifecycle 2.11.0, navigation-compose 2.10.1, core-ktx 1.19.0.
- **CameraX 1.6.2**: core, camera2, lifecycle, view, video. `camera-compose` with `CameraXViewfinder` is stable at 1.6.2; `PreviewView` inside `AndroidView` still works and is what the survey app uses. ([CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera))
- **ML Kit text-recognition 16.0.1** (bundled, 16 KB page aligned). Boxes: `Text.textBlocks → lines → elements → symbols`, each with `boundingBox`; boxes are in the rotated image space. ([guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android))
- **play-services-location 21.4.0**: `LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 1000L)`. `altitude` is WGS84 ellipsoid, not MSL; `hasMslAltitude()` exists from API 34.
- exifinterface 1.4.2.

## Gotchas
1. `ACTIVITY_RECOGNITION` is a runtime permission on API 29+ and is required to register the step detector.
2. Edge-to-edge is mandatory at targetSdk 36; call `enableEdgeToEdge()` and consume insets.
3. Predictive back is on by default at targetSdk 36; use `BackHandler`.
4. Compose + CameraX: bind once in a lifecycle-aware effect, `unbindAll()` on dispose; rebinding on every recomposition gives a black preview. Keep `ImageCapture` in `remember`.
5. Samsung ImageCapture output carries EXIF orientation rather than rotated pixels; read `ExifInterface` before measuring anything in pixels.
6. `getExternalFilesDir` needs no permission; share zips through `FileProvider` with `ACTION_SEND`; no storage permissions on Android 13+.
7. Accompanist permissions is deprecated; use `rememberLauncherForActivityResult(RequestMultiplePermissions())`. Request coarse and fine location together.
8. Samsung barometers deliver about 5 to 25 Hz; do not expect more.
9. Robolectric 4.17 supports SDK 36 but is not worth it for a one-day build; JVM tests cover the pure logic.

## Emulator smoke test (Windows)
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n survey36 -k "system-images;android-36;google_apis_playstore;x86_64" -d pixel_7 --force
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd survey36 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
adb wait-for-device
.\gradlew.bat installDebug
```
