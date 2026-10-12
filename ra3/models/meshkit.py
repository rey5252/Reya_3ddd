"""meshkit — tiny procedural mesh toolkit for the Fortress Protocol tower models.

Builds low/mid-poly meshes from primitives and writes binary glTF 2.0 (.glb).
Only numpy is required.

Conventions (shared by every tower model):
  * Y is up, 1 unit ~ 1 m. The foundation plate is a 6 x 6 square centred on the origin,
    its top face at y = PLATE_TOP. Towers stand on it and stay inside |x|,|z| <= 3.
  * A model is a tree of named nodes. Nodes that must move in game get fixed names:
    "Turret" (rotates around Y), "Barrel"/"Emitter" (pitch), "FX_<name>" (empty-ish
    marker nodes holding a tiny mesh, used as muzzle/FX bones). These names become
    W3X bones after conversion (see scripts/blender_export_w3x.py).
  * Materials are PBR metallic-roughness; emissive is used for glowing parts.

Example:
    from meshkit import Model, box, cylinder
    m = Model("AlliedPrismSpire")
    m.material("steel", (0.62, 0.70, 0.78), metallic=0.8, roughness=0.35)
    base = m.node("Base")
    base.add(box(4, 1, 4).translate(0, 0.8, 0), "steel")
    m.save("AlliedPrismSpire.glb")
"""
from __future__ import annotations

import json
import math
import os
import struct
import sys
from pathlib import Path

import numpy as np

PLATE_SIZE = 6.0
PLATE_TOP = 0.35

# --- textures (SPEC.md, "Текстури"): models/textures/<set>/<name>_{dif,nrm,spm}.png
TEXTURES_DIR = Path(__file__).resolve().parent / "textures"
BINDINGS_FILE = TEXTURES_DIR / "bindings.json"
TEX_TILE = 2.0          # 1 texture tile = 2 model units (2 m)
TEX_TINT_WHITE = 0.7    # baseColorFactor is lerped this far towards white on textured materials


# --------------------------------------------------------------------------- mesh

class Mesh:
    """Triangle mesh: positions (N,3), normals (N,3), uvs (N,2), indices (M,3)."""

    def __init__(self, pos, nrm, uv, idx):
        self.pos = np.asarray(pos, np.float32).reshape(-1, 3)
        self.nrm = np.asarray(nrm, np.float32).reshape(-1, 3)
        self.uv = np.asarray(uv, np.float32).reshape(-1, 2)
        self.idx = np.asarray(idx, np.uint32).reshape(-1, 3)

    def copy(self):
        return Mesh(self.pos.copy(), self.nrm.copy(), self.uv.copy(), self.idx.copy())

    # transforms return self so calls can be chained
    def translate(self, x=0.0, y=0.0, z=0.0):
        self.pos += np.array([x, y, z], np.float32)
        return self

    def scale(self, sx=1.0, sy=None, sz=None):
        sy = sx if sy is None else sy
        sz = sx if sz is None else sz
        s = np.array([sx, sy, sz], np.float32)
        self.pos *= s
        n = self.nrm / s
        self.nrm = _normalize(n)
        if (sx * sy * sz) < 0:
            self.idx = self.idx[:, ::-1].copy()
        return self

    def rotate(self, rx=0.0, ry=0.0, rz=0.0):
        """Rotate by Euler angles in degrees, applied X, then Y, then Z."""
        r = _rot_matrix(rx, ry, rz)
        self.pos = self.pos @ r.T
        self.nrm = self.nrm @ r.T
        return self

    def merged(self, other: "Mesh") -> "Mesh":
        off = len(self.pos)
        return Mesh(np.vstack([self.pos, other.pos]), np.vstack([self.nrm, other.nrm]),
                    np.vstack([self.uv, other.uv]), np.vstack([self.idx, other.idx + off]))

    def flat(self) -> "Mesh":
        """Un-shared vertices with per-face normals (faceted look, e.g. crystals)."""
        p = self.pos[self.idx.reshape(-1)]
        uv = self.uv[self.idx.reshape(-1)]
        tri = p.reshape(-1, 3, 3)
        fn = _normalize(np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]))
        n = np.repeat(fn, 3, axis=0)
        return Mesh(p, n, uv, np.arange(len(p), dtype=np.uint32))


