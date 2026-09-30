"""Checks that the GUI code and the textures agree on where everything is.

    python3 tools/check_layout.py

The panel texture is drawn by tools/gen_gui.py and the widget sheet by tools/gen_widgets.py; the slots and
the moving parts are placed by PortalMenu.java and PortalScreen.java (and JEI's click areas by
JeiCompat.java). A slot a few pixels off its frame is easy to miss, so CI runs this. Needs only the Python
standard library.
"""
import ast
import os
import re
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reya", "elvenportal")
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "elvenportal", "textures", "gui")


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
    """The class's `static final int A = 1, B = 2;` constants and int[][] tables."""
    src = java_source(path)
    out = {}
    for decl in re.findall(r"static final int\s+([^;]*);", src):
        for name, value in re.findall(r"(\w+)\s*=\s*(-?\d+)\b(?!\s*[-+*/])", decl):
            out[name] = int(value)
    for name, body in re.findall(r"static final int\[\]\[\]\s+(\w+)\s*=\s*\{(.*?)\};", src, re.S):
        out[name] = [tuple(int(v) for v in re.findall(r"-?\d+", pair)) for pair in re.findall(r"\{([^{}]*)\}", body)]
    return out


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


def main():
    gui = python_constants(os.path.join(ROOT, "tools", "gen_gui.py"))
    sheet = python_constants(os.path.join(ROOT, "tools", "gen_widgets.py"))
    menu = java_constants(os.path.join(JAVA, "PortalMenu.java"))
    screen = java_constants(os.path.join(JAVA, "client", "PortalScreen.java"))
    errors = []

    def same(what, code, texture):
        if code != texture:
            errors.append(f"{what}: code {code}, texture {texture}")

    # the menu's slots on the panel's frames
    same("panel size", (menu["WIDTH"], menu["HEIGHT"]), (gui["W"], gui["H"]))
    same("input slots", (menu["INPUT_X"], menu["GRID_Y"]), gui["INPUT"])
    same("output slots", (menu["OUTPUT_X"], menu["GRID_Y"]), gui["OUTPUT"])
    same("inventory", (menu["INV_X"], menu["INV_Y"], menu["HOTBAR_Y"]), (gui["INV_SLOT_X"], gui["INV_Y"], gui["HOTBAR_Y"]))

    # the screen's moving parts on the panel
    same("texture margin", screen["M"], gui["M"])
    same("machine panel height", screen["MACHINE_H"], gui["MACHINE_H"])
    same("inventory panel", (screen["INV_PANEL_X1"], screen["INV_PANEL_X2"]), (gui["INV_X1"], gui["INV_X2"]))
    same("portal (swirl)", (screen["SWIRL_X"], screen["SWIRL_Y"], screen["SWIRL_W"], screen["SWIRL_H"]), gui["SWIRL"])
    same("gate", (screen["GATE_X1"], screen["GATE_Y1"], screen["GATE_X2"], screen["GATE_Y2"]), gui["GATE_BOX"])
    same("gate's middle is the portal's", gui["GATE"], (gui["SWIRL"][0] + gui["SWIRL"][2] // 2, gui["SWIRL"][1] + gui["SWIRL"][3] // 2))
    same("the gem", (screen["GEM_X"], screen["GEM_Y"]), gui["GEM"])
    same("runes", screen["RUNES"], [tuple(p) for p in gui["RUNES"]])
    same("crystals", screen["CRYSTALS"], [tuple(p) for p in gui["CRYSTALS"]])
    same("arrows", [(screen["ARROW_IN_X"], screen["ARROW_Y"]), (screen["ARROW_OUT_X"], screen["ARROW_Y"])],
         [tuple(a) for a in gui["ARROWS"]])
    same("arrow size", (screen["ARROW_W"], screen["ARROW_H"]), (gui["ARROW_W"], gui["ARROW_H"]))
    same("mana bar", (screen["BAR_X1"], screen["BAR_Y1"], screen["BAR_X2"], screen["BAR_Y2"]), gui["BAR"])
    same("buttons", [(screen["BUTTON_REDSTONE_X"], screen["BUTTON_Y"])], [tuple(b) for b in gui["BUTTONS"]])
    same("pool light", (screen["POOL_X"], screen["POOL_Y"]), gui["POOL"])
    same("frame vein row", screen["VEIN"], gui["VEIN"])
    same("gold lights", screen["LIGHTS"], [tuple(p) for p in gui["LIGHTS"]])
    # the traded items fly between the middles of the slot grids and the portal's
    same("flights: input grid's middle", (screen["INPUT_MID_X"], screen["GRID_MID_Y"]), (gui["INPUT"][0] + 26, gui["INPUT"][1] + 26))
    same("flights: output grid's middle", (screen["OUTPUT_MID_X"], screen["GRID_MID_Y"]), (gui["OUTPUT"][0] + 26, gui["OUTPUT"][1] + 26))
    x, y, w, h = gui["SWIRL"]
    same("flights: the portal's middle", (screen["PORTAL_X"], screen["PORTAL_Y"]), (x + w // 2, y + h // 2))

    # the screen's pieces on the widget sheet
    same("widget sheet size (generator)", (screen["WIDGETS_W"], screen["WIDGETS_H"]), (sheet["SHEET_W"], sheet["SHEET_H"]))
    same("button icons", screen["ICON_V"], sheet["ICON_V"])
    same("lit runes", (screen["RUNE_U"], screen["RUNE_V"], screen["RUNE_SIZE"]), (sheet["RUNE_UV"][0], sheet["RUNE_UV"][1], sheet["RUNE_SIZE"]))
    same("crystal sprites", (screen["CRYSTAL_U"], screen["CRYSTAL_V"], screen["CRYSTAL_W"], screen["CRYSTAL_H"]),
         (sheet["CRYSTAL_UV"][0], sheet["CRYSTAL_UV"][1], sheet["CRYSTAL_W"], sheet["CRYSTAL_H"]))
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
    same("title scroll", (screen["SCROLL_H"], screen["SCROLL_CAP"], screen["SCROLL_V"], screen["SCROLL_TILE_U"], screen["SCROLL_TILE_W"]),
         (sheet["SCROLL_H"], sheet["SCROLL_CAP"], sheet["SCROLL_V"], sheet["SCROLL_TILE_U"], sheet["SCROLL_TILE_W"]))
    same("swirl frames", (screen["SWIRL_W"], screen["SWIRL_H"], screen["SWIRL_FRAMES"]),
         (sheet["SWIRL_W"], sheet["SWIRL_H"], sheet["SWIRL_FRAMES"]))

    # the textures' sizes
    same("panel texture size", png_size(os.path.join(TEXTURES, "elven_portal.png")),
         (gui["W"] + 2 * gui["M"], gui["H"] + 2 * gui["M"]))
    same("widget sheet size", png_size(os.path.join(TEXTURES, "elven_portal_widgets.png")), (screen["WIDGETS_W"], screen["WIDGETS_H"]))
    same("swirl sheet size", png_size(os.path.join(TEXTURES, "portal_swirl.png")),
         (screen["SWIRL_W"] * screen["SWIRL_FRAMES"], screen["SWIRL_H"]))

    # no piece of the widget sheet may reach into another's pixels (in the game that shows up as stray dots)
    regions = sheet["SHEET_REGIONS"]
    for i, (n1, x1, y1, w1, h1) in enumerate(regions):
        if x1 < 0 or y1 < 0 or x1 + w1 > sheet["SHEET_W"] or y1 + h1 > sheet["SHEET_H"]:
            errors.append(f"widget sheet: {n1} is outside the sheet")
        for (n2, x2, y2, w2, h2) in regions[i + 1:]:
            if x1 < x2 + w2 and x2 < x1 + w1 and y1 < y2 + h2 and y2 < y1 + h1:
                errors.append(f"widget sheet: {n1} and {n2} overlap")
    # the close button sits on the panel's top-right corner, inside the texture's margin
    if not (-gui["M"] <= screen["CLOSE_Y"] and screen["CLOSE_X"] + screen["CLOSE_SIZE"] <= gui["W"] + gui["M"]):
        errors.append("the close button sticks out of the panel texture's margin")

    # the block's renderer and the block's textures (tools/gen_block.py)
    block = python_constants(os.path.join(ROOT, "tools", "gen_block.py"))
    renderer = java_constants(os.path.join(JAVA, "client", "PortalRenderer.java"))
    vine_lights = [(x, y) for y, row in enumerate(block["VINE"]) for x, ch in enumerate(row) if ch == "y"]
    same("the vine's gold lights (renderer)", renderer["LIGHTS"], vine_lights)
    same("the swirl's frames (renderer)", renderer["FRAMES"], block["FRAMES"])
    entity = os.path.join(ROOT, "src", "main", "resources", "assets", "elvenportal", "textures", "entity", "elven_gate")
    same("the swirl's frames (texture)", png_size(os.path.join(entity, "swirl.png")),
         (block["SWIRL_SIZE"], block["SWIRL_SIZE"] * block["FRAMES"]))

    # JEI's click areas are the arrows
    jei = java_source(os.path.join(JAVA, "client", "JeiCompat.java"))
    areas = [tuple(int(v) for v in m) for m in re.findall(r"addRecipeClickArea\(PortalScreen\.class,\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+)", jei)]
    same("JEI click areas", areas, [(ax, ay, gui["ARROW_W"], gui["ARROW_H"]) for (ax, ay) in gui["ARROWS"]])

    if errors:
        print("The GUI code and its textures disagree:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print("GUI layout: code and textures agree.")


if __name__ == "__main__":
    main()
