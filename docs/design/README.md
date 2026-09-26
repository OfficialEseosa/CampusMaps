# CampusMaps design hand-off

This folder is a self-contained package for a visual designer who has never seen CampusMaps. It explains what the app does, which screens exist, what each one must show, and what to design first.

## How to view

Open `wireframes.html` in any browser (double-click it). It needs no server and no network; the images load from `img/`. It works at phone width and has a light/dark switch at the top.

## What is inside

| File | What it is |
|---|---|
| `wireframes.html` | The whole hand-off in one page: a product summary, the three demos, the screen flow diagram, and for each screen (S1 Destination, S1b Route options, S2 AR guidance, Arrived, S3 Glasses mode, Settings sheet, Debug overlay, Watch face) a current screenshot, a low-fidelity wireframe, an element table with the exact copy, and design notes. After those come the states and edge cases, content and tone, constraints (M3 tokens, type, icons, the AR arrow, the watch), a list of spec conflicts, and the prioritised asks. |
| `img/` | Screenshots of the current unstyled build (Material 3 defaults), from the `survey36` emulator at 1080 × 2400, downscaled to 540 px wide. |

## Conventions in the wireframes

- A solid outline means the element exists in the build today.
- A dashed orange outline means it is not built yet or is proposed.
- Camera-view screens (S2, Arrived) and S3 are always dark, whatever the page theme.

## Sources

The package is based on `docs/07-app-shell-ui.md` (screen spec), `docs/05-ar-guidance.md` (AR content), `docs/01-demos.md`, `docs/13-pitch.md`, `docs/08-watch-companion.md`, `docs/20-ui-status.md`, and the strings in `app/src/main/kotlin/com/campusmaps/ui/` and `core/.../routing/Instructions.kt` as of 2026-09-25. If the code changes, the copy tables may drift; the code wins.
