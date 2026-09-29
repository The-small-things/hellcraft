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
# Hand-drawn 16x16 icons, lit from the upper left, with dark coloured outlines (vanilla style).

# the Blood Heart: an anatomical heart, the aortic arch and pulmonary trunk above, the vena cava in blue,
# the coronary groove running down to the apex
art("blood_heart", {"o": "2a0508", "1": "5a0a14", "2": "7e1220", "3": "a31c2a", "4": "c62e38", "5": "e0504f", "6": "f39a86",
                    "A": "6a1428", "a": "a8344c", "b": "d0647a", "v": "3e3280", "w": "6a5cb0",
                    "g": "46060f", "f": "d8b25a", "p": "7a2448", "q": "a04068"}, [
    "......oooo......",
    ".ooo.obbbbo.....",
    "ovwvoabaaabo....",
    "ovwvoaboqqpaoo..",
    "ovwvoabqqpppAAo.",
    "ovvvoabqpppoaAo.",
    "oo45o5apppoo3Ao.",
    "o4555a54f433321o",
    "o4566554433g221o",
    "o456655433g2211o",
    "o45655433g22v11o",
    ".o455433g222v1o.",
    ".o44433g222211o.",
    "..o433g22211oo..",
    "...o33g2211o....",
    "....ooooooo.....",
])

# Blood Fragments: clotted blood crystals, one big faceted shard and two small ones
art("blood_fragment", {"o": "2a0508", "1": "4e0810", "2": "7a0f1a", "3": "a8182a", "4": "d0303e", "5": "f06a6a", "6": "ffc4bc"}, [
    "................",
    ".........oo.....",
    "........o54o....",
    ".......o5643o...",
    ".......o5433o...",
    "......o54332o...",
    "......o543321o..",
    ".oo...o4332211o.",
    "o54o..o43321o...",
    "o432o..o321o....",
    ".o21o..o221o.oo.",
    "..oo....o1o.o54o",
    "........oo..o431",
    "............o21o",
    ".............oo.",
    "................",
])

# Lucifer's Bane: a black star-shard of the Morning Star, a coal of hellfire at its heart
art("lucifers_bane", {"o": "07030c", "1": "1e1028", "2": "34203f", "3": "553866", "4": "7e5a94",
                      "R": "8a0a0a", "r": "e02a1a", "y": "ffb040", "w": "fff0c0"}, [
    ".......o........",
    "......o4o.......",
    "......o3o.......",
    "..o...o32o...o..",
    "...o.o3322o.o...",
    "...o4o3RR2o3o...",
    "....o3RrrR2o....",
    "oo4332rywr211oo.",
    ".oo332rrr2211oo.",
    "....o2RrR21o....",
    "...o3o21R21o1o..",
    "...o.o2211o.o...",
    "..o...o21o...o..",
    "......o21o......",
    "......o1o.......",
    ".......o........",
])

# the Vigil Candle: bone-white wax, dripping blood, on an iron dish, burning with soul fire
art("vigil_candle", {"o": "140c0a", "c": "e8ffff", "F": "8ff0ff", "f": "3cc4e4", "b": "1a6a8a", "W": "f6efe0", "w": "d8ccb0",
                     "d": "a8987a", "k": "2a2420", "I": "3c3c48", "i": "70707e", "j": "a0a0ae", "R": "8c0000"}, [
    ".......o........",
    "......obo.......",
    "......ofo.......",
    ".....ofFfo......",
    ".....ofcFo......",
    "......ofo.......",
    "......oko.......",
    ".....oWWwo......",
    ".....oWwwdo.....",
    ".....oWRwdo.....",
    "....oWWwwdo.....",
    ".....oWwwdo.....",
    "...ooWwwddoo....",
    "..ojjiiiiiiIo...",
    "...oIIIIIIIo....",
    "....ooooooo.....",
])

# the Soul Anchor: a soul bound in an iron anchor, glowing blue
art("soul_anchor", {"o": "0a0a12", "I": "3e3e4c", "i": "6a6a7a", "j": "a4a4b4", "S": "1a7aa0", "s": "3cc4e4", "c": "8ff0ff", "w": "e8ffff"}, [
    "......oooo......",
    ".....ojiiIo.....",
    ".....oio.Io.....",
    ".....ojiiIo.....",
    "..oooooiIooooo..",
    "..ojjjjiIiiiIo..",
    "..oooooiIooooo..",
    "......ojIo......",
    ".....oscsso.....",
    "....oscwcsSo....",
    "....osccssSo....",
    ".o...oSssSo...o.",
    "oio...oiIo...oIo",
    "ojio..oiIo..oiIo",
    ".ojiiiijiIiiiIo.",
    "..oooooooooooo..",
])

