#!/usr/bin/env python3
"""Minos, Judge of the Damned (Inferno V): an old horned king rising out of the coils of his own serpent tail, in a
crimson judge's mantle, with a long white beard and eyes like coals. "He girds himself with his tail as many times
as grades he wills the soul be thrust down." The tail curls around him at rest and lashes out in his attacks.

Run from the repo root:  python3 tools/art/minos.py && python3 tools/gen_bosses.py

The animations' events are what the server times Minos's fight by (see MinosFight): "flash" opens the window for a
shield block, "hit" is when a blow lands, "sweep" .. "swept" is the tail's pass, and so on.

The body is built like a man's, only larger: a gaunt old chest with sagging pectorals and ribs, a neck that juts the
head forward, arms in tapered segments (deltoid, upper arm, elbow, forearm, wrist) and clawed hands with three
fingers and a thumb that curl (the fingers are their own bones). The serpent half is one coil winding up and inward
from the tail into his waist, and the tail tapers segment by segment to a barbed tip.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SKIN = material("skin", "skin", base="8e8597", dark="62586c")
ROBE = material("robe", "robe", base="6a1222", fold="420a15", trim="d4a83a")
MANTLE = material("mantle", "robe", base="6a1222", fold="420a15", trim="d4a83a", opening=True)
LINING = material("lining", "robe", base="4a0c18", fold="2e0610")
SCALES = material("scales", "scales", base="2c2436", edge="5a4870", belly="b9a07e")
BELLY_FRONT = material("belly_front", "scales", base="2c2436", edge="5a4870", belly="b9a07e", belly_face="front")
HAIR = material("hair", "hair", light="e2ded6", dark="8f8a84")
GOLD = material("gold", "metal", base="c9982e", high="f1d27a", low="7d5a17")
HORN = material("horn", "horn", root="d9ceb4", tip="3a2c22")
CLAW = material("claw", "flat", base="1e1b21")

EYE = hexc("ffd25a")
EYE_HOT = hexc("fff6c8")
SOCKET = hexc("24182a")
MOUTH = hexc("2a0d10")
TOOTH = hexc("e8dfc8")
RUBY = hexc("c01828")
RUBY_HOT = hexc("ff5a5a")
VEIN = hexc("5a5078")
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


def mul(face, fx, fy, f):
    """Darkens (f < 1) or lightens one pixel of a face that is already painted."""
    if 0 <= fx < face.w and 0 <= fy < face.h:
        c = face.canvas.get(face.x + fx, face.y + fy)
        if c[3]:
            face.px(fx, fy, shade(c, f))


def ao(bottom=0, top=0, dark=0.7, faces=SIDES):
    """A detail: the side faces shaded along their lowest rows (where the part sinks into shadow under itself or into
    the next) and their highest (where it tucks under the part above)."""
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
    """The face under the crown: a furrowed forehead, deep sockets under the brow with eyes like coals, hollow cheeks
    and the lines from the nose to the corners of the mouth."""
    f = fs["front"]
    w = f.w
    dark = shade(SKIN.c["dark"], 0.85)
    for x in range(2, w - 2):  # the furrows of the forehead
        if x % 5:
            f.px(x, 3, dark)
        if (x + 2) % 6:
            mul(f, x, 4, 0.9)
    for cx in (3, w - 5):
        for dx in range(-1, 3):
            f.px(cx + dx, 6, SOCKET)
            f.px(cx + dx, 8, shade(SKIN.c["dark"], 0.6))
        f.px(cx - 1, 7, SOCKET)
        f.px(cx + 2, 7, SOCKET)
        f.glow(cx, 7, EYE)
        f.glow(cx + 1, 7, EYE_HOT)
        f.px(cx + (2 if cx < w // 2 else -1), 9, shade(SKIN.c["dark"], 0.7))  # a bag under the eye
    for y in (9, 10, 11):  # hollow cheeks and the lines from the nose
        mul(f, 1, y, 0.75)
        mul(f, w - 2, y, 0.75)
    for i, y in enumerate((9, 10, 11)):
        f.px(5 - i // 2, y, dark)
        f.px(w - 6 + i // 2, y, dark)
    for y in range(6, 12):  # the temples, in shadow
        mul(f, 0, y, 0.8)
        mul(f, w - 1, y, 0.8)
    s = fs["right"], fs["left"]
    for side in s:  # the temples and the hollow behind the cheekbone
        for y in range(5, 9):
            for x in range(1, 4):
                mul(side, x if side.name == "left" else side.w - 1 - x, y, 0.85)


def brow_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["base"], 1.15))  # light on the ridge
        mul(f, x, f.h - 1, 0.7)
    for x in range(1, f.w - 1, 2):
        f.px(x, 0, shade(HAIR.c["light"], 0.9))  # wiry white brows
    b = fs["bottom"]
    b.fill(lambda x, y: shade(SOCKET, 1.3))


def nose_detail(fs):
    f = fs["front"]
    for y in range(f.h):
        f.px(0, y, shade(SKIN.c["dark"], 0.9))
    f.px(f.w // 2, 0, shade(SKIN.c["base"], 1.15))


def nostrils(fs):
    f = fs["front"]
    f.px(0, f.h - 1, MOUTH)
    f.px(f.w - 1, f.h - 1, MOUTH)
    fs["bottom"].fill(lambda x, y: MOUTH if x != 1 else shade(SKIN.c["dark"], 0.8))


def jaw_detail(fs):
    """A grim mouth: thin dark lips over yellowed fangs."""
    f = fs["front"]
    for x in range(1, f.w - 1):
        f.px(x, 0, MOUTH)
        mul(f, x, 1, 0.8)
    for x in (3, f.w - 4):
        f.px(x, 0, TOOTH)
        f.px(x, 1, shade(TOOTH, 0.85))
    fs["top"].fill(lambda x, y: MOUTH)
    for n in ("left", "right", "bottom"):  # under the jaw, in shadow
        g = fs[n]
        for x in range(g.w):
            for y in range(g.h):
                mul(g, x, y, 0.8)


def ear_detail(fs):
    for n in ("left", "right"):
        f = fs[n]
        for y in range(1, f.h - 1):
            mul(f, f.w // 2, y, 0.65)  # the hollow of the ear


def neck_detail(fs):
    """Stringy old tendons down the front of the neck, the shadow under the jaw."""
    f = fs["front"]
    dark = shade(SKIN.c["dark"], 0.9)
    for y in range(f.h):
        f.px(1 + y // 3, y, dark)
        f.px(f.w - 2 - y // 3, y, dark)
    for x in range(f.w):
        mul(f, x, 0, 0.6)
        mul(f, x, 1, 0.8)


def chest_detail(fs):
    """Old muscle: the breastbone between the pectorals, collarbones above them and ribs down the flanks."""
    f = fs["front"]
    mid = f.w // 2
    dark = shade(SKIN.c["dark"], 0.85)
    for y in range(0, f.h):
        f.px(mid - 1, y, dark)
        mul(f, mid, y, 0.9)
    for x in range(2, mid - 2):  # the collarbones
        f.px(x, 1, shade(SKIN.c["base"], 1.12))
        f.px(f.w - 1 - x, 1, shade(SKIN.c["base"], 1.12))
        mul(f, x, 2, 0.8)
        mul(f, f.w - 1 - x, 2, 0.8)
    for x in range(1, f.w - 1):  # the shadow under the pectorals
        if abs(x - mid) > 1:
            mul(f, x, f.h - 2, 0.72)
    for n in ("left", "right"):  # ribs
        g = fs[n]
        for y in (2, 5, 8, 11):
            for x in range(1, g.w - 1):
                if (x + y) % 4:
                    mul(g, x, min(g.h - 1, y + x // 6), 0.78)


def pec_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.65)
        f.px(x, 0, shade(SKIN.c["base"], 1.1))
    f.px(f.w // 2, f.h - 2, shade(SKIN.c["dark"], 0.7))


def belly_detail(fs):
    """A gaunt old belly: the lower ribs arching over it, the line down the middle and the navel."""
    f = fs["front"]
    mid = f.w // 2
    dark = shade(SKIN.c["dark"], 0.88)
    for y in range(f.h):
        f.px(mid, y, dark)
    for i in range(5):
        f.px(mid - 2 - i, i, dark)
        f.px(mid + 2 + i, i, dark)
        mul(f, mid - 2 - i, i + 1, 0.85)
        mul(f, mid + 2 + i, i + 1, 0.85)
    f.px(mid, f.h - 3, MOUTH)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            for x in range(g.w):
                mul(g, x, y, 0.88)


def arm_detail(fs):
    """Veins winding down a forearm (or an upper arm)."""
    for n in ("front", "left", "right"):
        f = fs[n]
        x = f.w // 2
        for y in range(1, f.h - 1):
            x = max(1, min(f.w - 2, x + f.rng.choice((-1, 0, 0, 1))))
            f.px(x, y, mix(f.canvas.get(f.x + x, f.y + y)[:3], VEIN, 0.45))


def knuckle_detail(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.7)
        if f.h > 4:
            for x in range(1, f.w - 1, 2):
                mul(f, x, f.h - 3, 0.8)


def claw_detail(fs):
    for n in SIDES:
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                if y == 0:
                    mul(f, x, y, 1.6)  # where the claw meets the skin
                elif y >= f.h - 1:
                    mul(f, x, y, 1.4)  # the worn tip


def crown_detail(fs):
    """Gems set round the band: a ruby at the front, garnets between."""
    for n in SIDES:
        f = fs[n]
        mid = f.w // 2
        for x in range(2, f.w - 2, 4):
            f.px(x, 1, hexc("6a1a4a"))
        f.px(mid - 1, 1, RUBY)
        f.px(mid, 1, RUBY_HOT if n == "front" else RUBY)


def spike_detail(fs):
    f = fs["front"]
    if f.h >= 4:
        f.glow(f.w // 2, 1, RUBY_HOT)


def belt_detail(fs):
    """Gold plaques round the belt, each with a dark rivet."""
    for n in SIDES:
        f = fs[n]
        for x in range(0, f.w, 4):
            for y in range(f.h):
                mul(f, x, y, 0.65)
            f.px(x + 2, f.h // 2, hexc("5a3a10"))


def buckle_detail(fs):
    f = fs["front"]
    for y in range(1, f.h - 1):
        for x in range(1, f.w - 1):
            mul(f, x, y, 0.8)
    f.glow(f.w // 2, f.h // 2, RUBY_HOT)
    f.px(f.w // 2 - 1, f.h // 2, RUBY)


def clasp_detail(fs):
    f = fs["front"]
    f.glow(f.w // 2, f.h // 2, RUBY_HOT)


def lapel_detail(fs):
    """The gold edge of the mantle where it falls open over the chest."""
    f = fs["front"]
    inner = 0 if f.cube.frm[0] > 0 else f.w - 1
    for y in range(f.h):
        f.px(inner, y, shade(ROBE.c["trim"], 1.0 if y % 3 else 0.8))


def tabard_detail(fs):
    """A gold border down both edges of the hanging panel, and a judge's key embroidered on it."""
    f = fs["front"]
    gold = ROBE.c["trim"]
    for y in range(f.h):
        f.px(0, y, shade(gold, 0.85))
        f.px(f.w - 1, y, shade(gold, 0.85))
    mid = f.w // 2
    for y in range(2, 8):
        f.px(mid, y, gold)
    for dx in (-1, 1):
        f.px(mid + dx, 2, gold)
        f.px(mid + dx, 3, shade(gold, 0.8))
    f.px(mid + 1, 7, gold)
    f.px(mid + 1, 6, gold)


