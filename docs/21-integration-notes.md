# 21. Integration Notes (teammate base + Raphael's core, 2026-09-25)

Owner decision: the teammate's repository is the **base**. Raphael's work is rewritten on top of it; where the two contradict, Raphael's design wins, rewritten into the teammate's structure. This file records what came from whom, how the two halves are joined, what was removed and why, and what is still open.

Branch: `integration` (worktree `C:\Users\rapha\CampusMaps-integration`), rooted at the teammate's commit a52f23e. Nothing is committed yet; the owner reviews the working tree.

## Who wrote what

**Uyiosa Nehikhuere (OfficialEseosa), the base.** The whole Android app shell and its look: Gradle setup and version catalog, the `app`, `wear` and `shared` modules, the design handoff (`CampusMaps-Design-Handoff (1).md`) and its theme (Sora type, colors, shapes, `ArOverlayColors`), components and icons, every screen (S1 Destination, S1b Route options, S2 AR guidance with the minimap, floor badge, Locate me and the arrived state, S3 Glasses mode, S4 Add a shortcut, Settings, debug card), `MainViewModel`, `GuidanceController` + `GuidanceEngine` (pose-to-route matching, steps, reroute, watch mirroring), `SimulatedPositionProvider`, `SimulatedGlassesLink` (Looking / Recognising / Speaking), `Speaker`, `WatchBridge`, `NetworkMonitor`, `SettingsRepository` (DataStore), the student shortcuts feature (backend seam, repository, photos, notifications), the Wear OS app with haptics, `ArWorldOverlay` / `CameraPreview`, `FloorPlanMap`, `AppFlowTest` and the screenshots.

**Raphael Omorose.** The `core` module (building model, `BuildingLoader`, `BuildingValidator`, `Router` with entrance x vertical-method alternatives, `alsoVia`, locked-entrance notice, `now` as a parameter, avoid stairs, `Start.AtNode(id, cameFrom)` / `Start.Outside(lat, lng)`, instruction types and text, `Access` rules, `Geo`, the survey converter) and its 45 tests; the building data `app/src/main/assets/buildings/{KL,CS,CSE}.json` (CS from the real survey) and anchor photos; the plan in `docs/` (00 to 20, design, research); the debug overlay's simulated-time switch and picker, fake walk, jump picker and low-confidence switch; the docs/20 QA fixes; the world-locked AR layer (`loc/BuildingToWorld.kt`, `ui/ar/RouteArrows.kt`, `ui/ar/ArGuidanceView.kt`); the Meta toolkit notes in `tools/meta-sample/`.

**Integration (this change).** `data/campus/CoreBridge.kt`, `route/CoreRouter.kt`, `ui/ar/ArInputsAdapter.kt`, the debug controls, the shortcut floor rule, and the edits listed below.

## Module layout now

```
core/    Raphael's pure Kotlin/JVM module (unchanged copy): data model, loader, validator, router, survey converter
app/     teammate's phone app, sources in app/src/main/java (not kotlin/)
  assets/buildings/*.json, assets/anchors/**      Raphael's building files (source of truth)
  data/campus/CoreBridge.kt                       core Building -> app Building view
  route/                                          teammate's view models (RoutePlan, RouteOption, Route, RouteStep),
                                                  Formats, RouteCardText, RouteText, CoreRouter (the routing adapter)
  guidance/, ui/, platform/, data/shortcuts/      teammate's, adapted
  loc/BuildingToWorld.kt, ui/ar/RouteArrows.kt, ui/ar/ArGuidanceView.kt   Raphael's AR layer (unchanged copies)
  ui/ar/ArInputsAdapter.kt                        GuidanceState -> ArRouteInput
shared/  teammate's watch step format and haptics
wear/    teammate's Wear OS app
```

The teammate's `com.campusmaps.routing` package was renamed to `com.campusmaps.route`, because core owns `com.campusmaps.routing` (`Router`, `RouteOption`, `Instructions`) and the same fully qualified names in two modules would clash.

## The adapter

### Buildings: `CoreBridge`

