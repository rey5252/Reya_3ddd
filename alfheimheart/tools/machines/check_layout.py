"""Checks that the machines' GUI code and textures agree on where everything is.

    python3 tools/machines/check_layout.py

Every machine's layout lives in layouts.py: the Java code reads it from machine/MachineLayouts.java, which
layouts.py writes, and the panels are drawn on it (gen_v2.py). This makes sure the Java file is current, the
panels, gloss masks and widget sheets are their layouts' size, the sheets' shared pieces are where
machine/client/MachineScreen.java reads them, no two pieces of a sheet overlap and no two slots do, and every
machine with recipes in JEI gets its click areas from its layout. A slot a few pixels off its frame is easy to
miss, so CI runs this. Needs only the Python standard library.
"""
import os
import re
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import layouts as LS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reya", "alfheimheart")
MACHINE = os.path.join(JAVA, "machine")
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "alfheimheart", "textures", "gui")


def java_source(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def java_constants(path):
    """The class's `static final int A = 1, B = 2;` constants."""
    out = {}
    for decl in re.findall(r"static final int\s+([^;]*);", java_source(path)):
        for name, value in re.findall(r"(\w+)\s*=\s*(-?\d+)\b(?!\s*[-+*/.\[])", decl):
            out[name] = int(value)
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
    if java_source(LS.JAVA_OUT) != LS.java():
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
    margin = java_constants(os.path.join(MACHINE, "MachineLayout.java"))
    same("texture margin", margin["MARGIN"], LS.M)

    # every machine's textures are its layout's size, its sheet's pieces fit, its slots don't overlap
    for name, d in LS.LAYOUTS:
        key = d["key"]
        w, h, mh = d["size"]
        same(f"{key} panel texture size", png_size(os.path.join(TEXTURES, key + ".png")), (w + 2 * LS.M, h + 2 * LS.M))
        same(f"{key} gloss mask size", png_size(os.path.join(TEXTURES, key + "_gloss.png")), (w + 2 * LS.M, h + 2 * LS.M))
        same(f"{key} widget sheet size", png_size(os.path.join(TEXTURES, key + "_widgets.png")), (LS.SHEET_W, LS.SHEET_H))
        regions_fit(errors, f"{key} sheet", LS.SHARED_REGIONS + LS.MACHINES[key].SHEET, LS.SHEET_W, LS.SHEET_H)
        slots = d["inputs"] + d["outputs"] + d["special"]
        for i, (x1, y1) in enumerate(slots):
            if x1 - 1 < LS.FRAME or y1 - 1 < LS.FRAME or x1 + 17 > w - LS.FRAME or y1 + 17 > mh - LS.FRAME:
                errors.append(f"{key}: slot {i} at {(x1, y1)} is on or past the machine's frame")
            for (x2, y2) in slots[i + 1:]:
                if abs(x1 - x2) < 18 and abs(y1 - y2) < 18:
                    errors.append(f"{key}: slots at {(x1, y1)} and {(x2, y2)} overlap")

    # every machine with recipes in JEI has its click areas from its layout
    jei = java_source(os.path.join(JAVA, "client", "JeiCompat.java"))
    for name in ("RUNE_ALTAR", "TERRA_PLATE", "MANA_INFUSER", "PURE_DAISY", "PETAL_APOTHECARY", "ORECHID_MINE"):
        if "MachineLayouts." + name not in jei:
            errors.append(f"JEI: no click areas for {name} from its layout")
        if not dict(LS.LAYOUTS)[name]["click"]:
            errors.append(f"JEI: {name}'s layout has no click area")

    if errors:
        print("The machines' GUI code and textures disagree:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print("Machines' GUI layout: code and textures agree.")


if __name__ == "__main__":
    main()