def fold_detail(fs):
    for n in ("left", "right"):
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                mul(f, x, y, 0.7)


def strands(fs):
    """Hair and beard: combed strands, each column a little lighter or darker, with dark partings between locks and
    the ends catching the light."""
    for n in SIDES + ("top",):
        f = fs[n]
        tones = [0.86 + 0.24 * f.rng.random() for _ in range(f.w)]
        for x in range(f.w):
            parting = f.rng.random() < 0.22
            for y in range(f.h):
                k = tones[x] * (0.72 if parting and n != "top" else 1.0)
                mul(f, x, y, k)
        if n != "top":
            for x in range(f.w):
                if f.rng.random() < 0.5:
                    mul(f, x, f.h - 1, 1.12)


def scales_ridge(fs):
    """A paler stripe along the serpent's spine."""
    f = fs["top"]
    mid = f.w // 2
    for y in range(f.h):
        for x in (mid - 1, mid):
            mul(f, x, y, 1.3 if y % 3 else 1.1)


# ----------------------------------------------------------------------------------------------- the body

def build():
    m = Model("minos", texture_width=256)

    # the coils of the serpent tail he stands in: one coil winding up and inward from the tail (which leaves it at
    # his back) to his waist, each segment laid along the spiral and a little thinner than the one before
    coils = m.bone("coils", (0, 0, 0))
    per_turn, turns = 12, 2.5
    count = int(per_turn * turns)
    points = []
    for k in range(count + 1):
        t = k / count
        a = math.radians(90 + 360 * turns * t)
        r = 17 - 8 * t
        points.append((r * math.cos(a), 15 * t, r * math.sin(a), t))
    for k in range(count):
        (x0, y0, z0, t), (x1, y1, z1, _) = points[k], points[k + 1]
        thick = round(12 - 2.5 * t)
        height = round(11 - 2.5 * t)
        seg(coils, f"coil_{k}", (x0, y0 + height / 2, z0), (x1, y1 + height / 2, z1), thick, height, SCALES,
            extra=0.55 * thick + 0.5, detail=scales_ridge)

    # the serpent body rising out of the coils into the man, girt with his skirts
    waist = m.bone("waist", (0, 26, 2))
    waist.cube("waist_body", (-8, 18, -5), (16, 10, 14), BELLY_FRONT)
    waist.cube("waist_upper", (-9.5, 27, -6.5), (19, 12, 16), BELLY_FRONT)
    waist.cube("skirt", (-12, 33, -9), (24, 8, 20), ROBE, inflate=0.25, detail=ao(top=2))
    waist.cube("skirt_front", (-5, 25, -10.5), (10, 14, 2), ROBE, detail=tabard_detail)
    waist.cube("skirt_back", (-7, 26, 10), (14, 13, 2), ROBE)
    for s, side in ((1, "r"), (-1, "l")):
        waist.cube(f"skirt_{side}", (side_x(11.5, 2, s), 27, -6), (2, 12, 13), ROBE, rotation=(0, 0, 6 * s),
                   origin=(12 * s, 39, 0), mirror=s == -1)
    waist.cube("belt", (-12, 40, -9.5), (24, 3, 20), GOLD, inflate=0.35, detail=belt_detail)
    waist.cube("buckle", (-3, 38.5, -11), (6, 5, 1), GOLD, detail=buckle_detail)

    # the torso: a gaunt old chest, broad across the shoulders, narrowing to the belly; the mantle over it
    torso = waist.bone("torso", (0, 42, 2))
    torso.cube("chest", (-12, 50, -8), (24, 10, 16), SKIN, detail=both(smooth, chest_detail))
    torso.cube("lats", (-13, 52, -6), (26, 7, 13), SKIN, detail=both(smooth, ao(bottom=2)))
    torso.cube("belly", (-10, 42, -6.5), (20, 9, 14), SKIN, detail=both(smooth, belly_detail))
    for s, side in ((1, "r"), (-1, "l")):
        torso.cube(f"pec_{side}", (side_x(0.5, 9, s), 52, -9.5), (9, 6, 2), SKIN, rotation=(-6, 0, 0),
                   origin=(5 * s, 58, -8), mirror=s == -1, detail=pec_detail)
    torso.cube("neck", (-4, 60, -5), (8, 7, 8), SKIN, detail=neck_detail)
    torso.cube("mantle", (-13, 60, -6.5), (26, 4, 15), MANTLE, inflate=0.5)
    torso.cube("mantle_back", (-14, 50, 4.5), (28, 12, 5), ROBE, inflate=0.3)
    torso.cube("collar", (-6, 63.5, -6), (12, 2, 12), GOLD, inflate=0.2)
    for s, side in ((1, "r"), (-1, "l")):
        torso.cube(f"lapel_{side}", (side_x(7.5, 5, s), 47, -10), (5, 14, 2), ROBE, mirror=s == -1,
                   detail=lapel_detail)
    torso.cube("clasp_chain", (-7, 56, -10.5), (14, 1, 1), GOLD)
    torso.cube("clasp", (-1.5, 54.5, -11), (3, 3, 1), GOLD, detail=clasp_detail)
    # the cape down his back, flaring as it falls, with deep folds
    torso.cube("cape", (-14, 41, 9), (28, 19, 3), ROBE, detail=ao(top=3))
    torso.cube("cape_low", (-13.5, 23, 9.5), (27, 19, 3), ROBE, rotation=(-8, 0, 0), origin=(0, 42, 11))
    for i, fx in enumerate((-10, -3, 5)):
        torso.cube(f"cape_fold_{i}", (fx, 24, 12), (3, 17, 1), LINING, rotation=(-8, 0, 0), origin=(0, 42, 11),
                   detail=fold_detail)

    # the head, carried a little forward on the old neck: crown, horns, brow, nose, cheekbones, ears and white hair
    head = torso.bone("head", (0, 65, -1))
    head.cube("skull", (-7, 68, -9), (14, 12, 14), SKIN, detail=face_detail)
    for s, side in ((1, "r"), (-1, "l")):
        head.cube(f"brow_{side}", (side_x(0.2, 6.5, s), 73, -10.5), (7, 2, 2), SKIN, rotation=(0, 0, 9 * s),
                  origin=(0.5 * s, 74, -9.5), mirror=s == -1, detail=brow_detail)
        head.cube(f"cheek_{side}", (side_x(3.5, 3, s), 69.5, -10), (3, 2, 2), SKIN, mirror=s == -1, detail=ao(bottom=1))
        head.cube(f"ear_{side}", (side_x(7, 1, s), 69, -3), (1, 5, 3), SKIN, rotation=(0, -18 * s, 0),
                  origin=(7 * s, 71, -3), mirror=s == -1, detail=ear_detail)
        seg(head, f"ear_tip_{side}", (7.6 * s, 73.5, -1.2), (8.6 * s, 76.5, 0.6), 1, 1, SKIN, extra=0.5)
    head.cube("nose", (-1, 70, -11), (2, 4, 2), SKIN, rotation=(10, 0, 0), origin=(0, 74, -9), detail=nose_detail)
    head.cube("nose_tip", (-1.5, 68.5, -11.8), (3, 2, 3), SKIN, detail=nostrils)
    head.cube("moustache", (-2.5, 67.2, -11.3), (5, 2, 2), HAIR, detail=strands)
    for s, side in ((1, "r"), (-1, "l")):
        seg(head, f"moustache_{side}", (1.5 * s, 68, -10.8), (6.5 * s, 62.5, -10.6), 2, 2, HAIR, extra=1,
            detail=strands)
    head.cube("hair_top", (-6.5, 80, -8.5), (13, 1, 13), HAIR, detail=strands)
    head.cube("hair_back", (-7.5, 66, 4.5), (15, 13, 3), HAIR, inflate=0.3, detail=strands)
    head.cube("hair_nape", (-6.5, 62, 5), (13, 4, 3), HAIR, detail=strands)
    # the long white hair falls over the mantle and lies on the cape, thinning to its ends
    seg(head, "hair_fall", (0, 64, 8), (0, 57, 12), 13, 3, HAIR, extra=1, detail=strands)
    seg(head, "hair_fall_low", (0, 58, 12.6), (0, 49, 14.6), 9, 2, HAIR, extra=0.5, detail=strands)
    for s, side in ((1, "r"), (-1, "l")):
        seg(head, f"hair_lock_{side}", (6.9 * s, 74, 2.5), (7.6 * s, 63.5, 3.5), 2, 3, HAIR, extra=0.5, detail=strands)
    head.cube("crown", (-7, 77, -9), (14, 3, 14), GOLD, inflate=0.6, detail=crown_detail)
    for i, (sx, sz, sh) in enumerate(((0, -9.4, 5), (4, -9.4, 3), (-4, -9.4, 3), (6.9, -4, 3), (-6.9, -4, 3),
                                       (6.9, 2, 3), (-6.9, 2, 3), (0, 5.4, 3))):
        head.cube(f"crown_spike_{i}", (sx - 1, 80, sz - 1), (2, sh, 2), GOLD, detail=spike_detail)
        head.cube(f"crown_point_{i}", (sx - 0.5, 80 + sh, sz - 0.5), (1, 2, 1), GOLD)
    for s, side in ((1, "r"), (-1, "l")):
        # ram's horns from the temples: out, back, up and curling forward at the tips, ridged and tapering
        path = [(6.0, 76.0, -3.5), (9.5, 77.5, -3.0), (12.5, 79.5, -1.5), (14.5, 82.0, 0.0), (15.5, 84.5, -1.5),
                (15.2, 86.0, -3.8)]
        for i, thick in enumerate((5, 4, 4, 3, 2)):
            (ax, ay, az), (bx, by, bz) = path[i], path[i + 1]
            seg(head, f"horn_{side}_{i + 1}", (ax * s, ay, az), (bx * s, by, bz), thick, thick, HORN, extra=1.2)

    jaw = head.bone("jaw", (0, 66, -3))
    jaw.cube("jaw", (-6, 64, -9.5), (12, 4, 11), SKIN, detail=jaw_detail)
    jaw.cube("chin", (-3, 62.5, -10.5), (6, 3, 2), SKIN)
    # the long white beard: full at the chin, tapering and forking as it falls to his belt
    beard = jaw.bone("beard", (0, 64, -9))
    beard.cube("beard", (-6.5, 56, -12), (13, 9, 4), HAIR, detail=both(ao(top=1), strands))
    for s, side in ((1, "r"), (-1, "l")):
        beard.cube(f"whiskers_{side}", (side_x(5, 2, s), 59, -10), (2, 7, 6), HAIR, mirror=s == -1, detail=strands)
    beard.cube("beard_mid", (-5, 49, -12), (10, 8, 3), HAIR, detail=strands)
    beard_tip = beard.bone("beard_tip", (0, 50, -10.5))
    beard_tip.cube("beard_low", (-3.5, 43, -11.5), (7, 7, 3), HAIR, detail=strands)
    for s, side in ((1, "r"), (-1, "l")):
        seg(beard_tip, f"beard_fork_{side}", (1.8 * s, 44, -10.5), (2.6 * s, 37.5, -10.8), 3, 2, HAIR, extra=0.5,
            detail=strands)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mirror = s == -1
        arm = torso.bone(f"{side}_arm", (15 * s, 60, 0), rotation=(0, 0, 8 * s))
        # the mantle's shoulder capes, in two tiers, over a deltoid and a tapering upper arm
        arm.cube(f"{side}_shoulder", (x(12, 12), 56, -6.5), (12, 6, 13), ROBE, inflate=0.5, mirror=mirror)
        arm.cube(f"{side}_shoulder_2", (x(13.5, 11), 52, -6), (11, 5, 12), ROBE, inflate=0.3, mirror=mirror)
        arm.cube(f"{side}_deltoid", (x(14.5, 9), 49, -4.5), (9, 8, 9), SKIN, mirror=mirror, detail=smooth)
        arm.cube(f"{side}_upper", (x(15, 8), 44, -4), (8, 8, 8), SKIN, mirror=mirror, detail=both(smooth, arm_detail))
        arm.cube(f"{side}_bicep", (x(16, 5), 45, -5), (5, 6, 1), SKIN, mirror=mirror, detail=ao(bottom=1))
        arm.cube(f"{side}_upper_low", (x(15.5, 7), 40.5, -3.5), (7, 5, 7), SKIN, mirror=mirror, detail=smooth)
        fore = arm.bone(f"{side}_forearm", (19 * s, 42, 0), rotation=(25, 0, 0))
        fore.cube(f"{side}_elbow", (x(17, 4), 38.5, 2.5), (4, 4, 2), SKIN, mirror=mirror)
        fore.cube(f"{side}_fore", (x(15.5, 7), 34, -3.5), (7, 8, 7), SKIN, mirror=mirror,
                  detail=both(smooth, arm_detail))
        fore.cube(f"{side}_fore_low", (x(16, 6), 26.5, -3), (6, 8, 6), SKIN, mirror=mirror, detail=smooth)
        fore.cube(f"{side}_cuff", (x(15.5, 7), 27, -3.5), (7, 5, 7), GOLD, inflate=0.3, mirror=mirror)
        fore.cube(f"{side}_cuff_rim", (x(15, 8), 31.5, -4), (8, 1, 8), GOLD, inflate=0.2, mirror=mirror)
        # the hand: a palm turned toward him, a thumb at the front, three clawed fingers that curl
        hand = fore.bone(f"{side}_hand", (19 * s, 26, 0))
        hand.cube(f"{side}_wrist", (x(17, 4), 25, -2.5), (4, 2, 5), SKIN, mirror=mirror)
        hand.cube(f"{side}_palm", (x(17, 4), 19, -3.5), (4, 7, 7), SKIN, mirror=mirror, detail=knuckle_detail)
        seg(hand, f"{side}_thumb", (17.6 * s, 23.5, -3.0), (16.4 * s, 19.5, -5.3), 2, 2, SKIN, extra=1)
        seg(hand, f"{side}_thumb_claw", (16.4 * s, 19.5, -5.3), (15.8 * s, 16.5, -6.0), 2, 2, CLAW, extra=0.5,
            detail=claw_detail)
        fingers = hand.bone(f"{side}_fingers", (19 * s, 19.5, 0))
        for i, zc in enumerate((-2.5, 0.0, 2.5)):
            drop = 0.6 if i == 1 else 0.0  # the middle finger is the longest
            pts = [(19.0, 20.0, zc), (18.6, 15.3 - drop, zc), (17.8, 12.0 - drop, zc), (16.6, 8.8 - drop, zc - 0.2)]
            pts = [(px * s, py, pz) for px, py, pz in pts]
            seg(fingers, f"{side}_finger_{i}", pts[0], pts[1], 2, 2, SKIN, extra=0.6, detail=knuckle_detail)
            seg(fingers, f"{side}_finger_{i}_tip", pts[1], pts[2], 2, 2, SKIN, extra=0.6)
            seg(fingers, f"{side}_claw_{i}", pts[2], pts[3], 2, 2, CLAW, extra=0.3, detail=claw_detail)

    # the tail, curling around his right side at rest (each segment turned a little further), tapering as it goes
    parent = coils
    segs = [(14, 13, 16), (12, 11, 16), (10, 9, 16), (8, 7, 16), (6, 5, 16), (4, 4, 14)]
    z = 14
    for i, (w, h, d) in enumerate(segs):
        bone = parent.bone(f"tail_{i + 1}", (0, h / 2 + i * 0.5, z), rotation=(0, 28, 0))
        lap = 1 if i else 0
        half = d // 2
        bone.cube(f"tail_{i + 1}", (-w / 2, i * 0.5, z - lap), (w, h, half + lap), SCALES, detail=scales_ridge)
        bone.cube(f"tail_{i + 1}_b", (-(w - 1) / 2, i * 0.5, z + half), (w - 1, h - 1, d - half), SCALES,
                  detail=scales_ridge)
        parent = bone
        z += d
    parent.cube("tail_barb", (-1.5, 2, z - 2), (3, 3, 6), CLAW, rotation=(18, 0, 0), origin=(0, 3.5, z - 2))
    seg(parent, "tail_sting", (0, 4.2, z + 3.2), (0, 6.8, z + 6.0), 1, 2, CLAW, extra=0.5, detail=claw_detail)
    parent.cube("tail_fin_r", (1, 4, z - 10), (4, 1, 7), CLAW, rotation=(0, 25, -10), origin=(2, 4.5, z - 7))
    parent.cube("tail_fin_l", (-5, 4, z - 10), (4, 1, 7), CLAW, rotation=(0, -25, 10), origin=(-2, 4.5, z - 7))

    animations(m)
    return m




