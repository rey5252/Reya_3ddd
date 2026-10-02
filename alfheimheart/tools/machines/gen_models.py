"""The machines' block models, item models and blockstates.

    python3 tools/machines/gen_models.py

Every face uses its texture's pixels where the face is (the models' automatic UVs): gen_block.py draws each
texture with that in mind. What moves (the orbiting ingredients, the lit runes, the light gathering over the
plate, the mana in the basin) the machines' renderers draw.
"""
import json
import os

from pix import ASSETS

SIDES = ("north", "south", "east", "west")


def box(frm, to, faces):
    return {"from": list(frm), "to": list(to), "faces": faces}


def all_faces(side, up, down, cull_down=False, skip=()):
    faces = {s: {"texture": side} for s in SIDES if s not in skip}
    if "up" not in skip:
        faces["up"] = {"texture": up}
    if "down" not in skip:
        faces["down"] = {"texture": down}
        if cull_down:
            faces["down"]["cullface"] = "down"
    return faces


def crystal(x, y, z, w=2.0, h=2.5):
    return box((x, y, z), (x + w, y + h, z + w), all_faces("#crystal", "#crystal", "#crystal"))


def rune_altar():
    textures = {"particle": "alfheimheart:block/machine_bricks", "bricks": "alfheimheart:block/machine_bricks",
                "planks": "alfheimheart:block/machine_planks", "body": "alfheimheart:block/rune_altar_body",
                "top": "alfheimheart:block/rune_altar_top", "rim": "alfheimheart:block/rune_altar_rim",
                "crystal": "alfheimheart:block/machine_crystal"}
    elements = [
        box((1, 0, 1), (15, 2, 15), all_faces("#bricks", "#bricks", "#bricks", cull_down=True)),
        box((2, 2, 2), (14, 9, 14), all_faces("#body", "#planks", "#planks", skip=("up", "down"))),
        box((1, 9, 1), (15, 12, 15), all_faces("#rim", "#top", "#bricks")),
        crystal(1.75, 12, 1.75, 1.5, 3.0), crystal(12.75, 12, 1.75, 1.5, 3.0), crystal(1.75, 12, 12.75, 1.5, 3.0),
        crystal(12.75, 12, 12.75, 1.5, 3.0),
    ]
    return textures, elements


def terra_plate():
    textures = {"particle": "alfheimheart:block/terra_plate_top", "planks": "alfheimheart:block/machine_planks",
                "base": "alfheimheart:block/terra_plate_base", "top": "alfheimheart:block/terra_plate_top",
                "side": "alfheimheart:block/terra_plate_side", "bricks": "alfheimheart:block/machine_bricks",
                "crystal": "alfheimheart:block/machine_crystal"}
    elements = [
        box((0, 0, 0), (16, 3, 16), all_faces("#base", "#planks", "#planks", cull_down=True)),
        box((1, 3, 1), (15, 5, 15), all_faces("#side", "#top", "#planks", skip=("down",))),
    ]
    for (x, z) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        elements.append(box((x, 3, z), (x + 2, 6, z + 2), all_faces("#bricks", "#bricks", "#bricks", skip=("down",))))
        elements.append(crystal(x + 0.5, 6, z + 0.5, 1.0, 2.0))
    return textures, elements


def mana_infuser():
    textures = {"particle": "alfheimheart:block/mana_infuser_rim", "bricks": "alfheimheart:block/machine_bricks",
                "planks": "alfheimheart:block/machine_planks", "column": "alfheimheart:block/mana_infuser_column",
                "side": "alfheimheart:block/mana_infuser_side", "inner": "alfheimheart:block/mana_infuser_inner",
                "rim": "alfheimheart:block/mana_infuser_rim"}
    elements = [
        box((2, 0, 2), (14, 2, 14), all_faces("#bricks", "#bricks", "#bricks", cull_down=True)),
        box((4, 2, 4), (12, 6, 12), all_faces("#column", "#planks", "#planks", skip=("up", "down"))),
        box((1, 6, 1), (15, 7, 15), all_faces("#side", "#inner", "#bricks")),
        # the basin's walls: north and south the whole width, west and east between them
        box((1, 7, 1), (15, 12, 2), {"north": {"texture": "#side"}, "south": {"texture": "#inner"}, "up": {"texture": "#rim"},
                                    "east": {"texture": "#side"}, "west": {"texture": "#side"}}),
        box((1, 7, 14), (15, 12, 15), {"south": {"texture": "#side"}, "north": {"texture": "#inner"}, "up": {"texture": "#rim"},
                                      "east": {"texture": "#side"}, "west": {"texture": "#side"}}),
        box((1, 7, 2), (2, 12, 14), {"west": {"texture": "#side"}, "east": {"texture": "#inner"}, "up": {"texture": "#rim"}}),
        box((14, 7, 2), (15, 12, 14), {"east": {"texture": "#side"}, "west": {"texture": "#inner"}, "up": {"texture": "#rim"}}),
    ]
    return textures, elements


