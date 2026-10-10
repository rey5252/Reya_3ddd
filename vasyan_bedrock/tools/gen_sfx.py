"""Synthesizes the add-on's comedy sound effects from scratch (no samples): boom, sad trombone, boing,
air horn, ding, record scratch, crickets, drum roll.

Run from anywhere:  python3 vasyan_bedrock/tools/gen_sfx.py   (needs ffmpeg)
Writes resource_pack/sounds/sfx/<name>.ogg; tools/gen_voice.py adds them to sound_definitions.json as
vasyan.sfx.<name>.
"""
import math, os, random, struct, subprocess, tempfile, wave

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "resource_pack", "sounds", "sfx")
RATE = 24000
TAU = 2 * math.pi
rng = random.Random(7)  # same sounds on every run


def secs(d):
    return range(int(d * RATE))


def lowpass(xs, fc):
    a = 1 - math.exp(-TAU * fc / RATE)
    y, out = 0.0, []
    for x in xs:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(xs, fc):
    lp = lowpass(xs, fc)
    return [x - l for x, l in zip(xs, lp)]


def saw(ph):
    return 2 * (ph - math.floor(ph + 0.5))


def write(name, xs):
    peak = max(1e-9, max(abs(x) for x in xs))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, x / peak * 0.92)) * 32767)) for x in xs)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(data)
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(OUT, name + ".ogg")], check=True)
    print("wrote", name)


def boom():
    """A deep falling thump with a short crack on top."""
    ph, xs = 0.0, []
    for i in secs(1.8):
        t = i / RATE
        ph += (32 + 70 * math.exp(-7 * t)) / RATE
        body = math.sin(TAU * ph) * math.exp(-2.0 * t)
        crack = (rng.random() * 2 - 1) * math.exp(-35 * t) * 0.7
        xs.append(math.tanh(2.2 * (body + crack)))
    return lowpass(xs, 900)


def sad_trombone():
    """Four falling brassy notes, the last one long and wobbly."""
    notes = [(196.0, 0.42), (185.0, 0.42), (174.6, 0.42), (164.8, 1.4)]
    xs, ph = [], 0.0
    for n, (f, d) in enumerate(notes):
        last = n == len(notes) - 1
        for i in secs(d):
            t = i / RATE
            vib = 1 + (0.025 * math.sin(TAU * 5.5 * t) * min(1, t / 0.3) if last else 0)
            ph += f * vib / RATE
            env = min(1, t / 0.04) * (math.exp(-1.2 * max(0, t - 0.6)) if last else min(1, (d - t) / 0.05))
            xs.append((saw(ph) + 0.4 * saw(2 * ph)) * env)
    return lowpass(lowpass(xs, 1400), 2200)


def boing():
    """A spring: pitch jumps up and wobbles while it dies out."""
    ph, xs = 0.0, []
    for i in secs(0.8):
        t = i / RATE
        f = (170 + 330 * (1 - math.exp(-14 * t))) * (1 + 0.3 * math.sin(TAU * 13 * t) * math.exp(-3.5 * t))
        ph += f / RATE
        xs.append((math.sin(TAU * ph) + 0.3 * math.sin(2 * TAU * ph)) * math.exp(-4 * t) * min(1, t / 0.005))
    return xs


def air_horn():
    """Three blasts of a loud detuned chord."""
    xs = []
    chord = [415.0, 523.0, 622.0]
    for d in (0.16, 0.16, 0.7):
        phs = [0.0] * 6
        for i in secs(d):
            t = i / RATE
            v = 0.0
            for k, f in enumerate(chord):
                for j, det in enumerate((0.996, 1.004)):
                    phs[k * 2 + j] += f * det / RATE
                    v += saw(phs[k * 2 + j])
            xs.append(math.tanh(0.5 * v) * min(1, t / 0.01) * min(1, (d - t) / 0.02))
        xs.extend([0.0] * int(0.06 * RATE))
    return lowpass(xs, 3500)


def ding():
    """A bright little bell."""
    xs = []
    parts = [(1.0, 1.0), (2.01, 0.5), (3.02, 0.3), (4.17, 0.2)]
    for i in secs(1.4):
        t = i / RATE
        xs.append(sum(a * math.sin(TAU * 1318 * r * t) * math.exp(-3 * t * (1 + k)) for k, (r, a) in enumerate(parts)))
    return xs


def scratch():
    """A record scratch: a rough tone sliding down and back up."""
    ph, xs = 0.0, []
    d = 0.55
    for i in secs(d):
        t = i / RATE
        f = 900 * math.exp(-6 * t) + 120 if t < 0.3 else 120 + 1800 * (t - 0.3)
        ph += f / RATE
        noise = rng.random() * 2 - 1
        xs.append((0.6 * saw(ph) + 0.5 * noise) * math.sin(math.pi * t / d))
    return lowpass(highpass(xs, 250), 3000)


def crickets():
    """Awkward silence: a few cricket chirps."""
    xs = [0.0] * int(0.15 * RATE)
    for _ in range(3):
        for _ in range(3):
            for i in secs(0.11):
                t = i / RATE
                xs.append(math.sin(TAU * 4300 * t) * (0.5 + 0.5 * math.sin(TAU * 32 * t)) * math.sin(math.pi * t / 0.11) * 0.6)
            xs.extend([0.0] * int(0.05 * RATE))
        xs.extend([0.0] * int(0.35 * RATE))
    return xs


def drum_roll():
    """A snare roll getting louder, then a crash."""
    xs = []
    hits = 32
    for h in range(hits):
        loud = 0.25 + 0.75 * h / hits
        for i in secs(0.04):
            t = i / RATE
            xs.append((rng.random() * 2 - 1) * math.exp(-70 * t) * loud)
    for i in secs(1.3):
        t = i / RATE
        xs.append((rng.random() * 2 - 1) * math.exp(-3 * t) * (1.0 if t > 0.002 else t / 0.002))
    return highpass(xs, 400)


SOUNDS = {
    "boom": boom,
    "sad": sad_trombone,
    "boing": boing,
    "airhorn": air_horn,
    "ding": ding,
    "scratch": scratch,
    "crickets": crickets,
    "drumroll": drum_roll,
}

if __name__ == "__main__":
    for name, make in SOUNDS.items():
        write(name, make())
