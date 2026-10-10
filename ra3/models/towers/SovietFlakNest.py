"""SovietFlakNest (Флак-гніздо) — Soviet anti-air nest.

A round concrete nest wall crowned with two courses of sandbags, brass shell casings
on the floor, a red turret pedestal with a hazard ring, an olive turret with a gun
cradle and red star, and three flak barrels (recoil jackets, muzzle brakes) raised ~55°.

Nodes: Base > Turret > Barrel > FX_Muzzle01..03.
The guns point along -Z at rest (matches the concept-art view from the +X+Z camera).

Run:  python3 -I models/towers/SovietFlakNest.py  ->  models/glb/SovietFlakNest.glb
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import numpy as np  # noqa: E402

from meshkit import (PLATE_TOP, Mesh, Model, box, cylinder, disc, extrude,  # noqa: E402
                     foundation_plate, lathe, merge, rgb, sphere, torus, tube_between)

OUT = HERE.parent / "glb" / "SovietFlakNest.glb"
rng = np.random.RandomState(1945)


# --------------------------------------------------------------------------- helpers

def quad(a, b, c, d, out):
    """Single quad a-b-c-d, wound so that its normal faces along `out`."""
    p = np.array([a, b, c, d], np.float32)
    n = np.cross(p[1] - p[0], p[2] - p[0])
    if np.dot(n, out) < 0:
        p = p[[0, 3, 2, 1]]
        n = -n
    n = n / (np.linalg.norm(n) or 1)
    return Mesh(p, [n] * 4, [(0, 0), (1, 0), (1, 1), (0, 1)], [(0, 1, 2), (0, 2, 3)])


def smooth_normals(m):
    """Area-weighted vertex normals (for deformed primitives)."""
    tri = m.pos[m.idx]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    vn = np.zeros_like(m.pos)
    for k in range(3):
        np.add.at(vn, m.idx[:, k], fn)
    l = np.linalg.norm(vn, axis=1, keepdims=True)
    l[l == 0] = 1
    m.nrm = (vn / l).astype(np.float32)
    return m


def rounded_box(w, h, d, power=0.5, seg=8, rings=5):
    """Pillow / sandbag: a superellipsoid made by pushing a sphere towards a box."""
    m = sphere(1.0, seg, rings)
    m.pos = (np.sign(m.pos) * np.abs(m.pos) ** power).astype(np.float32)
    m.pos *= np.array([w / 2, h / 2, d / 2], np.float32)
    return smooth_normals(m)


def hazard_ring(r, y0, y1, count, skew=0.6):
    """Diagonal stripes on a cylinder surface: returns (black band, yellow stripes)."""
    band = cylinder(r, y1 - y0, 64, caps=False).translate(0, y0, 0)
    stripes = []
    step = 2 * math.pi / count
    sk = skew * step
    rr = r + 0.008

    def p(t, y):
        return (rr * math.cos(t), y, rr * math.sin(t))
    for i in range(count):
        t0 = i * step
        t1 = t0 + step * 0.5
        mid = t0 + step * 0.25 + sk * 0.5
        out = (math.cos(mid), 0, math.sin(mid))
        stripes.append(quad(p(t0, y0), p(t1, y0), p(t1 + sk, y1), p(t0 + sk, y1), out))
    return band, merge(*stripes)


def plate_side_hazard(half=3.0, flat=2.62, y0=0.12, y1=0.26, w=0.17):
    """Yellow/black diagonal hazard band on the 4 straight sides of the plate."""
    blacks, yellows = [], []
    for k in range(4):
        band = box(0.006, y1 - y0, 2 * flat).translate(half + 0.003, (y0 + y1) / 2, 0)
        st = []
        z = -flat + 0.02
        x = half + 0.009
        sk = (y1 - y0) * 0.9
        while z + w + sk < flat:
            st.append(quad((x, y0, z), (x, y0, z + w), (x, y1, z + w + sk), (x, y1, z + sk), (1, 0, 0)))
            z += 2 * w
        blacks.append(band.rotate(0, 90 * k, 0))
        yellows.append(merge(*st).rotate(0, 90 * k, 0))
    return merge(*blacks), merge(*yellows)


def star_points(r_out, r_in, n=5, rot=90):
    pts = []
    for i in range(2 * n):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + 180 * i / n)
        pts.append((r * math.cos(a), r * math.sin(a)))
    return pts


def crate(w, h, d, x, z, ry, y=PLATE_TOP):
    """Wooden ammo crate: body, dark strap bands, lid rim and red stencil stripe."""
    body = box(w, h, d, bevel=0.03).translate(0, h / 2, 0)
    straps = merge(box(0.06, h + 0.02, d + 0.02).translate(-w * 0.3, h / 2, 0),
                   box(0.06, h + 0.02, d + 0.02).translate(w * 0.3, h / 2, 0),
                   box(w + 0.02, 0.04, d + 0.02).translate(0, h - 0.04, 0))
    stripe = quad((-w * 0.22, h * 0.25, d / 2 + 0.012), (w * 0.22, h * 0.25, d / 2 + 0.012),
                  (w * 0.22, h * 0.6, d / 2 + 0.012), (-w * 0.22, h * 0.6, d / 2 + 0.012), (0, 0, 1))
    out = []
    for m in (body, straps, stripe):
        out.append(m.rotate(0, ry, 0).translate(x, y, z))
    return out


# --------------------------------------------------------------------------- model

m = Model("SovietFlakNest")
m.material("plate", rgb("#5f5c52"), metallic=0.1, roughness=0.85)
m.material("plate_top", rgb("#6d6a5f"), metallic=0.05, roughness=0.9)
m.material("concrete", rgb("#77746a"), metallic=0.0, roughness=0.92)
m.material("floor", rgb("#3e3d36"), metallic=0.0, roughness=0.95)
m.material("haz_y", rgb("#e8b923"), metallic=0.1, roughness=0.6)
m.material("haz_k", rgb("#1e1d1a"), metallic=0.2, roughness=0.7)
m.material("bag", rgb("#a68f5f"), metallic=0.0, roughness=0.97)
m.material("bag_dark", rgb("#857148"), metallic=0.0, roughness=0.97)
m.material("brass", rgb("#d9a83e"), metallic=0.9, roughness=0.28)
m.material("red", rgb("#c62b1f"), metallic=0.35, roughness=0.5)
m.material("olive", rgb("#5b5f4a"), metallic=0.45, roughness=0.55)
m.material("olive_lt", rgb("#7d8264"), metallic=0.4, roughness=0.55)
m.material("steel", rgb("#5d6058"), metallic=0.85, roughness=0.32)
m.material("gun_dark", rgb("#1f201c"), metallic=0.7, roughness=0.45)
m.material("rivet", rgb("#a9a99c"), metallic=0.8, roughness=0.35)
m.material("cream", rgb("#f0e6c8"), metallic=0.0, roughness=0.6)
m.material("crate", rgb("#7d6c42"), metallic=0.0, roughness=0.85)
m.material("crate_dark", rgb("#4f4428"), metallic=0.3, roughness=0.7)
m.material("lens", rgb("#6ad0ff"), roughness=0.2, emissive=rgb("#6ad0ff", 2.0))

base = m.node("Base")

# --- foundation plate with hazard edge band, inset deck and rivets
base.add(foundation_plate(), "plate")
base.add(box(5.5, 0.03, 5.5, bevel=0.3).translate(0, PLATE_TOP + 0.015, 0), "plate_top")
hk, hy = plate_side_hazard()
base.add(hk, "haz_k")
base.add(hy, "haz_y")
riv = []
for i in range(7):
    s = -2.4 + 4.8 * i / 6
    for (x, z) in ((s, 2.83), (s, -2.83), (2.83, s), (-2.83, s)):
        riv.append(cylinder(0.05, 0.03, 6).translate(x, PLATE_TOP + 0.03, z))
base.add(merge(*riv), "rivet")

# --- concrete nest wall (ring), skirt and floor
WALL_R_OUT, WALL_R_IN, WALL_TOP = 2.32, 2.0, 0.95
base.add(lathe([(WALL_R_IN, PLATE_TOP + 0.03), (WALL_R_IN, WALL_TOP - 0.05), (WALL_R_IN + 0.04, WALL_TOP),
                (WALL_R_OUT - 0.04, WALL_TOP), (WALL_R_OUT, WALL_TOP - 0.05), (WALL_R_OUT, PLATE_TOP + 0.03)][::-1],
               48, smooth=False), "concrete")
base.add(lathe([(WALL_R_OUT, 0.47), (2.43, 0.44), (2.43, PLATE_TOP + 0.03), (WALL_R_OUT, PLATE_TOP + 0.03)][::-1],
               48, smooth=False), "concrete")
base.add(disc(WALL_R_IN + 0.01, 48).translate(0, PLATE_TOP + 0.035, 0), "floor")
wk, wy = hazard_ring(WALL_R_OUT + 0.004, 0.58, 0.72, 44)
base.add(wk, "haz_k")
base.add(wy, "haz_y")

# --- two courses of sandbags on the wall crown
bags = {"bag": [], "bag_dark": []}
for layer, (n, r, y, off) in enumerate(((24, 2.2, WALL_TOP + 0.1, 0.0), (24, 2.16, WALL_TOP + 0.3, 0.5))):
    for i in range(n):
        th = 2 * math.pi * (i + off) / n + rng.uniform(-0.03, 0.03)
        sc = rng.uniform(0.92, 1.06)
        bag = rounded_box(0.62 * sc, 0.22 * rng.uniform(0.92, 1.08), 0.4 * sc)
        bag.rotate(rng.uniform(-4, 4), -math.degrees(th) + 90 + rng.uniform(-7, 7), rng.uniform(-4, 4))
        bag.translate(r * math.cos(th), y + rng.uniform(-0.015, 0.015), r * math.sin(th))
        bags["bag" if rng.rand() < 0.65 else "bag_dark"].append(bag)
for mat, lst in bags.items():
    base.add(merge(*lst), mat)

# --- spent brass casings on the floor (standing clusters + lying ones)
cas = []
FY = PLATE_TOP + 0.035
for (ang, rad) in ((150, 1.45), (162, 1.6), (140, 1.62), (170, 1.38), (155, 1.78), (205, 1.5),
                   (215, 1.68), (120, 1.5), (235, 1.45), (250, 1.62), (40, 1.55), (60, 1.7)):
    a = math.radians(ang + rng.uniform(-3, 3))
    cas.append(cylinder(0.048, 0.26, 8).translate(rad * math.cos(a), FY, rad * math.sin(a)))
for (ang, rad) in ((145, 1.3), (175, 1.75), (195, 1.35), (225, 1.85), (265, 1.4), (100, 1.75),
                   (300, 1.6), (20, 1.4), (75, 1.35)):
    a = math.radians(ang)
    c = cylinder(0.048, 0.26, 8).translate(0, -0.13, 0).rotate(0, 0, 90).rotate(0, rng.uniform(0, 180), 0)
    cas.append(c.translate(rad * math.cos(a), FY + 0.048, rad * math.sin(a)))
base.add(merge(*cas), "brass")

# --- red pedestal with hazard ring and rivets
PED_R, PED_TOP = 1.05, 0.95
base.add(lathe([(1.18, PLATE_TOP + 0.03), (1.18, 0.45), (1.08, 0.5), (0.0, 0.5)], 40, smooth=False),
         "gun_dark")
base.add(lathe([(0.0, PED_TOP), (PED_R - 0.04, PED_TOP), (PED_R, PED_TOP - 0.04), (PED_R, 0.48)][::-1], 40,
               smooth=False), "red")
pk, py = hazard_ring(PED_R + 0.003, 0.74, 0.86, 30)
base.add(pk, "haz_k")
base.add(py, "haz_y")
pr = []
for i in range(16):
    a = 2 * math.pi * (i + 0.5) / 16
    pr.append(tube_between((PED_R * math.cos(a), 0.6, PED_R * math.sin(a)),
                           ((PED_R + 0.035) * math.cos(a), 0.6, (PED_R + 0.035) * math.sin(a)), 0.035, 6))
base.add(merge(*pr), "rivet")
base.add(torus(0.9, 0.025, 40, 4).translate(0, PED_TOP, 0), "gun_dark")

# --- ammo crates on the plate (outside the wall)
for (w, h, d, x, z, ry) in ((0.7, 0.44, 0.48, 2.52, -1.5, 62), (0.56, 0.36, 0.42, 2.12, -2.42, 40),
                            (0.58, 0.36, 0.44, 2.4, 1.72, -35)):
    b_, s_, st_ = crate(w, h, d, x, z, ry)
    base.add(b_, "crate")
    base.add(s_, "crate_dark")
    base.add(st_, "red")
b_, s_, st_ = crate(0.46, 0.3, 0.36, 2.5, -1.5, 75, y=PLATE_TOP + 0.44)
base.add(b_, "crate")
base.add(s_, "crate_dark")
base.add(st_, "red")

# --------------------------------------------------------------------------- turret
turret = base.node("Turret", (0, PED_TOP, 0))
T0 = PED_TOP
turret.add(lathe([(0.0, T0), (0.86, T0), (0.86, T0 + 0.36), (0.82, T0 + 0.4), (0.0, T0 + 0.4)], 36,
                 smooth=False), "olive")
turret.add(torus(0.62, 0.02, 36, 4).translate(0, T0 + 0.4, 0), "gun_dark")
tr = []
for i in range(12):
    a = 2 * math.pi * i / 12
    tr.append(tube_between((0.86 * math.cos(a), T0 + 0.27, 0.86 * math.sin(a)),
                           (0.895 * math.cos(a), T0 + 0.27, 0.895 * math.sin(a)), 0.03, 6))
turret.add(merge(*tr), "rivet")

# housing (breech box) behind the guns
HY0, HY1 = T0 + 0.4, T0 + 0.95
turret.add(box(1.04, HY1 - HY0, 0.66, bevel=0.07).translate(0, (HY0 + HY1) / 2, 0.32), "olive")
turret.add(box(0.96, 0.05, 0.58, bevel=0.06).translate(0, HY1 + 0.02, 0.32), "olive_lt")
# red star on cream roundel on the back (+Z) face
turret.add(cylinder(0.19, 0.02, 20).rotate(90, 0, 0).translate(-0.2, (HY0 + HY1) / 2, 0.65), "cream")
turret.add(extrude(star_points(0.15, 0.062, rot=90), 0.02).rotate(90, 0, 0)
           .scale(1, -1, 1).translate(-0.2, (HY0 + HY1) / 2, 0.665), "red")
# hazard plate on the +X face
hx = 0.525
hp = [quad((hx + 0.003, HY0 + 0.12, 0.08), (hx + 0.003, HY0 + 0.12, 0.56), (hx + 0.003, HY0 + 0.28, 0.56),
           (hx + 0.003, HY0 + 0.28, 0.08), (1, 0, 0))]
turret.add(hp[0], "haz_k")
ys = []
for i in range(4):
    z0 = 0.1 + i * 0.12
    ys.append(quad((hx + 0.008, HY0 + 0.12, z0), (hx + 0.008, HY0 + 0.12, z0 + 0.055),
                   (hx + 0.008, HY0 + 0.28, z0 + 0.1), (hx + 0.008, HY0 + 0.28, z0 + 0.045), (1, 0, 0)))
turret.add(merge(*ys), "haz_y")
# sight / sensor box with blue lens
turret.add(box(0.22, 0.26, 0.24, bevel=0.03).translate(-0.33, HY1 + 0.16, 0.42), "olive_lt")
turret.add(cylinder(0.05, 0.03, 12).rotate(90, 0, 0).translate(-0.38, HY1 + 0.17, 0.54), "lens")
turret.add(box(0.28, 0.03, 0.3).translate(-0.33, HY1 + 0.305, 0.42), "olive")
# trunnion cheeks
PIV = np.array([0.0, T0 + 0.85, -0.05], np.float32)
for sx in (-1, 1):
    # profile given as (y, z); extrude along Y then roll it up into a vertical plate (thickness along X)
    cheek = extrude([(0.0, -0.42), (0.0, 0.38), (0.55, 0.38), (0.98, 0.12), (0.98, -0.2), (0.6, -0.42)], 0.11)
    cheek.rotate(0, 0, 90)
    turret.add(cheek.translate(sx * 0.62 + 0.055, T0 + 0.38, 0.0), "olive")

# --------------------------------------------------------------------------- barrels (pitch node)
ELEV = 55.0
D = np.array([0.0, math.sin(math.radians(ELEV)), -math.cos(math.radians(ELEV))], np.float32)
barrel = turret.node("Barrel", tuple(PIV.tolist()))


def P(x, t):
    return PIV + np.array([x, 0, 0], np.float32) + D * t


barrel.add(tube_between(PIV + [-0.74, 0, 0], PIV + [0.74, 0, 0], 0.12, 14), "gun_dark")
for sx in (-1, 1):
    barrel.add(tube_between(PIV + [sx * 0.69, 0, 0], PIV + [sx * 0.75, 0, 0], 0.17, 14), "olive_lt")
cradle = box(1.1, 1.0, 0.36, bevel=0.06).rotate(ELEV - 90, 0, 0)
barrel.add(cradle.translate(*(PIV + D * 0.12)), "olive")
NRM = np.array([0.0, math.cos(math.radians(ELEV)), math.sin(math.radians(ELEV))], np.float32)  # cradle top normal
barrel.add(box(1.13, 0.07, 0.39).rotate(ELEV - 90, 0, 0).translate(*(PIV + D * 0.5)), "red")

GX = (-0.32, 0.0, 0.32)
MUZ_T = 3.3
for i, x in enumerate(GX):
    # recoil jacket with bands
    barrel.add(tube_between(P(x, 0.25), P(x, 1.35), 0.135, 14), "olive")
    barrel.add(tube_between(P(x, 1.35), P(x, 1.45), 0.11, 14), "olive_lt")
    bands = [tube_between(P(x, t), P(x, t + 0.05), 0.145, 14) for t in (0.62, 0.92, 1.22)]
    barrel.add(merge(*bands), "gun_dark")
    # ammo feed clip on top of the cradle
    barrel.add(box(0.2, 0.34, 0.14, bevel=0.02).rotate(ELEV - 90, 0, 0)
               .translate(*(P(x, 0.05) + NRM * 0.24)), "brass")
    barrel.add(box(0.22, 0.36, 0.06).rotate(ELEV - 90, 0, 0)
               .translate(*(P(x, 0.05) + NRM * 0.33)), "gun_dark")
    # barrel tube, red band, muzzle brake
    barrel.add(tube_between(P(x, 1.4), P(x, 3.0), 0.075, 12), "steel")
    barrel.add(tube_between(P(x, 1.58), P(x, 1.68), 0.095, 12), "red")
    barrel.add(tube_between(P(x, 2.9), P(x, MUZ_T), 0.112, 12), "steel")
    slots = [tube_between(P(x, t), P(x, t + 0.05), 0.115, 12) for t in (3.0, 3.13)]
    barrel.add(merge(*slots), "gun_dark")
    barrel.add(tube_between(P(x, MUZ_T - 0.05), P(x, MUZ_T + 0.004), 0.065, 10), "gun_dark")
    tip = P(x, MUZ_T + 0.03)
    fx = barrel.node(f"FX_Muzzle{i + 1:02d}", tuple(tip.tolist()))
    fx.add(box(0.05, 0.05, 0.05).translate(*tip), "gun_dark")

if __name__ == "__main__":
    st = m.stats()
    allpos = np.vstack([mesh.pos for n in m._walk() for mesh in n.prims.values()])
    print(m.save(OUT), st, "bounds", allpos.min(0).round(3).tolist(), allpos.max(0).round(3).tolist())