`CoreBridge.load(context)` reads every `assets/buildings/*.json` with core's `BuildingLoader`. In debug builds it runs `BuildingValidator` and logs every problem under the `BuildingRepository` tag (ERROR as `Log.e`, WARN as `Log.w`, INFO as `Log.i`); ERROR and WARN per building are kept in `AppContainer.loadProblems` and shown as a count in the debug card (docs/20 QA #17). A file that fails to parse is logged and skipped.

`CoreBridge.fromCore(core)` builds the teammate's `Building` / `GraphNode` / `GraphEdge` view, used for drawing, the minimap, S1 lists and the simulated walker:

| core | app view |
|---|---|
| y grows **north** | y grows **south** (screen style): `Point(x, -y)` |
| `entrance` | `ENTRANCE` |
| `intersection`, `waypoint` | `CORRIDOR` |
| `stairs`, `elevator`, `room` | `STAIRS`, `ELEVATOR`, `ROOM` |
| `startPoints` (CS P1, P2) | `OUTDOOR` nodes; routed as `Start.Outside(lat, lng)` |
| no start points (KL, CSE) | one synthetic `OUTDOOR` node `OUT` at the building origin, named "by <nearest entrance>" (Raphael's app did the same) |
| anchor `text` / `aliases` | `GraphNode.signText` |
| room `doorFacing` | `GraphNode.roomInside` (2 m behind the door) |
| hallway / door / outdoor edge | `WALK`; stairs, elevator as themselves; plus drawing-only edges from each outdoor start to each outdoor entrance |
| access windows | not copied: `Building.isCardOnly` asks core's `Access.isLocked` live |
| `demoDestinations` | `demoDestinationIds` |

The view keeps a reference to the core `Building` (`Building.core`). "Where are you?" lists the outdoor starts first, then inside nodes (S1, S2 first), then rooms. The default start is `S1` when the file has it (Demo A, Klaus expo table), otherwise the first outdoor start (CS: P1). Default building: KL.

### Routing: `CoreRouter`

Every route comes from core `Router.route(building, start, destination, Prefs(avoidStairs, now))`:

- `start`: an `OUTDOOR` node becomes `Start.Outside(lat, lng)`; anything else `Start.AtNode(id, cameFrom)`.
- `now` comes from `AppClock.now()` (real, or simulated by the debug overlay). Core never reads the clock.
- Approved student shortcuts are added as extra core edges (`Building.copy(edges = ...)`), `HALLWAY` with notes `student shortcut: <name>`. A shortcut whose ends are on different floors, or not both in the building file, is dropped (teammate's open issue 1, second line of defence; the first is on submission, below).
- Output: the teammate's `RoutePlan.Options` / `AlreadyHere` / `NoRoute`, so S1b, S2, S3, `GuidanceEngine`, the watch and glasses flows are unchanged.
  - `RouteOption`: entrance from core's `entrance` (null for inside starts, "From here"), method from `verticalMethod`, ETA `etaSec`, walk `distanceM`, elevator wait from the ETA breakdown, `alsoVia` from core, `core` = the core option.
  - `LockedNotice.fromCore(notice)` parses core's sentence into locked and replacement entrance names (the watch's "Main locked" face needs them); `text` is core's sentence minus "Heads up: ".
  - `Route.points`: core node ids with `OUTSIDE` replaced by the start node (drawn on the entrance's floor, so a floor-2 entrance is a flat walk); cumulative walking metres from the core edges (haversine for the outdoor leg, as core does).
  - `RouteStep`s: one per core instruction, **text word for word**. The adapter only adds where each step starts and when it is done, as `GuidanceEngine` needs: core `START` from outside plus `ENTRANCE` become one `WALK_TO_ENTRANCE` with the entrance text as `approachText`; `TURN` becomes `TURN_LEFT/RIGHT/AROUND` (or `EXIT_TOWARD` right after a ride, done after half its first leg, at most 4 m); `ELEVATOR` / `STAIRS_UP/DOWN` become rides done on the arrival floor; `DOOR` stays a door; `ARRIVE` keeps core's side sentence ("Room 608 is ahead") and sets `Side`. `LOCKED_NOTICE` is not a step: it is the S1b banner, the watch's first face, and the first thing spoken (S2 speech and the glasses' first cycle say "Heads up: ... " before the first instruction).
  - Empty result: "No route to <room>: every entrance is card-only at Sat 23:30." when every outdoor entrance is locked from outside, "without stairs" when only avoid stairs blocks it, else "from here" (docs/20 QA #12). Start equal to destination is "You are already here" (QA #14).
- Reroutes (`GuidanceController.reroute`, debug jumps) call `CoreRouter.bestRoute(..., cameFromId, preferMethod)`: `cameFrom` is the route node last passed, so core says "Turn left/right toward X"; `preferMethod` keeps elevator vs stairs (Raphael's `NavLogic.pickReroute`). Rerouting from an elevator node starts with the ride ("Take the elevator to floor 1").

The teammate's `Router`, `StepWriter`, `Instructions` and `DemoBuildings` / `BuildingBuilder` are deleted. His `RouterTest` is replaced by `CoreRouterTest` (demos A, B, C through the adapter, shortcuts, rerouting, and a sweep of every building x demo destination x start x avoid stairs x seven clock times) and `CoreBridgeTest`; `GuidanceEngineTest` now runs on the real CS file.

### Clock and debug overlay

`AppClock` starts on **real time** (Raphael's design; the teammate started on Sat 21:00). A simulated day is a day of the event weekend (Fri 25 to Mon 28 September 2026, docs/20 QA #8); Reset demo returns to real time. `cycle()` stays as a debug link.

The teammate's debug card keeps its lines and links and gains Raphael's controls (`DebugControls` in `ui/screens/DebugOverlay.kt`):
- **Sim time** switch (on = Sat 2026-09-26 21:00) and "(change)": day chips Fri/Sat/Sun/Mon plus a Material 3 `TimePicker`.
- **Fake walk**: Step (next route node), Walk / Pause (one node every N s), interval -/+. Either pauses the teammate's continuous simulated walker; "Continuous walker: on/paused" toggles it back.
- **Low confidence** switch (persistent 0.2 until switched off; the teammate's 4 s "Drop confidence" link stays).
- **Jump to node / start point** picker: before guidance it sets "Where are you?"; during guidance it moves along the route, or off it (inside or to an outdoor start) with a reroute (docs/20 QA #10).
- Lines: building, clock and `now`, start node, AR override and ARCore's own availability string, barometer hPa, watch connected count, glasses state, TTS, network, validator problem count, and during guidance node / floor / confidence / pose / step / along / off-route / reroutes.
- The header row is the drag handle, the card is clamped on screen and its body scrolls (QA #7).

### AR

Raphael's AR layer is copied unchanged. `ui/ar/ArInputsAdapter.kt` replaces his `ArInputs.kt`: `GuidanceState.toArRouteInput()` negates y (the teammate's plan is y-down), takes the next turn arrow from the current TURN step, the destination, the placement at the current segment, and core's `floorHeightM`. `GuidanceController.buildingToWorld` holds the transform. `GuidanceScreen` composes `ArGuidanceView` only when ARCore reports supported, the camera is granted, AR is not forced off and the AR session has not failed; otherwise the teammate's `CameraPreview` / painted hallway plus `ArWorldOverlay` remain the fallback. Not exercised on hardware in this pass (the emulator reports `UNKNOWN_ERROR`).

### Glasses

`mwdat-core/camera/mockdevice` 0.7.0 compile on this version set and are in the app, with the Developer Mode manifest placeholders. `GlassesLink` is still the teammate's `SimulatedGlassesLink`: its Looking / Recognising / Speaking cycle is exactly the burst-then-speak design of 06 (camera and audio never overlap), so the real 0.7.0 implementation drops in behind the same interface. Porting the 0.7.0 session code (from `tools/meta-sample/sample-0.7.0`, not copied: 318 MB, gitignored) is still open.

## Removed, and why

| Removed | Why |
|---|---|
| Teammate `Router`, `StepWriter`, `Instructions` | Replaced by core's router and instruction text (owner decision: core is the source of truth) |
| `DemoBuildings`, `BuildingBuilder` | Invented plans; replaced by the survey-derived JSON files |
| `GoogleOutdoorMap`, `MapConfig` renderer switch, `maps-compose`, `MAPS_API_KEY` | Owner decision: the outdoor leg is ARCore Geospatial per docs/05; the map is always the offline floor plan |
| `CardOnlyWindow`, `GeoAnchor` | Access rules and geo now come from core (`Access`, `Geo`, `Origin`) |
| FormatsTest's instruction-template and card-only-window tests | Those templates and rules live in core now and are tested there |

Kept from the teammate and working: theme, components, icons, all screens, shortcuts, `wear` + `shared`, `Speaker`, `WatchBridge`, `NetworkMonitor`, `SettingsRepository`, `AppFlowTest`, screenshots.

## Other changes to the teammate's code

- **Shortcut floor rule (his open issue 1):** S4 refuses From / To on different floors ("From and To must be on the same floor"), outside start points are no longer offered as From / To (they are not graph nodes in core), and `CoreRouter` drops any cross-floor or unknown-node shortcut.
- **Double-tap guard** (QA #1, #2): a tap that lands on the next screen within 600 ms is ignored (`MainViewModel.go` / `settled`).
- **End route / Stop** sets "Where are you?" to where the student stopped, so S1b is recomputed from there (Raphael's behaviour), and after arrival reads "You are already here".
- `WatchBridge.connectedCount` for the debug line.
- `AppFlowTest` updated to the real data (CS P1 to 608 via Library South entrance (floor 2), CSE Sat 21:00 banner, CSE Sat 23:00 no route) and a 700 ms settle after screen changes.

## Verified (2026-09-25)

- `./gradlew :core:test :shared:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :wear:assembleDebug`: green. core 45 tests (1 `@Ignore`d by design), app 41, shared 4.
- `AppFlowTest` on `survey36` (emulator-5554): 8 of 8 pass.
- Emulator drive (screenshots in the session scratchpad, `integ/`):
  - KL: S1 to S1b, "From here 0:34", S2 walks to "Room 1116 is on your right" and the arrived state.
  - CS 608: P1 gives Library South entrance (floor 2), elevator 1:27, also via Classroom South main (floor 2); P2 gives Walters main entrance, elevator 1:45, also via Walters side entrance. Avoid stairs flips the stairs card to an elevator card live. S2 walks P1 to 608 through the elevator to arrival.
  - CSE Sat 21:00 (set with the switch and picker): "Heads up: Main entrance is card-only now. Using West entrance instead." S3 cycles and Stop returns to S1b.
  - Debug: Step, jump to KL EL-2 mid-route (reroute, "Take the elevator to floor 1", F2), jump outside (reroute from OUT), low confidence (Locate me prompt).
  - Demo mode: DEMO tag, demo destinations only, long-press does nothing, `KEEP_SCREEN_ON`; demo mode and avoid stairs survive `force-stop`.
  - Process death during S2 (process killed in the background): relaunch lands on a clean S1, no crash.
  - `logcat -s AndroidRuntime:E`: empty. Validator: KL 3 ERROR (rule 7, KL-A01/A02/A05 photos missing), CS and CSE 0.

## Open issues

1. The teammate's app does not save the screen or selection across process death: it always restarts on S1. That is safe (no stale S2, Raphael's QA #3 intent) but it does not restore the trip.
2. Real glasses: `GlassesLink` over mwdat 0.7.0 is not written yet.
3. Real localization: only `SimulatedPositionProvider` implements `PositionProvider`. `ArGuidanceView`'s placement and `BuildingToWorld.toBuilding` are the start of a real one (docs/05 "Porting" step 5).
4. AR on hardware: `ArGuidanceView` inside `GuidanceScreen` is untested on the S25.
5. Walk interval and the demo building are not persisted (Raphael's shell persisted both); demo mode and avoid stairs are.
6. `GuidanceEngine` completes a step within 1.5 m of its end, so when core gives two instructions a few metres apart ("Exit toward Elevator lobby, floor 6", then "Turn left at Elevator lobby, floor 6" 3 m later), the first shows only briefly.
7. The teammate's WHATS-LEFT issues 4 to 6 are unchanged (see WHATS-LEFT.md); 2 and 3 were already handled in his code (`WatchBridge.clear()` on stop, `STAIRS_DOWN`).
8. `shared/bin/` (stale IDE output) is tracked in the teammate's commit; it is now in `.gitignore` but still tracked.
