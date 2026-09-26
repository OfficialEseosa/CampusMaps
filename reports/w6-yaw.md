# W6 heading self-correction, Klaus stairs, Room 1116W (branch w6/yaw)

## What the owner saw
After the compass placement, walking about 20 m gave "Rerouted (6)". Indoors the compass is often 15 to 30 degrees off,
so the camera position slid 5 to 10 m beside the route line, the engine saw more than 6 m off route and rerouted again and
again. At the floor-2 landing the stairs step read "Take the stairs up one floor, Riding to Floor 3" while the Explore
sheet said "up two floors".

## How the refinement works, in plain words
The user walks along the route. So once they have walked 3 m in a fairly straight line, the direction they walked is
the direction the route goes there. The app compares the two and turns the whole building by the difference, around the
placement point, so the start node stays exactly where the user started and everything further along swings onto the
line they are walking.

- "Straight": the ends of the walk are at least 3 m apart and every camera position in between is within 1 m of the line
  joining them. A path with a corner does not count (the window slides forward until it is straight).
- "Where the route goes": the same distance along the route, from the point nearest where the window started. If the
  route itself turns inside that stretch, nothing is compared (no guess at corners).
- First time: the whole difference, capped at 45 degrees. After that: 10 percent of each new difference per straight
  3 m, and differences over 45 degrees are ignored (a detour round a pillar cannot swing the route). The total correction
  never goes past 45 degrees from the compass.
- Only after a compass (or door-heading) placement. A floor tap or a sign fix stops it for the rest of the AR session;
  they are better than walking.
- Logcat, tag `ArGuidanceView`:
  `yaw refined by -18 deg after 3.1 m (first, measured -18 deg, total -18 deg)`, later
  `yaw refined by 1 deg after 15.4 m (slow filter, measured 6 deg, total -17 deg)`.

Constants (`loc/YawRefiner.kt`): `MIN_TRAVEL_M` 3.0, `MAX_CHORD_DEV_M` 1.0, `MAX_CORRECTION_DEG` 45, `SLOW_GAIN` 0.1,
`SAMPLE_STEP_M` 0.25 (camera positions closer than this are skipped).

## No reroute storm
- Until the first refinement, the reroute rule uses 12 m instead of 6 m (`GuidanceEngine.OFF_ROUTE_UNREFINED_M`,
  `offRouteLimitM(compassPlaced, refined)`), only while the camera drives the position. After refinement, and for floor
  taps, sign fixes and the simulator, it is 6 m as before. Step progress already uses the projection onto the route.
- Debug card, new line under "reroutes": `yaw: compass, unrefined  reroute past 12 m`, then
  `yaw: compass, refined -18 deg  reroute past 6 m` (or `yaw: floor tap`, `yaw: sign fix`, `yaw: not placed`).
- The barometer used to re-zero to the placement floor every time the transform changed (including ARCore anchor drift).
  Refinements change the transform too, so it now re-zeroes only on real placements (`ArFeed.placements`: compass, door,
  floor tap, sign fix). Otherwise a refinement on floor 3 would have told the barometer it was on floor 1.

## Klaus stairs (KL.json)
- Was: ST-1 (floor 1) to ST-2 (floor 2 landing, placed half way, no floor-2 hallway) to ST-3 (floor 3), two one-floor
  edges. The first route read "up two floors" correctly, but on the landing the camera was far from ST-1 (the engine
  measured a climber against the bottom node only), so it rerouted from the nearest floor-2 node, ST-2, which gave
  "up one floor" with "Riding to Floor 3".