# the Halo: a ring of light seen from a little above, with a glint either side
art("halo", {"o": "6a4208", "G": "b8841c", "g": "f0c040", "y": "ffe68a", "w": "fffbe8"}, [
    "................",
    "................",
    "...w............",
    "..wyw......w....",
    "...w.oooooo.....",
    "...ooyyyyyyoo...",
    "..oyygggggggGo..",
    ".oygGo....oGgGo.",
    ".oygo......oGGo.",
    ".oygGo....oGgGo.",
    "..oGggggggggGo..",
    "...ooGGGGGGoo...",
    ".....oooooo.w...",
    "...........wyw..",
    "............w...",
    "................",
])

# the Seraph Wings: two white wings, their primaries layered, clasped in gold
_WING = [
    "........",
    "........",
    ".oo.....",
    "oWWoo...",
    "oWWWWoo.",
    "owWWWWWo",
    "owwWWWWg",
    ".owwwWWg",
    ".oswwwWo",
    "..osswso",
    "..owoswo",
    "...owoso",
    "....o.oo",
    "........",
    "........",
    "........",
]
art("seraph_wings", {"o": "5a6078", "W": "ffffff", "w": "dfe6f2", "s": "a8b4cc", "g": "ffd24a"}, [r + r[::-1] for r in _WING])

# Beatrice's Rose: a pink rose, its heart deep crimson, on a thorned stem
art("beatrices_rose", {"o": "3a0a1a", "P": "ff8ab8", "p": "ffc6dc", "R": "d0406e", "r": "9a1f4a", "d": "c05080",
                       "S": "3a7a2a", "L": "5aa040", "l": "8ad060"}, [
    "................",
    ".....oooooo.....",
    "....oPPpPPPo....",
    "...oPpppPPPPo...",
    "...oPpRRpPPdo...",
    "...oPRprRpPdo...",
    "...oPpRRpPddo...",
    "....oPPPPddo....",
    ".....oodddo.....",
    "....oLo.So......",
    "...oLlLoSo......",
    "....ooLSSo......",
    ".......So.......",
    ".......oSo......",
    "........So......",
    "........o.......",
])

STEEL = {"o": "1a0e10", "W": "f4f4f8", "w": "c4c6d0", "s": "7c7e8e", "R": "a01818", "r": "e03a3a",
         "G": "7a5a1c", "g": "d4a93c", "y": "f5d77a", "H": "3a1e10", "h": "7a4424", "i": "8a8c9a",
         "B": "ece2cc", "b": "bdae8e", "d": "857458"}

# the Bloodletter: a steel blade with a blood-filled fuller, a gold guard and a ruby pommel
art("bloodletter", STEEL, [
    ".............ooo",
    "............oWWo",
    "...........oWrso",
    "..........oWRso.",
    ".........oWRso..",
    "........oWRso...",
    "..oo...oWRso....",
    "..ogo.oWRso.....",
    "...oyoWRso......",
    "...ogyWso.......",
    "....oggo........",
    "...ohogGo.......",
    "..oho..oGo......",
    ".oho....oo......",
    "orro............",
    ".oo.............",
])

# the Reaper of Minos: a bone scythe blade on a dark shaft, bloodied at the tip
art("reaper_of_minos", STEEL, [
    ".....ooooooo....",
    "...ooBBBBBBBoo..",
    "..oBBbbbbbbbbho.",
    ".oBbbddooooohHo.",
    ".oRdoo....ohHo..",
    "ooRo.....ohHo...",
    "oro.....ohHo....",
    ".o.....ohHo.....",
    "......ohHo......",
    ".....ohHo.......",
    "....ohHo........",
    "...ohHo.........",
    "..ohHo..........",
    ".ohHo...........",
    "oiHo............",
    "ooo.............",
])

