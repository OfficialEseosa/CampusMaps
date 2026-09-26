# 12. Risks and Mitigations

Ordered by how badly it would hurt the demo, then by likelihood. Each row names when we find out and what we do.

| # | Risk | Likelihood | Impact | When we find out | Mitigation |
|---|---|---|---|---|---|
| 1 | **Anchors not detected** (too small, glare, repetitive design, low ARCore quality score) | High | Kills Demo A | Pre-event by scoring photos with the ARCore image tool; Hour 6 to 10 on the phone | Prefer big posters and directory boards for image anchors; text recognition for plaques; score every photo before the event and replace weak ones; fallback of printed markers taped near the route (allowed, but disclose) |
| 2 | **Hour budgets are optimistic** | Certain | Everything | Every stand-up | 3-hour rule; Demo A first; cut order in [01-demos.md](01-demos.md) |
| 3 | **Glasses toolkit setup problems** (Meta organization approval latency, firmware and app version triple, Developer Mode resetting) | Medium to High | Kills the magic in Demo C | Pre-event (account and sample app); Hour 0 to 2 | Start the Meta organization today; verify the Developer Mode `application_id = 0` bypass on day one; Mock Device Kit; fallback to phone camera with glasses as a Bluetooth headset; decide by Hour 18 |
| 3b | **Toolkit blocks audio while the camera streams, and the stream cannot read small text** (verified in the toolkit's issue tracker) | Certain | Continuous "stream and speak" is impossible | Known now | Burst capture then speak, full-resolution stills for OCR, large text anchors on the Demo C route, plan B poster recognizer; see [06-glasses-bridge.md](06-glasses-bridge.md) |
| 4 | **AR drift between anchors** | Medium | Arrows point at walls | Hour 10 to 14 | Anchors every 15 to 20 m; re-snap on every detection; short routes; minimap as a backup cue |
| 5 | **ETAs look made up, or the route options are seconds apart** | Medium | Demo B's "aha" evaporates | Pre-event timing at Classroom South | Measure walking, stairs, and elevator times; pick a room where options differ by a minute or more; show the reason on the route card |
| 6 | **GSU trip fails** (locked building, weather, timing, dead battery) | Medium | No Demo B or C footage | Saturday evening | Buffer in the schedule; simulated-time toggle; Demo B does not depend on time of day; spare batteries; copy footage before leaving |
| 7 | **Audio not routing to the glasses** while the watch is connected | Low to Medium | Demo C sounds come from the phone | Pre-event three-device test | Force the Bluetooth route in code; if hopeless, record Demo C with the phone in a shirt pocket and say so |
| 8 | **Glasses and watch fighting over Bluetooth**, or all three devices depending on one person | Medium | Testing stalls | Pre-event three-device test | Second teammate learns the setup; keep the watch optional (checkpoint) |
| 9 | **Demo phone overheats or drains** | Medium | Live demo dies | Hour 10 onwards | Backup phone with the same build; charge between runs; keep AR sessions short; no 4K screen recording during the live demo |
| 10 | **Venue Wi-Fi down** | High | None if we did it right | Always | Everything on-device; dependencies cached in Gradle before Friday; Geospatial only over cellular at GSU |
| 11 | **Crowded Atrium during the expo** (judging is an expo in the Klaus Atrium, Sunday 9:30 to 11 AM) | High | Tracking lost, anchors blocked by people, judges confused | Judging | Route starts at our table and uses anchors above head height (wall signs, not floor-level posters); rehearse Sunday 8 to 9:30 AM with people around; "point at a sign" recovery prompt; fallback video on a laptop at the table |
| 12 | **Build or dependency conflicts** (ARCore vs rendering library vs Compose versions) | Medium | Hours lost at Hour 0 | Hour 0 to 2 | Versions chosen in [14-tech-stack.md](14-tech-stack.md) and verified with a test project in advance |
| 13 | **Barometer floor detection flaky** (HVAC, doors, weather drift) | Medium | Demo B loses one sensor moment | Pre-event sanity test | Relative change only, anchored by the last recognized floor; only trust the reading at stairs and elevator nodes; drop it from the video if it misbehaves |
| 14 | **Geospatial VPS coverage poor at Classroom South** | Medium | Outdoor leg of Demo B fails | Pre-event by checking VPS availability at the site | Start the video at the doors instead; the entrance choice is still shown on the options screen |
| 15 | **Event rules surprise us** (AI code policy, judging format, hardware rules) | Low | Disqualification or wasted prep | When the packet is published | Demo Lead reads the packet the hour it is out and updates [15-open-questions.md](15-open-questions.md) |
| 16 | **Access rule at Student Center East is not what we assumed** | Medium | Demo C story is false | Pre-event visit | Photograph the posted notice; ask the desk; if there is no card rule, change the story to a closed entrance with posted hours, which is still time-aware routing |

## Cut order when things go wrong

All of these are in the primary plan (decided 2026-09-20); this is the emergency order only.

1. Watch companion (gated by a checkpoint).
2. Outdoor Geospatial leg of Demo B.
3. Barometer floor display in Demo B.
4. Minimap.
5. Path ribbon (keep simple arrows).
6. Live glasses stream (fall back to phone camera with glasses audio).
7. Alternative routes screen (keep the single best route with the locked notice).

Never cut: routing tests, the debug overlay, the Demo A fallback video.
