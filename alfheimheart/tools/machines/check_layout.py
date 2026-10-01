"""Checks that the machines' GUI code and textures agree on where everything is.

    python3 tools/machines/check_layout.py

Every machine's layout lives in layouts.py: the Java code reads it from machine/MachineLayouts.java, which
layouts.py writes, and the panels are drawn on it (gen_v2.py for the machines' own looks; gen_gui.py, on
layout.py, for the living-wood panel the first machines shared). This makes sure the Java file is current, the
panels, gloss masks and widget sheets are their layouts' size, the sheets' shared pieces are where
machine/client/MachineScreen.java reads them and no two pieces of a sheet overlap, and the first-generation
screens' own numbers match layout.py. A slot a few pixels off its frame is easy to miss, so CI runs this. Needs
only the Python standard library.
"""
import ast
import os
import re
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import layout as L  # noqa: E402
import layouts as LS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reya", "alfheimheart")
MACHINE = os.path.join(JAVA, "machine")
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "alfheimheart", "textures", "gui")


def python_constants(path):
    """The module's top-level NAME = literal assignments (without running it)."""
    out = {}
    with open(path) as f:
        tree = ast.parse(f.read())
    for node in tree.body:
        if isinstance(node, ast.Assign):
            try:
                value = ast.literal_eval(node.value)
            except ValueError:
                continue
            for target in node.targets:
                if isinstance(target, ast.Name):
                    out[target.id] = value
                elif isinstance(target, ast.Tuple) and isinstance(value, tuple):
                    for t, v in zip(target.elts, value):
                        out[t.id] = v
    return out


