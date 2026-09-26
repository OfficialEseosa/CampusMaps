# 00. Overview: What CampusMaps Is and Why

**Status:** planning document, pre-event. Written 2026-09-20. Nothing in this folder is code.

## One sentence

CampusMaps knows the building, not just the street. It finds where you are indoors from what your camera sees, picks the entrance you can actually use right now, and guides you to your room with AR on your phone or spoken directions through your glasses.

## The three claims we make, and what each one costs

| Claim | What has to work | Which demo proves it | Module docs |
|---|---|---|---|
| **Live indoor localization, no GPS, no beacons** | Camera recognizes a sign or poster, we snap to a known node, ARCore motion tracking carries us between signs | Demo A (live, Klaus) | [03-localization.md](03-localization.md) |
| **Entrance-level intelligence** | Building graph with every entrance, routing by total time to the room, access rules by time of day, alternative routes with ETAs | Demo B (recorded, Classroom South), Demo C (recorded, Student Center) | [02-building-data.md](02-building-data.md), [04-routing.md](04-routing.md) |
| **One system, two outputs** | Same route engine drives phone AR arrows and glasses speech (plus watch haptics) | Demo A (phone), Demo C (glasses) | [05-ar-guidance.md](05-ar-guidance.md), [06-glasses-bridge.md](06-glasses-bridge.md), [08-watch-companion.md](08-watch-companion.md) |

If a feature does not serve one of those three claims, it is the first thing to cut when time runs short.

## Why this is a hackathon-sized idea and not a research project

- We hand-build the map for three short routes. No SLAM, no floor plan import, no 3D scan.
- Localization is "which known landmark am I looking at", not general visual positioning. ARCore Augmented Images and ML Kit text recognition are both mature, on-device, and free.
- Routing is Dijkstra over a graph of roughly 30 to 60 nodes per building. It is a few dozen lines of Kotlin.
- Everything runs on one phone. The glasses and the watch are peripherals that receive text and send frames.

## How this compares to prior hackathon projects

Research could not confirm the "VisionNav" reference from the draft plan, so the pitch does not name it. The general line: camera-plus-audio assistive apps see what is in front of you; CampusMaps knows where you are, where you are going, and which door will actually open. Prior indoor-AR hackathon projects (see [research/hackgt-and-competitors.md](research/hackgt-and-competitors.md)) almost all started from QR codes and reported drift and relocalization as their unsolved problem; our answer is existing signage as anchors at every decision point with a re-snap on each detection.

**Track:** "The Lighthouse Laboratory", HackGT 13's Immersive (AR/VR/XR) track. "The Shipyard" (hardware) is a possible second submission if the packet allows multi-track entries.

## Ground rules (repeated because they matter)

1. **Check the event packet for the AI-generated code rule.** If disclosure is required, disclose on Devpost.
2. **Be transparent on Devpost** about what was captured before the event (photos, measurements, anchor log).
3. **Everything on-device.** No network in the demo path except the outdoor ARCore Geospatial leg in Demo B, which runs on cellular at GSU.
4. **Lock dependency versions in the first two hours** and do not upgrade mid-event.

## Reading order for a new teammate

1. This file.
2. [01-demos.md](01-demos.md) so you know what "done" looks like.
3. [14-tech-stack.md](14-tech-stack.md) for the libraries and versions.
4. The module doc for whatever you own.
5. [10-timeline.md](10-timeline.md) and [11-team-and-workflow.md](11-team-and-workflow.md).
6. [15-open-questions.md](15-open-questions.md), and answer anything you can.
