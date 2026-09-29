"""Mana Garden item textures and models: the floral tablets (blank and the four upgrades) and the
Heart of the Greenhouse.

    python3 tools/gen_items.py [--preview]

The upgrades are pearly livingrock tablets like Botania's mana tablet, with a coloured inlay; a glint
runs over them now and then (animated textures: 1 still frame shown long, then the glint frames).
"""
import json
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc, upscale

TEX = os.path.join(ASSETS, "textures", "item")
MODELS = os.path.join(ASSETS, "models", "item")

OUT = hexc("2A1F1A")
PEARL_RIM = [hexc("FFFFFF"), hexc("F2F0E8"), hexc("DCD8CB"), hexc("B9B3A2"), hexc("8C8574")]
INSET = [hexc("20363A"), hexc("172A2E"), hexc("0F1F22")]
L0, L1, L2, L3, L4, L5 = hexc("E4FAA8"), hexc("A8E563"), hexc("6DC043"), hexc("45922F"), hexc("2A6428"), hexc("173D1C")
M0, M1, M2, M3, M4, M5 = hexc("F2FFFF"), hexc("A6F6FF"), hexc("55D9F7"), hexc("2A9FE2"), hexc("1B64B8"), hexc("123C7C")
G0, G1, G2, G3, G4 = hexc("FFF8C8"), hexc("FFE27A"), hexc("F4C842"), hexc("C98B1A"), hexc("7A4E0C")
P0, P1, P2, P3, P4 = hexc("FFF0F8"), hexc("FFC4E2"), hexc("EE8FC2"), hexc("C45A97"), hexc("6E2350")

TABLET = [
    "................",
    "...oooooooooo...",
    "..oaaaaaaaaaaco.",
    "..oaiiiiiiiiibo.",
    "..oai........bo.",
    "..oai........bo.",
    "..oai........bo.",
    "..oai........bo.",
    "..oai........bo.",
    "..oai........bo.",
    "..oai........bo.",
    "..oai........bo.",
    "..oai........bo.",
    "..oabbbbbbbbbdo.",
    "...oooooooooo...",
    "................"]


def tablet(accent=None):
    """The pearly tablet (like Botania's mana tablet) with a dark inset (x 5..12, y 4..12) for the symbol;
    accent tints the rim's shadow side in the upgrade's colour."""
    cv = Canvas(16, 16)
    rim = list(PEARL_RIM)
    pal = {"o": OUT, "a": rim[0], "b": rim[2] if accent is None else mix(rim[2], accent, 0.35),
           "c": rim[1], "d": rim[3] if accent is None else mix(rim[3], accent, 0.5), "i": INSET[2]}
    cv.sprite(TABLET, 0, 0, pal)
    for y in range(4, 13):
        for x in range(5, 13):
            cv.set(x, y, INSET[1] if (x + y) % 7 else INSET[0])
    # iridescent sheen on the rim, like the mana tablet's
    for (x, y, c) in ((5, 2, hexc("E8FFF8")), (6, 2, hexc("FFF0FA")), (11, 2, hexc("E0F4FF")), (3, 8, hexc("F0FFF4")),
                      (13, 10, hexc("FFE8F4")), (8, 13, hexc("E8F0FF"))):
        cv.set(x, y, c)
    # rounded outer corners
    for (x, y) in ((3, 1), (12, 1), (3, 14), (12, 14)):
        cv.clear(x, y)
    for (x, y) in ((3, 2), (12, 2), (3, 13), (12, 13)):
        cv.set(x, y, OUT)
    return cv


def leaf_sprig(cv, x, y):
    """A tiny sprig growing from the tablet's top-right corner."""
    for (dx, dy, c) in ((0, 0, L4), (1, -1, L3), (2, -1, L2), (1, -2, L1), (2, -2, L1), (3, -2, L5), (0, -1, L5), (3, -1, L5),
                        (2, -3, L5), (1, -3, L5)):
        cv.set(x + dx, y + dy, c)


def symbol(cv, rows, pal, x=5, y=4):
    cv.sprite(rows, x, y, pal)


SPEED = ["...hh...",
         "..hLLh..",
         ".hLllLh.",
         "hLl..lLh",
         "...hh...",
         "..hLLh..",
         ".hLllLh.",
         "hLl..lLh",
         "........"]
CAPACITY = ["...w....",
            "...Cw...",
            "..CCCw..",
            "..CcCC..",
            ".CccCCC.",
            ".CcCCCC.",
            ".CCCCbC.",
            "..CCbC..",
            "...CC..."]
