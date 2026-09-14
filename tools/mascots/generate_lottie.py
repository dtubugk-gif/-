#!/usr/bin/env python3
"""Generates the six built-in mascots as Lottie files (one per stage) from the design-canvas SVGs.

Run from the repository root:
    python3 tools/mascots/generate_lottie.py

Art source: tools/mascots/mascot_art.py (verbatim SVG from the canvas). Each stage reuses the canvas
shapes: the body with its gradient, the three face variants (healthy / mid / rotten), the mould, the
flies and the stink lines. Colours slide toward the rot palette by stage, and the idle loop changes
character: healthy = 3.2 s breathing, mid = 2.4 s shallow breathing, rotten = 2.4 s stepped twitch.
Output is deterministic.
"""

from __future__ import annotations

import copy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

import mascot_art as art  # noqa: E402
from lottie_builder import animated, group, layer, mix, static, transform  # noqa: E402
from svg_lottie import groups_from_svg  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "feature", "mascot", "src", "main", "assets", "mascots")
FPS = 30
SIZE = 200
STAGES = [100, 80, 60, 40, 20, 0]
ROT_T = {100: 0.0, 80: 0.12, 60: 0.38, 40: 0.58, 20: 0.82, 0: 1.0}
ANCHOR = [100, 176, 0]

# Rot palette from the canvas (potato stage 0): body #6f6428, stroke #37320e, ink #23200c, mould #5c6e2a.
ROT_FILL = "#6f6428"
ROT_STROKE = "#37320e"
ROT_INK = "#23200c"
RUST_FILL = "#7a4a2e"
RUST_STROKE = "#3a2416"

EASE_IN_OUT = ((0.42, 0.0), (0.58, 1.0))


# ------------------------------------------------------------------ colour helpers


def _rgb_hex(color):
    return "#%02x%02x%02x" % tuple(int(round(c * 255)) for c in color[:3])


def _lighten(color, amount):
    return [min(1.0, c + (1 - c) * amount) for c in color[:3]] + [1]


def _darken(color, amount):
    return [max(0.0, c * (1 - amount)) for c in color[:3]] + [1]


def blend_toward_rot(items, t, fill_target=ROT_FILL, stroke_target=ROT_STROKE, fade_highlights=True):
    """Walks Lottie items and slides every colour toward the rot palette by t (0..1)."""
    for item in items:
        ty = item.get("ty")
        if ty == "gr":
            blend_toward_rot(item["it"], t, fill_target, stroke_target, fade_highlights)
        elif ty == "fl":
            c = item["c"]["k"]
            if c[:3] == [0, 0, 0]:  # shadows stay black
                continue
            if c[:3] == [1, 1, 1] and fade_highlights:  # eye highlights / glints fade out
                item["o"] = static(item["o"]["k"] * (1 - t))
                continue
            item["c"] = static(mix(_rgb_hex(c), fill_target, t * 0.85))
        elif ty == "st":
            c = item["c"]["k"]
            item["c"] = static(mix(_rgb_hex(c), stroke_target, t * 0.9))
        elif ty == "gf":
            k = item["g"]["k"]["k"]
            n = item["g"]["p"]
            out = list(k)
            target = mix(fill_target, fill_target, 0)
            targets = [_lighten(target, 0.25), target, _darken(target, 0.22)] if n == 3 else [target] * n
            for s in range(n):
                base = k[s * 4 + 1 : s * 4 + 4]
                tgt = targets[min(s, len(targets) - 1)]
                out[s * 4 + 1 : s * 4 + 4] = [base[i] + (tgt[i] - base[i]) * t * 0.9 for i in range(3)]
            item["g"]["k"] = static(out)
    return items


def fade(items, factor):
    for item in items:
        if item.get("ty") == "gr":
            tr = item["it"][-1]
            tr["o"] = static(tr["o"]["k"] * factor)
    return items


def recolor(items, mapping):
    """Replaces exact hex colours (fills and strokes) with others; mapping = {"#3b2c12": "#4a1f2e"}."""
    for item in items:
        ty = item.get("ty")
        if ty == "gr":
            recolor(item["it"], mapping)
        elif ty in ("fl", "st"):
            hexv = _rgb_hex(item["c"]["k"])
            if hexv in mapping:
                item["c"] = static(mix(mapping[hexv], mapping[hexv], 0))
    return items


def placed(items, cx=100, cy=100, sx=1.0, sy=1.0, ax=100, ay=100, rot=0.0, name="part"):
    """Wraps items in a group whose transform moves the design anchor (ax, ay) to (cx, cy)."""
    return group(items, name, transform(p=(cx, cy), a=(ax, ay), s=(sx * 100, sy * 100), r=rot))


