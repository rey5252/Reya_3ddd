"""The mod's logo for the Mods list (META-INF/mods.toml logoFile): the garden keeper peeking in beside
MANA GARDEN and the greenhouse's heart on a little greenhouse panel.

    python3 tools/gen_logo.py [--preview]

Forge draws the logo fitted into a box 50 GUI pixels high, so it is exactly 50 high: every pixel of it
lands on whole screen pixels at any GUI scale.
"""
import os
import sys

from PIL import Image

from pix import Canvas, ASSETS, ROOT, mix, shade, hexc, upscale

W, H = 200, 50
OUT = hexc("1A0703")
P0, P2 = hexc("235A5B"), hexc("173F43")
LW0, LW1, LW2, LW3 = hexc("A2512A"), hexc("7A3314"), hexc("5E240B"), hexc("4A1A08")
M0, M1, M2, M3, M4, M5 = hexc("F2FFFF"), hexc("A6F6FF"), hexc("55D9F7"), hexc("2A9FE2"), hexc("1B64B8"), hexc("0B2F5C")
L0, L1, L2, L3, L4, L5 = hexc("E4FAA8"), hexc("A8E563"), hexc("6DC043"), hexc("45922F"), hexc("2A6428"), hexc("173D1C")
PINK = [hexc("FFF0F8"), hexc("FFC4E2"), hexc("EE8FC2"), hexc("C45A97"), hexc("6E2350")]

# the plate letters (as client/PlateFont.java), 5 rows each
GLYPHS = {
    "A": [".XX.", "X..X", "XXXX", "X..X", "X..X"], "B": ["XXX.", "X..X", "XXX.", "X..X", "XXX."],
    "D": ["XXX.", "X..X", "X..X", "X..X", "XXX."], "E": ["XXXX", "X...", "XXX.", "X...", "XXXX"],
    "G": [".XXX", "X...", "X.XX", "X..X", ".XXX"], "I": ["XXX", ".X.", ".X.", ".X.", "XXX"],
    "M": ["X...X", "XX.XX", "X.X.X", "X...X", "X...X"], "N": ["X..X", "XX.X", "X.XX", "X..X", "X..X"],
    "O": [".XX.", "X..X", "X..X", "X..X", ".XX."], "R": ["XXX.", "X..X", "XXX.", "X.X.", "X..X"],
    "T": ["XXXXX", "..X..", "..X..", "..X..", "..X.."], "F": ["XXXX", "X...", "XXX.", "X...", "X..."],
    " ": ["..", "..", "..", "..", ".."],
}


def text_pixels(text, scale):
    """The set of pixels of the text at a scale, from (0, 0)."""
    pix = set()
    x = 0
    for ch in text:
        g = GLYPHS[ch]
        for j, row in enumerate(g):
            for i, c in enumerate(row):
                if c == "X":
                    for dy in range(scale):
                        for dx in range(scale):
                            pix.add((x + i * scale + dx, j * scale + dy))
        x += (len(g[0]) + 1) * scale
    return pix, x - scale


def draw_title(cv, text, x0, y0, scale, ramp, edge):
    """Big letters: a vertical colour ramp, a dark outline all round and a shadow under it."""
    pix, _ = text_pixels(text, scale)
    h = 5 * scale
    ring = set()
    for (x, y) in pix:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in pix:
                    ring.add((x + dx, y + dy))
    for (x, y) in ring:
        cv.set(x0 + x + 1, y0 + y + 2, (0, 0, 0), 90)
    for (x, y) in ring:
        cv.set(x0 + x, y0 + y, edge)
    for (x, y) in pix:
        t = y / (h - 1)
        c = ramp[min(len(ramp) - 1, int(t * len(ramp)))]
        if (x, y - 1) not in pix:
            c = shade(c, 0.35)          # a lit top edge on every stroke
        cv.set(x0 + x, y0 + y, c)


