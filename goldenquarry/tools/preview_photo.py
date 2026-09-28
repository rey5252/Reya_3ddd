"""Renders the quarry model from the reference photo's own camera, at the photo's pixel scale, and
puts it next to the photo (and a difference image) for checking every pixel.

Run from the goldenquarry folder:  python3 tools/preview_photo.py OUT.png [drill_angle]
The camera (position, direction, focal length) is fitted to where the block's corners are in
reference/block.png. Faces are shaded like the photo's unshaded texture look (the models use
"shade": false), textures are sampled per pixel with 3x3 supersampling.
"""
import json
import math
import sys

from png_io import read_png, write_png

ASSETS = "src/main/resources/assets/goldenquarry"
REF = "reference/block.png"
CROP = (385, 70, 555, 310)

# block corners (model units, block base at y 0, top half above 16) and where the photo shows them
POINTS = [
    ((16, 0, 16), (476, 297.0)), ((16, 16, 16), (476, 186.0)), ((16, 25, 16), (476, 125.3)),
    ((0, 0, 16), (403, 290.0)), ((0, 16, 16), (403, 179.1)), ((0, 25, 16), (403, 121.8)),
    ((16, 0, 0), (537.5, 288.0)), ((16, 16, 0), (537.5, 179.5)), ((16, 25, 0), (537.5, 121.3)),
    ((15, 31, 15), (476, 82.0)), ((1, 31, 15), (404.5, 85.0)), ((15, 31, 1), (536.0, 85.0)),
]


def camera(p):
    cx_w, cy_w, cz_w, yaw, pitch, f, px0, py0 = p
    cyw, syw = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    fwd = (-syw * cp, -sp, -cyw * cp)             # looking back towards the block
    right = (cyw, 0.0, -syw)
    up = (right[1] * fwd[2] - right[2] * fwd[1], right[2] * fwd[0] - right[0] * fwd[2], right[0] * fwd[1] - right[1] * fwd[0])

    def proj(P):
        d = (P[0] - cx_w, P[1] - cy_w, P[2] - cz_w)
        z = d[0] * fwd[0] + d[1] * fwd[1] + d[2] * fwd[2]
        x = d[0] * right[0] + d[1] * right[1] + d[2] * right[2]
        y = d[0] * up[0] + d[1] * up[1] + d[2] * up[2]
        return px0 + f * x / z, py0 - f * y / z, z
    return proj, (cx_w, cy_w, cz_w)


def fit():
    def err(p):
        proj, _ = camera(p)
        e = 0.0
        for P, (u, v) in POINTS:
            x, y, z = proj(P)
            if z <= 1:
                return 1e12
            e += (x - u) ** 2 + (y - v) ** 2
        return e
    # start: 12 blocks away at eye height, 40 degrees round from the south face
    p = [8 + 190 * math.sin(math.radians(40)), 28.0, 8 + 190 * math.cos(math.radians(40)), math.radians(40), math.radians(3), 1150.0, 470.0, 200.0]
    steps = [20.0, 4.0, 20.0, 0.05, 0.02, 100.0, 20.0, 20.0]
    best = err(p)
    for _ in range(4000):
        improved = False
        for k in range(len(p)):
            for sgn in (1, -1):
                q = list(p)
                q[k] += sgn * steps[k]
                e = err(q)
                if e < best:
                    p, best, improved = q, e, True
        if not improved:
            steps = [s * 0.6 for s in steps]
            if max(steps[3:5]) < 1e-7:
                break
    return p, best


_tex = {}


def texture(ref):
    name = ref.split(":")[1]
    if name not in _tex:
        _tex[name] = read_png(f"{ASSETS}/textures/{name}.png")
    return _tex[name]


def load(name):
    m = json.load(open(f"{ASSETS}/models/block/{name}.json"))
    return m["elements"], m["textures"]


