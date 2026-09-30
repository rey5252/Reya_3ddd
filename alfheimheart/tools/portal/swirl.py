"""The swirl of the elven portal, as pixel art: spiral arms of green light turning round a bright heart,
with golden and pink motes drawn in along them. Used for the block (tools/gen_block.py) and the GUI
(tools/gen_widgets.py); the frames loop.
"""
import math

from pix import hexc, mix, rnd

# from the deep of the portal to its brightest light
RAMP = [hexc(h) for h in ("0B2A1E", "0F3D26", "17603A", "22884A", "3DB356", "74DB6A", "B6F59A", "EDFFD9")]
GOLD = (hexc("FFE27A"), hexc("FFF6C8"))
PINK = (hexc("FF9AD8"), hexc("FFE0F3"))

ARMS = 3
TWIST = 2.3


def swirl_frame(w, h, t, cx=None, cy=None, rx=None, ry=None, motes=6, seed=0, soft_edge=True, dither=True):
    """One frame (t in [0, 1)) as a w x h grid of RGBA tuples (alpha 0 outside the ellipse (rx, ry) round
    (cx, cy)). The arms turn a third of the way round per loop, so the frames loop seamlessly."""
    cx = w / 2.0 if cx is None else cx
    cy = h / 2.0 if cy is None else cy
    rx = w / 2.0 if rx is None else rx
    ry = h / 2.0 if ry is None else ry
    out = [[(0, 0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            r = math.hypot(u, v)
            if r > 1.0:
                continue
            a = math.atan2(v, u)
            arm = math.sin(ARMS * a + TWIST * math.log(r + 0.06) * ARMS - math.tau * t)
            # bright arms, darker between them, brighter towards the heart, a dark rim at the edge
            level = 0.5 + 0.34 * arm + 0.42 * (1.0 - r) ** 2.2 - 0.3 * max(0.0, r - 0.72) / 0.28
            if r < 0.18:
                level = max(level, 0.95 - r)
            # a little ordered dither between two tones keeps it pixel-like
            if dither:
                level += ((x * 7 + y * 13) % 4 - 1.5) * 0.035
            i = max(0, min(len(RAMP) - 1, int(level * (len(RAMP) - 0.001))))
            alpha = 255
            if soft_edge and r > 0.9:
                alpha = 150 if r < 0.96 else 90
            out[y][x] = RAMP[i] + (alpha,)
    # motes: drawn in along the arms, fading in from the rim and out at the heart
    for k in range(motes):
        p = (t + k / motes + rnd(k, 5 + seed) * 0.13) % 1.0
        rr = 0.95 - 0.85 * p
        base = rnd(k, 11 + seed) * math.tau
        ang = base + math.tau * (p * 0.9) + (TWIST * math.log(rr + 0.06))
        x = int(math.floor(cx + math.cos(ang) * rr * rx))
        y = int(math.floor(cy + math.sin(ang) * rr * ry))
        if 0 <= x < w and 0 <= y < h and out[y][x][3]:
            gold = k % 3 != 2
            col = GOLD if gold else PINK
            out[y][x] = (col[1] if p > 0.4 else col[0]) + (255,)
    return out


def put(cv, grid, x0, y0):
    """Draws a frame grid on a canvas at (x0, y0)."""
    for y, row in enumerate(grid):
        for x, c in enumerate(row):
            if c[3]:
                cv.set(x0 + x, y0 + y, c[:3], c[3])
