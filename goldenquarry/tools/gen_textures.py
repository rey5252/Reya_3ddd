"""Generates all Golden Quarry textures as pixel art (no image libraries needed).

Run from the goldenquarry folder:  python3 tools/gen_textures.py [--preview DIR]
Writes the GUI (256x256, same coordinates as the menu, button sprites at u=208), the block
textures and the upgrade item textures into src/main/resources/assets/goldenquarry/textures.
"""
import os, struct, sys, zlib

ASSETS = "src/main/resources/assets/goldenquarry/textures"


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


def rnd(i, salt):
    h = (i * 0x9E3779B1 + salt * 0x85EBCA6B) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 0x2C1B3C6D) & 0xFFFFFFFF
    h ^= h >> 12
    return h


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


# ------------------------------------------------------------------ palette (from the reference)

OUT = (42, 22, 12)
BROWN_D = (74, 40, 20)
BROWN = (104, 60, 30)
GOLD_LO = (158, 100, 30)
GOLD = (228, 168, 56)
GOLD_HI = (255, 222, 120)
GOLD_W = (255, 244, 196)
PANEL = (186, 124, 58)
SLOT_BROWN, SLOT_BROWN_ENG, SLOT_BROWN_EDGE = (138, 84, 44), (112, 64, 32), (98, 56, 28)
SLOT_GOLD, SLOT_GOLD_ENG, SLOT_GOLD_EDGE = (226, 168, 60), (244, 198, 96), (150, 98, 34)
TEAL, TEAL_HI = (70, 214, 184), (180, 255, 236)
GREY_PANEL, GREY_SLOT, GREY_ENG = (62, 62, 66), (40, 40, 44), (84, 84, 90)
INV_BG, INV_HI, INV_LO, INV_SLOT = (198, 198, 198), (255, 255, 255), (85, 85, 85), (139, 139, 139)

# square spiral engraving of the slots (the "回" meander of the reference)
SPIRAL = ["#########.",
          "#.......#.",
          "#.#####.#.",
          "#.#...#.#.",
          "#.#.#.#.#.",
          "#.#.###.#.",
          "#.#.....#.",
          "#.#######.",
          "#.........",
          "##########"]


# ------------------------------------------------------------------ GUI

W, H = 208, 254
UP_X, UP_Y = 184, 65
INV_X, INV_Y = 23, 174
BAR_X1, BAR_X2 = 36, 170
ENERGY = (70, 78)
PROGRESS = (83, 89)
MAIN_H = 162


def slot(cv, x, y, base, eng, edge, spiral=True):
    """18x18 cell around the item position (x, y)."""
    cv.rect(x - 1, y - 1, x + 17, y + 17, edge)
    cv.rect(x, y, x + 16, y + 16, base)
    cv.rect(x, y, x + 16, y + 1, shade(base, 0.85))
    cv.rect(x, y, x + 1, y + 16, shade(base, 0.85))
    if spiral:
        cv.sprite(x + 3, y + 3, SPIRAL, {"#": eng})


def track(cv, x1, y1, x2, y2):
    cv.rect(x1 - 1, y1 - 1, x2 + 1, y2 + 1, OUT)
    cv.rect(x1, y1, x2, y2, (58, 32, 18))
    cv.rect(x1, y1, x2, y1 + 1, (34, 18, 10))
    cv.rect(x1, y2 - 1, x2, y2, (96, 58, 30))


def frame(cv, x1, y1, x2, y2):
    """Dark outline, bevelled gold band, dark inner line."""
    cv.outline(x1, y1, x2, y2, OUT)
    cv.rect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, GOLD)
    cv.bevel(x1 + 1, y1 + 1, x2 - 1, y2 - 1, GOLD_HI, GOLD_LO)
    cv.set(x1 + 2, y1 + 2, GOLD_W)
    cv.outline(x1 + 3, y1 + 3, x2 - 3, y2 - 3, BROWN_D)


