"""Every machine's GUI layout, and the numbers its screen draws its moving parts at: the one place they live.

    python3 tools/machines/layouts.py

writes machine/MachineLayouts.java from them (the menus put the slots there, the screens draw there); the
panels and widget sheets are drawn on the same numbers (gen_v2.py; the first-generation ones by gen_gui.py on
layout.py), so code and textures can't disagree. check_layout.py makes sure the Java file is current.

Coordinates are the menu's (the panel's top-left is 0, 0). A slot is given by its item's corner: its 16x16
item there, its 18x18 frame a pixel round it. Angles are degrees on the screen (y down): 0 is right, 90 down.

Second-generation machines all share the panel's size and proportions (W x H, the machine's part MACHINE_H
high, the player's inventory on a panel hanging under it in the same place) and where the controls are (the
redstone button and the close button on the top corners, the title between them); each has its own look,
slots placed as its machine works, its own mana gauge.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import layout as CLASSIC  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
JAVA_OUT = os.path.join(ROOT, "src", "main", "java", "com", "reya", "alfheimheart", "machine", "MachineLayouts.java")

M = 12                                   # the panel texture's margin (ornaments reach over the panel's edge)
W, H, MACHINE_H = 256, 248, 156
INV_X, INV_Y, HOTBAR_Y = 48, 166, 224    # the inventory's first slot (item corner); rows 18 apart, the hotbar 4 lower
INV_PANEL = (40, 216)                    # the inventory panel's sides
CLOSE = (W - 11, -8)                     # the round buttons on the top corners (16 x 16)
REDSTONE = (-5, -8)
FRAME = 7                                # the frame's width round the machine's part

# every machine's widget sheet (256 x 128) keeps these pieces in the same places, where
# machine/client/MachineScreen.java reads them (the first machines' shared sheet too: gen_widgets.py)
SHEET_W, SHEET_H = 256, 128
BUTTON_UV = (0, 0)                       # the redstone button 16x16: normal, hover, pressed
ICON_UV = (0, 16)                        # its icons 12x12: ignore, high, low
CLOSE_UV, CLOSE_SIZE = (104, 0), 16      # the close button: normal, hover, pressed
GLOW_UV, GLOW_SIZE = (104, 16), 9        # a small soft halo (the screens tint it)
GEM_UV, GEM_SIZE = (160, 16), 7          # the status gem: working, idle, waiting, stopped
POOL_UV, POOL_SIZE = (192, 0), 16        # the pool lamp: off, on
PLAQUE_V, PLAQUE_CAP, PLAQUE_TILE_U, PLAQUE_TILE_W = 56, 16, 32, 8   # the title plaque: its ends, two tiles
HALO_UV, HALO_SIZE = (48, 96), 15        # a big soft halo
SHARED_REGIONS = [("buttons", 0, 0, 48, 16), ("icons", 0, 16, 36, 12), ("close", 104, 0, 48, 16), ("glow", 104, 16, 9, 9),
                  ("gems", 160, 16, 28, 7), ("pool", 192, 0, 32, 16), ("plaque", 0, 56, 48, 20), ("halo", 48, 96, 15, 15)]


def polar(cx, cy, r, deg):
    a = math.radians(deg)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def slot_at(cx, cy, r, deg):
    """The item corner of the slot whose middle is r from (cx, cy) at the angle."""
    x, y = polar(cx, cy, r, deg)
    return int(round(x - 8)), int(round(y - 8))


def grid(x, y, cols, rows):
    return [(x + c * 18, y + r * 18) for r in range(rows) for c in range(cols)]


# ---------------------------------------------------------------- the Runic Altar: the rune sanctum

class ALTAR:
    """A ritual circle in a slate sanctum: the altar in the middle, the eight inputs on a ring of nine sockets
    round it (each under an element's rune, the rainbow round the ring), the livingrock socket the ninth at the
    bottom; a crystal column of mana on the left, the runes made in an arched reliquary on the right."""
    KEY = "rune_altar"
    CX, CY = 124, 78
    ALTAR_R = 22                         # the altar's octagonal top (inradius)
    HOLLOW_R = 10                        # the dark hollow the rune takes shape in
    INNER_R = 28                         # a gold ring round the altar
    SOCKET_R = 50                        # the ring of sockets
    OUTER_R = 64                         # the circle's outer gold ring
    INPUT_ANGLES = [250, 290, 330, 10, 50, 130, 170, 210]     # clockwise from the top-left
    REAGENT_ANGLE = 90
    # the runes over the inputs, the rainbow clockwise: fire, autumn, summer, earth, winter, water, air, spring
    RUNES = ["fire", "autumn", "summer", "earth", "winter", "water", "air", "spring"]
    RUNE_COLOURS = [0xFF5A3C, 0xFF9C33, 0xFFE04A, 0x86E04C, 0x6CF0EC, 0x4A86FF, 0xAE92FF, 0xFF74D2]
    REAGENT_COLOUR = 0xA6F6FF
    OUTPUTS = grid(203, 52, 2, 3)        # the reliquary
    NICHE = (196, 22, 244, 120)          # its arch: x1, y1 (the arch's top), x2, y2
    GAUGE = (28, 34, 38, 118)            # the crystal column's mana: x1, y1, x2, y2 (fills from the bottom)
    GAUGE_BOX = (21, 18, 45, 126)        # the column with its cap and base (its tooltip)
    POOL = (25, 129)                     # the pool lamp under it
    GEM = (CX, CY - ALTAR_R + 3)         # on the altar's top edge
    LAND = (220, 78)                     # where finished crafts fly to (the reliquary's middle)
    CLICK = [(CX - 9, CY - 9, 18, 18)]   # JEI's recipes: the altar's hollow
    LIGHTS = [(3, 152), (252, 152), (43, 244), (212, 244)]
    VEIN = (3, 3, W - 4, MACHINE_H - 4)
    # the widget sheet's own pieces
    GLYPH_UV, GLYPH_SIZE = (0, 112), 9   # the eight runes lit, white (the screen tints them), in RUNES' order
    MANA_GLYPH_UV = (72, 112)            # the reagent socket's mana rune
    HALO_SOCKET_UV, HALO_SOCKET_SIZE = (128, 32), 26  # a soft square glow round a socket
    FILL_UV = (232, 0)                   # the column's mana, GAUGE's size


# the sockets (a class body's comprehensions can't see its names)
ALTAR.INPUTS = [slot_at(ALTAR.CX, ALTAR.CY, ALTAR.SOCKET_R, a) for a in ALTAR.INPUT_ANGLES]
ALTAR.REAGENT = slot_at(ALTAR.CX, ALTAR.CY, ALTAR.SOCKET_R, ALTAR.REAGENT_ANGLE)
ALTAR.SHEET = [("glyphs", ALTAR.GLYPH_UV[0], ALTAR.GLYPH_UV[1], 8 * ALTAR.GLYPH_SIZE, ALTAR.GLYPH_SIZE),
               ("mana glyph", ALTAR.MANA_GLYPH_UV[0], ALTAR.MANA_GLYPH_UV[1], ALTAR.GLYPH_SIZE, ALTAR.GLYPH_SIZE),
               ("socket halo", ALTAR.HALO_SOCKET_UV[0], ALTAR.HALO_SOCKET_UV[1], ALTAR.HALO_SOCKET_SIZE, ALTAR.HALO_SOCKET_SIZE),
               ("column fill", ALTAR.FILL_UV[0], ALTAR.FILL_UV[1], ALTAR.GAUGE[2] - ALTAR.GAUGE[0], ALTAR.GAUGE[3] - ALTAR.GAUGE[1])]


def altar_spokes():
    """The channels from each socket in to the altar's gold ring: x1, y1 (at the socket), x2, y2 (at the ring)."""
    a = ALTAR
    out = []
    for deg in a.INPUT_ANGLES + [a.REAGENT_ANGLE]:
        c = math.cos(math.radians(deg))
        s = math.sin(math.radians(deg))
        inner = 9.5 / max(abs(c), abs(s))                 # from the socket's middle out to its frame
        x1, y1 = polar(a.CX, a.CY, a.SOCKET_R - inner - 1.0, deg)
        x2, y2 = polar(a.CX, a.CY, a.INNER_R + 1.5, deg)
        out.append((round(x1, 1), round(y1, 1), round(x2, 1), round(y2, 1)))
    return out


# ---------------------------------------------------------------- the Terrestrial Plate: the celestial astrolabe

class PLATE:
    """The plate as the heart of an astrolabe under the night sky: the six inputs on a hexagram's points (the
    sky's triangle in mana blue, the earth's in green: terrasteel is both), a silver limb round them, the mana
    gauge an arc on its left; what it makes goes along a beam out to a moon tower on the right."""
    KEY = "terra_plate"
    CX, CY = 114, 78
    PLATE_R = 23                         # the plate's octagon (inradius)
    CORE_R = 6.5                         # its dark core, where the light gathers
    SOCKET_R = 46
    INPUT_ANGLES = [270, 330, 30, 90, 150, 210]               # the top first, clockwise
    SKY = [0, 2, 4]                      # the inputs on the sky's triangle (the others are the earth's)
    LIMB_R1, LIMB_R2 = 54, 59            # the silver limb
    ARC_R1, ARC_R2 = 63, 69              # the mana gauge's arc
    ARC_FROM, ARC_TO = 120, 240          # it fills from its bottom (120) up round the left to its top (240)
    OUTPUTS = [(208, 52), (208, 70), (208, 88)]               # the moon tower
    TOWER = (197, 46, 235, 124)          # its body: x1, y1, x2, y2
    MOON = (216, 30, 11)                 # the crescent over it: middle, radius
    BEAM = (CX + LIMB_R2, CY, TOWER[0], CY)
    GAUGE_BOX = (44, 16, 84, 140)        # round the arc (the screen tells the arc itself)
    POOL = (24, 128)
    GEM = (216, 30)                      # in the crescent's arms
    LAND = (216, 78)
    CLICK = [(CX - 8, CY - 8, 16, 16)]   # the core
    LIGHTS = [(3, 152), (252, 152), (43, 244), (212, 244)]
    VEIN = (3, 3, W - 4, MACHINE_H - 4)
    STARS = [(16, 22), (60, 14), (180, 18), (246, 64), (152, 140), (14, 98), (238, 138), (90, 146)]   # the bright ones (they twinkle)
    # the sun on the plate (as the panel draws it): its ring, and each ray's pixels from RAY_FROM to RAY_TO, a tip to TIP_TO
    SUN_RING_R, RAY_FROM, RAY_TO, TIP_TO = 13.5, 8.5, 19.0, 20.2
    # the widget sheet's own pieces
    STAR_UV, STAR_SIZE = (128, 32), 9    # a four-pointed star, white


PLATE.INPUTS = [slot_at(PLATE.CX, PLATE.CY, PLATE.SOCKET_R, a) for a in PLATE.INPUT_ANGLES]
PLATE.SHEET = [("star", PLATE.STAR_UV[0], PLATE.STAR_UV[1], PLATE.STAR_SIZE, PLATE.STAR_SIZE)]


# ---------------------------------------------------------------- the first-generation machines

def classic(key, heart, gem, special):
    """The living-wood panel every machine shared before (layout.py): 3x3 grids, arrows, a mana bar."""
    L = CLASSIC
    bar = L.BAR
    return dict(key=key, classic=True, size=(L.W, L.H, L.MACHINE_H),
                inputs=grid(L.INPUT[0], L.INPUT[1], 3, 3), outputs=grid(L.OUTPUT[0], L.OUTPUT[1], 3, 3), special=special,
                inventory=(L.INV_SLOT_X, L.INV_Y, L.HOTBAR_Y, L.INV_X1, L.INV_X2),
                close=(229, -8), redstone=L.BUTTONS[0], pool=L.POOL, gem=gem, heart=heart,
                land=(L.OUTPUT[0] + 26, L.OUTPUT[1] + 26),
                gauge=(bar[0] - 14, bar[1] - 3, bar[2] + 2, bar[3] + 3),
                click=[(ax, ay, L.ARROW_W, L.ARROW_H) for (ax, ay) in L.ARROWS],
                lights=L.LIGHTS, vein=(3, 3, L.W - 4, L.MACHINE_H - 4))


def v2(m, special):
    return dict(key=m.KEY, classic=False, size=(W, H, MACHINE_H), inputs=m.INPUTS, outputs=m.OUTPUTS, special=special,
                inventory=(INV_X, INV_Y, HOTBAR_Y, INV_PANEL[0], INV_PANEL[1]), close=CLOSE, redstone=REDSTONE,
                pool=m.POOL, gem=m.GEM, heart=(m.CX, m.CY), land=m.LAND,
                gauge=getattr(m, "GAUGE_BOX", (0, 0, 0, 0)), click=m.CLICK, lights=m.LIGHTS, vein=m.VEIN)


SPECIAL = CLASSIC.SPECIAL_SLOT
LAYOUTS = [
    ("RUNE_ALTAR", v2(ALTAR, [ALTAR.REAGENT])),
    ("TERRA_PLATE", v2(PLATE, [])),
    ("MANA_INFUSER", classic("mana_infuser", (120, 27), (120, 66), [SPECIAL])),
    ("PURE_DAISY", classic("pure_daisy", (120, 52), (120, 19), [])),
    ("PETAL_APOTHECARY", classic("petal_apothecary", (120, 22), (120, 57), [SPECIAL])),
    ("PETAL_FARM", classic("petal_farm", (120, 44), (120, 62), [SPECIAL])),
    ("ORECHID_MINE", classic("orechid_mine", (120, 50), (120, 31), [])),
    ("CROP_FIELD", classic("crop_field", (120, 50), (120, 76), [SPECIAL])),
]


V2 = {"rune_altar": ALTAR, "terra_plate": PLATE}       # the second-generation machines


def art_constants():
    """The numbers each second-generation screen draws its own parts at."""
    a, p = ALTAR, PLATE
    altar = [
        ("CX", a.CX), ("CY", a.CY), ("ALTAR_R", a.ALTAR_R), ("HOLLOW_R", a.HOLLOW_R), ("INNER_R", a.INNER_R),
        ("SOCKET_R", a.SOCKET_R), ("OUTER_R", a.OUTER_R),
        ("RUNE_COLOURS", tuple(a.RUNE_COLOURS)), ("REAGENT_COLOUR", a.REAGENT_COLOUR),
        ("SPOKES", altar_spokes()), ("NICHE", a.NICHE), ("GAUGE", a.GAUGE),
        ("GLYPH_U", a.GLYPH_UV[0]), ("GLYPH_V", a.GLYPH_UV[1]), ("GLYPH_SIZE", a.GLYPH_SIZE),
        ("MANA_GLYPH_U", a.MANA_GLYPH_UV[0]), ("MANA_GLYPH_V", a.MANA_GLYPH_UV[1]),
        ("HALO_SOCKET_U", a.HALO_SOCKET_UV[0]), ("HALO_SOCKET_V", a.HALO_SOCKET_UV[1]), ("HALO_SOCKET_SIZE", a.HALO_SOCKET_SIZE),
        ("FILL_U", a.FILL_UV[0]), ("FILL_V", a.FILL_UV[1]),
    ]
    plate = [
        ("CX", p.CX), ("CY", p.CY), ("PLATE_R", p.PLATE_R), ("CORE_R", float(p.CORE_R)), ("SOCKET_R", p.SOCKET_R),
        ("SKY", tuple(p.SKY)), ("LIMB_R1", p.LIMB_R1), ("LIMB_R2", p.LIMB_R2), ("ARC_R1", p.ARC_R1), ("ARC_R2", p.ARC_R2),
        ("ARC_FROM", p.ARC_FROM), ("ARC_TO", p.ARC_TO), ("TOWER", p.TOWER), ("MOON", p.MOON), ("BEAM", p.BEAM),
        ("SUN_RING_R", float(p.SUN_RING_R)), ("RAY_FROM", float(p.RAY_FROM)), ("RAY_TO", float(p.RAY_TO)), ("TIP_TO", float(p.TIP_TO)),
        ("STARS", p.STARS), ("STAR_U", p.STAR_UV[0]), ("STAR_V", p.STAR_UV[1]), ("STAR_SIZE", p.STAR_SIZE),
    ]
    return [("Altar", "the Runic Altar's sanctum", altar), ("Plate", "the Terrestrial Plate's astrolabe", plate)]


# ---------------------------------------------------------------- the Java file

def java_value(v, hexa=False):
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, int):
        return "0x%06X" % v if hexa else str(v)
    if isinstance(v, float):
        return repr(v) + "F"
    if isinstance(v, str):
        return '"%s"' % v
    raise ValueError(v)


