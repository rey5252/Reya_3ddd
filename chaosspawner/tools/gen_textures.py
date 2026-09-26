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
# upgrades: tilted 3D medallions, 64x64 and animated (12 frames): a thick engraved metal rim with a
# visible edge, a glowing dome that fades from white to the upgrade's colour, a symbol on the dome and
# a shimmer that sweeps across the medallion.
import colorsys

MED = {
    # rim dark, rim, rim light, dome colour, symbol
    "speed":      ((10, 60, 62), (30, 150, 150), (150, 250, 240), (60, 230, 220), (10, 110, 120)),
    "looting":    ((80, 50, 8), (190, 140, 30), (255, 235, 150), (250, 200, 60), (120, 70, 10)),
    "quantity":   ((50, 14, 70), (150, 60, 200), (240, 190, 255), (220, 120, 255), (90, 20, 130)),
    "experience": ((20, 60, 10), (80, 170, 40), (210, 255, 160), (150, 240, 80), (40, 110, 20)),
}
FR, SZ = 12, 64
ANG = math.radians(-24)
CX, CY, RX, RY = 31.0, 32.0, 24.0, 27.0


def ell(x, y, ox=0.0, oy=0.0, scale=1.0):
    """Normalised radius of (x, y) in the tilted medallion ellipse (1 = its edge)."""
    dx, dy = x - CX - ox, y - CY - oy
    u = dx * math.cos(ANG) + dy * math.sin(ANG)
    v = -dx * math.sin(ANG) + dy * math.cos(ANG)
    return math.hypot(u / (RX * scale), v / (RY * scale)), math.atan2(v, u)


def symbol(name, x, y):
    # coordinates relative to the dome centre, a little up-left of the medallion centre
    X, Y = x - 30.5, y - 30.0
    if name == "speed":      # an arrow pointing up and right
        return (seg_dist(X, Y, -9, 8, 6, -7) <= 2.3 or seg_dist(X, Y, 8, -9, -2, -8) <= 2.0
                or seg_dist(X, Y, 8, -9, 7, 1) <= 2.0)
    if name == "looting":    # a sword: long blade to the top right, short guard, grip and pommel
        return (seg_dist(X, Y, -3.5, 3.5, 10, -10) <= 2.0 - max(0.0, X - 6) * 0.25
                or seg_dist(X, Y, -6.5, 0.5, -0.5, 6.5) <= 1.4
                or seg_dist(X, Y, -4, 4, -8, 8) <= 1.3 or math.hypot(X + 9.3, Y - 9.3) <= 2.0)
    if name == "quantity":   # a bold plus
        return (abs(X) <= 2.6 and abs(Y) <= 10) or (abs(Y) <= 2.6 and abs(X) <= 10)
    # experience: a four-point star inside a ring
    r = math.hypot(X, Y)
    star = abs(X) * abs(Y) <= 5.0 and abs(X) + abs(Y) <= 9.0
    return star or 9.5 <= r <= 11.5


def seg_dist(px_, py_, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px_ - ax) * dx + (py_ - ay) * dy) / float(dx * dx + dy * dy)))
    return math.hypot(px_ - (ax + t * dx), py_ - (ay + t * dy))


