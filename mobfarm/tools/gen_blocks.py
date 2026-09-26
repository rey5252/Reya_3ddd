"""Generates the farm block textures and models (one set per tier) with whole-pixel geometry, so
every texture pixel shows as one pixel on the block.

Run from the mobfarm folder:  python3 tools/gen_blocks.py [--preview DIR]
Block: base plate 16x2x16, four 2x2 corner posts, 2x2 top rails, glass box 2..14 inside.
"""
import json, os, struct, sys, zlib

ROOT = "src/main/resources/assets/mobfarm"

# light, mid, dark, deep: metal/wood for the posts and trim; plate colours for the base top
TIERS = {
    "wooden":    ((196, 146, 88), (150, 104, 58), (104, 68, 34), (64, 40, 18)),
    "stone":     ((196, 198, 200), (146, 148, 152), (102, 104, 108), (60, 62, 66)),
    "iron":      ((238, 240, 244), (200, 204, 210), (150, 156, 164), (92, 98, 106)),
    "golden":    ((255, 236, 130), (236, 186, 60), (184, 128, 26), (110, 70, 12)),
    "diamond":   ((170, 250, 240), (80, 214, 206), (34, 150, 150), (16, 86, 92)),
    "netherite": ((118, 108, 116), (82, 74, 82), (58, 51, 58), (34, 29, 34)),
}
BLACK = (22, 18, 20)


def png(path, px, w, h, scale=1):
    rows = []
    for y in range(h * scale):
        row = b"\x00"
        for x in range(w * scale):
            row += bytes(px[y // scale][x // scale])
        rows.append(row)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w * scale, h * scale, 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(b"".join(rows), 9)) + ch(b"IEND", b""))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rnd(x, y, salt=0):
    h = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def grid(c=(0, 0, 0, 0)):
    return [[c] * 16 for _ in range(16)]


def base_top(pal):
    """Top of the plate: dark edge, bevel, plate with a small emblem in the middle. The posts stand
    on 1..3 in each corner, so the corners carry a darker socket."""
    l, m, d, dd = pal
    px = grid()
    for y in range(16):
        for x in range(16):
            c = mix(m, d, 0.25) if (rnd(x, y) & 3) == 0 else m
            px[y][x] = c + (255,)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[y][x] = dd + (255,)
    for i in range(1, 15):
        px[1][i] = l + (255,); px[i][1] = l + (255,)
        px[14][i] = d + (255,); px[i][14] = d + (255,)
    px[14][1] = m + (255,); px[1][14] = m + (255,)
    # sockets for the posts
    for (sx, sy) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        for y in range(sy, sy + 2):
            for x in range(sx, sx + 2):
                px[y][x] = dd + (255,)
    # emblem: a ring with a bright centre
    ring = [(6, 5), (7, 4), (8, 4), (9, 5), (10, 6), (11, 7), (11, 8), (10, 9), (9, 10), (8, 11), (7, 11),
            (6, 10), (5, 9), (4, 8), (4, 7), (5, 6)]
    for (x, y) in ring:
        px[y][x] = (l if y < 8 else d) + (255,)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[y][x] = mix(l, (255, 255, 255), 0.35) + (255,)
    px[8][8] = l + (255,)
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[y][x] = d + (255,)
    return px


def base_side(pal):
    """Rows 0..1 are the plate's 2px side: bright top edge, darker band with rivets."""
    l, m, d, dd = pal
    px = grid()
    for x in range(16):
        px[0][x] = l + (255,)
        px[1][x] = (dd if x in (0, 15) else (l if x in (3, 12) else d)) + (255,)
    for y in range(2, 16):
        for x in range(16):
            px[y][x] = d + (255,)
    return px


