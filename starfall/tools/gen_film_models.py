"""The film's machines as meshes (run from the mod folder): SS-01's railgun and the accelerator ring at Jupiter.

Each mesh is a list of quads written as assets/starfall/film/<name>.bin:
    b"SFM1", int32 quad count, then 4 vertices per quad, each
    3 x float32 position, 2 x float32 surface position (for the panel patterns), 4 bytes colour (the last one is
    the material, see film_hull.fsh), 3 signed bytes normal, 1 byte padding. Big-endian.
"""
import math
import os
import random
import struct

OUT = "src/main/resources/assets/starfall/film"

# materials (the colour's alpha byte, read by film_hull.fsh)
PAINT, METAL, RADIATOR, WINDOWS, LAMP = 255, 209, 173, 133, 89


def CHARGE(t):
    """Lights up once the charge passes t (0..1)."""
    return max(0, min(63, int(t * 63.0)))


# ---------------------------------------------------------------- vectors

def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    l = math.sqrt(dot(a, a))
    return (a[0] / l, a[1] / l, a[2] / l) if l > 1e-9 else (0.0, 1.0, 0.0)


class Frame:
    """An origin and three axes: points given in the frame's own coordinates are placed into the model."""

    def __init__(self, o=(0, 0, 0), x=(1, 0, 0), y=(0, 1, 0), z=(0, 0, 1)):
        self.o, self.x, self.y, self.z = o, norm(x), norm(y), norm(z)

    def p(self, x, y, z):
        return add(self.o, add(mul(self.x, x), add(mul(self.y, y), mul(self.z, z))))

    def d(self, x, y, z):
        return norm(add(mul(self.x, x), add(mul(self.y, y), mul(self.z, z))))

    @staticmethod
    def along(o, direction, up=(0, 1, 0)):
        z = norm(direction)
        if abs(dot(z, norm(up))) > 0.98:
            up = (1, 0, 0)
        x = norm(cross(up, z))
        y = cross(z, x)
        return Frame(o, x, y, z)

    def rotated_z(self, a):
        c, s = math.cos(a), math.sin(a)
        return Frame(self.o, add(mul(self.x, c), mul(self.y, s)), add(mul(self.x, -s), mul(self.y, c)), self.z)

    def rotated_x(self, a):
        c, s = math.cos(a), math.sin(a)
        return Frame(self.o, self.x, add(mul(self.y, c), mul(self.z, s)), add(mul(self.y, -s), mul(self.z, c)))

    def rotated_y(self, a):
        c, s = math.cos(a), math.sin(a)
        return Frame(self.o, add(mul(self.x, c), mul(self.z, -s)), self.y, add(mul(self.x, s), mul(self.z, c)))

    def moved(self, x, y, z):
        return Frame(self.p(x, y, z), self.x, self.y, self.z)


WORLD = Frame()


class Mesh:
    def __init__(self):
        self.quads = []

    def quad(self, ps, ns, uvs, rgb, mat):
        self.quads.append((ps, ns, uvs, rgb, mat))

    def flat(self, a, b, c, d, rgb, mat, uv=None):
        n = norm(cross(sub(b, a), sub(d, a)))
        if uv is None:
            # planar coordinates in model units along the quad's own edges
            e1 = norm(sub(b, a))
            e2 = cross(n, e1)
            uv = [(dot(sub(p, a), e1), dot(sub(p, a), e2)) for p in (a, b, c, d)]
        self.quad((a, b, c, d), (n, n, n, n), uv, rgb, mat)

    def write(self, name):
        os.makedirs(OUT, exist_ok=True)
        out = bytearray(b"SFM1")
        out += struct.pack(">i", len(self.quads))
        for ps, ns, uvs, rgb, mat in self.quads:
            for k in range(4):
                p, n, uv = ps[k], ns[k], uvs[k]
                out += struct.pack(">fffff", p[0], p[1], p[2], uv[0], uv[1])
                out += bytes((rgb[0], rgb[1], rgb[2], mat))
                out += struct.pack(">bbbx", *(max(-127, min(127, int(round(c * 127)))) for c in n))
        with open(os.path.join(OUT, name + ".bin"), "wb") as f:
            f.write(out)
        print(f"{name}: {len(self.quads)} quads, {len(out) // 1024} KB")


# ---------------------------------------------------------------- shapes

