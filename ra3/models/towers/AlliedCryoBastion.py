"""AlliedCryoBastion (Кріо-бастіон) — procedural 3D model generator.

Squat armored bastion with sloped, seamed armor walls, a blue faction stripe and glowing
cryo vents, icicles hanging off the top lip, two blue coolant tanks piped into the turret
collar, a chrome turret drum and a tilted frost dish on a yoke carrying an ice-crystal
emitter cluster.

Run:  python3 -I models/towers/AlliedCryoBastion.py   ->  models/glb/AlliedCryoBastion.glb

Node tree:  Base > Turret > Emitter > FX_Muzzle01 ;  Base > FX_Glow
Turret forward is +X; the Emitter pitches around the Z axis.
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import numpy as np  # noqa: E402

from meshkit import (PLATE_TOP, Mesh, Model, box, crystal, cylinder, cone, disc,  # noqa: E402
                     extrude, foundation_plate, lathe, merge, radial, rgb, sphere, torus,
                     tube_between)

NAME = "AlliedCryoBastion"
OUT = HERE.parent / "glb" / f"{NAME}.glb"


# --------------------------------------------------------------------------- helpers

def oct_pts(half, ch):
    """Chamfered square (octagon) outline, ccw seen from +Y."""
    h, c = half, ch
    return [(h, -h + c), (h, h - c), (h - c, h), (-h + c, h), (-h, h - c), (-h, -h + c), (-h + c, -h), (h - c, -h)]


def loft(pts0, y0, pts1, y1):
    """Side faces between two outlines with equal vertex count (flat-shaded, outward)."""
    n = len(pts0)
    pos, nrm, uv, idx = [], [], [], []
    for i in range(n):
        j = (i + 1) % n
        q = [(pts0[i][0], y0, pts0[i][1]), (pts0[j][0], y0, pts0[j][1]),
             (pts1[j][0], y1, pts1[j][1]), (pts1[i][0], y1, pts1[i][1])]
        q = np.array(q, np.float32)
        fn = np.cross(q[1] - q[0], q[3] - q[0])
        c = q.mean(0)
        if fn[0] * c[0] + fn[2] * c[2] < 0:
            q = q[[1, 0, 3, 2]]
            fn = -fn
        fn = fn / (np.linalg.norm(fn) or 1)
        o = len(pos)
        pos += q.tolist()
        nrm += [fn.tolist()] * 4
        uv += [(0, 0), (1, 0), (1, 1), (0, 1)]
        idx += [(o, o + 1, o + 2), (o, o + 2, o + 3)]
    return Mesh(pos, nrm, uv, idx)


def sweep(points, r=0.06, seg=10, closed=False):
    """Tube swept along a polyline (smooth bends for pipes and frames)."""
    P = np.array(points, np.float32)
    n = len(P)
    tang = []
    for i in range(n):
        if closed:
            d = P[(i + 1) % n] - P[(i - 1) % n]
        else:
            d = P[min(i + 1, n - 1)] - P[max(i - 1, 0)]
        tang.append(d / (np.linalg.norm(d) or 1))
    pos, nrm, uv, idx = [], [], [], []
    ref = np.array([0, 1, 0], np.float32)
    for i in range(n):
        t = tang[i]
        a = ref if abs(float(np.dot(ref, t))) < 0.9 else np.array([1, 0, 0], np.float32)
        u = np.cross(t, a); u /= np.linalg.norm(u)
        v = np.cross(t, u)
        for k in range(seg):
            ang = 2 * math.pi * k / seg
            nn = u * math.cos(ang) + v * math.sin(ang)
            pos.append((P[i] + nn * r).tolist())
            nrm.append(nn.tolist())
            uv.append((k / seg, i / max(n - 1, 1)))
    rings = n if closed else n - 1
    for i in range(rings):
        i2 = (i + 1) % n
        for k in range(seg):
            k2 = (k + 1) % seg
            a0, a1, b0, b1 = i * seg + k, i * seg + k2, i2 * seg + k, i2 * seg + k2
            idx += [(a0, b0, b1), (a0, b1, a1)]
    m = Mesh(pos, nrm, uv, idx)
    # outward winding check
    tri = m.pos[m.idx]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    if (fn * m.nrm[m.idx].mean(1)).sum() < 0:
        m.idx = m.idx[:, ::-1].copy()
    return m


def bezier(p0, p1, p2, steps=10):
    p0, p1, p2 = (np.array(p, np.float32) for p in (p0, p1, p2))
    return [((1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t * t * p2).tolist()
            for t in np.linspace(0, 1, steps + 1)]


# --------------------------------------------------------------------------- model

m = Model(NAME)
m.material("plate", rgb("#c3cfdd"), metallic=0.35, roughness=0.55)
m.material("armor", rgb("#eef3fb"), metallic=0.15, roughness=0.42)
m.material("blue", rgb("#2f6fd6"), metallic=0.45, roughness=0.35)
m.material("chrome", rgb("#c4d2e0"), metallic=0.92, roughness=0.22)
m.material("steel", rgb("#7c91aa"), metallic=0.8, roughness=0.35)
m.material("dark", rgb("#2a3850"), metallic=0.5, roughness=0.5)
m.material("seam", rgb("#4a5d76"), metallic=0.4, roughness=0.6)
m.material("vent", rgb("#5fd8ff"), roughness=0.3, emissive=rgb("#3fc8f0", 1.25))
m.material("ice", rgb("#a8ecff"), metallic=0.0, roughness=0.12, emissive=rgb("#4fd2f5", 0.55))
m.material("frost", rgb("#d9f6ff"), metallic=0.1, roughness=0.25, emissive=rgb("#7fe3ff", 0.45))
m.material("frostcore", rgb("#e6fbff"), roughness=0.2, emissive=rgb("#9fe9ff", 0.9))
m.material("crystal", rgb("#8fe6ff"), roughness=0.08, emissive=rgb("#4fd8ff", 1.15))
m.material("snow", rgb("#f4f9ff"), roughness=0.9)

base = m.node("Base")

# ---- foundation plate: light steel, blue side band, chrome inset line, corner lamps
base.add(foundation_plate(), "plate")
base.add(sweep([(x, PLATE_TOP - 0.01, z) for x, z in oct_pts(2.955, 0.33)], 0.035, 6, closed=True), "blue")
y_top = PLATE_TOP
ring = oct_pts(2.8, 0.3)
base.add(sweep([(x, y_top + 0.005, z) for x, z in ring], 0.025, 4, closed=True), "chrome")
notch = merge(*[box(0.035, 0.26, 0.02).translate(x, 0.17, 3.0) for x in (-1.6, -0.55, 0.55, 1.6)])
base.add(radial(notch, 4), "seam")
for sx in (-1, 1):
    for sz in (-1, 1):
        x, z = sx * 2.68, sz * 2.68
        base.add(cylinder(0.14, 0.04, 12).translate(x, y_top, z), "dark")
        base.add(sphere(0.085, 10, 6).scale(1, 0.8, 1).translate(x, y_top + 0.04, z), "vent")
# frost patches on the plate margin
for (x, z, s, rot) in ((2.78, 1.1, 0.42, 0), (-0.9, 2.8, 0.36, 90), (1.7, 2.82, 0.3, 90),
                       (2.82, -1.4, 0.3, 0), (-2.8, -0.4, 0.3, 0)):
    base.add(sphere(s, 10, 5).scale(0.45, 0.1, 1.0).rotate(0, rot, 0).translate(x, y_top, z), "snow")

# ---- sloped armored body (chamfered square frustum)
Y0, Y1 = PLATE_TOP, 1.65
B0, B1 = 2.55, 2.1
C0, C1 = 0.45, 0.37
SLOPE = math.degrees(math.atan((B0 - B1) / (Y1 - Y0)))


def half_at(y):
    t = (y - Y0) / (Y1 - Y0)
    return B0 + (B1 - B0) * t, C0 + (C1 - C0) * t


def body_band(ya, yb, eps):
    ha, ca = half_at(ya)
    hb, cb = half_at(yb)
    return loft(oct_pts(ha + eps, ca), ya, oct_pts(hb + eps, cb), yb)


base.add(loft(oct_pts(B0, C0), Y0, oct_pts(B1, C1), Y1), "armor")
base.add(body_band(0.62, 0.86, 0.03), "blue")            # faction stripe
base.add(body_band(0.56, 0.6, 0.015), "seam")           # stripe pin-lines
base.add(body_band(0.88, 0.92, 0.015), "seam")
base.add(body_band(1.47, 1.51, 0.012), "seam")           # upper armor seam
base.add(body_band(Y0, 0.42, 0.04), "steel")            # foot skirt


def face_point(x, y, out=0.0):
    """Point on the +Z sloped face at lateral x, height y, pushed `out` along the face normal."""
    h, _ = half_at(y)
    a = math.radians(SLOPE)
    return (x, y + out * math.sin(a), h + out * math.cos(a))


def on_face(mesh, x, y, out):
    return mesh.rotate(-SLOPE, 0, 0).translate(*face_point(x, y, out))


# vertical armor seams (between the vents)
seam_len = (Y1 - 0.92) / math.cos(math.radians(SLOPE))
seams = merge(*[on_face(box(0.04, seam_len - 0.08, 0.02), x, (0.92 + Y1) / 2, 0.005) for x in (-0.56, 0.56)])
seams_lo = merge(*[on_face(box(0.04, 0.2, 0.02), x, 0.47, 0.005) for x in (-0.56, 0.56)])
base.add(radial(merge(seams, seams_lo), 4), "seam")

# cryo vents: dark frame, glowing core, two louver slats
VENT_Y = 1.17
vent_frame, vent_glow, vent_slat = [], [], []
for x in (-1.12, 0.0, 1.12):
    vent_frame.append(on_face(box(0.36, 0.56, 0.06), x, VENT_Y, 0.0))
    vent_glow.append(on_face(box(0.27, 0.47, 0.06), x, VENT_Y, 0.02))
    vent_slat.append(on_face(box(0.29, 0.03, 0.03), x, VENT_Y, 0.05))
base.add(radial(merge(*vent_frame), 4), "dark")
base.add(radial(merge(*vent_glow), 4), "vent")
base.add(radial(merge(*vent_slat), 4), "dark")

# top lip slab + deck frame
LIP_Y, LIP_H, LIP_HALF = Y1, 0.2, 2.24
DECK = LIP_Y + LIP_H
base.add(extrude(oct_pts(LIP_HALF, 0.42), LIP_H).translate(0, LIP_Y, 0), "armor")
base.add(loft(oct_pts(LIP_HALF + 0.012, 0.42), LIP_Y + 0.02, oct_pts(LIP_HALF + 0.012, 0.42), LIP_Y + 0.05), "blue")
base.add(sweep([(x, DECK + 0.004, z) for x, z in oct_pts(1.82, 0.36)], 0.028, 4, closed=True), "chrome")

# icicles hanging off the lip
rng = np.random.default_rng(7)
ice = []
xs = np.linspace(-1.72, 1.72, 9)
for x in xs:
    L = float(rng.uniform(0.2, 0.5))
    r = float(rng.uniform(0.06, 0.09))
    ice.append(cone(r, L, 5).rotate(180, 0, 0).translate(float(x + rng.uniform(-0.06, 0.06)), LIP_Y + 0.02, LIP_HALF - 0.05))
# corner icicles on the chamfers
for t in (0.3, 0.7):
    cx = (LIP_HALF - 0.42) + 0.42 * t
    cz = LIP_HALF - 0.42 * t
    ice.append(cone(0.055, float(rng.uniform(0.18, 0.34)), 5).rotate(180, 0, 0).translate(cx - 0.04, LIP_Y + 0.02, cz - 0.04))
base.add(radial(merge(*ice), 4), "ice")
# a crust of ice along the lip underside edge
base.add(loft(oct_pts(LIP_HALF + 0.005, 0.42), LIP_Y - 0.03, oct_pts(LIP_HALF + 0.03, 0.42), LIP_Y + 0.02), "ice")

# FX_Glow marker at the centre vent of the +Z face
g = face_point(0.0, VENT_Y, 0.06)
fx_glow = base.node("FX_Glow", g)
fx_glow.add(box(0.05, 0.05, 0.05).translate(*g), "vent")

# ---- static coolant collar around the drum foot
COLLAR_R, COLLAR_H = 1.42, 0.2
base.add(cylinder(COLLAR_R, COLLAR_H, 32).translate(0, DECK, 0), "steel")
base.add(cylinder(COLLAR_R + 0.02, 0.06, 32, caps=False).translate(0, DECK + 0.07, 0), "blue")

# ---- coolant tanks (screen-left / screen-right of the drum in the RA3 view)
TANK_R, TANK_H = 0.4, 0.78
for d in ((-1.0, 1.0), (1.0, -1.0)):
    dv = np.array([d[0], 0, d[1]], np.float32) / math.sqrt(2)
    c = dv * 2.12
    cx, cz = float(c[0]), float(c[2])
    base.add(cylinder(TANK_R + 0.06, 0.1, 20).translate(cx, DECK, cz), "chrome")
    body = lathe([(TANK_R, DECK + 0.1), (TANK_R, DECK + 0.1 + TANK_H), (TANK_R * 0.82, DECK + TANK_H + 0.2),
                  (TANK_R * 0.5, DECK + TANK_H + 0.25), (0.0, DECK + TANK_H + 0.25)], 20)
    base.add(body.translate(cx, 0, cz), "blue")
    base.add(cylinder(TANK_R + 0.015, 0.1, 20, caps=False).translate(cx, DECK + 0.48, cz), "armor")
    base.add(cylinder(TANK_R + 0.015, 0.04, 20, caps=False).translate(cx, DECK + 0.22, cz), "chrome")
    top = DECK + TANK_H + 0.25
    base.add(cylinder(0.2, 0.05, 16).translate(cx, top - 0.01, cz), "chrome")
    base.add(disc(0.14, 16).translate(cx, top + 0.045, cz), "vent")
    # pipe: from the tank's inner shoulder arcing into the collar
    p0 = dv * (2.12 - TANK_R * 0.7)
    p0 = (float(p0[0]), DECK + TANK_H + 0.12, float(p0[2]))
    p1 = dv * 1.5
    p1 = (float(p1[0]), DECK + TANK_H + 0.5, float(p1[2]))
    p2 = dv * (COLLAR_R - 0.14)
    p2 = (float(p2[0]), DECK + COLLAR_H - 0.02, float(p2[2]))
    base.add(sweep(bezier(p0, p1, p2, 10), 0.07, 10), "frost")
    base.add(sweep(bezier(p0, p1, p2, 10)[:2], 0.095, 10), "dark")
    base.add(sweep(bezier(p0, p1, p2, 10)[-2:], 0.095, 10), "dark")
    # secondary low pipe on the deck
    q0 = (float(c[0] - dv[0] * TANK_R), DECK + 0.3, float(c[2] - dv[2] * TANK_R))
    q2 = (float(dv[0] * COLLAR_R), DECK + 0.1, float(dv[2] * COLLAR_R))
    side = np.array([-dv[2], 0, dv[0]], np.float32) * 0.22
    base.add(tube_between((q0[0] + side[0], q0[1], q0[2] + side[2]), (q2[0] + side[0], q2[1] + 0.03, q2[2] + side[2]), 0.045, 8), "chrome")

# ---- Turret: chrome drum, yoke and trunnion
turret = base.node("Turret", (0, DECK, 0))
DRUM_R, DRUM_TOP = 1.12, DECK + 0.7
drum = lathe([(0, DECK), (DRUM_R - 0.03, DECK), (DRUM_R, DECK + 0.05), (DRUM_R, DRUM_TOP - 0.1),
              (DRUM_R - 0.08, DRUM_TOP), (0, DRUM_TOP)], 36, smooth=False)
turret.add(drum, "chrome")
turret.add(cylinder(DRUM_R + 0.03, 0.13, 36, caps=False).translate(0, DECK + 0.27, 0), "blue")
turret.add(cylinder(DRUM_R + 0.012, 0.025, 36, caps=False).translate(0, DECK + 0.43, 0), "seam")
slot = box(0.05, 0.06, 0.2).translate(DRUM_R + 0.005, DECK + 0.52, 0)
turret.add(radial(slot, 12, 15), "vent")
turret.add(cylinder(DRUM_R - 0.12, 0.03, 36).translate(0, DRUM_TOP, 0), "armor")
turret.add(cylinder(0.48, 0.05, 24).translate(0, DRUM_TOP, 0), "dark")

PIVOT = (0.0, DRUM_TOP + 0.24, 0.0)
turret.add(cylinder(0.2, PIVOT[1] - DRUM_TOP, 16).translate(0, DRUM_TOP, 0), "dark")
for sz in (-1, 1):
    turret.add(tube_between((0, DRUM_TOP - 0.02, sz * 0.5), (0, PIVOT[1], sz * 1.0), 0.1, 8), "chrome")
    turret.add(tube_between((0.18, DRUM_TOP - 0.02, sz * 0.5), (0.0, PIVOT[1], sz * 1.0), 0.06, 6), "steel")
    turret.add(sphere(0.13, 12, 8).translate(0, PIVOT[1], sz * 1.0), "chrome")
turret.add(tube_between((0, PIVOT[1], -1.08), (0, PIVOT[1], 1.08), 0.09, 12), "dark")

# ---- Emitter: tilted frost dish + ice-crystal cluster (built along +Y, then pitched toward +X)
TILT = 14.0
DS = 0.86  # dish scale
emitter = turret.node("Emitter", PIVOT)


def em(mesh, s=DS):
    return mesh.scale(s).rotate(0, 0, -TILT).translate(*PIVOT)


SEG = 36
emitter.add(em(cylinder(0.3, 0.32, 16).translate(0, -0.1, 0)), "steel")
emitter.add(em(lathe([(0.0, 0.2), (0.4, 0.23), (0.8, 0.32), (1.15, 0.48), (1.4, 0.64), (1.48, 0.7)], SEG)), "chrome")
emitter.add(em(lathe([(1.48, 0.7), (1.48, 0.8), (1.36, 0.8)], SEG, smooth=False)), "armor")
emitter.add(em(lathe([(1.36, 0.8), (1.1, 0.64), (0.75, 0.52)], SEG)), "frost")
emitter.add(em(lathe([(0.75, 0.52), (0.4, 0.46), (0.0, 0.44)], SEG)), "frostcore")
emitter.add(em(torus(1.42, 0.05, SEG, 6).translate(0, 0.81, 0)), "blue")
emitter.add(em(torus(1.05, 0.018, SEG, 4).translate(0, 0.625, 0)), "seam")
emitter.add(em(torus(0.68, 0.018, SEG, 4).translate(0, 0.51, 0)), "seam")
# back ribs
rib = box(0.06, 0.08, 0.9).rotate(-20, 0, 0).translate(0, 0.3, 0.75)
emitter.add(em(radial(rib, 6, 30)), "steel")

APEX_Y = 1.5
APEX_W = APEX_Y * DS
struts = []
for k in range(3):
    a = math.radians(120 * k)
    p = (1.28 * math.cos(a), 0.78, 1.28 * math.sin(a))
    struts.append(tube_between(p, (0, APEX_Y, 0), 0.035, 6))
    struts.append(sphere(0.06, 8, 6).translate(*p))
emitter.add(em(merge(*struts)), "chrome")
emitter.add(em(cone(0.16, 0.25, 12).rotate(180, 0, 0).translate(0, APEX_Y + 0.05, 0)), "steel")
emitter.add(em(cylinder(0.22, 0.14, 16).translate(0, APEX_Y - 0.02, 0)), "blue")
emitter.add(em(torus(0.22, 0.03, 16, 4).translate(0, APEX_Y + 0.12, 0)), "chrome")

C_BASE, C_H = APEX_W - 0.04, 1.3
cluster = [crystal(0.21, C_H, 6, 0.5).translate(0, C_BASE, 0)]
for (yaw, lean, r, h) in ((10, 30, 0.14, 0.85), (100, 26, 0.12, 0.72), (190, 32, 0.13, 0.8),
                          (280, 28, 0.14, 0.9), (55, 50, 0.09, 0.5), (235, 48, 0.09, 0.52)):
    cluster.append(crystal(r, h, 6, 0.5).rotate(0, 0, -lean).rotate(0, yaw, 0).translate(0, C_BASE + 0.08, 0))
emitter.add(em(merge(*cluster), 1.0), "crystal")
emitter.add(em(sphere(0.14, 10, 6).translate(0, APEX_W + 0.1, 0), 1.0), "frostcore")

tip = em(Mesh([(0, C_BASE + C_H, 0)], [(0, 1, 0)], [(0, 0)], np.zeros((0, 3))), 1.0).pos[0]
fx_m = emitter.node("FX_Muzzle01", tuple(float(v) for v in tip))
fx_m.add(box(0.05, 0.05, 0.05).translate(*[float(v) for v in tip]), "crystal")

if __name__ == "__main__":
    path = m.save(OUT)
    st = m.stats()
    # bounds check
    allp = np.vstack([p.pos for n in m._walk() for p in n.prims.values()])
    print(path, st, "bounds", allp.min(0).round(3).tolist(), allp.max(0).round(3).tolist())
