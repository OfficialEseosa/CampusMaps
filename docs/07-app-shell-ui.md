# 07. App Shell and UI

**Owner:** Device Lead (builds and runs) with Claude writing screens. **Depends on:** routing for anything meaningful; everything else plugs in later.

The shell is boring on purpose. Judges should look at the AR view and hear the glasses, not admire buttons. Every screen is Jetpack Compose (Material 3) unless the AR library forces a View.

## Screens

### S1: Destination
- Building selector (three chips: Klaus, Classroom South, Student Center East). Preselect by a "demo building" setting so the judge never has to choose.
- Search field: room number or name, filtered from the building's room nodes. Show the top 5 as you type.
- "Recent destinations" list, prefilled with the demo destinations so one tap works.
- Accessibility toggle: "Avoid stairs". Persisted.
- Bottom: "Route" button.

### S1b: Route options
- 2 or 3 cards, best first. Each card: entrance name, vertical method icon (stairs or elevator), ETA in minutes and seconds, one-line explanation of what makes it different ("shorter walk, but 2 flights of stairs" / "elevator, average wait 35 s").
- Accessibility toggle repeated here, so flipping it in front of a judge updates the cards live.
- If a locked entrance was avoided, a banner at the top: "North entrance is card-access after 10 pm. Routes below use the south entrance."
- Tap a card to start guidance with that route.
- If the user is inside and there is only one sensible route, skip this screen.

### S2: AR guidance
- Full-screen camera with AR arrows and the destination marker (see [05-ar-guidance.md](05-ar-guidance.md)).
- Top: current instruction as text, large. Below it the next instruction, small.
- Bottom-left: 2D minimap (the floor's nodes and edges, the route highlighted, the user as a dot). Tap to enlarge.
- Bottom-right: "Locate me" prompt when tracking is lost ("Point at a sign").
- Speaks each new instruction through the phone speaker or connected audio (toggle in settings).

### S3: Glasses mode
- Large status text, high contrast: last seen anchor ("Seen: Room 220"), current instruction, next instruction.
- Big "Repeat" button (re-speaks the current instruction) and "Stop".
- Live thumbnail of the glasses stream (small) so the Device Lead can see what the glasses see. Can be hidden for recording.
- Enter from S1b when glasses are connected; the app should offer "Guide me with glasses" automatically if the toolkit reports a connected device.

### Debug overlay (toggle, long-press the app title)
- Current node and confidence.
- Last anchor seen, how long ago, and its kind (image or text).
- ARCore tracking state.
- Barometer: current pressure, filtered pressure, floor estimate.
- Simulated time: a switch plus a time picker; overrides `now` for routing.
- Watch connection state and last message sent.
- Replay: choose a recorded video to feed the localization pipeline instead of the camera (see [03-localization.md](03-localization.md)).
- Everything here is what testers report when something is wrong, so keep it readable in a screenshot.

## State model

One `NavigationViewModel` that owns:
- selected building and loaded graph
- destination, preferences, `now` source
- current localization estimate (node, pose if available, floor, timestamp)
- current route options and selected route
- current instruction index

All outputs (AR view, glasses speech, watch messages) observe this one state. When localization updates, the view model reroutes and republishes. No module talks to another directly.

## Permissions and setup on first run

- Camera (required).
- Bluetooth connect (glasses and watch).
- Location (only if the Geospatial leg stays in Demo B; fine location and "precise").
- Nearby devices on Android 12 and above.
- ARCore install check on launch, with a clear message if Google Play Services for AR is missing.

## Things that must be in the build for demo day

- A "demo mode" that hides the debug overlay, preselects the building and destination list, and keeps the screen on.
- Screen recording friendly: no flashing permission dialogs mid-route.
- Battery: keep the phone plugged in between runs; AR plus camera plus Bluetooth drains it fast.

## Work plan

| Hour | Task |
|---|---|
| 0 to 2 | Project skeleton, Compose navigation, S1 with hard-coded destinations, ARCore session opens on S2 |
| 2 to 6 | Wire the loaded building graph into S1 search |
| 6 to 10 | Debug overlay (needed to test localization) |
| 14 to 20 | S1b route options, accessibility toggle, locked banner, S3 glasses mode |
| 20 to 26 | Demo mode, polish, screen-recording pass |
