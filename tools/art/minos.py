#!/usr/bin/env python3
"""Minos, Judge of the Damned (Inferno V): an old horned king rising out of the coils of his own serpent tail, in a
crimson judge's mantle, with a long white beard and eyes like coals. "He girds himself with his tail as many times
as grades he wills the soul be thrust down." The tail curls around him at rest and lashes out in his attacks.

Run from the repo root:  python3 tools/art/minos.py && python3 tools/gen_bosses.py

The animations' events are what the server times Minos's fight by (see MinosFight): "flash" opens the window for a
shield block, "hit" is when a blow lands, "sweep" .. "swept" is the tail's pass, and so on.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="8e8597", dark="62586c")
ROBE = material("robe", "robe", base="6a1222", fold="420a15", trim="d4a83a")
MANTLE = material("mantle", "robe", base="6a1222", fold="420a15", trim="d4a83a", opening=True)
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


# ----------------------------------------------------------------------------------------------- details

def face_detail(faces):
    """The skull's front: a deep brow shadow, eyes like coals, hollow cheeks and a grim mouth."""
    f = faces["front"]
    w = f.w
    for x in range(1, w - 1):
        f.px(x, 5, shade(SKIN.c["dark"], 0.8))  # the brow's shadow
    for cx in (4, w - 5):
        for dx in range(-2, 2):
            f.px(cx + dx, 6, SOCKET)
            f.px(cx + dx, 8, SOCKET)
        f.px(cx - 2, 7, SOCKET)
        f.px(cx + 1, 7, SOCKET)
        f.glow(cx - 1, 7, EYE)
        f.glow(cx, 7, EYE_HOT)
    for y in (9, 10, 11):
        f.px(2, y, shade(SKIN.c["dark"], 0.9))
        f.px(w - 3, y, shade(SKIN.c["dark"], 0.9))
    for x in range(4, w - 4):
        f.px(x, f.h - 1, MOUTH)
    for x in range(5, w - 5):
        f.px(x, f.h - 3, shade(SKIN.c["dark"], 0.7))  # a downturned lip
    f.px(3, f.h - 3, shade(SKIN.c["dark"], 0.8))
    f.px(w - 4, f.h - 3, shade(SKIN.c["dark"], 0.8))


def jaw_detail(faces):
    f = faces["front"]
    for x in range(1, f.w - 1):
        f.px(x, 0, MOUTH)
    for x in (3, f.w - 4):
        f.px(x, 0, TOOTH)
        f.px(x, 1, shade(TOOTH, 0.85))
    t = faces["top"]
    t.fill(lambda x, y: MOUTH)


def chest_detail(faces):
    """Old muscle under the mantle: the line between the pectorals and a few ribs."""
    f = faces["front"]
    mid = f.w // 2
    dark = shade(SKIN.c["dark"], 0.9)
    for y in range(2, 9):
        f.px(mid, y, dark)
    for x in range(3, mid - 1):
        f.px(x, 8, dark)
        f.px(f.w - 1 - x, 8, dark)
    for y in (11, 14, 17):
        for x in list(range(4, mid - 2)) + list(range(mid + 3, f.w - 4)):
            if (x + y) % 3:
                f.px(x, y, dark)


def crown_detail(faces):
    f = faces["front"]
    mid = f.w // 2
    f.px(mid - 1, 1, hexc("b0141e"))
    f.px(mid, 1, hexc("ff3a3a"))


