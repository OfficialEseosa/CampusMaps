# 02. Building Data (the "map")

**Owner:** Data Lead. **Depends on:** data capture ([09-data-capture.md](09-data-capture.md)). **Consumed by:** localization, routing, AR guidance, glasses bridge, watch.

This is the most important non-code artifact we have. One hand-written file per building. If the file is wrong, every module downstream is wrong, and no amount of code fixes it.

## Design goals

- **Writable by hand in an hour** from the anchor log and floor sketch. No tooling required beyond a text editor.
- **Readable by every module** through one Kotlin data model. Nobody parses it twice.
- **Small.** Tens of nodes, not thousands. Position precision of about half a meter is fine.
- **Validated on load.** A bad edge id or a missing anchor node should crash loudly in the debug build, not produce a silent wrong route at judging.

## Coordinate frame

Each building has one **origin point** and one **north direction**, chosen by the Data Lead and written into the file.

- Origin: a physical spot that is easy to find again, for example "the centre of the main entrance doormat". Record it in the file as a human description and a rough latitude and longitude (for the Geospatial hand-off in Demo B).
- Axes: **x** points east-ish along the main hallway, **y** points left of x (north-ish), **z** is floor number (integer), not height. We store floor as an integer and keep a separate per-floor height in metres for the barometer.
- Units: metres. Positions come from step counts multiplied by measured stride, or from a phone measuring app.
- The building frame is never the ARCore world frame. Localization computes the transform between the two whenever it sees an anchor (see [03-localization.md](03-localization.md)).

## Schema

Format: JSON, loaded with kotlinx.serialization (decision in [14-tech-stack.md](14-tech-stack.md)). Stored in `assets/buildings/<code>.json`. Anchor reference photos stored in `assets/anchors/<code>/` and the prebuilt ARCore image database in `assets/anchors/<code>.imgdb`.

```json
{
  "code": "CS",
  "name": "Classroom South",
  "origin": {
    "description": "Centre of the doormat inside the main (north) entrance, ground floor",
    "lat": 33.7530, "lng": -84.3850,
    "headingDeg": 0
  },
  "floorHeightM": 3.8,
  "walkingSpeedMps": 1.3,
  "stairsSecondsPerFloor": 22,
  "elevators": [
    { "id": "ELEV-1", "avgWaitSec": 35, "worstWaitSec": 70, "secondsPerFloor": 6 }
  ],
  "nodes": [
    { "id": "E-N",  "type": "entrance",     "name": "North entrance",  "floor": 1, "x": 0,   "y": 0,
      "access": [ { "days": "Mon-Fri", "open": "07:00", "close": "22:00", "rule": "public" },
                  { "days": "Mon-Fri", "open": "22:00", "close": "07:00", "rule": "card" },
                  { "days": "Sat-Sun", "open": "00:00", "close": "24:00", "rule": "card" } ] },
    { "id": "E-S",  "type": "entrance",     "name": "South entrance",  "floor": 1, "x": 42,  "y": -3 },
    { "id": "H1",   "type": "intersection", "name": "Lobby T-junction", "floor": 1, "x": 6, "y": 0 },
    { "id": "ST-A-1", "type": "stairs",     "name": "Stair A, floor 1", "floor": 1, "x": 12, "y": 4 },
    { "id": "ST-A-3", "type": "stairs",     "name": "Stair A, floor 3", "floor": 3, "x": 12, "y": 4 },
    { "id": "EL-1-1", "type": "elevator",   "name": "Elevator, floor 1", "floor": 1, "x": 20, "y": 0, "elevatorId": "ELEV-1" },
    { "id": "EL-1-3", "type": "elevator",   "name": "Elevator, floor 3", "floor": 3, "x": 20, "y": 0, "elevatorId": "ELEV-1" },
    { "id": "R-312", "type": "room",        "name": "Room 312",        "floor": 3, "x": 30,  "y": 8,
      "doorFacing": "south" }
  ],
  "edges": [
    { "from": "E-N", "to": "H1", "lengthM": 6, "kind": "hallway", "hint": "through the glass doors" },
    { "from": "H1", "to": "ST-A-1", "lengthM": 7.2, "kind": "hallway", "hint": "past the vending machines" },
    { "from": "ST-A-1", "to": "ST-A-3", "lengthM": 0, "kind": "stairs", "floors": 2 },
    { "from": "EL-1-1", "to": "EL-1-3", "lengthM": 0, "kind": "elevator", "floors": 2 },
    { "from": "ST-A-3", "to": "R-312", "lengthM": 19, "kind": "hallway", "hint": "the room is on your right" }
  ],
  "anchors": [
    { "id": "CS-A03", "node": "H1", "kind": "image",
      "image": "anchors/CS/CS-A03.jpg", "widthM": 0.9,
      "x": 6.4, "y": 1.8, "floor": 1, "heightM": 1.6, "facing": "south",
      "text": "CLASSROOM SOUTH DIRECTORY", "description": "Directory board on the north wall of the lobby" },
    { "id": "CS-A07", "node": "R-312", "kind": "text",
      "text": "312", "aliases": ["3l2", "S12"],
      "x": 30.5, "y": 7.8, "floor": 3, "facing": "south",
      "description": "Room plaque right of the door" }
  ]
}
```

