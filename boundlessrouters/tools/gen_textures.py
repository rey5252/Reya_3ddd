"""Draws the mod's textures: the two GUI panels and their widget sheet, the router block, the module and upgrade
items, the logo.

    python3 tools/gen_textures.py

The panels are drawn on the numbers in layout.py, the same the menus and screens use.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import layout as L  # noqa: E402
from art import *  # noqa: E402,F401,F403

ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "boundlessrouters")
TEX = os.path.join(ASSETS, "textures")

MODULES = ["sender", "puller", "distributor", "dropper", "flinger", "placer", "breaker", "vacuum", "void", "player", "detector",
           "extruder"]
MODULE_COLOURS = {"sender": 0x5FD45F, "puller": 0x4FA8FF, "distributor": 0x3FE0D0, "dropper": 0xC9C9C9, "flinger": 0xFF9A3C,
                  "placer": 0x9BE15D, "breaker": 0xFF5A4F, "vacuum": 0xC07CFF, "void": 0x8A5CD0, "player": 0xFFD24A,
                  "detector": 0xFF3B3B, "extruder": 0xD29A5C}
UPGRADES = ["speed", "stack", "range", "muffler"]
UPGRADE_COLOURS = {"speed": 0xFFD84A, "stack": 0xE8E8F0, "range": 0x4FE3FF, "muffler": 0x9A8F86}


def out(*parts):
    path = os.path.join(TEX, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


# ------------------------------------------------------------------ the panels

def machine_and_inventory(cv, mh, h, seed):
    """A machine area mh tall, the player's inventory panel under it, joined."""
    w = L.W
    px = (w - L.INV_W) // 2
    interior(cv, 6, 6, w - 6, mh - 6, seed=seed)
    steel_frame(cv, 0, 0, w, mh, seed=seed + 10)
    interior(cv, px + 6, mh - 6, px + L.INV_W - 6, h - 6, seed=seed + 1, grid=False)
    steel_frame(cv, px, mh - 6, px + L.INV_W, h, seed=seed + 20)
    # open the join between the two: the machine's inside runs into the inventory's
    for y in range(mh - 6, mh):
        for x in range(px + 6, px + L.INV_W - 6):
            cv.set(x, y, mix(BASE, BASE_LO, (y - (mh - 6)) / 6.0))
    for y in range(mh - 6, mh + 1):
        cv.set(px + 5, y, OUTLINE)
        cv.set(px + L.INV_W - 6, y, OUTLINE)
        cv.set(px + 4, y, STEEL_LO)
        cv.set(px + L.INV_W - 5, y, STEEL_HI)
    for (x, y) in ((3, 3), (w - 4, 3), (3, mh - 4), (w - 4, mh - 4), (px + 3, h - 4), (px + L.INV_W - 4, h - 4)):
        rivet(cv, x, y)


