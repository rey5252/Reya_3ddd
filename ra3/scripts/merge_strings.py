#!/usr/bin/env python3
"""Зливає фрагменти рядків FortressTowers/data/strings/*.str у data/mod.str.

Порядок: усі *.str за алфавітом (без урахування регістру), `_Logic.str` — останнім.
Лише стандартна бібліотека. Шляхи за замовчуванням — відносно цього скрипта:

    python merge_strings.py
    python merge_strings.py --strings-dir ..\\FortressTowers\\data\\strings --output ..\\FortressTowers\\data\\mod.str

Код виходу: 0 — успіх, 1 — помилка (немає фрагментів, дубль мітки, зламаний блок).
"""
import argparse
import datetime
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DATA = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "FortressTowers", "data"))
DEFAULT_STRINGS = os.path.join(DEFAULT_DATA, "strings")
DEFAULT_OUTPUT = os.path.join(DEFAULT_DATA, "mod.str")
LAST_FILE = "_logic.str"

LABEL_RE = re.compile(r'^([A-Za-z0-9_]+:[^\s"]+)\s*$')


def order_key(name):
    low = name.lower()
    return (1 if low == LAST_FILE else 0, low)


def parse_labels(text, fname, problems):
    """Повертає список (мітка, номер рядка). Пише проблеми у problems."""
    labels = []
    in_block = None
    for n, raw in enumerate(text.split("\n"), 1):
        line = raw.strip()
        if not line or line.startswith("//") or line.startswith(";"):
            continue
        if in_block is None:
            m = LABEL_RE.match(line)
            if m:
                in_block = (m.group(1), n)
                labels.append(in_block)
            else:
                problems.append("%s:%d: очікувалась мітка, знайдено: %s" % (fname, n, line[:60]))
        else:
            if line.upper() == "END":
                in_block = None
            elif LABEL_RE.match(line) and not line.startswith('"'):
                problems.append("%s:%d: мітка %s не закрита END перед %s"
                                % (fname, in_block[1], in_block[0], line))
                in_block = (LABEL_RE.match(line).group(1), n)
                labels.append(in_block)
    if in_block is not None:
        problems.append("%s:%d: мітка %s не закрита END" % (fname, in_block[1], in_block[0]))
    return labels


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(errors="replace")
        except Exception:
            pass
    ap = argparse.ArgumentParser(description="Зливає data/strings/*.str у data/mod.str")
    ap.add_argument("--strings-dir", default=DEFAULT_STRINGS, help="тека з фрагментами .str")
    ap.add_argument("--output", default=DEFAULT_OUTPUT, help="вихідний файл mod.str")
    ap.add_argument("--bom", action="store_true", help="записати UTF-8 з BOM (за замовчуванням без BOM)")
    ap.add_argument("--lf", action="store_true", help="переводи рядків LF замість CRLF")
    args = ap.parse_args(argv)

    sdir = os.path.abspath(args.strings_dir)
    out = os.path.abspath(args.output)
    if not os.path.isdir(sdir):
        print("ПОМИЛКА: теки з рядками немає: %s" % sdir)
        return 1
    files = sorted((f for f in os.listdir(sdir)
                    if f.lower().endswith(".str") and os.path.isfile(os.path.join(sdir, f))),
                   key=order_key)
    if not files:
        print("ПОМИЛКА: у %s немає жодного .str" % sdir)
        return 1

    problems = []
    seen = {}
    chunks = []
    for f in files:
        path = os.path.join(sdir, f)
        try:
            with open(path, "r", encoding="utf-8-sig") as fh:
                text = fh.read()
        except UnicodeDecodeError as e:
            problems.append("%s: не UTF-8 (%s)" % (f, e))
            continue
        text = text.replace("\r\n", "\n").replace("\r", "\n").strip("\n")
        labels = parse_labels(text, f, problems)
        for lab, n in labels:
            if lab in seen:
                problems.append("дубль мітки %s: %s:%d і %s" % (lab, f, n, seen[lab]))
            else:
                seen[lab] = "%s:%d" % (f, n)
        chunks.append((f, len(labels), text))

    if problems:
        print("ПОМИЛКИ — mod.str НЕ записано:")
        for p in problems:
            print("  - " + p)
        return 1

    stamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    lines = [
        "// ==========================================================",
        "// FortressTowers 1.0 — mod.str",
        "// ЗГЕНЕРОВАНО scripts/merge_strings.py (%s). НЕ РЕДАГУВАТИ ВРУЧНУ:" % stamp,
        "// редагуйте data/strings/*.str і запустіть скрипт знову.",
        "// Джерела (%d): %s" % (len(chunks), ", ".join(c[0] for c in chunks)),
        "// ==========================================================",
        "",
    ]
    for f, count, text in chunks:
        lines.append("// ---- %s (міток: %d) ----" % (f, count))
        lines.append("")
        if text:
            lines.extend(text.split("\n"))
            lines.append("")
    nl = "\n" if args.lf else "\r\n"
    data = nl.join(lines).rstrip(nl) + nl
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8-sig" if args.bom else "utf-8", newline="") as fh:
        fh.write(data)
    print("OK: файлів %d, міток %d -> %s" % (len(chunks), len(seen), out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
