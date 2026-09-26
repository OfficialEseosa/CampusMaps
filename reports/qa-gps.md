# QA GPS WALK: outdoor to indoor on a faked GPS track

Emulator-5558 (API 36, Google APIs, no ARCore), branch `qa/gps`, 2026-09-25 23:35 to 23:58. Demo mode off.
Destination Room 608, Classroom South. Fixes sent with `emu geo fix` along a straight line to the recommended
entrance, which from the start point (33.7545, -84.3900) is **E-WM Walters main entrance**, not Library South.

## Steps

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Cold start far away opens Explore with the dot | Pass | first cold start opened the map. Later cold starts opened S1 because the emulator keeps the last fix near the door until an app listens (dumpsys location), so the home rule was right |
| 1 | Room 608 via search, Back: clay line, sheet with distance, minutes, floor, steps | Pass | shot 01: 279 m, 4 min, Floor 1, 3 steps |
| 1 | Street steps from the Routes API | **Fail (key)** | every request: `Routes API HTTP 403; trying legacy Directions` then `Legacy Directions gave no route; straight line`. The sheet says "straight line". The key cannot call the Routes API yet (API not enabled for this key's project, or the key's API restrictions leave out Routes API) |
| 2 | Sheet distance follows the walk | Pass | 279, 165 (shot 02), 48 m |
| 2 | "Almost there" card under 40 m, exactly once | Pass | shot 03. It fired once at 35 m; it did not fire again at 30, 25, 20 m |
| 3 | S2 opens through the map-to-AR transition, no jump cut | **Fail on main, fixed** | see Transition below |
| 3 | S2 banner and chip | Pass | shot 04: "Walk to Walters main entrance, in 35 m", chip "Finding your position", "AR unavailable on this device" |
| 3 | Banner distance follows FusedLocation at 20, 10, 5 m | **Could not check** | the simulated walker starts walking the route when S2 opens (no ARCore), so it reached the entrance and the indoor steps before my fixes did. The banner showed the walker, not GPS (see bug 4) |
| 4 | Past the entrance the chip shows the indoor node | Pass | shot 07: chip "Walters main entrance", then "Elevator lobby turn". Arrival "You have arrived, Room 608 is ahead" was reached |
| 4 | End route from a map-started S2 returns to the map | Pass | |
| 4 | The card stays away until a new route (re-arm only above 60 m) | **Fail on main, fixed** | bug 1, shot 05. After the fix: End route at 35 m, fixes at 30, 25, 20 m, no card (shot 06). Walking out to 70 to 80 m and back to 35 m fires it again, as intended |
| 5 | Manual AR button from 279 m: transition, no card | Pass | 0.6 s zoom and fade with S2 underneath, banner "in 279 m", no card |
| 6 | Back on Explore | Pass | leaves the app (to the launcher); relaunch shows the map |
| 6 | Back on S2 from the map | Pass | returns to the map, no card |
| 6 | Back after End route | Pass | same as Back on Explore |
| 6 | Process death on S2 from the map | Pass | HOME, `run-as kill -9` (plain `am kill` does not kill a foreground-ish process), relaunch: clean S1 (near the building), no crash |
| 7 | Location permission revoked, relaunch | Pass | shot 08: the map with "Turn on location to see your route", no crash |
| 7 | Wi-Fi and data off | Pass | `Directions failed, using a straight line: Unable to resolve host "routes.googleapis.com"`, sheet shows the straight line, no crash |

## Transition (frame by frame)

