"""Synthesized sound design for the VFX pass, mixed over the clip's own audio.

Usage: python3 sfx.py SRC.mp4 OUT.wav
Cue times match the timeline constants in render.py.
"""
import subprocess
import sys

import numpy as np
from scipy.signal import butter, sosfilt

SRC, OUT = sys.argv[1], sys.argv[2]
SR = 48000
DUR = 253 / 30
n_total = int(DUR * SR)
rng = np.random.default_rng(4)

raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", SRC, "-vn", "-ac", "2", "-ar", str(SR), "-f", "f32le", "-"],
                     capture_output=True, check=True).stdout
orig = np.frombuffer(raw, np.float32).reshape(-1, 2)[:n_total]
orig = np.pad(orig, ((0, n_total - len(orig)), (0, 0)))
mix = np.zeros((n_total, 2), np.float32)


def t_axis(d):
    return np.arange(int(d * SR)) / SR


def place(sig, at, gain=1.0, pan=0.0):
    if sig.ndim == 1:
        sig = np.stack([sig * np.sqrt(0.5 * (1 - pan)), sig * np.sqrt(0.5 * (1 + pan))], 1) * np.sqrt(2)
    s = int(at * SR)
    e = min(n_total, s + len(sig))
    if e > s:
        mix[s:e] += sig[: e - s] * gain


def filt(x, kind, f, order=2):
    return sosfilt(butter(order, f, kind, fs=SR, output="sos"), x)


def sweep_bp(x, f0, f1, q=1.2, blocks=96):
    """band-pass whose centre glides f0 -> f1 (block-wise, crossfaded)"""
    out = np.zeros_like(x)
    edges = np.linspace(0, len(x), blocks + 1).astype(int)
    for k in range(blocks):
        a, b = edges[k], edges[k + 1]
        f = f0 * (f1 / f0) ** (k / (blocks - 1))
        lo, hi = f / (1 + 1 / (2 * q)), min(f * (1 + 1 / (2 * q)), SR / 2 - 100)
        seg = x[max(0, a - 2048):b]
        out[a:b] = sosfilt(butter(2, [lo, hi], "bandpass", fs=SR, output="sos"), seg)[-(b - a):]
    return out


def env(d, a, r, curve=2.0):
    t = t_axis(d)
    e = np.minimum(1, t / max(a, 1e-4)) * np.clip((d - t) / max(r, 1e-4), 0, 1) ** curve
    return e


def noise(d):
    return rng.standard_normal(int(d * SR))


def crackle(d, density):
    """sparse decaying impulses: frying sparks"""
    n = int(d * SR)
    x = np.zeros(n)
    dens = np.broadcast_to(density, (n,)) if np.ndim(density) else np.full(n, density)
    hits = np.nonzero(rng.random(n) < dens)[0]
    x[hits] = rng.uniform(-1, 1, len(hits)) * rng.uniform(0.3, 1, len(hits)) ** 2
    k = np.exp(-np.arange(160) / 18.0)
    x = np.convolve(x, k)[:n]
    return filt(x, "highpass", 1400)


def bell(d, freqs, decay):
    t = t_axis(d)
    return sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t / (decay * (1 - 0.3 * i / len(freqs))))
               for i, (f, a) in enumerate(freqs))


# 0.30 hand rises: airy whoosh
d = 0.7
place(sweep_bp(noise(d), 300, 2600) * env(d, 0.35, 0.3), 0.28, 0.35)

# 0.55 shield: struck bell + shimmering chord held until it dissolves
place(bell(2.4, [(1174.7, 1), (1760, 0.6), (2349, 0.35), (3136, 0.2)], 0.9), 0.55, 0.16)
d = 1.55
t = t_axis(d)
chord = sum(a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) for f, a in
            [(293.66, 0.5), (440.0, 0.35), (587.33, 0.3), (880.0, 0.14), (1174.7, 0.08)])
