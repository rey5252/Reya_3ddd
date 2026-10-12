"""Soviet texture set for Fortress Protocol (Red Alert 3 Soviet look).

Heavy riveted olive steel, chipped red paint, worn hazard stripes, copper coil
windings, rough cast concrete, burlap sandbags, a riveted steel deck, gunmetal
and brass. Stylised and painterly-clean: bold rivets and seams, baked top light,
grime only in cavities and on edges.

Run:  python3 -I models/textures/gen_soviet.py
Out:  models/textures/soviet/<name>_{dif,nrm,spm}.png  (512x512, seamless)
      models/textures/soviet/_sheet.png                (contact sheet)

_spm: R = specular, G = self-illumination (unused here), B = house-colour mask
(red paint of paint_red, red stripe markings of plate_steel).
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))  # models/ -> texkit

import numpy as np  # noqa: E402

import texkit as tk  # noqa: E402

S = tk.SIZE
OUT = HERE / "soviet"
NAMES = ["steel_olive", "paint_red", "hazard", "copper_coil", "concrete", "sandbag",
         "plate_steel", "dark_metal", "brass"]

# Soviet palette (SPEC.md)
RED = "#c62b1f"
OLIVE = "#5b5f4a"
RUST = "#8a4b2a"

# light from the top (image up = +Y in the OpenGL normal map), a bit from the left
LIGHT = np.array([-0.35, 0.65, 0.68])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

YY, XX = np.mgrid[0:S, 0:S]


# --------------------------------------------------------------------------- helpers
def c(hexcol):
    return np.array(tk.hex_rgb(hexcol), float)


def lerp(a, b, t):
    """Blend colour/array a -> b by mask t (S,S)."""
    a = c(a) if isinstance(a, str) else np.asarray(a, float)
    b = c(b) if isinstance(b, str) else np.asarray(b, float)
    t = np.asarray(t, float)
    if t.ndim == 2:
        t = t[..., None]
    return a + (b - a) * t


def ramp(lo, hi, t):
    return lerp(lo, hi, np.clip(t, 0, 1))


def sstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def vnoise(cx, cy, seed=0):
    """Tileable value noise with separate lattice counts along x (cx) and y (cy)."""
    g = tk.rng(seed).random((cy, cx))

    def axis(n):
        t = np.arange(S) * n / S
        i0 = np.floor(t).astype(int)
        f = t - i0
        return i0, (i0 + 1) % n, f * f * (3 - 2 * f)

    y0, y1, fy = axis(cy)
    x0, x1, fx = axis(cx)
    fx, fy = fx[None, :], fy[:, None]
    a, b = g[y0][:, x0], g[y0][:, x1]
    cc, d = g[y1][:, x0], g[y1][:, x1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (cc * (1 - fx) + d * fx) * fy


def afbm(cx, cy, octaves=4, seed=0, gain=0.55):
    """Anisotropic fractal noise (streaks), 0..1."""
    out = np.zeros((S, S))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        kx, ky = cx * 2 ** o, cy * 2 ** o
        if kx > S or ky > S:
            break
        out += amp * vnoise(kx, ky, seed + 31 * o)
        tot += amp
        amp *= gain
    out /= tot
    return (out - out.min()) / (np.ptp(out) or 1)


def dilate(m, r=1):
    out = m.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx or dy:
                out = np.maximum(out, np.roll(np.roll(m, dy, 0), dx, 1))
    return out


def light(height, strength):
    """Baked directional light term (0 on flat ground, + facing the light)."""
    n = tk.normal_from_height(height, strength) * 2 - 1
    return n @ LIGHT - LIGHT[2]


def cavity(height, r=4):
    return np.clip(tk.blur(height, r) - height, 0, None)


def panels(rects, gap=3, bevel=7):
    """Bevelled panels from rects (x0,y0,x1,y1); coordinates may run past S and wrap.

    Returns height (1 top, 0 groove), panel id, distance from the panel edge (px)
    and v (0 at the panel top .. 1 at its bottom) for the baked top-lit gradient.
    """
    h = np.zeros((S, S))
    pid = np.zeros((S, S), int)
    dist = np.zeros((S, S))
    v = np.zeros((S, S))
    for k, (x0, y0, x1, y1) in enumerate(rects):
        yy, xx = np.mgrid[y0:y1, x0:x1]
        d = np.minimum.reduce([xx - x0, x1 - 1 - xx, yy - y0, y1 - 1 - yy]).astype(float) - gap
        iy, ix = yy % S, xx % S
        h[iy, ix] = np.clip(d / bevel, 0, 1)
        dist[iy, ix] = d
        pid[iy, ix] = k
        v[iy, ix] = (yy - y0) / (y1 - y0)
    return h, pid, dist, v


def rivet_ring(rect, inset=13, spacing=34):
    x0, y0, x1, y1 = rect
    xa, xb, ya, yb = x0 + inset, x1 - 1 - inset, y0 + inset, y1 - 1 - inset
    nx = max(1, round((xb - xa) / spacing))
    ny = max(1, round((yb - ya) / spacing))
    pts = []
    for i in range(nx + 1):
        x = xa + (xb - xa) * i / nx
        pts += [(x, ya), (x, yb)]
    for j in range(1, ny):
        y = ya + (yb - ya) * j / ny
        pts += [(xa, y), (xb, y)]
    return pts


def domes(pts, r, power=0.5):
    """Wrapping round domes (rivets / pits) at sub-pixel centres, max-combined."""
    h = np.zeros((S, S))
    R = int(np.ceil(r)) + 1
    off = np.arange(-R, R + 1)
    for px, py in pts:
        bx, by = int(round(px)), int(round(py))
        dx = bx + off - px
        dy = by + off - py
        d2 = (dy[:, None] ** 2 + dx[None, :] ** 2) / (r * r)
        val = np.clip(1 - d2, 0, 1) ** power
        ix, iy = (bx + off) % S, (by + off) % S
        sub = np.ix_(iy, ix)
        h[sub] = np.maximum(h[sub], val)
    return h


def chip_mask(dist, width, seed, cells=24, spots=0.0, sharp=9.0, bias=0.62):
    """Ragged paint chips concentrated on panel edges (dist small), plus a few spots."""
    n = tk.fbm(cells=cells, octaves=3, seed=seed)
    e = np.clip(1 - dist / width, 0, 1)
    m = np.clip((e * 1.1 + (n - 0.5) * 1.0 - bias) * sharp, 0, 1)
    if spots:
        n2 = tk.fbm(cells=cells * 2, octaves=3, seed=seed + 7)
        m = np.maximum(m, np.clip((n2 - (1 - spots)) * sharp * 3, 0, 1))
    return m


def drip(mask, length=40, seed=0):
    """Streaks running down from a mask (rust/grime runs)."""
    out = np.zeros((S, S))
    for k in range(0, length, 2):
        out = np.maximum(out, np.roll(mask, k, 0) * (1 - k / length))
    return out * afbm(64, 4, 3, seed) ** 1.5


def cracks(n, steps, seed):
    r = tk.rng(seed)
    m = np.zeros((S, S))
    for _ in range(n):
        x, y = r.random(2) * S
        a = r.random() * 2 * np.pi
        for _s in range(steps):
            a += r.normal(0, 0.35)
            x += np.cos(a) * 1.5
            y += np.sin(a) * 1.5
            m[int(y) % S, int(x) % S] = 1
            if r.random() < 0.01:
                a += r.choice([-1, 1]) * 0.9
    return np.clip(tk.blur(m, 1) * 3, 0, 1)


def hazard_mask(period, wobble=0.0, seed=0):
    """Anti-aliased 45-degree stripes (1 = yellow).

    t = x + y, so any period that divides S tiles seamlessly in both directions.
    (texkit.stripes uses x*cos+y*sin, which is not seamless at 45 deg.)
    """
    t = (XX + YY).astype(float)
    if wobble:
        t = t + (tk.fbm(cells=16, octaves=3, seed=seed) - 0.5) * wobble
    g = ((t / period - 0.25 + 0.5) % 1.0) - 0.5      # -0.5..0.5, 0 = stripe centre
    return np.clip((0.25 - np.abs(g)) * period / np.sqrt(2) + 0.5, 0, 1)


def finish(name, dif, height, nrm_strength, spec, house=None):
    nrm = tk.normal_from_height(height, nrm_strength)
    spm = tk.spm(np.clip(spec, 0, 1), None, None if house is None else np.clip(house, 0, 1))
    return tk.save_set(OUT, name, np.clip(dif, 0, 1), nrm, spm)


# --------------------------------------------------------------------------- shared plated steel
def plated(rects, seed, paint, under, *, gap=3, bevel=7, rivet_inset=13, rivet_spacing=34,
           rivet_r=5.2, chip_w=10.0, chip_spots=0.04, grime="#24251b", light_k=0.95,
           n_scratch=50, overlay=None, primer=None, mottle=0.35, nrm_strength=4.0,
           paint_spec=0.28, under_spec=0.72, extra_pts=()):
    """Riveted, bevelled steel panels with paint chipped at the edges.

    overlay(ctx) -> (S,S,3) colour replacing the paint base (markings etc.).
    primer: optional (lo, hi) colour layer revealed by chips before bare steel.
    """
    r = tk.rng(seed)
    h, pid, dist, v = panels(rects, gap, bevel)
    pts = list(extra_pts)
    if rivet_spacing:
        for rc in rects:
            pts += rivet_ring(rc, rivet_inset, rivet_spacing)
    riv = domes(pts, rivet_r) if pts else np.zeros((S, S))

    lo_n = tk.fbm(cells=4, octaves=4, seed=seed + 1)
    mid_n = tk.fbm(cells=12, octaves=4, seed=seed + 2)
    tone = r.uniform(-0.05, 0.05, len(rects))[pid]

    base = ramp(paint[0], paint[1], lo_n * (1 - mottle) + mid_n * mottle)
    ctx = dict(h=h, pid=pid, dist=dist, v=v, riv=riv, lo=lo_n, mid=mid_n)
    if overlay is not None:
        base = overlay(base, ctx)
    base = base * (1 + tone)[..., None] * (1.08 - 0.16 * v)[..., None]

    chip = chip_mask(dist, chip_w, seed + 3, spots=chip_spots)
    chip = np.maximum(chip, np.clip((riv - 0.55) * 4, 0, 1) * (mid_n > 0.55))  # worn rivet tops
    chip *= sstep(0.0, 0.25, h + riv)                                          # grooves stay painted
    scr = np.clip(tk.scratches(count=n_scratch, length=46, seed=seed + 4), 0, 1) if n_scratch else np.zeros((S, S))

    bare = ramp(under[0], under[1], mid_n)
    col = lerp(base, bare, chip)
    deep = chip
    if primer is not None:
        inner = chip_mask(dist, chip_w * 0.55, seed + 5, spots=chip_spots * 0.4) * chip
        prim = ramp(primer[0], primer[1], mid_n)
        col = lerp(lerp(base, prim, chip), bare, inner)
        deep = inner
    rim = np.clip(dilate(chip, 1) - chip, 0, 1)
    col = lerp(col, "#1e1f18", rim * 0.55)
    col = lerp(col, bare * 1.05, scr * (1 - chip) * 0.45)

    height = h * 0.9 + riv * 0.5 + (1 - chip) * 0.05 - scr * 0.02 + mid_n * 0.015
    lam = light(height, nrm_strength)
    cav = cavity(height, 4)
    gm = np.clip(cav * 7, 0, 1)
    col = col * np.clip(1 + light_k * lam, 0.35, 1.6)[..., None]
    col = col + 0.18 * np.clip(lam, 0, None)[..., None] * (0.5 + deep)[..., None]   # bright worn bevel edges
    rshadow = np.clip(tk.blur(np.roll(riv, 3, 0), 1) - riv, 0, 1)
    col = col * (1 - 0.45 * rshadow)[..., None]
    dirt = np.clip(v ** 3 * 1.3 - 0.2, 0, 1) * tk.fbm(cells=8, octaves=4, seed=seed + 6)
    runs = drip((riv > 0.3).astype(float), 34, seed + 7)
    col = lerp(col, grime, np.clip(gm * 0.8 + dirt * 0.35, 0, 0.85))
    col = lerp(col, RUST, runs * 0.3)

    spec = paint_spec + 0.08 * mid_n
    spec = spec + (under_spec - spec) * deep + 0.15 * riv
    spec = spec * (1 - 0.7 * np.clip(gm + dirt * 0.4, 0, 1)) + 0.25 * scr * (1 - chip)
    return dict(col=col, height=height, spec=spec, chip=chip, deep=deep, nrm=nrm_strength, **ctx)


# --------------------------------------------------------------------------- textures
def steel_olive():
    rects = [(0, 0, 256, 176), (256, 0, 512, 176),
             (128, 176, 384, 304), (384, 176, 640, 304),
             (0, 304, 176, 512), (176, 304, 512, 512)]
    t = plated(rects, 100, ("#4c513c", "#6a6f56"), ("#8f8e80", "#b9b7a7"))
    finish("steel_olive", t["col"], t["height"], t["nrm"], t["spec"])


def paint_red():
    rects = [(0, 0, 256, 256), (256, 0, 512, 256),
             (0, 256, 160, 512), (160, 256, 352, 512), (352, 256, 512, 512)]
    t = plated(rects, 200, ("#a8231a", "#d63a26"), ("#8f8e80", "#b9b7a7"),
               primer=("#4c513c", "#666b52"), chip_w=12, chip_spots=0.05,
               grime="#2a1610", paint_spec=0.34)
    finish("paint_red", t["col"], t["height"], t["nrm"], t["spec"], house=1 - t["chip"])


def hazard():
    seed = 300
    stripe = hazard_mask(128, wobble=3.0, seed=seed)
    lo = tk.fbm(cells=4, octaves=4, seed=seed + 1)
    mid = tk.fbm(cells=12, octaves=4, seed=seed + 2)
    yel = ramp("#d9a514", "#f2c53a", lo * 0.6 + mid * 0.4)
    blk = ramp("#17171a", "#2c2b28", mid)
    col = lerp(blk, yel, stripe)
    # chips: ragged worn patches, more on the yellow, plus scratches
    n = tk.fbm(cells=20, octaves=4, seed=seed + 3)
    wear = np.clip(lo * 0.9 + 0.1, 0, 1)
    chip = np.clip((n * wear - 0.52) * 10, 0, 1)
    scr = np.clip(tk.scratches(count=90, length=50, seed=seed + 4), 0, 1)
    steel = ramp("#77766c", "#a9a798", mid)
    under = lerp(steel, RUST, np.clip((tk.fbm(cells=10, octaves=3, seed=seed + 5) - 0.55) * 3, 0, 1))
    col = lerp(col, under, chip)
    rim = np.clip(dilate(chip, 1) - chip, 0, 1)
    col = lerp(col, "#141412", rim * 0.5)
    col = lerp(col, steel, scr * (1 - chip) * 0.5)
    height = (1 - chip) * 0.25 - scr * 0.08 + mid * 0.05 + stripe * 0.03
    lam = light(height, 3.0)
    col = col * np.clip(1 + 0.9 * lam, 0.4, 1.5)[..., None]
    grime = np.clip((tk.fbm(cells=6, octaves=5, seed=seed + 6) - 0.45) * 1.6, 0, 1)
    col = lerp(col, "#2b2416", grime * 0.4 + cavity(height, 3).clip(0, 1) * 3)
    spec = 0.3 * stripe + 0.22 * (1 - stripe) + 0.4 * chip + 0.2 * scr
    spec *= 1 - 0.5 * grime
    finish("hazard", col, height, 3.0, spec)


def copper_coil():
    seed = 400
    P = 16                                   # one winding = 16 px = 6 cm; 32 windings per tile
    q = YY + XX * (P / S)                    # slight helix: shifts one winding across the tile
    ph = (q % P) / P
    idx = np.floor(q / P).astype(int) % (S // P)
    cs = 2 * ph - 1                          # -1 top .. +1 bottom of the wire
    wire = np.sqrt(np.clip(1 - (cs / 0.93) ** 2, 0, 1))
    r = tk.rng(seed)
    tone = r.uniform(-0.07, 0.07, S // P)[idx]
    lo = tk.fbm(cells=4, octaves=4, seed=seed + 1)
    streak = afbm(16, 64, 3, seed + 2)
    base = ramp("#9a4a1f", "#c8703a", lo * 0.6 + streak * 0.4)
    base = base * (1 + tone)[..., None]
    lam = light(wire, 3.0)
    col = base * np.clip(1 + 1.1 * lam, 0.25, 1.7)[..., None]
    hi = np.exp(-((cs + 0.42) / 0.16) ** 2)  # specular band on the top of each wire
    col = lerp(col, "#ffd6a0", hi * (0.55 + 0.3 * streak))
    gap = 1 - sstep(0.0, 0.45, wire)
    col = lerp(col, "#2a1208", gap * 0.9)
    # dark varnish / oxidation blotches and verdigris in the gaps
    ox = np.clip((tk.fbm(cells=8, octaves=4, seed=seed + 3) - 0.55) * 2.5, 0, 1)
    col = lerp(col, "#5a2a14", ox * 0.5)
    ver = np.clip((tk.fbm(cells=16, octaves=3, seed=seed + 4) - 0.62) * 4, 0, 1) * gap
    col = lerp(col, "#3f8f6e", ver * 0.7)
    height = wire * 0.9 + streak * 0.03
    spec = (0.55 + 0.35 * wire) * (1 - 0.6 * ox) * (1 - 0.8 * gap)
    finish("copper_coil", col, height, 3.0, spec)


def concrete():
    seed = 500
    rects = [(0, 0, 256, 256), (256, 0, 512, 256), (128, 256, 384, 512), (384, 256, 640, 512)]
    h, pid, dist, v = panels(rects, gap=2, bevel=4)
    r = tk.rng(seed)
    # form-tie holes and small air pits
    ties = [(x0 + dx, y0 + dy) for (x0, y0, x1, y1) in rects for dx in (64, 192) for dy in (64, 192)]
    tie = domes(ties, 5.0, 0.7)
    pits = np.zeros((S, S))
    pr = r.uniform(1.2, 3.2, 320)
    pp = r.random((320, 2)) * S
    for k in range(0, 320, 40):
        pits = np.maximum(pits, domes([tuple(p) for p in pp[k:k + 40]], pr[k], 0.8))
    crack = cracks(9, 120, seed + 1)
    lo = tk.fbm(cells=4, octaves=5, seed=seed + 2)
    mid = tk.fbm(cells=16, octaves=4, seed=seed + 3)
    agg = np.clip((tk.value_noise(cells=128, seed=seed + 4) - 0.72) * 5, 0, 1)
    # corner/edge chips on slabs
    chip = chip_mask(dist, 6, seed + 5, cells=20, bias=0.75)
    tone = r.uniform(-0.04, 0.04, len(rects))[pid]
    col = ramp("#7a776e", "#a6a297", lo * 0.65 + mid * 0.35)
    col = col * (1 + tone)[..., None] * (1.05 - 0.1 * v)[..., None]
    col = lerp(col, "#bcb8ac", agg * 0.35)
    col = lerp(col, "#5d5a52", chip * 0.6)
    height = h * 0.7 - tie * 0.5 - pits * 0.25 - crack * 0.12 - chip * 0.25 + mid * 0.12 + agg * 0.03
    lam = light(height, 3.0)
    col = col * np.clip(1 + 0.8 * lam, 0.4, 1.5)[..., None]
    cav = np.clip(cavity(height, 3) * 6, 0, 1)
    runs = drip((h < 0.3).astype(float) * (np.abs(np.roll(h, 1, 0) - h) > 0) * 1.0 + tie * 0.6, 60, seed + 6)
    stain = np.clip((tk.fbm(cells=6, octaves=4, seed=seed + 7) - 0.5) * 2, 0, 1)
    col = lerp(col, "#3a372f", np.clip(cav * 0.8 + crack * 0.7 + tie * 0.7 + pits * 0.6, 0, 0.9))
    col = lerp(col, "#4a463b", np.clip(runs * 0.35 + stain * 0.25, 0, 0.6))
    spec = 0.1 + 0.06 * mid - 0.06 * cav
    finish("concrete", col, height, 3.0, spec)


def sandbag():
    seed = 600
    r = tk.rng(seed)
    rows, cols = 8, 4
    bh, bw = S // rows, S // cols                     # 64 x 128 px = 25 x 50 cm bags
    height = np.zeros((S, S))
    bid = np.zeros((S, S), int)
    ux = np.zeros((S, S))                             # -1..1 along the bag
    uy = np.zeros((S, S))
    seam_side = np.zeros((S, S))
    k = 0
    for j in range(rows):
        off = (bw // 2) * (j % 2) + r.integers(-10, 10)
        for i in range(cols):
            cx = i * bw + off + bw / 2 + r.uniform(-4, 4)
            cy = j * bh + bh / 2 + r.uniform(-3, 3)
            ax = bw / 2 + r.uniform(2, 8)
            ay = bh / 2 + r.uniform(1, 5)
            x0, x1 = int(cx - ax - 2), int(cx + ax + 3)
            y0, y1 = int(cy - ay - 2), int(cy + ay + 3)
            yy, xx = np.mgrid[y0:y1, x0:x1]
            dx = (xx - cx) / ax
            dy = (yy - cy) / ay
            ayk = 1 - 0.38 * np.abs(dx) ** 4                     # tied, pinched ends
            e = np.abs(dx) ** 3.0 + (dy / ayk) ** 2
            hh = np.clip(1 - e, 0, 1) ** 0.45
            hh *= 1 + 0.06 * dy                                  # sagging: fuller at the bottom
            iy, ix = yy % S, xx % S
            sel = hh > height[iy, ix]
            height[iy[sel], ix[sel]] = hh[sel]
            bid[iy[sel], ix[sel]] = k
            ux[iy[sel], ix[sel]] = dx[sel]
            uy[iy[sel], ix[sel]] = dy[sel]
            seam_side[iy[sel], ix[sel]] = 1 if r.random() < 0.5 else -1
            k += 1
    inside = sstep(0.0, 0.15, height)
    # creases near the tied ends + soft lumps of sand
    crease = np.sin(uy * 9 + tk.fbm(cells=16, octaves=3, seed=seed + 1) * 6) * np.abs(ux) ** 3
    lumps = tk.fbm(cells=16, octaves=3, seed=seed + 2)
    # burlap weave (period 4 px), basket pattern
    chk = ((XX // 4 + YY // 4) % 2).astype(float)
    wx = 0.5 + 0.5 * np.cos(2 * np.pi * XX / 4)
    wy = 0.5 + 0.5 * np.cos(2 * np.pi * YY / 4)
    weave = chk * wx + (1 - chk) * wy
    # stitched seam across one end of each bag (dashes along y)
    sx = np.abs(ux - 0.72 * seam_side)
    stitch_line = (sx < 0.025) * (np.abs(uy) < 0.75)
    dash = ((YY // 3) % 2 == 0).astype(float)
    stitch = stitch_line * dash * inside
    seam_groove = np.clip(1 - sx / 0.04, 0, 1) * (np.abs(uy) < 0.8) * inside

    hgt = height * 1.0 + crease * 0.05 * inside + lumps * 0.05 * inside + weave * 0.025 * inside \
        - seam_groove * 0.04 + stitch * 0.03
    tone = r.uniform(-0.08, 0.08, k)[bid]
    hue = r.uniform(0, 1, k)[bid]
    lo = tk.fbm(cells=4, octaves=4, seed=seed + 3)
    col = ramp("#b39a63", "#d4bd85", lo * 0.6 + lumps * 0.4)
    col = lerp(col, "#a39a6a", (hue > 0.7) * 0.5)              # a few greener / older bags
    col = col * (1 + tone)[..., None] * (1 + 0.06 * (weave - 0.5) * inside)[..., None]
    lam = light(hgt, 5.0)
    col = col * np.clip(1 + 0.9 * lam, 0.3, 1.5)[..., None]
    col = col * (1.06 - 0.12 * np.clip(uy, -1, 1) * 0.5)[..., None]
    col = lerp(col, "#efe0b0", stitch * 0.8)
    col = lerp(col, "#5a4628", seam_groove * 0.4)
    dirt = np.clip(uy, 0, 1) ** 2 * tk.fbm(cells=8, octaves=4, seed=seed + 4)
    col = lerp(col, "#5b4a2c", dirt * 0.5)
    col = lerp(col, "#241c10", 1 - inside * 0.92 - 0.08 * sstep(0.0, 0.05, height))
    spec = 0.06 + 0.06 * lumps * inside + 0.1 * stitch
    finish("sandbag", col, hgt, 5.0, spec)


def plate_steel():
    rects = [(0, 0, 256, 256), (256, 0, 512, 256), (0, 256, 256, 512), (256, 256, 512, 512)]
    edge = np.minimum.reduce([XX, S - 1 - XX, YY, S - 1 - YY]).astype(float) + 0.5
    BAND, RED_W = 22, 7
    haz = hazard_mask(32, wobble=1.5, seed=711)
    band = np.clip(BAND - edge + 0.5, 0, 1)                         # 1 inside the hazard frame
    redl = np.clip(BAND + 4 + RED_W - edge + 0.5, 0, 1) * np.clip(edge - (BAND + 4) + 0.5, 0, 1)
    corner = (np.minimum(XX, S - 1 - XX) < 70) & (np.minimum(YY, S - 1 - YY) < 70)
    redl = redl * corner                                            # red corner brackets

    def overlay(base, ctx):
        mid = ctx["mid"]
        hz = lerp(ramp("#1b1b1c", "#2d2c29", mid), ramp("#d7a316", "#f0c43a", mid), haz)
        out = lerp(base, hz, band)
        return lerp(out, ramp("#b0261b", "#d43727", mid), redl)

    ex = [(x, y) for x in (128, 384) for y in (128, 384)]               # centre bolts
    t = plated(rects, 700, ("#6c6e68", "#83857d"), ("#a3a397", "#c4c3b6"), overlay=overlay,
               rivet_inset=12, rivet_spacing=32, rivet_r=5.8, chip_w=11, chip_spots=0.05,
               n_scratch=110, grime="#23231e", paint_spec=0.34, extra_pts=ex)
    house = redl * (1 - t["chip"])
    finish("plate_steel", t["col"], t["height"], t["nrm"], t["spec"], house=house)


def dark_metal():
    rects = [(0, 0, 320, 192), (320, 0, 512, 192), (96, 192, 352, 512), (352, 192, 608, 512)]
    brushed = afbm(4, 96, 4, 801)
    t = plated(rects, 800, ("#2c2f2c", "#3d403b"), ("#7f8180", "#a2a4a2"), rivet_spacing=0,
               extra_pts=[(x0 + dx, y0 + 14) for (x0, y0, x1, y1) in rects for dx in (16, x1 - x0 - 17)]
               + [(x0 + dx, y1 - 15) for (x0, y0, x1, y1) in rects for dx in (16, x1 - x0 - 17)],
               rivet_r=6.0, chip_w=7, chip_spots=0.015, n_scratch=70, grime="#121311",
               paint_spec=0.48, under_spec=0.8, light_k=1.1, mottle=0.5)
    col = t["col"] * (0.93 + 0.14 * brushed)[..., None]
    spec = t["spec"] * (0.85 + 0.3 * brushed)
    finish("dark_metal", col, t["height"] + brushed * 0.02, t["nrm"], spec)


def brass():
    seed = 900
    brushed = afbm(4, 128, 4, seed)
    lo = tk.fbm(cells=4, octaves=4, seed=seed + 1)
    # turned bands: two raised rings per tile with small grooves
    v = (YY % 256) / 256.0
    ring = sstep(0.02, 0.05, v) * (1 - sstep(0.95, 0.98, v))
    groove = np.exp(-((v - 0.5) / 0.006) ** 2) * 0.6
    dents = domes([tuple(p) for p in tk.rng(seed).random((26, 2)) * S], 6.0, 1.0)
    height = ring * 0.6 - groove * 0.3 - dents * 0.15 + brushed * 0.04
    col = ramp("#a77f2c", "#d8b257", lo * 0.55 + brushed * 0.45)
    lam = light(height, 4.0)
    col = col * np.clip(1 + 1.1 * lam, 0.3, 1.7)[..., None]
    col = lerp(col, "#fff0b8", np.clip(lam, 0, 1) * 0.6)
    col = col * (1.06 - 0.12 * ((YY % 256) / 256.0))[..., None]
    tarn = np.clip((tk.fbm(cells=6, octaves=5, seed=seed + 2) - 0.55) * 2.2, 0, 1)
    col = lerp(col, "#6c5a2a", tarn * 0.55)
    cav = np.clip(cavity(height, 3) * 8, 0, 1)
    col = lerp(col, "#3a2c10", cav * 0.7)
    scr = np.clip(tk.scratches(count=50, length=40, seed=seed + 3), 0, 1)
    col = lerp(col, "#f6dc90", scr * 0.35)
    spec = (0.82 + 0.12 * brushed) * (1 - 0.55 * tarn) * (1 - 0.6 * cav)
    finish("brass", col, height, 4.0, spec)


def main():
    for fn in (steel_olive, paint_red, hazard, copper_coil, concrete, sandbag,
               plate_steel, dark_metal, brass):
        fn()
        print("wrote", fn.__name__)
    tk.contact_sheet(OUT / "_sheet.png", OUT, NAMES)
    total = sum(p.stat().st_size for p in OUT.glob("*_*.png") if not p.name.startswith("_"))
    print(f"{len(NAMES) * 3} maps, {total / 1e6:.2f} MB -> {OUT}")


if __name__ == "__main__":
    main()
