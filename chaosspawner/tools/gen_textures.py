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
        ".#....#.....",
        ".##...##....",
        ".###..###...",
        ".####.####..",
        ".#####.####.",
        ".#####.####.",
        ".####.####..",
        ".###..###...",
        ".##...##....",
        ".#....#.....",
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
        "..#......#..",
        ".###....###.",
        "..#......#..",
        ".....##.....",
        "....####....",
        "....####....",
        ".....##.....",
        "..#......#..",
        ".###....###.",
        "..#......#..",
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
# upgrades: shiny metal tokens like the reference - a dark frame, a plate glowing from the orange
# rim to a bright middle, a diagonal glint, white shine dots in two corners and an engraved symbol
METALS = {
    # frame dark, frame, rim, middle, bright, engraving
    "speed":      ((14, 44, 58), (24, 74, 92), (30, 120, 150), (80, 200, 225), (205, 250, 255), (12, 46, 62)),
    "looting":    ((96, 56, 24), (117, 77, 46), (163, 99, 35), (232, 190, 70), (255, 246, 160), (72, 46, 18)),
    "quantity":   ((52, 20, 70), (76, 36, 98), (120, 50, 160), (190, 120, 232), (248, 220, 255), (50, 16, 70)),
    "experience": ((26, 58, 20), (40, 84, 30), (62, 138, 34), (150, 222, 76), (232, 255, 172), (26, 62, 18)),
}
for n, (name, (bg, rim, sym, glow, rows)) in enumerate(CARDS.items()):
    fdark, frame, edge, mid, bright, ink = METALS[name]
    px = grid()
    for y in range(16):
        for x in range(16):
            pale = tuple(int(bright[i] * 0.7 + edge[i] * 0.3) for i in range(3))
            if x == 15 or y == 15:
                # raised light lip on the right and bottom, like the reference frame
                c = pale if not (x == 0 or y == 0) else shade(frame, 1.1)
            elif x == 0 or y == 0:
                c = fdark
            elif x == 14 or y == 14:
                c = shade(edge, 0.62)
            elif x == 1 or y == 1:
                c = frame
            elif x == 2 or y == 2 or x == 13 or y == 13:
                c = edge
            else:
                # glow from the rim to the middle, a little brighter towards the top
                d = max(abs(x - 7.5), abs(y - 7.0)) / 5.0
                t = max(0.0, min(1.0, 1.0 - d))
                c = tuple(int(mid[i] * (1 - t * 0.7) + bright[i] * t * 0.7) for i in range(3))
                if d > 0.85:
                    c = tuple(int(c[i] * 0.55 + edge[i] * 0.45) for i in range(3))
                c = shade(c, 1.0 + ((hsh(x, y, n) % 5) - 2) * 0.02)
                # diagonal glint across the upper right
                if x - y in (4, 5) and y <= 9:
                    c = tuple(int(c[i] * 0.3 + bright[i] * 0.7) for i in range(3))
                elif x - y == 3 and y <= 8:
                    c = tuple(int(c[i] * 0.65 + bright[i] * 0.35) for i in range(3))
            px[y][x] = c + (255,)
    # white shine dots
    for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4), (11, 11), (12, 11), (11, 12), (12, 12)):
        px[y][x] = (255, 255, 250, 255)
    px[4][4] = tuple(int(v * 0.5 + 255 * 0.5) for v in bright) + (255,)
    px[12][12] = tuple(int(v * 0.5 + 255 * 0.5) for v in bright) + (255,)
    # the symbol, engraved: dark lines with a bright lip below-right
    pts = {(2 + c, 2 + r) for r, row in enumerate(rows) for c, ch in enumerate(row) if ch == "#"}
    # only the outline of a solid shape is cut in (thin lines like the reference); its inside stays
    # plate, just a shade deeper
    edge_pts = {(x, y) for (x, y) in pts
                if name == "looting" or any((x + dx, y + dy) not in pts for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for (x, y) in edge_pts:
        q = (x + 1, y + 1)
        if q not in edge_pts and q not in pts and 3 <= q[0] <= 12 and 3 <= q[1] <= 12:
            px[q[1]][q[0]] = tuple(int(px[q[1]][q[0]][i] * 0.4 + bright[i] * 0.6) for i in range(3)) + (255,)
    for (x, y) in pts - edge_pts:
        px[y][x] = shade(px[y][x], 0.82)
    for (x, y) in edge_pts:
        px[y][x] = ink + (255,)
    png(f"{ROOT}/textures/item/{name}_upgrade.png", px)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"chaosspawner:item/{name}_upgrade"}},
              open(f"{ROOT}/models/item/{name}_upgrade.json", "w"), indent=2)
