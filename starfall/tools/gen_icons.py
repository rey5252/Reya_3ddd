"""Pixel-art pictures of the three strikes, 64x32, for the HUD and the strike menu (run from the mod folder).

Each picture comes as a base layer and a glow layer; the game draws the glow over the base, breathing.
"""
import math, os, sys

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/starfall/textures/gui/skill"
W, H = 64, 32


def rnd(seed):
    return np.random.default_rng(seed)


class Pic:
    def __init__(self):
        self.base = np.zeros((H, W, 4), np.float32)
        self.glow = np.zeros((H, W, 4), np.float32)

    def sky(self, top, bottom):
        for y in range(H):
            k = y / (H - 1)
            self.base[y, :, :3] = np.array(top) * (1 - k) + np.array(bottom) * k
        self.base[..., 3] = 255

    def px(self, x, y, c, a=1.0, layer="base"):
        x, y = int(round(x)), int(round(y))
        if not (0 <= x < W and 0 <= y < H) or a <= 0:
            return
        img = self.base if layer == "base" else self.glow
        if layer == "base":
            img[y, x, :3] = img[y, x, :3] * (1 - a) + np.array(c[:3]) * a
        else:
            # the glow layer keeps the brightest light that lands on a pixel
            if a * 255 > img[y, x, 3]:
                img[y, x, :3] = c[:3]
                img[y, x, 3] = a * 255

    def add(self, x, y, c, k):
        """Adds light to the base layer."""
        x, y = int(round(x)), int(round(y))
        if 0 <= x < W and 0 <= y < H:
            self.base[y, x, :3] = np.minimum(255, self.base[y, x, :3] + np.array(c[:3]) * k)

    def stars(self, seed, count, avoid=lambda x, y: False):
        r = rnd(seed)
        for _ in range(count):
            x, y = int(r.integers(0, W)), int(r.integers(0, H))
            if avoid(x, y):
                continue
            b = r.random()
            tint = (255, 255, 255) if r.random() < 0.6 else (190, 205, 255) if r.random() < 0.5 else (255, 225, 200)
            self.px(x, y, tint, 0.25 + 0.6 * b * b)
            if b > 0.93:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    self.px(x + dx, y + dy, tint, 0.3)
                self.px(x, y, (255, 255, 255), 1.0, "glow")

    def line(self, x0, y0, x1, y1, c, a=1.0, layer="base", step=0.25):
        n = max(1, int(math.hypot(x1 - x0, y1 - y0) / step))
        done = set()
        for i in range(n + 1):
            t = i / n
            p = (int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)))
            if p in done:
                continue
            done.add(p)
            self.px(p[0], p[1], c, a, layer)

    def disc(self, cx, cy, r, shade):
        """Fills a disc, asking shade(nx, ny, x, y) for each pixel's colour (or None) on the unit disc."""
        for y in range(H):
            for x in range(W):
                nx, ny = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
                if nx * nx + ny * ny <= 1.0:
                    c = shade(nx, ny, x, y)
                    if c is not None:
                        self.px(x, y, c, c[3] / 255 if len(c) > 3 else 1.0)

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        Image.fromarray(np.clip(self.base, 0, 255).astype(np.uint8), "RGBA").save(f"{OUT}/{name}.png")
        Image.fromarray(np.clip(self.glow, 0, 255).astype(np.uint8), "RGBA").save(f"{OUT}/{name}_glow.png")
        return self


def burst(p, x, y, color, rays=((1, 0, 6), (-1, 0, 6), (0, 1, 4), (0, -1, 4), (1, 1, 3), (-1, 1, 3), (1, -1, 3), (-1, -1, 3))):
    """A starburst: a white heart and rays thinning out, in the glow layer and lit into the base."""
    for dx, dy, n in rays:
        for i in range(1, n + 1):
            k = 1 - i / (n + 1)
            p.px(x + dx * i, y + dy * i, color, 0.25 + 0.75 * k, "glow")
            p.add(x + dx * i, y + dy * i, color, 0.5 * k)
    for dx in (0, 1):
        for dy in (0, 1):
            p.px(x + dx - 0.5, y + dy - 0.5, (255, 255, 255), 1.0, "glow")
            p.px(x + dx - 0.5, y + dy - 0.5, (255, 255, 255), 1.0)


