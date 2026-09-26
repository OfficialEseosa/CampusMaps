# 03. Localization (where am I?)

**Owner:** Device Lead runs it, Claude writes it. **Depends on:** [02-building-data.md](02-building-data.md). **Consumed by:** routing (node), AR guidance (pose), glasses bridge and watch (node). **Research:** [research/arcore.md](research/arcore.md), [research/mlkit-barometer-wear.md](research/mlkit-barometer-wear.md).

Localization answers two questions with different precision:
1. **Which node am I at?** Needed by routing, the glasses, and the watch. A node id and a floor.
2. **Where exactly is the building frame in ARCore's world?** Needed only by phone AR so arrows land on the real floor. A rigid transform.

Three sensors feed it. The estimate is one object, `LocalizationState`, published by a `Localizer` and observed by the view model.

```
LocalizationState(
  node: NodeId?,            // best-known node
  floor: Int?,              // best-known floor
  buildingToWorld: Pose?,   // building frame -> ARCore world, null if unknown
  confidence: Float,        // 0..1, decays with time since last anchor
  lastAnchor: AnchorId?, lastAnchorAt: Instant?,
  source: IMAGE | TEXT | DEAD_RECKONING | BAROMETER | GEOSPATIAL | NONE
)
```

## 1. Phone path: ARCore Augmented Images (gives node and pose)

**How it works.** Every image anchor's reference photo goes into an ARCore image database (`.imgdb`) built offline with `arcoreimg.exe` from the anchor log, with the measured physical width. When the camera sees one, ARCore returns the image's centre pose in world coordinates. We know the same image's position and wall direction in building coordinates, so one detection fixes the building-to-world transform.

**Constraints that drive the data capture** (verified in research):
- Reference image at least 300×300 px, greyscale is all that is used.
- `arcoreimg eval-img` score must be 75 or higher. Score every photo before the event.
- The image must fill about 25% of the camera frame to be *detected* the first time. A 90 cm directory board is detectable from about 2 m on a phone; a 15 cm plaque needs the phone about 40 cm away. That is why small plaques are text anchors, not image anchors.
- Up to 20 images tracked at once, 1,000 per database, one database per session. We will have under 40 per building. Fine.
- Glossy, glass-covered, and repetitive signs fail. Two signs that differ only by a small room number collide.

**Snapping to the building frame** (the yaw-only method, since both frames are gravity-aligned):
1. On detection with `trackingState == TRACKING`, read `centerPose`. The image's +Y axis is the wall normal, +X runs left to right across the image, +Z runs *down* the image. Do not reuse the image pose for floor content; only take its position and its horizontal normal.
2. `yawWorld = atan2(n.x, n.z)` where `n` is the world-space normal. `yawBuilding` is the anchor's `facing` from the building file. `theta = yawWorld - yawBuilding`.
3. `buildingToWorld = translate(pWorld - Ry(theta) * pBuilding) * Ry(theta)`, horizontal components only. Floor height in world comes from the anchor's `heightM` above the floor (`pWorld.y - heightM`) or from a detected horizontal plane, whichever is available.
4. Set `node` to the anchor's node, `floor` to the anchor's floor, `confidence` to 1.0, `source = IMAGE`.
5. Create an ARCore anchor via `augmentedImage.createAnchor(pose)` (not a bare session anchor) so content inherits ARCore's later corrections to that image.

**Between anchors.** ARCore motion tracking carries the pose forward. Research numbers: tens of centimetres and 1 to 3 degrees of yaw drift over 10 to 20 m of corridor; worse across a turn or past a blank wall. 2 degrees over 20 m is about 0.7 m of sideways arrow error. That is why anchors are spaced at most 15 to 20 m apart, with one at every decision point (junction, stairwell, elevator lobby). Node estimation between anchors: project the current camera position into building coordinates and pick the nearest node on the current route within 3 m; otherwise keep the last node. Confidence decays linearly to 0.3 over 20 m of travel.

**Re-snap on every detection.** Recompute `buildingToWorld`. Interpolate the correction over about 300 ms so the ribbon slides instead of teleporting. Reject a correction whose yaw disagrees with the current estimate by more than 20 degrees while confidence is still above 0.6 (likely a misdetection); accept it if confidence is low.

