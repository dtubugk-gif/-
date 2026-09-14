"""Tiny Lottie (bodymovin) JSON builder used by generate_lottie.py.

Only the subset that lottie-android renders reliably is emitted: shape layers with ellipses,
rectangles, bezier paths, fills, strokes, group transforms, and keyframed transforms.
"""

from __future__ import annotations

import math
from typing import Sequence

FPS = 30
SIZE = 512
CENTER = SIZE / 2

EASE_SMOOTH = ((0.42, 0.0), (0.58, 1.0))
EASE_SNAP = ((0.7, 0.0), (0.3, 1.0))


# ---------------------------------------------------------------- values / keyframes


def static(value):
    return {"a": 0, "k": value}


def _dims(value):
    return len(value) if isinstance(value, (list, tuple)) else 1


def animated(keyframes: Sequence[tuple], ease=EASE_SMOOTH, hold=False):
    """keyframes: [(frame, value), ...]. Values must all be the same shape."""
    out = []
    for index, (frame, value) in enumerate(keyframes):
        entry = {"t": frame, "s": list(value) if isinstance(value, (list, tuple)) else [value]}
        if index < len(keyframes) - 1:
            if hold:
                entry["h"] = 1
            else:
                n = _dims(value)
                (ox, oy), (ix, iy) = ease
                entry["o"] = {"x": [ox] * n, "y": [oy] * n}
                entry["i"] = {"x": [ix] * n, "y": [iy] * n}
        out.append(entry)
    return {"a": 1, "k": out}


def loop_wave(frames: int, base, amplitude, cycles=1.0, dims=1, phase=0.0, steps=8):
    """Smooth sinusoidal loop sampled into keyframes so the loop is seamless."""
    kfs = []
    for i in range(steps + 1):
        t = i / steps
        frame = round(t * frames)
        s = math.sin((t * cycles + phase) * 2 * math.pi)
        if dims == 1:
            kfs.append((frame, base + amplitude * s))
        else:
            kfs.append((frame, [base[d] + amplitude[d] * s for d in range(dims)]))
    return animated(kfs)


def jitter(frames: int, base, amplitude, seed: int, every=5, dims=2):
    """Twitchy hold-keyframes for rotten mascots (deterministic)."""
    rnd = _Rand(seed)
    kfs = []
    frame = 0
    while frame < frames:
        if dims == 1:
            kfs.append((frame, base + rnd.uniform(-amplitude, amplitude)))
        else:
            kfs.append((frame, [base[d] + rnd.uniform(-amplitude[d], amplitude[d]) for d in range(dims)]))
        frame += every + rnd.randint(0, 3)
    kfs.append((frames, list(base) if dims > 1 else base))
    return animated(kfs, hold=True)


class _Rand:
    """Tiny deterministic LCG so output never depends on Python's random module state."""

    def __init__(self, seed: int):
        self.state = (seed * 1103515245 + 12345) & 0x7FFFFFFF

    def next(self) -> float:
        self.state = (self.state * 1103515245 + 12345) & 0x7FFFFFFF
        return self.state / 0x7FFFFFFF

    def uniform(self, a: float, b: float) -> float:
        return a + (b - a) * self.next()

    def randint(self, a: int, b: int) -> int:
        return a + int(self.next() * (b - a + 1))


# ---------------------------------------------------------------- colours


def hex_to_rgb(hex_color: str):
    h = hex_color.lstrip("#")
    return tuple(int(h[i : i + 2], 16) / 255 for i in (0, 2, 4))


def mix(a: str, b: str, t: float) -> list:
    ra, rb = hex_to_rgb(a), hex_to_rgb(b)
    return [ra[i] + (rb[i] - ra[i]) * t for i in range(3)] + [1]


def rgba(hex_color: str, alpha=1.0):
    return list(hex_to_rgb(hex_color)) + [alpha]


def darken(color: list, amount: float) -> list:
    return [max(0.0, c * (1 - amount)) for c in color[:3]] + [color[3]]


# ---------------------------------------------------------------- shapes


def ellipse(cx, cy, w, h, name="el"):
    return {"ty": "el", "p": static([cx, cy]), "s": static([w, h]), "d": 1, "nm": name}


