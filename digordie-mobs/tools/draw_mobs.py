"""
Draws the sprites of the new Dig or Die monsters (128x128 frames, transparent background).

    python3 tools/draw_mobs.py            -> src/DigOrDieMobs/assets/*.png

Every creature is left-right symmetric (front view), so it looks right whichever way the
game flips it. Frames: stand, run1, run2, jump, fight, dead (the set vanilla monsters use).
Drawing happens at 4x size and is downscaled for smooth edges. Needs Pillow and numpy.
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

FRAME = 128
SS = 4                  # supersampling
N = FRAME * SS
GROUND = 102            # feet line, same framing as the DODModAPI example monster
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'DigOrDieMobs', 'assets')
FRAMES = ['stand', 'run1', 'run2', 'jump', 'fight', 'dead']


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def layer():
    return Image.new('RGBA', (N, N), (0, 0, 0, 0))


def sc(v):
    return v * SS


def ellipse_mask(cx, cy, rx, ry):
    m = Image.new('L', (N, N), 0)
    ImageDraw.Draw(m).ellipse([sc(cx - rx), sc(cy - ry), sc(cx + rx), sc(cy + ry)], fill=255)
    return m


def poly_mask(points):
    m = Image.new('L', (N, N), 0)
    ImageDraw.Draw(m).polygon([(sc(x), sc(y)) for x, y in points], fill=255)
    return m


def union(*masks):
    out = np.zeros((N, N), dtype=np.float32)
    for m in masks:
        out = np.maximum(out, np.asarray(m, dtype=np.float32))
    return Image.fromarray(out.astype(np.uint8))


def shaded(mask, dark, light, lx, ly, radius, alpha=1.0):
    """Fills a mask with a radial gradient: `light` at (lx, ly) fading to `dark` at `radius`."""
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    d = np.sqrt((xx - sc(lx)) ** 2 + (yy - sc(ly)) ** 2) / sc(radius)
    t = np.clip(d, 0, 1)[..., None]
    dark = np.array(dark[:3], np.float32)
    light = np.array(light[:3], np.float32)
    rgb = light * (1 - t) + dark * t
    a = np.asarray(mask, np.float32)[..., None] * alpha
    return Image.fromarray(np.concatenate([rgb, a], axis=2).clip(0, 255).astype(np.uint8), 'RGBA')


def solid(mask, color, alpha=1.0):
    arr = np.zeros((N, N, 4), np.float32)
    arr[..., :3] = color[:3]
    arr[..., 3] = np.asarray(mask, np.float32) * alpha * (color[3] / 255.0)
    return Image.fromarray(arr.clip(0, 255).astype(np.uint8), 'RGBA')


def outline(mask, width):
    return mask.filter(ImageFilter.MaxFilter(int(sc(width)) * 2 + 1))


def glow(mask, color, blur, strength=1.0):
    g = mask.filter(ImageFilter.GaussianBlur(sc(blur)))
    g = Image.fromarray((np.asarray(g, np.float32) * strength).clip(0, 255).astype(np.uint8))
    return solid(g, color)


def stack(*layers):
    out = layer()
    for l in layers:
        if l is not None:
            out.alpha_composite(l)
    return out


def line_layer(segments, color, width):
    """segments: list of point lists (polylines) in 128-space."""
    l = layer()
    d = ImageDraw.Draw(l)
    for pts in segments:
        p = [(sc(x), sc(y)) for x, y in pts]
        d.line(p, fill=color, width=int(sc(width)), joint='curve')
        r = sc(width) / 2
        for x, y in (p[0], p[-1]):
            d.ellipse([x - r, y - r, x + r, y + r], fill=color)
    return l


def finish(img):
    return img.resize((FRAME, FRAME), Image.LANCZOS)


# ---------------------------------------------------------------- acid slime
def acid_slime(kind):
    sx, sy, lift = {'stand': (1, 1, 0), 'run1': (1.12, 0.9, 0), 'run2': (0.92, 1.08, 0),
                    'jump': (0.84, 1.2, 10), 'fight': (1.16, 0.94, 0), 'dead': (1.55, 0.32, 0)}[kind]
    dead = kind == 'dead'
    base = GROUND - lift
    rx, ry = 33 * sx, 26 * sy
    cy = base - ry + 3
    body = union(ellipse_mask(64, cy, rx, ry), ellipse_mask(64, base - 5, rx + 3, 6 * max(sy, 0.6)))
    dark, mid, light = (hexc('#3E5A3A'), hexc('#6E8A5E'), hexc('#B8C9A8')) if dead else \
        (hexc('#1F8A2E'), hexc('#45C83F'), hexc('#C8FF7A'))
    layers = [
        None if dead else glow(body, hexc('#7DFF4F'), 6, 0.45),
        solid(outline(body, 2.2), hexc('#0B3512') if not dead else hexc('#283426')),
        shaded(body, dark, light, 64 - rx * 0.35, cy - ry * 0.45, max(rx, ry) * 1.6, 0.95),
    ]
    # bubbles inside the jelly
    rnd = random.Random(7)
    bubbles = layer()
    bd = ImageDraw.Draw(bubbles)
    for _ in range(0 if dead else 7):
        bx = 64 + rnd.uniform(-0.6, 0.6) * rx
        by = cy + rnd.uniform(-0.1, 0.6) * ry
        br = rnd.uniform(1.5, 3.5)
        bd.ellipse([sc(bx - br), sc(by - br), sc(bx + br), sc(by + br)], fill=(220, 255, 190, 110))
    layers.append(bubbles)
    # glossy highlight
    layers.append(solid(ellipse_mask(64 - rx * 0.38, cy - ry * 0.5, rx * 0.28, ry * 0.18), (255, 255, 255, 255), 0.55))
    # acid drips on the sides
    if not dead:
        for side in (-1, 1):
            dx = 64 + side * (rx * 0.82)
            drop = union(ellipse_mask(dx, base - 1, 3.2, 4.5), poly_mask([(dx - 2.5, base - 4), (dx + 2.5, base - 4), (dx, base - 11)]))
            layers.append(shaded(drop, dark, light, dx - 1, base - 3, 6))
    # eye stalks + eyes
    top = cy - ry
    eyes = layer()
    if dead:
        ed = ImageDraw.Draw(eyes)
        for side in (-1, 1):
            ex, ey = 64 + side * 11, cy - 2
            for a, b in (((-3, -3), (3, 3)), ((-3, 3), (3, -3))):
                ed.line([(sc(ex + a[0]), sc(ey + a[1])), (sc(ex + b[0]), sc(ey + b[1]))], fill=(30, 45, 28, 255), width=int(sc(1.6)))
        layers.append(eyes)
    else:
        stalk_h = 12 if kind != 'jump' else 15
        stalks = [[(64 + s * 9, top + 6), (64 + s * 12, top - stalk_h + 4)] for s in (-1, 1)]
        layers.append(line_layer(stalks, hexc('#1C7A28'), 3.2))
        for s in (-1, 1):
            ex, ey = 64 + s * 12, top - stalk_h + 2
            eye = ellipse_mask(ex, ey, 6, 6)
            layers.append(solid(outline(eye, 1.4), hexc('#0B3512')))
            layers.append(shaded(eye, hexc('#C8E6C0'), hexc('#FFFFFF'), ex - 2, ey - 2, 8))
            py = ey + (2 if kind == 'fight' else 0.8)
            layers.append(solid(ellipse_mask(ex, py, 2.8, 3.2), hexc('#10140F')))
            layers.append(solid(ellipse_mask(ex - 1, py - 1.2, 0.9, 0.9), (255, 255, 255, 255)))
        if kind == 'fight':
            mouth = ellipse_mask(64, cy + ry * 0.25, rx * 0.42, ry * 0.3)
            layers.append(solid(mouth, hexc('#0E3E14')))
            teeth = layer()
            td = ImageDraw.Draw(teeth)
            mw = rx * 0.42
            for i in range(5):
                tx = 64 - mw * 0.7 + i * (mw * 1.4 / 4)
                ty = cy + ry * 0.25 - ry * 0.3 + 1
                td.polygon([(sc(tx - 1.6), sc(ty)), (sc(tx + 1.6), sc(ty)), (sc(tx), sc(ty + 3.5))], fill=(235, 255, 210, 255))
            layers.append(teeth)
    return finish(stack(*layers))


# ------------------------------------------------------------- crystal spider
def crystal_spider(kind):
    dead = kind == 'dead'
    lift = 9 if kind == 'jump' else 0
    by = GROUND - 26 - lift if not dead else GROUND - 12
    legs = []
    for i in range(4):
        for s in (-1, 1):
            hip = (64 + s * (12 + i * 1.5), by + 4 + i * 2.5)
            spread = 21 + i * 5
            foot_y = GROUND - lift * 0.5
            knee = (64 + s * spread, by - 14 + i * 3)
            foot = (64 + s * (spread + 7 + i * 1.5), foot_y)
            up = (kind == 'run1' and (i + (s > 0)) % 2 == 0) or (kind == 'run2' and (i + (s > 0)) % 2 == 1)
            if up:
                knee = (knee[0], knee[1] - 6)
                foot = (foot[0] - s * 3, foot[1] - 8)
            if kind == 'jump':
                knee = (64 + s * (spread - 4), by - 8 + i * 2)
                foot = (64 + s * (spread - 2), by + 10 + i * 2)
            if kind == 'fight' and i == 0:
                knee = (64 + s * 24, by - 26)
                foot = (64 + s * 18, by - 42)
            if dead:
                knee = (64 + s * (16 + i * 5), by - 16 - i * 2)
                foot = (64 + s * (10 + i * 4), by - 26 - i * 3)
            legs.append([hip, knee, foot])
    dark = hexc('#3A3A44') if dead else hexc('#1F1530')
    legs_dark = line_layer(legs, dark, 5.2)
    legs_light = line_layer([[ (x, y - 0.8) for x, y in leg] for leg in legs], hexc('#5A4A78') if not dead else hexc('#5B5B66'), 2.2)

    abdomen = ellipse_mask(64, by - 2, 25, 17)
    head = ellipse_mask(64, by + 9, 15, 10)
    body = union(abdomen, head)
    layers = [
        solid(outline(body, 2), hexc('#120B1E')),
        shaded(abdomen, hexc('#2A1D45') if not dead else hexc('#3A3A44'), hexc('#7A5AB0') if not dead else hexc('#77777F'), 56, by - 12, 34),
        shaded(head, hexc('#24183B') if not dead else hexc('#35353D'), hexc('#6A4E98') if not dead else hexc('#6E6E76'), 60, by + 4, 20),
    ]
    # crystals on the back
    crystals = [(64, by - 14, 7, 30), (49, by - 10, 5, 19), (79, by - 10, 5, 19), (39, by - 4, 4, 11), (89, by - 4, 4, 11)]
    cmask_list = []
    for cx, base_y, w, h in crystals:
        lean = (cx - 64) * 0.25
        pts = [(cx - w, base_y), (cx - w * 0.7 + lean * 0.5, base_y - h * 0.7), (cx + lean, base_y - h),
               (cx + w * 0.7 + lean * 0.5, base_y - h * 0.7), (cx + w, base_y)]
        cmask_list.append((poly_mask(pts), cx, base_y, h, pts))
    allc = union(*[c[0] for c in cmask_list])
    if not dead:
        layers.append(glow(allc, hexc('#5FF2FF'), 5, 0.7))
    layers.append(solid(outline(allc, 1.4), hexc('#0C4E58') if not dead else hexc('#3D4A4C')))
    for m, cx, base_y, h, pts in cmask_list:
        layers.append(shaded(m, hexc('#1A9DB3') if not dead else hexc('#5E7377'), hexc('#E6FFFF') if not dead else hexc('#A9B8BA'),
                             cx - 2, base_y - h * 0.75, h * 1.1))
        # facet line
        layers.append(line_layer([[pts[2], ((pts[0][0] + pts[4][0]) / 2, base_y)]], (255, 255, 255, 90), 0.8))
    # eyes
    if dead:
        layers.append(line_layer([[(57, by + 8), (61, by + 8)], [(67, by + 8), (71, by + 8)]], hexc('#151018'), 1.3))
    else:
        eyes = [(58.5, by + 7, 2.6), (69.5, by + 7, 2.6), (54, by + 11, 1.6), (74, by + 11, 1.6)]
        em = union(*[ellipse_mask(x, y, r, r) for x, y, r in eyes])
        col = hexc('#FF3B6B') if kind != 'fight' else hexc('#FF7A2F')
        layers.append(glow(em, col, 2.5, 0.9))
        layers.append(solid(em, col))
        layers.append(solid(union(*[ellipse_mask(x - r * 0.35, y - r * 0.35, r * 0.35, r * 0.35) for x, y, r in eyes]), (255, 240, 240, 255)))
        # fangs
        layers.append(line_layer([[(61, by + 15), (60, by + 20)], [(67, by + 15), (68, by + 20)]], hexc('#E8E0F0'), 1.6))
    return finish(stack(legs_dark, legs_light, *layers))


# ----------------------------------------------------------------- lava wisp
def lava_wisp(kind):
    dead = kind == 'dead'
    phase = {'stand': 0.0, 'run1': 0.33, 'run2': 0.66, 'jump': 0.15, 'fight': 0.5, 'dead': 0}[kind]
    cy = 66 if kind != 'jump' else 60
    r = 21 if kind != 'fight' else 23
    layers = []
    core = ellipse_mask(64, cy, r, r)
    if dead:
        smoke = layer()
        sd = ImageDraw.Draw(smoke)
        for i, (dx, dy, rr) in enumerate([(-6, -26, 7), (4, -36, 9), (-2, -48, 11)]):
            sd.ellipse([sc(64 + dx - rr), sc(cy + dy - rr), sc(64 + dx + rr), sc(cy + dy + rr)], fill=(90, 90, 95, 90 - i * 20))
        layers.append(smoke.filter(ImageFilter.GaussianBlur(sc(1.5))))
        layers.append(solid(outline(core, 1.6), hexc('#121010')))
        layers.append(shaded(core, hexc('#1E1A1A'), hexc('#5A4E4A'), 56, cy - 8, 30))
        cracks = [[(64, cy - 6), (59, cy + 2), (62, cy + 10)], [(66, cy - 2), (72, cy + 5)], [(55, cy - 9), (50, cy - 3)]]
        layers.append(line_layer(cracks, hexc('#C2410C', 200), 1.4))
        return finish(stack(*layers))
    # aura
    layers.append(glow(ellipse_mask(64, cy - 4, r + 16, r + 18), hexc('#FF7A1A'), 10, 0.55))
    # flames: pairs of tongues, symmetric
    flames = []
    for i, (ang, length, width) in enumerate([(0, 34, 9), (28, 26, 8), (52, 19, 7), (78, 13, 6)]):
        for s in ((1,) if ang == 0 else (-1, 1)):
            a = math.radians(ang * s)
            wob = math.sin((phase + i * 0.21) * math.tau) * 0.18
            L = length * (1 + wob)
            bx, byy = 64 + math.sin(a) * r * 0.75, cy - math.cos(a) * r * 0.75
            tx, ty = 64 + math.sin(a) * (r + L), cy - math.cos(a) * (r + L)
            px, py = math.cos(a) * width, math.sin(a) * width
            curl = math.sin((phase + i * 0.37) * math.tau) * 4 * s
            pts = [(bx - px, byy - py), (bx + (tx - bx) * 0.55 - px * 0.6 + curl, byy + (ty - byy) * 0.55 - py * 0.6),
                   (tx + curl, ty), (bx + (tx - bx) * 0.55 + px * 0.6 + curl, byy + (ty - byy) * 0.55 + py * 0.6), (bx + px, byy + py)]
            flames.append(poly_mask(pts))
    fm = union(*flames)
    layers.append(solid(fm.filter(ImageFilter.GaussianBlur(sc(0.6))), hexc('#E2380B')))
    inner = fm.filter(ImageFilter.MinFilter(int(sc(2.2)) * 2 + 1))
    layers.append(solid(inner.filter(ImageFilter.GaussianBlur(sc(0.8))), hexc('#FF9A1F')))
    inner2 = inner.filter(ImageFilter.MinFilter(int(sc(2)) * 2 + 1))
    layers.append(solid(inner2.filter(ImageFilter.GaussianBlur(sc(1))), hexc('#FFE26A'), 0.9))
    # core
    layers.append(solid(outline(core, 1.6), hexc('#7A1203')))
    layers.append(shaded(core, hexc('#D43A0C'), hexc('#FFF6C2'), 60, cy - 6, r * 1.35))
    # face
    for s in (-1, 1):
        ex, ey = 64 + s * 8, cy - 1
        w, h = (5.2, 3.4) if kind != 'fight' else (5.8, 4.2)
        eye = poly_mask([(ex - w, ey + s * 0), (ex - w * 0.2 * s, ey - h), (ex + w, ey - s * 0.5 * h * 0.2), (ex + w * 0.2 * s, ey + h * 0.8)])
        layers.append(solid(eye, hexc('#3A0800')))
        layers.append(solid(ellipse_mask(ex + s * 0.8, ey - 0.3, 1.2, 1.2), hexc('#FFE9A0')))
    if kind == 'fight':
        layers.append(solid(ellipse_mask(64, cy + 9, 6.5, 4.2), hexc('#3A0800')))
        layers.append(solid(ellipse_mask(64, cy + 10, 3.5, 2), hexc('#FF7A1A')))
    else:
        layers.append(line_layer([[(59, cy + 9), (64, cy + 11), (69, cy + 9)]], hexc('#5A1000'), 1.4))
    # embers
    rnd = random.Random(int(phase * 100) + 3)
    emb = layer()
    ed = ImageDraw.Draw(emb)
    for _ in range(9):
        a = rnd.uniform(0, math.tau)
        d = rnd.uniform(r + 6, r + 24)
        x, y = 64 + math.cos(a) * d, cy + math.sin(a) * d * 0.9 - 6
        rr = rnd.uniform(0.7, 1.5)
        ed.ellipse([sc(x - rr), sc(y - rr), sc(x + rr), sc(y + rr)], fill=(255, 200, 90, 230))
    layers.append(emb)
    return finish(stack(*layers))


# ----------------------------------------------------------------- deep jelly
def deep_jelly(kind):
    dead = kind == 'dead'
    phase = {'stand': 0.0, 'run1': 0.25, 'run2': 0.75, 'jump': 0.5, 'fight': 0.1, 'dead': 0}[kind]
    rx, ry = {'run1': (25, 26), 'run2': (32, 20), 'jump': (24, 27)}.get(kind, (29, 23))
    if dead:
        rx, ry = 32, 15
    cy = 52 if not dead else 74
    rim = cy + ry * 0.35
    layers = []
    body_dark, body_light = (hexc('#3F4C58'), hexc('#8A98A4')) if dead else (hexc('#1C4F9C'), hexc('#9BE8FF'))
    tent_col = hexc('#7FD8FF', 170) if not dead else hexc('#8A98A4', 150)
    # tentacles
    tents = []
    for i in range(7):
        x0 = 64 + (i - 3) * (rx * 0.25)
        pts = []
        for k in range(12):
            t = k / 11
            length = 52 if not dead else 30
            y = rim + t * length
            sway = 0 if dead else math.sin((phase + t * 0.9 + i * 0.13) * math.tau) * 5 * t
            spread = (i - 3) * (8 if kind == 'fight' else 2.5) * t
            pts.append((x0 + sway + spread, y))
        tents.append(pts)
    layers.append(line_layer(tents, tent_col, 1.8))
    # oral arms (frilly, pink)
    arms = []
    for s in (-1, 1):
        pts = []
        for k in range(10):
            t = k / 9
            y = rim + t * (34 if not dead else 22)
            sway = 0 if dead else math.sin((phase + t * 1.3 + (s > 0) * 0.5) * math.tau) * 4 * t
            pts.append((64 + s * 6 + sway + (s * 6 * t if kind == 'fight' else 0), y))
        arms.append(pts)
    layers.append(line_layer(arms, hexc('#FF8FD0', 200) if not dead else hexc('#9E8C98', 160), 4.2))
    # bell
    bell = union(ellipse_mask(64, cy, rx, ry), *[ellipse_mask(64 + (j - 3.5) * rx * 0.27, rim - 1, rx * 0.16, 3.4) for j in range(8)])
    cut = Image.new('L', (N, N), 0)
    ImageDraw.Draw(cut).rectangle([0, 0, N, sc(rim + 3)], fill=255)
    bell = Image.fromarray(np.minimum(np.asarray(bell), np.asarray(cut)))
    if not dead:
        layers.insert(0, glow(bell, hexc('#3FB6FF'), 9, 0.6 if kind != 'fight' else 0.95))
    layers.append(solid(outline(bell, 1.4), hexc('#0E2F5E') if not dead else hexc('#2A3138'), 0.9))
    layers.append(shaded(bell, body_dark, body_light, 60, cy - ry * 0.45, max(rx, ry) * 1.5, 0.85))
    # inner organ + glowing spots
    layers.append(solid(ellipse_mask(64, cy + ry * 0.05, rx * 0.45, ry * 0.35), hexc('#FFB3E6') if not dead else hexc('#A9A0A8'), 0.45))
    if not dead:
        spots = union(*[ellipse_mask(64 + dx * rx, cy + dy * ry, 1.8, 1.8)
                        for dx, dy in [(-0.55, -0.1), (0.55, -0.1), (-0.3, -0.5), (0.3, -0.5), (0, -0.65), (-0.75, 0.2), (0.75, 0.2)]])
        layers.append(glow(spots, hexc('#B6FFF9'), 2.2, 1.0))
        layers.append(solid(spots, hexc('#E8FFFE')))
    layers.append(solid(ellipse_mask(64 - rx * 0.4, cy - ry * 0.5, rx * 0.22, ry * 0.15), (255, 255, 255, 255), 0.5))
    if kind == 'fight':
        sparks = []
        rnd = random.Random(11)
        for s in (-1, 1):
            for k in range(2):
                x, y = 64 + s * (rx + 6 + k * 6), cy + 10 + k * 14
                sparks.append([(x, y), (x + s * 4, y + 4), (x, y + 7), (x + s * 5, y + 12)])
        layers.append(line_layer(sparks, hexc('#FFF27A'), 1.3))
    return finish(stack(*layers))


MOBS = {
    'acid_slime': acid_slime,
    'crystal_spider': crystal_spider,
    'lava_wisp': lava_wisp,
    'deep_jelly': deep_jelly,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in MOBS.items():
        for kind in FRAMES:
            fn(kind).save(os.path.join(OUT, '%s_%s.png' % (name, kind)))
    print('sprites written to', os.path.abspath(OUT))


if __name__ == '__main__':
    main()
