# w7 PantherCard door sign in AR

Branch `w7/cardsign`. A floating sign at the door that needs a PantherCard, drawn like the red "Room 150" post and label at the destination.

## What is drawn
- A thin post, 1.3 m tall, standing on the door.
- A label 1.6 m above the ground, 1.2 m wide and 0.3 m tall. It always turns to face the camera, like the destination label.
- Text is white. Two looks:
  - **Amber** "Tap your PantherCard" ("Tap your BuzzCard" on Georgia Tech): the user said they carry the card, and the route goes through a door that is card-only right now.
  - **Red** "PantherCard required": the route still ends at the door named in the Heads up notice. Without the card the router normally goes around that door, so this is rare. It only shows if the screen passes the notice in: `state.toArRouteInput(lockedNotice)`. GuidanceScreen does not pass it today, so on the phone only the amber sign appears.
- No sign when the route's door is public.

## Where and when
- **Outdoor leg (Geospatial arrows on):** the sign stands on the entrance's terrain anchor, the same point the clay chevrons walk toward. So it floats at the door while you are still outside. It shows only when those chevrons show: Earth tracking, accuracy good enough, and the anchor resolved.
- **Indoor, once the route is placed:** the sign stands on the entrance node, at that node's floor height.
- **Neither:** the sign is parked far below the floor, out of view.
- **The sign is never removed from the scene once it has appeared** in this AR view. This is the same Filament rule as the destination label: a text node must not be re-created. Distance, floor, leg and tracking changes only move it. If the new route has no card door, the sign is parked too.

## What the owner should see at Student Center East, sim time Sat 21:00
- **Settings "I carry a PantherCard" ON:** the route goes through the Main entrance. Walking up to it outside, an amber "Tap your PantherCard" label floats about 1.6 m up at the Main entrance on a thin amber post. The chevrons point into its foot. After the hand-over and placement, the same label stands on the Main entrance node, right behind you as you walk in.
- **Setting OFF:** the route uses the West entrance and no sign is drawn. The Heads up banner is unchanged.
- **Outdoor sign needs the Geospatial arrows to work** (API key, location, VPS). Without them, the sign appears only once the route is placed indoors.
- CSE.json is being rewritten by another agent. The sign needs the Main entrance's `card` window (Sat 20:00 to 23:00) to stay in the file.

## Files
- `ui/ar/RouteArrows.kt`: `ArDoorSign`, `DoorSignTone`, and `ArRouteInput.cardDoor`.
- `ui/ar/ArInputsAdapter.kt`: `cardDoorSign(route, lockedNotice)`. It reads the "Walk to" step's card name (set by CoreRouter when `cardNeeded`) and places the sign on that step's entrance point.
- `ui/ar/CardDoorSign.kt` (new): `CardDoorPlacement.foot` (terrain anchor first, then the building transform, else parked) and the `CardDoorSignNodes` composable.
- `ui/ar/ArGuidanceView.kt`: 2 lines. The sign is drawn at the ARScene top level, in world space.
- `geo/ArCoreGeospatialProvider.kt` (additive): `OutdoorArrowFrame.door` is the terrain anchor position, published with the chevrons.
- Test `ui/ar/CardDoorSignTest.kt`:
  - Classroom South with every outdoor door made card-only in memory, Sat 21:00, with the card: the sign is at the entrance node, says "Tap your PantherCard" and is amber.
  - Plain CS: no sign.
  - Red sign with a matching notice.
  - Placement order.

## Verified
- `:app:assembleDebug :app:testDebugUnitTest` pass (4 new tests).
- **Not seen on a device.** The emulator has no ARCore.

## Things to check on the phone
- **Label text width.** The label bitmap is fixed at 512 x 128 px, and the font is 40 px so "Tap your PantherCard" fits on one line. If it is clipped, lower `fontSize` in CardDoorSign.kt.
- **Changing the card setting while S2 is open.** If the text or colour changes mid-view, SceneView updates the label in place (setText). That was not tested on Filament. In normal use the text is fixed for the route.
