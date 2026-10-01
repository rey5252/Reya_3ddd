"""A look at the machines' block models before a game run: each model drawn from above at an angle (as an
item in the inventory is), its faces textured and shaded as the game shades them.

    python3 tools/machines/preview_model.py      writes build/preview/models.png
"""
import json
import math
import os

from PIL import Image

from pix import ROOT, ASSETS

OUT_DIR = os.path.join(ROOT, "build", "preview")
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def load_model(name):
    path = os.path.join(ASSETS, "models", "block", name + ".json")
    data = json.load(open(path))
    textures = data["textures"]
    return textures, data["elements"]


def texture(textures, ref):
    while ref.startswith("#"):
        ref = textures[ref[1:]]
    ns, path = ref.split(":")
    return Image.open(os.path.join(ROOT, "src", "main", "resources", "assets", ns, "textures", path + ".png")).convert("RGBA")


def face_quad(frm, to, side):
    """The face's corners (model pixels) in the order matching the texture's (u1,v1), (u2,v1), (u2,v2), (u1,v2),
    and its automatic uv."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    if side == "up":
        return [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)], (x1, z1, x2, z2)
    if side == "down":
        return [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)], (x1, 16 - z2, x2, 16 - z1)
    if side == "north":
        return [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)], (16 - x2, 16 - y2, 16 - x1, 16 - y1)
    if side == "south":
        return [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)], (x1, 16 - y2, x2, 16 - y1)
    if side == "west":
        return [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)], (z1, 16 - y2, z2, 16 - y1)
    return [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)], (16 - z2, 16 - y2, 16 - z1, 16 - y1)


NORMALS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def project(p, scale, yaw=math.radians(225), pitch=math.radians(30)):
    """An orthographic view like the inventory's: turned 225 degrees, tilted 30 degrees."""
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    cx = x * math.cos(yaw) - z * math.sin(yaw)
    cz = x * math.sin(yaw) + z * math.cos(yaw)
    cy = y * math.cos(pitch) - cz * math.sin(pitch)
    depth = y * math.sin(pitch) + cz * math.cos(pitch)
    return cx * scale, -cy * scale, depth


def render(name, size=256):
    textures, elements = load_model(name)
    scale = size / 26.0
    img = Image.new("RGBA", (size, size), (52, 60, 56, 255))
    px = img.load()
    zbuf = [[-1e9] * size for _ in range(size)]
    view = (math.sin(math.radians(225)) * 0, 0, 0)
    for el in elements:
        for side, face in el["faces"].items():
            corners, auto_uv = face_quad(el["from"], el["to"], side)
            uv = face.get("uv", auto_uv)
            tex = texture(textures, face["texture"])
            tw, th = tex.size
            tp = tex.load()
            pts = [project(c, scale) for c in corners]
            # back-face cull: the projected winding
            (ax, ay, _), (bx, by, _), (cx_, cy_, _) = pts[0], pts[1], pts[2]
            if (bx - ax) * (cy_ - ay) - (by - ay) * (cx_ - ax) <= 0:
                continue
            xs = [p[0] for p in pts]
            ys = [p[1] for p in pts]
            for sy in range(int(min(ys) + size / 2) - 1, int(max(ys) + size / 2) + 2):
                for sx in range(int(min(xs) + size / 2) - 1, int(max(xs) + size / 2) + 2):
                    if not (0 <= sx < size and 0 <= sy < size):
                        continue
                    qx, qy = sx + 0.5 - size / 2, sy + 0.5 - size / 2
                    # solve q = p0 + s (p1 - p0) + t (p3 - p0)
                    e1 = (pts[1][0] - pts[0][0], pts[1][1] - pts[0][1])
                    e2 = (pts[3][0] - pts[0][0], pts[3][1] - pts[0][1])
                    det = e1[0] * e2[1] - e1[1] * e2[0]
                    if abs(det) < 1e-9:
                        continue
                    dx, dy = qx - pts[0][0], qy - pts[0][1]
                    s = (dx * e2[1] - dy * e2[0]) / det
                    t = (e1[0] * dy - e1[1] * dx) / det
                    if not (0 <= s < 1 and 0 <= t < 1):
                        continue
                    depth = pts[0][2] + s * (pts[1][2] - pts[0][2]) + t * (pts[3][2] - pts[0][2])
                    if depth < zbuf[sy][sx]:
                        continue
                    u = uv[0] + s * (uv[2] - uv[0])
                    v = uv[1] + t * (uv[3] - uv[1])
                    c = tp[min(tw - 1, int(u * tw / 16.0)), min(th - 1, int(v * th / 16.0))]
                    if c[3] < 128:
                        continue
                    k = SHADE[side]
                    zbuf[sy][sx] = depth
                    px[sx, sy] = (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)
    return img


def main():
    names = ["rune_altar", "terra_plate", "mana_infuser"]
    out = Image.new("RGBA", (256 * len(names), 256))
    for i, n in enumerate(names):
        out.paste(render(n), (i * 256, 0))
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "models.png")
    out.save(path)
    print(path)


if __name__ == "__main__":
    main()
