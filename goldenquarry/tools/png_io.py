import struct, zlib

def read_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos = 8
    idat = b''
    plte = None
    trns = None
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos+4])[0]
        typ = data[pos+4:pos+8]
        body = data[pos+8:pos+8+ln]
        pos += 12 + ln
        if typ == b'IHDR':
            w, h, bd, ct, cm, fm, im = struct.unpack('>IIBBBBB', body)
        elif typ == b'IDAT':
            idat += body
        elif typ == b'PLTE':
            plte = body
        elif typ == b'tRNS':
            trns = body
        elif typ == b'IEND':
            break
    assert bd == 8 and im == 0, (bd, im)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(idat)
    stride = w * ch
    rows = []
    prev = bytearray(stride)
    i = 0
    for y in range(h):
        f = raw[i]; i += 1
        line = bytearray(raw[i:i+stride]); i += stride
        if f == 1:
            for x in range(ch, stride): line[x] = (line[x] + line[x-ch]) & 255
        elif f == 2:
            for x in range(stride): line[x] = (line[x] + prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                a = line[x-ch] if x >= ch else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x-ch] if x >= ch else 0
                b = prev[x]
                c = prev[x-ch] if x >= ch else 0
                p = a + b - c
                pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        rows.append(line)
        prev = line
    px = []
    for line in rows:
        r = []
        for x in range(w):
            if ct == 2:
                r.append((line[3*x], line[3*x+1], line[3*x+2], 255))
            elif ct == 6:
                r.append(tuple(line[4*x:4*x+4]))
            elif ct == 0:
                v = line[x]; r.append((v, v, v, 255))
            elif ct == 4:
                v = line[2*x]; r.append((v, v, v, line[2*x+1]))
            elif ct == 3:
                k = line[x]; r.append((plte[3*k], plte[3*k+1], plte[3*k+2], trns[k] if trns and k < len(trns) else 255))
        px.append(r)
    return w, h, px

def write_png(path, w, h, px):
    raw = b''.join(b'\x00' + bytes(sum((list(p[:3]) + [p[3] if len(p) > 3 else 255] for p in row), [])) for row in px)
    ch = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + ch(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + ch(b'IDAT', zlib.compress(raw, 6)) + ch(b'IEND', b''))

def crop_scale(px, x1, y1, x2, y2, s):
    out = []
    for y in range(y1, y2):
        row = []
        for x in range(x1, x2):
            row += [px[y][x]] * s
        for _ in range(s):
            out.append(list(row))
    return (x2 - x1) * s, (y2 - y1) * s, out
