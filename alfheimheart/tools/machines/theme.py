"""What every second-generation machine GUI is built of, each in its machine's own materials (a Theme): the
panel's frame, the player's inventory on its panel, the slots, the title plaque, the round buttons, the status
gems, the pool lamp, the soft halos; and the gloss mask (where a shine sweeps over the panel).

The sheet every machine has (textures/gui/<key>_widgets.png, 256 x 128) keeps the shared pieces where
machine/client/MachineScreen.java reads them (layouts.py's SHARED_REGIONS); the rest of it is the machine's own
(its layout's SHEET).
"""
import math

import art as A
from pix import Canvas, hexc
from layouts import W, H, MACHINE_H, INV_X, INV_Y, HOTBAR_Y, INV_PANEL, M

from layouts import (SHEET_W, SHEET_H, BUTTON_UV, ICON_UV, CLOSE_UV, GLOW_UV, GLOW_SIZE, GEM_UV, GEM_SIZE, PLAQUE_V,  # noqa: F401
                     PLAQUE_CAP, PLAQUE_TILE_U, PLAQUE_TILE_W, HALO_UV, HALO_SIZE, POOL_UV, POOL_SIZE, SHARED_REGIONS)


class Theme:
    """A machine's materials. Colours are (r, g, b)."""

    def __init__(self, outline, metal, groove, stone, stone_noise, floor_shadow, slot_rim, slot_inside, inv_bg,
                 plaque_face, plaque_text_hint, button_face, crystal, salt=0):
        self.outline = outline              # the darkest line round everything
        self.metal = metal                  # (bright, lit, mid, shadow, deep): the frame's trim
        self.groove = groove                # the dark groove mana runs along
        self.stone = stone                  # (lit, mid, shadow): the frame's band
        self.stone_noise = stone_noise
        self.floor_shadow = floor_shadow    # the shadow the frame casts on the panel
        self.slot_rim = slot_rim            # (lit, mid, shadow)
        self.slot_inside = slot_inside      # (top, bottom)
        self.inv_bg = inv_bg                # fill(x, y) of the inventory panel
        self.plaque_face = plaque_face      # (top, bottom) of the title plaque
        self.plaque_text_hint = plaque_text_hint
        self.button_face = button_face      # (lit, mid, shadow) of the round buttons' faces
        self.crystal = crystal              # (bright, light, mid, dark, deep): the corner gems
        self.salt = salt


# ---------------------------------------------------------------- frames

def frame_colour(th, depth, k, x, y):
    """The frame's colour `depth` pixels in from the edge (k: how much that edge faces the light)."""
    m = th.metal
    if depth < 1:
        return th.outline
    if depth < 3:                                                    # the metal trim, bevelled
        if depth < 2:
            return m[0] if k > 0.5 else (m[1] if k > 0.0 else (m[3] if k < -0.5 else m[2]))
        return m[1] if k > 0.3 else (m[3] if k < -0.3 else m[2])
    if depth < 4:
        return th.groove                                             # the groove (mana runs along it)
    if depth < 7:                                                    # the stone band
        lit, mid, shade = th.stone
        c = A.ramp([(0.0, shade), (0.5, mid), (1.0, lit)], 0.5 + 0.5 * k if depth < 4.6 or depth > 6 else 0.5)
        n = A.noise(x, y, 2.0, th.salt + 5)
        c = A.tint(c, (n - 0.5) * th.stone_noise)
        if depth >= 6:
            c = A.dark(c, 0.25)
        return c
    return None


def panel(cv, x1, y1, x2, y2, th, fill, r=5, shadow_rows=(0.42, 0.26, 0.13, 0.05)):
    """A panel: its frame (outline, metal trim, groove, stone band) round fill(x, y), the frame's shadow on it."""
    f = A.box(x1, y1, x2, y2, r)
    for x, y, depth in A.bounds(f, x1, y1, x2, y2):
        k = A.facing(f, x + 0.5, y + 0.5)
        c = frame_colour(th, depth, k, x, y)
        if c is None:
            c = fill(x, y)
            s = int(depth - 7)
            if s < len(shadow_rows):
                c = A.lerp(c, th.floor_shadow, shadow_rows[s])
        cv.set(x, y, c)


