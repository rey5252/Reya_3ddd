"""Where everything is in the fusion core's screen, and on its textures.

    python3 tools/layout.py

The menu (slots), the screen (the space window, the pylons' portholes, the tubes, the orb, the hologram), JEI's page
and the textures (gen_gui.py) all read these numbers; this writes them into
src/main/java/com/reya/singularityfusion/gui/Layouts.java, and check.py makes sure the Java file is current and
nothing overlaps. Screen coordinates are GUI pixels from the panel's top left; the textures are drawn at twice that
(2 texture pixels to a GUI pixel), their regions given in texture pixels.
"""
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
JAVA_OUT = os.path.join(ROOT, "src", "main", "java", "com", "reya", "singularityfusion", "gui", "Layouts.java")

W = 256                     # the panel's width
FRAME = 8                   # its frame
MH = 160                    # the machine area's height; the player's inventory panel hangs under it
INV_W = 176
TITLE = (68, 1, 188, 19)    # the title's plaque over the top of the frame: x1, y1, x2, y2
WINDOW = (8, 8, 248, 152)   # the window onto space: x1, y1, x2, y2
HOLE = (128, 64)            # the singularity's middle
PYLONS_LEFT = [(30 - (3 if i in (0, 5) else 1 if i in (1, 4) else 0), 24 + 20 * i) for i in range(6)]   # the pylons' items, a gentle curve
PYLONS_RIGHT = [(W - 30 - 16 + (3 if i in (0, 5) else 1 if i in (1, 4) else 0), 24 + 20 * i) for i in range(6)]
PORTHOLE = 22               # a porthole round an item, its middle on the item's
CATALYST = (95, 108)        # the catalyst slot (its item's top left)
START = (116, 103)          # the start orb, 24 square
ORB = 24
OUTPUT = (145, 108)
ENERGY = (12, 24, 10, 122)  # the energy's plasma in its tube: x, y, width, height (fills from the bottom)
PROGRESS = (W - 22, 24, 10, 122)
STATUS = (50, 130, 206, 152)  # the hologram: x1, y1, x2, y2 (two lines of text in it)


def inventory(mh):
    """The player inventory panel under a machine area mh tall: its x, the slots' x and y, the hotbar's y, height."""
    px = (W - INV_W) // 2
    inv_y = mh + 12
    hot_y = inv_y + 58
    return px, px + 8, inv_y, hot_y, hot_y + 18 + 8


PANEL_X, INV_X, INV_Y, HOTBAR_Y, H = inventory(MH)
INV_WINDOW = (PANEL_X + 6, MH - 4, PANEL_X + INV_W - 6, H - 6)

# the frame's ornaments: ringed medallions (x, y, radius; each with a gem in its middle 0.45 of it across), the two
# at the ends of the title's plaque, and gems set in the frame (x, y, radius)
MEDALLIONS = [(8.5, 8.5, 8.0), (W - 8.5, 8.5, 8.0), (8.5, MH - 8.5, 8.0), (W - 8.5, MH - 8.5, 8.0), (PANEL_X + 7.0, H - 7.0, 5.0),
              (PANEL_X + INV_W - 7.0, H - 7.0, 5.0)]
PLAQUE_MEDALLIONS = [(TITLE[0] + 1.0, (TITLE[1] + TITLE[3]) / 2.0, 5.5), (TITLE[2] - 1.0, (TITLE[1] + TITLE[3]) / 2.0, 5.5)]
GEMS = [(4.0, 56.0, 2.5), (4.0, 104.0, 2.5), (W - 4.0, 56.0, 2.5), (W - 4.0, 104.0, 2.5), (40.0, 4.0, 2.0), (216.0, 4.0, 2.0),
        (40.0, MH - 4.0, 2.0), (216.0, MH - 4.0, 2.0)]


def lights():
    """Every gem the screen lights (the medallions' too), round the panel clockwise from its top left: x, y, radius."""
    pts = [(x, y, 0.45 * r) for x, y, r in MEDALLIONS + PLAQUE_MEDALLIONS] + list(GEMS)
    start = math.atan2(-H / 2.0, -W / 2.0)
    return sorted(pts, key=lambda p: (math.atan2(p[1] - H / 2.0, p[0] - W / 2.0) - start) % (2 * math.pi))


