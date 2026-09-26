# 16. CampusSurvey: the data collection app

**Status:** 0.2 built 2026-09-24 (geometry capture, on-screen coaching, step-by-step tutorial); 0.1 built and smoke-tested 2026-09-21. **Purpose:** replace the notebook, the spreadsheet, and the default camera app during the site visits. **Not part of the CampusMaps submission.**

## Version 0.2 (2026-09-24)

A review before the site visits found the 0.1 log couldn't produce node positions or a trustworthy anchor `facing`. 0.2 fixes that and adds coaching on the capture screens:
- **Geometry:** each edge records its walking heading (circular mean and spread), and each entrance its facing-out heading. The anchor heading is now taken at the straight-on shot (0.1 took it at the Save tap), stored as `facingDeg`. Anchors also get a centre height, a nearest-node picker, and the distance from that node. Rooms get a door side, and there's a `START` node type for P1 and P2.
- **Quality on site:** a preview of each shot with sharpness and OCR, and Retake. A frame guide, a level indicator and a dim-light warning on the camera. The glasses OCR check now uses the 2 to 3 m shot, with the glasses' wider lens accounted for.
- **Coaching:** a "Next up" hint card, a per-building checklist, and a "Fix before leaving" warning list. An edge left open links automatically to the next node, and edge From/To can be edited. The stride is remembered. The tutorial is now one step per page.
- Log format version 2. Version 1 logs still load. Field list in [survey-app/README.md](../survey-app/README.md).

## Built (0.1)

CampusSurvey compiles, its 12 JVM unit tests pass, and an Android 16 emulator smoke test exercised a new session, an entrance node with 10 s GPS averaging, an edge walk with step count, an elevator ride (called, boarded, doors opened) with a 5 Hz pressure trace, the 3-shot anchor camera flow with on-device OCR and blur check, the review list with edit and delete, and export to a shared zip. Usage instructions are in [survey-app/README.md](../survey-app/README.md). The debug APK is at `survey-app/dist/CampusSurvey-0.2-debug.apk`.

The emulator has synthetic sensors, so it could not verify OCR against real signage text or real barometer and step data. The first real-world check of those is on the phone this week, per the workflow in [survey-app/README.md](../survey-app/README.md).

## The rules question, answered first

HackGT's rule is "you may not submit your past projects to HackGT." A survey tool written before the event is fine **only if it stays completely separate from the submission**. The firewall, which is not negotiable:

1. **Separate Git repository, separate application id, separate Android Studio project.** `CampusSurvey`, not `CampusMaps`. It never enters the CampusMaps repo.
2. **Zero code moves between them.** Not one file, not one function, not a copy-pasted sensor helper. If we want something similar in CampusMaps, we write it cleanly in CampusMaps.
3. **It emits a raw survey log, never the building JSON.** A converter turns a survey log into `CS.json`. That keeps CampusMaps concepts, the graph, the anchors, and the routing costs separate. The survey app knows nothing about routing.
4. **Not demoed, not on the Devpost project, not in the video.** It appears once, in the transparency section: "Measurements and photos were collected before the event with a separate survey app we wrote."

That is the same position as using a tape measure, a stopwatch, and a spreadsheet, which the plan already allows. The difference is that our tape measure writes JSON.

## What it is

One Android app, one screen plus a camera, that records observations with every sensor attached automatically. It is a recorder, not an editor. Typing is the enemy; every observation should be one tap plus a number.

## What it captures that a notebook cannot

| Capture | Why it matters | Where it goes |
|---|---|---|
| **Elevator pressure trace at 5 Hz** | Calibrates `floorHeightM` and proves the barometer can see a 6-floor ride. Also gives the real ride and wait times. | [03-localization.md](03-localization.md) floor logic, elevator ETA |
| **Averaged GPS at each entrance** | 10 seconds of fixes averaged beats one reading by several metres. Five entrances, each needing coordinates for the Geospatial leg. | `lat`, `lng` on entrance nodes |
| **Compass heading per anchor** | The `facing` field drives the yaw-only snap math. Guessing "south" from memory is how arrows end up pointing at walls. | `facing` on anchors |
| **Step count per edge** | Walk from node to node, press stop, get a distance from a calibrated stride. No counting out loud. | `lengthM` on edges |
| **Light level per anchor** | Predicts whether the glasses camera can read that sign in the evening. | Demo C route choice |
| **On-device OCR check per anchor** | Reads the sign with ML Kit and reports the character pixel height against the 16 px floor. Tells you on site whether it is a text anchor. | `kind` on anchors |
| **Blur check per photo** | Warns on a soft photo while you can still retake it. | Anchor photo quality |
| **Synchronized sensor track during video** | Walkthrough videos get a pressure and heading log alongside, so the replay tool knows the true floor at each moment. | Replay testing |

## Screens

### 1. Session
Building (KL, CS, CSE), current floor, surveyor name. Everything after is tagged with these. A floor selector stays pinned at the top of every screen, because forgetting to change it is the one mistake that ruins a session.

