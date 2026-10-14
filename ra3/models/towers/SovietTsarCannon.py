"""SovietTsarCannon (Цар-гармата) — Soviet super-heavy long-range artillery.

An 8 x 8 m riveted steel foundation with a hazard edge band and ammo bunkers, a red
pedestal with a hazard ring, a massive olive armoured casemate (turret) with red bands
and stars, and a very long barrel (~11 m) raised 25° with recuperator cylinders,
reinforcing bands and a big muzzle brake.

Nodes: Base > Turret > Barrel > FX_Muzzle01;  Base > FX_Glow (breech glow marker).
Turret forward is +X (W3D convention), so no yaw fix is needed at export.

Run:  python3 -I models/towers/SovietTsarCannon.py  ->  models/glb/SovietTsarCannon.glb
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from meshkit import (Model, box, cylinder, disc, extrude, foundation_plate,  # noqa: E402
                     lathe, merge, radial, rgb, sphere, torus, tube_between)

OUT = HERE.parent / "glb" / "SovietTsarCannon.glb"

HALF = 4.0                # foundation is 8 x 8
PLATE_H = 0.4
PED_TOP = 1.25            # top of the red pedestal
TURRET_PIVOT = (0.0, PED_TOP, 0.0)
TRUNNION = (1.55, 3.05, 0.0)
ELEV = 35.0               # barrel elevation at rest, degrees
BARREL_LEN = 13.0
KL = BARREL_LEN / 11.0    # barrel profile below is drawn for 11 m: length scale
KR = 1.25                 # ... and radius scale (a fat super-gun barrel)


def star(r_out, r_in, h, n=5):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.pi / 2 + i * math.pi / n
        pts.append((r * math.cos(a), r * math.sin(a)))
    return extrude(pts, h)


def rivet_row(p0, p1, count, r=0.06):
    out = []
    for i in range(count):
        t = (i + 0.5) / count
        p = [p0[k] + (p1[k] - p0[k]) * t for k in range(3)]
        out.append(sphere(r, 6, 4).translate(*p))
    return merge(*out)


def along_x(mesh):
    """Profile built along +Y -> turned to point along +X."""
    return mesh.rotate(0, 0, -90)


def aim(mesh):
    """Barrel-space mesh (along +X from the trunnion) -> raised by ELEV and moved to the trunnion."""
    return mesh.rotate(0, 0, ELEV).translate(*TRUNNION)


m = Model("SovietTsarCannon")
m.material("plate", rgb("#5f5c52"), metallic=0.1, roughness=0.85)
m.material("plate_top", rgb("#6d6a5f"), metallic=0.05, roughness=0.9)
m.material("concrete", rgb("#77746a"), metallic=0.0, roughness=0.92)
m.material("haz_y", rgb("#e8b923"), metallic=0.1, roughness=0.6)
m.material("haz_k", rgb("#1e1d1a"), metallic=0.2, roughness=0.7)
m.material("red", rgb("#c62b1f"), metallic=0.35, roughness=0.5)
m.material("red_dk", rgb("#7a1810"), metallic=0.35, roughness=0.55)
m.material("olive", rgb("#5b5f4a"), metallic=0.45, roughness=0.55)
m.material("olive_lt", rgb("#7d8264"), metallic=0.4, roughness=0.55)
m.material("steel", rgb("#5d6058"), metallic=0.85, roughness=0.32)
m.material("gun_dark", rgb("#1f201c"), metallic=0.7, roughness=0.45)
m.material("rivet", rgb("#a9a99c"), metallic=0.8, roughness=0.35)
m.material("cream", rgb("#f0e6c8"), metallic=0.0, roughness=0.6)
m.material("glow", rgb("#ff8a3a"), roughness=0.3, emissive=rgb("#ff7a2a", 1.6))

base = m.node("Base")

# --- foundation: 8 x 8 steel deck, hazard edge band, inner concrete pad
base.add(foundation_plate(size=2 * HALF - 0.04, top=PLATE_H, bevel=0.45), "plate")
base.add(box(2 * HALF - 1.0, 0.06, 2 * HALF - 1.0, bevel=0.35).translate(0, PLATE_H + 0.03, 0), "plate_top")
for sgn in (-1, 1):
    for axis in ("x", "z"):
        for i in range(14):
            t = -HALF + 0.55 + i * (2 * HALF - 1.1) / 14
            w = (2 * HALF - 1.1) / 14
            mat = "haz_y" if i % 2 == 0 else "haz_k"
            if axis == "x":
                b = box(w, 0.07, 0.32).translate(t + w / 2, PLATE_H + 0.035, sgn * (HALF - 0.32))
            else:
                b = box(0.32, 0.07, w).translate(sgn * (HALF - 0.32), PLATE_H + 0.035, t + w / 2)
            base.add(b, mat)
base.add(cylinder(3.05, 0.08, 40).translate(0, PLATE_H + 0.06, 0), "concrete")

# corner ammo bunkers (rear corners) and shell racks (front corners)
for sx, sz in ((-1, -1), (-1, 1)):
    cx, cz = sx * 3.05, sz * 3.05
    base.add(box(1.35, 0.85, 1.35, bevel=0.2).translate(cx, PLATE_H + 0.43, cz), "olive")
    base.add(box(1.45, 0.12, 1.45, bevel=0.22).translate(cx, PLATE_H + 0.9, cz), "olive_lt")
    base.add(box(0.7, 0.45, 0.05).translate(cx + sx * -0.0, PLATE_H + 0.35, cz - sz * 0.69), "gun_dark")
    base.add(star(0.22, 0.09, 0.03).rotate(-90, 0, 0).translate(cx, PLATE_H + 0.6, cz - sz * 0.69 - 0.03 * sz), "red")
for sz in (-1, 1):
    cx, cz = 3.05, sz * 3.05
    base.add(box(1.1, 0.12, 0.8).translate(cx, PLATE_H + 0.06, cz), "gun_dark")
    for k in range(4):
        shell = merge(cylinder(0.11, 0.55, 10), lathe([(0.11, 0.55), (0.08, 0.7), (0.0, 0.8)], 10))
        base.add(shell.translate(cx - 0.36 + k * 0.24, PLATE_H + 0.12, cz), "rivet" if k % 2 else "cream")

# deck rivets around the pad
base.add(radial(sphere(0.05, 6, 4).translate(3.25, PLATE_H + 0.07, 0), 24), "rivet")

# --- red pedestal with hazard ring and an olive bearing ring
ped = lathe([(0.0, PLATE_H), (2.75, PLATE_H), (2.75, 0.85), (2.55, 1.0), (2.45, PED_TOP), (0.0, PED_TOP)], 40, smooth=False)
base.add(ped, "red")
for i in range(28):
    a0, a1 = i * 360 / 28, (i + 0.55) * 360 / 28
    seg = lathe([(2.765, 0.55), (2.765, 0.78)], 3, start_deg=a0, end_deg=a1)
    base.add(seg, "haz_y")
base.add(cylinder(2.76, 0.23, 40, caps=False).translate(0, 0.55, 0), "haz_k")
base.add(torus(2.45, 0.09, 40, 8).translate(0, PED_TOP, 0), "steel")
base.add(radial(sphere(0.06, 6, 4).translate(2.62, 0.95, 0), 20), "rivet")

# breech glow marker sits inside the casemate
fxg = base.node("FX_Glow", (0.6, 2.6, 0.0))
fxg.add(box(0.05, 0.05, 0.05).translate(0.6, 2.6, 0.0), "glow")

# --- turret: massive armoured casemate, forward = +X
tur = base.node("Turret", TURRET_PIVOT)
y0 = PED_TOP
tur.add(cylinder(2.3, 0.35, 40).translate(0, y0, 0), "olive_lt")
# lower hull: long octagon-ish footprint
hull_pts = [(2.6, -1.25), (2.6, 1.25), (1.6, 1.85), (-2.2, 1.85), (-2.8, 1.3), (-2.8, -1.3), (-2.2, -1.85), (1.6, -1.85)]
tur.add(extrude(hull_pts, 1.35).translate(0, y0 + 0.35, 0), "olive")
# upper sloped glacis: smaller octagon on top, joined by a frustum-like band
top_pts = [(2.0, -0.95), (2.0, 0.95), (1.2, 1.45), (-1.9, 1.45), (-2.4, 1.0), (-2.4, -1.0), (-1.9, -1.45), (1.2, -1.45)]
tur.add(extrude(top_pts, 0.9).translate(0, y0 + 1.7, 0), "olive")
tur.add(extrude(top_pts, 0.08).scale(1.04, 1, 1.04).translate(0, y0 + 2.6, 0), "olive_lt")
# red band around the lower hull and red stars on both flanks
tur.add(extrude(hull_pts, 0.28).scale(1.012, 1, 1.012).translate(0, y0 + 1.05, 0), "red")
for sz in (-1, 1):
    tur.add(disc(0.42, 24).rotate(sz * 90, 0, 0).translate(-0.6, y0 + 1.35, sz * 1.88), "cream")
    tur.add(star(0.36, 0.15, 0.04).rotate(sz * 90, 0, 0).translate(-0.6, y0 + 1.35, sz * 1.9), "red")
    # side hatch and vision blocks
    tur.add(box(0.9, 0.6, 0.06).translate(-1.7, y0 + 2.05, sz * 1.48), "gun_dark")
    tur.add(rivet_row((-2.15, y0 + 0.5, sz * 1.87), (1.55, y0 + 0.5, sz * 1.87), 12), "rivet")
    tur.add(rivet_row((-1.85, y0 + 2.5, sz * 1.47), (1.15, y0 + 2.5, sz * 1.47), 9), "rivet")
# rear ammo hoist / counterweight
tur.add(box(1.0, 1.4, 2.6, bevel=0.25).translate(-3.05, y0 + 1.15, 0), "olive_lt")
tur.add(box(0.06, 0.9, 1.8).translate(-3.57, y0 + 1.2, 0), "haz_k")
for i in range(6):
    tur.add(box(0.07, 0.9, 0.3).rotate(25, 0, 0).translate(-3.58, y0 + 1.2, -0.75 + i * 0.3), "haz_y")
# commander cupola and periscope on the roof
tur.add(cylinder(0.45, 0.35, 20).translate(-1.0, y0 + 2.68, 0.7), "olive_lt")
tur.add(cylinder(0.5, 0.06, 20).translate(-1.0, y0 + 3.03, 0.7), "steel")
tur.add(box(0.18, 0.3, 0.12).translate(-0.8, y0 + 3.2, 0.7), "gun_dark")
tur.add(torus(0.42, 0.04, 20, 6).translate(-1.0, y0 + 2.86, 0.7), "red")
# roof vents
for k in range(4):
    tur.add(box(0.5, 0.08, 0.12).translate(-1.6 + k * 0.0, y0 + 2.72, -0.9 + k * 0.22), "gun_dark")

# --- barrel (pitches around the trunnion axis Z)
bar = tur.node("Barrel", TRUNNION)
# mantlet: big armoured shield the barrel goes through
mant = box(1.1, 1.7, 2.2, bevel=0.35)
bar.add(aim(mant.translate(0.35, 0, 0)), "olive")
bar.add(aim(box(0.12, 1.5, 2.0).translate(0.93, 0, 0)), "olive_lt")
bar.add(aim(along_x(cylinder(0.55, 0.5, 24)).translate(0.95, 0, 0)), "steel")
# trunnion caps on both sides of the mantlet
for sz in (-1, 1):
    bar.add(aim(cylinder(0.32, 0.3, 16).rotate(90, 0, 0).translate(0.1, 0, sz * 1.1 + (0.3 if sz < 0 else 0))), "gun_dark")
# barrel profile along +Y (radius, distance), then turned to +X
prof = [
    (0.0, 0.0), (0.52, 0.0), (0.52, 2.4), (0.47, 2.6), (0.45, 3.4), (0.5, 3.45), (0.5, 3.75), (0.43, 3.8),
    (0.4, 5.6), (0.44, 5.65), (0.44, 5.9), (0.38, 5.95), (0.34, 8.6), (0.37, 8.65), (0.37, 8.85), (0.33, 8.9),
    (0.32, 9.6), (0.47, 9.65), (0.47, 10.95), (0.2, 11.0), (0.0, 11.0),
]
barrel = along_x(lathe([(r * KR, y * KL + 1.2) for r, y in prof], 24))
bar.add(aim(barrel), "gun_dark")
# reinforcing bands in red
for y, r in ((3.45, 0.505), (5.65, 0.445), (8.65, 0.375)):
    bar.add(aim(along_x(cylinder(r * KR + 0.01, 0.24 * KL, 24, caps=False).translate(0, y * KL + 1.2, 0))), "red")
# muzzle brake: two side ports, cut look made with dark slabs
for sz in (-1, 1):
    bar.add(aim(box(0.9 * KL, 0.3 * KR, 0.06).translate(1.2 + 10.25 * KL, 0, sz * 0.47 * KR)), "haz_k")
bar.add(aim(along_x(torus(0.47 * KR, 0.06, 24, 6).translate(0, 1.2 + 9.7 * KL, 0))), "steel")
# recuperator cylinders above the barrel
for dz in (-0.28, 0.28):
    bar.add(aim(along_x(cylinder(0.24, 4.4, 16).translate(0, 1.0, 0)).translate(0, 0.78, dz * 1.2)), "olive_lt")
    bar.add(aim(along_x(cylinder(0.27, 0.15, 16).translate(0, 5.4, 0)).translate(0, 0.78, dz * 1.2)), "steel")
# muzzle marker at the very tip
tip_x = 1.2 + BARREL_LEN
a = math.radians(ELEV)
tip = (TRUNNION[0] + tip_x * math.cos(a), TRUNNION[1] + tip_x * math.sin(a), 0.0)
fxm = bar.node("FX_Muzzle01", tip)
fxm.add(box(0.05, 0.05, 0.05).translate(*tip), "glow")

if __name__ == "__main__":
    print(m.save(OUT), m.stats())
