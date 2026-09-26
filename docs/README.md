# CampusMaps planning folder

Indoor AR campus navigation for HackGT 13 (September 25 to 27, 2026). Android (Kotlin) on a Galaxy S25 Ultra, Ray-Ban Meta Gen 2 glasses for hands-free speech and camera, Galaxy Watch 8 Classic for glanceable cues.

This folder is the plan, one document per part of the project, plus the research those documents are based on.

## Documents

| # | Document | What it covers |
|---|---|---|
| | [PLAN.md](PLAN.md) | The original draft v1 plan, kept as the source of record |
| 00 | [00-overview.md](00-overview.md) | The pitch, the three claims, ground rules, reading order |
| 01 | [01-demos.md](01-demos.md) | The three hero demos: scripts, success criteria, fallbacks, dependency matrix |
| 02 | [02-building-data.md](02-building-data.md) | The building graph format: nodes, edges, anchors, access rules, validation |
| 03 | [03-localization.md](03-localization.md) | ARCore image anchors, text recognition, barometer, Geospatial, replay tool |
| 04 | [04-routing.md](04-routing.md) | Cost model, Dijkstra, alternative routes, access rules, instructions, tests |
| 05 | [05-ar-guidance.md](05-ar-guidance.md) | SceneView, arrows, destination marker, minimap, coordinate gotchas |
| 06 | [06-glasses-bridge.md](06-glasses-bridge.md) | Meta toolkit, the camera/audio interlock, burst capture design, speech, fallback |
| 07 | [07-app-shell-ui.md](07-app-shell-ui.md) | Screens, state model, debug overlay, permissions, demo mode |
| 08 | [08-watch-companion.md](08-watch-companion.md) | Wear OS 6 app, Data Layer protocol, haptic patterns, checkpoint |
| 09 | [09-data-capture.md](09-data-capture.md) | Pre-event checklist, recording protocol, anchor log, file naming |
| 10 | [10-timeline.md](10-timeline.md) | Hour-by-hour event plan, critical path, Saturday trip, feature freeze |
| 11 | [11-team-and-workflow.md](11-team-and-workflow.md) | Roles, how to report bugs, Git, stand-ups, Devpost transparency |
| 12 | [12-risks.md](12-risks.md) | Ranked risks with mitigations and the cut order |
| 13 | [13-pitch.md](13-pitch.md) | Pitch outline, prepared answers, Devpost structure, slides |
| 14 | [14-tech-stack.md](14-tech-stack.md) | Libraries, versions, project layout, what is deliberately excluded |
| 15 | [15-open-questions.md](15-open-questions.md) | Questions for the team, unverified items, decisions already made |
| 17 | [17-survey-review.md](17-survey-review.md) | Review of the first campus survey session, revised 70-minute field protocol, Klaus 20-minute plan |
| 18 | [18-demo-video.md](18-demo-video.md) | Demo video: rules, recording setup, shot lists, editing, submission timeline |
| 19 | [19-building-data-status.md](19-building-data-status.md) | Per building: measured vs estimated, what the next survey must capture, converter usage |
| 21 | [21-integration-notes.md](21-integration-notes.md) | Integration onto the teammate's base: who wrote what, the core adapter, what was removed, open issues |

## Research

Reports compiled on 2026-09-20 with sources linked. The module documents cite them.

| Report | Topic |
|---|---|
| [research/arcore.md](research/arcore.md) | ARCore 1.56 / SceneView 4.38, Augmented Images constraints, Geospatial, pose math, drift |
| [research/meta-glasses.md](research/meta-glasses.md) | Wearables Device Access Toolkit 0.9.0, the audio/camera interlock, Mock Device Kit, setup lead times |
| [research/mlkit-barometer-wear.md](research/mlkit-barometer-wear.md) | ML Kit OCR, anchor recognition alternatives, barometer floor detection, Wear OS 6, TTS, routing |
| [research/hackgt-and-competitors.md](research/hackgt-and-competitors.md) | HackGT 13 rules (mostly unpublished), HackGT 12 winners, prior hackathon projects, competitors, venues |

## Event facts (verified from hack.gt on 2026-09-20)

Hacking runs Friday, September 25, 8:00 PM to Sunday, September 27, 8:00 AM. The expo is Sunday 9:30 to 11:00 AM in the Klaus Atrium. Our track is "The Lighthouse Laboratory" (Immersive AR/VR/XR). Teams are up to four; past projects are not allowed. Meta gives a tech talk Saturday 3:30 PM. Details in [10-timeline.md](10-timeline.md).

## The three findings that changed the plan

1. **Glasses camera and glasses audio cannot run at the same time on Android** with the current toolkit. Demo C is now a "look, then speak" burst loop with a headset fallback. See [06-glasses-bridge.md](06-glasses-bridge.md).
2. **Sceneform is archived; SceneView 4.38.0 is the path**, pinned to ARCore 1.54.0. Small plaques cannot be ARCore image anchors (25% of frame rule); they are text anchors. See [03-localization.md](03-localization.md).
3. **The Meta developer organization has unbounded approval latency** and should be started today; the Developer Mode bypass is the mitigation but is unverified. See [15-open-questions.md](15-open-questions.md).

## How to use this folder during the event

- Each module doc has a work plan table with hours and "done when". Stand-ups check against [10-timeline.md](10-timeline.md).
- When something is decided, write it in the module doc and in section C of [15-open-questions.md](15-open-questions.md).
- When something breaks, report it in the five-part format in [11-team-and-workflow.md](11-team-and-workflow.md).
