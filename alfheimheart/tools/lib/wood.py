"""The mod's GUI style, "living wood", shared by every machine's panel and widget sheet: planks of Botania's
livingwood, a frame with a strip of pale livingrock set in it (the row mana runs along, VEIN), mana crystals
on the panel's corners, slots rimmed in livingrock (inputs), gold (outputs) or mana blue, livingrock inlay
rings and carved runes behind a machine's heart, a livingrock plaque for the title with a crystal at each end,
round livingrock buttons.

Each machine's tools/<machine>/gen_gui.py builds its panel from these; tools/<machine>/gen_widgets.py its
sheet. Coordinates are a machine's GUI pixels (a Canvas with the texture's margin as its origin).
"""
import math

from pix import Canvas, mix, shade, hexc, rnd2, rrect

# livingwood, from its sunlit edge to the seams between planks
WOOD = [hexc(h) for h in ("C4703E", "A2512A", "8A4020", "7A3314", "5E240B", "3E1606", "241004")]
# livingrock, pale and warm
LR = [hexc(h) for h in ("FBF8EE", "E2DCCB", "C9C2B1", "A89F8B", "7A715F")]
# mana (Botania's blue) and gold
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]
GOLD = [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308")]
RED = [hexc(h) for h in ("FF9A88", "F2553F", "C82C26", "8C1518", "4C0A12")]

FRAME = 5            # the frame's rows: outline, two of wood, the livingrock strip, a shadow
VEIN = 3             # the livingrock strip: the screens run mana along it
CORNER_R = 3
LIGHT_DIR = (-0.62, -0.78)

SLOT_IN = (LR[0], LR[1], LR[3])
SLOT_OUT = (GOLD[1], GOLD[2], GOLD[3])
SLOT_MANA = (MANA[1], MANA[3], MANA[5])
SLOT_INV = (LR[1], LR[2], LR[3])
SLOT_INSIDE = (hexc("2A140A"), hexc("170A05"))


def noise(x, y, scale, salt):
    """Smooth value noise in [0, 1)."""
    fx, fy = x / scale, y / scale
    x0, y0 = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b = rnd2(x0, y0, salt), rnd2(x0 + 1, y0, salt)
    c, d = rnd2(x0, y0 + 1, salt), rnd2(x0 + 1, y0 + 1, salt)
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def planks(x, y, inner=True):
    """Livingwood planks: rows 7 high with dark seams, their ends staggered, a grain along each."""
    yy = y + (0 if inner else 3)
    row = yy // 7
    if yy % 7 == 0:
        return WOOD[5]
    off = (row * 37) % 23
    if (x + off) % 29 == 0:
        return WOOD[5]
    c = WOOD[2] if row % 2 else WOOD[3]
    if inner:
        c = mix(c, WOOD[1], 0.22)
    g = noise(x * 0.3, yy * 3, 3, row)
    if g < 0.22:
        c = mix(c, WOOD[4], 0.5)
    elif g > 0.85:
        c = mix(c, WOOD[1], 0.35)
    if (x + off) % 29 == 1 or yy % 7 == 1:
        c = mix(c, WOOD[1], 0.3)            # the lit edge after a seam
    return c


def frame_color(depth, lit):
    hi, lo = lit > 0.3, lit < -0.3
    if depth == 0:
        return WOOD[6]
    if depth in (1, 2):
        return WOOD[1] if hi else (WOOD[4] if lo else WOOD[3])
    if depth == VEIN:
        return LR[1] if hi else (LR[3] if lo else LR[2])
    return WOOD[5]


def panel(cv, x1, y1, x2, y2, inner=True, r=CORNER_R):
    """A livingwood panel with its frame."""
    for y in range(y1, y2):
        for x in range(x1, x2):
            depth, nx, ny = rrect(x, y, x1, y1, x2, y2, r)
            if depth <= 0:
                continue
            d = int(depth)
            lit = nx * LIGHT_DIR[0] + ny * LIGHT_DIR[1]
            if d < FRAME:
                cv.set(x, y, frame_color(d, lit))
            else:
                # a soft shadow under the frame
                c = planks(x, y, inner)
                k = (0.32, 0.18, 0.08)[d - FRAME] if d - FRAME < 3 else 0.0
                cv.set(x, y, mix(c, WOOD[6], k) if k else c)


CRYSTAL = ["....o....",
           "...oCo...",
           "..oCcco..",
           ".oCccddo.",
           "oCcccddDo",
           "oCccddDDo",
           ".oCcdDDo.",
           "..occDo..",
           "...odo...",
           "....o...."]
CRYSTAL_PAL = {"o": MANA[5], "C": MANA[0], "c": MANA[1], "d": MANA[2], "D": MANA[3]}


def crystal(cv, cx, cy):
    """A mana crystal (9x10) round the pixel (cx, cy)."""
    cv.sprite(CRYSTAL, cx - 4, cy - 5, CRYSTAL_PAL)


def corner_crystals(x1, y1, x2, y2):
    """The middles of the crystals on a panel's corners."""
    return [(x1 + 1, y1 + 1), (x2 - 2, y1 + 1), (x1 + 1, y2 - 2), (x2 - 2, y2 - 2)]


def slot(cv, x, y, rim, inside=SLOT_INSIDE):
    """An 18x18 slot (x, y = the item's corner): a bevelled rim, a dark wooden inside, rounded corners."""
    hi, mid, lo = rim
    top, bottom = inside
    x1, y1 = x - 1, y - 1
    cv.rect(x1, y1, x1 + 18, y1 + 18, mid)
    cv.hline(x1, x1 + 18, y1, hi)
    cv.vline(x1, y1, y1 + 18, hi)
    cv.hline(x1, x1 + 18, y1 + 17, lo)
    cv.vline(x1 + 17, y1, y1 + 18, lo)
    cv.set(x1, y1 + 17, mid)
    cv.set(x1 + 17, y1, mid)
    for yy in range(y, y + 16):
        cv.hline(x, x + 16, yy, mix(top, bottom, (yy - y) / 15.0))
    cv.hline(x, x + 16, y, shade(top, -0.45))
    cv.vline(x, y, y + 16, shade(top, -0.45))
    cv.hline(x + 1, x + 16, y + 15, mix(bottom, hi, 0.1))
    cv.vline(x + 15, y + 1, y + 16, mix(bottom, hi, 0.1))
    for (cx, cy) in ((x1, y1), (x1 + 17, y1), (x1, y1 + 17), (x1 + 17, y1 + 17)):
        cv.set(cx, cy, WOOD[5])


def inlay_ring(cv, cx, cy, r, width=1.0, col=None):
    """A thin ring of livingrock set into the wood."""
    col = col or mix(LR[2], WOOD[2], 0.35)
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if abs(d - r) <= width / 2.0:
                cv.set(x, y, col)
            elif abs(d - r) <= width / 2.0 + 0.8:
                cv.set(x, y, mix(cv.get(x, y)[:3], WOOD[5], 0.5))


CARVED = [
    [".#.", "###", ".#."], ["#.#", ".#.", "#.#"], ["##.", "#.#", ".##"], [".##", "#..", ".##"],
    ["#.#", "###", "#.#"], ["###", ".#.", "#.#"], [".#.", "#.#", "###"], ["#..", "###", "..#"]]


def carved_runes(cv, cx, cy, r, n, skip=lambda x, y: False):
    """n little runes carved into the wood round (cx, cy) at distance r (dark, with a lit lower edge)."""
    for k in range(n):
        a = (k + 0.5) / n * math.tau
        x, y = int(round(cx + math.cos(a) * r)), int(round(cy + math.sin(a) * r))
        if skip(x, y):
            continue
        for j, row in enumerate(CARVED[k % len(CARVED)]):
            for i, ch in enumerate(row):
                if ch == "#":
                    cv.set(x - 1 + i, y - 1 + j, WOOD[5])
                    if j + 1 >= len(row) or row[i] == "#" and (j + 1 == 3 or CARVED[k % len(CARVED)][j + 1][i] != "#"):
                        cv.set(x - 1 + i, y + j, mix(WOOD[1], LR[2], 0.3))


def bar_track(cv, x1, y1, x2, y2, inside=hexc("0A1620")):
    """A sunk track for a bar (x1..x2, y1..y2 its inside): livingrock rim, dark inside."""
    cv.outline(x1 - 2, y1 - 2, x2 + 2, y2 + 2, WOOD[6])
    cv.hline(x1 - 1, x2 + 1, y1 - 1, LR[3])
    cv.vline(x1 - 1, y1 - 1, y2 + 1, LR[3])
    cv.hline(x1 - 1, x2 + 1, y2, LR[1])
    cv.vline(x2, y1 - 1, y2 + 1, LR[1])
    cv.rect(x1, y1, x2, y2, inside)


# ---------------------------------------------------------------- widget sheet pieces

def button(cv, x, y, state):
    """A 16x16 button: a livingrock rim round a wooden face; hover lighter, pressed sunk, off greyed."""
    rim = {"normal": (LR[0], LR[1], LR[3]), "hover": (hexc("FFFFFF"), LR[0], LR[2]),
           "pressed": (LR[3], LR[2], LR[1]), "off": (hexc("9A948A"), hexc("7E786F"), hexc("5E5850"))}[state]
    face = {"normal": (WOOD[2], WOOD[4]), "hover": (WOOD[1], WOOD[3]), "pressed": (WOOD[4], WOOD[5]),
            "off": (hexc("4A3A30"), hexc("3A2E26"))}[state]
    hi, mid, lo = rim
    cv.rect(x, y, x + 16, y + 16, mid)
    cv.hline(x, x + 16, y, hi)
    cv.vline(x, y, y + 16, hi)
    cv.hline(x, x + 16, y + 15, lo)
    cv.vline(x + 15, y, y + 16, lo)
    for yy in range(y + 2, y + 14):
        cv.hline(x + 2, x + 14, yy, mix(face[0], face[1], (yy - y - 2) / 11.0))
    cv.hline(x + 2, x + 14, y + 2, shade(face[0], -0.35))
    cv.vline(x + 2, y + 2, y + 14, shade(face[0], -0.35))
    for (cx, cy) in ((x, y), (x + 15, y), (x, y + 15), (x + 15, y + 15)):
        cv.clear(cx, cy)
    for (cx, cy) in ((x + 1, y), (x, y + 1), (x + 14, y), (x + 15, y + 1), (x, y + 14), (x + 1, y + 15), (x + 14, y + 15), (x + 15, y + 14)):
        cv.set(cx, cy, WOOD[6])


CROSS = ["XX....XX",
         "XXX..XXX",
         ".XXXXXX.",
         "..XXXX..",
         "..XXXX..",
         ".XXXXXX.",
         "XXX..XXX",
         "XX....XX"]


def close_button(cv, x0, y0, state):
    """A round livingrock button (16x16) with a red X; hover rims it with mana light, pressed sinks it."""
    cx, cy, r = x0 + 8, y0 + 8, 7.5
    face = {"normal": (LR[0], LR[1], LR[3]), "hover": (hexc("FFFFFF"), LR[0], LR[2]), "pressed": (LR[2], LR[3], LR[4])}[state]
    for y in range(y0, y0 + 16):
        for x in range(x0, x0 + 16):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d > r:
                continue
            if d > r - 1:
                cv.set(x, y, MANA[2] if state == "hover" else WOOD[6])
                continue
            k = (px * LIGHT_DIR[0] + py * LIGHT_DIR[1]) / (d + 1e-6)
            if d > r - 2:
                cv.set(x, y, face[0] if k > 0.2 else (face[2] if k < -0.3 else face[1]))
                continue
            cv.set(x, y, mix(face[1], face[2], max(0.0, min(1.0, (py + 5) / 12.0)) * 0.6))
    off = 1 if state == "pressed" else 0
    for shadow in (True, False):
        col = RED[4] if shadow else (RED[1] if state != "hover" else RED[0])
        d = off + (1 if shadow else 0)
        for j, row in enumerate(CROSS):
            for i, ch in enumerate(row):
                if ch == "X":
                    cv.set(x0 + 4 + i + d - 1, y0 + 4 + j + d - 1, col)


def glow(cv, x0, y0, size=9, strength=125):
    """A soft round halo, white fading to nothing, with a hole in its middle: the screens tint it."""
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if d < 1.05:
                continue
            a = max(0.0, 1.0 - (d - 1.0) / (c - 0.5)) ** 2.0
            if a > 0.02:
                cv.set(x0 + x, y0 + y, (255, 255, 255), int(a * strength))


PLAQUE_TOP, PLAQUE_BOTTOM = 3, 16    # the plaque's rows on its 20-high pieces


def plaque_row(j):
    """The plaque's colour on its row j (PLAQUE_TOP..PLAQUE_BOTTOM)."""
    top, bottom = PLAQUE_TOP, PLAQUE_BOTTOM
    if j in (top, bottom):
        return WOOD[6]
    if j == top + 1:
        return LR[0]
    if j == bottom - 1:
        return LR[3]
    if j == bottom - 2:
        return LR[2]
    return LR[1]


def plaque_tile(cv, x0, y0, salt):
    """8 columns of the plaque (tiled by the screens, two variants so it doesn't repeat)."""
    for i in range(8):
        for j in range(PLAQUE_TOP, PLAQUE_BOTTOM + 1):
            c = plaque_row(j)
            if PLAQUE_TOP + 1 < j < PLAQUE_BOTTOM - 2 and rnd2(i, j, 41 + salt) < 0.1:
                c = mix(c, LR[3], 0.35)
            cv.set(x0 + i, y0 + j, c)


def plaque_cap(cv, x0, y0, right):
    """The plaque's end (16x20): its rounded end and a mana crystal set over it."""
    for k in range(16):
        x = x0 + (k if right else 15 - k)          # k = 0 next to the tiles
        for j in range(PLAQUE_TOP, PLAQUE_BOTTOM + 1):
            inset = max(0, k - 9)
            if j < PLAQUE_TOP + inset or j > PLAQUE_BOTTOM - inset:
                continue
            if k > 13:
                continue
            c = plaque_row(j) if (j != PLAQUE_TOP + inset and j != PLAQUE_BOTTOM - inset and k < 13) else WOOD[6]
            cv.set(x, y0 + j, c)
    cx = x0 + (9 if right else 6)
    cv.sprite(CRYSTAL, cx - 4, y0 + 5, CRYSTAL_PAL)