def frame(pal):
    """Posts and rails, laid out for exact UVs:
       0..2 x 0..2   post / rail end cap
       2..14 x 0..2  rail side (12 long, 2 high)
       0..2 x 2..14  post side (2 wide, 12 high)"""
    l, m, d, dd = pal
    px = grid(d + (255,))
    # caps
    px[0][0] = l + (255,); px[0][1] = l + (255,); px[1][0] = l + (255,); px[1][1] = m + (255,)
    # rail: light top row, mid lower row, darker bands next to the posts and in the middle
    for x in range(2, 14):
        px[0][x] = l + (255,)
        px[1][x] = m + (255,)
    for x in (2, 13):
        px[0][x] = m + (255,); px[1][x] = d + (255,)
    px[1][7] = d + (255,); px[1][8] = d + (255,)
    # post: light left column, mid right column, rings near the ends
    for y in range(2, 14):
        px[y][0] = l + (255,)
        px[y][1] = m + (255,)
    for y in (3, 12):
        px[y][0] = m + (255,); px[y][1] = d + (255,)
    return px


def glass():
    """Clear pane with a faint tint and two diagonal glints."""
    px = grid((220, 240, 245, 38))
    for k in range(16):
        for (x, y, a) in ((k, 15 - k, 110), (k + 1, 15 - k, 60), (k - 4, 15 - k, 70)):
            if 0 <= x < 16 and 0 <= y < 16 and 4 <= x <= 12:
                px[y][x] = (255, 255, 255, a)
    return px


def face(uv, tex, cull=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def model(tier):
    els = []
    els.append({"name": "base", "from": [0, 0, 0], "to": [16, 2, 16], "faces": {
        "down": face([0, 0, 16, 16], "#base", "down"),
        "up": face([0, 0, 16, 16], "#base"),
        **{s: face([0, 0, 16, 2], "#side", s) for s in ("north", "south", "west", "east")}}})
    posts = [(1, 1), (13, 1), (1, 13), (13, 13)]
    for (x, z) in posts:
        els.append({"name": "post", "from": [x, 2, z], "to": [x + 2, 14, z + 2], "faces": {
            **{s: face([0, 2, 2, 14], "#frame") for s in ("north", "south", "west", "east")},
            "up": face([0, 0, 2, 2], "#frame")}})
    for z in (1, 13):   # rails along x
        els.append({"name": "rail", "from": [3, 12, z], "to": [13, 14, z + 2], "faces": {
            "north": face([3, 0, 13, 2], "#frame"), "south": face([3, 0, 13, 2], "#frame"),
            "up": face([3, 0, 13, 2], "#frame")}})
    for x in (1, 13):   # rails along z
        els.append({"name": "rail", "from": [x, 12, 3], "to": [x + 2, 14, 13], "faces": {
            "west": face([3, 0, 13, 2], "#frame"), "east": face([3, 0, 13, 2], "#frame"),
            "up": face([3, 0, 13, 2], "#frame") | {"rotation": 90}}})
    els.append({"name": "glass", "from": [2, 2, 2], "to": [14, 12, 14], "faces": {
        **{s: face([2, 4, 14, 14], "#glass") for s in ("north", "south", "west", "east")},
        "up": face([2, 2, 14, 14], "#glass")}})
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "textures": {
            "particle": f"mobfarm:block/{tier}_mob_farm_base",
            "base": f"mobfarm:block/{tier}_mob_farm_base",
            "side": f"mobfarm:block/{tier}_mob_farm_side",
            "frame": f"mobfarm:block/{tier}_mob_farm_frame",
            "glass": "mobfarm:block/mob_farm_glass",
        },
        "elements": els,
    }


if __name__ == "__main__":
    tex = f"{ROOT}/textures/block"
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    png(f"{tex}/mob_farm_glass.png", glass(), 16, 16)
    for tier, pal in TIERS.items():
        png(f"{tex}/{tier}_mob_farm_base.png", base_top(pal), 16, 16)
        png(f"{tex}/{tier}_mob_farm_side.png", base_side(pal), 16, 16)
        png(f"{tex}/{tier}_mob_farm_frame.png", frame(pal), 16, 16)
        with open(f"{ROOT}/models/block/{tier}_mob_farm.json", "w") as f:
            json.dump(model(tier), f, indent=2)
            f.write("\n")
        if preview:
            png(f"{preview}/block_{tier}_base.png", base_top(pal), 16, 16, 12)
            png(f"{preview}/block_{tier}_frame.png", frame(pal), 16, 16, 12)
