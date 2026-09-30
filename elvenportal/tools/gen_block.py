"""The portal block's textures: a round elven moon gate of cream stone rimmed in gold, a leafy vine winding
round it with little gold lights, a green gem set in its top, on a two-step pedestal.

    python3 tools/gen_block.py [--preview]

Block textures (the model, tools/gen_models.py, maps them pixel for pixel: the gate faces north, and its
front and back faces show textures/block/gate_ring.png as drawn):
  gate_ring.png        the ring's front and back (only the ring's pixels show; RING is its shape)
  gate_rim.png         the ring's outer edge (gold)          gate_inner.png   its inner edge (stone)
  gate_vines.png       the vine in front of and behind the ring (cut out; LIGHTS are its gold lights)
  gate_gem.png         the gem in the ring's top, dim        gate_gem_lit.png  lit, while the portal is open
  gate_base.png        the pedestal's tops                   gate_base_side.png  its sides
  gate_swirl_still.png the portal in the item's model (one frame of the swirl)

The block's renderer (client/PortalRenderer.java) draws what moves, from textures/entity/elven_gate/:
  swirl.png    the portal, FRAMES frames of 32x32 one under another: spiral arms turning round a bright heart
  runes.png    a ring of glowing runes, turned slowly round the portal (light: grey is its strength)
  glow.png     a soft round glow, for the lights, the motes and the gem (light, as runes.png)
  crystal.png  the natura crystals floating over the pedestal's corners
"""
import json
import math
import os
import sys

from PIL import Image

from pix import Canvas, ASSETS, ROOT, mix, shade, hexc, rnd2, upscale
from style import G0, G1, G2, G3, G4, G5, Y0, Y1, Y2, Y3, Y4, LR0, LR1, LR2, LR3, LR4, VOUT
import swirl as S

BLOCK = os.path.join(ASSETS, "textures", "block")
ENTITY = os.path.join(ASSETS, "textures", "entity", "elven_gate")

# the ring, in texture pixels (x from the left, y from the top, as seen from the front): round (CX, CY),
# the pixels whose middles lie between RI and RO
CX, CY, RO, RI = 8.0, 7.0, 7.0, 4.9
# the gem's pixels in the ring's top (the model's gem element covers them)
GEM = [(7, 0), (8, 0), (7, 1), (8, 1), (7, 2), (8, 2)]
FRAMES, SWIRL_SIZE = 20, 32
LIGHT = (-0.62, -0.78)


def in_ring(x, y):
    d = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
    return RI <= d <= RO


RING = [[in_ring(x, y) for x in range(16)] for y in range(16)]


def opening(x, y):
    return math.hypot(x + 0.5 - CX, y + 0.5 - CY) < RI


# ---------------------------------------------------------------- the ring

def ring():
    """Cream stone with a gold rim: the outermost pixels gold, the rest stone, each lit from the top-left
    by which way its edge faces; a thin line of gold dots along the stone."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if not RING[y][x]:
                continue
            px, py = x + 0.5 - CX, y + 0.5 - CY
            d = math.hypot(px, py)
            nx, ny = px / d, py / d
            lit = nx * LIGHT[0] + ny * LIGHT[1]          # > 0: the outer edge here faces the light
            outer = any(not RING[yy][xx] and not opening(xx, yy)
                        for (xx, yy) in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                        if 0 <= xx < 16 and 0 <= yy < 16) or x in (0, 15) or y in (0, 15)
            inner = any(opening(xx, yy) for (xx, yy) in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
            if outer:
                c = Y1 if lit > 0.35 else (Y3 if lit < -0.35 else Y2)
            elif inner:
                # the inner edge faces the other way: lit where the outer edge is shaded
                c = LR1 if lit < -0.35 else (LR3 if lit > 0.35 else LR2)
            else:
                c = LR0 if lit > 0.35 else (LR2 if lit < -0.35 else LR1)
            cv.set(x, y, c)
    return cv


def rim():
    """The ring's outer edge: gold, a darker line along its middle."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = Y2 if (x + y) % 5 else Y1
            if y % 4 == 3:
                c = Y3
            cv.set(x, y, c)
    return cv


