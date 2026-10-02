"""Small pixel-art helpers shared by the texture generators (Pillow only).

Colours are (r, g, b) or (r, g, b, a) tuples; every drawing call works on whole pixels.
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "alfheimheart")


def rgba(c, a=255):
    return (c[0], c[1], c[2], c[3] if len(c) > 3 else a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    """k > 0 lightens towards white, k < 0 darkens towards black."""
    return mix(c, (255, 255, 255), k) if k > 0 else mix(c, (0, 0, 0), -k)


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Canvas:
    """An RGBA image with an origin offset, so pieces can be drawn in their own coordinates."""

    def __init__(self, w, h, ox=0, oy=0):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()
        self.w, self.h = w, h
        self.ox, self.oy = ox, oy

    def inside(self, x, y):
        x += self.ox
        y += self.oy
        return 0 <= x < self.w and 0 <= y < self.h

    def get(self, x, y):
        x += self.ox
        y += self.oy
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[x, y]
        return (0, 0, 0, 0)

    def set(self, x, y, c, a=None):
        x += self.ox
        y += self.oy
        if not (0 <= x < self.w and 0 <= y < self.h):
            return
        c = rgba(c) if a is None else rgba(c, a)
        if c[3] >= 255:
            self.px[x, y] = c
        elif c[3] > 0:
            old = self.px[x, y]
            t = c[3] / 255.0
            if old[3] == 0:
                self.px[x, y] = c
            else:
                na = old[3] / 255.0 * (1 - t) + t
                col = tuple(int(round((c[i] * t + old[i] * old[3] / 255.0 * (1 - t)) / na)) for i in range(3))
                self.px[x, y] = col + (int(round(na * 255)),)

    def clear(self, x, y):
        x += self.ox
        y += self.oy
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (0, 0, 0, 0)

    def rect(self, x1, y1, x2, y2, c, a=None):
        """Fills x1 <= x < x2, y1 <= y < y2."""
        for y in range(y1, y2):
            for x in range(x1, x2):
                self.set(x, y, c, a)

    def hline(self, x1, x2, y, c, a=None):
        for x in range(x1, x2):
            self.set(x, y, c, a)

    def vline(self, x, y1, y2, c, a=None):
        for y in range(y1, y2):
            self.set(x, y, c, a)

    def outline(self, x1, y1, x2, y2, c):
        self.hline(x1, x2, y1, c)
        self.hline(x1, x2, y2 - 1, c)
        self.vline(x1, y1, y2, c)
        self.vline(x2 - 1, y1, y2, c)

    def line(self, x1, y1, x2, y2, c, a=None):
        """Bresenham line, both ends included."""
        dx, dy = abs(x2 - x1), -abs(y2 - y1)
        sx, sy = (1 if x1 < x2 else -1), (1 if y1 < y2 else -1)
        err = dx + dy
        while True:
            self.set(x1, y1, c, a)
            if x1 == x2 and y1 == y2:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x1 += sx
            if e2 <= dx:
                err += dx
                y1 += sy

    def disc(self, cx, cy, r, c, a=None):
        """Filled circle of radius r round the pixel centre (cx, cy) (half-pixel centres allowed)."""
        for y in range(int(math.floor(cy - r)) - 1, int(math.ceil(cy + r)) + 2):
            for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 2):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.set(x, y, c, a)

    def paste(self, other, x, y):
        """Draws another canvas (its own pixels, origin ignored) at x, y, blending."""
        for yy in range(other.h):
            for xx in range(other.w):
                p = other.px[xx, yy]
                if p[3]:
                    self.set(x + xx, y + yy, p[:3], p[3])

    def sprite(self, rows, x, y, pal, flip=False):
        """Draws a sprite given as strings of palette keys ('.' or ' ' = clear)."""
        for j, row in enumerate(rows):
            if flip:
                row = row[::-1]
            for i, ch in enumerate(row):
                if ch in ". ":
                    continue
                self.set(x + i, y + j, pal[ch])

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


def upscale(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def rnd(i, salt=0):
    """Deterministic hash noise in [0, 1)."""
    h = (i * 0x9E3779B1 + salt * 0x85EBCA6B + 0x632BE5AB) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 0x2C1B3C6D) & 0xFFFFFFFF
    h ^= h >> 12
    h = (h * 0x297A2D39) & 0xFFFFFFFF
    h ^= h >> 15
    return (h & 0xFFFFFF) / float(0x1000000)


def rnd2(x, y, salt=0):
    return rnd(x * 7919 + y * 104729, salt)


def rrect(x, y, x1, y1, x2, y2, r):
    """(depth, nx, ny) of the pixel (x, y) for the rounded rectangle [x1, x2) x [y1, y2) with corner
    radius r: how far in from its edge the pixel's middle is (0 or less = outside it) and the unit
    direction that points out of the rectangle there."""
    px, py = x + 0.5, y + 0.5
    cx, cy = (x1 + x2) / 2.0, (y1 + y2) / 2.0
    hx, hy = (x2 - x1) / 2.0, (y2 - y1) / 2.0
    dx, dy = px - cx, py - cy
    qx, qy = abs(dx) - (hx - r), abs(dy) - (hy - r)
    sx, sy = (1 if dx >= 0 else -1), (1 if dy >= 0 else -1)
    if qx > 0 and qy > 0:
        d = math.hypot(qx, qy)
        return r - d, sx * qx / d, sy * qy / d
    if qx > qy:
        return r - qx, sx, 0.0
    return r - qy, 0.0, sy