def corner_curl(cv, x, y, fx, fy):
    """Little brown scroll on the frame corners."""
    rows = ["..###",
            ".#..#",
            "#.#.#",
            "#..#.",
            "###.."]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                cv.set(x + (i if fx > 0 else -i), y + (j if fy > 0 else -j), BROWN_D)


LIGHTNING = ["..#",
             ".##",
             "##.",
             "###",
             ".##",
             ".#.",
             "#.."]
PICK = ["####.",
        ".#..#",
        "#.#.#",
        "...##",
        "....#"]

ICONS = {
    0: ["...##...",   # power
        ".#.##.#.",
        "#..##..#",
        "#..##..#",
        "#......#",
        "#......#",
        ".#....#.",
        "..####.."],
    1: ["........",   # eye / area
        "..####..",
        ".#....#.",
        "#..##..#",
        "#..##..#",
        ".#....#.",
        "..####..",
        "........"],
    2: [".######.",   # trash can
        "########",
        ".#....#.",
        ".#.#.##.",
        ".#.#.##.",
        ".#.#.##.",
        ".#....#.",
        "..####.."],
}


def button(cv, u, v, idx, on):
    body = GOLD if on else (196, 136, 50)
    hi = GOLD_W if on else GOLD_HI
    lo = GOLD_LO if on else (120, 76, 26)
    cv.rect(u, v, u + 18, v + 18, OUT)
    cv.rect(u + 1, v + 1, u + 17, v + 17, body)
    cv.bevel(u + 1, v + 1, u + 17, v + 17, hi, lo)
    cv.bevel(u + 2, v + 2, u + 16, v + 16, shade(hi, 0.97), shade(lo, 1.1))
    cv.sprite(u + 5, v + 5, ICONS[idx], {"#": (70, 38, 16) if on else (104, 64, 28)})
    led = (90, 240, 110) if on else (200, 60, 44)
    cv.rect(u + 13, v + 3, u + 15, v + 5, led)
    cv.set(u + 13, v + 3, shade(led, 1.3))


