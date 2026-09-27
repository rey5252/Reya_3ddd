"""Block, item and model files for the Chaos Spawner (run from the mod folder)."""
import json, struct, zlib

ROOT = "src/main/resources/assets/chaosspawner"
T = (0, 0, 0, 0)


def png(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p if len(p) == 4 else tuple(p) + (255,)) for p in row) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", len(px[0]), len(px), 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def grid(c=T):
    return [[c] * 16 for _ in range(16)]


DARK, METAL, METAL_L = (16, 24, 36), (40, 58, 76), (70, 96, 118)
CYAN, CYAN_L, CYAN_D = (80, 220, 230), (190, 250, 255), (30, 120, 140)
NAVY, NAVY_L, NAVY_D = (22, 36, 52), (40, 66, 88), (10, 16, 26)
TEAL, TEAL_L = (38, 150, 160), (70, 200, 205)
STONE, STONE_L, STONE_D = (140, 146, 150), (186, 192, 196), (84, 90, 96)
BARS = (3, 6, 9, 12)

# cage side: rims top and bottom, dark bars, and teal glyphs between them (drips, hollow squares
# with a pale middle) like the reference; corners left open for the posts
G, G_L, G_D, G_P = (38, 200, 190), (120, 240, 225), (25, 120, 140), (205, 240, 236)
cage = grid()
for x in range(2, 14):
    for y in (0, 15):
        cage[y][x] = NAVY_D
    cage[1][x] = NAVY_L
    cage[14][x] = NAVY
for bx in (5, 10):
    for y in range(2, 14):
        cage[y][bx] = NAVY if y % 5 else NAVY_L
GLYPHS = [
    "##..#....#..",
    "#...#....##.",
    "#...#.....#.",
    "##..#.##..#.",
    ".......#..#.",
    ".......#.##.",
    "###..####...",
    "#P#..#PP#...",
    "###..#PP#.##",
    ".....####.#P",
    "..........##",
    "............"]
for r, row in enumerate(GLYPHS):
    for c, ch in enumerate(row):
        x, y = 2 + c, 2 + r
        if x in (5, 10):
            continue
        if ch == "#":
            cage[y][x] = G_L if r in (0, 6) or (c + r) % 4 == 0 else G if r < 9 else G_D
        elif ch == "P":
            cage[y][x] = G_P
png(f"{ROOT}/textures/block/cage.png", cage)

# top: dark rim around the edge and a square spiral of bars with teal glints, corners left for the caps
top = grid()
for i in range(2, 14):
    for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
        top[y][x] = NAVY_D
    for (x, y) in ((i, 1), (1, i)):
        top[y][x] = NAVY_L
    for (x, y) in ((i, 14), (14, i)):
        top[y][x] = NAVY
TOP_SPIRAL = ["............",
              ".##########.",
              ".#........#.",
              ".#.######.#.",
              ".#.#....#.#.",
              ".#.#.##.#.#.",
              ".#.#.#..#.#.",
              ".#.#.####.#.",
              ".#.#......#.",
              ".#.########.",
              ".#..........",
              "............"]
for r, row in enumerate(TOP_SPIRAL):
    for c, ch in enumerate(row):
        if ch == "#":
            top[2 + r][2 + c] = G_L if (r + c) % 6 == 0 else (60, 170, 190) if r < 6 else (40, 130, 160)
png(f"{ROOT}/textures/block/cage_top.png", top)

# bottom: solid dark plate
bottom = grid(NAVY_D)
for i in range(16):
    bottom[0][i] = bottom[i][0] = NAVY
png(f"{ROOT}/textures/block/cage_bottom.png", bottom)

# corner post: dark bar with a teal edge; caps are pale stone blocks
post = grid(NAVY)
for y in range(16):
    post[y][0] = NAVY_L
    post[y][1] = TEAL if 4 <= y <= 11 else NAVY_L
    post[y][15] = NAVY_D
png(f"{ROOT}/textures/block/post.png", post)
cap = grid(STONE)
for i in range(16):
    cap[0][i] = cap[i][0] = STONE_L
    cap[15][i] = cap[i][15] = STONE_D