def box(m, f, x0, y0, z0, x1, y1, z1, rgb, mat, faces="xXyYzZ", mats=None):
    """A box between two corners of frame f. faces picks which sides to make (x = -x side, X = +x side, ...)."""
    P = lambda x, y, z: f.p(x, y, z)
    sides = {
        "x": (P(x0, y0, z1), P(x0, y0, z0), P(x0, y1, z0), P(x0, y1, z1)),
        "X": (P(x1, y0, z0), P(x1, y0, z1), P(x1, y1, z1), P(x1, y1, z0)),
        "y": (P(x0, y0, z0), P(x0, y0, z1), P(x1, y0, z1), P(x1, y0, z0)),
        "Y": (P(x0, y1, z1), P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1)),
        "z": (P(x1, y0, z0), P(x0, y0, z0), P(x0, y1, z0), P(x1, y1, z0)),
        "Z": (P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)),
    }
    for k in faces:
        a, b, c, d = sides[k]
        mm = mats.get(k, mat) if mats else mat
        m.flat(a, b, c, d, rgb, mm)


def prism(m, f, profile, z0, z1, rgb, mat, smooth=False, caps=True, mats=None, scale1=1.0):
    """Extrudes a closed 2D outline (counter-clockwise, in f's xy) along f's z. scale1 shrinks the far end."""
    n = len(profile)
    perim = 0.0
    for i in range(n):
        a, b = profile[i], profile[(i + 1) % n]
        p0, p1 = f.p(a[0], a[1], z0), f.p(b[0], b[1], z0)
        q0, q1 = f.p(a[0] * scale1, a[1] * scale1, z1), f.p(b[0] * scale1, b[1] * scale1, z1)
        seg = math.hypot(b[0] - a[0], b[1] - a[1])
        uv = [(z0, perim), (z0, perim + seg), (z1, perim + seg), (z1, perim)]
        mm = mats[i] if mats else mat
        if smooth:
            na = f.d(a[0], a[1], 0)
            nb = f.d(b[0], b[1], 0)
            m.quad((p0, p1, q1, q0), (na, nb, nb, na), uv, rgb, mm)
        else:
            m.flat(p0, p1, q1, q0, rgb, mm, uv)
        perim += seg
    if caps:
        cz0, cz1 = f.p(0, 0, z0), f.p(0, 0, z1)
        for i in range(n):
            a, b = profile[i], profile[(i + 1) % n]
            m.flat(cz0, f.p(b[0], b[1], z0), f.p(a[0], a[1], z0), cz0, rgb, mat,
                   [(0, 0), (b[0], b[1]), (a[0], a[1]), (0, 0)])
            m.flat(cz1, f.p(a[0] * scale1, a[1] * scale1, z1), f.p(b[0] * scale1, b[1] * scale1, z1), cz1, rgb, mat,
                   [(0, 0), (a[0], a[1]), (b[0], b[1]), (0, 0)])


def circle(r, seg, phase=0.0):
    return [(math.cos(phase + 2 * math.pi * i / seg) * r, math.sin(phase + 2 * math.pi * i / seg) * r) for i in range(seg)]


def octagon(w, h, c):
    """A box outline w x h with its corners cut by c."""
    x, y = w / 2, h / 2
    return [(x, -y + c), (x, y - c), (x - c, y), (-x + c, y), (-x, y - c), (-x, -y + c), (-x + c, -y), (x - c, -y)]


def cylinder(m, f, r, z0, z1, rgb, mat, seg=16, caps=True, scale1=1.0):
    prism(m, f, circle(r, seg), z0, z1, rgb, mat, smooth=True, caps=caps, scale1=scale1)


def torus(m, f, R, r, rgb, mat, segu=48, segv=10, mat_of=None):
    """A ring around f's z axis in its xy plane. mat_of(i) can give each of the segu segments its own material."""
    for i in range(segu):
        u0, u1 = 2 * math.pi * i / segu, 2 * math.pi * (i + 1) / segu
        mm = mat_of(i) if mat_of else mat
        col = rgb(i) if callable(rgb) else rgb
        for j in range(segv):
            v0, v1 = 2 * math.pi * j / segv, 2 * math.pi * (j + 1) / segv
            pts, nrm, uvs = [], [], []
            for (u, v) in ((u0, v0), (u1, v0), (u1, v1), (u0, v1)):
                cu, su, cv, sv = math.cos(u), math.sin(u), math.cos(v), math.sin(v)
                pts.append(f.p((R + r * cv) * cu, (R + r * cv) * su, r * sv))
                nrm.append(f.d(cv * cu, cv * su, sv))
                uvs.append((u * R, v * r))
            m.quad(tuple(pts), tuple(nrm), tuple(uvs), col, mm)


def rod(m, a, b, r, rgb, mat, seg=6, caps=False):
    d = sub(b, a)
    l = math.sqrt(dot(d, d))
    if l < 1e-6:
        return
    cylinder(m, Frame.along(a, d), r, 0, l, rgb, mat, seg=seg, caps=caps)


