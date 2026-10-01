"""The machines' block textures (16x16, laid out for the models' automatic UVs: a face shows the texture's
pixels where the face is, see gen_models.py), and the textures their renderers draw with.

    python3 tools/machines/gen_block.py

  textures/block/machine_planks.png      livingwood planks
  textures/block/machine_bricks.png      livingrock bricks
  textures/block/machine_crystal.png     a mana crystal's facets (the little crystals on the machines)
  textures/block/rune_altar_*.png        the Runic Altar: its top (a gold ring round a dark hollow, runes in the
                                         corners), the top's rim, the body (planks between livingrock pillars,
                                         a rune of mana in gold), its plinth
  textures/block/terra_plate_*.png       the Terrestrial Plate: its top (a lapis field with a pale sun), its
                                         sides, its livingwood base
  textures/block/mana_infuser_*.png      the Mana Infuser: the basin's walls outside (a gold band), inside and
                                         on top, its column
  textures/entity/machines/glow.png      a soft round glow (white on black: the renderers add it)
  textures/entity/machines/altar_glow.png    the altar top's ring and runes, lit (added over the top)
  textures/entity/machines/plate_glow.png    the plate's sun, lit
  textures/entity/machines/beam.png      a column of light fading upwards
  textures/entity/machines/mana.png      the infuser's mana, 16 frames one under another
"""
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc, rnd2

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import wood as WD  # noqa: E402

WOOD, LR, MANA, GOLD = WD.WOOD, WD.LR, WD.MANA, WD.GOLD
LAPIS = [hexc(h) for h in ("4A7CE0", "3361C6", "254CA8", "1B3A88", "122966")]
TEAL = [hexc(h) for h in ("D8FFF6", "8FF0DE", "4FC9C4", "2C8F98")]
BLOCK = os.path.join(ASSETS, "textures", "block")
ENTITY = os.path.join(ASSETS, "textures", "entity", "machines")
MANA_FRAMES = 16


def tex():
    return Canvas(16, 16)


def planks_px(x, y, salt=0):
    """Livingwood planks: rows 4 high with dark seams, staggered ends, a grain."""
    row = y // 4
    if y % 4 == 3:
        return WOOD[5]
    off = (row * 5 + salt) % 16
    if (x + off) % 16 == 0:
        return WOOD[5]
    c = WOOD[2] if row % 2 else WOOD[3]
    g = rnd2(x // 3, y, 11 + row + salt)
    if g < 0.2:
        c = mix(c, WOOD[4], 0.5)
    elif g > 0.82:
        c = mix(c, WOOD[1], 0.4)
    if y % 4 == 0:
        c = mix(c, WOOD[1], 0.25)
    return c


def bricks_px(x, y):
    """Livingrock bricks 8x4, staggered, lit from the top-left."""
    row = y // 4
    bx = (x + (4 if row % 2 else 0)) % 8
    by = y % 4
    if by == 3 or bx == 7:
        return LR[3]
    if by == 0 or bx == 0:
        return LR[0]
    c = LR[1]
    if rnd2(x, y, 5) < 0.15:
        c = LR[2]
    return c


def planks():
    cv = tex()
    for y in range(16):
        for x in range(16):
            cv.set(x, y, planks_px(x, y))
    cv.save(os.path.join(BLOCK, "machine_planks.png"))


def bricks():
    cv = tex()
    for y in range(16):
        for x in range(16):
            cv.set(x, y, bricks_px(x, y))
    cv.save(os.path.join(BLOCK, "machine_bricks.png"))


def crystal():
    """Facets of mana crystal: diagonal bands, light at the top-left, a glint."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            k = (x + y) % 8
            c = MANA[1] if k < 2 else (MANA[2] if k < 5 else MANA[3])
            if (x - y) % 8 == 0:
                c = mix(c, MANA[0], 0.5)
            cv.set(x, y, c)
    cv.set(1, 1, MANA[0])
    cv.save(os.path.join(BLOCK, "machine_crystal.png"))


# ---------------------------------------------------------------- the Runic Altar

ALTAR_RING_R = 5.6
ALTAR_HOLLOW_R = 2.6
CORNER_GLYPHS = [["#.#", ".#.", "#.#"], [".#.", "###", ".#."], ["##.", "#.#", ".##"], ["#..", "###", "..#"]]
CORNERS = [(2, 2), (11, 2), (2, 11), (11, 11)]


def altar_top_parts(x, y):
    """What the altar's top has at the pixel: 'ring', 'hollow', 'rune' or None (plain livingrock)."""
    d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
    if abs(d - ALTAR_RING_R) < 0.6:
        return "ring"
    if d < ALTAR_HOLLOW_R:
        return "hollow"
    for (gx, gy), g in zip(CORNERS, CORNER_GLYPHS):
        if gx <= x < gx + 3 and gy <= y < gy + 3 and g[y - gy][x - gx] == "#":
            return "rune"
    return None


def altar_top():
    cv = tex()
    for y in range(16):
        for x in range(16):
            part = altar_top_parts(x, y)
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if x in (0, 15) or y in (0, 15):
                c = GOLD[2] if (x + y) % 2 else GOLD[3]
            elif part == "ring":
                c = GOLD[1] if (x + y) < 15 else GOLD[2]
            elif part == "hollow":
                c = mix(MANA[4], MANA[5], d / ALTAR_HOLLOW_R)
            elif part == "rune":
                c = LR[3]
            else:
                c = LR[1] if rnd2(x, y, 7) > 0.12 else LR[2]
                if abs(d - ALTAR_RING_R) < 1.3:
                    c = mix(c, LR[3], 0.3)
            cv.set(x, y, c)
    cv.set(7, 7, MANA[1])
    cv.save(os.path.join(BLOCK, "rune_altar_top.png"))
    glow = tex()
    for y in range(16):
        for x in range(16):
            part = altar_top_parts(x, y)
            if part in ("ring", "rune"):
                glow.set(x, y, (255, 255, 255))
            elif part == "hollow":
                glow.set(x, y, (150, 150, 150))
    glow.save(os.path.join(ENTITY, "altar_glow.png"))


def altar_rim():
    """The top's sides (rows 4 to 6 show): a lit edge, a gold band, a shaded edge."""
    cv = tex()
    rows = [LR[0], GOLD[1], GOLD[3]]
    for y in range(16):
        for x in range(16):
            k = (y - 4) % 3
            c = rows[k]
            if k == 1 and x % 4 == 2:
                c = GOLD[0]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "rune_altar_rim.png"))