def pure_daisy():
    textures = {"particle": "alfheimheart:block/pure_daisy_top", "bricks": "alfheimheart:block/machine_bricks",
                "side": "alfheimheart:block/pure_daisy_side", "top": "alfheimheart:block/pure_daisy_top",
                "planks": "alfheimheart:block/machine_planks"}
    elements = [
        box((1, 0, 1), (15, 1, 15), all_faces("#bricks", "#bricks", "#bricks", cull_down=True)),
        box((2, 1, 2), (14, 7, 14), all_faces("#side", "#planks", "#planks", skip=("up", "down"))),
        box((1, 7, 1), (15, 9, 15), all_faces("#side", "#top", "#bricks")),
    ]
    return textures, elements


def petal_apothecary():
    textures = {"particle": "alfheimheart:block/mana_infuser_rim", "bricks": "alfheimheart:block/machine_bricks",
                "planks": "alfheimheart:block/machine_planks", "column": "alfheimheart:block/mana_infuser_column",
                "side": "alfheimheart:block/petal_apothecary_side", "inner": "alfheimheart:block/petal_apothecary_inner",
                "rim": "alfheimheart:block/mana_infuser_rim"}
    elements = [
        box((3, 0, 3), (13, 2, 13), all_faces("#bricks", "#bricks", "#bricks", cull_down=True)),
        box((5, 2, 5), (11, 7, 11), all_faces("#column", "#planks", "#planks", skip=("up", "down"))),
        box((1, 7, 1), (15, 8, 15), all_faces("#side", "#inner", "#bricks")),
        box((1, 8, 1), (15, 13, 2), {"north": {"texture": "#side"}, "south": {"texture": "#inner"}, "up": {"texture": "#rim"},
                                    "east": {"texture": "#side"}, "west": {"texture": "#side"}}),
        box((1, 8, 14), (15, 13, 15), {"south": {"texture": "#side"}, "north": {"texture": "#inner"}, "up": {"texture": "#rim"},
                                      "east": {"texture": "#side"}, "west": {"texture": "#side"}}),
        box((1, 8, 2), (2, 13, 14), {"west": {"texture": "#side"}, "east": {"texture": "#inner"}, "up": {"texture": "#rim"}}),
        box((14, 8, 2), (15, 13, 14), {"east": {"texture": "#side"}, "west": {"texture": "#inner"}, "up": {"texture": "#rim"}}),
    ]
    return textures, elements


def petal_farm():
    textures = {"particle": "alfheimheart:block/petal_farm_side", "bricks": "alfheimheart:block/machine_bricks",
                "planks": "alfheimheart:block/machine_planks", "side": "alfheimheart:block/petal_farm_side",
                "soil": "alfheimheart:block/petal_farm_soil", "crystal": "alfheimheart:block/machine_crystal"}
    elements = [box((0, 0, 0), (16, 7, 16), all_faces("#side", "#soil", "#planks", cull_down=True))]
    for (x, z) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        elements.append(box((x, 7, z), (x + 2, 8, z + 2), all_faces("#bricks", "#bricks", "#bricks", skip=("down",))))
        elements.append(crystal(x + 0.5, 8, z + 0.5, 1.0, 1.5))
    return textures, elements


def orechid_mine():
    textures = {"particle": "alfheimheart:block/orechid_mine_side", "bricks": "alfheimheart:block/machine_bricks",
                "side": "alfheimheart:block/orechid_mine_side", "top": "alfheimheart:block/orechid_mine_top",
                "crystal": "alfheimheart:block/machine_crystal"}
    elements = [box((0, 0, 0), (16, 10, 16), all_faces("#side", "#top", "#bricks", cull_down=True))]
    for (x, z) in ((0, 0), (14, 0), (0, 14)):
        elements.append(crystal(x + 0.5, 10, z + 0.5, 1.0, 2.0))
    return textures, elements


def crop_field():
    textures = {"particle": "alfheimheart:block/petal_farm_side", "bricks": "alfheimheart:block/machine_bricks",
                "planks": "alfheimheart:block/machine_planks", "side": "alfheimheart:block/petal_farm_side",
                "soil": "alfheimheart:block/crop_field_top", "crystal": "alfheimheart:block/machine_crystal"}
    elements = [box((0, 0, 0), (16, 7, 16), all_faces("#side", "#soil", "#planks", cull_down=True))]
    for (x, z) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        elements.append(box((x, 7, z), (x + 2, 8, z + 2), all_faces("#bricks", "#bricks", "#bricks", skip=("down",))))
    return textures, elements


MACHINES = {"rune_altar": rune_altar, "terra_plate": terra_plate, "mana_infuser": mana_infuser, "pure_daisy": pure_daisy,
            "petal_apothecary": petal_apothecary, "petal_farm": petal_farm, "orechid_mine": orechid_mine, "crop_field": crop_field}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    models = os.path.join(ASSETS, "models")
    for name, make in MACHINES.items():
        textures, elements = make()
        write(os.path.join(models, "block", name + ".json"),
              {"parent": "minecraft:block/block", "textures": textures, "elements": elements})
        write(os.path.join(models, "item", name + ".json"), {"parent": "alfheimheart:block/" + name})
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for active in ("false", "true"):
                v = {"model": "alfheimheart:block/" + name}
                if y:
                    v["y"] = y
                variants[f"active={active},facing={facing}"] = v
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


if __name__ == "__main__":
    main()