def rect(cx, cy, w, h, r=0, name="rc"):
    return {"ty": "rc", "p": static([cx, cy]), "s": static([w, h]), "r": static(r), "d": 1, "nm": name}


def path(points, closed=True, smooth=0.0, name="sh"):
    """points: list of (x, y). smooth>0 turns the polygon into a rounded blob (catmull-rom style)."""
    n = len(points)
    v = [list(p) for p in points]
    i_t = []
    o_t = []
    for idx in range(n):
        if smooth <= 0:
            i_t.append([0, 0])
            o_t.append([0, 0])
            continue
        prev_p = points[(idx - 1) % n]
        next_p = points[(idx + 1) % n]
        dx = (next_p[0] - prev_p[0]) * smooth
        dy = (next_p[1] - prev_p[1]) * smooth
        i_t.append([-dx, -dy])
        o_t.append([dx, dy])
    return {"ty": "sh", "ks": static({"c": closed, "v": v, "i": i_t, "o": o_t}), "nm": name}


def fill(color, opacity=100):
    return {"ty": "fl", "c": static(color), "o": static(opacity), "r": 1, "nm": "fill"}


def stroke(color, width, opacity=100, cap=2, join=2):
    return {"ty": "st", "c": static(color), "o": static(opacity), "w": static(width), "lc": cap, "lj": join, "nm": "stroke"}


def transform(p=(0, 0), a=(0, 0), s=(100, 100), r=0, o=100):
    return {
        "ty": "tr",
        "p": p if isinstance(p, dict) else static(list(p)),
        "a": a if isinstance(a, dict) else static(list(a)),
        "s": s if isinstance(s, dict) else static(list(s)),
        "r": r if isinstance(r, dict) else static(r),
        "o": o if isinstance(o, dict) else static(o),
        "sk": static(0),
        "sa": static(0),
        "nm": "tr",
    }


def group(items, name="group", tr=None):
    return {"ty": "gr", "it": list(items) + [tr or transform()], "nm": name}


def layer(name, shapes, frames, ind, p=None, a=None, s=None, r=None, o=None, parent=None):
    ks = {
        "o": o if isinstance(o, dict) else static(100 if o is None else o),
        "r": r if isinstance(r, dict) else static(0 if r is None else r),
        "p": p if isinstance(p, dict) else static(list(p) if p else [CENTER, CENTER, 0]),
        "a": a if isinstance(a, dict) else static(list(a) if a else [CENTER, CENTER, 0]),
        "s": s if isinstance(s, dict) else static(list(s) if s else [100, 100, 100]),
    }
    out = {
        "ddd": 0,
        "ind": ind,
        "ty": 4,
        "nm": name,
        "sr": 1,
        "ks": ks,
        "ao": 0,
        "shapes": shapes,
        "ip": 0,
        "op": frames,
        "st": 0,
        "bm": 0,
    }
    if parent is not None:
        out["parent"] = parent
    return out


def composition(name, layers, frames):
    return {
        "v": "5.7.4",
        "fr": FPS,
        "ip": 0,
        "op": frames,
        "w": SIZE,
        "h": SIZE,
        "nm": name,
        "ddd": 0,
        "assets": [],
        "layers": layers,
        "markers": [],
    }


# ---------------------------------------------------------------- shared facial features


