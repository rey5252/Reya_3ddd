"""
Tiny pixel-art engine for item sprites in the style of the game (and of the DODModAPI "More Items" mod):
hard edges, 4-tone palettes, light from the top-left, a dark 1-pixel outline, no blur.

Shapes are rasterized without anti-aliasing on a small logical canvas, shaded per pixel and then
scaled up with nearest-neighbour, so every logical pixel becomes a crisp square.
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw

# 4 tones each, dark -> light
PALETTES = {
    'steel':      ['#1F252D', '#39424E', '#57636F', '#8A97A3'],
    'darksteel':  ['#14181E', '#232931', '#343C46', '#4E5864'],
    'gunmetal':   ['#1A1D22', '#2C3138', '#454C55', '#69727C'],
    'purplemetal': ['#1C1426', '#30233F', '#47365C', '#665083'],
    'gold':       ['#5A3A08', '#8F6414', '#C79A2A', '#F2D36B'],
    'acid':       ['#155A1A', '#2A8A26', '#55C23F', '#B5F27A'],
    'violet':     ['#2A1460', '#4B2A9E', '#7D55D6', '#C9B2FF'],
    'cyan':       ['#0F4E5A', '#1A8A9C', '#3FC6D6', '#B0F6FF'],
    'fire':       ['#6A1404', '#C0400C', '#F28A1C', '#FFE07A'],
    'obsidian':   ['#0E0809', '#1E1214', '#311F22', '#4C3438'],
    'abyss':      ['#190A26', '#371853', '#622C8A', '#9963C6'],
    'eye':        ['#6A5C7C', '#A194B4', '#D4CAE0', '#F7F3FB'],
    'jelly':      ['#12305E', '#1F5498', '#3C88CE', '#94D4F4'],
    'glass':      ['#55676C', '#88A0A5', '#BCD3D6', '#EEF8F8'],
    'cork':       ['#3E2410', '#6A4020', '#96643A', '#C49264'],
    'red':        ['#5E0A10', '#A0141C', '#DC2E36', '#FF8A80'],
    'pink':       ['#6A1E44', '#A8306A', '#DC5A96', '#FFB0D4'],
}


def rgb(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


class Canvas:
    def __init__(self, w, h, seed=1):
        self.w, self.h = w, h
        self.rgba = np.zeros((h, w, 4), np.float32)
        self.rnd = random.Random(seed)
        self.glows = []

    # ---------------------------------------------------------------- masks
    def _mask(self, draw_fn):
        m = Image.new('L', (self.w, self.h), 0)
        draw_fn(ImageDraw.Draw(m))
        return np.asarray(m) > 0

    def poly(self, pts):
        return self._mask(lambda d: d.polygon([(round(x), round(y)) for x, y in pts], fill=255))

    def rect(self, x0, y0, x1, y1):
        """Inclusive pixel rectangle."""
        return self._mask(lambda d: d.rectangle([round(x0), round(y0), round(x1), round(y1)], fill=255))

    def ellipse(self, cx, cy, rx, ry):
        return self._mask(lambda d: d.ellipse([round(cx - rx), round(cy - ry), round(cx + rx), round(cy + ry)], fill=255))

    def line(self, pts, width=1):
        return self._mask(lambda d: d.line([(round(x), round(y)) for x, y in pts], fill=255, width=width))

    @staticmethod
    def union(*masks):
        out = masks[0].copy()
        for m in masks[1:]:
            out |= m
        return out

    # -------------------------------------------------------------- shading
    def _tones_bevel(self, mask):
        up = np.zeros_like(mask); up[1:] = mask[:-1]
        down = np.zeros_like(mask); down[:-1] = mask[1:]
        left = np.zeros_like(mask); left[:, 1:] = mask[:, :-1]
        right = np.zeros_like(mask); right[:, :-1] = mask[:, 1:]
        up2 = np.zeros_like(mask); up2[2:] = mask[:-2]
        t = np.ones(mask.shape, np.int8)
        t[mask & up & ~up2] = 2            # second row from the top
        t[mask & ~down] = 0                # bottom edge
        t[mask & ~right & up] = 0          # right edge
        t[mask & ~left & up & down] = 2    # left edge
        t[mask & ~up] = 3                  # top edge
        return t

    def _tones_radial(self, mask, lx, ly, radius):
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        d = np.sqrt((xx + 0.5 - lx) ** 2 + (yy + 0.5 - ly) ** 2) / max(radius, 1e-3)
        t = np.select([d < 0.3, d < 0.62, d < 0.95], [3, 2, 1], 0).astype(np.int8)
        down = np.zeros_like(mask); down[:-1] = mask[1:]
        t[mask & ~down] = 0
        return t

    def fill(self, mask, palette, shade='bevel', light=None, radius=None, alpha=1.0, noise=0.0, tones=None):
        """Paints a mask with a 4-tone palette. shade: bevel | radial | flat (tones = fixed tone index)."""
        pal = [rgb(c) for c in PALETTES[palette]] if isinstance(palette, str) else [rgb(c) for c in palette]
        if shade == 'flat':
            t = np.full(mask.shape, tones if tones is not None else 1, np.int8)
        elif shade == 'radial':
            ys, xs = np.nonzero(mask)
            if light is None:
                light = (xs.min() + (xs.max() - xs.min()) * 0.35, ys.min() + (ys.max() - ys.min()) * 0.3)
            if radius is None:
                radius = max(xs.max() - xs.min(), ys.max() - ys.min()) * 0.85 + 1
            t = self._tones_radial(mask, light[0], light[1], radius)
        else:
            t = self._tones_bevel(mask)
        if noise > 0:
            ys, xs = np.nonzero(mask)
            for y, x in zip(ys, xs):
                if 0 < t[y, x] < 3 and self.rnd.random() < noise:
                    t[y, x] += self.rnd.choice((-1, 1))
        for i in range(4):
            sel = mask & (t == i)
            self.rgba[sel, :3] = pal[i]
            self.rgba[sel, 3] = 255 * alpha

    def color(self, mask, hex_color, alpha=1.0):
        self.rgba[mask, :3] = rgb(hex_color)
        self.rgba[mask, 3] = 255 * alpha

    def pixel(self, x, y, hex_color, alpha=1.0):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.w and 0 <= y < self.h:
            self.rgba[y, x, :3] = rgb(hex_color)
            self.rgba[y, x, 3] = 255 * alpha

    def erase(self, mask):
        self.rgba[mask] = 0

    def glow(self, mask, hex_color, alpha=0.35):
        """A 1-pixel halo around a bright part, added after the outline (pixel-art 'glow')."""
        self.glows.append((mask, hex_color, alpha))

    # -------------------------------------------------------------- finish
    def outline(self, darken=0.28):
        """Dark 1-pixel outline around everything drawn so far, tinted by the neighbouring colour."""
        a = self.rgba[..., 3] > 0
        out = self.rgba.copy()
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            src = np.roll(np.roll(self.rgba, dy, axis=0), dx, axis=1)
            src_a = np.roll(np.roll(a, dy, axis=0), dx, axis=1)
            sel = ~a & src_a & (out[..., 3] == 0)
            out[sel, :3] = src[sel, :3] * darken
            out[sel, 3] = 255
        self.rgba = out

    def finish(self, scale=2, outline=True):
        if outline:
            self.outline()
        for mask, hex_color, alpha in self.glows:
            ring = np.zeros_like(mask)
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ring |= np.roll(np.roll(mask, dy, axis=0), dx, axis=1)
            sel = ring & (self.rgba[..., 3] == 0)
            self.rgba[sel, :3] = rgb(hex_color)
            self.rgba[sel, 3] = 255 * alpha
        img = Image.fromarray(self.rgba.clip(0, 255).astype(np.uint8), 'RGBA')
        return img.resize((self.w * scale, self.h * scale), Image.NEAREST)


class Placed:
    """Draws a design given in local pixel units at an offset and scale on a canvas."""

    def __init__(self, canvas, ox, oy, scale=1.0):
        self.c, self.ox, self.oy, self.s = canvas, ox, oy, scale

    def p(self, x, y):
        return (self.ox + x * self.s, self.oy + y * self.s)

    def poly(self, pts):
        return self.c.poly([self.p(x, y) for x, y in pts])

    def rect(self, x0, y0, x1, y1):
        return self.poly([(x0, y0), (x1 + 1, y0), (x1 + 1, y1 + 1), (x0, y1 + 1)])

    def ellipse(self, cx, cy, rx, ry):
        x, y = self.p(cx, cy)
        return self.c.ellipse(x, y, rx * self.s, ry * self.s)

    def line(self, pts, width=1):
        return self.c.line([self.p(x, y) for x, y in pts], max(1, int(round(width * self.s))))

    def pixel(self, x, y, color):
        px, py = self.p(x, y)
        self.c.pixel(px, py, color)
