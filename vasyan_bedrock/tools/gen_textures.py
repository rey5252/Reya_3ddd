"""Generates every texture of the Vasyan add-on: the 64x64 skin (robot with a monitor for a head),
the AI chip item icon and both pack icons.

Run from the vasyan_bedrock folder:  python3 tools/gen_textures.py [--preview DIR]
Skin layout matches models/entity/vasyan.geo.json (standard 64x64 humanoid box UVs + antenna).
"""
import os, struct, sys, zlib

RP = "resource_pack"
BP = "behavior_pack"

CLEAR = (0, 0, 0, 0)
FRAME = (126, 132, 148)
FRAME_LIGHT = (168, 174, 190)
FRAME_DARK = (70, 74, 88)
OUTLINE = (40, 42, 52)
SCREEN = (12, 26, 44)
GLOW = (64, 216, 255)
GLOW_DIM = (30, 120, 160)
SUIT = (64, 74, 104)
SUIT_DARK = (42, 48, 70)
SUIT_LIGHT = (92, 104, 140)
LEG = (52, 56, 68)
BOOT = (28, 30, 38)
RED = (232, 64, 64)
YELLOW = (250, 210, 70)
GREEN = (90, 220, 110)


def png(path, px, scale=1):
    h, w = len(px), len(px[0])
    rows = []
    for y in range(h * scale):
        row = b"\x00"
        for x in range(w * scale):
            row += bytes(px[y // scale][x // scale])
        rows.append(row)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w * scale, h * scale, 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(b"".join(rows), 9)) + ch(b"IEND", b""))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rnd(x, y, salt=0):
    h = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def grid(w, h, c=CLEAR):
    return [[c] * w for _ in range(h)]


def put(px, x, y, c):
    px[y][x] = c + (255,) if len(c) == 3 else c


def fill(px, x0, y0, w, h, c, noise=None, salt=0):
    """Fills a rectangle; with noise=(other, chance/256) some pixels are swapped for the other colour."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if noise and rnd(x, y, salt) < noise[1]:
                put(px, x, y, noise[0])
            else:
                put(px, x, y, c)


def box(u, v, w, h, d):
    """Box UV faces of a cube: name -> (x, y, width, height) on the texture."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def face(px, x0, y0, light=False):
    """The derpy monitor face: frame, dark screen, one big eye, one tiny eye, wobbly smile."""
    fill(px, x0, y0, 8, 8, FRAME)
    for i in range(8):
        put(px, x0 + i, y0, FRAME_LIGHT)
        put(px, x0, y0 + i, FRAME_LIGHT)
        put(px, x0 + i, y0 + 7, FRAME_DARK)
        put(px, x0 + 7, y0 + i, FRAME_DARK)
    fill(px, x0 + 1, y0 + 1, 6, 6, SCREEN)
    for (x, y) in ((1, 1), (2, 1), (1, 2)):
        put(px, x0 + x, y0 + y, mix(SCREEN, GLOW, 0.18))  # glare in the corner
    for (x, y) in ((2, 2), (3, 2), (2, 3), (3, 3)):
        put(px, x0 + x, y0 + y, GLOW)
    put(px, x0 + 2, y0 + 2, (200, 250, 255))
    put(px, x0 + 5, y0 + 3, GLOW)
    for (x, y) in ((2, 5), (3, 6), (4, 5), (5, 6)):
        put(px, x0 + x, y0 + y, GLOW_DIM if y == 6 else GLOW)


def head(px):
    f = box(0, 0, 8, 8, 8)
    for name in ("right", "left", "back"):
        x, y, w, h = f[name]
        fill(px, x, y, w, h, FRAME, (mix(FRAME, FRAME_DARK, 0.4), 30), salt=1)
        for i in range(w):
            put(px, x + i, y, FRAME_LIGHT)
            put(px, x + i, y + h - 1, FRAME_DARK)
        for row in (2, 4):  # cooling vents
            for i in range(1, w - 1):
                put(px, x + i, y + row, OUTLINE if i % 2 else FRAME_DARK)
    x, y, w, h = f["back"]
    put(px, x + 6, y + 6, RED)  # power led
    x, y, w, h = f["top"]
    fill(px, x, y, w, h, FRAME_LIGHT, (FRAME, 50), salt=2)
    put(px, x + 3, y + 3, OUTLINE); put(px, x + 4, y + 4, OUTLINE)
    x, y, w, h = f["bottom"]
    fill(px, x, y, w, h, FRAME_DARK)
    x, y, w, h = f["front"]
    face(px, x, y)


def antenna(px):
    f = box(32, 0, 1, 3, 1)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, OUTLINE if name in ("right", "back") else FRAME_DARK)
    f = box(40, 0, 2, 2, 2)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, mix(RED, (90, 10, 10), 0.4) if name in ("bottom", "back", "right") else RED)
    x, y, w, h = f["front"]
    put(px, x, y, (255, 180, 180))
    x, y, w, h = f["top"]
    put(px, x, y, (255, 190, 190))


