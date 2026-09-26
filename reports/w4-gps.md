# W4 outdoor QA: faked GPS walk after the campus redesign (branch w4/gps)

Emulator-5558 (API 36, Google APIs, no ARCore, no Wearable API), 2026-09-26 10:30 to 11:00. Demo mode off.
Destination Room 608, Classroom South. Fixes with `adb emu geo fix` (longitude first) every 2 s.
From the start point (33.7545, -84.3900) core picks **E-WM Walters main entrance** (33.752882, -84.387688), not Library South.
Google (Routes API, working now) gave 3 street steps, logged with their end points (new log line, commit 892b93d):
`CS/E-WM: DIRECTIONS 282 m, 3 steps: STRAIGHT 33.754343,-84.389676 24 m | STRAIGHT 33.753102,-84.387549 240 m | RIGHT 33.752970,-84.387747 18 m`

## Steps

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Where a cold start opens after the redesign | Pass, with a flash (bug 2) | fix 300 m away: the splash, then **S0 "Choose your campus" for about 1 s**, then the Explore map. Explore becomes home through `showExploreAsHome` from S0. The emulator's first cold start opened S0 and stayed there: the last fix was a stale one near the door (no app had listened since 10:01), so the home rule was right |
| 1 | Path to CS 608 on the map | Pass | Explore home: tap the **CS 608** chip under the search field (or the search field, which opens S1). From S0: GSU, Classroom South, then the **map icon** on S1's top bar. S1b's new "Map" tile is **not** the Explore map: it is S2 with the camera off (`GuideMode.MAP`) |
| 1 | Sheet shows Google's street steps | Pass | shot 01: 282 m, 4 min, Floor 1, "Head southeast on Marietta St NW...", "Continue onto Decatur St. SE", "Turn right onto Central Ave SW...", "Enter Classroom South at the Walters main entrance" |
| 1 | Directions cache: no request on small moves | Pass | 5 jitter fixes within about 10 m: 0 `request for` lines. While walking 15 m per fix a request comes every 25 to 40 m (by design, `DirectionsCache.MOVE_M`) |
| 2 | S2 from "Start AR navigation" at 300 m: banner, GPS distance, "Then:" | Pass | shot 02: "Head southeast on Marietta St NW toward Peachtree St, in 35 m", "Then: Continue onto Decatur St. SE". The 35 m is the GPS straight line to the step end |
| 2 | Steps complete within 12 m of their end | Pass | `step 0 done (GPS 11 m)`, `step 1 done (GPS 10 m)`, `step 2 done (GPS 10 m)`, `step 3 done (GPS 11 m)` (the Enter step) |
| 2 | Walker does not advance outdoor steps while fixes are fresh | Pass | "Continue onto Decatur St." stayed current from 249 m down to 12 m over 60 s while the walker ran |
| 2 | "Enter Classroom South at the ..." then indoor, chip switches | Pass (chip partly) | Enter shown 3 s ("in 11 m", "Then: Turn left at Walters lobby"), then `entrance reached: indoor steps from E-WM`, banner "Turn right at Elevator lobby turn", then "You have arrived, Room 608 is ahead". I did not catch the chip text in the 3 s window; qa-gps saw it switch to the indoor node |
| 2 | WatchBridge text = banner | **Could not check** | this emulator has no Wearable API (`Watch not reachable: API_UNAVAILABLE`); WatchBridge logs the step text only after a watch node is found. Check on the S25 |
| 3 | "Almost there" card once under 40 m, from the map | Pass | first map walk: card fired between the 45 m and 37 m fixes, advanced by itself. Second approach (after End route, out to 80 m, back): nothing at 45 m, card at 35 m (shot 03), once |
| 3 | Go opens S2 through the transition, no jump cut | Pass | see Transition. Logs `Handoff: S2 ready after 2083 ms`, `map-to-AR ran 1332 ms, 7 in-between frames` |
| 3 | No card after End route, re-arm only above 60 m | Pass | End route at about 30 m, then fixes at 25 to 35 m: no card. Out to 80 m and back to 35 m: the card again |
| 4 | Manual AR button from 300 m: transition, no card | Pass | `S2 ready after 1957 ms`, `map-to-AR ran 1262 ms, 12 in-between frames`, no card |
| 5 | End route on S2-from-map | Pass | back to the Explore map with the route, no card |
| 5 | Back on S2-from-map / Back on Explore | Pass | S2: back to the map, no card. Explore (home): leaves to the launcher |
| 5 | Done on arrival | as before | goes to S1 (reset), not the map. S1 then shows the wrong campus (bug 1, shot 07) |
| 5 | Process death on S2-from-map | Pass, no crash | HOME, `run-as kill -9`, relaunch near the door: S0, no crash. Revoking location (which kills the process) and relaunching restored S2 with the same route |
| 5 | Offline (Wi-Fi and data off) | Pass | sheet "Enter at the Walters main entrance · straight line", 279 m, "Walk to the Walters main entrance" (shot 08). S2: `S2 outdoor steps: 1 by GPS (0 from Directions): Walk to Walters main entrance`, banner "in 279 m" (shot 09). No crash. Network restored |
| 5 | No location permission | Pass | map with "Turn on location to see your route" (shot 10), no crash |
| 6 | 3D preview and S1b Start with a "Your location" start | **Not done** | out of time |

