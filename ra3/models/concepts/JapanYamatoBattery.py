"""JapanYamatoBattery («Ямато») — concept: a coastal triple 46 cm gun turret, as on the battleship Yamato.

A battleship turret set on land: reinforced concrete casemate with a barbette ring, a flat-sided
armoured gun house with a sloped face, three long guns with canvas blast bags, the wide
rangefinder "ears" at the back, vents, hatches and rivets. Navy grey with Empire crimson/black trim.

Firing (realistic): one gun fires — orange flash and a big smoke cloud; three shells in flight;
impacts down-range throw up dirt and leave craters around a destroyed tank.

Nodes: Base > Turret > Barrel > FX_Muzzle01..03.  Forward = +X.
Run:  python3 -I models/concepts/JapanYamatoBattery.py
"""
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))

from meshkit import Model, box, cylinder, disc, extrude, lathe, merge, sphere, torus, tube_between  # noqa: E402
from _empire_common import DECK, PLATE_H, add_all, deck, ell, materials, rising_sun, tank  # noqa: E402

OUT_DIR = HERE.parent / "glb" / "concepts"
RING_TOP = PLATE_H + 1.3
TRUNNION_X, TRUNNION_Y = 1.6, RING_TOP + 1.25
ELEV = 14.0
GUN_LEN = 11.0
GUN_Z = (-1.25, 0.0, 1.25)


def gun_mesh():
    """One 46 cm gun along +X from x=0 (built along +Y, turned)."""
    prof = [(0.0, 0.0), (0.42, 0.0), (0.42, 1.6), (0.36, 1.8), (0.33, 6.0), (0.28, 10.4), (0.31, 10.5),
            (0.31, GUN_LEN), (0.2, GUN_LEN), (0.0, GUN_LEN)]
    return lathe(prof, 22).rotate(0, 0, -90)


def aim(mesh, z=0.0):
    return mesh.rotate(0, 0, ELEV).translate(TRUNNION_X, TRUNNION_Y, z)


