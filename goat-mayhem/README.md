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
| Q | Bleat: scares people, shoves small things |
| R | Ragdoll flop on/off |
| G | Moon gravity |
| T | Slow motion |
| J | Jetpack (find it on the scaffold tower first) |
| B | Barrel rain |
| Enter | Respawn |
| Mouse / drag | Look, wheel to zoom |

On phones: left thumb stick (push far to sprint), drag anywhere to look, and JUMP / BUTT / LICK / FLOP / BAA! buttons (JET appears once you find the jetpack). The ☰ button has moon gravity, slow motion, barrel rain and respawn. The camera drifts behind the goat on its own, and quality settings drop automatically on phones.

## What's in town

Explosive barrels that chain-react, panicking townsfolk who ragdoll and get back up, cars that loop the ring road and launch you on contact, a windmill that swats goats, trampolines, ramps, a crate pyramid, dominoes, a bowling lane, and six golden goats to collect. Seven challenges (dominoes, a bowling strike, wrecked cars, chained explosions and more) sit next to the statue counter. The goat is a real multi-part ragdoll: when it flops, its neck, head and legs come loose and flail. Combos multiply your score; random "physics bugs" occasionally fire things into the sky.

## Files

- `js/world.js` renderer, physics world, cel-shaded materials, phone quality profile
- `js/town.js` houses with walkable roofs, trees, scenery, street furniture
- `js/entities.js` props, trampolines, windmill, barrels, explosions, particles
- `js/people.js` townsfolk, cars, golden goats, jetpack
- `js/goatmodel.js` goat model parts (also used for golden statues and the ragdoll)
- `js/goat.js` goat controller, headbutt, tongue, bleat, multi-part ragdoll
- `js/main.js` input, camera, scoring, main loop
- `js/audio.js` synthesized sound effects
