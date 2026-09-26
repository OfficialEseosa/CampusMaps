# CampusMaps (HackGT 13)

Indoor AR campus navigation. Native Kotlin Android on a Galaxy S25 Ultra, Ray-Ban Meta Gen 2 glasses (camera bursts and speech), Galaxy Watch 8 Classic (arrows and haptics). Event: Friday September 25, 8:00 PM to Sunday September 27, 8:00 AM, 2026. Expo Sunday 9:30 AM in the Klaus Atrium. Track: The Lighthouse Laboratory (Immersive).

## Layout
- Base is the teammate's structure (integration branch, see [docs/21-integration-notes.md](docs/21-integration-notes.md)): modules `core`, `app`, `shared`, `wear`.
- `docs/` is the plan: [docs/README.md](docs/README.md) is the index, [docs/NEXT-STEPS.md](docs/NEXT-STEPS.md) is the to-do list, research with sources is in `docs/research/`. [WHATS-LEFT.md](WHATS-LEFT.md) is the teammate's status list, updated at integration.
- `app/` is the phone app (applicationId `com.campusmaps`). **Sources are in `app/src/main/java`, not `kotlin/`.** Routing goes through `route/CoreRouter.kt` (core in, the teammate's `RoutePlan` / `Route` / `RouteStep` out); buildings through `data/campus/CoreBridge.kt` (core JSON in, drawing view out, y flipped to y-down). Screens are in `ui/screens`, guidance in `guidance/`.
- `core/` is pure Kotlin/JVM: building data model, loader, validator, routing, survey converter. It owns the package `com.campusmaps.routing`; the app's route view models live in `com.campusmaps.route`.
- `shared/` is the watch step format and haptics; `wear/` is the Wear OS app.
- `survey-app/` is the separate CampusSurvey tool (own Git repo, ignored by this repo).
- Decisions live in [docs/15-open-questions.md](docs/15-open-questions.md) section C. Do not reopen them without a reason.
- Each module has its own doc (02 to 08) with a schema, a work plan, and a cut order.

## Rules
- **CampusSurvey** ([survey-app/](survey-app/), spec in [docs/16-survey-app.md](docs/16-survey-app.md)) is a separate pre-event data-collection app. It has its own application id and Git history, emits a raw observation log (never building JSON), and **no code ever moves from it into CampusMaps**. It is disclosed on Devpost as a tool, not submitted.
- Versions are fixed in `gradle/libs.versions.toml` (the teammate's catalog; integration bumps are listed at the end of [docs/14-tech-stack.md](docs/14-tech-stack.md)). Never upgrade during the event.
- Everything on-device. No network in the demo path except the outdoor Geospatial leg.
- `core/` has no Android dependencies; keep it JVM-testable (`./gradlew :core:test`).
- Routing takes `now` as a parameter; never read the system clock inside routing.
- Glasses: camera and audio never overlap (toolkit limitation). Burst capture, stop, speak.
- Commit after every working step. Tag `routing-works`, `demo-a-works`, `freeze`.
- When something breaks, the report has five parts: steps, expected vs actual, full Logcat or build error text, device state (glasses and watch connected?), a screenshot for visual issues.

## Conventions
- Building codes: KL (Klaus), CS (Classroom South), CSE (Student Center East). Files `app/src/main/assets/buildings/<code>.json`. The app's `Building.id` is the code.
- Anchor ids `<code>-A<nn>`; node ids short and typed (`E-N`, `H1`, `EL-6`, `R-608`).
- Units are metres and seconds everywhere. Floors are integers.
- Windows build laptop. Use `arcoreimg.exe`, PowerShell paths. Android SDK at `C:/Users/rapha/AppData/Local/Android/Sdk`, JDK 21 (Temurin), emulator AVD `survey36` (API 36).
