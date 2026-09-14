#!/usr/bin/env python3
"""Generates the six built-in mascots as Lottie files (one per stage) into the mascot asset folders.

Run from the repository root:
    python3 tools/mascots/generate_lottie.py

Every stage has its own idle loop: healthy stages breathe slowly and blink; rotten stages twitch,
drip and attract flies. The output is deterministic so re-running never changes committed files.
"""

from __future__ import annotations

import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

from lottie_builder import (  # noqa: E402
    CENTER,
    EASE_SNAP,
    animated,
    composition,
    darken,
    drips,
    ellipse,
    eyes,
    fill,
    flies,
    group,
    jitter,
    layer,
    loop_wave,
    mix,
    mouth,
    path,
    rect,
    rgba,
    spots,
    static,
    stroke,
    transform,
)

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "feature", "mascot", "src", "main", "assets", "mascots")
STAGES = [100, 80, 60, 40, 20, 0]
ROT = "#5B6B3F"
INK = "#0D0E11"


def frames_for(health: float) -> int:
    """Idle loop length: 4s healthy, down to 2s rotten (spec: 2-4s)."""
    return int(60 + 60 * health)


def breathing(frames, health, base=(100, 100), amp=3.5):
    if health >= 0.5:
        a = amp * (0.5 + health)
        return loop_wave(frames, list(base), [a, a * 0.7], cycles=1, dims=2)
    if health >= 0.3:
        return loop_wave(frames, list(base), [amp * 0.6, amp * 0.9], cycles=2, dims=2)
    return jitter(frames, list(base), [2.5, 3.5], seed=int(health * 100) + 7, every=4)


def body_position(frames, health, seed):
    if health >= 0.5:
        return loop_wave(frames, [CENTER, CENTER, 0], [0, 4 * health, 0], cycles=1, dims=3, phase=0.25)
    if health >= 0.3:
        return loop_wave(frames, [CENTER, CENTER, 0], [3, 2, 0], cycles=3, dims=3)
    return jitter(frames, [CENTER, CENTER, 0], [7 - 5 * health, 4, 0], seed=seed, every=3, dims=3)


# ------------------------------------------------------------------ BRAIN


def brain(stage, health, frames):
    pink = mix("#FF7EB6", ROT, (1 - health) * 0.85)
    dark = darken(pink, 0.35)
    shapes = [
        group([ellipse(196, 250, 200, 220), fill(pink)], "lobe_l"),
        group([ellipse(316, 250, 200, 220), fill(pink)], "lobe_r"),
        group([ellipse(256, 196, 190, 160), fill(pink)], "top"),
        group([rect(256, 372, 74, 70, 24), fill(dark)], "stem"),
    ]
    gyri = [
        [(140, 230), (175, 200), (205, 240), (235, 205)],
        [(150, 300), (190, 275), (220, 310), (250, 285)],
        [(275, 205), (305, 240), (335, 200), (370, 230)],
        [(265, 285), (295, 310), (325, 275), (360, 300)],
        [(215, 160), (256, 145), (300, 160)],
    ]
    for i, pts in enumerate(gyri):
        shapes.append(group([path(pts, closed=False, smooth=0.35), stroke(dark, 11)], f"gyrus{i}"))
    shapes += eyes(212, 300, 262, 54, health, frames=frames)
    shapes += mouth(256, 322, 70, health)
    if health <= 0.4:
        shapes += spots([(160, 210), (340, 290), (300, 170)], darken(pink, 0.5), [26, 20, 18])
    if health <= 0.2:
        shapes += drips(frames, [200, 320], 350, 70, pink, seed=3)
        shapes += flies(frames, 256, 150, 150, 3 if health > 0 else 5, seed=1)
    return [layer("brain", shapes, frames, 1, s=breathing(frames, health), p=body_position(frames, health, 11))]


# ------------------------------------------------------------------ PLANT


