"""Writes Singularity Fusion's data and models: the blockstates and block models (their glowing overlays lit at full
brightness whatever the light), the items' models, the crafting and fusion recipes (Draconic Evolution's items in the
top ones when it is installed, vanilla ones when not), the loot tables and the tags.

    python3 tools/gen_data.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
MOD = "singularityfusion"
DE = "draconicevolution"

CASINGS = ["void_casing", "void_casing_rune", "void_casing_seam"]
BLOCKS = CASINGS + ["fusion_core", "graviton_pylon", "creative_cell"]
ITEMS = ["graviton_crystal", "singularity_shard", "collapsed_star", "event_horizon_core"]
FACES = ["north", "south", "east", "west", "up", "down"]


def write(rel, data):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def tex(name):
    return "%s:block/%s" % (MOD, name)


# ------------------------------------------------------------------ models

def layered(name, base, glow):
    """A full block from per-face textures (base: face -> texture), with a glowing overlay on the faces in glow
    (face -> texture) drawn over them at full brightness."""
    textures = {"particle": tex(base["north"])}
    faces_base, faces_glow = {}, {}
    for face in FACES:
        key = "b_" + face
        textures[key] = tex(base[face])
        faces_base[face] = {"texture": "#" + key, "cullface": face}
        if face in glow:
            gkey = "g_" + face
            textures[gkey] = tex(glow[face])
            faces_glow[face] = {"texture": "#" + gkey, "cullface": face}
    elements = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces_base}]
    if faces_glow:
        elements.append({"from": [0, 0, 0], "to": [16, 16, 16], "shade": False,
                         "forge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}, "faces": faces_glow})
    write("assets/%s/models/block/%s.json" % (MOD, name), {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": elements})


def all_faces(texture):
    return {f: texture for f in FACES}


def models():
    a = "assets/%s/" % MOD
    write(a + "models/block/void_casing.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("void_casing")}})
    layered("void_casing_rune", all_faces("void_casing_rune"), all_faces("void_casing_rune_glow"))
    layered("void_casing_seam", all_faces("void_casing_seam"), all_faces("void_casing_seam_glow"))
    core = {"north": "fusion_core_side", "south": "fusion_core_side", "east": "fusion_core_side", "west": "fusion_core_side",
            "up": "fusion_core_top", "down": "fusion_core_bottom"}
    for name, suffix in (("fusion_core", "_glow_off"), ("fusion_core_lit", "_glow")):
        glow = {f: core[f] + suffix for f in ("north", "south", "east", "west", "up")}
        layered(name, core, glow)
    pylon = {"north": "graviton_pylon_side", "south": "graviton_pylon_side", "east": "graviton_pylon_side", "west": "graviton_pylon_side",
             "up": "graviton_pylon_top", "down": "graviton_pylon_bottom"}
    layered("graviton_pylon", pylon, {f: pylon[f] + "_glow" for f in ("north", "south", "east", "west", "up")})
    layered("creative_cell", all_faces("creative_cell"), all_faces("creative_cell_glow"))

    for b in BLOCKS:
        if b == "fusion_core":
            write(a + "blockstates/fusion_core.json", {"variants": {
                "lit=false": {"model": "%s:block/fusion_core" % MOD}, "lit=true": {"model": "%s:block/fusion_core_lit" % MOD}}})
        else:
            write(a + "blockstates/%s.json" % b, {"variants": {"": {"model": "%s:block/%s" % (MOD, b)}}})
        write(a + "models/item/%s.json" % b, {"parent": "%s:block/%s" % (MOD, b)})
    for i in ITEMS:
        write(a + "models/item/%s.json" % i, {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, i)}})


# ------------------------------------------------------------------ recipes

def item(id_, count=None):
    out = {"item": id_ if ":" in id_ else "minecraft:" + id_}
    if count:
        out["count"] = count
    return out


def ours(name):
    return "%s:%s" % (MOD, name)


WITH_DE = [{"type": "forge:mod_loaded", "modid": DE}]
WITHOUT_DE = [{"type": "forge:not", "value": {"type": "forge:mod_loaded", "modid": DE}}]


def shaped(name, pattern, key, result, count=1, conditions=None):
    data = {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: (v if isinstance(v, dict) else item(v)) for k, v in key.items()},
            "result": {"item": ours(result), "count": count}}
    if conditions:
        data = {"conditions": conditions, **data}
    write("data/%s/recipes/%s.json" % (MOD, name), data)


def shapeless(name, ingredients, result, count=1):
    write("data/%s/recipes/%s.json" % (MOD, name), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [i if isinstance(i, dict) else item(i) for i in ingredients],
        "result": {"item": ours(result), "count": count}})


def fusion(name, catalyst, ingredients, result, energy, time, conditions=None):
    """A fusion: the catalyst in the core, the ingredients (id, count) on the pylons, the energy it takes (FE/RF/OP)."""
    data = {"type": ours("fusion"), "catalyst": item(catalyst),
            "ingredients": [item(i, c if c > 1 else None) for (i, c) in ingredients],
            "result": {"item": result if ":" in result else ours(result)}, "energy": energy, "time": time}
    if conditions:
        data = {"conditions": conditions, **data}
    write("data/%s/recipes/fusion/%s.json" % (MOD, name), data)


# what each fusion takes: how much energy (of a full core's 2,000,000,000) and how long (ticks)
FUSIONS = {
    "singularity_shard": 64_000_000,
    "collapsed_star": 256_000_000,
    "event_horizon_core": 1_024_000_000,
    "creative_cell": 2_000_000_000,
}


def recipes():
    shapeless("graviton_crystal", ["amethyst_shard", "amethyst_shard", "popped_chorus_fruit", "ender_eye"], "graviton_crystal", 2)
    shaped("void_casing", ["DDD", "DCD", "DDD"], {"D": "polished_deepslate", "C": ours("graviton_crystal")}, "void_casing", 8)
    shapeless("void_casing_rune", [ours("void_casing"), "glowstone_dust", "amethyst_shard"], "void_casing_rune")
    shapeless("void_casing_seam", [ours("void_casing"), "end_rod"], "void_casing_seam")
    shaped("graviton_pylon", [" C ", "ERE", "VVV"],
           {"C": ours("graviton_crystal"), "E": "ender_eye", "R": "end_rod", "V": ours("void_casing")}, "graviton_pylon")
    # the core: with Draconic Evolution, round an awakened core; without it, a nether star
    shaped("fusion_core", ["VCV", "CSC", "VNV"],
           {"V": ours("void_casing"), "C": ours("graviton_crystal"), "S": "nether_star", "N": "netherite_ingot"}, "fusion_core",
           conditions=WITHOUT_DE)
    shaped("fusion_core_draconic", ["VCV", "CAC", "VNV"],
           {"V": ours("void_casing"), "C": ours("graviton_crystal"), "A": DE + ":awakened_core", "N": "netherite_ingot"}, "fusion_core",
           conditions=WITH_DE)

    fusion("singularity_shard", "nether_star", [("echo_shard", 2), ("dragon_breath", 2)], "singularity_shard",
           FUSIONS["singularity_shard"], 200)
    fusion("collapsed_star", ours("singularity_shard"), [("nether_star", 4), ("netherite_ingot", 4)], "collapsed_star",
           FUSIONS["collapsed_star"], 300)
    fusion("event_horizon_core_draconic", DE + ":chaotic_core",
           [(DE + ":chaos_shard", 4), (DE + ":dragon_heart", 2), (ours("collapsed_star"), 2)], "event_horizon_core",
           FUSIONS["event_horizon_core"], 400, WITH_DE)
    fusion("event_horizon_core", ours("collapsed_star"),
           [("nether_star", 2), ("echo_shard", 2), ("netherite_block", 2), ("totem_of_undying", 2)], "event_horizon_core",
           FUSIONS["event_horizon_core"], 400, WITHOUT_DE)
    fusion("creative_cell", ours("event_horizon_core"), [("nether_star", 4), (ours("collapsed_star"), 4)], "creative_cell",
           FUSIONS["creative_cell"], 600)


# ------------------------------------------------------------------ loot and tags

def loot_and_tags():
    for b in BLOCKS:
        write("data/%s/loot_tables/blocks/%s.json" % (MOD, b), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": ours(b)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write("data/%s/tags/blocks/foundation.json" % MOD, {"replace": False, "values": [ours(c) for c in CASINGS]})
    write("data/minecraft/tags/blocks/mineable/pickaxe.json", {"replace": False, "values": [ours(b) for b in BLOCKS]})
    write("data/minecraft/tags/blocks/needs_diamond_tool.json", {"replace": False, "values": [ours("fusion_core"), ours("graviton_pylon")]})
    write("data/minecraft/tags/blocks/needs_iron_tool.json", {"replace": False, "values": [ours(c) for c in CASINGS]})


def main():
    models()
    recipes()
    loot_and_tags()
    print("data written")


if __name__ == "__main__":
    main()
