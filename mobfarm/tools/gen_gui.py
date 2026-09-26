"""Generates the Tiny Mob Farm GUI textures (one per tier) as pixel art.

Run from the mobfarm folder:  python3 tools/gen_gui.py
Writes src/main/resources/assets/mobfarm/textures/gui/mob_farm_<tier>.png (268x206, same
coordinates as the screen) and, with --preview, a 3x preview image.
"""
import math, struct, sys, zlib

M = 4                       # margin around the screen area (caps and clips stick out)
SW, SH = 268, 206           # screen area, same coordinates as the menu
W, H = SW + 2 * M, SH + 2 * M
B = 26                      # body starts after the left upgrade tab
BODY_W = 216

TIERS = {
    # bg, bg light, bg dark, trim, trim light, trim dark
    "wooden":    ((107, 63, 31), (128, 78, 40), (70, 40, 17), (201, 145, 63), (236, 190, 110), (118, 78, 30)),
    "stone":     ((78, 82, 88), (96, 100, 107), (50, 53, 58), (185, 190, 198), (232, 235, 240), (104, 109, 116)),
    "iron":      ((60, 74, 94), (76, 92, 114), (38, 47, 61), (200, 210, 222), (244, 247, 251), (106, 118, 132)),
    "golden":    ((122, 32, 32), (142, 44, 40), (80, 19, 19), (227, 179, 65), (255, 226, 140), (136, 96, 18)),
    "diamond":   ((28, 85, 99), (38, 104, 118), (17, 56, 66), (227, 179, 65), (255, 226, 140), (136, 96, 18)),
    "netherite": ((58, 35, 64), (72, 46, 80), (35, 20, 41), (224, 160, 128), (255, 210, 186), (136, 88, 70)),
}
BLACK = (10, 9, 12)
BAND = (30, 27, 38)
BAND_HI = (50, 45, 60)
CLIP, CLIP_HI, CLIP_LO = (140, 145, 157), (204, 208, 218), (86, 90, 102)
STONE_OUT, STONE, STONE_HI, STONE_LO, STONE_ENG = (38, 42, 40), (152, 166, 154), (196, 208, 197), (110, 122, 112), (128, 142, 130)
TRACK_OUT, TRACK_RIM, TRACK = (12, 10, 12), (126, 131, 141), (38, 24, 26)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def set(self, x, y, c, a=255):
        x += M
        y += M
        if 0 <= x < self.w and 0 <= y < self.h:
            if a >= 255 or self.px[y][x][3] == 0:
                self.px[y][x] = tuple(c[:3]) + (a,)
            else:
                old = self.px[y][x]
                t = a / 255.0
                self.px[y][x] = tuple(int(old[i] * (1 - t) + c[i] * t) for i in range(3)) + (255,)

    def rect(self, x1, y1, x2, y2, c, a=255):
        for y in range(y1, y2):
            for x in range(x1, x2):
                self.set(x, y, c, a)

    def outline(self, x1, y1, x2, y2, c):
        self.rect(x1, y1, x2, y1 + 1, c)
        self.rect(x1, y2 - 1, x2, y2, c)
        self.rect(x1, y1, x1 + 1, y2, c)
        self.rect(x2 - 1, y1, x2, y2, c)

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
        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", self.w * scale, self.h * scale, 8, 6, 0, 0, 0))
                    + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rnd(i, salt):
    h = (i * 0x9E3779B1 + salt * 0x85EBCA6B) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 0x2C1B3C6D) & 0xFFFFFFFF
    h ^= h >> 12
    return h


# ------------------------------------------------------------------ pieces

def framed_panel(cv, x1, y1, x2, y2, pal, band=3):
    """Black edge, dark band with a lit top, thin trim line, then the tier background."""
    bg, bgl, bgd, tr, trl, trd = pal
    cv.rect(x1, y1, x2, y2, BLACK)
    cv.rect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, BAND)
    cv.rect(x1 + 1, y1 + 1, x2 - 1, y1 + 2, BAND_HI)
    i = band + 1
    cv.rect(x1 + i, y1 + i, x2 - i, y2 - i, tr)
    cv.rect(x1 + i, y1 + i, x2 - i, y1 + i + 1, trl)
    cv.rect(x1 + i + 1, y1 + i + 1, x2 - i - 1, y2 - i - 1, bgd)
    # background with a soft vertical gradient
    top, bottom = y1 + i + 2, y2 - i - 1
    for y in range(top, bottom):
        t = (y - top) / max(1, bottom - top - 1)
        cv.rect(x1 + i + 2, y, x2 - i - 1, y + 1, mix(bgl, bg, min(1.0, t * 1.6)))


