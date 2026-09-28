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
    # inside of the front and back walls: the front's gold and black, a little darker
    inner = blank()
    for j in range(10):
        inner[6 + j] = [tuple(int(q * 0.8) for q in c[:3]) + (255,) for c in front[j]]
    save("qi_inner", inner)
    # the bottom (shot 5), also the floor of the hollow the drill stands on
    bottom = unwarp(5, ((15, 38), (720, 38), (42, 708), (695, 708)), 16, 16, 0.5)
    save("qi_bottom", bottom)
    # the pillars by the side openings and the tops of the walls
    pillar = [[(135, 92, 53, 255) if (x + y) % 5 else (150, 104, 60, 255) for x in range(16)] for y in range(16)]
    for x in range(16):
        pillar[0][x] = (252, 199, 106, 255)
    save("qi_pillar", pillar)
    save("qi_walltop", [[(22, 22, 22, 255) if (x + y) % 3 else (37, 38, 39, 255) for x in range(16)] for y in range(16)])
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
    # the drill (shots 1 and 4): black head with a grey band and a tan ring on top, grey sides,
    # grey legs and a black shaft
    L, g, k, W = (124, 134, 138, 255), (84, 84, 93, 255), (34, 42, 43, 255), (230, 232, 232, 255)
    Tl, Td = (252, 199, 106, 255), (130, 76, 50, 255)
    drill = blank()
    top = ["KKKKKKKK",
           "KGGGGGGK",
           "KGbbbdGK",
           "KGt..dGK",
           "KGt..dGK",
           "KGtttbGK",
           "KGGGGGGK",
           "KKKKKKKK"]
    pal = {"K": K, "G": G, "t": Tl, "b": Bn, "d": Td, ".": (10, 10, 10, 255)}
    for j, row in enumerate(top):
        for i, ch in enumerate(row):
            drill[j][i] = pal[ch]
    sides = ["KKKKKKKK",
             "LgKkLLKg",
             "gLKkgLKL"]
    pal = {"K": K, "L": L, "g": g, "k": k}
    for j, row in enumerate(sides):
        for i, ch in enumerate(row):
            drill[8 + j][i] = pal[ch]
    for j in range(16):                              # legs (column 8..9) and shaft (10..11)
        drill[j][8], drill[j][9] = (L, g) if j % 3 else (W, L)
        drill[j][10], drill[j][11] = K, (16, 16, 16, 255)
    save("qi_drill", drill)


if __name__ == "__main__":
    main()
