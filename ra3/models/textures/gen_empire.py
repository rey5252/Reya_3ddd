"""Empire of the Rising Sun texture set (Fortress Protocol).

Run:  python3 -I models/textures/gen_empire.py
Writes models/textures/empire/<name>_{dif,nrm,spm}.png (512x512, seamless) and empire/_sheet.png.

Look: RA3 Empire — glossy white ceramic armour with fine angular panel lines and laced
overlapping lames, deep crimson lacquer, black trim, teal energy circuitry, pale shrine stone,
dark tech deck with crimson/teal lane markings. Clean, high contrast, baked top light, little grime.
1 tile = 2 m. _spm: R = specular, G = self-illumination, B = house-colour mask.
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

import texkit as tk  # noqa: E402

S = tk.SIZE
OUT = HERE / "empire"

# palette (SPEC.md: Empire)
WHITE = "#f4f1ec"
CRIMSON = "#c8102e"
BLACK = "#1d1d22"
TEAL = "#3fe0c5"


# ----------------------------------------------------------------- helpers
class Canvas:
    """Supersampled, wrap-around vector canvas (every shape is drawn at the 9 tile offsets)."""

    def __init__(self, ss=4):
        self.ss = ss
        self.im = Image.new("L", (S * ss, S * ss), 0)
        self.d = ImageDraw.Draw(self.im)

    def _each(self, pts):
        ss = self.ss
        for ox in (-S, 0, S):
            for oy in (-S, 0, S):
                yield [((x + ox) * ss, (y + oy) * ss) for x, y in pts]

    def poly(self, pts, v=1.0):
        for p in self._each(pts):
            self.d.polygon(p, fill=int(v * 255))
        return self

    def rect(self, x0, y0, x1, y1, v=1.0, cut=0):
        return self.poly(chamfer_rect(x0, y0, x1, y1, cut), v)

    def line(self, pts, w, v=1.0):
        for p in self._each(pts):
            self.d.line(p, fill=int(v * 255), width=max(1, int(round(w * self.ss))), joint="curve")
        return self

    def circle(self, cx, cy, r, v=1.0):
        r2 = r * self.ss
        for ((x, y),) in self._each([(cx, cy)]):
            self.d.ellipse([x - r2, y - r2, x + r2, y + r2], fill=int(v * 255))
        return self

    def get(self):
        return np.asarray(self.im.resize((S, S), Image.BOX), dtype=float) / 255.0


def chamfer_rect(x0, y0, x1, y1, cut=0):
    """Rectangle with 45-degree corner cuts; cut = int or (tl, tr, br, bl)."""
    tl, tr, br, bl = (cut,) * 4 if np.isscalar(cut) else cut
    return [(x0 + tl, y0), (x1 - tr, y0), (x1, y0 + tr), (x1, y1 - br),
            (x1 - br, y1), (x0 + bl, y1), (x0, y1 - bl), (x0, y0 + tl)]


def wrap_dist(seed, maxd):
    """Wrap-around chamfer distance (px) to the nearest pixel where seed > 0.5, capped at maxd."""
    d = np.where(seed > 0.5, 0.0, float(maxd))
    steps = ((0, 1, 1.0), (0, -1, 1.0), (1, 0, 1.0), (-1, 0, 1.0),
             (1, 1, 1.414), (1, -1, 1.414), (-1, 1, 1.414), (-1, -1, 1.414))
    for _ in range(int(maxd) + 1):
        for dy, dx, w in steps:
            d = np.minimum(d, np.roll(d, (dy, dx), (0, 1)) + w)
    return d


def bevel(mask, width):
    """Crisp linear chamfer inside a 0..1 coverage mask -> 0..1 height."""
    d = wrap_dist(1 - mask, width + 1)
    h = np.clip((d - 0.5) / width, 0, 1)
    return tk.blur(h, 1) * (mask > 0.02)


def rgb(c, k=1.0):
    return np.array(tk.hex_rgb(c)) * k


def paint(base, mask, color):
    """Composite a flat colour over base with a 0..1 mask."""
    c = rgb(color) if isinstance(color, str) else np.asarray(color)
    return base * (1 - mask[..., None]) + c * mask[..., None]


LIGHT = np.array([0.0, 0.62, 0.78])
LIGHT /= np.linalg.norm(LIGHT)


def bake(col, h, nrm, light=0.55, ao=0.45, rim=0.35, rim_col=(1, 1, 1)):
    """RA3-style baked lighting: top light from the normal map, cavity AO, bright upper bevels."""
    n = nrm * 2 - 1
    nd = n @ LIGHT
    k = 1 + light * (nd - LIGHT[2])
    cav = np.clip((tk.blur(h, 4) - h) * 5, 0, 1)
    k = k * (1 - ao * cav)
    out = col * k[..., None]
    hl = np.clip((nd - LIGHT[2]) * 3, 0, 1) * rim
    out = out + (np.array(rim_col) - out) * hl[..., None]
    return np.clip(out, 0, 1)


def grain(seed, cells=32, amp=0.02):
    """Very soft tileable mottling (keeps PNGs small and the look clean)."""
    return 1 + amp * (tk.fbm(S, cells=cells, octaves=3, seed=seed) - 0.5) * 2


# ------------------------------------------------------------ armor_white
def armor_white():
    main = Canvas()
    inset = Canvas()
    engr = Canvas()
    stripe = Canvas()
    lacing = Canvas()
    lamp = Canvas()
    tri = Canvas()

    # A) big angular plates, y 0..222
    main.rect(100, 4, 348, 220, cut=(30, 6, 30, 6))
    main.rect(356, 4, 604, 220, cut=(6, 30, 6, 30))
    inset.poly([(140, 30), (300, 30), (322, 52), (322, 150), (262, 196), (178, 196), (140, 158)])
    inset.poly([(392, 40), (560, 40), (578, 58), (578, 120), (548, 150), (392, 150)])
    engr.line([(112, 160), (150, 198), (336, 198)], 1.6)
    engr.line([(392, 172), (590, 172)], 1.6)
    engr.line([(392, 186), (520, 186), (536, 202)], 1.6)
    engr.line([(330, 30), (330, 196)], 1.4)
    lamp.rect(406, 56, 470, 64, cut=3)
    lamp.rect(406, 70, 444, 78, cut=3)

    # B) crimson stripe band, y 226..244 (angular step)
    main.rect(-20, 226, 532, 244)
    stripe.poly([(-20, 230), (180, 230), (192, 236), (300, 236), (312, 230), (532, 230),
                 (532, 236), (312, 236), (300, 242), (192, 242), (180, 236), (-20, 236)])

    # C) laced overlapping lames, y 248..408 (4 rows of 40 px)
    lame = np.zeros((S, S))
    yy = np.arange(S)[:, None] * np.ones((1, S))
    for r in range(4):
        y0 = 248 + 40 * r
        v = (yy - y0) / 40.0
        inside = (v >= 0) & (v < 1)
        lame = np.where(inside, 0.30 + 0.45 * v, lame)
    lame_m = ((yy >= 248) & (yy < 408)).astype(float)
    # small dark step under each lame (shadow line) handled by the height drop
    for r in range(4):
        y0 = 248 + 40 * r
        off = 64 * (r % 2)
        for k in range(4):
            x = 40 + off + 128 * k
            for dx in (0, 10):
                lacing.rect(x + dx - 2, y0 + 9, x + dx + 2, y0 + 29, cut=1.5)
            engr.line([(x + 69, y0 + 6), (x + 69, y0 + 34)], 1.2)

    # D) lower plates, y 412..508
    main.rect(60, 412, 216, 508, cut=(6, 22, 6, 6))
    main.rect(224, 412, 376, 508, cut=(22, 6, 6, 6))
    main.rect(384, 412, 572, 508, cut=(6, 6, 22, 6))
    inset.rect(250, 430, 350, 490, cut=12)
    engr.line([(80, 494), (196, 494)], 1.4)
    engr.line([(400, 430), (440, 470), (556, 470)], 1.6)
    tri.poly([(82, 428), (102, 428), (82, 448)])
    tri.poly([(106, 428), (114, 428), (94, 448), (86, 448)])
    tri.poly([(286, 446), (314, 446), (300, 470)])
    lamp.rect(470, 484, 540, 492, cut=3)

    m = main.get()
    m_all = np.maximum(m, lame_m)
    hm = bevel(m, 5)
    hi = bevel(inset.get(), 4)
    e = engr.get()
    st = stripe.get()
    lc = lacing.get()
    lp = lamp.get()
    tr = tri.get()

    h = 0.55 * hm + 0.25 * hi * (hm > 0.5) - 0.18 * e - 0.06 * st - 0.10 * lp - 0.05 * tr
    h = np.where(lame_m > 0, lame + 0.10 * bevel(lc, 2) - 0.12 * e, h)
    nrm = tk.normal_from_height(h, 7.0)

    col = np.ones((S, S, 3)) * rgb(WHITE)
    col *= grain(11, amp=0.015)[..., None]
    col = paint(col, 1 - m_all, "#2a2a31")
    col = paint(col, e * 0.55, "#8d8c92")
    house = np.clip(st + lc + tr, 0, 1)
    col = paint(col, house, CRIMSON)
    col = paint(col, lp, "#bffcf2")
    col = bake(col, h, nrm)
    # crisp shadow line where each lame overlaps the next one
    under = np.zeros(S)
    for y0 in (288, 328, 368):
        under[y0:y0 + 4] = (0.45, 0.3, 0.15, 0.05)
    col = col * (1 - under[:, None, None])

    spec = 0.62 * m_all + 0.10 + 0.12 * house - 0.25 * e
    spec = np.clip(np.where(lp > 0.3, 0.9, spec), 0, 1)
    glow = np.clip(lp + 0.35 * tk.blur(lp, 3), 0, 1)
    return col, nrm, tk.spm(spec, glow, house)


# --------------------------------------------------------- lacquer_crimson
def lacquer_crimson():
    main = Canvas()
    inlay = Canvas()
    # two staggered rows of large lacquered panels
    for y0, off in ((0, 0), (256, 128)):
        for k in range(2):
            x0 = off + 256 * k
            main.rect(x0 + 4, y0 + 4, x0 + 252, y0 + 252, cut=(26, 6, 26, 6))
            pts = chamfer_rect(x0 + 18, y0 + 18, x0 + 238, y0 + 238, (20, 4, 20, 4))
            inlay.line(pts + [pts[0]], 2.2)
    m = main.get()
    il = inlay.get()
    hm = bevel(m, 7)
    # lacquer surfaces are slightly pillowed (soft dome) for a glossy look
    dome = np.sqrt(np.clip(wrap_dist(1 - m, 40) / 40, 0, 1))
    h = 0.5 * hm + 0.12 * dome - 0.10 * il
    nrm = tk.normal_from_height(h, 6.0)

    depth = tk.fbm(S, cells=4, octaves=3, seed=21)
    col = tk.colorize(depth, "#b20c27", "#cf1532")
    col = paint(col, 1 - m, "#3a0710")
    col = paint(col, il, BLACK)
    col = bake(col, h, nrm, light=0.6, rim=0.28, rim_col=(1.0, 0.75, 0.75))
    spec = np.clip(0.82 * m + 0.05 - 0.4 * il, 0, 1)
    return col, nrm, tk.spm(spec)


# --------------------------------------------------------------- trim_black
def trim_black():
    main = Canvas()
    rib = Canvas()
    engr = Canvas()
    bolt = Canvas()
    for y0 in (0, 256):
        main.rect(-20, y0 + 4, 532, y0 + 140)              # flat band
        for i in range(3):                                   # ribbed strip
            yr = y0 + 150 + 22 * i
            rib.rect(-20, yr, 532, yr + 16)
        main.rect(-20, y0 + 220, 532, y0 + 252)             # lower band
        sh = 0 if y0 == 0 else 64
        for k in range(4):
            x = sh + 128 * k
            engr.line([(x, y0 + 6), (x, y0 + 138)], 1.6)
            engr.line([(x + 64, y0 + 222), (x + 64, y0 + 250)], 1.4)
            for bx in (x + 24, x + 104):
                hexagon = [(bx + 7 * np.cos(a), y0 + 72 + 7 * np.sin(a))
                           for a in np.radians(np.arange(0, 360, 60) + 30)]
                bolt.poly(hexagon)
        engr.line([(-20, y0 + 30), (532, y0 + 30)], 1.2)
        engr.line([(-20, y0 + 114), (532, y0 + 114)], 1.2)
    m = main.get()
    rb = rib.get()
    e = engr.get()
    b = bolt.get()
    h = 0.5 * bevel(m, 5) + 0.45 * bevel(rb, 6) - 0.15 * e + 0.25 * bevel(b, 2)
    nrm = tk.normal_from_height(h, 6.0)
    surf = np.clip(m + rb, 0, 1)
    col = np.ones((S, S, 3)) * rgb(BLACK)
    col *= grain(31, amp=0.03)[..., None]
    col = paint(col, 1 - surf, "#0b0b0e")
    col = paint(col, b, "#2c2d34")
    col = bake(col, h, nrm, light=0.7, ao=0.5, rim=0.22, rim_col=(0.55, 0.57, 0.65))
    spec = np.clip(0.45 * surf + 0.08 + 0.3 * b - 0.2 * e, 0, 1)
    return col, nrm, tk.spm(spec)


# -------------------------------------------------------------- energy_teal
def hex_field(cols=10, rows=12):
    """Seamless pointy-top hex Voronoi: (edge distance px, cell id). rows must be even."""
    a = S / cols
    b2 = S / rows
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    best1 = np.full((S, S), 1e9)
    best2 = np.full((S, S), 1e9)
    id1 = np.zeros((S, S), int)
    id2 = np.zeros((S, S), int)
    cents = []
    for j in range(rows):
        for i in range(cols):
            cents.append(((i + 0.5 * (j % 2)) * a, j * b2))
    cents = np.array(cents)
    for k, (cx, cy) in enumerate(cents):
        dx = (xx - cx + S / 2) % S - S / 2
        dy = (yy - cy + S / 2) % S - S / 2
        d = dx * dx + dy * dy
        nb1 = d < best1
        nb2 = (~nb1) & (d < best2)
        best2 = np.where(nb1, best1, np.where(nb2, d, best2))
        id2 = np.where(nb1, id1, np.where(nb2, k, id2))
        best1 = np.where(nb1, d, best1)
        id1 = np.where(nb1, k, id1)
    c1, c2 = cents[id1], cents[id2]
    dc = (c1 - c2 + S / 2) % S - S / 2
    sep = np.sqrt((dc ** 2).sum(-1)) + 1e-6
    edge = (best2 - best1) / (2 * sep)
    return edge, id1


def energy_teal():
    edge, cid = hex_field()
    r = tk.rng(41)
    hot = r.random(cid.max() + 1)
    channel = np.clip(1 - (edge - 1.5) / 1.5, 0, 1)            # glowing gaps between tiles
    tile_h = np.clip((edge - 2.0) / 5.0, 0, 1)                 # chamfered dark hex tiles
    energized = (hot[cid] < 0.28).astype(float)
    inner = np.clip((edge - 13.0) / 1.2, 0, 1) * energized     # glowing inset hex in some cells
    inner_rim = np.clip(1 - abs(edge - 11.0) / 1.2, 0, 1) * energized

    # circuit traces on the tiles (45/90 degree), pads at the ends
    tr = Canvas()
    pads = Canvas()
    rr = tk.rng(7)
    for _ in range(14):
        x, y = rr.integers(0, 32, 2) * 16
        pts = [(x, y)]
        for _s in range(rr.integers(2, 5)):
            ang = rr.choice([0, 45, 90, 135, 180, 225, 270, 315])
            L = rr.integers(2, 6) * 12
            x, y = x + L * np.cos(np.radians(ang)), y + L * np.sin(np.radians(ang))
            pts.append((x, y))
        tr.line(pts, 2.4)
        for p in (pts[0], pts[-1]):
            pads.circle(p[0], p[1], 3.6)
    t = tr.get() * (1 - channel)
    pd = pads.get() * (1 - channel)

    h = 0.6 * tile_h - 0.25 * inner - 0.1 * channel + 0.08 * t + 0.1 * pd
    nrm = tk.normal_from_height(h, 5.0)

    col = np.ones((S, S, 3)) * rgb("#13201f")
    col *= (0.85 + 0.15 * tile_h)[..., None]
    col = bake(col, h, nrm, light=0.8, ao=0.3, rim=0.25, rim_col=(0.35, 0.55, 0.52))
    spill = tk.blur(channel, 4)
    col = col + rgb(TEAL, 0.35)[None, None] * spill[..., None]
    glow_core = np.clip(channel + inner + inner_rim + t + pd, 0, 1)
    col = paint(col, np.clip(channel + inner_rim + t + pd, 0, 1), TEAL)
    col = paint(col, inner, "#1f8f7f")
    core_hot = np.clip(channel * (edge < 1.6) + t * 0.6 + pd * 0.8 + inner_rim * 0.7, 0, 1)
    col = paint(col, core_hot * 0.75, "#d8fff8")
    col = np.clip(col, 0, 1)

    glow = np.clip(glow_core * 0.95 + 0.35 * spill + 0.5 * inner, 0, 1)
    spec = np.clip(0.55 * tile_h + 0.2 + 0.2 * glow_core, 0, 1)
    return col, nrm, tk.spm(spec, glow)


# -------------------------------------------------------------------- stone
def stone():
    blocks = Canvas()
    r = tk.rng(53)
    tints = []
    layouts = [(0, 256), (128, 320, 448), (64, 256, 384), (192, 352)]  # wrapping block joints
    jid = Canvas()  # block-id painted as grey levels for per-block tint
    n = 0
    for j, xs in enumerate(layouts):
        y0 = 128 * j
        xs = list(xs) + [xs[0] + S]
        for i in range(len(xs) - 1):
            x0, x1 = xs[i], xs[i + 1]
            blocks.rect(x0 + 3, y0 + 3, x1 - 3, y0 + 125, cut=4)
            n += 1
            jid.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 127, v=n / 20)
            tints.append(r.random())
    m = blocks.get()
    ids = np.rint(jid.get() * 20).astype(int)
    tint = np.array([0.5] + tints)[np.clip(ids, 0, n)]

    d = wrap_dist(1 - m, 30)
    chamfer = np.clip(d / 6, 0, 1)
    pillow = np.sqrt(np.clip(d / 30, 0, 1))
    tex = tk.fbm(S, cells=16, octaves=4, seed=57)
    chip = tk.edge_wear(chamfer, 0.15, tk.fbm(S, 32, 3, seed=59)) * (d < 8) * (m > 0.5)
    h = 0.45 * chamfer + 0.12 * pillow + 0.04 * tex - 0.06 * chip
    h = tk.blur(h, 1)
    nrm = tk.normal_from_height(h, 6.0)

    base = tk.colorize(tint, "#cfc8ba", "#e2ddd2")
    base *= (0.97 + 0.06 * tex)[..., None]
    col = paint(base, 1 - m, "#8f897d")
    col = paint(col, chip * 0.35, "#ebe6dc")
    col = bake(col, h, nrm, light=0.5, ao=0.5, rim=0.25)
    spec = np.clip(0.18 * m + 0.04 + 0.05 * tex, 0, 1)
    return col, nrm, tk.spm(spec)


# -------------------------------------------------------------- plate_steel
def plate_steel():
    main = Canvas()
    inset = Canvas()
    mark = Canvas()
    lights = Canvas()
    bolts = Canvas()
    engr = Canvas()
    for y0 in (0, 256):
        for x0 in (0, 256):
            main.rect(x0 + 3, y0 + 3, x0 + 253, y0 + 253, cut=10)
            for bx, by in ((14, 14), (242, 14), (242, 242), (14, 242)):
                bolts.circle(x0 + bx, y0 + by, 4.2)
    # inset deck plates
    inset.rect(28, 168, 228, 236, cut=(14, 0, 14, 0))
    inset.rect(284, 168, 484, 236, cut=(0, 14, 0, 14))
    inset.rect(28, 300, 228, 480, cut=(18, 4, 18, 4))
    inset.rect(284, 300, 484, 480, cut=(4, 18, 4, 18))
    engr.line([(128, 300), (128, 480)], 1.4)
    engr.line([(384, 300), (384, 480)], 1.4)
    # crimson lane: band with chevron arrows, y 100..132 (crosses the tile seam horizontally)
    mark.poly([(-20, 100), (532, 100), (532, 106), (-20, 106)])
    mark.poly([(-20, 126), (532, 126), (532, 132), (-20, 132)])
    for k in range(8):
        x = 64 * k + 20
        mark.poly([(x, 109), (x + 14, 109), (x + 26, 116), (x + 14, 123), (x, 123), (x + 12, 116)])
    # crimson corner brackets on lower deck plates
    for x0, sgn in ((40, 1), (472, -1)):
        mark.poly([(x0, 312), (x0 + sgn * 40, 312), (x0 + sgn * 40, 318), (x0 + sgn * 6, 318),
                   (x0 + sgn * 6, 350), (x0, 350)])
        mark.poly([(x0, 468), (x0 + sgn * 40, 468), (x0 + sgn * 40, 462), (x0 + sgn * 6, 462),
                   (x0 + sgn * 6, 430), (x0, 430)])
    # teal light strips: dashed lane lights + small lamps
    for k in range(16):
        x = 32 * k + 6
        lights.rect(x, 146, x + 18, 150)
        lights.rect(x + 8, 80, x + 26, 84)
    for x0 in (64, 320):
        lights.rect(x0 + 10, 386, x0 + 118, 394, cut=3)
    mk = mark.get()
    lg = lights.get()
    m = main.get()
    hm = bevel(m, 4)
    hi = bevel(inset.get(), 3)
    b = bevel(bolts.get(), 2)
    e = engr.get()
    h = 0.5 * hm + 0.18 * hi - 0.15 * e + 0.2 * b - 0.08 * lg + 0.02 * mk
    nrm = tk.normal_from_height(h, 6.0)

    col = np.ones((S, S, 3)) * rgb("#2b2e35")
    col *= grain(61, cells=16, amp=0.04)[..., None]
    col = paint(col, hi * 0.6, "#363a43")
    col = paint(col, 1 - m, "#0f1013")
    col = paint(col, b, "#4a4e58")
    col = paint(col, mk, "#b80f2b")
    col = paint(col, lg, "#8ff7e6")
    col = bake(col, h, nrm, light=0.6, ao=0.5, rim=0.25, rim_col=(0.75, 0.78, 0.85))
    # light scuffs (worn markings, bright metal on edges)
    sc = tk.scratches(S, count=40, length=30, seed=63) * 0.15
    col = col + (0.55 - col) * sc[..., None]
    wear = tk.edge_wear(hm, 0.3, tk.fbm(S, 16, 4, seed=65)) * 0.25 * (hm > 0.35) * (1 - mk)
    col = col + (np.array([0.55, 0.57, 0.6]) - col) * wear[..., None]
    col = col + rgb(TEAL, 0.3)[None, None] * tk.blur(lg, 3)[..., None] * (1 - lg[..., None])
    col = np.clip(col, 0, 1)

    spec = np.clip(0.42 * m + 0.06 + 0.2 * b + 0.15 * mk + 0.3 * wear, 0, 1)
    glow = np.clip(lg + 0.3 * tk.blur(lg, 3), 0, 1)
    return col, nrm, tk.spm(spec, glow, mk)


SETS = {
    "armor_white": armor_white,
    "lacquer_crimson": lacquer_crimson,
    "trim_black": trim_black,
    "energy_teal": energy_teal,
    "stone": stone,
    "plate_steel": plate_steel,
}


def main():
    total = 0
    for name, fn in SETS.items():
        dif, nrm, sp = fn()
        for p in tk.save_set(OUT, name, dif, nrm, sp):
            total += p.stat().st_size
        print(f"{name}: ok")
    tk.contact_sheet(OUT / "_sheet.png", OUT, list(SETS))
    print(f"wrote {len(SETS) * 3} maps to {OUT} ({total / 1e6:.2f} MB) + _sheet.png")


if __name__ == "__main__":
    main()