def java_array(v, hexa=False):
    """int[] / float[] / int[][] / float[][] literal of a tuple or a list of tuples."""
    if len(v) and isinstance(v[0], (tuple, list)):
        inner = ", ".join("{" + ", ".join(java_value(x, hexa) for x in row) + "}" for row in v)
        return "{" + inner + "}"
    return "{" + ", ".join(java_value(x, hexa) for x in v) + "}"


def java_type(v):
    if isinstance(v, (tuple, list)):
        first = v[0] if len(v) else 0
        if isinstance(first, (tuple, list)):
            return "float[][]" if any(isinstance(x, float) for row in v for x in row) else "int[][]"
        return "float[]" if any(isinstance(x, float) for x in v) else "int[]"
    if isinstance(v, float):
        return "float"
    return "int"


def ints(rows):
    return "new int[][]" + java_array([tuple(r) for r in rows]) if rows else "new int[0][]"


def java():
    out = ["// Written by tools/machines/layouts.py from its numbers: change them there and run it (check_layout.py",
           "// makes sure this is current).",
           "package com.reya.alfheimheart.machine;",
           "",
           "/** Every machine's GUI layout (see {@link MachineLayout}), and the numbers its screen draws its own parts at. */",
           "public final class MachineLayouts {"]
    for name, d in LAYOUTS:
        w, h, mh = d["size"]
        inv = d["inventory"]
        out.append("    public static final MachineLayout %s = new MachineLayout(\"%s\", %s, %d, %d, %d," % (
            name, d["key"], java_value(d["classic"]), w, h, mh))
        out.append("            %s," % ints(d["inputs"]))
        out.append("            %s," % ints(d["outputs"]))
        out.append("            %s," % ints(d["special"]))
        out.append("            new int[]%s, new int[]%s, new int[]%s, new int[]%s," % (
            java_array(inv), java_array(d["close"]), java_array(d["redstone"]), java_array(d["pool"])))
        out.append("            new int[]%s, new int[]%s, new int[]%s, new int[]%s," % (
            java_array(d["gem"]), java_array(d["heart"]), java_array(d["land"]), java_array(d["gauge"])))
        out.append("            %s, %s, new int[]%s);" % (ints(d["click"]), ints(d["lights"]), java_array(d["vein"])))
    for cls, what, consts in art_constants():
        out.append("")
        out.append("    /** The numbers %s is drawn at. */" % what)
        out.append("    public static final class %s {" % cls)
        for k, v in consts:
            t = java_type(v)
            hexa = "COLOUR" in k
            if t.endswith("]"):
                out.append("        public static final %s %s = %s;" % (t, k, java_array(v, hexa)))
            else:
                out.append("        public static final %s %s = %s;" % (t, k, java_value(v, hexa)))
        out.append("")
        out.append("        private %s() {" % cls)
        out.append("        }")
        out.append("    }")
    out.append("")
    out.append("    private MachineLayouts() {")
    out.append("    }")
    out.append("}")
    return "\n".join(out) + "\n"


def main():
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write(java())
    print("wrote " + os.path.relpath(JAVA_OUT, ROOT))


if __name__ == "__main__":
    main()
