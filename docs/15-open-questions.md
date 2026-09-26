# 15. Open Questions and Decisions

Updated 2026-09-20 after the team lead's answers. Section C is the record of decisions; sections A and B are what is still open.

## A. Questions still open for the team

### Classroom South (rooms 150 and 608)
1. **Floors.** Confirm 150 is on floor 1 and 608 on floor 6 on the site visit. For the two floor-2 entrances: which is the library connection, and is the other one an outdoor door (a bridge or a slope-side door)? Does the elevator bank stop at floor 2?
2. **Start points.** Which two outdoor spots, on different sides of the building, will be P1 and P2? They must make the best entrance for 608 differ by a minute or more. Since all four elevators are in one place, the entrance difference comes from the walk to the elevator bank, so P1 and P2 should be on opposite sides of the building from it. Pick them on the visit with a stopwatch.

### Student Center East
4. On a **Saturday evening**, is either entrance card-access, or is the whole building closed after 8 PM? Photograph the notices and ask the desk this week. This decides whether the redirect is recorded for real or with the simulated-time toggle.
5. What is the short indoor route (start entrance, two or three turns, destination)?

### Klaus
6. Which room or spot off the Atrium is the Demo A destination? Decide Friday between check-in and the opening ceremony, when the Atrium is visible and we know where the expo tables go.

### Setup and logistics
7. Who films at GSU with the second phone, and who stays at Klaus on call?
8. Has the Meta Managed Account organization been started at work.meta.com? If the official CameraAccess sample streams from the glasses with app id `0` in Developer Mode, the org can be skipped. Run the sample this week; that is the real test.
9. The glasses report build `70523680060800100`. The toolkit's minimum is stated as firmware "v126"; the Meta AI app shows a short version under the glasses' settings (Settings → your glasses → About or Software). Note that short number. If the sample app connects, the version is fine regardless.

## B. Answered: does changing the phone time trigger the after-hours rule?

It would, but do not do it. Routing never reads the system clock; it takes `now` as a parameter, and the debug overlay has a **simulated time** switch with a time picker that overrides it (see [04-routing.md](04-routing.md) and [07-app-shell-ui.md](07-app-shell-ui.md)). Set that to 9 PM and the Student Center notice fires at any real time of day. Changing the phone's clock also risks breaking Bluetooth pairing, Google Play Services, certificate checks for the Meta toolkit, and screen-recording timestamps. For honesty, the recording shows the simulated-time chip in the debug overlay, and Devpost says the notice was triggered with a simulated clock because the building closes at 8 PM on Saturdays.

## C. Decisions made (do not reopen without a reason)

Decided 2026-09-20 by the team lead unless noted.

- **Stack:** native Kotlin with Jetpack Compose, no Unity. SceneView 4.38.0 for AR, ARCore 1.54.0 transitively, ML Kit bundled text recognition, kotlinx.serialization, Dijkstra by hand.
- **Demo C:** full glasses-camera burst mode is the primary plan; headset fallback built and ready by Hour 18 (Saturday 2 PM). See [06-glasses-bridge.md](06-glasses-bridge.md).
- **Demo B:** rooms **Classroom South 150 and 608**. Outdoor Geospatial leg stays in the primary plan so nothing outdoors is mapped by hand. Barometer floor display stays, shown during the elevator ride to floor 6.
- **Watch:** kept, still behind the checkpoint in [08-watch-companion.md](08-watch-companion.md).
- **Glasses:** owned by the team lead. Meta AI app version 289.0.0.25.162 (above the 282 minimum for toolkit 0.9.0). Glasses build 70523680060800100 (short version still to be read from the app; see question 9).
- **Classroom South elevators:** four cars in one bank. Modelled as one elevator node per floor.
- **Classroom South entrances:** about five; three on floor 1, two on floor 2, at least one of those from the connected Library South. The library connection is modelled as an indoor entrance with library hours. Entrance floor is shown on every route card.
- **Google Cloud project** for the Geospatial API key: created and owned by the team lead.
- **Build laptop:** Windows. Windows instructions (`arcoreimg.exe`, PowerShell) apply throughout.
- **Printed backup markers** in Klaus are acceptable if real signage scores badly with ARCore's image tool. Disclose on Devpost. The HIVE makerspace is open Saturday 3 to 9 PM for printing.
- **VisionNav:** origin unknown, not confirmed by research; removed from the pitch.
- **Data collection:** starts this week.
- **Team size and roles:** not a concern for planning; the role names in [11-team-and-workflow.md](11-team-and-workflow.md) are labels for responsibilities, not headcount.
- **Event facts (from hack.gt):** hacking Friday 8 PM to Sunday 8 AM; expo Sunday 9:30 to 11 AM in the Klaus Atrium; track is "The Lighthouse Laboratory" (Immersive AR/VR/XR); teams up to four; no past projects; Meta tech talk Saturday 3:30 PM.
- Demo phone: Samsung Galaxy S25 Ultra on stable Android 16, updates off.
- Everything on-device; no network in the demo path except the outdoor Geospatial leg over cellular.

## D. Unverified items from the research, and who checks them

| Item | Why it matters | By when |
|---|---|---|
| S25 Ultra runs ARCore with surface tracking and Geospatial | Demo A and B | Pre-event |
| S25 Ultra and watch expose a barometer; per-floor delta measured in the Classroom South elevator | Demo B | Site visit |
| Three-device Bluetooth test (glasses, watch, phone), TTS on the glasses | Demo C | Pre-event |
| Developer Mode plus `application_id = 0` bypasses Developer Center registration | Glasses on day one | Pre-event |
| Toolkit fps, latency, and the stop → speak → restart cycle time on the S25 Ultra | Demo C design | Pre-event if the sample app is running, else Hour 0 to 2 |
| ARCore image quality scores of real signage in all three buildings | Whole localization approach | Site visits |
| Character height of plaque text in glasses footage versus ML Kit's 16 px floor | Demo C recognition | Site visit |
| Entrance-to-608 timings differ by 60 s or more from P1 and P2 | Demo B | Site visit |
| Student Center East Saturday evening rule per entrance | Demo C | Site visit |
| VPS availability at P1 and P2 | Outdoor leg | Site visit (any ARCore Geospatial sample app) |
| Version set compiles together (AGP 9.4.0, Kotlin 2.4.10, SceneView 4.38.0, Compose BOM 2026.09.00, mwdat 0.9.0) | Hour 0 | Pre-event test project |
| Wi-Fi ADB install to the watch | Watch | Pre-event |
| AI-code disclosure rule and Devpost deadline | Submission | Event packet, opening ceremony |
| Classroom South address (110 vs 95 Decatur St SE) | Trip | Site visit |

## E. Stretch idea logged 2026-09-25: Gemini for semantic recognition

Raphael proposed using the Gemini API to identify room signs, elevators and similar things the camera sees. Decision: not on the critical path. On-device OCR and image anchors stay primary (offline, sub-second). Gemini fits as a Hour 20+ stretch in the glasses burst loop (one still per burst, only when OCR found nothing, over cellular, answer cached for the recording), and as a laptop-side helper that extracts sign text and type from survey photos. Check first whether ML Kit's GenAI Prompt API (Gemini Nano) accepts images on the S25 Ultra; if it does, the same idea works offline. First thing cut if anything slips.
