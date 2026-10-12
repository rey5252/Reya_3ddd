#!/usr/bin/env python3
"""Fortress Protocol — cameo composer, step 2 of 2 (Pillow only).

Takes the transparent renders + manifest.json produced by render_cameos.js and
builds RA3-style build-menu cameos: faction-tinted background, silhouette back
glow, glossy sheen, vignette and a beveled faction frame.

Outputs (next to this script):  <Id>.png (256x256), <Id>_128.png (128x128),
_sheet.png (contact sheet: every cameo at 256 and 128 px).

Usage (from the repo root):
    python3 -I ra3/models/cameos/compose_cameos.py [--renders=DIR]

    --renders=DIR / env FP_CAMEO_RENDER_DIR   raw renders from render_cameos.js
        (default: <tempdir>/fortress_cameo_renders, same as render_cameos.js).
"""
import json
import math
import os
import sys
import tempfile

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
SS = 512          # supersampled working size
FRAME = 30        # frame width at SS


def arg(name, default=None):
    for a in sys.argv[1:]:
        if a.startswith(f"--{name}="):
            return a.split("=", 1)[1]
    return default


RENDERS = arg("renders") or os.environ.get("FP_CAMEO_RENDER_DIR") or os.path.join(
    tempfile.gettempdir(), "fortress_cameo_renders")

# SPEC palettes. bg: (centre, edge) of the radial background; glow: silhouette
# back light; frame: list of bevel bands from outside in:
# (base colour, highlight colour, shadow colour, width).
FACTIONS = {
    "allied": dict(
        bg=((58, 112, 196), (8, 22, 52)), floor=(150, 200, 255), glow=(190, 230, 255),
        frame=[((47, 111, 214), (238, 243, 251), (16, 40, 92), 14),
               ((159, 179, 200), (255, 255, 255), (70, 88, 110), 8),
               ((22, 46, 96), (60, 100, 170), (8, 16, 36), 4)],
        inner_line=(238, 243, 251), corner="ticks", accent=(127, 227, 255)),
    "soviet": dict(
        bg=((150, 34, 24), (28, 6, 4)), floor=(255, 150, 110), glow=(255, 205, 170),
        frame=[((70, 72, 62), (150, 150, 135), (22, 22, 18), 12),
               ((198, 43, 31), (255, 120, 95), (90, 14, 8), 10),
               ((40, 38, 32), (91, 95, 74), (12, 12, 10), 4)],
        inner_line=(240, 160, 120), corner="rivets", accent=(106, 208, 255)),
    "empire": dict(
        bg=((150, 22, 40), (26, 8, 14)), floor=(255, 170, 180), glow=(255, 230, 235),
        frame=[((244, 241, 236), (255, 255, 255), (150, 146, 140), 13),
               ((200, 16, 46), (255, 90, 110), (100, 6, 20), 9),
               ((29, 29, 34), (70, 70, 80), (8, 8, 10), 5)],
        inner_line=(63, 224, 197), corner="notch", accent=(63, 224, 197)),
    "neutral": dict(
        bg=((96, 102, 112), (16, 18, 22)), floor=(255, 220, 150), glow=(255, 236, 190),
        frame=[((178, 138, 52), (255, 226, 140), (80, 56, 14), 13),
               ((92, 98, 108), (190, 198, 210), (30, 32, 38), 10),
               ((26, 28, 32), (70, 74, 82), (6, 6, 8), 4)],
        inner_line=(255, 220, 140), corner="rivets", accent=(255, 200, 90)),
}


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def radial(size, center, c0, c1, radius, power=1.0):
    """RGB radial gradient via a resized 1-D ramp on a distance map."""
    w, h = size
    cx, cy = center
    # distance map built from a small grid then upscaled (cheap, smooth)
    n = 128
    dm = Image.new("L", (n, n))
    px = dm.load()
    for y in range(n):
        for x in range(n):
            dx = (x + 0.5) / n * w - cx
            dy = (y + 0.5) / n * h - cy
            px[x, y] = min(255, int(255 * (math.hypot(dx, dy) / radius) ** power))
    dm = dm.resize((w, h), Image.BICUBIC)
    a = Image.new("RGB", size, c0)
    b = Image.new("RGB", size, c1)
    return Image.composite(b, a, dm)


