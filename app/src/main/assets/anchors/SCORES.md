# Anchor image scores (arcoreimg, ARCore SDK 1.56.0)

Tool: `arcoreimg.exe` from google-ar/arcore-android-sdk tag v1.56.0 (`tools/arcoreimg/windows`), kept outside the repo at
`C:\Users\rapha\tools\arcoreimg\arcoreimg.exe`. Threshold: 75 (docs/02 rule 7, docs/17 section 5). Scored 2026-09-25 22:40 EDT.

| Image | Anchor | Score | Kept |
|---|---|---|---|
| CS/CS-A01.jpg (1600x541, greyscale) | CS-A01, CLASSROOM SOUTH sign over the Walters main door | fail: "Failed to get enough keypoints from target image" (below 0) | no |
| CS/CS-A08.jpg (1600x329, greyscale) | CS-A08, CLASSROOM SOUTH canopy sign, floor-2 main entrance | fail: same message | no |
| CSE/* | none: no image files exist yet | - | - |

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

`anchors.imgdb` (5 KB) holds one image, `KL-A01`, width 4.80 m, built with:

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
