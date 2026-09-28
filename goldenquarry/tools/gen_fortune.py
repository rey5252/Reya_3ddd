"""Fortune upgrades (levels 2, 5, 10) and fortune blocks, after reference/fortune.jpg.

Run from the goldenquarry folder:  python3 tools/gen_fortune.py
Items: a four-petal charm with a gem in the tier's colour. Blocks: black pedestal with copper studs,
two glowing rings and a glowing top plate in the tier's colour.
"""
import json
import os

from png_io import write_png

A = "src/main/resources/assets/goldenquarry"
D = "src/main/resources/data"

TIERS = {
    # gem / glow: dark, main, light
    2: ((34, 88, 204), (63, 150, 255), (190, 232, 255)),
    5: ((120, 24, 176), (181, 60, 240), (240, 190, 255)),
    10: ((200, 88, 16), (255, 154, 42), (255, 226, 160)),
}

CHARM = [
    ".pPPPPp..pPPPPp.",
    ".PWWLLP..PLWWLP.",
    ".PWDDLP..PLDDLP.",
    ".PLDDLP.gPLDDLP.",
    ".PLLLLPgGPLLLLP.",
    ".pPPPPpGHpPPPPp.",
    "..pPPp.GHg.pPPp.",
    "MM.m..gHHGg.m.MM",
    ".M.m..gGHGg..mM.",
    ".pPPPPpgGgpPPPPp",
    ".PWWLLPmgmPWWLLP",
    ".PLDDLPmMmPLDDLP",
    ".PLDDLPmMmPLDDLP",
    ".PLLLLPMMMPLLLLP",
    ".pPPPPpMMMpPPPPp",
    "..MMM.......MMM.",
]
CHARM_PAL = {"P": (214, 134, 110), "p": (178, 94, 80), "L": (238, 196, 138), "W": (242, 214, 238),
             "D": (78, 24, 80), "M": (110, 42, 116), "m": (138, 60, 138)}

BLACK, BLACK_HI, BLACK_LO = (28, 28, 34), (44, 44, 52), (14, 14, 18)
CU, CU_HI, CU_LO = (200, 145, 74), (232, 190, 116), (138, 90, 44)


def img(w=16, h=16):
    return [[(0, 0, 0, 0)] * w for _ in range(h)]


def rgba(c):
    return tuple(c) + (255,)


