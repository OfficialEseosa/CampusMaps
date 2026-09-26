# W4 barometer floor (branch w4/baro)

## What it does, in plain words
Air pressure drops about 0.12 hPa for every metre you go up. Absolute pressure is useless (weather, phone offset), so the app
only measures change from a known floor:

1. **Calibrate at the start.** When guidance starts, the reference pressure is the median of the first 2 s of readings and
   the reference floor is the start node's floor.
2. **Re-zero on known floors.** A sign fix or a "Place route here" floor tap (a new building-to-world transform), and every
   hallway step completed on foot outside a ride, re-zero the reference on the current floor. Any other floor change from
   the position source (debug jump, the simulator's timed ride, ARCore height) also re-zeroes there.
3. **Estimate.** `floor = refFloor + round((refPressure - p) / (0.12 * floorHeightM))`, with `p` low-passed (about 1 s).
   The floor changes only when the rounded estimate has held for 1.5 s and is more than 0.6 of a floor away.
4. **Only during rides.** Changes are acted on while the current step is ELEVATOR or STAIRS and for 20 s after. On a
   hallway step, the same change is logged as "drift ignored" and nothing happens.
5. **Driving the position.** When the barometer changes floor during a ride:
   - Camera driving: the camera's floor is replaced by the barometer's (badge, minimap, step completion follow it).
   - Simulated student: the student stands in the car (`holdRides`) instead of riding on a timer, and is moved floor by
     floor; on the arrival floor it is placed on the ride's node there and walks on. If the pressure has not moved
     0.15 hPa within 10 s of the student standing in the car, the timer rides instead (emulator without pressure changes,
     a stuck sensor).
   - No barometer: nothing changes, the simulator's timed ride stays.

Logcat tag `Baro`. Debug card line (under the existing "barometer:" line):
`baro: 1011.63 hPa, ref floor 2 @ 1012.88, est floor 4, delta -1.10 (ride)`.

## Files
- `app/src/main/java/com/campusmaps/loc/baro/BarometerFloorEstimator.kt` pure logic (calibrate, re-zero, filter, hysteresis, ride gate).
- `loc/baro/PressureSource.kt` interface + `SensorPressureSource` (TYPE_PRESSURE, SENSOR_DELAY_UI).
- `loc/baro/BarometerFloorTracker.kt` one session: source to estimator, publishes the debug status.
- `loc/baro/BaroFeed.kt` app-wide holder: installed with the app context, hands a tracker to each session, debug status flow.
- `loc/SwitchablePositionProvider.kt` `setBarometerFloor` / `barometerFloorKnown` (camera floor override, simulator move).
- `guidance/GuidanceController.kt` start/stop, ride gate from the current step, hold, re-zeros, floor to route point.
- `guidance/SimulatedPositionProvider.kt` small additive hook: `holdRides` and `riding` (not in my list, but not owned
  by another agent; 9 lines).
- `ui/CampusMapsApp.kt` installs the barometer (`BaroFeed.install(app.appContext)`) and adds the debug line.
- Tests: `app/src/test/java/com/campusmaps/loc/baro/BarometerFloorEstimatorTest.kt` (7) and `BarometerFloorTrackerTest.kt` (2).

## Constants and how to tune them (BarometerFloorEstimator constructor, GuidanceController)
| Constant | Value | Tune when |
|---|---|---|
| `HPA_PER_METRE` | 0.12 | Surveys measured 0.113 (CS) and 0.112 (KL). With floorHeightM from those surveys 0.12 slightly over-counts per floor (0.47 vs 0.44 hPa on CS); the 0.6 threshold absorbs it for rides up to ~5 floors. If a real 5-floor ride ends one floor short, set 0.113. |
| `filterTauMs` | 1000 | Longer = smoother but slower to count floors. |
| `stableMs` | 1500 | Real elevators do about 2 to 3 s per floor; if floors are skipped in the count, lower to 1000. |
| `minFloorFraction` | 0.6 | Raise to 0.7 if a floor flickers at the door. |
| `calibrationMs` | 2000 | Median window at route start. |
| `afterRideMs` | 20 000 | Window after a ride when late changes are still accepted. |
| `HOLD_GIVE_UP_MS` (controller) | 10 000 | How long the simulated student waits in a still car before the timer rides. |

## Verified
- JVM: 7 estimator tests (calibration median; 4-floor ride with 0.05 hPa noise over 20 seeds ends on 6, counts 3-4-5-6 with
  no step back; ride down; 0.3 hPa drift over 10 min on a hallway step changes nothing; drift after the 20 s window;
  sign-fix re-zero; re-zero on another floor) and 2 tracker tests. Full `:app:testDebugUnitTest` passes.
- Emulator-5554 (API 36, virtual pressure sensor, no ARCore so the simulator drives), CS, start "Classroom South main
  (floor 2)" to Room 608, Phone AR:
  - Calibrated floor 2 @ 1012.88 hPa. Student reached the car and waited. Lowered pressure 10 x 0.187 hPa over 10 s:
    log `floor 2 -> 3`, `3 -> 4`, `4 -> 5`, `5 -> 6 (route point 5 EL-6)`, `ride over`. Badge F2 to F4 to F6, then the
    route continued (step 7/8 "Floor 6 corridor bend") and arrived. Shots 1 to 3.
  - Reverse: start "Floor 6 corridor bend" to Room 150, raised pressure 10 x 0.234 hPa: `6 -> 5 -> 4 -> 3 -> 2 (EL-2) -> 1
    (EL-1)`, badge F1, route continued. Shots 4 and 5.
- Shots in `reports/shots-w4-baro/` (360 px wide).
- Not verified on the emulator: the 10 s give-up (still pressure) path and the camera (AR) override path (no ARCore on the AVD).

## Found in passing (not mine)
- Explore start (outside) on the emulator: the outdoor "Head southeast" step stays current for the whole route because the
  emulator's GPS fix is fresh but far away, while the simulated student walks the whole building. The ride step never
  becomes current, so the barometer gate never opens in that flow. Owner of `guidance/OutdoorGps.kt` / outdoor should look.

## What the owner must check in a real elevator ride (S25 Ultra has a barometer)
1. Open debug, start a CS or KL route with an elevator, and watch the `baro:` line: at rest the delta should wander less
   than 0.1 hPa.
2. With the camera driving: in the car the badge should count floors within about 2 s of each floor, and land on the
   right floor before the doors open. `adb logcat -s Baro` gives the full trace; save it with the report.
3. Stand still on a hallway for a few minutes: expect at most "drift ignored" lines, never a floor change.
4. If the count ends one floor short or long, adjust `HPA_PER_METRE` (see table). If it flickers, raise `minFloorFraction`.
5. Stairs: the gate is the same; commits happen once a landing's pressure has held for 1.5 s.

## Wiring I could not do in files I do not own
- `AppContainer` is the natural place for `BaroFeed.install(appContext)` (next to `ArFeed.arExpected`); it is in
  `CampusMapsApp.kt` for now. Moving it is one line.
- The watch and the glasses get the new floor through the existing step flow; no change was needed there.
- `ui/screens/DebugOverlay.kt` still has its own `rememberPressure()` line ("barometer: ..."); it can go once the `baro:`
  line is trusted.