def mixc(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


for n, name in enumerate(CARDS):
    rdark, rim, rlight, dome, sym = MED[name]
    sheet = [[T] * SZ for _ in range(SZ * FR)]
    # symbol coverage (4x supersampled) is the same in every frame
    cov = [[sum(symbol(name, x + (i + 0.5) / 4, y + (j + 0.5) / 4) for i in range(4) for j in range(4)) / 16.0
            for x in range(SZ)] for y in range(SZ)]
    # a soft halo of the dome colour round the symbol
    glow = [[0.0] * SZ for _ in range(SZ)]
    for y in range(SZ):
        for x in range(SZ):
            if cov[y][x] < 1:
                near = max(cov[yy][xx] for yy in range(max(0, y - 2), min(SZ, y + 3))
                           for xx in range(max(0, x - 2), min(SZ, x + 3)))
                glow[y][x] = near
    for f in range(FR):
        sweep = -40 + f * (150 / FR)            # position of the shimmer band along x + y
        for y in range(SZ):
            for x in range(SZ):
                acc, alpha = [0.0, 0.0, 0.0], 0.0
                for (sx, sy) in ((0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)):
                    fx, fy = x + sx, y + sy
                    r, a = ell(fx, fy)
                    if r <= 1.0:
                        if r > 0.95:
                            c = rdark                               # outline
                        elif r > 0.72:
                            # the rim: lit from the top left, engraved with rings and notches
                            lightk = 0.5 + 0.5 * math.cos(a + 2.4)
                            h0, l0, s0 = colorsys.rgb_to_hls(*(v / 255.0 for v in rim))
                            hr = colorsys.hls_to_rgb((h0 + 0.09 * math.sin(a * 2 + 1)) % 1.0, l0, min(1.0, s0 * 1.1))
                            rim_c = tuple(v * 255 for v in hr)
                            c = mixc(rdark, rim_c, 0.35 + 0.65 * lightk)
                            c = mixc(c, rlight, max(0.0, lightk - 0.55) * 1.6)
                            if abs(r - 0.84) < 0.018 or abs(r - 0.76) < 0.02:
                                c = mixc(c, rdark, 0.6)
                            notch = (int((a + math.pi) / (2 * math.pi) * 28) % 3 == 0) and 0.79 < r < 0.83
                            if notch:
                                c = mixc(c, rlight, 0.45)
                            grain = ((hsh(int(fx * 2), int(fy * 2), n) % 9) - 4) / 60.0
                            c = mixc(c, (255, 255, 255) if grain > 0 else (0, 0, 0), abs(grain))
                        elif r > 0.69:
                            c = mixc(rdark, (0, 0, 0), 0.3)          # groove round the dome
                        else:
                            # the dome: white-hot up and to the left, the upgrade colour at its edge
                            dx, dy = fx - 25.0, fy - 23.0
                            h = min(1.0, math.hypot(dx, dy) / 26.0)
                            c = mixc((255, 255, 255), dome, (h - 0.15) * 1.5)
                            c = mixc(c, rdark, max(0.0, r - 0.55) * 1.4)
                            g = glow[y][x]
                            if g > 0:
                                c = mixc(c, dome, g * 0.6)
                            k = cov[y][x]
                            if k > 0:
                                c = mixc(c, sym, k)
                        # shimmer
                        band = abs((fx + fy) - sweep)
                        if band < 7:
                            c = mixc(c, (255, 255, 255), (1 - band / 7) * 0.6)
                    else:
                        # the medallion's edge, seen below and right of it
                        r2, a2 = ell(fx, fy, 2.2, 3.0)
                        if r2 > 1.0:
                            continue
                        c = mixc(rdark, rim, 0.25 + 0.2 * math.sin(a2 * 6))
                        if r2 > 0.96:
                            c = mixc(rdark, (0, 0, 0), 0.5)
                    acc = [acc[i] + c[i] for i in range(3)]
                    alpha += 1
                if alpha:
                    sheet[f * SZ + y][x] = tuple(int(v / alpha) for v in acc) + (int(255 * min(1.0, alpha / 2.0)) if alpha < 4 else 255,)
    # the symbol's soft outer glow is baked in above; write the strip and its animation file
    png(f"{ROOT}/textures/item/{name}_upgrade.png", sheet)
    json.dump({"animation": {"frametime": 2}}, open(f"{ROOT}/textures/item/{name}_upgrade.png.mcmeta", "w"), indent=2)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"chaosspawner:item/{name}_upgrade"}},
              open(f"{ROOT}/models/item/{name}_upgrade.json", "w"), indent=2)
