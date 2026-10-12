"""make_bindings — (re)build models/textures/bindings.json from the current .glb files.

Run:  python3 -I models/textures/make_bindings.py [--check]

For every models/glb/<Model>.glb it reads the glTF JSON chunk, takes the material names and maps
each one to a texture of that model's faction set (SPEC.md, "Текстури"):

  * MANUAL[<Model>][<material>] wins (value "<set>/<name>", {"texture" (or "tex"): ..., "white": ...,
    "tile": ..., "offset": ...} or None = leave untextured);
  * otherwise translucent (alpha < 1) and glowing (emissive) materials stay untextured, except where
    ENERGY_OK allows an energy_* texture;
  * otherwise the first matching regex of the faction's KEYWORDS table decides; a list of
    candidates picks the texture whose average brightness (after meshkit's tint) is closest to the
    material colour;
  * no match -> untextured (listed in the report so the table can be extended);
  * */plate_steel on the foundation plate gets tile 6 (one framed deck panel per 6 x 6 plate).

Tint: meshkit lerps a textured material's baseColorFactor towards white by TEX_TINT_WHITE (0.7), so
the texture's own colours show. When that would make the surface much brighter than the model's
design colour (dark material on a light texture), a smaller per-binding "white" is written instead
({"texture": ..., "white": w}) so that the average brightness stays near the design (auto_white()).

Bindings to textures whose _dif.png does not exist yet are still written (meshkit.save() warns and
skips them), so the file can be generated before the texture sets are finished. --check only prints.
"""
from __future__ import annotations

import json
import math
import re
import struct
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
GLB_DIR = HERE.parent / "glb"
OUT = HERE / "bindings.json"

# SPEC.md: required names per set
REQUIRED = {
    "allied": ["panel_white", "panel_blue", "chrome", "energy_cyan", "plate_steel", "dark_metal"],
    "soviet": ["steel_olive", "paint_red", "hazard", "copper_coil", "concrete", "sandbag", "plate_steel",
               "dark_metal", "brass"],
    "empire": ["armor_white", "lacquer_crimson", "trim_black", "energy_teal", "stone", "plate_steel"],
}

PREFIX_SET = (("Allied", "allied"), ("Soviet", "soviet"), ("Japan", "empire"), ("Empire", "empire"))

# (regex matched against the whole material name, texture name in the faction set or None).
# First match wins; order matters (specific before generic).
SKIP = r"marker|hole|fx|scorch|flame.*|glow.*|lens|shield.*|halo|veil|ripple"
KEYWORDS = {
    "allied": [
        (SKIP, None),
        (r"snow|ice|frost.*|crystal.*", None),
        (r"chrome.*|steel.*|metal.*", "chrome"),
        (r"white.*|armor.*|armour.*", "panel_white"),
        (r"blue.*|navy.*", "panel_blue"),
        (r"plate.*|panel.*", ["plate_steel", "dark_metal"]),
        (r"dark.*|seam.*|black.*|gun.*|trim.*", "dark_metal"),
    ],
    "soviet": [
        (SKIP, None),
        (r"cream|ceramic|insulator.*|crate.*|wood.*|tesla.*", None),
        (r"haz.*", "hazard"),
        (r"red.*|paint.*", "paint_red"),
        (r"olive.*|green.*", "steel_olive"),
        (r"copper.*|coil.*", "copper_coil"),
        (r"brass.*|shell.*", "brass"),
        (r"bag.*|sandbag.*", "sandbag"),
        (r"concrete.*|floor.*|bunker.*", "concrete"),
        (r"groove.*|slit.*|gun.*|steel_d.*|dark.*|black.*|plate_d.*", "dark_metal"),
        (r"plate.*|steel.*|rivet.*|metal.*|armor.*", ["plate_steel", "dark_metal", "steel_olive"]),
    ],
    "empire": [
        (SKIP, None),
        (r"teal.*|orb|core|bowl|paper|rope.*", None),
        (r"white.*|armor.*|armour.*", "armor_white"),
        (r"crimson.*|red.*|lacquer.*", "lacquer_crimson"),
        (r"black.*|graphite.*|trim.*|dark.*", "trim_black"),
        (r"stone.*|rock.*", "stone"),
        (r"plate.*|panel.*|steel.*|metal.*", ["plate_steel", "armor_white", "stone"]),
    ],
}

# Glowing materials that may still take an energy texture (broad, softly glowing surfaces).
ENERGY_OK = {
    "allied": {"vent": "energy_cyan"},
    "empire": {"teal_floor": "energy_teal"},
    "soviet": {},
}

# Per-model manual overrides: {model: {material: "<set>/<name>" | {"texture":..., "white":...,
# "tile":..., "offset":...} | None}}
MANUAL: dict[str, dict] = {
    # separate yellow/black hazard materials: both take the hazard texture (its stripes + wear
    # show through the yellow blocks, the near-black tint keeps the black blocks dark)
    "SovietFlakNest": {"haz_y": "soviet/hazard", "haz_k": "soviet/hazard"},
    "SovietTeslaObelisk": {"hazard_y": "soviet/hazard", "hazard_k": "soviet/hazard"},
    "SovietInfernoBunker": {"haz_y": "soviet/hazard", "haz_k": "soviet/hazard"},
}

