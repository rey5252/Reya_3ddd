"""Builds all of Quantum Solar's blocks from reference/panels.jpg (needs numpy and Pillow).

Run from the quantumsolar folder:  python3 tools/build.py
Every face is read off the picture (tools/strips.py says where each lies; tools/rectify.py takes it
out of the picture's perspective, each texel the mean of the picture's pixels over its middle). Where
the picture shows a face too far off or cut by the corner to be read, the nearest clear face of the
same kind is taken and put into the block's own colours: the lightning sides of the grids and of the
last woven panels, the last flare side and tearful side, the sides of the serpent generators, and
the tops of the tearful panels (the picture only shows them as a sliver: their colours are read off
it and laid over a flare top's pattern) and of the core generators.

Writes the block textures, models, blockstates, loot tables, recipes, the pickaxe tag, the three
language files and the block table in Panels.java (between its "generated" marks).
"""
import colorsys
import json
import os
import re
import sys

import numpy as np
from PIL import Image, ImageEnhance

sys.path.insert(0, os.path.dirname(__file__))
from rectify import all_faces  # noqa: E402
import extract_cores  # noqa: E402

MOD = "quantumsolar"
RES = "src/main/resources"
ASSETS = f"{RES}/assets/{MOD}"
DATA = f"{RES}/data/{MOD}"
JAVA = "src/main/java/com/reya/quantumsolar/Panels.java"

