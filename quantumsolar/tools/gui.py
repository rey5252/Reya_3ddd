"""Draws the panels' GUI (textures/gui/panel.png), in the blocks' own style: dark steel like their
sides, the grey corner posts the picture shows at every block's corners, sunk boxes for the
block and the energy bar. Run from the quantumsolar folder:  python3 tools/gui.py

Sheet: the panel (W x H) at 0,0; the energy bar's fill at 0,112 (white, tinted in the screen);
a lamp off and on at 0,124 and 12,124; the status icons (12x12) from 0,132: sun, moon, rain,
roof, full, atom.
"""
from PIL import Image

OUT = "src/main/resources/assets/quantumsolar/textures/gui/panel.png"
W, H = 196, 108
BLACK = (8, 8, 10)
BODY = [(34, 34, 40), (30, 30, 36), (27, 27, 32)]
STEEL_LIGHT, STEEL, STEEL_DARK = (196, 198, 206), (140, 142, 152), (84, 86, 96)
POST = ["KKKKKKKK", "KWWWWWLK", "KWLLLLDK", "KWLLLLDK", "KWLLLLDK", "KWLLLLDK", "KLDDDDDK", "KKKKKKKK"]
POST_COLOURS = {"K": BLACK, "W": (236, 238, 242), "L": (176, 178, 186), "D": (104, 106, 116)}
ICON_BOX = (10, 22, 36, 36)
BAR = (10, 64, 176, 14)
LAMPS_Y = 86


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def rect(im, x, y, w, h, c):
    for j in range(h):
        for i in range(w):
            put(im, x + i, y + j, c)


def sunk(im, x, y, w, h, inside=(14, 14, 18)):
    """A box sunk into the panel: dark top and left edge, light bottom and right."""
    rect(im, x, y, w, h, inside)
    for i in range(w):
        put(im, x + i, y, BLACK)
        put(im, x + i, y + h - 1, STEEL)
    for j in range(h):
        put(im, x, y + j, BLACK)
        put(im, x + w - 1, y + j, STEEL)
    put(im, x + w - 1, y, STEEL_DARK)
    put(im, x, y + h - 1, STEEL_DARK)


def panel(im):
    for y in range(H):
        for x in range(W):
            # the body: dark steel with a faint diagonal brushing
            put(im, x, y, BODY[(x + 2 * y) // 3 % 3] if (x * 7 + y * 3) % 11 else BODY[0])
    # the frame: black line, light steel lit from the top left, dark steel, black line
    for x in range(W):
        for y in range(H):
            d = min(x, y, W - 1 - x, H - 1 - y)
            if d == 0:
                put(im, x, y, BLACK)
            elif d in (1, 2):
                top_left = (y == d and x < W - d - 1) or (x == d and y < H - d - 1)
                put(im, x, y, STEEL_LIGHT if top_left else STEEL_DARK)
            elif d == 3:
                put(im, x, y, STEEL)
            elif d == 4:
                put(im, x, y, BLACK)
    # the posts at the corners, and halves of them along the long sides
    for px, py in ((0, 0), (W - 8, 0), (0, H - 8), (W - 8, H - 8), (W // 2 - 4, 0), (W // 2 - 4, H - 8)):
        for j, row in enumerate(POST):
            for i, ch in enumerate(row):
                put(im, px + i, py + j, POST_COLOURS[ch])
    # the title strip
    sunk(im, 12, 5, W - 24, 13, (20, 20, 25))
    # the block's box and the bar
    sunk(im, *ICON_BOX)
    sunk(im, *BAR)
    # the lamps' row
    sunk(im, 10, LAMPS_Y - 2, W - 20, 10, (18, 18, 22))


def sprites(im):
    # the bar's fill: light at the top, darker down, a dark notch every 6 pixels
    for x in range(BAR[2] - 4):
        for y in range(10):
            v = 255 - y * 12
            if x % 6 == 5:
                v = int(v * 0.7)
            put(im, x, 112 + y, (v, v, v))
    # lamps: off, on
    for i, on in ((0, False), (12, True)):
        for x in range(10):
            for y in range(6):
                edge = x in (0, 9) or y in (0, 5)
                if on:
                    c = (120, 120, 120) if edge else (255, 255, 255) if y < 3 else (215, 215, 215)
                else:
                    c = (10, 10, 12) if edge else (44, 44, 52) if y < 3 else (36, 36, 42)
                put(im, i + x, 124 + y, c)
    icons = {
        "sun": ["....Y....Y..", ".Y...YY...Y.", "....YYYY....", "...YyyyyY...", "..YyyyyyyY..", "YYYyyyyyyYYY",
                "..YyyyyyyY..", "...YyyyyY...", "....YYYY....", ".Y...YY...Y.", "....Y....Y..", "............"],
        "moon": ["....WWW.....", "...WwwW.....", "..Wwww......", ".Wwww.......", ".Wwww.......", ".Wwww.......",
                 ".Wwwww......", ".WwwwwW...W.", "..WwwwwWWW..", "...WwwwwwW..", "....WWWW....", "............"],
        "rain": ["....GGGG....", "..GGggggGG..", ".GggggggggG.", "GggggggggggG", "GGGGGGGGGGGG", "............",
                 ".B...B...B..", "B...B...B...", "...B...B...B", "..B...B...B.", ".B...B...B..", "............"],
        "roof": [".....RR.....", "....RrrR....", "...RrrrrR...", "..RrrrrrrR..", ".RrrrrrrrrR.", "RRRRRRRRRRRR",
                 ".SsssssssS..", ".Ss.ss.ssS..", ".SsssssssS..", ".Ss.ss.ssS..", ".SSSSSSSSS..", "............"],
        "full": ["....KKKK....", "..KKKKKKKK..", "..KggggggK..", "..KggggggK..", "..KggggggK..", "..KggggggK..",
                 "..KggggggK..", "..KggggggK..", "..KggggggK..", "..KggggggK..", "..KKKKKKKK..", "............"],
        "atom": ["...P....P...", "....P..P....", ".PPPPPPPPPP.", "P...P..P...P", "P...PccP...P", ".PPPPccPPPP.",
                 "P...PccP...P", "P...P..P...P", ".PPPPPPPPPP.", "....P..P....", "...P....P...", "............"],
    }
    colours = {"Y": (255, 196, 40), "y": (255, 236, 120), "W": (170, 180, 220), "w": (230, 236, 255),
               "G": (120, 128, 140), "g": (190, 196, 206), "B": (90, 160, 255), "R": (150, 60, 40),
               "r": (210, 100, 70), "S": (90, 90, 100), "s": (150, 150, 160), "K": (60, 60, 70),
               "P": (190, 120, 255), "c": (255, 255, 255)}
    for k, name in enumerate(("sun", "moon", "rain", "roof", "full", "atom")):
        for j, row in enumerate(icons[name]):
            for i, ch in enumerate(row):
                if ch != ".":
                    c = colours[ch]
                    if name == "full" and ch == "g":
                        c = (90, 220, 110)
                    put(im, k * 12 + i, 132 + j, c)


def main():
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    panel(im)
    sprites(im)
    import os
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)


if __name__ == "__main__":
    main()