png(f"{ROOT}/textures/block/cap.png", cap)

# glowing floor: a bright square spiral on a dark plate
SPIRAL = ["############",
          "#..........#",
          "#.########.#",
          "#.#......#.#",
          "#.#.####.#.#",
          "#.#.#..#.#.#",
          "#.#.#.##.#.#",
          "#.#.#....#.#",
          "#.#.######.#",
          "#.#........#",
          "#.##########",
          "#..........."]
floor = grid(NAVY_D)
for y, row in enumerate(SPIRAL):
    for x, ch in enumerate(row):
        if ch == "#":
            floor[2 + y][2 + x] = CYAN_L if (x + y) % 5 == 0 else CYAN
        else:
            floor[2 + y][2 + x] = (14, 60, 72)
png(f"{ROOT}/textures/block/floor.png", floor)

def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), rot=None, glow=False, uvs=None):
    e = {"from": frm, "to": to, "faces": {}}
    for f in faces:
        if uvs and f in uvs:
            uv = uvs[f]
        elif f in ("up", "down"):
            uv = [frm[0], frm[2], to[0], to[2]]
        elif f in ("north", "south"):
            uv = [16 - to[0], 16 - to[1], 16 - frm[0], 16 - frm[1]] if f == "north" else [frm[0], 16 - to[1], to[0], 16 - frm[1]]
        else:
            uv = [16 - to[2], 16 - to[1], 16 - frm[2], 16 - frm[1]] if f == "east" else [frm[2], 16 - to[1], to[2], 16 - frm[1]]
        e["faces"][f] = {"uv": uv, "texture": tex}
    if rot:
        e["rotation"] = rot
    if glow:
        e["forge_data"] = {"block_light": 15, "sky_light": 15}
    return e


FULL = [0, 0, 16, 16]
els = [
    # outer walls (outside faces) and the same bars seen from inside, so the back of the cage shows
    box([0, 0, 0], [16, 16, 16], "#cage", faces=("north", "south", "east", "west"), uvs={f: FULL for f in ("north", "south", "east", "west")}),
    box([0, 16, 0], [16, 16, 16], "#top", faces=("up", "down"), uvs={"up": FULL, "down": FULL}),
    box([0, 0, 0], [16, 0, 16], "#bottom", faces=("down",), uvs={"down": FULL}),
    box([0, 0, 0], [16, 16, 0], "#cage", faces=("south",), uvs={"south": FULL}),
    box([0, 0, 16], [16, 16, 16], "#cage", faces=("north",), uvs={"north": FULL}),
    box([0, 0, 0], [0, 16, 16], "#cage", faces=("east",), uvs={"east": FULL}),
    box([16, 0, 0], [16, 16, 16], "#cage", faces=("west",), uvs={"west": FULL}),
    # glowing floor
    box([2, 0, 2], [14, 1, 14], "#floor", faces=("up",), glow=True),
]
for (x, z) in ((0, 0), (14, 0), (0, 14), (14, 14)):
    els.append(box([x, 2, z], [x + 2, 14, z + 2], "#post", faces=("north", "south", "east", "west"),
                   uvs={f: [0, 2, 2, 14] for f in ("north", "south", "east", "west")}))
    for y in (0, 14):
        els.append(box([x, y, z], [x + 2, y + 2, z + 2], "#cap"))

model = {
    "parent": "minecraft:block/block",
    "render_type": "minecraft:cutout",
    "ambientocclusion": False,
    "textures": {"particle": "chaosspawner:block/cage", "cage": "chaosspawner:block/cage", "top": "chaosspawner:block/cage_top",
                 "bottom": "chaosspawner:block/cage_bottom", "post": "chaosspawner:block/post", "cap": "chaosspawner:block/cap",
                 "floor": "chaosspawner:block/floor"},
    "elements": els,
}
json.dump(model, open(f"{ROOT}/models/block/chaos_spawner.json", "w"), indent=2)
json.dump({"variants": {"": {"model": "chaosspawner:block/chaos_spawner"}}}, open(f"{ROOT}/blockstates/chaos_spawner.json", "w"), indent=2)
json.dump({"parent": "chaosspawner:block/chaos_spawner"}, open(f"{ROOT}/models/item/chaos_spawner.json", "w"), indent=2)