# family: (kind, en, uk, ru)
FAMILIES = {
    "grid": ("solar", "Grid Solar Panel", "Сіткова сонячна панель", "Сетчатая солнечная панель"),
    "flare": ("solar", "Flare Solar Panel", "Спалахова сонячна панель", "Вспышечная солнечная панель"),
    "woven": ("solar", "Woven Solar Panel", "Плетена сонячна панель", "Плетёная солнечная панель"),
    "tearful": ("solar", "Tearful Solar Panel", "Плакуча сонячна панель", "Плачущая солнечная панель"),
    "serpent": ("quantum", "Serpent Quantum Generator", "Зміїний квантовий генератор", "Змеиный квантовый генератор"),
    "spiral": ("quantum", "Spiral Quantum Generator", "Спіральний квантовий генератор", "Спиральный квантовый генератор"),
    "meteor": ("quantum", "Meteor Quantum Generator", "Метеорний квантовий генератор", "Метеорный квантовый генератор"),
    "core": ("quantum", "Core Quantum Generator", "Ядерний квантовий генератор", "Ядерный квантовый генератор"),
}
# in tier order: family, colour id, top, side (a face name, or (face, recolour) to take that face in
# the block's colour), colour names en / uk / ru
BLOCKS = [
    ("grid", "grey", "LA0", ("FLC0", None), "Grey", "Сіра", "Серая"),
    ("grid", "blue", "LA1", ("FLC5", None), "Blue", "Синя", "Синяя"),
    ("grid", "indigo", "LA2", ("FLC5", "top"), "Indigo", "Індигова", "Индиговая"),
    ("grid", "teal", "LA3", ("FLC2", None), "Teal", "Бірюзова", "Бирюзовая"),
    ("flare", "sunset", "LB0", "FLB0", "Sunset", "Захід сонця", "Закат"),
    ("flare", "aurora", "LB1", "FLB1", "Aurora", "Полярне сяйво", "Северное сияние"),
    ("flare", "ember", "LB2", "FLB2", "Ember", "Жар", "Жар"),
    ("flare", "glyph", "LB3", "FLB3", "Glyph", "Руна", "Руна"),
    ("flare", "amber", "LB4", "FLB4", "Amber", "Бурштин", "Янтарь"),
    ("flare", "blaze", "LB5", ("FLB4", "top"), "Blaze", "Полум'я", "Пламя"),
    ("woven", "white", "LC0", "FLC0", "White", "Біла", "Белая"),
    ("woven", "red", "LC1", "FLC1", "Red", "Червона", "Красная"),
    ("woven", "teal", "LC2", "FLC2", "Teal", "Бірюзова", "Бирюзовая"),
    ("woven", "orange", "LC3", "FLC3", "Orange", "Помаранчева", "Оранжевая"),
    ("woven", "graphite", "LC4", "FLC4", "Graphite", "Графітова", "Графитовая"),
    ("woven", "azure", "LC5", "FLC5", "Azure", "Блакитна", "Голубая"),
    ("woven", "lime", "LC6", ("FLC5", "top"), "Lime", "Лаймова", "Лаймовая"),
    ("woven", "violet", "LC7", ("FLC5", "top"), "Violet", "Фіалкова", "Фиолетовая"),
    ("tearful", "sky", ("LB1", 0), "FD0", "Sky", "Небесна", "Небесная"),
    ("tearful", "meadow", ("LB0", 1), "FD1", "Meadow", "Лугова", "Луговая"),
    ("tearful", "dusk", ("LB2", 2), "FD2", "Dusk", "Сутінкова", "Сумеречная"),
    ("tearful", "sunrise", ("LB5", 3), "FD3", "Sunrise", "Світанкова", "Рассветная"),
    ("tearful", "spring", ("LB3", 4), "FD4", "Spring", "Весняна", "Весенняя"),
    ("tearful", "honey", ("LB4", 5), "FD5", "Honey", "Медова", "Медовая"),
    ("tearful", "orchid", ("LB1", 6), "FD6", "Orchid", "Орхідейна", "Орхидейная"),
    ("tearful", "twilight", ("LB2", 7), ("FD6", "top"), "Twilight", "Вечорова", "Вечерняя"),
    ("serpent", "violet", "RA0", ("FRB2", "top"), "Violet", "Фіолетовий", "Фиолетовый"),
    ("serpent", "coral", "RA1", ("FRB2", "top"), "Coral", "Кораловий", "Коралловый"),
    ("serpent", "frost", "RA2", ("FRB3", "top"), "Frost", "Морозний", "Морозный"),
    ("serpent", "cyan", "RA3", ("FRB3", "top"), "Cyan", "Ціановий", "Циановый"),
    ("serpent", "prism", "RA4", ("FRB4", None), "Prism", "Призматичний", "Призматический"),
    ("spiral", "orange", "RB0", "FRB0", "Orange", "Помаранчевий", "Оранжевый"),
    ("spiral", "gold", "RB1", "FRB1", "Gold", "Золотий", "Золотой"),
    ("spiral", "violet", "RB2", "FRB2", "Violet", "Фіолетовий", "Фиолетовый"),
    ("spiral", "lavender", "RB3", "FRB3", "Lavender", "Лавандовий", "Лавандовый"),
    ("spiral", "prism", "RB4", "FRB4", "Prism", "Призматичний", "Призматический"),
    ("meteor", "lime", ("RC2", "lime"), ("FRC1", "top"), "Lime", "Лаймовий", "Лаймовый"),
    ("meteor", "lime2", "RC1", "FRC1", "Olive", "Оливковий", "Оливковый"),
    ("meteor", "rose", "RC2", "FRC2", "Rose", "Рожевий", "Розовый"),
    ("meteor", "iris", "RC3", "FRC3", "Iris", "Ірисовий", "Ирисовый"),
    ("meteor", "magenta", "RC4", "FRC4", "Magenta", "Пурпуровий", "Пурпурный"),
    ("meteor", "carnival", "RC5", "FRC5", "Carnival", "Карнавальний", "Карнавальный"),
    ("core", "ice_heart", "FRD0", "FRD0", "Ice Heart", "Крижане серце", "Ледяное сердце"),
    ("core", "sun_heart", "FRD1", "FRD1", "Sun Heart", "Сонячне серце", "Солнечное сердце"),
    ("core", "ruby", "FRD2", "FRD2", "Ruby", "Рубіновий", "Рубиновый"),
    ("core", "jade", "FRD3", "FRD3", "Jade", "Нефритовий", "Нефритовый"),
    ("core", "topaz", "FRD4", "FRD4", "Topaz", "Топазовий", "Топазовый"),
    ("core", "amethyst", "FRD5", "FRD5", "Amethyst", "Аметистовий", "Аметистовый"),
]
# the tearful panels' tops: their colours as the sliver of them in the picture shows (main, second)
TEARFUL_COLOURS = [((78, 146, 167), (146, 159, 61)), ((180, 191, 77), (178, 163, 79)),
                   ((157, 89, 192), (115, 88, 170)), ((206, 150, 65), (195, 130, 89)),
                   ((164, 177, 82), (86, 139, 159)), ((210, 170, 70), (164, 177, 82)),
                   ((200, 90, 190), (164, 175, 76)), ((140, 88, 167), (172, 128, 69))]
