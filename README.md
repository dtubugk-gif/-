# Periapsis

A one-tap orbit arcade game in a single HTML file. No build step, no dependencies.

Your craft circles a star on one of two orbits. Tap (or press Space) to hop between them, dodge debris, and collect sparks.

**Play:** open `index.html` in any modern browser.

## Scoring

| Event | Points |
|---|---|
| Debris passed | +1 |
| Spark collected | +3 |
| Close call (dodged at the last moment) | +2 bonus |

## Adaptive interface

The UI adapts to how you play instead of showing the same screen every time:

- **Crash diagnosis.** The coach on the game-over screen tells you *why* you crashed: you hopped onto a rock, hopped too late, tapped in a frenzy, or never reacted. Each cause gets its own tip.
- **Difficulty suggestions.** Three short runs in a row offer Assist mode. A strong run on Assist or Standard offers the next mode up. The suggestion shows up as a one-tap chip.
- **Assist cues.** In Assist mode the game rings the rock you need to dodge.
- **First-run hint.** Shown only until your first hop.

## Controls

- Tap / click / Space / ↑ / ↓: hop orbits
- P or Esc: pause (the game also pauses when the tab is hidden)

Respects `prefers-reduced-motion` (no screen shake, no trails, minimal particles). Best scores are saved per mode in local storage.
