# HANDOFF for the next Claude session

Written 2026-09-25, about 22:10 EDT, Hour 2 of HackGT 13 (hacking Fri 8 PM to Sun 8 AM; expo Sun 9:30 AM, Klaus Atrium). Owner: Raphael Omorose. Read this, then `CLAUDE.md`, then `docs/README.md`.

## Where the code is, and what is committed

One repository, `C:\Users\rapha\CampusMaps`, currently on branch **`integration`**. This folder is the working app.

| Branch | What it is |
|---|---|
| `integration` (checked out) | Rooted at teammate Uyiosa Nehikhuere's commit `a52f23e` (GitHub OfficialEseosa/CampusMaps, added as remote `friend`). All of Raphael's work is rewritten on top as **uncommitted** changes (61 paths: 34 modified, 11 deleted, 16 added). Nothing staged. The next session commits this as its first step. |
| `main` | Raphael's original line (docs, `core`, his own app shell, tools), 22 commits ending in a WIP snapshot `0bccfad` taken right before the base swap. Tags `routing-works`, `ui-shell-works`. Reference only. |

Owner instructions that still stand:
- **Commit in small increments with plain commit messages and no Co-Authored-By trailer** (owner decision 2026-09-25 22:20). The first commit on `integration` should be the current uncommitted integration result, as one commit, before new work starts. `main` stays as a branch for history; the teammate's commit stays as the root.
- `survey-app/` is a separate repo and tool (firewalled, gitignored). Never move code from it into CampusMaps. The survey zip in the root is gitignored too.
- A plain-file backup of the integrated tree is at `C:\Users\rapha\CampusMaps-integ-backup` (safe to delete once the owner has committed).

## What exists and works (verified)

