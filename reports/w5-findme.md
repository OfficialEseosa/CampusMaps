# W5 Find me: the phone reads the sign to know where you are

Branch `w5/findme`. Not merged, not pushed.

## How it works, in plain words

- On S1 there is a small **Find me** button (camera icon) next to "Where are you?". The picker below it stays as the manual override.
- Tap it and a full-screen camera sheet opens: "Point at a room number or a sign". If the camera is not allowed, a "Camera needed" card with an "Allow camera" button shows instead (no crash).
- Every 700 ms the sheet takes the latest camera frame, reads its text with ML Kit (the same `SignReader` the glasses use) on a background thread, and shows what it read under the hint ("Seen: 1116W SEMINAR ROOM WEST").
- The frame is scaled up 2x before reading. `SignReader` drops lines under 20 px tall (tuned for big signs in glasses stills); a door plaque's room number is smaller than that in a phone frame. Without the 2x step the 1116W plaque photo read as nothing. `SignReader` itself is not changed.
- There are two ways it can recognise where you are, both limited to the building open on S1:
  1. **Room number**: if one frame has a whole word equal to a room number in this building ("1116W", "608", or "1116 W"), that is a match straight away. It works even when no anchor lists the room. The start is that room's node. Room numbers come from the room ids and names. Numbers shorter than 3 characters are ignored, and "1116" does not match "1116W".
  2. **Sign text**: the glasses' `SignVoter.vote` runs over the last 3 frames, and at least 2 of them must agree on the same sign ("RESEARCH WING" gives E-RWD, "LIBRARY SOUTH" gives E-LM2).
- On a match the sheet shows the place name with a tick and a **Use this** button. After 1.5 s it accepts by itself: the sheet closes, "Where are you?" is set to that node, and a snackbar says "You are at Room 1116W". A room node has its own floor, so the start floor is set with it.
- If nothing matches after 20 s, the sheet says "No known sign here. Try another sign or pick your start below." and keeps looking. Cancel, the X, or Back closes the sheet.

## What was verified on the emulator (emulator-5556)

- **JVM tests** (`FindMeVoterTest`, 9 tests, all pass), run with the normal `:app:testDebugUnitTest` build:
  - the room-number rule: one frame is enough, it works without an anchor, the number must be a whole word, "1116 W" and "6O8" are handled
  - the 2-of-3 rule: one frame is not enough, 2 of 3 with a blank frame in between is enough, and old frames drop out of the window
- **Tests on the emulator with real ML Kit** (`FindMePipelineTest`, 4 of 4 pass). Each one reads one of the app's own photos:
  - `anchors/KL/KL-A01.jpg` (Research Wing sign) gives **E-RWD** on the 2nd frame
  - `anchors/KL/KL-A03.jpg` (1116W plaque) gives **R-1116W** on the 1st frame (by room number)
  - `glasses-replay/CS/CS-A05-straight.jpg` (Library South) gives **E-LM2** on the 2nd frame
  - `glasses-replay/CS/CS-A07-straight.jpg` (608) gives **R-608** on the 1st frame
- **Screen flow on the emulator** (`FindMeSheetShotsTest`, 3 of 3 pass): S1, then Find me, then the sheet, then a match, then S1 shows "Inside: Room 1116W, Floor 1". Also tested: the no-match message, and the "Camera needed" card with the permission denied. The emulator camera cannot see a real sign, so debug builds have a switch that makes the sheet read a photo from the app's files instead of the camera:
  `adb shell setprop debug.campusmaps.findme anchors/KL/KL-A03.jpg` (to turn it off: `adb shell setprop debug.campusmaps.findme "''"`).
- Screenshots at 360 px in `reports/shots-w5-findme/`:
  1. S1 with the Find me button
  2. the sheet with the hint
  3. the match (Room 1116W with "Use this")
  4. back on S1 with the snackbar, captured while it was still fading in
  5. no match
  6. camera needed

## What the owner should try on the phone

1. Install the build from this branch, open Klaus on S1 and tap **Find me** (allow the camera the first time).
2. Stand about an arm's length from the **1116W** plaque, the one next to the double doors. "Seen:" should show 1116W, and within about a second it should say Room 1116W and then set "Inside: Room 1116W, Floor 1".
3. Outside, point at the **KLAUS ADVANCED COMPUTING BUILDING / RESEARCH WING** sign. It needs 2 frames (about 1.5 s), then it should say "Research Wing door".
4. Point at something with no known sign for 20 s: the "No known sign here" message should appear. Cancel should go back to S1 with nothing changed.
5. If the match takes long, check Logcat tag `FindMe`: each frame logs what was read and what matched.

## Files changed

- New: `app/src/main/java/com/campusmaps/ui/findme/FindMeSheet.kt` (camera sheet, permission, states), `FindMeVoter.kt` (room-number and 2-of-3 rules, anchors and rooms of a building), `FindMeOcr.kt` (2x scale before `SignReader`)
- `app/src/main/java/com/campusmaps/glasses/SignVoter.kt`: one new pure function, `matchRoomNumber`. The existing code is unchanged.
- `app/src/main/java/com/campusmaps/ui/screens/DestinationScreen.kt`: Find me button, the sheet overlay, the snackbar, and `DestinationActions.onStartFromSign`
- `app/src/main/java/com/campusmaps/ui/MainViewModel.kt`: `startFromSign(nodeId)`
- `app/src/main/java/com/campusmaps/ui/CampusMapsApp.kt`: one line to connect `onStartFromSign = vm::startFromSign`
- Tests: `app/src/test/java/com/campusmaps/ui/findme/FindMeVoterTest.kt`, `app/src/androidTest/java/com/campusmaps/ui/findme/FindMePipelineTest.kt`, `FindMeSheetShotsTest.kt`

## Notes

- **emulator-5558**: my first `installDebug` ran before I pointed it at emulator-5556, so this debug build may have been installed on emulator-5558 at about 11:39. Whoever uses 5558 should reinstall their own build. The phone and emulator-5554 were not touched: I checked their install times.
- Reading a frame takes about 1 to 2 s on the emulator at 2x. The S25 should be much faster. Frames that arrive while one is still being read are dropped, so the 700 ms pace never builds a queue.
- `AppFlowTest` expects the app to open on the campus picker, but the app now opens on the Explore map. I did not change that test.
