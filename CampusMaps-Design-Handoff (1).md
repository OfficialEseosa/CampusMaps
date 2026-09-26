# CampusMaps Design Handoff for Claude Code

Visual reference (design canvas): https://claude.ai/artifact/WowLtFmDPYTDRWCibym24V

This file tells you how to style the CampusMaps Android app. The app logic, routing and AR already exist or are being built separately. Your job is to apply this design: colors, type, shapes, layouts, copy and states.

## 0. Ground rules

* **Stack:** Kotlin, Jetpack Compose, Material 3. Use standard M3 components (Card, Switch, SegmentedButton, FilterChip, ModalBottomSheet, Button, OutlinedButton). Custom `Canvas` drawing is fine for the AR overlay, minimap and watch arrow.
* **Target device:** Galaxy S25 Ultra, portrait only (locked). Design size is **412 x 915 dp**. Every number in this file is dp (layout) or sp (text).
* **Offline:** fonts are bundled as TTF in `res/font`. Nothing is downloaded at runtime.
* **Touch targets:** at least 48 dp. Main buttons are 56 to 60 dp tall.
* **Units:** metres and seconds everywhere. Never feet, never "minutes only".
* **No hidden gestures for judges.** Everything tappable must look tappable. The only hidden gesture is the debug long press, and demo mode turns it off.
* **Code style:** keep code simple and heavily commented with plain English explanations. No emojis anywhere, including logs.

## 1. Theme files

Create these files under `ui/theme/`.

### 1.1 Color.kt (Material 3 roles, light and dark)

```kotlin
package com.campusmaps.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Light scheme: warm "clay" brand. Used by S1, S1b and Settings in light mode.
val LightColors = lightColorScheme(
    primary = Color(0xFFA85F33),              // Route button, Done, selected chips, switches
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DDCC),     // Best route card, selected destination row
    onPrimaryContainer = Color(0xFF3A1A08),
    secondary = Color(0xFF6E5A4E),            // Method icon on other route cards
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1E4DA),   // Icon tiles on non best cards
    onSecondaryContainer = Color(0xFF2B1D15),
    tertiary = Color(0xFF2F6B7A),             // Locked entrance banner icon
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3ECF2),    // Locked banner and simulated time chip background
    onTertiaryContainer = Color(0xFF0F3540),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF231A15),
    surface = Color(0xFFFFF8F4),              // Screen background
    onSurface = Color(0xFF231A15),
    surfaceVariant = Color(0xFFEFE2D9),
    onSurfaceVariant = Color(0xFF5A4F48),     // Secondary text, section labels
    surfaceContainer = Color(0xFFF5EBE4),     // Non best route cards, list rows
    surfaceContainerHigh = Color(0xFFEFE2D9), // Search field, switch track when off
    outline = Color(0xFF85746A),
    outlineVariant = Color(0xFFD8C8BE),       // Card borders
    error = Color(0xFFB3261E),                // "No route" text only
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

// Dark scheme: same roles, tuned for night recordings.
val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF512300),
    primaryContainer = Color(0xFF7E4424),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFDDC2B3),
    onSecondary = Color(0xFF3E2D23),
    secondaryContainer = Color(0xFF55443A),
    onSecondaryContainer = Color(0xFFFADDCD),
    tertiary = Color(0xFF9CD0DE),
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF124D5A),
    onTertiaryContainer = Color(0xFFD3ECF2),
    background = Color(0xFF1A120E),
    onBackground = Color(0xFFF0DFD6),
    surface = Color(0xFF1A120E),
    onSurface = Color(0xFFF0DFD6),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF322823),
    outline = Color(0xFFA08D82),
    outlineVariant = Color(0xFF52443C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
```

### 1.2 ArOverlayColors.kt (fixed set, never follows light or dark)

S2, S3 and the debug card always use these, because they sit on a camera image or pure black.

