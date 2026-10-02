"""The drawing kit of the machines' second-generation GUIs (every machine its own look): colour ramps and
gradients, value noise, signed-distance shapes lit from the top-left (raised or sunk, with bevels, outlines
and drop shadows), materials (stone, metal, glass, night sky...), pixel glyphs.

Shapes are signed distance functions f(px, py) -> distance from the shape's edge at the point (negative
inside); a pixel's middle is (x + 0.5, y + 0.5). Colours are (r, g, b) tuples.
"""
import colorsys
import math

from pix import hexc, rnd, rnd2  # noqa: F401

LIGHT = (-0.62, -0.78)          # where the light comes from (top-left), as every panel of the mod is lit


# ---------------------------------------------------------------- colour

def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def lerp(a, b, t):
    t = clamp(t)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(stops, t):
    """A colour on a gradient of stops [(t0, colour), (t1, colour), ...] (t ascending)."""
    if t <= stops[0][0]:
        return stops[0][1]
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return lerp(c0, c1, (t - t0) / float(t1 - t0) if t1 > t0 else 1.0)
    return stops[-1][1]


def even(*colours):
    """Stops spread evenly from 0 to 1."""
    n = len(colours) - 1
    return [(i / float(n), hexc(c) if isinstance(c, str) else c) for i, c in enumerate(colours)]


def light(c, k):
    """Lighter towards white by k (0..1)."""
    return lerp(c, (255, 255, 255), k)


def dark(c, k):
    return lerp(c, (0, 0, 0), k)


def tint(c, k):
    """k > 0 lightens, k < 0 darkens."""
    return light(c, k) if k > 0 else dark(c, -k)


def hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, clamp(s), clamp(v))
    return int(round(r * 255)), int(round(g * 255)), int(round(b * 255))


def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255.0


BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def bayer(x, y):
    return (BAYER[y & 3][x & 3] + 0.5) / 16.0


# ---------------------------------------------------------------- noise

def noise(x, y, scale, salt):
    """Smooth value noise in [0, 1)."""
    fx, fy = x / float(scale), y / float(scale)
    x0, y0 = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b = rnd2(x0, y0, salt), rnd2(x0 + 1, y0, salt)
    c, d = rnd2(x0, y0 + 1, salt), rnd2(x0 + 1, y0 + 1, salt)
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def fbm(x, y, scale, salt, octaves=3):
    """Noise of a few octaves, in [0, 1)."""
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total += noise(x, y, scale / (2 ** o), salt + o * 17) * amp
        norm += amp
        amp *= 0.5
    return total / norm


def vein(x, y, scale, salt, width=0.035):
    """1 on thin wandering lines (marble veins, cracks), 0 elsewhere, soft between."""
    n = fbm(x, y, scale, salt, 3)
    return clamp(1.0 - abs(n - 0.5) / width)


# ---------------------------------------------------------------- shapes (signed distance)

def circle(cx, cy, r):
    return lambda px, py: math.hypot(px - cx, py - cy) - r


def ellipse(cx, cy, rx, ry):
    def f(px, py):
        dx, dy = (px - cx) / rx, (py - cy) / ry
        k = math.hypot(dx, dy)
        return (k - 1.0) * min(rx, ry)
    return f


def box(x1, y1, x2, y2, r=0.0):
    """The rectangle [x1, x2] x [y1, y2] (pixel edges), corners rounded by r."""
    cx, cy, hx, hy = (x1 + x2) / 2.0, (y1 + y2) / 2.0, (x2 - x1) / 2.0, (y2 - y1) / 2.0

    def f(px, py):
        qx, qy = abs(px - cx) - hx + r, abs(py - cy) - hy + r
        return math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - r
    return f


def polygon(cx, cy, r, n, start_deg=0.0):
    """A regular polygon of n sides with inradius r (the first side's normal at start_deg)."""
    normals = [(math.cos(math.radians(start_deg + k * 360.0 / n)), math.sin(math.radians(start_deg + k * 360.0 / n))) for k in range(n)]

    def f(px, py):
        return max((px - cx) * nx + (py - cy) * ny for (nx, ny) in normals) - r
    return f


