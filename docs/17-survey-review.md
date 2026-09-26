# 17. Review of survey session CS-20260924-1614

**Session:** Classroom South, Thursday 2026-09-24, 16:14 to 16:31 (17 min), Raphael, survey collection on the S25 Ultra. **Captured:** 6 nodes, 2 edges, 2 anchors (6 photos). **Not captured:** elevator, stairs, notes, videos, start points, floors 2 and 6. **Reviewed:** 2026-09-25 from `survey.json`, `README.txt` and all six photos.

## Verdict

As a **smoke test this session went well**. It proves the app works in the field: entrance GPS averaged to 3 to 4 m, ML Kit read real signage, the barometer was stable (0.23 hPa spread on one floor), the export was clean, and the "heading wandered" warning fired when it should have. As a **map it is not usable yet**. It holds roughly 10% of what `CS.json` needs for Demo B. There are no start points, no elevator bank, nothing on floors 2 or 6, and no room 608. There are no stopwatch runs and no videos. Neither anchor can be used as an indoor image anchor. Every distance is also scaled by a stride that is almost certainly wrong (0.417 m, while the GPS implies at least 0.56 m).

The time was not well spent either. Only 24% of the steps walked were logged as edges. About 8 minutes went to walking with no edge open, and the anchors took about 2.4 minutes each.

**Demo B is at risk.** The only window left is the Saturday 5 to 10 PM trip, which also has to fit the Demo B and Demo C recordings. A 60 to 75 minute survey, then conversion and a rebuild, then recording, does not fit into daylight plus the Student Center's 8 PM close. There is a second risk: one third-party listing says Classroom South is **closed on Saturdays and Sundays**, and after 8 PM GSU buildings need a PantherCard ([GSU building access](https://safety.gsu.edu/safety-and-you/building-access/), [campus-maps listing](https://www.campus-maps.com/gsu/classroom-south-clso/)).

**Recommendation:** send one surveyor back to Classroom South **today (Friday) between about 9:30 and 12:30**, while the building is open on weekday hours, to run the 70-minute protocol in section 4. Then go to Klaus check-in at 2 PM. The team can then convert `CS.json` and build `CS.imgdb` during Hours 2 to 10. On Saturday the trip only records. While on site today, **photograph the posted hours at every door** so you know whether Saturday entry is possible. If today is impossible, the fallback is to survey during the first hour of the Saturday trip (5:10 to 6:15 PM), convert and install on a laptop on site (6:15 to 6:45), and record Demo B from 6:45 to 7:30. In that case Demo C drops to phone-camera fallback or simulated time, and the outdoor leg is the first thing cut.

---

## 1. What was captured vs what CS.json needs for Demo B

Requirements come from [01-demos.md](01-demos.md), [02-building-data.md](02-building-data.md) and [09-data-capture.md](09-data-capture.md).

| Requirement | This session | Status |
|---|---|---|
| Start points P1, P2 (`START` nodes, averaged GPS) | none | **missing** |
| About 5 entrances: 3 on floor 1, 2 on floor 2 (one is the indoor Library South connection), each with floor, side, lat/lng, facing heading, hours | 5 `ENTRANCE` nodes, all on floor 1 and all outdoor, GPS 3.2 to 4.1 m, headings recorded. 2 of them look like Library South's own street doors (see section 2). No floor-2 entrance, no library connection, no hours | **partial** |
| Building origin (a physical spot plus lat/lng) | none chosen | **missing** |
| Hallway graph, floor 1 | 2 edges (5→1 is 108 steps with a turn inside it, 8→5 is 35 steps), 0 intersection nodes, 0 waypoints | **partial** (about 10%) |
| Hallway graph, floors 2 and 6 | none. The floor never changed from 1 | **missing** |
| Elevator bank node on floors 1, 2 and 6 (`EL-1`, `EL-2`, `EL-6`) | none | **missing** |
| 5 timed elevator waits (call to *any* door opening), average, worst | none | **missing** |
| Elevator ride 1→6 with barometer trace (floor delta) | none | **missing** |
| One stair flight timed up and down | none | **missing** |
| Room 150 (floor 1) | `ROOM` node, doorSide AHEAD, 1 edge to "Walters side", plaque photographed | **partial** |
| Room 608 (floor 6) | none | **missing** |
| Anchors: 8 to 12 per route, ≤20 m apart, ≥2 per floor, one at each elevator lobby, measured width, exact text | 2 anchors. A01 is an outdoor building sign; A02 is a photo of a door with a small plaque. Neither width was measured, and the A02 text is incomplete | **partial** (about 1 of 20 usable, as a text anchor) |
| Stopwatch runs P1→608 and P2→608 through each candidate entrance, top two differing by ≥60 s | none | **missing** (the single most important check for Demo B, per doc 09) |
| Walking speed (50 m timed at normal pace) | edges suggest 0.57 to 0.77 m/s, a slow surveying pace and not usable as the ETA speed | **missing** |
| Barometer sanity test (stairs ×2, elevator ×1) | pressure on floor 1 only | **missing** |
| Walkthrough videos (≥4: both start points through each candidate entrance) | 0 | **missing** |
| Access notes (posted hours, card readers, library hours) | 0 notes. A reader post is visible in `CS-A01-angle.jpg` but was not logged | **missing** |

