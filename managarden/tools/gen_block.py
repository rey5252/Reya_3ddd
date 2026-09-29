"""Mana Greenhouse block: its textures and models.

    python3 tools/gen_block.py [--preview]

The block: a livingrock base with a mana-crystal band, a bed of soil inside, four livingwood posts
holding panes of mana glass, a livingwood rim, and on top a bud of four petals (drawn by the block
entity renderer, so they can bloom open) over a floating mana crystal.
"""
import json
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc, rnd, rnd2, upscale

TEX = os.path.join(ASSETS, "textures", "block")
MODELS = os.path.join(ASSETS, "models")

OUT = hexc("1A0703")
LW0, LW1, LW2, LW3, LW4 = hexc("A2512A"), hexc("7A3314"), hexc("5E240B"), hexc("4A1A08"), hexc("310B04")
LR0, LR1, LR2, LR3, LR4, LR5 = hexc("FBF8EE"), hexc("E2DCCB"), hexc("C9C2B1"), hexc("B3AA97"), hexc("968D79"), hexc("6E6656")
M0, M1, M2, M3, M4, M5 = hexc("F2FFFF"), hexc("A6F6FF"), hexc("55D9F7"), hexc("2A9FE2"), hexc("1B64B8"), hexc("123C7C")
L0, L1, L2, L3, L4, L5 = hexc("E4FAA8"), hexc("A8E563"), hexc("6DC043"), hexc("45922F"), hexc("2A6428"), hexc("173D1C")
P0, P1, P2, P3, P4 = hexc("FFF0F8"), hexc("FFC4E2"), hexc("EE8FC2"), hexc("C45A97"), hexc("6E2350")
SOIL0, SOIL1, SOIL2, SOIL3 = hexc("6B4A30"), hexc("54392A"), hexc("3F2B1C"), hexc("2A1C12")
PEARL = [hexc("003848"), hexc("00738B"), hexc("00B4C4"), hexc("38FFFA"), hexc("E6FFFD")]


def tex(name, cv):
    cv.save(os.path.join(TEX, name + ".png"))


# ---------------------------------------------------------------- textures

def livingrock_bricks(cv, y1, y2, seed=0):
    """Livingrock bricks, rows 4 px high, lit from the top."""
    for y in range(y1, y2):
        row = (y - y1) // 4
        in_row = (y - y1) % 4
        off = 0 if row % 2 == 0 else 4
        for x in range(16):
            col_edge = (x + off) % 8 == 0
            if in_row == 3 or col_edge:
                c = LR4
            else:
                n = rnd2(x, y, seed)
                c = LR2 if n < 0.55 else (LR1 if n < 0.8 else LR3)
                if in_row == 0:
                    c = LR1 if n < 0.7 else LR0
            cv.set(x, y, c)


def base_side():
    """The base's side (the model shows its bottom 4 rows): livingrock with a mana-crystal band."""
    cv = Canvas(16, 16)
    livingrock_bricks(cv, 0, 16, 1)
    # rows 12..15: the base band: top lip, a groove with little mana crystals, bottom
    for x in range(16):
        cv.set(x, 12, LR0 if x % 8 else LR1)
        cv.set(x, 13, LR3)
        cv.set(x, 14, LR2)
        cv.set(x, 15, LR4)
    for cx in (3, 12):
        cv.set(cx, 13, PEARL[2])
        cv.set(cx + 1, 13, PEARL[1])
        cv.set(cx, 14, PEARL[3])
        cv.set(cx + 1, 14, PEARL[2])
    cv.set(7, 13, LR4)
    cv.set(8, 13, LR4)
    cv.set(7, 14, M3)
    cv.set(8, 14, M2)
    tex("greenhouse_base", cv)


def base_top():
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            n = rnd2(x, y, 11)
            cv.set(x, y, LR1 if n < 0.6 else (LR0 if n < 0.8 else LR2))
    cv.outline(0, 0, 16, 16, LR3)
    tex("greenhouse_base_top", cv)


def base_bottom():
    cv = Canvas(16, 16)
    livingrock_bricks(cv, 0, 16, 7)
    tex("greenhouse_base_bottom", cv)