def mantle_detail(faces):
    """A gold clasp where the mantle closes over the chest."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h - 5, f.h - 2):
        for x in range(mid - 2, mid + 2):
            f.px(x, y, hexc("f1d27a") if (x + y) % 2 else hexc("c9982e"))


def palm_detail(faces):
    f = faces["front"]
    for x in range(1, f.w - 1, 2):
        f.px(x, f.h - 2, shade(SKIN.c["dark"], 0.8))


# ----------------------------------------------------------------------------------------------- the body

def build():
    m = Model("minos", texture_width=256)

    # the coils of the serpent tail he stands in: three rings of segments, each laid along its ring
    coils = m.bone("coils", (0, 0, 0))
    rings = [  # radius, base, height, thickness, length, offset (degrees)
        (17, 0, 10, 12, 15, 0.0),
        (14, 9, 9, 11, 13, 22.5),
        (10, 17, 8, 10, 10, 0.0),
    ]
    for i, (radius, y0, height, thick, length, offset) in enumerate(rings):
        for k in range(8):
            a = math.radians(offset + k * 45)
            cx, cz = round(radius * math.cos(a), 3), round(radius * math.sin(a), 3)
            turn = (-(offset + k * 45) - 90) % 180
            coils.cube(f"coil_{i}_{k}", (cx - length / 2, y0, cz - thick / 2), (length, height, thick), SCALES,
                       rotation=(0, round(turn, 3), 0), origin=(cx, y0 + height / 2, cz))

    # the serpent body rising out of the coils into the man
    waist = m.bone("waist", (0, 26, 2))
    waist.cube("waist_body", (-10, 22, -8), (20, 20, 17), BELLY_FRONT)
    waist.cube("skirt", (-12, 32, -10), (24, 9, 21), ROBE, inflate=0.25)
    waist.cube("belt", (-12, 40, -10), (24, 3, 21), GOLD, inflate=0.3)

    torso = waist.bone("torso", (0, 42, 2))
    torso.cube("chest", (-14, 43, -8), (28, 20, 17), SKIN, detail=chest_detail)
    torso.cube("mantle", (-16, 54, -9), (32, 11, 20), MANTLE, inflate=0.5, detail=mantle_detail)
    torso.cube("cape", (-14, 24, 9), (28, 36, 3), ROBE)
    torso.cube("collar", (-10, 63, -7), (20, 3, 15), GOLD)

    head = torso.bone("head", (0, 65, -1))
    head.cube("skull", (-8, 65, -10), (16, 15, 16), SKIN, detail=face_detail)
    head.cube("brow", (-8, 73, -11), (16, 2, 3), SKIN)
    head.cube("nose", (-1, 67, -12), (2, 5, 2), SKIN)
    head.cube("hair_back", (-8, 57, 4), (16, 20, 4), HAIR, inflate=0.4)
    head.cube("crown", (-8, 78, -10), (16, 3, 16), GOLD, inflate=0.6, detail=crown_detail)
    head.cube("crown_spike_mid", (-1, 81, -11), (2, 6, 2), GOLD)
    head.cube("crown_spike_r", (4, 81, -11), (2, 4, 2), GOLD)
    head.cube("crown_spike_l", (-6, 81, -11), (2, 4, 2), GOLD)
    head.cube("crown_spike_back", (-1, 81, 5), (2, 4, 2), GOLD)
    for side, s in (("r", 1), ("l", -1)):
        # ram's horns sweeping out and up from the temples
        def x(a, w):  # a cube from a to a + w on the right becomes -(a + w) to -a on the left
            return a if s == 1 else -(a + w)
        head.cube(f"horn_{side}_1", (x(7, 5), 70, -6), (5, 5, 5), HORN, rotation=(0, 0, -15 * s), origin=(8 * s, 72, -3.5))
        head.cube(f"horn_{side}_2", (x(10, 4), 72, -5), (4, 7, 4), HORN, rotation=(0, 0, -38 * s), origin=(11 * s, 74, -3))
        head.cube(f"horn_{side}_3", (x(13, 3), 77, -4), (3, 7, 3), HORN, rotation=(-18, 0, -62 * s), origin=(14 * s, 78, -2.5))

    jaw = head.bone("jaw", (0, 66, -3))
    jaw.cube("jaw", (-7, 62, -10), (14, 4, 12), SKIN, detail=jaw_detail)
    jaw.cube("moustache", (-6, 64, -11), (12, 2, 2), HAIR)
    jaw.cube("beard", (-6, 52, -11), (12, 11, 3), HAIR)
    jaw.cube("beard_mid", (-4, 45, -10.5), (8, 7, 3), HAIR)
    jaw.cube("beard_tip", (-2, 40, -10), (4, 5, 2), HAIR)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        arm = torso.bone(f"{side}_arm", (15 * s, 60, 0), rotation=(0, 0, 8 * s))
        arm.cube(f"{side}_shoulder", (x(13, 11), 54, -6), (11, 10, 12), ROBE, inflate=0.5, mirror=mirror)
        arm.cube(f"{side}_upper", (x(15, 8), 41, -4), (8, 19, 8), SKIN, mirror=mirror)
        fore = arm.bone(f"{side}_forearm", (19 * s, 42, 0), rotation=(25, 0, 0))
        fore.cube(f"{side}_fore", (x(15.5, 7), 25, -3.5), (7, 18, 7), SKIN, mirror=mirror)
        fore.cube(f"{side}_cuff", (x(15, 8), 36, -4), (8, 5, 8), GOLD, inflate=0.2, mirror=mirror)
        hand = fore.bone(f"{side}_hand", (19 * s, 26, 0))
        hand.cube(f"{side}_palm", (x(15, 8), 18, -4), (8, 8, 8), SKIN, mirror=mirror, detail=palm_detail)
        for i, cx in enumerate((15.5, 18, 20.5)):
            hand.cube(f"{side}_claw_{i}", (x(cx, 2), 11, -4), (2, 7, 2), CLAW, mirror=mirror)
        hand.cube(f"{side}_thumb", (x(14, 2), 15, -2), (2, 6, 2), CLAW, mirror=mirror)

    # the tail, curling around his right side at rest (each segment turned a little further)
    parent = coils
    segs = [(14, 13, 16), (12, 11, 16), (10, 9, 16), (8, 7, 16), (6, 5, 16), (4, 4, 14)]
    z = 14
    for i, (w, h, d) in enumerate(segs):
        bone = parent.bone(f"tail_{i + 1}", (0, h / 2 + i * 0.5, z), rotation=(0, 28, 0))
        bone.cube(f"tail_{i + 1}", (-w / 2, i * 0.5, z - (1 if i else 0)), (w, h, d + (1 if i else 0)), SCALES)
        parent = bone
        z += d
    parent.cube("tail_barb", (-1.5, 2, z - 2), (3, 3, 8), CLAW, rotation=(18, 0, 0), origin=(0, 3.5, z - 2))
    parent.cube("tail_fin_r", (1, 4, z - 10), (4, 1, 6), CLAW, rotation=(0, 25, 0), origin=(2, 4.5, z - 7))
    parent.cube("tail_fin_l", (-5, 4, z - 10), (4, 1, 6), CLAW, rotation=(0, -25, 0), origin=(-2, 4.5, z - 7))

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


def arms(a, keys, forearm=None, hand=None):
    """Both arms alike (mirrored); keys are the right arm's."""
    a.rot("right_arm", keys)
    a.rot("left_arm", [(t, mirror(v)) for t, v in keys])
    if forearm:
        a.rot("right_forearm", forearm)
        a.rot("left_forearm", [(t, mirror(v)) for t, v in forearm])
    if hand:
        a.rot("right_hand", hand)
        a.rot("left_hand", [(t, mirror(v)) for t, v in hand])


