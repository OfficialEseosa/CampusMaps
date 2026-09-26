# W3: in-app building editor

Branch `w3/editor`. Fix or extend a building (for example Classroom South) on the phone without walking it again, then put the result back in git.

## Using it in the field
1. Open Settings (gear, top right). Turn **Demo mode off**; the editor entry is hidden in demo mode.
2. Under "Demo building" pick the building (for example Classroom South), then tap **Edit this building**.
3. Pick the floor with the **Floor** chips. Pinch to zoom, drag with one finger to move the plan. Every point shows its id (H6, R-608, EL-6...).
4. To add a room: tap the **Add room** chip, tap the plan where the room door is, type the name ("Room 612"), tap **Add**. The id comes from the number (R-612). **Add hallway point** gives H12, H13...; **Add door** gives D1, D2... (name optional).
5. To link two points: tap **Connect** (scroll the chip row left if you cannot see it), tap the first point, then the second. The link length is the straight line in metres. A link that touches a door is a door link.
6. To change a point: tap **Select**, tap the point. The card at the bottom lets you rename it, nudge it 1 m west, east, north or south, tap **Move** then tap the plan to put it there, change its floor with - and +, remove one of its links, or **Delete** it (asks first).
7. **Undo** (curved arrow) takes back the last change. **Save** (disk icon) keeps the changes on the phone. The line under the title shows "N errors, M warnings" from the building checker; tap **Show** to read them. Nothing is blocked.
8. Routing uses the saved change straight away, no restart: search "612" on the start screen and route to it.
9. Leaving with unsaved changes asks Save or Discard. The three-dot menu has **Export merged file** and **Reset patch** (throws away every phone edit for that building).

## Getting the changes back into git
1. In the editor, three-dot menu > **Export merged file**. This saves first, then:
   - opens the Android share sheet with `CS.json` (the building file with your edits in it): send it to yourself (Drive, Gmail, Quick Share);
   - puts the small patch (only the changes) on the clipboard;
   - also writes the file to the phone at `Android/data/com.campusmaps/files/exports/CS.json`.
2. On the laptop, easiest is adb:
   `adb pull /sdcard/Android/data/com.campusmaps/files/exports/CS.json app/src/main/assets/buildings/CS.json`
   (or save the shared file over `app/src/main/assets/buildings/CS.json`).
3. Check it: `./gradlew :core:test` (all building tests), and optionally
   `$env:EXPORTED_BUILDING="C:\path\CS.json"; ./gradlew :core:test --tests "*BuildingPatchTest*" --rerun-tasks -i` prints its node, edge, error and warning counts.
4. `git diff app/src/main/assets/buildings/CS.json` shows only the new or changed entries (untouched entries are kept byte for byte). Commit it, for example `git commit -m "data: CS Room 612 added on the phone"`.
5. After installing the new build, open the editor and use **Reset patch** (or leave it: an old patch that no longer fits, for example it adds a room that the file now has, is logged and skipped at load time, so the file wins).

## What was verified on emulator-5558
- Editor opens for CS, 0 errors, 0 warnings. Floor 6, Add room, tap east of H6, "Room 612" gives id R-612; Connect H6 to R-612 (3.57 m); Save: "Saved (1 added, 1 links added). Routing uses it now. Validator: 0 errors, 0 warnings".
- Without restarting: start screen, search "612", Room 612 (Floor 6), start Outside Decatur St side (P1): Route options show 3 routes (fastest 1:19 via Library South entrance and the elevator).
- Export: share sheet "Sharing 1 file"; the pulled file differs from the asset only by the 17 added lines; loaded with core's loader in a JVM test: 27 nodes, 31 edges, 0 errors, 0 warnings.
- Screenshots: `reports/shots-w3-editor/` (1 to 6).
- Build: `./gradlew :core:test :app:assembleDebug :app:testDebugUnitTest` passes. New JVM tests in `core/src/test/kotlin/com/campusmaps/data/BuildingPatchTest.kt`: id rule, add plus connect plus JSON round trip plus route P1 to R-612, change, move, rename, remove, bad patches skipped, merged file equals the asset text for an empty patch.

## How it works
- Patch file on the phone: `files/patches/<code>.json`, format `{building, addedNodes, changedNodes, removedNodes, addedEdges, removedEdges, note}` in core coordinates (y north). The editor works on a copy of the building and saves the difference with the asset file (`BuildingPatch.diff`).
- `CoreBridge.load` applies the stored patch right after parsing each asset, then the validator runs on the merged building as before (debug builds log it). A patch that does not fit is logged under the `BuildingEditor` tag and skipped; the app never crashes on it.
- Save calls `AppContainer.reloadBuildings()`; closing the editor clears the start-screen selection so it re-reads the building.

## Not supported (yet)
- Adding stairs, elevator or entrance points, and stairs or elevator links between floors from the screen (core `BuildingEdits.connect` supports vertical links, but the screen shows one floor at a time). Edit those in the JSON.
- Anchors (signs, photos), access hours, door facing, lat/lng and start points are not editable. Deleting a point also deletes its anchors and its demo-destination entry.
- Drag-to-move with a finger: use Move (tap the new spot) or the 1 m nudges.
- Moving a point recomputes the length only of links added in the editor; surveyed lengths stay as they are.
- Link lengths are straight lines (marked `estimated`), not walked.
- Export needs a debug build for `run-as`; the `Android/data/.../exports` copy works with plain `adb pull`.

## Files
- New: `core/src/main/kotlin/com/campusmaps/data/BuildingPatch.kt` (patch model, apply, diff, merged-file writer, edit steps and id rule), `core/src/test/kotlin/com/campusmaps/data/BuildingPatchTest.kt`, `app/src/main/java/com/campusmaps/editor/PatchStore.kt`, `EditorViewModel.kt`, `BuildingEditorScreen.kt`.
- Changed (small): `data/campus/CoreBridge.kt` (apply patch in `load`), `AppContainer.kt` (`buildings` and `loadProblems` now reloadable, `reloadBuildings()`), `ui/MainViewModel.kt` (`Screen.EDITOR`, `buildingEditor(open)`, one `back()` line), `ui/CampusMapsApp.kt` (one screen branch, one Settings action), `ui/screens/SettingsSheet.kt` (optional `onEditBuilding`, "Edit this building" row), `res/xml/file_paths.xml` (cache `exports/`).
- Merge note: `Screen` gained `EDITOR`; any other branch that adds an exhaustive `when (screen)` needs that branch too.