def inner():
    """The ring's inner edge: warm cream stone with flecks of gold, lit a little green by the portal."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = LR1 if rnd2(x, y, 3) < 0.6 else LR2
            if rnd2(x, y, 4) < 0.12:
                c = Y2
            cv.set(x, y, mix(c, G1, 0.08))
    return cv


# ---------------------------------------------------------------- the vine

# the vine, over the ring's front: s stem, l/m/d leaf light/mid/dark, y a gold light ('.' clear)
VINE = ["................",
        "..............y.",
        "............dls.",
        "..............m.",
        "...............s",
        "..............ls",
        "...............d",
        "................",
        "................",
        "d...............",
        "sl..............",
        "s...............",
        ".sm.............",
        "y.sl............",
        "................",
        "................"]
VINE_PAL = {"s": G4, "l": G1, "m": G2, "d": G3, "y": Y0}


def lights():
    """The gold lights on the vine, in texture pixels (the renderer makes them twinkle)."""
    return [(x, y) for y, row in enumerate(VINE) for x, ch in enumerate(row) if ch == "y"]


def vines():
    cv = Canvas(16, 16)
    cv.sprite(VINE, 0, 0, VINE_PAL)
    return cv


# ---------------------------------------------------------------- the gem and the pedestal

GEM_LAYOUT = {
    # (u, v) regions the model's gem element uses: front 2x3, sides 4x3, top 2x4
    "front": (0, 0, 2, 3), "side": (4, 0, 8, 3), "top": (4, 4, 6, 8)}


def gem(lit):
    cv = Canvas(16, 16)
    light, mid, dark = (G0, G1, G3) if lit else (G2, G3, G5)
    # front: gold above and below, the gem between
    cv.set(0, 0, Y1)
    cv.set(1, 0, Y2)
    cv.set(0, 1, hexc("F4FFE8") if lit else light)
    cv.set(1, 1, mid)
    cv.set(0, 2, Y2)
    cv.set(1, 2, Y3)
    # sides: gold, the gem showing in the middle
    for x in range(4, 10):
        cv.set(x, 0, Y1)
        cv.set(x, 1, light if 6 <= x <= 7 else Y2)
        cv.set(x, 2, Y3)
    # top: gold
    for y in range(4, 10):
        for x in range(4, 6):
            cv.set(x, y, Y1 if x == 4 else Y2)
    return cv


def base():
    """The pedestal's tops: pale stone slabs with a gold line round each step's edge."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = LR1 if rnd2(x, y, 9) < 0.75 else LR2
            if (x + y * 3) % 11 == 0:
                c = LR0
            cv.set(x, y, c)
    for (x1, y1, x2, y2) in ((1, 2, 15, 14), (3, 4, 13, 12)):
        for x in range(x1, x2):
            cv.set(x, y1, Y2)
            cv.set(x, y2 - 1, Y3)
        for y in range(y1, y2):
            cv.set(x1, y, Y2)
            cv.set(x2 - 1, y, Y3)
    return cv


def base_side():
    """The pedestal's sides: the upper step gold (row 14), the lower one stone (row 15)."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = LR2 if rnd2(x, y, 5) < 0.7 else LR3
            if y == 14:
                c = Y2 if x % 4 else Y1
            elif y == 15:
                c = LR2 if x % 5 else LR3
            cv.set(x, y, c)
    return cv


# ---------------------------------------------------------------- what the renderer draws

def swirl_frames():
    """The portal: FRAMES frames of SWIRL_SIZE x SWIRL_SIZE, one under another, the swirl's disc filling each."""
    n = SWIRL_SIZE
    cv = Canvas(n, n * FRAMES)
    for f in range(FRAMES):
        grid = S.swirl_frame(n, n, f / FRAMES, rx=n / 2.0, ry=n / 2.0, motes=5, seed=7, soft_edge=False, dither=False)
        S.put(cv, grid, 0, n * f)
    return cv


def swirl_still():
    """One frame of the swirl, 16x16, for the item's model (the pane shows its middle 10x10)."""
    cv = Canvas(16, 16)
    S.put(cv, S.swirl_frame(16, 16, 0.3, cx=8.0, cy=8.0, rx=5.4, ry=5.4, motes=2, seed=2, soft_edge=False, dither=False), 0, 0)
    return cv


RUNE_GLYPHS = [
    [".#.", "###", ".#."], ["#.#", ".#.", "#.#"], ["##.", "#.#", ".##"], [".##", "#..", ".##"],
    ["#.#", "###", "#.#"], ["###", ".#.", "#.#"], [".#.", "#.#", "###"], ["#..", "###", "..#"]]