LUCK = [".gg..gg.",
        "gGGggGGg",
        "gGyGGyGg",
        ".gGGGGg.",
        ".gGGGGg.",
        "gGyGGyGg",
        "gGGggGGg",
        ".gg.sgg.",
        "...s...."]
YIELD = ["...pp...",
         "..pPPp..",
         ".ppPPpp.",
         "pPPYYPPp",
         "pPPYYPPp",
         ".ppPPpp.",
         "..pPPp..",
         "..lppl..",
         ".ll..ll."]


def upgrade(name, rows, pal, accent):
    frames = []
    base = tablet(accent)
    symbol(base, rows, pal)
    leaf_sprig(base, 11, 3)
    frames.append(base)
    # glint: a bright diagonal band sweeping over the tablet in 6 frames
    for k in range(6):
        f = Canvas(16, 16)
        f.img = base.img.copy()
        f.px = f.img.load()
        band = -4 + k * 4
        for y in range(16):
            for x in range(16):
                px = f.px[x, y]
                if px[3] == 0 or px[:3] == OUT:
                    continue
                d = (x + y) - (band + 8)
                if abs(d) <= 1:
                    f.set(x, y, (255, 255, 255), 150 if d == 0 else 70)
        frames.append(f)
    sheet = Canvas(16, 16 * len(frames))
    for i, f in enumerate(frames):
        sheet.paste(f, 0, 16 * i)
    sheet.save(os.path.join(TEX, name + ".png"))
    anim = {"animation": {"frametime": 2, "frames": [{"index": 0, "time": 80}, 1, 2, 3, 4, 5, 6]}}
    write_json(os.path.join(TEX, name + ".png.mcmeta"), anim)
    return frames[0]


def heart():
    """Heart of the Greenhouse: a heart-shaped mana crystal cradled by two leaves, a sprout on top."""
    cv = Canvas(16, 16)
    rows = [
        "................",
        ".......ll.......",
        "......lhl.......",
        ".......lL.......",
        "..ooo..L..ooo...",
        ".oWWco.L.oCcco..",
        "oWwCCcooocCCcbo.",
        "oWCCCCCccCCCcbo.",
        "oCCCCCCCCCCcbbo.",
        ".oCCCCCCCCcbbo..",
        "..oCCCCCCcbbo...",
        "LL.oCCCCcbbo..LL",
        "LlL.oCCcbbo..LlL",
        ".LlL.oCbbo..LlL.",
        "..LLL.obo..LLL..",
        "......ooo.......",
    ]
    pal = {"o": hexc("0B2F5C"), "W": M0, "w": hexc("FFFFFF"), "C": M2, "c": M3, "b": M4,
           "l": L1, "h": L0, "L": L3}
    cv.sprite(rows, 0, 0, pal)
    cv.save(os.path.join(TEX, "greenhouse_heart.png"))
    return cv


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def item_model(name):
    write_json(os.path.join(MODELS, name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "managarden:item/" + name}})


def main():
    os.makedirs(TEX, exist_ok=True)
    blank = tablet()
    leaf_sprig(blank, 11, 3)
    blank.save(os.path.join(TEX, "upgrade_base.png"))
    shown = [blank]
    shown.append(upgrade("speed_upgrade", SPEED, {"h": L1, "L": L2, "l": L0, "d": L4}, L2))
    shown.append(upgrade("capacity_upgrade", CAPACITY, {"w": M0, "C": M2, "c": M1, "b": M4}, M3))
    shown.append(upgrade("luck_upgrade", LUCK, {"g": G3, "G": G1, "y": G0, "s": L3}, G2))
    shown.append(upgrade("yield_upgrade", YIELD, {"p": P3, "P": P1, "Y": G1, "l": L3}, P2))
    shown.append(heart())
    for name in ("upgrade_base", "speed_upgrade", "capacity_upgrade", "luck_upgrade", "yield_upgrade", "greenhouse_heart"):
        item_model(name)
    if "--preview" in sys.argv:
        from PIL import Image
        sheet = Image.new("RGBA", (16 * len(shown) + 4 * (len(shown) + 1), 24), (139, 139, 139, 255))
        for i, cv in enumerate(shown):
            sheet.alpha_composite(cv.img.crop((0, 0, 16, 16)), (4 + i * 20, 4))
        os.makedirs(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "preview"), exist_ok=True)
        upscale(sheet, 8).save(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "preview", "items.png"))


if __name__ == "__main__":
    main()
