# Research: ARCore stack for CampusMaps (2026-09-20)

Compiled by a research agent with web sources. Claims carry links; anything without a link is the agent's inference. Verify the version numbers against Maven the day the build starts.

## 1. ARCore SDK, toolchain, device

**Latest `com.google.ar:core` = 1.56.0**, published 2026-09-04 ([releases](https://github.com/google-ar/arcore-android-sdk/releases), [maven-metadata](https://dl.google.com/dl/android/maven2/com/google/ar/core/maven-metadata.xml); 1.55.0 was never published). 1.56.0's only breaking change: `targetSdkVersion` bumped to API 37, which merges into the manifest if the app does not set its own; it also added `AcquireDepthImageMeters` / `AcquireRawDepthImageMeters` ([1.56.0 notes](https://github.com/google-ar/arcore-android-sdk/releases/tag/v1.56.0)).

Recommended build setup: AGP 9.4.0 (current stable, supports API 37) ([AGP 9.4 notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes)), Kotlin 2.4.x (SceneView 4.38.0 ships against kotlin-stdlib 2.4.10), compileSdk 37, targetSdk 37, minSdk 24, JVM target 17+, Compose BOM 2026.09.00 (what SceneView 4.38.0 imports).

**Galaxy S25 Ultra**: on the official [ARCore supported devices](https://developers.google.com/ar/devices) list, annotated "Supports Depth API" (software depth-from-motion, no ToF). Geospatial: not flagged as unsupported, but gate at runtime with `Session.checkGeospatialModeSupported(GeospatialMode.ENABLED)` ([enable guide](https://developers.google.com/ar/develop/java/geospatial/enable)).

## 2. Augmented Images

From the [overview](https://developers.google.com/ar/develop/augmented-images) and [Java guide](https://developers.google.com/ar/develop/java/augmented-images/guide):

- PNG/JPEG, at least 300×300 px (more resolution does not help); only greyscale is used.
- `arcoreimg eval-img` score 0 to 100; use 75 or higher. Avoid repetitive patterns, heavy JPEG artefacts, barcode-like geometry.
- Image must fill at least 25% of the camera frame to be *initially* detected; must be flat, unobstructed, not at an extreme angle.
- Up to 20 images tracked simultaneously; no two instances of the same image at once. Database up to 1,000 images, about 6 KB each; one active database per session; runtime `addImage` costs about 30 ms per image.
- Supplying the physical width in metres speeds detection and gives an immediate pose. Helps most for images over 75 cm.
- Tracking method: for fixed images (wall signage) the pose is valid whenever `trackingState == TRACKING`; ignore `FULL_TRACKING` vs `LAST_KNOWN_POSE` unless the image moves.
- Offline .imgdb build on Windows: ship `arcoreimg.exe` from the SDK and run
  `arcoreimg.exe build-db --input_image_list_path=list.txt --output_db_path=campus.imgdb`
  where `list.txt` lines are `name|path|width_metres` ([arcoreimg](https://developers.google.com/ar/develop/augmented-images/arcoreimg)). Put the file in `assets/` and load once at session config.
- Practical detection latency: typically well under a second once the sign fills a quarter of the frame and the width is supplied. Budget "walk up, hold steady about 1 s".

Signage best practice: matte prints (gloss and glass reflections kill features), high local contrast, text-heavy plaques score well, logos on flat colour score badly. Avoid two plaques that differ only by a small room number; descriptors collide.

## 3. Geospatial API

Requires a Google Cloud project with the ARCore API enabled, then keyless (OAuth/signing certificate) auth or an API key; add `play-services-location`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`, set `Config.GeospatialMode.ENABLED`, gate with `checkGeospatialModeSupported` ([enable](https://developers.google.com/ar/develop/java/geospatial/enable)). Network-dependent: VPS localisation is a server call.

Anchors ([guide](https://developers.google.com/ar/develop/java/geospatial/anchors)): `Earth.createAnchor(lat, lng, alt, q)`; `resolveAnchorOnTerrainAsync(...)`; `resolveAnchorOnRooftopAsync(...)`. Terrain and Rooftop anchors are preferred because altitude comes from Google's data. Gate on `earth.trackingState == TRACKING` and on accuracy; the samples use thresholds of 10 m horizontal and 15 degrees yaw before placing anything.

Verdict for downtown Atlanta: outdoors, VPS coverage in a dense US downtown with Street View is very likely; confirm at runtime with `checkVpsAvailability(lat, lng)` ([VPS availability](https://developers.google.com/ar/develop/java/geospatial/check-vps-availability)). Indoors it is the wrong tool. Use Geospatial only for the outdoor "walk to this entrance" leg, off the critical path.

## 4. Rendering

Options: SceneView `io.github.sceneview:arsceneview` 4.38.0 (released 2026-09-20; active, Compose-native, stable for Android AR) ([repo](https://github.com/SceneView/sceneview-android), [releases](https://github.com/SceneView/sceneview-android/releases)); Filament direct; raw GLES with `hello_ar_kotlin` (still in the SDK, alongside `augmented_image_java`, `geospatial_java`, `ml_kotlin`).

**Recommendation: SceneView 4.38.0 with the `ARScene` composable.** `ARScene(...) { AnchorNode(...) { ModelNode("arrow.glb") } }`, built-in augmented-image support, GLB loading, depth occlusion (fixed in 4.30.0), gestures, ARCore install and permission handling. Raw GLES means writing shaders, the camera background pass, and matrix plumbing: a full hackathon day by itself.

Pitfalls: (a) `arsceneview:4.38.0` pins `com.google.ar:core:1.54.0`, not 1.56.0; take 1.54.0 transitively. (b) SceneView minor versions have broken API between 2.x and 4.x; pin an exact version. (c) Targets compileSdk 37 as of 4.34.0; mismatched AGP fails the build. (d) Compose/Filament surface lifecycle bugs appear if `ARScene` sits inside a nav-graph destination that recomposes heavily; keep the AR screen a single top-level destination.

## 5. Placing content from an Augmented Image pose

`AugmentedImage.getCenterPose()`: +Y is the normal out of the image plane, +X is left to right across the image, +Z is top to bottom down the image ([API ref](https://developers.google.com/ar/reference/java/com/google/ar/core/AugmentedImage)); `extentX`/`extentZ` are width and height in metres. ARCore world is right-handed, +Y up, gravity-aligned.

Full form: with the plaque's pose in building coordinates `T_B_image`, `T_world_B = T_world_image · (T_B_image)⁻¹`, and any waypoint `p_B` renders at `p_world = T_world_B · p_B`. In code: `Pose.inverse()`, `Pose.compose()`, `Pose.transformPoint()`.

**Yaw-only shortcut (recommended).** Both frames share gravity-up, so the unknown is a 2-D rigid transform: yaw θ plus translation.
1. World normal `n = centerPose.rotateVector(0,1,0)` (horizontal for a wall poster).
2. `yaw_world = atan2(n.x, n.z)`; `yaw_B` for that wall comes from the floor plan. `θ = yaw_world − yaw_B`.
3. `T_world_B = translate(p_world_image − Ry(θ)·p_B_image) · Ry(θ)`, using horizontal components; take floor Y from a detected plane or from `p_world_image.y − plaqueHeightAboveFloor`.

Gotchas: for a vertical plaque, image +Z points down (world −Y); reusing the image pose naively for a floor arrow puts it face-down. A rotationally ambiguous plaque can localise 180 degrees off. Never read `centerPose` before `TRACKING`. Anchor content via `augmentedImage.createAnchor(pose)` so it inherits ARCore's corrections. Only trust `extentX/Z` if you did not supply the width.

## 6. Drift and re-anchoring

Published benchmarks put good VIO under about 1% of distance travelled, but ARCore showed a final drift of 3.98 m in one four-system benchmark, behind ARKit's 0.19 m ([Sensors 22:9873](https://doi.org/10.3390/s22249873); [arXiv:2207.06780](https://arxiv.org/pdf/2207.06780)). Planning number: a few tens of centimetres of position error and 1 to 3 degrees of yaw over 10 to 20 m of corridor; worse across a turn, a featureless wall, or an elevator. Yaw is what hurts: 2 degrees over 20 m is about 0.7 m of lateral arrow offset.

Strategy: marker-relative re-localisation. A plaque at every decision point (junctions, stairwells, lift lobbies); on each new detection recompute `T_world_B` and re-snap the whole route. Smooth the correction (lerp over about 300 ms) and reject a correction whose yaw disagrees with the current estimate by more than about 20 degrees. Cloud Anchors are out (network, hosting step).

Depth API for occlusion: skip for the demo. One-flag experiment in SceneView, but software depth on a moving handset is noisy at corridor range and a partially eaten ribbon looks worse than no occlusion.

## 7. 2026 landscape

- No deprecation of Augmented Images in 1.53 to 1.56 release notes.
- Sceneform is dead: archived 2020; the community fork `SceneView/sceneform-android` was archived 2026-03-27. Ignore every tutorial that uses `ArFragment`.
- ARCore for Jetpack XR reached 1.0.0-beta02 in Aug 2026 ([blog](https://android-developers.googleblog.com/2026/08/jetpack-xr-sdk-core-libraries-beta.html)); its phone path is developer preview and has no augmented images ([mobile guide](https://developer.android.com/develop/xr/jetpack-xr-sdk/arcore/mobile)). Not for this hackathon.
- ML Kit custom labelling or MediaPipe Image Embedder ([MediaPipe](https://ai.google.dev/edge/mediapipe/solutions/vision/image_embedder/android), [ML Kit](https://developers.google.com/ml-kit/vision/image-labeling/custom-models/android)) give identity only, no 6-DoF pose. For small plaques that score under 75, a hybrid is plan B: OCR or embedder to identify the node, plane hit-test plus device heading to place content.

## Recommended stack

| Library | Version | Why |
|---|---|---|
| `com.google.ar:core` | 1.54.0 (transitive via SceneView; 1.56.0 is latest) | What SceneView 4.38.0 is tested against |
| `io.github.sceneview:arsceneview` | 4.38.0 | Compose-native `ARScene`, GLB loading, augmented images, occlusion |
| Compose BOM | 2026.09.00 | Matches SceneView |
| Kotlin | 2.4.10 | SceneView's stdlib version |
| AGP / Gradle | 9.4.0 | Current stable, supports API 37 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 | ARCore 1.56 forces target 37 via manifest merge |
| `arcoreimg.exe` | from SDK 1.56.0 | Offline `.imgdb` build and `eval-img` scoring on Windows |
| `play-services-location` | 21.x+ | Only for the outdoor Geospatial leg |

## Gotchas
1. SceneView pins ARCore 1.54.0; forcing 1.56.0 is untested; pin exact versions.
2. ARCore 1.56 silently sets `targetSdkVersion` 37 via manifest merge.
3. Augmented image pose is +Y out of the plaque, +Z down the plaque.
4. Images must fill 25% of the frame to be detected (not to stay tracked).
5. Always pass physical width in `list.txt`.
6. 20 images tracked at once, 1,000 per database, one database per session.
7. Geospatial is online and outdoor-only in practice.
8. Sceneform and its fork are archived.
9. ARCore for Jetpack XR on phones has no augmented images.
10. Glossy plaques and near-identical room signs fail `eval-img` or cross-match.

## First 2 hours: must test
1. Run `arcoreimg eval-img` on two real anchor photos. Under 75 changes the whole marker plan.
2. Build `campus.imgdb` offline and detect both on the S25 Ultra; log time-to-`TRACKING` and whether `getName()` is right at about 1.5 m.
3. Stand up `ARScene` from SceneView 4.38.0 and render one GLB anchored to a detected image. This proves the version set composes at all.
4. Walk a 20 m corridor and back; measure how far the anchored object moved. That number decides plaque spacing.
5. Detect plaque A, walk to plaque B, detect it, verify the re-snap repositions the route without a visible jump.
