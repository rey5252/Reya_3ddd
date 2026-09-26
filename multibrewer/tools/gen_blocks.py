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
for x in range(16):
    for y in range(16):
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if 4.6 <= d <= 5.4:
            top[y][x] = G if (x + y) % 2 else GD
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


def liquid_tex(col):
    """Opaque liquid body: light edge on the left and top, darker bottom, a white shine."""
    px = grid()
    for y in range(16):
        for x in range(16):
            f = 1.15 - y / 16 * 0.45
            px[y][x] = tuple(min(255, int(v * f)) for v in col)
    for i in range(16):
        px[i][0] = tuple(min(255, int(v * 1.35)) for v in col)
        px[0][i] = tuple(min(255, int(v * 1.35)) for v in col)
    px[1][1] = (245, 240, 255)
    return px


PURPLE = (160, 60, 210)
flask_tex = liquid_tex(PURPLE)
for (x, y) in ((4, 2),):
    flask_tex[y][x] = tuple(min(255, int(v * 1.45)) for v in PURPLE)
png(f"{ROOT}/textures/block/flask.png", flask_tex)
glass = grid((200, 232, 240))
for y in range(16):
    glass[y][0] = (240, 252, 255)
    glass[y][15] = (150, 190, 205)
png(f"{ROOT}/textures/block/neck.png", glass)
cork = grid((150, 104, 60))
for y in range(16):
    for x in range(16):
        if rnd(x, y, 5) % 3 == 0:
            cork[y][x] = (120, 80, 44)
png(f"{ROOT}/textures/block/cork.png", cork)
for name, col in (("red", (220, 50, 60)), ("green", (70, 200, 80)), ("blue", (60, 120, 230))):
    b = liquid_tex(col)
    for x in range(16):
        b[1][x] = (236, 222, 190)          # paper label round the bottle
        b[2][x] = tuple(int(v * 0.95) for v in col)
    b[1][1] = (120, 90, 60)
    png(f"{ROOT}/textures/block/bottle_{name}.png", b)
iron = grid(IRON_D)
for y in range(16):
    for x in range(16):
        if rnd(x, y, 9) % 4 == 0:
            iron[y][x] = IRON
for i in range(16):
    iron[0][i] = IRON_L
png(f"{ROOT}/textures/block/iron.png", iron)
stone = grid((120, 118, 112))
for y in range(16):
    for x in range(16):
        if rnd(x, y, 10) % 3 == 0:
            stone[y][x] = (96, 94, 90)
for i in range(16):
    stone[0][i] = (150, 148, 142)
png(f"{ROOT}/textures/block/stone.png", stone)
mortar = grid((96, 94, 90))
for y in range(1, 3):
    for x in range(1, 3):
        mortar[y][x] = (40, 30, 26)
mortar[1][1] = (90, 170, 70)
mortar[2][2] = (60, 130, 50)
mortar[1][2] = (200, 80, 90)
png(f"{ROOT}/textures/block/mortar_top.png", mortar)
wood = grid((110, 76, 44))
for y in range(16):
    wood[y][0] = (140, 100, 60)
png(f"{ROOT}/textures/block/wood.png", wood)
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


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), rel=False):
    f = {}
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    for s in faces:
        if rel:
            f[s] = {"uv": [0, 0, w, d] if s in ("up", "down") else [0, 0, w if s in ("north", "south") else d, h], "texture": tex}
        elif s in ("up", "down"):
            f[s] = {"uv": [frm[0], frm[2], to[0], to[2]], "texture": tex}
        elif s in ("north", "south"):
            f[s] = {"uv": [frm[0], 16 - to[1], to[0], 16 - frm[1]], "texture": tex}
        else:
            f[s] = {"uv": [frm[2], 16 - to[1], to[2], 16 - frm[1]], "texture": tex}
    return {"from": frm, "to": to, "faces": f}


