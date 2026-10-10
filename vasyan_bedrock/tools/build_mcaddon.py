"""Checks the add-on and packs it into dist/vasyan.mcaddon (a zip with both packs inside).

Run from anywhere:  python3 vasyan_bedrock/tools/build_mcaddon.py
Checks: every .json parses, manifest uuids are unique and the BP depends on the RP, every chat line the
script can send (vasyan.<key>.1..n from LINES, plus literal translate keys) exists in every .lang file.
"""
import json, os, re, sys, zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PACKS = {"behavior_pack": "Vasyan_BP", "resource_pack": "Vasyan_RP"}
OUT = os.path.join(ROOT, "dist", "vasyan.mcaddon")


def fail(msg):
    print("ERROR:", msg)
    sys.exit(1)


def check_json():
    manifests = {}
    for pack in PACKS:
        for dirpath, _, files in os.walk(os.path.join(ROOT, pack)):
            for f in files:
                if f.endswith(".json"):
                    path = os.path.join(dirpath, f)
                    try:
                        data = json.load(open(path, encoding="utf-8"))
                    except ValueError as e:
                        fail(f"{os.path.relpath(path, ROOT)}: {e}")
                    if f == "manifest.json":
                        manifests[pack] = data
    uuids = []
    for m in manifests.values():
        uuids.append(m["header"]["uuid"])
        uuids += [mod["uuid"] for mod in m["modules"]]
    if len(uuids) != len(set(uuids)):
        fail("manifest uuids are not unique")
    rp = manifests["resource_pack"]["header"]["uuid"]
    if not any(d.get("uuid") == rp for d in manifests["behavior_pack"].get("dependencies", [])):
        fail("behavior pack does not depend on the resource pack")


def check_lang():
    script = open(os.path.join(ROOT, "behavior_pack/scripts/main.js"), encoding="utf-8").read()
    lines = re.search(r"const LINES = \{(.*?)\};", script, re.S)
    keys = set()
    for name, n in re.findall(r"(\w+): (\d+)", lines.group(1)):
        keys.update(f"vasyan.{name}.{i}" for i in range(1, int(n) + 1))
    keys.update(re.findall(r'translate: "([^"]+)"', script))
    keys.update(k for k in re.findall(r'"(vasyan\.toggle\.\w+)"', script))
    keys.update(["entity.vasyan:vasyan.name", "item.vasyan:ai_chip.name", "action.interact.vasyan",
                 "item.spawn_egg.entity.vasyan:vasyan.name"])
    texts = os.path.join(ROOT, "resource_pack/texts")
    for lang in json.load(open(os.path.join(texts, "languages.json"), encoding="utf-8")):
        have = set()
        for line in open(os.path.join(texts, lang + ".lang"), encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("##") and "=" in line:
                have.add(line.split("=", 1)[0])
        missing = sorted(keys - have)
        if missing:
            fail(f"{lang}.lang is missing: {', '.join(missing)}")
    print(f"lang ok: {len(keys)} keys in every language")


def pack():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        for pack, name in PACKS.items():
            base = os.path.join(ROOT, pack)
            for dirpath, _, files in os.walk(base):
                for f in sorted(files):
                    path = os.path.join(dirpath, f)
                    z.write(path, name + "/" + os.path.relpath(path, base).replace(os.sep, "/"))
    print("wrote", os.path.relpath(OUT, os.getcwd()))


if __name__ == "__main__":
    check_json()
    check_lang()
    pack()