# ----------------------------------------------------------------------------------------------- animations

TAIL = [f"tail_{i}" for i in range(1, 7)]
ZERO = (0, 0, 0)


def wave(a, bone, length, amplitude, phase, axis=1, points=8):
    """A smooth loop: amplitude * sin over the animation's length, shifted by phase (radians)."""
    keys = []
    for i in range(points + 1):
        t = length * i / points
        v = [0.0, 0.0, 0.0]
        v[axis] = round(amplitude * math.sin(2 * math.pi * i / points - phase), 3)
        keys.append((round(t, 4), tuple(v)))
    a.rot(bone, keys)


def mirror(v):
    """The same pose for the left side: turns about Y and Z flip."""
    return (v[0], -v[1], -v[2])


def arms(a, keys, forearm=None, hand=None, fingers=None):
    """Both arms alike (mirrored); keys are the right arm's."""
    a.rot("right_arm", keys)
    a.rot("left_arm", [(t, mirror(v)) for t, v in keys])
    if forearm:
        a.rot("right_forearm", forearm)
        a.rot("left_forearm", [(t, mirror(v)) for t, v in forearm])
    if hand:
        a.rot("right_hand", hand)
        a.rot("left_hand", [(t, mirror(v)) for t, v in hand])
    if fingers:
        a.rot("right_fingers", fingers)
        a.rot("left_fingers", [(t, mirror(v)) for t, v in fingers])