def model(lit):
    sides = ("north", "south", "east", "west")
    els = [
        # two-step base with gold studs on the corners
        box([0, 0, 0], [16, 1, 16], "#base_side", sides + ("down", "up"), rel=True),
        box([1, 1, 1], [15, 2, 15], "#base_side", sides, rel=True),
        {"from": [1, 1, 1], "to": [15, 2, 15], "faces": {"up": {"uv": [1, 1, 15, 15], "texture": "#base_top"}}},
        box([0, 1, 0], [2, 3, 2], "#gold", sides + ("up",)), box([14, 1, 0], [16, 3, 2], "#gold", sides + ("up",)),
        box([0, 1, 14], [2, 3, 16], "#gold", sides + ("up",)), box([14, 1, 14], [16, 3, 16], "#gold", sides + ("up",)),
        # burner: iron ring round the fire, four gold legs up to the flask
        box([5, 2, 5], [11, 3, 6], "#iron", sides + ("up",)), box([5, 2, 10], [11, 3, 11], "#iron", sides + ("up",)),
        box([5, 2, 6], [6, 3, 10], "#iron", sides + ("up",)), box([10, 2, 6], [11, 3, 10], "#iron", sides + ("up",)),
        box([6, 2, 6], [10, 3, 10], "#fire" if lit else "#coal", ("up",)),
        box([5, 3, 5], [6, 5, 6], "#gold", sides), box([10, 3, 5], [11, 5, 6], "#gold", sides),
        box([5, 3, 10], [6, 5, 11], "#gold", sides), box([10, 3, 10], [11, 5, 11], "#gold", sides),
        # round-bellied flask: narrow bottom, wide belly, narrow shoulder, glass neck, gold ring, cork
        box([6, 5, 6], [10, 6, 10], "#flask", sides + ("down",), rel=True),
        box([5, 6, 5], [11, 10, 11], "#flask", sides + ("down", "up"), rel=True),
        box([6, 10, 6], [10, 11, 10], "#flask", sides + ("up",), rel=True),
        box([7, 11, 7], [9, 14, 9], "#neck", sides),
        box([6, 12, 6], [10, 13, 10], "#gold", sides + ("up", "down")),
        box([7, 14, 7], [9, 15, 9], "#cork", sides + ("up",)),
        # mortar with herbs and a pestle in the fourth corner
        box([11, 2, 11], [14, 4, 14], "#stone", sides, rel=True),
        {"from": [11, 2, 11], "to": [14, 4, 14], "faces": {"up": {"uv": [0, 0, 3, 3], "texture": "#mortar_top"}}},
        box([13, 4, 12], [14, 7, 13], "#wood", sides + ("up",)),
    ]
    for (x, z, col) in ((2, 2, "red"), (11, 2, "green"), (2, 11, "blue")):
        els.append(box([x, 2, z], [x + 3, 6, z + 3], f"#b_{col}", sides, rel=True))
        els.append({"from": [x, 2, z], "to": [x + 3, 6, z + 3], "faces": {"up": {"uv": [4, 4, 7, 7], "texture": f"#b_{col}"}}})
        els.append(box([x + 1, 6, z + 1], [x + 2, 7, z + 2], "#neck", sides))
        els.append(box([x + 1, 7, z + 1], [x + 2, 8, z + 2], "#cork", sides + ("up",)))
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": "multibrewer:block/base_top",
            "base_top": "multibrewer:block/base_top", "base_side": "multibrewer:block/base_side",
            "gold": "multibrewer:block/gold", "flask": "multibrewer:block/flask", "neck": "multibrewer:block/neck",
            "cork": "multibrewer:block/cork", "fire": "multibrewer:block/fire", "coal": "multibrewer:block/coal",
            "iron": "multibrewer:block/iron", "stone": "multibrewer:block/stone", "wood": "multibrewer:block/wood",
            "mortar_top": "multibrewer:block/mortar_top",
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