### 2. Capture (the main screen)
Six big buttons. Each one snapshots all sensors at the moment of the tap.

- **Anchor.** Opens the camera and walks you through three shots with on-screen prompts: straight on filling the frame, from 2 to 3 m, at a walking angle. Then asks width in centimetres (number pad) and the exact text (keyboard, prefilled with whatever OCR read). Auto-names `CS-A07`. Captures heading, light level, GPS, pressure, and the OCR result with character height.
- **Node.** Drops a node: type (entrance, intersection, stairs, elevator, room, waypoint) and a name. Entrances get the 10-second GPS averaging with a progress ring.
- **Edge.** Press to start walking, press to stop. Records step count, duration, start and end pressure, and both endpoints. Distance comes from steps times your calibrated stride.
- **Elevator.** Three taps: "called", "boarded", "doors opened". Records the wait, the ride, and the full pressure curve at 5 Hz throughout.
- **Stairs.** Press at the bottom, press at the top. Same trace, plus step count.
- **Note.** A photo and a text field, for posted hours, card readers, and anything odd. Voice memo optional.

Plus a **Video** toggle that records a walkthrough with a synchronized 5 Hz sensor log.

### 3. Review
A list of everything in the session, newest first, each editable and deletable. This is where you catch "that anchor was on floor 2, not 1" before leaving the building.

### 4. Export
Writes one folder per session: `survey-CS-2026-09-22/` containing `survey.json`, all photos and videos under their generated names, and a `README.txt` listing what is inside. Share it to Drive from the phone.

### Calibrate (once, at the start)
Walk a measured 10 metres, app counts steps, computes your stride. Redo per surveyor.

## Output format

A flat log of observations. Deliberately not the building schema.

```json
{
  "session": { "building": "CS", "surveyor": "raphael", "startedAt": "2026-09-22T14:03:11-04:00",
               "strideM": 0.74, "device": "SM-S938U" },
  "observations": [
    { "id": 1, "kind": "node", "at": "2026-09-22T14:04:02-04:00", "floor": 1,
      "nodeType": "entrance", "name": "Decatur St entrance",
      "gps": { "lat": 33.75301, "lng": -84.38502, "accuracyM": 3.1, "samples": 42 },
      "pressureHpa": 1006.83, "headingDeg": 184.2, "lightLux": 8400 },
    { "id": 2, "kind": "anchor", "at": "2026-09-22T14:06:40-04:00", "floor": 1,
      "photos": ["CS-A01-straight.jpg", "CS-A01-far.jpg", "CS-A01-angle.jpg"],
      "widthCm": 90, "text": "CLASSROOM SOUTH DIRECTORY",
      "ocr": { "read": "CLASSROOM SOUTH DIRECTORY", "charHeightPx": 41 },
      "headingDeg": 274.0, "lightLux": 520, "blurScore": 0.81,
      "nearestObservation": 1, "note": "north wall of the lobby" },
    { "id": 3, "kind": "edge", "at": "2026-09-22T14:08:12-04:00", "floor": 1,
      "fromObservation": 1, "toObservation": 4, "steps": 31, "durationSec": 24,
      "pressureStartHpa": 1006.83, "pressureEndHpa": 1006.81 },
    { "id": 7, "kind": "elevator", "at": "2026-09-22T14:21:05-04:00",
      "fromFloor": 1, "toFloor": 6, "waitSec": 28, "rideSec": 22,
      "pressureTrace": [[0.0, 1006.80], [0.2, 1006.79], [0.4, 1006.71]]
    }
  ]
}
```

A converter reads this and produces `CS.json`.

## Two sizes. Pick one.

**Tier 1, about 2 hours.** One screen, a floor selector, three buttons (Photo, Mark, Trace), a review list, and a CSV plus photo folder export. Every capture still snapshots GPS, pressure, heading, and light. The elevator trace and the averaged GPS both work. No camera prompts, no OCR check, no step counting.

**Tier 2, about 6 to 8 hours.** Everything in this document.

**Recommendation: Tier 1 on Monday morning, then leave for Classroom South in the afternoon.** Add Tier 2 features only if a visit slips to Wednesday. The site visits are the real bottleneck and they have a hard deadline; the tool exists to serve them, not the other way round.

## Non-negotiable: the paper fallback

Print the anchor log table from [09-data-capture.md](09-data-capture.md) and take it to every visit. If the app crashes, the battery dies, or a screen freezes in the cold, the visit still happens on paper. A tool that can block a site visit is worse than no tool.

## Tech

Plain Kotlin and Compose, CameraX for capture, `SensorManager` for pressure, rotation vector, light, and step detector, `FusedLocationProviderClient` for GPS, ML Kit bundled text recognition for the on-site OCR check, kotlinx.serialization for the log. `minSdk` 26. Nothing exotic, nothing shared with CampusMaps.

## What it does not do

It does not decide which entrance is fastest, draw the floor sketch, or replace walking each route with a stopwatch. Those are still human jobs on the visit.
