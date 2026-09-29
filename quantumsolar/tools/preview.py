"""An isometric picture of every block (for looking them over): python3 tools/preview.py out.png"""
import sys
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, "tools")
from build import BLOCKS, FAMILIES

T = "src/main/resources/assets/quantumsolar/textures/block"
S = 5   # screen pixels per texel along an edge


def iso(top, side):
    w = 32 * S
    im = Image.new("RGBA", (w + 4, 32 * S + 4), (0, 0, 0, 0))
    px = im.load()
    t, sd = np.asarray(top.convert("RGB")), np.asarray(side.convert("RGB"))
    cx = w // 2
    for v in range(16 * 4):
        for u in range(16 * 4):
            a, b = u / 4.0, v / 4.0
            # top: u to the right-down, v to the left-down
            x = cx + (a - b) * S
            y = (a + b) * S / 2
            c = t[int(b), int(a)]
            px[int(x) + 1, int(y) + 1] = tuple(int(q) for q in c) + (255,)
            px[int(x), int(y) + 1] = tuple(int(q) for q in c) + (255,)
            # left face (front), darker
            x2 = cx - 16 * S + a * S
            y2 = 8 * S + a * S / 2 + b * S
            c = sd[int(b), int(a)] * 0.78
            px[int(x2), int(y2)] = tuple(int(q) for q in c) + (255,)
            px[int(x2), int(y2) + 1] = tuple(int(q) for q in c) + (255,)
            # right face, darker still
            x3 = cx + a * S
            y3 = 16 * S - a * S / 2 + b * S
            c = sd[int(b), int(a)] * 0.6
            if x3 < im.width and y3 + 1 < im.height:
                px[int(x3), int(y3)] = tuple(int(q) for q in c) + (255,)
                px[int(x3), int(y3) + 1] = tuple(int(q) for q in c) + (255,)
    return im


def main(out):
    per = 8
    cell_w, cell_h = 32 * S + 20, 32 * S + 34
    rows = (len(BLOCKS) + per - 1) // per
    sheet = Image.new("RGB", (per * cell_w, rows * cell_h), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    for k, (fam, colour, *_rest) in enumerate(BLOCKS):
        kind = FAMILIES[fam][0]
        bid = f"{fam}_{'panel' if kind == 'solar' else 'generator'}_{colour}"
        cube = iso(Image.open(f"{T}/{bid}_top.png"), Image.open(f"{T}/{bid}_side.png"))
        x, y = k % per * cell_w + 10, k // per * cell_h + 4
        sheet.paste(cube, (x, y), cube)
        d.text((x, y + 32 * S + 8), f"{k + 1}. {fam} {colour}", fill=(230, 230, 120))
    sheet.save(out)


if __name__ == "__main__":
    main(sys.argv[1])
