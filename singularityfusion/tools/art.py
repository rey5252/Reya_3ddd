"""Pixel drawing for Singularity Fusion's textures: a canvas, colours, noise.

The look: void stone, near black with a cold violet cast, engraved and bevelled; violet light in its glyphs and seams;
the singularity's own light white-gold, orange at its edges, as Gargantua's.
"""
import math
import random

from PIL import Image

# ------------------------------------------------------------------ the palette

VOID_0 = (7, 6, 12)
VOID_1 = (14, 12, 22)
VOID_2 = (22, 19, 34)
VOID_3 = (32, 28, 48)
VOID_4 = (46, 41, 68)
VOID_5 = (64, 58, 92)
EDGE_HI = (96, 88, 130)
STEEL_HI = (150, 144, 178)
PURPLE_DK = (52, 18, 96)
PURPLE = (132, 58, 226)
PURPLE_HI = (190, 130, 255)
VIOLET_WHITE = (236, 218, 255)
GOLD_DK = (104, 62, 20)
GOLD = (214, 150, 58)
GOLD_HI = (255, 222, 150)
WHITE = (255, 255, 255)
BLACK = (0, 0, 0)


def clamp(x, lo=0.0, hi=1.0):
    return lo if x < lo else hi if x > hi else x


def mix(a, b, t):
    t = clamp(t)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3])


def add(a, b):
    return tuple(min(255, a[i] + b[i]) for i in range(3))