```kotlin
package com.campusmaps.ui.theme

import androidx.compose.ui.graphics.Color

// Colors for anything drawn over the camera or on the black glasses screen.
// These do NOT change with the system theme.
object ArOverlayColors {
    val scrim = Color(0xE6141110)          // Instruction banner, 90% opaque
    val scrimMap = Color(0xD1141110)       // Minimap background, about 82% opaque
    val text = Color(0xFFFFFFFF)           // Main instruction text
    val textMuted = Color(0xFFD7CCC4)      // Distance and "Then:" line
    val arrowCore = Color(0xFFFFB27A)      // Fill of AR chevrons, next turn arrow, route line on map
    val arrowEdge = Color(0xFFC0612A)      // Outline and glow of the same
    val user = Color(0xFF4C8DF6)           // User dot, floor badge
    val destination = Color(0xFFF9F2ED)    // Pin label background
    val destinationRing = Color(0xFFC67C4E)// Destination ring on map and in AR
    val arrived = Color(0xFF7BC98A)        // "You have arrived" banner, arrived pin
    val onArrived = Color(0xFF10301A)      // Text and icons on the arrived banner
    val attention = Color(0xFFFFC247)      // Locate prompt border, reroute chip
    val onAttention = Color(0xFF2A1E00)
    val stop = Color(0xFFD84A3A)           // S3 Stop button
    val glassesMuted = Color(0xFFCFD8DC)   // S3 secondary text (never darker than 0xFFB0BEC5)
    val glassesOutline = Color(0xFFB0BEC5)

    // Map drawing
    val mapCorridor = Color(0xFF6B625C)

    // Debug card
    val debugCard = Color(0xF2101010)      // 95% opaque
    val debugHeader = Color(0xFFFFD54F)
    val debugLink = Color(0xFF90CAF9)
}
```

### 1.3 Type.kt (one family: Sora, bundled)

Download Sora (Regular 400, Medium 500, SemiBold 600, Bold 700, ExtraBold 800) from Google Fonts and put the TTF files in `res/font` as `sora_regular.ttf`, `sora_medium.ttf`, `sora_semibold.ttf`, `sora_bold.ttf`, `sora_extrabold.ttf`.

```kotlin
package com.campusmaps.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.campusmaps.R

// The one and only font family in the app.
val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_medium, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
    Font(R.font.sora_extrabold, FontWeight.ExtraBold),
)

// Map Sora onto the Material 3 type scale.
val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp), // "Where to?", "To Room 608"
    headlineMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),   // App bar title
    titleMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),  // Room name, entrance name
    bodyLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),  // Switch labels
    bodyMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),   // Primary buttons
    labelMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),  // Section labels
    labelSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp),   // "Fastest" tag, badges
)

// Extra styles that are bigger than the M3 scale. Use these by name.
object AppTextStyles {
    val etaBest = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-1).sp)   // S1b best card ETA
    val etaOther = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.8).sp)// S1b other card ETA (minimum 28)
    val arInstruction = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp)  // S2 banner (minimum 28)
    val arDistance = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)                           // "in 16 m"
    val arThen = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 15.sp)                                 // "Then: ..."
    val arrivedHeadline = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
    val glassesInstruction = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp) // S3 (minimum 30)
    val glassesNext = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 29.sp)
    val floorBadge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
    val debugMono = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
}
```

### 1.4 Shapes and spacing

```kotlin
package com.campusmaps.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Small tags ("Fastest", simulated time chip)
    small = RoundedCornerShape(12.dp),       // Icon tiles
    medium = RoundedCornerShape(16.dp),      // Destination rows, banners
    large = RoundedCornerShape(20.dp),       // Route cards, AR banner
    extraLarge = RoundedCornerShape(28.dp),  // Bottom sheet, pill buttons
)

// Spacing tokens. Use these instead of random numbers.
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp   // Screen side padding on S1 and S1b
    val xxl = 28.dp  // Bottom padding above the primary button
}
```

### 1.5 Theme.kt

```kotlin
package com.campusmaps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// Wrap S1, S1b and Settings in this. It follows the system light/dark setting.
// S2, S3 and the debug card ignore it and use ArOverlayColors directly.
@Composable
fun CampusMapsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
```

## 2. Icons

Use Material Symbols Rounded (one style only). Add `androidx.compose.material:material-icons-extended` or import the Material Symbols vector drawables.