def build_gui():
    cv = Canvas(256, 256)
    # main panel
    cv.rect(0, 0, W, MAIN_H, PANEL)
    frame(cv, 0, 0, W, MAIN_H)
    # soft vertical texture on the panel
    for y in range(4, MAIN_H - 4):
        for x in range(4, W - 4):
            r = rnd(x * 977 + y, 3) % 23
            if r == 0:
                cv.set(x, y, shade(PANEL, 1.06))
            elif r == 1:
                cv.set(x, y, shade(PANEL, 0.94))
    for (x, y, fx, fy) in [(5, 5, 1, 1), (W - 6, 5, -1, 1), (5, MAIN_H - 6, 1, -1), (W - 6, MAIN_H - 6, -1, -1)]:
        corner_curl(cv, x, y, fx, fy)
    # storages: brown engraved cells on top, golden ones below, each block with a gold rim
    for (gx, gy, base, eng, edge) in [(8, 8, SLOT_BROWN, SLOT_BROWN_ENG, SLOT_BROWN_EDGE),
                                      (8, 96, SLOT_GOLD, SLOT_GOLD_ENG, SLOT_GOLD_EDGE)]:
        cv.outline(gx - 2, gy - 2, gx + 9 * 18, gy + 3 * 18, OUT)
        for r in range(3):
            for c in range(9):
                slot(cv, gx + c * 18, gy + r * 18, base, eng, edge)
    # quarry icon box
    cv.rect(7, 71, 27, 91, OUT)
    cv.rect(8, 72, 26, 90, (92, 50, 24))
    cv.bevel(8, 72, 26, 90, (60, 32, 16), (150, 96, 44))
    # bars with their little icons
    track(cv, BAR_X1, ENERGY[0], BAR_X2, ENERGY[1])
    track(cv, BAR_X1, PROGRESS[0], BAR_X2, PROGRESS[1])
    cv.sprite(30, ENERGY[0] + 1, LIGHTNING, {"#": TEAL})
    cv.set(31, ENERGY[0] + 2, TEAL_HI)
    cv.sprite(29, PROGRESS[0], PICK, {"#": (200, 204, 210)})
    # right column: buttons live in sprites; a dark iron rack for the upgrades
    cv.rect(UP_X - 3, UP_Y - 3, UP_X + 19, UP_Y + 5 * 18 + 3, OUT)
    cv.rect(UP_X - 2, UP_Y - 2, UP_X + 18, UP_Y + 5 * 18 + 2, GREY_PANEL)
    cv.bevel(UP_X - 2, UP_Y - 2, UP_X + 18, UP_Y + 5 * 18 + 2, (96, 96, 102), (36, 36, 40))
    link = ["..####..",
            ".#....#.",
            "#......#",
            ".#....#.",
            "..####.."]
    for i in range(5):
        x, y = UP_X, UP_Y + i * 18
        cv.rect(x - 1, y - 1, x + 17, y + 17, (28, 28, 32))
        cv.rect(x, y, x + 16, y + 16, GREY_SLOT)
        cv.rect(x, y + 16, x + 17, y + 17, (78, 78, 84))
        cv.rect(x + 16, y, x + 17, y + 17, (78, 78, 84))
        cv.sprite(x + 4, y + 5, link, {"#": GREY_ENG})
    # hinges joining the panels
    for hx in (40, 160):
        cv.rect(hx, MAIN_H - 1, hx + 8, MAIN_H + 5, OUT)
        cv.rect(hx + 1, MAIN_H - 1, hx + 7, MAIN_H + 4, GOLD_LO)
        cv.rect(hx + 2, MAIN_H - 1, hx + 6, MAIN_H + 3, GOLD)
    # player inventory: plain vanilla grey panel
    px1, py1, px2, py2 = INV_X - 8, MAIN_H + 4, INV_X + 162 + 8, H
    cv.rect(px1 + 1, py1, px2 - 1, py2, (0, 0, 0))
    cv.rect(px1, py1 + 1, px2, py2 - 1, (0, 0, 0))
    cv.rect(px1 + 1, py1 + 1, px2 - 1, py2 - 1, INV_BG)
    cv.rect(px1 + 1, py1 + 1, px2 - 2, py1 + 3, INV_HI)
    cv.rect(px1 + 1, py1 + 1, px1 + 3, py2 - 2, INV_HI)
    cv.rect(px1 + 2, py2 - 3, px2 - 1, py2 - 1, INV_LO)
    cv.rect(px2 - 3, py1 + 2, px2 - 1, py2 - 1, INV_LO)
    for r in range(4):
        for c in range(9):
            x = INV_X + c * 18
            y = INV_Y + r * 18 + (4 if r == 3 else 0)
            cv.rect(x - 1, y - 1, x + 17, y + 17, INV_SLOT)
            cv.rect(x - 1, y - 1, x + 16, y, (55, 55, 55))
            cv.rect(x - 1, y - 1, x, y + 16, (55, 55, 55))
            cv.rect(x, y + 16, x + 17, y + 17, INV_HI)
            cv.rect(x + 16, y, x + 17, y + 17, INV_HI)
    # button sprites: off at u=208, on at u=226, one row per button
    for idx in range(3):
        button(cv, 208, idx * 18, idx, False)
        button(cv, 226, idx * 18, idx, True)
    return cv


# ------------------------------------------------------------------ block (after reference photo 1)
#
# The quarry is two blocks tall: a plain dark base and on top of it a golden chest with dark
# scroll ornaments, carrying an open cage (rust posts, dark top ring, glass glints) with the
# drill hanging inside. Model elements: tools/gen_model.py.

BASE = (57, 31, 20)
DARK = (60, 31, 15)
RUST, RUST_HI, RUST_LO = (178, 76, 40), (218, 118, 58), (128, 50, 24)
ORANGE_B, ORANGE_D = (232, 156, 46), (206, 122, 30)
G_BRIGHT, G_MID, G_LIGHT, G_PALE = (246, 214, 62), (238, 190, 50), (255, 236, 120), (255, 248, 186)
IRON_D, IRON, IRON_HI, IRON_W = (56, 44, 42), (122, 110, 108), (170, 160, 156), (240, 232, 222)


