"""Generates the DOM gun's 3D item model, its colour-swatch texture and the ammo icon.

Run from the domgun/ folder:  python3 tools/gen_assets.py
The rifle is built from boxes, barrel towards +x, grip near the model centre (8,8,8) so the hand holds it there.
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "domgun")

# 4x4 grid of 4px cells; a face samples the middle of one cell, so each cell is one flat colour
SWATCHES = {
    "black": (0, 0, (24, 25, 28)),
    "metal": (1, 0, (58, 61, 68)),
    "steel": (2, 0, (104, 109, 120)),
    "poly": (3, 0, (82, 74, 62)),
    "accent": (0, 1, (232, 118, 24)),
    "glass": (1, 1, (60, 200, 230)),
}

# (name, from, to, swatch, optional (axis, angle, origin))
BOXES = [
    ("stock", [-7, 6, 7], [2, 10.5, 9], "poly", None),
    ("butt", [-8, 5.5, 6.8], [-7, 11, 9.2], "black", None),
    ("receiver", [2, 7, 6.6], [13, 11, 9.4], "metal", None),
    ("rail", [3, 11, 7.2], [14, 11.8, 8.8], "black", None),
    ("stripe", [2, 10.4, 6.5], [6, 10.9, 9.5], "accent", None),
    ("optic", [6, 11.8, 7.3], [10.5, 13.8, 8.7], "black", None),
    ("lens", [10.5, 12.1, 7.6], [10.7, 13.5, 8.4], "glass", None),
    ("handguard", [13, 7.2, 6.8], [21, 10.8, 9.2], "poly", None),
    ("barrel", [21, 8.5, 7.4], [28, 9.8, 8.6], "steel", None),
    ("brake", [28, 8.2, 7.1], [30.5, 10.1, 8.9], "black", None),
    ("magazine", [8, 0.5, 7.2], [11, 7.2, 8.8], "steel", ("z", 22.5, [9.5, 7.2, 8])),
    ("grip", [4.6, 2, 7.2], [7, 7.2, 8.8], "black", ("z", -22.5, [5.8, 7.2, 8])),
    ("trigger", [7.6, 6.2, 7.8], [8.1, 7.2, 8.2], "steel", None),
    ("foregrip", [15, 4.4, 7.4], [17.5, 7.2, 8.6], "black", None),
]


def uv(swatch):
    cx, cy, _ = SWATCHES[swatch]
    return [cx * 4 + 1, cy * 4 + 1, cx * 4 + 3, cy * 4 + 3]


def element(name, frm, to, swatch, rot):
    face = {"uv": uv(swatch), "texture": "#0"}
    el = {"name": name, "from": frm, "to": to,
          "faces": {side: dict(face) for side in ("north", "east", "south", "west", "up", "down")}}
    if rot:
        axis, angle, origin = rot
        el["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
    return el


def build_model():
    # Barrel points to +x. Display rotation y=90 turns +x into the hand's forward direction.
    return {
        "textures": {"0": "domgun:item/dom_gun", "particle": "domgun:item/dom_gun"},
        "elements": [element(*b) for b in BOXES],
        "display": {
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1.5, 1], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 1.5, 1], "scale": [0.7, 0.7, 0.7]},
            "firstperson_righthand": {"rotation": [0, 90, 0], "translation": [1.5, 0.5, 2], "scale": [0.75, 0.75, 0.75]},
            "firstperson_lefthand": {"rotation": [0, 90, 0], "translation": [1.5, 0.5, 2], "scale": [0.75, 0.75, 0.75]},
            "gui": {"rotation": [0, 0, 0], "translation": [-1, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        },
    }


def build_swatches(path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for cx, cy, color in SWATCHES.values():
        for x in range(cx * 4, cx * 4 + 4):
            for y in range(cy * 4, cy * 4 + 4):
                img.putpixel((x, y), color + (255,))
    img.save(path)


def build_ammo_icon(path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    brass = [(214, 164, 62), (176, 126, 40), (120, 82, 24)]
    tip = [(236, 236, 242), (160, 164, 176), (96, 100, 112)]
    for i, x in enumerate((3, 7, 11)):
        top = 2 + (i % 2)
        # bullet tip (rows top..top+3), case (rows top+4..top+11)
        img.putpixel((x + 1, top), tip[0] + (255,))
        for dy in range(1, 4):
            for dx in range(3):
                img.putpixel((x + dx, top + dy), tip[dx] + (255,))
        for dy in range(4, 12):
            for dx in range(3):
                img.putpixel((x + dx, top + dy), brass[dx] + (255,))
        for dx in range(3):
            img.putpixel((x + dx, top + 12), (70, 48, 14, 255))
    img.save(path)


def main():
    os.makedirs(os.path.join(ROOT, "models", "item"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "textures", "item"), exist_ok=True)
    with open(os.path.join(ROOT, "models", "item", "dom_gun.json"), "w") as f:
        json.dump(build_model(), f, indent=2)
        f.write("\n")
    with open(os.path.join(ROOT, "models", "item", "dom_ammo.json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "domgun:item/dom_ammo"}}, f, indent=2)
        f.write("\n")
    build_swatches(os.path.join(ROOT, "textures", "item", "dom_gun.png"))
    build_ammo_icon(os.path.join(ROOT, "textures", "item", "dom_ammo.png"))


if __name__ == "__main__":
    main()