| Use | Symbol |
|---|---|
| Turn left / right | `turn_left`, `turn_right` |
| Straight | `straight` |
| U turn | `u_turn_left` |
| Elevator (method icon + instruction) | `elevator` |
| Stairs | `stairs` |
| No floor change ("LEVEL") | `trending_flat` |
| Door / entrance | `door_front` |
| Locked entrance | `lock` |
| Destination row, start picker | `location_on` |
| Locate me | `my_location` |
| Sign | `signpost` |
| Glasses | `eyeglasses` |
| Avoid stairs | `accessible` |
| Speak | `volume_up` |
| Repeat | `replay` |
| Stop | `stop` |
| Arrived, selected | `check_circle`, `check` |
| Settings | `settings` |
| Reset | `restart_alt` |
| Simulated time | `schedule` |
| Minimap enlarge | `open_in_full` |
| End route | `close` |
| Back | `arrow_back` |
| Search | `search` |
| Seen (S3) | `visibility` |
| Add a shortcut | `add_road` |
| Photo | `photo_camera` |
| Add photo | `add_a_photo` |
| Pending review | `hourglass_top` |
| Approved | `verified` |
| Rejected | `block` |

Stairs and elevator must differ in shape, not just color. The icons above do.

## 3. Screen flow

```
S1 Destination --"Route to Room X"--> S1b Route options --tap a card--> S2 AR guidance --last node--> Arrived (state of S2)
                                            |                               |
                                            |                        "End route" -> S1b
                                            |
                                   "Guide me with glasses" --> S3 Glasses mode --Stop--> S1b

Arrived: "Done" -> S1 (reset for next judge), "Back to routes" -> S1b
S1 "Add a shortcut" row --> S4 Add a shortcut --Submit--> S4 (submission appears as "Pending review")
Approved shortcuts appear as a "Student shortcut" card on S1b (see section 16)
Settings: ModalBottomSheet from the app bar, any screen
Debug overlay: long press on app title, disabled in demo mode
Watch: separate Wear OS app, mirrors the current step
```

Theme rule: S1, S1b and Settings follow the system theme. S2, S3 and the debug card are always dark. In demo mode, consider forcing S1b dark so the jump into S2 is not harsh on video.

## 4. S1 Destination

Background: `surface`. Side padding 20 dp. Vertical gap between blocks 18 dp.

**App bar** (64 dp tall, sits under the status bar):
* Left: title "CampusMaps" (`titleLarge`) plus a building code tag ("CS") on `primaryContainer`, 11 sp bold, 8 dp corners. In demo mode add a "DEMO" tag next to it.
* Right: two 48 dp icon buttons, Reset (`restart_alt`) and Settings (`settings`), tinted `onSurfaceVariant`. Reset stays quiet on purpose so judges do not tap it.
* Long press on the title toggles the debug overlay (disabled in demo mode).

**Content, top to bottom:**
1. Heading "Where to?" (`headlineLarge`).
2. Building selector: M3 `SingleChoiceSegmentedButtonRow`, 3 segments, 52 dp tall, full width, pill outline in `outline`. Use full names: "Klaus", "Classroom South", "Student Center East" (12 sp, wraps to 2 lines if needed). Selected segment: `primaryContainer` fill with a check icon.
3. Search field: 56 dp tall, fully rounded, `surfaceContainerHigh` fill, no border, search icon on the left. Placeholder "Search room number or name". Shows up to 5 hits; keyboard search key picks the top hit. Hidden in demo mode (layout must still look complete without it).
4. Section label "Recent and demo destinations" (`labelMedium`, `onSurfaceVariant`). In demo mode the label is "Demo destinations".
5. Destination rows: each a card at least 64 dp tall, 16 dp corners, 12 dp gap between rows.
   * Left: 40 dp icon tile with `door_front`.
   * Middle: room name (`titleMedium`), then floor line like "Floor 6 · Classroom South" (`bodySmall`).
   * Unselected: `surfaceContainer` fill, 1 dp `outlineVariant` border, icon tile `secondaryContainer`.
   * Selected: `primaryContainer` fill, 2 dp `primary` border, icon tile `primary` with white icon, trailing "Selected" with a check.
   * Empty: "None in this building file".
