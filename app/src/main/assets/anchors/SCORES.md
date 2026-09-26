# Anchor image scores (arcoreimg, ARCore SDK 1.56.0)

Tool: `arcoreimg.exe` from google-ar/arcore-android-sdk tag v1.56.0 (`tools/arcoreimg/windows`), kept outside the repo at
`C:\Users\rapha\tools\arcoreimg\arcoreimg.exe`. Threshold: 75 (docs/02 rule 7, docs/17 section 5). Scored 2026-09-25 22:40 EDT.

| Image | Anchor | Score | Kept |
|---|---|---|---|
| CS/CS-A01.jpg (1600x541, greyscale) | CS-A01, CLASSROOM SOUTH sign over the Walters main door | fail: "Failed to get enough keypoints from target image" (below 0) | no |
| CS/CS-A08.jpg (1600x329, greyscale) | CS-A08, CLASSROOM SOUTH canopy sign, floor-2 main entrance | fail: same message | no |
| KL/*, CSE/* | none: no image files exist yet (KL-A01, KL-A02, KL-A05 are referenced in KL.json but not captured) | - | - |

Also tried (scratch copies, not kept): A01 tight crop to the sign panel (fail), A01 tight crop with autocontrast (fail),
A01 histogram-equalised (score 0), A08 autocontrast (fail), A08 equalised (fail). The sign is a plain panel with thin
lettering on a flat facade; there are not enough corners for ARCore. Tool sanity check: a busy app screenshot scores 0 and
another fails, so the tool itself runs.

**Result: no image passes, so there is no `anchors.imgdb` yet.** The app code is ready for one (see below).

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
