"""JapanKitsuneShrine (Святилище Кіцуне) - Empire of the Rising Sun fox-fire shrine.

A two-step stone platform carrying a crimson torii gate (curved black kasagi, crimson
shimaki and nuki, white plaque with the rising-sun emblem, shimenawa rope with paper
shide). A glowing teal fox-fire orb floats between the pillars inside two tilted rings,
with four small fox-fire flames orbiting it (node "Emitter"). Two stone toro lanterns
flank the gate; faint teal ripple rings glow on the platform.

The gate faces the RA3 camera diagonal (+X+Z), so it is built in a local "gate frame"
(beams along X, front towards +Z) and rotated 45 deg around Y.

Also writes the nanocore transport JapanKitsuneShrineNanocore.glb.

Run:  python3 -I models/towers/JapanKitsuneShrine.py
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import numpy as np  # noqa: E402
from meshkit import (Model, Mesh, box, cylinder, disc, sphere, torus, lathe, extrude,  # noqa: E402
                     tube_between, radial, merge, foundation_plate, rgb, PLATE_TOP)

OUT = HERE.parent / "glb"

GATE_YAW = 45.0          # local +X (beam axis) -> world (1,0,-1)/sqrt2, front +Z -> camera diagonal
STEP1_TOP = PLATE_TOP + 0.28
STEP2_TOP = STEP1_TOP + 0.28
ORB_Y = 2.6


def G(m: Mesh) -> Mesh:
    """Gate-frame mesh -> world."""
    return m.rotate(0, GATE_YAW, 0)


def seg_box(p0, p1, thick, depth, overlap=0.05, z=0.0):
    """Box spanning p0->p1 in the XY plane (gate frame), centred on z."""
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    a = math.degrees(math.atan2(y1 - y0, x1 - x0))
    return box(L + overlap, thick, depth).rotate(0, 0, a).translate((x0 + x1) / 2, (y0 + y1) / 2, z)


def curve_beam(xmax, ytop_fn, thick, depth, n, z=0.0):
    xs = np.linspace(-xmax, xmax, n + 1)
    parts = [seg_box((xs[i], ytop_fn(xs[i]) - thick / 2), (xs[i + 1], ytop_fn(xs[i + 1]) - thick / 2),
                     thick, depth, z=z) for i in range(n)]
    return merge(*parts)


def teardrop(r=0.12, h=0.42, segments=10):
    """Small flame: round bottom, pointed top, origin at the bulb centre."""
    prof = [(0.0, -r), (r * 0.7, -r * 0.72), (r, -r * 0.1), (r * 0.92, r * 0.45),
            (r * 0.62, h * 0.45), (r * 0.3, h * 0.72), (0.0, h)]
    return lathe(prof, segments)


def sun_emblem(r=0.12, ray_len=None, ray_w=None, rays=12, thick=0.012, segments=16):
    """Rising-sun emblem lying flat (facing +Y), centred on the origin."""
    ray_len = ray_len or r * 1.25
    ray_w = ray_w or r * 0.32
    core = cylinder(r, thick, segments)
    ray = extrude([(r * 0.6, -ray_w / 2), (r * 0.6 + ray_len, -ray_w * 0.18),
                   (r * 0.6 + ray_len, ray_w * 0.18), (r * 0.6, ray_w / 2)], thick * 0.9)
    return merge(core, radial(ray, rays, 360 / rays / 2))


def arc_ring(R, r, a0, a1, segments=6, tube=5):
    prof = [(R + r * math.cos(2 * math.pi * i / tube), r * math.sin(2 * math.pi * i / tube)) for i in range(tube + 1)]
    return lathe(prof, segments, start_deg=a0, end_deg=a1)


def materials(m: Model):
    m.material("stone", rgb("#c2bcb2"), roughness=0.85)
    m.material("stone_dark", rgb("#8b867f"), roughness=0.9)
    m.material("white", rgb("#f4f1ec"), roughness=0.45)
    m.material("crimson", rgb("#c8102e"), metallic=0.1, roughness=0.42)
    m.material("crimson_hi", rgb("#e8364f"), metallic=0.1, roughness=0.4)
    m.material("black", rgb("#1d1d22"), metallic=0.3, roughness=0.45)
    m.material("graphite", rgb("#4a4a54"), metallic=0.35, roughness=0.4)
    m.material("rope", rgb("#d9c79a"), roughness=0.95)
    m.material("rope_dark", rgb("#a8935f"), roughness=0.95)
    m.material("paper", rgb("#ffffff"), roughness=0.7, emissive=rgb("#ffffff", 0.15))
    m.material("teal", rgb("#3fe0c5"), roughness=0.25, emissive=rgb("#3fe0c5", 1.25))
    m.material("teal_core", rgb("#e8fffb"), roughness=0.2, emissive=rgb("#b8fff2", 2.5))
    m.material("teal_floor", rgb("#7fe9d6"), roughness=0.5, emissive=rgb("#3fe0c5", 0.55))
    m.material("teal_orb", rgb("#5ff0d6"), roughness=0.15, emissive=rgb("#3fe0c5", 1.6))
    m.material("teal_veil", rgb("#3fe0c5"), roughness=0.2, emissive=rgb("#3fe0c5", 0.9), alpha=0.32)
    m.material("teal_ripple", rgb("#3fe0c5"), roughness=0.3, emissive=rgb("#3fe0c5", 1.1), alpha=0.8)


# =========================================================================== tower

def build_tower():
    m = Model("JapanKitsuneShrine")
    materials(m)
    base = m.node("Base")

    # ---------------------------------------------------------------- plate & steps
    base.add(foundation_plate(), "stone")
    base.add(box(6.02, 0.06, 6.02, bevel=0.36).translate(0, 0.22, 0), "crimson")
    base.add(box(6.02, 0.035, 6.02, bevel=0.36).translate(0, 0.07, 0), "black")
    # engraved border lines on the plate top
    for s in (2.62, 2.42):
        L = 2 * s
        for sx in (-1, 1):
            base.add(box(0.035, 0.012, L).translate(sx * s, PLATE_TOP + 0.006, 0), "stone_dark")
            base.add(box(L, 0.012, 0.035).translate(0, PLATE_TOP + 0.006, sx * s), "stone_dark")
    # crimson corner arrows + teal studs
    for ang in (45, 135, 225, 315):
        tri = extrude([(2.48, -0.2), (2.82, 0.0), (2.48, 0.2)], 0.02)
        tri.translate(0, PLATE_TOP, 0).rotate(0, ang, 0)
        base.add(tri, "crimson")
        stud = cylinder(0.07, 0.04, 10).translate(2.25, PLATE_TOP, 0).rotate(0, ang, 0)
        base.add(stud, "teal")
    # mid-edge teal studs
    for ang in (0, 90, 180, 270):
        base.add(cylinder(0.05, 0.03, 8).translate(2.75, PLATE_TOP, 0).rotate(0, ang, 0), "teal")

    # step 1
    base.add(box(3.8, 0.28, 3.8, bevel=0.12).translate(0, PLATE_TOP + 0.14, 0), "white")
    base.add(box(3.83, 0.05, 3.83, bevel=0.13).translate(0, PLATE_TOP + 0.09, 0), "crimson")
    base.add(box(3.83, 0.025, 3.83, bevel=0.13).translate(0, PLATE_TOP + 0.015, 0), "black")
    for s in (1.72,):
        for sx in (-1, 1):
            base.add(box(0.03, 0.01, 2 * s).translate(sx * s, STEP1_TOP + 0.005, 0), "stone_dark")
            base.add(box(2 * s, 0.01, 0.03).translate(0, STEP1_TOP + 0.005, sx * s), "stone_dark")
    # step 2
    base.add(box(3.0, 0.28, 3.0, bevel=0.1).translate(0, STEP1_TOP + 0.14, 0), "stone")
    base.add(box(3.02, 0.04, 3.02, bevel=0.1).translate(0, STEP1_TOP + 0.02, 0), "black")
    base.add(box(2.6, 0.02, 2.6, bevel=0.06).translate(0, STEP2_TOP + 0.01, 0), "white")
    base.add(box(2.2, 0.03, 2.2, bevel=0.05).translate(0, STEP2_TOP + 0.015, 0), "teal_floor")
    base.add(sun_emblem(0.26, segments=20).translate(0, STEP2_TOP + 0.03, 0), "crimson")

    # ripple rings (flat, faint)
    base.add(torus(1.0, 0.032, 48, 5).translate(0, STEP2_TOP + 0.05, 0), "teal_ripple")
    base.add(torus(1.85, 0.032, 64, 5).translate(0, STEP1_TOP + 0.03, 0), "teal_ripple")
    base.add(torus(2.72, 0.034, 72, 5).translate(0, PLATE_TOP + 0.03, 0), "teal_ripple")
    dashes = 28
    for i in range(dashes):
        a0 = 360 * i / dashes
        base.add(arc_ring(2.9, 0.03, a0, a0 + 360 / dashes * 0.55, 3, 4).translate(0, PLATE_TOP + 0.025, 0),
                 "teal_ripple")

    # ---------------------------------------------------------------- torii (gate frame)
    PX = 1.5            # pillar centre x
    KX = 3.6            # kasagi half length

    def kasagi_top(x):
        return 5.62 + 0.42 * (abs(x) / KX) ** 2.6

    # kasagi: black main beam + graphite top cap
    base.add(G(curve_beam(KX, kasagi_top, 0.3, 0.44, 12)), "black")
    base.add(G(curve_beam(KX + 0.04, lambda x: kasagi_top(x) + 0.06, 0.07, 0.52, 12)), "graphite")
    # shimaki (crimson, directly under the kasagi)
    SX = 2.85
    base.add(G(curve_beam(SX, lambda x: kasagi_top(x) - 0.3, 0.24, 0.34, 8)), "crimson")
    # glowing kasagi tips
    for sx in (-1, 1):
        base.add(G(sphere(0.075, 10, 6).translate(sx * (KX + 0.02), kasagi_top(KX) + 0.03, 0)), "teal")

    # nuki (straight tie beam) with black end caps and highlight top
    NX, NY0, NY1 = 2.5, 4.22, 4.58
    base.add(G(box(2 * NX, NY1 - NY0, 0.3).translate(0, (NY0 + NY1) / 2, 0)), "crimson")
    base.add(G(box(2 * NX - 0.02, 0.035, 0.32).translate(0, NY1 + 0.012, 0)), "crimson_hi")
    for sx in (-1, 1):
        base.add(G(box(0.14, NY1 - NY0 + 0.04, 0.34).translate(sx * (NX + 0.05), (NY0 + NY1) / 2, 0)), "black")

    # gakuzuka plaque with rising-sun emblem (both faces)
    sh_bot = kasagi_top(0) - 0.3 - 0.24
    pl_y0, pl_y1 = NY1, sh_bot
    pc = (pl_y0 + pl_y1) / 2
    ph = pl_y1 - pl_y0
    base.add(G(box(0.64, ph, 0.2).translate(0, pc, 0)), "black")
    base.add(G(box(0.54, ph - 0.1, 0.24).translate(0, pc, 0)), "white")
    for sz in (1, -1):
        em = sun_emblem(0.075, ray_len=0.1, ray_w=0.03, thick=0.012, segments=14).rotate(90 * sz, 0, 0)
        base.add(G(em.translate(0, pc, sz * 0.12)), "crimson")

    # pillars (hashira) with bands and black bases (kamaki)
    for sx in (-1, 1):
        x = sx * PX
        base.add(G(cylinder(0.25, kasagi_top(x) - 0.4 - STEP2_TOP, 16, r_top=0.215, caps=False)
                   .translate(x, STEP2_TOP, 0)), "crimson")
        base.add(G(cylinder(0.35, 0.42, 16, r_top=0.33).translate(x, STEP2_TOP, 0)), "black")
        base.add(G(cylinder(0.31, 0.04, 16).translate(x, STEP2_TOP + 0.42, 0)), "graphite")
        base.add(G(cylinder(0.245, 0.12, 16).translate(x, 3.35, 0)), "white")
        base.add(G(cylinder(0.255, 0.1, 16).translate(x, 2.3, 0)), "black")
        # rope collar where the shimenawa is tied
        base.add(G(torus(0.255, 0.05, 16, 6).translate(x, 3.78, 0)), "rope")

    # shimenawa: sagging rope in front of the pillars
    RZ = 0.28
    pts = []
    n = 10
    for i in range(n + 1):
        t = i / n
        x = -PX + 2 * PX * t
        y = 3.78 - 0.3 * (1 - (2 * t - 1) ** 2)
        z = RZ * (1 - (2 * t - 1) ** 2) ** 0.5 + 0.0
        pts.append((x, y, z))
    for i in range(n):
        base.add(G(tube_between(pts[i], pts[i + 1], 0.065, 8)), "rope" if i % 2 == 0 else "rope_dark")
    # shide (zig-zag paper streamers)
    for xs in (-0.62, 0.62):
        t = (xs + PX) / (2 * PX)
        y0 = 3.78 - 0.3 * (1 - (2 * t - 1) ** 2) - 0.06
        z0 = RZ * (1 - (2 * t - 1) ** 2) ** 0.5 + 0.02
        for k in range(4):
            off = 0.05 if k % 2 == 0 else -0.05
            base.add(G(box(0.11, 0.15, 0.015).translate(xs + off, y0 - 0.08 - k * 0.13, z0)), "paper")

    # ---------------------------------------------------------------- toro lanterns
    for sx in (-1, 1):
        lan = []
        mats = []

        def L(mesh, mat):
            lan.append(mesh)
            mats.append(mat)
        y = PLATE_TOP
        L(cylinder(0.34, 0.08, 12).translate(0, y, 0), "black")
        L(cylinder(0.24, 0.07, 12, r_top=0.18).translate(0, y + 0.08, 0), "white")
        L(cylinder(0.11, 0.42, 12, r_top=0.1).translate(0, y + 0.15, 0), "white")
        L(box(0.46, 0.08, 0.46, bevel=0.06).translate(0, y + 0.61, 0), "white")
        L(box(0.36, 0.3, 0.36, bevel=0.03).translate(0, y + 0.8, 0), "white")
        for a in (0, 90, 180, 270):
            L(box(0.18, 0.18, 0.03).translate(0, y + 0.8, 0.175).rotate(0, a, 0), "teal")
        roof = lathe([(0.0, 0.0), (0.4, 0.0), (0.42, 0.04), (0.3, 0.1), (0.14, 0.2), (0.0, 0.26)], 4,
                     start_deg=45, end_deg=405, smooth=False)
        L(roof.translate(0, y + 0.95, 0), "crimson")
        L(sphere(0.06, 10, 6).translate(0, y + 1.25, 0), "black")
        for mesh, mat in zip(lan, mats):
            base.add(G(mesh.translate(sx * 3.12, 0, 0)), mat)

    # ---------------------------------------------------------------- Emitter (spins)
    em = base.node("Emitter", (0, ORB_Y, 0))
    em.add(sphere(0.2, 12, 8).translate(-0.1, ORB_Y + 0.1, 0.18), "teal_core")
    em.add(sphere(0.4, 24, 16).translate(0, ORB_Y, 0), "teal_orb")
    em.add(sphere(0.54, 24, 16).translate(0, ORB_Y, 0), "teal_veil")
    # flame tongue rising out of the orb, slightly licking sideways
    tongue = lathe([(0.0, 0.0), (0.36, 0.1), (0.38, 0.32), (0.28, 0.58), (0.15, 0.82),
                    (0.06, 0.98), (0.0, 1.08)], 16)
    tp = tongue.pos
    tp[:, 0] += 0.14 * (np.clip(tp[:, 1], 0, None) / 1.08) ** 2
    em.add(tongue.copy().translate(0, ORB_Y - 0.05, 0), "teal_veil")
    inner = tongue.copy().scale(0.62, 0.78, 0.62)
    em.add(inner.translate(0, ORB_Y + 0.05, 0), "teal")
    # two tilted rings
    for roll, yaw in ((25, 0), (-25, 0)):
        em.add(G(torus(1.12, 0.035, 48, 6).rotate(0, 0, roll).rotate(12, 0, 0).translate(0, ORB_Y, 0)), "teal")
    # four fox-fire flames orbiting
    for (x, y, z, tilt) in ((-1.05, ORB_Y + 0.5, 0.25, -25), (1.1, ORB_Y + 0.62, -0.2, 22),
                            (-0.7, ORB_Y - 0.75, -0.35, -12), (0.82, ORB_Y - 0.6, 0.4, 16)):
        f = teardrop(0.17, 0.55, 10).rotate(0, 0, tilt).translate(x, y, z)
        em.add(G(f), "teal_veil")
        em.add(G(sphere(0.06, 8, 5).translate(x, y, z)), "teal_core")
        em.add(G(teardrop(0.11, 0.38, 10).rotate(0, 0, tilt).translate(x, y, z)), "teal")
    for name in ("FX_Glow", "FX_Muzzle01"):
        fx = em.node(name, (0, ORB_Y, 0))
        fx.add(box(0.05, 0.05, 0.05).translate(0, ORB_Y, 0), "teal_core")

    return m


# =========================================================================== nanocore

def build_nanocore():
    m = Model("JapanKitsuneShrineNanocore")
    materials(m)
    b = m.node("Base")

    def side(pts_xy, width, z=0.0):
        """Convex side profile [(x, y)] extruded across Z (centred on z)."""
        return extrude([(x, -y) for x, y in pts_xy], width).rotate(90, 0, 0).translate(0, 0, z - width / 2)

    # hover pads
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = sx * 1.02, sz * 0.66
            b.add(cylinder(0.3, 0.16, 12, r_top=0.26).translate(x, 0.0, z), "black")
            b.add(disc(0.22, 12).rotate(180, 0, 0).translate(x, 0.001, z), "teal")
            b.add(torus(0.28, 0.03, 16, 4).translate(x, 0.08, z), "teal")
            b.add(cylinder(0.12, 0.2, 10).translate(x, 0.16, z), "graphite")
    # hull
    hull = [(-1.45, 0.3), (1.15, 0.3), (1.52, 0.52), (1.3, 0.86), (-1.3, 0.92), (-1.5, 0.7)]
    b.add(side(hull, 1.36), "white")
    b.add(side([(-1.48, 0.33), (1.2, 0.33), (1.42, 0.45), (-1.5, 0.45)], 1.42), "black")
    b.add(side([(-1.42, 0.6), (1.41, 0.6), (1.36, 0.7), (-1.46, 0.7)], 1.4), "crimson")
    # cockpit
    b.add(side([(0.55, 0.88), (1.3, 0.86), (1.1, 1.16), (0.6, 1.2)], 0.96), "white")
    b.add(side([(1.12, 0.9), (1.31, 0.88), (1.14, 1.14), (1.08, 1.12)], 0.8), "teal")
    b.add(box(0.5, 0.03, 0.98).translate(0.86, 1.2, 0), "crimson")
    # side emblems + running lights
    for sz in (-1, 1):
        b.add(sun_emblem(0.11, ray_len=0.12, ray_w=0.04, rays=8, thick=0.015, segments=10)
              .rotate(90 * sz, 0, 0).translate(-0.4, 0.62, sz * 0.7), "crimson")
        b.add(box(0.8, 0.04, 0.02).translate(0.6, 0.8, sz * 0.69), "teal")
        b.add(sphere(0.05, 6, 4).translate(1.44, 0.6, sz * 0.5), "teal")
        b.add(sphere(0.05, 6, 4).translate(-1.46, 0.75, sz * 0.5), "crimson_hi")

    # folded torii package on the rear deck
    deck = 0.92
    b.add(box(1.7, 0.08, 1.1, bevel=0.06).translate(-0.45, deck + 0.04, 0), "graphite")
    for sz in (-1, 1):  # two crimson pillars lying along X
        b.add(tube_between((-1.25, deck + 0.24, sz * 0.27), (0.35, deck + 0.24, sz * 0.27), 0.15, 12), "crimson")
        b.add(cylinder(0.19, 0.05, 12).rotate(0, 0, 90).translate(0.38, deck + 0.24, sz * 0.27), "black")
        b.add(cylinder(0.19, 0.05, 12).rotate(0, 0, 90).translate(-1.24, deck + 0.24, sz * 0.27), "black")
    # nuki beam across the pillars
    b.add(box(0.2, 0.14, 1.0).translate(-0.2, deck + 0.44, 0), "crimson")
    # kasagi folded on top, ends curving up
    kx = 0.95

    def ktop(x):
        return deck + 0.68 + 0.14 * (abs(x + 0.45) / kx) ** 2.4

    km = curve_beam(kx, lambda x: ktop(x - 0.45), 0.16, 0.34, 6).translate(-0.45, 0, 0)
    b.add(km, "black")
    for sx in (-1, 1):
        b.add(sphere(0.05, 6, 4).translate(-0.45 + sx * (kx + 0.02), ktop(-0.45 + sx * kx) + 0.02, 0), "teal")
    # straps
    for x in (-0.9, 0.05):
        b.add(box(0.07, 0.5, 1.02).translate(x, deck + 0.3, 0), "black")
    # small fox-fire core canister at the rear
    b.add(cylinder(0.2, 0.06, 14).translate(-1.15, deck + 0.4, 0), "black")
    b.add(sphere(0.15, 10, 6).translate(-1.15, deck + 0.6, 0), "teal_orb")
    b.add(torus(0.2, 0.025, 14, 4).rotate(90, 0, 0).translate(-1.15, deck + 0.6, 0), "black")
    return m


def main():
    tower = build_tower()
    nano = build_nanocore()
    for mdl in (tower, nano):
        p = mdl.save(OUT / f"{mdl.name}.glb")
        print(p, mdl.stats())


if __name__ == "__main__":
    main()