def _normalize(v):
    v = np.asarray(v, np.float32)
    l = np.linalg.norm(v, axis=-1, keepdims=True)
    l[l == 0] = 1
    return v / l


def _rot_matrix(rx, ry, rz):
    ax, ay, az = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]], np.float32)
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], np.float32)
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]], np.float32)
    return mz @ my @ mx


def merge(*meshes: Mesh) -> Mesh:
    out = meshes[0].copy()
    for m in meshes[1:]:
        out = out.merged(m)
    return out


# --------------------------------------------------------------------------- primitives

def box(w=1.0, h=1.0, d=1.0, bevel=0.0) -> Mesh:
    """Axis-aligned box centred on the origin. bevel>0 chamfers the vertical edges."""
    if bevel > 0:
        b = min(bevel, w / 2 - 1e-3, d / 2 - 1e-3)
        x, z = w / 2, d / 2
        pts = [(x - b, -z), (x, -z + b), (x, z - b), (x - b, z), (-x + b, z), (-x, z - b), (-x, -z + b), (-x + b, -z)]
        return extrude(pts, h).translate(0, -h / 2, 0)
    hw, hh, hd = w / 2, h / 2, d / 2
    faces = [  # normal, 4 corners (ccw seen from outside)
        ((1, 0, 0), [(hw, -hh, hd), (hw, -hh, -hd), (hw, hh, -hd), (hw, hh, hd)]),
        ((-1, 0, 0), [(-hw, -hh, -hd), (-hw, -hh, hd), (-hw, hh, hd), (-hw, hh, -hd)]),
        ((0, 1, 0), [(-hw, hh, hd), (hw, hh, hd), (hw, hh, -hd), (-hw, hh, -hd)]),
        ((0, -1, 0), [(-hw, -hh, -hd), (hw, -hh, -hd), (hw, -hh, hd), (-hw, -hh, hd)]),
        ((0, 0, 1), [(-hw, -hh, hd), (hw, -hh, hd), (hw, hh, hd), (-hw, hh, hd)]),
        ((0, 0, -1), [(hw, -hh, -hd), (-hw, -hh, -hd), (-hw, hh, -hd), (hw, hh, -hd)]),
    ]
    pos, nrm, uv, idx = [], [], [], []
    for n, quad in faces:
        o = len(pos)
        pos += quad
        nrm += [n] * 4
        uv += [(0, 0), (1, 0), (1, 1), (0, 1)]
        idx += [(o, o + 1, o + 2), (o, o + 2, o + 3)]
    return Mesh(pos, nrm, uv, idx)


def lathe(profile, segments=24, start_deg=0.0, end_deg=360.0, smooth=True) -> Mesh:
    """Revolve a profile [(radius, y), ...] (bottom to top) around the Y axis.

    Point order sets the facing: for a closed ring (a wall) go up the outer face, across the top,
    then down the inner face; the reverse order turns the surface inside out.

    Use radius 0 at the ends to close the shape. smooth=False gives hard rings (faceted).
    """
    prof = [(float(r), float(y)) for r, y in profile]
    full = abs((end_deg - start_deg) % 360) < 1e-6
    cols = segments if full else segments + 1
    pos, nrm, uv, idx = [], [], [], []
    # profile normals (2D), averaged at joints
    pn = []
    for i in range(len(prof)):
        a = prof[max(i - 1, 0)]
        b = prof[min(i + 1, len(prof) - 1)]
        dr, dy = b[0] - a[0], b[1] - a[1]
        l = math.hypot(dr, dy) or 1.0
        pn.append((dy / l, -dr / l))
    if not smooth:
        segs = []
        for i in range(len(prof) - 1):
            segs.append((prof[i], prof[i + 1]))
        for si, (a, b) in enumerate(segs):
            dr, dy = b[0] - a[0], b[1] - a[1]
            l = math.hypot(dr, dy) or 1.0
            n2 = (dy / l, -dr / l)
            base = len(pos)
            for c in range(cols):
                t = math.radians(start_deg + (end_deg - start_deg) * c / segments)
                ct, st = math.cos(t), math.sin(t)
                for (r, y) in (a, b):
                    pos.append((r * ct, y, r * st))
                    nrm.append((n2[0] * ct, n2[1], n2[0] * st))
                    uv.append((c / segments, y))
            for c in range(segments):
                c2 = (c + 1) % cols
                a0, a1 = base + c * 2, base + c * 2 + 1
                b0, b1 = base + c2 * 2, base + c2 * 2 + 1
                idx += [(a0, b1, b0), (a0, a1, b1)]
        return Mesh(pos, nrm, uv, idx)
    for c in range(cols):
        t = math.radians(start_deg + (end_deg - start_deg) * c / segments)
        ct, st = math.cos(t), math.sin(t)
        for i, (r, y) in enumerate(prof):
            pos.append((r * ct, y, r * st))
            nrm.append((pn[i][0] * ct, pn[i][1], pn[i][0] * st))
            uv.append((c / segments, i / max(len(prof) - 1, 1)))
    rows = len(prof)
    for c in range(segments):
        c2 = (c + 1) % cols
        for i in range(rows - 1):
            a0, a1 = c * rows + i, c * rows + i + 1
            b0, b1 = c2 * rows + i, c2 * rows + i + 1
            idx += [(a0, b1, b0), (a0, a1, b1)]
    return Mesh(pos, nrm, uv, idx)


