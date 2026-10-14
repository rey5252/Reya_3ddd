"""SovietBigBertha («Велика Берта») — Soviet super-heavy railway gun (in the spirit of the 80 cm siege guns).

A superweapon BUILDING, not a turret: the gun stands still on its railway siding (a 16 x 16 m concrete
pad with a ballast bed) and fires on any point of the map as a special power. On the siding: two
parallel double tracks with sleepers running across the whole pad, ten four-wheel bogies,
two massive box-girder side frames with truss bracing and rivets, upper decks with railings and
ladders, the breech house, two shell-hoist towers at the back and a ~17 m barrel with recuperators.
Soviet dark olive-grey with red stars.

Nodes: Base > Carriage (static) > Barrel > FX_Muzzle01, Carriage > FX_Glow;  the gun points along +X.

Writes  models/glb/SovietBigBertha.glb           (the game model)
        models/glb/concepts/SovietBigBertha_fire.glb (presentation scene: firing at a fortress)
Run:    python3 -I models/towers/SovietBigBertha.py
"""
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from meshkit import (Model, box, cylinder, disc, extrude, foundation_plate, lathe,  # noqa: E402
                     merge, rgb, sphere, torus, tube_between)

OUT = HERE.parent / "glb" / "SovietBigBertha.glb"
OUT_FIRE = HERE.parent / "glb" / "concepts" / "SovietBigBertha_fire.glb"

HALF = 12.0                      # design units; the whole model is scaled by SCALE at the end
SCALE = 1.6                      # -> 38 x 38 m siding, ~33 m carriage, 32 m barrel, ~11 m to the trunnions
PLATE_H = 0.4
TT_TOP = PLATE_H + 0.15          # turntable deck
TRACK_Z = (-1.9, 1.9)            # two parallel double tracks
GAUGE = 1.435
RAIL_Y = TT_TOP + 0.32
GIRDER_Y0, GIRDER_Y1 = RAIL_Y + 0.75, RAIL_Y + 3.0
DECK_Y = GIRDER_Y1
TRUNNION = (1.5, DECK_Y + 2.6, 0.0)
ELEV = 35.0
BARREL_LEN = 20.0
KL = BARREL_LEN / 17.0           # barrel profile below is drawn for 17 units
KR = 1.15


def star(r_out, r_in, h, n=5):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.pi / 2 + i * math.pi / n
        pts.append((r * math.cos(a), r * math.sin(a)))
    return extrude(pts, h)


def railing(x0, x1, z, y, posts=10):
    parts = [tube_between((x0, y + 1.0, z), (x1, y + 1.0, z), 0.035, 5),
             tube_between((x0, y + 0.55, z), (x1, y + 0.55, z), 0.025, 5)]
    for i in range(posts + 1):
        x = x0 + (x1 - x0) * i / posts
        parts.append(tube_between((x, y, z), (x, y + 1.0, z), 0.03, 5))
    return merge(*parts)


def ladder(p_bottom, p_top, width=0.5, rungs=8, axis_z=True):
    pb, pt = np.array(p_bottom, float), np.array(p_top, float)
    off = np.array([0, 0, width / 2]) if axis_z else np.array([width / 2, 0, 0])
    parts = [tube_between(pb - off, pt - off, 0.03, 5), tube_between(pb + off, pt + off, 0.03, 5)]
    for i in range(1, rungs):
        p = pb + (pt - pb) * i / rungs
        parts.append(tube_between(p - off, p + off, 0.022, 4))
    return merge(*parts)


def truss_panel(x0, x1, y0, y1, z, bays=8):
    """Diagonal truss bracing on a girder face (flat bars)."""
    parts = []
    w = (x1 - x0) / bays
    for i in range(bays):
        a = (x0 + i * w, y0, z)
        b = (x0 + (i + 1) * w, y1, z)
        c = (x0 + (i + 1) * w, y0, z)
        d = (x0 + i * w, y1, z)
        parts.append(tube_between(a, b, 0.05, 4))
        parts.append(tube_between(c, d, 0.05, 4))
        parts.append(tube_between((x0 + i * w, y0, z), (x0 + i * w, y1, z), 0.06, 4))
    return merge(*parts)


def aim(mesh):
    return mesh.rotate(0, 0, ELEV).translate(*TRUNNION)


