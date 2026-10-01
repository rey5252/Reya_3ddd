"""Checks that the machines' GUI code and textures agree on where everything is.

    python3 tools/machines/check_layout.py

The panels are drawn by gen_gui.py on the layout of layout.py, the widget sheet by gen_widgets.py; the slots and
the moving parts are placed by machine/MachineMenu.java, machine/client/MachineScreen.java and each machine's
menu and screen (and JEI's click areas by client/JeiCompat.java). A slot a few pixels off its frame is easy to
miss, so CI runs this. Needs only the Python standard library.
"""
import ast
import os
import re
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import layout as L  # noqa: E402

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
        for name, value in re.findall(r"(\w+)\s*=\s*(-?\d+)\b(?!\s*[-+*/.])", decl):
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


def main():
    sheet = python_constants(os.path.join(HERE, "gen_widgets.py"))
    menu = java_constants(os.path.join(MACHINE, "MachineMenu.java"))
    screen = java_constants(os.path.join(MACHINE, "client", "MachineScreen.java"))
    errors = []

    def same(what, code, texture):
        if code != texture:
            errors.append(f"{what}: code {code}, texture {texture}")

    # the menu's slots on the panels' frames
    same("panel size", (menu["WIDTH"], menu["HEIGHT"]), (L.W, L.H))
    same("input slots", (menu["INPUT_X"], menu["GRID_Y"]), L.INPUT)
    same("output slots", (menu["OUTPUT_X"], menu["GRID_Y"]), L.OUTPUT)
    same("inventory", (menu["INV_X"], menu["INV_Y"], menu["HOTBAR_Y"]), (L.INV_SLOT_X, L.INV_Y, L.HOTBAR_Y))

    # what every machine's screen draws over its panel
    same("texture margin", screen["M"], L.M)
    same("machine panel height", screen["MACHINE_H"], L.MACHINE_H)
    same("inventory panel", (screen["INV_PANEL_X1"], screen["INV_PANEL_X2"]), (L.INV_X1, L.INV_X2))
    same("arrows", [(screen["ARROW_IN_X"], screen["ARROW_Y"]), (screen["ARROW_OUT_X"], screen["ARROW_Y"])], [tuple(a) for a in L.ARROWS])
    same("arrow size", (screen["ARROW_W"], screen["ARROW_H"]), (L.ARROW_W, L.ARROW_H))
    same("mana bar", (screen["BAR_X1"], screen["BAR_Y1"], screen["BAR_X2"], screen["BAR_Y2"]), L.BAR)
    same("buttons", [(screen["BUTTON_REDSTONE_X"], screen["BUTTON_Y"])], [tuple(b) for b in L.BUTTONS])
    same("pool light", (screen["POOL_X"], screen["POOL_Y"]), L.POOL)
    same("corner crystals", screen["LIGHTS"], [tuple(p) for p in L.LIGHTS])
    same("flights: output grid's middle", (screen["OUTPUT_MID_X"], screen["GRID_MID_Y"]), (L.OUTPUT[0] + 26, L.OUTPUT[1] + 26))
    same("flights: input grid's middle", (screen["INPUT_MID_X"], screen["GRID_MID_Y"]), (L.INPUT[0] + 26, L.INPUT[1] + 26))

    # the shared sheet
    same("widget sheet size (generator)", (screen["WIDGETS_W"], screen["WIDGETS_H"]), (sheet["SHEET_W"], sheet["SHEET_H"]))
    same("button icons", screen["ICON_V"], sheet["ICON_V"])
    same("gems", (screen["GEM_SIZE"], screen["GEM_U"], screen["GEM_V"]), (sheet["GEM_SIZE"], sheet["GEM_UV"][0], sheet["GEM_UV"][1]))
    same("lit arrows", (screen["ARROW_W"], screen["ARROW_H"], screen["ARROW_U"], screen["ARROW_V"]),
         (sheet["ARROW_W"], sheet["ARROW_H"], sheet["ARROW_UV"][0], sheet["ARROW_UV"][1]))
    same("mana fill", (screen["FILL_V"], screen["BAR_X2"] - screen["BAR_X1"], screen["BAR_Y2"] - screen["BAR_Y1"]),
         (sheet["FILL_V"], sheet["FILL_W"], sheet["FILL_H"]))
    same("pool light (sheet)", (screen["POOL_SIZE"], screen["POOL_U"], screen["POOL_V"]),
         (sheet["POOL_SIZE"], sheet["POOL_UV"][0], sheet["POOL_UV"][1]))
    same("close button", (screen["CLOSE_SIZE"], screen["CLOSE_U"], screen["CLOSE_V"]),
         (sheet["CLOSE_SIZE"], sheet["CLOSE_UV"][0], sheet["CLOSE_UV"][1]))
    same("glow", (screen["GLOW_SIZE"], screen["GLOW_U"], screen["GLOW_V"]), (sheet["GLOW_SIZE"], sheet["GLOW_UV"][0], sheet["GLOW_UV"][1]))
    same("title plaque", (screen["SCROLL_CAP"], screen["SCROLL_V"], screen["SCROLL_TILE_U"], screen["SCROLL_TILE_W"]),
         (sheet["SCROLL_CAP"], sheet["SCROLL_V"], sheet["SCROLL_TILE_U"], sheet["SCROLL_TILE_W"]))
    same("widget sheet size", png_size(os.path.join(TEXTURES, "machine_widgets.png")), (screen["WIDGETS_W"], screen["WIDGETS_H"]))
    regions = sheet["SHEET_REGIONS"]
    for i, (n1, x1, y1, w1, h1) in enumerate(regions):
        if x1 < 0 or y1 < 0 or x1 + w1 > sheet["SHEET_W"] or y1 + h1 > sheet["SHEET_H"]:
            errors.append(f"widget sheet: {n1} is outside the sheet")
        for (n2, x2, y2, w2, h2) in regions[i + 1:]:
            if x1 < x2 + w2 and x2 < x1 + w1 and y1 < y2 + h2 and y2 < y1 + h1:
                errors.append(f"widget sheet: {n1} and {n2} overlap")

    # each machine's panel, its special slot and its heart
    for name in ("rune_altar", "terra_plate", "mana_infuser"):
        same(f"{name} panel texture size", png_size(os.path.join(TEXTURES, name + ".png")), (L.W + 2 * L.M, L.H + 2 * L.M))

    altar_menu = java_constants(os.path.join(MACHINE, "altar", "RuneAltarMenu.java"))
    altar = java_constants(os.path.join(MACHINE, "altar", "client", "RuneAltarScreen.java"))
    same("altar: reagent slot", (altar_menu["REAGENT_X"], altar_menu["REAGENT_Y"]), L.ALTAR.SLOT)
    same("altar: its middle, radius, orbit", (altar["ALTAR_X"], altar["ALTAR_Y"], altar["ALTAR_R"], altar["ORBIT_R"], altar["HEART_R"]),
         (L.ALTAR.CENTER[0], L.ALTAR.CENTER[1], L.ALTAR.R, L.ALTAR.ORBIT_R, L.ALTAR.HEART_R))
    same("altar: hollow", altar["RECESS_R"], int(L.ALTAR.RECESS_R))
    same("altar: gem", (altar["GEM_X"], altar["GEM_Y"]), L.ALTAR.GEM)
    same("altar: runes", altar["RUNES"], [tuple(p) for p in L.ALTAR.RUNES])
    same("altar: stones", altar["STONES"], [tuple(p) for p in L.ALTAR.STONES])
    same("altar: lit runes (sheet)", (altar["RUNE_U"], altar["RUNE_V"], altar["RUNE_SIZE"]),
         (sheet["RUNE_UV"][0], sheet["RUNE_UV"][1], sheet["RUNE_SIZE"]))
    same("altar: rune size", altar["RUNE_SIZE"], L.ALTAR.RUNE_SIZE)
    same("altar: halo (sheet)", (altar["HALO_U"], altar["HALO_V"], altar["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    plate = java_constants(os.path.join(MACHINE, "plate", "client", "TerraPlateScreen.java"))
    same("plate: its middle, radius, sockets' ring", (plate["PLATE_X"], plate["PLATE_Y"], plate["PLATE_R"], plate["SOCKET_R"], plate["HEART_R"]),
         (L.PLATE.CENTER[0], L.PLATE.CENTER[1], L.PLATE.R, L.PLATE.SOCKET_R, L.PLATE.HEART_R))
    same("plate: core", plate["CORE_R"], L.PLATE.CORE_R)
    same("plate: gem", (plate["GEM_X"], plate["GEM_Y"]), L.PLATE.GEM)
    same("plate: sockets", plate["SOCKETS"], [tuple(p) for p in L.PLATE.SOCKETS])
    same("plate: halo (sheet)", (plate["HALO_U"], plate["HALO_V"], plate["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))
    gui = java_source(os.path.join(HERE, "gen_gui.py"))
    for what, value in (("ring", plate["SUN_RING_R"]), ("rays' start", plate["RAY_FROM"]), ("rays' end", plate["RAY_TO"]),
                        ("tips' end", plate["TIP_TO"])):
        if not re.search(r"\b%s\b" % re.escape(repr(value)), gui):
            errors.append(f"plate: the sun's {what} ({value}) isn't in gen_gui.plate_field")

    infuser_menu = java_constants(os.path.join(MACHINE, "infuser", "ManaInfuserMenu.java"))
    infuser = java_constants(os.path.join(MACHINE, "infuser", "client", "ManaInfuserScreen.java"))
    same("infuser: catalyst slot", (infuser_menu["CATALYST_X"], infuser_menu["CATALYST_Y"]), L.POOL_.SLOT)
    same("infuser: the pool", (infuser["POOL_X"], infuser["POOL_Y"], infuser["SURFACE_RX"], infuser["SURFACE_RY"]),
         (L.POOL_.CENTER[0], L.POOL_.CENTER[1], L.POOL_.SURFACE_RX, L.POOL_.SURFACE_RY))
    same("infuser: the item", (infuser["ITEM_X"], infuser["ITEM_Y"], infuser["ITEM_RING_R"]),
         (L.POOL_.ITEM[0], L.POOL_.ITEM[1], L.POOL_.ITEM_RING_R))
    same("infuser: gem", (infuser["GEM_X"], infuser["GEM_Y"]), L.POOL_.GEM)
    same("infuser: heart's box", infuser["HEART_BOX"], L.POOL_.HEART_BOX)
    same("infuser: halo (sheet)", (infuser["HALO_U"], infuser["HALO_V"], infuser["HALO_SIZE"]),
         (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    for name in ("pure_daisy", "petal_apothecary", "petal_farm"):
        same(f"{name} panel texture size", png_size(os.path.join(TEXTURES, name + ".png")), (L.W + 2 * L.M, L.H + 2 * L.M))

    daisy = java_constants(os.path.join(MACHINE, "daisy", "client", "PureDaisyScreen.java"))
    same("daisy: its middle, ring, heart", (daisy["DAISY_X"], daisy["DAISY_Y"], daisy["RING_R"], daisy["HEART_R"]),
         (L.DAISY.CENTER[0], L.DAISY.CENTER[1], L.DAISY.RING_R, L.DAISY.HEART_R))
    same("daisy: gem", (daisy["GEM_X"], daisy["GEM_Y"]), L.DAISY.GEM)
    same("daisy: stones", daisy["CELLS"], [tuple(p) for p in L.DAISY.CELLS])
    same("daisy: halo (sheet)", (daisy["HALO_U"], daisy["HALO_V"], daisy["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    bowl_menu = java_constants(os.path.join(MACHINE, "apothecary", "PetalApothecaryMenu.java"))
    bowl = java_constants(os.path.join(MACHINE, "apothecary", "client", "PetalApothecaryScreen.java"))
    same("apothecary: seeds slot", (bowl_menu["REAGENT_X"], bowl_menu["REAGENT_Y"]), L.BOWL.SLOT)
    same("apothecary: the water", (bowl["BOWL_X"], bowl["BOWL_Y"], bowl["WATER_RX"], bowl["WATER_RY"]),
         (L.BOWL.CENTER[0], L.BOWL.CENTER[1], L.BOWL.WATER_RX, L.BOWL.WATER_RY))
    same("apothecary: the flower", (bowl["ITEM_X"], bowl["ITEM_Y"], bowl["ITEM_RING_R"]), (L.BOWL.ITEM[0], L.BOWL.ITEM[1], L.BOWL.ITEM_RING_R))
    same("apothecary: gem", (bowl["GEM_X"], bowl["GEM_Y"]), L.BOWL.GEM)
    same("apothecary: heart's box", bowl["HEART_BOX"], L.BOWL.HEART_BOX)
    same("apothecary: halo (sheet)", (bowl["HALO_U"], bowl["HALO_V"], bowl["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    farm_menu = java_constants(os.path.join(MACHINE, "farm", "PetalFarmMenu.java"))
    farm = java_constants(os.path.join(MACHINE, "farm", "client", "PetalFarmScreen.java"))
    same("farm: bone meal slot", (farm_menu["FERTILIZER_X"], farm_menu["FERTILIZER_Y"]), L.FARM.SLOT)
    same("farm: the planter", (farm["BED_X1"], farm["SOIL_Y"], farm["BED_X2"], farm["BED_Y2"]), L.FARM.BED)
    same("farm: the soil's top", farm["SOIL_TOP"], L.FARM.SOIL_TOP)
    same("farm: the sun's arc", (farm["SUN_X"], farm["SUN_Y"], farm["SUN_R"]), (L.FARM.SUN[0], L.FARM.SUN[1], L.FARM.SUN_R))
    same("farm: gem", (farm["GEM_X"], farm["GEM_Y"]), L.FARM.GEM)
    same("farm: heart's box", farm["HEART_BOX"], L.FARM.HEART_BOX)
    same("farm: halo (sheet)", (farm["HALO_U"], farm["HALO_V"], farm["HALO_SIZE"]), (sheet["HALO_UV"][0], sheet["HALO_UV"][1], sheet["HALO_SIZE"]))

    # JEI's click areas are the arrows
    jei = java_source(os.path.join(JAVA, "client", "JeiCompat.java"))
    m = re.search(r"void arrows\(.*?\{(.*?)\n    \}", jei, re.S)
    areas = [tuple(int(v) for v in a) for a in re.findall(r"addRecipeClickArea\(screen,\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+)", m.group(1))] if m else []
    same("JEI click areas", areas, [(ax, ay, L.ARROW_W, L.ARROW_H) for (ax, ay) in L.ARROWS])

    if errors:
        print("The machines' GUI code and textures disagree:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print("Machines' GUI layout: code and textures agree.")


if __name__ == "__main__":
    main()