def pick(svg, indices):
    """Lottie groups for the SVG leaves at these indices (order preserved)."""
    wanted = set(indices)
    return groups_from_svg(svg, select=lambda i, el: i in wanted)


def visible(items):
    """Canvas hides mid/rot faces with opacity=0 until CSS reveals them; we turn them fully on."""
    for item in items:
        if item.get("ty") == "gr":
            item["it"][-1]["o"] = static(100)
    return items


# ------------------------------------------------------------------ animation helpers


def cycle(period, total, keyframes_fn):
    """Tiles a keyframe list (frames in 0..period) across a longer composition so loops stay seamless."""
    kfs = []
    for start in range(0, total, period):
        for frame, value in keyframes_fn():
            f = start + frame
            if f > total:
                break
            if kfs and kfs[-1][0] == f:
                continue
            kfs.append((f, value))
    if kfs[-1][0] != total:
        kfs.append((total, keyframes_fn()[0][1]))
    return kfs


def breathe_scale(period, total, amount=3.0):
    return animated(cycle(period, total, lambda: [(0, [100, 100, 100]), (period // 2, [100 + amount, 100 - amount, 100]), (period, [100, 100, 100])]), ease=EASE_IN_OUT)


def twitch_position(period, total, base=(100, 176)):
    steps = [(0.0, 0, 0), (0.12, -1, 1), (0.24, 1, 0), (0.36, 0, 2), (0.60, -2, 0), (0.72, 1, 1)]
    kfs = cycle(period, total, lambda: [(round(p * period), [base[0] + dx, base[1] + dy, 0]) for p, dx, dy in steps] + [(period, [base[0], base[1], 0])])
    return animated(kfs, hold=True)


def twitch_rotation(period, total):
    steps = [(0.0, 0), (0.12, -1.2), (0.24, 0.8), (0.36, 0), (0.60, -0.6), (0.72, 1.0)]
    kfs = cycle(period, total, lambda: [(round(p * period), r) for p, r in steps] + [(period, 0)])
    return animated(kfs, hold=True)


def fly_group(items, period, total, offsets, name):
    """Animates a fly (dot + wings) along the canvas keyframes; offsets are relative translations."""
    n = len(offsets)
    kfs = cycle(period, total, lambda: [(round(i * period / n), [dx, dy]) for i, (dx, dy) in enumerate(offsets)] + [(period, list(offsets[0]))])
    return group(items, name, transform(p=animated(kfs, ease=EASE_IN_OUT)))


FLY1 = [(0, 0), (9, -7), (2, -13), (-8, -5)]
FLY2 = [(0, 0), (-10, -9), (6, -14)]


def stink_group(items, period, total, name):
    pos = cycle(period, total, lambda: [(0, [0, 0]), (period, [0, -16])])
    op = cycle(period, total, lambda: [(0, 0), (round(period * 0.25), 50), (period, 0)])
    return group(items, name, transform(p=animated(pos), o=animated(op)))


# ------------------------------------------------------------------ shared kits (from the potato cycle)

CYCLE = art.POTATO_CYCLE
FACE_HEALTHY = [6, 7, 8, 9, 10, 11, 12]  # eyes, glints, blush, smile (brows come from each mascot)
FACE_MID = [13, 14, 15, 16, 17, 18, 19]  # faded sprout, eyes, glints, worried brows, flat mouth
FACE_ROT = [20, 21, 22, 23, 24]  # squint squiggles, dots, brows, wavy mouth
MOULD_FIRST = [25, 26, 27]
MOULD_ALL = [25, 26, 27, 28, 29]
FLY_A = [30, 31]
FLY_B = [32, 33]
STINK_A = [34]
STINK_B = [35]
POTATO_INK = "#3b2c12"


def face_kit(stage, ink, cx=100, cy=100, sx=1.0, sy=1.0, blush=None):
    """The canvas face for a stage, re-inked and re-positioned for another mascot."""
    if stage >= 80:
        items = visible(pick(CYCLE, [6, 7, 8, 9, 12]))
    elif stage >= 40:
        items = visible(pick(CYCLE, [14, 15, 16, 17, 18, 19]))
    else:
        items = visible(pick(CYCLE, FACE_ROT))
    mapping = {POTATO_INK: ink, "#8a6220": _rgb_hex(mix(ink, ink, 0)), "#37320e": ROT_INK, "#23200c": ROT_INK}
    recolor(items, mapping)
    return placed(items, cx=cx, cy=cy, sx=sx, sy=sy, ax=100, ay=100, name="face")


def mould(stage, points, sizes=(15, 10)):
    """Mould blobs at explicit points; two for stage 20, all for stage 0."""
    items = []
    count = 2 if stage == 20 else len(points)
    for i, (x, y) in enumerate(points[:count]):
        blob = visible(pick(CYCLE, [25, 26, 27] if i % 2 == 0 else [28]))
        ax, ay = (64, 126) if i % 2 == 0 else (138, 140)
        items.append(placed(blob, cx=x, cy=y, ax=ax, ay=ay, sx=sizes[0] / 15, sy=sizes[1] / 10, name=f"mould{i}"))
    return items


def rot_extras(total, fly_period=96, fly2_period=144, stink_period=72, two_flies=True, stink=True, points=((58, 42), (150, 52))):
    items = []
    fly_a = visible(pick(CYCLE, FLY_A))
    items.append(fly_group([placed(fly_a, cx=points[0][0], cy=points[0][1], ax=58, ay=42)], fly_period, total, FLY1, "fly1"))
    if two_flies:
        fly_b = visible(pick(CYCLE, FLY_B))
        items.append(fly_group([placed(fly_b, cx=points[1][0], cy=points[1][1], ax=150, ay=52)], fly2_period, total, FLY2, "fly2"))
    if stink:
        items.append(stink_group(visible(pick(CYCLE, STINK_A)), stink_period, total, "stink1"))
        items.append(stink_group(visible(pick(CYCLE, STINK_B)), 96, total, "stink2"))
    return items


def idle(stage):
    """(total_frames, layer transform kwargs) for the stage's idle character."""
    if stage >= 80:
        total = 96
        return total, {"s": breathe_scale(96, total, 3.0)}
    if stage >= 40:
        total = 72
        return total, {"s": breathe_scale(72, total, 1.8)}
    total = 288
    kwargs = {"p": twitch_position(72, total), "r": twitch_rotation(72, total)}
    if stage == 0:
        kwargs["s"] = static([106, 94, 100])
    return total, kwargs


# ------------------------------------------------------------------ mascots


def potato(stage, t, total):
    h = art.POTATO_HEALTHY
    r = art.POTATO_ROTTEN
    items = []
    items += pick(h, [0])  # shadow
    if stage >= 40:
        items += blend_toward_rot(pick(h, [1, 2, 3, 6]), t)  # feet, body, dimples
        items += blend_toward_rot(pick(h, [4, 5]), t)  # sprout
    else:
        items += pick(r, [7, 8, 9])  # rotated feet, rotten body (canvas gradient)
        items += pick(r, [10, 11])  # wilted sprout
        items += pick(r, [24, 25])  # drips
    if stage >= 80:
        items += pick(h, [7, 8, 9, 10, 11, 12, 13, 14])  # eyes, glints, arched brows, blush, smile
    elif stage >= 40:
        items += visible(pick(CYCLE, FACE_MID))
        items += fade(pick(h, [12, 13]), 0.6)
    else:
        items += pick(r, [12, 13, 14, 15, 16, 17]) if stage == 0 else mould(stage, [(64, 126), (138, 140)])
        items += pick(r, [18, 19, 20, 21, 22, 23])  # rotten face
        items += rot_extras(total, two_flies=(stage == 0), stink=(stage == 0))
    if stage == 40:
        items += mould(20, [(64, 126)])
    return items


def brain(stage, t, total):
    b = art.BRAIN_MID
    items = pick(b, [0])
    items += blend_toward_rot(pick(b, [1, 2, 3, 4, 5]), t)  # feet, body, midline, gyri
    if stage >= 80:
        # the canvas brain is the "mid" pose; healthy gets the potato's arched brows and full smile
        items += pick(b, [6, 7, 8, 9, 11, 12])  # eyes, glints, blush
        brows = placed(recolor(pick(art.POTATO_HEALTHY, [11]), {"#8a6220": "#9c4364"}), cx=100, cy=101, ax=100, ay=100, sx=0.83)
        items += [brows] + pick(b, [13])
    elif stage >= 40:
        items += pick(b, [6, 7, 8, 9, 10, 13])  # canvas mid face: straight brows + smirk
        items += fade(pick(b, [11, 12]), 0.5)
        if stage == 40:
            items += mould(20, [(62, 118)])
    else:
        items += [face_kit(stage, "#4a1f2e", cx=100, cy=102, sx=0.83)]
        items += mould(stage, [(62, 118), (136, 100), (100, 60)])
        items += rot_extras(total, two_flies=(stage == 0), stink=(stage == 0), points=((56, 40), (152, 48)))
        if stage == 0:
            items += recolor(pick(art.POTATO_ROTTEN, [24, 25]), {"#37320e": "#5a2a3c", "#4c5726": "#6b3348"})
    return items


def cat(stage, t, total):
    c = art.CAT_HEALTHY
    droop = {100: 0, 80: 0, 60: 18, 40: 34, 20: 55, 0: 70}[stage]
    items = pick(c, [0])
    items += blend_toward_rot(pick(c, [1, 2]), t)
    left = placed(blend_toward_rot(pick(c, [3, 5]), t), cx=56, cy=72, ax=56, ay=72, rot=-droop, name="ear_l")
    right = placed(blend_toward_rot(pick(c, [4, 6]), t), cx=144, cy=72, ax=144, ay=72, rot=droop, name="ear_r")
    items += [left, right]
    items += blend_toward_rot(pick(c, [7]), t)  # head
    if stage >= 80:
        items += pick(c, [8, 9, 10, 11, 12, 13, 14, 15, 16])
    elif stage >= 40:
        items += [face_kit(stage, "#22242c", cx=100, cy=104, sx=0.92)]
        items += pick(c, [13])  # nose stays
        items += fade(pick(c, [15, 16]), 0.5)
        if stage == 40:
            items += mould(20, [(60, 132)])
    else:
        items += [face_kit(stage, "#22242c", cx=100, cy=104, sx=0.92)]
        items += pick(c, [13])
        items += mould(stage, [(60, 132), (140, 90), (104, 154)])
        items += rot_extras(total, two_flies=(stage == 0), stink=(stage == 0), points=((50, 46), (156, 40)))
    items += blend_toward_rot(pick(c, [17]), t)  # whiskers
    return items


def plant(stage, t, total):
    p = art.ICON_PLANT
    droop = {100: 0, 80: 0, 60: 28, 40: 48, 20: 72, 0: 88}[stage]
    items = [group([{"ty": "el", "p": static([100, 178]), "s": static([96, 14]), "d": 1, "nm": "shadow"}, {"ty": "fl", "c": static([0, 0, 0, 1]), "o": static(30), "r": 1, "nm": "fill"}], "shadow")]
    stem = blend_toward_rot(pick(p, [0]), t)
    leaf_l = placed(blend_toward_rot(pick(p, [1]), t, "#7a6a2a", "#4a4419"), cx=98, cy=84, ax=98, ay=84, rot=droop, name="leaf_l")
    leaf_r = placed(blend_toward_rot(pick(p, [2]), t, "#7a6a2a", "#4a4419"), cx=102, cy=74, ax=102, ay=74, rot=-droop, name="leaf_r")
    items += stem + [leaf_l, leaf_r]
    items += blend_toward_rot(pick(p, [3, 4]), t * 0.5)  # pot rots slower than the plant
    if stage >= 80:
        items += pick(p, [5, 6, 7, 8, 9])
    else:
        items += [face_kit(stage, "#4a2410", cx=100, cy=149, sx=0.58, sy=0.72)]
        if stage == 40:
            items += mould(20, [(74, 128)], sizes=(9, 6))
        if stage <= 20:
            items += mould(stage, [(74, 128), (126, 160), (100, 116)], sizes=(9, 6))
            items += rot_extras(total, two_flies=(stage == 0), stink=(stage == 0), points=((60, 48), (144, 40)))
    return items


def goldfish(stage, t, total):
    f = art.ICON_FISH
    items = []
    bowl = pick(f, [0, 1, 2])
    blend_toward_rot(bowl, t, "#4e6b3d", "#3a4519")
    items += bowl
    fish = pick(f, [3, 4, 5])
    blend_toward_rot(fish, t)
    if stage >= 80:
        fish += pick(f, [6, 7])
        swim = animated(cycle(96, total, lambda: [(0, [96, 122]), (48, [106, 118]), (96, [96, 122])]), ease=EASE_IN_OUT)
        items.append(group(fish, "fish", transform(p=swim, a=(96, 122))))
        for i, idx in enumerate([8, 9]):
            bubble = pick(f, [idx])
            rise = animated(cycle(96, total, lambda i=i: [(0, [0, 0]), (96, [0, -34 - i * 10])]))
            op = animated(cycle(96, total, lambda: [(0, 70), (80, 60), (96, 0)]))
            items.append(group(bubble, f"bubble{i}", transform(p=rise, o=op)))
    elif stage >= 40:
        fish += pick(f, [6, 7])
        brow = recolor([placed(visible(pick(CYCLE, [18])), cx=84, cy=112, ax=100, ay=93, sx=0.5, sy=0.6)], {"#8a6220": "#a85e20"})
        fish += brow
        tilt = 22 if stage == 60 else 48
        items.append(group(fish, "fish", transform(p=(96, 118), a=(96, 122), r=tilt)))
    else:
        squiggle = recolor(visible(pick(CYCLE, [20])), {"#23200c": ROT_INK})
        fish.append(placed(squiggle, cx=84, cy=118, ax=78, ay=98, sx=0.55, sy=0.55))
        items.append(group(fish, "fish", transform(p=(100, 76), a=(96, 122), r=180)))
        items += mould(stage, [(70, 150), (136, 140), (100, 160)], sizes=(11, 7))
        items += rot_extras(total, two_flies=(stage == 0), stink=(stage == 0), points=((54, 40), (150, 36)))
    return items


def robot(stage, t, total):
    r = art.ICON_ROBOT
    items = [group([{"ty": "el", "p": static([100, 174]), "s": static([110, 14]), "d": 1, "nm": "shadow"}, {"ty": "fl", "c": static([0, 0, 0, 1]), "o": static(30), "r": 1, "nm": "fill"}], "shadow")]
    bend = {100: 0, 80: 0, 60: 14, 40: 26, 20: 40, 0: 52}[stage]
    antenna = blend_toward_rot(pick(r, [0, 1]), t, RUST_FILL, RUST_STROKE)
    if stage >= 80:
        pulse = animated(cycle(48, total, lambda: [(0, [100, 100]), (24, [116, 116]), (48, [100, 100])]), ease=EASE_IN_OUT)
        antenna[1] = group([antenna[1]], "ball", transform(p=(100, 28), a=(100, 28), s=pulse))
    items.append(placed(antenna, cx=100, cy=56, ax=100, ay=56, rot=bend, name="antenna"))
    items += blend_toward_rot(pick(r, [2, 3]), t, RUST_FILL, RUST_STROKE)
    eyes = pick(r, [4, 5])
    if stage >= 80:
        items += eyes
    elif stage >= 40:
        flicker = animated(cycle(24, total, lambda: [(0, 100), (6, 55), (9, 100), (18, 80), (24, 100)]), hold=True)
        items.append(group([eyes[0]], "eye_l", transform(o=flicker)))
        items.append(eyes[1])
        items += fade(pick(r, [4]), 0.0)
    else:
        dead = group([eyes[0]], "eye_dead", transform(o=static(18)))
        spark = animated(cycle(30, total, lambda: [(0, 0), (4, 100), (7, 0), (18, 100), (21, 0), (30, 0)]), hold=True)
        items.append(dead)
        items.append(group([eyes[1]], "eye_r", transform(o=spark)))
        sparks = recolor(visible(pick(CYCLE, [31])), {"#2f331f": "#ffe27a"})
        items.append(group([placed(sparks, cx=60, cy=64, ax=58, ay=38, sx=1.4, sy=1.4)], "spark", transform(o=spark)))
        items += mould(stage, [(60, 140), (140, 70)], sizes=(12, 8))
        items += rot_extras(total, two_flies=(stage == 0), stink=(stage == 0), points=((48, 30), (152, 24)))
    items += blend_toward_rot(pick(r, [6]), t, RUST_FILL, RUST_STROKE)  # mouth + legs
    return items


MASCOTS = {
    "potato": potato,
    "brain": brain,
    "cat": cat,
    "plant": plant,
    "goldfish": goldfish,
    "robot": robot,
}


def composition(name, shapes, total, layer_kwargs):
    lyr = layer(name, shapes, total, 1, p=layer_kwargs.get("p", ANCHOR), a=ANCHOR, s=layer_kwargs.get("s"), r=layer_kwargs.get("r"))
    return {
        "v": "5.7.4",
        "fr": FPS,
        "ip": 0,
        "op": total,
        "w": SIZE,
        "h": SIZE,
        "nm": name,
        "ddd": 0,
        "assets": [],
        "layers": [lyr],
        "markers": [],
    }


def main():
    for mascot_id, builder in MASCOTS.items():
        folder = os.path.join(ROOT, mascot_id)
        os.makedirs(folder, exist_ok=True)
        for stage in STAGES:
            total, kwargs = idle(stage)
            shapes = builder(stage, ROT_T[stage], total)
            comp = composition(f"{mascot_id}_{stage}", copy.deepcopy(shapes), total, kwargs)
            target = os.path.join(folder, f"stage_{stage}.json")
            with open(target, "w", encoding="utf-8") as fh:
                json.dump(comp, fh, separators=(",", ":"))
            print(f"wrote {os.path.relpath(target)} ({os.path.getsize(target)} bytes, {total} frames)")


if __name__ == "__main__":
    main()
