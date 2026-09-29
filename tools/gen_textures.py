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

# a votive candle of bone-white wax on an iron dish, burning with soul fire
art("vigil_candle", {"K": "120c0a", "W": "efe6d2", "w": "c9bea4", "d": "8f846c", "F": "7fe8ff", "f": "2fb4d6",
                     "c": "e8ffff", "I": "3c3c44", "i": "6a6a74", "R": "8c0000"}, [
    "................",
    ".......f........",
    "......fFf.......",
    "......FcF.......",
    "......fFf.......",
    ".......K........",
    "......KWK.......",
    ".....KWWwK......",
    ".....KWWwK......",
    ".....KWRwK......",
    ".....KWWwK......",
    ".....KWwdK......",
    "....KKWwdKK.....",
    "..KiiiiiiiiiK...",
    "...KIIIIIIIK....",
    "....KKKKKKK.....",
])

# a soul caught in an iron anchor, glowing blue
art("soul_anchor", {"K": "0a0a10", "I": "4a4a58", "i": "8a8a9a", "S": "2fb4d6", "s": "7fe8ff", "c": "e8ffff"}, [
    "......KKKK......",
    ".....KiIIiK.....",
    ".....KI..IK.....",
    ".....KiIIiK.....",
    "..KKKKKiIKKKKK..",
    "..KiiiiiIiiiiK..",
    "..KKKKKiIKKKKK..",
    "......KiIK......",
    "....KKKsSKKK....",
    "...KsSSccSSsK...",
    "...KSscssssSK...",
    ".K..KSsssSSK..K.",
    "KiK..KSSSSK..KiK",
    "KiiK..KiIK..KiiK",
    ".KiiKKiiIIKKiiK.",
    "..KKKKKKKKKKKK..",
])

# a halo of light
art("halo", {"K": "6a4a10", "G": "ffd84a", "g": "fff4b0", "W": "ffffff"}, [
    "................",
    "................",
    "................",
    "................",
    ".....KKKKKK.....",
    "...KKGGGGGGKK...",
    "..KGGggWWggGGK..",
    ".KGgK......KgGK.",
    ".KGgK......KgGK.",
    "..KGGggWWggGGK..",
    "...KKGGGGGGKK...",
    ".....KKKKKK.....",
    "................",
    "................",
    "................",
    "................",
])

# the wings of a seraph
art("seraph_wings", {"K": "5a6070", "W": "ffffff", "w": "dce4f0", "G": "ffe070"}, [
    "................",
    ".KK..........KK.",
    "KWwK........KwWK",
    "KWWwK......KwWWK",
    "KWWWwK....KwWWWK",
    ".KWWWwKGGKwWWWK.",
    ".KWWWWwGGwWWWWK.",
    "..KWWWWKKWWWWK..",
    "..KwWWWK.KWWWK..",
    "...KwWWK..KWWK..",
    "...KwWK...KwWK..",
    "....KwK....KwK..",
    "....KK......KK..",
    "................",
    "................",
    "................",
])

