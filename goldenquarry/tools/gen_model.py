"""Writes the Golden Quarry blockstate and models (after the in-game shots in reference/ingame).

Run from the goldenquarry folder:  python3 tools/gen_model.py
One block. The chest is 10 high: a solid bottom 6 high, and on it the ornate front and back walls
(3 deep) with the sides open between them, so the drill inside shows; brown pillars stand at the
openings' edges. On the chest sits the open cage (sheets one pixel in from the edges, 6 high, a
ring on top). golden_quarry_drill is the drill standing in the hollow, which the block entity
renderer turns slowly; golden_quarry_glass is the glass over the cage and the side openings,
drawn translucent. The textures come from tools/extract_ingame.py and use the default UVs: every
face texture is laid out in block space.
"""
import json
import os

ASSETS = "src/main/resources/assets/goldenquarry"
SIDES = ["north", "south", "west", "east"]


def box(frm, to, faces):
    def tidy(v):
        v = round(v, 4)
        return int(v) if v == int(v) else v
    return {"from": [tidy(v) for v in frm], "to": [tidy(v) for v in to], "faces": faces}


def faces(tex, only=None, **over):
    names = only or SIDES + ["up", "down"]
    out = {n: {"texture": over.get(n, tex)} for n in names}
    return out


def top_elements():
    els = []
    # the solid bottom of the chest; its top is the floor of the hollow
    els.append(box([0, 0, 0], [16, 6, 16], {
        "north": {"texture": "#front"}, "south": {"texture": "#front"},
        "west": {"texture": "#side"}, "east": {"texture": "#side"},
        "up": {"texture": "#bottom"}, "down": {"texture": "#bottom", "cullface": "down"}}))
    # the front and back walls: ornate outside, gold and black inside
    els.append(box([0, 6, 0], [16, 10, 3], {
        "north": {"texture": "#front"}, "south": {"texture": "#inner"},
        "west": {"texture": "#side"}, "east": {"texture": "#side"}, "up": {"texture": "#walltop"}}))
    els.append(box([0, 6, 13], [16, 10, 16], {
        "south": {"texture": "#front"}, "north": {"texture": "#inner"},
        "west": {"texture": "#side"}, "east": {"texture": "#side"}, "up": {"texture": "#walltop"}}))
    # brown pillars at the edges of the side openings, one pixel above the chest
    for x in (0, 15):
        for z in (3, 12):
            els.append(dict(box([x, 6, z], [x + 1, 11, z + 1], faces("#pillar")), shade=False))
    # the cage: four sheets one pixel in from the edges, drawn inside and out, and the ring on top
    def sheet(frm, to, out, inn, tex):
        f = {out: {"texture": tex}}
        # the inside of a sheet shows the same picture mirrored
        u = [15, 0, 1, 6] if out in ("north", "east") else [1, 0, 15, 6]
        f[inn] = {"texture": tex, "uv": u}
        return box(frm, to, f)
    els.append(sheet([1, 10, 1], [15, 16, 1], "north", "south", "#front"))
    els.append(sheet([1, 10, 15], [15, 16, 15], "south", "north", "#front"))
    els.append(sheet([1, 10, 1], [1, 16, 15], "west", "east", "#side"))
    els.append(sheet([15, 10, 1], [15, 16, 15], "east", "west", "#side"))
    els.append(box([1, 16, 1], [15, 16, 15], {"up": {"texture": "#ring"}, "down": {"texture": "#ring"}}))
    # little grey brackets stepping in from the cage's corners, kept a hair off the cage sheets
    # (faces in the same plane as a sheet flicker)
    grey = {"texture": "#bracket", "uv": [15, 0, 16, 1]}
    for cx, cz, dx, dz in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        for step, y in ((0, 13), (1, 12)):
            x, z = cx + dx * step, cz + dz * step
            lo = [x + (0.1 if x == 1 else 0), y, z + (0.1 if z == 1 else 0)]
            hi = [x + 1 - (0.1 if x == 14 else 0), y + 1, z + 1 - (0.1 if z == 14 else 0)]
            els.append(box(lo, hi, {n: grey for n in SIDES + ["up", "down"]}))
    return els


