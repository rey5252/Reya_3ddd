"""A preview of the whole GUI, to look at the pixel art before a game run.

    python3 tools/preview.py [uk]      writes build/preview/screen_en.png (or screen_uk.png): the screen at GUI
                                       scale 3, put together the way client/PortalScreen.java does it (the
                                       portal open and trading, with stand-in items where the game shows real ones)
"""
import os
import re
import sys

from PIL import Image

from pix import ROOT, ASSETS, upscale, rnd2

OUT_DIR = os.path.join(ROOT, "build", "preview")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reya", "alfheimheart", "portal", "client")


def plate_glyphs():
    """The plate font's glyphs, read from client/PlateFont.java."""
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


def block_item(color):
    """A 16x16 stand-in for an item."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(3, 13):
        for x in range(3, 13):
            k = 0.85 + 0.3 * rnd2(x, y, 5)
            px[x, y] = tuple(min(255, int(c * k)) for c in color) + (255,)
    return im


def preview_screen(lang="en", scale=3, width=640, height=360):
    import gen_gui as G
    import gen_widgets as W

    tex = os.path.join(ASSETS, "textures", "gui")
    panel = Image.open(os.path.join(tex, "elven_portal.png")).convert("RGBA")
    widgets = Image.open(os.path.join(tex, "elven_portal_widgets.png")).convert("RGBA")
    swirl = Image.open(os.path.join(tex, "portal_swirl.png")).convert("RGBA")
    glyphs = plate_glyphs()
    title = {"en": "Elven Portal", "uk": "Ельфійський портал"}[lang]

    scr = Image.new("RGBA", (width, height), (28, 36, 30, 255))
    for y in range(height):
        for x in range(width):
            if rnd2(x // 16, y // 16, 3) < 0.5:
                scr.putpixel((x, y), (24, 32, 27, 255))
    # the layout, as PortalScreen.init()
    scroll_up = 12
    left = (width - G.W) // 2
    top = max(scroll_up + 2, (height - G.H - scroll_up) // 2 + scroll_up)
    scr.alpha_composite(panel, (left - G.M, top - G.M))

    def sheet(x, y, w, h):
        return widgets.crop((x, y, x + w, y + h))

    # the gold lights' glow
    glow = sheet(*W.GLOW_UV, W.GLOW_SIZE, W.GLOW_SIZE)
    for (lx, ly) in G.LIGHTS:
        tint = Image.new("RGBA", glow.size, (255, 220, 107, 255))
        tint.putalpha(glow.getchannel("A").point(lambda a: int(a * 0.7)))
        scr.alpha_composite(tint, (left + lx - 4, top + ly - 4))
    # the title scroll
    text_w = plate_width(glyphs, title)
    paper = text_w + 12
    mid = left + G.W // 2
    x0 = mid - paper // 2
    x1 = x0 + paper
    y0 = top - scroll_up
    k = 0
    x = x0
    while x < x1:
        scr.alpha_composite(sheet(W.SCROLL_TILE_U + (k & 1) * 8, W.SCROLL_V, min(8, x1 - x), 20), (x, y0))
        x += 8
        k += 1
    scr.alpha_composite(sheet(0, W.SCROLL_V, 16, 20), (x0 - 16, y0))
    scr.alpha_composite(sheet(16, W.SCROLL_V, 16, 20), (x1, y0))
    plate_draw(scr, glyphs, title, mid - text_w // 2 + 1, y0 + 9, (255, 249, 224, 255))
    plate_draw(scr, glyphs, title, mid - text_w // 2, y0 + 8, (78, 52, 23, 255))
    # the close button
    scr.alpha_composite(sheet(W.CLOSE_UV[0], W.CLOSE_UV[1], 16, 16), (left + 229, top - 8))
    # the swirl, the gem (trading), a few lit runes, the crystals afloat, the lit arrow, the mana, the button, the pool
    sx, sy, sw, sh = G.SWIRL
    frame = swirl.crop((3 * sw, 0, 4 * sw, sh))
    scr.alpha_composite(frame, (left + sx, top + sy))
    scr.alpha_composite(sheet(W.GEM_UV[0], W.GEM_UV[1], 7, 7), (left + G.GEM[0] - 3, top + G.GEM[1] - 3))
    for k in (0, 1, 2):
        rune = sheet(W.RUNE_UV[0] + k * W.RUNE_SIZE, W.RUNE_UV[1], W.RUNE_SIZE, W.RUNE_SIZE)
        rune.putalpha(rune.getchannel("A").point(lambda a: int(a * (1.0 - 0.3 * k))))
        scr.alpha_composite(rune, (left + G.RUNES[k][0], top + G.RUNES[k][1]))
    for (cx, cy) in G.CRYSTALS:
        scr.alpha_composite(sheet(W.CRYSTAL_UV[0] + W.CRYSTAL_W, W.CRYSTAL_UV[1], W.CRYSTAL_W, W.CRYSTAL_H), (left + cx, top + cy - 3))
    arrow = sheet(W.ARROW_UV[0] + W.ARROW_W, W.ARROW_UV[1], W.ARROW_W, W.ARROW_H)
    scr.alpha_composite(arrow, (left + G.ARROWS[0][0], top + G.ARROWS[0][1]))
    bx1, by1, bx2, by2 = G.BAR
    scr.alpha_composite(sheet(0, W.FILL_V, int((bx2 - bx1) * 0.64), 6), (left + bx1, top + by1))
    bx, by = G.BUTTONS[0]
    scr.alpha_composite(sheet(0, 0, 16, 16), (left + bx, top + by))
    scr.alpha_composite(sheet(0, W.ICON_V, 12, 12), (left + bx + 2, top + by + 2))
    scr.alpha_composite(sheet(W.POOL_UV[0] + 16, W.POOL_UV[1], 16, 16), (left + G.POOL[0], top + G.POOL[1]))
    # stand-in items: things for the elves on the left, their trades on the right, the inventory
    ins = [(170, 120, 80), (150, 200, 230), (200, 190, 180), (240, 200, 70), (90, 220, 230), (120, 220, 200), (240, 240, 230), (60, 90, 200)]
    outs = [(220, 230, 210), (240, 130, 190), (200, 140, 110), (250, 120, 200), (250, 190, 240), (240, 190, 60)]
    for i, col in enumerate(ins):
        scr.alpha_composite(block_item(col), (left + G.INPUT[0] + i % 3 * 18, top + G.INPUT[1] + i // 3 * 18))
    for i, col in enumerate(outs):
        scr.alpha_composite(block_item(col), (left + G.OUTPUT[0] + i % 3 * 18, top + G.OUTPUT[1] + i // 3 * 18))
    for c in range(9):
        scr.alpha_composite(block_item([(120, 200, 120), (170, 120, 80), (150, 200, 230), (200, 190, 180)][c % 4]),
                            (left + G.INV_SLOT_X + c * 18, top + G.HOTBAR_Y))
    out = upscale(scr, scale)
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "screen_%s.png" % lang)
    out.save(path)
    return path


if __name__ == "__main__":
    print(preview_screen("uk" if "uk" in sys.argv else "en"))
