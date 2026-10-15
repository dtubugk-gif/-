# Nova Sling

A gravity slingshot puzzle game. One file, no dependencies: open `index.html` in any modern browser.

**How to play**

- Drag anywhere to pull the sling back, release to launch. The probe flies the opposite way.
- Bend your path with gravity from planets, bounce off bumpers, steer with magenta repulsors, and never touch a black hole.
- Fall into the portal to clear the level. Stars: 1 for clearing it, 1 for collecting every star, 1 for finishing within par.
- `R` restarts, `M` mutes, `Esc` goes back. Time slows while you aim.

10 levels, synthesised sound, touch support, progress saved in the browser.

**Physics**: fixed 120 Hz step, softened gravity from every body, circle collisions with restitution and friction, moving bodies on closed-form orbits (so the aim preview is exact). `window.NovaSling.solve(levelIndex)` brute-forces aim angles to prove each level is beatable.
