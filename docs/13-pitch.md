# 13. Pitch and Devpost

**Owner:** Demo and Pitch Lead. **Format:** Expo in the Klaus Atrium, Sunday 9:30 to 11:00 AM (from the hack.gt schedule). That is table judging: 3 to 5 minutes per judge group, repeated many times, so the live demo must be fast and resettable and the videos must play on a laptop at the table without sound problems (bring a small speaker). **Track:** The Lighthouse Laboratory (Immersive).

## Outline

| Time | Beat | What is on screen |
|---|---|---|
| 0:00 to 0:15 | **Hook.** "Google Maps gets you to the building. Then you're on your own." One sentence about being locked out of a card-access door at 9 pm. | B-roll: card reader on a locked door |
| 0:15 to 1:15 | **Demo A, live.** Hand the judge the phone. "Pick anywhere on this list." They point at a sign, arrows appear, they walk. | The phone in the judge's hands, second teammate narrating minimally |
| 1:15 to 1:45 | **Demo B video.** Same room, two starting points, two different entrances, route cards with real ETAs, the accessibility toggle removing stairs. | Edited video with captions, no voice-over needed |
| 1:45 to 2:15 | **Demo C video.** Evening, glasses on, the spoken after-hours heads-up, the redirect, spoken turns inside, watch buzzing. Framed around accessibility and hands-free use. | Edited video with the glasses audio audible |
| 2:15 to 2:35 | **How it works.** Landmark recognition on device, a hand-built building graph, time-aware routing, one route engine driving three outputs. | One architecture slide |
| 2:35 to 2:50 | **What's next.** Class schedule sync, more buildings, crowd-sourced anchor capture, a sponsored pilot with a university. | One slide |

## Prepared answers

| Question | Answer |
|---|---|
| "What happens if I start somewhere else?" | Show it. Walk to the second start point and re-localize. |
| "How is this different from assistive camera apps or last year's winner VisionNav?" | VisionNav (HackGT 12, 1st in The Curator's Cause) steers you around what its camera sees right now: obstacles, a cup, a chair. It has no map. CampusMaps knows the building: where you are, where you are going, and which door will open at 9 pm. Different problem; theirs is the last 2 metres, ours is the last 200. |
| "Isn't this just Google Maps Indoor Live View?" | Live View covers select malls, airports, and transit hubs. It does not know campus buildings, which entrance gets you to your room fastest, or who can get through which door at 9 pm. That data comes from the university, which is why this is a campus product. |
| "How does this scale to a whole campus?" | One JSON file per building plus crowd-sourced anchor photos. A student with a phone can map a building in an hour; we did three this week. Universities already have floor plans and door schedules. |
| "Why not beacons or Wi-Fi positioning?" | Beacons cost money and batteries; Wi-Fi positioning needs a survey and gives 5 to 10 m. Signs are already on every wall, and they are free. |
| "What if a sign is removed or changed?" | The next anchor re-snaps. Between anchors, phone motion tracking carries us. Missing anchors degrade gracefully; they do not break the route. |
| "How accurate is it?" | About half a metre right after seeing a sign, drifting to a metre or two over 20 m, then snapping back. Good enough to find a door. |

## Devpost page structure

1. Inspiration: the locked door story.
2. What it does: the three claims from [00-overview.md](00-overview.md).
3. How we built it: the architecture, the libraries (from [14-tech-stack.md](14-tech-stack.md)), the building data format.
4. Challenges: whatever actually bit us (anchors, Bluetooth, the glasses toolkit).
5. Accomplishments: three buildings, three outputs, all on-device.
6. What we learned.
7. What's next.
8. **Transparency:** pre-event data capture, AI assistance disclosure per the event rule.
9. Video: under 3 minutes, Demo A footage first, then B, then C.

## Slides (at most 5)

1. Title and one-liner.
2. The gap: Maps stops at the door.
3. Architecture (six modules, one route engine, three outputs).
4. What we measured (walking speed, stairs, elevator waits, anchor count) so the ETAs are visibly real.
5. What's next.

## Rehearsal

Three full run-throughs in Hour 32 to 36, with a different teammate playing the judge each time, including resetting the phone between runs in under 20 seconds.