- Now: one stairs edge ST-1 to ST-3, `floors: 2` (2 x 21.6 s = 43 s). ST-2 is removed (it was not reachable except by
  these stairs, and keeping it unconnected breaks the validator's reachability rule). The route reads "Take the stairs up
  two floors" once, fromFloor 1, targetFloor 3.
- Guidance: a climber on a stairs or elevator hop is now measured against the flight seen from above (ST-1 to ST-3 runs
  about 33 m sideways), not against the bottom node. Elevators are unchanged (both ends at the same spot). Mid-flight on
  floor 2 there is no floor-2 node to reroute from, so no bogus "up one floor".

## Room 1116W (KL.json, from the owner at Klaus)
The door is straight ahead walking from the glass staircase (H1) along the route. `doorFacing` west -> south, so core's
arrival reads "Room 1116W is ahead" for the S1 and S2 routes (was "is on your right"). There is no doorSide field.
Tests updated with the reason: `RouterTest.klausRoutesFromBothStartsMatchTheHandCheckedLists` (arrival text) and
`CoreBridgeTest.anchorsBecomeSignTextAndDoorsGiveARoomInside` (room inside is now north of the door).

## Files
- `app/src/main/java/com/campusmaps/loc/YawRefiner.kt` (new): `YawRefiner`, `YawStatus`, `YawSource`, `RoutePath`.
- `app/src/main/java/com/campusmaps/loc/ArFeed.kt`: `yaw` status flow, `placements` counter.
- `app/src/main/java/com/campusmaps/ui/ar/ArGuidanceView.kt`: starts the refiner on auto-placement, stops it on floor tap
  and sign fix, runs it every frame, logs; keeps its own last transform so a refinement is not overwritten by the anchor
  drift check before the host recomposes.
- `app/src/main/java/com/campusmaps/guidance/GuidanceEngine.kt`: `OFF_ROUTE_UNREFINED_M`, `offRouteLimitM`, hop matching.
- `app/src/main/java/com/campusmaps/guidance/GuidanceController.kt`: reroute uses `offRouteLimitM()`; barometer re-zero on
  placements only.
- `app/src/main/java/com/campusmaps/ui/CampusMapsApp.kt`: the one debug line.
- `app/src/main/assets/buildings/KL.json`: stairs edge, ST-2 removed, R-1116W doorFacing.
- Tests: `app/src/test/java/com/campusmaps/loc/YawRefinerTest.kt` (10: first correction, wraparound across south, 45
  degree cap, nothing under 3 m, nothing on an L-shaped path, one-shot then slow filter, tap/sign stop, route turn skipped,
  pivot stays, route distances), `app/src/test/java/com/campusmaps/guidance/KlausStairsTest.kt` (3, real KL file: one
  "up two floors" step 1 -> 3, climber half way up on floor 2 is on the route, 12 m / 6 m tolerance).

## Verification
`./gradlew :core:test :app:assembleDebug :app:testDebugUnitTest`: builds, all tests pass (192 app tests). Not run on a
device (phone with the owner).

## Owner test steps (Klaus)
1. Stand at Start 1 facing the staircase, pick COEUS (or 1116W), start Phone AR. Open the debug card.
2. Wait for "Route placed from your compass". Debug line: `yaw: compass, unrefined  reroute past 12 m`.
3. Walk 5 m straight toward the staircase at a normal pace, phone up.
4. Expect: the debug line changes to `yaw: compass, refined N deg  reroute past 6 m`; the arrows swing onto your line;
   no "Rerouted" chip. Logcat: `adb logcat -s ArGuidanceView` shows `yaw refined by N deg after 3.x m`.
5. To COEUS: at the stairs the step reads "Take the stairs up two floors", and stays that way on the landing.
6. To 1116W: the arrival says "Room 1116W is ahead".
7. If it refines the wrong way (arrows swing away from your walk): send the Logcat lines with "yaw refined" and
   "auto-placed", and how you walked (straight? along the route?).

## Not covered / risks
- If the first straight 3 m is not along the route (the user walks to the side to avoid someone), the first correction is
  wrong by that angle (at most 45 degrees); the slow filter then pulls it back 10 percent per 3 m. A floor tap fixes it at
  once.
- The refinement turns about the start node; if the user did not start on the start node, the position error from that
  stays (as before).
- A reroute before the first refinement keeps the 12 m rule until the user walks 3 m straight along the new route.
