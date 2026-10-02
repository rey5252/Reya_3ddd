"""Checks that the code and the art agree, before anything is built (CI runs it).

    python3 tools/check.py

- gui/Layouts.java is what layout.py makes now;
- nothing in the screen's layout overlaps what it shouldn't, and the widget sheet's pieces fit and don't overlap;
- every texture a model, the renderers or the screen name is there, at the size it is drawn at (animated ones in frames);
- the pylon's texture regions in gen_textures.py are the renderer's;
- every translation key the code uses is in all three languages, and the three have the same keys.
"""
import ast
import json
import os
import re
import sys

import layout as L

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reya", "singularityfusion")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "singularityfusion")
problems = []


def problem(msg):
    problems.append(msg)


def overlap(a, b):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def java_sources():
    for dirpath, _, files in os.walk(JAVA):
        for name in files:
            if name.endswith(".java"):
                yield os.path.join(dirpath, name), read(os.path.join(dirpath, name))


def check_layouts():
    if read(L.JAVA_OUT) != L.java():
        problem("gui/Layouts.java is out of date: run tools/layout.py")
    boxes = [("title", (L.TITLE[0], L.TITLE[1], L.TITLE[2] - L.TITLE[0], L.TITLE[3] - L.TITLE[1])),
             ("viewport", (L.VIEW[0] - L.VIEW_R - 4, L.VIEW[1] - L.VIEW_R - 4, 2 * L.VIEW_R + 8, 2 * L.VIEW_R + 8)),
             ("catalyst", (L.CATALYST[0] - 1, L.CATALYST[1] - 1, 18, 18)), ("start", (L.START[0], L.START[1], 18, 18)),
             ("output", (L.OUTPUT[0] - 1, L.OUTPUT[1] - 1, 18, 18)),
             ("energy", (L.ENERGY[0] - 1, L.ENERGY[1] - 1, L.ENERGY[2] + 2, L.ENERGY[3] + 2)),
             ("progress", (L.PROGRESS[0] - 1, L.PROGRESS[1] - 1, L.PROGRESS[2] + 2, L.PROGRESS[3] + 2)),
             ("status", (50, 128, 156, 25))]
    for i, (x, y) in enumerate(L.PYLONS_LEFT + L.PYLONS_RIGHT):
        boxes.append(("pylon %d" % i, (x - 1, y - 1, 18, 18)))
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            if overlap(boxes[i][1], boxes[j][1]):
                problem("the screen's %s and %s overlap" % (boxes[i][0], boxes[j][0]))
    for name, (x, y, w, h) in boxes:
        if x < L.FRAME and name != "title" or x + w > L.W - L.FRAME or y + h > L.MH - L.FRAME:
            problem("the screen's %s runs into the frame" % name)
    regions = L.Sheet.REGIONS
    for name, x, y, w, h in regions:
        if x + w > L.W or y + h > L.Sheet.TEX_H or y < L.H:
            problem("the sheet's %s doesn't fit under the panel" % name)
    for i in range(len(regions)):
        for j in range(i + 1, len(regions)):
            if overlap(regions[i][1:], regions[j][1:]):
                problem("the sheet's %s and %s overlap" % (regions[i][0], regions[j][0]))


def png_size(path):
    """A PNG's width and height, from its header (no image library needed)."""
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return int.from_bytes(head[16:20], "big"), int.from_bytes(head[20:24], "big")


def texture(rel, size=None, frames=False):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    if not os.path.exists(path):
        problem("missing texture " + rel)
        return
    dims = png_size(path)
    if dims is None:
        problem("%s isn't a PNG" % rel)
        return
    w, h = dims
    if size and (w, h) != size and not (frames and w == size[0] and h % size[1] == 0):
        problem("%s is %dx%d, not %dx%d" % (rel, w, h, size[0], size[1]))
    if frames and h > w and h % w == 0 and not os.path.exists(path + ".mcmeta"):
        problem("%s has frames but no .mcmeta" % rel)


def check_textures():
    models = os.path.join(ASSETS, "models")
    for dirpath, _, files in os.walk(models):
        for name in files:
            data = json.loads(read(os.path.join(dirpath, name)))
            for ref in data.get("textures", {}).values():
                if ref.startswith("#") or not ref.startswith("singularityfusion:"):
                    continue
                rel = ref.split(":", 1)[1]
                texture(rel, (32, 32), frames=True)
    for path, src in java_sources():
        for kind, name in re.findall(r'Fx\.(effect|entity)\("([a-z_]+)"\)', src):
            texture(("effect/" if kind == "effect" else "entity/") + name)
    texture("entity/pylon", (64, 64))
    texture("entity/pylon_glow", (64, 64))
    texture("gui/fusion_core", (L.W, L.Sheet.TEX_H))
    if not os.path.exists(os.path.join(ROOT, "src", "main", "resources", "singularityfusion_logo.png")):
        problem("missing the logo")


def check_pylon_regions():
    src = read(os.path.join(JAVA, "client", "GravitonPylonRenderer.java"))
    m = re.search(r"REGIONS = \{(.*?)\};", src, re.S)
    java_regions = [tuple(int(v) for v in r.split(",")) for r in re.findall(r"\{(\d+, \d+, \d+, \d+)\}", m.group(1))] if m else []
    tree = ast.parse(read(os.path.join(HERE, "gen_textures.py")))
    py_regions = []
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "PYLON_REGIONS" for t in node.targets):
            py_regions = list(ast.literal_eval(node.value).values())
    if java_regions != py_regions:
        problem("the pylon's texture regions differ: renderer %s, gen_textures %s" % (java_regions, py_regions))


def check_languages():
    langs = {}
    for code in ("en_us", "uk_ua", "ru_ru"):
        path = os.path.join(ASSETS, "lang", code + ".json")
        if not os.path.exists(path):
            problem("missing language " + code)
            continue
        langs[code] = json.loads(read(path))
    if not langs:
        return
    keys = set(langs.get("en_us", {}))
    for code, table in langs.items():
        if set(table) != keys:
            problem("%s has different keys: missing %s, extra %s" % (code, sorted(keys - set(table)), sorted(set(table) - keys)))
    used = set()
    for path, src in java_sources():
        used.update(re.findall(r'"((?:gui|jei)\.singularityfusion\.[a-z_.]+[a-z])"', src))
    status_src = read(os.path.join(JAVA, "block", "FusionStatus.java"))
    for name in re.findall(r"^\s+([A-Z_]+)[,;]", status_src, re.M):
        used.add("gui.singularityfusion.status." + name.lower())
    for k in sorted(used):
        if k.endswith(".status"):
            continue
        if k not in keys:
            problem("translation key %s is used but not in the languages" % k)
    for kind, folder in (("block", "blockstates"), ("item", "models/item")):
        for name in os.listdir(os.path.join(ASSETS, folder)):
            n = name[:-5]
            key = "%s.singularityfusion.%s" % ("block" if os.path.exists(os.path.join(ASSETS, "blockstates", name)) else "item", n)
            if key not in keys:
                problem("%s has no name (%s)" % (n, key))


def main():
    check_layouts()
    check_textures()
    check_pylon_regions()
    check_languages()
    if problems:
        for p in problems:
            print("PROBLEM: " + p)
        sys.exit(1)
    print("all good")


if __name__ == "__main__":
    main()
