#!/usr/bin/env python3
"""Malacoda, Evil-Tail (Inferno XXI), captain of the Malebranche: taller than his devils, with great curled horns, a
ragged red cape, a long forked tail and a staff with a hook. He is all courtesy: he offers to lend you an escort to
the next bridge ("the sixth bridge is all in pieces; go on along this ridge, there's another"), and it's a lie, as
it was to Virgil. He leads you into his devils' ambush and slips away laughing.

Run from the repo root:  python3 tools/art/malacoda.py && python3 tools/gen_bosses.py

Animations: idle, walk, "talk" (a courtly gesture), "point" (the way: "this way"), "laugh" (the trap is sprung:
"signal" is when he gives it), hurt, and death (he isn't killed: he flaps off; we play this if he is).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

HIDE = material("hide", "skin", base="3a1e1e", dark="1a0a0a")
HORN = material("horn", "horn", root="c0b090", tip="2a2018")
CAPE = material("cape", "robe", base="7a1414", fold="4a0a0a", trim="c8a040")
IRON = material("iron", "metal", base="4a4a4e", high="7a7a80", low="26262a")
SHAFT = material("shaft", "hair", light="6a5a40", dark="2a2014")
EYE = hexc("ffa020")
GRIN = hexc("f0e8d0")


def face(faces):
    f = faces["front"]
    w = f.w
    for cx in (1, w - 3):
        f.glow(cx, 3, EYE)
    # the courtly smile
    for x in range(1, w - 1):
        f.px(x, f.h - 2, GRIN if x % 2 else shade(HIDE.c["dark"], 0.4))
    f.px(0, f.h - 3, GRIN)
    f.px(w - 1, f.h - 3, GRIN)


def build():
    m = Model("malacoda", texture_width=128)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (2 * s, 15, 0))
        leg.cube(f"{side}_leg", (x(0, 4), 0, -2), (4, 15, 4), HIDE, mirror=s == -1)
    body = m.bone("body", (0, 15, 0))
    body.cube("torso", (-4.5, 15, -2.5), (9, 13, 5), HIDE)
    body.cube("cape", (-5, 6, 2.5), (10, 22, 1), CAPE)
    tail = body.bone("tail", (0, 16, 2))
    tail.cube("tail", (-0.5, 4, 3), (1, 12, 1), HIDE)
    tail.cube("tail_fork", (-1.5, 2, 3), (3, 2, 1), HIDE)
    head = body.bone("head", (0, 28, 0))
    head.cube("head", (-3.5, 28, -3.5), (7, 7, 7), HIDE, detail=face)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        horn = head.bone(f"{side}_horn", (3 * s, 34, -1))
        horn.cube(f"{side}_horn", (x(2, 4), 33, -2), (4, 2, 2), HORN, mirror=s == -1)
        horn.cube(f"{side}_horn_tip", (x(5, 2), 35, -2), (2, 4, 2), HORN, mirror=s == -1)
        arm = body.bone(f"{side}_arm", (6 * s, 27, 0))
        arm.cube(f"{side}_arm", (x(4.5, 3), 14, -1.5), (3, 13, 3), HIDE, mirror=s == -1)
    staff = body.bone("staff", (-6.5, 15.5, 0))
    staff.cube("staff", (-7, 0, -1), (1, 34, 1), SHAFT)
    staff.cube("staff_hook", (-7, 33, -3), (1, 1, 3), IRON)
    staff.cube("staff_barb", (-7, 31, -3), (1, 2, 1), IRON)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    idle = m.animation("idle", 2.4, loop=True)
    idle.rot("tail", [(0, (20, 0, 0)), (1.2, (20, 25, 0)), (2.4, (20, 0, 0))])
    idle.rot("left_arm", [(0, (15, 0, 0)), (2.4, (15, 0, 0))])
    idle.rot("head", [(0, ZERO), (1.2, (0, 12, 4)), (2.4, ZERO)])

    walk = m.animation("walk", 1.0, loop=True)
    walk.rot("right_leg", [(0, (30, 0, 0)), (0.5, (-30, 0, 0)), (1.0, (30, 0, 0))])
    walk.rot("left_leg", [(0, (-30, 0, 0)), (0.5, (30, 0, 0)), (1.0, (-30, 0, 0))])
    walk.rot("right_arm", [(0, (20, 0, 0)), (0.5, (-20, 0, 0)), (1.0, (20, 0, 0))])
    walk.rot("left_arm", [(0, (15, 0, 0)), (1.0, (15, 0, 0))])
    walk.rot("tail", [(0, (20, -15, 0)), (0.5, (20, 15, 0)), (1.0, (20, -15, 0))])

    # a courtly gesture: hand to the breast, a little bow
    talk = m.animation("talk", 2.0)
    talk.rot("right_arm", [(0, ZERO), (0.4, (70, 40, 0)), (1.4, (70, 40, 0)), (2.0, ZERO)])
    talk.rot("body", [(0, ZERO), (0.6, (-15, 0, 0)), (1.2, (-15, 0, 0)), (2.0, ZERO)])
    talk.rot("head", [(0, ZERO), (0.6, (-10, 0, 0)), (2.0, ZERO)])

    # "this way": a long arm pointing down the ridge
    point = m.animation("point", 1.6)
    point.rot("right_arm", [(0, ZERO), (0.4, (95, 0, 0)), (1.2, (95, 0, 0)), (1.6, ZERO)])
    point.rot("head", [(0, ZERO), (0.4, (0, -20, 0)), (1.2, (0, -20, 0)), (1.6, ZERO)])

    # the trap springs: he throws his head back and laughs, staff raised to signal his devils
    laugh = m.animation("laugh", 2.4)
    laugh.rot("head", [(0, ZERO), (0.3, (40, 0, 0)), (0.5, (25, 0, 0)), (0.7, (40, 0, 0)), (0.9, (25, 0, 0)), (1.1, (40, 0, 0)), (2.4, ZERO)])
    laugh.rot("left_arm", [(0, (15, 0, 0)), (0.6, (170, 0, 0)), (1.8, (170, 0, 0)), (2.4, (15, 0, 0))])
    laugh.rot("staff", [(0, ZERO), (0.6, (0, 0, 0)), (2.4, ZERO)])
    laugh.rot("body", [(0, ZERO), (0.3, (10, 0, 0)), (2.0, (10, 0, 0)), (2.4, ZERO)])
    laugh.event(0.6, "signal")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (12, 0, 0)), (0.3, ZERO)])

    death = m.animation("death", 1.4)
    death.rot("body", [(0, ZERO), (0.6, (-70, 0, 0)), (1.4, (-85, 0, 0))])
    death.pos("body", [(0, ZERO), (0.6, (0, -8, -2)), (1.4, (0, -13, -3))])


if __name__ == "__main__":
    build().write()
