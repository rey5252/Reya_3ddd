"""Shared pieces for the Empire super-cannon concepts: materials, the 8 x 8 Empire deck,
target tanks, curve sampling and an "orient along a direction" helper."""
import math

import numpy as np

from meshkit import (box, cylinder, foundation_plate, lathe, merge, rgb, sphere,  # noqa: F401
                     torus, tube_between)

HALF = 4.0
PLATE_H = 0.4

DECK = {
    "plate": {"texture": "empire/plate_steel", "tile": 8.0, "offset": 0.5},
    "plate_in": {"texture": "empire/armor_white", "tile": 4.0},
    "armor": "empire/armor_white",
    "crimson": "empire/lacquer_crimson",
    "black": "empire/trim_black",
    "stone": "empire/stone",
    "navy_dk": {"texture": "empire/trim_black", "white": 0.5},
    "concrete": "soviet/concrete",
}


def materials(m):
    m.material("plate", rgb("#3a3b44"), metallic=0.4, roughness=0.5)
    m.material("plate_in", rgb("#e9e6e0"), metallic=0.1, roughness=0.4)
    m.material("armor", rgb("#f4f1ec"), metallic=0.15, roughness=0.3)
    m.material("crimson", rgb("#c8102e"), metallic=0.2, roughness=0.3)
    m.material("black", rgb("#1d1d22"), metallic=0.5, roughness=0.4)
    m.material("stone", rgb("#cfc7b8"), metallic=0.0, roughness=0.85)
    m.material("paper", rgb("#f6f2ea"), metallic=0.0, roughness=0.5)
    m.material("gold", rgb("#e3b23c"), metallic=0.95, roughness=0.22)
    m.material("silver", rgb("#dfe6ee"), metallic=1.0, roughness=0.12)
    m.material("teal", rgb("#3fe0c5"), roughness=0.2, emissive=rgb("#1fd6b8", 1.1))
    m.material("teal_halo", rgb("#3fe0c5"), roughness=0.1, emissive=rgb("#1fd6b8", 0.7), alpha=0.28)
    m.material("white_hot", rgb("#f2fffb"), roughness=0.1, emissive=rgb("#d9fff6", 1.4))
    m.material("tank_blue", rgb("#5a7699"), metallic=0.5, roughness=0.45)
    m.material("tank_dk", rgb("#262c35"), metallic=0.6, roughness=0.5)
    m.material("molten", rgb("#ff7a1a"), roughness=0.3, emissive=rgb("#ff5a0a", 1.3))
    m.material("smoke", rgb("#c9c4bd"), roughness=1.0, alpha=0.45)
    m.material("smoke_dk", rgb("#55524d"), roughness=1.0, alpha=0.55)
    m.material("navy", rgb("#8d949c"), metallic=0.35, roughness=0.45)
    m.material("navy_dk", rgb("#5d636b"), metallic=0.4, roughness=0.45)
    m.material("concrete", rgb("#a7a297"), metallic=0.0, roughness=0.9)
    m.material("flash", rgb("#ffd27a"), roughness=0.2, emissive=rgb("#ffb43a", 1.4))
    m.material("dirt", rgb("#6b5a43"), roughness=1.0)
    m.material("copper", rgb("#c27a3a"), metallic=0.9, roughness=0.3)


def ell(rx, ry, rz, c, seg=16, rings=10):
    return sphere(1.0, seg, rings).scale(rx, ry, rz).translate(*c)


def orient(mesh, d, at=(0, 0, 0)):
    """Rotate a mesh built along +Y so that +Y points along d, then move its origin to `at`."""
    d = np.asarray(d, np.float32)
    d = d / (np.linalg.norm(d) or 1)
    y = np.array([0, 1, 0], np.float32)
    axis = np.cross(y, d)
    s = float(np.linalg.norm(axis))
    c = float(np.dot(y, d))
    if s > 1e-6:
        k = axis / s
        K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]], np.float32)
        R = np.eye(3, dtype=np.float32) + s * K + (1 - c) * (K @ K)
    else:
        R = np.eye(3, dtype=np.float32) if c > 0 else np.diag([1, -1, -1]).astype(np.float32)
    mesh.pos = mesh.pos @ R.T
    mesh.nrm = mesh.nrm @ R.T
    return mesh.translate(*at)


