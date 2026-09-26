# Meta CameraAccess sample: the glasses connection test

Two builds, both with app id and client token `0` in `local.properties` (the Developer Mode path that needs no Meta developer organisation):

| APK | Toolkit | Needs Meta AI app | Needs glasses firmware | Use it when |
|---|---|---|---|---|
| `CameraAccess-0.9.0-debug.apk` (91 MB) | 0.9.0 (release 81dfb51, 2026-08-03) | V282+ | V126+ (Ray-Ban Meta) | **Now.** Matches the phone (289) and current glasses firmware, and the 0.9.0 CampusMaps pins |
| `CameraAccess-1.0.0-debug.apk` (99 MB) | 1.0.0 (release d3159f7, 2026-09-24) | V290+ | V128+ | Only if the Meta AI app updates to 290 and the glasses to V128 |

First attempt on 2026-09-25 used the 1.0.0 build and got "session ended by device" then "glasses need an update" although the Meta AI app showed them up to date: 1.0.0 shipped yesterday and wants firmware V128, which is not offered to these glasses yet. Uninstall the 1.0.0 build before installing 0.9.0 (same package name).

Source: https://wearables.developer.meta.com/docs/version-dependencies

## Install on the S25 Ultra
1. Copy the APK to the phone (USB cable to Downloads, Drive, or email to yourself).
2. Open it from Files. Android asks to allow installs from that app once; allow it.
3. Grant camera, Bluetooth / nearby devices, and notifications when asked.

## Before launching
1. Meta AI app updated (289 or newer is fine); glasses firmware updated with the glasses above 10% battery and the phone on Wi-Fi.
2. Developer Mode: Meta AI app → Settings → App Info → tap the version number 5 times → toggle Developer Mode. Then Settings → your glasses → Developer Mode on as well.
3. Glasses on, unfolded, worn or at least out of the case, paired to this phone in the Meta AI app.

## In the app (from its README)
1. Tap **Connect**. The Meta AI app opens to approve the registration and returns you to the sample. This is the step that fails if the `0` ids are not accepted.
2. Tap **Start Session**. Wait for the state to reach STARTED.
3. Tap **Preview**. The live feed from the glasses appears (the capture LED lights up).
4. Try **Capture photo** and a short **Record video**.

## Write down
- Did registration succeed with ids `0`? (yes / what error)
- Did the session reach STARTED? How long did it take?
- Rough frame rate and lag of the preview. Wave a hand in front of the glasses and count.
- Force-stop the Meta AI app (Settings → Apps → Meta AI → Force stop), reopen the sample, repeat. Lag better?
- Audio interlock: while the preview is streaming, play music or a YouTube clip. Does it come out of the glasses, the phone, or stay silent? Stop the preview and play again.
- With the Galaxy Watch also connected: does the audio still land on the glasses?
- The short firmware version shown in the Meta AI app under the glasses.

## What the result means
- **Everything works:** no Meta organisation needed. Demo C stays on the full glasses-camera plan. Tell Claude the numbers; they go into docs/06 and the debug overlay.
- **Registration fails:** start the Meta Managed Account organisation at work.meta.com right away (unbounded approval time) and plan on the headset fallback: phone camera in a shirt pocket, glasses as a Bluetooth speaker. Same story, less magic.
- **Streams but under 5 fps:** design for single photo capture per burst instead of video frames (already the plan in docs/06).

## Note on toolkit 1.0.0
The 1.0.0 sample advertises "sound-in-video captured from the glasses' microphone", which the 0.9.0 research said had no Android API. If that works on your glasses, the glasses mic is an option for voice input later. Not needed for the demos.
