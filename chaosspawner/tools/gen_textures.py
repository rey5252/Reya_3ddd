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
# upgrades: metal tokens redrawn from the reference gold token on a 40x40 grid (almost the reference's
# own pixel size) and doubled to 80x80 so the texture keeps full mipmaps. Frame, plate ramp, glint,
# shine squares and an engraved symbol are drawn in gold; the other upgrades are the same token turned
# to their own hue.
import colorsys

G = 40
LIT = [(116, 76, 48), (86, 47, 18), (99, 58, 28), (117, 74, 35), (140, 86, 34), (172, 110, 32), (205, 145, 52)]
UNLIT = [(182, 141, 86), (242, 200, 140), (226, 182, 122), (150, 100, 36), (138, 84, 18), (165, 105, 28), (200, 140, 40)]
PLATE_RAMP = [(7, (250, 236, 140)), (12, (248, 226, 116)), (17, (242, 206, 84)), (22, (236, 184, 58)),
              (27, (231, 164, 42)), (32, (224, 145, 32))]
INK_GOLD, INKSHADE_GOLD = (92, 58, 20), (190, 120, 30)
HUES = {"speed": 185, "looting": None, "quantity": 282, "experience": 105}


def seg_d(x, y, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / float(dx * dx + dy * dy)))
    return math.hypot(x - (ax + t * dx), y - (ay + t * dy))


def glyph_hit(name, x, y):
    if name == "speed":
        return min(seg_d(x, y, 12, 12, 19, 20), seg_d(x, y, 19, 20, 12, 28),
                   seg_d(x, y, 20, 12, 27, 20), seg_d(x, y, 27, 20, 20, 28)) <= 1.25
    if name == "looting":
        blade = seg_d(x, y, 16, 24, 28, 12) <= 1.45 - max(0.0, x - 24) * 0.12
        guard = seg_d(x, y, 12.5, 20.5, 19.5, 27.5) <= 1.15
        grip = seg_d(x, y, 15, 25, 11.5, 28.5) <= 1.05
        pommel = math.hypot(x - 10.5, y - 29.5) <= 1.7
        return blade or guard or grip or pommel
    if name == "quantity":
        return min(seg_d(x, y, 20, 12, 20, 28), seg_d(x, y, 12, 20, 28, 20)) <= 1.5
    r = math.hypot(x - 20, y - 20)
    return abs(r - 7.2) <= 1.25 or r <= 2.2


def lerp_c(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def ramp_c(v):
    for (p0, c0), (p1, c1) in zip(PLATE_RAMP, PLATE_RAMP[1:]):
        if v <= p1:
            return lerp_c(c0, c1, (v - p0) / float(p1 - p0))
    return PLATE_RAMP[-1][1]


def gold_token(name):
    px = [[None] * G for _ in range(G)]
    L = G - 1
    for y in range(G):
        for x in range(G):
            if min(x, L - x) + min(y, L - y) < 1:
                continue                                         # clipped corners
            lo, hi = min(L - x, L - y), min(x, y)
            if min(lo, hi) < 7:
                px[y][x] = UNLIT[lo] if lo <= hi else LIT[hi]
                continue
            c = ramp_c(y)
            if y < 22 and x < 18:
                c = lerp_c(c, (255, 248, 176), (18 - x) / 18.0 * 0.3)
            if x == 7 or y == 7:
                c = lerp_c(c, (255, 244, 170), 0.3)
            elif x == 32 or y == 32:
                c = lerp_c(c, (205, 135, 34), 0.45)
            c = tuple(v * (1.0 + ((hsh(x, y, 5) % 5) - 2) * 0.012) for v in c)
            # glint: a band running from the top edge down to the right edge, strongest in its middle
            k = x - y
            if 7 <= y <= 25 and 8 <= k <= 15:
                w = {8: 0.2, 9: 0.45, 10: 0.7, 11: 0.85, 12: 0.85, 13: 0.7, 14: 0.45, 15: 0.2}[k]
                c = lerp_c(c, (255, 252, 158), w)
            px[y][x] = c
    # shine squares with a pale halo: large top left, small bottom right
    for y in range(8, 16):
        for x in range(8, 16):
            px[y][x] = lerp_c(px[y][x], (252, 246, 200), 0.45)
    for y in range(9, 14):
        for x in range(9, 14):
            px[y][x] = (255, 255, 238)
    for (x, y) in ((10, 10), (11, 10), (10, 11), (11, 11)):
        px[y][x] = (255, 255, 255)
    for y in range(26, 32):
        for x in range(26, 32):
            px[y][x] = lerp_c(px[y][x], (250, 234, 176), 0.4)
    for y in range(27, 30):
        for x in range(27, 30):
            px[y][x] = (253, 248, 206)
    # the engraved symbol: dark 2px lines with a warm shade on their right and lower side
    hit = {(x, y) for y in range(G) for x in range(G)
           if sum(glyph_hit(name, x + ox, y + oy) for ox in (0.25, 0.75) for oy in (0.25, 0.75)) >= 2}
    for (x, y) in hit:
        for (dx, dy, k) in ((1, 0, 0.55), (0, 1, 0.45), (1, 1, 0.3)):
            q = (x + dx, y + dy)
            if q not in hit and 7 < q[0] < 32 and 7 < q[1] < 32:
                px[q[1]][q[0]] = lerp_c(px[q[1]][q[0]], INKSHADE_GOLD, k)
    for (x, y) in hit:
        px[y][x] = INK_GOLD
    return px


def rehue_c(c, hue):
    h, l, s_ = colorsys.rgb_to_hls(*(v / 255.0 for v in c))
    base = colorsys.rgb_to_hls(241 / 255.0, 201 / 255.0, 84 / 255.0)[0]
    r, g, b = colorsys.hls_to_rgb((h - base + hue / 360.0) % 1.0, l, s_)
    return (r * 255, g * 255, b * 255)


for name in CARDS:
    tok = gold_token(name)
    if HUES[name] is not None:
        tok = [[None if c is None else rehue_c(c, HUES[name]) for c in row] for row in tok]
    fine = [[T if c is None else tuple(max(0, min(255, int(v))) for v in c) + (255,) for c in row] for row in tok]
    px = [[fine[y // 2][x // 2] for x in range(G * 2)] for y in range(G * 2)]
    png(f"{ROOT}/textures/item/{name}_upgrade.png", px)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"chaosspawner:item/{name}_upgrade"}},
              open(f"{ROOT}/models/item/{name}_upgrade.json", "w"), indent=2)
