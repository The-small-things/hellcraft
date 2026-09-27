#!/usr/bin/env python3
"""Generates Hellcraft's textures (Minecraft 26.3 resource pack) from the pixel art below.

Run from the repo root:  python3 tools/gen_textures.py

Item icons are 16x16 character grids ('.' is transparent). Lucifer's two forms use 32x32 "swatch"
atlases: a 4x4 grid of 8x8 cells, one material each, which tools/gen_models.py maps onto the cuboids
of his models. No image library is needed; PNGs are written directly.
"""
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "assets", "hellcraft",
                   "textures")


def png(path, pixels):
    """pixels: rows of (r, g, b, a) tuples."""
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def hexc(s, a=255):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def art(name, palette, rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    pixels = [[(0, 0, 0, 0) if c == "." else hexc(palette[c]) for c in row] for row in rows]
    png(os.path.join(OUT, "item", name + ".png"), pixels)


# ------------------------------------------------------------------------------------ item icons

BLOOD = {"K": "1a0505", "D": "5c0707", "R": "9e1010", "r": "c81d1d", "P": "ff6b6b", "V": "4a0a2a"}

art("blood_heart", BLOOD, [
    "................",
    ".....KK..KK.....",
    "....KDDK.KDK....",
    "....KDDKKDDK....",
    "...KKrDKKDrKK...",
    "..KrrrrDDrrrrK..",
    ".KrrPPrrrrrrrRK.",
    ".KrPPrrrVrrrrRK.",
    ".KrrrrrrVrrrRRK.",
    ".KrrrrrVrrrrRDK.",
    "..KrrrrVrrrRDK..",
    "..KRrrrrVrRDDK..",
    "...KRRrrrRDDK...",
    "....KRRRRDDK....",
    ".....KKRDDK.....",
    ".......KKK......",
])

art("blood_fragment", BLOOD, [
    "................",
    "......K.........",
    ".....KrK........",
    ".....KrK........",
    "....KrPrK.......",
    "....KrrRK...K...",
    "....KrRDK..KrK..",
    ".....KDK...KrK..",
    "......K...KrPrK.",
    "..K.......KrrRK.",
    ".KrK......KrRDK.",
    "KrPrK......KDK..",
    "KrrRK.......K...",
    "KrRDK...........",
    ".KDK............",
    "..K.............",
])

art("lucifers_bane", {"K": "07030c", "S": "2c1838", "s": "4a2a5c", "R": "a01010", "r": "ff4a3a"}, [
    ".......KK.......",
    ".......KK.......",
    "......KsSK......",
    "..K...KsSK...K..",
    "...KK.KsSK.KK...",
    "...KsKSsSSKsK...",
    "....KSSRRSSK....",
    ".KKKsSRrrRSsKKK.",
    ".KKKsSRrrRSsKKK.",
    "....KSSRRSSK....",
    "...KsKSSsSKsK...",
    "...KK.KSsK.KK...",
    "..K...KSsK...K..",
    "......KSsK......",
    ".......KK.......",
    ".......KK.......",
])

STEEL = {"K": "140a0a", "W": "d6d6de", "w": "9a9aa6", "R": "b01515", "r": "e03030", "G": "6b5a2a", "g": "c9a445",
         "H": "4a2615", "h": "7a4a2a", "B": "e2d8c4", "b": "a8997c"}

art("bloodletter", STEEL, [
    "..............KK",
    ".............KWK",
    "............KWrK",
    "...........KWRwK",
    "..........KWRwK.",
    ".........KWRwK..",
    "........KWRwK...",
    ".......KWRwK....",
    "..KK..KWRwK.....",
    "..KgKKWRwK......",
    "...KggRwK.......",
    "....KgGK........",
    "...KhKgGK.......",
    "..KhK.KgK.......",
    ".KHK...KK.......",
    ".KK.............",
])

art("reaper_of_minos", STEEL, [
    ".......KKKKKK...",
    ".....KKBBBBBBKK.",
    "....KBBbbbbbBhK.",
    "...KBbRKKKKKKhK.",
    "...KBRK.....KhK.",
    "..KBRK.....KhK..",
    "..KrK.....KHK...",
    "..KK.....KhK....",
    "........KHK.....",
    ".......KhK......",
    "......KHK.......",
    ".....KhK........",
    "....KHK.........",
    "...KhK..........",
    "..KHK...........",
    "..KK............",
])

art("tithe_axe", STEEL, [
    "......KKKKK.....",
    ".....KWWWWWKK...",
    "....KWwwwwWKhK..",
    "....KWwRRRKhHK..",
    "....KWRRrKhHK...",
    ".....KWRKhHKK...",
    "......KKhHK.....",
    "......KhHK......",
    ".....KhHK.......",
    "....KhHK........",
    "...KhHK.........",
    "..KhHK..........",
    ".KhHK...........",
    "KhHK............",
    "KHK.............",
    "KK..............",
])

# ---------------------------------------------------------------------- Lucifer swatch atlases
# cell index = row * 4 + column; each cell is 8x8 pixels (4x4 model UV units)

rng = random.Random(6660)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def noise_cell(base, spread=0.18):
    return [[shade(base, 1.0 + (rng.random() - 0.5) * spread) for _ in range(8)] for _ in range(8)]


def stripes_cell(dark, light):
    return [[shade(light if (x + y // 2) % 4 == 0 else dark, 1.0 + (rng.random() - 0.5) * 0.1) for x in range(8)] for y in range(8)]


def gradient_cell(top, bottom):
    out = []
    for y in range(8):
        t = y / 7.0
        c = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)) + (255,)
        out.append([shade(c, 1.0 + (rng.random() - 0.5) * 0.08) for _ in range(8)])
    return out


def face_cell(skin, eye, mouth=None, tears=False):
    cell = noise_cell(skin, 0.1)
    for x in (1, 2, 5, 6):
        cell[3][x] = eye
    cell[2][1] = cell[2][6] = shade(skin, 0.5)  # brows
    cell[2][2] = cell[2][5] = shade(skin, 0.5)
    if mouth:
        for x in range(2, 6):
            cell[6][x] = mouth
    if tears:
        for y in (4, 5):
            cell[y][1] = cell[y][6] = hexc("7a0000")
    return cell


def atlas(name, cells):
    pixels = [[(0, 0, 0, 0)] * 32 for _ in range(32)]
    for i, cell in enumerate(cells):
        if cell is None:
            continue
        cx, cy = (i % 4) * 8, (i // 4) * 8
        for y in range(8):
            for x in range(8):
                pixels[cy + y][cx + x] = cell[y][x]
    png(os.path.join(OUT, "entity", name + ".png"), pixels)


SKIN = hexc("7a0c10")
atlas("lucifer_morning_star", [
    noise_cell(SKIN),                                        # 0 skin
    noise_cell(shade(SKIN, 0.6)),                            # 1 skin in shadow
    stripes_cell(hexc("0d0a10"), hexc("2a1d38")),            # 2 black feathers
    stripes_cell(hexc("1a1222"), hexc("4a2d63")),            # 3 feather tips (violet sheen)
    gradient_cell(hexc("e8dcc0"), hexc("6b5d45")),           # 4 horn
    noise_cell(hexc("2a1f14"), 0.1),                         # 5 horn tip
    gradient_cell(hexc("ffe27a"), hexc("b8861c")),           # 6 gold halo
    face_cell(SKIN, hexc("ffd23a"), hexc("2a0000")),         # 7 face: burning eyes
    gradient_cell(hexc("e6e6ee"), hexc("8a8a96")),           # 8 blade
    noise_cell(hexc("b51818"), 0.2),                         # 9 blade edge (blood)
    noise_cell(hexc("241612"), 0.15),                        # 10 hilt / belt
    stripes_cell(hexc("120a0a"), hexc("2b1515")),            # 11 loincloth
    noise_cell(hexc("f0a020"), 0.3),                         # 12 ember
    None, None, None,
])

atlas("lucifer_emperor", [
    face_cell(hexc("8e0e0e"), hexc("ffe060"), hexc("300000"), tears=True),   # 0 red face (hatred)
    face_cell(hexc("c9b56a"), hexc("fff4c0"), hexc("4a3a10"), tears=True),   # 1 pale yellow face (impotence)
    face_cell(hexc("141014"), hexc("ff3020"), hexc("000000"), tears=True),   # 2 black face (ignorance)
    noise_cell(hexc("6a0a0e")),                                              # 3 body skin
    noise_cell(hexc("5a1822"), 0.16),                                        # 4 bat wing membrane
    gradient_cell(hexc("c8b8a0"), hexc("5a4a3a")),                           # 5 wing bone
    gradient_cell(hexc("dff4ff"), hexc("8fc4e8")),                           # 6 ice
    noise_cell(hexc("5f8fb8"), 0.1),                                         # 7 deep ice
    gradient_cell(hexc("e8dcc0"), hexc("6b5d45")),                           # 8 horn
    gradient_cell(hexc("ffe27a"), hexc("8a6012")),                           # 9 tarnished crown
    noise_cell(hexc("300808"), 0.1),                                         # 10 hair / shadow
    noise_cell(hexc("b01010"), 0.25),                                        # 11 blood
    None, None, None, None,
])

if __name__ == "__main__":
    print("Wrote textures to", os.path.normpath(OUT))
