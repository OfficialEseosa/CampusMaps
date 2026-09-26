# 01. The Three Hero Demos

Each demo has exactly one job. This document is the acceptance test for the whole project: if all three pass, we are done; if one fails, its fallback runs.

---

## Demo A: Live in Klaus ("try it yourself")

**Job:** prove live localization and world-locked AR to a judge who is holding the phone.

### Where
Judging is an **expo in the Klaus Atrium, Sunday 9:30 to 11:00 AM**. The Klaus route therefore starts at our expo table in the Atrium and runs to a nearby destination (a classroom off the Atrium, the glass staircase landing, or an exit). Both rehearsed starting points are within 10 m of the table so a judge never leaves earshot.

### Script (60 seconds)
1. Judge is standing at or near our expo table (one of two rehearsed starting points).
2. Judge opens the app, building is preselected (Klaus), taps a destination from a short list (for example the judging room, a named lab, or "nearest exit").
3. App shows "Point the camera at a sign to get started" with a small hint showing what the nearest anchor looks like.
4. Judge points at the sign. Within 5 seconds the debug chip flips to "Located: Node H3" and floor arrows appear leading down the hallway.
5. Judge walks the route (under 60 m, at most 2 turns). Arrows update as they walk; passing a second anchor re-snaps position silently.
6. Destination marker floats at the door. App says "You have arrived" (on phone speaker, quietly).

### Success criteria
- Works from at least 2 different starting points.
- Route under about 60 m and under 3 turns.
- Localizes within 5 s of pointing at an anchor.
- Runs 5 times in a row without a restart before feature freeze.

### What can go wrong and what we do
| Problem | Live response |
|---|---|
| Anchor not recognized | Device Lead points out the "big" anchor (poster or directory board) instead of a plaque |
| Tracking lost mid-route (someone walks in front) | Arrows fade, "Look at a sign to re-locate" prompt, re-snap at the next anchor |
| Crowd at judging time | Rehearse at the same time of day on Saturday; pick the quieter of the two Klaus routes |
| Phone overheats | Second phone with the same build, kept cool and charging |

### Fallback
Pre-recorded video of the same route, captured before feature freeze (Hour 26 to 28). If the live demo dies, we show the video and hand the judge the phone in "replay" mode (see [03-localization.md](03-localization.md), replay tool).

### Status
Flexible. If the glasses path turns out more reliable than phone AR in Klaus, Demo A may become a live glasses demo. Decide by Hour 20.

---

## Demo B: Classroom South, multiple entrances (recorded video)

**Job:** prove entrance-level intelligence, multi-floor routing, and real choice with ETAs.

### Destinations and entrances
Two rooms, decided 2026-09-20: **Classroom South 150** (floor 1, a short route that shows entrance choice alone) and **Classroom South 608** (floor 6, the multi-floor route with the elevator bank). Confirm floor numbers on the site visit this week.

Classroom South has **about five entrances: three on floor 1, two on floor 2**, and at least one of the floor-2 entrances connects from Library South, which is a joined building. That makes the entrance story better than the plan assumed:
- For 608, a floor-2 entrance saves one floor of elevator ride; a floor-1 entrance may be closer to the sidewalk. The ETA decides, and it is not obvious, which is the point.
- For 150, floor-1 entrances win and floor-2 ones lose, so the same building recommends different floors' doors for different rooms.
- The library connection is an indoor entrance: no outdoor Geospatial anchor, and its opening hours follow the library, which is a second real access rule for the building file. It is a good **P3 start point** ("I'm in the library, take me to 608") if time allows.

### Script (30 seconds in the pitch, about 3 minutes of raw footage)
1. **Take 1, start point P1** (one side of the building, outdoors). User searches for room 608. Route options screen shows 2 or 3 cards, for example:
   - "North entrance, elevator, 4 min"
   - "South entrance, stairs, 6 min"
   - "South entrance, elevator, 5 min"
2. User taps the top option. Outdoor AR arrows (ARCore Geospatial) lead from the sidewalk to that entrance; we only map the inside, the outdoor leg is a single Geospatial anchor per entrance. Inside, anchor localization takes over. Arrows lead to the elevator, the app says "take the elevator to floor 6", **the barometer shows the floor counting up during the ride** (a big floor number on screen and on the watch), then arrows continue to the room.
3. **Take 2, start point P2** (a different side). Same room 608. The recommended entrance is different and the ETA shows why.
4. **Take 3, room 150** from either start. Short route, no floor change, shows that the entrance choice is about the room, not the building.
5. **Take 4, accessibility toggle on** for room 608. The stairs options disappear; only elevator routes remain.

