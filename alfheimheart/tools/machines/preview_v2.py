"""A still of a second-generation machine GUI roughly as the game draws it, to check the art without the game:
the panel, the title on its plaque, the buttons, the status gem, items in the slots (Botania's own item
textures, if its sources are at $BOTANIA_ASSETS), and a machine's moving parts caught in the middle of a craft.

    python3 tools/machines/preview_v2.py rune_altar out.png [scale]

Only for looking at: the game's screens (machine/*/client) are what really draws them.
"""
import math
import os
import re
import sys

from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_v2  # noqa: E402
import theme as T  # noqa: E402
import layouts as L  # noqa: E402
from pix import ASSETS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
BOTANIA = os.environ.get("BOTANIA_ASSETS", "/home/user/vazkiimods/botania450/Xplat/src/main/resources/assets/botania/textures")
FONT_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reya", "alfheimheart", "portal", "client", "PlateFont.java")


def plate_font():
    glyphs = {}
    src = open(FONT_JAVA, encoding="utf-8").read()
    for chars, rows in re.findall(r'put\("([^"]+)",\s*((?:"[^"]*",?\s*)+)\);', src):
        rows = re.findall(r'"([^"]*)"', rows)
        if len(rows) == 5:
            rows = ["." * len(rows[0])] + rows + ["." * len(rows[0])]
        for c in chars:
            glyphs[c] = rows
    return glyphs


def draw_text(img, text, x, y, colour, font):
    px = img.load()
    for ch in text:
        g = font.get(ch.upper()) or font.get(" ") or ["...."] * 7
        for j, row in enumerate(g):
            for i, c in enumerate(row):
                if c == "X" and 0 <= x + i < img.width and 0 <= y + j - 1 < img.height:
                    px[x + i, y + j - 1] = colour
        x += len(g[0]) + 1


def text_width(text, font):
    return sum(len((font.get(ch.upper()) or ["...."])[0]) + 1 for ch in text) - 1


def item(name):
    for sub in ("item", "block"):
        p = os.path.join(BOTANIA, sub, name + ".png")
        if os.path.exists(p):
            im = Image.open(p).convert("RGBA")
            return im.crop((0, 0, 16, 16)) if im.height > 16 else im
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(3, 13):
            im.putpixel((x, y), (190, 190, 200, 255))
    return im


def blit(dst, src, x, y, box=None, tint=None, alpha=1.0, add=False):
    if box is not None:
        src = src.crop(box)
    src = src.convert("RGBA")
    if tint is not None or alpha != 1.0:
        r, g, b = tint or (255, 255, 255)
        px = src.load()
        for j in range(src.height):
            for i in range(src.width):
                p = px[i, j]
                px[i, j] = (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, int(p[3] * alpha))
    if add:
        d = dst.load()
        s = src.load()
        for j in range(src.height):
            for i in range(src.width):
                p = s[i, j]
                if p[3] == 0 or not (0 <= x + i < dst.width and 0 <= y + j < dst.height):
                    continue
                q = d[x + i, y + j]
                a = p[3] / 255.0
                d[x + i, y + j] = tuple(min(255, int(q[k] + p[k] * a)) for k in range(3)) + (q[3],)
        return
    dst.alpha_composite(src, (x, y))


def add_disc(img, cx, cy, r, colour, strength):
    px = img.load()
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d < r and 0 <= x < img.width and 0 <= y < img.height:
                a = strength * (1 - d / r) ** 1.5
                q = px[x, y]
                px[x, y] = tuple(min(255, int(q[k] + colour[k] * a)) for k in range(3)) + (q[3],)


