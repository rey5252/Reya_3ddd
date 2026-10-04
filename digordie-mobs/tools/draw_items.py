"""
Draws the new items as pixel art in the style of the game's item sprites: weapons (in-hand tile +
inventory icon), crafting materials, devices and projectiles.

    python3 tools/draw_items.py           -> src/DigOrDieMobs/assets/items/*.png

Everything is drawn on a 64x64 logical canvas and scaled x2 (128x128 tiles), see pixel.py.
Weapon tiles follow the game's layout: the grip a little left of the middle, the barrel pointing right.
"""
import math
import os

from pixel import Canvas, Placed

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'DigOrDieMobs', 'assets', 'items')
LOGICAL = 64


# =================================================================== weapons
# Designs use local pixel units: x along the barrel (0 = stock end), y down, about 36 x 17.

def acid_blaster(g):
    c = g.c
    stock = g.poly([(0, 6), (5, 5), (5, 11), (1, 12)])
    body = g.rect(5, 5, 22, 11)
    grip = g.poly([(8, 11), (13, 11), (11, 18), (6, 18)])
    c.fill(c.union(stock, body, grip), 'gunmetal', noise=0.08)
    barrel = g.rect(22, 7, 31, 9)
    c.fill(barrel, 'steel')
    muzzle = g.rect(31, 6, 33, 10)
    c.fill(muzzle, 'darksteel')
    # acid tank on top, with metal caps
    tank = g.rect(8, 1, 20, 4)
    c.fill(tank, 'acid', shade='radial', noise=0.12)
    c.fill(c.union(g.rect(6, 0, 7, 5), g.rect(21, 0, 22, 5)), 'steel')
    for x, y in ((11, 3), (15, 2), (18, 3)):
        g.pixel(x, y, '#E6FFC2')
    # details
    c.color(g.rect(6, 8, 21, 8), '#1A1D22')
    g.pixel(19, 6, '#7DFF4F')
    g.pixel(20, 6, '#3CB82A')
    emitter = g.rect(34, 7, 34, 9)
    c.color(emitter, '#B5F27A')
    c.glow(emitter, '#7DFF4F', 0.45)


def crystal_shotgun(g):
    c = g.c
    stock = g.poly([(0, 6), (9, 4), (9, 11), (1, 13)])
    c.fill(stock, 'purplemetal', noise=0.08)
    receiver = g.rect(9, 3, 20, 10)
    c.fill(receiver, 'gunmetal', noise=0.06)
    c.color(g.rect(10, 6, 19, 6), '#C79A2A')
    grip = g.poly([(12, 10), (16, 10), (15, 17), (11, 17)])
    c.fill(grip, 'purplemetal')
    forend = g.rect(20, 8, 27, 10)
    c.fill(forend, 'darksteel')
    # three crystal barrels
    for i, (y, length, pal) in enumerate(((3, 12, 'violet'), (5, 14, 'cyan'), (7, 11, 'violet'))):
        shard = g.poly([(20, y), (20 + length - 2, y), (20 + length, y + 0.5), (20 + length - 2, y + 1.9), (20, y + 1.9)])
        c.fill(shard, pal)
    g.pixel(33, 5, '#FFFFFF')
    g.pixel(30, 3, '#FFFFFF')


def pyre_launcher(g):
    c = g.c
    tube = g.rect(3, 2, 31, 10)
    c.fill(tube, 'obsidian', noise=0.04)
    cap = g.rect(0, 3, 2, 9)
    c.fill(cap, 'steel')
    ring = g.rect(32, 1, 35, 11)
    c.fill(ring, 'steel')
    grip = g.poly([(10, 11), (14, 11), (13, 17), (9, 17)])
    handle = g.rect(21, 11, 23, 15)
    c.fill(c.union(grip, handle), 'darksteel')
    # molten core window and vents
    core = g.ellipse(8, 6, 2.6, 2.6)
    c.fill(core, 'fire', shade='radial')
    c.glow(core, '#FF7A1A', 0.3)
    vents = c.union(*[g.rect(x, 3, x + 1, 4) for x in (15, 18, 21, 24, 27)])
    c.color(vents, '#F28A1C')
    for x in (15, 18, 21, 24, 27):
        g.pixel(x, 3, '#FFE07A')
    for x in (5, 13, 29):
        g.pixel(x, 9, '#6E5A50')
    mouth = g.rect(36, 4, 36, 8)
    c.color(mouth, '#FFB04A')
    c.glow(mouth, '#FF7A1A', 0.4)


