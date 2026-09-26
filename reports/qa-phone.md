# Phone QA, wave 1 (Galaxy S25 Ultra, 2026-09-25 23:33 to 23:50)

Device: S25 Ultra `R5CY12PPNPY`, lying face up on a desk (rear camera against the desk, so the AR camera is dark). APK: current main (`edfe957`, installed 23:32), then this branch's fix (`f0a2436`) for check 4. Screenshots (360 px): `reports/shots-qa-phone/`. `AndroidRuntime:E` was empty for the whole session. No crash, no ANR.

## Cold start (`am start -W`, force-stop first)

| Run | TotalTime | State |
|---|---|---|
| 1 | 679 ms | COLD |
| 2 | 544 ms | COLD |
| 3 | 542 ms | COLD |
| After reinstall | 725 ms | COLD |

This is the same range as docs/22 (489 to 706 ms), so the splash adds nothing before the first frame.

## Checks

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Cold start and splash | **Pass** | 542 to 725 ms. A screen recording (`01-splash-frames.png`, 5 fps) shows the system splash (logo on clay-brown), then the Compose overlay: corners, pin drop, chevrons, dotted line, "CampusMaps", then a crossfade into the map. Total about 1.4 s. **Home = Explore**, not S1. Demo mode is off, and the phone's fix (33.75716, -84.38383, 4.8 m) is about 530 m from the CS origin, more than 150 m from every building. One small thing: a thin vertical line near the top left shows on the splash frames (see bug N6). |
| 2a | Explore tiles, key and dot | **Pass, with a bug** | Tiles render with the key. The blue dot with its accuracy disc shows at the real fix, which is **4.4 to 4.8 m fused (GPS/Wi-Fi) indoors**. But the camera opens on the campus centre (33.7530, -84.3853), about 470 m from the fix, so the dot is off screen until you tap "Show my location" (`02-explore-dot.png`, bug N4). |
| 2b | CS 608: line and sheet | **Pass** | Clay line from the dot to the entrance pin. Sheet: "596 m / 8 min / Floor 2", "Enter at the Classroom South main · straight line", 3 steps, "Start AR navigation" (`03-explore-cs608.png`). |
| 2c | Street steps from the Routes API | **Fail (key setting, not code)** | Logcat `Directions: Routes API HTTP 403; trying legacy Directions`, then `Legacy Directions gave no route (status not OK); straight line`. A direct request with the same key returns `PERMISSION_DENIED, reason API_KEY_SERVICE_BLOCKED, service routes.googleapis.com`. **The key's API restrictions still do not list the Routes API.** Enabling the API on the project is not enough. The straight-line fallback works. |
| 2d | Map-to-AR transition | **Fail (jump cut)** | 8 s recording at 6 fps: the map frames go straight to the black S2 frame, with no tilt or zoom and no crossfade. Cause: `startFromExplore` calls `startSession`, which does `go(Screen.GUIDANCE)`, so the `Screen.EXPLORE` branch that hosts `MapToArHost` leaves the composition before the 900 ms animation can play. See bug N1. |
| 2e | Explore entrance vs S2 entrance | **Fail** | The map draws the line to **Classroom South main**, but S2 says "Walk to **Library South entrance** (floor 2)" (`04-S2-from-explore-entrance-mismatch.png`). See bug N2. |
| 2f | End route returns to the map | **Pass** | Back on Explore with CS 608 still selected. `WatchBridge: Route ended: clearing the watch` logged once. |
| 3a | S2 with real ARCore | **Untestable on the desk** | The camera is dark, so "Place route here" never appears and the floor tap and arrows could not be tested. Logs: `AnchorImages: 0 images; skipped CS-A01, CS-A08 (ImageInsufficientQualityException)`, then `Position: AR view live: camera drives the position once the route is placed`. |
| 3b | Position does not move by itself | **Pass** | "in 592 m" stayed the same for more than 60 s with the phone still (the old walker bug is gone). |
| 3c | Debug Step | **Pass** | Two taps went P1 to H10, "Turn left at T junction, floor 2, in 18 m". `Position: position mode SIMULATED (debug control)`. The position then held (walker paused). `05`/`06` screenshots. |
| 3d | "Locate me" | **Pass, with a flicker** | It shows at once with the dark camera. In the recording, the Locate me card and the "Reading sign..." pill blink on and off every few frames (bug N5). The 3 s cover test itself cannot be done, because the camera is already covered. |
| 4 | Outdoor layer (Geo) | **Pass after fix** | First S2: `Geospatial mode ENABLED`, `earth state ENABLED`, `checkVpsAvailability = AVAILABLE`, `earth TRACKING at 33.757160, -84.383822 acc 4.4 m yaw 56.9 deg`, `terrain anchor SUCCESS`. The chip says "Finding your position" (yaw 57 degrees is over the 15 degree gate, as expected indoors). **Second S2 in the same process: `checkVpsAvailabilityAsync failed java.lang.IllegalArgumentException`** (bug N3, fixed on this branch). With the fix: End route logs `Geo: stop`, and the second S2 logs VPS AVAILABLE and anchor SUCCESS again. No crash either way. Later runs showed earth accuracy of 276 to 287 m, so indoor accuracy is not stable. |
| 5 | Real glasses | **Blocked: owner consent needed** | With `setprop debug.campusmaps.glasses real`: `GlassesMode: glasses mode real`, `GlassesLink: start: source Ray-Ban Meta`, `GlassesMeta: Wearables initialised, dev mode false`, then `registration state UNAVAILABLE`. It retries after 2, 4, 8, 16 and 30 s (the reconnect loop works), and S3 shows "Not connected". Android asked for **Nearby devices** (`07-...png`), and the Meta AI app opened **"Connect CampusMaps to your Meta devices? Unverified app"**, which needs a toggle and Connect (`08-...png`). I did not grant either one, so no burst ran and there is no camera OFF / SPEAK order to report. Bluetooth: the glasses "RB Meta 02GZ" are bonded, but the ACL link is down (`ACL BR/EDR:N LE:N`). A2DP went unavailable at 17:16 and no A2DP device is active now, so TTS would play on the phone speaker. The property is reset to unset afterwards. |
| 6 | Watch | **Pass (phone side)** | End route from S2, both from the map and from S1b: exactly one `Route ended: clearing the watch` plus `Watch clear sent to 1 watch(es)` each time. Successful step sends do not log, and there were no `Watch not reachable` lines. The watch face itself was not seen. Done on arrival was not run (time-box). |
| 7 | Shortcut persistence | **Not run (time-box)** | Submitting a shortcut in S4 takes a walk recording and 2 photos. Hand steps are below. |

