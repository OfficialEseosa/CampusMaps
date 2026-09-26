# 11. Team Roles and How We Work

## Roles (up to four people)

| Role | Owns | First to do at Hour 0 |
|---|---|---|
| **Device Lead** | The demo phone, the glasses, the watch. Builds, installs, and runs every new version. First to report errors. Learns the glasses toolkit setup. | Skeleton project builds and installs; ARCore camera opens; glasses sample connects |
| **Data Lead** | Building data files, anchor photos and log, accuracy of routes, hints, and access rules. | Klaus capture (if needed), then writes CS.json |
| **Demo and Pitch Lead** | The three demo scripts, video recording and editing, Devpost, slides, the Saturday trip logistics. | Confirms event packet rules, drafts Devpost skeleton, plans the trip |
| **QA and Floater** | Walks every route repeatedly, logs what breaks, helps wherever the bottleneck is. Second person who can install builds. | Learns the install flow from the Device Lead; sets up the watch |

A second teammate must be able to do the Device Lead's job, so testing never waits on one person.

## How Claude and the team work together

Claude writes the code. The team builds, runs, tests, and reports. The speed of this loop decides the weekend.

### When something breaks, send all five
1. What you did (exact steps).
2. What you expected versus what happened.
3. The **full** error text or stack trace, copied from Android Studio's Build output or Logcat filtered to our app. Not a screenshot of part of it.
4. Phone model, Android version, and whether the glasses and watch were connected.
5. A photo of the screen if it is a visual or AR problem, ideally with the debug overlay on.

### Rules
- **One module at a time.** Get it working, commit, then move on.
- **Commit after every working step** with a clear message so we can always roll back. Tag milestones (`routing-works`, `demo-a-works`, `freeze`).
- **Never hand-edit generated code without saying so**, or the next change may overwrite it.
- **Two failed fixes means stop.** If the same error survives two fix attempts, describe the situation fresh, including anything that changed since it last worked.
- **Lock versions at Hour 0 to 2.** No dependency upgrades mid-event.
- **Test on the phone, not the emulator.** ARCore, Bluetooth, and the barometer do not exist on the emulator.

## Git

- One repository, `main` branch, short-lived feature branches are optional. With four people and 36 hours, committing to `main` with small commits is fine.
- Before the event: repository exists, and everyone has pushed a test commit.
- The repository holds the app, the Wear app module, the building JSON files, the anchor images, and this planning folder.
- Large videos stay in the shared drive, not in Git.

## Communication

- One group chat for the team. Errors go there in the five-part format above.
- One shared drive folder for captures (see [09-data-capture.md](09-data-capture.md)).
- Every 4 hours: a 5-minute stand-up against [10-timeline.md](10-timeline.md). Anything more than 3 hours behind gets its polish cut on the spot.

## Devpost transparency

State plainly on Devpost:
- Building photos, measurements, and the anchor log were collected before the event.
- Whether and how AI assistance was used, per the event packet's rule.
