#!/usr/bin/env python3
"""Generate the deep, ASMR-style guided relaxation voice (audio/asmr_voice.mp3).

Each line of the Hebrew script is spoken by a neural text-to-speech voice
(he-IL-AvriNeural, slowed and lowered), then placed on a timeline so that the
breathing cues land on the 0.1 Hz swell used in the soundscape: "breathe in"
as a wave rises, "breathe out" as it falls. The voice is then given an ASMR
treatment:
  - close-mic warmth: low-shelf boost, softened 3-5 kHz presence, rolled-off top
  - a whisper layer: the voice's own spectrum with randomized phase (the
    voiced pitch disappears and only breath remains), mixed underneath
  - ear-to-ear movement: each line sits a little left or right, with a small
    interaural delay, so the voice seems to move close around the head
  - a small, intimate room

Needs network access to the text-to-speech service.

Usage:
  pip install numpy imageio-ffmpeg edge-tts
  python3 generate_voice.py [--out audio]
"""

import argparse
import asyncio
import os
import subprocess
import tempfile

import numpy as np

from generate_noise import SAMPLE_RATE, db, ffmpeg_exe, reverb, write_mp3

VOICE = "he-IL-AvriNeural"
RATE = "-22%"
PITCH = "-16Hz"

# (target start in seconds, line). Breathing cues sit on the swell:
# a wave rises from 10k to 10k+5 s and falls from 10k+5 to 10k+10 s.
SCRIPT = [
    (20, "שלום."),
    (24, "הגעתם למקום שקט."),
    (30, "אין לאן למהר עכשיו. אין מה לעשות."),
    (38, "רק להקשיב... לטפטוף המים... לגלים... לנשימה."),
    (48, "תנו לעיניים להיעצם, לאט."),
    (60, "נשמו פנימה..."),
    (65, "ושחררו... לאט."),
    (70, "פנימה..."),
    (75, "והחוצה..."),
    (80, "עוד פעם אחת... פנימה."),
    (85, "ולאט... החוצה."),
    (96, "עכשיו תנו לנשימה לחזור לקצב שלה. היא יודעת את הדרך."),
    (110, "שימו לב למצח. תנו לו להתרכך."),
    (122, "הלסת משתחררת... הלשון נחה."),
    (134, "הכתפיים יורדות, רחוק מהאוזניים."),
    (146, "הזרועות כבדות... וחמימות."),
    (158, "כפות הידיים פתוחות ורכות."),
    (170, "החזה עולה ויורד מעצמו, כמו גלים."),
    (182, "הבטן רכה."),
    (192, "הגב נשען, וכל המשקל נמסר למיטה."),
    (206, "הרגליים כבדות... ונעימות."),
    (218, "עד קצות האצבעות."),
    (234, "דמיינו מערה שקטה וקרירה."),
    (246, "טיפות מים נופלות, אחת אחת, לתוך בריכה צלולה."),
    (260, "כל טיפה... לוקחת איתה עוד קצת מהיום."),
    (274, "עוד מחשבה... ועוד דאגה... נמסות במים."),
    (290, "אין צורך להחזיק בשום דבר."),
    (304, "המים יודעים לזרום. גם אתם."),
    (320, "עכשיו אספור לאט, מעשר עד אחת."),
    (330, "עשר..."), (340, "תשע..."), (350, "שמונה..."), (360, "שבע..."), (370, "שש..."),
    (380, "חמש..."), (390, "ארבע..."), (400, "שלוש..."), (410, "שתיים..."), (420, "אחת."),
    (434, "אתם בטוחים. אתם רגועים."),
    (448, "אפשר להישאר כאן, כמה שרוצים."),
    (462, "לילה טוב."),
]


async def speak(text, path):
    import certifi
    import edge_tts

    # Behind a TLS-inspecting proxy, trust its CA bundle (verification stays on).
    bundle = os.environ.get("SSL_CERT_FILE") or ("/root/.ccr/ca-bundle.crt" if os.path.exists("/root/.ccr/ca-bundle.crt") else None)
    if bundle:
        certifi.where = lambda: bundle
    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
    for attempt in range(4):
        try:
            await edge_tts.Communicate(text, VOICE, rate=RATE, pitch=PITCH, proxy=proxy).save(path)
            return
        except Exception:
            if attempt == 3:
                raise
            await asyncio.sleep(2 ** (attempt + 1))


