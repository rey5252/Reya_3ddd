"""Checks that the GUI code and the textures agree on where everything is.

    python3 tools/check_layout.py

The panel texture is drawn by tools/gen_gui.py, the slots and the moving parts are placed by
GreenhouseMenu.java and GreenhouseScreen.java; a slot a few pixels off its frame is easy to miss,
so CI runs this. Needs only the Python standard library.
"""
import ast
import os
import re
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reya", "managarden")
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "managarden", "textures", "gui")


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


def java_constants(path):
    """The class's `static final int A = 1, B = 2;` constants and int[][] tables."""
    with open(path) as f:
        src = f.read()
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
    menu = java_constants(os.path.join(JAVA, "GreenhouseMenu.java"))
    screen = java_constants(os.path.join(JAVA, "client", "GreenhouseScreen.java"))
    errors = []

    def same(what, code, texture):
        if code != texture:
            errors.append(f"{what}: code {code}, texture {texture}")

    same("panel size", (menu["WIDTH"], menu["HEIGHT"]), (gui["W"], gui["H"]))
    same("heart", (menu["HEART_X"], menu["HEART_Y"]), gui["HEART"])
    same("flower slots", menu["FLOWER_POS"], [tuple(p) for p in gui["FLOWERS"]])
    same("upgrade slots", [(menu["UPGRADE_X"], menu["UPGRADE_Y"] + i * menu["UPGRADE_STEP"]) for i in range(4)],
         [tuple(p) for p in gui["UPGRADES"]])
    same("charge slot", (menu["CHARGE_X"], menu["CHARGE_Y"]), gui["CHARGE"])
    same("inventory", (menu["INV_X"], menu["INV_Y"], menu["HOTBAR_Y"]), (gui["INV_SLOT_X"], gui["INV_Y"], gui["HOTBAR_Y"]))
    same("texture margin", screen["M"], gui["M"])
    same("orb radius", screen["ORB_R"], gui["ORB_R"])
    same("machine panel height", screen["MACHINE_H"], gui["MACHINE_H"])
    same("inventory panel", (screen["INV_PANEL_X1"], screen["INV_PANEL_X2"]), (gui["INV_X1"], gui["INV_X2"]))
    same("charge gauge", (screen["GAUGE_X1"], screen["GAUGE_Y1"], screen["GAUGE_X2"], screen["GAUGE_Y2"]), gui["GAUGE"])
    same("growth bar", (screen["BAR_X1"], screen["BAR_Y1"], screen["BAR_X2"], screen["BAR_Y2"]), gui["BAR"])
    same("buttons", [(screen["BUTTON_REDSTONE_X"], screen["BUTTON_Y"]), (screen["BUTTON_OUTPUT_X"], screen["BUTTON_Y"])],
         [tuple(b) for b in gui["BUTTONS"]])
    same("panel texture size", png_size(os.path.join(TEXTURES, "greenhouse.png")),
         (gui["W"] + 2 * gui["M"], gui["H"] + 2 * gui["M"]))
    same("widget sheet size", png_size(os.path.join(TEXTURES, "greenhouse_widgets.png")), (screen["WIDGETS_W"], screen["WIDGETS_H"]))
    same("bloom sheet size", png_size(os.path.join(TEXTURES, "greenhouse_bloom.png")),
         (screen["BLOOM_SIZE"] * screen["BLOOM_FRAMES"], screen["BLOOM_SIZE"]))

    mascot = java_constants(os.path.join(JAVA, "client", "Mascot.java"))
    keeper = python_constants(os.path.join(ROOT, "tools", "gen_mascot.py"))
    same("keeper sheet size", png_size(os.path.join(TEXTURES, "mascot.png")), (mascot["TEX_W"], mascot["TEX_H"]))
    same("keeper sheet", (mascot["TEX_W"], mascot["TEX_H"]), (keeper["SHEET_W"], keeper["SHEET_H"]))
    same("keeper's portrait size", (mascot["WIDTH"], mascot["HEIGHT"]), (keeper["PW"], keeper["PH"]))
    same("keeper eyes", tuple(mascot[k] for k in ("EYES_X", "EYES_Y", "EYES_W", "EYES_H", "EYES_U", "EYES_V")),
         tuple(keeper["EYES_BOX"]) + tuple(keeper["EYES_UV"]))
    same("keeper mouth", tuple(mascot[k] for k in ("MOUTH_X", "MOUTH_Y", "MOUTH_W", "MOUTH_H", "MOUTH_U", "MOUTH_V")),
         tuple(keeper["MOUTH_BOX"]) + tuple(keeper["MOUTH_UV"]))


    if errors:
        print("The GUI code and its textures disagree:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print("GUI layout: code and textures agree.")


if __name__ == "__main__":
    main()
