"""JapanWaveCitadel (Хвильова цитадель) — Empire of the Rising Sun sonic emitter tower.

Massive sloped white lathe body (black base band, crimson stripes, side fins, rising-sun
emblems) wrapped by two teal charging rings; a black turret ring carries two support arms
holding a large tilted emitter dish (crimson rim, dark bowl with glowing teal rings, central
horn with charging rings and a bright core).

Also builds the nanocore transport JapanWaveCitadelNanocore.

Run:  python3 -I models/towers/JapanWaveCitadel.py
Out:  models/glb/JapanWaveCitadel.glb, models/glb/JapanWaveCitadelNanocore.glb
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODELS = HERE.parent
sys.path.insert(0, str(MODELS))

import numpy as np  # noqa: E402

from meshkit import (PLATE_TOP, Mesh, Model, box, cone, cylinder, disc, extrude,  # noqa: E402
                     foundation_plate, lathe, merge, radial, rgb, sphere, torus, tube_between)

OUT = MODELS / "glb"

WHITE, CRIMSON, BLACK, TEAL = "#f4f1ec", "#c8102e", "#1d1d22", "#3fe0c5"
FRONT = 45.0          # direction of the camera in the RA3-like preview (+x, +z)


# --------------------------------------------------------------------------- helpers

def materials(m: Model):
    m.material("white", rgb(WHITE), metallic=0.08, roughness=0.42)
    m.material("plate", rgb("#d9d4cc"), metallic=0.1, roughness=0.7)
    m.material("trim", rgb("#8d887f"), metallic=0.3, roughness=0.55)
    m.material("crimson", rgb(CRIMSON), metallic=0.15, roughness=0.4)
    m.material("black", rgb(BLACK), metallic=0.55, roughness=0.38)
    m.material("dark", rgb("#2a2a31"), metallic=0.4, roughness=0.5)
    m.material("bowl", rgb("#14161a"), metallic=0.3, roughness=0.45, emissive=rgb(TEAL, 0.06))
    m.material("teal", rgb(TEAL), roughness=0.25, emissive=rgb(TEAL, 1.6))
    m.material("teal_soft", rgb(TEAL), roughness=0.3, emissive=rgb(TEAL, 1.1))
    m.material("core", rgb("#eafffb"), roughness=0.2, emissive=rgb("#c8fff5", 2.5))
    m.material("halo", rgb(TEAL), roughness=0.3, emissive=rgb(TEAL, 0.9), alpha=0.22)


def band(r0, y0, r1, y1, seg=48):
    """Open frustum ring between (r0, y0) and (r1, y1)."""
    return cylinder(r0, y1 - y0, seg, r_top=r1, caps=False).translate(0, y0, 0)


def _cw(pts):
    """Orient a polygon the way meshkit.extrude really needs it: counter-clockwise in the math
    (u, v) plane (positive signed area). With the opposite order extrude's side normals point
    inwards and _fix_winding then turns the side faces inside-out."""
    a = sum(pts[i][0] * pts[(i + 1) % len(pts)][1] - pts[(i + 1) % len(pts)][0] * pts[i][1]
            for i in range(len(pts)))
    return list(reversed(pts)) if a < 0 else list(pts)


def _swizzle(m: Mesh, order):
    m.pos = m.pos[:, order].copy()
    m.nrm = m.nrm[:, order].copy()
    perm_parity = sum(1 for i in range(3) for j in range(i + 1, 3) if order[i] > order[j]) % 2
    if perm_parity:
        m.idx = m.idx[:, ::-1].copy()
    return m


def prism_xy(pts, t):
    """Convex polygon [(x, y)] extruded along z, centred on z = 0."""
    m = extrude(_cw(pts), t)            # (u, h, v) = (x, z+, y)
    _swizzle(m, [0, 2, 1])
    return m.translate(0, 0, -t / 2)


def prism_zy(pts, t):
    """Convex polygon [(z, y)] extruded along x, centred on x = 0."""
    m = extrude(_cw(pts), t)            # (u, h, v) = (z, x, y)
    _swizzle(m, [1, 2, 0])
    return m.translate(-t / 2, 0, 0)


def on_surface(mesh, a_deg, r, y, lean=0.0):
    """Place a mesh built facing +z (outward) onto a lathe surface at angle a, radius r, height y.
    lean = inward slope of the surface in degrees (top leaning towards the axis)."""
    a = math.radians(a_deg)
    return mesh.rotate(-lean, 90 - a_deg, 0).translate(r * math.cos(a), y, r * math.sin(a))


def slope_deg(r0, y0, r1, y1):
    return math.degrees(math.atan2(r0 - r1, y1 - y0))


def r_at(r0, y0, r1, y1, y):
    return r0 + (r1 - r0) * (y - y0) / (y1 - y0)


def rot_z(p, deg):
    a = math.radians(deg)
    x, y, z = p
    return (x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a), z)


def flat_disc(r, seg=28, rings=3, r_in=0.0):
    """Single-sided flat disc / annulus facing +y, subdivided radially (wraps onto curves)."""
    prof = [(r_in + (r - r_in) * (1 - i / rings), 0.0) for i in range(rings + 1)]
    return lathe(prof, seg)


def wrap(m: Mesh, R):
    """Bend a mesh built facing +z (plane z=0 = surface) around a vertical cylinder of radius R."""
    u, v, w = m.pos[:, 0].copy(), m.pos[:, 1].copy(), m.pos[:, 2].copy()
    th = u / R
    m.pos = np.stack([(R + w) * np.sin(th), v, (R + w) * np.cos(th) - R], 1).astype(np.float32)
    nx, ny, nz = m.nrm[:, 0].copy(), m.nrm[:, 1].copy(), m.nrm[:, 2].copy()
    m.nrm = np.stack([nx * np.cos(th) + nz * np.sin(th), ny, -nx * np.sin(th) + nz * np.cos(th)], 1).astype(np.float32)
    return m


def emblem(r=0.5, rays=14, body_r=None, seg=32):
    """Rising-sun emblem facing +z: white disc, crimson rays + sun, black rim.
    Returns dict material -> mesh. body_r bends it onto a round body of that radius."""
    white = flat_disc(r, seg, 3).translate(0, 0.012, 0)
    parts = [flat_disc(r * 0.42, seg, 2).translate(0, 0.03, 0)]
    step = 360.0 / rays
    for i in range(rays):
        c = step * i + step / 2
        parts.append(lathe([(r * 0.9, 0), (r * 0.62, 0), (r * 0.36, 0)], 2,
                           start_deg=c - step * 0.3, end_deg=c + step * 0.3).translate(0, 0.022, 0))
    out = {"white": white, "crimson": merge(*parts),
           "black": torus(r * 1.0, 0.03, seg, 4).scale(1, 0.6, 1).translate(0, 0.012, 0)}
    for k, mm in out.items():
        mm.rotate(90, 0, 0)
        if body_r:
            wrap(mm, body_r)
    return out


# --------------------------------------------------------------------------- tower

# body profile (radius, y)
L0, L1, L2, L3 = (2.80, PLATE_TOP), (2.72, 0.78), (2.44, 1.40), (2.38, 1.56)
U0, U1, U2 = (1.96, 1.60), (1.44, 4.60), (1.39, 4.90)
TURRET_Y = 4.90
PIVOT = (0.0, 6.35, 0.0)
TILT = -22.0               # dish pitched towards +X (game forward)


def build_tower():
    m = Model("JapanWaveCitadel")
    materials(m)
    base = m.node("Base")

    # ---- foundation plate with Empire trim
    for (y0, y1, mat) in ((0.0, 0.05, "plate"), (0.05, 0.085, "black"), (0.085, 0.20, "plate"),
                          (0.20, 0.26, "crimson"), (0.26, PLATE_TOP, "plate")):
        base.add(box(6.0, y1 - y0, 6.0, bevel=0.35).translate(0, (y0 + y1) / 2, 0), mat)
    for i in range(4):     # inset groove frame
        ang = 90 * i
        base.add(box(4.9, 0.025, 0.05).translate(0, PLATE_TOP + 0.012, 2.45).rotate(0, ang, 0), "trim")
    for i in range(4):     # crimson corner chevrons + teal lights
        ca = 45 + 90 * i
        tri = prism_xy([(0, 0), (0.42, 0.22), (0.42, -0.22)], 0.03)      # pointing -x (inward)
        tri.rotate(-90, 0, 0)                                            # lie flat on the plate
        tri.translate(-3.72, PLATE_TOP + 0.015, 0).rotate(0, 180 - ca, 0)
        base.add(tri, "crimson")
        lp = 3.05
        base.add(box(0.16, 0.05, 0.16).rotate(0, 45, 0).translate(
            lp * math.cos(math.radians(ca)), PLATE_TOP + 0.025, lp * math.sin(math.radians(ca))), "teal")

    # ---- lower tier: black base band, white slope, crimson stripe, white shoulder
    base.add(band(*L0, *L1, 48), "black")
    base.add(band(*L1, *L2, 48), "white")
    base.add(band(*L2, *L3, 48), "crimson")
    base.add(lathe([L3, (2.30, 1.585), U0], 48), "white")
    for i in range(10):    # teal light strips on the black band (front arc)
        a = FRONT + (i - 4.5) * 17
        if abs(((a - FRONT + 180) % 360) - 180) < 8:
            continue
        base.add(on_surface(box(0.34, 0.07, 0.05), a, r_at(*L0, *L1, 0.58) + 0.005, 0.58,
                            slope_deg(*L0, *L1)), "teal")
    for i in range(10):    # same at the back
        a = FRONT + 180 + (i - 4.5) * 17
        base.add(on_surface(box(0.34, 0.07, 0.05), a, r_at(*L0, *L1, 0.58) + 0.005, 0.58,
                            slope_deg(*L0, *L1)), "teal")
    for i in range(20):    # panel seams on the lower slope
        a = math.radians(360 * i / 20 + 9)
        p0 = ((L1[0] + 0.012) * math.cos(a), L1[1] + 0.02, (L1[0] + 0.012) * math.sin(a))
        p1 = ((L2[0] + 0.012) * math.cos(a), L2[1] - 0.02, (L2[0] + 0.012) * math.sin(a))
        base.add(tube_between(p0, p1, 0.016, 4), "trim")

    # ---- side fins (screen left / right in the RA3 view)
    fin_pts = [(1.66, 3.05), (2.12, 3.22), (3.62, 0.66), (3.54, PLATE_TOP), (2.60, PLATE_TOP)]
    for fa in (FRONT + 90, FRONT - 90):
        fin = prism_xy(fin_pts, 0.42)
        stripe = prism_xy([(2.62, 1.86), (2.90, 1.94), (3.16, 1.50), (2.90, 1.43)], 0.44)
        cap = box(0.36, 0.14, 0.48).translate(3.52, 0.48, 0)
        tip = box(0.08, 0.10, 0.26).translate(3.62, 0.72, 0)
        base.add(fin.rotate(0, -fa, 0), "white")
        base.add(stripe.rotate(0, -fa, 0), "crimson")
        base.add(cap.rotate(0, -fa, 0), "black")
        base.add(tip.rotate(0, -fa, 0), "teal")
        base.add(prism_xy([(1.70, 3.0), (2.12, 3.15), (2.18, 3.08), (1.76, 2.93)], 0.46).rotate(0, -fa, 0), "black")

    # ---- upper body: white sloped drum with black crown band
    base.add(band(*U0, *U1, 48), "white")
    base.add(band(*U1, *U2, 48), "black")
    base.add(lathe([U2, (1.30, TURRET_Y + 0.01), (0.0, TURRET_Y + 0.01)], 40), "dark")
    lean_u = slope_deg(*U0, *U1)
    for side in (FRONT, FRONT + 180):     # rising-sun emblems + crimson stripes
        ey = 3.05
        rr = r_at(*U0, *U1, ey)
        for mat, mm in emblem(0.74, 14, rr).items():
            base.add(on_surface(mm, side, rr, ey, lean_u), mat)
        for da in (-27, 27):
            sy0, sy1 = 2.35, 3.95
            ym = (sy0 + sy1) / 2
            st = box(0.13, (sy1 - sy0) / math.cos(math.radians(lean_u)), 0.04)
            base.add(on_surface(st, side + da, r_at(*U0, *U1, ym) + 0.005, ym, lean_u), "crimson")
    for i in range(16):    # vertical seams on the drum (skip emblem zones)
        a_deg = 360 * i / 16 + 11.25
        if min(abs(((a_deg - FRONT + 180) % 360) - 180), abs(((a_deg - FRONT) % 360) - 180)) < 30:
            continue
        a = math.radians(a_deg)
        p0 = ((U0[0] + 0.01) * math.cos(a), U0[1] + 0.1, (U0[0] + 0.01) * math.sin(a))
        p1 = ((r_at(*U0, *U1, 4.5) + 0.01) * math.cos(a), 4.5, (r_at(*U0, *U1, 4.5) + 0.01) * math.sin(a))
        base.add(tube_between(p0, p1, 0.015, 4), "trim")
    for i in range(12):    # teal lights on the crown band
        a = 360 * i / 12 + 15
        base.add(on_surface(box(0.11, 0.07, 0.04), a, r_at(*U1, *U2, 4.75) + 0.01, 4.75), "teal")

    # ---- two teal charging rings around the body (with soft halo)
    for (R, y) in ((2.62, 2.12), (2.18, 3.78)):
        base.add(torus(R, 0.06, 48, 6).translate(0, y, 0), "teal")
        base.add(torus(R, 0.13, 40, 4).translate(0, y, 0), "halo")

    # ---- Turret: black ring + support arms
    tur = base.node("Turret", (0, TURRET_Y, 0))
    ty = TURRET_Y
    tur.add(band(1.46, ty, 1.46, ty + 0.5, 40), "black")
    tur.add(band(1.48, ty + 0.5, 1.40, ty + 0.56, 40), "trim")
    tur.add(disc(1.40, 40).translate(0, ty + 0.56, 0), "dark")
    tur.add(band(1.52, ty + 0.02, 1.52, ty + 0.1, 40), "crimson")
    for i in range(12):
        a = 360 * i / 12
        tur.add(on_surface(box(0.16, 0.08, 0.04), a, 1.465, ty + 0.3), "teal")
    tur.add(cylinder(0.55, 0.6, 24, r_top=0.42).translate(0, ty + 0.56, 0), "black")     # dish mast
    arm = [(0.80, ty + 0.40), (1.50, ty + 0.40), (2.02, 6.12), (1.86, 6.48), (1.42, 6.22)]
    for sgn in (1, -1):
        pts = [(z * sgn, y) for z, y in arm]
        tur.add(prism_zy(pts, 0.46), "white")
        tur.add(prism_zy([(1.30 * sgn, 5.55), (1.52 * sgn, 5.55), (1.72 * sgn, 5.88), (1.52 * sgn, 5.88)], 0.48), "crimson")
        pod = cylinder(0.30, 0.42, 20).rotate(90 * sgn, 0, 0).translate(0, PIVOT[1], 1.72 * sgn)
        # rotate(90) maps +y to +z: pod spans z 1.72..2.14 (or mirrored)
        tur.add(pod, "black")
        tur.add(disc(0.13, 16).rotate(90 * sgn, 0, 0).translate(0, PIVOT[1], 2.145 * sgn), "teal")
        tur.add(torus(0.22, 0.025, 20, 4).rotate(90, 0, 0).translate(0, PIVOT[1], 2.15 * sgn), "trim")

    # ---- Emitter: dish + horn (built at the pivot, pitched by TILT)
    emi = tur.node("Emitter", PIVOT)
    E = {}

    def add_e(mesh, mat):
        E.setdefault(mat, []).append(mesh)

    add_e(lathe([(0.0, -0.12), (0.62, -0.12), (1.35, 0.08), (1.88, 0.38), (2.16, 0.72)], 48), "white")
    add_e(lathe([(2.16, 0.72), (2.30, 0.78), (2.30, 0.93), (2.12, 0.98), (2.02, 0.94)], 48), "crimson")
    add_e(lathe([(2.02, 0.94), (1.72, 0.64), (1.15, 0.36), (0.45, 0.22), (0.0, 0.21)], 48), "bowl")
    for (R, y, tube) in ((1.80, 0.71, 0.04), (1.30, 0.43, 0.035), (0.80, 0.285, 0.03)):
        add_e(torus(R, tube, 40, 5).translate(0, y + 0.02, 0), "teal")
    for i in range(8):
        a = math.radians(360 * i / 8 + 22.5)
        add_e(box(0.08, 0.05, 0.08).translate(1.97 * math.cos(a), 0.93, 1.97 * math.sin(a)), "white")
    for i in range(6):     # underside ribs
        a = math.radians(360 * i / 6)
        p0 = (0.62 * math.cos(a), -0.13, 0.62 * math.sin(a))
        p1 = (1.95 * math.cos(a), 0.44, 1.95 * math.sin(a))
        add_e(tube_between(p0, p1, 0.035, 4), "trim")
    add_e(cylinder(0.56, 0.24, 24, r_top=0.64).translate(0, -0.36, 0), "crimson")         # under collar
    add_e(cylinder(0.42, 0.12, 24).translate(0, -0.48, 0), "black")
    add_e(cylinder(0.2, 3.9, 12).rotate(90, 0, 0).translate(0, 0.0, -1.95), "black")     # trunnion axle z
    # horn
    add_e(cylinder(0.48, 0.14, 28, r_top=0.42).translate(0, 0.2, 0), "black")
    add_e(lathe([(0.36, 0.34), (0.30, 0.9), (0.22, 1.3), (0.0, 1.3)], 24), "white")
    add_e(band(0.22, 1.3, 0.19, 1.45, 24), "crimson")
    add_e(lathe([(0.19, 1.45), (0.13, 1.85), (0.07, 2.05), (0.0, 2.08)], 24), "white")
    add_e(sphere(0.18, 16, 10).translate(0, 2.2, 0), "core")
    add_e(sphere(0.34, 12, 8).translate(0, 2.2, 0), "halo")
    for (R, y, tube) in ((0.95, 1.05, 0.04), (0.68, 1.7, 0.04), (0.42, 2.38, 0.035)):
        add_e(torus(R, tube, 32, 5).translate(0, y, 0), "teal")
        add_e(torus(R, tube * 2.6, 24, 4).translate(0, y, 0), "halo")
    for mat, parts in E.items():
        emi.add(merge(*parts).rotate(0, 0, TILT).translate(*PIVOT), mat)

    def fx(name, local):
        p = rot_z(local, TILT)
        p = (p[0] + PIVOT[0], p[1] + PIVOT[1], p[2] + PIVOT[2])
        n = emi.node(name, p)
        n.add(box(0.05, 0.05, 0.05).translate(*p), "core")

    fx("FX_Muzzle01", (0.0, 2.08, 0.0))
    fx("FX_Glow", (0.0, 2.2, 0.0))
    return m


# --------------------------------------------------------------------------- nanocore

def build_nanocore():
    m = Model("JapanWaveCitadelNanocore")
    materials(m)
    base = m.node("Base")
    # lower hull (chamfered plan), black skirt, crimson stripe
    base.add(box(3.5, 0.44, 1.70, bevel=0.32).translate(0, 0.56, 0), "white")
    base.add(box(3.30, 0.12, 1.74, bevel=0.32).translate(0, 0.36, 0), "black")
    base.add(box(3.52, 0.07, 1.72, bevel=0.32).translate(0, 0.66, 0), "crimson")
    # upper deck with sloped nose and tail
    base.add(prism_xy([(-1.62, 0.78), (1.64, 0.78), (1.22, 1.04), (-1.46, 1.04)], 1.42), "white")
    base.add(prism_xy([(-1.30, 1.03), (0.60, 1.03), (0.60, 1.07), (-1.30, 1.07)], 1.22), "dark")
    # cab / visor at the front (+x is forward)
    base.add(prism_xy([(0.62, 1.03), (1.24, 1.03), (1.02, 1.30), (0.70, 1.32)], 1.10), "white")
    base.add(prism_xy([(1.00, 1.05), (1.25, 1.05), (1.06, 1.28), (0.96, 1.28)], 0.96), "black")
    base.add(box(0.04, 0.05, 0.86).rotate(0, 0, 40).translate(1.43, 0.92, 0), "teal")
    base.add(box(0.30, 0.06, 0.06).translate(0.82, 1.335, 0), "crimson")
    # folded dish package: closed bowl tilted back, horn folded along the tail
    dc, tilt = (-0.40, 1.10, 0.0), 10.0
    dish = [
        (lathe([(0.0, 0.0), (0.42, 0.0), (0.66, 0.10), (0.72, 0.16)], 24), "white"),
        (lathe([(0.72, 0.16), (0.78, 0.19), (0.78, 0.26), (0.70, 0.28)], 24), "crimson"),
        (lathe([(0.70, 0.28), (0.45, 0.20), (0.0, 0.18)], 24), "bowl"),
        (torus(0.56, 0.022, 24, 4).translate(0, 0.25, 0), "teal"),
        (torus(0.33, 0.02, 20, 4).translate(0, 0.21, 0), "teal"),
        (cylinder(0.15, 0.08, 12).translate(0, 0.18, 0), "black"),
        (sphere(0.09, 10, 6).translate(0, 0.30, 0), "core"),
    ]
    for mesh, mat in dish:
        base.add(mesh.rotate(0, 0, tilt).translate(*dc), mat)
    base.add(tube_between((-0.48, 1.34, 0), (-1.42, 1.18, 0), 0.075, 10), "white")       # folded horn
    base.add(band(0.11, 0, 0.11, 0.12, 10).rotate(0, 0, 80).translate(-1.18, 1.22, 0), "crimson")
    base.add(sphere(0.07, 8, 5).translate(-1.47, 1.17, 0), "teal")
    base.add(box(0.26, 0.16, 0.36).translate(-1.36, 1.10, 0), "black")                    # horn cradle
    for sz in (-1, 1):
        base.add(box(0.34, 0.24, 0.14).translate(-0.40, 1.16, sz * 0.66), "black")         # dish hinges
        base.add(box(0.12, 0.05, 0.03).translate(-0.40, 1.18, sz * 0.735), "teal")
        for x in (-1.15, -0.65, 0.25, 0.75):                                             # running lights
            base.add(box(0.24, 0.05, 0.02).translate(x, 0.48, sz * 0.865), "teal")
        for mat, mm in emblem(0.17, 8, None, 12).items():
            base.add(mm.rotate(0, 0 if sz > 0 else 180, 0).translate(-0.20, 0.50, sz * 0.852), mat)
    base.add(box(0.02, 0.06, 0.9).translate(-1.755, 0.52, 0), "teal")                     # tail light
    # 4 hover pads
    for px in (-1.12, 1.10):
        for pz in (-0.80, 0.80):
            base.add(band(0.30, 0.0, 0.35, 0.16, 14).translate(px, 0, pz), "black")
            base.add(band(0.35, 0.16, 0.30, 0.30, 14).translate(px, 0, pz), "white")
            base.add(band(0.36, 0.15, 0.36, 0.19, 14).translate(px, 0, pz), "crimson")
            base.add(disc(0.30, 14).translate(px, 0.30, pz), "white")
            base.add(disc(0.29, 14).rotate(180, 0, 0).translate(px, 0.004, pz), "teal")
    return m


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    t = build_tower()
    n = build_nanocore()
    p1 = t.save(OUT / "JapanWaveCitadel.glb")
    p2 = n.save(OUT / "JapanWaveCitadelNanocore.glb")
    for p, mdl in ((p1, t), (p2, n)):
        allp = np.vstack([mm.pos for nd in mdl._walk() for mm in nd.prims.values()])
        print(p.name, mdl.stats(), "min", allp.min(0).round(2).tolist(), "max", allp.max(0).round(2).tolist())


if __name__ == "__main__":
    main()