def altar_body():
    """The body's sides (rows 7 to 13, columns 2 to 13 show): planks between livingrock pillars, a gold
    rune of mana in the middle."""
    cv = tex()
    glyph = ["..#..", ".#.#.", "#...#", ".#.#.", "..#.."]
    for y in range(16):
        for x in range(16):
            if x in (2, 3, 12, 13):
                c = LR[0] if x in (2, 12) else LR[2]
                if y % 4 == 3:
                    c = LR[3]
            else:
                c = planks_px(x, y, 3)
            cv.set(x, y, c)
    for j, row in enumerate(glyph):
        for i, ch in enumerate(row):
            if ch == "#":
                cv.set(6 + i, 8 + j, GOLD[1] if j < 2 else GOLD[2])
    cv.save(os.path.join(BLOCK, "rune_altar_body.png"))


# ---------------------------------------------------------------- the Terrestrial Plate

PLATE_RING_R = 4.4


def plate_parts(x, y):
    px, py = x + 0.5 - 8, y + 0.5 - 8
    d = math.hypot(px, py)
    if d < 1.2:
        return "core"
    if abs(d - PLATE_RING_R) < 0.45:
        return "ring"
    if 2.0 <= d < 6.2:
        ang = math.atan2(py, px)
        k = round(ang / (math.pi / 4.0))
        if k % 2 == 0 and abs(ang - k * math.pi / 4.0) * d < 0.5:
            return "ray"
    return None


def plate_top():
    cv = tex()
    glow = tex()
    for y in range(16):
        for x in range(16):
            part = plate_parts(x, y)
            if x in (1, 14) or y in (1, 14):
                c = LR[0] if (x == 1 or y == 1) else LR[3]
            elif x in (0, 15) or y in (0, 15):
                c = GOLD[2]
            elif part == "core":
                c = TEAL[1]
            elif part == "ring":
                c = TEAL[3]
            elif part == "ray":
                c = TEAL[2]
            else:
                c = mix(LAPIS[1], LAPIS[3], y / 15.0)
                if rnd2(x, y, 13) < 0.14:
                    c = mix(c, LAPIS[0] if rnd2(x, y, 14) < 0.5 else LAPIS[4], 0.5)
            cv.set(x, y, c)
            if part is not None:
                glow.set(x, y, (255, 255, 255) if part != "ring" else (200, 200, 200))
    cv.save(os.path.join(BLOCK, "terra_plate_top.png"))
    glow.save(os.path.join(ENTITY, "plate_glow.png"))


def plate_side():
    """The plate's sides (rows 11 and 12 show): livingrock with gold dots, its shaded lower edge."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            k = y % 2
            c = (GOLD[1] if x % 4 == 1 else LR[1]) if k == 1 else LR[3]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "terra_plate_side.png"))


def plate_base():
    """The base's sides (rows 13 to 15 show): a livingrock edge over livingwood."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            c = planks_px(x, y, 7)
            if y == 13:
                c = LR[0] if x % 8 else LR[2]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "terra_plate_base.png"))


