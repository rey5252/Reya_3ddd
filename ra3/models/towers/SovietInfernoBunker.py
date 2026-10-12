"""SovietInfernoBunker — Бункер «Пекло» (Soviet, ~3.5 m).

Low sloped armored bunker on a hazard-striped plinth: olive armor with a red upper band,
rivets and plate seams, six glowing firing slits, a roof with hazard border, vent grille,
exhaust stack and a rotating cupola (node "Turret") with a red star. Two red fuel tanks
with hazard bands stand behind, piped into the bunker. Four flamethrower nozzles (one per
side, 360 deg coverage) with tiny pilot flames; FX_Muzzle01..04 at the nozzle tips,
FX_Glow inside the bunker at firing-slit height.

Run:  python3 -I models/towers/SovietInfernoBunker.py  ->  models/glb/SovietInfernoBunker.glb
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import numpy as np  # noqa: E402

from meshkit import (PLATE_TOP, Mesh, Model, box, cylinder, disc, extrude, foundation_plate,  # noqa: E402
                     lathe, merge, rgb, sphere, torus, tube_between)

NAME = "SovietInfernoBunker"
OUT = HERE.parent / "glb" / f"{NAME}.glb"

# ----------------------------------------------------------------------------- dimensions
PLINTH_H = 0.40
PLINTH_HALF = 2.15
Y0 = PLATE_TOP + PLINTH_H          # wall base (plinth top)  0.75
Y_BAND = 1.90                      # olive -> red band
Y1 = 2.35                          # wall top
HB, HT = 1.95, 1.50                # wall half-size at base / top
K = (HB - HT) / (Y1 - Y0)
ALPHA = math.degrees(math.atan(K))  # wall slope from vertical
BAND_OUT = 0.03                    # red band stands proud of the olive armor
ROOF_Y = Y1 + 0.10
SQ2 = math.sqrt(2.0)

CUPOLA = (-0.35, 0.0)
STACK = (-1.05, 0.55)
GRILLE = (0.55, -0.75)
TANKS = [((0.90, -2.55), 0.40, 2.60), ((2.30, -2.30), 0.45, 2.75)]
NOZZLE_Y = 1.12
NOZZLE_TIP = 2.70
# side angle (0 -> +Z face, 90 -> +X, 180 -> -Z, 270 -> -X) and lateral offset of the nozzle
NOZZLES = [(0, 0.0), (90, 0.0), (180, 0.4), (270, 0.0)]


def h(y):
    """Half-size of the olive armor at height y."""
    return HB - (y - Y0) * K


# ----------------------------------------------------------------------------- helpers
def quad(a, b, c, d, n):
    a, b, c, d = (np.asarray(p, np.float32) for p in (a, b, c, d))
    n = np.asarray(n, np.float32)
    idx = [(0, 1, 2), (0, 2, 3)]
    if np.dot(np.cross(b - a, c - a), n) < 0:
        idx = [(0, 2, 1), (0, 3, 2)]
    return Mesh([a, b, c, d], [n] * 4, [(0, 0), (1, 0), (1, 1), (0, 1)], idx)


def haz_strip(P, D, W, N, L, period=0.26):
    """Diagonal hazard stripes on a flat strip: origin P, along unit D for length L,
    across W (vector, its length = strip width), facing N. Returns (yellow, black)."""
    P, D, W, N = (np.asarray(v, np.float32) for v in (P, D, W, N))
    blk = quad(P + N * 0.002, P + D * L + N * 0.002, P + D * L + W + N * 0.002, P + W + N * 0.002, N)
    skew = float(np.linalg.norm(W))
    half = period / 2
    yel = []
    s = -skew - period
    off = N * 0.005
    while s < L:
        b0, b1 = np.clip([s, s + half], 0, L)
        t0, t1 = np.clip([s + skew, s + half + skew], 0, L)
        if (b1 - b0) + (t1 - t0) > 1e-4:
            yel.append(quad(P + D * b0 + off, P + D * b1 + off, P + D * t1 + W + off, P + D * t0 + W + off, N))
        s += period
    return merge(*yel), blk


def haz_ring(cx, cz, r, y0, y1, n=14, sub=2):
    """Diagonal hazard band around a vertical cylinder. Returns (yellow, black)."""
    blk = cylinder(r + 0.004, y1 - y0, n * 2, caps=False).translate(cx, y0, cz)
    rr = r + 0.009
    skew = (y1 - y0) / rr
    w = math.pi / n
    pos, nrm, uv, idx = [], [], [], []
    for i in range(n):
        a0 = 2 * math.pi * i / n
        base = len(pos)
        for j in range(sub + 1):
            a = a0 + w * j / sub
            for (ang, y) in ((a, y0), (a + skew, y1)):
                c, s_ = math.cos(ang), math.sin(ang)
                pos.append((cx + rr * c, y, cz + rr * s_))
                nrm.append((c, 0, s_))
                uv.append((j / sub, 0 if y == y0 else 1))
        for j in range(sub):
            p0, p1, p2, p3 = base + 2 * j, base + 2 * j + 1, base + 2 * j + 2, base + 2 * j + 3
            idx += [(p0, p1, p2), (p2, p1, p3)]
    yel = Mesh(pos, nrm, uv, idx)
    # orient outward
    tri = yel.pos[yel.idx]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    bad = (fn * yel.nrm[yel.idx].mean(1)).sum(1) < 0
    yel.idx[bad] = yel.idx[bad][:, ::-1]
    return yel, blk


def frustum(profile):
    """Square-section lathe: profile [(half_size, y), ...]."""
    return lathe([(s * SQ2, y) for s, y in profile], 4, start_deg=45, end_deg=405, smooth=False)


def star_pts(ro, ri, n=5, rot=90.0):
    pts = []
    for i in range(2 * n):
        r = ro if i % 2 == 0 else ri
        a = math.radians(rot + 180.0 * i / n)
        pts.append((r * math.cos(a), r * math.sin(a)))
    return pts


def rivet(r=0.045):
    return lathe([(r, 0.0), (r * 0.82, r * 0.5), (0.0, r * 0.72)], 6)


def to_face(mesh, th, u, y, extra=0.0):
    """Mesh built facing local +Z (x along the face, y up the slope) -> sloped wall of side th."""
    return mesh.rotate(-ALPHA, 0, 0).translate(u, y, h(y) + extra).rotate(0, th, 0)


def side_point(th, u, y, z):
    t = math.radians(th)
    return (u * math.cos(t) + z * math.sin(t), y, -u * math.sin(t) + z * math.cos(t))


def facing_z(mesh):
    """Turn a +Y-facing mesh (disc, rivet, star) to face +Z."""
    return mesh.rotate(90, 0, 0)


# ----------------------------------------------------------------------------- model
m = Model(NAME)
m.material("concrete", rgb("#7c796d"), metallic=0.0, roughness=0.92)
m.material("concrete_lt", rgb("#8d8a7b"), metallic=0.0, roughness=0.88)
m.material("groove", rgb("#4c4a42"), metallic=0.0, roughness=0.9)
m.material("olive", rgb("#5b5f4a"), metallic=0.45, roughness=0.55)
m.material("olive_lt", rgb("#73775d"), metallic=0.4, roughness=0.6)
m.material("olive_hi", rgb("#8a8f70"), metallic=0.4, roughness=0.55)
m.material("red", rgb("#c62b1f"), metallic=0.35, roughness=0.5)
m.material("red_dk", rgb("#7a1810"), metallic=0.35, roughness=0.55)
m.material("haz_y", rgb("#e8b923"), metallic=0.1, roughness=0.6)
m.material("haz_k", rgb("#1e1d1a"), metallic=0.1, roughness=0.7)
m.material("steel", rgb("#8a8d84"), metallic=0.85, roughness=0.35)
m.material("steel_dk", rgb("#2b2c29"), metallic=0.7, roughness=0.5)
m.material("rivet", rgb("#a9a99c"), metallic=0.75, roughness=0.35)
m.material("slit_frame", rgb("#1a1a16"), metallic=0.5, roughness=0.6)
m.material("glow", rgb("#ff4a10"), roughness=0.4, emissive=rgb("#ff3c0a", 1.0))
m.material("glow_hot", rgb("#ff8a20"), roughness=0.4, emissive=rgb("#ff7a14", 1.15))
m.material("flame", rgb("#ff7a1a"), roughness=0.3, emissive=rgb("#ff5a10", 1.6))
m.material("flame_core", rgb("#ffd060"), roughness=0.3, emissive=rgb("#ffc040", 1.8))
m.material("cream", rgb("#f0e6c8"), metallic=0.1, roughness=0.6)
m.material("scorch", rgb("#1a120c"), roughness=1.0, alpha=0.32)
m.material("fx", rgb("#ff6a1a"), emissive=rgb("#ff6a1a", 1.0))

base = m.node("Base")

# --- foundation plate + hazard border on its top --------------------------------------
base.add(foundation_plate(), "concrete")
yt = PLATE_TOP + 0.001
for th in (0, 90, 180, 270):
    y_, k_ = haz_strip((-2.64, yt, 2.80), (1, 0, 0), (0, 0, -0.16), (0, 1, 0), 5.44)
    base.add(y_.rotate(0, th, 0), "haz_y")
    base.add(k_.rotate(0, th, 0), "haz_k")
    # groove line inside the border
    base.add(box(5.0, 0.01, 0.04).translate(0, PLATE_TOP + 0.002, 2.52).rotate(0, th, 0), "groove")
for sx, sz in ((1, 1), (-1, 1), (-1, -1)):
    base.add(rivet(0.06).translate(2.45 * sx, PLATE_TOP, 2.45 * sz), "rivet")

# --- plinth -------------------------------------------------------------------------------
base.add(box(2 * PLINTH_HALF, PLINTH_H, 2 * PLINTH_HALF, bevel=0.4).translate(0, PLATE_TOP + PLINTH_H / 2, 0),
         "concrete_lt")
base.add(box(2 * PLINTH_HALF - 0.1, 0.03, 2 * PLINTH_HALF - 0.1, bevel=0.36).translate(0, Y0 + 0.0, 0), "steel_dk")
ys0, ys1 = PLATE_TOP + 0.09, PLATE_TOP + 0.29
c_ = PLINTH_HALF - 0.4
for th in (0, 90, 180, 270):
    y_, k_ = haz_strip((-c_, ys0, PLINTH_HALF), (1, 0, 0), (0, ys1 - ys0, 0), (0, 0, 1), 2 * c_, 0.22)
    base.add(y_.rotate(0, th, 0), "haz_y")
    base.add(k_.rotate(0, th, 0), "haz_k")
    d = np.array([1, 0, -1], np.float32) / SQ2
    n = np.array([1, 0, 1], np.float32) / SQ2
    y_, k_ = haz_strip((c_, ys0, PLINTH_HALF), d, (0, ys1 - ys0, 0), n, 0.4 * SQ2, 0.22)
    base.add(y_.rotate(0, th, 0), "haz_y")
    base.add(k_.rotate(0, th, 0), "haz_k")

# --- armored hull ---------------------------------------------------------------------------
base.add(frustum([(HB, Y0), (h(Y_BAND), Y_BAND)]), "olive")
base.add(frustum([(h(Y_BAND), Y_BAND), (h(Y_BAND) + BAND_OUT, Y_BAND), (HT + BAND_OUT, Y1), (0, Y1)]), "red")
# dark trim at the band's lower edge
base.add(frustum([(h(Y_BAND) + BAND_OUT + 0.012, Y_BAND - 0.001), (h(Y_BAND + 0.05) + BAND_OUT + 0.012, Y_BAND + 0.05)]),
         "red_dk")
# roof slab
base.add(box(3.18, ROOF_Y - Y1, 3.18, bevel=0.14).translate(0, (Y1 + ROOF_Y) / 2, 0), "olive_lt")
base.add(frustum([(1.59 + 0.004, ROOF_Y - 0.004), (1.59 + 0.004, ROOF_Y - 0.0)]), "olive_hi")

# corner ribs (armor plate edges)
for th in (45, 135, 225, 315):
    tilt = math.atan(K * SQ2)  # diagonal edge leans more than the faces
    rib = box(0.07, (Y_BAND - Y0) / math.cos(tilt), 0.07)
    rib.rotate(-math.degrees(tilt), 0, 0)
    ym = (Y0 + Y_BAND) / 2
    rib.translate(0, ym, h(ym) * SQ2 + 0.005)
    base.add(rib.rotate(0, th, 0), "olive_hi")

# per-face details: rivets, seams, slits, emblem, door
riv = rivet()
SLIT_FACES = (0, 90)
SLIT_U = (-1.0, 0.0, 1.0)
SLIT_Y = 1.56
for th, nu in NOZZLES:
    # lower rivet row
    yb = 0.90
    for i in range(11):
        u = -1.7 + i * 0.34
        if abs(u - nu) < 0.38 or (th == 270 and abs(u + 0.85) < 0.42):
            continue
        base.add(to_face(facing_z(riv.copy()), th, u, yb), "rivet")
    # upper rivet row on the red band
    yr = Y_BAND + 0.075
    for i in range(9):
        u = -1.36 + i * 0.34
        if th in (90, 270) and abs(u - 0.75) < 0.22:
            continue
        if th == 180 and abs(u + 0.9) < 0.22:
            continue
        base.add(to_face(facing_z(riv.copy()), th, u, yr, BAND_OUT), "rivet")
    # plate seams
    seam_u = (-0.52, 0.52) if th != 180 else (-0.35, 1.15)
    sl = (Y_BAND - 0.98) / math.cos(math.radians(ALPHA))
    for u in seam_u:
        base.add(to_face(box(0.03, sl, 0.024), th, u, (0.98 + Y_BAND) / 2), "slit_frame")
    # horizontal seam just above the lower rivet row
    base.add(to_face(box(2 * h(0.98) - 0.1, 0.025, 0.024), th, 0.0, 0.98), "slit_frame")

    if th in SLIT_FACES:
        for u in SLIT_U:
            fr = merge(
                box(0.76, 0.07, 0.10).translate(0, 0.165, 0.0),     # top bar
                box(0.76, 0.07, 0.10).translate(0, -0.165, 0.0),    # bottom bar
                box(0.07, 0.40, 0.10).translate(0.345, 0, 0.0),     # sides
                box(0.07, 0.40, 0.10).translate(-0.345, 0, 0.0),
                box(0.62, 0.26, 0.04).translate(0, 0, -0.01),       # dark back
            )
            base.add(to_face(fr, th, u, SLIT_Y), "slit_frame")
            hood = box(0.86, 0.05, 0.17).translate(0, 0.225, 0.04)
            base.add(to_face(hood, th, u, SLIT_Y), "olive_hi")
            base.add(to_face(box(0.62, 0.11, 0.02).translate(0, 0.06, 0.012), th, u, SLIT_Y), "glow_hot")
            base.add(to_face(box(0.62, 0.15, 0.02).translate(0, -0.06, 0.012), th, u, SLIT_Y), "glow")
    if th in (90, 270):
        yc = Y_BAND + 0.25
        base.add(to_face(facing_z(disc(0.15, 16)).translate(0, 0, 0.004), th, 0.75, yc, BAND_OUT), "cream")
        st = facing_z(extrude(star_pts(0.11, 0.045, rot=90), 0.012)).translate(0, 0, 0.004)
        base.add(to_face(st, th, 0.75, yc, BAND_OUT), "red")
        base.add(to_face(facing_z(torus(0.15, 0.012, 16, 4)).translate(0, 0, 0.004), th, 0.75, yc, BAND_OUT),
                 "slit_frame")
    if th == 270:
        # rear hatch
        dy = 1.20
        dl = 0.86 / math.cos(math.radians(ALPHA))
        base.add(to_face(box(0.66, dl + 0.08, 0.05), th, -0.85, dy), "slit_frame")
        base.add(to_face(box(0.56, dl - 0.04, 0.05).translate(0, 0, 0.02), th, -0.85, dy), "olive_lt")
        base.add(to_face(box(0.06, 0.2, 0.05).translate(0.18, 0, 0.06), th, -0.85, dy), "steel")
        for yy in (-0.28, 0.28):
            base.add(to_face(box(0.12, 0.08, 0.05).translate(-0.3, yy, 0.03), th, -0.85, dy), "steel_dk")

# --- roof: hazard border, vent grille, exhaust stack ------------------------------------
yr_ = ROOF_Y + 0.001
for th in (0, 90, 180, 270):
    y_, k_ = haz_strip((-1.30, yr_, 1.44), (1, 0, 0), (0, 0, -0.13), (0, 1, 0), 2.74, 0.2)
    base.add(y_.rotate(0, th, 0), "haz_y")
    base.add(k_.rotate(0, th, 0), "haz_k")
gx, gz = GRILLE
base.add(box(0.76, 0.05, 0.76).translate(gx, ROOF_Y + 0.025, gz), "steel_dk")
base.add(box(0.66, 0.03, 0.66).translate(gx, ROOF_Y + 0.04, gz), "slit_frame")
for i in range(5):
    base.add(box(0.62, 0.04, 0.05).translate(gx, ROOF_Y + 0.07, gz - 0.24 + i * 0.12), "olive_lt")
sx_, sz_ = STACK
base.add(cylinder(0.21, 0.10, 14).translate(sx_, ROOF_Y, sz_), "steel_dk")
base.add(cylinder(0.14, 0.80, 14).translate(sx_, ROOF_Y + 0.10, sz_), "steel")
base.add(cylinder(0.165, 0.07, 14, caps=False).translate(sx_, ROOF_Y + 0.55, sz_), "steel_dk")
base.add(lathe([(0.165, ROOF_Y + 0.84), (0.165, ROOF_Y + 0.92), (0.12, ROOF_Y + 0.92), (0.12, ROOF_Y + 0.86)],
               14).translate(sx_, 0, sz_), "steel_dk")
base.add(disc(0.12, 14).translate(sx_, ROOF_Y + 0.86, sz_), "slit_frame")

# --- fuel tanks + pipes ------------------------------------------------------------------
for (cx, cz), r, top in TANKS:
    base.add(cylinder(r + 0.05, 0.10, 20).translate(cx, PLATE_TOP, cz), "steel_dk")
    body = lathe([(r, PLATE_TOP + 0.10), (r, top - 0.2), (r * 0.86, top - 0.08), (r * 0.5, top - 0.015), (0, top)], 20)
    base.add(body.translate(cx, 0, cz), "red")
    for yy in (PLATE_TOP + 0.32, top - 0.42):
        base.add(cylinder(r + 0.016, 0.06, 20, caps=False).translate(cx, yy, cz), "steel_dk")
    hy = PLATE_TOP + 0.1 + 0.52 * (top - PLATE_TOP - 0.1)
    y_, k_ = haz_ring(cx, cz, r, hy - 0.13, hy + 0.13, 12)
    base.add(y_, "haz_y")
    base.add(k_, "haz_k")
    base.add(torus(r * 0.62, 0.022, 18, 4).translate(cx, top - 0.035, cz), "red_dk")
    base.add(box(0.1, 0.07, 0.1).translate(cx, top + 0.01, cz), "steel_dk")
    for i in range(6):
        a = math.radians(15 + 60 * i)
        rv = facing_z(rivet(0.035)).rotate(0, math.degrees(math.atan2(math.cos(a), math.sin(a))), 0)
        base.add(rv.translate(cx + r * math.cos(a), top - 0.3, cz + r * math.sin(a)), "rivet")

(t1x, t1z), t1r, t1top = TANKS[0]
(t2x, t2z), t2r, t2top = TANKS[1]
# tank 1 -> rear wall (red band)
py = 2.10
wall_z = -(h(py) + BAND_OUT)
base.add(tube_between((t1x, py, t1z + t1r - 0.05), (t1x, py, wall_z + 0.02), 0.07, 10), "steel")
base.add(tube_between((t1x, py, wall_z - 0.06), (t1x, py, wall_z + 0.02), 0.12, 12), "steel_dk")
base.add(tube_between((t1x, py, t1z + t1r - 0.02), (t1x, py, t1z + t1r + 0.05), 0.11, 12), "steel_dk")
# tank 2 -> roof, over the top
A = (t2x, t2top - 0.02, t2z)
B = (t2x, 3.02, t2z)
C = (1.12, 3.02, -1.12)
D = (1.12, ROOF_Y, -1.12)
for p, q in ((A, B), (B, C), (C, D)):
    base.add(tube_between(p, q, 0.07, 10), "steel")
for p in (B, C):
    base.add(sphere(0.085, 10, 6).translate(*p), "steel_dk")
base.add(cylinder(0.14, 0.06, 12).translate(D[0], ROOF_Y, D[2]), "steel_dk")
# tank 1 <-> tank 2 manifold with a valve wheel
base.add(tube_between((t1x, 1.0, t1z), (t2x, 1.0, t2z), 0.06, 10), "steel")
mx, mz = (t1x + t2x) / 2, (t1z + t2z) / 2
ang = math.degrees(math.atan2(-(t2z - t1z), t2x - t1x))
base.add(torus(0.11, 0.018, 14, 4).rotate(0, 0, 90).rotate(0, ang, 0).translate(mx, 1.0, mz), "red")
base.add(box(0.08, 0.16, 0.08).translate(mx, 1.0 + 0.05, mz), "steel_dk")

# --- flamethrower nozzles ------------------------------------------------------------------
for i, (th, nu) in enumerate(NOZZLES):
    hz = h(NOZZLE_Y)
    y = NOZZLE_Y
    yt_ = y - 0.04
    parts_dk, parts_st, parts_red, parts_ol = [], [], [], []
    parts_ol.append(box(0.70, 0.58, 0.36, bevel=0.08).translate(nu, y, hz + 0.04))
    parts_dk.append(box(0.74, 0.06, 0.40, bevel=0.08).translate(nu, y + 0.30, hz + 0.04))
    parts_st.append(sphere(0.21, 12, 8).translate(nu, y, hz + 0.24))
    parts_st.append(tube_between((nu, y, hz + 0.3), (nu, yt_, NOZZLE_TIP - 0.12), 0.10, 10))
    parts_red.append(tube_between((nu, y, hz + 0.40), (nu, y - 0.003, hz + 0.54), 0.118, 10))
    zf = (hz + 0.52 + NOZZLE_TIP) / 2
    for dz in (-0.07, 0.0, 0.07):
        parts_dk.append(tube_between((nu, y - 0.02, zf + dz - 0.015), (nu, y - 0.02, zf + dz + 0.015), 0.12, 10))
    parts_dk.append(tube_between((nu, yt_, NOZZLE_TIP - 0.16), (nu, yt_, NOZZLE_TIP), 0.15, 12))
    parts_dk.append(tube_between((nu, yt_ - 0.11, NOZZLE_TIP - 0.45), (nu, yt_ - 0.11, NOZZLE_TIP - 0.02), 0.022, 6))
    tip = disc(0.11, 12).rotate(90, 0, 0).translate(nu, yt_, NOZZLE_TIP + 0.002)
    for lst, mat in ((parts_dk, "steel_dk"), (parts_st, "steel"), (parts_red, "red"), (parts_ol, "olive")):
        base.add(merge(*lst).rotate(0, th, 0), mat)
    base.add(tip.rotate(0, th, 0), "slit_frame")
    # pilot flame: small glowing tongue on the muzzle axis
    fl = lathe([(0.0, 0.0), (0.07, 0.04), (0.06, 0.11), (0.0, 0.24)], 10).rotate(90, 0, 0)
    core = lathe([(0.0, 0.0), (0.035, 0.03), (0.0, 0.14)], 8).rotate(90, 0, 0)
    base.add(fl.translate(nu, yt_, NOZZLE_TIP + 0.005).rotate(0, th, 0), "flame")
    base.add(core.translate(nu, yt_, NOZZLE_TIP + 0.01).rotate(0, th, 0), "flame_core")
    # scorch decal on the plate
    sc = disc(0.34, 16).scale(0.85, 1, 1).translate(nu, PLATE_TOP + 0.006, 2.62)
    base.add(sc.rotate(0, th, 0), "scorch")
    p = side_point(th, nu, yt_, NOZZLE_TIP + 0.04)
    fx = base.node(f"FX_Muzzle{i + 1:02d}", translation=p)
    fx.add(box(0.05, 0.05, 0.05).translate(*p), "fx")

# FX_Glow: inside the bunker at firing-slit height (lights all slits from within)
gp = (0.0, SLIT_Y, 0.0)
base.node("FX_Glow", translation=gp).add(box(0.05, 0.05, 0.05).translate(*gp), "fx")

# --- cupola (Turret) ----------------------------------------------------------------------
cx, cz = CUPOLA
tur = base.node("Turret", translation=(cx, ROOF_Y, cz))
y = ROOF_Y
tur.add(cylinder(0.56, 0.08, 20).translate(cx, y, cz), "steel_dk")
tur.add(lathe([(0.5, y + 0.08), (0.5, y + 0.16)], 20).translate(cx, 0, cz), "olive")
tur.add(cylinder(0.508, 0.09, 20, caps=False).translate(cx, y + 0.16, cz), "slit_frame")
tur.add(lathe([(0.512, y + 0.19), (0.512, y + 0.22)], 20, start_deg=-40, end_deg=220).translate(cx, 0, cz), "glow")
tur.add(lathe([(0.5, y + 0.25), (0.5, y + 0.36), (0.45, y + 0.42), (0.0, y + 0.43)], 20).translate(cx, 0, cz),
        "olive_hi")
tur.add(extrude(star_pts(0.27, 0.11, rot=90 + 45), 0.025).translate(cx, y + 0.425, cz), "red")
# periscope block facing the front corner (+x,+z)
per = merge(box(0.16, 0.12, 0.14).translate(0, y + 0.47, 0.30), box(0.13, 0.03, 0.02).translate(0, y + 0.49, 0.372))
tur.add(merge(per.copy()).rotate(0, 45, 0).translate(cx, 0, cz), "steel_dk")
tur.add(box(0.11, 0.025, 0.012).translate(0, y + 0.49, 0.38).rotate(0, 45, 0).translate(cx, 0, cz), "glow")
# grab rail
tur.add(torus(0.44, 0.012, 20, 4).translate(cx, y + 0.44, cz), "steel")

m.save(OUT)
print(OUT, m.stats())