def panel(cv):
    """A rounded greenhouse panel: livingwood frame, a mana vein inside, the teal glass."""
    x1, y1, x2, y2 = 30, 6, W - 1, H - 1
    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            dx = min(x - x1, x2 - x)
            dy = min(y - y1, y2 - y)
            if dx + dy < 3:
                continue
            d = min(dx, dy)
            if d == 0 or dx + dy == 3:
                c = OUT
            elif d == 1:
                c = LW0 if (y - y1 == 1 or x - x1 == 1) else LW2
            elif d == 2:
                c = LW1 if (y - y1 == 2 or x - x1 == 2) else LW3
            elif d == 3:
                c = M2 if (y - y1 == 3 or x - x1 == 3) else M3
            else:
                c = mix(P0, P2, (y - y1) / (y2 - y1))
            cv.set(x, y, c)
    # faint rings of the rune circle behind the title
    for y in range(y1 + 4, y2 - 3):
        for x in range(x1 + 4, x2 - 3):
            r = ((x - 150) ** 2 + ((y - 28) * 1.6) ** 2) ** 0.5
            if abs(r - 38) < 0.6 or abs(r - 44) < 0.5:
                cv.set(x, y, mix(cv.get(x, y)[:3], hexc("2C6C69"), 0.8))


def sprite(cv, rows, x0, y0, pal):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in pal:
                cv.set(x0 + i, y0 + j, pal[ch])


LEAF = ["....oo", "..ollo", ".olmlo", "olmdo.", "odo...", "o....."]
BLOSSOM = [".oo.oo.", "oqqoqpo", "oqwqppo", ".opYpo.", "oppPpPo", "opPoPPo", ".oo.oo."]
SPARK = ["..w..", ".wWw.", "wWWWw", ".wWw.", "..w.."]


def keeper(cv, x0, y0):
    """The keeper's head and shoulders, beaming (from the GUI sheet: body, happy eyes and mouth)."""
    sheet = Image.open(os.path.join(ASSETS, "textures", "gui", "mascot.png")).convert("RGBA")
    body = Image.new("RGBA", (64, 112), (0, 0, 0, 0))
    body.alpha_composite(sheet.crop((0, 0, 64, 112)), (0, 0))
    body.alpha_composite(sheet.crop((128, 30, 150, 40)), (21, 28))      # happy eyes
    body.alpha_composite(sheet.crop((152, 8, 158, 12)), (29, 38))       # happy mouth
    part = body.crop((6, 0, 58, H - y0))
    cv.img.alpha_composite(part, (x0, y0))


def build():
    cv = Canvas(W, H)
    panel(cv)
    # leaves and a blossom on the frame
    pal = {"o": L5, "l": L1, "m": L2, "d": L3}
    sprite(cv, LEAF, 186, 1, pal)
    sprite(cv, [r[::-1] for r in LEAF], 58, 1, pal)
    sprite(cv, BLOSSOM, 176, 1, {"o": PINK[4], "q": PINK[1], "w": PINK[0], "p": PINK[2], "P": PINK[3], "Y": hexc("FFD84A")})
    # MANA over GARDEN, the greenhouse's heart beside MANA
    draw_title(cv, "MANA", 70, 9, 3, [M0, M1, M2, M2, M3], M5)
    draw_title(cv, "GARDEN", 70, 28, 3, [L0, L1, L2, L2, L3], L5)
    heart = Image.open(os.path.join(ASSETS, "textures", "item", "greenhouse_heart.png")).convert("RGBA")
    cv.img.alpha_composite(heart, (140, 9))
    for (x, y, big) in ((162, 12, True), (172, 30, False), (182, 18, True), (131, 7, False)):
        sprite(cv, SPARK if big else ["w"], x, y, {"w": M1, "W": M0})
    # a sprig with a blossom growing in the panel's corner
    sprite(cv, LEAF, 171, 37, pal)
    sprite(cv, BLOSSOM, 178, 33, {"o": PINK[4], "q": PINK[1], "w": PINK[0], "p": PINK[2], "P": PINK[3], "Y": hexc("FFD84A")})
    keeper(cv, 0, 0)
    return cv


def main():
    cv = build()
    cv.save(os.path.join(ROOT, "src", "main", "resources", "managarden_logo.png"))
    if "--preview" in sys.argv:
        out = os.path.join(ROOT, "build", "preview")
        os.makedirs(out, exist_ok=True)
        bg = Image.new("RGBA", (W + 8, H + 8), (40, 40, 40, 255))
        bg.alpha_composite(cv.img, (4, 4))
        upscale(bg, 5).save(os.path.join(out, "logo.png"))


if __name__ == "__main__":
    main()