# ---------------------------------------------------------------- the Mana Infuser

def infuser_side():
    """The basin's outer walls (rows 4 to 9 show): a lit lip, a gold band, livingrock bricks, the floor's edge."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            if y <= 4:
                c = LR[0]
            elif y == 5:
                c = LR[1]
            elif y == 6:
                c = GOLD[1] if x % 3 else GOLD[0]
            elif y == 7:
                c = GOLD[3]
            elif y == 8:
                c = LR[1] if (x + 2) % 6 else LR[3]
            else:
                c = LR[3] if y == 9 else bricks_px(x, y)
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "mana_infuser_side.png"))


def infuser_inner():
    cv = tex()
    for y in range(16):
        for x in range(16):
            c = LR[3] if rnd2(x, y, 17) > 0.15 else LR[4]
            if y >= 8:
                c = mix(c, MANA[4], 0.35)
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "mana_infuser_inner.png"))


def infuser_rim():
    cv = tex()
    for y in range(16):
        for x in range(16):
            c = LR[0] if rnd2(x, y, 19) > 0.2 else LR[1]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "mana_infuser_rim.png"))


def infuser_column():
    """The column under the basin (rows 10 to 13, columns 4 to 11 show): planks with gold rings top and bottom."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            c = planks_px(x, y, 5)
            if y in (10, 13):
                c = GOLD[1] if y == 10 else GOLD[3]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "mana_infuser_column.png"))


# ---------------------------------------------------------------- the Pure Daisy, the Petal Apothecary, the Petal Farm

GRASS = [hexc(h) for h in ("9BE36A", "6FC24A", "4E9E38", "37782C", "245420")]
SOIL = [hexc(h) for h in ("5A3B22", "43291A", "2E1C12", "1C110B")]
PETALS = [hexc(h) for h in ("FFFFFF", "FFB3DE", "FFE066", "B9A3FF", "8FD8FF", "FF8A80")]


def daisy_top():
    """The planter's top (columns and rows 1 to 14 show): a livingrock rim round a bed of grass with tiny flowers."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = LR[2]
            elif x in (1, 14) or y in (1, 14):
                c = LR[0] if (x == 1 or y == 1) else LR[3]
            else:
                c = GRASS[1] if rnd2(x, y, 31) > 0.3 else GRASS[2]
                if rnd2(x, y, 32) > 0.9:
                    c = GRASS[0]
                if rnd2(x, y, 33) < 0.05:
                    c = PETALS[int(rnd2(x, y, 34) * len(PETALS))]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "pure_daisy_top.png"))


def daisy_side():
    """The planter's sides: its livingrock rim (rows 7 and 8) with a gold band, planks under it (rows 9 to 14),
    its foot (row 15)."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            if y <= 7:
                c = LR[0] if y == 7 else LR[1]
            elif y == 8:
                c = GOLD[1] if x % 4 else GOLD[0]
            elif y == 15:
                c = bricks_px(x, y)
            else:
                c = planks_px(x, y, 9)
                if x in (2, 13):
                    c = LR[1] if x == 2 else LR[3]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "pure_daisy_side.png"))


def apothecary_side():
    """The bowl's outer walls (rows 3 to 8 show): a lit lip, a band of petals in relief, a gold band, the floor's edge."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            if y <= 3:
                c = LR[0]
            elif y == 4:
                c = LR[1]
            elif y in (5, 6):
                c = LR[1]
                k = x % 4
                if (y == 5 and k in (1, 2)) or (y == 6 and k in (0, 1, 2, 3) and k != 3):
                    c = mix(PETALS[1 + (x // 4) % 4], LR[1], 0.35 if y == 6 else 0.1)
            elif y == 7:
                c = GOLD[1] if x % 3 else GOLD[0]
            elif y == 8:
                c = GOLD[3]
            else:
                c = LR[3] if y == 9 else bricks_px(x, y)
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "petal_apothecary_side.png"))


def apothecary_inner():
    cv = tex()
    for y in range(16):
        for x in range(16):
            c = LR[3] if rnd2(x, y, 37) > 0.15 else LR[4]
            if y >= 6:
                c = mix(c, hexc("2E86A8"), 0.3)
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "petal_apothecary_inner.png"))


def farm_side():
    """The planter's sides (rows 9 to 15 show): a livingrock edge, planks with a gold band, a dark foot."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            if y <= 9:
                c = LR[0] if y == 9 else LR[1]
            elif y == 13:
                c = GOLD[1] if x % 4 else GOLD[0]
            elif y == 15:
                c = WOOD[5]
            else:
                c = planks_px(x, y, 11)
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "petal_farm_side.png"))


