"""The layout every machine's GUI shares (machine/MachineMenu.java, machine/client/MachineScreen.java), and
each machine's own: where its heart is drawn, its special slots, its status gem. check_layout.py keeps the
Java constants in step with these.

Coordinates are the menu's: the panel is W x H, its texture has a margin M round it (the corner crystals and
the plaque's ends reach over the frame).
"""
import math

M = 12
W, H = 240, 214
MACHINE_H = 124
INV_X1, INV_X2 = 28, 212

INPUT = (18, 30)                     # the first input slot's item corner; three rows of three, 18 apart
OUTPUT = (168, 30)                   # the first output slot's item corner
INV_Y, HOTBAR_Y, INV_SLOT_X = 132, 190, 40
ARROWS = [(74, 53), (155, 53)]       # the arrows' top-left corners (12 x 9); the screen lights them
ARROW_W, ARROW_H = 12, 9
BAR = (64, 108, 176, 114)            # the mana bar's inside
BUTTONS = [(18, 102)]                # the redstone button (16 x 16)
POOL = (206, 102)                    # the pool light (16 x 16)
# the mana crystals on the panels' corners (their middles): the screen makes them twinkle
LIGHTS = [(1, 1), (1, 122), (238, 122), (29, 212), (210, 212)]
SPECIAL_SLOT = (112, 84)             # a special slot under the heart (the altar's reagent, the infuser's catalyst)


def ring_spots(cx, cy, r, n, start_deg, size):
    """Top-left corners of n squares of `size` round (cx, cy) at distance r, clockwise from start_deg."""
    spots = []
    for k in range(n):
        a = math.radians(start_deg + k * 360.0 / n)
        spots.append((int(round(cx + math.cos(a) * r - size / 2.0)), int(round(cy + math.sin(a) * r - size / 2.0))))
    return spots


def ring_points(cx, cy, r, n, start_deg):
    """n points round (cx, cy) at distance r, clockwise from start_deg (rounded to pixels)."""
    pts = []
    for k in range(n):
        a = math.radians(start_deg + k * 360.0 / n)
        pts.append((int(round(cx + math.cos(a) * r)), int(round(cy + math.sin(a) * r))))
    return pts


# ---------------------------------------------------------------- the Runic Altar

class ALTAR:
    CENTER = (120, 46)               # the middle of the altar's top (seen from above)
    R = 21                           # its outer radius
    RECESS_R = 9.5                   # the dark hollow in its middle, where the rune takes shape
    ORBIT_R = 30                     # the ingredients circle round it at this distance
    RUNE_R = 14.5                    # the eight runes carved round its top
    RUNE_SIZE = 5
    RUNES = ring_spots(120, 46, 14.5, 8, -90 + 22.5, 5)
    GEM = (120, 25)                  # the keystone gem on its rim
    SLOT = SPECIAL_SLOT              # the livingrock (reagent) slot under it
    STONES = [(120, 72), (120, 78)]  # two little stones between the altar and that slot (their middles)
    HEART_R = 35                     # the heart's tooltip shows within this distance of its middle


# ---------------------------------------------------------------- the Terrestrial Plate

class PLATE:
    CENTER = (120, 52)               # the middle of the plate
    R = 21                           # the octagon's inradius
    CORE_R = 6.5                     # the dark core where the light gathers
    SOCKET_R = 28                    # the ingredients hover over three sockets this far out
    SOCKET_SIZE = 5.5
    SOCKETS = ring_points(120, 52, 28, 3, -90)
    GEM = (120, 76)
    HEART_R = 36


# ---------------------------------------------------------------- the Mana Infuser

class POOL_:
    CENTER = (120, 50)               # the middle of the basin's rim (seen from a little above)
    RX, RY = 27, 10                  # the rim's outer half-axes
    SURFACE_RX, SURFACE_RY = 23, 7   # the mana's (the screen draws it)
    WALL_H = 12                      # the basin's front wall below the rim
    ITEM = (120, 27)                 # the middle of the item being infused, floating over the mana
    ITEM_RING_R = 12                 # the progress ring round it
    GEM = (120, 66)                  # the status gem set in the front wall
    SLOT = SPECIAL_SLOT              # the catalyst slot the basin stands on
    HEART_BOX = (90, 12, 150, 80)


# ---------------------------------------------------------------- the Pure Daisy

class DAISY:
    CENTER = (120, 52)               # the daisy's bed in the middle of a round meadow
    MEADOW_R = 33
    BED_R = 10.5
    RING_R = 24                      # the eight stones round it (the blocks being purified), as round the real daisy
    STONE_R = 7.5
    CELLS = ring_points(120, 52, 24, 8, -90 + 22.5)
    GEM = (120, 19)                  # the status gem on the meadow's top edge
    HEART_R = 34


# ---------------------------------------------------------------- the Petal Apothecary

class BOWL:
    CENTER = (120, 46)               # the middle of the bowl's rim (seen from a little above)
    RX, RY = 25, 8                   # the rim's outer half-axes
    WATER_RX, WATER_RY = 21, 5       # the water's (the screen draws it)
    BODY_H = 15                      # the bowl's body under the rim, narrowing to its foot
    ITEM = (120, 22)                 # the flower taking shape over the water
    ITEM_RING_R = 11
    GEM = (120, 57)                  # the status gem on the bowl's front
    SLOT = SPECIAL_SLOT              # the seeds slot its stem stands on
    HEART_BOX = (90, 8, 150, 80)


# ---------------------------------------------------------------- the Petal Farm

class FARM:
    BED = (88, 50, 152, 72)          # the planter: x1, the soil's line, x2, its bottom
    SOIL_TOP = 45                    # the top of the soil seen over its front
    SUN = (120, 46)                  # the middle of the arc the sun runs along over the bed
    SUN_R = 31
    GEM = (120, 62)                  # the status gem on the planter's front
    SLOT = SPECIAL_SLOT              # the fertilizer slot under the planter
    HEART_BOX = (86, 10, 154, 80)


# ---------------------------------------------------------------- the Orechid Mine

class MINE:
    CENTER = (120, 50)               # the middle of the window into the rock
    R = 19                           # the window's outer radius (a gold and livingrock rim)
    ROCK_R = 15                      # the rock inside it (the screen draws the block and its cracks)
    ORE_R = 30                       # the possible ores sit in sockets on a ring this far out
    ORE_SOCKET_R = 6.5
    ORES = ring_points(120, 50, 30, 8, -90 + 22.5)
    GEM = (120, 31)                  # the keystone gem on the window's rim
    HEART_R = 37


# ---------------------------------------------------------------- the Crop Field

class FIELD:
    BOX = (88, 24, 152, 76)          # the field's livingwood frame: x1, y1, x2, y2
    ROWS = [38, 54, 70]              # the furrows' lines, where the crops stand
    CLOUD = (120, 14)                # where the little rain cloud floats (the screen draws it)
    GEM = (120, 76)                  # the status gem on the frame's bottom edge
    SLOT = SPECIAL_SLOT              # the bone meal slot under the field
    HEART_BOX = (86, 6, 154, 80)
