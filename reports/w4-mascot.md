# w4 mascot watermark

Branch `w4/mascot`. The big faded "GT" / "GSU" letters behind the S0 campus cards and the S0b header are now the mascots: Buzz for Georgia Tech, the panther head for Georgia State.

## What changed
- `app/src/main/res/drawable-nodpi/campus_mark_gt.png` (736 x 1024, 141 KB) and `campus_mark_gsu.png` (1024 x 842, 113 KB). They hold alpha only (colour is plain white); the app tints them.
- `data/campus/Campuses.kt`: `Campus.markRes` (`@DrawableRes Int`), set to the two drawables, with a comment that the marks belong to the universities and are used as decoration only.
- `ui/screens/CampusScreen.kt`: `CampusGhostCode` (letters) replaced by `CampusGhostMark`: an `Image` of `painterResource(markRes)`, `ContentScale.Fit`, `ColorFilter.tint(p.ghost)`, `contentDescription = null`. Anchored bottom-right like the letters were and pushed past the card edge. It is drawn first, so the name, chips and arrow sit on top of it.
  - On the card it is 66% of the card height, offset (18 dp, 30 dp).
  - On the S0b header (`BuildingsScreen.kt`) it is 150 dp tall, offset (10 dp, 22 dp).
- Alpha: `p.ghost` is the exact colour the letters used, and its alpha is built in: navy at 0x17 (9%) on gold, white at 0x14 (8%) on blue. So the opacity is the same as before. I did not add a separate alpha on top.

## Processing (Pillow 12.3, script not committed)
- GT: I converted the webp sheet to PNG with ffmpeg, then cropped Buzz with hands on hips at x 720 to 1180, y 120 to 570. I painted the TM mark white (around x 1005 to 1040, y 525 to 555 on the sheet).
- GSU: `1.png` is 500 x 424 with a transparent background. I cleared the registered mark (x 325 to 365, y 355 to 395).
- Alpha = "ink amount" = (1 - luminance) x gain, times the source alpha. The gain is 1/0.75 for the panther and 1/0.85 for Buzz. White becomes fully transparent, so the teeth, eyes, wing fill and face details show as cut-outs. For Buzz, navy comes out near full alpha and gold at about 45%, so the body stripes still show inside the silhouette.
- Each mark is trimmed to its bounds and scaled with LANCZOS to 1024 px on the long side. The panther source is only 500 px, so its 1024 px version is an upscale; it is soft but does not show at 8% alpha.

## Screenshots (`reports/shots-w4-mascot/`, 360 px wide)
The emulator's default is 1080 x 2400 at 420 dpi, which is 411 dp wide. The requested `wm size 1080x2340` alone keeps the width at 411 dp, so for the 360 dp shots I also ran `wm density 480`. Both are reset now.
1. `1-s0-light-411dp.png`: S0, both cards
2. `2-s0b-gt-light-411dp.png`
3. `3-s0b-gsu-light-411dp.png`
4. `4-s0-dark-411dp.png`: S0 with the system in dark mode
5. `5-s0-dark-360dp.png`
6. `6-s0b-gt-dark-360dp.png`

## Things to know
- S0 and S0b use fixed campus skins and ignore the system dark theme, so the dark and light shots look the same. That is how it worked before this change.
- To reach S0 on the emulator I turned on Demo mode in Settings and revoked location. With a location fix near campus, the app opens on the Explore map instead of S0. Demo mode is still on in emulator-5558.
- On S0b, Buzz's antennae and the panther's ear sit behind the "switch campus" pill. They are faint and behind the pill, and the old letters overlapped the same area.
- The campus name and subtitle stay clear of the mark at both widths. At 360 dp the cards are shorter, so the mascots are smaller (they scale with the card).
- Buzz is narrow (aspect about 0.72), so on the card he fills less width than "GT" did. If he should read bigger, raise the 0.66 height fraction for GT only.
- `CampusGhostCode` was removed; nothing else used it.
