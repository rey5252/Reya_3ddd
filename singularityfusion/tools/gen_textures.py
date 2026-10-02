"""Draws all of Singularity Fusion's textures.

    python3 tools/gen_textures.py

Writes into src/main/resources/assets/singularityfusion/textures (and the mod's logo): the singularity's pieces
(effect/), the pylon's skin (entity/), the blocks and their glowing overlays (block/, some animated), the items
(item/, two animated). The core's screen and JEI's page are gen_gui.py's.
"""
import json
import math
import os

from art import (BLACK, EDGE_HI, GOLD, GOLD_DK, GOLD_HI, PURPLE, PURPLE_DK, PURPLE_HI, VIOLET_WHITE, VOID_0, VOID_1, VOID_2,
                 VOID_3, VOID_4, VOID_5, WHITE, Canvas, Noise, add, clamp, fbm, mix, ramp, rng, shade, smooth, wrapping_octaves)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
TEX = os.path.join(RES, "assets", "singularityfusion", "textures")


def out(*parts):
    path = os.path.join(TEX, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def animate(path, frametime, interpolate=True):
    with open(path + ".mcmeta", "w", encoding="utf-8") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": interpolate}}, f, indent=2)
        f.write("\n")


def light(c, k):
    """A colour of light at a strength: premultiplied, for the additive textures (black is nothing)."""
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


# =================================================================== the singularity (effect/)

DISK_HOT = [(0.0, (70, 18, 6)), (0.18, (150, 52, 14)), (0.36, (226, 116, 38)), (0.54, (255, 172, 74)), (0.7, (255, 210, 130)),
            (0.84, (255, 236, 190)), (1.0, (255, 252, 244))]


def disk():
    """The accretion disk, 512 round by 64 out: u round it (tiling), v from its inner edge (white-hot) out (dim orange).
    Thin streaks of hotter and cooler gas run round it, like the grooves of a record; broad clumps brighten it here and
    there."""
    w, h = 512, 64
    c = Canvas(w, h, (0, 0, 0, 255))
    streaks = wrapping_octaves(11, 3, None, 4)
    fibres = wrapping_octaves(17, 7, None, 3)
    clumps = wrapping_octaves(23, 4, None, 3)
    for y in range(h):
        v = (y + 0.5) / h
        profile = smooth(0.0, 0.03, v) * (0.34 + 0.66 * math.exp(-v * 2.3)) * (1.0 - smooth(0.74, 1.0, v))
        for x in range(w):
            u = (x + 0.5) / w
            # streaks: barely changing round the disk, quickly across it
            s = fbm(streaks, u * 3, v * 34, 4, 2.0, 0.55)
            f = fbm(fibres, u * 7, v * 70, 3)
            k = fbm(clumps, u * 4, v * 2.5, 3)
            gas = (0.5 + 0.9 * smooth(0.25, 0.85, s)) * (0.8 + 0.35 * f) * (0.78 + 0.42 * k)
            t = clamp((1.0 - v) ** 0.85 + 0.12 * (s - 0.5) + 0.06 * (k - 0.5))
            c.set(x, y, light(ramp(DISK_HOT, t), clamp(profile * gas * 1.25)))
    c.save(out("effect", "disk.png"))


def ring():
    """The photon ring, 256 round by 32 across: a thin line, sharp on its inside, softer outward."""
    w, h = 256, 32
    c = Canvas(w, h, (0, 0, 0, 255))
    n = wrapping_octaves(5, 9, None, 3)
    for y in range(h):
        v = (y + 0.5) / h
        p = math.exp(-((v - 0.24) / 0.06) ** 2) if v < 0.24 else math.exp(-((v - 0.24) / 0.22) ** 2)
        for x in range(w):
            u = (x + 0.5) / w
            k = p * (0.8 + 0.25 * fbm(n, u * 9, v * 2, 3))
            c.set(x, y, light(mix((255, 236, 200), WHITE, p), clamp(k)))
    c.save(out("effect", "ring.png"))


def glow():
    """A soft round glow, white (tinted by whoever draws it)."""
    s = 64
    c = Canvas(s, s, (0, 0, 0, 255))
    for y in range(s):
        for x in range(s):
            r = math.hypot(x + 0.5 - s / 2, y + 0.5 - s / 2) / (s / 2)
            k = math.exp(-r * r * 4.2) * (1.0 - smooth(0.82, 1.0, r))
            c.set(x, y, light(WHITE, k))
    c.save(out("effect", "glow.png"))


def bolt():
    """Lightning's cross-section: a bright core with a soft edge (u along the bolt, v across)."""
    s = 16
    c = Canvas(s, s, (0, 0, 0, 255))
    for y in range(s):
        v = (y + 0.5) / s
        k = min(1.0, math.exp(-((v - 0.5) / 0.15) ** 2) + 0.35 * math.exp(-((v - 0.5) / 0.32) ** 2))
        for x in range(s):
            c.set(x, y, light(WHITE, k))
    c.save(out("effect", "bolt.png"))


def beam():
    """A beam of energy, 64 along by 32 across: a bright core, a halo, pulses running along it (tiling along)."""
    w, h = 64, 32
    c = Canvas(w, h, (0, 0, 0, 255))
    n = wrapping_octaves(41, 4, None, 3)
    for y in range(h):
        v = (y + 0.5) / h
        core = math.exp(-((v - 0.5) / 0.1) ** 2)
        halo = math.exp(-((v - 0.5) / 0.3) ** 2)
        for x in range(w):
            u = (x + 0.5) / w
            pulse = (0.5 + 0.5 * math.cos(2 * math.pi * u * 2)) ** 6
            wob = fbm(n, u * 4, v * 3, 3)
            k = core * (0.7 + 0.3 * pulse) + halo * (0.22 + 0.35 * pulse) * (0.7 + 0.6 * wob)
            c.set(x, y, light(mix((214, 196, 255), WHITE, core), clamp(k)))
    c.save(out("effect", "beam.png"))


def jet():
    """A polar jet's light, 64 along by 32 across: knots running along it (tiling along), violet-blue round a white core."""
    w, h = 64, 32
    c = Canvas(w, h, (0, 0, 0, 255))
    n = wrapping_octaves(53, 6, None, 3)
    for y in range(h):
        v = (y + 0.5) / h
        across = math.exp(-((v - 0.5) / 0.17) ** 2)
        for x in range(w):
            u = (x + 0.5) / w
            knots = 0.45 + 0.75 * fbm(n, u * 6, v * 1.5, 3) ** 2
            k = across * knots
            c.set(x, y, light(mix((150, 120, 255), (245, 240, 255), across ** 3), clamp(k * 1.3)))
    c.save(out("effect", "jet.png"))


def spark():
    """A spark: a bright point with four short rays."""
    s = 16
    c = Canvas(s, s, (0, 0, 0, 255))
    m = s / 2
    for y in range(s):
        for x in range(s):
            dx, dy = x + 0.5 - m, y + 0.5 - m
            r = math.hypot(dx, dy)
            k = math.exp(-(r / 2.0) ** 2) + 0.55 * math.exp(-abs(dx) / 2.6) * math.exp(-(dy / 0.6) ** 2) \
                + 0.55 * math.exp(-abs(dy) / 2.6) * math.exp(-(dx / 0.6) ** 2)
            k *= 1.0 - smooth(0.8, 1.0, r / m)
            c.set(x, y, light(WHITE, clamp(k)))
    c.save(out("effect", "spark.png"))


def shock():
    """The shock wave's ring, 256 round by 32 across: v 0 its sharp front, a glow trailing behind it."""
    w, h = 256, 32
    c = Canvas(w, h, (0, 0, 0, 255))
    n = wrapping_octaves(61, 12, None, 3)
    for y in range(h):
        v = (y + 0.5) / h
        p = smooth(0.0, 0.07, v) * (0.18 + 0.82 * math.exp(-max(0.0, v - 0.07) / 0.1)) * (1.0 - smooth(0.75, 1.0, v))
        for x in range(w):
            u = (x + 0.5) / w
            k = p * (0.7 + 0.45 * fbm(n, u * 12, v * 3, 3))
            c.set(x, y, light(mix((200, 160, 255), (250, 240, 255), p), clamp(k)))
    c.save(out("effect", "shock.png"))


def shadow():
    """The shadow is drawn black; any opaque texture will do."""
    c = Canvas(16, 16, (0, 0, 0, 255))
    c.save(out("effect", "shadow.png"))


# =================================================================== the pylon's skin (entity/)

# the same pixel corners as GravitonPylonRenderer.REGIONS
PYLON_REGIONS = {"shaft_side": (0, 0, 16, 32), "shaft_end": (16, 0, 32, 16), "collar_side": (16, 16, 32, 24), "socket_side": (16, 24, 32, 28),
                 "collar_end": (32, 0, 48, 16), "neck_side": (32, 16, 40, 32), "crystal": (48, 0, 64, 16), "ring_face": (0, 32, 64, 40),
                 "ring_wall": (0, 40, 64, 44), "ring_inner": (0, 44, 64, 48)}


def metal(c, x0, y0, w, h, seed, lo=VOID_2, hi=VOID_4):
    """Dark brushed metal: fine streaks along it."""
    n = Noise(seed)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            k = 0.55 * n.at(x * 0.9, y * 0.25) + 0.45 * n.at(x * 2.3 + 17, y * 0.6)
            c.set(x, y, mix(lo, hi, k))


def pylon():
    tex = Canvas(64, 64)
    glo = Canvas(64, 64, (0, 0, 0, 255))
    r = PYLON_REGIONS

    # the shaft's sides: a panel with a glyph channel down its middle
    x0, y0, x1, y1 = r["shaft_side"]
    metal(tex, x0, y0, 16, 32, 3)
    tex.vline(x0, y0, y1 - 1, VOID_1)
    tex.vline(x1 - 1, y0, y1 - 1, VOID_0)
    tex.vline(x0 + 1, y0, y1 - 1, VOID_5)
    tex.hline(x0, x1 - 1, y0, EDGE_HI)
    tex.hline(x0, x1 - 1, y0 + 1, VOID_4)
    tex.hline(x0, x1 - 1, y1 - 1, VOID_0)
    tex.hline(x0, x1 - 1, y1 - 2, VOID_1)
    for (bx, by) in ((2, 3), (13, 3), (2, 28), (13, 28)):
        tex.set(x0 + bx, y0 + by, GOLD)
        tex.set(x0 + bx, y0 + by + 1, GOLD_DK)
    tex.rect(x0 + 6, y0 + 3, 4, 26, VOID_0)
    tex.vline(x0 + 5, y0 + 3, y0 + 28, VOID_1)
    tex.vline(x0 + 10, y0 + 3, y0 + 28, VOID_5)
    glyphs = [((0, 1, 1, 0), (1, 1, 1, 1)), ((1, 0, 0, 1), (0, 1, 1, 0)), ((1, 1, 0, 0), (0, 0, 1, 1)), ((0, 1, 0, 1), (1, 0, 1, 0))]
    for i, gy in enumerate(range(y0 + 5, y0 + 27, 5)):
        rows = glyphs[i % len(glyphs)]
        for j, row in enumerate(rows):
            for k, on in enumerate(row):
                if on:
                    tex.set(x0 + 6 + k, gy + j, PURPLE_DK)
                    glo.set(x0 + 6 + k, gy + j, light(VIOLET_WHITE, 0.95))
        glo.set(x0 + 7, gy + 3, light(PURPLE, 0.6))
        glo.set(x0 + 8, gy + 3, light(PURPLE, 0.6))
    for y in range(y0 + 3, y0 + 29):
        for x in (x0 + 6, x0 + 9):
            if glo.get(x, y)[0] == 0:
                glo.set(x, y, light(PURPLE, 0.28))

    # its ends: a cap with a diamond
    x0, y0, x1, y1 = r["shaft_end"]
    metal(tex, x0, y0, 16, 16, 5)
    tex.frame(x0, y0, 16, 16, VOID_0)
    tex.bevel(x0 + 1, y0 + 1, 14, 14, VOID_5, VOID_1)
    for d in range(5):
        for (sx, sy) in ((d, 4 - d), (-d, 4 - d), (d, d - 4), (-d, d - 4)):
            tex.set(x0 + 8 + sx, y0 + 8 + sy, PURPLE_DK)
            glo.set(x0 + 8 + sx, y0 + 8 + sy, light(PURPLE_HI, 0.8))
    glo.set(x0 + 8, y0 + 8, light(VIOLET_WHITE, 1.0))

    # the collar: bands, a gold one in the middle
    x0, y0, x1, y1 = r["collar_side"]
    metal(tex, x0, y0, 16, 8, 7, VOID_3, VOID_5)
    tex.hline(x0, x1 - 1, y0, EDGE_HI)
    tex.hline(x0, x1 - 1, y0 + 3, GOLD)
    tex.hline(x0, x1 - 1, y0 + 4, GOLD_DK)
    tex.hline(x0, x1 - 1, y1 - 1, VOID_0)
    for x in range(x0, x1):
        glo.set(x, y0 + 3, light(GOLD_HI, 0.55))
        glo.set(x, y0 + 4, light(GOLD, 0.25))
    for x in range(x0 + 1, x1, 4):
        tex.set(x, y0 + 1, VOID_1)
        tex.set(x, y0 + 6, VOID_1)

    # the socket's side: dark, a violet line round it
    x0, y0, x1, y1 = r["socket_side"]
    metal(tex, x0, y0, 16, 4, 9, VOID_2, VOID_4)
    tex.hline(x0, x1 - 1, y0, EDGE_HI)
    tex.hline(x0, x1 - 1, y0 + 2, PURPLE_DK)
    tex.hline(x0, x1 - 1, y1 - 1, VOID_0)
    for x in range(x0, x1):
        glo.set(x, y0 + 2, light(PURPLE_HI, 0.75))

    # the collar's (and socket's) top: a round plate with a ring engraved
    x0, y0, x1, y1 = r["collar_end"]
    metal(tex, x0, y0, 16, 16, 11, VOID_3, VOID_5)
    tex.frame(x0, y0, 16, 16, VOID_0)
    tex.ring(x0 + 8, y0 + 8, 4.6, 6.0, VOID_0, 0.9)
    tex.ring(x0 + 8, y0 + 8, 6.0, 6.8, EDGE_HI, 0.6)
    tex.disc(x0 + 8, y0 + 8, 2.6, VOID_1)
    for y in range(y0, y1):
        for x in range(x0, x1):
            d = math.hypot(x + 0.5 - (x0 + 8), y + 0.5 - (y0 + 8))
            k = math.exp(-((d - 5.3) / 0.7) ** 2)
            if k > 0.05:
                glo.set(x, y, light(PURPLE_HI, 0.8 * k))

    # the neck: grooved
    x0, y0, x1, y1 = r["neck_side"]
    metal(tex, x0, y0, 8, 16, 13, VOID_3, VOID_5)
    for y in range(y0 + 1, y1, 3):
        tex.hline(x0, x1 - 1, y, VOID_0)
        for x in range(x0, x1):
            glo.set(x, y, light(PURPLE, 0.45))
    tex.vline(x0, y0, y1 - 1, VOID_1)
    tex.vline(x1 - 1, y0, y1 - 1, VOID_0)

    # the crystal: lit from its point (its upper faces take the top half, its lower the bottom)
    x0, y0, x1, y1 = r["crystal"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = (y - y0 + 0.5) / 16.0
            u = (x - x0 + 0.5) / 16.0
            facet = 0.85 + 0.15 * math.cos(u * math.pi * 4)
            col = ramp([(0.0, VIOLET_WHITE), (0.3, PURPLE_HI), (0.5, PURPLE), (0.75, PURPLE_DK), (1.0, (28, 8, 54))], v)
            col = shade(col, facet)
            tex.set(x, y, col)
            glo.set(x, y, light(col, 0.55 + 0.45 * (1.0 - v)))
    for k in range(16):
        tex.set(x0 + k, y0 + 8, VIOLET_WHITE)
        glo.set(x0 + k, y0 + 8, light(VIOLET_WHITE, 0.9))

    # the rings: their faces (v from inside out) with glyph dashes round them, their outer walls with a violet line
    x0, y0, x1, y1 = r["ring_face"]
    metal(tex, x0, y0, 64, 8, 17, VOID_3, VOID_5)
    tex.hline(x0, x1 - 1, y0, VOID_0)
    tex.hline(x0, x1 - 1, y1 - 1, EDGE_HI)
    for i in range(8):
        dx = x0 + i * 8 + 2
        for k in range(4):
            tex.set(dx + k, y0 + 3, GOLD)
            tex.set(dx + k, y0 + 4, GOLD_DK)
            glo.set(dx + k, y0 + 3, light(VIOLET_WHITE, 0.95))
            glo.set(dx + k, y0 + 4, light(PURPLE_HI, 0.6))
        tex.set(dx + 6, y0 + 3, PURPLE_DK)
        glo.set(dx + 6, y0 + 3, light(PURPLE, 0.7))
    x0, y0, x1, y1 = r["ring_wall"]
    metal(tex, x0, y0, 64, 4, 19, VOID_3, VOID_4)
    tex.hline(x0, x1 - 1, y0, EDGE_HI)
    tex.hline(x0, x1 - 1, y0 + 1, PURPLE_DK)
    tex.hline(x0, x1 - 1, y0 + 2, PURPLE_DK)
    tex.hline(x0, x1 - 1, y1 - 1, VOID_0)
    for x in range(x0, x1):
        k = 0.65 + 0.35 * math.cos((x - x0) / 64 * 2 * math.pi * 4)
        glo.set(x, y0 + 1, light(PURPLE_HI, 0.85 * k))
        glo.set(x, y0 + 2, light(PURPLE, 0.6 * k))
    x0, y0, x1, y1 = r["ring_inner"]
    metal(tex, x0, y0, 64, 4, 23, VOID_1, VOID_3)
    tex.save(out("entity", "pylon.png"))
    glo.save(out("entity", "pylon_glow.png"))


# =================================================================== the blocks (block/), 32 x 32

def void_base(seed, inner=True):
    """Void stone: near black, a cold violet cast, faint veins and flecks; a dark seam round the tile, bevelled."""
    c = Canvas(32, 32)
    n0, n1, n2 = Noise(seed), Noise(seed + 1), Noise(seed + 2)
    for y in range(32):
        for x in range(32):
            k = 0.5 * n0.at(x / 6.0, y / 6.0) + 0.3 * n1.at(x / 3.0, y / 3.0) + 0.2 * n2.at(x / 1.4, y / 1.4)
            col = mix(VOID_1, VOID_3, k)
            vein = abs(n1.at(x / 5.0 + 10, y / 5.0 + 3) - 0.5)
            if vein < 0.035:
                col = mix(col, PURPLE_DK, 0.55 * (1 - vein / 0.035))
            c.set(x, y, col)
    r = rng(seed)
    for _ in range(7):
        c.set(r.randrange(3, 29), r.randrange(3, 29), mix(PURPLE_DK, PURPLE, r.random() * 0.5))
    c.frame(0, 0, 32, 32, VOID_0)
    c.bevel(1, 1, 30, 30, VOID_5, VOID_1)
    if inner:
        # an engraved border round a slightly sunken middle, studs at its corners
        for y in range(6, 26):
            for x in range(6, 26):
                c.set(x, y, shade(c.get(x, y), 0.86))
        c.inset(5, 5, 22, 22, VOID_0, VOID_4)
        for (sx, sy) in ((2, 2), (28, 2), (2, 28), (28, 28)):
            c.set(sx, sy, EDGE_HI)
            c.set(sx + 1, sy, VOID_4)
            c.set(sx, sy + 1, VOID_4)
            c.set(sx + 1, sy + 1, VOID_1)
    return c


def frames(draw, count, path, frametime):
    """An animated texture: `draw(frame, canvas)` for each frame, stacked one under another."""
    sheet = Canvas(32, 32 * count)
    for i in range(count):
        f = Canvas(32, 32)
        draw(i, f)
        sheet.paste(f, 0, 32 * i)
    sheet.save(path)
    animate(path, frametime)


def mask_to(c, mask, colour_at):
    """Puts light where a mask (a dict of pixel to strength) is: opaque pixels for a cutout overlay."""
    for (x, y), k in mask.items():
        if k >= 0.35:
            c.set(x, y, colour_at(x, y, k))


def rune_mask():
    """The singularity sigil: a ring, a point in it with two crescents round it, ticks at the four quarters."""
    m = Canvas(32, 32)
    m.ring(16, 16, 7.6, 8.8, WHITE)
    m.disc(16, 16, 1.9, WHITE)
    m.ring(16, 16, 3.7, 4.7, WHITE)
    for (a, b) in ((16, 4.0), (16, 28.0)):
        m.aaline(a, b, a, b + (3.0 if b < 16 else -3.0), WHITE, 1.0, 1.2)
    for (a, b) in ((4.0, 16), (28.0, 16)):
        m.aaline(a, b, a + (3.0 if a < 16 else -3.0), b, WHITE, 1.0, 1.2)
    for k in range(4):
        ang = math.pi / 4 + k * math.pi / 2
        m.aaline(16 + 5.6 * math.cos(ang), 16 + 5.6 * math.sin(ang), 16 + 6.8 * math.cos(ang), 16 + 6.8 * math.sin(ang), WHITE, 1.0, 1.1)
    return {(x, y): m.get(x, y)[3] / 255.0 for y in range(32) for x in range(32) if m.get(x, y)[3] > 0}


def void_casing():
    c = void_base(101)
    for d in range(6):
        for (dx, dy) in ((d, 5 - d), (-d, 5 - d), (d, d - 5), (-d, d - 5)):
            c.set(16 + dx, 16 + dy, VOID_0)
            c.set(16 + dx, 17 + dy, VOID_4)
    c.save(out("block", "void_casing.png"))


def void_casing_rune():
    base = void_base(202)
    mask = rune_mask()
    for (x, y), k in mask.items():
        base.set(x, y, mix(base.get(x, y), PURPLE_DK, k))
    # a dim violet halo round the sigil, in the stone itself
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if 5 < d < 12 and (x, y) not in mask:
                base.set(x, y, mix(base.get(x, y), PURPLE_DK, 0.25 * math.exp(-((d - 8.2) / 2.0) ** 2)))
    base.save(out("block", "void_casing_rune.png"))

    def draw(i, f):
        pulse = 0.62 + 0.38 * math.sin(i / 16.0 * 2 * math.pi)
        mask_to(f, mask, lambda x, y, k: mix(PURPLE, VIOLET_WHITE, pulse * k))
    frames(draw, 16, out("block", "void_casing_rune_glow.png"), 3)


def void_casing_seam():
    base = void_base(303, inner=False)
    rows = (10, 21)
    for y in rows:
        for x in range(32):
            base.set(x, y - 1, VOID_0)
            base.set(x, y, PURPLE_DK)
            base.set(x, y + 1, VOID_4)
    for x in (8, 23):
        for y in range(rows[0] + 2, rows[1] - 1):
            base.set(x - 1, y, VOID_0)
            base.set(x, y, PURPLE_DK)
            base.set(x + 1, y, VOID_4)
    base.save(out("block", "void_casing_seam.png"))

    def draw(i, f):
        p = i / 16.0 * 32
        for y in rows:
            for x in range(32):
                d = min(abs(x - p), 32 - abs(x - p)) if y == rows[0] else min(abs(31 - x - p), 32 - abs(31 - x - p))
                k = 0.5 + 0.5 * math.exp(-d * d / 14.0)
                f.set(x, y, mix(PURPLE, VIOLET_WHITE, k * k))
        for x in (8, 23):
            for y in range(rows[0] + 2, rows[1] - 1):
                f.set(x, y, mix(PURPLE_DK, PURPLE_HI, 0.6 + 0.4 * math.sin((y + i * 2) / 3.0)))
    frames(draw, 16, out("block", "void_casing_seam_glow.png"), 2)


def plate(seed):
    """A heavy machine face: void stone with a thick brushed frame."""
    c = void_base(seed, inner=False)
    metal(c, 2, 2, 28, 28, seed + 5, VOID_3, VOID_5)
    c.bevel(2, 2, 28, 28, EDGE_HI, VOID_1)
    return c


def vortex_mask(phase, arms=3, inner=1.5, outer=8.0, cx=16.0, cy=16.0):
    """Spiral arms turning into a middle: a dict of pixel to strength."""
    mask = {}
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if r < inner or r > outer:
                continue
            a = math.atan2(dy, dx)
            spiral = math.cos(arms * (a - 1.9 * math.log(r)) + phase)
            k = smooth(0.55, 0.95, spiral) * (1 - smooth(outer - 2.5, outer, r))
            if k > 0:
                mask[(x, y)] = k
    return mask


def core_side():
    base = plate(404)
    # the window: a sunken square on the dark, with a vortex in it
    for y in range(8, 24):
        for x in range(8, 24):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            base.set(x, y, mix((10, 6, 20), (34, 14, 60), clamp(1 - d / 11)))
    for (x, y), k in vortex_mask(0.0).items():
        if 8 <= x < 24 and 8 <= y < 24:
            base.set(x, y, mix(base.get(x, y), PURPLE_DK, k))
    base.disc(16, 16, 2.2, BLACK)
    base.inset(7, 7, 18, 18, VOID_0, EDGE_HI)
    base.frame(6, 6, 20, 20, GOLD_DK)
    for (gx, gy) in ((6, 6), (25, 6), (6, 25), (25, 25)):
        base.set(gx, gy, GOLD_HI)
    for (gx, gy) in ((3, 3), (28, 3), (3, 28), (28, 28)):
        base.set(gx, gy, GOLD)
        base.set(gx + 1, gy + 1, GOLD_DK)
    base.save(out("block", "fusion_core_side.png"))

    def window(f, mask, k):
        for (x, y), v in mask.items():
            if 8 <= x < 24 and 8 <= y < 24 and v >= 0.35:
                d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
                f.set(x, y, mix(PURPLE, VIOLET_WHITE, k * v * clamp(1.2 - d / 9)))
        for (gx, gy) in ((6, 6), (25, 6), (6, 25), (25, 25)):
            f.set(gx, gy, mix(GOLD, GOLD_HI, k))

    def draw(i, f):
        window(f, vortex_mask(-i / 16.0 * 2 * math.pi), 0.85)
        for a in range(16):
            ang = a / 16.0 * 2 * math.pi
            f.set(int(16 + 2.6 * math.cos(ang)), int(16 + 2.6 * math.sin(ang)), GOLD_HI if (a + i) % 4 == 0 else (255, 196, 120))
    frames(draw, 16, out("block", "fusion_core_side_glow.png"), 2)
    off = Canvas(32, 32)
    window(off, vortex_mask(0.0), 0.25)
    off.save(out("block", "fusion_core_side_glow_off.png"))


def core_top():
    base = plate(505)
    base.disc(16, 16, 12.5, VOID_1)
    base.ring(16, 16, 11.5, 13.0, VOID_0)
    base.ring(16, 16, 12.6, 13.6, EDGE_HI, 0.7)
    base.disc(16, 16, 6.2, (3, 2, 6))
    base.ring(16, 16, 6.0, 7.0, GOLD_DK)
    for k in range(12):
        ang = k / 12.0 * 2 * math.pi
        base.aaline(16 + 8.6 * math.cos(ang), 16 + 8.6 * math.sin(ang), 16 + 10.6 * math.cos(ang), 16 + 10.6 * math.sin(ang), PURPLE_DK, 1.0, 1.1)
    for (gx, gy) in ((3, 3), (28, 3), (3, 28), (28, 28)):
        base.set(gx, gy, GOLD)
        base.set(gx + 1, gy + 1, GOLD_DK)
    base.save(out("block", "fusion_core_top.png"))
    ring = Canvas(32, 32)
    ring.ring(16, 16, 6.0, 7.1, WHITE)
    ring_mask = {(x, y): ring.get(x, y)[3] / 255.0 for y in range(32) for x in range(32) if ring.get(x, y)[3] > 0}
    ticks = Canvas(32, 32)
    for k in range(12):
        ang = k / 12.0 * 2 * math.pi
        ticks.aaline(16 + 8.6 * math.cos(ang), 16 + 8.6 * math.sin(ang), 16 + 10.6 * math.cos(ang), 16 + 10.6 * math.sin(ang), WHITE, 1.0, 1.1)

    def draw(i, f, strength=1.0):
        pulse = strength * (0.7 + 0.3 * math.sin(i / 8.0 * 2 * math.pi))
        mask_to(f, ring_mask, lambda x, y, k: mix(GOLD, (255, 246, 226), pulse))
        for y in range(32):
            for x in range(32):
                if ticks.get(x, y)[3] > 90:
                    ang = math.atan2(y + 0.5 - 16, x + 0.5 - 16)
                    lit = 0.5 + 0.5 * math.cos(ang * 1 - i / 8.0 * 2 * math.pi)
                    f.set(x, y, mix(PURPLE_DK, PURPLE_HI, strength * (0.35 + 0.65 * lit)))
    frames(draw, 8, out("block", "fusion_core_top_glow.png"), 3)
    off = Canvas(32, 32)
    draw(0, off, 0.3)
    off.save(out("block", "fusion_core_top_glow_off.png"))


def core_bottom():
    void_base(606).save(out("block", "fusion_core_bottom.png"))


def pylon_side():
    base = plate(707)
    base.rect(13, 4, 6, 24, VOID_0)
    base.vline(12, 4, 27, VOID_1)
    base.vline(19, 4, 27, EDGE_HI)
    for y in (3, 28):
        base.hline(4, 27, y, GOLD_DK)
    glyphs = [((1, 1, 1, 1), (0, 1, 1, 0), (1, 0, 0, 1)), ((0, 1, 1, 0), (1, 1, 1, 1), (0, 1, 1, 0)), ((1, 0, 0, 1), (1, 1, 1, 1), (1, 0, 0, 1)),
              ((1, 1, 0, 0), (0, 1, 1, 0), (0, 0, 1, 1)), ((0, 0, 1, 1), (0, 1, 1, 0), (1, 1, 0, 0))]
    cells = []
    for i, gy in enumerate(range(5, 27, 5)):
        for j, row in enumerate(glyphs[i % len(glyphs)]):
            for k, on in enumerate(row):
                if on and gy + j < 27:
                    base.set(14 + k, gy + j, PURPLE_DK)
                    cells.append((14 + k, gy + j, i))
    base.save(out("block", "graviton_pylon_side.png"))

    def draw(i, f):
        for (x, y, g) in cells:
            lit = 0.5 + 0.5 * math.cos((g - i * 5 / 8.0) / 5.0 * 2 * math.pi)
            f.set(x, y, mix(PURPLE, VIOLET_WHITE, lit))
        for x in range(4, 28):
            for y in (3, 28):
                f.set(x, y, mix(GOLD_DK, GOLD, 0.6))
    frames(draw, 8, out("block", "graviton_pylon_side_glow.png"), 3)


def pylon_top():
    base = plate(808)
    base.disc(16, 16, 11.0, VOID_1)
    base.ring(16, 16, 9.0, 10.4, PURPLE_DK)
    base.ring(16, 16, 10.4, 11.4, VOID_0)
    base.ring(16, 16, 11.4, 12.2, EDGE_HI, 0.6)
    base.disc(16, 16, 4.0, VOID_0)
    base.save(out("block", "graviton_pylon_top.png"))
    ring = Canvas(32, 32)
    ring.ring(16, 16, 9.1, 10.3, WHITE)
    mask = {(x, y): ring.get(x, y)[3] / 255.0 for y in range(32) for x in range(32) if ring.get(x, y)[3] > 0}

    def draw(i, f):
        def col(x, y, k):
            ang = math.atan2(y + 0.5 - 16, x + 0.5 - 16)
            lit = 0.5 + 0.5 * math.cos(ang * 2 - i / 8.0 * 2 * math.pi)
            return mix(PURPLE, VIOLET_WHITE, 0.25 + 0.75 * lit)
        mask_to(f, mask, col)
    frames(draw, 8, out("block", "graviton_pylon_top_glow.png"), 3)


def pylon_bottom():
    void_base(909).save(out("block", "graviton_pylon_bottom.png"))


def creative_cell():
    base = plate(1001)
    for y in range(6, 26):
        for x in range(6, 26):
            base.set(x, y, (12, 8, 22))
    base.inset(5, 5, 22, 22, VOID_0, EDGE_HI)
    base.frame(4, 4, 24, 24, GOLD_DK)
    for (gx, gy) in ((4, 4), (27, 4), (4, 27), (27, 27)):
        base.set(gx, gy, GOLD_HI)
    base.save(out("block", "creative_cell.png"))

    def hue(h):
        h = h % 1.0
        stops = [(0.0, (255, 80, 200)), (0.2, (170, 90, 255)), (0.4, (80, 140, 255)), (0.6, (80, 230, 255)), (0.8, (255, 120, 255)), (1.0, (255, 80, 200))]
        return ramp(stops, h)

    def draw(i, f):
        for y in range(6, 26):
            for x in range(6, 26):
                dx, dy = x + 0.5 - 16, y + 0.5 - 16
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                swirl = 0.5 + 0.5 * math.cos(2 * a - r * 0.7 + i / 16.0 * 2 * math.pi)
                k = swirl * (1 - smooth(8.5, 11.0, r))
                if k > 0.3:
                    f.set(x, y, mix(hue(a / (2 * math.pi) + i / 16.0), WHITE, 0.25 * k))
        # the infinity sign in the middle
        for t in range(64):
            ang = t / 64.0 * 2 * math.pi
            den = 1 + math.sin(ang) ** 2
            px = 16 + 6.5 * math.cos(ang) / den
            py = 16 + 6.5 * math.sin(ang) * math.cos(ang) / den
            f.set(int(px), int(py), WHITE)
    frames(draw, 16, out("block", "creative_cell_glow.png"), 2)


# =================================================================== the items (item/), 32 x 32

def supersample(w, h, fn, n=4):
    """Colours each pixel by averaging fn(x, y) -> (rgb, alpha) over n x n points in it."""
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            r = g = b = a = 0.0
            for j in range(n):
                for i in range(n):
                    col, al = fn(x + (i + 0.5) / n, y + (j + 0.5) / n)
                    r += col[0] * al
                    g += col[1] * al
                    b += col[2] * al
                    a += al
            if a > 0:
                c.set(x, y, (r / a, g / a, b / a), 255 * a / (n * n))
    return c


def outline(c, colour=(16, 8, 28)):
    """A dark outline round what is drawn, as items have."""
    solid = {(x, y) for y in range(c.h) for x in range(c.w) if c.get(x, y)[3] > 100}
    for (x, y) in list(solid):
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if 0 <= p[0] < c.w and 0 <= p[1] < c.h and p not in solid and c.get(*p)[3] < 100:
                c.set(p[0], p[1], colour)


def graviton_crystal():
    def crystal(px, py):
        x, y = px - 16, py - 16
        if abs(x) > 5.2 or abs(y) > 13:
            return None
        top = -13 + abs(x) * 1.15
        bottom = 13 - abs(x) * 1.15
        if not (top <= y <= bottom):
            return None
        face = -1 if x < -1.6 else 1 if x > 1.6 else 0
        v = (y + 13) / 26
        base = ramp([(0.0, VIOLET_WHITE), (0.35, PURPLE_HI), (0.7, PURPLE), (1.0, PURPLE_DK)], v)
        if face < 0:
            base = shade(base, 0.72)
        elif face == 0:
            base = mix(base, VIOLET_WHITE, 0.35)
        if y < top + 2.2 or y > bottom - 1.2:
            base = mix(base, VIOLET_WHITE, 0.25)
        return base

    def orbit(px, py, front):
        x, y = px - 16, py - 17
        e = (x / 12.5) ** 2 + (y / 3.6) ** 2
        if abs(e - 1.0) > 0.12:
            return None
        if (y > 0) != front:
            return None
        return GOLD_HI if front else GOLD_DK

    def fn(px, py):
        o = orbit(px, py, True)
        if o:
            return o, 1.0
        cr = crystal(px, py)
        if cr:
            return cr, 1.0
        o = orbit(px, py, False)
        if o:
            return o, 1.0
        return (0, 0, 0), 0.0
    c = supersample(32, 32, fn)
    outline(c)
    for (sx, sy) in ((7, 6), (25, 9), (24, 25)):
        c.set(sx, sy, VIOLET_WHITE)
    c.save(out("item", "graviton_crystal.png"))


def singularity_shard():
    pts = [(13.5, 3.5), (21.0, 8.5), (23.5, 17.0), (19.5, 28.0), (11.5, 25.0), (8.5, 14.5)]

    def inside(x, y):
        n = len(pts)
        hit = False
        for i in range(n):
            (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
                hit = not hit
        return hit

    def edge(x, y):
        best = 99.0
        for i in range(len(pts)):
            (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
            dx, dy = x2 - x1, y2 - y1
            t = clamp(((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy))
            best = min(best, math.hypot(x - x1 - dx * t, y - y1 - dy * t))
        return best

    def fn(px, py):
        if not inside(px, py):
            return (0, 0, 0), 0.0
        e = edge(px, py)
        d = math.hypot(px - 15.5, py - 15.5)
        col = mix(VOID_3, VOID_1, clamp((py - 4) / 24))
        lit_side = px > 15
        if e < 1.3:
            col = mix(col, PURPLE_HI if lit_side else PURPLE, 0.85)
        col = add(col, light(PURPLE, 0.9 * math.exp(-d * d / 18.0)))
        col = add(col, light(VIOLET_WHITE, math.exp(-d * d / 2.5)))
        return col, 1.0
    c = supersample(32, 32, fn)
    for t in range(9):
        c.set(16 + t // 2, 16 + t, mix(PURPLE, PURPLE_HI, t / 8.0))
    outline(c)
    c.save(out("item", "singularity_shard.png"))


def mini_hole(frame, frames_total, cx, cy, r, disk_rx, disk_ry, gold=True):
    """A small singularity at (cx, cy): fn(x, y) -> (rgb, alpha) or None; streaks turned by the frame."""
    hot = [(0.0, (150, 52, 14)), (0.4, (255, 160, 70)), (0.8, (255, 228, 170)), (1.0, (255, 250, 240))]
    phase = frame / frames_total * 2 * math.pi

    def streak(a, q):
        return 0.6 + 0.4 * math.cos(a * 3 + q * 9 + phase * 2) * math.cos(a * 5 - q * 4 + phase)

    def fn(px, py):
        x, y = px - cx, py - cy
        d = math.hypot(x, y)
        # the disk, an ellipse ring: where (x, y) falls on it, which half, how far out
        e = math.hypot(x / disk_rx, y / disk_ry)
        on_disk = 0.48 <= e <= 1.0
        near = y > 0
        if on_disk and (near or d >= r):
            q = (e - 0.48) / 0.52
            a = math.atan2(y / disk_ry, x / disk_rx)
            k = (1 - q) ** 0.6 * streak(a, q)
            return ramp(hot, 1 - q), clamp(0.35 + k)
        if d < r:
            return BLACK, 1.0
        if d < r + 1.0:
            return (255, 244, 220), 1.0
        if y < 0 and d < r + 2.6:
            q = (d - r - 1.0) / 1.6
            a = math.atan2(y, x)
            return ramp(hot, 0.9 - 0.5 * q), clamp(0.95 - 0.5 * q) * (0.75 + 0.25 * streak(a, q))
        return None
    return fn


def collapsed_star():
    """A star crushed to a point: a white-hot core, a ring of its own light turning round it, four rays flaring."""
    hot = [(0.0, (170, 70, 20)), (0.4, (255, 160, 70)), (0.8, (255, 228, 170)), (1.0, (255, 250, 240))]

    def draw(i, f):
        phase = i / 8.0 * 2 * math.pi
        flare = 0.55 + 0.45 * math.sin(phase)

        def fn(px, py):
            x, y = px - 16, py - 16
            d = math.hypot(x, y)
            e = math.hypot(x / 12.0, y / 3.5)
            ring = None
            if 0.62 <= e <= 1.0:
                q = (e - 0.62) / 0.38
                a = math.atan2(y / 3.5, x / 12.0)
                k = (1 - q) ** 0.5 * (0.65 + 0.35 * math.cos(a * 4 + phase * 2 + q * 6))
                ring = (ramp(hot, 1 - q), clamp(0.3 + k))
            if ring and (y > 0 or d > 3.8):
                return ring
            core = math.exp(-(d / 3.0) ** 4)
            if core > 0.05:
                return mix((255, 220, 150), WHITE, core), 1.0
            glow = 0.7 * math.exp(-d * d / 30.0) + flare * (math.exp(-abs(x) / 4.2) * math.exp(-(y / 0.55) ** 2)
                                                            + math.exp(-abs(y) / 4.2) * math.exp(-(x / 0.55) ** 2))
            if ring:
                return ring
            if glow > 0.06:
                return mix(GOLD, (255, 246, 226), clamp(glow)), clamp(glow)
            return (0, 0, 0), 0.0
        f.paste(supersample(32, 32, fn), 0, 0)
    frames(draw, 8, out("item", "collapsed_star.png"), 3)


def event_horizon_core():
    def draw(i, f):
        hole = mini_hole(i, 8, 16, 16, 5.6, 14.5, 4.0)

        def setting(px, py):
            x, y = px - 16, py - 16
            d = abs(x) + abs(y)
            if 12.5 <= d <= 15.2:
                k = (d - 12.5) / 2.7
                return mix(PURPLE_HI, PURPLE_DK, k), 1.0
            if d < 12.5:
                return mix((40, 12, 70), (8, 4, 16), d / 12.5), 1.0
            return None

        def fn(px, py):
            h = hole(px, py)
            if h:
                return h
            s = setting(px, py)
            if s:
                return s
            return (0, 0, 0), 0.0
        c = supersample(32, 32, fn)
        outline(c)
        for (sx, sy) in ((16, 1), (16, 30), (1, 16), (30, 16)):
            c.set(sx, sy, VIOLET_WHITE)
        f.paste(c, 0, 0)
    frames(draw, 8, out("item", "event_horizon_core.png"), 3)


# =================================================================== the logo (the mod list's picture)

def sample(img, u, v, wrap=True):
    w, h = img.size
    x = int(math.floor(u * w)) % w if wrap else min(w - 1, max(0, int(u * w)))
    y = min(h - 1, max(0, int(v * h)))
    return img.getpixel((x, y))[:3]


def singularity(px, py, R, imgs, col, aura=0.55):
    """The singularity's light at a point (px, py from its middle, y up) over a background colour, as the renderer
    composes it: glows behind, the shadow, the tipped disk, the bent arcs and the photon ring."""
    disk_img, ring_img, glow_img = imgs
    tilt, din, dout = 0.2, 1.55, 3.3
    sin_t = math.sin(tilt)
    col = list(col)
    rho = math.hypot(px, py)
    inside = rho < R

    def plus(rgb, k):
        col[0] += rgb[0] * k
        col[1] += rgb[1] * k * 0.94
        col[2] += rgb[2] * k * 0.88
    if inside:
        col = [0.0, 0.0, 0.0]
    else:
        for gs, tint, kk in ((3.5 * R, (0.45, 0.29, 0.15), 0.42), (5.2 * R, (0.3, 0.09, 0.52), aura)):
            if abs(px) < gs and abs(py) < gs:
                g = sample(glow_img, (px / gs + 1) / 2, (1 - py / gs) / 2, False)
                col[0] += g[0] * tint[0] * kk
                col[1] += g[1] * tint[1] * kk
                col[2] += g[2] * tint[2] * kk
    dy = py / sin_t
    r = math.hypot(px, dy)
    if din * R <= r <= dout * R:
        a = math.atan2(dy, px)
        if not (math.sin(a) > 0 and inside):
            t = (r / R - din) / (dout - din)
            plus(sample(disk_img, (a % (2 * math.pi)) / (2 * math.pi) * 3 + 0.3, t),
                 (1 + 0.3 * math.cos(a)) * (1 + 0.25 * max(0.0, -math.sin(a))))
    if not inside:
        phi = math.atan2(py, px) % (2 * math.pi)
        if phi <= math.pi:
            o = R * (1.46 + 0.14 * math.cos(phi) ** 2)
            if 1.04 * R <= rho <= o:
                plus(sample(disk_img, phi / math.pi * 1.5 + 0.3, (rho - 1.04 * R) / (o - 1.04 * R) * 0.62), 0.95 * (0.8 + 0.2 * math.sin(phi)))
        else:
            o = R * (1.03 + 0.24 * (0.7 + 0.3 * abs(math.sin(phi))))
            if 1.03 * R <= rho <= o:
                plus(sample(disk_img, (1 - (phi - math.pi) / math.pi) * 1.5 + 0.3, (rho - 1.03 * R) / (o - 1.03 * R) * 0.4), 0.5)
        if 0.985 * R <= rho <= 1.1 * R:
            plus(sample(ring_img, phi / (2 * math.pi) * 4, (rho - 0.985 * R) / (0.115 * R)), 1.05)
    return col


def logo():
    """The singularity among the stars, as the renderer draws it, two pylons aimed at it from below."""
    from PIL import Image
    w, h = 512, 256
    disk_img = Image.open(out("effect", "disk.png")).convert("RGB")
    ring_img = Image.open(out("effect", "ring.png")).convert("RGB")
    glow_img = Image.open(out("effect", "glow.png")).convert("RGB")
    c = Canvas(w, h, (0, 0, 0, 255))
    stars = rng(5)
    neb = [Noise(3100 + i) for i in range(4)]
    cx, cy, R = w / 2, h / 2 - 6, 34.0
    imgs = (disk_img, ring_img, glow_img)
    for y in range(h):
        for x in range(w):
            k = fbm(neb, x / 60.0, y / 60.0, 4)
            col = [4 + 30 * smooth(0.5, 0.85, k), 3 + 10 * smooth(0.5, 0.85, k), 9 + 46 * smooth(0.5, 0.85, k)]
            col = singularity(x + 0.5 - cx, -(y + 0.5 - cy), R, imgs, col)
            c.set(x, y, tuple(min(255, int(v)) for v in col))
    for _ in range(260):
        x, y = stars.randrange(w), stars.randrange(h)
        if math.hypot(x - cx, (y - cy) * 2.2) < 3.6 * R:
            continue
        b = stars.random() ** 2
        tint = stars.choice([WHITE, (200, 215, 255), (255, 230, 190), (220, 200, 255)])
        c.set(x, y, mix(c.get(x, y), tint, 0.3 + 0.7 * b))
    # two pylons rising from the corners toward it, glyphs lit, lightning to their points
    for side in (-1, 1):
        bx, by = cx + side * 205, h + 10
        tx, ty = cx + side * 3.25 * R, cy + 0.9 * R
        dx, dy = tx - bx, ty - by
        length = math.hypot(dx, dy)
        ux, uy = dx / length, dy / length
        nx, ny = -uy, ux
        for t in range(int(length * 0.82)):
            half = 9.0 - 4.0 * t / length
            for s in range(-int(half), int(half) + 1):
                x, y = int(bx + ux * t + nx * s), int(by + uy * t + ny * s)
                if 0 <= x < w and 0 <= y < h:
                    edge = abs(s) >= half - 1
                    col = VOID_0 if edge else mix(VOID_2, VOID_4, 0.5 + 0.5 * (s / half))
                    if abs(s) <= 1 and (t // 5) % 2 == 0:
                        col = PURPLE_HI
                    c.set(x, y, col)
        for ring_at in (0.45, 0.55, 0.65, 0.75):
            rx, ry = bx + ux * length * ring_at, by + uy * length * ring_at
            for s in range(-14, 15):
                x, y = int(rx + nx * s), int(ry + ny * s)
                c.set(x, y, mix(VOID_4, PURPLE, 0.6 if abs(s) > 11 else 0.2))
        px, py = bx + ux * length * 0.86, by + uy * length * 0.86
        c.disc(px, py, 4.0, VIOLET_WHITE)
        for yy in range(int(py - 14), int(py + 15)):
            for xx in range(int(px - 14), int(px + 15)):
                d = math.hypot(xx - px, yy - py)
                c.glow(xx, yy, PURPLE_HI, 0.5 * math.exp(-d * d / 40.0))
        bolt = rng(17 + side)
        ax, ay = px, py
        steps = 9
        for k in range(1, steps + 1):
            t = k / steps
            qx = px + (cx + side * R * 1.05 - px) * t + (bolt.random() - 0.5) * 10 * math.sin(t * math.pi)
            qy = py + (cy + 0.25 * R - py) * t + (bolt.random() - 0.5) * 10 * math.sin(t * math.pi)
            c.aaline(ax, ay, qx, qy, PURPLE, 0.6, 3.0, True)
            c.aaline(ax, ay, qx, qy, VIOLET_WHITE, 0.9, 1.0, True)
            ax, ay = qx, qy
    path = os.path.join(RES, "singularityfusion_logo.png")
    c.save(path)


def main():
    for f in (disk, ring, glow, bolt, beam, jet, spark, shock, shadow, pylon, void_casing, void_casing_rune, void_casing_seam, core_side,
              core_top, core_bottom, pylon_side, pylon_top, pylon_bottom, creative_cell, graviton_crystal, singularity_shard, collapsed_star,
              event_horizon_core, logo):
        f()
        print("drew " + f.__name__)


if __name__ == "__main__":
    main()
