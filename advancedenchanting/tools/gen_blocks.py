"""Pixel textures for the advanced enchanting table and the table upgrade (run from the mod folder).
The table uses the vanilla enchanting table shape: 12px tall, sides show rows 4..15 of the side texture."""
import struct, zlib

OUT = "src/main/resources/assets/advancedenchanting/textures"
BLACK = (18, 12, 14)
GL, G, GD, GDD = (255, 230, 140), (228, 176, 58), (168, 112, 24), (104, 66, 10)
R, RL, RD, RDD = (122, 34, 34), (150, 52, 46), (86, 20, 24), (52, 12, 16)
OB, OB2, OBL = (26, 18, 36), (38, 26, 52), (70, 50, 96)
CYAN, CYANL = (60, 200, 220), (190, 250, 255)


def png(path, px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p if len(p) == 4 else p + (255,)) for p in row) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def rnd(x, y, s=0):
    h = (x * 374761393 + y * 668265263 + s * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def grid(c):
    return [[c] * 16 for _ in range(16)]


# top: red velvet with a gold border, gold corner diamonds and a cyan gem in the middle
top = grid(R)
for y in range(16):
    for x in range(16):
        if rnd(x, y) % 5 == 0:
            top[y][x] = RL if rnd(x, y, 1) % 2 else RD
for i in range(16):
    top[0][i] = GL; top[i][0] = GL; top[15][i] = GD; top[i][15] = GD
for i in range(1, 15):
    top[1][i] = G; top[i][1] = G; top[14][i] = GDD; top[i][14] = GDD
top[0][15] = G; top[15][0] = G
for i in range(2, 14):
    top[2][i] = RDD; top[i][2] = RDD
for (cx, cy) in ((4, 4), (11, 4), (4, 11), (11, 11)):
    top[cy][cx] = GL
    for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        top[cy + dy][cx + dx] = GD
for (x, y, c) in ((7, 5, GD), (8, 5, GD), (6, 6, GD), (9, 6, GD), (5, 7, GD), (10, 7, GD), (5, 8, GD), (10, 8, GD),
                  (6, 9, GD), (9, 9, GD), (7, 10, GD), (8, 10, GD),
                  (7, 6, CYANL), (8, 6, CYAN), (6, 7, CYANL), (7, 7, CYANL), (8, 7, CYAN), (9, 7, CYAN),
                  (6, 8, CYAN), (7, 8, CYAN), (8, 8, CYAN), (9, 8, (30, 120, 140)), (7, 9, (30, 120, 140)), (8, 9, (30, 120, 140))):
    top[y][x] = c
png(f"{OUT}/block/advanced_enchanting_table_top.png", top)

# side (rows 4..15 visible): gold lip, obsidian body with gold corner posts and a glowing rune, gold foot
side = grid(OB)
for y in range(16):
    for x in range(16):
        if rnd(x, y, 2) % 4 == 0:
            side[y][x] = OB2
for x in range(16):
    side[4][x] = GL; side[5][x] = G; side[6][x] = GDD
    side[14][x] = G; side[15][x] = GD
for y in range(7, 14):
    side[y][0] = G; side[y][1] = GD; side[y][14] = G; side[y][15] = GD
for x in range(3, 13, 3):
    side[5][x] = GDD
for (x, y, c) in ((7, 8, CYANL), (8, 8, CYAN), (6, 9, CYAN), (7, 9, CYANL), (8, 9, CYAN), (9, 9, CYAN),
                  (7, 10, CYAN), (8, 10, (30, 120, 140)), (7, 11, (30, 120, 140)), (8, 11, (30, 120, 140))):
    side[y][x] = c
for (x, y) in ((4, 9), (11, 9), (4, 10), (11, 10)):
    side[y][x] = OBL
for y in range(4):
    for x in range(16):
        side[y][x] = OB
png(f"{OUT}/block/advanced_enchanting_table_side.png", side)

bottom = grid(OB)
for y in range(16):
    for x in range(16):
        if rnd(x, y, 3) % 3 == 0:
            bottom[y][x] = OB2
png(f"{OUT}/block/advanced_enchanting_table_bottom.png", bottom)

# table upgrade: a gold-bound tome with a cyan gem, drawn on a transparent 16x16
T = (0, 0, 0, 0)
up = [[T] * 16 for _ in range(16)]
shape = [
    "................",
    "....KKKKKKKKK...",
    "...KgGGGGGGGGK..",
    "..KgRRRRRRRRRGK.",
    "..KgRrrrrrrrRGK.",
    "..KgRrrccrrrRGK.",
    "..KgRrcCCcrrRGK.",
    "..KgRrcCCcrrRGK.",
    "..KgRrrccrrrRGK.",
    "..KgRrrrrrrrRGK.",
    "..KgRRRRRRRRRGK.",
    "..KgGGGGGGGGGDK.",
    "..KPPPPPPPPPPDK.",
    "..KpppppppppppK.",
    "...KKKKKKKKKKK..",
    "................"]
cols = {"K": BLACK, "g": GL, "G": G, "D": GD, "R": RD, "r": R, "c": CYAN, "C": CYANL,
        "P": (236, 226, 200), "p": (190, 176, 150)}
for y, row in enumerate(shape):
    for x, ch in enumerate(row):
        if ch in cols:
            up[y][x] = cols[ch]
png(f"{OUT}/item/table_upgrade.png", up)
