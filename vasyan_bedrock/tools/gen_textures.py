"""Generates every texture of the add-on: the 64x64 skins of Vasyan (robot with a monitor for a head) and
Klod (white robot with a visor), each also with an open mouth for talking, the two chip item icons and
both pack icons.

Run from the vasyan_bedrock folder:  python3 tools/gen_textures.py [--preview DIR]
Skin layout matches models/entity/vasyan.geo.json (standard 64x64 humanoid box UVs + antenna).
"""
import os, struct, sys, zlib

RP = "resource_pack"
BP = "behavior_pack"

CLEAR = (0, 0, 0, 0)
RED = (232, 64, 64)
YELLOW = (250, 210, 70)
GREEN = (90, 220, 110)

# Vasyan: grey monitor head, navy suit, cyan screen
VASYAN = {
    "frame": (126, 132, 148), "frame_light": (168, 174, 190), "frame_dark": (70, 74, 88), "outline": (40, 42, 52),
    "screen": (12, 26, 44), "glow": (64, 216, 255), "glow_dim": (30, 120, 160),
    "suit": (64, 74, 104), "suit_dark": (42, 48, 70), "suit_light": (92, 104, 140),
    "leg": (52, 56, 68), "boot": (28, 30, 38), "ball": RED, "ball_light": (255, 180, 180),
}
# Klod: white plating, coral accents, dark visor with warm orange eyes
KLOD = {
    "frame": (226, 222, 214), "frame_light": (248, 246, 240), "frame_dark": (170, 164, 156), "outline": (96, 88, 82),
    "screen": (30, 22, 20), "glow": (255, 150, 90), "glow_dim": (196, 96, 56),
    "suit": (214, 208, 198), "suit_dark": (176, 168, 158), "suit_light": (240, 236, 228),
    "leg": (120, 112, 106), "boot": (70, 62, 58), "ball": (240, 120, 80), "ball_light": (255, 210, 180),
}


def png(path, px, scale=1):
    h, w = len(px), len(px[0])
    rows = []
    for y in range(h * scale):
        row = b"\x00"
        for x in range(w * scale):
            row += bytes(px[y // scale][x // scale])
        rows.append(row)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w * scale, h * scale, 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(b"".join(rows), 9)) + ch(b"IEND", b""))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rnd(x, y, salt=0):
    h = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def grid(w, h, c=CLEAR):
    return [[c] * w for _ in range(h)]


def put(px, x, y, c):
    px[y][x] = c + (255,) if len(c) == 3 else c


def fill(px, x0, y0, w, h, c, noise=None, salt=0):
    """Fills a rectangle; with noise=(other, chance/256) some pixels are swapped for the other colour."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if noise and rnd(x, y, salt) < noise[1]:
                put(px, x, y, noise[0])
            else:
                put(px, x, y, c)


def box(u, v, w, h, d):
    """Box UV faces of a cube: name -> (x, y, width, height) on the texture."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def frame8(px, x0, y0, p):
    fill(px, x0, y0, 8, 8, p["frame"])
    for i in range(8):
        put(px, x0 + i, y0, p["frame_light"])
        put(px, x0, y0 + i, p["frame_light"])
        put(px, x0 + i, y0 + 7, p["frame_dark"])
        put(px, x0 + 7, y0 + i, p["frame_dark"])


def face_vasyan(px, x0, y0, p, talk=False):
    """The derpy monitor face: one big eye, one tiny eye, wobbly smile (or an open mouth)."""
    frame8(px, x0, y0, p)
    fill(px, x0 + 1, y0 + 1, 6, 6, p["screen"])
    for (x, y) in ((1, 1), (2, 1), (1, 2)):
        put(px, x0 + x, y0 + y, mix(p["screen"], p["glow"], 0.18))  # glare in the corner
    for (x, y) in ((2, 2), (3, 2), (2, 3), (3, 3)):
        put(px, x0 + x, y0 + y, p["glow"])
    put(px, x0 + 2, y0 + 2, (200, 250, 255))
    put(px, x0 + 5, y0 + 3, p["glow"])
    if talk:
        for (x, y) in ((2, 5), (3, 5), (4, 5), (5, 5), (2, 6), (5, 6)):
            put(px, x0 + x, y0 + y, p["glow"])
        put(px, x0 + 3, y0 + 6, p["glow_dim"]); put(px, x0 + 4, y0 + 6, p["glow_dim"])
    else:
        for (x, y) in ((2, 5), (3, 6), (4, 5), (5, 6)):
            put(px, x0 + x, y0 + y, p["glow_dim"] if y == 6 else p["glow"])


