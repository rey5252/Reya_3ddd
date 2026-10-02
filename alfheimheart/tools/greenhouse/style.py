"""The GUI's palette, shared by gen_gui.py, gen_widgets.py and gen_mascot.py: a deep navy panel with a
bright green edge, curly leaf vines, little gold lights, a parchment scroll for the title and a red
close button.

Every ramp runs from the lightest tone (0) to the darkest (last).
"""
from pix import hexc

# the darkest line round everything (navy black) and the vines' own dark outline (green black)
OUT = hexc("070B19")
VOUT = hexc("0B2A1A")

# navy: the panel's inside, from its lit bevel to its darkest slot
N0, N1, N2, N3, N4, N5 = (hexc(h) for h in ("3A509A", "2A3D78", "1F2F62", "182653", "111C40", "0A1129"))

# green: the frame's edge and the vines
G0, G1, G2, G3, G4, G5 = (hexc(h) for h in ("DDF9A6", "A4EC5C", "62CC42", "37993C", "1E6534", "0F3D22"))

# sage: the slots' outline (a calmer green)
S0, S1, S2, S3, S4 = (hexc(h) for h in ("CDE3B0", "A2C68B", "76A26C", "4F7752", "34503A"))

# gold: the lights
Y0, Y1, Y2, Y3, Y4 = (hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308"))

# parchment: the title scroll and the speech bubble
P0, P1, P2, P3, P4, P5 = (hexc(h) for h in ("FFF9E0", "F8E8B8", "EAD092", "CBAA68", "8F6C38", "4E3417"))

# red: the close button
R0, R1, R2, R3, R4 = (hexc(h) for h in ("FF9A88", "F2553F", "C82C26", "8C1518", "4C0A12"))

# mana (as in Botania's blue) and the flower planters' soil
M0, M1, M2, M3, M4, M5 = (hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "123C7C"))
SOIL0, SOIL1, SOIL2 = hexc("4A3428"), hexc("33231F"), hexc("21161A")