def java_source(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def java_constants(path):
    """The class's `static final int A = 1, B = 2;` constants, int[] and int[][] tables."""
    src = java_source(path)
    out = {}
    for decl in re.findall(r"static final int\s+([^;]*);", src):
        for name, value in re.findall(r"(\w+)\s*=\s*(-?\d+)\b(?!\s*[-+*/.\[])", decl):
            out[name] = int(value)
    for decl in re.findall(r"static final float\s+([^;]*);", src):
        for name, value in re.findall(r"(\w+)\s*=\s*(-?\d+(?:\.\d+)?)F\b", decl):
            out[name] = float(value)
    for name, body in re.findall(r"static final int\[\]\[\]\s+(\w+)\s*=\s*\{(.*?)\};", src, re.S):
        out[name] = [tuple(int(v) for v in re.findall(r"-?\d+", pair)) for pair in re.findall(r"\{([^{}]*)\}", body)]
    for name, body in re.findall(r"static final int\[\]\s+(\w+)\s*=\s*\{([^{}]*)\};", src):
        out[name] = tuple(int(v) for v in re.findall(r"-?\d+", body))
    return out


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


def regions_fit(errors, what, regions, w, h):
    for i, (n1, x1, y1, w1, h1) in enumerate(regions):
        if x1 < 0 or y1 < 0 or x1 + w1 > w or y1 + h1 > h:
            errors.append(f"{what}: {n1} is outside the sheet")
        for (n2, x2, y2, w2, h2) in regions[i + 1:]:
            if x1 < x2 + w2 and x2 < x1 + w1 and y1 < y2 + h2 and y2 < y1 + h1:
                errors.append(f"{what}: {n1} and {n2} overlap")


def main():
    errors = []

    def same(what, code, texture):
        if code != texture:
            errors.append(f"{what}: code {code}, texture {texture}")

    # the Java layouts are layouts.py's
    generated = java_source(LS.JAVA_OUT)
    if generated != LS.java():
        errors.append("machine/MachineLayouts.java isn't current: run python3 tools/machines/layouts.py")

    # the pieces every sheet has where MachineScreen reads them
    screen = java_constants(os.path.join(MACHINE, "client", "MachineScreen.java"))
    same("widget sheet size", (screen["WIDGETS_W"], screen["WIDGETS_H"]), (LS.SHEET_W, LS.SHEET_H))
    same("button icons", screen["ICON_V"], LS.ICON_UV[1])
    same("gems", (screen["GEM_SIZE"], screen["GEM_U"], screen["GEM_V"]), (LS.GEM_SIZE,) + LS.GEM_UV)
    same("pool lamp", (screen["POOL_SIZE"], screen["POOL_U"], screen["POOL_V"]), (LS.POOL_SIZE,) + LS.POOL_UV)
    same("close button", (screen["CLOSE_SIZE"], screen["CLOSE_U"], screen["CLOSE_V"]), (LS.CLOSE_SIZE,) + LS.CLOSE_UV)
    same("glow", (screen["GLOW_SIZE"], screen["GLOW_U"], screen["GLOW_V"]), (LS.GLOW_SIZE,) + LS.GLOW_UV)
    same("title plaque", (screen["SCROLL_CAP"], screen["SCROLL_V"], screen["SCROLL_TILE_U"], screen["SCROLL_TILE_W"]),
         (LS.PLAQUE_CAP, LS.PLAQUE_V, LS.PLAQUE_TILE_U, LS.PLAQUE_TILE_W))
    same("texture margin", LS.M, L.M)

    # the first machines' shared sheet has them in the same places
    sheet = python_constants(os.path.join(HERE, "gen_widgets.py"))
    same("shared sheet size", (sheet["SHEET_W"], sheet["SHEET_H"]), (LS.SHEET_W, LS.SHEET_H))
    same("shared sheet: icons", sheet["ICON_V"], LS.ICON_UV[1])
    same("shared sheet: gems", (sheet["GEM_SIZE"],) + sheet["GEM_UV"], (LS.GEM_SIZE,) + LS.GEM_UV)
    same("shared sheet: pool light", (sheet["POOL_SIZE"],) + sheet["POOL_UV"], (LS.POOL_SIZE,) + LS.POOL_UV)
    same("shared sheet: close button", (sheet["CLOSE_SIZE"],) + sheet["CLOSE_UV"], (LS.CLOSE_SIZE,) + LS.CLOSE_UV)
    same("shared sheet: glow", (sheet["GLOW_SIZE"],) + sheet["GLOW_UV"], (LS.GLOW_SIZE,) + LS.GLOW_UV)
    same("shared sheet: plaque", (sheet["SCROLL_CAP"], sheet["SCROLL_V"], sheet["SCROLL_TILE_U"], sheet["SCROLL_TILE_W"]),
         (LS.PLAQUE_CAP, LS.PLAQUE_V, LS.PLAQUE_TILE_U, LS.PLAQUE_TILE_W))
    same("shared sheet: halo", (sheet["HALO_SIZE"],) + sheet["HALO_UV"], (LS.HALO_SIZE,) + LS.HALO_UV)
    same("shared sheet: lit arrows", (screen["ARROW_W"], screen["ARROW_H"], screen["ARROW_U"], screen["ARROW_V"]),
         (sheet["ARROW_W"], sheet["ARROW_H"], sheet["ARROW_UV"][0], sheet["ARROW_UV"][1]))
    same("shared sheet: mana fill", (screen["FILL_V"], screen["BAR_X2"] - screen["BAR_X1"], screen["BAR_Y2"] - screen["BAR_Y1"]),
         (sheet["FILL_V"], sheet["FILL_W"], sheet["FILL_H"]))
    same("first machines' mana bar", (screen["BAR_X1"], screen["BAR_Y1"], screen["BAR_X2"], screen["BAR_Y2"]), L.BAR)
    same("shared sheet png", png_size(os.path.join(TEXTURES, "machine_widgets.png")), (LS.SHEET_W, LS.SHEET_H))
    regions_fit(errors, "shared sheet", sheet["SHEET_REGIONS"], sheet["SHEET_W"], sheet["SHEET_H"])

    # every machine's textures are its layout's size; the second-generation sheets' pieces fit
    for name, d in LS.LAYOUTS:
        key = d["key"]
        w, h, _ = d["size"]
        same(f"{key} panel texture size", png_size(os.path.join(TEXTURES, key + ".png")), (w + 2 * LS.M, h + 2 * LS.M))
        if d["classic"]:
            continue
        same(f"{key} gloss mask size", png_size(os.path.join(TEXTURES, key + "_gloss.png")), (w + 2 * LS.M, h + 2 * LS.M))
        same(f"{key} widget sheet size", png_size(os.path.join(TEXTURES, key + "_widgets.png")), (LS.SHEET_W, LS.SHEET_H))
        regions_fit(errors, f"{key} sheet", LS.SHARED_REGIONS + LS.V2[key].SHEET, LS.SHEET_W, LS.SHEET_H)
        slots = d["inputs"] + d["outputs"] + d["special"]
        for i, (x1, y1) in enumerate(slots):
            if x1 - 1 < 0 or y1 - 1 < 0 or x1 + 17 > w or y1 + 17 > d["size"][2]:
                errors.append(f"{key}: slot {i} at {(x1, y1)} is off the machine's panel")
            for (x2, y2) in slots[i + 1:]:
                if abs(x1 - x2) < 18 and abs(y1 - y2) < 18:
                    errors.append(f"{key}: slots at {(x1, y1)} and {(x2, y2)} overlap")

    # the first-generation machines' own numbers
    infuser = java_constants(os.path.join(MACHINE, "infuser", "client", "ManaInfuserScreen.java"))
    same("infuser: the pool", (infuser["POOL_X"], infuser["POOL_Y"], infuser["SURFACE_RX"], infuser["SURFACE_RY"]),
         (L.POOL_.CENTER[0], L.POOL_.CENTER[1], L.POOL_.SURFACE_RX, L.POOL_.SURFACE_RY))
    same("infuser: the item", (infuser["ITEM_X"], infuser["ITEM_Y"], infuser["ITEM_RING_R"]),
         (L.POOL_.ITEM[0], L.POOL_.ITEM[1], L.POOL_.ITEM_RING_R))
    same("infuser: gem", (infuser["GEM_X"], infuser["GEM_Y"]), L.POOL_.GEM)
    same("infuser: heart's box", infuser["HEART_BOX"], L.POOL_.HEART_BOX)
    same("infuser: halo (sheet)", (infuser["HALO_U"], infuser["HALO_V"], infuser["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    daisy = java_constants(os.path.join(MACHINE, "daisy", "client", "PureDaisyScreen.java"))
    same("daisy: its middle, ring, heart", (daisy["DAISY_X"], daisy["DAISY_Y"], daisy["RING_R"], daisy["HEART_R"]),
         (L.DAISY.CENTER[0], L.DAISY.CENTER[1], L.DAISY.RING_R, L.DAISY.HEART_R))
    same("daisy: gem", (daisy["GEM_X"], daisy["GEM_Y"]), L.DAISY.GEM)
    same("daisy: stones", daisy["CELLS"], [tuple(p) for p in L.DAISY.CELLS])
    same("daisy: halo (sheet)", (daisy["HALO_U"], daisy["HALO_V"], daisy["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    bowl = java_constants(os.path.join(MACHINE, "apothecary", "client", "PetalApothecaryScreen.java"))
    same("apothecary: the water", (bowl["BOWL_X"], bowl["BOWL_Y"], bowl["WATER_RX"], bowl["WATER_RY"]),
         (L.BOWL.CENTER[0], L.BOWL.CENTER[1], L.BOWL.WATER_RX, L.BOWL.WATER_RY))
    same("apothecary: the flower", (bowl["ITEM_X"], bowl["ITEM_Y"], bowl["ITEM_RING_R"]), (L.BOWL.ITEM[0], L.BOWL.ITEM[1], L.BOWL.ITEM_RING_R))
    same("apothecary: gem", (bowl["GEM_X"], bowl["GEM_Y"]), L.BOWL.GEM)
    same("apothecary: heart's box", bowl["HEART_BOX"], L.BOWL.HEART_BOX)
    same("apothecary: halo (sheet)", (bowl["HALO_U"], bowl["HALO_V"], bowl["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    farm = java_constants(os.path.join(MACHINE, "farm", "client", "PetalFarmScreen.java"))
    same("farm: the planter", (farm["BED_X1"], farm["SOIL_Y"], farm["BED_X2"], farm["BED_Y2"]), L.FARM.BED)
    same("farm: the soil's top", farm["SOIL_TOP"], L.FARM.SOIL_TOP)
    same("farm: the sun's arc", (farm["SUN_X"], farm["SUN_Y"], farm["SUN_R"]), (L.FARM.SUN[0], L.FARM.SUN[1], L.FARM.SUN_R))
    same("farm: gem", (farm["GEM_X"], farm["GEM_Y"]), L.FARM.GEM)
    same("farm: heart's box", farm["HEART_BOX"], L.FARM.HEART_BOX)
    same("farm: halo (sheet)", (farm["HALO_U"], farm["HALO_V"], farm["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    mine = java_constants(os.path.join(MACHINE, "orechid", "client", "OrechidMineScreen.java"))
    same("mine: its window, ring, heart", (mine["MINE_X"], mine["MINE_Y"], mine["MINE_R"], mine["ROCK_R"], mine["ORE_R"], mine["HEART_R"]),
         (L.MINE.CENTER[0], L.MINE.CENTER[1], L.MINE.R, L.MINE.ROCK_R, L.MINE.ORE_R, L.MINE.HEART_R))
    same("mine: gem", (mine["GEM_X"], mine["GEM_Y"]), L.MINE.GEM)
    same("mine: ore sockets", mine["ORES"], [tuple(p) for p in L.MINE.ORES])
    same("mine: halo (sheet)", (mine["HALO_U"], mine["HALO_V"], mine["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    field = java_constants(os.path.join(MACHINE, "field", "client", "CropFieldScreen.java"))
    same("field: its frame", field["BOX"], L.FIELD.BOX)
    same("field: its rows", field["ROWS"], tuple(L.FIELD.ROWS))
    same("field: the cloud", (field["CLOUD_X"], field["CLOUD_Y"]), L.FIELD.CLOUD)
    same("field: gem", (field["GEM_X"], field["GEM_Y"]), L.FIELD.GEM)
    same("field: heart's box", field["HEART_BOX"], L.FIELD.HEART_BOX)

    # every machine with recipes in JEI has its click areas from its layout
    jei = java_source(os.path.join(JAVA, "client", "JeiCompat.java"))
    for name in ("RUNE_ALTAR", "TERRA_PLATE", "MANA_INFUSER", "PURE_DAISY", "PETAL_APOTHECARY", "ORECHID_MINE"):
        if "MachineLayouts." + name not in jei:
            errors.append(f"JEI: no click areas for {name} from its layout")

    if errors:
        print("The machines' GUI code and textures disagree:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print("Machines' GUI layout: code and textures agree.")


if __name__ == "__main__":
    main()
