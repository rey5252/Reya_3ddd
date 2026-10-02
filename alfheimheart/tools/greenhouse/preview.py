"""Preview images of the GUI, to look at the pixel art before a game run.

    python3 tools/preview.py [uk]      writes build/preview/screen.png: the whole screen at GUI scale 3, the
                                       way client/GreenhouseScreen.java puts the pieces together (with stand-in
                                       items where the game shows Botania's)

`preview_gui` (used by gen_gui.py --preview) shows just the panel texture, with Botania's own item icons if a
clone of VazkiiMods/Botania is next to this repo (the BOTANIA env var or /home/user/vazkiimods/botania).
"""
import math
import os
import re
import sys

from PIL import Image

from pix import ROOT, ASSETS, upscale, mix, rnd2
import style as st

BOTANIA = os.environ.get("BOTANIA", "/home/user/vazkiimods/botania")
TEX = os.path.join(BOTANIA, "Xplat", "src", "main", "resources", "assets", "botania", "textures")
OUT_DIR = os.path.join(ROOT, "build", "preview")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reya", "alfheimheart", "greenhouse", "client")


def botania_icon(name):
    for sub in ("block", "item"):
        p = os.path.join(TEX, sub, name + ".png")
        if os.path.exists(p):
            img = Image.open(p).convert("RGBA")
            return img.crop((0, 0, 16, 16))
    return None


def preview_gui(cv, extra=None, scale=3, name="gui.png"):
    from gen_gui import M, FLOWERS, CHARGE
    img = cv.img.copy()
    bg = Image.new("RGBA", (img.width + 40, img.height + 40), (40, 44, 52, 255))
    bg.alpha_composite(img, (20, 20))
    ox, oy = 20 + M, 20 + M
    flowers = ["endoflame", "hydroangeas", "thermalily", "rosa_arcana", "munchdew", "kekimurus", "gourmaryllis", "spectrolus"]
    for (x, y), f in zip(FLOWERS, flowers):
        icon = botania_icon(f)
        if icon:
            bg.alpha_composite(icon, (ox + x, oy + y))
    tablet = botania_icon("mana_tablet")
    if tablet:
        bg.alpha_composite(tablet, (ox + CHARGE[0], oy + CHARGE[1]))
    if extra:
        extra(bg, ox, oy)
    os.makedirs(OUT_DIR, exist_ok=True)
    out = upscale(bg, scale)
    out.save(os.path.join(OUT_DIR, name))
    return out


# ---------------------------------------------------------------- the plate font, read from the Java source

def plate_glyphs():
    src = open(os.path.join(JAVA, "PlateFont.java"), encoding="utf-8").read()
    glyphs = {}
    for m in re.finditer(r'put\("([^"]+)",\s*((?:"[^"]*"(?:,\s*)?)+)\);', src):
        rows = re.findall(r'"([^"]*)"', m.group(2))
        if len(rows) != 7:
            rows = ["." * len(rows[0])] + rows + ["." * len(rows[0])]
        for ch in m.group(1):
            glyphs[ch] = rows
    return glyphs


def plate_width(glyphs, text):
    w = 0
    for i, ch in enumerate(text.upper()):
        if i:
            w += 1
        g = glyphs.get(ch)
        w += 3 if g is None else len(g[1])
    return w


def plate_draw(img, glyphs, text, x, y, color):
    px = img.load()
    for ch in text.upper():
        g = glyphs.get(ch)
        if g is None:
            x += 4
            continue
        for r, row in enumerate(g):
            for c, cell in enumerate(row):
                if cell == "X" and 0 <= x + c < img.width and 0 <= y - 1 + r < img.height:
                    px[x + c, y - 1 + r] = color
        x += len(g[1]) + 1


# ---------------------------------------------------------------- stand-in items

