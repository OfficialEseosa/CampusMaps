# Transparent clips for the demo video

Rendered from the deck modules with `promo/deck/capture.js` on a virtual clock (deterministic
frames, real alpha). Outputs in `out/` are not committed.

    cd promo/deck; python -m http.server 8770      # in one terminal
    node capture.js building3d 20 <frames-dir> --b64=<base64 of {"code":"CS","autoRotateSpeed":6}> --fps=30 --warm=1 --pre="window.__handle.play()"
    node capture.js explainer 35 <frames-dir> --fps=30 --pre="window.__handle.play()"

Then encode the frames like promo/logo-animation/README.md (ProRes 4444 for the editor,
WebM for the web, green-screen MP4 for CapCut / iMovie). Kill leftover headless Edge after a run:
`Get-Process msedge | Where-Object { $_.MainWindowHandle -eq 0 } | Stop-Process -Force`.
