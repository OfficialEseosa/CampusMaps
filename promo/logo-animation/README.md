# Logo reveal animation

Transparent 1920x1080 @ 60 fps, 4 s. Rendered from the splash geometry in
`app/src/main/java/com/campusmaps/ui/splash/SplashOverlay.kt`.

    npm install
    node render.js frames                 # transparent PNG frames
    node render.js frames_preview "#3d332e"

Encode (outputs in out/, not committed):

    ffmpeg -framerate 60 -i frames/f_%05d.png -c:v prores_ks -profile:v 4444 -pix_fmt yuva444p10le -alpha_bits 16 -vendor apl0 out/CampusMaps-logo-alpha.mov
    ffmpeg -framerate 60 -i frames/f_%05d.png -c:v libvpx-vp9 -pix_fmt yuva420p -auto-alt-ref 0 -crf 18 -b:v 0 out/CampusMaps-logo-alpha.webm
    ffmpeg -f lavfi -i color=0x00FF00:s=1920x1080:r=60 -framerate 60 -i frames/f_%05d.png -filter_complex "[0][1]overlay=shortest=1" -c:v libx264 -crf 16 -pix_fmt yuv420p out/CampusMaps-logo-greenscreen.mp4

Use the .mov in Premiere / Resolve / Final Cut (straight alpha), the .webm on the web,
the green-screen .mp4 with chroma key in CapCut / iMovie / Clipchamp.