def glowing(cv, x, y, v):
    """A pixel of light for an additive texture (the renderer adds its colour and ignores its alpha): grey
    `v` (0..1) stands for white at that strength."""
    g = int(round(255 * v))
    old = cv.get(x, y)
    if old[3] and old[0] >= g:
        return
    cv.set(x, y, (g, g, g), 255)


def runes():
    """Eight runes round a ring with a faint circle through them, as light (the renderer tints it)."""
    n = 32
    cv = Canvas(n, n)
    c = n / 2.0
    for k in range(96):
        a = k / 96.0 * math.tau
        glowing(cv, int(math.floor(c + math.cos(a) * 14.5)), int(math.floor(c + math.sin(a) * 14.5)), 0.28)
    for k, g in enumerate(RUNE_GLYPHS):
        a = (k + 0.5) / len(RUNE_GLYPHS) * math.tau
        gx, gy = int(round(c + math.cos(a) * 12.5)) - 1, int(round(c + math.sin(a) * 12.5)) - 1
        for j, row in enumerate(g):
            for i, ch in enumerate(row):
                if ch == "#":
                    glowing(cv, gx + i, gy + j, 1.0)
    return cv


def glow():
    """A soft round glow, as light."""
    n = 16
    cv = Canvas(n, n)
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - n / 2, y + 0.5 - n / 2) / (n / 2)
            a = max(0.0, 1.0 - d) ** 1.6
            if a > 0.01:
                glowing(cv, x, y, a)
    return cv


def crystal():
    """A natura crystal's facets: bright at the top, deep green below (the renderer uses a row per facet)."""
    cv = Canvas(4, 4)
    for y, c in enumerate((hexc("F4FFE8"), G0, G2, G3)):
        for x in range(4):
            cv.set(x, y, c if x != 3 else mix(c, G4, 0.3))
    return cv


# ---------------------------------------------------------------- output

def save(cv, *path):
    cv.save(os.path.join(*path))


def main():
    save(ring(), BLOCK, "gate_ring.png")
    save(rim(), BLOCK, "gate_rim.png")
    save(inner(), BLOCK, "gate_inner.png")
    save(vines(), BLOCK, "gate_vines.png")
    save(gem(False), BLOCK, "gate_gem.png")
    save(gem(True), BLOCK, "gate_gem_lit.png")
    save(base(), BLOCK, "gate_base.png")
    save(base_side(), BLOCK, "gate_base_side.png")
    save(swirl_still(), BLOCK, "gate_swirl_still.png")
    save(swirl_frames(), ENTITY, "swirl.png")
    save(runes(), ENTITY, "runes.png")
    save(glow(), ENTITY, "glow.png")
    save(crystal(), ENTITY, "crystal.png")
    if "--preview" in sys.argv:
        preview()


def preview():
    """The gate's front as the game shows it, open, 12 screen pixels to a texture pixel and the swirl at its
    own finer grain (build/preview/gate.png)."""
    k = 12
    img = Image.new("RGBA", (16 * k, 16 * k), (120, 170, 230, 255))
    for y in range(12 * k, 16 * k):
        for x in range(16 * k):
            img.putpixel((x, y), (96, 140, 70, 255))
    size = int(round(2 * 5.3 * k))
    sw = swirl_frames().img.crop((0, 0, SWIRL_SIZE, SWIRL_SIZE)).resize((size, size), Image.NEAREST)
    img.alpha_composite(sw, (int(CX * k - size / 2), int(CY * k - size / 2)))
    front = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    front.alpha_composite(ring().img)
    g = gem(True).img
    for (x, y) in GEM:
        front.putpixel((x, y), g.getpixel((x - 7, y)))
    side = base_side().img
    for x in range(1, 15):
        front.putpixel((x, 15), side.getpixel((x, 15)))
    for x in range(3, 13):
        front.putpixel((x, 14), side.getpixel((x, 14)))
    front.alpha_composite(vines().img)
    img.alpha_composite(upscale(front, k))
    out = os.path.join(ROOT, "build", "preview")
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, "gate.png"))
    print(os.path.join(out, "gate.png"), "lights:", lights())


if __name__ == "__main__":
    main()