**On main, card path** (shot 09, first S2 of the process): 1.92 s card up, 1.97 s card fading, 2.5 to 3.7 s a
**blank cream screen** (map gone, S2 not there), 4.24 s S2 appears in one frame. A jump cut. Cause: the card's Go
set the hand-off to ANIMATING before any S2 session existed. The 900 ms ran over an empty AR layer, then
`startFromExplore` created S2, and S2's first composition blocked the main thread for 1.1 to 2 s (`Davey!
duration=1140..2148ms`, `Skipped 76 frames`). The Crossfade was not restarted: `crossTarget` stays EXPLORE and the
map-to-AR host is not recreated. The frames were lost to the empty layer and the stall.

**Manual AR button** (warm process, before the fix too): 1.0 to 1.6 s the map and sheet scale up about 1.5 times
toward the centre and fade out, while S2 (banner, Locate me card, minimap) fades in over them. No cut. The 55 degree
tilt is hard to see because the Google map surface follows the scale and alpha but shows little of the tilt.

**After the fix, card path, cold S2** (shot 10): 3.82 s card up, 3.87 s card gone, map holds still while S2 is
composed underneath at zero alpha (`Handoff: S2 ready after 899 ms`), 4.45 to 5.21 s map zooms in and fades while S2
fades in, 15 frames in between (`map-to-AR ran 1125 ms, 15 in-between frames`, before: 2). No blank frames, no cut.

## Bugs

| # | Severity | Bug | Repro | File | Fix |
|---|----------|-----|-------|------|-----|
| 1 | **High** (fixed) | After End route (or Back) within 40 m the card fires again at once and reopens S2. The student cannot leave S2 near the door | map route, walk to 35 m, S2 opens, End route, send one more fix | `ui/CampusMapsApp.kt` EXPLORE branch: the `LaunchedEffect(..., guidance == null)` called `handoff.newRoute` every time the session ended, which re-arms the trigger | re-arm only when the map route (building, room, entrance) changes (`vm.handoffRouteKey`, cleared by `reset()`), always re-attach the fixes. Committed |
| 2 | **High** (fixed) | Card path: jump cut with about 1.7 s of blank screen (see Transition) | walk to 35 m, let the card advance | `CampusMapsApp.kt` (ANIMATING before the session), `ui/transition/MapToArTransition.kt` | the card's Go now calls `startFromExplore(fromCard = true)` (which then calls `startHandoff` with S2 composed); the progress snaps to 0.0001 so S2 is composed at zero alpha and waits for three smooth frames (max 2 s) before the 900 ms run; logs `Handoff` timings. Committed |
| 3 | Medium (fixed) | The card's auto-advance is dropped by the tap guard if it fires within `TAP_GUARD_MS` of a screen change, which left the hand-off stuck in ANIMATING (empty screen) | not hit; found reading `startFromExplore` | `ui/MainViewModel.kt` | `fromCard` skips the tap guard; a second start is ignored while one is running; if no session comes of it, `backToMap()` |
| 4 | Medium (open) | With no ARCore pose the simulated walker walks the whole route from S2 open, outdoor leg included, so the banner and chip follow the walker, not FusedLocation. On the S25 this matters while Geospatial has not localized | open S2 from the map at 35 m, wait: "You have arrived" in about 2 min without moving | `guidance/GuidanceController.kt`, `loc/SwitchablePositionProvider.kt` | hold the simulator at the start node while the route's outdoor leg is active and `outdoorEntrance` is set, and drive the banner from `handoffUi.distanceToEntranceM` until the entrance is crossed |
| 5 | Medium (owner) | Routes API returns HTTP 403, legacy Directions returns not OK: always the straight line | any map route | Cloud console | enable "Routes API" on the key's project and add it to the key's API restrictions; logging the 403 body in `DirectionsClient` would show which |
| 6 | Low | Directions is called again on every fix (every 2 s here), 403 or not | walk the track, count `Directions` lines | `outdoor/ExploreViewModel.kt` | recompute only when the start moved more than about 25 m or the entrance changed |
| 7 | Low | Returning to the map after End route shows about 1 s of blank cream while the map surface is recreated | End route | `CampusMapsApp.kt` / `ExploreScreen` | cosmetic; keep the map composed under S2 at alpha 0 instead of dropping it at progress 1 |

Not a bug: Done on arrival goes to S1 (reset), End route goes to the map. The brief expected "Walk to Library South
entrance"; core recommends Walters main from this start point, which is correct for the NW approach.
