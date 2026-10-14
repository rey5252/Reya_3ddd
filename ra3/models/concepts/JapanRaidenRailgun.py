"""JapanRaidenRailgun («Райден») — concept: an electromagnetic railgun emplacement.

Realistic layout: a capacitor hall (rows of white capacitor drums with crimson bands) and cooling
radiators on the deck, heavy copper bus bars and cables to an armoured mount on a yaw ring.
The 12 m barrel is a box-section: dark conductive rails top and bottom, white composite side
plates, clamped every metre by black yokes; cooling lines run along it.

Firing: a white-blue plasma flash and a ring of vapour at the muzzle, a straight glowing tracer,
and a tank punched through at the target with an explosion and a gouge in the ground.

Nodes: Base > Turret > Barrel > FX_Muzzle01.  Forward = +X.
Run:  python3 -I models/concepts/JapanRaidenRailgun.py
"""
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))

from meshkit import Model, box, cylinder, disc, merge, sphere, torus, tube_between  # noqa: E402
from _empire_common import DECK, PLATE_H, add_all, deck, ell, materials, tank  # noqa: E402

OUT_DIR = HERE.parent / "glb" / "concepts"
RING_TOP = PLATE_H + 0.9
TRUNNION = (0.6, RING_TOP + 1.75, 0.0)
ELEV = 4.0
BARREL_LEN = 12.0


def aim(mesh):
    return mesh.rotate(0, 0, ELEV).translate(*TRUNNION)


