#!/usr/bin/env python3
"""Збирання 3D-моделей Fortress Protocol (лише стандартна бібліотека, запуск з будь-якої теки).

    python build_models.py                      # .py -> .glb для всіх веж + звіт по трикутниках
    python build_models.py --only AlliedPrismSpire,SovietFlakNest
    python build_models.py --no-generate        # лише звіт по наявних models/glb/*.glb
    python build_models.py --textures           # спершу models/textures/gen_*.py (потрібен Pillow)
    python build_models.py --blender "C:\\Program Files\\Blender Foundation\\Blender 4.2\\blender.exe"
    python build_models.py --blender blender -- --addon-path D:\\OpenSAGE.BlenderPlugin --scale 10

Кроки:
  1. (--textures) запускає models/textures/gen_<set>.py;
  2. запускає кожен models/towers/*.py поточним Python з -I (генератори пишуть models/glb/*.glb);
  3. читає JSON-чанк кожного .glb і рахує трикутники / вершини / вузли, перевіряє бюджет SPEC
     (вежа 3 000-12 000, наноядро <= 2 500) і наявність усіх 12 файлів;
  4. (--blender PATH) запускає  PATH -b -P scripts/blender_export_w3x.py -- models/glb <art>
     (за замовчуванням <art> = FortressTowers/art/FT); усе після окремого "--" передається
     скрипту експорту без змін (--addon-path, --scale, --only, --no-textures ...).
Код виходу 1, якщо генератор/експорт упав або бракує моделей (бюджет — лише попередження).
"""
import json
import os
import struct
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(SCRIPT_DIR, ".."))
TOWERS_DIR = os.path.join(ROOT, "models", "towers")
GLB_DIR = os.path.join(ROOT, "models", "glb")
TEX_DIR = os.path.join(ROOT, "models", "textures")
DEFAULT_ART = os.path.join(ROOT, "FortressTowers", "art", "FT")
EXPORT_SCRIPT = os.path.join(SCRIPT_DIR, "blender_export_w3x.py")

TOWER_IDS = ["AlliedPrismSpire", "AlliedCryoBastion", "AlliedAegisSpire",
             "SovietTeslaObelisk", "SovietFlakNest", "SovietInfernoBunker", "SovietTsarCannon",
             "JapanTenguSpire", "JapanWaveCitadel", "JapanKitsuneShrine"]
NANOCORES = [t + "Nanocore" for t in TOWER_IDS if t.startswith("Japan")]
BUDGET_TOWER = (3000, 12000)
BUDGET_NANOCORE = (0, 2500)
GEN_TIMEOUT = 600


def ft_name(stem):
    for p in ("Allied", "Soviet", "Japan"):
        if stem.startswith(p):
            return "FT" + stem[len(p):]
    return stem


def usage_exit(msg):
    print("ПОМИЛКА: %s\n" % msg)
    print(__doc__)
    sys.exit(2)


def parse_args(argv):
    opts = dict(only=None, generate=True, textures=False, blender=None, art=DEFAULT_ART,
                passthrough=[])
    if "--" in argv:
        k = argv.index("--")
        opts["passthrough"] = argv[k + 1:]
        argv = argv[:k]
    i = 0
    while i < len(argv):
        a = argv[i]
        if a in ("-h", "--help"):
            print(__doc__)
            sys.exit(0)
        elif a == "--no-generate":
            opts["generate"] = False
        elif a == "--textures":
            opts["textures"] = True
        elif a in ("--only", "--blender", "--art"):
            if i + 1 >= len(argv):
                usage_exit("немає значення після %s" % a)
            v = argv[i + 1]
            i += 1
            if a == "--only":
                opts["only"] = {s.strip() for s in v.split(",") if s.strip()}
            elif a == "--blender":
                opts["blender"] = v
            else:
                opts["art"] = os.path.abspath(v)
        else:
            usage_exit("невідомий аргумент %s" % a)
        i += 1
    return opts


# --------------------------------------------------------------------------- glb

def read_glb_json(path):
    with open(path, "rb") as fh:
        head = fh.read(12)
        if len(head) < 12:
            raise ValueError("файл закороткий")
        magic, version, _length = struct.unpack("<III", head)
        if magic != 0x46546C67:
            raise ValueError("не GLB (magic)")
        if version != 2:
            raise ValueError("glTF версії %d (очікується 2)" % version)
        clen, ctype = struct.unpack("<II", fh.read(8))
        if ctype != 0x4E4F534A:
            raise ValueError("перший чанк не JSON")
        return json.loads(fh.read(clen).decode("utf-8"))


