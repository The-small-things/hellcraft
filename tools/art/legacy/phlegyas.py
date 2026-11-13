#!/usr/bin/env python3
"""Phlegyas, the Ferryman of the Styx (Inferno VIII): a king who burned Apollo's temple in his rage, now the boatman
of the fifth circle, a drowned-grey giant wading waist-deep in the marsh with a great oar. Wild hair and beard
plastered with weed, a ragged cloak, chains of the sullen hanging off him, and eyes like the beacon flames that call
him.

Run from the repo root:  python3 tools/art/phlegyas.py && python3 tools/gen_bosses.py

The animations' events are what the server times Phlegyas's fight by (see PhlegyasFight): "flash" opens the window
for a shield block and "hit" is when the oar lands; "sweep" .. "swept" is the oar's pass over the water, "slam" the
wave he raises, "grab" and "throw" his reach for a soul, "roar" his fury at half health.

He is built as a gaunt, heavy-boned giant: a sunken belly under a deep ribcage, a thick neck carrying the head
forward, rower's arms in tapered segments (deltoid, upper arm, elbow, forearm, wrist), a right fist closed round the
oar and an open left hand whose fingers close on the soul he grabs. His legs (thigh, knee, shin, calf, foot) are
under the water but bend as he wades. The cloak hangs in rags of different lengths; a broken chain crosses his chest.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SKIN = material("skin", "skin", base="7e8a80", dark="4e5a52")
CLOAK = material("cloak", "robe", base="2e3a34", fold="1a221e")
RAG = material("rag", "robe", base="26302b", fold="161d19")
HAIR = material("hair", "hair", light="5e6a58", dark="26302a")
WEED = material("weed", "hair", light="4a6a2a", dark="22341a")
WOOD = material("wood", "hair", light="6a5236", dark="3a2a1a")
ROPE = material("rope", "hair", light="7a6a4a", dark="40362a")
IRON = material("iron", "metal", base="4a4a4e", high="7a7a80", low="26262a")
NAIL = material("nail", "flat", base="a8a890")

EYE = hexc("ff8a2a")
EYE_HOT = hexc("ffe0a0")
SOCKET = hexc("141a16")
MOUTH = hexc("1a0e0a")
TOOTH = hexc("d8d0b0")
RUST = hexc("6a3a1e")
VEIN = hexc("3e4e5e")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- helpers

def side_x(a, w, s):
    """A cube from a to a + w on the right becomes -(a + w) to -a on the left."""
    return a if s == 1 else -(a + w)


def seg(bone, name, p0, p1, w, h, mat, extra=0.0, **kw):
    """A cube laid along the line p0 -> p1 (its length on its own Z axis), w across and h thick, turned about its own
    centre. extra lengthens it so that segments overlap at their joints instead of opening gaps."""
    d = [p1[i] - p0[i] for i in range(3)]
    length = math.sqrt(sum(v * v for v in d))
    size = max(1, math.ceil(length + extra - 1e-6))
    c = [round((p0[i] + p1[i]) / 2, 3) for i in range(3)]
    pitch = -math.degrees(math.asin(max(-1.0, min(1.0, d[1] / length))))
    yaw = math.degrees(math.atan2(d[0], d[2]))
    return bone.cube(name, (c[0] - w / 2, c[1] - h / 2, c[2] - size / 2), (w, h, size), mat,
                     rotation=(round(pitch, 3), round(yaw, 3), 0), origin=tuple(c), **kw)


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def chain(bone, name, p0, p1, links):
    """A chain from p0 to p1: links turned alternately flat and edge-on, each with its dark eye."""
    for i in range(links):
        flat = i % 2 == 0
        seg(bone, f"{name}_{i}", lerp(p0, p1, i / links), lerp(p0, p1, (i + 1) / links), 2 if flat else 1,
            1 if flat else 2, IRON, extra=0.8, detail=link_detail)


def mul(face, fx, fy, f):
    """Darkens (f < 1) or lightens one pixel of a face that is already painted."""
    if 0 <= fx < face.w and 0 <= fy < face.h:
        c = face.canvas.get(face.x + fx, face.y + fy)
        if c[3]:
            face.px(fx, fy, shade(c, f))


def ao(bottom=0, top=0, dark=0.7, faces=SIDES):
    """A detail: the side faces shaded along their lowest rows (where the part sinks into shadow) and their highest
    (where it tucks under the part above)."""
    def detail(fs):
        for n in faces:
            f = fs[n]
            for i in range(min(bottom, f.h)):
                for fx in range(f.w):
                    mul(f, fx, f.h - 1 - i, dark + (1 - dark) * i / bottom)
            for i in range(min(top, f.h)):
                for fx in range(f.w):
                    mul(f, fx, i, dark + (1 - dark) * i / top)
    return detail


def smooth(fs):
    """Undoes the rim shading along the top and bottom edges of the sides, so that the stacked segments of a limb
    blend into one another instead of showing seams (the corners and the vertical edges keep their shade)."""
    for n in SIDES:
        f = fs[n]
        if f.w < 3 or f.h < 3:
            continue
        for fx in range(1, f.w - 1):
            mul(f, fx, 0, 1 / 1.1)
            mul(f, fx, f.h - 1, 1 / 0.8)


def both(*details):
    def detail(fs):
        for d in details:
            d(fs)
    return detail


# ----------------------------------------------------------------------------------------------- details

def face_detail(fs):
    """The drowned face: sunken sockets with the beacon-fire eyes deep in them, gaunt cheeks, a scowl."""
    f = fs["front"]
    w = f.w
    dark = shade(SKIN.c["dark"], 0.8)
    for x in range(2, w - 2):  # furrows of the brow, above the ridge
        if (x + 1) % 4:
            f.px(x, 3, dark)
    for cx in (3, w - 5):
        for dx in range(-1, 3):
            f.px(cx + dx, 7, SOCKET)
            f.px(cx + dx, 9, shade(SKIN.c["dark"], 0.55))
        f.px(cx - 1, 8, SOCKET)
        f.px(cx + 2, 8, SOCKET)
        f.glow(cx, 8, EYE)
        f.glow(cx + 1, 8, EYE_HOT)
    for y in range(9, f.h):  # hollow cheeks
        for x in (1, 2, w - 3, w - 2):
            mul(f, x, y, 0.72)
    for i, y in enumerate(range(9, f.h)):
        f.px(4 - i // 2, y, dark)
        f.px(w - 5 + i // 2, y, dark)
    for y in range(f.h):
        mul(f, 0, y, 0.8)
        mul(f, w - 1, y, 0.8)
    # a pale scar across the face where the oar of an angel once struck him
    for i in range(5):
        f.px(w - 4 - i, 4 + i, shade(SKIN.c["base"], 1.25))


def brow_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["base"], 1.12))
        mul(f, x, f.h - 1, 0.65)
    fs["bottom"].fill(lambda x, y: SOCKET)


def nose_detail(fs):
    f = fs["front"]
    for y in range(f.h):
        mul(f, 0, y, 0.8)
    f.px(f.w // 2, 1, shade(SKIN.c["base"], 1.15))  # the break in it


def nostrils(fs):
    f = fs["front"]
    f.px(0, f.h - 1, MOUTH)
    f.px(f.w - 1, f.h - 1, MOUTH)
    fs["bottom"].fill(lambda x, y: MOUTH if x in (0, fs["bottom"].w - 1) else shade(SKIN.c["dark"], 0.8))


def jaw_detail(fs):
    """A snarl: broken teeth along the top of the jaw, the mouth black behind them."""
    f = fs["front"]
    for x in range(1, f.w - 1):
        f.px(x, 0, MOUTH)
    for x in range(2, f.w - 2, 2):
        f.px(x, 0, TOOTH if x % 4 else shade(TOOTH, 0.75))
    for x in range(f.w):
        mul(f, x, 1, 0.8)
    fs["top"].fill(lambda x, y: MOUTH)
    for n in ("left", "right", "bottom"):
        g = fs[n]
        for x in range(g.w):
            for y in range(g.h):
                mul(g, x, y, 0.78)


def upper_teeth(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, f.h - 1, TOOTH if x % 2 == 0 else shade(TOOTH, 0.7))
    fs["bottom"].fill(lambda x, y: MOUTH)


def neck_detail(fs):
    f = fs["front"]
    dark = shade(SKIN.c["dark"], 0.85)
    for y in range(f.h):
        f.px(1 + y // 3, y, dark)
        f.px(f.w - 2 - y // 3, y, dark)
    f.px(f.w // 2, f.h // 2, shade(SKIN.c["base"], 1.15))  # the Adam's apple
    for x in range(f.w):
        mul(f, x, 0, 0.55)
        mul(f, x, 1, 0.75)


def ribs_detail(fs):
    """Gaunt ribs under drowned skin, the breastbone between them, the collarbones above."""
    f = fs["front"]
    mid = f.w // 2
    dark = shade(SKIN.c["dark"], 0.82)
    for y in range(f.h):
        f.px(mid, y, dark)
    for y in range(3, f.h, 2):
        for x in list(range(2, mid - 1)) + list(range(mid + 2, f.w - 2)):
            if (x + y // 2) % 3:
                mul(f, x, y + (abs(x - mid) // 5), 0.8)
    for x in range(2, f.w - 2):
        if abs(x - mid) > 1:
            f.px(x, 0, shade(SKIN.c["base"], 1.12))
            mul(f, x, 1, 0.8)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(2, g.h, 2):
            for x in range(1, g.w - 1):
                if (x + y) % 3:
                    mul(g, x, y, 0.8)


def pec_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.62)
        f.px(x, 0, shade(SKIN.c["base"], 1.08))


def belly_detail(fs):
    """A sunken belly: the lowest ribs arching over a hollow, the line down the middle, the navel."""
    f = fs["front"]
    mid = f.w // 2
    dark = shade(SKIN.c["dark"], 0.85)
    for y in range(f.h):
        f.px(mid, y, dark)
        for x in range(f.w):
            mul(f, x, y, 0.9 if 2 < y < f.h - 1 else 1.0)
    for i in range(5):
        f.px(mid - 2 - i, i, shade(SKIN.c["base"], 1.08))
        f.px(mid + 2 + i, i, shade(SKIN.c["base"], 1.08))
        mul(f, mid - 2 - i, i + 1, 0.75)
        mul(f, mid + 2 + i, i + 1, 0.75)
    f.px(mid, f.h - 3, MOUTH)


def arm_detail(fs):
    """Swollen dark veins down the drowned arms."""
    for n in ("front", "left", "right"):
        f = fs[n]
        x = f.w // 2
        for y in range(1, f.h - 1):
            x = max(1, min(f.w - 2, x + f.rng.choice((-1, 0, 0, 1))))
            f.px(x, y, mix(f.canvas.get(f.x + x, f.y + y)[:3], VEIN, 0.5))


def knuckle_detail(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.72)


def finger_rolls(fs):
    """The fingers closed round the oar: dark creases between them."""
    for n in SIDES:
        f = fs[n]
        for y in range(1, f.h, 2):
            for x in range(f.w):
                mul(f, x, y, 0.72)


def strands(fs):
    """Wet, matted hair: clumped strands, dark partings, a few pale glints of water."""
    for n in SIDES + ("top",):
        f = fs[n]
        tones = [0.8 + 0.3 * f.rng.random() for _ in range(f.w)]
        for x in range(f.w):
            parting = f.rng.random() < 0.25
            for y in range(f.h):
                mul(f, x, y, tones[x] * (0.68 if parting and n != "top" else 1.0))
        for _ in range(max(1, f.w * f.h // 40)):
            f.px(f.rng.randrange(f.w), f.rng.randrange(f.h), hexc("8a9a8a"))


def weed_in(fs):
    """Strands of marsh weed caught in hair or cloth."""
    for n in SIDES:
        f = fs[n]
        for _ in range(max(1, f.w // 4)):
            x = f.rng.randrange(f.w)
            y0 = f.rng.randrange(max(1, f.h // 2))
            for y in range(y0, min(f.h, y0 + f.rng.randint(2, 5))):
                f.px(x, y, mix(WEED.c["light"], WEED.c["dark"], f.rng.random()))


def rag_detail(fs):
    """Rotten cloth: a frayed, uneven hem, holes, and stains of the marsh water."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            cut = f.rng.randint(0, 2)
            for i in range(cut):
                mul(f, x, f.h - 1 - i, 0.5)
        for _ in range(max(1, f.w * f.h // 60)):
            hx, hy = f.rng.randrange(f.w), f.rng.randrange(f.h)
            f.px(hx, hy, hexc("0e1210"))
        for y in range(f.h * 2 // 3, f.h):
            for x in range(f.w):
                if f.rng.random() < 0.35:
                    mul(f, x, y, 0.85)


def link_detail(fs):
    for n in SIDES:
        f = fs[n]
        if f.w >= 2 and f.h >= 3:
            for y in range(1, f.h - 1):
                f.px(f.w // 2 if f.w > 2 else 0, y, hexc("121214"))
        if f.rng.random() < 0.5:
            f.px(f.rng.randrange(f.w), f.rng.randrange(f.h), RUST)


def shackle_detail(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            if f.rng.random() < 0.3:
                f.px(x, f.rng.randrange(f.h), RUST)
        f.px(f.w // 2, f.h // 2, hexc("121214"))  # a rivet


def grain(fs):
    """Wood grain along the oar: long dark lines, a knot or two."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            if x % 2 == 0:
                for y in range(f.h):
                    if f.rng.random() < 0.7:
                        mul(f, x, y, 0.82)
        for _ in range(max(1, f.h // 30)):
            y = f.rng.randrange(f.h)
            f.px(f.w // 2, y, shade(WOOD.c["dark"], 0.6))


def blade_detail(fs):
    """The blade: worn pale at its edge, darker where it has soaked longest."""
    for n in ("left", "right"):
        f = fs[n]
        for y in range(f.h):
            f.px(0, y, shade(WOOD.c["dark"], 0.8))
            f.px(f.w - 1, y, shade(WOOD.c["dark"], 0.8))
            for x in range(f.w):
                if y > f.h * 2 // 3:
                    mul(f, x, y, 0.8)
        cx = f.w // 2 + 1  # a crack up the blade
        for y in range(f.h - 1, f.h - 9, -1):
            f.px(cx, y, shade(WOOD.c["dark"], 0.5))
            cx += f.rng.choice((-1, 0, 0, 1))
    grain(fs)


def strap_detail(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(1, f.w - 1, 4):
            f.px(x, f.h // 2, hexc("121214"))
        for x in range(f.w):
            if f.rng.random() < 0.35:
                f.px(x, f.rng.randrange(f.h), RUST)


def toes(fs):
    f = fs["front"]
    for x in range(2, f.w, 2):
        for y in range(f.h):
            f.px(x, y, shade(SKIN.c["dark"], 0.75))
    for x in range(1, f.w, 2):
        f.px(x, 0, NAIL.c["base"])


# ----------------------------------------------------------------------------------------------- the body

def build():
    m = Model("phlegyas", texture_width=256)

    # he wades: the hips sit at the water line, the legs below it, wrapped in a rotten loincloth and a rope belt
    hips = m.bone("hips", (0, 40, 0))
    hips.cube("hips", (-12, 34, -7.5), (24, 9, 15), CLOAK, detail=ao(bottom=2))
    hips.cube("rope_belt", (-12.5, 41, -8), (25, 2, 16), ROPE, inflate=0.2)
    hips.cube("rope_knot", (-2, 37.5, -9.5), (3, 4, 2), ROPE)
    hips.cube("loin_front", (-5, 25, -9), (6, 14, 2), RAG, detail=rag_detail)
    hips.cube("loin_front_2", (1.5, 28, -8.8), (5, 11, 2), RAG, detail=rag_detail)
    # the cloak's lower rags, hanging behind in strips of different lengths
    for i, (x0, w, length, flare) in enumerate(((-14, 7, 30, -4), (-7.5, 8, 24, -2), (0, 7, 33, -3), (7, 7, 27, -5))):
        name = "cloak_back" if i == 0 else f"cloak_back_{i}"
        hips.cube(name, (x0, 42 - length, 7), (w, length, 2), RAG, rotation=(flare, 0, 0), origin=(0, 42, 8),
                  detail=rag_detail)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mirror = s == -1
        leg = hips.bone(f"{side}_leg", (7 * s, 34, 0))
        leg.cube(f"{side}_thigh", (x(2, 10), 22, -5), (10, 13, 10), SKIN, mirror=mirror, detail=smooth)
        leg.cube(f"{side}_thigh_low", (x(2.5, 9), 17.5, -4.5), (9, 5, 9), SKIN, mirror=mirror, detail=smooth)
        shin = leg.bone(f"{side}_shin", (7 * s, 18, 0))
        shin.cube(f"{side}_knee", (x(5, 4), 15, -5.5), (4, 4, 1), SKIN, mirror=mirror)
        shin.cube(f"{side}_shin", (x(3, 8), 5, -4), (8, 13, 8), SKIN, mirror=mirror, detail=both(smooth, arm_detail))
        shin.cube(f"{side}_calf", (x(4, 6), 8, 3), (6, 8, 2), SKIN, mirror=mirror)
        shin.cube(f"{side}_ankle", (x(4, 6), 3, -3), (6, 3, 6), SKIN, mirror=mirror)
        shin.cube(f"{side}_foot", (x(3, 8), 0, -7), (8, 3, 11), SKIN, mirror=mirror, detail=ao(bottom=1))
        shin.cube(f"{side}_toes", (x(3, 8), 0, -10), (8, 2, 3), SKIN, mirror=mirror, detail=toes)

    # the torso: a deep, gaunt ribcage over a sunken belly, broad at the back; the ragged cloak round his shoulders
    torso = hips.bone("torso", (0, 44, 0))
    torso.cube("belly", (-10, 43, -6.5), (20, 9, 13), SKIN, detail=both(smooth, belly_detail))
    torso.cube("chest", (-13, 51, -7.5), (26, 11, 15), SKIN, detail=both(smooth, ribs_detail))
    torso.cube("lats", (-14, 52, -6), (28, 8, 13), SKIN, detail=both(smooth, ao(bottom=2)))
    for s, side in ((1, "r"), (-1, "l")):
        torso.cube(f"pec_{side}", (side_x(0.5, 10, s), 55, -8.7), (10, 6, 2), SKIN, rotation=(-5, 0, 0),
                   origin=(5 * s, 61, -7.5), mirror=s == -1, detail=pec_detail)
    torso.cube("traps", (-10, 61, -5), (20, 5, 12), SKIN)
    torso.cube("neck", (-4.5, 65, -6), (9, 7, 9), SKIN, detail=both(smooth, neck_detail))
    torso.cube("mantle", (-12, 64, -6), (24, 5, 16), CLOAK, inflate=0.4, detail=rag_detail)
    torso.cube("cloak_upper", (-14, 44, 7), (28, 20, 3), CLOAK, detail=both(ao(top=3), rag_detail))
    torso.cube("cloak_hood", (-9, 66, 6), (18, 6, 5), CLOAK, inflate=0.3, detail=rag_detail)
    # the broken chain of the sullen across his chest, and weed caught on him
    chain(torso, "chain", (-11.5, 64, -9.6), (11, 44.5, -8.2), 9)
    torso.cube("chain_end", (10, 39.5, -8.5), (2, 5, 1), IRON, detail=link_detail)
    seg(torso, "weed_r", (10, 63.5, -8), (12.5, 49, -8.8), 3, 1, WEED, extra=0.5)
    seg(torso, "weed_l", (-8, 62, -9.2), (-9.5, 46, -8), 3, 1, WEED, extra=0.5)
    seg(torso, "weed_chain", (1, 54.5, -10.2), (1.5, 47, -10), 2, 1, WEED, extra=0.5)

    # the head, thrust forward on the thick neck: heavy brow, broken nose, snarling jaw, matted hair and beard
    head = torso.bone("head", (0, 71, -1))
    head.cube("skull", (-7, 74, -9), (14, 12, 13), SKIN, detail=face_detail)
    for s, side in ((1, "r"), (-1, "l")):
        head.cube(f"brow_{side}", (side_x(0.2, 7, s), 80, -10.5), (7, 2, 2), SKIN, rotation=(0, 0, 12 * s),
                  origin=(0.5 * s, 81, -9.5), mirror=s == -1, detail=brow_detail)
        head.cube(f"cheek_{side}", (side_x(3.5, 3, s), 76.5, -10), (3, 2, 2), SKIN, mirror=s == -1,
                  detail=ao(bottom=1))
        head.cube(f"ear_{side}", (side_x(7, 1, s), 76, -3), (1, 4, 3), SKIN, mirror=s == -1)
    head.cube("nose", (-1.5, 76.5, -11), (3, 4, 2), SKIN, rotation=(8, 0, 4), origin=(0, 80.5, -9), detail=nose_detail)
    head.cube("nose_tip", (-2, 75, -11.6), (4, 2, 3), SKIN, detail=nostrils)
    head.cube("lip", (-4.5, 74, -9.8), (9, 1, 1), SKIN, detail=upper_teeth)
    head.cube("hair", (-8, 84, -9), (16, 5, 15), HAIR, inflate=0.3, detail=both(strands, weed_in))
    for s, side in ((1, "r"), (-1, "l")):
        head.cube(f"hair_side_{side}", (side_x(6.5, 3, s), 74, -6), (3, 11, 12), HAIR, mirror=s == -1,
                  detail=both(strands, weed_in))
        seg(head, f"hair_lock_{side}", (7.5 * s, 81, -6), (9.5 * s, 64, -6.5), 3, 3, HAIR, extra=0.5,
            detail=strands)
    head.cube("forelock", (-6, 82.5, -10.5), (5, 3, 2), HAIR, rotation=(0, 0, -15), origin=(-3.5, 85, -9.5),
              detail=strands)
    head.cube("forelock_2", (2, 83, -10.3), (4, 2, 2), HAIR, rotation=(0, 0, 10), origin=(4, 85, -9.5),
              detail=strands)
    head.cube("hair_back", (-8, 70, 3.5), (16, 15, 4), HAIR, detail=both(strands, weed_in))
    seg(head, "hair_fall", (0, 72, 6.5), (0, 58, 11), 14, 3, HAIR, extra=1, detail=both(strands, weed_in))
    seg(head, "hair_weed", (3, 66, 10.5), (4, 54, 12.5), 3, 1, WEED, extra=0.5)

    jaw = head.bone("jaw", (0, 72, -4))
    jaw.cube("jaw", (-6.5, 70, -9.5), (13, 4, 11), SKIN, detail=jaw_detail)
    jaw.cube("chin", (-3.5, 68.5, -10.5), (7, 3, 2), SKIN)
    # the beard, matted with weed, hanging in wet ropes
    beard = jaw.bone("beard", (0, 70, -10))
    beard.cube("beard", (-7.5, 61, -12), (15, 9, 4), HAIR, detail=both(ao(top=1), strands, weed_in))
    beard.cube("beard_mid", (-6, 55, -11.5), (12, 7, 3), HAIR, detail=both(strands, weed_in))
    for i, (bx, by0, by1, bw) in enumerate(((-4.5, 56, 47, 3), (0, 56, 49.5, 3), (4, 56, 50, 2))):
        seg(beard, f"beard_rope_{i}", (bx, by0, -10.5), (bx + 0.6, by1, -10.8), bw, 2, HAIR, extra=0.5, detail=strands)
    seg(beard, "beard_weed", (-1.5, 57, -10.6), (-2, 48, -10.2), 3, 2, WEED, extra=0.5)

    arms = {}
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mirror = s == -1
        arm = torso.bone(f"{side}_arm", (17 * s, 66, 0))
        # the cloak draped over the shoulder, sloping down the arm, its rags hanging over the deltoid
        arm.cube(f"{side}_shoulder", (x(12.5, 12), 62.5, -6.5), (12, 6, 13), CLOAK, inflate=0.4, mirror=mirror,
                 rotation=(0, 0, -14 * s), origin=(14 * s, 66, 0), detail=rag_detail)
        arm.cube(f"{side}_shoulder_rag", (x(17.5, 7), 56, -6), (7, 5, 12), RAG, inflate=0.2, mirror=mirror,
                 rotation=(0, 0, -8 * s), origin=(20 * s, 61, 0), detail=rag_detail)
        arm.cube(f"{side}_deltoid", (x(14, 10), 57, -5), (10, 8, 10), SKIN, mirror=mirror,
                 detail=both(smooth, ao(top=2)))
        arm.cube(f"{side}_upper", (x(14.5, 9), 51, -4.5), (9, 7, 9), SKIN, mirror=mirror,
                 detail=both(smooth, arm_detail))
        arm.cube(f"{side}_bicep", (x(15.5, 6), 51.5, -5.5), (6, 5, 1), SKIN, mirror=mirror, detail=ao(bottom=1))
        arm.cube(f"{side}_upper_low", (x(15, 8), 46, -4), (8, 6, 8), SKIN, mirror=mirror, detail=smooth)
        fore = arm.bone(f"{side}_forearm", (19 * s, 47, 0), rotation=(30, 0, 0))
        fore.cube(f"{side}_elbow", (x(17, 4), 43.5, 3), (4, 4, 2), SKIN, mirror=mirror)
        fore.cube(f"{side}_fore", (x(15, 8), 39, -4), (8, 8, 8), SKIN, mirror=mirror, detail=both(smooth, arm_detail))
        fore.cube(f"{side}_fore_low", (x(15.5, 7), 30, -3.5), (7, 9, 7), SKIN, mirror=mirror, detail=smooth)
        fore.cube(f"{side}_shackle", (x(15, 8), 32, -4), (8, 4, 8), IRON, inflate=0.3, mirror=mirror,
                  detail=shackle_detail)
        hand = fore.bone(f"{side}_hand", (19 * s, 30, 0))
        hand.cube(f"{side}_wrist", (x(16, 6), 28.5, -3), (6, 2, 6), SKIN, mirror=mirror)
        arms[side] = hand
    # the broken chain hanging from his left shackle
    chain(m.bone_names["left_forearm"], "left_chain", (-22.8, 33.5, 0.5), (-23.5, 22, 2.5), 5)

    # the right fist, closed round the oar: the back of the hand outward, four fingers wrapped round the front of the
    # shaft and their tips on the inside, the thumb over them
    right = arms["right"]
    right.cube("right_hand", (20.3, 22.5, -3.5), (3, 7, 7), SKIN, detail=knuckle_detail)
    right.cube("right_knuckles", (19.5, 22.8, -4.5), (3, 7, 1), SKIN, detail=finger_rolls)
    right.cube("right_grip", (16.5, 22.8, -4.3), (4, 7, 3), SKIN, detail=finger_rolls)
    right.cube("right_fingertips", (15.6, 22.8, -1.8), (2, 7, 4), SKIN, detail=finger_rolls)
    seg(right, "right_thumb", (20.5, 29.5, -1.5), (16.5, 29.2, -3.6), 2, 2, SKIN, extra=0.6)

    # the left hand, open: a palm turned toward him, a thumb at the front, four fingers that close as he grabs
    left = arms["left"]
    left.cube("left_hand", (-21, 21.5, -4), (4, 7, 8), SKIN, mirror=True, detail=knuckle_detail)
    seg(left, "left_thumb", (-17.6, 26, -3.5), (-16.4, 21.5, -5.8), 2, 2, SKIN, extra=0.8)
    seg(left, "left_thumb_tip", (-16.4, 21.5, -5.8), (-16.0, 19, -6.4), 2, 2, SKIN, extra=0.3, detail=knuckle_detail)
    fingers = left.bone("left_fingers", (-19, 22, 0))
    for i, (zc, length) in enumerate(((-3, 4.0), (-1, 4.6), (1, 4.3), (3, 3.4))):
        p0, p1 = (-19.0, 22.5, zc), (-18.6, 22.5 - length, zc)
        p2 = (-17.8, 22.5 - length - length * 0.75, zc)
        seg(fingers, f"left_finger_{i}", p0, p1, 2, 2, SKIN, extra=0.6, detail=knuckle_detail)
        seg(fingers, f"left_finger_{i}_tip", p1, p2, 2, 2, SKIN, extra=0.4, detail=knuckle_detail)

    # the great oar, held in the right hand: a long shaft bound with rope under its iron cap, an iron collar where the
    # shaft meets the blade, and a heavy blade patched with iron straps
    oar = arms["right"].bone("oar", (19, 26, 0))
    oar.cube("shaft", (17.5, -30, -1.5), (3, 90, 3), WOOD, detail=grain)
    oar.cube("grip", (17, 58, -2), (4, 4, 4), IRON, detail=shackle_detail)
    oar.cube("binding", (17.2, 50, -1.8), (4, 6, 4), ROPE, inflate=-0.1)
    oar.cube("collar", (17, -32, -2), (4, 4, 4), IRON, detail=shackle_detail)
    oar.cube("blade_neck", (17.5, -33, -3.5), (3, 3, 7), WOOD, detail=grain)
    oar.cube("blade", (17.5, -53, -5), (3, 20, 10), WOOD, detail=blade_detail)
    oar.cube("blade_tip", (17.5, -54, -4), (3, 1, 8), WOOD)
    oar.cube("blade_spine", (16.5, -52, -1.5), (5, 20, 3), WOOD, detail=grain)
    for i, y in enumerate((-38, -47)):
        oar.cube(f"blade_strap_{i}", (17, y, -5.5), (4, 1, 11), IRON, detail=strap_detail)
    seg(oar, "blade_weed", (21, -36, 3), (21.5, -46, 4.5), 2, 1, WEED, extra=0.5)

    animations(m)
    return m


# ----------------------------------------------------------------------------------------------- animations

ZERO = (0, 0, 0)


def mirror(v):
    return (v[0], -v[1], -v[2])


def fingers(amount):
    """The left hand's fingers curled toward the palm by this many degrees (negative spreads them)."""
    return (0, 0, amount)


def animations(m):
    # wading in place: he sways with the water, the oar dipping
    idle = m.animation("idle", 4.0, loop=True)
    idle.pos("hips", [(0, ZERO), (2, (0, -1, 0)), (4, ZERO)])
    idle.rot("torso", [(0, ZERO), (1, (0, 4, 1)), (2, ZERO), (3, (0, -4, -1)), (4, ZERO)])
    idle.rot("head", [(0, ZERO), (1.5, (5, 10, 0)), (2.5, (5, -10, 0)), (4, ZERO)])
    idle.rot("right_arm", [(0, (-20, 0, 0)), (2, (-26, 0, 0)), (4, (-20, 0, 0))])
    idle.rot("left_arm", [(0, (-10, 0, 0)), (2, (-6, 0, 0)), (4, (-10, 0, 0))])
    idle.rot("oar", [(0, (10, 0, 0)), (2, (14, 0, 0)), (4, (10, 0, 0))])
    idle.rot("beard", [(0, ZERO), (1.3, (3, 0, -2)), (2.6, (1, 0, 2)), (4, ZERO)])
    idle.rot("left_fingers", [(0, fingers(10)), (2, fingers(20)), (4, fingers(10))])

    walk = m.animation("walk", 2.0, loop=True)
    walk.rot("right_leg", [(0, (20, 0, 0)), (1, (-20, 0, 0)), (2, (20, 0, 0))])
    walk.rot("left_leg", [(0, (-20, 0, 0)), (1, (20, 0, 0)), (2, (-20, 0, 0))])
    walk.rot("torso", [(0, (0, 6, 0)), (1, (0, -6, 0)), (2, (0, 6, 0))])
    # the knee bends as each leg swings back and straightens as it reaches forward
    walk.rot("right_shin", [(0, ZERO), (0.5, (-15, 0, 0)), (1, (-30, 0, 0)), (1.5, (-10, 0, 0)), (2, ZERO)])
    walk.rot("left_shin", [(0, (-30, 0, 0)), (0.5, (-10, 0, 0)), (1, ZERO), (1.5, (-15, 0, 0)), (2, (-30, 0, 0))])

    # the oar raised overhead and brought down on the landing: the white flash is the moment to block
    oar = m.animation("oar", 1.8)
    oar.rot("right_arm", [(0, ZERO), (0.7, (175, 0, -10)), (1.05, (70, 0, 0)), (1.3, (65, 0, 0)), (1.8, ZERO)])
    oar.rot("left_arm", [(0, ZERO), (0.7, (165, 0, 20)), (1.05, (60, 0, 10)), (1.8, ZERO)])
    oar.rot("torso", [(0, ZERO), (0.7, (14, 0, 0)), (1.05, (-22, 0, 0)), (1.3, (-20, 0, 0)), (1.8, ZERO)])
    oar.rot("head", [(0, ZERO), (0.7, (12, 0, 0)), (1.05, (-15, 0, 0)), (1.8, ZERO)])
    oar.rot("jaw", [(0, ZERO), (0.9, (-30, 0, 0)), (1.3, ZERO)])
    oar.rot("oar", [(0, (10, 0, 0)), (0.7, (30, 0, 0)), (1.05, (-10, 0, 0)), (1.3, (-5, 0, 0)), (1.8, (10, 0, 0))])
    oar.rot("beard", [(0, ZERO), (0.9, (22, 0, 0)), (1.3, ZERO)])
    oar.event(0.55, "flash").event(1.05, "hit")

    # the oar swept round over the water in a full circle (purple: it can't be blocked, jump it)
    # the oar held straight out in front at arm's length, level over the deck, and swung right round with him (the purple
    # ring is its reach); he winds up the other way first
    sweep = m.animation("sweep", 2.6)
    sweep.rot("torso", [(0, ZERO), (1.0, (0, 50, 0)), (1.2, (0, 50, 0)), (2.0, (0, -310, 0)), (2.6, (0, -360, 0))])
    sweep.rot("right_arm", [(0, ZERO), (0.8, (90, 0, 0)), (2.0, (90, 0, 0)), (2.6, ZERO)])
    sweep.rot("right_forearm", [(0, ZERO), (0.8, (-30, 0, 0)), (2.0, (-30, 0, 0)), (2.6, ZERO)])
    sweep.rot("oar", [(0, (10, 0, 0)), (0.8, (0, 0, 0)), (2.0, (0, 0, 0)), (2.6, (10, 0, 0))])
    sweep.rot("left_arm", [(0, ZERO), (0.8, (60, 0, -20)), (2.0, (60, 0, -20)), (2.6, ZERO)])
    sweep.event(1.2, "sweep").event(2.0, "swept")

    # the wave: both fists brought down on the water; it rolls out over the landing
    wave = m.animation("wave", 2.0)
    for side, s in (("right", 1), ("left", -1)):
        keys = [(0, ZERO), (0.6, (170, 0, 25)), (1.0, (40, 0, 5)), (1.4, (40, 0, 5)), (2.0, ZERO)]
        wave.rot(f"{side}_arm", keys if s == 1 else [(t, mirror(v)) for t, v in keys])
    wave.rot("torso", [(0, ZERO), (0.6, (12, 0, 0)), (1.0, (-30, 0, 0)), (1.4, (-28, 0, 0)), (2.0, ZERO)])
    wave.rot("jaw", [(0, ZERO), (0.8, (-35, 0, 0)), (1.4, ZERO)])
    wave.rot("oar", [(0, (10, 0, 0)), (0.6, (-30, 0, 0)), (2.0, (10, 0, 0))])
    wave.rot("left_fingers", [(0, ZERO), (0.6, fingers(80)), (1.4, fingers(80)), (2.0, ZERO)])
    wave.rot("beard", [(0, ZERO), (0.8, (25, 0, 0)), (1.4, ZERO)])
    wave.event(1.0, "slam")

    # he reaches for a soul with his free hand and hurls it into the deep
    grab = m.animation("grab", 2.2)
    grab.rot("left_arm", [(0, ZERO), (0.8, (95, -20, 25)), (1.1, (100, -25, 30)), (1.6, (150, 0, 10)), (1.9, (40, 0, -20)), (2.2, ZERO)])
    grab.rot("left_forearm", [(0, ZERO), (0.8, (-20, 0, 0)), (1.6, (10, 0, 0)), (2.2, ZERO)])
    grab.rot("torso", [(0, ZERO), (0.8, (20, -25, 0)), (1.6, (-10, 15, 0)), (1.9, (10, 25, 0)), (2.2, ZERO)])
    grab.rot("head", [(0, ZERO), (0.8, (15, -20, 0)), (1.6, (-10, 0, 0)), (2.2, ZERO)])
    grab.rot("left_fingers", [(0, ZERO), (0.8, fingers(-20)), (1.1, fingers(75)), (1.6, fingers(80)),
                              (1.9, fingers(-25)), (2.2, ZERO)])
    grab.event(1.1, "grab").event(1.9, "throw")

    stagger = m.animation("stagger", 2.0)
    stagger.rot("torso", [(0, ZERO), (0.15, (-18, 0, 6)), (0.8, (-12, 0, -5)), (1.4, (-8, 0, 4)), (2.0, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.15, (-30, 0, 0)), (1.0, (-15, 15, 0)), (2.0, ZERO)])
    stagger.rot("jaw", [(0, ZERO), (0.15, (-30, 0, 0)), (1.4, (-15, 0, 0)), (2.0, ZERO)])
    stagger.rot("beard", [(0, ZERO), (0.15, (22, 0, 0)), (1.4, (10, 0, 0)), (2.0, ZERO)])
    stagger.rot("right_arm", [(0, ZERO), (0.15, (-30, 0, 40)), (2.0, ZERO)])
    stagger.rot("left_arm", [(0, ZERO), (0.15, (-30, 0, -40)), (2.0, ZERO)])

    # at half his health: he roars up at the burning city and beats the water with his oar
    phase = m.animation("phase", 3.0)
    phase.rot("torso", [(0, ZERO), (0.8, (25, 0, 0)), (2.0, (25, 0, 0)), (2.4, (-15, 0, 0)), (3.0, ZERO)])
    phase.rot("head", [(0, ZERO), (0.8, (-30, 0, 0)), (2.0, (-30, 0, 0)), (3.0, ZERO)])
    phase.rot("jaw", [(0, ZERO), (0.8, (-55, 0, 0)), (2.0, (-55, 0, 0)), (2.4, ZERO)])
    phase.rot("right_arm", [(0, ZERO), (0.8, (170, 0, -20)), (2.0, (170, 0, -20)), (2.4, (60, 0, 0)), (3.0, ZERO)])
    phase.rot("left_arm", [(0, ZERO), (0.8, (-20, 0, -80)), (2.0, (-20, 0, -80)), (3.0, ZERO)])
    # the beard hangs from the jaw: as the jaw drops it would swing back into his chest, so it swings forward to hang
    phase.rot("beard", [(0, ZERO), (0.8, (40, 0, 0)), (1.2, (36, 0, 3)), (1.6, (38, 0, -3)), (2.0, (38, 0, 0)),
                        (2.4, (-8, 0, 0)), (3.0, ZERO)])
    phase.rot("left_fingers", [(0, ZERO), (0.8, fingers(-25)), (2.0, fingers(-25)), (3.0, ZERO)])
    phase.event(0.8, "roar").event(2.4, "slam")

    # up out of the deep: he rises from the water, shakes the weed from his face and roars
    intro = m.animation("intro", 6.0)
    intro.pos("hips", [(0, (0, -60, 0)), (0.8, (0, -60, 0)), (3.0, (0, -4, 0)), (3.4, ZERO)])
    intro.rot("torso", [(0, (30, 0, 0)), (3.0, (10, 0, 0)), (3.6, (0, 0, 8)), (3.9, (0, 0, -8)), (4.2, (-20, 0, 0)), (5.2, (-20, 0, 0)), (6.0, ZERO)])
    intro.rot("head", [(0, (30, 0, 0)), (3.0, (10, 0, 0)), (4.2, (-25, 0, 0)), (5.2, (-25, 0, 0)), (6.0, ZERO)])
    intro.rot("jaw", [(0, ZERO), (4.1, (-5, 0, 0)), (4.3, (-55, 0, 0)), (5.2, (-50, 0, 0)), (5.6, ZERO)])
    intro.rot("right_arm", [(0, (-20, 0, 0)), (4.2, (160, 0, -15)), (5.2, (160, 0, -15)), (6.0, (-20, 0, 0))])
    intro.rot("beard", [(0, (25, 0, 0)), (3.0, (10, 0, 0)), (3.6, (0, 0, 10)), (3.9, (0, 0, -10)), (4.1, (8, 0, 0)),
                        (4.3, (40, 0, 0)), (5.2, (36, 0, 0)), (5.6, ZERO)])
    intro.event(0.8, "rise").event(4.3, "roar")

    hurt = m.animation("hurt", 0.35)
    hurt.rot("torso", [(0, ZERO), (0.1, (6, 0, 0)), (0.35, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (-8, 0, 0)), (0.35, ZERO)])

    # he sinks back into the Styx, his oar left floating
    death = m.animation("death", 3.6)
    death.rot("torso", [(0, ZERO), (0.6, (-20, 0, 0)), (1.8, (30, 0, 10)), (3.6, (40, 0, 12))])
    death.pos("hips", [(0, ZERO), (1.0, (0, -4, 0)), (3.6, (0, -70, 0))])
    death.rot("jaw", [(0, ZERO), (0.6, (-50, 0, 0)), (3.6, (-30, 0, 0))])
    death.rot("right_arm", [(0, ZERO), (0.6, (120, 0, -20)), (1.8, (60, 0, 0)), (3.6, (40, 0, 0))])
    death.rot("oar", [(0, (10, 0, 0)), (1.8, (70, 0, 0)), (3.6, (85, 0, 0))])
    death.rot("beard", [(0, ZERO), (0.6, (36, 0, 0)), (3.6, (22, 0, 0))])
    death.event(1.8, "sink")


if __name__ == "__main__":
    build().write()