def plant(stage, health, frames):
    leaf = mix("#6EDB8F", "#7A5A2E", (1 - health) * 0.9)
    stem = mix("#4CAF6A", "#5C4426", (1 - health) * 0.9)
    pot = rgba("#C9694A")
    pot_dark = darken(pot, 0.3)
    droop = (1 - health) * 75  # degrees the leaves fall
    sway_amp = 4 * health
    shapes = []
    # pot with face
    shapes.append(group([path([(170, 330), (342, 330), (318, 460), (194, 460)], smooth=0.0), fill(pot)], "pot"))
    shapes.append(group([rect(256, 330, 200, 34, 10), fill(pot_dark)], "rim"))
    shapes.append(group([ellipse(256, 318, 170, 30), fill(rgba("#4B3826"))], "soil"))
    shapes += eyes(222, 290, 385, 40, health, frames=frames)
    shapes += mouth(256, 428, 56, health)
    # stem
    stem_shape = group(
        [rect(0, -70, 16, 140, 8), fill(stem)],
        "stem",
        transform(p=(256, 318), r=loop_wave(frames, 0, sway_amp, cycles=1) if health >= 0.3 else jitter(frames, 0, 3, seed=4, dims=1, every=4)),
    )
    leaves = []
    for i, (angle, y, size) in enumerate([(-55, 250, 1.0), (55, 250, 1.0), (-35, 200, 0.85), (35, 200, 0.85), (0, 170, 0.7)]):
        sign = -1 if angle < 0 else 1
        base_rot = angle + sign * droop if angle != 0 else 0
        if health >= 0.3:
            rot = loop_wave(frames, base_rot, sway_amp * 1.5, cycles=1, phase=0.1 * i)
        else:
            rot = jitter(frames, base_rot, 3, seed=5 + i, dims=1, every=5)
        leaves.append(
            group(
                [ellipse(0, -46 * size, 46 * size, 100 * size), fill(leaf), path([(0, -8), (0, -85 * size)], closed=False), stroke(darken(leaf, 0.35), 4)],
                f"leaf{i}",
                transform(p=(256, y), r=rot),
            )
        )
    shapes.append(stem_shape)
    shapes += leaves
    if health <= 0.4:
        shapes += spots([(240, 235), (285, 205), (220, 180)], darken(leaf, 0.55), [16, 12, 12])
    if health <= 0.2:
        shapes += flies(frames, 256, 200, 120, 3 if health > 0 else 5, seed=2)
    if health <= 0:
        shapes += drips(frames, [200, 315], 335, 60, rgba("#3E4A2A"), seed=9)
    return [layer("plant", shapes, frames, 1, s=breathing(frames, health, amp=2.5), p=body_position(frames, health, 12))]


# ------------------------------------------------------------------ GOLDFISH


def goldfish(stage, health, frames):
    water = mix("#3FA7D6", "#4E6B3D", (1 - health) * 0.9)
    body = mix("#FFA94D", "#8C7A4A", (1 - health) * 0.8)
    fin = darken(body, 0.25)
    shapes = []
    shapes.append(group([ellipse(256, 270, 380, 380), fill(darken(water, 0.5), 35)], "bowl_bg"))
    shapes.append(group([path([(90, 250), (422, 250), (400, 440), (112, 440)], smooth=0.35), fill(water, 85)], "water"))
    shapes.append(group([ellipse(256, 270, 380, 380), stroke(rgba("#DCE9F5"), 10)], "bowl"))
    shapes.append(group([rect(256, 84, 190, 26, 12), fill(rgba("#DCE9F5"))], "bowl_rim"))
    # fish tilt: 0 healthy -> 180 belly up
    tilt = 180 * (1 - health) if health < 0.7 else 0
    fish_y = 300 - (1 - health) * 60 if health < 0.5 else 300
    fish_parts = [
        ellipse(0, 0, 150, 96),
        fill(body),
    ]
    fish = [
        group(fish_parts, "body"),
        group([path([(70, 0), (128, -52), (118, 0), (128, 52)], smooth=0.1), fill(fin)], "tail"),
        group([path([(-10, -40), (20, -78), (34, -38)], smooth=0.1), fill(fin)], "fin_top"),
    ]
    fish += eyes(-34, -34, -8, 30, health, frames=frames)[: 2 if health >= 0.3 else 2]
    fish += mouth(-62, 12, 26, health)
    if health >= 0.5:
        swim = loop_wave(frames, [256, fish_y], [34, 6], cycles=1, dims=2)
        rot = loop_wave(frames, tilt, 6, cycles=1)
    elif health >= 0.3:
        swim = loop_wave(frames, [256, fish_y], [12, 10], cycles=2, dims=2)
        rot = loop_wave(frames, tilt, 10, cycles=2)
    else:
        swim = jitter(frames, [256, fish_y], [5, 4], seed=21, every=5)
        rot = jitter(frames, tilt, 4, seed=22, dims=1, every=6)
    shapes.append(group(fish, "fish", transform(p=swim, r=rot)))
    # bubbles rise when alive; algae when rotten
    if health >= 0.3:
        for i, x in enumerate([200, 236, 300]):
            offset = i * 0.33
            kfs_p, kfs_o = [], []
            for s in range(7):
                t = s / 6
                ph = (t + offset) % 1
                kfs_p.append((round(t * frames), [x + math.sin(ph * 6) * 6, 400 - ph * 300]))
                kfs_o.append((round(t * frames), 80 if ph < 0.85 else 0))
            shapes.append(group([ellipse(0, 0, 14 + i * 4, 14 + i * 4), stroke(rgba("#FFFFFF"), 3)], f"bubble{i}", transform(p=animated(kfs_p), o=animated(kfs_o))))
    if health <= 0.4:
        shapes += spots([(150, 400), (330, 420), (240, 430)], darken(water, 0.45), [50, 40, 34])
    if health <= 0.2:
        shapes += flies(frames, 256, 110, 130, 3 if health > 0 else 5, seed=3)
    return [layer("goldfish", shapes, frames, 1, s=breathing(frames, health, amp=1.5), p=body_position(frames, health, 13))]


