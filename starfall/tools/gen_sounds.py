"""Starfall's sounds, synthesized from scratch (run from the mod folder; needs numpy and ffmpeg with libvorbis).

Every sound is built from oscillators, filtered noise, envelopes and a synthetic reverb, then written as mono
Ogg Vorbis to assets/starfall/sounds/. Nothing is sampled from anywhere.
"""
import math
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/starfall/sounds"
RNG = np.random.default_rng(1997)


# ---------------------------------------------------------------- building blocks

def T(d):
    return np.arange(int(d * SR)) / SR


def silence(d):
    return np.zeros(int(d * SR))


def noise(n):
    return RNG.standard_normal(n)


def brown(n):
    x = np.cumsum(RNG.standard_normal(n))
    x -= np.convolve(x, np.ones(2048) / 2048, mode="same")
    return x / (np.max(np.abs(x)) + 1e-9)


def osc(freq, d=None, shape="sine", phase=0.0):
    """An oscillator whose frequency may change over time (an array, one value per sample)."""
    if np.isscalar(freq):
        freq = np.full(int(d * SR), float(freq))
    ph = phase + 2 * np.pi * np.cumsum(freq) / SR
    if shape == "sine":
        return np.sin(ph)
    if shape == "saw":
        # band-limited enough: a handful of harmonics, fewer as the pitch rises
        out = np.zeros_like(ph)
        for k in range(1, 9):
            out += np.sin(k * ph) / k * (freq * k < SR * 0.45)
        return out * 0.6
    if shape == "square":
        out = np.zeros_like(ph)
        for k in range(1, 12, 2):
            out += np.sin(k * ph) / k * (freq * k < SR * 0.45)
        return out * 0.9
    raise ValueError(shape)


def env(n, attack, decay_tau, hold=0.0):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    d = np.where(t < attack + hold, 1.0, np.exp(-(t - attack - hold) / decay_tau))
    return a * d


def fade_out(x, d):
    n = min(len(x), int(d * SR))
    if n > 0:
        x[-n:] *= np.linspace(1, 0, n) ** 2
    return x


def filt(x, lo=None, hi=None):
    """A gentle band-pass in the frequency domain (lo/hi in Hz, either may be None)."""
    n = len(x)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    m = np.ones_like(f)
    if lo:
        m *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** 4)
    if hi:
        m *= 1 / np.sqrt(1 + (f / hi) ** 4)
    return np.fft.irfft(X * m, n)


def sweep_filt(x, lo_fn=None, hi_fn=None, bp=None):
    """A filter that changes over time: lo_fn/hi_fn give cut-offs (Hz) for a time in seconds, or bp gives a band
    (centre, width in octaves). Done frame by frame with overlap-add."""
    N, H = 2048, 512
    win = np.hanning(N)
    out = np.zeros(len(x) + N)
    norm = np.zeros(len(x) + N)
    f = np.fft.rfftfreq(N, 1 / SR)
    pad = np.concatenate([x, np.zeros(N)])
    for start in range(0, len(x), H):
        seg = pad[start:start + N] * win
        t = (start + N / 2) / SR
        m = np.ones_like(f)
        if lo_fn:
            m *= 1 / np.sqrt(1 + (lo_fn(t) / np.maximum(f, 1e-3)) ** 4)
        if hi_fn:
            m *= 1 / np.sqrt(1 + (f / hi_fn(t)) ** 4)
        if bp:
            c, w = bp(t)
            oct_ = np.log2(np.maximum(f, 1.0) / c)
            m *= np.exp(-0.5 * (oct_ / w) ** 2)
        out[start:start + N] += np.fft.irfft(np.fft.rfft(seg) * m, N) * win
        norm[start:start + N] += win ** 2
    return (out / np.maximum(norm, 1e-6))[:len(x)]


