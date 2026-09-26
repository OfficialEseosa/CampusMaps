# 19. Building data status (2026-09-25)

**Owner:** core library. **Files:** `app/src/main/assets/buildings/{KL,CS,CSE}.json`. **Code:** `core/` (`com.campusmaps.data`, `.routing`, `.survey`).

**Validator counts (2026-09-25 late, `ValidatorReportTest`: rule 7 against the real asset folder, rule 8 on the router's top demo routes):** KL 0 errors, 3 warnings (photos pending), 2 info; CS 1 error (known, rule 8 on the P2 route, see below), 0 warnings, 5 info; CSE 0, 0, 0. Before this pass: KL 3 errors, CS 3 errors.

All three files load, pass the validator with no ERROR or WARN (rules 1 to 6, 9 and 11; rule 7 file check is a hook the app wires to its assets, and a test checks CS's two image files) and pass the routing tests. Rule 10 reports every entrance without posted hours as INFO (5 in CS, both of KL's, none in CSE); that is expected until the hours are photographed. Rule 8 (anchor spacing on demo routes) is not wired to a test and fails on the CS routes (see below). **Measured so far: CS only** (survey CS-20260925-1238: 4 entrance fixes, 7 edges, elevator, stairs-down, walking speed, floor height, 2 image anchors). KL and CSE are entirely estimated. Everything else carries `"estimated": true` and a `notes` string. Search the files for `estimated` before trusting a number.

## Conventions used in the files

- Building frame: origin from `origin.lat/lng`, +y points at compass bearing `origin.headingDeg` (all three files use 0, so x = east, y = north), metres.
- `facing` (anchors) and `doorFacing` (rooms) are **8-point compass words** in the building frame (`north`, `northeast`, ...). The converter rounds the surveyed `facingDeg` and keeps the exact degrees in `notes` ("facingDeg 9.9"). Routing also accepts a number of degrees in these fields.
- Access: windows are matched on the day they open; a window whose close is not after its open runs into the next morning. If any `public` window covers `now` the door is public, otherwise card-only. No `access` at all means always public.
- Extensions to the docs/02 schema (ignored by anything that does not know them): `estimated`, `notes` on nodes, edges, anchors, elevators; `demoDestinations` (rule 6 input); `startPoints` (P1, P2 as lat/lng); `stairsDownSecondsPerFloor` (default 18); `stairsId` on stairs nodes (family for rule 4, defaults to the id minus its trailing `-<floor>`).
- Placeholder anchors use ids `<code>-A9x` so they never collide with survey anchors (`A01` upward).
- **Entrance geo (outdoor leg):** every outdoor entrance carries `lat`, `lng` and `headingDeg`. In the files `headingDeg` is the bearing the door **faces out** (0 = north, clockwise), because that is what CampusSurvey records (standing on the threshold facing out). The heading you face when **walking in** is that plus 180: core `Node.walkInHeadingDeg`, app `GraphNode.headingDeg`, and `CoreBridge.entranceGeo(...)` (`headingDeg` = walk in, `facingOutDeg` = file value). Rule 11 (WARN) fires for an outdoor entrance missing any of the three. CS entrances are survey fixes (Walters side placed by hand); KL and CSE entrances are map guesses flagged `estimated`.
- `imagePending: true` on an image anchor = the photo is not taken yet; its missing file is a rule 7 WARN instead of an ERROR. Remove the flag when the photo lands in `assets/anchors/<code>/`.

## Classroom South (CS.json), Demo B

Source: survey `CS-20260925-1238` (CampusSurvey 0.3, Friday 12:38 to 13:12, stride 0.768 m calibrated by `gps-walk`: 12 places, 7 walks, 2 elevator rides, 2 stair descents, 8 sign photos, 5 walkthrough videos with 5 Hz sensor logs). Converted with `ConvertMain` (test fixture `core/src/test/resources/CS-20260925-1238.survey.json`), then laid out by hand: the survey has 581 steps outside any walk and five walks with a turn inside, so the missing legs were rebuilt from the step counts in the videos' `sensors.json` (and their frames, to see where doors and turns are). The 2026-09-24 survey is superseded; only its "150" plaque (as `CS-A03`) and its Walters-side door spacing are carried over.

**Frame:** origin = Walters main GPS fix (obs #1), x east, y north. The Decatur Street facade runs along bearing 120/300; every entrance faces out north-east.

**26 nodes, 30 edges, 8 anchors.** Unestimated: 4 node positions, 7 edges (2 walks, 5 vertical), 1 anchor (CS-A01), plus the timings below. Everything else carries `"estimated": true` and a `notes` string that says where the number came from.

| Item | Measured | Estimated (and from what) |
|---|---|---|
| Entrances (5) | GPS fix (10 samples, ±3 m) and facing: `E-WM` Walters main (origin, floor 1), `E-95DS` 95 Decatur Street (floor 1), `E-LM2` Library South plaza doors (floor 2), `E-CSM2` Classroom South main (floor 2) | `E-WS` Walters side: its fix lands 42 m north of Walters main, but four walks put it within about 12 m; placed 9.4 m from `E-WM` at bearing 124 (the spacing of the two 09-24 fixes). Access hours: none photographed (all public). `E-LM2` facing unreliable (spread 57°) |
| Floor 1 | `H1`–`H2` main hallway 30.0 m (walk #16, 39 steps, spread 26°) | `H7` Walters lobby, `R-150` (18.4 m on from `H7`, so `E-WM`>`H7`>`R-150` = walk #2's 23.05 m), `H8` Decatur corner (W03: 19 steps in), `H2`–`H8` 20.0 m (W03: 65 hallway steps minus walk #16), `H3` Library South stair foot (indoor 10-sample GPS; `H3`–`H8` 20.8 m = walk #24 minus W03), `H9` elevator lobby 11.6 m past the vending machines (W04), `EL-1` +3 m, `ST-1` 9.3 m (16 steps from the stair door to boarding ride #28), `E-WS`–`H7` 7.7 m (W02: 34 steps to 150), `E-WS`–`H1` 11.5 m (W04), `H7`–`H1` 7.7 m (W03) |
| Floor 2 | `E-LM2`–`E-CSM2` 37.7 m outdoors (walk #37); walk #35 `H4`–`E-LM2` 34.6 m (split at `H10`, both halves estimated) | `H4` T junction, `H5` elevator lobby, `H4`–`H5` 17.0 m (walk #33 counted 0 steps in 13.7 s; GPS straight line, bearing matches the walk heading), `H10` where `E-CSM2` meets the hallway (no inward walk logged), `EL-2`, `ST-2` (stacked above floor 1), `ST-LS-2` 6.1 m in from `E-LM2` |
| Floor 6 | none | `H6` elevator lobby (stacked), `EL-6`, `ST-6`, `H11` bend 9.2 m and `R-608` 4.6 m after it (W05: 12 steps, right turn, 6 steps). The survey has no 608 ROOM node |
| Elevator `ELEV-1` | 2 rides 1 → 6 (Friday 13:03 and 13:05): wait 3.4 and 1.4 s, ride 22.4 and 22.0 s → **avgWaitSec 2.41, worstWaitSec 3.45, secondsPerFloor 4.44**. Stop at floor 2 inferred (6 → 2 unlogged, 3 steps) | n = 2 at a quiet hour: the wait is luck |
| Stairs | 2 descents: main stairwell 6 → 1 in 60.9 s (98 steps), Library South 2 → 1 in 20.8 s → **stairsDownSecondsPerFloor 13.6** (81.7 s over 6 floors) | **stairsSecondsPerFloor 16.6**: no upward climb was timed; descent × 22/18 (the defaults' up/down ratio) |
| Walking speed | **1.29 m/s** from the 5 walkthrough videos (223.6 m in 173.0 s, per video 1.23 to 1.38; cadence 96 to 108 steps/min). Survey edge walks (0.26 to 0.71 m/s) are survey pace and not used | |
| Floor height | **3.9 m** from the barometer: 2.21 and 2.20 hPa over 5 floors on the two rides (0.441 hPa per floor at 0.112 hPa/m) | |
| Anchors | `CS-A01` image (Walters main fascia sign), `CS-A08` image (floor-2 canopy sign): photos, facing, text | widthM of both crops derived from the recorded 200 cm sign width (A08's was itself estimated); every text anchor's position; `CS-A03` (150 plaque) carried over from 09-24 |
| Start points | none (no START nodes again) | `P1`, `P2` regenerated: 30 m outward from `E-95DS` and `E-WM`, away from the centroid of the five entrances |

**Anchors** (`app/src/main/assets/anchors/CS/`; image anchors are the straight-on shot cropped to the sign and its coplanar panel, greyscale, 1600 px long side, JPEG 85; not yet scored with arcoreimg, rule 7 needs ≥ 75):

| Id | Node | Kind | Text | Why | File |
|---|---|---|---|---|---|
| CS-A01 | E-WM | image, widthM 3.29 | CLASSROOM SOUTH | flat matte fascia sign plus panel joints; outdoors | 1600×541, 82 KB |
| CS-A02 | E-95DS | text | 95 DECATUR ST | glass door with a small plaque; logged twice (#12, #13) | none |
| CS-A03 | R-150 | text | 150 | 09-24 plaque, carried over (this session has no 150 anchor) | none |
| CS-A04 | H2 | text | STAIRS | corridor scene, not planar; 45 lux; offset recorded as 300 | none |
| CS-A05 | E-LM2 | text | LIBRARY SOUTH | lettering on glass over glass doors | none |
| CS-A06 | H3 | text | LIBRARY SOUTH | flat but low texture, 18 lux (retry as image in better light) | none |
| CS-A07 | R-608 | text | 608 | small plaque (replaces placeholder CS-A91) | none |
| CS-A08 | E-CSM2 | image, widthM 2.20 | CLASSROOM SOUTH | flat canopy sign; same wording as A01 (its stains differ) | 1600×329, 58 KB |

| CS-A92 | H10 | text, PLACEHOLDER | EXIT | exit sign inside the Classroom South main floor-2 doors (every exterior door has one) | none |
| CS-A93 | H4 | text, PLACEHOLDER | ELEVATORS | floor-2 T junction; **wording guessed**, read the real sign | none |
| CS-A94 | H9 | text, PLACEHOLDER | FLOOR 1 | floor 1 elevator lobby, floor-number plaque (aliases 1, 1ST FLOOR, LEVEL 1) | none |
| CS-A95 | H5 | text, PLACEHOLDER | FLOOR 2 | floor 2 elevator lobby, floor-number plaque | none |
| CS-A96 | H6 | text, PLACEHOLDER | FLOOR 6 | floor 6 elevator lobby, floor-number plaque | none |

A92 to A96 were added 2026-09-25 late without a survey (the 0.3 log has no sign at any lobby or on the floor-2 hallway): sign text inferred, position at the node, `estimated: true`, notes say PLACEHOLDER. Replace them on the next CS visit (item 3 below).

Both image anchors are outdoors: they confirm an entrance at the hand-off, they cannot localize anyone indoors. **Rule 8 (an anchor every 20 m on a demo route):** the P1 route to 608 (`E-LM2 > H10 > H4 > H5 > EL-2 > EL-6 > H6 > H11 > R-608`) now passes (longest stretch 17.0 m, H4 to H5); every elevator lobby has an anchor. Still failing, and left on purpose: the P2 route, 45 m `E-WM > H7 > H1 > H2` with no sign logged along the 30 m main hallway `H1`–`H2`. Fix it on the next visit with a room plaque half way along that hallway (a node there plus a text anchor); `ValidatorReportTest` lists it as the one known error.

**Resolved from the 09-24 contradiction:** two entrances are on floor 2 (`E-LM2`, `E-CSM2`), confirmed by pressure (0.5 hPa above floor 1). `E-LM2` is Library South's own door: routes through it cross the library, so library hours apply (not photographed). The 09-24 "Library South entrance" and "LibSo main entrance" street doors are not in the new survey and were dropped.

### Demo B route cards (2026-09-25, survey CS-20260925-1238)

| Start | Cards (`RouteOption.describe()` first line) |
|---|---|
| P1 | Library South entrance (floor 2), floor 2, elevator: 87 s (walk 67, wait 2, ride 18; 86 m), also via Classroom South main (floor 2) |
| | Library South entrance (floor 2), floor 2, stairs: 143 s (walk 76, stairs 66; 99 m), also via Classroom South main (floor 2) |
| | 95 Decatur Street entrance, floor 1, stairs: 167 s (walk 84, stairs 83; 109 m) |
| P2 | Walters main entrance, floor 1, elevator: 105 s (walk 80, wait 2, ride 22; 104 m), also via Walters side entrance |
| | Classroom South main (floor 2), floor 2, elevator: 133 s (walk 113, wait 2, ride 18; 145 m) |
| | Walters main entrance, floor 1, stairs: 173 s (walk 90, stairs 83; 116 m), also via Walters side entrance |

P1 and P2 pick different entrances, and P1's winner uses a floor-2 door (one elevator floor saved), which is the Demo B story. 95 Decatur by elevator (99 s) is 12 s behind P1's top card, so the 20 s card rule drops it. To 150: P1 gets Walters side 80 s (77 m of it outdoors along Decatur), P2 gets Walters main 41 s. Outdoor legs are straight lines from the start (the router's model), which here run along the facade.

### Demo B: the 60 s gap

docs/04 wants the top two 608 options from P1 to differ by at least 60 s. With the survey numbers they differ by **56 s** (87 against 143):

gap = 4 floors of stairs up (4 × 16.6 = 66 s) − (wait 2.4 + ride 4 × 4.44 = 20 s) + extra walk to the stair doors on floors 2 and 6 (2 × 6.3 m = 12.6 m, 10 s) = 56 s.

`RouterTest.cs608TopTwoFromP1DifferByAMinute` stays `@Ignore`. The one estimated term decides it: stairs up is the measured descent × 22/18. Each 1 s per floor moves the gap by 4 s; at ≥ 17.6 s per floor up the gap is ≥ 60 s (the old 22 s default would give 78 s). The elevator wait (2.4 s from two lucky rides) cuts the other way: every extra second of real wait shrinks the gap by one. Do not tune either; time them. If a measured climb still leaves it under 60 s, docs/04 says move the start points, not the numbers.

### What one more 15-minute visit would fix (in this order)

1. **One timed climb UP, main stairwell 2 → 6** (or 1 → 6) at normal pace, Stairs button at the bottom, tap at the top: replaces the only estimated timing and settles the 60 s gap. 2 min.
2. **3 more elevator rides 2 → 6 and 1 → 6**, ideally at a class change (:50 to :05): n = 2 now. 5 min, can overlap with 1.
3. **Elevator lobbies:** an ELEVATOR node at the call buttons on floors 1, 2, 6, a STAIRS node at each stair door, and an edge walk lobby → buttons and lobby → stair door (replaces `H9`/`H5`/`H6` → `EL-*`/`ST-*`, all estimated). One sign anchor per lobby (fixes rule 8). 4 min.
4. **Floor 6:** ROOM node at 608 with door side, edge walk lobby → bend → 608 with a node at the bend (replaces `H11`, `R-608`). 2 min.
5. **Floor 2:** edge walk from inside the Classroom South main doors to the hallway, and re-walk T junction → elevator lobby holding the phone (walk #33 counted 0 steps). 1 min.
6. **Walters side:** re-take the ENTRANCE fix standing outside the door for 10 s (current fix is 42 m off), then edge walk to the Walters lobby. 1 min.

Not in 15 minutes: posted hours at all five doors (and Library South's hours), START nodes P1/P2, and an anchor every 15 to 20 m along the main hallway.

## Klaus (KL.json), Demo A

**Nothing measured.** A plausible 17-node atrium graph: south entrance `E-S`, expo table `T` with starts `S1` and `S2` 4 m either side, atrium spine `H1`–`H2`–`H3`, west hallway `H4`–`W1` to destination `R-1116` (number made up), glass staircase `ST-1/ST-2`, elevator `EL-1/EL-2`, north entrance `E-N`. Six placeholder anchors (`KL-A01` to `A06`, three image, three text) spaced so rule 8 passes. Hand-checked routes: `S1 > H2 > H3 > H4 > W1 > R-1116` (44 m, 1 turn) and `S2 > H2 > H3 > H4 > W1 > R-1116` (48 m, 2 turns).

**To photograph at the Klaus survey** (image anchors with no photo anywhere: not in the CS zip, not in `assets/`; marked `imagePending: true`, rule 7 WARN):

| Anchor | Node | What | Measure |
|---|---|---|---|
| KL-A01 | H2 | Building directory board, atrium centre | width of the board |
| KL-A02 | H3 | Poster on the north atrium wall | width |
| KL-A05 | ST-1 | Sign at the foot of the glass staircase | width |

Straight-on photo, then crop to the flat sign, greyscale, 1600 px long side, JPEG 85, save as `app/src/main/assets/anchors/KL/KL-A0n.jpg`, set `widthM`, remove `imagePending`. If the survey picks other signs, drop these three instead.

Entrance geo: `E-S` (origin, facing out 180) and `E-N` (facing out 0) are guesses near Klaus on the Georgia Tech campus. The survey's ENTRANCE fixes (10 s facing out) replace them.

### What the Klaus survey must capture (Hour 0 to 2)

1. Our expo table position (TABLE as a WAYPOINT node) and two start points within 10 m of it.
2. The demo destination room (real number), ROOM node with door side.
3. INTERSECTION nodes at each decision point from the table to the room, edge walks between them (target under 60 m, at most 2 turns).
4. 8 to 12 anchors on the route, one at each decision point, at most 15 to 20 m apart: directory board and posters as image anchors (width measured), room plaques and signs as text anchors.
5. Main south entrance GPS and facing (Geospatial is not used in Demo A, but the origin needs one fix).
6. Glass staircase and elevator nodes on floors 1 and 2, if the route or the barometer test uses them.

## Student Center East (CSE.json), Demo C

**Nothing measured.** A 12-node placeholder: `E-MAIN` (access Mon to Thu 08:00 to 22:00, Fri 08:00 to 20:00, Sat 12:00 to 20:00, Sun 14:00 to 20:00 public, from engagement.gsu.edu, card-only otherwise, inferred), `E-WEST` (public 07:30 to 23:00, assumed, the redirect target), lobby `H1`, elevator lobby `H3`, west corridor `H2`, elevators and stairs floors 1 to 2, floor-2 corridor `H4`–`H5`, `R-220`. Five text anchors (`STUDENT CENTER EAST`, `ELEVATORS`, `FLOOR 2`, `210-230`, `220`). On Saturday at 21:00 the router gives "Heads up: Main entrance is card-only now. Using West entrance instead."; at 14:00 no notice.

### What the CSE survey must capture (Saturday trip, before 20:00)

1. Every entrance: ENTRANCE node with GPS and facing, and a **Note photo of the posted hours and card reader** for each. Find the one that stays open latest; that is the redirect target.
2. The route to the demo room: ENTRANCE → junctions → elevator or stairs → floor 2 → room, with a node at every turn and an edge walk per segment (2 to 3 turns).
3. The redirect leg: GPS at the closed entrance and the open one, and the outdoor walk between them (an edge with the step count).
4. Text anchors on the route, large text preferred: the elevator sign, the floor number, room range signs, the room plaque. Take the 2 to 3 m shot for the glasses OCR check.
5. One elevator ride 1 → 2 and one stair climb 1 → 2.

## Regenerating a draft from a survey

`ConvertMain` writes the draft building JSON and prints the gap report (missing items, warnings, validator problems). It is not wired to a Gradle task; build the classes, then run it with the three jars from the Gradle cache.

Git Bash, from the repo root:

```bash
./gradlew :core:classes
G=~/.gradle/caches/modules-2/files-2.1
CP="$(cygpath -w core/build/classes/kotlin/main);$(cygpath -w $G/org.jetbrains.kotlin/kotlin-stdlib/2.4.10/*/kotlin-stdlib-2.4.10.jar);$(cygpath -w $G/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.11.0/*/*1.11.0.jar);$(cygpath -w $G/org.jetbrains.kotlinx/kotlinx-serialization-json-jvm/1.11.0/*/*1.11.0.jar)"
java -cp "$CP" com.campusmaps.survey.ConvertMain path/to/survey.json draft-CS.json
```

PowerShell, from the repo root:

```powershell
.\gradlew.bat :core:classes
$G = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$cp = @("core\build\classes\kotlin\main",
  (Get-ChildItem "$G\org.jetbrains.kotlin\kotlin-stdlib\2.4.10\*\kotlin-stdlib-2.4.10.jar").FullName,
  (Get-ChildItem "$G\org.jetbrains.kotlinx\kotlinx-serialization-core-jvm\1.11.0\*\kotlinx-serialization-core-jvm-1.11.0.jar").FullName,
  (Get-ChildItem "$G\org.jetbrains.kotlinx\kotlinx-serialization-json-jvm\1.11.0\*\kotlinx-serialization-json-jvm-1.11.0.jar").FullName) -join ";"
java -cp $cp com.campusmaps.survey.ConvertMain path\to\survey.json draft-CS.json
```

The draft is never copied over `CS.json` wholesale: merge the measured nodes, edges and anchors into the hand file, and clear `estimated` on what the survey replaced. What the converter does: node ids per CLAUDE.md (`E-<initials>`, `R-<number>`, `EL-<floor>`, `ST-<floor>`, `H<n>`, `W<n>`; START nodes become `startPoints`), origin at the first entrance fix, outdoor nodes from GPS, indoor nodes dead-reckoned along edges (steps × session stride, walking heading) unless the heading spread is over 45°, then indoor GPS (flagged). Anchors: image if width ≥ 40 cm, else text; position = nearest node minus `offsetFromNodeM` along the facing. Elevator rides give vertical edges and one `ELEV-1` spec (mean wait, max wait, mean seconds per floor); stairs climbs give `stairsSecondsPerFloor`. The elevator and stairs field names are now confirmed by the first real 0.3 export (CS-20260925-1238): elevator `fromFloor`, `toFloor`, `waitSec`, `rideSec`; stairs `fromFloor`, `toFloor`, `durationSec`, `steps`; both carry a `pressureTrace`. The observation's own `floor` is the floor selector after the ride (the arrival floor), so `fromFloor` falls back to `floor` only when that differs from the arrival floor. Older names are still read leniently (`arrivalFloor`, `boardedAt − calledAt`, `doorsOpenedAt − boardedAt`, numbers as strings, junk ignored). A ride with no floors is skipped. Stairs climbs give `stairsSecondsPerFloor` (up) and `stairsDownSecondsPerFloor` (down) as total seconds over total floors; the report lists "an UPWARD stairs climb" as missing when only descents were timed.

Fixes from the real 0.3 log (2026-09-25): a walk with 0 steps (walk #33) gets its length from the GPS straight line between its end nodes (fixes ≤ 10 m) or else duration × this session's median survey pace, is flagged estimated and is never dead-reckoned; a walk with heading spread over 45° is noted "probably a turn inside the walk; split it with an intersection node"; an anchor logged twice (CS-A02) is kept once; an `offsetFromNodeM` over 20 m (CS-A04's 300) is ignored; `widthEstimated` sets the anchor's `estimated`; `strideMethod` goes into the draft notes; a ride's EL nodes are placed 3 m from a same-floor node named like "elevator lobby" (along the heading saved there) and joined to it by an estimated edge; the report lists the walkthrough videos. The converter still cannot use the videos: their legs were rebuilt by hand (see the CS section).

## Klaus refresh, step by step

For the owner, right after the Klaus survey. About 20 minutes. Nothing here needs the phone.

**At Klaus, in CampusSurvey, name things so the merge can find them:** the two start spots by our table as Places of type waypoint named exactly **S1** and **S2**, the table as **T**, the demo room as **Room <number>** (it becomes `R-<number>`). Every door: Place → Door, facing out, 10 s (this gives `lat`, `lng`, `headingDeg`).

1. **Export** from CampusSurvey (Review → Export → Drive). Download the zip, for example `KL-20260926-0930.zip`, and put it in the repo root `C:\Users\rapha\CampusMaps\` (zips there are gitignored). No need to unzip it.
2. **Convert**, in PowerShell from the repo root:

   ```powershell
   .\gradlew.bat :core:classes
   $G = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
   $cp = @("core\build\classes\kotlin\main",
     (Get-ChildItem "$G\org.jetbrains.kotlin\kotlin-stdlib\2.4.10\*\kotlin-stdlib-2.4.10.jar").FullName,
     (Get-ChildItem "$G\org.jetbrains.kotlinx\kotlinx-serialization-core-jvm\1.11.0\*\kotlinx-serialization-core-jvm-1.11.0.jar").FullName,
     (Get-ChildItem "$G\org.jetbrains.kotlinx\kotlinx-serialization-json-jvm\1.11.0\*\kotlinx-serialization-json-jvm-1.11.0.jar").FullName) -join ";"
   java -cp $cp com.campusmaps.survey.ConvertMain KL-20260926-0930.zip draft-KL.json --keep app\src\main\assets\buildings\KL.json
   ```

   It reads `survey.json` out of the zip, writes `draft-KL.json` and prints three blocks: the gap report ("Missing versus docs/02 target"), **"Kept from KL.json"**, and **"Validator on the merged draft"**. (Tried 2026-09-25 on the real `CS-20260925-1238.zip` with `--keep CS.json`: works.)
3. **What `--keep` carries over from the old KL.json** (the survey cannot know these): node ids the app uses (a draft node of the same type named `S1`, `S2`, `T`, or with the old node's name, takes the old id), `demoDestinations` (only if the room exists in the draft), `startPoints` (if the survey has no START node), and `access` windows per entrance (matched by id, then by name). Everything else (positions, lengths, anchors, timings) comes from the survey.
4. **Check the printout:**
   - "Kept from": you want `renamed ... to S1`, `to S2`, `kept demo destination R-...`. A line `DEMO DESTINATION ... IS NOT IN THE SURVEY` means the room was named differently: rename that node in the draft by hand and add it to `demoDestinations`.
   - "Validator on the merged draft": no `rule 1` to `rule 6` lines. A `rule 5` line (cannot reach) means a walk is missing between two places: add the edge by hand (length = steps × stride) or re-walk it.
   - "Missing versus docs/02 target": fewer than 8 anchors is fine for a first pass if rule 8 passes on S1 → room.
5. **Replace the file:** copy `draft-KL.json` over `app\src\main\assets\buildings\KL.json`. Then hand-fix only: `name` fields you want nicer, a `hint` on an edge, `doorFacing` on the room if missing. Measured things carry no `estimated` flag; leave them that way.
6. **Photos:** for every image anchor in the draft, crop the survey's `KL-Axx-straight.jpg` (in the zip) as described under Klaus above and save it as `app\src\main\assets\anchors\KL\KL-Axx.jpg`.
7. **Test:** `.\gradlew.bat :core:test :app:testDebugUnitTest`. Expect failures in tests that hard-code the guessed Klaus layout (`RouterTest.klausRoutesFromBothStartsMatchTheHandCheckedLists`, `BuildingDataTest.everyGuessIsFlagged` and `anchorSpacingRule8` for KL, `CoreBridgeTest` checks on `R-1116`, `KlausRefreshTest` if `R-1116` is gone). Update their node lists to the new route; do not bend the data to the tests. `ValidatorReportTest` prints the new counts.
8. Commit `KL.json` and the photos in one commit ("data: KL from survey KL-...").

`KlausRefreshTest` is the regression for this path: it zips the CS 0.3 log the way CampusSurvey exports it, runs `ConvertMain` with `--keep`, and relabels the same log as a Klaus session to check the S1 / demo room merge. What the CS log shows the converter still cannot do: legs walked only in videos are not converted (the raw CS draft fails rule 5 until they are added by hand), and a room reached only in a video (608) is not a node. Nothing is missing from the log format itself.

## Tests

`./gradlew :core:test` (52 tests: 51 pass, 1 skipped, the 60 s gap above). `ValidatorReportTest` runs the full validator (images, demo routes) and prints the counts; `KlausRefreshTest` covers the refresh path. `SurveyConverterTest` runs on both real logs (`CS-20260924-1614`, `CS-20260925-1238`). The building JSON directory is a declared input of the test task, so data-only edits re-run the tests.

## CampusSurvey 0.3 log additions (2026-09-25)

Format stays `campussurvey-log` v2. Two optional keys: `session.strideMethod` (`gps-walk`, `known-distance`, `typed`) and `anchor.widthEstimated` (true when the width was estimated with a phone or A4 sheet rather than taped). Both are read since 2026-09-25: `widthEstimated` sets the anchor's `estimated`, `strideMethod` is copied into the draft notes.
