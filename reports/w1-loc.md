# W1 localization report (branch w1/loc)

## Anchor scores (arcoreimg 1.56.0, threshold 75)
- CS-A01.jpg: fail, "Failed to get enough keypoints from target image"
- CS-A08.jpg: fail, same
- Also failed: tighter crops, autocontrast, equalised copies (best was 0). KL and CSE have no image files.
- So no image passes and there is **no anchors.imgdb**. Details and the build-db command: `app/src/main/assets/anchors/SCORES.md`.
- Tool: `C:\Users\rapha\tools\arcoreimg\arcoreimg.exe` (4.5 MB, downloaded from the v1.56.0 tag).

## What works on the phone (S25, 22:52 to 22:56)
- The AR session starts with the image database step: logcat `AnchorImages: run-time database: 0 images []; skipped [CS-A01 (ImageInsufficientQualityException), CS-A08 (...)]`. ARCore itself rejects both signs, which matches arcoreimg.
- The AR view reports itself live (`Position: AR view live ...`) and the simulated walker is paused: the student stayed at "in 77 m" for 20 s with the phone still. The old problem (walking by itself) is gone on S2 when ARCore is available.
- Debug fallbacks still work: Step moved the student to the next node (77 m to 18 m, mode switched to SIMULATED in logcat), "Continuous walker: paused (tap: resume)" restarted the walker (18 m to 10 m).
- No AndroidRuntime errors, no crash.
- Build `:app:assembleDebug :app:testDebugUnitTest` green; 50 unit tests, 0 failures (9 new in `ArPositionProviderTest`).

## Not tested (phone lay face down, camera dark: "too dark")
- Camera-driven position: needs a transform first ("Place route here" floor tap, or a sign fix), then moving the phone.
- Sign fix from an Augmented Image: no image passes, so nothing can be tracked yet. Code path is in place.

## Owner, test by hand
1. Pick a room, start S2 with ARCore on, point the camera at the floor. Wait for "Place route here".
2. Stand on the start node, face along the route, tap the floor. Walk 3 to 5 m along the route: the blue dot and "in N m" should change only when you move, at about 5 updates per second.
3. Cover the camera for 3 s: confidence falls (0.25 per second), "Locate me" at 2 s; uncover, it returns to 1.0.
4. Debug card: Step / Walk / jump move the dot (simulator takes over). Tap "Re-place route" to hand control back to the camera.
5. Logcat tags: `Position`, `AnchorImages`, `ArGuidance`.
6. For a real sign fix: photograph a high-texture sign or poster (directory board, event poster), score it (>= 75), build `anchors.imgdb` (SCORES.md), add the anchor to the building JSON with x, y, floor, heightM (centre above floor) and facing.

## How it works (for other agents)
- `GuidanceController.position` / `.simulation` are now `loc/SwitchablePositionProvider` (exposed as `controller.positionSource`, with `mode` StateFlow and `setMode(PositionMode.AUTO | SIMULATED)`). The simulator is still inside it.
- AUTO: camera pose (`loc/ArPositionProvider`) when the AR view is live AND a building-to-world transform exists; otherwise the simulator's pose.
- Any SimulationControls call (Step, Walk, jump, skip step, off route, drop confidence, paused setter) switches to SIMULATED, seeded from the camera position. A new transform (>0.3 m or >3 degrees change: new placement or sign fix) switches back to AUTO.
- When ARCore is supported and not glasses mode, the walker starts paused. If no AR frame arrives in 4 s (camera denied, AR forced off) it resumes as before. S3 (glasses) and the emulator behave as before.
- UI caveat: the debug link reads "Continuous walker: on" while the camera drives (paused getter returns false then); the first tap pauses (simulator, standing still). A proper "Position: AR / simulated" switch needs a line in `ui/screens/DebugOverlay.kt` bound to `controller.positionSource.setMode` (I did not touch ui/screens).
- `loc/ArFeed` is a small singleton: the controller registers its provider and the building anchors; `ArGuidanceView` sends camera samples and reads anchors. `AppContainer` sets `ArFeed.arExpected` from `ArSupport.isSupported`.
- Sign fix: `loc/ImageFix.transform` uses the image centre and its normal (centre pose +Y) against the anchor's x, y, floor, heightM (default 1.5) and facing; re-snaps at most every 1.5 s per image and re-anchors the ARCore anchor.
- Floor = transform's reference floor plus whole floors of camera height change (phone assumed 1.3 m above the floor).
- Image database: `assets/anchors/anchors.imgdb` if present; else debug builds add `assets/anchors/<code>/<id>.jpg` at run time (rejected ones logged).

## Files
- New: `app/src/main/java/com/campusmaps/loc/{ArPositionProvider,SwitchablePositionProvider,ArFeed,ImageFix,AnchorImages}.kt`, `app/src/test/java/com/campusmaps/loc/ArPositionProviderTest.kt`, `app/src/main/assets/anchors/SCORES.md`, this report.
- Changed: `ui/ar/ArGuidanceView.kt` (database, sign fix, camera samples), `guidance/GuidanceController.kt` (position source wiring), `guidance/Positioning.kt` (comment), `AppContainer.kt` (arExpected).
