# CampusMaps

Indoor AR campus navigation for HackGT 13. Phone (Kotlin, Compose, ARCore via SceneView), Ray-Ban Meta glasses (burst capture, spoken turns), Galaxy Watch (arrows and haptics).

The app shell, screens, design system, shortcuts feature and watch app are by Uyiosa Nehikhuere (OfficialEseosa); the routing core, building data, AR layer, debug tooling and the plan are by Raphael Omorose. How the two were joined: [docs/21-integration-notes.md](docs/21-integration-notes.md).

## Modules

- `app/` phone app (`com.campusmaps`), sources in `app/src/main/java`. Building files in `app/src/main/assets/buildings/{KL,CS,CSE}.json`.
- `core/` pure Kotlin/JVM: building model, loader, validator, router, survey converter. `./gradlew :core:test`.
- `shared/` pure Kotlin: watch step format and haptic patterns shared by `app` and `wear`.
- `wear/` Galaxy Watch app (same applicationId as `app`, so the Wear Data Layer pairs them).

## Docs

- Plan and module docs: [docs/README.md](docs/README.md)
- Design handoff: [CampusMaps-Design-Handoff (1).md](CampusMaps-Design-Handoff%20(1).md)
- Status and open items: [WHATS-LEFT.md](WHATS-LEFT.md)

## Build

```
./gradlew :core:test :shared:test :app:testDebugUnitTest :app:assembleDebug :wear:assembleDebug
```

JDK 21 runs Gradle; modules target JVM 17. Debug overlay: long-press the title on S1 or S1b (disabled in demo mode).
