"""Draws the fusion core's screen and JEI page in the cosmic style, at twice the GUI's size (needs Pillow and numpy).

    python3 tools/gen_gui.py

- gui/fusion_frame.png: the panel: a rounded frame of dark brushed metal with a gold trim, gems in ringed medallions
  at its corners, a glass title plaque; the window onto space left clear (with a soft shadow round its edge); the
  inventory a panel of dark glass with glossy slots.
- gui/fusion_space.png: space through the window: nebulae, dust lanes, stars; and wisps of nebula that drift.
- effect/planets.png: the planets the screen (and the world) show orbiting the singularity, shade and rim to light them
  from any side, a ringed giant's ring, a galaxy, glints, a shooting star.
- gui/fusion_widgets.png: portholes for items, the start orb and its ring, the tubes of plasma, the hologram, gloss,
  sparkles, JEI's frame and arrow.
Layouts are layout.py's.
"""
import math
import os

import numpy as np
from PIL import Image

import layout as L

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "singularityfusion", "textures")


def out(*parts):
    path = os.path.join(TEX, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


# =================================================================== helpers

def save(rgba, path):
    """rgba: float array (h, w, 4) in 0..1, straight (not premultiplied) alpha."""
    img = np.clip(rgba * 255.0 + 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(path)


def col(*c):
    return np.array(c, dtype=np.float64) / 255.0


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def mix(a, b, t):
    t = np.asarray(t)[..., None] if np.ndim(t) else t
    return a + (b - a) * t


def lattice(w, h, cells_x, cells_y, seed, wrap_x=False, wrap_y=False):
    """Value noise over a w x h image with cells_x by cells_y lattice cells (tiling if wrapped), 0 to 1."""
    rng = np.random.default_rng(seed)
    grid = rng.random((cells_y + 1, cells_x + 1))
    if wrap_x:
        grid[:, -1] = grid[:, 0]
    if wrap_y:
        grid[-1, :] = grid[0, :]
    x = (np.arange(w) + 0.5) / w * cells_x
    y = (np.arange(h) + 0.5) / h * cells_y
    xi = np.floor(x).astype(int)
    yi = np.floor(y).astype(int)
    xf = x - xi
    yf = y - yi
    sx = xf * xf * (3 - 2 * xf)
    sy = yf * yf * (3 - 2 * yf)
    a = grid[yi][:, xi]
    b = grid[yi][:, xi + 1]
    c = grid[yi + 1][:, xi]
    d = grid[yi + 1][:, xi + 1]
    top = a + (b - a) * sx[None, :]
    bottom = c + (d - c) * sx[None, :]
    return top + (bottom - top) * sy[:, None]


def fbm(w, h, cells_x, cells_y, seed, octaves=5, gain=0.5, wrap_x=False, wrap_y=False):
    total = np.zeros((h, w))
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        total += amp * lattice(w, h, cells_x * 2 ** o, cells_y * 2 ** o, seed + 17 * o, wrap_x, wrap_y)
        norm += amp
        amp *= gain
    return total / norm


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float64)
    return x + 0.5, y + 0.5


def sd_round_rect(px, py, x1, y1, x2, y2, r):
    """Signed distance to a rounded rectangle (negative inside)."""
    cx, cy = (x1 + x2) / 2.0, (y1 + y2) / 2.0
    bx, by = (x2 - x1) / 2.0, (y2 - y1) / 2.0
    qx = np.abs(px - cx) - bx + r
    qy = np.abs(py - cy) - by + r
    outside = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0))
    inside = np.minimum(np.maximum(qx, qy), 0)
    return outside + inside - r


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b + (a - b) * h - k * h * (1 - h)


def gradient(f):
    gy, gx = np.gradient(f)
    n = np.hypot(gx, gy) + 1e-9
    return gx / n, gy / n


