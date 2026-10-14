"""SingularityScorpion («Сингулярність») — concept: a radioactive mech-scorpion that shoots black holes.

A dark armoured scorpion with eight spider legs clamps onto a radioactive reactor deck (toxic
green pools, nuclear barrels, radiation signs, spider drones). Its pincers hold glowing
radioactive canisters; its tail ends in a stinger-cannon: a tiny black hole held in containment
rings, with little planets orbiting it. Firing opens a big black hole on the target with a
spiral-galaxy accretion disk that drags tanks in and stretches them.

Variants (concept renders, not part of the mod build):
    SingularityScorpion_idle.glb
    SingularityScorpion_fire.glb

Run:  python3 -I models/concepts/SingularityScorpion.py
"""
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from meshkit import (Model, box, cone, cylinder, disc, foundation_plate, lathe,  # noqa: E402
                     merge, rgb, sphere, torus, tube_between)

OUT_DIR = HERE.parent / "glb" / "concepts"
HALF = 4.5
PLATE_H = 0.4
STING = np.array([2.6, 7.4, 0.0])        # black hole in the stinger

DECK = {
    "plate": {"texture": "soviet/plate_steel", "tile": 9.0, "offset": 0.5},
    "haz_y": "soviet/hazard", "haz_k": {"texture": "soviet/hazard", "white": 0.15},
    "metal": "soviet/dark_metal",
}


def ell(rx, ry, rz, c, seg=14, rings=9):
    return sphere(1.0, seg, rings).scale(rx, ry, rz).translate(*c)


def trefoil(r, at, normal_up=True):
    """Radiation sign (yellow disc + three black blades + black centre), lying on the deck."""
    parts_y = [disc(r, 32)]
    parts_k = [disc(r * 0.16, 16).translate(0, 0.004, 0)]
    for k in range(3):
        a0 = k * 120 + 30
        parts_k.append(lathe([(r * 0.24, 0.0), (r * 0.82, 0.0)], 6, start_deg=a0, end_deg=a0 + 60).translate(0, 0.004, 0))
    y = merge(*parts_y).translate(*at)
    kk = merge(*parts_k).translate(*at)
    # lathe ring segments face +-Y; make them flat discs by squashing
    return y, kk


def spider(at, yaw=0.0, s=1.0):
    """Small spider drone: dark body, glowing green abdomen mark, 8 bent legs. {mat: [mesh]}"""
    out = {"carapace": [], "tox": [], "eye": []}
    c = np.array([0.0, 0.35, 0.0])
    out["carapace"].append(ell(0.28, 0.18, 0.22, c))
    out["carapace"].append(ell(0.42, 0.3, 0.36, (-0.52, 0.42, 0)))
    out["tox"].append(ell(0.18, 0.06, 0.1, (-0.55, 0.71, 0), 10, 6))
    out["eye"].append(sphere(0.05, 6, 4).translate(0.26, 0.42, 0.07))
    out["eye"].append(sphere(0.05, 6, 4).translate(0.26, 0.42, -0.07))
    for side in (-1, 1):
        for k in range(4):
            a = math.radians(-50 + k * 33)
            root = np.array([0.08 * math.cos(a), 0.36, side * 0.18])
            knee = np.array([0.55 * math.cos(a), 0.75, side * (0.45 + 0.1 * k)])
            foot = np.array([0.95 * math.cos(a), 0.0, side * (0.7 + 0.12 * k)])
            out["carapace"].append(tube_between(root, knee, 0.035, 5))
            out["carapace"].append(tube_between(knee, foot, 0.028, 5))
    for k in out:
        out[k] = [m.scale(s).rotate(0, yaw, 0).translate(*at) for m in out[k]]
    return out


def add_all(node, parts):
    for mat, meshes in parts.items():
        if meshes:
            node.add(merge(*meshes), mat)


def tank(at, yaw=0.0, stretch=1.0, roll=0.0, pitch=0.0):
    """Enemy tank; stretch > 1 elongates it along +X (spaghettification)."""
    p = {"tank_blue": [], "tank_dk": []}
    p["tank_blue"].append(box(3.0, 0.7, 1.9, bevel=0.3).translate(0, 0.75, 0))
    for z in (-0.95, 0.95):
        p["tank_dk"].append(box(3.1, 0.55, 0.42, bevel=0.12).translate(0, 0.35, z))
    p["tank_blue"].append(cylinder(0.72, 0.42, 16).translate(-0.2, 1.1, 0))
    p["tank_dk"].append(tube_between((-0.2, 1.32, 0), (1.9, 1.38, 0), 0.09, 8))
    for k in p:
        p[k] = [m.scale(stretch, 1.0 / math.sqrt(stretch), 1.0 / math.sqrt(stretch))
                .rotate(roll, yaw, pitch).translate(*at) for m in p[k]]
    return p


