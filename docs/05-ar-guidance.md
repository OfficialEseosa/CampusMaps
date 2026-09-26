# 05. AR Guidance (phone)

**Owner:** Claude writes, Device Lead runs. **Depends on:** localization (`buildingToWorld`), routing (node list). **Research:** [research/arcore.md](research/arcore.md).

## Decision: SceneView 4.38.0

Rendering library is **SceneView for Android** (`io.github.sceneview:arsceneview:4.38.0`), using its Compose `ARScene` composable. Reasons, from the research:
- It is the maintained successor to Sceneform (both Sceneform and its community fork are archived; ignore any `ArFragment` tutorial).
- Built-in ARCore session handling, install and permission prompts, augmented image support, GLB model loading, and optional depth occlusion.
- Drawing arrows becomes "load a GLB, parent it to an anchor node", which is hours of work rather than the full day raw OpenGL would cost.
- It pins ARCore 1.54.0. We take that transitively and do not force 1.56.0.
- Keep the AR screen a single top-level Compose destination; SceneView's Filament surface misbehaves inside heavily recomposing nav graphs.

Fallback if SceneView fails to build in Hour 0 to 2: the ARCore SDK's `hello_ar_kotlin` sample with its OpenGL renderer, drawing flat arrow quads. Uglier, but known to work. Decide by Hour 2.

## What we draw

1. **Path ribbon on the floor.** The route's node positions on the current floor, converted with `buildingToWorld`, offset to floor height, joined into a polyline. Rendered as a chain of flat arrow GLBs every 1.5 m, pointing along the path, or as a single ribbon mesh if that turns out easy. Arrows are simpler to get right and read well on video; start with arrows.
2. **Next-turn arrow.** A larger arrow at the next decision node, rotated to the turn direction.
3. **Destination marker.** A floating pin or ring at the room's door position, 1.5 m above the floor, facing the camera (billboard), with the room name as a text label.
4. **Fade beyond 15 m** so the ribbon does not visibly cross walls the user cannot see through. Depth occlusion is off by default; it is a one-flag experiment for the last two hours if everything else works.

## Anchoring strategy

- All content is parented to **one root node** whose pose is `buildingToWorld`. Re-snapping updates that one pose; nothing else moves.
- The root is attached to the ARCore anchor created from the most recent augmented image detection so it inherits ARCore's own corrections.
- When the user changes floor, rebuild the ribbon for the new floor from the same root (floor height differs by `floorHeightM`).
- When `buildingToWorld` is null (tracking lost), hide the content and show the "Point at a sign" prompt. Keep the minimap visible.

## Smoothing

- On a re-snap, interpolate the root pose from old to new over 300 ms. Users do not notice a slow slide; they notice a jump.
- Do not animate the destination marker; it is far away and should feel fixed.

## Minimap (2D backup)

- A `Canvas` composable in the corner: the current floor's nodes and edges in building coordinates, the route in colour, the user as a dot with a heading line (heading from ARCore camera yaw transformed into building yaw).
- Works even when AR tracking is lost, since it only needs `node` and the last known building-space position.
- Tap to expand to half screen.

## Coordinate gotchas (do not skip)

- ARCore world: +Y up. Augmented image pose: +Y out of the image, +Z **down** the image. Never use the image's rotation directly for floor content.
- Building frame: x east-ish, y north-ish, floor as integer. Convert to world with `buildingToWorld` and set world y from floor height, not from building z.
- GLB arrows must be modelled pointing along +Z (or whatever we standardise) so the rotation code is one `lookAt`.
- Units are metres everywhere. A 30 cm arrow model exported in centimetres will be 30 m long.

## Assets

- `arrow.glb`: a flat chevron, 0.5 m long, 0.3 m wide, emissive colour so it reads in dim hallways. Make it in Blender or download a CC0 one.
- `pin.glb`: a simple ring or pin. Optional; a billboarded text label alone is acceptable.

## Work plan

