# 10. Event Timeline

**Source:** the official schedule embedded in hack.gt (read 2026-09-20). Hacking begins **Friday, September 25 at 8:00 PM** and ends **Sunday, September 27 at 8:00 AM**: exactly 36 hours. The **Expo is Sunday 9:30 to 11:00 AM in the Klaus Atrium**, and the closing ceremony is 12:30 PM at Ferst Theatre. Hour 0 below is Friday 8:00 PM.

## The one rule

**If a module is more than 3 hours behind, cut its polish and move on.** A rough working Demo A beats a perfect half-built one.

## Hour to clock conversion

| Hour | Clock | | Hour | Clock |
|---|---|---|---|---|
| 0 | Fri 8 PM | | 20 | Sat 4 PM |
| 4 | Sat 12 AM | | 22 | Sat 6 PM |
| 8 | Sat 4 AM | | 24 | Sat 8 PM |
| 12 | Sat 8 AM | | 28 | Sun 12 AM |
| 16 | Sat 12 PM | | 32 | Sun 4 AM |
| 18 | Sat 2 PM | | 36 | Sun 8 AM |

## Fixed events that affect us

| When | What | Why it matters |
|---|---|---|
| Fri 2 to 4 PM | Check-in outside Ferst Center | Arrive early; Klaus data capture can happen between check-in and opening ceremony |
| Fri 4 to 5 PM | Opening ceremony, Ferst Theatre | Event packet rules are announced here |
| Fri 5:30 to 7 PM | Sponsor fair, Klaus Atrium | Meta is a sponsor; find their booth and ask about the toolkit |
| Sat 3:30 to 4:30 PM | **Meta Tech Talk** | Bring the glasses questions from [06-glasses-bridge.md](06-glasses-bridge.md); a Meta engineer in the room is worth an hour of guessing |
| Sat 3 to 9 PM | HIVE Makerspace open | Print a backup marker poster if signage scores badly |
| Sat 7 to 8:30 PM | Dinner | The GSU trip overlaps; eat before leaving or grab food to go |
| Sun 8 AM | Hacking ends | Devpost must be submitted by whatever deadline the packet states, likely 8 AM |
| Sun 9:30 to 11 AM | **Expo, Klaus Atrium** | Demo A must be walkable **from the Atrium**. The Klaus route starts at or near our expo table |

## Critical path

```
skeleton + ARCore camera (0-2)
   -> building data + routing (2-6)
      -> anchor recognition + snap (6-10)
         -> AR arrows (10-14)                       -> Demo A works (by 20)
         -> glasses bridge (14-18) -----------------> Demo C recorded (Sat evening)
            -> entrance/access/alternatives (18-20) -> Demo B recorded (Sat evening)
```

Routing is the fan-out point. If it slips, everything slips.

## Hour by hour

| Hours | Clock | Focus | Owner | Done when |
|---|---|---|---|---|
| Pre-0 | Fri 2 to 8 PM | Check-in, Klaus data capture (anchors, sketch, step counts) around the Atrium route, sponsor fair (Meta booth) | Data Lead + QA | Klaus anchor log complete |
| 0 to 2 | Fri 8 to 10 PM | Project skeleton, locked dependency versions, ARCore camera view, glasses toolkit sample connects on the real glasses, watch "hello" installs | Device Lead + Claude | Camera preview; a still from the glasses reaches the phone; watch shows a test message |
| 2 to 6 | Fri 10 PM to Sat 2 AM | Building data model, loader, validator; all three building files; routing with alternatives and ETAs; unit tests | Data Lead (files) + Claude (code) | Route printed as text for every demo scenario, tests green |
| 6 to 10 | Sat 2 to 6 AM | ARCore image database scored and loaded; detection snaps to nodes; debug overlay | Device Lead + Claude | Pointing at an anchor shows the correct node; weak anchors replaced |
| 10 to 14 | Sat 6 to 10 AM | AR arrows, destination marker, minimap | Claude + Device Lead | Arrows lead the right way from 2 starting points in Klaus |
| 14 to 18 | Sat 10 AM to 2 PM | Glasses bridge: burst capture, OCR with voting, spoken instructions over A2DP | Claude + Device Lead | Walking past 3 text anchors triggers 3 correct spoken turns |
| 14 to 20 (parallel) | Sat 10 AM to 4 PM | Watch companion: arrows, icons, haptics wired to instructions | QA + Claude | Right arrow and buzz at each step on the Klaus route |
| 18 to 20 | Sat 2 to 4 PM | Entrance selection by total time, access rules, spoken locked notice, simulated-time toggle, route options screen, accessibility toggle, barometer floor change in the elevator, Geospatial outdoor leg | Claude + Data Lead | Two start points pick two different Classroom South entrances; options screen shows 2 to 3 real choices; elevator ride flips the floor; Student Center notice fires at the cutoff time |
| 20 to 21 | Sat 4 to 5 PM | Meta Tech Talk (one person), final build for the trip installed and tested on the Klaus route | Device Lead | Trip build tagged |
| 21 to 26 | Sat 5 to 10 PM | **Trip to GSU.** Classroom South first (daylight for the outdoor leg), then Student Center East | Demo Lead + Device Lead + one more | Clean takes of B and C, plus backups, copied to two laptops |
| 21 to 28 (those who stay) | Sat 5 PM to Sun 12 AM | Klaus polish, bug fixing, UI cleanup, sleep in shifts | QA + Claude | Demo A works 5 times in a row from the Atrium |
| 28 to 30 | Sun 12 to 2 AM | **Feature freeze.** Record the fallback video of Demo A. Tag the freeze commit | Everyone | No new features after this line |
| 30 to 34 | Sun 2 to 6 AM | Edit demo videos, Devpost write-up, pitch slides | Demo Lead | Devpost draft complete |
| 34 to 36 | Sun 6 to 8 AM | Final submission with 30 minutes to spare, device charging, one full rehearsal | Everyone | Submitted |
| Sun 8 to 9:30 AM | | Breakfast, two more rehearsals at the expo table, phones on chargers | Everyone | Reset between runs under 20 seconds |
| Sun 9:30 to 11 AM | | **Expo** | Everyone | |

## Saturday evening trip plan

- Leave Klaus around 5 PM with: demo phone (charged, trip build installed), backup phone, filming phone, glasses (charged, case charged), watch, battery packs, printed scripts for Demos B and C.
- Classroom South first, while there is daylight for the outdoor Geospatial leg. Two rooms: 150 and 608 (see [01-demos.md](01-demos.md)).
- Student Center East second. Its posted Saturday hours end at 8 PM, so the indoor route must be walked **before 8 PM** through the public entrance. The after-hours redirect is recorded with the **simulated-time toggle** set past the cutoff, disclosed on Devpost. If the team confirms this week that one entrance is genuinely card-only earlier in the evening, record it for real instead.
- Someone who stays behind keeps a laptop on the call so a build fix can be pushed and installed on site.
- Copy all footage to two laptops before leaving GSU.

## Sleep

Two shifts. Nobody codes past 20 hours awake. Device Lead sleeps before the trip. Whoever is presenting at the expo sleeps Sunday 2 to 6 AM.

## What "feature freeze" means

After Hour 28: bug fixes only, each one tested on the Klaus route before commit. No new dependencies, no refactors, no "quick" UI changes. The build that recorded the fallback video is the build that ships unless a fix is proven on the route.
