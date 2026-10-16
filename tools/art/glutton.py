#!/usr/bin/env python3
"""The glutton (Gluttony's monster): a soul swollen past its shape, sunk in the mire under the rain "like a dog that
barking craves" (Inferno VI). It waits half-buried and heaves itself up out of the mud when someone comes near; its
blow is a slam of its belly that leaves you sick and starving, and it bursts into filth when it dies.

Run from the repo root:  python3 tools/art/glutton.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="8a8a5a", dark="5a5a36")
BELLY = material("belly", "skin", base="a49a6a", dark="6a6440")
MUD = material("mud", "hair", light="3e3220", dark="1e180e")
EYE = hexc("e8f05a")
MOUTH = hexc("2a1a0a")


def face(faces):
    f = faces["front"]
    for cx in (1, f.w - 2):
        f.glow(cx, 2, EYE)
    for x in range(1, f.w - 1):
        f.px(x, f.h - 2, MOUTH)
        f.px(x, f.h - 1, MOUTH)


def belly(faces):
    """Stretched skin, a navel, and the mud it has been lying in."""
    f = faces["front"]
    f.px(f.w // 2, f.h // 2, shade(BELLY.c["dark"], 0.7))
    for name in ("front", "back", "left", "right"):
        s = faces[name]
        for y in range(s.h - 4, s.h):
            for x in range(s.w):
                if (x + y) % 3:
                    s.px(x, y, MUD.c["light"] if (x * y) % 4 else MUD.c["dark"])
    for y in range(2, f.h - 4, 4):
        for x in range(1, f.w - 1):
            if (x + y) % 5 == 0:
                f.px(x, y, shade(BELLY.c["dark"], 0.9))


def build():
    m = Model("glutton", texture_width=128)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (4 * s, 6, 0))
        leg.cube(f"{side}_leg", (x(1.5, 5), 0, -2.5), (5, 6, 5), SKIN, mirror=s == -1)
    body = m.bone("body", (0, 6, 0))
    body.cube("belly", (-9, 4, -8), (18, 16, 16), BELLY, detail=belly)
    body.cube("chest", (-6, 19, -5), (12, 5, 10), SKIN)
    head = body.bone("head", (0, 24, -2))
    head.cube("head", (-3.5, 23, -6), (7, 6, 6), SKIN, detail=face)
    head.cube("chins", (-4.5, 21, -6.5), (9, 3, 7), BELLY)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = body.bone(f"{side}_arm", (9 * s, 20, -1))
        arm.cube(f"{side}_arm", (x(8, 4), 10, -3), (4, 10, 4), SKIN, mirror=s == -1)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    idle = m.animation("idle", 2.0, loop=True)
    idle.pos("body", [(0, ZERO), (1, (0, -0.6, 0)), (2, ZERO)])
    idle.rot("head", [(0, ZERO), (1, (-8, 0, 0)), (2, ZERO)])
    idle.rot("right_arm", [(0, (0, 0, 10)), (1, (0, 0, 16)), (2, (0, 0, 10))])
    idle.rot("left_arm", [(0, (0, 0, -10)), (1, (0, 0, -16)), (2, (0, 0, -10))])

    walk = m.animation("walk", 1.4, loop=True)
    walk.rot("body", [(0, (0, 0, 6)), (0.7, (0, 0, -6)), (1.4, (0, 0, 6))])
    walk.rot("right_leg", [(0, (25, 0, 0)), (0.7, (-25, 0, 0)), (1.4, (25, 0, 0))])
    walk.rot("left_leg", [(0, (-25, 0, 0)), (0.7, (25, 0, 0)), (1.4, (-25, 0, 0))])

    # it throws its whole belly forward
    attack = m.animation("attack", 1.0)
    attack.rot("body", [(0, ZERO), (0.35, (18, 0, 0)), (0.5, (-25, 0, 0)), (0.75, (-20, 0, 0)), (1.0, ZERO)])
    attack.pos("body", [(0, ZERO), (0.35, (0, 0, 2)), (0.5, (0, 0, -4)), (1.0, ZERO)])
    attack.rot("right_arm", [(0, ZERO), (0.35, (60, 0, 20)), (0.5, (80, 0, 10)), (1.0, ZERO)])
    attack.rot("left_arm", [(0, ZERO), (0.35, (60, 0, -20)), (0.5, (80, 0, -10)), (1.0, ZERO)])
    attack.event(0.5, "hit")

    # heaving itself up out of the mud
    emerge = m.animation("emerge", 2.0)
    emerge.pos("body", [(0, (0, -22, 0)), (0.3, (0, -22, 0)), (1.6, (0, 1, 0)), (2.0, ZERO)])
    emerge.pos("right_leg", [(0, (0, -22, 0)), (0.3, (0, -22, 0)), (1.6, ZERO)])
    emerge.pos("left_leg", [(0, (0, -22, 0)), (0.3, (0, -22, 0)), (1.6, ZERO)])
    emerge.rot("head", [(0, (-30, 0, 0)), (1.6, (15, 0, 0)), (2.0, ZERO)])
    emerge.rot("right_arm", [(0, (150, 0, 20)), (1.0, (120, 0, 30)), (2.0, ZERO)])
    emerge.rot("left_arm", [(0, (150, 0, -20)), (1.0, (120, 0, -30)), (2.0, ZERO)])
    emerge.event(0.3, "rise")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (8, 0, 0)), (0.3, ZERO)])

    # it slumps and splits
    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.6, (30, 0, 15)), (1.2, (40, 0, 20))])
    death.pos("body", [(0, ZERO), (0.6, (0, -3, 0)), (1.2, (0, -6, 0))])
    death.event(0.6, "burst")


if __name__ == "__main__":
    build().write()
