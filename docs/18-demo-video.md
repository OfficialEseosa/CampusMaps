# 18. Demo Video: Recording, Editing, Submission

**Owner:** Demo and Pitch Lead. **Depends on:** [01-demos.md](01-demos.md) (what each demo proves), [13-pitch.md](13-pitch.md) (expo beats, Devpost structure), [10-timeline.md](10-timeline.md) (trip and freeze times), [06-glasses-bridge.md](06-glasses-bridge.md) (camera and audio never overlap), [08-watch-companion.md](08-watch-companion.md). Researched 2026-09-25 (Friday, before the opening ceremony). Claims carry links; anything not verified is marked **unverified**.

---

## Best way to do the demo video

1. **One 2:00 to 2:30 cut, hard cap 2:59.** The event has published no length rule yet (section 1). 2:00 meets the MLH standard rule if the packet adopts it; under 3:00 meets the usual Devpost norm. Make a 2:00 version first and extend only if the packet allows more.
2. **At the expo, the video backs up the live demo; it does not replace it.** Judges hold the phone for Demo A live. Show the B and C clips at the table as a local MP4 on a laptop, not streamed. The Devpost video is a separate thing, for judges who look at the page before or after visiting the table.
3. **Structure: hook (10 s), A (35 s), B (35 s), C (40 s), how it works (15 s), what's next plus name (10 s).** Real footage from the first frame. No title card longer than 2 s.
4. **Main source for every demo: the over-the-shoulder phone** (landscape, 1080p, exposure locked), with the **demo phone's screen recording as a picture-in-picture inset** on the right. The PiP inset shows the debug chip ("Located: Node H3", "Seen: 'ROOM 220'"). That inset is our honesty: it shows the recognition really happened.
5. **Screen-record the demo phone with Samsung's built-in recorder, set to 1080p, not scrcpy.** It runs on the phone with no cable or Wi-Fi link to a laptop, which matters when walking outdoors at GSU. Use scrcpy over USB only for the Klaus fallback and for mirroring at the table.
6. **Glasses POV is B-roll from the glasses' own capture button, recorded in a separate pass.** Toolkit camera bursts and native capture both need the same camera, so do not expect both in one take (unverified, but assume it). POV clips are portrait, so show them as a vertical inset or crop them.
7. **Audio: burned-in captions carry the story; a clean voice-over is optional.** The glasses' spoken turns must still be heard. Get them with a phone mic held near the glasses frame as a guaranteed fallback. An Android rule means the TTS may not be captured by any screen recorder (section 3.5); test that tonight.
8. **Edit in Clipchamp** (built into Windows 11, free 1080p export, no watermark, auto captions). Use ffmpeg for the mechanical jobs: trimming raw files, normalising formats, loudness, final encode.
9. **Record every take twice, copy everything to two laptops before leaving GSU, never edit an original.** Keep the raw files after the event.
10. **Upload by 6:30 AM Sunday**: YouTube, unlisted unless the packet says public, "No, it's not made for kids". Open the Devpost page on a phone on cellular data and play the video to the end. Then also submit the Devpost link at expo.hexlabs.org.

---

## 1. What HackGT 13 and Devpost actually require

### Checked today (2026-09-25)

