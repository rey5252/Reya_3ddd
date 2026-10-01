"""The second-generation machine GUIs, each in its own look: their panels, widget sheets and gloss masks.

    python3 tools/machines/gen_v2.py

  rune_altar   the rune sanctum (sanctum.py)
  terra_plate  the celestial astrolabe (astrolabe.py)

Each writes textures/gui/<key>.png (the panel, with the texture's margin round it), <key>_widgets.png (its
sheet: the shared pieces where machine/client/MachineScreen.java reads them, its own elsewhere) and
<key>_gloss.png (white where a shine sweeps over the panel). The numbers are layouts.py's.
"""
import os

from pix import ASSETS
import astrolabe
import sanctum

MACHINES = {"rune_altar": sanctum, "terra_plate": astrolabe}


def build(key):
    mod = MACHINES[key]
    panel = mod.panel()
    return panel, mod.sheet(), mod.gloss(panel)


def main():
    for key in MACHINES:
        panel, sheet, gloss = build(key)
        gui = os.path.join(ASSETS, "textures", "gui")
        panel.save(os.path.join(gui, key + ".png"))
        sheet.save(os.path.join(gui, key + "_widgets.png"))
        gloss.save(os.path.join(gui, key + "_gloss.png"))
        print("wrote " + key)


if __name__ == "__main__":
    main()
