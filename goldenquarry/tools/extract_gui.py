"""Builds the Golden Quarry GUI textures straight from the reference screenshot (reference/gui.png).

Run from the goldenquarry folder:  python3 tools/extract_gui.py [--preview DIR]
The screenshot shows the GUI at 1.892 screen pixels per GUI pixel; every GUI pixel is read back
from its centre, so the texture is the reference pixel for pixel. What the screenshot shows on top
of the background is taken off again: the items and counts in the slots (the slots are rebuilt
from the empty ones), the filled energy and progress bars (their fills go to quarry_widgets.png and
the screen draws them), and the golden cards in the three frames on the right, which are upgrade
slots: the cards become the upgrade item textures (redrawn crisp in the original item's colours). The three missing top rows of the frame are mirrored from its bottom.

GUI pixel (gx, gy) of the screenshot lands at texture pixel (gx + 1, gy + 3).
"""
import os
import sys

import anim
from png_io import read_png, write_png

REF = "reference/gui.png"
OUT = "src/main/resources/assets/goldenquarry/textures/gui"
S, X0, Y0 = 1.8920, 204.218, -0.092     # screen pixels per GUI pixel, grid origin (fitted to the slot lines)
DX, DY = 1, 3                           # texture offset of GUI pixel (0, 0)
WIDTH, HEIGHT = 212, 247                # size of the GUI in the texture

W, H, PX = read_png(REF)


def samp(gx, gy):
    """The screenshot pixel nearest to the centre of GUI pixel (gx, gy)."""
    x = X0 + (gx + 0.5) * S
    y = Y0 + (gy + 0.5) * S
    return PX[int(round(y - 0.5))][int(round(x - 0.5))][:3]


