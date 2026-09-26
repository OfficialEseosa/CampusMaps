# Research: HackGT 13 rules, prior winners, prior art, venues (2026-09-20)

Compiled by a research agent with web sources. Items marked unverified are open risks; the Demo and Pitch Lead re-checks them when the event packet is published.

## 0. Update 2026-09-20 evening: verified from the hack.gt site bundle

The hack.gt page is JavaScript-rendered; reading its bundle directly gave the real schedule, tracks, and FAQ.

**Schedule (hack.gt, "Schedule" section)**
- Fri Sep 25: Check-in 2 to 4 PM outside Ferst Center; Opening Ceremony 4 to 5 PM, Ferst Theatre; Team Formation 5 to 6 PM; Sponsor Fair 5:30 to 7 PM, Klaus Atrium; Dinner 7 to 8:30 PM, Klaus 1116 W; **Hacking Begins 8:00 PM**; NSA Tech Talk 8:30 to 9:30 PM, Klaus 1456.
- Sat Sep 26: Breakfast 8 to 9:30 AM; Visa, Impiricus, Aramco, Cursor tech talks; **Meta Tech Talk 3:30 to 4:30 PM**; HIVE Makerspace open 3 to 9 PM; Lunch 12 to 2 PM; Dinner 7 to 8:30 PM; Seaside Market and Club Showcase 7 to 10 PM in the Atrium.
- Sun Sep 27: Breakfast 7:30 to 9 AM; **Hacking Ends 8:00 AM**; **Expo 9:30 to 11:00 AM, Klaus Atrium**; Closing Ceremony 12:30 to 1:30 PM, Ferst Theatre.
- Total hacking time: 36 hours.

**Tracks (hack.gt, "Tracks" section, quoted)**
- "Oracle of the Deep": ML / AI / Data Visualization / Analytics Track.
- "The Shipyard": Hardware Track. "Show off your best builds and set sail with robots and smart devices to conquer the seven seas."
- **"The Lighthouse Laboratory": Immersive (AR/VR/XR) Track.** "Explore creating experiences that blur the lines of reality and pull people into another world."
- "A Marina's Mission", presented by Aramco: Social Good (Healthcare, Sustainability) Track.
- Tracks exist "for both General and Emerging participants"; teams apply to Emerging or Advanced levels.

**FAQ (quoted)**
- "You may not submit your past projects to HackGT. We will send out an event packet containing project submission rules closer to September."
- "Teams can have up to four members."
- "We have a hardware desk with a variety of devices you can borrow throughout the event."
- Eligibility: enrolled college student; non-GT students must be 18+ by September 25, 2026.
- Sponsors visible on the page include Meta, Aramco, Visa, Impiricus, Cursor, NSA, MLH.

Still unpublished: the AI-code disclosure rule and the Devpost deadline (the Devpost page is a placeholder). The Immersive track is now confirmed.

## 1. HackGT 13 official details (earlier research, partly superseded above)