# */plate_steel is drawn as ONE deck panel framed on the tile border, so on the foundation plate
# one tile must span the whole 6 x 6 plate: tile 6, offset 0.5 puts the frame on the plate edges.
# Applied automatically to plate_steel bindings of materials whose geometry spans the plate
# (x and z extent >= FOUNDATION_MIN_EXTENT, lowest point at or below the plate top).
FOUNDATION_TEX = ("plate_steel",)
FOUNDATION_TILE = {"tile": 6.0, "offset": 0.5}
FOUNDATION_MIN_EXTENT = 5.4
FOUNDATION_NAMES = r"plate.*|deck.*|floor.*|base.*|foundation.*"   # not rivets/bolts spread on it
PLATE_TOP = 0.35


TINT_WHITE = 0.7      # must match meshkit.TEX_TINT_WHITE
BRIGHT_LIMIT = 1.35   # allowed brightness ratio (textured / design) before "white" is lowered
_MEAN_CACHE: dict = {}


def _lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def tex_mean(tex: str):
    """Average linear RGB of <tex>_dif.png, or None if the file does not exist (yet)."""
    if tex not in _MEAN_CACHE:
        f = HERE / f"{tex}_dif.png"
        if not f.exists():
            _MEAN_CACHE[tex] = None
        else:
            import numpy as np
            from PIL import Image
            a = np.asarray(Image.open(f).convert("RGB"), dtype=np.float64) / 255.0
            lin = np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)
            _MEAN_CACHE[tex] = tuple(lin.reshape(-1, 3).mean(0))
    return _MEAN_CACHE[tex]


def _tinted_lum(col, mean, w):
    return _lum([(c + (1 - c) * w) * m for c, m in zip(col, mean)])


def auto_white(col, mean):
    """Lerp-to-white amount for this material/texture pair (TINT_WHITE unless that is too bright)."""
    lc = _lum(col)
    if _tinted_lum(col, mean, TINT_WHITE) <= BRIGHT_LIMIT * lc + 0.01:
        return TINT_WHITE
    target = 1.2 * lc + 0.005
    l0 = _tinted_lum(col, mean, 0.0)
    l1 = _lum([(1 - c) * m for c, m in zip(col, mean)]) or 1e-9
    return round(min(max((target - l0) / l1, 0.15), TINT_WHITE), 2)


def pick(col, tset, cands):
    """Best candidate texture for a design colour (closest average brightness after tinting)."""
    if isinstance(cands, str):
        return cands
    best, score = cands[0], None
    for t in cands:
        m = tex_mean(f"{tset}/{t}")
        if m is None:
            continue
        sc = abs(math.log((_tinted_lum(col, m, auto_white(col, m)) + 0.02) / (_lum(col) + 0.02)))
        if score is None or sc < score - 1e-6:
            best, score = t, sc
    return best


def with_tint(binding, col):
    """String binding -> {"texture", "white"} when the automatic tint differs from the default."""
    if not isinstance(binding, str):
        return binding
    m = tex_mean(binding)
    if m is None:
        return binding
    w = auto_white(col, m)
    return binding if abs(w - TINT_WHITE) < 0.05 else {"texture": binding, "white": w}


def glb_json(path: Path) -> dict:
    b = path.read_bytes()
    magic, _ver, _len = struct.unpack_from("<III", b, 0)
    if magic != 0x46546C67:
        raise ValueError(f"{path.name}: not a glb")
    clen, ctype = struct.unpack_from("<II", b, 12)
    if ctype != 0x4E4F534A:
        raise ValueError(f"{path.name}: first chunk is not JSON")
    return json.loads(b[20:20 + clen])


def faction_set(model: str) -> str | None:
    for pre, s in PREFIX_SET:
        if model.startswith(pre):
            return s
    return None


def classify(model: str, mat: dict, tset: str):
    """-> (binding or None, reason)."""
    name = mat.get("name", "")
    pbr = mat.get("pbrMetallicRoughness", {})
    col = (pbr.get("baseColorFactor") or [1, 1, 1, 1])
    ext = mat.get("extras", {}).get("ft_design_color")   # pre-tint colour of an already textured glb
    if ext:
        col = list(ext) + [col[3]]
    if name in MANUAL.get(model, {}):
        return with_tint(MANUAL[model][name], col[:3]), "manual"
    alpha = col[3]
    if alpha < 1 or mat.get("alphaMode") == "BLEND":
        return None, "translucent"
    if any(mat.get("emissiveFactor") or []):
        e = ENERGY_OK.get(tset, {}).get(name)
        return (f"{tset}/{e}", "energy") if e else (None, "emissive")
    for rx, tex in KEYWORDS[tset]:
        if re.fullmatch(rx, name):
            if not tex:
                return None, f"keyword /{rx}/"
            return with_tint(f"{tset}/{pick(col[:3], tset, tex)}", col[:3]), f"keyword /{rx}/"
    return None, "no keyword"


