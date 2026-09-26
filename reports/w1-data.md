# W1 data agent report (2026-09-25 late, branch w1/data)

Tests: `:core:test` 52 (51 pass, 1 skipped, the known 60 s gap), `:app:testDebugUnitTest` 44 pass. Both green.

## Entrance geo: one thing to know first

The files already had `lat`, `lng`, `headingDeg` on every outdoor entrance (CS from the survey, KL and CSE estimated). **In the files `headingDeg` means the door faces OUT**, which is what field survey records and what the converter writes. So the file values stay as they are, and the walk-in heading is worked out from them (+180):
- core `Node.walkInHeadingDeg`
- app `GraphNode.lat`, `GraphNode.lng`, `GraphNode.headingDeg` (walk-in; filled only on outdoor entrances, null elsewhere)
- `CoreBridge.entranceGeo(...)`, where `headingDeg` = walk-in and `facingOutDeg` = the file value

Helper for the map and Geospatial agents (`app/.../data/campus/CoreBridge.kt`):
```kotlin
data class EntranceGeo(val nodeId: String, val name: String, val floor: Int, val lat: Double, val lng: Double,
                       val headingDeg: Double?, val facingOutDeg: Double?, val estimated: Boolean)
fun entranceGeo(building: Building, coreOption: com.campusmaps.routing.RouteOption): EntranceGeo?
fun entranceGeo(building: Building, entranceIdOrName: String): EntranceGeo?   // "E-LM2" or the card's entrance name
```
It returns null for inside starts, for non-entrances, and for entrances with no lat/lng. It is tested on real CS: P1 to 608 gives E-LM2 at 33.75255063, -84.38702004, facing out 16.3, walk-in 196.3, floor 2, not estimated.

Validator rule 11 (WARN) is new: an outdoor entrance missing lat, lng or headingDeg. It fires 0 times on all three files.

Klaus is on the Georgia Tech campus in Midtown, not GSU downtown. KL keeps its Georgia Tech guess (origin 33.77721, -84.39585). CS and CSE are GSU downtown.

## What changed in each JSON

- **KL.json:** KL-A01, KL-A02 and KL-A05 now have `imagePending: true`, so their missing photos are rule 7 WARN instead of ERROR. E-S and E-N notes now explain the lat/lng/heading guess. No values changed; everything is still `estimated`.
- **CS.json:** 5 new placeholder text anchors, all `estimated: true`, placed at their node, notes starting "PLACEHOLDER":
  - CS-A92 H10 "EXIT"
  - CS-A93 H4 "ELEVATORS" (wording guessed)
  - CS-A94 H9 "FLOOR 1", CS-A95 H5 "FLOOR 2", CS-A96 H6 "FLOOR 6" (elevator lobby floor plaques)

  The survey log has no sign at any lobby. The P1 (Library South) route to 608 now passes rule 8 (longest stretch 17.0 m).
- **CSE.json:** the notes on both entrances explain the lat/lng/heading guess. No values changed.

## Which values are estimates

All of KL and CSE (entrance lat/lng/heading are map guesses). In CS, the 5 new anchors and everything already flagged. The CS entrance fixes are measured, except Walters side.

## Validator counts (full: images against assets, rule 8 on router demo routes; `ValidatorReportTest`)

| File | Before (E / W / I) | After (E / W / I) |
|---|---|---|
| KL | 3 / 0 / 2 (rule 7 photos) | 0 / 3 / 2 (photos pending) |
| CS | 3 / 0 / 5 (rule 8 x3) | 1 / 0 / 5 |
| CSE | 0 / 0 / 0 | 0 / 0 / 0 |

The one CS error left is on purpose: on the P2 route (E-WM > H7 > H1 > H2) there are 45 m with no sign logged on the 30 m main hallway. I did not invent a node and sign there. The test lists it as the one known error.

## Klaus refresh (full steps in docs/19 "Klaus refresh, step by step")

`ConvertMain` now reads the field survey export zip directly and has `--keep` to carry over:
- the ids S1, S2, T and other old ids, matched by node name
- `demoDestinations`
- `startPoints`
- access windows

PowerShell, from the repo root, after `.\gradlew.bat :core:classes` and building `$cp` as shown in docs/19:
```
java -cp $cp com.campusmaps.survey.ConvertMain KL-<session>.zip draft-KL.json --keep app\src\main\assets\buildings\KL.json
```
I ran it on the real CS zip with `--keep CS.json` and it works. `KlausRefreshTest` is the regression test: it covers the zip, `--keep`, and a CS log relabelled as KL.

What the converter cannot do (a CS log limit, not a missing field): legs that were walked only on video are not converted, so the raw CS draft fails rule 5 until they are added by hand. A room reached only on video (608) does not become a node.

## What the owner must capture at Klaus

- Name the Places **S1**, **S2** (waypoints by the table), **T** (the table), and **Room <number>** for the demo room.
- Every door: Place → Door, facing out, hold 10 s. This gives lat/lng/heading.
- Take straight-on photos with widths for KL-A01 (directory board at the atrium centre), KL-A02 (north atrium wall poster) and KL-A05 (foot of the glass staircase), or replace them with other signs.
- Put a sign at least every 20 m from S1 to the room.

## Files outside the strict ownership list

- `app/src/main/java/com/campusmaps/data/model/Campus.kt`: 3 nullable fields added at the end of `GraphNode`, as the task asked. Existing constructor calls are unaffected.
