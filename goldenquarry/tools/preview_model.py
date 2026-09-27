"""Software preview of the quarry model (both halves stacked), seen corner-on from slightly above
like reference photo 1. Only for checking the look; Minecraft does the real rendering.

Run from the goldenquarry folder:  python3 tools/preview_model.py OUT.png [yaw] [pitch] [scale]
"""
import json
import math
import sys

from png_io import read_png, write_png

ASSETS = "src/main/resources/assets/goldenquarry"
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}
_tex_cache = {}


def texture(ref):
    name = ref.split(":")[1]
    if name not in _tex_cache:
        w, h, px = read_png(f"{ASSETS}/textures/{name}.png")
        _tex_cache[name] = (w, h, px)
    return _tex_cache[name]


def load_model(name):
    m = json.load(open(f"{ASSETS}/models/block/{name}.json"))
    if m.get("parent") == "minecraft:block/cube_all":
        all_tex = m["textures"]["all"]
        f = {n: {"texture": "#all"} for n in ["north", "south", "west", "east", "up", "down"]}
        return [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": f}], {"all": all_tex}
    return m["elements"], m["textures"]


def default_uv(n, a, b):
    if n == "north":
        return [16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]]
    if n == "south":
        return [a[0], 16 - b[1], b[0], 16 - a[1]]
    if n == "west":
        return [a[2], 16 - b[1], b[2], 16 - a[1]]
    if n == "east":
        return [16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]]
    if n == "up":
        return [a[0], a[2], b[0], b[2]]
    return [a[0], 16 - b[2], b[0], 16 - a[2]]


def corners(n, a, b):
    """top-left, top-right, bottom-right, bottom-left in texture orientation."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    if n == "north":
        return [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)]
    if n == "south":
        return [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)]
    if n == "west":
        return [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)]
    if n == "east":
        return [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)]
    if n == "up":
        return [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)]
    return [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)]


def render(parts, W, H, yaw, pitch, scale, cx, cy, bg=(0, 0, 0, 0)):
    img = [[bg] * W for _ in range(H)]
    zb = [[-1e9] * W for _ in range(H)]
    cyaw, syaw = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p[0] - 8, p[1] - 16, p[2] - 8
        xr = x * cyaw - z * syaw
        zr = x * syaw + z * cyaw
        yr = y * cp - zr * sp
        depth = y * sp + zr * cp
        return cx + xr * scale, cy - yr * scale, depth

    # faces pointing away from the camera are culled, as in Minecraft
    view = (syaw * cp, sp, cyaw * cp)
    normal = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

    for elements, textures, yoff in parts:
        for e in elements:
            a = [e["from"][0], e["from"][1], e["from"][2]]
            b = [e["to"][0], e["to"][1], e["to"][2]]
            for n, f in e["faces"].items():
                if sum(normal[n][i] * view[i] for i in range(3)) <= 0:
                    continue
                ref = f["texture"]
                while ref.startswith("#"):
                    ref = textures[ref[1:]]
                tw, th, tpx = texture(ref)
                uv = f.get("uv") or default_uv(n, a, b)
                cs = [project((p[0], p[1] + yoff, p[2])) for p in corners(n, a, b)]
                st = [(0, 0), (1, 0), (1, 1), (0, 1)]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    (x0, y0, d0), (x1, y1, d1), (x2, y2, d2) = [cs[i] for i in tri]
                    den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
                    if abs(den) < 1e-9:
                        continue
                    for py in range(max(0, int(min(y0, y1, y2))), min(H, int(max(y0, y1, y2)) + 1)):
                        for px_ in range(max(0, int(min(x0, x1, x2))), min(W, int(max(x0, x1, x2)) + 1)):
                            X, Y = px_ + 0.5, py + 0.5
                            l0 = ((y1 - y2) * (X - x2) + (x2 - x1) * (Y - y2)) / den
                            l1 = ((y2 - y0) * (X - x2) + (x0 - x2) * (Y - y2)) / den
                            l2 = 1 - l0 - l1
                            if l0 < -1e-6 or l1 < -1e-6 or l2 < -1e-6:
                                continue
                            d = l0 * d0 + l1 * d1 + l2 * d2
                            if d <= zb[py][px_]:
                                continue
                            s = l0 * st[tri[0]][0] + l1 * st[tri[1]][0] + l2 * st[tri[2]][0]
                            t = l0 * st[tri[0]][1] + l1 * st[tri[1]][1] + l2 * st[tri[2]][1]
                            u = uv[0] + s * (uv[2] - uv[0])
                            v = uv[1] + t * (uv[3] - uv[1])
                            tx = min(tw - 1, max(0, int(u / 16 * tw)))
                            ty = min(th - 1, max(0, int(v / 16 * th)))
                            c = tpx[ty][tx]
                            if c[3] < 26:
                                continue
                            k = SHADE[n]
                            zb[py][px_] = d
                            img[py][px_] = (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)
    return img


def main():
    out = sys.argv[1]
    yaw = float(sys.argv[2]) if len(sys.argv) > 2 else 45
    pitch = float(sys.argv[3]) if len(sys.argv) > 3 else 14
    scale = float(sys.argv[4]) if len(sys.argv) > 4 else 6
    base = load_model("golden_quarry_base")
    top = load_model("golden_quarry_top")
    W, H = int(34 * scale), int(40 * scale)
    img = render([(base[0], base[1], 0), (top[0], top[1], 16)], W, H, yaw, pitch, scale, W / 2, H / 2 + 1 * scale,
                 bg=(252, 236, 160, 255))
    write_png(out, W, H, img)


if __name__ == "__main__":
    main()