# soul crystal: a faceted cyan crystal; the filled one has a two-colour soul inside (tinted per mob)
CRYSTAL = [
    "................",
    "......####......",
    ".....#WLMM#.....",
    "....#WLLMMD#....",
    "...#LLLMMMDD#...",
    "...#LLMMMMDD#...",
    "..#LLLMMMMDDD#..",
    "..#LLMMMMMDDD#..",
    "..#LLMMMMMDDD#..",
    "..#LLMMMMMDDS#..",
    "...#LMMMMDDS#...",
    "...#LMMMMDDS#...",
    "....#MMMDDS#....",
    ".....#MDDS#.....",
    "......####......",
    "................"]
cols = {"#": (18, 52, 66), "W": (255, 255, 255), "L": (170, 242, 246), "M": (96, 210, 226), "D": (52, 150, 182),
        "S": (34, 104, 138)}
empty = grid()
for y, row in enumerate(CRYSTAL):
    for x, ch in enumerate(row):
        if ch in cols:
            empty[y][x] = cols[ch] + (255,)
# facet ridges and a couple of sparkles
for (x, y) in ((5, 6), (5, 7), (5, 8), (5, 9), (6, 10), (6, 11)):
    empty[y][x] = (130, 228, 238, 255)
for (x, y) in ((10, 4), (11, 6), (11, 7)):
    empty[y][x] = (80, 180, 205, 255)
empty[1][13] = (200, 250, 255, 255)
empty[3][14] = (120, 220, 235, 255)
png(f"{ROOT}/textures/item/soul_crystal.png", empty)
# the captured soul: a flame-shaped wisp (tinted with the mob's first colour) with two eyes (second colour)
FLAME = [(7, 4), (7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7),
         (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9),
         (6, 10), (7, 10), (8, 10), (7, 11)]
soul1, soul2 = grid(), grid()
for (x, y) in FLAME:
    v = 255 if y < 8 else 225 if y < 10 else 190
    soul1[y][x] = (v, v, v, 255)
for (x, y) in ((6, 8), (8, 8)):
    soul1[y][x] = T
    soul2[y][x] = (255, 255, 255, 255)
png(f"{ROOT}/textures/item/soul_crystal_soul.png", soul1)
png(f"{ROOT}/textures/item/soul_crystal_spots.png", soul2)
json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "chaosspawner:item/soul_crystal"},
           "overrides": [{"predicate": {"chaosspawner:filled": 1}, "model": "chaosspawner:item/soul_crystal_filled"}]},
          open(f"{ROOT}/models/item/soul_crystal.json", "w"), indent=2)
json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "chaosspawner:item/soul_crystal",
                                                              "layer1": "chaosspawner:item/soul_crystal_soul",
                                                              "layer2": "chaosspawner:item/soul_crystal_spots"}},
          open(f"{ROOT}/models/item/soul_crystal_filled.json", "w"), indent=2)

# upgrades: glowing cards like the reference - a dark rounded outline, a bevelled coloured rim, a
# speckled background brighter in the middle, and a bright symbol with a soft glow round it
import math


def hsh(x, y, salt):
    h = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


