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


def el(frm, to, tex, tint=None, shade_=True, skip=(), uv=None, glow=False):
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        if d in skip:
            continue
        face = {"texture": "#" + tex}
        if tint is not None:
            face["tintindex"] = tint
        if uv is not None:
            face["uv"] = uv
        faces[d] = face
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if not shade_:
        e["shade"] = False
    if glow:
        e["forge_data"] = {"block_light": 15, "sky_light": 15}
    return e


# ================================================================ the remote
# A tall hand-held uplink: antenna, a radar screen, the skill's code, a hazard-striped flip cover over the
# one button, and a keypad. Each moving part is its own model so the item renderer can animate it.
R = f"{ROOT}/textures/item/remote"

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = add((58, 62, 72), int((hsh(x, 0, 1) - 0.5) * 10 + (hsh(x, y, 2) - 0.5) * 4) - (y // 6))
png(f"{R}/metal.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (40, 42, 48) if y % 2 == 0 else (28, 29, 34)
png(f"{R}/rubber.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = add((26, 28, 34), int((hsh(x, y, 3) - 0.5) * 6))
png(f"{R}/panel.png", g)

g = grid((16, 17, 21))
for i in range(16):
    for p in ((i, 0), (i, 15), (0, i), (15, i)):
        put(g, p[0], p[1], (44, 46, 54))
png(f"{R}/bezel.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (206, 212, 222) if y in (0, 1) else (150, 156, 168) if y < 13 else (112, 118, 130)
png(f"{R}/trim.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (238, 192, 32) if ((x + y) // 3) % 2 == 0 else (22, 22, 24)
png(f"{R}/hazard.png", g)

g = grid()
for y in range(16):
    for x in range(16):
        edge = x in (0, 15) or y in (0, 15)
        streak = 3 <= (x + 15 - y) <= 5 or (x + 15 - y) == 9
        g[y][x] = (255, 236, 236, 150) if streak else (255, 190, 190, 120) if edge else (230, 210, 216, 64)
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
        g[y][x] = (232, 234, 240) if y < 2 else (196, 200, 208) if y < 13 else (138, 142, 150)
png(f"{R}/key.png", g)

png(f"{R}/label.png", grid((44, 46, 54)))
png(f"{R}/well.png", grid((6, 6, 8)))
g = grid()
for y in range(16):
    for x in range(16):
        g[y][x] = (12, 4, 5) if y % 2 else (18, 6, 7)
png(f"{R}/screen.png", g)
png(f"{R}/metal_dark.png", grid((28, 30, 36)))

TEX = {k: f"starfall:item/remote/{k}" for k in
       ("metal", "rubber", "panel", "bezel", "trim", "hazard", "glass", "white", "halo", "key", "label", "well",
        "screen", "metal_dark")}


def part(name, elements, textures):
    tex = {k: TEX[k] for k in textures}
    tex["particle"] = TEX["metal"]
    write_json(f"{ROOT}/models/item/remote/{name}.json", {"textures": tex, "elements": elements})


body = [
    # shell, rounded with a second, slimmer box over the corners
    el((5.5, 0.6, 6.7), (10.5, 15.4, 9.3), "metal"),
    el((5.25, 0.85, 6.95), (10.75, 15.15, 9.05), "metal"),
    el((5.75, 15.4, 6.95), (10.25, 15.65, 9.05), "metal"),
    el((5.75, 0.35, 6.95), (10.25, 0.6, 9.05), "trim"),
    el((5.8, 0.9, 9.3), (10.2, 15.1, 9.42), "panel"),
    # radar screen and the label under it
    el((6.0, 11.9, 9.42), (10.0, 14.9, 9.6), "bezel"),
    el((6.3, 12.2, 9.6), (9.7, 14.6, 9.62), "screen"),
    el((6.2, 10.85, 9.42), (9.8, 11.65, 9.5), "label"),
    # the button's well and its rim, with the cover's hinge barrel on top
    el((6.5, 6.6, 9.42), (9.5, 10.2, 9.47), "well"),
    el((6.2, 6.3, 9.42), (6.5, 10.5, 9.75), "trim"),
    el((9.5, 6.3, 9.42), (9.8, 10.5, 9.75), "trim"),
    el((6.5, 6.3, 9.42), (9.5, 6.6, 9.75), "trim"),
    el((6.5, 10.2, 9.42), (9.5, 10.5, 9.75), "trim"),
    el((6.4, 10.45, 9.75), (9.6, 10.85, 10.15), "trim"),
    # keypad
    el((6.0, 1.3, 9.42), (10.0, 5.7, 9.5), "bezel"),
    # antenna
    el((6.0, 15.5, 7.35), (7.3, 16.3, 8.65), "rubber"),
    el((6.4, 16.3, 7.75), (6.9, 24.0, 8.25), "metal_dark"),
    el((6.3, 18.6, 7.65), (7.0, 18.9, 8.35), "trim"),
    el((6.3, 21.2, 7.65), (7.0, 21.5, 8.35), "trim"),
    # battery door on the back
    el((6.0, 1.5, 6.55), (10.0, 9.5, 6.7), "rubber"),
]
for row, y0 in enumerate((3.75, 1.65)):
    for col in range(3):
        x0 = 6.25 + col * 1.2
        body.append(el((x0, y0, 9.5), (x0 + 1.05, y0 + 1.6, 9.72), "key"))
for k in range(11):
    y = 1.4 + k * 0.85
    body.append(el((5.05, y, 7.3), (5.25, y + 0.45, 8.7), "rubber"))
    body.append(el((10.75, y, 7.3), (10.95, y + 0.45, 8.7), "rubber"))
part("body", body, ("metal", "rubber", "panel", "bezel", "trim", "label", "well", "screen", "key", "metal_dark"))

# glowing bits, drawn additively: side trims (1, breathing), status LED (2), antenna tip (3, blinking)
part("glow", [
    el((5.82, 1.2, 9.42), (5.97, 10.6, 9.47), "white", tint=1, shade_=False),
    el((10.03, 1.2, 9.42), (10.18, 10.6, 9.47), "white", tint=1, shade_=False),
    el((8.6, 15.65, 7.7), (9.4, 15.75, 8.3), "white", tint=2, shade_=False),
    el((6.3, 24.0, 7.65), (7.0, 24.6, 8.35), "white", tint=3, shade_=False),
], ("white",))

part("button", [
    el((6.85, 7.0, 9.47), (9.15, 9.8, 9.95), "white", tint=0),
    el((7.1, 7.25, 9.95), (8.9, 9.55, 10.15), "white", tint=0),
    el((7.3, 9.0, 10.15), (7.8, 9.3, 10.16), "white", shade_=False),
], ("white",))
part("button_halo", [
    el((6.4, 6.5, 10.17), (9.6, 10.3, 10.175), "halo", tint=0, shade_=False,
       skip=("east", "west", "up", "down")),
], ("halo",))

# the flip cover, modelled closed; it turns about its hinge at the top
part("cover_frame", [
    el((6.15, 6.25, 9.75), (6.55, 10.55, 10.4), "hazard"),
    el((9.45, 6.25, 9.75), (9.85, 10.55, 10.4), "hazard"),
    el((6.55, 6.25, 9.75), (9.45, 6.65, 10.4), "hazard"),
    el((6.55, 10.15, 9.75), (9.45, 10.55, 10.4), "hazard"),
    el((7.4, 5.85, 9.9), (8.6, 6.25, 10.3), "trim"),
], ("hazard", "trim"))
part("cover_glass", [
    el((6.55, 6.65, 10.2), (9.45, 10.15, 10.38), "glass"),
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
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1.5], "scale": [0.45, 0.45, 0.45]},
        "firstperson_righthand": {"rotation": [-10, -20, 8], "translation": [1.0, 1.0, -0.5], "scale": [0.8, 0.8, 0.8]},
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
print("ok")
