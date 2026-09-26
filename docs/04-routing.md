# 04. Routing (how do I get there?)

**Owner:** whoever is fastest at Kotlin, likely with Claude writing it. **Depends on:** [02-building-data.md](02-building-data.md). **Consumed by:** AR guidance, glasses bridge, watch, route options screen.

Routing is the module most likely to be finished early and most likely to be the one that makes the pitch land. Build it as a pure Kotlin library with no Android dependencies so it can be unit-tested on a laptop in seconds.

## Inputs and outputs

**Input**
- The building graph.
- Start: a node id (from localization), or "outside near lat/lng" for Demo B.
- Destination: a node id (a room).
- Preferences: `avoidStairs: Boolean`, `now: LocalDateTime` (real or simulated).

**Output**: a list of `RouteOption`, best first, each with
- ordered list of node ids
- total ETA in seconds, broken into walking, stairs, elevator wait, elevator ride
- the entrance used (if the start is outdoors)
- the vertical method (`stairs`, `elevator`, `none`)
- a list of `Instruction` objects (see below)
- a `notice` string if a locked entrance was avoided ("The north entrance is card-access after 10 pm. Using the south entrance instead.")

## Cost model (ETA, in seconds)

All constants come from data capture, not guesses. Defaults exist only so the code runs before the data is in.

| Edge kind | Cost |
|---|---|
| hallway, door, outdoor | `lengthM / walkingSpeedMps` (measured walking speed, default 1.3 m/s) |
| stairs | `floors * stairsSecondsPerFloor` (measured, default 22 s per floor going up, 18 s down) |
| elevator | `elevator.avgWaitSec + floors * elevator.secondsPerFloor` (measured 5 times, use the average; keep the worst case for the route card) |
| door with card rule active | infinite (edge removed) |
| stairs with `avoidStairs` | infinite (edge removed) |

Doors and turns add a small fixed penalty (2 s) so that routes with fewer turns win ties and instructions stay short.

## Algorithm

1. **Filter.** Build the working graph by removing edges that are locked for `now` (entrance access rules) and stairs edges if `avoidStairs`.
2. **Dijkstra** from the start to the destination. With under 100 nodes there is no reason for anything fancier. Write it by hand (about 40 lines); do not add a graph library.
3. **Alternatives.** We want 2 or 3 *meaningfully different* options, which means a different entrance or a different vertical method, not a different hallway. The cheap, deterministic way:
   - Enumerate combinations of (entrance E, vertical method V) where E is any unlocked *outdoor* entrance (or "already inside", or the library connection when the start is in the library) and V is `stairs`, `elevator`.
   - For each combination, run Dijkstra on the graph restricted to that entrance and that vertical method.
   - Collect finite results, sort by ETA, drop any whose ETA is within 15 s of a better option using the same entrance and method (duplicates), and keep the top 3.
   - Classroom South has 5 entrances, so this is at most 10 Dijkstra runs, all under a millisecond each.
   - Each option's card says the entrance **and its floor** ("Decatur St entrance, floor 2, elevator, 3 min") because that is the reason the ETAs differ.
4. **Locked notice.** Run step 2 once more with access rules ignored. If the unlocked best route uses an entrance that is locked now, produce the `notice` describing the redirect. This is the Demo C line.
5. **Instructions.** Walk the node list and emit one `Instruction` per decision point (see below).
6. **Recompute** on every localization snap. Routing is cheap; do not cache.

## Instructions

An `Instruction` has: `type`, `text`, `atNode`, `distanceM` to the next instruction, `floorDelta`, and `direction` (left, right, straight, uTurn, up, down, arrive).

Turn direction is derived from the angle between the incoming and outgoing edge vectors in building coordinates: under 30 degrees is straight, 30 to 150 is left or right by sign of the cross product, over 150 is a U-turn.

| Type | Example text | Watch icon | Haptic |
|---|---|---|---|
| `start` | "Head toward the vending machines" | straight | one short |
| `turn` | "Turn left at the lobby T-junction" | left/right | two short (left), three short (right) |
| `stairsUp` / `stairsDown` | "Take the stairs up two floors" | stairs | long-short |
| `elevator` | "Take the elevator to floor 3" | elevator | long-long |
| `door` / `entrance` | "Go through the south entrance" | door | short-long |
| `lockedNotice` | "Heads up. The north entrance is card-access after 10 pm. Redirecting to the south entrance." | lock | four short |
| `arrive` | "Room 312 is on your right" | flag | long |

