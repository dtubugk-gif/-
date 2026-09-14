#!/usr/bin/env python3
"""Synthesises the short UI sounds (mascot voices, score up/down) as 16-bit mono WAV files.

Run from the repository root:
    python3 tools/mascots/generate_sounds.py

Each mascot gets a tiny vocalisation in its own character: a glottal pulse train with vibrato, shaped by
two resonant formant filters (a crude vocal tract), plus breath noise. Pure standard library, deterministic.
"""

from __future__ import annotations

import math
import os
import struct
import wave

RATE = 22050
ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "feature", "mascot", "src", "main", "assets")


class Lcg:
    def __init__(self, seed: int):
        self.s = seed & 0x7FFFFFFF

    def noise(self) -> float:
        self.s = (self.s * 1103515245 + 12345) & 0x7FFFFFFF
        return self.s / 0x3FFFFFFF - 1.0


class Formant:
    """Two-pole resonator (band-pass) at a vowel formant frequency."""

    def __init__(self, freq: float, bandwidth: float, gain: float = 1.0):
        r = math.exp(-math.pi * bandwidth / RATE)
        self.a1 = -2 * r * math.cos(2 * math.pi * freq / RATE)
        self.a2 = r * r
        self.gain = gain * (1 - r)
        self.y1 = 0.0
        self.y2 = 0.0

    def process(self, x: float) -> float:
        y = self.gain * x - self.a1 * self.y1 - self.a2 * self.y2
        self.y2, self.y1 = self.y1, y
        return y


def env(t: float, duration: float, attack=0.02, release=0.1) -> float:
    if t < attack:
        return t / attack
    if t > duration - release:
        return max(0.0, (duration - t) / release)
    return 1.0


def write(name: str, samples: list, gain=0.8):
    path = os.path.join(ROOT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    peak = max(1e-6, max(abs(s) for s in samples))
    norm = gain / peak
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s * norm)) * 32767)) for s in samples))
    print(f"wrote {os.path.relpath(path)} ({len(samples) / RATE:.2f}s)")


def sine(f, t):
    return math.sin(2 * math.pi * f * t)


def voice(duration, f0_fn, vowels, vibrato=(5.5, 0.03), breath=0.06, seed=1, attack=0.02, release=0.1):
    """A syllable: f0_fn(t) gives the pitch, vowels is a list of (start_time, [(freq, bandwidth, gain), ...])
    segments that crossfade, so "mrrow" can slide from /a/ to /o/."""
    rnd = Lcg(seed)
    n = int(duration * RATE)
    out = []
    phase = 0.0
    banks = [[Formant(f, bw, g) for f, bw, g in segment] for _, segment in vowels]
    starts = [start for start, _ in vowels]
    for i in range(n):
        t = i / RATE
        f0 = f0_fn(t) * (1 + vibrato[1] * sine(vibrato[0], t))
        phase += f0 / RATE
        # glottal pulse: narrow-ish sawtooth with a soft edge
        frac = phase - math.floor(phase)
        pulse = (1.0 - 2.0 * frac) * (1.0 if frac < 0.7 else 0.3)
        source = pulse + breath * rnd.noise()
        # which vowel segment(s)
        idx = max(j for j, s in enumerate(starts) if s <= t) if t >= starts[0] else 0
        mix = 0.0
        if idx + 1 < len(starts):
            span = starts[idx + 1] - starts[idx]
            mix = min(1.0, max(0.0, (t - starts[idx]) / max(1e-6, span)))
        a = sum(f.process(source) for f in banks[idx])
        b = sum(f.process(source) for f in banks[idx + 1]) if idx + 1 < len(banks) else a
        sample = (1 - mix) * a + mix * b
        out.append(sample * env(t, duration, attack, release))
    return out