def header(cv):
    x1, y1, x2, y2 = L.HEADER
    for y in range(y1, y2):
        cv.rect(x1, y, x2, y + 1, mix(HEADER, BASE_LO, (y - y1) / (y2 - y1)))
    for x in range(x1, x2):
        cv.set(x, y2 - 1, COPPER_LO)
        cv.set(x, y2, OUTLINE)
    rivet(cv, x1 + 4, (y1 + y2) // 2)
    rivet(cv, x2 - 5, (y1 + y2) // 2)


def inventory_slots(cv, inv_x, inv_y, hot_y):
    for r in range(3):
        for c in range(9):
            slot(cv, inv_x + c * 18, inv_y + r * 18)
    for c in range(9):
        slot(cv, inv_x + c * 18, hot_y)
    # a copper line between the inventory and the hotbar
    for x in range(inv_x - 1, inv_x + 9 * 18 - 1):
        cv.set(x, hot_y - 4, COPPER_LO)
        cv.set(x, hot_y - 3, mix(COPPER, BASE, 0.5))


def router_panel():
    R = L.Router
    cv = Canvas(256, 256)
    machine_and_inventory(cv, R.MH, R.H, 3)
    header(cv)
    cx, cy = R.CORE

    # traces from the core to the modules, the upgrades and the readouts (under everything else)
    bus = R.BUS_Y
    first, last = R.MODULES[0][0] + 8, R.MODULES[-1][0] + 8
    trace(cv, [(cx - 1, cy + 20), (cx - 1, bus)])
    trace(cv, [(first - 1, bus), (last - 1, bus)])
    for (mx, my) in R.MODULES:
        trace(cv, [(mx + 7, bus), (mx + 7, my - 2)])
    ux = R.UPGRADES[0][0]
    rail = ux - 8
    trace(cv, [(cx + 20, cy - 1), (rail, cy - 1)])
    trace(cv, [(rail, R.UPGRADES[0][1] + 7), (rail, R.UPGRADES[-1][1] + 7)])
    for (_, uy) in R.UPGRADES:
        trace(cv, [(rail, uy + 7), (ux - 3, uy + 7)])
    trace(cv, [(cx - 21, cy - 1), (92, cy - 1), (92, 64), (90, 64)])

    # the core: a copper ring round the buffer, a groove outside it for the tick ring
    def core(d, a):
        if d > R.CORE_R + 1.6:
            return None
        if d > R.CORE_R + 0.8:
            return OUTLINE
        if d > R.CORE_R - 1.0:
            return BASE_LO
        if d > R.CORE_R - 1.8:
            return OUTLINE
        if d > R.CORE_R - 4.4:
            light = 0.5 - 0.5 * math.cos(a + math.pi / 4)        # lit from the top left
            notch = abs(((a / (math.pi / 4)) % 1.0) - 0.5) > 0.42
            c = mix(COPPER_HI, COPPER_LO, light)
            return shade(c, 0.6) if notch else c
        if d > R.CORE_R - 5.2:
            return OUTLINE
        return mix(SLOT_BG, BASE_LO, d / (R.CORE_R - 5.2))
    disc(cv, cx, cy, R.CORE_R + 1.6, core)
    slot(cv, R.BUFFER[0], R.BUFFER[1], big=True)

    # junction pads
    pad(cv, cx, bus + 1)
    pad(cv, rail + 1, cy)
    for (mx, my) in R.MODULES:
        cv.set(mx + 7, my - 2, COPPER_HI)
        cv.set(mx + 8, my - 2, COPPER_HI)

    # the redstone button's seat and its readout, the three readouts
    plate(cv, R.REDSTONE[0] - 2, R.REDSTONE[1] - 2, R.REDSTONE[0] + 18, R.REDSTONE[1] + 18)
    lcd(cv, R.REDSTONE[0] + 21, R.REDSTONE[1] + 1, 88, R.REDSTONE[1] + 15)
    lcd(cv, 9, R.INFO[0][1] - 1, 88, R.INFO[-1][1] + 11)
    for (ix, iy) in R.INFO[1:]:
        for x in range(9, 88):
            cv.set(x, iy - 1, mix(LCD, TEAL_DK, 0.35))

    # a light for each module, as on the block's front
    lx1, ly1 = R.LEDS[0]
    lx2, ly2 = R.LEDS[-1]
    plate(cv, lx1 - 4, ly1 - 4, lx2 + R.LED + 4, ly2 + R.LED + 4, fill=(14, 18, 22))
    for (x, y) in R.LEDS:
        cv.rect(x, y, x + R.LED, y + R.LED, (30, 46, 46))
        cv.bevel(x, y, x + R.LED, y + R.LED, OUTLINE, (52, 70, 70))

    # the module slots, a seat for each gear under them
    for (mx, my) in R.MODULES:
        slot(cv, mx, my)
    for (gx, gy) in R.GEARS:
        plate(cv, gx - 1, gy - 1, gx + R.GEAR + 1, gy + R.GEAR + 1)

    # the upgrade column
    copper_frame(cv, ux - 4, R.UPGRADES[0][1] - 4, ux + 20, R.UPGRADES[-1][1] + 20)
    for y in range(R.UPGRADES[0][1] - 2, R.UPGRADES[-1][1] + 18):
        for x in range(ux - 2, ux + 18):
            cv.set(x, y, BASE_LO)
    for (x, y) in R.UPGRADES:
        slot(cv, x, y)

    inventory_slots(cv, R.INV_X, R.INV_Y, R.HOTBAR_Y)
    cv.save(out("gui", "router.png"))
    return cv


def module_panel():
    M = L.Module
    cv = Canvas(256, 256)
    machine_and_inventory(cv, M.MH, M.H, 7)
    header(cv)
    # the module's icon sits in the header, on a little plate
    ix, iy = M.ICON
    plate(cv, ix - 1, iy - 1, ix + 17, iy + 15, fill=(16, 18, 22))
    # the direction picker's seat: an unfolded cube, its cells joined by engraved lines
    plate(cv, 9, 23, 67, 91)
    for name, (x, y) in M.DIRS.items():
        cv.outline(x - 1, y - 1, x + M.DIR_CELL + 1, y + M.DIR_CELL + 1, mix(BASE_LO, TEAL_DK, 0.6))
    lcd(cv, 12, M.DIR_LABEL[1] - 4, 65, M.DIR_LABEL[1] + 5)
    # the filter: its slots on a copper-framed plate
    copper_frame(cv, 73, 23, 137, 87)
    cv.rect(75, 25, 135, 85, BASE_LO)
    for (x, y) in M.FILTER:
        slot(cv, x, y)
    # seats for the toggles, and the readout under them
    for (x, y) in M.TOGGLES:
        plate(cv, x - 1, y - 1, x + M.TOGGLE + 1, y + M.TOGGLE + 1)
    lcd(cv, *M.INFO)
    # its own settings
    x1, y1, x2, y2 = M.PANEL
    copper_frame(cv, x1 - 2, y1 - 2, x2 + 2, y2 + 2)
    cv.rect(x1, y1, x2, y2, BASE_LO)
    noise(cv, x1, y1, x2, y2, 1.5, 17)
    inventory_slots(cv, M.INV_X, M.INV_Y, M.HOTBAR_Y)
    cv.save(out("gui", "module.png"))
    return cv


# ------------------------------------------------------------------ the widget sheet

def button(cv, x, y, s, state):
    """A steel button s square: 0 normal, 1 hovered, 2 selected (lit), 3 disabled."""
    if state == 3:
        cv.rect(x, y, x + s, y + s, STEEL_DK)
        cv.bevel(x, y, x + s, y + s, STEEL_LO, OUTLINE)
        return
    if state == 2:
        for j in range(s):
            cv.rect(x, y + j, x + s, y + j + 1, mix(TEAL_DK, shade(TEAL_DK, 0.6), j / s))
        cv.outline(x, y, x + s, y + s, OUTLINE)
        cv.bevel(x + 1, y + 1, x + s - 1, y + s - 1, TEAL, shade(TEAL, 0.45))
        return
    top, bottom = (STEEL, STEEL_LO) if state == 0 else (mix(STEEL, STEEL_HI, 0.5), STEEL_MID)
    for j in range(s):
        cv.rect(x, y + j, x + s, y + j + 1, mix(top, bottom, j / s))
    cv.outline(x, y, x + s, y + s, OUTLINE)
    cv.bevel(x + 1, y + 1, x + s - 1, y + s - 1, STEEL_HI if state == 0 else WHITE, STEEL_DK)
    if state == 1:
        for i in range(2, s - 2):
            cv.set(x + i, y + s - 2, mix(STEEL_DK, TEAL, 0.6))


def toggle(cv, x, y, s, on, hover):
    if on:
        for j in range(s):
            cv.rect(x, y + j, x + s, y + j + 1, mix(shade(TEAL, 0.55 if not hover else 0.7), shade(TEAL, 0.3), j / s))
        cv.outline(x, y, x + s, y + s, OUTLINE)
        cv.bevel(x + 1, y + 1, x + s - 1, y + s - 1, TEAL, shade(TEAL, 0.35))
    else:
        cv.rect(x, y, x + s, y + s, mix(BASE_LO, BASE_HI, 0.5 if hover else 0.2))
        cv.outline(x, y, x + s, y + s, OUTLINE)
        cv.bevel(x + 1, y + 1, x + s - 1, y + s - 1, SLOT_SHADOW, mix(SLOT_LIGHT, TEAL, 0.4 if hover else 0.0))


G = {}  # glyph art: name -> (rows, palette)


def glyph(name, rows, palette):
    G[name] = (rows, palette)


W_ = WHITE
pal_w = {"#": WHITE, "+": GREY, "o": OUTLINE}
pal_r = {"#": RED, "+": RED_DK, "o": OUTLINE, "w": WHITE, "s": (120, 86, 50)}

glyph("gear", ["..#..#..", ".######.", "##+..+##", ".#.oo.#.", ".#.oo.#.", "##+..+##", ".######.", "..#..#.."],
      {"#": GREY, "+": STEEL_LO, "o": OUTLINE})
glyph("back", ["....#.......", "...##.......", "..#########.", ".##########.", "..#########.", "...##.......", "....#......."],
      {"#": WHITE})
# redstone modes, 12 square
glyph("rs_always", ["............", "...######...", "..#......#..", ".#..####..#.", ".#.#....#.#.", ".#.#....#.#.",
                    ".#.#....#.#.", ".#..####..#.", "..#......#..", "...######...", "............", "............"],
      {"#": GREY})
glyph("rs_high", [".....##.....", "....#ww#....", "....#ww#....", ".....##.....", ".....ss.....", ".....ss.....",
                  ".....ss.....", ".....ss.....", ".....ss.....", "....ssss....", "............", "............"],
      {"#": RED, "w": (255, 210, 190), "s": (150, 104, 60)})
glyph("rs_low", [".....++.....", "....+oo+....", "....+oo+....", ".....++.....", ".....ss.....", ".....ss.....",
                 ".....ss.....", ".....ss.....", ".....ss.....", "....ssss....", "............", "............"],
      {"+": RED_DK, "o": (60, 14, 12), "s": (120, 84, 50)})
glyph("rs_never", ["...######...", "..#......#..", ".#........#.", "#.##.......#", "#..##......#", "#...##.....#",
                   "#....##....#", "#.....##...#", ".#.....##.#.", "..#......#..", "...######...", "............"],
      {"#": RED})
glyph("rs_pulse", ["............", "............", "...####.....", "...#..#.....", "...#..#.....", "...#..#.....",
                   "...#..#.....", "...#..#.....", "####..######", "............", "............", "............"],
      {"#": RED})
# readouts, 10 square
glyph("i_clock", ["..######..", ".#......#.", "#....#...#", "#....#...#", "#....#...#", "#....###.#", "#........#",
                  "#........#", ".#......#.", "..######.."], {"#": TEAL})
glyph("i_stack", ["..........", ".#######..", ".#.....#..", ".#######..", "..#######.", "..#.....#.", "..#######.",
                  ".#######..", ".#.....#..", ".#######.."], {"#": TEAL})
glyph("i_range", ["....##....", "...#..#...", "..#.##.#..", ".#.#..#.#.", ".#.#..#.#.", "..#.##.#..", "...#..#...",
                  "....##....", "....##....", "....##...."], {"#": TEAL})
# directions, 10 square, in RelativeDirection's order
glyph("d_none", ["..######..", ".#......#.", "#......#.#", "#.....#..#", "#....#...#", "#...#....#", "#..#.....#",
                 "#.#......#", ".#......#.", "..######.."], {"#": GREY})
glyph("d_front", ["##########", "#........#", "#.######.#", "#.#....#.#", "#.#.##.#.#", "#.#.##.#.#", "#.#....#.#",
                  "#.######.#", "#........#", "##########"], {"#": WHITE})
glyph("d_back", ["##########", "#........#", "#.#....#.#", "#..#..#..#", "#...##...#", "#...##...#", "#..#..#..#",
                 "#.#....#.#", "#........#", "##########"], {"#": GREY})
glyph("d_up", ["....##....", "...####...", "..######..", ".########.", "....##....", "....##....", "....##....",
               "....##....", "....##....", "....##...."], {"#": WHITE})
glyph("d_down", ["....##....", "....##....", "....##....", "....##....", "....##....", "....##....", ".########.",
                 "..######..", "...####...", "....##...."], {"#": WHITE})
glyph("d_left", ["...#......", "..##......", ".###......", "##########", "##########", ".###......", "..##......",
                 "...#......", "..........", ".........."], {"#": WHITE})
glyph("d_right", ["......#...", "......##..", "......###.", "##########", "##########", "......###.", "......##..",
                  "......#...", "..........", ".........."], {"#": WHITE})
# filter and module options, 10 square
glyph("o_white", ["#########.", "#.......#.", "#.####..#.", "#.......#.", "#.####..#.", "#.....#.#.", "#.###.#.#.",
                  "#...#.#..#", "#....#...#", "#########."], {"#": WHITE})
glyph("o_black", ["#########.", "#.......#.", "#.####..#.", "#.......#.", "#.####..#.", "#.......##", "#.###.#..#",
                  "#.....##.#", "#.....#..#", "########.#"], {"#": GREY})
glyph("o_damage", [".......##.", "......###.", ".....###..", "....###...", "...###....", "#.###.....", ".##.......",
                   ".#.#......", "#...#.....", ".........."], {"#": (240, 200, 120)})
glyph("o_nbt", ["..##..##..", ".#......#.", ".#......#.", ".#......#.", "#........#", ".#......#.", ".#......#.",
                ".#......#.", "..##..##..", ".........."], {"#": (200, 160, 255)})
glyph("o_tags", ["..#...#...", "..#...#...", "#########.", "..#...#...", "..#...#...", "..#...#...", "#########.",
                 "..#...#...", "..#...#...", ".........."], {"#": (120, 220, 255)})
glyph("o_mod", ["...##.....", "..#..#....", "..#..####.", ".##......#", "#........#", "#.......#.", ".##......#",
                "..#.....#.", "..#######.", ".........."], {"#": (160, 230, 140)})
glyph("o_term", ["..######..", ".#++++++#.", "#++++++++#", "#+######+#", "#+######+#", "#++++++++#", "#++++++++#",
                 ".#++++++#.", "..######..", ".........."], {"#": RED, "+": RED_DK})
glyph("o_rs_always", ["..........", "...####...", "..#....#..", ".#.####.#.", ".#.#..#.#.", ".#.#..#.#.", ".#.####.#.",
                      "..#....#..", "...####...", ".........."], {"#": GREY})
glyph("o_rs_high", ["..........", "...####...", "..######..", ".########.", ".########.", ".########.", ".########.",
                    "..######..", "...####...", ".........."], {"#": RED})
glyph("o_rs_low", ["..........", "...####...", "..#....#..", ".#......#.", ".#.####.#.", ".#......#.", ".#......#.",
                   "..#....#..", "...####...", ".........."], {"#": RED_DK})
# small things, 10 square
glyph("m_plus", ["..........", "....##....", "....##....", "....##....", ".########.", ".########.", "....##....",
                 "....##....", "....##....", ".........."], {"#": WHITE})
glyph("m_minus", ["..........", "..........", "..........", "..........", ".########.", ".########.", "..........",
                  "..........", "..........", ".........."], {"#": WHITE})
glyph("m_cycle", ["...####...", "..#....#.#", ".#......##", ".#.....###", ".#........", "........#.", "###.....#.",
                  "##......#.", "#.#....#..", "...####..."], {"#": WHITE})
glyph("m_clear", ["#........#", ".#......#.", "..#....#..", "...#..#...", "....##....", "....##....", "...#..#...",
                  "..#....#..", ".#......#.", "#........#"], {"#": RED})
glyph("m_silk", ["........#.", ".......##.", "......#.#.", ".....#..#.", "....#..#..", "...#..#...", "..#.##....",
                 ".#.#......", "##........", "#........."], {"#": (230, 240, 255)})
glyph("m_fortune", ["....##....", "...####...", "..##..##..", ".##....##.", "##..##..##", ".##....##.", "..##..##..",
                    "...####...", "....##....", ".........."], {"#": (90, 220, 255)})
glyph("m_strong", ["....##....", "...###....", "..###.....", ".#######..", "....###...", "...###....", "..###.....",
                   "..##......", ".##.......", ".#........"], {"#": RED})
glyph("m_weak", ["..........", "..........", "..##......", ".#..#.....", "#....#...#", ".....#..#.", "......##..",
                 "..........", "..........", ".........."], {"#": RED_DK})
glyph("m_player", ["...####...", "..#....#..", "..#.##.#..", "..#....#..", "...####...", "..######..", ".#......#.",
                   ".#......#.", ".#......#.", ".########."], {"#": (255, 210, 74)})
glyph("m_target", ["....#.....", "..#####...", ".#..#..#..", ".#..#..#..", "#########.", ".#..#..#..", ".#..#..#..",
                   "..#####...", "....#.....", ".........."], {"#": (95, 212, 95)})


def widget_sheet():
    S = L.Sheet
    cv = Canvas(S.W, S.H)
    bx, by = S.BUTTON
    for i in range(4):
        button(cv, bx + 16 * i, by, 16, i)
    tx, ty = S.TOGGLE
    for i, (on, hover) in enumerate([(False, False), (False, True), (True, False), (True, True)]):
        toggle(cv, tx + 14 * i, ty, 14, on, hover)
    gx, gy = S.GEAR
    for i, col in enumerate([GREY, TEAL, STEEL_DK]):
        rows, pal = G["gear"]
        cv.draw(gx + 8 * i, gy, rows, {**pal, "#": col})
    kx, ky = S.BACK
    for i in range(2):
        button(cv, kx + 12 * i, ky, 12, i)
        cv.draw(kx + 12 * i, ky + 2, G["back"][0], {"#": WHITE if i else GREY})
    sx, sy = S.SMALL
    for i in range(3):
        button(cv, sx + 10 * i, sy, 10, (0, 1, 2)[i])
    rx, ry = S.REDSTONE
    for i, name in enumerate(["rs_always", "rs_high", "rs_low", "rs_never", "rs_pulse"]):
        cv.draw(rx + 12 * i, ry, *G[name])
    ix, iy = S.INFO
    for i, name in enumerate(["i_clock", "i_stack", "i_range"]):
        cv.draw(ix + 10 * i, iy, *G[name])
    dx, dy = S.DIRS
    for i, name in enumerate(["d_none", "d_front", "d_back", "d_up", "d_down", "d_left", "d_right"]):
        cv.draw(dx + 10 * i, dy, *G[name])
    ox, oy = S.OPTS
    for i, name in enumerate(["o_white", "o_black", "o_damage", "o_nbt", "o_tags", "o_mod", "o_term", "o_rs_always", "o_rs_high",
                              "o_rs_low"]):
        cv.draw(ox + 10 * i, oy, *G[name])
    mx, my = S.MISC
    for i, name in enumerate(["m_plus", "m_minus", "m_cycle", "m_clear", "m_silk", "m_fortune", "m_strong", "m_weak", "m_player",
                              "m_target"]):
        cv.draw(mx + 10 * i, my, *G[name])
    cv.save(out("gui", "widgets.png"))
    return cv


# ------------------------------------------------------------------ the block

def block_textures():
    def steel_face(seed, rivets=True):
        cv = Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                cv.set(x, y, mix(STEEL, STEEL_LO, (x + y) / 30.0))
        noise(cv, 0, 0, 16, 16, 6, seed)
        cv.bevel(0, 0, 16, 16, STEEL_HI, STEEL_DK)
        cv.outline(1, 1, 15, 15, mix(STEEL_MID, STEEL_DK, 0.3))
        if rivets:
            for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                cv.set(x, y, COPPER_HI)
                cv.set(x + 1, y + 1, COPPER_LO)
                cv.set(x + 1, y, COPPER)
                cv.set(x, y + 1, COPPER)
        return cv

    side = steel_face(31)
    # a copper stripe with a trace across the side
    for x in range(1, 15):
        side.set(x, 7, COPPER_HI)
        side.set(x, 8, COPPER_LO)
    for y in range(4, 12):
        side.set(7, y, mix(COPPER, BASE_LO, 0.4) if y not in (7, 8) else COPPER)
    side.set(7, 7, COPPER_HI)
    side.save(out("block", "router_side.png"))

    top = steel_face(37)
    for y in range(4, 12):
        for x in range(4, 12):
            top.set(x, y, OUTLINE if (x + y) % 2 == 0 else STEEL_DK)
    top.outline(3, 3, 13, 13, COPPER_LO)
    top.save(out("block", "router_top.png"))

    bottom = steel_face(41, rivets=False)
    for y in range(16):
        for x in range(16):
            r, g, b, a = bottom.get(x, y)
            bottom.set(x, y, shade((r, g, b), 0.75))
    bottom.save(out("block", "router_bottom.png"))

    for active in (False, True):
        cv = steel_face(43)
        cv.rect(2, 2, 14, 14, (14, 18, 22))
        cv.bevel(2, 2, 14, 14, OUTLINE, STEEL_HI)
        lit, dim = (TEAL, (30, 70, 66))
        for r in range(3):
            for c in range(3):
                x, y = 4 + c * 3, 4 + r * 3
                on = active and (r * 3 + c) % 2 == 0 or active and r == 1 and c == 1
                col = lit if on else dim
                cv.set(x, y, col)
                cv.set(x + 1, y, shade(col, 0.8))
                cv.set(x, y + 1, shade(col, 0.8))
                cv.set(x + 1, y + 1, shade(col, 0.6))
        if active:
            # a soft halo round the lit lights
            for y in range(3, 13):
                for x in range(3, 13):
                    r_, g_, b_, a_ = cv.get(x, y)
                    if (r_, g_, b_) == (14, 18, 22):
                        cv.set(x, y, mix((14, 18, 22), TEAL_DK, 0.35))
        cv.save(out("block", "router_front_active.png" if active else "router_front.png"))


# ------------------------------------------------------------------ the items

MODULE_GLYPHS = {
    "sender": ["........", "....#...", "....##..", "#######.", "#######.", "....##..", "....#...", "........"],
    "puller": ["........", "...#....", "..##....", ".#######", ".#######", "..##....", "...#....", "........"],
    "distributor": ["......##", ".....#..", "....#...", "######..", "######..", "....#...", ".....#..", "......##"],
    "dropper": ["...##...", "...##...", "...##...", ".######.", "..####..", "...##...", "........", "########"],
    "flinger": ["....###.", "..##...#", ".#.....#", "#......#", "#.......", "#.......", "##......", "#......."],
    "placer": ["..####..", ".#.##.#.", "#..##..#", "########", "#..##..#", "#..##..#", ".#....#.", "..####.."],
    "breaker": ["..####..", ".#....##", ".....##.", "....##..", "...##...", "..##....", ".##.....", "##......"],
    "vacuum": ["..####..", ".#....#.", "#..##..#", "#.#..#.#", "#.#.##.#", "#..#...#", ".#....#.", "..####.."],
    "void": ["..####..", ".#....#.", "#......#", "#..##..#", "#..##..#", "#......#", ".#....#.", "..####.."],
    "player": ["..####..", "..#..#..", "..####..", "...##...", ".######.", "#.####.#", "..#..#..", "..#..#.."],
    "detector": ["........", "..####..", ".#....#.", "#..##..#", "#..##..#", ".#....#.", "..####..", "........"],
    "extruder": ["#.......", "##......", "########", "########", "##......", "#.......", "........", "........"],
}

UPGRADE_GLYPHS = {
    "speed": ["...##.", "..##..", ".####.", "..##..", ".##...", "##...."],
    "stack": ["######", "#....#", "######", "######", "#....#", "######"],
    "range": ["#.##.#", ".#..#.", "#....#", "#....#", ".#..#.", "#.##.#"],
    "muffler": ["#...#.", ".#.#..", "..#...", ".#.#..", "#...#.", "......"],
}


def module_item(name, colour):
    """A module: a cartridge with copper contacts, its glyph lit in its colour on a dark window."""
    cv = Canvas(16, 16)
    body, body_hi, body_lo = (64, 70, 82), (104, 112, 126), (38, 42, 50)
    for y in range(1, 13):
        for x in range(1, 15):
            if (x, y) in ((1, 1), (14, 1)):
                continue
            cv.set(x, y, body)
    for x in range(2, 14):
        cv.set(x, 1, body_hi)
    for y in range(2, 13):
        cv.set(1, y, body_hi)
        cv.set(14, y, body_lo)
    for x in range(1, 15):
        cv.set(x, 12, body_lo)
    # outline
    for x in range(2, 14):
        cv.set(x, 0, OUTLINE)
    for y in range(1, 13):
        cv.set(0, y, OUTLINE)
        cv.set(15, y, OUTLINE)
    cv.set(1, 0, OUTLINE)
    cv.set(14, 0, OUTLINE)
    # copper contacts
    for x in range(2, 14):
        cv.set(x, 13, OUTLINE)
        cv.set(x, 14, OUTLINE)
    for x in (3, 5, 7, 9, 11):
        cv.set(x, 13, COPPER_HI)
        cv.set(x + 1, 13, COPPER)
        cv.set(x, 14, COPPER)
        cv.set(x + 1, 14, COPPER_LO)
    for x in range(2, 14):
        cv.set(x, 15, (0, 0, 0, 0)[:3], 0)
    # the window and the glyph
    c = hexrgb(colour) if colour is not None else None
    for y in range(3, 11):
        for x in range(3, 13):
            cv.set(x, y, (16, 18, 22) if c is None else mix((16, 18, 22), c, 0.12))
    cv.bevel(2, 2, 14, 12, OUTLINE, body_hi)
    if name in MODULE_GLYPHS and c is not None:
        rows = MODULE_GLYPHS[name]
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    cv.set(4 + i, 3 + j, c)
        # a faint glow round the glyph
        for j in range(8):
            for i in range(8):
                if rows[j][i] != "#":
                    near = any(0 <= j + dj < 8 and 0 <= i + di < 8 and rows[j + dj][i + di] == "#"
                               for dj in (-1, 0, 1) for di in (-1, 0, 1))
                    if near:
                        cv.set(4 + i, 3 + j, mix((16, 18, 22), c, 0.3))
    elif c is None:
        # a blank module: a faint circuit on the window
        for x in range(4, 12):
            cv.set(x, 7, (40, 46, 52))
        for y in range(4, 10):
            cv.set(7, y, (40, 46, 52))
    cv.save(out("item", (name + "_module.png") if name else "blank_module.png"))


def upgrade_item(name, colour):
    """An upgrade: a chip with pins down its sides, a band of its colour, its glyph."""
    cv = Canvas(16, 16)
    c = hexrgb(colour) if colour is not None else GREY
    for y in range(2, 14):
        for x in range(3, 13):
            cv.set(x, y, (40, 44, 52))
    cv.outline(2, 1, 14, 15, OUTLINE)
    cv.bevel(3, 2, 13, 14, (88, 94, 108), (26, 28, 34))
    for y in (3, 5, 7, 9, 11, 13):
        cv.set(1, y, COPPER)
        cv.set(14, y, COPPER)
        cv.set(0, y, COPPER_LO)
        cv.set(15, y, COPPER_LO)
    for x in range(4, 12):
        cv.set(x, 3, c)
        cv.set(x, 4, shade(c, 0.7))
    if name in UPGRADE_GLYPHS:
        for j, row in enumerate(UPGRADE_GLYPHS[name]):
            for i, ch in enumerate(row):
                if ch == "#":
                    cv.set(5 + i, 6 + j, c)
    else:
        cv.set(7, 9, (70, 76, 88))
        cv.set(8, 9, (70, 76, 88))
    cv.save(out("item", (name + "_upgrade.png") if name else "blank_upgrade.png"))


def items():
    module_item(None, None)
    for name in MODULES:
        module_item(name, MODULE_COLOURS[name])
    upgrade_item(None, None)
    for name in UPGRADES:
        upgrade_item(name, UPGRADE_COLOURS[name])


def logo():
    """The mod list's logo: the router's front, large, its lights lit, traces running out of it."""
    cv = Canvas(128, 128, (20, 22, 27, 255))
    big = Image.open(out("block", "router_front_active.png")).convert("RGBA").resize((64, 64), resample=0)
    for (ax, ay, bx, by) in ((0, 64, 32, 64), (96, 64, 128, 64), (64, 0, 64, 32), (64, 96, 64, 128)):
        for t in range(0, 33):
            x = ax + (bx - ax) * t // 32
            y = ay + (by - ay) * t // 32
            for d in (-1, 0, 1):
                cv.set(x + (d if ax == bx else 0), y + (d if ay == by else 0), COPPER if d == 0 else COPPER_LO)
    cv.img.alpha_composite(big, (32, 32))
    for (x, y) in ((16, 64), (112, 64), (64, 16), (64, 112)):
        pad(cv, x, y, 3)
    cv.save(os.path.join(ROOT, "src", "main", "resources", "boundlessrouters_logo.png"))


def main():
    router_panel()
    module_panel()
    widget_sheet()
    block_textures()
    items()
    logo()
    print("textures written")


if __name__ == "__main__":
    main()