def cylinder(r=0.5, h=1.0, segments=24, r_top=None, caps=True) -> Mesh:
    """Cylinder / truncated cone standing on y=0, height h."""
    rt = r if r_top is None else r_top
    side = lathe([(r, 0), (rt, h)], segments, smooth=True)
    # lathe smooth normals on a 2-point profile are correct for the side
    parts = [side]
    if caps:
        if rt > 0:
            parts.append(disc(rt, segments).translate(0, h, 0))
        if r > 0:
            parts.append(disc(r, segments).rotate(180, 0, 0))
    return merge(*parts)


def cone(r=0.5, h=1.0, segments=24) -> Mesh:
    return cylinder(r, h, segments, r_top=0.0)


def disc(r=0.5, segments=24) -> Mesh:
    """Flat disc facing +Y at y=0."""
    pos, nrm, uv, idx = [(0, 0, 0)], [(0, 1, 0)], [(0.5, 0.5)], []
    for i in range(segments + 1):
        t = 2 * math.pi * i / segments
        pos.append((r * math.cos(t), 0, r * math.sin(t)))
        nrm.append((0, 1, 0))
        uv.append((0.5 + 0.5 * math.cos(t), 0.5 + 0.5 * math.sin(t)))
    for i in range(segments):
        idx.append((0, i + 2, i + 1))
    return Mesh(pos, nrm, uv, idx)


def sphere(r=0.5, segments=24, rings=16) -> Mesh:
    prof = [(r * math.sin(math.pi * i / rings), -r * math.cos(math.pi * i / rings)) for i in range(rings + 1)]
    return lathe(prof, segments)


def torus(R=1.0, r=0.15, segments=32, tube_segments=12) -> Mesh:
    """Ring lying in the XZ plane, centred on the origin."""
    prof = [(R + r * math.cos(2 * math.pi * i / tube_segments), r * math.sin(2 * math.pi * i / tube_segments))
            for i in range(tube_segments + 1)]
    m = lathe(prof, segments)
    # recompute normals exactly (lathe averages them; for a torus we know them)
    p = m.pos
    rad = np.sqrt(p[:, 0] ** 2 + p[:, 2] ** 2)
    rad[rad == 0] = 1
    cx, cz = p[:, 0] / rad * R, p[:, 2] / rad * R
    m.nrm = _normalize(np.stack([p[:, 0] - cx, p[:, 1], p[:, 2] - cz], 1))
    return m


