# w7 Classroom South refresh: Walters side door to Room 150 (2026-09-26)

Input: the survey export `CS-20260926-1445.zip` (read-only, not copied in). Merged by hand into `CS.json`; no full conversion.

## What the export measured (stride 0.768 m)

| Walk | Steps | Metres | Heading (mean, spread) |
|---|---|---|---|
| #2 Mini stairs > Outside turn | 1 | 0.77 | 126, 9 (outdoor steps, about 1 m down) |
| #4 Outside turn > Walters side | 12 | 9.22 | 203, 24 (outdoors, along the building wall) |
| #6 Walters side > 150 Entrance | 24 | 18.44 | 215, 37 (spread = turning round at the door; video shows a straight corridor) |
| #8 150 Entrance > (no end) "to the sign" | 11 | 8.45 | 72, 14 |
| Video "Mini stairs to 150" | door > 150 doors about 27 | about 20.7 | 200 to 230 |

The "mini stairs" are outdoor steps on the path to the side door (80 000 lux, video frames), not the short steps inside the main lobby.

## What changed in CS.json

- `R-150` moved to 18.44 m from `E-WS` (walk #6) on the owner's straight corridor line (bearing 216.4): 7.2 m nearer the side door than the owner's on-site spot. The walk wins on length, the owner on direction. `H7`>`R-150` 21.1 > 12.44 m. Edge lengths equal the drawn distances.
- `E-WS`, `E-WM`, `H7`, `H1`, their edges, and the hint "ahead, then down the short steps on your left" are unchanged: no walk from the main door. The new `E-WS` fix (5.2 m north-east of Walters main) is again off the facade, so the owner's placement stays; noted.
- `CS-A03` (150 plaque) moved with `R-150`, 1 m in front of the doors.
- New image anchor `CS-A09`: the vaccine roll-up banner (the export called it CS-A01; it is not the fascia sign). Node `H7`, floor 1, text `VACCINE` plus the banner's words, widthM 1.2 (the 1.0 m banner fills about 1000 of 1200 px), facing 350 as measured (not the perpendicular 306), heightM 1.3, position estimated 7 m before `R-150`, 1.2 m to the left wall. Image `anchors/CS/CS-A09.jpg` = the anchor agent's banner file (arcoreimg 100). `anchors.imgdb` rebuilt with KL-A01 (4.80) and CS-A09 (1.20), 9.2 KB. I used 1.20, not 1.00: the image is wider than the banner.
- Hint on `H7`>`R-150`: "past the vaccine sign" (the turn text plus hint has an 11-word limit; "on your left" did not fit).

## Routes (core router, Saturday 14:00)

- Walters side > 150: `E-WS > H7 > R-150`, 18.4 m. "Head toward bottom of the short steps" / "Room 150 is ahead" (18.4 m).
- Walters main > 150: `E-WM > H7 > R-150`, 24.8 m. "Head toward bottom of the short steps" / "Turn right at bottom of the short steps, past the vaccine sign" (12.4 m) / "Room 150 is ahead" (24.8 m).
- From P2: via Walters main, 54.8 m, 43 s. From P1: via Walters side, 95.4 m, 74 s.
- Seen: the `E-WM`>`H7` hint is never spoken (the start step ignores edge hints). This was already the case before this change.

## Tests

`:core:test :app:testDebugUnitTest` green. `ValidatorReportTest` known rule 8 error is now "31.6 m without an anchor between H7 and H2" (was 44.0 m from E-WM). No ETA pins changed.

## Still estimated

`E-WS` position, `H7`, `E-WM`>`H7` 12.4 m (never walked), `H1` and its edges, door-to-150 length (walk 18.4 m against video 20.7 m), all of CS-A09's placement and its height. Next visit: walk from the revolving door to the bottom of the short steps, with a place saved there.
