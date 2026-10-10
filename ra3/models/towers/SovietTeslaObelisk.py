"""SovietTeslaObelisk (Тесла-обеліск) — procedural 3D model generator.

Run:  python3 -I models/towers/SovietTeslaObelisk.py
Out:  models/glb/SovietTeslaObelisk.glb

Tapering olive-steel four-sided obelisk with red paint strips and riveted corner seams,
standing on a red/olive two-tier pedestal (hazard-stripe faces, red star emblem).
Three stacked copper coil rings with glowing insulator tips wrap the shaft; a glowing
tesla orb sits on a clawed spire at the top (node "Emitter"). Three capacitor pylons
stand on the plate corners. Lightning is drawn by the game, not modelled.
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODELS = HERE.parent
sys.path.insert(0, str(MODELS))

import numpy as np  # noqa: E402

from meshkit import (PLATE_TOP, Mesh, Model, box, cylinder, extrude, foundation_plate,  # noqa: E402
                     lathe, merge, rgb, sphere, torus, tube_between)

NAME = "SovietTeslaObelisk"
OUT = MODELS / "glb" / f"{NAME}.glb"

# ----------------------------------------------------------------------------- dimensions
T1_HALF, T1_Y0, T1_Y1 = 1.875, PLATE_TOP, PLATE_TOP + 1.0          # red lower tier
T2_HALF, T2_Y1 = 1.375, T1_Y1 + 0.7                                # olive upper tier
SH_Y0, SH_Y1 = T2_Y1, T2_Y1 + 6.0                                  # obelisk shaft
SH_H0, SH_H1 = 0.95, 0.375                                         # shaft half widths
COL_Y1 = SH_Y1 + 0.45                                              # red collar top
ORB_Y, ORB_R = COL_Y1 + 1.2, 0.5
RINGS = [(3.40, 2.00), (5.05, 1.66), (6.60, 1.30)]                 # (height, radius)
PYLONS = [(-2.35, 2.35), (2.35, 2.35), (2.35, -2.35)]


def shaft_half(y):
    return SH_H0 + (SH_H1 - SH_H0) * (y - SH_Y0) / (SH_Y1 - SH_Y0)


# ----------------------------------------------------------------------------- helpers
def _rot_y_to(n):
    """Rotation matrix taking +Y onto unit vector n."""
    y = np.array([0, 1, 0], np.float32)
    n = np.asarray(n, np.float32)
    n = n / np.linalg.norm(n)
    axis = np.cross(y, n)
    s = float(np.linalg.norm(axis))
    c = float(np.dot(y, n))
    if s < 1e-6:
        return np.eye(3, dtype=np.float32) if c > 0 else np.diag([1, -1, -1]).astype(np.float32)
    k = axis / s
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]], np.float32)
    return np.eye(3, dtype=np.float32) + s * K + (1 - c) * (K @ K)


def aligned(mesh, n, at):
    """Re-orient a mesh built along +Y so that +Y points along n, then move it to `at`."""
    r = _rot_y_to(n)
    mesh.pos = mesh.pos @ r.T
    mesh.nrm = mesh.nrm @ r.T
    return mesh.translate(*at)


def rivet(at, n, r=0.05):
    m = lathe([(r, 0.0), (r * 0.65, r * 0.6), (0.0, r * 0.85)], 5)
    return aligned(m, n, at)


def poly(points, n):
    """Flat single-sided convex polygon (fan) facing n."""
    pts = np.asarray(points, np.float32)
    k = len(pts)
    idx = [(0, i, i + 1) for i in range(1, k - 1)]
    m = Mesh(pts, [n] * k, [(0, 0)] * k, idx)
    tri = m.pos[m.idx]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    bad = (fn @ np.asarray(n, np.float32)) < 0
    m.idx[bad] = m.idx[bad][:, ::-1]
    return m


def _clip(poly2, axis, val, keep_greater):
    out = []
    for i in range(len(poly2)):
        a, b = poly2[i], poly2[(i + 1) % len(poly2)]
        ina = (a[axis] >= val) if keep_greater else (a[axis] <= val)
        inb = (b[axis] >= val) if keep_greater else (b[axis] <= val)
        if ina:
            out.append(a)
        if ina != inb:
            t = (val - a[axis]) / (b[axis] - a[axis])
            out.append((a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])))
    return out


def hazard_panel(origin, U, V, n, W, H, period=0.32, slant=0.6, depth=0.012):
    """Diagonal yellow/black hazard panel on a face.

    origin: 3D lower-left corner; U, V: unit in-plane axes (right, up); n: outward normal.
    Returns (black_backing, yellow_stripes) meshes.
    """
    o, U, V, n = (np.asarray(a, np.float32) for a in (origin, U, V, n))

    def to3(pts2, off):
        return [o + u * U + v * V + n * off for u, v in pts2]

    black = poly(to3([(0, 0), (W, 0), (W, H), (0, H)], depth), n)
    yel = []
    w = period / 2
    k = -int(math.ceil(slant * H / period)) - 1
    while k * period < W + period:
        u0 = k * period
        band = [(u0, 0), (u0 + w, 0), (u0 + w + slant * H, H), (u0 + slant * H, H)]
        c = band
        for axis, val, g in ((0, 0.0, True), (0, W, False), (1, 0.0, True), (1, H, False)):
            c = _clip(c, axis, val, g)
            if len(c) < 3:
                break
        if len(c) >= 3:
            yel.append(poly(to3(c, depth * 2), n))
        k += 1
    return black, merge(*yel)


def star_points(r_out, r_in, n=5):
    """Star polygon in (x, z); top point at -z so it ends up at +y after rotate(90,0,0)."""
    pts = []
    for i in range(2 * n):
        a = math.pi * i / n
        r = r_out if i % 2 == 0 else r_in
        pts.append((r * math.sin(a), -r * math.cos(a)))
    return pts


def coil(R, r, y, ridges=16, segs=64, tube=6, depth=0.2):
    """Copper coil ring: torus with winding ridges."""
    m = torus(R, r, segs, tube)
    p = m.pos
    ang = np.arctan2(p[:, 2], p[:, 0])
    rad = np.sqrt(p[:, 0] ** 2 + p[:, 2] ** 2)
    rad[rad == 0] = 1
    cx, cz = p[:, 0] / rad * R, p[:, 2] / rad * R
    f = 1.0 + depth * np.cos(ang * ridges) ** 2 - depth * 0.5
    off = np.stack([p[:, 0] - cx, p[:, 1], p[:, 2] - cz], 1) * f[:, None]
    m.pos = np.stack([cx, np.zeros_like(cx), cz], 1).astype(np.float32) + off.astype(np.float32)
    return m.translate(0, y, 0)


def insulator(at, h=0.32, r=0.09):
    """Ceramic stack (returned) + glowing tip centre."""
    x, y, z = at
    prof = [(r * 0.55, 0), (r * 1.15, h * 0.18), (r * 0.5, h * 0.36), (r * 0.95, h * 0.56),
            (r * 0.45, h * 0.78), (r * 0.45, h), (0, h)]
    return lathe(prof, 6).translate(x, y, z), (x, y + h + r * 0.75, z)


# ----------------------------------------------------------------------------- model
def build():
    m = Model(NAME)
    m.material("plate", rgb("#7c796d"), metallic=0.25, roughness=0.8)
    m.material("plate_dark", rgb("#3a3933"), metallic=0.3, roughness=0.7)
    m.material("red", rgb("#c62b1f"), metallic=0.35, roughness=0.5)
    m.material("red_dark", rgb("#7a1810"), metallic=0.35, roughness=0.55)
    m.material("olive", rgb("#5b5f4a"), metallic=0.55, roughness=0.45)
    m.material("olive_light", rgb("#7d8264"), metallic=0.5, roughness=0.5)
    m.material("steel", rgb("#a9a99c"), metallic=0.85, roughness=0.3)
    m.material("gunmetal", rgb("#3b3c35"), metallic=0.8, roughness=0.4)
    m.material("copper", rgb("#b4682f"), metallic=0.9, roughness=0.28)
    m.material("hazard_y", rgb("#e8b923"), metallic=0.1, roughness=0.6)
    m.material("hazard_k", rgb("#1e1d1a"), metallic=0.1, roughness=0.7)
    m.material("cream", rgb("#f0e6c8"), metallic=0.1, roughness=0.5)
    m.material("ceramic", rgb("#d6d3c6"), metallic=0.0, roughness=0.25)
    m.material("tesla", rgb("#8fdcff"), roughness=0.15, emissive=rgb("#6ad0ff", 2.5))
    m.material("tesla_tip", rgb("#6ad0ff"), roughness=0.2, emissive=rgb("#3fb4ff", 1.6))
    m.material("tesla_halo", rgb("#2a8fd0"), roughness=0.1, emissive=rgb("#2a9be8", 1.2), alpha=0.38)

    base = m.node("Base")

    # ---- foundation plate
    base.add(foundation_plate(), "plate_dark")
    base.add(box(5.5, 0.04, 5.5, bevel=0.3).translate(0, PLATE_TOP + 0.02, 0), "plate")
    # hazard band around the plate sides
    side = 6.0 - 2 * 0.35
    for ry in (0, 90, 180, 270):
        blk, yel = hazard_panel((3.0, 0.1, side / 2), (0, 0, -1), (0, 1, 0), (1, 0, 0),
                                side, 0.16, period=0.3, slant=0.8, depth=0.006)
        base.add(blk.rotate(0, ry, 0), "hazard_k")
        base.add(yel.rotate(0, ry, 0), "hazard_y")
    # plate seams + rivets
    for ry in (0, 90):
        base.add(box(5.3, 0.012, 0.05).rotate(0, ry + 45, 0).translate(0, PLATE_TOP + 0.04, 0), "plate_dark")
    for i in range(4):
        a = math.radians(90 * i)
        for t in (-2.0, -1.0, 1.0, 2.0):
            x, z = 2.55 * math.cos(a) - t * math.sin(a), 2.55 * math.sin(a) + t * math.cos(a)
            base.add(rivet((x, PLATE_TOP + 0.04, z), (0, 1, 0), 0.06), "steel")
    # painted floor ring (dark groove + red dashes)
    yf = PLATE_TOP + 0.045
    base.add(lathe([(2.9, yf), (2.55, yf)], 48), "plate_dark")
    for i in range(20):
        a0 = i * 18.0
        base.add(lathe([(2.82, yf + 0.004), (2.63, yf + 0.004)], 3, a0, a0 + 10.0), "red")

    # ---- pedestal: red lower tier
    base.add(box(2 * T1_HALF + 0.2, 0.12, 2 * T1_HALF + 0.2, bevel=0.12).translate(0, T1_Y0 + 0.06, 0), "gunmetal")
    base.add(box(2 * T1_HALF, T1_Y1 - T1_Y0, 2 * T1_HALF, bevel=0.12).translate(0, (T1_Y0 + T1_Y1) / 2, 0), "red")
    base.add(box(2 * T1_HALF + 0.06, 0.1, 2 * T1_HALF + 0.06, bevel=0.14).translate(0, T1_Y1 - 0.05, 0), "red_dark")
    # rivet rows along the top of every face
    for ry in (0, 90, 180, 270):
        for i in range(7):
            t = -1.5 + 3.0 * i / 6
            base.add(rivet((T1_HALF + 0.03, T1_Y1 - 0.2, t), (1, 0, 0), 0.05).rotate(0, ry, 0), "steel")
    # hazard faces (+x / -x)
    fw, fh, fy = 3.1, 0.42, T1_Y0 + 0.2
    for ry in (0, 180):
        blk, yel = hazard_panel((T1_HALF, fy, fw / 2), (0, 0, -1), (0, 1, 0), (1, 0, 0), fw, fh)
        base.add(blk.rotate(0, ry, 0), "hazard_k")
        base.add(yel.rotate(0, ry, 0), "hazard_y")
    # star emblem (+z / -z)
    ey = (T1_Y0 + T1_Y1) / 2 - 0.05
    for ry in (0, 180):
        disc = cylinder(0.4, 0.04, 20).rotate(90, 0, 0).translate(0, ey, T1_HALF)
        ring = torus(0.4, 0.035, 20, 6).rotate(90, 0, 0).translate(0, ey, T1_HALF + 0.04)
        star = extrude(star_points(0.3, 0.12), 0.03).rotate(90, 0, 0).translate(0, ey, T1_HALF + 0.04)
        base.add(disc.rotate(0, ry, 0), "cream")
        base.add(ring.rotate(0, ry, 0), "gunmetal")
        base.add(star.rotate(0, ry, 0), "red")
        # flanking hazard strips beside the emblem
        for sx in (-1, 1):
            blk, yel = hazard_panel((0.75 if sx > 0 else -1.55, fy, T1_HALF), (1, 0, 0), (0, 1, 0), (0, 0, 1),
                                    0.8, fh, period=0.3)
            base.add(blk.rotate(0, ry, 0), "hazard_k")
            base.add(yel.rotate(0, ry, 0), "hazard_y")

    # ---- pedestal: olive upper tier
    base.add(box(2 * T2_HALF, T2_Y1 - T1_Y1, 2 * T2_HALF, bevel=0.1).translate(0, (T1_Y1 + T2_Y1) / 2, 0), "olive")
    base.add(box(2 * T2_HALF + 0.05, 0.1, 2 * T2_HALF + 0.05, bevel=0.12).translate(0, T2_Y1 - 0.12, 0), "red")
    base.add(box(2 * T2_HALF - 0.2, 0.03, 2 * T2_HALF - 0.2, bevel=0.1).translate(0, T2_Y1 + 0.015, 0), "olive_light")
    for ry in (0, 90, 180, 270):
        for i in range(4):
            t = -0.9 + 1.8 * i / 3
            base.add(rivet((T2_HALF + 0.02, T1_Y1 + 0.2, t), (1, 0, 0), 0.045).rotate(0, ry, 0), "steel")

    # ---- obelisk shaft
    sq = math.sqrt(2)
    # base flare
    base.add(lathe([(1.25 * sq, SH_Y0), (SH_H0 * sq + 0.02, SH_Y0 + 0.35)], 4, 45, 405).flat(), "gunmetal")
    base.add(lathe([(SH_H0 * sq, SH_Y0), (SH_H1 * sq, SH_Y1)], 4, 45, 405).flat(), "olive")
    dh = (SH_H1 - SH_H0) / (SH_Y1 - SH_Y0)
    fn = np.array([1.0, -dh, 0.0]) / math.hypot(1.0, dh)
    # red paint strips (centre of each face) and corner seams with rivets
    for ry in (0, 90, 180, 270):
        ya, yb = SH_Y0 + 0.4, SH_Y1 - 0.05
        wa, wb = shaft_half(ya) * 0.2, shaft_half(yb) * 0.2
        strip = poly([(shaft_half(ya) + 0.012, ya, -wa), (shaft_half(ya) + 0.012, ya, wa),
                      (shaft_half(yb) + 0.012, yb, wb), (shaft_half(yb) + 0.012, yb, -wb)], tuple(fn))
        base.add(strip.rotate(0, ry, 0), "red")
        # thin dark pinstripes either side of the red strip
        for s in (-1, 1):
            ps = poly([(shaft_half(ya) + 0.014, ya, s * wa), (shaft_half(ya) + 0.014, ya, s * (wa + 0.04)),
                       (shaft_half(yb) + 0.014, yb, s * (wb + 0.025)), (shaft_half(yb) + 0.014, yb, s * wb)],
                      tuple(fn))
            base.add(ps.rotate(0, ry, 0), "red_dark")
        c0 = np.array([SH_H0, SH_Y0 + 0.3, SH_H0 - 0.0])
        c1 = np.array([SH_H1, SH_Y1, SH_H1])
        c0[[0, 2]] = shaft_half(c0[1])
        base.add(tube_between(c0 + [0.01, 0, 0.01], c1 + [0.01, 0, 0.01], 0.045, 4).rotate(0, ry, 0), "olive_light")
        nrm = np.array([1.0, 0.1, 1.0])
        y = SH_Y0 + 0.55
        while y < SH_Y1 - 0.2:
            h = shaft_half(y)
            base.add(rivet((h + 0.035, y, h + 0.035), nrm, 0.045).rotate(0, ry, 0), "steel")
            y += 0.5
    # shaft cap + red collar
    base.add(lathe([(SH_H1 * sq + 0.08, SH_Y1 - 0.12), (SH_H1 * sq + 0.08, SH_Y1)], 4, 45, 405).flat(), "gunmetal")
    base.add(box(1.0, COL_Y1 - SH_Y1, 1.0, bevel=0.06).translate(0, (SH_Y1 + COL_Y1) / 2, 0), "red")
    base.add(box(1.06, 0.06, 1.06, bevel=0.07).translate(0, COL_Y1 - 0.03, 0), "olive_light")
    for ry in (0, 90, 180, 270):
        for t in (-0.3, 0.0, 0.3):
            base.add(rivet((0.505, SH_Y1 + 0.2, t), (1, 0, 0), 0.04).rotate(0, ry, 0), "steel")

    # ---- copper coil rings with spokes, clamps and glowing insulator tips
    tips = []
    for y, R in RINGS:
        h = shaft_half(y)
        base.add(box(2 * h + 0.12, 0.22, 2 * h + 0.12, bevel=0.05).translate(0, y, 0), "gunmetal")
        base.add(coil(R, 0.13, y), "copper")
        for k in range(4):
            a = math.radians(45 + 90 * k)
            d = np.array([math.cos(a), 0, math.sin(a)])
            p0 = np.array([0, y, 0]) + d * (h * sq + 0.05)
            p1 = np.array([0, y, 0]) + d * (R - 0.08)
            base.add(tube_between(p0, p1, 0.05, 6), "gunmetal")
            base.add(tube_between(p0 + [0, -0.45, 0], p1 + [0, -0.06, 0], 0.03, 5), "gunmetal")  # brace
            ins, tip = insulator(tuple(np.array([0, y + 0.1, 0]) + d * R))
            base.add(ins, "ceramic")
            tips.append(tip)
    for t in tips:
        base.add(sphere(0.1, 6, 4).translate(*t), "tesla_tip")

    # ---- capacitor pylons on three plate corners
    for (px, pz) in PYLONS:
        y0 = PLATE_TOP + 0.04
        base.add(box(0.78, 0.12, 0.78, bevel=0.08).translate(px, y0 + 0.06, pz), "gunmetal")
        base.add(box(0.6, 0.6, 0.6, bevel=0.06).translate(px, y0 + 0.42, pz), "olive")
        base.add(box(0.64, 0.08, 0.64, bevel=0.07).translate(px, y0 + 0.55, pz), "red")
        base.add(box(0.64, 0.05, 0.64, bevel=0.07).translate(px, y0 + 0.25, pz), "hazard_y")
        base.add(cylinder(0.16, 0.08, 10).translate(px, y0 + 0.72, pz), "copper")
        ins, tip = insulator((px, y0 + 0.8, pz), h=0.36, r=0.1)
        base.add(ins, "ceramic")
        base.add(sphere(0.11, 6, 4).translate(*tip), "tesla_tip")
        # feed cable to the pedestal
        d = np.array([px, 0, pz]) / math.hypot(px, pz)
        a = np.array([px, y0 + 0.07, pz]) - d * 0.35
        b = d * (T1_HALF * sq - 0.25) + [0, y0 + 0.07, 0]
        mid = (a + b) / 2 + [0, 0.04, 0]
        base.add(tube_between(a, mid, 0.06, 6), "hazard_k")
        base.add(tube_between(mid, b, 0.06, 6), "hazard_k")

    # ---- Emitter: spire + clawed orb holder + tesla orb
    em = base.node("Emitter", (0, COL_Y1, 0))
    em.add(lathe([(0.32 * sq, COL_Y1), (0.2 * sq, COL_Y1 + 0.75)], 4, 45, 405).flat(), "olive")
    em.add(lathe([(0.24 * sq, COL_Y1 + 0.75), (0.24 * sq, COL_Y1 + 0.85)], 4, 45, 405).flat(), "gunmetal")
    em.add(cylinder(0.12, 0.25, 10).translate(0, COL_Y1 + 0.85, 0), "copper")
    em.add(torus(0.2, 0.05, 16, 6).translate(0, COL_Y1 + 0.95, 0), "copper")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        d = np.array([math.cos(a), 0, math.sin(a)])
        p0 = np.array([0, COL_Y1 + 0.9, 0]) + d * 0.18
        p1 = np.array([0, ORB_Y - 0.3, 0]) + d * 0.62
        p2 = np.array([0, ORB_Y + 0.15, 0]) + d * 0.6
        em.add(tube_between(p0, p1, 0.04, 5), "copper")
        em.add(tube_between(p1, p2, 0.035, 5), "copper")
        em.add(sphere(0.05, 6, 4).translate(*p2), "tesla_tip")
    em.add(sphere(ORB_R, 16, 12).translate(0, ORB_Y, 0), "tesla")
    em.add(sphere(ORB_R * 1.45, 14, 8).translate(0, ORB_Y, 0), "tesla_halo")

    fx = em.node("FX_Muzzle01", (0, ORB_Y, 0))
    fx.add(box(0.05, 0.05, 0.05).translate(0, ORB_Y, 0), "tesla")
    gy = RINGS[1][0]
    glow = base.node("FX_Glow", (0, gy, 0))
    glow.add(box(0.05, 0.05, 0.05).translate(0, gy, 0), "tesla")
    return m


if __name__ == "__main__":
    model = build()
    print(model.save(OUT), model.stats())
