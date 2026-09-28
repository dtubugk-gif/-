#!/usr/bin/env python3
"""Generate relaxing white / pink noise tracks as seamless-loop MP3 files.

All noise is synthesized in the frequency domain (random phase + spectral
shaping + inverse FFT). An inverse FFT is inherently periodic, so every track
loops perfectly with no click or gap at the seam, and the left/right channels
are independent noise for a wide, natural stereo image.

Tracks:
  white_noise.mp3    - true white noise (flat spectrum), set a bit quieter
                       because white noise is perceived as bright/hissy
  pink_noise.mp3     - pink noise (-3 dB/octave), the warmer, "rain-like" one
  relaxing_mix.mp3   - pink noise bed + softened white noise that swells
                       slowly at 0.1 Hz (one "wave" every 10 s = 6 breaths
                       per minute, a common relaxation breathing pace)

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


def ffmpeg_exe():
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return "ffmpeg"


def shaped_noise(n, rng, shape):
    """Periodic noise of length n whose power spectrum is shape(freqs)."""
    spectrum = rng.standard_normal(n // 2 + 1) + 1j * rng.standard_normal(n // 2 + 1)
    freqs = np.fft.rfftfreq(n, 1 / SAMPLE_RATE)
    gain = np.sqrt(shape(freqs))
    spectrum *= gain
    spectrum[0] = 0  # remove DC
    signal = np.fft.irfft(spectrum, n)
    return signal / np.sqrt(np.mean(signal**2))  # unit RMS


def white(f):
    return np.ones_like(f)


def pink(f):
    # 1/f power, with a gentle high-pass at 20 Hz to keep sub-bass rumble out.
    f = np.maximum(f, 1.0)
    return (1.0 / f) * (f**2 / (f**2 + 20.0**2))


def soft_white(f):
    # White noise with a 2nd-order low-pass around 5 kHz: airy, not hissy.
    return 1.0 / (1.0 + (f / 5000.0) ** 4)


def stereo(n, rng, shape):
    return np.stack([shaped_noise(n, rng, shape) for _ in range(2)], axis=1)


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
    parser.add_argument("--minutes", type=float, default=10)
    parser.add_argument("--out", default="audio")
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    rng = np.random.default_rng(args.seed)
    n = int(args.minutes * 60 * SAMPLE_RATE)

    # White noise sounds louder than pink at equal RMS, so it sits 3 dB lower.
    write_mp3(os.path.join(args.out, "white_noise.mp3"), stereo(n, rng, white) * db(-22))
    write_mp3(os.path.join(args.out, "pink_noise.mp3"), stereo(n, rng, pink) * db(-19))

    # Relaxing mix: a steady pink bed plus soft white noise that swells and
    # recedes like slow surf. 0.1 Hz divides any whole-minute length, so the
    # swell stays in phase across the loop seam.
    t = np.arange(n) / SAMPLE_RATE
    swell = 0.55 + 0.45 * (0.5 - 0.5 * np.cos(2 * np.pi * 0.1 * t)) ** 1.5
    bed = stereo(n, rng, pink) * db(-23)
    surf = stereo(n, rng, soft_white) * db(-23) * swell[:, None]
    write_mp3(os.path.join(args.out, "relaxing_mix.mp3"), bed + surf)


if __name__ == "__main__":
    main()
