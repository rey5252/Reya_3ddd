"""Checks the add-on and packs it into dist/bobik_klod.mcaddon (a zip with both packs inside).

Run from anywhere:  python3 vasyan_bedrock/tools/build_mcaddon.py
Checks: every .json parses, manifest uuids are unique and the BP depends on the RP, every .lang file has
the names, every line in tools/lines is voiced and every line key the script uses exists.
"""
import json, os, re, sys, time, zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PACKS = {"behavior_pack": "Bobik_Klod_BP", "resource_pack": "Bobik_Klod_RP"}
OUT = os.path.join(ROOT, "dist", "bobik_klod.mcaddon")


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
    keys = []
    for ent in ("vasyan:vasyan", "vasyan:klod"):
        keys += [f"entity.{ent}.name", f"item.spawn_egg.entity.{ent}.name"]
    keys += ["item.vasyan:ai_chip.name", "item.vasyan:klod_chip.name", "action.interact.vasyan"]
    texts = os.path.join(ROOT, "resource_pack/texts")
    for lang in json.load(open(os.path.join(texts, "languages.json"), encoding="utf-8")):
        have = set()
        for line in open(os.path.join(texts, lang + ".lang"), encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("##") and "=" in line:
                have.add(line.split("=", 1)[0])
        missing = sorted(set(keys) - have)
        if missing:
            fail(f"{lang}.lang is missing: {', '.join(missing)}")
    print(f"lang ok: {len(keys)} names in every language")


def check_voice():
    """Every line has its sound, and every line key the script asks for exists."""
    defs = json.load(open(os.path.join(ROOT, "resource_pack/sounds/sound_definitions.json"), encoding="utf-8"))
    defs = defs["sound_definitions"]
    for sid, d in defs.items():
        for snd in d["sounds"]:
            if not os.path.exists(os.path.join(ROOT, "resource_pack", snd["name"] + ".ogg")):
                fail(f"sound {sid}: missing file {snd['name']}.ogg")
    lines_dir = os.path.join(ROOT, "tools/lines")
    keys_by_lang = {}
    for f in sorted(os.listdir(lines_dir)):
        ch, lang = f[:-5].rsplit("_", 1)
        d = json.load(open(os.path.join(lines_dir, f), encoding="utf-8"))
        for s in ("normal", "mat"):
            for key, lines in d[s].items():
                keys_by_lang.setdefault(lang, set()).add(key)
                for i in range(1, len(lines) + 1):
                    if f"{ch}.{lang}.{s}.{key}.{i}" not in defs:
                        fail(f"{f}: {s}.{key}.{i} is not voiced, run tools/gen_voice.py")
    script = open(os.path.join(ROOT, "behavior_pack/scripts/main.js"), encoding="utf-8").read()
    # the key argument of say(player, who, KEY, ...), react(player, KEY, ...) and ask(KEY)
    used = set()
    for arg in re.findall(r'say\(\w+, [\w.?() ]+?, ([^,{]+?)(?:, \{|\))', script):
        used.update(re.findall(r'"(\w+)"', arg))
    used.update(re.findall(r'react\(\w+, "(\w+)"', script))
    used.update(re.findall(r'ask\("(\w+)"', script))
    used.update(re.findall(r'key: "(\w+)"', script))  # Bobik's pranks
    used.update(re.findall(r'return "(\w+)";', script))  # Klod's useful things
    for pair in re.findall(r'say\(\w+, \w+, [\w.()!]+ \? "(\w+)" : "(\w+)"', script):
        used.update(pair)
    for lang, have in keys_by_lang.items():
        missing = sorted(k for k in used if k not in have)
        if missing:
            fail(f"lines for {lang} are missing keys the script uses: {', '.join(missing)}")
    print(f"voice ok: {len(defs)} voiced lines, {len(used)} line keys used by the script")


def stamped_manifests():
    """Every build gets a new, higher patch version (minutes since 2026), so Minecraft always takes it as an
    update. With two different packs under the same uuid and version, the import fails as a duplicate."""
    build = int((time.time() - 1767225600) // 60)
    out = {}
    for pack in PACKS:
        m = json.load(open(os.path.join(ROOT, pack, "manifest.json"), encoding="utf-8"))
        version = m["header"]["version"][:2] + [build]
        m["header"]["version"] = version
        for mod in m["modules"]:
            mod["version"] = version
        out[pack] = m
    rp_uuid = out["resource_pack"]["header"]["uuid"]
    for dep in out["behavior_pack"].get("dependencies", []):
        if dep.get("uuid") == rp_uuid:
            dep["version"] = out["resource_pack"]["header"]["version"]
    print("version", ".".join(map(str, out["behavior_pack"]["header"]["version"])))
    return out


def pack():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    manifests = stamped_manifests()
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        for pack, name in PACKS.items():
            base = os.path.join(ROOT, pack)
            for dirpath, _, files in os.walk(base):
                for f in sorted(files):
                    path = os.path.join(dirpath, f)
                    rel = os.path.relpath(path, base).replace(os.sep, "/")
                    if rel == "manifest.json":
                        z.writestr(name + "/" + rel, json.dumps(manifests[pack], ensure_ascii=False, indent=2))
                    else:
                        z.write(path, name + "/" + rel)
    print("wrote", os.path.relpath(OUT, os.getcwd()))


if __name__ == "__main__":
    check_json()
    check_lang()
    check_voice()
    pack()