def over(dst, src):
    """Paints src (h, w, 4, straight alpha) over dst in place."""
    sa = src[..., 3:4]
    da = dst[..., 3:4]
    oa = sa + da * (1 - sa)
    rgb = (src[..., :3] * sa + dst[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-9)
    dst[..., :3] = rgb
    dst[..., 3:4] = oa


def layer(h, w, rgb, alpha):
    """An (h, w, 4) layer of colour rgb (array (h, w, 3) or a colour) with an alpha (array or number)."""
    out_ = np.zeros((h, w, 4))
    out_[..., :3] = rgb
    out_[..., 3] = alpha
    return out_


def disc_mask(px, py, cx, cy, r, soft=1.0):
    return np.clip((r - np.hypot(px - cx, py - cy)) / soft + 0.5, 0.0, 1.0)


LIGHT = np.array([-0.7071, -0.7071])   # light comes from the top left: faces turned that way are lit

# palette
OUTLINE = col(10, 8, 18)
METAL_TOP, METAL_BOTTOM = col(66, 58, 92), col(28, 25, 42)
METAL_HI = col(150, 140, 190)
INNER_TOP, INNER_BOTTOM = col(34, 30, 50), col(18, 16, 28)
GOLD_DK, GOLD, GOLD_HI, GOLD_GLINT = col(104, 64, 22), col(206, 146, 60), col(255, 222, 148), col(255, 248, 226)
VIOLET_DK, VIOLET, VIOLET_HI, VIOLET_WHITE = col(52, 16, 96), col(140, 70, 236), col(200, 150, 255), col(240, 226, 255)


def gem(canvas, px, py, cx, cy, r, core=VIOLET_HI, mid=VIOLET, edge=VIOLET_DK, rim=True):
    """A cut gem: a radial gradient, a facet shine, a gold setting round it."""
    d = np.hypot(px - cx, py - cy) / r
    m = disc_mask(px, py, cx, cy, r, 1.2)
    t = np.clip(d, 0, 1)
    c = mix(mix(core, mid, smooth(0.0, 0.55, t)), edge, smooth(0.55, 1.0, t))
    # a shine up and left, a glint
    shine = np.exp(-(((px - (cx - r * 0.35)) / (r * 0.35)) ** 2 + ((py - (cy - r * 0.38)) / (r * 0.28)) ** 2))
    c = c + (VIOLET_WHITE - c) * shine[..., None] * 0.8
    over(canvas, layer(px.shape[0], px.shape[1], c, m))
    if rim:
        ring = np.clip(1.0 - np.abs(np.hypot(px - cx, py - cy) - r - 1.2) / 1.4, 0, 1)
        ang = np.arctan2(py - cy, px - cx)
        lit = 0.5 + 0.5 * np.cos(ang + 2.36)
        over(canvas, layer(px.shape[0], px.shape[1], mix(GOLD_DK, GOLD_HI, lit), ring))


def medallion(canvas, px, py, cx, cy, r):
    """A ringed medallion: a dark metal disc with ticks round it, a gold ring, a gem in the middle."""
    d = np.hypot(px - cx, py - cy)
    ang = np.arctan2(py - cy, px - cx)
    lit = 0.5 + 0.5 * np.cos(ang + 2.36)
    h, w = px.shape
    body = disc_mask(px, py, cx, cy, r, 1.0)
    base = mix(INNER_BOTTOM, METAL_TOP, 0.25 + 0.5 * lit)
    over(canvas, layer(h, w, base, body))
    ticks = (np.abs(np.sin(ang * 6)) > 0.93) & (d > r * 0.62) & (d < r * 0.86)
    over(canvas, layer(h, w, GOLD, ticks.astype(float) * body * 0.9))
    for rr, wdt, cc in ((r * 0.98, 1.4, None), (r * 0.58, 1.2, None)):
        ring = np.clip(1.0 - np.abs(d - rr) / wdt, 0, 1)
        over(canvas, layer(h, w, mix(GOLD_DK, GOLD_HI, lit), ring))
    over(canvas, layer(h, w, OUTLINE, np.clip(1.0 - np.abs(d - r - 1.0) / 1.0, 0, 1) * 0.9))
    gem(canvas, px, py, cx, cy, r * 0.45, rim=False)


# =================================================================== the frame

def frame():
    s = L.Frame.SIZE
    px, py = grid(s, s)
    k = 2.0
    machine = sd_round_rect(px, py, 0, 0, 2 * L.W, 2 * L.MH, 14)
    inv = sd_round_rect(px, py, 2 * L.PANEL_X, 2 * L.MH - 20, 2 * (L.PANEL_X + L.INV_W), 2 * L.H, 12)
    outer = smin(machine, inv, 12)
    wx1, wy1, wx2, wy2 = (v * k for v in L.WINDOW)
    ix1, iy1, ix2, iy2 = (v * k for v in L.INV_WINDOW)
    space_win = sd_round_rect(px, py, wx1, wy1, wx2, wy2, 12)
    inv_win = sd_round_rect(px, py, ix1, iy1, ix2, iy2, 10)
    win = np.minimum(space_win, inv_win)
    canvas = np.zeros((s, s, 4))

    # the frame's metal, banded from its outer edge to the windows' edges
    t1 = np.maximum(-outer, 0)
    t2 = np.maximum(win, 0)
    band = t1 / (t1 + t2 + 1e-6)
    onx, ony = gradient(outer)
    inx, iny = gradient(win)
    lit_out = onx * LIGHT[0] + ony * LIGHT[1]
    lit_in = -(inx * LIGHT[0] + iny * LIGHT[1])
    vertical = py / (2 * L.H)
    brushed = fbm(s, s, 64, 4, 3, 3) * 0.7 + fbm(s, s, 8, 160, 5, 2) * 0.3
    metal = mix(METAL_TOP, METAL_BOTTOM, np.clip(py / (2 * L.MH), 0, 1)) * (0.82 + 0.3 * brushed)[..., None]
    metal = metal + (METAL_HI - metal) * (np.clip(lit_out, 0, 1) * smooth(0.16, 0.1, band) * 0.0)[..., None]
    c = np.zeros((s, s, 3))
    c[:] = metal
    # the outer bevel: lit up and left
    bevel = smooth(0.1, 0.16, band) * (1 - smooth(0.2, 0.3, band))
    c = c + (METAL_HI - c) * (np.clip(lit_out, 0, 1) * bevel * 0.55)[..., None]
    c = c * (1 - (np.clip(-lit_out, 0, 1) * bevel * 0.35))[..., None]
    # the gold trim, a bright line along it and a dark one inside
    gold_t = smooth(0.4, 0.45, band) * (1 - smooth(0.57, 0.62, band))
    gold = mix(GOLD_DK, GOLD_HI, np.clip(0.55 + 0.45 * lit_out, 0, 1))
    gold = gold + (GOLD_GLINT - gold) * (np.exp(-((band - 0.47) / 0.025) ** 2) * 0.6)[..., None]
    c = c + (gold - c) * gold_t[..., None]
    c = c * (1 - 0.5 * np.exp(-((band - 0.6) / 0.02) ** 2))[..., None]
    # the inner metal, darker, its bevel lit where it faces up and left (into the window)
    inner_t = smooth(0.6, 0.64, band)
    inner = mix(INNER_TOP, INNER_BOTTOM, np.clip(vertical, 0, 1)) * (0.85 + 0.25 * brushed)[..., None]
    inner = inner + (METAL_HI - inner) * (np.clip(lit_in, 0, 1) * smooth(0.75, 0.92, band) * 0.4)[..., None]
    c = c + (inner - c) * inner_t[..., None]
    # the outline round it, and the dark line where it meets the windows
    c = c * (1 - 0.85 * (1 - smooth(0.0, 0.1, band)))[..., None] + OUTLINE * (1 - smooth(0.0, 0.1, band))[..., None] * 0.85
    c = c * (1 - 0.8 * smooth(0.9, 0.98, band))[..., None]
    alpha_out = np.clip(0.5 - outer, 0, 1)
    in_frame = np.clip(0.5 + win, 0, 1)
    over(canvas, layer(s, s, c, alpha_out * in_frame))

    # through the space window: a soft shadow round its edge, clear beyond
    inside_space = np.clip(0.5 - space_win, 0, 1)
    shadow = 0.62 * (1 - smooth(0.0, 18.0, -space_win))
    over(canvas, layer(s, s, OUTLINE * 0.3, inside_space * shadow))

    # the inventory's panel: dark glass, a faint nebula, dim stars, glossy slots
    inside_inv = np.clip(0.5 - inv_win, 0, 1)
    glass = mix(col(26, 21, 42), col(12, 10, 20), np.clip((py - iy1) / (iy2 - iy1), 0, 1))
    neb = fbm(s, s, 5, 5, 41, 4)
    glass = glass + col(40, 16, 66) * (smooth(0.55, 0.85, neb) * 0.5)[..., None]
    glass = glass * (1 - 0.5 * (1 - smooth(0.0, 12.0, -inv_win)))[..., None]
    over(canvas, layer(s, s, glass, inside_inv))
    rng = np.random.default_rng(9)
    for _ in range(110):
        x = rng.uniform(ix1 + 6, ix2 - 6)
        y = rng.uniform(iy1 + 6, iy2 - 6)
        b = rng.random() ** 2
        m = disc_mask(px, py, x, y, 0.7 + b, 0.8) * (0.25 + 0.5 * b)
        over(canvas, layer(s, s, col(220, 210, 255), m * inside_inv))
    for row in range(3):
        for c_ in range(9):
            slot(canvas, px, py, 2 * (L.INV_X - 1 + 18 * c_), 2 * (L.INV_Y - 1 + 18 * row))
    for c_ in range(9):
        slot(canvas, px, py, 2 * (L.INV_X - 1 + 18 * c_), 2 * (L.HOTBAR_Y - 1))

    # ornaments (layout.py's): medallions at the corners, gems on the sides and along the top and bottom, the title's plaque
    for (cx, cy, r) in L.MEDALLIONS:
        medallion(canvas, px, py, 2 * cx, 2 * cy, 2 * r)
    for (cx, cy, r) in L.GEMS:
        gem(canvas, px, py, 2 * cx, 2 * cy, 2 * r)
    plaque(canvas, px, py)
    save(canvas, out("gui", "fusion_frame.png"))


def slot(canvas, px, py, x, y):
    """A glossy slot recess, 36 square at (x, y)."""
    h, w = px.shape
    d = sd_round_rect(px, py, x + 1, y + 1, x + 35, y + 35, 5)
    inside = np.clip(0.5 - d, 0, 1)
    c = mix(col(8, 6, 15), col(20, 16, 32), np.clip((py - y) / 36.0, 0, 1))
    c = c + col(70, 30, 120) * (np.exp(-((py - (y + 34)) / 5.0) ** 2) * 0.35)[..., None]
    over(canvas, layer(h, w, c, inside))
    nx, ny = gradient(d)
    lit = -(nx * LIGHT[0] + ny * LIGHT[1])
    rim = np.clip(1.0 - np.abs(d + 1.0) / 1.3, 0, 1)
    rim_c = mix(col(4, 3, 8), col(110, 98, 150), np.clip(lit * 0.5 + 0.5, 0, 1))
    over(canvas, layer(h, w, rim_c, rim))


def plaque(canvas, px, py):
    h, w = px.shape
    x1, y1, x2, y2 = (v * 2 for v in L.TITLE)
    d = sd_round_rect(px, py, x1, y1, x2, y2, 9)
    inside = np.clip(0.5 - d, 0, 1)
    glass = mix(col(40, 30, 62), col(10, 8, 18), np.clip((py - y1) / (y2 - y1), 0, 1))
    gloss = (1 - smooth(y1, (y1 + y2) / 2, py)) * 0.12
    glass = glass + (col(255, 255, 255) - glass) * gloss[..., None]
    over(canvas, layer(h, w, glass, inside))
    nx, ny = gradient(d)
    lit = np.clip(0.5 - 0.5 * (nx * LIGHT[0] + ny * LIGHT[1]) * -1, 0, 1)
    border = np.clip(1.0 - np.abs(d + 2.0) / 1.8, 0, 1)
    over(canvas, layer(h, w, mix(GOLD_DK, GOLD_HI, lit), border))
    inner = np.clip(1.0 - np.abs(d + 5.0) / 0.9, 0, 1) * 0.6
    over(canvas, layer(h, w, VIOLET, inner))
    for (cx, cy, r) in L.PLAQUE_MEDALLIONS:
        medallion(canvas, px, py, 2 * cx, 2 * cy, 2 * r)


# =================================================================== space

def space():
    s = L.Space.SIZE
    canvas = np.zeros((s, s, 4))
    bx, by, bw, bh = L.Space.BASE
    px, py = grid(bw, bh)
    hx, hy = 2 * (L.HOLE[0] - L.WINDOW[0]), 2 * (L.HOLE[1] - L.WINDOW[1])
    d = np.hypot(px - hx, (py - hy) * 1.25)
    base = mix(col(30, 14, 52), col(4, 3, 11), smooth(10, 330, d))
    n1 = fbm(bw, bh, 4, 3, 101, 6)
    n2 = fbm(bw, bh, 6, 4, 202, 5)
    n3 = fbm(bw, bh, 5, 3, 303, 6)
    n4 = fbm(bw, bh, 7, 5, 404, 4)
    dust = fbm(bw, bh, 9, 5, 505, 5)
    # a violet and magenta nebula drifting up and left, a blue and teal one down and right
    mask_a = smooth(0.42, 0.78, n1) * (1 - 0.7 * smooth(120, 360, np.hypot(px - 150, py - 80)))
    neb_a = mix(col(86, 26, 150), col(190, 56, 140), n2)
    mask_b = smooth(0.45, 0.8, n3) * (1 - 0.7 * smooth(110, 340, np.hypot(px - 360, py - 230)))
    neb_b = mix(col(26, 56, 160), col(24, 140, 160), n4)
    c = base + neb_a * (mask_a * 0.62)[..., None] + neb_b * (mask_b * 0.5)[..., None]
    # bright edges where the gas is thickest
    c = c + col(255, 170, 230) * (smooth(0.72, 0.9, n1) * mask_a * 0.25)[..., None]
    c = c + col(150, 230, 255) * (smooth(0.75, 0.92, n3) * mask_b * 0.2)[..., None]
    # dark lanes of dust across them
    c = c * (1 - 0.55 * smooth(0.55, 0.72, dust) * (1 - smooth(0.72, 0.9, dust)))[..., None]
    c = c * (0.85 + 0.15 * fbm(bw, bh, 30, 18, 606, 3))[..., None]
    # stars: a dust of faint ones, some brighter with a glow, a few with spikes
    rng = np.random.default_rng(77)
    stars = np.zeros((bh, bw, 3))
    tints = [col(255, 255, 255), col(200, 215, 255), col(255, 228, 190), col(230, 205, 255)]
    for _ in range(1500):
        x, y = rng.integers(0, bw), rng.integers(0, bh)
        stars[y, x] += tints[rng.integers(0, 4)] * (0.15 + 0.6 * rng.random() ** 2)
    for _ in range(90):
        x, y = rng.uniform(0, bw), rng.uniform(0, bh)
        b = 0.4 + 0.6 * rng.random()
        g = np.exp(-((px - x) ** 2 + (py - y) ** 2) / (2 * (0.8 + b) ** 2))
        stars += tints[rng.integers(0, 4)] * (g * b)[..., None]
    for _ in range(12):
        x, y = rng.uniform(10, bw - 10), rng.uniform(10, bh - 10)
        b = 0.7 + 0.3 * rng.random()
        r = 6 + 6 * rng.random()
        core = np.exp(-((px - x) ** 2 + (py - y) ** 2) / 3.0)
        spikes = (np.exp(-np.abs(px - x) / r) * np.exp(-((py - y) / 0.7) ** 2) + np.exp(-np.abs(py - y) / r) * np.exp(-((px - x) / 0.7) ** 2))
        stars += tints[rng.integers(0, 4)] * ((core + 0.6 * spikes) * b)[..., None]
    c = c + stars
    canvas[by:by + bh, bx:bx + bw, :3] = np.clip(c, 0, 1)
    canvas[by:by + bh, bx:bx + bw, 3] = 1.0

    # wisps: soft clouds tiling round, light to add over the window
    wx, wy, ww, wh = L.Space.WISPS
    w1 = fbm(ww, wh, 4, 2, 707, 6, wrap_x=True)
    w2 = fbm(ww, wh, 8, 4, 808, 4, wrap_x=True)
    vy = (np.arange(wh) + 0.5) / wh
    fade = np.sin(vy * math.pi)[:, None]
    k = smooth(0.5, 0.85, w1) * fade * 0.55
    wc = mix(col(120, 60, 220), col(60, 150, 230), w2) * k[..., None]
    canvas[wy:wy + wh, wx:wx + ww, :3] = np.clip(wc, 0, 1)
    canvas[wy:wy + wh, wx:wx + ww, 3] = 1.0
    save(canvas, out("gui", "fusion_space.png"))


# =================================================================== planets

def sphere(size, surface, limb=0.35):
    """A sphere's albedo (no light: its shade is laid over it later), its surface a function of longitude and
    latitude; darker toward the limb."""
    px, py = grid(size, size)
    r = size / 2.0 - 0.5
    x = (px - size / 2.0) / r
    y = (py - size / 2.0) / r
    rr = x * x + y * y
    inside = rr < 1.0
    z = np.sqrt(np.clip(1 - rr, 0, 1))
    lon = np.arctan2(x, z)
    lat = np.arcsin(np.clip(y, -1, 1))
    c = surface(lon, lat, x, y, z)
    c = c * (1 - limb * (1 - z) ** 1.5)[..., None]
    alpha = np.clip((1.0 - np.sqrt(rr)) * r + 0.5, 0, 1)
    return np.dstack([np.clip(c, 0, 1), alpha * inside + alpha * (~inside)])


def surface_noise(lon, lat, scale, seed, octaves=4):
    """Noise over a sphere's surface (as seen from the front: enough for a still picture)."""
    h, w = lon.shape
    u = (lon / math.pi + 1) / 2
    v = (lat / (math.pi / 2) + 1) / 2
    n = fbm(64, 64, scale, scale, seed, octaves)
    ui = np.clip((u * 63).astype(int), 0, 63)
    vi = np.clip((v * 63).astype(int), 0, 63)
    return n[vi, ui]


def giant(lon, lat, x, y, z):
    turb = surface_noise(lon, lat, 4, 11, 5)
    bands = np.sin(lat * 9.0 + turb * 3.2)
    t = 0.5 + 0.5 * bands
    palette = [col(244, 226, 190), col(214, 170, 118), col(226, 132, 72), col(150, 78, 52), col(136, 96, 176)]
    idx = np.clip(t * (len(palette) - 1), 0, len(palette) - 1.001)
    i0 = np.floor(idx).astype(int)
    f = (idx - i0)[..., None]
    pal = np.array(palette)
    c = pal[i0] + (pal[i0 + 1] - pal[i0]) * f
    # a great storm
    storm = np.exp(-(((lon - 0.5) / 0.32) ** 2 + ((lat + 0.32) / 0.13) ** 2))
    c = c + (col(255, 236, 214) - c) * (storm * 0.7)[..., None]
    c = c * (1 - 0.45 * np.exp(-(((lon - 0.5) / 0.2) ** 2 + ((lat + 0.32) / 0.07) ** 2)))[..., None]
    return c


def ocean(lon, lat, x, y, z):
    land = surface_noise(lon, lat, 3, 21, 5)
    clouds = surface_noise(lon * 1.3 + 0.4, lat, 4, 22, 5)
    sea = mix(col(16, 44, 118), col(40, 120, 190), smooth(0.2, 0.5, land))
    ground = mix(col(48, 120, 64), col(150, 130, 80), smooth(0.6, 0.75, land))
    c = np.where((land > 0.53)[..., None], ground, sea)
    ice = smooth(1.05, 1.35, np.abs(lat) + 0.2 * land)
    c = c + (col(240, 248, 255) - c) * ice[..., None]
    cl = smooth(0.55, 0.75, clouds) * 0.85
    return c + (col(250, 252, 255) - c) * cl[..., None]


def ice(lon, lat, x, y, z):
    n = surface_noise(lon, lat, 5, 31, 5)
    c = mix(col(150, 196, 230), col(232, 244, 255), smooth(0.3, 0.7, n))
    cracks = np.exp(-((n - 0.5) / 0.018) ** 2)
    c = c * (1 - 0.5 * cracks)[..., None]
    cap = smooth(1.0, 1.3, np.abs(lat))
    return c + (col(255, 255, 255) - c) * cap[..., None]


def lava(lon, lat, x, y, z):
    n = surface_noise(lon, lat, 5, 41, 5)
    c = mix(col(36, 24, 26), col(80, 46, 40), n)
    cracks = np.exp(-((n - 0.5) / 0.025) ** 2)
    return c + (col(255, 150, 50) - c) * cracks[..., None]


def moon(lon, lat, x, y, z):
    n = surface_noise(lon, lat, 6, 51, 4)
    c = mix(col(110, 108, 118), col(176, 174, 184), n)
    rng = np.random.default_rng(5)
    for _ in range(9):
        cx, cy, r = rng.uniform(-0.7, 0.7), rng.uniform(-0.7, 0.7), rng.uniform(0.08, 0.22)
        d = np.hypot(x - cx, y - cy) / r
        c = c * (1 - 0.3 * (d < 1.0))[..., None] + col(200, 198, 206) * (np.exp(-((d - 1.0) / 0.12) ** 2) * 0.35)[..., None]
    return c


def place(canvas, sprite, x, y):
    h, w = sprite.shape[:2]
    canvas[y:y + h, x:x + w] = sprite


def planets():
    s = L.Planets.SIZE
    canvas = np.zeros((s, s, 4))
    for (region, fn) in ((L.Planets.GIANT, giant), (L.Planets.OCEAN, ocean), (L.Planets.ICE, ice), (L.Planets.LAVA, lava),
                         (L.Planets.MOON, moon)):
        x, y, w, h = region
        place(canvas, sphere(w, fn), x, y)
    # the shade: night on the side away from the light (lit from -x), a soft terminator; the rim: a thin bright edge
    x0, y0, w, h = L.Planets.SHADE
    px, py = grid(w, h)
    r = w / 2.0 - 0.5
    nx = (px - w / 2.0) / r
    ny = (py - h / 2.0) / r
    rr = nx * nx + ny * ny
    nz = np.sqrt(np.clip(1 - rr, 0, 1))
    lam = (-nx * 0.94 + nz * 0.34)
    shade_a = (1 - smooth(-0.12, 0.4, lam)) * 0.9 * np.clip((1 - np.sqrt(rr)) * r + 0.5, 0, 1)
    canvas[y0:y0 + h, x0:x0 + w, :3] = col(4, 3, 10)
    canvas[y0:y0 + h, x0:x0 + w, 3] = shade_a
    x0, y0, w, h = L.Planets.RIM
    rim = smooth(0.72, 0.98, np.sqrt(rr)) * np.clip(-nx, 0, 1) ** 1.5 * np.clip((1 - np.sqrt(rr)) * r + 0.5, 0, 1)
    canvas[y0:y0 + h, x0:x0 + w, :3] = col(255, 228, 196) * rim[..., None]
    canvas[y0:y0 + h, x0:x0 + w, 3] = 1.0
    # the ring, split into the half behind the giant and the half in front
    x0, y0, w, h2 = L.Planets.RING_BACK
    hh = 2 * h2
    px, py = grid(w, hh)
    ex = (px - w / 2.0) / 62.0
    ey = (py - hh / 2.0) / 18.0
    e = np.sqrt(ex * ex + ey * ey)
    bands = 0.6 + 0.4 * np.sin(e * 46.0)
    ring_a = smooth(0.58, 0.62, e) * (1 - smooth(0.97, 1.0, e)) * bands * (1 - 0.7 * np.exp(-((e - 0.8) / 0.02) ** 2))
    ring_c = mix(col(236, 210, 170), col(170, 130, 200), smooth(0.6, 1.0, e))
    ring = np.dstack([np.broadcast_to(ring_c, (hh, w, 3)), ring_a * 0.9])
    canvas[y0:y0 + h2, x0:x0 + w] = ring[:h2]
    canvas[y0 + h2:y0 + hh, x0:x0 + w] = ring[h2:]
    # a spiral galaxy (light: colour is strength)
    x0, y0, w, h = L.Planets.GALAXY
    px, py = grid(w, h)
    dx, dy = (px - w / 2.0), (py - h / 2.0) * 1.6
    rr = np.hypot(dx, dy) + 1e-6
    th = np.arctan2(dy, dx)
    arms = 0.5 + 0.5 * np.cos(2 * th - np.log(rr) * 3.2)
    k = np.exp(-rr / 9.0) * (0.35 + 0.65 * arms ** 2) + 1.2 * np.exp(-rr * rr / 6.0)
    k = k * (1 - smooth(20, 31, rr))
    gc = mix(col(150, 180, 255), col(255, 236, 200), np.exp(-rr / 5.0))
    canvas[y0:y0 + h, x0:x0 + w, :3] = np.clip(gc * k[..., None], 0, 1)
    canvas[y0:y0 + h, x0:x0 + w, 3] = 1.0
    # a glint: a point with four rays
    x0, y0, w, h = L.Planets.GLINT
    px, py = grid(w, h)
    dx, dy = px - w / 2.0, py - h / 2.0
    k = np.exp(-(dx * dx + dy * dy) / 4.0) + 0.7 * (np.exp(-np.abs(dx) / 4.0) * np.exp(-(dy / 0.8) ** 2) + np.exp(-np.abs(dy) / 4.0)
                                                    * np.exp(-(dx / 0.8) ** 2))
    k = k * (1 - smooth(12, 16, np.hypot(dx, dy)))
    canvas[y0:y0 + h, x0:x0 + w, :3] = np.clip(k, 0, 1)[..., None]
    canvas[y0:y0 + h, x0:x0 + w, 3] = 1.0
    # a shooting star: head on the right, tail fading left
    x0, y0, w, h = L.Planets.STREAK
    px, py = grid(w, h)
    u = px / w
    k = u ** 2.2 * np.exp(-((py - h / 2.0) / (0.6 + 1.6 * u)) ** 2) + np.exp(-((px - w + 3) ** 2 + (py - h / 2.0) ** 2) / 3.0)
    canvas[y0:y0 + h, x0:x0 + w, :3] = np.clip(k, 0, 1)[..., None] * col(230, 220, 255)
    canvas[y0:y0 + h, x0:x0 + w, 3] = 1.0
    # an atmosphere: a ring of light just round a planet's edge
    x0, y0, w, h = L.Planets.ATMOS
    px, py = grid(w, h)
    rr = np.hypot(px - w / 2.0, py - h / 2.0) / (w / 2.0)
    k = np.exp(-((rr - 0.78) / 0.1) ** 2) * (1 - smooth(0.92, 1.0, rr))
    canvas[y0:y0 + h, x0:x0 + w, :3] = np.clip(k, 0, 1)[..., None]
    canvas[y0:y0 + h, x0:x0 + w, 3] = 1.0
    # a soft glow
    x0, y0, w, h = L.Planets.GLOW
    px, py = grid(w, h)
    rr = np.hypot(px - w / 2.0, py - h / 2.0) / (w / 2.0)
    k = np.exp(-rr * rr * 4.2) * (1 - smooth(0.85, 1.0, rr))
    canvas[y0:y0 + h, x0:x0 + w, :3] = k[..., None]
    canvas[y0:y0 + h, x0:x0 + w, 3] = 1.0
    save(canvas, out("effect", "planets.png"))


# =================================================================== widgets

def porthole(rim_lo, rim_hi, glow=None):
    s = 44
    px, py = grid(s, s)
    c = s / 2.0
    d = np.hypot(px - c, py - c)
    ang = np.arctan2(py - c, px - c)
    lit = 0.5 + 0.5 * np.cos(ang + 2.36)
    canvas = np.zeros((s, s, 4))
    glass = mix(col(22, 16, 38), col(6, 5, 12), smooth(0, 17, d))
    if glow is not None:
        glass = glass + glow * (np.exp(-((d - 15) / 3.0) ** 2) * 0.55)[..., None]
    over(canvas, layer(s, s, glass, np.clip(17.5 - d, 0, 1)))
    ring = np.clip(np.minimum(d - 16.2, 21.0 - d) + 0.5, 0, 1)
    over(canvas, layer(s, s, mix(rim_lo, rim_hi, lit), ring))
    over(canvas, layer(s, s, col(255, 255, 255), np.exp(-((d - 19.5) / 0.6) ** 2) * np.clip(np.cos(ang + 2.36), 0, 1) * 0.6))
    over(canvas, layer(s, s, OUTLINE, np.clip(1.0 - np.abs(d - 21.4) / 0.8, 0, 1) * 0.9))
    over(canvas, layer(s, s, OUTLINE, np.clip(1.0 - np.abs(d - 16.4) / 0.7, 0, 1) * 0.8))
    return canvas


def port_gloss():
    s = 44
    px, py = grid(s, s)
    c = s / 2.0
    d = np.hypot(px - c, py - c)
    # a crescent of reflection on the glass, up and left
    shine = np.exp(-(((px - c + 5) / 9.0) ** 2 + ((py - c + 7) / 5.0) ** 2)) * (d < 15.5)
    canvas = np.zeros((s, s, 4))
    canvas[..., :3] = 1.0
    canvas[..., 3] = shine * 0.32
    return canvas


def orb(state):
    s = 48
    px, py = grid(s, s)
    c = s / 2.0
    d = np.hypot(px - c, py - c)
    cores = [(col(176, 110, 255), col(60, 20, 120)), (col(220, 170, 255), col(100, 40, 180)), (col(110, 104, 124), col(30, 28, 38)),
             (col(250, 236, 255), col(150, 90, 255))]
    hi, lo = cores[state]
    canvas = np.zeros((s, s, 4))
    body = mix(hi, lo, smooth(0, 19, np.hypot(px - c + 3, py - c + 3)))
    over(canvas, layer(s, s, body, np.clip(19.5 - d, 0, 1)))
    # its glyph: a play arrow, or a spiral while it fuses
    if state < 3:
        tri = (px > c - 5) & (np.abs(py - c) < (c + 8 - px) * 0.62) & (px < c + 8)
        gl = [GOLD_HI, col(255, 246, 220), col(80, 76, 90)][state]
        over(canvas, layer(s, s, gl, tri.astype(float) * 0.95))
    else:
        th = np.arctan2(py - c, px - c)
        spiral = np.cos(2 * th - np.log(d + 1) * 5.0) > 0.6
        over(canvas, layer(s, s, col(255, 255, 255), (spiral & (d < 13) & (d > 2)).astype(float) * 0.85))
    shine = np.exp(-(((px - c + 6) / 7.0) ** 2 + ((py - c + 8) / 4.0) ** 2))
    over(canvas, layer(s, s, col(255, 255, 255), shine * 0.55 * (d < 19)))
    ang = np.arctan2(py - c, px - c)
    lit = 0.5 + 0.5 * np.cos(ang + 2.36)
    ring = np.clip(np.minimum(d - 19.0, 22.5 - d) + 0.5, 0, 1)
    rim_lo, rim_hi = (GOLD_DK, GOLD_HI) if state != 2 else (col(40, 38, 50), col(120, 116, 136))
    over(canvas, layer(s, s, mix(rim_lo, rim_hi, lit), ring))
    over(canvas, layer(s, s, OUTLINE, np.clip(1.0 - np.abs(d - 23.0) / 0.8, 0, 1)))
    return canvas


def orb_ring():
    s = 64
    px, py = grid(s, s)
    c = s / 2.0
    d = np.hypot(px - c, py - c)
    ang = np.arctan2(py - c, px - c)
    canvas = np.zeros((s, s, 4))
    ring = np.clip(1.0 - np.abs(d - 28.5) / 1.1, 0, 1)
    over(canvas, layer(s, s, GOLD, ring * 0.9))
    ticks = (np.abs(np.sin(ang * 8)) > 0.9) & (d > 25.5) & (d < 31)
    over(canvas, layer(s, s, GOLD_HI, ticks.astype(float)))
    for k in range(3):
        a = k * 2 * math.pi / 3
        gem(canvas, px, py, c + 28.5 * math.cos(a), c + 28.5 * math.sin(a), 2.6, rim=False)
    return canvas


def tube():
    w, h = L.Widgets.TUBE[2:]
    px, py = grid(w, h)
    d = sd_round_rect(px, py, 1, 1, w - 1, h - 1, 10)
    inner = sd_round_rect(px, py, 4, 4, w - 4, h - 4, 8)
    canvas = np.zeros((h, w, 4))
    nx, ny = gradient(d)
    lit = np.clip(0.5 + 0.5 * -(nx * LIGHT[0] + ny * LIGHT[1]) * -1, 0, 1)
    wall = np.clip(np.minimum(-d, inner) + 0.5, 0, 1)
    over(canvas, layer(h, w, mix(col(30, 26, 46), col(120, 110, 160), lit), wall))
    over(canvas, layer(h, w, OUTLINE, np.clip(1.0 - np.abs(d) / 1.0, 0, 1)))
    # the glass over the plasma: a strip of reflection down its left, its right a little dark
    glass_in = np.clip(0.5 - inner, 0, 1)
    strip = np.exp(-((px - 8.5) / 1.6) ** 2) * 0.42 + np.exp(-((px - 12) / 0.8) ** 2) * 0.12
    over(canvas, layer(h, w, col(255, 255, 255), glass_in * strip))
    over(canvas, layer(h, w, col(0, 0, 0), glass_in * smooth(w - 9, w - 4, px) * 0.35))
    # ticks along its right
    for k in range(1, 10):
        y = 4 + k * (h - 8) / 10.0
        tick = (np.abs(py - y) < 0.8) & (px > w - 9) & (px < w - 5)
        over(canvas, layer(h, w, col(200, 190, 240), tick.astype(float) * 0.45))
    return canvas


def plasma(stops):
    w, h = 20, 244
    px, py = grid(w, h)
    t = 1 - py / h
    pal = np.array([s_[1] for s_ in stops])
    ts = np.array([s_[0] for s_ in stops])
    c = np.zeros((h, w, 3))
    for ch in range(3):
        c[..., ch] = np.interp(t, ts, pal[:, ch])
    cyl = np.cos((px / w - 0.5) * math.pi * 0.9)
    c = c * (0.55 + 0.45 * cyl)[..., None]
    c = c + (col(255, 255, 255) - c) * (np.exp(-((px - 6) / 1.5) ** 2) * 0.25)[..., None]
    return np.dstack([np.clip(c, 0, 1), np.ones((h, w))])


def flow():
    w, h = L.Widgets.FLOW[2:]
    n = fbm(w, h, 2, 6, 909, 4, wrap_y=True)
    m = fbm(w, h, 4, 12, 919, 3, wrap_y=True)
    k = smooth(0.55, 0.8, n) * 0.7 + smooth(0.6, 0.85, m) * 0.4
    return np.dstack([np.clip(k, 0, 1)[..., None] * np.ones((h, w, 3)), np.ones((h, w))])


def hologram():
    w, h = L.Widgets.HOLOGRAM[2:]
    px, py = grid(w, h)
    d = sd_round_rect(px, py, 1, 1, w - 1, h - 1, 7)
    inside = np.clip(0.5 - d, 0, 1)
    canvas = np.zeros((h, w, 4))
    fill = mix(col(16, 22, 46), col(6, 8, 20), py / h)
    scan = (np.floor(py) % 4 == 0) * 0.08
    fill = fill + (col(120, 160, 255) - fill) * scan[..., None]
    over(canvas, layer(h, w, fill, inside * 0.82))
    glow_top = np.exp(-((py - 3) / 2.5) ** 2) * inside
    over(canvas, layer(h, w, col(150, 190, 255), glow_top * 0.25))
    border = np.clip(1.0 - np.abs(d + 1.2) / 1.0, 0, 1)
    over(canvas, layer(h, w, mix(col(110, 80, 230), col(110, 200, 255), px / w), border * 0.8))
    for (cx, cy, sx, sy) in ((3, 3, 1, 1), (w - 4, 3, -1, 1), (3, h - 4, 1, -1), (w - 4, h - 4, -1, -1)):
        br = ((np.abs(py - cy) < 1.0) & ((px - cx) * sx >= 0) & ((px - cx) * sx < 10)) | \
             ((np.abs(px - cx) < 1.0) & ((py - cy) * sy >= 0) & ((py - cy) * sy < 7))
        over(canvas, layer(h, w, col(190, 230, 255), br.astype(float)))
    return canvas


def gloss():
    w, h = L.Widgets.GLOSS[2:]
    px, py = grid(w, h)
    u = (px - w / 2.0) + (py - h / 2.0) * 0.8
    k = np.exp(-(u / 6.0) ** 2) * np.sin(py / h * math.pi)
    return np.dstack([k[..., None] * np.ones((h, w, 3)), np.ones((h, w))])


def arrow():
    w, h = L.Widgets.ARROW[2:]
    px, py = grid(w, h)
    cy = h / 2.0
    shaft = (px < 28) & (np.abs(py - cy) < 4.5)
    head = (px >= 24) & (np.abs(py - cy) < (w - px) * 0.75)
    m = (shaft | head).astype(float)
    canvas = np.zeros((h, w, 4))
    c = mix(VIOLET, VIOLET_WHITE, px / w)
    c = c + (col(255, 255, 255) - c) * (np.exp(-((py - cy) / 1.5) ** 2) * 0.5)[..., None]
    over(canvas, layer(h, w, c, m))
    return canvas


def sparkle():
    s = 16
    px, py = grid(s, s)
    dx, dy = px - s / 2.0, py - s / 2.0
    k = np.exp(-(dx * dx + dy * dy) / 2.0) + 0.6 * (np.exp(-np.abs(dx) / 2.2) * np.exp(-(dy / 0.6) ** 2) + np.exp(-np.abs(dy) / 2.2)
                                                    * np.exp(-(dx / 0.6) ** 2))
    k = k * (1 - smooth(6, 8, np.hypot(dx, dy)))
    return np.dstack([np.clip(k, 0, 1)[..., None] * np.ones((s, s, 3)), np.ones((s, s))])


def bloom():
    s = 64
    px, py = grid(s, s)
    rr = np.hypot(px - s / 2.0, py - s / 2.0) / (s / 2.0)
    k = np.exp(-rr * rr * 4.0) * (1 - smooth(0.85, 1.0, rr))
    return np.dstack([k[..., None] * np.ones((s, s, 3)), np.ones((s, s))])


def jei_frame():
    w, h = L.Widgets.JEI_FRAME[2:]
    px, py = grid(w, h)
    d = sd_round_rect(px, py, 0, 0, w, h, 12)
    win = sd_round_rect(px, py, 9, 9, w - 9, h - 9, 8)
    canvas = np.zeros((h, w, 4))
    t1 = np.maximum(-d, 0)
    t2 = np.maximum(win, 0)
    band = t1 / (t1 + t2 + 1e-6)
    onx, ony = gradient(d)
    lit = np.clip(0.5 + 0.5 * (onx * LIGHT[0] + ony * LIGHT[1]), 0, 1)
    c = mix(METAL_BOTTOM, METAL_TOP, lit)
    gold_t = smooth(0.38, 0.45, band) * (1 - smooth(0.6, 0.67, band))
    c = c + (mix(GOLD_DK, GOLD_HI, lit) - c) * gold_t[..., None]
    c = c * (1 - 0.85 * (1 - smooth(0.0, 0.12, band)))[..., None]
    c = c * (1 - 0.8 * smooth(0.88, 0.98, band))[..., None]
    a = np.clip(0.5 - d, 0, 1) * np.clip(0.5 + win, 0, 1)
    over(canvas, layer(h, w, c, a))
    shadow = np.clip(0.5 - win, 0, 1) * 0.55 * (1 - smooth(0, 14, -win))
    over(canvas, layer(h, w, OUTLINE * 0.3, shadow))
    for (cx, cy) in ((9, 9), (w - 9, 9), (9, h - 9), (w - 9, h - 9)):
        gem(canvas, px, py, cx, cy, 5)
    return canvas


def widgets():
    s = L.Widgets.SIZE
    canvas = np.zeros((s, s, 4))

    def put(region, sprite):
        x, y = region[0], region[1]
        hh, ww = sprite.shape[:2]
        canvas[y:y + hh, x:x + ww] = sprite
    put(L.Widgets.PORT_STEEL, porthole(col(44, 40, 60), col(170, 160, 210)))
    put(L.Widgets.PORT_VIOLET, porthole(col(70, 30, 130), col(210, 170, 255), glow=col(150, 80, 255)))
    put(L.Widgets.PORT_GOLD, porthole(GOLD_DK, GOLD_HI, glow=col(255, 190, 90)))
    put(L.Widgets.PORT_GLOSS, port_gloss())
    for state in range(4):
        x, y = L.Widgets.ORB[0] + 48 * state, L.Widgets.ORB[1]
        put((x, y), orb(state))
    put(L.Widgets.ORB_RING, orb_ring())
    put(L.Widgets.BLOOM, bloom())
    put(L.Widgets.TUBE, tube())
    put(L.Widgets.PLASMA_VIOLET, plasma([(0.0, col(34, 8, 70)), (0.45, col(110, 44, 200)), (0.8, col(196, 140, 255)), (1.0, col(244, 230, 255))]))
    put(L.Widgets.PLASMA_GOLD, plasma([(0.0, col(80, 36, 8)), (0.5, col(214, 146, 54)), (0.85, col(255, 222, 148)), (1.0, col(255, 248, 230))]))
    put(L.Widgets.FLOW, flow())
    put(L.Widgets.HOLOGRAM, hologram())
    put(L.Widgets.GLOSS, gloss())
    put(L.Widgets.ARROW, arrow())
    put(L.Widgets.SPARKLE, sparkle())
    put(L.Widgets.JEI_FRAME, jei_frame())
    save(canvas, out("gui", "fusion_widgets.png"))


def main():
    for f in (frame, space, planets, widgets):
        f()
        print("drew " + f.__name__)


if __name__ == "__main__":
    main()