| Item | What the source says | Source |
|---|---|---|
| Submission | "Submissions open soon." Teams make a Devpost submission, then **submit to expo.hexlabs.org with the Devpost link** (a second step, easy to miss) | [hackgt13.devpost.com](https://hackgt13.devpost.com/) |
| Deadline | **Not published.** Our plan assumes 8:00 AM Sunday, when hacking ends | [hackgt13.devpost.com](https://hackgt13.devpost.com/), [10-timeline.md](10-timeline.md) |
| Video | **No video requirement on the rules page** (no length, no host) | [rules](https://hackgt13.devpost.com/rules) |
| Code | "You must submit some form of code (GitHub, Repl.it, Google Drive link, etc)" | [rules](https://hackgt13.devpost.com/rules) |
| Expo | "You must be at expo on Sunday for your project to be evaluated." Judging criteria are on live.hexlabs.org, which shows no content without JavaScript | [rules](https://hackgt13.devpost.com/rules), [live.hexlabs.org](https://live.hexlabs.org/) |
| AI | "You may use AI/LLMs/ChatGPT as a tool, but are not allowed to reskin it for your project. AI projects should clearly state what they are utilizing vs what they worked on over the weekend." (same wording as HackGT 12) | [rules](https://hackgt13.devpost.com/rules), [HackGT 12 rules](https://hackgt-12.devpost.com/rules) |
| Frameworks | Public frameworks are allowed but must be credited on Devpost | [rules](https://hackgt13.devpost.com/rules) |
| Start time | The rules page lists 8pm EST. Ask at the ceremony and write down the answer | [rules](https://hackgt13.devpost.com/rules) |
| Our track prize | Lighthouse Laboratory 1st: Ray-Ban Meta Gen 2 per member; 2nd: Aurzen projector per member | [hackgt13.devpost.com](https://hackgt13.devpost.com/) |
| Also listed | "Meta: Bringing People Closer Together with AI": 3 teams win an invitation to a Round 2 hackathon at Meta Menlo Park. No description published | [hackgt13.devpost.com](https://hackgt13.devpost.com/) |

### Defaults if the packet says nothing

- **Devpost:** video links from YouTube, Vimeo or Youku embed on the project page; the video must be publicly viewable; most demo videos run under 3 minutes; on YouTube mark it "not made for kids" or it may not play for judges ([Devpost help](https://help.devpost.com/article/126-know-your-submission-steps), [Devpost blog, 6 tips](https://info.devpost.com/blog/6-tips-for-making-a-hackathon-demo-video)).
- **MLH standard rules:** "We require all teams to submit a 2 minute or less demo video. Your video must be created the weekend of the hackathon." The video "must remain public post event". Teams "should be honest and transparent about the AI code tools they used" ([MLH standard rules](https://github.com/MLH/mlh-policies/blob/main/standard-hackathon-rules.md)). These rules clearly apply to MLH-run events. Whether HackGT (MLH member, run by HexLabs) adopts them is **unverified**. **The 2-minute rule is the one that could hurt us**, so cut a 2:00 version first.
- **Unlisted vs public on YouTube:** unlisted videos embed on Devpost fine. The MLH wording "remain public" is ambiguous about unlisted. If the packet says "public", use public.

### Questions for the 4 PM opening ceremony (and the packet)

1. Submission deadline, exact time and time zone. Is expo.hexlabs.org submission a separate deadline?
2. Is a video required? Maximum length? YouTube only, or is a Drive link allowed?
3. Must the video be recorded during the event? Our B and C footage is recorded Saturday, so yes, but also ask: **may the video include footage from pre-event site visits?** Our answer: no pre-event footage goes into the cut. The glasses footage captured this week is test data for the Mock Device Kit, not demo material.
4. Exact AI disclosure wording, and where it should go on Devpost.
5. Expo logistics: table size, power outlets, table number assignment, how long judges stay at each table, and whether judges walk away from the table (Demo A needs about 60 m of walking).
6. The event start date typo (Friday the 25th vs "Friday, September 26th").
7. The Meta challenge: what does "Bringing People Closer Together with AI" require, and can one project enter both it and the Lighthouse track?

---

## 2. What wins at an expo table

**The format.** Expo judging means 3 to 5 minutes per judge group, repeated all morning ([13-pitch.md](13-pitch.md)). HackGT 12 judged on creativity, complexity and completeness ([research](research/hackgt-and-competitors.md)). At a table, **completeness is judged by what the judge touches**. A working live Demo A in the judge's hands is worth more than any edit. The video has three jobs:

1. **At the table:** show the two demos that cannot happen live (Classroom South and Student Center East are at GSU, downtown). Use short clips, 30 to 40 s each, cued up on a laptop in a local player, with a small speaker. Do not stream from YouTube on venue Wi-Fi.
2. **On Devpost:** a judge scoring later, or a track judge who never reached our table, sees everything in about 2 minutes.
3. **Insurance:** if Demo A fails live, the Klaus fallback clip is 20 s of the same route.

**Recommended 2:30 structure** (a 2:00 version drops the bracketed parts):

| Time | Beat | Footage |
|---|---|---|
| 0:00 to 0:10 | **Hook.** Caption: "Maps gets you to the building. Then you're on your own." Hand tries a card-reader door and it does not open | Over-shoulder at a real card reader (GSU, Saturday) |
| 0:10 to 0:45 | **Demo A, Klaus.** Pick destination, point at sign, "Located" chip, arrows down the hall, "You have arrived" | Over-shoulder main plus screen-recording PiP |
| 0:45 to 1:20 | **Demo B, Classroom South.** Two start points, two different recommended entrances, route cards with ETAs, elevator with the floor counting up, accessibility toggle removes stairs | Screen recording full frame for the options screen, over-shoulder plus PiP for the walk; walking sped up 2 to 4 times |
| 1:20 to 2:00 | **Demo C, Student Center East.** Glasses on, phone in pocket. Spoken heads-up about the card-only door, redirect, spoken turns inside, watch arrow and buzz | Follow cam from behind, glasses POV inset, wrist close-up, captions of each spoken line, audio of the speech |
| 2:00 to 2:15 | **How it works.** One diagram: signs, then position, then route engine, then phone, glasses and watch | Architecture slide as a still, with a slow push-in |
| 2:15 to 2:30 | [What's next] plus team name, "HackGT 13", track | One slide |

**Rules for the cut**
- Put the pitch line on screen during B: "The closest door isn't always the fastest way to your seat."
- Say "HackGT 13" in the first 10 s or on the end card. MLH asks for the hackathon name at the start ([MLH rules](https://github.com/MLH/mlh-policies/blob/main/standard-hackathon-rules.md)). It costs nothing.
- **Captions vs voice-over:** use burned-in captions for every beat, because judges often watch muted. Add a voice-over only if a teammate with a clear voice records it in a quiet room by 4 AM. Otherwise use captions over natural sound, and the spoken glasses lines are the voice. Devpost recommends narration for screencasts ([Devpost help](https://help.devpost.com/article/84-video-making-best-practices)), but our footage is people walking, not a UI walkthrough, so captions fit better.
- **Honesty, on screen:** keep the debug chip visible in the PiP for A and C. Add a small caption at the Demo C redirect: "Simulated time: 8:15 PM (building closes at 8)". Show a corner label on sped-up footage ("2x"). Do not add any AR graphics in the edit. Viewers are right to distrust edited AR, so the unedited PiP is our proof.
- **Never cut inside a recognition event.** From pointing at a sign until the chip flips must play at 1x, so viewers can see the latency is under 5 s.
- **Keep the raw footage** (originals on two laptops plus one cloud folder). If a judge asks "was that edited?", open the raw file.

---

## 3. Recording setup

### 3.1 Demo phone screen (Galaxy S25 Ultra, ARCore running)

| Option | How | Pros | Cons | Verdict |
|---|---|---|---|---|
| **Samsung screen recorder** | Quick panel, Screen recorder. Settings: Settings, Advanced features, Screenshots and screen recorder: **Video quality High (1080p)**, **Sound: Media sounds** (or "Media sounds and mic" if you want a clap for sync), "Show taps and touches" on for Demo B's options screen | Runs on the phone only; no laptop or Wi-Fi needed; hardware encoder | Records into phone storage; quality options stop at 1080p (fine); may not capture our TTS (3.5) | **Use this at GSU and Klaus** ([Samsung](https://www.samsung.com/us/support/answer/ANS10001616/)) |
| **scrcpy** | `winget install --exact Genymobile.scrcpy` (bundles adb; current release v4.1). USB: `scrcpy --no-audio --max-size=1920 --video-bit-rate=8M --record=A_take1.mp4`. Wireless: `scrcpy --tcpip` once over USB, then unplug | Recording lands on the laptop directly; clean timestamps ([scrcpy recording](https://github.com/Genymobile/scrcpy/blob/master/doc/recording.md)); wireless option | Needs a laptop within cable or Wi-Fi reach (useless walking outdoors; campus Wi-Fi often blocks device-to-device traffic). **Default audio capture turns off sound on the phone** ([scrcpy audio](https://github.com/Genymobile/scrcpy/blob/master/doc/audio.md)), so TTS would stop reaching the glasses; always pass `--no-audio`, or `--audio-source=playback --audio-dup` (Android 13+) | Klaus fallback recording (USB, laptop in a backpack), and mirroring at the expo table so judges see the phone screen on the laptop |
| **`adb shell screenrecord`** | `adb shell screenrecord --bit-rate 8000000 /sdcard/a.mp4` | Nothing to install beyond adb | "The default and maximum value is 180 (3 minutes)", no audio ([Android adb docs](https://developer.android.com/tools/adb)). Some blogs claim Android 14 raised the cap; the official docs do not say so (**unverified**) | Do not use |

**Heat.** ARCore camera plus tracking, AR rendering, and a video encoder together are the heaviest load this phone will see. The Samsung recorder is the lightest option. It uses the same hardware encoder as scrcpy, without the USB or Wi-Fi streaming and without a laptop tethered. To reduce heat further:
- Stop the recorder between takes; never run one 20-minute recording.
- Take the case off. Keep brightness below max. Keep the phone out of direct sun between takes.
- Swap to the backup phone (same build) if the thermal warning appears.
- Do not record at 4K or with the phone at QHD+ display resolution. Set Display, Screen resolution to FHD+ on the demo phone for the trip: fewer pixels to render and encode (**unverified** effect size, but free).
- Test tonight: record a 3-minute Klaus run and check that ARCore tracking quality and FPS in the debug overlay stay the same with and without the recorder.

### 3.2 Over-the-shoulder second phone

- **Landscape, 1080p, 60 fps if the phone offers it** (smoother when sped up 2 to 4 times; 30 fps is acceptable). Not 4K: files are 3 times larger and editing on the laptop gets slow.
- **Framing:** stand about 1 m behind and slightly to the right of the user, at shoulder height. Frame the phone screen in the right third and the hallway ahead in the rest, so viewers see the arrow and the real corridor it points at. For Demo C, stand 2 to 3 m behind for a follow cam, then move in for a wrist close-up at one turn.
- **Steadying:** turn on the phone's stabilisation (Samsung "Super steady" crops and may drop to 1080p30, which is acceptable). Hold with both hands and elbows tucked; walk heel-to-toe. A cheap handheld gimbal is a bonus, not a requirement.
- **Exposure indoors at dusk:** long-press on the phone screen or a mid-grey wall to set **AE/AF lock** (Samsung and iPhone both support this), then pull exposure down one step so the phone display does not blow out. Or use Samsung Pro Video with fixed shutter 1/120 (1/100 is fine too; 1/120 avoids flicker from 60 Hz US lights) and ISO on auto. Re-lock when moving from outdoors to indoors (Demo B's outdoor leg).
- **Airplane mode plus Do Not Disturb** on the filming phone. Clear 20 GB of space. At 1080p60 a phone records roughly 150 to 200 MB per minute.

### 3.3 Ray-Ban Meta Gen 2 point of view

**Verified (Meta help pages):**
- Start video: **press and hold the capture button**; press again to stop. By voice: "Hey Meta, take a video" / "Hey Meta, stop". "The white notification LED will stay on while you're recording." ([Meta help: capture](https://www.meta.com/help/ai-glasses/272319252352130/))
- Length: default 30 s, extendable to **3 minutes** in the Meta AI app (Device settings, Media, Video settings) ([Meta POV guide](https://www.meta.com/ai-glasses/learn/guide-shooting-pov/)). Tom's Guide reports that with 3K selected the choices are 1 or 3 minutes ([Tom's Guide](https://www.tomsguide.com/computing/smart-glasses/7-settings-to-change-first-on-your-ray-ban-meta-glasses)).
- Resolution: Gen 2 does "up to 3K Ultra HD at 30 fps, or 1080p at 60 fps", with stabilisation low, medium, high or auto ([Meta POV guide](https://www.meta.com/ai-glasses/learn/guide-shooting-pov/), [Meta blog](https://www.meta.com/blog/ray-ban-meta-gen-2-now-available-ai-glasses-extended-battery-life-3k-video/)).
- Format: "Recordings are saved as MP4 on Android devices or HEVC on iOS" ([Meta help: capture](https://www.meta.com/help/ai-glasses/272319252352130/)).
- Import: glasses connected to the Meta AI app, Menu, **Import** next to Gallery. Media is "removed from your glasses and saved to your phone's photos app after importing" ([Meta help: storage](https://www.meta.com/help/ai-glasses/1427588664906909/), [Meta POV guide](https://www.meta.com/ai-glasses/learn/guide-shooting-pov/)). From the phone to the laptop: USB cable (File Transfer mode, `DCIM` folder), or Quick Share / Google Drive.

**Reported but not verified:**
- **Orientation is portrait.** Reviewers quote 1440×1920 for 1080p-class capture ([Medium comparison](https://medium.com/antaeus-ar/meta-ray-bans-gen-1-vs-gen-2-full-review-and-comparison-7facac116080)). Plan for vertical clips: a tall inset on the left of a 16:9 frame, or a 16:9 centre crop (3K gives room to crop).
- Audio drifts out of sync on Gen 2 clips longer than about a minute (Meta community forum report, [thread](https://communityforums.atmeta.com/discussions/ai-troubleshooting/ray-ban-meta-audio-out-of-sync-with-video/1353062), page not readable by our fetcher). Keep POV clips **under 60 s** and do not rely on their audio.
- Whether native capture works while our app holds a toolkit camera session: **no documentation found.** The toolkit's public sample captures stills only during streaming ([Meta community forum](https://communityforums.atmeta.com/discussions/Questions_Discussions/camera-access-limitations-in-the-meta-wearable-device-toolkit/1365415)), and [06-glasses-bridge.md](06-glasses-bridge.md) already treats the camera as a single resource. **Assume they conflict.** Test once tonight (start a toolkit session, press the capture button) and write the result here.
- Whether the glasses' own mics pick up the TTS they are playing during a native recording: unknown (echo cancellation may remove it). Do not depend on it.

**The toolkit stream cannot serve as footage.** The Android stream runs at about 10 fps and 650 Kbps, 504×896 at MEDIUM, and our app only takes stills in bursts ([06-glasses-bridge.md](06-glasses-bridge.md)). The one useful toolkit visual is the **last-still thumbnail on the S3 glasses screen**, which appears in the phone screen recording. Show it in the PiP: it proves what the glasses saw.

**Recommendation.** For the real Demo C take, the glasses run our app (burst camera plus speech), the demo phone screen-records, and the second phone follows. **Then do a second pass of the same walk with the app stopped and the glasses' capture button recording POV in 1080p60, stabilisation auto.** Use that pass only as B-roll inset ("what the wearer sees"), never presented as the recognition take. Import it to the demo phone right after the pass. Importing needs the Meta AI app open, so **force-stop Meta AI again before the next app take** (the 0.9.0 lag bug, [06-glasses-bridge.md](06-glasses-bridge.md)). If Demo C runs in headset-fallback mode, the glasses camera is free, and native POV can run during the real take.

**The capture LED** is on during every recording and cannot be disabled ([9to5Google via research](research/meta-glasses.md)). It is visible in the follow-cam footage. That is fine and honest; add it to the privacy answer. Do not film in restrooms or labs (Meta acceptable use policy, [research](research/meta-glasses.md)).

### 3.4 Watch (Galaxy Watch 8 Classic, Wear OS 6)

- Google's documentation says: "Unlike on mobile, the standard `adb screenrecord` command is not supported on Wear OS", and recommends `scrcpy --no-audio --no-window --record video.mp4` ([Android Developers, Wear screenshots](https://developer.android.com/training/wearables/get-started/screenshots)). A Samsung developer blog (Dec 2025) says `adb shell screenrecord` works on Galaxy Watch4 and newer, "typically up to 3 minutes", without audio ([Samsung Developer](https://developer.samsung.com/sdp/blog/en/2025/12/16/record-and-capture-galaxy-watch-screens-no-mobile-device-or-third-party-app-required)). **The two sources conflict, and nobody we found has tested Watch 8 on Wear OS 6.** Test it tonight in two minutes, since the watch is already on wireless adb for installs ([08-watch-companion.md](08-watch-companion.md)).
- **Either way it is impractical at GSU.** Both need the watch on the same Wi-Fi as a laptop, and wireless adb costs watch battery. **Film the wrist with the second phone**: one close-up per demo at a turn, where the arrow and the buzz (visible as the wrist twitches) land together with the spoken line. That shot is more convincing than a flat screen capture anyway.
- Optional: in Klaus, record a clean `scrcpy -s <watch-ip>:<port> --no-audio --record=watch.mp4` of one route for a small round inset (mask it with a circle in Clipchamp).

### 3.5 Audio: getting the glasses' voice onto the video

**The catch, which is verified:** Android lets other apps capture playback audio only if the player's usage is `USAGE_MEDIA`, `USAGE_GAME` or `USAGE_UNKNOWN` ([Android Developers, playback capture](https://developer.android.com/media/platform/av-capture)). Our TTS uses `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` ([06-glasses-bridge.md](06-glasses-bridge.md)), so **scrcpy's playback capture will not hear it.** Samsung's system recorder may or may not follow the same rule: **unverified**, test tonight (screen-record with "Media sounds" while the app speaks one instruction to the glasses, then play the file back).

In order of preference:
1. **If Samsung "Media sounds" captures the TTS:** use that track directly. It is clean and already in sync with the screen.
2. **If not, a debug-overlay switch "TTS usage: media"** (for recording only) that sets `USAGE_MEDIA` on the TTS audio attributes. It still routes over A2DP to the glasses. This is a one-line change owned by the glasses bridge module. **Ask the Device Lead; do not add it after freeze unless it is proven on the route.**
3. **Guaranteed fallback:** the filming phone records the room. For the one "heads-up" line and one turn, the filming person steps in so the phone mic is about 20 cm from the wearer's temple. Open-ear speakers are quiet but readable at that distance in a quiet corridor. The captions carry the words anyway.
4. **Not recommended:** re-synthesising the logged instruction text in post and laying it at the logged timestamps. It is the same words, but it is reconstructed audio. If we ever do it, the caption must say "audio re-rendered from app log".

**Voice-over (optional):** record it on a phone in a quiet room (a car works well): phone 20 cm from the mouth, voice recorder app, WAV or high quality. One sentence per beat, read from the section 2 table. Normalise with the loudnorm one-liner (section 4).

### 3.6 Syncing sources

- **Clap-and-flash at the start of every take:** the user holds the demo phone up to the filming camera, a teammate claps once next to both phones, and the user taps the screen at the same moment. That gives an audio spike (filming phone, and screen recording if the mic is on), plus a visible tap dot on the screen recording ("Show taps" on).
- **A clock on screen:** if the debug overlay shows a seconds clock, film it for 2 s at the start. It lines up the screen recording, the over-shoulder footage and the app's log (the recognition events have timestamps).
- **Say the take name aloud:** "B, start P1, room 608, take 2" (slate by voice). The name is then in the audio of every file, which makes the 3 AM file search easy.
- In Clipchamp, line up the clap spike on the waveforms; nudge by frames. In ffmpeg, offset one input with `-itsoffset 0.40`.

---

## 4. Editing on Windows

| Tool | PiP | Captions | Speed ramps | Trim to 3 min | 1080p H.264 export | Learning curve | Verdict |
|---|---|---|---|---|---|---|---|
| **Clipchamp** (built into Windows 11) | Yes, drag a second clip onto an upper track and resize | Auto captions on the free plan | Constant speed per clip (split the clip, set 2x to 4x) | Yes | Free, 1080p, no watermark ([review](https://freealternatives.to/clipchamp/review), [costbench](https://costbench.com/software/video-editing/clipchamp/free-plan/)) | Lowest | **Use this** |
| **CapCut desktop** | Yes | Auto captions now limited on free, fuller on Pro ([BIGVU](https://bigvu.tv/blog/capcut-free-vs-pro-what-2026s-restructure-actually-gives-you/)) | Speed curves free | Yes | Free 1080p | Low | Fine backup; risk of a paywall prompt at 4 AM; needs an account |
| **DaVinci Resolve (free)** | Yes | Manual subtitle track (the AI captioning is in the paid Studio version) | Excellent | Yes | Yes | High | Only if someone already knows it |
| **ffmpeg 8.0.1** (installed, gyan.dev full build with libx264, NVENC, libass, loudnorm, drawtext) | `overlay` | `drawtext` or SRT with `subtitles` | Per-segment `setpts` | Exact | Exact | Commands, no GUI | Preparing and normalising files, loudness, final re-encode |

**Workflow:** ffmpeg normalises the raw clips to one format, Clipchamp does the story cut, captions and PiP and exports 1080p, and ffmpeg runs loudnorm and a final check. The ffmpeg build even includes a `whisper` filter for auto captions, but it needs a downloaded model; do not start that at 3 AM.

### ffmpeg one-liners (PowerShell, one line each)

Work in `C:\Users\rapha\CampusMaps-video\` (not inside the repo). Keep `raw\` read-only; write into `work\`.

```powershell
# Inspect a file (resolution, fps, rotation, audio?)
ffprobe -hide_banner -show_entries stream=codec_type,width,height,r_frame_rate:stream_side_data=rotation -of compact raw\B_P1_608_t1_screen.mp4

# Trim, frame-accurate (re-encode). -ss/-to are times in the input.
ffmpeg -ss 00:00:12.5 -to 00:00:48 -i raw\B_P1_608_t1_screen.mp4 -c:v libx264 -crf 18 -preset medium -c:a aac -b:a 160k work\B_screen_cut.mp4

# Trim fast (no re-encode, cuts on the nearest keyframe, may be ~1 s off)
ffmpeg -ss 00:00:12 -to 00:00:48 -i raw\clip.mp4 -c copy work\clip_rough.mp4

# Normalise any clip to 1920x1080, 30 fps, stereo 48 kHz (letterboxed/pillarboxed; adds silence if the clip has no audio)
ffmpeg -i work\in.mp4 -f lavfi -i anullsrc=r=48000:cl=stereo -vf "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,fps=30,setsar=1" -map 0:v -map 0:a? -map 1:a -shortest -c:v libx264 -crf 18 -preset medium -c:a aac -b:a 160k -ar 48000 work\in_norm.mp4
# (If the clip has its own audio, drop "-map 1:a" and the lavfi input.)

# Picture-in-picture: over-shoulder full frame, phone screen recording as an inset on the right (height 900 px)
ffmpeg -i work\B_shoulder.mp4 -i work\B_screen.mp4 -filter_complex "[1:v]scale=-2:900,setsar=1[pip];[0:v][pip]overlay=W-w-40:(H-h)/2:shortest=1[v]" -map "[v]" -map 0:a? -c:v libx264 -crf 19 -preset medium -c:a aac -b:a 160k work\B_pip.mp4

# Side by side (portrait screen recording left, landscape over-shoulder right) on a 1920x1080 canvas
ffmpeg -i work\C_screen.mp4 -i work\C_follow.mp4 -filter_complex "[0:v]scale=-2:1080,setsar=1[a];[1:v]scale=-2:1080,setsar=1[b];[a][b]hstack=inputs=2,scale=1920:-2,pad=1920:1080:(ow-iw)/2:(oh-ih)/2[v]" -map "[v]" -map 1:a? -shortest -c:v libx264 -crf 19 -c:a aac work\C_sbs.mp4

# Add a caption for a time window (seconds 2 to 6)
ffmpeg -i work\B_pip.mp4 -vf "drawtext=fontfile='C\:/Windows/Fonts/segoeuib.ttf':text='Two starting points. Two different doors.':fontsize=54:fontcolor=white:box=1:boxcolor=black@0.6:boxborderw=18:x=(w-text_w)/2:y=h-text_h-70:enable='between(t,2,6)'" -c:v libx264 -crf 19 -c:a copy work\B_cap.mp4

# Many captions: write captions.srt in the working folder, then burn it in
ffmpeg -i work\final_nocap.mp4 -vf "subtitles=captions.srt:force_style='FontName=Segoe UI,FontSize=22,Bold=1,BorderStyle=3,Outline=2,MarginV=40'" -c:v libx264 -crf 19 -c:a copy work\final_cap.mp4

# Speed up a walking segment 3x (video only; drop the audio or replace it)
ffmpeg -i work\walk.mp4 -filter:v "setpts=PTS/3" -an -c:v libx264 -crf 19 work\walk_3x.mp4

# Concatenate clips that are already normalised (same size/fps/codec/audio). list.txt has lines: file 'work/A.mp4'
ffmpeg -f concat -safe 0 -i list.txt -c copy work\final_nocap.mp4

# Mix a voice-over over natural sound (natural sound at 30 %)
ffmpeg -i work\final_cap.mp4 -i vo\vo.wav -filter_complex "[0:a]volume=0.3[bg];[bg][1:a]amix=inputs=2:duration=first:normalize=0[a]" -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k work\final_vo.mp4

# Normalise loudness for YouTube (about -14 LUFS), video untouched
ffmpeg -i work\final_vo.mp4 -c:v copy -af loudnorm=I=-14:TP=-1.5:LRA=11 -c:a aac -b:a 192k -ar 48000 out\CampusMaps_HackGT13.mp4

# Final YouTube-safe encode if an editor export misbehaves (NVENC is fast; libx264 is the safe default)
ffmpeg -i out\CampusMaps_HackGT13.mp4 -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -r 30 -movflags +faststart -c:a aac -b:a 192k out\CampusMaps_HackGT13_yt.mp4

# Check the duration (must be under 179 s)
ffprobe -v error -show_entries format=duration -of default=nw=1 out\CampusMaps_HackGT13_yt.mp4
```

Watch for:
- Phone screen recordings are **portrait and variable frame rate**. Always pass them through the normalise step before concat.
- Samsung recordings may carry a rotation flag; `ffprobe` shows it.

---

## 5. Shot lists

Target durations are for the final Devpost cut. Name every file `<demo>_<start>_<room>_t<n>_<source>.mp4` (for example `B_P2_608_t1_shoulder.mp4`). Rename on the laptop the same night.

### Demo A: Klaus (target 35 s)

| # | Shot | Source | Final length |
|---|---|---|---|
| A1 | Judge's-eye view: phone in hand at the Atrium table, destination list, tap | Over-shoulder | 4 s |
| A2 | "Point the camera at a sign", sign comes into frame, chip flips to "Located", arrows appear. **Real time, no cut** | Over-shoulder main plus screen PiP | 8 s |
| A3 | Walk down the hallway, arrows on the floor, second anchor silently re-snaps (show the chip change in the PiP) | Over-shoulder, 2x, "2x" label | 12 s |
| A4 | Turn at the junction; destination marker at the door; "You have arrived" | Over-shoulder plus PiP | 7 s |
| A5 | Watch on wrist showing the arrow at the turn (optional) | Over-shoulder close-up | 4 s |

**Klaus fallback video.** Record it in the **Sunday 12 to 2 AM freeze window**, on the freeze build, from both rehearsed starting points, twice each. The Atrium is quieter at night, which is good for footage, but the lighting differs from 9:30 AM. Record one more short take at the Sunday 8 to 9:30 AM rehearsal, if there is time, as a daylight version. Sources:
- scrcpy over USB to a laptop in a backpack, or the Samsung recorder;
- the over-shoulder phone.

Export a **20 s fallback clip** (A2 plus A4) as a local MP4 on the expo laptop, plus the full take. The Devpost cut uses the best Klaus take from this session, because the expo itself cannot be filmed before the deadline.

### Demo B: Classroom South (target 35 s)

| # | Shot | Source | Final length |
|---|---|---|---|
| B1 | Establishing: user on the sidewalk at P1, building behind | Over-shoulder, wide | 3 s |
| B2 | Search "608", **route options screen with 2 to 3 cards and ETAs**, tap the top card | Screen recording, full frame, taps visible | 6 s |
| B3 | Outdoor arrow to the chosen entrance (Geospatial) | Over-shoulder plus PiP, 3x | 5 s |
| B4 | Inside: sign recognised, arrows to the elevator, "Take the elevator to floor 6" | Over-shoulder plus PiP | 5 s |
| B5 | **Elevator: big floor number counting up** (phone and watch in one frame) | Over-shoulder close-up, 1x | 5 s |
| B6 | Arrive at 608 | Over-shoulder | 2 s |
| B7 | Cut to P2: same search, **a different recommended entrance**; freeze-frame the two option screens side by side | Screen recording, two takes side by side | 5 s |
| B8 | Accessibility toggle on: stairs cards disappear | Screen recording | 4 s |
| (raw only) | Room 150 take; P3 library start if done | Both | 0 s in the Devpost cut; keep for the table |

### Demo C: Student Center East (target 40 s)

| # | Shot | Source | Final length |
|---|---|---|---|
| C1 | Evening exterior: user walking toward the entrance, glasses on, phone in pocket | Follow cam from behind | 4 s |
| C2 | **Spoken heads-up** ("card-access only after 7 pm, redirecting to the west entrance"), watch buzz close-up with the redirect arrow; caption "Simulated time: 8:15 PM" | Follow cam, wrist close-up, audio per 3.5 | 8 s |
| C3 | Walk to the open entrance and in | Follow cam, 2x | 5 s |
| C4 | Glasses POV looking at a sign/plaque | Native glasses POV (second pass), vertical inset | 3 s |
| C5 | PiP of the S3 glasses screen: "Seen: 'ROOM 220'", last-still thumbnail; spoken "Turn left at the elevators"; watch left arrow | Screen recording plus follow cam plus audio | 8 s |
| C6 | Second and third spoken turns (tighten; keep the words) | Same | 8 s |
| C7 | "You have arrived", arrival buzz | Follow cam plus wrist | 4 s |

### Hook and closing (target 25 s)

| # | Shot | Source | Length |
|---|---|---|---|
| H1 | Hand tries a locked card-reader door (any GSU or GT door with a reader; do not tamper with it, just try the handle) | Second phone, 60 fps | 5 s |
| H2 | Title caption over the Klaus Atrium | Still or a 3 s pan | 3 s |
| H3 | Architecture diagram | Slide 3 exported as PNG | 12 s |
| H4 | End card: CampusMaps, HackGT 13, Lighthouse Laboratory, team names | Slide | 5 s |

### Saturday 5 to 10 PM GSU trip checklist

**Before leaving Klaus (4:30 PM)**
- [ ] Trip build installed on the demo **and** backup phone; the Klaus route works once on each
- [ ] All batteries 100 %: demo phone, backup phone, filming phone, glasses **and case**, watch. Two power banks plus cables (USB-C to USB-C, watch charger)
- [ ] Storage: 30 GB free on the demo phone and the filming phone. Import and clear old glasses media
- [ ] Samsung screen recorder settings: 1080p, sound set per the 3.5 test result, show taps on
- [ ] Filming phone: 1080p60, airplane mode, DND, grid on
- [ ] Glasses: video length 3 min, 1080p60, stabilisation auto. Meta AI app **force-stopped** before app takes
- [ ] Printed scripts for B and C with the take list; a pen to tick takes off
- [ ] Watch paired, instruction screen tested; DND on the watch except for our app
- [ ] Demo phone: DND on (no notification banners in the recording), brightness about 70 %, display at FHD+
- [ ] Laptop plus USB-C cable (to copy files at GSU); someone at Klaus on a call for hotfix builds

**Order on site**
1. **Classroom South first, 5:30 to about 7:15 PM.** Sunset in Atlanta on 2026-09-26 is about **7:29 PM**, civil dusk about 7:54 PM (computed from the NOAA sunrise equations; check a weather app on the day). Do the outdoor Geospatial takes (B1 to B3) first, P1 then P2, then the indoor-only takes, room 150 and the accessibility toggle.
2. **Student Center East by 7:15 PM; the indoor walk must finish before 8:00 PM** (posted Saturday hours 12 to 8 PM, [engagement.gsu.edu](https://engagement.gsu.edu/student-center/visitus/)). Order:
   - the app take with simulated time;
   - the native-POV second pass;
   - wrist close-ups;
   - the audio fallback (phone mic at the temple).
3. The hook shot (H1) at any card-reader door on the way out. After 8 PM, a real locked door is the best version.

**Per take**
- [ ] Voice slate plus clap-and-tap
- [ ] **Record each take twice.** A take counts only when the recognition events fire on camera
- [ ] After each pair, play back 5 s of each file on the phone (the recorder really ran; audio is present)
- [ ] Stop recorders between takes; check the phone temperature

**Before leaving GSU**
- [ ] Copy **all** footage (demo phone DCIM/Screen recordings, filming phone DCIM, glasses imports) to **two laptops**. Check the file counts match. Then start an upload to a shared Drive folder over hotspot or when back on Wi-Fi
- [ ] Do not delete anything from the phones until Monday

### Resetting the phone between judges in under 20 s (the expo table)

1. Tap "End route". The app returns to the home screen with Klaus preselected (1 tap). If the app has no such button, a **"Reset demo" item in the debug overlay** must: clear the route, restart the ARCore session, and clear simulated time. Ask for it before freeze.
2. Put the phone in the next judge's hands at start point 1, the camera pointed at the floor (ARCore needs about 2 s of motion to track; it happens while handing over).
3. Watch: follows the phone automatically. If it shows a stale arrow, one tap on the watch clears it (check it does).
4. **Every 3 judges:** feel the phone. If it is warm, swap with the backup phone that sits on the charger, and put the hot one on the charger face down, out of the sun.
5. The laptop at the table keeps scrcpy mirroring the demo phone (`scrcpy --no-audio --max-size=1280`) over USB **only if a long cable reaches**. Otherwise skip it; a cable getting in the judge's way is worse than no mirror.
6. Time it at the Sunday 8 AM rehearsal. The target is under 20 s from "You have arrived" to the next judge holding a ready phone.

---

## 6. Sunday editing and upload timeline

The Demo Lead edits. The expo presenter sleeps 2 to 6 AM ([10-timeline.md](10-timeline.md)).

| Clock | Task | Done when |
|---|---|---|
| Sat 10 PM to Sun 12 AM (while others polish) | Rename files and pick the best take per shot (fill in the shot tables above with file names and in/out times). Write captions.srt in draft | Every shot has a chosen file |
| Sun 12 to 2 AM | Freeze; record the Klaus fallback | Klaus takes on two laptops |
| 2:00 to 2:30 AM | ffmpeg: trim and normalise the chosen segments into `work\` | All clips 1920×1080, 30 fps, with audio tracks |
| 2:30 to 4:00 AM | Clipchamp: assemble the 2:00 cut in the order from section 2; PiP; speed-ups; captions | Rough cut plays start to finish |
| 4:00 to 4:30 AM | Voice-over if used; mix; a teammate watches **muted**, then with sound, and gives two notes | Notes fixed |
| 4:30 to 5:00 AM | Export 1080p; loudnorm; ffprobe duration under 179 s. Export **table clips**: B (35 s), C (40 s), Klaus fallback (20 s) as separate MP4s on the expo laptop desktop | Files on disk |
| 5:00 to 5:30 AM | Upload to YouTube: unlisted (or public if the packet says so); "No, it's not made for kids"; title "CampusMaps: indoor AR campus navigation (HackGT 13)". Processing to HD can take a while, so upload early ([Devpost blog](https://info.devpost.com/blog/6-tips-for-making-a-hackathon-demo-video)) | Link works |
| 5:30 to 6:30 AM | Devpost write-up final (checklist below), paste the video link, **open the project page on a phone on cellular data, play the video end to end**. Then submit the Devpost link at **expo.hexlabs.org** | Both submissions show as submitted |
| by 7:30 AM | Submitted with 30 minutes to spare; screenshot the confirmation | |

**If the edit is late at 5:30 AM:** upload the rough cut anyway, then replace it later only if the rules allow edits (Devpost usually locks after the deadline). A 2:00 honest rough cut beats a missing video.

### Devpost page checklist

- [ ] **Title:** CampusMaps
- [ ] **Tagline** (under 200 chars): "Indoor AR navigation that knows which door is open: phone arrows, spoken turns on Ray-Ban Meta, and a buzz on your wrist."
- [ ] Video link (YouTube), plays on a phone
- [ ] Images: 3 to 5 stills (A2 "Located" frame, B2 options screen, B5 floor counter, C5 glasses screen, architecture)
- [ ] Sections per [13-pitch.md](13-pitch.md): Inspiration, What it does, How we built it, Challenges, Accomplishments, What we learned, What's next
- [ ] **Built with:** Kotlin, ARCore (Augmented Images, Geospatial), ML Kit text recognition, Meta Wearables Device Access Toolkit, Wear OS / Compose for Wear, Android TextToSpeech, plus the libraries in [14-tech-stack.md](14-tech-stack.md). The rules require crediting frameworks
- [ ] Code link: GitHub repo (public, or access for judges). The rules require "some form of code"
- [ ] Submitted to the Lighthouse Laboratory track (and the Meta challenge if eligible)
- [ ] **Transparency section** (draft):
  - *Data capture:* "We photographed signs, measured corridors and walking times, and logged anchor positions in Klaus, Classroom South and Student Center East for mapping."
  - *Demo footage:* "Demos B and C were recorded on Saturday evening during the event at Georgia State. Walking segments are sped up (labelled). The after-hours notice in Demo C was triggered with an in-app simulated-time setting, because the building closes at 8 PM. No AR graphics were added in editing; the phone screen inset is an unedited screen recording." (Add "The glasses ran in headset mode; the phone camera did the recognition" if the fallback was used.)
  - *AI assistance:* "We used Claude (Anthropic) as a coding assistant throughout. The design, building data, and all testing on devices are ours. Per the HackGT rule, what we used vs what we built: …" (list the models and tools honestly; match the packet's exact wording)
  - *Hardware:* our own Galaxy S25 Ultra, Ray-Ban Meta Gen 2, Galaxy Watch 8 Classic

---

## 7. Open questions (resolve today and tomorrow)

| # | Question | How to answer | By |
|---|---|---|---|
| 1 | Video length limit, host, and deadline | Opening ceremony / packet | Fri 5 PM |
| 2 | Does Samsung's recorder capture our `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` TTS with "Media sounds"? | 1-minute test on the demo phone | Fri night |
| 3 | Does the glasses capture button work while a toolkit session is active? | 1-minute test | Hour 0 to 2 |
| 4 | Does `adb shell screenrecord` or scrcpy work on the Watch 8 (Wear OS 6)? | 2-minute test over wireless adb | Hour 0 to 2 |
| 5 | Is Gen 2 native video portrait on our unit, at which resolution, and does its audio drift? | Record one 90 s clip, `ffprobe` it | Fri night |
| 6 | Does the Samsung recorder change ARCore tracking or FPS? | A 3-minute Klaus run with and without it | Sat before 4 PM |
| 7 | "Reset demo" in the debug overlay, and the optional "TTS usage: media" switch | Ask the Device Lead; both before freeze | Hour 20 |
| 8 | Who is the second camera operator on the trip? (The Demo Lead cannot both walk and film) | Team decision | Sat 4 PM |
