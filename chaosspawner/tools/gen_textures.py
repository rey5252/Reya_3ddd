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
# The tokens are 32x32 so there is room for detail: a mitred metal frame (dark bevel top/left, dark
# groove and pale lip bottom/right), rivets in the corners, a small gem set in the middle of each side,
# a glowing plate with glints, shine stars and corner filigree, and an engraved symbol with an inner
# highlight and a bright lip under it.
TS = 32


def seg_dist(px_, py_, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px_ - ax) * dx + (py_ - ay) * dy) / float(dx * dx + dy * dy)))
    return math.hypot(px_ - (ax + t * dx), py_ - (ay + t * dy))


def chevron(x, y, ox):
    # a solid ">" between x = ox and ox + 9, rows 9..22
    if not (9 <= y <= 22):
        return False
    k = abs(y - 15.5) / 6.5          # 0 at the point row, 1 at the ends
    left = ox + 5.5 * (1 - k)
    return left <= x <= left + 3.6


SYMBOLS32 = {
    "speed": lambda x, y: chevron(x, y, 7) or chevron(x, y, 14),
    "looting": lambda x, y: (seg_dist(x, y, 13.5, 17.5, 22.5, 8.5) <= 1.9 - 0.08 * max(0.0, (x - 18))
                             or seg_dist(x, y, 9.5, 15.5, 15.5, 21.5) <= 1.25
                             or seg_dist(x, y, 12.0, 19.0, 9.0, 22.0) <= 1.15
                             or math.hypot(x - 8.2, y - 22.8) <= 1.8),
    "quantity": lambda x, y: (abs(x - 15.5) + abs(y - 15.5) <= 4.6
                              or any(abs(x - cx) + abs(y - cy) <= 2.3 for cx, cy in ((10.5, 10.5), (20.5, 10.5), (10.5, 20.5), (20.5, 20.5)))),
    "experience": lambda x, y: math.hypot(x - 15.5, y - 15.5) <= 7.6,
}


def mask_of(fn):
    m = set()
    for y in range(TS):
        for x in range(TS):
            hits = sum(fn(x + ox, y + oy) for ox in (0.25, 0.75) for oy in (0.25, 0.75))
            if hits >= 2:
                m.add((x, y))
    return m