def build(fire=False, show=False):
    m = Model("SovietBigBertha" + ("_fire" if fire else "_show" if show else ""))
    m.material("plate", rgb("#77746a"), metallic=0.05, roughness=0.9)
    m.material("plate_top", rgb("#6d6a5f"), metallic=0.05, roughness=0.9)
    m.material("haz_y", rgb("#e8b923"), metallic=0.1, roughness=0.6)
    m.material("haz_k", rgb("#1e1d1a"), metallic=0.2, roughness=0.7)
    m.material("olive", rgb("#4f5446"), metallic=0.5, roughness=0.5)
    m.material("olive_lt", rgb("#6a7060"), metallic=0.45, roughness=0.55)
    m.material("gun_dark", rgb("#2a2c27"), metallic=0.7, roughness=0.42)
    m.material("steel", rgb("#8a8d84"), metallic=0.9, roughness=0.3)
    m.material("rail", rgb("#9a9a95"), metallic=0.95, roughness=0.25)
    m.material("sleeper", rgb("#4a3b2a"), metallic=0.0, roughness=0.9)
    m.material("ballast", rgb("#8a857a"), metallic=0.0, roughness=1.0)
    m.material("red", rgb("#c62b1f"), metallic=0.35, roughness=0.5)
    m.material("cream", rgb("#f0e6c8"), metallic=0.0, roughness=0.6)
    m.material("rivet", rgb("#a9a99c"), metallic=0.8, roughness=0.35)
    m.material("glow", rgb("#ff8a3a"), roughness=0.3, emissive=rgb("#ff7a2a", 1.4))

    base = m.node("Base")
    # --- concrete siding pad with hazard edge
    base.add(foundation_plate(size=2 * HALF - 0.04, top=PLATE_H, bevel=0.6), "plate")
    n = 24
    w = (2 * HALF - 1.4) / n
    for sgn in (-1, 1):
        for i in range(n):
            t = -HALF + 0.7 + i * w + w / 2
            mat = "haz_y" if i % 2 == 0 else "haz_k"
            base.add(box(w, 0.07, 0.4).translate(t, PLATE_H + 0.035, sgn * (HALF - 0.4)), mat)
            base.add(box(0.4, 0.07, w).translate(sgn * (HALF - 0.4), PLATE_H + 0.035, t), mat)
    # ballast bed (gravel) under the siding, with a red edge line
    base.add(box(2 * HALF - 0.9, 0.15, 7.6, bevel=0.3).translate(0, PLATE_H + 0.075, 0), "ballast")
    for zs in (-1, 1):
        base.add(box(2 * HALF - 0.9, 0.05, 0.12).translate(0, PLATE_H + 0.16, zs * 3.9), "red")

    # --- the gun on its siding = Carriage (static building)
    tur = base.node("Carriage", (0.0, TT_TOP, 0.0))
    L0, L1 = -HALF + 0.45, HALF - 0.45
    for zc in TRACK_Z:
        for k in range(48):
            x = L0 + 0.25 + k * 0.48
            tur.add(box(0.24, 0.12, 2.6).translate(x, TT_TOP + 0.06, zc), "sleeper")
        for dz in (-GAUGE / 2, GAUGE / 2):
            tur.add(box(L1 - L0, 0.14, 0.09).translate(0, TT_TOP + 0.19, zc + dz), "rail")
    # bogies: 8 per track, 4 wheels each
    for zc in TRACK_Z:
        for k in range(8):
            bx = -8.75 + k * 2.5
            tur.add(box(2.3, 0.35, 1.9, bevel=0.1).translate(bx, RAIL_Y + 0.45, zc), "gun_dark")
            for wx in (-0.75, 0.75):
                for dz in (-GAUGE / 2, GAUGE / 2):
                    tur.add(cylinder(0.33, 0.1, 14).rotate(90, 0, 0).translate(bx + wx, RAIL_Y + 0.07, zc + dz + (0.05 if dz > 0 else -0.05)), "steel")
            tur.add(cylinder(0.2, 0.3, 10).translate(bx, RAIL_Y + 0.62, zc), "olive")
    # two massive side girders with truss bracing, rivet rows and red stars
    for zc in TRACK_Z:
        tur.add(box(20.4, GIRDER_Y1 - GIRDER_Y0, 1.3, bevel=0.15).translate(0, (GIRDER_Y0 + GIRDER_Y1) / 2, zc), "olive")
        zf = zc + (0.67 if zc > 0 else -0.67)
        tur.add(truss_panel(-9.9, 9.9, GIRDER_Y0 + 0.15, GIRDER_Y1 - 0.15, zf, 16), "olive_lt")
        for y in (GIRDER_Y0 + 0.12, GIRDER_Y1 - 0.12):
            for k in range(45):
                tur.add(sphere(0.045, 6, 4).translate(-9.95 + k * 0.45, y, zf), "rivet")
        tur.add(disc(0.62, 24).rotate(90 if zc > 0 else -90, 0, 0).translate(-1.4, (GIRDER_Y0 + GIRDER_Y1) / 2, zf + (0.03 if zc > 0 else -0.03)), "cream")
        tur.add(star(0.52, 0.21, 0.05).rotate(90 if zc > 0 else -90, 0, 0).translate(-1.4, (GIRDER_Y0 + GIRDER_Y1) / 2, zf + (0.04 if zc > 0 else -0.04)), "red")
        tur.add(box(20.4, 0.16, 0.02).translate(0, GIRDER_Y1 - 0.45, zf + (0.01 if zc > 0 else -0.01)), "red")
    # main deck between the girders + upper gun cradle
    tur.add(box(20.0, 0.3, 5.2).translate(0, DECK_Y + 0.15, 0), "olive_lt")
    tur.add(box(9.0, 2.4, 3.6, bevel=0.2).translate(0.8, DECK_Y + 1.5, 0), "olive")
    tur.add(box(9.1, 0.18, 3.7).translate(0.8, DECK_Y + 2.1, 0), "red")
    for zs in (-1, 1):
        tur.add(railing(-9.9, 9.9, zs * 2.55, DECK_Y + 0.3, 26), "steel")
        tur.add(ladder((8.6, RAIL_Y + 0.3, zs * 2.75), (9.2, DECK_Y + 0.3, zs * 2.75), 0.55, 10, axis_z=False), "steel")
        tur.add(ladder((-8.0, RAIL_Y + 0.3, zs * 2.75), (-8.6, DECK_Y + 0.3, zs * 2.75), 0.55, 10, axis_z=False), "steel")
        tur.add(box(1.6, 0.08, 0.9).translate(-6.0, DECK_Y + 2.4, zs * 1.8), "olive_lt")              # gallery
        tur.add(railing(-6.8, -5.2, zs * 2.25, DECK_Y + 2.44, 4), "steel")
    # breech house at the rear with windows
    tur.add(box(3.6, 3.4, 3.8, bevel=0.15).translate(-5.0, DECK_Y + 2.0, 0), "olive")
    tur.add(box(3.7, 0.2, 3.9).translate(-5.0, DECK_Y + 3.8, 0), "gun_dark")
    for zs in (-1, 1):
        for k in range(3):
            tur.add(box(0.5, 0.4, 0.04).translate(-6.2 + k * 1.2, DECK_Y + 2.8, zs * 1.92), "gun_dark")
    # two shell-hoist towers at the very back with a crane beam
    for zs in (-1, 1):
        x, z = -9.0, zs * 2.0
        top = DECK_Y + 7.0
        for dx in (-0.4, 0.4):
            for dz in (-0.4, 0.4):
                tur.add(tube_between((x + dx, DECK_Y + 0.3, z + dz), (x + dx, top, z + dz), 0.07, 6), "olive")
        for k in range(7):
            y0 = DECK_Y + 0.3 + k * (top - DECK_Y - 0.3) / 7
            y1 = y0 + (top - DECK_Y - 0.3) / 7
            tur.add(tube_between((x - 0.4, y0, z - 0.4), (x + 0.4, y1, z - 0.4), 0.035, 4), "olive_lt")
            tur.add(tube_between((x - 0.4, y0, z + 0.4), (x + 0.4, y1, z + 0.4), 0.035, 4), "olive_lt")
            tur.add(tube_between((x - 0.4, y1, z - 0.4), (x - 0.4, y0, z + 0.4), 0.035, 4), "olive_lt")
        tur.add(box(1.1, 0.5, 1.1).translate(x, top + 0.25, z), "olive")
    tur.add(box(0.4, 0.4, 4.8).translate(-9.0, DECK_Y + 7.4, 0), "gun_dark")
    tur.add(tube_between((-9.0, DECK_Y + 7.2, 0), (-9.0, DECK_Y + 5.0, 0), 0.02, 4), "steel")
    shell = merge(cylinder(0.4, 1.6, 14), lathe([(0.4, 1.6), (0.3, 2.2), (0.0, 2.6)], 14))
    tur.add(shell.translate(-9.0, DECK_Y + 2.3, 0), "cream")
    tur.add(torus(0.41, 0.05, 14, 4).translate(-9.0, DECK_Y + 2.6, 0), "red")

    # --- barrel (pitches) with jacket, chase, bands, recuperators, muzzle
    bar = tur.node("Barrel", TRUNNION)
    bar.add(aim(box(2.8, 2.3, 2.8, bevel=0.3).translate(-0.6, 0, 0)), "olive")                    # cradle
    for zs in (-1, 1):
        bar.add(aim(cylinder(0.5, 0.35, 16).rotate(90, 0, 0).translate(0, 0, zs * 1.2 + (0.35 if zs < 0 else 0))), "gun_dark")
    prof = [(0.0, -2.2), (0.95, -2.2), (0.95, 3.2), (0.82, 3.6), (0.72, 5.0), (0.6, 11.0), (0.52, 15.8),
            (0.56, 16.2), (0.56, BARREL_LEN), (0.36, BARREL_LEN), (0.0, BARREL_LEN)]
    prof = [(r * KR, y * KL if y > 0 else y) for r, y in prof]
    bar.add(aim(lathe(prof, 28).rotate(0, 0, -90)), "gun_dark")
    for xb, r in ((3.25, 0.9), (8.0, 0.68)):
        bar.add(aim(cylinder(r * KR + 0.01, 0.5 * KL, 28, caps=False).rotate(0, 0, -90).translate(xb * KL, 0, 0)), "cream" if xb > 5 else "olive_lt")
    for zs in (-0.42, 0.42):
        bar.add(aim(tube_between((-1.6, 1.25, zs * 1.1), (5.0, 1.25, zs * 1.1), 0.34, 14)), "olive_lt")
        bar.add(aim(tube_between((4.95, 1.25, zs * 1.1), (5.2, 1.25, zs * 1.1), 0.38, 14)), "steel")
    a = math.radians(ELEV)
    tip = (TRUNNION[0] + BARREL_LEN * math.cos(a), TRUNNION[1] + BARREL_LEN * math.sin(a), 0.0)
    fx = bar.node("FX_Muzzle01", tip)
    fx.add(box(0.05, 0.05, 0.05).translate(*tip), "glow")
    glow = (TRUNNION[0] - 2.0, TRUNNION[1], 0.0)
    fxg = tur.node("FX_Glow", glow)
    fxg.add(box(0.05, 0.05, 0.05).translate(*glow), "glow")

    if show:
        # scale reference (presentation only): a ~6.5 m tank and soldiers standing next to the carriage
        m.material("tank", rgb("#4f5446"), metallic=0.5, roughness=0.5)
        m.material("uniform", rgb("#5a5a3c"), roughness=0.8)
        m.material("skin", rgb("#d9b08c"), roughness=0.6)
        k = 1.0 / SCALE                        # these are drawn in metres, the model is scaled at the end
        ref = base.node("FX_ScaleRef", (0, 0, 0))
        tx, tz = 4.0, 9.5
        ref.add(box(6.5 * k, 1.2 * k, 3.0 * k, bevel=0.3 * k).translate(tx, PLATE_H + 1.0 * k, tz), "tank")
        for zz in (-1.35, 1.35):
            ref.add(box(6.7 * k, 0.9 * k, 0.6 * k, bevel=0.15 * k).translate(tx, PLATE_H + 0.45 * k, tz + zz * k), "gun_dark")
        ref.add(cylinder(1.2 * k, 0.8 * k, 16).translate(tx - 0.5 * k, PLATE_H + 1.6 * k, tz), "tank")
        ref.add(tube_between((tx - 0.5 * k, PLATE_H + 2.0 * k, tz), (tx + 4.0 * k, PLATE_H + 2.1 * k, tz), 0.1 * k, 8), "gun_dark")
        for i, (sx, sz) in enumerate(((-2.0, 6.2), (-1.2, 6.5), (0.4, 6.1), (6.5, 6.4), (7.2, 6.0), (-6.0, 6.3))):
            ref.add(cylinder(0.25 * k, 1.5 * k, 8).translate(sx, PLATE_H, sz), "uniform")
            ref.add(sphere(0.14 * k, 8, 5).translate(sx, PLATE_H + 1.65 * k, sz), "skin")
    if fire:
        m.material("smoke", rgb("#c9c4bd"), roughness=1.0, alpha=0.5)
        m.material("smoke_dk", rgb("#55524d"), roughness=1.0, alpha=0.55)
        m.material("flash", rgb("#ffd27a"), roughness=0.2, emissive=rgb("#ffb43a", 1.4))
        m.material("dirt", rgb("#6b5a43"), roughness=1.0)
        m.material("bunker", rgb("#9b968b"), roughness=0.9)
        sc = base.node("FX_Scene", (0, 0, 0))
        tipv = np.array(tip)
        d = np.array([math.cos(a), math.sin(a), 0.0])
        rng = np.random.RandomState(4)
        sc.add(sphere(2.2, 18, 10).scale(1.4, 0.9, 1.0).translate(*(tipv + d * 2.2)), "flash")
        for k in range(22):
            c = tipv + d * rng.uniform(2.0, 9.0) + rng.normal(0, 1.2, 3) * [1, 0.7, 1.4]
            sc.add(sphere(rng.uniform(1.2, 2.6), 12, 8).translate(*c), "smoke" if k % 3 else "smoke_dk")
        for k in range(10):
            c = np.array([rng.uniform(-6, 6), rng.uniform(0.5, 1.5), rng.choice([-1, 1]) * rng.uniform(4.0, 6.5)])
            sc.add(sphere(rng.uniform(0.8, 1.5), 10, 6).translate(*c), "smoke")       # dust kicked up around the gun
        # the target: an enemy fortress far down-range, blown apart
        T = np.array([34.0, 0.0, -4.0])
        sc.add(box(7.0, 3.0, 6.0, bevel=0.4).translate(T[0], 1.5, T[2]), "bunker")
        sc.add(box(2.0, 4.5, 2.0).translate(T[0] - 3.0, 2.2, T[2] + 2.5), "bunker")
        sc.add(lathe([(0.0, 0.0), (5.0, 0.0), (5.5, 0.5), (4.0, 0.1), (0.0, -1.0)], 28).translate(T[0] + 2.0, 0.05, T[2] - 1.0), "dirt")
        sc.add(sphere(3.5, 18, 10).translate(T[0] + 1.0, 3.0, T[2]), "flash")
        for k in range(14):
            sc.add(sphere(rng.uniform(1.5, 3.0), 12, 8).translate(T[0] + rng.normal(0, 2), 3 + k * 1.3, T[2] + rng.normal(0, 2)),
                   "smoke_dk" if k > 3 else "dirt")
        for k in range(18):
            sc.add(box(rng.uniform(0.4, 1.2), 0.5, rng.uniform(0.4, 1.0)).rotate(*rng.uniform(0, 90, 3))
                   .translate(T[0] + rng.normal(0, 5), rng.uniform(0.3, 6), T[2] + rng.normal(0, 5)), "bunker")
    return scale_model(m, SCALE)