def corner_cap(cv, cx, cy, pal):
    """5x5 trim square sitting on an outer corner."""
    tr, trl, trd = pal[3], pal[4], pal[5]
    cv.rect(cx - 3, cy - 3, cx + 3, cy + 3, BLACK)
    cv.rect(cx - 2, cy - 2, cx + 2, cy + 2, tr)
    cv.rect(cx - 2, cy - 2, cx + 2, cy - 1, trl)
    cv.rect(cx - 2, cy - 2, cx - 1, cy + 2, trl)
    cv.rect(cx + 1, cy - 1, cx + 2, cy + 2, trd)


KEY = ["1111111",
       "1000001",
       "1011101",
       "1010101",
       "1010001",
       "1011111",
       "1000000"]


def key_spiral(cv, x, y, pal, flip_x=False, flip_y=False):
    """Square spiral on a dark tile, like the ones sitting on the reference frame."""
    tr, trl = pal[3], pal[4]
    cv.rect(x - 1, y - 1, x + 8, y + 8, BLACK)
    cv.rect(x, y, x + 7, y + 7, BAND)
    for r in range(7):
        for c in range(7):
            if KEY[r][c] == "1":
                xx = x + (6 - c if flip_x else c)
                yy = y + (6 - r if flip_y else r)
                cv.set(xx, yy, trl if (r == 0 or c == 0) else tr)


def clip(cv, x, y, vertical=True):
    if vertical:
        cv.rect(x, y, x + 3, y + 8, BLACK)
        cv.rect(x, y + 1, x + 3, y + 7, CLIP)
        cv.rect(x, y + 1, x + 3, y + 2, CLIP_HI)
        cv.rect(x, y + 4, x + 3, y + 5, CLIP_LO)
    else:
        cv.rect(x, y, x + 8, y + 3, BLACK)
        cv.rect(x + 1, y, x + 7, y + 3, CLIP)
        cv.rect(x + 1, y, x + 2, y + 3, CLIP_HI)
        cv.rect(x + 4, y, x + 5, y + 3, CLIP_LO)


def stone_slot(cv, x, y, dark=False):
    """18x18 carved stone slot at slot position (x, y) (item area x..x+16)."""
    face = mix(STONE, (70, 76, 72), 0.35) if dark else STONE
    hi = mix(STONE_HI, (90, 98, 92), 0.35) if dark else STONE_HI
    lo = mix(STONE_LO, (50, 56, 52), 0.35) if dark else STONE_LO
    eng = mix(STONE_ENG, (64, 70, 66), 0.35) if dark else STONE_ENG
    cv.rect(x - 1, y - 1, x + 17, y + 17, STONE_OUT)
    cv.rect(x, y, x + 16, y + 16, face)
    cv.rect(x, y, x + 16, y + 1, hi)
    cv.rect(x, y, x + 1, y + 16, hi)
    cv.rect(x, y + 15, x + 16, y + 16, lo)
    cv.rect(x + 15, y, x + 16, y + 16, lo)
    if not dark:
        # engraved ring
        for yy in range(16):
            for xx in range(16):
                d = math.hypot(xx - 7.5, yy - 7.5)
                if 4.6 <= d < 5.6:
                    cv.set(x + xx, y + yy, eng)
                elif 5.6 <= d < 6.4 and xx + yy > 15:
                    cv.set(x + xx, y + yy, hi)


def gold_brackets(cv, x1, y1, x2, y2, pal, arm=5):
    """Gold L-brackets on the four corners of a box (outside it)."""
    tr, trl, trd = pal[3], pal[4], pal[5]
    for (cx, cy, dx, dy) in ((x1, y1, 1, 1), (x2 - 1, y1, -1, 1), (x1, y2 - 1, 1, -1), (x2 - 1, y2 - 1, -1, -1)):
        for i in range(-1, arm + 1):
            for t in range(-1, 3):
                cv.set(cx - 2 * dx + i * dx, cy - 2 * dy + t * dy, BLACK)
                cv.set(cx - 2 * dx + t * dx, cy - 2 * dy + i * dy, BLACK)
        for i in range(arm):
            cv.set(cx - 2 * dx + i * dx, cy - 2 * dy, trl)
            cv.set(cx - 2 * dx + i * dx, cy - 1 * dy, tr)
            cv.set(cx - 2 * dx, cy - 2 * dy + i * dy, trl)
            cv.set(cx - 1 * dx, cy - 2 * dy + i * dy, tr)
        cv.set(cx - 2 * dx + (arm - 1) * dx, cy - 1 * dy, trd)
        cv.set(cx - 1 * dx, cy - 2 * dy + (arm - 1) * dy, trd)


