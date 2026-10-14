"""AlliedAtomDisintegrator («Нуль») — concept: an Allied turret that takes targets apart atom by atom.

A white-and-blue Allied tower on a 6 x 6 deck. Instead of a gun it carries a giant model of an
atom: a glowing nucleus inside three tilted electron orbits, held by three curved chrome forks
above a focusing lens. The beam pulls the target apart into drifting glowing particles.

Two variants (concept renders, not part of the mod build):
    AlliedAtomDisintegrator_idle.glb   the tower
    AlliedAtomDisintegrator_fire.glb   tower + beam + a tank dissolving into atoms

Run:  python3 -I models/concepts/AlliedAtomDisintegrator.py
"""
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from meshkit import (Model, box, crystal, cylinder, disc, foundation_plate,  # noqa: E402
                     lathe, merge, radial, rgb, sphere, torus, tube_between)

OUT_DIR = HERE.parent / "glb" / "concepts"
ATOM = (0.0, 7.2, 0.0)      # centre of the atom (nucleus)
LENS = (0.0, 5.55, 0.0)

DECK = {
    "plate": {"texture": "allied/plate_steel", "tile": 6.0, "offset": 0.5},
    "plate_in": {"texture": "allied/plate_steel", "tile": 6.0},
    "white": "allied/panel_white", "blue": "allied/panel_blue",
    "chrome": "allied/chrome", "dark": "allied/dark_metal",
}


def blade(p0, p1, p2, w0, w1, t=0.12, seg=10):
    """Curved fork: quadratic bezier p0-p1-p2, width w0 -> w1, thickness t (a chain of boxes)."""
    parts = []
    pts = [tuple((1 - s) ** 2 * np.array(p0) + 2 * (1 - s) * s * np.array(p1) + s ** 2 * np.array(p2))
           for s in np.linspace(0, 1, seg + 1)]
    for i in range(seg):
        a, b = np.array(pts[i]), np.array(pts[i + 1])
        w = w0 + (w1 - w0) * (i + 0.5) / seg
        parts.append(tube_between(a, b, w / 2, 6))
    return merge(*parts)


def orbit(R, r, tilt_x, tilt_z):
    return torus(R, r, 64, 8).scale(1.0, 1.0, 0.62).rotate(tilt_x, 0, tilt_z).translate(*ATOM)