def median(colors):
    cs = sorted(colors, key=lambda c: 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    return cs[len(cs) // 2]


def is_background(c):
    """Scene behind the GUI: dim, a little warm and not saturated (the frame's darks are neutral or red)."""
    r, g, b = c
    lum = 0.3 * r + 0.59 * g + 0.11 * b
    return 12 <= lum <= 62 and 3 <= r - b <= 18 and abs(r - g) < 12


class Tex:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def set(self, gx, gy, c):
        x, y = gx + DX, gy + DY
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = (None if c is None else tuple(c[:3]) + (255,)) or (0, 0, 0, 0)

    def get(self, gx, gy):
        return self.px[gy + DY][gx + DX]

    def save(self, path):
        write_png(path, self.w, self.h, self.px)


def in_gui(gx, gy):
    """Parts of the screenshot that belong to the GUI (the scene around it is left out)."""
    if 0 <= gx <= 209 and gy <= 153:
        return True                                      # main panel with its frame
    if gx in (-1, 210) and (gy <= 6 or 168 <= gy <= 176):
        return True                                      # corner ornaments sticking out
    if (0 <= gx <= 16 or 193 <= gx <= 209) and 154 <= gy <= 176:
        return True                                      # the frame's legs down the sides
    if 17 <= gx <= 192 and gy >= 154:
        return True                                      # player inventory panel
    return False


def main():
    tex = Tex(256, 256)
    # 1. every GUI pixel of the screenshot
    for gy in range(0, 240):
        for gx in range(-1, 211):
            if not in_gui(gx, gy):
                continue
            c = samp(gx, gy)
            if (gy >= 154 and not (17 <= gx <= 192)) or gx in (-1, 210):
                if is_background(c):
                    continue
            tex.set(gx, gy, c)
    # rounded corners of the player panel
    for (gx, gy) in [(17, 154), (18, 154), (17, 155), (190, 154), (191, 154), (192, 154), (191, 155), (192, 155), (192, 156)]:
        tex.set(gx, gy, None)

    # 2. the frame's three rows cut off at the top: mirror of the bottom of the legs (rows 174..176)
    for gx in range(-1, 211):
        leg = gx <= 16 or gx >= 193
        src_x = gx if leg else 12
        for gy, src_y in ((-1, 174), (-2, 175), (-3, 176)):
            if gy == -3 and not (gx <= 11 or gx >= 198):
                continue
            c = samp(src_x, src_y)
            if gy == -3 and is_background(c):
                continue
            tex.set(gx, gy, c)

    # 3. bottom of the player panel, cut off in the screenshot (vanilla bevel: grey, shadow, outline)
    grey, shadow, black, white = (198, 198, 198), (85, 85, 85), (0, 0, 0), (255, 255, 255)
    for gy in range(237, 241):
        for gx in range(17, 193):
            tex.set(gx, gy, samp(gx, 237))
    for gx in range(17, 193):
        tex.set(gx, 241, black if gx in (17, 192) else white if gx == 18 else grey if gx == 19 else shadow)
        tex.set(gx, 242, None if gx in (17, 192) else black if gx in (18, 191) else shadow)
        tex.set(gx, 243, black if 19 <= gx <= 190 else None)

    # 4. brown storage (top): every slot from the median of the empty ones (items and counts go)
    def tile(x0, y0):
        return [[samp(x0 + i, y0 + j) for i in range(18)] for j in range(18)]
    empty = [tile(13 + 18 * k, 11 + 18 * r) for r in range(3) for k in range(9) if not (r <= 1 and k <= 2)]
    brown = [[median([t[j][i] for t in empty]) for i in range(18)] for j in range(18)]
    for r in range(3):
        for k in range(9):
            for j in range(18):
                for i in range(18):
                    tex.set(13 + 18 * k + i, 11 + 18 * r + j, brown[j][i])

    # 5. golden storage (bottom): all slots hold iron ingots in the screenshot. Frame lines are taken
    #    as they are; the gold inside is rebuilt row by row from where the ingot doesn't cover it.
    tiles = [tile(13 + 18 * k, 91 + 18 * r) for r in range(3) for k in range(9)]
    med = [[median([t[j][i] for t in tiles]) for i in range(18)] for j in range(18)]
    def goldish(c):
        return c[0] > 200 and c[1] > 150 and c[2] < 125
    gold = [[None] * 18 for _ in range(18)]
    # frame lines: dark on top and left, light on the right and bottom; the count "64" and the
    # ingot's shadow spill over the lower right, so those lines are continued from their clean parts
    for i in range(18):
        gold[0][i] = med[0][i]
    for j in range(18):
        gold[j][0] = med[j][0]
    right = median([med[j][17] for j in range(1, 9)])
    bottom = median([med[17][i] for i in range(1, 7)])
    for j in range(1, 18):
        gold[j][17] = right
    for i in range(1, 17):
        gold[17][i] = bottom
    gold[17][17] = med[17][17] if goldish(med[17][17]) or sum(med[17][17]) > 500 else right
    # the gold inside: the top rows as they are; lower down, the colour at the edges of each row,
    # where the ingot doesn't reach
    fallback = median([med[j][i] for j in range(1, 4) for i in range(1, 17) if goldish(med[j][i])])
    for j in range(1, 17):
        edge = [med[j][i] for i in (1, 2, 3, 15, 16) if goldish(med[j][i])]
        fill = median(edge) if edge else fallback
        for i in range(1, 17):
            gold[j][i] = med[j][i] if (j <= 3 and goldish(med[j][i])) else fill
    for r in range(3):
        for k in range(9):
            for j in range(18):
                for i in range(18):
                    tex.set(13 + 18 * k + i, 91 + 18 * r + j, gold[j][i])

    # 6. bars: the empty track is the unfilled end of the progress bar; the fills go to the widgets
    track = [[samp(150, 80 + j)] for j in range(8)]
    widgets = Tex(256, 64)
    for j in range(8):
        for gx in range(45, 173):
            widgets.px[j][gx - 45] = samp(gx, 69 + j) + (255,)            # energy fill, full width
    filled = [[samp(gx, 80 + j) for gx in range(45, 95)] for j in range(8)]
    period = best_period([row for row in filled[1:6]])
    print("progress stripes repeat every", period, "pixels")
    for j in range(8):
        for x in range(160):
            widgets.px[8 + j][x] = filled[j][x % period] + (255,)          # progress stripes, repeated
    for j in range(8):
        for gx in range(45, 173):
            tex.set(gx, 69 + j, track[j][0])
            tex.set(gx, 80 + j, track[j][0])
        for gx in (44, 173):                      # end caps of the empty track
            tex.set(gx, 69 + j, samp(gx, 80 + j))

    # 7. the three golden frames on the right are upgrade slots: their cards become the upgrade
    #    items (stack, infinite engine, autosmelt from top to bottom), and the frames are left empty in the texture
    cards = [crisp_card(n) for n in range(3)]
    for n in range(3):
        hole = samp(180, 24 + 18 * n)
        for j in range(14):
            for i in range(-1, 15):                 # the whole slot hole
                tex.set(181 + i, 17 + 18 * n + j, hole)
    # the icon box left of the bars becomes the fortune charm's slot: the box is the reference's own,
    # pixel for pixel; only the picture drawn inside it is taken out, and the gold behind it is
    # the reference's gold, fitted smooth (a quadratic over its visible gold pixels)
    def is_gold(c):
        return c[0] > 215 and c[1] > 150 and c[2] < 140 and c[0] - c[2] > 110

    terms = lambda x, y: (1.0, x, y, x * x, y * y, x * y)
    pts = [(gx, gy, samp(gx, gy)) for gy in range(71, 86) for gx in range(15, 30)]
    pts = [(gx, gy, c) for gx, gy, c in pts if is_gold(c)]
    coef = []
    for q in range(3):
        m = [[sum(terms(gx, gy)[i] * terms(gx, gy)[j] for gx, gy, _ in pts) for j in range(6)] for i in range(6)]
        v = [sum(terms(gx, gy)[i] * c[q] for gx, gy, c in pts) for i in range(6)]
        for col in range(6):                                   # Gauss-Jordan
            piv = max(range(col, 6), key=lambda r: abs(m[r][col]))
            m[col], m[piv], v[col], v[piv] = m[piv], m[col], v[piv], v[col]
            for r in range(6):
                if r != col:
                    f = m[r][col] / m[col][col]
                    m[r] = [a - f * b for a, b in zip(m[r], m[col])]
                    v[r] -= f * v[col]
        coef.append([v[i] / m[i][i] for i in range(6)])
    for gy in range(66, 90):
        for gx in range(10, 35):
            if 15 <= gx <= 29 and 71 <= gy <= 85:
                t = terms(gx, gy)
                c = tuple(max(0, min(255, int(round(sum(k * x for k, x in zip(coef[q], t)))))) for q in range(3))
            else:
                c = samp(gx, gy)
            tex.set(gx, gy, c)
    # made straight: the box's top left quarter mirrored round the slot's middle (gui 22.5, 77.5)
    for gy in range(66, 90):
        for gx in range(10, 35):
            sx = gx if gx <= 22 else 45 - gx
            sy = gy if gy <= 77 else 155 - gy
            if (sx, sy) != (gx, gy):
                tex.set(gx, gy, tex.get(sx, sy))
    # a pixel taller on top, so the charm (15 high, in the slot's top rows) has a pixel of gold
    # above and below it and does not reach over the frame
    box = {(gx, gy): tex.get(gx, gy) for gx in range(10, 36) for gy in range(66, 90)}
    for gy in range(66, 78):
        for gx in range(10, 36):
            tex.set(gx, gy, box[(gx, gy + 1)])
    for name, card in zip(("stack_upgrade", "infinite_upgrade", "smelting_upgrade"), cards):
        save_item(name, card)

    os.makedirs(OUT, exist_ok=True)
    tex.save(f"{OUT}/quarry.png")
    widgets.save(f"{OUT}/quarry_widgets.png")
    if "--preview" in sys.argv:
        out = sys.argv[-1]
        big = [[(0, 0, 0, 0)] * (256 * 3) for _ in range(256 * 3)]
        for y in range(256 * 3):
            for x in range(256 * 3):
                c = tex.px[y // 3][x // 3]
                big[y][x] = c if c[3] else ((60, 60, 70, 255) if ((x // 12) + (y // 12)) % 2 else (50, 50, 58, 255))
        write_png(f"{out}/gui_texture_preview.png", 256 * 3, 256 * 3, big)


# The infinite engine card as the original item draws it (reference/ingame/24.png, 14x14, read
# cell by cell), with its seven colours. The GUI screenshot shows the cards one GUI pixel further
# left and blurred; its cards are read back into these colours.
CARD_COLORS = {"A": (186, 101, 17), "B": (202, 125, 25), "C": (231, 156, 36), "D": (252, 235, 95),
               "E": (255, 255, 255), "F": (242, 187, 57), "G": (91, 78, 60)}
ENGINE_CARD = ["AAABBCCCCBBAAA",
               "ADDDDDDDDDDDDA",
               "ADEEEFFFFFFFFA",
               "BDEEEFFFFFFFFB",
               "BDEEEFFFGFCFFB",
               "CDFFFCGGFFFFFC",
               "CDFFFGGFFFCFFC",
               "CFFFGGGGGGCFFC",
               "CDCFCCFGGCCCFC",
               "BFFCCCGGCCCCCB",
               "BFCCCGCCCCEECB",
               "ACCCCCCCCCEECA",
               "ACCCCCCCCCCCCA",
               "AAAABBBBBBAAAA"]


def card_color(c):
    """The original's colour for a screenshot pixel of a card."""
    r, g, b = c
    if 0.3 * r + 0.59 * g + 0.11 * b < 120:
        return "G"
    if b > 180:
        return "E"
    if b >= 95 and r > 240 and g > 212:
        return "D"
    return "F" if g > 175 else "C"


def crisp_card(n):
    """Card n (0 stack, 1 infinite engine, 2 autosmelt) in the original's colours: the frame and the
    two white glints are the original's; the icon and the light streak across the card are read
    off the screenshot (the engine's icon is the original's own)."""
    out = []
    for j, row in enumerate(ENGINE_CARD):
        line = []
        for i, k in enumerate(row):
            if 2 <= i <= 11 and 2 <= j <= 11 and k not in "DE" or (i, j) in ((12, 2), (12, 3), (12, 4)):
                k2 = card_color(samp(181 + i, 17 + 18 * n + j))
                if k2 in "DE" and not (n == 1 and k == "G"):
                    k = k2                          # the light streak
                elif n != 1:
                    k = k2 if k2 in "GFC" else k
            line.append(CARD_COLORS[k])
        out.append(line)
    return out


def save_item(name, card):
    """A 14x14 card in the middle of a 16x16 item texture, as it sits in the slot."""
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for j, row in enumerate(card):
        for i, c in enumerate(row):
            px[1 + j][1 + i] = tuple(c) + (255,)
    # animated: a shine passes over the card now and then (tools/anim.py)
    anim.save(f"src/main/resources/assets/goldenquarry/textures/item/{name}.png", anim.shine_only(px), 2, 60)


def best_period(rows):
    """Horizontal repeat of the progress bar's stripes."""
    best, best_err = 4, None
    for p in range(2, 13):
        err = 0
        for row in rows:
            for x in range(len(row) - p):
                err += sum(abs(row[x][q] - row[x + p][q]) for q in range(3))
        err /= (len(row) - p)
        if best_err is None or err < best_err:
            best, best_err = p, err
    return best


if __name__ == "__main__":
    main()