def reverb(x, decay=2.0, mix=0.3, bright=6000.0, pre=0.02):
    """Convolution with a synthetic room: decaying noise, darker as it fades."""
    # the dry sound must end softly, or the room rings with the click of it being cut off
    x = fade_out(np.array(x, dtype=float), min(0.3, len(x) / SR * 0.25))
    n = int(decay * SR)
    t = np.arange(n) / SR
    ir = noise(n) * np.exp(-t * 6.9 / decay)
    ir = sweep_filt(ir, hi_fn=lambda tt: bright * math.exp(-tt * 1.5 / decay) + 400)
    ir = np.concatenate([np.zeros(int(pre * SR)), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    L = len(x) + len(ir)
    nfft = 1 << (L - 1).bit_length()
    wet = np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(ir, nfft), nfft)[:L]
    dry = np.concatenate([x, np.zeros(len(ir))])
    return dry * (1 - mix) + wet * mix * 3.0


def place(dst, src, at, gain=1.0):
    i = int(at * SR)
    if i >= len(dst):
        return dst
    n = min(len(src), len(dst) - i)
    dst[i:i + n] += src[:n] * gain
    return dst


def grain(d, lo, hi):
    g = filt(noise(int(d * SR) + 64), lo, hi)[:int(d * SR)]
    return g * np.hanning(len(g))


def bell(freq, d, partials=((1.0, 1.0, 1.0), (2.0, 0.5, 0.6), (2.76, 0.45, 0.45), (3.76, 0.3, 0.3), (5.4, 0.2, 0.18))):
    t = T(d)
    out = np.zeros_like(t)
    for ratio, amp, tau in partials:
        for det in (-0.6, 0.6):
            out += amp * np.sin(2 * np.pi * (freq * ratio + det) * t) * np.exp(-t / (tau * d * 0.6))
    return out * np.clip(t / 0.004, 0, 1)


def finish(x, peak=0.89, tail=0.05):
    x = x - np.mean(x)
    x = fade_out(x, tail)
    x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * 1.4) / np.tanh(1.4)
    return x * peak


def write(name, x):
    x = finish(x)
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav = tmp.name
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    os.remove(wav)
    print(f"{name}: {len(x) / SR:.2f} s, {os.path.getsize(path) // 1024} KB")


# ---------------------------------------------------------------- SS-01

def railgun_charge():
    d = 2.3
    t = T(d)
    p = t / d
    f = 90 * 25 ** (p ** 1.3)
    whine = osc(f, shape="saw") + 0.35 * osc(f * 1.5, shape="saw") + 0.2 * osc(f * 0.5, shape="sine")
    whine *= 0.15 + 0.85 * p ** 1.5
    whine = sweep_filt(whine, hi_fn=lambda tt: 600 + 7000 * (tt / d) ** 1.2)
    sub = (np.sin(2 * np.pi * 45 * t) + 0.5 * np.sin(2 * np.pi * 90 * t)) * (0.2 + 0.8 * p) * (0.8 + 0.2 * np.sin(2 * np.pi * 6 * t))
    x = whine * 0.6 + sub * 0.5
    # capacitor banks ticking faster and faster
    tick = 0.0
    while tick < d - 0.05:
        rate = 8 + 50 * (tick / d) ** 1.4
        place(x, grain(0.004, 2000, 5000) * 2.0, tick, 0.25 + 0.3 * tick / d)
        tick += 1.0 / rate
    # sparks crackling, more and more
    for _ in range(260):
        at = d * RNG.random() ** 0.6
        place(x, grain(0.002 + 0.004 * RNG.random(), 2500, 8000), at, 0.2 + 0.5 * at / d)
    return reverb(x, 1.0, 0.18)