# the Tithe Axe: a broad bearded head, blood on the edge
art("tithe_axe", STEEL, [
    "...oooooo...ooo.",
    "..oWWwwwso.ohHo.",
    ".oWwwRwwssohHo..",
    ".oWwRRrwsohHo...",
    ".oWRRrwsohHo....",
    ".oWRrwsohHo.....",
    "..oWRsohHo......",
    "..oooohHo.......",
    "....ohHo........",
    "...ohHo.........",
    "..ohHo..........",
    ".ohHo...........",
    "ohHo............",
    "oio.............",
    "oo..............",
    "................",
])

# the Blood Pickaxe: a crimson-steel head bound to its haft with bone
art("blood_pickaxe", {"o": "1a0508", "2": "7a1018", "3": "a81c26", "r": "d0343c", "5": "f07068",
                      "H": "3a1e10", "h": "7a4424", "B": "e2d8c4"}, [
    "................",
    "................",
    "....oooooo......",
    "...o5rrrr3o.....",
    "....oo2233ro....",
    "......oBo23ro...",
    ".....ohBo.o23o..",
    "....ohHo...o3ro.",
    "...ohHo....o23o.",
    "..ohHo......o2o.",
    ".ohHo........o..",
    "ohHo............",
    "oHo.............",
    "oo..............",
    "................",
    "................",
])

ARMOUR = {"o": "1a0505", "1": "4a0808", "2": "7a0e12", "3": "a81a22", "4": "d0343a", "5": "f07068", "B": "e2d8c4", "b": "a8997c"}

art("blood_helmet", ARMOUR, [
    "................",
    "................",
    "................",
    "....oooooooo....",
    "...o54444433o...",
    "..o5443333322o..",
    "..o4333333222o..",
    "..o33oooooo21o..",
    "..oBbo....obBo..",
    "..oBbo....obBo..",
    "...oo......oo...",
    "................",
    "................",
    "................",
    "................",
    "................",
])

# ribs of bone over crimson plate
art("blood_chestplate", ARMOUR, [
    "................",
    "..oooo....oooo..",
    ".oB54oooooo32Bo.",
    ".o5443bBBb3322o.",
    ".o443333333322o.",
    "..oo43333332oo..",
    "....oBBb3BBo....",
    "....o433332o....",
    "....oBBb3BBo....",
    "....o433332o....",
    "....oBbb3bBo....",
    "....o433332o....",
    "....o222211o....",
    "....oooooooo....",
    "................",
    "................",
])

art("blood_leggings", ARMOUR, [
    "................",
    "................",
    "...oooooooooo...",
    "...oBbBBBBbBo...",
    "...o54443332o...",
    "...o443oo322o...",
    "...o443oo321o...",
    "...o433oo321o...",
    "...oBbBooBbBo...",
    "...o433oo321o...",
    "...o433oo221o...",
    "...o332oo221o...",
    "...o322oo211o...",
    "...oooooooooo...",
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
    "..oooo....oooo..",
    "..oBbo....obBo..",
    "..o43o....o32o..",
    "..o43o....o32o..",
    ".o543o....o322o.",
    "o5443o....o3221o",
    "o4332o....o3211o",
    "oooooo....oooooo",
    "................",
    "................",
])