def beam(m, a, b, w, h, rgb, mat, up=(0, 1, 0)):
    d = sub(b, a)
    l = math.sqrt(dot(d, d))
    if l < 1e-6:
        return
    f = Frame.along(a, d, up)
    box(m, f, -w / 2, -h / 2, 0, w / 2, h / 2, l, rgb, mat)


# ---------------------------------------------------------------- SS-01

GREY = (86, 90, 98)
DARK = (52, 54, 60)
STEEL = (120, 124, 132)
LIGHT = (196, 198, 204)
WHITE = (232, 232, 236)
RED = (255, 46, 40)
RED_HOT = (255, 120, 100)
WARM = (255, 236, 200)
PANEL = (78, 88, 112)


def railgun():
    rnd = random.Random(1997)
    m = Mesh()
    hull = octagon(10.0, 8.0, 2.0)
    # the hull in seven sections with collars between them
    for s in range(7):
        z0, z1 = s * 12.0, s * 12.0 + 12.0
        prism(m, WORLD, hull, z0 + 0.4, z1 - 0.4, GREY if s % 2 else (80, 84, 92), PAINT, caps=(s == 0))
        prism(m, WORLD, octagon(10.8, 8.8, 2.2), z0 - 0.4, z0 + 0.4, DARK, METAL, caps=True)
        # angled armour plates on both sides, with a red trim along their lower edge
        for side in (-1, 1):
            up = (side * math.sin(0.35), math.cos(0.35), 0)
            plate = Frame((side * 5.15, -1.2, 0), (0, 0, 1), up, cross((0, 0, 1), up))
            px0, px1 = z0 + 1.2, z1 - 1.2
            box(m, plate, px0, -1.6, -0.3, px1, 3.0, 0.9, (98, 102, 110), PAINT)
            box(m, plate, px0 + 1.0, -1.2, 0.9, px1 - 1.0, 2.4, 1.5, (88, 92, 100), PAINT)
            box(m, plate, px0 + 0.2, -1.8, 0.1, px1 - 0.2, -1.6, 0.5, RED, LAMP)
            box(m, plate, px0 + 1.2, 2.4, 1.38, px1 - 1.2, 2.55, 1.52, RED, LAMP)
            # a module with lit windows on some sections
            if s in (1, 4):
                box(m, WORLD, side * 5.0 if side > 0 else -7.6, 1.4, z0 + 2.5, 7.6 if side > 0 else -5.0, 3.6, z0 + 9.5,
                    (70, 74, 80), PAINT, mats={"X" if side > 0 else "x": WINDOWS})
        # greebles on top, between the girder posts
        for _ in range(9):
            gx = rnd.uniform(-2.2, 2.2)
            gz = rnd.uniform(z0 + 1.0, z1 - 1.5)
            sx, sy, sz = rnd.uniform(0.4, 1.4), rnd.uniform(0.25, 0.9), rnd.uniform(0.5, 2.2)
            box(m, WORLD, gx - sx / 2, 4.0, gz, gx + sx / 2, 4.0 + sy, gz + sz, rnd.choice((DARK, GREY, STEEL)),
                rnd.choice((PAINT, METAL)), faces="xXYzZ")
        # under the hull: a keel with boxes
        box(m, WORLD, -2.0, -5.2, z0 + 0.6, 2.0, -4.0, z1 - 0.6, DARK, METAL, faces="xXyzZ")
        for _ in range(4):
            gz = rnd.uniform(z0 + 1.0, z1 - 3.0)
            box(m, WORLD, -3.2, -4.7, gz, 3.2, -4.0, gz + rnd.uniform(0.8, 2.0), STEEL, METAL, faces="xXyzZ")
    # the girder along the top
    for side in (-1, 1):
        beam(m, (side * 3.0, 7.0, 3.0), (side * 3.0, 7.0, 82.0), 0.6, 0.6, DARK, METAL)
        beam(m, (side * 3.0, 4.6, 3.0), (side * 3.0, 4.6, 82.0), 0.4, 0.4, DARK, METAL)
        for k in range(14):
            z = 3.0 + k * 6.0
            beam(m, (side * 3.0, 4.0, z), (side * 3.0, 7.0, z), 0.45, 0.45, STEEL, METAL)
            if k < 13:
                beam(m, (side * 3.0, 4.6, z), (side * 3.0, 7.0, z + 6.0), 0.25, 0.25, DARK, METAL)
                beam(m, (side * 3.0, 7.0, z), (side * 3.0, 4.6, z + 6.0), 0.25, 0.25, DARK, METAL)
    for k in range(14):
        z = 3.0 + k * 6.0
        beam(m, (-3.0, 7.0, z), (3.0, 7.0, z), 0.35, 0.35, DARK, METAL, up=(0, 0, 1))
    # long pipes along both sides, held by clamps
    for side in (-1, 1):
        for (y, r) in ((1.9, 0.5), (0.6, 0.36), (-2.2, 0.42)):
            x = side * (6.7 + r)
            cylinder(m, Frame((x, y, -3.0)), r, 0, 90.0, STEEL, METAL, seg=12, caps=True)
            for k in range(12):
                cz = 2.0 + k * 7.5
                cylinder(m, Frame((x, y, cz)), r + 0.18, 0, 0.7, DARK, METAL, seg=12, caps=True)
    # the barrel's base: the hull narrows towards the rails
    prism(m, WORLD, hull, 84.0, 92.0, (80, 84, 92), PAINT, caps=False, scale1=0.72)
    prism(m, WORLD, octagon(7.6, 6.2, 1.6), 92.0, 93.0, DARK, METAL, caps=True)
    # six rails out to the muzzle; the strip facing the bore lights from the back to the front with the charge
    for k in range(6):
        a = math.pi / 6 + k * math.pi / 3
        rf = Frame((0, 0, 0)).rotated_z(a)
        box(m, rf, 2.6, -0.55, 92.0, 3.7, 0.55, 126.0, (62, 64, 70), METAL)
        for j in range(10):
            z0 = 93.0 + j * 3.3
            box(m, rf, 2.5, -0.22, z0, 2.6, 0.22, z0 + 3.0, RED, CHARGE(0.08 + j * 0.085), faces="x")
        # a red line along the outside, always lit, like the hull's trims
        box(m, rf, 3.7, -0.08, 94.0, 3.78, 0.08, 124.0, RED, LAMP, faces="X")
    # coils around the rails, each lighting at its turn
    for i in range(7):
        z = 96.0 + i * 4.6
        torus(m, Frame((0, 0, z)), 4.4, 0.55, DARK, METAL, segu=36, segv=8)
        torus(m, Frame((0, 0, z)), 3.95, 0.16, RED, CHARGE(0.1 + i * 0.12), segu=36, segv=6)
    # the muzzle ring, white and red in turns, with struts to the rails
    torus(m, Frame((0, 0, 127.0)), 5.0, 1.0, lambda i: WHITE if i % 4 else RED, PAINT, segu=48, segv=10,
          mat_of=lambda i: PAINT if i % 4 else LAMP)
    torus(m, Frame((0, 0, 127.0)), 4.0, 0.22, RED, CHARGE(0.85), segu=48, segv=6)
    for k in range(6):
        a = math.pi / 6 + k * math.pi / 3
        beam(m, (math.cos(a) * 3.15, math.sin(a) * 3.15, 125.6), (math.cos(a) * 4.6, math.sin(a) * 4.6, 127.0), 0.7, 0.7,
             LIGHT, PAINT, up=(0, 0, 1))
    # the reactor drum at the back, with ports around it and a hub on its end
    drum = Frame((0, 0, 0))
    cylinder(m, drum, 6.4, -13.0, -0.6, (92, 94, 100), PAINT, seg=32)
    for zc in (-12.6, -6.8, -1.0):
        cylinder(m, Frame((0, 0, zc)), 6.8, 0, 0.6, DARK, METAL, seg=32)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        for zc in (-10.0, -3.8):
            port = Frame.along((math.cos(a) * 6.3, math.sin(a) * 6.3, zc), (math.cos(a), math.sin(a), 0), (0, 0, 1))
            cylinder(m, port, 1.0, 0, 0.6, STEEL, METAL, seg=14)
            cylinder(m, port, 0.65, 0.6, 0.62, (20, 20, 24), METAL, seg=14)
    cylinder(m, Frame((0, 0, -15.5)), 2.6, 0, 2.6, STEEL, METAL, seg=24)
    torus(m, Frame((0, 0, -15.6)), 2.0, 0.25, (255, 120, 90), LAMP, segu=24, segv=6)
    # a tower of rooms on top of the drum, and the mast over it
    box(m, WORLD, -2.6, 6.0, -11.0, 2.6, 13.0, -3.0, (84, 86, 92), PAINT, mats={"x": WINDOWS, "X": WINDOWS, "z": WINDOWS})
    box(m, WORLD, -1.8, 13.0, -10.0, 1.8, 14.6, -4.0, DARK, METAL)
    rod(m, (0, 14.6, -7.0), (0, 24.0, -7.0), 0.18, STEEL, METAL)
    rod(m, (0, 18.0, -7.0), (2.6, 18.0, -7.0), 0.1, STEEL, METAL)
    # booms out to the radiator panels, two on each side
    for side in (-1, 1):
        rod(m, (side * 6.0, 0, -7.0), (side * 46.0, 0, -7.0), 0.45, STEEL, METAL, seg=8)
        for (x0, x1) in ((9.0, 26.0), (28.0, 45.0)):
            tilt = 0.28 * side
            pf = Frame((0, 0, -7.0), (1, 0, 0), (0, math.cos(tilt), math.sin(tilt)), (0, -math.sin(tilt), math.cos(tilt)))
            box(m, pf, side * x0 if side > 0 else -x1, -0.12, -6.0, x1 if side > 0 else -x0, 0.12, 6.0, PANEL, RADIATOR)
            # frames around each panel
            lo, hi = (x0, x1) if side > 0 else (-x1, -x0)
            box(m, pf, lo, -0.2, -6.2, hi, 0.2, -5.9, DARK, METAL)
            box(m, pf, lo, -0.2, 5.9, hi, 0.2, 6.2, DARK, METAL)
            box(m, pf, lo - 0.1, -0.2, -6.2, lo + 0.2, 0.2, 6.2, DARK, METAL)
            box(m, pf, hi - 0.2, -0.2, -6.2, hi + 0.1, 0.2, 6.2, DARK, METAL)
    # a dish on one side of the drum
    dish = Frame.along((-6.6, 3.5, -5.0), (-1, 0.6, -0.2))
    cylinder(m, dish, 0.4, 0, 1.4, STEEL, METAL, seg=10)
    cylinder(m, dish.moved(0, 0, 1.4), 0.6, 0, 0.9, LIGHT, PAINT, seg=20, scale1=4.2)
    return m