def railgun_fire():
    d = 4.5
    t = T(d)
    x = np.zeros_like(t)
    place(x, filt(noise(int(0.02 * SR)), 2000, None) * np.hanning(int(0.02 * SR)), 0.0, 1.2)
    fz = 4000 * (120 / 4000) ** np.clip(t / 0.35, 0, 1)
    zap = np.tanh(3 * osc(fz)) * np.exp(-t / 0.18)
    fb = 110 * (32 / 110) ** np.clip(t / 0.6, 0, 1)
    boom = np.tanh(2.5 * np.sin(2 * np.pi * np.cumsum(fb) / SR)) * np.exp(-t / 0.9)
    body = sweep_filt(noise(len(t)), hi_fn=lambda tt: 150 + 5000 * math.exp(-tt / 0.5)) * np.exp(-t / 1.0)
    rumble = filt(brown(len(t)), None, 120) * np.exp(-t / 2.0) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.7 * t))
    x += zap * 0.5 + boom * 1.0 + body * 0.7 + rumble * 0.9
    return reverb(x, 2.6, 0.35, bright=4000)


def railgun_beam():
    d = 5.0
    t = T(d)
    roar = filt(brown(len(t)) + 0.3 * noise(len(t)), 60, 800)
    turb = 0.7 + 0.3 * filt(noise(len(t)), None, 8) / 0.05
    hum = filt(osc(55 + 1.5 * np.sin(2 * np.pi * 3 * t), shape="saw") + 0.5 * osc(110, d, "saw"), None, 900)
    x = roar * np.clip(turb, 0.3, 1.5) * 0.9 + hum * 0.35
    for _ in range(500):
        place(x, grain(0.002 + 0.003 * RNG.random(), 1500, 7000), d * RNG.random(), 0.25)
    e = np.clip(t / 0.15, 0, 1) * np.clip((d - t) / 1.2, 0, 1)
    return reverb(x * e, 1.8, 0.3, bright=3000)


def railgun_impact():
    d = 6.0
    t = T(d)
    fb = 60 * (30 / 60) ** np.clip(t / 0.5, 0, 1)
    thump = np.tanh(2.0 * np.sin(2 * np.pi * np.cumsum(fb) / SR)) * np.exp(-t / 0.5)
    burst = sweep_filt(noise(len(t)), hi_fn=lambda tt: 200 + 3000 * math.exp(-tt / 0.5)) * np.exp(-t / 1.4)
    x = thump + burst * 0.8 + filt(brown(len(t)), None, 100) * np.exp(-t / 2.5) * 0.8
    for _ in range(420):
        at = 0.15 + 4.0 * RNG.random() ** 1.8
        place(x, grain(0.001 + 0.004 * RNG.random(), 900, 4500), at, 0.35 * math.exp(-at / 1.5))
    return reverb(x, 3.0, 0.38, bright=3500)


def railgun_arc():
    d = 0.6
    t = T(d)
    buzz = osc(120 * (1 + 0.15 * filt(noise(len(t)), None, 40) / 0.05), shape="saw")
    gate = (filt(noise(len(t)), None, 60) > 0.0).astype(float)
    x = filt(buzz * gate, 300, 9000) + grain(d, 3000, 10000) * 0.5
    return x * env(len(t), 0.005, 0.18)


# ---------------------------------------------------------------- the film

def whoosh():
    d = 1.3
    t = T(d)
    x = sweep_filt(noise(len(t)), bp=lambda tt: (300 * (10 ** (math.sin(math.pi * tt / d) * 1.0)), 0.8))
    return x * np.sin(np.pi * t / d) ** 2


def warp():
    d = 1.8
    t = T(d)
    x = sweep_filt(noise(len(t)), bp=lambda tt: (200 * 40 ** (tt / d), 1.0)) * (t / d) ** 1.5
    x += 0.4 * osc(80 * 20 ** (t / d)) * (t / d) ** 2
    place(x, np.tanh(2 * np.sin(2 * np.pi * 50 * T(0.5))) * np.exp(-T(0.5) / 0.15), 1.5, 0.9)
    return reverb(x, 1.6, 0.3)


