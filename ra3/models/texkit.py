"""texkit — procedural, seamlessly tiling textures for the Fortress Protocol models.

numpy + Pillow. Every map is a float array in 0..1 of shape (S, S) or (S, S, 3);
all noise and filters wrap around the edges, so textures tile without seams.

A texture set is written as three files (see SPEC.md, section "Текстури"):
    <name>_dif.png  colour (sRGB)
    <name>_nrm.png  tangent-space normal map, OpenGL convention (+Y up, glTF style)
    <name>_spm.png  RA3-style mask: R = specular, G = self-illumination, B = house-colour mask
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 512


def rng(seed):
    return np.random.default_rng(seed)


def value_noise(size=SIZE, cells=8, seed=0):
    """Tileable smooth value noise in 0..1 with `cells` lattice cells per side."""
    g = rng(seed).random((cells, cells))
    t = np.arange(size) * cells / size
    i0 = np.floor(t).astype(int)
    f = t - i0
    f = f * f * (3 - 2 * f)
    i1 = (i0 + 1) % cells
    a = g[i0][:, i0]
    b = g[i0][:, i1]
    c = g[i1][:, i0]
    d = g[i1][:, i1]
    fx = f[None, :]
    fy = f[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(size=SIZE, cells=4, octaves=5, seed=0, gain=0.5):
    """Fractal noise (sum of value-noise octaves), normalised to 0..1."""
    out = np.zeros((size, size))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        c = cells * 2 ** o
        if c > size:
            break
        out += amp * value_noise(size, c, seed + o * 101)
        total += amp
        amp *= gain
    out /= total
    return (out - out.min()) / (np.ptp(out) or 1)


def blur(a, r=2):
    """Wrap-around box blur (repeated twice ~ gaussian)."""
    for _ in range(2):
        acc = np.zeros_like(a, dtype=float)
        for d in range(-r, r + 1):
            acc += np.roll(a, d, axis=0)
        a = acc / (2 * r + 1)
        acc = np.zeros_like(a, dtype=float)
        for d in range(-r, r + 1):
            acc += np.roll(a, d, axis=1)
        a = acc / (2 * r + 1)
    return a


def panel_grid(size=SIZE, cols=4, rows=4, gap=4, bevel=10, jitter=0.0, seed=0):
    """Height map of raised panels separated by grooves (1 = panel top, 0 = groove).

    jitter > 0 randomly merges/offsets rows for a less regular, hand-made layout.
    Returns (height, panel_id) — panel_id lets you tint panels individually.
    """
    r = rng(seed)
    h = np.zeros((size, size))
    pid = np.zeros((size, size), int)
    ys = np.linspace(0, size, rows + 1).astype(int)
    k = 0
    for j in range(rows):
        shift = int(r.random() * size / cols * jitter)
        xs = (np.linspace(0, size, cols + 1).astype(int) + shift) % size
        for i in range(cols):
            x0, x1 = xs[i], xs[(i + 1)] if i + 1 < cols else xs[0] + size
            y0, y1 = ys[j], ys[j + 1]
            yy, xx = np.mgrid[y0:y1, x0:x1]
            dist = np.minimum.reduce([xx - x0, x1 - 1 - xx, yy - y0, y1 - 1 - yy]).astype(float)
            v = np.clip((dist - gap) / max(bevel, 1), 0, 1)
            h[yy % size, xx % size] = v
            pid[yy % size, xx % size] = k
            k += 1
    return h, pid


def rivets(size=SIZE, spacing=32, radius=4, margin=0, seed=0, along="grid"):
    """Height map of small domed rivets on a regular grid (wraps)."""
    yy, xx = np.mgrid[0:size, 0:size]
    cx = (xx + spacing // 2) % spacing - spacing // 2
    cy = (yy + spacing // 2) % spacing - spacing // 2
    d = np.sqrt(cx ** 2 + cy ** 2)
    return np.clip(1 - (d / radius) ** 2, 0, 1) ** 0.5


def stripes(size=SIZE, period=64, angle_deg=45, duty=0.5):
    """Diagonal stripe mask (hazard stripes). Period must divide size for seamless tiling at 45°."""
    yy, xx = np.mgrid[0:size, 0:size]
    a = np.radians(angle_deg)
    t = (xx * np.cos(a) + yy * np.sin(a)) / period
    return ((t % 1.0) < duty).astype(float)


def scratches(size=SIZE, count=120, length=40, seed=0):
    """Thin random scratch lines (0..1 mask), wraps."""
    r = rng(seed)
    m = np.zeros((size, size))
    for _ in range(count):
        x, y = r.random(2) * size
        a = r.random() * np.pi
        L = r.random() * length + 6
        for s in np.linspace(0, L, int(L * 2)):
            m[int(y + s * np.sin(a)) % size, int(x + s * np.cos(a)) % size] = 1
    return blur(m, 1).clip(0, 1) * 2


def edge_wear(height, amount=0.5, noise=None):
    """Mask that is high on panel edges (where height changes) modulated by noise: chipped paint."""
    gy = np.roll(height, -1, 0) - np.roll(height, 1, 0)
    gx = np.roll(height, -1, 1) - np.roll(height, 1, 1)
    e = np.clip(np.sqrt(gx ** 2 + gy ** 2) * 4, 0, 1)
    e = blur(e, 2)
    if noise is not None:
        e = e * (noise > (1 - amount))
    return e.clip(0, 1)


def normal_from_height(height, strength=4.0):
    """Tangent-space normal map (OpenGL, +Y up) from a 0..1 height map; returns (S,S,3) in 0..1."""
    gx = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) * 0.5 * strength
    gy = (np.roll(height, -1, 0) - np.roll(height, 1, 0)) * 0.5 * strength
    n = np.stack([-gx, gy, np.ones_like(height)], -1)  # image rows go down, so +gy flips to +Y up
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    return n * 0.5 + 0.5


def colorize(mask, c0, c1):
    """Lerp between two hex/RGB colours by mask -> (S,S,3)."""
    a, b = np.array(hex_rgb(c0)), np.array(hex_rgb(c1))
    return a + (b - a) * mask[..., None]


def hex_rgb(c):
    if isinstance(c, str):
        h = c.lstrip("#")
        return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return tuple(c)


def shade(color, height, ao=0.35, highlight=0.12):
    """Bake a little cavity darkening and top-edge highlight into a colour map (RA3 look)."""
    cav = blur(height, 3) - height  # >0 in grooves
    lit = (np.roll(height, 2, 0) - height)  # light from the top
    k = 1 - ao * np.clip(cav * 6, 0, 1) + highlight * np.clip(-lit * 3, -1, 1)
    return np.clip(color * k[..., None], 0, 1)


def save_set(out_dir, name, dif, nrm, spm):
    """Write <name>_dif/_nrm/_spm.png (inputs float 0..1)."""
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    paths = []
    for suf, arr in (("dif", dif), ("nrm", nrm), ("spm", spm)):
        a = np.clip(np.asarray(arr) * 255 + 0.5, 0, 255).astype(np.uint8)
        p = out / f"{name}_{suf}.png"
        Image.fromarray(a, "RGB" if a.ndim == 3 else "L").save(p, optimize=True)
        paths.append(p)
    return paths


def spm(specular, glow=None, house=None):
    """Pack the RA3-style mask map from 0..1 arrays (missing channels = 0)."""
    z = np.zeros_like(specular)
    return np.stack([specular, z if glow is None else glow, z if house is None else house], -1)


def contact_sheet(out_png, texture_dir, names, tile=192):
    """Preview: each set as dif | nrm | spm, and dif tiled 2x2, one row per set."""
    rows = []
    for n in names:
        ims = [Image.open(Path(texture_dir) / f"{n}_{s}.png").convert("RGB").resize((tile, tile)) for s in ("dif", "nrm", "spm")]
        d = Image.open(Path(texture_dir) / f"{n}_dif.png").convert("RGB").resize((tile // 2, tile // 2))
        t = Image.new("RGB", (tile, tile))
        for i in range(2):
            for j in range(2):
                t.paste(d, (i * tile // 2, j * tile // 2))
        row = Image.new("RGB", (tile * 4 + 12, tile), (20, 22, 26))
        for i, im in enumerate(ims + [t]):
            row.paste(im, (i * (tile + 4), 0))
        rows.append(row)
    sheet = Image.new("RGB", (rows[0].width, len(rows) * (tile + 4)), (20, 22, 26))
    for i, r in enumerate(rows):
        sheet.paste(r, (0, i * (tile + 4)))
    sheet.save(out_png)
    return out_png