def horn(cv, ax, ay, pal, mirror=False):
    """Wide curled gold horn anchored at (ax, ay): the tail rises out from the anchor and ends
    in a spiral curling back over itself (drawn pointing left; mirror=True points right)."""
    tr, trl, trd = pal[3], pal[4], pal[5]
    pts = []
    # tail: from the anchor, out to the left and up
    for k in range(80):
        t = k / 79.0
        pts.append((-t * 16.0, -t * t * 3.0))
    # spiral at the outer end, curling up and back in
    sx, sy = -16.0, -3.0
    cx0, cy0 = sx + 1.0, sy - 3.6
    for k in range(140):
        t = k / 139.0
        ang = math.pi * 0.5 + t * 1.75 * math.pi      # start below the centre, go round anticlockwise
        r = 3.6 * (1.0 - t * 0.6)
        pts.append((cx0 + r * 1.25 * math.cos(ang), cy0 + r * math.sin(ang)))
    stroke = set()
    for (x, y) in pts:
        for dx in (0, 1):
            for dy in (0, 1):
                stroke.add((int(math.floor(x)) + dx, int(math.floor(y)) + dy))
    def put(x, y, c):
        cv.set(ax - x if mirror else ax + x, ay + y, c)
    for (x, y) in stroke:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in stroke:
                    put(x + dx, y + dy, (40, 24, 14))
    for (x, y) in stroke:
        c = tr
        if (x, y - 1) not in stroke:
            c = trl
        elif (x, y + 1) not in stroke:
            c = trd
        put(x, y, c)
    # a little sparkle beside each horn
    put(-22, -2, trl)


def sparkle(cv, x, y, pal, big=False):
    tr, trl = pal[3], pal[4]
    if big:
        cv.set(x, y - 1, tr, 200); cv.set(x, y + 1, tr, 200)
        cv.set(x - 1, y, tr, 200); cv.set(x + 1, y, tr, 200)
        cv.set(x, y, trl)
    else:
        cv.set(x, y, tr, 190)


def gem_icon(cv, cx, cy, bright, deep):
    shape = ["..#..", ".###.", "#####", "#####", "#####", ".###.", "..#.."]
    for r, row in enumerate(shape):
        for c, ch in enumerate(row):
            if ch == "#":
                col = bright if r < 4 else deep
                cv.set(cx - 2 + c, cy - 3 + r, col)
    for r, row in enumerate(shape):
        for c, ch in enumerate(row):
            if ch == "#":
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    rr, cc = r + dy, c + dx
                    if not (0 <= rr < 7 and 0 <= cc < 5) or shape[rr][cc] != "#":
                        cv.set(cx - 2 + cc, cy - 3 + rr, BLACK)
    cv.set(cx - 1, cy - 1, (255, 255, 255))
    cv.set(cx - 1, cy, (255, 255, 255), 150)


def bar_track(cv, x1, y, x2, h):
    cv.rect(x1 - 2, y - 1, x2 + 2, y + h + 1, TRACK_OUT)
    cv.rect(x1 - 1, y - 2, x2 + 1, y + h + 2, TRACK_OUT)
    cv.rect(x1 - 1, y - 1, x2 + 1, y + h + 1, TRACK_RIM)
    cv.rect(x1, y, x2, y + h, TRACK)
    cv.rect(x1, y, x2, y + 1, (24, 14, 16))


# ------------------------------------------------------------------ whole screen

