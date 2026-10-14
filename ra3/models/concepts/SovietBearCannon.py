"""SovietBearCannon («Ведмежа гармата») — concept: a giant mechanical bear head that fires war bears.

The turret is a riveted robot bear head in a fur ushanka with a gold star, glowing red eyes and
angry brows. The cannon sits in its open jaws and shoots helmeted war bears riding rockets;
the bear lands, flips an enemy tank and roars. A cage of spare bears waits on the deck.

Two variants (concept renders, not part of the mod build):
    SovietBearCannon_idle.glb   the turret
    SovietBearCannon_fire.glb   jaws wide open, a rocket-bear in flight, a bear on a flipped tank

Run:  python3 -I models/concepts/SovietBearCannon.py
"""
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from meshkit import (Model, box, cone, cylinder, disc, extrude, foundation_plate,  # noqa: E402
                     lathe, merge, rgb, sphere, torus, tube_between)

OUT_DIR = HERE.parent / "glb" / "concepts"
HALF = 4.0
PLATE_H = 0.4
PED_TOP = 1.1
HEAD_C = np.array([0.2, 4.3, 0.0])

DECK = {
    "plate": {"texture": "soviet/plate_steel", "tile": 8.0, "offset": 0.5},
    "plate_top": {"texture": "soviet/plate_steel", "tile": 8.0},
    "haz_y": "soviet/hazard", "haz_k": {"texture": "soviet/hazard", "white": 0.15},
    "olive": "soviet/steel_olive", "steel": "soviet/plate_steel", "gun_dark": "soviet/dark_metal",
    "red": "soviet/paint_red",
}


def ell(rx, ry, rz, c, seg=16, rings=10):
    return sphere(1.0, seg, rings).scale(rx, ry, rz).translate(*c)


def star(r_out, r_in, h, n=5):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.pi / 2 + i * math.pi / n
        pts.append((r * math.cos(a), r * math.sin(a)))
    return extrude(pts, h)


def star_facing_x(r_out, at):
    return star(r_out, r_out * 0.42, 0.04).rotate(0, 0, -90).translate(*at)


class Parts:
    """Collects meshes per material, then applies one transform to all of them."""

    def __init__(self):
        self.d = {}

    def add(self, mesh, mat):
        self.d.setdefault(mat, []).append(mesh)

    def emit(self, node, fn=lambda m: m):
        for mat, meshes in self.d.items():
            node.add(fn(merge(*meshes)), mat)


def bear(pose="stand", s=1.0, helmet=True):
    """A war bear about 1.5 m tall at s=1, facing +X, feet at y=0. Returns Parts (local coords)."""
    p = Parts()
    p.add(ell(0.45, 0.55, 0.42, (0, 0.62, 0), 12, 8), "fur")
    p.add(ell(0.3, 0.38, 0.32, (0.18, 0.62, 0), 10, 6), "fur_lt")          # belly
    p.add(sphere(0.32, 12, 8).translate(0.06, 1.28, 0), "fur")              # head
    p.add(ell(0.17, 0.13, 0.15, (0.33, 1.22, 0), 10, 6), "fur_lt")          # snout
    p.add(sphere(0.06, 8, 5).translate(0.5, 1.26, 0), "black")              # nose
    for z in (-0.12, 0.12):
        p.add(sphere(0.04, 6, 4).translate(0.33, 1.38, z), "black")         # eyes
        p.add(sphere(0.1, 8, 5).translate(-0.02, 1.55, z * 1.8), "fur_dk")  # ears
    for z in (-0.23, 0.23):                                                 # legs
        p.add(ell(0.17, 0.22, 0.16, (0.05, 0.16, z), 8, 5), "fur_dk")
    if pose == "fly":       # arms stretched forward (superman)
        for z in (-0.3, 0.3):
            p.add(ell(0.38, 0.12, 0.12, (0.5, 1.0, z), 8, 5), "fur_dk")
    elif pose == "roar":    # arms up
        for z in (-1, 1):
            p.add(ell(0.12, 0.38, 0.12, (0.05, 1.35, z * 0.55), 8, 5).rotate(z * 25, 0, 0), "fur_dk")
        p.add(ell(0.1, 0.07, 0.11, (0.4, 1.12, 0), 8, 5), "mouth")          # open mouth
    else:
        for z in (-0.5, 0.5):
            p.add(ell(0.12, 0.32, 0.12, (0.08, 0.72, z), 8, 5), "fur_dk")
    if helmet:
        p.add(lathe([(0.0, 1.62), (0.2, 1.6), (0.31, 1.46), (0.35, 1.36), (0.0, 1.36)], 12).translate(0.04, 0, 0), "helmet")
        p.add(star(0.07, 0.03, 0.02).rotate(0, 0, -90).translate(0.37, 1.48, 0), "red")
    for mat in p.d:
        p.d[mat] = [m.scale(s) for m in p.d[mat]]
    return p