def flower_item(petal, heart=(255, 216, 74)):
    """A 16x16 stand-in for a flower item."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(8, 15):
        px[8, y] = (60, 140, 50, 255)
    for (dx, dy) in ((-1, 11), (1, 10), (-2, 12), (2, 12)):
        px[8 + dx, dy] = (80, 170, 60, 255)
    for (dx, dy) in ((0, -2), (2, -1), (2, 1), (0, 2), (-2, 1), (-2, -1)):
        for ax in (0, 1):
            px[8 + dx + ax - 1, 5 + dy] = petal + (255,)
    for (dx, dy) in ((0, 0), (-1, 0), (0, -1), (-1, -1)):
        px[8 + dx, 5 + dy] = heart + (255,)
    return im


def block_item(color):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(2, 14):
        for x in range(2, 14):
            k = 0.85 + 0.3 * rnd2(x, y, 5)
            px[x, y] = tuple(min(255, int(c * k)) for c in color) + (255,)
    return im


# ---------------------------------------------------------------- the whole screen

def preview_screen(lang="en", scale=3, width=640, height=360, name=None):
    import gen_gui as G
    import gen_widgets as W
    import gen_mascot as K

    tex = os.path.join(ASSETS, "textures", "gui")
    panel = Image.open(os.path.join(tex, "greenhouse.png")).convert("RGBA")
    widgets = Image.open(os.path.join(tex, "greenhouse_widgets.png")).convert("RGBA")
    mascot = Image.open(os.path.join(tex, "mascot.png")).convert("RGBA")
    glyphs = plate_glyphs()
    title = "Mana Greenhouse" if lang == "en" else "Манова оранжерея"

    scr = Image.new("RGBA", (width, height), (28, 36, 30, 255))
    for y in range(height):
        for x in range(width):
            if rnd2(x // 16, y // 16, 3) < 0.5:
                scr.putpixel((x, y), (24, 32, 27, 255))

    # layout, as GreenhouseScreen.init(): the panel in the middle, the keeper beside it
    M = G.M
    px_mascot_x, px_mascot_w = -104, 88
    left = px_mascot_w - (px_mascot_x + px_mascot_w)
    scroll_up = 12
    leftPos = max(left + 2, (width - G.W) // 2)
    topPos = max(scroll_up + 2, (height - G.H - scroll_up) // 2 + scroll_up)

    scr.alpha_composite(panel, (leftPos - M, topPos - M))

    def sheet(x, y, w, h):
        return widgets.crop((x, y, x + w, y + h))

    # the gold lights' glow
    glow = sheet(*W.GLOW_UV, W.GLOW_SIZE, W.GLOW_SIZE)
    for (lx, ly) in G.LIGHTS:
        g2 = glow.copy()
        g2.putalpha(g2.getchannel("A").point(lambda a: int(a * 0.7)))
        tint = Image.new("RGBA", g2.size, (255, 220, 107, 255))
        tint.putalpha(g2.getchannel("A"))
        scr.alpha_composite(tint, (leftPos + lx - 4, topPos + ly - 4))

    # the title scroll
    text_w = plate_width(glyphs, title)
    paper = text_w + 12
    mid = leftPos + G.W // 2
    x0 = mid - paper // 2
    x1 = x0 + paper
    top = topPos - scroll_up
    k = 0
    x = x0
    while x < x1:
        w = min(8, x1 - x)
        scr.alpha_composite(sheet(W.SCROLL_TILE_U + (k & 1) * 8, W.SCROLL_V, w, 20), (x, top))
        x += 8
        k += 1
    scr.alpha_composite(sheet(0, W.SCROLL_V, 16, 20), (x0 - 16, top))
    scr.alpha_composite(sheet(16, W.SCROLL_V, 16, 20), (x1, top))
    plate_draw(scr, glyphs, title, mid - text_w // 2 + 1, top + 9, (255, 249, 224, 255))
    plate_draw(scr, glyphs, title, mid - text_w // 2, top + 8, (78, 52, 23, 255))

    # the close button
    scr.alpha_composite(sheet(W.CLOSE_UV[0], W.CLOSE_UV[1], 16, 16), (leftPos + 245, topPos - 8))

    # the keeper
    mx, my = leftPos + px_mascot_x, topPos + 4
    scr.alpha_composite(K.compose(mascot, 0, 0, 0), (mx, my))

    # the heart's mana
    cx, cy = leftPos + G.HEART[0], topPos + G.HEART[1]
    r = G.ORB_R - 2
    level = 0.62
    surface = cy + r - level * 2 * r
    for xx in range(-r, r):
        half = math.sqrt(max(0.0, r * r - (xx + 0.5) ** 2))
        top_y, bot_y = int(math.ceil(cy - half)), int(math.floor(cy + half))
        wave = math.sin(xx * 0.45) * 0.9 + math.sin(xx * 0.9) * 0.5
        s = int(round(surface + wave))
        for yy in range(max(top_y, s), bot_y):
            depth = (yy - s) / max(1, bot_y - s)
            col = mix(st.M2, st.M4, min(1.0, depth * 1.6 + 0.15))
            if yy == max(top_y, s):
                col = st.M1
            scr.putpixel((cx + xx, yy), col + (255,))
    scr.alpha_composite(sheet(*W.SHINE_UV, W.SHINE_SIZE, W.SHINE_SIZE), (cx - G.ORB_R, cy - G.ORB_R))

    # growth bar and charge gauge
    bx1, by1, bx2, by2 = G.BAR
    w = int((bx2 - bx1) * 0.42)
    scr.alpha_composite(sheet(0, W.FILL_V, w, 6), (leftPos + bx1, topPos + by1))
    gx1, gy1, gx2, gy2 = G.GAUGE
    h = int((gy2 - gy1) * 0.4)
    for yy in range(gy2 - h, gy2):
        col = mix(st.M2, st.M4, min(1.0, (yy - (gy2 - h)) / float(gy2 - gy1) * 1.4))
        for xx in range(gx1, gx2):
            scr.putpixel((leftPos + xx, topPos + yy), col + (255,))

    # buttons and their icons
    for (bx, by), icon in zip(G.BUTTONS, (0, 3)):
        scr.alpha_composite(sheet(0, 0, 16, 16), (leftPos + bx, topPos + by))
        scr.alpha_composite(sheet(icon * 12, W.ICON_V, 12, 12), (leftPos + bx + 2, topPos + by + 2))

    # stand-in items
    petals = [(230, 60, 60), (250, 200, 40), (60, 140, 230), (220, 80, 200), (250, 130, 30), (140, 230, 60), (90, 220, 220), (240, 120, 120)]
    for (fx, fy), pc in zip(G.FLOWERS, petals):
        scr.alpha_composite(flower_item(pc), (leftPos + fx, topPos + fy))
    for i, (ux, uy) in enumerate(G.UPGRADES):
        scr.alpha_composite(block_item([(90, 200, 90), (90, 170, 230), (240, 200, 70), (240, 130, 190)][i]), (leftPos + ux, topPos + uy))
    scr.alpha_composite(block_item((220, 220, 230)), (leftPos + G.CHARGE[0], topPos + G.CHARGE[1]))
    for c, col in enumerate([(120, 200, 120), (90, 190, 240), (60, 60, 70), (250, 200, 60), (240, 130, 190), (200, 200, 210)]):
        scr.alpha_composite(block_item(col), (leftPos + G.INV_SLOT_X + c * 18, topPos + G.INV_Y))

    out = upscale(scr, scale)
    os.makedirs(OUT_DIR, exist_ok=True)
    out.save(os.path.join(OUT_DIR, name or ("screen_%s.png" % lang)))
    return out


if __name__ == "__main__":
    preview_screen("uk" if "uk" in sys.argv else "en")
