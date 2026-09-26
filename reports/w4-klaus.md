# Wave 4: Klaus data from the survey export KL-20260926-0946

Branch `w4/klaus`. Input: `C:\Users\rapha\CampusMaps\KL-20260926-0946.zip` (read only, not in git). Everything ran on the JVM, no device.

## What changed

- `app/src/main/assets/buildings/KL.json` replaced: the old 17-node guess is gone, the file now follows the survey export. The converter
  draft (`ConvertMain --keep`) was kept in the scratchpad, not in the repo.
- `app/src/main/assets/anchors/KL/KL-A01.jpg`, `KL-A02.jpg`, `KL-A03.jpg` and `app/src/main/assets/anchors/anchors.imgdb` (KL-A01 only).
- `anchors/SCORES.md`: all nine Klaus photos scored.
- `docs/19`: the Klaus section is now "measured 2026-09-26".
- Tests that hard-coded the guessed layout (`R-1116`, `H2 > H3 > H4 > W1`, `E-N`, `EL-2`) now use the measured one: `RouterTest`,
  `BuildingDataTest`, `ValidatorReportTest`, `KlausRefreshTest`, app `CoreBridgeTest`, `EntranceGeoTest`, `CoreRouterTest`.
  `:core:test` and `:app:testDebugUnitTest` pass (the 60 s CS gap test stays skipped).

## Klaus in plain words

- **Floor 1.** You come in through the **Research Wing door** on the south side (`E-RWD`, GPS fix, faces out south). A corridor runs
  north. After 10 m you reach the foot of the **glass staircase** (`H1`); the atrium opens to the right there. Keep going straight
  8.5 m and **Room 1116W** (Seminar Room West) is on your right (`R-1116W`, the Demo A room). Turn right at the staircase instead
  and you cross the **atrium** (the sponsor tables are here in the video), pass the doorway into the carpeted elevator wing after
  23 m (`H2`) and reach the **elevator** 18.5 m later (`EL-1`).
- **Floor 3.** Out of the elevator (`EL-3`) the stairs are 7 m away (`ST-3`). A 32 m corridor leads to a corner (`H3`); 10 m past it
  is the **COEUS lab, room 3361** (`R-3361`).
- **Stairs.** The glass staircase goes from the atrium up toward the elevators (two floors in 54 steps), so the floor-3 stair node
  is not above the floor-1 one. Floor 2 has only the stair landing (`ST-2`, placed half way).
- **Our table and the starts.** The survey did not name S1, S2 or T. I put them in the atrium, 1 m beside the measured walk from the
  staircase to the elevator: `S1` 8 m from the staircase, `S2` 16 m, the table `T` between them (4.1 and 5.0 m away). All three are
  flagged `estimated`. Move them when the table is assigned.
- **Numbers from the survey.** Walking speed 1.36 m/s (three videos), stairs up 21.6 s per floor and down 17.9 s, elevator 12.4 s
  per floor and a 3.1 s wait (one ride), floor height 4.7 m (barometer).

Hand fixes: two "walks" (#12, #23) were really stair climbs (pressure changes 1 hPa) and had become 40 m hallway edges between floors;
they are now stair timings. Walk #14 (±48°) is split at the corner to COEUS, which the W02 video shows. Walks #18 and #22 are kept
straight (the first wandered while the plaque was photographed, the second is only 6 m). Walk #10 is split at the elevator-wing
doorway seen in the W01 video. At both rooms the saved heading and the sign photo disagree by 180°; the door directions follow the
videos (1116W faces west into the corridor, COEUS faces south-west).

## Demo A routes (router, Saturday 14:00)

| Start | Route | ETA | Spoken instructions |
|---|---|---|---|
| S1 | S1 > H1 > R-1116W | 12 s, 17 m, 1 turn | Head toward the glass staircase (8 m). Turn right at the glass staircase (8 m). Room 1116W is on your right. |
| S2 | S2 > H1 > R-1116W | 18 s, 24 m, 1 turn | Head toward the glass staircase (16 m). Turn right at the glass staircase (8 m). Room 1116W is on your right. |

Both are well under 60 m. The card label says "1 min" (rounded up). S1 to COEUS also works: 84 s by elevator (76 m) or 84 s by the
stairs (55 m); it is not a demo destination because it is over 60 m.

## Anchors and the image database

| Anchor | What | Kind | Scores (straight / far / angle) |
|---|---|---|---|
| KL-A01 | KLAUS ADVANCED COMPUTING BUILDING / RESEARCH WING sign over the door, outdoors | image | 55 / 70 / 50; facade crop, greyscale, equalised: **100** |
| KL-A02 | COEUS door and 3361 LAB plaque, floor 3 | text | 0 / 0 / 0 |
| KL-A03 | 1116W SEMINAR ROOM WEST doors and sign | text | fails / fails / fails |
| KL-A05 | placeholder at the glass staircase | image, photo pending | not photographed |

**`anchors.imgdb` exists** with one image, KL-A01 (width 4.80 m: the owner's estimated 3 m sign width scaled to the crop). It is outdoors,
so it confirms the door but cannot localize anyone in the atrium. The app keeps working without the file (`loc/AnchorImages.kt`
falls back when it is missing). With the file present, the run-time database from jpg files is no longer built, so the CS images and
the KL text-anchor pictures are not image targets (they all failed anyway).

## Validator

KL: 0 errors, 1 warning (KL-A05 photo pending), 1 info (no posted hours at the Research Wing door). Rule 8 passes on both Demo A
routes only because of the A05 placeholder at the staircase; without it the S2 route has 24 m without an anchor. CS and CSE unchanged.

## What the owner should re-survey (about 15 minutes)

1. **Our table** once assigned: Place `T`, waypoints `S1` and `S2` within 10 m, a walk from each to the glass staircase.
2. **A05**: a poster or board by the glass staircase or in the atrium, straight-on photo, width taped. It must score 75 or more;
   Demo A has no indoor image anchor without it. Tape the Research Wing sign too (fixes KL-A01's scale).
3. **Door directions** at 1116W and COEUS: stand in the corridor facing the door and save the Place again.
4. The corridor from the staircase to 1116W as its own walk, and the stair node at the first step.
5. Two or three more elevator rides; the posted hours at the Research Wing door; any other entrance you want as an outdoor start.
