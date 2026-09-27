"""Block, item and model files for the Chaos Spawner (run from the mod folder)."""
import json, math, struct, zlib

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

# upgrades: cards like the reference - a dark outline (lighter in the middle of the top edge), a pale
# highlight along the top and left, a plate lighter in its upper half and darker below, white shine
# squares in the top-left and bottom-right corners, and a bright neon symbol with its own shading
CARD_PAL = {
    # outline, highlight, band under the highlight, plate top, plate bottom
    "speed":      ((26, 26, 35), (194, 255, 217), (85, 100, 105), (60, 65, 79), (40, 40, 55)),
    "looting":    ((25, 38, 62), (129, 238, 255), (58, 161, 194), (41, 143, 180), (45, 82, 134)),
    "quantity":   ((35, 25, 62), (255, 170, 249), (161, 72, 182), (139, 49, 166), (80, 41, 116)),
    "experience": ((16, 42, 28), (170, 255, 196), (68, 172, 104), (42, 140, 84), (26, 88, 56)),
}
# symbol colours: core, bright, main, low, shadow
SYM_PAL = {
    "speed":      ((225, 255, 255), (78, 249, 255), (72, 182, 191), (54, 115, 127), (40, 40, 55)),
    "looting":    ((255, 250, 214), (255, 222, 92), (232, 162, 42), (150, 92, 30), (29, 34, 52)),
    "quantity":   ((254, 255, 236), (244, 255, 93), (174, 225, 52), (110, 140, 60), (91, 58, 111)),
    "experience": ((255, 250, 196), (224, 255, 110), (140, 240, 90), (70, 205, 160), (18, 50, 34)),
}
# W core, H bright, M main, L low, D shadow on the plate
SYM = {
    "speed": ["HM...HM..",
              ".HM...HM.",
              "..HM...HM",
              "...WM...W",
              "..ML...ML",
              ".ML...ML.",
              "ML...ML..",
              "D...D...."],
    "looting": ["......HW",
                ".....HWM",
                "....HWM.",
                ".M.HWM..",
                "..MWM...",
                "..LMM...",
                ".L..M...",
                "L.......",
                ".D......"],
    "quantity": ["...HM...",
                 "...WM...",
                 "...WM...",
                 "HHHWWHHM",
                 "MMMWWMML",
                 "...WL...",
                 "...ML...",
                 "...LL...",
                 "....D..."],
    "experience": [".LMML.",
                   "LHHHML",
                   "MHWWHM",
                   "MHWWHM",
                   "LMHHML",
                   ".LMML.",
                   "..DD..",
                   ".DDDD."],
}
SYM_AT = {"speed": (4, 3), "looting": (5, 3), "quantity": (5, 3), "experience": (6, 3)}


CARDS = ["speed", "looting", "quantity", "experience"]
CARD_FRAMES = 8


def hsh(x, y, salt):
    h = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def mixp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


for n, name in enumerate(CARDS):
    O, H, H2, P1, P2 = CARD_PAL[name]
    px = grid()
    for y in range(16):
        for x in range(16):
            mid = 1.0 - abs(x - 7.5) / 7.5
            if y == 0:
                c = mixp(O, P1, 0.15 + 0.55 * mid) if 0 < x < 15 else O
            elif y == 15 or x == 0:
                c = O
            elif x == 15:
                c = mixp(O, P2, 0.35 + 0.25 * (y / 15.0))
            elif y == 1:
                c = mixp(H, P1, 0.12) if x == 1 else H
            elif x == 1:
                c = mixp(H, P1, 0.12) if y <= 8 else mixp(H, P2, min(1.0, (y - 8) / 4.0))
            elif y == 14:
                c = mixp(P2, O, 0.12)
            else:
                # plate: upper half lighter, lower half darker, a soft seam between
                c = P1 if y <= 8 else P2 if y >= 10 else mixp(P1, P2, 0.5)
                if y == 2 and x >= 5:
                    c = H2
                c = shade(c, 1.0 + ((hsh(x, y, n) % 3) - 1) * 0.015)
            px[y][x] = c + (255,)
    # white shine squares with a pale halo
    for (x, y, k) in ((2, 2, 0.6), (3, 2, 0.85), (4, 2, 0.85), (2, 3, 0.7), (2, 4, 0.7),
                      (5, 2, 0.5), (5, 3, 0.55), (5, 4, 0.55), (2, 5, 0.3), (3, 5, 0.3), (4, 5, 0.3)):
        px[y][x] = mixp(px[y][x][:3], (255, 255, 255), k) + (255,)
    for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):
        px[y][x] = (255, 255, 255, 255)
    for (x, y, k) in ((11, 11, 0.3), (12, 11, 0.6), (13, 11, 0.5), (11, 12, 0.5), (13, 12, 0.8),
                      (11, 13, 0.35), (12, 13, 0.7), (13, 13, 0.6)):
        px[y][x] = mixp(px[y][x][:3], (255, 255, 255), k) + (255,)
    px[12][12] = (255, 255, 255, 255)
    # the neon symbol, with a faint glow of its main colour on the plate round it
    Wc, Hc, Mc, Lc, Dc = SYM_PAL[name]
    ox, oy = SYM_AT[name]
    cols = {"W": Wc, "H": Hc, "M": Mc, "L": Lc}
    rows = SYM[name]
    pts = {(ox + c, oy + r) for r, row in enumerate(rows) for c, ch in enumerate(row) if ch in cols}
    for (x, y) in pts:
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in pts and 2 <= q[0] <= 14 and 2 <= q[1] <= 13:
                px[q[1]][q[0]] = mixp(px[q[1]][q[0]][:3], Mc, 0.25) + (255,)
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            x, y = ox + c, oy + r
            if not (1 <= x <= 14 and 1 <= y <= 14):
                continue
            if ch in cols:
                px[y][x] = cols[ch] + (255,)
            elif ch == "D":
                px[y][x] = mixp(px[y][x][:3], Dc, 0.7) + (255,)
    # animation: the symbol and its halo breathe, the shine squares twinkle in turn
    strip = []
    for f in range(CARD_FRAMES):
        pulse = 0.5 + 0.5 * math.sin(f / CARD_FRAMES * 2 * math.pi)
        fr = [row[:] for row in px]
        for (x, y) in pts:
            fr[y][x] = mixp(fr[y][x][:3], (255, 255, 255), 0.22 * pulse) + (255,)
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in pts and 2 <= q[0] <= 14 and 2 <= q[1] <= 13:
                    fr[q[1]][q[0]] = mixp(fr[q[1]][q[0]][:3], Mc, 0.18 * pulse) + (255,)
        tw = 0.5 + 0.5 * math.cos(f / CARD_FRAMES * 2 * math.pi)
        for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):
            fr[y][x] = mixp(H, (255, 255, 255), 0.55 + 0.45 * tw) + (255,)
        fr[12][12] = mixp(H, (255, 255, 255), 1.0 - 0.45 * tw) + (255,)
        strip += fr
    png(f"{ROOT}/textures/item/{name}_upgrade.png", strip)
    json.dump({"animation": {"frametime": 3}}, open(f"{ROOT}/textures/item/{name}_upgrade.png.mcmeta", "w"), indent=2)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"chaosspawner:item/{name}_upgrade"}},
              open(f"{ROOT}/models/item/{name}_upgrade.json", "w"), indent=2)
