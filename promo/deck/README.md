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
