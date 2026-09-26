# Wave 5: GPS default start on S1 (branch w5/gpsstart)

Emulator `emulator-5558` (API 36, fake GPS with `emu geo fix`). Build `./gradlew :app:assembleDebug :app:testDebugUnitTest` is green. Screenshots (360 px) are in `reports/shots-w5-gpsstart/`.

## The rule in plain words

When "Where to?" (S1) is on screen, the app listens to the phone's location. It does not ask for the permission; only Explore asks. With no permission, it gets no fixes and behaves as before.

- **The start is "Your location"** when all of these are true:
  - the fix is less than 30 s old,
  - its accuracy is better than 50 m,
  - demo mode is off,
  - the phone is away from the selected building.

  "Away" means farther than 25 m (or the fix's accuracy, if that is larger) from every outdoor entrance, and outside the box around the building's nodes. The start uses the same "Your location" node as the Explore hand-off (`CoreBridge.withGpsStart`), so core routes it with `Start.Outside(lat, lng)`. The picker shows "Outside: Your location" and can still be changed.
- **At the building, in demo mode, or with no good fix**, the start is the building's default, as today: S1 at Klaus, P1 at Classroom South.
- **A start picked by hand is kept** until the building changes or Reset. The same goes for a start that came from Explore or from the end of a route.
- **An automatic GPS start follows new fixes** while S1 is open. It ignores moves under 5 m, so S1 does not re-plan every second. It goes back to the default if the phone reaches the building or demo mode is turned on.

## What was verified

- **JVM tests** in `ui/GpsStartTest`, 7 tests, all green:
  - a fresh fix away from the building gives the GPS start;
  - at the Library South entrance, the start stays P1;
  - demo mode keeps the default;
  - a fix that is too old, too inaccurate or missing keeps the default;
  - a pick by hand wins;
  - an automatic start follows the fix, and goes back to the default at the building;
  - from a fix 300 m out, route option 1 is the same entrance the Explore map computes (`OutdoorRoutes.plan`).
- **Emulator:**
  - **Fix 300 m north of Classroom South** (33.75558, -84.38769), S1 for CS opened directly: the picker shows "Outside: Your location" (`01`). Logcat: `GpsStart: S1 start: Your location (33.755580, -84.387690, 5 m) for CS`. Room 608, then Route: "From: Outside, Your location", fastest route by Walters main entrance (`02`). The test above checks that this is the map's entrance.
  - **Fix moved to the Library South entrance** with S1 open: the start went back to "Outside: Decatur St side" (P1) (`03`). Logcat: `S1 start: CS default (fix 33.752548, -84.387020, 5 m, demo false)`.
  - **Demo mode on**, same far fix: the start stays at the default, P1 (`04`).

## UI hook for DestinationScreen (the orchestrator adds it)

`TripUiState.gpsHint: String?` is `"From your location (GPS, 8 m)"` while the start is the automatic GPS default, and null otherwise. In `DestinationScreen.kt`, add this right after `StartPicker(state.start, state.startOptions, actions.onStart)` (line 177):

```kotlin
state.gpsHint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) }
```

## Extension: VPS position and entrance snap (S2 outdoor leg)

- **VPS position.** `geo/VpsPosition.kt` turns the Geospatial state into a position only while Earth is TRACKING with horizontal accuracy under 10 m. S2's `OutdoorGeoEffect` publishes it on the outdoor leg. `MainViewModel` then feeds the banner distance (`controller.onFix`) and the 40 m hand-off trigger (`handoff.attach`) through `VpsPosition.preferVps`. While a VPS position is less than 3 s old, FusedLocation fixes are dropped.
- **Entrance snap.** On the outdoor leg, a VPS position within 15 m of any outdoor entrance of the current building ends the outdoor leg at that door. It runs once per route.
  - It logs under tag `Geo`, for example `VPS snap to E-WM, 6.2 m` (plus "(planned another door: reroute)" when it is not the planned door).
  - Then it calls `GuidanceController.jumpTo(entrance)`. For the planned door, that moves along the route to the door. For another door, it reroutes from that door with `CoreRouter.bestRoute`, keeping the same way of changing floors.
  - One difference from the request: the new route starts at that entrance (`Start.AtNode(entrance)`) rather than passing it as `cameFrom`, because `jumpTo` already did that.
- **JVM tests** in `geo/VpsSnapTest`, 5 tests, all green:
  - the fake provider TRACKING 8 m from E-WM while the plan said E-LM2 snaps to E-WM with reroute, and the best route from there starts at E-WM;
  - the planned door snaps without a reroute;
  - no snap at 40 m;
  - only TRACKING under 10 m counts;
  - a fresh VPS position hides FusedLocation.
- **Not tested on a device.** The emulator has no Geospatial. Only a smoke launch was done: no crash.
- **Owner check outdoors:** stand at the Walters door with a route planned for Library South. Logcat `-s Geo` should show the snap, and the S2 steps should start from Walters.

## Files changed

- `app/src/main/java/com/campusmaps/ui/ExploreStart.kt` (new `GpsStart` rule object)
- `app/src/main/java/com/campusmaps/ui/MainViewModel.kt`:
  - `Selection.gpsAutoAccuracyM` and `TripUiState.gpsHint`;
  - the S1 fix collector;
  - `selectStart` and `endGuidance` now count as picks by hand;
  - VPS feed and snap job.
- `app/src/main/java/com/campusmaps/geo/VpsPosition.kt` (new)
- `app/src/main/java/com/campusmaps/ui/screens/GuidanceScreen.kt` (only `OutdoorGeoEffect`: publishes the VPS position)
- `app/src/test/java/com/campusmaps/ui/GpsStartTest.kt`, `app/src/test/java/com/campusmaps/geo/VpsSnapTest.kt` (new)
- `reports/shots-w5-gpsstart/01..04`, this report
