#!/usr/bin/env python3
"""A Cyclops of the quarries: one of Vulcan's smiths (Aeneid VIII: Brontes, Steropes and Arges forging thunderbolts under
Etna), set to cutting stone in the Cyclops Quarries of the Forge of Dis. A hulking one-eyed giant in a hide loincloth,
a quarry maul in his fists, his single eye burning.

Run from the repo root:  python3 tools/art/cyclops.py && python3 tools/gen_bosses.py

The animations' events are what the server times his blows by (CyclopsEntity): "flash" opens the window to block his
maul and "hit" is when it lands; "throw" lets the boulder go; "stamp" is his quake; "roar" his rage at half health and
his waking; "fall" his death.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="8a6a52", dark="5a4232")
HIDE = material("hide", "hair", light="6a4a2a", dark="3a2614")
STONE = material("stone", "flat", base="4a4448")
IRON = material("iron", "metal", base="4a4a50", high="7a7a84", low="26262a")
HAFT = material("haft", "flat", base="6a4a2a")
EYE = hexc("ffb020")
EYE_HOT = hexc("fff0a0")
PUPIL = hexc("3a1000")


def face(faces):
    f = faces["front"]
    w = f.w
    cx = w // 2
    # the one great eye, burning, under a heavy brow
    for x in range(1, w - 1):
        f.px(x, 1, shade(SKIN.c["dark"], 0.7))
    for dx in (-2, -1, 0, 1):
        for dy in (2, 3, 4):
            f.glow(cx + dx, dy, EYE)
    f.glow(cx - 1, 3, EYE_HOT)
    f.glow(cx, 3, PUPIL)
    # a wide mouth
    for x in range(2, w - 2):
        f.px(x, f.h - 2, shade(SKIN.c["dark"], 0.6))


def build():
    m = Model("cyclops", texture_width=256)
    hips = m.bone("hips", (0, 22, 0))
    hips.cube("loincloth", (-7, 17, -4.5), (14, 8, 9), HIDE)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        thigh = hips.bone(f"{side}_thigh", (4 * s, 19, 0))
        thigh.cube(f"{side}_thigh", (x(0.5, 7), 9, -3.5), (7, 11, 7), SKIN, mirror=s == -1)
        shin = thigh.bone(f"{side}_shin", (4 * s, 10, 0))
        shin.cube(f"{side}_shin", (x(1, 6), 0, -3), (6, 11, 6), SKIN, mirror=s == -1)
    torso = hips.bone("torso", (0, 24, 0))
    torso.cube("belly", (-8, 24, -5), (16, 9, 10), SKIN)
    torso.cube("chest", (-10, 33, -5.5), (20, 11, 11), SKIN)
    torso.cube("hump", (-7, 42, -1), (14, 5, 7), SKIN)
    head = torso.bone("head", (0, 44, -2))
    head.cube("skull", (-5, 43, -8), (10, 10, 9), SKIN, detail=face)
    head.cube("horn", (-1, 52, -6), (2, 3, 2), STONE)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = torso.bone(f"{side}_arm", (12 * s, 42, 0))
        arm.cube(f"{side}_arm", (x(10, 7), 30, -3.5), (7, 13, 7), SKIN, mirror=s == -1)
        forearm = arm.bone(f"{side}_forearm", (13.5 * s, 30, 0))
        forearm.cube(f"{side}_forearm", (x(10, 7), 18, -3.5), (7, 12, 7), SKIN, mirror=s == -1)
        forearm.cube(f"{side}_fist", (x(10, 7), 13, -3.5), (7, 5, 7), SKIN, mirror=s == -1)
        if s == 1:
            # the maul: haft through the right fist, a great block of iron hanging at its end
            maul = forearm.bone("maul", (13.5, 16, 0))
            maul.cube("maul_haft", (12, -6, -1.5), (3, 26, 3), HAFT)
            maul.cube("maul_head", (8.5, -12, -5), (10, 7, 10), IRON)
    # the boulder he throws, held over his head when he does (hidden in its socket otherwise: scaled to nothing)
    boulder = torso.bone("boulder", (0, 60, -2))
    boulder.cube("boulder", (-6, 55, -8), (12, 11, 12), STONE)
    animations(m)
    return m


ZERO = (0, 0, 0)
HIDDEN = (0.01, 0.01, 0.01)
SHOWN = (1, 1, 1)


def animations(m):
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("torso", [(0, ZERO), (1.5, (0, 0.6, 0)), (3.0, ZERO)])
    idle.rot("head", [(0, (0, -12, 0)), (1.5, (0, 12, 0)), (3.0, (0, -12, 0))])
    idle.scale("boulder", [(0, HIDDEN), (3.0, HIDDEN)])

    walk = m.animation("walk", 1.3, loop=True)
    walk.rot("right_thigh", [(0, (-28, 0, 0)), (0.65, (28, 0, 0)), (1.3, (-28, 0, 0))])
    walk.rot("left_thigh", [(0, (28, 0, 0)), (0.65, (-28, 0, 0)), (1.3, (28, 0, 0))])
    walk.rot("right_shin", [(0, ZERO), (0.3, (-30, 0, 0)), (0.65, ZERO), (1.3, ZERO)])
    walk.rot("left_shin", [(0, ZERO), (0.65, ZERO), (0.95, (-30, 0, 0)), (1.3, ZERO)])
    walk.rot("left_arm", [(0, (-20, 0, -6)), (0.65, (20, 0, -6)), (1.3, (-20, 0, -6))])
    walk.pos("hips", [(0, ZERO), (0.32, (0, -0.8, 0)), (0.65, ZERO), (0.97, (0, -0.8, 0)), (1.3, ZERO)])
    walk.scale("boulder", [(0, HIDDEN), (1.3, HIDDEN)])

    # the Smash: the maul swung up over his head, a white flash, then down in front of him
    smash = m.animation("smash", 1.7)
    smash.rot("right_arm", [(0, ZERO), (0.65, (160, 0, 0)), (0.95, (165, 0, 0)), (1.1, (40, 0, 0)), (1.7, ZERO)])
    smash.rot("left_arm", [(0, ZERO), (0.65, (150, 0, 20)), (1.1, (40, 0, 10)), (1.7, ZERO)])
    smash.rot("torso", [(0, ZERO), (0.65, (12, 0, 0)), (1.1, (-25, 0, 0)), (1.7, ZERO)])
    smash.scale("boulder", [(0, HIDDEN), (1.7, HIDDEN)])
    smash.event(0.55, "flash").event(1.1, "hit")

    # the Throw: he wrenches a boulder from the ground, lifts it over his head and hurls it
    throw = m.animation("throw", 2.0)
    throw.rot("torso", [(0, ZERO), (0.5, (-35, 0, 0)), (1.0, (15, 0, 0)), (1.35, (-25, 0, 0)), (2.0, ZERO)])
    throw.rot("right_arm", [(0, ZERO), (0.5, (60, 0, 0)), (1.0, (170, 0, 10)), (1.35, (60, 0, 0)), (2.0, ZERO)])
    throw.rot("left_arm", [(0, ZERO), (0.5, (60, 0, 0)), (1.0, (170, 0, -10)), (1.35, (60, 0, 0)), (2.0, ZERO)])
    throw.scale("boulder", [(0, HIDDEN), (0.5, HIDDEN), (0.55, SHOWN), (1.35, SHOWN), (1.4, HIDDEN), (2.0, HIDDEN)])
    throw.pos("boulder", [(0, (0, -40, -10)), (0.55, (0, -40, -10)), (1.0, ZERO), (1.35, ZERO)])
    throw.event(1.35, "throw")

    # the Quake: up on one foot, then down with all his weight
    quake = m.animation("quake", 1.6)
    quake.rot("right_thigh", [(0, ZERO), (0.6, (-70, 0, 0)), (0.9, (-75, 0, 0)), (1.05, (10, 0, 0)), (1.6, ZERO)])
    quake.rot("right_shin", [(0, ZERO), (0.6, (60, 0, 0)), (1.05, ZERO)])
    quake.rot("torso", [(0, ZERO), (0.6, (10, 0, 0)), (1.05, (-15, 0, 0)), (1.6, ZERO)])
    quake.pos("hips", [(0, ZERO), (0.6, (0, 2, 0)), (1.05, (0, -3, 0)), (1.6, ZERO)])
    quake.scale("boulder", [(0, HIDDEN), (1.6, HIDDEN)])
    quake.event(1.05, "stamp")

    # reels after a blocked blow
    stagger = m.animation("stagger", 2.2)
    stagger.pos("hips", [(0, ZERO), (0.3, (0, -7, 2)), (1.8, (0, -7, 2)), (2.2, ZERO)])
    stagger.rot("right_thigh", [(0, ZERO), (0.3, (80, 0, 0)), (1.8, (80, 0, 0)), (2.2, ZERO)])
    stagger.rot("right_shin", [(0, ZERO), (0.3, (-90, 0, 0)), (1.8, (-90, 0, 0)), (2.2, ZERO)])
    stagger.rot("torso", [(0, ZERO), (0.3, (-25, 0, 0)), (1.8, (-25, 0, 0)), (2.2, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.6, (-10, -25, 0)), (0.9, (-10, 25, 0)), (1.2, (-10, -25, 0)), (2.2, ZERO)])
    stagger.scale("boulder", [(0, HIDDEN), (2.2, HIDDEN)])

    # woken (and again at half health): he beats his chest and roars
    roar = m.animation("roar", 2.4)
    roar.rot("head", [(0, ZERO), (0.8, (35, 0, 0)), (1.8, (35, 0, 0)), (2.4, ZERO)])
    roar.rot("right_arm", [(0, ZERO), (0.4, (100, 0, -40)), (0.6, (80, 0, -30)), (0.8, (100, 0, -40)), (1.0, (20, 0, 60)), (1.8, (20, 0, 60)),
                           (2.4, ZERO)])
    roar.rot("left_arm", [(0, ZERO), (0.5, (100, 0, 40)), (0.7, (80, 0, 30)), (1.0, (20, 0, -60)), (1.8, (20, 0, -60)), (2.4, ZERO)])
    roar.rot("torso", [(0, ZERO), (0.8, (12, 0, 0)), (1.8, (12, 0, 0)), (2.4, ZERO)])
    roar.scale("boulder", [(0, HIDDEN), (2.4, HIDDEN)])
    roar.event(1.0, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (10, 0, 0)), (0.4, ZERO)])
    hurt.scale("boulder", [(0, HIDDEN), (0.4, HIDDEN)])

    death = m.animation("death", 2.8)
    death.pos("hips", [(0, ZERO), (0.8, (0, -8, 0)), (1.6, (0, -14, -8)), (2.8, (0, -18, -14))])
    death.rot("hips", [(0, ZERO), (0.8, ZERO), (1.6, (-60, 0, 0)), (2.8, (-85, 0, 0))])
    death.rot("right_thigh", [(0, ZERO), (0.8, (80, 0, 0)), (2.8, (80, 0, 0))])
    death.rot("left_thigh", [(0, ZERO), (0.8, (75, 0, 0)), (2.8, (75, 0, 0))])
    death.rot("right_arm", [(0, ZERO), (1.6, (140, 0, 20)), (2.8, (160, 0, 20))])
    death.rot("left_arm", [(0, ZERO), (1.6, (140, 0, -20)), (2.8, (160, 0, -20))])
    death.scale("boulder", [(0, HIDDEN), (2.8, HIDDEN)])
    death.event(1.6, "fall")


if __name__ == "__main__":
    build().write()