# ------------------------------------------------------------------ CAT


def cat(stage, health, frames):
    fur = mix("#B48CFF", ROT, (1 - health) * 0.85)
    inner = mix("#FFC0D8", "#6B5A3F", (1 - health) * 0.8)
    ear_droop = (1 - health) * 70
    shapes = []
    for i, sign in enumerate((-1, 1)):
        base_rot = sign * (18 + ear_droop)
        rot = loop_wave(frames, base_rot, 3, cycles=1, phase=0.2 * i) if health >= 0.5 else jitter(frames, base_rot, 3, seed=31 + i, dims=1, every=7)
        shapes.append(
            group(
                [path([(-46, 20), (46, 20), (0, -100)], smooth=0.05), fill(fur), path([(-24, 12), (24, 12), (0, -60)], smooth=0.05), fill(inner)],
                f"ear{i}",
                transform(p=(256 + sign * 108, 176), r=rot),
            )
        )
    shapes.append(group([ellipse(256, 280, 300, 260), fill(fur)], "head"))
    shapes.append(group([ellipse(256, 322, 120, 70), fill(darken(fur, -0.15) if health > 0.5 else darken(fur, 0.15))], "muzzle"))
    # eyes: slit pupils, judgmental half-lid when declining
    if health >= 0.3:
        lid = 1.0 if health >= 0.8 else 0.6 if health >= 0.5 else 0.4
        blink = animated(
            [(0, [100, 100 * lid]), (round(frames * 0.6), [100, 100 * lid]), (round(frames * 0.63), [100, 6]), (round(frames * 0.66), [100, 100 * lid]), (frames, [100, 100 * lid])],
            ease=EASE_SNAP,
        )
        for cx in (196, 316):
            shapes.append(group([ellipse(0, 0, 64, 52), fill(rgba("#F6F3A6"))], "eye", transform(p=(cx, 262), s=blink)))
            shapes.append(group([ellipse(0, 0, 14 if health >= 0.7 else 22, 40), fill(rgba(INK))], "slit", transform(p=(cx, 262), s=blink)))
    else:
        shapes += eyes(196, 316, 262, 56, health, frames=frames)
    shapes.append(group([path([(-14, -8), (14, -8), (0, 8)], smooth=0.05), fill(rgba("#3A2A3F"))], "nose", transform(p=(256, 306))))
    if health >= 0.5:
        shapes.append(group([path([(226, 318), (240, 336), (256, 320)], closed=False, smooth=0.25), stroke(rgba(INK), 6)], "mouth_l"))
        shapes.append(group([path([(256, 320), (272, 336), (286, 318)], closed=False, smooth=0.25), stroke(rgba(INK), 6)], "mouth_r"))
    else:
        shapes += mouth(256, 334, 50, health)
    for i, (x, y, dx) in enumerate([(160, 300, -1), (160, 322, -1), (352, 300, 1), (352, 322, 1)]):
        shapes.append(group([path([(x, y), (x + dx * 90, y + (i % 2) * 14 - 6)], closed=False), stroke(rgba("#E8E8F0"), 5)], f"whisker{i}"))
    if health <= 0.4:
        shapes += spots([(190, 210), (330, 340), (300, 220)], darken(fur, 0.5), [30, 24, 18])
    if health <= 0.2:
        shapes += flies(frames, 256, 150, 150, 3 if health > 0 else 5, seed=4)
    return [layer("cat", shapes, frames, 1, s=breathing(frames, health, amp=2.5), p=body_position(frames, health, 14))]


