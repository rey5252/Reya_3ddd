"""Textures and model files for Starfall (run from the mod folder)."""
import json, math, os, struct, zlib

ROOT = "src/main/resources/assets/starfall"
T = (0, 0, 0, 0)


def png(path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + b"".join(bytes(p if len(p) == 4 else tuple(p) + (255,)) for p in row) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", len(px[0]), len(px), 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def grid(c=T):
    return [[c] * 16 for _ in range(16)]


def put(g, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        g[y][x] = c


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- the remote
# Held upright: a dark slate body with a bevel, the flip cover swung open on its hinge at the top,
# and under it the one button, lit in the selected weapon's colour.
OUT, BODY, BODY_L, BODY_D = (14, 16, 22), (46, 52, 64), (78, 86, 102), (30, 34, 42)
COVER, COVER_L, COVER_GLASS = (150, 190, 210), (210, 236, 246), (150, 200, 230, 110)
STEEL = (120, 128, 140)

BUTTONS = {
    "railgun": ((255, 52, 52), (255, 170, 160), (150, 16, 20)),
    "gungnir": ((255, 128, 36), (255, 214, 140), (150, 64, 12)),
    "seven_stars": ((170, 104, 255), (232, 210, 255), (88, 40, 150)),
}

for name, (btn, btn_l, btn_d) in BUTTONS.items():
    g = grid()
    # body, x 4..11, y 3..15
    for y in range(3, 16):
        for x in range(4, 12):
            g[y][x] = BODY
    for y in range(3, 16):
        g[y][4] = OUT
        g[y][11] = OUT
    for x in range(4, 12):
        g[3][x] = OUT
        g[15][x] = OUT
    for y in range(4, 15):
        g[y][5] = BODY_L
        g[y][10] = BODY_D
    for x in range(5, 11):
        g[4][x] = BODY_L
        g[14][x] = BODY_D
    put(g, 4, 3, T); put(g, 11, 3, T); put(g, 4, 15, T); put(g, 11, 15, T)
    # button well and the button
    for y in range(6, 10):
        for x in range(6, 10):
            g[y][x] = OUT
    for y in range(6, 9):
        for x in range(6, 9):
            g[y][x] = btn
    put(g, 6, 6, btn_l); put(g, 7, 6, btn_l); put(g, 6, 7, btn_l)
    put(g, 9, 9, btn_d); put(g, 8, 9, btn_d); put(g, 9, 8, btn_d)
    # hinge across the top of the well, cover swung up and back above the body
    for x in range(5, 11):
        g[5][x] = STEEL
    for y in range(0, 5):
        for x in range(6, 10):
            g[y][x] = COVER if y in (0, 4) or x in (6, 9) else COVER_GLASS
    put(g, 7, 1, COVER_L); put(g, 8, 2, COVER_L)
    # status lights and the grille
    put(g, 6, 11, btn_l); put(g, 8, 11, (90, 220, 120))
    for x in (6, 8):
        put(g, x, 13, BODY_D)
    png(f"{ROOT}/textures/item/stellar_remote_{name}.png", g)

write_json(f"{ROOT}/models/item/stellar_remote.json", {
    "parent": "minecraft:item/handheld",
    "textures": {"layer0": "starfall:item/stellar_remote_railgun"},
    "overrides": [
        {"predicate": {"starfall:skill": 0.5}, "model": "starfall:item/stellar_remote_gungnir"},
        {"predicate": {"starfall:skill": 1.0}, "model": "starfall:item/stellar_remote_seven_stars"},
    ],
})
for name in ("gungnir", "seven_stars"):
    write_json(f"{ROOT}/models/item/stellar_remote_{name}.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": f"starfall:item/stellar_remote_{name}"},
    })

# ---------------------------------------------------------------- the needle
# Black glassy metal with faint ember seams running down it.
g = grid()
for y in range(16):
    for x in range(16):
        n = (x * 7 + y * 13) % 11
        g[y][x] = (10 + n, 9 + n, 14 + n)
for x in (3, 12):
    for y in range(16):
        g[y][x] = (64, 24, 10) if (y + x) % 4 else (150, 60, 20)
for y in range(16):
    g[y][0] = (4, 4, 6)
    g[y][15] = (26, 24, 32)
png(f"{ROOT}/textures/block/star_needle.png", g)
write_json(f"{ROOT}/models/block/star_needle.json", {
    "parent": "minecraft:block/cube_all", "textures": {"all": "starfall:block/star_needle"}})
write_json(f"{ROOT}/blockstates/star_needle.json", {"variants": {"": {"model": "starfall:block/star_needle"}}})
write_json(f"{ROOT}/models/item/star_needle.json", {"parent": "starfall:block/star_needle"})

# ---------------------------------------------------------------- the star-core crystal
# A cluster of violet-white shards (drawn on a cross model) with a white-hot heart.
g = grid()
V_D, V, V_L, V_W = (70, 30, 130), (150, 90, 240), (200, 160, 255), (250, 240, 255)
SHARDS = [(7, 1, 15, 2), (4, 5, 15, 1), (11, 6, 15, 1), (2, 10, 15, 1), (13, 10, 15, 1)]
for cx, top, bottom, half in SHARDS:
    for y in range(top, bottom + 1):
        w = half if y > top + 1 else 0
        for x in range(cx - w - 1, cx + w + 2):
            if not 0 <= x < 16:
                continue
            edge = x in (cx - w - 1, cx + w + 1)
            g[y][x] = V_D if edge else (V_L if x <= cx else V)
    put(g, cx, top, V_W)
for y in range(11, 16):
    for x in range(6, 10):
        g[y][x] = V_W if 7 <= x <= 8 and y >= 12 else V_L
png(f"{ROOT}/textures/block/star_core.png", g)
write_json(f"{ROOT}/models/block/star_core.json", {
    "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
    "textures": {"cross": "starfall:block/star_core"}})
write_json(f"{ROOT}/blockstates/star_core.json", {"variants": {"": {"model": "starfall:block/star_core"}}})
write_json(f"{ROOT}/models/item/star_core.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "starfall:block/star_core"}})
print("ok")
