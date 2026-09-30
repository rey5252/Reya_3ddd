"""Curly leaf vines for the GUI: shapes are drawn as vectors, then turned into two-tone pixel art.

A vine is a *walk*: a path a turtle takes, turning by kappa(s) radians per pixel, so a stem can wave
along an edge and wind up into a curl at its tip. Strokes (stems, leaves, tendrils) are tapered discs
laid along a walk; `paint` rasterises them (8x supersampled, so the shapes are smooth), outlines them and
shades them from the top-left in two or three greens, the light side always the one that faces the light,
whichever way the stroke runs. Later strokes cover earlier ones, with their own dark outline between.

Coordinates are the GUI's pixels (floats); rotations are in radians, 0 = right, pi/2 = down.
"""
import math

from PIL import Image, ImageDraw

SS = 8
LIGHT = (-0.62, -0.78)               # the direction the light comes from (up and a little left)
STEP = 0.25


# ---------------------------------------------------------------- walks

def walk(x, y, theta, length, kappa=0.0, step=STEP):
    """The path from (x, y) heading theta for `length` pixels, turning kappa(s) radians per pixel
    (a number or a function of the distance walked). Returns [(x, y, theta, s)]."""
    k = kappa if callable(kappa) else (lambda s: kappa)
    out = []
    s = 0.0
    while s <= length + 1e-9:
        out.append((x, y, theta, s))
        theta += k(s) * step
        x += math.cos(theta) * step
        y += math.sin(theta) * step
        s += step
    return out


def wave(amp, period, phase=0.0):
    """Turning that makes a walk sway about its heading: the heading swings by +-amp radians, one
    wave per `period` pixels."""
    return lambda s: amp * math.tau / period * math.cos((s / period + phase) * math.tau)


def curl(length, turn, power=1.7):
    """Turning that winds a walk up: none at first, then tighter and tighter (an Euler spiral), `turn`
    radians in all (positive = clockwise on the screen)."""
    k1 = turn * (power + 1.0) / length
    return lambda s: k1 * (s / length) ** power


def both(*fs):
    return lambda s: sum(f(s) for f in fs)


def joined(first, second):
    """A walk continued by another one starting where the first stopped (same heading)."""
    return first + second


def taper(path, r0, r1=None, q=1.0):
    """A path with half-widths: r0 at the start easing to r1 at the end (power q).
    Returns [(x, y, r, theta)]."""
    n = len(path) - 1
    out = []
    for i, (x, y, th, s) in enumerate(path):
        u = i / n if n else 0.0
        if r1 is None:
            r = r0
        else:
            r = r0 + (r1 - r0) * u ** q
        out.append((x, y, r, th))
    return out


def blade(base, angle, length, width, bend=0.0, fat=0.72, tip=0.9):
    """A leaf: a pointed blade from `base` at `angle`, `length` long and `width` wide at its widest,
    curving `bend` radians over its length. Returns samples [(x, y, r, theta)]."""
    path = walk(base[0], base[1], angle, length, bend / length if length else 0.0)
    n = len(path) - 1
    out = []
    for i, (x, y, th, s) in enumerate(path):
        u = i / n
        r = width / 2.0 * math.sin(math.pi * u ** fat) ** tip
        out.append((x, y, r, th))
    return out


def at(path, s):
    """The walk's point nearest to distance s along it: (x, y, theta)."""
    best = min(path, key=lambda p: abs(p[3] - s))
    return best[0], best[1], best[2]


# ---------------------------------------------------------------- transforms

def mirrored(samples, fx=False, fy=False, cx=0.0, cy=0.0):
    """The samples mirrored about the vertical line x = cx (fx) and/or the horizontal line y = cy (fy)."""
    out = []
    for (x, y, r, th) in samples:
        if fx:
            x = 2 * cx - x
            th = math.pi - th
        if fy:
            y = 2 * cy - y
            th = -th
        out.append((x, y, r, th))
    return out


def shifted(samples, dx, dy):
    return [(x + dx, y + dy, r, th) for (x, y, r, th) in samples]


# ---------------------------------------------------------------- rasterising

def coverage(samples):
    """{(px, py): fraction of the pixel covered} by the union of the discs along the samples."""
    if not samples:
        return {}
    x0 = int(math.floor(min(x - r for x, y, r, th in samples))) - 1
    y0 = int(math.floor(min(y - r for x, y, r, th in samples))) - 1
    x1 = int(math.ceil(max(x + r for x, y, r, th in samples))) + 2
    y1 = int(math.ceil(max(y + r for x, y, r, th in samples))) + 2
    w, h = x1 - x0, y1 - y0
    img = Image.new("L", (w * SS, h * SS), 0)
    dr = ImageDraw.Draw(img)
    for (x, y, r, th) in samples:
        if r <= 0.02:
            continue
        cx, cy = (x - x0) * SS, (y - y0) * SS
        dr.ellipse([cx - r * SS, cy - r * SS, cx + r * SS - 1, cy + r * SS - 1], fill=255)
    small = img.resize((w, h), Image.BOX).load()
    return {(x0 + i, y0 + j): small[i, j] / 255.0 for j in range(h) for i in range(w) if small[i, j]}


def shape_of(samples, thr=0.5, min_r=0.0):
    if min_r:
        samples = [(x, y, max(r, min_r) if r > 0.02 else r, th) for (x, y, r, th) in samples]
    cov = coverage(samples)
    return {p for p, c in cov.items() if c >= thr}, samples