def build(variant):
    m = Model(f"AlliedAtomDisintegrator_{variant}")
    m.material("plate", rgb("#c9d3df"), metallic=0.2, roughness=0.6)
    m.material("plate_in", rgb("#aeb9c7"), metallic=0.2, roughness=0.6)
    m.material("white", rgb("#eef3fb"), metallic=0.15, roughness=0.35)
    m.material("blue", rgb("#2f6fd6"), metallic=0.3, roughness=0.35)
    m.material("chrome", rgb("#cfd8e3"), metallic=0.95, roughness=0.15)
    m.material("dark", rgb("#2b3340"), metallic=0.7, roughness=0.4)
    m.material("cyan", rgb("#7fe3ff"), roughness=0.2, emissive=rgb("#5fdcff", 1.6))
    m.material("nucleus", rgb("#e9fbff"), roughness=0.1, emissive=rgb("#bff4ff", 2.2))
    m.material("violet", rgb("#b25cff"), roughness=0.2, emissive=rgb("#a64dff", 1.6))
    m.material("halo", rgb("#7fe3ff"), roughness=0.1, emissive=rgb("#5fdcff", 1.0), alpha=0.25)

    base = m.node("Base")
    # --- 6 x 6 Allied deck with a blue ring and corner lamps
    base.add(foundation_plate(size=5.98), "plate")
    base.add(cylinder(2.6, 0.05, 48).translate(0, 0.35, 0), "plate_in")
    base.add(torus(2.62, 0.05, 48, 6).translate(0, 0.38, 0), "blue")
    for sx, sz in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
        base.add(cylinder(0.16, 0.25, 12).translate(sx * 2.55, 0.35, sz * 2.55), "chrome")
        base.add(sphere(0.12, 10, 6).translate(sx * 2.55, 0.66, sz * 2.55), "cyan")
    # --- stepped pedestal and slim column
    base.add(lathe([(0.0, 0.35), (2.2, 0.35), (2.2, 0.75), (1.9, 0.9), (1.9, 1.35), (1.5, 1.5), (0.0, 1.5)], 8,
                   start_deg=22.5, end_deg=382.5, smooth=False), "white")
    base.add(lathe([(1.92, 0.95), (1.92, 1.25)], 8, start_deg=22.5, end_deg=382.5, smooth=False), "blue")
    base.add(lathe([(0.0, 1.5), (1.2, 1.5), (0.95, 3.2), (0.8, 4.6), (1.05, 4.9), (0.0, 4.9)], 8,
                   start_deg=22.5, end_deg=382.5, smooth=False), "white")
    for k in range(4):   # glowing power rails up the column
        a = math.radians(45 + k * 90)
        base.add(tube_between((1.12 * math.cos(a), 1.6, 1.12 * math.sin(a)),
                              (0.82 * math.cos(a), 4.55, 0.82 * math.sin(a)), 0.05, 6), "cyan")
    base.add(cylinder(0.98, 0.25, 32).translate(0, 3.0, 0), "blue")

    # --- turret: the focusing head + forks + the atom
    tur = base.node("Turret", (0.0, 4.9, 0.0))
    tur.add(cylinder(1.15, 0.3, 32).translate(0, 4.9, 0), "chrome")
    tur.add(lathe([(1.0, 5.2), (0.75, 5.5), (0.0, 5.55)], 32), "white")
    tur.add(torus(0.62, 0.07, 32, 6).translate(*LENS), "blue")
    tur.add(disc(0.55, 32).translate(LENS[0], LENS[1] + 0.02, LENS[2]), "cyan")
    # three curved forks rising from the head and curling around the atom
    for k in range(3):
        a = math.radians(90 + k * 120)
        c, s = math.cos(a), math.sin(a)
        p0 = (0.95 * c, 5.2, 0.95 * s)
        p1 = (2.6 * c, 6.4, 2.6 * s)
        p2 = (1.45 * c, 8.7, 1.45 * s)
        tur.add(blade(p0, p1, p2, 0.34, 0.14), "chrome")
        tur.add(blade((p0[0] * 1.02, p0[1] + 0.1, p0[2] * 1.02), (p1[0] * 1.03, p1[1], p1[2] * 1.03),
                      (p2[0] * 1.03, p2[1], p2[2] * 1.03), 0.12, 0.06), "blue")
        tur.add(sphere(0.16, 12, 8).translate(*p2), "cyan")
    # the atom: nucleus (cluster of protons/neutrons), halo, three tilted electron orbits + electrons
    nuc = []
    rng = np.random.RandomState(7)
    for i in range(14):
        v = rng.normal(size=3)
        v = v / np.linalg.norm(v) * 0.32 * rng.uniform(0.3, 1.0)
        nuc.append(sphere(0.24, 12, 8).translate(ATOM[0] + v[0], ATOM[1] + v[1], ATOM[2] + v[2]))
    atom = tur.node("Emitter", ATOM)
    atom.add(merge(*nuc[::2]), "nucleus")
    atom.add(merge(*nuc[1::2]), "violet")
    atom.add(sphere(0.95, 24, 16).translate(*ATOM), "halo")
    for k, (tx, tz) in enumerate(((0, 0), (60, 0), (-60, 0))):
        rot = (tx, k * 60, 0)
        ring = torus(1.55, 0.045, 64, 6).scale(1.0, 1.0, 0.6).rotate(*rot).translate(*ATOM)
        atom.add(ring, "cyan")
        e_ang = math.radians(40 + k * 110)
        ep = np.array([1.55 * math.cos(e_ang), 0.0, 0.6 * 1.55 * math.sin(e_ang)])
        e = sphere(0.14, 12, 8).translate(*ep).rotate(*rot).translate(*ATOM)
        atom.add(e, "violet")
    fx = atom.node("FX_Muzzle01", LENS)
    fx.add(box(0.05, 0.05, 0.05).translate(*LENS), "cyan")

    if variant == "fire":
        m.material("tank", rgb("#5b5f4a"), metallic=0.45, roughness=0.55)
        m.material("tank_dk", rgb("#2c2e25"), metallic=0.5, roughness=0.6)
        m.material("tank_red", rgb("#c62b1f"), metallic=0.3, roughness=0.5)
        m.material("beam", rgb("#d9f7ff"), roughness=0.1, emissive=rgb("#9eeaff", 2.4), alpha=0.85)
        m.material("beam_halo", rgb("#b25cff"), roughness=0.1, emissive=rgb("#a64dff", 1.2), alpha=0.3)
        fxn = base.node("FX_Scene", (0, 0, 0))
        # target: an enemy tank 9 m away, its front half breaking up into glowing particles
        T = np.array([9.5, 0.0, 0.0])
        cut = 0.15                                    # x (tank space) where the hull is already gone
        fxn.add(box(3.0, 0.7, 2.0, bevel=0.3).translate(T[0] - 0.75 - 0.3, 0.7, 0)
                .scale(1, 1, 1), "tank")
        fxn.add(box(1.5, 0.7, 2.0, bevel=0.2).translate(T[0] - 1.5, 0.7, 0), "tank")
        for sz in (-1, 1):
            fxn.add(box(2.6, 0.55, 0.45, bevel=0.1).translate(T[0] - 1.0, 0.35, sz * 1.0), "tank_dk")
        fxn.add(cylinder(0.75, 0.45, 20).translate(T[0] - 1.6, 1.05, 0), "tank")
        fxn.add(tube_between((T[0] - 1.6, 1.3, 0), (T[0] - 3.4, 1.38, 0), 0.1, 10), "tank_dk")
        fxn.add(box(0.05, 0.25, 0.25).translate(T[0] - 2.7, 0.9, 1.01), "tank_red")
        # the dissolving front: particles (atoms) drifting up and towards the beam, glowing more the higher
        rng = np.random.RandomState(42)
        low, mid, high = [], [], []
        for i in range(260):
            x = rng.uniform(cut - 0.2, 1.8)
            y = rng.uniform(0.1, 1.4)
            z = rng.uniform(-1.0, 1.0)
            lift = (x - cut) * rng.uniform(0.6, 2.6) + rng.uniform(0, 0.6)
            p = (T[0] - 0.75 + x - lift * 0.35, y + lift, z * (1 + lift * 0.15))
            sz_ = rng.uniform(0.06, 0.16) * (1.0 - min(lift, 3.5) / 5.0)
            cube = box(sz_, sz_, sz_).rotate(*rng.uniform(0, 90, 3)).translate(*p)
            (low if lift < 1.0 else mid if lift < 2.2 else high).append(cube)
        fxn.add(merge(*low), "tank")
        fxn.add(merge(*mid), "cyan")
        fxn.add(merge(*high), "violet")
        # the beam: from the lens to the dissolving hull, with a violet sheath and impact sphere
        hit = (T[0] - 0.4, 0.9, 0.0)
        fxn.add(tube_between(LENS, hit, 0.12, 16), "beam")
        fxn.add(tube_between(LENS, hit, 0.32, 16), "beam_halo")
        fxn.add(sphere(0.55, 20, 12).translate(*hit), "beam_halo")
        fxn.add(sphere(0.3, 16, 10).translate(*hit), "nucleus")
        # small spiral of atoms being pulled up along the beam
        for i in range(24):
            t = i / 24
            p = np.array(hit) + (np.array(LENS) - np.array(hit)) * t * 0.55
            ang = t * 18
            p = p + np.array([0, math.cos(ang) * 0.4, math.sin(ang) * 0.4])
            fxn.add(sphere(0.06, 6, 4).translate(*p), "violet" if i % 2 else "cyan")
    return m


if __name__ == "__main__":
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for v in ("idle", "fire"):
        mdl = build(v)
        print(mdl.save(OUT_DIR / f"AlliedAtomDisintegrator_{v}.glb", textures=True, bindings=DECK), mdl.stats())