### Field notes

**Nodes**
- `type`: `entrance`, `intersection`, `stairs`, `elevator`, `room`, `waypoint` (a bend in a hallway with no decision to make).
- Stairs and elevators are **one node per floor they touch**, connected by a vertical edge. That keeps routing a plain graph problem.
- **Classroom South has four elevators in one bank.** Model the bank as a single elevator node per floor (`EL-1`, `EL-6`, ...) with one `elevators` entry whose `avgWaitSec` is measured by pressing the call button and timing until *any* car opens. Do not model the four cars separately.
- `access` is only on entrances. Time windows are local time, may wrap midnight, and `rule` is `public`, `card` or `closed`. Missing `access` means always public; a time no window covers is closed for everyone. A `card` window is usable by someone who turned on "I carry a PantherCard" (BuzzCard on Georgia Tech); everyone else is routed to a public door. Do not guess these; photograph the posted hours (see [09-data-capture.md](09-data-capture.md)).
- Entrances carry `floor` like any node. **Classroom South has about five: three on floor 1, two on floor 2.** Outdoor entrances also carry `lat`, `lng`, and `headingDeg` for the Geospatial hand-off. The **Library South connection** is an entrance with `indoor: true` (no coordinates, not reachable from the outdoor virtual node) and its own `access` windows following library hours.
- `doorFacing` on rooms tells AR guidance which side of the hallway to put the destination marker.

**Edges**
- Undirected unless `oneWay: true` (an exit-only door, for example).
- `kind`: `hallway`, `door`, `stairs`, `elevator`, `outdoor`. Cost is computed from kind plus length, see [04-routing.md](04-routing.md).
- Vertical edges carry `floors` (how many floors are traversed) and `lengthM: 0`.
- `hint` is optional spoken flavour: "past the vending machines". Keep it under 8 words.

**Anchors**
- `kind: image` anchors go into the ARCore image database and give a full 6-DoF pose. Use them for big flat things: posters, directory boards, framed signs.
- `kind: text` anchors are matched by ML Kit text recognition from the glasses stream (and by the phone as a backup). They give a node, not a pose. Use them for room plaques and small signs.
- One physical sign can be both: list it twice with different `kind`.
- `aliases` lists common OCR misreads to accept.
- `widthM` is required for image anchors (ARCore uses it to estimate distance faster) and must be measured, not estimated.
- `x, y, floor, heightM, facing` are the anchor's own position, not the node's. The node is where the user is assumed to stand when they can read it comfortably.

## Validation rules (run at load, fail fast in debug builds)

1. Every edge `from` and `to` exists in nodes.
2. Every anchor `node` exists.
3. Every node with `type: elevator` has an `elevatorId` that exists.
4. Every vertical edge connects two nodes of the same stairs or elevator id family on different floors.
5. The graph is connected (every node reachable from every entrance).
6. Every room that is a demo destination has at least one anchor within 20 m on its floor.
7. Every image anchor file exists and its ARCore image quality score is at least 75 (checked offline, recorded in the anchor log).
8. Anchors on a demo route are spaced no more than 20 m apart.

## Files to produce

| Building | Code | File | Demo | Nodes (estimate) | Anchors (target) |
|---|---|---|---|---|---|
| Klaus Advanced Computing | KL | `KL.json` | A | 15 to 25 | 8 to 12 on the route |
| Classroom South (GSU) | CS | `CS.json` | B | 50 to 80 (5 entrances on floors 1 and 2, one elevator bank, stairs, floors 1, 2, 6) | 8 to 12 per route, at least 2 per floor used, one at the elevator bank on each of floors 1, 2, 6 |
| Student Center East (GSU) | CSE | `CSE.json` | C | 10 to 20 | 4 to 6 on the short route, text anchors preferred |

## Work plan at the event

| Hour | Task |
|---|---|
| 0 to 2 | Klaus capture (if not done before), anchor log complete for all three |
| 2 to 4 | Data model in Kotlin, loader, validator, unit test that loads all three files |
| 2 to 6 | Write CS.json first (longest), then CSE.json, then KL.json. Each file is a commit. |
| 6 to 10 | Run every anchor image through the ARCore image quality tool, replace anything under 75 |
| Ongoing | Corrections as walkers report wrong distances or hints |

## Decisions still open

- Whether `x, y` are measured from the origin directly or as offsets along the hallway. Recommendation: hallway offsets, converted to `x, y` when writing the file, because that is how people count steps.
- Whether Classroom South needs the outdoor sidewalk nodes for the Geospatial leg (needs lat and lng per outdoor node). Only if Demo B keeps the outdoor leg.
