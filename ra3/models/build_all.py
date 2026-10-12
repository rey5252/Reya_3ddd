"""build_all — rebuild textures, texture bindings and every tower model.

Run:  python3 -I models/build_all.py [--no-textures] [--skip-gen]

  1. models/textures/gen_*.py        (texture sets; those that exist)   -- skipped with --skip-gen
  2. models/towers/*.py              (first pass: fresh .glb files, so new materials are seen)
  3. models/textures/make_bindings.py (material -> texture map, bindings.json)
  4. models/towers/*.py              (second pass: .glb files with UVs + texture references)
  5. summary: triangles, textured materials, missing texture files per model

--no-textures sets FT_NO_TEXTURES=1 (untextured .glb, steps 1, 3, 4 skipped).
Failures of single scripts are reported and do not stop the run.
"""
from __future__ import annotations

import json
import os
import struct
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
TEX = HERE / "textures"
GLB = HERE / "glb"
TOWERS = HERE / "towers"


def run(script: Path, env) -> bool:
    t = time.time()
    r = subprocess.run([sys.executable, "-I", str(script)], cwd=HERE.parent, env=env,
                       capture_output=True, text=True)
    status = "ok" if r.returncode == 0 else f"FAILED ({r.returncode})"
    print(f"  {script.relative_to(HERE.parent)}: {status}  [{time.time() - t:.1f}s]")
    warn = [ln for ln in (r.stdout + r.stderr).splitlines() if "missing" in ln or "WARN" in ln.upper()]
    for ln in warn[:8]:
        print(f"      {ln}")
    if r.returncode != 0:
        for ln in r.stderr.strip().splitlines()[-6:]:
            print(f"      {ln}")
    return r.returncode == 0


def glb_json(path: Path):
    b = path.read_bytes()
    clen, _ = struct.unpack_from("<II", b, 12)
    return json.loads(b[20:20 + clen])


def summary():
    print("\nSummary")
    print(f"  {'model':<28}{'triangles':>10}{'vertices':>10}  textured materials")
    missing_all = set()
    for f in sorted(GLB.glob("*.glb")):
        try:
            js = glb_json(f)
        except (OSError, ValueError) as e:
            print(f"  {f.stem:<28} unreadable: {e}")
            continue
        acc = js["accessors"]
        tris = verts = 0
        for m in js.get("meshes", []):
            for p in m["primitives"]:
                tris += acc[p["indices"]]["count"] // 3
                verts += acc[p["attributes"]["POSITION"]]["count"]
        mats = js.get("materials", [])
        tex = [m for m in mats if "baseColorTexture" in m.get("pbrMetallicRoughness", {})]
        missing = []
        for im in js.get("images", []):
            uri = im.get("uri")
            if uri and not (f.parent / uri).resolve().exists():
                missing.append(uri)
        missing_all.update(missing)
        names = ", ".join(f"{m['name']}={m.get('extras', {}).get('ft_texture', '?')}" for m in tex)
        print(f"  {f.stem:<28}{tris:>10}{verts:>10}  {len(tex)}/{len(mats)}: {names}")
        for u in missing:
            print(f"      MISSING image {u}")
    # bindings that could not be applied (texture file absent at build time)
    try:
        bind = json.loads((TEX / "bindings.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        bind = {}
    absent = set()
    for ent in bind.values():
        for b in ent.values():
            t = b if isinstance(b, str) else (b or {}).get("texture")
            if t and not (TEX / f"{t}_dif.png").exists():
                absent.add(t)
    for t in sorted(absent):
        print(f"  bound but missing texture file: textures/{t}_dif.png")
    if not absent and not missing_all:
        print("  all bound texture files present")


def main():
    no_tex = "--no-textures" in sys.argv
    env = dict(os.environ)
    if no_tex:
        env["FT_NO_TEXTURES"] = "1"
    if not no_tex and "--skip-gen" not in sys.argv:
        gens = sorted(TEX.glob("gen_*.py"))
        print(f"[1] texture generators ({len(gens)})")
        for g in gens:
            run(g, env)
    towers = sorted(TOWERS.glob("*.py"))
    if no_tex:
        print(f"[2] tower models, untextured ({len(towers)})")
        for t in towers:
            run(t, env)
    else:
        print(f"[2] tower models, first pass ({len(towers)})")
        env1 = dict(env, FT_NO_TEXTURES="1")
        for t in towers:
            run(t, env1)
        print("[3] texture bindings")
        run(TEX / "make_bindings.py", env)
        print("[4] tower models with textures")
        for t in towers:
            run(t, env)
    summary()


if __name__ == "__main__":
    main()
