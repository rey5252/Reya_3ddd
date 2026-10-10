"""JapanTenguSpire (Шпиль Тенгу) — Empire of the Rising Sun missile spire + its nanocore.

Run:  python3 -I models/towers/JapanTenguSpire.py
Writes models/glb/JapanTenguSpire.glb and models/glb/JapanTenguSpireNanocore.glb.

Layout (RA3 camera looks from the +X+Z diagonal): the rising-sun emblem and the slit
windows face the camera (yaw 45 deg), the two missile pods sit left/right of the core
(yaw 315 / 135 deg). Pagoda roofs have their upturned corners on the diagonals, so the
tips point left/right/front/back on screen, like the concept art.
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import numpy as np  # noqa: E402

from meshkit import (PLATE_TOP, Mesh, Model, box, cone, cylinder, disc, extrude,  # noqa: E402
                     foundation_plate, lathe, merge, radial, rgb, sphere, torus, tube_between)

OUT_DIR = HERE.parent / "glb"

FRONT = 45.0           # yaw (deg) facing the RA3 camera
POD_YAWS = (315.0, 135.0)  # screen-left, screen-right


# --------------------------------------------------------------------------- helpers

def grid(P, wrap=True, orient=None):
    """Triangulated (nu, nv, 3) point grid; u wraps around. Smooth area-weighted normals.

    orient: 'up' | 'down' | 'out' — flips the winding if the faces point the wrong way.
    """
    P = np.asarray(P, np.float32)
    nu, nv, _ = P.shape
    pos = P.reshape(-1, 3)
    idx = []
    for i in range(nu if wrap else nu - 1):
        i2 = (i + 1) % nu
        for j in range(nv - 1):
            a, b, c, d = i * nv + j, i2 * nv + j, i2 * nv + j + 1, i * nv + j + 1
            idx += [(a, b, c), (a, c, d)]
    idx = np.array(idx, np.uint32)
    tri = pos[idx]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    if orient:
        cen = tri.mean(1)
        if orient == "up":
            ref = np.tile([0, 1, 0], (len(cen), 1))
        elif orient == "down":
            ref = np.tile([0, -1, 0], (len(cen), 1))
        else:
            ref = cen * np.array([1, 0, 1])
        if (fn * ref).sum() < 0:
            idx = idx[:, ::-1].copy()
            fn = -fn
    nrm = np.zeros_like(pos)
    for k in range(3):
        np.add.at(nrm, idx[:, k], fn)
    l = np.linalg.norm(nrm, axis=1, keepdims=True)
    l[l == 0] = 1
    return Mesh(pos, nrm / l, np.zeros((len(pos), 2)), idx)


def place(mesh, yaw, r=0.0, y=0.0, tilt=0.0):
    """Mesh built facing +Z (outward) -> tilt outward, rotate to yaw, push out to radius r."""
    if tilt:
        mesh.rotate(tilt, 0, 0)
    mesh.rotate(0, yaw, 0)
    a = math.radians(yaw)
    return mesh.translate(r * math.sin(a), y, r * math.cos(a))


def roof(r_mid, r_tip, y_eave, y_top, r_top, lift, thick=0.1, nu=64, nv=8):
    """Curved pagoda roof with four upturned corners on the diagonals.

    Returns (top_mesh, rim_mesh, under_mesh, tip_points).
    """
    th = np.linspace(0, 2 * math.pi, nu, endpoint=False)
    c = (1 + np.cos(4 * (th - math.pi / 4))) / 2          # 1 at diagonals, 0 on axes
    R = r_mid + (r_tip - r_mid) * c ** 2
    L = lift * c ** 4
    ct, st = np.cos(th), np.sin(th)
    drop = y_top - y_eave

    def surf_y(t):
        return y_top - drop * (1 - (1 - t) ** 2) + L * t ** 3

    def ring(rad, y):
        return np.stack([rad * ct, np.broadcast_to(y, th.shape), rad * st], -1)

    ts = np.linspace(0, 1, nv)
    top = grid(np.stack([ring(r_top + (R - r_top) * t, surf_y(t)) for t in ts], 1), orient="up")
    ye = surf_y(1.0)
    rim = grid(np.stack([ring(R, ye), ring(R * 1.015 + 0.012, ye - thick * 0.5),
                         ring(R * 0.995, ye - thick)], 1), orient="out")
    us = np.linspace(1, 0.08, 5)
    under = grid(np.stack([ring(r_top + (R * 0.995 - r_top) * t, surf_y(t) - thick) for t in us], 1),
                 orient="down")
    tips = []
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        tips.append((r_tip * 1.01 * math.cos(a), y_top - drop + lift - thick * 0.35, r_tip * 1.01 * math.sin(a)))
    return top, rim, under, tips


def band(r, y0, h, seg=48):
    return cylinder(r, h, seg, caps=False).translate(0, y0, 0)


def missile(r=0.11, body=0.3, nose=0.26):
    """Missile standing on y=0: white body + crimson ogive nose. Returns (white, crimson, tip_y)."""
    w = cylinder(r, body, 12)
    n = lathe([(r, 0), (r * 0.92, nose * 0.35), (r * 0.62, nose * 0.7), (r * 0.25, nose * 0.93), (0, nose)],
              12).translate(0, body, 0)
    return w, n, body + nose


# --------------------------------------------------------------------------- materials

def materials(m):
    m.material("white", rgb("#f4f1ec"), metallic=0.15, roughness=0.42)
    m.material("plate", rgb("#dcd7cf"), metallic=0.1, roughness=0.6)
    m.material("panel", rgb("#a9a49c"), metallic=0.2, roughness=0.55)
    m.material("crimson", rgb("#c8102e"), metallic=0.25, roughness=0.38)
    m.material("black", rgb("#1d1d22"), metallic=0.5, roughness=0.45)
    m.material("teal", rgb("#3fe0c5"), metallic=0.0, roughness=0.3, emissive=rgb("#3fe0c5", 2.0))
    m.material("orb", rgb("#b8fff2"), metallic=0.0, roughness=0.2, emissive=rgb("#3fe0c5", 2.5))
    m.material("halo", rgb("#3fe0c5"), metallic=0.0, roughness=0.2, emissive=rgb("#3fe0c5", 1.2), alpha=0.28)


# --------------------------------------------------------------------------- tower

def build_tower():
    m = Model("JapanTenguSpire")
    materials(m)
    base = m.node("Base")
    T = PLATE_TOP

    # ---- foundation plate
    base.add(foundation_plate(), "plate")
    # top border: crimson frame + black hairline, raised deck inside (all within the 6x6)
    base.add(box(5.86, 0.02, 5.86, bevel=0.34).translate(0, T + 0.01, 0), "crimson")
    base.add(box(5.7, 0.03, 5.7, bevel=0.33).translate(0, T + 0.015, 0), "black")
    base.add(box(5.64, 0.04, 5.64, bevel=0.32).translate(0, T + 0.02, 0), "plate")
    base.add(box(4.9, 0.05, 4.9, bevel=0.3).translate(0, T + 0.025, 0), "panel")
    base.add(box(4.84, 0.06, 4.84, bevel=0.29).translate(0, T + 0.03, 0), "plate")
    for k in range(4):
        a = 45 + 90 * k
        tri = extrude([(-0.2, 0.0), (0.2, 0.0), (0.0, -0.32)], 0.05).translate(0, T + 0.03, 0)
        base.add(place(tri, a, 3.7), "crimson")
        base.add(place(box(0.3, 0.05, 0.1).translate(0, T + 0.06, 0), a, 3.2), "teal")
    # edge-midpoint plate clamps
    for k in range(4):
        base.add(place(box(0.7, 0.05, 0.16).translate(0, T + 0.06, 0), 90 * k, 2.62), "panel")

    # ---- base drum
    RD, D0, D1 = 2.1, T, 1.1
    base.add(cylinder(RD, D1 - D0, 56).translate(0, D0, 0), "white")
    base.add(band(RD + 0.015, D0, 0.16, 56), "black")
    base.add(band(RD + 0.015, D1 - 0.24, 0.09, 56), "crimson")
    base.add(cylinder(RD + 0.04, 0.05, 56, r_top=RD - 0.02).translate(0, D1 - 0.01, 0), "white")
    base.add(torus(1.72, 0.025, 48, 6).translate(0, D1 + 0.03, 0), "panel")
    for a in (FRONT - 52, FRONT - 26, FRONT + 26, FRONT + 52, FRONT + 180 - 30, FRONT + 180 + 30):
        base.add(place(box(0.035, 0.42, 0.04).translate(0, 0.0, 0), a, RD, D0 + 0.42), "panel")
    for a in (FRONT - 39, FRONT + 39):
        base.add(place(box(0.24, 0.07, 0.05), a, RD + 0.01, D0 + 0.3), "teal")

    # rising-sun emblem (white panel, crimson disc + 12 rays), facing the camera
    emb = box(0.92, 0.6, 0.08, bevel=0.05)
    rays = []
    for i in range(12):
        rr = extrude([(-0.032, 0.13), (0.032, 0.13), (0.0, 0.27)], 0.012).rotate(-90, 0, 0)
        rays.append(rr.rotate(0, 0, 15 + 30 * i).translate(0, 0, 0.04))
    sun = merge(cylinder(0.135, 0.02, 20).rotate(90, 0, 0).translate(0, 0, 0.04), *rays,
                box(0.94, 0.045, 0.09).translate(0, -0.28, 0))
    base.add(place(emb, FRONT, RD - 0.005, 0.73), "white")
    base.add(place(sun, FRONT, RD - 0.005, 0.73), "crimson")

    # ---- pagoda core tiers
    # tier 1
    base.add(cylinder(1.0, 2.3, 40, r_top=0.95).translate(0, D1, 0), "white")
    base.add(band(0.97, 2.85, 0.08, 40), "black")
    for a in (FRONT - 28, FRONT + 28, FRONT + 152, FRONT + 208, FRONT + 90, FRONT - 90):
        base.add(place(box(0.03, 1.3, 0.04), a, 0.975, 2.15), "panel")
    base.add(place(box(0.13, 0.95, 0.06, bevel=0.03), FRONT, 0.97, 2.15), "teal")
    base.add(place(box(0.2, 0.06, 0.06), FRONT, 0.98, 2.67), "black")
    base.add(place(box(0.2, 0.06, 0.06), FRONT, 0.98, 1.63), "black")

    roofs = [
        # r_mid, r_tip, y_eave, y_top, r_top, lift, thick, nu
        (1.42, 1.95, 3.05, 3.62, 0.62, 0.3, 0.11, 48),
        (1.08, 1.5, 4.45, 4.95, 0.44, 0.24, 0.095, 40),
        (0.78, 1.08, 5.68, 6.06, 0.3, 0.18, 0.08, 32),
    ]
    tips_all = []
    for (rm, rt, ye, yt, rtp, lift, th, nu) in roofs:
        top, rim, under, tips = roof(rm, rt, ye, yt, rtp, lift, th, nu, 7)
        base.add(top, "white")
        base.add(rim, "crimson")
        base.add(under, "black")
        base.add(disc(rtp, 24).translate(0, yt, 0), "white")
        base.add(torus(rtp, 0.035, 24, 6).translate(0, yt, 0), "crimson")
        tips_all += tips
    for p in tips_all:
        base.add(sphere(0.075, 8, 4).translate(*p), "teal")

    # tier 2
    base.add(cylinder(0.66, 1.15, 32, r_top=0.62).translate(0, 3.5, 0), "white")
    base.add(band(0.645, 3.8, 0.09, 32), "crimson")
    base.add(place(box(0.1, 0.42, 0.05, bevel=0.025), FRONT, 0.63, 4.17), "teal")
    for a in (FRONT - 35, FRONT + 35, FRONT + 145, FRONT + 215):
        base.add(place(box(0.025, 0.55, 0.03), a, 0.645, 4.15), "panel")
    # tier 3
    base.add(cylinder(0.42, 0.85, 28, r_top=0.4).translate(0, 4.88, 0), "white")
    base.add(place(cylinder(0.075, 0.04, 14).rotate(90, 0, 0), FRONT, 0.41, 5.35), "teal")
    base.add(band(0.415, 5.0, 0.06, 28), "black")

    # needle spire
    base.add(lathe([(0.2, 6.02), (0.17, 6.15), (0.1, 6.9), (0.065, 7.3), (0.0, 7.36)], 16), "white")
    base.add(band(0.158, 6.35, 0.07, 16), "black")
    base.add(band(0.118, 6.8, 0.07, 16), "crimson")
    base.add(sphere(0.19, 18, 12).translate(0, 7.3, 0), "orb")
    base.add(sphere(0.32, 18, 12).translate(0, 7.3, 0), "halo")
    base.add(torus(0.26, 0.02, 24, 6).translate(0, 7.3, 0), "black")
    base.add(cylinder(0.025, 0.55, 8).translate(0, 7.45, 0), "black")
    base.add(sphere(0.055, 10, 6).translate(0, 8.03, 0), "crimson")
    glow = base.node("FX_Glow", (0, 7.3, 0))
    glow.add(box(0.05, 0.05, 0.05).translate(0, 7.3, 0), "orb")

    # ---- turret: rotating collar + two missile pods
    PIV = D1
    tur = base.node("Turret", (0, PIV, 0))
    tur.add(cylinder(1.22, 0.24, 40).translate(0, D1, 0), "black")
    tur.add(band(1.235, D1 + 0.09, 0.05, 40), "crimson")
    POD_R, POD_Y, TILT = 2.0, D1 + 0.22, 12.0
    pw, ph, pd = 0.95, 0.85, 1.0
    zs = (-0.3, 0.0, 0.3)
    mi = 1
    for yaw in POD_YAWS:
        # arm from collar to pod (black), built along local +Z
        tur.add(place(box(0.42, 0.26, 1.0).translate(0, D1 + 0.2, 1.4 - POD_R + 0.2), yaw, POD_R - 0.2), "black")
        parts_w = [box(pw, ph, pd, bevel=0.12).translate(0, ph / 2, 0),
                   box(pw * 0.86, 0.06, pd * 0.86).translate(0, ph + 0.03, 0)]
        parts_r = [box(pw + 0.02, 0.12, pd + 0.02, bevel=0.13).translate(0, 0.22, 0)]
        parts_k = [box(pw + 0.01, 0.025, pd + 0.01, bevel=0.125).translate(0, 0.55, 0)]
        parts_t = [box(0.18, 0.08, 0.04).translate(0.22, 0.4, pd / 2 + 0.005),
                   box(0.04, 0.08, 0.18).translate(-pw / 2 - 0.005, 0.4, 0.25),
                   box(0.04, 0.08, 0.18).translate(pw / 2 + 0.005, 0.4, 0.25)]
        for z in zs:
            parts_k.append(cylinder(0.14, 0.06, 14).translate(0, ph + 0.06, z))
            w, n, tip = missile()
            parts_w.append(w.translate(0, ph + 0.1, z))
            parts_r.append(n.translate(0, ph + 0.1, z))
        for meshes, mat in ((parts_w, "white"), (parts_r, "crimson"), (parts_k, "panel"), (parts_t, "teal")):
            tur.add(place(merge(*meshes), yaw, POD_R, POD_Y, TILT), mat)
        for z in zs:
            mk = place(box(0.05, 0.05, 0.05).translate(0, ph + 0.1 + tip + 0.02, z), yaw, POD_R, POD_Y, TILT)
            c = mk.pos.mean(0)
            fx = tur.node(f"FX_Muzzle{mi:02d}", tuple(float(v) for v in c))
            fx.add(mk, "orb")
            mi += 1
    return m


# --------------------------------------------------------------------------- nanocore

def build_nanocore():
    """Compact Empire transport (~3 m long, nose at +X) carrying the folded spire."""
    m = Model("JapanTenguSpireNanocore")
    materials(m)
    base = m.node("Base")
    L, W = 3.0, 1.5
    H0, H1 = 0.32, 0.78  # hull bottom / top

    # hover pads
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = sx * 1.02, sz * 0.62
            base.add(cylinder(0.3, 0.12, 12, r_top=0.26).translate(x, 0.06, z), "black")
            base.add(cylinder(0.22, 0.05, 12, caps=False).translate(x, 0.01, z), "teal")
            base.add(cylinder(0.31, 0.04, 12, caps=False).translate(x, 0.1, z), "crimson")
            base.add(tube_between((x, 0.18, z), (x * 0.85, H0 + 0.05, z * 0.85), 0.07, 8), "panel")
            base.add(box(0.5, 0.1, 0.32).translate(x, 0.2, z), "white")

    # hull: extruded side profile (wedge nose at +X)
    prof = [(-L / 2, -W / 2), (L / 2 - 0.45, -W / 2), (L / 2, -W / 2 + 0.3), (L / 2, W / 2 - 0.3),
            (L / 2 - 0.45, W / 2), (-L / 2, W / 2)]
    base.add(extrude(prof, H1 - H0).translate(0, H0, 0), "white")
    base.add(extrude([(x * 1.01, z * 1.01) for x, z in prof], 0.08).translate(0, H0 + 0.12, 0), "crimson")
    base.add(extrude([(x * 1.012, z * 1.012) for x, z in prof], 0.05).translate(0, H0, 0), "black")
    # cab with dark canopy at the nose
    base.add(box(0.7, 0.28, 1.0, bevel=0.12).translate(1.0, H1 + 0.14, 0), "white")
    base.add(box(0.72, 0.13, 0.86, bevel=0.1).translate(1.06, H1 + 0.17, 0), "black")
    base.add(box(0.06, 0.1, 0.5).translate(1.5, H0 + 0.3, 0), "teal")
    for sz in (-1, 1):
        base.add(box(0.12, 0.08, 0.1).translate(1.46, H0 + 0.3, sz * 0.48), "teal")
        base.add(box(0.1, 0.08, 0.14).translate(-1.51, H0 + 0.3, sz * 0.55), "crimson")
        base.add(box(1.4, 0.05, 0.04).translate(-0.3, H1 - 0.1, sz * 0.765), "panel")

    # folded tower package: cradle + drum section + folded roof stack + spire lying back
    base.add(box(1.3, 0.12, 1.0, bevel=0.1).translate(-0.4, H1 + 0.06, 0), "black")
    base.add(cylinder(0.55, 0.36, 24).translate(-0.4, H1 + 0.12, 0), "white")
    base.add(band(0.56, H1 + 0.3, 0.06, 24).translate(-0.4, 0, 0), "crimson")
    top, rim, under, tips = roof(0.62, 0.82, H1 + 0.58, H1 + 0.8, 0.18, 0.1, 0.06, 24, 4)
    for mesh, mat in ((top, "white"), (rim, "crimson"), (under, "black")):
        base.add(mesh.translate(-0.4, 0, 0), mat)
    top, rim, under, tips2 = roof(0.42, 0.56, H1 + 0.86, H1 + 1.0, 0.12, 0.08, 0.05, 20, 4)
    for mesh, mat in ((top, "white"), (rim, "crimson"), (under, "black")):
        base.add(mesh.translate(-0.4, 0, 0), mat)
    for p in tips + tips2:
        base.add(box(0.07, 0.07, 0.07).translate(p[0] - 0.4, p[1], p[2]), "teal")
    # needle folded flat along the hull, orb at the tail
    base.add(tube_between((0.45, H1 + 0.34, 0), (-1.45, H1 + 0.34, 0), 0.07, 10), "white")
    base.add(box(0.14, 0.2, 0.14).translate(0.4, H1 + 0.22, 0), "black")
    base.add(sphere(0.13, 10, 6).translate(-1.5, H1 + 0.34, 0), "orb")
    base.add(box(0.2, 0.22, 0.2).translate(-1.5, H1 + 0.13, 0), "black")
    # folded missile pods on the flanks
    for sz in (-1, 1):
        base.add(box(0.75, 0.32, 0.26, bevel=0.06).translate(-0.4, H1 + 0.16, sz * 0.66), "white")
        base.add(box(0.77, 0.06, 0.28).translate(-0.4, H1 + 0.1, sz * 0.66), "crimson")
        for i, x in enumerate((-0.65, -0.4, -0.15)):
            base.add(tube_between((x, H1 + 0.32, sz * 0.66), (x, H1 + 0.4, sz * 0.66), 0.06, 6), "white")
            base.add(cone(0.06, 0.08, 6).translate(x, H1 + 0.4, sz * 0.66), "crimson")
    return m


def main():
    tower = build_tower()
    nano = build_nanocore()
    p1 = tower.save(OUT_DIR / "JapanTenguSpire.glb")
    p2 = nano.save(OUT_DIR / "JapanTenguSpireNanocore.glb")
    for mdl, p in ((tower, p1), (nano, p2)):
        st = mdl.stats()
        allp = np.vstack([mm.pos for n in mdl._walk() for mm in n.prims.values()])
        print(f"{p.name}: {st}  bounds min {allp.min(0).round(2)} max {allp.max(0).round(2)}")


if __name__ == "__main__":
    main()