class Frame:
    """gui/fusion_frame.png: the whole panel at twice size (512 x 512); the space window left clear."""
    SIZE = 512


class Space:
    """gui/fusion_space.png (512 x 512): space as seen through the window (at twice size), and wisps of nebula that
    drift across it (tiling round)."""
    SIZE = 512
    BASE = (0, 0, 2 * (WINDOW[2] - WINDOW[0]), 2 * (WINDOW[3] - WINDOW[1]))
    WISPS = (0, 288, 512, 224)


class Widgets:
    """gui/fusion_widgets.png (512 x 512), at twice size: x, y, width, height."""
    SIZE = 512
    PORT_STEEL = (0, 0, 44, 44)
    PORT_VIOLET = (44, 0, 44, 44)
    PORT_GOLD = (88, 0, 44, 44)
    PORT_GLOSS = (132, 0, 44, 44)
    ORB = (176, 0, 48, 48)          # four states side by side: ready, hovered, can't start, fusing
    ORB_RING = (368, 0, 64, 64)
    BLOOM = (432, 0, 64, 64)
    TUBE = (0, 64, 28, 252)
    PLASMA_VIOLET = (28, 64, 20, 244)
    PLASMA_GOLD = (48, 64, 20, 244)
    FLOW = (68, 64, 20, 128)
    HOLOGRAM = (88, 64, 312, 44)
    GLOSS = (88, 108, 64, 32)
    ARROW = (152, 108, 44, 30)
    SPARKLE = (196, 108, 16, 16)
    JEI_FRAME = (88, 140, 336, 236)
    REGIONS = [("ports", 0, 0, 176, 44), ("orbs", 176, 0, 192, 48), ("orb ring", 368, 0, 64, 64), ("bloom", 432, 0, 64, 64),
               ("tube", 0, 64, 28, 252), ("plasma", 28, 64, 40, 244), ("flow", 68, 64, 20, 128), ("hologram", 88, 64, 312, 44),
               ("gloss", 88, 108, 64, 32), ("arrow", 152, 108, 44, 30), ("sparkle", 196, 108, 16, 16), ("jei frame", 88, 140, 336, 236)]


class Planets:
    """effect/planets.png (256 x 256): the planets (no light on them: the shade and the rim are laid over them, turned
    toward the light), a ringed giant's ring in halves, a galaxy, glints, a shooting star, glows."""
    SIZE = 256
    GIANT = (0, 0, 64, 64)
    OCEAN = (64, 0, 48, 48)
    ICE = (112, 0, 48, 48)
    LAVA = (160, 0, 40, 40)
    MOON = (200, 0, 24, 24)
    SHADE = (0, 64, 64, 64)
    RIM = (64, 64, 64, 64)
    RING_BACK = (128, 64, 128, 20)
    RING_FRONT = (128, 84, 128, 20)
    GALAXY = (0, 128, 64, 64)
    GLINT = (64, 128, 32, 32)
    STREAK = (96, 128, 64, 8)
    ATMOS = (160, 128, 32, 32)
    GLOW = (192, 128, 64, 64)
    REGIONS = [("giant", 0, 0, 64, 64), ("ocean", 64, 0, 48, 48), ("ice", 112, 0, 48, 48), ("lava", 160, 0, 40, 40),
               ("moon", 200, 0, 24, 24), ("shade", 0, 64, 64, 64), ("rim", 64, 64, 64, 64), ("ring", 128, 64, 128, 40),
               ("galaxy", 0, 128, 64, 64), ("glint", 64, 128, 32, 32), ("streak", 96, 128, 64, 8), ("atmos", 160, 128, 32, 32),
               ("glow", 192, 128, 64, 64)]


