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

# upgrades: the reference icon pixel for pixel - cyan corner brackets, a bevelled gold frame, a striped
# navy panel with little light ornaments in two corners - with each upgrade's own symbol in the panel
# the whole 16x16 template ("." = transparent); S/T are the navy panel stripes
UP_PAL = {
    "A": (14, 154, 229),
    "B": (21, 196, 255),
    "C": (90, 233, 255),
    "D": (134, 255, 250),
    "E": (9, 117, 199),
    "F": (198, 131, 58),
    "G": (177, 119, 55),
    "H": (122, 71, 40),
    "I": (109, 67, 39),
    "J": (101, 64, 38),
    "K": (77, 43, 27),
    "L": (255, 249, 207),
    "M": (255, 246, 188),
    "N": (245, 201, 100),
    "O": (243, 193, 93),
    "P": (191, 139, 64),
    "Q": (141, 99, 51),
    "R": (247, 212, 110),
    "S": (38, 46, 102),
    "T": (31, 30, 81),
    "U": (255, 255, 255),
    "V": (255, 242, 158),
    "W": (81, 44, 27),
    "X": (199, 154, 67),
    "Y": (155, 107, 53),
    "Z": (85, 45, 28),
    "a": (255, 244, 170),
    "b": (255, 245, 182),
    "c": (118, 69, 40),
}
UP_ICON = [
    "AAABBBBCCCCD....",
    "EFFFGHIIJJJJJJK.",
    "EFLMMNNOOPPPPQK.",
    "EFMHIIJJJJJJOQK.",
    ".HRISTSSTSUKOQK.",
    ".HNISTSSTSVVOQK.",
    ".INJSTSSTSKKJPK.",
    ".IPJSTSSTSTSJPW.",
    ".JPQSTSSTSTSJXW.",
    ".JPQSTSSTSTSIXW.",
    ".JQOVVSSTSTSIXW.",
    ".JQOKUSSTSTSYNW.",
    ".JQOJJJJJYYYYNZE",
    ".JQPPPPPXNNNabcE",
    ".KKKKKKWWWWWcccE",
    "....DCCCCBBBBAAA",
]
# symbols drawn over the panel, per pixel: a shade letter from the symbol's palette
UP_SYMBOLS = {
    # lightning bolt with two speed streaks behind it
    "speed": ({"a": (24, 120, 190), "b": (25, 158, 217), "c": (77, 196, 232), "d": (140, 225, 245), "w": (230, 252, 255),
               "s": (40, 90, 150)},
              ["....dw",
               "...cd.",
               "s.bcdw",
               "...bc.",
               "s.ab..",
               "..a..."]),
    # sword: steel blade with an edge, gold guard, leather grip, gold pommel
    "looting": ({"g": (243, 193, 93), "G": (190, 130, 50), "h": (141, 99, 51), "c": (190, 214, 228), "d": (255, 255, 255),
                 "e": (110, 140, 168)},
                ["g.....",
                 ".h.G..",
                 "..g...",
                 ".G.cd.",
                 "...ecd",
                 "....ec"]),
    # a plus made of a big soul gem with small ones round it
    "quantity": ({"a": (110, 40, 165), "b": (164, 79, 216), "c": (200, 134, 240), "d": (236, 195, 255), "w": (255, 240, 255)},
                 ["..bb..",
                  "..cd..",
                  "bcwdcb",
                  "abdcba",
                  "..cb..",
                  "..aa.."]),
    # experience orb: glossy, with a highlight and a darker rim
    "experience": ({"a": (46, 139, 31), "b": (79, 191, 42), "c": (143, 224, 74), "d": (223, 245, 138), "w": (255, 255, 230),
                    "y": (240, 250, 120)},
                   [".bccb.",
                    "bwdcyb",
                    "cddccb",
                    "bccyba",
                    ".abba.",
                    "......"]),
}
SYM_AT = (5, 5)
FRAMES = 8
BRACKET_RAMP = [(9, 117, 199), (14, 154, 229), (21, 196, 255), (90, 233, 255), (134, 255, 250), (200, 255, 255)]
# bracket pixels in order along each bracket (top-left one then bottom-right one)
BRACKETS = ([(0, 3), (0, 2), (0, 1)] + [(x, 0) for x in range(12)],
            [(15, 12), (15, 13), (15, 14)] + [(x, 15) for x in range(15, 3, -1)])


def mixu(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


for name, (cols, rows) in UP_SYMBOLS.items():
    frames = []
    for f in range(FRAMES):
        px = [[(UP_PAL[ch] + (255,)) if ch in UP_PAL else T for ch in row] for row in UP_ICON]
        # a bright pulse running along both brackets
        for chain in BRACKETS:
            n = len(chain)
            for i, (x, y) in enumerate(chain):
                base = i / (n - 1)
                head = (f / FRAMES) * 1.4 - 0.2
                glow = max(0.0, 1.0 - abs(base - head) * 5)
                c = BRACKET_RAMP[min(len(BRACKET_RAMP) - 2, int(base * (len(BRACKET_RAMP) - 2)))]
                px[y][x] = mixu(c, BRACKET_RAMP[-1], glow * 0.8) + (255,)
        # symbol with a breathing glow and a glint sweeping across it
        pulse = 0.5 + 0.5 * math.sin(f / FRAMES * 2 * math.pi)
        glint = f * 12 // FRAMES - 3                      # diagonal x + y offset of the glint
        pts = []
        for r, row in enumerate(rows):
            for c_, ch in enumerate(row):
                if ch in cols:
                    pts.append((SYM_AT[0] + c_, SYM_AT[1] + r, cols[ch], c_ + r))
        for (x, y, col, dg) in pts:
            # soft halo on the navy panel round the symbol
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if 4 <= q[0] <= 11 and 4 <= q[1] <= 11 and not any(q == (p[0], p[1]) for p in pts):
                    o = px[q[1]][q[0]]
                    if o[:3] in ((38, 46, 102), (31, 30, 81)):
                        px[q[1]][q[0]] = mixu(o[:3], col, 0.12 + 0.1 * pulse) + (255,)
        for (x, y, col, dg) in pts:
            c = mixu(col, (255, 255, 255), 0.12 * pulse)
            if dg == glint or dg == glint + 1:
                c = mixu(c, (255, 255, 255), 0.55 if dg == glint else 0.3)
            px[y][x] = c + (255,)
        # the white sparkles in the panel corners twinkle in turn
        for (x, y, ph) in ((10, 4, 0), (5, 11, FRAMES // 2)):
            k = 0.5 + 0.5 * math.cos((f - ph) / FRAMES * 2 * math.pi)
            px[y][x] = mixu((255, 242, 158), (255, 255, 255), k) + (255,)
        frames.append(px)
    strip = [row for fr in frames for row in fr]
    png(f"{ROOT}/textures/item/{name}_upgrade.png", strip)
    json.dump({"animation": {"frametime": 3}}, open(f"{ROOT}/textures/item/{name}_upgrade.png.mcmeta", "w"), indent=2)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"chaosspawner:item/{name}_upgrade"}},
              open(f"{ROOT}/models/item/{name}_upgrade.json", "w"), indent=2)