def corner_gem(cv, cx, cy, th):
    """A round gem set in metal on a panel's corner (its middle the pixel (cx, cy)): the screen twinkles it."""
    m, g = th.metal, th.crystal
    setting = A.circle(cx + 0.5, cy + 0.5, 4.6)
    A.draw(cv, setting, (cx - 6, cy - 6, cx + 7, cy + 7), m[2], outline=th.outline, bevel=1.5, hi=0.5, lo=0.45)
    stone = A.circle(cx + 0.5, cy + 0.5, 2.6)
    for x, y, d in A.bounds(stone, cx - 4, cy - 4, cx + 5, cy + 5):
        k = A.facing(stone, x + 0.5, y + 0.5)
        c = g[1] if k > 0.4 else (g[2] if k > -0.3 else g[3])
        cv.set(x, y, c)
    cv.set(cx - 1, cy - 1, g[0])


def inventory(cv, th):
    """The player's inventory on its own panel, hanging under the machine's, a little metal filigree in its
    bottom corners."""
    x1, x2 = INV_PANEL
    panel(cv, x1, MACHINE_H - 8, x2, H, th, th.inv_bg)
    m = th.metal
    for (cx, flip) in ((x1 + 8, 1), (x2 - 9, -1)):
        for i in range(6):
            cv.set(cx + flip * i, H - 9, m[2] if i < 5 else m[3])
            cv.set(cx, H - 9 - i, m[2] if i < 5 else m[3])
        cv.set(cx + flip, H - 10, m[1])
        cv.set(cx + flip * 2, H - 11, m[3])
    for r in range(3):
        for c in range(9):
            slot(cv, INV_X + c * 18, INV_Y + r * 18, th)
    for c in range(9):
        slot(cv, INV_X + c * 18, HOTBAR_Y, th)


def slot(cv, x, y, th, rim=None, inside=None, mark=None, mark_colour=None, mark_alpha=60):
    A.slot(cv, x, y, rim or th.slot_rim, inside or th.slot_inside, frame=None, corner=1, mark=mark, mark_colour=mark_colour,
           mark_alpha=mark_alpha)


# ---------------------------------------------------------------- the sheet's shared pieces

REDSTONE_ICONS = {
    "ignore": ["............",
               "....rr......",
               "...rRRr.rr..",
               "..rRwRrrRRr.",
               "...rRRRRRr..",
               "..rrRRrRr...",
               ".rRRr..rRr..",
               ".rRRRr.rRRr.",
               "..rrRRrrRr..",
               "....rRRr....",
               ".....rr.....",
               "............"],
    "high": ["....o.......",
             "...oRo......",
             "..oRwRo.....",
             "...oRo......",
             "...obo......",
             "...obo......",
             "...obo......",
             "...obo......",
             "...obo......",
             "...ooo......",
             "............",
             "............"],
    "low": ["....o.......",
            "...oko......",
            "..okKko.....",
            "...oko......",
            "...obo......",
            "...obo......",
            "...obo......",
            "...obo......",
            "...obo......",
            "...ooo......",
            "............",
            "............"],
}
RED = [hexc(h) for h in ("FF9A88", "F2553F", "C82C26", "8C1518", "4C0A12")]
ICON_PAL = {"r": RED[2], "R": RED[1], "w": (255, 255, 255), "o": hexc("2A1206"), "b": hexc("8A4020"), "k": hexc("4A4A4A"),
            "K": hexc("7A7A7A")}
CROSS = ["XX....XX",
         "XXX..XXX",
         ".XXXXXX.",
         "..XXXX..",
         "..XXXX..",
         ".XXXXXX.",
         "XXX..XXX",
         "XX....XX"]