# Beatrice's rose, pink and gold
art("beatrices_rose", {"K": "3a0a1a", "P": "ff7ab0", "p": "ffc0d8", "G": "ffe070", "S": "3a7a2a", "s": "5aa040"}, [
    "................",
    "......KKKK......",
    ".....KPpPPK.....",
    "....KPpGGpPK....",
    "....KPGppGPK....",
    "....KPpGGpPK....",
    ".....KPPPPK.....",
    "......KKKK......",
    ".......SK.......",
    "....KK.SK.......",
    "...KsSKSK.......",
    "....KKSSK.......",
    ".......SK.......",
    ".......SK.......",
    ".......SK.......",
    "........K.......",
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

ARMOUR = {"K": "1a0505", "D": "4a0808", "R": "8a1010", "r": "c02020", "B": "e2d8c4", "b": "a8997c", "E": "ff4a3a"}

art("blood_helmet", ARMOUR, [
    "................",
    "................",
    "....KKKKKKKK....",
    "...KBbRRRRbBK...",
    "..KBRRrrrrRRBK..",
    "..KRRrRRRRrRRK..",
    "..KRRRRRRRRRRK..",
    "..KRKKKKKKKKRK..",
    "..KRKE.KK.EKRK..",
    "..KRK......KRK..",
    "..KBK......KBK..",
    "...K........K...",
    "................",
    "................",
    "................",
    "................",
])

art("blood_chestplate", ARMOUR, [
    "................",
    "..KKKK....KKKK..",
    ".KBbRKK..KKRbBK.",
    ".KRRRRKKKKRRRRK.",
    ".KRrRRRBBRRRrRK.",
    "..KKRRBRRBRRKK..",
    "....KRRBBRRK....",
    "....KRrRRrRK....",
    "....KRRBBRRK....",
    "....KRBRRBRK....",
    "....KRRBBRRK....",
    "....KRrRRrRK....",
    "....KDRRRRDK....",
    "....KKKKKKKK....",
    "................",
    "................",
])

art("blood_leggings", ARMOUR, [
    "................",
    "................",
    "....KKKKKKKK....",
    "....KBbBBbBK....",
    "....KRRRRRRK....",
    "....KRrKKrRK....",
    "....KRRKKRRK....",
    "....KRrKKrRK....",
    "....KRRKKRRK....",
    "....KBRKKRBK....",
    "....KRRKKRRK....",
    "....KRrKKrRK....",
    "....KDRKKRDK....",
    "....KKKKKKKK....",
    "................",
    "................",
])

art("blood_boots", ARMOUR, [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "...KKKK..KKKK...",
    "...KBbK..KbBK...",
    "...KRRK..KRRK...",
    "...KRrK..KrRK...",
    "..KRRRK..KRRRK..",
    ".KRRrRK..KRrRRK.",
    ".KDRRRK..KRRRDK.",
    ".KKKKKK..KKKKKK.",
    "................",
    "................",
])


def armour_layer(path, regions, face=None):
    """A 64x32 humanoid armour layer: crimson plates with dark seams and bone rivets."""
    base, seam, bone = hexc("6b0d0d"), hexc("2a0404"), hexc("e2d8c4")
    pixels = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
    for x0, y0, x1, y1 in regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (y - y0) % 6 == 5:
                    c = seam
                elif (x - x0) % 8 == 3 and (y - y0) % 6 == 2:
                    c = bone
                else:
                    c = shade(base, 1.0 + (rng.random() - 0.5) * 0.35)
                pixels[y][x] = c
    if face:
        fx0, fy0 = face
        for x in range(fx0, fx0 + 8):
            pixels[fy0][x] = bone          # a bone brow band
        for x in (fx0 + 1, fx0 + 2, fx0 + 5, fx0 + 6):
            pixels[fy0 + 4][x] = hexc("ff4a3a")  # burning eye slits
        for x in (fx0 + 3, fx0 + 4):
            pixels[fy0 + 4][x] = hexc("0a0000")
    png(os.path.join(OUT, "entity", "equipment", path, "blood.png"), pixels)


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

# the blood armour as worn (layer 1: helmet, chestplate and boots; layer 2: leggings)
armour_layer("humanoid", [(0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32)], face=(8, 8))
armour_layer("humanoid_leggings", [(16, 16, 40, 32), (0, 16, 16, 32)])

# ---------------------------------------------------------------------- the circle guardians


def pattern_cell(base, colours):
    """Geryon's painted hide: knots and little circles (Inferno XVII)."""
    cell = noise_cell(base, 0.12)
    for (cx, cy), col in zip(((1, 1), (5, 2), (2, 5), (6, 6)), colours):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            cell[cy + dy][cx + dx] = col
        cell[cy][cx] = shade(col, 1.4)
    return cell


def fanged_face(skin, eye):
    """A beast's face: burning eyes and a row of fangs."""
    cell = face_cell(skin, eye)
    for x in range(1, 7):
        cell[6][x] = hexc("e8e0d0") if x % 2 else hexc("300000")
    cell[7][2] = cell[7][5] = hexc("e8e0d0")
    return cell


atlas("guardian_minos", [
    noise_cell(hexc("6b7a5a")),                                  # 0 skin
    noise_cell(hexc("3e4a34")),                                  # 1 skin in shadow
    stripes_cell(hexc("2a1238"), hexc("4a2266")),                # 2 judge's robe
    gradient_cell(hexc("ffe27a"), hexc("a0741c")),               # 3 gold trim and crown
    stripes_cell(hexc("1f4a1c"), hexc("3f7a34")),                # 4 tail scales
    noise_cell(hexc("c8c090"), 0.1),                             # 5 tail belly
    face_cell(hexc("6b7a5a"), hexc("ffe040"), hexc("200000")),   # 6 face: the judge
    noise_cell(hexc("9a9a9a"), 0.2),                             # 7 beard
    gradient_cell(hexc("e8dcc0"), hexc("6b5d45")),               # 8 horn
    None, None, None, None, None, None, None,
])

atlas("guardian_cerberus", [
    noise_cell(hexc("1c1410"), 0.25),                            # 0 fur
    noise_cell(hexc("0c0806"), 0.2),                             # 1 dark fur
    fanged_face(hexc("1c1410"), hexc("ff2010")),                 # 2 face
    noise_cell(hexc("8a0a0a"), 0.2),                             # 3 maw
    noise_cell(hexc("5a5a64"), 0.15),                            # 4 iron collar
    noise_cell(hexc("2a2020"), 0.1),                             # 5 claws
    noise_cell(hexc("4a5a1a"), 0.3),                             # 6 filth
    gradient_cell(hexc("e8e0d0"), hexc("a09880")),               # 7 fangs
    None, None, None, None, None, None, None, None,
])

atlas("guardian_plutus", [
    noise_cell(hexc("5a4a3a"), 0.2),                             # 0 wolf fur
    noise_cell(hexc("342a20"), 0.2),                             # 1 dark fur
    fanged_face(hexc("5a4a3a"), hexc("ffd23a")),                 # 2 face
    gradient_cell(hexc("ffe27a"), hexc("b8861c")),               # 3 gold
    noise_cell(hexc("8a6a1a"), 0.3),                             # 4 tarnished gold
    noise_cell(hexc("9a8a70"), 0.15),                            # 5 bloated belly
    stripes_cell(hexc("4a0814"), hexc("7a1024")),                # 6 velvet
    None, None, None, None, None, None, None, None, None,
])

atlas("guardian_minotaur", [
    noise_cell(hexc("5a3a20"), 0.2),                             # 0 hide
    noise_cell(hexc("342010"), 0.2),                             # 1 dark hide
    face_cell(hexc("342010"), hexc("ff3020"), hexc("100800")),   # 2 bull face
    gradient_cell(hexc("e8dcc0"), hexc("6b5d45")),               # 3 horn
    noise_cell(hexc("2a1f14"), 0.1),                             # 4 horn tip, hooves
    stripes_cell(hexc("2a1a10"), hexc("4a3020")),                # 5 leather
    gradient_cell(hexc("ffe27a"), hexc("a0741c")),               # 6 nose ring
    noise_cell(hexc("6a3020"), 0.2),                             # 7 chest
    None, None, None, None, None, None, None, None,
])

atlas("guardian_geryon", [
    face_cell(hexc("e0b090"), hexc("3a6aa0"), hexc("8a4a40")),   # 0 the face of a just man
    noise_cell(hexc("5a3a1a"), 0.2),                             # 1 hair
    pattern_cell(hexc("2a5a4a"), [hexc("c83a2a"), hexc("e8c040"), hexc("40a0c0"), hexc("e8e0d0")]),  # 2 painted hide
    noise_cell(hexc("c8b890"), 0.1),                             # 3 belly
    noise_cell(hexc("6a4a2a"), 0.3),                             # 4 hairy paws
    noise_cell(hexc("1a1010"), 0.1),                             # 5 claws
    stripes_cell(hexc("1a0f1a"), hexc("3a2a3a")),                # 6 carapace
    gradient_cell(hexc("c02020"), hexc("200000")),               # 7 stinger
    None, None, None, None, None, None, None, None,
])

atlas("guardian_vulcan", [
    noise_cell(hexc("1c1512"), 0.15),                            # 0 soot-black skin
    stripes_cell(hexc("1c1512"), hexc("ff6a1a")),                # 1 ember-veined arms
    face_cell(hexc("241a16"), hexc("ffb020"), hexc("100804")),   # 2 face, eyes of fire
    stripes_cell(hexc("3a2412"), hexc("5a3a1e")),                # 3 leather apron
    gradient_cell(hexc("ffd040"), hexc("c02010")),               # 4 beard of sparks
    noise_cell(hexc("3a3a40"), 0.2),                             # 5 iron
    noise_cell(hexc("4a2a14"), 0.2),                             # 6 hammer handle
    gradient_cell(hexc("fff0a0"), hexc("ff4000")),               # 7 the forge-heart
    None, None, None, None, None, None, None, None,
])

# ------------------------------------------------------------------------------------ the Blood Heart HUD
# 9x9 sprites over the vanilla health bar (assets/minecraft/.../hud/heart). "O" is the container's rim, "i" its
# hollow; the fill sprites paint the inside and a drop of blood that has run off the point.

HEART = [
    ".OO...OO.",
    "OiiO.OiiO",
    "OiiiOiiiO",
    "OiiiiiiiO",
    ".OiiiiiO.",
    "..OiiiO..",
    "...OiO...",
    "....O....",
    ".........",
]
# light from the upper left, dark blood pooling low and right
FILL_SHADE = [
    ".........",
    ".hh...ll.",
    ".hll.llm.",
    ".lllmmmd.",
    "..lmmmd..",
    "...mdd...",
    "....d....",
    ".........",
    "....q....",   # the drop that has run off the point
]
HUD = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "assets", "minecraft",
                   "textures", "gui", "sprites", "hud", "heart")
RANKS = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "hellcraft_ranks")


