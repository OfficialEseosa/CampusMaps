# Demo deck

Offline reveal.js deck for the expo. Everything is vendored; no network needed.

    cd promo/deck
    python -m http.server 8770
    # open http://localhost:8770 in Chrome or Edge

Keys: arrows to move, F fullscreen, S speaker notes (needs the http server, not file://),
R replays the current slide's animation, Esc overview. Drag on the 3D slides to orbit.

Slides live in index.html. Each animated slide is a module in js/ with
`mount(el, opts) -> { play, pause, destroy }`; the shell plays the current slide's module
and pauses the others, so only one WebGL loop runs at a time.

## Deploy on Vercel

Static site, no build step. In Vercel: New Project, import this repo, set **Root Directory** to
`promo/deck`, Framework Preset **Other**, leave build command and output directory empty, deploy.
`vercel.json` in this folder sets the cache headers. The same works with the CLI:

    cd promo/deck
    vercel --prod

Everything the deck needs is inside this folder (fonts, media, vendored reveal.js, GSAP and
three.js), so it runs offline once loaded. The URL hash is the slide number (`/#/5` is Klaus 3D).
