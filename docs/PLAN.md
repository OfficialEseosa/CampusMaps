# CampusMaps: HackGT 13 Build Plan (source document, draft v1)

This is the original plan as written before the event. The detailed per-module documents in this folder
expand on it. If the two disagree, the per-module document is newer and wins. See [README.md](README.md)
for the index.

---

**Track:** Immersive (AR/VR/XR)
**Event:** HackGT 13, September 25 to 27, 2026, Klaus Center for Advanced Computing, Georgia Tech
**Platform:** Android (Kotlin) on a Samsung Galaxy S25 Ultra running stable Android 16, Ray-Ban Meta Gen 2 glasses (audio and camera only), Galaxy Watch 8 Classic (Wear OS 6, glanceable cues and haptics)
**Buildings:** Klaus (live), Classroom South at GSU (recorded), GSU Student Center (recorded)
**Build window:** roughly 36 hours of hacking (confirm in the event packet)

> **Status: draft v1, written before the event.** Nothing here is final. Scope limits, demo definitions, hour budgets, and cut lines are starting positions with reasoning attached, meant to be revised as the team learns more.

## 1. The Pitch in One Sentence

CampusMaps knows the building, not just the street: it finds where you are indoors from what your camera sees, picks the entrance you can actually use right now, and guides you to your room through AR on your phone or spoken directions through your glasses.

### What makes it different
- **Entrance-level intelligence.** Google Maps gets you to the building. CampusMaps gets you through the right door (including after-hours card-access rules) and to your seat.
- **Live indoor localization.** No GPS, no beacons. The app recognizes signs, plaques, and landmarks to figure out where you are.
- **One system, two outputs.** Phone shows world-locked AR arrows. Glasses give hands-free spoken turn-by-turn.

### Why this beats last year's comparable winner
(Draft v1 named "VisionNav" here; research on 2026-09-20 could not confirm that project, and the team does not know where the reference came from. The pitch no longer names it. See [00-overview.md](00-overview.md).)

## 2. Ground Rules

1. Check the event packet for AI-generated code rules. Disclose on Devpost if required.
2. Be transparent on Devpost about what was captured before the event.
3. Everything runs on-device. No cloud calls in the demo path, except the outdoor Geospatial leg in Demo B over cellular at GSU.

## 3. The Three Hero Demos

- **Demo A, live in Klaus:** judge picks a destination; phone recognizes a landmark, localizes, draws AR arrows. Works from 2 starting points, route under ~60 m, localizes within 5 s.
- **Demo B, Classroom South, multiple entrances (recorded):** two outdoor starting points, two different recommended entrances for the same upper-floor room, 2 to 3 route options with ETAs, accessibility toggle removes stairs, barometer confirms floor change.
- **Demo C, hands-free with glasses at the Student Center after hours (recorded):** spoken after-hours card-access notice with redirect, then 2 to 3 spoken turns triggered by what the glasses camera saw. Simulated-time toggle as safety net.

## 4. Architecture (six modules plus watch)

4.1 Building Data, 4.2 Localization, 4.3 Routing, 4.4 AR Guidance, 4.5 Glasses Bridge, 4.6 App Shell and UI, 4.7 Watch Companion. Each is now its own document in this folder.

## 5. Out of Scope for Now

Class schedule sync, congestion estimates, outdoor navigation between buildings, other buildings, iOS, accounts or backend, Ray-Ban Display HUD, 3D building scans. Watch checkpoint: watch starts only after routing produces correct instructions for all three demos. Stretch: "leave by" time.

## 6 to 6b. Pre-Event Checklist and Recording Protocol

See [09-data-capture.md](09-data-capture.md).

## 7. Event Timeline

See [10-timeline.md](10-timeline.md).

## 8 to 9. Team Roles and How We Work

See [11-team-and-workflow.md](11-team-and-workflow.md).

## 10. Risks

See [12-risks.md](12-risks.md).

## 11. Pitch Outline

See [13-pitch.md](13-pitch.md).

## 12 to 13. Open Questions and Notes

See [15-open-questions.md](15-open-questions.md).
