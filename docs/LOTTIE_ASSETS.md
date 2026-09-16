# Lottie assets: what ships and what to commission

The app ships with 36 Lottie files (six mascots × six stages) generated from the design-canvas SVG art by
`tools/mascots/generate_lottie.py`. They are the canvas drawings, animated: the body with its gradient, the
three face variants (healthy / mid / rotten), the mould patch, the flies and the stink lines. Replace any of them
one file at a time by dropping a file with the **same name** into the mascot folder; no code or manifest change
is needed. Only the goldfish, plant and robot are drawn at icon level in the canvas, so those are the first
candidates for hand-drawn art.

## Global spec (applies to every file)

| Property | Value |
|---|---|
| Format | Lottie JSON (bodymovin ≥ 5.7), shape layers only – no images, no expressions, no effects |
| Canvas | 200 × 200 (the canvas viewBox), transparent background; any square size scales, keep the character centred |
| Frame rate | 30 fps |
| Loop | seamless; first and last frame identical |
| Idle length | healthy stages 3.2 s breathing, mid stages 2.4 s shallow breathing, rotten stages a 2.4 s signature motion per mascot (spec: 2–4 s) |
| Style | canvas style: warm flat shapes, `#8a6220` outlines at 4 px, no drop shadows; each mascot decays toward its own rot palette (see below) |
| Safe area | keep the character inside the central 170 px; flies/stink may use the full canvas |
| File size | ≤ 60 KB per stage (shipped files are 6–25 KB) |
| Layer count | ≤ 25 (the widget and the notification render frame 0 through `LottieDrawable`) |
| Theme colour | body colour at stage 100 must match `themeColor` in the manifest |
| Testing | `./gradlew :feature:mascot:testDebugUnitTest` (parser) + open the gallery preview slider |

## Stage language (shared skeleton, from the rot-cycle sheet)

The six stages share a health ladder and a face ladder; everything else (palette, damage, motion, extras) is
the mascot's own and is listed per mascot below. A potato moulds and leaks; a brain does not.

| Stage | Health | Face | Rot blend | Idle |
|---|---|---|---|---|
| `stage_100` | pristine | healthy | 0 % | slow, wide breathing (±3 % scale) |
| `stage_80` | fresh | healthy | 12 % | same breathing |
| `stage_60` | worn | mid (half-lidded, flat mouth) | 38 % | shallow breathing (±1.8 %) |
| `stage_40` | wilted | mid, first damage mark | 58 % | shallow breathing |
| `stage_20` | rotting | rotten (X eyes) | 82 % | the mascot's signature motion |
| `stage_0` | rotten | rotten | 100 % | signature motion plus a permanent deformation |

"Rot blend" is how far every fill and stroke has moved from its canvas colour toward **that mascot's** rot
palette (`ROT_T` in the generator; palettes are the `*_ROT` constants). Only the potato uses the sheet's
`#6f6428` / `#37320e`; the others go to grey-violet (brain), ash (cat), dry straw (plant), pond green (goldfish)
and rust (robot).

## Per-mascot list (36 files)

Durations: stage 100/80 = 3.2 s (96 frames), stage 60/40 = 2.4 s (72 frames), stage 20/0 = a 2.4 s idle
inside a 9.6 s composition (288 frames) so the extras (fly orbits, drips, rising wisps, falling leaves) loop
without a seam.

### potato – תפוח אדמה (theme `#E0B64F`, indifferent, reaction *roll*) – full canvas art
Decays like a potato: **sprouts push out, mould spreads, it leaks, the flies arrive.** Rot palette `#6f6428` /
`#37320e`; idle at 20/0 is the sheet's stepped twitch, with a 106/94 squash at 0.
| File | Notes |
|---|---|
| `stage_100.json` | canvas "potato healthy": dot eyes, flat mouth, two sprouts |
| `stage_80.json` | same, slightly duller |
| `stage_60.json` | mid face, a third sprout pushes out of the side |
| `stage_40.json` | mid face, four sprouts, first mould patch |
| `stage_20.json` | canvas "potato rotten" body with drips, five sprouts, two mould patches, one fly |
| `stage_0.json` | canvas rotten mould, six sprouts, two flies, stink, squashed body |

### brain – מוח (theme `#F08BAB`, cynical, reaction *pulse*) – canvas art (mid state drawn in the canvas)
Decays like tissue: **goes grey-violet, the gyri smooth out, the lobes sag, it bruises and leaks. Never any
flies or mould.** Rot palette `#6e5a72` / `#3b2a40`; idle at 20/0 is a slow ±2.5° wobble, with a 104/96
slump at 0.
| File | Notes |
|---|---|
| `stage_100.json` | pink lobes with gyri, arched brows and full smile |
| `stage_80.json` | same, a shade duller |
| `stage_60.json` | canvas "brain mid" (straight brows, smirk), gyri fading, lobes at 97 % height |
| `stage_40.json` | mid face, first bruise on the left lobe, lobes at 94 % |
| `stage_20.json` | rotten face, two bruises, gyri almost gone, lobes at 90 %, one drip |
| `stage_0.json` | three bruises, two drips, lobes at 86 %, slumped |

