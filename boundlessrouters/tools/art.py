"""Pixel drawing for the mod's textures: a canvas, colours, and the pieces the panels and icons are made of.

The look: dark graphite panels in brushed steel frames, copper traces and contacts, teal light for what works.
"""
import math
import random

from PIL import Image

# ------------------------------------------------------------------ the palette

OUTLINE = (12, 13, 17)
STEEL_HI = (190, 198, 210)
STEEL = (128, 137, 151)
STEEL_MID = (104, 112, 126)
STEEL_LO = (70, 76, 88)
STEEL_DK = (46, 50, 59)
BASE = (35, 38, 46)
BASE_HI = (46, 50, 60)
BASE_LO = (25, 27, 33)
SLOT_BG = (19, 21, 26)
SLOT_SHADOW = (8, 9, 12)
SLOT_LIGHT = (84, 90, 104)
HEADER = (22, 24, 30)
COPPER = (190, 110, 54)
COPPER_HI = (238, 162, 98)
COPPER_LO = (112, 60, 28)
TEAL = (57, 230, 208)
TEAL_DK = (18, 92, 86)
LCD = (12, 24, 26)
RED = (255, 72, 60)
RED_DK = (110, 22, 18)
WHITE = (236, 240, 246)
GREY = (150, 156, 168)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3])


def hexrgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), fill)
        self.px = self.img.load()

    def set(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (c[0], c[1], c[2], c[3] if len(c) == 4 else a)

    def get(self, x, y):
        return self.px[x, y]

    def blend(self, x, y, c, a):
        """Paints c over what is there at strength a (0..1)."""
        if 0 <= x < self.w and 0 <= y < self.h:
            r, g, b, al = self.px[x, y]
            if al == 0:
                self.px[x, y] = (c[0], c[1], c[2], int(round(255 * a)))
            else:
                self.px[x, y] = (int(round(r + (c[0] - r) * a)), int(round(g + (c[1] - g) * a)), int(round(b + (c[2] - b) * a)), al)

    def rect(self, x1, y1, x2, y2, c):
        """Fills x1..x2-1, y1..y2-1."""
        for y in range(max(0, y1), min(self.h, y2)):
            for x in range(max(0, x1), min(self.w, x2)):
                self.set(x, y, c)

    def outline(self, x1, y1, x2, y2, c):
        for x in range(x1, x2):
            self.set(x, y1, c)
            self.set(x, y2 - 1, c)
        for y in range(y1, y2):
            self.set(x1, y, c)
            self.set(x2 - 1, y, c)

    def bevel(self, x1, y1, x2, y2, light, dark):
        """A one pixel edge: light along the top and left, dark along the bottom and right."""
        for x in range(x1, x2):
            self.set(x, y1, light)
            self.set(x, y2 - 1, dark)
        for y in range(y1, y2):
            self.set(x1, y, light)
            self.set(x2 - 1, y, dark)
        self.set(x2 - 1, y1, mix(light, dark, 0.5))
        self.set(x1, y2 - 1, mix(light, dark, 0.5))

    def paste(self, other, x, y):
        self.img.alpha_composite(other.img, (x, y))

    def draw(self, x, y, rows, palette):
        """Pixel art: rows of characters, each looked up in the palette ('.' and ' ' leave it be)."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in palette:
                    self.set(x + i, y + j, palette[ch])

    def save(self, path):
        self.img.save(path)


def noise(cv, x1, y1, x2, y2, amount, seed):
    """Brushed grain: a little lighter or darker here and there, streaked along x."""
    rnd = random.Random(seed)
    for y in range(y1, y2):
        streak = rnd.uniform(-amount, amount) * 0.5
        for x in range(x1, x2):
            r, g, b, a = cv.get(x, y)
            if a == 0:
                continue
            d = streak + rnd.uniform(-amount, amount)
            cv.set(x, y, (max(0, min(255, int(r + d))), max(0, min(255, int(g + d))), max(0, min(255, int(b + d)))), a)


# ------------------------------------------------------------------ panel pieces

def steel_frame(cv, x1, y1, x2, y2, seed=1):
    """A brushed steel frame six pixels wide round x1..x2, y1..y2: raised outside, sunk inside."""
    for i in range(6):
        a1, b1, a2, b2 = x1 + i, y1 + i, x2 - i, y2 - i
        if i == 0:
            cv.outline(a1, b1, a2, b2, OUTLINE)
        elif i == 1:
            cv.bevel(a1, b1, a2, b2, STEEL_HI, STEEL_LO)
        elif i in (2, 3):
            for x in range(a1, a2):
                cv.set(x, b1, mix(STEEL, STEEL_MID, (x - x1) / max(1, x2 - x1)))
                cv.set(x, b2 - 1, STEEL_MID)
            for y in range(b1, b2):
                cv.set(a1, y, mix(STEEL, STEEL_MID, (y - y1) / max(1, y2 - y1)))
                cv.set(a2 - 1, y, STEEL_MID)
        elif i == 4:
            cv.bevel(a1, b1, a2, b2, STEEL_LO, STEEL_HI)
        else:
            cv.outline(a1, b1, a2, b2, OUTLINE)
    # grain on the frame only
    rnd = random.Random(seed)
    for y in range(y1, y2):
        for x in range(x1, x2):
            inside = x1 + 6 <= x < x2 - 6 and y1 + 6 <= y < y2 - 6
            if inside or not (x1 + 2 <= x < x2 - 2 and y1 + 2 <= y < y2 - 2):
                continue
            r, g, b, a = cv.get(x, y)
            d = rnd.uniform(-5, 5)
            cv.set(x, y, (max(0, min(255, int(r + d))), max(0, min(255, int(g + d))), max(0, min(255, int(b + d)))), a)


def rivet(cv, x, y):
    """A copper bolt head, 3 x 3, its middle at (x, y)."""
    cv.set(x - 1, y - 1, COPPER_HI)
    cv.set(x, y - 1, COPPER_HI)
    cv.set(x + 1, y - 1, COPPER)
    cv.set(x - 1, y, COPPER_HI)
    cv.set(x, y, COPPER)
    cv.set(x + 1, y, COPPER_LO)
    cv.set(x - 1, y + 1, COPPER)
    cv.set(x, y + 1, COPPER_LO)
    cv.set(x + 1, y + 1, COPPER_LO)


def interior(cv, x1, y1, x2, y2, seed=2, grid=True):
    """The panel's graphite inside: grain and a faint grid."""
    cv.rect(x1, y1, x2, y2, BASE)
    if grid:
        for y in range(y1, y2):
            for x in range(x1, x2):
                if (x - x1) % 8 == 0 or (y - y1) % 8 == 0:
                    cv.set(x, y, mix(BASE, BASE_HI, 0.45))
    noise(cv, x1, y1, x2, y2, 2.2, seed)


def slot(cv, sx, sy, big=False):
    """A sunk slot under a menu slot at (sx, sy) (the item's top left)."""
    x1, y1 = sx - 1, sy - 1
    cv.rect(x1, y1, x1 + 18, y1 + 18, SLOT_BG)
    cv.bevel(x1, y1, x1 + 18, y1 + 18, SLOT_SHADOW, SLOT_LIGHT)
    if big:
        cv.outline(x1 - 1, y1 - 1, x1 + 19, y1 + 19, OUTLINE)


def plate(cv, x1, y1, x2, y2, fill=BASE_LO, raised=False):
    """A plate let into the panel (sunk) or standing on it (raised)."""
    cv.rect(x1, y1, x2, y2, fill)
    if raised:
        cv.bevel(x1, y1, x2, y2, BASE_HI, OUTLINE)
    else:
        cv.bevel(x1, y1, x2, y2, OUTLINE, BASE_HI)


def copper_frame(cv, x1, y1, x2, y2):
    cv.outline(x1, y1, x2, y2, COPPER_LO)
    cv.outline(x1 + 1, y1 + 1, x2 - 1, y2 - 1, COPPER)
    for x in range(x1 + 1, x2 - 1):
        cv.set(x, y1 + 1, COPPER_HI)
    for y in range(y1 + 1, y2 - 1):
        cv.set(x1 + 1, y, COPPER_HI)


def trace(cv, points, width=2):
    """A copper trace through orthogonal points, laid into the panel (a dark edge round it)."""
    segs = list(zip(points, points[1:]))
    # the dark bed first, then the copper on it
    for (ax, ay), (bx, by) in segs:
        for x in range(min(ax, bx) - 1, max(ax, bx) + width + 1):
            for y in range(min(ay, by) - 1, max(ay, by) + width + 1):
                cv.set(x, y, mix(BASE_LO, OUTLINE, 0.5))
    for (ax, ay), (bx, by) in segs:
        horizontal = ay == by
        for x in range(min(ax, bx), max(ax, bx) + width):
            for y in range(min(ay, by), max(ay, by) + width):
                k = (y - min(ay, by)) if horizontal else (x - min(ax, bx))
                cv.set(x, y, COPPER_HI if k == 0 else COPPER if k < width - 1 else COPPER_LO)


def pad(cv, x, y, r=2):
    """A round copper pad with a hole, its middle at (x, y)."""
    for j in range(-r - 1, r + 2):
        for i in range(-r - 1, r + 2):
            d = math.hypot(i, j)
            if d <= r + 0.6:
                cv.set(x + i, y + j, COPPER_HI if (i + j) < 0 else COPPER if d < r else COPPER_LO)
    cv.set(x, y, OUTLINE)


def disc(cv, cx, cy, r, colour_at):
    """A filled disc, its colour from colour_at(distance, angle)."""
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                c = colour_at(d, math.atan2(y + 0.5 - cy, x + 0.5 - cx))
                if c is not None:
                    cv.set(x, y, c)


def lcd(cv, x1, y1, x2, y2):
    """A dark readout window with faint scanlines."""
    cv.rect(x1, y1, x2, y2, LCD)
    for y in range(y1, y2, 2):
        for x in range(x1, x2):
            cv.set(x, y, mix(LCD, (0, 0, 0), 0.25))
    cv.bevel(x1 - 1, y1 - 1, x2 + 1, y2 + 1, OUTLINE, BASE_HI)