def round_button(cv, x0, y0, th, state):
    """A round button 16x16: a metal ring round a stone face; hover brighter (a glow in the ring), pressed sunk."""
    m, face = th.metal, th.button_face
    cx, cy = x0 + 8.0, y0 + 8.0
    ring = A.circle(cx, cy, 7.6)
    inner = A.circle(cx, cy, 5.4)
    for x, y, d in A.bounds(ring, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(ring, x + 0.5, y + 0.5)
        if d <= 1.0:
            cv.set(x, y, th.outline)
            continue
        di = inner(x + 0.5, y + 0.5)
        if di > 0:                                                   # the metal ring
            if state == "pressed":
                k = -k
            c = m[1] if k > 0.35 else (m[3] if k < -0.35 else m[2])
            if state == "hover":
                c = A.light(c, 0.25)
            cv.set(x, y, c)
        elif di > -1.0:
            cv.set(x, y, th.outline)
        else:                                                        # the face, sunk a little
            kk = A.facing(inner, x + 0.5, y + 0.5)
            lit, mid, shade = face
            c = A.lerp(mid, shade, (y - y0 - 3) / 10.0)
            if di > -2.0:
                c = A.dark(c, 0.3) if kk > 0.2 else c
            if state == "hover":
                c = A.light(c, 0.12)
            if state == "pressed":
                c = A.dark(c, 0.2)
            cv.set(x, y, c)


def close_button(cv, x0, y0, th, state):
    round_button(cv, x0, y0, th, state)
    off = 1 if state == "pressed" else 0
    for shadow in (True, False):
        col = RED[4] if shadow else (RED[0] if state == "hover" else RED[1])
        d = off + (1 if shadow else 0)
        for j, row in enumerate(CROSS):
            for i, ch in enumerate(row):
                if ch == "X":
                    cv.set(x0 + 4 + i + d - 1, y0 + 4 + j + d - 1, col)


GEM_COLOURS = [
    [hexc(h) for h in ("F4FFE8", "A4EC5C", "62CC42", "1E6534")],     # working: green
    [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16")],     # idle: gold
    [hexc(h) for h in ("FFE4DC", "FF9A88", "C82C26", "4C0A12")],     # no mana, or the outputs are full: red
    [hexc(h) for h in ("E8E8F4", "A0A0B8", "6A6A84", "34344A")],     # stopped by redstone: grey
]


def gem(cv, x0, y0, colours):
    """A round faceted gem 7x7, lit from the top-left."""
    glint, light, mid, dark = colours
    f = A.circle(x0 + 3.5, y0 + 3.5, 3.5)
    for x, y, d in A.bounds(f, x0, y0, x0 + 7, y0 + 7):
        k = ((x + 0.5 - x0 - 3.5) * A.LIGHT[0] + (y + 0.5 - y0 - 3.5) * A.LIGHT[1]) / 3.5
        c = dark if d < 0.8 else (light if k > 0.25 else (mid if k > -0.35 else dark))
        cv.set(x, y, c)
    cv.set(x0 + 2, y0 + 2, glint)
    cv.set(x0 + 4, y0 + 5, A.light(mid, 0.3))


def soft_glow(cv, x0, y0, size, strength, hole=1.05, power=2.0):
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if d < hole:
                continue
            a = max(0.0, 1.0 - (d - hole) / (c + 0.5 - hole)) ** power
            if a > 0.02:
                cv.set(x0 + x, y0 + y, (255, 255, 255), int(a * strength))


def plaque(cv, th):
    """The title plaque's pieces: its ends (a gem set in metal) and two tiles of its face."""
    top, bottom = 3, 16
    face_top, face_bottom = th.plaque_face
    m = th.metal

    def face(j):
        if j == top or j == bottom:
            return th.outline
        if j == top + 1:
            return m[1]
        if j == bottom - 1:
            return m[3]
        return A.lerp(face_top, face_bottom, (j - top - 2) / float(bottom - top - 4))

    for t in range(2):
        x0 = PLAQUE_TILE_U + t * PLAQUE_TILE_W
        for i in range(PLAQUE_TILE_W):
            for j in range(top, bottom + 1):
                c = face(j)
                if top + 1 < j < bottom - 1:
                    c = A.tint(c, (A.noise(i + t * 8, j, 2.0, 31 + th.salt) - 0.5) * 0.12)
                cv.set(x0 + i, PLAQUE_V + j, c)
    for right in (False, True):
        x0 = PLAQUE_CAP if right else 0
        for k in range(16):
            x = x0 + (k if right else 15 - k)
            inset = max(0, k - 9)
            for j in range(top + inset, bottom - inset + 1):
                if k > 13:
                    continue
                edge = j in (top + inset, bottom - inset) or k == 13
                cv.set(x, PLAQUE_V + j, th.outline if edge else face(j))
        cx = x0 + (9 if right else 6)
        corner_gem(cv, cx, PLAQUE_V + 9, th)


def shared_pieces(cv, th):
    for i, s in enumerate(("normal", "hover", "pressed")):
        round_button(cv, BUTTON_UV[0] + i * 16, BUTTON_UV[1], th, s)
        close_button(cv, CLOSE_UV[0] + i * 16, CLOSE_UV[1], th, s)
    for i, key in enumerate(("ignore", "high", "low")):
        cv.sprite(REDSTONE_ICONS[key], ICON_UV[0] + i * 12, ICON_UV[1], ICON_PAL)
    soft_glow(cv, GLOW_UV[0], GLOW_UV[1], GLOW_SIZE, 125)
    for i, colours in enumerate(GEM_COLOURS):
        gem(cv, GEM_UV[0] + i * GEM_SIZE, GEM_UV[1], colours)
    plaque(cv, th)
    soft_glow(cv, HALO_UV[0], HALO_UV[1], HALO_SIZE, 235, hole=0.0, power=1.6)


def new_panel():
    return Canvas(W + 2 * M, H + 2 * M, M, M)


def new_sheet():
    return Canvas(SHEET_W, SHEET_H)