class Jei:
    """JEI's page for a fusion: the ingredients on a ring round the catalyst (the singularity behind it), an arrow to
    the output, the energy and time along the bottom."""
    SIZE = (168, 118)
    CENTER = (62, 54)           # the catalyst's middle, and the ring's
    RING_R = 40                 # the ingredients' middles sit on this ring
    OUTPUT = (146, 46)          # the output's item, top left
    ARROW = (117, 47)           # 22 x 15
    TEXT_Y = 106


def java():
    def pts(name, points):
        return "    public static final int[][] %s = {%s};\n" % (name, ", ".join("{%d, %d}" % p for p in points))

    def arr(name, values):
        return "%s = {%s}" % (name, ", ".join(str(v) for v in values))

    def group(names, cls):
        return "    public static final int[] " + ", ".join(arr(prefix + n, getattr(cls, n)) for prefix, n in names) + ";\n"

    def floats(name, rows):
        return "    public static final float[][] %s = {%s};\n" % (name, ", ".join("{%s}" % ", ".join("%gF" % v for v in row) for row in rows))

    out = ["package com.reya.singularityfusion.gui;\n\n",
           "/** Generated by tools/layout.py: where everything is in the fusion core's screen and on its textures. Edit that file, not this one. */\n",
           "public final class Layouts {\n",
           "    public static final int W = %d, H = %d, MH = %d, FRAME = %d;\n" % (W, H, MH, FRAME),
           "    public static final int PANEL_X = %d, INV_X = %d, INV_Y = %d, HOTBAR_Y = %d;\n" % (PANEL_X, INV_X, INV_Y, HOTBAR_Y),
           "    public static final int[] %s, %s, %s, %s;\n" % (arr("TITLE", TITLE), arr("WINDOW", WINDOW), arr("HOLE", HOLE),
                                                          arr("INV_WINDOW", INV_WINDOW)),
           pts("PYLONS", PYLONS_LEFT + PYLONS_RIGHT),
           "    public static final int PORTHOLE = %d, ORB = %d;\n" % (PORTHOLE, ORB),
           "    public static final int[] %s, %s, %s;\n" % (arr("CATALYST", CATALYST), arr("START", START), arr("OUTPUT", OUTPUT)),
           "    public static final int[] %s, %s, %s;\n" % (arr("ENERGY", ENERGY), arr("PROGRESS", PROGRESS), arr("STATUS", STATUS)),
           "    /** The gems on the frame, round it clockwise from the top left: x, y, radius. */\n",
           floats("GEMS", lights()),
           "    public static final int FRAME_TEX = %d, SPACE_TEX = %d, WIDGETS_TEX = %d, PLANETS_TEX = %d;\n"
           % (Frame.SIZE, Space.SIZE, Widgets.SIZE, Planets.SIZE),
           group([("SPACE_", "BASE"), ("SPACE_", "WISPS")], Space),
           group([("W_", n) for n in ("PORT_STEEL", "PORT_VIOLET", "PORT_GOLD", "PORT_GLOSS", "ORB", "ORB_RING", "BLOOM")], Widgets),
           group([("W_", n) for n in ("TUBE", "PLASMA_VIOLET", "PLASMA_GOLD", "FLOW", "HOLOGRAM", "GLOSS", "ARROW", "SPARKLE",
                                      "JEI_FRAME")], Widgets),
           group([("P_", n) for n in ("GIANT", "OCEAN", "ICE", "LAVA", "MOON", "SHADE", "RIM")], Planets),
           group([("P_", n) for n in ("RING_BACK", "RING_FRONT", "GALAXY", "GLINT", "STREAK", "ATMOS", "GLOW")], Planets),
           "    public static final int[] %s, %s, %s, %s;\n" % (arr("JEI", Jei.SIZE), arr("JEI_CENTER", Jei.CENTER),
                                                          arr("JEI_OUTPUT", Jei.OUTPUT), arr("JEI_ARROW", Jei.ARROW)),
           "    public static final int JEI_RING_R = %d, JEI_TEXT_Y = %d;\n" % (Jei.RING_R, Jei.TEXT_Y),
           "\n    private Layouts() {\n    }\n}\n"]
    return "".join(out)


def main():
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write(java())
    print("wrote " + os.path.relpath(JAVA_OUT, ROOT))


if __name__ == "__main__":
    main()
