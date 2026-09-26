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

# cage side: rims top and bottom, thin bars between, corners left open for the posts
cage = grid()
for x in range(2, 14):
    for y in (0, 15):
        cage[y][x] = NAVY_D
    cage[1][x] = NAVY_L
    cage[14][x] = NAVY
    cage[2][x] = NAVY_D
    cage[13][x] = NAVY_D
for k, bx in enumerate(BARS):
    for y in range(3, 13):
        cage[y][bx] = NAVY
    # a teal glint running down part of each bar, like the reference
    for y in range(4 + k % 3, 9 + k % 3):
        cage[y][bx] = TEAL if y % 4 else TEAL_L
png(f"{ROOT}/textures/block/cage.png", cage)

# top: dark rim around the edge and a grid of bars, corners left for the post caps
top = grid()
for i in range(2, 14):
    for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
        top[y][x] = NAVY_D
    for (x, y) in ((i, 1), (1, i)):
        top[y][x] = NAVY_L
    for (x, y) in ((i, 14), (14, i)):
        top[y][x] = NAVY
for bx in BARS:
    for i in range(2, 14):
        top[i][bx] = NAVY
        top[bx][i] = NAVY
for bx in BARS:
    for by in BARS:
        top[by][bx] = TEAL
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

# crystal shard: teal pane with a lighter core
shard = grid()
for y in range(16):
    for x in range(16):
        edge = x in (0, 15) or y in (0, 15)
        core = 5 <= x <= 10 and 5 <= y <= 10
        shard[y][x] = (30, 120, 130) if edge else (200, 235, 235) if core else (60, 190, 190) if (x + y) % 7 else (110, 225, 220)
png(f"{ROOT}/textures/block/shard.png", shard)


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
# crystal shards leaning in the corners and flat crystal panes on two walls
for (x, z, ang, axis) in ((3, 3, 22.5, "x"), (11, 11, -22.5, "x"), (11, 3, -22.5, "z"), (3, 11, 22.5, "z")):
    els.append(box([x, 1, z], [x + 2, 8, z + 2], "#shard", uvs={f: [4, 2, 8, 14] for f in ("north", "south", "east", "west", "up", "down")},
                   rot={"angle": ang, "axis": axis, "origin": [x + 1, 1, z + 1]}, glow=True))
els.append(box([4, 4, 2.5], [8, 9, 3.5], "#shard", uvs={f: [2, 2, 14, 14] for f in ("north", "south", "east", "west", "up", "down")},
               rot={"angle": 22.5, "axis": "z", "origin": [6, 6, 3]}, glow=True))
els.append(box([12.5, 5, 8], [13.5, 10, 12], "#shard", uvs={f: [2, 2, 14, 14] for f in ("north", "south", "east", "west", "up", "down")},
               rot={"angle": -22.5, "axis": "x", "origin": [13, 7, 10]}, glow=True))

model = {
    "parent": "minecraft:block/block",
    "render_type": "minecraft:cutout",
    "ambientocclusion": False,
    "textures": {"particle": "chaosspawner:block/cage", "cage": "chaosspawner:block/cage", "top": "chaosspawner:block/cage_top",
                 "bottom": "chaosspawner:block/cage_bottom", "post": "chaosspawner:block/post", "cap": "chaosspawner:block/cap",
                 "floor": "chaosspawner:block/floor", "shard": "chaosspawner:block/shard"},
    "elements": els,
}
json.dump(model, open(f"{ROOT}/models/block/chaos_spawner.json", "w"), indent=2)
json.dump({"variants": {"": {"model": "chaosspawner:block/chaos_spawner"}}}, open(f"{ROOT}/blockstates/chaos_spawner.json", "w"), indent=2)
json.dump({"parent": "chaosspawner:block/chaos_spawner"}, open(f"{ROOT}/models/item/chaos_spawner.json", "w"), indent=2)

# soul crystal: a faceted cyan crystal; the filled one has a two-colour soul inside (tinted per mob)
CRYSTAL = [
    "................",
    ".......##.......",
    "......#LL#......",
    ".....#LWLL#.....",
    "....#LWLLLD#....",
    "....#LLLLLD#....",
    "...#LLLLLLDD#...",
    "...#LLLLLLDD#...",
    "...#LLLLLLDD#...",
    "....#LLLLDD#....",
    "....#LLLLDD#....",
    ".....#LLDD#.....",
    "......#DD#......",
    ".......##.......",
    "................",
    "................"]
cols = {"#": (20, 60, 76), "L": (150, 230, 240, 200), "W": (255, 255, 255), "D": (60, 160, 180, 220)}
empty = grid()
for y, row in enumerate(CRYSTAL):
    for x, ch in enumerate(row):
        if ch in cols:
            c = cols[ch]
            empty[y][x] = c if len(c) == 4 else c + (255,)
png(f"{ROOT}/textures/item/soul_crystal.png", empty)
soul1, soul2 = grid(), grid()
for (x, y) in ((6, 6), (7, 6), (8, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9)):
    soul1[y][x] = (255, 255, 255, 255)
for (x, y) in ((7, 5), (6, 10), (8, 10), (9, 6), (7, 11)):
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

# upgrades: a dark tile with a cyan rim and a symbol
SYMBOLS = {
    "speed": (["....#...#.....", "....##..##....", "....###.###...", "....####.###..", "....###.###...", "....##..##....",
               "....#...#....."], (90, 230, 250)),
    "looting": (["..........##..", ".........##...", "........##....", "...#...##.....", "....#.##......", ".....##.......",
                 "....#.#.......", "...#...#......"], (240, 200, 90)),
    "quantity": (["...##....##...", "..####..####..", "...##....##...", "..............", "...##....##...",
                  "..####..####..", "...##....##..."], (230, 110, 230)),
    "experience": (["......##......", ".....####.....", "....##..##....", "...##.##.##...", "....##..##....",
                    ".....####.....", "......##......"], (130, 240, 90)),
}
for name, (shape, col) in SYMBOLS.items():
    px = grid()
    for y in range(1, 15):
        for x in range(1, 15):
            px[y][x] = DARK + (255,)
    for i in range(1, 15):
        px[1][i] = CYAN_L + (255,); px[i][1] = CYAN_L + (255,)
        px[14][i] = CYAN_D + (255,); px[i][14] = CYAN_D + (255,)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            if 0 < i < 15:
                px[y][x] = (6, 10, 16, 255)
    top = (16 - len(shape)) // 2
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == "#":
                px[top + y][1 + x] = tuple(min(255, int(v * (1.15 if y < len(shape) / 2 else 0.85))) for v in col) + (255,)
    png(f"{ROOT}/textures/item/{name}_upgrade.png", px)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"chaosspawner:item/{name}_upgrade"}},
              open(f"{ROOT}/models/item/{name}_upgrade.json", "w"), indent=2)
