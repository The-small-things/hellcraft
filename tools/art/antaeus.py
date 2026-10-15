#!/usr/bin/env python3
"""Antaeus (Inferno XXXI), the giant who was never chained because he took no part in the war on the gods: he stands
on the ice of Cocytus at the foot of the Well, as tall as the cliff, a lion's skin round his loins, and at Virgil's
asking takes the travellers in his hand and sets them down on the ice. "He set us down upon the bottom, and did not
linger thus bent, but like a mast upon a ship uprose."

Run from the repo root:  python3 tools/art/antaeus.py && python3 tools/gen_bosses.py

He is drawn eight times his Blockbench size (about 28 blocks tall). His right hand rests palm-up on the Well's rim in
his idle pose; "lower" carries whoever is in it down to the ice ("take" .. "set"), and "raise" puts it back ("ready").
The server moves the passenger along the same arc (AntaeusEntity), so keep the arm's angles in step with it.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="7a5a42", dark="4a3426")
LION = material("lion", "hair", light="b08a40", dark="5a4018")
HAIR = material("hair", "hair", light="3a2a1e", dark="120c08")
FROST = hexc("c8e8ff")
EYE = hexc("ffd890")


def face(faces):
    f = faces["front"]
    w = f.w
    for cx in (2, w - 4):
        f.px(cx, 4, shade(SKIN.c["dark"], 0.5))
        f.glow(cx + 1, 5, EYE)
    for x in range(2, w - 2):
        f.px(x, 8, shade(SKIN.c["dark"], 0.6))
    for y in range(f.h - 3, f.h):
        for x in range(1, w - 1):
            f.px(x, y, HAIR.c["light"] if (x + y) % 2 else HAIR.c["dark"])


def frosted(faces):
    """Rime on his shins, from standing in the ice."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(f.h - 8, f.h):
            for x in range(f.w):
                if (x + y) % 3 == 0 or y > f.h - 3:
                    f.px(x, y, FROST)


def build():
    m = Model("antaeus", texture_width=256)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (5 * s, 24, 0))
        leg.cube(f"{side}_leg", (x(1, 9), 0, -4.5), (9, 24, 9), SKIN, mirror=s == -1, detail=frosted)
    body = m.bone("body", (0, 24, 0))
    body.cube("lion_skin", (-11, 20, -6), (22, 8, 12), LION)
    body.cube("torso", (-12, 26, -6), (24, 18, 12), SKIN)
    head = body.bone("head", (0, 44, 0))
    head.cube("head", (-6, 44, -6), (12, 12, 12), SKIN, detail=face)
    head.cube("hair", (-6.5, 52, -6), (13, 5, 13), HAIR, inflate=0.3)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = body.bone(f"{side}_arm", (12 * s, 42, 0))
        arm.cube(f"{side}_arm", (x(10, 7), 18, -3.5), (7, 24, 7), SKIN, mirror=s == -1)
        arm.cube(f"{side}_hand", (x(9, 9), 14, -4.5), (9, 4, 9), SKIN, mirror=s == -1)
    animations(m)
    return m


ZERO = (0, 0, 0)
# the right arm's angles: resting on the rim (forward and a little up), and down at the ice
AT_RIM = (-110, 0, 0)
AT_ICE = (-35, 0, 0)


def animations(m):
    # waiting: breathing slowly, his right palm up on the rim of the Well
    idle = m.animation("idle", 5.0, loop=True)
    idle.rot("right_arm", [(0, AT_RIM), (5.0, AT_RIM)])
    idle.rot("left_arm", [(0, (0, 0, -8)), (2.5, (0, 0, -10)), (5.0, (0, 0, -8))])
    idle.pos("body", [(0, ZERO), (2.5, (0, 0.6, 0)), (5.0, ZERO)])
    idle.rot("head", [(0, (-10, 0, 0)), (2.5, (-12, 6, 0)), (5.0, (-10, 0, 0))])

    # carrying you down: he bends, and the hand sweeps down to the ice
    lower = m.animation("lower", 4.0)
    lower.rot("right_arm", [(0, AT_RIM), (0.5, AT_RIM), (3.5, AT_ICE), (4.0, AT_ICE)], interp="linear")
    lower.rot("body", [(0, ZERO), (0.5, ZERO), (3.5, (25, 0, 0)), (4.0, (25, 0, 0))])
    lower.rot("head", [(0, (-10, 0, 0)), (3.5, (20, 0, 0)), (4.0, (20, 0, 0))])
    lower.event(0.5, "take").event(3.5, "set")

    # and up again, like a mast on a ship
    raise_ = m.animation("raise", 3.0)
    raise_.rot("right_arm", [(0, AT_ICE), (2.5, AT_RIM), (3.0, AT_RIM)])
    raise_.rot("body", [(0, (25, 0, 0)), (2.0, ZERO), (3.0, ZERO)])
    raise_.rot("head", [(0, (20, 0, 0)), (2.5, (-10, 0, 0)), (3.0, (-10, 0, 0))])
    raise_.event(2.5, "ready")


if __name__ == "__main__":
    build().write()
