"""AlliedPrismSpire (Призматичний шпиль) — procedural 3D model generator.

Run:  python3 -I models/towers/AlliedPrismSpire.py   ->  models/glb/AlliedPrismSpire.glb

Layout (Y up, 1 unit ~ 1 m, everything inside the 6 x 6 plate):
  Base    plate with blue side stripe and corner lamps, white pedestal with a blue band,
          blue chamfered tier, tall four-sided white/chrome spire (blue band, cyan window
          slits, glowing cyan corner ribs), four curved chrome fins at the diagonals with
          blue inlays, cyan inner glow strips and lamp tips.
  Turret  claw holder (blue collar + four chrome prongs) gripping a large faceted cyan
          prism crystal, with two energy rings around it.
    FX_Muzzle01  crystal tip         FX_Glow  crystal centre
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import numpy as np  # noqa: E402

from meshkit import (PLATE_TOP, Mesh, Model, box, cylinder, foundation_plate, lathe,  # noqa: E402
                     merge, radial, rgb, sphere, torus, tube_between)

NAME = "AlliedPrismSpire"
OUT = HERE.parent / "glb" / f"{NAME}.glb"

# ----------------------------------------------------------------- dimensions
PED_TOP = 1.07          # white pedestal top
TIER_TOP = 1.52         # blue tier top
SPIRE_TOP = 5.30        # spire cap / turret pivot
CRY_BOT = 5.55          # crystal bottom point
CRY_H = 3.45            # crystal height -> tip at 9.0
CRY_R = 0.9
FIN_ANGLES = 45.0       # fins at the four diagonals (front / back / left / right on screen)
SQ = math.sqrt(0.5)


# ----------------------------------------------------------------- helpers
def _bezier(p0, p1, p2, n):
    t = np.linspace(0.0, 1.0, n + 1)[:, None]
    p0, p1, p2 = (np.array(p, np.float64) for p in (p0, p1, p2))
    return (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2


def _curve_normals(c):
    """2D normals (s, y) of a polyline, pointing to the right of travel direction."""
    d = np.gradient(c, axis=0)
    d /= np.linalg.norm(d, axis=1, keepdims=True) + 1e-9
    return np.stack([d[:, 1], -d[:, 0]], 1)


def blade(outer, inner, th):
    """Curved plate between two polylines in the (s, y) plane, thickness `th` along z.

    outer/inner: (n+1, 2) arrays of (s, y) points, outer edge must be on the +s side.
    Faces: front (+z), back (-z), outer edge, inner edge, bottom and top caps.
    Edge normals follow the curve (smooth chrome look), faces are flat.
    """
    outer, inner = np.asarray(outer, np.float64), np.asarray(inner, np.float64)
    n = len(outer) - 1
    no = _curve_normals(outer)
    if np.mean(no[:, 0]) < 0:
        no = -no
    ni = _curve_normals(inner)
    if np.mean(ni[:, 0]) > 0:
        ni = -ni
    h = th / 2
    pos, nrm, idx = [], [], []

    def quad(a, b, c, d, na, nb, nc, nd, want):
        o = len(pos)
        pos.extend([a, b, c, d])
        nrm.extend([na, nb, nc, nd])
        tri = [(o, o + 1, o + 2), (o, o + 2, o + 3)]
        fn = np.cross(np.subtract(b, a), np.subtract(c, a))
        if np.linalg.norm(fn) < 1e-12:
            fn = np.cross(np.subtract(c, a), np.subtract(d, a))
        if np.dot(fn, want) < 0:
            tri = [(t[0], t[2], t[1]) for t in tri]
        idx.extend(tri)

    P = lambda p, z: (float(p[0]), float(p[1]), z)  # noqa: E731
    for k in range(n):
        o0, o1, i0, i1 = outer[k], outer[k + 1], inner[k], inner[k + 1]
        for z, nz in ((h, 1.0), (-h, -1.0)):
            nn = (0.0, 0.0, nz)
            quad(P(o0, z), P(o1, z), P(i1, z), P(i0, z), nn, nn, nn, nn, nn)
        a, b = no[k], no[k + 1]
        quad(P(o0, h), P(o0, -h), P(o1, -h), P(o1, h), (a[0], a[1], 0), (a[0], a[1], 0),
             (b[0], b[1], 0), (b[0], b[1], 0), (a[0] + b[0], a[1] + b[1], 0))
        a, b = ni[k], ni[k + 1]
        quad(P(i0, h), P(i0, -h), P(i1, -h), P(i1, h), (a[0], a[1], 0), (a[0], a[1], 0),
             (b[0], b[1], 0), (b[0], b[1], 0), (a[0] + b[0], a[1] + b[1], 0))
    for k, sgn in ((0, -1.0), (n, 1.0)):
        o, i = outer[k], inner[k]
        if np.linalg.norm(o - i) < 1e-4:
            continue
        # cap normal: perpendicular to the cap edge, pointing away from the blade body
        mid_next = (outer[k - int(sgn)] + inner[k - int(sgn)]) / 2
        cn = np.array([-(o - i)[1], (o - i)[0]])
        if np.dot(cn, (o + i) / 2 - mid_next) < 0:
            cn = -cn
        cn /= np.linalg.norm(cn)
        nn = (cn[0], cn[1], 0.0)
        quad(P(o, h), P(o, -h), P(i, -h), P(i, h), nn, nn, nn, nn, nn)
    uv = [(0.0, 0.0)] * len(pos)
    return Mesh(pos, nrm, uv, idx)


def lerp_curve(a, b, f):
    return a + (b - a) * f


def sub_curve(c, t0, t1, n):
    """Resample polyline c on its parameter range [t0, t1] (0..1) with n segments."""
    src = np.linspace(0.0, 1.0, len(c))
    t = np.linspace(t0, t1, n + 1)
    return np.stack([np.interp(t, src, c[:, 0]), np.interp(t, src, c[:, 1])], 1)


def square_lathe(profile):
    """Four-sided faceted lathe whose corners point at the diagonals (faces face +-x, +-z).

    profile radii are face half-widths (half the side length)."""
    return lathe([(r / SQ, y) for r, y in profile], 4, start_deg=45, end_deg=405, smooth=False).flat()


def on_face(mesh, half0, y0, half1, y1, y):
    """Place a small mesh (built around the origin, thin along x) onto the +x face of a
    tapered square section, at height y; tilts it to follow the taper."""
    f = (y - y0) / (y1 - y0)
    half = half0 + (half1 - half0) * f
    tilt = math.degrees(math.atan2(half0 - half1, y1 - y0))
    return mesh.rotate(0, 0, tilt).translate(half + 0.005, y, 0)


# ----------------------------------------------------------------- model
m = Model(NAME)
m.material("plate", rgb("#c3cfdd"), metallic=0.35, roughness=0.5)
m.material("plate_in", rgb("#a7b7ca"), metallic=0.4, roughness=0.45)
m.material("white", rgb("#eef3fb"), metallic=0.15, roughness=0.32)
m.material("chrome", rgb("#9fb3c8"), metallic=0.92, roughness=0.18)
m.material("dark", rgb("#3b4a60"), metallic=0.6, roughness=0.4)
m.material("blue", rgb("#2f6fd6"), metallic=0.45, roughness=0.3)
m.material("blue_dark", rgb("#1d4f9f"), metallic=0.5, roughness=0.35)
m.material("glow", rgb("#29c4ff"), roughness=0.4, emissive=rgb("#1fb8ff", 1.0))
m.material("lamp", rgb("#bff4ff"), roughness=0.2, emissive=rgb("#7fe3ff", 2.0))
m.material("crystal", rgb("#2fb8f0"), metallic=0.0, roughness=0.3, emissive=rgb("#1fa8e8", 1.0))
m.material("crystal_lite", rgb("#7fe3ff"), metallic=0.0, roughness=0.25, emissive=rgb("#4fd8ff", 1.1))
m.material("crystal_core", rgb("#e8fcff"), roughness=0.1, emissive=rgb("#b8f2ff", 2.2))
m.material("ring", rgb("#29c4ff"), roughness=0.4, emissive=rgb("#1fb0ff", 1.3))
m.material("marker", (1.0, 0.0, 1.0), emissive=(1.0, 0.0, 1.0))

base = m.node("Base")

# --- foundation plate --------------------------------------------------------
base.add(foundation_plate(size=5.96), "plate")
base.add(box(6.0, 0.07, 6.0, bevel=0.36).translate(0, 0.19, 0), "blue")           # side stripe
base.add(box(5.0, 0.03, 5.0, bevel=0.3).translate(0, PLATE_TOP + 0.015, 0), "plate_in")
base.add(box(4.4, 0.012, 4.4, bevel=0.26).translate(0, PLATE_TOP + 0.036, 0), "plate")
for sx, sz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
    x, z = 2.62 * sx, 2.62 * sz
    base.add(cylinder(0.16, 0.06, 12).translate(x, PLATE_TOP, z), "dark")
    base.add(sphere(0.14, 10, 6).scale(1, 0.7, 1).translate(x, PLATE_TOP + 0.07, z), "lamp")
# small dark seams on the plate edge (mid sides)
for a in range(4):
    for off in (-1.5, 0.0, 1.5):
        base.add(box(0.02, 0.2, 0.03).translate(2.985, 0.17, off).rotate(0, 90 * a, 0), "dark")

# --- pedestal ----------------------------------------------------------------
y0 = PLATE_TOP + 0.04
base.add(square_lathe([(0, y0), (1.62, y0), (1.62, PED_TOP - 0.08), (1.54, PED_TOP), (0, PED_TOP)]), "white")
# blue band and chrome lip
base.add(square_lathe([(0, 0.8), (1.645, 0.8), (1.645, 0.92), (0, 0.92)]), "blue")
base.add(square_lathe([(0, 0.46), (1.64, 0.46), (1.64, 0.5), (0, 0.5)]), "chrome")
# dark vents on pedestal sides
vent = merge(*[box(0.04, 0.16, 0.34).translate(1.625, 0.65, zz) for zz in (-0.95, -0.45, 0.45, 0.95)])
base.add(radial(vent, 4), "dark")

# --- blue tier (chamfered top) -------------------------------------------------
base.add(square_lathe([(0, PED_TOP), (1.18, PED_TOP), (1.18, TIER_TOP - 0.12), (1.04, TIER_TOP), (0, TIER_TOP)]), "blue")
base.add(square_lathe([(0, PED_TOP), (1.21, PED_TOP), (1.21, PED_TOP + 0.07), (0, PED_TOP + 0.07)]), "blue_dark")

# --- spire -------------------------------------------------------------------
# face half-widths along the height (four-sided, corners at the diagonals)
S0, S1 = (0.84, TIER_TOP), (0.58, 3.15)      # lower shaft
S2, S3 = (0.52, 3.5), (0.30, 5.05)           # upper shaft
base.add(square_lathe([(0, S0[1]), S0, S1, (0, S1[1])]), "white")
base.add(square_lathe([(0, S2[1]), S2, S3, (0.36, 5.12), (0.36, 5.22), (0, 5.22)]), "white")
# blue band between the shafts, chrome rims
base.add(square_lathe([(0, 3.13), (0.61, 3.13), (0.62, 3.2), (0.6, 3.45), (0.55, 3.52), (0, 3.52)]), "blue")
base.add(square_lathe([(0, 3.06), (0.63, 3.08), (0.63, 3.14), (0, 3.14)]), "chrome")
base.add(square_lathe([(0, 1.5), (0.9, 1.5), (0.88, 1.66), (0, 1.66)]), "chrome")
# chrome neck + top collar under the claw
base.add(lathe([(0, 5.1), (0.42, 5.1), (0.46, 5.18), (0.4, SPIRE_TOP), (0, SPIRE_TOP)], 8, start_deg=22.5,
               end_deg=382.5, smooth=False).flat(), "chrome")

# cyan window slits on all four faces
slit = box(0.03, 0.11, 0.26)
slits = merge(on_face(slit.copy(), S0[0], S0[1], S1[0], S1[1], 2.05).translate(0, 0, -0.18),
              on_face(slit.copy(), S0[0], S0[1], S1[0], S1[1], 2.32).translate(0, 0, -0.18),
              on_face(box(0.03, 0.11, 0.18), S0[0], S0[1], S1[0], S1[1], 2.32).translate(0, 0, 0.22),
              on_face(box(0.03, 0.18, 0.16), S2[0], S2[1], S3[0], S3[1], 4.15).translate(0, 0, -0.08),
              on_face(box(0.03, 0.08, 0.16), S2[0], S2[1], S3[0], S3[1], 4.45).translate(0, 0, -0.08))
base.add(radial(slits, 4), "glow")
# dark panel seams
seam = merge(on_face(box(0.02, 0.025, 1.05), S0[0], S0[1], S1[0], S1[1], 2.7),
             on_face(box(0.02, 0.025, 0.7), S2[0], S2[1], S3[0], S3[1], 4.7))
base.add(radial(seam, 4), "dark")

# glowing cyan ribs along the four spire corners
rib = merge(tube_between((S0[0] / SQ + 0.02, S0[1] + 0.15, 0), (S1[0] / SQ + 0.02, S1[1] - 0.05, 0), 0.035, 6),
            tube_between((S2[0] / SQ + 0.02, S2[1] + 0.05, 0), (S3[0] / SQ + 0.02, S3[1], 0), 0.03, 6))
base.add(radial(rib, 4, start_deg=FIN_ANGLES), "glow")

# --- four curved chrome fins ---------------------------------------------------
N = 22
outer = _bezier((2.88, PLATE_TOP), (2.82, 4.6), (1.14, 7.35), N)
inner = _bezier((2.02, PLATE_TOP), (1.62, 4.2), (1.02, 6.95), N)
inner[-1] = outer[-1] + (inner[-1] - outer[-1]) * 0.35            # sharp-ish tip
TH = 0.2
fin = blade(outer, inner, TH)
# blue inlay strip near the outer edge (both faces)
inl_o = sub_curve(lerp_curve(outer, inner, 0.14), 0.05, 0.9, N)
inl_i = sub_curve(lerp_curve(outer, inner, 0.42), 0.05, 0.9, N)
inlay = blade(inl_o, inl_i, TH + 0.04)
# cyan glow strip near the inner edge
gl_o = sub_curve(lerp_curve(outer, inner, 0.62), 0.1, 0.86, N)
gl_i = sub_curve(lerp_curve(outer, inner, 0.74), 0.1, 0.86, N)
gstrip = blade(gl_o, gl_i, TH + 0.05)
# foot block anchoring each fin into the plate / pedestal corner
foot = box(0.95, 0.42, 0.34).translate(2.42, PLATE_TOP + 0.21, 0)
foot_band = box(0.97, 0.06, 0.36).translate(2.42, PLATE_TOP + 0.3, 0)
# strut tying the fin to the spire at the blue band height
k = int(N * 0.45)
strut = tube_between((S1[0] / SQ - 0.05, 3.32, 0), (float(inner[k][0]) + 0.05, 3.32, 0), 0.07, 8)
strut_ring = cylinder(0.11, 0.12, 8).rotate(0, 0, 90).translate(float(inner[k][0]) + 0.06, 3.32, 0)
# lamp at the tip
tip = outer[-1]
lamp_holder = cylinder(0.09, 0.14, 8).translate(float(tip[0]) - 0.02, float(tip[1]) - 0.08, 0)
lamp = sphere(0.12, 10, 8).translate(float(tip[0]) - 0.02, float(tip[1]) + 0.08, 0)

base.add(radial(fin, 4, FIN_ANGLES), "white")
base.add(radial(inlay, 4, FIN_ANGLES), "blue")
base.add(radial(gstrip, 4, FIN_ANGLES), "glow")
base.add(radial(merge(foot, strut_ring, lamp_holder), 4, FIN_ANGLES), "chrome")
base.add(radial(merge(foot_band), 4, FIN_ANGLES), "blue")
base.add(radial(strut, 4, FIN_ANGLES), "chrome")
base.add(radial(lamp, 4, FIN_ANGLES), "lamp")

# ----------------------------------------------------------------- Turret: claw + crystal
turret = base.node("Turret", (0, SPIRE_TOP, 0))
# blue collar
turret.add(lathe([(0, SPIRE_TOP), (0.5, SPIRE_TOP), (0.6, SPIRE_TOP + 0.1), (0.56, SPIRE_TOP + 0.22),
                  (0.3, SPIRE_TOP + 0.3), (0, SPIRE_TOP + 0.3)], 8, start_deg=22.5, end_deg=382.5,
                 smooth=False).flat(), "blue")
turret.add(torus(0.6, 0.035, 24, 6).translate(0, SPIRE_TOP + 0.1, 0), "chrome")
# four claw prongs gripping the crystal (at the diagonals, like the fins)
NC = 10
c_out = _bezier((0.6, SPIRE_TOP + 0.12), (1.12, SPIRE_TOP + 0.7), (0.86, SPIRE_TOP + 1.75), NC)
c_in = _bezier((0.36, SPIRE_TOP + 0.24), (0.8, SPIRE_TOP + 0.75), (0.76, SPIRE_TOP + 1.6), NC)
claw = blade(c_out, c_in, 0.14)
claw_in = blade(sub_curve(lerp_curve(c_out, c_in, 0.2), 0.15, 0.85, NC),
                sub_curve(lerp_curve(c_out, c_in, 0.55), 0.15, 0.85, NC), 0.17)
turret.add(radial(claw, 4, FIN_ANGLES), "chrome")
turret.add(radial(claw_in, 4, FIN_ANGLES), "blue")
turret.add(radial(sphere(0.06, 8, 6).translate(float(c_out[-1][0]) - 0.04, float(c_out[-1][1]), 0), 4, FIN_ANGLES),
           "lamp")

# the prism crystal (eight facets, hard edges)
cy = CRY_BOT
prof = [(0.0, cy), (CRY_R * 0.62, cy + CRY_H * 0.28), (CRY_R, cy + CRY_H * 0.5),
        (CRY_R * 0.86, cy + CRY_H * 0.6), (0.0, cy + CRY_H)]
# alternate facet columns get a lighter / brighter material (two-tone prism look)
for j in range(8):
    col = lathe(prof, 1, start_deg=45 * j, end_deg=45 * (j + 1), smooth=False).flat()
    turret.add(col, "crystal_lite" if j % 2 else "crystal")
# glowing seed crystal inside the claw, under the main prism
turret.add(lathe([(0, SPIRE_TOP + 0.25), (0.34, SPIRE_TOP + 0.4), (0, CRY_BOT + 0.5)], 6,
                 smooth=False).flat(), "crystal_core")

# energy rings: a solid one around the claw, a dashed one high around the crystal
turret.add(torus(1.32, 0.045, 40, 6).translate(0, SPIRE_TOP + 0.62, 0), "ring")
dash = merge(*[lathe([(1.6 + 0.034 * math.cos(2 * math.pi * i / 6), 0.034 * math.sin(2 * math.pi * i / 6))
                      for i in range(7)], 3, start_deg=a, end_deg=a + 18) for a in range(0, 360, 30)])
turret.add(dash.translate(0, 8.05, 0), "ring")

# FX bones
cx = CRY_BOT + CRY_H * 0.5
turret.node("FX_Muzzle01", (0, CRY_BOT + CRY_H, 0)).add(box(0.05, 0.05, 0.05).translate(0, CRY_BOT + CRY_H, 0), "marker")
turret.node("FX_Glow", (0, cx, 0)).add(box(0.05, 0.05, 0.05).translate(0, cx, 0), "marker")

# ----------------------------------------------------------------- checks + save
all_pos = np.vstack([p.pos for n in m._walk() for p in n.prims.values()])
ext = np.abs(all_pos[:, [0, 2]]).max()
assert ext <= 3.0 + 1e-3, f"model leaves the plate: {ext:.3f}"
st = m.stats()
assert 3000 <= st["triangles"] <= 12000, st
m.save(OUT)
print(f"{OUT}  {st}  extent={ext:.3f}  height={all_pos[:, 1].max():.2f}")
