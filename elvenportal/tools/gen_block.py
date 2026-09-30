"""The portal block's own textures (the arch itself uses Botania's: glimmering dreamwood, dreamwood,
livingrock and the Elven Gateway Core):

    python3 tools/gen_block.py

  textures/block/portal_swirl.png   the portal inside the arch, 16 frames of 16x16 (drawn by the block's
                                    renderer while the portal is open, and in the item's model)
  textures/block/natura_crystal.png the little green crystals at the arch's feet
"""
import json
import os

from pix import Canvas, ASSETS, hexc, mix
from style import G0, G1, G2, G3, G4
import swirl as S

FRAMES = 16
# the opening in the arch, in texture pixels (x from the left, y from the top): the swirl's oval fills it
OPENING_CX, OPENING_CY, OPENING_RX, OPENING_RY = 8.0, 8.5, 5.2, 5.7


def portal_swirl():
    cv = Canvas(16, 16 * FRAMES)
    for f in range(FRAMES):
        grid = S.swirl_frame(16, 16, f / FRAMES, cx=OPENING_CX, cy=OPENING_CY, rx=OPENING_RX, ry=OPENING_RY, motes=3, seed=4)
        S.put(cv, grid, 0, 16 * f)
    path = os.path.join(ASSETS, "textures", "block", "portal_swirl.png")
    cv.save(path)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"animation": {"frametime": 2}}, f, indent=2)
        f.write("\n")


def natura_crystal():
    """Rows of green from light to dark with a white glint: the crystals' faces take a few pixels of it."""
    cv = Canvas(16, 16)
    ramp = [hexc("F4FFE8"), G0, G1, G2, G3, G4]
    for y in range(16):
        for x in range(16):
            c = ramp[min(len(ramp) - 1, 1 + (y + x // 4) // 3)]
            if (x + y) % 7 == 0:
                c = mix(c, ramp[0], 0.5)
            cv.set(x, y, c)
    cv.save(os.path.join(ASSETS, "textures", "block", "natura_crystal.png"))


if __name__ == "__main__":
    portal_swirl()
    natura_crystal()
