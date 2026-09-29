"""The heart generators' textures, read off photos of the originals pixel for pixel (needs numpy,
scipy and Pillow). Run from the quantumsolar folder:  python3 tools/extract_hearts.py

reference/ice_heart_side.png shows the ice heart's front close up, reference/hearts_top.png the two
tops (the same on both); the sun heart's front is the ice heart's on orange (reference/hearts_side.png). Each face is given by its corners
roughly; the 16 x 16 grid is then laid on it exactly (its corners moved about in shrinking steps
while the texels come out more even inside, as tools/fit.py does for the big picture) and each
texel is the median of the photo over its middle. The fronts are lit back up by as much as the game
darkens a front (its white heart shows how much). The two tops are read both and the texel with
the two agreeing best is kept (each photo has a spot of glare or the cursor somewhere).
"""
import numpy as np
from PIL import Image
from scipy.ndimage import map_coordinates

OUT = "src/main/resources/assets/quantumsolar/textures/block"
SUB, INNER = 5, 0.6
# corners: top left, top right, bottom right, bottom left
# the ice heart's front, from reference/ice_heart_side.png (close up, seen a little from above)
ICE_FRONT = [(12.5, 17.5), (400.5, 15), (377.5, 350), (37.5, 360)]
TOPS = [[(55, 28), (431, 27), (432, 407), (45, 408)],
        [(431, 27), (812, 25), (822, 405), (432, 407)]]


def homography(src, dst):
    A = []
    for (x, y), (u, v) in zip(src, dst):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y, -u])
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y, -v])
    _, _, vt = np.linalg.svd(np.array(A, float))
    return vt[-1].reshape(3, 3)


def texels(img, q):
    H = homography([(0, 0), (1, 0), (1, 1), (0, 1)], q)
    t = (np.arange(SUB) + 0.5) / SUB * INNER + (1 - INNER) / 2
    u = (np.arange(16)[:, None] + t[None, :]).reshape(-1) / 16.0
    uu, vv = np.meshgrid(u, u)
    P = H @ np.stack([uu.ravel(), vv.ravel(), np.ones(uu.size)])
    x, y = P[0] / P[2], P[1] / P[2]
    ch = [map_coordinates(img[:, :, c], [y, x], order=1, mode="nearest") for c in range(3)]
    return np.stack(ch, axis=-1).reshape(16, SUB, 16, SUB, 3)


def cost(img, q):
    s = texels(img, q)
    return s.var(axis=(1, 3)).sum() / (s.mean(axis=(1, 3)).var(axis=(0, 1)).sum() + 1.0)


def fit(img, q0, reach=14.0):
    q = [list(map(float, p)) for p in q0]
    best = cost(img, q)
    for size in (4.0, 2.0, 1.0, 0.5):
        improved = True
        while improved:
            improved = False
            for k in range(4):
                for dx, dy in ((size, 0), (-size, 0), (0, size), (0, -size)):
                    t = [p[:] for p in q]
                    t[k][0] += dx
                    t[k][1] += dy
                    if abs(t[k][0] - q0[k][0]) > reach or abs(t[k][1] - q0[k][1]) > reach:
                        continue
                    c = cost(img, t)
                    if c < best - 1e-9:
                        best, q, improved = c, t, True
    return q


def read(img, q):
    s = texels(img, q)
    return np.median(s.transpose(0, 2, 1, 3, 4).reshape(16, 16, -1, 3), axis=2)


def heart_mask(a):
    """The heart on a front: its light and coloured pixels inside the rim."""
    m = np.zeros((16, 16), dtype=bool)
    for y in range(1, 15):
        for x in range(1, 15):
            c = a[y, x]
            m[y, x] = c.max() > 175 or (c.max() - c.min()) > 60
    return m


def sun_front(ice):
    """The sun heart's front is the ice heart's with its dark ground orange (reference/hearts_side.png
    shows it so): each ground pixel's lightness put onto the orange's own shades."""
    out = ice.copy()
    heart = heart_mask(ice)
    lum = ice.sum(axis=2)
    ground = [(y, x) for y in range(1, 15) for x in range(1, 15) if not heart[y, x]]
    lo, hi = min(lum[p] for p in ground), max(lum[p] for p in ground)
    dark, mid, light = np.array([150, 70, 32]), np.array([214, 116, 52]), np.array([240, 186, 112])
    for (y, x) in ground:
        t = (lum[y, x] - lo) / max(1.0, hi - lo)
        out[y, x] = dark + (mid - dark) * min(1, t * 2) if t < 0.5 else mid + (light - mid) * (t - 0.5) * 2
    return out


def main():
    front = np.asarray(Image.open("reference/ice_heart_side.png").convert("RGB")).astype(float)
    top = np.asarray(Image.open("reference/hearts_top.png").convert("RGB")).astype(float)
    ice = read(front, fit(front, ICE_FRONT, reach=5.0))
    # the game draws a front darker than it is (the face turned from the light): its white
    # heart shows the factor, and all of it is lit back up by that
    ice = np.clip(ice * (250.0 / np.percentile(ice.max(axis=2), 97)), 0, 255)
    # right inside the rim the originals have their ground; the photo gives that ring a lighter
    # stripe (the rim and the ground run together at the photo's slant): it takes the ground's colour
    # from the ring inside it
    for y in range(1, 15):
        for x in range(1, 15):
            if max(abs(x - 7.5), abs(y - 7.5)) == 6.5:
                ix, iy = min(max(x, 2), 13), min(max(y, 2), 13)
                ice[y, x] = ice[iy, ix] if ice[iy, ix].max() < 90 else np.array([28, 28, 31])
    faces = {"ice_heart": ice, "sun_heart": sun_front(ice)}
    tops = [read(top, fit(top, q)) for q in TOPS]
    # where the two tops disagree, keep the one nearer the texel's neighbours (glare or the cursor
    # is on one of them only)
    a, b = tops
    t = (a + b) / 2
    for y in range(16):
        for x in range(16):
            if np.abs(a[y, x] - b[y, x]).sum() > 60:
                around = [t[j, i] for j, i in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)) if 0 <= j < 16 and 0 <= i < 16]
                m = np.mean(around, axis=0)
                t[y, x] = a[y, x] if np.abs(a[y, x] - m).sum() < np.abs(b[y, x] - m).sum() else b[y, x]
    for name, s in faces.items():
        Image.fromarray(s.round().clip(0, 255).astype(np.uint8)).save(f"{OUT}/core_generator_{name}_side.png")
        Image.fromarray(t.round().clip(0, 255).astype(np.uint8)).save(f"{OUT}/core_generator_{name}_top.png")


if __name__ == "__main__":
    main()