def build(variant):
    fire = variant == "fire"
    m = Model(f"SingularityScorpion_{variant}")
    m.material("plate", rgb("#4a4652"), metallic=0.2, roughness=0.8)
    m.material("metal", rgb("#2c2a33"), metallic=0.7, roughness=0.4)
    m.material("haz_y", rgb("#e8c21f"), metallic=0.1, roughness=0.6)
    m.material("haz_k", rgb("#1a1a18"), metallic=0.2, roughness=0.7)
    m.material("carapace", rgb("#1f1c2b"), metallic=0.8, roughness=0.28)
    m.material("carapace_hi", rgb("#463d66"), metallic=0.85, roughness=0.25)
    m.material("tox", rgb("#7dff4a"), roughness=0.2, emissive=rgb("#3dff10", 0.95))
    m.material("tox_pool", rgb("#5bff2a"), roughness=0.1, emissive=rgb("#2bd60c", 0.8), alpha=0.8)
    m.material("eye", rgb("#ff2a3a"), roughness=0.2, emissive=rgb("#ff1020", 1.2))
    m.material("purple", rgb("#9b3dff"), roughness=0.2, emissive=rgb("#8a2dff", 1.0))
    m.material("void", rgb("#000000"), metallic=0.0, roughness=1.0)
    m.material("accretion", rgb("#ffe0a0"), roughness=0.2, emissive=rgb("#ffb040", 1.3))
    m.material("halo", rgb("#7a2dff"), roughness=0.1, emissive=rgb("#6a1dff", 0.7), alpha=0.22)
    m.material("barrel", rgb("#e8c21f"), metallic=0.4, roughness=0.45)
    m.material("planet_ring", rgb("#e7c98f"), roughness=0.6)
    m.material("planet_a", rgb("#d9a86a"), roughness=0.7)
    m.material("planet_b", rgb("#c2452d"), roughness=0.7)
    m.material("planet_c", rgb("#2f8fd0"), roughness=0.5)
    m.material("tank_blue", rgb("#5a7699"), metallic=0.5, roughness=0.45)
    m.material("tank_dk", rgb("#262c35"), metallic=0.6, roughness=0.5)
    m.material("star_w", rgb("#ffffff"), emissive=rgb("#ffffff", 1.1))
    m.material("star_o", rgb("#ffb25c"), emissive=rgb("#ff8a20", 1.1))
    m.material("star_b", rgb("#8ab4ff"), emissive=rgb("#3a7cff", 1.1))
    m.material("star_p", rgb("#c27bff"), emissive=rgb("#9a3dff", 1.1))
    m.material("dust", rgb("#6d5f86"), roughness=1.0, alpha=0.45)

    base = m.node("Base")
    # --- reactor deck: 9 x 9 hazard-edged plate, radiation signs, toxic pools, barrels
    base.add(foundation_plate(size=2 * HALF - 0.04, top=PLATE_H, bevel=0.5), "plate")
    n = 16
    w = (2 * HALF - 1.1) / n
    for sgn in (-1, 1):
        for i in range(n):
            t = -HALF + 0.55 + i * w + w / 2
            mat = "haz_y" if i % 2 == 0 else "haz_k"
            base.add(box(w, 0.07, 0.34).translate(t, PLATE_H + 0.035, sgn * (HALF - 0.34)), mat)
            base.add(box(0.34, 0.07, w).translate(sgn * (HALF - 0.34), PLATE_H + 0.035, t), mat)
    for at in ((-2.9, PLATE_H + 0.02, -2.9), (2.9, PLATE_H + 0.02, 2.9)):
        y, kk = trefoil(0.8, at)
        base.add(y, "haz_y")
        base.add(kk, "haz_k")
    # reactor core under the scorpion: glowing green ring + vents
    base.add(cylinder(2.3, 0.35, 40).translate(0, PLATE_H, 0), "metal")
    base.add(torus(2.3, 0.1, 40, 6).translate(0, PLATE_H + 0.35, 0), "tox")
    for k in range(8):
        a = 2 * math.pi * k / 8
        base.add(box(0.5, 0.06, 0.18).rotate(0, -math.degrees(a), 0)
                 .translate(1.8 * math.cos(a), PLATE_H + 0.37, 1.8 * math.sin(a)), "tox")
    # toxic pools and goo drips
    for (x, z, r) in ((-3.2, 0.6, 0.7), (3.0, -2.6, 0.55), (-1.0, 3.3, 0.5), (3.4, 1.4, 0.4)):
        base.add(disc(r, 24).scale(1.0, 1, 0.8).translate(x, PLATE_H + 0.012, z), "tox_pool")
        base.add(sphere(0.12, 8, 5).translate(x + r * 0.4, PLATE_H + 0.06, z), "tox")
    # nuclear barrels (yellow, glowing green tops), one tipped over leaking
    for (x, z, tipped) in ((-3.5, -1.2, False), (-3.0, -1.8, False), (-3.7, -2.0, False), (2.2, 3.4, True)):
        bar = merge(cylinder(0.3, 0.8, 16), torus(0.31, 0.03, 16, 4).translate(0, 0.25, 0),
                    torus(0.31, 0.03, 16, 4).translate(0, 0.55, 0))
        lid = disc(0.26, 16).translate(0, 0.81, 0)
        if tipped:
            bar = bar.rotate(0, 0, 90).translate(0.4, 0.3, 0)
            lid = lid.rotate(0, 0, 90).translate(0.4, 0.3, 0)
            base.add(disc(0.6, 20).translate(x - 0.7, PLATE_H + 0.015, z), "tox_pool")
        base.add(bar.translate(x, PLATE_H, z), "barrel")
        base.add(lid.translate(x, PLATE_H, z), "tox")
    # spider drones guarding the deck
    for (x, z, yaw) in ((-2.6, 2.4, 40), (2.8, -0.6, -120), (-0.5, -3.4, 170), (1.4, 2.9, 80)):
        add_all(base, spider((x, PLATE_H, z), yaw, 0.85))

    # --- the scorpion (Turret, forward = +X)
    tur = base.node("Turret", (0.0, PLATE_H + 0.35, 0.0))
    y0 = PLATE_H + 0.35
    # thorax segments + head
    for i, (x, rx, ry, rz) in enumerate(((-1.2, 1.0, 0.75, 1.15), (0.0, 1.15, 0.85, 1.3), (1.15, 0.95, 0.72, 1.1))):
        tur.add(ell(rx, ry, rz, (x, y0 + 1.6, 0), 20, 12), "carapace")
        tur.add(torus(1.0, 0.06, 24, 6).scale(rz * 0.95, 1, rz * 0.95).rotate(0, 0, 90).translate(x + rx * 0.55, y0 + 1.6, 0), "tox")
        tur.add(ell(rx * 0.7, 0.15, rz * 0.6, (x, y0 + 1.6 + ry * 0.92, 0), 14, 6), "carapace_hi")
    tur.add(ell(0.75, 0.55, 0.85, (2.15, y0 + 1.45, 0), 18, 10), "carapace")
    # eight spider eyes
    for k, (dy, dz, r) in enumerate(((0.25, 0.22, 0.11), (0.25, -0.22, 0.11), (0.38, 0.45, 0.08), (0.38, -0.45, 0.08),
                                     (0.05, 0.38, 0.08), (0.05, -0.38, 0.08), (0.45, 0.12, 0.06), (0.45, -0.12, 0.06))):
        tur.add(sphere(r, 10, 6).translate(2.72, y0 + 1.45 + dy, dz), "eye")
    # mandibles
    for z in (-0.3, 0.3):
        tur.add(cone(0.12, 0.6, 8).rotate(0, 0, -100).translate(2.8, y0 + 1.1, z), "carapace_hi")
    # eight spider legs, clamped to the deck
    for side in (-1, 1):
        for k in range(4):
            x = -1.5 + k * 0.95
            root = np.array([x, y0 + 1.5, side * 1.05])
            knee = np.array([x * 1.25, y0 + 3.4 - abs(k - 1.5) * 0.25, side * (2.4 + 0.1 * k)])
            foot = np.array([x * 1.55 + (0.3 if k > 1 else -0.3), PLATE_H, side * (3.8 - 0.15 * abs(k - 1.5))])
            tur.add(tube_between(root, knee, 0.2, 10), "carapace")
            tur.add(tube_between(knee, foot, 0.13, 10), "carapace_hi")
            tur.add(sphere(0.26, 10, 6).translate(*knee), "tox")
            tur.add(cone(0.16, 0.35, 8).rotate(180, 0, 0).translate(foot[0], foot[1] + 0.35, foot[2]), "metal")
    # two pincers holding radioactive canisters
    for side in (-1, 1):
        sh = np.array([1.3, y0 + 1.4, side * 1.0])
        el = np.array([2.6, y0 + 1.9, side * 1.9])
        wr = np.array([3.6, y0 + 1.4, side * 1.6])
        tur.add(tube_between(sh, el, 0.22, 10), "carapace")
        tur.add(tube_between(el, wr, 0.2, 10), "carapace_hi")
        tur.add(ell(0.55, 0.38, 0.42, wr, 14, 8), "carapace")
        for dz in (-0.22, 0.22):
            tur.add(cone(0.18, 0.95, 10).rotate(0, 0, -90).rotate(dz * 60, 0, 0).translate(wr[0] + 0.3, wr[1], wr[2] + dz), "carapace_hi")
        can = np.array([wr[0] + 0.85, wr[1], wr[2]])
        tur.add(cylinder(0.22, 0.7, 14).translate(can[0], can[1] - 0.35, can[2]), "tox")
        tur.add(torus(0.23, 0.04, 14, 4).translate(can[0], can[1] + 0.2, can[2]), "metal")
        tur.add(torus(0.23, 0.04, 14, 4).translate(can[0], can[1] - 0.2, can[2]), "metal")
    # tail: segments arcing up and over towards the front
    path = [np.array(p) for p in ((-2.0, y0 + 2.0, 0), (-2.9, y0 + 3.2, 0), (-3.1, y0 + 4.6, 0), (-2.6, y0 + 5.9, 0),
                                   (-1.4, y0 + 6.8, 0), (0.1, y0 + 7.2, 0), (1.5, y0 + 7.0, 0))]
    for i, p in enumerate(path):
        r = 0.78 - i * 0.07
        tur.add(sphere(r, 18, 12).translate(*p), "carapace" if i % 2 == 0 else "carapace_hi")
        if i + 1 < len(path):
            mid = (p + path[i + 1]) / 2
            tur.add(torus(r * 0.62, r * 0.12, 16, 6).rotate(0, 0, 90 + math.degrees(math.atan2((path[i + 1] - p)[1], (path[i + 1] - p)[0]))).translate(*mid), "tox")
            tur.add(sphere(r * 0.62, 12, 8).translate(*mid), "carapace_hi")
    # stinger-cannon: containment rings around a tiny black hole, little planets in orbit
    S = STING
    tur.add(tube_between(path[-1], S - np.array([0.55, 0.0, 0.0]), 0.3, 12), "carapace_hi")
    tur.add(cone(0.35, 0.9, 12).rotate(0, 0, -150).translate(S[0] - 0.2, S[1] - 0.4, 0), "carapace_hi")
    hole = tur.node("Emitter", tuple(S))
    hole.add(torus(0.95, 0.07, 32, 6).rotate(0, 0, 90).translate(*S), "purple")
    hole.add(torus(0.95, 0.06, 32, 6).rotate(60, 0, 90).translate(*S), "tox")
    hole.add(torus(0.95, 0.06, 32, 6).rotate(-60, 0, 90).translate(*S), "purple")
    hole.add(sphere(0.38, 20, 14).translate(*S), "void")
    hole.add(torus(0.6, 0.06, 32, 6).scale(1, 0.25, 1).rotate(20, 0, 0).translate(*S), "accretion")
    hole.add(sphere(0.7, 20, 12).translate(*S), "halo")
    hole.add(torus(1.75, 0.012, 48, 4).rotate(15, 0, 10).translate(*S), "halo")
    planets = (
        (np.array([0.0, 1.3, 1.1]), 0.28, "planet_a", True),
        (np.array([0.0, -0.9, -1.5]), 0.2, "planet_b", False),
        (np.array([0.3, 0.6, -1.6]), 0.24, "planet_c", False),
    )
    for off, r, mat, ringed in planets:
        c = S + off
        hole.add(sphere(r, 16, 10).translate(*c), mat)
        if ringed:
            hole.add(torus(r * 1.8, 0.03, 24, 4).scale(1, 0.3, 1).rotate(25, 0, 0).translate(*c), "planet_ring")
    fx = hole.node("FX_Muzzle01", tuple(S))
    fx.add(box(0.05, 0.05, 0.05).translate(*S), "purple")

    if fire:
        sc = base.node("FX_Scene", (0, 0, 0))
        BH = np.array([19.0, 3.0, 0.0])
        # beam: core + two helix strands (toxic green and violet)
        sc.add(tube_between(S, BH, 0.14, 12), "accretion")
        sc.add(tube_between(S, BH, 0.4, 12), "halo")
        d = BH - S
        L = np.linalg.norm(d)
        u = d / L
        v = np.cross(u, [0, 0, 1.0])
        v = v / np.linalg.norm(v)
        wv = np.cross(u, v)
        for strand, mat in ((0.0, "tox"), (math.pi, "purple")):
            pts = []
            for i in range(80):
                t = i / 79
                a = t * 14 * math.pi + strand
                pts.append(S + d * t + (v * math.cos(a) + wv * math.sin(a)) * 0.45)
            for i in range(len(pts) - 1):
                sc.add(tube_between(pts[i], pts[i + 1], 0.05, 5), mat)
        # the black hole on the target + a spiral-galaxy accretion disk around it
        sc.add(sphere(1.15, 28, 18).translate(*BH), "void")
        sc.add(sphere(1.7, 24, 14).translate(*BH), "halo")
        tilt = (18.0, 0.0, 8.0)
        sc.add(torus(1.55, 0.12, 48, 8).scale(1, 0.2, 1).rotate(*tilt).translate(*BH), "accretion")
        sc.add(torus(2.2, 0.08, 48, 6).scale(1, 0.2, 1).rotate(*tilt).translate(*BH), "star_o")
        sc.add(disc(10.0, 64).rotate(*tilt).translate(*BH), "halo")
        rng = np.random.RandomState(3)
        groups = {"star_w": [], "star_o": [], "star_b": [], "star_p": []}
        for arm in range(3):
            for i in range(320):
                t = i / 320
                r = 1.6 + t * 8.5
                a = arm * 2 * math.pi / 3 + math.log(r) * 2.4 + rng.normal(0, 0.12)
                h = rng.normal(0, 0.08) * (1 + t)
                p = np.array([r * math.cos(a), h, r * math.sin(a)]) + rng.normal(0, 0.15 * (1 + 2 * t), 3) * [1, 0.2, 1]
                mat = "star_w" if t < 0.2 else "star_o" if t < 0.45 else "star_p" if rng.rand() < 0.5 else "star_b"
                s = rng.uniform(0.07, 0.18) * (1.4 - t * 0.6)
                groups[mat].append(box(s, s, s).translate(*p))
        for mat, meshes in groups.items():
            sc.add(merge(*meshes).rotate(*tilt).translate(*BH), mat)
        # tanks being dragged in: one spaghettified on the disk, one tumbling, one sliding
        add_all(sc, tank(BH + np.array([-2.8, 0.2, 2.4]), yaw=-40, stretch=3.2, roll=25, pitch=-15))
        add_all(sc, tank(BH + np.array([-4.2, -0.3, -3.0]), yaw=35, stretch=1.6, roll=-60, pitch=30))
        add_all(sc, tank(np.array([BH[0] - 6.5, 0.0, 4.5]), yaw=-35, stretch=1.0))
        for k in range(6):
            sc.add(sphere(0.5 + 0.15 * k, 10, 6).translate(BH[0] - 7.6 - 0.5 * k, 0.4, 4.9 + 0.3 * k), "dust")
        # toxic shockwave ring on the ground under the hole
        sc.add(torus(5.5, 0.12, 64, 6).translate(BH[0], 0.05, 0), "tox")
        sc.add(disc(5.4, 48).translate(BH[0], 0.02, 0), "halo")
        # spider drones charging the target
        for (x, z, yaw) in ((8.0, -2.0, -10), (10.5, 1.8, 15), (12.5, -0.8, 0)):
            add_all(sc, spider((x, 0.0, z), yaw, 1.0))
    return m


if __name__ == "__main__":
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for v in ("idle", "fire"):
        mdl = build(v)
        print(mdl.save(OUT_DIR / f"SingularityScorpion_{v}.glb", textures=True, bindings=DECK), mdl.stats())