- **Dates and venue:** September 25 to 27, 2026, Klaus Advanced Computing Building, 266 Ferst Dr NW, Atlanta. [hack.gt](https://hack.gt/), [HackGT 13 Devpost](https://hackgt13.devpost.com/)
- **Duration:** described as the traditional 36-hour format (HackGT 12 ran Fri to Sun, Sept 26 to 28, 2025). **Exact hacking start and end times not yet published.** Confirm in the packet.
- **Team size:** up to 4, with a team-formation event after the opening ceremony (from search summaries of hack.gt; the site is JS-rendered and could not be fetched in full).
- **Tracks:** the HackGT 13 Devpost page currently shows only "Productivity" and "Social Good" and placeholder prize data ("Submissions open soon"). **No confirmed Immersive/AR/VR track yet.** HackGT 12 had "Hall of Illusions" (immersive), Best Overall, Best Emerging, The Curator's Cause, Crypt of Data, The Gadget Gallery. [HackGT 12 Devpost](https://hackgt-12.devpost.com/)
- **Sponsors (HackGT 12, for reference):** PrizePicks, Impiricus, Capital One, Cedar, Warp, Ergo, MLH, Cloudflare, GoDaddy Registry. $33,000+ prizes, 910 participants.
- **Judging format:** HexLabs runs its own expo/judging platform ([HexLabs Expo](https://expo.hexlabs.org/), [HackGT/timber](https://github.com/HackGT/timber)). HackGT 12 criteria: creativity, complexity, completeness. **Per-team slot length and expo vs presentation format unverified;** likely expo-style.
- **Submission:** Devpost, hackgt13.devpost.com. Deadline not yet posted.
- **Prior work and AI code rules:** not yet published for HackGT 13. MLH-partnered events generally require disclosure of AI tool use on Devpost. **Confirm the exact clause** at the rules page once published.
- **Hardware lending:** HackGT historically has a hardware desk (MLH hardware lab). **No device list for HackGT 13; bring our own glasses and watch.**
- **Eligibility:** college students, 18+ by Sept 25, 2026 if not a GT student.

## 2. HackGT 12 winners

- **Dispatch**, 1st in Hall of Illusions: VR locomotion for accessible firefighter training, body as locomotion input. Rewarded: a concrete accessibility problem solved in VR. [Devpost](https://devpost.com/software/dispatch-u7fwrv)
- **Hall of Us**, immersive track: photos become exhibits in a Quest 3 museum, tied to the "Midnight at the Museum" theme. Devpost link not retrieved.
- **VisionNav / The Curator's Cause: could not verify.** "The Curator's Cause" at HackGT 12 was "most impactful productivity agent using the Mastra Agent Framework" (prizes: Quest 3S, Ninja Creami), which does not match an indoor-navigation entry. Several unrelated "VisionNav" projects exist on GitHub. **The team should re-check the HackGT 12 project gallery** (https://hackgt-12.devpost.com/project-gallery) before naming VisionNav in the pitch. If it cannot be confirmed, drop the comparison and use the generic "obstacle-avoidance apps see what is in front of you; we know the building" line.

## 3. Prior indoor AR navigation hackathon projects

1. **arnav**: QR-scan start, ARCore tracking, Unity + AR Foundation, FastAPI + Dijkstra backend. Built because "GPS has no signal, magnetometer heading drifts badly near building steel." [GitHub](https://github.com/abdulrehman501/arnav)
2. **NAV-AR**: Unity + ARCore + A* + QR calibration. Reported "difficulty repositioning the user inside the map while navigating." [Devpost](https://devpost.com/software/nav-ar-f2gcrb)
3. **AR Indoor Navigation** (HackPrinceton 2018): Wi-Fi RSSI localisation + AR escape arrows. Multi-metre error from walls and beams. [Devpost](https://devpost.com/software/ar-indoor-navigation)
4. **AR Navigator** [Devpost](https://devpost.com/software/ar-navigator-tcjd6w), 5. **NAVIO** [Devpost](https://devpost.com/software/navio-h3mha), 6. **AR_NAV_CAMP** [GitHub](https://github.com/ShreyaKumar-dev/AR_NAV_CAMP), 7. **Indoor Navigation Using AR** (HackBIOS 5.0): AR Foundation + Unity NavMesh + QR. [GitHub](https://github.com/singhrishabh93/Indoor-Navigation-Using-Augmented-Reality-AR)

**Themes:** (a) indoor GPS failure is the universal motivation; (b) QR codes or manual calibration are the near-universal cold start; (c) drift and relocalisation mid-route is the most reported unsolved problem; (d) Wi-Fi RSSI gives multi-metre error. CampusMaps' differentiator on the technical axis is "existing signage as anchors plus re-snap on every detection", and judges will ask about it.

**Ray-Ban Meta at hackathons:** the Wearables Device Access Toolkit is a Kotlin/Swift SDK in developer preview with a MockDeviceKit. A HackMIT 2026 project used it live on the glasses. [Meta blog](https://developers.meta.com/blog/introducing-meta-wearables-device-access-toolkit/), [facebook/meta-wearables-dat-android](https://github.com/facebook/meta-wearables-dat-android), [hackmit26 example](https://github.com/andrewwbuilds/hackmit26)

## 4. Competitors and prior art

- **Google Maps Indoor Live View:** a handful of Westfield malls plus some airports and transit hubs in Tokyo and Zurich. No universities. [Gizmodo](https://gizmodo.com/google-maps-is-adding-indoor-live-view-and-100-other-f-1846577782)
- **Apple Indoor Maps / IMDF:** venues can build IMDF maps, but Apple Maps displays indoor maps only for airports. [Apple](https://register.apple.com/resources/indoor/program/indoor_maps)
- **Mappedin, Pointr, Situm:** enterprise indoor wayfinding SDKs with accessibility routing; require institutional procurement. [Mappedin](https://www.mappedin.com/resources/blog/best-campus-wayfinding-software/)
- **GoodMaps Explore:** accessibility-first indoor navigation for blind and low-vision users, deployed at some universities. [Ontario Tech](https://accessibility.ontariotechu.ca/resources/goodmaps-explore.php)
- **Georgia Tech:** map.gatech.edu is outdoor only. A student project **GTMaps** did indoor shortest-path routing for some buildings (no AR). [GTMaps GitHub](https://github.com/GTMaps/GTMaps)
- **Georgia State:** outdoor Concept3D map only; a 2013 capstone proposed indoor navigation and was never deployed. [Concept3D](https://map.concept3d.com/?id=1108)

**Gap:** nothing combines free and consumer-facing, AR-first, coverage of actual classroom buildings, entrance and access-rule awareness, and phone + glasses + watch.

## 5. Venue and demo building facts

- **Klaus:** 3 storeys, three-storey atrium with a glass staircase, main entrance is the row of glass doors on the south side facing west (from search summaries of [Wikipedia](https://en.wikipedia.org/wiki/Klaus_Advanced_Computing_Building) and GT pages). Room numbering unverified; get a floor plan on site.
- **GSU Classroom South:** address conflicts between 110 and 95 Decatur St SE. Connects to Library South and has a "back entrance". Floors and entrances unverified. [calendar.gsu.edu](https://calendar.gsu.edu/classroom_south)
- **GSU Student Center East:** regular hours Mon to Thu 8am to 10pm, Fri 8am to 8pm, Sat 12pm to 8pm, Sun 2pm to 8pm ([engagement.gsu.edu](https://engagement.gsu.edu/student-center/visitus/)). GSU buildings generally require swipe-card access 8pm to 11pm weekdays and before 7:30am ([safety.gsu.edu](https://safety.gsu.edu/safety-and-you/building-access/)). Student Center East specific after-hours rule is inferred, not confirmed. **Note for Demo C:** Saturday hours are 12pm to 8pm, so on the Saturday evening trip the building may be fully closed after 8pm rather than card-only. Plan the trip so the after-hours moment is real but the building is still enterable, or record the redirect outdoors and the indoor turns before closing.

## Things the team must confirm in the event packet
- Exact hacking start and end times.
- Whether an Immersive track exists for HackGT 13.
- Sponsor challenges and prizes.
- Submission deadline.
- Judging format and slot length.
- Prior-work and AI-code disclosure rules.
- Hardware lending list.
- Whether GSU buildings are accessible on the Saturday of the event, and until what time.
- Correct Classroom South address.
- A Klaus floor plan.

## Tough judge questions
1. "Google and Apple already do indoor AR." Neither covers GT or GSU buildings; Live View is malls and airports, Apple shows airports only.
2. "How do you handle relocalisation drift?" Existing signage as ARCore image anchors at every decision point, re-snap of the whole route on every detection, text recognition for small plaques.
3. "What is novel versus Mappedin or Pointr?" Those need an institutional contract; this is a student-mappable, free, multi-device app.
4. "How did you build the map data in a weekend?" Hand-drawn graph from step counts and photos, disclosed as pre-event capture; an hour per building.
5. "Why glasses and a watch?" Hands-free and glanceable guidance for people carrying things or who cannot hold a phone up; the same route engine drives all three.

## Update 2026-09-25: VisionNav confirmed

Raphael supplied the link: https://github.com/jaeheonshim/VisionNav (video https://www.youtube.com/watch?v=Hh7d22NlIk4). It is real and won **1st place at HackGT 12 in "The Curator's Cause"**. What it is: an iPhone app for visually impaired users. iPhone camera plus LiDAR, frames split into five depth columns, YOLO object detection on a Python server on the same laptop, Gemini for voice commands and speech, spatial audio through AirPods to steer around obstacles or toward an object ("hand-guided" mode: audio steers your hand to the target). No map, no routing, no knowledge of the building; it is a "see what is in front of you" assistant, exactly the category the pitch already contrasts against. Its own README lists weak multi-object handling, mode switching and Gemini voice accuracy as problems. It has one star and no activity since 2025-09-28. Prepared answer in 13-pitch.md updated.