# what each tier is made with round its previous tier (the first solar panel and the first
# quantum generator have their own recipes)
POWER = ["redstone", "iron_ingot", "gold_ingot", "lapis_lazuli", "redstone_block", "iron_block",
         "gold_block", "diamond", "lapis_block", "emerald", "obsidian", "blaze_rod", "ender_pearl",
         "amethyst_shard", "glowstone", "quartz_block", "prismarine_crystals", "diamond_block",
         "crying_obsidian", "ender_eye", "emerald_block", "netherite_scrap", "shulker_shell",
         "echo_shard", "netherite_ingot", "nether_star", "end_crystal", "dragon_breath",
         "heart_of_the_sea", "totem_of_undying", "netherite_block", "beacon", "sculk_catalyst",
         "recovery_compass", "nether_star", "end_crystal", "dragon_head", "netherite_block",
         "beacon", "totem_of_undying", "nether_star", "dragon_head", "netherite_block", "beacon",
         "nether_star", "beacon", "netherite_block", "dragon_head", "nether_star"]
# the stained glass nearest each block's colour, by hue
DYES = {"white": (240, 240, 240), "light_gray": (160, 160, 160), "gray": (80, 80, 80), "red": (200, 50, 50),
        "orange": (240, 130, 30), "yellow": (240, 220, 60), "lime": (130, 210, 40), "green": (70, 120, 30),
        "cyan": (40, 160, 170), "light_blue": (100, 170, 230), "blue": (50, 70, 200), "purple": (140, 60, 200),
        "magenta": (200, 70, 190), "pink": (240, 150, 180), "brown": (130, 80, 40)}


def enhance(a):
    im = Image.fromarray(a)
    im = ImageEnhance.Contrast(im).enhance(1.12)
    im = ImageEnhance.Color(im).enhance(1.15)
    return np.asarray(im)


def main_colour(a):
    """The block's colour: the mean of its most colourful pixels (its lightest, if it has none)."""
    px = a.reshape(-1, 3) / 255.0
    hsv = np.array([colorsys.rgb_to_hsv(*p) for p in px])
    score = hsv[:, 1] * hsv[:, 2]
    if score.max() < 0.15:
        pick = px[np.argsort(hsv[:, 2])[-40:]]
    else:
        pick = px[np.argsort(score)[-40:]]
    return tuple(int(v) for v in (pick.mean(axis=0) * 255).round())


def recolour(a, rgb):
    """The coloured pixels of a face in another colour, each keeping its lightness."""
    th, ts, tv = colorsys.rgb_to_hsv(*(np.array(rgb) / 255.0))
    out = a.copy()
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            h, s, v = colorsys.rgb_to_hsv(*(a[y, x] / 255.0))
            if s > 0.3 and v > 0.2:
                r, g, b = colorsys.hsv_to_rgb(th, min(1.0, max(s, ts) if ts > 0.15 else ts), v)
                out[y, x] = (np.array([r, g, b]) * 255).round()
    return out


def vivid(rgb, k=1.35):
    h, s, v = colorsys.rgb_to_hsv(*(np.array(rgb) / 255.0))
    r, g, b = colorsys.hsv_to_rgb(h, min(1.0, s * k), min(1.0, v * 1.08))
    return (r * 255, g * 255, b * 255)