def frame_common(key, title, items, extra):
    lay = dict(L.LAYOUTS)[key.upper()]
    panel, sheet, _ = gen_v2.build(key)
    w, h, mh = lay["size"]
    left, top = 40, 40
    img = Image.new("RGBA", (w + 80, h + 72), (58, 64, 70, 255))
    blit(img, panel.img, left - L.M, top - L.M)
    sh = sheet.img
    # the plaque and its title
    font = plate_font()
    tw = text_width(title, font)
    paper = tw + 12
    x0 = left + w // 2 - paper // 2
    ptop = top - 12
    x = x0
    k = 0
    while x < x0 + paper:
        blit(img, sh, x, ptop, (T.PLAQUE_TILE_U + (k & 1) * 8, T.PLAQUE_V, T.PLAQUE_TILE_U + (k & 1) * 8 + min(8, x0 + paper - x), T.PLAQUE_V + 20))
        x += 8
        k += 1
    blit(img, sh, x0 - 16, ptop, (0, T.PLAQUE_V, 16, T.PLAQUE_V + 20))
    blit(img, sh, x0 + paper, ptop, (16, T.PLAQUE_V, 32, T.PLAQUE_V + 20))
    th = gen_v2.MACHINES[key].THEME
    draw_text(img, title, left + w // 2 - tw // 2 + 1, ptop + 9, (0, 0, 0, 255), font)
    draw_text(img, title, left + w // 2 - tw // 2, ptop + 8, th.plaque_text_hint + (255,), font)
    # the buttons
    cx, cy = lay["close"]
    blit(img, sh, left + cx, top + cy, (T.CLOSE_UV[0], 0, T.CLOSE_UV[0] + 16, 16))
    rx, ry = lay["redstone"]
    blit(img, sh, left + rx, top + ry, (0, 0, 16, 16))
    blit(img, sh, left + rx + 2, top + ry + 2, (0, 16, 12, 28))
    # the gem: working
    gx, gy = lay["gem"]
    blit(img, sh, left + gx - 3, top + gy - 3, (T.GEM_UV[0], T.GEM_UV[1], T.GEM_UV[0] + 7, T.GEM_UV[1] + 7))
    extra(img, sh, left, top)
    # items
    slots = lay["inputs"] + lay["outputs"] + lay["special"]
    for (sx, sy), name in zip(slots, items):
        if name:
            blit(img, item(name).resize((16, 16), Image.NEAREST), left + sx, top + sy)
    inv = ["mana_pearl", "manasteel_ingot", "mana_diamond", "rune_water", "livingrock", "pixie_dust", None, "terrasteel_ingot", "lexicon"]
    ix, iy, hy, _, _ = lay["inventory"]
    for c, name in enumerate(inv):
        if name:
            blit(img, item(name), left + ix + c * 18, top + hy)
    return img


def altar_extra(img, sh, left, top):
    a = L.ALTAR
    p = 0.62
    # the column: 70 % full
    x1, y1, x2, y2 = a.GAUGE
    hgt = y2 - y1
    fill = int(hgt * 0.7)
    blit(img, sh, left + x1, top + y2 - fill, (a.FILL_UV[0], a.FILL_UV[1] + hgt - fill, a.FILL_UV[0] + x2 - x1, a.FILL_UV[1] + hgt))
    px, py = a.POOL
    blit(img, sh, left + px, top + py, (L.POOL_UV[0] + 16, L.POOL_UV[1], L.POOL_UV[0] + 32, L.POOL_UV[1] + 16))
    # the sockets in use glowing, their runes lit, the light running in along the channels
    for k in range(5):
        col = ((a.RUNE_COLOURS[k] >> 16) & 255, (a.RUNE_COLOURS[k] >> 8) & 255, a.RUNE_COLOURS[k] & 255)
        sx, sy = a.INPUTS[k]
        s = a.HALO_SOCKET_SIZE
        blit(img, sh, left + sx + 8 - s // 2, top + sy + 8 - s // 2, (a.HALO_SOCKET_UV[0], a.HALO_SOCKET_UV[1], a.HALO_SOCKET_UV[0] + s, a.HALO_SOCKET_UV[1] + s),
             tint=col, alpha=0.8, add=True)
        x1s, y1s, x2s, y2s = L.altar_spokes()[k]
        for t in range(0, 11):
            q = t / 10.0
            add_disc(img, left + x1s + (x2s - x1s) * q, top + y1s + (y2s - y1s) * q, 1.6, col, 0.9 if t % 3 == 0 else 0.4)
    # the hollow's light and the forming rune
    add_disc(img, left + a.CX, top + a.CY, 14, (90, 200, 255), 0.9)
    rune = item("rune_fire").resize((12, 12), Image.NEAREST)
    blit(img, rune, left + a.CX - 6, top + a.CY - 6)
    # a progress ring round the altar
    for i in range(int(360 * p)):
        ang = math.radians(i - 90)
        r = a.INNER_R
        x, y = left + a.CX + r * math.cos(ang), top + a.CY + r * math.sin(ang)
        add_disc(img, x, y, 1.4, (166, 246, 255), 0.6)


def plate_extra(img, sh, left, top):
    pl = L.PLATE
    p = 0.55
    cx, cy = left + pl.CX, top + pl.CY
    # the arc gauge, 60 % full, blue to cyan
    a0, a1 = math.radians(pl.ARC_FROM), math.radians(pl.ARC_TO)
    fill = 0.6
    for y in range(cy - pl.ARC_R2 - 1, cy + pl.ARC_R2 + 2):
        for x in range(cx - pl.ARC_R2 - 1, cx + pl.ARC_R2 + 2):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if not (pl.ARC_R1 + 0.6 < d < pl.ARC_R2 - 0.4):
                continue
            ang = math.atan2(y + 0.5 - cy, x + 0.5 - cx) % (2 * math.pi)
            if a0 <= ang <= a1:
                t = (ang - a0) / (a1 - a0)
                if t <= fill:
                    col = (int(40 + 120 * t), int(120 + 120 * t), 255)
                    img.putpixel((x, y), col + (255,))
    # beams from three sockets to the core, the core's orb between blue and green
    for i in (0, 2, 4):
        sx, sy = pl.INPUTS[i]
        x1, y1 = left + sx + 8, top + sy + 8
        for t in range(0, 21):
            q = t / 20.0
            add_disc(img, x1 + (cx - x1) * q, y1 + (cy - y1) * q, 1.4, (80, 200, 255), 0.5 if t % 4 else 0.9)
    add_disc(img, cx, cy, 14, (90, 230, 150), 1.0)
    add_disc(img, cx, cy, 6, (220, 255, 230), 1.0)
    # the beam out to the tower
    x1, y1, x2, y2 = pl.BEAM
    for x in range(x1, x2):
        add_disc(img, left + x, top + y1, 1.2, (110, 255, 150), 0.5)
    px, py = pl.POOL
    blit(img, sh, left + px, top + py, (L.POOL_UV[0] + 16, L.POOL_UV[1], L.POOL_UV[0] + 32, L.POOL_UV[1] + 16))


def infuser_extra(img, sh, left, top):
    f = L.INFUSER
    mana = 0.7
    wy = f.CY + f.WATER_EMPTY + (f.WATER_FULL - f.WATER_EMPTY) * mana
    px = img.load()

    def half(y, mid):
        fy = (y + 0.5 - mid) / f.OPEN_RY
        return -1 if abs(fy) >= 1 else f.OPEN_RX * math.sqrt(1 - fy * fy)
    for y in range(int(wy - f.OPEN_RY), int(wy + f.OPEN_RY) + 1):
        h = min(half(y, wy), half(y, f.CY))
        if h <= 0:
            continue
        fy = (y + 0.5 - wy) / f.OPEN_RY
        for x in range(int(math.ceil(f.CX - h - 0.5)), int(math.floor(f.CX + h - 0.5)) + 1):
            wave = math.sin((x - f.CX) * 0.42 + y * 0.9) + 0.6 * math.sin((x - f.CX) * -0.19 + y * 1.4)
            k = (wave + 1.6) / 3.2
            t = 0.55 + 0.45 * (fy + 1) / 2
            c = (int(14 + (42 - 14) * t), int(58 + (159 - 58) * t), int(122 + (226 - 122) * t))
            if k > 0.6:
                c = tuple(min(255, int(v + (w - v) * (k - 0.6) * 1.5)) for v, w in zip(c, (85, 217, 247)))
            if half(y - 1, wy) < 0:
                c = (200, 250, 255)
            px[left + x, top + y] = c + (255,)
    add_disc(img, left + f.CX, top + f.CY - 21, 14, (120, 220, 255), 0.6)
    blit(img, item("mana_pearl"), left + f.CX - 8, top + f.CY - 29)
    s_ = f.CATALYST_RING_SIZE
    sx, sy = f.CATALYST
    blit(img, sh, left + sx + 8 - s_ // 2, top + sy + 8 - s_ // 2, (f.CATALYST_RING_UV[0], f.CATALYST_RING_UV[1], f.CATALYST_RING_UV[0] + s_, f.CATALYST_RING_UV[1] + s_),
         tint=(255, 150, 255), alpha=0.7, add=True)
    blit(img, sh, left + f.POOL[0], top + f.POOL[1], (L.POOL_UV[0] + 16, L.POOL_UV[1], L.POOL_UV[0] + 32, L.POOL_UV[1] + 16))


PREVIEWS = {
    "rune_altar": ("RUNIC ALTAR", ["manasteel_ingot", "mana_powder", "mana_pearl", "rune_mana", "mana_diamond", None, None, None,
                                   "rune_fire", "rune_water", "rune_earth", None, None, None, "livingrock"], altar_extra),
    "mana_infuser": ("MANA INFUSER", ["ender_pearl", "manasteel_ingot", "mana_diamond", None, None, None,
                                      "mana_pearl", "manasteel_ingot", None, None, None, None, "alchemy_catalyst"], infuser_extra),
    "terra_plate": ("TERRESTRIAL PLATE", ["manasteel_ingot", None, "mana_pearl", None, "mana_diamond", None,
                                          "terrasteel_ingot", None, None], plate_extra),
}


def main():
    key = sys.argv[1] if len(sys.argv) > 1 else "rune_altar"
    out = sys.argv[2] if len(sys.argv) > 2 else key + "_preview.png"
    scale = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    title, items, extra = PREVIEWS[key]
    img = frame_common(key, title, items, extra)
    img.resize((img.width * scale, img.height * scale), Image.NEAREST).save(out)
    print("wrote " + out)


if __name__ == "__main__":
    main()
