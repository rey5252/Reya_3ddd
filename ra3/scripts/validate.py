#!/usr/bin/env python3
"""Перевірка мода FortressTowers (лише стандартна бібліотека, запуск з будь-якої теки).

    python validate.py                 # мод = ../FortressTowers відносно скрипта
    python validate.py D:\\path\\FortressTowers

Перевіряє:
  (a) кожен .xml у FortressTowers/data парситься;
  (b) mod.xml підключає кожен Towers/** і Logic/** .xml, і кожна ціль Include існує;
  (c) усі 9 TowerId зі SPEC є як GameObject id, плюс 3 японські *Nanocore;
  (d) кожна мітка NAME:/DESC:/TYPE: (і TOOLTIP:) з XML є в якомусь .str; жодна мітка не визначена двічі;
  (e) кожна команда Command_Construct* існує, а її <Object> — існуючий GameObject;
  (f) кількість коментарів VERIFY по файлах.
Код виходу 1, якщо є ПОМИЛКИ (попередження допускаються).
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

NS = "uri:ea.com:eala:asset"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_MOD = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "FortressTowers"))

# TowerId -> (папка фракції, Side) — зі SPEC.md
TOWERS = [
    ("AlliedPrismSpire", "Allies"),
    ("AlliedCryoBastion", "Allies"),
    ("AlliedAegisSpire", "Allies"),
    ("SovietTeslaObelisk", "Soviet"),
    ("SovietFlakNest", "Soviet"),
    ("SovietInfernoBunker", "Soviet"),
    ("SovietTsarCannon", "Soviet"),
    ("SovietBigBertha", "Soviet"),
    ("JapanTenguSpire", "Japan"),
    ("JapanWaveCitadel", "Japan"),
    ("JapanKitsuneShrine", "Japan"),
]
TOWER_IDS = [t for t, _ in TOWERS]
NANOCORES = [t + "Nanocore" for t, s in TOWERS if s == "Japan"]
UPGRADE_ID = "Upgrade_FortressReinforcedPlating"

STRICT_PREFIXES = ("NAME", "DESC", "TYPE")   # відсутня мітка мод-об'єкта = помилка
SOFT_PREFIXES = ("TOOLTIP",)                 # відсутня мітка = попередження
LABEL_REF_RE = re.compile(r"\b(NAME|DESC|TYPE|TOOLTIP):([A-Za-z0-9_.\-]+)")
STR_LABEL_RE = re.compile(r'^([A-Za-z0-9_]+:[^\s"]+)\s*$')
COMMENT_RE = re.compile(r"<!--(.*?)-->", re.S)
CONSTRUCT_RE = re.compile(r"\bCommand_Construct[A-Za-z0-9_]+")


class Report:
    def __init__(self):
        self.errors = []
        self.warnings = []
        self.sections = []

    def section(self, title):
        self.sections.append([title, [], 0, 0])

    def _add(self, kind, msg):
        sec = self.sections[-1]
        sec[1].append((kind, msg))
        if kind == "ERR":
            self.errors.append(msg)
            sec[2] += 1
        elif kind == "WARN":
            self.warnings.append(msg)
            sec[3] += 1

    def err(self, msg):
        self._add("ERR", msg)

    def warn(self, msg):
        self._add("WARN", msg)

    def info(self, msg):
        self._add("INFO", msg)

    def ok(self, msg):
        self._add("OK", msg)

    def print(self):
        tags = {"ERR": "[ПОМИЛКА]", "WARN": "[УВАГА]  ", "INFO": "         ", "OK": "[OK]     "}
        for title, items, ne, nw in self.sections:
            status = "ПОМИЛКИ: %d" % ne if ne else ("попереджень: %d" % nw if nw else "OK")
            print("")
            print("=== %s  — %s" % (title, status))
            for kind, msg in items:
                print("  %s %s" % (tags[kind], msg))
        print("")
        print("-" * 64)
        print("ПІДСУМОК: помилок %d, попереджень %d" % (len(self.errors), len(self.warnings)))
        print("РЕЗУЛЬТАТ: %s" % ("НЕ ПРОЙДЕНО" if self.errors else "ПРОЙДЕНО"))


def local(tag):
    if not isinstance(tag, str):
        return ""
    return tag.split("}", 1)[1] if tag.startswith("{") else tag


def rel(path, base):
    return os.path.relpath(path, base).replace(os.sep, "/")


def read_text(path):
    with open(path, "rb") as fh:
        raw = fh.read()
    for enc in ("utf-8-sig", "cp1251", "latin-1"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", "replace")


def parse_str_file(path, name, rep):
    """Повертає список (мітка, рядок файлу)."""
    labels = []
    in_block = None
    for n, raw in enumerate(read_text(path).replace("\r\n", "\n").split("\n"), 1):
        line = raw.strip()
        if not line or line.startswith("//") or line.startswith(";"):
            continue
        if in_block is None:
            m = STR_LABEL_RE.match(line)
            if m:
                in_block = (m.group(1), n)
                labels.append(in_block)
            else:
                rep.warn("%s:%d: очікувалась мітка, знайдено: %s" % (name, n, line[:60]))
        elif line.upper() == "END":
            in_block = None
        elif not line.startswith('"') and STR_LABEL_RE.match(line):
            rep.err("%s:%d: мітка %s не закрита END" % (name, in_block[1], in_block[0]))
            in_block = (STR_LABEL_RE.match(line).group(1), n)
            labels.append(in_block)
    if in_block is not None:
        rep.err("%s:%d: мітка %s не закрита END" % (name, in_block[1], in_block[0]))
    return labels


def resolve_include(source, including_file, data_dir):
    """Повертає (шлях або None, is_sdk_reference)."""
    src = source.replace("\\", "/")
    m = re.match(r"^([A-Za-z]+):(.*)$", src)
    candidates = []
    sdk_ref = False
    if m and len(m.group(1)) > 1:          # DATA:, ART:, AUDIO: ... (не літера диска)
        prefix, rest = m.group(1).upper(), m.group(2).lstrip("/")
        sdk_ref = True
        if prefix == "DATA":
            candidates.append(os.path.join(data_dir, rest))
    else:
        candidates.append(os.path.join(os.path.dirname(including_file), src))
        candidates.append(os.path.join(data_dir, src))
    for c in candidates:
        if os.path.isfile(c):
            return os.path.normpath(c), False
    return None, sdk_ref


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(errors="replace")
        except Exception:
            pass
    argv = sys.argv[1:] if argv is None else argv
    if argv and argv[0] in ("-h", "--help"):
        print(__doc__)
        return 0
    mod_dir = os.path.abspath(argv[0]) if argv else DEFAULT_MOD
    data_dir = os.path.join(mod_dir, "data")
    rep = Report()
    print("FortressTowers validate — мод: %s" % mod_dir)

    rep.section("0. Структура")
    if not os.path.isdir(data_dir):
        rep.err("немає теки %s" % data_dir)
        rep.print()
        return 1
    rep.ok("тека data існує")

    # ---------------------------------------------------------------- (a)
    rep.section("(a) Парсинг XML")
    xml_files = []
    for root, dirs, files in os.walk(data_dir):
        dirs.sort()
        for f in sorted(files):
            if f.lower().endswith(".xml"):
                xml_files.append(os.path.join(root, f))
    trees = {}
    raw_texts = {}
    for path in xml_files:
        name = rel(path, data_dir)
        try:
            raw_texts[path] = read_text(path)
        except OSError as e:
            rep.err("%s: не читається (%s)" % (name, e))
            continue
        try:
            tree = ET.parse(path)
        except ET.ParseError as e:
            rep.err("%s: XML не парситься: %s" % (name, e))
            continue
        r = tree.getroot()
        trees[path] = r
        if local(r.tag) != "AssetDeclaration":
            rep.err("%s: кореневий елемент %s, очікувався AssetDeclaration" % (name, local(r.tag)))
        elif not r.tag.startswith("{%s}" % NS):
            rep.err("%s: AssetDeclaration без xmlns=\"%s\"" % (name, NS))
        if not raw_texts[path].lstrip("\ufeff").startswith("<?xml"):
            rep.warn("%s: немає заголовка <?xml ...?>" % name)
    if not xml_files:
        rep.err("у data немає жодного .xml")
    else:
        rep.info("XML-файлів: %d, розпарсено: %d" % (len(xml_files), len(trees)))

    # Збір визначень (id) з усіх файлів
    defs = {}          # id -> [(тип, файл)]
    game_objects = {}  # id -> файл
    for path, r in trees.items():
        for el in r.iter():
            i = el.get("id")
            if i and el is not r:
                t = local(el.tag)
                defs.setdefault(i, []).append((t, rel(path, data_dir)))
                if t == "GameObject":
                    if i in game_objects:
                        rep.warn("GameObject %s визначено двічі: %s і %s"
                                 % (i, game_objects[i], rel(path, data_dir)))
                    game_objects.setdefault(i, rel(path, data_dir))

    # ---------------------------------------------------------------- (b)
    rep.section("(b) Підключення (Includes)")
    mod_xml = os.path.join(data_dir, "mod.xml")
    if not os.path.isfile(mod_xml):
        rep.err("немає data/mod.xml")
    sdk_refs = {}
    for path, r in sorted(trees.items()):
        name = rel(path, data_dir)
        for inc in r.iter("{%s}Include" % NS):
            src = inc.get("source")
            if not src:
                rep.err("%s: <Include> без source" % name)
                continue
            target, sdk_ref = resolve_include(src, path, data_dir)
            if target is None:
                if sdk_ref:
                    sdk_refs.setdefault(src, set()).add(name)
                else:
                    rep.err("%s: ціль Include не існує: %s" % (name, src))
    for src in sorted(sdk_refs):
        rep.info("%s — асет SDK, локально не перевіряється (у %d файлах)" % (src, len(sdk_refs[src])))
    if mod_xml in trees:
        included = set()
        for inc in trees[mod_xml].iter("{%s}Include" % NS):
            src = inc.get("source") or ""
            target, _ = resolve_include(src, mod_xml, data_dir)
            if target:
                included.add(os.path.normcase(target))
        expected = [p for p in xml_files
                    if rel(p, data_dir).split("/")[0] in ("Towers", "Logic")]
        missing = [p for p in expected if os.path.normcase(os.path.normpath(p)) not in included]
        for p in missing:
            rep.err("mod.xml не підключає %s" % rel(p, data_dir))
        if expected and not missing:
            rep.ok("mod.xml підключає всі %d файлів Towers/** і Logic/**" % len(expected))
        elif not expected:
            rep.warn("у Towers/ і Logic/ ще немає жодного .xml")
    # Очікувані файли веж за SPEC
    for tid, side in TOWERS:
        found = [p for p in xml_files if os.path.basename(p) == tid + ".xml"
                 and rel(p, data_dir).startswith("Towers/")]
        if not found:
            rep.warn("немає файла Towers/<Faction>/%s.xml" % tid)

    # ---------------------------------------------------------------- (c)
    rep.section("(c) GameObject веж і наноядер")
    for i in TOWER_IDS + NANOCORES:
        if i in game_objects:
            rep.ok("%-28s %s" % (i, game_objects[i]))
        else:
            other = defs.get(i)
            if other:
                rep.err("%s визначено, але не як GameObject (%s)" % (i, ", ".join(t for t, _ in other)))
            else:
                rep.err("немає GameObject id=\"%s\"" % i)
    if UPGRADE_ID not in defs:
        rep.warn("не знайдено визначення %s (апгрейд зі SPEC)" % UPGRADE_ID)

    # ---------------------------------------------------------------- (d)
    rep.section("(d) Рядки (.str)")
    strings_dir = os.path.join(data_dir, "strings")
    fragments = []
    if os.path.isdir(strings_dir):
        fragments = sorted(f for f in os.listdir(strings_dir) if f.lower().endswith(".str"))
    labels = {}  # мітка -> перше місце
    for f in fragments:
        name = "strings/" + f
        for lab, n in parse_str_file(os.path.join(strings_dir, f), name, rep):
            if lab in labels:
                rep.err("мітка %s визначена двічі: %s:%d і %s" % (lab, name, n, labels[lab]))
            else:
                labels[lab] = "%s:%d" % (name, n)
    mod_str = os.path.join(data_dir, "mod.str")
    if os.path.isfile(mod_str):
        mod_labels = {}
        for lab, n in parse_str_file(mod_str, "mod.str", rep):
            if lab in mod_labels:
                rep.err("mod.str: мітка %s визначена двічі (рядки %d і %d)" % (lab, mod_labels[lab], n))
            else:
                mod_labels[lab] = n
        if fragments:
            stale = set(labels) ^ set(mod_labels)
            if stale:
                rep.warn("mod.str не збігається з data/strings/*.str (%d міток різниці) — запустіть scripts/merge_strings.py"
                         % len(stale))
            else:
                rep.ok("mod.str актуальний (%d міток)" % len(mod_labels))
        else:
            for lab, n in mod_labels.items():
                labels.setdefault(lab, "mod.str:%d" % n)
    else:
        rep.warn("немає data/mod.str — запустіть scripts/merge_strings.py")
    rep.info(".str фрагментів: %d, унікальних міток: %d" % (len(fragments), len(labels)))

    mod_ids = set(defs) | set(TOWER_IDS) | set(NANOCORES)
    refs = {}  # мітка -> множина файлів
    for path, r in trees.items():
        name = rel(path, data_dir)
        for el in r.iter():
            chunks = [el.text or ""] + list(el.attrib.values())
            for c in chunks:
                for m in LABEL_REF_RE.finditer(c):
                    refs.setdefault("%s:%s" % (m.group(1), m.group(2)), set()).add(name)
    missing_strict = 0
    for lab in sorted(refs):
        if lab in labels:
            continue
        prefix, ident = lab.split(":", 1)
        where = ", ".join(sorted(refs[lab]))
        if prefix in STRICT_PREFIXES and ident in mod_ids:
            rep.err("мітка %s (у %s) відсутня в .str" % (lab, where))
            missing_strict += 1
        elif prefix in STRICT_PREFIXES:
            rep.warn("мітка %s (у %s) відсутня в .str — якщо це ванільна мітка, усе гаразд" % (lab, where))
        else:
            rep.warn("мітка %s (у %s) відсутня в .str" % (lab, where))
    for tid in TOWER_IDS:
        for p in STRICT_PREFIXES:
            lab = "%s:%s" % (p, tid)
            if lab not in labels and lab not in refs:
                rep.err("SPEC вимагає мітку %s — її немає в .str" % lab)
    rep.info("посилань на мітки в XML: %d, знайдено в .str: %d"
             % (len(refs), sum(1 for l in refs if l in labels)))

    # ---------------------------------------------------------------- (e)
    rep.section("(e) Команди Command_Construct*")
    commands = {}  # id -> (файл, [object ids])
    for path, r in trees.items():
        for el in r.iter():
            i = el.get("id") or ""
            if i.startswith("Command_Construct"):
                objs = [(o.text or "").strip() for o in el.iter("{%s}Object" % NS)]
                if el.get("Object"):
                    objs.append(el.get("Object").strip())
                commands[i] = (rel(path, data_dir), local(el.tag), [o for o in objs if o])
    for cid in sorted(commands):
        fname, tag, objs = commands[cid]
        if not objs:
            rep.err("%s (%s, %s): немає <Object>" % (cid, tag, fname))
        for o in objs:
            if o in game_objects:
                rep.ok("%s -> %s" % (cid, o))
            else:
                rep.err("%s (%s): <Object>%s</Object> — такого GameObject у моді немає" % (cid, fname, o))
    used = {}
    for path, r in trees.items():
        name = rel(path, data_dir)
        for el in r.iter():
            for c in [el.text or ""] + [v for k, v in el.attrib.items() if k != "id"]:
                for m in CONSTRUCT_RE.finditer(c):
                    used.setdefault(m.group(0), set()).add(name)
    mod_prefixes = tuple("Command_Construct" + t for t in TOWER_IDS)
    vanilla = []
    for cid in sorted(used):
        if cid in commands:
            continue
        where = ", ".join(sorted(used[cid]))
        if cid.startswith(mod_prefixes):
            rep.err("посилання на %s (у %s), але команду не визначено" % (cid, where))
        else:
            vanilla.append(cid)
    if vanilla:
        rep.info("ванільних команд у наборах (не перевіряються): %d" % len(vanilla))
        for k in range(0, len(vanilla), 3):
            rep.info("    " + ", ".join(vanilla[k:k + 3]))
    for tid, side in TOWERS:
        cid = "Command_Construct" + tid + ("Nanocore" if side == "Japan" else "")
        if cid not in commands:
            rep.err("SPEC вимагає команду %s — її не визначено" % cid)

    # ---------------------------------------------------------------- (f)
    rep.section("(f) Коментарі VERIFY (перевірити вручну в SageXml)")
    total = 0
    for path in xml_files:
        txt = raw_texts.get(path, "")
        n = sum(1 for c in COMMENT_RE.findall(txt) if "VERIFY" in c)
        total += n
        if n:
            rep.info("%4d  %s" % (n, rel(path, data_dir)))
    rep.info("%4d  РАЗОМ" % total)

    rep.print()
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