def limb(p, cx, cy, r, sun, land_seed, ocean=(26, 70, 168), land=(52, 116, 60), desert=(150, 128, 78)):
    """The edge of a planet like the Earth seen from space: ocean, land, clouds, a bright rim of air."""
    noise = rnd(land_seed)
    blobs = [(noise.uniform(-0.9, 0.9), noise.uniform(-0.9, 0.2), noise.uniform(0.06, 0.16)) for _ in range(9)]
    clouds = [(noise.uniform(-1, 1), noise.uniform(-1, 0.3), noise.uniform(0.04, 0.1)) for _ in range(26)]

    def shade(nx, ny, x, y):
        d = math.hypot(nx, ny)
        nz = math.sqrt(max(0.0, 1 - d * d))
        light = max(0.0, nx * sun[0] + ny * sun[1] + nz * sun[2])
        c = np.array(ocean, np.float32)
        for bx, by, br in blobs:
            if (nx - bx) ** 2 + ((ny - by) * 1.6) ** 2 < br * br:
                c = np.array(desert if by < -0.2 and bx > 0.3 else land, np.float32)
        for bx, by, br in clouds:
            if ((nx - bx) * 0.5) ** 2 + (ny - by) ** 2 < br * br * 0.3:
                c = c * 0.3 + np.array((235, 240, 250)) * 0.7
        c = c * (0.16 + 0.9 * light)
        # air glows at the edge
        rim = max(0.0, (d - 0.86) / 0.14)
        c = c * (1 - rim * 0.6) + np.array((110, 170, 255)) * rim * (0.4 + 0.8 * light)
        if (x + y) % 2 == 0 and light < 0.12:
            c = c * 0.8
        return tuple(int(v) for v in np.clip(c, 0, 255)) + (255,)

    p.disc(cx, cy, r, shade)
    # a thin halo of air just outside the disc on the lit side
    for y in range(H):
        for x in range(W):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r < d < r + 1.6:
                nx, ny = (x + 0.5 - cx) / d, (y + 0.5 - cy) / d
                lit = max(0.0, nx * sun[0] + ny * sun[1] + 0.3)
                p.px(x, y, (120, 180, 255), min(1.0, 0.75 * lit * (1 - (d - r) / 1.6)))


def railgun():
    p = Pic()
    p.sky((4, 5, 16), (14, 20, 46))
    p.stars(11, 70, lambda x, y: math.hypot(x - 16, y - 66) < 45)
    limb(p, 16, 66, 44, (-0.35, -0.8, 0.5), 5, land=(40, 96, 52))
    # the beam from the railgun far off at the top right, down into the Earth
    x0, y0, x1, y1 = 56.0, 4.0, 22.0, 24.6
    n = int(math.hypot(x1 - x0, y1 - y0) * 4)
    dx, dy = (x1 - x0), (y1 - y0)
    ln = math.hypot(dx, dy)
    sx, sy = -dy / ln, dx / ln
    for i in range(n + 1):
        t = i / n
        x, y = x0 + dx * t, y0 + dy * t
        for off, c, a in ((0, (255, 236, 236), 1.0), (1, (255, 60, 60), 0.9), (-1, (255, 60, 60), 0.9),
                          (2, (170, 20, 34), 0.55), (-2, (170, 20, 34), 0.55)):
            p.px(x + sx * off, y + sy * off, c, a * (0.75 + 0.25 * t))
            if abs(off) < 2:
                p.px(x + sx * off, y + sy * off, c, a * 0.6, "glow")
    # where it lands
    for yy in range(-3, 4):
        for xx in range(-4, 5):
            d = math.hypot(xx * 0.8, yy)
            if d < 3.4:
                k = 1 - d / 3.4
                p.add(x1 + xx, y1 + yy, (255, 80, 70), 0.9 * k)
                p.px(x1 + xx, y1 + yy, (255, 120, 110), k, "glow")
    p.px(x1, y1, (255, 255, 255), 1.0)
    p.px(x1 + 1, y1, (255, 230, 230), 1.0)
    burst(p, 56.5, 4.5, (235, 245, 255), ((1, 0, 6), (-1, 0, 7), (0, 1, 4), (0, -1, 4), (1, 1, 3), (-1, 1, 4),
                                          (1, -1, 3), (-1, -1, 3)))
    return p.save("railgun")


