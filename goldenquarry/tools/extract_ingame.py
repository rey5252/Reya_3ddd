"""Builds the Golden Quarry block textures from the in-game reference shots (reference/ingame/*.png).

Run from the goldenquarry folder:  python3 tools/extract_ingame.py
The shots show the quarry face on: 2 the ornate front, 1 a side, 4 the top, 5 the bottom. Every
face is unwarped from its four corners in the shot and each art pixel is the median of the shot's
pixels round its middle. Minecraft darkens faces by their direction (sides 0.6, front 0.8, bottom
0.5), so the colours are brightened back; the game darkens them again.

Each face texture is 16x16 in block space (row 0 = top of the block): rows 0..5 hold the cage
sheet (inset one pixel, 14 wide), rows 6..15 the chest (10 high), so the models use the default UVs.
"""
import os

from PIL import Image

REF = "reference/ingame/%d.png"
OUT = "src/main/resources/assets/goldenquarry/textures/block"
CLEAR = (0, 0, 0, 0)

# cage colours read off shot 2 (front, so darkened 0.8 like the chest front)
CAGE = {"T": (185, 146, 78), "B": (135, 92, 53), "D": (96, 56, 37), "K": (8, 8, 8), "P": (27, 28, 28)}
CAGE_ROWS = [              # 14 wide: the ring (tan caps at the ends, tan knobs), drips, posts, feet
    "TBKKTKKKKTKKBT",
    "DD..D....D..DD",
    "P............P",
    "P............P",
    "P............P",
    "B............B",
]


def load(n):
    return Image.open(REF % n).convert("RGB").load()


