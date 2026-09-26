# CampusMaps — demo video script

Target length about 2:30. Voiceover lines are written to be read at a normal pace; each block
lists what is on screen and which overlay to drop on top. Overlays are the transparent clips in
`promo/clips/out/` and `promo/logo-animation/out/`.

Legend: **VO** = voiceover · **ON SCREEN** = base footage · **OVERLAY** = transparent clip on top ·
**TEXT** = a lower-third or caption you add in the editor.

---

## 0:00 – 0:03 · Logo

**ON SCREEN:** black.
**OVERLAY:** `CampusMaps-logo-alpha.mov`, cut at 3 s (before its fade-out) and hard-cut to the next shot.
**VO:** *(none)*

## 0:03 – 0:15 · The problem

**ON SCREEN:** four quick cuts, ~2.5 s each, phone-camera footage: (1) a Google Maps screen ending
at the building pin, (2) a long corridor of identical doors, (3) an elevator panel with the floor
you need greyed out or a stairwell, (4) a door with a PantherCard reader. Last beat: black.
**TEXT (lower third, appears on "Late again"):** "Finding the room is the hard part."
**VO (A):**
> Google Maps gets you to the building. Then it quits.
> Inside, every hallway looks the same, the elevator skips your floor, and the nearest door needs a card.
> Late again.
> CampusMaps takes you the rest of the way.

Alternates, same cuts:
- **B:** "Maps stops at the door. Inside: same hallways, wrong floor, locked door. Late for class — again. CampusMaps takes you to the room."
- **C:** "I found the building. I still couldn't find the room. Every corridor looks the same, and the closest door only opens with a card. That's the part nobody's mapped. CampusMaps did."

## 0:15 – 0:22 · The app in three taps

**ON SCREEN:** the phone screen recording — campus picker, "Pick a building", "Where to?", tap
Room 608. Speed to 2×.
**TEXT:** "CampusMaps · Georgia Tech + Georgia State"
**VO:**
> Pick your campus, pick the building, say the room — and the arrows come to you.

## 0:22 – 0:45 · The building is a graph (Classroom South)

**ON SCREEN:** dark background, or a slow handheld shot of the building exterior at 30% opacity.
**OVERLAY:** `ClassroomSouth-3D-spin-alpha.mov` (20 s). Floors explode up in the first two
seconds, the route draws for three, then it orbits.
**TEXT:** "Classroom South · 6 floors · 5 entrances · surveyed on foot"
**VO:**
> We walked every hallway ourselves. Every entrance, elevator, staircase and room becomes a
> node with real distances, in metres, and real timings — how long the elevator actually waits,
> how long the stairs actually take.
> Watch the route: in through Walters, along the corridor, up the elevator, straight to Room 608.
> One minute twenty-seven. Not because we guessed — because we measured it.

## 0:45 – 1:20 · How it works (the explainer)

**ON SCREEN:** dark background with a subtle slow-moving shot behind it (a drone or a slow
pan of the atrium at 25% opacity). Keep the base calm — the overlay is busy.
**OVERLAY:** `HowCampusMapsWorks-alpha.mov` (35 s). Five chapters, 7 s each. Read one paragraph
per chapter; the clip holds for you.

**VO — chapter 1 (0:45) "The building is a graph":**
> Each building is a JSON graph. Nodes are doors, corners, elevators and rooms; edges are
> hallways with the metres we walked. A validator checks every file before the app will load it.

**VO — chapter 2 (0:52) "Dijkstra picks the route — with time in it":**
> Routing is Dijkstra, in a pure Kotlin core with forty-five tests. It tries every entrance,
> stairs against the elevator, and it knows which doors need a PantherCard right now — the time
> of day is an input, never a surprise. Lock a door at nine PM and the route swings to the next one.

**VO — chapter 3 (0:59) "Finding you":**
> Localisation layers up. GPS gets you to campus. ARCore's Geospatial API locks the door to about
> a metre. Inside, the door's measured heading and the compass place you; ML Kit reads room signs
> and ARCore matches poster images for exact fixes; the barometer tells us which floor you're on.

**VO — chapter 4 (1:06) "Arrows on the floor":**
> Guidance is ARCore and SceneView in Jetpack Compose: metre-wide arrows anchored to the real
> floor, an amber arrow at the next turn. All of it runs on the phone. No network indoors.

**VO — chapter 5 (1:13) "Hands-free":**
> Put on the Ray-Ban Metas and the phone goes in your pocket. The glasses take a burst of stills,
> the phone recognises the sign, the glasses speak the turn — camera and audio never overlap.
> Every step reaches the Galaxy Watch in under a second with its own haptic pattern.

## 1:20 – 1:45 · Phone AR — the real walk

**ON SCREEN:** your phone AR screen recording in Classroom South (full-screen, real speed).
Let the arrows and the "Room 608 is ahead" banner play without cutting.
**TEXT:** "Real footage · Classroom South · no network"
**VO:**
> This is the phone, unedited. The arrows sit on the floor and stay there as you walk. The turn
> is marked before you reach it. If you drift, it reroutes from where you are.
> And when you arrive, it tells you: Room 608 is on your right.

## 1:45 – 2:02 · Glasses and watch

**ON SCREEN:** split — glasses point-of-view / phone screen on the left, the watch on the right
(the wear screenshots or a wrist shot). Sound: keep the glasses' spoken instruction audible.
**TEXT:** "Ray-Ban Meta Gen 2 · Galaxy Watch 8 Classic"
**VO:**
> Hands free, it's the same route. Look at a sign — the glasses tell you where to go next.
> And the watch buzzes: one tap for straight on, two for left, a long buzz when you've arrived.
> You never look down.

## 2:02 – 2:17 · Klaus (Georgia Tech) — it's not one building

**ON SCREEN:** dark background.
**OVERLAY:** `Klaus-3D-spin-alpha.mov` (20 s, cut to 15).
**TEXT:** "Klaus · Georgia Tech · floors 1 and 3 · same core, second campus"
**VO:**
> Same engine, different campus. Klaus at Georgia Tech, the Research Wing door up to the COEUS
> lab on three. Adding a building is a survey walk and a JSON file — no servers, no beacons,
> nothing installed in the building.

## 2:17 – 2:30 · Close

**ON SCREEN:** the campus picker screen, then black.
**OVERLAY:** `CampusMaps-logo-alpha.mov` again, or the last frame held.
**TEXT:** "CampusMaps · HackGT 13 · The Lighthouse Laboratory · Uyiosa Nehikhuere & Raphael Omorose"
**VO:**
> CampusMaps. Walk to any room on campus, and the arrows come to you.
> Built in thirty-six hours at HackGT. Come walk with it.

---

## Notes for the edit

- The three transparent overlays have soft drop shadows; over bright footage, still drop a
  20–30% black plate under them or they wash out.
- The ProRes `.mov` files are straight alpha. If an edge looks dark-fringed, set the clip's
  alpha to "straight/unpremultiplied" in the editor.
- The explainer holds ~3 s per chapter after its diagram builds; if a paragraph runs long, freeze
  the last frame of that chapter rather than speeding the VO.
- Numbers used above, all measured: 1:27 to Room 608 via Walters + elevator; 1.36 m/s walking
  speed; elevator 3.1 s wait, 12.4 s per floor; stairs 21.6 s per floor up; 45 core tests.