def face_klod(px, x0, y0, p, talk=False):
    """A calm visor: two level eyes and a small smile (or a speaking bar)."""
    frame8(px, x0, y0, p)
    fill(px, x0 + 1, y0 + 2, 6, 4, p["screen"])
    for i in range(1, 7):
        put(px, x0 + i, y0 + 2, mix(p["screen"], p["glow"], 0.15))  # visor glare
    put(px, x0 + 2, y0 + 3, p["glow"]); put(px, x0 + 5, y0 + 3, p["glow"])
    put(px, x0 + 1, y0 + 3, p["glow_dim"]); put(px, x0 + 6, y0 + 3, p["glow_dim"])
    if talk:
        for i in (2, 3, 4, 5):
            put(px, x0 + i, y0 + 5, p["glow"])
        put(px, x0 + 3, y0 + 4, p["glow_dim"]); put(px, x0 + 4, y0 + 4, p["glow_dim"])
    else:
        put(px, x0 + 3, y0 + 5, p["glow_dim"]); put(px, x0 + 4, y0 + 5, p["glow_dim"])
        put(px, x0 + 2, y0 + 4, p["glow_dim"]); put(px, x0 + 5, y0 + 4, p["glow_dim"])
    fill(px, x0 + 1, y0 + 6, 6, 1, p["frame_dark"])  # chin plate


def head(px, p, face, talk):
    f = box(0, 0, 8, 8, 8)
    for name in ("right", "left", "back"):
        x, y, w, h = f[name]
        fill(px, x, y, w, h, p["frame"], (mix(p["frame"], p["frame_dark"], 0.4), 30), salt=1)
        for i in range(w):
            put(px, x + i, y, p["frame_light"])
            put(px, x + i, y + h - 1, p["frame_dark"])
        for row in (2, 4):  # cooling vents
            for i in range(1, w - 1):
                put(px, x + i, y + row, p["outline"] if i % 2 else p["frame_dark"])
    x, y, w, h = f["back"]
    put(px, x + 6, y + 6, RED)  # power led
    x, y, w, h = f["top"]
    fill(px, x, y, w, h, p["frame_light"], (p["frame"], 50), salt=2)
    put(px, x + 3, y + 3, p["outline"]); put(px, x + 4, y + 4, p["outline"])
    x, y, w, h = f["bottom"]
    fill(px, x, y, w, h, p["frame_dark"])
    x, y, w, h = f["front"]
    face(px, x, y, p, talk)


def antenna(px, p):
    f = box(32, 0, 1, 3, 1)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, p["outline"] if name in ("right", "back") else p["frame_dark"])
    f = box(40, 0, 2, 2, 2)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, mix(p["ball"], (90, 10, 10), 0.4) if name in ("bottom", "back", "right") else p["ball"])
    x, y, w, h = f["front"]
    put(px, x, y, p["ball_light"])
    x, y, w, h = f["top"]
    put(px, x, y, p["ball_light"])


def body(px, p, klod):
    f = box(16, 16, 8, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, p["suit"], (p["suit_dark"], 28), salt=3)
    x, y, w, h = f["front"]
    for i in range(w):  # collar and belt
        put(px, x + i, y, p["suit_light"])
        put(px, x + i, y + 9, p["outline"])
    put(px, x + 3, y + 9, p["frame_light"]); put(px, x + 4, y + 9, p["frame_light"])
    if klod:
        # coral stripes down the sides and a round core light in the chest
        for r in range(1, 9):
            put(px, x, y + r, p["glow_dim"]); put(px, x + w - 1, y + r, p["glow_dim"])
        fill(px, x + 3, y + 3, 2, 2, p["glow"])
        for (cx, cy) in ((3, 2), (4, 2), (2, 3), (5, 3), (2, 4), (5, 4), (3, 5), (4, 5)):
            put(px, x + cx, y + cy, p["glow_dim"])
        put(px, x + 3, y + 3, (255, 230, 200))
    else:
        fill(px, x + 2, y + 2, 4, 4, p["screen"])  # chest display with three status lights
        for i in range(4):
            put(px, x + 2 + i, y + 2, p["frame_dark"])
        put(px, x + 2, y + 4, RED); put(px, x + 3, y + 4, YELLOW); put(px, x + 4, y + 4, GREEN)
        put(px, x + 5, y + 4, mix(p["screen"], p["glow"], 0.3))
        fill(px, x + 2, y + 5, 4, 1, p["glow_dim"])
        for i in range(2):
            put(px, x + 3 + i, y + 7, p["frame_dark"])  # little hatch under the display
    x, y, w, h = f["back"]
    for row in range(2, 8, 2):
        for i in range(2, 6):
            put(px, x + i, y + row, p["suit_dark"])
    for i in range(w):
        put(px, x + i, y + 9, p["outline"])


def arm(px, p, u, v):
    f = box(u, v, 4, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, p["suit"], (p["suit_dark"], 24), salt=u + v)
    for name in ("right", "front", "left", "back"):
        x, y, w, h = f[name]
        for i in range(w):
            put(px, x + i, y + 4, p["outline"])  # elbow joint
            put(px, x + i, y + 5, p["glow_dim"] if name in ("front", "back") else p["frame_dark"])
            for r in range(9, 12):  # metal hand
                put(px, x + i, y + r, p["frame"] if (r + i) % 3 else p["frame_dark"])
    x, y, w, h = f["top"]
    fill(px, x, y, w, h, p["suit_light"])
    x, y, w, h = f["bottom"]
    fill(px, x, y, w, h, p["frame_dark"])