def lock():
    x = silence(0.8)
    for i in range(3):
        b = osc(1760, 0.055, "square") * env(int(0.055 * SR), 0.003, 0.05)
        place(x, filt(b, None, 6000), i * 0.1, 0.5)
    tt = T(0.32)
    place(x, filt(osc(2349 * (1 + 0.04 * tt / 0.32), shape="square"), None, 6000) * env(len(tt), 0.004, 0.2), 0.32, 0.55)
    return reverb(x, 0.6, 0.15)


def uplink():
    x = silence(1.2)
    place(x, grain(0.01, 1000, 8000), 0.0, 0.6)
    for i in range(14):
        f = 600 + 2400 * RNG.random()
        dd = 0.015 + 0.015 * RNG.random()
        place(x, osc(f, dd) * np.hanning(int(dd * SR)), 0.05 + i * 0.045, 0.35)
    tt = T(1.0)
    place(x, osc(300 * 2 ** (tt / 1.0)) * np.sin(np.pi * tt / 1.0) ** 2, 0.15, 0.35)
    return reverb(x, 0.9, 0.2)


def title():
    d = 3.2
    t = T(d)
    x = np.zeros_like(t)
    for f, a in ((55, 1.0), (55.4, 0.8), (110, 0.6), (165.2, 0.35), (82.5, 0.4)):
        x += a * osc(f + 0.3 * np.sin(2 * np.pi * 0.5 * t), shape="saw")
    x = sweep_filt(x, hi_fn=lambda tt: 200 + 1600 * math.exp(-((tt - 0.4) / 0.6) ** 2))
    x = x * env(len(t), 0.08, 1.2, 0.3) + 0.6 * np.sin(2 * np.pi * 41 * t) * env(len(t), 0.05, 1.0, 0.2)
    return reverb(x, 3.0, 0.4, bright=2500)


# ---------------------------------------------------------------- SS-03

def gungnir_spin():
    d = 4.8
    t = T(d)
    q = t / d
    x = 0.3 * osc(60 * 12 ** (q ** 1.5), shape="saw") * (0.2 + 0.8 * q)
    x = filt(x, None, 3000)
    for k in range(1, 8):
        at = d * (k / 7.0) ** (1 / 1.7)
        dd = max(0.12, 0.6 * (1.0 - k / 9.0))
        w = sweep_filt(noise(int(dd * SR)), bp=lambda tt, k=k: (400 + 300 * k + 3000 * tt, 0.7))
        w *= np.sin(np.pi * np.arange(len(w)) / len(w)) ** 2
        place(x, w, at - dd * 0.6, 0.8)
        place(x, bell(900 + 60 * k, 0.6, ((1, 1, 1), (2.32, 0.5, 0.6), (4.1, 0.3, 0.4))), at, 0.15)
    return reverb(x, 1.6, 0.25)


def gungnir_launch():
    d = 1.6
    t = T(d)
    fb = 70 * (40 / 70) ** np.clip(t / 0.4, 0, 1)
    thud = np.tanh(2 * np.sin(2 * np.pi * np.cumsum(fb) / SR)) * np.exp(-t / 0.4)
    clang = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t / tau) for f, a, tau in
                ((410, 0.6, 0.9), (1090, 0.45, 0.6), (1830, 0.35, 0.4), (2770, 0.25, 0.3), (3910, 0.15, 0.2)))
    away = sweep_filt(noise(len(t)), bp=lambda tt: (3000 * (400 / 3000) ** (tt / d), 0.9)) * np.exp(-t / 0.6)
    tone = osc(900 * (300 / 900) ** (t / d)) * np.exp(-t / 0.5) * 0.3
    return reverb(thud + clang * 0.4 + away * 0.6 + tone, 1.8, 0.3)