def extrude(points2d, h=1.0, cap=True) -> Mesh:
    """Extrude a convex-or-star-shaped polygon [(x, z), ...] from y=0 to y=h.

    Either winding order works: the points are reordered so the side walls always face outward.
    """
    pts = [(float(x), float(z)) for x, z in points2d]
    n = len(pts)
    area2 = sum(pts[i][0] * pts[(i + 1) % n][1] - pts[(i + 1) % n][0] * pts[i][1] for i in range(n))
    if area2 < 0:  # side normals below are outward only for positive signed area in (x, z)
        pts.reverse()
    pos, nrm, uv, idx = [], [], [], []
    for i in range(n):
        a, b = pts[i], pts[(i + 1) % n]
        dx, dz = b[0] - a[0], b[1] - a[1]
        l = math.hypot(dx, dz) or 1.0
        nn = (dz / l, 0, -dx / l)
        o = len(pos)
        pos += [(a[0], 0, a[1]), (b[0], 0, b[1]), (b[0], h, b[1]), (a[0], h, a[1])]
        nrm += [nn] * 4
        uv += [(0, 0), (1, 0), (1, 1), (0, 1)]
        idx += [(o, o + 2, o + 1), (o, o + 3, o + 2)]
    if cap:
        cx = sum(p[0] for p in pts) / n
        cz = sum(p[1] for p in pts) / n
        for y, ny, flip in ((h, 1, False), (0, -1, True)):
            o = len(pos)
            pos.append((cx, y, cz))
            nrm.append((0, ny, 0))
            uv.append((0.5, 0.5))
            for x, z in pts:
                pos.append((x, y, z))
                nrm.append((0, ny, 0))
                uv.append((0.5, 0.5))
            for i in range(n):
                a, b = o + 1 + i, o + 1 + (i + 1) % n
                idx.append((o, b, a) if flip else (o, a, b))
    m = Mesh(pos, nrm, uv, idx)
    # make triangle winding agree with the (outward) normals computed above
    return _fix_winding(m)


def _fix_winding(m: Mesh) -> Mesh:
    tri = m.pos[m.idx]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    vn = m.nrm[m.idx].mean(axis=1)
    bad = (fn * vn).sum(1) < 0
    m.idx[bad] = m.idx[bad][:, ::-1]
    return m


def ngon(sides=6, r=1.0, h=1.0, rot_deg=0.0) -> Mesh:
    """Regular n-sided prism standing on y=0 (hard edges)."""
    pts = [(r * math.cos(math.radians(rot_deg + 360 * i / sides)), r * math.sin(math.radians(rot_deg + 360 * i / sides)))
           for i in range(sides)]
    return extrude(pts, h)


def crystal(r=0.6, h=2.0, sides=6, mid=0.55) -> Mesh:
    """Faceted bipyramid crystal standing on y=0 (flat-shaded)."""
    prof = [(0.0, 0.0), (r, h * mid * 0.35), (r * 0.9, h * mid), (0.0, h)]
    return lathe(prof, sides, smooth=False).flat()


def tube_between(p0, p1, r=0.08, segments=10) -> Mesh:
    """Cylinder from point p0 to p1 (pipes, struts, barrels)."""
    p0, p1 = np.array(p0, np.float32), np.array(p1, np.float32)
    d = p1 - p0
    L = float(np.linalg.norm(d))
    m = cylinder(r, L, segments)
    y = np.array([0, 1, 0], np.float32)
    dn = d / (L or 1)
    axis = np.cross(y, dn)
    s = float(np.linalg.norm(axis))
    c = float(np.dot(y, dn))
    if s > 1e-6:
        k = axis / s
        K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]], np.float32)
        R = np.eye(3, dtype=np.float32) + s * K + (1 - c) * (K @ K)
    else:
        R = np.eye(3, dtype=np.float32) if c > 0 else np.diag([1, -1, -1]).astype(np.float32)
    m.pos = m.pos @ R.T
    m.nrm = m.nrm @ R.T
    return m.translate(*p0)


def radial(mesh: Mesh, count: int, start_deg=0.0) -> Mesh:
    """Copies of a mesh rotated around Y (fins, bolts, barrels)."""
    return merge(*[mesh.copy().rotate(0, start_deg + 360.0 * i / count, 0) for i in range(count)])


def foundation_plate(size=PLATE_SIZE, top=PLATE_TOP, bevel=0.35) -> Mesh:
    """The standard square foundation every tower stands on (top face at y=top)."""
    return box(size, top, size, bevel=bevel).translate(0, top / 2, 0)