def lit_sides(samples):
    """+1/-1 per sample: which of the stroke's sides (its left normal, or the opposite) faces the light.
    Kept from flipping while the stroke runs nearly along the light."""
    side = 1
    out = []
    for (x, y, r, th) in samples:
        d = -math.sin(th) * LIGHT[0] + math.cos(th) * LIGHT[1]
        if abs(d) > 0.3:
            side = 1 if d > 0 else -1
        out.append(side)
    return out


def shade_shape_pixels(cv, shape, samples, tones, hi_at=0.55, keep=None):
    """Colours a rasterised stroke's pixels in `tones` = (light, dark) or (highlight, light, dark):
    the side facing the light is the light one."""
    sides = lit_sides(samples)
    three = len(tones) == 3
    for (x, y) in shape:
        px, py = x + 0.5, y + 0.5
        best, bi = -1e9, 0
        for i, (sx, sy, sr, th) in enumerate(samples):
            depth = sr - math.hypot(px - sx, py - sy)
            if depth > best:
                best, bi = depth, i
        sx, sy, sr, th = samples[bi]
        lat = ((px - sx) * -math.sin(th) + (py - sy) * math.cos(th)) * sides[bi]
        if three:
            c = tones[0] if sr >= 1.5 and lat > hi_at * sr else (tones[1] if lat > 0.0 else tones[2])
        else:
            c = tones[0] if lat > 0.0 else tones[1]
        if keep is None or (x, y) not in keep:
            cv.set(x, y, c)


def outline_of(cv, shape, outline, keep=None):
    """Paints the pixels just outside the shape (4-neighbours) in the outline colour."""
    ring = set()
    for (x, y) in shape:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in shape:
                ring.add(p)
    for (x, y) in ring:
        if keep is None or (x, y) not in keep:
            cv.set(x, y, outline)
    return ring


def stroke(cv, samples, tones, outline, thr=0.5, min_r=0.0, hi_at=0.55, keep=None):
    """A stroke with its outline: the outline first (over whatever is there), then the shaded inside.
    Returns the set of pixels it covers."""
    shape, samples = shape_of(samples, thr, min_r)
    if not shape:
        return set()
    outline_of(cv, shape, outline, keep)
    shade_shape_pixels(cv, shape, samples, tones, hi_at, keep)
    return shape


# ---------------------------------------------------------------- ready-made parts
# A part is a *spec*: (samples, tones, thr, min_r). Specs are painted in order, so a later one covers an
# earlier one; `paint_specs` can mirror a whole group, so a corner is drawn once and used for all four.

def spec(samples, tones, thr=0.5, min_r=0.0):
    return (samples, tones, thr, min_r)


def chain_specs(path, s0, s1, tones, length=15.0, pitch=8.5, r=2.2, seed=0):
    """A vine of overlapping pointed blades along the path between the distances s0 and s1: each blade
    swells and tapers, the next one starts before it ends."""
    from pix import rnd
    out = []
    s = s0
    k = 0
    while s < s1:
        seg = [p for p in path if s <= p[3] <= min(s + length, s1 + length * 0.4)]
        if len(seg) < 4:
            break
        n = len(seg) - 1
        rr = r * (0.86 + 0.28 * rnd(k, 3 + seed))
        samples = [(x, y, rr * math.sin(math.pi * (i / n) ** 0.85) ** 0.9, th) for i, (x, y, th, ss) in enumerate(seg)]
        out.append(spec(samples, tones))
        s += pitch * (0.9 + 0.2 * rnd(k, 11 + seed))
        k += 1
    return out


def curl_spec(x, y, theta, length, turn, r0, r1, tones, power=1.6):
    """A tendril from (x, y): straight at first, winding up into a curl (`turn` radians, positive =
    clockwise on the screen), thick to thin."""
    p = walk(x, y, theta, length, curl(length, turn, power))
    return spec(taper(p, r0, r1, 1.0), tones, thr=0.4, min_r=0.55)


def hook_spec(path, s, side, length, turn, tones, r0=1.05, r1=0.5, lean=1.0, power=1.4):
    """A small tendril leaving the path at distance s, leaning `lean` radians to `side` (+1 clockwise,
    -1 anticlockwise) and curling the same way."""
    x, y, th = at(path, s)
    return curl_spec(x, y, th + side * lean, length, side * turn, r0, r1, tones, power)


def crescent_spec(x, y, theta, radius, sweep, r, tones, side=1):
    """A crescent blade: an arc of `radius` starting at (x, y) heading theta, turning `side` * sweep
    radians, swelling in the middle."""
    length = radius * sweep
    p = walk(x, y, theta, length, side / radius)
    n = len(p) - 1
    samples = [(a, b, r * math.sin(math.pi * (i / n) ** 0.8) ** 0.8 + 0.15, th) for i, (a, b, th, s) in enumerate(p)]
    return spec(samples, tones, thr=0.45)


def paint_specs(cv, specs, outline, fx=False, fy=False, cx=0.0, cy=0.0, dx=0.0, dy=0.0, keep=None):
    """Paints the specs in order, mirrored about x = cx (fx) and/or y = cy (fy), then moved by (dx, dy)."""
    for (samples, tones, thr, min_r) in specs:
        if fx or fy:
            samples = mirrored(samples, fx, fy, cx, cy)
        if dx or dy:
            samples = shifted(samples, dx, dy)
        stroke(cv, samples, tones, outline, thr=thr, min_r=min_r, keep=keep)
