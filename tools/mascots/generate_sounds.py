#!/usr/bin/env python3
"""Synthesises the short UI sounds (mascot reactions, score up/down) as 16-bit mono WAV files.

Run from the repository root:
    python3 tools/mascots/generate_sounds.py

Pure standard library so it runs anywhere; deterministic output.
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


def env(t: float, duration: float, attack=0.01, release=0.08) -> float:
    if t < attack:
        return t / attack
    if t > duration - release:
        return max(0.0, (duration - t) / release)
    return 1.0


def render(duration: float, fn) -> list:
    n = int(duration * RATE)
    return [max(-1.0, min(1.0, fn(i / RATE) * env(i / RATE, duration))) for i in range(n)]


def write(name: str, samples: list, gain=0.8):
    path = os.path.join(ROOT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s * gain * 32767)) for s in samples))
    print(f"wrote {os.path.relpath(path)} ({len(samples) / RATE:.2f}s)")


def sine(f, t):
    return math.sin(2 * math.pi * f * t)


def square(f, t):
    return 1.0 if sine(f, t) >= 0 else -1.0


def brain_hmm():
    return render(0.4, lambda t: 0.6 * sine(140 + 6 * sine(5, t), t) + 0.25 * sine(280, t) * (1 - t))


def plant_rustle():
    rnd = Lcg(7)
    prev = 0.0

    def fn(t):
        nonlocal prev
        prev = 0.7 * prev + 0.3 * rnd.noise()
        return prev * 1.6 * (0.5 + 0.5 * sine(9, t)) * (1 - t / 0.35)

    return render(0.35, fn)


def fish_bubbles():
    def fn(t):
        out = 0.0
        for start, f in ((0.0, 620), (0.14, 780), (0.27, 940)):
            if start <= t < start + 0.11:
                lt = t - start
                out += sine(f + 500 * lt, lt) * math.exp(-lt * 28)
        return out * 0.9

    return render(0.42, fn)


def cat_meow():
    def fn(t):
        f = 520 + 320 * math.sin(math.pi * t / 0.45)
        return 0.55 * sine(f, t) + 0.25 * sine(2 * f, t) + 0.12 * sine(3 * f, t)

    return render(0.45, fn)


def robot_beeps():
    def fn(t):
        if t < 0.13:
            return 0.5 * square(880, t)
        if 0.16 <= t < 0.32:
            return 0.5 * square(1320, t)
        return 0.0

    return render(0.34, fn)


def potato_thud():
    rnd = Lcg(3)
    return render(0.25, lambda t: 0.8 * sine(90 - 60 * t, t) * math.exp(-t * 14) + 0.2 * rnd.noise() * math.exp(-t * 40))


def score_up():
    notes = ((0.0, 523.25), (0.12, 659.25), (0.24, 783.99))

    def fn(t):
        out = 0.0
        for start, f in notes:
            if t >= start:
                lt = t - start
                out += 0.5 * sine(f, lt) * math.exp(-lt * 6)
        return out

    return render(0.6, fn)


def score_down():
    return render(0.55, lambda t: 0.6 * sine(220 - 110 * (t / 0.55), t) * (1 - 0.5 * t) + 0.15 * sine(110, t))


def main():
    write("mascots/brain/reaction.wav", brain_hmm())
    write("mascots/plant/reaction.wav", plant_rustle())
    write("mascots/goldfish/reaction.wav", fish_bubbles())
    write("mascots/cat/reaction.wav", cat_meow())
    write("mascots/robot/reaction.wav", robot_beeps())
    write("mascots/potato/reaction.wav", potato_thud())
    write("sounds/score_up.wav", score_up())
    write("sounds/score_down.wav", score_down())


if __name__ == "__main__":
    main()