# --------------------------------------------------------------------------- model / glTF

class Node:
    def __init__(self, model: "Model", name: str, translation=(0, 0, 0), parent: "Node | None" = None):
        self.model, self.name, self.translation, self.parent = model, name, tuple(translation), parent
        self.prims: dict[str, Mesh] = {}
        self.children: list[Node] = []

    def add(self, mesh: Mesh, material: str) -> "Node":
        if material not in self.model.materials:
            raise KeyError(f"unknown material {material!r} in node {self.name!r}")
        self.prims[material] = self.prims[material].merged(mesh) if material in self.prims else mesh
        return self

    def node(self, name: str, translation=(0, 0, 0)) -> "Node":
        """Child node. `translation` is its pivot in WORLD coordinates; meshes passed to add() are
        also in world coordinates (save() converts them to node-local space)."""
        n = Node(self.model, name, translation, self)
        self.children.append(n)
        self.model._register(n)
        return n


class Model:
    def __init__(self, name: str):
        self.name = name
        self.materials: dict[str, dict] = {}
        self.roots: list[Node] = []
        self._names: set[str] = set()

    def _register(self, n: Node):
        if n.name in self._names:
            raise ValueError(f"duplicate node name {n.name!r}")
        self._names.add(n.name)

    def material(self, name, color, metallic=0.0, roughness=0.6, emissive=(0, 0, 0), alpha=1.0):
        """color/emissive are linear-ish RGB 0..1 (hex helpers: rgb('#2f6fd6'))."""
        self.materials[name] = dict(color=tuple(color), metallic=metallic, roughness=roughness,
                                    emissive=tuple(emissive), alpha=alpha)
        return name

    def node(self, name: str, translation=(0, 0, 0)) -> Node:
        n = Node(self, name, translation)
        self.roots.append(n)
        self._register(n)
        return n

    def stats(self):
        tris = verts = 0
        for n in self._walk():
            for m in n.prims.values():
                tris += len(m.idx)
                verts += len(m.pos)
        return dict(nodes=len(self._names), triangles=tris, vertices=verts)

    def _walk(self):
        stack = list(self.roots)
        while stack:
            n = stack.pop()
            yield n
            stack.extend(n.children)

    def save(self, path, textures=None, bindings=None):
        """Write the model as .glb.

        textures: None = automatic (on unless env FT_NO_TEXTURES=1), False = never, True = always.
        bindings: optional {material: "<set>/<name>" | {...}} overriding models/textures/bindings.json
                  for this model. Bound materials get box-projected UVs (1 tile = TEX_TILE units)
                  and external baseColor/normal textures (uri relative to the .glb).
        """
        path = Path(path)
        mat_names = list(self.materials)
        tex_plan = self._texture_plan(path, textures, bindings)
        self.textured = sorted(tex_plan)
        gl = {"asset": {"version": "2.0", "generator": "Fortress Protocol meshkit"},
              "scene": 0, "scenes": [{"name": self.name, "nodes": []}],
              "nodes": [], "meshes": [], "materials": [], "accessors": [], "bufferViews": [], "buffers": []}
        for mn in mat_names:
            m = self.materials[mn]
            mat = {"name": mn, "pbrMetallicRoughness": {
                "baseColorFactor": [*m["color"], m["alpha"]], "metallicFactor": m["metallic"],
                "roughnessFactor": m["roughness"]}, "doubleSided": False}
            if any(m["emissive"]):
                e = m["emissive"]
                peak = max(e)
                if peak > 1:
                    ext = "KHR_materials_emissive_strength"
                    used = gl.setdefault("extensionsUsed", [])
                    if ext not in used:
                        used.append(ext)
                    mat["emissiveFactor"] = [c / peak for c in e]
                    mat["extensions"] = {ext: {"emissiveStrength": peak}}
                else:
                    mat["emissiveFactor"] = list(e)
            if m["alpha"] < 1:
                mat["alphaMode"] = "BLEND"
                mat["doubleSided"] = True
            if mn in tex_plan:
                _apply_texture(gl, mat, tex_plan[mn])
            gl["materials"].append(mat)
        blob = bytearray()

        def view(data: bytes, target=None):
            while len(blob) % 4:
                blob.append(0)
            off = len(blob)
            blob.extend(data)
            bv = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
            if target:
                bv["target"] = target
            gl["bufferViews"].append(bv)
            return len(gl["bufferViews"]) - 1

        def accessor(arr, ctype, typ, target, minmax=False):
            acc = {"bufferView": view(arr.tobytes(), target), "componentType": ctype,
                   "count": int(arr.shape[0]) if typ != "SCALAR" else int(arr.size), "type": typ}
            if minmax:
                acc["min"] = arr.min(0).tolist()
                acc["max"] = arr.max(0).tolist()
            gl["accessors"].append(acc)
            return len(gl["accessors"]) - 1

        def emit(n: Node, parent_t):
            t = np.array(n.translation, np.float32)
            entry = {"name": n.name}
            local = (t - parent_t).tolist()
            if any(abs(v) > 1e-9 for v in local):
                entry["translation"] = local
            if n.prims:
                prims = []
                for mn, mesh in n.prims.items():
                    if mn in tex_plan:
                        mesh = box_project_uv(mesh, tex_plan[mn]["tile"])
                    p = (mesh.pos - t).astype(np.float32)
                    prims.append({"attributes": {
                        "POSITION": accessor(p, 5126, "VEC3", 34962, True),
                        "NORMAL": accessor(_normalize(mesh.nrm).astype(np.float32), 5126, "VEC3", 34962),
                        "TEXCOORD_0": accessor(mesh.uv.astype(np.float32), 5126, "VEC2", 34962)},
                        "indices": accessor(mesh.idx.astype(np.uint32).reshape(-1), 5125, "SCALAR", 34963),
                        "material": mat_names.index(mn)})
                gl["meshes"].append({"name": n.name, "primitives": prims})
                entry["mesh"] = len(gl["meshes"]) - 1
            gl["nodes"].append(entry)
            idx = len(gl["nodes"]) - 1
            kids = [emit(c, t) for c in n.children]
            if kids:
                gl["nodes"][idx]["children"] = kids
            return idx

        root = {"name": self.name, "children": []}
        gl["nodes"].append(root)
        for r in self.roots:
            root["children"].append(emit(r, np.zeros(3, np.float32)))
        gl["scenes"][0]["nodes"] = [0]
        while len(blob) % 4:
            blob.append(0)
        gl["buffers"].append({"byteLength": len(blob)})
        js = json.dumps(gl, separators=(",", ":")).encode()
        while len(js) % 4:
            js += b" "
        out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob))
        out += struct.pack("<II", len(js), 0x4E4F534A) + js
        out += struct.pack("<II", len(blob), 0x004E4942) + bytes(blob)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(out)
        return path


    # ------------------------------------------------------------------ textures

    def _texture_plan(self, path: Path, textures=None, bindings=None) -> dict:
        """{material: {"dif": uri, "nrm": uri|None, "white": f, "tile": f, "normal_scale": f}}."""
        if textures is False or (textures is None and os.environ.get("FT_NO_TEXTURES", "") not in ("", "0")):
            return {}
        if bindings is None:
            bindings = load_bindings().get(self.name)
            if bindings is None:
                bindings = load_bindings().get(path.stem, {})
        plan = {}
        for mn, spec in (bindings or {}).items():
            if mn.startswith("_") or mn not in self.materials or not spec:
                continue
            if isinstance(spec, str):
                spec = {"texture": spec}
            name = spec.get("texture")
            if not name:
                continue
            dif = TEXTURES_DIR / f"{name}_dif.png"
            nrm = TEXTURES_DIR / f"{name}_nrm.png"
            if not dif.exists():
                print(f"meshkit: {self.name}: texture {name}_dif.png missing - material {mn!r} left untextured",
                      file=sys.stderr)
                continue
            plan[mn] = {"texture": name, "dif": _rel_uri(dif, path.parent),
                        "nrm": _rel_uri(nrm, path.parent) if nrm.exists() else None,
                        "white": float(spec.get("white", TEX_TINT_WHITE)),
                        "tile": float(spec.get("tile", TEX_TILE)),
                        "normal_scale": float(spec.get("normal_scale", 1.0))}
        return plan


