# w7 CS anchor photo (2026-09-26)

Input: the owner's export `C:\Users\rapha\CampusMaps\CS-20260926-1445.zip` (not copied into the repo).

## Main finding

The three photos saved as CS-A01 (straight, far, angle) are **not the CLASSROOM SOUTH sign over the Walters door**. They show a
free-standing roll-up banner indoors (#SLEEVEUPGSU vaccine poster with the Georgia State logo), about 7 m from the place the export
calls "150 Entrance", floor 1. The export records width 100 cm, centre height 180 cm, facing 350 deg, note "this is to the sign".
The OCR text in the export is the banner's wording, which confirms it.

## Scores (arcoreimg 1.56.0 eval-img, threshold 75)

| Variant | Score |
|---|---|
| straight / far / angle, whole photo, colour 1200x1600 | 95 / 60 / 70 |
| straight, whole, colour 1024 and 1600 wide | 100 / 100 |
| straight, whole, greyscale; greyscale equalised (1200) | 85 / 85 |
| straight, tight crop to the banner: colour, grey, equalised (1024 / 1200 / 1600) | 20-20-20, 20-20-20, 65-75-25 |
| far, tight crop: colour, grey, equalised (1024 / 1200 / 1600) | 35-40-35, 35-40-35, 90-85-85 |
| straight, banner plus a small wall margin, colour 1200 | 0 |
| **straight, banner plus margin, greyscale equalised, 1200x1787** | **100** (checked twice) |

Full table in `app/src/main/assets/anchors/SCORES.md`, section "CS-A01 (2026-09-26 export)".

## What I did and did not change

- `anchors.imgdb` is **unchanged**: still one image, KL-A01 (5.3 KB). `anchors/CS/CS-A01.jpg` is unchanged.
- Reason: putting the banner in the database as `CS-A01` would make the app think the user is at the outdoor Walters sign (node
  E-WM, width 3.29 m in `CS.json`): wrong place, and a scale three times too big. The old photo is the right subject, so it stays.
- **The CLASSROOM SOUTH sign still cannot be an image anchor.** Nothing new of the sign was photographed; its old crops all failed.
- The best banner image is kept outside the app assets as `reports/w7-csanchor-banner.jpg` (306 KB, scores 100), ready to adopt.

## To use the banner (needs someone who owns CS.json)

Add an image anchor, for example `CS-A09`, at a node near "150 Entrance", floor 1, widthM 1.00, heightM 1.8, facing about 350 deg
(north). It is a movable banner: check it is still there before the demo. Then:

```powershell
$exe = "C:\Users\rapha\tools\arcoreimg\arcoreimg.exe"
Copy-Item reports\w7-csanchor-banner.jpg app\src\main\assets\anchors\CS\CS-A09.jpg
# image_list.txt:
#   KL-A01|app\src\main\assets\anchors\KL\KL-A01.jpg|4.80
#   CS-A09|app\src\main\assets\anchors\CS\CS-A09.jpg|1.00
& $exe build-db --input_image_list_path=image_list.txt --output_db_path=app\src\main\assets\anchors\anchors.imgdb
```

I ran exactly this list in a scratch folder: `build-db` wrote a 9.2 KB database containing both names `KL-A01` and `CS-A09`
(arcoreimg has no list command, so the check was file size plus a search of the file for the names).

Commands used for scoring: `arcoreimg.exe eval-img --input_image_path=<file>` on each variant; variants made with Pillow 12.3
(`ImageOps.exif_transpose`, crop, LANCZOS resize, `grayscale`, `equalize`, JPEG quality 85).

## Tests

`./gradlew :app:testDebugUnitTest --tests "*Anchor*" -Dorg.gradle.jvmargs=-Xmx2g --max-workers=4 -q`: passes (exit 0). The app assets did not change, so nothing else was run.
