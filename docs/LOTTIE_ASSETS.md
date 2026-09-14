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
| Idle length | healthy stages 3.2 s breathing, mid stages 2.4 s shallow breathing, rotten stages 2.4 s stepped twitch (spec: 2–4 s) |
| Style | canvas style: warm flat shapes, `#8a6220` outlines at 4 px, no drop shadows; colour ramp `#dda94a → #b58f35 → #6f6428` from healthy to rotten |
| Safe area | keep the character inside the central 170 px; flies/stink may use the full canvas |
| File size | ≤ 60 KB per stage (shipped files are 6–25 KB) |
| Layer count | ≤ 25 (the widget and the notification render frame 0 through `LottieDrawable`) |
| Theme colour | body colour at stage 100 must match `themeColor` in the manifest |
| Testing | `./gradlew :feature:mascot:testDebugUnitTest` (parser) + open the gallery preview slider |

## Stage language (same for all mascots, from the rot-cycle sheet)

| Stage | Health | Idle character | Visual cues |
|---|---|---|---|
| `stage_100` | pristine | slow, wide breathing (±3 % scale) | healthy face, canvas colours untouched (rot blend 0 %) |
| `stage_80` | fresh | same breathing | healthy face, rot blend 12 % |
| `stage_60` | worn | shallow breathing (±1.8 %) | mid face (half-lidded, flat mouth), rot blend 38 % |
| `stage_40` | wilted | shallow breathing | mid face, mould patch appears, rot blend 58 % |
| `stage_20` | rotting | stepped twitch with hold frames, ±1.2° rotation | rotten face (X eyes), mould, one orbiting fly, rot blend 82 % |
| `stage_0` | rotten | twitch plus a permanent 106/94 squash | rotten face, two flies, stink lines rising, full rot palette (`#6f6428` / `#37320e`) |

"Rot blend" is how far every fill and stroke has moved from its canvas colour toward the rot palette
(`ROT_T` in the generator); the mid colour `#b58f35` on the canvas sits at roughly 40 %.

## Per-mascot list (36 files)

Durations: stage 100/80 = 3.2 s (96 frames), stage 60/40 = 2.4 s (72 frames), stage 20/0 = 2.4 s twitch
inside a 9.6 s composition (288 frames) so the fly orbits (3.2 s and 4.8 s) and stink lines (2.4 s and 3.2 s)
loop without a seam.

### potato – תפוח אדמה (theme `#E0B64F`, indifferent, reaction *roll*) – full canvas art
| File | Notes |
|---|---|
| `stage_100.json` | canvas "potato healthy": dot eyes, flat mouth, two sprouts |
| `stage_80.json` | same, slightly duller |
| `stage_60.json` | mid face, first mould patch |
| `stage_40.json` | mid face, larger mould, darker outline |
| `stage_20.json` | canvas "potato rotten" face, one fly |
| `stage_0.json` | two flies, stink, squashed body |

### brain – מוח (theme `#F08BAB`, cynical, reaction *pulse*) – canvas art (mid state drawn in the canvas)
| File | Notes |
|---|---|
| `stage_100.json` | pink lobes with gyri, smug healthy face |
| `stage_80.json` | one gyrus darker |
| `stage_60.json` | canvas "brain mid" |
| `stage_40.json` | mid face, mould on the left lobe |
| `stage_20.json` | rotten face, fly |
| `stage_0.json` | two flies, stink, lobes sagging |

### cat – חתול (theme `#E0C26A`, surface tint `#8D94A3`, judgmental, reaction *turn_away*) – canvas art
| File | Notes |
|---|---|
| `stage_100.json` | canvas "cat healthy": grey fur, gold eyes, ear twitch |
| `stage_80.json` | eyes narrowed |
| `stage_60.json` | mid face, dull fur |
| `stage_40.json` | mid face, mould behind the ear |
| `stage_20.json` | rotten face, fly |
| `stage_0.json` | two flies, stink, ears flat |

### plant – עציץ (theme `#7DC9A6`, dramatic, reaction *wilt*) – icon-level in the canvas, commission first
| File | Notes |
|---|---|
| `stage_100.json` | upright leaves swaying, pot with a healthy face |
| `stage_80.json` | one leaf trembling |
| `stage_60.json` | leaves drooping 30°, mid face |
| `stage_40.json` | leaves at 60°, mould on the soil |
| `stage_20.json` | leaves touching the soil, rotten face, fly |
| `stage_0.json` | stem bent double, two flies, stink |

### goldfish – דג זהב (theme `#F0A457`, confused, reaction *flip*) – icon-level in the canvas, commission first
| File | Notes |
|---|---|
| `stage_100.json` | fish in clear water, bubbles |
| `stage_80.json` | water a shade greener |
| `stage_60.json` | fish tilted 20°, murky water, mid face |
| `stage_40.json` | tilted 70°, algae mould |
| `stage_20.json` | belly-up, rotten face, fly above the bowl |
| `stage_0.json` | brown water, two flies, stink |

### robot – רובוט (theme `#7DE3D2`, bureaucratic, reaction *glitch*) – icon-level in the canvas, commission first
| File | Notes |
|---|---|
| `stage_100.json` | gentle hover, antenna ball pulsing, LED eyes |
| `stage_80.json` | one mouth segment dimmer |
| `stage_60.json` | rust patches, mid face |
| `stage_40.json` | crack across the screen, rust mould |
| `stage_20.json` | one eye dead, rotten face, fly |
| `stage_0.json` | smoke as stink lines, two flies, everything rust |

## Optional extras (not required by the code)

| File | Purpose | Spec |
|---|---|---|
| `<id>/reaction.json` | replaces the transform-based long-press reaction | 1.1 s, non-looping; parser support would be a one-line addition in `MascotManifestParser` |
| `<id>/reaction.wav` | reaction sound | ≤ 0.5 s, mono 22.05 kHz 16-bit, peak −6 dBFS; current files are synthesised |
| `sounds/score_up.wav`, `sounds/score_down.wav` | score change sounds | ≤ 0.6 s, same format |
