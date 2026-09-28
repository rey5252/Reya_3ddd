"""Fortune upgrades (levels 2, 5, 10), redrawn pixel by pixel from reference/fortune.jpg.

Run from the goldenquarry folder:  python3 tools/gen_fortune.py [--preview DIR]
The three charms in the reference are 16x16 pixel art, drawn slightly skewed. Each one is unwarped
with its own grid (the corners below were fitted so every grid cell holds one flat art pixel), and
every cell's colour is the median of the photo pixels in its middle. The parts are then made
separately and put together: the charm itself (the four petals, the side wings and the feet), which
is the same in all three, from the median of the three; and each level's gem from its own charm.
JPEG noise is removed by merging colours that differ by only a few steps.
"""
import json
import os
import sys

from PIL import Image

A = "src/main/resources/assets/goldenquarry"
D = "src/main/resources/data"
REF = "reference/fortune.jpg"
N = 16

# grid corners (top-left, top-right, bottom-left, bottom-right of the 16x16 art) in the reference
CORNERS = {
    2: ((128.46, 86.66), (232.08, 86.11), (134.32, 189.98), (235.51, 190.12)),
    5: ((396.40, 88.00), (496.35, 88.00), (396.40, 189.26), (496.35, 189.26)),
    10: ((657.98, 88.52), (758.14, 88.88), (650.99, 189.48), (751.61, 189.60)),
}
# the gem: the same cells in all three, only its colours change with the level
GEM = {(9, 4), (8, 5), (7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (9, 8),
       (7, 9), (8, 9), (7, 10), (6, 11)}
DARK = 60        # darker cells are the black background: transparent
MERGE = 14       # colours closer than this are one colour (JPEG noise)
# the JPEG and the median dull the colours: give them back their glow (saturation, brightness)
SAT, BRIGHT = 1.35, 1.12


def vivid(c, sat=SAT, bright=BRIGHT):
    import colorsys
    h, l, s = colorsys.rgb_to_hls(*(x / 255 for x in c))
    r, g, b = colorsys.hls_to_rgb(h, min(1.0, l * bright), min(1.0, s * sat))
    return tuple(min(255, int(round(x * 255))) for x in (r, g, b))


def solve(m, b):
    n = len(b)
    m = [row[:] + [b[i]] for i, row in enumerate(m)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[p] = m[p], m[c]
        for r in range(n):
            if r != c:
                f = m[r][c] / m[c][c]
                m[r] = [x - f * y for x, y in zip(m[r], m[c])]
    return [m[i][n] / m[i][i] for i in range(n)]


def homography(src, dst):
    rows, rhs = [], []
    for (x, y), (u, v) in zip(src, dst):
        rows.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); rhs.append(u)
        rows.append([0, 0, 0, x, y, 1, -v * x, -v * y]); rhs.append(v)
    h = solve(rows, rhs) + [1.0]
    return lambda x, y: ((h[0] * x + h[1] * y + h[2]) / (h[6] * x + h[7] * y + 1),
                         (h[3] * x + h[4] * y + h[5]) / (h[6] * x + h[7] * y + 1))


def sample(px, corners):
    """16x16 cell colours: the median of the photo pixels whose centres fall in a cell's middle."""
    to_grid = homography(corners, [(0, 0), (N, 0), (0, N), (N, N)])
    xs = [c[0] for c in corners]
    ys = [c[1] for c in corners]
    cells = [[[] for _ in range(N)] for _ in range(N)]
    for y in range(int(min(ys)) - 2, int(max(ys)) + 3):
        for x in range(int(min(xs)) - 2, int(max(xs)) + 3):
            u, v = to_grid(x + 0.5, y + 0.5)
            iu, iv = int(u // 1), int(v // 1)
            if 0 <= iu < N and 0 <= iv < N and 0.2 < u - iu < 0.8 and 0.2 < v - iv < 0.8:
                cells[iv][iu].append(px[x, y])
    return [[tuple(sorted(p[q] for p in cells[v][u])[len(cells[v][u]) // 2] for q in range(3)) for u in range(N)]
            for v in range(N)]


def median3(cs):
    return tuple(sorted(c[q] for c in cs)[len(cs) // 2] for q in range(3))


def merge_colours(img):
    """Colours only a few steps apart become one (their mean), so each colour of the art is clean."""
    groups = []
    for v in range(N):
        for u in range(N):
            if img[v][u] is not None:
                groups.append([img[v][u], [(u, v)]])
    while True:
        best = None
        for i in range(len(groups)):
            for j in range(i + 1, len(groups)):
                d = sum((a - b) ** 2 for a, b in zip(groups[i][0], groups[j][0])) ** 0.5
                if d < MERGE and (best is None or d < best[0]):
                    best = (d, i, j)
        if best is None:
            break
        _, i, j = best
        gi, gj = groups[i], groups[j]
        ni, nj = len(gi[1]), len(gj[1])
        gi[0] = tuple((a * ni + b * nj) / (ni + nj) for a, b in zip(gi[0], gj[0]))
        gi[1] += gj[1]
        del groups[j]
    out = [[None] * N for _ in range(N)]
    for c, cells in groups:
        for u, v in cells:
            out[v][u] = tuple(int(round(x)) for x in c)
    return out


def textures():
    px = Image.open(REF).convert("RGB").load()
    samples = {lvl: sample(px, c) for lvl, c in CORNERS.items()}
    # the charm: the same in all three references, so take the median of the three
    body = [[None if (u, v) in GEM else median3([s[v][u] for s in samples.values()]) for u in range(N)] for v in range(N)]
    body = [[c if c is None or max(c) >= DARK else None for c in row] for row in body]
    body = merge_colours(body)
    out = {}
    for lvl, s in samples.items():
        gem = [[s[v][u] if (u, v) in GEM else None for u in range(N)] for v in range(N)]
        gem = merge_colours(gem)
        img = [[vivid(gem[v][u], 1.3, 0.97) if gem[v][u] else vivid(body[v][u]) if body[v][u] else None for u in range(N)] for v in range(N)]
        rgba = [[(c + (255,)) if c else (0, 0, 0, 0) for c in row] for row in img]
        path = f"{A}/textures/item/fortune_upgrade_{lvl}.png"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im = Image.new("RGBA", (N, N))
        im.putdata([c for row in rgba for c in row])
        im.save(path)
        out[lvl] = im
    return out


def preview(icons, out_dir):
    """The reference's three charms above the redrawn ones, both enlarged the same."""
    ref = Image.open(REF).convert("RGB")
    Z = 8
    tiles = []
    for lvl, im in icons.items():
        xs = [c[0] for c in CORNERS[lvl]]
        ys = [c[1] for c in CORNERS[lvl]]
        crop = ref.crop((int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys)))).resize((N * Z, N * Z), Image.NEAREST)
        bg = Image.new("RGBA", (N, N), (22, 22, 24, 255))
        bg.alpha_composite(im)
        tiles.append((crop, bg.resize((N * Z, N * Z), Image.NEAREST)))
    sheet = Image.new("RGB", (3 * (N * Z + 12), 2 * N * Z + 12), (60, 60, 60))
    for i, (a, b) in enumerate(tiles):
        sheet.paste(a, (i * (N * Z + 12), 0))
        sheet.paste(b.convert("RGB"), (i * (N * Z + 12), N * Z + 12))
    sheet.save(f"{out_dir}/fortune_compare.png")


def models():
    for lvl in CORNERS:
        write(f"{A}/models/item/fortune_upgrade_{lvl}.json", {"parent": "minecraft:item/generated",
              "textures": {"layer0": f"goldenquarry:item/fortune_upgrade_{lvl}"}})
    recipes = {
        2: (["GRG", "RXR", "GRG"], {"G": "minecraft:gold_ingot", "R": "minecraft:lapis_lazuli", "X": "minecraft:diamond"}),
        5: (["DLD", "LXL", "DLD"], {"D": "minecraft:diamond", "L": "minecraft:lapis_block", "X": "goldenquarry:fortune_upgrade_2"}),
        10: (["NEN", "EXE", "NEN"], {"N": "minecraft:netherite_ingot", "E": "minecraft:emerald_block", "X": "goldenquarry:fortune_upgrade_5"}),
    }
    for lvl, (pattern, key) in recipes.items():
        write(f"{D}/goldenquarry/recipes/fortune_upgrade_{lvl}.json", {"type": "minecraft:crafting_shaped", "pattern": pattern,
              "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": f"goldenquarry:fortune_upgrade_{lvl}"}})


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(json.dumps(obj, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    icons = textures()
    models()
    if "--preview" in sys.argv:
        preview(icons, sys.argv[-1])
