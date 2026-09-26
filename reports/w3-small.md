# Wave 3: small fixes (branch w3/small)

Build: `:app:assembleDebug :app:testDebugUnitTest` green (all unit tests pass, 6 new). Device: emulator-5554 (API 36), no glasses or watch. Screenshots in `reports/shots-w3-small/`.

| # | Fix | Status | How verified |
|---|-----|--------|--------------|
| 1 | Directions was re-requested on every recompute (O1) | Fixed | New `DirectionsCache`: one answer (or failure) per building + entrance, position snapped to a 15 m grid; asks again only after a move of more than 25 m or a new entrance. `DirectionsCacheTest` (6 tests). On the emulator: Explore, CS 608, then four 20 m GPS moves (each triggers a recompute): 0 new "Directions: request" lines in Logcat; a 55 m move: 1 request. |
| 2 | Explore unreachable from S1 in demo mode (O3) | Fixed | "Campus map" icon (48 dp) after Settings in the S1 app bar, every mode; the row stays in non-demo mode. First try squeezed the DEMO tag to "DEM" at 411 dp, so the icon became an optional `onMap` slot of `AppTopBar` that also trims the start gap (4 dp) and end padding. Emulator: DEMO tag fits, icon opens Explore in demo mode, Back returns to S1 (shots 01, 02). |
| 3 | Debug card reopens unfolded on each screen; link rows overlap (O5) | Fixed in code, fold persistence not checked on device | Fold state moved from `DebugOverlay` to `CampusMapsApp` (above the screen switch); a long-press that shows the card again opens it unfolded. Link rows: 44 dp tall, no gap (44 dp pitch, measured 116 px on the emulator), minimum touch target lowered to 44 dp for the link list. A tap 5 px below a row edge hit the lower row (correct). The emulator display went black (screencap returns a black frame) before the fold-across-screens check. |
| 4 | One Force reroute counted 2 (qa-emuA Q3) | Fixed in code, not checked on device | Cause: after the 8 m push the controller reroutes from the nearest node, but the simulated student walks the lead-in slower than the 3 s reroute cooldown and is still more than 6 m off the new route, so it reroutes again. Fix in `MainViewModel.debugPushOffRoute`: after the first reroute, jump the simulation onto point 0 of the new route. The real fix for real walkers belongs in `guidance/GuidanceController` (for example no new reroute until the student has been within 6 m of the fresh route once); owner of guidance/ should decide. |
| 5 | S1 flashes one frame before Explore as home (O6) | Fixed in code, not checked on device | `CampusMapsApp` draws a blank background instead of S1 until `exploreShouldBeHome()` answers or 300 ms pass (saved across recreation). Not verified with animations off: the emulator display stalled. |
| 6 | S3 "Seen:" keeps an old sign after a burst that finds nothing | Note only | In glasses/ (another owner): clear the seen sign when a burst returns no sign. |

Commits (in order): 74b0b15 cache, 064ddc6 + 0f66547 S1 map icon, 19b2dd6 (holds the 44 dp debug link rows; its message wrongly says map icon, not rewritten), 3c6c807 debug card, 2a29b60 reroute, d43730c home pick.

Files changed: `outdoor/DirectionsCache.kt` (new), `outdoor/ExploreViewModel.kt` (6 lines in `recompute`), `test/.../outdoor/DirectionsCacheTest.kt` (new), `ui/screens/DestinationScreen.kt`, `ui/components/Components.kt` and `ui/icons/AppIcons.kt` (additive `onMap` slot and map icon; outside my list, needed for the DEMO tag to fit), `ui/screens/DebugOverlay.kt`, `ui/CampusMapsApp.kt`, `ui/MainViewModel.kt`. `DirectionsClient.kt` untouched.

Also seen: the debug card is not drawn over the Explore map. To recheck on a healthy device: items 3, 4, 5.
