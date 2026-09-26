# HANDOFF for the next Claude session

Written 2026-09-26, about 00:40 EDT, Hour 5 of HackGT 13 (hacking Fri 8 PM to Sun 8 AM; expo Sun 9:30 AM, Klaus Atrium). Owner: Raphael Omorose. Read this, then `CLAUDE.md`, then `docs/README.md`, then `docs/22-phone-qa.md` (the wave 2 section is the current state).

## Repository state

- One repository, `C:\Users\rapha\CampusMaps`, branch **`main`**, pushed to `origin` (the teammate's GitHub repo). The owner commits directly to `origin/main`; commit small, plain messages, no attribution trailers, never rewrite history.
- The owner rewrote `main` once at 23:39 on 2026-09-25 (same content, new hashes). Any branch created before that has no common ancestor with `main`: cherry-pick, do not merge.
- Agent branches from this session (`w1/*`, `qa/*`, `w2/fix`) and their worktrees under `C:\Users\rapha\CampusMaps-wt\` are all merged or cherry-picked into `main`; safe to delete (`git worktree remove <path>`; `git branch -D <name>`).
- **Keys**: `MAPS_API_KEY` and `ARCORE_API_KEY` live only in the gitignored `local.properties` (copy it into any new worktree). A local pre-commit hook in `.git/hooks/pre-commit` refuses any commit containing a Google API key. Never write the key into a tracked file.
- Emulators: `survey36` (emulator-5554, window), plus two clones `survey36b` (5556) and `survey36c` (5558) started headless. Phone: Galaxy S25 Ultra, serial `R5CY12PPNPY`.

## What is built (all on `origin/main`, build and unit tests green)

- **Journey in three legs** (owner decision 2026-09-25 evening, design canvas boards 02, 03, 04):
  1. Explore map (`ui/screens/ExploreScreen.kt`, `outdoor/`): Google Maps with the walk from the real fix to the entrance core recommends; sheet with distance, minutes, floor, steps; "Start AR navigation". Home when far from every building and not in demo mode. Street steps come from the Routes API when the key allows it; otherwise a straight line.
  2. Hand-off (`geo/`, `ui/transition/`): 40 m trigger, "Almost there" card (fires once, re-arms above 60 m), 900 ms map-to-AR transition, outdoor chevrons behind a `GeospatialProvider` seam (drawn only when earth tracking is TRACKING with accuracy under 10 m), chip "AR tracking on" / "Finding your position" / indoor node.
  3. Indoor: unchanged, plus `loc/` real localization: `ArPositionProvider` from the camera pose, `SwitchablePositionProvider` (camera drives the position once the route is placed or a sign is seen; debug moves switch to the simulator), Augmented Images loading (no database yet, see below).
- **Glasses** (`glasses/`): real `GlassesLink` on Meta toolkit 0.7.0 with the burst loop (3 stills, camera off, OCR, vote 2 of 3, speak, repeat), reconnect with backoff, setup helper, mock replay (`adb shell setprop debug.campusmaps.glasses replay|real|sim`). Verified in replay on the emulator; not yet run with the real glasses (needs the owner's consent taps in Meta AI).
- **Splash** (`ui/splash/`): system splash plus a 1.4 s Compose animation; launcher icon is the logo mark.
- **Fixes**: WHATS-LEFT 2 to 6, docs/22 bugs 9, 10, 12, 14, 15, 16, and the wave 2 list W1 to W9 in docs/22.
- **Data**: entrance lat/lng/heading exposed via `CoreBridge.entranceGeo`; KL pending photos are warnings; CS lobby anchors (estimated); Klaus refresh path in docs/19 ("Klaus refresh, step by step").

## Also built after the hand tests (wave 3, 2026-09-26 morning)

Street steps in S2 and on the watch, glasses polish, Directions cache, S1 map icon, debug card fixes, and the in-app building editor (Settings, "Edit this building"). Details: docs/22 "Wave 3" and `reports/w3-*.md`. The glasses and the watch are verified end to end on the real devices; the watch app is installed on the Galaxy Watch (Wi-Fi ADB, port changes each time).

## Open, in priority order

1. **Anchor photos**: both CS sign photos fail ARCore's quality check (`assets/anchors/SCORES.md`), so there is no image database. Photograph high-texture targets at Klaus (directory boards, posters), score with `C:\Users\rapha\tools\arcoreimg\arcoreimg.exe`, build `anchors.imgdb`.
2. **Routes API 403**: add "Routes API" to the key's API restrictions in Google Cloud.
3. **Hand tests** listed in docs/22 "Only a human can test (wave 2)": the real walk to CS, AR floor tap, real glasses, watch on the wrist, shortcut persistence, expo volume.
4. **Klaus survey**: convert with core's ConvertMain (docs/19), replace KL.json estimates, pick two start spots and a destination under 60 m.
5. Open low items O2 to O6 in docs/22; demo videos (docs/18); Devpost (docs/13).

## Commands

```bash
cd /c/Users/rapha/CampusMaps
./gradlew :core:test :shared:test :app:testDebugUnitTest :app:assembleDebug :wear:assembleDebug
adb -s R5CY12PPNPY install -r app/build/outputs/apk/debug/app-debug.apk
adb -s R5CY12PPNPY shell am start -n com.campusmaps/.MainActivity
adb -s R5CY12PPNPY logcat -s AndroidRuntime:E Geo Handoff GlassesLink ArGuidanceView WatchBridge Directions
```