def farm_soil():
    """The planter's soil, rich and dark, a few sprouts in it."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            c = SOIL[1] if rnd2(x, y, 41) > 0.3 else SOIL[2]
            if rnd2(x, y, 42) > 0.88:
                c = SOIL[0]
            if rnd2(x, y, 43) < 0.04:
                c = GRASS[2]
            if x in (0, 15) or y in (0, 15):
                c = LR[1]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "petal_farm_soil.png"))


# ---------------------------------------------------------------- the Orechid Mine, the Crop Field

ROCK = [hexc(h) for h in ("A9A9A9", "8E8E8E", "767676", "5E5E5E", "434343")]
ORE_FLECKS = [hexc(h) for h in ("E8B37F", "D8D8D8", "FCEE4B", "5DECF5", "17DD62", "345EC3", "FF2A2A")]


def mine_top():
    """The pit's top (columns and rows 1 to 14 show): a livingrock rim round raw rock with flecks of ore."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = GOLD[2]
            elif x in (1, 14) or y in (1, 14):
                c = LR[0] if (x == 1 or y == 1) else LR[3]
            else:
                n = rnd2(x // 2, y // 2, 51)
                c = ROCK[1] if n > 0.6 else (ROCK[2] if n > 0.25 else ROCK[3])
                if rnd2(x, y, 52) < 0.09:
                    c = ORE_FLECKS[int(rnd2(x, y, 53) * len(ORE_FLECKS))]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "orechid_mine_top.png"))


def mine_side():
    """The pit's sides (rows 6 to 15 show): a livingrock edge with a gold band, then livingrock bricks flecked with ore."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            if y <= 6:
                c = LR[0]
            elif y == 7:
                c = GOLD[1] if x % 3 else GOLD[0]
            elif y == 8:
                c = GOLD[3]
            else:
                c = bricks_px(x, y)
                if rnd2(x, y, 54) < 0.05:
                    c = ORE_FLECKS[int(rnd2(x, y, 55) * len(ORE_FLECKS))]
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "orechid_mine_side.png"))


def field_top():
    """The field's top: tilled soil in furrows, damp, a livingrock rim."""
    cv = tex()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = LR[1]
            else:
                k = y % 4
                c = SOIL[0] if k == 0 else (SOIL[1] if k in (1, 3) else SOIL[3])
                if rnd2(x, y, 56) < 0.12:
                    c = mix(c, SOIL[2], 0.6)
            cv.set(x, y, c)
    cv.save(os.path.join(BLOCK, "crop_field_top.png"))


# ---------------------------------------------------------------- the renderers' textures

def glow():
    """A soft round glow, white fading to black (the renderers add it: black adds nothing)."""
    cv = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16.0
            a = max(0.0, 1.0 - d) ** 2.2
            v = int(round(255 * a))
            if v > 0:
                cv.set(x, y, (v, v, v), max(v, 1))
    cv.save(os.path.join(ENTITY, "glow.png"))


def beam():
    """A column of light: bright in its middle, fading to its sides and upwards."""
    cv = Canvas(16, 64)
    for y in range(64):
        up = (y / 63.0) ** 0.7                     # v = 0 is the top
        for x in range(16):
            side = 1.0 - abs(x + 0.5 - 8) / 8.0
            v = int(round(255 * up * side ** 1.6))
            if v > 0:
                cv.set(x, y, (v, v, v), max(v, 1))
    cv.save(os.path.join(ENTITY, "beam.png"))


def mana():
    """The infuser's mana: Botania's blue with light running over it in waves and a few sparkles, frame
    after frame (16 frames of 16x16 one under another; the renderer steps through them)."""
    cv = Canvas(16, 16 * MANA_FRAMES)
    for f in range(MANA_FRAMES):
        t = f / float(MANA_FRAMES)
        for y in range(16):
            for x in range(16):
                a = math.sin((x * 0.55 + y * 0.35) + t * math.tau) + 0.6 * math.sin((x * -0.3 + y * 0.6) - t * math.tau * 2)
                k = (a + 1.6) / 3.2
                c = mix(MANA[3], MANA[2], k)
                if k > 0.82:
                    c = mix(c, MANA[1], (k - 0.82) / 0.18)
                if rnd2(x, y + f * 16, 23) < 0.012:
                    c = MANA[0]
                cv.set(x, f * 16 + y, c)
    cv.save(os.path.join(ENTITY, "mana.png"))


def main():
    planks()
    bricks()
    crystal()
    altar_top()
    altar_rim()
    altar_body()
    plate_top()
    plate_side()
    plate_base()
    infuser_side()
    infuser_inner()
    infuser_rim()
    infuser_column()
    daisy_top()
    daisy_side()
    apothecary_side()
    apothecary_inner()
    farm_side()
    farm_soil()
    mine_top()
    mine_side()
    field_top()
    glow()
    beam()
    mana()


if __name__ == "__main__":
    main()
