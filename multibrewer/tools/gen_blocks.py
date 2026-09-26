"""Block model and textures for the Multi Brewer, plus the upgrade item icons (run from the mod folder).
Every element sits on whole pixels and uses 1:1 UVs."""
import json, struct, zlib

ROOT = "src/main/resources/assets/multibrewer"
T = (0, 0, 0, 0)


def png(path, px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p if len(p) == 4 else tuple(p) + (255,)) for p in row) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def rnd(x, y, s=0):
    h = (x * 374761393 + y * 668265263 + s * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def grid(c=T):
    return [[c] * 16 for _ in range(16)]


GL, G, GD = (255, 230, 140), (228, 176, 58), (150, 100, 20)
IRON, IRON_L, IRON_D = (70, 72, 82), (104, 108, 120), (44, 45, 52)

# base plate: iron with a gold rim and rivets (top), gold band (side, rows 0..1)
top = grid(IRON)
for y in range(16):
    for x in range(16):
        if rnd(x, y) % 5 == 0:
            top[y][x] = IRON_L if rnd(x, y, 1) % 2 else IRON_D
for i in range(1, 15):
    top[1][i] = GL; top[i][1] = GL; top[14][i] = GD; top[i][14] = GD
for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
    top[y][x] = G
png(f"{ROOT}/textures/block/base_top.png", top)
side = grid(IRON_D)
for x in range(16):
    side[0][x] = GL; side[1][x] = GD if x % 4 == 1 else G
png(f"{ROOT}/textures/block/base_side.png", side)

gold = grid(G)
for i in range(16):
    gold[0][i] = GL; gold[i][0] = GL
    gold[15][i] = GD; gold[i][15] = GD
png(f"{ROOT}/textures/block/gold.png", gold)


def glass_liquid(liquid, rows_glass, h, w=16):
    """Side region (0..w, 0..h): glass on top rows, liquid below. Top region at y 8..: liquid surface."""
    px = grid()
    for y in range(h):
        for x in range(w):
            if y < rows_glass:
                px[y][x] = (210, 240, 250, 90 if 0 < x < w - 1 else 150)
            else:
                d = (y - rows_glass) / max(1, h - rows_glass)
                c = tuple(int(liquid[i] * (1.15 - d * 0.4)) for i in range(3))
                px[y][x] = tuple(min(255, v) for v in c) + (225,)
        px[y][0] = (230, 250, 255, 170)
    for y in range(8, 16):
        for x in range(16):
            c = tuple(min(255, int(v * 1.3)) for v in liquid)
            px[y][x] = c + (230,)
    return px


PURPLE = (160, 60, 210)
flask = glass_liquid(PURPLE, 2, 6)
flask[3][1] = (255, 255, 255, 200)
flask[4][1] = (255, 255, 255, 160)
png(f"{ROOT}/textures/block/flask.png", flask)
neck = grid((210, 240, 250, 110))
for y in range(16):
    neck[y][0] = (235, 250, 255, 170)
png(f"{ROOT}/textures/block/neck.png", neck)
cork = grid((150, 104, 60))
for y in range(16):
    for x in range(16):
        if rnd(x, y, 5) % 3 == 0:
            cork[y][x] = (120, 80, 44)
png(f"{ROOT}/textures/block/cork.png", cork)
for name, col in (("red", (220, 50, 60)), ("green", (70, 200, 80)), ("blue", (60, 120, 230))):
    b = glass_liquid(col, 2, 4)
    for y in range(8, 16):
        for x in range(16):
            b[y][x] = (150, 104, 60, 255) if (x + y) % 3 else (120, 80, 44, 255)
    png(f"{ROOT}/textures/block/bottle_{name}.png", b)
fire = grid()
for y in range(16):
    for x in range(16):
        k = rnd(x, y, 7) % 4
        fire[y][x] = ((255, 230, 120), (255, 160, 40), (230, 90, 20), (255, 200, 60))[k]
png(f"{ROOT}/textures/block/fire.png", fire)
coal = grid()
for y in range(16):
    for x in range(16):
        coal[y][x] = (40, 34, 34) if rnd(x, y, 8) % 3 else (90, 40, 30)
png(f"{ROOT}/textures/block/coal.png", coal)


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), side_uv=None, top_uv=None):
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    f = {}
    for s in faces:
        if s in ("up", "down"):
            f[s] = {"uv": top_uv or [0, 0, w, d], "texture": tex}
        else:
            span = w if s in ("north", "south") else d
            f[s] = {"uv": side_uv or [0, 0, span, h], "texture": tex}
    return {"from": frm, "to": to, "faces": f}


