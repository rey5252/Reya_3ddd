"""Allied texture set for Fortress Protocol (Red Alert 3 Allied look).

Clean white/light-grey high-tech armour, steel-blue accents, polished chrome,
cyan energy, gunmetal joints and a light steel/concrete foundation slab.
Stylised and painterly-clean: crisp bevelled panel lines, baked top light,
grime only in seams and on edges.

Run:  python3 -I models/textures/gen_allied.py
Out:  models/textures/allied/<name>_{dif,nrm,spm}.png  (512x512, seamless)
      models/textures/allied/_sheet.png                (contact sheet)
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))  # models/ -> texkit

import numpy as np  # noqa: E402

import texkit as tk  # noqa: E402

S = tk.SIZE
OUT = HERE / "allied"
NAMES = ["panel_white", "panel_blue", "chrome", "energy_cyan", "plate_steel", "dark_metal"]

# Allied palette (SPEC.md)
BLUE = "#2f6fd6"
WHITE = "#eef3fb"
CHROME = "#9fb3c8"
CYAN = "#7fe3ff"

# light from the top (image up = +Y in the OpenGL normal map), a bit from the left
LIGHT = np.array([-0.30, 0.70, 0.65])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


# --------------------------------------------------------------------------- helpers
def c(hexcol):
    return np.array(tk.hex_rgb(hexcol))


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def wrap_d(a, b):
    """Shortest signed distance on the tiling torus."""
    d = (a - b + S / 2) % S - S / 2
    return d


YY, XX = np.mgrid[0:S, 0:S].astype(float)


def split_rects(rect, depth, r, min_size=96, aspect_bias=0.5):
    """Recursive axis-aligned subdivision of rect=(x0,y0,x1,y1) into panels."""
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    if depth <= 0 or (w < 2 * min_size and h < 2 * min_size):
        return [rect]
    vertical = (w > h) if r.random() > 0.2 else (r.random() < aspect_bias)
    if vertical and w < 2 * min_size:
        vertical = False
    if not vertical and h < 2 * min_size:
        vertical = True
    if vertical:
        cut = x0 + int(round((w * r.uniform(0.33, 0.67)) / 16) * 16)
        cut = int(np.clip(cut, x0 + min_size, x1 - min_size))
        a, b = (x0, y0, cut, y1), (cut, y0, x1, y1)
    else:
        cut = y0 + int(round((h * r.uniform(0.33, 0.67)) / 16) * 16)
        cut = int(np.clip(cut, y0 + min_size, y1 - min_size))
        a, b = (x0, y0, x1, cut), (x0, cut, x1, y1)
    return split_rects(a, depth - 1, r, min_size) + split_rects(b, depth - 1, r, min_size)


def panels_height(rects, gap=3.0, bevel=7.0):
    """Height of raised bevelled panels; the rects tile the square, so seams wrap.

    Returns (height 0..1, panel id, inner distance to the panel border in px)."""
    h = np.zeros((S, S))
    pid = np.zeros((S, S), int)
    dist = np.zeros((S, S))
    for k, (x0, y0, x1, y1) in enumerate(rects):
        sl = (slice(y0, y1), slice(x0, x1))
        yy, xx = YY[sl], XX[sl]
        d = np.minimum.reduce([xx - x0 + 0.5, x1 - 0.5 - xx, yy - y0 + 0.5, y1 - 0.5 - yy])
        h[sl] = smooth((d - gap) / bevel)
        pid[sl] = k
        dist[sl] = d
    return h, pid, dist


def rect_mask(x0, y0, x1, y1, soft=1.0, radius=0.0):
    """Anti-aliased (rounded) rectangle mask, wrapping around the tile."""
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hw, hh = (x1 - x0) / 2, (y1 - y0) / 2
    dx = np.abs(wrap_d(XX, cx)) - hw + radius
    dy = np.abs(wrap_d(YY, cy)) - hh + radius
    out = np.sqrt(np.maximum(dx, 0) ** 2 + np.maximum(dy, 0) ** 2) + np.minimum(np.maximum(dx, dy), 0) - radius
    return np.clip(0.5 - out / soft, 0, 1)


def bolt(cx, cy, r=4.0):
    """Domed bolt height (0..1) at a point, wrapping."""
    d = np.sqrt(wrap_d(XX, cx) ** 2 + wrap_d(YY, cy) ** 2)
    return np.clip(1 - (d / r) ** 2, 0, 1) ** 0.5


def bolt_ring(cx, cy, r=4.0):
    d = np.sqrt(wrap_d(XX, cx) ** 2 + wrap_d(YY, cy) ** 2)
    return np.clip(1 - np.abs(d - r - 1.2) / 1.2, 0, 1)


def light_bake(color, height, strength, amount=0.55, ao=0.30, hi=0.10):
    """RA3 look: baked top light on bevels + cavity darkening in the grooves."""
    n = tk.normal_from_height(height, strength) * 2 - 1
    lam = (n @ LIGHT) - LIGHT[2]  # 0 on flat faces, >0 facing light, <0 away
    k = 1 + amount * np.clip(lam, -1, 1)
    col = tk.shade(color, height, ao=ao, highlight=hi)
    return np.clip(col * k[..., None], 0, 1), n * 0.5 + 0.5


def lerp(a, b, t):
    t = np.asarray(t)
    if t.ndim == 2:
        t = t[..., None]
    return a + (b - a) * t


def per_panel(pid, seed, lo=-1.0, hi=1.0):
    r = tk.rng(seed)
    vals = r.uniform(lo, hi, pid.max() + 1)
    return vals[pid]


def groove_mask(h):
    return 1 - smooth(h * 3)


def seam_core(h):
    """Only the bottom of the groove (for a crisp dark panel line)."""
    return 1 - smooth(h * 8)


def grime(h, seed, amount=0.5, spread=4):
    """Dirt that collects in grooves and along panel edges, broken up by noise."""
    g = tk.blur(groove_mask(h), spread)
    n = tk.fbm(S, 8, 4, seed)
    return np.clip(g * (0.4 + 0.9 * n) * amount, 0, 1)


def wear(h, seed, amount=0.35):
    """Chipped edges: thin, broken highlights on panel rims."""
    n = tk.fbm(S, 16, 4, seed)
    e = tk.edge_wear(h, amount=1.0)
    return np.clip(e * smooth((n - (1 - amount)) * 6), 0, 1)


def scratch_mask(seed, count=60, length=30):
    return np.clip(tk.scratches(S, count, length, seed), 0, 1)  # texkit returns 0..2


def standard_layout(seed, depth=3, min_size=96, gap=3.0, bevel=7.0, shift=(0, 0)):
    r = tk.rng(seed)
    rects = split_rects((0, 0, S, S), depth, r, min_size)
    h, pid, dist = panels_height(rects, gap, bevel)
    h, pid, dist = (np.roll(a, shift, (0, 1)) for a in (h, pid, dist))
    rects = [((x0 + shift[1]) % S, (y0 + shift[0]) % S, (x1 - x0), (y1 - y0)) for x0, y0, x1, y1 in rects]
    return h, pid, dist, rects  # rects now as (x, y, w, h) with wrapped origin


def corner_bolts(rects, inset=10, r=3.5, every=1):
    h = np.zeros((S, S))
    ring = np.zeros((S, S))
    for k, (x, y, w, hh) in enumerate(rects):
        if k % every:
            continue
        for bx, by in ((x + inset, y + inset), (x + w - inset, y + inset),
                       (x + inset, y + hh - inset), (x + w - inset, y + hh - inset)):
            h = np.maximum(h, bolt(bx, by, r))
            ring = np.maximum(ring, bolt_ring(bx, by, r))
    return h, ring


def vents(x, y, w, h, slot=6, pitch=12):
    """Horizontal louvre slots inside a box: returns depression mask (1 = slot)."""
    m = np.zeros((S, S))
    n = max(1, int((h - pitch / 2) // pitch))
    for i in range(n):
        yy = y + pitch / 2 + i * pitch
        m = np.maximum(m, rect_mask(x, yy, x + w, yy + slot, soft=1.0, radius=slot / 2))
    return m


def hex_cells(cols=8, rows=10, seed=0):
    """Seamless hexagonal cell field: (edge distance in px, cell id, centre offset).

    Uses an (almost) regular hex lattice squeezed to fit `rows` rows in the tile."""
    w = S / cols
    rh = S / rows
    sy = (1.5 * w / np.sqrt(3)) / rh  # stretch y so the metric is a regular hex lattice
    pts = [((i + 0.5 * (j % 2)) * w, j * rh) for j in range(rows) for i in range(cols)]
    d1 = np.full((S, S), 1e9)
    d2 = np.full((S, S), 1e9)
    cid = np.zeros((S, S), int)
    for k, (px, py) in enumerate(pts):
        d = np.sqrt(wrap_d(XX, px) ** 2 + (wrap_d(YY, py) * sy) ** 2)
        closer = d < d1
        d2 = np.where(closer, d1, np.minimum(d2, d))
        cid = np.where(closer, k, cid)
        d1 = np.where(closer, d, d1)
    edge = (d2 - d1) / 2  # distance to the Voronoi edge
    return edge, cid, d1


# --------------------------------------------------------------------------- textures
def panel_white():
    h, pid, dist, rects = standard_layout(11, depth=3, min_size=96, shift=(40, 72))
    # sub-details: inset service hatch, small vents, label plates
    det = np.zeros((S, S))
    vent = np.zeros((S, S))
    label = np.zeros((S, S))
    r = tk.rng(12)
    for k, (x, y, w, hh) in enumerate(rects):
        roll = r.random()
        if roll < 0.25 and w >= 128 and hh >= 96:
            vx, vy = x + w * 0.2, y + hh * 0.35
            vent = np.maximum(vent, vents(vx, vy, w * 0.35, 48, slot=5, pitch=11))
        elif roll < 0.55:
            label = np.maximum(label, rect_mask(x + 16, y + 16, x + 16 + min(56, w * 0.4), y + 26, radius=2))
        elif roll < 0.75 and w >= 96 and hh >= 96:
            det = np.maximum(det, rect_mask(x + 18, y + 18, x + w - 18, y + hh - 18, soft=2.5, radius=6))
    inset_line = np.clip(1 - np.abs(tk.blur(det, 1) - 0.5) * 4, 0, 1)  # engraved outline of hatch
    bh, bring = corner_bolts(rects, inset=15, r=3.2)
    height = h - 0.25 * inset_line - 0.5 * vent + 0.35 * bh - 0.12 * bring
    height += 0.015 * tk.fbm(S, 16, 3, 13)

    tint = per_panel(pid, 14) * 0.025
    base = lerp(c("#dde5ef"), c(WHITE), 0.65 + tint * 10)
    col = base * (1 + tint)[..., None]
    col = lerp(col, c("#c7d2df"), 0.5 * det * (1 - inset_line))  # hatch slightly greyer
    col = lerp(col, c("#2f6fd6"), label * 0.9)                    # small blue ID plates
    col = lerp(col, c("#1a2433"), vent * 0.85)
    col = lerp(col, c("#5b6878"), groove_mask(h) * 0.85)           # dark seams
    col = lerp(col, c("#2c3542"), seam_core(h) * 0.8)
    col = lerp(col, c("#aab6c4"), bh * 0.6)
    g = grime(h, 15, 0.45)
    col = lerp(col, c("#7d7a70"), g * 0.45)
    col = lerp(col, c("#8b98a8"), wear(h, 16, 0.3) * 0.6)
    col = lerp(col, c("#b8c2ce"), scratch_mask(17, 30, 20) * 0.25)
    col = col * (0.97 + 0.03 * tk.fbm(S, 4, 3, 18))[..., None]
    dif, nrm = light_bake(col, height, 6.0, amount=0.6)

    spec = 0.55 - 0.35 * groove_mask(h) - 0.3 * g - 0.3 * vent + 0.25 * bh
    return dif, nrm, tk.spm(np.clip(spec, 0, 1))


def panel_blue():
    h, pid, dist, rects = standard_layout(21, depth=3, min_size=112, shift=(96, 24))
    # accent stripes: one wide light band + white pinstripe pair, running across the tile
    band = rect_mask(-S, 300, 2 * S, 336, soft=1.2)
    pin = np.maximum(rect_mask(-S, 286, 2 * S, 290, soft=0.8), rect_mask(-S, 346, 2 * S, 350, soft=0.8))
    # small chevron ticks on the band (period 64 -> seamless)
    t = (XX % 64) - 32 + np.abs(YY - 318) * 0.9
    chev = np.clip(1 - np.abs(t) / 4, 0, 1) * rect_mask(-S, 304, 2 * S, 332, soft=1)
    bh, bring = corner_bolts(rects, inset=15, r=3.4)
    r = tk.rng(29)
    stencil = np.zeros((S, S))
    for x, y, w, hh in rects:
        if r.random() < 0.5:
            stencil = np.maximum(stencil, rect_mask(x + w - 20 - min(48, w * 0.35), y + hh - 30, x + w - 20, y + hh - 24, radius=1.5))
    height = h + 0.35 * bh - 0.12 * bring - 0.08 * pin + 0.03 * band
    height += 0.012 * tk.fbm(S, 16, 3, 23)

    tint = per_panel(pid, 24) * 0.04
    col = lerp(c("#2a62c2"), c(BLUE), 0.6) * (1 + tint)[..., None]
    col = col * (0.95 + 0.08 * tk.fbm(S, 4, 3, 25))[..., None]
    col = lerp(col, c("#5d98f2"), band * 0.85)
    col = lerp(col, c("#2f6fd6"), chev * 0.7)
    col = lerp(col, c("#eef3fb"), pin * 0.95)
    col = lerp(col, c("#dbe6f5"), stencil * 0.8)
    col = lerp(col, c("#16294a"), groove_mask(h) * 0.85)
    col = lerp(col, c("#0a1428"), seam_core(h) * 0.8)
    col = lerp(col, c("#9fb3c8"), bh * 0.7)
    g = grime(h, 26, 0.4)
    col = lerp(col, c("#2b3442"), g * 0.4)
    col = lerp(col, c("#c9d6e6"), wear(h, 27, 0.3) * 0.55)  # bare metal under paint
    col = lerp(col, c("#7fa7e6"), scratch_mask(28, 25, 18) * 0.2)
    dif, nrm = light_bake(col, height, 6.0, amount=0.65)

    spec = 0.6 - 0.35 * groove_mask(h) - 0.3 * g + 0.25 * bh + 0.15 * wear(h, 27, 0.3)
    house = np.clip(band * 0.5 + pin * 0.35, 0, 1) * (1 - groove_mask(h))
    return dif, nrm, tk.spm(np.clip(spec, 0, 1), house=house)


def chrome():
    # stylised environment reflection: periodic sky / horizon bands along V
    yv = YY / S
    warp = 0.025 * (tk.fbm(S, 4, 3, 31) - 0.5)
    t = (yv + warp) % 1.0
    sky = 0.5 + 0.5 * np.cos(2 * np.pi * t)                  # bright band at t=0
    horizon = np.exp(-((((t - 0.42) + 0.5) % 1 - 0.5) / 0.05) ** 2)  # sharp dark horizon line
    second = np.exp(-((((t - 0.62) + 0.5) % 1 - 0.5) / 0.09) ** 2)
    glint = np.exp(-((((t - 0.08) + 0.5) % 1 - 0.5) / 0.03) ** 2)  # hot sky reflection
    lum = 0.38 + 0.5 * sky - 0.4 * horizon + 0.12 * second + 0.25 * glint
    # brushed streaks: noise blurred along X only
    r = tk.rng(32)
    n = r.random((S, S))
    for rad in (24, 24, 12):
        acc = np.zeros_like(n)
        for d in range(-rad, rad + 1, 2):
            acc += np.roll(n, d, 1)
        n = acc / len(range(-rad, rad + 1, 2))
    n = (n - n.mean()) / (n.std() + 1e-6)
    n = tk.blur(n, 1)
    lum = lum + 0.03 * n
    # machined ring grooves + one panel seam
    rings = np.zeros((S, S))
    for y0, wdt in ((60, 3), (68, 1.5), (316, 4), (440, 1.5)):
        rings = np.maximum(rings, rect_mask(-S, y0, 2 * S, y0 + wdt, soft=0.9))
    seam = rect_mask(150, -S, 153, 2 * S, soft=0.9) * (1 - rect_mask(-S, 60, 2 * S, 72))
    height = 1 - 0.6 * tk.blur(rings, 1) - 0.4 * tk.blur(seam, 1)
    height += 0.004 * n
    col = lerp(c("#56677c"), c("#f2f8ff"), np.clip(lum, 0, 1))
    col = lerp(col, c(CHROME), 0.18)
    col = col * (1 + 0.04 * (c("#cfe6ff") - 0.8))  # cool blue tint
    g = grime(height, 33, 0.25, spread=2)
    col = lerp(col, c("#4a5562"), g * 0.35)
    col = lerp(col, c("#ffffff"), scratch_mask(34, 30, 20) * 0.25)
    dif, nrm = light_bake(col, height, 5.0, amount=0.5, ao=0.4)
    spec = 0.9 + 0.08 * np.clip(lum - 0.5, -1, 1) - 0.5 * rings - 0.2 * g
    return dif, nrm, tk.spm(np.clip(spec, 0, 1))


def energy_cyan():
    edge, cid, d1 = hex_cells(8, 10, 41)
    r = tk.rng(42)
    cell_lvl = r.uniform(0.55, 1.0, cid.max() + 1)[cid]
    hot = (r.random(cid.max() + 1) < 0.12)[cid].astype(float)  # a few flaring cells
    line = np.clip(1 - (edge - 1.0) / 2.0, 0, 1)                 # bright hex lattice
    halo = np.exp(-edge / 9.0)                                     # glow bleeding from lines
    rim = smooth((edge - 2.5) / 5.0)
    # scanlines (period 8 px) and a slow vertical energy wave (period = tile)
    scan = 0.5 + 0.5 * np.cos(2 * np.pi * YY / 8)
    wave = 0.5 + 0.5 * np.sin(2 * np.pi * (YY / S + 0.15 * tk.fbm(S, 4, 3, 43)))
    flow = tk.fbm(S, 8, 3, 44)
    inten = (0.35 + 0.35 * cell_lvl * (0.6 + 0.4 * flow) + 0.25 * hot) * (0.85 + 0.15 * wave)
    inten = inten * (0.9 + 0.1 * scan)
    inten = np.maximum(inten, 0.6 * halo + 0.25)
    col = lerp(c("#0f4f86"), c("#2cc6f2"), np.clip(inten, 0, 1))
    col = lerp(col, c(CYAN), np.clip(halo * 0.9 + hot * 0.3, 0, 1))
    col = lerp(col, c("#e9fcff"), line)
    col = col * (0.92 + 0.08 * scan)[..., None]
    height = rim * 0.6 + 0.05 * flow
    nrm = tk.normal_from_height(height, 3.0)
    glow = np.clip(0.55 + 0.45 * np.maximum(line, inten * 0.85 + halo * 0.3), 0, 1)
    spec = 0.25 + 0.2 * line
    return np.clip(col, 0, 1), nrm, tk.spm(spec, glow=glow)


def plate_steel():
    # foundation slab: 2x2 big tiles (1 m each), each with an inset border and corner bolts
    rects = [(0, 0, 256, 256), (256, 0, 512, 256), (0, 256, 256, 512), (256, 256, 512, 512)]
    h, pid, dist = panels_height(rects, gap=4.0, bevel=6.0)
    sh = (0, 0)
    inner = np.zeros((S, S))
    for x0, y0, x1, y1 in rects:
        inner = np.maximum(inner, rect_mask(x0 + 22, y0 + 22, x1 - 22, y1 - 22, soft=1.5, radius=4))
    inner_line = np.clip(1 - np.abs(tk.blur(inner, 1) - 0.5) * 3.5, 0, 1)
    # Allied blue lane: band crossing horizontally through the middle of the tile row y 216..240
    lane = rect_mask(-S, 112, 2 * S, 144, soft=1.2)
    lane_pin = np.maximum(rect_mask(-S, 103, 2 * S, 106, soft=0.8), rect_mask(-S, 150, 2 * S, 153, soft=0.8))
    # dashed centre-line markings across the lower tiles (period 128 -> seamless)
    dash = rect_mask(-S, 380, 2 * S, 388, soft=1) * (((XX + 16) % 128) < 64)
    dash = tk.blur(dash.astype(float), 1)
    # corner "L" markers in blue on every tile
    lmark = np.zeros((S, S))
    for x0, y0, x1, y1 in rects:
        for (ax, ay, sx, sy) in ((x0 + 34, y0 + 34, 1, 1), (x1 - 34, y1 - 34, -1, -1)):
            lmark = np.maximum(lmark, rect_mask(min(ax, ax + 28 * sx), ay - 3, max(ax, ax + 28 * sx), ay + 3))
            lmark = np.maximum(lmark, rect_mask(ax - 3, min(ay, ay + 28 * sy), ax + 3, max(ay, ay + 28 * sy)))
    bh = np.zeros((S, S))
    bring = np.zeros((S, S))
    for x0, y0, x1, y1 in rects:
        for bx, by in ((x0 + 11, y0 + 11), (x1 - 11, y0 + 11), (x0 + 11, y1 - 11), (x1 - 11, y1 - 11)):
            bh = np.maximum(bh, bolt(bx, by, 4.5))
            bring = np.maximum(bring, bolt_ring(bx, by, 4.5))
    conc = tk.fbm(S, 8, 5, 51)
    speck = tk.fbm(S, 64, 2, 52)
    height = h - 0.2 * inner_line + 0.35 * bh - 0.12 * bring + 0.02 * conc + 0.01 * speck
    height, pid, inner_line, lane, lane_pin, dash, lmark, bh, conc, speck = (
        np.roll(a, sh, (0, 1)) for a in (height, pid, inner_line, lane, lane_pin, dash, lmark, bh, conc, speck))
    hpan = np.roll(h, sh, (0, 1))

    tint = per_panel(pid, 53) * 0.03
    col = lerp(c("#a9b4c1"), c("#c6cfd9"), 0.55 + 0.45 * conc) * (1 + tint)[..., None]
    col = col * (0.97 + 0.04 * speck)[..., None]
    col = lerp(col, c("#8f9bab"), inner_line * 0.5)
    paint = np.clip(lane + lmark, 0, 1)
    col = lerp(col, c(BLUE), paint * 0.92)
    col = lerp(col, c("#eef3fb"), np.clip(lane_pin + dash, 0, 1) * 0.9)
    col = lerp(col, c("#4b5563"), groove_mask(hpan) * 0.9)
    col = lerp(col, c("#262d38"), seam_core(hpan) * 0.8)
    col = col * (1 - 0.06 * tk.blur(tk.fbm(S, 4, 3, 58), 4))[..., None]  # soft large stains
    col = lerp(col, c("#d5dde6"), bh * 0.6)
    g = grime(hpan, 54, 0.6, spread=6)
    col = lerp(col, c("#6f6c63"), g * 0.5)
    w = wear(hpan, 55, 0.4)
    paint_wear = smooth((tk.fbm(S, 32, 3, 56) - 0.72) * 5)
    col = lerp(col, c("#b8c1cb"), np.clip(paint_wear * paint, 0, 1) * 0.5)  # scuffed paint
    col = lerp(col, c("#dfe6ee"), w * 0.5)
    col = lerp(col, c("#9aa5b2"), scratch_mask(57, 70, 34) * 0.3)
    dif, nrm = light_bake(col, height, 5.0, amount=0.55)
    spec = 0.35 + 0.1 * paint - 0.25 * groove_mask(hpan) - 0.2 * g + 0.3 * bh
    house = np.clip(lane * 0.5 + lmark * 0.4, 0, 1) * (1 - paint_wear * 0.6)
    return dif, nrm, tk.spm(np.clip(spec, 0, 1), house=house)


def dark_metal():
    h, pid, dist, rects = standard_layout(61, depth=3, min_size=96, gap=3.0, bevel=5.0, shift=(16, 200))
    r = tk.rng(62)
    vent = np.zeros((S, S))
    grille = np.zeros((S, S))
    for k, (x, y, w, hh) in enumerate(rects):
        roll = r.random()
        if roll < 0.45 and w >= 96 and hh >= 96:
            vent = np.maximum(vent, vents(x + 16, y + 14, w - 32, hh - 28, slot=6, pitch=12))
        elif roll < 0.6 and w >= 96 and hh >= 96:
            # round grille: concentric slots
            cx, cy = x + w / 2, y + hh / 2
            d = np.sqrt(wrap_d(XX, cx) ** 2 + wrap_d(YY, cy) ** 2)
            rad = min(w, hh) / 2 - 16
            grille = np.maximum(grille, (np.clip(1 - np.abs((d % 10) - 5) / 2, 0, 1) * (d < rad)))
    bh, bring = corner_bolts(rects, inset=13, r=3.2)
    height = h - 0.55 * tk.blur(vent, 1) - 0.4 * grille + 0.35 * bh - 0.12 * bring
    height += 0.015 * tk.fbm(S, 16, 3, 63)

    tint = per_panel(pid, 64) * 0.06
    col = lerp(c("#2d343e"), c("#46505e"), 0.5 + 0.5 * tk.fbm(S, 4, 4, 65)) * (1 + tint)[..., None]
    col = lerp(col, c("#0d1118"), np.clip(vent + grille * 0.8, 0, 1) * 0.9)
    col = lerp(col, c("#141922"), groove_mask(h) * 0.85)
    col = lerp(col, c("#6a7685"), bh * 0.6)
    g = grime(h, 66, 0.5)
    col = lerp(col, c("#1f1d1a"), g * 0.4)
    w = wear(h, 67, 0.45)
    col = lerp(col, c("#9aa7b6"), w * 0.7)  # polished edges catch light
    col = lerp(col, c("#6a7582"), scratch_mask(68, 40, 22) * 0.2)
    dif, nrm = light_bake(col, height, 6.0, amount=0.7, ao=0.25)
    spec = 0.45 - 0.3 * groove_mask(h) - 0.25 * vent - 0.2 * g + 0.4 * w + 0.25 * bh
    return dif, nrm, tk.spm(np.clip(spec, 0, 1))


# --------------------------------------------------------------------------- main
def seam_score(img):
    """Mean abs jump across the tile border vs. a typical interior row/col step."""
    a = np.asarray(img, float)
    border = (np.abs(a[0] - a[-1]).mean() + np.abs(a[:, 0] - a[:, -1]).mean()) / 2
    inner = (np.abs(a[S // 2] - a[S // 2 - 1]).mean() + np.abs(a[:, S // 2] - a[:, S // 2 - 1]).mean()) / 2
    return border, inner


def main():
    gens = dict(panel_white=panel_white, panel_blue=panel_blue, chrome=chrome,
                energy_cyan=energy_cyan, plate_steel=plate_steel, dark_metal=dark_metal)
    total = 0
    for name in NAMES:
        dif, nrm, sp = gens[name]()
        for arr in (dif, nrm, sp):
            assert arr.shape == (S, S, 3), (name, arr.shape)
        paths = tk.save_set(OUT, name, dif, nrm, sp)
        size = sum(p.stat().st_size for p in paths)
        total += size
        b, i = seam_score(dif)
        print(f"{name:12s} {size / 1024:7.1f} KB   seam {b:.4f} (interior step {i:.4f})")
    tk.contact_sheet(OUT / "_sheet.png", OUT, NAMES)
    print(f"total {total / 1024 / 1024:.2f} MB -> {OUT}")


if __name__ == "__main__":
    main()
