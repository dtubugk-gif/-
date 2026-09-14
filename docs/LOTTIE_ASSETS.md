# Lottie assets to commission

The app ships with procedurally generated Lottie files for every mascot and stage (`tools/mascots/generate_lottie.py`).
They are complete and animated, but flat-shape placeholders in style. Replace them one file at a time by
dropping a file with the **same name** into the mascot folder; no code or manifest change is needed.

## Global spec (applies to every file)

| Property | Value |
|---|---|
| Format | Lottie JSON (bodymovin ≥ 5.7), shape layers only – no images, no expressions, no effects |
| Canvas | 512 × 512, transparent background |
| Frame rate | 30 fps |
| Loop | seamless; first and last frame identical |
| Idle length | healthy stages 3.5–4 s, mid stages 2.5–3 s, rotten stages 2 s (spec: 2–4 s) |
| Style | dark, flat, illustrative-funny; 3–5 colours per mascot; thick 10–12 px outlines optional |
| Safe area | keep the character inside the central 440 px; flies/drips may use the full canvas |
| File size | ≤ 60 KB per stage |
| Layer count | ≤ 25 (the widget renders frame 0 through `LottieDrawable`) |
| Theme colour | body colour at stage 100 must match `themeColor` in the manifest |
| Testing | `./gradlew :feature:mascot:testDebugUnitTest` (parser) + open the gallery preview slider |

## Stage language (same for all mascots)

| Stage | Health | Idle character | Visual cues |
|---|---|---|---|
| `stage_100` | pristine | slow, wide breathing; one blink per loop | saturated colour, bright eyes, smile |
| `stage_80` | fresh | breathing slightly faster | one small imperfection, eyes open |
| `stage_60` | worn | shorter breath, occasional twitch | desaturated 30 %, half-lidded eyes, flat mouth |
| `stage_40` | wilted | breathing broken into two short cycles, sag | 3 spots/patches, frown, drooping parts |
| `stage_20` | rotting | jitter, hold-frames, no smooth breath | X eyes, 3 orbiting flies, one drip |
| `stage_0` | rotten | irregular twitches, sagging further, drips loop | 5 flies, ooze/smoke, tongue out or "…" |

## Per-mascot list (36 files)

### brain – מוח (theme `#FF7EB6`, personality cynical, reaction *pulse*)
| File | Duration | Notes |
|---|---|---|
| `stage_100.json` | 4.0 s | pink lobes, glossy gyri, stem; smug smile |
| `stage_80.json` | 3.6 s | one gyrus slightly darker |
| `stage_60.json` | 3.2 s | greyer pink, half-lidded eyes |
| `stage_40.json` | 2.8 s | green-grey patches, frown, cracks |
| `stage_20.json` | 2.4 s | drips from both lobes, flies, X eyes |
| `stage_0.json` | 2.0 s | mostly olive, sagging lobes, tongue |

### plant – עציץ (theme `#6EDB8F`, dramatic, reaction *wilt*)
| File | Duration | Notes |
|---|---|---|
| `stage_100.json` | 4.0 s | five upright leaves swaying ±4°, terracotta pot with a face |
| `stage_80.json` | 3.6 s | one leaf trembling |
| `stage_60.json` | 3.2 s | leaves at 30° droop, yellow tips |
| `stage_40.json` | 2.8 s | leaves at 60°, brown spots, pot face frowning |
| `stage_20.json` | 2.4 s | leaves touching the soil, flies |
| `stage_0.json` | 2.0 s | stem bent double, black ooze on the soil, flies |

### goldfish – דג זהב (theme `#FFA94D`, confused, reaction *flip*)
| File | Duration | Notes |
|---|---|---|
| `stage_100.json` | 4.0 s | fish swims a figure-eight, three bubbles rising, clear water |
| `stage_80.json` | 3.6 s | slightly slower, water a shade greener |
| `stage_60.json` | 3.2 s | fish tilted 20°, murky water, algae blob |
| `stage_40.json` | 2.8 s | tilted 70°, floating upward, no bubbles |
| `stage_20.json` | 2.4 s | belly-up at the surface, X eyes, flies above the bowl |
| `stage_0.json` | 2.0 s | fully belly-up, brown water, five flies |

### cat – חתול (theme `#B48CFF`, judgmental, reaction *turn_away*)
| File | Duration | Notes |
|---|---|---|
| `stage_100.json` | 4.0 s | wide slit-pupil eyes, ear twitch, one slow blink, tiny smile |
| `stage_80.json` | 3.6 s | eyes narrowed 20 % (already judging) |
| `stage_60.json` | 3.2 s | ears drooping 30°, dull fur |
| `stage_40.json` | 2.8 s | fur patches, ears at 60°, frown |
| `stage_20.json` | 2.4 s | X eyes, flies, fur greening |
| `stage_0.json` | 2.0 s | ears flat, tongue out, five flies |

### robot – רובוט (theme `#5AC8FA`, bureaucratic, reaction *glitch*)
| File | Duration | Notes |
|---|---|---|
| `stage_100.json` | 4.0 s | gentle hover, antenna ball pulsing, steady LED eyes, 5-segment mouth |
| `stage_80.json` | 3.6 s | one mouth segment dimmer |
| `stage_60.json` | 3.2 s | rust patches, antenna bent 20°, eyes flicker |
| `stage_40.json` | 2.8 s | crack across the screen, 3-segment mouth, antenna 40° |
| `stage_20.json` | 2.4 s | one eye dead, sparks, flies (for consistency) |
| `stage_0.json` | 2.0 s | smoke puffs rising, antenna 55°, everything rust |

### potato – תפוח אדמה (theme `#D4A45A`, indifferent, reaction *roll*)
| File | Duration | Notes |
|---|---|---|
| `stage_100.json` | 4.0 s | barely-there breathing, one tiny sprout, dot eyes, flat mouth |
| `stage_80.json` | 3.6 s | two sprouts |
| `stage_60.json` | 3.2 s | four sprouts, greenish tinge |
| `stage_40.json` | 2.8 s | long wild sprouts, dark spots, softening outline |
| `stage_20.json` | 2.4 s | sprout forest, flies, ooze |
| `stage_0.json` | 2.0 s | collapsed blob, five flies, drips |

## Optional extras (not required by the code)

| File | Purpose | Spec |
|---|---|---|
| `<id>/reaction.json` | replaces the transform-based long-press reaction | 1.1 s, non-looping; parser support would be a one-line addition in `MascotManifestParser` |
| `<id>/reaction.wav` | reaction sound | ≤ 0.5 s, mono 22.05 kHz 16-bit, peak −6 dBFS; current files are synthesised |
| `sounds/score_up.wav`, `sounds/score_down.wav` | score change sounds | ≤ 0.6 s, same format |
