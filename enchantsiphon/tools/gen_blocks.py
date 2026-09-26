"""Redraws the siphon's trim and body textures with shading (run from the enchantsiphon folder).
Trim faces use the top-left corner of the texture (2-3 rows on the sides, up to 14x14 on top),
so row 0 / column 0 carry the light edge and row 1 the rivets."""
import struct, zlib

OUT = "src/main/resources/assets/enchantsiphon/textures/block"


def png(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def rnd(x, y):
    h = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


L, M, D, DD = (255, 232, 140), (228, 176, 58), (176, 120, 26), (110, 70, 12)
trim = [[M + (255,)] * 16 for _ in range(16)]
for y in range(16):
    for x in range(16):
        if rnd(x, y) % 7 == 0:
            trim[y][x] = (236, 190, 76, 255)
for i in range(16):
    trim[0][i] = L + (255,)
    trim[i][0] = L + (255,)
for x in range(2, 16, 4):
    trim[1][x] = DD + (255,)
    trim[1][x + 1] = D + (255,)
for i in range(1, 16):
    trim[13][i] = D + (255,)
    trim[i][13] = D + (255,)
    trim[9][i] = D + (255,) if i < 10 else trim[9][i]
    trim[i][9] = D + (255,) if i < 10 else trim[i][9]
png(f"{OUT}/siphon_trim.png", trim)

B0, B1, B2 = (34, 24, 48), (46, 32, 64), (24, 16, 34)
body = [[(B0 if rnd(x, y) % 3 else B1) + (255,) for x in range(16)] for y in range(16)]
for x in range(16):
    body[0][x] = (70, 50, 96, 255)
    body[4][x] = B2 + (255,)
# a glowing rune in the middle of the 8x5 body face
for (x, y, c) in ((3, 2, (190, 140, 255)), (4, 2, (190, 140, 255)), (3, 1, (120, 80, 190)), (4, 3, (120, 80, 190))):
    body[y][x] = c + (255,)
png(f"{OUT}/siphon_body.png", body)