def save(path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    write_png(path, len(px[0]), len(px), px)


def dark_fill(px):
    for y in range(16):
        for x in range(16):
            px[y][x] = rgba(BLACK_HI if (x * 7 + y * 3) % 11 == 0 else BLACK)


def stud(px, x, y, w=2, h=2):
    for j in range(h):
        for i in range(w):
            px[y + j][x + i] = rgba(CU_HI if j == 0 else CU if j < h - 1 or h == 1 else CU_LO)


def textures():
    for lvl, (dk, mn, lt) in TIERS.items():
        charm = img()
        pal = dict(CHARM_PAL, g=dk, G=mn, H=lt)
        for y, row in enumerate(CHARM):
            for x, ch in enumerate(row):
                if ch in pal:
                    charm[y][x] = rgba(pal[ch])
        save(f"{A}/textures/item/fortune_upgrade_{lvl}.png", charm)
        # glowing top plate: dark rim with copper corners, light in the middle
        top = img()
        dark_fill(top)
        for y in range(2, 14):
            for x in range(2, 14):
                d = max(abs(x - 7.5), abs(y - 7.5))
                top[y][x] = rgba(lt if d < 3 else mn if d < 5 else dk)
        for x in range(2, 14):
            top[2][x] = rgba(lt)
        for (x, y) in ((0, 0), (14, 0), (0, 14), (14, 14)):
            stud(top, x, y)
        save(f"{A}/textures/block/fortune_top_{lvl}.png", top)
        ring = img()
        for y in range(16):
            for x in range(16):
                ring[y][x] = rgba(lt if y % 4 == 0 else mn if (x + y) % 5 else dk)
        save(f"{A}/textures/block/fortune_ring_{lvl}.png", ring)
    # base slab side (rows 12..15 are shown): studs at the ends, copper square in the middle
    base = img()
    dark_fill(base)
    for y in (12, 13):
        for x in (0, 1, 14, 15):
            base[y][x] = rgba(CU_HI if y == 12 else CU)
    for x in range(5, 11):
        base[12][x] = rgba(CU_HI)
        base[15][x] = rgba(CU_LO)
    for y in range(12, 16):
        base[y][5] = rgba(CU)
        base[y][10] = rgba(CU)
    save(f"{A}/textures/block/fortune_base.png", base)
    btop = img()
    dark_fill(btop)
    for i in range(3, 13):
        for (x, y) in ((i, 3), (i, 12), (3, i), (12, i)):
            btop[y][x] = rgba(CU)
    for (x, y) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        stud(btop, x, y)
    save(f"{A}/textures/block/fortune_base_top.png", btop)
    # upper slab side (rows 0..5 are shown): studs along the top, two hanging lower
    up = img()
    dark_fill(up)
    for x0 in (0, 4, 8, 12):
        stud(up, x0 + 1, 0)
    stud(up, 4, 3, 2, 3)
    stud(up, 10, 3, 2, 3)
    save(f"{A}/textures/block/fortune_upper.png", up)
    core = img()
    dark_fill(core)
    save(f"{A}/textures/block/fortune_core.png", core)


def box(frm, to, faces, glow=False):
    e = {"from": frm, "to": to, "faces": faces}
    if glow:
        e["shade"] = False
        e["forge_data"] = {"block_light": 15, "sky_light": 15}
    return e


SIDES = ["north", "south", "west", "east"]


def ring(y):
    t = y + 0.75
    f = {n: {"texture": "#ring"} for n in SIDES + ["up", "down"]}
    return [box([2, y, 2], [14, t, 3], f, True), box([2, y, 13], [14, t, 14], f, True),
            box([2, y, 3], [3, t, 13], f, True), box([13, y, 3], [14, t, 13], f, True)]


def models():
    base = {n: {"texture": "#base", "uv": [0, 12, 16, 16]} for n in SIDES}
    base["up"] = {"texture": "#base_top"}
    base["down"] = {"texture": "#base_top", "cullface": "down"}
    core = {n: {"texture": "#core", "uv": [4, 4, 12, 10]} for n in SIDES}
    upper = {n: {"texture": "#upper", "uv": [1, 0, 15, 6]} for n in SIDES}
    upper["down"] = {"texture": "#core"}
    plate = {"up": {"texture": "#top"}}
    plate.update({n: {"texture": "#upper", "uv": [1, 0, 15, 0.1]} for n in SIDES})
    template = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": "goldenquarry:block/fortune_base", "base": "goldenquarry:block/fortune_base",
                     "base_top": "goldenquarry:block/fortune_base_top", "core": "goldenquarry:block/fortune_core",
                     "upper": "goldenquarry:block/fortune_upper"},
        "elements": [box([0, 0, 0], [16, 4, 16], base), box([4, 4, 4], [12, 10, 12], core)]
        + ring(5.5) + ring(7.75)
        + [box([1, 10, 1], [15, 15.9, 15], upper), box([1, 15.9, 1], [15, 16, 15], plate, True)],
    }
    write(f"{A}/models/block/fortune_block_template.json", template)
    for lvl in TIERS:
        write(f"{A}/models/block/fortune_block_{lvl}.json", {"parent": "goldenquarry:block/fortune_block_template",
              "textures": {"top": f"goldenquarry:block/fortune_top_{lvl}", "ring": f"goldenquarry:block/fortune_ring_{lvl}"}})
        write(f"{A}/models/item/fortune_block_{lvl}.json", {"parent": f"goldenquarry:block/fortune_block_{lvl}"})
        write(f"{A}/blockstates/fortune_block_{lvl}.json", {"variants": {"": {"model": f"goldenquarry:block/fortune_block_{lvl}"}}})
        write(f"{A}/models/item/fortune_upgrade_{lvl}.json", {"parent": "minecraft:item/generated",
              "textures": {"layer0": f"goldenquarry:item/fortune_upgrade_{lvl}"}})
        write(f"{D}/goldenquarry/loot_tables/blocks/fortune_block_{lvl}.json", {"type": "minecraft:block", "pools": [{"rolls": 1,
              "entries": [{"type": "minecraft:item", "name": f"goldenquarry:fortune_block_{lvl}"}],
              "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        write(f"{D}/goldenquarry/recipes/fortune_block_{lvl}.json", {"type": "minecraft:crafting_shaped",
              "pattern": ["CUC", "GOG", "BBB"],
              "key": {"C": {"item": "minecraft:copper_ingot"}, "U": {"item": f"goldenquarry:fortune_upgrade_{lvl}"},
                      "G": {"item": "minecraft:glass"}, "O": {"item": "minecraft:obsidian"},
                      "B": {"item": "minecraft:polished_blackstone"}},
              "result": {"item": f"goldenquarry:fortune_block_{lvl}"}})
    recipes = {
        2: (["GRG", "RXR", "GRG"], {"G": "minecraft:gold_ingot", "R": "minecraft:lapis_lazuli", "X": "minecraft:diamond"}),
        5: (["DLD", "LXL", "DLD"], {"D": "minecraft:diamond", "L": "minecraft:lapis_block", "X": "goldenquarry:fortune_upgrade_2"}),
        10: (["NEN", "EXE", "NEN"], {"N": "minecraft:netherite_ingot", "E": "minecraft:emerald_block", "X": "goldenquarry:fortune_upgrade_5"}),
    }
    for lvl, (pattern, key) in recipes.items():
        write(f"{D}/goldenquarry/recipes/fortune_upgrade_{lvl}.json", {"type": "minecraft:crafting_shaped", "pattern": pattern,
              "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": f"goldenquarry:fortune_upgrade_{lvl}"}})
    tag = f"{D}/minecraft/tags/blocks/mineable/pickaxe.json"
    t = json.load(open(tag))
    for lvl in TIERS:
        if f"goldenquarry:fortune_block_{lvl}" not in t["values"]:
            t["values"].append(f"goldenquarry:fortune_block_{lvl}")
    write(tag, t)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(json.dumps(obj, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    textures()
    models()
