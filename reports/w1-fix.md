# W1 fix agent report (branch w1/fix)

Build: `:app:assembleDebug :app:testDebugUnitTest :shared:test :wear:assembleDebug` green. App unit tests: 52, 0 failures.
Device: emulator-5554 only. Screenshots in `reports/shots-fix/`.

| # | Bug | Status | How verified |
|---|---|---|---|
| 1 | Watch clears on Done / Stop (WHATS-LEFT 2) | Fixed | `WatchClearOnce` gate: clear only when a route was running, once per route (Done used to clear twice: done() then reset()). `WatchClearOnceTest` (4 tests). Logcat line `WatchBridge: Route ended: clearing the watch` counted on the emulator: S3 Back to routes = 1, S2 Done = 1, S3 Done = 1. |
| 2 | Stairs-down icon on the watch (WHATS-LEFT 3) | Fixed (not seen on a watch) | Wear already mirrored the shape; a mirror still reads as "stairs". Added an up arrow (STAIRS) and a down arrow (STAIRS_DOWN) in the empty corner. `:wear:assembleDebug` builds; new preview `PreviewStairsDown`. Phone banner icon unchanged: `AppIcons.forStep` takes only the kind; the text says up/down. |
| 3 | S3 arrival buttons (WHATS-LEFT 4) | Fixed | After arrival S3 shows Done (to S1), Repeat and Back to routes (to S1b); Fake step is hidden. Driven on emulator, `4-S3-arrived-buttons.png`. |
| 4 | Shortcut persistence (docs/22 #9, #10) | Fixed | `FakeShortcutBackend.attachStore(file)` saves the review queue to `filesDir/fake-review-queue.json` (attached by `ShortcutRepository`). Dead seeded `cs` shortcut removed. `FakeShortcutBackendTest` (2 tests: survives a new instance, broken file ignored). Not tested with a real force-stop. |
| 5 | Repeated "elevator" difference text (#12) | Fixed | `RouteCardTextTest` (3 tests). Emulator CS P1 to 608, avoid stairs: "+54 s: 64 m more walking, rides 5 floors" (`1-S1b-elevator-difference.png`). |
| 6 | Skipped "Exit toward" step (#16) | Fixed in tests; not seen on emulator | Exit step: no 1.5 m slack, and at least 2.5 s on screen. The step right after an exit (the "Turn left at Elevator lobby" 1.5 m later) gets 1.5 s, otherwise it was skipped instead. Distance rules unchanged, so the walk never stalls. `GuidanceEngineTest`: the simulated walk now visits all 8 steps with the exit shown >= 2.5 s, plus 2 new tests on real CS P1 to 608 via EL-6. On the emulator my 3 s UI polling was too slow to catch the exit banner. |
| 7 | S4 leftover theme colours (WHATS-LEFT 5) | Already fixed in 0e62cfe | Code check: theme defines inverseSurface/inverseOnSurface for the snackbar; "Record the walk again" is already an outlined secondary. No change; not re-checked on screen. |
| 8a | Phase pill icons (#14) | Fixed | Looking = eye, Recognising = search, Speaking = speaker. `2-S3-phase-pill.png`, `4-...png`. |
| 8b | Debug card alpha and link rows (#15) | Fixed | Card background is opaque; LinkText is exactly 36 dp. `5-debug-card.png`. |

## Files changed
- `app/.../platform/WatchBridge.kt` (clear log, `WatchClearOnce`), `app/.../ui/MainViewModel.kt` (3 lines: gate field, `routeStarted()` in startSession, `routeEnded()` in stopSession)
- `app/.../ui/screens/GlassesScreen.kt`, `app/.../ui/screens/DebugOverlay.kt`
- `app/.../route/RouteCardText.kt`
- `app/.../guidance/GuidanceEngine.kt`, `app/.../guidance/GuidanceController.kt` (one line: passes `System.currentTimeMillis()` into `GuidanceEngine.update`)
- `app/.../data/shortcuts/ShortcutBackend.kt`, `ShortcutRepository.kt`
- `wear/.../WatchFace.kt`, `shared/.../WatchStep.kt` (comment)
- Tests: `RouteCardTextTest`, `WatchClearOnceTest`, `FakeShortcutBackendTest`, `GuidanceEngineTest`

## Owner must check by hand (real watch and phone)
- Galaxy Watch: after Done or Stop the face goes back to "Start a route on your phone" (logcat `WatchBridge: Watch clear sent to N watch(es)`).
- Galaxy Watch: stairs up vs stairs down arrows are easy to tell apart at arm's length.
- Approve a shortcut in S4 (debug Approve), force-stop, reopen, route: the shortcut should still be used.
- S2 walk through CS EL-6: "Exit toward Elevator lobby" should stay about 2.5 s, then "Turn left at Elevator lobby".

## For other agents
- `CampusMapsApp.kt` owner: `GlassesScreen` has two new optional params `onDone` / `onBackToRoutes`. When null it looks up the activity's `MainViewModel` itself (`viewModel()`), so nothing breaks. Please pass `onDone = vm::done, onBackToRoutes = vm::endGuidance` next time you edit that file.
- `GuidanceEngine.update` now takes an optional `nowMs` and `Progress` has `stepShownAtMs`. Callers that pass no time get the old distance-only behaviour, except the exit step now needs the student to actually reach its end (no 1.5 m slack).
- `CoreRouter` (not mine) sets the exit step's end at half the first leg (at most `SHORT_STEP_M`), which on CS is exactly where the following turn completes. That is why the step after an exit needs its own hold.
- Logcat noise: on a device without Wear APIs, `WatchBridge.send` logs "Watch not reachable" on every distance tick (about 1 per second). Worth rate-limiting.
