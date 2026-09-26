# w7 Student Center East from the survey (2026-09-26)

Input: the survey export `CSE-20260926-1530.zip` (read-only, not copied in). Converted with `ConvertMain --keep CSE.json`, then finished by hand.
The converter found 3 nodes, 2 edges, 2 anchors and kept nothing from the placeholder (every old node, and the demo room R-220, was missing).

## The layout in plain words

From the Passio Go stop, walk about 7 m south-east to the STUDENT CENTER EAST doors: a glass vestibule with two sets of double doors.
Inside is the main lobby (elevators on the left, the information desk ahead, a SEPTEMBER MOVIES poster tower). Turn right and walk
about 11 m toward the SPEAKER'S AUDITORIUM lettering on the end wall. The auditorium's double doors are in the recess on the right of
that wall (last seconds of the video). Floor 1 only.

| Id | Name | Measured |
|---|---|---|
| `E-MAIN` | Main entrance (the export's "Door 1") | GPS 3 m, faces out 307.8 deg |
| `H1` | the main lobby | 7 steps = 5.38 m from the door at 150 deg |
| `R-AUD` | Speaker Auditorium | 14 steps = 10.76 m from the lobby at 230 deg, doors on the right (`doorFacing` southeast) |
| `P1` | Passio Go stop (start point) | GPS 3 m; 9 steps = 6.9 m to the door |

The warning "walk #4 wandered 42 deg" is the owner turning round after the facing-out reading and starting the right turn at the lobby.
The video's compass shows only one turn, at the lobby (about 150 to 230 deg), so the lobby is the turn node and no node was added.

Removed: the West entrance, elevators, stairs, floor 2, Room 220 and the five placeholder text anchors. None was surveyed.

## Access (owner facts)

One entrance. Public Mon to Thu 08:00 to 22:00, Fri 08:00 to 20:00 (the weekday hours the placeholder cited from engagement.gsu.edu).
PantherCard-only at every other time, all weekend included. Not flagged estimated. Because there is no second door:

- Without the card after hours there is **no route**, and "Route me around" has no public door to use.
- With the card the route goes in the Main entrance with the PantherCard tag, the S2 banner and the watch LOCKED face (`cardNeeded` is
  set by the router from the door's windows; nothing extra is needed in the data).

## Demo route (from P1, Saturday 21:00)

With the PantherCard (19.6 m, under a minute, tag "PantherCard", S1b card "Main entrance needs a PantherCard right now"):
1. Walk to Main entrance (3.5 m). Near the door: "Tap your PantherCard at the Main entrance".
2. Turn right at the main lobby, toward the Speaker's Auditorium sign (8.9 m).
3. Speaker Auditorium is on your right (19.6 m).

Without the card: "No route to Speaker Auditorium: every entrance is card-only at Sat 21:00."
Tuesday 14:00: the same route, public, no card. Friday 21:00 with the card: the S1b card says "after 8 pm".

## Anchors (arcoreimg 1.56.0, threshold 75)

| Photo | Best score | Result |
|---|---|---|
| CSE-A01 SPEAKER'S AUDITORIUM lettering (straight / far / angle) | 0 / 0 / 20, crops fail or 0 | text anchor (OCR), straight shot kept as a hint picture |
| CSE-A02 SEPTEMBER MOVIES poster tower (straight / far / angle) | whole equalised 80 / 65 / 60 | straight, tower plus margin, greyscale equalised 860x1600 = **80**, in `anchors.imgdb` |

`anchors.imgdb` rebuilt with KL-A01 4.80, CS-A09 1.20, CSE-A02 1.02 (13.5 KB, all three names found in the file). Full table in
`app/src/main/assets/anchors/SCORES.md`.

## Tests

`:core:test :app:testDebugUnitTest` green. Validator on CSE: 0 errors, 0 warnings, 0 info. Updated pins, each with its reason in a comment:
RouterTest (Sat 21:00 no route without the card, Main with it, 23:30 is card-only not closed, Friday "after 8 pm"), CoreRouterTest
(Demo C, closed message on a hand-closed copy), CardAccessTest, CoreBridgeTest (R-AUD, default start P1), BuildingDataTest,
ValidatorReportTest, KlausRefreshTest, AppFlowTest (card holder via Main; no route without the card). AppFlowTest compiles but was
not run (no device).

## Still guessed

- Anchor widths (5 m lettering, 50 cm poster, not taped) and CSE-A02's height (recorded 50 cm; set 1.0 m, the tower's centre).
- CSE-A01's position: at the room node; the wall is a few metres further.
- Door heading: 307.8 as recorded (spread 57); the walk in says 303.
- The outdoor leg: GPS gives 3.5 m, the walk 6.9 m.

## Gaps outside my files

- The S1 hint says "bring your PantherCard or we route you to the public door"; CSE has no public door after hours (`route/CardAccess.kt`).
- The S1b "I have my card" button only shows over route options, so on the no-route screen the only way in is Settings.
- The expo is Sunday 09:30: CSE is card-only then. Turn the PantherCard setting on for Demo C.

## For the owner on site

1. Tap the poster tower as the phone's image target from the lobby entrance; check it is still there on Sunday (films end Sept 30).
2. Tape the poster tower's width and the lettering's width.
3. Check the posted weekday hours on the door (the public windows come from the website).
4. Walk the door in once with the app at sim time Sat 21:00 and the card on: banner, voice line and watch face at the door.
