"""Writes the Golden Quarry blockstate and models (after reference photo 1).

Run from the goldenquarry folder:  python3 tools/gen_model.py
The quarry is two blocks tall: golden_quarry_base is the plain dark lower block, golden_quarry_top
is the golden chest with the open cage and the drill; the item shows the top part.
"""
import json
import os

ASSETS = "src/main/resources/assets/goldenquarry"
SIDES = ["north", "south", "west", "east"]


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def faces(tex, names, uv=None, **extra):
    out = {}
    for n in names:
        f = {"texture": tex}
        if uv is not None:
            f["uv"] = uv(n) if callable(uv) else uv
        f.update(extra.get(n, {}))
        out[n] = f
    return out


def top_elements():
    els = []
    # golden chest body; its sides use rows 6..15 of the side texture (default uv)
    body = faces("#gold_side", SIDES)
    body["up"] = {"texture": "#gold_top"}
    body["down"] = {"texture": "#gold_top", "cullface": "down"}
    els.append(box([0, 0, 0], [16, 10, 16], body))
    # orange knobs at the feet of the posts
    for x, z in [(0, 0), (13.5, 0), (0, 13.5), (13.5, 13.5)]:
        f = faces("#bulb", SIDES, [0, 0, 2.5, 1.5])
        f["up"] = {"texture": "#bulb", "uv": [0, 2, 2.5, 4.5]}
        els.append(box([x, 10, z], [x + 2.5, 11.5, z + 2.5], f))
    # slim rust corner posts, nearly flush with the chest's edges
    for x, z in [(0.5, 0.5), (14, 0.5), (0.5, 14), (14, 14)]:
        els.append(box([x, 11.5, z], [x + 1.5, 14.5, z + 1.5], faces("#post", SIDES, [0, 0, 1.5, 3])))
    # slim top ring: north/south bars run the whole width, west/east bars fit between them
    for z in (0.5, 14):
        f = faces("#frame", SIDES, lambda n: [0, 0, 15, 1.5] if n in ("north", "south") else [0, 0, 1.5, 1.5])
        f["up"] = {"texture": "#frame", "uv": [0.5, 2, 15.5, 3.5]}
        f["down"] = {"texture": "#frame", "uv": [0.5, 4, 15.5, 5.5]}
        els.append(box([0.5, 14.5, z], [15.5, 16, z + 1.5], f))
    for x in (0.5, 14):
        f = faces("#frame", ["west", "east"], [0, 0, 12, 1.5])
        f["up"] = {"texture": "#frame", "uv": [1, 2, 2.5, 14]}
        f["down"] = {"texture": "#frame", "uv": [5, 2, 6.5, 14]}
        els.append(box([x, 14.5, 2], [x + 1.5, 16, 14], f))
    # rounded inner corners of the cage openings
    fill = lambda: faces("#post", ["north", "south", "west", "east", "down"], [2, 0, 3, 1])
    for x in (2, 13):
        for z in (0.75, 14.25):
            els.append(box([x, 13.5, z], [x + 1, 14.5, z + 1], fill()))
    for x in (0.75, 14.25):
        for z in (2, 13):
            els.append(box([x, 13.5, z], [x + 1, 14.5, z + 1], fill()))
    # clear glass panes between the posts, glints on the outside only (the far panes are culled)
    els.append(box([2, 10, 1.25], [14, 14.5, 1.25], faces("#glass", ["north"], [0, 0, 12, 4.5])))
    els.append(box([2, 10, 14.75], [14, 14.5, 14.75], faces("#glass", ["south"], [0, 0, 12, 4.5])))
    els.append(box([1.25, 10, 2], [1.25, 14.5, 14], faces("#glass", ["west"], [0, 0, 12, 4.5])))
    els.append(box([14.75, 10, 2], [14.75, 14.5, 14], faces("#glass", ["east"], [0, 0, 12, 4.5])))
    # the drill: cross beam in the ring, rod, dark motor box, stepped funnel, spiral shaft
    f = faces("#frame", ["north", "south"], [0, 0, 12, 1.5])
    f["up"] = {"texture": "#frame", "uv": [2, 6, 14, 8]}
    f["down"] = {"texture": "#frame", "uv": [2, 8, 14, 10]}
    els.append(box([2, 14.5, 7], [14, 16, 9], f))
    els.append(box([7.25, 13.75, 7.25], [8.75, 14.5, 8.75], faces("#drill", SIDES, [0, 0, 1.5, 0.75])))
    f = faces("#housing", SIDES, [0, 0, 9, 2])
    f["up"] = {"texture": "#housing", "uv": [0, 4, 9, 13]}
    f["down"] = {"texture": "#housing", "uv": [0, 4, 9, 13]}
    els.append(box([3.5, 11.75, 3.5], [12.5, 13.75, 12.5], f))
    f = faces("#drill", SIDES, [0, 0, 5, 0.75])
    f["down"] = {"texture": "#drill", "uv": [0, 4, 5, 9]}
    els.append(box([5.5, 11, 5.5], [10.5, 11.75, 10.5], f))
    f = faces("#drill", SIDES, [0, 1, 3, 1.5])
    f["down"] = {"texture": "#drill", "uv": [0, 4, 3, 7]}
    els.append(box([6.5, 10.5, 6.5], [9.5, 11, 9.5], f))
    els.append(box([7, 10, 7], [9, 10.5, 9], faces("#drill", SIDES, [0, 2, 2, 2.5])))
    return els


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(json.dumps(obj, indent=2) + "\n")


def main():
    t = lambda name: "goldenquarry:block/" + name
    write(f"{ASSETS}/models/block/golden_quarry_base.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": t("quarry_base")},
    })
    write(f"{ASSETS}/models/block/golden_quarry_top.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {
            "particle": t("quarry_gold_side"),
            "gold_side": t("quarry_gold_side"),
            "gold_top": t("quarry_gold_top"),
            "frame": t("quarry_frame"),
            "post": t("quarry_post"),
            "bulb": t("quarry_bulb"),
            "glass": t("quarry_glass"),
            "housing": t("quarry_housing"),
            "drill": t("quarry_drill"),
        },
        "elements": top_elements(),
    })
    write(f"{ASSETS}/models/item/golden_quarry.json", {"parent": "goldenquarry:block/golden_quarry_top"})
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