def build(variant):
    fire = variant == "fire"
    m = Model(f"JapanRaidenRailgun_{variant}")
    materials(m)
    base = m.node("Base")
    deck(base)
    # --- capacitor hall (rear), radiators (sides), bus bars to the mount
    base.add(box(2.2, 0.25, 6.4).translate(-2.7, PLATE_H + 0.12, 0), "black")
    for row in range(2):
        for k in range(6):
            x, z = -3.2 + row * 1.0, -2.6 + k * 1.04
            base.add(cylinder(0.4, 1.5, 18).translate(x, PLATE_H + 0.25, z), "armor")
            base.add(cylinder(0.41, 0.14, 18).translate(x, PLATE_H + 1.25, z), "crimson")
            base.add(cylinder(0.18, 0.12, 12).translate(x, PLATE_H + 1.75, z), "copper")
    base.add(box(0.25, 0.12, 6.0).translate(-2.7, PLATE_H + 1.92, 0), "copper")
    for z in (-3.2, 3.2):
        base.add(box(3.2, 1.1, 0.5).translate(0.6, PLATE_H + 0.55, z), "black")
        for k in range(14):
            base.add(box(0.06, 0.95, 0.56).translate(-0.85 + k * 0.22, PLATE_H + 0.55, z), "silver")
    for z in (-0.5, 0.5):     # power cables from the hall to the mount
        base.add(tube_between((-2.7, PLATE_H + 1.95, z * 2), (-1.2, RING_TOP + 0.5, z), 0.12, 10), "black")
    base.add(cylinder(2.0, 0.5, 40).translate(0, PLATE_H + 0.4, 0), "black")
    base.add(torus(2.0, 0.06, 40, 6).translate(0, RING_TOP, 0), "silver")

    # --- armoured mount (Turret)
    tur = base.node("Turret", (0.0, RING_TOP, 0.0))
    tur.add(cylinder(1.8, 0.45, 40).translate(0, RING_TOP, 0), "armor")
    for z in (-1, 1):   # two cheek walls holding the trunnions
        tur.add(box(2.6, 1.9, 0.45, bevel=0.15).translate(0.2, RING_TOP + 1.3, z * 1.05), "armor")
        tur.add(box(2.62, 0.16, 0.47).translate(0.2, RING_TOP + 1.9, z * 1.05), "crimson")
        tur.add(cylinder(0.42, 0.2, 18).rotate(90, 0, 0).translate(TRUNNION[0], TRUNNION[1], z * 1.38 + (0.2 if z < 0 else 0)), "black")
    tur.add(box(1.6, 0.8, 1.6, bevel=0.15).translate(-1.4, RING_TOP + 0.85, 0), "black")      # breech power block
    for z in (-0.5, 0.5):
        tur.add(box(1.0, 0.12, 0.2).translate(-1.4, RING_TOP + 1.3, z), "copper")
    # sensor head (radar + optics) on the right cheek
    tur.add(box(0.5, 0.45, 0.4, bevel=0.06).translate(0.6, RING_TOP + 2.5, 1.05), "black")
    tur.add(disc(0.14, 12).rotate(0, 0, -90).translate(0.86, RING_TOP + 2.5, 1.05), "teal")

    # --- the barrel: box-section rails, composite side plates, yokes every metre
    bar = tur.node("Barrel", TRUNNION)
    x0 = -1.2
    bar.add(aim(box(BARREL_LEN, 0.22, 0.5).translate(x0 + BARREL_LEN / 2, 0.36, 0)), "black")    # top rail
    bar.add(aim(box(BARREL_LEN, 0.22, 0.5).translate(x0 + BARREL_LEN / 2, -0.36, 0)), "black")   # bottom rail
    for z in (-1, 1):
        bar.add(aim(box(BARREL_LEN - 0.3, 0.62, 0.12).translate(x0 + BARREL_LEN / 2 - 0.15, 0, z * 0.3)), "armor")
    bar.add(aim(box(BARREL_LEN, 0.03, 0.03).translate(x0 + BARREL_LEN / 2, 0.0, 0.37)), "copper")
    for k in range(int(BARREL_LEN) + 1):
        x = x0 + 0.3 + k * (BARREL_LEN - 0.6) / BARREL_LEN
        bar.add(aim(box(0.22, 1.12, 0.92, bevel=0.06).translate(x, 0, 0)), "navy_dk")
        if k % 3 == 1:
            bar.add(aim(box(0.24, 0.08, 0.94).translate(x, 0.52, 0)), "crimson")
    for z in (-0.55, 0.55):    # cooling lines along the barrel
        bar.add(aim(tube_between((x0 + 0.2, -0.42, z), (x0 + BARREL_LEN - 0.4, -0.42, z), 0.06, 8)), "silver")
    bar.add(aim(box(0.4, 1.3, 1.1, bevel=0.1).translate(x0 + BARREL_LEN - 0.2, 0, 0)), "black")    # muzzle brace
    bore = aim(box(0.05, 0.4, 0.4).translate(x0 + BARREL_LEN, 0, 0))
    bar.add(bore, "white_hot" if fire else "black")
    a = math.radians(ELEV)
    tip = (TRUNNION[0] + (x0 + BARREL_LEN) * math.cos(a), TRUNNION[1] + (x0 + BARREL_LEN) * math.sin(a), 0.0)
    fx = bar.node("FX_Muzzle01", tip)
    fx.add(box(0.05, 0.05, 0.05).translate(*tip), "teal")

    if fire:
        sc = base.node("FX_Scene", (0, 0, 0))
        tip = np.array(tip)
        d = np.array([math.cos(a), math.sin(a), 0.0])
        # plasma flash, vapour ring and a little arc lightning at the muzzle
        sc.add(ell(1.0, 0.7, 0.7, tip + d * 0.8, 16, 10), "white_hot")
        sc.add(ell(1.8, 1.2, 1.2, tip + d * 1.2, 16, 10), "teal_halo")
        sc.add(torus(1.4, 0.18, 32, 8).rotate(0, 0, 90 + ELEV).translate(*(tip + d * 0.4)), "smoke")
        sc.add(torus(2.2, 0.12, 32, 8).rotate(0, 0, 90 + ELEV).translate(*(tip + d * 1.6)), "smoke")
        # straight, flat trajectory: thin glowing tracer to the target
        T = np.array([18.0, 1.1, 0.0])
        sc.add(tube_between(tip + d * 1.5, T, 0.05, 8), "white_hot")
        sc.add(tube_between(tip + d * 1.5, T, 0.16, 8), "teal_halo")
        # target: tank punched through (hole glow on both sides), explosion, gouge and debris behind
        add_all(sc, tank(np.array([18.0, 0.0, 0.0]), yaw=80, roll=6))
        for x in (17.0, 19.0):
            sc.add(sphere(0.25, 10, 6).translate(x, 1.0, 0.0), "molten")
        sc.add(sphere(1.3, 16, 10).translate(19.8, 1.2, 0.0), "flash")
        rng = np.random.RandomState(9)
        for k in range(10):
            sc.add(sphere(rng.uniform(0.6, 1.3), 10, 6).translate(20.5 + rng.uniform(0, 3), 1.0 + rng.uniform(0, 2.2), rng.normal(0, 0.8)), "smoke_dk")
        sc.add(box(6.0, 0.05, 0.9).translate(23.5, 0.02, 0), "dirt")
        for k in range(12):
            sc.add(box(0.2, 0.12, 0.15).rotate(*rng.uniform(0, 90, 3)).translate(21 + rng.uniform(0, 5), 0.1 + rng.uniform(0, 1.5), rng.normal(0, 1)), "tank_dk")
    return m


if __name__ == "__main__":
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for v in ("idle", "fire"):
        mdl = build(v)
        print(mdl.save(OUT_DIR / f"JapanRaidenRailgun_{v}.glb", textures=True, bindings=DECK), mdl.stats())
