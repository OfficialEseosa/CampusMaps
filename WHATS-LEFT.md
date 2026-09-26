# CampusMaps — What's left

Status as of this build: the app compiles, installs, and runs on the emulator (Pixel 5
AVD, resized to the S25 Ultra's 412×915 dp). Every screen in the design handoff has a
Compose implementation styled from the handoff's tokens and has been driven live.
30 unit tests pass (4 test classes across `shared` and `app`), and all 7 instrumented UI
tests in `AppFlowTest` pass on the Pixel 5 emulator. The watch app runs on a Wear OS
emulator. Live screenshots are in `screenshots/` (phone) and `screenshots/wear/` (watch).

## Done and verified on-device

- **S1 Destination**: building selector, search, destination rows, start picker,
  avoid-stairs switch, "Add a shortcut" row.
- **S1b Route options**: locked-entrance "Heads up" banner, simulated-time chip, best
  card vs. other cards, "also via" folding, student-shortcut tag. Verified against the
  Sat 21:00 locked-Main-entrance scenario end to end.
- **S2 AR guidance**: camera background (painted stand-in when there's no real
  camera/ARCore), chevron chain, bent 3D turn arrow, minimap, floor badge, Locate-me,
  "AR unavailable" card, instruction banner with "Then:". Full simulated walk with AR
  both forced off and forced on.
- **Arrived state**: green banner, arrival pin (AR on). Done returns to S1, Back to
  routes returns to S1b.
- **S3 Glasses mode**: Looking/Recognising/Speaking cycle, fake steps, arrival, and the
  "Not connected" state.
- **S4 Add a shortcut**: From/To pickers, walk recorder, photo grid, submit, then
  "Pending review" pill and snackbar.
- **Settings sheet**.
- **Routing engine / data layer / debug overlay**: as before, all unit-tested.
- **Instrumented UI tests**: 7/7 pass. This needed espresso-core 3.7.0 added to the test
  dependencies; the 3.5.0 pulled in by Compose calls `InputManager.getInstance`, which
  newer Android releases removed.

## Watch app (Wear OS)

- Runs on the "Wear_Round" AVD (Wear OS 6.0, `android-36;android-wear-signed`).
- Verified: idle state, all 8 step types, a distance-only tick updating the face
  without buzzing, ambient mode (outline icons, updates and buzzes while dozing), steps
  received while backgrounded, and one vibration per step change matching `WatchHaptics`.
- Steps were injected through the watch's own Data Layer using the debug-only
  `DebugStepReceiver` (`wear/src/debug/`), so they went through the real
  `StepListenerService` and `WatchProtocol.decode`:
  `adb -s emulator-5556 shell am broadcast -n com.campusmaps/com.campusmaps.wear.DebugStepReceiver --es type LEFT --es big "'10 m'" --es label "'Atrium north'" --ez datalayer true`
- **Still unverified**: real phone-to-watch delivery (`WatchBridge` finding the watch
  node), a cold start triggered only by the Data Layer, and physical vibration strength.
  These need a real paired phone and watch.

## Open issues found during live testing

1. **Fixed at integration.** S4 refuses From/To on different floors and `CoreRouter` drops cross-floor shortcuts.
   Was: **Shortcut floor validation**: an approved shortcut is trusted even when its floors
   don't match. A 24 m walk recorded on floor 1 from Room 150 (floor 1) to Room 608
   (floor 6) became the best route, "same floor, 1:20". The simulated walk recorder also
   never changes floor. This needs validation on submission or in the router.
2. (Handled in code: `MainViewModel.stopSession` calls `WatchBridge.clear()`; not re-verified on a real watch.)
   **Watch never learns the route ended**: it keeps showing the last step after Done or
   Stop.
3. **Stairs down on the watch** uses the same up-staircase icon as stairs up.
4. **S3 arrival** keeps Repeat/Stop/"Fake step" and has no Done/Back to routes. The
   handoff only specifies these buttons for S2; this is a design decision.
5. **S4 leftover theme colors**: the snackbar and the "Record the walk again" button use
   M3 default purple-grey (the theme doesn't define them), and that button is a full
   primary button competing with Submit.
6. **Simulated glasses** never send a still, so the S3 thumbnail always reads "Waiting for
   the first still".

## Explicitly out of scope for now (per the handoff, section 15)

Onboarding/permission explainers, full accounts (device-ID stand-in is used instead),
deeper settings, outdoor Geospatial screens, the replay tool, multi-building search.

## Integration points left for you

- ~~Google Maps API key~~: removed at integration (owner decision; the outdoor leg is ARCore Geospatial, docs/05).
- **Real ARCore / camera pose**: implement `PositionProvider` (only
  `SimulatedPositionProvider` exists).
- **Real glasses SDK**: implement `GlassesLink` (only `SimulatedGlassesLink` exists).
- **Real shortcut backend**: implement `ShortcutBackend` (only `FakeShortcutBackend`).
- **Launcher icon**: still a vector placeholder.

## Suggested next steps

1. Fix open issues 1, 2, 3 and 5 (code bugs); decide on 4.
2. (Dropped: Google Maps outdoor renderer removed.)
3. Pair a real phone and watch to confirm end-to-end step delivery.