### Success criteria
- Two starting points produce two different recommended entrances.
- The 608 route crosses floors and the barometer visibly counts floors during the elevator ride.
- The accessibility toggle visibly changes the recommendation.
- ETAs differ by at least a minute between the top two options, using measured numbers, not guesses.

### Recording plan
- Saturday evening trip to GSU, same trip as Demo C.
- Two phones: the demo phone with screen recording on, and a second phone filming the person over the shoulder.
- Record each take twice. Keep the raw files; edit on Sunday.
- Demo B does not depend on the time of day; do it first, while there is daylight for the outdoor leg.

### Pitch line
"The closest door isn't always the fastest way to your seat."

### Load warning
Demo B carries the outdoor leg, the entrance comparison, route options, a floor change, and the accessibility toggle. Both the outdoor leg and the barometer are **in the primary plan** (decided 2026-09-20). If Hour 18 to 20 runs long, the emergency cut is still the outdoor leg first (start the recording at the doors; the options screen still shows the entrance choice), then the barometer display (the route still crosses floors; we just do not show the sensor).

---

## Demo C: Hands-free with glasses at the Student Center, after hours (recorded video)

**Job:** prove hands-free accessibility and time-aware access rules.

### Script (30 seconds in the pitch, about 2 minutes raw)
1. Evening. User walks toward Student Center East wearing the Ray-Ban Meta Gen 2s, phone in pocket, watch on wrist.
2. Glasses speak: "Heads up. The entrance ahead is card-access only after 7 pm. Redirecting you to the west entrance, one minute away." Watch buzzes the "locked entrance" pattern and shows the redirect arrow.
3. User walks in through the open entrance. Inside, the glasses camera reads a sign or plaque. Glasses speak "Turn left at the elevators." Watch shows a left arrow.
4. Two more spoken turns, each triggered by something the glasses camera actually saw (debug overlay on the phone screen recording shows "Seen: 'ROOM 220'").
5. "You have arrived." Watch buzzes the arrival pattern.

### Success criteria
- One spoken after-hours notice with a redirect.
- At least 2 to 3 spoken turns triggered by real camera recognition, in one continuous walk.
- Watch mirrors each instruction (only if the watch checkpoint in [08-watch-companion.md](08-watch-companion.md) was met).

### Recording plan
- Same Saturday evening trip as Demo B. Student Center East's posted Saturday hours end at 8 PM, so the indoor walk is recorded **before 8 PM** and the after-hours notice is triggered with the simulated-time toggle (not by changing the phone clock; see [15-open-questions.md](15-open-questions.md)). If the site visit this week shows one entrance is genuinely card-only earlier in the evening, record it for real.
- Film the person from behind with the second phone; also screen-record the demo phone (debug overlay on) so the recognition events are visible in the edit.
- Record the glasses' own POV video as well if the toolkit allows capturing it.

### The constraint that shapes this demo
Research ([research/meta-glasses.md](research/meta-glasses.md)) found that on Android the Meta toolkit **blocks audio playback while the glasses camera is streaming**, and the stream is too low-bitrate to read small text. So the glasses do not stream continuously: they capture a burst of stills, the camera stops, the phone recognizes and speaks, then the camera restarts. The spoken turns still come from what the glasses saw; there is just a short look-then-speak rhythm. Design in [06-glasses-bridge.md](06-glasses-bridge.md).

### Safety nets
- **Simulated-time toggle** in the debug overlay lets the after-hours notice fire at any time of day.
- **Fallback:** if the glasses toolkit fights us or the burst cycle is too slow, use the phone camera (shirt pocket or chest mount) with the glasses as a plain Bluetooth headset. Same story, less magic. Decide by Hour 18, with the fallback already working.
- **Building hours:** Student Center East's posted Saturday hours end at 8 pm, when it may be fully closed rather than card-only. Confirm the real per-entrance rule this week and plan the trip so the redirect is real and the indoor route is still walkable (see [15-open-questions.md](15-open-questions.md)).

---

## Demo dependencies (what must exist for each)

| Capability | A | B | C |
|---|---|---|---|
| Building data file | Klaus | Classroom South | Student Center East |
| ARCore Augmented Images localization | yes | yes | no |
| ML Kit text recognition (glasses stream) | no | no | yes |
| ARCore Geospatial (outdoor) | no | optional | no |
| Barometer floor change | no | yes | no |
| Routing with alternatives and ETAs | basic | yes | basic |
| Access rules and simulated time | no | no | yes |
| Accessibility toggle | no | yes | no |
| AR arrows on phone | yes | yes | no |
| Text-to-speech through glasses | optional | no | yes |
| Watch companion | optional | optional | optional |

Read down a column to see the minimum build for that demo. Demo A is the smallest and the only live one, so it gets built first and polished last.
