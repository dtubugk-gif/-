# Goat Mayhem

A 3D goat-chaos sandbox in the spirit of Goat Simulator. Three.js for rendering, cannon.js for (deliberately bad) physics, every sound synthesized with WebAudio. No build step.

## Run it

Serve the folder and open `index.html`:

```
cd goat-mayhem
python3 -m http.server 8000
# open http://localhost:8000
```

## Controls

| Key | Action |
| --- | --- |
| WASD / arrows | Run |
| Shift | Sprint |
| Space | Jump (hold in the air to flail) |
| F / left click | Headbutt |
| E / right click | Lick: tongue sticks to things and drags them (or you) |
| R | Ragdoll flop on/off |
| G | Moon gravity |
| T | Slow motion |
| J | Jetpack (find it on the scaffold tower first) |
| B | Barrel rain |
| Enter | Respawn |
| Mouse / drag | Look, wheel to zoom |

Phones get a joystick and buttons.

## What's in town

Explosive barrels that chain-react, panicking townsfolk who ragdoll and get back up, cars that loop the ring road and launch you on contact, a windmill that swats goats, trampolines, ramps, a crate pyramid, dominoes, a bowling lane, and six golden goats to collect. Combos multiply your score; random "physics bugs" occasionally fire things into the sky.

## Files

- `js/world.js` renderer, physics world, town layout
- `js/entities.js` props, trampolines, windmill, barrels, explosions, particles
- `js/people.js` townsfolk, cars, golden goats, jetpack
- `js/goat.js` goat model, controller, headbutt, tongue, ragdoll
- `js/main.js` input, camera, scoring, main loop
- `js/audio.js` synthesized sound effects