def heart_sprite(path, colours, half=False, rim=None, crown=None):
    """colours: shade letter -> hex for the fill, or None for a container (rim/hollow from `rim`)."""
    pixels = []
    for y in range(9):
        row = []
        for x in range(9):
            c = (0, 0, 0, 0)
            if rim is not None:
                cell = HEART[y][x]
                if cell == "O":
                    c = hexc(rim[0])
                elif cell == "i":
                    c = hexc(rim[1])
                if crown and y == 0 and x == 4:
                    c = hexc(crown)
            else:
                cell = FILL_SHADE[y][x]
                if cell != "." and not (half and x > 4):
                    c = hexc(colours[cell])
            row.append(c)
        pixels.append(row)
    png(path, pixels)


BLEED = {"h": "ff5a5a", "l": "c8141e", "m": "9a0a14", "d": "5c040c", "q": "7a0610"}
BLEED_FLASH = {"h": "ffd0d0", "l": "ff7a7a", "m": "e04a4a", "d": "a02a2a", "q": "c04040"}
DAMNED = {"h": "ff8a2a", "l": "7a0a0a", "m": "4a0406", "d": "240204", "q": "3a0204"}          # hardcore: ember-lit black blood
DAMNED_FLASH = {"h": "ffd09a", "l": "c04a3a", "m": "8a2a2a", "d": "5a1a1a", "q": "6a2020"}
for name, shade in (("full", BLEED), ("full_blinking", BLEED_FLASH), ("hardcore_full", DAMNED), ("hardcore_full_blinking", DAMNED_FLASH)):
    heart_sprite(os.path.join(HUD, name + ".png"), shade)
    heart_sprite(os.path.join(HUD, name.replace("full", "half") + ".png"), shade, half=True)