# ------------------------------------------------------------------ ROBOT


def robot(stage, health, frames):
    metal = mix("#5AC8FA", "#8A5A3C", (1 - health) * 0.9)
    panel = darken(metal, 0.45)
    led = mix("#DFFBFF", "#FF8A5A", (1 - health) * 0.8)
    shapes = []
    bend = (1 - health) * 55
    antenna_rot = loop_wave(frames, bend, 4, cycles=1) if health >= 0.5 else jitter(frames, bend, 6, seed=41, dims=1, every=5)
    ball_pulse = loop_wave(frames, [100, 100], [14, 14], cycles=2, dims=2) if health >= 0.5 else jitter(frames, [100, 100], [20, 20], seed=42, every=4)
    shapes.append(
        group(
            [rect(0, -40, 10, 80, 4), fill(panel), group([ellipse(0, -90, 34, 34), fill(led)], "ball", transform(s=ball_pulse))],
            "antenna",
            transform(p=(256, 150), r=antenna_rot),
        )
    )
    shapes.append(group([rect(256, 250, 250, 210, 34), fill(metal)], "head"))
    shapes.append(group([rect(256, 250, 210, 150, 22), fill(panel)], "screen"))
    shapes.append(group([rect(256, 400, 200, 100, 26), fill(darken(metal, 0.15))], "body"))
    for sign in (-1, 1):
        shapes.append(group([rect(256 + sign * 156, 400, 40, 90, 14), fill(darken(metal, 0.25))], "arm"))
    # LED eyes: steady when healthy, flicker when failing, one dead when rotten
    for i, cx in enumerate((208, 304)):
        if health >= 0.5:
            op = static(100)
        elif health >= 0.3:
            op = jitter(frames, 80, 30, seed=43 + i, dims=1, every=4)
        else:
            op = static(15) if i == 0 else jitter(frames, 50, 50, seed=45, dims=1, every=3)
        w, h = (52, 26) if health >= 0.5 else (52, 14)
        shapes.append(group([rect(0, 0, w, h, 8), fill(led)], f"eye{i}", transform(p=(cx, 230), o=op)))
    # mouth grid
    segs = 5 if health >= 0.5 else 3
    for i in range(segs):
        x = 256 + (i - (segs - 1) / 2) * 28
        op = static(100) if health >= 0.3 else jitter(frames, 60, 60, seed=50 + i, dims=1, every=5)
        shapes.append(group([rect(0, 0, 18, 12 if health >= 0.8 else 8, 3), fill(led)], f"seg{i}", transform(p=(x, 292 + (6 if health < 0.5 and i % 2 else 0)), o=op)))
    if health <= 0.4:
        shapes += spots([(170, 190), (340, 300), (200, 420)], rgba("#6B3A1E"), [34, 26, 22])
        shapes.append(group([path([(300, 170), (322, 205), (296, 236)], closed=False), stroke(rgba("#2A2A2A"), 5)], "crack"))
    if health <= 0.2:
        # sparks
        for i, (x, y) in enumerate([(150, 220), (360, 200), (250, 160)]):
            op = jitter(frames, 0, 100, seed=60 + i, dims=1, every=3)
            shapes.append(group([path([(-10, 0), (10, 0), (0, -10), (0, 10)], smooth=0.0), stroke(rgba("#FFE27A"), 4)], f"spark{i}", transform(p=(x, y), o=op)))
    if health <= 0:
        for i, x in enumerate([230, 270]):
            kfs_p, kfs_o = [], []
            for s in range(7):
                t = s / 6
                ph = (t + i * 0.5) % 1
                kfs_p.append((round(t * frames), [x + math.sin(ph * 4) * 10, 150 - ph * 120]))
                kfs_o.append((round(t * frames), 70 * (1 - ph)))
            shapes.append(group([ellipse(0, 0, 40 + ph * 0, 40), fill(rgba("#6E6E6E"))], f"smoke{i}", transform(p=animated(kfs_p), o=animated(kfs_o), s=animated([(0, [60, 60]), (frames, [140, 140])]))))
    hover = loop_wave(frames, [CENTER, CENTER, 0], [0, 6 * health, 0], cycles=1, dims=3) if health >= 0.5 else body_position(frames, health, 15)
    return [layer("robot", shapes, frames, 1, s=breathing(frames, health, amp=1.2), p=hover)]