def leg(px, p, u, v):
    f = box(u, v, 4, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(px, x, y, w, h, p["leg"], (mix(p["leg"], p["outline"], 0.5), 30), salt=u * 3 + v)
    for name in ("right", "front", "left", "back"):
        x, y, w, h = f[name]
        for i in range(w):
            put(px, x + i, y + 5, p["outline"])  # knee
            for r in (10, 11):
                put(px, x + i, y + r, p["boot"])
        if name == "front":
            put(px, x + 1, y + 5, p["glow_dim"]); put(px, x + 2, y + 5, p["glow_dim"])
            for i in range(w):
                put(px, x + i, y + 10, mix(p["boot"], p["frame"], 0.35))
    x, y, w, h = f["bottom"]
    fill(px, x, y, w, h, p["boot"])


def skin(klod=False, talk=False):
    p = KLOD if klod else VASYAN
    px = grid(64, 64)
    head(px, p, face_klod if klod else face_vasyan, talk)
    antenna(px, p)
    body(px, p, klod)
    arm(px, p, 40, 16)  # right arm
    arm(px, p, 32, 48)  # left arm
    leg(px, p, 0, 16)   # right leg
    leg(px, p, 16, 48)  # left leg
    return px


def chip(board, board_dark, board_light, core, core_dim):
    """16x16 item: a circuit board chip with gold pins and a glowing core."""
    px = grid(16, 16)
    gold, gold_d = (240, 200, 80), (170, 120, 30)
    for i in range(4, 12, 2):  # pins on all four sides
        for (x, y) in ((i, 1), (i, 2), (i, 13), (i, 14), (1, i), (2, i), (13, i), (14, i)):
            put(px, x, y, gold if (x + y) % 2 else gold_d)
    fill(px, 3, 3, 10, 10, board, (board_dark, 60), salt=7)
    for i in range(3, 13):
        put(px, i, 3, board_light); put(px, 3, i, board_light)
        put(px, i, 12, board_dark); put(px, 12, i, board_dark)
    fill(px, 5, 5, 6, 6, (40, 42, 52))
    fill(px, 6, 6, 4, 4, core_dim)
    fill(px, 7, 7, 2, 2, core)
    put(px, 7, 7, mix(core, (255, 255, 255), 0.7))
    for (x, y) in ((4, 8), (11, 7), (8, 4), (7, 11)):  # traces
        put(px, x, y, gold)
    return px


def vasyan_chip():
    return chip((34, 120, 70), (16, 70, 40), (70, 170, 100), VASYAN["glow"], VASYAN["glow_dim"])


def klod_chip():
    return chip((60, 50, 46), (34, 28, 26), (110, 96, 88), KLOD["glow"], KLOD["glow_dim"])


def icon():
    """32x32 pack icon: both heads side by side on a dark tile."""
    px = grid(32, 32, (22, 26, 40, 255))
    for y in range(32):
        for x in range(32):
            if rnd(x, y, 9) < 20:
                put(px, x, y, (30, 36, 54))
    for (x0, p, face) in ((1, VASYAN, face_vasyan), (17, KLOD, face_klod)):
        fill(px, x0 + 6, 6, 2, 4, p["frame_dark"])
        fill(px, x0 + 5, 4, 4, 2, p["ball"])
        small = grid(8, 8)
        face(small, 0, 0, p)
        for y in range(14):
            for x in range(14):
                px[10 + y][x0 + x] = small[y * 8 // 14][x * 8 // 14]
    return px


def main():
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    out = {
        f"{RP}/textures/entity/vasyan.png": (skin(), 1),
        f"{RP}/textures/entity/vasyan_talk.png": (skin(talk=True), 1),
        f"{RP}/textures/entity/klod.png": (skin(klod=True), 1),
        f"{RP}/textures/entity/klod_talk.png": (skin(klod=True, talk=True), 1),
        f"{RP}/textures/items/ai_chip.png": (vasyan_chip(), 1),
        f"{RP}/textures/items/klod_chip.png": (klod_chip(), 1),
        f"{RP}/pack_icon.png": (icon(), 4),
        f"{BP}/pack_icon.png": (icon(), 4),
    }
    for path, (px, scale) in out.items():
        png(path, px, scale)
        print("wrote", path)
    if "--preview" in sys.argv:
        d = sys.argv[sys.argv.index("--preview") + 1]
        png(os.path.join(d, "vasyan_skin_x8.png"), skin(), 8)
        png(os.path.join(d, "klod_skin_x8.png"), skin(klod=True), 8)
        png(os.path.join(d, "klod_talk_x8.png"), skin(klod=True, talk=True), 8)
        png(os.path.join(d, "chips_x16.png"), [a + b for a, b in zip(vasyan_chip(), klod_chip())], 16)
        png(os.path.join(d, "pack_icon_x8.png"), icon(), 8)
        print("previews in", d)


if __name__ == "__main__":
    main()