def gungnir():
    p = Pic()
    p.sky((5, 4, 10), (18, 12, 22))
    p.stars(23, 60, lambda x, y: math.hypot(x - 15, y - 12) < 13)
    # Jupiter, banded, lit from the left
    bands = [(214, 190, 160), (178, 120, 78), (232, 214, 186), (160, 96, 60), (220, 196, 160), (190, 138, 92),
             (236, 222, 200), (150, 100, 70)]

    def jup(nx, ny, x, y):
        nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
        light = max(0.0, -0.75 * nx - 0.2 * ny + 0.65 * nz)
        lat = ny + 0.025 * math.sin(nx * 4.0 + ny * 6.0)
        b = bands[min(len(bands) - 1, int((lat + 1) * 0.5 * len(bands)))]
        c = np.array(b, np.float32)
        # the great red spot
        if (nx - 0.25) ** 2 * 3 + (ny - 0.42) ** 2 * 9 < 0.12:
            c = np.array((196, 82, 54), np.float32)
        c = c * (0.12 + 0.95 * light)
        if (x + y) % 2 and 0.05 < light < 0.2:
            c *= 0.8
        return tuple(int(v) for v in np.clip(c, 0, 255)) + (255,)

    # the far half of the accelerator ring goes behind the planet
    ring = lambda a: (15 + math.cos(a) * 19, 12 + math.sin(a) * 4.2 - math.cos(a) * 1.5)
    for i in range(200):
        a = math.pi * 2 * i / 200
        x, y = ring(a)
        if math.sin(a) < 0:
            p.px(x, y, (160, 70, 30), 0.7)
    p.disc(15, 12, 11.5, jup)
    for i in range(200):
        a = math.pi * 2 * i / 200
        x, y = ring(a)
        if math.sin(a) >= 0:
            on = i % 10 < 7
            p.px(x, y, (255, 150, 60) if on else (120, 50, 24), 1.0)
            if on:
                p.px(x, y, (255, 150, 60), 0.6, "glow")
    # the Earth's edge at the bottom right
    limb(p, 76, 52, 30, (-0.6, -0.6, 0.5), 9)
    # the needle, flung down at the Earth with an ember trail behind it
    hx, hy, tx, ty = 52.0, 25.0, 31.0, 11.0
    n = 120
    for i in range(n + 1):
        t = i / n
        x, y = tx + (hx - tx) * t, ty + (hy - ty) * t
        heat = t ** 1.5
        c = (int(120 + 135 * heat), int(30 + 170 * heat * heat), int(10 + 90 * heat ** 3))
        p.px(x, y, c, 0.35 + 0.65 * heat)
        p.px(x, y + 1, c, 0.25 * heat)
        if heat > 0.3:
            p.px(x, y, c, heat, "glow")
    p.line(hx - 4.0, hy - 2.7, hx + 1.5, hy + 1.0, (14, 12, 16), 1.0)
    p.px(hx + 2, hy + 1.4, (255, 250, 230), 1.0)
    p.px(hx + 2, hy + 1.4, (255, 220, 150), 1.0, "glow")
    burst(p, hx + 2.2, hy + 1.6, (255, 200, 120), ((1, 0, 3), (-1, 0, 3), (0, 1, 2), (0, -1, 2)))
    return p.save("gungnir")


def seven_stars():
    p = Pic()
    p.sky((6, 5, 18), (20, 14, 40))
    p.stars(37, 64)
    # the land far below, with the figure's last star coming down onto a sigil
    for y in range(26, H):
        for x in range(W):
            k = (y - 26) / 6
            c = (int(16 + 10 * k), int(14 + 12 * k), int(30 + 10 * k))
            p.px(x, y, c, 1.0)
    p.line(0, 26, W, 26, (60, 52, 110), 0.8)
    for i in range(240):
        a = math.pi * 2 * i / 240
        x, y = 49 + math.cos(a) * 11, 29 + math.sin(a) * 2.4
        on = (i // 6) % 3 != 2
        p.px(x, y, (224, 200, 255) if on else (110, 80, 190), 1.0 if on else 0.7)
        if on:
            p.px(x, y, (200, 160, 255), 0.6, "glow")
    for i in range(120):
        a = math.pi * 2 * i / 120
        p.px(49 + math.cos(a) * 6, 29 + math.sin(a) * 1.2, (170, 130, 250), 0.85)
    for k in range(6):
        a = math.pi * 2 * k / 6 + 0.3
        p.line(49 + math.cos(a) * 6.5, 29 + math.sin(a) * 1.3, 49 + math.cos(a) * 10, 29 + math.sin(a) * 2.2,
               (190, 160, 255), 0.8)
    stars = [(7, 7, 1.0), (8, 15, 0.9), (19, 16, 0.85), (21, 9, 0.6), (31, 8, 1.0), (41, 9, 0.95), (52, 5, 0.95)]
    lines = [(0, 1), (1, 2), (2, 3), (3, 0), (3, 4), (4, 5), (5, 6)]
    for a, b in lines:
        (x0, y0, _), (x1, y1, _) = stars[a], stars[b]
        p.line(x0, y0, x1, y1, (120, 104, 200), 0.5)
    # the beam of light from the handle's end down onto the sigil
    p.line(52, 6, 49, 28.5, (236, 224, 255), 0.9)
    p.line(52, 6, 49, 28.5, (210, 180, 255), 0.8, "glow")
    for dx in (-1, 1):
        p.line(52 + dx, 7, 49 + dx, 28.5, (150, 110, 240), 0.35)
    for x, y, b in stars:
        n = 2 + int(b * 4)
        burst(p, x + 0.5, y + 0.5, (220, 200, 255), ((1, 0, n), (-1, 0, n), (0, 1, n - 1), (0, -1, n - 1),
                                                     (1, 1, 1), (-1, 1, 1), (1, -1, 1), (-1, -1, 1)))
    return p.save("seven_stars")


def preview():
    rows = []
    for name in ("railgun", "gungnir", "seven_stars"):
        base = Image.open(f"{OUT}/{name}.png").convert("RGBA")
        glow = Image.open(f"{OUT}/{name}_glow.png").convert("RGBA")
        lit = Image.alpha_composite(base, glow)
        rows.append(np.concatenate([np.array(base), np.zeros((H, 4, 4), np.uint8), np.array(lit)], axis=1))
    sheet = np.concatenate([np.concatenate([r, np.zeros((4, r.shape[1], 4), np.uint8)]) for r in rows])
    Image.fromarray(sheet).resize((sheet.shape[1] * 6, sheet.shape[0] * 6), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    railgun()
    gungnir()
    seven_stars()
    if len(sys.argv) > 1:
        preview()
    print("ok")