chord *= (0.75 + 0.25 * np.sin(2 * np.pi * 6.2 * t)) * env(d, 0.18, 0.35, 1.0)
place(chord, 0.55, 0.13)
place(crackle(d, 0.0035) * env(d, 0.1, 0.3, 1.0), 0.55, 0.22, -0.3)

# 1.88 shield bursts into sparks
place(crackle(0.7, 0.02) * env(0.7, 0.005, 0.65), 1.86, 0.45, -0.25)
place(sweep_bp(noise(0.5), 3000, 500) * env(0.5, 0.02, 0.45), 1.86, 0.3)

# 2.2 - 2.95 sling ring grinding, building up
d = 0.8
dens = np.linspace(0.004, 0.03, int(d * SR))
place(crackle(d, dens) * env(d, 0.2, 0.05, 1.0), 2.15, 0.5, 0.35)
place(sweep_bp(noise(d), 400, 3500, q=2) * np.linspace(0, 1, int(d * SR)) ** 2, 2.15, 0.3)

# 2.95 portal thrown open: whoosh, sub boom, sparkle
d = 1.3
place(sweep_bp(noise(d), 250, 4200, q=0.8) * env(d, 0.12, 1.1), 2.93, 0.55)
t = t_axis(1.6)
sub = np.sin(2 * np.pi * np.cumsum(np.geomspace(70, 32, len(t))) / SR) * np.exp(-t / 0.45)
place(sub, 2.95, 0.75)
place(crackle(0.9, 0.05) * env(0.9, 0.005, 0.85), 2.95, 0.5)

# 3.0 - 7.8 the open portal: stereo sizzle, low hum, wind from the other side
d = 4.85
place(np.stack([crackle(d, 0.011), crackle(d, 0.011)], 1) * env(d, 0.4, 0.4, 1.0)[:, None], 3.0, 0.42)
t = t_axis(d)
hum = sum(np.sin(2 * np.pi * f * t) * a for f, a in [(55, 1), (110.4, 0.4), (164.8, 0.15)])
place(hum * env(d, 0.6, 0.5, 1.0), 3.0, 0.11)
wind = filt(noise(d), "lowpass", 700) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.35 * t))
place(wind * env(d, 1.0, 0.6, 1.0), 3.0, 0.06)

# 7.1 iris closes: inward suction whoosh
d = 0.75
place(sweep_bp(noise(d), 3500, 180, q=0.9) * np.linspace(0.05, 1, int(d * SR)) ** 1.8, 7.08, 0.6)
place(np.stack([crackle(d, 0.03), crackle(d, 0.03)], 1) * np.linspace(0.3, 1, int(d * SR))[:, None], 7.08, 0.4)

# 7.8 final impact: boom, spark burst, ringing tail
t = t_axis(0.64)
boom = np.sin(2 * np.pi * np.cumsum(np.geomspace(90, 30, len(t))) / SR) * np.exp(-t / 0.35)
place(boom, 7.8, 0.9)
place(filt(noise(0.64), "lowpass", 1800) * env(0.64, 0.003, 0.6), 7.8, 0.45)
place(crackle(0.64, 0.06) * env(0.64, 0.003, 0.6), 7.8, 0.5)
place(bell(0.64, [(587.33, 1), (880, 0.5), (1760, 0.25)], 0.5), 7.8, 0.12)

# the clip's own room sound sits underneath, gone once the iris closes
duck = np.ones(n_total)
a, b = int(7.1 * SR), int(7.55 * SR)
duck[a:b] = np.linspace(1, 0, b - a)
duck[b:] = 0
out = orig * 0.8 * duck[:, None] + mix
fade = int(0.08 * SR)
out[-fade:] *= np.linspace(1, 0, fade)[:, None]
out = np.tanh(out * 1.4) / np.tanh(1.4)
out *= 0.93 / max(1e-6, np.abs(out).max())

pcm = (np.clip(out, -1, 1) * 32767).astype("<i2")
with open(OUT, "wb") as fh:
    import wave
    w = wave.open(fh, "wb")
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
    w.close()
print("wrote", OUT, f"{n_total / SR:.2f}s")