# ---------------------------------------------------------------- the accelerator at Jupiter

def accelerator():
    """A ring of track around Jupiter (radius 100 in the film): two rails on ties, with stations along it."""
    m = Mesh()
    R = 170.0
    seg = 360
    # two rails, a little above and below the ring's plane
    for y in (-1.2, 1.2):
        torus(m, Frame((0, 0, 0), (1, 0, 0), (0, 0, 1), (0, 1, 0)).moved(0, 0, y), R, 0.45, (70, 72, 78), METAL, segu=seg, segv=6)
    # ties between them
    for i in range(seg // 2):
        a = 2 * math.pi * i / (seg // 2)
        c, s = math.cos(a), math.sin(a)
        f = Frame((c * R, 0, s * R), (c, 0, s), (0, 1, 0), (-s, 0, c))
        box(m, f, -1.1, -1.3, -0.25, 1.1, 1.3, 0.25, (54, 56, 62), METAL)
    # stations: a block over the track with a coil around it and lit rooms on its sides
    for k in range(24):
        a = 2 * math.pi * k / 24
        c, s = math.cos(a), math.sin(a)
        f = Frame((c * R, 0, s * R), (c, 0, s), (0, 1, 0), (-s, 0, c))
        big = k == 0
        w = 5.0 if big else 3.6
        box(m, f, -w, 2.0, -2.4, w, 4.6 if big else 3.8, 2.4, (96, 98, 106), PAINT, mats={"x": WINDOWS, "X": WINDOWS})
        box(m, f, -w, -4.6 if big else -3.8, -2.4, w, -2.0, 2.4, (96, 98, 106), PAINT)
        box(m, f, w - 0.6, -2.0, -2.4, w, 2.0, 2.4, (80, 82, 90), PAINT)
        box(m, f, -w, -2.0, -2.4, -w + 0.6, 2.0, 2.4, (80, 82, 90), PAINT)
        coil = Frame(f.o, f.x, f.y, f.z)
        torus(m, coil, 3.2, 0.5, DARK, METAL, segu=24, segv=6)
        torus(m, coil, 2.7, 0.16, (255, 150, 70), LAMP, segu=24, segv=5)
        rod(m, f.p(0, 4.6 if big else 3.8, 0), f.p(0, 9.0 if big else 6.5, 0), 0.12, STEEL, METAL)
        if big:
            # the launch gate: a short barrel along the track
            cylinder(m, Frame(f.p(0, 0, 2.4), f.x, f.y, f.z), 2.2, 0, 9.0, (90, 92, 98), PAINT, seg=16)
            torus(m, Frame(f.p(0, 0, 11.4), f.x, f.y, f.z), 2.6, 0.5, (255, 140, 60), LAMP, segu=24, segv=6)
    return m


if __name__ == "__main__":
    railgun().write("railgun")
    accelerator().write("accelerator")
