"""AlliedAegisSpire — Вежа «Егіда» (Allies shield / anti-air missile tower), height ~7.

Slim chrome column on a stepped white/blue base, a bearing ring on top carrying the
rotating head (node "Turret"): cyan sensor eye, a mast with radar bar and beacon and
two 2x2 missile pods on side arms.  Three shield emitter posts stand on the plate corners.
The shield dome is a separate translucent node "FX_ShieldDome" so it can be toggled.

Run:  python3 -I models/towers/AlliedAegisSpire.py   ->  models/glb/AlliedAegisSpire.glb
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from meshkit import (PLATE_SIZE, PLATE_TOP, Model, box, cone, cylinder, disc, foundation_plate,  # noqa: E402
                     lathe, merge, radial, rgb, sphere, torus, tube_between)

NAME = "AlliedAegisSpire"
OUT = HERE.parent / "glb" / f"{NAME}.glb"

# The RA3-like camera looks from the (+x, +z) corner, so the head faces that way:
# turret parts are modelled facing local +Z with arms along local X, then yawed 45 deg.
YAW = 45.0

m = Model(NAME)
m.material("plate", rgb("#8ea2b9"), metallic=0.45, roughness=0.5)
m.material("plate_hi", rgb("#c9d6e6"), metallic=0.3, roughness=0.5)
m.material("plate_in", rgb("#6c819b"), metallic=0.45, roughness=0.55)
m.material("white", rgb("#eef3fb"), metallic=0.1, roughness=0.4)
m.material("blue", rgb("#2f6fd6"), metallic=0.35, roughness=0.35)
m.material("chrome", rgb("#c4d2e0"), metallic=0.95, roughness=0.16)
m.material("steel", rgb("#9fb3c8"), metallic=0.8, roughness=0.3)
m.material("dark", rgb("#4a5d76"), metallic=0.7, roughness=0.4)
m.material("hole", rgb("#0d1626"), metallic=0.2, roughness=0.85)
m.material("glow", rgb("#2cc6f2"), emissive=rgb("#33c9f5", 1.1), roughness=0.2)
m.material("shield", rgb("#7fe3ff"), emissive=rgb("#7fe3ff", 0.12), roughness=0.4, alpha=0.18)
m.material("shield_rim", rgb("#bff4ff"), emissive=rgb("#7fe3ff", 1.6), roughness=0.2, alpha=0.55)

P = PLATE_TOP


def sq_frustum(side0, side1, y0, y1):
    """Axis-aligned square frustum (hard edges)."""
    r0, r1 = side0 / math.sqrt(2), side1 / math.sqrt(2)
    return lathe([(0, y0), (r0, y0), (r1, y1), (0, y1)], 4, start_deg=45, end_deg=405, smooth=False).flat()


# =========================================================================== Base (static)
base = m.node("Base")

# foundation plate: light plate, darker inset panel, blue side stripe, corner lamps
base.add(foundation_plate(size=PLATE_SIZE - 0.02), "plate")
base.add(box(PLATE_SIZE, 0.07, PLATE_SIZE, bevel=0.355).translate(0, 0.19, 0), "blue")
base.add(box(5.0, 0.04, 5.0, bevel=0.3).translate(0, P + 0.02, 0), "plate_in")
base.add(box(4.2, 0.012, 4.2, bevel=0.25).translate(0, P + 0.045, 0), "plate_hi")
base.add(box(3.6, 0.02, 3.6, bevel=0.2).translate(0, P + 0.05, 0), "plate_in")
for sx, sz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
    base.add(cylinder(0.09, 0.03, 12).translate(2.62 * sx, P, 2.62 * sz), "glow")
    # small panel seams along the inset edges
    base.add(box(0.5, 0.02, 0.06).translate(1.4 * sx, P + 0.045, 2.53 * sz), "dark")
    base.add(box(0.06, 0.02, 0.5).translate(2.53 * sx, P + 0.045, 1.4 * sz), "dark")

# stepped base block (white with blue band and dark vents)
B0, B1 = P + 0.04, P + 0.66
base.add(box(2.5, B1 - B0, 2.5, bevel=0.16).translate(0, (B0 + B1) / 2, 0), "white")
base.add(box(2.56, 0.12, 2.56, bevel=0.18).translate(0, B1 - 0.2, 0), "blue")
base.add(box(2.58, 0.05, 2.58, bevel=0.19).translate(0, B0 + 0.04, 0), "dark")
for ang in (0, 90, 180, 270):
    for off in (-0.55, 0.55):
        base.add(box(0.36, 0.13, 0.04).translate(off, B0 + 0.24, 1.26).rotate(0, ang, 0), "dark")
base.add(box(2.36, 0.03, 2.36, bevel=0.14).translate(0, B1 + 0.015, 0), "steel")

# sloped plinth to the column
S1 = B1 + 0.45
base.add(sq_frustum(1.85, 1.15, B1 + 0.03, S1), "white")
base.add(sq_frustum(1.2, 1.12, S1, S1 + 0.05), "steel")

# chrome column
C0, C1 = S1 + 0.05, 4.15
base.add(cylinder(0.64, 0.14, 32).translate(0, C0, 0), "dark")
base.add(cylinder(0.5, C1 - C0, 32, r_top=0.47, caps=False).translate(0, C0, 0), "chrome")
base.add(cylinder(0.535, 0.28, 32).translate(0, 2.25, 0), "blue")          # blue band
for y in (1.85, 3.0, 3.45):                                                 # groove rings
    base.add(torus(0.49 if y < 3 else 0.48, 0.022, 32, 6).translate(0, y, 0), "dark")
# glowing cyan power line(s) running up the column (front-left one faces the camera)
for deg in (100, 280):
    a = math.radians(deg)
    for y0, y1 in ((C0 + 0.2, 2.2), (2.58, C1 - 0.12)):
        r = 0.5 - 0.03 * (y0 + y1 - 2 * C0) / (2 * (C1 - C0))
        strip = box(0.1, y1 - y0, 0.05).translate(0, (y0 + y1) / 2, r + 0.01)
        base.add(strip.rotate(0, 90 - deg, 0), "glow")
        cap = box(0.13, 0.05, 0.06).translate(0, y0, r + 0.005)
        base.add(cap.copy().rotate(0, 90 - deg, 0), "dark")
        base.add(cap.translate(0, y1 - y0, 0).rotate(0, 90 - deg, 0), "dark")

# bearing ring with cyan indicator lights
R0, R1 = C1 - 0.05, C1 + 0.25
base.add(cylinder(0.62, 0.1, 32, r_top=0.9).translate(0, R0 - 0.08, 0), "dark")
base.add(cylinder(0.97, R1 - R0 - 0.02, 40).translate(0, R0 + 0.02, 0), "steel")
base.add(torus(0.97, 0.035, 40, 6).translate(0, R1, 0), "chrome")
base.add(radial(box(0.1, 0.07, 0.04).translate(0, R0 + 0.12, 0.975), 12, 15), "glow")

# three shield emitter posts on the plate corners (front, left, right as seen in RA3)
for sx, sz in ((1, 1), (1, -1), (-1, 1)):
    x, z = 1.85 * sx, 1.85 * sz
    base.add(box(0.54, 0.46, 0.54, bevel=0.06).translate(x, P + 0.23, z), "white")
    base.add(box(0.57, 0.07, 0.57, bevel=0.07).translate(x, P + 0.12, z), "blue")
    base.add(cylinder(0.24, 0.06, 16, r_top=0.17).translate(x, P + 0.46, z), "steel")
    base.add(cylinder(0.055, 0.38, 10).translate(x, P + 0.5, z), "chrome")
    base.add(torus(0.13, 0.025, 16, 6).translate(x, P + 0.86, z), "dark")
    base.add(sphere(0.11, 14, 8).translate(x, P + 0.99, z), "glow")

# =========================================================================== Turret (head)
TY = R1 + 0.01
turret = base.node("Turret", (0, TY, 0))
L = []  # (mesh, material) in local head frame (faces +Z), yawed at the end


def lt(mesh, mat):
    L.append((mesh, mat))


# neck collar
turret.add(cylinder(0.6, 0.12, 32).translate(0, TY, 0), "dark")
# head block: world-axis-aligned box (reads as a diamond from the RA3 camera)
H0, H1 = TY + 0.1, TY + 1.0
turret.add(box(0.98, H1 - H0, 0.98, bevel=0.08).translate(0, (H0 + H1) / 2, 0), "white")
turret.add(box(1.02, 0.11, 1.02, bevel=0.1).translate(0, H1 - 0.22, 0), "blue")
turret.add(box(1.02, 0.05, 1.02, bevel=0.1).translate(0, H0 + 0.05, 0), "dark")
turret.add(box(0.8, 0.05, 0.8, bevel=0.07).translate(0, H1 + 0.025, 0), "steel")
# cyan sensor eye on the face toward the camera's left (+Z face), small aux eye on +X face
EY = (H0 + H1) / 2 - 0.08
turret.add(cylinder(0.2, 0.06, 20).rotate(90, 0, 0).translate(0, EY, 0.49), "dark")
turret.add(sphere(0.14, 20, 10).scale(1, 1, 0.55).translate(0, EY, 0.54), "glow")
turret.add(box(0.36, 0.1, 0.04).translate(0, EY, 0.5).rotate(0, 90, 0), "dark")
turret.add(box(0.26, 0.05, 0.03).translate(0, EY, 0.52).rotate(0, 90, 0), "glow")

# mast with radar bar and beacon
M1 = H1 + 1.35
turret.add(cylinder(0.12, 0.12, 16, r_top=0.08).translate(0, H1 + 0.05, 0), "dark")
turret.add(cylinder(0.05, M1 - H1, 12, r_top=0.035).translate(0, H1, 0), "steel")
RB = H1 + 0.82
lt(box(1.2, 0.07, 0.12, bevel=0.03).rotate(0, 0, -8).translate(0, RB, 0), "chrome")
lt(box(1.0, 0.025, 0.13).rotate(0, 0, -8).translate(0, RB - 0.04, 0.0), "dark")
lt(box(0.14, 0.14, 0.18).translate(0, RB, 0), "dark")
lt(box(0.3, 0.05, 0.03).translate(0, H1 + 0.45, 0.06), "steel")
turret.add(cylinder(0.09, 0.05, 12).translate(0, M1, 0), "dark")
turret.add(sphere(0.13, 16, 10).translate(0, M1 + 0.15, 0), "glow")
turret.add(cone(0.025, 0.2, 8).translate(0, M1 + 0.26, 0), "steel")

# arms and missile pods (2x2 tubes with white warheads)
POD_X, POD_Y, POD_Z = 1.46, H1 + 0.0, 0.05
PW, PH, PD = 1.14, 1.14, 0.94
muzzles = []
for s in (-1, 1):
    # arm: dark bar + pivot hub
    lt(box(0.7, 0.2, 0.36, bevel=0.04).translate(s * 0.76, H0 + 0.38, 0), "dark")
    lt(tube_between((s * 0.78, H0 + 0.4, 0), (s * 0.9, H0 + 0.4, 0), 0.2, 16), "steel")
    lt(box(0.2, 0.42, 0.3, bevel=0.03).translate(s * 0.9, H0 + 0.56, 0), "dark")
    px = s * POD_X
    # pod body
    lt(box(PW, PH, PD, bevel=0.08).translate(px, POD_Y, POD_Z), "white")
    lt(box(PW + 0.03, 0.09, PD + 0.03, bevel=0.09).translate(px, POD_Y - PH / 2 + 0.14, POD_Z), "blue")
    lt(box(PW - 0.12, 0.05, PD - 0.1, bevel=0.05).translate(px, POD_Y + PH / 2 + 0.02, POD_Z), "steel")
    lt(box(PW - 0.1, PH - 0.1, 0.08).translate(px, POD_Y, POD_Z - PD / 2 - 0.03), "dark")
    lt(box(0.06, PH - 0.3, PD - 0.3).translate(px + s * (PW / 2 + 0.02), POD_Y + 0.05, POD_Z), "dark")
    fz = POD_Z + PD / 2
    lt(box(PW - 0.08, PH - 0.22, 0.03).translate(px, POD_Y + 0.07, fz + 0.005), "steel")
    for ix in (-1, 1):
        for iy in (-1, 1):
            cx, cy = px + ix * 0.255, POD_Y + 0.07 + iy * 0.245
            lt(cylinder(0.205, 0.09, 18).rotate(90, 0, 0).translate(cx, cy, fz), "steel")
            lt(disc(0.165, 18).rotate(90, 0, 0).translate(cx, cy, fz + 0.092), "hole")
            nose = lathe([(0, 0), (0.115, 0), (0.115, 0.05), (0.095, 0.12), (0.05, 0.17), (0, 0.19)], 14)
            lt(nose.rotate(90, 0, 0).translate(cx, cy, fz + 0.04), "white")
    lt(box(0.07, 0.04, 0.02).translate(px - s * 0.42, POD_Y - PH / 2 + 0.09, fz + 0.02), "glow")
    muzzles.append((px, POD_Y + 0.07, fz + 0.2))

for mesh, mat in L:
    turret.add(mesh.rotate(0, YAW, 0), mat)

for i, (x, y, z) in enumerate(muzzles):
    # local -> world with the same yaw (rotate((x,0,z)) around Y)
    a = math.radians(YAW)
    wx, wz = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
    fx = turret.node(f"FX_Muzzle0{i + 1}", (wx, y, wz))
    fx.add(box(0.05, 0.05, 0.05).translate(wx, y, wz), "glow")

# =========================================================================== shield dome FX
# Toggleable translucent shield: hemisphere of radius 2.9 stretched upward so it encloses
# the whole tower (as in the concept art), plus a glowing rim ring on the plate.
DR, DH = 2.9, 7.9
dome = m.node("FX_ShieldDome", (0, P, 0))
rings = 14
prof = [(DR * math.cos(0.5 * math.pi * i / rings) ** 0.75, P + DH * math.sin(0.5 * math.pi * i / rings))
        for i in range(rings + 1)]
dome.add(lathe(prof, 40), "shield")
dome.add(torus(DR, 0.035, 48, 6).translate(0, P + 0.04, 0), "shield_rim")

if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    m.save(OUT)
    print(OUT, m.stats())