def background(f, s):
    bg = radial((s, s), (s * 0.5, s * 0.42), f["bg"][0], f["bg"][1], s * 0.78, 1.15)
    # soft floor glow where the structure stands
    fl = Image.new("L", (s, s), 0)
    ImageDraw.Draw(fl).ellipse([s * 0.1, s * 0.66, s * 0.9, s * 1.02], fill=90)
    fl = fl.filter(ImageFilter.GaussianBlur(s * 0.07))
    bg = Image.composite(Image.new("RGB", (s, s), f["floor"]), bg, fl)
    # faint diagonal hatching for texture (RA3 cameo backdrops are not flat)
    hatch = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(hatch)
    for k in range(-s, 2 * s, 10):
        d.line([(k, 0), (k - s, s)], fill=14, width=3)
    bg = Image.composite(Image.new("RGB", (s, s), (255, 255, 255)), bg, hatch)
    return bg


# Per-id extra zoom for slim towers whose foundation plate would otherwise
# shrink the silhouette; the base then bleeds under the bottom frame.
ZOOM = {
    "AlliedAegisSpire": 1.28,
    "AlliedPrismSpire": 1.12,
    "JapanTenguSpire": 1.12,
    "SovietTeslaObelisk": 1.10,
    "JapanWaveCitadel": 1.06,
}


def place_model(render, bbox, s, inner, zoom=1.0):
    """Crop to the model and scale it so it fills the frame (base may bleed)."""
    x0, y0, x1, y1 = bbox
    pad = 6
    crop = render.crop((max(0, x0 - pad), max(0, y0 - pad), x1 + pad, y1 + 40))  # +shadow below
    bw, bh = x1 - x0 + 2 * pad, y1 - y0 + 2 * pad
    avail = s - 2 * inner
    k = min(avail * 0.97 / bh, avail * 1.10 / bw) * zoom
    m = crop.resize((max(1, int(crop.width * k)), max(1, int(crop.height * k))), Image.LANCZOS)
    layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    mw, mh = int(bw * k), int(bh * k)
    ox = (s - mw) // 2
    # vertical: centre in the available area, but nudge down a bit (heroic, grounded)
    oy = inner + (avail - mh) // 2 + int(avail * 0.03)
    if mh > avail * 0.94:
        oy = inner + int(avail * 0.03)
    layer.paste(m, (ox, oy), m)
    return layer


def glow_behind(layer, color, s):
    a = layer.getchannel("A").point(lambda v: 255 if v > 40 else 0)
    a = a.filter(ImageFilter.MaxFilter(11)).filter(ImageFilter.GaussianBlur(s * 0.02))
    a = a.point(lambda v: int(v * 0.68))
    g = Image.new("RGBA", (s, s), color + (0,))
    g.putalpha(a)
    return g


def dark_outline(layer, s):
    a = layer.getchannel("A").point(lambda v: 255 if v > 60 else 0)
    a = a.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(1.2))
    o = Image.new("RGBA", (s, s), (6, 8, 12, 0))
    o.putalpha(a.point(lambda v: int(v * 0.85)))
    return o


def vignette(img, s):
    m = radial((s, s), (s * 0.5, s * 0.46), (0, 0, 0), (255, 255, 255), s * 0.72, 2.4)
    m = m.convert("L").point(lambda v: int(v * 0.62))
    black = Image.new("RGB", (s, s), (0, 0, 0))
    return Image.composite(black, img, m)


def sheen(img, s, inner):
    """Glassy highlight over the upper part of the portrait."""
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    d.polygon([(inner, inner), (s - inner, inner), (s - inner, s * 0.24), (inner, s * 0.42)], fill=34)
    m = m.filter(ImageFilter.GaussianBlur(s * 0.03))
    return Image.composite(Image.new("RGB", (s, s), (255, 255, 255)), img, m)


def bevel_band(d, o, w, s, base, hi, lo):
    """One beveled square ring: outer offset o, width w. Top/left lit, bottom/right shaded."""
    a, b = o, s - 1 - o
    c, e = o + w, s - 1 - o - w
    top = lerp(base, hi, 0.55)
    left = lerp(base, hi, 0.25)
    bottom = lerp(base, lo, 0.6)
    right = lerp(base, lo, 0.35)
    d.polygon([(a, a), (b, a), (e, c), (c, c)], fill=top)
    d.polygon([(a, a), (c, c), (c, e), (a, b)], fill=left)
    d.polygon([(a, b), (c, e), (e, e), (b, b)], fill=bottom)
    d.polygon([(b, a), (b, b), (e, e), (e, c)], fill=right)
    # crisp highlight on the outer top/left edge, shadow on the inner edge
    d.line([(a, a), (b, a)], fill=hi, width=2)
    d.line([(a, a), (a, b)], fill=lerp(hi, base, 0.3), width=2)
    d.line([(c, e), (e, e), (e, c)], fill=lerp(hi, base, 0.5), width=1)
    d.line([(c, e), (c, c), (e, c)], fill=lo, width=1)