### cat – חתול (theme `#E0C26A`, surface tint `#8D94A3`, judgmental, reaction *turn_away*) – canvas art
Decays like fur: **goes to ash, the ears flatten, the coat mats into clumps, loose hair drifts off. It
shivers instead of twitching.** Rot palette `#5c6157` / `#2b2e2a`; idle at 20/0 is a 2 px shiver, with a
permanent −3° lean at 0.
| File | Notes |
|---|---|
| `stage_100.json` | canvas "cat healthy": grey fur, gold eyes, ear twitch |
| `stage_80.json` | same, coat slightly dull |
| `stage_60.json` | mid face, ears drooped 18°, dull fur |
| `stage_40.json` | mid face, ears at 34°, first matted clump on the flank |
| `stage_20.json` | rotten face, ears at 55°, two clumps, one tuft of hair drifting off |
| `stage_0.json` | ears flat (70°), three clumps, two tufts drifting, leaning |

### plant – עציץ (theme `#7DC9A6`, dramatic, reaction *wilt*) – icon-level in the canvas, commission first
Decays like a houseplant: **leaves droop and dry to straw, the soil moulds, the pot cracks, leaves break off.
It sways instead of twitching.** Leaf palette `#7a6a2a` / `#4a4419`, pot rots at half speed toward `#6b4a3a`;
idle at 20/0 is a ±3° / ±4.5° sway.
| File | Notes |
|---|---|
| `stage_100.json` | upright leaves, pot with a healthy face |
| `stage_80.json` | same, leaves slightly yellowed |
| `stage_60.json` | leaves drooping 28°, mid face |
| `stage_40.json` | leaves at 48°, first mould patch on the soil |
| `stage_20.json` | leaves at 72°, rotten face, soil mould, one crack in the pot, one leaf falling |
| `stage_0.json` | leaves at 88°, two cracks, two leaves falling, wider sway |

### goldfish – דג זהב (theme `#F0A457`, confused, reaction *flip*) – icon-level in the canvas, commission first
Decays like a neglected bowl: **water goes green then brown, the fish tilts then floats belly-up and bobs,
algae rise, scum forms.** Water palette `#4e6b3d` / `#3a4519`, fish `#8a7a3c`; no twitch, the belly-up fish
bobs on a 3.2 s sine.
| File | Notes |
|---|---|
| `stage_100.json` | fish in clear water, swimming, two bubbles |
| `stage_80.json` | water a shade greener |
| `stage_60.json` | fish tilted 22°, worried brow, murky water |
| `stage_40.json` | tilted 48°, first strand of algae rising |
| `stage_20.json` | belly-up and bobbing, squiggle eye, two algae strands, thin scum |
| `stage_0.json` | brown water, thick scum, one fly over the bowl, bowl tilted −2° |

### robot – רובוט (theme `#7DE3D2`, bureaucratic, reaction *glitch*) – icon-level in the canvas, commission first
Decays like hardware: **rusts, the antenna bends, the screen cracks, one eye dies, it sparks and smokes. It
glitches, never twitches.** Rust palette `#7a4a2e` / `#3a2416`; idle at 20/0 is a hold-frame glitch jump,
with a permanent −4° lean at 0.
| File | Notes |
|---|---|
| `stage_100.json` | gentle hover, antenna ball pulsing, LED eyes |
| `stage_80.json` | same, first tarnish |
| `stage_60.json` | antenna bent 14°, left eye flickering, rust blend |
| `stage_40.json` | antenna at 26°, first crack in the screen, first rust patch |
| `stage_20.json` | antenna at 40°, left eye dead, right eye sparking, one crack, two rust patches, one smoke wisp |
| `stage_0.json` | antenna at 52°, two cracks, three rust patches, two smoke wisps, leaning |

## Optional extras (not required by the code)

| File | Purpose | Spec |
|---|---|---|
| `<id>/reaction.json` | replaces the transform-based long-press reaction | 1.1 s, non-looping; parser support would be a one-line addition in `MascotManifestParser` |
| `<id>/reaction.wav` | long-press vocalisation | ≤ 0.7 s, mono 22.05 kHz 16-bit, peak −2 dBFS; current files are formant-synthesised voices in each pet's character |
| `sounds/score_up.wav`, `sounds/score_down.wav` | score change sounds | ≤ 0.6 s, same format |
