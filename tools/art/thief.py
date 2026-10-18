#!/usr/bin/env python3
"""The thieves (Malebolge's monster): "among this cruel and most dismal throng people were running naked and
affrighted, with serpents were their hands behind them bound" (Inferno XXIV). Lean grey souls with a serpent coiled
round their chest and hissing over a shoulder, a sack slung on the back. They snatch something from you and run;
kill one to get it back.

Run from the repo root:  python3 tools/art/thief.py && python3 tools/gen_bosses.py

The body is a starved man's: a ribcage you can count, a pinched waist over a bony pelvis, stringy arms in tapered
segments (deltoid, upper arm, elbow, forearm, wrist) ending in long-fingered grasping hands, knobbly knees and bare
feet with toes. He wears only a loincloth and a ragged hood that shadows his eyes. The serpent winds twice round his
chest and waist, climbs his back and rears over his right shoulder, jaws open. The sack hangs from a strap across
his chest.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="7a8070", dark="4a5044")
SERPENT = material("serpent", "scales", base="3a6a2a", edge="6a9a3a", belly="b0a060", belly_face="bottom")
SACK = material("sack", "robe", base="6a5a3a", fold="4a3a22")
STRAP = material("strap", "flat", base="3a2a1a")
HOOD = material("hood", "robe", base="2a2a26", fold="141410")
CLOTH = material("cloth", "robe", base="4a4636", fold="2e2c22")
EYE = hexc("c8ff3a")
SNAKE_EYE = hexc("ffd23a")
FANG = hexc("f0ece0")
MOUTH = hexc("1e1a14")
MAW = hexc("8a2a2a")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- helpers

def side_x(a, w, s):
    """A cube from a to a + w on the right becomes -(a + w) to -a on the left."""
    return a if s == 1 else -(a + w)


def seg(bone, name, p0, p1, w, h, mat, extra=0.0, **kw):
    """A cube laid along the line p0 -> p1 (its length on its own Z axis), w across and h thick, turned about its own
    centre; extra lengthens it so that segments overlap at their joints."""
    d = [p1[i] - p0[i] for i in range(3)]
    length = math.sqrt(sum(v * v for v in d))
    size = max(1, math.ceil(length + extra - 1e-6))
    c = [round((p0[i] + p1[i]) / 2, 3) for i in range(3)]
    pitch = -math.degrees(math.asin(max(-1.0, min(1.0, d[1] / length))))
    yaw = math.degrees(math.atan2(d[0], d[2]))
    return bone.cube(name, (c[0] - w / 2, c[1] - h / 2, c[2] - size / 2), (w, h, size), mat,
                     rotation=(round(pitch, 3), round(yaw, 3), 0), origin=tuple(c), **kw)


def mul(face, fx, fy, f):
    """Darkens (f < 1) or lightens one already painted pixel."""
    if 0 <= fx < face.w and 0 <= fy < face.h:
        c = face.canvas.get(face.x + fx, face.y + fy)
        if c[3]:
            face.px(fx, fy, shade(c, f))


def ao(bottom=0, top=0, dark=0.7, faces=SIDES):
    """Side faces shaded along their lowest rows (where a part sinks into shadow) and their highest (where it tucks
    under the part above)."""
    def detail(fs):
        for n in faces:
            f = fs[n]
            for i in range(min(bottom, f.h)):
                for fx in range(f.w):
                    mul(f, fx, f.h - 1 - i, dark + (1 - dark) * i / max(1, bottom))
            for i in range(min(top, f.h)):
                for fx in range(f.w):
                    mul(f, fx, i, dark + (1 - dark) * i / max(1, top))
    return detail


def both(*details):
    def detail(fs):
        for d in details:
            d(fs)
    return detail


# ----------------------------------------------------------------------------------------------- details

def face_detail(fs):
    """Sunken sockets under the hood with sickly eyes, hollow cheeks, a thin mouth line on the jaw below."""
    f = fs["front"]
    dark = shade(SKIN.c["dark"], 0.55)
    for x in range(f.w):
        f.px(x, 0, shade(HOOD.c["base"], 0.6))  # the hood's shadow on the forehead
        mul(f, x, 1, 0.65)
    for cx in (1, f.w - 2):
        f.px(cx, 2, dark)
        f.glow(cx, 3, EYE)
    for y in (3, 4):
        mul(f, 0, y, 0.78)
        mul(f, f.w - 1, y, 0.78)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            for x in range(g.w):
                mul(g, x, y, 0.9)


def brow_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["base"], 1.12))
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.45))


def nose_detail(fs):
    f = fs["front"]
    mul(f, 0, 0, 1.15)
    fs["bottom"].fill(lambda x, y: MOUTH)


def jaw_detail(fs):
    """A thin, pinched mouth and the shadow under the jaw."""
    f = fs["front"]
    for x in range(1, f.w - 1):
        f.px(x, 0, MOUTH)
    mul(f, 0, 0, 0.75)
    mul(f, f.w - 1, 0, 0.75)
    for n in ("bottom", "left", "right"):
        g = fs[n]
        for y in range(g.h):
            for x in range(g.w):
                mul(g, x, y, 0.75)


def ear_detail(fs):
    for n in ("left", "right"):
        mul(fs[n], 0, 1, 0.6)


def rib_detail(fs):
    """A ribcage you can count: dark lines between the ribs arching round the front and flanks, the breastbone."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid, y, 0.82)
    for y in (1, 3):
        for x in range(f.w):
            if abs(x - mid) > 0:
                mul(f, x, min(f.h - 1, y + abs(x - mid) // 3), 0.72)
    for n in ("left", "right"):
        g = fs[n]
        for y in (0, 2):
            for x in range(g.w):
                mul(g, x, y + (x % 2), 0.74)
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.7)


def belly_detail(fs):
    """A hollow belly: the line down the middle, the navel, the hip bones' shadow."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid, y, 0.82)
    f.px(mid, f.h - 2, MOUTH)
    for y in range(f.h):
        mul(f, 0, y, 0.8)
        mul(f, f.w - 1, y, 0.8)


def pec_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.7)
        mul(f, x, 0, 1.08)


def collar_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.15)


def knee_detail(fs):
    f = fs["front"]
    mul(f, 0, f.h - 1, 0.75)
    mul(f, f.w - 1, f.h - 1, 0.75)
    mul(f, 0, 0, 1.1)


def hand_detail(fs):
    """Knuckles and the dark nails of grasping fingers."""
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.6)


def toe_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 0.75)
    for x in range(1, f.w, 2):
        mul(f, x, 0, 0.6)


def hood_detail(fs):
    """Ragged hems: a few torn pixels along the lower edge, the inside in shadow."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            if (x * 7 + f.h) % 3 == 0:
                mul(f, x, f.h - 1, 0.55)
    fs["bottom"].fill(lambda x, y: shade(HOOD.c["fold"], 0.8))


def sack_detail(fs):
    """Patches and coarse stitching on the sack, a bulge of loot."""
    f = fs["back"]
    for y in range(1, f.h - 1):
        if y % 2:
            f.px(1, y, shade(SACK.c["base"], 1.25))
    for x in range(2, f.w - 1):
        f.px(x, 2, shade(SACK.c["fold"], 0.8))
    f.px(f.w - 2, f.h - 3, hexc("c8a040"))  # a coin poking through a hole


def strap_detail(fs):
    for n in SIDES:
        f = fs[n]
        for y in range(0, f.h, 3):
            mul(f, 0, y, 1.3)


def snake_head(fs):
    """Yellow eyes on the sides of the head, nostrils at the tip, a darker crown."""
    for n in ("left", "right"):
        f = fs[n]
        ex = f.w - 2 if n == "right" else 1
        f.glow(ex, 0, SNAKE_EYE)
    t = fs["top"]
    for x in range(t.w):
        for y in range(t.h):
            if (x + y) % 2:
                mul(t, x, y, 0.7)
    fs["bottom"].fill(lambda x, y: MAW)


def snake_jaw(fs):
    """The open mouth: red inside, fangs at the front."""
    fs["top"].fill(lambda x, y: MAW if y < 2 else FANG)
    fs["front"].fill(lambda x, y: shade(SERPENT.c["base"], 0.7))


def tongue_detail(fs):
    for n in fs:
        fs[n].fill(lambda x, y: hexc("b02a3a"))


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("thief", texture_width=128)

    # --- the legs: bony thighs, knobbly knees, stringy shins, bare feet with toes
    for side, s in (("right", 1), ("left", -1)):
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        leg.cube(f"{side}_thigh", (side_x(0.5, 3, s), 6, -1.5), (3, 7, 3), SKIN, mirror=s == -1, detail=ao(top=2, dark=0.75))
        shin = leg.bone(f"{side}_shin", (2 * s, 6.5, -0.3))
        shin.cube(f"{side}_knee", (side_x(1, 2, s), 5, -1.9), (2, 2, 1), SKIN, mirror=s == -1, detail=knee_detail)
        shin.cube(f"{side}_shinbone", (side_x(1, 2, s), 1, -1.2), (2, 6, 2), SKIN, mirror=s == -1, detail=ao(bottom=1, dark=0.8))
        shin.cube(f"{side}_calf", (side_x(1, 2, s), 3.5, 0.5), (2, 3, 1), SKIN, mirror=s == -1, detail=ao(bottom=1))
        shin.cube(f"{side}_foot", (side_x(0.6, 3, s), 0, -2.6), (3, 1, 4), SKIN, mirror=s == -1)
        shin.cube(f"{side}_big_toe", (side_x(0.6, 1, s), 0, -3.5), (1, 1, 1), SKIN, mirror=s == -1, detail=toe_detail)
        shin.cube(f"{side}_toes", (side_x(1.7, 2, s), 0, -3.3), (2, 1, 1), SKIN, mirror=s == -1, detail=toe_detail)

    # --- the body, hunched: a bony pelvis, a pinched waist, a ribcage you can count
    body = m.bone("body", (0, 12, 0), rotation=(-12, 0, 0))
    body.cube("pelvis", (-3, 11.5, -2), (6, 3, 4), SKIN, detail=ao(bottom=1))
    body.cube("waist", (-2.5, 14, -1.6), (5, 4, 3), SKIN, detail=belly_detail)
    body.cube("ribcage", (-3.5, 17.5, -2.2), (7, 4, 4), SKIN, detail=rib_detail)
    body.cube("shoulders", (-3, 21.2, -1.7), (6, 1, 3), SKIN)
    for side, s in (("right", 1), ("left", -1)):
        body.cube(f"{side}_pec", (side_x(0.3, 3, s), 19.6, -2.6), (3, 2, 1), SKIN, mirror=s == -1, detail=pec_detail)
        body.cube(f"{side}_collarbone", (side_x(0.4, 3, s), 21.1, -2.4), (3, 1, 1), SKIN, mirror=s == -1,
                  rotation=(0, 0, 8 * s), origin=(0, 21.5, -2), detail=collar_detail)
    # the loincloth, its cord, and the flaps front and back
    body.cube("cord", (-3, 13.4, -2), (6, 1, 4), STRAP, inflate=0.2)
    body.cube("loin_front", (-2, 9.6, -2.4), (4, 4, 1), CLOTH, rotation=(-6, 0, 0), origin=(0, 13.5, -2), detail=hood_detail)
    body.cube("loin_back", (-2, 10.2, 1.5), (4, 3, 1), CLOTH, rotation=(8, 0, 0), origin=(0, 13.5, 2), detail=hood_detail)
    # the sack on his back, tied at the neck, on a strap across the chest
    body.cube("sack", (-2.5, 14.5, 2.1), (5, 6, 4), SACK, rotation=(-8, 0, 0), origin=(0, 17, 3), detail=sack_detail)
    body.cube("sack_belly", (-2, 15, 5.4), (4, 4, 1), SACK)
    body.cube("sack_neck", (-1, 20.2, 3), (2, 1, 2), SACK)
    body.cube("sack_tie", (-1.5, 21, 2.5), (3, 1, 3), SACK, rotation=(0, 20, 0), origin=(0, 21.5, 4))
    body.cube("strap", (-0.5, 11.5, -3.1), (1, 13, 1), STRAP, rotation=(0, 0, -38), origin=(0, 18, -2.6), detail=strap_detail)
    body.cube("strap_back", (-0.5, 16, 1.8), (1, 8, 1), STRAP, rotation=(0, 0, 30), origin=(0, 20, 2.3))

    # --- the serpent's coils: one round the chest, one round the waist, winding up the back
    body.cube("coil_low_front", (-3.5, 14.3, -2.6), (7, 2, 1), SERPENT, rotation=(0, 0, 8), origin=(0, 15.3, -2.1))
    body.cube("coil_low_back", (-3.5, 15.1, 1.4), (7, 2, 1), SERPENT, rotation=(0, 0, -8), origin=(0, 16.1, 1.9))
    body.cube("coil_low_right", (2.5, 14.8, -1.6), (1, 2, 3), SERPENT)
    body.cube("coil_low_left", (-3.5, 14.5, -1.6), (1, 2, 3), SERPENT)
    body.cube("coil_high_front", (-4.5, 18.4, -3.2), (9, 2, 1), SERPENT, rotation=(0, 0, -7), origin=(0, 19.4, -2.7))
    body.cube("coil_high_back", (-4.5, 19.2, 1.8), (9, 2, 1), SERPENT, rotation=(0, 0, 7), origin=(0, 20.2, 2.3))
    body.cube("coil_high_right", (3.5, 18.9, -2.2), (1, 2, 4), SERPENT)
    body.cube("coil_high_left", (-4.5, 18.9, -2.2), (1, 2, 4), SERPENT)

    # --- the serpent rearing over his right shoulder, jaws open
    serpent = body.bone("serpent", (3, 22, 0))
    seg(serpent, "serpent_neck_low", (3.8, 19.5, 2.3), (4.4, 23, 1.6), 2, 2, SERPENT, extra=1)
    seg(serpent, "serpent_neck_mid", (4.4, 23, 1.6), (4.6, 26, 0.2), 2, 2, SERPENT, extra=1)
    seg(serpent, "serpent_neck_high", (4.6, 26, 0.2), (4.5, 27.3, -1.6), 2, 2, SERPENT, extra=0.5)
    serpent.cube("serpent_head", (3.5, 26.8, -4.4), (2, 2, 3), SERPENT, detail=snake_head)
    serpent.cube("serpent_snout", (3.5, 27.2, -5.4), (2, 1, 1), SERPENT)
    jaw = serpent.bone("serpent_jaw", (4.5, 26.9, -2), rotation=(-20, 0, 0))
    jaw.cube("serpent_jaw", (3.6, 26.1, -5), (2, 1, 3), SERPENT, detail=snake_jaw)
    jaw.cube("serpent_tongue", (4, 26.4, -6.4), (1, 1, 2), SERPENT, inflate=-0.3, detail=tongue_detail)

    # --- the head: a gaunt face in the shadow of a ragged hood
    head = body.bone("head", (0, 23, 0))
    head.cube("neck", (-1, 22, -1.2), (2, 3, 2), SKIN, detail=ao(top=1, dark=0.6))
    head.cube("skull", (-2.5, 24.5, -2.5), (5, 5, 5), SKIN, detail=face_detail)
    head.cube("brow", (-2.5, 27, -3.1), (5, 1, 1), SKIN, detail=brow_detail)
    head.cube("nose", (-0.5, 25.4, -3.3), (1, 2, 1), SKIN, rotation=(-10, 0, 0), origin=(0, 27, -2.8), detail=nose_detail)
    head.cube("jaw", (-2, 23.4, -2.7), (4, 2, 4), SKIN, detail=jaw_detail)
    for side, s in (("right", 1), ("left", -1)):
        head.cube(f"{side}_cheek", (side_x(1.2, 1, s), 25.6, -2.9), (1, 1, 1), SKIN, mirror=s == -1)
        head.cube(f"{side}_ear", (side_x(2.4, 1, s), 25.5, -0.6), (1, 2, 1), SKIN, mirror=s == -1, detail=ear_detail)
        head.cube(f"{side}_hood", (side_x(2.6, 1, s), 23.8, -2.4), (1, 6, 5), HOOD, mirror=s == -1, detail=hood_detail)
    head.cube("hood_top", (-3, 29.2, -2.6), (6, 1, 5), HOOD, inflate=0.1, detail=hood_detail)
    head.cube("hood_crown", (-2, 28.6, 1.6), (4, 2, 2), HOOD, rotation=(-20, 0, 0), origin=(0, 29.5, 1.5), detail=hood_detail)
    head.cube("hood_peak", (-3, 28.6, -3.6), (6, 1, 2), HOOD, rotation=(12, 0, 0), origin=(0, 29.4, -2.6), detail=hood_detail)
    head.cube("hood_back", (-2.5, 23.4, 2.4), (5, 5, 1), HOOD, detail=hood_detail)
    head.cube("hood_tail", (-1.5, 19.8, 2.8), (3, 4, 1), HOOD, rotation=(-14, 0, 0), origin=(0, 23.5, 3), detail=hood_detail)

    # --- the arms: stringy, tapered, with long grasping hands
    for side, s in (("right", 1), ("left", -1)):
        arm = body.bone(f"{side}_arm", (4.5 * s, 22, 0))
        arm.cube(f"{side}_deltoid", (side_x(3.3, 3, s), 19.6, -1.5), (3, 3, 3), SKIN, mirror=s == -1)
        arm.cube(f"{side}_upper_arm", (side_x(3.7, 2, s), 15.8, -1), (2, 5, 2), SKIN, mirror=s == -1, detail=ao(top=1, dark=0.8))
        fore = arm.bone(f"{side}_forearm", (4.7 * s, 16.3, 0))
        fore.cube(f"{side}_elbow", (side_x(3.8, 2, s), 15.6, -0.5), (2, 1, 2), SKIN, inflate=0.1, mirror=s == -1)
        fore.cube(f"{side}_forearm", (side_x(3.7, 2, s), 12.3, -1), (2, 4, 2), SKIN, mirror=s == -1, detail=ao(bottom=1, dark=0.8))
        fore.cube(f"{side}_palm", (side_x(4.2, 1, s), 10.4, -1), (1, 2, 2), SKIN, inflate=0.15, mirror=s == -1)
        fore.cube(f"{side}_fingers_front", (side_x(4.2, 1, s), 8.7, -1.1), (1, 2, 1), SKIN, mirror=s == -1,
                  rotation=(10, 0, 0), origin=(4.7 * s, 10.5, -0.6), detail=hand_detail)
        fore.cube(f"{side}_fingers_back", (side_x(4.2, 1, s), 8.9, 0), (1, 2, 1), SKIN, mirror=s == -1,
                  rotation=(6, 0, 0), origin=(4.7 * s, 10.5, 0.5), detail=hand_detail)
        fore.cube(f"{side}_thumb", (side_x(3.6, 1, s), 10.1, -1.7), (1, 2, 1), SKIN, mirror=s == -1,
                  rotation=(20, 0, 0), origin=(4.1 * s, 11.5, -1.2), detail=hand_detail)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # crouched and shifty, the serpent swaying, fingers ready
    idle = m.animation("idle", 1.4, loop=True)
    idle.rot("head", [(0, (0, -25, 0)), (0.7, (0, 25, 0)), (1.4, (0, -25, 0))])
    idle.rot("serpent", [(0, (0, 0, -10)), (0.7, (10, 0, 10)), (1.4, (0, 0, -10))])
    idle.rot("serpent_jaw", [(0, ZERO), (0.35, (-15, 0, 0)), (0.7, ZERO), (1.05, (-15, 0, 0)), (1.4, ZERO)])
    idle.rot("right_arm", [(0, (30, 0, 0)), (1.4, (30, 0, 0))])
    idle.rot("left_arm", [(0, (30, 0, 0)), (1.4, (30, 0, 0))])
    idle.rot("right_forearm", [(0, (35, 0, 0)), (0.7, (45, 0, 0)), (1.4, (35, 0, 0))])
    idle.rot("left_forearm", [(0, (45, 0, 0)), (0.7, (35, 0, 0)), (1.4, (45, 0, 0))])

    # a quick, low run: knees snapping up behind
    walk = m.animation("walk", 0.5, loop=True)
    walk.rot("right_leg", [(0, (45, 0, 0)), (0.25, (-45, 0, 0)), (0.5, (45, 0, 0))])
    walk.rot("left_leg", [(0, (-45, 0, 0)), (0.25, (45, 0, 0)), (0.5, (-45, 0, 0))])
    walk.rot("right_shin", [(0, (-10, 0, 0)), (0.125, (-30, 0, 0)), (0.25, (-70, 0, 0)), (0.375, (-40, 0, 0)), (0.5, (-10, 0, 0))])
    walk.rot("left_shin", [(0, (-70, 0, 0)), (0.125, (-40, 0, 0)), (0.25, (-10, 0, 0)), (0.375, (-30, 0, 0)), (0.5, (-70, 0, 0))])
    walk.rot("right_arm", [(0, (45, 0, 0)), (0.25, (-30, 0, 0)), (0.5, (45, 0, 0))])
    walk.rot("left_arm", [(0, (-30, 0, 0)), (0.25, (45, 0, 0)), (0.5, (-30, 0, 0))])
    walk.rot("right_forearm", [(0, (50, 0, 0)), (0.25, (25, 0, 0)), (0.5, (50, 0, 0))])
    walk.rot("left_forearm", [(0, (25, 0, 0)), (0.25, (50, 0, 0)), (0.5, (25, 0, 0))])
    walk.rot("body", [(0, (-15, 0, 0)), (0.5, (-15, 0, 0))])

    # the snatch: a quick grab, while the serpent strikes
    attack = m.animation("attack", 0.5)
    attack.rot("right_arm", [(0, (30, 0, 0)), (0.15, (100, -20, 0)), (0.3, (20, 30, 0)), (0.5, (30, 0, 0))])
    attack.rot("right_forearm", [(0, (35, 0, 0)), (0.15, (5, 0, 0)), (0.3, (80, 0, 0)), (0.5, (35, 0, 0))])
    attack.rot("serpent", [(0, ZERO), (0.15, (-40, 0, 0)), (0.25, (50, 0, 0)), (0.5, ZERO)])
    attack.rot("serpent_jaw", [(0, ZERO), (0.15, (-40, 0, 0)), (0.25, (5, 0, 0)), (0.5, ZERO)])
    attack.event(0.25, "snatch")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (15, 0, 0)), (0.3, ZERO)])
    hurt.rot("serpent_jaw", [(0, ZERO), (0.1, (-30, 0, 0)), (0.3, ZERO)])

    # off and away with the prize: the sack hugged close, a glance back
    flee = m.animation("flee", 0.8)
    flee.rot("head", [(0, ZERO), (0.3, (0, 150, 0)), (0.6, (0, 150, 0)), (0.8, ZERO)])
    flee.rot("right_arm", [(0, ZERO), (0.2, (60, 0, -40)), (0.8, (60, 0, -40))])
    flee.rot("left_arm", [(0, ZERO), (0.2, (60, 0, 40)), (0.8, (60, 0, 40))])
    flee.rot("right_forearm", [(0, ZERO), (0.2, (60, 0, 0)), (0.8, (60, 0, 0))])
    flee.rot("left_forearm", [(0, ZERO), (0.2, (60, 0, 0)), (0.8, (60, 0, 0))])

    # the serpent turns on him at the last
    death = m.animation("death", 1.2)
    death.rot("serpent", [(0, ZERO), (0.3, (60, 0, -40)), (0.6, (90, 0, -60)), (1.2, (90, 0, -60))])
    death.rot("serpent_jaw", [(0, ZERO), (0.3, (-45, 0, 0)), (0.6, (0, 0, 0)), (1.2, (0, 0, 0))])
    death.rot("body", [(0, ZERO), (0.6, (-50, 0, 0)), (1.2, (-80, 0, 0))])
    death.pos("body", [(0, ZERO), (0.6, (0, -6, -2)), (1.2, (0, -10, -3))])
    # the legs go down with the hips and trail out behind him
    death.rot("right_leg", [(0, ZERO), (0.6, (-45, 0, 0)), (1.2, (-78, 0, 0))])
    death.rot("left_leg", [(0, ZERO), (0.6, (-40, 0, 0)), (1.2, (-72, 0, 0))])
    death.pos("right_leg", [(0, ZERO), (0.6, (0, -6, -2)), (1.2, (0, -10, -3))])
    death.pos("left_leg", [(0, ZERO), (0.6, (0, -6, -2)), (1.2, (0, -10, -3))])
    death.rot("right_shin", [(0, ZERO), (0.6, (-30, 0, 0)), (1.2, (-10, 0, 0))])
    death.rot("left_shin", [(0, ZERO), (0.6, (-45, 0, 0)), (1.2, (-25, 0, 0))])


if __name__ == "__main__":
    build().write()