def frame(img, f, s):
    d = ImageDraw.Draw(img)
    o = 0
    for base, hi, lo, w in f["frame"]:
        bevel_band(d, o, w, s, base, hi, lo)
        o += w
    inner = o
    # inner light rim (thin, bright line just inside the frame, top brighter)
    d.line([(inner, inner), (s - 1 - inner, inner)], fill=f["inner_line"], width=2)
    d.line([(inner, inner), (inner, s - 1 - inner)], fill=lerp(f["inner_line"], (0, 0, 0), 0.35), width=2)
    d.line([(s - 1 - inner, inner), (s - 1 - inner, s - 1 - inner)], fill=lerp(f["inner_line"], (0, 0, 0), 0.55), width=2)
    d.line([(inner, s - 1 - inner), (s - 1 - inner, s - 1 - inner)], fill=lerp(f["inner_line"], (0, 0, 0), 0.6), width=2)
    w0 = f["frame"][0][3]
    if f["corner"] == "rivets":
        for cx in (w0 // 2 + 1, s - 2 - w0 // 2):
            for cy in (w0 // 2 + 1, s - 2 - w0 // 2):
                r = 4
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(30, 30, 28))
                d.ellipse([cx - r + 1, cy - r + 1, cx + r - 2, cy + r - 2], fill=(190, 190, 175))
    elif f["corner"] == "ticks":
        L = 34
        for sx, sy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            x = 3 if sx == 0 else s - 4
            y = 3 if sy == 0 else s - 4
            dx = 1 if sx == 0 else -1
            dy = 1 if sy == 0 else -1
            d.line([(x, y), (x + dx * L, y)], fill=(255, 255, 255), width=3)
            d.line([(x, y), (x, y + dy * L)], fill=(255, 255, 255), width=3)
    elif f["corner"] == "notch":
        L = 22
        col = f["frame"][1][0]
        for x, y, dx, dy in ((0, 0, 1, 1), (s - 1, 0, -1, 1), (0, s - 1, 1, -1), (s - 1, s - 1, -1, -1)):
            d.polygon([(x, y), (x + dx * L, y), (x, y + dy * L)], fill=col)
            d.line([(x + dx * L, y), (x, y + dy * L)], fill=(29, 29, 34), width=2)
    return inner


def compose(entry):
    f = FACTIONS[entry["faction"]]
    s = SS
    render = Image.open(os.path.join(RENDERS, entry["file"])).convert("RGBA")
    inner = sum(b[3] for b in f["frame"])
    img = background(f, s)
    layer = place_model(render, entry["bbox"], s, inner, ZOOM.get(entry["id"], 1.0))
    rgba = img.convert("RGBA")
    rgba.alpha_composite(glow_behind(layer, f["glow"], s))
    rgba.alpha_composite(dark_outline(layer, s))
    rgba.alpha_composite(layer)
    img = rgba.convert("RGB")
    img = sheen(img, s, inner)
    img = vignette(img, s)
    frame(img, f, s)
    big = img.resize((256, 256), Image.LANCZOS)
    small = img.resize((128, 128), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=1.0, percent=60, threshold=2))
    return big, small


def sheet(items):
    cols = 3
    cw, ch = 256 + 12 + 128 + 24, 256 + 34
    rows = (len(items) + cols - 1) // cols
    W = Image.new("RGB", (cols * cw + 12, rows * ch + 12), (24, 26, 30))
    d = ImageDraw.Draw(W)
    try:
        font = ImageFont.load_default(size=14)
    except TypeError:
        font = ImageFont.load_default()
    for i, (cid, big, small) in enumerate(items):
        x = 12 + (i % cols) * cw
        y = 12 + (i // cols) * ch
        W.paste(big, (x, y))
        W.paste(small, (x + 256 + 12, y))
        d.text((x, y + 260), cid, fill=(220, 224, 230), font=font)
    return W


def main():
    with open(os.path.join(RENDERS, "manifest.json"), encoding="utf-8") as fh:
        manifest = json.load(fh)
    order = {"allied": 0, "soviet": 1, "empire": 2, "neutral": 3}
    ids = sorted(manifest, key=lambda k: (order[manifest[k]["faction"]], k))
    items = []
    for cid in ids:
        big, small = compose(dict(manifest[cid], id=cid))
        big.save(os.path.join(HERE, f"{cid}.png"), optimize=True)
        small.save(os.path.join(HERE, f"{cid}_128.png"), optimize=True)
        items.append((cid, big, small))
        print("cameo", cid)
    sheet(items).save(os.path.join(HERE, "_sheet.png"), optimize=True)
    print(f"{len(items)} cameos + _sheet.png in {HERE}")


if __name__ == "__main__":
    main()