def curl(amount):
    """The right hand's fingers curled toward the palm by this many degrees (negative spreads them)."""
    return (0, 0, -amount)


def animations(m):
    idle = m.animation("idle", 4.0, loop=True)
    idle.pos("torso", [(0, ZERO), (2, (0, 0.6, 0)), (4, ZERO)])
    idle.rot("head", [(0, ZERO), (1, (-2, 8, 0)), (2, ZERO), (3, (-2, -8, 0)), (4, ZERO)])
    idle.rot("jaw", [(0, ZERO), (2, (-3, 0, 0)), (4, ZERO)])
    idle.rot("waist", [(0, ZERO), (1, (0, 0, 1.5)), (2, ZERO), (3, (0, 0, -1.5)), (4, ZERO)])
    idle.rot("right_arm", [(0, ZERO), (2, (2, 0, 3)), (4, ZERO)])
    idle.rot("left_arm", [(0, ZERO), (2, (2, 0, -3)), (4, ZERO)])
    idle.rot("coils", [(0, ZERO), (1, (0, 1.5, 0)), (2, ZERO), (3, (0, -1.5, 0)), (4, ZERO)])
    idle.rot("beard", [(0, ZERO), (1.2, (2, 0, -1.5)), (2.2, (1, 0, 0)), (3.2, (2, 0, 1.5)), (4, ZERO)])
    idle.rot("beard_tip", [(0, ZERO), (1.5, (3, 0, -2)), (2.5, (1, 0, 0)), (3.5, (3, 0, 2)), (4, ZERO)])
    idle.rot("right_fingers", [(0, curl(5)), (2, curl(15)), (4, curl(5))])
    idle.rot("left_fingers", [(0, mirror(curl(5))), (2, mirror(curl(15))), (4, mirror(curl(5)))])
    for i, bone in enumerate(TAIL):
        wave(idle, bone, 4.0, 4 + i, 0.7 * i)

    # rising out of his coils: bowed and wrapped, he straightens, spreads his arms and roars
    intro = m.animation("intro", 6.0)
    intro.pos("waist", [(0, (0, -20, 0)), (0.6, (0, -20, 0)), (2.6, ZERO)])
    intro.rot("waist", [(0, (-35, 0, 0)), (0.6, (-35, 0, 0)), (2.6, ZERO)])
    intro.rot("head", [(0, (-35, 0, 0)), (2.4, (-10, 0, 0)), (3.0, (25, 0, 0)), (4.4, (25, 0, 0)), (5.4, ZERO)])
    intro.rot("jaw", [(0, ZERO), (2.8, ZERO), (3.1, (-35, 0, 0)), (3.6, (-30, 0, 0)), (4.0, (-36, 0, 0)), (4.4, (-32, 0, 0)), (4.9, ZERO)])
    arms(intro, [(0, (40, 0, -10)), (2.4, (20, 0, 0)), (3.1, (-20, 0, 75)), (4.4, (-20, 0, 72)), (5.4, ZERO)],
         forearm=[(0, (60, 0, 0)), (3.1, (-10, 0, 0)), (5.4, ZERO)],
         fingers=[(0, curl(40)), (2.4, curl(30)), (3.1, curl(-20)), (4.4, curl(-15)), (5.4, ZERO)])
    intro.rot("beard", [(0, (-20, 0, 0)), (2.6, (-5, 0, 0)), (3.1, (-12, 0, 0)), (4.4, (-10, 0, 0)), (5.4, ZERO)])
    for i, bone in enumerate(TAIL):
        intro.rot(bone, [(0, (0, 30, 0)), (2.6, (0, 30, 0)), (3.4, (0, -25 - 3 * i, 0)), (4.2, (0, 12, 0)), (5.4, ZERO)])
    intro.rot("coils", [(0, (0, -20, 0)), (2.6, (0, -10, 0)), (5.4, ZERO)])
    intro.event(0.6, "rise").event(3.1, "roar")

    # a claw brought down from on high: the white flash is the moment to raise a shield
    strike = m.animation("strike", 1.6)
    strike.rot("waist", [(0, ZERO), (0.6, (8, 0, 0)), (0.95, (-14, 0, 0)), (1.6, ZERO)])
    strike.rot("torso", [(0, ZERO), (0.6, (0, -20, 0)), (0.95, (0, 25, 0)), (1.6, ZERO)])
    strike.rot("right_arm", [(0, ZERO), (0.6, (165, 0, 20)), (0.95, (55, 0, 0)), (1.2, (50, 0, 0)), (1.6, ZERO)])
    strike.rot("right_forearm", [(0, ZERO), (0.6, (30, 0, 0)), (0.95, (-5, 0, 0)), (1.6, ZERO)])
    strike.rot("right_hand", [(0, ZERO), (0.6, (-30, 0, 0)), (0.95, (40, 0, 0)), (1.6, ZERO)])
    strike.rot("right_fingers", [(0, ZERO), (0.6, curl(-20)), (0.85, curl(-10)), (1.05, curl(55)), (1.6, ZERO)])
    strike.rot("beard", [(0, ZERO), (0.6, (-6, 0, 0)), (0.95, (10, 0, 0)), (1.3, (-3, 0, 0)), (1.6, ZERO)])
    strike.rot("head", [(0, ZERO), (0.6, (10, -10, 0)), (0.95, (-10, 10, 0)), (1.6, ZERO)])
    strike.rot("jaw", [(0, ZERO), (0.6, (-15, 0, 0)), (1.0, (-5, 0, 0)), (1.6, ZERO)])
    strike.event(0.45, "flash").event(0.95, "hit")

    # the Tail of Judgement: wound back, then a full turn along the ground (purple: it can't be blocked, jump it)
    tail = m.animation("tail", 3.0)
    tail.rot("tail_1", [(0, ZERO), (1.2, (0, -50, 0)), (1.8, (0, 330, 0)), (2.2, (0, 360, 0)), (3.0, (0, 360, 0))])
    for bone in TAIL[1:]:
        tail.rot(bone, [(0, ZERO), (1.2, (0, -20, 0)), (1.5, (0, -38, 0)), (1.8, (0, -30, 0)), (2.5, ZERO)])
    tail.rot("coils", [(0, ZERO), (1.2, (0, -15, 0)), (1.8, (0, 30, 0)), (2.6, ZERO)])
    tail.rot("waist", [(0, ZERO), (1.2, (0, -20, 0)), (1.6, (0, 25, -4)), (2.4, ZERO)])
    arms(tail, [(0, ZERO), (1.0, (0, 0, 40)), (1.8, (10, 0, 45)), (2.4, ZERO)])
    tail.rot("head", [(0, ZERO), (1.0, (-10, 0, 0)), (2.4, ZERO)])
    tail.rot("jaw", [(0, ZERO), (1.2, (-20, 0, 0)), (2.0, ZERO)])
    tail.event(1.2, "sweep").event(1.8, "swept")

    # Sentence: he points out a soul, names its circle, and brings both fists down
    sentence = m.animation("sentence", 3.6)
    sentence.rot("right_arm", [(0, ZERO), (0.5, (90, -10, 10)), (2.4, (90, -10, 10)), (2.8, (170, 0, 15)), (3.0, (60, 0, 10)), (3.6, ZERO)])
    sentence.rot("right_forearm", [(0, ZERO), (0.5, (-25, 0, 0)), (2.4, (-25, 0, 0)), (2.8, (20, 0, 0)), (3.0, (-5, 0, 0)), (3.6, ZERO)])
    sentence.rot("right_hand", [(0, ZERO), (0.5, (-10, 0, 0)), (2.4, (-10, 0, 0)), (3.6, ZERO)])
    sentence.rot("left_arm", [(0, ZERO), (2.4, ZERO), (2.8, (170, 0, -15)), (3.0, (60, 0, -10)), (3.6, ZERO)])
    sentence.rot("right_fingers", [(0, ZERO), (0.5, curl(-10)), (2.4, curl(-10)), (2.7, curl(80)), (3.2, curl(80)),
                                   (3.6, ZERO)])
    sentence.rot("left_fingers", [(0, ZERO), (2.4, ZERO), (2.7, mirror(curl(80))), (3.2, mirror(curl(80))),
                                  (3.6, ZERO)])
    sentence.rot("head", [(0, ZERO), (0.5, (5, 0, 0)), (2.4, (5, 0, 0)), (3.0, (-15, 0, 0)), (3.6, ZERO)])
    sentence.rot("jaw", [(0, ZERO), (0.5, (-12, 0, 0)), (1.0, (-3, 0, 0)), (1.5, (-14, 0, 0)), (2.0, (-3, 0, 0)), (2.8, (-25, 0, 0)), (3.2, ZERO)])
    sentence.rot("torso", [(0, ZERO), (2.8, (8, 0, 0)), (3.0, (-12, 0, 0)), (3.6, ZERO)])
    sentence.event(2.4, "lock").event(3.0, "hit")

    # the tail whips round in front of him and closes on a soul
    grab = m.animation("grab", 1.4)
    grab.rot("tail_1", [(0, ZERO), (0.8, (0, -40, 0)), (1.0, (0, 150, 0)), (1.4, (0, 150, 0))])
    for bone in TAIL[1:]:
        grab.rot(bone, [(0, ZERO), (0.8, (0, -10, 0)), (1.0, (0, 10, 0)), (1.4, (0, 25, 0))])
    grab.rot("torso", [(0, ZERO), (0.8, (0, 15, 0)), (1.0, (-8, -10, 0)), (1.4, (-8, -10, 0))])
    grab.rot("right_arm", [(0, ZERO), (1.0, (60, 0, 10)), (1.4, (60, 0, 10))])
    grab.rot("right_fingers", [(0, ZERO), (0.8, curl(-15)), (1.0, curl(60)), (1.4, curl(60))])
    grab.rot("head", [(0, ZERO), (1.0, (-15, 0, 0)), (1.4, (-15, 0, 0))])
    grab.event(0.8, "lunge").event(1.0, "catch")

    hold = m.animation("hold", 1.0, loop=True)
    hold.rot("tail_1", [(0, (0, 150, 0)), (0.5, (0, 145, 0)), (1.0, (0, 150, 0))])
    for bone in TAIL[1:]:
        hold.rot(bone, [(0, (0, 25, 0)), (0.5, (0, 32, 0)), (1.0, (0, 25, 0))])
    hold.rot("torso", [(0, (-8, -10, 0)), (0.5, (-11, -10, 0)), (1.0, (-8, -10, 0))])
    hold.rot("right_arm", [(0, (60, 0, 10)), (0.5, (70, 0, 10)), (1.0, (60, 0, 10))])
    hold.rot("right_fingers", [(0, curl(60)), (0.5, curl(70)), (1.0, curl(60))])
    hold.rot("head", [(0, (-15, 0, 0)), (0.5, (-15, 0, 0)), (1.0, (-15, 0, 0))])
    hold.rot("jaw", [(0, ZERO), (0.5, (-15, 0, 0)), (1.0, ZERO)])

    release = m.animation("release", 0.8)
    release.rot("tail_1", [(0, (0, 150, 0)), (0.8, ZERO)])
    for bone in TAIL[1:]:
        release.rot(bone, [(0, (0, 25, 0)), (0.8, ZERO)])
    release.rot("torso", [(0, (-8, -10, 0)), (0.8, ZERO)])
    release.rot("right_arm", [(0, (60, 0, 10)), (0.8, ZERO)])
    release.rot("right_fingers", [(0, curl(60)), (0.3, curl(-15)), (0.8, ZERO)])
    release.rot("head", [(0, (-15, 0, 0)), (0.8, ZERO)])

    # parried: thrown back off balance, open to blows for a moment
    stagger = m.animation("stagger", 2.0)
    stagger.rot("waist", [(0, ZERO), (0.15, (18, 0, 0)), (0.6, (12, 0, -5)), (1.2, (14, 0, 5)), (2.0, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.15, (30, 0, 0)), (0.6, (20, 15, 0)), (1.2, (20, -15, 0)), (2.0, ZERO)])
    stagger.rot("jaw", [(0, ZERO), (0.15, (-25, 0, 0)), (1.0, (-20, 0, 0)), (1.6, ZERO)])
    arms(stagger, [(0, ZERO), (0.15, (-30, 0, 60)), (1.4, (-20, 0, 50)), (2.0, ZERO)],
         forearm=[(0, ZERO), (0.15, (-10, 0, 0)), (2.0, ZERO)],
         fingers=[(0, ZERO), (0.15, curl(-25)), (1.4, curl(-10)), (2.0, ZERO)])
    stagger.rot("beard", [(0, ZERO), (0.15, (-15, 0, 0)), (0.6, (-5, 0, 4)), (1.2, (-6, 0, -4)), (2.0, ZERO)])
    for bone in TAIL:
        stagger.rot(bone, [(0, ZERO), (0.15, (0, -15, 0)), (2.0, ZERO)])

    # at half his health: he rears up and roars, and his tail lashes out
    phase = m.animation("phase", 3.2)
    phase.pos("waist", [(0, ZERO), (0.4, (0, -6, 0)), (1.0, (0, 4, 0)), (2.6, (0, 4, 0)), (3.2, ZERO)])
    phase.rot("torso", [(0, ZERO), (0.4, (-15, 0, 0)), (1.0, (15, 0, 0)), (2.6, (15, 0, 0)), (3.2, ZERO)])
    phase.rot("head", [(0, ZERO), (1.0, (35, 0, 0)), (2.6, (35, 0, 0)), (3.2, ZERO)])
    phase.rot("jaw", [(0, ZERO), (1.0, (-40, 0, 0)), (2.6, (-40, 0, 0)), (3.0, ZERO)])
    arms(phase, [(0, ZERO), (1.0, (12, 0, 120)), (1.4, (8, 0, 126)), (1.8, (14, 0, 118)), (2.2, (9, 0, 125)), (2.6, (12, 0, 120)), (3.2, ZERO)],
         forearm=[(0, ZERO), (1.0, (-20, 0, 0)), (2.6, (-20, 0, 0)), (3.2, ZERO)],
         fingers=[(0, ZERO), (1.0, curl(-25)), (2.6, curl(-25)), (3.2, ZERO)])
    phase.rot("beard", [(0, ZERO), (1.0, (-18, 0, 0)), (1.4, (-14, 0, 2)), (2.2, (-16, 0, -2)), (2.6, (-15, 0, 0)),
                        (3.2, ZERO)])
    for bone in TAIL:
        phase.rot(bone, [(0, ZERO), (0.4, (0, 30, 0)), (1.2, (0, -30, 0)), (2.0, (0, 20, 0)), (3.2, ZERO)])
    phase.rot("coils", [(0, ZERO), (1.0, (0, 20, 0)), (3.2, ZERO)])
    phase.event(1.0, "roar")

    hurt = m.animation("hurt", 0.35)
    hurt.rot("torso", [(0, ZERO), (0.1, (6, 0, 0)), (0.35, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (8, 0, 0)), (0.35, ZERO)])

    # judged: he cries out, then slumps forward and sinks into his coils
    death = m.animation("death", 3.5)
    death.rot("waist", [(0, ZERO), (0.5, (10, 0, 0)), (1.6, (-38, 0, 8)), (3.5, (-42, 0, 10))])
    death.pos("waist", [(0, ZERO), (1.6, (0, -6, 0)), (3.5, (0, -10, 0))])
    death.rot("head", [(0, ZERO), (0.5, (30, 0, 0)), (1.6, (-30, 0, 10)), (3.5, (-35, 0, 15))])
    death.rot("jaw", [(0, ZERO), (0.5, (-35, 0, 0)), (1.6, (-25, 0, 0)), (3.5, (-25, 0, 0))])
    arms(death, [(0, ZERO), (0.5, (-20, 0, 60)), (1.6, (10, 0, 15)), (3.5, (15, 0, 10))],
         forearm=[(0, ZERO), (1.6, (-20, 0, 0)), (3.5, (-20, 0, 0))],
         fingers=[(0, ZERO), (0.5, curl(-25)), (1.6, curl(30)), (3.5, curl(35))])
    death.rot("beard", [(0, ZERO), (0.5, (-15, 0, 0)), (1.6, (20, 0, 0)), (3.5, (25, 0, 0))])
    for bone in TAIL:
        death.rot(bone, [(0, ZERO), (0.5, (0, -20, 0)), (1.6, (0, -28, 0)), (3.5, (0, -28, 0))])
    death.pos("coils", [(0, ZERO), (1.6, (0, -4, 0)), (3.5, (0, -6, 0))])
    death.event(1.6, "fall")


if __name__ == "__main__":
    build().write()