## New bugs

| # | Bug | Severity | Repro | Status | File |
|---|---|---|---|---|---|
| N1 | The map-to-AR transition never plays: it is a jump cut from the map to S2. `startSession` switches to `Screen.GUIDANCE`, and `MapToArHost` only exists in the `Screen.EXPLORE` branch (its `arContent` also requires `screen == GUIDANCE`, which can never be true there). | Medium (demo polish) | Explore, CS 608, Start AR navigation, and record the screen. | Open. Fix idea: let `startFromExplore` start the session without leaving `EXPLORE` (for example a `startSession(..., screen = null)` parameter), and render S2 through `arContent` while `fromExplore` is set. The back, dark-screen and debug checks that key on `Screen.GUIDANCE` also need `fromExplore`. | `ui/MainViewModel.kt` (`startSession`, `startFromExplore`), `ui/CampusMapsApp.kt` |
| N2 | The Explore entrance and the S2 entrance disagree. The map routes from the real fix and picks Classroom South main. S2 starts from the nearest outdoor start point (P1, Decatur St side), where CS main is only an "also via" of the Library South option. So `options.firstOrNull { it.entrance?.id == entranceId }` misses and falls back to the first option. The student follows the map line to one door, and AR then talks about another door 592 m away. | Medium | Phone at 33.7572, -84.3838 (or any fix nearer CS main), Explore, CS 608, Start AR. | Open. Fix idea: also match the option's `alsoVia` entrances, or route S2 with `Start.Outside(lat, lng)` from the fix like Explore does. | `ui/MainViewModel.kt:311-321` |
| N3 | The second S2 in one process checked VPS on the previous, closed ARCore session (`IllegalArgumentException` from `nativeCheckVpsAvailabilityAsync`). `geo.stop()` only ran when the outdoor leg ended, not when S2 left the screen, and "Geo: stop" was never logged on End route. | Medium (outdoor leg silently degraded on every second route) | S2 (outdoor start), End route, then S2 again: logcat `Geo`. | **Fixed** (`f0a2436`): `DisposableEffect(geo) { onDispose { geo.stop() } }` in `OutdoorGeoEffect`. Verified on the S25: `Geo: stop` on End route, and the second S2 logs VPS AVAILABLE and anchor SUCCESS. | `ui/screens/GuidanceScreen.kt` |
| N4 | Explore does not centre on the first fix. The camera stays on the campus centre, so the blue dot is off screen until "Show my location" is tapped, or until a room is picked (the route framing then includes it). | Low | Cold start away from campus centre. | Open | `ui/screens/ExploreScreen.kt` / `ui/map/GoogleOutdoorMap.kt` (animate to the first non-null fix once) |
| N5 | S2 with a dark camera and no placement: the "Locate me" card and the "Reading sign..." pill blink on and off, a few times per second in the recording. | Low | S2 with the camera covered: record the screen. | Open (confidence hovering around the threshold; needs hysteresis) | `ui/screens/GuidanceScreen.kt`, `loc/SwitchablePositionProvider.kt` |
| N6 | A thin vertical line shows under the status bar on the splash frames (top left, visible in several frames). | Low (cosmetic) | Cold start with a screen recording. | Open, not diagnosed. It may be the dashed-line path drawn before its trim starts. | `ui/splash/SplashOverlay.kt` |
| N7 | After glasses setup opens the Meta AI app, the Meta AI activity stays inside the CampusMaps task. Tapping the CampusMaps launcher icon (or `am start`) then shows Meta AI, not CampusMaps, until that task is removed. | Medium (at the expo, a judge could "lose" the app) | Real glasses, not registered: S3 opens Meta AI, back out, then relaunch CampusMaps. | Open. Fix idea: start `GlassesSetupActivity` with its own `taskAffinity`/`excludeFromRecents`, or finish it before `startRegistration`, so Meta AI opens in its own task. | `glasses/GlassesSetupActivity.kt`, `AndroidManifest.xml` |
| N8 | The Explore sheet text is clunky: "Walk to the Classroom South main" (the node name has no "entrance"), and "Turn right at Hallway at Classroom South main, down the hallway". | Low | Explore, CS 608. | Open (node names in `CS.json`, or `OutdoorRoutes.sheetSteps`) | `outdoor/OutdoorRoute.kt` |
| N9 | `GlassesMeta: Wearables initialised, dev mode false`, although HANDOFF says Developer Mode works with app id 0. Registration may need Developer Mode on in the Meta AI app first. | Info | See check 5. | Owner to check | `glasses/MetaStillSource.kt` |
| N10 | Debug card (collapsed) covers the top of the expanded S2 banner, and the S2 End-route button moves when the banner expands (478 to 658 px, then 394 to 477 px), so a tap aimed at its old place misses. | Low (debug only) | S2 with the debug card folded, then Step. | Open | `ui/screens/DebugOverlay.kt` |

