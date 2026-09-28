#!/usr/bin/env python3
"""Generate relaxing noise and ambient music tracks as seamless-loop MP3 files.

All noise is synthesized in the frequency domain (random phase + spectral
shaping + inverse FFT). An inverse FFT is inherently periodic, so every track
loops perfectly with no click or gap at the seam, and the left/right channels
are independent noise for a wide, natural stereo image. The music is built to
be circular too: pad frequencies complete whole cycles per chord loop, and
bell tails and reverb wrap around the end of the file.

Tracks:
  white_noise.mp3     - true white noise (flat spectrum), set a bit quieter
                        because white noise is perceived as bright/hissy
  pink_noise.mp3      - pink noise (-3 dB/octave), the warmer, "rain-like" one
  brown_noise.mp3     - brown noise (-6 dB/octave), deep and rumbly like
                        distant surf or wind
  relaxing_mix.mp3    - pink noise bed + softened white noise that swells
                        slowly at 0.1 Hz (one "wave" every 10 s = 6 breaths
                        per minute, a common relaxation breathing pace)
  relaxing_music.mp3  - original generative ambient music in D major: slow
                        pad chords (Dmaj9 - Bm9 - Gmaj9 - Asus2, 15 s each)
                        with sparse pentatonic bells and a long reverb
  all_together.mp3    - the music over brown, pink and softened white noise
                        with the 0.1 Hz swell

Usage:
  pip install numpy imageio-ffmpeg
  python3 generate_noise.py [--minutes 10] [--out audio]
"""

import argparse
import os
import subprocess

import numpy as np

SAMPLE_RATE = 44100
BITRATE = "128k"
WAVE_HZ = 0.1          # surf swell, one wave every 10 s
CHORD_SEC = 15         # four chords -> 60 s harmonic cycle
CHORDS = [             # MIDI notes, voiced low and open
    [50, 57, 61, 64, 66],   # Dmaj9:  D3 A3 C#4 E4 F#4
    [47, 54, 57, 61, 62],   # Bm9:    B2 F#3 A3 C#4 D4
    [43, 50, 54, 57, 59],   # Gmaj9:  G2 D3 F#3 A3 B3
    [45, 52, 57, 59, 64],   # Asus2:  A2 E3 A3 B3 E4
]
BELL_SCALE = [69, 71, 74, 76, 78, 81, 83, 86]   # D major pentatonic, A4..D6


def ffmpeg_exe():
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return "ffmpeg"


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ---------------------------------------------------------------- noise