**Lost tracking.** If ARCore reports `PAUSED` for more than 2 s, set `buildingToWorld = null`, keep `node`, and show "Point the camera at a sign" in the UI. Instructions on the glasses and watch keep working from `node`.

## 2. Glasses and phone-fallback path: text recognition (gives node only)

**How it works.** Frames from the glasses (or the phone camera in fallback) are sampled at a few per second, run through ML Kit Text Recognition (bundled model), and every recognized element is matched against the building's text anchors. A match sets `node` and `floor`, `source = TEXT`, and leaves `buildingToWorld` alone.

**Pipeline** (each step is cheap and each one matters):
1. **Frame gate.** At most 5 to 8 frames per second into OCR; drop frames while one is processing. Crop to the centre 50 to 60% of the frame; halves latency and removes distractor text.
2. **Rotation.** `InputImage` rotation must make the image upright. Wrong rotation gives silent empty results. Test in the first hour.
3. **Candidate filter.** Keep elements whose bounding box is at least 20 px high and whose text matches `^([A-Z]{1,4}\s?-?)?\d{3,4}[A-Za-z]?$` after uppercasing and stripping spaces and hyphens. Apply the OCR confusion map (`O→0, I/l→1, S→5, B→8, Z→2`) before matching.
4. **Fuzzy match** against the building's text anchors and their `aliases`: Levenshtein distance at most 1 for 3-digit tokens, at most 2 for longer tokens. Prefer exact matches.
5. **Vote.** Ring buffer of the last 5 frames. Accept a node only when the same node wins at least 3 of 5. Then lock out re-announcing the same node for 3 s. "312" appears on door plates, fire maps, and directories; a single frame lies.
6. **Sanity.** Reject a node more than 40 m from the current node estimate unless confidence is under 0.4.

**Glasses stream reality** (from [research/meta-glasses.md](research/meta-glasses.md)): about 10 fps at about 650 Kbps on Android, and audio playback is blocked while streaming. Characters must be at least 16 px tall for ML Kit. At 504×896 a 5 cm plaque digit at 1.5 m is roughly 12 to 15 px, which is marginal. Two responses:
- Use `capturePhoto()` (full-resolution still) for the OCR read when a sign is expected, rather than the video frames. Stills are only available while streaming, so the bridge runs in bursts.
- Prefer **large** text anchors on the Demo C route: directional signs, floor numbers on the wall, big room-number banners. Measure the text height on the glasses point-of-view footage this week.

**Plan B recognizer.** If OCR on the glasses is not reliable enough, a MediaPipe Image Embedder (MobileNet-V3) with nearest-neighbour cosine matching against the anchor photos recognizes whole posters with no training, in 2 to 3 hours of work. Same voting logic. It gives identity, not pose, which is all the glasses path needs. Decide by Hour 16.

## 3. Barometer: floor changes (adjusts floor only)

In the primary plan (decided 2026-09-20): the Demo B route to room 608 rides the elevator, and the floor number counting up on screen and on the watch during the ride is the visual. The elevator is the easy case for a barometer: fast, monotonic, no step noise. Relative pressure only.

- Read `TYPE_PRESSURE` at `SENSOR_DELAY_UI`. Exponential moving average with alpha 0.15 (about 1 s window).
- **Reference.** Every anchor detection sets `refPressure = filtered` and `refFloor = anchor.floor`. This re-zeroes weather and HVAC drift every 15 to 20 m.
- **Per-floor delta** comes from the pre-event barometer test (expected 0.42 to 0.48 hPa for a 3.5 to 4 m storey; the file stores `floorHeightM` and we use 0.12 hPa per metre unless the test says otherwise).
- **Gate.** Only change `floor` while the current node is a stairs or elevator node, or within 10 s of leaving one. Elsewhere, ignore the barometer entirely; doors and HVAC produce 0.1 to 0.3 hPa steps that would otherwise look like a floor change.
- **Decision.** Continuously compute `floorEstimate = refFloor - (filtered - refPressure) / floorDelta` (pressure falls going up) and show it rounded, live, while at an elevator or stairs node; that is the "floor counting up" display. Commit `floor` when the rounded estimate has been stable for 1.5 s and the elevator ride has plausibly ended (pressure slope near zero). Snap the node to the matching elevator node on that floor. Speak and buzz "Floor 6".
- **Elevator-specific.** Set `refPressure` the moment the user is at the elevator node on the boarding floor (the last anchor before it, or the "boarding" instruction being reached). A six-floor ride is about 2.4 hPa over roughly 20 s; easy to see. Stairs are noisier; for the stairs alternative, commit only per landing.
- **Stairs versus elevator** is known from the route; we do not need to classify it from the signal.
- Watch barometer as a backup source is out of scope unless the phone sensor proves noisy.

