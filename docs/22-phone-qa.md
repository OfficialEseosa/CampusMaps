# 22. Phone QA (Galaxy S25 Ultra, 2026-09-25)

This QA pass ran on the physical S25 Ultra (serial R5CY12PPNPY, Android 17, 1440x3120, 600 dpi, so 1 dp is 3.75 px), using the `integration` working tree driven over adb. The phone lay face-up on a desk, so ARCore saw only the table. Nothing is committed.

Screenshots are in the session scratchpad under `phoneqa/` (full size). Downscaled copies are in `phoneqa/small/`. The path is:
`C:\Users\rapha\AppData\Local\Temp\claude\C--Users-rapha-CampusMaps\64c5d672-f56f-45e1-9532-0c7ace6e3938\scratchpad\phoneqa\`

**Build state:** the APK on the phone was built from the current tree after the last edit, and installed with `install -r`. There are no unbuilt fixes. `:app:testDebugUnitTest` is green.

## Bugs

| # | Bug | Severity | Repro | Status | File |
|---|---|---|---|---|---|
| 1 | Approved student shortcuts never reached the router. `ShortcutRepository.sync()` fetched approved shortcuts for the teammate's old ids `cs`, `klaus`, `sce`. Buildings are now `KL`, `CS`, `CSE`, and S4 stores `building.id`. | High | CSE: S4 shortcut from Floor 2 corridor to Room 220, then debug "Approve my pending shortcuts". Before the fix the cards were unchanged. | **Fixed.** The repository takes `buildingIds` from `AppContainer.buildings`. Verified: from Floor 2 corridor the card reads "Student shortcut, 0:12, 16 m walk, via Floor 2 corridor cut through" (`38-CSE-shortcut-used.png`). | `data/shortcuts/ShortcutRepository.kt`, `AppContainer.kt` |
| 2 | The search field dropped typed characters: "ROOM" became "R", "608" became "6", and "1" disappeared. The TextField was fed from `trip.query`, which is computed on `Dispatchers.Default`, so a late recomposition put back an older value. The IME Search action also read the stale hits. | Medium | S1: tap search and type quickly (`adb shell input text ROOM`). | **Fixed.** `MainViewModel.searchText` is synchronous Compose state, and every selection change goes through `updateSelection`. `submitSearch` searches `searchText`. Verified with ROOM, "room 1", 1, zzzq, 608, R-, 5 and Klaus: every character is kept. | `ui/MainViewModel.kt`, `ui/CampusMapsApp.kt` |
| 3 | The search field had no Clear button. docs/20 QA #13 added one, but it is missing from the teammate's field. | Low | S1: type anything. | **Fixed.** An X icon ("Clear search") shows while text is present. Verified (`37-search-gibberish-clear.png`). | `ui/screens/DestinationScreen.kt` |
| 4 | The AR hint ("Point the camera at the floor or a sign" / "Place route here") overlapped other controls: the arrived minimap and Done area (`04-KL-S2-arrived.png`), the Locate me card (`10-KL-S2-outside.png`), and it would sit behind the enlarged minimap. | Medium | S2 with real ARCore: arrive; start outside (Locate me for the first 4 s); tap the minimap. | **Fixed.** `ArGuidanceView` takes `hintBottom` and `showHints`. The hint sits above the minimap or the enlarged map and is hidden while arrived or locating. Verified in `39-S2-arrived-fixed.png`, `40-CS-S2-locating-fixed.png` and `41-CS-S2-map-enlarged.png`. | `ui/ar/ArGuidanceView.kt`, `ui/screens/GuidanceScreen.kt` |
| 5 | The first banner read "Head toward Atrium centre, in 0 m". The HEAD step starts where you stand. | Low | KL Room 1116 from Start 1, enter S2. | **Fixed.** A distance under 0.5 m is not shown. The watch still gets "0 m" for HEAD (`GuidanceEngine.watchStep`, not changed). | `ui/screens/GuidanceScreen.kt` |
| 6 | Glasses mode (S3) kept cycling after arrival and re-spoke "Room 220 is on your right" about every 5 s until Stop. Measured on the TTS audio track: `110000001110000011110000011100`. | Medium | CSE Sat 21:00, Guide me with glasses, Fake step to arrival, listen. | **Fixed.** `GlassesLink.start(..., finished)` ends the cycle once the arrival sentence has been spoken. Repeat still speaks. Verified: two bursts, then 13 s of silence; Repeat is audible. | `guidance/GlassesLink.kt`, `ui/MainViewModel.kt` |
| 7 | S3: the Looking / Recognising / Speaking pills beside "Floor N" wrapped, putting "Speaking" on a second line (384 dp wide screen). | Low | Open S3. | **Fixed.** The floor is on its own line and the pills are on one full-width row (`42-S3-arrived-fixed.png`). | `ui/screens/GlassesScreen.kt` |
| 8 | The simulated walker moves the student by itself on S2 on real hardware. The route "arrives" in about 15 s while the phone lies still, and the AR arrows would follow a fake position. | High (demo) | Any S2 on the S25. | **Open, by design** until real localization exists (docs/21 open issue 3). For the demo, use debug Step, Walk, or Pause (the "Continuous walker" link), or implement `PositionProvider`. | `guidance/SimulatedPositionProvider.kt` |
| 9 | Shortcut approvals do not survive a restart. `FakeShortcutBackend` is in memory, so after a reinstall or process death the S4 list still says "Approved" but the edge is gone from routing. | Low | Approve, then force-stop, then route. | Open (fake backend). | `data/shortcuts/ShortcutBackend.kt` |
| 10 | The seeded "Sparks side cut through" shortcut uses building `cs` and nodes `cs_p1`/`cs_walters`, which do not exist. It is inert (dropped by `usableShortcut`) but is dead data. | Info | - | Open | `data/shortcuts/ShortcutBackend.kt` |
| 11 | KL "Outside" start (synthetic OUT at the entrance): the "Walk to / Go through South main entrance" step completes at once. The first banner is already "Turn left at Atrium north". | Low | KL, start Outside, Room 1116, S2. | Open (KL has no `startPoints`; data). | `assets/buildings/KL.json` (data, not edited) |
| 12 | The card difference line repeats "elevator, avg wait 2 s" when both cards use the elevator but enter on different floors ("+54 s: 64 m more walking, elevator, avg wait 2 s"). | Low | CS P1 to Room 608, Avoid stairs on. | Open | `route/RouteCardText.kt` |
| 13 | The CS elevator "avg wait 2 s" comes from 2 quiet-hour survey rides (2.41 s). A class change will be much longer. The ETAs for Demo B (1:27 vs 1:45) depend on it. | Info (data) | - | Open (data) | `assets/buildings/CS.json` |
| 14 | The active phase pill always uses the volume_up icon, even for "Looking" and "Recognising". | Low | S3 | Open (handoff design) | `ui/screens/GlassesScreen.kt` |
| 15 | Debug card: the S1 text shows through the card more than the 95% alpha suggests (`05-KL-S1-debug.png`). The link rows are 48 dp tall on a 36 dp pitch, so their tap areas overlap. | Low (debug only) | Long-press the title. | Open | `ui/screens/DebugOverlay.kt` |
| 16 | Exit steps are skipped (known, docs/21 open issue 6). CS walk: EL-6 jumps from step 4 to step 6 and skips "Exit toward Elevator lobby". | Low | CS P1 to 608, Step. | Open (known) | `guidance/GuidanceEngine.kt` |

## Demo flows

| Flow | Result | Screenshots |
|---|---|---|
| **Demo A (KL)**, Start 1 to Room 1116 | **Pass.** One card "From here 0:34, 44 m". S2 shows the banner, "Then:", minimap, F1 badge, the ARCore hint "Point the camera at the floor or a sign (too dark)" (no crash, no black-screen hang), and Arrived "Room 1116 is on your right". Done lands on S1. Back to routes shows "You are already here". Debug Step reaches arrival. From Outside: South main 0:45, then North 1:11 (+26 s: 34 m more walking). | `01`, `02`, `03`, `04`, `05`, `06`, `07`, `09`, `10`, `39` |
| **Demo B (CS)**, Room 608 | **Pass.** P1: Library South entrance (floor 2), elevator 1:27, "also via: Classroom South main (floor 2)"; stairs 2:23; 95 Decatur 2:47. P2: Walters main, elevator 1:45, "also via: Walters side entrance". Avoid stairs flips the cards live. S2 Step walk: "Take the elevator to floor 6", then "Riding to Floor 6", then the badge goes F2 to F6, then arrival. Room 150: P2 gives Walters main 0:41; P1 gives Walters side 1:20 (also via Walters main, 95 Decatur). ETAs are m:ss and the deltas read "+56 s" / "+1:20". | `11`, `12`, `13`, `14`, `15`, `16`, `17` |
| **Demo C (CSE)**, Room 220 | **Pass.** Sim time Sat 21:00: "Heads up: Main entrance is card-only now. Using West entrance instead." Sat 14:00 (day chip plus TimePicker): no banner, Main entrance first. Sat 23:30: "No route to Room 220: every entrance is card-only at Sat 23:30.", no crash. S3: Looking / Recognising / Speaking cycle, "Seen: 210-230" then "220", Fake step to arrival, Repeat, and Stop (to S1b, "You are already here"). | `18`, `19`, `20`, `21`, `22`, `23`, `42` |

## Other checks

- **Cold start** (`am start -W`, force-stop first): 663, 489, 491, 493, 706, 669 ms. After process death: 576 ms (COLD). Hot resume: 47 ms. The emulator's 12 s cold start is not a problem on the S25.
- **Process death on S2** (HOME, then `am kill`, then relaunch): lands on a clean S1, no crash. Background and foreground on S2 keeps the route, and AR resumes.
- **Double taps:** double-tapping Route lands the second tap on "Guide me with glasses" within 600 ms and it is ignored. A triple tap on a card opens one S2. Back goes S2 to S1b to S1 to exit.
- **Rotation:** the manifest locks portrait (`requestedOrientation=SCREEN_ORIENTATION_PORTRAIT`).
- **Themes:** in both night and day mode, S1, S1b and Settings follow the system and S2 is always dark. Text is readable and status-bar icons are visible in both (`50-53-dark-*.png`, `50-53-light-*.png`). Night mode was restored to "yes" afterwards.
- **Settings:**
  - The TTS toggle persists across force-stop.
  - Demo mode shows the DEMO tag and demo destinations only, and long-press does nothing. `FLAG_KEEP_SCREEN_ON` is set with demo on (window `fl=81810180`) and cleared with it off (`fl=81810100`).
  - Demo mode and the demo building (CSE) persist across force-stop.
  - Reset demo clears the selection and the start.
- **Shortcuts (S4):**
  - From/To pickers work, and cross-floor pairs are refused ("From and To must be on the same floor").
  - The walk recorder and 2 photos (Samsung camera) work, submission shows "Pending review", and debug Approve shows "Approved".
  - After fix #1 the shortcut is routed. Routing for the other cards is unchanged.
  - The notification permission prompt was answered "Don't allow".
- **Search:** see bugs #2 and #3. The IME Search key picks the top hit and hides the keyboard. Gibberish shows `No rooms match "zzzq"`.
- **Watch:** the phone has a paired watch. The debug card says "watch: 1 connected", not "not connected". `logcat -s WatchBridge` is empty, so there were no send failures and no exceptions. Whether the CampusMaps wear app received anything was not checked.
- **Logcat:** `AndroidRuntime:E` is empty for the whole session.
  - Validator lines (`BuildingRepository`):
    - CS: 26 nodes, 30 edges, 8 anchors, 0 errors, 0 warnings.
    - CSE: 12 nodes, 13 edges, 5 anchors, 0 errors, 0 warnings.
    - KL: 17 nodes, 18 edges, 6 anchors, **3 errors**, all rule 7: `KL-A01`, `KL-A02` and `KL-A05` images are missing from `anchors/KL/`.
  - Rule 10 INFO: entrances with no access windows: CS E-WM, E-WS, E-95DS, E-LM2, E-CSM2 (plus others above the log cut), and KL E-S, E-N.
  - One `Unable to open libpenguin.so` error at startup (Samsung vendor noise).
- **TTS / audio:**
  - Phone TTS works. The system default engine is Samsung SMT, but the speech track belongs to `com.google.android.tts` (uid 10284). It plays as `USAGE_MEDIA` on the phone speaker.
  - **Media volume is 3 of 15, so speech is quiet.** Raise the media volume before the demo. It was not changed during QA.
  - Glasses-mode speech also goes through the phone speaker (simulated glasses).
- **Memory (PSS):**
  - 225 MB cold on S1.
  - About 570 MB on S2 with ARCore running.
  - About 350 to 360 MB back on S1b after S2.
  - Four S2 enter/exit cycles held steady at 361, 353, 354 and 352 MB, so there is no leak.
- **Jank** (S1 to S1b, gfxinfo, 120 Hz): 57 frames, p50 8 ms, p90 16 ms, p99 25 ms, 16% missed deadline. Nothing visible. Routing is done before Route is tapped, so S1b opens at once.
- **Touch targets:** the main controls measure 48 dp or more:
  - Back, Reset and Settings: 48x48.
  - Building chips: 113 to 115 x 52.
  - Route: 344x58.
  - Cards: 344 x 142 to 76.
  - Done: 360x60.
  - Back to routes: 360x56.
  - End route: 123x48, or 48x48 compact.
  - Debug Step/Walk: 58x48.
  - Exception: the debug Offline link is 36 dp tall.
- **Contrast:** the AR banner is white text on the near-black scrim, fine even over a bright camera image. The arrived banner is dark green on light green, and was readable.

## Only a human can test

1. **AR floor tap.** Point the phone at a real floor until "Place route here" appears, tap the floor, and check that the arrows lie on the floor along the route and the destination label appears. On the desk ARCore reached TRACKING once ("Move the phone slowly over the floor"), but no floor plane was found.
2. **Real walking.** The position is still simulated (bug #8). Use debug Pause plus Step to demo, or wire a real `PositionProvider`.
3. **Glasses.** S3 is simulated. The Meta toolkit session is not ported (docs/21 open issue 2).
4. **Watch.** Confirm that the paired Galaxy Watch shows the steps and buzzes, and clears on Done/Stop. The phone side sends without errors.
5. **Speech loudness** at expo volume, with the media volume raised.

## Wave 2 QA (2026-09-26, about 00:30)

Four QA agents ran in parallel after the wave 1 merge: the S25 (AR, Geospatial, glasses, watch), emulator A (the three demo flows, mock-glasses replay, debug card), emulator B (splash, themes, search, persistence, process death, memory, touch targets) and a faked GPS walk from 300 m to the Classroom South entrance. Full reports with screenshots: `reports/qa-phone.md`, `reports/qa-emuA.md`, `reports/qa-emuB.md`, `reports/qa-gps.md`. Fixes made during QA: `reports/w2-fix.md`.

### Results

| Surface | Result |
|---|---|
| Cold start with the splash (S25) | 542 to 679 ms, splash 1.4 s, no white flash. System logo and first Compose frame match in size. |
| Demo A, B, C on the emulator | All pass. "Exit toward" shows (bug 16 fixed), difference line reads "rides 5 floors" (bug 12 fixed), S3 shows Done / Repeat / Back to routes on arrival. |
| Mock-glasses replay (emulator) | 10 cycles, camera OFF before speech every time, "Seen: LIBRARY SOUTH", thumbnail shows the still, **no ANR**. |
| Explore map (S25, real key) | Tiles, blue dot, clay line and sheet work. Street steps are still the straight-line fallback: the Routes API returns 403 `API_KEY_SERVICE_BLOCKED` until "Routes API" is added to the key's API restrictions. |
| GPS walk (emulator) | Map home, sheet, one "Almost there" card at 35 m, transition plays (15 in-between frames, no blank frames after the fix), S2 banner and chip, indoor chip past the door, arrival, Back, process death, no permission, offline: all pass. |
| S2 on the S25 (desk) | Position no longer moves by itself; debug Step works; "Locate me" steady after the hysteresis fix. Floor tap and arrows need a real floor. |
| Geospatial (S25, indoors) | Session starts, VPS available at the entrance, earth tracking reached, entrance anchor placed; a second session in the same run now works (fix). |
| Real glasses (S25) | The app reaches Meta AI registration; the agent did not grant "Nearby devices" or "Connect CampusMaps" (owner consent). No burst ran yet. |
| Watch (phone side) | Exactly one "Route ended: clearing the watch" per Done / End route / Back to routes / S3 Stop; reached 1 watch. |
| Themes, search, settings persistence, process death, memory, touch targets (emulator) | All pass. PSS steady at about 101 MB over four S2 cycles (no ARCore on the emulator). |

### Fixed during wave 2

| # | Bug | Fix |
|---|---|---|
| W1 | Map-to-AR transition was a jump cut (card started the animation before S2 existed) | Card starts S2 first, S2 draws underneath, 900 ms runs once frames settle (`ui/transition`, `CampusMapsApp`) |
| W2 | "Almost there" card re-fired after End route, reopening S2 | Trigger re-arms only on a new route or above 60 m |
| W3 | Map and S2 could pick different doors (S2 routed from the nearest fixed start) | S2 routes from a "Your location" node at the real fix (`ui/ExploreStart.kt`), same entrance as the map |
| W4 | Map opened on the campus centre, dot off screen | First fix frames the dot (and the entrance), then the camera is the user's |
| W5 | "Locate me" flickered | Shows after 2 s low confidence, hides after 1 s good |
| W6 | Meta AI opened inside the CampusMaps task | Glasses setup helper has its own task affinity |
| W7 | Second S2 checked VPS on a closed ARCore session | Geospatial provider stops when S2 leaves |
| W8 | Back on Explore opened from S1 exited the app | Back returns to S1 |
| W9 | "Watch not reachable" logged every second | At most once per 30 s |

### Still open

| # | Item | Severity | Note |
|---|---|---|---|
| O1 | Street directions 403 | Medium | Owner: add "Routes API" to the key's API restrictions in Google Cloud. Directions is also re-requested on every fix; cache per entrance. |
| O2 | Without ARCore the simulated walker walks the outdoor leg by itself | Medium | Only affects the emulator and the S25 until Geospatial tracks. |
| O3 | Demo mode hides the "See the map" row, so Explore is unreachable from S1 in demo mode | Low | Decide: keep (judges start indoors) or add a map icon to the S1 app bar. |
| O4 | Glasses mode says "Take the elevator" 4 times during the ride; "Seen:" keeps an old sign | Low | `glasses/`, S3 |
| O5 | Debug: link rows overlap (48 dp on a 40 dp pitch); one Force reroute counts 2; the card reopens on every screen change | Low | `ui/screens/DebugOverlay.kt` |
| O6 | With animations off, S1 flashes one frame before Explore opens as home | Low | `CampusMapsApp` home pick |
| O7 | Both CS sign photos fail ARCore's image quality check, so no image database exists | High (data) | Photograph high-texture targets (directory boards, posters) at Klaus; see `assets/anchors/SCORES.md`. |

### Only a human can test (wave 2)

1. **Walk up to Classroom South with the S25**: Explore shows the line to the recommended entrance; under 40 m the "Almost there" card appears once; Go plays the tilt-zoom-fade into S2; outdoors the chip says "AR tracking on" and chevrons point to the door once earth tracking is TRACKING with accuracy under 10 m; crossing the door switches the chip to the indoor node.
2. **AR floor tap and a 3 to 5 m walk** on a real floor: arrows lie on the floor, the dot moves only when you move, covering the camera 3 s shows "Locate me".
3. **Real glasses**: turn on Developer Mode, open Glasses mode on CS to Room 608, grant "Nearby devices", confirm "Connect CampusMaps" in Meta AI; watch `adb logcat -s GlassesLink GlassesMeta GlassesSetup GlassesMode`: "camera OFF" must precede "SPEAK start" every cycle. Then tap the CampusMaps launcher icon: the app, not Meta AI, must appear.
4. **Watch on the wrist**: the face shows each step and buzzes, returns to idle on Done and Stop, and stairs up and down look different.
5. **Shortcut persistence** on the phone: add a same-floor shortcut with the walk and 2 photos, debug Approve, force-stop, relaunch, route: the card still says "Student shortcut".
6. **Speech loudness** at expo volume with media volume raised (it was 3 of 15).

## Wave 3 (2026-09-26, about 09:00): street steps, watch, glasses polish, building editor

Verified on the S25 and the Galaxy Watch 8 Classic (watch app installed over Wi-Fi ADB):
- **Routes API** works with the key's restriction fixed: real street steps in the Explore sheet.
- **Glasses end to end**: 3 stills per burst (1080x1440, 1 to 2 s each), camera off before speech, OCR, vote, speech; about 9 s per cycle. A capture timeout used to kill the loop silently (fixed). Speech went to the phone speaker because the glasses were not connected for audio; S3 now shows "Speech: glasses / phone speaker".
- **Watch**: steps arrive within 1 s with the haptic pattern; the face opens by itself on the first step of a route; text fits inside the round screen.
- **Street steps in S2 and on the watch** (`reports/w3-outdoor.md`): each Google turn is a step, advanced by GPS (12 m), banner, "Then:", watch and glasses share the same text and GPS distance. To test on a real walk: steps advance at each corner (tune `OutdoorGps.COMPLETE_M`), the Enter step and the hand-over at the door.
- **Small fixes** (`reports/w3-small.md`): Directions cached per entrance (re-asked after 25 m), S1 map icon in every mode, debug card stays folded and rows are 44 dp, reroute counted once, no S1 flash before Explore.
- **Glasses polish** (`reports/w3-glasses.md`): no ghost text on arrival, unchanged step spoken at most once per 25 s, "Seen: nothing new" after two empty bursts.
- **Building editor** (`reports/w3-editor.md`): Settings, "Edit this building" (demo mode off): add room / hallway point / door, connect, move, rename, delete, undo, save (validator counts shown, routing reloads at once), export the merged file via the share sheet. Patches live in filesDir/patches/<code>.json and are applied on top of the asset file at load; a patch that no longer fits is skipped. Not supported: stairs, elevators, entrances, cross-floor links, signs, access hours.

## Wave 4 (2026-09-26, about 11:20): redesign merge, measured Klaus, voice, barometer

- **Teammate's campus redesign** (S0 campus picker, S0b building picker, campus skins, new S1b with Phone AR / Glasses / Map and a 3D preview, S2 progress pill) merged; the design pass made Explore, the transition card, the editor, Settings and S3 follow the skins, fixed contrast and clipping (`reports/w4-design.md`). Mascot watermarks (Buzz, the panther) replace the letters on S0 and S0b (`reports/w4-mascot.md`).
- **Klaus is measured** (`reports/w4-klaus.md`, docs/19): 14 nodes, 18 edges, 4 anchors from the survey export; Demo A S1 to 1116W 12 s / 17 m, S2 18 s / 24 m; verified on the S25 ("Head toward the glass staircase", "Turn right at the glass staircase", "Room 1116W is on your right"). Image database holds the Research Wing sign only (score 100 after an equalised crop); indoor signs stay text anchors. S1, S2 and the table are placed by hand in the atrium (flagged); door directions at 1116W and COEUS follow the videos.
- **Voice**: ElevenLabs (teammate) with a per-sentence cache and phone TTS fallback; every route pre-warms its sentences at start so the demo path stays offline after the first run. Verified on the S25.
- **Barometer floor** (`reports/w4-baro.md`): calibrates at the start node, re-zeroes on a sign fix, floor tap or hallway step; changes floor only during elevator and stair rides (and 20 s after) with 1.5 s hold and 0.6 floor hysteresis; the fake walker waits in the elevator for the pressure (10 s give-up). Emulator: F2 to F6 and F6 to F1 driven by pressure. Owner: one real elevator ride in Classroom South; tune `HPA_PER_METRE` (0.12; surveys measured 0.113) if a floor off.
- **Flow QA** (`reports/w4-flow.md`): instrumented tests 8/8, Demos A, B, C through the new S1b pass, no crashes; fixed: S1 home in demo mode again, no picker flash before Explore, light status icons on the Georgia State picker, campus colours and label follow the building (including from Explore).
- **Outdoor QA** (`reports/w4-gps.md`): the faked 300 m walk passes on the redesigned flow; "Head east. Take the stairs" punctuation fixed.

### Still open after wave 4

| # | Item | Severity |
|---|---|---|
| P1 | Klaus: S1, S2 and the table are guesses; re-survey the real table spot; a straight-on poster at the staircase scoring 75+ for an indoor image anchor; door directions at 1116W and COEUS | Data |
| P2 | Real elevator ride with the barometer on the S25; real walk to Classroom South for street steps and the door hand-over | Hand test |
| P3 | The S1b "Map" tile starts map-only guidance (camera off), not the Explore map. Decided 2026-09-26: keep as built (owner). | Closed |
| P4 | Outside start on the emulator stays on the first street step for the whole route when the fix is far from the building (fresh fix gate); real GPS is fine | Low |
| P5 | Home can jump from the picker to Explore up to 3 s after launch while location resolves; "1 rooms" counts destination rooms only; Explore keeps "Turn on location" until restart after a grant; room lost on a relaunch restored from process death; stale sheet distance near the door | Low |
| P6 | The watch shows "0 m" on the first "Head toward" step | Low |

## Wave 5 (2026-09-26, about 12:00): the app knows where you are

Owner concern: S1 asked "Where are you?" although the plan was automatic position. Root cause: indoors the first fix must come from a sign, and the image-anchor path fails on plain plaques; text reading existed only in the glasses loop. Built:
- **GPS start** (`reports/w5-gpsstart.md`): S1 opened outdoors with a fresh fix (under 30 s, better than 50 m, more than 25 m from every entrance, demo mode off) starts from "Outside: Your location" with a hint "From your location (GPS, N m)"; a manual pick is never overwritten; at the building or in demo mode the default start stays (Start 1 at the expo table).
- **VPS entrance snap** (same report): on the outdoor leg of S2, the Geospatial position (TRACKING, under 10 m) drives the banner distance and the 40 m trigger; within 15 m of any entrance of the building it snaps to that door and reroutes from it if it is not the planned one (logcat `Geo`: "VPS snap to E-WM, 6.2 m"). JVM-tested; needs the real walk.
- **Find me** (`reports/w5-findme.md`): a camera sheet from S1 that reads sign text and room numbers (ML Kit, frames scaled 2x), matches room numbers in one frame and anchor texts by a 2-of-3 vote, sets the start automatically ("You are at Room 1116W"). Verified on the emulator with the app's own sign photos (E-RWD, R-1116W, E-LM2, R-608). Owner: hold the phone an arm's length from the 1116W plaque, then at the Research Wing sign.
