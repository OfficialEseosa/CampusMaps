# 09. Data Capture and Pre-Event Checklist

**Owner:** Data Lead, with everyone helping on the site visits. **Deadline:** Thursday, September 24 for GSU buildings; Klaus by Hour 2 on Friday if not before.

Capturing data before the event is allowed and must be disclosed on Devpost. The recordings this week are **mapping and test recordings, not demo videos**. Demo videos are recorded during the event with the working app.

## Priority order

1. **Classroom South** (longest route, most entrances, the whole point of Demo B).
2. **Student Center East** (short route, access rules, must be visited in the evening once).
3. **Klaus** (can be done in Hour 0 to 2 on Friday, but earlier is better).

## Per-building checklist

### 1. Floor sketch
Hand-drawn is fine. One sheet per floor on the route. Mark: entrances (with compass side), hallway intersections, bends, stairs, elevators, target rooms, and the building origin point (see [02-building-data.md](02-building-data.md)). Photograph the sketch immediately.

### 2. Distances
- Measure one person's stride once with a tape measure (walk 10 steps, divide).
- Count steps between every pair of connected nodes. Write them on the sketch.
- Or use a phone measuring app for short hops; steps are faster for hallways.

### 3. Anchor photos (highest priority)
For every candidate sign, poster, directory board, and room plaque on each route, take **three photos** with the default camera app, no filters, no zoom, no HDR artefacts:
1. Straight on, filling the frame, well lit (this is the ARCore reference image).
2. From 2 to 3 metres away at a normal walking approach.
3. At a slight walking angle (about 30 degrees).

Then **measure the physical width** in centimetres and **write down the exact text** on it.

What makes a good phone (image) anchor:
- Flat, matte, large (at least 40 cm wide), lots of high-contrast detail, not repetitive.
- Directory boards, posters, framed departmental signs, large room-number banners.

What makes a poor one:
- Glossy glass with reflections, plain text on a plain background, anything under 20 cm wide, anything with repeating patterns, anything that gets changed (bulletin boards with pinned flyers).

Small room plaques become **text anchors** (recognized by OCR), not image anchors.

**Target:** 8 to 12 anchors per demo route, no more than 15 to 20 m apart, at least 2 per floor, and one near each stairwell and elevator landing used on the route.

### 4. Route walkthrough videos
Phone at chest height pointing forward, normal walking pace, narrate what you pass and where you turn, count steps out loud. These feed the replay tool so localization can be tested on a laptop without going back to GSU.
- Classroom South: from **both** outdoor start points, through **each** candidate entrance, to the demo room. That is at least 4 videos.
- Student Center East: the short indoor route, once in daylight and once in the evening.
- Klaus: both start points to the destination.

### 5. Glasses point-of-view footage
Walk the Student Center East route wearing the Ray-Ban Meta Gen 2s in the evening, recording with the glasses' own capture. This tells us whether the glasses camera angle and evening lighting are good enough for text recognition before we bet Demo C on it.

### 6. Timing (Classroom South, rooms 150 and 608)
- Stopwatch every entrance-to-room route for **both rooms**. For 608, **if no entrance is at least a minute faster than another from the two chosen outdoor start points, move the start points** further apart around the building. This is the single most important pre-event check for Demo B.
- Time one flight of stairs up and one down.
- Time the elevator bank (four cars, one location) 5 times from pressing the call button to *any* car's doors opening, plus the ride time from floor 1 to 6. Record all five, the average, and the worst. Also record the barometer reading before and after each ride (this doubles as the floor-delta calibration).
- Classroom South has about five entrances (three on floor 1, two on floor 2, one of them from Library South). For **every** entrance record: name and street side, floor, whether it is outdoor or a connection from another building, its latitude, longitude, and heading if outdoor, the walk to the elevator bank in steps, and any posted hours or card readers. For the library connection, also record Library South's opening hours.
- Note the latitude, longitude, and compass heading of each entrance door (phone compass app standing on the threshold facing out). This is the only outdoor data we map.
- Time a 50 m straight hallway walk at normal pace to get walking speed.

### 7. Floors
Note which floor every node is on. Note which floor the barometer test was done on (see pre-event checklist below).

### 8. Access notices (Student Center East)
Photograph any posted hours and card-access signs on **both** entrances. Confirm which one stays open to the public in the evening and until when, **specifically on a Saturday** (posted hours say Saturday 12 PM to 8 PM). Ask the front desk if the signs are unclear. **Do not guess.** Demo C is only honest if the rule is real. If the building is simply closed after 8 PM on Saturday, the story becomes "this entrance is closed after 8 PM on weekends, but it is card-access until 11 PM on weekdays", which is still a time-aware rule, and the recording uses the simulated-time toggle.

### 9. Spoken hints
Write down memorable landmarks along each route: "the vending machines", "the big mural", "the double doors". These become edge hints.