| Hour | Task | Done when |
|---|---|---|
| 0 to 2 | `ARScene` renders the camera and one GLB anchored to a detected image | Version set compiles and runs on the S25 Ultra |
| 10 to 12 | Root node driven by `buildingToWorld`; ribbon of arrows for the current route | Arrows along the Klaus hallway from start point 1 |
| 12 to 14 | Destination marker, next-turn arrow, fade, re-snap smoothing, minimap | Arrows lead the right way from both Klaus start points, no jump on re-snap |
| 20 to 26 | Polish: colours, label font, lost-tracking prompt, demo mode | Demo A runs 5 times in a row |

## Cut order inside this module

Minimap, then destination marker label, then next-turn arrow. Never cut the ribbon of arrows; it is the demo.

## Built 2026-09-25

**Approach: SceneView 4.38.0 `ARScene` (the planned library).** It compiled against our version set first time and rendered on the S25 Ultra, so the raw-ARCore + Compose `Canvas` fallback (the teammate's `ArWorldOverlay`, a fixed pinhole camera, not ARCore) was not needed. His chevron geometry (0.3 m x 0.5 m) and the 10 m / 15 m fade numbers were reused.

**Files** (all self-contained; only `ArInputs.kt` knows our app types):

| File | What |
|---|---|
| `app/.../loc/BuildingToWorld.kt` | Pure-Kotlin yaw-only transform: `fromCorrespondence(worldPoint, worldDir, buildingPoint, buildingDir)`, `toWorld`, `toBuilding`, `buildingHeadingOf`, `lerp` (300 ms slide). JVM test `app/src/test/.../loc/BuildingToWorldTest.kt` (8 tests). |
| `app/.../ui/ar/RouteArrows.kt` | Pure layout: `RoutePoint`, `FloorArrow`, `Placement`, `ArDestination`, `ArRouteInput`; `chain()` (arrow every 1.5 m, spacing carried across corners, cleared around the turn arrow), `arrowAt()`, `placementAt()`. Test `RouteArrowsTest.kt`. |
| `app/.../ui/ar/ArInputs.kt` | `UiState.toArRouteInput()`: the only adapter to our state (skips the virtual OUTSIDE node, finds the next TURN instruction, the destination, and the node the user stands at). |
| `app/.../ui/ar/ArGuidanceView.kt` | `ArGuidanceView(input, buildingToWorld, onBuildingToWorld, onFail)`: ARScene, one root `Node` at `buildingToWorld`, chevrons as `ShapeNode` polygons, amber 2.2x next-turn chevron, red post + billboarded `TextNode` room label 1.5 m up, prompts, and the place flow. |

`UiState.buildingToWorld: BuildingToWorld?` and `NavigationViewModel.setBuildingToWorld(t)` are the shared seam (additive only). The AR view clears it on dispose because the pose only means something inside one ARCore session.

**What works on the phone:**
- The ARScene camera feed, with plane dots shown until the route is placed.
- The prompts. "Point the camera at the floor or a sign (too dark / moving too fast / ...)" shows whenever the camera is not TRACKING, and the arrows are hidden meanwhile. "Move the phone slowly over the floor" shows until the first upward plane is found.
- The chevron chain, verified on device with a forced transform: flat, pointing along the route, dim between 10 and 15 m, hidden beyond 15 m.
- The fallbacks are unchanged: permission denied, ARCore unsupported (the emulator panel), and session failure.

**Debug place-route flow:**
1. Stand at the current node (the one the debug overlay shows, or the entrance for an outdoor start).
2. Face along the route.
3. Tap **Place route here**.
4. Tap the floor just in front of you.

The app then hit-tests an upward plane under the tap and reads the phone's horizontal heading (-Z of `displayOrientedPose`, or screen-up if the phone points straight down). It calls `fromCorrespondence(hit, heading, node, nextEdgeDir)`, sets the floor height from the hit, and creates an ARCore anchor at the hit. The transform follows that anchor's translation corrections, checked once a second when the anchor moves more than 3 cm. **Re-place route** redoes it, with the 300 ms slide. Fake-walk steps move the minimap dot and the instruction but not the arrows: the chain is laid along the whole route on the current floor, independent of progress. Only the big turn arrow moves on to the next turn.

**Stubbed:** the sign-based snap. The real `ArCoreLocalizer` should call `BuildingToWorld.fromCorrespondence(imageCentre, imageNormal.xz, anchor.x/y, facingDir, floor)` and then `vm.setBuildingToWorld(...)`. The AR layer does not change. The 20 degree misdetection gate and "null after 2 s PAUSED" belong in that localizer.

**Gotchas hit:**
- **Axes.** Building (x east, y north) maps to local `(x, h, -y)`, so north is -Z. Ry(theta) turns +Z toward +X, which matches SceneView's `rotation = Float3(0, deg, 0)`. The chevron is modelled in ShapeNode's XY plane pointing +Y, and `rotation = (-90, 0, 0)` lays it face-up pointing local -Z. Its yaw is `atan2(-dirX, dirY)`. Nest one rotation per node level so Euler order never matters.
- **Plane detection time.** A bare desk or dark floor gives no plane and TRACKING never starts ("too dark" with the phone lying on a table). Textured floors take 1 to 3 s of slow sweeping.
- **GLB units.** No GLB was needed. `ShapeNode` takes a metre-scale polygon, which removes the centimetre-export risk.
- **Filament materials.** `materialLoader.createUnlitColorInstance(Color)` is used so the arrows look emissive in dim halls.
- **Gradle.** Concurrent Gradle builds from several agents corrupted `:core`'s incremental cache ("Unresolved reference Building"). `./gradlew :core:compileKotlin --rerun` fixes it.
- **API names.** SceneView 4.38 ships no sources jar. The parameter names came from `javap -l` LocalVariableTables: `ARScene(planeRenderer, sessionConfiguration, onSessionUpdated, onTrackingFailureChanged, onSessionFailed, content)`, `ShapeNode(polygonPath, materialInstance, position, rotation)`, `TextNode(text, fontSize, textColor, backgroundColor, widthMeters, heightMeters, position, cameraPositionProvider)`.

**Not verified on the real floor yet:** the tap-place and 2 to 3 m walk test needs someone holding the phone. The device was lying face-up on the desk for this session. Run it by hand: S2, point at the floor, Place route here, tap, then walk.

### Porting to the teammate's base (integration branch)

1. Copy `loc/BuildingToWorld.kt`, `ui/ar/RouteArrows.kt` and `ui/ar/ArGuidanceView.kt` unchanged (change only the package lines if you move them). Do **not** copy `ArInputs.kt`; write his adapter instead. Copy the two tests too.
2. Gradle: add `io.github.sceneview:arsceneview:4.38.0`. He already has `com.google.ar:core:1.56.0`, and Gradle resolves the pair to 1.56.0, which is fine. SceneView pulls Filament and kotlin-math (`dev.romainguy.kotlin.math`). Keep `compileSdk` at 36 or higher, and confirm his Compose BOM (2026.04.01) builds with it.
3. Adapter from his `GuidanceState`:
   - `points = route.points.map { RoutePoint(it.position.x, -it.position.y, it.floor) }`. **His plan is y-down (heading PI/2 = south), ours is y-up, so negate y.**
   - `floor = state.floor`
   - `nextTurn = RouteArrows.arrowAt(points, step.startIndex)` when `step.kind` is TURN_LEFT, TURN_RIGHT or TURN_AROUND
   - `destination = ArDestination(destination.name, destination.position.x, -destination.position.y, destination.floor)`
   - `placement = RouteArrows.placementAt(points, progress.segmentIndex, "here")`
   - `floorHeightM` from his building
4. Hold `buildingToWorld` in his controller or ViewModel (a `MutableStateFlow<BuildingToWorld?>`). Put `ArGuidanceView(...)` where `CameraPreview()` + `ArWorldOverlay` sit, guarded by his ARCore availability check and the camera permission, and keep `ArWorldOverlay` as the no-ARCore fallback.
5. Later, his `PositionProvider` can read the camera pose: `toBuilding(cameraWorld)` gives plan metres, and `buildingHeadingOf(forward.x, forward.z)` gives the heading. Negate y and the heading sign for his y-down plan.