def decode_mono(path):
    raw = subprocess.run(
        [ffmpeg_exe(), "-loglevel", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SAMPLE_RATE), "-"],
        capture_output=True, check=True).stdout
    x = np.frombuffer(raw, np.float32).copy()
    active = np.flatnonzero(np.abs(x) > 0.01)
    x = x[max(0, active[0] - 400): active[-1] + 2000]   # trim silent edges
    fade = np.minimum(1, np.arange(len(x)) / 200) * np.minimum(1, np.arange(len(x))[::-1] / 2000)
    return x * fade


def whisper(x, n_fft=1024, hop=256, seed=3):
    """Same spectral envelope, random phase: the voice becomes breath."""
    rng = np.random.default_rng(seed)
    win = np.hanning(n_fft).astype(np.float32)
    pad = np.concatenate([np.zeros(n_fft), x, np.zeros(n_fft)]).astype(np.float32)
    out = np.zeros_like(pad)
    norm = np.zeros_like(pad)
    for i in range(0, len(pad) - n_fft, hop):
        mag = np.abs(np.fft.rfft(pad[i:i + n_fft] * win))
        ph = np.exp(2j * np.pi * rng.random(len(mag)))
        out[i:i + n_fft] += np.fft.irfft(mag * ph, n_fft).astype(np.float32) * win
        norm[i:i + n_fft] += win**2
    return (out / np.maximum(norm, 1e-3))[n_fft:n_fft + len(x)]


def tone(x):
    """Close-mic warmth, applied as a linear-phase curve in the frequency domain."""
    f = np.fft.rfftfreq(len(x), 1 / SAMPLE_RATE)
    g_db = (4.5 / (1 + (f / 180) ** 2)                               # low shelf, proximity warmth
            - 3.5 * np.exp(-0.5 * (np.log2(np.maximum(f, 1) / 3800) / 0.6) ** 2)   # soften presence
            - 10 * np.log10(1 + (f / 9000) ** 4))                   # roll off the top
    g_db -= 20 * np.log10(1 + (60 / np.maximum(f, 1)) ** 4) / 2      # clear sub-rumble
    return np.fft.irfft(np.fft.rfft(x) * 10 ** (g_db / 20), len(x)).astype(np.float32)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", default="audio")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    lines = []
    with tempfile.TemporaryDirectory() as tmp:
        for i, (_, text) in enumerate(SCRIPT):
            path = os.path.join(tmp, f"{i}.mp3")
            asyncio.run(speak(text, path))
            x = decode_mono(path)
            lines.append(x / np.sqrt(np.mean(x**2)))   # even level line to line
            print(f"{i + 1}/{len(SCRIPT)} {text}")

    # Place each line at its cue, never overlapping the previous one.
    starts, t = [], 0
    for (cue, _), x in zip(SCRIPT, lines):
        start = max(int(cue * SAMPLE_RATE), t + int(0.8 * SAMPLE_RATE))
        starts.append(start)
        t = start + len(x)
    n = t + 4 * SAMPLE_RATE
    dry = np.zeros(n, np.float32)
    for s, x in zip(starts, lines):
        dry[s:s + len(x)] += x

    # Whisper layer: keep only its airy top so it reads as breath, not noise.
    breath = whisper(dry)
    f = np.fft.rfftfreq(n, 1 / SAMPLE_RATE)
    breath = np.fft.irfft(np.fft.rfft(breath) / np.sqrt(1 + (1400 / np.maximum(f, 1)) ** 4), n).astype(np.float32)
    voice = tone(dry + breath * db(-7))

    # Ear-to-ear: alternate each line's position, with up to 0.35 ms interaural delay.
    positions = [-0.35, 0.3, -0.2, 0.35, -0.3, 0.2]
    stereo = np.zeros((n, 2), np.float32)
    for i, (s, x) in enumerate(zip(starts, lines)):
        seg_end = min(n, s + len(x) + int(0.5 * SAMPLE_RATE))
        seg = voice[s:seg_end]
        pan = positions[i % len(positions)]
        itd = int(abs(pan) * 0.001 * SAMPLE_RATE)
        near, far = np.sqrt((1 + abs(pan)) / 2), np.sqrt((1 - abs(pan)) / 2)
        late = np.concatenate([np.zeros(itd, np.float32), seg])[:len(seg)]
        left, right = (seg * near, late * far) if pan < 0 else (late * far, seg * near)
        stereo[s:seg_end, 0] += left
        stereo[s:seg_end, 1] += right

    stereo = reverb(stereo, np.random.default_rng(5), seconds=1.2, rt60=0.6, wet=0.14)
    speech = np.abs(stereo).max(axis=1) > 0.05
    level = np.sqrt(np.mean(stereo[speech] ** 2))
    write_mp3(os.path.join(args.out, "asmr_voice.mp3"), stereo / level * db(-20))


if __name__ == "__main__":
    main()