def segment(x1, y1, x2, y2, w):
    """A capsule of half-width w round the segment."""
    def f(px, py):
        dx, dy = x2 - x1, y2 - y1
        t = clamp(((px - x1) * dx + (py - y1) * dy) / float(dx * dx + dy * dy or 1.0))
        return math.hypot(px - x1 - dx * t, py - y1 - dy * t) - w
    return f


def arch(x1, y1, x2, y2):
    """A round-topped arch: the rectangle [x1, x2] x [y1 + half-width, y2] under a half circle."""
    r = (x2 - x1) / 2.0
    cx, top = (x1 + x2) / 2.0, y1 + r
    body = box(x1, top, x2, y2)
    head = circle(cx, top, r)
    return lambda px, py: min(body(px, py), head(px, py))


def union(*fs):
    return lambda px, py: min(f(px, py) for f in fs)


def intersect(*fs):
    return lambda px, py: max(f(px, py) for f in fs)


def subtract(a, b):
    return lambda px, py: max(a(px, py), -b(px, py))


def shell(f, w):
    """The band of half-width w round the shape's edge (a ring from a circle)."""
    return lambda px, py: abs(f(px, py)) - w


def offset(f, d):
    return lambda px, py: f(px, py) - d


def moved(f, dx, dy):
    return lambda px, py: f(px - dx, py - dy)


def normal(f, px, py):
    """The outward unit normal of the shape at the point."""
    gx = f(px + 0.5, py) - f(px - 0.5, py)
    gy = f(px, py + 0.5) - f(px, py - 0.5)
    n = math.hypot(gx, gy)
    return (gx / n, gy / n) if n > 1e-9 else (0.0, 0.0)


def facing(f, px, py):
    """How much the shape's edge there faces the light: 1 lit, -1 in shadow."""
    nx, ny = normal(f, px, py)
    return nx * LIGHT[0] + ny * LIGHT[1]


def bounds(f, x1, y1, x2, y2):
    """The pixels of [x1, x2) x [y1, y2) inside the shape: (x, y, depth) with depth > 0."""
    for y in range(int(math.floor(y1)), int(math.ceil(y2))):
        for x in range(int(math.floor(x1)), int(math.ceil(x2))):
            d = -f(x + 0.5, y + 0.5)
            if d > 0:
                yield x, y, d


def draw(cv, f, area, fill, outline=None, bevel=2.0, hi=0.32, lo=0.38, sunk=False, outline_w=1.0, edge=None):
    """Fills the shape with fill(x, y, depth) (or a colour), outlined, its edge bevelled: raised (lit on the
    top-left) or sunk (lit on the bottom-right). edge(x, y, k) may colour the bevel itself (k = facing)."""
    x1, y1, x2, y2 = area
    for x, y, depth in bounds(f, x1, y1, x2, y2):
        if outline is not None and depth <= outline_w:
            cv.set(x, y, outline)
            continue
        c = fill(x, y, depth) if callable(fill) else fill
        b = depth - (outline_w if outline is not None else 0.0)
        if bevel > 0 and b <= bevel:
            k = facing(f, x + 0.5, y + 0.5)
            if sunk:
                k = -k
            if edge is not None:
                c = edge(x, y, k)
            else:
                c = light(c, hi * k) if k > 0 else dark(c, -lo * k)
        cv.set(x, y, c)


def shadow(cv, f, area, dx=1, dy=2, alpha=110, colour=(0, 0, 0), soft=1.5):
    """A soft shadow the shape casts down and to the right (drawn before the shape)."""
    x1, y1, x2, y2 = area
    for y in range(int(y1), int(y2) + dy + 3):
        for x in range(int(x1), int(x2) + dx + 3):
            d = f(x + 0.5 - dx, y + 0.5 - dy)
            if d < soft:
                a = alpha * clamp((soft - d) / (2 * soft))
                if a > 2:
                    cv.set(x, y, colour, int(a))