### 10. B-roll for the pitch video
Building exteriors, a card reader on a locked door, a busy hallway at class change. Short, steady, landscape clips.

## File naming and folders

Building code + type + number:
- Codes: `CS` Classroom South, `CSE` Student Center East, `KL` Klaus.
- Types: `A` anchor, `W` walkthrough, `G` glasses clip, `B` b-roll, `S` sketch, `T` timing note.
- Examples: `CS-A03.jpg`, `CS-W01.mp4`, `CSE-G01.mp4`, `KL-B02.mp4`, `CS-S1.jpg` (floor 1 sketch).

One shared folder (Drive or similar) with a subfolder per building, each containing `anchors/`, `walkthroughs/`, `glasses/`, `broll/`, `sketches/`.

## Anchor log (one row per anchor)

Keep as a spreadsheet; it becomes the `anchors` array in the building file.

| file | building | floor | location description | wall faces | width cm | exact text | nearest node | kind (image/text/both) | ARCore score (fill at event) | keep? |
|---|---|---|---|---|---|---|---|---|---|---|
| CS-A03 | CS | 1 | Directory board, lobby north wall | south | 90 | CLASSROOM SOUTH DIRECTORY | H1 | image | | |

## Pre-event device checklist

### Accounts and SDKs
- [ ] **Start today:** Meta Managed Account organization at work.meta.com (needs a company name and an admin), then a Wearables Developer Center team. Unbounded approval latency; see [research/meta-glasses.md](research/meta-glasses.md).
- [x] Meta AI app on the demo phone at v282 or newer (289.0.0.25.162 confirmed 2026-09-20).
- [ ] Glasses firmware v126 or newer: build 70523680060800100 reported; read the short version in the Meta AI app under the glasses' settings, or just confirm the sample app streams.
- [ ] Developer Mode on in the Meta AI app (tap the version 5 times) **and** per device under the glasses' settings. Re-check Friday morning; it resets after updates.
- [ ] Official CameraAccess sample from `facebook/meta-wearables-dat-android` builds and streams from the real glasses with `APPLICATION_ID` and `CLIENT_TOKEN` set to `0`. This proves the Developer Mode bypass.
- [ ] Convert the glasses point-of-view footage to H.265 with ffmpeg for the Mock Device Kit.
- [ ] Score every anchor photo with `arcoreimg.exe eval-img` (ARCore SDK 1.56.0 download); keep only 75 and above; build the three `.imgdb` files.
- [ ] Test project proving AGP 9.4.0 + Kotlin 2.4.10 + SceneView 4.38.0 + Compose BOM 2026.09.00 + mwdat 0.9.0 compile together.
- [ ] Install Google TTS on the demo phone and set it as the default engine.
- [ ] Android Studio (current stable) on every laptop that will build.
- [ ] Google Cloud project with the ARCore API enabled, API key restricted to the app (only if the Geospatial leg stays).
- [ ] Git repository configured, everyone can push.

### Demo phone (Galaxy S25 Ultra, stable Android 16)
- [x] Chosen and rolled back to stable Android 16.
- [ ] Google Play Services for AR installed; any ARCore app shows surface tracking.
- [ ] Automatic system updates off until after the event.
- [ ] Developer options and USB and wireless debugging on.
- [ ] A free sensor app shows barometric pressure readings.
- [ ] **Three-device Bluetooth test:** glasses and watch connected at the same time, play audio through the glasses for 15 minutes while walking. Confirm nothing disconnects and audio stays on the glasses.
- [ ] Backup phone on stable Android, ARCore-capable, if possible.

### Watch (Galaxy Watch 8 Classic)
- [ ] Developer options and wireless debugging on; Android Studio can install to it.
- [ ] Sensor app shows barometric pressure.

### Barometer sanity test (Classroom South)
Note the pressure on one floor, walk up one floor by stairs, note again. Repeat twice, then once by elevator. Record the numbers. This tells us how big "one floor" looks on this phone and this watch (expected roughly 0.4 hPa per floor; see [03-localization.md](03-localization.md)).

### Pack
- [ ] Chargers for phone, watch, glasses case. Battery packs. USB-C cables for the laptops.
- [ ] Tape measure. Notebook. The printed anchor log.
- [ ] A second phone for filming.

## What happens to the captures at the event

| Capture | Becomes |
|---|---|
| Anchor log and straight-on photos | `anchors` in each building JSON and the ARCore image database (scored, weak ones replaced by Hour 10) |
| Sketches and step counts | `nodes` and `edges` |
| Timing notes | `walkingSpeedMps`, `stairsSecondsPerFloor`, elevator wait numbers |
| Access photos | `access` rules on entrances |
| Walkthrough and glasses videos | replay tool inputs for testing localization on a laptop |
| B-roll | the pitch video |
