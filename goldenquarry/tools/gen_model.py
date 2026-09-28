"""Writes the Golden Quarry blockstate and models (after reference photo 1).

Run from the goldenquarry folder:  python3 tools/gen_model.py
The quarry is two blocks tall: golden_quarry_base is the plain dark stand, golden_quarry_top the
golden chest (9 units high; two different faces, A and B, as in the photo) with the open cage on
it (6 units high, nothing across the top) and a dark hollow inside, seen through a window in face A.
golden_quarry_drill is the drill: motor in the cage, its body in the hollow, standing on its floor;
the block entity renderer spins it while the quarry digs. The item shows chest, cage and drill.
All textures come from the photo (tools/extract_block.py) and are shown unshaded, like the photo.
"""
import json
import os

ASSETS = "src/main/resources/assets/goldenquarry"
SIDES = ["north", "south", "west", "east"]


def box(frm, to, faces):
    return {"from": frm, "to": to, "shade": False, "faces": faces}


def top_elements():
    els = []
    # the chest: faces A on north/south, B on east/west, so every corner shows an A and a B
    body = {n: {"texture": "#side_a" if n in ("north", "south") else "#side_b", "uv": [0, 7, 16, 16]} for n in SIDES}
    body["up"] = {"texture": "#top"}
    body["down"] = {"texture": "#top", "cullface": "down"}
    els.append(box([0, 0, 0], [16, 9, 16], body))
    # the cage: four sheets one unit in from the edges, drawn inside and out; open on top
    outer, inner = [1, 10, 15, 16], [15, 10, 1, 16]
    els.append(box([1, 9, 1], [15, 15, 1], {"north": {"texture": "#cage_a", "uv": outer}, "south": {"texture": "#cage_a", "uv": inner}}))
    els.append(box([1, 9, 15], [15, 15, 15], {"south": {"texture": "#cage_a", "uv": outer}, "north": {"texture": "#cage_a", "uv": inner}}))
    els.append(box([1, 9, 1], [1, 15, 15], {"west": {"texture": "#cage_b", "uv": outer}, "east": {"texture": "#cage_b", "uv": inner}}))
    els.append(box([15, 9, 1], [15, 15, 15], {"east": {"texture": "#cage_b", "uv": outer}, "west": {"texture": "#cage_b", "uv": inner}}))
    # behind the window in face A: a dark hollow (walls facing inwards, right behind the chest's own
    # faces, so nothing shows through the window but the hollow and the drill)
    dark = {"texture": "#inside", "uv": [0, 7, 16, 10]}
    els.append(box([0, 6, 16], [16, 9, 16], {"north": dark}))
    els.append(box([0, 6, 0], [16, 9, 0], {"south": dark}))
    els.append(box([16, 6, 0], [16, 9, 16], {"west": dark}))
    els.append(box([0, 6, 0], [0, 9, 16], {"east": dark}))
    els.append(box([0, 6, 0], [16, 6, 16], {"up": {"texture": "#inside", "uv": [0, 0, 16, 16]}}))
    els.append(box([0, 9, 0], [16, 9, 16], {"down": {"texture": "#inside", "uv": [0, 0, 16, 16]}}))
    return els


def drill_elements():
    """Dark motor with its lid, a funnel and the bit standing on the chest."""
    def sides(uv_a, uv_b=None):
        return {n: {"texture": "#drill", "uv": uv_a if n in ("north", "south") else (uv_b or uv_a)} for n in SIDES}
    els = []
    f = sides([0, 2, 9, 3])
    f["up"] = {"texture": "#drill", "uv": [0, 6, 8, 14]}
    f["down"] = {"texture": "#drill", "uv": [0, 6, 8, 14]}
    els.append(box([3.5, 12.5, 3.5], [12.5, 13, 12.5], f))
    f = sides([0, 0, 8, 2], [8, 0, 16, 2])
    f["down"] = {"texture": "#drill", "uv": [0, 6, 8, 14]}
    els.append(box([4, 10.75, 4], [12, 12.5, 12], f))
    f = sides([0, 3, 6, 4])
    f["down"] = {"texture": "#drill", "uv": [0, 6, 6, 12]}
    els.append(box([5, 10, 5], [11, 10.75, 11], f))
    # the neck goes down through the chest's lid; below it, inside the chest and seen through the
    # window in face A, the drill's body stands on the floor of the hollow
    els.append(box([6.5, 9, 6.5], [9.5, 10, 9.5], sides([0, 4, 3, 5])))
    els.append(box([5, 6, 5], [11, 9, 11], sides([8, 6, 14, 9])))
    return els


TEXTURES = {
    "particle": "goldenquarry:block/quarry_side_a",
    "side_a": "goldenquarry:block/quarry_side_a",
    "side_b": "goldenquarry:block/quarry_side_b",
    "top": "goldenquarry:block/quarry_gold_top",
    "cage_a": "goldenquarry:block/quarry_cage_a",
    "cage_b": "goldenquarry:block/quarry_cage_b",
    "inside": "goldenquarry:block/quarry_inside",
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(json.dumps(obj, indent=2) + "\n")


def main():
    base_faces = {n: {"texture": "#base", "cullface": n} for n in SIDES + ["up", "down"]}
    write(f"{ASSETS}/models/block/golden_quarry_base.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "goldenquarry:block/quarry_base", "base": "goldenquarry:block/quarry_base"},
        "elements": [box([0, 0, 0], [16, 16, 16], base_faces)],
    })
    write(f"{ASSETS}/models/block/golden_quarry_top.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": TEXTURES,
        "elements": top_elements(),
    })
    write(f"{ASSETS}/models/block/golden_quarry_drill.json", {
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {"particle": "goldenquarry:block/quarry_drill", "drill": "goldenquarry:block/quarry_drill"},
        "elements": drill_elements(),
    })
    write(f"{ASSETS}/models/item/golden_quarry.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": dict(TEXTURES, drill="goldenquarry:block/quarry_drill"),
        "elements": top_elements() + drill_elements(),
    })
    variants = {}
    for facing, rot in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
        variants[f"facing={facing},half=lower"] = {"model": "goldenquarry:block/golden_quarry_base"}
        top = {"model": "goldenquarry:block/golden_quarry_top"}
        if rot:
            top["y"] = rot
        variants[f"facing={facing},half=upper"] = top
    write(f"{ASSETS}/blockstates/golden_quarry.json", {"variants": variants})


if __name__ == "__main__":
    main()