## 4. Outdoor leg: ARCore Geospatial (Demo B only, in the primary plan)

Decided 2026-09-20: keep it, so that nothing outdoors has to be mapped by hand. The only outdoor data is one latitude, longitude, and heading per entrance in the building file. The user's outdoor position comes from Geospatial, not from our graph; routing treats "outside" as a virtual node connected to every entrance with an edge whose length is the straight-line distance from the user's current Geospatial position.

- Needs a Google Cloud project with the ARCore API enabled, an API key or keyless auth, `ACCESS_FINE_LOCATION`, `INTERNET`, and a network connection (cellular at GSU). Gate with `checkGeospatialModeSupported` and `checkVpsAvailability(lat, lng)` at the Classroom South start points **before the event** (a throwaway test app is fine).
- Place a Terrain anchor at each candidate entrance's latitude and longitude (from the building file). Draw arrows toward the chosen entrance only when `earth.trackingState == TRACKING` and horizontal accuracy is under 10 m and yaw accuracy under 15 degrees.
- At the entrance node, the first indoor image anchor takes over and `source` switches to `IMAGE`.
- Emergency cut only if Hour 18 to 20 runs long (see [12-risks.md](12-risks.md)). It is never used indoors; VPS has no coverage inside buildings.
- Needs a Google Cloud project with the ARCore API enabled and an API key restricted to the app's package and signing certificate. Someone on the team owns that project; see [15-open-questions.md](15-open-questions.md).

## 5. Replay tool (testing without travelling)

A debug-only mode that feeds a recorded walkthrough video, frame by frame, into the text-recognition pipeline (and, for glasses footage, into the Mock Device Kit's `setCameraFeed`). It prints every node decision with a timestamp. This is how we test Demo C's recognition logic in Klaus using the Student Center footage captured this week. ARCore itself cannot be replayed from a video, so the phone image path is tested live in Klaus.

## Work plan

| Hour | Task | Done when |
|---|---|---|
| Pre-event | Score every anchor photo with `arcoreimg eval-img`; build `KL.imgdb`, `CS.imgdb`, `CSE.imgdb` | All kept anchors score 75+ |
| 0 to 2 | ARCore session with the image database loaded, detections logged | Log shows the anchor name within 1 s at about 1.5 m |
| 6 to 10 | Yaw-only snap, node estimate, re-snap with smoothing, debug overlay | Pointing at an anchor shows the correct node; walking 20 m and re-detecting shows no visible jump |
| 14 to 18 | OCR pipeline with voting, replay tool, tested on the glasses footage | Replay of the Student Center video prints the right nodes in order |
| 18 to 20 | Barometer floor change gated to stairs and elevator nodes | Stairs in Klaus flips the floor once and only once |
| 18 to 20 (optional) | Geospatial outdoor leg | Arrows point to the right entrance on the Klaus lawn test |

## Tests in the first two hours (from the research, in priority order)

1. `arcoreimg eval-img` on two real Klaus anchors. Under 75 changes the whole marker plan.
2. Detect both from the prebuilt database on the S25 Ultra; log time to `TRACKING` at about 1.5 m.
3. Walk 20 m and back; measure how far an anchored object moved. That number confirms or tightens anchor spacing.
4. Detect anchor A, walk to B, detect B, confirm the re-snap is smooth.
5. OCR one plaque from the phone camera with correct rotation and see text in the log.