# The */plate_steel textures are one deck panel with a frame on the tile border, so on the
# foundation itself one tile spans the whole 6x6 plate instead of the default 2 units.
PLATE_MATERIALS = re.compile(r"^plate(_top|_hi|_in)?$")
PLATE_TILE = 6.0


def plate_tile(name, binding):
    tex = binding if isinstance(binding, str) else binding.get("texture")
    if not (tex and tex.endswith("/plate_steel") and PLATE_MATERIALS.match(name)):
        return binding
    out = {"texture": tex} if isinstance(binding, str) else dict(binding)
    out["tile"] = PLATE_TILE
    return out


def material_bounds(js: dict) -> dict:
    """{material index: (min xyz, max xyz)} in model space (node translations applied)."""
    out = {}
    nodes, meshes, acc = js.get("nodes", []), js.get("meshes", []), js.get("accessors", [])

    def walk(i, off):
        n = nodes[i]
        t = n.get("translation", [0, 0, 0])
        o = [off[k] + t[k] for k in range(3)]
        if "mesh" in n:
            for prim in meshes[n["mesh"]]["primitives"]:
                a = acc[prim["attributes"]["POSITION"]]
                if "min" not in a:
                    continue
                lo = [a["min"][k] + o[k] for k in range(3)]
                hi = [a["max"][k] + o[k] for k in range(3)]
                mi = prim.get("material")
                if mi in out:
                    plo, phi = out[mi]
                    lo = [min(lo[k], plo[k]) for k in range(3)]
                    hi = [max(hi[k], phi[k]) for k in range(3)]
                out[mi] = (lo, hi)
        for c in n.get("children", []):
            walk(c, o)

    for r in js.get("scenes", [{}])[js.get("scene", 0)].get("nodes", []):
        walk(r, [0.0, 0.0, 0.0])
    return out


def foundation_density(binding, bounds, name=""):
    """plate_steel on the foundation plate -> one tile per plate (see FOUNDATION_TILE)."""
    tex = binding if isinstance(binding, str) else binding.get("texture") or binding.get("tex")
    if (not tex or tex.split("/")[-1] not in FOUNDATION_TEX or not bounds
            or not re.fullmatch(FOUNDATION_NAMES, name)):
        return binding, False
    lo, hi = bounds
    if (hi[0] - lo[0] >= FOUNDATION_MIN_EXTENT and hi[2] - lo[2] >= FOUNDATION_MIN_EXTENT
            and lo[1] <= PLATE_TOP + 0.05):
        b = {"texture": tex} if isinstance(binding, str) else dict(binding)
        if "tile" not in b:
            b.update(FOUNDATION_TILE)
        return b, True
    return binding, False


def build(verbose=True):
    out, missing = {}, set()
    for glb in sorted(GLB_DIR.glob("*.glb")):
        model = glb.stem
        tset = faction_set(model)
        if tset is None:
            print(f"skip {glb.name}: unknown faction prefix")
            continue
        try:
            js = glb_json(glb)
        except (OSError, ValueError) as e:
            print(f"skip {glb.name}: {e}")
            continue
        entry = {}
        lines = []
        bounds = material_bounds(js)
        for mi, mat in enumerate(js.get("materials", [])):
            b, why = classify(model, mat, tset)
            if b:
                b, fnd = foundation_density(b, bounds.get(mi), mat.get("name", ""))
                if fnd:
                    why += ", foundation tile 6"
            nm = mat.get("name", "?")
            if b:
                b = plate_tile(nm, b)
                entry[nm] = b
                tex = b if isinstance(b, str) else (b.get("texture") or b.get("tex"))
                if not (HERE / f"{tex}_dif.png").exists():
                    missing.add(tex)
            shown = (b if isinstance(b, str) else (b or {}).get("texture") or (b or {}).get("tex")) or "-"
            if isinstance(b, dict) and "white" in b:
                shown += f" w={b['white']}"
            lines.append(f"    {nm:<16} -> {shown:<30} ({why})")
        out[model] = entry
        if verbose:
            print(f"{model}: {len(entry)}/{len(js.get('materials', []))} materials textured")
            print("\n".join(lines))
    return out, sorted(missing)


def main():
    check = "--check" in sys.argv
    data, missing = build()
    for tset, names in REQUIRED.items():  # sanity: every bound texture must be a required name
        for model, ent in data.items():
            for mat, b in ent.items():
                t = b if isinstance(b, str) else (b.get("texture") or b.get("tex"))
                s, n = t.split("/", 1)
                if s == tset and n not in names:
                    print(f"WARNING {model}.{mat}: {t} is not in the SPEC list for {tset}")
    if missing:
        print("texture files not present yet (meshkit will skip them):", ", ".join(missing))
    if check:
        return
    OUT.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({len(data)} models)")


if __name__ == "__main__":
    main()