def glb_stats(path):
    """Трикутники рахуються по екземплярах: меш, на який посилаються 2 вузли, — двічі."""
    gl = read_glb_json(path)
    acc = gl.get("accessors", [])
    meshes = gl.get("meshes", [])
    nodes = gl.get("nodes", [])

    def mesh_counts(mi):
        tris = verts = 0
        for prim in meshes[mi].get("primitives", []):
            mode = prim.get("mode", 4)
            nverts = acc[prim["attributes"]["POSITION"]]["count"]
            n = acc[prim["indices"]]["count"] if "indices" in prim else nverts
            if mode == 4:
                tris += n // 3
            elif mode in (5, 6):
                tris += max(0, n - 2)
            verts += nverts
        return tris, verts

    tris = verts = 0
    names, fx = [], []
    scene = gl.get("scenes", [{}])[gl.get("scene", 0)] if gl.get("scenes") else {}
    stack = list(scene.get("nodes", range(len(nodes))))
    seen = set()
    while stack:
        ni = stack.pop()
        if ni in seen:
            continue
        seen.add(ni)
        nd = nodes[ni]
        names.append(nd.get("name", "#%d" % ni))
        if "mesh" in nd:
            t, v = mesh_counts(nd["mesh"])
            tris += t
            verts += v
        stack.extend(nd.get("children", []))
    for n in names:
        if n.startswith("FX_"):
            fx.append(n)
    images = [im.get("uri", "<embedded>") for im in gl.get("images", [])]
    missing_img = [u for u in images if u != "<embedded>" and not u.startswith("data:")
                   and not os.path.isfile(os.path.join(os.path.dirname(path), u))]
    return dict(tris=tris, verts=verts, nodes=len(names), materials=len(gl.get("materials", [])),
                images=len(images), missing_img=missing_img, fx=fx,
                moving=[n for n in names if n in ("Base", "Turret", "Barrel", "Emitter")])


# --------------------------------------------------------------------------- steps

def run(cmd, cwd, timeout):
    t0 = time.time()
    try:
        p = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           timeout=timeout)
        out = p.stdout.decode("utf-8", "replace")
        return p.returncode, out, time.time() - t0
    except subprocess.TimeoutExpired:
        return -1, "таймаут %d с" % timeout, time.time() - t0
    except OSError as exc:
        return -1, str(exc), time.time() - t0


def run_scripts(paths, title):
    failed = []
    print("\n=== %s" % title)
    for p in paths:
        rc, out, dt = run([sys.executable, "-I", p], os.path.dirname(p), GEN_TIMEOUT)
        rel = os.path.relpath(p, ROOT)
        if rc == 0:
            print("  OK    %-44s %5.1f с" % (rel, dt))
        else:
            failed.append(rel)
            print("  FAIL  %-44s код %s" % (rel, rc))
            for line in out.strip().splitlines()[-12:]:
                print("        | " + line)
    return failed


def report(only):
    print("\n=== Моделі (models/glb)")
    expected = [t for t in TOWER_IDS + NANOCORES if not only or t in only or ft_name(t) in only]
    extra = sorted(os.path.splitext(f)[0] for f in (os.listdir(GLB_DIR) if os.path.isdir(GLB_DIR)
                                                     else [])
                   if f.lower().endswith(".glb") and os.path.splitext(f)[0] not in TOWER_IDS + NANOCORES)
    print("  %-28s %-24s %7s %7s %5s %4s %4s  %s" % ("файл", "W3X", "трикут.", "вершин", "вузли",
                                                    "мат.", "тек.", "стан"))
    missing, total = [], 0
    for stem in expected + [e for e in extra if not only or e in only]:
        path = os.path.join(GLB_DIR, stem + ".glb")
        if not os.path.isfile(path):
            missing.append(stem)
            print("  %-28s %-24s %7s %7s %5s %4s %4s  НЕМАЄ" % (stem, ft_name(stem), "-", "-", "-",
                                                            "-", "-"))
            continue
        try:
            st = glb_stats(path)
        except Exception as exc:  # noqa: BLE001 - зламаний файл не валить звіт
            missing.append(stem)
            print("  %-28s %-24s ПОМИЛКА читання: %s" % (stem, ft_name(stem), exc))
            continue
        total += st["tris"]
        lo, hi = BUDGET_NANOCORE if stem.endswith("Nanocore") else BUDGET_TOWER
        notes = []
        if not lo <= st["tris"] <= hi:
            notes.append("поза бюджетом %d-%d" % (lo, hi))
        if "Base" not in st["moving"]:
            notes.append("немає вузла Base")
        if st["missing_img"]:
            notes.append("бракує %d текстур" % len(st["missing_img"]))
        if stem in extra:
            notes.append("не зі SPEC")
        print("  %-28s %-24s %7d %7d %5d %4d %4d  %s" % (
            stem, ft_name(stem), st["tris"], st["verts"], st["nodes"], st["materials"],
            st["images"], "; ".join(notes) or "OK"))
        bones = st["moving"] + sorted(st["fx"])
        print("  %-28s кістки: %s" % ("", " ".join(bones)))
        for u in st["missing_img"][:3]:
            print("  %-28s   немає: %s" % ("", u))
    print("  разом трикутників: %d;  відсутні/зламані: %d" % (total, len(missing)))
    return missing