def eyes(cx_left, cx_right, cy, size, health, ink="#0D0E11", white="#FFFFFF", seed=1, frames=120):
    """Eyes that go from wide + blinking to half-lidded to X-ed out."""
    items = []
    if health >= 0.3:
        lid = 1.0 if health >= 0.7 else 0.55 if health >= 0.5 else 0.35
        blink = animated(
            [
                (0, [100, 100 * lid]),
                (round(frames * 0.42), [100, 100 * lid]),
                (round(frames * 0.45), [100, 8]),
                (round(frames * 0.48), [100, 100 * lid]),
                (frames, [100, 100 * lid]),
            ],
            ease=EASE_SNAP,
        )
        for cx in (cx_left, cx_right):
            items.append(
                group(
                    [ellipse(0, 0, size, size), fill(rgba(white))],
                    "eye_white",
                    transform(p=(cx, cy), s=blink),
                )
            )
            pupil = size * (0.42 if health >= 0.7 else 0.34)
            items.append(
                group(
                    [ellipse(0, 0, pupil, pupil), fill(rgba(ink))],
                    "pupil",
                    transform(p=(cx + (0 if health >= 0.7 else size * 0.12), cy + size * 0.08), s=blink),
                )
            )
    else:
        w = size * 0.9
        for cx in (cx_left, cx_right):
            wobble = jitter(frames, 0, 6 if health <= 0 else 0, seed, every=8, dims=1)
            items.append(
                group(
                    [rect(0, 0, w, size * 0.18, 4), fill(rgba(ink))],
                    "x1",
                    transform(p=(cx, cy), r=animated([(0, 45), (frames, 45)]) if health > 0 else wobble_rotation(45, wobble)),
                )
            )
            items.append(
                group(
                    [rect(0, 0, w, size * 0.18, 4), fill(rgba(ink))],
                    "x2",
                    transform(p=(cx, cy), r=static(-45)),
                )
            )
    return items


def wobble_rotation(base, wobble):
    out = dict(wobble)
    out["k"] = [dict(k, s=[base + k["s"][0]]) for k in wobble["k"]]
    return out


def mouth(cx, cy, width, health, ink="#0D0E11", tongue="#FF6B8A"):
    """Smile -> flat -> frown -> open with tongue."""
    if health >= 0.8:
        pts = [(cx - width / 2, cy), (cx, cy + width * 0.35), (cx + width / 2, cy)]
        return [group([path(pts, closed=False, smooth=0.3), stroke(rgba(ink), 9)], "mouth_smile")]
    if health >= 0.5:
        pts = [(cx - width / 2, cy), (cx + width / 2, cy)]
        return [group([path(pts, closed=False), stroke(rgba(ink), 9)], "mouth_flat")]
    if health >= 0.2:
        pts = [(cx - width / 2, cy + width * 0.2), (cx, cy - width * 0.15), (cx + width / 2, cy + width * 0.2)]
        return [group([path(pts, closed=False, smooth=0.3), stroke(rgba(ink), 9)], "mouth_frown")]
    return [
        group([ellipse(cx, cy + 4, width * 0.6, width * 0.5), fill(rgba(ink))], "mouth_open"),
        group([ellipse(cx, cy + width * 0.2, width * 0.3, width * 0.28), fill(rgba(tongue))], "tongue"),
    ]


def flies(frames, cx, cy, radius, count, seed, ink="#1B1B1B"):
    """Little orbiting dots for the rotten stages."""
    out = []
    for i in range(count):
        phase = i / count
        rx, ry = radius * (1 + 0.25 * ((i % 3) - 1)), radius * 0.45
        kfs = []
        steps = 12
        for s in range(steps + 1):
            t = s / steps
            ang = (t + phase) * 2 * math.pi
            kfs.append((round(t * frames), [cx + math.cos(ang) * rx, cy + math.sin(ang * 2) * ry * 0.4 + math.sin(ang) * ry]))
        out.append(group([ellipse(0, 0, 11, 9), fill(rgba(ink))], f"fly{i}", transform(p=animated(kfs))))
    return out


def drips(frames, xs, y0, length, color, seed):
    out = []
    for i, x in enumerate(xs):
        offset = (i * 0.37) % 1
        kfs_p = []
        kfs_o = []
        steps = 6
        for s in range(steps + 1):
            t = s / steps
            phase = (t + offset) % 1
            kfs_p.append((round(t * frames), [x, y0 + phase * length]))
            kfs_o.append((round(t * frames), 100 if phase < 0.75 else max(0, 100 - (phase - 0.75) * 400)))
        out.append(
            group(
                [ellipse(0, 0, 14, 26), fill(color)],
                f"drip{i}",
                transform(p=animated(kfs_p, hold=False), o=animated(kfs_o)),
            )
        )
    return out


def spots(points, color, sizes=None):
    out = []
    for i, (x, y) in enumerate(points):
        s = sizes[i] if sizes else 22
        out.append(group([ellipse(x, y, s, s * 0.8), fill(color)], f"spot{i}"))
    return out