def shaped_noise(n, rng, shape):
    """Periodic noise of length n whose power spectrum is shape(freqs)."""
    spectrum = rng.standard_normal(n // 2 + 1) + 1j * rng.standard_normal(n // 2 + 1)
    freqs = np.fft.rfftfreq(n, 1 / SAMPLE_RATE)
    spectrum *= np.sqrt(shape(freqs))
    spectrum[0] = 0  # remove DC
    signal = np.fft.irfft(spectrum, n)
    return (signal / np.sqrt(np.mean(signal**2))).astype(np.float32)  # unit RMS


def white(f):
    return np.ones_like(f)


def pink(f):
    # 1/f power, with a gentle high-pass at 20 Hz to keep sub-bass rumble out.
    f = np.maximum(f, 1.0)
    return (1.0 / f) * (f**2 / (f**2 + 20.0**2))


def brown(f):
    # 1/f^2 power, with a 2nd-order high-pass at 30 Hz: brown noise piles up
    # energy toward DC, which small speakers can't play and which only muddies.
    f = np.maximum(f, 1.0)
    return (1.0 / f**2) * (f**4 / (f**4 + 30.0**4))


def soft_white(f):
    # White noise with a 2nd-order low-pass around 5 kHz: airy, not hissy.
    return 1.0 / (1.0 + (f / 5000.0) ** 4)


def stereo(n, rng, shape):
    return np.stack([shaped_noise(n, rng, shape) for _ in range(2)], axis=1)


def swell(n):
    # 0.1 Hz divides any whole-minute length, so the swell stays in phase
    # across the loop seam.
    t = np.arange(n) / SAMPLE_RATE
    s = 0.55 + 0.45 * (0.5 - 0.5 * np.cos(2 * np.pi * WAVE_HZ * t)) ** 1.5
    return s.astype(np.float32)[:, None]


# ---------------------------------------------------------------- music

def pad_cycle():
    """One 60 s cycle of the chord pad (stereo), periodic sample-for-sample."""
    cycle = CHORD_SEC * len(CHORDS)
    n = cycle * SAMPLE_RATE
    t = np.arange(n) / SAMPLE_RATE
    out = np.zeros((n, 2), np.float32)
    for i, chord in enumerate(CHORDS):
        # Hold each chord 10 s, cross-fade 5 s into the next (cos^2 tapers
        # of neighbours sum to 1).
        d = np.abs(((t - (i + 0.5) * CHORD_SEC) + cycle / 2) % cycle - cycle / 2)
        env = np.where(d < 5, 1.0, np.cos(np.pi / 2 * np.clip((d - 5) / 5, 0, 1)) ** 2)
        voice = np.zeros((n, 2))
        for m in chord:
            for detune, pan in ((1.0, 0.5), (1.0017, 0.15), (0.9983, 0.85)):
                # Round to whole cycles per loop so the tiled pad never clicks.
                f = round(midi_hz(m) * detune * cycle) / cycle
                wave = (np.sin(2 * np.pi * f * t)
                        + 0.18 * np.sin(2 * np.pi * 2 * f * t)
                        + 0.06 * np.sin(2 * np.pi * 3 * f * t))
                gain = 1.0 / (1 + (m - 45) / 24)   # tilt: upper notes softer
                voice[:, 0] += wave * gain * np.sqrt(1 - pan)
                voice[:, 1] += wave * gain * np.sqrt(pan)
        out += (voice * env[:, None]).astype(np.float32)
    return out


def bells(n, rng):
    """Sparse pentatonic bell melody over the whole file, wrapping at the end."""
    out = np.zeros((n, 2), np.float32)
    length = 5.0
    tt = np.arange(int(length * SAMPLE_RATE)) / SAMPLE_RATE
    idx = 3
    t0 = 1.5
    while t0 < n / SAMPLE_RATE:
        if rng.random() > 0.22:  # leave some space between phrases
            idx = int(np.clip(idx + rng.choice([-2, -1, -1, 0, 1, 1, 2]), 0, len(BELL_SCALE) - 1))
            f = midi_hz(BELL_SCALE[idx])
            vel = rng.uniform(0.45, 1.0)
            env = (1 - np.exp(-tt / 0.012)) * np.exp(-tt / 1.3)
            note = env * (np.sin(2 * np.pi * f * tt)
                          + 0.25 * np.exp(-tt / 0.4) * np.sin(2 * np.pi * 2.76 * f * tt)
                          + 0.08 * np.exp(-tt / 0.2) * np.sin(2 * np.pi * 5.4 * f * tt)) * vel
            pan = rng.uniform(0.25, 0.75)
            start = int(t0 * SAMPLE_RATE)
            pos = (start + np.arange(len(tt))) % n
            out[pos, 0] += (note * np.sqrt(1 - pan)).astype(np.float32)
            out[pos, 1] += (note * np.sqrt(pan)).astype(np.float32)
        t0 += rng.uniform(2.0, 4.8)
    return out


def reverb(x, rng, seconds=4.0, rt60=3.6, wet=0.45):
    """Circular FFT convolution with a dark synthetic hall impulse response."""
    n = len(x)
    k = int(seconds * SAMPLE_RATE)
    t = np.arange(k) / SAMPLE_RATE
    out = np.empty_like(x)
    for c in range(2):
        ir = rng.standard_normal(k) * np.exp(-6.9 * t / rt60)
        ir = np.convolve(ir, np.ones(12) / 12, mode="same")   # darken the tail
        ir[: int(0.02 * SAMPLE_RATE)] = 0                      # 20 ms pre-delay
        ir /= np.sqrt(np.sum(ir**2))
        spec = np.fft.rfft(x[:, c].astype(np.float64)) * np.fft.rfft(ir, n)
        out[:, c] = (1 - wet) * x[:, c] + wet * np.fft.irfft(spec, n).astype(np.float32)
    return out


def music(n, rng):
    cycle = CHORD_SEC * len(CHORDS) * SAMPLE_RATE
    if n % cycle:
        raise ValueError("music length must be a whole number of minutes")
    pad = np.tile(pad_cycle(), (n // cycle, 1))
    pad /= np.sqrt(np.mean(pad**2))
    bell = bells(n, rng)
    bell /= np.sqrt(np.mean(bell**2))
    mix = reverb(pad * db(-2) + bell * db(-9), rng)
    return mix / np.sqrt(np.mean(mix**2))


# ---------------------------------------------------------------- output

def db(x):
    return 10 ** (x / 20)


def write_mp3(path, samples):
    peak = np.max(np.abs(samples))
    if peak > db(-1):
        raise ValueError(f"{path}: peak {20 * np.log10(peak):.1f} dBFS would clip")
    pcm = (samples * 32767).astype("<i2").tobytes()
    subprocess.run(
        [ffmpeg_exe(), "-y", "-loglevel", "error",
         "-f", "s16le", "-ar", str(SAMPLE_RATE), "-ac", "2", "-i", "-",
         "-c:a", "libmp3lame", "-b:a", BITRATE, path],
        input=pcm, check=True,
    )
    print(f"{path}: {os.path.getsize(path) / 1e6:.1f} MB, peak {20 * np.log10(peak):.1f} dBFS")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--minutes", type=int, default=10)
    parser.add_argument("--out", default="audio")
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    out = lambda name: os.path.join(args.out, name)
    rng = np.random.default_rng(args.seed)
    n = args.minutes * 60 * SAMPLE_RATE

    # White noise sounds louder than pink at equal RMS, so it sits 3 dB lower;
    # brown noise sounds quieter (its energy is in the lows), so it sits higher.
    write_mp3(out("white_noise.mp3"), stereo(n, rng, white) * db(-22))
    write_mp3(out("pink_noise.mp3"), stereo(n, rng, pink) * db(-19))

    # Relaxing mix: a steady pink bed plus soft white noise that swells and
    # recedes like slow surf.
    bed = stereo(n, rng, pink) * db(-23)
    surf = stereo(n, rng, soft_white) * db(-23) * swell(n)
    write_mp3(out("relaxing_mix.mp3"), bed + surf)
    del bed, surf

    brown_bed = stereo(n, rng, brown)
    write_mp3(out("brown_noise.mp3"), brown_bed * db(-17))

    tune = music(n, rng)
    write_mp3(out("relaxing_music.mp3"), tune * db(-21))

    # Everything together: the music on top, noise beds 7-14 dB beneath it so
    # they fill the room without masking the melody.
    mix = (tune * db(-22)
           + brown_bed * db(-29)
           + stereo(n, rng, pink) * db(-33)
           + stereo(n, rng, soft_white) * db(-36) * swell(n))
    write_mp3(out("all_together.mp3"), mix)


if __name__ == "__main__":
    main()
