# w7 PantherCard prompt

Branch `w7/panthercard`. Georgia State students carry a PantherCard that opens card-access doors after hours (Georgia Tech: BuzzCard).

## How it works
- **Door rules.** Each entrance can list time windows with a rule: `public`, `card` or `closed`. Public wins over card. A time that no window covers now counts as **closed** for everyone (before this change it counted as card-only). No windows at all means always public.
- **Setting.** Settings has a new row, "I carry a PantherCard" ("I carry a BuzzCard" on Georgia Tech), with a card icon. It is off by default and saved on the phone, so it survives a force-stop. The row shows on both campuses and its label follows the campus.
- **Routing.** Core `Prefs` now has `hasCard`. If it is on, a door that is card-only right now is used like a public one, and that route option is marked `cardNeeded`. If it is off, nothing changes: the app skips that door and shows "Heads up: Main entrance is card-only now. Using West entrance instead." A closed door stays closed either way, and its notice says "closed".
- **S1 (Where to?).** When a door of this building is card-only right now and the setting is off, one line shows under the building pill: "After hours: bring your PantherCard or we route you to the public door".
- **S1b (Route options).** When the plan went around a card-only door, or goes through one because of the card, a card shows at the top: "Main entrance needs a PantherCard after 8 pm". The time comes from the file: the last public window that closed earlier today.
  - "I have my card" turns the setting on, and the plan recomputes through the Main entrance.
  - "Route me around" plans this one route without the card and hides the card for it. The Heads up banner comes back.
  - When the setting is already on, only "Route me around" is shown.
  - A route through a card door gets a "PantherCard" tag next to FASTEST.
- **S2, glasses and watch.** On the "Walk to / Enter at" step through a card door, the S2 banner under the instruction says "Tap your PantherCard at this door", in the campus colour with a card icon. Near the door the approach text becomes "Tap your PantherCard at the Main entrance": it shows in the banner, is spoken once, and the glasses read the same text. Near the door the watch shows the LOCKED face with the label "PantherCard". Without the card nothing changes.
- **Sim time.** Everything is computed from `app.clock.now()` in the same `trip` flow as the old notice, so it follows the debug sim-time chips.

## What to add when Student Center East is surveyed
For each outdoor entrance, write down the posted hours as windows in `CSE.json`:
- `public` windows for the open hours;
- `card` windows for the hours a PantherCard opens the door;
- nothing (or `closed`) for the hours it is shut to everyone.

Example: `{ "days": "Mon-Sun", "open": "07:00", "close": "23:00", "rule": "card" }`. The Main entrance card window here is inferred (07:00 to 23:00); confirm it on site. Any building works the same way. The code has no per-building settings.

## Files
- core: `data/Model.kt` (AccessRule.CLOSED), `data/Geo.kt` (ruleAt, isClosed, blocks, cardOnlySince), `routing/Router.kt` (Prefs.hasCard, RouteOption.cardNeeded, closed notice), tests `RouterTest`, `BuildingDataTest`.
- data: `app/src/main/assets/buildings/CSE.json` (Main entrance card window), `docs/02-building-data.md` (the rule text).
- app:
  - `data/SettingsRepository.kt`, `data/campus/Campuses.kt` (cardName)
  - `route/CardAccess.kt` (new: S1b decision and S1 hint), `route/CoreRouter.kt`, `route/RouteModels.kt`, `route/RouteText.kt`
  - `ui/MainViewModel.kt` (small: hasCard into the plan, setHasCard, routeAroundCard), `ui/CampusMapsApp.kt`
  - `ui/screens/SettingsSheet.kt`, `RouteOptionsScreen.kt`, `DestinationScreen.kt`, `GuidanceScreen.kt` (2 lines), `CardDoorBanner.kt` (new)
  - `guidance/GuidanceController.kt`, `guidance/GuidanceEngine.kt`, `outdoor/OutdoorRoute.kt`, `outdoor/ExploreViewModel.kt`
  - tests: `CardAccessTest` (new), `CoreRouterTest` (the 23:30 message now says "closed")

## Verified
- JVM: `:core:test :app:assembleDebug :app:testDebugUnitTest` all pass. Checked on CSE:
  - Sat 21:00 without card: West entrance plus the notice.
  - Sat 21:00 with card: Main entrance, cardNeeded, no notice, approach text "Tap your PantherCard at the Main entrance", watch LOCKED face labelled "PantherCard".
  - Sat 14:00: Main entrance, no card needed, no prompt, no hint.
  - Sat 23:30: no route with or without the card.
  - Prompt logic: both buttons when the setting is off; only "Route me around" when it is on; hidden after "Route me around"; hint only when the setting is off.
  - Card name: PantherCard for CSE, BuzzCard for KL.
- Emulator 5554, CSE, sim time Sat 21:00 (screenshots in `shots-w7-panthercard/`):
  - S1 hint (1).
  - S1b card with both buttons (2).
  - "Route me around" brought back the Heads up banner and kept the West entrance.
  - "I have my card" switched the plan to the Main entrance with the PantherCard tag (3).
  - The Settings row stayed on after a force-stop (4). The label read "I carry a BuzzCard" on Klaus.
- **Not seen on the emulator: the S2 banner.** The simulated walker starts at the door, so the Walk-to-entrance step is already done when S2 opens (5 shows step 2). The data behind the banner is covered by the JVM test. Check it on the phone by starting a few metres outside the Main entrance with sim time Sat 21:00.