def gungnir_fall():
    d = 1.2
    t = T(d)
    f = 2600 * (700 / 2600) ** (t / d) * (1 + 0.01 * np.sin(2 * np.pi * 7 * t))
    x = osc(f) * np.clip(t / 0.1, 0, 1) * np.clip((d - t) / 0.15, 0, 1)
    x += 0.3 * sweep_filt(noise(len(t)), bp=lambda tt: (2000 * (500 / 2000) ** (tt / d), 0.8)) * (t / d)
    return reverb(x, 1.0, 0.25)


def gungnir_boom():
    d = 5.5
    t = T(d)
    burst = filt(noise(len(t)), None, 400) * np.exp(-t / 0.7)
    thump = np.tanh(2 * np.sin(2 * np.pi * 40 * t)) * np.exp(-t / 0.6)
    swell = 0.6 + 0.4 * filt(noise(len(t)), None, 2) / 0.02
    roll = filt(brown(len(t)), None, 90) * np.exp(-t / 2.4) * np.clip(swell, 0.2, 1.6)
    return reverb(burst * 0.8 + thump + roll * 0.9, 3.2, 0.4, bright=1500)


# ---------------------------------------------------------------- SS-04

def seven_wake():
    x = bell(880, 1.8)
    place(x, bell(1760 * 1.003, 1.2), 0.0, 0.25)
    shimmer = sum(np.sin(2 * np.pi * f * T(1.8)) * 0.05 for f in (3520, 3527, 4440))
    return reverb(x + shimmer * np.exp(-T(1.8) / 0.6), 2.0, 0.4, bright=9000)


def seven_array():
    d = 2.8
    t = T(d)
    x = np.zeros_like(t)
    for f in (220, 277.2, 329.6, 440):
        for det in (-0.8, 0.0, 0.8):
            x += osc(f + det, d, "saw") * 0.25
    vowel = filt(x, 500, 900) * 0.9 + filt(x, 1000, 1400) * 0.5 + filt(x, 2300, 2900) * 0.25
    vowel *= np.clip(t / 1.2, 0, 1) ** 2 * np.clip((d - t) / 0.6, 0, 1)
    for _ in range(40):
        place(vowel, bell(2000 + 4000 * RNG.random(), 0.3), d * RNG.random(), 0.04)
    return reverb(vowel, 2.6, 0.45, bright=8000)


def seven_beam():
    d = 1.6
    t = T(d)
    x = sweep_filt(noise(len(t)), bp=lambda tt: (9000 * (1200 / 9000) ** (tt / d), 0.7)) * np.sin(np.pi * t / d) ** 1.5
    for _ in range(60):
        place(x, osc(2000 + 6000 * RNG.random(), 0.05) * np.hanning(int(0.05 * SR)), d * RNG.random(), 0.08)
    x += 0.5 * np.sin(2 * np.pi * 50 * t) * (t / d) ** 2
    return reverb(x, 1.8, 0.35, bright=9000)


def seven_impact():
    d = 3.8
    t = T(d)
    fb = 80 * (35 / 80) ** np.clip(t / 0.4, 0, 1)
    thump = np.tanh(2 * np.sin(2 * np.pi * np.cumsum(fb) / SR)) * np.exp(-t / 0.4)
    burst = sweep_filt(noise(len(t)), hi_fn=lambda tt: 300 + 6000 * math.exp(-tt / 0.3)) * np.exp(-t / 0.8)
    x = thump + burst * 0.6
    for _ in range(500):
        at = 0.6 * RNG.random() ** 1.5
        place(x, osc(1500 + 7500 * RNG.random(), 0.03) * np.exp(-T(0.03) / 0.008), at, 0.12)
    ring = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t / 1.5) for f, a in ((1180, 0.5), (1870, 0.35), (2690, 0.25), (3730, 0.15)))
    return reverb(x + ring * 0.35, 2.8, 0.4, bright=8000)