def export_w3x(blender, art, passthrough):
    print("\n=== W3X: Blender -> %s" % art)
    if not os.path.isdir(GLB_DIR):
        print("  немає models/glb — нічого конвертувати")
        return False
    # без --factory-startup: він вимкнув би аддони, увімкнені в налаштуваннях користувача.
    # Якщо io_mesh_w3d не знаходиться - передайте "-- --addon-path <тека з io_mesh_w3d>".
    cmd = [blender, "-b", "-P", EXPORT_SCRIPT, "--", GLB_DIR, art] + passthrough
    print("  " + " ".join('"%s"' % c if " " in c else c for c in cmd))
    try:
        p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    except OSError as exc:
        print("  ПОМИЛКА: не вдалося запустити Blender (%s)" % exc)
        return False
    out = p.stdout.decode("utf-8", "replace").splitlines()
    keep = [l for l in out if not l.startswith(("INFO:", "Info:")) and " | INFO: " not in l]
    if "SUMMARY" in "\n".join(keep):
        keep = keep[[i for i, l in enumerate(keep) if l.strip() == "SUMMARY"][-1] - 1:]
    else:
        keep = keep[-40:]
    for l in keep:
        print("  " + l)
    if p.returncode != 0:
        print("  Blender завершився з кодом %d" % p.returncode)
    return p.returncode == 0


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(errors="replace")
        except Exception:  # noqa: BLE001
            pass
    opts = parse_args(sys.argv[1:] if argv is None else argv)
    ok = True
    if opts["textures"]:
        gens = sorted(os.path.join(TEX_DIR, f) for f in (os.listdir(TEX_DIR)
                      if os.path.isdir(TEX_DIR) else []) if f.startswith("gen_") and f.endswith(".py"))
        if not gens:
            print("\n=== Текстури: генераторів models/textures/gen_*.py не знайдено — пропускаю")
        elif run_scripts(gens, "Текстури (models/textures/gen_*.py)"):
            ok = False
    if opts["generate"]:
        gens = sorted(os.path.join(TOWERS_DIR, f) for f in (os.listdir(TOWERS_DIR)
                      if os.path.isdir(TOWERS_DIR) else [])
                      if f.endswith(".py") and not f.startswith("_"))
        if opts["only"]:
            gens = [g for g in gens if os.path.splitext(os.path.basename(g))[0] in opts["only"]
                    or ft_name(os.path.splitext(os.path.basename(g))[0]) in opts["only"]]
        if not gens:
            print("\n=== Генератори: models/towers/*.py не знайдено")
        elif run_scripts(gens, "Генератори (models/towers/*.py -> models/glb/*.glb)"):
            ok = False
    missing = report(opts["only"])
    if missing:
        ok = False
    if opts["blender"]:
        passthrough = list(opts["passthrough"])
        if opts["only"] and "--only" not in passthrough:
            # наноядра теж: --only AlliedX -> лише AlliedX, JapanX -> JapanX + JapanXNanocore
            names = set(opts["only"])
            names |= {n + "Nanocore" for n in opts["only"] if n.startswith("Japan")}
            passthrough += ["--only", ",".join(sorted(names))]
        if not export_w3x(opts["blender"], opts["art"], passthrough):
            ok = False
    print("\nРЕЗУЛЬТАТ: %s" % ("OK" if ok else "Є ПОМИЛКИ (див. вище)"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
