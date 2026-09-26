# W4 flow QA (branch w4/flow)

2026-09-26, about 10:30 to 11:15. Device: emulator-5556 (API 36, headless, no ARCore, no watch, glasses `sim`). The worktree is `main` at 8104382, so the Klaus data is the **old** estimate (Room 1116, Start 1). The new survey (1116W; S1 12 s / 17 m, S2 18 s / 24 m) was not tested here. GPS was faked with test location providers (`cmd location providers set-test-provider-location`), because `emu geo fix` did not reach the fused provider. The emulator was slow (a cold start took 11 to 22 s) and was rebooted once. Screenshots (360 px) are in `reports/shots-w4-flow/`.

Build: `:app:assembleDebug :app:testDebugUnitTest` green after every commit.

## Commits

| Commit | Fix |
|---|---|
| fd862a8 | S1 is home in demo mode again. The redesign always opened on S0, even in demo mode. The home screen stays blank for up to 300 ms, on S0 as well as S1, so S0 no longer flashes before Explore (this was O6 again). |
| 39d3c3b | S0b for Georgia State had dark status bar icons on the blue header. They are now light. Georgia Tech (gold) keeps dark icons. |
| c34802a | The campus skin and the "Campus · Building" pill now follow the building's campus on every building change: the Explore map, `startFromExplore`, Settings and S0b. Before, CS reached from the map showed "Georgia Tech · Classroom South" in gold. Now it shows "Georgia State · Classroom South" (`25`). The S0 card and the S0b swap still set the campus themselves. |

All three commits change only `ui/CampusMapsApp.kt` and `ui/MainViewModel.kt`.

## Results

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Instrumented tests (`connectedDebugAndroidTest`, 8 tests, run before my fixes) | **Pass** 8/8: destinationToRouteOptionsToGuidanceAndBack, lockedEntranceBannerNamesBothEntrances, avoidStairsRemovesStairsCards, everyEntranceLockedShowsNoRoute, glassesModeStartsAndStops, demoModeHidesSearchAndShortcuts, addShortcutScreenExplainsWhatIsMissing, settingsSheetShowsTheTeamSwitches. **Not re-run after my fixes.** The @Before does not use demo mode, so the home fix should not affect it. | Gradle output |
| 2a | Cold start, demo off, fix 2 km away | **Pass**: Explore is home. | `02` |
| 2b | Cold start, demo off, fix near a building | **Pass**: S0 campus picker. | `01` |
| 2c | Cold start, demo on, far fix | **Failed, then fixed** (fd862a8). It landed on S0. It now lands on S1 with DEMO and Room 1116. | `03` |
| 2d | Home rule (after the fixes) | Explore when not in demo mode and the last fix is missing or more than HOME_RADIUS from every building. S1 in demo mode. S0 otherwise. Back on S1 goes to S0b, or to Explore when Explore is home. Back on S0b goes to S0, and Back on S0 exits. | code and device |
| 2e | S0 and S0b | **Pass**: GT lists Klaus plus Clough Commons and Van Leer as "Soon"; GSU lists CS, CSE and Library South "Soon". Tapping a Soon row does nothing (no crash). The swap pill switches campus. | `04`, `05` |
| 3a | Demo A (old KL data): S0 > GT > Klaus > Room 1116 > S1b | **Pass**: "From here 0:34, 44 m walk". Map mode: S2 "Step 2 of 3", "Map-only mode. Follow the text and the map.", "Then: Room 1116 is on your right". | device |
| 3b | Demo B: CS 608 from P1 | **Pass**: Library South (floor 2) elevator 1:27, "also via: Classroom South main (floor 2)", stairs 2:23. Tapping the stairs card selects it and the Start ETA becomes 2:23. Avoid stairs flips live (elevator 1:27, Walters side 2:21). | `07` |
| 3c | 3D preview | **Pass**: the route is drawn across 6 floors; F2 focus dims the others; drag and Back work (to S1b), no crash. | `09`, `10` |
| 3d | Demo C: CSE 220, Sat 21:00 | **Pass**: "Heads up: Main entrance is card-only now. Using West entrance instead." West entrance 1:45 stairs, 2:04 elevator. Sat 23:00 no route: covered by the instrumented test only. | `15` |
| 3e | S1b "Guide me with" Glasses + Start | **Pass**: S3 "Walk to West entrance", "Speech: phone speaker". Fake step to arrival: "Room 220 is on your right", "Seen: 220". Back to routes goes to S1b. | `16`, `17` |
| 3f | "Map" tile | Works as built, not as the brief says. Map = S2 with the camera off (map-only mode, progress pill with the map icon). It does **not** open Explore. Owner: decide whether that is intended. | device |
| 4 | S2 (CS 608) | **Pass**: the progress pill advances with debug Step (2 of 8 to 8 of 8, then "Arrived"): elevator, floor 6 bend, "Room 608 is ahead". Done goes to S1. End route goes to S1b, with the start moved to the entrance ("From here"). | `11`, `14` |
| 4b | S2 from Explore (fix about 300 m from CS, from the Klaus S1 map icon) | **Pass**: Directions gives 3 street steps plus Enter. Banner "Head southwest on Peachtree St toward Walton St NW, in 143 m", "Then: Turn left onto Decatur St. SE". Log: map entrance E-WM = S2 entrance E-WM. End route goes back to the map. | `24` |
| 5 | Editor | **Not run** (out of time). | - |
| 6 | Debug card | **Pass**: long-press on the S1 "CampusMaps" title and on the S1b title opens it. Fold stays folded into S2 ("step 2/8 · conf 0.92"). Step, Walk, Sim time and Jump are present, and Step drives S2. | device |
| 7 | Settings persistence, TTS | Not re-checked (out of time), apart from demo on/off surviving force-stop. | - |
| 7b | Process death on S2 (HOME, `am kill`, relaunch) | **Pass**: no crash. It lands on Explore because the fix was 300 m away (home rule). The route is not restored (as before). | device |
| 8 | Watch log | **Pass**: exactly one "Route ended: clearing the watch" per Done (CS), per Back to routes (S3) and per End route (from Explore). | logcat |
| 9 | Crashes | None. No FATAL lines in the session. | logcat |

## Open (not fixed)

| # | Item | Severity | Repro | File |
|---|---|---|---|---|
| F1 | Home decision can come late. After 300 ms the app shows S0; if the location lookup (up to 3 s) then says "far", S0 jumps to Explore. On the slow emulator this happened 3 to 5 s after launch. If the user already went to S0b, it does not jump. After a reboot (no last fix) it always goes to Explore. | Low | Cold start with no recent fix | `ui/CampusMapsApp.kt`, `outdoor/ExploreViewModel.exploreShouldBeHome` |
| F2 | S0b "N rooms" counts only destination rooms (Klaus "1 rooms", CSE "1 rooms"): wrong plural, low count. | Low (look) | S0b | `ui/screens/BuildingsScreen.kt` (design agent) |
| F3 | The Map tile does not open Explore (see 3f). | Decision | - | `ui/MainViewModel.kt` |
| F4 | Coordinator notes, not checked: Explore keeps "Turn on location" after the grant until a restart; the room is lost when relaunching after process death; the sheet distance is stale within a few metres of the door. | Low | - | `outdoor/` |
| F5 | The instrumented tests were not re-run after the three commits. | - | `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest` | - |

The S0 campus-card bug reported from the S25 was retracted by the coordinator. On the emulator, a tap on the GT card arrow opened the Georgia Tech list correctly.