def abyss_scepter(g):
    c = g.c
    shaft = g.rect(0, 5, 27, 6)
    c.fill(shaft, 'abyss')
    wrap = g.rect(8, 4, 12, 7)
    c.fill(wrap, 'obsidian')
    bands = c.union(*[g.rect(x, 4, x + 1, 7) for x in (4, 15, 22, 26)])
    c.fill(bands, 'gold')
    # crystal prongs holding the eye
    prongs = c.union(g.poly([(27, 5), (31, 0), (34, 0), (30, 4)]), g.poly([(27, 7), (31, 12), (34, 12), (30, 8)]))
    c.fill(prongs, 'violet')
    eye = g.ellipse(33, 6, 4.2, 4.2)
    c.fill(eye, 'eye', shade='radial')
    iris = g.ellipse(33.5, 6, 2.1, 2.1)
    c.fill(iris, 'cyan', shade='radial')
    c.color(g.rect(33.5, 4.6, 33.5, 7.4), '#06020B')
    g.pixel(31.5, 4, '#FFFFFF')
    c.glow(eye, '#B23CFF', 0.35)


WEAPONS = {
    'gun_acid_blaster': acid_blaster,
    'gun_crystal_shotgun': crystal_shotgun,
    'gun_pyre_launcher': pyre_launcher,
    'gun_abyss_scepter': abyss_scepter,
}


def weapon_images(fn):
    hand = Canvas(LOGICAL, LOGICAL, seed=3)
    fn(Placed(hand, 22, 30, 1.0))           # same spot as vanilla in-hand weapons
    icon = Canvas(LOGICAL, LOGICAL, seed=3)
    fn(Placed(icon, 5, 19, 1.5))            # bigger, centred
    return hand.finish(), icon.finish()


# ================================================================= materials

def acid_gland(c):
    sac = c.union(c.ellipse(32, 36, 12, 14), c.ellipse(32, 22, 5, 4))
    c.fill(sac, 'acid', shade='radial', light=(27, 27), radius=17, noise=0.06)
    for pts in (((32, 21), (29, 30), (26, 38)), ((32, 21), (35, 31), (38, 40)), ((29, 30), (33, 41), (31, 47))):
        c.color(c.line(pts), '#155A1A')
    for x, y in ((27, 40), (36, 35), (33, 45)):
        c.pixel(x, y, '#D8FFA8')
    c.fill(c.ellipse(32, 17, 3, 2), 'acid')


def crystal_heart(c):
    heart = c.union(c.ellipse(25, 26, 8, 8), c.ellipse(39, 26, 8, 8), c.poly([(17, 29), (47, 29), (32, 47)]))
    c.fill(heart, 'violet', shade='radial', light=(26, 22), radius=24)
    for pts in (((32, 24), (32, 46)), ((24, 19), (32, 31), (40, 19)), ((18, 28), (32, 31), (46, 28))):
        c.color(c.line(pts), '#4B2A9E')
    for x, y in ((24, 22), (25, 21), (38, 23)):
        c.pixel(x, y, '#FFFFFF')
    c.glow(heart, '#C77DFF', 0.25)


def pyre_core(c):
    ring = c.ellipse(32, 34, 13, 13)
    c.fill(ring, 'obsidian', shade='radial', light=(26, 27), radius=20, noise=0.05)
    core = c.ellipse(32, 34, 9, 9)
    c.erase(core)
    c.fill(core, 'fire', shade='radial', light=(30, 31), radius=11)
    # four small obsidian claws gripping the core
    for a in (45, 135, 225, 315):
        r = math.radians(a)
        bx, by = 32 + math.cos(r) * 11, 34 + math.sin(r) * 11
        tx, ty = 32 + math.cos(r) * 7, 34 + math.sin(r) * 7
        nx, ny = -math.sin(r) * 2, math.cos(r) * 2
        c.fill(c.poly([(bx + nx, by + ny), (bx - nx, by - ny), (tx, ty)]), 'steel', shade='flat', tones=2)
    for x, y in ((28, 30), (29, 30), (28, 31)):
        c.pixel(x, y, '#FFFBE0')
    flame = c.union(c.poly([(27, 22), (30, 22), (28, 16)]), c.poly([(30, 22), (34, 22), (32, 13)]), c.poly([(34, 22), (37, 22), (36, 16)]))
    c.fill(flame, 'fire', shade='flat', tones=2)
    c.glow(core, '#FF7A1A', 0.3)


