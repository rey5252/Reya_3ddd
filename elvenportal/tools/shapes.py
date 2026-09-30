"""Shape shading and little hand-drawn plant sprites for the GUI and item textures.

Shapes are sets of pixels; `shade_shape` paints one with an outline and light from the top-left,
so a leaf keeps the same lighting whichever way it points.
"""
import math

from pix import hexc, mix, shade

# leaf ramp: outline, dark, mid, light, highlight
LEAF = [hexc("173D1C"), hexc("2F6E2A"), hexc("4E9C35"), hexc("7CCB47"), hexc("C4F08A")]
LEAF_YOUNG = [hexc("1E4A1F"), hexc("3F8A31"), hexc("67B842"), hexc("98DD5C"), hexc("DDFBA8")]
PETAL_PINK = [hexc("6E2350"), hexc("C45A97"), hexc("EE8FC2"), hexc("FFC4E2"), hexc("FFF0F8")]
PETAL_WHITE = [hexc("5C4E62"), hexc("C9C0D6"), hexc("E9E4F2"), hexc("FAF8FF"), hexc("FFFFFF")]
PETAL_YELLOW = [hexc("6B4A0E"), hexc("D69A1C"), hexc("F4C842"), hexc("FFE58A"), hexc("FFF8D2")]
MANA = [hexc("0B2F5C"), hexc("1766B4"), hexc("27A6E4"), hexc("5ADCF8"), hexc("E8FFFF")]
PEARL = [hexc("003848"), hexc("00738B"), hexc("00B4C4"), hexc("38FFFA"), hexc("E6FFFD")]


def shade_shape(cv, pixels, ramp, outline=True, light=(-1, -1), rib=None, rib_col=None, soft=False):
    """Paints a set of (x, y) pixels: outline on the border (if wanted), inside lit from `light`.

    ramp = [outline, dark, mid, light, highlight]. `rib` is an optional set of pixels drawn in
    rib_col (a leaf's midrib). With soft=True the outline is the dark tone instead of the darkest.
    """
    pix = set(pixels)
    lx, ly = light
    inner = set()
    border = set()
    for (x, y) in pix:
        if outline and any((x + dx, y + dy) not in pix for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            border.add((x, y))
        else:
            inner.add((x, y))
    for (x, y) in border:
        cv.set(x, y, ramp[1] if soft else ramp[0])
    for (x, y) in inner:
        up = (x + lx, y + ly)
        down = (x - lx, y - ly)
        towards_light = up not in inner
        away = down not in inner
        if towards_light and not away:
            c = ramp[3]
        elif away and not towards_light:
            c = ramp[1]
        else:
            c = ramp[2]
        cv.set(x, y, c)
    # one or two specular pixels near the lit corner
    if inner:
        best = min(inner, key=lambda p: p[0] * -lx * -1 + p[1] * -ly * -1)
        cv.set(best[0], best[1], ramp[4])
    if rib:
        for (x, y) in rib:
            if (x, y) in inner:
                cv.set(x, y, rib_col if rib_col is not None else ramp[1])


def leaf_shape(x, y, angle, length=6.0, width=2.2):
    """A pointed leaf growing from (x, y) at `angle` (radians, 0 = right, pi/2 = down)."""
    pix = set()
    rib = set()
    ca, sa = math.cos(angle), math.sin(angle)
    n = int(length * 3) + 2
    for i in range(n + 1):
        t = i / n
        # half-width: grows fast, tapers to the tip
        hw = width * math.sin(math.pi * min(1.0, t * 1.1)) ** 0.8
        cx, cy = x + 0.5 + ca * length * t, y + 0.5 + sa * length * t
        rib.add((int(math.floor(cx)), int(math.floor(cy))))
        steps = int(hw * 4) + 1
        for s in range(-steps, steps + 1):
            off = hw * s / max(1, steps)
            px, py = cx - sa * off, cy + ca * off
            pix.add((int(math.floor(px)), int(math.floor(py))))
    return pix, rib


def draw_leaf(cv, x, y, angle, length=6.0, width=2.2, ramp=LEAF):
    pix, rib = leaf_shape(x, y, angle, length, width)
    rib = {p for i, p in enumerate(sorted(rib, key=lambda p: (p[0] - x) ** 2 + (p[1] - y) ** 2)) if i < len(rib) - 1}
    shade_shape(cv, pix, ramp, rib=rib if length >= 5 else None, rib_col=ramp[1])


def flower(cv, cx, cy, ramp=PETAL_PINK, heart=PETAL_YELLOW, petals=5, r=2.6, pr=1.9, turn=-math.pi / 2):
    """A little flower: round petals round a heart, outlined, lit from the top-left."""
    pix = set()
    for k in range(petals):
        a = turn + k * math.tau / petals
        px, py = cx + math.cos(a) * r, cy + math.sin(a) * r
        for yy in range(int(py - pr) - 1, int(py + pr) + 2):
            for xx in range(int(px - pr) - 1, int(px + pr) + 2):
                if (xx + 0.5 - px) ** 2 + (yy + 0.5 - py) ** 2 <= pr * pr:
                    pix.add((xx, yy))
    shade_shape(cv, pix, ramp)
    hp = set()
    hr = max(1.0, r * 0.55)
    for yy in range(int(cy - hr) - 1, int(cy + hr) + 2):
        for xx in range(int(cx - hr) - 1, int(cx + hr) + 2):
            if (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= hr * hr:
                hp.add((xx, yy))
    shade_shape(cv, hp, heart, soft=True)


def gem(cv, cx, cy, r, ramp=PEARL):
    """A round gem (mana pearl) with a bright glint."""
    pix = set()
    for yy in range(int(cy - r) - 1, int(cy + r) + 2):
        for xx in range(int(cx - r) - 1, int(cx + r) + 2):
            if (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r:
                pix.add((xx, yy))
    shade_shape(cv, pix, ramp)
    gx, gy = int(math.floor(cx - r * 0.35)), int(math.floor(cy - r * 0.35))
    cv.set(gx, gy, ramp[4])
