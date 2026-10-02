"""Checks that the mod's code, textures, models and languages agree.

    python3 tools/check.py

The Java layout constants must be the ones layout.py writes; no two slots or widget sheet pieces overlap, and
slots sit inside their panels; every item has a model and a texture; the three languages have the same keys,
and every item a name. Needs only the Python standard library (CI runs it).
"""
import glob
import json
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import layout as L  # noqa: E402

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "boundlessrouters")
MODULES = ["sender", "puller", "distributor", "dropper", "flinger", "placer", "breaker", "vacuum", "void", "player", "detector",
           "extruder", "activator"]
UPGRADES = ["speed", "stack", "range", "range_2", "range_3", "infinite_range", "muffler"]


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


def overlaps(a, b):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah


def main():
    errors = []

    # the Java layout is layout.py's
    with open(L.JAVA_OUT, encoding="utf-8") as f:
        if f.read() != L.java():
            errors.append("Layouts.java isn't current: run python3 tools/layout.py")

    # textures are their sizes
    for name, size in (("router", (256, 256)), ("module", (256, 256)), ("widgets", (L.Sheet.W, L.Sheet.H))):
        path = os.path.join(ASSETS, "textures", "gui", name + ".png")
        if not os.path.exists(path):
            errors.append("missing texture gui/%s.png" % name)
        elif png_size(path) != size:
            errors.append("gui/%s.png is %s, not %s" % (name, png_size(path), size))
    regions = L.Sheet.REGIONS
    for i, (n1, *a) in enumerate(regions):
        if a[0] + a[2] > L.Sheet.W or a[1] + a[3] > L.Sheet.H:
            errors.append("widget sheet: %s is off the sheet" % n1)
        for (n2, *b) in regions[i + 1:]:
            if overlaps(a, b):
                errors.append("widget sheet: %s and %s overlap" % (n1, n2))

    # slots don't overlap, and sit inside their panel's machine area or inventory
    def check_slots(what, slots, mh):
        for i, (x, y) in enumerate(slots):
            r = (x - 1, y - 1, 18, 18)
            if x - 1 < L.FRAME or x + 17 > L.W - L.FRAME:
                errors.append("%s: slot at %s is on the frame" % (what, (x, y)))
            for (x2, y2) in slots[i + 1:]:
                if overlaps(r, (x2 - 1, y2 - 1, 18, 18)):
                    errors.append("%s: slots at %s and %s overlap" % (what, (x, y), (x2, y2)))

    def inventory(cls):
        return [(cls.INV_X + c * 18, cls.INV_Y + r * 18) for r in range(3) for c in range(9)] + \
               [(cls.INV_X + c * 18, cls.HOTBAR_Y) for c in range(9)]

    R, M = L.Router, L.Module
    check_slots("router", [R.BUFFER] + R.MODULES + R.UPGRADES + inventory(R), R.MH)
    check_slots("module", M.FILTER + inventory(M), M.MH)
    for (x, y) in R.MODULES + R.UPGRADES + [R.BUFFER]:
        if y + 17 > R.MH - L.FRAME:
            errors.append("router: slot at %s is under the machine area" % ((x, y),))
    for (gx, gy) in R.GEARS:
        if gy + R.GEAR > R.MH - L.FRAME:
            errors.append("router: a gear at %s is on the frame" % ((gx, gy),))
    controls = [(x, y, M.DIR_CELL, M.DIR_CELL) for (x, y) in M.DIRS.values()] + \
               [(x, y, M.TOGGLE, M.TOGGLE) for (x, y) in M.TOGGLES] + [(x - 1, y - 1, 18, 18) for (x, y) in M.FILTER]
    for i, a in enumerate(controls):
        for b in controls[i + 1:]:
            if overlaps(a, b):
                errors.append("module screen: %s and %s overlap" % (a, b))

    # every item has a model and a texture
    items = ["blank_module", "blank_upgrade"] + [m + "_module" for m in MODULES] + [u + "_upgrade" for u in UPGRADES]
    for n in items:
        if not os.path.exists(os.path.join(ASSETS, "models", "item", n + ".json")):
            errors.append("no item model for " + n)
        if not os.path.exists(os.path.join(ASSETS, "textures", "item", n + ".png")):
            errors.append("no texture for " + n)
    for n in ("router_side", "router_top", "router_bottom", "router_front", "router_front_active"):
        if not os.path.exists(os.path.join(ASSETS, "textures", "block", n + ".png")):
            errors.append("no block texture " + n)

    # the languages agree, and every item has a name
    langs = {}
    for path in glob.glob(os.path.join(ASSETS, "lang", "*.json")):
        with open(path, encoding="utf-8") as f:
            langs[os.path.basename(path)] = json.load(f)
    keys = None
    for name, data in sorted(langs.items()):
        if keys is None:
            keys = set(data)
        elif set(data) != keys:
            errors.append("%s's keys differ from the others': %s" % (name, sorted(set(data) ^ keys)[:10]))
    for n in items:
        if keys is not None and "item.boundlessrouters." + n not in keys:
            errors.append("no name for item " + n)

    if errors:
        print("The mod's code and assets disagree:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print("Layouts, textures, models and languages agree.")


if __name__ == "__main__":
    main()