def body(px):
    f = box(16, 16, 8, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, SUIT, (SUIT_DARK, 28), salt=3)
    x, y, w, h = f["front"]
    for i in range(w):  # collar and belt
        put(px, x + i, y, SUIT_LIGHT)
        put(px, x + i, y + 9, OUTLINE)
    put(px, x + 3, y + 9, FRAME_LIGHT); put(px, x + 4, y + 9, FRAME_LIGHT)
    fill(px, x + 2, y + 2, 4, 4, SCREEN)  # chest display with three status lights
    for i in range(4):
        put(px, x + 2 + i, y + 2, FRAME_DARK)
    put(px, x + 2, y + 4, RED); put(px, x + 3, y + 4, YELLOW); put(px, x + 4, y + 4, GREEN)
    put(px, x + 5, y + 4, mix(SCREEN, GLOW, 0.3))
    fill(px, x + 2, y + 5, 4, 1, GLOW_DIM)
    for i in range(2):
        put(px, x + 3 + i, y + 7, FRAME_DARK)  # little hatch under the display
    x, y, w, h = f["back"]
    for row in range(2, 8, 2):
        for i in range(2, 6):
            put(px, x + i, y + row, SUIT_DARK)
    for i in range(w):
        put(px, x + i, y + 9, OUTLINE)


def arm(px, u, v):
    f = box(u, v, 4, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, SUIT, (SUIT_DARK, 24), salt=u + v)
    for name in ("right", "front", "left", "back"):
        x, y, w, h = f[name]
        for i in range(w):
            put(px, x + i, y + 4, OUTLINE)  # elbow joint
            put(px, x + i, y + 5, GLOW_DIM if name in ("front", "back") else FRAME_DARK)
            for r in range(9, 12):  # metal hand
                put(px, x + i, y + r, FRAME if (r + i) % 3 else FRAME_DARK)
    x, y, w, h = f["top"]
    fill(px, x, y, w, h, SUIT_LIGHT)
    x, y, w, h = f["bottom"]
    fill(px, x, y, w, h, FRAME_DARK)


def leg(px, u, v):
    f = box(u, v, 4, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, LEG, (mix(LEG, OUTLINE, 0.5), 30), salt=u * 3 + v)
    for name in ("right", "front", "left", "back"):
        x, y, w, h = f[name]
        for i in range(w):
            put(px, x + i, y + 5, OUTLINE)  # knee
            for r in (10, 11):
                put(px, x + i, y + r, BOOT)
        if name == "front":
            put(px, x + 1, y + 5, GLOW_DIM); put(px, x + 2, y + 5, GLOW_DIM)
            for i in range(w):
                put(px, x + i, y + 10, mix(BOOT, FRAME, 0.35))
    x, y, w, h = f["bottom"]
    fill(px, x, y, w, h, BOOT)


def skin():
    px = grid(64, 64)
    head(px)
    antenna(px)
    body(px)
    arm(px, 40, 16)  # right arm
    arm(px, 32, 48)  # left arm
    leg(px, 0, 16)   # right leg
    leg(px, 16, 48)  # left leg
    return px


def chip():
    """16x16 item: green circuit board chip with gold pins and a glowing cyan core."""
    px = grid(16, 16)
    gold, gold_d = (240, 200, 80), (170, 120, 30)
    for i in range(4, 12, 2):  # pins on all four sides
        for (x, y) in ((i, 1), (i, 2), (i, 13), (i, 14), (1, i), (2, i), (13, i), (14, i)):
            put(px, x, y, gold if (x + y) % 2 else gold_d)
    fill(px, 3, 3, 10, 10, (34, 120, 70), ((24, 96, 54), 60), salt=7)
    for i in range(3, 13):
        put(px, i, 3, (70, 170, 100)); put(px, 3, i, (70, 170, 100))
        put(px, i, 12, (16, 70, 40)); put(px, 12, i, (16, 70, 40))
    fill(px, 5, 5, 6, 6, OUTLINE)
    fill(px, 6, 6, 4, 4, GLOW_DIM)
    fill(px, 7, 7, 2, 2, GLOW)
    put(px, 7, 7, (210, 250, 255))
    for (x, y) in ((4, 8), (11, 7), (8, 4), (7, 11)):  # traces
        put(px, x, y, gold)
    return px


def icon():
    """32x32 pack icon: the monitor head with its antenna on a dark tile."""
    px = grid(32, 32, (22, 26, 40, 255))
    for y in range(32):
        for x in range(32):
            if rnd(x, y, 9) < 20:
                put(px, x, y, (30, 36, 54))
    fill(px, 15, 3, 2, 5, FRAME_DARK)
    fill(px, 13, 1, 6, 3, RED)
    put(px, 14, 1, (255, 190, 190))
    small = grid(8, 8)
    face(small, 0, 0)
    for y in range(24):
        for x in range(24):
            px[7 + y][4 + x] = small[y // 3][x // 3]
    return px


def main():
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    out = {
        f"{RP}/textures/entity/vasyan.png": (skin(), 1),
        f"{RP}/textures/items/ai_chip.png": (chip(), 1),
        f"{RP}/pack_icon.png": (icon(), 4),
        f"{BP}/pack_icon.png": (icon(), 4),
    }
    for path, (px, scale) in out.items():
        png(path, px, scale)
        print("wrote", path)
    if "--preview" in sys.argv:
        d = sys.argv[sys.argv.index("--preview") + 1]
        png(os.path.join(d, "vasyan_skin_x8.png"), skin(), 8)
        png(os.path.join(d, "ai_chip_x16.png"), chip(), 16)
        png(os.path.join(d, "pack_icon_x8.png"), icon(), 8)
        print("previews in", d)


if __name__ == "__main__":
    main()