def load_bindings(file=None) -> dict:
    """Read models/textures/bindings.json ({} if absent or broken)."""
    f = Path(file) if file else BINDINGS_FILE
    try:
        return json.loads(f.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return {}
    except (OSError, ValueError) as e:
        print(f"meshkit: cannot read {f}: {e} - saving without textures", file=sys.stderr)
        return {}


def _rel_uri(target: Path, base_dir: Path) -> str:
    return Path(os.path.relpath(Path(target).resolve(), Path(base_dir).resolve())).as_posix()


# face classes for box projection: (+X, -X, +Y, -Y, +Z, -Z) -> (u axis, u sign, v axis, v sign).
# Side faces use v = -y so the image top points up (glTF v runs downwards in the image).
_BOX_AXES = ((2, -1, 1, -1), (2, 1, 1, -1), (0, 1, 2, 1), (0, 1, 2, -1), (0, 1, 1, -1), (0, -1, 1, -1))


def box_project_uv(mesh: Mesh, tile: float = TEX_TILE) -> Mesh:
    """Box (tri-planar, per triangle) UVs from world positions: each triangle is projected along the
    dominant axis of its face normal, 1 tile = `tile` units. Vertices are split only where triangles
    sharing them project along different axes; positions/normals/triangles are unchanged."""
    if len(mesh.idx) == 0:
        return mesh
    tri = mesh.pos[mesh.idx]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    deg = np.linalg.norm(fn, axis=1) < 1e-12
    if deg.any():  # degenerate triangles: fall back to the averaged vertex normal
        fn[deg] = mesh.nrm[mesh.idx[deg]].sum(1)
    ax = np.abs(fn).argmax(1)
    sign = fn[np.arange(len(fn)), ax] < 0
    cls = (ax * 2 + sign).astype(np.int64)                       # (M,)
    keys = mesh.idx.astype(np.int64) * 6 + cls[:, None]          # (M,3)
    uniq, inv = np.unique(keys.reshape(-1), return_inverse=True)
    src, c = uniq // 6, uniq % 6
    p = mesh.pos[src]
    axes = np.array(_BOX_AXES)[c]
    r = np.arange(len(src))
    u = p[r, axes[:, 0]] * axes[:, 1]
    v = p[r, axes[:, 2]] * axes[:, 3]
    uv = np.stack([u, v], 1) / float(tile)
    return Mesh(p, mesh.nrm[src], uv, inv.reshape(-1, 3))


def _apply_texture(gl: dict, mat: dict, plan: dict):
    """Add baseColorTexture/normalTexture (external images, REPEAT + mipmaps) to a glTF material."""
    def tex_index(uri):
        imgs = gl.setdefault("images", [])
        for i, im in enumerate(imgs):
            if im.get("uri") == uri:
                break
        else:
            imgs.append({"uri": uri, "mimeType": "image/png", "name": Path(uri).stem})
            i = len(imgs) - 1
        if not gl.get("samplers"):
            gl["samplers"] = [{"magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 10497}]
        texs = gl.setdefault("textures", [])
        for j, t in enumerate(texs):
            if t["source"] == i:
                return j
        texs.append({"sampler": 0, "source": i})
        return len(texs) - 1

    pbr = mat["pbrMetallicRoughness"]
    w = min(max(plan["white"], 0.0), 1.0)
    col = pbr["baseColorFactor"]
    pbr["baseColorFactor"] = [c + (1.0 - c) * w for c in col[:3]] + [col[3]]
    pbr["baseColorTexture"] = {"index": tex_index(plan["dif"])}
    if plan.get("nrm"):
        mat["normalTexture"] = {"index": tex_index(plan["nrm"])}
        if plan.get("normal_scale", 1.0) != 1.0:
            mat["normalTexture"]["scale"] = plan["normal_scale"]
    mat.setdefault("extras", {})["ft_texture"] = plan["texture"]


def rgb(hex_color: str, k: float = 1.0):
    """'#2f6fd6' -> linear RGB tuple (sRGB decoded), optionally multiplied by k."""
    h = hex_color.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    lin = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return tuple(v * k for v in lin)
