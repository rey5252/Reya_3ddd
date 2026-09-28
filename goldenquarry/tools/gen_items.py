"""Generates the Golden Quarry upgrade item textures (cards with a symbol) as pixel art.

Run from the goldenquarry folder:  python3 tools/gen_items.py
The block and GUI textures come from the reference pictures: tools/extract_block.py, tools/extract_gui.py.
"""
import os
import struct
import zlib

ASSETS = "src/main/resources/assets/goldenquarry/textures"
OUT = (42, 22, 12)
GOLD_LO = (158, 100, 30)
GOLD = (228, 168, 56)
GOLD_HI = (255, 222, 120)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def set(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            if a >= 255 or self.px[y][x][3] == 0:
                self.px[y][x] = tuple(c[:3]) + (a,)
            else:
                old = self.px[y][x]
                t = a / 255.0
                self.px[y][x] = tuple(int(old[i] * (1 - t) + c[i] * t) for i in range(3)) + (255,)

    def get(self, x, y):
        return self.px[y][x]

    def rect(self, x1, y1, x2, y2, c, a=255):
        for y in range(y1, y2):
            for x in range(x1, x2):
                self.set(x, y, c, a)

    def outline(self, x1, y1, x2, y2, c):
        self.rect(x1, y1, x2, y1 + 1, c)
        self.rect(x1, y2 - 1, x2, y2, c)
        self.rect(x1, y1, x1 + 1, y2, c)
        self.rect(x2 - 1, y1, x2, y2, c)

    def bevel(self, x1, y1, x2, y2, hi, lo):
        """Light top/left edge, dark bottom/right edge."""
        self.rect(x1, y1, x2 - 1, y1 + 1, hi)
        self.rect(x1, y1, x1 + 1, y2 - 1, hi)
        self.rect(x1 + 1, y2 - 1, x2, y2, lo)
        self.rect(x2 - 1, y1 + 1, x2, y2, lo)

    def sprite(self, x, y, rows, pal):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in pal:
                    self.set(x + i, y + j, pal[ch])

    def save(self, path, scale=1, bg=None):
        rows = []
        for y in range(self.h * scale):
            row = b"\x00"
            for x in range(self.w * scale):
                p = self.px[y // scale][x // scale]
                if bg is not None and p[3] < 255:
                    t = p[3] / 255.0
                    p = tuple(int(p[i] * t + bg[i] * (1 - t)) for i in range(3)) + (255,)
                row += bytes(p)
            rows.append(row)
        raw = b"".join(rows)
        ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", self.w * scale, self.h * scale, 8, 6, 0, 0, 0))
                    + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


# ------------------------------------------------------------------ upgrade cards

UPGRADES = {
    "speed_upgrade": ((70, 214, 184), ["........",
                                       "#...#...",
                                       ".#...#..",
                                       "..#...#.",
                                       "..#...#.",
                                       ".#...#..",
                                       "#...#...",
                                       "........"]),
    "range_upgrade": ((110, 190, 255), ["##....##",
                                        "#......#",
                                        "..#..#..",
                                        "...##...",
                                        "...##...",
                                        "..#..#..",
                                        "#......#",
                                        "##....##"]),
    "fortune_upgrade": ((70, 110, 255), ["..####..",
                                         ".#....#.",
                                         "########",
                                         "#.#..#.#",
                                         ".#.##.#.",
                                         "..#..#..",
                                         "...##...",
                                         "........"]),
    "silk_touch_upgrade": ((250, 240, 255), [".....##.",
                                             "....#.#.",
                                             "...#.#..",
                                             "..#.#...",
                                             ".#.#....",
                                             ".##.....",
                                             "#.......",
                                             "........"]),
    "smelting_upgrade": ((255, 130, 30), ["...#....",
                                          "...##...",
                                          "..###.#.",
                                          ".#####..",
                                          ".##.###.",
                                          "##...##.",
                                          "##...##.",
                                          ".#####.."]),
}


def upgrade_texture(color, icon):
    cv = Canvas(16, 16)
    cv.rect(1, 1, 15, 15, OUT)
    cv.rect(2, 2, 14, 14, GOLD)
    cv.bevel(2, 2, 14, 14, GOLD_HI, GOLD_LO)
    cv.rect(3, 3, 13, 13, (70, 40, 20))
    cv.rect(3, 3, 13, 4, (50, 28, 14))
    cv.sprite(4, 4, icon, {"#": color})
    # a highlight pixel on each lit symbol cell's top
    for j, row in enumerate(icon):
        for i, ch in enumerate(row):
            if ch == "#" and (j == 0 or icon[j - 1][i] != "#"):
                cv.set(4 + i, 4 + j, tuple(min(255, v + 70) for v in color))
    # gold rivets
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        cv.set(x, y, GOLD_LO)
    return cv


if __name__ == "__main__":
    for name, (color, icon) in UPGRADES.items():
        upgrade_texture(color, icon).save(f"{ASSETS}/item/{name}.png")
