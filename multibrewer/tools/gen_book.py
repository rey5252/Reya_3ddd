"""Pixel textures for the brewer's guide book: the open-book GUI (256x180) and the item icon.
Run from the mod folder: python3 tools/gen_book.py [--preview DIR]"""
import math, struct, sys, zlib

ROOT = "src/main/resources/assets/multibrewer/textures"
W, H = 256, 180


def png(path, px, scale=1):
    h, w = len(px), len(px[0])
    rows = []
    for y in range(h * scale):
        rows.append(b"\x00" + b"".join(bytes(px[y // scale][x // scale]) for x in range(w * scale)))
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w * scale, h * scale, 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(b"".join(rows), 9)) + ch(b"IEND", b""))


def rnd(x, y, s=0):
    h = (x * 374761393 + y * 668265263 + s * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


BLACK = (18, 10, 20)
LEATHER, LEATHER_D, LEATHER_L = (78, 38, 100), (48, 22, 64), (104, 58, 130)
GOLD, GOLD_L, GOLD_D = (227, 179, 65), (255, 226, 140), (136, 96, 18)
PAPER, PAPER_D, PAPER_E = (238, 224, 190), (214, 196, 156), (178, 152, 108)
INK = (120, 70, 50)

px = [[(0, 0, 0, 0)] * W for _ in range(H)]


def put(x, y, c, a=255):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = tuple(c[:3]) + (a,)


def rect(x1, y1, x2, y2, c):
    for y in range(y1, y2):
        for x in range(x1, x2):
            put(x, y, c)


# cover with rounded corners
for y in range(2, H - 2):
    for x in range(2, W - 2):
        if (x < 5 or x > W - 6) and (y < 5 or y > H - 6) and abs(x - (4 if x < 128 else W - 5)) + abs(y - (4 if y < 90 else H - 5)) > 2:
            continue
        c = LEATHER if rnd(x, y) % 6 else LEATHER_D
        put(x, y, c)
for x in range(4, W - 4):
    put(x, 2, BLACK); put(x, H - 3, BLACK)
    put(x, 3, LEATHER_L)
for y in range(4, H - 4):
    put(2, y, BLACK); put(W - 3, y, BLACK)
    put(3, y, LEATHER_L)
# gold corner plates
for (cx, cy, dx, dy) in ((3, 3, 1, 1), (W - 4, 3, -1, 1), (3, H - 4, 1, -1), (W - 4, H - 4, -1, -1)):
    for i in range(12):
        for j in range(12 - i):
            put(cx + i * dx, cy + j * dy, GOLD if i + j < 10 else GOLD_D)
    for i in range(10):
        put(cx + i * dx, cy, GOLD_L); put(cx, cy + i * dy, GOLD_L)
    put(cx + 3 * dx, cy + 3 * dy, (70, 130, 235)); put(cx + 4 * dx, cy + 3 * dy, (150, 200, 255))

# page stacks and pages
for (x1, x2) in ((9, 127), (129, 247)):
    for k in range(3):
        for x in range(x1 - k, x2 + k):
            put(x, 172 + k, PAPER_E if k % 2 == 0 else PAPER_D)
    for y in range(7, 172):
        for x in range(x1, x2):
            c = PAPER if rnd(x, y, 1) % 23 else mix(PAPER, PAPER_D, 0.5)
            # shade toward the spine
            d = (x - x1) if x1 > 100 else (x2 - 1 - x)
            if d < 8:
                c = mix(c, PAPER_E, (8 - d) / 12.0)
            put(x, y, c)
    for x in range(x1, x2):
        put(x, 7, PAPER_E); put(x, 171, PAPER_E)
    for y in range(7, 172):
        put(x1, y, PAPER_E); put(x2 - 1, y, PAPER_E)
    # thin ink border with little corner flourishes
    bx1, by1, bx2, by2 = x1 + 5, 12, x2 - 6, 166
    for x in range(bx1, bx2 + 1):
        put(x, by1, INK); put(x, by2, INK)
    for y in range(by1, by2 + 1):
        put(bx1, y, INK); put(bx2, y, INK)
    for (cx, cy, dx, dy) in ((bx1, by1, 1, 1), (bx2, by1, -1, 1), (bx1, by2, 1, -1), (bx2, by2, -1, -1)):
        for i in range(2, 6):
            put(cx + i * dx, cy + 2 * dy, INK)
            put(cx + 2 * dx, cy + i * dy, INK)
        put(cx + 4 * dx, cy + 4 * dy, GOLD_D)

# spine
for y in range(5, 175):
    for x in range(126, 130):
        put(x, y, LEATHER_D if x in (126, 129) else LEATHER)
for y in (20, 60, 120, 160):
    for x in range(126, 130):
        put(x, y, GOLD); put(x, y + 1, GOLD_D)
# ribbon bookmark hanging out at the bottom
for y in range(7, 179):
    put(140, y, (170, 30, 40)) if y > 168 else None
    put(141, y, (200, 50, 60)) if y > 168 else None
put(140, 179, (120, 20, 30)); put(141, 178, (120, 20, 30))

gui = f"{ROOT}/gui/guide.png"
png(gui, px)

# item icon: violet book with a gold potion on the cover
T = (0, 0, 0, 0)
icon = [[T] * 16 for _ in range(16)]
shape = [
    "................",
    "..KKKKKKKKKKK...",
    ".KgLLLLLLLLLLK..",
    ".KgLLLLyLLLLLPK.",
    ".KgLLLyGyLLLLPK.",
    ".KgLLLLgLLLLLPK.",
    ".KgLLLgRgLLLLPK.",
    ".KgLLgRRRgLLLPK.",
    ".KgLLgRwRgLLLPK.",
    ".KgLLgRRRgLLLPK.",
    ".KgLLLgggLLLLPK.",
    ".KgLLLLLLLLLLPK.",
    ".KgdddddddddddK.",
    ".KpppppppppppK..",
    "..KKKKKKKKKKK...",
    "................"]
cols = {"K": BLACK + (255,), "g": GOLD_L + (255,), "L": LEATHER + (255,), "d": LEATHER_D + (255,),
        "y": GOLD + (255,), "G": GOLD_D + (255,), "R": (200, 60, 200, 255), "w": (255, 255, 255, 255),
        "P": PAPER + (255,), "p": PAPER_D + (255,)}
for y, row in enumerate(shape):
    for x, ch in enumerate(row):
        if ch in cols:
            icon[y][x] = cols[ch]
png(f"{ROOT}/item/guide_book.png", icon)

if "--preview" in sys.argv:
    png(sys.argv[-1] + "/preview_guide.png", px, 3)
