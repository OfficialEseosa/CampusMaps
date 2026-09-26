# W1 MAP agent report: Explore map (leg 1)

Branch `w1/map`. Build `:app:assembleDebug :app:testDebugUnitTest` green; 51 app unit tests pass (10 new in `outdoor/OutdoorRoutesTest`). Verified on emulator-5558 with the fix `-84.3880 33.7545`.

## What works without a key
- Explore opens as home (no fix yet, or more than 150 m from every building origin, and not demo mode). Without location permission: blank map centred on 33.7530, -84.3853 and the hint card "Turn on location to see your route" (tap asks again).
- Room chips for every demo destination (KL 1116, CS 150, CS 608, CSE 220), a search field that opens S1, settings, layers, "Show my location", and the AR button above the sheet.
- The entrance comes from core's `Router.route(core, Start.Outside(lat, lng), room, Prefs(avoidStairs, clock.now()))`, first option. Entrance lat/lng from the core node; if missing, the building origin and "(approximate)" in the sheet.
- Straight line, haversine distance, minutes = distance / 1.3 m/s rounded up. Sheet: room, "Classroom South · Floor 6 · Room", tiles (182 m, 3 min, Floor 1), steps "Walk to the Walters main entrance", "Enter Classroom South at the Walters main entrance", "Turn left at Walters lobby", then "Start AR navigation".
- Start AR navigation (and the AR button) goes to S2 on the same room and entrance: it selects the building, the room and the outdoor start point nearest the student, waits for the trip plan, and calls the existing `startSession` with the option whose entrance matches.
- Back: S2 goes to S1b, then S1, then Explore. Search on Explore opens S1.
- Without a key the map shows no tiles, dot or line (the SDK draws nothing); the sheet still works.

Note: from the test fix, core picks **Walters main** (182 m), not Library South. That is core's choice from that spot. Library South wins from the Decatur St side.

## With the key (now in local.properties)
- Tiles, blue dot with accuracy disc, clay line on a white casing, entrance pin and satellite toggle all work (shots 04, 05).
- Street directions do **not** work yet. The key is blocked for the Routes API (HTTP 403 API_KEY_SERVICE_BLOCKED), and the legacy Directions API is not enabled on the project (REQUEST_DENIED). The app tries Routes first, then legacy, then uses the straight line. This is logged under the `Directions` tag, and the app does not crash or block.
- **Owner, one line:** in Google Cloud, enable the "Routes API" and add it to the key's API restrictions (the key stays in `local.properties` as `MAPS_API_KEY=...`, which is gitignored).

## Files
- New: `app/src/main/java/com/campusmaps/outdoor/{OutdoorRoute.kt, DirectionsClient.kt, UserLocationProvider.kt, ExploreViewModel.kt}`, `ui/map/{MapConfig.kt, GoogleOutdoorMap.kt}`, `ui/screens/ExploreScreen.kt`, `app/src/test/java/com/campusmaps/outdoor/OutdoorRoutesTest.kt`, `reports/shots-map/*.jpg`.
- Changed: `gradle/libs.versions.toml` (mapsCompose 8.2.2, playServicesLocation 21.3.0), `app/build.gradle.kts` (local.properties -> BuildConfig + manifest placeholder, two deps), `AndroidManifest.xml` (fine/coarse location, geo API_KEY meta-data), `docs/14-tech-stack.md` (end), `ui/CampusMapsApp.kt`, `ui/MainViewModel.kt`.

## Hooks for the merge
MainViewModel:
- `enum class Screen { ..., ADD_SHORTCUT, EXPLORE }`
- `back()`: `Screen.DESTINATION -> if (_exploreHome.value) go(Screen.EXPLORE)` and `Screen.EXPLORE -> Unit`
- New block before "S2 / S3": `exploreHome` StateFlow, `showExploreAsHome()`, `openExplore()`, `openSearchFromExplore()`, `startFromExplore(buildingId, destinationId, entranceId, lat, lng)`; import `kotlinx.coroutines.flow.first`.

CampusMapsApp:
- `exploreHome` collected; `exploreVm = viewModel(factory = ExploreViewModel.Factory(app, LocalContext.current))`; `LaunchedEffect(Unit) { if (exploreVm.exploreShouldBeHome()) vm.showExploreAsHome() }`
- `screenDark`: `Screen.EXPLORE -> false`
- `BackHandler(enabled = screen != Screen.EXPLORE && (screen != Screen.DESTINATION || exploreHome))`
- `Screen.EXPLORE -> ExploreScreen(...)` branch (also passes a room picked on S1 to the map)
- Debug link "Open Explore map" (`vm::openExplore`). This is the only way to reach Explore from S1 when S1 is home (near a building, or demo mode). An S1 app-bar map button would be better, but I did not touch DestinationScreen.

## Seam for the Geospatial agent (40 m hand-off)
`ExploreViewModel.distanceToEntranceM: StateFlow<Double?>` gives the metres from the fused fix to the recommended entrance, or null with no fix or no plan. It is built on `UserLocationProvider.distanceTo(LatLngPoint): Flow<Double?>`. `ExploreViewModel.state.value.plan` has `entranceId`, `entrance` (lat/lng) and the core `RouteOption`.

## Open
- The S2 simulated walker starts at P1/P2 (the nearest outdoor start), not at the real fix. The entrance matches when core agrees.
- No instrumented test for Explore yet. `AppFlowTest` may now open on Explore when the emulator has no fix; tests that expect S1 first may need `vm` in demo mode.