A = [(700, 90, 1.0), (1200, 110, 0.6), (2600, 160, 0.25)]  # /a/
O = [(500, 80, 1.0), (900, 100, 0.55), (2400, 160, 0.2)]  # /o/
U = [(350, 70, 1.0), (700, 90, 0.5), (2300, 160, 0.15)]  # /u/
E = [(600, 90, 1.0), (1700, 120, 0.55), (2500, 160, 0.25)]  # /ɛ/
M = [(280, 60, 1.0), (1000, 120, 0.25), (2200, 200, 0.08)]  # nasal /m/


def brain_hmph():
    # cynical: a low nasal "hmph", pitch sagging
    return voice(0.42, lambda t: 118 - 18 * t, [(0.0, M), (0.22, E), (0.3, M)], vibrato=(4.0, 0.02), breath=0.12, seed=3)


def plant_sigh():
    # dramatic: theatrical "aaah~" with wide vibrato and a falling tail
    return voice(
        0.7,
        lambda t: 330 - 90 * (t / 0.7) ** 2,
        [(0.0, A), (0.45, O)],
        vibrato=(6.0, 0.06),
        breath=0.05,
        seed=5,
        attack=0.06,
        release=0.2,
    )


def fish_blub():
    # confused: two bubbly "blub"s that rise in pitch
    first = voice(0.2, lambda t: 380 + 700 * t, [(0.0, U), (0.12, O)], vibrato=(8.0, 0.02), breath=0.03, seed=7, release=0.06)
    gap = [0.0] * int(0.05 * RATE)
    second = voice(0.22, lambda t: 440 + 800 * t, [(0.0, U), (0.14, O)], vibrato=(8.0, 0.02), breath=0.03, seed=8, release=0.06)
    return first + gap + second


def cat_mrrow():
    # judgmental: "mrrow" gliding m → a → o, pitch up then down
    return voice(
        0.55,
        lambda t: 420 + 220 * math.sin(math.pi * t / 0.55),
        [(0.0, M), (0.12, A), (0.38, O)],
        vibrato=(7.0, 0.035),
        breath=0.08,
        seed=11,
        attack=0.04,
        release=0.12,
    )


def robot_beep():
    # bureaucratic: two clipped tones with a 30 Hz ring, no breath
    n = int(0.4 * RATE)
    out = []
    for i in range(n):
        t = i / RATE
        if t < 0.15:
            f = 880
        elif 0.19 <= t < 0.36:
            f = 587
        else:
            out.append(0.0)
            continue
        square = 1.0 if sine(f, t) >= 0 else -1.0
        out.append(0.6 * square * (0.6 + 0.4 * abs(sine(30, t))) * env(t, 0.4, 0.005, 0.03))
    return out


def potato_meh():
    # indifferent: flat, short "meh"
    return voice(0.32, lambda t: 128.0, [(0.0, M), (0.08, E)], vibrato=(3.0, 0.01), breath=0.1, seed=13, release=0.08)


def score_up():
    notes = ((0.0, 523.25), (0.12, 659.25), (0.24, 783.99))

    def fn(t):
        out = 0.0
        for start, f in notes:
            if t >= start:
                lt = t - start
                out += 0.5 * sine(f, lt) * math.exp(-lt * 6)
        return out

    n = int(0.6 * RATE)
    return [fn(i / RATE) * env(i / RATE, 0.6, 0.01, 0.08) for i in range(n)]


def score_down():
    n = int(0.55 * RATE)
    return [(0.6 * sine(220 - 110 * (t / 0.55), t) * (1 - 0.5 * t) + 0.15 * sine(110, t)) * env(t, 0.55, 0.01, 0.08) for t in (i / RATE for i in range(n))]


def main():
    write("mascots/brain/reaction.wav", brain_hmph())
    write("mascots/plant/reaction.wav", plant_sigh())
    write("mascots/goldfish/reaction.wav", fish_blub())
    write("mascots/cat/reaction.wav", cat_mrrow())
    write("mascots/robot/reaction.wav", robot_beep())
    write("mascots/potato/reaction.wav", potato_meh())
    write("sounds/score_up.wav", score_up())
    write("sounds/score_down.wav", score_down())


if __name__ == "__main__":
    main()