def soil():
    """The flower bed: dark soil with moss and tiny sprouts (the flowers stand on it)."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            n = rnd2(x, y, 21)
            c = SOIL2 if n < 0.45 else (SOIL1 if n < 0.75 else (SOIL3 if n < 0.9 else SOIL0))
            cv.set(x, y, c)
    for (x, y) in ((2, 3), (3, 3), (2, 4), (12, 2), (13, 2), (13, 3), (4, 12), (5, 12), (5, 13), (11, 11), (12, 12), (11, 12),
                   (7, 7), (8, 8), (1, 9), (14, 8), (14, 9), (8, 14)):
        cv.set(x, y, L4 if rnd2(x, y, 3) < 0.5 else L3)
    for (x, y) in ((3, 2), (12, 1), (5, 11), (12, 11), (14, 7)):
        cv.set(x, y, L2)
    # a ring of moss round the middle where the crystal's light falls
    for k in range(20):
        a = k / 20 * math.tau
        x, y = int(8 + math.cos(a) * 3.2), int(8 + math.sin(a) * 3.2)
        if rnd(k, 8) < 0.5:
            cv.set(x, y, L3)
    tex("greenhouse_soil", cv)


def post():
    """A livingwood post (2x2 in the model): dark red-brown with a glimmering green vein."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            band = x % 4
            c = [LW1, LW2, LW2, LW3][band]
            if rnd2(x, y, 31) < 0.12:
                c = shade(c, -0.2)
            cv.set(x, y, c)
    for y in range(16):
        if (y // 3) % 2 == 0:
            cv.set(1, y, L2 if y % 3 else L1)
            cv.set(5, y, L2 if y % 3 else L1)
            cv.set(9, y, L2 if y % 3 else L1)
            cv.set(13, y, L2 if y % 3 else L1)
    tex("greenhouse_post", cv)


def rim():
    """The livingwood rim round the top: planks along it, a light top edge."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = [LW0, LW1, LW2, LW2, LW3, LW4][min(5, (y % 8))] if y % 8 < 6 else LW2
            if rnd2(x, y, 41) < 0.14:
                c = shade(c, -0.15)
            if (x + (y // 8) * 5) % 11 == 0:
                c = LW4
            cv.set(x, y, c)
    tex("greenhouse_rim", cv)


def glass():
    """Mana glass: a faint cyan tint, a thin bright frame and two diagonal glints."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            cv.set(x, y, (120, 220, 240), 38)
    for x in range(16):
        cv.set(x, 0, (200, 250, 255), 150)
        cv.set(x, 15, (80, 180, 220), 120)
    for y in range(16):
        cv.set(0, y, (200, 250, 255), 150)
        cv.set(15, y, (80, 180, 220), 120)
    for k in range(5):
        cv.set(3 + k, 9 - k, (235, 255, 255), 150)
        cv.set(4 + k, 9 - k, (235, 255, 255), 90)
    for k in range(3):
        cv.set(9 + k, 13 - k, (235, 255, 255), 110)
    cv.set(12, 3, (255, 255, 255), 170)
    tex("greenhouse_glass", cv)


def petal(outer):
    """A petal (the model maps its u 1..15, v 1..8.6): wide at the hinge (top rows), coming to a tip.
    Outside: a green sepal with a pink edge; inside: a pink petal with mana veins."""
    cv = Canvas(16, 16)
    L = 7.6
    for y in range(1, 9):
        d = y - 1 + 0.5                     # distance from the hinge
        t = d / L
        half = 7.0 * (1.0 - t) + 0.9 * math.sin(math.pi * min(1.0, t)) ** 1.5
        for x in range(1, 15):
            dx = abs(x + 0.5 - 8.0)
            if dx > half:
                continue
            edge = dx > half - 1.0
            if outer:
                if edge:
                    c = P3 if t > 0.25 else L4
                else:
                    c = mix(L3, L2, 1.0 - t) if dx > 1.0 else L1
                    if t > 0.7:
                        c = mix(c, P2, (t - 0.7) / 0.3)
            else:
                if edge:
                    c = P3
                else:
                    c = mix(P1, P0, t)
                    if dx < 0.8:
                        c = M2 if t < 0.8 else M1
                    elif abs(dx - 3.0 * (1 - t)) < 0.5 and t < 0.75:
                        c = mix(P1, M1, 0.5)
            cv.set(x, y, c)
    name = "greenhouse_petal_outer" if outer else "greenhouse_petal_inner"
    tex(name, cv)
    # the same petal lying on its side (hinge on the left), for the item model's east and west petals
    side = Canvas(16, 16)
    side.img = cv.img.rotate(90)
    side.px = side.img.load()
    tex(name + "_side", side)


def core():
    """The floating mana crystal: faceted cyan with a white heart."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x + 0.5 - 8), abs(y + 0.5 - 8)
            d = dx + dy
            if d < 3:
                c = M0
            elif d < 6:
                c = M1
            elif d < 9:
                c = M2
            elif d < 12:
                c = M3
            else:
                c = M4
            if (x + y) % 5 == 0 and d > 4:
                c = shade(c, 0.2)
            cv.set(x, y, c)
    tex("greenhouse_core", cv)


# ---------------------------------------------------------------- models

def element(frm, to, faces, rotation=None, shade_=True):
    e = {"from": frm, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    if not shade_:
        e["shade"] = False
    return e


def face(tex_, uv, cull=None):
    f = {"texture": tex_, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f


def block_elements():
    els = []
    # base 0..4 high: the side texture's bottom rows
    els.append(element([0, 0, 0], [16, 4, 16], {
        "north": face("#base", [0, 12, 16, 16], "north"), "south": face("#base", [0, 12, 16, 16], "south"),
        "west": face("#base", [0, 12, 16, 16], "west"), "east": face("#base", [0, 12, 16, 16], "east"),
        "down": face("#bottom", [0, 0, 16, 16], "down"), "up": face("#top", [0, 0, 16, 16])}))
    # the bed of soil on it
    els.append(element([1, 4, 1], [15, 4.5, 15], {"up": face("#soil", [1, 1, 15, 15])}))
    # posts at the corners
    for (x, z) in ((0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5)):
        els.append(element([x, 4, z], [x + 2, 13, z + 2], {
            d: face("#post", [0, 3, 2, 12]) for d in ("north", "south", "west", "east")}))
    # glass panes between the posts (outer and inner face), slightly in from the edge
    for (frm, to, faces) in (
            ([2.5, 4, 1], [13.5, 13, 1], ("north", "south")),
            ([2.5, 4, 15], [13.5, 13, 15], ("south", "north")),
            ([1, 4, 2.5], [1, 13, 13.5], ("west", "east")),
            ([15, 4, 2.5], [15, 13, 13.5], ("east", "west"))):
        els.append(element(frm, to, {f: face("#glass", [2.5, 3, 13.5, 12]) for f in faces}, shade_=True))
    # the rim round the top
    for (frm, to) in (([0.5, 13, 0.5], [15.5, 14, 2.5]), ([0.5, 13, 13.5], [15.5, 14, 15.5]),
                      ([0.5, 13, 2.5], [2.5, 14, 13.5]), ([13.5, 13, 2.5], [15.5, 14, 13.5])):
        uv_long = [0, 0, 15, 1]
        els.append(element(frm, to, {
            "north": face("#rim", [0, 0, 15, 1]), "south": face("#rim", [0, 0, 15, 1]),
            "west": face("#rim", [0, 0, 15, 1]), "east": face("#rim", [0, 0, 15, 1]),
            "up": face("#rim", [0, 1, 15, 3]), "down": face("#rim", [0, 5, 15, 7])}))
    return els


PETAL_LEN = 7.0 / math.cos(math.radians(22.5))
TIP = round(1 + PETAL_LEN, 3)


def petal_element(rotation=None):
    """The north petal lying flat: hinge along z = 1, tip towards the middle. An up face maps the
    texture's top to the north, a down face to the south, so the down face's uv runs the other way."""
    return element([1, 14, 1], [15, 14, TIP], {
        "up": face("#petal_outer", [1, 1, 15, TIP]),
        "down": face("#petal_inner", [1, TIP, 15, 1])}, rotation)


def models():
    textures = {
        "particle": "managarden:block/greenhouse_base_top",
        "base": "managarden:block/greenhouse_base", "top": "managarden:block/greenhouse_base_top",
        "bottom": "managarden:block/greenhouse_base_bottom", "soil": "managarden:block/greenhouse_soil",
        "post": "managarden:block/greenhouse_post", "glass": "managarden:block/greenhouse_glass",
        "rim": "managarden:block/greenhouse_rim"}
    block = {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
             "textures": textures, "elements": block_elements()}
    write_json(os.path.join(MODELS, "block", "mana_greenhouse.json"), block)

    petal_model = {"parent": "minecraft:block/block", "ambientocclusion": False,
                   "textures": {"particle": "managarden:block/greenhouse_petal_outer",
                                "petal_outer": "managarden:block/greenhouse_petal_outer",
                                "petal_inner": "managarden:block/greenhouse_petal_inner"},
                   "elements": [petal_element()]}
    write_json(os.path.join(MODELS, "block", "greenhouse_petal.json"), petal_model)

    core_model = {"parent": "minecraft:block/block", "ambientocclusion": False,
                  "textures": {"particle": "managarden:block/greenhouse_core", "core": "managarden:block/greenhouse_core"},
                  "elements": [element([6, 6, 6], [10, 10, 10], {
                      d: face("#core", [4, 4, 12, 12]) for d in ("north", "south", "west", "east", "up", "down")}, shade_=False)]}
    write_json(os.path.join(MODELS, "block", "greenhouse_core.json"), core_model)

    # the item: the block with its bud closed (petals leaning in 22.5 degrees) and the crystal inside
    item_els = block_elements()
    # north: hinge z = 1, leaning in = its far edge up = a negative turn round x
    item_els.append(petal_element({"origin": [8, 14, 1], "axis": "x", "angle": -22.5}))
    # south: hinge z = 15; an up face shows the texture's top at the north (the tip), so flip it
    item_els.append(element([1, 14, round(15 - PETAL_LEN, 3)], [15, 14, 15], {
        "up": face("#petal_outer", [1, TIP, 15, 1]),
        "down": face("#petal_inner", [1, 1, 15, TIP])},
        {"origin": [8, 14, 15], "axis": "x", "angle": 22.5}))
    # west: hinge x = 1, the side texture has its hinge on the left (u grows with x on both faces)
    item_els.append(element([1, 14, 1], [TIP, 14, 15], {
        "up": face("#petal_outer_side", [1, 1, TIP, 15]),
        "down": face("#petal_inner_side", [1, 1, TIP, 15])},
        {"origin": [1, 14, 8], "axis": "z", "angle": 22.5}))
    # east: hinge x = 15, so the side texture runs the other way
    item_els.append(element([round(15 - PETAL_LEN, 3), 14, 1], [15, 14, 15], {
        "up": face("#petal_outer_side", [TIP, 1, 1, 15]),
        "down": face("#petal_inner_side", [TIP, 1, 1, 15])},
        {"origin": [15, 14, 8], "axis": "z", "angle": -22.5}))
    # the crystal inside, as it floats in the closed bud
    item_els.append(element([6.5, 7.5, 6.5], [9.5, 10.5, 9.5], {
        d: face("#core", [5, 5, 11, 11]) for d in ("north", "south", "west", "east", "up", "down")},
        {"origin": [8, 9, 8], "axis": "y", "angle": 45}, shade_=False))
    item_textures = dict(textures)
    item_textures.update({"petal_outer": "managarden:block/greenhouse_petal_outer",
                          "petal_inner": "managarden:block/greenhouse_petal_inner",
                          "petal_outer_side": "managarden:block/greenhouse_petal_outer_side",
                          "petal_inner_side": "managarden:block/greenhouse_petal_inner_side",
                          "core": "managarden:block/greenhouse_core"})
    item = {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
            "textures": item_textures, "elements": item_els}
    write_json(os.path.join(MODELS, "item", "mana_greenhouse.json"), item)

    state = {"variants": {"active=false": {"model": "managarden:block/mana_greenhouse"},
                          "active=true": {"model": "managarden:block/mana_greenhouse"}}}
    write_json(os.path.join(ASSETS, "blockstates", "mana_greenhouse.json"), state)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    base_side()
    base_top()
    base_bottom()
    soil()
    post()
    rim()
    glass()
    petal(True)
    petal(False)
    core()
    models()


if __name__ == "__main__":
    main()