def gradient_map(a, main, second):
    main, second = vivid(main), vivid(second)
    """A face's pattern in other colours: dark to light along second -> main -> white."""
    lum = a.astype(float) @ [0.3, 0.59, 0.11] / 255.0
    lo, hi = lum.min(), lum.max()
    t = (lum - lo) / max(1e-6, hi - lo)
    stops = [(0.0, np.array(second) * 0.22), (0.4, np.array(second) * 0.9), (0.75, np.array(main)),
             (1.0, np.array(main) * 0.65 + 255 * 0.35)]
    out = np.zeros_like(a, dtype=float)
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            v = t[y, x]
            for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
                if t0 <= v <= t1:
                    k = (v - t0) / (t1 - t0)
                    out[y, x] = c0 * (1 - k) + c1 * k
                    break
    return out.round().clip(0, 255).astype(np.uint8)


def core_top(front, rgb):
    """The core generators' tops (the picture shows only their edge: dark with purple, ice and white
    marks round it): the front's middle square set in that edge."""
    out = np.zeros((16, 16, 3), dtype=np.uint8)
    out[:] = (18, 16, 24)
    ring = [(170, 70, 220), (170, 70, 220), (120, 220, 230), (240, 240, 250), (26, 22, 34), (200, 90, 230)]
    k = 0
    edge = [(x, 0) for x in range(16)] + [(15, y) for y in range(1, 16)] + \
           [(x, 15) for x in range(14, -1, -1)] + [(0, y) for y in range(14, 0, -1)]
    for (x, y) in edge:
        out[y, x] = ring[(k // 2) % len(ring)]
        k += 1
    out[2:14, 2:14] = front[2:14, 2:14]
    return out


def fill_edges(a, brown=False):
    """The picture gives a front face's top and bottom rows a sliver of the tops above and below it,
    which are not this block's pixels: each such pixel is filled with the block's own pixel next in
    from the edge. Nothing else is changed."""
    out = a.copy()

    def foreign(c):
        h, s, v = colorsys.rgb_to_hsv(*(c / 255.0))
        if brown:
            return s > 0.3 and not (0.04 < h < 0.13) and v > 0.25
        return s > 0.3 and v > 0.25

    for rows, step in (((0, 1, 2), 1), ((15, 14, 13), -1)):
        for x in range(16):
            for y in rows:
                if foreign(out[y, x]):
                    k = y + step
                    while 0 < k < 15 and foreign(out[k, x]):
                        k += step
                    out[y, x] = out[k, x]
    return out


def top_colours(a, n=3):
    """A face's n most common bright colours (for redrawing it)."""
    px = [tuple(int(v) for v in c) for c in a.reshape(-1, 3)]
    bright = [c for c in px if colorsys.rgb_to_hsv(*(np.array(c) / 255.0))[1] > 0.35
              and colorsys.rgb_to_hsv(*(np.array(c) / 255.0))[2] > 0.45]
    if not bright:
        return [(200, 200, 210)] * n
    img = Image.new("RGB", (len(bright), 1))
    img.putdata(bright)
    pal = img.quantize(n, method=Image.Quantize.MEDIANCUT).getpalette()[:n * 3]
    return [tuple(pal[i * 3:i * 3 + 3]) for i in range(n)]


# the core generators' colours (their fronts are mostly dark: their own colour is in the middle)
CORE_COLOURS = {"ice_heart": (120, 220, 235), "sun_heart": (245, 150, 50), "ruby": (225, 50, 70),
                "jade": (60, 205, 150), "topaz": (245, 190, 60), "amethyst": (180, 100, 245)}
# the meteor generators' dash colours, as the picture shows them (brightened: it shows them far off)
METEOR_COLOURS = {"lime": [(170, 220, 50), (110, 190, 40), (220, 240, 120)],
                  "lime2": [(210, 215, 80), (160, 175, 60), (245, 235, 150)],
                  "rose": [(240, 90, 140), (255, 150, 190), (200, 60, 110)],
                  "iris": [(150, 120, 255), (110, 160, 255), (200, 170, 255)],
                  "magenta": [(230, 80, 220), (255, 140, 240), (180, 60, 200)],
                  "carnival": [(255, 100, 160), (255, 210, 70), (100, 180, 255)]}
# the meteor generators' dashes: (x, y, colour index) of each dash's upper end; each runs 3 down-left
METEOR_DASHES = [(4, 2, 0), (9, 1, 1), (13, 4, 2), (6, 6, 1), (11, 8, 0), (3, 10, 2), (8, 11, 0), (13, 12, 1)]


def meteor_top(colours):
    """The picture shows these tops only far off: dark, with short bright dashes falling to the left.
    Redrawn so: a dark plate, a darker rim, the dashes lit at their heads."""
    out = np.zeros((16, 16, 3), dtype=np.uint8)
    for y in range(16):
        for x in range(16):
            out[y, x] = (24, 24, 30) if (x + y) % 5 else (28, 28, 35)
            if x in (0, 15) or y in (0, 15):
                out[y, x] = (12, 12, 16)
    for x, y, k in METEOR_DASHES:
        c = np.array(colours[k % len(colours)], dtype=float)
        for i in range(3):
            shade = 1.0 - i * 0.2
            out[y + i, x - i] = (c * shade).clip(0, 255)
            out[y + i, x - i + 1] = (c * shade * 0.7).clip(0, 255)
        out[y, x] = (c * 0.5 + 127).clip(0, 255)
    return out


def meteor_side(base, colours):
    """Their fronts: the chains of the spiral generators' fronts, and between them, in place of the
    lightning, three bright slashes, as the picture shows."""
    out = base.copy()
    for y in range(16):
        for x in range(3, 13):
            h, sat, v = colorsys.rgb_to_hsv(*(out[y, x] / 255.0))
            if (sat > 0.2 and v > 0.15) or (3 <= y <= 12 and 4 <= x <= 11 and v > 0.3):
                out[y, x] = (22, 22, 28)
    for k, (x, y0) in enumerate(((5, 4), (8, 3), (11, 5))):
        c = np.array(colours[k % len(colours)], dtype=float)
        for i in range(6):
            xx = x - (i // 3)
            out[y0 + i, xx] = (c * (1.0 - i * 0.08)).clip(0, 255)
    return out


def fill_weave(a):
    """The woven tops repeat every 8 texels each way; where the picture gives a corner of one the
    shadow beside it instead (much darker than the weave), that pixel is taken from the weave 8
    texels over."""
    lum = a.astype(float) @ [0.3, 0.59, 0.11]
    med = np.median(lum)
    out = a.copy()
    dark = lum < med * 0.45
    for y in range(16):
        for x in range(16):
            if dark[y, x]:
                for dx, dy in ((8, 0), (0, 8), (8, 8)):
                    xx, yy = (x + dx) % 16, (y + dy) % 16
                    if not dark[yy, xx]:
                        out[y, x] = a[yy, xx]
                        break
    return out


def mirror_edges(a, cols=3):
    """A front's two edges are halves of the sockets that meet round the block's corners (and the
    next block's): the originals have each edge the other's mirror image. The photos give one edge
    of a block the neighbour's pixels as often as not, so the richer of the two (the one with more
    colour in it) is kept and mirrored onto the other."""
    def rich(block):
        c = block.reshape(-1, 3).astype(float)
        return ((c.max(axis=1) - c.min(axis=1)) * c.max(axis=1)).mean()
    out = a.copy()
    left, right = a[:, :cols], a[:, 16 - cols:]
    if rich(left) >= rich(right):
        out[:, 16 - cols:] = left[:, ::-1]
    else:
        out[:, :cols] = right[:, ::-1]
    return out


def straighten(fam, colour, top, side, faces):
    """The faces the picture shows crooked, drawn again straight and centred in the picture's own
    design and colours (tools/straight.py); the rest are left as the picture has them."""
    import straight as st
    if fam == "grid":
        face = faces[{"grey": "LA0", "blue": "LA1", "indigo": "LA2", "teal": "LA3"}[colour]]
        rim = tuple(int(v) for v in np.concatenate([face[0], face[15], face[:, 0], face[:, 15]]).mean(axis=0))
        cell = tuple(int(v) for v in face[5:11, 5:11].reshape(-1, 3).mean(axis=0))
        cell = vivid(cell, 1.25)
        cell = tuple(int(v) for v in cell)
        return st.top_grid(cell, rim), st.side_bolt(st.light(cell, 0.3))
    if fam == "woven":
        return fill_weave(top), side
    if fam == "flare":
        return top, side
    if fam == "tearful":
        return top, st.side_face(TEARS[colour])
    if fam in ("serpent", "spiral"):
        c = top_colours(top, 2)
        c = sorted(c, key=lambda q: sum(q))
        a = (st.top_serpent if fam == "serpent" else st.top_spiral)(tuple(int(v) for v in vivid(c[0], 1.2)),
                                                                   tuple(int(v) for v in vivid(c[1], 1.1)),
                                                                   colour == "prism")
        return a, st.side_chain_bolt(tuple(int(v) for v in vivid(main_colour(top), 1.2)))
    return top, side


# the tears on the tearful panels' fronts, as the picture shows them
TEARS = {"sky": (90, 200, 240), "meadow": (180, 220, 60), "dusk": (200, 110, 230), "sunrise": (255, 190, 70),
         "spring": (120, 230, 160), "honey": (250, 200, 60), "orchid": (240, 110, 210), "twilight": (140, 110, 240)}


def dye(rgb):
    return min(DYES, key=lambda d: sum((a - b) ** 2 for a, b in zip(DYES[d], rgb)))


def generation(kind, tier):
    if kind == "solar":
        return int(round(8 * 1.42 ** tier))
    return int(round(65536 * 1.4 ** tier))


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    extract_cores.main()
    import extract_hearts
    extract_hearts.main()
    import extract_originals
    extract_originals.main()
    import extract_bottom
    extract_bottom.main()
    faces = {n: enhance(f) for n, f in all_faces(size=16, sub=4)}
    # where the grid has been laid exactly on the face (tools/fit.py), read it from there
    import fit
    for n, q in json.load(open("tools/fitted.json")).items():
        faces[n] = enhance(fit.read(q))
    os.makedirs(f"{ASSETS}/textures/block", exist_ok=True)
    lang = {"en_us": {}, "uk_ua": {}, "ru_ru": {}}
    table = []
    tiers = {"solar": 0, "quantum": 0}
    ids = []
    for fam, colour, top_src, side_src, en, uk, ru in BLOCKS:
        kind = FAMILIES[fam][0]
        tier = tiers[kind]
        tiers[kind] += 1
        bid = f"{fam}_{'panel' if kind == 'solar' else 'generator'}_{colour}"
        ids.append(bid)
        if isinstance(top_src, tuple) and isinstance(top_src[1], str):
            # a face of the same kind in this block's colour (the picture's own is cut by the corner)
            top = recolour(faces[top_src[0]], (150, 200, 40))
        elif isinstance(top_src, tuple):
            src, k = top_src
            main, second = TEARFUL_COLOURS[k]
            top = gradient_map(faces[src], main, second)
            # no two alike: each turned or turned over its own way
            top = np.rot90(top, k % 4)
            if k >= 4:
                top = top[:, ::-1]
            top = np.ascontiguousarray(top)
        elif fam == "core":
            top = None
        else:
            top = faces[top_src]
        if isinstance(side_src, tuple):
            src, to = side_src
            side = faces[src] if to is None else recolour(faces[src], main_colour(top))
        else:
            side = faces[side_src]
        if fam == "core":
            top = core_top(side, main_colour(side))
        if fam != "core":
            side = fill_edges(side, brown=fam == "tearful")
        if fam == "meteor":
            # the picture shows these only far off: drawn as it shows them
            top = meteor_top(METEOR_COLOURS[colour])
            side = meteor_side(faces["FRB2"], METEOR_COLOURS[colour])
        top, side = straighten(fam, colour, top, side, faces)
        if fam in ("flare", "tearful"):
            # these have their originals (tools/extract_originals.py): read off them
            top = np.asarray(Image.open(f"{ASSETS}/textures/block/{fam}_panel_{colour}_top.png").convert("RGB"))[:16]
            side = np.asarray(Image.open(f"{ASSETS}/textures/block/{fam}_panel_{colour}_side.png").convert("RGB"))[:16]
        if fam == "core" and colour in ("ice_heart", "sun_heart"):
            # the hearts have their originals too (reference/hearts_*.png)
            top = np.asarray(Image.open(f"{ASSETS}/textures/block/core_generator_{colour}_top.png").convert("RGB"))[:16]
            side = np.asarray(Image.open(f"{ASSETS}/textures/block/core_generator_{colour}_side.png").convert("RGB"))[:16]
        if fam == "core" and colour in extract_cores.NAMES:
            # these four have their originals (reference/cores.png): read off them pixel for pixel
            top = side = np.asarray(Image.open(f"{ASSETS}/textures/block/core_generator_{colour}_top.png").convert("RGB"))
        rgb = main_colour(top if fam != "core" else side)
        if fam != "core":
            side = mirror_edges(side)
        Image.fromarray(top).save(f"{ASSETS}/textures/block/{bid}_top.png")
        Image.fromarray(side).save(f"{ASSETS}/textures/block/{bid}_side.png")
        write_json(f"{ASSETS}/models/block/{bid}.json", {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"{MOD}:block/{bid}_top", "side": f"{MOD}:block/{bid}_side",
                         "bottom": f"{MOD}:block/bottom"}})
        write_json(f"{ASSETS}/models/item/{bid}.json", {"parent": f"{MOD}:block/{bid}"})
        write_json(f"{ASSETS}/blockstates/{bid}.json", {"variants": {"": {"model": f"{MOD}:block/{bid}"}}})
        write_json(f"{DATA}/loot_tables/blocks/{bid}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{bid}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        glass = f"minecraft:{dye(rgb)}_stained_glass"
        index = len(table)
        if index == 0:
            recipe = {"pattern": ["GGG", "IDI", "IRI"],
                      "key": {"G": {"item": "minecraft:glass"}, "I": {"item": "minecraft:iron_ingot"},
                              "D": {"item": "minecraft:daylight_detector"}, "R": {"item": "minecraft:redstone"}}}
        else:
            recipe = {"pattern": ["GCG", "CPC", "GCG"],
                      "key": {"G": {"item": glass}, "C": {"item": f"minecraft:{POWER[index]}"},
                              "P": {"item": f"{MOD}:{ids[index - 1]}"}}}
        write_json(f"{DATA}/recipes/{bid}.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                                    **recipe, "result": {"item": f"{MOD}:{bid}"}})
        fen, fuk, fru = FAMILIES[fam][1:]
        lang["en_us"][f"block.{MOD}.{bid}"] = f"{fen} ({en})"
        lang["uk_ua"][f"block.{MOD}.{bid}"] = f"{fuk} ({uk})"
        lang["ru_ru"][f"block.{MOD}.{bid}"] = f"{fru} ({ru})"
        table.append((bid, kind == "quantum", tier, generation(kind, tier), rgb))
    write_json(f"{RES}/data/minecraft/tags/blocks/mineable/pickaxe.json",
               {"replace": False, "values": [f"{MOD}:{b}" for b in ids]})
    extra = json.load(open("tools/lang.json", encoding="utf-8"))
    for code in lang:
        lang[code].update(extra[code])
        write_json(f"{ASSETS}/lang/{code}.json", lang[code])
    import animate_cores
    animate_cores.main()
    animate_cores.animate_panels()
    java = open(JAVA, encoding="utf-8").read()
    body = "\n".join(
        f'            new Panel("{b}", {str(q).lower()}, {t}, {g}, 0x{r:02X}{gg:02X}{bb:02X}),'
        for b, q, t, g, (r, gg, bb) in table)
    block = ("    // <generated by tools/build.py>\n"
             "    public static final Panel[] ALL = {\n" + body + "\n    };\n    // </generated>")
    java = re.sub(r"    // <generated by tools/build.py>.*?// </generated>", lambda m: block, java, flags=re.S)
    open(JAVA, "w", encoding="utf-8").write(java)


if __name__ == "__main__":
    main()
