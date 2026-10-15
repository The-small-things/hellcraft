#!/usr/bin/env python3
"""The centaurs (the river of blood's monster): "round about the moat in thousands go, shooting with shafts whatever
soul uplifts itself out of the blood more than its guilt allotted" (Inferno XII). A bearded archer's body on a bay
horse's, with a bow in hand and a quiver on his back. They patrol the banks by the fords and shoot whoever they see
crossing; the special animation is the long look around that tells you when they're watching.

Run from the repo root:  python3 tools/art/centaur.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="a07658", dark="6a4a34")
COAT = material("coat", "hair", light="7a4a2a", dark="3a2010")
MANE = material("mane", "hair", light="2a1a10", dark="0a0604")
HOOF = material("hoof", "horn", root="3a3028", tip="1a1410")
LEATHER = material("leather", "flat", base="4a3020")
BOW = material("bow", "horn", root="5a3a1a", tip="2a1a0a")
STRING = material("string", "flat", base="d8d0b8")
EYE = hexc("ff8a2a")


def face(faces):
    f = faces["front"]
    for cx in (1, f.w - 3):
        f.px(cx, 2, shade(SKIN.c["dark"], 0.5))
        f.glow(cx, 3, EYE)
    # a dark beard over the lower face
    for y in range(f.h - 3, f.h):
        for x in range(f.w):
            f.px(x, y, MANE.c["light"] if (x + y) % 3 else MANE.c["dark"])


def strap(faces):
    """The quiver strap across the chest, shoulder to hip."""
    f = faces["front"]
    for i in range(f.h):
        x = int(f.w - 1 - i * (f.w - 1) / max(1, f.h - 1))
        f.px(x, i, LEATHER.c["base"])
        if x + 1 < f.w:
            f.px(x + 1, i, shade(LEATHER.c["base"], 0.8))


def build():
    m = Model("centaur", texture_width=128)
    horse = m.bone("horse", (0, 14, 0))
    horse.cube("barrel", (-5, 12, -9), (10, 10, 22), COAT)
    tail = horse.bone("tail", (0, 21, 13))
    tail.cube("tail", (-1.5, 9, 13), (3, 12, 3), MANE)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        for end, z in (("front", -6), ("back", 9)):
            leg = horse.bone(f"{end}_{side}_leg", (3 * s, 13, z))
            leg.cube(f"{end}_{side}_leg", (x(1, 4), 2, z - 2), (4, 11, 4), COAT, mirror=s == -1)
            leg.cube(f"{end}_{side}_hoof", (x(0.5, 5), 0, z - 2.5), (5, 2, 5), HOOF, mirror=s == -1)
    # the man's half rises from the front of the barrel
    torso = horse.bone("torso", (0, 21, -7))
    torso.cube("torso", (-4, 21, -9.5), (8, 11, 5), SKIN, detail=strap)
    torso.cube("quiver", (1, 25, -4.5), (3, 9, 3), LEATHER)
    head = torso.bone("head", (0, 32, -7))
    head.cube("head", (-3.5, 32, -10.5), (7, 7, 7), SKIN, detail=face)
    head.cube("hair", (-4, 36, -10), (8, 4, 8), MANE, inflate=0.2)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = torso.bone(f"{side}_arm", (5.5 * s, 31, -7))
        arm.cube(f"{side}_arm", (x(4, 3), 21, -8.5), (3, 11, 3), SKIN, mirror=s == -1)
    # the bow, in the left hand
    bow = torso.bone("bow", (-5.5, 21.5, -7))
    bow.cube("bow", (-6, 13.5, -7.5), (1, 16, 1), BOW)
    bow.cube("string", (-6, 14, -6.5), (1, 15, 1), STRING, inflate=-0.3)
    animations(m)
    return m


ZERO = (0, 0, 0)
ARMS_READY = {"right_arm": (-20, 0, 0), "left_arm": (-40, 0, 10)}


def animations(m):
    idle = m.animation("idle", 2.0, loop=True)
    idle.rot("tail", [(0, (20, 0, 0)), (1.0, (25, 8, 0)), (2.0, (20, 0, 0))])
    idle.pos("torso", [(0, ZERO), (1.0, (0, 0.4, 0)), (2.0, ZERO)])
    idle.rot("left_arm", [(0, ARMS_READY["left_arm"]), (2.0, ARMS_READY["left_arm"])])
    idle.rot("bow", [(0, (-40, 0, 10)), (2.0, (-40, 0, 10))])

    # a four-beat walk
    walk = m.animation("walk", 1.0, loop=True)
    for leg, off in (("front_right", 0.0), ("back_left", 0.25), ("front_left", 0.5), ("back_right", 0.75)):
        keys = []
        for t in (0.0, 0.25, 0.5, 0.75, 1.0):
            phase = (t - off) % 1.0
            angle = 30 if phase < 0.25 else (-10 if phase < 0.5 else (-30 if phase < 0.75 else 10))
            keys.append((t, (angle, 0, 0)))
        walk.rot(f"{leg}_leg", keys)
    walk.rot("tail", [(0, (25, 0, 0)), (0.5, (30, 0, 0)), (1.0, (25, 0, 0))])
    walk.pos("horse", [(0, ZERO), (0.25, (0, 0.5, 0)), (0.5, ZERO), (0.75, (0, 0.5, 0)), (1.0, ZERO)])

    # draw and loose: the bow comes up level, the right hand draws to the cheek
    attack = m.animation("attack", 1.0)
    attack.rot("left_arm", [(0, ARMS_READY["left_arm"]), (0.3, (-90, 0, 0)), (0.75, (-90, 0, 0)), (1.0, ARMS_READY["left_arm"])])
    attack.rot("bow", [(0, (-40, 0, 10)), (0.3, (-90, 0, 90)), (0.75, (-90, 0, 90)), (1.0, (-40, 0, 10))])
    attack.rot("right_arm", [(0, ARMS_READY["right_arm"]), (0.3, (-90, 0, 0)), (0.6, (-95, -30, 0)), (0.65, (-80, 10, 0)),
                             (1.0, ARMS_READY["right_arm"])])
    attack.rot("torso", [(0, ZERO), (0.3, (0, 20, 0)), (0.75, (0, 20, 0)), (1.0, ZERO)])
    attack.event(0.62, "loose")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (-15, 0, 0)), (0.4, ZERO)])
    hurt.rot("horse", [(0, ZERO), (0.12, (-6, 0, 0)), (0.4, ZERO)])

    # the long look round: the archer turns from one bank to the other, shading his eyes
    look = m.animation("look", 3.0)
    look.rot("torso", [(0, ZERO), (0.6, (0, 55, 0)), (1.4, (0, 55, 0)), (2.0, (0, -55, 0)), (2.6, (0, -55, 0)), (3.0, ZERO)])
    look.rot("head", [(0, ZERO), (0.6, (0, 20, 0)), (1.4, (0, 20, 0)), (2.0, (0, -20, 0)), (2.6, (0, -20, 0)), (3.0, ZERO)])
    look.rot("right_arm", [(0, ARMS_READY["right_arm"]), (0.4, (-150, 0, 30)), (2.6, (-150, 0, 30)), (3.0, ARMS_READY["right_arm"])])

    # buckles and goes down on its side
    death = m.animation("death", 1.4)
    death.rot("horse", [(0, ZERO), (0.5, (0, 0, 10)), (1.4, (0, 0, 85))])
    death.pos("horse", [(0, ZERO), (0.5, (0, -4, 0)), (1.4, (0, -9, 0))])
    for leg in ("front_right", "front_left"):
        death.rot(f"{leg}_leg", [(0, ZERO), (0.4, (-60, 0, 0)), (1.4, (-20, 0, 0))])
    death.rot("torso", [(0, ZERO), (1.4, (40, 0, 0))])


if __name__ == "__main__":
    build().write()
