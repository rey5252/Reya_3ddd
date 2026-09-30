"""The block's models and blockstate, as JSON (the arch is small enough to write out by hand, but its parts
are easier to keep straight here):

    python3 tools/gen_models.py

The arch faces north in the models: a livingrock plinth, two glimmering dreamwood pillars, a dreamwood
lintel in two halves with the Elven Gateway Core between them as its keystone (glowing while the portal is
open), four natura crystals at the pillars' feet. The portal itself is drawn by the block's renderer; the
item's model has it as a pane between the pillars.
"""
import json
import os

from pix import ASSETS

TEXTURES = {
    "particle": "botania:block/glimmering_dreamwood_log",
    "base_top": "botania:block/livingrock_bricks",
    "base_side": "botania:block/polished_livingrock",
    "pillar": "botania:block/glimmering_dreamwood_log",
    "beam": "botania:block/dreamwood_log",
    "beam_end": "botania:block/dreamwood_log_top",
    "keystone": "botania:block/alfheim_portal",
    "crystal": "elvenportal:block/natura_crystal",
}


def face(uv, tex, cull=None, rotation=None):
    f = {"uv": uv, "texture": "#" + tex}
    if cull:
        f["cullface"] = cull
    if rotation:
        f["rotation"] = rotation
    return f


def elements():
    els = []
    # the plinth
    els.append({"from": [0, 0, 1], "to": [16, 2, 15], "faces": {
        "down": face([0, 1, 16, 15], "base_top", "down"),
        "up": face([0, 1, 16, 15], "base_top"),
        "north": face([0, 14, 16, 16], "base_side"),
        "south": face([0, 14, 16, 16], "base_side"),
        "west": face([1, 14, 15, 16], "base_side", "west"),
        "east": face([1, 14, 15, 16], "base_side", "east")}})
    # the pillars
    for x1 in (1, 13):
        els.append({"from": [x1, 2, 4], "to": [x1 + 2, 13, 12], "faces": {
            "north": face([x1, 3, x1 + 2, 14], "pillar"),
            "south": face([16 - x1 - 2, 3, 16 - x1, 14], "pillar"),
            "west": face([4, 3, 12, 14], "pillar"),
            "east": face([4, 3, 12, 14], "pillar")}})
    # the lintel, in two halves either side of the keystone
    for (x1, x2) in ((0, 5), (11, 16)):
        els.append({"from": [x1, 13, 3], "to": [x2, 16, 13], "faces": {
            "down": face([x1, 3, x2, 13], "beam"),
            "up": face([x1, 3, x2, 13], "beam", "up"),
            "north": face([x1, 0, x2, 3], "beam"),
            "south": face([16 - x2, 0, 16 - x1, 3], "beam"),
            **({"west": face([3, 6, 13, 9], "beam_end", "west")} if x1 == 0 else {}),
            **({"east": face([3, 6, 13, 9], "beam_end", "east")} if x2 == 16 else {})}})
    # the keystone: the Elven Gateway Core, a little deeper than the lintel and hanging below it
    els.append({"from": [5, 11, 2], "to": [11, 16, 14], "faces": {
        "down": face([5, 2, 11, 14], "keystone"),
        "up": face([5, 2, 11, 14], "keystone", "up"),
        "north": face([5, 0, 11, 5], "keystone"),
        "south": face([5, 0, 11, 5], "keystone"),
        "west": face([2, 0, 14, 5], "keystone"),
        "east": face([2, 0, 14, 5], "keystone")}})
    # natura crystals at the pillars' feet, in front and behind
    for x1 in (1, 13):
        for z1 in (1.5, 12.5):
            els.append({"from": [x1, 2, z1], "to": [x1 + 2, 5, z1 + 2], "faces": {
                "up": face([6, 0, 8, 2], "crystal"),
                "north": face([6, 2, 8, 5], "crystal"),
                "south": face([7, 2, 9, 5], "crystal"),
                "west": face([5, 3, 7, 6], "crystal"),
                "east": face([8, 3, 10, 6], "crystal")}})
    return els


SWIRL_PANE = {"from": [3, 2, 7.75], "to": [13, 13, 8.25], "faces": {
    "north": {"uv": [3, 3, 13, 14], "texture": "#swirl"},
    "south": {"uv": [3, 3, 13, 14], "texture": "#swirl"}}}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    models = os.path.join(ASSETS, "models")
    write(os.path.join(models, "block", "elven_portal.json"), {"textures": TEXTURES, "elements": elements()})
    write(os.path.join(models, "block", "elven_portal_open.json"), {
        "parent": "elvenportal:block/elven_portal",
        "textures": {"keystone": "botania:block/alfheim_portal_activated"}})
    item_textures = dict(TEXTURES)
    item_textures["keystone"] = "botania:block/alfheim_portal_activated"
    item_textures["swirl"] = "elvenportal:block/portal_swirl"
    write(os.path.join(models, "item", "elven_portal.json"), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "textures": item_textures,
        "elements": elements() + [SWIRL_PANE],
        "display": {
            "gui": {"rotation": [25, 205, 0], "translation": [0, 0, 0], "scale": [0.66, 0.66, 0.66]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}}})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for is_open in (False, True):
            v = {"model": "elvenportal:block/elven_portal" + ("_open" if is_open else "")}
            if y:
                v["y"] = y
            variants[f"facing={facing},open={'true' if is_open else 'false'}"] = v
    write(os.path.join(ASSETS, "blockstates", "elven_portal.json"), {"variants": variants})


if __name__ == "__main__":
    main()