def abyss_tear(c):
    tear = c.union(c.ellipse(32, 37, 11, 12), c.poly([(22, 33), (42, 33), (32, 13)]))
    c.fill(tear, 'abyss', shade='radial', light=(28, 28), radius=22)
    c.fill(c.ellipse(32, 38, 5, 5), 'eye', shade='radial')
    c.fill(c.ellipse(32, 38, 2.6, 2.6), 'cyan', shade='radial')
    c.color(c.rect(32, 36, 32, 40), '#06020B')
    c.pixel(28, 28, '#E0C8FF')
    c.pixel(28, 29, '#E0C8FF')
    c.glow(tear, '#B23CFF', 0.3)


def jelly_goo(c):
    blob = c.union(c.ellipse(32, 38, 15, 9), c.ellipse(26, 32, 7, 6), c.ellipse(38, 31, 8, 6))
    c.fill(blob, 'jelly', shade='radial', light=(27, 28), radius=18, alpha=0.92)
    for x, y in ((25, 37), (35, 33), (40, 40), (30, 42)):
        c.pixel(x, y, '#E8FFFE')
    c.pixel(23, 30, '#FFFFFF')
    c.pixel(24, 30, '#FFFFFF')


MATERIALS = {
    'mat_acid_gland': acid_gland,
    'mat_crystal_heart': crystal_heart,
    'mat_pyre_core': pyre_core,
    'mat_abyss_tear': abyss_tear,
    'mat_jelly_goo': jelly_goo,
}


# =================================================================== devices

def royal_potion(c):
    cap = c.rect(28, 10, 35, 13)
    c.fill(cap, 'steel')
    neck = c.rect(29, 14, 34, 19)
    body = c.union(c.poly([(24, 22), (39, 22), (43, 27), (43, 50), (41, 53), (22, 53), (20, 50), (20, 27)]), c.rect(27, 19, 36, 22))
    c.fill(c.union(neck, body), 'glass', noise=0.04)
    liquid = c.poly([(21, 33), (42, 33), (42, 50), (40, 52), (23, 52), (21, 50)])
    c.fill(liquid, 'acid', shade='radial', light=(27, 36), radius=18)
    label = c.rect(24, 38, 39, 47)
    c.fill(label, ['#B9A57E', '#D9C9A0', '#EFE3C2', '#FFF8E6'])
    crown = c.poly([(27, 45), (27, 40), (29, 42), (31.5, 39), (34, 42), (36, 40), (36, 45)])
    c.fill(crown, 'gold')
    c.pixel(31, 43, '#DC2E36')
    c.color(c.rect(22, 24, 22, 32), '#FFFFFF', 0.8)


def crystal_shield(c):
    disc = c.ellipse(32, 32, 20, 20)
    c.fill(disc, ['#7E5AC8', '#A684EA', '#C9B2FF', '#EDE4FF'], shade='radial', light=(25, 24), radius=28, alpha=0.5)
    rim = c.ellipse(32, 32, 20, 20) & ~c.ellipse(32, 32, 18, 18)
    c.fill(rim, 'violet', shade='flat', tones=2, alpha=0.8)
    band = c.rect(13, 30, 50, 34)
    c.fill(band, 'steel')
    for x in (19, 26, 37, 44):
        c.pixel(x, 32, '#2C3138')
    gem = c.poly([(32, 25), (37, 32), (32, 39), (27, 32)])
    c.fill(gem, 'cyan')
    c.pixel(31, 28, '#FFFFFF')
    c.color(c.line([(19, 21), (24, 17)]), '#FFFFFF', 0.8)


