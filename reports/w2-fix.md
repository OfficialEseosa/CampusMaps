# Wave 2 fixes (branch w2/fix)

Device: Galaxy S25 Ultra `R5CY12PPNPY`, lying on a desk indoors with a real fix about 530 m from Classroom South. Build: `./gradlew :app:assembleDebug :app:testDebugUnitTest` green. No `AndroidRuntime:E` lines. Screenshots (360 px) are in `reports/shots-w2-fix/`.

| # | Bug | Status | Commit | How it was checked |
|---|---|---|---|---|
| 1 | Explore entrance and S2 entrance disagree (qa-phone N2) | **Fixed** | `a7738df`, then `cb3e039` (moves the code into `ui/ExploreStart.kt`) | JVM tests `route/ExploreStartTest` on the real `CS.json`: a fix near Library South gives `E-LM2` on the map, as core's first S2 option and as the chosen option; a fix near Walters gives Walters; the S25's own desk fix gives the same entrance in both. On the phone: CS 608, Start AR navigation, and Logcat `Explore: S2 from GPS: map entrance E-CSM2, S2 entrance E-CSM2`. S2 says "Walk to Classroom South main (floor 2)", the entrance the map drew (`02`, `03`). |
| 2 | Explore opens on the campus centre, so the dot is off screen (N4) | **Fixed** | `466fb1d` | Cold start: the blue dot is on screen with no tap (`01`). The first fix frames the dot and the entrance once (or just the dot at zoom 17 when no room is picked). After that the camera belongs to the user. |
| 3 | "Locate me" blinks on S2 on the desk (N5) | **Fixed** (hysteresis) | `f0c24d1` | JVM test `guidance/LocateHysteresisTest`: shows after 2 s low, hides after 1 s good, and never blinks when the value flaps every 200 ms. Phone: a 10 s recording at 5 fps shows the card from about 2 s to about 6 s, with no blink, and a steady "Reading sign..." pill (`04`, `05`). |
| 4 | Meta AI stays inside the CampusMaps task (N7) | **Fixed** | `67d28d4` | `dumpsys activity activities`: Meta AI's `SilverstoneMainActivity` is now in task `A=com.campusmaps.glassessetup`, and the CampusMaps task holds only `MainActivity`. A launcher start (`monkey -c LAUNCHER`) shows the CampusMaps map (`06` to `08`). Nothing was granted or confirmed: the Nearby devices prompt was dismissed with Back, and the Meta AI screen was left with Home. |
| 5 | "Watch not reachable" logged about once a second (w1-fix) | **Fixed** | `ae9fd1e` | The line now logs at most once every 30 s, with a count of the lines it skipped. Not seen on the phone: the phone's watch was reachable, so no such line came up in this session. |

## Details

**1. How S2 now starts from the real fix.** `startFromExplore` no longer picks the nearest of P1 and P2. With a fix, the trip's `Selection` stores the fix (`gps`). `trip` then uses `CoreBridge.withGpsStart(building, lat, lng)`, which adds an OUTDOOR node `GPS` named "Your location" with `outdoorStarts[GPS] = (lat, lng)`. So `CoreRouter` routes it with `Start.Outside(lat, lng)`, exactly like the map. The option is chosen with `CoreRouter.optionForEntrance`, which matches the entrance id first and then core's `alsoVia` names. It falls back to the first option.

- **Where the node is drawn** (x/y, which is also where the simulated walker starts): the fix projected into building metres, then pulled in along the line to its nearest outdoor entrance so that it is at most 30 m outside that door. That is the same distance P1 and P2 sit from their doors. A fix 500 m away would otherwise shrink the S2 minimap to a dot.
- **Distances still use the real fix.** The distance and the banner ("in 596 m") come from haversine to the real fix.
- **S1 "Where are you?"** lists "Your location" first only while a trip started from Explore holds a fix. Picking another building or Reset clears it.

**3. Cause.** On this desk run the raw confidence crossed the threshold only twice: 0.30, then 0.92 after about 4 s. That is the simulator's scripted "start lost" window for outdoor starts. There was no AR fix (the camera is dark), so the pose came from the simulator the whole time. I could not reproduce the QA flicker in the raw signal. The hysteresis in `GuidanceController` covers both possible sources (AR confidence decaying around 0.5, or AR and the simulator switching), and so does the "Reading sign..." pill, which keys on `locating`. Each raw crossing is now logged at debug level: `Position: confidence X below/above 0.5 (source AR|simulator)`.

**4. How the fix works.** `GlassesSetupActivity` now has its own `taskAffinity` (`com.campusmaps.glassessetup`) and `excludeFromRecents`. The Meta AI SDK opens its registration screen with `startActivityForResult`, so adding `FLAG_ACTIVITY_NEW_TASK` would break the result. The Meta AI screen now lands in the helper's separate task instead.

## Notes for the merge

- The commits are built on `faaeab1`. `MainViewModel.kt` changes only 4 small places: `Selection.gps`, one line in `trip`, 2 lines in `startFromExplore`, and the option line plus a log line. The comment line above `startFromExplore` is untouched. It still says "nearest the student" and should read "from the phone's fix (ui/ExploreStart.kt)" after the cherry-pick. I did not touch `CampusMapsApp.kt`, the card, the re-arm logic or `ui/transition/`.
- I could not dry-run the cherry-pick onto `154694f` (a temporary worktree was not allowed). My hunks sit 2 or more unchanged lines away from that commit's `try {` / `finally` edits, so they should apply cleanly.

## Owner must check by hand

1. **Walk up to Classroom South from the Walters side.** Start AR from Explore about 100 m out. The S2 banner should name the same door as the map, and "Where are you?" on S1 should show "Your location".
2. **Explore with location off at start.** Turn location on after the map is up. The camera should move to the dot once and never again by itself.
3. **Cover the camera with the route placed (real AR).** "Locate me" should show after about 2 s and hide about 1 s after you uncover it, without blinking.
4. **Full glasses setup** (Allow Nearby devices, then the Meta AI Unverified-app toggle and Connect). Then tap the CampusMaps launcher icon: it must show CampusMaps. Also check that the Meta AI app still returns to CampusMaps through the `campusmaps://` link and that the camera permission step follows.
5. **Watch off or out of range during a route.** `adb logcat -s WatchBridge` should show "Watch not reachable" at most every 30 s.
6. **Monkey launch.** A `monkey` launcher start put a second `MainActivity` on the CampusMaps task. This is standard launch mode and was already there. Check whether a real launcher tap does the same.

## Device state left

- CampusMaps is on this branch's debug build, on Explore, with no route running.
- The Nearby devices permission was not granted, and the Meta AI connect screen was not confirmed.
- The helper task holding Meta AI may still be in the task list; `am stack remove` did not report success.