# ------------------------------------------------------------------ POTATO


def potato(stage, health, frames):
    skin = mix("#D4A45A", "#5E6B3A", (1 - health) * 0.85)
    shapes = []
    blob = [(150, 210), (220, 150), (320, 160), (380, 240), (360, 340), (290, 390), (190, 370), (140, 300)]
    shapes.append(group([path(blob, smooth=0.3), fill(skin)], "body"))
    for i, (x, y) in enumerate([(200, 200), (330, 230), (250, 340), (170, 300)]):
        shapes.append(group([ellipse(x, y, 16, 12), fill(darken(skin, 0.3))], f"dimple{i}"))
    shapes += eyes(224, 296, 250, 34, health, frames=frames)
    shapes += mouth(260, 300, 44, health)
    # sprouts grow as it rots
    sprout_count = 1 if health >= 0.8 else 2 if health >= 0.5 else 4 if health >= 0.3 else 6
    sprout = mix("#9BD46A", "#C9B7E8", (1 - health) * 0.7)
    for i in range(sprout_count):
        ang = -60 + i * (120 / max(1, sprout_count - 1)) if sprout_count > 1 else 0
        length = 40 + (1 - health) * 90 + (i % 2) * 20
        rot = loop_wave(frames, ang, 5, cycles=1, phase=0.15 * i) if health >= 0.3 else jitter(frames, ang, 8, seed=70 + i, dims=1, every=5)
        shapes.append(
            group(
                [path([(0, 0), (8, -length * 0.5), (-4, -length)], closed=False, smooth=0.3), stroke(sprout, 7), ellipse(-4, -length, 18, 12), fill(sprout)],
                f"sprout{i}",
                transform(p=(256 + (i - sprout_count / 2) * 30, 170), r=rot),
            )
        )
    if health <= 0.4:
        shapes += spots([(190, 240), (340, 300), (270, 360)], darken(skin, 0.55), [36, 30, 26])
    if health <= 0.2:
        shapes += flies(frames, 256, 170, 150, 3 if health > 0 else 5, seed=6)
        shapes += drips(frames, [210, 310], 370, 60, darken(skin, 0.2), seed=8)
    return [layer("potato", shapes, frames, 1, s=breathing(frames, health, amp=1.6), p=body_position(frames, health, 16))]


MASCOTS = {
    "brain": brain,
    "plant": plant,
    "goldfish": goldfish,
    "cat": cat,
    "robot": robot,
    "potato": potato,
}


def main():
    for mascot_id, builder in MASCOTS.items():
        folder = os.path.join(ROOT, mascot_id)
        os.makedirs(folder, exist_ok=True)
        for stage in STAGES:
            health = stage / 100
            frames = frames_for(health)
            layers = builder(stage, health, frames)
            comp = composition(f"{mascot_id}_{stage}", layers, frames)
            target = os.path.join(folder, f"stage_{stage}.json")
            with open(target, "w", encoding="utf-8") as fh:
                json.dump(comp, fh, separators=(",", ":"))
            print(f"wrote {os.path.relpath(target)} ({os.path.getsize(target)} bytes, {frames} frames)")


if __name__ == "__main__":
    main()
