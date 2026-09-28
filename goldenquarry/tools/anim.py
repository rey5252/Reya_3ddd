"""Animated item textures: a shine sweeping across the item, and a gem that glows on and off.

Frames are stacked in a strip (16 wide, 16 per frame) with a .png.mcmeta next to the texture.
"""
import json
import math

from png_io import write_png


def _lighten(c, t):
    return tuple(int(round(q + (255 - q) * t)) for q in c[:3]) + (c[3],)


def frame(px, shine_pos=None, glow=0.0, gem=()):
    """px: 16x16 RGBA rows. A diagonal band of light at x + y = shine_pos; gem pixels lightened by glow."""
    out = []
    for y, row in enumerate(px):
        new = []
        for x, c in enumerate(row):
            if c[3] == 0:
                new.append(c)
                continue
            t = 0.0
            if shine_pos is not None:
                d = abs(x + y - shine_pos)
                t = 0.55 if d < 0.75 else 0.28 if d < 1.75 else 0.0
            if (x, y) in gem:
                t = max(t, glow)
            new.append(_lighten(c, t) if t > 0 else c)
        out.append(new)
    return out


def save(path, frames, frametime=2, first_hold=None):
    """Write the frames as a strip and the .mcmeta that plays them; first_hold keeps frame 0 on longer."""
    strip = [row for f in frames for row in f]
    write_png(path, 16, 16 * len(frames), strip)
    anim = {"frametime": frametime}
    if first_hold:
        anim["frames"] = [{"index": 0, "time": first_hold}] + list(range(1, len(frames)))
    with open(path + ".mcmeta", "w") as f:
        f.write(json.dumps({"animation": anim}, indent=2) + "\n")


def shine_only(px):
    """A card: the shine passes over it now and then."""
    frames = [px] + [frame(px, shine_pos=p) for p in range(-2, 33, 3)]
    return frames


def shine_and_glow(px, gem):
    """A fortune module: the gem glows on and off, and every other round the shine passes over."""
    frames = []
    n = 32
    for k in range(n):
        glow = 0.3 * (0.5 + 0.5 * math.sin(2 * math.pi * k / n))
        shine = -2 + k * 2.2 if k < 16 else None
        frames.append(frame(px, shine_pos=shine, glow=glow, gem=gem))
    return frames