def jelly_lamp(c):
    glass = c.rect(24, 18, 39, 47)
    c.fill(glass, ['#3A86CC', '#5AA8E0', '#8CD0F2', '#C8F0FF'], shade='radial', light=(31, 28), radius=22, alpha=0.6)
    bell = c.ellipse(31.5, 28, 5, 4)
    c.fill(bell, ['#5AA8E0', '#94D4F4', '#C8F0FF', '#FFFFFF'], shade='radial', light=(30, 26), radius=6)
    for i, x in enumerate((28, 30, 32, 34, 35)):
        c.color(c.line([(x, 31), (x - 1 + (i % 2) * 2, 36), (x, 41)]), '#E0F8FF', 0.95)
    c.color(c.line([(25, 20), (25, 30)]), '#FFFFFF', 0.6)
    frame = c.union(c.rect(22, 15, 41, 17), c.rect(22, 48, 41, 50), c.rect(22, 18, 23, 47), c.rect(40, 18, 41, 47))
    c.fill(frame, 'darksteel')
    hook = c.union(c.rect(30, 9, 33, 10), c.rect(30, 11, 30, 14), c.rect(33, 11, 33, 14))
    c.fill(hook, 'steel')
    c.glow(glass, '#3FB6FF', 0.35)


DEVICES = {
    'dev_royal_potion': royal_potion,
    'dev_crystal_shield': crystal_shield,
    'dev_jelly_lamp': jelly_lamp,
}


# =============================================================== projectiles

def proj_acid(c):
    blob = c.ellipse(11, 10, 5, 5)
    c.fill(blob, 'acid', shade='radial')
    c.color(c.rect(3, 10, 5, 10), '#55C23F', 0.7)
    c.glow(blob, '#7DFF4F', 0.4)


def proj_shard(c):
    shard = c.poly([(2, 5), (18, 2), (26, 5), (18, 8)])
    c.fill(shard, ['#7D55D6', '#A684EA', '#D4C2FF', '#FFFFFF'], shade='radial', light=(20, 4), radius=14)
    c.color(c.rect(12, 5, 22, 5), '#FFFFFF', 0.9)
    c.glow(shard, '#C77DFF', 0.35)


def proj_fire(c):
    ball = c.ellipse(21, 9, 7, 7)
    c.fill(ball, 'fire', shade='radial')
    tail = c.poly([(4, 9), (15, 5), (15, 13)])
    c.fill(tail, 'fire', shade='flat', tones=1, alpha=0.8)
    c.glow(ball, '#FF7A1A', 0.4)


def proj_abyss(c):
    tail = c.poly([(4, 8), (30, 5), (30, 11)])
    c.fill(tail, ['#64308E', '#9963C6', '#C9A0F0', '#E9D8FF'], shade='flat', tones=1, alpha=0.55)
    head = c.ellipse(36, 8, 8, 4)
    c.fill(head, ['#9963C6', '#C9A0F0', '#EBDDFF', '#FFFFFF'], shade='radial', light=(39, 7), radius=10)
    c.glow(head, '#B23CFF', 0.4)


PROJECTILES = {
    # name: (drawing, logical canvas size) -> scaled x2 (same sizes as before)
    'proj_acid_bolt': (proj_acid, (20, 20)),
    'proj_crystal_shard': (proj_shard, (28, 10)),
    'proj_pyre_ball': (proj_fire, (32, 19)),
    'proj_abyss_bolt': (proj_abyss, (48, 16)),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in WEAPONS.items():
        hand, icon = weapon_images(fn)
        hand.save(os.path.join(OUT, name + '.png'))
        icon.save(os.path.join(OUT, name + '_icon.png'))
    for group in (MATERIALS, DEVICES):
        for name, fn in group.items():
            c = Canvas(LOGICAL, LOGICAL, seed=7)
            fn(c)
            c.finish().save(os.path.join(OUT, name + '.png'))
    for name, (fn, (w, h)) in PROJECTILES.items():
        c = Canvas(w, h, seed=5)
        fn(c)
        c.finish(outline=False).save(os.path.join(OUT, name + '.png'))  # projectiles glow instead
    print('items written to', os.path.abspath(OUT))


if __name__ == '__main__':
    main()