6. Section label "Where are you?" then a 56 dp outlined row: `location_on` in `primary`, text "Outside: P1 Decatur St side", trailing dropdown chevron. Later shows the live position from sign recognition.
7. Avoid stairs row: 56 dp tall, `accessible` icon, label "Avoid stairs" (`bodyLarge`), M3 `Switch` on the right. Persisted.
8. "Add a shortcut" row (hidden in demo mode): 56 dp text row, `add_road` icon in `primary`, label "Found a faster way? Add a shortcut", trailing chevron. Opens S4 (section 16).
9. Route error (only when needed): red text in `error`, for example "No route to Room 220: every entrance is card-only at Sat 23:00."
10. Primary button, pinned to the bottom with 28 dp bottom padding: full width, 58 dp tall, fully rounded, `primary`. Text "Route to Room 608". Disabled text "Pick a destination".

## 5. S1b Route options

Background: `surface`. Side padding 20 dp. Gap 14 dp.

**App bar:** back arrow (48 dp) on the left, title "Route options", Reset and Settings on the right.

**Header:**
* "To Room 608" (`headlineMedium`).
* "Floor 6 · Classroom South" (`bodyMedium`, `onSurfaceVariant`).
* "From: Outside, P1 Decatur St side" (same style).
* Simulated time chip: 32 dp tall, `tertiaryContainer`, `schedule` icon, text "Routed for Sat 21:00 (simulated)". Only shown when the clock is simulated. It must look honest, not like a hack.

**Locked entrance banner** (only when a door was skipped):
* 16 dp corners, `tertiaryContainer` fill, `lock` icon in `tertiary`, 14 sp text.
* Copy: "**Heads up:** Main entrance is card-only now. Using West entrance instead." Fallback: "...Taking another way."
* This is information, not an error. Never red.

**Avoid stairs row:** same as S1. Flipping it recomputes the cards live. Animate the change (cross fade or `animateItemPlacement` reorder, about 250 ms) so it reads on video.

**Route cards** (1 to 3, best first). The whole card is the tap target and opens S2.
* Best card: `primaryContainer` fill, 2 dp `primary` border, 20 dp corners, soft shadow (elevation 4). Padding 16 dp.
  * Left: 44 dp icon tile, `primary` fill, white method icon (elevator / stairs / trending_flat).
  * Middle: "Fastest" tag (`labelSmall`, white on `primary`, 8 dp corners), entrance name (`titleMedium`, minimum 16 sp), "Enter on floor 1 · by elevator" (`bodySmall`).
  * Right: ETA `AppTextStyles.etaBest`, for example "2:14".
  * Bottom line: "90 m walk, elevator, avg wait 35 s", then "also via: 95 Decatur Street entrance" in `onSurfaceVariant`.
* Other cards: `surfaceContainer` fill, 1 dp `outlineVariant` border, icon tile `secondaryContainer` with `secondary` icon, ETA `AppTextStyles.etaOther`. Bottom line shows the difference: "+47 s: same walk, 5 floors of stairs", "+1:39: 71 m more walking, 5 floors of stairs".
* The best card must stand out by more than color: tag, border, size and elevation.
* Approved student shortcut card (section 16): same layout as other cards, plus a "Student shortcut" tag (`labelSmall`, `onTertiaryContainer` on `tertiaryContainer`, `add_road` icon). If it is the fastest, it becomes the best card and shows both tags.
* From an inside start the title is "From here" or "From here, by elevator". If start equals destination: one card "You are already here".

**No route empty state:** centered `lock` or `signpost` icon, the sentence in `error`, and one button "Pick another room" back to S1.

**Glasses button:** pinned at the bottom, full width, 56 dp, `OutlinedButton`, `eyeglasses` icon, text "Guide me with glasses", text color `primary`.

## 6. S2 AR guidance (hero screen)

Full screen ARCore camera. Everything else floats on top. Always uses `ArOverlayColors`. Keep the **middle 60% of the screen clear** for the floor arrows.

**Top row** (top 44 dp, sides 12 dp, 48 dp tall):
* Left: reroute chip, only after a reroute. 36 dp, `attention` fill, `onAttention` text, `replay` icon, "Rerouted (1)". Fades out after about 3 s.
* Right: "End route" button, 48 dp tall pill, `scrim` fill, white `close` icon and text. Goes back to S1b.

