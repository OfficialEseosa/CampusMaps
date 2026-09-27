## Inspiration
Google Maps gets you to the building. Then you're on your own. Every one of us has wandered a hallway looking for a room number, or walked up to an entrance at 9 pm only to find it's card access after hours. Campus buildings are confusing, doors have rules that aren't on any map, and nothing out there knows which entrance actually gets you to your classroom fastest.

## What it does
CampusMaps takes you from anywhere on campus to the door of the room, and it knows the building, not just the street.

- **From across campus.** A map shows your position, the walking route to the entrance our router recommends, and real street steps. Under 40 m from the door the map tilts and dissolves into the camera view, Google's Visual Positioning locks your position to about a metre, and arrows point at the door. At the door the app snaps you to that exact entrance and the indoor steps take over.
- **Indoors with no GPS and no beacons.** The route is placed from the door's measured heading, corrects itself from the direction you walk, and snaps exactly the moment the camera recognizes a sign or poster that already hangs on the wall. The barometer follows you up the elevator, floor by floor.
- **Picks the entrance you can actually use right now.** Routing is time aware: it knows which doors lock after hours, offers alternative routes with real ETAs, and includes an avoid stairs option. At Student Center East, card access evenings and weekends, it asks "I have my PantherCard?", routes you through the main door, and floats a "Tap your PantherCard" sign at the door in AR.
- **Guides you on whatever you're wearing.** One route engine drives metre-wide world locked AR arrows on your phone, spoken turn by turn directions through Ray-Ban Meta glasses, and haptic arrows on a Galaxy Watch that opens its face by itself when a route starts.

Students can also record and submit their own shortcuts with photos, and anyone can add a room, a corridor point or a link in the app's built-in editor, so the map grows without getting less accurate.

## How we built it
- **Phone app:** Kotlin and Jetpack Compose, ARCore through SceneView for the AR layer, ARCore Geospatial for the outdoor leg, Maps Compose and the Routes API for the street steps, ML Kit text recognition for reading signs on device, ElevenLabs for the voice with every sentence cached on the phone so a route never waits on the network.
- **Routing core:** a pure Kotlin module that models each building as a graph of entrances, hallways, stairs, elevators and rooms. It runs Dijkstra over total time to the room, applies access rules by time of day, and returns alternatives by entrance and stairs versus elevator. Covered by unit tests, including a sweep of every building, destination, start and clock time.
- **Building data:** one JSON file per building. We surveyed Klaus, Classroom South and Student Center East ourselves, measuring walking speed, stair and elevator times, entrance coordinates and headings, and photographing anchors, so the ETAs are real, not guessed. A validator checks every file on launch.
- **Glasses:** Meta's Wearables Device Access Toolkit captures bursts of stills from the Ray-Bans, we run OCR on them, vote on which sign we saw, and speak the next turn through the glasses.
- **Watch:** a Wear OS app that receives each step over the Wearable Data Layer and buzzes a distinct haptic pattern per turn type.

## Challenges we ran into
- **The glasses toolkit fought us.** The glasses only accept an older SDK version, the toolkit can't play audio while the camera is streaming, and the stream is too low resolution to read small text. We redesigned around it: capture a burst of full resolution stills, stop the camera, read the sign, then speak.
- **Plain plaques are invisible to ARCore.** Image anchors need texture, and a room number plate has none. We ended up reading plaques as text instead, and using posters and banners that already hang in the corridors as image anchors.
- **Heading indoors.** A compass next to a steel door frame can be 70 degrees off. We fixed it by trusting the door's surveyed heading at the entrance and letting the first few metres of walking correct the rest.
- **Bugs we only found live,** like a student shortcut recorded on one floor being trusted as a route to a room six floors up. We now validate floors on submission and in the router.

## Accomplishments that we're proud of
- Three real, surveyed buildings, two campuses, three output devices, and one route engine, all running on device.
- A continuous journey from the campus map to the room door, with the hand-off from GPS to Visual Positioning to indoor AR.
- Time aware routing that actually changes your entrance at night, with the PantherCard built in.
- A crowd sourced shortcuts system with a review step, and an editor that lets anyone fix the map on the spot.

## What we learned
We learned how much of indoor navigation is a data problem, not an AR problem. Getting a good route depended on measuring the building carefully. We also learned how to design around hardware limits instead of fighting them, and how to integrate two independently built halves of an app under time pressure.

## What's next
Class schedule sync so your next class is one tap away, more buildings, crowd sourced anchor capture so any student can map a building in an hour, and a pilot with a university that already has the floor plans and door schedules.

## Transparency
We used AI coding assistance during development.