def build(pal):
    cv = Canvas(W, H)
    bx1, bx2 = B, B + BODY_W

    # upgrade tabs (behind the body)
    for tx in (0, SW - B - 4):
        framed_panel(cv, tx, 22, tx + B + 4, 80, pal, band=2)
    clip(cv, 0, 30, True)
    clip(cv, SW - 3, 30, True)

    # body
    framed_panel(cv, bx1, 0, bx2, SH, pal, band=3)

    # sparkles: dense near the inner edge, a few spread over the panel
    inner = (bx1 + 7, 7, bx2 - 7, SH - 7)
    for i in range(420):
        x = inner[0] + rnd(i, 1) % (inner[2] - inner[0])
        y = inner[1] + rnd(i, 2) % (inner[3] - inner[1])
        edge = min(x - inner[0], inner[2] - x, y - inner[1], inner[3] - y)
        if edge > 6 and rnd(i, 3) % 9 != 0:
            continue
        sparkle(cv, x, y, pal, big=(rnd(i, 4) % 11 == 0))

    # corner caps and key spirals with clips on the sides
    for (cx, cy) in ((bx1, 0), (bx2 - 1, 0), (bx1, SH - 1), (bx2 - 1, SH - 1)):
        corner_cap(cv, cx, cy, pal)
    for (y, fy) in ((12, False), (SH - 20, True)):
        key_spiral(cv, bx1 + 1, y, pal, flip_x=False, flip_y=fy)
        key_spiral(cv, bx2 - 8, y, pal, flip_x=True, flip_y=fy)
        clip(cv, bx1 - 3, y + (9 if not fy else -10), True)
        clip(cv, bx2, y + (9 if not fy else -10), True)

    # horns curling up beside the window top
    horn(cv, B + 72 - 3, 18 - 2, pal, mirror=False)
    horn(cv, B + 144 + 2, 18 - 2, pal, mirror=True)

    # mob window: framed dark pane with small caps
    wx1, wy1, wx2, wy2 = B + 72, 18, B + 144, 82
    cv.rect(wx1 - 3, wy1 - 3, wx2 + 3, wy2 + 3, BLACK)
    cv.rect(wx1 - 2, wy1 - 2, wx2 + 2, wy2 + 2, BAND)
    cv.rect(wx1 - 1, wy1 - 1, wx2 + 1, wy2 + 1, pal[3])
    cv.rect(wx1 - 1, wy1 - 1, wx2 + 1, wy1, pal[4])
    for y in range(wy1, wy2):
        t = (y - wy1) / (wy2 - wy1 - 1)
        cv.rect(wx1, y, wx2, y + 1, mix((12, 10, 16), pal[2], t))
    for (cx, cy) in ((wx1 - 1, wy1 - 1), (wx2, wy1 - 1), (wx1 - 1, wy2), (wx2, wy2)):
        corner_cap(cv, cx, cy, pal)
    # pedestal
    mid = (wx1 + wx2) // 2
    cv.rect(mid - 18, wy2 - 9, mid + 18, wy2 - 7, pal[5])
    cv.rect(mid - 16, wy2 - 10, mid + 16, wy2 - 9, pal[3])

    # lasso slot with gold brackets, loot grid with brackets around it
    lx, ly = B + 29, 42
    stone_slot(cv, lx, ly)
    gold_brackets(cv, lx - 2, ly - 2, lx + 18, ly + 18, pal, arm=5)
    gx, gy = B + 153, 24
    for r in range(3):
        for c in range(3):
            stone_slot(cv, gx + c * 18, gy + r * 18)
    gold_brackets(cv, gx - 2, gy - 2, gx + 54, gy + 54, pal, arm=7)

    # upgrade slots in the tabs
    for (sx, sy) in ((5, 32), (5, 52), (SW - 21, 32), (SW - 21, 52)):
        stone_slot(cv, sx, sy)

    # bars with gems
    for (y, bright, deep) in ((88, (232, 70, 60), (128, 22, 18)), (99, (100, 226, 240), (18, 108, 124))):
        gem_icon(cv, B + 11, y + 3, bright, deep)
        bar_track(cv, B + 18, y, B + 208, 6)

    # divider above the inventory
    dy = 116
    cv.rect(bx1 + 16, dy, bx2 - 16, dy + 1, pal[5])
    for x in (bx1 + 13, (bx1 + bx2) // 2, bx2 - 14):
        cv.set(x, dy - 1, pal[3]); cv.set(x, dy + 1, pal[3]); cv.set(x - 1, dy, pal[3]); cv.set(x + 1, dy, pal[3])
        cv.set(x, dy, pal[4])

    # player inventory
    for r in range(3):
        for c in range(9):
            stone_slot(cv, B + 27 + c * 18, 124 + r * 18, dark=True)
    for c in range(9):
        stone_slot(cv, B + 27 + c * 18, 182, dark=True)
    return cv


if __name__ == "__main__":
    import os
    out = "src/main/resources/assets/mobfarm/textures/gui"
    os.makedirs(out, exist_ok=True)
    for name, pal in TIERS.items():
        cv = build(pal)
        cv.save(f"{out}/mob_farm_{name}.png")
        if "--preview" in sys.argv:
            cv.save(f"{sys.argv[-1]}/preview_{name}.png", scale=3, bg=(40, 60, 40))
