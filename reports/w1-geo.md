# w1/geo report: LEG 2 (outdoor Geospatial leg and map-to-AR hand-off)

## Built and tested on the JVM
- `geo/GeospatialProvider.kt`: `GeospatialProvider` (StateFlow<GeoState>: earth tracking, lat/lng/heading, horizontal and yaw accuracy, VPS, anchor, failure text; start/stop/checkVps/placeTerrainAnchor) and `OutdoorArrowGate` (arrows only when TRACKING and accuracy under 10 m, yaw under 15 degrees when known; chip text).
- `geo/HandoffTrigger.kt`: 40 m trigger (constants TRIGGER_M 40, REARM_M 60, STALE_MS 10 s). Fires once per route, re-arms above 60 m or on a new route, ignores stale fixes. `geo/HandoffState.kt`: MAP, CARD, ANIMATING, AR state machine.
- `geo/FakeGeospatialProvider.kt`: scripted approach (localize, then walk with accuracy tightening), `step()` for tests, `play(scope)` for emulator QA.
- `ui/ar/OutdoorArrows.kt`: pure `OutdoorChain` (straight chevron chain toward the anchor, 2 m to 15 m, 1.5 m apart), the SceneView layer, `TrackingChip`, `nearestRouteNodeName`.
- Tests: `HandoffTriggerTest` (8) and `OutdoorArrowGateTest` (6), green. `:app:assembleDebug :app:testDebugUnitTest` green.

## Built, needs the phone outdoors
- `geo/ArCoreGeospatialProvider.kt`: fed by ArGuidanceView's session (`configure` from sessionConfiguration, `onFrame` from onSessionUpdated). Enables GeospatialMode only with location permission and device support, checkVpsAvailabilityAsync, resolveAnchorOnTerrainAsync at the entrance, publishes at 4 Hz, logs everything under tag `Geo`. Every failure is soft (UNAVAILABLE or PAUSED plus a reason).
- `geo/FusedLocationFixes.kt` (1 Hz fixes, empty without permission), `geo/GeoEntrance.kt` (entrance lat/lng from the core building file; logs why when missing), `geo/HandoffController.kt` (the trigger plus `startHandoff()`, `animationDone()`, `backToMap()`).
- `ui/transition/MapToArTransition.kt`: `MapToArTransition(mapContent, arContent, progress)` (map tilts 55 degrees, zooms 2.6x into the dot and fades while AR fades in underneath, 900 ms), `rememberHandoffProgress`, `HandoffCard` (Clay Deep, Sora, "Almost there. Point your camera ahead", tap or 1.6 s auto), `MapToArHost`, `FakeMapPlaceholder`, previews.

## Phone check (S25, 23:08, indoors on a desk, temporary wiring not committed)
CS, Outside Decatur St to 608: logged `outdoor target E-LM2 ... 33.75255, -84.38702`, `start (API key set)`, `Geospatial mode ENABLED`, `earth state ENABLED`, `checkVpsAvailability = AVAILABLE`. Earth never reached TRACKING (camera dark on a desk), so the chip said "Finding your position" and no arrows were drawn. When the simulated walker crossed the entrance, `stop` was logged and the chip changed to "Elevator lobby, floor 2". No crash. Still untested: TRACKING outdoors, the Terrain anchor, the chevrons, the 40 m trigger on a real walk, and the transition on the device.

## ARCore key (owner)
Put `ARCORE_API_KEY=<key>` in `local.properties` at the repo root (gitignored). It goes into the manifest as `com.google.android.ar.API_KEY` and sets `BuildConfig.ARCORE_API_KEY_SET`. With no key, the outdoor leg shows the banner, the map and the distance only.

## Wiring for the orchestrator
1. In the app/view model: `val handoff = HandoffController()` and `val geo = ArCoreGeospatialProvider(context)`, one of each.
2. When a route starts: `val entrance = GeoEntrances.forRoute(context, route)`, then `handoff.newRoute(entrance)` and `handoff.attach(scope, FusedLocationFixes.flow(context))`. Call `handoff.detach()` when the route ends.
3. Ask for ACCESS_FINE_LOCATION on the map screen, before AR starts. GuidanceScreen also asks, but a grant made there only works from the next AR session.
4. Put the map and S2 inside the host: `MapToArHost(ui = handoff.ui.collectAsState().value, controller = handoff, mapContent = { ExploreScreen(...) }, arContent = { GuidanceScreen(..., geo = geo, outdoorEntrance = ui.entrance, outdoorDistanceM = ui.distanceToEntranceM) })`. You can pass `focus = TransformOrigin(dotX, dotY)` for the user's dot.
5. The map agent's AR button calls `handoff.startHandoff()`. End route or back calls `handoff.backToMap()`.
6. Emulator QA (no ARCore): use `FakeGeospatialProvider(from, to).play(scope)` and feed `fake.asFix(now)` into `handoff.onFix`.

## Merge notes
- ArGuidanceView: one new parameter `outdoor` (default null), `latestOutdoor` state, two one-line calls in sessionConfiguration and onSessionUpdated (the lambdas now name `session`), and `outdoor?.let { OutdoorArrowLayer(it) }` in the scene. If the localization agent's new session setup replaces these lambdas, re-add the two calls.
- GuidanceScreen: new parameters `geo`, `outdoorEntrance`, `outdoorDistanceM` (all default null, so S2 is unchanged without them), the chip, a `distanceOverrideM` parameter on CompactBanner, and `OutdoorGeoEffect` at the end of the file.
- Added play-services-location 21.4.0 (a new library, no upgrade to any existing version; it was already in the Gradle cache). Also added the location permissions and the API key meta-data to the manifest.

## Files
New: app/src/main/java/com/campusmaps/geo/{GeospatialProvider, HandoffTrigger, HandoffState, FakeGeospatialProvider, ArCoreGeospatialProvider, FusedLocationFixes, GeoEntrance, HandoffController}.kt, ui/ar/OutdoorArrows.kt, ui/transition/MapToArTransition.kt, app/src/test/java/com/campusmaps/geo/{HandoffTriggerTest, OutdoorArrowGateTest}.kt.
Changed: ui/ar/ArGuidanceView.kt, ui/screens/GuidanceScreen.kt, app/build.gradle.kts, app/src/main/AndroidManifest.xml, gradle/libs.versions.toml.
