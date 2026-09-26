# W6 automatic placement and bigger arrows (branch w6/autoplace)

## What it does, in plain words
Before this, the arrows only appeared after you tapped "Place route here" on the floor while standing at the start and
facing along the route. Now the app places the route by itself:

1. When the AR camera is tracking, the app reads the phone's compass for about 1 second. It does not average the raw
   compass: it averages the difference between the compass and ARCore's own direction, so turning the phone during that
   second does not matter. Magnetic declination at the building (from its origin lat / lng) is added, so it is true north.
2. As soon as ARCore sees a floor plane (or after 3 s of tracking with no plane), the app puts the start node on the
   floor under the camera. Floor height = the highest tracked floor plane 0.5 to 2.2 m below the camera, else the camera
   height minus 1.4 m.
3. It turns the building so that its compass directions match the phone's. The building file says which compass bearing
   the building's +y points at (`origin.headingDeg`; Klaus is 0 = north). Building bearing = compass bearing - headingDeg.
4. It uses the same transform builder as the floor tap (`BuildingToWorld.fromCorrespondence`) and the same world anchor
   drift handling, so arrows, the camera position provider, sign fixes and the barometer all work unchanged (the
   barometer re-zeroes on the new transform as it does for a tap).
5. A 2 s hint says "Route placed from your compass. Tap the floor to re-place if it looks off." While the placement is the
   automatic one, one tap on the floor re-places (no need to press the button first). "Re-place route" still works.
6. Place once: at most once per route, never over an existing transform, never after a floor tap or a sign fix (those win
   for the rest of the AR session), never on the outdoor leg.
7. No compass sensor: nothing changes, the user taps as before.

Logcat, tag `ArGuidanceView`:
`auto-placed at S1, compass 212 deg, building yaw 212 deg; floor plane 1.38 m below camera, after 1450 ms of tracking`
(also `compass <sensor name>, declination -5.x deg` at start, and `compass accuracy N` when it changes).

### Outdoor start (entrance snap)
While the outdoor leg runs, auto-placement is off (Geospatial and the VPS snap own it). When the outdoor leg ends (the VPS
entrance snap calls `jumpTo(entrance)`, so the current node is the door), the route is placed at the entrance node with
the door's walk-in heading instead of the compass. If the compass disagrees with the door by more than 60 degrees (the
student turned round), the compass is used. Works without a compass too. This is implemented, but not tested outdoors.

## Arrow sizes (constants at the top of ui/ar/ArGuidanceView.kt)
| Constant | Value | Was |
|---|---|---|
| `ARROW_WIDTH_M` | 0.9 m | 0.3 |
| `ARROW_LENGTH_M` | 0.6 m | 0.5 |
| `ARROW_ARM_M` (arm thickness) | 0.28 m | 0.2 |
| `RouteArrows.SPACING_M` | 2.5 m | 1.5 |
| `ARROW_LIFT_M` | 0.02 m (flat on the floor) | same |
| `TURN_ARROW_WIDTH_M` (amber) | 1.6 m | 0.66 |
| `TURN_CLEAR_RADIUS_M` | 1.6 m | 1.2 |
| `DIM_BEYOND_M` / `HIDE_BEYOND_M` | 12 / 20 m | 10 / 15 |
Cyan and the destination post and label are unchanged.

Auto-place constants (loc/AutoPlace.kt): `FALLBACK_CAMERA_HEIGHT_M` 1.4, `NO_PLANE_WAIT_MS` 3000, `COMPASS_WINDOW_MS`
1000, `FLOOR_MIN_BELOW_M` 0.5, `FLOOR_MAX_BELOW_M` 2.2, `ENTRANCE_TRUST_DEG` 60. Compass steadiness threshold 0.8
(`CompassOffset.minSteadiness`): if the compass jumps around (a magnet, a steel pillar), placement waits.

## Files
- `app/src/main/java/com/campusmaps/loc/AutoPlace.kt` pure math: bearings, floor height, entrance heading, transform,
  `CompassOffset` (1 s circular average), `AutoPlaceGate` (place once).
- `app/src/main/java/com/campusmaps/loc/CompassHeading.kt` rotation vector (or geomagnetic rotation vector) + declination.
- `app/src/main/java/com/campusmaps/loc/ArFeed.kt` now also holds the building origin (lat, lng, headingDeg).
- `app/src/main/java/com/campusmaps/ui/ar/ArGuidanceView.kt` `autoPlace(...)`, the arrow constants, tap to re-place.
- `app/src/main/java/com/campusmaps/ui/ar/RouteArrows.kt` `SPACING_M` 2.5, `Placement.walkInDeg`.
- `app/src/main/java/com/campusmaps/ui/ar/ArInputsAdapter.kt` fills `walkInDeg` from the node (outdoor entrances only).
- Tests: `app/src/test/java/com/campusmaps/loc/AutoPlaceTest.kt` (15: yaw conversion with origin heading and
  wraparound, compass averaging across north and while turning, entrance heading, floor fallback, place-once rule, outdoor
  leg), `RouteArrowsTest` (+1 for the 2.5 m spacing).

## Verification
`./gradlew :app:assembleDebug :app:testDebugUnitTest`: builds; 175 tests, 174 pass. The one failure is
`CoreBridgeTest.demoDestinationsSurvive`, already failing on main since `7f6dd45` (COEUS room 3361 added to Klaus demo
destinations; the test still expects only R-1116W). Not touched here. No device run (phone with the owner, emulators
have no ARCore).

## Owner test steps (Klaus)
1. Stand at Start 1 (atrium by the tables), face the staircase, pick a destination, start Phone AR.
2. Hold the phone up, camera a little toward the floor. Do not tap anything.
3. Expect: within about 3 s the cyan arrows appear on the floor and the hint "Route placed from your compass..." shows
   for 2 s. Logcat `adb logcat -s ArGuidanceView` shows `auto-placed at S1, compass ... deg, building yaw ... deg`.
4. Check: arrows start under you and run toward the staircase / along the hallway. Arrows are about 0.9 m wide, 2.5 m apart.
5. If they point wrong: tap the floor just in front of you while facing along the route (one tap re-places). Note the
   compass and building yaw numbers from the log and how many degrees off it looked.
6. Walk the route. Arrows should stay on the floor; a sign fix still re-snaps.
7. If the arrows are off by the same angle every time, the building's `origin.headingDeg` in KL.json (now 0, "y north")
   is not true north for the drawn plan; set it to the bearing the plan's +y really points at.
8. If nothing appears after 5 s: send the Logcat lines with tag ArGuidanceView (compass accuracy, "no compass").

## Not covered
- Not run on a device. Compass accuracy indoors near steel and electronics is unknown; the steadiness check makes it wait
  rather than place badly, but a steady wrong field (a nearby magnet) still gives a wrong yaw: tap to fix.
- Assumes the user stands on the start node. A few metres off shifts the whole route by that much.
- Assumes portrait (the app is locked to portrait).
- Entrance snap placement is implemented but not tested outdoors; it relies on the current node being the door after
  `jumpTo`.
- After a floor tap or a sign fix, auto-placement is off for the rest of that AR session (by design).