def seven_ignite():
    d = 2.0
    t = T(d)
    roar = filt(noise(len(t)), 80, 1200) * np.clip(t / 0.25, 0, 1) * np.exp(-np.maximum(t - 0.4, 0) / 0.7)
    x = roar
    for _ in range(140):
        place(x, grain(0.003, 1000, 6000), d * RNG.random() ** 1.3, 0.4)
    return reverb(x, 1.4, 0.25)


def seven_flare():
    d = 5.0
    t = T(d)
    rise = np.clip(t / 1.8, 0, 1)
    swell = sweep_filt(noise(len(t)), bp=lambda tt: (300 * 20 ** min(1, tt / 1.8), 1.0)) * rise ** 3 * (t < 1.8)
    chord = sum(osc(f * (1 + 0.1 * rise), d, "saw") for f in (220, 330, 440)) * rise ** 2 * (t < 1.8) * 0.2
    x = swell + filt(chord, None, 3000)
    burst = np.zeros_like(t)
    place(burst, bell(1320, 3.0), 1.8, 0.6)
    place(burst, bell(1980, 2.5), 1.8, 0.36)
    place(burst, bell(990, 3.2), 1.8, 0.42)
    place(burst, filt(noise(int(2.5 * SR)), 2000, None) * np.exp(-T(2.5) / 0.4), 1.8, 0.5)
    return reverb(x + burst, 3.5, 0.45, bright=9000)


# ---------------------------------------------------------------- the remote and the menu

def remote_cover():
    x = silence(0.3)
    place(x, grain(0.004, 1800, 3500) * 1.5, 0.0, 0.9)
    place(x, grain(0.003, 3000, 6000) * 1.5, 0.045, 0.7)
    tt = T(0.14)
    place(x, np.sin(2 * np.pi * (600 + 40 * np.sin(2 * np.pi * 30 * tt)) * tt) * np.exp(-tt / 0.04), 0.05, 0.12)
    return reverb(x, 0.3, 0.1)


def remote_press():
    x = silence(0.45)
    tt = T(0.02)
    place(x, np.sin(2 * np.pi * 300 * tt) * np.exp(-tt / 0.006), 0.0, 0.8)
    place(x, grain(0.006, 1000, 3000) * 1.4, 0.0, 0.7)
    place(x, grain(0.004, 1500, 4000), 0.12, 0.5)
    for i, f in enumerate((1318.5, 1975.5)):
        b = osc(f, 0.045, "square") * env(int(0.045 * SR), 0.002, 0.04)
        place(x, filt(b, None, 7000), 0.16 + i * 0.05, 0.35)
    return reverb(x, 0.4, 0.12)


def ui_hover():
    return osc(1400, 0.08) * env(int(0.08 * SR), 0.002, 0.02) * 0.6


def ui_select():
    x = silence(0.3)
    for i, f in enumerate((880, 1320)):
        place(x, osc(f, 0.09) * env(int(0.09 * SR), 0.003, 0.05), i * 0.07, 0.6)
    return reverb(x, 0.4, 0.15)


SOUNDS = {
    "railgun/charge": railgun_charge, "railgun/fire": railgun_fire, "railgun/beam": railgun_beam,
    "railgun/impact": railgun_impact, "railgun/arc": railgun_arc,
    "film/whoosh": whoosh, "film/warp": warp, "film/lock": lock, "film/uplink": uplink, "film/title": title,
    "gungnir/spin": gungnir_spin, "gungnir/launch": gungnir_launch, "gungnir/fall": gungnir_fall, "gungnir/boom": gungnir_boom,
    "seven/wake": seven_wake, "seven/array": seven_array, "seven/beam": seven_beam, "seven/impact": seven_impact,
    "seven/ignite": seven_ignite, "seven/flare": seven_flare,
    "remote/cover": remote_cover, "remote/press": remote_press, "ui/hover": ui_hover, "ui/select": ui_select,
}

if __name__ == "__main__":
    import sys
    only = sys.argv[1:]
    for name, make in SOUNDS.items():
        if only and name not in only:
            continue
        write(name, make())