def mix3(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


for n, name in enumerate(CARDS):
    fdark, frame, edge, mid, bright, ink = METALS[name]
    pale = mix3(edge, bright, 0.7)
    px = [[T] * TS for _ in range(TS)]
    L = TS - 1
    for y in range(TS):
        for x in range(TS):
            dl, dt, dr, db = x, y, L - x, L - y
            if min(dl, dr) + min(dt, db) < 2:
                continue                                    # rounded outer corners
            d = min(dl, dt, dr, db)
            # which side of the mitred frame this pixel belongs to
            lit = min(dl, dt) < min(dr, db) or (min(dl, dt) == min(dr, db) and x + y < L)
            along = 1.0 - abs(((x if min(dt, db) <= min(dl, dr) else y)) - 15.5) / 15.5
            diag = (x + y) / (2.0 * L)
            if d <= 5 and lit:
                if d == 0:
                    c = shade(fdark, 0.85 + 0.3 * along)
                elif d in (1, 2):
                    c = shade(frame, (1.12 if d == 1 else 0.95) * (0.88 + 0.3 * along))
                elif d == 3:
                    c = shade(fdark, 1.1)
                else:
                    c = shade(edge, (1.32 if d == 4 else 1.12) - 0.4 * diag + 0.08 * along)
            elif d <= 5:
                if d == 0:
                    c = shade(pale, 0.72)
                elif d in (1, 2):
                    c = shade(pale, (1.0 if d == 2 else 0.9) * (0.86 + 0.2 * along))
                elif d in (3, 4):
                    c = shade(edge, (0.46 if d == 3 else 0.58) + 0.1 * along)
                else:
                    c = shade(edge, 0.85 + 0.1 * along)
            else:
                # the plate: glow in the middle, lit from the top left, a fine brushed grain
                r = max(abs(x - 15.5), abs(y - 15.0)) / 10.0
                t = max(0.0, min(1.0, 1.0 - r))
                c = mix3(mid, bright, t * 0.72)
                c = shade(c, 1.14 - 0.32 * diag + ((hsh(0, y, n + 7) % 5) - 2) * 0.012 + ((hsh(x, y, n) % 5) - 2) * 0.018)
                if r > 0.88:
                    c = mix3(c, edge, 0.42)
                if x == 6 or y == 6:
                    c = mix3(c, bright, 0.35)
                elif x == 25 or y == 25:
                    c = shade(c, 0.84)
                # glints across the upper right, strongest in their middle
                g = max(0.0, 1.0 - abs(y - 11.0) / 9.0)
                k = x - y
                if k in (9, 10, 11) and y <= 18:
                    c = mix3(c, bright, (0.85 if k == 10 else 0.5) * g)
                elif k in (8, 12) and y <= 17:
                    c = mix3(c, bright, 0.22 * g)
                elif k in (14, 15) and y <= 10:
                    c = mix3(c, bright, 0.55 * g)
            px[y][x] = c + (255,)

    # rivets in the four frame corners
    for (rx, ry) in ((2, 2), (L - 4, 2), (2, L - 4), (L - 4, L - 4)):
        for yy in range(ry, ry + 3):
            for xx in range(rx, rx + 3):
                px[yy][xx] = shade(fdark, 0.9) + (255,)
        px[ry][rx] = shade(frame, 1.3) + (255,)
        px[ry][rx + 1] = mix3(frame, pale, 0.6) + (255,)
        px[ry + 1][rx] = mix3(frame, pale, 0.6) + (255,)
        px[ry + 1][rx + 1] = mix3(pale, (255, 255, 255), 0.5) + (255,)
        px[ry + 2][rx + 2] = shade(fdark, 0.6) + (255,)

    # a small cut gem set in the middle of each side
    gem = [".##.", "####", "####", ".##."]
    for (gx, gy) in ((14, 1), (14, L - 4), (1, 14), (L - 4, 14)):
        for r_, row in enumerate(gem):
            for c_, ch in enumerate(row):
                if ch != "#":
                    continue
                xx, yy = gx + c_, gy + r_
                col = mix3(bright, (255, 255, 255), 0.55) if (r_ + c_) <= 1 else bright if (r_ + c_) <= 3 else mid if (r_ + c_) <= 4 else edge
                px[yy][xx] = col + (255,)
        for (ox, oy) in ((0, 0), (3, 0), (0, 3), (3, 3)):
            px[gy + oy][gx + ox] = shade(fdark, 0.8) + (255,)

    # filigree in the free plate corners (top right and bottom left): small engraved brackets
    for (fx, fy, sx_, sy_) in ((24, 7, -1, 1), (7, 24, 1, -1)):
        for i in range(4):
            for (xx, yy) in ((fx + sx_ * i, fy), (fx, fy + sy_ * i)):
                px[yy][xx] = mix3(ink, edge, 0.35) + (255,)
        px[fy + sy_ * 2][fx + sx_ * 2] = mix3(bright, (255, 255, 255), 0.4) + (255,)

    # the symbol: engraved outline, a slightly deeper inside with a highlight along its upper-left
    # edge, and a bright lip below-right of the cut
    m = mask_of(SYMBOLS32[name])
    edge_m = {(x, y) for (x, y) in m if any((x + dx, y + dy) not in m for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    inner = m - edge_m
    for (x, y) in m:
        q = (x + 1, y + 1)
        if q not in m and 6 <= q[0] <= 25 and 6 <= q[1] <= 25:
            px[q[1]][q[0]] = mix3(px[q[1]][q[0]][:3], bright, 0.6) + (255,)
    for (x, y) in inner:
        base = shade(px[y][x][:3], 0.86 - 0.006 * (x + y))
        if (x - 1, y) in edge_m or (x, y - 1) in edge_m:
            base = mix3(base, bright, 0.35)
        elif (x + 1, y) in edge_m or (x, y + 1) in edge_m:
            base = shade(base, 0.88)
        px[y][x] = base + (255,)
    if name == "experience":
        for (x, y) in inner:
            if 4.4 <= math.hypot(x + 0.5 - 15.5, y + 0.5 - 15.5) <= 5.5:
                px[y][x] = mix3(ink, edge, 0.25) + (255,)
        for (x, y) in ((12, 12), (13, 12), (12, 13)):
            px[y][x] = mix3(bright, (255, 255, 255), 0.6) + (255,)
    for (x, y) in edge_m:
        k = (x + y - 14) / 30.0
        px[y][x] = shade(ink, 1.45 - 0.7 * k) + (255,)

    # shine: a four-point star top left, a soft dot bottom right, and a few twinkles
    for (xx, yy, a) in ((8, 8, 1.0), (7, 8, 0.7), (9, 8, 0.7), (8, 7, 0.7), (8, 9, 0.7), (6, 8, 0.3), (8, 6, 0.3), (10, 8, 0.3), (8, 10, 0.3)):
        if (xx, yy) not in m:
            px[yy][xx] = mix3(px[yy][xx][:3], (255, 255, 250), a) + (255,)
    for (xx, yy, a) in ((22, 22, 0.9), (23, 22, 0.6), (22, 23, 0.6), (23, 23, 0.4)):
        if (xx, yy) not in m:
            px[yy][xx] = mix3(px[yy][xx][:3], (255, 255, 245), a) + (255,)
    for i in range(3):
        xx, yy = 7 + hsh(i, n, 23) % 18, 7 + hsh(n, i, 29) % 18
        if (xx, yy) not in m and all((xx + dx, yy + dy) not in m for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
            px[yy][xx] = mix3(px[yy][xx][:3], (255, 255, 255), 0.55) + (255,)
    png(f"{ROOT}/textures/item/{name}_upgrade.png", px)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"chaosspawner:item/{name}_upgrade"}},
              open(f"{ROOT}/models/item/{name}_upgrade.json", "w"), indent=2)