def animations(m):
    idle = m.animation("idle", 4.0, loop=True)
    idle.pos("torso", [(0, ZERO), (2, (0, 0.6, 0)), (4, ZERO)])
    idle.rot("head", [(0, ZERO), (1, (-2, 8, 0)), (2, ZERO), (3, (-2, -8, 0)), (4, ZERO)])
    idle.rot("jaw", [(0, ZERO), (2, (-3, 0, 0)), (4, ZERO)])
    idle.rot("waist", [(0, ZERO), (1, (0, 0, 1.5)), (2, ZERO), (3, (0, 0, -1.5)), (4, ZERO)])
    idle.rot("right_arm", [(0, ZERO), (2, (2, 0, 3)), (4, ZERO)])
    idle.rot("left_arm", [(0, ZERO), (2, (2, 0, -3)), (4, ZERO)])
    idle.rot("coils", [(0, ZERO), (1, (0, 1.5, 0)), (2, ZERO), (3, (0, -1.5, 0)), (4, ZERO)])
    for i, bone in enumerate(TAIL):
        wave(idle, bone, 4.0, 4 + i, 0.7 * i)

    # rising out of his coils: bowed and wrapped, he straightens, spreads his arms and roars
    intro = m.animation("intro", 6.0)
    intro.pos("waist", [(0, (0, -20, 0)), (0.6, (0, -20, 0)), (2.6, ZERO)])
    intro.rot("waist", [(0, (-35, 0, 0)), (0.6, (-35, 0, 0)), (2.6, ZERO)])
    intro.rot("head", [(0, (-35, 0, 0)), (2.4, (-10, 0, 0)), (3.0, (25, 0, 0)), (4.4, (25, 0, 0)), (5.4, ZERO)])
    intro.rot("jaw", [(0, ZERO), (2.8, ZERO), (3.1, (-35, 0, 0)), (3.6, (-30, 0, 0)), (4.0, (-36, 0, 0)), (4.4, (-32, 0, 0)), (4.9, ZERO)])
    arms(intro, [(0, (40, 0, -10)), (2.4, (20, 0, 0)), (3.1, (-20, 0, 75)), (4.4, (-20, 0, 72)), (5.4, ZERO)],
         forearm=[(0, (60, 0, 0)), (3.1, (-10, 0, 0)), (5.4, ZERO)])
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
    grab.rot("head", [(0, ZERO), (1.0, (-15, 0, 0)), (1.4, (-15, 0, 0))])
    grab.event(0.8, "lunge").event(1.0, "catch")

    hold = m.animation("hold", 1.0, loop=True)
    hold.rot("tail_1", [(0, (0, 150, 0)), (0.5, (0, 145, 0)), (1.0, (0, 150, 0))])
    for bone in TAIL[1:]:
        hold.rot(bone, [(0, (0, 25, 0)), (0.5, (0, 32, 0)), (1.0, (0, 25, 0))])
    hold.rot("torso", [(0, (-8, -10, 0)), (0.5, (-11, -10, 0)), (1.0, (-8, -10, 0))])
    hold.rot("right_arm", [(0, (60, 0, 10)), (0.5, (70, 0, 10)), (1.0, (60, 0, 10))])
    hold.rot("head", [(0, (-15, 0, 0)), (0.5, (-15, 0, 0)), (1.0, (-15, 0, 0))])
    hold.rot("jaw", [(0, ZERO), (0.5, (-15, 0, 0)), (1.0, ZERO)])

    release = m.animation("release", 0.8)
    release.rot("tail_1", [(0, (0, 150, 0)), (0.8, ZERO)])
    for bone in TAIL[1:]:
        release.rot(bone, [(0, (0, 25, 0)), (0.8, ZERO)])
    release.rot("torso", [(0, (-8, -10, 0)), (0.8, ZERO)])
    release.rot("right_arm", [(0, (60, 0, 10)), (0.8, ZERO)])
    release.rot("head", [(0, (-15, 0, 0)), (0.8, ZERO)])

    # parried: thrown back off balance, open to blows for a moment
    stagger = m.animation("stagger", 2.0)
    stagger.rot("waist", [(0, ZERO), (0.15, (18, 0, 0)), (0.6, (12, 0, -5)), (1.2, (14, 0, 5)), (2.0, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.15, (30, 0, 0)), (0.6, (20, 15, 0)), (1.2, (20, -15, 0)), (2.0, ZERO)])
    stagger.rot("jaw", [(0, ZERO), (0.15, (-25, 0, 0)), (1.0, (-20, 0, 0)), (1.6, ZERO)])
    arms(stagger, [(0, ZERO), (0.15, (-30, 0, 60)), (1.4, (-20, 0, 50)), (2.0, ZERO)],
         forearm=[(0, ZERO), (0.15, (-10, 0, 0)), (2.0, ZERO)])
    for bone in TAIL:
        stagger.rot(bone, [(0, ZERO), (0.15, (0, -15, 0)), (2.0, ZERO)])

    # at half his health: he rears up and roars, and his tail lashes out
    phase = m.animation("phase", 3.2)
    phase.pos("waist", [(0, ZERO), (0.4, (0, -6, 0)), (1.0, (0, 4, 0)), (2.6, (0, 4, 0)), (3.2, ZERO)])
    phase.rot("torso", [(0, ZERO), (0.4, (-15, 0, 0)), (1.0, (15, 0, 0)), (2.6, (15, 0, 0)), (3.2, ZERO)])
    phase.rot("head", [(0, ZERO), (1.0, (35, 0, 0)), (2.6, (35, 0, 0)), (3.2, ZERO)])
    phase.rot("jaw", [(0, ZERO), (1.0, (-40, 0, 0)), (2.6, (-40, 0, 0)), (3.0, ZERO)])
    arms(phase, [(0, ZERO), (1.0, (12, 0, 120)), (1.4, (8, 0, 126)), (1.8, (14, 0, 118)), (2.2, (9, 0, 125)), (2.6, (12, 0, 120)), (3.2, ZERO)],
         forearm=[(0, ZERO), (1.0, (-20, 0, 0)), (2.6, (-20, 0, 0)), (3.2, ZERO)])
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
         forearm=[(0, ZERO), (1.6, (-20, 0, 0)), (3.5, (-20, 0, 0))])
    for bone in TAIL:
        death.rot(bone, [(0, ZERO), (0.5, (0, -20, 0)), (1.6, (0, -28, 0)), (3.5, (0, -28, 0))])
    death.pos("coils", [(0, ZERO), (1.6, (0, -4, 0)), (3.5, (0, -6, 0))])
    death.event(1.6, "fall")


if __name__ == "__main__":
    build().write()
