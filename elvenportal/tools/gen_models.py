"""The block's models and blockstate: the elven moon gate, facing north.

    python3 tools/gen_models.py

The ring is built pixel for pixel from gen_block.RING: one element per run of ring pixels in each row, its
front and back showing gate_ring.png where the pixels are, its outer edges gold (gate_rim.png) and its
inner edges stone (gate_inner.png). A gem sits in the ring's top (lit while the portal is open: the
"_open" model), the vine is a cut-out pane just in front of the ring and another just behind it, and the
gate stands on a two-step pedestal. The portal inside the ring, the runes, the lights and the crystals
move, so the block's renderer draws them; the item's model has a still swirl in the ring.
"""
import json
import os

from pix import ASSETS
import gen_block as B

TEXTURES = {
    "particle": "elvenportal:block/gate_rim",
    "ring": "elvenportal:block/gate_ring",
    "rim": "elvenportal:block/gate_rim",
    "inner": "elvenportal:block/gate_inner",
    "vines": "elvenportal:block/gate_vines",
    "gem": "elvenportal:block/gate_gem",
    "base": "elvenportal:block/gate_base",
    "base_side": "elvenportal:block/gate_base_side",
}
RING_Z = (6, 10)                     # the ring's depth
GEM_BOX = ([7, 13, 5], [9, 16, 11])  # the gem stands out of the ring a pixel in front and behind
VINE_Z = (5.9, 10.1)


def runs():
    """(row, first column, end column) of every run of ring pixels (texture pixels)."""
    out = []
    for y, row in enumerate(B.RING):
        x = 0
        while x < 16:
            if row[x]:
                x0 = x
                while x < 16 and row[x]:
                    x += 1
                out.append((y, x0, x))
            else:
                x += 1
    return out


def ring_elements():
    els = []
    z1, z2 = RING_Z
    for (row, c1, c2) in runs():
        # texture column c is at model x = 16 - c (the front faces north: its left is +x)
        x1, x2 = 16 - c2, 16 - c1
        y1, y2 = 15 - row, 16 - row
        top_half = row + 0.5 < B.CY
        left, right = c2 <= B.CX, c1 >= B.CX          # which side of the ring the run is on, as drawn
        faces = {
            "north": {"texture": "#ring"},
            "south": {"texture": "#ring"},
            "up": {"texture": "#rim" if top_half else "#inner"},
            "down": {"texture": "#inner" if top_half else "#rim"},
            # east is +x, the drawing's left
            "east": {"texture": "#inner" if right else "#rim"},
            "west": {"texture": "#inner" if left else "#rim"},
        }
        if y2 == 16:
            faces["up"]["cullface"] = "up"
        els.append({"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces})
    return els


def gem_element():
    (x1, y1, z1), (x2, y2, z2) = GEM_BOX
    f, s, t = B.GEM_LAYOUT["front"], B.GEM_LAYOUT["side"], B.GEM_LAYOUT["top"]
    return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": {
        "north": {"uv": list(f), "texture": "#gem"},
        "south": {"uv": list(f), "texture": "#gem"},
        "east": {"uv": list(s), "texture": "#gem"},
        "west": {"uv": list(s), "texture": "#gem"},
        "up": {"uv": list(t), "texture": "#gem", "cullface": "up"},
        "down": {"uv": list(t), "texture": "#gem"}}}


def vine_elements():
    front, back = VINE_Z
    return [
        {"from": [0, 0, front], "to": [16, 16, front], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#vines"}}},
        {"from": [0, 0, back], "to": [16, 16, back], "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#vines"}}},
    ]


def pedestal_elements():
    def step(frm, to, cull_down):
        faces = {side: {"texture": "#base_side"} for side in ("north", "south", "east", "west")}
        faces["up"] = {"texture": "#base"}
        faces["down"] = {"texture": "#base"}
        if cull_down:
            faces["down"]["cullface"] = "down"
        return {"from": frm, "to": to, "faces": faces}
    return [step([1, 0, 2], [15, 1, 14], True), step([3, 1, 4], [13, 2, 12], False)]


def elements():
    return pedestal_elements() + ring_elements() + [gem_element()] + vine_elements()


SWIRL_PANE = {"from": [3, 4, 8], "to": [13, 14, 8], "faces": {
    "north": {"uv": [3, 3, 13, 13], "texture": "#swirl"},
    "south": {"uv": [3, 3, 13, 13], "texture": "#swirl"}}}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    models = os.path.join(ASSETS, "models")
    write(os.path.join(models, "block", "elven_portal.json"), {
        "render_type": "minecraft:cutout", "ambientocclusion": False, "textures": TEXTURES, "elements": elements()})
    write(os.path.join(models, "block", "elven_portal_open.json"), {
        "parent": "elvenportal:block/elven_portal",
        "textures": {"gem": "elvenportal:block/gate_gem_lit"}})
    item_textures = dict(TEXTURES)
    item_textures["gem"] = "elvenportal:block/gate_gem_lit"
    item_textures["swirl"] = "elvenportal:block/gate_swirl_still"
    write(os.path.join(models, "item", "elven_portal.json"), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "ambientocclusion": False,
        "textures": item_textures,
        "elements": elements() + [SWIRL_PANE],
        "display": {
            "gui": {"rotation": [20, 205, 0], "translation": [0, 0.5, 0], "scale": [0.7, 0.7, 0.7]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for is_open in (False, True):
            v = {"model": "elvenportal:block/elven_portal" + ("_open" if is_open else "")}
            if y:
                v["y"] = y
            variants[f"facing={facing},open={'true' if is_open else 'false'}"] = v
    write(os.path.join(ASSETS, "blockstates", "elven_portal.json"), {"variants": variants})
    print(len(elements()), "elements")


if __name__ == "__main__":
    main()