- **Build**: `./gradlew :core:test :shared:test :app:testDebugUnitTest :app:assembleDebug :wear:assembleDebug` is green in `C:\Users\rapha\CampusMaps` (re-run after the folder swap at 22:08). Tests: core 45 (1 `@Ignore`), app 41, shared 4. `AppFlowTest` 8/8 on the emulator.
- **Version set** (teammate's catalog plus forced bumps, recorded at the end of `docs/14-tech-stack.md`): AGP 9.1.0, Gradle 9.6.1, Kotlin 2.4.10 (bumped from 2.2.21 for SceneView), Compose BOM 2026.04.01, compileSdk 37 / targetSdk 36 / minSdk 29, SceneView 4.38.0, ARCore 1.56.0, ML Kit text 16.0.1, **mwdat 0.7.0** (see glasses below), play-services-wearable 19.0.0, Wear Compose M3 1.6.2.
- **Architecture**: teammate's app shell and screens (S1 Destination, S1b Route options, S2 AR guidance, S3 Glasses, S4 Add a shortcut, Settings, debug card), theme (Sora, clay palette), `GuidanceController`/`GuidanceEngine`, `Speaker` (TTS), `WatchBridge`, `wear` + `shared` modules, shortcuts feature. Raphael's `core` module does all routing and data: JSON building files, loader, validator, Dijkstra router with entrance x vertical-method alternatives, `alsoVia`, locked-entrance notice, `now` as a parameter, survey converter. Joined by `data/campus/CoreBridge.kt` (core Building to drawing view; **y is flipped**, core y grows north, app y grows south) and `route/CoreRouter.kt` (core RouteOption/Instruction to the teammate's RoutePlan/Route/RouteStep). Sources are in `app/src/main/java`, not `kotlin/`. Details: `docs/21-integration-notes.md`.
- **Building data**: `app/src/main/assets/buildings/{KL,CS,CSE}.json`. **CS is real** (survey CS-20260925-1238: 26 nodes, 30 edges, 8 anchors, walking speed 1.29 m/s, elevator 2.4 s wait / 4.4 s per floor from 2 rides, stairs down 13.6 s per floor, floor height 3.9 m). KL and CSE are still guesses (`estimated: true`). Anchor images in `assets/anchors/CS/`. Status and gaps: `docs/19-building-data-status.md`.
- **Demo routes on real CS data**: P1 to 608 = Library South entrance (floor 2), elevator, 1:27 (also via Classroom South main); P2 to 608 = Walters main, elevator, 1:45 (also via Walters side). Top-two gap from P1 is 56 s (docs/04 wanted 60 s; needs one timed stair climb up, see docs/19).
- **AR layer**: `loc/BuildingToWorld.kt`, `ui/ar/RouteArrows.kt`, `ui/ar/ArGuidanceView.kt` (SceneView `ARScene`, chevron chain every 1.5 m, amber next-turn arrow, red destination post with label, fade beyond 15 m, 300 ms re-snap, debug "Place route here" floor tap). Adapter `ui/ar/ArInputsAdapter.kt`. Rendering verified on the S25 with a forced transform; **the floor-tap placement and a real walk have NOT been tested by a human** (the phone lay on a desk all session). `docs/05-ar-guidance.md` "Built 2026-09-25".
- **Glasses (Ray-Ban Meta Gen 2)**: verified on the real glasses with Meta's CameraAccess sample. Developer Mode with app id / client token `0` works (no Meta org needed). The glasses run DWA component 0.7, so **only toolkit 0.7.0 connects**; 0.9.0 and 1.0.0 fail with `UPDATE_REQUIRED`. Stream 24 fps at about 500 kbps, 25 ms transport delay after a 5 s warm-up; `capturePhoto()` returns a sharp still in 2.2 s; the stream dropped when Spotify was opened (audio and camera must never overlap). The app's `GlassesLink` is still `SimulatedGlassesLink`; the real one is the next module. Notes and sample APKs: `docs/06-glasses-bridge.md` (end), `tools/meta-sample/HOW-TO-TEST.md`. Sample sources are gitignored worktrees at `tools/meta-sample/sample-0.7.0/` (recreate with `git -C tools/meta-sample/meta-wearables-dat-android worktree add ../sample-0.7.0 25f3a6d` after cloning facebook/meta-wearables-dat-android there).
- **Watch**: builds; a watch is paired to the phone (debug card says "watch: 1 connected") but delivery to the real Galaxy Watch 8 Classic is unconfirmed (needs Wi-Fi ADB install, docs/08).
- **Phone**: Galaxy S25 Ultra, serial `R5CY12PPNPY`, Android 17, Meta AI app 290.1. The integrated debug APK is installed. "Stay awake while charging" was turned ON for testing (`adb shell svc power stayon false` and `adb shell settings put global stay_on_while_plugged_in 0` to undo). Media volume is 3 of 15; raise it before any demo.
- **Survey tool**: CampusSurvey 0.3 at `survey-app/dist/CampusSurvey-0.3-debug.apk` (guided visit wizard for CS, KL, CSE; plain-language UI; GPS stride calibration). Field guide: `survey-app/FIELD-GUIDE.md`.

## Phone QA (2026-09-25 22:05, real S25 Ultra)

All three demo flows pass; 16 issues found, 7 fixed in app code (shortcuts never reached routing because of old building ids; search dropped characters; no Clear button; AR hint overlapped controls; "in 0 m"; glasses arrival sentence repeated; S3 pills wrapped). Cold start 0.5 to 0.7 s; no crashes; memory steady at about 570 MB on S2 with ARCore. Full table in `docs/22-phone-qa.md`.

Highest open item: the teammate's **simulated walker moves the user by itself**, so S2 "arrives" in about 15 s while the phone lies still and the AR arrows would follow the fake position. Until real localization exists, use the debug Pause / Step / Walk controls. Other open items: approved shortcuts are lost on restart (fake backend); KL validator errors are 3 missing anchor photos; the "Exit toward" step is skipped; card difference text repeats "elevator" when both cards use it.

## Not built yet (in priority order for the demos)

1. **Real localization** from sign photos: ARCore Augmented Images database from `assets/anchors/*` (score with `arcoreimg.exe` first, threshold 75, see docs/17 section 5), `PositionProvider` from the camera pose (replaces the simulated walker), snap via `BuildingToWorld.fromCorrespondence(...)`, OCR text anchors with voting (docs/03). Until then S2 uses the debug floor tap.
2. **Glasses bridge** against mwdat **0.7.0**: burst loop (stream, 2 to 3 stills, stop, OCR, speak over A2DP, repeat), reconnect loop, mock device replay. Copy API shapes from `tools/meta-sample/sample-0.7.0/samples/CameraAccess`. Interface to implement: `guidance/GlassesLink.kt`.
3. **Klaus survey** (Demo A is the only live demo). Use the CampusSurvey Klaus guide; replace KL.json estimates; pick two start spots near the expo table and a destination under 60 m; add the missing anchor photos.
4. **Barometer floor change** during the elevator ride (docs/03 section 3), Geospatial outdoor leg (docs/03 section 4), watch on the real wrist, demo videos (docs/18), Devpost (docs/13).

## Other open issues

- Teammate's `WHATS-LEFT.md` items 2 to 6 (watch never learns the route ended, stairs-down icon, S3 arrival buttons, S4 leftover colours, simulated glasses never send a still).
- Validator rule 8 (an anchor every 20 m on a demo route) fails on CS: 68 m with no anchor from the Library South entrance to 608; no elevator lobby has an anchor.
- `GuidanceEngine` completes a step 1.5 m early, so two instructions 3 m apart flash.
- Trip state is not restored after process death (restarts on S1 by design).
- `docs/design/wireframes.html` (designer hand-off) predates the teammate's theme.

## People and decisions

- Raphael owns the plan and the repo; the teammate did the design and the shell. Credit both on Devpost. The pre-event code (Friday daytime) is kept without special disclosure (owner decision); the pitch answer in `docs/13-pitch.md` was updated accordingly.
- Decisions log: `docs/15-open-questions.md` section C (and E for the Gemini stretch idea). Survey review and revised protocols: `docs/17-survey-review.md`. Demo video plan: `docs/18-demo-video.md`.
- Working style the owner asked for: split QA across several subagents and devices in parallel with short time-boxes; explain field tools in plain words (he is new to node/edge vocabulary).

## Commands

```bash
cd /c/Users/rapha/CampusMaps
./gradlew :core:test :shared:test :app:testDebugUnitTest :app:assembleDebug :wear:assembleDebug
adb -s R5CY12PPNPY install -r app/build/outputs/apk/debug/app-debug.apk
adb -s R5CY12PPNPY shell am start -n com.campusmaps/.MainActivity
adb -s R5CY12PPNPY logcat -d -s AndroidRuntime:E
```

Emulator AVD `survey36` (API 36, no ARCore) is fine for everything except AR and glasses.
