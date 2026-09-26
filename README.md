# CampusMaps

**Walk to any room on campus and the arrows come to you.** CampusMaps is indoor and outdoor AR navigation built at HackGT 13: a Galaxy S25 Ultra draws the route on the floor in front of you, Ray-Ban Meta glasses read the signs and speak the turns, and a Galaxy Watch buzzes and points at every corner. Everything indoors runs on the phone, with no network.

Built by Uyiosa Nehikhuere and Raphael Omorose for the Lighthouse Laboratory track. Buildings mapped: Klaus (Georgia Tech), Classroom South and Student Center East (Georgia State).

## The journey

1. **Explore.** Open the app anywhere on campus and it shows a Google map with your dot, the walking route to the entrance the router recommends, street steps with names, distance and minutes. One tap starts AR.
2. **Approach.** Under 40 m from the door a card says "Almost there, point your camera ahead" and the map tilts, zooms and dissolves into the camera view. Outside, ARCore's Visual Positioning locks your position to about a metre and chevrons point at the door; at the door the app snaps you to the exact entrance and the indoor steps take over.
3. **Inside.** Metre-wide arrows lie on the floor along the corridor, an amber arrow marks the next turn, a floating post labels the room, and the banner says "Room 150 is ahead" in the campus voice. Sign posters are image anchors that fix your position; the barometer follows you up the elevator floor by floor; the heading self-corrects as you walk.
4. **Hands free.** In Glasses mode the Ray-Bans take three stills, the camera stops, the phone reads the sign, votes on where you are, and speaks the next instruction through the glasses, then loops. Camera and audio never overlap.
5. **On the wrist.** Every step reaches the watch within a second with its own arrow and haptic pattern, the face opens by itself when a route starts, and clears when it ends.

## What makes it work

- **A real routing core**, pure Kotlin and unit-tested: entrance and elevator-versus-stairs alternatives, measured walking, stair and elevator times, card-only doors by time of day, "also via" folding, rerouting from wherever you are.
- **Surveyed buildings.** Each building is a JSON graph measured on foot with a stride-calibrated survey and walk videos, converted by `core`, validated on every launch (sign spacing, reachability, door directions). An in-app editor adds a room, a corridor point or a link on the spot and exports the merged file.
- **Localization that layers up.** GPS for the campus, Visual Positioning for the door, the door's measured heading and the compass for the first indoor placement, walking-direction refinement, image anchors and OCR text anchors for exact fixes, the barometer for floors.
- **PantherCard aware.** Student Center East is card-only evenings and weekends. The app says so, lets a card holder route straight through the main door, and floats a "Tap your PantherCard" sign at the door in AR.
- **Two campuses, two skins.** Georgia Tech gold and navy with Buzz, Georgia State blue and white with the panther; Sora type throughout; an animated launch splash.
- **ElevenLabs voice** for spoken directions, cached per sentence so a route never waits on the network; phone TTS as fallback.

## Modules

- `app/` the phone app (`com.campusmaps`), Kotlin and Compose, ARCore via SceneView, Maps Compose, ML Kit text, Meta Wearables toolkit. Sources in `app/src/main/java`; building files in `app/src/main/assets/buildings/`; anchor images in `app/src/main/assets/anchors/`.
- `core/` pure Kotlin/JVM: building model, loader, validator, router, instructions, survey converter and editor patches. `./gradlew :core:test`.
- `shared/` the watch step format and haptic patterns shared by `app` and `wear`.
- `wear/` the Galaxy Watch app.

## Docs

- Plan and module docs: [docs/README.md](docs/README.md); building data status: [docs/19-building-data-status.md](docs/19-building-data-status.md); QA and field logs: [docs/22-phone-qa.md](docs/22-phone-qa.md)
- Next session: [HANDOFF.md](HANDOFF.md); agent reports from the build-out: `reports/`
- Design handoff: [CampusMaps-Design-Handoff (1).md](CampusMaps-Design-Handoff%20(1).md); status list: [WHATS-LEFT.md](WHATS-LEFT.md)

## Build and run

```
./gradlew :core:test :shared:test :app:testDebugUnitTest :app:assembleDebug :wear:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 21 runs Gradle; modules target JVM 17. Keys go in `local.properties` (never committed): `MAPS_API_KEY`, `ARCORE_API_KEY`, `ELEVENLABS_API_KEY`. Debug overlay: long-press the title on S1 (off in demo mode). Debug builds keep their own guidance log at `files/logs/campusmaps.log` on the phone.

## Demo mode

Settings, Demo mode: the app opens on the building's start spot, shows the demo rooms only, keeps the screen on and hides debug. Turn on "I carry a PantherCard" for Student Center East.
