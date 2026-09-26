# W4 design consistency

Branch `w4/design`, emulator-5554 (API 36, 1080 x 2400, 411 dp by default, 360 dp with `wm density 480`).
Build and unit tests green before every install (`:app:assembleDebug :app:testDebugUnitTest`).
Screenshots: `reports/shots-w4-design/` (360 px wide).

## What changed

| Screen | Georgia Tech (gold/navy) | Georgia State (blue/white) | Commit |
|---|---|---|---|
| Splash | consistent (Ink + Clay brand, before a campus is picked) | same | none |
| S0 campus picker | consistent | consistent | none |
| S0b building picker | consistent; "1 rooms" fixed to "1 room" | consistent; status bar icons dark on blue header (logic, see below) | `ui: 1 room, not 1 rooms` |
| Explore map + sheet | was all clay; now campus skin: accent chips, AR button and Start pill (`PrimaryPillButton`), tiles in the S1b card style (card fill, cardBorder, 18 dp), line-coloured step numbers | picking CS / CSE now shows the Georgia State skin even when the app campus is GT | `ui: Explore map and sheet on the campus skin...` |
| Explore map drawing | route line = campus accent, entrance pin = campus deep (was clay) | same, blue line vs blue dot still distinct | `ui: outdoor route line...` |
| Explore layout | AR button was hidden behind a tall sheet; now placed from the measured sheet height. Sheet capped at half the screen so the round buttons never cover the room chips at 360 dp | same | `...AR button above the sheet`, `...at most half the screen` |
| "Almost there" card | clay card -> campus accent card, onAccent text | same | `ui: Almost there card uses the campus accent` |
| S1 | "Add a shortcut" / "See the map" icons were gold on cream (unreadable); now navy | blue | `ui: readable text buttons...` |
| S1b (cards, 3 modes, XR preview) | consistent | consistent | none |
| Settings | "Reset demo" / "Edit this building" were gold text on cream; now line colour (navy / blue), light clay over S2 | consistent | same commit |
| S4 Add a shortcut | "Stop recording", check icon, outlined button, photo tile were gold on cream; now line colour | consistent | same commit |
| Building editor | clay chips and Save tint -> campus line; Add button and dialog actions navy/blue, not gold; node title and small buttons used Ink #313131 (invisible in dark) -> onSurface; small buttons 36 -> 48 dp; plan canvas on the campus card colour (was cream on GSU cool white) | same; dark follows system with the clay dark scheme (existing decision) | `ui: building editor...`, `ui: editor plan canvas...` |
| S2 fallback (banner, fallback note, minimap, floor badge) | dark in both skins, light status icons, consistent with ArOverlayColors | same | none |
| S3 "Speech:" line | consistent (Sora, glassesMuted / arrived green) | same | none (code read, not seen on device) |

Before / after pairs: `05` vs `06` (Explore GT), `07` vs `08` (CS picked from GT), `09` vs `10` (360 dp overlap), `15` (editor cream canvas, before) vs the canvas commit.

## Needs a logic change (not mine to edit)

1. `app/src/main/java/com/campusmaps/ui/CampusMapsApp.kt:116`: S0b on Georgia State has a blue header under dark status bar icons. `screenDark` for `Screen.BUILDINGS` should be `campusId == CampusId.GSU` (white header text) so the icons turn light.
2. `CampusMapsApp.kt:99-100`: the skin comes from `vm.campus`, not from the room. Explore now skins itself per picked room, but the "Almost there" card and S2 after a CS pick from a GT start stay GT. Setting the campus in MainViewModel when a room of the other campus is picked would fix it everywhere.
3. Explore street steps read "Head east Take the stairs" (no full stop between the two parts); the text is built in `outdoor/` (ExploreViewModel / OutdoorRoutes), not in the screen.
4. `res/values-night/colors.xml` window_background is dark while the whole light flow is forced light; harmless now (screens paint their own background), left as is.

## Not verified on the emulator (time-box)

S1b 3D preview, map-only mode, S2 progress pill and arrived state, S3 (glasses mode), the "Almost there" card on device (needs a 40 m trigger), debug card, S4 and the node card in the editor. The TrackingChip on S2 lives in `ui/ar/OutdoorArrows.kt` (not in my scope) and uses the Clay dot; fine on the dark screen. No leftover Material purple found: every scheme sets all roles. Sora is the only family in the screens I touched.