## 2. Data-quality findings

| # | Finding (from the numbers) | Consequence | Fix |
|---|---|---|---|
| 1 | **Stride 0.417 m** (`strideCalibrated: true`), so the 10 m calibration counted 24 steps. For an adult, a normal step is 0.67 to 0.79 m, roughly 41% of height ([marathonhandbook](https://marathonhandbook.com/average-stride-length/), [timetowalk](https://timetowalk.net/guides/science-of-stride-length/)). The session's own GPS disagrees too: between nodes 5 and 1 the straight line is 60.1 m for 108 steps (≥0.56 m/step), and between nodes 3 and 4 it is 89.5 m for 156 steps (≥0.57 m/step). A walked path is never shorter than the straight line, so the true stride is **at least 0.56 m and probably 0.65 to 0.75 m**. | Every `lengthM` is scaled by the same factor. With the recorded stride, distances are **25 to 45% too short**, so indoor ETAs come out too short. Worse, the gap between two entrance options shrinks, and that gap is exactly what Demo B is about. | Recalibrate on a **taped** 10 m at the pace you survey. Accept 0.55 to 0.95 m only. Rescale the existing edges with `steps × newStride`; the log keeps raw steps, so nothing is lost. |
| 2 | **Walking pace**: edge 7 took 108 steps in 78.3 s (83 steps/min). Edge 10 took 35 steps in 29.7 s (71 steps/min). That is 0.57 to 0.77 m/s depending on stride. | Edge durations are survey pace, not user pace. Used as ETAs they would be about 70% too slow. | Take `walkingSpeedMps` from one timed 50 m walk at normal pace (doc 09). Never use edge durations for it. |
| 3 | **All six nodes are outdoor `ENTRANCE` nodes on floor 1**, including "Library South entrance" and "LibSo main entrance". Pressure at node 3 is 982.47 hPa against 982.56 at node 1, about 0.75 m higher and **not one floor** (≈0.42 hPa). So both are street-level doors of Library South, not the floor-2 indoor connection that doc 02 describes. | Routing would offer two library street doors as Classroom South entrances with outdoor Geospatial legs. The real floor-2 connection (indoor, library hours, P3 start) is still unmapped. There are also 0 floor-2 entrances, so the "a floor-2 door saves an elevator floor" story cannot be told. | Keep them only if a Demo B route really goes through the library. Otherwise drop them or mark them `note: Library South`. Capture the library connection on floor 2 with outdoors off. |
| 4 | Nodes 1 ("95 Decatur Street entrance") and 2 ("Library South entrance") are **3.6 m apart**, within GPS error. The building's address is 95 Decatur St SE, and its main entrance is at Decatur and Central ([GSU calendar](https://calendar.gsu.edu/classroom_south), [New South](https://newsouthconstruction.com/project/gsu-classroom-south/)). | Two entrances 4 m apart are either one doorway logged twice or two adjacent doors. If they are one doorway, the options screen shows two cards with the same ETA. | Confirm on site and write it on the sketch. |
| 5 | Node 1's facing-out heading has **57.5° spread**; the other entrances have 2 to 5°. | The Geospatial arrow to that door and the entrance's `headingDeg` are unreliable. | Re-take node 1 standing still for the full 10 s. |
| 6 | **No `START` nodes** for P1 and P2. | Demo B's core claim ("two start points pick two different entrances") cannot be computed or checked. The outdoor virtual node has nothing to measure from. | Drop P1 and P2 first, then place them so that different entrances win (section 4). |
| 7 | **Edge #7 (5→1) has 55.2° heading spread**; the app warned. 108 steps × 0.417 = 45 m, while GPS gives 60 m straight-line, so the walked path is 60 to 78 m at a realistic stride. The end reading is 144 lux, so the edge ran through the building. | One edge contains at least one turn. AR guidance draws a straight ribbon through a wall, the turn instruction is missing, and there is no node to hang an anchor on. | Split every walk at each turn with an intersection node. Keep each edge straight (spread under 25°). |
| 8 | **"Room 150" node: heading spread 64.7°, GPS accuracy 10.1 m** (indoor). | Indoor GPS carries no position information. The room's position has to come from edges, and the only edge is #10 (35 steps to "Walters side"). The 64.7° heading is noise, and `doorSide` is the only usable orientation. | Converter: ignore GPS for indoor nodes, position them from the edge chain. Stand still when saving. |
| 9 | **Edge #10 GPS accuracy 22.5 m** (a single sample at the end, 4954 lux: at or just outside a door). | No effect if the converter uses steps, which it should. Harmful if anything averages GPS into an indoor position. | Converter: GPS only for `ENTRANCE` and `START` nodes, and only when accuracy ≤ 6 m. |
| 10 | **Only 143 of 598 steps (24%) were walked inside edges.** Walks of 38 (2→3), 156 (3→4), 38 (4→5) and 110 (1→8) steps were not logged. | The walk from 95 Decatur to room 150 (110 steps) is missing, so room 150 connects only through "Walters side". Four entrances have no indoor edge at all, which makes the graph disconnected (validation rule 5 fails). | Every move between two nodes is an edge (section 6, suggestion 2). |
| 11 | **Both anchors `widthCm: 200`.** A01 is a building fascia sign about 4 m up, above the canopy. From the photos it is about 3.5:1 and 2 to 3 m wide. It could not have been taped, so 200 is an estimate. A02's "200" matches the **double door** (about 1.8 to 2 m), not the "150" plaque, which is about 15 to 20 cm. | ARCore uses `widthM` to size the image, so a wrong width means a wrong distance: a 200 cm entry for a 20 cm plaque puts the anchor 10× too far away. Validation rule 7 also needs a measured width. | Tape everything reachable. For high signs use an AR measuring app, count façade panel joints, or record the width as "estimated" and let ARCore refine it. |
| 12 | **A01 `heightCm: 200`.** The photos put the sign centre about 4 m above the sidewalk (above a canopy that sits over a 2.4 m revolving door). | The floor-height fallback in the snap math (`pWorld.y - heightM`) would put the AR floor 2 m too high, so arrows float at chest height. | Estimate from the door (standard door about 2.1 m), or better, rely on a detected floor plane. |
| 13 | **A01 has no `offsetFromNodeM`, and its GPS is 27 m from its chosen nearest node** (node 4, "Walters main"). The photographer stood about 8 to 10 m in front of the sign, so the sign is still about 20 m from node 4. | Either the wrong node was picked or the sign sits over a door that was never logged as a node. The anchor-to-node link is wrong either way. | Required offset. Warn when the anchor's GPS or steps put it more than 10 m from its node. |
| 14 | **A02 `offsetFromNodeM: 0.0`, `heightCm: 160`.** The plaque sits beside the door frame, about 1 m to the side at about 1.5 m high. | A small error, but 0.0 looks like an unset default. | Required entry, no default. |
| 15 | **A02 text "150", but OCR read "AUDITORIUM 150 …".** These are two separate plaques: "AUDITORIUM" left of the door, "150" plus the GSU logo right of it. Character height at 896 px is 5 px on the straight shot and 3 px on the far shot, against a 16 px floor. | Demo C-style OCR from glasses at 2 to 3 m **cannot read this plaque**. A phone OCR text anchor needs the phone at arm's length. "AUDITORIUM" is a second, independent text anchor. | Log two text anchors: `150` (aliases `15O`, `I50`) and `AUDITORIUM`. |
| 16 | **A02 blur: far 122 vs straight 554; A01 far 1794 vs straight 463.** Looked at directly, the A02 far shot is **not visibly soft**. The frame is mostly blank wall and concrete, and a Laplacian-variance score drops on textureless scenes. A01's far shot scores *higher* because it contains more façade detail. | The blur score cannot be compared between scenes, and the "soft" warning was probably a false positive here. | Score only the frame-guide region (section 6, suggestion 9). |
| 17 | **No elevator or stairs observations; floor never changed from 1.** | No elevator nodes means no floor 6, no route to 608, no `avgWaitSec` or `worstWaitSec`, no floor-delta calibration for the barometer display, and no stairs alternative for the accessibility toggle. Four of Demo B's five takes cannot be computed. | Section 4, minutes 22 to 50. |
| 18 | Pressure stayed between 982.39 and 982.62 hPa across 17 minutes. | Good news: the floor-1 baseline is stable to about 0.2 hPa, well under the 0.42 hPa per floor we expect. The barometer display should work once there is a trace. | Keep. |

### The six photos

| Photo | What it shows | Image-anchor verdict |
|---|---|---|
| `CS-A01-straight` | Aluminium fascia panel "CLASSROOM SOUTH" with GSU logo, about 4 m up over a revolving door. Overcast, no glare, level (pitch 3.4°, roll -1.3°). The sign fills about 47% of the width and about 6% of the frame; the rest is glass with reflections of people. | **Flat and matte-ish, but low texture**: thin dark letters on white plus one logo, with a wide aspect ratio. Crop it to the sign before scoring. Expect a borderline score. It is **outdoors**, so it cannot localize anyone indoors. At most it confirms an entrance at the hand-off. |
| `CS-A01-far` | Same sign from a similar distance, sharp. | Good for the Geospatial hand-off hint image. |
| `CS-A01-angle` | About 30° from the left. Shows a secondary double door on the far left and a reader post right of the swing door. | Log the reader post as a Note (access rule evidence). |
| `CS-A02-straight` | A **wood-veneer double door** with a small "AUDITORIUM" plaque on the left and "150" plus the GSU logo plaque on the right (plaque about 8% of the frame width). Slight keystone. | **Not an image anchor.** Identical veneer doors repeat through the building (collision), a door changes appearance when it opens, and the plaque crop is about 250×230 px, under ARCore's 300×300 minimum. Use it as a **text anchor** only. |
| `CS-A02-far` | Same door from about 4 m, with ducts overhead. Sharp despite the score. | Useful context for the walkthrough, not as an anchor. |
| `CS-A02-angle` | About 30° from the left, plaque legible. | Fine for phone OCR at 1 m. |

## 3. Efficiency

Timestamps are relative to the session start at 16:14:56. The anchor snapshot is taken at the straight-on shot, and an edge's snapshot at its Stop.

| Block | Time | Share | Notes |
|---|---|---|---|
| Setup to first node | 0:54 | 5% | Fine |
| 3 entrance nodes (1, 2, 3) | 0:54 to 2:25 | 9% | **About 45 s each including walking.** Efficient, and this is the model for everything else |
| Walk from node 3 to node 4 with no edge (156 steps) | 2:25 to 7:42 | **31%** | Largest single block, no data from it |
| 2 entrance nodes (4, 5) | 7:42 to 8:31 | 5% | Efficient |
| Anchor A01 (walk, 3 shots, form) | 8:31 to about 11:25 | 17% | **About 2:55** |
| Edge 7 walking | about 11:25 to 12:43 | 8% | Straight line with a turn in it |
| Walk to room 150 with no edge (110 steps) | 12:43 to 14:34 | 11% | Lost edge, room 150's best link |
| Room node plus anchor A02 | 14:34 to about 16:26 | 11% | About 1:52 |
| Edge 10 walking | about 16:26 to 16:56 | 3% | |

About 8 minutes (47%) went to walking with no edge open, about 4:45 (28%) to two anchors, and about 3 minutes to six nodes. At 2.4 minutes per anchor, 10 anchors would take 24 minutes, so the protocol below budgets 1.5 minutes each: widths taped before tapping Anchor, and text typed as the OCR suggestion is confirmed.

### What the 0.2 coaching should have prompted and did not

1. **No START node before the first entrance.** README step 2 says start points come first, but the app let the session begin with entrances.
2. **Walking with no edge open.** After about 20 steps with no open edge, it should prompt "Walk from node 3 as an edge?". It stayed silent for 156 steps.
3. **5 outdoor entrances and 0 indoor nodes after 8 minutes**, with no warning.
4. **No elevator node on this floor** after 15 minutes indoors. There was no "tap Node → elevator at the bank" prompt, and nothing to put the elevator bank ahead of room 150 even though the bank is the hub of every 608 route.
5. **No floor-change nudge.** After 17 minutes on floor 1 with "Elevator rides 0/5" and "Floor 6: 0 nodes", nothing asked "Go to floor 6 next?".
6. **"Library" in an outdoor entrance name** should ask "Is this the Library South connection? It is indoor, on floor 2."
7. **A 200 cm width on text "150"** should have been challenged: 3 or 4 digits means a plaque, which is 10 to 30 cm.
8. **Nearest node 27 m away** (A01) and **offset 0.0** (A02) were accepted silently.
9. **Edge with a 55° spread**: the warning came after Stop. It should have said "Turn detected: drop an intersection node here?" at the moment of the turn.
10. **Demo rooms 1/2** (no 608) and **stopwatch runs 0/2** were not on the checklist, or were not shown on the Export screen.
11. **Stride 0.417 m accepted at calibration** without a "this is short for an adult, re-walk?" prompt.
12. **Export allowed with 1 open warning** and a checklist about 10% complete. It needs an "Export anyway? Missing: …" confirmation.

**Rating:** 8/10 as a field smoke test of the app, 2/10 as survey data. That is fine for a first outing; the fix is protocol and coaching, not more time on site.

## 4. Field protocols

### 4a. Classroom South, 70 minutes, one surveyor (target: today, Friday, 9:30 to 12:30)

**Bring:** phone at 100%, battery pack, **5 m tape**, the printed anchor log (paper fallback), the floor sketch sheet, and a second timer (watch stopwatch). **Before leaving:** delete nothing from the 09-24 session. Start a new session; edges only reference nodes in their own session.

**Rules for the whole visit:** every move between two nodes is an Edge (tap Edge before the first step). Drop a node at every turn. Stand still for 2 s before saving any node. Tape the anchor width *before* tapping Anchor. Change the floor selector the moment the elevator doors open.

| Min | Where | Do | Buttons and text |
|---|---|---|---|
| 0 to 3 | Sidewalk, any straight run | **Recalibrate stride**: tape 10 m (two tape lengths, chalk or a coin at each end), walk it at normal pace holding the phone texting-style. Accept 0.60 to 0.85 m; otherwise redo. | Stride → Start → Stop → Save |
| 3 to 5 | Same spot | New session: `CS`, floor 1, name. Choose the **origin**: centre of the doormat at the 95 Decatur St entrance. | Note: `ORIGIN: centre of doormat, 95 Decatur St entrance, floor 1` |
| 5 to 7 | **P1**: sidewalk about 40 to 60 m from the Decatur/Central main entrance, on the side *away* from the "Walters" doors | START node, 10 s GPS, stand still | Node → start → `P1 Decatur/Central sidewalk` |
| 7 to 13 | Loop the building outside, clockwise | At each **Classroom South** street door: ENTRANCE node on the threshold facing out, 10 s. Then a Note with a photo of any posted hours and any card reader. Re-take node 1 steadily. Skip the library's street doors unless a route uses them. | Node → entrance → `E-Decatur main` / `E-Walters main` / `E-Walters side`, then Note → photo → `hours: …` |
| 13 to 15 | **P2**: the opposite side, 40 to 60 m from P1 and nearest the "Walters" doors | START node | Node → start → `P2 <street> sidewalk` |
| 15 to 27 | **Floor 1 indoors** | From E-Walters side: Edge → walk → intersection nodes at each turn → **elevator bank** (`Node → elevator → EL-1 bank`). Then Edge EL-1 → turns → **R-150** (reuse the room node, door side). Then Edge from **each other floor-1 entrance** to the nearest existing intersection. Anchors (4): the lobby directory board, a sign at the elevator lobby, one mid-corridor sign or poster on the 150 route, and the **"150" plaque as a text anchor** (width about 18 cm, text `150`) plus `AUDITORIUM` as a second text anchor. | Edge / Node / Anchor. For each anchor: tape, then 3 shots with level ✓, width, exact text, height, nearest node, offset in metres |
| 27 to 29 | Floor 1, 50 m straight corridor or sidewalk | **Walking speed**: time 50 m at normal pace (watch stopwatch). | Note: `walk 50 m = __ s` |
| 29 to 33 | EL-1 → floor 6 | **Elevator 1**: Called (press button), Boarded, Doors opened, enter 6. **Switch the floor selector to 6.** | Elevator |
| 33 to 43 | **Floor 6** | `Node → elevator → EL-6 bank`. Edges with turn nodes → **R-608** (door side). Anchors (3): elevator lobby floor sign, one junction sign, the 608 plaque (text) plus any larger sign or directory near it. | Edge / Node / Anchor |
| 43 to 46 | Floor 6 → 5 → 6 | **Stairs**: nearest stairwell to EL-6. Stairs node on 6, walk down one flight, stairs node on 5, then back up one flight, timing both ways. | Node → stairs → `ST-A-6`; Stairs (bottom/top) ×2 |
| 46 to 49 | EL-6 → floor 2 | **Elevator 2** (6→2). Floor selector to 2. | Elevator |
| 49 to 57 | **Floor 2** | `EL-2 bank` node. Edges → **both floor-2 entrances**, including the **Library South connection** (Node → entrance, *outdoors off*, `E-Library connection`). Note with a photo of the library hours. 2 anchors (one at EL-2, one at the library connection). | Edge / Node / Anchor / Note |
| 57 to 60 | EL-2 → 6 → 1 | **Elevators 3 and 4** (2→6, 6→1). Change the floor selector each time. That gives two 1↔6-scale traces for the barometer delta. | Elevator ×2 |
| 60 to 67 | **Timed runs + walkthrough videos** | **P1 → 608** via the entrance you expect to win: Video on, chest height, narrate turns, **Elevator 5** (1→6) inside the run. Then walk to **P2 → 608** via the *other* entrance, Video on. Each video's length is the stopwatch time. If the two differ by **< 60 s, move P1 or P2 further apart and re-run one** (doc 09's single most important check). | Video (CS-W01, CS-W02); Elevator inside the run |
| 67 to 70 | Anywhere | Review: clear "Fix before leaving", check every anchor width and offset, and check the checklist (5 elevator waits, 2 START, EL on 1/2/6, 150, 608). Export and share to Drive. | Review → Export |

**If time runs out, cut in this order:** the floor-2 anchors, then the second library edge, then the stairs down leg. **Never cut** the P1 and P2 runs, the 5 elevator waits, or R-608.

**Picking anchors:** directory boards, framed departmental signs, large floor-number plates at elevator lobbies, and posters that are unlikely to change. Avoid doors, glass-covered frames, glossy panels, bulletin boards, and identical signs that differ only by a number. Anything under 20 cm wide is a text anchor. Three shots each: straight on and **filling the frame guide**, 2 to 3 m, and a 30° walking angle.

### 4b. Klaus, 20 minutes, today between check-in (2 PM) and the opening ceremony (4 PM)

**Goal:** Demo A route in the Atrium: two start points within 10 m of the expo table area, one destination under 60 m with at most 2 turns, and 4 to 6 anchors mounted **above head height** so the expo crowd does not block them. The expo table positions may not be known yet, so place S1 and S2 in the middle of the Atrium floor (open question C6 in [15-open-questions.md](15-open-questions.md)).

| Min | Do | Buttons |
|---|---|---|
| 0 to 2 | Pick the destination: a classroom off the Atrium, the glass staircase landing, or an exit. Walk it once without the app and count turns (≤2). New session `KL`, Atrium floor. The stride from this morning is remembered. | Session |
| 2 to 4 | **S1** and **S2**: two spots 5 to 10 m apart near where the tables will go. Use intersection or waypoint nodes (indoors, so no START). | Node → intersection → `S1 expo`, `S2 expo` |
| 4 to 9 | Edges S1 → J1, S2 → J1, J1 → (turn node) → DEST, with a node at each turn. Destination as a room node with door side. | Edge / Node |
| 9 to 16 | **4 to 6 anchors above head height** along the route, ≤15 m apart, one visible from each start point: hanging banners, wall-mounted directory or department signs, signs above doors. Width from an AR measuring app if out of reach, marked estimated. `heightCm` estimated from the ceiling or door height. | Anchor ×5 (about 1.4 min each) |
| 16 to 19 | Two walkthrough videos, S1 → DEST and S2 → DEST, narrated. | Video ×2 |
| 19 to 20 | Review, export, share. | Review → Export |

## 5. Anchor scoring with arcoreimg (for the user to run; not downloaded here)

The v1.56.0 release exists ([GitHub release](https://github.com/google-ar/arcore-android-sdk/releases/tag/v1.56.0)). Its assets are the source archives, and `tools/arcoreimg/windows/arcoreimg.exe` is in the repository at that tag ([tree](https://github.com/google-ar/arcore-android-sdk/tree/v1.56.0/tools/arcoreimg/windows)). The zip is the whole SDK repo (tens of MB); check its size before running on event Wi-Fi.

```powershell
# Windows PowerShell 5.1
$ProgressPreference = 'SilentlyContinue'   # otherwise Invoke-WebRequest is very slow
$dir = "$env:USERPROFILE\tools\arcore"
New-Item -ItemType Directory -Force $dir | Out-Null
$zip = "$dir\arcore-android-sdk-1.56.0.zip"
Invoke-WebRequest -Uri "https://github.com/google-ar/arcore-android-sdk/archive/refs/tags/v1.56.0.zip" -OutFile $zip
Expand-Archive -Path $zip -DestinationPath $dir -Force
$exe = "$dir\arcore-android-sdk-1.56.0\tools\arcoreimg\windows\arcoreimg.exe"
(Get-Item $exe).Length   # should be MBs. If it is ~130 bytes it is a Git LFS pointer; then instead:
# Invoke-WebRequest -Uri "https://github.com/google-ar/arcore-android-sdk/raw/v1.56.0/tools/arcoreimg/windows/arcoreimg.exe" -OutFile "$dir\arcoreimg.exe"; $exe = "$dir\arcoreimg.exe"

# Score every straight-on shot (0-100; keep only >= 75)
$survey = "C:\Users\rapha\AppData\Local\Temp\claude\C--Users-rapha-CampusMaps\64c5d672-f56f-45e1-9532-0c7ace6e3938\scratchpad\survey\CS-20260924-1614"
Get-ChildItem "$survey\*-straight.jpg" | ForEach-Object {
    $score = & $exe eval-img --input_image_path="$($_.FullName)"
    "{0}`t{1}" -f $_.Name, $score
}
```

**Crop before scoring.** The reference image should be the sign, not the façade. The glass and the people reflected in it will not be there next time. A01's sign occupies about x 836 to 2281, y 1153 to 1418 of the 3060×4080 straight shot:

```powershell
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Bitmap]::FromFile("$survey\CS-A01-straight.jpg")
$crop = $src.Clone([System.Drawing.Rectangle]::new(836, 1153, 1445, 265), $src.PixelFormat)
$crop.Save("$survey\CS-A01-crop.jpg", [System.Drawing.Imaging.ImageFormat]::Jpeg); $src.Dispose(); $crop.Dispose()
& $exe eval-img --input_image_path="$survey\CS-A01-crop.jpg"
```

**Threshold: 75** (validation rule 7 in [02-building-data.md](02-building-data.md)). **If the building sign scores under 75:** (1) try a crop that keeps the logo with a little façade margin, since more corners help. (2) Do not spend time on it anyway: it is outdoors and Demo B's outdoor leg is Geospatial. It is not needed for indoor localization. (3) Replace it with the **lobby directory board** just inside the door as the entrance's first indoor image anchor. (4) As a last resort, use a printed high-texture poster from the HIVE (Saturday 3 to 9 PM), placed only with permission and disclosed on Devpost. Don't score A02: the door is not a candidate, and the plaque is a text anchor.

## 6. Survey suggestions (only those that change tomorrow's data)


| Rank | Change | Fixes finding | Effort |
|---|---|---|---|
| 1 | **Stride gate**: reject calibration outside 0.55 to 0.95 m unless confirmed ("That's short for an adult. Was it a taped 10 m?"). Show "implied stride from GPS" on outdoor edges. | 1 | 15 min |
| 2 | **Auto-open an edge on leaving a node**: once more than 15 steps are counted with no edge open, start an edge from the last node retroactively, and ask for To at the next node. | 10 | 45 min |
| 3 | **Demo B checklist that blocks Export** ("Export anyway?"): START ×2, EL node on floors 1/2/6, elevator waits 5/5, ride 1→6, stairs 1, rooms 150 and 608, ≥2 anchors per floor, videos ≥2, the 50 m walk note. | 6, 17 | 45 min |
| 4 | **Next-up rules**: no START yet → "Drop P1 first". 8+ min with only outdoor nodes → "Go inside; add the elevator bank". 10+ min on one floor with no EL node → "Node → elevator on this floor". EL node present and the other demo floors empty → "Ride to floor 6". | 3, 17 | 30 min |
| 5 | **Anchor width sanity**: text of 1 to 4 digits → default 18 cm and kind text. Width over 100 cm needs confirmation. A "taped / estimated" toggle is stored. | 11, 15 | 20 min |
| 6 | **`offsetFromNodeM` required** (no 0 default), plus a warning when the anchor's GPS is more than 10 m from an outdoor nearest node. | 13, 14 | 15 min |
| 7 | **Live turn detection**: if the heading holds more than 40° off the edge mean for 3 s, buzz "Turn: drop a node here?" and split the edge. | 7 | 1 h |
| 8 | **Floor-change nudge**: filtered pressure more than 0.3 hPa from the floor's baseline for 10 s with the floor unchanged → "Did you change floors?". Set the floor automatically from the Elevator arrival floor. | 17 | 30 min |
| 9 | **Blur and OCR on the frame-guide region only**, not the whole frame, and show the sign's pixel size against 300×300. | 16, A02 crop | 45 min |
| 10 | Entrance name containing "library" or "connection" → ask "Indoor? Which floor?". | 3 | 5 min |

---

Sources: [GSU Classroom South calendar page](https://calendar.gsu.edu/classroom_south), [New South Construction, GSU Classroom South](https://newsouthconstruction.com/project/gsu-classroom-south/), [GSU building access](https://safety.gsu.edu/safety-and-you/building-access/), [campus-maps.com Classroom South listing](https://www.campus-maps.com/gsu/classroom-south-clso/) (hours unverified), [Marathon Handbook, average stride length](https://marathonhandbook.com/average-stride-length/), [TimeToWalk, stride length and height](https://timetowalk.net/guides/science-of-stride-length/), [ARCore Android SDK v1.56.0](https://github.com/google-ar/arcore-android-sdk/releases/tag/v1.56.0).