def noisy(cv, base, salt, amount=0.06, x1=0, y1=0, x2=16, y2=16):
    for y in range(y1, y2):
        for x in range(x1, x2):
            r = rnd(x + y * 16, salt) % 100
            k = 1.0 + ((r / 100.0) - 0.5) * 2 * amount
            cv.set(x, y, shade(base, k))


def base_texture():
    """Smooth dark brown, a touch lighter at the top, like the stand in the reference."""
    cv = Canvas(16, 16)
    noisy(cv, BASE, 7, 0.07)
    for x in range(16):
        cv.set(x, 0, shade(BASE, 1.18))
        cv.set(x, 15, shade(BASE, 0.82))
    for y in range(16):
        cv.set(0, y, shade(cv.get(0, y), 1.08))
        cv.set(15, y, shade(cv.get(15, y), 0.88))
    return cv


# 16x10 face of the golden chest: dark scroll strokes on bright gold, orange foot.
#   D dark  G gold  H light edge  L pale speck  O orange  E deep orange  R rust
GOLD_FACE = [
    "DHGGGDGGGGDGGGOD",
    "DHGGGDGGGGDGGGOD",
    "DDDDDDDDDDDDDDDD",
    "DDDDDDDDDDDDDDDD",
    "DHGDGGGDDGGGDGOD",
    "DHGDGGGDDGGGDGOD",
    "DHGDDGDDDDGDDGOD",
    "DHGGGGGLGGGGGGOD",
    "DHLGGGGGGGGLGGOD",
    "DOOOOOOOOOOOOOED",
]
FACE_PAL = {"D": DARK, "G": G_BRIGHT, "H": G_LIGHT, "L": G_PALE, "O": ORANGE_B, "E": ORANGE_D, "R": RUST}


def gold_side_texture():
    """Rows 6..15 are the chest's side (the body is 10 pixels high); the rows above repeat plain gold."""
    cv = Canvas(16, 16)
    noisy(cv, G_BRIGHT, 13, 0.04, 0, 0, 16, 6)
    for j, row in enumerate(GOLD_FACE):
        for i, ch in enumerate(row):
            c = FACE_PAL[ch]
            if ch == "G":
                # gold gets lighter towards the bottom, with a little grain
                k = 0.97 + 0.05 * (j / 9.0) + ((rnd(i + j * 16, 17) % 7) - 3) * 0.012
                c = shade(G_MID if j < 2 else G_BRIGHT, k)
            cv.set(i, 6 + j, c)
    return cv


def gold_top_texture():
    """Top of the chest: dark outline, golden rim, dark opening the drill goes into."""
    cv = Canvas(16, 16)
    cv.rect(0, 0, 16, 16, DARK)
    cv.rect(1, 1, 15, 15, G_BRIGHT)
    cv.bevel(1, 1, 15, 15, G_LIGHT, ORANGE_B)
    cv.rect(3, 3, 13, 13, DARK)
    noisy(cv, (40, 21, 11), 29, 0.12, 4, 4, 12, 12)
    # glints of what it digs up, and the warm glow of the drill in the middle
    for (x, y, c) in [(5, 6, (255, 200, 80)), (10, 5, (120, 230, 255)), (6, 10, (230, 230, 230)), (10, 10, (255, 140, 40))]:
        cv.set(x, y, c)
    cv.rect(7, 7, 9, 9, (150, 70, 26))
    return cv


def frame_texture():
    """Top ring of the cage. Rows 0-1: its sides (dark band studded with gold and orange over a
    rust edge); rows 2-15: its top, dark with a grid of gold studs."""
    cv = Canvas(16, 16)
    for x in range(16):
        cv.set(x, 0, G_LIGHT if x % 3 == 1 else (ORANGE_B if x % 6 == 4 else DARK))
        cv.set(x, 1, RUST if x % 5 else RUST_HI)
    for y in range(2, 16):
        for x in range(16):
            stud = x % 3 == 1 and y % 3 == 1
            c = G_LIGHT if stud else ((88, 46, 20) if (x + y) % 4 == 0 else DARK)
            cv.set(x, y, c)
    return cv


