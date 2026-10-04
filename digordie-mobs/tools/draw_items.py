"""
Draws the new items: weapons (in-hand tile + inventory icon), crafting materials, devices and
projectiles. Same procedural style and helpers as draw_mobs.py.

    python3 tools/draw_items.py           -> src/DigOrDieMobs/assets/items/*.png

Weapon tiles follow the game's layout: the grip a little left of the middle, the barrel pointing right.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

import draw_mobs as d
from draw_mobs import hexc, ellipse_mask, poly_mask, union, shaded, solid, outline, glow, stack, line_layer, layer

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'DigOrDieMobs', 'assets', 'items')


class Gun:
    """Maps gun-local coordinates (x along the barrel, y down) into the 128-space frame."""

    def __init__(self, ox, oy, scale):
        self.ox, self.oy, self.s = ox, oy, scale

    def p(self, x, y):
        return (self.ox + x * self.s, self.oy + y * self.s)

    def poly(self, pts):
        return poly_mask([self.p(x, y) for x, y in pts])

    def rect(self, x0, y0, x1, y1):
        return self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)])

    def ell(self, x, y, rx, ry):
        cx, cy = self.p(x, y)
        return ellipse_mask(cx, cy, rx * self.s, ry * self.s)

    def lines(self, segs, color, width):
        return line_layer([[self.p(x, y) for x, y in seg] for seg in segs], color, width * self.s)


def metal(m, g, dark='#262B33', light='#7C8794'):
    cx, cy = g.p(20, -12)
    return [solid(outline(m, 1.1), hexc('#0C0F14')), shaded(m, hexc(dark), hexc(light), cx, cy, 60 * g.s)]


# ------------------------------------------------------------------- weapons
def acid_blaster(g):
    L = []
    grip = g.poly([(12, 5), (22, 5), (19, 24), (9, 24)])
    body = g.poly([(6, -8), (46, -8), (48, -5), (48, 5), (6, 6), (4, 2)])
    barrel = g.rect(46, -4, 66, 3)
    L += metal(union(grip, body, barrel), g)
    L.append(g.lines([[(8, 0), (44, 0)]], hexc('#9AA5B2', 120), 0.8))
    # acid tank
    tank = g.rect(16, -18, 42, -8)
    L.append(solid(outline(tank, 1.1), hexc('#0C2410')))
    L.append(shaded(tank, hexc('#1E8F2A'), hexc('#C8FF7A'), *g.p(22, -16), 30 * g.s))
    bubbles = union(*[g.ell(x, y, r, r) for x, y, r in [(22, -12, 1.6), (29, -14, 1.2), (35, -11, 1.8), (39, -15, 1)]])
    L.append(solid(bubbles, (230, 255, 200, 255), 0.7))
    L.append(solid(g.rect(17, -17.5, 41, -16), (255, 255, 255, 255), 0.35))
    L += metal(union(g.rect(14, -19, 17, -7), g.rect(41, -19, 44, -7)), g)
    # muzzle
    muzzle = g.rect(64, -5, 69, 4)
    L += metal(muzzle, g, '#1E2228', '#5A6470')
    L.append(glow(g.ell(70, -0.5, 4, 4), hexc('#7DFF4F'), 3, 1.0))
    L.append(solid(g.ell(68.5, -0.5, 1.6, 2.6), hexc('#D8FFB0')))
    return L


def crystal_shotgun(g):
    L = []
    stock = g.poly([(0, -3), (22, -5), (22, 6), (2, 9)])
    body = g.poly([(20, -7), (44, -7), (46, -4), (46, 6), (20, 6)])
    grip = g.poly([(26, 5), (34, 5), (32, 20), (24, 20)])
    L += metal(union(stock, body, grip), g, '#241C33', '#6A5A86')
    L.append(g.lines([[(22, -2), (44, -2)]], hexc('#B57BFF', 200), 1.0))
    shards = []
    for i, y in enumerate((-6, -1, 4)):
        length = 26 if i == 1 else 22
        shards.append(g.poly([(44, y - 2.2), (44 + length * 0.8, y - 2.4), (44 + length, y), (44 + length * 0.8, y + 2.4), (44, y + 2.2)]))
    sm = union(*shards)
    L.append(glow(sm, hexc('#C77DFF'), 2.5, 0.9))
    L.append(solid(outline(sm, 0.8), hexc('#2A0F55')))
    L.append(shaded(sm, hexc('#6A2BD9'), hexc('#E8FFFF'), *g.p(60, -6), 26 * g.s))
    L.append(g.lines([[(46, -6), (66, -6)], [(46, -1), (70, -1)], [(46, 4), (66, 4)]], (255, 255, 255, 110), 0.6))
    return L


def pyre_launcher(g):
    L = []
    tube = g.poly([(4, -9), (62, -10), (64, -8), (64, 7), (62, 9), (4, 8), (2, 0)])
    grip = g.poly([(20, 7), (28, 7), (26, 22), (18, 22)])
    handle = g.poly([(40, 7), (46, 7), (46, 15), (40, 15)])
    L += metal(union(tube, grip, handle), g, '#1C1714', '#5E5048')
    for x in (34, 40, 46, 52):
        vent = g.rect(x, -7, x + 3, -3)
        L.append(solid(vent, hexc('#FF7A1A')))
        L.append(glow(vent, hexc('#FF7A1A'), 1.5, 0.8))
    core = g.ell(18, -1, 6, 6)
    L.append(solid(outline(core, 1), hexc('#2A0500')))
    L.append(shaded(core, hexc('#C2410C'), hexc('#FFF2A8'), *g.p(16, -3), 8 * g.s))
    L.append(glow(core, hexc('#FF9A1F'), 2, 0.7))
    rivets = union(*[g.ell(x, y, 0.9, 0.9) for x in (8, 28, 58) for y in (-7, 6)])
    L.append(solid(rivets, hexc('#8A7A70')))
    ring = g.rect(62, -11, 69, 10)
    L += metal(ring, g, '#2A1F18', '#7A6A5E')
    L.append(glow(g.ell(70, -0.5, 4, 7), hexc('#FF7A1A'), 3, 0.9))
    return L


def abyss_scepter(g):
    L = []
    shaft = g.rect(0, -2, 52, 2)
    L += metal(shaft, g, '#1E0A2C', '#6A3F8A')
    bands = union(*[g.rect(x, -3, x + 2.4, 3) for x in (10, 22, 40, 50)])
    L.append(solid(outline(bands, 0.6), hexc('#4A3000')))
    L.append(shaded(bands, hexc('#9A6A00'), hexc('#FFE77A'), *g.p(20, -3), 40 * g.s))
    L.append(solid(g.rect(14, -2.6, 24, 2.6), hexc('#3A1650')))  # grip wrap
    # prongs around the orb
    prongs = union(g.poly([(52, -2), (60, -12), (66, -13), (60, -8)]), g.poly([(52, 2), (60, 12), (66, 13), (60, 8)]))
    L.append(solid(outline(prongs, 0.8), hexc('#2A0F55')))
    L.append(shaded(prongs, hexc('#6A2BD9'), hexc('#F3D6FF'), *g.p(60, -10), 14 * g.s))
    # eye orb
    L.append(glow(g.ell(62, 0, 11, 11), hexc('#B23CFF'), 4, 0.9))
    orb = g.ell(62, 0, 8, 8)
    L.append(solid(outline(orb, 1), hexc('#1E0A2C')))
    L.append(shaded(orb, hexc('#8E79AE'), hexc('#FFFFFF'), *g.p(60, -3), 10 * g.s))
    L.append(shaded(g.ell(63, 0, 4.2, 4.2), hexc('#2A0E52'), hexc('#7FF6FF'), *g.p(63, 0), 5 * g.s))
    L.append(solid(g.ell(63, 0, 0.9, 3.2), hexc('#06020B')))
    L.append(solid(g.ell(60.5, -2.5, 1.4, 1), (255, 255, 255, 255), 0.8))
    return L


WEAPONS = {
    'gun_acid_blaster': acid_blaster,
    'gun_crystal_shotgun': crystal_shotgun,
    'gun_pyre_launcher': pyre_launcher,
    'gun_abyss_scepter': abyss_scepter,
}


# ------------------------------------------------------------------ materials
def acid_gland():
    sac = union(ellipse_mask(64, 70, 26, 30), ellipse_mask(64, 44, 12, 12))
    L = [glow(sac, hexc('#7DFF4F'), 6, 0.5), solid(outline(sac, 2), hexc('#0B3512')),
         shaded(sac, hexc('#1F8A2E'), hexc('#D8FF9A'), 56, 58, 40, 0.95)]
    veins = [[(64, 40), (58, 58), (50, 74)], [(64, 40), (70, 60), (78, 76)], [(58, 58), (66, 78), (62, 92)]]
    L.append(line_layer(veins, hexc('#0E5A1C', 170), 1.4))
    L.append(solid(union(*[ellipse_mask(x, y, r, r) for x, y, r in [(56, 80, 3), (72, 70, 2.4), (66, 90, 2)]]), (230, 255, 200, 255), 0.6))
    L.append(solid(ellipse_mask(54, 58, 6, 9), (255, 255, 255, 255), 0.4))
    L.append(solid(outline(ellipse_mask(64, 34, 7, 4), 1), hexc('#0B3512')))
    L.append(shaded(ellipse_mask(64, 34, 7, 4), hexc('#1F8A2E'), hexc('#9DFF7A'), 62, 32, 8))
    return L


def crystal_heart():
    heart = union(ellipse_mask(50, 52, 17, 17), ellipse_mask(78, 52, 17, 17), poly_mask([(34, 58), (94, 58), (64, 100)]))
    L = [glow(heart, hexc('#C77DFF'), 7, 0.8), solid(outline(heart, 2), hexc('#2A0F55')),
         shaded(heart, hexc('#5A1FC2'), hexc('#F6E9FF'), 52, 44, 52)]
    facets = [[(64, 46), (64, 98)], [(50, 40), (64, 64), (78, 40)], [(36, 56), (64, 64), (92, 56)]]
    L.append(line_layer(facets, (255, 255, 255, 90), 1.2))
    L.append(solid(ellipse_mask(48, 46, 6, 4), (255, 255, 255, 255), 0.6))
    return L


def pyre_core():
    L = [glow(ellipse_mask(64, 66, 26, 26), hexc('#FF7A1A'), 9, 0.8)]
    # small flames on top
    flames = union(*[poly_mask([(64 + dx - 6, 50), (64 + dx + 6, 50), (64 + dx + tilt, 50 - h)]) for dx, h, tilt in [(-10, 14, -3), (0, 20, 0), (10, 14, 3)]])
    L.append(solid(flames.filter(ImageFilter.GaussianBlur(1.5)), hexc('#FF9A1F')))
    core = ellipse_mask(64, 66, 22, 22)
    L.append(solid(outline(core, 1.4), hexc('#5A1000')))
    L.append(shaded(core, hexc('#D43A0C'), hexc('#FFF8C8'), 58, 60, 30))
    # obsidian claws holding the core
    claws = []
    for a in (45, 135, 225, 315):
        r = math.radians(a)
        cx, cy = 64 + math.cos(r) * 22, 66 + math.sin(r) * 22
        nx, ny = math.cos(r), math.sin(r)
        tx, ty = -ny, nx
        claws.append(poly_mask([(cx + tx * 7 + nx * 6, cy + ty * 7 + ny * 6), (cx - tx * 7 + nx * 6, cy - ty * 7 + ny * 6),
                                (cx - tx * 3 - nx * 7, cy - ty * 3 - ny * 7), (cx + tx * 3 - nx * 7, cy + ty * 3 - ny * 7)]))
    cm = union(*claws)
    L.append(solid(outline(cm, 1), hexc('#0A0403')))
    L.append(shaded(cm, hexc('#140806'), hexc('#6A4A40'), 54, 50, 40))
    L.append(solid(ellipse_mask(57, 58, 6, 4), (255, 255, 255, 255), 0.55))
    return L


def abyss_tear():
    tear = union(ellipse_mask(64, 74, 22, 24), poly_mask([(44, 66), (84, 66), (64, 24)]))
    L = [glow(tear, hexc('#B23CFF'), 8, 0.85), solid(outline(tear, 2), hexc('#1E0A2C')),
         shaded(tear, hexc('#3A0F6B'), hexc('#E9C8FF'), 56, 58, 46)]
    L.append(shaded(ellipse_mask(64, 76, 10, 10), hexc('#2A0E52'), hexc('#7FF6FF'), 64, 76, 11))
    L.append(solid(ellipse_mask(64, 76, 2, 7), hexc('#06020B')))
    L.append(solid(ellipse_mask(55, 56, 4, 7), (255, 255, 255, 255), 0.5))
    return L


def jelly_goo():
    blob = union(ellipse_mask(64, 74, 30, 20), ellipse_mask(52, 62, 14, 12), ellipse_mask(76, 60, 15, 13))
    L = [glow(blob, hexc('#3FB6FF'), 7, 0.7), solid(outline(blob, 2), hexc('#0E2F5E'), 0.9),
         shaded(blob, hexc('#1C4F9C'), hexc('#C8F4FF'), 56, 56, 44, 0.85)]
    spots = union(*[ellipse_mask(x, y, 2, 2) for x, y in [(52, 70), (70, 64), (78, 78), (60, 82)]])
    L.append(glow(spots, hexc('#B6FFF9'), 2, 1.0))
    L.append(solid(spots, hexc('#E8FFFE')))
    L.append(solid(ellipse_mask(52, 58, 6, 4), (255, 255, 255, 255), 0.5))
    return L


MATERIALS = {
    'mat_acid_gland': acid_gland,
    'mat_crystal_heart': crystal_heart,
    'mat_pyre_core': pyre_core,
    'mat_abyss_tear': abyss_tear,
    'mat_jelly_goo': jelly_goo,
}


# -------------------------------------------------------------------- devices
def royal_potion():
    glass = union(poly_mask([(46, 54), (82, 54), (90, 70), (90, 100), (84, 106), (44, 106), (38, 100), (38, 70)]), ellipse_mask(64, 50, 10, 4))
    neck = poly_mask([(56, 30), (72, 30), (72, 54), (56, 54)])
    L = [solid(outline(union(glass, neck), 2), hexc('#1A2A20'))]
    L.append(shaded(neck, hexc('#7AA0A8'), hexc('#F0FFFF'), 60, 36, 30, 0.7))
    liquid = poly_mask([(39, 74), (89, 74), (90, 100), (84, 106), (44, 106), (38, 100)])
    L.append(shaded(glass, hexc('#7AA0A8'), hexc('#F0FFFF'), 50, 60, 60, 0.45))
    L.append(shaded(liquid, hexc('#1FAE63'), hexc('#F5FF9A'), 54, 80, 40))
    L.append(glow(liquid, hexc('#9DFFC4'), 4, 0.5))
    cork = poly_mask([(54, 22), (74, 22), (73, 32), (55, 32)])
    L.append(solid(outline(cork, 1), hexc('#2A1A0A')))
    L.append(shaded(cork, hexc('#6A4020'), hexc('#C89060'), 60, 24, 14))
    # tiny crown on the label
    crown = poly_mask([(54, 92), (54, 82), (59, 87), (64, 80), (69, 87), (74, 82), (74, 92)])
    L.append(solid(outline(crown, 0.8), hexc('#4A3000')))
    L.append(shaded(crown, hexc('#9A6A00'), hexc('#FFE77A'), 60, 82, 16))
    L.append(solid(poly_mask([(44, 60), (50, 58), (48, 98), (42, 96)]), (255, 255, 255, 255), 0.35))
    return L


def crystal_shield():
    pts = [(64 + math.cos(math.radians(a)) * 40, 64 + math.sin(math.radians(a)) * 40) for a in range(-90, 270, 60)]
    hexm = poly_mask(pts)
    L = [glow(hexm, hexc('#C77DFF'), 8, 0.6), solid(outline(hexm, 2), hexc('#2A0F55'), 0.9),
         shaded(hexm, hexc('#4A1FA0'), hexc('#E9D8FF'), 50, 40, 70, 0.55)]
    inner = poly_mask([(64 + math.cos(math.radians(a)) * 24, 64 + math.sin(math.radians(a)) * 24) for a in range(-90, 270, 60)])
    L.append(shaded(inner, hexc('#6A2BD9'), hexc('#F6E9FF'), 58, 52, 34, 0.85))
    L.append(line_layer([[pts[i], (64, 64)] for i in range(6)], (255, 255, 255, 80), 1.0))
    L.append(solid(ellipse_mask(54, 48, 7, 4), (255, 255, 255, 255), 0.5))
    return L


def jelly_lamp():
    L = []
    frame = [[(44, 30), (84, 30)], [(48, 30), (44, 100)], [(80, 30), (84, 100)], [(40, 100), (88, 100)], [(64, 18), (64, 30)]]
    hook = ellipse_mask(64, 16, 5, 5)
    glass = poly_mask([(48, 32), (80, 32), (82, 98), (46, 98)])
    L.append(glow(glass, hexc('#3FB6FF'), 9, 0.8))
    L.append(shaded(glass, hexc('#1C4F9C'), hexc('#BFF4FF'), 58, 50, 60, 0.45))
    bell = ellipse_mask(64, 56, 13, 11)
    L.append(shaded(bell, hexc('#3A8FE0'), hexc('#E8FFFF'), 60, 50, 16))
    L.append(line_layer([[(58 + i * 3, 62), (57 + i * 3 + (i % 2) * 2, 86)] for i in range(5)], hexc('#9BE8FF', 200), 1.4))
    L.append(line_layer(frame, hexc('#2A2F38'), 3))
    ring = Image.fromarray(__import__('numpy').clip(__import__('numpy').asarray(outline(hook, 1.5), 'float32') - __import__('numpy').asarray(hook, 'float32'), 0, 255).astype('uint8'))
    L.append(solid(ring, hexc('#2A2F38')))
    return L


DEVICES = {
    'dev_royal_potion': royal_potion,
    'dev_crystal_shield': crystal_shield,
    'dev_jelly_lamp': jelly_lamp,
}


# ---------------------------------------------------------------- projectiles
def bolt(color_dark, color_light, glow_color, length, thickness):
    body = union(ellipse_mask(64, 64, length * 0.5, thickness), poly_mask([(64 - length * 0.9, 64), (64, 64 - thickness), (64, 64 + thickness)]))
    return [glow(body, hexc(glow_color), thickness * 0.45, 0.9), shaded(body, hexc(color_dark), hexc(color_light), 64 + length * 0.2, 64, length * 0.8)]


PROJECTILES = {
    # name: (drawing, crop box in 128-space, output size)
    'proj_acid_bolt': (lambda: bolt('#2FA83A', '#F0FFC0', '#7DFF4F', 16, 9), (36, 36, 92, 92), (40, 40)),
    'proj_crystal_shard': (lambda: [glow(poly_mask([(36, 64), (76, 58), (92, 64), (76, 70)]), hexc('#C77DFF'), 3, 1.0),
                                    shaded(poly_mask([(36, 64), (76, 58), (92, 64), (76, 70)]), hexc('#6A2BD9'), hexc('#F2FFFF'), 80, 62, 40)],
                           (30, 52, 98, 76), (56, 20)),
    'proj_pyre_ball': (lambda: bolt('#D43A0C', '#FFF6C2', '#FF7A1A', 28, 14), (14, 34, 114, 94), (64, 38)),
    'proj_abyss_bolt': (lambda: bolt('#5A1FC2', '#FFFFFF', '#B23CFF', 40, 9), (4, 44, 124, 84), (96, 32)),
}


def render(layers):
    return d.finish(stack(*[l for l in layers if l is not None]))


def main():
    os.makedirs(OUT, exist_ok=True)
    d.set_output(128)
    for name, fn in WEAPONS.items():
        render(fn(Gun(46, 72, 1.0))).save(os.path.join(OUT, name + '.png'))
        render(fn(Gun(12, 62, 1.45))).save(os.path.join(OUT, name + '_icon.png'))
    for group in (MATERIALS, DEVICES):
        for name, fn in group.items():
            render(fn()).save(os.path.join(OUT, name + '.png'))
    for name, (fn, box, size) in PROJECTILES.items():
        img = render(fn())
        scale = 128 / 128.0
        img.crop(tuple(int(v * scale) for v in box)).resize(size, Image.LANCZOS).save(os.path.join(OUT, name + '.png'))
    print('items written to', os.path.abspath(OUT))


if __name__ == '__main__':
    main()