## Transition (card path, 4 fps from a screen recording, shots 03 to 06)

0.5 s: the card rises over the sheet ("Almost there. Point your camera ahead", Walters main entrance) and the map camera zooms in on the dot.
0.5 to 6 s: the card stays up over the map. 6 s: the card goes; the map holds still for about 1 s while S2 is composed underneath at zero alpha.
7 s: the map and sheet scale up toward the centre (shot 04). 7.25 s: S2 (banner, Locate me card, minimap) fades in over the enlarged map (shot 05).
7.5 s: S2 alone (shot 06). No blank frame, no jump cut. On the loaded emulator only 7 in-between frames were drawn (15 in qa-gps).

## Bugs

| # | Severity | Bug | Repro | File | Fix |
|---|----------|-----|-------|------|-----|
| 1 | Medium (open, not my file) | After a cold start the campus is always Georgia Tech: S1 reads "Georgia Tech · Classroom South" with the gold skin | cold start on Explore, CS 608, Start AR, Done (or search) | `ui/MainViewModel.kt`: `_campus` is set from `settings.value.buildingId` at construction, before DataStore loads; `startFromExplore` calls `app.settings.setBuilding` without updating `_campus` | in `startFromExplore` set `_campus.value = Campuses.of(buildingId).id`; and update `_campus` when the stored building first loads |
| 2 | Low (open, not my file) | Cold start far away shows S0 for about 1 s before Explore | cold start 300 m away | `ui/CampusMapsApp.kt`, top: the blank-until-decided box checks only `screen == Screen.DESTINATION`; the first screen is now `CAMPUS` | `if (!homeDecided && (screen == Screen.DESTINATION \|\| screen == Screen.CAMPUS))` |
| 3 | Low (fixed, 58d1dbe) | Two sentences of a Google step ran together: "Head east Take the stairs", here "...Shirley C. Franklin Blvd Destination will be on the left" | any Routes API step with a second line | `outdoor/DirectionsClient.kt` `plainText`, and the sheet's Routes parser used `replace('\n', ' ')` | a block break (`<div>`, `<br>`, newline before a capital) becomes ". "; a newline before a lower-case word stays a space; no double dot after "St.". Both parsers use it. Test `blockBreaksBecomeSentences`. Rebuilt; not re-walked on the device |
| 4 | Low (open) | Near the door the sheet distance stays at the last Directions answer (69 m shown while 25 m away), because the cache returns the old route for moves under 25 m | walk the map to 25 m | `outdoor/ExploreViewModel.kt` `recompute` | when using the cached route, show the distance from the fix's nearest point on the cached polyline to its end |
| 5 | Low (open) | Granting location while the map is open keeps "Turn on location" until the map is hidden and shown again | revoke, open app, grant with `pm grant` | `outdoor/ExploreViewModel.kt` (`hasPermission` read only in `onVisible`) | recheck on resume (the real permission dialog path may already do this) |
| 6 | Low (open) | The map loses the Room 608 selection after a relaunch that restored S2 | revoke location on S2, relaunch, End route | `outdoor/ExploreViewModel.kt` (selection not saved) | re-select from `trip.destination` when the map returns (the existing LaunchedEffect only runs when the trip changes) |

## What the owner must test on the real walk
- Street steps move at each corner within 12 m and the banner counts down; the watch shows the same text and distance (not checkable here).
- The "Enter ..." step shows about 3 s, then the chip switches to the indoor node.
- The card fires once at about 40 m from the Walters main entrance (it measures to the entrance, not Google's end point, which is 11 m away), and the transition looks smooth on the S25.
- The street text now reads "... Blvd. Destination will be on the left" on the sheet and S2.
- Bug 1 (wrong campus label after Explore) for the flow owner.
- Step 6 (3D preview and S1b Start from "Your location") was not tested.

## Commits
- 892b93d outdoor: log Directions step end points
- 58d1dbe outdoor: a block break in a Google step becomes a full stop (Head east. Take the stairs)
