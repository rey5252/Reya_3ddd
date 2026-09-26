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

# cage: a thick metal rim, bars across, glowing cyan corners; see-through between the bars
cage = grid()
for i in range(16):
    for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
        cage[y][x] = DARK
    for (x, y) in ((i, 1), (1, i)):
        if 0 < i < 15:
            cage[y][x] = METAL_L
    for (x, y) in ((i, 14), (14, i)):
        if 0 < i < 15:
            cage[y][x] = METAL
for k in (5, 10):
    for i in range(2, 14):
        cage[i][k] = METAL
        cage[k][i] = METAL
    for i in range(2, 14):
        cage[i][k + 1] = DARK if cage[i][k + 1] == T else cage[i][k + 1]
for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
    cage[y][x] = CYAN_L
for (x, y) in ((2, 1), (1, 2), (13, 1), (14, 2), (1, 13), (2, 14), (14, 13), (13, 14)):
    cage[y][x] = CYAN
for (x, y) in ((5, 5), (10, 5), (5, 10), (10, 10)):
    cage[y][x] = CYAN
png(f"{ROOT}/textures/block/cage.png", cage)

model = {
    "parent": "minecraft:block/block",
    "render_type": "minecraft:cutout",
    "textures": {"particle": "chaosspawner:block/cage", "cage": "chaosspawner:block/cage"},
    "elements": [
        {"from": [0, 0, 0], "to": [16, 16, 16],
         "faces": {s: {"uv": [0, 0, 16, 16], "texture": "#cage"} for s in ("north", "south", "east", "west", "up", "down")}},
    ],
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
