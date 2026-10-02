"""Writes the mod's data and models: the router's blockstates and models, the items' models, the recipes, the
router's loot table (it keeps its modules and upgrades) and its tags.

    python3 tools/gen_data.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
MOD = "boundlessrouters"

MODULES = ["sender", "puller", "distributor", "dropper", "flinger", "placer", "breaker", "vacuum", "void", "player", "detector",
           "extruder"]
UPGRADES = ["speed", "stack", "range", "range_2", "range_3", "infinite_range", "muffler"]


def write(rel, data):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def item(id_):
    return {"item": id_ if ":" in id_ else "minecraft:" + id_}


def models():
    a = "assets/%s/" % MOD
    variants = {}
    for active in ("false", "true"):
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": "%s:block/router%s" % (MOD, "_active" if active == "true" else "")}
            if y:
                v["y"] = y
            variants["active=%s,facing=%s" % (active, facing)] = v
    write(a + "blockstates/router.json", {"variants": variants})
    for name, front in (("router", "router_front"), ("router_active", "router_front_active")):
        write(a + "models/block/%s.json" % name, {
            "parent": "minecraft:block/orientable_with_bottom",
            "textures": {"top": "%s:block/router_top" % MOD, "bottom": "%s:block/router_bottom" % MOD,
                         "side": "%s:block/router_side" % MOD, "front": "%s:block/%s" % (MOD, front)}})
    write(a + "models/item/router.json", {"parent": "%s:block/router" % MOD})
    names = ["blank_module", "blank_upgrade"] + [m + "_module" for m in MODULES] + [u + "_upgrade" for u in UPGRADES]
    for n in names:
        write(a + "models/item/%s.json" % n, {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, n)}})


def shaped(name, pattern, key, result, count=1):
    write("data/%s/recipes/%s.json" % (MOD, name), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": pattern,
        "key": {k: (v if isinstance(v, dict) else item(v)) for k, v in key.items()},
        "result": {"item": "%s:%s" % (MOD, result), "count": count}})


def shapeless(name, ingredients, result, count=1):
    write("data/%s/recipes/%s.json" % (MOD, name), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [i if isinstance(i, dict) else item(i) for i in ingredients],
        "result": {"item": "%s:%s" % (MOD, result), "count": count}})


def recipes():
    blank = MOD + ":blank_module"
    shaped("router", ["IRI", "RHR", "IRI"], {"I": "iron_ingot", "R": "redstone", "H": "hopper"}, "router")
    shaped("blank_module", [" R ", "PGP"], {"R": "redstone", "P": "paper", "G": "gold_nugget"}, "blank_module", 2)
    shaped("blank_upgrade", ["GRG", "NLN"], {"G": "gold_nugget", "R": "redstone", "N": "iron_nugget", "L": "lapis_lazuli"},
           "blank_upgrade", 4)
    keys = {"sender": ["ender_pearl"], "puller": ["hopper"], "distributor": ["ender_pearl", "comparator"], "dropper": ["dropper"],
            "flinger": ["dispenser"], "placer": ["piston"], "breaker": ["iron_pickaxe"], "vacuum": ["ender_eye"], "void": ["cactus"],
            "player": ["ender_chest"], "detector": ["observer"], "extruder": ["sticky_piston"]}
    for m in MODULES:
        shapeless(m + "_module", [blank] + keys[m], m + "_module")
    ub = MOD + ":blank_upgrade"
    ukeys = {"speed": ["sugar", "redstone"], "stack": ["chest"], "range": ["spyglass"], "muffler": [{"tag": "minecraft:wool"}]}
    for u in ukeys:
        shapeless(u + "_upgrade", [ub] + ukeys[u], u + "_upgrade")
    # each range tier is made from the one before it; the infinite one is hard to come by
    shapeless("range_2_upgrade", [MOD + ":range_upgrade", "ender_pearl", "ender_pearl", "gold_ingot", "gold_ingot"], "range_2_upgrade")
    shapeless("range_3_upgrade", [MOD + ":range_2_upgrade", "ender_eye", "ender_eye", "diamond", "diamond"], "range_3_upgrade")
    shaped("infinite_range_upgrade", ["ESE", "YRY", "ENE"],
           {"E": "echo_shard", "S": "nether_star", "Y": "ender_eye", "R": MOD + ":range_3_upgrade", "N": "netherite_ingot"},
           "infinite_range_upgrade")


def loot_and_tags():
    write("data/%s/loot_tables/blocks/router.json" % MOD, {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{
                "type": "minecraft:item", "name": "%s:router" % MOD,
                "functions": [{"function": "minecraft:copy_nbt", "source": "block_entity", "ops": [
                    {"source": "Modules", "target": "BlockEntityTag.Modules", "op": "replace"},
                    {"source": "Upgrades", "target": "BlockEntityTag.Upgrades", "op": "replace"},
                    {"source": "Redstone", "target": "BlockEntityTag.Redstone", "op": "replace"}]}]}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write("data/minecraft/tags/blocks/mineable/pickaxe.json", {"replace": False, "values": ["%s:router" % MOD]})


def main():
    models()
    recipes()
    loot_and_tags()
    print("data written")


if __name__ == "__main__":
    main()