def glow(cv, f, area, colour, reach=3.0, alpha=120):
    """A soft halo round the outside of the shape."""
    x1, y1, x2, y2 = area
    r = int(math.ceil(reach)) + 1
    for y in range(int(y1) - r, int(y2) + r):
        for x in range(int(x1) - r, int(x2) + r):
            d = f(x + 0.5, y + 0.5)
            if 0 < d < reach:
                a = alpha * (1.0 - d / reach) ** 2
                cv.set(x, y, colour, int(a))


# ---------------------------------------------------------------- lines and glyphs

def thick_line(cv, x1, y1, x2, y2, w, colour, alpha=255):
    f = segment(x1, y1, x2, y2, w)
    lo_x, hi_x = min(x1, x2) - w - 1, max(x1, x2) + w + 1
    lo_y, hi_y = min(y1, y2) - w - 1, max(y1, y2) + w + 1
    for x, y, d in bounds(f, lo_x, lo_y, hi_x + 1, hi_y + 1):
        cv.set(x, y, colour, alpha if d >= 0.5 else int(alpha * (0.5 + d)))


def ring_line(cv, cx, cy, r, colour, w=0.5, alpha=255, skip=None):
    f = shell(circle(cx, cy, r), w)
    for x, y, d in bounds(f, cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2):
        if skip is not None and skip(x, y):
            continue
        cv.set(x, y, colour, alpha)


def glyph(cv, rows, x, y, colour, alpha=255, carve=None):
    """A pixel glyph ('#' set): carve=(dark, lit) draws it cut into the surface: dark, with its lower edge lit."""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != "#":
                continue
            if carve is not None:
                cv.set(x + i, y + j, carve[0], alpha)
                below = j + 1 < len(rows) and rows[j + 1][i] == "#"
                if not below:
                    cv.set(x + i, y + j + 1, carve[1], alpha // 2)
            else:
                cv.set(x + i, y + j, colour, alpha)


def glyph_glow(cv, rows, x, y, core=(255, 255, 255), halo=(255, 255, 255), halo_alpha=70):
    """A glyph lit: a white core and a one-pixel halo round it (the screens tint it)."""
    h, w = len(rows), len(rows[0])
    on = {(i, j) for j in range(h) for i in range(w) if rows[j][i] == "#"}
    for (i, j) in on:
        for (di, dj) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (i + di, j + dj) not in on:
                cv.set(x + i + di, y + j + dj, halo, halo_alpha)
    for (i, j) in on:
        cv.set(x + i, y + j, core)


# ---------------------------------------------------------------- slots

def slot(cv, x, y, rim, inside, frame=None, corner=1, sunk_depth=1, mark=None, mark_colour=None, mark_alpha=60):
    """An 18x18 slot round the item's 16x16 at (x, y): rim = (lit, mid, shadow) of its 1-pixel bevel, inside =
    (top, bottom) of the hollow's gradient, frame = an outline round it all, corner = how round its corners
    are; mark = a 9x9 glyph faint in the hollow (what goes there)."""
    lit, mid, shade = rim
    top, bottom = inside
    x1, y1, x2, y2 = x - 1, y - 1, x + 17, y + 17
    if frame is not None:
        f = box(x1 - 1, y1 - 1, x2 + 1, y2 + 1, corner + 1)
        for px, py, d in bounds(f, x1 - 1, y1 - 1, x2 + 1, y2 + 1):
            if d <= 1.0:
                cv.set(px, py, frame)
    f = box(x1, y1, x2, y2, corner)
    for px, py, d in bounds(f, x1, y1, x2, y2):
        if d <= 1.0:
            k = facing(f, px + 0.5, py + 0.5)
            cv.set(px, py, shade if k > 0.3 else (lit if k < -0.3 else mid))     # sunk: lit on the bottom-right
        else:
            t = (py - y) / 15.0
            c = lerp(top, bottom, t)
            if py == y or px == x:
                c = dark(c, 0.35)                                                  # the hollow's own shadow
            cv.set(px, py, c)
    if mark is not None:
        glyph(cv, mark, x + 4, y + 4, mark_colour or light(top, 0.4), mark_alpha)
