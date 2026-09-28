"""Fortune upgrades (levels 2, 5, 10) after reference/fortune.jpg: a four-petal charm with a gem in
the tier's colour.

Run from the goldenquarry folder:  python3 tools/gen_fortune.py
"""
import json
import os

from png_io import write_png

A = "src/main/resources/assets/goldenquarry"
D = "src/main/resources/data"

TIERS = {
    # gem: dark, main, light
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


def img(w=16, h=16):
    return [[(0, 0, 0, 0)] * w for _ in range(h)]


def rgba(c):
    return tuple(c) + (255,)


def save(path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    write_png(path, len(px[0]), len(px), px)


def textures():
    for lvl, (dk, mn, lt) in TIERS.items():
        charm = img()
        pal = dict(CHARM_PAL, g=dk, G=mn, H=lt)
        for y, row in enumerate(CHARM):
            for x, ch in enumerate(row):
                if ch in pal:
                    charm[y][x] = rgba(pal[ch])
        save(f"{A}/textures/item/fortune_upgrade_{lvl}.png", charm)


def models():
    for lvl in TIERS:
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
    textures()
    models()