The `hint` on an edge is appended to the spoken text ("Turn left, past the vending machines"). Keep every instruction under 12 words; text-to-speech at walking pace has about 4 seconds per instruction.

## Simulated time

`now` is a parameter, never read from the clock inside routing. The app shell provides the real clock or the simulated value from the debug overlay. This makes the after-hours logic testable at 2 pm and unit-testable at all.

## Tests (write these first, at Hour 2 to 4)

- Loads all three building files; graph is connected.
- Klaus: route from each of the 2 Atrium start nodes to the demo destination matches the hand-checked node list.
- Classroom South, room 608: from start P1 the best entrance is X; from P2 the best entrance is Y; X != Y.
- Classroom South, room 150: from P1 and P2 the routes pick floor-1 entrances and cross no floors.
- Classroom South, room 608: at least one of the top options uses a floor-2 entrance (one fewer floor to ride); the option cards show the entrance floor.
- Classroom South, library start: the route uses the Library South connection when the library is open and avoids it (with a notice) when it is closed.
- Classroom South, room 608: with `avoidStairs` the recommended vertical method is elevator and no stairs node appears.
- Classroom South, room 608: top two options differ in ETA by at least 60 s (if this fails, pick different start points; see [09-data-capture.md](09-data-capture.md)).
- Outdoor start: a virtual `OUTSIDE` node with an edge to every entrance whose length is the straight-line distance from a given latitude and longitude; the option list changes when that position changes.
- Student Center East: at 21:00 the best unlocked entrance is locked, notice is non-null and names both entrances; at 14:00 notice is null.
- Instructions for each demo route read sensibly when printed (eyeball test).

## Estimated size

Roughly 250 to 350 lines of Kotlin including tests. Budget: Hours 2 to 6. This is the one module that must not slip, because every other module needs its output to have anything to show.

## Decisions 2026-09-25

Found on the emulator by the UI engineer; implemented in `core/` with tests.

- **Duplicate cards.** An option is folded into a faster one with the same vertical method when its ETA is within 15 s and any of: same entrance; same nodes after the entrance (vestibule waypoints skipped); walking distance within 5 m; or a card label that would differ only by the entrance name. Folded entrances are listed in the new `RouteOption.alsoVia: List<String>` (names) so the card can say "also via Library South entrance". Then no two cards may be closer than 20 s (`MIN_CARD_GAP_SEC`), except that the fastest option of each vertical method is always kept, so an elevator and a stairs card both survive.
- **Classroom South Decatur side.** The three doors there are 3.6 to 20 m apart on one facade and funnelled into one node, so their routes were identical. Each now has its own vestibule and edge into the lobby (estimated, from the surveyed fixes and facing-out headings). From P1 they still differ by under 3 s, which is true of doors on one facade; they fold into one card.
- **Entrance only for outdoor starts.** `entrance`, `entranceName` and `entranceFloor` come from the node reached by an outdoor edge (OUTSIDE to entrance, or an outdoor edge in the file that re-enters). A `Start.AtNode` path that passes an entrance node indoors reports null.
- **Starting at an elevator or stairs node.** When the first edge is vertical, the first instruction is the ride or climb ("Take the elevator to floor 6", type `ELEVATOR`), not "Head toward".
- **Reroute direction.** `Start.AtNode(id, cameFrom = null)`. With a same-floor `cameFrom`, cameFrom → id is the incoming vector: the first instruction becomes "Turn left/right toward X" (type `TURN`), "Turn around toward X" or "Continue toward X", and the first step gets the usual turn penalty. It never forbids walking back toward `cameFrom`. Unknown or other-floor `cameFrom` is ignored.
- **60 s gap test.** Not met with estimated data (47 s from P1: elevator 134 s, stairs 181 s). Kept as an `@Ignore` test; the measurements that settle it are listed in [19-building-data-status.md](19-building-data-status.md) ("Demo B: the 60 s gap"). No numbers were tuned to pass it.
- **Validator severities.** `Problem.severity` is `ERROR` (rules 0 to 8, unchanged), `WARN` (rule 9: two nodes under 0.5 m apart on one floor) or `INFO` (rule 10: entrance without access windows, treated as always public). Fail fast on `ERROR` only.