def corners(n, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {
        "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[n]


NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def rot_y(P, angle, cx=8.0, cz=8.0):
    c, s = math.cos(angle), math.sin(angle)
    x, z = P[0] - cx, P[2] - cz
    return (cx + x * c + z * s, P[1], cz - x * s + z * c)


def render(parts, proj, cam, W, H, x0, y0, ss=3):
    """parts: (elements, textures, y offset, spin angle). Returns W x H image of the area at (x0, y0)."""
    acc = [[[0, 0, 0, 0] for _ in range(W)] for _ in range(H)]
    for sy in range(ss):
        for sx in range(ss):
            ox, oy = (sx + 0.5) / ss, (sy + 0.5) / ss
            zb = [[1e18] * W for _ in range(H)]
            img = [[None] * W for _ in range(H)]
            for elements, textures, yoff, spin in parts:
                for e in elements:
                    a, b = e["from"], e["to"]
                    for n, f in e["faces"].items():
                        cs = [rot_y((p[0], p[1] + yoff, p[2]), spin) for p in corners(n, a, b)]
                        nrm = rot_y(NORMAL[n], spin, 0, 0)
                        mid = [sum(c[i] for c in cs) / 4 for i in range(3)]
                        if sum(nrm[i] * (cam[i] - mid[i]) for i in range(3)) <= 0:
                            continue
                        ref = f["texture"]
                        while ref.startswith("#"):
                            ref = textures[ref[1:]]
                        tw, th, tpx = texture(ref)
                        uv = f.get("uv")
                        pr = [proj(c) for c in cs]
                        st = [(0, 0), (1, 0), (1, 1), (0, 1)]
                        for tri in ((0, 1, 2), (0, 2, 3)):
                            (X0_, Y0_, Z0), (X1, Y1, Z1), (X2, Y2, Z2) = [pr[i] for i in tri]
                            den = (Y1 - Y2) * (X0_ - X2) + (X2 - X1) * (Y0_ - Y2)
                            if abs(den) < 1e-9:
                                continue
                            ymin, ymax = max(0, int(min(Y0_, Y1, Y2) - y0)), min(H - 1, int(max(Y0_, Y1, Y2) - y0) + 1)
                            xmin, xmax = max(0, int(min(X0_, X1, X2) - x0)), min(W - 1, int(max(X0_, X1, X2) - x0) + 1)
                            for py in range(ymin, ymax + 1):
                                for px in range(xmin, xmax + 1):
                                    X, Y = x0 + px + ox, y0 + py + oy
                                    l0 = ((Y1 - Y2) * (X - X2) + (X2 - X1) * (Y - Y2)) / den
                                    l1 = ((Y2 - Y0_) * (X - X2) + (X0_ - X2) * (Y - Y2)) / den
                                    l2 = 1 - l0 - l1
                                    if l0 < -1e-7 or l1 < -1e-7 or l2 < -1e-7:
                                        continue
                                    iz = l0 / Z0 + l1 / Z1 + l2 / Z2
                                    z = 1 / iz
                                    if z >= zb[py][px]:
                                        continue
                                    s_ = (l0 * st[tri[0]][0] / Z0 + l1 * st[tri[1]][0] / Z1 + l2 * st[tri[2]][0] / Z2) * z
                                    t_ = (l0 * st[tri[0]][1] / Z0 + l1 * st[tri[1]][1] / Z1 + l2 * st[tri[2]][1] / Z2) * z
                                    if uv is None:
                                        continue
                                    u = uv[0] + s_ * (uv[2] - uv[0])
                                    v = uv[1] + t_ * (uv[3] - uv[1])
                                    tx = min(tw - 1, max(0, int(u / 16 * tw)))
                                    ty = min(th - 1, max(0, int(v / 16 * th)))
                                    c = tpx[ty][tx]
                                    if c[3] < 26:
                                        continue
                                    zb[py][px] = z
                                    img[py][px] = c
            for py in range(H):
                for px in range(W):
                    c = img[py][px]
                    if c is not None:
                        for i in range(3):
                            acc[py][px][i] += c[i]
                        acc[py][px][3] += 1
    return acc


def main():
    out = sys.argv[1]
    spin = math.radians(float(sys.argv[2])) if len(sys.argv) > 2 else 0.0
    p, e = fit()
    proj, cam = camera(p)
    print("camera fit: rms %.2f px, eye %.1f blocks high, %.1f blocks away" % (math.sqrt(e / len(POINTS)), p[1] / 16,
          math.hypot(p[0] - 8, p[2] - 8) / 16))
    base = load("golden_quarry_base")
    top = load("golden_quarry_top")
    drill = load("golden_quarry_drill")
    # uv defaults: the models give uv on every face except the base's (full face)
    for els in (base[0],):
        for el in els:
            for n, f in el["faces"].items():
                f.setdefault("uv", [0, 0, 16, 16])
    for el in top[0]:
        for n, f in el["faces"].items():
            if "uv" not in f:
                f["uv"] = [0, 0, 16, 16]
    x0, y0, x1, y1 = CROP
    W, H = x1 - x0, y1 - y0
    wref, href, ref = read_png(REF)
    acc = render([(base[0], base[1], 0, 0.0), (top[0], top[1], 16, 0.0), (drill[0], drill[1], 16, spin)], proj, cam, W, H, x0, y0)
    S = 3
    img = [[(30, 30, 34, 255)] * (W * S * 3 + 20) for _ in range(H * S)]
    for py in range(H):
        for px in range(W):
            rp = ref[y0 + py][x0 + px]
            a = acc[py][px]
            if a[3]:
                n = 9
                cov = a[3] / n
                mc = tuple(int((a[i] / a[3]) * cov + rp[i] * (1 - cov)) for i in range(3))
            else:
                mc = tuple(rp[:3])
            diff = tuple(min(255, abs(mc[i] - rp[i]) * 2) for i in range(3))
            for dy in range(S):
                for dx in range(S):
                    img[py * S + dy][px * S + dx] = tuple(rp[:3]) + (255,)
                    img[py * S + dy][W * S + 10 + px * S + dx] = mc + (255,)
                    img[py * S + dy][2 * W * S + 20 + px * S + dx] = diff + (255,)
    write_png(out, W * S * 3 + 20, H * S, img)


if __name__ == "__main__":
    main()