def drill_elements():
    """The drill as measured on the reference shots (cameras fitted to the block's corners on shots
    1 and 3, see tools/extract_ingame.py): a black head 8.75 wide and 2.625 high whose texture is
    10 pixels across (0.875 each), a grey column under it narrowing to the tip in three blocks, four grey blades on the
    diagonals (a thin upper part out under the head, a lower part in at the shaft, so they close in
    towards the tip)."""
    els = []
    black = {"texture": "#drill", "uv": [10, 0, 11, 1]}
    side_a = {"texture": "#drill", "uv": [0, 10, 10, 13]}
    side_b = {"texture": "#drill", "uv": [0, 13, 10, 16]}
    els.append(box([3.625, 11.8, 3.625], [12.375, 14.425, 12.375], {"north": side_a, "south": side_a, "west": side_b, "east": side_b,
                                                                   "up": {"texture": "#drill", "uv": [0, 0, 10, 10]}, "down": black}))
    # the column narrows towards the tip: a big block under the head, a middle one, a small one
    for half, y0, y1, uv in ((2.0, 9.6, 11.8, [10, 6, 14, 9]), (1.3, 7.8, 9.6, [10, 9, 13, 12]), (0.65, 6, 7.8, [13, 9, 15, 12])):
        t = {"texture": "#drill", "uv": uv}
        els.append(box([8 - half, y0, 8 - half], [8 + half, y1, 8 + half], {n: t for n in SIDES + ["down"] + (["up"] if y1 < 11.8 else [])}))
    upper = {"texture": "#drill", "uv": [11, 0, 12, 2]}
    lower = [{"texture": "#drill", "uv": [12, 0, 13, 4]}, {"texture": "#drill", "uv": [13, 0, 14, 4]}]
    for i, (sx, sz) in enumerate(((-1, -1), (1, -1), (1, 1), (-1, 1))):
        # the upper part reaches over the lower one, so there is no slit between them
        cx, cz = 8 + sx * 2.15, 8 + sz * 2.15
        els.append(box([cx - 0.45, 9.9, cz - 0.45], [cx + 0.45, 11.8, cz + 0.45], {n: upper for n in SIDES + ["down"]}))
        # a joint where the two parts meet on the diagonal, so nothing shows through between them
        cx, cz = 8 + sx * 1.775, 8 + sz * 1.775
        els.append(box([cx - 0.45, 9.9, cz - 0.45], [cx + 0.45, 10.4, cz + 0.45], {n: upper for n in SIDES + ["up", "down"]}))
        cx, cz = 8 + sx * 1.4, 8 + sz * 1.4
        low = lower[i % 2]
        els.append(box([cx - 0.45, 6.6, cz - 0.45], [cx + 0.45, 10.4, cz + 0.45], {n: low for n in SIDES + ["up", "down"]}))
    return els


def glass_elements():
    """See-through glass over the cage's four sides and its top, and over the chest's side
    openings; the glass texture is clear wherever the frame has pixels of its own."""
    els = []
    def pane(frm, to, out, inn, tex, mirror):
        f = {out: {"texture": tex}}
        f[inn] = {"texture": tex, "uv": mirror}
        return box(frm, to, f)
    rev, fwd = [15, 0, 1, 6], [1, 0, 15, 6]
    els.append(pane([1, 10, 1], [15, 16, 1], "north", "south", "#glass", rev))
    els.append(pane([1, 10, 15], [15, 16, 15], "south", "north", "#glass", fwd))
    els.append(pane([1, 10, 1], [1, 16, 15], "west", "east", "#glass", fwd))
    els.append(pane([15, 10, 1], [15, 16, 15], "east", "west", "#glass", rev))
    els.append(box([1, 16, 1], [15, 16, 15], {"up": {"texture": "#glass_top"}, "down": {"texture": "#glass_top"}}))
    els.append(pane([0, 6, 4], [0, 10, 12], "west", "east", "#glass", [4, 6, 12, 10]))
    els.append(pane([16, 6, 4], [16, 10, 12], "east", "west", "#glass", [12, 6, 4, 10]))
    return els


TEXTURES = {
    "particle": "goldenquarry:block/qi_front",
    "front": "goldenquarry:block/qi_front",
    "side": "goldenquarry:block/qi_side",
    "inner": "goldenquarry:block/qi_inner",
    "bottom": "goldenquarry:block/qi_bottom",
    "pillar": "goldenquarry:block/qi_pillar",
    "walltop": "goldenquarry:block/qi_walltop",
    "ring": "goldenquarry:block/qi_ring",
    "bracket": "goldenquarry:block/qi_drill",
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(json.dumps(obj, indent=2) + "\n")


def main():
    for old in ("golden_quarry_base", "golden_quarry_top"):
        p = f"{ASSETS}/models/block/{old}.json"
        if os.path.exists(p):
            os.remove(p)
    write(f"{ASSETS}/models/block/golden_quarry.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": TEXTURES,
        "elements": top_elements(),
    })
    write(f"{ASSETS}/models/block/golden_quarry_drill.json", {
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {"particle": "goldenquarry:block/qi_drill", "drill": "goldenquarry:block/qi_drill"},
        "elements": drill_elements(),
    })
    write(f"{ASSETS}/models/block/golden_quarry_glass.json", {
        "render_type": "minecraft:translucent",
        "ambientocclusion": False,
        "textures": {"particle": "goldenquarry:block/qi_glass", "glass": "goldenquarry:block/qi_glass",
                     "glass_top": "goldenquarry:block/qi_glass_top"},
        "elements": glass_elements(),
    })
    write(f"{ASSETS}/models/item/golden_quarry.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": dict(TEXTURES, drill="goldenquarry:block/qi_drill"),
        "elements": top_elements() + drill_elements(),
    })
    variants = {}
    for facing, rot in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
        v = {"model": "goldenquarry:block/golden_quarry"}
        if rot:
            v["y"] = rot
        variants[f"facing={facing}"] = v
    write(f"{ASSETS}/blockstates/golden_quarry.json", {"variants": variants})


if __name__ == "__main__":
    main()