def post_texture():
    """Rust post: two pixels wide, lit on the left, shadowed on the right; also the corner fillers."""
    cv = Canvas(16, 16)
    for y in range(16):
        cv.set(0, y, RUST_HI if y % 5 else RUST)
        cv.set(1, y, RUST_LO if y % 5 else RUST)
        for x in range(2, 16):
            cv.set(x, y, RUST)
    cv.rect(0, 0, 2, 1, (240, 150, 80))
    return cv


def bulb_texture():
    """Orange-gold knob at the foot of every post (the orange ornament of the reference)."""
    cv = Canvas(16, 16)
    cv.rect(0, 0, 16, 16, ORANGE_B)
    for x in range(16):
        cv.set(x, 0, G_LIGHT)
        cv.set(x, 1, (240, 172, 56) if x % 3 else G_LIGHT)
    cv.set(0, 0, DARK)
    cv.set(2, 0, DARK)
    for y in range(2, 16):
        cv.set(0, y, (214, 110, 38))
        cv.set(2, y, (198, 96, 30))
    cv.rect(1, 3, 2, 4, (255, 244, 200))
    return cv


def glass_texture():
    """Clear glass (transparent) with a pale glare arc near its upper corners, as in the reference."""
    cv = Canvas(16, 16)
    glint = (250, 248, 238)
    for (x, y) in [(1, 3), (1, 2), (2, 1), (3, 1), (10, 1), (11, 2)]:
        cv.set(x, y, glint)
    return cv


def housing_texture():
    """Dark iron motor box of the drill with grey and white rivets, like the reference's."""
    cv = Canvas(16, 16)
    noisy(cv, IRON_D, 37, 0.1)
    for x in range(16):
        if x % 3 == 1:
            cv.set(x, 0, IRON_HI)
    for x in (2, 6, 11):
        cv.set(x, 1, IRON_W)
        cv.set(x + 1, 1, IRON)
    return cv


def drill_texture():
    """Grey drill: diagonal spiral stripes; the pale bottom rows are the tip."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            band = (x + y) % 4
            c = (214, 208, 204) if band == 0 else (164, 156, 152) if band < 3 else (110, 102, 100)
            cv.set(x, y, c)
    cv.rect(0, 14, 16, 16, IRON_W)
    return cv


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
    gui = build_gui()
    gui.save(f"{ASSETS}/gui/quarry.png")
    blocks = {
        "quarry_base": base_texture(),
        "quarry_gold_side": gold_side_texture(),
        "quarry_gold_top": gold_top_texture(),
        "quarry_frame": frame_texture(),
        "quarry_post": post_texture(),
        "quarry_bulb": bulb_texture(),
        "quarry_glass": glass_texture(),
        "quarry_housing": housing_texture(),
        "quarry_drill": drill_texture(),
    }
    for name, cv in blocks.items():
        cv.save(f"{ASSETS}/block/{name}.png")
    for name, (color, icon) in UPGRADES.items():
        upgrade_texture(color, icon).save(f"{ASSETS}/item/{name}.png")
    if "--preview" in sys.argv:
        out = sys.argv[-1]
        gui.save(f"{out}/preview_gui.png", scale=3, bg=(30, 30, 30))
        strip = Canvas(16 * (len(blocks) + len(UPGRADES)), 16)
        for k, cv in enumerate(list(blocks.values()) + [upgrade_texture(c, i) for c, i in UPGRADES.values()]):
            for y in range(16):
                for x in range(16):
                    p = cv.get(x, y)
                    if p[3]:
                        strip.set(k * 16 + x, y, p)
        strip.save(f"{out}/preview_textures.png", scale=8, bg=(60, 90, 60))