def catmull(points, n):
    """Sample a Catmull-Rom curve through `points` at n positions."""
    P = [np.asarray(p, float) for p in points]
    P = [P[0]] + P + [P[-1]]
    out = []
    segs = len(P) - 3
    for i in range(n):
        t = i / (n - 1) * segs
        k = min(int(t), segs - 1)
        u = t - k
        p0, p1, p2, p3 = P[k], P[k + 1], P[k + 2], P[k + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u
                          + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3))
    return out


def deck(base, half=HALF):
    """8 x 8 Empire deck: dark tech plate, white inset, crimson edge bands, black corner posts with teal lights."""
    base.add(foundation_plate(size=2 * half - 0.04, top=PLATE_H, bevel=0.5), "plate")
    base.add(box(2 * half - 1.4, 0.06, 2 * half - 1.4, bevel=0.5).translate(0, PLATE_H + 0.03, 0), "plate_in")
    for s in (-1, 1):
        base.add(box(2 * half - 1.0, 0.08, 0.16).translate(0, PLATE_H + 0.04, s * (half - 0.45)), "crimson")
        base.add(box(0.16, 0.08, 2 * half - 1.0).translate(s * (half - 0.45), PLATE_H + 0.04, 0), "crimson")
    for sx in (-1, 1):
        for sz in (-1, 1):
            base.add(box(0.6, 0.5, 0.6, bevel=0.12).translate(sx * (half - 0.5), PLATE_H + 0.25, sz * (half - 0.5)), "black")
            base.add(sphere(0.16, 10, 6).translate(sx * (half - 0.5), PLATE_H + 0.62, sz * (half - 0.5)), "teal")


def tank(at, yaw=0.0, roll=0.0, pitch=0.0, sx=1.0, sy=1.0, mat="tank_blue", half=None):
    """Enemy tank ~3 m long as {material: [mesh]}. half='front'/'back' keeps only that half (cut in two)."""
    p = {mat: [], "tank_dk": []}
    p[mat].append(box(3.0, 0.7, 1.9, bevel=0.3).translate(0, 0.75, 0))
    for z in (-0.95, 0.95):
        p["tank_dk"].append(box(3.1, 0.55, 0.42, bevel=0.12).translate(0, 0.35, z))
    p[mat].append(cylinder(0.72, 0.42, 16).translate(-0.2, 1.1, 0))
    p["tank_dk"].append(tube_between((-0.2, 1.32, 0), (1.9, 1.38, 0), 0.09, 8))
    if half:   # cut in two: keep one half of every part, squashed to half length
        off = 0.78 if half == "front" else -0.78
        for k in p:
            p[k] = [mm.scale(0.5, 1.0, 1.0).translate(off, 0, 0) for mm in p[k]]
    for k in p:
        p[k] = [mm.scale(sx, sy, 1.0).rotate(roll, yaw, pitch).translate(*at) for mm in p[k]]
    return p


def add_all(node, parts):
    for mat, meshes in parts.items():
        if meshes:
            node.add(merge(*meshes), mat)


def rising_sun(r, rays=16):
    """Rising-sun disc in the XY plane facing +Z: (white back, crimson disc+rays) meshes."""
    from meshkit import disc
    back = disc(r, 48).rotate(90, 0, 0)
    red = [disc(r * 0.42, 40).rotate(90, 0, 0).translate(0, 0, 0.02)]
    for k in range(rays):
        a0 = k * 360 / rays
        red.append(lathe([(r * 0.42, 0.0), (r * 0.98, 0.0)], 2, start_deg=a0, end_deg=a0 + 360 / rays / 2)
                   .rotate(90, 0, 0).translate(0, 0, 0.015))
    return back, merge(*red)
