"""Writes the game tests' structure: an empty room nine blocks square and six high on a stone floor.

    python3 tools/gen_structure.py

(a structure file is gzipped NBT; this writes it with the standard library alone).
"""
import gzip
import os
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "src", "dev", "resources", "data", "boundlessrouters", "structures", "empty.nbt")
DATA_VERSION = 3465  # Minecraft 1.20.1
SIZE = (9, 6, 9)

END, INT, STRING, LIST, COMPOUND = 0, 3, 8, 9, 10


def name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def payload(kind, value):
    if kind == INT:
        return struct.pack(">i", value)
    if kind == STRING:
        return name(value)
    if kind == LIST:
        inner, items = value
        out = struct.pack(">b", inner if items else END) + struct.pack(">i", len(items))
        return out + b"".join(payload(inner, v) for v in items)
    if kind == COMPOUND:
        out = b""
        for key, (k, v) in value.items():
            out += struct.pack(">b", k) + name(key) + payload(k, v)
        return out + struct.pack(">b", END)
    raise ValueError(kind)


def main():
    blocks = []
    for y in range(SIZE[1]):
        for z in range(SIZE[2]):
            for x in range(SIZE[0]):
                blocks.append({"pos": (LIST, (INT, [x, y, z])), "state": (INT, 0 if y == 0 else 1)})
    root = {
        "DataVersion": (INT, DATA_VERSION),
        "size": (LIST, (INT, list(SIZE))),
        "palette": (LIST, (COMPOUND, [{"Name": (STRING, "minecraft:stone")}, {"Name": (STRING, "minecraft:air")}])),
        "blocks": (LIST, (COMPOUND, blocks)),
        "entities": (LIST, (COMPOUND, [])),
    }
    data = struct.pack(">b", COMPOUND) + name("") + payload(COMPOUND, root)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(data)
    print("wrote " + os.path.relpath(OUT, os.path.dirname(HERE)))


if __name__ == "__main__":
    main()