**Instruction banner** (below the top row, sides 12 dp, 22 dp corners, `scrim` fill, padding 16 x 18):
* Row: 60 dp turn icon tile (18 dp corners, `arrowCore` fill, icon in dark `0xFF141110`, 34 dp icon) then the instruction in `AppTextStyles.arInstruction` white, for example "Turn left at Atrium north", and under it "in 16 m" in `arDistance`, `textMuted`.
* 1 dp divider at 14% white.
* "Then: Room 608 is on your right" in `arThen`, `textMuted`.
* The icon in the banner must match the 3D next turn arrow in shape and color.
* Never put text directly on the camera image. Always on the scrim.

**AR world objects** (see section 9 for exact geometry):
* Chevron chain on the floor every 1.5 m, fading out beyond 15 m.
* Larger next turn arrow at the decision point, bent in the turn direction.
* Destination pin with room label, 1.5 m above the floor at the door.

**Bottom row** (bottom 28 dp, sides 12 dp):
* Left: minimap, 170 x 170 dp, 18 dp corners, `scrimMap` fill. Corridors `mapCorridor` 10 dp stroke, route `arrowCore` 4 dp, user dot `user` with white 2 dp ring and a soft halo, destination ring `destinationRing`. Floor label "F6" bottom left, 11 sp bold `textMuted`. A visible 32 dp "enlarge" button (`open_in_full`) in its top right corner. Tap enlarges to half screen.
* Right column, bottom aligned: optional Locate prompt (below) above a 64 dp round floor badge, `user` fill, 3 dp white border, "F1" in `floorBadge`. During an elevator ride the badge counts up.

**Locate me state** (confidence below 0.5, not arrived):
* Arrows fade out over 400 ms (no flash). They fade back in when confidence recovers.
* Prompt card, 190 dp wide, 18 dp corners, `scrim` fill, 2 dp `attention` border: `my_location` icon plus "Locate me" in `attention` (15 sp ExtraBold), body "Point at a sign so I can find where you are." (13 sp), and a small example sign chip (green rectangle "ROOM 608") with "like this".
* When a sign is being read, draw amber corner brackets around it and a small amber pill "Reading sign…".
* When starting outside, the banner uses a compact version: 48 dp `door_front` tile, "Walk to Library South entrance", "in 90 m", and a round 48 dp close button.

**AR unavailable state:** keep the banner and show a larger minimap. Text: "AR unavailable on this device. Follow the text and the map." It must look intentional, not broken.

## 7. Arrived (state of S2 and S3)

* Banner turns `arrived`: 22 dp corners, padding 18. 60 dp round check tile (`onArrived` fill, `arrived` check). "You have arrived" (`arrivedHeadline`, `onArrived`), then "Room 608 is on your right" (16 sp SemiBold). If the door side is unknown show only "You have arrived at Room 608" (do not repeat "You have arrived" twice).
* The AR pin switches to a green `arrived` circle with a dark check, still labelled "Room 608".
* Bottom: small minimap (120 dp) and floor badge, then:
  * **Done** (primary): full width, 60 dp, fully rounded, `LightColors.primary` fill, white check plus "Done". Resets to S1.
  * **Back to routes**: full width, 56 dp, `scrim` fill, 1.5 dp border at 40% white. Goes to S1b.
* No confetti. Confident color change and a clear check.

## 8. S3 Glasses mode

Pure black `0xFF000000`. Stays dark in both themes. Padding 20 dp sides, 48 dp top. Filmed, so very high contrast. No text darker than `0xFFB0BEC5`.

1. Header row: "GLASSES MODE" (15 sp ExtraBold, 2 sp letter spacing) and a connection chip on the right: green dot plus "Glasses connected" (`arrived` text on `onArrived` fill). Not connected: grey outline chip "Not connected".
2. Seen line: `visibility` icon, "Seen: ELEVATORS" with the sign text in white bold. Empty: "Seen: nothing yet".
3. Instruction: 72 dp icon tile (`arrowCore` fill, black icon) plus `glassesInstruction`, for example "Turn left at Elevator lobby".
4. "Next: Take the elevator to floor 2" in `glassesNext`, `glassesMuted`. On arrival this line becomes the door side.
5. Live thumbnail: 190 dp tall, 18 dp corners, dashed `glassesOutline` border when empty, shows the last still from the glasses. "Hide" pill in the top right.
6. Row: "Floor 1" (22 sp ExtraBold) on the left, cycle indicator on the right: pills "Looking", "Recognising", "Speaking". Active pill is white fill with black text and a `volume_up` icon; inactive pills are outlined. Camera and audio never overlap.
7. Bottom: two buttons side by side, each half width, 80 dp tall, 22 dp corners.
   * Repeat: black fill, 2 dp white border, `replay` icon, "Repeat".
   * Stop: `stop` red fill, white `stop` icon, "Stop". Goes to S1b.