def scale_model(m, k):
    """Scale the whole model (geometry is in world coordinates, node pivots too)."""
    for nd in m._walk():
        nd.translation = tuple(v * k for v in nd.translation)
        nd.prims = {mat: mesh.scale(k) for mat, mesh in nd.prims.items()}
    return m


SHOW_BINDINGS = {
    "plate": {"texture": "soviet/concrete", "tile": 4.0}, "haz_y": "soviet/hazard",
    "haz_k": {"texture": "soviet/hazard", "white": 0.15}, "olive": "soviet/steel_olive",
    "olive_lt": "soviet/steel_olive", "gun_dark": "soviet/dark_metal", "red": "soviet/paint_red",
    "ballast": {"texture": "soviet/concrete", "tile": 1.5}, "bunker": "soviet/concrete", "tank": "soviet/steel_olive"}

if __name__ == "__main__":
    print(build().save(OUT), "game model")
    OUT_FIRE.parent.mkdir(parents=True, exist_ok=True)
    ms = build(show=True)
    print(ms.save(OUT_FIRE.with_name("SovietBigBertha_show.glb"), textures=True, bindings=SHOW_BINDINGS), ms.stats())
    mf = build(fire=True)
    print(mf.save(OUT_FIRE, textures=True, bindings=SHOW_BINDINGS), mf.stats())
