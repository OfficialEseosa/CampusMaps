# Anchor image scores (arcoreimg, ARCore SDK 1.56.0)

Tool: `arcoreimg.exe` from google-ar/arcore-android-sdk tag v1.56.0 (`tools/arcoreimg/windows`), kept outside the repo at
`C:\Users\rapha\tools\arcoreimg\arcoreimg.exe`. Threshold: 75 (docs/02 rule 7, docs/17 section 5). Scored 2026-09-25 22:40 EDT.

| Image | Anchor | Score | Kept |
|---|---|---|---|
| CS/CS-A01.jpg (1600x541, greyscale) | CS-A01, CLASSROOM SOUTH sign over the Walters main door | fail: "Failed to get enough keypoints from target image" (below 0) | no |
| CS/CS-A08.jpg (1600x329, greyscale) | CS-A08, CLASSROOM SOUTH canopy sign, floor-2 main entrance | fail: same message | no |
| CSE/* | see "Student Center East" below (2026-09-26) | - | - |

## Klaus (survey export KL-20260926-0946, scored 2026-09-26)

Every photo downscaled to 1200x1600 (colour, JPEG 85) before scoring. A05 was not photographed.

| Image | Anchor | Score | Kept |
|---|---|---|---|
| KL-A01-straight | KL-A01, KLAUS ADVANCED COMPUTING BUILDING / RESEARCH WING sign over the Research Wing doors (outdoors) | 55 | no |
| KL-A01-far | same | 70 | no |
| KL-A01-angle | same | 50 | no |
| **KL/KL-A01.jpg** = straight shot cropped to the facade above the paving (top 60 %: curtain wall, sign, canopy, four doors), greyscale, histogram-equalised, 1600x1280 | KL-A01 | **100** | **yes, in anchors.imgdb (widthM 4.80)** |
| KL-A02-straight | KL-A02, COEUS door and 3361 LAB plaque (floor 3) | 0 | no (text anchor; KL/KL-A02.jpg is the straight shot, kept as a hint picture) |
| KL-A02-far | same | 0 | no |
| KL-A02-angle | same | 0 | no |
| KL-A03-straight | KL-A03, 1116W SEMINAR ROOM WEST double doors and sign | fail: not enough keypoints | no (text anchor; KL/KL-A03.jpg is the straight shot, kept as a hint picture) |
| KL-A03-far | same | fail | no |
| KL-A03-angle | same | fail | no |

Also tried (scratch, not kept): A01 straight band without the curtain wall (0), sign alone (fail), facade crop greyscale only (60), autocontrast (55);
A01 far facade crop equalised (100, the straight one was kept); A01 whole photo equalised (100, rejected: the brick paving is not in the sign's
plane); A02 door crop equalised (20); A03 sign panel crop, plain and equalised (fail, 0). The equalised facade wins because the window
mullions and door frames give corners once the contrast is stretched. The width is the owner's estimated 300 cm for the sign (999 of
1600 px), so widthM = 3.0 x 1600/999 = 4.80; tape it to fix the scale. Tree shadows cross the facade in the morning: check on the
phone at the demo hour.

`anchors.imgdb` (5 KB) held one image, `KL-A01`, width 4.80 m (now three: KL-A01, CS-A09, CSE-A02, see "Student Center East" below), built with:

```powershell
# image_list.txt:  KL-A01|app\src\main\assets\anchors\KL\KL-A01.jpg|4.80
& $exe build-db --input_image_list_path=image_list.txt --output_db_path=app\src\main\assets\anchors\anchors.imgdb
```

Now that the file exists, the app loads it and no longer builds a run-time database from the jpg files (`loc/AnchorImages.kt`); the
CS images and the KL text-anchor pictures are therefore not image targets. Delete `anchors.imgdb` to fall back to the run-time path.

Also tried (scratch copies, not kept): A01 tight crop to the sign panel (fail), A01 tight crop with autocontrast (fail),
A01 histogram-equalised (score 0), A08 autocontrast (fail), A08 equalised (fail). The sign is a plain panel with thin
lettering on a flat facade; there are not enough corners for ARCore. Tool sanity check: a busy app screenshot scores 0 and
another fails, so the tool itself runs.

**Result (2026-09-25): no CS image passes.** Klaus below: one passes (KL-A01), so `anchors.imgdb` exists since 2026-09-26.

## CS-A01 (2026-09-26 export)

Export CS-20260926-1445, scored 2026-09-26 about 15:00. **The three photos filed under CS-A01 are not the CLASSROOM SOUTH sign.**
They show a free-standing roll-up banner indoors (#SLEEVEUPGSU vaccine poster, Georgia State logo), about 7 m from the place the
export calls "150 Entrance", floor 1: width 100 cm, centre 180 cm high, facing 350 deg. The export's OCR text is the banner's wording.

| Image | Score | Kept |
|---|---|---|
| straight, whole photo, colour 1200x1600 | 95 | no |
| far, whole photo, colour 1200x1600 | 60 | no |
| angle, whole photo, colour 1200x1600 | 70 | no |
| straight, whole, colour 1024 / 1600 wide | 100 / 100 | no |
| straight, whole, greyscale 1200 | 85 | no |
| straight, whole, greyscale equalised 1200 | 85 | no |
| straight, whole, colour or greyscale at JPEG 80 | 80 / 80 | no |
| straight, tight crop to the banner, colour 1024 / 1200 / 1600 | 20 / 20 / 20 | no |
| straight, tight crop, greyscale 1024 / 1200 / 1600 | 20 / 20 / 20 | no |
| straight, tight crop, greyscale equalised 1024 / 1200 / 1600 | 65 / 75 / 25 | no |
| far, tight crop, colour 1024 / 1200 / 1600 | 35 / 40 / 35 | no |
| far, tight crop, greyscale 1024 / 1200 / 1600 | 35 / 40 / 35 | no |
| far, tight crop, greyscale equalised 1024 / 1200 / 1600 | 90 / 85 / 85 | no |
| straight, banner plus a small margin of wall, colour 1200 | 0 | no |
| **straight, banner plus a small margin, greyscale equalised, 1200x1787 (306 KB)** | **100** (twice) | candidate, see below |

Scores jump between neighbouring variants (the same crop gives 0 in colour and 100 equalised), so trust only the phone test.

**Nothing changed in this folder's images or in `anchors.imgdb`.** Naming the banner `CS-A01` would tell the app the user is at the
outdoor Walters sign (node E-WM, width 3.29 m): wrong place and a scale three times too big. The sign itself still has no image that
passes (see the 2026-09-25 table above), so CS-A01 stays a text anchor.

The banner is a good indoor image target (it scores 100) but it can be moved or taken away. To use it, someone who owns `CS.json`
adds a new image anchor (for example `CS-A09`, node near "150 Entrance", widthM 1.00), then:

```powershell
Copy-Item reports\w7-csanchor-banner.jpg app\src\main\assets\anchors\CS\CS-A09.jpg
# image_list.txt:
#   KL-A01|app\src\main\assets\anchors\KL\KL-A01.jpg|4.80
#   CS-A09|app\src\main\assets\anchors\CS\CS-A09.jpg|1.00
& $exe build-db --input_image_list_path=image_list.txt --output_db_path=app\src\main\assets\anchors\anchors.imgdb
```

Tested in a scratch folder: that list builds a 9.2 KB database (the current one-image file is 5.3 KB) and both names `KL-A01` and
`CS-A09` are in it. arcoreimg has no list command (actions: build-db, eval-db, eval-img), so the check was the file size and a
search of the file for the two names.

## Student Center East (survey export CSE-20260926-1530, scored 2026-09-26)

arcoreimg 1.56.0 eval-img, threshold 75. Each photo is 3060x4080; variants downscaled with LANCZOS, JPEG 85.

| Image | Anchor | Score | Kept |
|---|---|---|---|
| A01 straight / far / angle, whole, colour 1200x1600 | CSE-A01, SPEAKER'S AUDITORIUM lettering on a plain wall | 0 / 0 / 0 | no |
| A01 straight / far / angle, whole, greyscale 1200 | same | 0 / 0 / 0 | no |
| A01 straight / far / angle, whole, greyscale equalised 1200 | same | 0 / 0 / 20 | no |
| A01 straight, crop to the lettering band, colour and greyscale 1024 / 1200 / 1600 | same | fail (not enough keypoints) | no |
| A01 straight, same crop, greyscale equalised 1024 / 1200 / 1600 | same | 0 / 0 / 0 | no |
| A02 straight / far / angle, whole, colour 1200x1600 | CSE-A02, SEPTEMBER MOVIES @ CINEFEST poster tower in the lobby | 35 / 45 / 40 | no |
| A02 straight / far / angle, whole, greyscale 1200 | same | 35 / 45 / 40 | no |
| A02 straight / far / angle, whole, greyscale equalised 1200 | same | 80 / 65 / 60 | no (floor and lobby dominate) |
| A02 straight and far, tight crop to the tower, colour, grey, equalised, 1024 / 1200 / 1600 | same | 0 everywhere | no |
| A02 straight, tower plus a margin of lobby, colour 1024 / 1200 / 1600 | same | 45 / 50 / 50 | no |
| A02 straight, tower plus a margin, greyscale 1024 / 1200 / 1600 | same | 45 / 50 / 50 | no |
| A02 straight, tower plus a margin, greyscale equalised 1024 / 1200 | same | 45 / 40 | no |
| **CSE/CSE-A02.jpg** = straight, tower plus a margin, greyscale equalised, 860x1600 | CSE-A02 | **80** (twice) | **yes, in anchors.imgdb (widthM 1.02)** |

- `CSE/CSE-A01.jpg` is the straight shot (colour 1200x1600), kept as a hint picture; CSE-A01 is a text anchor (OCR reads the lettering
  as "SPEA ER'S AUDITC RIUM": thin light letters on a grey wall, like the CLASSROOM SOUTH sign).
- CSE-A02 width: the export says 50 cm (estimated); the tower is about 420 of 860 px, so widthM = 0.5 x 860/420 = 1.02. Tape it.
- The poster advertises films up to September 30 and stands on its own base: it can be moved or replaced. Check it before the demo.
- Scores jump between neighbouring variants (the tight crop scores 0, the wider one 80), so trust only the phone test.

`anchors.imgdb` now holds three images (13.5 KB), built with:

```powershell
# image_list.txt:
#   KL-A01|app\src\main\assets\anchors\KL\KL-A01.jpg|4.80
#   CS-A09|app\src\main\assets\anchors\CS\CS-A09.jpg|1.20
#   CSE-A02|app\src\main\assets\anchors\CSE\CSE-A02.jpg|1.02
& $exe build-db --input_image_list_path=image_list.txt --output_db_path=app\src\main\assets\anchors\anchors.imgdb
```

(build-db also writes `anchors.imgdb-imglist.txt` next to the database; it was deleted, not committed.) The three names were found in the file.

## How the app uses this folder

- `anchors/anchors.imgdb` (one database for all buildings, image names = anchor ids such as `KL-A01`): if present, the AR
  session loads it (`loc/AnchorImages.kt`).
- If it is absent, debug builds build a database at run time from `anchors/<code>/<anchor id>.jpg` (width from the building
  JSON). Images ARCore rejects are skipped and logged under the `AnchorImages` tag. With today's two CS images that
  database is empty, which is logged too.

## Adding an image (for example the Klaus directory board or a high-texture poster)

```powershell
$exe = "C:\Users\rapha\tools\arcoreimg\arcoreimg.exe"
& $exe eval-img --input_image_path=app\src\main\assets\anchors\KL\KL-A01.jpg    # keep only >= 75
# image_list.txt, one line per kept image:  KL-A01|app\src\main\assets\anchors\KL\KL-A01.jpg|0.9
& $exe build-db --input_image_list_path=image_list.txt --output_db_path=app\src\main\assets\anchors\anchors.imgdb
```

Then add a row to the table above. Good candidates: posters, directory boards, photos, logos with many corners.
Plain name signs and room plaques stay text anchors (OCR).