8. Debug only: "Fake step" button, hidden in demo mode.

## 9. AR objects (for the 3D team)

**Chevron (floor arrow)**
* Flat chevron lying on the floor, pointing forward along the path.
* Size: 0.3 m wide, 0.5 m long, arm thickness about 0.12 m (40% of the length in the top view).
* Repeated every 1.5 m along the route, world locked.
* Fill `arrowCore` 0xFFFFB27A, edge stroke `arrowEdge` 0xFFC0612A, plus an emissive glow in `arrowEdge`.
* Opacity 100% up to 10 m, fading linearly to 0% at 15 m.
* Hidden when tracking is lost (fade 400 ms).
* No pure white (blows out on camera). Must read on dark carpet and bright atrium floors.

Top down SVG (units in metres x 1000, so 300 wide and 500 long):

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 500" width="300" height="500">
  <!-- Chevron pointing up (forward). Light core, darker edge. -->
  <polygon points="0,300 150,0 300,300 300,500 150,200 0,500"
           fill="#FFB27A" stroke="#C0612A" stroke-width="24" stroke-linejoin="round"/>
</svg>
```

**Next turn arrow**
* Larger arrow at the decision point, about 0.5 m wide shaft, bent 90 degrees toward the turn.
* Variants: left, right, straight, U turn, elevator (up arrow), stairs (up or down arrow).
* Same colors and glow as the chevron.

**Destination pin**
* Ring 0.4 m across, stroke `destinationRing` 0xFFC67C4E with glow, center dot `destination` 0xFFF9F2ED.
* Floats 1.5 m above the floor at the door, always faces the camera (billboard).
* Label pill above it: `destination` fill, dark text `0xFF313131`, room name like "Room 608". If the pin is cut, the label alone is fine.
* Arrived state: ring becomes a filled `arrived` circle with a dark check.

## 10. Settings sheet

`ModalBottomSheet` from the app bar. Team only. Keep it tidy, no extra depth. Rows 56 dp.

* Title "Settings".
* "Speak instructions" switch, with status text "TTS: ready".
* "Avoid stairs" switch (same preference as S1 and S1b).
* "Demo mode" switch, supporting text "Hides debug, keeps screen on, demo destinations only".
* "Demo building" segmented button: Klaus, Classroom South, Student Center East.
* "Reset demo" text button.
* Hint text (hidden in demo mode): "Long press the title to toggle the debug overlay."

## 11. Debug overlay

Draggable card over S2. Background `debugCard` (95% opaque so S2 text does not bleed through), 12 dp corners. Header "DEBUG" in `debugHeader`, links in `debugLink`, body in `AppTextStyles.debugMono` white. Collapses to one line. Hidden completely in demo mode.

## 12. Watch face (Wear OS, Galaxy Watch)

Separate app, Compose for Wear OS Material 3. One screen, display only, nothing tappable.

* Pure black background (AMOLED). Stay inside the round safe area.
* One big arrow filling most of the face, relative to the body (not world locked). Readable at arm's length in under half a second.
* Under it: one short fact (26 sp ExtraBold white) and a small label (14 sp, `glassesMuted`).
* Ambient mode: same arrow as a 2 dp white outline only.

| Step type | Icon and color | Big text | Label | Haptic (ms on/off) |
|---|---|---|---|---|
| Straight | `straight`, arrowCore | "10 m" | place name | 80 |
| Turn left | `turn_left`, arrowCore | "10 m" | "Atrium north" | 80, 80, 80 |
| Turn right | `turn_right`, arrowCore | "10 m" | place name | 80, 80, 80, 80, 80 |
| Stairs | `stairs`, arrowCore | "Floor 3" | "Stairs up" | 300, 100, 80 |
| Elevator | `elevator`, arrowCore | "Floor 6" | "Elevator" | 300, 100, 300 |
| Door | `door_front`, arrowCore | entrance name | "Go through" | 80, 100, 300 |
| Locked | `lock`, attention | "West entrance" | "Main locked" | 80, 60, 80, 60, 80, 60, 80 |
| Arrived | `check`, arrived | "Room 608" | "Arrived" | 600 |

```kotlin
// Haptic patterns for the watch. Each array alternates ON, OFF, ON... in milliseconds.
// Pass to VibrationEffect.createWaveform(timings, -1) after prepending a 0 ms start delay.
object WatchHaptics {
    val straight = longArrayOf(80)
    val left = longArrayOf(80, 80, 80)
    val right = longArrayOf(80, 80, 80, 80, 80)
    val stairs = longArrayOf(300, 100, 80)
    val elevator = longArrayOf(300, 100, 300)
    val door = longArrayOf(80, 100, 300)
    val locked = longArrayOf(80, 60, 80, 60, 80, 60, 80)
    val arrive = longArrayOf(600)
}
```

## 13. Copy and formats

Instructions come from the router as templates. Style the templates, do not hardcode sentences.

| Type | Template |
|---|---|
| Start outside | Walk to {entrance} |
| Entrance | Go through {entrance}[, {hint}] |
| Start inside | Head toward / Continue toward / Turn left toward {place} |
| Turn | Turn left / Turn right / Turn around at {place}[, {hint}] |
| Elevator | Take the elevator to floor {n} |
| Stairs | Take the stairs up/down {one, two...} floor(s) |
| After floor change | Exit toward {place} |
| Door | Go through the door |
| Locked | Heads up: {entrance} is card-only now. Using {other} instead. |
| Arrive | {room} is on your left/right/ahead/behind, or You have arrived at {room} |
| Already there | You are at {room} |

* S2 prefixes the next step with "Then:". S3 uses "Next:".
* Distance: "in 16 m".
* ETA: m:ss, rounded ("0:44", "2:14"). Unknown: "--:--".
* ETA difference: seconds under a minute ("+47 s"), otherwise m:ss ("+1:39").
* Floors: "F1" on the S2 badge and minimap, "Floor 1" everywhere else.
* Voice: calm, specific, landmark first. "Heads up" is as loud as it gets.

## 14. Accessibility and video checklist

* Text contrast at least 4.5:1 (3:1 for 24 sp and up). The colors above already pass.
* Every tappable thing at least 48 dp and visibly a button.
* Nothing relies on color alone: best card has a tag and border, stairs vs elevator differ in shape, locked uses a lock icon.
* S1b ETA at least 28 sp and entrance name at least 16 sp so they read in a small 1080p recording.
* All state changes (arrows hiding, cards reordering, reroute chip) fade over 250 to 400 ms. No flashes.

## 15. Out of scope for now

Onboarding and permission explainers, full accounts and sign in (section 16 uses a device ID instead), deeper settings, outdoor (Geospatial) screens, the replay tool, multiple building search.

## 16. S4 Add a shortcut (user submitted routes)

Students can record a faster way between two places, attach photos along the way, and send it to the dev team. Nothing goes live until a developer approves it. Hidden in demo mode.

### 16.1 Identity (for now)

No sign in yet. Each install generates a random UUID on first launch and stores it in DataStore. Every submission carries that `deviceId` so the app can list "Your submissions". Keep this behind a small `SubmitterIdProvider` interface so real sign in can replace it later without touching the screens.

```kotlin
// Gives each install a stable anonymous ID. Swap this class out when sign in exists.
interface SubmitterIdProvider {
    suspend fun currentId(): String
}
```

### 16.2 Screen layout

Theme: follows the system theme like S1. Background `surface`. Side padding 20 dp, gap 18 dp.

1. **App bar:** back arrow (48 dp) to S1, title "Add a shortcut".
2. **Intro text** (`bodyMedium`, `onSurfaceVariant`): "Found a faster way? Walk it, snap a few photos, and our team reviews it before it goes live for everyone."
3. **Progress steps:** three steps in a row joined by 2 dp lines. Labels: "Walk", "Photos", "Submit".
   * Done: 26 dp circle, `onSurface` fill, white check.
   * Current: 26 dp circle, `primary` fill, white number.
   * Upcoming: 26 dp circle, 1 dp `outlineVariant` border, number in `onSurfaceVariant`.
   * Line after a done step is `onSurface`, otherwise `outlineVariant`.
4. **Step 1, Walk:** two fields side by side, labels "From" and "To", 48 dp tall, 12 dp corners, `outlineVariant` border. Both pick from known places or rooms (same search as S1). Under them a big button "Start recording walk" (56 dp, `primary`). While recording: a live distance and time readout ("84 m · 1:05") and a "Stop recording" outlined button. The walk is saved as a list of positions from the existing localization.
5. **Step 2, Photos:** label "Photos along the way" with counter "2 of 6" on the right. Grid of 3 columns, 96 dp tall tiles, 14 dp corners, 10 dp gap.
   * Filled tile: the photo, cropped to fill, small remove button (32 dp) in the corner.
   * Add tile: dashed 1.5 dp `primary` border, `add_a_photo` icon in `primary`, contentDescription "Add photo".
   * Minimum 2 photos, maximum 6. Each photo is tagged with the position where it was taken.
6. **Optional note:** single text field "Anything we should know? (optional)", for example "Door is unlocked until 6 pm".
7. **Submit button:** full width, 56 dp, `primary`, text "Submit for review". Disabled until From, To, the recorded walk and at least 2 photos exist. Disabled text "Add 2 photos to submit".
8. **Your submissions:** label "Your submissions" then one row per submission, 14 dp corners, `surfaceContainer` fill, 1 dp `outlineVariant` border, padding 14 dp.
   * Left: name (13 sp Bold, for example "Library cut through") and route line ("Library South to Langdale Hall", 11 sp `onSurfaceVariant`).
   * Right: status pill (see 16.3).
   * Empty: "Nothing submitted yet."

After submitting, show a snackbar: "Sent for review. We'll show it to everyone once it's approved." and add the row with "Pending review".

### 16.3 Status pills

| Status | Pill | Colors |
|---|---|---|
| Pending review | `hourglass_top` + "Pending review" | `primaryContainer` fill, `onPrimaryContainer` text |
| Approved | `verified` + "Approved" | `onSurface` fill, `surface` text |
| Rejected | `block` + "Not approved" | `errorContainer` fill, `onErrorContainer` text |

Pills are 28 dp tall, fully rounded, 11 sp Bold. A rejected row can show one line of reviewer feedback under it in `onSurfaceVariant`.

### 16.4 Data model

```kotlin
// One shortcut a student submitted. Stored on the backend, cached on the phone.
data class ShortcutSubmission(
    val id: String,                    // UUID made on the phone
    val submitterId: String,           // From SubmitterIdProvider (device ID for now)
    val name: String,                  // Short title, e.g. "Library cut through"
    val fromNodeId: String,            // Start place or room node
    val toNodeId: String,              // End place or room node
    val path: List<PathPoint>,         // Recorded walk, in order
    val photos: List<ShortcutPhoto>,   // 2 to 6 photos
    val note: String?,                 // Optional tip from the student
    val status: ShortcutStatus,        // PENDING, APPROVED or REJECTED
    val reviewerNote: String?,         // Why it was rejected, if it was
    val createdAt: Long,               // Epoch millis
)

data class PathPoint(val x: Double, val y: Double, val floor: Int, val timeMs: Long)

data class ShortcutPhoto(val uri: String, val atPointIndex: Int) // Which path point the photo was taken at

enum class ShortcutStatus { PENDING, APPROVED, REJECTED }
```

### 16.5 How approval works

* Submissions go to a backend review queue. Developers approve or reject them (a simple admin page is enough; it is not part of the phone app).
* When approved, the backend adds the path as extra edges in that building's route graph, marked `source = STUDENT`.
* The phone pulls approved shortcuts on launch and when the building file loads. The router then treats them like any other edge.
* On S1b an approved shortcut shows as a normal route card with the "Student shortcut" tag (section 5).
* The submitter's row flips from "Pending review" to "Approved", and the phone can post a notification: "Your shortcut Library cut through is live."

### 16.6 Rules

* Hidden in demo mode (judges never see it).
* Needs camera permission for photos. If denied, show "Camera permission needed to add photos" with a button that opens system settings.
* Photos are compressed to about 1600 px on the long side before upload.
* Works offline: submissions are queued locally and sent when the network is back. Show "Waiting to upload" on the row until then.