def armour_layer(path, regions, face=None):
    """A 64x32 humanoid armour layer: overlapping crimson plates, each lit along its top edge and shadowed
    beneath, with bone rivets; the helmet's face is a skull visor with burning eye slits."""
    base, seam, bone, bone_d = hexc("7a0e12"), hexc("2a0404"), hexc("e2d8c4"), hexc("8a7c62")
    pixels = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
    for x0, y0, x1, y1 in regions:
        grain = [[1.0 + (rng.random() - 0.5) * 0.22 for _ in range((x1 - x0) // 2 + 1)] for _ in range((y1 - y0) // 2 + 1)]
        for y in range(y0, y1):
            for x in range(x0, x1):
                band = (y - y0) % 5
                f = grain[(y - y0) // 2][(x - x0) // 2] + (rng.random() - 0.5) * 0.08
                if band == 4:
                    c = seam
                elif band == 0:
                    c = shade(base, 1.35 * f)       # the lit lip of the plate
                elif band == 3:
                    c = shade(base, 0.72 * f)       # shadow before the next plate
                else:
                    c = shade(base, f)
                if band == 1 and (x - x0) % 6 == 2:
                    c = bone
                elif band == 2 and (x - x0) % 6 == 2:
                    c = bone_d
                pixels[y][x] = c
    if face:
        fx0, fy0 = face
        for x in range(fx0, fx0 + 8):
            pixels[fy0][x] = bone                                   # the bone brow
            pixels[fy0 + 1][x] = bone_d
        for x in (fx0 + 1, fx0 + 2, fx0 + 5, fx0 + 6):
            pixels[fy0 + 3][x] = hexc("1a0000")                     # sockets
            pixels[fy0 + 4][x] = hexc("ff4a3a") if x in (fx0 + 2, fx0 + 5) else hexc("a01a10")
        for y in range(fy0 + 2, fy0 + 7):
            pixels[y][fx0 + 3] = pixels[y][fx0 + 4] = bone if y < fy0 + 6 else bone_d   # the nasal guard
        for x in range(fx0 + 1, fx0 + 7):
            if x not in (fx0 + 3, fx0 + 4):
                pixels[fy0 + 6][x] = bone if x % 2 else bone_d      # teeth along the jaw
    png(os.path.join(OUT, "entity", "equipment", path, "blood.png"), pixels)


# ---------------------------------------------------------------------- Lucifer swatch atlases
# cell index = row * 4 + column; each cell is 8x8 pixels (4x4 model UV units)

rng = random.Random(6660)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def bevel(cell, light=1.12, dark=0.8):
    """Each cell covers a whole face of a cuboid: a lit top edge and a shadowed bottom edge give it volume."""
    for x in range(8):
        cell[0][x] = shade(cell[0][x], light)
        cell[7][x] = shade(cell[7][x], dark)
    for y in range(1, 7):
        cell[y][7] = shade(cell[y][7], 0.9)
    return cell


def noise_cell(base, spread=0.18):
    """A material with grain: 2x2 clusters of light and shade over a fine noise, not per-pixel static."""
    blobs = [[1.0 + (rng.random() - 0.5) * spread for _ in range(4)] for _ in range(4)]
    cell = [[shade(base, blobs[y // 2][x // 2] + (rng.random() - 0.5) * spread * 0.35) for x in range(8)] for y in range(8)]
    return bevel(cell)


def stripes_cell(dark, light):
    """Layered feathers, scales or fur: offset rows of strands, each lit at its root and dark at its tip."""
    cell = []
    for y in range(8):
        row = []
        for x in range(8):
            k = (x + (y // 2) * 2) % 4
            c = light if k == 0 else dark
            f = 1.15 if (y % 2 == 0 and k == 0) else (0.85 if y % 2 else 1.0)
            row.append(shade(c, f * (1.0 + (rng.random() - 0.5) * 0.08)))
        cell.append(row)
    return bevel(cell, 1.08, 0.82)


def gradient_cell(top, bottom):
    """Horn, bone, gold and ice: a smooth ramp, dithered where one band meets the next."""
    out = []
    for y in range(8):
        row = []
        for x in range(8):
            t = (y + (0.5 if (x + y) % 2 else 0.0)) / 7.5
            t = max(0.0, min(1.0, t))
            c = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)) + (255,)
            row.append(shade(c, 1.0 + (rng.random() - 0.5) * 0.05))
        out.append(row)
    for x in range(8):
        out[0][x] = shade(out[0][x], 1.1)
    return out


def face_cell(skin, eye, mouth=None, tears=False):
    """A face: a heavy brow, sunken sockets with a burning eye and a glint, a shaded nose and a mouth."""
    cell = noise_cell(skin, 0.08)
    brow, socket = shade(skin, 0.55), shade(skin, 0.35)
    for x in range(1, 7):
        cell[2][x] = brow if x not in (3, 4) else shade(skin, 0.75)
    for x in (1, 2, 5, 6):
        cell[3][x] = socket
    cell[3][2] = cell[3][5] = eye                               # the eyes, looking forward
    cell[3][1] = cell[3][6] = shade(eye, 0.6)
    cell[4][3] = cell[4][4] = shade(skin, 1.12)                 # the bridge of the nose, lit
    cell[5][3] = cell[5][4] = shade(skin, 0.7)                  # its shadow
    if mouth:
        for x in range(2, 6):
            cell[6][x] = mouth
        cell[6][1] = cell[6][6] = shade(skin, 0.7)
    if tears:
        for y in (4, 5, 6):
            cell[y][1] = cell[y][6] = hexc("8a0000") if y < 6 else hexc("5a0000")
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