## Only a human can test

1. **Routes API key.** In Google Cloud Console, go to APIs & Services, then Credentials, then the Maps key, then API restrictions, and add "Routes API". Save and wait about 5 minutes. Then pick CS 608 on Explore. `adb logcat -s Directions` should be silent, and the sheet should say something other than "straight line".
2. **AR floor tap and walk.** Hold the phone up and point it at the floor. Wait for "Place route here" and tap it. The chevrons should lie on the floor along the route. Walk 3 to 5 m: "in N m" should change only while you move. Cover the camera for 3 s: "Locate me" should show within about 2 s and go away after you uncover it. Tags: `Position`, `ArGuidance`, `AnchorImages`.
3. **Map-to-AR transition** (after N1 is fixed). Record the screen from Start AR navigation. The map should tilt and zoom into the dot over about 0.9 s while S2 fades in.
4. **Glasses.**
   1. Turn the glasses on and take them out of the case. Check that Bluetooth shows "RB Meta 02GZ" as connected (it is bonded but not connected now).
   2. In the Meta AI app, check Developer Mode (Settings, App info, tap the version 5 times, then glasses settings, Developer Mode).
   3. `adb shell setprop debug.campusmaps.glasses real`, then force-stop and open CampusMaps. Open CS, Room 608, "Guide me with glasses".
   4. **Allow** "Nearby devices". In Meta AI, turn on the **Unverified app** toggle and tap **Connect**. Then allow the glasses camera permission.
   5. Back in CampusMaps, within 30 s: `adb logcat -s GlassesLink GlassesMeta` should show CYCLE / BURST camera ON / 3 stills / **BURST camera OFF before SPEAK start**.
   6. While it speaks, run `adb shell dumpsys audio | grep -i -A3 a2dp` to see whether the speech is on the glasses.
   7. After this, relaunch CampusMaps from the launcher and check whether Meta AI shows instead (bug N7).
5. **Watch.** Start any route: the watch shows the arrow and distance. On End route, and on Done after arrival, the watch face goes back to "Start a route on your phone". Logcat `WatchBridge` should show exactly one "Route ended" line each time.
6. **Shortcut persistence** (docs/22 bug 9):
   1. On S1 for CSE, add a shortcut from Floor 2 corridor to Room 220 (walk and 2 photos).
   2. Open the debug card and tap "Approve my pending shortcuts".
   3. `adb shell am force-stop com.campusmaps`, then reopen the app.
   4. Route to CSE Room 220 from Floor 2 corridor. The card should read "Student shortcut ... via Floor 2 corridor cut through".
7. **Outdoor Geospatial walk.** Walk outdoors from about 60 m to the CS Library South entrance. The chip should go to TRACKING, the chevrons should point at the door, and the 40 m trigger should show "Almost there".

## Device state left

- The `debug.campusmaps.glasses` property is back to unset.
- CampusMaps is on this branch's debug build (main plus `f0a2436`).
- The Nearby devices prompt was dismissed (not answered). The Meta AI connect screen was backed out of without connecting.
- During check 5, a mis-aimed tap opened "Create an image" in Meta AI and typed one letter. It was deleted and nothing was sent.
- The CampusMaps task holding Meta AI was removed with `am stack remove`.