def unwarp(n, quad, w, h, gain):
    px = load(n)
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = quad
    rows = []
    for j in range(h):
        v = (j + 0.5) / h
        row = []
        for i in range(w):
            t = (i + 0.5) / w
            tx, ty = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            bx, by = x2 + (x3 - x2) * t, y2 + (y3 - y2) * t
            x, y = tx + (bx - tx) * v, ty + (by - ty) * v
            cs = sorted((px[int(x) + dx, int(y) + dy] for dx in range(-6, 7, 3) for dy in range(-6, 7, 3)), key=sum)
            c = cs[len(cs) // 2]
            row.append(tuple(min(255, int(round(q / gain))) for q in c) + (255,))
        rows.append(row)
    return rows


def blank():
    return [[CLEAR] * 16 for _ in range(16)]


def save(name, img):
    im = Image.new("RGBA", (16, 16))
    im.putdata([c for row in img for c in row])
    im.save(f"{OUT}/{name}.png")


def face(chest):
    img = blank()
    for j, row in enumerate(CAGE_ROWS):
        for i, ch in enumerate(row):
            if ch != ".":
                img[j][1 + i] = tuple(min(255, int(q / 0.8)) for q in CAGE[ch]) + (255,)
    for j, row in enumerate(chest):
        img[6 + j] = row
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    # the ornate front and back (shot 2) and the sides (shot 1), chest part: 16 x 10
    front = unwarp(2, ((188, 363), (968, 363), (210, 830), (950, 830)), 16, 10, 0.8)
    side = unwarp(1, ((112, 340), (880, 340), (120, 810), (872, 810)), 16, 10, 0.6)
    save("qi_front", face(front))
    save("qi_side", face(side))
    # inside of the front and back walls (shot 13): the front's own picture, seen from behind
    inner = blank()
    for j in range(10):
        inner[6 + j] = list(reversed(front[j]))
    save("qi_inner", inner)
    # the bottom (shot 5), also the floor of the hollow the drill stands on
    bottom = unwarp(5, ((15, 38), (720, 38), (42, 708), (695, 708)), 16, 16, 0.5)
    save("qi_bottom", bottom)
    # the pillars by the side openings and the tops of the walls
    # pillars (shots 1 and 3, read through the fitted cameras; they are drawn unshaded there):
    # tan at the top unit, brown for two, dark brown for the bottom two; rows are 16 - y
    tan, brown, dark = (125, 99, 52, 255), (91, 62, 36, 255), (65, 38, 25, 255)
    pillar = [[tan] * 16 for _ in range(16)]
    for y in (6, 7):
        pillar[y] = [brown] * 16
    for y in (8, 9):
        pillar[y] = [dark] * 16
    save("qi_pillar", pillar)
    # the tops of the front and back walls (shot 10, looking down on the front wall): gold and
    # black meander, brown and tan at the corners; row 0 is the front edge (z 0), rows 13..15 the
    # back wall's, mirrored so its outer edge is row 15
    top = unwarp(10, ((58, 12), (893, 12), (43, 148), (890, 148)), 16, 3, 1.0)
    top[0][2] = (37, 38, 39, 255)                  # a grey bracket in front of the far row there
    wall = blank()
    for j, row in enumerate((top[2], top[1], top[0])):
        wall[j] = row
        wall[15 - j] = row
    for j in range(3, 13):
        wall[j] = [(22, 22, 22, 255)] * 16
    save("qi_walltop", wall)
    # the cage's top ring (shot 4): black bars with tan knobs, tan and brown caps at the corners
    ring = blank()
    T, Bn, K, G = (252, 199, 106, 255), (184, 125, 72, 255), (22, 22, 22, 255), (37, 38, 39, 255)
    for i in range(1, 15):
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            ring[y][x] = G if i % 2 else K
    for k in (5, 10):
        for (x, y) in ((k, 1), (k, 14), (1, k), (14, k)):
            ring[y][x] = T
    for cx, cy in ((1, 1), (13, 1), (1, 13), (13, 13)):
        ring[cy][cx], ring[cy][cx + 1], ring[cy + 1][cx], ring[cy + 1][cx + 1] = T, Bn, Bn, T
    save("qi_ring", ring)
    # the drill, measured on shots 1, 3 (both sides, cameras fitted to the block's corners) and 4 (top):
    # its head's texture is 10 pixels across. Top (10x10): black edge, dark grey band, black, the tan
    # ring round a black hole. Sides (10x3): black top row, then grey blocks in 3 / 2 / 3 with black
    # gaps (shot 1's face for north and south, shot 3's for east and west). Colours as the shots show
    # them in daylight (the drill is drawn unshaded).
    K0, K1, G0 = (4, 4, 4, 255), (22, 22, 22, 255), (37, 38, 39, 255)
    L, P, Q, M, m, D = ((124, 134, 138, 255), (104, 104, 114, 255), (93, 91, 104, 255), (84, 84, 93, 255),
                        (75, 74, 84, 255), (40, 48, 50, 255))
    F = (125, 125, 125, 255)
    Tl, Tb, Td = (252, 199, 106, 255), (184, 125, 72, 255), (130, 76, 50, 255)
    drill = blank()
    top = ["KKKKKKKKKK",
           "KGGGGGGGGK",
           "KGKKKKKKGK",
           "KGKbbddKGK",
           "KGKb..dKGK",
           "KGKt..bKGK",
           "KGKtttbKGK",
           "KGKKKKKKGK",
           "KGGGGGGGGK",
           "KKKKKKKKKK"]
    pal = {"K": K1, "G": G0, "t": Tl, "b": Tb, "d": Td, ".": (8, 8, 8, 255)}
    for j, row in enumerate(top):
        for i, ch in enumerate(row):
            drill[j][i] = pal[ch]
    sides = {10: ["KKKKKKKKKK",      # shot 1
                  "LMmKmMKMmM",
                  "MMDKmMKMmm"],
             13: ["KKKKKKKKKK",      # shot 3
                  "PDmKLMKDmM",
                  "DmMKMMKmmL"]}
    pal = {"K": K0, "L": L, "P": P, "M": M, "m": m, "D": D}
    for y0, rows in sides.items():
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                drill[y0 + j][i] = pal[ch]
    for j in range(6):                                 # the shaft and the head's underside: black
        drill[j][10] = K0
    for j, c in enumerate((Q, P)):                     # legs: upper part (2 high), lower part (4 high)
        drill[j][11] = c
    for j, c in enumerate((L, M, D, Q)):
        drill[j][12] = c
    for j, c in enumerate((m, m, m, M)):
        drill[j][13] = c
    drill[0][15] = F                                   # the plain grey block at the foot, the brackets
    save("qi_drill", drill)
    # glass: faintly blue, with a few white streaks; only where the cage and the side openings
    # are open (the frame's own pixels stay clear, so nothing lies on top of them)
    fill, streak = (214, 236, 255, 46), (255, 255, 255, 150)
    glass = blank()
    for j, row in enumerate(CAGE_ROWS):
        for i, ch in enumerate(row):
            if ch == ".":
                glass[j][1 + i] = fill
    for j in range(6, 10):
        for i in range(4, 12):
            glass[j][i] = fill
    for x, y in ((4, 2), (5, 3), (6, 4), (9, 2), (10, 3), (6, 7), (7, 8), (9, 7)):
        glass[y][x] = streak
    save("qi_glass", glass)
    gtop = blank()
    for j in range(2, 14):
        for i in range(2, 14):
            if not (j in (2, 13) and i in (2, 13)):
                gtop[j][i] = fill
    for x, y in ((4, 5), (5, 4), (6, 3), (5, 6), (6, 5), (7, 4), (10, 9), (11, 8), (9, 11), (10, 10)):
        gtop[y][x] = streak
    save("qi_glass_top", gtop)


if __name__ == "__main__":
    main()
