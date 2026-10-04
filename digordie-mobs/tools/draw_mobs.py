"""
Draws the sprites of the new Dig or Die monsters (transparent background): 128x128 frames for
the monsters, 256x256 frames for the mini-bosses and the boss.

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

FRAME = 128             # drawing coordinates are always 0..128
SS = 4                  # supersampling (internal pixels per drawing unit)
N = FRAME * SS
OUT_SIZE = 128          # size of the saved frame


def set_output(size):
    """Frames of `size` pixels; drawing stays in 128-space, supersampling follows."""
    global SS, N, OUT_SIZE
    OUT_SIZE = size
    SS = size * 4 // FRAME
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
    return img.resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS)


# ---------------------------------------------------------------- acid slime
def acid_slime(kind, king=False):
    sx, sy, lift = {'stand': (1, 1, 0), 'run1': (1.12, 0.9, 0), 'run2': (0.92, 1.08, 0),
                    'jump': (0.84, 1.2, 10), 'fight': (1.16, 0.94, 0), 'dead': (1.55, 0.32, 0)}[kind]
    dead = kind == 'dead'
    base = GROUND - lift
    rx, ry = 33 * sx, 26 * sy
    cy = base - ry + 3
    body = union(ellipse_mask(64, cy, rx, ry), ellipse_mask(64, base - 5, rx + 3, 6 * max(sy, 0.6)))
    dark, mid, light = (hexc('#3E5A3A'), hexc('#6E8A5E'), hexc('#B8C9A8')) if dead else \
        (hexc('#1F8A2E'), hexc('#45C83F'), hexc('#C8FF7A'))
    if king and not dead:
        dark, mid, light = hexc('#0B5E3B'), hexc('#1FAE63'), hexc('#9DFFC4')
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
    if king:
        layers.extend(king_face(kind, cy, rx, ry, top, dead))
        return finish(stack(*layers))
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
def crystal_spider(kind, queen=False):
    dead = kind == 'dead'
    lift = 9 if kind == 'jump' else 0
    by = GROUND - 26 - lift if not dead else GROUND - 12
    if queen:
        by -= 4
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
    legs_dark = line_layer(legs, dark, 6.4 if queen else 5.2)
    legs_light = line_layer([[ (x, y - 0.8) for x, y in leg] for leg in legs], hexc('#5A4A78') if not dead else hexc('#5B5B66'), 2.2)

    abdomen = ellipse_mask(64, by - 2, 29 if queen else 25, 19 if queen else 17)
    head = ellipse_mask(64, by + 9, 17 if queen else 15, 11 if queen else 10)
    body = union(abdomen, head)
    layers = [
        solid(outline(body, 2), hexc('#120B1E')),
        shaded(abdomen, hexc('#2A1D45') if not dead else hexc('#3A3A44'), hexc('#7A5AB0') if not dead else hexc('#77777F'), 56, by - 12, 34),
        shaded(head, hexc('#24183B') if not dead else hexc('#35353D'), hexc('#6A4E98') if not dead else hexc('#6E6E76'), 60, by + 4, 20),
    ]
    if queen and not dead:
        veins = [[(64, by - 16), (58, by - 6), (52, by + 2)], [(64, by - 16), (70, by - 6), (76, by + 2)],
                 [(50, by - 10), (42, by - 2)], [(78, by - 10), (86, by - 2)]]
        layers.append(glow(Image.fromarray(np.asarray(line_layer(veins, (255, 255, 255, 255), 1.6))[..., 3]), hexc('#C77DFF'), 2, 1.0))
        layers.append(line_layer(veins, hexc('#E9C8FF'), 1.2))
    # crystals on the back
    crystals = [(64, by - 14, 7, 30), (49, by - 10, 5, 19), (79, by - 10, 5, 19), (39, by - 4, 4, 11), (89, by - 4, 4, 11)]
    if queen:
        crystals = [(64, by - 16, 8, 38), (52, by - 13, 6, 27), (76, by - 13, 6, 27), (42, by - 8, 5, 18),
                    (86, by - 8, 5, 18), (35, by - 1, 4, 11), (93, by - 1, 4, 11)]
    cmask_list = []
    for cx, base_y, w, h in crystals:
        lean = (cx - 64) * 0.25
        pts = [(cx - w, base_y), (cx - w * 0.7 + lean * 0.5, base_y - h * 0.7), (cx + lean, base_y - h),
               (cx + w * 0.7 + lean * 0.5, base_y - h * 0.7), (cx + w, base_y)]
        cmask_list.append((poly_mask(pts), cx, base_y, h, pts))
    allc = union(*[c[0] for c in cmask_list])
    if not dead:
        layers.append(glow(allc, hexc('#C77DFF') if queen else hexc('#5FF2FF'), 6 if queen else 5, 0.8 if queen else 0.7))
    layers.append(solid(outline(allc, 1.4), hexc('#0C4E58') if not dead else hexc('#3D4A4C')))
    for m, cx, base_y, h, pts in cmask_list:
        crystal_dark = hexc('#6A2BD9') if queen else hexc('#1A9DB3')
        crystal_light = hexc('#F6E9FF') if queen else hexc('#E6FFFF')
        layers.append(shaded(m, crystal_dark if not dead else hexc('#5E7377'), crystal_light if not dead else hexc('#A9B8BA'),
                             cx - 2, base_y - h * 0.75, h * 1.1))
        # facet line
        layers.append(line_layer([[pts[2], ((pts[0][0] + pts[4][0]) / 2, base_y)]], (255, 255, 255, 90), 0.8))
    # eyes
    if dead:
        layers.append(line_layer([[(57, by + 8), (61, by + 8)], [(67, by + 8), (71, by + 8)]], hexc('#151018'), 1.3))
    else:
        eyes = [(58.5, by + 7, 2.6), (69.5, by + 7, 2.6), (54, by + 11, 1.6), (74, by + 11, 1.6)]
        if queen:
            eyes = [(58, by + 6, 3), (70, by + 6, 3), (53, by + 10, 2), (75, by + 10, 2), (61, by + 11, 1.6), (67, by + 11, 1.6)]
        em = union(*[ellipse_mask(x, y, r, r) for x, y, r in eyes])
        col = hexc('#FF3B6B') if kind != 'fight' else hexc('#FF7A2F')
        layers.append(glow(em, col, 2.5, 0.9))
        layers.append(solid(em, col))
        layers.append(solid(union(*[ellipse_mask(x - r * 0.35, y - r * 0.35, r * 0.35, r * 0.35) for x, y, r in eyes]), (255, 240, 240, 255)))
        # fangs
        layers.append(line_layer([[(61, by + 15), (60, by + 20)], [(67, by + 15), (68, by + 20)]], hexc('#E8E0F0'), 1.6))
    return finish(stack(legs_dark, legs_light, *layers))


# ----------------------------------------------------------------- lava wisp
def lava_wisp(kind, lord=False):
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
    layers.append(glow(ellipse_mask(64, cy - 4, r + 16, r + 18), hexc('#FF4A12') if lord else hexc('#FF7A1A'), 10, 0.7 if lord else 0.55))
    if lord:
        # obsidian horns behind the flames
        for side in (-1, 1):
            horn = poly_mask([(64 + side * 12, cy - r * 0.7), (64 + side * 22, cy - r * 0.45), (64 + side * 30, cy - r - 16),
                              (64 + side * 34, cy - r - 30), (64 + side * 24, cy - r - 12)])
            layers.append(solid(outline(horn, 1.2), hexc('#1A0A06')))
            layers.append(shaded(horn, hexc('#140806'), hexc('#5A3A30'), 64 + side * 20, cy - r - 8, 22))
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
    if lord:
        layers.append(shaded(core, hexc('#B0200A'), hexc('#E8FBFF'), 64, cy - 2, r * 1.25))
        layers.append(solid(ellipse_mask(64, cy - 2, r * 0.55, r * 0.55).filter(ImageFilter.GaussianBlur(sc(3))), hexc('#9FE6FF'), 0.55))
    else:
        layers.append(shaded(core, hexc('#D43A0C'), hexc('#FFF6C2'), 60, cy - 6, r * 1.35))
    # face
    for s in (-1, 1):
        ex, ey = 64 + s * 8, cy - 1
        w, h = (5.2, 3.4) if kind != 'fight' else (5.8, 4.2)
        eye = poly_mask([(ex - w, ey + s * 0), (ex - w * 0.2 * s, ey - h), (ex + w, ey - s * 0.5 * h * 0.2), (ex + w * 0.2 * s, ey + h * 0.8)])
        layers.append(solid(eye, hexc('#3A0800')))
        layers.append(solid(ellipse_mask(ex + s * 0.8, ey - 0.3, 1.2, 1.2), hexc('#FFFFFF') if lord else hexc('#FFE9A0')))
        if lord:  # angry brows
            layers.append(line_layer([[(ex + s * 6, ey - 7), (ex - s * 4, ey - 3)]], hexc('#2A0500'), 2))
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


# ----------------------------------------------------------- slime king face
def king_face(kind, cy, rx, ry, top, dead):
    """Big angry eyes in the body and a golden crown, for the Slime King."""
    out = []
    gold_dark, gold_light, gold_line = hexc('#9A6A00'), hexc('#FFE77A'), hexc('#4A3000')

    def crown(cx, base, w, tilt):
        pts = []
        spikes = [(-1.0, 0.62), (-0.5, 0.8), (0.0, 1.0), (0.5, 0.8), (1.0, 0.62)]
        h = w * 0.85
        pts.append((-w, 0))
        for i, (px, ph) in enumerate(spikes):
            pts.append((px * w, -h * ph))
            if i < len(spikes) - 1:
                pts.append(((px + 0.25) * w, -h * 0.32))
        pts.append((w, 0))
        ca, sa = math.cos(tilt), math.sin(tilt)
        world = [(cx + x * ca - y * sa, base + x * sa + y * ca) for x, y in pts]
        m = poly_mask(world)
        res = [solid(outline(m, 1.2), gold_line), shaded(m, gold_dark, gold_light, cx - w * 0.4, base - h * 0.7, w * 1.8)]
        band = poly_mask([(cx + x * ca - y * sa, base + x * sa + y * ca) for x, y in [(-w, 0), (w, 0), (w, -h * 0.22), (-w, -h * 0.22)]])
        res.append(solid(band, hexc('#C98A00'), 0.8))
        for gx, col in ((-0.55, '#3D7BFF'), (0.0, '#FF2E4D'), (0.55, '#3D7BFF')):
            x, y = gx * w, -h * 0.11
            res.append(solid(ellipse_mask(cx + x * ca - y * sa, base + x * sa + y * ca, 2.2, 2.2), hexc(col)))
        return res

    if dead:
        out.extend(crown(64 + rx * 0.45, GROUND - 7, 11, 0.5))
        for side in (-1, 1):
            ex, ey = 64 + side * 12, GROUND - 6
            out.append(line_layer([[(ex - 3, ey - 3), (ex + 3, ey + 3)], [(ex - 3, ey + 3), (ex + 3, ey - 3)]], hexc('#1E2A1C'), 1.6))
        return out

    out.extend(crown(64, top + 6, 19, 0.0))
    for side in (-1, 1):
        ex, ey = 64 + side * rx * 0.33, cy - ry * 0.1
        eye = ellipse_mask(ex, ey, 7.5, 8.5)
        out.append(solid(outline(eye, 1.4), hexc('#06301A')))
        out.append(shaded(eye, hexc('#CDEFD8'), hexc('#FFFFFF'), ex - 2, ey - 3, 10))
        py = ey + (2.5 if kind == 'fight' else 1.5)
        out.append(solid(ellipse_mask(ex + side * -0.8, py, 3.6, 4.2), hexc('#0C120E')))
        out.append(solid(ellipse_mask(ex - 1.2, py - 1.5, 1.1, 1.1), (255, 255, 255, 255)))
        # angry brows: the inner end (towards the middle) is lower
        out.append(line_layer([[(ex + side * 9, ey - 14), (ex - side * 5, ey - 8)]], hexc('#06301A'), 3))
    if kind == 'fight':
        my = cy + ry * 0.38
        out.append(solid(ellipse_mask(64, my, rx * 0.45, ry * 0.28), hexc('#06301A')))
        teeth = layer()
        td = ImageDraw.Draw(teeth)
        for i in range(7):
            tx = 64 - rx * 0.35 + i * rx * 0.7 / 6
            ty = my - ry * 0.28 + 1
            td.polygon([(sc(tx - 1.8), sc(ty)), (sc(tx + 1.8), sc(ty)), (sc(tx), sc(ty + 4))], fill=(235, 255, 225, 255))
        out.append(teeth)
    else:
        out.append(line_layer([[(64 - 8, cy + ry * 0.42), (64, cy + ry * 0.34), (64 + 8, cy + ry * 0.42)]], hexc('#06301A'), 2))
    return out


# ------------------------------------------------------------- abyss eye boss
def abyss_eye(kind):
    dead = kind == 'dead'
    phase = {'stand': 0.0, 'run1': 0.17, 'run2': 0.5, 'jump': 0.33, 'fight': 0.75, 'dead': 0}[kind]
    cx, cy = 64, 50 if not dead else 66
    R = 30 if kind != 'fight' else 31
    layers = []
    # aura
    if not dead:
        layers.append(glow(ellipse_mask(cx, cy, R + 12, R + 12), hexc('#9B30FF') if kind != 'fight' else hexc('#FF2E6A'), 12, 0.65))
    # tentacles
    tents = []
    for i in range(6):
        x0 = cx + (i - 2.5) * 8.5
        pts = []
        for k in range(14):
            t = k / 13
            y = cy + R * 0.7 + t * (44 if not dead else 22)
            sway = 0 if dead else math.sin((phase + t * 0.8 + i * 0.19) * math.tau) * 6 * t
            spread = (i - 2.5) * (5 if kind == 'fight' else 2) * t
            pts.append((x0 + sway + spread, y))
        tents.append(pts)
    tent_dark = hexc('#3A1650') if not dead else hexc('#3C3842')
    layers.append(line_layer(tents, tent_dark, 5))
    layers.append(line_layer([[(x, y - 0.8) for x, y in t] for t in tents], hexc('#7A3FA0') if not dead else hexc('#66626C'), 2))
    if not dead:
        tips = union(*[ellipse_mask(t[-1][0], t[-1][1], 2.2, 2.2) for t in tents])
        layers.append(glow(tips, hexc('#E08CFF'), 2, 1.0))
        layers.append(solid(tips, hexc('#F3D6FF')))
    # orbiting crystal shards (behind the eye on the far side of the orbit)
    shards_back, shards_front = [], []
    if not dead:
        for k in range(5):
            a = (phase + k / 5.0) * math.tau
            d = R + 15
            x, y = cx + math.cos(a) * d, cy + math.sin(a) * d * 0.55 - 2
            size = 4.5 + 1.5 * math.sin(a)
            m = poly_mask([(x, y - size * 1.6), (x + size * 0.7, y), (x, y + size * 1.6), (x - size * 0.7, y)])
            (shards_front if math.sin(a) > 0 else shards_back).append(m)
    def draw_shards(ms):
        if not ms:
            return []
        u = union(*ms)
        return [glow(u, hexc('#C77DFF'), 2.5, 0.9), solid(outline(u, 0.8), hexc('#3A0F6B')), shaded(u, hexc('#7B3BFF'), hexc('#F8EDFF'), cx, cy - R, R * 2.5)]
    layers.extend(draw_shards(shards_back))
    # eyeball
    ball = ellipse_mask(cx, cy, R, R)
    layers.append(solid(outline(ball, 2), hexc('#1E0A2C')))
    layers.append(shaded(ball, hexc('#8E79AE') if not dead else hexc('#55525A'), hexc('#F7F0FF') if not dead else hexc('#9A97A0'), cx - 8, cy - 10, R * 1.5))
    if not dead:
        rnd = random.Random(5)
        veins = []
        for k in range(9):
            a = rnd.uniform(0, math.tau)
            x0, y0 = cx + math.cos(a) * R * 0.97, cy + math.sin(a) * R * 0.97
            x1, y1 = cx + math.cos(a + 0.15) * R * 0.62, cy + math.sin(a + 0.15) * R * 0.62
            veins.append([(x0, y0), ((x0 + x1) / 2 + rnd.uniform(-2, 2), (y0 + y1) / 2 + rnd.uniform(-2, 2)), (x1, y1)])
        layers.append(line_layer(veins, hexc('#D0204A', 150), 0.9))
        # iris + slit pupil
        open_ = {'jump': 1.12, 'fight': 0.92}.get(kind, 1.0)
        ir = R * 0.48 * open_
        iris = ellipse_mask(cx, cy, ir, ir)
        if kind == 'fight':
            layers.append(shaded(iris, hexc('#5A0018'), hexc('#FF8A3D'), cx, cy, ir))
        else:
            layers.append(shaded(iris, hexc('#2A0E52'), hexc('#7FF6FF'), cx, cy, ir))
        layers.append(solid(outline(iris, 0.8), hexc('#12051F')))
        layers.append(solid(ellipse_mask(cx, cy, R * (0.05 if kind == 'fight' else 0.09), ir * 0.78), hexc('#06020B')))
        layers.append(solid(ellipse_mask(cx - R * 0.32, cy - R * 0.36, R * 0.16, R * 0.1), (255, 255, 255, 255), 0.75))
    # eyelids
    lid_dark, lid_light = (hexc('#2E1040'), hexc('#7A3FA0')) if not dead else (hexc('#2C2A30'), hexc('#5E5A64'))
    if dead:
        lid = ellipse_mask(cx, cy, R + 0.5, R + 0.5)
        layers.append(shaded(lid, lid_dark, lid_light, cx - 6, cy - 12, R * 1.6))
        layers.append(line_layer([[(cx - R * 0.9, cy + 2), (cx, cy + 6), (cx + R * 0.9, cy + 2)]], hexc('#151318'), 1.6))
    else:
        open_lid = {'jump': 0.25, 'fight': 0.5}.get(kind, 0.42)
        top_lid = ellipse_mask(cx, cy - R * (1.0 - open_lid * 0.2) - 2, R * 1.08, R * open_lid)
        bot_lid = ellipse_mask(cx, cy + R * 1.0 + 2, R * 1.02, R * open_lid * 0.8)
        lids = Image.fromarray(np.minimum(np.asarray(union(top_lid, bot_lid)), np.asarray(outline(ball, 2.5))))
        layers.append(solid(outline(lids, 1.0), hexc('#12051F')))
        layers.append(shaded(lids, lid_dark, lid_light, cx - 8, cy - R, R * 1.4))
        # fleshy inner rim of the lids
        rim = Image.fromarray(np.clip(np.asarray(outline(lids, 1.4), np.float32) - np.asarray(lids, np.float32), 0, 255).astype(np.uint8))
        rim = Image.fromarray(np.minimum(np.asarray(rim), np.asarray(ball)))
        layers.append(solid(rim, hexc('#D2386C'), 0.9))
        # lashes along the upper lid
        lashes = []
        for k in range(7):
            a = math.radians(-54 + k * 18)
            bx, by_ = cx + math.sin(a) * (R + 1), cy - math.cos(a) * (R + 1)
            lashes.append([(bx, by_), (bx + math.sin(a) * 7, by_ - math.cos(a) * 7)])
        layers.append(line_layer(lashes, hexc('#1A0726'), 1.6))
    layers.extend(draw_shards(shards_front))
    return finish(stack(*layers))


MOBS = {
    'acid_slime': acid_slime,
    'crystal_spider': crystal_spider,
    'lava_wisp': lava_wisp,
    'deep_jelly': deep_jelly,
}

BOSSES = {
    'slime_king': lambda kind: acid_slime(kind, king=True),
    'crystal_matriarch': lambda kind: crystal_spider(kind, queen=True),
    'pyre_lord': lambda kind: lava_wisp(kind, lord=True),
    'abyss_eye': abyss_eye,
}


def main():
    import sys
    only = set(sys.argv[1:])  # optional: names to redraw, e.g. abyss_eye slime_king
    os.makedirs(OUT, exist_ok=True)
    for size, group in ((128, MOBS), (256, BOSSES)):
        set_output(size)
        for name, fn in group.items():
            if only and name not in only:
                continue
            for kind in FRAMES:
                fn(kind).save(os.path.join(OUT, '%s_%s.png' % (name, kind)))
    print('sprites written to', os.path.abspath(OUT))


if __name__ == '__main__':
    main()