def tank(at, flipped=False):
    """Small enemy (Allied) tank, ~3 m long."""
    p = Parts()
    p.add(box(3.0, 0.7, 1.9, bevel=0.3).translate(0, 0.75, 0), "tank_blue")
    for z in (-0.95, 0.95):
        p.add(box(3.1, 0.55, 0.42, bevel=0.12).translate(0, 0.35, z), "tank_dk")
    p.add(cylinder(0.72, 0.42, 16).translate(-0.2, 1.1, 0), "tank_blue")
    p.add(tube_between((-0.2, 1.32, 0), (1.9, 1.38, 0), 0.09, 8), "tank_dk")
    p.add(box(0.06, 0.3, 0.3).translate(-0.3, 0.9, 0.96), "white")
    if flipped:
        for mat in p.d:
            p.d[mat] = [m.rotate(165, 0, 12).translate(0, 1.7, 0) for m in p.d[mat]]
    for mat in p.d:
        p.d[mat] = [m.translate(*at) for m in p.d[mat]]
    return p


def bezier(p0, p1, p2, t):
    return (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2


def build(variant):
    fire = variant == "fire"
    m = Model(f"SovietBearCannon_{variant}")
    m.material("plate", rgb("#5f5c52"), metallic=0.1, roughness=0.85)
    m.material("plate_top", rgb("#6d6a5f"), metallic=0.05, roughness=0.9)
    m.material("haz_y", rgb("#e8b923"), metallic=0.1, roughness=0.6)
    m.material("haz_k", rgb("#1e1d1a"), metallic=0.2, roughness=0.7)
    m.material("olive", rgb("#5b5f4a"), metallic=0.5, roughness=0.5)
    m.material("steel", rgb("#7d8070"), metallic=0.85, roughness=0.32)
    m.material("gun_dark", rgb("#1f201c"), metallic=0.7, roughness=0.45)
    m.material("red", rgb("#c62b1f"), metallic=0.35, roughness=0.5)
    m.material("gold", rgb("#e3b23c"), metallic=0.9, roughness=0.25)
    m.material("rivet", rgb("#b3b3a6"), metallic=0.8, roughness=0.35)
    m.material("hat", rgb("#5a3a22"), metallic=0.0, roughness=0.95)
    m.material("hat_lt", rgb("#7a5534"), metallic=0.0, roughness=0.95)
    m.material("eye", rgb("#ff3b1f"), roughness=0.2, emissive=rgb("#ff2a10", 2.2))
    m.material("mouth", rgb("#5a0d0b"), roughness=0.7)
    m.material("teeth", rgb("#ece6d4"), metallic=0.4, roughness=0.3)
    m.material("glow", rgb("#ff7a2a"), roughness=0.3, emissive=rgb("#ff6a1a", 1.8))
    m.material("fur", rgb("#7a4a26"), roughness=0.9)
    m.material("fur_dk", rgb("#4e2e16"), roughness=0.9)
    m.material("fur_lt", rgb("#c79b6a"), roughness=0.9)
    m.material("black", rgb("#151212"), roughness=0.4)
    m.material("helmet", rgb("#4f5a3c"), metallic=0.5, roughness=0.45)
    m.material("white", rgb("#eef3fb"), roughness=0.4)
    m.material("tank_blue", rgb("#5a7699"), metallic=0.5, roughness=0.45)
    m.material("tank_dk", rgb("#262c35"), metallic=0.6, roughness=0.5)
    m.material("flame", rgb("#ffb347"), roughness=0.2, emissive=rgb("#ff8a1a", 2.4))
    m.material("smoke", rgb("#d9d4cb"), roughness=1.0, alpha=0.5)
    m.material("dust", rgb("#bfae8c"), roughness=1.0, alpha=0.45)

    base = m.node("Base")
    # --- 8 x 8 Soviet deck with hazard edges
    base.add(foundation_plate(size=2 * HALF - 0.04, top=PLATE_H, bevel=0.45), "plate")
    base.add(box(2 * HALF - 1.0, 0.06, 2 * HALF - 1.0, bevel=0.35).translate(0, PLATE_H + 0.03, 0), "plate_top")
    n = 14
    w = (2 * HALF - 1.1) / n
    for sgn in (-1, 1):
        for i in range(n):
            t = -HALF + 0.55 + i * w + w / 2
            mat = "haz_y" if i % 2 == 0 else "haz_k"
            base.add(box(w, 0.07, 0.32).translate(t, PLATE_H + 0.035, sgn * (HALF - 0.32)), mat)
            base.add(box(0.32, 0.07, w).translate(sgn * (HALF - 0.32), PLATE_H + 0.035, t), mat)
    # bear cage on the rear-left corner, three spare bears inside
    cx, cz = -2.9, 2.75
    base.add(box(1.9, 0.12, 1.5).translate(cx, PLATE_H + 0.06, cz), "olive")
    base.add(box(1.9, 0.1, 1.5).translate(cx, PLATE_H + 1.55, cz), "olive")
    for i in range(9):
        x = cx - 0.9 + i * 0.225
        for z in (cz - 0.72, cz + 0.72):
            base.add(cylinder(0.03, 1.45, 6).translate(x, PLATE_H + 0.1, z), "steel")
    for i in range(7):
        z = cz - 0.72 + i * 0.24
        for x in (cx - 0.92, cx + 0.92):
            base.add(cylinder(0.03, 1.45, 6).translate(x, PLATE_H + 0.1, z), "steel")
    base.add(star_facing_x(0.2, (cx + 0.96, PLATE_H + 1.62, cz)), "red")
    for k, (dx, dz, yaw) in enumerate(((-0.45, -0.2, 20), (0.25, 0.25, -30), (0.5, -0.35, 70))):
        b = bear("stand", 0.62, helmet=k != 1)
        b.emit(base, lambda mm, dx=dx, dz=dz, yaw=yaw: mm.rotate(0, yaw, 0).translate(cx + dx, PLATE_H + 0.12, cz + dz))
    # rocket rack (ammo) on the front-right corner
    for k in range(3):
        z = -3.2 + k * 0.45
        base.add(tube_between((2.4, PLATE_H + 0.3, z), (3.5, PLATE_H + 0.3, z), 0.16, 10), "red")
        base.add(cone(0.16, 0.3, 10).rotate(0, 0, -90).translate(3.5, PLATE_H + 0.3, z), "gold")
    base.add(box(1.3, 0.15, 1.4).translate(2.95, PLATE_H + 0.08, -2.75), "gun_dark")
    # --- red pedestal + bearing ring
    base.add(lathe([(0.0, PLATE_H), (2.7, PLATE_H), (2.7, 0.8), (2.5, 0.95), (2.45, PED_TOP), (0.0, PED_TOP)], 40,
                   smooth=False), "red")
    base.add(torus(2.45, 0.09, 40, 6).translate(0, PED_TOP, 0), "steel")

    # --- the turret: robot bear head, forward = +X
    tur = base.node("Turret", (0.0, PED_TOP, 0.0))
    hc = HEAD_C
    tur.add(cylinder(1.7, 1.3, 32).translate(0, PED_TOP, 0), "olive")                          # neck
    tur.add(torus(1.72, 0.08, 32, 6).translate(0, PED_TOP + 0.65, 0), "steel")
    for k in range(4):                                                                       # neck rams
        a = math.radians(45 + k * 90)
        tur.add(tube_between((1.9 * math.cos(a), PED_TOP + 0.1, 1.9 * math.sin(a)),
                             (1.45 * math.cos(a), PED_TOP + 1.9, 1.45 * math.sin(a)), 0.12, 8), "steel")
    tur.add(ell(2.25, 1.95, 2.15, hc, 32, 20), "olive")                                     # skull
    tur.add(torus(1.0, 0.05, 40, 6).scale(2.2, 1, 2.1).translate(hc[0], hc[1] - 0.35, 0), "rivet")   # plate seam
    for k in range(20):                                                                      # rivets along the seam
        a = 2 * math.pi * k / 20
        tur.add(sphere(0.07, 6, 4).translate(hc[0] + 2.2 * math.cos(a), hc[1] - 0.35, 2.1 * math.sin(a)), "rivet")
    # upper snout (muzzle) and nose
    tur.add(ell(1.25, 0.72, 1.0, (2.2, 3.85, 0), 24, 14), "steel")
    tur.add(ell(0.36, 0.26, 0.42, (3.42, 4.12, 0), 14, 8), "black")
    # mouth interior + the cannon in the jaws
    jaw_open = 32.0 if fire else 14.0
    tur.add(ell(0.95, 0.6, 0.82, (2.15, 3.2, 0), 18, 10), "mouth")
    tur.add(tube_between((1.4, 3.25, 0), (3.35, 3.32, 0), 0.4, 20), "gun_dark")
    tur.add(torus(0.42, 0.07, 20, 6).rotate(0, 0, 90).translate(3.35, 3.32, 0), "glow")
    tur.add(disc(0.34, 20).rotate(0, 0, -90).translate(3.37, 3.32, 0), "glow" if fire else "gun_dark")
    for i in range(5):                                                                       # upper teeth
        t = i / 4
        x = 2.55 + 0.75 * math.cos(t * math.pi / 2)
        z = 0.78 * math.sin((t - 0.5) * math.pi) if True else 0
        for zz in (z,):
            tur.add(cone(0.12, 0.38, 8).rotate(180, 0, 0).translate(x, 3.38, zz), "teeth")
    tur.add(cone(0.16, 0.55, 8).rotate(180, 0, 0).translate(3.15, 3.4, 0.55), "teeth")       # fangs
    tur.add(cone(0.16, 0.55, 8).rotate(180, 0, 0).translate(3.15, 3.4, -0.55), "teeth")
    # lower jaw on a hinge (opens wider when firing)
    hinge = np.array([0.9, 3.0, 0.0])
    jaw = Parts()
    jaw.add(ell(1.3, 0.38, 0.92, (2.1, 2.72, 0), 20, 10), "steel")
    for i in range(4):
        z = -0.6 + i * 0.4
        jaw.add(cone(0.11, 0.32, 8).translate(2.9 - abs(z) * 0.5, 2.98, z), "teeth")
    jaw.emit(tur, lambda mm: mm.translate(*(-hinge)).rotate(0, 0, -jaw_open).translate(*hinge))
    # eyes + angry brows
    for z in (-0.85, 0.85):
        tur.add(sphere(0.3, 16, 10).translate(1.78, 4.72, z), "eye")
        tur.add(torus(0.32, 0.06, 16, 6).rotate(0, 0, 90).translate(1.82, 4.72, z), "gun_dark")
        tur.add(box(0.2, 0.2, 0.85).rotate(-26 if z > 0 else 26, 0, -10).translate(1.9, 5.15, z * 0.95), "gun_dark")
    # ears poking out of the ushanka
    for z in (-1.45, 1.45):
        tur.add(cylinder(0.62, 0.3, 20).rotate(0, 0, 90).translate(0.1, 6.15, z), "olive")
        tur.add(disc(0.42, 16).rotate(0, 0, -90).translate(0.12, 6.15, z), "red")
    # ushanka: fur dome, front fold with a gold star, side flaps
    tur.add(lathe([(0.0, 6.85), (1.1, 6.75), (1.7, 6.35), (2.0, 5.75), (0.0, 5.75)], 28).translate(hc[0] - 0.1, 0, 0), "hat")
    tur.add(torus(1.95, 0.32, 32, 10).scale(1.0, 0.9, 1.0).translate(hc[0] - 0.1, 5.8, 0), "hat_lt")
    tur.add(star_facing_x(0.38, (hc[0] + 2.1, 5.9, 0)), "gold")
    for z in (-1, 1):
        tur.add(box(1.3, 1.5, 0.3, bevel=0.12).rotate(z * -12, 0, 0).translate(hc[0] - 0.2, 4.95, z * 2.12), "hat_lt")
    # cheek exhausts with smoke
    for z in (-1, 1):
        p0 = (0.0, 3.3, z * 2.0)
        p1 = (-0.7, 5.1, z * 2.35)
        tur.add(tube_between(p0, p1, 0.17, 10), "gun_dark")
        tur.add(cylinder(0.2, 0.12, 10).translate(*p1), "steel")
        for k in range(3):
            tur.add(sphere(0.32 + k * 0.12, 10, 6).translate(p1[0] - 0.15 * k, p1[1] + 0.45 + k * 0.5, p1[2]), "smoke")
    # red star on the forehead plate and a red stripe
    tur.add(star_facing_x(0.3, (hc[0] + 2.2, 4.25, 0)) if False else star(0.3, 0.12, 0.04).rotate(0, 0, -90).rotate(0, 0, 0).translate(2.15, 5.35, 0), "red")

    muzzle = (3.4, 3.32, 0.0)
    fx = tur.node("FX_Muzzle01", muzzle)
    fx.add(box(0.05, 0.05, 0.05).translate(*muzzle), "glow")

    if fire:
        sc = base.node("FX_Scene", (0, 0, 0))
        # muzzle flash
        sc.add(ell(0.9, 0.7, 0.8, (3.9, 3.35, 0), 14, 8), "flame")
        sc.add(sphere(1.2, 14, 8).translate(4.2, 3.5, 0), "smoke")
        # flight arc from the jaws to the landing point
        P0, P1, P2 = np.array([3.6, 3.4, 0.0]), np.array([8.5, 11.0, 0.6]), np.array([13.5, 1.0, 1.2])
        tb = 0.42
        pts = [bezier(P0, P1, P2, t) for t in np.linspace(0.0, tb, 14)]
        for i in range(len(pts) - 1):
            sc.add(tube_between(pts[i], pts[i + 1], 0.16 + 0.22 * (1 - i / 13), 8), "smoke")
        pos = bezier(P0, P1, P2, tb)
        tan = bezier(P0, P1, P2, tb + 0.01) - pos
        pitch = math.degrees(math.atan2(tan[1], tan[0]))
        # the rocket (along +X) with fins and flame, and the bear riding it
        rk = Parts()
        rk.add(lathe([(0.0, -1.1), (0.28, -1.0), (0.3, 0.6), (0.18, 1.0), (0.0, 1.15)], 14).rotate(0, 0, -90), "red")
        rk.add(torus(0.3, 0.04, 14, 5).rotate(0, 0, 90).translate(0.2, 0, 0), "gold")
        for k in range(4):
            rk.add(box(0.45, 0.04, 0.5).translate(-0.9, 0, 0.35).rotate(k * 90, 0, 0), "gold")
        rk.add(cone(0.3, 1.2, 12).rotate(0, 0, 90).translate(-1.05, 0, 0), "flame")
        rk.add(sphere(0.36, 10, 6).translate(-1.25, 0, 0), "flame")
        b = bear("fly", 1.0)
        for mat, meshes in b.d.items():
            for mm in meshes:
                rk.add(mm.copy().rotate(0, 0, -78).translate(-0.6, 0.15, 0), mat)
        rk.emit(sc, lambda mm: mm.rotate(0, 0, pitch).translate(*pos))
        # landing: a flipped enemy tank, a roaring bear on top, dust and a second tank fleeing
        land = P2
        tank(land, flipped=True).emit(sc)
        bear("roar", 1.15).emit(sc, lambda mm: mm.rotate(0, 200, 0).translate(land[0] + 0.1, 2.45, land[2]))
        for k in range(9):
            a = 2 * math.pi * k / 9
            sc.add(sphere(0.55 + 0.2 * (k % 3), 10, 6).translate(land[0] + 2.0 * math.cos(a), 0.45, land[2] + 2.0 * math.sin(a)), "dust")
        tank((15.5, 0.0, -3.5)).emit(sc, lambda mm: mm.translate(-15.5, 0, 3.5).rotate(0, 25, 0).translate(15.5, 0, -3.5))
    return m


if __name__ == "__main__":
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for v in ("idle", "fire"):
        mdl = build(v)
        print(mdl.save(OUT_DIR / f"SovietBearCannon_{v}.glb", textures=True, bindings=DECK), mdl.stats())
