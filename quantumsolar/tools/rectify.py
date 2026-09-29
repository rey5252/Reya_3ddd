import sys, os
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from strips import STRIPS

REF = "reference/panels.jpg"


def homography(src, dst):
    A = []
    for (x, y), (u, v) in zip(src, dst):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y, -u])
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y, -v])
    _, _, vt = np.linalg.svd(np.array(A, float))
    return vt[-1].reshape(3, 3)


def strip_h(s):
    n = s["n"]
    return homography([(0, 1), (n, 1), (n, 0), (0, 0)], s["q"])


def to_img(H, x, y):
    p = H @ [x, y, 1]
    return p[0] / p[2], p[1] / p[2]


def face(img, H, i, size=16, sub=4):
    """Block i of a strip at size x size: each texel the mean of sub x sub samples over its middle."""
    a = np.asarray(img).astype(float)
    out = np.zeros((size, size, 3))
    for ty in range(size):
        for tx in range(size):
            acc = []
            for sy in range(sub):
                for sx in range(sub):
                    u = i + (tx + (sx + 0.5) / sub * 0.6 + 0.2) / size
                    v = (ty + (sy + 0.5) / sub * 0.6 + 0.2) / size
                    x, y = to_img(H, u, v)
                    xi, yi = int(round(x)), int(round(y))
                    if 0 <= xi < a.shape[1] and 0 <= yi < a.shape[0]:
                        acc.append(a[yi, xi])
            out[ty, tx] = np.mean(acc, axis=0) if acc else 0
    return out.astype(np.uint8)


def overlay(path, scale=2):
    img = Image.open(REF).convert("RGB")
    big = img.resize((img.width * scale, img.height * scale), Image.LANCZOS)
    d = ImageDraw.Draw(big)
    for name, s in STRIPS.items():
        H = strip_h(s)
        for i in range(s["n"] + 1):
            a, b = to_img(H, i, 0), to_img(H, i, 1)
            d.line([(a[0] * scale, a[1] * scale), (b[0] * scale, b[1] * scale)], fill=(255, 0, 0))
        for v in (0, 1):
            a, b = to_img(H, 0, v), to_img(H, s["n"], v)
            d.line([(a[0] * scale, a[1] * scale), (b[0] * scale, b[1] * scale)], fill=(255, 255, 0) if v == 0 else (0, 255, 255))
        x, y = to_img(H, 0, 0.5)
        d.text((x * scale - 20, y * scale), name, fill=(255, 255, 255))
    big.save(path)


def all_faces(size=32, sub=2):
    img = Image.open(REF).convert("RGB")
    out = []
    for name, st in STRIPS.items():
        H = strip_h(st)
        for i in range(st["n"]):
            out.append((f"{name}{i}" if st["n"] > 1 else name, face(img, H, i, size=size, sub=sub)))
    return out


def sheet(path, scale=3, per_row=8):
    faces = all_faces()
    cell = 32 * scale + 14
    rows = (len(faces) + per_row - 1) // per_row
    out = Image.new("RGB", (per_row * cell, rows * cell), (40, 40, 40))
    d = ImageDraw.Draw(out)
    for k, (name, f) in enumerate(faces):
        x, y = k % per_row * cell, k // per_row * cell
        out.paste(Image.fromarray(f).resize((32 * scale, 32 * scale), Image.NEAREST), (x + 2, y + 12))
        d.text((x + 2, y), name, fill=(255, 255, 0))
    out.save(path)


if __name__ == "__main__":
    overlay(sys.argv[1])
    sheet(sys.argv[2])