def model(lit):
    els = [
        box([1, 0, 1], [15, 2, 15], "#base_side", ("north", "south", "east", "west", "down"), side_uv=[0, 0, 14, 2]),
        {"from": [1, 0, 1], "to": [15, 2, 15], "faces": {"up": {"uv": [1, 1, 15, 15], "texture": "#base_top"}}},
        box([6, 2, 6], [10, 4, 10], "#fire" if lit else "#coal", ("north", "south", "east", "west", "up")),
        box([5, 4, 5], [11, 10, 11], "#flask", ("north", "south", "east", "west"), side_uv=[0, 0, 6, 6]),
        {"from": [5, 4, 5], "to": [11, 10, 11], "faces": {"up": {"uv": [0, 8, 6, 14], "texture": "#flask"}}},
        box([7, 10, 7], [9, 13, 9], "#neck", ("north", "south", "east", "west")),
        box([7, 13, 7], [9, 14, 9], "#cork"),
        box([4, 10, 7], [7, 11, 9], "#gold"),
        box([9, 10, 7], [12, 11, 9], "#gold"),
    ]
    for (x, z, col) in ((2, 2, "red"), (11, 2, "green"), (2, 11, "blue")):
        els.append(box([x, 2, z], [x + 3, 6, z + 3], f"#b_{col}", ("north", "south", "east", "west"), side_uv=[0, 0, 3, 4]))
        els.append({"from": [x, 2, z], "to": [x + 3, 6, z + 3], "faces": {"up": {"uv": [0, 8, 3, 11], "texture": f"#b_{col}"}}})
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "textures": {
            "particle": "multibrewer:block/base_top",
            "base_top": "multibrewer:block/base_top", "base_side": "multibrewer:block/base_side",
            "gold": "multibrewer:block/gold", "flask": "multibrewer:block/flask", "neck": "multibrewer:block/neck",
            "cork": "multibrewer:block/cork", "fire": "multibrewer:block/fire", "coal": "multibrewer:block/coal",
            "b_red": "multibrewer:block/bottle_red", "b_green": "multibrewer:block/bottle_green",
            "b_blue": "multibrewer:block/bottle_blue",
        },
        "elements": els,
    }


for lit, name in ((False, "multi_brewer"), (True, "multi_brewer_on")):
    with open(f"{ROOT}/models/block/{name}.json", "w") as f:
        json.dump(model(lit), f, indent=2)
        f.write("\n")

# upgrade icons: a gold-rimmed plate with a symbol
SYMBOLS = {
    "speed": (["................", "................", "................", "....#...#.......", "....##..##......",
               "....###.###.....", "....####.###....", "....#####.###...", "....####.###....", "....###.###.....",
               "....##..##......", "....#...#.......", "................", "................", "................",
               "................"], (90, 220, 240)),
    "efficiency": (["................", "................", "................", ".......##.......", "......####......",
                    ".....##..##.....", "....##....##....", "....#..##..#....", "....#.####.#....", "....##.##.##....",
                    ".....##..##.....", "......####......", ".......##.......", "................", "................",
                    "................"], (110, 230, 110)),
    "potency": (["................", "................", "................", ".......##.......", ".......##.......",
                 "...##..##..##...", "....########....", ".....######.....", "....########....", "...##..##..##...",
                 ".......##.......", ".......##.......", "................", "................", "................",
                 "................"], (210, 110, 250)),
}
for name, (shape, col) in SYMBOLS.items():
    px = grid()
    for y in range(1, 15):
        for x in range(1, 15):
            px[y][x] = (40, 30, 44)
    for i in range(1, 15):
        px[1][i] = GL; px[i][1] = GL; px[14][i] = GD; px[i][14] = GD
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            if 0 < i < 15:
                px[y][x] = (18, 12, 14)
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == "#":
                light = y < 7
                px[y][x] = tuple(min(255, int(v * (1.2 if light else 0.9))) for v in col)
    png(f"{ROOT}/textures/item/{name}_upgrade.png", px)