heart_sprite(os.path.join(HUD, "container.png"), None, rim=("1a0306", "2e0a0e"))
heart_sprite(os.path.join(HUD, "container_blinking.png"), None, rim=("e8c8c8", "2e0a0e"))
heart_sprite(os.path.join(HUD, "container_hardcore.png"), None, rim=("0c0102", "1e0406"), crown="d8d0c0")
heart_sprite(os.path.join(HUD, "container_hardcore_blinking.png"), None, rim=("e8c8c8", "1e0406"), crown="ffffff")

# The Seven P's: each rank's small pack re-rims the containers, bronze on the first terrace to white on the last
# (the colours match Prestige.Terrace); the purified wear a point of light on top.
RANK_RIMS = ["8c5a2b", "a8743a", "c0913f", "d4af37", "e8c75a", "f4e08a", "ffffff"]
for rank, rim in enumerate(RANK_RIMS, start=1):
    crown = "fffbe0" if rank == 7 else None
    base = os.path.join(RANKS, str(rank), "assets", "minecraft", "textures", "gui", "sprites", "hud", "heart")
    heart_sprite(os.path.join(base, "container.png"), None, rim=(rim, "2e0a0e"), crown=crown)
    heart_sprite(os.path.join(base, "container_blinking.png"), None, rim=("ffffff", "2e0a0e"), crown=crown)
    heart_sprite(os.path.join(base, "container_hardcore.png"), None, rim=(rim, "1e0406"), crown=crown or "d8d0c0")
    heart_sprite(os.path.join(base, "container_hardcore_blinking.png"), None, rim=("ffffff", "1e0406"), crown=crown or "ffffff")

if __name__ == "__main__":
    print("Wrote textures to", os.path.normpath(OUT))
