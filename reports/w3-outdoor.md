# W3 outdoor steps (branch w3/outdoor)

## What changed
- The Google walking steps shown on the Explore sheet now also reach S2. Each Google maneuver becomes its own step, then comes "Enter <building> at the <entrance>", then the indoor steps, unchanged. Without Directions (no key, offline, straight line) the single "Walk to <entrance>" step stays.
- Outdoor steps move forward by GPS, not by the fake walker. A step is done when a fresh FusedLocation fix (under 10 s old) is within 12 m of the step's end point. While a fix is fresh the walker cannot move an outdoor step. The debug Step, Walk and Skip controls still force the next step. The "Enter" step stays on screen for at least 3 s, because it ends at the same door as the last street step. After the last outdoor step the simulator is placed at the entrance, so the indoor steps carry on the same way as when the walker reaches the door.
- The banner, the "Then:" line, the watch and the spoken text all use the same step and the same GPS distance. The watch gets the maneuver's arrow (LEFT, RIGHT or STRAIGHT) with the street text as its label. The banner keeps the last GPS distance for up to 30 s, so a short gap in fixes does not make the number jump.
- The glasses (S3) read `bannerText` from the same controller, so they speak the street steps too. Nothing in glasses/ was changed.
- The WatchBridge log line now also shows the watch label: `Step sent to N watch(es): TYPE bigText / label`.

## Seen on the S25 (R5CY12PPNPY)
- First run, about 600 m from Classroom South: the Explore sheet listed 3 Google steps. S2's first banner read "Head west on John Wesley Dobbs Ave NE toward Courtland St NE, in 203 m", with "Then: Turn left onto Peachtree Center Ave NE" under it. The watch log read `STRAIGHT 203 m / Head west on ...`, so the text and distance matched the banner.
- I pressed the debug Step through each street step: `LEFT 626 m / Turn left onto Peachtree Center Ave NE` (the banner also said 626 m), then the Enter step. After that, the log said "entrance reached: indoor steps from E-CSM2", the chip changed to "Classroom South main (floor 2)", and the step became the indoor "Turn right at Hallway..." step.
- The continuous walker did not move the outdoor steps while GPS was fresh (step 1/11, along 0 m).
- Second run, after the phone had moved about 3 km away: Directions gave 18 steps. GPS completed "Head west" on its own at 11 m (`step 0 done (GPS 11 m)`). After that the banner showed "Turn left, in 105 m" and the watch log showed `LEFT 105 m / Turn left`.
- End route went back to the map both times, and the watch was cleared.
- The watch was offline over Wi-Fi ADB, so I checked it through the phone's WatchBridge log only. The wear app is unchanged.
- Screenshots (360 px) are in `reports/shots-w3-outdoor/`.

## What the owner must test on a real walk
- Street steps should move forward at each corner (12 m) while walking outside, and the distance should count down. If GPS is noisy, change `OutdoorGps.COMPLETE_M`.
- Near the door, the "Enter" step should show for about 3 s and then hand over to the indoor steps. Check that the AR, chip and minimap state is right after the hand-over. The simulator is placed at the entrance point.
- The watch face should show the LEFT/RIGHT arrows and wrap the long street labels.
- Spoken street steps on S2. They are spoken once the GPS distance is live, even when the AR pose confidence is low.
- Known limits:
  - A route started from S1b's fixed outdoor start points keeps today's behaviour: one "Walk to" step with the old entrance-distance override on the banner.
  - If the S2 entrance differs from the map's entrance, no street steps are used (this is logged).
  - On the Enter step, the watch label is the door name, not the full sentence.

## Files
- New: `app/src/main/java/com/campusmaps/outdoor/StreetSteps.kt`, `app/src/main/java/com/campusmaps/guidance/OutdoorGps.kt`, and tests `app/src/test/java/com/campusmaps/outdoor/StreetStepsTest.kt` (5) and `app/src/test/java/com/campusmaps/guidance/OutdoorGpsTest.kt` (6).
- Changed:
  - `outdoor/OutdoorRoute.kt`: `StreetStep`, `StreetTurn`, `OutdoorRoute.streetLegs`.
  - `outdoor/DirectionsClient.kt`: additive only. New `STEP_FIELDS` in the field mask, new `parseRoutesStreetSteps` and `parseLegacyStreetSteps`, and one `streetLegs =` argument in each parser.
  - `route/RouteModels.kt`: `STREET_LEFT`, `STREET_RIGHT`, `STREET_STRAIGHT`, `RouteStep.outdoorEnd`.
  - `guidance/GuidanceEngine.kt`: watch arrows for the street kinds.
  - `guidance/GuidanceController.kt`: `onFix`, GPS step rule, shared distance, `gpsDistance`.
  - `ui/MainViewModel.kt`: builds the street steps in `startFromExplore`, feeds FusedLocation fixes, and seeds the map's fix.
  - `ui/CampusMapsApp.kt`: passes the map's steps and entrance.
  - `ui/screens/GuidanceScreen.kt`: CompactBanner icon per step, "Then:" line, and the override only for S1b routes.
  - `ui/icons/AppIcons.kt` and `platform/WatchBridge.kt`: log label.
- Build `:app:assembleDebug :app:testDebugUnitTest :shared:test :wear:assembleDebug`: green.
