"""Textures, models and blockstates for Starfall (run from the mod folder)."""
import json, math, os, struct, zlib

ROOT = "src/main/resources/assets/starfall"
T = (0, 0, 0, 0)


def png(path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + b"".join(bytes(p if len(p) == 4 else tuple(p) + (255,)) for p in row) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", len(px[0]), len(px), 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def grid(c=T, w=16, h=16):
    return [[c] * w for _ in range(h)]


def put(g, x, y, c):
    if 0 <= y < len(g) and 0 <= x < len(g[0]):
        g[y][x] = c


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def hsh(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3]) + tuple(c[3:])


def add(c, d):
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + tuple(c[3:])


def face_uv(d, f, t):
    """Minecraft's default UV for a face, moved back inside the texture when the element sticks out of 0..16
    (otherwise the face samples whatever sits next to the texture in the atlas)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    uv = {"down": [x0, 16 - z1, x1, 16 - z0], "up": [x0, z0, x1, z1],
          "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
          "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0]}[d]
    for a, b in ((0, 2), (1, 3)):
        lo, hi = min(uv[a], uv[b]), max(uv[a], uv[b])
        if hi - lo >= 16:
            uv[a], uv[b] = 0, 16
            continue
        shift = 0
        while lo + shift < 0:
            shift += 16
        while hi + shift > 16:
            shift -= 16
        if lo + shift < 0:
            # straddles a texture edge: slide it to start at the edge instead
            shift = -lo
        uv[a] += shift
        uv[b] += shift
    return [round(v, 3) for v in uv]


def el(frm, to, tex, tint=None, shade_=True, skip=(), uv=None, glow=False):
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        if d in skip:
            continue
        face = {"texture": "#" + tex, "uv": uv if uv is not None else face_uv(d, frm, to)}
        if tint is not None:
            face["tintindex"] = tint
        faces[d] = face
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if not shade_:
        e["shade"] = False
    if glow:
        e["forge_data"] = {"block_light": 15, "sky_light": 15}
    return e


# ================================================================ the remote
# A tall navy hand-held uplink: a big screen with a grille under it, ribs down the left, a hazard-striped box
# over the one button on the right, the skill's code under it, a keypad, red trims and a two-tone antenna.
# Each moving part is its own model so the item renderer can animate it.
R = f"{ROOT}/textures/item/remote"

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = add((34, 44, 76), int((hsh(x, 0, 1) - 0.5) * 6 + (hsh(x, y, 2) - 0.5) * 4) - (y // 8))
png(f"{R}/metal.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = add((66, 82, 124), int((hsh(x, y, 4) - 0.5) * 6)) if y not in (0, 15) else (92, 108, 150)
png(f"{R}/edge.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (16, 18, 30) if y % 2 == 0 else (30, 36, 58)
png(f"{R}/rubber.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = add((24, 30, 54), int((hsh(x, y, 3) - 0.5) * 5))
png(f"{R}/panel.png", g)

g = grid((12, 14, 24))
for i in range(16):
    for p_ in ((i, 0), (i, 15), (0, i), (15, i)):
        put(g, p_[0], p_[1], (74, 90, 134))
png(f"{R}/bezel.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (214, 220, 230) if y in (0, 1) else (156, 162, 176) if y < 13 else (110, 116, 130)
png(f"{R}/trim.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (240, 196, 30) if ((x + y) // 3) % 2 == 0 else (20, 20, 22)
png(f"{R}/hazard.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        edge = x in (0, 15) or y in (0, 15)
        streak = 3 <= (x + 15 - y) <= 5 or (x + 15 - y) == 9
        g[y][x] = (255, 236, 236, 110) if streak else (255, 200, 200, 80) if edge else (255, 150, 150, 40)
png(f"{R}/glass.png", g)

png(f"{R}/white.png", grid((255, 255, 255)))

g = grid()
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 7.5, y - 7.5) / 7.8
        k = max(0.0, 1.0 - d) ** 1.6
        v = int(255 * k)
        g[y][x] = (v, v, v)
png(f"{R}/halo.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (78, 90, 128) if y < 2 else (40, 48, 76) if y < 13 else (22, 26, 42)
png(f"{R}/key.png", g)

png(f"{R}/label.png", grid((18, 22, 38)))
png(f"{R}/well.png", grid((5, 5, 8)))
g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (5, 7, 12) if y % 2 else (9, 12, 20)
png(f"{R}/screen.png", g)
png(f"{R}/metal_dark.png", grid((18, 19, 24)))

TEX = {k: f"starfall:item/remote/{k}" for k in
       ("metal", "edge", "rubber", "panel", "bezel", "trim", "hazard", "glass", "white", "halo", "key", "label",
        "well", "screen", "metal_dark")}


def part(name, elements, textures):
    tex = {k: TEX[k] for k in textures}
    tex["particle"] = TEX["metal"]
    write_json(f"{ROOT}/models/item/remote/{name}.json", {"textures": tex, "elements": elements})


body = [
    # the shell, its edges a lighter navy, with a slimmer box over the corners to round them
    el((5.5, 0.6, 6.7), (10.5, 15.4, 9.3), "metal"),
    el((5.25, 0.85, 6.95), (10.75, 15.15, 9.05), "edge"),
    el((5.75, 15.4, 6.95), (10.25, 15.65, 9.05), "edge"),
    el((5.75, 0.35, 6.95), (10.25, 0.6, 9.05), "edge"),
    el((5.8, 0.9, 9.3), (10.2, 15.1, 9.42), "panel"),
    # the big screen
    el((6.0, 11.3, 9.42), (10.0, 14.9, 9.58), "bezel"),
    el((6.25, 11.55, 9.58), (9.75, 14.65, 9.6), "screen"),
    # the grille under it
    el((6.45, 10.25, 9.42), (9.55, 11.1, 9.44), "label"),
    # the box's dark well on the right, and the plate for the skill's code under it
    el((7.75, 5.95, 9.42), (10.85, 9.95, 9.47), "well"),
    el((6.1, 4.85, 9.42), (9.9, 5.65, 9.5), "label"),
    # keypad
    el((6.0, 1.15, 9.42), (10.0, 4.65, 9.47), "bezel"),
    # antenna: a rubber foot, a black lower half, a silver upper half and a black tip
    el((6.0, 15.5, 7.35), (7.4, 16.6, 8.65), "rubber"),
    el((6.3, 16.6, 7.65), (7.1, 19.8, 8.35), "metal_dark"),
    el((6.45, 19.8, 7.8), (6.95, 23.6, 8.2), "trim"),
    el((6.3, 23.6, 7.65), (7.1, 24.2, 8.35), "metal_dark"),
    # battery door on the back
    el((6.0, 1.5, 6.55), (10.0, 9.5, 6.7), "rubber"),
]
for k in range(6):
    x0 = 6.75 + k * 0.45
    body.append(el((x0, 10.4, 9.44), (x0 + 0.2, 10.95, 9.45), "well"))
for k in range(7):
    y0 = 6.1 + k * 0.55
    body.append(el((6.05, y0, 9.42), (7.45, y0 + 0.25, 9.48), "rubber"))
for row, y0 in enumerate((2.95, 1.3)):
    for col in range(3):
        x0 = 6.25 + col * 1.2
        body.append(el((x0, y0, 9.47), (x0 + 1.05, y0 + 1.5, 9.69), "key"))
for k in range(11):
    y = 1.4 + k * 0.85
    body.append(el((5.05, y, 7.3), (5.25, y + 0.45, 8.7), "rubber"))
part("body", body, ("metal", "edge", "rubber", "panel", "bezel", "trim", "label", "well", "screen", "key",
                    "metal_dark"))

# glowing bits, drawn additively: the red trims (1, breathing), status LED (2), antenna tip (3, blinking)
part("glow", [
    el((5.82, 1.0, 9.42), (5.97, 11.1, 9.47), "white", tint=1, shade_=False),
    el((6.0, 0.95, 9.42), (10.0, 1.08, 9.46), "white", tint=1, shade_=False),
    el((10.03, 1.0, 9.42), (10.18, 5.8, 9.47), "white", tint=1, shade_=False),
    el((8.8, 15.65, 7.7), (9.5, 15.75, 8.3), "white", tint=2, shade_=False),
    el((6.45, 24.2, 7.8), (6.95, 24.5, 8.2), "white", tint=3, shade_=False),
], ("white",))

part("button", [
    el((8.25, 6.65, 9.47), (10.35, 9.25, 9.95), "white", tint=0),
    el((8.5, 6.9, 9.95), (10.1, 9.0, 10.15), "white", tint=0),
    el((8.7, 8.5, 10.15), (9.2, 8.8, 10.16), "white", shade_=False),
], ("white",))
part("button_halo", [
    el((7.85, 6.2, 10.17), (10.75, 9.7, 10.175), "halo", tint=0, shade_=False,
       skip=("east", "west", "up", "down")),
], ("halo",))

# the box over the button, modelled closed; it turns about its hinge along the top of the well
part("cover_frame", [
    el((7.75, 5.95, 9.47), (8.05, 9.95, 10.85), "hazard"),
    el((10.55, 5.95, 9.47), (10.85, 9.95, 10.85), "hazard"),
    el((8.05, 5.95, 9.47), (10.55, 6.25, 10.85), "hazard"),
    el((8.05, 9.65, 9.47), (10.55, 9.95, 10.85), "hazard"),
    el((8.9, 5.55, 10.4), (9.7, 5.95, 10.8), "trim"),
], ("hazard", "trim"))
part("cover_glass", [
    el((8.05, 6.25, 10.6), (10.55, 9.65, 10.78), "glass"),
], ("glass",))

write_json(f"{ROOT}/models/item/stellar_remote.json", {
    "parent": "builtin/entity",
    "gui_light": "side",
    "textures": {"particle": TEX["metal"]},
    "display": {
        "gui": {"rotation": [12, -28, 0], "translation": [0, -2.6, 0], "scale": [0.62, 0.62, 0.62]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, -2.5, 0], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 6, 7], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, 1.5, 3.0], "scale": [0.42, 0.42, 0.42]},
        "firstperson_righthand": {"rotation": [-8, -18, 6], "translation": [-1.2, 3.6, 0.0], "scale": [0.6, 0.6, 0.6]},
    },
})

# ================================================================ Gungnir's needle
# Two by two blocks, each a quarter of a round column, black with glowing ember bands and a silver foot.
B = f"{ROOT}/textures/block"
plain = grid()
for y in range(16):
    for x in range(16):
        base = (13, 13, 17)
        if x in (3, 4, 11):
            base = (34, 34, 44)
        plain[y][x] = add(base, int((hsh(x, y, 7) - 0.5) * 4))
png(f"{B}/needle_plain.png", plain)
band = [row[:] for row in plain]
for y in range(5, 11):
    for x in range(16):
        band[y][x] = (190, 70, 12) if y in (5, 10) else (255, 140, 36) if y in (6, 9) else (255, 206, 110)
png(f"{B}/needle_band.png", band)
foot = grid()
for y in range(16):
    for x in range(16):
        c = (182, 188, 198) if 2 <= y <= 13 else (118, 124, 136)
        if x in (4, 5):
            c = (226, 230, 238)
        foot[y][x] = c
png(f"{B}/needle_base.png", foot)

QUARTER = [(0, 0, 14.9, 4), (0, 4, 13.75, 8), (0, 8, 11.6, 11), (0, 11, 9.0, 13), (0, 13, 5.4, 15)]
for name in ("plain", "band", "base"):
    elements = [el((x0, 0, z0), (x1, 16, z1), "needle", skip=("west", "north"), glow=(name == "band"))
                for x0, z0, x1, z1 in QUARTER]
    write_json(f"{ROOT}/models/block/needle_{name}.json", {
        "parent": "minecraft:block/block",
        "textures": {"needle": f"starfall:block/needle_{name}", "particle": f"starfall:block/needle_{name}"},
        "elements": elements})
variants = {}
for p, name in enumerate(("plain", "band", "base")):
    for q in range(4):
        v = {"model": f"starfall:block/needle_{name}"}
        if q:
            v["y"] = 90 * q
        variants[f"part={p},quarter={q}"] = v
write_json(f"{ROOT}/blockstates/star_needle.json", {"variants": variants})
write_json(f"{ROOT}/models/item/star_needle.json", {"parent": "starfall:block/needle_band"})

# ================================================================ the planed crater floor
# A 4x4-block hex grid of glowing ember lines with nodes, cut into 16 tiles that are laid by position.
S = 64
centers = []
for j in range(2):
    for i in range(2):
        centers.append((i * 32 + (16 if j % 2 else 0), j * 32))


def nearest(px, py):
    best = []
    for cx, cy in centers:
        for ox in (-S, 0, S):
            for oy in (-S, 0, S):
                best.append((math.hypot(px - cx - ox, py - cy - oy), ((cx + ox) % S, (cy + oy) % S, ox, oy)))
    best.sort()
    return best[0], best[1], best[2]


def circumcenter(a, b, c):
    d = 2 * (a[0] * (b[1] - c[1]) + b[0] * (c[1] - a[1]) + c[0] * (a[1] - b[1]))
    if abs(d) < 1e-9:
        return a
    a2, b2, c2 = a[0] ** 2 + a[1] ** 2, b[0] ** 2 + b[1] ** 2, c[0] ** 2 + c[1] ** 2
    return ((a2 * (b[1] - c[1]) + b2 * (c[1] - a[1]) + c2 * (a[1] - b[1])) / d,
            (a2 * (c[0] - b[0]) + b2 * (a[0] - c[0]) + c2 * (b[0] - a[0])) / d)


big = [[None] * S for _ in range(S)]
for py in range(S):
    for px in range(S):
        (d1, c1), (d2, c2), (d3, c3) = nearest(px + 0.5, py + 0.5)
        edge = d2 - d1
        cell = hsh(c1[0], c1[1], 11)
        base = add((26, 23, 22), int((hsh(px, py, 5) - 0.5) * 10))
        if cell > 0.62:
            # some cells still hold heat
            base = (base[0] + 22, base[1] + 9, base[2] + 2)
        c = base
        if edge < 1.25:
            c = (255, 168, 60)
        elif edge < 2.6:
            c = tuple(int(b + (v - b) * 0.4) for b, v in zip(base, (220, 96, 24)))
        # a ring around some of the corners where three cells meet
        pts = [(cc[0] + cc[2], cc[1] + cc[3]) for cc in (c1, c2, c3)]
        vx, vy = circumcenter(*pts)
        dv = math.hypot(px + 0.5 - vx, py + 0.5 - vy)
        if hsh(int(round(vx)) % S, int(round(vy)) % S, 13) > 0.45:
            if 2.6 <= dv < 4.0:
                c = (255, 214, 130)
            elif dv < 2.6:
                c = (40, 20, 10) if dv > 1.0 else (255, 200, 110)
        big[py][px] = c
for tz in range(4):
    for tx in range(4):
        tile = [[big[tz * 16 + y][tx * 16 + x] for x in range(16)] for y in range(16)]
        i = tx + tz * 4
        png(f"{B}/scorched_plate_{i}.png", tile)
        write_json(f"{ROOT}/models/block/scorched_plate_{i}.json", {
            "parent": "minecraft:block/block",
            "textures": {"all": f"starfall:block/scorched_plate_{i}", "particle": f"starfall:block/scorched_plate_{i}"},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "forge_data": {"block_light": 15, "sky_light": 15},
                          "faces": {d: {"texture": "#all", "cullface": d}
                                    for d in ("north", "south", "east", "west", "up", "down")}}]})
write_json(f"{ROOT}/blockstates/scorched_plate.json",
           {"variants": {f"tile={i}": {"model": f"starfall:block/scorched_plate_{i}"} for i in range(16)}})
write_json(f"{ROOT}/models/item/scorched_plate.json", {"parent": "starfall:block/scorched_plate_5"})

# ================================================================ the star-core crystal
# A cluster of violet-white shards (drawn on a cross model) with a white-hot heart.
g = grid()
V_D, V, V_L, V_W = (70, 30, 130), (150, 90, 240), (200, 160, 255), (250, 240, 255)
SHARDS = [(7, 1, 15, 2), (4, 5, 15, 1), (11, 6, 15, 1), (2, 10, 15, 1), (13, 10, 15, 1)]
for cx, top, bottom, half in SHARDS:
    for y in range(top, bottom + 1):
        w = half if y > top + 1 else 0
        for x in range(cx - w - 1, cx + w + 2):
            if not 0 <= x < 16:
                continue
            edge = x in (cx - w - 1, cx + w + 1)
            g[y][x] = V_D if edge else (V_L if x <= cx else V)
    put(g, cx, top, V_W)
for y in range(11, 16):
    for x in range(6, 10):
        g[y][x] = V_W if 7 <= x <= 8 and y >= 12 else V_L
png(f"{B}/star_core.png", g)
write_json(f"{ROOT}/models/block/star_core.json", {
    "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
    "textures": {"cross": "starfall:block/star_core"}})
write_json(f"{ROOT}/blockstates/star_core.json", {"variants": {"": {"model": "starfall:block/star_core"}}})
write_json(f"{ROOT}/models/item/star_core.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "starfall:block/star_core"}})

# ================================================================ the molten wall of SS-01's shaft
# Rock melted by the beam: streaked downwards like it ran, white-hot at heat 7, a dark crust with glowing
# cracks at heat 0.
MOLTEN = [((40, 22, 18), (110, 40, 20), (24, 16, 14)), ((85, 25, 12), (150, 50, 20), (45, 16, 10)),
          ((140, 40, 15), (200, 80, 30), (80, 22, 10)), ((190, 70, 20), (240, 120, 40), (120, 35, 10)),
          ((230, 110, 30), (255, 170, 70), (170, 60, 15)), ((250, 160, 50), (255, 210, 110), (200, 100, 25)),
          ((255, 200, 90), (255, 236, 160), (230, 140, 40)), ((255, 236, 170), (255, 252, 230), (255, 190, 90))]
for h, (base, hot, dark) in enumerate(MOLTEN):
    g = grid()
    for y in range(16):
        for x in range(16):
            streak = 0.55 + 0.45 * hsh(x, 3, 70) + 0.15 * (hsh(x, y // 3, 71) - 0.5)
            c = tuple(int(b + (v - b) * min(1.0, max(0.0, streak - 0.35))) for b, v in zip(base, hot))
            crust = hsh(x // 2 + (y // 3) * 7, h, 72)
            if crust > 0.55 + h * 0.05:
                c = dark
            if h == 0 and hsh(x, y, 73) > 0.9:
                c = (150, 55, 20)
            g[y][x] = c
    png(f"{B}/molten_rock_{h}.png", g)
    write_json(f"{ROOT}/models/block/molten_rock_{h}.json", {"parent": "minecraft:block/cube_all",
                                                             "textures": {"all": f"starfall:block/molten_rock_{h}"}})
write_json(f"{ROOT}/blockstates/molten_rock.json",
           {"variants": {f"heat={h}": {"model": f"starfall:block/molten_rock_{h}"} for h in range(8)}})
print("ok")