CARDS = {
    # background, rim, symbol, glow, symbol rows (12x12, "#" symbol, "o" darker symbol edge)
    "speed": ((18, 104, 118), (80, 220, 230), (225, 255, 255), (90, 240, 250), [
        "............",
        "............",
        "..##..##....",
        "...##..##...",
        "....##..##..",
        ".....##..##.",
        ".....##..##.",
        "....##..##..",
        "...##..##...",
        "..##..##....",
        "............",
        "............"]),
    "looting": ((26, 44, 120), (110, 140, 250), (255, 222, 120), (255, 190, 80), [
        "............",
        ".........##.",
        "........###.",
        ".......###..",
        "......###...",
        ".....###....",
        "..#.###.....",
        "...###......",
        "...##.......",
        "..#..#......",
        ".#..........",
        "............"]),
    "quantity": ((84, 30, 118), (210, 110, 240), (150, 255, 110), (120, 255, 90), [
        "............",
        "............",
        "..#......#..",
        ".....##.....",
        "....#..#....",
        "...#.##.#...",
        "...#.##.#...",
        "....#..#....",
        ".....##.....",
        "..#......#..",
        "............",
        "............"]),
    "experience": ((30, 96, 34), (130, 230, 90), (240, 255, 120), (200, 255, 80), [
        "............",
        "............",
        "....####....",
        "...######...",
        "..########..",
        "..########..",
        "..########..",
        "..########..",
        "...######...",
        "....####....",
        "............",
        "............"]),
}
# upgrades: built from the button tiles in tools/upgrade_tiles.png (20x20 tiles, 6 columns, two rows
# per colour; the first column is blank). The blank tile of each colour is kept whole, a symbol is drawn
# on it in the tiles' own style (2px dark strokes with a mid-tone shade on the right), and the result is
# scaled up 4x to 80x80 so the pixels stay identical while the texture size still allows full mipmaps.
from PIL import Image as _Image

_TILES = _Image.open("tools/upgrade_tiles.png").convert("RGBA")
TILE_ROW = {"speed": 2, "looting": 0, "quantity": 4, "experience": 6}
INK = {"speed": ((11, 53, 46), (36, 156, 144)), "looting": ((62, 33, 6), (180, 112, 24)),
       "quantity": ((28, 11, 53), (94, 36, 156)), "experience": ((25, 53, 11), (67, 156, 36))}
GLYPHS = {
    "speed": ["XX...XX...",
              ".XX...XX..",
              "..XX...XX.",
              "...XX...XX",
              "...XX...XX",
              "..XX...XX.",
              ".XX...XX..",
              "XX...XX..."],
    "looting": [".......XX",
                "......XXX",
                ".....XXX.",
                "....XXX..",
                ".X.XXX...",
                "..XXX....",
                "..XX.....",
                ".X..X....",
                "X........"],
    "quantity": ["...XX...",
                 "...XX...",
                 "...XX...",
                 "XXXXXXXX",
                 "XXXXXXXX",
                 "...XX...",
                 "...XX...",
                 "...XX..."],
    "experience": ["..XXXX..",
                   ".XX..XX.",
                   "XX....XX",
                   "X..XX..X",
                   "X..XX..X",
                   "XX....XX",
                   ".XX..XX.",
                   "..XXXX.."],
}
SCALE = 4
for name in CARDS:
    ty = TILE_ROW[name] * 20
    tile = [[_TILES.getpixel((x, ty + y)) for x in range(20)] for y in range(20)]
    ink, shade_c = INK[name]
    rows = GLYPHS[name]
    w, h = len(rows[0]), len(rows)
    gx = 3 + (14 - w) // 2
    gy = 3 + (14 - h) // 2
    pts = {(gx + c, gy + r) for r, row in enumerate(rows) for c, ch in enumerate(row) if ch == "X"}
    for (x, y) in pts:
        for dx in (1, 2):
            q = (x + dx, y)
            if q in pts:
                break
            if q[0] <= 15:
                tile[y][q[0]] = shade_c + (255,)
    for (x, y) in pts:
        tile[y][x] = ink + (255,)
    # frame: a soft gradient along every side (lighter in the middle, darker towards the corners)
    for y in range(20):
        for x in range(20):
            c = tile[y][x]
            d = min(x, y, 19 - x, 19 - y)
            if c[3] == 0 or d > 2:
                continue
            along = 1.0 - abs((x if min(y, 19 - y) <= min(x, 19 - x) else y) - 9.5) / 9.5
            k = 0.88 + 0.2 * along
            tile[y][x] = tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)
    px = [[tile[y // SCALE][x // SCALE] for x in range(20 * SCALE)] for y in range(20 * SCALE)]
    png(f"{ROOT}/textures/item/{name}_upgrade.png", px)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"chaosspawner:item/{name}_upgrade"}},
              open(f"{ROOT}/models/item/{name}_upgrade.json", "w"), indent=2)