def build(variant):
    fire = variant == "fire"
    m = Model(f"JapanYamatoBattery_{variant}")
    materials(m)
    base = m.node("Base")
    deck(base)
    # --- concrete casemate (octagon) with a crimson band and the barbette ring
    oct8 = [(3.6 * math.cos(math.radians(22.5 + k * 45)), 3.6 * math.sin(math.radians(22.5 + k * 45))) for k in range(8)]
    base.add(extrude(oct8, 0.9).translate(0, PLATE_H, 0), "concrete")
    base.add(extrude(oct8, 0.12, cap=False).scale(1.01, 1, 1.01).translate(0, PLATE_H + 0.6, 0), "crimson")
    base.add(cylinder(3.0, 0.4, 48).translate(0, PLATE_H + 0.9, 0), "navy_dk")
    base.add(torus(3.0, 0.06, 48, 6).translate(0, RING_TOP, 0), "black")
    # ready-ammunition hatch and a ladder on the casemate side
    base.add(box(0.1, 0.6, 1.0).translate(-3.32, PLATE_H + 0.45, 1.8), "black")
    for k in range(5):
        base.add(box(0.06, 0.04, 0.5).translate(-3.36, PLATE_H + 0.12 + k * 0.17, -1.6), "silver")

    # --- the gun house (Turret)
    tur = base.node("Turret", (0.0, RING_TOP, 0.0))
    house = [(2.4, -2.2), (2.4, 2.2), (0.6, 2.9), (-3.2, 2.9), (-3.6, 2.3), (-3.6, -2.3), (-3.2, -2.9), (0.6, -2.9)]
    tur.add(extrude(house, 1.7).translate(0, RING_TOP, 0), "navy")
    roof = [(2.0, -2.0), (2.0, 2.0), (0.5, 2.6), (-3.0, 2.6), (-3.35, 2.1), (-3.35, -2.1), (-3.0, -2.6), (0.5, -2.6)]
    tur.add(extrude(roof, 0.55).translate(0, RING_TOP + 1.7, 0), "navy")
    tur.add(extrude(house, 0.12, cap=False).scale(1.01, 1, 1.01).translate(0, RING_TOP + 0.05, 0), "black")
    # sloped face plate with three gun ports
    tur.add(box(0.5, 2.2, 5.0).rotate(0, 0, -22).translate(2.45, RING_TOP + 1.15, 0), "navy")
    # rangefinder "ears" at the rear of the roof
    rf_y = RING_TOP + 2.55
    tur.add(box(1.0, 0.7, 2.2, bevel=0.2).translate(-2.4, rf_y, 0), "navy_dk")
    tur.add(tube_between((-2.4, rf_y + 0.1, -3.6), (-2.4, rf_y + 0.1, 3.6), 0.24, 14), "navy_dk")
    for z in (-3.6, 3.6):
        tur.add(box(0.7, 0.7, 0.4, bevel=0.1).translate(-2.4, rf_y + 0.1, z), "navy")
        tur.add(disc(0.18, 12).rotate(0, 0, -90).translate(-2.04, rf_y + 0.1, z), "black")
    # sighting hoods, vents, hatches, rivets, a crimson roof band and the rising-sun emblem
    for z in (-1.8, 1.8):
        tur.add(box(0.8, 0.35, 0.6, bevel=0.1).translate(0.6, RING_TOP + 2.4, z), "navy_dk")
        tur.add(box(0.06, 0.12, 0.45).translate(1.02, RING_TOP + 2.4, z), "black")
    for k in range(3):
        tur.add(box(0.5, 0.25, 0.9).translate(-0.8 - k * 0.8, RING_TOP + 2.37, 0), "navy_dk")
    tur.add(extrude(roof, 0.1, cap=False).scale(1.015, 1, 1.015).translate(0, RING_TOP + 2.05, 0), "crimson")
    for zs in (-1, 1):
        back, red = rising_sun(0.7, 12)
        tur.add(back.translate(-1.4, RING_TOP + 0.95, zs * 2.92), "paper")
        tur.add(red.translate(-1.4, RING_TOP + 0.95, zs * 2.94), "crimson")
        for k in range(10):
            tur.add(sphere(0.045, 6, 4).translate(-3.0 + k * 0.38, RING_TOP + 0.3, zs * 2.93), "silver")

    # --- three guns (one Barrel node: they elevate together)
    bar = tur.node("Barrel", (TRUNNION_X, TRUNNION_Y, 0.0))
    tips = []
    for z in GUN_Z:
        bar.add(aim(gun_mesh(), z), "navy_dk")
        bar.add(aim(ell(0.9, 0.62, 0.55, (0.3, 0, 0), 14, 8), z), "paper")          # canvas blast bag
        for k in range(4):
            bar.add(aim(torus(0.6 - k * 0.02, 0.03, 14, 4).rotate(0, 0, 90).translate(-0.3 + k * 0.3, 0, 0), z), "silver")
        bar.add(aim(cylinder(0.32, 0.12, 22).rotate(0, 0, -90).translate(GUN_LEN - 0.12, 0, 0), z), "black")
        a = math.radians(ELEV)
        tips.append((TRUNNION_X + GUN_LEN * math.cos(a), TRUNNION_Y + GUN_LEN * math.sin(a), z))
    for i, t in enumerate(tips, 1):
        fx = bar.node(f"FX_Muzzle0{i}", t)
        fx.add(box(0.05, 0.05, 0.05).translate(*t), "black")

    if fire:
        sc = base.node("FX_Scene", (0, 0, 0))
        tip = np.array(tips[1])
        a = math.radians(ELEV)
        d = np.array([math.cos(a), math.sin(a), 0.0])
        # muzzle flash (short) and the big smoke cloud that rolls forward
        sc.add(ell(1.6, 1.0, 1.1, tip + d * 1.3, 18, 10), "flash")
        sc.add(ell(0.8, 0.6, 0.6, tip + d * 0.6, 14, 8), "white_hot")
        rng = np.random.RandomState(5)
        for k in range(16):
            c = tip + d * rng.uniform(1.5, 6.0) + rng.normal(0, 0.8, 3) * [1, 0.6, 1.2]
            sc.add(sphere(rng.uniform(0.9, 1.9), 12, 8).translate(*c), "smoke" if k % 3 else "smoke_dk")
        for z in GUN_Z[::2]:
            t = np.array([tip[0], tip[1], z])
            sc.add(sphere(0.5, 10, 6).translate(*(t + d * 0.6)), "smoke")
        # three shells in flight (with faint heat trails)
        # impacts down-range (ballistic arc from the muzzle): shells, dirt columns, craters, a wrecked tank
        T = np.array([13.0, 0.0, -13.0])
        apex = (tip + T) / 2 + np.array([0.0, 9.0, 0.0])
        for k, t in enumerate((0.42, 0.5, 0.58)):
            p = (1 - t) ** 2 * tip + 2 * (1 - t) * t * apex + t ** 2 * T + np.array([0, 0, (k - 1) * 0.9])
            q = (1 - t + 0.04) ** 2 * tip + 2 * (1 - t + 0.04) * (t - 0.04) * apex + (t - 0.04) ** 2 * T + np.array([0, 0, (k - 1) * 0.9])
            sc.add(sphere(0.3, 10, 6).scale(1.6, 0.8, 0.8).translate(*p), "navy_dk")
            sc.add(tube_between(q, p, 0.07, 6), "smoke")
        for (dx, dz, s) in ((0.0, 0.0, 1.2), (3.0, -3.5, 0.9), (-2.5, 3.0, 1.0)):
            c = T + np.array([dx, 0, dz])
            sc.add(lathe([(0.0, 0.0), (2.0 * s, 0.0), (2.2 * s, 0.25 * s), (1.6 * s, 0.05), (0.0, -0.4 * s)], 24).translate(*c), "dirt")
            for k in range(7):
                sc.add(sphere((0.9 - k * 0.08) * s, 10, 6).translate(c[0] + rng.normal(0, 0.3), (0.8 + k * 0.9) * s, c[2] + rng.normal(0, 0.3)),
                       "dirt" if k < 3 else "smoke_dk")
            sc.add(sphere(0.9 * s, 10, 6).translate(c[0], 0.6 * s, c[2]), "flash")
        add_all(sc, tank(T + np.array([1.2, 0.6, 1.6]), yaw=40, roll=150, mat="tank_dk"))
    return m


if __name__ == "__main__":
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for v in ("idle", "fire"):
        mdl = build(v)
        print(mdl.save(OUT_DIR / f"JapanYamatoBattery_{v}.glb", textures=True, bindings=DECK), mdl.stats())