def smooth(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


def ramp(stops, t):
    """A colour along a gradient: stops is [(t, colour), ...] in order of t."""
    if t <= stops[0][0]:
        return stops[0][1]
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return mix(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 1.0)
    return stops[-1][1]


# ------------------------------------------------------------------ noise

class Noise:
    """Value noise on a lattice, optionally wrapping (tiling) every `wrap_x` / `wrap_y` lattice cells."""

    def __init__(self, seed, wrap_x=None, wrap_y=None):
        self.seed = seed
        self.wrap_x, self.wrap_y = wrap_x, wrap_y
        self.cache = {}

    def lattice(self, i, j):
        if self.wrap_x:
            i %= self.wrap_x
        if self.wrap_y:
            j %= self.wrap_y
        key = (i, j)
        v = self.cache.get(key)
        if v is None:
            h = (i * 374761393 + j * 668265263 + self.seed * 2246822519) & 0xFFFFFFFF
            h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
            v = ((h ^ (h >> 16)) & 0xFFFF) / 65535.0
            self.cache[key] = v
        return v

    def at(self, x, y):
        i, j = math.floor(x), math.floor(y)
        fx, fy = x - i, y - j
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a, b = self.lattice(i, j), self.lattice(i + 1, j)
        c, d = self.lattice(i, j + 1), self.lattice(i + 1, j + 1)
        return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy


def fbm(noises, x, y, octaves=4, lacunarity=2.0, gain=0.5):
    """Fractal noise from a list of Noise (one per octave, each wrapping at its own scale), 0 to 1."""
    total, amp, norm, f = 0.0, 1.0, 0.0, 1.0
    for o in range(octaves):
        total += noises[o].at(x * f, y * f) * amp
        norm += amp
        amp *= gain
        f *= lacunarity
    return total / norm


def wrapping_octaves(seed, cells_x, cells_y=None, octaves=4):
    """Noise layers for fbm that tile: the base lattice wraps every cells_x (cells_y) cells, each octave twice as many."""
    return [Noise(seed + o * 101, cells_x * 2 ** o if cells_x else None, cells_y * 2 ** o if cells_y else None) for o in range(octaves)]


# ------------------------------------------------------------------ the canvas

class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), fill)
        self.px = self.img.load()

    def set(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (int(c[0]), int(c[1]), int(c[2]), int(c[3]) if len(c) == 4 else int(a))

    def get(self, x, y):
        return self.px[x, y]

    def blend(self, x, y, c, alpha):
        """Paints c over what is there with an opacity (0 to 1)."""
        if not (0 <= x < self.w and 0 <= y < self.h) or alpha <= 0:
            return
        r, g, b, a = self.px[x, y]
        alpha = clamp(alpha)
        out_a = alpha + (a / 255.0) * (1 - alpha)
        if out_a <= 0:
            return
        nr = (c[0] * alpha + r * (a / 255.0) * (1 - alpha)) / out_a
        ng = (c[1] * alpha + g * (a / 255.0) * (1 - alpha)) / out_a
        nb = (c[2] * alpha + b * (a / 255.0) * (1 - alpha)) / out_a
        self.px[x, y] = (int(round(nr)), int(round(ng)), int(round(nb)), int(round(out_a * 255)))

    def glow(self, x, y, c, k):
        """Adds light: c times k onto what is there (its opacity kept, or made opaque enough to show)."""
        if not (0 <= x < self.w and 0 <= y < self.h) or k <= 0:
            return
        r, g, b, a = self.px[x, y]
        self.px[x, y] = (min(255, int(r + c[0] * k)), min(255, int(g + c[1] * k)), min(255, int(b + c[2] * k)), max(a, min(255, int(255 * k))))

    def rect(self, x, y, w, h, c, a=255):
        for j in range(y, y + h):
            for i in range(x, x + w):
                self.set(i, j, c, a)

    def hline(self, x1, x2, y, c, a=255):
        for i in range(x1, x2 + 1):
            self.set(i, y, c, a)

    def vline(self, x, y1, y2, c, a=255):
        for j in range(y1, y2 + 1):
            self.set(x, j, c, a)

    def frame(self, x, y, w, h, c, a=255):
        self.hline(x, x + w - 1, y, c, a)
        self.hline(x, x + w - 1, y + h - 1, c, a)
        self.vline(x, y, y + h - 1, c, a)
        self.vline(x + w - 1, y, y + h - 1, c, a)

    def bevel(self, x, y, w, h, light, dark):
        """A raised edge: light along the top and left, dark along the bottom and right."""
        self.hline(x, x + w - 2, y, light)
        self.vline(x, y, y + h - 2, light)
        self.hline(x + 1, x + w - 1, y + h - 1, dark)
        self.vline(x + w - 1, y + 1, y + h - 1, dark)

    def inset(self, x, y, w, h, light, dark):
        """A sunken edge: dark along the top and left, light along the bottom and right."""
        self.bevel(x, y, w, h, dark, light)

    def line(self, x0, y0, x1, y1, c, a=255):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c, a)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def aaline(self, x0, y0, x1, y1, c, k=1.0, width=1.0, additive=False):
        """A soft line: every pixel near it painted by how near (to `width` / 2 from its middle, fading over a pixel)."""
        minx, maxx = int(math.floor(min(x0, x1) - width - 1)), int(math.ceil(max(x0, x1) + width + 1))
        miny, maxy = int(math.floor(min(y0, y1) - width - 1)), int(math.ceil(max(y0, y1) + width + 1))
        dx, dy = x1 - x0, y1 - y0
        ll = dx * dx + dy * dy
        for y in range(max(0, miny), min(self.h, maxy + 1)):
            for x in range(max(0, minx), min(self.w, maxx + 1)):
                px, py = x + 0.5, y + 0.5
                t = 0.0 if ll == 0 else clamp(((px - x0) * dx + (py - y0) * dy) / ll)
                qx, qy = x0 + dx * t, y0 + dy * t
                d = math.hypot(px - qx, py - qy)
                cover = clamp(width / 2 + 0.5 - d)
                if cover > 0:
                    if additive:
                        self.glow(x, y, c, k * cover)
                    else:
                        self.blend(x, y, c, k * cover)

    def disc(self, cx, cy, r, c, a=1.0, soft=0.7):
        """A filled circle with a soft edge (centre and radius in pixels, pixel centres at +0.5)."""
        for y in range(max(0, int(cy - r - 2)), min(self.h, int(cy + r + 3))):
            for x in range(max(0, int(cx - r - 2)), min(self.w, int(cx + r + 3))):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                cover = clamp((r - d) / soft + 0.5)
                if cover > 0:
                    self.blend(x, y, c, a * cover)

    def ring(self, cx, cy, r_in, r_out, c, a=1.0, soft=0.6):
        for y in range(max(0, int(cy - r_out - 2)), min(self.h, int(cy + r_out + 3))):
            for x in range(max(0, int(cx - r_out - 2)), min(self.w, int(cx + r_out + 3))):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                cover = min(clamp((r_out - d) / soft + 0.5), clamp((d - r_in) / soft + 0.5))
                if cover > 0:
                    self.blend(x, y, c, a * cover)

    def paste(self, other, x, y):
        self.img.alpha_composite(other.img, (x, y))
        self.px = self.img.load()

    def save(self, path):
        self.img.save(path)


def rng(seed):
    return random.Random(seed)
